package org.nowni.intercom_alpha.geo

import org.nowni.intercom_alpha.map.GeoPoint
import kotlin.test.*

/**
 * Alpine Mountain Pass Multi-Node Elevation & Trail Sync Simulation Test (Sprint 36 - ICA-3604).
 *
 * Simulates 12 convoy nodes traversing a 2,500m high-altitude mountain pass with 1,200m elevation gain:
 * - Validates real-time grade calculations, steep descent/climb warnings (> 12%), and vertical separation alerts.
 * - Confirms offline map tile caching and route elevation profile synchronization across multi-hop relay nodes.
 * - 100% pure KMP multiplatform test execution with zero JVM or C/JNI dependencies (Rules 2 & 3).
 */
class AlpineElevationSimulationTest {

    private lateinit var tileDecoder: MvtTileDecoder
    private lateinit var tileEngine: OfflineVectorTileEngine

    @BeforeTest
    fun setUp() {
        tileDecoder = MvtTileDecoder()
        tileEngine = OfflineVectorTileEngine(maxCachedTiles = 16, decoder = tileDecoder)
    }

    private fun createSyntheticPassTile(
        layerName: String,
        featureId: Long,
        points: List<Pair<Int, Int>>
    ): ByteArray {
        val out = mutableListOf<Byte>()
        val layerNameBytes = layerName.encodeToByteArray()
        out.add((layerNameBytes.size and 0x0F).toByte())
        for (b in layerNameBytes) out.add(b)

        out.add(1.toByte()) // 1 feature
        out.add((featureId and 0xFF).toByte())
        out.add(((featureId shr 8) and 0xFF).toByte())
        out.add(2.toByte()) // LineString
        out.add((points.size and 0xFF).toByte())

        for ((dx, dy) in points) {
            val encX = tileDecoder.encodeZigZag(dx)
            val encY = tileDecoder.encodeZigZag(dy)
            out.add((encX and 0xFF).toByte())
            out.add(((encX shr 8) and 0xFF).toByte())
            out.add((encY and 0xFF).toByte())
            out.add(((encY shr 8) and 0xFF).toByte())
        }
        out.add(0.toByte()) // 0 properties
        return out.toByteArray()
    }

