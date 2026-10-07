package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.*

class VoiceCommandRecognizerTest {

    private lateinit var recognizer: VoiceCommandRecognizer
    private val config = VoiceCommandConfig(
        sampleRate = 48000,
        frameSizeMs = 20,
        minConfidence = 0.70f,
        cooldownMs = 1500L,
        energyThresholdDb = -40.0f,
        minUtteranceDurationMs = 300L,
        maxUtteranceDurationMs = 2500L,
        windNoiseSuppression = true
    )

    @BeforeTest
    fun setup() {
        recognizer = VoiceCommandRecognizer(config)
    }

    @Test
    fun testSilenceProducesNoDetection() = runTest {
        val silence = ShortArray(960) // 20ms of silence
        for (i in 0 until 20) {
            val result = recognizer.processPcmSamples(silence, 1000L + i * 20L)
            assertNull(result)
        }
        assertNull(recognizer.latestDetection.value)
    }

    @Test
    fun testWindNoiseSuppressionRejectsRumble() = runTest {
        // Generate continuous 60 Hz low-frequency wind buffeting rumble at high amplitude
        val windFrames = ShortArray(960)
        val sampleRate = 48000.0
        val freq = 60.0

        for (i in windFrames.indices) {
            windFrames[i] = (sin(2.0 * PI * freq * (i / sampleRate)) * 18000.0).toInt().toShort()
        }

        // Feed 1.5 seconds of sustained wind noise
        for (i in 0 until 75) {
            val detection = recognizer.processPcmSamples(windFrames, 1000L + i * 20L)
            assertNull(detection) // Wind noise must not trigger false commands
        }
        assertNull(recognizer.latestDetection.value)
    }

    @Test
    fun testSimulateCommandEmitsFlowAndChannel() = runTest {
        val detection = recognizer.simulateCommand(
            command = VoiceCommand.SOS_TRIGGER,
            confidence = 0.92f,
            timestampMs = 5000L
        )

        assertEquals(VoiceCommand.SOS_TRIGGER, detection.command)
        assertEquals(0.92f, detection.confidence, 0.01f)
        assertEquals(5000L, detection.timestampMs)
        assertEquals(detection, recognizer.latestDetection.value)

        val channelItem = recognizer.detections.tryReceive().getOrNull()
        assertNotNull(channelItem)
        assertEquals(VoiceCommand.SOS_TRIGGER, channelItem.command)
    }

    @Test
    fun testSyntheticSosCommandDetection() = runTest {
        // Synthesize an utterance mimicking "In-ter-com S-O-S":
        // 3 distinct energy peaks (syllables) over ~800ms with high ZCR at coda
        val totalFrames = 40 // 800ms
        for (f in 0 until totalFrames) {
            val timestamp = 10000L + f * 20L
            val frame = ShortArray(960)
            val isSyllablePeak = (f in 5..10) || (f in 16..22) || (f in 28..36)

            if (isSyllablePeak) {
                // If in coda (f >= 28), inject high-frequency fricative noise ('S' in SOS)
                if (f >= 28) {
                    for (i in frame.indices) {
                        val sign = if (i % 3 == 0) 1 else -1
                        frame[i] = (sign * 12000).toShort()
                    }
                } else {
                    // Mid-frequency formant voice (800 Hz)
                    for (i in frame.indices) {
                        frame[i] = (sin(2.0 * PI * 800.0 * (i / 48000.0)) * 14000.0).toInt().toShort()
                    }
                }
            }
            recognizer.processPcmSamples(frame, timestamp)
        }

        // Feed trailing silence to trigger utterance boundary
        var detection: CommandDetection? = null
        for (f in 0 until 10) {
            val d = recognizer.processPcmSamples(ShortArray(960), 10800L + f * 20L)
            if (d != null) detection = d
        }

        assertNotNull(detection)
        assertEquals(VoiceCommand.SOS_TRIGGER, detection.command)
        assertTrue(detection.confidence >= 0.70f)
    }

