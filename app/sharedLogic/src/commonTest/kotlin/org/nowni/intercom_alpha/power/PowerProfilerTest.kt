package org.nowni.intercom_alpha.power

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerProfilerTest {

    @Test
    fun testInitialIdleTracking() = runTest {
        val t0 = 10_000L
        val profiler = PowerProfiler(startTimeMs = t0)

        assertEquals(RadioPowerState.IDLE, profiler.getCurrentState())

        // 10 seconds later
        val report = profiler.generateReport(now = t0 + 10_000L)
        assertEquals(RadioPowerState.IDLE, report.currentState)
        assertEquals(10_000L, report.timeInIdleMs)
        assertEquals(0L, report.timeInTxMs)
        assertEquals(0.0, report.radioDutyCyclePercent)
        assertEquals(PowerAlert.NONE, report.activeAlert)
    }

    @Test
    fun testDutyCycleCalculation() = runTest {
        val t0 = 10_000L
        val profiler = PowerProfiler(startTimeMs = t0)

        // Idle for 80 seconds
        profiler.transitionTo(RadioPowerState.TRANSMITTING, now = t0 + 80_000L)
        // Transmit for 20 seconds
        val report = profiler.generateReport(now = t0 + 100_000L)

        assertEquals(80_000L, report.timeInIdleMs)
        assertEquals(20_000L, report.timeInTxMs)
        assertEquals(100_000L, report.totalSessionDurationMs)
        // Active time = 20s / 100s = 20%
        assertEquals(20.0, report.radioDutyCyclePercent)
    }

    @Test
    fun testFourHourMotorcycleRideBatteryDrainWellBelow30Percent() = runTest {
        val t0 = 1_000_000L
        val profiler = PowerProfiler(
            spec = HardwarePowerSpec(
                idleCurrentMa = 20.0,
                transmitCurrentMa = 75.0,
                receiveCurrentMa = 55.0,
                relayCurrentMa = 35.0,
                referenceBatteryCapacityMah = 4000.0
            ),
            startTimeMs = t0
        )

        val oneHourMs = 3600_000L
        var currentT = t0

        // Simulate 4 continuous hours of realistic motorcycle mesh activity:
        // 70% idle, 10% transmit, 15% receive, 5% relay per hour
        for (hour in 1..4) {
            // Idle: 2520s (70%)
            currentT += (0.70 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.TRANSMITTING, now = currentT)

            // Transmit: 360s (10%)
            currentT += (0.10 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.RECEIVING, now = currentT)

            // Receive: 540s (15%)
            currentT += (0.15 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.RELAYING, now = currentT)

            // Relay: 180s (5%)
            currentT += (0.05 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.IDLE, now = currentT)
        }

        val report = profiler.generateReport(now = currentT)
        assertEquals(4 * oneHourMs, report.totalSessionDurationMs)

        // Acceptance Criteria: 4-hour battery consumption < 30%
        assertTrue(report.estimatedDrainPercent < 30.0, "Drain was ${report.estimatedDrainPercent}%, expected < 30%")
        // In reality, 31.5 mA avg * 4h = 126 mAh -> ~3.15% on a 4,000 mAh battery
        assertTrue(report.estimatedDrainPercent in 2.5..6.0)
        assertEquals(PowerAlert.NONE, report.activeAlert)
    }

    @Test
    fun testRunawayWakeLockDetection() = runTest {
        val t0 = 10_000L
        val profiler = PowerProfiler(
            spec = HardwarePowerSpec(maxContinuousTxSeconds = 60L),
            startTimeMs = t0
        )

        // Start transmitting
        profiler.transitionTo(RadioPowerState.TRANSMITTING, now = t0 + 1000L)

        // 30 seconds later: Still safe
        val reportSafe = profiler.generateReport(now = t0 + 31_000L)
        assertEquals(PowerAlert.NONE, reportSafe.activeAlert)

        // 65 seconds later: Runaway wake lock triggered (> 60s continuous Tx)
        val reportAlert = profiler.generateReport(now = t0 + 66_000L)
        assertEquals(PowerAlert.RUNAWAY_WAKELOCK, reportAlert.activeAlert)
    }

    @Test
    fun testMarkdownSummaryAndReset() = runTest {
        val t0 = 10_000L
        val profiler = PowerProfiler(startTimeMs = t0)

        profiler.transitionTo(RadioPowerState.RECEIVING, now = t0 + 10_000L)
        val report = profiler.generateReport(now = t0 + 20_000L)

        val markdown = report.toMarkdownSummary()
        assertTrue(markdown.contains("Battery & Power Profile Report"))
        assertTrue(markdown.contains("Radio Duty Cycle"))
        assertTrue(markdown.contains("4-Hour Projection"))

        profiler.reset(now = t0 + 30_000L)
        val resetReport = profiler.generateReport(now = t0 + 30_000L)
        assertEquals(0L, resetReport.timeInIdleMs)
        assertEquals(0L, resetReport.timeInRxMs)
        assertEquals(0.0, resetReport.radioDutyCyclePercent)
    }
}
