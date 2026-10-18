package org.nowni.intercom_alpha.geo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nowni.intercom_alpha.map.GeoPoint
import kotlin.math.*

/**
 * Severity level of terrain slope incline / decline.
 */
enum class GradeSeverity {
    NORMAL,
    STEEP_CAUTION,
    EXTREME_ALPINE_WARNING
}

/**
 * Real-time slope warning for steep climbs, alpine descents, or switchbacks.
 */
data class SlopeGradeWarning(
    val severity: GradeSeverity,
    val gradePercentage: Double,
    val isClimb: Boolean,
    val message: String
)

/**
 * Elevation sample point along a trail route or timeline.
 */
data class ElevationProfilePoint(
    val distanceMeters: Double,
    val altitudeMeters: Double,
    val gradePercentage: Double
)

/**
 * Real-time elevation tracking for an individual convoy rider.
 */
data class RiderElevationState(
    val peerId: String,
    val callsign: String,
    val altitudeMeters: Double,
    val distanceAlongRouteMeters: Double,
    val verticalDeltaToLeadMeters: Double,
    val currentGradePct: Double,
    val isLead: Boolean = false,
    val lastUpdateMs: Long = 0L
)

/**
 * Complete immutable snapshot of convoy elevation profile and slope telemetry.
 */
data class ConvoyElevationProfile(
    val profilePoints: List<ElevationProfilePoint> = emptyList(),
    val riders: List<RiderElevationState> = emptyList(),
    val localAltitudeMeters: Double = 0.0,
    val minAltitudeMeters: Double = 0.0,
    val maxAltitudeMeters: Double = 0.0,
    val totalClimbMeters: Double = 0.0,
    val totalDescentMeters: Double = 0.0,
    val currentGradePercentage: Double = 0.0,
    val leadTailVerticalSeparationMeters: Double = 0.0,
    val activeWarnings: List<SlopeGradeWarning> = emptyList(),
    val upcomingSummitDistanceMeters: Double? = null,
    val upcomingSummitAltitudeMeters: Double? = null
)

/**
 * Configuration parameters for ConvoyElevationEngine.
 */
data class ElevationEngineConfig(
    val steepCautionGradeThresholdPct: Double = 8.0,
    val extremeWarningGradeThresholdPct: Double = 12.0,
    val minDistanceDeltaForGradeMeters: Double = 25.0,
    val smoothingWindowSize: Int = 5,
    val summitDetectionWindowMeters: Double = 500.0
)

/**
 * Real-Time Convoy Elevation Profile & Altitude Incline Math Engine.
 *
 * Computes moving slope grade percentage, steep alpine descent/climb hazard warnings (> 12% grade),
 * vertical altitude separation between convoy members, and summit peaks with zero platform dependencies.
 */
