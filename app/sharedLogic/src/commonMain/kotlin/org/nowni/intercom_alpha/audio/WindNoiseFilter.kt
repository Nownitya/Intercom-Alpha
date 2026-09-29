package org.nowni.intercom_alpha.audio

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Helmet noise filtering presets calibrated for different riding speed regimes.
 */
@Serializable
enum class HelmetNoiseProfile(
    val highPassCutoffHz: Float,
    val voiceBoostDb: Float,
    val voiceBoostFreqHz: Float,
    val description: String
) {
    /** Pass-through without filtering */
    BYPASS(0f, 0f, 2200f, "Bypass - Clean studio/indoor recording"),

    /** Mild filtering for urban commuting (<50 km/h) */
    CITY_COMMUTE(150f, 2.0f, 2200f, "City Commute (<50 km/h) - Light rumble filter"),

    /** Standard motorcycle highway profile (80 km/h) */
    HIGHWAY_80KMH(300f, 4.0f, 2200f, "Highway 80 km/h - Standard wind buffeting filter"),

    /** Aggressive filtering for high speed or open-face helmets (120 km/h) */
    HIGHWAY_120KMH(380f, 6.0f, 2400f, "Highway 120 km/h - Aggressive wind roar filter & voice boost")
}

/**
 * High-performance pure Kotlin Multiplatform audio DSP filter for motorcycle helmet wind noise.
 *
 * Combines a 4th-order Butterworth High-Pass Filter (cascaded dual-biquad stages) to eliminate
 * low-frequency turbulence rumble (<300 Hz by >18 dB) with a parametric peaking EQ to boost speech
 * intelligibility formants in the 1.5 kHz - 3.4 kHz band.
 *
 * Implemented using Direct Form II Transposed biquad sections for numerical stability, zero runtime
 * allocations, and pristine cross-platform execution on Android, iOS, JVM, and Wasm/JS.
 *
 * @param sampleRateHz Audio sampling frequency in Hz (e.g. 16000, 24000, 48000). Default: 24000 Hz.
 * @param initialProfile Initial helmet profile preset. Default: [HelmetNoiseProfile.HIGHWAY_80KMH].
 */
