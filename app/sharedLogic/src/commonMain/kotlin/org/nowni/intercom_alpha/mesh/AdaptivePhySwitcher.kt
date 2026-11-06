package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.phy.BlePhyMode

/**
 * Configuration parameters for [AdaptivePhySwitcher].
 *
 * @property rssiUpgradeThresholdDbm Minimum RSSI threshold (-65 dBm) required to upgrade to LE 2M.
 * @property rssiDowngradeThresholdDbm Minimum RSSI floor (-85 dBm) below which PHY steps down to LE Coded S8.
 * @property scoreUpgradeThreshold Minimum Q_link score (85) required to upgrade to LE 2M.
 * @property scoreDowngradeThreshold Q_link score floor (70) below which LE 2M downgrades to LE 1M.
 * @property packetLossDowngradeThreshold Packet loss ratio threshold (0.15 or 15%) triggering step down to LE Coded S8.
 * @property packetLossUpgradeThreshold Maximum packet loss ratio (0.05 or 5%) permitted for upgrading to LE 2M.
 * @property samplesRequiredForUpgrade Number of consecutive consistent samples required before stepping up PHY mode (hysteresis).
 * @property samplesRequiredForDowngrade Number of consecutive degraded samples required before stepping down PHY mode.
 * @property cooldownPeriodMs Minimum duration in milliseconds between PHY migrations per peer to prevent flapping.
 * @property defaultPhy Default baseline PHY mode (LE 1M).
 */
@Serializable
data class AdaptivePhyConfig(
    val rssiUpgradeThresholdDbm: Int = -65,
    val rssiDowngradeThresholdDbm: Int = -85,
    val scoreUpgradeThreshold: Int = 85,
    val scoreDowngradeThreshold: Int = 70,
    val packetLossDowngradeThreshold: Double = 0.15,
    val packetLossUpgradeThreshold: Double = 0.05,
    val samplesRequiredForUpgrade: Int = 2,
    val samplesRequiredForDowngrade: Int = 1,
    val cooldownPeriodMs: Long = 3_000L,
    val defaultPhy: BlePhyMode = BlePhyMode.LE_1M
)

/**
 * Event triggered whenever a peer's or local transceiver's PHY mode changes.
 */
@Serializable
data class PhySwitchEvent(
    val peerId: String,
    val previousPhy: BlePhyMode,
    val targetPhy: BlePhyMode,
    val reason: String,
    val timestampMs: Long
)

/**
 * Real-time diagnostic snapshot of the [AdaptivePhySwitcher] engine.
 */
@Serializable
data class PhySwitcherSnapshot(
    val activePhy: BlePhyMode = BlePhyMode.LE_1M,
    val activePhyName: String = "LE_1M",
    val shortPillLabel: String = "1M",
    val peerPhyModes: Map<String, BlePhyMode> = emptyMap(),
    val totalPhySwitches: Long = 0L,
    val lastSwitchEvent: PhySwitchEvent? = null,
    val lastEvaluationTimestampMs: Long = 0L
)

/**
 * Adaptive Multi-PHY Switcher Engine for Bluetooth 5.0 (LE 1M, LE 2M, LE Coded S8).
 *
 * Implements SRS-NET-07 and ADR-020:
 * 1. Evaluates live link quality scores ($Q_{link}$), RSSI, and packet loss ratios.
 * 2. Dynamically negotiates PHY modes:
 *    - LE 2M: High throughput (2000 kbps) for close proximity convoys ($Q_{link} > 85$ and RSSI $> -65$ dBm).
 *    - LE 1M: Standard baseline modulation (1000 kbps).
 *    - LE Coded S8: 4x long range (125 kbps with FEC) when RSSI drops below $-85$ dBm or packet drop $> 15\%$.
 * 3. Enforces asymmetric hysteresis dampening and cooldown timers to prevent rapid ping-pong flapping.
 * 4. 100% pure Kotlin Multiplatform implementation (Rules 2 & 3).
 */
