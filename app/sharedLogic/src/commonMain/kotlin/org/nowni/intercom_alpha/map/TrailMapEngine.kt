package org.nowni.intercom_alpha.map

import kotlinx.serialization.json.*
import kotlin.math.*

/**
 * Pure KMP Offline Vector Geometry & Projection Engine.
 *
 * Provides high-speed equirectangular projections, radar coordinates,
 * elevation analysis, and lightweight GeoJSON parsing with zero external SDKs.
 */
object TrailMapEngine {

    const val EARTH_RADIUS_METERS = 6_371_000.0

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Compute Great-Circle Haversine distance in meters between two GeoPoints.
     */
    fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = p1.latitude * (PI / 180.0)
        val lat2Rad = p2.latitude * (PI / 180.0)
        val deltaLat = (p2.latitude - p1.latitude) * (PI / 180.0)
        val deltaLon = (p2.longitude - p1.longitude) * (PI / 180.0)

        val a = sin(deltaLat / 2.0).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Compute initial forward azimuth (0°..360° from True North) from p1 to p2.
     */
    fun calculateForwardAzimuth(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = p1.latitude * (PI / 180.0)
        val lat2Rad = p2.latitude * (PI / 180.0)
        val deltaLon = (p2.longitude - p1.longitude) * (PI / 180.0)

        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        val azimuthRad = atan2(y, x)
        val azimuthDeg = azimuthRad * (180.0 / PI)
        return (azimuthDeg + 360.0) % 360.0
    }

    /**
     * Normalize an angle difference to [-180.0 .. +180.0] degrees.
     */
    fun normalizeRelativeAngle(bearingDeg: Double, headingDeg: Float): Double {
        var diff = bearingDeg - headingDeg.toDouble()
        while (diff > 180.0) diff -= 360.0
        while (diff < -180.0) diff += 360.0
        return diff
    }

    /**
     * Project a geographic coordinate into the local rider's circular radar coordinate frame.
     *
     * @param point Target point to project.
     * @param origin Observer (local rider) geographic location.
     * @param originHeadingDegrees Observer vehicle heading (0°..360°).
     * @param maxRangeMeters Current radar envelope range (e.g. 500m, 1500m, 5000m).
     */
    fun projectToRadar(
        point: GeoPoint,
        origin: GeoPoint,
        originHeadingDegrees: Float,
        maxRangeMeters: Double
    ): ProjectedRadarPoint {
        val dist = calculateDistanceMeters(origin, point)
        val azimuth = calculateForwardAzimuth(origin, point)
        val relBearing = normalizeRelativeAngle(azimuth, originHeadingDegrees)

        val clampedDist = min(dist, maxRangeMeters)
        val normDist = (clampedDist / maxRangeMeters).toFloat()

        val rad = relBearing * (PI / 180.0)
        // Radar: 0° is up (-Y), 90° is right (+X), -90° is left (-X), 180° is down (+Y)
        val normX = (normDist * sin(rad)).toFloat()
        val normY = (-normDist * cos(rad)).toFloat()

        return ProjectedRadarPoint(
            normalizedX = normX,
            normalizedY = normY,
            distanceMeters = dist,
            relativeBearingDegrees = relBearing,
            altitudeMeters = point.altitudeMeters,
            isVisibleOnRadar = dist <= maxRangeMeters
        )
    }

