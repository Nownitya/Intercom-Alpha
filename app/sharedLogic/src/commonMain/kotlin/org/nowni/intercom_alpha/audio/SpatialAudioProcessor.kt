package org.nowni.intercom_alpha.audio

import org.nowni.intercom_alpha.mesh.RadarTarget
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3D Binaural Stereo Audio Spatializer for Helmet Headsets.
 *
 * Transforms mono 16-bit 48kHz voice streams into spatialized stereo PCM with:
 * 1. Constant-power Interaural Level Difference (ILD) panning based on convoy bearing (-90° left to +90° right).
 * 2. Psychoacoustic Interaural Time Difference (ITD) delay emulation (up to 31 samples / ~0.65ms at 48kHz).
 * 3. Logarithmic distance gain attenuation preserving intelligibility while conveying convoy proximity.
 */
class SpatialAudioProcessor(
    val enableItd: Boolean = true,
    val referenceDistanceMeters: Double = 5.0,
    val minDistanceGain: Double = 0.35
) {
    companion object {
        const val SAMPLE_RATE = 48000
        const val MAX_ITD_SAMPLES = 32 // ~0.66ms head shadow delay
    }

    // Circular delay buffers for contralateral ITD
    private val leftDelayBuffer = ShortArray(MAX_ITD_SAMPLES)
    private val rightDelayBuffer = ShortArray(MAX_ITD_SAMPLES)
    private var leftDelayIndex = 0
    private var rightDelayIndex = 0

    /**
     * Resets internal ITD delay buffers.
     */
    fun resetDelayBuffers() {
        leftDelayBuffer.fill(0)
        rightDelayBuffer.fill(0)
        leftDelayIndex = 0
        rightDelayIndex = 0
    }

    /**
     * Converts a mono 16-bit PCM little-endian buffer into an interleaved 16-bit stereo buffer
     * panned to [azimuthDegrees] and attenuated by [distanceMeters].
     *
     * @param monoPcm Raw 16-bit little-endian mono audio samples.
     * @param azimuthDegrees Directional bearing: -90.0° (full left), 0.0° (center), +90.0° (full right).
     * @param distanceMeters Estimated distance to rider.
     * @return Interleaved 16-bit stereo PCM buffer [L, R, L, R, ...].
     */
    fun spatialize(
        monoPcm: ByteArray,
        azimuthDegrees: Double,
        distanceMeters: Double = 10.0
    ): ByteArray {
        val numSamples = monoPcm.size / 2
        val stereoBytes = ByteArray(numSamples * 4) // 2 channels * 2 bytes/sample

        // Clamp azimuth to -90..+90
        val clampedAzimuth = azimuthDegrees.coerceIn(-90.0, 90.0)

        // 1. Equal-power panning law:
        // p in [0.0 (left), 0.5 (center), 1.0 (right)]
        val panNorm = (clampedAzimuth + 90.0) / 180.0
        val panAngle = panNorm * (PI / 2.0)
        val gainL = cos(panAngle)
        val gainR = sin(panAngle)

        // 2. Distance attenuation curve:
        val d = max(1.0, distanceMeters)
        val distGain = if (d <= referenceDistanceMeters) {
            1.0
        } else {
            val atten = referenceDistanceMeters / (referenceDistanceMeters + 0.04 * (d - referenceDistanceMeters))
            max(minDistanceGain, atten)
        }

        val finalGainL = gainL * distGain
        val finalGainR = gainR * distGain

        // 3. ITD sample delay:
        // Sound to the right delays reaching the left ear, and vice versa.
        val itdFraction = abs(clampedAzimuth) / 90.0
        val delaySamples = if (enableItd) (itdFraction * (MAX_ITD_SAMPLES - 1)).roundToInt() else 0
        val delayLeftEar = clampedAzimuth > 0.0 // Sound on right -> delay left ear
        val delayRightEar = clampedAzimuth < 0.0 // Sound on left -> delay right ear

        for (i in 0 until numSamples) {
            val byteOffset = i * 2
            val low = monoPcm[byteOffset].toInt() and 0xFF
            val high = monoPcm[byteOffset + 1].toInt()
            val monoSample = ((high shl 8) or low).toShort()

            val leftSample: Short
            val rightSample: Short

            if (enableItd && delaySamples > 0) {
                if (delayLeftEar) {
                    // Left ear delayed
                    leftDelayBuffer[leftDelayIndex] = monoSample
                    val readIdx = (leftDelayIndex - delaySamples + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES
                    val delayedMono = leftDelayBuffer[readIdx]
                    leftDelayIndex = (leftDelayIndex + 1) % MAX_ITD_SAMPLES

                    leftSample = (delayedMono * finalGainL).roundToInt().coerceIn(-32768, 32767).toShort()
                    rightSample = (monoSample * finalGainR).roundToInt().coerceIn(-32768, 32767).toShort()
                } else if (delayRightEar) {
                    // Right ear delayed
                    rightDelayBuffer[rightDelayIndex] = monoSample
                    val readIdx = (rightDelayIndex - delaySamples + MAX_ITD_SAMPLES) % MAX_ITD_SAMPLES
                    val delayedMono = rightDelayBuffer[readIdx]
                    rightDelayIndex = (rightDelayIndex + 1) % MAX_ITD_SAMPLES

                    leftSample = (monoSample * finalGainL).roundToInt().coerceIn(-32768, 32767).toShort()
                    rightSample = (delayedMono * finalGainR).roundToInt().coerceIn(-32768, 32767).toShort()
                } else {
                    leftSample = (monoSample * finalGainL).roundToInt().coerceIn(-32768, 32767).toShort()
                    rightSample = (monoSample * finalGainR).roundToInt().coerceIn(-32768, 32767).toShort()
                }
            } else {
                leftSample = (monoSample * finalGainL).roundToInt().coerceIn(-32768, 32767).toShort()
                rightSample = (monoSample * finalGainR).roundToInt().coerceIn(-32768, 32767).toShort()
            }

            val outOffset = i * 4
            // Left sample
            stereoBytes[outOffset] = (leftSample.toInt() and 0xFF).toByte()
            stereoBytes[outOffset + 1] = ((leftSample.toInt() shr 8) and 0xFF).toByte()
            // Right sample
            stereoBytes[outOffset + 2] = (rightSample.toInt() and 0xFF).toByte()
            stereoBytes[outOffset + 3] = ((rightSample.toInt() shr 8) and 0xFF).toByte()
        }

        return stereoBytes
    }

    /**
     * Helper to spatialize incoming audio directly from a [RadarTarget] telemetry record.
     */
    fun spatializeForTarget(
        monoPcm: ByteArray,
        target: RadarTarget,
        syntheticAzimuth: Double = 0.0
    ): ByteArray {
        return spatialize(
            monoPcm = monoPcm,
            azimuthDegrees = syntheticAzimuth,
            distanceMeters = target.estimatedDistanceMeters
        )
    }
}
