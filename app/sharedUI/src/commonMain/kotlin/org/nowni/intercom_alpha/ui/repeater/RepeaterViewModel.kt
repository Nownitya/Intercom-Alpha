package org.nowni.intercom_alpha.ui.repeater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.bridge.IntercomHardwareBridge
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.RepeaterDutyCycleMode

/**
 * ViewModel managing Basecamp Stationary Repeater operational state,
 * low-power standby lock, and diagnostic ping link interrogation.
 */
class RepeaterViewModel(
    val repeaterService: MeshRepeaterService = MeshRepeaterService(
        repeaterId = "basecamp-relay-1",
        repeaterName = "Basecamp Relay Alpha"
    ),
    val hardwareBridge: IntercomHardwareBridge = NoOpIntercomHardwareBridge()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RepeaterUiState(
            repeaterId = repeaterService.repeaterId,
            repeaterName = repeaterService.repeaterName,
            isRunning = repeaterService.telemetry.value.isRunning,
            batteryPercent = repeaterService.telemetry.value.batteryPercent,
            dutyCycleMode = repeaterService.dutyCycleMode,
            packetsForwarded = repeaterService.telemetry.value.packetsForwarded,
            activeNeighborCount = repeaterService.telemetry.value.activeNeighborCount,
            activeNeighbors = repeaterService.telemetry.value.activeNeighbors
        )
    )
    val uiState: StateFlow<RepeaterUiState> = _uiState.asStateFlow()

    init {
        // 1. Synchronize telemetry from MeshRepeaterService
        viewModelScope.launch {
            repeaterService.telemetry.collect { tele ->
                _uiState.update { current ->
                    current.copy(
                        repeaterId = tele.repeaterId,
                        repeaterName = tele.repeaterName,
                        isRunning = tele.isRunning,
                        batteryPercent = tele.batteryPercent,
                        packetsForwarded = tele.packetsForwarded,
                        packetsDroppedDuplicate = tele.packetsDroppedDuplicate,
                        packetsDroppedTtlExpired = tele.packetsDroppedTtlExpired,
                        lastForwardLatencyMs = tele.lastForwardLatencyMs,
                        beaconEmissionCount = tele.beaconEmissionCount,
                        activeNeighborCount = tele.activeNeighborCount,
                        activeNeighbors = tele.activeNeighbors
                    )
                }
                hardwareBridge.updateRepeaterTelemetry(tele)
            }
        }

        // 2. Synchronize incoming diagnostic pongs from hardware bridge
        viewModelScope.launch {
            hardwareBridge.incomingRepeaterPongs.collect { pong ->
                val now = currentTimeMillis()
                val latency = (now - pong.pingTimestampMs).coerceAtLeast(0L)
                _uiState.update { current ->
                    current.copy(
                        lastPingLatencyMs = latency,
                        isPinging = false,
                        statusMessage = "Diagnostic Pong: ${latency}ms (Batt: ${pong.batteryPercent}%)"
                    )
                }
            }
        }

        // 3. Synchronize repeater mode active status with hardware bridge
        viewModelScope.launch {
            hardwareBridge.isRepeaterModeActive.collect { active ->
                if (active && !_uiState.value.isRunning) {
                    startRepeaterInternal()
                } else if (!active && _uiState.value.isRunning) {
                    stopRepeaterInternal()
                }
            }
        }
    }

    fun onAction(action: RepeaterUiAction) {
        when (action) {
            is RepeaterUiAction.StartRepeater -> startRepeater()
            is RepeaterUiAction.StopRepeater -> stopRepeater()
            is RepeaterUiAction.SetDutyCycle -> setDutyCycle(action.mode)
            is RepeaterUiAction.TriggerDiagnosticPing -> triggerDiagnosticPing()
            is RepeaterUiAction.ToggleNightStandby -> toggleNightStandby(action.enabled)
            is RepeaterUiAction.EmitBeacon -> emitBeacon()
        }
    }

    fun startRepeater() {
        startRepeaterInternal()
        hardwareBridge.setRepeaterModeActive(true)
    }

    private fun startRepeaterInternal() {
        repeaterService.start()
        _uiState.update {
            it.copy(
                isRunning = true,
                statusMessage = "Repeater Active - Audio Bypassed"
            )
        }
    }

    fun stopRepeater() {
        stopRepeaterInternal()
        hardwareBridge.setRepeaterModeActive(false)
    }

    private fun stopRepeaterInternal() {
        repeaterService.stop()
        _uiState.update {
            it.copy(
                isRunning = false,
                statusMessage = "Standby Ready"
            )
        }
    }

    fun setDutyCycle(mode: RepeaterDutyCycleMode) {
        repeaterService.setDutyCycle(mode)
        _uiState.update { it.copy(dutyCycleMode = mode) }
    }

    fun emitBeacon() {
        val beacon = repeaterService.emitBeaconPacket()
        hardwareBridge.sendRepeaterPacket(beacon)
        _uiState.update {
            it.copy(
                beaconEmissionCount = repeaterService.telemetry.value.beaconEmissionCount,
                statusMessage = "Beacon Broadcast Emitted"
            )
        }
    }

    fun triggerDiagnosticPing() {
        val pingPacket = repeaterService.createDiagnosticPing(
            senderId = repeaterService.repeaterId,
            sequence = _uiState.value.beaconEmissionCount + 1
        )
        hardwareBridge.sendRepeaterPacket(pingPacket)
        _uiState.update {
            it.copy(
                isPinging = true,
                statusMessage = "Sending Diagnostic Ping..."
            )
        }
    }

    fun toggleNightStandby(enabled: Boolean) {
        _uiState.update { it.copy(isNightStandbyLocked = enabled) }
    }
}
