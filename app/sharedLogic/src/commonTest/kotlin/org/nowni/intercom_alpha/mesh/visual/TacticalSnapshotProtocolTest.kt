package org.nowni.intercom_alpha.mesh.visual

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.map.Adler32
import kotlin.test.*

class TacticalSnapshotProtocolTest {

    @Test
    fun testEmptyPayloadChunkingAndIntegrity() {
        val emptyBytes = ByteArray(0)
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = emptyBytes,
            snapshotId = "snap-empty-01",
            senderId = "rider-alpha"
        )

        assertEquals("snap-empty-01", header.snapshotId)
        assertEquals(0L, header.totalBytes)
        assertEquals(1, header.totalChunks)
        assertEquals(1L, header.adler32Checksum)
        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].chunkIndex)
        assertEquals(0, chunks[0].data.size)
        assertEquals(1L, chunks[0].chunkChecksum)

        val buffer = TacticalSnapshotProtocol.ReassemblyBuffer(header)
        assertTrue(buffer.addChunk(chunks[0]))
        assertTrue(buffer.isComplete)
        val reassembled = buffer.reassemble()
        assertNotNull(reassembled)
        assertEquals(0, reassembled.size)
    }

    @Test
    fun testPayloadChunkingUnderMaxMtu() {
        // 1.2 KB simulated compressed thumbnail payload (fits under 1.5 KB limit)
        val testPayload = ByteArray(1200) { (it % 256).toByte() }
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = testPayload,
            snapshotId = "snap-thumb-01",
            senderId = "scout-lead",
            chunkSize = 100 // <= 128 bytes
        )

        assertEquals("snap-thumb-01", header.snapshotId)
        assertEquals(1200L, header.totalBytes)
        assertEquals(12, header.totalChunks)
        assertEquals(12, chunks.size)
        assertEquals(160, header.width)
        assertEquals(120, header.height)
        assertEquals(0.5f, header.framerateLimitFps)

        // Verify each chunk is <= 128 bytes MTU and has correct Adler-32
        for (i in chunks.indices) {
            val chunk = chunks[i]
            assertEquals(i, chunk.chunkIndex)
            assertEquals(12, chunk.totalChunks)
            assertTrue(chunk.data.size <= 128)
            assertEquals(header.adler32Checksum, chunk.payloadChecksum)
            assertEquals(Adler32.checksum(chunk.data), chunk.chunkChecksum)
        }
    }

    @Test
    fun testOutOfOrderReassemblyComplete() {
        val testPayload = ByteArray(850) { ((it * 7) % 256).toByte() }
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = testPayload,
            snapshotId = "snap-ooo-01",
            senderId = "scout-lead",
            chunkSize = 100
        )

        val buffer = TacticalSnapshotProtocol.ReassemblyBuffer(header)
        assertFalse(buffer.isComplete)

        // Feed chunks in reverse / pseudo-random out-of-order sequence
        val permutedChunks = chunks.shuffled()
        for (chunk in permutedChunks) {
            assertTrue(buffer.addChunk(chunk))
        }

        assertTrue(buffer.isComplete)
        assertEquals(emptyList(), buffer.getMissingChunkIndices())

        val reassembled = buffer.reassemble()
        assertNotNull(reassembled)
        assertTrue(testPayload.contentEquals(reassembled))
    }

    @Test
    fun testMissingChunkDetectionAndSelectiveNackRecovery() {
        val testPayload = ByteArray(600) { ((it * 13) % 256).toByte() }
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = testPayload,
            snapshotId = "snap-nack-01",
            senderId = "rider-sweep",
            chunkSize = 100
        )

        assertEquals(6, chunks.size)
        val buffer = TacticalSnapshotProtocol.ReassemblyBuffer(header)

        // Simulate channel loss: drop chunk indices 1 and 4
        for (chunk in chunks) {
            if (chunk.chunkIndex != 1 && chunk.chunkIndex != 4) {
                buffer.addChunk(chunk)
            }
        }

        assertFalse(buffer.isComplete)
        val missing = buffer.getMissingChunkIndices()
        assertEquals(listOf(1, 4), missing)
        assertNull(buffer.reassemble())

        val ack = buffer.createAck()
        assertFalse(ack.isComplete)
        assertEquals(listOf(1, 4), ack.missingChunkIndices)
        assertEquals(listOf(0, 2, 3, 5), ack.receivedChunks)

        // Recover missing chunks via selective NACK retransmission
        assertTrue(buffer.addChunk(chunks[1]))
        assertFalse(buffer.isComplete)
        assertTrue(buffer.addChunk(chunks[4]))
        assertTrue(buffer.isComplete)

        val recovered = buffer.reassemble()
        assertNotNull(recovered)
        assertTrue(testPayload.contentEquals(recovered))
    }

    @Test
    fun testCorruptedChunkRejection() {
        val testPayload = ByteArray(400) { (it % 256).toByte() }
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = testPayload,
            snapshotId = "snap-corrupt-01",
            senderId = "rider-lead",
            chunkSize = 100
        )

        val buffer = TacticalSnapshotProtocol.ReassemblyBuffer(header)

        // Corrupt chunk 1 data bit
        val validChunk = chunks[1]
        val corruptedBytes = validChunk.data.copyOf()
        corruptedBytes[0] = (corruptedBytes[0] + 1).toByte()
        val corruptedChunk = validChunk.copy(data = corruptedBytes) // Checksum doesn't match data

        assertFalse(buffer.addChunk(corruptedChunk))
        assertEquals(0, buffer.receivedCount)

        // Ingest original valid chunk
        assertTrue(buffer.addChunk(validChunk))
        assertEquals(1, buffer.receivedCount)
    }

    @Test
    fun testWireSerializationSymmetry() {
        val header = SnapshotHeader(
            snapshotId = "snap-wire-01",
            senderId = "rider-3",
            timestamp = 1728550000000L,
            totalBytes = 1450L,
            totalChunks = 15,
            mimeType = SnapshotMimeType.JPEG,
            width = 160,
            height = 120,
            framerateLimitFps = 0.5f,
            adler32Checksum = 0x1234ABCDL
        )

        val headerBytes = TacticalSnapshotProtocol.serializeHeader(header)
        val deserializedHeader = TacticalSnapshotProtocol.deserializeHeader(headerBytes)
        assertEquals(header, deserializedHeader)

        val chunk = SnapshotChunk(
            snapshotId = "snap-wire-01",
            chunkIndex = 3,
            totalChunks = 15,
            totalBytes = 1450L,
            data = byteArrayOf(1, 2, 3, 4, 5),
            chunkChecksum = 0x9876L,
            payloadChecksum = 0x1234ABCDL
        )

        val chunkBytes = TacticalSnapshotProtocol.serializeChunk(chunk)
        val deserializedChunk = TacticalSnapshotProtocol.deserializeChunk(chunkBytes)
        assertEquals(chunk, deserializedChunk)

        val ack = SnapshotAck(
            snapshotId = "snap-wire-01",
            chunkIndex = 3,
            receivedChunks = listOf(0, 1, 2, 3),
            missingChunkIndices = emptyList(),
            isComplete = false
        )

        val ackBytes = TacticalSnapshotProtocol.serializeAck(ack)
        val deserializedAck = TacticalSnapshotProtocol.deserializeAck(ackBytes)
        assertEquals(ack, deserializedAck)
    }

    @Test
    fun testSnapshotTransferManagerEndToEnd() = runTest {
        val senderManager = SnapshotTransferManager()
        val receiverManager = SnapshotTransferManager()

        val sentChunks = ArrayList<SnapshotChunk>()
        var sentHeader: SnapshotHeader? = null

        senderManager.onSendHeader = { h, _ ->
            sentHeader = h
        }
        senderManager.onSendChunk = { c, _ ->
            sentChunks.add(c)
        }

        val testPayload = ByteArray(350) { ((it * 3) % 256).toByte() }
        val snapshotId = senderManager.startSendSnapshot(
            payload = testPayload,
            senderId = "rider-lead",
            recipientId = "rider-tail",
            mimeType = SnapshotMimeType.WEBP,
            chunkSize = 100
        )

        assertTrue(snapshotId.startsWith("snap-"))
        assertNotNull(sentHeader)
        assertEquals(4, sentChunks.size) // 350 bytes / 100 = 4 chunks total

        // Ingest header and chunks on receiver
        val header = sentHeader
        assertNotNull(header)
        receiverManager.handleIncomingHeader(header, "rider-lead")

        var finalAck: SnapshotAck? = null
        for (chunk in sentChunks) {
            val ack = receiverManager.handleIncomingChunk(chunk, "rider-lead")
            if (ack != null && ack.isComplete) {
                finalAck = ack
            }
        }

        assertNotNull(finalAck)
        assertTrue(finalAck.isComplete)

        // Verify receiver state
        val latestSnapshot = receiverManager.latestReceivedSnapshot.value
        assertNotNull(latestSnapshot)
        assertEquals(header.snapshotId, latestSnapshot.first.snapshotId)
        assertTrue(testPayload.contentEquals(latestSnapshot.second))

        // Relay final ACK back to sender
        senderManager.handleIncomingAck(finalAck)

        val senderProgress = senderManager.activeTransfers.value.find { it.snapshotId == snapshotId }
        assertNotNull(senderProgress)
        assertEquals(SnapshotTransferStatus.COMPLETED, senderProgress.status)
        assertEquals(1.0f, senderProgress.progressPercent)
    }

    @Test
    fun testSnapshotTransferManagerSelectiveRetransmission() = runTest {
        val senderManager = SnapshotTransferManager()
        val receiverManager = SnapshotTransferManager()

        val sentChunks = ArrayList<SnapshotChunk>()
        var sentHeader: SnapshotHeader? = null

        senderManager.onSendHeader = { h, _ -> sentHeader = h }
        senderManager.onSendChunk = { c, _ -> sentChunks.add(c) }

        val testPayload = ByteArray(500) { (it % 128).toByte() } // 5 chunks of 100 bytes
        val snapshotId = senderManager.startSendSnapshot(
            payload = testPayload,
            senderId = "rider-lead",
            chunkSize = 100
        )

        // Initial burst sent 4 chunks (0, 1, 2, 3), chunk 4 still pending
        assertEquals(4, sentChunks.size)

        // Receiver receives header and chunks 0, 2 (drops 1 and 3)
        val header = sentHeader
        assertNotNull(header)
        receiverManager.handleIncomingHeader(header, "rider-lead")
        receiverManager.handleIncomingChunk(sentChunks[0], "rider-lead")
        val ackAfterChunk2 = receiverManager.handleIncomingChunk(sentChunks[2], "rider-lead")

        assertNotNull(ackAfterChunk2)
        assertFalse(ackAfterChunk2.isComplete)
        // Chunks 1, 3, 4 are missing from receiver's perspective of 5 chunks
        assertTrue(ackAfterChunk2.missingChunkIndices.contains(1))
        assertTrue(ackAfterChunk2.missingChunkIndices.contains(3))

        // Track retransmitted chunks
        val retransmitted = ArrayList<SnapshotChunk>()
        senderManager.onSendChunk = { c, _ -> retransmitted.add(c) }

        senderManager.handleIncomingAck(ackAfterChunk2)

        // Sender should have retransmitted chunks 1 and 3 (from the 4 chunks in its outgoing queue)
        val retransmittedIndices = retransmitted.map { it.chunkIndex }
        assertTrue(retransmittedIndices.contains(1))
        assertTrue(retransmittedIndices.contains(3))

        // Dispatch remaining chunks to finish
        senderManager.continueTransfer(snapshotId, batchSize = 1)
        val chunk4 = retransmitted.find { it.chunkIndex == 4 } ?: run {
            // Send chunk 4
            senderManager.continueTransfer(snapshotId, batchSize = 2)
            retransmitted.find { it.chunkIndex == 4 }
        }
        assertNotNull(chunk4)

        // Feed missing chunks to receiver
        receiverManager.handleIncomingChunk(retransmitted.first { it.chunkIndex == 1 }, "rider-lead")
        receiverManager.handleIncomingChunk(retransmitted.first { it.chunkIndex == 3 }, "rider-lead")
        val finalAck = receiverManager.handleIncomingChunk(chunk4, "rider-lead")

        assertNotNull(finalAck)
        assertTrue(finalAck.isComplete)
    }
}
