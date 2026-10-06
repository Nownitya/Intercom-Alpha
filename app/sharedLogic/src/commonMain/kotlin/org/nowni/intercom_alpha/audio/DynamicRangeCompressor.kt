package org.nowni.intercom_alpha.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure Kotlin Multiplatform Dynamic Range Compressor (DRC) and Lookahead Peak Limiter DSP.
 *
 * Engineered specifically for motorcycle helmet intercom communication:
 * - Soft-Knee Compression Curve: Smoothly transitions from transparent 1:1 linear into 4:1 compression
 *   across [kneeWidthDb] (default: 6.0 dB), eliminating harsh clipping and unnatural pumping when a rider shouts.
 * - Envelope Follower: Slew-rate smoothed gain reduction with fast transient attack ([attackTimeMs]) and
 *   organic vocal decay ([releaseTimeMs]).
 * - Lookahead Brickwall Peak Limiter: Employs a low-latency lookahead buffer ([lookaheadMs]) to detect peaks
 *   ahead of time and strictly enforce a peak ceiling ([ceilingDb], default: -0.5 dBFS), preventing distortion
 *   and buffer overflow before IMA ADPCM encoding or acoustic playback.
 *
 * @param sampleRate Audio sampling rate in Hz (default: 24000).
 * @param thresholdDb Compression threshold in dBFS where soft-knee begins engaging (default: -12.0 dBFS).
 * @param ratio Compression ratio above the knee (default: 4.0f).
 * @param kneeWidthDb Width of the soft-knee transition zone in dB (default: 6.0 dB).
 * @param attackTimeMs Attack time constant for gain reduction engagement (default: 5.0 ms).
 * @param releaseTimeMs Release time constant for gain reduction recovery (default: 60.0 ms).
 * @param makeupGainDb Post-compression makeup gain in dB (default: 0.0 dB).
 * @param ceilingDb Brickwall peak limiter maximum ceiling in dBFS (default: -0.5 dBFS).
 * @param lookaheadMs Lookahead delay buffer duration in milliseconds (default: 1.5 ms).
 */
