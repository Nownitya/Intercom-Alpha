package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AcousticWakeWordEngineTest {

    @Test
    fun testInitializationState() {
        val engine = AcousticWakeWordEngine()
        assertEquals(VoxState.IDLE, engine.voxState.value)
        assertEquals(null, engine.latestEvent.value)
    }

    @Test
    fun testHeyAlphaKeywordSpotting() = runTest {
        val config = WakeWordConfig(
            sampleRateHz = 16000,
            confidenceThreshold = 0.60f,
            energyThresholdDb = -45.0f,
            minUtteranceDurationMs = 250L,
            maxUtteranceDurationMs = 1200L
        )
        val engine = AcousticWakeWordEngine(config)

        var timeMs = 1000L
        val frameSamples = 320 // 20ms @ 16kHz
        var detectedEvent: WakeWordEvent? = null

        // 1. Syllable 1 ("Hey"): Voiced 440 Hz tone (low ZCR), 10 frames = 200ms
        for (f in 0 until 10) {
            val samples = ShortArray(frameSamples) { i ->
                (12000 * sin(2.0 * PI * 440.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            val evt = engine.processSamples(samples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        // 2. Syllable 2 ("Al"): Open vowel 800 Hz tone, 8 frames = 160ms
        for (f in 0 until 8) {
            val samples = ShortArray(frameSamples) { i ->
                (10000 * sin(2.0 * PI * 800.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            val evt = engine.processSamples(samples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        // 3. Syllable 3 ("-pha"): Fricative high-frequency 'f' noise, 8 frames = 160ms
        val random = Random(42)
        for (f in 0 until 8) {
            val samples = ShortArray(frameSamples) {
                // Alternating high frequency / high ZCR
                (random.nextInt(-8000, 8000)).toShort()
            }
            val evt = engine.processSamples(samples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        // 4. Utterance trailing silence: 8 frames = 160ms
        for (f in 0 until 8) {
            val silenceSamples = ShortArray(frameSamples) { 0 }
            val evt = engine.processSamples(silenceSamples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        assertNotNull(detectedEvent, "Engine should identify 'Hey Alpha' acoustic pattern")
        assertEquals(WakeWordType.HEY_ALPHA, detectedEvent.type)
        assertFalse(detectedEvent.isEmergency)
        assertTrue(detectedEvent.confidence >= 0.60f)
        assertEquals(VoxState.TRIGGERED, engine.voxState.value)
    }

    @Test
    fun testEmergencySosKeywordSpotting() = runTest {
        val config = WakeWordConfig(
            sampleRateHz = 16000,
            confidenceThreshold = 0.60f,
            energyThresholdDb = -45.0f,
            minUtteranceDurationMs = 300L,
            maxUtteranceDurationMs = 1400L
        )
        val engine = AcousticWakeWordEngine(config)

        var timeMs = 2000L
        val frameSamples = 320
        var detectedEvent: WakeWordEvent? = null

        // 1. "Emergency" voiced preamble: 14 frames = 280ms
        for (f in 0 until 14) {
            val samples = ShortArray(frameSamples) { i ->
                (10000 * sin(2.0 * PI * 600.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            val evt = engine.processSamples(samples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        // 2. "SOS" sharp sibilant bursts: 16 frames = 320ms (high ZCR & high differences)
        val random = Random(99)
        for (f in 0 until 16) {
            val samples = ShortArray(frameSamples) { i ->
                val sign = if (i % 2 == 0) 1 else -1
                (sign * (6000 + random.nextInt(2000))).toShort()
            }
            val evt = engine.processSamples(samples, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        // 3. Trailing silence to close utterance: 8 frames = 160ms
        for (f in 0 until 8) {
            val silence = ShortArray(frameSamples) { 0 }
            val evt = engine.processSamples(silence, timeMs)
            if (evt != null) detectedEvent = evt
            timeMs += 20L
        }

        assertNotNull(detectedEvent, "Engine should identify 'Emergency SOS' acoustic pattern")
        assertEquals(WakeWordType.EMERGENCY_SOS, detectedEvent.type)
        assertTrue(detectedEvent.isEmergency)
    }

    @Test
    fun testVoxStateMachineTransitions() = runTest {
        val config = WakeWordConfig(
            sampleRateHz = 16000,
            energyThresholdDb = -45.0f,
            holdTailDurationMs = 600L,
            cooldownDurationMs = 500L
        )
        val engine = AcousticWakeWordEngine(config)

        var timeMs = 5000L
        val frameSamples = 320
        val speechSamples = ShortArray(frameSamples) { 8000 }
        val silenceSamples = ShortArray(frameSamples) { 0 }

        // Start in IDLE
        assertEquals(VoxState.IDLE, engine.voxState.value)

        // Manual PTT activation
        engine.setManualPtt(true, timeMs)
        assertEquals(VoxState.TRIGGERED, engine.voxState.value)

        // Continuous speech keeps state in TRIGGERED
        for (i in 0 until 5) {
            timeMs += 20L
            engine.processSamples(speechSamples, timeMs)
            assertEquals(VoxState.TRIGGERED, engine.voxState.value)
        }

        // Silence for 200ms -> should enter HOLD_TAIL
        for (i in 0 until 10) { // 200ms
            timeMs += 20L
            engine.processSamples(silenceSamples, timeMs)
        }
        assertEquals(VoxState.HOLD_TAIL, engine.voxState.value)

        // Speech resumes during HOLD_TAIL -> returns to TRIGGERED
        timeMs += 20L
        engine.processSamples(speechSamples, timeMs)
        assertEquals(VoxState.TRIGGERED, engine.voxState.value)

        // Silence again
        for (i in 0 until 10) { // 200ms -> into HOLD_TAIL
            timeMs += 20L
            engine.processSamples(silenceSamples, timeMs)
        }
        assertEquals(VoxState.HOLD_TAIL, engine.voxState.value)

        // Silence persists for > holdTailDurationMs (600ms) -> transitions to COOLDOWN
        for (i in 0 until 35) { // 700ms
            timeMs += 20L
            engine.processSamples(silenceSamples, timeMs)
        }
        assertEquals(VoxState.COOLDOWN, engine.voxState.value)

        // Silence persists for > cooldownDurationMs (500ms) -> transitions to IDLE
        for (i in 0 until 30) { // 600ms
            timeMs += 20L
            engine.processSamples(silenceSamples, timeMs)
        }
        assertEquals(VoxState.IDLE, engine.voxState.value)
    }

    @Test
    fun testPcmByteProcessingAndReset() = runTest {
        val engine = AcousticWakeWordEngine()
        val pcmBytes = ByteArray(640) { 0 } // 320 samples of silence

        val result = engine.processPcmBytes(pcmBytes, 1000L)
        assertEquals(null, result)
        assertEquals(VoxState.IDLE, engine.voxState.value)

        engine.setManualPtt(true, 1020L)
        assertEquals(VoxState.TRIGGERED, engine.voxState.value)

        engine.reset()
        assertEquals(VoxState.IDLE, engine.voxState.value)
        assertEquals(null, engine.latestEvent.value)
    }
}
