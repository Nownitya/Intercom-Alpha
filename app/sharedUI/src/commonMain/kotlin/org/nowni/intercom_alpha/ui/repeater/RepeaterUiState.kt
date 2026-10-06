package org.nowni.intercom_alpha.ui.repeater

import org.nowni.intercom_alpha.mesh.RepeaterDutyCycleMode

/**
 * Presentation state for Basecamp Stationary Repeater node and cockpit HUD.
 */
data class RepeaterUiState(
    val repeaterId: String = "basecamp-relay-1",
    val repeaterName: String = "Basecamp Relay Alpha",
    val isRunning: Boolean = false,
    val isStationary: Boolean = true,
    val isSolarPowered: Boolean = true,
    val batteryPercent: Int = 100,
    val dutyCycleMode: RepeaterDutyCycleMode = RepeaterDutyCycleMode.CONTINUOUS,
    val packetsForwarded: Long = 0L,
    val packetsDroppedDuplicate: Long = 0L,
    val packetsDroppedTtlExpired: Long = 0L,
    val lastForwardLatencyMs: Long = 0L,
    val beaconEmissionCount: Long = 0L,
    val activeNeighbors: List<String> = emptyList(),
    val activeNeighborCount: Int = 0,
    val lastPingLatencyMs: Long? = null,
    val isPinging: Boolean = false,
    val isNightStandbyLocked: Boolean = false,
    val statusMessage: String = "Standby Ready"
)

/**
 * User actions and events dispatched from the Basecamp Repeater UI.
 */
sealed interface RepeaterUiAction {
    data object StartRepeater : RepeaterUiAction
    data object StopRepeater : RepeaterUiAction
    data class SetDutyCycle(val mode: RepeaterDutyCycleMode) : RepeaterUiAction
    data object TriggerDiagnosticPing : RepeaterUiAction
    data class ToggleNightStandby(val enabled: Boolean) : RepeaterUiAction
    data object EmitBeacon : RepeaterUiAction
}