class ConvoyElevationEngine(
    val localPeerId: String = "local-rider",
    val localCallsign: String = "Lead",
    val config: ElevationEngineConfig = ElevationEngineConfig()
) {
    private val _elevationProfile = MutableStateFlow(ConvoyElevationProfile())
    val elevationProfile: StateFlow<ConvoyElevationProfile> = _elevationProfile.asStateFlow()

    private val localHistory = mutableListOf<GeoPoint>()
    private val peerStates = mutableMapOf<String, RiderElevationState>()
    private var plannedRoutePoints: List<GeoPoint> = emptyList()

    private var cumulativeDistanceMeters: Double = 0.0
    private var previousPoint: GeoPoint? = null

    /**
     * Sets the planned trail or route polyline to generate upcoming terrain slope predictions.
     */
    fun setPlannedRoute(points: List<GeoPoint>) {
        plannedRoutePoints = points
        recalculateProfile()
    }

    /**
     * Ingests local GPS coordinates with altitude from device hardware.
     */
    fun ingestLocalPosition(point: GeoPoint, timestampMs: Long = 0L) {
        if (previousPoint != null) {
            val dist = calculateHaversineDistance(previousPoint!!, point)
            cumulativeDistanceMeters += dist
        }
        previousPoint = point
        localHistory.add(point)

        // Calculate moving grade %
        val currentGrade = calculateCurrentGrade()

        val localState = RiderElevationState(
            peerId = localPeerId,
            callsign = localCallsign,
            altitudeMeters = point.altitudeMeters,
            distanceAlongRouteMeters = cumulativeDistanceMeters,
            verticalDeltaToLeadMeters = 0.0,
            currentGradePct = currentGrade,
            isLead = true,
            lastUpdateMs = timestampMs
        )
        peerStates[localPeerId] = localState

        recalculateProfile()
    }

    /**
     * Ingests remote convoy member GPS coordinate & altitude received over BLE mesh telemetry.
     */
    fun ingestPeerPosition(
        peerId: String,
        callsign: String,
        point: GeoPoint,
        distanceAlongRouteMeters: Double? = null,
        timestampMs: Long = 0L
    ) {
        val leadAltitude = peerStates[localPeerId]?.altitudeMeters ?: point.altitudeMeters
        val verticalDelta = point.altitudeMeters - leadAltitude

        val riderDistance = distanceAlongRouteMeters ?: run {
            // Estimate based on direct distance from lead if route index not supplied
            val leadPos = previousPoint
            if (leadPos != null) {
                cumulativeDistanceMeters - calculateHaversineDistance(leadPos, point)
            } else {
                0.0
            }
        }

        val existing = peerStates[peerId]
        val grade = if (existing != null) {
            val dAlt = point.altitudeMeters - existing.altitudeMeters
            val dDist = max(1.0, abs(riderDistance - existing.distanceAlongRouteMeters))
            (dAlt / dDist) * 100.0
        } else {
            0.0
        }

        peerStates[peerId] = RiderElevationState(
            peerId = peerId,
            callsign = callsign,
            altitudeMeters = point.altitudeMeters,
            distanceAlongRouteMeters = riderDistance,
            verticalDeltaToLeadMeters = verticalDelta,
            currentGradePct = grade,
            isLead = false,
            lastUpdateMs = timestampMs
        )

        recalculateProfile()
    }

    private fun calculateCurrentGrade(): Double {
        if (localHistory.size < 2) return 0.0
        val current = localHistory.last()

        // Look back for a point at least minDistanceDeltaForGradeMeters away
        var targetPrev: GeoPoint? = null
        var distAcc = 0.0
        for (i in localHistory.size - 2 downTo 0) {
            val p = localHistory[i]
            val nextP = localHistory[i + 1]
            distAcc += calculateHaversineDistance(p, nextP)
            if (distAcc >= config.minDistanceDeltaForGradeMeters) {
                targetPrev = p
                break
            }
        }

        val prev = targetPrev ?: localHistory.first()
        val horizDist = max(1.0, calculateHaversineDistance(prev, current))
        val vertDelta = current.altitudeMeters - prev.altitudeMeters
        return (vertDelta / horizDist) * 100.0
    }

    private fun recalculateProfile() {
        val profilePoints = mutableListOf<ElevationProfilePoint>()
        var minAlt = Double.MAX_VALUE
        var maxAlt = Double.MIN_VALUE
        var climb = 0.0
        var descent = 0.0

        if (plannedRoutePoints.isNotEmpty()) {
            var dist = 0.0
            for (i in plannedRoutePoints.indices) {
                val pt = plannedRoutePoints[i]
                if (i > 0) {
                    val prev = plannedRoutePoints[i - 1]
                    val d = calculateHaversineDistance(prev, pt)
                    dist += d
                    val dAlt = pt.altitudeMeters - prev.altitudeMeters
                    if (dAlt > 0) climb += dAlt else descent += abs(dAlt)
                }

                val grade = if (i > 0) {
                    val prev = plannedRoutePoints[i - 1]
                    val d = max(1.0, calculateHaversineDistance(prev, pt))
                    ((pt.altitudeMeters - prev.altitudeMeters) / d) * 100.0
                } else 0.0

                minAlt = min(minAlt, pt.altitudeMeters)
                maxAlt = max(maxAlt, pt.altitudeMeters)

                profilePoints.add(
                    ElevationProfilePoint(
                        distanceMeters = dist,
                        altitudeMeters = pt.altitudeMeters,
                        gradePercentage = grade
                    )
                )
            }
        } else {
            // Construct profile from local history
            var dist = 0.0
            for (i in localHistory.indices) {
                val pt = localHistory[i]
                if (i > 0) {
                    val prev = localHistory[i - 1]
                    dist += calculateHaversineDistance(prev, pt)
                }
                minAlt = min(minAlt, pt.altitudeMeters)
                maxAlt = max(maxAlt, pt.altitudeMeters)
                profilePoints.add(
                    ElevationProfilePoint(
                        distanceMeters = dist,
                        altitudeMeters = pt.altitudeMeters,
                        gradePercentage = 0.0
                    )
                )
            }
        }

        if (minAlt == Double.MAX_VALUE) minAlt = 0.0
        if (maxAlt == Double.MIN_VALUE) maxAlt = 0.0

        val localAlt = peerStates[localPeerId]?.altitudeMeters ?: 0.0
        val currentGrade = peerStates[localPeerId]?.currentGradePct ?: 0.0

        // Calculate lead vs tail vertical separation
        val allRiders = peerStates.values.sortedByDescending { it.distanceAlongRouteMeters }
        val leadRider = peerStates.values.find { it.isLead } ?: allRiders.firstOrNull()
        val tailRider = allRiders.lastOrNull()

        val separation = if (leadRider != null && tailRider != null && leadRider != tailRider) {
            abs(leadRider.altitudeMeters - tailRider.altitudeMeters)
        } else {
            0.0
        }

        // Grade warning detection
        val warnings = mutableListOf<SlopeGradeWarning>()
        val absGrade = abs(currentGrade)

        if (absGrade >= config.extremeWarningGradeThresholdPct) {
            val isClimb = currentGrade > 0
            val directionStr = if (isClimb) "STEEP CLIMB" else "STEEP DESCENT"
            val gradeFormatted = ((absGrade * 10.0).roundToInt() / 10.0).toString()
            warnings.add(
                SlopeGradeWarning(
                    severity = GradeSeverity.EXTREME_ALPINE_WARNING,
                    gradePercentage = currentGrade,
                    isClimb = isClimb,
                    message = "WARNING: $directionStr $gradeFormatted% - Switchbacks / Alpine Grade"
                )
            )
        } else if (absGrade >= config.steepCautionGradeThresholdPct) {
            val isClimb = currentGrade > 0
            val directionStr = if (isClimb) "Climb" else "Descent"
            val gradeFormatted = ((absGrade * 10.0).roundToInt() / 10.0).toString()
            warnings.add(
                SlopeGradeWarning(
                    severity = GradeSeverity.STEEP_CAUTION,
                    gradePercentage = currentGrade,
                    isClimb = isClimb,
                    message = "CAUTION: $directionStr $gradeFormatted% grade"
                )
            )
        }

        // Summit detection along planned route
        var upcomingSummitDist: Double? = null
        var upcomingSummitAlt: Double? = null

        if (profilePoints.isNotEmpty()) {
            val currentDist = cumulativeDistanceMeters
            for (i in 1 until profilePoints.size - 1) {
                val pt = profilePoints[i]
                if (pt.distanceMeters > currentDist && pt.distanceMeters <= currentDist + config.summitDetectionWindowMeters) {
                    val prevAlt = profilePoints[i - 1].altitudeMeters
                    val nextAlt = profilePoints[i + 1].altitudeMeters
                    if (pt.altitudeMeters >= prevAlt && pt.altitudeMeters > nextAlt) {
                        upcomingSummitDist = pt.distanceMeters - currentDist
                        upcomingSummitAlt = pt.altitudeMeters
                        break
                    }
                }
            }
        }

        _elevationProfile.value = ConvoyElevationProfile(
            profilePoints = profilePoints,
            riders = allRiders,
            localAltitudeMeters = localAlt,
            minAltitudeMeters = minAlt,
            maxAltitudeMeters = maxAlt,
            totalClimbMeters = climb,
            totalDescentMeters = descent,
            currentGradePercentage = currentGrade,
            leadTailVerticalSeparationMeters = separation,
            activeWarnings = warnings,
            upcomingSummitDistanceMeters = upcomingSummitDist,
            upcomingSummitAltitudeMeters = upcomingSummitAlt
        )
    }

    private fun calculateHaversineDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val r = 6371000.0 // Earth radius in meters
        val lat1Rad = p1.latitude * PI / 180.0
        val lat2Rad = p2.latitude * PI / 180.0
        val dLat = (p2.latitude - p1.latitude) * PI / 180.0
        val dLon = (p2.longitude - p1.longitude) * PI / 180.0

        val a = sin(dLat / 2.0).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    fun clear() {
        localHistory.clear()
        peerStates.clear()
        plannedRoutePoints = emptyList()
        cumulativeDistanceMeters = 0.0
        previousPoint = null
        _elevationProfile.value = ConvoyElevationProfile()
    }
}
