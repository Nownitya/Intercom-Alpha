package org.nowni.intercom_alpha.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BleMtuAggregatorTest {

    @Test
    fun testBasicPackingAndUnpackingSymmetry() {
        val aggregator = BleMtuAggregator(
            BleMtuAggregatorConfig(targetFramesPerPacket = 3, defaultMtu = 247)
        )

        val frame1 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val frame2 = byteArrayOf(0x05, 0x06, 0x07, 0x08, 0x09)
        val frame3 = byteArrayOf(0x0A, 0x0B, 0x0C)

        val p1 = aggregator.addFrame(frame1, SubFrameType.VOICE_AUDIO, 1000L)
        assertTrue(p1.isEmpty())

        val p2 = aggregator.addFrame(frame2, SubFrameType.VOICE_AUDIO, 1020L)
        assertTrue(p2.isEmpty())

        val p3 = aggregator.addFrame(frame3, SubFrameType.VOICE_AUDIO, 1040L)
        assertEquals(1, p3.size)

        val packet = p3[0]
        assertEquals(3, packet.frameCount)
        assertFalse(packet.isEmergency)
        assertEquals(0, packet.sequenceNumber)

        // Unpack
        val unpacked = aggregator.unpack(packet.rawBytes)
        assertEquals(3, unpacked.size)
        assertEquals(SubFrameType.VOICE_AUDIO, unpacked[0].type)
        assertTrue(frame1.contentEquals(unpacked[0].payload))

        assertEquals(SubFrameType.VOICE_AUDIO, unpacked[1].type)
        assertTrue(frame2.contentEquals(unpacked[1].payload))

        assertEquals(SubFrameType.VOICE_AUDIO, unpacked[2].type)
        assertTrue(frame3.contentEquals(unpacked[2].payload))
    }

    @Test
    fun testCapacityBoundOverflowFlush() {
        // Negotiated MTU 64 bytes -> effective capacity = 64 - 3 - 4 = 57 bytes
        val aggregator = BleMtuAggregator(
            BleMtuAggregatorConfig(defaultMtu = 64, targetFramesPerPacket = 10)
        )

        // 30 bytes payload + 2 header = 32 bytes wire per frame
        val largeFrame1 = ByteArray(30) { it.toByte() }
        val largeFrame2 = ByteArray(30) { (it + 5).toByte() }

        val p1 = aggregator.addFrame(largeFrame1, SubFrameType.VOICE_AUDIO)
        assertTrue(p1.isEmpty())

        // Adding largeFrame2 would need 32 + 32 = 64 bytes > 57 bytes, so frame 1 flushes
        val p2 = aggregator.addFrame(largeFrame2, SubFrameType.VOICE_AUDIO)
        assertEquals(1, p2.size)
        assertEquals(1, p2[0].frameCount)
        assertTrue(largeFrame1.contentEquals(p2[0].subFrames[0].payload))

        // Force flush of frame 2
        val p3 = aggregator.flush()
        assertNotNull(p3)
        assertEquals(1, p3.frameCount)
        assertTrue(largeFrame2.contentEquals(p3.subFrames[0].payload))
    }

    @Test
    fun testEmergencySosImmediateFlush() {
        val aggregator = BleMtuAggregator(
            BleMtuAggregatorConfig(targetFramesPerPacket = 4)
        )

        val normalFrame = byteArrayOf(0x11, 0x22)
        val emergencyFrame = byteArrayOf(0xEE.toByte(), 0xFF.toByte())

        // Add 1 normal frame (buffered)
        val p1 = aggregator.addFrame(normalFrame, SubFrameType.VOICE_AUDIO)
        assertTrue(p1.isEmpty())

        // Add emergency frame: must flush normal frame, then flush emergency frame immediately
        val p2 = aggregator.addFrame(emergencyFrame, SubFrameType.EMERGENCY_SOS)
        assertEquals(2, p2.size)

        // First packet: flushed normal frame
        assertEquals(1, p2[0].frameCount)
        assertFalse(p2[0].isEmergency)
        assertEquals(SubFrameType.VOICE_AUDIO, p2[0].subFrames[0].type)

        // Second packet: emergency frame
        assertEquals(1, p2[1].frameCount)
        assertTrue(p2[1].isEmergency)
        assertEquals(SubFrameType.EMERGENCY_SOS, p2[1].subFrames[0].type)
    }

    @Test
    fun testMaxHoldTimeoutFlush() {
        val aggregator = BleMtuAggregator(
            BleMtuAggregatorConfig(targetFramesPerPacket = 4, maxHoldDelayMs = 20L)
        )

        aggregator.addFrame(byteArrayOf(0x01), SubFrameType.VOICE_AUDIO, timestampMs = 1000L)

        // After 10ms: under threshold -> null
        val noFlush = aggregator.flushPending(currentTimeMs = 1010L)
        assertNull(noFlush)

        // After 25ms: exceeded 20ms threshold -> flushed
        val flushed = aggregator.flushPending(currentTimeMs = 1025L)
        assertNotNull(flushed)
        assertEquals(1, flushed.frameCount)
    }

    @Test
    fun testTelemetryAccurateRfReduction() {
        val aggregator = BleMtuAggregator(
            BleMtuAggregatorConfig(targetFramesPerPacket = 3, defaultMtu = 247)
        )

        // Ingest 9 frames -> exactly 3 packets
        for (i in 0 until 9) {
            aggregator.addFrame(ByteArray(32) { i.toByte() }, SubFrameType.VOICE_AUDIO)
        }

        val telemetry = aggregator.getTelemetry()
        assertEquals(247, telemetry.negotiatedMtu)
        assertEquals(9L, telemetry.totalFramesIngested)
        assertEquals(3L, telemetry.totalPacketsEmitted)
        assertEquals(3.0, telemetry.averageFramesPerPacket)

        // 9 frames would have been 9 packets. Packing into 3 packets saves 6 packets: 6/9 = 66.666...%
        assertTrue(telemetry.rfPacketReductionPct > 66.0)
        assertTrue(telemetry.rfPacketReductionPct < 67.0)
        assertTrue(telemetry.packingEfficiencyPct > 80.0) // high payload-to-overhead ratio
    }

    @Test
    fun testCorruptAndShortPayloadUnpack() {
        val aggregator = BleMtuAggregator()

        // Empty bytes
        assertTrue(aggregator.unpack(ByteArray(0)).isEmpty())

        // Wrong magic byte
        val badMagic = byteArrayOf(0xAA.toByte(), 0x00, 0x01, 0x01, 0x02, 0x01, 0x55, 0x66)
        assertTrue(aggregator.unpack(badMagic).isEmpty())

        // Truncated sub-frame length
        val truncated = byteArrayOf(BleMtuAggregator.MAGIC_BYTE, 0x00, 0x01, 0x01, 0x10, 0x01, 0x55)
        assertTrue(aggregator.unpack(truncated).isEmpty())
    }
}