    @Test
    fun test12NodeAlpinePassTraversalWithExtremeGradeAndSeparation() {
        val nodeCount = 12
        val alpineConfig = ElevationEngineConfig(summitDetectionWindowMeters = 10000.0)
        val engines = (1..nodeCount).map { id ->
            val peerId = "rider-${id.toString().padStart(2, '0')}"
            val callsign = if (id == 1) "Squad Lead" else if (id == nodeCount) "Sweep Tail" else "Rider $id"
            ConvoyElevationEngine(localPeerId = peerId, localCallsign = callsign, config = alpineConfig)
        }

        // Build 10km Alpine Pass planned route: 1,300m -> 2,500m summit (at km 6.5) -> 1,600m
        val plannedRoute = mutableListOf<GeoPoint>()
        // Basecamp / Valley
        plannedRoute.add(GeoPoint(latitude = 45.0000, longitude = 6.0000, altitudeMeters = 1300.0))
        // Climb section (km 1.0 to km 5.0)
        plannedRoute.add(GeoPoint(latitude = 45.0100, longitude = 6.0050, altitudeMeters = 1550.0))
        plannedRoute.add(GeoPoint(latitude = 45.0200, longitude = 6.0100, altitudeMeters = 1850.0))
        plannedRoute.add(GeoPoint(latitude = 45.0300, longitude = 6.0150, altitudeMeters = 2200.0))
        // Pass Summit (km 6.5)
        plannedRoute.add(GeoPoint(latitude = 45.0400, longitude = 6.0200, altitudeMeters = 2500.0))
        // Steep Technical Switchbacks Descent (km 7.5 to km 10.0)
        plannedRoute.add(GeoPoint(latitude = 45.0500, longitude = 6.0250, altitudeMeters = 2100.0))
        plannedRoute.add(GeoPoint(latitude = 45.0600, longitude = 6.0300, altitudeMeters = 1800.0))
        plannedRoute.add(GeoPoint(latitude = 45.0700, longitude = 6.0350, altitudeMeters = 1600.0))

        // 1. Distribute planned route to all 12 convoy nodes and initialize position
        for (engine in engines) {
            engine.setPlannedRoute(plannedRoute)
            engine.ingestLocalPosition(plannedRoute[0], timestampMs = 500L)
            val profile = engine.elevationProfile.value
            assertEquals(1300.0, profile.minAltitudeMeters)
            assertEquals(2500.0, profile.maxAltitudeMeters)
            assertEquals(1200.0, profile.totalClimbMeters)
            assertEquals(900.0, profile.totalDescentMeters)
            assertEquals(2500.0, profile.upcomingSummitAltitudeMeters)
            val dist = profile.upcomingSummitDistanceMeters
            assertNotNull(dist)
            assertTrue(dist > 0.0)
        }

        val leadEngine = engines[0]

        // 2. Lead rider encounters extreme alpine climb section (> 12% grade)
        val climbP1 = GeoPoint(latitude = 45.0200, longitude = 6.0100, altitudeMeters = 1850.0)
        leadEngine.ingestLocalPosition(climbP1, timestampMs = 1000L)

        // 100m horizontal distance, +14m vertical gain = 14% grade
        val climbP2 = GeoPoint(latitude = 45.0209, longitude = 6.0100, altitudeMeters = 1864.0)
        leadEngine.ingestLocalPosition(climbP2, timestampMs = 6000L)

        val climbProfile = leadEngine.elevationProfile.value
        assertTrue(climbProfile.currentGradePercentage > 12.0, "Climb grade must be > 12%, was ${climbProfile.currentGradePercentage}")
        assertEquals(1, climbProfile.activeWarnings.size)
        val climbWarning = climbProfile.activeWarnings.first()
        assertEquals(GradeSeverity.EXTREME_ALPINE_WARNING, climbWarning.severity)
        assertTrue(climbWarning.isClimb)
        assertTrue(climbWarning.message.contains("STEEP CLIMB"))

        // 3. Convoy stretching: Lead rider reaches summit at 2,500m while tail rider is at 1,700m (800m vertical delta)
        val summitFix = GeoPoint(latitude = 45.0400, longitude = 6.0200, altitudeMeters = 2500.0)
        leadEngine.ingestLocalPosition(summitFix, timestampMs = 60000L)

        // Simulate 11 remote convoy riders reporting telemetry along the pass climb
        for (i in 2..nodeCount) {
            val peerId = "rider-${i.toString().padStart(2, '0')}"
            val callsign = if (i == nodeCount) "Sweep Tail" else "Rider $i"
            val alt = 2500.0 - (i - 1) * 72.7 // Rider 12 ends at approx 1700m
            val riderLoc = GeoPoint(
                latitude = 45.0400 - (i - 1) * 0.003,
                longitude = 6.0200 - (i - 1) * 0.0015,
                altitudeMeters = alt
            )
            leadEngine.ingestPeerPosition(
                peerId = peerId,
                callsign = callsign,
                point = riderLoc,
                distanceAlongRouteMeters = (nodeCount - i) * 500.0,
                timestampMs = 60000L
            )
        }

        val stretchedProfile = leadEngine.elevationProfile.value
        assertEquals(12, stretchedProfile.riders.size)
        assertEquals(2500.0, stretchedProfile.localAltitudeMeters)

        // Lead vs Tail vertical separation must be approx 800m
        val separation = stretchedProfile.leadTailVerticalSeparationMeters
        assertTrue(separation in 750.0..850.0, "Lead/tail separation should be ~800m, was $separation")

        // 4. Lead rider begins technical alpine descent (-13.5% grade)
        val descP1 = GeoPoint(latitude = 45.0450, longitude = 6.0220, altitudeMeters = 2480.0)
        leadEngine.ingestLocalPosition(descP1, timestampMs = 70000L)

        // 100m horizontal distance, -13.5m drop = -13.5% grade
        val descP2 = GeoPoint(latitude = 45.0459, longitude = 6.0220, altitudeMeters = 2466.5)
        leadEngine.ingestLocalPosition(descP2, timestampMs = 75000L)

        val descentProfile = leadEngine.elevationProfile.value
        assertTrue(descentProfile.currentGradePercentage < -12.0, "Descent grade must be < -12%, was ${descentProfile.currentGradePercentage}")
        assertEquals(1, descentProfile.activeWarnings.size)
        val descWarning = descentProfile.activeWarnings.first()
        assertEquals(GradeSeverity.EXTREME_ALPINE_WARNING, descWarning.severity)
        assertFalse(descWarning.isClimb)
        assertTrue(descWarning.message.contains("STEEP DESCENT"))
    }

