package org.nowni.intercom_alpha.map

import kotlinx.serialization.Serializable

/**
 * Standard WGS-84 geographic coordinate point with elevation in meters.
 */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0
)

/**
 * Tactical waypoint classification for outdoor backcountry expeditions.
 */
@Serializable
enum class WaypointType {
    BASECAMP,
    WATER_SOURCE,
    TRAILHEAD,
    CHECKPOINT,
    OBSTACLE_HAZARD,
    MOUNTAIN_PASS,
    SHELTER,
    CUSTOM
}

/**
 * Named geographic waypoint along a trail or convoy route.
 */
@Serializable
data class TrailWaypoint(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val type: WaypointType = WaypointType.CUSTOM,
    val description: String = ""
)

/**
 * Technical terrain difficulty rating.
 */
@Serializable
enum class TrailDifficulty {
    EASY,
    MODERATE,
    DIFFICULT,
    EXTREME_TECHNICAL
}

/**
 * Continuous vector trail track composed of sequential GeoPoints.
 */
@Serializable
data class TrailPolyline(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val difficulty: TrailDifficulty = TrailDifficulty.MODERATE,
    val totalDistanceMeters: Double = 0.0,
    val totalElevationGainMeters: Double = 0.0
)

/**
 * Topographic contour line at a fixed elevation above sea level.
 */
@Serializable
data class ContourLine(
    val elevationMeters: Int,
    val points: List<GeoPoint>
)

/**
 * Point projected into the tactical radar's coordinate space.
 *
 * Coordinates are normalized to [-1.0 .. 1.0] within the circular radar aperture:
 * - [normalizedX]: -1.0 (left) to +1.0 (right)
 * - [normalizedY]: -1.0 (ahead / 12 o'clock) to +1.0 (behind / 6 o'clock)
 * - [distanceMeters]: Euclidean/Haversine distance from observer in meters
 * - [relativeBearingDegrees]: Relative bearing from -180.0 (port) to +180.0 (starboard)
 * - [isVisibleOnRadar]: True if distance <= current radar max range
 */
@Serializable
data class ProjectedRadarPoint(
    val normalizedX: Float,
    val normalizedY: Float,
    val distanceMeters: Double,
    val relativeBearingDegrees: Double,
    val altitudeMeters: Double = 0.0,
    val isVisibleOnRadar: Boolean = true
)

/**
 * Waypoint projected onto the radar display.
 */
@Serializable
data class ProjectedWaypoint(
    val waypoint: TrailWaypoint,
    val projectedPoint: ProjectedRadarPoint
)

/**
 * Continuous trail segment projected onto the radar canvas.
 */
@Serializable
data class ProjectedTrailSegment(
    val trailId: String,
    val trailName: String,
    val difficulty: TrailDifficulty,
    val points: List<ProjectedRadarPoint>
)

/**
 * Topographic contour line projected onto the radar canvas.
 */
@Serializable
data class ProjectedContour(
    val elevationMeters: Int,
    val points: List<ProjectedRadarPoint>
)

/**
 * Selectable radar range zoom envelopes for handlebars/cockpit display.
 */
@Serializable
enum class RadarRangeZoom(val maxDistanceMeters: Double, val label: String) {
    CLOSE(500.0, "500m"),
    MEDIUM(1500.0, "1.5km"),
    WIDE(5000.0, "5.0km");

    fun next(): RadarRangeZoom = when (this) {
        CLOSE -> MEDIUM
        MEDIUM -> WIDE
        WIDE -> CLOSE
    }
}

/**
 * Complete immutable snapshot of the offline vector terrain & trail overlay.
 */
@Serializable
data class TrailMapSnapshot(
    val activeTrail: TrailPolyline? = null,
    val projectedTrails: List<ProjectedTrailSegment> = emptyList(),
    val projectedWaypoints: List<ProjectedWaypoint> = emptyList(),
    val projectedContours: List<ProjectedContour> = emptyList(),
    val peerBreadcrumbs: Map<String, List<ProjectedRadarPoint>> = emptyMap(),
    val localBreadcrumbs: List<ProjectedRadarPoint> = emptyList(),
    val currentElevationMeters: Double = 0.0,
    val elevationDeltaToLeadMeters: Double = 0.0,
    val activeRangeZoom: RadarRangeZoom = RadarRangeZoom.MEDIUM,
    val isOverlayEnabled: Boolean = true,
    val nearestWaypoint: TrailWaypoint? = null,
    val nearestWaypointDistanceMeters: Double? = null,
    val timestampMs: Long = 0L
)
