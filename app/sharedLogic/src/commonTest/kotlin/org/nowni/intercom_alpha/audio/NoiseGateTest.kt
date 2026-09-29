package org.nowni.intercom_alpha.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoiseGateTest {

    private val sampleRate = 24000
    private val frameSize = 480 // 20ms at 24kHz

    private fun generateSineWave(freq: Double, samples: Int, amplitude: Short): ShortArray {
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
    fun testSubThresholdNoiseAttenuated() {
        val gate = NoiseGate(
            sampleRate = sampleRate,
            openThresholdDb = -35.0f,
            closeThresholdDb = -42.0f,
            floorGainDb = -60.0f
        )

        // Generate low level noise at ~-50 dBFS (amplitude ~100 out of 32768)
        val lowNoise = generateSineWave(100.0, frameSize * 3, amplitude = 100)
        val gated = gate.process(lowNoise)

        assertFalse(gate.isOpen, "Gate must remain closed for sub-threshold noise")
        val outRms = calculateRms(gated)
        assertTrue(outRms < 5.0, "Gated output RMS ($outRms) must be heavily attenuated")
    }

    @Test
    fun testSpeechOpensGateSmoothly() {
        val gate = NoiseGate(
            sampleRate = sampleRate,
            openThresholdDb = -35.0f,
            closeThresholdDb = -42.0f,
            attackTimeMs = 5.0f
        )

        // Generate loud speech at ~-10 dBFS (amplitude 10000)
        val speech = generateSineWave(500.0, frameSize * 2, amplitude = 10000)
        val gated = gate.process(speech)

        assertTrue(gate.isOpen, "Gate must open when speech exceeds open threshold")
        assertTrue(gate.currentGain > 0.95f, "Gain should reach near 1.0 after attack")
        val outRms = calculateRms(gated)
        assertTrue(outRms > 5000.0, "Voice audio must pass cleanly through open gate")
    }

    @Test
    fun testHysteresisPreventsChatter() {
        val gate = NoiseGate(
            sampleRate = sampleRate,
            openThresholdDb = -30.0f,
            closeThresholdDb = -40.0f
        )

        // Mid-level signal: amplitude ~1800 (RMS ~1272 -> ~-28 dBFS? Let's calibrate: 20*log10(1000/32768) = -30.3 dBFS)
        // Amplitude 900 -> RMS ~636 -> ~-34.2 dBFS (between -30dB and -40dB)
        val midSignal = generateSineWave(400.0, frameSize, amplitude = 900)

        // Case A: Gate is currently CLOSED -> midSignal should NOT open it
        gate.process(midSignal)
        assertFalse(gate.isOpen, "Signal between thresholds must not open a closed gate")

        // Case B: Open gate with loud signal
        val loudSignal = generateSineWave(400.0, frameSize, amplitude = 15000)
        gate.process(loudSignal)
        assertTrue(gate.isOpen, "Loud signal must open gate")

        // Now feed midSignal again -> should REMAIN OPEN due to hysteresis
        gate.process(midSignal)
        assertTrue(gate.isOpen, "Signal above closeThreshold must keep an open gate open")
    }

    @Test
    fun testHoldTimeMaintainsGateDuringPauses() {
        val gate = NoiseGate(
            sampleRate = sampleRate,
            openThresholdDb = -30.0f,
            closeThresholdDb = -40.0f,
            holdTimeMs = 100.0f, // 100ms hold = 5 frames of 20ms
            releaseTimeMs = 50.0f
        )

        // Open gate with voice
        val voice = generateSineWave(300.0, frameSize, amplitude = 12000)
        gate.process(voice)
        assertTrue(gate.isOpen)

        // Feed 3 frames of silence (60ms < 100ms hold)
        val silence = ShortArray(frameSize)
        for (f in 0 until 3) {
            gate.process(silence)
            assertTrue(gate.isOpen, "Gate must remain open during hold period (frame $f)")
        }
    }

    @Test
    fun testReleaseRampsDownAfterHoldExpires() {
        val gate = NoiseGate(
            sampleRate = sampleRate,
            openThresholdDb = -30.0f,
            closeThresholdDb = -40.0f,
            holdTimeMs = 20.0f, // 20ms hold = 1 frame
            releaseTimeMs = 40.0f, // 40ms release = 2 frames
            floorGainDb = -60.0f
        )

        // Open gate
        val voice = generateSineWave(300.0, frameSize, amplitude = 12000)
        gate.process(voice)
        assertTrue(gate.isOpen)

        // Silence frame 1: hold active
        val silence = ShortArray(frameSize)
        gate.process(silence)

        // Silence frame 2 & 3: releasing
        gate.process(silence)
        gate.process(silence)

        // Silence frame 4: should be closed
        gate.process(silence)
        assertFalse(gate.isOpen, "Gate must close after hold + release elapse")
    }

    @Test
    fun testResetClosesGateImmediately() {
        val gate = NoiseGate(sampleRate = sampleRate)
        val loud = generateSineWave(440.0, frameSize, amplitude = 15000)

        gate.process(loud)
        assertTrue(gate.isOpen)

        gate.reset()
        assertFalse(gate.isOpen, "Gate must be closed immediately after reset")
        assertEquals(-120.0f, gate.currentEnvelopeDb)
    }
}
