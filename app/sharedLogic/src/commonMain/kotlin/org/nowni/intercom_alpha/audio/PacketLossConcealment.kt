package org.nowni.intercom_alpha.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure Kotlin Multiplatform Packet Loss Concealment (PLC) & Frame Interpolator.
 *
 * Implements pitch-synchronous waveform extrapolation and exponential decay:
 * 1. Pitch Detection: Autocorrelation over the recent PCM history to locate fundamental pitch period.
 * 2. Waveform Extrapolation: Periodic repetition of pitch-period waveform during missing frames.
 * 3. Exponential Attenuation: -3 dB (~0.707 linear gain) attenuation per consecutive lost frame,
 *    decaying to complete silence after [maxConsecutiveLosses].
 * 4. Crossfade Smoothing: Overlap-add cosine/linear crossfade when normal packet transmission resumes
 *    to prevent acoustic phase transients and clicks.
 */
class PacketLossConcealment(
    val sampleRate: Int = 24000,
    val maxConsecutiveLosses: Int = 5,
    val lossAttenuationFactor: Float = 0.70794578f, // -3 dB per frame
    val crossfadeMs: Float = 4.0f
) {
    private val crossfadeSamples: Int = max(1, ((crossfadeMs / 1000f) * sampleRate).roundToInt())
    private val minPitchLag: Int = max(8, sampleRate / 500)   // 500 Hz upper pitch limit
    private val maxPitchLag: Int = max(minPitchLag + 10, sampleRate / 50) // 50 Hz lower pitch limit

    // Ring buffer sized to hold pitch search history plus headroom
    private val historyCapacity: Int = max(2048, maxPitchLag * 4)
    private val historyBuffer = ShortArray(historyCapacity)
    private var historyCount: Int = 0
    private var lastDetectedPitch: Int = (minPitchLag + maxPitchLag) / 2

    var consecutiveLossCount: Int = 0
        private set

    /**
     * Ingests a valid incoming PCM frame.
     * If the pipeline was previously in concealment mode, performs smooth crossfade blending
     * over the start of the frame to eliminate discontinuity clicks.
     */
    fun pushGoodFrame(samples: ShortArray): ShortArray {
        if (samples.isEmpty()) return samples

        val output = if (consecutiveLossCount > 0 && historyCount >= minPitchLag) {
            // Synthesize crossfade continuation
            val blendLength = min(crossfadeSamples, samples.size)
            val continuation = synthesizeWaveform(blendLength, consecutiveLossCount)
            val blended = samples.copyOf()
            for (i in 0 until blendLength) {
                val t = i.toFloat() / blendLength.toFloat()
                val extrapolatedSample = continuation[i]
                val realSample = samples[i]
                val mixed = (1.0f - t) * extrapolatedSample + t * realSample
                blended[i] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
            }
            blended
        } else {
            samples.copyOf()
        }

        consecutiveLossCount = 0
        appendHistory(output)
        updatePitchEstimate()
        return output
    }

    /**
     * Generates a concealment frame to replace a dropped or delayed packet.
     * Extrapolates previous pitch waveform with cumulative exponential attenuation.
     */
    fun conceal(frameSize: Int): ShortArray {
        if (frameSize <= 0) return ShortArray(0)

        // If no prior audio history exists or max consecutive losses exceeded, output silence
        if (historyCount < minPitchLag || consecutiveLossCount >= maxConsecutiveLosses) {
            consecutiveLossCount++
            val silence = ShortArray(frameSize)
            appendHistory(silence)
            return silence
        }

        val output = synthesizeWaveform(frameSize, consecutiveLossCount)
        consecutiveLossCount++
        appendHistory(output)
        return output
    }

    /**
     * Resets PLC history buffer and loss counter.
     */
    fun reset() {
        historyCount = 0
        consecutiveLossCount = 0
        historyBuffer.fill(0)
        lastDetectedPitch = (minPitchLag + maxPitchLag) / 2
    }

    private fun synthesizeWaveform(length: Int, lossIndex: Int): ShortArray {
        val output = ShortArray(length)
        val pitch = lastDetectedPitch.coerceIn(minPitchLag, min(maxPitchLag, historyCount))
        if (pitch <= 0) return output

        // Exponential decay calculation
        var startGain = 1.0f
        for (i in 0 until lossIndex) {
            startGain *= lossAttenuationFactor
        }
        val endGain = startGain * lossAttenuationFactor

        for (i in 0 until length) {
            val progress = i.toFloat() / max(1, length).toFloat()
            val currentGain = startGain + (endGain - startGain) * progress

            // Sample from history pitch loop
            val historyOffset = historyCount - pitch + (i % pitch)
            val sourceSample = if (historyOffset in 0 until historyCount) {
                historyBuffer[historyOffset]
            } else {
                0
            }
            val attenuated = (sourceSample * currentGain).roundToInt().coerceIn(-32768, 32767).toShort()
            output[i] = attenuated
        }
        return output
    }

    private fun appendHistory(samples: ShortArray) {
        if (samples.isEmpty()) return

        if (samples.size >= historyCapacity) {
            val offset = samples.size - historyCapacity
            samples.copyInto(historyBuffer, 0, offset, samples.size)
            historyCount = historyCapacity
            return
        }

        if (historyCount + samples.size > historyCapacity) {
            val shift = (historyCount + samples.size) - historyCapacity
            historyBuffer.copyInto(historyBuffer, 0, shift, historyCount)
            historyCount -= shift
        }

        samples.copyInto(historyBuffer, historyCount, 0, samples.size)
        historyCount += samples.size
    }

    private fun updatePitchEstimate() {
        if (historyCount < maxPitchLag + minPitchLag) return

        val searchWindow = min(historyCount - maxPitchLag, 480) // 20ms analysis window
        if (searchWindow <= 0) return

        val targetOffset = historyCount - searchWindow
        var bestCorrelation = Long.MIN_VALUE
        var bestLag = lastDetectedPitch

        // Autocorrelation search across valid pitch range
        for (lag in minPitchLag..maxPitchLag) {
            if (targetOffset - lag < 0) break

            var correlation = 0L
            var energy = 0L

            for (n in 0 until searchWindow) {
                val s1 = historyBuffer[targetOffset + n].toLong()
                val s2 = historyBuffer[targetOffset + n - lag].toLong()
                correlation += s1 * s2
                energy += s2 * s2
            }

            if (energy > 0) {
                val normalizedCorrelation = correlation / max(1L, sqrt(energy.toDouble()).toLong())
                if (normalizedCorrelation > bestCorrelation) {
                    bestCorrelation = normalizedCorrelation
                    bestLag = lag
                }
            }
        }

        if (bestLag in minPitchLag..maxPitchLag) {
            lastDetectedPitch = bestLag
        }
    }
}
