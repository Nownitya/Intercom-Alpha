package org.nowni.intercom_alpha.audio

import org.nowni.intercom_alpha.common.AudioProfile
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioCodecTest {

    private fun generateSineWave(freq: Double, sampleRate: Int, samples: Int, amplitude: Short = 16000): ShortArray {
        val result = ShortArray(samples)
        for (i in 0 until samples) {
            val angle = 2.0 * PI * freq * i / sampleRate
            result[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return result
    }

    private fun calculateSnr(original: ShortArray, decoded: ShortArray): Double {
        var signalPower = 0.0
        var noisePower = 0.0
        val n = minOf(original.size, decoded.size)
        for (i in 0 until n) {
            val s = original[i].toDouble()
            val diff = s - decoded[i].toDouble()
            signalPower += s * s
            noisePower += diff * diff
        }
        if (noisePower == 0.0) return 100.0
        return 10.0 * log10(signalPower / noisePower)
    }

    @Test
    fun testEncodeDecodeRoundtripMedium() {
        val codec = AudioCodec()
        val original = generateSineWave(1000.0, 24000, 480) // 20ms at 24kHz

        val compressed = codec.encode(original, AudioProfile.MEDIUM)
        assertTrue(compressed.size in 100..150, "Medium compressed frame size (${compressed.size}) must fit BLE MTU")

        val decoded = codec.decode(compressed)
        assertEquals(original.size, decoded.size, "Decoded sample count must match original")

        val snr = calculateSnr(original, decoded)
        assertTrue(snr > 12.0, "SNR ($snr dB) must exceed voice intelligibility threshold")
    }

    @Test
    fun testEncodeDecodeHighProfile() {
        val codec = AudioCodec()
        val original = generateSineWave(800.0, 24000, 480)

        val compressed = codec.encode(original, AudioProfile.HIGH)
        assertTrue(compressed.size in 200..260, "High compressed frame size (${compressed.size}) must fit standard MTU")

        val decoded = codec.decode(compressed)
        assertEquals(original.size, decoded.size)

        val snr = calculateSnr(original, decoded)
        assertTrue(snr > 20.0, "Full-rate ADPCM SNR ($snr dB) must be high quality")
    }

    @Test
    fun testEncodeDecodeUltraLowProfile() {
        val codec = AudioCodec()
        val original = generateSineWave(400.0, 16000, 960) // 60ms at 16kHz

        val compressed = codec.encode(original, AudioProfile.ULTRA_LOW)
        assertTrue(compressed.size in 100..140, "Ultra low frame size (${compressed.size}) must be ultra compact")

        val decoded = codec.decode(compressed)
        assertEquals(original.size, decoded.size)

        val snr = calculateSnr(original, decoded)
        assertTrue(snr > 8.0, "Ultra low SNR ($snr dB) must be acceptable for low-bandwidth mesh")
    }

    @Test
    fun testEmptyInputHandledCleanly() {
        val codec = AudioCodec()
        val emptyInput = ShortArray(0)

        val compressed = codec.encode(emptyInput)
        assertEquals(0, compressed.size)

        val decoded = codec.decode(compressed)
        assertEquals(0, decoded.size)
    }

    @Test
    fun testCorruptedPacketHandledGracefully() {
        val codec = AudioCodec()

        // Truncated packet below header size
        val truncated = ByteArray(4) { 0x01 }
        val decodedTruncated = codec.decode(truncated)
        assertEquals(0, decodedTruncated.size)

        // Invalid magic byte
        val invalidMagic = ByteArray(10) { 0xFF.toByte() }
        val decodedInvalid = codec.decode(invalidMagic)
        assertEquals(0, decodedInvalid.size)
    }
}
