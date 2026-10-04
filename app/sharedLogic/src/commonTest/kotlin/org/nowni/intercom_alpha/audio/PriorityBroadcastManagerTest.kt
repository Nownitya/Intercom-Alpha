package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PriorityBroadcastManagerTest {

    @Test
    fun testAlertChimeSynthesis() {
        val hazardChime = AlertChimeSynthesizer.generateHazardChime()
        // 150ms at 48,000 samples/sec = 7,200 samples * 2 bytes/sample = 14,400 bytes
        assertEquals(14400, hazardChime.size)

        val emergencyChime = AlertChimeSynthesizer.generateEmergencyChime()
        // 120ms (5760 samples) + 130ms (6240 samples) = 12000 samples * 2 = 24,000 bytes
        assertEquals(24000, emergencyChime.size)

        // Verify audio content is non-zero
        val hasNonZero = hazardChime.any { it != 0.toByte() }
        assertTrue(hasNonZero)
    }

    @Test
    fun testNormalTrafficFlow() = runTest {
        val manager = PriorityBroadcastManager()
        val normalFrame = PrioritizedAudioFrame(
            priority = PriorityLevel.NORMAL,
            senderId = "rider-1",
            sequence = 1L,
            timestamp = 1000L,
            data = byteArrayOf(1, 2, 3)
        )

        assertTrue(manager.shouldPlayFrame(normalFrame, now = 1000L))
        assertEquals(PriorityLevel.NORMAL, manager.activePriority.value)
        assertFalse(manager.isEmergencyActive.value)
    }

    @Test
    fun testEmergencyPreemption() = runTest {
        val manager = PriorityBroadcastManager(hangoverDurationMs = 600L)

        val normalFrame = PrioritizedAudioFrame(
            priority = PriorityLevel.NORMAL,
            senderId = "chatter-bob",
            sequence = 10L,
            timestamp = 1000L,
            data = byteArrayOf(1, 1, 1)
        )
        assertTrue(manager.shouldPlayFrame(normalFrame, now = 1000L))

        // Emergency frame arrives at t=1100ms
        val emergencyFrame = PrioritizedAudioFrame(
            priority = PriorityLevel.EMERGENCY_OVERRIDE,
            senderId = "lead-alice",
            sequence = 1L,
            timestamp = 1100L,
            data = byteArrayOf(9, 9, 9),
            alertType = AlertType.HAZARD_WARNING
        )
        // Emergency frame immediately preempts normal stream
        assertTrue(manager.shouldPlayFrame(emergencyFrame, now = 1100L))
        assertEquals(PriorityLevel.EMERGENCY_OVERRIDE, manager.activePriority.value)
        assertTrue(manager.isEmergencyActive.value)

        // Subsequent normal frame arrives at t=1300ms during hangover window
        val interruptedNormal = PrioritizedAudioFrame(
            priority = PriorityLevel.NORMAL,
            senderId = "chatter-bob",
            sequence = 11L,
            timestamp = 1300L,
            data = byteArrayOf(2, 2, 2)
        )
        // Normal frame must be preempted and dropped
        assertFalse(manager.shouldPlayFrame(interruptedNormal, now = 1300L))
    }

    @Test
    fun testHangoverDecayAllowsNormalTrafficToResume() = runTest {
        val manager = PriorityBroadcastManager(hangoverDurationMs = 600L)

        val emergencyFrame = PrioritizedAudioFrame(
            priority = PriorityLevel.EMERGENCY_OVERRIDE,
            senderId = "lead-alice",
            sequence = 1L,
            timestamp = 1000L,
            data = byteArrayOf(9, 9, 9)
        )
        assertTrue(manager.shouldPlayFrame(emergencyFrame, now = 1000L))

        // At t=1500ms (500ms elapsed < 600ms hangover): normal frame still rejected
        val normalWhileActive = PrioritizedAudioFrame(
            priority = PriorityLevel.NORMAL,
            senderId = "bob",
            sequence = 2L,
            timestamp = 1500L,
            data = byteArrayOf(1)
        )
        assertFalse(manager.shouldPlayFrame(normalWhileActive, now = 1500L))

        // At t=1700ms (700ms elapsed > 600ms hangover): normal frame accepted!
        val normalAfterHangover = PrioritizedAudioFrame(
            priority = PriorityLevel.NORMAL,
            senderId = "bob",
            sequence = 3L,
            timestamp = 1700L,
            data = byteArrayOf(2)
        )
        assertTrue(manager.shouldPlayFrame(normalAfterHangover, now = 1700L))
        assertEquals(PriorityLevel.NORMAL, manager.activePriority.value)
        assertFalse(manager.isEmergencyActive.value)
    }

    @Test
    fun testPriorityGuidancePreemption() = runTest {
        val manager = PriorityBroadcastManager(hangoverDurationMs = 500L)

        val normal = PrioritizedAudioFrame(PriorityLevel.NORMAL, "bob", 1L, 1000L, byteArrayOf(1))
        val navPrompt = PrioritizedAudioFrame(PriorityLevel.PRIORITY, "gps-node", 1L, 1100L, byteArrayOf(2))
        val crashAlert = PrioritizedAudioFrame(PriorityLevel.EMERGENCY_OVERRIDE, "dave", 1L, 1200L, byteArrayOf(3))

        // 1. Normal plays
        assertTrue(manager.shouldPlayFrame(normal, now = 1000L))
        // 2. Navigation prompt preempts normal
        assertTrue(manager.shouldPlayFrame(navPrompt, now = 1100L))
        assertEquals(PriorityLevel.PRIORITY, manager.activePriority.value)
        // 3. Crash alert preempts navigation prompt
        assertTrue(manager.shouldPlayFrame(crashAlert, now = 1200L))
        assertEquals(PriorityLevel.EMERGENCY_OVERRIDE, manager.activePriority.value)
    }

    @Test
    fun testCreateEmergencyBroadcastWithChime() {
        val manager = PriorityBroadcastManager()
        val voicePayload = byteArrayOf(10, 20, 30, 40)

        val frame = manager.createEmergencyBroadcast(
            senderId = "rider-crash",
            audioData = voicePayload,
            alertType = AlertType.HAZARD_WARNING,
            sequence = 100L,
            timestamp = 2000L,
            includePreRollChime = true
        )

        assertEquals(PriorityLevel.EMERGENCY_OVERRIDE, frame.priority)
        assertEquals("rider-crash", frame.senderId)
        assertEquals(AlertType.HAZARD_WARNING, frame.alertType)
        // Payload size should be chime size (14400) + voice payload size (4)
        assertEquals(14404, frame.data.size)
    }

    @Test
    fun testExplicitReset() = runTest {
        val manager = PriorityBroadcastManager()
        val emergency = PrioritizedAudioFrame(PriorityLevel.EMERGENCY_OVERRIDE, "r1", 1L, 1000L, byteArrayOf(1))
        assertTrue(manager.shouldPlayFrame(emergency, now = 1000L))
        assertTrue(manager.isEmergencyActive.value)

        manager.reset()
        assertEquals(PriorityLevel.NORMAL, manager.activePriority.value)
        assertFalse(manager.isEmergencyActive.value)
    }
}
