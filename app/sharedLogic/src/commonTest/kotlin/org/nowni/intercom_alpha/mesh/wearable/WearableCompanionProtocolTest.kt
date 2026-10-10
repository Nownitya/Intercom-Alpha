package org.nowni.intercom_alpha.mesh.wearable

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearableCompanionProtocolTest {

    @Test
    fun testCompactBinaryRoundtrip() {
        val original = WearableCompanionPacket(
            deviceId = "watch-ultra-01",
            riderId = "rider-alpine",
            deviceType = WearableDeviceType.APPLE_WATCH,
            pttState = PttButtonState.PRESSED,
            vitals = RiderBiometricVitals(
                heartRateBpm = 145,
                spo2Percent = 97,
                skinTemperatureCelsius = 36.8,
                stressLevelIndex = 42,
                isFallDetected = false,
                batteryPercent = 85,
                timestampMs = 12345678L
            ),
            sequenceNumber = 42L,
            timestampMs = 12345678L
        )

        val bytes = original.toCompactBinary()
        assertEquals(12, bytes.size)

        val decoded = WearableCompanionPacket.fromCompactBinary(
            deviceId = "watch-ultra-01",
            riderId = "rider-alpine",
            bytes = bytes,
            timestampMs = 12345678L
        )

        assertNotNull(decoded)
        assertEquals(WearableDeviceType.APPLE_WATCH, decoded.deviceType)
        assertEquals(PttButtonState.PRESSED, decoded.pttState)
        assertEquals(42L, decoded.sequenceNumber)
        val vitals = decoded.vitals
        assertNotNull(vitals)
        assertEquals(145, vitals.heartRateBpm)
        assertEquals(97, vitals.spo2Percent)
        assertEquals(85, vitals.batteryPercent)
        assertEquals(42, vitals.stressLevelIndex)
        assertEquals(36.8, vitals.skinTemperatureCelsius)
        assertFalse(vitals.isFallDetected)
    }

    @Test
    fun testCorruptedFrameRejection() {
        val invalidBytes = byteArrayOf(0x02, 0x00, 0x00) // Invalid version and length
        val decoded = WearableCompanionPacket.fromCompactBinary(
            deviceId = "watch-01",
            riderId = "rider-01",
            bytes = invalidBytes,
            timestampMs = 1000L
        )
        assertNull(decoded)
    }

    @Test
    fun testPttStateMachineAndHaptics() = runTest {
        val protocol = WearableCompanionProtocol(
            localRiderId = "rider-alpha",
            localDeviceId = "watch-01",
            debounceIntervalMs = 50L
        )

        assertEquals(PttButtonState.RELEASED, protocol.pttState.value)

        // Press PTT
        val p1 = protocol.processPttEvent(PttButtonState.PRESSED, timestampMs = 1000L)
        assertTrue(p1)
        assertEquals(PttButtonState.PRESSED, protocol.pttState.value)

        // Release PTT
        val p2 = protocol.processPttEvent(PttButtonState.RELEASED, timestampMs = 1100L)
        assertTrue(p2)
        assertEquals(PttButtonState.RELEASED, protocol.pttState.value)

        // Double-Tap SOS
        val p3 = protocol.processPttEvent(PttButtonState.DOUBLE_TAP_SOS, timestampMs = 1200L)
        assertTrue(p3)
        assertEquals(PttButtonState.DOUBLE_TAP_SOS, protocol.pttState.value)
        assertEquals(RiderDistressStatus.EMERGENCY_SOS, protocol.riderDistressStatus.value)
    }

    @Test
    fun testPttDebouncing() = runTest {
        val protocol = WearableCompanionProtocol(
            localRiderId = "rider-alpha",
            localDeviceId = "watch-01",
            debounceIntervalMs = 50L
        )

        protocol.processPttEvent(PttButtonState.PRESSED, timestampMs = 1000L)

        // Duplicate bounce event at +10ms should be rejected
        val bounce = protocol.processPttEvent(PttButtonState.PRESSED, timestampMs = 1010L)
        assertFalse(bounce)

        // Legitimate event after debounce window
        val legitimate = protocol.processPttEvent(PttButtonState.RELEASED, timestampMs = 1060L)
        assertTrue(legitimate)
    }

    @Test
    fun testBiometricDistressClassification() = runTest {
        val protocol = WearableCompanionProtocol(
            localRiderId = "rider-alpha",
            localDeviceId = "watch-01"
        )

        // Normal vitals
        val normalVitals = RiderBiometricVitals(heartRateBpm = 75, spo2Percent = 98)
        val s1 = protocol.processBiometricVitals(normalVitals)
        assertEquals(RiderDistressStatus.NORMAL, s1)

        // Fatigue warning (elevated heart rate / stress)
        val fatigueVitals = RiderBiometricVitals(heartRateBpm = 168, stressLevelIndex = 85)
        val s2 = protocol.processBiometricVitals(fatigueVitals)
        assertEquals(RiderDistressStatus.FATIGUE_WARNING, s2)

        // Critical tachycardia (> 185 bpm)
        val criticalVitals = RiderBiometricVitals(heartRateBpm = 192, spo2Percent = 94)
        val s3 = protocol.processBiometricVitals(criticalVitals)
        assertEquals(RiderDistressStatus.CRITICAL_TACHYCARDIA, s3)

        // Fall detected
        val fallVitals = RiderBiometricVitals(heartRateBpm = 110, isFallDetected = true)
        val s4 = protocol.processBiometricVitals(fallVitals)
        assertEquals(RiderDistressStatus.FALL_CONFIRMED, s4)
    }

    @Test
    fun testIncomingFrameProcessing() = runTest {
        val protocol = WearableCompanionProtocol(
            localRiderId = "rider-alpha",
            localDeviceId = "companion-remote"
        )

        val frame = WearableCompanionPacket(
            deviceId = "watch-peer",
            riderId = "rider-bravo",
            deviceType = WearableDeviceType.WEAR_OS,
            pttState = PttButtonState.PRESSED,
            vitals = RiderBiometricVitals(
                heartRateBpm = 175,
                spo2Percent = 96,
                isFallDetected = false
            ),
            sequenceNumber = 99L,
            timestampMs = 5000L
        ).toCompactBinary()

        val parsed = protocol.processIncomingFrame(
            deviceId = "watch-peer",
            riderId = "rider-bravo",
            bytes = frame,
            timestampMs = 5000L
        )

        assertNotNull(parsed)
        assertEquals(PttButtonState.PRESSED, protocol.pttState.value)
        assertEquals(RiderDistressStatus.FATIGUE_WARNING, protocol.riderDistressStatus.value)
        assertEquals(175, protocol.latestVitals.value?.heartRateBpm)
    }
}
