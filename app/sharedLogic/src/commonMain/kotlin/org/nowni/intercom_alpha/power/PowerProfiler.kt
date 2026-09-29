package org.nowni.intercom_alpha.power

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.max
import kotlin.math.round

/**
 * Operating radio state for estimating battery consumption and duty cycle.
 */
@Serializable
enum class RadioPowerState {
    /** Background BLE mesh scanning and periodic advertising */
    IDLE,

    /** Active voice transmission (mic capture, ADPCM compression, GATT notifications) */
    TRANSMITTING,

    /** Active voice reception (packet ingress, jitter buffering, decoding, speaker playback) */
    RECEIVING,

    /** Background multi-hop packet relay forwarding without local audio playback */
    RELAYING
}

/**
 * Diagnostic power alert triggered when abnormal consumption or stuck states occur.
 */
@Serializable
enum class PowerAlert {
    /** System operating within normal consumption parameters */
    NONE,

    /** Continuous transmission or wake lock held beyond safety duration */
    RUNAWAY_WAKELOCK,

    /** Hourly drain rate exceeds target threshold (> 7.5% per hour) */
    EXCESSIVE_DRAIN,

    /** Battery level critically low (< 15%) */
    CRITICAL_BATTERY
}

/**
 * Hardware electrical current consumption profile in milliamperes (mA).
 *
 * Typical values measured on modern BLE 5.0+ smartphones (Pixel / iPhone / Galaxy):
 * - Idle BLE mesh background duty cycle: ~20 mA
 * - Transmitting with mic + DSP + radio: ~75 mA
 * - Receiving with speaker + jitter + decode: ~55 mA
 * - Relaying multi-hop forwarding: ~35 mA
 */
@Serializable
data class HardwarePowerSpec(
    val idleCurrentMa: Double = 20.0,
    val transmitCurrentMa: Double = 75.0,
    val receiveCurrentMa: Double = 55.0,
    val relayCurrentMa: Double = 35.0,
    val referenceBatteryCapacityMah: Double = 4000.0,
    val maxContinuousTxSeconds: Long = 60L,
    val maxHourlyDrainPercent: Double = 7.5
)

/**
 * Periodic or on-demand power diagnostic telemetry report.
 */
@Serializable
data class PowerReport(
    val timestamp: Long,
    val totalSessionDurationMs: Long,
    val currentState: RadioPowerState,
    val timeInIdleMs: Long,
    val timeInTxMs: Long,
    val timeInRxMs: Long,
    val timeInRelayMs: Long,
    val radioDutyCyclePercent: Double,
    val estimatedConsumedMah: Double,
    val estimatedDrainPercent: Double,
    val hourlyDrainRatePercent: Double,
    val estimatedFourHourDrainPercent: Double,
    val activeAlert: PowerAlert
) {
    /**
     * Renders a human-readable markdown table of current power metrics.
     */
    fun toMarkdownSummary(): String {
        val sb = StringBuilder()
        val durationMin = round((totalSessionDurationMs / 60000.0) * 10.0) / 10.0
        val dutyPct = round(radioDutyCyclePercent * 10.0) / 10.0
        val consumed = round(estimatedConsumedMah * 10.0) / 10.0
        val drainPct = round(estimatedDrainPercent * 10.0) / 10.0
        val ratePerHour = round(hourlyDrainRatePercent * 10.0) / 10.0
        val fourHourProj = round(estimatedFourHourDrainPercent * 10.0) / 10.0

        sb.appendLine("### 🔋 Battery & Power Profile Report (${durationMin} min)")
        sb.appendLine("- **Current State:** `$currentState` | **Alert:** `$activeAlert`")
        sb.appendLine("- **Radio Duty Cycle:** $dutyPct% (Active Tx/Rx/Relay vs Total Time)")
        sb.appendLine("- **Consumed Energy:** ${consumed} mAh (${drainPct}% of 4,000 mAh reference)")
        sb.appendLine("- **Burn Rate:** ${ratePerHour}% / hour")
        sb.appendLine("- **4-Hour Projection:** ${fourHourProj}% (Target: < 30.0%)")
        sb.appendLine()
        sb.appendLine("| State | Time (s) | Proportion (%) |")
        sb.appendLine("| :--- | :--- | :--- |")
        val totalSec = max(1.0, totalSessionDurationMs / 1000.0)
        sb.appendLine("| Idle | ${timeInIdleMs / 1000}s | ${round((timeInIdleMs / 1000.0 / totalSec) * 1000.0) / 10.0}% |")
        sb.appendLine("| Transmit | ${timeInTxMs / 1000}s | ${round((timeInTxMs / 1000.0 / totalSec) * 1000.0) / 10.0}% |")
        sb.appendLine("| Receive | ${timeInRxMs / 1000}s | ${round((timeInRxMs / 1000.0 / totalSec) * 1000.0) / 10.0}% |")
        sb.appendLine("| Relay | ${timeInRelayMs / 1000}s | ${round((timeInRelayMs / 1000.0 / totalSec) * 1000.0) / 10.0}% |")

        return sb.toString()
    }
}

