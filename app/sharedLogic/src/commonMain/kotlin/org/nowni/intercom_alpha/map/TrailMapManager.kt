package org.nowni.intercom_alpha.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tactical coordinator for offline vector terrain contours, trail tracks, and GPS breadcrumbs.
 *
 * Maintains offline map geometry, calculates radar projections whenever the observer moves
 * or changes vehicle heading, and projects peer breadcrumb paths with zero allocation loops.
 */
class TrailMapManager(
    val maxBreadcrumbsPerRider: Int = 100,
    val minBreadcrumbDeltaMeters: Double = 10.0
) {
    private val mutex = Mutex()

    private var activeTrail: TrailPolyline? = null
    private val waypoints = mutableListOf<TrailWaypoint>()
    private val contours = mutableListOf<ContourLine>()

    private var currentPosition: GeoPoint? = null
    private var currentHeadingDegrees: Float = 0f
    private var activeRangeZoom: RadarRangeZoom = RadarRangeZoom.MEDIUM
    private var isOverlayEnabled: Boolean = true

    // Rolling historical GPS points
    private val localBreadcrumbs = mutableListOf<GeoPoint>()
    private val peerBreadcrumbs = mutableMapOf<String, MutableList<GeoPoint>>()

    private val _snapshot = MutableStateFlow(
        TrailMapSnapshot(
            activeRangeZoom = activeRangeZoom,
            isOverlayEnabled = isOverlayEnabled
        )
    )
    val snapshot: StateFlow<TrailMapSnapshot> = _snapshot.asStateFlow()

    /**
     * Load an active trail into the offline map.
     */
    suspend fun loadTrail(trail: TrailPolyline) = mutex.withLock {
        activeTrail = trail
        recalculateLocked()
    }

    /**
     * Add or update a waypoint.
     */
    suspend fun addWaypoint(waypoint: TrailWaypoint) = mutex.withLock {
        waypoints.removeAll { it.id == waypoint.id }
        waypoints.add(waypoint)
        recalculateLocked()
    }

    /**
     * Ingest GeoJSON and set active trail / waypoints.
     */
    suspend fun loadGeoJson(geoJsonString: String) = mutex.withLock {
        val (parsedTrails, parsedWaypoints) = TrailMapEngine.parseGeoJson(geoJsonString)
        if (parsedTrails.isNotEmpty()) {
            activeTrail = parsedTrails.first()
        }
        waypoints.clear()
        waypoints.addAll(parsedWaypoints)
        recalculateLocked()
    }

    /**
     * Load a sample alpine trail centered around a reference point (e.g., initial GPS fix).
     */
    suspend fun loadSampleAlpineTrail(center: GeoPoint) = mutex.withLock {
        val (sampleTrail, sampleWaypoints, sampleContours) = TrailMapEngine.createSampleAlpineTrail(center)
        activeTrail = sampleTrail
        waypoints.clear()
        waypoints.addAll(sampleWaypoints)
        contours.clear()
        contours.addAll(sampleContours)
        recalculateLocked()
    }

    /**
     * Update active radar range zoom level (500m, 1.5km, 5km).
     */
    suspend fun setRangeZoom(zoom: RadarRangeZoom) = mutex.withLock {
        activeRangeZoom = zoom
        recalculateLocked()
    }

    /**
     * Cycle through radar range zoom levels.
     */
    suspend fun cycleRangeZoom(): RadarRangeZoom = mutex.withLock {
        activeRangeZoom = activeRangeZoom.next()
        recalculateLocked()
        activeRangeZoom
    }

    /**
     * Toggle trail and contour overlay visibility on radar.
     */
    suspend fun setOverlayEnabled(enabled: Boolean) = mutex.withLock {
        isOverlayEnabled = enabled
        recalculateLocked()
    }

    /**
     * Ingest local vehicle GPS fix and recompute radar projections.
     */
    suspend fun updateLocalPosition(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
        headingDegrees: Float,
        timestampMs: Long = 0L
    ) = mutex.withLock {
        val newPos = GeoPoint(latitude, longitude, altitudeMeters)
        currentHeadingDegrees = headingDegrees

        // Check if moved enough to record a new breadcrumb
        val lastBreadcrumb = localBreadcrumbs.lastOrNull()
        if (lastBreadcrumb == null || TrailMapEngine.calculateDistanceMeters(lastBreadcrumb, newPos) >= minBreadcrumbDeltaMeters) {
            localBreadcrumbs.add(newPos)
            if (localBreadcrumbs.size > maxBreadcrumbsPerRider) {
                localBreadcrumbs.removeAt(0)
            }
        }

        currentPosition = newPos
        recalculateLocked(timestampMs)
    }

    /**
     * Ingest remote convoy rider GPS coordinate for breadcrumb tracking.
     */
    suspend fun updatePeerPosition(
        peerId: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double
    ) = mutex.withLock {
        val newPos = GeoPoint(latitude, longitude, altitudeMeters)
        val list = peerBreadcrumbs.getOrPut(peerId) { mutableListOf() }
        val lastPos = list.lastOrNull()
        if (lastPos == null || TrailMapEngine.calculateDistanceMeters(lastPos, newPos) >= minBreadcrumbDeltaMeters) {
            list.add(newPos)
            if (list.size > maxBreadcrumbsPerRider) {
                list.removeAt(0)
            }
        }
        recalculateLocked()
    }

    /**
     * Calculate relative elevation difference from current rider to lead rider.
     */
    fun calculateElevationDelta(leadAltitudeMeters: Double): Double {
        val currentAlt = currentPosition?.altitudeMeters ?: return 0.0
        return leadAltitudeMeters - currentAlt
    }

    /**
     * Clear all breadcrumb trails.
     */
    suspend fun clearBreadcrumbs() = mutex.withLock {
        localBreadcrumbs.clear()
        peerBreadcrumbs.clear()
        recalculateLocked()
    }

    private fun recalculateLocked(timestampMs: Long = 0L) {
        val origin = currentPosition
        val heading = currentHeadingDegrees
        val maxRange = activeRangeZoom.maxDistanceMeters

        if (origin == null) {
            _snapshot.value = TrailMapSnapshot(
                activeTrail = activeTrail,
                activeRangeZoom = activeRangeZoom,
                isOverlayEnabled = isOverlayEnabled,
                timestampMs = timestampMs
            )
            return
        }

        // 1. Project Active Trail
        val projectedTrails = mutableListOf<ProjectedTrailSegment>()
        activeTrail?.let { trail ->
            val projectedPoints = trail.points.map { pt ->
                TrailMapEngine.projectToRadar(pt, origin, heading, maxRange)
            }
            projectedTrails.add(
                ProjectedTrailSegment(
                    trailId = trail.id,
                    trailName = trail.name,
                    difficulty = trail.difficulty,
                    points = projectedPoints
                )
            )
        }

        // 2. Project Waypoints & find nearest
        var nearestWp: TrailWaypoint? = null
        var nearestDist: Double? = null
        val projectedWaypoints = waypoints.map { wp ->
            val proj = TrailMapEngine.projectToRadar(wp.point, origin, heading, maxRange)
            val currentNearest = nearestDist
            if (currentNearest == null || proj.distanceMeters < currentNearest) {
                nearestDist = proj.distanceMeters
                nearestWp = wp
            }
            ProjectedWaypoint(
                waypoint = wp,
                projectedPoint = proj
            )
        }

        // 3. Project Topographic Contours
        val projectedContours = contours.map { c ->
            ProjectedContour(
                elevationMeters = c.elevationMeters,
                points = c.points.map { pt ->
                    TrailMapEngine.projectToRadar(pt, origin, heading, maxRange)
                }
            )
        }

        // 4. Project Local Breadcrumbs
        val projectedLocalBreadcrumbs = localBreadcrumbs.map { pt ->
            TrailMapEngine.projectToRadar(pt, origin, heading, maxRange)
        }

        // 5. Project Peer Breadcrumbs
        val projectedPeerBreadcrumbs = peerBreadcrumbs.mapValues { (_, points) ->
            points.map { pt ->
                TrailMapEngine.projectToRadar(pt, origin, heading, maxRange)
            }
        }

        _snapshot.value = TrailMapSnapshot(
            activeTrail = activeTrail,
            projectedTrails = projectedTrails,
            projectedWaypoints = projectedWaypoints,
            projectedContours = projectedContours,
            peerBreadcrumbs = projectedPeerBreadcrumbs,
            localBreadcrumbs = projectedLocalBreadcrumbs,
            currentElevationMeters = origin.altitudeMeters,
            elevationDeltaToLeadMeters = 0.0,
            activeRangeZoom = activeRangeZoom,
            isOverlayEnabled = isOverlayEnabled,
            nearestWaypoint = nearestWp,
            nearestWaypointDistanceMeters = nearestDist,
            timestampMs = timestampMs
        )
    }
}
