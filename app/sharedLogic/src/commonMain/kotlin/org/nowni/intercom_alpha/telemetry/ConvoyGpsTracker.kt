package org.nowni.intercom_alpha.telemetry

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Standardized WGS-84 geographic coordinate representation.
 */
@Serializable
data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val speedKmh: Float = 0f,
    val headingDegrees: Float = 0f, // 0..360 True North
    val accuracyMeters: Float = 5f,
    val timestampMs: Long = 0L
)

/**
 * Motion dynamics state determining dynamic BLE beaconing frequency.
 */
@Serializable
enum class MotionState(val beaconIntervalMs: Long) {
    /** High speed cruising on highway or open trails (> 30 km/h) -> rapid 3-second beaconing. */
    HIGHWAY_CRUISE(3_000L),

    /** City riding or technical slow tracks (10..30 km/h) -> standard 6-second beaconing. */
    CITY_CONVOY(6_000L),

    /** Stationary or idling at light / pitstop (< 10 km/h) -> battery-conserving 15-second beaconing. */
    STATIONARY(15_000L),

    /** Sudden hard braking or crash detection -> immediate urgent broadcast. */
    EMERGENCY_ALERT(1_000L)
}

/**
 * Over-the-air binary/JSON telemetry payload exchanged across mesh control channels.
 */
@Serializable
data class ConvoyTelemetryFrame(
    val peerId: String,
    val peerName: String,
    val coordinate: GpsCoordinate,
    val motionState: MotionState,
    val isEmergencyAlert: Boolean = false,
    val timestampMs: Long = 0L
)

/**
 * Rich relative positioning data of a remote convoy member relative to local rider.
 */
@Serializable
data class ConvoyRiderPosition(
    val peerId: String,
    val peerName: String,
    val coordinate: GpsCoordinate,
    val distanceMeters: Double,
    val absoluteBearingDegrees: Double, // 0..360 from True North
    val relativeBearingDegrees: Double, // -180 (left) .. +180 (right) relative to local rider heading
    val isAhead: Boolean,
    val isBehind: Boolean,
    val motionState: MotionState,
    val isEmergencyAlert: Boolean,
    val lastUpdatedMs: Long,
    val isStale: Boolean = false
)

/**
 * Reactive convoy GPS telemetry snapshot consumed by cockpit vector maps and radar.
 */
@Serializable
data class ConvoyTelemetrySnapshot(
    val localCoordinate: GpsCoordinate?,
    val localMotionState: MotionState,
    val riders: List<ConvoyRiderPosition> = emptyList(),
    val closestRider: ConvoyRiderPosition? = null,
    val leadRider: ConvoyRiderPosition? = null,
    val sweepRider: ConvoyRiderPosition? = null,
    val activeCount: Int = 0,
    val timestampMs: Long = 0L
)

/**
 * Decentralized GPS Convoy Telemetry and Relative Vector Tracking Engine.
 *
 * Computes exact Great-Circle Haversine distance, initial forward azimuth,
 * and relative rider bearing (-180° to +180°) tailored for SpatialAudio and cockpit radar.
 */
