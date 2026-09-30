package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Discrete proximity distance zones for cockpit rider radar and proximity alerts.
 */
@Serializable
enum class ProximityZone {
    /** Rider is within pack/formation riding distance (< 15 meters). */
    NEAR,

    /** Rider is within line-of-sight visual convoy distance (15m .. < 60 meters). */
    MEDIUM,

    /** Rider is trailing at the extended edge of direct BLE range (60m .. < 150 meters). */
    FAR,

    /** Rider is disconnected, timed out, or exceeds direct RF reception range (>= 150 meters). */
    OUT_OF_RANGE
}

/**
 * Real-time tracking representation of a remote rider for radar visualization.
 */
@Serializable
data class RadarTarget(
    val peerId: String,
    val displayName: String? = null,
    val estimatedDistanceMeters: Double,
    val rssi: Int,
    val smoothedRssi: Double,
    val linkMarginDb: Double,
    val zone: ProximityZone,
    val hopCount: Int = 1,
    val signalStrengthPercent: Int = 0,
    val isApproaching: Boolean = false,
    val lastSeenTimestamp: Long = 0L
)

/**
 * Immutable snapshot of all visible riders in the mesh radar.
 */
@Serializable
data class RadarSnapshot(
    val timestamp: Long,
    val targets: List<RadarTarget> = emptyList(),
    val closestTarget: RadarTarget? = null,
    val activeRidersCount: Int = 0,
    val zoneCounts: Map<ProximityZone, Int> = emptyMap()
)

/**
 * Low-Power BLE Proximity Radar Engine.
 *
 * Translates RF physical layer metrics (RSSI, link margins, multi-hop relay count)
 * into human-actionable proximity zones (NEAR, MEDIUM, FAR, OUT_OF_RANGE) with
 * smoothed distance estimation and distance trend detection (approaching vs receding).
 *
 * Emits reactive snapshots via [radarFlow] for Jetpack Compose and native SwiftUI cockpit rendering.
 *
 * @param referenceRssiAtOneMeter Calibrated RSSI at 1 meter open air (dBm). Default: -59.0 dBm (Class 2 BLE).
 * @param pathLossExponent Environmental path loss exponent (n). Default: 2.5 (motorcycle convoy on road/highway).
 * @param receiverSensitivityDb Radio receiver sensitivity floor (dBm). Default: -93.0 dBm.
 * @param nearThresholdMeters Distance boundary for [ProximityZone.NEAR]. Default: 15.0m.
 * @param mediumThresholdMeters Distance boundary for [ProximityZone.MEDIUM]. Default: 60.0m.
 * @param farThresholdMeters Distance boundary for [ProximityZone.FAR]. Default: 150.0m.
 * @param staleTimeoutMs Milliseconds of silence before marking peer as [ProximityZone.OUT_OF_RANGE]. Default: 10,000ms.
 */