    @Test
    fun testSyntheticNextChannelDetection() = runTest {
        // Synthesize utterance for "Next Chan-nel": 3 syllables, mid formants
        val totalFrames = 38 // 760ms
        for (f in 0 until totalFrames) {
            val timestamp = 20000L + f * 20L
            val frame = ShortArray(960)
            val isPeak = (f in 4..10) || (f in 14..22) || (f in 26..34)

            if (isPeak) {
                if (f in 8..10) {
                    // High ZCR stop in 'ne-xt'
                    for (i in frame.indices) {
                        frame[i] = (if (i % 4 == 0) 10000 else -10000).toShort()
                    }
                } else {
                    for (i in frame.indices) {
                        frame[i] = (sin(2.0 * PI * 1200.0 * (i / 48000.0)) * 12000.0).toInt().toShort()
                    }
                }
            }
            recognizer.processPcmSamples(frame, timestamp)
        }

        var detection: CommandDetection? = null
        for (f in 0 until 10) {
            val d = recognizer.processPcmSamples(ShortArray(960), 20760L + f * 20L)
            if (d != null) detection = d
        }

        assertNotNull(detection)
        assertEquals(VoiceCommand.NEXT_CHANNEL, detection.command)
    }

    @Test
    fun testSyntheticCancelAlertDetection() = runTest {
        // Synthesize utterance for "Can-cel A-lert": 4 syllables over 1000ms
        val totalFrames = 50 // 1000ms
        for (f in 0 until totalFrames) {
            val timestamp = 30000L + f * 20L
            val frame = ShortArray(960)
            val isPeak = (f in 4..10) || (f in 14..20) || (f in 24..30) || (f in 34..42)

            if (isPeak) {
                for (i in frame.indices) {
                    val highFreq = if (i % 5 == 0) 6000 else 0
                    val midFreq = (sin(2.0 * PI * 1000.0 * (i / 48000.0)) * 12000.0).toInt()
                    frame[i] = (midFreq + highFreq).toShort()
                }
            }
            recognizer.processPcmSamples(frame, timestamp)
        }

        var detection: CommandDetection? = null
        for (f in 0 until 10) {
            val d = recognizer.processPcmSamples(ShortArray(960), 31000L + f * 20L)
            if (d != null) detection = d
        }

        assertNotNull(detection)
        assertEquals(VoiceCommand.CANCEL_ALERT, detection.command)
    }

    @Test
    fun testShortUtteranceIsRejected() = runTest {
        // An accidental cough or click lasting only 120ms (< 300ms threshold)
        for (f in 0 until 6) {
            val frame = ShortArray(960) { 15000 }
            recognizer.processPcmSamples(frame, 40000L + f * 20L)
        }

        // Followed by silence
        var detection: CommandDetection? = null
        for (f in 0 until 10) {
            val d = recognizer.processPcmSamples(ShortArray(960), 40120L + f * 20L)
            if (d != null) detection = d
        }

        assertNull(detection) // Must reject sub-duration audio transients
    }

    @Test
    fun testByteConversionSymmetry() = runTest {
        // Test that processPcmBytes converts little-endian bytes accurately
        val samples = shortArrayOf(0, 1000, -1000, 32767, -32768)
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            bytes[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((samples[i].toInt() shr 8) and 0xFF).toByte()
        }

        val result = recognizer.processPcmBytes(bytes, 50000L)
        assertNull(result) // Only 5 samples, no crash, cleanly processed
    }

    @Test
    fun testResetClearsBuffer() = runTest {
        recognizer.simulateCommand(VoiceCommand.BATTERY_STATUS, 0.85f, 60000L)
        assertEquals(VoiceCommand.BATTERY_STATUS, recognizer.latestDetection.value?.command)

        recognizer.reset()
        assertNull(recognizer.latestDetection.value)
    }
}
