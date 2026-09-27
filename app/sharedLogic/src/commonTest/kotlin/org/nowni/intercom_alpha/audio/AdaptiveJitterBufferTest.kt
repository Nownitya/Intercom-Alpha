package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdaptiveJitterBufferTest {

    private fun makePacket(sequence: Long, timestamp: Long): MeshPacket.Audio {
        return MeshPacket.Audio(
            senderId = "rider-1",
            sequence = sequence,
            timestamp = timestamp,
            data = byteArrayOf(0x01, 0x02, (sequence and 0xFF).toByte()),
            profile = AudioProfile.MEDIUM
        )
    }

    @Test
    fun testInOrderPlayout() = runTest {
        val buffer = AdaptiveJitterBuffer(minDelayMs = 40, maxDelayMs = 120, baseDelayMs = 40)

        val t0 = 1000L
        buffer.push(makePacket(1, 100), t0)
        buffer.push(makePacket(2, 120), t0 + 20)
        buffer.push(makePacket(3, 140), t0 + 40)

        // Before playoutStartTime (1000 + 40 = 1040)
        val early = buffer.pull(1020L)
        assertIs<PlayoutFrame.Buffering>(early)

        // At deadline (1040)
        val f1 = buffer.pull(1040L)
        assertIs<PlayoutFrame.Available>(f1)
        assertEquals(1L, f1.sequence)

        // At next frame (1060)
        val f2 = buffer.pull(1060L)
        assertIs<PlayoutFrame.Available>(f2)
        assertEquals(2L, f2.sequence)

        // At next frame (1080)
        val f3 = buffer.pull(1080L)
        assertIs<PlayoutFrame.Available>(f3)
        assertEquals(3L, f3.sequence)
    }

    @Test
    fun testOutOfOrderReordering() = runTest {
        val buffer = AdaptiveJitterBuffer(minDelayMs = 40, maxDelayMs = 120, baseDelayMs = 40)

        val t0 = 1000L
        // Packets arrive out of order: 1, 3, 2
        buffer.push(makePacket(1, 100), t0)
        buffer.push(makePacket(3, 140), t0 + 10)
        buffer.push(makePacket(2, 120), t0 + 20)

        val f1 = buffer.pull(1040L)
        assertIs<PlayoutFrame.Available>(f1)
        assertEquals(1L, f1.sequence)

        val f2 = buffer.pull(1060L)
        assertIs<PlayoutFrame.Available>(f2)
        assertEquals(2L, f2.sequence)

        val f3 = buffer.pull(1080L)
        assertIs<PlayoutFrame.Available>(f3)
        assertEquals(3L, f3.sequence)
    }

    @Test
    fun testStaleAndDuplicatePacketsRejected() = runTest {
        val buffer = AdaptiveJitterBuffer(minDelayMs = 40, maxDelayMs = 120, baseDelayMs = 40)

        val t0 = 1000L
        buffer.push(makePacket(10, 100), t0)

        // Duplicate rejection
        val dupPushed = buffer.push(makePacket(10, 100), t0 + 5)
        assertFalse(dupPushed, "Duplicate packet should be rejected")

        // Play packet 10
        val f10 = buffer.pull(1040L)
        assertIs<PlayoutFrame.Available>(f10)
        assertEquals(10L, f10.sequence)

        // Late arriving packet 9 arrives after sequence advanced to 11
        val latePushed = buffer.push(makePacket(9, 80), t0 + 45)
        assertFalse(latePushed, "Stale arrival before expected sequence should be rejected")
    }

    @Test
    fun testMissingPacketEmitsConcealment() = runTest {
        val buffer = AdaptiveJitterBuffer(minDelayMs = 40, maxDelayMs = 120, baseDelayMs = 40)

        val t0 = 1000L
        // Packet 2 is lost in transit!
        buffer.push(makePacket(1, 100), t0)
        buffer.push(makePacket(3, 140), t0 + 40)

        // Frame 1 available
        val f1 = buffer.pull(1040L)
        assertIs<PlayoutFrame.Available>(f1)
        assertEquals(1L, f1.sequence)

        // Frame 2 is missing -> Concealment
        val f2 = buffer.pull(1060L)
        assertIs<PlayoutFrame.Concealment>(f2)
        assertEquals(2L, f2.expectedSequence)

        // Frame 3 available
        val f3 = buffer.pull(1080L)
        assertIs<PlayoutFrame.Available>(f3)
        assertEquals(3L, f3.sequence)
    }

    @Test
    fun testDynamicJitterAdaptation() = runTest {
        val buffer = AdaptiveJitterBuffer(minDelayMs = 40, maxDelayMs = 150, baseDelayMs = 40)

        // Simulate high jitter (irregular arrival deltas vs sender timestamps)
        var senderTime = 1000L
        var localTime = 2000L

        buffer.push(makePacket(1, senderTime), localTime)

        // Jittery arrivals: sender increments by 20ms, arrival fluctuates by 80ms
        for (i in 2..15) {
            senderTime += 20L
            val jitterDelta = if (i % 2 == 0) 90L else 10L
            localTime += jitterDelta
            buffer.push(makePacket(i.toLong(), senderTime), localTime)
        }

        // Verify that running jitter was calculated and target delay scaled up from baseDelayMs
        assertTrue(buffer.currentJitterMs > 5.0, "Jitter estimate should increase under irregular timing")
        assertTrue(buffer.currentTargetDelayMs > buffer.baseDelayMs, "Target delay should scale up with jitter")
        assertTrue(buffer.currentTargetDelayMs <= buffer.maxDelayMs, "Target delay must remain clamped to maxDelayMs")
    }

    @Test
    fun testBufferReset() = runTest {
        val buffer = AdaptiveJitterBuffer()
        buffer.push(makePacket(1, 100), 1000L)
        assertEquals(1, buffer.size())

        buffer.reset()
        assertEquals(0, buffer.size())
        assertEquals(0.0, buffer.currentJitterMs)
        val res = buffer.pull(2000L)
        assertIs<PlayoutFrame.Empty>(res)
    }
}