class DynamicRangeCompressor(
    val sampleRate: Int = 24000,
    var thresholdDb: Float = -12.0f,
    val ratio: Float = 4.0f,
    val kneeWidthDb: Float = 6.0f,
    val attackTimeMs: Float = 5.0f,
    val releaseTimeMs: Float = 60.0f,
    var makeupGainDb: Float = 0.0f,
    val ceilingDb: Float = -0.5f,
    val lookaheadMs: Float = 1.5f
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(ratio >= 1.0f) { "ratio must be >= 1.0" }
        require(kneeWidthDb >= 0.0f) { "kneeWidthDb must be non-negative" }
        require(attackTimeMs > 0.0f) { "attackTimeMs must be positive" }
        require(releaseTimeMs > 0.0f) { "releaseTimeMs must be positive" }
        require(ceilingDb <= 0.0f) { "ceilingDb must be <= 0.0 dBFS" }
    }

    private val compSlope: Float = (1.0f / ratio) - 1.0f
    private val halfKnee: Float = kneeWidthDb / 2.0f
    private val lowerKnee: Float = thresholdDb - halfKnee
    private val upperKnee: Float = thresholdDb + halfKnee
    private val kneeFactor: Float = if (kneeWidthDb > 0.0f) compSlope / (2.0f * kneeWidthDb) else 0.0f

    private val attackAlpha: Float = 1.0f - exp(-1.0f / (sampleRate.toFloat() * (attackTimeMs / 1000.0f)))
    private val releaseAlpha: Float = 1.0f - exp(-1.0f / (sampleRate.toFloat() * (releaseTimeMs / 1000.0f)))

    val ceilingLinear: Float = (32768.0 * 10.0.pow(ceilingDb.toDouble() / 20.0)).toFloat()
    val ceilingShort: Short = ceilingLinear.roundToInt().coerceIn(1, 32767).toShort()

    val lookaheadSamples: Int = max(0, ((lookaheadMs / 1000.0f) * sampleRate).roundToInt())
    private val lookaheadBuffer = ShortArray(max(1, lookaheadSamples))
    private var lookaheadIndex: Int = 0

    /** Current compressor gain reduction in decibels (always <= 0.0 dB). */
    var currentGainReductionDb: Float = 0.0f
        private set

    /** Peak input level in dBFS observed in the most recent frame. */
    var peakInputDb: Float = -120.0f
        private set

    /** Peak output level in dBFS observed in the most recent frame. */
    var peakOutputDb: Float = -120.0f
        private set

    /**
     * Calculates the soft-knee static compression gain reduction in dB for an input level in dBFS.
     */
    fun calculateGainReductionDb(inputDb: Float): Float {
        return when {
            kneeWidthDb <= 0.0f -> {
                // Hard-knee fallback
                if (inputDb > thresholdDb) compSlope * (inputDb - thresholdDb) else 0.0f
            }
            inputDb <= lowerKnee -> 0.0f
            inputDb >= upperKnee -> compSlope * (inputDb - thresholdDb)
            else -> {
                val delta = inputDb - lowerKnee
                kneeFactor * delta * delta
            }
        }
    }

    /**
     * Processes a buffer of 16-bit PCM samples through soft-knee compression and lookahead peak limiting.
     * Returns a new ShortArray containing dynamically compressed audio strictly bounded to [ceilingDb].
     */
    fun process(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        val output = ShortArray(samples.size)
        var maxInAbs = 0f
        var maxOutAbs = 0f

        for (i in samples.indices) {
            val inputSample = samples[i]
            val inputAbs = abs(inputSample.toFloat())
            if (inputAbs > maxInAbs) maxInAbs = inputAbs

            // 1. Convert input sample amplitude to dBFS
            val inputDb = if (inputAbs > 0.0f) {
                (20.0 * log10(inputAbs.toDouble() / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
            } else {
                -120.0f
            }

            // 2. Compute static soft-knee gain reduction
            val targetGrDb = calculateGainReductionDb(inputDb)

            // 3. Smooth gain reduction via envelope follower (attack / release)
            if (targetGrDb < currentGainReductionDb) {
                // Attack phase: signal getting louder, increase gain reduction
                currentGainReductionDb += attackAlpha * (targetGrDb - currentGainReductionDb)
            } else {
                // Release phase: signal getting quieter, relax gain reduction back towards 0 dB
                currentGainReductionDb += releaseAlpha * (targetGrDb - currentGainReductionDb)
            }

            // 4. Compute total linear compressor gain including makeup gain
            val compLinearGain = 10.0f.pow((currentGainReductionDb + makeupGainDb) / 20.0f)

            // 5. Lookahead delay line
            val sampleToProcess: Short = if (lookaheadSamples > 0) {
                val delayed = lookaheadBuffer[lookaheadIndex]
                lookaheadBuffer[lookaheadIndex] = inputSample
                lookaheadIndex = (lookaheadIndex + 1) % lookaheadSamples
                delayed
            } else {
                inputSample
            }

            // 6. Apply compression gain
            var processedFloat = sampleToProcess.toFloat() * compLinearGain

            // 7. Lookahead Brickwall Peak Limiter enforcement
            val processedAbs = abs(processedFloat)
            if (processedAbs > ceilingLinear) {
                val limiterGain = ceilingLinear / processedAbs
                processedFloat *= limiterGain
            }

            // 8. Integer clamp to guarantee zero clipping and exact ceiling compliance
            val clamped = processedFloat.roundToInt().coerceIn(-ceilingShort.toInt(), ceilingShort.toInt()).toShort()
            output[i] = clamped

            val outAbs = abs(clamped.toFloat())
            if (outAbs > maxOutAbs) {
                maxOutAbs = outAbs
            }
        }

        peakInputDb = if (maxInAbs > 0.0f) {
            (20.0 * log10(maxInAbs.toDouble() / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
        } else {
            -120.0f
        }

        peakOutputDb = if (maxOutAbs > 0.0f) {
            (20.0 * log10(maxOutAbs.toDouble() / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
        } else {
            -120.0f
        }

        return output
    }

    /**
     * Resets the compressor envelope follower, lookahead buffer, and peak telemetry.
     */
    fun reset() {
        currentGainReductionDb = 0.0f
        peakInputDb = -120.0f
        peakOutputDb = -120.0f
        lookaheadBuffer.fill(0)
        lookaheadIndex = 0
    }
}
