package org.nowni.intercom_alpha.map

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.*

class TrailMapManagerTest {

    @Test
    fun testHaversineDistanceAndAzimuth() {
        val p1 = GeoPoint(37.7749, -122.4194, 10.0) // San Francisco
        val p2 = GeoPoint(37.7749, -122.4094, 10.0) // ~880m East

        val distance = TrailMapEngine.calculateDistanceMeters(p1, p2)
        assertTrue(distance in 850.0..920.0, "Distance should be ~880m, was $distance")

        val azimuth = TrailMapEngine.calculateForwardAzimuth(p1, p2)
        assertTrue(abs(azimuth - 90.0) < 1.0, "Azimuth to East should be ~90°, was $azimuth")
    }

    @Test
    fun testRelativeAngleNormalization() {
        // Heading North (0°), target East (90°) -> +90°
        assertEquals(90.0, TrailMapEngine.normalizeRelativeAngle(90.0, 0f), 0.001)

        // Heading East (90°), target North (0°) -> -90°
        assertEquals(-90.0, TrailMapEngine.normalizeRelativeAngle(0.0, 90f), 0.001)

        // Heading South (180°), target North (0°) -> 180° / -180°
        val opp = TrailMapEngine.normalizeRelativeAngle(0.0, 180f)
        assertTrue(opp == 180.0 || opp == -180.0)
    }

    @Test
    fun testProjectToRadarDirectAheadAndRight() {
        val origin = GeoPoint(45.0, 10.0, 500.0)
        // Target 500m North
        val targetNorth = GeoPoint(45.0045, 10.0, 550.0)
        // Target 500m East
        val targetEast = GeoPoint(45.0, 10.0063, 480.0)

        // Case 1: Heading North (0°), target North -> straight ahead (Y negative, X zero)
        val projAhead = TrailMapEngine.projectToRadar(targetNorth, origin, 0f, 1500.0)
        assertTrue(projAhead.isVisibleOnRadar)
        assertTrue(abs(projAhead.normalizedX) < 0.05f)
        assertTrue(projAhead.normalizedY < -0.2f)

        // Case 2: Heading North (0°), target East -> right side (X positive, Y zero)
        val projRight = TrailMapEngine.projectToRadar(targetEast, origin, 0f, 1500.0)
        assertTrue(projRight.isVisibleOnRadar)
        assertTrue(projRight.normalizedX > 0.2f)
        assertTrue(abs(projRight.normalizedY) < 0.05f)

        // Case 3: Heading East (90°), target East -> straight ahead (Y negative, X zero)
        val projTurned = TrailMapEngine.projectToRadar(targetEast, origin, 90f, 1500.0)
        assertTrue(projTurned.isVisibleOnRadar)
        assertTrue(abs(projTurned.normalizedX) < 0.05f)
        assertTrue(projTurned.normalizedY < -0.2f)
    }

    @Test
    fun testTrailMetricsComputation() {
        val points = listOf(
            GeoPoint(0.0, 0.0, 100.0),
            GeoPoint(0.001, 0.0, 150.0), // +50m
            GeoPoint(0.002, 0.0, 120.0), // -30m
            GeoPoint(0.003, 0.0, 200.0)  // +80m
        )
        val (dist, ascent) = TrailMapEngine.computeTrailMetrics(points)
        assertTrue(dist > 300.0, "Total distance should be ~333m, was $dist")
        assertEquals(130.0, ascent, 0.01, "Total ascent should be 50 + 80 = 130m")
    }

    @Test
    fun testGeoJsonParsing() {
        val geoJson = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "id": "wp-1",
                "name": "Eagle Rock",
                "type": "BASECAMP",
                "description": "Camp 1"
              },
              "geometry": {
                "type": "Point",
                "coordinates": [-106.8, 39.1, 2800.0]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "id": "trail-1",
                "name": "Ridgeline Pass"
              },
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [-106.8, 39.1, 2800.0],
                  [-106.81, 39.11, 2950.0],
                  [-106.82, 39.12, 3100.0]
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val (trails, waypoints) = TrailMapEngine.parseGeoJson(geoJson)
        assertEquals(1, waypoints.size)
        assertEquals("Eagle Rock", waypoints[0].name)
        assertEquals(WaypointType.BASECAMP, waypoints[0].type)
        assertEquals(2800.0, waypoints[0].point.altitudeMeters)

        assertEquals(1, trails.size)
        assertEquals("Ridgeline Pass", trails[0].name)
        assertEquals(3, trails[0].points.size)
        assertEquals(300.0, trails[0].totalElevationGainMeters, 0.1)
    }

    @Test
    fun testTrailMapManagerLifecycleAndProjection() = runTest {
        val manager = TrailMapManager(maxBreadcrumbsPerRider = 5, minBreadcrumbDeltaMeters = 5.0)

        val center = GeoPoint(45.5, -122.6, 300.0)
        manager.loadSampleAlpineTrail(center)

        var snap = manager.snapshot.value
        val trail = snap.activeTrail
        assertNotNull(trail)
        assertEquals("Black Bear Pass Trail", trail.name)
        assertEquals(RadarRangeZoom.MEDIUM, snap.activeRangeZoom)

        // Set observer position
        manager.updateLocalPosition(45.5, -122.6, 300.0, 0f, 1000L)
        snap = manager.snapshot.value

        assertEquals(300.0, snap.currentElevationMeters)
        assertEquals(1, snap.projectedTrails.size)
        assertEquals(5, snap.projectedWaypoints.size)
        val nearest = snap.nearestWaypoint
        assertNotNull(nearest)
        assertEquals("Pass Checkpoint", nearest.name)
        assertTrue((snap.nearestWaypointDistanceMeters ?: 999.0) < 5.0)

        // Cycle zoom
        val newZoom = manager.cycleRangeZoom()
        assertEquals(RadarRangeZoom.WIDE, newZoom)
        assertEquals(RadarRangeZoom.WIDE, manager.snapshot.value.activeRangeZoom)

        // Ingest peer position
        manager.updatePeerPosition("rider-beta", 45.501, -122.6, 320.0)
        snap = manager.snapshot.value
        assertTrue(snap.peerBreadcrumbs.containsKey("rider-beta"))
        assertEquals(1, snap.peerBreadcrumbs["rider-beta"]?.size)

        // Test breadcrumb sliding window limit
        for (i in 1..10) {
            manager.updateLocalPosition(45.5 + i * 0.0002, -122.6, 300.0 + i, 0f)
        }
        snap = manager.snapshot.value
        assertTrue(snap.localBreadcrumbs.size <= 5, "Local breadcrumbs must be capped at 5")
    }
}