class AdaptivePhySwitcher(
    val config: AdaptivePhyConfig = AdaptivePhyConfig()
) {
    private val mutex = Mutex()

    private val _activePhy = MutableStateFlow(config.defaultPhy)
    val activePhy: StateFlow<BlePhyMode> = _activePhy.asStateFlow()

    private val _peerPhyModes = MutableStateFlow<Map<String, BlePhyMode>>(emptyMap())
    val peerPhyModes: StateFlow<Map<String, BlePhyMode>> = _peerPhyModes.asStateFlow()

    private val _switchEvents = MutableSharedFlow<PhySwitchEvent>(extraBufferCapacity = 64)
    val switchEvents: SharedFlow<PhySwitchEvent> = _switchEvents.asSharedFlow()

    private var totalPhySwitches = 0L
    private var lastSwitchEvent: PhySwitchEvent? = null
    private var lastEvaluationTimestampMs = 0L

    private class PeerTracker(
        val peerId: String,
        var currentPhy: BlePhyMode,
        var lastSwitchTimestampMs: Long = 0L,
        var consecutiveUpgradeSamples: Int = 0,
        var consecutiveDowngradeSamples: Int = 0,
        var candidatePhy: BlePhyMode? = null,
        var lastScore: Int = 0,
        var lastRssiDbm: Int = -75,
        var lastPacketLossRatio: Double = 0.0
    )

    private val trackers = mutableMapOf<String, PeerTracker>()

    /**
     * Returns the currently assigned PHY mode for a specific peer.
     */
    fun getPeerPhy(peerId: String): BlePhyMode {
        return _peerPhyModes.value[peerId] ?: config.defaultPhy
    }

    /**
     * Evaluates link metrics for a specific peer and triggers a PHY migration if criteria and hysteresis are met.
     *
     * @return `true` if a PHY migration occurred, `false` otherwise.
     */
    suspend fun evaluatePeerLink(
        peerId: String,
        linkQualityScore: Int,
        rssiDbm: Int,
        packetLossRatio: Double,
        timestampMs: Long = currentTimeMillis()
    ): Boolean = mutex.withLock {
        lastEvaluationTimestampMs = timestampMs
        val tracker = trackers.getOrPut(peerId) {
            PeerTracker(peerId = peerId, currentPhy = config.defaultPhy)
        }

        tracker.lastScore = linkQualityScore
        tracker.lastRssiDbm = rssiDbm
        tracker.lastPacketLossRatio = packetLossRatio

        // Determine recommended PHY mode based on RF conditions
        val recommendedPhy = when {
            // Extreme range / alpine shadow condition -> LE Coded S8
            rssiDbm < config.rssiDowngradeThresholdDbm ||
                packetLossRatio > config.packetLossDowngradeThreshold ||
                linkQualityScore < 40 -> BlePhyMode.CODED_S8

            // Close proximity high throughput condition -> LE 2M
            linkQualityScore > config.scoreUpgradeThreshold &&
                rssiDbm > config.rssiUpgradeThresholdDbm &&
                packetLossRatio <= config.packetLossUpgradeThreshold -> BlePhyMode.LE_2M

            // Standard baseline condition -> LE 1M
            else -> BlePhyMode.LE_1M
        }

        if (recommendedPhy == tracker.currentPhy) {
            tracker.consecutiveUpgradeSamples = 0
            tracker.consecutiveDowngradeSamples = 0
            tracker.candidatePhy = null
            return@withLock false
        }

        // Hysteresis cooldown check: prevent rapid flapping if within cooldown window
        if (tracker.lastSwitchTimestampMs > 0L &&
            timestampMs - tracker.lastSwitchTimestampMs < config.cooldownPeriodMs
        ) {
            return@withLock false
        }

        val isUpgrade = recommendedPhy.bitRateKbps > tracker.currentPhy.bitRateKbps

        if (isUpgrade) {
            tracker.consecutiveDowngradeSamples = 0
            if (tracker.candidatePhy == recommendedPhy) {
                tracker.consecutiveUpgradeSamples++
            } else {
                tracker.candidatePhy = recommendedPhy
                tracker.consecutiveUpgradeSamples = 1
            }

            if (tracker.consecutiveUpgradeSamples >= config.samplesRequiredForUpgrade) {
                executeSwitch(
                    tracker = tracker,
                    targetPhy = recommendedPhy,
                    reason = "Upgrade: Q_link=$linkQualityScore, RSSI=${rssiDbm}dBm, Loss=${(packetLossRatio * 100).toInt()}%",
                    timestampMs = timestampMs
                )
                return@withLock true
            }
        } else {
            // Downgrade (safety priority to protect voice playout & emergency delivery)
            tracker.consecutiveUpgradeSamples = 0
            if (tracker.candidatePhy == recommendedPhy) {
                tracker.consecutiveDowngradeSamples++
            } else {
                tracker.candidatePhy = recommendedPhy
                tracker.consecutiveDowngradeSamples = 1
            }

            if (tracker.consecutiveDowngradeSamples >= config.samplesRequiredForDowngrade) {
                executeSwitch(
                    tracker = tracker,
                    targetPhy = recommendedPhy,
                    reason = "Downgrade: Q_link=$linkQualityScore, RSSI=${rssiDbm}dBm, Loss=${(packetLossRatio * 100).toInt()}%",
                    timestampMs = timestampMs
                )
                return@withLock true
            }
        }

        return@withLock false
    }

    private fun executeSwitch(
        tracker: PeerTracker,
        targetPhy: BlePhyMode,
        reason: String,
        timestampMs: Long
    ) {
        val previousPhy = tracker.currentPhy
        tracker.currentPhy = targetPhy
        tracker.lastSwitchTimestampMs = timestampMs
        tracker.consecutiveUpgradeSamples = 0
        tracker.consecutiveDowngradeSamples = 0
        tracker.candidatePhy = null

        totalPhySwitches++
        val event = PhySwitchEvent(
            peerId = tracker.peerId,
            previousPhy = previousPhy,
            targetPhy = targetPhy,
            reason = reason,
            timestampMs = timestampMs
        )
        lastSwitchEvent = event
        _switchEvents.tryEmit(event)

        val updatedMap = trackers.mapValues { it.value.currentPhy }
        _peerPhyModes.value = updatedMap

        // Update cluster active PHY mode: choose the lowest PHY to protect farthest peer in broadcast
        val clusterPhy = updatedMap.values.minByOrNull { it.bitRateKbps } ?: config.defaultPhy
        _activePhy.value = clusterPhy
    }

    /**
     * Resets all peer trackers and restores default PHY modes.
     */
    suspend fun reset() = mutex.withLock {
        trackers.clear()
        totalPhySwitches = 0L
        lastSwitchEvent = null
        lastEvaluationTimestampMs = 0L
        _peerPhyModes.value = emptyMap()
        _activePhy.value = config.defaultPhy
    }

    /**
     * Returns a point-in-time snapshot of the switcher engine telemetry.
     */
    fun getSnapshot(): PhySwitcherSnapshot {
        val active = _activePhy.value
        val shortLabel = when (active) {
            BlePhyMode.LE_2M -> "2M"
            BlePhyMode.LE_1M -> "1M"
            BlePhyMode.CODED_S2 -> "S2"
            BlePhyMode.CODED_S8 -> "S8"
        }

        return PhySwitcherSnapshot(
            activePhy = active,
            activePhyName = active.name,
            shortPillLabel = shortLabel,
            peerPhyModes = _peerPhyModes.value,
            totalPhySwitches = totalPhySwitches,
            lastSwitchEvent = lastSwitchEvent,
            lastEvaluationTimestampMs = lastEvaluationTimestampMs
        )
    }
}
