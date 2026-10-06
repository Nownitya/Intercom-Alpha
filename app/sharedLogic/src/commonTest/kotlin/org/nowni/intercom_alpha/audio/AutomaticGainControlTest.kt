package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutomaticGainControlTest {

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
            val d = s.toDouble()
            sum += d * d
        }
        return sqrt(sum / samples.size)
    }

    private fun toDbFs(rms: Double): Float {
        return if (rms > 0.0) {
            (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
        } else {
            -120.0f
        }
    }

    @Test
    fun testWhisperNormalizedToTargetRmsWithin150ms() {
        val agc = AutomaticGainControl(
            sampleRate = sampleRate,
            targetLevelDb = -18.0f,
            maxGainDb = 18.0f,
            minGainDb = -12.0f,
            noiseFloorDb = -50.0f,
            attackTimeMs = 60.0f,
            releaseTimeMs = 300.0f
        )

        // Quiet speech at ~ -32 dBFS (amplitude = 1150 out of 32768)
        // RMS of sine with amplitude 1150 is 1150 / sqrt(2) = 813. 20*log10(813/32768) ~= -32.1 dBFS
        val quietSpeech = generateSineWave(440.0, frameSize, amplitude = 1150)
        val initialInputDb = toDbFs(calculateRms(quietSpeech))
        assertTrue(initialInputDb < -30.0f, "Initial input ($initialInputDb dBFS) should be quiet whisper")

        // Feed frames for 160ms (8 frames of 20ms)
        var lastOutput = ShortArray(0)
        for (frame in 1..8) {
            lastOutput = agc.process(quietSpeech)
        }

        val finalOutputRmsDb = toDbFs(calculateRms(lastOutput))
        // Target is -18.0 dBFS, within 160ms it should reach within 2.5 dB of target (-20.5 to -15.5 dBFS)
        assertTrue(
            finalOutputRmsDb in -21.0f..-15.0f,
            "Normalized output ($finalOutputRmsDb dBFS) should be near -18.0 dBFS target within 150ms"
        )
        assertTrue(agc.currentGainDb > 8.0f, "AGC should boost quiet speech gain (gain was ${agc.currentGainDb} dB)")
        assertFalse(agc.isHolding, "Active speech must not trigger anti-pumping hold")
    }

    @Test
    fun testAntiPumpingSuppressesNoiseAmplification() {
        val agc = AutomaticGainControl(
            sampleRate = sampleRate,
            targetLevelDb = -18.0f,
            maxGainDb = 18.0f,
            noiseFloorDb = -50.0f,
            attackTimeMs = 60.0f
        )

        // Ambient road noise at ~ -60 dBFS (amplitude ~45 out of 32768)
        val ambientNoise = generateSineWave(120.0, frameSize, amplitude = 45)
        val noiseDb = toDbFs(calculateRms(ambientNoise))
        assertTrue(noiseDb < -55.0f, "Ambient noise ($noiseDb dBFS) should be well below noise floor")

        // Feed 10 frames of noise (200ms)
        var lastOutput = ShortArray(0)
        for (i in 1..10) {
            lastOutput = agc.process(ambientNoise)
        }

        assertTrue(agc.isHolding, "Signals below noise floor must trigger anti-pumping hold")
        assertEquals(0.0f, agc.currentGainDb, 0.001f, "AGC must not pump up gain during silence/noise")
        val outDb = toDbFs(calculateRms(lastOutput))
        assertTrue(outDb < -55.0f, "Noise must remain low without pumping (measured $outDb dBFS)")
    }

    @Test
    fun testLoudShoutAttenuatedSmoothly() {
        val agc = AutomaticGainControl(
            sampleRate = sampleRate,
            targetLevelDb = -18.0f,
            minGainDb = -12.0f,
            releaseTimeMs = 150.0f
        )

        // Loud shout at ~ -6 dBFS (amplitude = 23000 out of 32768)
        val loudSpeech = generateSineWave(500.0, frameSize, amplitude = 23000)
        val inputDb = toDbFs(calculateRms(loudSpeech))
        assertTrue(inputDb > -8.0f, "Input ($inputDb dBFS) should be loud shout")

        // Feed 10 frames (200ms)
        var lastOutput = ShortArray(0)
        for (i in 1..10) {
            lastOutput = agc.process(loudSpeech)
        }

        assertTrue(agc.currentGainDb < -5.0f, "AGC should attenuate loud shout (gain: ${agc.currentGainDb} dB)")
        val outDb = toDbFs(calculateRms(lastOutput))
        assertTrue(outDb < -14.0f, "Output level ($outDb dBFS) should be attenuated toward -18 dBFS")
    }

    @Test
    fun testDynamicRangeCompressorSoftKneeCurve() {
        val drc = DynamicRangeCompressor(
            sampleRate = sampleRate,
            thresholdDb = -12.0f,
            ratio = 4.0f,
            kneeWidthDb = 6.0f
        )

        // 1. Below knee: -20 dBFS -> Gain reduction must be exactly 0 dB
        val grBelow = drc.calculateGainReductionDb(-20.0f)
        assertEquals(0.0f, grBelow, 0.0001f, "Below knee should have 0 dB gain reduction")

        // 2. Above knee: -4 dBFS -> Above upper knee (-9 dBFS)
        // With 4:1 ratio, compSlope = 1/4 - 1 = -0.75. For input -4 dBFS, distance from threshold (-12) is +8 dB.
        // Gain reduction = -0.75 * 8 = -6.0 dB.
        val grAbove = drc.calculateGainReductionDb(-4.0f)
        assertEquals(-6.0f, grAbove, 0.01f, "Above knee should follow 4:1 compression slope")

        // 3. Inside knee: at threshold (-12.0 dBFS)
        // Knee range is [-15, -9], delta from lower knee (-15) is 3.0 dB.
        // kneeFactor = -0.75 / 12 = -0.0625.
        // GR = -0.0625 * 3^2 = -0.5625 dB.
        val grAtThreshold = drc.calculateGainReductionDb(-12.0f)
        assertEquals(-0.5625f, grAtThreshold, 0.01f, "At threshold soft-knee must smoothly compress")
    }

    @Test
    fun testDynamicRangeCompressorPeakLimiterPreventsClipping() {
        val drc = DynamicRangeCompressor(
            sampleRate = sampleRate,
            thresholdDb = -12.0f,
            ratio = 4.0f,
            ceilingDb = -0.5f, // -0.5 dBFS ceiling (~30921 peak)
            lookaheadMs = 1.5f
        )

        // Near full-scale input with massive amplitude (32700)
        val massiveInput = generateSineWave(600.0, frameSize * 3, amplitude = 32700)

        val compressed = drc.process(massiveInput)
        assertEquals(massiveInput.size, compressed.size)

        var maxPeak = 0
        for (sample in compressed) {
            val s = abs(sample.toInt())
            if (s > maxPeak) maxPeak = s
        }

        // Must strictly not exceed ceilingShort (30921) and zero clipping at 32767
        assertTrue(
            maxPeak <= drc.ceilingShort,
            "Maximum peak ($maxPeak) must not exceed ceilingShort (${drc.ceilingShort})"
        )
        assertTrue(drc.peakOutputDb <= -0.5f, "Peak output (${drc.peakOutputDb} dBFS) must be <= -0.5 dBFS")
        assertTrue(drc.currentGainReductionDb < -5.0f, "Compressor must engage significant gain reduction")
    }

    @Test
    fun testAudioPipelineSessionWithAgcAndDrc() = runTest {
        val session = AudioPipelineSession(profile = AudioProfile.MEDIUM)

        // Speech input: 440 Hz, amplitude 15000 (loud speech)
        val speech = generateSineWave(440.0, session.frameSizeSamples, amplitude = 15000)

        // Process through outgoing pipeline: NoiseGate -> AGC -> DRC -> AudioCodec
        val packet = session.processOutgoingMicrophone(speech)
        assertNotNull(packet, "Active speech must produce compressed packet")
        assertTrue(packet.size in 100..200, "Packet size (${packet.size}) must comply with MTU")

        // Playout path
        val playout = session.pullPlayoutFrame(currentTimeMs = 1000L)
        assertEquals(session.frameSizeSamples, playout.size)
    }
}
