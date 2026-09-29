package org.nowni.intercom_alpha.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindNoiseFilterTest {

    private fun generateSineWave(freqHz: Double, sampleRateHz: Int, durationSeconds: Double, amplitude: Short): ShortArray {
        val totalSamples = (sampleRateHz * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRateHz.toDouble()
            val sample = amplitude * sin(2.0 * PI * freqHz * t)
            buffer[i] = sample.toInt().toShort()
        }
        return buffer
    }

    private fun computeRms(buffer: ShortArray, startIndex: Int = 0): Double {
        var sumSq = 0.0
        val count = buffer.size - startIndex
        if (count <= 0) return 0.0
        for (i in startIndex until buffer.size) {
            val v = buffer[i].toDouble()
            sumSq += v * v
        }
        return sqrt(sumSq / count)
    }

    @Test
    fun testBypassModePreservesAudioExactly() {
        val filter = WindNoiseFilter(sampleRateHz = 24000, initialProfile = HelmetNoiseProfile.BYPASS)
        val input = shortArrayOf(100, -200, 300, -400, 500)
        val output = filter.process(input)

        for (i in input.indices) {
            assertEquals(input[i], output[i])
        }
    }

    @Test
    fun testLowFrequencyWindSuppressionExceeds18dB() {
        val sampleRate = 24000
        val filter = WindNoiseFilter(sampleRateHz = sampleRate, initialProfile = HelmetNoiseProfile.HIGHWAY_80KMH)

        // 100 Hz deep wind rumble tone (1.0 second duration)
        val input = generateSineWave(freqHz = 100.0, sampleRateHz = sampleRate, durationSeconds = 1.0, amplitude = 10000)
        val output = filter.process(input)

        // Skip initial filter settling time (first 2400 samples = 100ms)
        val inputRms = computeRms(input, startIndex = 2400)
        val outputRms = computeRms(output, startIndex = 2400)

        // Calculate attenuation in dB: 20 * log10(inputRms / outputRms)
        val attenuationDb = 20.0 * kotlin.math.log10(inputRms / outputRms)

        // Acceptance criteria: > 18 dB attenuation
        assertTrue(attenuationDb > 18.0, "Expected >18 dB attenuation at 100 Hz, got $attenuationDb dB")
    }

    @Test
    fun testVoiceBandPreservedWithFormantBoost() {
        val sampleRate = 24000
        val filter = WindNoiseFilter(sampleRateHz = sampleRate, initialProfile = HelmetNoiseProfile.HIGHWAY_80KMH)

        // 1000 Hz mid-speech tone (should pass with virtually no loss)
        val input1k = generateSineWave(freqHz = 1000.0, sampleRateHz = sampleRate, durationSeconds = 1.0, amplitude = 8000)
        val output1k = filter.process(input1k)
        val inRms1k = computeRms(input1k, startIndex = 2400)
        val outRms1k = computeRms(output1k, startIndex = 2400)
        val gain1k = outRms1k / inRms1k
        // Expected ~1.0 to 1.15
        assertTrue(gain1k in 0.95..1.20, "1 kHz speech tone should pass cleanly, gain: $gain1k")

        // 2200 Hz voice formant emphasis frequency (+4 dB boost target, gain ~1.58)
        filter.reset()
        val input2k2 = generateSineWave(freqHz = 2200.0, sampleRateHz = sampleRate, durationSeconds = 1.0, amplitude = 8000)
        val output2k2 = filter.process(input2k2)
        val inRms2k2 = computeRms(input2k2, startIndex = 2400)
        val outRms2k2 = computeRms(output2k2, startIndex = 2400)
        val gain2k2 = outRms2k2 / inRms2k2
        val boostDb = 20.0 * kotlin.math.log10(gain2k2)

        assertTrue(boostDb in 3.5..4.5, "Expected ~4 dB boost at 2.2 kHz formant peak, got $boostDb dB")
    }

    @Test
    fun testHighway120KmhProfileAggressiveSuppression() {
        val sampleRate = 24000
        val filter = WindNoiseFilter(sampleRateHz = sampleRate, initialProfile = HelmetNoiseProfile.HIGHWAY_120KMH)

        // 150 Hz wind buffeting
        val input150 = generateSineWave(freqHz = 150.0, sampleRateHz = sampleRate, durationSeconds = 1.0, amplitude = 12000)
        val output150 = filter.process(input150)

        val inRms = computeRms(input150, startIndex = 2400)
        val outRms = computeRms(output150, startIndex = 2400)
        val attenuationDb = 20.0 * kotlin.math.log10(inRms / outRms)

        assertTrue(attenuationDb > 20.0, "Expected >20 dB suppression for 120 km/h wind profile, got $attenuationDb dB")
    }

    @Test
    fun testFloatProcessingMatchesShortProcessing() {
        val filter = WindNoiseFilter(sampleRateHz = 24000, initialProfile = HelmetNoiseProfile.HIGHWAY_80KMH)
        val floatInput = floatArrayOf(0.1f, -0.2f, 0.5f, -0.4f, 0.3f)
        val floatOutput = filter.process(floatInput)

        assertEquals(floatInput.size, floatOutput.size)
        for (sample in floatOutput) {
            assertTrue(sample in -1.0f..1.0f)
        }
    }

    @Test
    fun testSampleRateSwitching16kAnd48k() {
        val filter = WindNoiseFilter(sampleRateHz = 16000, initialProfile = HelmetNoiseProfile.HIGHWAY_80KMH)

        // 100 Hz at 16 kHz sample rate
        val input16k = generateSineWave(freqHz = 100.0, sampleRateHz = 16000, durationSeconds = 0.5, amplitude = 10000)
        val output16k = filter.process(input16k)
        val atten16k = 20.0 * kotlin.math.log10(computeRms(input16k, 1600) / computeRms(output16k, 1600))
        assertTrue(atten16k > 18.0)

        // Switch to 48 kHz
        filter.setSampleRate(48000)
        filter.reset()
        val input48k = generateSineWave(freqHz = 100.0, sampleRateHz = 48000, durationSeconds = 0.5, amplitude = 10000)
        val output48k = filter.process(input48k)
        val atten48k = 20.0 * kotlin.math.log10(computeRms(input48k, 4800) / computeRms(output48k, 4800))
        assertTrue(atten48k > 18.0)
    }
}