/**
 * Multiplatform Battery Drain Profiler and Power Telemetry Manager.
 *
 * Tracks the duty cycle of BLE mesh radios and audio DSP subsystems, models battery mAh
 * consumption, detects runaway wake locks, and validates the 4-hour <30% battery drain constraint.
 */
class PowerProfiler(
    val spec: HardwarePowerSpec = HardwarePowerSpec(),
    startTimeMs: Long = currentTimeMillis()
) {
    private val mutex = Mutex()
    private var sessionStartMs = startTimeMs
    private var currentState = RadioPowerState.IDLE
    private var lastStateTransitionMs = startTimeMs

    private var timeInIdleMs = 0L
    private var timeInTxMs = 0L
    private var timeInRxMs = 0L
    private var timeInRelayMs = 0L

    /**
     * Transitions radio to a new power state, accumulating elapsed time in the previous state.
     */
    suspend fun transitionTo(newState: RadioPowerState, now: Long = currentTimeMillis()) {
        mutex.withLock {
            accumulateStateTime(now)
            currentState = newState
            lastStateTransitionMs = now
        }
    }

    /**
     * Returns the current power state.
     */
    suspend fun getCurrentState(): RadioPowerState = mutex.withLock { currentState }

    /**
     * Generates a complete power diagnostic report.
     */
    suspend fun generateReport(now: Long = currentTimeMillis()): PowerReport {
        return mutex.withLock {
            accumulateStateTime(now)
            lastStateTransitionMs = now

            val totalDurationMs = max(1L, now - sessionStartMs)
            val activeTimeMs = timeInTxMs + timeInRxMs + timeInRelayMs
            val dutyCycle = (activeTimeMs.toDouble() / totalDurationMs.toDouble()) * 100.0

            // Energy calculation in mAh: Current (mA) * (Time in hours)
            val idleMah = spec.idleCurrentMa * (timeInIdleMs / 3600000.0)
            val txMah = spec.transmitCurrentMa * (timeInTxMs / 3600000.0)
            val rxMah = spec.receiveCurrentMa * (timeInRxMs / 3600000.0)
            val relayMah = spec.relayCurrentMa * (timeInRelayMs / 3600000.0)
            val totalConsumedMah = idleMah + txMah + rxMah + relayMah

            val drainPercent = (totalConsumedMah / spec.referenceBatteryCapacityMah) * 100.0
            val sessionHours = totalDurationMs / 3600000.0
            val hourlyRate = if (sessionHours > 0.0) drainPercent / sessionHours else 0.0
            val fourHourProjected = hourlyRate * 4.0

            // Alert evaluation
            var alert = PowerAlert.NONE
            if (currentState == RadioPowerState.TRANSMITTING) {
                val currentTxDurationSec = (now - lastStateTransitionMs) / 1000L
                if (currentTxDurationSec >= spec.maxContinuousTxSeconds) {
                    alert = PowerAlert.RUNAWAY_WAKELOCK
                }
            }
            if (alert == PowerAlert.NONE && hourlyRate > spec.maxHourlyDrainPercent && totalDurationMs > 60000L) {
                alert = PowerAlert.EXCESSIVE_DRAIN
            }

            PowerReport(
                timestamp = now,
                totalSessionDurationMs = totalDurationMs,
                currentState = currentState,
                timeInIdleMs = timeInIdleMs,
                timeInTxMs = timeInTxMs,
                timeInRxMs = timeInRxMs,
                timeInRelayMs = timeInRelayMs,
                radioDutyCyclePercent = dutyCycle,
                estimatedConsumedMah = totalConsumedMah,
                estimatedDrainPercent = drainPercent,
                hourlyDrainRatePercent = hourlyRate,
                estimatedFourHourDrainPercent = fourHourProjected,
                activeAlert = alert
            )
        }
    }

    /**
     * Resets telemetry counters to initial state.
     */
    suspend fun reset(now: Long = currentTimeMillis()) {
        mutex.withLock {
            sessionStartMs = now
            lastStateTransitionMs = now
            currentState = RadioPowerState.IDLE
            timeInIdleMs = 0L
            timeInTxMs = 0L
            timeInRxMs = 0L
            timeInRelayMs = 0L
        }
    }

    private fun accumulateStateTime(now: Long) {
        val elapsed = max(0L, now - lastStateTransitionMs)
        when (currentState) {
            RadioPowerState.IDLE -> timeInIdleMs += elapsed
            RadioPowerState.TRANSMITTING -> timeInTxMs += elapsed
            RadioPowerState.RECEIVING -> timeInRxMs += elapsed
            RadioPowerState.RELAYING -> timeInRelayMs += elapsed
        }
    }
}
