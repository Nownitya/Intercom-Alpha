package org.nowni.intercom_alpha.audio

import org.nowni.intercom_alpha.mesh.ProximityZone
import org.nowni.intercom_alpha.mesh.RadarTarget
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialAudioProcessorTest {

    private fun generateMonoSine(numSamples: Int, amplitude: Short = 20000): ByteArray {
        val pcm = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val offset = i * 2
            pcm[offset] = (amplitude.toInt() and 0xFF).toByte()
            pcm[offset + 1] = ((amplitude.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun readStereoSample(stereo: ByteArray, sampleIndex: Int): Pair<Short, Short> {
        val offset = sampleIndex * 4
        val lLow = stereo[offset].toInt() and 0xFF
        val lHigh = stereo[offset + 1].toInt()
        val left = ((lHigh shl 8) or lLow).toShort()

        val rLow = stereo[offset + 2].toInt() and 0xFF
        val rHigh = stereo[offset + 3].toInt()
        val right = ((rHigh shl 8) or rLow).toShort()

        return Pair(left, right)
    }

    @Test
    fun testBufferSizing() {
        val processor = SpatialAudioProcessor()
        val monoPcm = ByteArray(200) // 100 samples
        val stereo = processor.spatialize(monoPcm, azimuthDegrees = 0.0, distanceMeters = 5.0)

        // 100 stereo samples * 4 bytes/sample = 400 bytes
        assertEquals(400, stereo.size)
    }

    @Test
    fun testCenterPanningEqualGains() {
        val processor = SpatialAudioProcessor(enableItd = false)
        val mono = generateMonoSine(100, amplitude = 20000)
        val stereo = processor.spatialize(mono, azimuthDegrees = 0.0, distanceMeters = 5.0)

        val (left, right) = readStereoSample(stereo, 50)
        // At center, Left and Right should be identical within 1 LSB
        assertTrue(abs(left - right) <= 1)
        assertTrue(left > 10000)
    }

    @Test
    fun testHardLeftPanning() {
        val processor = SpatialAudioProcessor(enableItd = false)
        val mono = generateMonoSine(100, amplitude = 20000)
        val stereo = processor.spatialize(mono, azimuthDegrees = -90.0, distanceMeters = 5.0)

        val (left, right) = readStereoSample(stereo, 50)
        // Hard left: left channel retains amplitude, right channel is zero
        assertTrue(left > 18000)
        assertEquals(0.toShort(), right)
    }

    @Test
    fun testHardRightPanning() {
        val processor = SpatialAudioProcessor(enableItd = false)
        val mono = generateMonoSine(100, amplitude = 20000)
        val stereo = processor.spatialize(mono, azimuthDegrees = 90.0, distanceMeters = 5.0)

        val (left, right) = readStereoSample(stereo, 50)
        // Hard right: right channel retains amplitude, left channel is zero
        assertEquals(0.toShort(), left)
        assertTrue(right > 18000)
    }

    @Test
    fun testDistanceAttenuation() {
        val processor = SpatialAudioProcessor(enableItd = false)
        val mono = generateMonoSine(100, amplitude = 20000)

        val stereoClose = processor.spatialize(mono, azimuthDegrees = 0.0, distanceMeters = 5.0)
        val stereoFar = processor.spatialize(mono, azimuthDegrees = 0.0, distanceMeters = 50.0)

        val (leftClose, _) = readStereoSample(stereoClose, 50)
        val (leftFar, _) = readStereoSample(stereoFar, 50)

        // Close distance should produce higher amplitude than far distance
        assertTrue(leftClose > leftFar)
        // Far amplitude must remain above min gain floor (0.35 * 0.7071 * 20000 ~ 4950)
        assertTrue(leftFar > 4000)
    }

    @Test
    fun testTargetSpatialization() {
        val processor = SpatialAudioProcessor(enableItd = true)
        val mono = generateMonoSine(100, amplitude = 20000)

        val target = RadarTarget(
            peerId = "lead-rider",
            estimatedDistanceMeters = 12.0,
            rssi = -68,
            smoothedRssi = -68.0,
            linkMarginDb = 25.0,
            zone = ProximityZone.NEAR,
            hopCount = 1
        )

        val stereo = processor.spatializeForTarget(mono, target, syntheticAzimuth = 30.0)
        assertEquals(400, stereo.size)

        val (left, right) = readStereoSample(stereo, 50)
        // Panned +30 degrees (right side): right channel should be louder than left
        assertTrue(right > left)
    }
}
