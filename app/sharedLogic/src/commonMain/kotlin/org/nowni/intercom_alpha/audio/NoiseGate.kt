package org.nowni.intercom_alpha.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin Multiplatform Adaptive Noise Gate & VOX Energy DSP.
 *
 * Specifically engineered for motorcycle intercom and outdoor environments:
 * - Hysteresis thresholding to prevent fluttering near the activation boundary:
 *     - [openThresholdDb]: Gate opens when energy exceeds this level (e.g. -35 dBFS).
 *     - [closeThresholdDb]: Gate only closes when energy falls below this level (e.g. -42 dBFS).
 * - Attack, Hold, and Release envelope phases:
 *     - Attack: Rapid opening (e.g. 5ms) to catch soft plosives without clipping consonants.
 *     - Hold: Preserves gate openness during short speech pauses (e.g. 100ms) between syllables.
 *     - Release: Smooth gain ramp-down (e.g. 50ms) to floor attenuation.
 * - Sample-by-sample gain interpolation to prevent zipper noise and clicks.
 */
class NoiseGate(
    val sampleRate: Int = 24000,
    var openThresholdDb: Float = -35.0f,
    var closeThresholdDb: Float = -42.0f,
    attackTimeMs: Float = 5.0f,
    holdTimeMs: Float = 100.0f,
    releaseTimeMs: Float = 50.0f,
    val floorGainDb: Float = -60.0f
) {
    init {
        require(openThresholdDb >= closeThresholdDb) {
            "openThresholdDb ($openThresholdDb) must be >= closeThresholdDb ($closeThresholdDb)"
        }
    }

    private val floorLinearGain: Float = if (floorGainDb <= -90.0f) 0.0f else 10.0f.pow(floorGainDb / 20.0f)

    private val attackSamples: Int = max(1, ((attackTimeMs / 1000.0f) * sampleRate).roundToInt())
    private val holdSamples: Int = max(0, ((holdTimeMs / 1000.0f) * sampleRate).roundToInt())
    private val releaseSamples: Int = max(1, ((releaseTimeMs / 1000.0f) * sampleRate).roundToInt())

    private val attackStep: Float = (1.0f - floorLinearGain) / attackSamples.toFloat()
    private val releaseStep: Float = (1.0f - floorLinearGain) / releaseSamples.toFloat()

    var currentGain: Float = floorLinearGain
        private set

    var currentEnvelopeDb: Float = -120.0f
        private set

    private var remainingHoldSamples: Int = 0
    private var gateActive: Boolean = false

    val isOpen: Boolean
        get() = currentGain > (floorLinearGain + 0.01f)

    /**
     * Processes a buffer of 16-bit PCM audio samples, applying adaptive noise gating.
     * Returns a new ShortArray containing the gated audio.
     */
    fun process(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return ShortArray(0)

        val frameRms = calculateRms(samples)
        currentEnvelopeDb = if (frameRms > 0.0) {
            (20.0 * log10(frameRms / 32768.0)).toFloat().coerceIn(-120.0f, 0.0f)
        } else {
            -120.0f
        }

        // Hysteresis decision logic
        if (currentEnvelopeDb >= openThresholdDb) {
            gateActive = true
            remainingHoldSamples = holdSamples
        } else if (currentEnvelopeDb >= closeThresholdDb) {
            if (gateActive) {
                remainingHoldSamples = holdSamples
            }
        } else {
            // Signal below close threshold
            if (remainingHoldSamples > 0) {
                // Keep gateActive true while hold timer runs
                gateActive = true
            } else {
                gateActive = false
            }
        }

        val output = ShortArray(samples.size)

        for (i in samples.indices) {
            // Check hold expiration sample by sample
            if (!gateActive || (currentEnvelopeDb < closeThresholdDb && remainingHoldSamples > 0)) {
                if (remainingHoldSamples > 0) {
                    remainingHoldSamples--
                    if (remainingHoldSamples == 0 && currentEnvelopeDb < closeThresholdDb) {
                        gateActive = false
                    }
                }
            }

            val targetGain = if (gateActive) 1.0f else floorLinearGain

            // Smooth sample-by-sample gain slew
            if (currentGain < targetGain) {
                currentGain = min(targetGain, currentGain + attackStep)
            } else if (currentGain > targetGain) {
                currentGain = max(targetGain, currentGain - releaseStep)
            }

            val sample = samples[i]
            val gatedSample = (sample * currentGain).roundToInt().coerceIn(-32768, 32767).toShort()
            output[i] = gatedSample
        }

        return output
    }

    /**
     * Resets the noise gate state to initial closed conditions.
     */
    fun reset() {
        currentGain = floorLinearGain
        currentEnvelopeDb = -120.0f
        remainingHoldSamples = 0
        gateActive = false
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
}
