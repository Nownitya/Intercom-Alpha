package org.nowni.intercom_alpha.audio

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin Multiplatform Automatic Gain Control (AGC) DSP.
 *
 * Tailored for motorcycle helmet intercom acoustics where speech amplitudes
 * vary dramatically (whispering at a stop light vs shouting over wind buffeting).
 *
 * Core Features:
 * - Dynamic RMS Speech Normalization: Drives speech energy toward [targetLevelDb] (default: -18.0 dBFS).
 * - Anti-Pumping Envelope Follower: Below [noiseFloorDb] (default: -50.0 dBFS), AGC suspends gain boosts
 *   and holds or gently decays toward unity gain, preventing the amplification of background engine rumble
 *   and wind noise during conversational pauses.
 * - Slew-Rate Limiting & Sample Interpolation: Gain transitions smoothly across samples and frames without
 *   clicks, zipper noise, or sudden acoustic jumps.
 * - Asymmetric Attack & Release: Rapid reaction ([attackTimeMs]) to catch quiet syllables, with gentle
 *   decay ([releaseTimeMs]) to avoid modulating vocal inflections.
 *
 * @param sampleRate Audio sampling rate in Hz (default: 24000).
 * @param targetLevelDb Desired speech output RMS level in dBFS (default: -18.0 dBFS).
 * @param maxGainDb Maximum upward gain boost in dB (default: +18.0 dB).
 * @param minGainDb Maximum downward attenuation in dB (default: -12.0 dB).
 * @param noiseFloorDb Anti-pumping threshold in dBFS (default: -50.0 dBFS). Signals below this are treated as silence/noise.
 * @param attackTimeMs Time in milliseconds for gain to adjust upward to target level (default: 80.0 ms).
 * @param releaseTimeMs Time in milliseconds for gain to adjust downward (default: 300.0 ms).
 */
class AutomaticGainControl(
    val sampleRate: Int = 24000,
    var targetLevelDb: Float = -18.0f,
    val maxGainDb: Float = 18.0f,
    val minGainDb: Float = -12.0f,
    var noiseFloorDb: Float = -50.0f,
    val attackTimeMs: Float = 80.0f,
    val releaseTimeMs: Float = 300.0f
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(maxGainDb >= 0.0f) { "maxGainDb must be non-negative" }
        require(minGainDb <= 0.0f) { "minGainDb must be non-positive" }
        require(attackTimeMs > 0.0f) { "attackTimeMs must be positive" }
        require(releaseTimeMs > 0.0f) { "releaseTimeMs must be positive" }
    }

    /** Current applied gain in decibels. Starts at unity gain (0.0 dB). */
    var currentGainDb: Float = 0.0f
        private set

    /** Input RMS level in dBFS of the most recently processed frame. */
    var currentInputRmsDb: Float = -120.0f
        private set

    /** Output RMS level in dBFS of the most recently processed frame. */
    var currentOutputRmsDb: Float = -120.0f
        private set

    /** True when AGC is in anti-pumping hold mode (signal below [noiseFloorDb]). */
    var isHolding: Boolean = false
        private set

    /**
     * Processes a buffer of 16-bit PCM audio samples, applying automatic gain leveling.
     * Returns a new ShortArray containing normalized audio.
     */
    fun process(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        val inputRms = calculateRms(samples)
        currentInputRmsDb = toDbFs(inputRms)

        val frameDurationMs = (samples.size.toFloat() / sampleRate.toFloat()) * 1000.0f

        val targetGainDb: Float
        if (currentInputRmsDb < noiseFloorDb) {
            // Signal below noise floor: anti-pumping protection active.
            // Do NOT amplify background noise or breathing pauses.
            isHolding = true
            // If gain was previously boosted above unity (0 dB), gently decay towards unity.
            // If gain was attenuating (< 0 dB), hold current gain to avoid sudden surges.
            targetGainDb = if (currentGainDb > 0.0f) {
                max(0.0f, currentGainDb - (frameDurationMs / releaseTimeMs) * 3.0f)
            } else {
                currentGainDb
            }
        } else {
            // Active speech detected. Drive gain to reach targetLevelDb.
            isHolding = false
            val errorDb = targetLevelDb - currentInputRmsDb
            targetGainDb = errorDb.coerceIn(minGainDb, maxGainDb)
        }

        // Exponential smoothing towards target gain
        val timeConstantMs = if (targetGainDb > currentGainDb) attackTimeMs else releaseTimeMs
        val alpha = 1.0f - exp(-frameDurationMs / max(1.0f, timeConstantMs))
        val nextGainDb = currentGainDb + alpha * (targetGainDb - currentGainDb)

        val startLinearGain = 10.0f.pow(currentGainDb / 20.0f)
        val endLinearGain = 10.0f.pow(nextGainDb / 20.0f)

        val output = ShortArray(samples.size)
        val n = samples.size
        for (i in 0 until n) {
            val factor = if (n > 1) i.toFloat() / (n - 1).toFloat() else 1.0f
            val interpolatedGain = startLinearGain + factor * (endLinearGain - startLinearGain)
            val leveled = (samples[i].toFloat() * interpolatedGain).roundToInt().coerceIn(-32768, 32767).toShort()
            output[i] = leveled
        }

        currentGainDb = nextGainDb
        val outputRms = calculateRms(output)
        currentOutputRmsDb = toDbFs(outputRms)

        return output
    }

    /**
     * Resets the AGC state back to unity gain (0 dB) and cleared levels.
     */
    fun reset() {
        currentGainDb = 0.0f
        currentInputRmsDb = -120.0f
        currentOutputRmsDb = -120.0f
        isHolding = false
    }

    private fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sumSquares = 0.0
        for (sample in samples) {
            val s = sample.toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / samples.size.toDouble())
    }

    private fun toDbFs(rms: Double): Float {
        return if (rms > 0.0) {
            (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
        } else {
            -120.0f
        }
    }
}
