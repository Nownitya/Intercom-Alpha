package org.nowni.intercom_alpha.power

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryGovernorTest {

    @Test
    fun testInitialState() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")
        val state = governor.governorState.value

        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals(100, state.batteryPercent)
        assertFalse(state.isCharging)
        assertFalse(state.isStill)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)
        assertEquals("HIGH", state.dutyCycle.maxAudioProfile)
        assertNull(state.wakeReason)
    }

    @Test
    fun testBatteryThresholdScaling() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")

        // 75% -> NORMAL
        var state = governor.updateBattery(percent = 75)
        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)

        // 28% (< 30%) -> CONSERVATIVE
        state = governor.updateBattery(percent = 28)
        assertEquals(PowerPolicy.CONSERVATIVE, state.policy)
        assertEquals(50.0, state.dutyCycle.dutyCyclePercent)
        assertEquals("MEDIUM", state.dutyCycle.maxAudioProfile)

        // 12% (< 15%) -> EXTREME_SURVIVAL
        state = governor.updateBattery(percent = 12)
        assertEquals(PowerPolicy.EXTREME_SURVIVAL, state.policy)
        assertEquals(20.0, state.dutyCycle.dutyCyclePercent)
        assertEquals("LOW", state.dutyCycle.maxAudioProfile)

        // Charging while low battery -> NORMAL
        state = governor.updateBattery(percent = 12, charging = true)
        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)
    }

    @Test
    fun testRestStopStillnessDetection() = runTest {
        val config = GovernorConfig(
            restStopStillnessDurationMs = 600_000L, // 10 min
            restStopSpeedThresholdKmh = 3.0
        )
        val governor = BatteryGovernor(nodeId = "local-rider", config = config)
        val startTime = 1_000_000L

        // Stopped at rest stop (0 km/h, still = true)
        governor.updateMotion(speedKmh = 0.0, still = true, timestampMs = startTime)

        // 5 minutes later (300,000 ms) -> Not yet 10 min, remains NORMAL
        var state = governor.tick(timestampMs = startTime + 300_000L)
        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals(300_000L, state.stillDurationMs)

        // 10 minutes later (600,000 ms) -> Transitions to REST_STOP_SLEEP
        state = governor.tick(timestampMs = startTime + 600_000L)
        assertEquals(PowerPolicy.REST_STOP_SLEEP, state.policy)
        assertEquals(5.0, state.dutyCycle.dutyCyclePercent)
        assertEquals("ULTRA_LOW", state.dutyCycle.maxAudioProfile)
        assertEquals(600_000L, state.stillDurationMs)
    }

    @Test
    fun testMotionWakeFromSleep() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")
        val startTime = 1_000_000L

        // Put into sleep mode
        governor.updateMotion(speedKmh = 0.0, still = true, timestampMs = startTime)
        governor.tick(timestampMs = startTime + 650_000L)
        assertEquals(PowerPolicy.REST_STOP_SLEEP, governor.governorState.value.policy)

        // Motion detected: rider resumes riding at 25 km/h
        val state = governor.updateMotion(speedKmh = 25.0, still = false, timestampMs = startTime + 700_000L)
        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals("MOTION_DETECTED", state.wakeReason)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)
        assertFalse(state.isStill)
    }

    @Test
    fun testPttWakeFromSleep() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")
        val startTime = 1_000_000L

        // Enter sleep mode
        governor.updateMotion(speedKmh = 0.0, still = true, timestampMs = startTime)
        governor.tick(timestampMs = startTime + 650_000L)
        assertEquals(PowerPolicy.REST_STOP_SLEEP, governor.governorState.value.policy)

        // Rider presses PTT button on handlebar
        val state = governor.onPttPressed(timestampMs = startTime + 660_000L)
        assertEquals(PowerPolicy.NORMAL, state.policy)
        assertEquals("PTT_PRESSED", state.wakeReason)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)
    }

    @Test
    fun testEmergencySosPreemption() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")

        // Put into extreme survival due to 8% critical battery
        governor.updateBattery(percent = 8)
        assertEquals(PowerPolicy.EXTREME_SURVIVAL, governor.governorState.value.policy)

        // Emergency SOS activated
        var state = governor.updateEmergencyStatus(emergencyActive = true)
        assertEquals(PowerPolicy.EMERGENCY_OVERRIDE, state.policy)
        assertEquals(100.0, state.dutyCycle.dutyCyclePercent)
        assertEquals("HIGH", state.dutyCycle.maxAudioProfile)
        assertTrue(state.isEmergencyActive)

        // Emergency cleared -> reverts to EXTREME_SURVIVAL because battery is still 8%
        state = governor.updateEmergencyStatus(emergencyActive = false)
        assertEquals(PowerPolicy.EXTREME_SURVIVAL, state.policy)
        assertEquals(20.0, state.dutyCycle.dutyCyclePercent)
        assertFalse(state.isEmergencyActive)
    }

    @Test
    fun testManualOverride() = runTest {
        val governor = BatteryGovernor(nodeId = "local-rider")

        // Manually force CONSERVATIVE at 90% battery
        var state = governor.setManualOverride(PowerPolicy.CONSERVATIVE)
        assertEquals(PowerPolicy.CONSERVATIVE, state.policy)

        // Reset manual override to auto
        state = governor.setManualOverride(null)
        assertEquals(PowerPolicy.NORMAL, state.policy)
    }

    @Test
    fun testWirePacketSerialization() {
        val governor = BatteryGovernor(nodeId = "rider-alpha")
        val packet = governor.createPowerStatusPacket(timestampMs = 5000L)

        assertEquals("rider-alpha", packet.nodeId)
        assertEquals(100, packet.batteryPercent)
        assertEquals(PowerPolicy.NORMAL, packet.policy)
        assertFalse(packet.isCharging)

        val jsonString = governor.serializePowerPacket(packet)
        assertTrue(jsonString.contains("rider-alpha"))
        assertTrue(jsonString.contains("NORMAL"))

        val parsed = governor.parsePowerPacket(jsonString)
        assertNotNull(parsed)
        assertEquals(packet.nodeId, parsed.nodeId)
        assertEquals(packet.batteryPercent, parsed.batteryPercent)
        assertEquals(packet.policy, parsed.policy)
        assertEquals(packet.isCharging, parsed.isCharging)
        assertEquals(packet.timestampMs, parsed.timestampMs)
    }
}
