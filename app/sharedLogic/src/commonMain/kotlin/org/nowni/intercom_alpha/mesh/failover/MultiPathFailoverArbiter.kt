package org.nowni.intercom_alpha.mesh.failover

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure KMP Multi-Path Link Quality Arbiter & Automated Handover State Machine.
 *
 * Dynamically arbitrates between offline BLE mesh and wide-area cellular/SFU cloud relay:
 * - Immediately escalates to [FailoverMode.MULTI_PATH_HEDGED] on link degradation (high ETX, loss, weak RSSI)
 *   to ensure zero audio dropout via dual-path transmission with receiver deduplication.
 * - Transitions to [FailoverMode.CLOUD_RELAY_ONLY] when BLE peers are completely lost or heartbeat times out.
 * - Applies configurable hysteresis dampening before de-escalating back to [FailoverMode.BLE_DIRECT]
 *   to eliminate rapid flapping and ping-pong state oscillation.
 *
 * Rules 2 & 3: 100% pure Kotlin Multiplatform with zero Java or platform dependencies.
 */
class MultiPathFailoverArbiter(
    val config: FailoverConfig = FailoverConfig()
) {
    private val mutex = Mutex()

    private var hasReceivedMeshMetrics: Boolean = false
    private var meshPeerCount: Int = 0
    private var averageEtx: Double = 1.0
    private var averageDeliveryRatio: Double = 1.0
    private var lowestRssiDbm: Int = -75
    private var lastBleActivityMs: Long = 0L
    private var isCloudConnected: Boolean = false

    private var currentMode: FailoverMode = FailoverMode.RESTRICTED_OFFLINE
    private var modeEnteredTimestampMs: Long = 0L
    private var totalHandovers: Long = 0L
    private var hedgedPacketsCount: Long = 0L
    private var forcedMode: FailoverMode? = null

    // Hysteresis tracking
    private var stableBleStartTimeMs: Long? = null

    private val _metrics = MutableStateFlow(FailoverMetrics())
    val metrics: StateFlow<FailoverMetrics> = _metrics.asStateFlow()

    /**
     * Updates local BLE mesh link telemetry.
     */
    suspend fun updateMeshMetrics(
        peerCount: Int,
        avgEtx: Double,
        deliveryRatio: Double,
        minRssiDbm: Int = -75,
        currentTimeMs: Long
    ): FailoverMode = mutex.withLock {
        hasReceivedMeshMetrics = true
        meshPeerCount = peerCount
        averageEtx = avgEtx
        averageDeliveryRatio = deliveryRatio
        lowestRssiDbm = minRssiDbm
        if (peerCount > 0) {
            lastBleActivityMs = currentTimeMs
        }
        recomputeState(currentTimeMs)
    }

    /**
     * Updates cellular/SFU cloud relay connection state.
     */
    suspend fun updateCloudStatus(
        connected: Boolean,
        currentTimeMs: Long
    ): FailoverMode = mutex.withLock {
        isCloudConnected = connected
        recomputeState(currentTimeMs)
    }

    /**
     * Manually overrides automatic arbitration, or sets null to resume autonomous arbitration.
     */
    suspend fun forceMode(
        mode: FailoverMode?,
        currentTimeMs: Long
    ): FailoverMode = mutex.withLock {
        val wasForced = forcedMode != null
        forcedMode = mode
        if (mode == null) {
            stableBleStartTimeMs = null
        }
        recomputeState(currentTimeMs, bypassHysteresis = (wasForced && mode == null))
    }

    /**
     * Increments count of voice frames hedged across dual transports.
     */
    suspend fun recordHedgedPacket() = mutex.withLock {
        hedgedPacketsCount++
        publishMetricsLocked(currentTimeMs = modeEnteredTimestampMs)
    }

    /**
     * Periodic evaluation tick, e.g. checking heartbeat timeouts and hysteresis windows.
     */
    suspend fun evaluateState(currentTimeMs: Long): FailoverMode = mutex.withLock {
        recomputeState(currentTimeMs)
    }

    private fun recomputeState(currentTimeMs: Long, bypassHysteresis: Boolean = false): FailoverMode {
        val isFirstEval = (modeEnteredTimestampMs == 0L)
        if (isFirstEval) {
            modeEnteredTimestampMs = currentTimeMs
        }

        val targetMode: FailoverMode = forcedMode ?: computeTargetMode(currentTimeMs, bypassHysteresis)

        if (targetMode != currentMode) {
            val isInitialBoot = isFirstEval || (currentMode == FailoverMode.RESTRICTED_OFFLINE && totalHandovers == 0L)
            currentMode = targetMode
            modeEnteredTimestampMs = currentTimeMs
            if (!isInitialBoot) {
                totalHandovers++
            }
            // Reset hysteresis counter when leaving
            if (targetMode != FailoverMode.BLE_DIRECT) {
                stableBleStartTimeMs = null
            }
        }

        publishMetricsLocked(currentTimeMs)
        return currentMode
    }

    private fun computeTargetMode(currentTimeMs: Long, bypassHysteresis: Boolean = false): FailoverMode {
        val bleTimedOut = (stableBleStartTimeMs == null && lastBleActivityMs > 0L && (currentTimeMs - lastBleActivityMs) > config.bleTimeoutMs)
        val hasBlePeers = meshPeerCount > 0 && !bleTimedOut

        val isDegraded = hasBlePeers && (
            averageEtx > config.degradeEtxThreshold ||
            (1.0 - averageDeliveryRatio) > config.degradeLossRatioThreshold ||
            lowestRssiDbm < config.degradeRssiThreshold
        )

        // Case 0: Initialization before first mesh metrics received
        if (!hasReceivedMeshMetrics) {
            stableBleStartTimeMs = null
            return FailoverMode.RESTRICTED_OFFLINE
        }

        // Case 1: Neither transport available
        if (!hasBlePeers && !isCloudConnected) {
            stableBleStartTimeMs = null
            return FailoverMode.RESTRICTED_OFFLINE
        }

        // Case 2: Only Cloud available (out of BLE range or convoy split)
        if (!hasBlePeers && isCloudConnected) {
            stableBleStartTimeMs = null
            return FailoverMode.CLOUD_RELAY_ONLY
        }

        // Case 3: BLE available, but Cloud is unavailable
        if (hasBlePeers && !isCloudConnected) {
            stableBleStartTimeMs = null
            return FailoverMode.BLE_DIRECT
        }

        // Case 4: Both BLE and Cloud are available
        // If link is degraded, immediately hedge to prevent packet drops
        if (isDegraded) {
            stableBleStartTimeMs = null
            return FailoverMode.MULTI_PATH_HEDGED
        }

        // Link is healthy. Check hysteresis if we were in CLOUD_RELAY_ONLY or MULTI_PATH_HEDGED
        if (!bypassHysteresis && (currentMode == FailoverMode.CLOUD_RELAY_ONLY || currentMode == FailoverMode.MULTI_PATH_HEDGED)) {
            val stableStart = stableBleStartTimeMs
            if (stableStart == null) {
                stableBleStartTimeMs = currentTimeMs
                // Stay in current mode until hysteresis completes
                return currentMode
            } else if ((currentTimeMs - stableStart) < config.hysteresisDurationMs) {
                // Still waiting out the hysteresis period
                return currentMode
            } else {
                // Hysteresis elapsed cleanly, transition back to BLE_DIRECT
                stableBleStartTimeMs = null
                lastBleActivityMs = currentTimeMs
                return FailoverMode.BLE_DIRECT
            }
        }

        // Already in BLE_DIRECT or RESTRICTED_OFFLINE
        stableBleStartTimeMs = null
        return FailoverMode.BLE_DIRECT
    }

    private fun publishMetricsLocked(currentTimeMs: Long) {
        val bleTimedOut = (lastBleActivityMs > 0L && (currentTimeMs - lastBleActivityMs) > config.bleTimeoutMs)
        val hasBlePeers = meshPeerCount > 0 && !bleTimedOut
        val isDegraded = hasBlePeers && (
            averageEtx > config.degradeEtxThreshold ||
            (1.0 - averageDeliveryRatio) > config.degradeLossRatioThreshold ||
            lowestRssiDbm < config.degradeRssiThreshold
        )

        _metrics.value = FailoverMetrics(
            currentMode = currentMode,
            meshPeerCount = meshPeerCount,
            averageEtx = averageEtx,
            averageDeliveryRatio = averageDeliveryRatio,
            lastBleActivityMs = lastBleActivityMs,
            isCloudConnected = isCloudConnected,
            timeInCurrentModeMs = (currentTimeMs - modeEnteredTimestampMs).coerceAtLeast(0L),
            totalHandovers = totalHandovers,
            hedgedPacketsCount = hedgedPacketsCount,
            isBleDegraded = isDegraded
        )
    }
}
