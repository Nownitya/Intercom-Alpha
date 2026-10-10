package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpectralNoiseSuppressorTest {

    @Test
    fun testInitializationAndFftSizeValidation() {
        val suppressor = SpectralNoiseSuppressor(sampleRateHz = 16000, fftSize = 256)
        assertEquals(16000, suppressor.sampleRateHz)
        assertEquals(256, suppressor.fftSize)
        assertEquals(NoiseSuppressionMode.AGGRESSIVE, suppressor.profile.mode)
    }

    @Test
    fun testBypassModeLeavesAudioUnchanged() = runTest {
        val suppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.OFF
        )

        val input = ShortArray(512) { i -> (1000 * sin(2.0 * PI * 440.0 * i / 16000)).toInt().toShort() }
        val output = suppressor.processSamples(input)

        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i])
        }
    }

    @Test
    fun testPcmByteProcessingRoundtrip() = runTest {
        val suppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.MILD
        )

        val sampleCount = 512
        val inputBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sample = (2000 * sin(2.0 * PI * 800.0 * i / 16000)).toInt().toShort()
            inputBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            inputBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val outputBytes = suppressor.processPcmBytes(inputBytes)
        assertEquals(inputBytes.size, outputBytes.size)

        val outputSamples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = outputBytes[i * 2].toInt() and 0xFF
            val high = outputBytes[i * 2 + 1].toInt()
            outputSamples[i] = ((high shl 8) or low).toShort()
        }

        // Verify that tone signal was preserved
        var maxAmplitude = 0
        for (s in outputSamples) {
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > maxAmplitude) maxAmplitude = absVal
        }
        assertTrue(maxAmplitude > 200, "Clean 800 Hz speech formant tone should be retained")
    }

    @Test
    fun testNoiseAttenuationOnTurbulentNoise() = runTest {
        val suppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.AGGRESSIVE
        )

        // Generate 1280 samples (5 x 256 frames) of synthetic wind noise
        val random = Random(12345)
        val noiseSamples = ShortArray(1280) { (random.nextInt(-3000, 3000)).toShort() }

        // Process noise through suppressor
        val outputSamples = suppressor.processSamples(noiseSamples)
        assertEquals(noiseSamples.size, outputSamples.size)

        // Measure RMS of input vs later output frames (after initial calibration)
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        val evalStart = 512
        for (i in evalStart until noiseSamples.size) {
            inputEnergy += noiseSamples[i].toDouble() * noiseSamples[i].toDouble()
            outputEnergy += outputSamples[i].toDouble() * outputSamples[i].toDouble()
        }

        assertTrue(outputEnergy < inputEnergy, "Spectral suppressor must attenuate stationary noise")
        val attenuationRatio = inputEnergy / (outputEnergy + 1.0)
        assertTrue(attenuationRatio > 1.8, "Noise attenuation ratio should be at least 1.8x (observed: $attenuationRatio)")
    }

    @Test
    fun testSpeechTonePreservationUnderModerateNoise() = runTest {
        val suppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.AGGRESSIVE
        )

        val random = Random(999)
        // 1. Initial 4 frames of pure background wind noise (for calibration)
        val calibSamples = ShortArray(1024) { (random.nextInt(-1500, 1500)).toShort() }
        suppressor.processSamples(calibSamples)

        // 2. High-energy speech tone (1200 Hz) mixed with noise
        val mixedSamples = ShortArray(1024) { i ->
            val tone = (8000.0 * sin(2.0 * PI * 1200.0 * i / 16000)).toInt()
            val noise = random.nextInt(-1500, 1500)
            (tone + noise).coerceIn(-32768, 32767).toShort()
        }

        val enhancedSamples = suppressor.processSamples(mixedSamples)

        // Inspect diagnostics
        val diag = suppressor.latestDiagnostics.value
        assertTrue(diag.isSpeechPresent, "Should classify 8000-amplitude 1200 Hz tone as speech")
        assertTrue(diag.estimatedSnrDb > 4.0f, "Estimated SNR should exceed speech threshold")

        // Check stats
        val stats = suppressor.getStats()
        assertTrue(stats.speechFramesCount > 0L, "Speech frame count must be positive")
        assertTrue(stats.framesProcessed >= 8L, "Processed frames should count across both batches")
    }

    @Test
    fun testProfileSwitchingAndReset() = runTest {
        val suppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.MILD
        )

        assertEquals(NoiseSuppressionMode.MILD, suppressor.profile.mode)
        suppressor.updateProfile(SpectralNoiseProfile.EXTREME_ALPINE)
        assertEquals(NoiseSuppressionMode.EXTREME_ALPINE, suppressor.profile.mode)
        assertEquals(3.5f, suppressor.profile.overSubtractionAlpha)

        // Run some audio
        val samples = ShortArray(512) { 1000 }
        suppressor.processSamples(samples)
        assertTrue(suppressor.getStats().framesProcessed > 0L)

        // Reset
        suppressor.reset()
        val statsAfterReset = suppressor.getStats()
        assertEquals(0L, statsAfterReset.framesProcessed)
        assertEquals(0L, statsAfterReset.speechFramesCount)
    }
}
