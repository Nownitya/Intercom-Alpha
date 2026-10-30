package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.qos.QoSPacketClassifier
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Configuration parameters for [MeshCongestionGovernor].
 *
 * @property minAdvIntervalMs Minimum BLE advertising interval in milliseconds (default: 20ms).
 * @property maxAdvIntervalMs Maximum throttled BLE advertising interval in milliseconds (default: 250ms).
 * @property utilizationThrottleThreshold Channel utilization ratio threshold above which advertising throttling is applied (default: 0.70f or 70%).
 * @property highDensityNodeThreshold Node cluster density threshold above which probabilistic relay dampening is activated (default: 8 nodes).
 * @property minRelayProbability Absolute lower bound for non-emergency relay probability to prevent network partitions (default: 0.20f).
 * @property neighborRetentionWindowMs Sliding window duration in milliseconds to track active neighbor nodes (default: 10,000ms).
 * @property airtimeEvaluationWindowMs Sliding window duration in milliseconds to aggregate airtime events (default: 1,000ms).
 */
@Serializable
data class MeshCongestionGovernorConfig(
    val minAdvIntervalMs: Long = 20L,
    val maxAdvIntervalMs: Long = 250L,
    val utilizationThrottleThreshold: Float = 0.70f,
    val highDensityNodeThreshold: Int = 8,
    val minRelayProbability: Float = 0.20f,
    val neighborRetentionWindowMs: Long = 10_000L,
    val airtimeEvaluationWindowMs: Long = 1_000L
)

/**
 * Real-time diagnostic snapshot emitted by [MeshCongestionGovernor].
 */
@Serializable
data class MeshCongestionSnapshot(
    val channelUtilization: Float = 0.0f,
    val activeNodeCount: Int = 0,
    val currentAdvIntervalMs: Long = 20L,
    val currentRelayProbability: Float = 1.0f,
    val isThrottled: Boolean = false,
    val isHighDensity: Boolean = false,
    val totalPacketsEvaluated: Long = 0L,
    val totalPacketsRelayed: Long = 0L,
    val totalPacketsDampened: Long = 0L,
    val emergencyPacketsBypassed: Long = 0L,
    val lastEvaluationTimestampMs: Long = 0L
)

/**
 * Autonomous Fleet Mesh Congestion Governor & Dynamic BLE Advertising Interval Throttler.
 *
 * Implements SRS-NET-06 and ADR-020:
 * 1. Measures and tracks RF channel airtime utilization and local neighbor cluster density.
 * 2. Dynamically throttles BLE advertising intervals from [config.minAdvIntervalMs] (20ms)
 *    up to [config.maxAdvIntervalMs] (250ms) whenever channel utilization exceeds 70%.
 * 3. Enforces probabilistic flooding relay dampening for non-emergency packets in high-density
 *    clusters (neighbor count > 8) to suppress broadcast storm saturation.
 * 4. Strictly guarantees zero latency degradation and 100% bypass for life-safety emergency SOS frames.
 * 5. 100% pure Kotlin Multiplatform implementation (Rule 2 & Rule 3).
 *
 * @param config Configuration parameters.
 * @param customEmergencyPredicate Optional user-defined emergency packet detector.
 * @param randomSupplier Source of pseudorandom values in [0.0, 1.0) for deterministic unit testing.
 */