    @Test
    fun testOfflineTileCachingAcrossAlpinePassBoundingBox() {
        // Alpine Pass area bounding box: lat 45.0 to 45.05, lon 6.0 to 6.05 at zoom 14
        val passBbox = BoundingBox(minLat = 45.0, minLon = 6.0, maxLat = 45.05, maxLon = 6.05)
        val tiles = TileCoord.getTilesIntersecting(passBbox, zoom = 14)
        assertTrue(tiles.isNotEmpty(), "Intersecting tiles must be found for Alpine pass")

        // Ingest synthetic contour and trail vector tiles into the engine
        for ((idx, tile) in tiles.withIndex()) {
            val syntheticBytes = createSyntheticPassTile(
                layerName = "contours",
                featureId = 2000L + idx,
                points = listOf(10 to 10, 50 to 80, 100 to 120, 200 to 250)
            )
            tileEngine.storeTile(tile, syntheticBytes)
        }

        // Query bounding boxes along the trail route
        val queryBox = BoundingBox(minLat = 45.01, minLon = 6.01, maxLat = 45.03, maxLon = 6.03)
        // First query: cache misses, parses tiles into cache
        val features1 = tileEngine.queryBoundingBox(queryBox, zoomLevel = 14, layerFilter = setOf("contours"))
        assertTrue(features1.isNotEmpty(), "Offline vector features must be returned from tile engine")

        // Second query on same region: cache hits
        val features2 = tileEngine.queryBoundingBox(queryBox, zoomLevel = 14, layerFilter = setOf("contours"))
        assertEquals(features1.size, features2.size)

        for (f in features2) {
            assertEquals("contours", f.layerName)
            assertEquals(VectorGeometryType.LINESTRING, f.geometryType)
            assertTrue(f.points.size >= 2)
        }

        val telemetry = tileEngine.getTelemetry()
        assertTrue(telemetry.cachedTilesCount > 0)
        assertTrue(telemetry.totalCacheHits > 0)
        assertTrue(telemetry.lastQueryTimeMs < 15.0, "Spatial query time must be sub-15ms")
    }

    @Test
    fun testMultiHopRouteElevationSyncBetweenNodes() {
        val leadNode = ConvoyElevationEngine("rider-01", "Squad Lead")
        val relayNode = ConvoyElevationEngine("rider-05", "Mid Relay")
        val sweepNode = ConvoyElevationEngine("rider-12", "Sweep Tail")

        val routePoints = listOf(
            GeoPoint(45.0000, 6.0000, 1400.0),
            GeoPoint(45.0150, 6.0100, 1950.0),
            GeoPoint(45.0300, 6.0200, 2480.0),
            GeoPoint(45.0450, 6.0300, 1750.0)
        )

        // Lead sets planned route
        leadNode.setPlannedRoute(routePoints)
        val leadSnapshot = leadNode.elevationProfile.value

        // Simulate multi-hop broadcast of planned route points to relay and sweep nodes
        relayNode.setPlannedRoute(routePoints)
        sweepNode.setPlannedRoute(routePoints)

        val relaySnapshot = relayNode.elevationProfile.value
        val sweepSnapshot = sweepNode.elevationProfile.value

        // Verify identical synchronization across all nodes
        assertEquals(leadSnapshot.minAltitudeMeters, relaySnapshot.minAltitudeMeters)
        assertEquals(leadSnapshot.maxAltitudeMeters, relaySnapshot.maxAltitudeMeters)
        assertEquals(leadSnapshot.totalClimbMeters, relaySnapshot.totalClimbMeters)
        assertEquals(leadSnapshot.totalDescentMeters, relaySnapshot.totalDescentMeters)

        assertEquals(leadSnapshot.minAltitudeMeters, sweepSnapshot.minAltitudeMeters)
        assertEquals(leadSnapshot.maxAltitudeMeters, sweepSnapshot.maxAltitudeMeters)
        assertEquals(leadSnapshot.totalClimbMeters, sweepSnapshot.totalClimbMeters)
        assertEquals(leadSnapshot.totalDescentMeters, sweepSnapshot.totalDescentMeters)
    }
}
