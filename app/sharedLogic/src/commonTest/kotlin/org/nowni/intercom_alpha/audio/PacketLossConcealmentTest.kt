package org.nowni.intercom_alpha.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketLossConcealmentTest {

    private val sampleRate = 24000
    private val frameSize = 480 // 20ms at 24kHz

    private fun generateSineWave(freq: Double, samples: Int, amplitude: Short = 16000): ShortArray {
        val result = ShortArray(samples)
        for (i in 0 until samples) {
            val angle = 2.0 * PI * freq * i / sampleRate
            result[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return result
    }

    private fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            sum += s.toDouble() * s.toDouble()
        }
        return sqrt(sum / samples.size)
    }

    @Test
    fun testGoodFramePassthrough() {
        val plc = PacketLossConcealment(sampleRate = sampleRate)
        val sine = generateSineWave(440.0, frameSize)

        val output = plc.pushGoodFrame(sine)
        assertEquals(frameSize, output.size)
        assertEquals(0, plc.consecutiveLossCount)

        // Verifies exact passthrough when no prior loss occurred
        for (i in 0 until frameSize) {
            assertEquals(sine[i], output[i])
        }
    }

    @Test
    fun testConcealmentPitchSynthesis() {
        val plc = PacketLossConcealment(sampleRate = sampleRate)
        val sine = generateSineWave(220.0, frameSize * 3) // Feed 60ms of audio to build pitch history

        plc.pushGoodFrame(sine.copyOfRange(0, frameSize))
        plc.pushGoodFrame(sine.copyOfRange(frameSize, frameSize * 2))
        plc.pushGoodFrame(sine.copyOfRange(frameSize * 2, frameSize * 3))

        // Lost packet concealment
        val concealed = plc.conceal(frameSize)
        assertEquals(frameSize, concealed.size)
        assertEquals(1, plc.consecutiveLossCount)

        val concealedRms = calculateRms(concealed)
        assertTrue(concealedRms > 5000.0, "Concealed frame must synthesize voiced audio, RMS=$concealedRms")
    }

    @Test
    fun testExponentialEnergyDecay() {
        val plc = PacketLossConcealment(
            sampleRate = sampleRate,
            maxConsecutiveLosses = 5,
            lossAttenuationFactor = 0.70794578f
        )
        val sine = generateSineWave(300.0, frameSize * 3)

        plc.pushGoodFrame(sine.copyOfRange(0, frameSize))
        plc.pushGoodFrame(sine.copyOfRange(frameSize, frameSize * 2))
        plc.pushGoodFrame(sine.copyOfRange(frameSize * 2, frameSize * 3))

        var prevRms = Double.MAX_VALUE
        for (frame in 1..4) {
            val concealed = plc.conceal(frameSize)
            val rms = calculateRms(concealed)
            assertTrue(rms < prevRms, "Frame $frame RMS ($rms) must be lower than previous ($prevRms)")
            prevRms = rms
        }
    }

    @Test
    fun testSilenceAfterMaxLosses() {
        val plc = PacketLossConcealment(sampleRate = sampleRate, maxConsecutiveLosses = 3)
        val sine = generateSineWave(440.0, frameSize * 2)

        plc.pushGoodFrame(sine.copyOfRange(0, frameSize))
        plc.pushGoodFrame(sine.copyOfRange(frameSize, frameSize * 2))

        // Frame 1, 2, 3 concealed
        plc.conceal(frameSize)
        plc.conceal(frameSize)
        plc.conceal(frameSize)

        // 4th frame exceeds maxConsecutiveLosses (3) -> Must return pure silence
        val silentFrame = plc.conceal(frameSize)
        assertEquals(frameSize, silentFrame.size)
        for (sample in silentFrame) {
            assertEquals(0.toShort(), sample)
        }
    }

    @Test
    fun testSmoothCrossfadeRecovery() {
        val plc = PacketLossConcealment(sampleRate = sampleRate)
        val sine = generateSineWave(250.0, frameSize * 3)

        plc.pushGoodFrame(sine.copyOfRange(0, frameSize))
        plc.pushGoodFrame(sine.copyOfRange(frameSize, frameSize * 2))

        // Induce loss
        plc.conceal(frameSize)
        assertEquals(1, plc.consecutiveLossCount)

        // Recover with next good frame
        val nextGoodFrame = sine.copyOfRange(frameSize * 2, frameSize * 3)
        val recovered = plc.pushGoodFrame(nextGoodFrame)

        assertEquals(frameSize, recovered.size)
        assertEquals(0, plc.consecutiveLossCount)

        val recoveredRms = calculateRms(recovered)
        assertTrue(recoveredRms > 5000.0, "Recovered frame must retain signal energy without dropouts")
    }

    @Test
    fun testResetClearsHistoryAndState() {
        val plc = PacketLossConcealment(sampleRate = sampleRate)
        val sine = generateSineWave(440.0, frameSize)

        plc.pushGoodFrame(sine)
        plc.conceal(frameSize)
        assertTrue(plc.consecutiveLossCount > 0)

        plc.reset()
        assertEquals(0, plc.consecutiveLossCount)

        // Conceal without history after reset must yield silence
        val concealed = plc.conceal(frameSize)
        for (sample in concealed) {
            assertEquals(0.toShort(), sample)
        }
    }
}
