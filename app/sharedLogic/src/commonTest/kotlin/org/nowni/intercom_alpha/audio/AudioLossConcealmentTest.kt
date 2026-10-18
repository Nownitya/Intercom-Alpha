package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioLossConcealmentTest {

    @Test
    fun testGoodFramesProcessCleanly() = runTest {
        val plc = AudioLossConcealment(sampleRate = 24000)
        val sineWave = ShortArray(480) { i ->
            (kotlin.math.sin(i * 2.0 * kotlin.math.PI / 48.0) * 10000.0).toInt().toShort()
        }

        val out = plc.processGoodFrame("peer_alice", sineWave)
        assertEquals(480, out.size)
        assertEquals(1L, plc.telemetry.value.totalGoodFramesProcessed)
        assertEquals(0L, plc.telemetry.value.totalFramesConcealed)
        assertEquals(0, plc.getConsecutiveLossCount("peer_alice"))
    }

    @Test
    fun testConcealmentSynthesizesAndAttenuates() = runTest {
        val plc = AudioLossConcealment(sampleRate = 24000, maxConsecutiveLosses = 4)

        // Seed with a 500 Hz tone (48 samples per cycle at 24 kHz)
        val sineWave = ShortArray(480) { i ->
            (kotlin.math.sin(i * 2.0 * kotlin.math.PI / 48.0) * 10000.0).toInt().toShort()
        }

        // Feed 3 good frames so pitch detector establishes lock
        repeat(3) {
            plc.processGoodFrame("peer_bob", sineWave)
        }

        // Frame 1 dropped: trigger concealment
        val concealed1 = plc.generateConcealment("peer_bob", frameSize = 480)
        assertEquals(480, concealed1.size)
        assertEquals(1, plc.getConsecutiveLossCount("peer_bob"))
        val energy1 = concealed1.map { abs(it.toInt()) }.sum()
        assertTrue(energy1 > 0, "Concealed frame 1 must contain audible voice waveform")

        // Frame 2 dropped: trigger concealment (must attenuate)
        val concealed2 = plc.generateConcealment("peer_bob", frameSize = 480)
        assertEquals(2, plc.getConsecutiveLossCount("peer_bob"))
        val energy2 = concealed2.map { abs(it.toInt()) }.sum()
        assertTrue(energy2 > 0, "Concealed frame 2 must contain audible voice waveform")
        assertTrue(energy2 < energy1, "Energy in frame 2 must attenuate compared to frame 1")

        // Frame 3 dropped: further attenuation
        val concealed3 = plc.generateConcealment("peer_bob", frameSize = 480)
        assertEquals(3, plc.getConsecutiveLossCount("peer_bob"))
        val energy3 = concealed3.map { abs(it.toInt()) }.sum()
        assertTrue(energy3 < energy2, "Energy in frame 3 must attenuate compared to frame 2")

        // Frames 4 and 5 dropped: exceeds max consecutive losses -> smooth mute to silence
        plc.generateConcealment("peer_bob", frameSize = 480) // frame 4
        val silenceFrame = plc.generateConcealment("peer_bob", frameSize = 480) // frame 5
        val silenceEnergy = silenceFrame.map { abs(it.toInt()) }.sum()
        assertEquals(0, silenceEnergy, "Losses exceeding limit must decay to complete silence")

        assertEquals(5L, plc.telemetry.value.totalFramesConcealed)
        assertEquals(1, plc.telemetry.value.activeConcealingPeers)
    }

    @Test
    fun testCrossfadeSmoothingOnResumption() = runTest {
        val plc = AudioLossConcealment(sampleRate = 24000)
        val testPcm = ShortArray(480) { (it % 100 * 50).toShort() }

        // Feed voice, drop a frame, then resume
        plc.processGoodFrame("peer_charlie", testPcm)
        plc.generateConcealment("peer_charlie", 480)
        assertEquals(1, plc.getConsecutiveLossCount("peer_charlie"))

        // Resumption frame should blend smoothly and reset loss count to 0
        val resumed = plc.processGoodFrame("peer_charlie", testPcm)
        assertEquals(480, resumed.size)
        assertEquals(0, plc.getConsecutiveLossCount("peer_charlie"))
    }

    @Test
    fun testMultiPeerIsolation() = runTest {
        val plc = AudioLossConcealment(sampleRate = 24000)

        val alicePcm = ShortArray(480) { 1000 }
        val bobPcm = ShortArray(480) { 5000 }

        plc.processGoodFrame("alice", alicePcm)
        plc.processGoodFrame("bob", bobPcm)

        // Drop Alice's frame, but Bob remains good
        plc.generateConcealment("alice", 480)
        plc.processGoodFrame("bob", bobPcm)

        assertEquals(1, plc.getConsecutiveLossCount("alice"))
        assertEquals(0, plc.getConsecutiveLossCount("bob"))
        assertEquals(1, plc.telemetry.value.activeConcealingPeers)
        assertEquals("alice", plc.telemetry.value.lastConcealedPeerId)
    }
}