class ProximityRadar(
    val referenceRssiAtOneMeter: Double = -59.0,
    val pathLossExponent: Double = 2.5,
    val receiverSensitivityDb: Double = -93.0,
    val nearThresholdMeters: Double = 15.0,
    val mediumThresholdMeters: Double = 60.0,
    val farThresholdMeters: Double = 150.0,
    val staleTimeoutMs: Long = 10_000L
) {
    private val mutex = Mutex()

    private class InternalTarget(
        val peerId: String,
        var displayName: String? = null,
        var currentRssi: Int = -70,
        var smoothedRssi: Double = -70.0,
        var hopCount: Int = 1,
        var isApproaching: Boolean = false,
        var lastSeenTimestamp: Long = 0L
    )

    private val internalTargets = mutableMapOf<String, InternalTarget>()

    private val _radarFlow = MutableStateFlow(
        RadarSnapshot(
            timestamp = currentTimeMillis(),
            targets = emptyList(),
            closestTarget = null,
            activeRidersCount = 0,
            zoneCounts = emptyMap()
        )
    )

    /**
     * Reactive StateFlow stream observed by Compose and SwiftUI views for cockpit radar rendering.
     */
    val radarFlow: StateFlow<RadarSnapshot> = _radarFlow.asStateFlow()

    /**
     * Updates radio metrics for [peerId] from an incoming BLE packet or advertisement beacon.
     */
    suspend fun updatePeerRssi(
        peerId: String,
        rssi: Int,
        hopCount: Int = 1,
        displayName: String? = null,
        timestamp: Long = currentTimeMillis()
    ) {
        mutex.withLock {
            val target = internalTargets.getOrPut(peerId) {
                InternalTarget(
                    peerId = peerId,
                    displayName = displayName,
                    currentRssi = rssi,
                    smoothedRssi = rssi.toDouble(),
                    hopCount = max(1, hopCount),
                    lastSeenTimestamp = timestamp
                )
            }

            if (displayName != null) {
                target.displayName = displayName
            }

            target.currentRssi = rssi
            target.hopCount = max(1, hopCount)

            val prevSmoothed = target.smoothedRssi
            // Exponential smoothing (alpha = 0.25)
            target.smoothedRssi = 0.75 * prevSmoothed + 0.25 * rssi.toDouble()

            // Directional trend detection: positive delta means increasing signal strength / closing distance
            val delta = target.smoothedRssi - prevSmoothed
            if (delta > 0.4) {
                target.isApproaching = true
            } else if (delta < -0.4) {
                target.isApproaching = false
            }

            target.lastSeenTimestamp = timestamp

            publishSnapshotLocked(timestamp)
        }
    }

    /**
     * Synchronizes radar with aggregated network telemetry from [MeshDiagnosticsReport].
     */
    suspend fun updateFromDiagnostics(
        report: MeshDiagnosticsReport,
        displayNames: Map<String, String> = emptyMap(),
        timestamp: Long = currentTimeMillis()
    ) {
        mutex.withLock {
            for (stat in report.peerStats) {
                val target = internalTargets.getOrPut(stat.peerId) {
                    InternalTarget(
                        peerId = stat.peerId,
                        displayName = displayNames[stat.peerId],
                        currentRssi = stat.currentRssi,
                        smoothedRssi = stat.smoothedRssi,
                        hopCount = max(1, stat.lastHopCount),
                        lastSeenTimestamp = stat.lastSeenTimestamp
                    )
                }

                if (displayNames.containsKey(stat.peerId)) {
                    target.displayName = displayNames[stat.peerId]
                }

                target.currentRssi = stat.currentRssi
                target.smoothedRssi = stat.smoothedRssi
                target.hopCount = max(1, stat.lastHopCount)
                target.lastSeenTimestamp = stat.lastSeenTimestamp
            }

            publishSnapshotLocked(timestamp)
        }
    }

    /**
     * Evaluates all tracked peers and updates proximity state, marking timed-out peers as OUT_OF_RANGE.
     */
    suspend fun pruneStalePeers(now: Long = currentTimeMillis()) {
        mutex.withLock {
            publishSnapshotLocked(now)
        }
    }

    /**
     * Removes a peer from active radar tracking (e.g. when leaving group).
     */
    suspend fun removePeer(peerId: String, now: Long = currentTimeMillis()) {
        mutex.withLock {
            if (internalTargets.remove(peerId) != null) {
                publishSnapshotLocked(now)
            }
        }
    }

    /**
     * Clears all radar targets.
     */
    suspend fun clear(now: Long = currentTimeMillis()) {
        mutex.withLock {
            internalTargets.clear()
            publishSnapshotLocked(now)
        }
    }

    /**
     * Computes estimated physical distance in meters using Log-Distance Path Loss model:
     *   d = 10 ^ ((A - RSSI) / (10 * n))
     */
    fun estimateDistance(rssi: Double): Double {
        if (rssi >= referenceRssiAtOneMeter) {
            return 1.0
        }
        val exponent = (referenceRssiAtOneMeter - rssi) / (10.0 * pathLossExponent)
        val distance = 10.0.pow(exponent)
        return max(0.5, round(distance * 10.0) / 10.0)
    }

    /**
     * Computes RF link margin relative to receiver sensitivity floor.
     */
    fun calculateLinkMargin(rssi: Double): Double {
        return round((rssi - receiverSensitivityDb) * 10.0) / 10.0
    }

    /**
     * Normalizes RSSI into a user-friendly percentage (0% to 100%).
     */
    fun calculateSignalStrengthPercent(rssi: Double): Int {
        val minRssi = receiverSensitivityDb // -93 dBm
        val maxRssi = -50.0                 // Optimal 0.5m BLE signal
        if (rssi <= minRssi) return 0
        if (rssi >= maxRssi) return 100
        val ratio = (rssi - minRssi) / (maxRssi - minRssi)
        return (ratio * 100.0).roundToInt().coerceIn(0, 100)
    }

    /**
     * Maps physical distance and freshness into discrete [ProximityZone].
     */
    fun classifyZone(distanceMeters: Double, isStale: Boolean): ProximityZone {
        if (isStale) return ProximityZone.OUT_OF_RANGE
        return when {
            distanceMeters < nearThresholdMeters -> ProximityZone.NEAR
            distanceMeters < mediumThresholdMeters -> ProximityZone.MEDIUM
            distanceMeters < farThresholdMeters -> ProximityZone.FAR
            else -> ProximityZone.OUT_OF_RANGE
        }
    }

    private fun publishSnapshotLocked(now: Long) {
        val targets = internalTargets.values.map { internal ->
            val isStale = (now - internal.lastSeenTimestamp) > staleTimeoutMs
            val distance = estimateDistance(internal.smoothedRssi)
            val zone = classifyZone(distance, isStale)
            val linkMargin = calculateLinkMargin(internal.smoothedRssi)
            val signalPercent = calculateSignalStrengthPercent(internal.smoothedRssi)

            RadarTarget(
                peerId = internal.peerId,
                displayName = internal.displayName,
                estimatedDistanceMeters = distance,
                rssi = internal.currentRssi,
                smoothedRssi = round(internal.smoothedRssi * 10.0) / 10.0,
                linkMarginDb = linkMargin,
                zone = zone,
                hopCount = internal.hopCount,
                signalStrengthPercent = signalPercent,
                isApproaching = internal.isApproaching,
                lastSeenTimestamp = internal.lastSeenTimestamp
            )
        }.sortedBy { it.estimatedDistanceMeters }

        val activeTargets = targets.filter { it.zone != ProximityZone.OUT_OF_RANGE }
        val closest = activeTargets.firstOrNull()

        val zoneCounts = mutableMapOf<ProximityZone, Int>()
        for (z in ProximityZone.entries) {
            zoneCounts[z] = 0
        }
        for (t in targets) {
            zoneCounts[t.zone] = (zoneCounts[t.zone] ?: 0) + 1
        }

        _radarFlow.value = RadarSnapshot(
            timestamp = now,
            targets = targets,
            closestTarget = closest,
            activeRidersCount = activeTargets.size,
            zoneCounts = zoneCounts
        )
    }
}
