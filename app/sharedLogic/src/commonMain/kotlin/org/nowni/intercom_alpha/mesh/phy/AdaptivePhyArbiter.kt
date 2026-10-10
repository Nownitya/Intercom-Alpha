package org.nowni.intercom_alpha.mesh.phy

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adaptive PHY Layer Arbiter for Extreme Reach and Alpine Terrain.
 *
 * Dynamically arbitrates BLE PHY modulation between LE 2M, LE 1M, LE Coded S=2,
 * and LE Coded S=8 based on continuous RF feedback, packet loss ratios,
 * elevation differentials, and alpine topography shadowing profiles.
 *
 * Implements strict hysteresis dampening to prevent high-frequency ping-pong
 * oscillations along hairpin switchbacks.
 *
 * 100% Pure Kotlin Multiplatform with zero Java/C dependencies (Rules 2 & 3).
 */
class AdaptivePhyArbiter(
    val localNodeId: String,
    initialTerrainProfile: TerrainShadowingProfile = TerrainShadowingProfile.OPEN_ALPINE_RIDGE,
    val hysteresisMarginDb: Int = 6,
    val minSamplesBeforeSwitch: Int = 3,
    val cooldownPeriodMs: Long = 5_000L
) {
    private val mutex = Mutex()

    private var currentTerrainProfile: TerrainShadowingProfile = initialTerrainProfile

    private val _peerPhyMap = MutableStateFlow<Map<String, BlePhyMode>>(emptyMap())
    val peerPhyMap: StateFlow<Map<String, BlePhyMode>> = _peerPhyMap.asStateFlow()

    private val _negotiationEvents = MutableSharedFlow<PhyNegotiationEvent>(extraBufferCapacity = 64)
    val negotiationEvents: SharedFlow<PhyNegotiationEvent> = _negotiationEvents.asSharedFlow()

    private val peerCapabilities = mutableMapOf<String, PhyCapability>()
    private val peerLinkHistory = mutableMapOf<String, PhyLinkMetrics>()
    private var sequenceCounter = 0L

    /**
     * Updates the active alpine terrain shadowing profile.
     */
    suspend fun setTerrainProfile(profile: TerrainShadowingProfile) = mutex.withLock {
        currentTerrainProfile = profile
    }

    /**
     * Registers or updates the radio capabilities of a remote peer.
     */
    suspend fun registerPeerCapability(capability: PhyCapability) = mutex.withLock {
        peerCapabilities[capability.peerId] = capability
        if (!_peerPhyMap.value.containsKey(capability.peerId)) {
            val initial = if (capability.isModeSupported(BlePhyMode.LE_1M)) BlePhyMode.LE_1M else BlePhyMode.CODED_S8
            _peerPhyMap.value = _peerPhyMap.value + (capability.peerId to initial)
        }
    }

    /**
     * Evaluates a link metric sample and returns a target [BlePhyMode] if a migration
     * is recommended and passes hysteresis dampening, or null if no change is needed.
     */
    suspend fun evaluateLinkSample(sample: PhyLinkMetrics): BlePhyMode? = mutex.withLock {
        val peerId = sample.peerId
        val capability = peerCapabilities[peerId] ?: PhyCapability(peerId)
        val currentPhy = _peerPhyMap.value[peerId] ?: sample.currentPhy
        val previousHistory = peerLinkHistory[peerId]

        // Cooldown check
        val now = sample.timestampMs
        if (previousHistory != null && (now - previousHistory.lastNegotiationTimestampMs) < cooldownPeriodMs) {
            peerLinkHistory[peerId] = sample.copy(
                currentPhy = currentPhy,
                lastNegotiationTimestampMs = previousHistory.lastNegotiationTimestampMs
            )
            return@withLock null
        }

        // Calculate terrain-adjusted effective RSSI
        val effectiveRssi = calculateEffectiveRssi(sample.rssi, sample.elevationDeltaMeters, currentTerrainProfile)

        // Determine ideal target PHY based on metrics
        val candidatePhy = selectCandidatePhy(currentPhy, effectiveRssi, sample.packetLossRatio, capability)

        if (candidatePhy == currentPhy) {
            peerLinkHistory[peerId] = sample.copy(
                currentPhy = currentPhy,
                consecutiveWeakSamples = 0,
                consecutiveStrongSamples = 0,
                lastNegotiationTimestampMs = previousHistory?.lastNegotiationTimestampMs ?: 0L
            )
            return@withLock null
        }

        val isSteppingUpRange = candidatePhy.nominalSensitivityDbm < currentPhy.nominalSensitivityDbm
        val updatedWeak = if (isSteppingUpRange) (previousHistory?.consecutiveWeakSamples ?: 0) + 1 else 0
        val updatedStrong = if (!isSteppingUpRange) (previousHistory?.consecutiveStrongSamples ?: 0) + 1 else 0

        // Emergency fast-track step-up on critical packet loss (>30%) or extreme weak signal
        val isEmergencyStepUp = isSteppingUpRange && (sample.packetLossRatio >= 0.30 || effectiveRssi <= -100)

        val thresholdMet = isEmergencyStepUp || (if (isSteppingUpRange) updatedWeak >= 2 else updatedStrong >= minSamplesBeforeSwitch)

        if (thresholdMet) {
            // Apply migration
            _peerPhyMap.value = _peerPhyMap.value + (peerId to candidatePhy)
            peerLinkHistory[peerId] = sample.copy(
                currentPhy = candidatePhy,
                consecutiveWeakSamples = 0,
                consecutiveStrongSamples = 0,
                lastNegotiationTimestampMs = now
            )

            val reason = if (isSteppingUpRange) {
                "Range step-up (RSSI: ${sample.rssi} dBm, eff: $effectiveRssi dBm, loss: ${(sample.packetLossRatio * 100).toInt()}%)"
            } else {
                "Throughput step-up (RSSI: ${sample.rssi} dBm, loss: ${(sample.packetLossRatio * 100).toInt()}%)"
            }

            _negotiationEvents.tryEmit(
                PhyNegotiationEvent(
                    peerId = peerId,
                    previousPhy = currentPhy,
                    targetPhy = candidatePhy,
                    reason = reason,
                    timestampMs = now
                )
            )

            return@withLock candidatePhy
        } else {
            peerLinkHistory[peerId] = sample.copy(
                currentPhy = currentPhy,
                consecutiveWeakSamples = updatedWeak,
                consecutiveStrongSamples = updatedStrong,
                lastNegotiationTimestampMs = previousHistory?.lastNegotiationTimestampMs ?: 0L
            )
            return@withLock null
        }
    }

    /**
     * Builds an outgoing OTA negotiation packet to request PHY migration with peer.
     */
    suspend fun createNegotiationRequest(peerId: String, requestedPhy: BlePhyMode, timestampMs: Long): PhyNegotiationPacket = mutex.withLock {
        sequenceCounter++
        PhyNegotiationPacket(
            sourceId = localNodeId,
            targetId = peerId,
            requestedPhy = requestedPhy,
            sequenceNumber = sequenceCounter,
            timestampMs = timestampMs,
            isAcknowledgement = false,
            accepted = true
        )
    }

    /**
     * Handles an incoming PHY negotiation packet from a peer.
     * Returns an acknowledgement packet.
     */
    suspend fun handleNegotiationPacket(packet: PhyNegotiationPacket): PhyNegotiationPacket = mutex.withLock {
        if (packet.isAcknowledgement) {
            if (packet.accepted) {
                val previous = _peerPhyMap.value[packet.sourceId] ?: BlePhyMode.LE_1M
                _peerPhyMap.value = _peerPhyMap.value + (packet.sourceId to packet.requestedPhy)
                _negotiationEvents.tryEmit(
                    PhyNegotiationEvent(
                        peerId = packet.sourceId,
                        previousPhy = previous,
                        targetPhy = packet.requestedPhy,
                        reason = "Peer ACK confirmed",
                        timestampMs = packet.timestampMs
                    )
                )
            }
            return@withLock packet
        }

        // Evaluating incoming request from peer
        val capability = peerCapabilities[packet.sourceId] ?: PhyCapability(packet.sourceId)
        val canAccept = capability.isModeSupported(packet.requestedPhy)

        if (canAccept) {
            val previous = _peerPhyMap.value[packet.sourceId] ?: BlePhyMode.LE_1M
            _peerPhyMap.value = _peerPhyMap.value + (packet.sourceId to packet.requestedPhy)
            _negotiationEvents.tryEmit(
                PhyNegotiationEvent(
                    peerId = packet.sourceId,
                    previousPhy = previous,
                    targetPhy = packet.requestedPhy,
                    reason = "Accepted peer migration request",
                    timestampMs = packet.timestampMs
                )
            )
        }

        PhyNegotiationPacket(
            sourceId = localNodeId,
            targetId = packet.sourceId,
            requestedPhy = packet.requestedPhy,
            sequenceNumber = packet.sequenceNumber,
            timestampMs = packet.timestampMs,
            isAcknowledgement = true,
            accepted = canAccept
        )
    }

    /**
     * Computes effective RSSI by factoring in elevation differences and terrain shadowing.
     */
    private fun calculateEffectiveRssi(rawRssi: Int, elevationDeltaMeters: Double, profile: TerrainShadowingProfile): Int {
        var penaltyDb = profile.diffractionMarginDb

        // Elevational steepness penalty (e.g. mountain switchback elevation gradient)
        if (kotlin.math.abs(elevationDeltaMeters) > 30.0) {
            penaltyDb += kotlin.math.min(10.0, (kotlin.math.abs(elevationDeltaMeters) - 30.0) * 0.1)
        }

        return (rawRssi - penaltyDb).toInt()
    }

    /**
     * Selects candidate PHY mode based on current mode, thresholds, and hysteresis margins.
     */
    private fun selectCandidatePhy(
        current: BlePhyMode,
        effectiveRssi: Int,
        lossRatio: Double,
        capability: PhyCapability
    ): BlePhyMode {
        return when (current) {
            BlePhyMode.CODED_S8 -> {
                // Must have healthy hysteresis margin to step up to higher throughput
                if (effectiveRssi >= (-92 + hysteresisMarginDb) && lossRatio <= 0.05 && capability.isModeSupported(BlePhyMode.CODED_S2)) {
                    if (effectiveRssi >= (-82 + hysteresisMarginDb) && lossRatio <= 0.03 && capability.isModeSupported(BlePhyMode.LE_1M)) {
                        BlePhyMode.LE_1M
                    } else {
                        BlePhyMode.CODED_S2
                    }
                } else {
                    BlePhyMode.CODED_S8
                }
            }
            BlePhyMode.CODED_S2 -> {
                if ((effectiveRssi < -92 || lossRatio >= 0.20) && capability.isModeSupported(BlePhyMode.CODED_S8)) {
                    BlePhyMode.CODED_S8
                } else if (effectiveRssi >= (-82 + hysteresisMarginDb) && lossRatio <= 0.03 && capability.isModeSupported(BlePhyMode.LE_1M)) {
                    BlePhyMode.LE_1M
                } else {
                    BlePhyMode.CODED_S2
                }
            }
            BlePhyMode.LE_1M -> {
                if ((effectiveRssi < -92 || lossRatio >= 0.20) && capability.isModeSupported(BlePhyMode.CODED_S8)) {
                    BlePhyMode.CODED_S8
                } else if ((effectiveRssi < -82 || lossRatio >= 0.12) && capability.isModeSupported(BlePhyMode.CODED_S2)) {
                    BlePhyMode.CODED_S2
                } else if (effectiveRssi >= (-70 + hysteresisMarginDb) && lossRatio <= 0.02 && capability.isModeSupported(BlePhyMode.LE_2M)) {
                    BlePhyMode.LE_2M
                } else {
                    BlePhyMode.LE_1M
                }
            }
            BlePhyMode.LE_2M -> {
                if ((effectiveRssi < -92 || lossRatio >= 0.20) && capability.isModeSupported(BlePhyMode.CODED_S8)) {
                    BlePhyMode.CODED_S8
                } else if ((effectiveRssi < -82 || lossRatio >= 0.12) && capability.isModeSupported(BlePhyMode.CODED_S2)) {
                    BlePhyMode.CODED_S2
                } else if ((effectiveRssi < -70 || lossRatio >= 0.05) && capability.isModeSupported(BlePhyMode.LE_1M)) {
                    BlePhyMode.LE_1M
                } else {
                    BlePhyMode.LE_2M
                }
            }
        }
    }
}
