package org.nowni.intercom_alpha.geo

import org.nowni.intercom_alpha.map.GeoPoint
import kotlin.test.*

class OfflineVectorTileEngineTest {

    private lateinit var decoder: MvtTileDecoder
    private lateinit var engine: OfflineVectorTileEngine

    @BeforeTest
    fun setUp() {
        decoder = MvtTileDecoder()
        engine = OfflineVectorTileEngine(maxCachedTiles = 4, decoder = decoder)
    }

    private fun createSyntheticTileBytes(
        layerName: String,
        featureId: Long,
        points: List<Pair<Int, Int>>, // tile coordinates delta
        properties: Map<String, String> = emptyMap()
    ): ByteArray {
        val out = mutableListOf<Byte>()

        // 1. Tag with layer name
        val layerNameBytes = layerName.encodeToByteArray()
        out.add((layerNameBytes.size and 0x0F).toByte())
        for (b in layerNameBytes) out.add(b)

        // 2. Feature count = 1
        out.add(1.toByte())

        // 3. Feature ID (2 bytes)
        out.add((featureId and 0xFF).toByte())
        out.add(((featureId shr 8) and 0xFF).toByte())

        // 4. Geom type (2 = LineString)
        out.add(2.toByte())

        // 5. Coord count
        out.add((points.size and 0xFF).toByte())

        // 6. Coordinates (zig-zag encoded)
        for ((dx, dy) in points) {
            val encX = decoder.encodeZigZag(dx)
            val encY = decoder.encodeZigZag(dy)
            out.add((encX and 0xFF).toByte())
            out.add(((encX shr 8) and 0xFF).toByte())
            out.add((encY and 0xFF).toByte())
            out.add(((encY shr 8) and 0xFF).toByte())
        }

        // 7. Properties
        out.add((properties.size and 0xFF).toByte())
        for ((k, v) in properties) {
            val kBytes = k.encodeToByteArray()
            out.add((kBytes.size and 0xFF).toByte())
            for (b in kBytes) out.add(b)

            val vBytes = v.encodeToByteArray()
            out.add((vBytes.size and 0xFF).toByte())
            for (b in vBytes) out.add(b)
        }

        return out.toByteArray()
    }

    @Test
    fun testBoundingBoxCalculationsAndIntersection() {
        val bbox = BoundingBox(minLat = 45.0, minLon = 6.0, maxLat = 46.0, maxLon = 7.0)

        // Point inside
        assertTrue(bbox.contains(GeoPoint(45.5, 6.5)))
        // Point on boundary
        assertTrue(bbox.contains(GeoPoint(45.0, 6.0)))
        // Points outside
        assertFalse(bbox.contains(GeoPoint(44.9, 6.5)))
        assertFalse(bbox.contains(GeoPoint(45.5, 7.1)))

        // Intersecting bbox
        val overlapping = BoundingBox(minLat = 45.5, minLon = 6.5, maxLat = 46.5, maxLon = 7.5)
        assertTrue(bbox.intersects(overlapping))

        // Disjoint bbox
        val disjoint = BoundingBox(minLat = 47.0, minLon = 8.0, maxLat = 48.0, maxLon = 9.0)
        assertFalse(bbox.intersects(disjoint))

        // From points
        val points = listOf(
            GeoPoint(45.1, 6.2),
            GeoPoint(45.8, 6.9),
            GeoPoint(45.4, 6.5)
        )
        val envelope = BoundingBox.fromPoints(points)
        assertEquals(45.1, envelope.minLat, 0.001)
        assertEquals(6.2, envelope.minLon, 0.001)
        assertEquals(45.8, envelope.maxLat, 0.001)
        assertEquals(6.9, envelope.maxLon, 0.001)
    }

    @Test
    fun testTileCoordinateConversions() {
        // Paris approx: 48.8566 N, 2.3522 E at zoom 10
        val coord = TileCoord.fromGeoPoint(48.8566, 2.3522, 10)
        assertEquals(10, coord.z)
        assertTrue(coord.x in 510..525, "X should be in expected Paris tile range")
        assertTrue(coord.y in 345..360, "Y should be in expected Paris tile range")

        // Invert to bounding box
        val bbox = coord.toBoundingBox()
        assertTrue(bbox.contains(GeoPoint(48.8566, 2.3522)), "Calculated bbox must contain the original coordinates")

        // Intersecting tiles for small region
        val tiles = TileCoord.getTilesIntersecting(bbox, 10)
        assertTrue(tiles.contains(coord), "Tiles list must contain enclosing tile")
    }