class ConvoyGpsTracker(
    val localPeerId: String,
    val localPeerName: String,
    val staleTimeoutMs: Long = 30_000L
) {
    private val mutex = Mutex()

    private var localCoord: GpsCoordinate? = null
    private var lastBeaconTimestampMs: Long = 0L
    private val remoteRiders = mutableMapOf<String, ConvoyRiderPosition>()

    private val _convoyState = MutableStateFlow(
        ConvoyTelemetrySnapshot(
            localCoordinate = null,
            localMotionState = MotionState.STATIONARY,
            timestampMs = 0L
        )
    )
    val convoyState: StateFlow<ConvoyTelemetrySnapshot> = _convoyState.asStateFlow()

    private val _outgoingFrames = Channel<ConvoyTelemetryFrame>(Channel.BUFFERED)
    val outgoingFrames: ReceiveChannel<ConvoyTelemetryFrame> = _outgoingFrames

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encodeTelemetryFrame(frame: ConvoyTelemetryFrame): ByteArray =
            json.encodeToString(ConvoyTelemetryFrame.serializer(), frame).encodeToByteArray()

        fun decodeTelemetryFrame(bytes: ByteArray): ConvoyTelemetryFrame =
            json.decodeFromString(ConvoyTelemetryFrame.serializer(), bytes.decodeToString())

        /**
         * Calculate Great-Circle distance between two coordinates in meters using Haversine formula.
         */
        fun calculateHaversineDistance(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val dLat = (lat2 - lat1) * PI / 180.0
            val dLon = (lon2 - lon1) * PI / 180.0
            val rLat1 = lat1 * PI / 180.0
            val rLat2 = lat2 * PI / 180.0

            val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                    cos(rLat1) * cos(rLat2) *
                    sin(dLon / 2.0) * sin(dLon / 2.0)
            val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
            return EARTH_RADIUS_METERS * c
        }

        /**
         * Calculate initial forward azimuth (compass bearing 0..360°) from point 1 to point 2.
         */
        fun calculateForwardAzimuth(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val dLon = (lon2 - lon1) * PI / 180.0
            val rLat1 = lat1 * PI / 180.0
            val rLat2 = lat2 * PI / 180.0

            val y = sin(dLon) * cos(rLat2)
            val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
            val initialBearing = atan2(y, x) * 180.0 / PI
            return (initialBearing + 360.0) % 360.0
        }

        /**
         * Calculate relative bearing (-180° left to +180° right) of target relative to local heading.
         */
        fun calculateRelativeBearing(
            targetAzimuth: Double,
            localHeadingDegrees: Float
        ): Double {
            val diff = (targetAzimuth - localHeadingDegrees + 540.0) % 360.0 - 180.0
            return diff
        }
    }

    /**
     * Ingest newly updated local GPS coordinates from platform location provider.
     * Evaluates speed dynamics and automatically emits telemetry beacon when interval is met.
     */
    suspend fun updateLocalLocation(
        coordinate: GpsCoordinate,
        forceImmediateBeacon: Boolean = false
    ): Boolean = mutex.withLock {
        val now = if (coordinate.timestampMs > 0L) coordinate.timestampMs else currentTimeMillis()
        val prevCoord = localCoord
        localCoord = coordinate.copy(timestampMs = now)

        // Evaluate motion state
        val motionState = when {
            forceImmediateBeacon -> MotionState.EMERGENCY_ALERT
            coordinate.speedKmh >= 30.0f -> MotionState.HIGHWAY_CRUISE
            coordinate.speedKmh >= 10.0f -> MotionState.CITY_CONVOY
            else -> MotionState.STATIONARY
        }

        // Detect crash / sudden deceleration: speed dropped by >35 km/h in < 1.5 seconds
        val isEmergencyDecel = prevCoord != null &&
                (prevCoord.speedKmh - coordinate.speedKmh) >= 35.0f &&
                (now - prevCoord.timestampMs) <= 1500L

        val shouldBeacon = forceImmediateBeacon || isEmergencyDecel ||
                (now - lastBeaconTimestampMs) >= motionState.beaconIntervalMs

        if (shouldBeacon) {
            val frame = ConvoyTelemetryFrame(
                peerId = localPeerId,
                peerName = localPeerName,
                coordinate = coordinate.copy(timestampMs = now),
                motionState = if (isEmergencyDecel) MotionState.EMERGENCY_ALERT else motionState,
                isEmergencyAlert = forceImmediateBeacon || isEmergencyDecel,
                timestampMs = now
            )
            _outgoingFrames.send(frame)
            lastBeaconTimestampMs = now
        }

        recomputeSnapshotInternal(now)
        shouldBeacon
    }

    /**
     * Ingest an incoming telemetry frame received from a remote mesh peer.
     */
    suspend fun handleIncomingTelemetry(frame: ConvoyTelemetryFrame) = mutex.withLock {
        if (frame.peerId == localPeerId) return@withLock

        val now = currentTimeMillis()
        val local = localCoord

        val (dist, absBearing, relBearing, isAhead, isBehind) = if (local != null) {
            val d = calculateHaversineDistance(
                local.latitude, local.longitude,
                frame.coordinate.latitude, frame.coordinate.longitude
            )
            val az = calculateForwardAzimuth(
                local.latitude, local.longitude,
                frame.coordinate.latitude, frame.coordinate.longitude
            )
            val rel = calculateRelativeBearing(az, local.headingDegrees)
            val ahead = kotlin.math.abs(rel) <= 60.0
            val behind = kotlin.math.abs(rel) >= 120.0
            Tuple5(d, az, rel, ahead, behind)
        } else {
            Tuple5(0.0, 0.0, 0.0, false, false)
        }

        val position = ConvoyRiderPosition(
            peerId = frame.peerId,
            peerName = frame.peerName,
            coordinate = frame.coordinate,
            distanceMeters = dist,
            absoluteBearingDegrees = absBearing,
            relativeBearingDegrees = relBearing,
            isAhead = isAhead,
            isBehind = isBehind,
            motionState = frame.motionState,
            isEmergencyAlert = frame.isEmergencyAlert,
            lastUpdatedMs = now,
            isStale = false
        )

        remoteRiders[frame.peerId] = position
        recomputeSnapshotInternal(now)
    }

    /**
     * Prune stale convoy members who have timed out.
     */
    suspend fun pruneStaleRiders(now: Long = currentTimeMillis()) = mutex.withLock {
        val iterator = remoteRiders.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastUpdatedMs >= staleTimeoutMs) {
                iterator.remove()
            }
        }
        recomputeSnapshotInternal(now)
    }

    private fun recomputeSnapshotInternal(now: Long) {
        val local = localCoord
        val currentLocalMotion = when {
            local == null -> MotionState.STATIONARY
            local.speedKmh >= 30f -> MotionState.HIGHWAY_CRUISE
            local.speedKmh >= 10f -> MotionState.CITY_CONVOY
            else -> MotionState.STATIONARY
        }

        val updatedRiders = remoteRiders.values.map { rider ->
            if (now - rider.lastUpdatedMs >= staleTimeoutMs) {
                rider.copy(isStale = true)
            } else {
                rider
            }
        }.sortedBy { it.distanceMeters }

        val closest = updatedRiders.firstOrNull { !it.isStale }
        val lead = updatedRiders.filter { !it.isStale && it.isAhead }.maxByOrNull { it.distanceMeters }
        val sweep = updatedRiders.filter { !it.isStale && it.isBehind }.maxByOrNull { it.distanceMeters }

        _convoyState.value = ConvoyTelemetrySnapshot(
            localCoordinate = local,
            localMotionState = currentLocalMotion,
            riders = updatedRiders,
            closestRider = closest,
            leadRider = lead,
            sweepRider = sweep,
            activeCount = updatedRiders.count { !it.isStale },
            timestampMs = now
        )
    }

    private data class Tuple5(
        val d: Double,
        val az: Double,
        val rel: Double,
        val ahead: Boolean,
        val behind: Boolean
    )
}
