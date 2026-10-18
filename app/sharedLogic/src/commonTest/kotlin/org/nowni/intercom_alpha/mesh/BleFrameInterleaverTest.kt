package org.nowni.intercom_alpha.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleFrameInterleaverTest {

    @Test
    fun testFullBlockInterleavingAndDeinterleavingSymmetry() {
        val config = InterleaverConfig(depthM = 3, framesPerPacketN = 3)
        val interleaver = BleFrameInterleaver(config)

        // Generate 9 distinct frames f0..f8
        val frames = (0 until 9).map { i ->
            SubFrame(
                type = SubFrameType.VOICE_AUDIO,
                payload = byteArrayOf(i.toByte(), 0xAA.toByte()),
                timestampMs = i * 20L
            )
        }

        val emitted = mutableListOf<InterleavedPacket>()
        for (f in frames) {
            emitted.addAll(interleaver.ingestFrame(f))
        }

        // Exactly 3 interleaved packets emitted
        assertEquals(3, emitted.size)
        for (p in emitted) {
            assertEquals(3, p.frames.size)
            assertFalse(p.isEmergency)
        }

        // Column 0 packet should contain f0, f3, f6
        val p0 = emitted[0]
        assertEquals(0, p0.packetIndexInBlock)
        assertEquals(0.toByte(), p0.frames[0].payload[0])
        assertEquals(3.toByte(), p0.frames[1].payload[0])
        assertEquals(6.toByte(), p0.frames[2].payload[0])

        // Column 1 packet should contain f1, f4, f7
        val p1 = emitted[1]
        assertEquals(1, p1.packetIndexInBlock)
        assertEquals(1.toByte(), p1.frames[0].payload[0])
        assertEquals(4.toByte(), p1.frames[1].payload[0])
        assertEquals(7.toByte(), p1.frames[2].payload[0])

        // Column 2 packet should contain f2, f5, f8
        val p2 = emitted[2]
        assertEquals(2, p2.packetIndexInBlock)
        assertEquals(2.toByte(), p2.frames[0].payload[0])
        assertEquals(5.toByte(), p2.frames[1].payload[0])
        assertEquals(8.toByte(), p2.frames[2].payload[0])

        // Deliver all 3 packets to receiver (even out of order: p2, p0, p1)
        val r1 = interleaver.receivePacket(p2, nowMs = 100L)
        assertTrue(r1.isEmpty())

        val r2 = interleaver.receivePacket(p0, nowMs = 105L)
        assertTrue(r2.isEmpty())

        val r3 = interleaver.receivePacket(p1, nowMs = 110L)
        assertEquals(9, r3.size)

        // Verify recovered in exact original order 0..8
        for (i in 0 until 9) {
            val df = r3[i]
            assertEquals(i, df.frameIndex)
            assertFalse(df.isMissing)
            assertNotNull(df.subFrame)
            assertEquals(i.toByte(), df.subFrame.payload[0])
        }
    }

    @Test
    fun testBurstLossDispersion() {
        val config = InterleaverConfig(depthM = 3, framesPerPacketN = 3, maxBlockAssemblyTimeoutMs = 50L)
        val interleaver = BleFrameInterleaver(config)

        // Generate 9 frames f0..f8
        val frames = (0 until 9).map { i ->
            SubFrame(
                type = SubFrameType.VOICE_AUDIO,
                payload = byteArrayOf(i.toByte()),
                timestampMs = i * 20L
            )
        }

        var emitted: List<InterleavedPacket> = emptyList()
        for (f in frames) {
            val res = interleaver.ingestFrame(f)
            if (res.isNotEmpty()) emitted = res
        }
        assertEquals(3, emitted.size)

        // Simulate burst radio fade wiping out Packet 1 entirely:
        // Deliver Packet 0 and Packet 2 only
        interleaver.receivePacket(emitted[0], nowMs = 100L)
        interleaver.receivePacket(emitted[2], nowMs = 120L)

        // Trigger timeout after 60ms
        val recovered = interleaver.checkTimeouts(nowMs = 170L)
        assertEquals(9, recovered.size)

        // Verify that lost frames are strictly isolated (indices 1, 4, 7), NOT clustered
        assertFalse(recovered[0].isMissing)
        assertTrue(recovered[1].isMissing)
        assertFalse(recovered[2].isMissing)
        assertFalse(recovered[3].isMissing)
        assertTrue(recovered[4].isMissing)
        assertFalse(recovered[5].isMissing)
        assertFalse(recovered[6].isMissing)
        assertTrue(recovered[7].isMissing)
        assertFalse(recovered[8].isMissing)

        // Verify intact frames have correct original data
        assertEquals(0.toByte(), recovered[0].subFrame!!.payload[0])
        assertNull(recovered[1].subFrame)
        assertEquals(2.toByte(), recovered[2].subFrame!!.payload[0])
        assertEquals(3.toByte(), recovered[3].subFrame!!.payload[0])
        assertNull(recovered[4].subFrame)
        assertEquals(5.toByte(), recovered[5].subFrame!!.payload[0])
    }

    @Test
    fun testEmergencySosBypass() {
        val interleaver = BleFrameInterleaver()

        val normalFrame = SubFrame(SubFrameType.VOICE_AUDIO, byteArrayOf(0x01))
        val emergencyFrame = SubFrame(SubFrameType.EMERGENCY_SOS, byteArrayOf(0x99.toByte()))

        // Buffer normal frame
        val r1 = interleaver.ingestFrame(normalFrame)
        assertTrue(r1.isEmpty())

        // Emergency frame should flush normal frame, and emit emergency frame immediately
        val r2 = interleaver.ingestFrame(emergencyFrame)
        assertEquals(2, r2.size)

        val flushedNormal = r2[0]
        assertFalse(flushedNormal.isEmergency)

        val emergencyPacket = r2[1]
        assertTrue(emergencyPacket.isEmergency)
        assertEquals(1, emergencyPacket.frames.size)
        assertEquals(SubFrameType.EMERGENCY_SOS, emergencyPacket.frames[0].type)

        // Receiver receives emergency packet: must play out immediately
        val playout = interleaver.receivePacket(emergencyPacket, nowMs = 50L)
        assertEquals(1, playout.size)
        assertFalse(playout[0].isMissing)
        assertEquals(SubFrameType.EMERGENCY_SOS, playout[0].subFrame!!.type)
    }

    @Test
    fun testPartialBlockFlush() {
        val interleaver = BleFrameInterleaver(InterleaverConfig(depthM = 3, framesPerPacketN = 3))

        // Ingest only 4 frames
        for (i in 0 until 4) {
            interleaver.ingestFrame(SubFrame(SubFrameType.VOICE_AUDIO, byteArrayOf(i.toByte())))
        }

        val flushed = interleaver.flush()
        assertTrue(flushed.isNotEmpty())

        val received = mutableListOf<DeinterleavedFrame>()
        for (p in flushed) {
            received.addAll(interleaver.receivePacket(p, nowMs = 0L))
        }

        // All 4 frames recovered in order
        assertEquals(4, received.size)
        for (i in 0 until 4) {
            assertEquals(i.toByte(), received[i].subFrame!!.payload[0])
        }
    }
}