class MeshCongestionGovernor(
    val config: MeshCongestionGovernorConfig = MeshCongestionGovernorConfig(),
    private val customEmergencyPredicate: ((MeshPacket) -> Boolean)? = null,
    private val randomSupplier: () -> Double = { Random.nextDouble() }
) {
    private val mutex = Mutex()

    private class AirtimeEvent(
        val timestampMs: Long,
        val durationMicros: Long
    )

    private val neighbors = mutableMapOf<String, Long>()
    private val airtimeEvents = mutableListOf<AirtimeEvent>()

    private var explicitChannelUtilization: Float? = null
    private var explicitNodeCount: Int? = null

    private var totalPacketsEvaluated: Long = 0L
    private var totalPacketsRelayed: Long = 0L
    private var totalPacketsDampened: Long = 0L
    private var emergencyPacketsBypassed: Long = 0L

    private val _metrics = MutableStateFlow(
        MeshCongestionSnapshot(
            channelUtilization = 0.0f,
            activeNodeCount = 0,
            currentAdvIntervalMs = config.minAdvIntervalMs,
            currentRelayProbability = 1.0f,
            isThrottled = false,
            isHighDensity = false,
            lastEvaluationTimestampMs = currentTimeMillis()
        )
    )
    val metrics: StateFlow<MeshCongestionSnapshot> = _metrics.asStateFlow()

    val currentAdvIntervalMs: Long
        get() = _metrics.value.currentAdvIntervalMs

    val currentRelayProbability: Float
        get() = _metrics.value.currentRelayProbability

    val isThrottled: Boolean
        get() = _metrics.value.isThrottled

    val isHighDensity: Boolean
        get() = _metrics.value.isHighDensity

    /**
     * Records presence of a neighboring mesh node from advertising beacons or incoming packets.
     *
     * @param nodeId Unique identifier of the neighbor peer.
     * @param rssi Observed signal strength in dBm.
     * @param timestampMs Timestamp of observation.
     */
    suspend fun recordNeighborPresence(
        nodeId: String,
        rssi: Int = 0,
        timestampMs: Long = currentTimeMillis()
    ) = mutex.withLock {
        neighbors[nodeId] = timestampMs
        updateMetricsSnapshotInternal(timestampMs)
    }

    /**
     * Records local RF medium airtime usage (Tx or Rx burst) in microseconds.
     *
     * @param durationMicros Airtime duration in microseconds.
     * @param timestampMs Epoch timestamp in milliseconds.
     */
    suspend fun recordAirtimeUsage(
        durationMicros: Long,
        timestampMs: Long = currentTimeMillis()
    ) = mutex.withLock {
        airtimeEvents.add(AirtimeEvent(timestampMs, durationMicros))
        updateMetricsSnapshotInternal(timestampMs)
    }

    /**
     * Updates channel utilization and optional node count explicitly from external diagnostic engines.
     *
     * @param channelUtilization Measured channel airtime utilization in [0.0, 1.0].
     * @param activeNodeCount Optional override for active neighbor cluster count.
     * @param timestampMs Epoch timestamp in milliseconds.
     */
    suspend fun updateMetrics(
        channelUtilization: Float,
        activeNodeCount: Int? = null,
        timestampMs: Long = currentTimeMillis()
    ) = mutex.withLock {
        explicitChannelUtilization = channelUtilization.coerceIn(0.0f, 1.0f)
        if (activeNodeCount != null) {
            explicitNodeCount = activeNodeCount.coerceAtLeast(0)
        }
        updateMetricsSnapshotInternal(timestampMs)
    }

    /**
     * Clears explicitly set utilization and node count overrides, restoring automatic measurement.
     */
    suspend fun clearExplicitOverrides(timestampMs: Long = currentTimeMillis()) = mutex.withLock {
        explicitChannelUtilization = null
        explicitNodeCount = null
        updateMetricsSnapshotInternal(timestampMs)
    }

    /**
     * Determines whether an incoming flooded mesh packet should be forwarded/relayed by this node.
     *
     * In high-density clusters or high-utilization conditions, non-emergency packets are probabilistically
     * dampened to prevent broadcast storms. Emergency SOS frames unconditionally bypass dampening with zero delay.
     *
     * @param packet Packet candidate for relay.
     * @param now Current timestamp in milliseconds.
     * @return `true` if packet should be relayed; `false` if dropped/suppressed.
     */
    suspend fun shouldRelay(packet: MeshPacket, now: Long = currentTimeMillis()): Boolean {
        if (isEmergencyPacket(packet)) {
            mutex.withLock {
                emergencyPacketsBypassed++
                totalPacketsEvaluated++
                totalPacketsRelayed++
                updateMetricsSnapshotInternal(now)
            }
            return true
        }

        return mutex.withLock {
            totalPacketsEvaluated++
            val relayProb = _metrics.value.currentRelayProbability
            val roll = randomSupplier()

            val shouldForward = roll <= relayProb
            if (shouldForward) {
                totalPacketsRelayed++
            } else {
                totalPacketsDampened++
            }
            updateMetricsSnapshotInternal(now)
            shouldForward
        }
    }

    /**
     * Fast non-suspending check for relay admission against current snapshot metrics.
     * Emergency frames are immediately passed.
     */
    fun shouldRelayFast(packet: MeshPacket): Boolean {
        if (isEmergencyPacket(packet)) return true
        val roll = randomSupplier()
        return roll <= _metrics.value.currentRelayProbability
    }

    /**
     * Checks if the packet contains an emergency signal (crash alert, geofence alarm, eCall SOS).
     */
    fun isEmergencyPacket(packet: MeshPacket): Boolean {
        customEmergencyPredicate?.let { if (it(packet)) return true }
        val root = unwrapRelay(packet)
        if (root is MeshPacket.Control) {
            if (root.type == ControlType.CRASH_ALERT || root.type == ControlType.GEOFENCE_ALARM) {
                return true
            }
        }
        return QoSPacketClassifier.classify(root) == QoSPriority.EMERGENCY_CRITICAL
    }

    /**
     * Calculates the dynamic BLE advertising interval in milliseconds based on RF channel utilization
     * and local neighbor density.
     *
     * When utilization <= 70%, maintains baseline advertising interval (20ms, mildly scaled with density).
     * When utilization > 70%, throttles dynamically up to [config.maxAdvIntervalMs] (250ms).
     */
    fun calculateAdvInterval(channelUtilization: Float, activeNodeCount: Int): Long {
        val clampedUtil = channelUtilization.coerceIn(0.0f, 1.0f)
        val minInterval = config.minAdvIntervalMs
        val maxInterval = config.maxAdvIntervalMs
        val threshold = config.utilizationThrottleThreshold

        val excessDensity = (activeNodeCount - config.highDensityNodeThreshold).coerceAtLeast(0)
        val densityRatio = (excessDensity / 16.0f).coerceIn(0.0f, 1.0f)
        val baseInterval = minInterval + (30L * densityRatio).roundToLong()

        return if (clampedUtil <= threshold) {
            baseInterval.coerceIn(minInterval, maxInterval)
        } else {
            val excessRatio = (clampedUtil - threshold) / (1.0f - threshold)
            val throttledSpan = maxInterval - baseInterval
            (baseInterval + (throttledSpan * excessRatio).roundToLong()).coerceIn(minInterval, maxInterval)
        }
    }

    /**
     * Calculates probabilistic relay acceptance ratio in [minRelayProbability, 1.0].
     *
     * In sparse clusters and low channel utilization, returns 1.0 (100% forwarding).
     * In high-density clusters (> 8 nodes) or channel utilization > 70%, scales down to prevent
     * redundant packet broadcast collisions.
     */
    fun calculateRelayProbability(channelUtilization: Float, activeNodeCount: Int): Float {
        val clampedUtil = channelUtilization.coerceIn(0.0f, 1.0f)
        val highDensityThreshold = config.highDensityNodeThreshold
        val utilThreshold = config.utilizationThrottleThreshold

        if (activeNodeCount <= highDensityThreshold && clampedUtil <= utilThreshold) {
            return 1.0f
        }

        val densityFactor = if (activeNodeCount > highDensityThreshold) {
            (highDensityThreshold.toFloat() / activeNodeCount.toFloat()).coerceIn(0.0f, 1.0f)
        } else {
            1.0f
        }

        val utilFactor = if (clampedUtil > utilThreshold) {
            val excess = (clampedUtil - utilThreshold) / (1.0f - utilThreshold)
            1.0f - (0.5f * excess)
        } else {
            1.0f
        }

        val combined = densityFactor * utilFactor
        return combined.coerceIn(config.minRelayProbability, 1.0f)
    }

    /**
     * Resets governor state, neighbor lists, airtime history, and counters.
     */
    suspend fun reset(timestampMs: Long = currentTimeMillis()) = mutex.withLock {
        neighbors.clear()
        airtimeEvents.clear()
        explicitChannelUtilization = null
        explicitNodeCount = null
        totalPacketsEvaluated = 0L
        totalPacketsRelayed = 0L
        totalPacketsDampened = 0L
        emergencyPacketsBypassed = 0L
        updateMetricsSnapshotInternal(timestampMs)
    }

    private fun updateMetricsSnapshotInternal(now: Long) {
        pruneExpiredInternal(now)

        val nodeCount = explicitNodeCount ?: neighbors.size
        val utilization = explicitChannelUtilization ?: calculateMeasuredAirtimeUtilization(now)

        val advInterval = calculateAdvInterval(utilization, nodeCount)
        val relayProb = calculateRelayProbability(utilization, nodeCount)
        val isThrottled = utilization > config.utilizationThrottleThreshold || advInterval > config.minAdvIntervalMs
        val isHighDensity = nodeCount > config.highDensityNodeThreshold

        _metrics.value = MeshCongestionSnapshot(
            channelUtilization = utilization,
            activeNodeCount = nodeCount,
            currentAdvIntervalMs = advInterval,
            currentRelayProbability = relayProb,
            isThrottled = isThrottled,
            isHighDensity = isHighDensity,
            totalPacketsEvaluated = totalPacketsEvaluated,
            totalPacketsRelayed = totalPacketsRelayed,
            totalPacketsDampened = totalPacketsDampened,
            emergencyPacketsBypassed = emergencyPacketsBypassed,
            lastEvaluationTimestampMs = now
        )
    }

    private fun calculateMeasuredAirtimeUtilization(now: Long): Float {
        if (airtimeEvents.isEmpty()) return 0.0f
        val windowMicros = config.airtimeEvaluationWindowMs * 1_000.0f
        val totalAirtimeMicros = airtimeEvents.sumOf { it.durationMicros }.toFloat()
        return (totalAirtimeMicros / windowMicros).coerceIn(0.0f, 1.0f)
    }

    private fun pruneExpiredInternal(now: Long) {
        val neighborCutoff = now - config.neighborRetentionWindowMs
        val expired = neighbors.filter { it.value < neighborCutoff }.keys
        for (key in expired) {
            neighbors.remove(key)
        }

        val airtimeCutoff = now - config.airtimeEvaluationWindowMs
        airtimeEvents.removeAll { it.timestampMs < airtimeCutoff }
    }

    private fun unwrapRelay(packet: MeshPacket): MeshPacket {
        var curr = packet
        while (curr is MeshPacket.Relay) {
            curr = curr.packet
        }
        return curr
    }
}