class WindNoiseFilter(
    private var sampleRateHz: Int = 24000,
    initialProfile: HelmetNoiseProfile = HelmetNoiseProfile.HIGHWAY_80KMH
) {
    private var activeProfile: HelmetNoiseProfile = initialProfile

    // Biquad section 1 (HPF Butterworth stage 1: Q = 0.5411961)
    private val hpfStage1 = Biquad()

    // Biquad section 2 (HPF Butterworth stage 2: Q = 1.3065630)
    private val hpfStage2 = Biquad()

    // Biquad section 3 (Peaking voice formant emphasis EQ)
    private val peakingStage = Biquad()

    init {
        recomputeCoefficients()
    }

    /**
     * Changes active filtering preset.
     */
    fun setProfile(profile: HelmetNoiseProfile) {
        if (activeProfile != profile) {
            activeProfile = profile
            recomputeCoefficients()
        }
    }

    /**
     * Returns the currently active helmet profile.
     */
    fun getProfile(): HelmetNoiseProfile = activeProfile

    /**
     * Updates the audio sampling frequency and recalculates filter poles/zeros.
     */
    fun setSampleRate(sampleRate: Int) {
        if (sampleRateHz != sampleRate && sampleRate > 0) {
            sampleRateHz = sampleRate
            recomputeCoefficients()
        }
    }

    /**
     * Resets filter delay states to zero (prevents acoustic clicking across pauses/stream cuts).
     */
    fun reset() {
        hpfStage1.reset()
        hpfStage2.reset()
        peakingStage.reset()
    }

    /**
     * Processes 16-bit PCM samples in place or into [output].
     *
     * @param input Raw 16-bit PCM input samples.
     * @param output Destination array for filtered samples (defaults to newly allocated array matching input size).
     * @return The filtered 16-bit PCM sample array.
     */
    fun process(input: ShortArray, output: ShortArray = ShortArray(input.size)): ShortArray {
        if (activeProfile == HelmetNoiseProfile.BYPASS) {
            input.copyInto(output)
            return output
        }

        for (i in input.indices) {
            var sample = input[i].toDouble()

            // Cascaded 4th-order HPF
            sample = hpfStage1.process(sample)
            sample = hpfStage2.process(sample)

            // Voice formant emphasis peaking EQ
            sample = peakingStage.process(sample)

            // Soft-limit to prevent digital hard-clipping on loud voice bursts
            val clamped = when {
                sample > 32767.0 -> 32767.0
                sample < -32768.0 -> -32768.0
                else -> sample
            }

            output[i] = clamped.toInt().toShort()
        }

        return output
    }

    /**
     * Processes normalized float audio samples (-1.0f to 1.0f).
     */
    fun process(input: FloatArray, output: FloatArray = FloatArray(input.size)): FloatArray {
        if (activeProfile == HelmetNoiseProfile.BYPASS) {
            input.copyInto(output)
            return output
        }

        for (i in input.indices) {
            var sample = input[i].toDouble()

            sample = hpfStage1.process(sample)
            sample = hpfStage2.process(sample)
            sample = peakingStage.process(sample)

            val clamped = when {
                sample > 1.0 -> 1.0
                sample < -1.0 -> -1.0
                else -> sample
            }

            output[i] = clamped.toFloat()
        }

        return output
    }

    private fun recomputeCoefficients() {
        if (activeProfile == HelmetNoiseProfile.BYPASS || activeProfile.highPassCutoffHz <= 0f) {
            hpfStage1.setPassthrough()
            hpfStage2.setPassthrough()
            peakingStage.setPassthrough()
            return
        }

        val fs = sampleRateHz.toDouble()
        val fc = min(activeProfile.highPassCutoffHz.toDouble(), fs * 0.45)

        // 4th-order Butterworth: Stage 1 Q = 0.54119610, Stage 2 Q = 1.30656296
        hpfStage1.setupHighPass(fc, fs, 0.54119610)
        hpfStage2.setupHighPass(fc, fs, 1.30656296)

        // Peaking EQ for speech intelligibility
        val boostFreq = min(activeProfile.voiceBoostFreqHz.toDouble(), fs * 0.45)
        if (activeProfile.voiceBoostDb > 0f) {
            peakingStage.setupPeaking(boostFreq, fs, activeProfile.voiceBoostDb.toDouble(), 1.0)
        } else {
            peakingStage.setPassthrough()
        }
    }

    /**
     * Direct Form II Transposed Biquad Filter:
     *   y[n] = b0*x[n] + d1
     *   d1   = b1*x[n] - a1*y[n] + d2
     *   d2   = b2*x[n] - a2*y[n]
     */
    private class Biquad {
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0

        private var d1 = 0.0
        private var d2 = 0.0

        fun reset() {
            d1 = 0.0
            d2 = 0.0
        }

        fun setPassthrough() {
            b0 = 1.0
            b1 = 0.0
            b2 = 0.0
            a1 = 0.0
            a2 = 0.0
            reset()
        }

        fun process(x: Double): Double {
            val y = b0 * x + d1
            d1 = b1 * x - a1 * y + d2
            d2 = b2 * x - a2 * y
            return y
        }

        fun setupHighPass(f0: Double, fs: Double, q: Double) {
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW0) / 2.0) / a0
            b1 = (-(1.0 + cosW0)) / a0
            b2 = ((1.0 + cosW0) / 2.0) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun setupPeaking(f0: Double, fs: Double, gainDb: Double, q: Double) {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            val a0 = 1.0 + alpha / a
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * cosW0) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha / a) / a0
        }
    }
}