    /**
     * Calculate cumulative length and elevation gain of a trail polyline.
     */
    fun computeTrailMetrics(points: List<GeoPoint>): Pair<Double, Double> {
        if (points.size < 2) return 0.0 to 0.0
        var totalDist = 0.0
        var totalAscent = 0.0

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            totalDist += calculateDistanceMeters(p1, p2)
            val dAlt = p2.altitudeMeters - p1.altitudeMeters
            if (dAlt > 0.0) {
                totalAscent += dAlt
            }
        }
        return totalDist to totalAscent
    }

    /**
     * Parse GeoJSON FeatureCollection into TrailPolyline tracks and TrailWaypoints.
     */
    fun parseGeoJson(geoJsonString: String): Pair<List<TrailPolyline>, List<TrailWaypoint>> {
        val trails = mutableListOf<TrailPolyline>()
        val waypoints = mutableListOf<TrailWaypoint>()

        try {
            val root = jsonParser.parseToJsonElement(geoJsonString).jsonObject
            val features = root["features"]?.jsonArray ?: return emptyList<TrailPolyline>() to emptyList<TrailWaypoint>()

            for (feature in features) {
                val fObj = feature.jsonObject
                val properties = fObj["properties"]?.jsonObject
                val geometry = fObj["geometry"]?.jsonObject ?: continue
                val type = geometry["type"]?.jsonPrimitive?.content ?: continue

                val name = properties?.get("name")?.jsonPrimitive?.content ?: "Unnamed"
                val id = properties?.get("id")?.jsonPrimitive?.content ?: "feat-${waypoints.size + trails.size}"

                when (type) {
                    "Point" -> {
                        val coords = geometry["coordinates"]?.jsonArray
                        if (coords != null && coords.size >= 2) {
                            val lon = coords[0].jsonPrimitive.double
                            val lat = coords[1].jsonPrimitive.double
                            val alt = if (coords.size >= 3) coords[2].jsonPrimitive.double else 0.0
                            val typeStr = properties?.get("type")?.jsonPrimitive?.content ?: "CUSTOM"
                            val wpType = try {
                                WaypointType.valueOf(typeStr.uppercase())
                            } catch (_: Exception) {
                                WaypointType.CUSTOM
                            }
                            waypoints.add(
                                TrailWaypoint(
                                    id = id,
                                    name = name,
                                    point = GeoPoint(lat, lon, alt),
                                    type = wpType,
                                    description = properties?.get("description")?.jsonPrimitive?.content ?: ""
                                )
                            )
                        }
                    }
                    "LineString" -> {
                        val coordsList = geometry["coordinates"]?.jsonArray
                        if (coordsList != null) {
                            val points = mutableListOf<GeoPoint>()
                            for (c in coordsList) {
                                val cArr = c.jsonArray
                                if (cArr.size >= 2) {
                                    val lon = cArr[0].jsonPrimitive.double
                                    val lat = cArr[1].jsonPrimitive.double
                                    val alt = if (cArr.size >= 3) cArr[2].jsonPrimitive.double else 0.0
                                    points.add(GeoPoint(lat, lon, alt))
                                }
                            }
                            if (points.isNotEmpty()) {
                                val (dist, ascent) = computeTrailMetrics(points)
                                trails.add(
                                    TrailPolyline(
                                        id = id,
                                        name = name,
                                        points = points,
                                        difficulty = TrailDifficulty.MODERATE,
                                        totalDistanceMeters = dist,
                                        totalElevationGainMeters = ascent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback on malformed json
        }

        return trails to waypoints
    }

    /**
     * Generate an alpine trail track and contour loops centered around a base reference coordinate.
     * Ideal for offline testing and immediate out-of-the-box tactical display.
     */
    fun createSampleAlpineTrail(center: GeoPoint): Triple<TrailPolyline, List<TrailWaypoint>, List<ContourLine>> {
        // Create an s-curve mountain trail passing through center
        val latStep = 0.003
        val lonStep = 0.003
        val points = listOf(
            GeoPoint(center.latitude - latStep * 2.0, center.longitude - lonStep * 1.5, center.altitudeMeters - 180.0),
            GeoPoint(center.latitude - latStep * 1.0, center.longitude - lonStep * 0.5, center.altitudeMeters - 90.0),
            GeoPoint(center.latitude, center.longitude, center.altitudeMeters),
            GeoPoint(center.latitude + latStep * 1.0, center.longitude + lonStep * 0.8, center.altitudeMeters + 120.0),
            GeoPoint(center.latitude + latStep * 2.2, center.longitude + lonStep * 1.6, center.altitudeMeters + 250.0)
        )

        val (dist, ascent) = computeTrailMetrics(points)
        val trail = TrailPolyline(
            id = "trail-alpine-pass",
            name = "Black Bear Pass Trail",
            points = points,
            difficulty = TrailDifficulty.DIFFICULT,
            totalDistanceMeters = dist,
            totalElevationGainMeters = ascent
        )

        val waypoints = listOf(
            TrailWaypoint(
                id = "wp-basecamp",
                name = "Basecamp Ridge",
                point = points[0],
                type = WaypointType.BASECAMP,
                description = "Primary stationary relay & bivouac"
            ),
            TrailWaypoint(
                id = "wp-river",
                name = "River Crossing",
                point = points[1],
                type = WaypointType.WATER_SOURCE,
                description = "Creek ford with loose shale"
            ),
            TrailWaypoint(
                id = "wp-checkpoint",
                name = "Pass Checkpoint",
                point = points[2],
                type = WaypointType.CHECKPOINT,
                description = "Mid-convoy regroup point"
            ),
            TrailWaypoint(
                id = "wp-hazard",
                name = "Rockslide Area",
                point = points[3],
                type = WaypointType.OBSTACLE_HAZARD,
                description = "Active scree slope"
            ),
            TrailWaypoint(
                id = "wp-summit",
                name = "Eagle Summit",
                point = points[4],
                type = WaypointType.MOUNTAIN_PASS,
                description = "High elevation saddle"
            )
        )

        // Generate 3 concentric elevation contour arcs
        val baseAlt = center.altitudeMeters.toInt()
        val contours = listOf(
            ContourLine(
                elevationMeters = baseAlt - 100,
                points = listOf(
                    GeoPoint(center.latitude - 0.005, center.longitude - 0.006, (baseAlt - 100).toDouble()),
                    GeoPoint(center.latitude - 0.002, center.longitude - 0.007, (baseAlt - 100).toDouble()),
                    GeoPoint(center.latitude + 0.003, center.longitude - 0.006, (baseAlt - 100).toDouble())
                )
            ),
            ContourLine(
                elevationMeters = baseAlt,
                points = listOf(
                    GeoPoint(center.latitude - 0.004, center.longitude - 0.002, baseAlt.toDouble()),
                    GeoPoint(center.latitude, center.longitude - 0.003, baseAlt.toDouble()),
                    GeoPoint(center.latitude + 0.004, center.longitude - 0.002, baseAlt.toDouble())
                )
            ),
            ContourLine(
                elevationMeters = baseAlt + 100,
                points = listOf(
                    GeoPoint(center.latitude - 0.003, center.longitude + 0.003, (baseAlt + 100).toDouble()),
                    GeoPoint(center.latitude + 0.001, center.longitude + 0.004, (baseAlt + 100).toDouble()),
                    GeoPoint(center.latitude + 0.005, center.longitude + 0.003, (baseAlt + 100).toDouble())
                )
            )
        )

        return Triple(trail, waypoints, contours)
    }
}
