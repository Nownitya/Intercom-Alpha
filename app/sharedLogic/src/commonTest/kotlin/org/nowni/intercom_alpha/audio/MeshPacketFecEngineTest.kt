package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeshPacketFecEngineTest {

    @Test
    fun testEnvelopeSerializationRoundTrip() {
        val originalPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x55, 0xAA.toByte())
        val envelope = FecEnvelope(
            blockId = 123456789L,
            indexInBlock = 2,
            dataCount = 4,
            parityCount = 1,
            originalSize = originalPayload.size,
            payload = originalPayload
        )

        val wireBytes = envelope.toByteArray()
        assertEquals(FecEnvelope.HEADER_SIZE + originalPayload.size, wireBytes.size)

        val parsed = FecEnvelope.fromByteArray(wireBytes)
        assertNotNull(parsed)
        assertEquals(envelope.blockId, parsed.blockId)
        assertEquals(envelope.indexInBlock, parsed.indexInBlock)
        assertEquals(envelope.dataCount, parsed.dataCount)
        assertEquals(envelope.parityCount, parsed.parityCount)
        assertEquals(envelope.originalSize, parsed.originalSize)
        assertTrue(envelope.payload.contentEquals(parsed.payload))
    }

    @Test
    fun testDisabledRatioPassThrough() = runTest {
        val engine = MeshPacketFecEngine(initialRatio = FecRatio.DISABLED)
        val testPacket = byteArrayOf(10, 20, 30, 40)

        val envelopes = engine.ingestOutgoingPacket(testPacket)
        assertEquals(1, envelopes.size)
        assertEquals(0, envelopes[0].indexInBlock)
        assertTrue(testPacket.contentEquals(envelopes[0].payload))

        val result = engine.ingestIncomingEnvelope(envelopes[0], timestampMs = 1000L)
        assertTrue(result is FecDecodeResult.Ready)
        assertEquals(1, result.dataPackets.size)
        assertTrue(testPacket.contentEquals(result.dataPackets[0]))
        assertTrue(result.recoveredIndices.isEmpty())
    }

    @Test
    fun testRatio41NoLossImmediateReady() = runTest {
        val engine = MeshPacketFecEngine(initialRatio = FecRatio.RATIO_4_1)
        val packets = List(4) { i -> byteArrayOf(i.toByte(), (i * 2).toByte(), (i * 3).toByte(), 99) }

        val allEnvelopes = mutableListOf<FecEnvelope>()
        for (pkt in packets) {
            val envs = engine.ingestOutgoingPacket(pkt)
            allEnvelopes.addAll(envs)
        }

        // 4 data packets + 1 parity packet = 5 total
        assertEquals(5, allEnvelopes.size)
        for (i in 0..3) {
            assertEquals(i, allEnvelopes[i].indexInBlock)
            assertEquals(false, allEnvelopes[i].isParity)
        }
        assertEquals(4, allEnvelopes[4].indexInBlock)
        assertEquals(true, allEnvelopes[4].isParity)

        // Ingest the 4 data packets (no loss)
        for (i in 0..2) {
            val res = engine.ingestIncomingEnvelope(allEnvelopes[i], timestampMs = 1000L)
            assertTrue(res is FecDecodeResult.Pending)
        }

        val finalRes = engine.ingestIncomingEnvelope(allEnvelopes[3], timestampMs = 1000L)
        assertTrue(finalRes is FecDecodeResult.Ready)
        assertEquals(4, finalRes.dataPackets.size)
        for (i in 0..3) {
            assertTrue(packets[i].contentEquals(finalRes.dataPackets[i]))
        }
        assertTrue(finalRes.recoveredIndices.isEmpty())
    }

    @Test
    fun testRatio41SingleErasureReconstruction() = runTest {
        val engine = MeshPacketFecEngine(initialRatio = FecRatio.RATIO_4_1)
        val packets = listOf(
            byteArrayOf(1, 2, 3, 4),
            byteArrayOf(10, 20, 30, 40),
            byteArrayOf(50, 60, 70, 80),
            byteArrayOf(100, 110, 120, 125)
        )

        val envelopes = mutableListOf<FecEnvelope>()
        for (pkt in packets) {
            envelopes.addAll(engine.ingestOutgoingPacket(pkt))
        }
        assertEquals(5, envelopes.size)

        // Simulate ERASURE of packet 2 (index 2 dropped over RF)
        // Receiver receives: index 0, index 1, index 3, and PARITY (index 4)
        engine.ingestIncomingEnvelope(envelopes[0], timestampMs = 2000L)
        engine.ingestIncomingEnvelope(envelopes[1], timestampMs = 2000L)
        engine.ingestIncomingEnvelope(envelopes[3], timestampMs = 2000L)

        // Parity packet arrives: triggers XOR reconstruction of index 2
        val recoveryResult = engine.ingestIncomingEnvelope(envelopes[4], timestampMs = 2000L)
        assertTrue(recoveryResult is FecDecodeResult.Ready)
        assertEquals(4, recoveryResult.dataPackets.size)
        assertEquals(listOf(2), recoveryResult.recoveredIndices)

        // Verify the recovered packet 2 matches the original byte-for-byte!
        assertTrue(packets[2].contentEquals(recoveryResult.dataPackets[2]))
        for (i in 0..3) {
            assertTrue(packets[i].contentEquals(recoveryResult.dataPackets[i]))
        }

        assertEquals(1L, engine.telemetry.value.totalPacketsRecovered)
        assertEquals(1L, engine.telemetry.value.totalBlocksRecovered)
    }

    @Test
    fun testRatio42DualErasureGaloisReconstruction() = runTest {
        val engine = MeshPacketFecEngine(initialRatio = FecRatio.RATIO_4_2)
        val packets = listOf(
            byteArrayOf(11, 22, 33, 44, 55),
            byteArrayOf(66, 77, 88, 99, 100),
            byteArrayOf(12, 34, 56, 78, 90),
            byteArrayOf(105, 115, 125, 35, 45)
        )

        val envelopes = mutableListOf<FecEnvelope>()
        for (pkt in packets) {
            envelopes.addAll(engine.ingestOutgoingPacket(pkt))
        }
        // 4 data + 2 parity = 6 packets
        assertEquals(6, envelopes.size)

        // Simulate DOUBLE ERASURE: dropped packet 0 and packet 2
        // Received: packet 1, packet 3, Parity 1 (index 4), Parity 2 (index 5)
        val r1 = engine.ingestIncomingEnvelope(envelopes[1], timestampMs = 3000L)
        assertTrue(r1 is FecDecodeResult.Pending)

        val r2 = engine.ingestIncomingEnvelope(envelopes[3], timestampMs = 3000L)
        assertTrue(r2 is FecDecodeResult.Pending)

        val r3 = engine.ingestIncomingEnvelope(envelopes[4], timestampMs = 3000L) // Parity 1
        assertTrue(r3 is FecDecodeResult.Pending)

        // Parity 2 arrives: provides 2nd linear equation in GF(2^8) to reconstruct both 0 and 2
        val r4 = engine.ingestIncomingEnvelope(envelopes[5], timestampMs = 3000L)
        assertTrue(r4 is FecDecodeResult.Ready)
        assertEquals(4, r4.dataPackets.size)
        assertEquals(listOf(0, 2), r4.recoveredIndices)

        // Verify both recovered packets match original payloads exactly!
        assertTrue(packets[0].contentEquals(r4.dataPackets[0]))
        assertTrue(packets[1].contentEquals(r4.dataPackets[1]))
        assertTrue(packets[2].contentEquals(r4.dataPackets[2]))
        assertTrue(packets[3].contentEquals(r4.dataPackets[3]))

        assertEquals(2L, engine.telemetry.value.totalPacketsRecovered)
        assertEquals(1L, engine.telemetry.value.totalBlocksRecovered)
    }

    @Test
    fun testTimeoutPruningEmitsUnrecoverable() = runTest {
        val engine = MeshPacketFecEngine(initialRatio = FecRatio.RATIO_4_1, blockTimeoutMs = 300L)
        val pkt = byteArrayOf(1, 2, 3)
        val envelopes = engine.ingestOutgoingPacket(pkt)
        engine.flushOutgoing()

        // Only packet 0 arrives out of block of 4
        engine.ingestIncomingEnvelope(
            FecEnvelope(
                blockId = 999L,
                indexInBlock = 0,
                dataCount = 4,
                parityCount = 1,
                originalSize = 3,
                payload = pkt
            ),
            timestampMs = 1000L
        )

        // Advance time past 300ms timeout
        val unrecoverable = engine.pruneTimedOutBlocks(currentTimeMs = 1500L)
        assertEquals(1, unrecoverable.size)
        assertEquals(999L, unrecoverable[0].blockId)
        assertEquals(listOf(0), unrecoverable[0].receivedIndices)
        assertEquals(listOf(1, 2, 3), unrecoverable[0].missingIndices)
        assertEquals(1L, engine.telemetry.value.totalBlocksUnrecoverable)
    }
}