    @Test
    fun testZigZagEncodingAndDecoding() {
        val testValues = listOf(0, -1, 1, -2, 2, 100, -100, 2048, -2048, 4096)
        for (v in testValues) {
            val encoded = decoder.encodeZigZag(v)
            val decoded = decoder.decodeZigZag(encoded)
            assertEquals(v, decoded, "ZigZag encode/decode must match for $v")
        }
    }

    @Test
    fun testDecoderTileParsing() {
        val coord = TileCoord(12, 2137, 1445)
        val tileBytes = createSyntheticTileBytes(
            layerName = "trails",
            featureId = 101L,
            points = listOf(100 to 100, 50 to 50, 150 to -30),
            properties = mapOf("difficulty" to "MODERATE", "name" to "Alpine Ridge Trail")
        )

        val tile = decoder.decodeTile(coord, tileBytes)
        assertEquals(coord, tile.coord)
        assertTrue(tile.layers.containsKey("trails"))

        val features = tile.layers["trails"]!!
        assertEquals(1, features.size)
        val feature = features[0]
        assertEquals(101L, feature.id)
        assertEquals(VectorGeometryType.LINESTRING, feature.geometryType)
        assertEquals(3, feature.points.size)
        assertEquals("MODERATE", feature.properties["difficulty"])
        assertEquals("Alpine Ridge Trail", feature.properties["name"])
    }

    @Test
    fun testOfflineVectorTileEngineCachingAndQuerying() {
        val coord1 = TileCoord(12, 2137, 1445)
        val bbox1 = coord1.toBoundingBox()

        val tileBytes1 = createSyntheticTileBytes(
            layerName = "trails",
            featureId = 101L,
            points = listOf(500 to 500, 100 to 100),
            properties = mapOf("name" to "Pass Trail")
        )
        engine.storeTile(coord1, tileBytes1)
        assertTrue(engine.hasTile(coord1))

        // Initial query: Cache miss, then parse and cache
        val results1 = engine.queryBoundingBox(bbox1, zoomLevel = 12)
        assertEquals(1, results1.size)
        assertEquals("Pass Trail", results1[0].properties["name"])

        var telemetry = engine.getTelemetry()
        assertEquals(1, telemetry.cachedTilesCount)
        assertEquals(1L, telemetry.totalQueriesExecuted)
        assertEquals(1L, telemetry.totalCacheMisses)
        assertEquals(0L, telemetry.totalCacheHits)

        // Second query on same bbox: Cache HIT
        val results2 = engine.queryBoundingBox(bbox1, zoomLevel = 12)
        assertEquals(1, results2.size)

        telemetry = engine.getTelemetry()
        assertEquals(2L, telemetry.totalQueriesExecuted)
        assertEquals(1L, telemetry.totalCacheHits)

        // Query with layer filter
        val trailResults = engine.queryBoundingBox(bbox1, zoomLevel = 12, layerFilter = setOf("trails"))
        assertEquals(1, trailResults.size)

        val roadResults = engine.queryBoundingBox(bbox1, zoomLevel = 12, layerFilter = setOf("roads"))
        assertEquals(0, roadResults.size, "Filtered layer should return 0 features")

        // Query latency must be sub-15ms
        assertTrue(telemetry.lastQueryTimeMs < 15.0, "Spatial query must complete in < 15ms")
    }

    @Test
    fun testLruEvictionWhenExceedingCapacity() {
        // Cache max capacity configured to 4 in setUp
        for (i in 0..5) {
            val coord = TileCoord(12, 2000 + i, 1000)
            val bytes = createSyntheticTileBytes("contours", i.toLong(), listOf(10 to 10))
            engine.storeTile(coord, bytes)
            engine.getTile(coord) // load into LRU
        }

        val telemetry = engine.getTelemetry()
        assertTrue(telemetry.cachedTilesCount <= 4, "Cache must not exceed max capacity of 4 tiles")
    }
}
