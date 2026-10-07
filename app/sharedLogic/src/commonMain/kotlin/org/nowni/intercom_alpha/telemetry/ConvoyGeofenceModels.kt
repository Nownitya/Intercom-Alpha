package org.nowni.intercom_alpha.telemetry

import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.map.WaypointType

/**
 * Tactical status of a convoy member relative to safe distance tether bounds.
 */
@Serializable
enum class GeofenceTetherStatus {
    /** Rider is within safe visual/radio tether radius (<= warning threshold, e.g. 300m). */
    IN_BOUNDS,

    /** Rider is trailing behind or separated (between warning and breach threshold, e.g. 300m..600m). */
    WARNING,

    /** Rider has breached safe convoy tether distance (> breach threshold, e.g. > 600m). */
    BREACH,

    /** Rider's GPS signal is stale or timed out. */
    DISCONNECTED
}

/**
 * Alert detail for a single convoy member's tether status.
 */
@Serializable
data class RiderTetherAlert(
    val peerId: String,
    val peerName: String,
    val distanceMeters: Double,
    val status: GeofenceTetherStatus,
    val isTrailingBehind: Boolean,
    val timestampMs: Long
)

/**
 * Proximity alert when approaching a known obstacle, hazard, or tactical waypoint.
 */
@Serializable
data class HazardProximityAlert(
    val waypointId: String,
    val waypointName: String,
    val waypointType: WaypointType,
    val distanceMeters: Double,
    val relativeBearingDegrees: Double,
    val elevationMeters: Double,
    val isApproaching: Boolean,
    val timestampMs: Long
)

/**
 * Convoy cluster partition / bifurcation detection result.
 */
@Serializable
data class ConvoySplitWarning(
    val isSplitDetected: Boolean,
    val clusterCount: Int,
    val clusterGapMeters: Double,
    val localClusterSize: Int,
    val remoteClusterSize: Int,
    val recommendedAction: String,
    val timestampMs: Long
)

/**
 * Comprehensive geofence and proximity radar telemetry snapshot.
 */
@Serializable
data class ConvoyGeofenceSnapshot(
    val overallStatus: GeofenceTetherStatus = GeofenceTetherStatus.IN_BOUNDS,
    val furthestRiderDistanceMeters: Double = 0.0,
    val tetherAlerts: List<RiderTetherAlert> = emptyList(),
    val activeHazardAlerts: List<HazardProximityAlert> = emptyList(),
    val splitWarning: ConvoySplitWarning? = null,
    val timestampMs: Long = 0L
)

/**
 * Type of over-the-air BLE mesh geofence alarm.
 */
@Serializable
enum class GeofenceAlarmType {
    TETHER_WARNING,
    TETHER_BREACH,
    HAZARD_PROXIMITY,
    CONVOY_SPLIT
}

/**
 * Compact wire payload for over-the-air mesh geofence alarms (ControlType.GEOFENCE_ALARM).
 */
@Serializable
data class GeofenceAlarmPacket(
    val senderId: String,
    val alarmType: GeofenceAlarmType,
    val targetId: String = "",
    val title: String,
    val details: String,
    val distanceMeters: Double = 0.0,
    val timestampMs: Long = 0L
)
