package org.nowni.intercom_alpha.map

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MeshFileTransferTest {

    @Test
    fun testAdler32ChecksumConsistency() {
        val emptyBytes = ByteArray(0)
        assertEquals(1L, Adler32.checksum(emptyBytes))

        val data = "Intercom-Alpha Off-Grid Tactical BLE Mesh".encodeToByteArray()
        val c1 = Adler32.checksum(data)
        val c2 = Adler32.checksum(data)
        assertEquals(c1, c2)
        assertTrue(c1 > 1L)

        // Modified byte must change checksum
        val corrupted = data.copyOf()
        corrupted[0] = (corrupted[0] + 1).toByte()
        assertNotEquals(c1, Adler32.checksum(corrupted))
    }

    @Test
    fun testGpxExportAndParseSymmetry() {
        val trail = TrailPolyline(
            id = "trail-alpine-pass",
            name = "Col du Galibier Alpine Pass",
            points = listOf(
                GeoPoint(45.0642, 6.5562, 2642.0),
                GeoPoint(45.0650, 6.5570, 2648.0),
                GeoPoint(45.0660, 6.5580, 2655.0)
            ),
            difficulty = TrailDifficulty.DIFFICULT,
            totalDistanceMeters = 340.0,
            totalElevationGainMeters = 13.0
        )

        val waypoints = listOf(
            TrailWaypoint(
                id = "wp-summit",
                name = "Galibier Summit",
                point = GeoPoint(45.0642, 6.5562, 2642.0),
                type = WaypointType.MOUNTAIN_PASS,
                description = "2,642m French Alps monument"
            ),
            TrailWaypoint(
                id = "wp-shelter",
                name = "Refuge du Galibier",
                point = GeoPoint(45.0660, 6.5580, 2655.0),
                type = WaypointType.SHELTER,
                description = "Stone mountain emergency refuge"
            )
        )

        val gpxXml = MeshFileTransferEngine.exportToGpx(trail, waypoints)
        assertTrue(gpxXml.contains("<gpx"))
        assertTrue(gpxXml.contains("Col du Galibier Alpine Pass"))
        assertTrue(gpxXml.contains("<trkpt"))
        assertTrue(gpxXml.contains("<wpt"))

        val (parsedTrail, parsedWps) = MeshFileTransferEngine.parseGpx(gpxXml)
        assertNotNull(parsedTrail)
        assertEquals("Col du Galibier Alpine Pass", parsedTrail.name)
        assertEquals(3, parsedTrail.points.size)
        assertEquals(2, parsedWps.size)
        assertEquals("Galibier Summit", parsedWps[0].name)
        assertEquals(WaypointType.MOUNTAIN_PASS, parsedWps[0].type)
        assertEquals("Refuge du Galibier", parsedWps[1].name)
        assertEquals(WaypointType.SHELTER, parsedWps[1].type)
    }

    @Test
    fun testGeoJsonExportAndParse() {
        val trail = TrailPolyline(
            id = "trail-ridge",
            name = "Black Ridge Trail",
            points = listOf(
                GeoPoint(37.7749, -122.4194, 100.0),
                GeoPoint(37.7755, -122.4180, 120.0)
            )
        )
        val waypoints = listOf(
            TrailWaypoint(
                id = "wp-camp",
                name = "Camp North",
                point = GeoPoint(37.7749, -122.4194, 100.0),
                type = WaypointType.BASECAMP
            )
        )

        val geoJson = MeshFileTransferEngine.exportToGeoJson(trail, waypoints)
        assertTrue(geoJson.contains("FeatureCollection"))
        assertTrue(geoJson.contains("LineString"))
        assertTrue(geoJson.contains("Black Ridge Trail"))
        assertTrue(geoJson.contains("Camp North"))

        val (parsedTrails, parsedWps) = TrailMapEngine.parseGeoJson(geoJson)
        assertTrue(parsedTrails.isNotEmpty())
        assertEquals("Black Ridge Trail", parsedTrails[0].name)
        assertEquals(1, parsedWps.size)
        assertEquals("Camp North", parsedWps[0].name)
    }

    @Test
    fun testChunkingAndReassemblyComplete() {
        val originalText = "A".repeat(450) + "B".repeat(350)
        val data = originalText.encodeToByteArray()

        val chunks = MeshFileTransferEngine.chunkData(
            bytes = data,
            fileName = "test_route.gpx",
            fileType = FileTransferType.GPX,
            transferId = "tx-test-01",
            chunkSize = 100
        )

        assertEquals(8, chunks.size) // 800 bytes / 100 = 8 chunks
        for (i in chunks.indices) {
            assertEquals(i, chunks[i].chunkIndex)
            assertEquals(8, chunks[i].totalChunks)
            assertEquals(800L, chunks[i].totalBytes)
            assertEquals(chunks[0].adler32Checksum, chunks[i].adler32Checksum)
        }

        val buffer = MeshFileTransferEngine.ReassemblyBuffer(
            transferId = "tx-test-01",
            fileName = "test_route.gpx",
            fileType = FileTransferType.GPX,
            totalChunks = 8,
            totalBytes = 800L,
            expectedChecksum = chunks[0].adler32Checksum
        )

        assertFalse(buffer.isComplete)
        for (chunk in chunks) {
            buffer.addChunk(chunk)
        }
        assertTrue(buffer.isComplete)
        assertEquals(emptyList(), buffer.getMissingChunkIndices())

        val reassembled = buffer.reassemble()
        assertNotNull(reassembled)
        assertEquals(originalText, reassembled.decodeToString())
    }

    @Test
    fun testReassemblyMissingChunksAndSelectiveRecovery() {
        val originalText = "TACTICAL_DATA_".repeat(20)
        val data = originalText.encodeToByteArray()

        val chunks = MeshFileTransferEngine.chunkData(
            bytes = data,
            fileName = "lost_packets.gpx",
            fileType = FileTransferType.GPX,
            transferId = "tx-loss-02",
            chunkSize = 50
        )

        val buffer = MeshFileTransferEngine.ReassemblyBuffer(
            transferId = "tx-loss-02",
            fileName = "lost_packets.gpx",
            fileType = FileTransferType.GPX,
            totalChunks = chunks.size,
            totalBytes = data.size.toLong(),
            expectedChecksum = chunks[0].adler32Checksum
        )

        // Drop chunk index 2
        for (chunk in chunks) {
            if (chunk.chunkIndex != 2) {
                buffer.addChunk(chunk)
            }
        }

        assertFalse(buffer.isComplete)
        assertEquals(listOf(2), buffer.getMissingChunkIndices())
        assertNull(buffer.reassemble())

        // Feed missing chunk 2
        val added = buffer.addChunk(chunks[2])
        assertTrue(added)
        assertTrue(buffer.isComplete)
        assertEquals(emptyList(), buffer.getMissingChunkIndices())

        val recovered = buffer.reassemble()
        assertNotNull(recovered)
        assertEquals(originalText, recovered.decodeToString())
    }

    @Test
    fun testMeshFileTransferManagerLifecycle() = runTest {
        val manager = MeshFileTransferManager()

        // 1. Check initial stored sample trail
        val initialTrails = manager.storedTrails.value
        assertTrue(initialTrails.isNotEmpty())

        val trailToSend = initialTrails.first()

        val sentChunks = ArrayList<FileTransferChunk>()
        manager.onSendChunk = { chunk, _ ->
            sentChunks.add(chunk)
        }

        val transferId = manager.startSendTrail(
            trail = trailToSend,
            format = FileTransferType.GPX
        )

        assertTrue(transferId.startsWith("tx-"))
        assertTrue(sentChunks.isNotEmpty())

        val progress = manager.activeTransfers.value.find { it.transferId == transferId }
        assertNotNull(progress)
        assertEquals(FileTransferStatus.IN_PROGRESS, progress.status)
        assertEquals(FileTransferDirection.OUTGOING, progress.direction)

        // 2. Simulate recipient sending completion ACK
        manager.handleIncomingAck(
            FileTransferAck(
                transferId = transferId,
                chunkIndex = progress.totalChunks - 1,
                missingChunks = emptyList(),
                isComplete = true
            )
        )

        val completedProgress = manager.activeTransfers.value.find { it.transferId == transferId }
        assertNotNull(completedProgress)
        assertEquals(FileTransferStatus.COMPLETED, completedProgress.status)

        // 3. Test direct text import
        val gpxText = MeshFileTransferEngine.exportToGpx(trailToSend, emptyList())
        val imported = manager.importTrailFromText(gpxText, "Imported_Direct.gpx")
        assertNotNull(imported)
        assertTrue(manager.storedTrails.value.any { it.name == trailToSend.name })
    }
}
