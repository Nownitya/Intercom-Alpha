package org.nowni.intercom_alpha.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.AlertType
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.MeshTextManager

class HomeViewModel(
    val channelManager: ChannelManager = ChannelManager(),
    val proximityRadar: ProximityRadar = ProximityRadar(),
    val convoyGpsTracker: ConvoyGpsTracker = ConvoyGpsTracker("android-local", "Android Rider"),
    val meshTextManager: MeshTextManager = MeshTextManager("android-local", "Android Rider"),
    val priorityManager: PriorityBroadcastManager = PriorityBroadcastManager(),
    val blackboxRecorder: EmergencyBlackboxRecorder = EmergencyBlackboxRecorder(),
    val bitrateController: AdaptiveBitrateController = AdaptiveBitrateController()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            groupName = "Rider Club Alpha",
            inviteCode = "INTERCOM:v1:eyJncm91cElkIjoiZ3JwLTEwMSIsImdyb3VwTmFtZSI6IlJpZGVyIENsdWIgQWxwaGEiLCJsZWFkZXJJZCI6InBlZXItMSIsImV4cGlyZXNBdCI6NDkyMDA4ODQwMDAwMH0=",
            peers = listOf(
                Peer("peer-1", "Rider Alpha (Lead)", -55, 0L, true, AudioProfile.MEDIUM),
                Peer("peer-2", "Rider Bravo", -68, 0L, true, AudioProfile.MEDIUM)
            )
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            channelManager.channelState.collect { state ->
                _uiState.update { it.copy(channelState = state) }
            }
        }
        viewModelScope.launch {
            proximityRadar.radarFlow.collect { snapshot ->
                _uiState.update { it.copy(radarSnapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            convoyGpsTracker.convoyState.collect { snapshot ->
                _uiState.update { it.copy(convoySnapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            meshTextManager.messages.collect { list ->
                _uiState.update { it.copy(messages = list) }
            }
        }
        viewModelScope.launch {
            meshTextManager.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadTextCount = count) }
            }
        }
        viewModelScope.launch {
            priorityManager.isEmergencyActive.collect { active ->
                _uiState.update { it.copy(isEmergencyActive = active) }
            }
        }
        viewModelScope.launch {
            priorityManager.activePriority.collect { priority ->
                _uiState.update { it.copy(activePriorityLevel = priority) }
            }
        }
        viewModelScope.launch {
            blackboxRecorder.bufferedFrameCount.collect { count ->
                _uiState.update { it.copy(blackboxBufferedCount = count) }
            }
        }
        viewModelScope.launch {
            bitrateController.currentProfile.collect { profile ->
                _uiState.update { it.copy(activeProfile = profile) }
            }
        }
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.StartTransmitting -> {
                _uiState.update { it.copy(isTransmitting = true) }
            }

            HomeUiAction.StopTransmitting -> {
                _uiState.update { it.copy(isTransmitting = false) }
            }

            is HomeUiAction.SelectAudioProfile -> {
                viewModelScope.launch {
                    bitrateController.setManualOverride(action.profile)
                }
            }

            HomeUiAction.LeaveGroup -> {
                _uiState.update { it.copy(isGroupActive = false, peers = emptyList(), inviteCode = "") }
            }

            is HomeUiAction.ToggleShareDialog -> {
                _uiState.update { it.copy(showShareDialog = action.show) }
            }

            is HomeUiAction.ToggleJoinDialog -> {
                _uiState.update { it.copy(showJoinDialog = action.show) }
            }

            is HomeUiAction.JoinGroup -> {
                _uiState.update {
                    it.copy(
                        groupName = "Joined Mesh Group",
                        inviteCode = action.inviteCode,
                        isGroupActive = true,
                        showJoinDialog = false
                    )
                }
            }

            HomeUiAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }

            // v2.0 Cockpit Subsystems Actions
            is HomeUiAction.SelectChannel -> {
                viewModelScope.launch {
                    channelManager.selectActiveChannel(action.channelId)
                }
            }

            is HomeUiAction.ToggleDualWatch -> {
                viewModelScope.launch {
                    val currentMonitored = _uiState.value.channelState.monitoredChannelIds
                    val updated = if (currentMonitored.contains(action.channelId)) {
                        currentMonitored - action.channelId
                    } else {
                        currentMonitored + action.channelId
                    }
                    channelManager.setMonitoredChannels(updated)
                }
            }

            HomeUiAction.ToggleScanMode -> {
                viewModelScope.launch {
                    channelManager.setScanMode(!_uiState.value.channelState.isScanMode)
                }
            }

            is HomeUiAction.SendCannedHazard -> {
                viewModelScope.launch {
                    meshTextManager.sendCannedMessage(action.code)
                }
            }

            is HomeUiAction.SendCustomText -> {
                viewModelScope.launch {
                    meshTextManager.sendCustomText(action.content)
                }
            }

            HomeUiAction.MarkMessagesAsRead -> {
                viewModelScope.launch {
                    meshTextManager.markAllAsRead()
                }
            }

            HomeUiAction.TriggerSos -> {
                viewModelScope.launch {
                    blackboxRecorder.triggerManualSos(
                        peerId = "local-user",
                        peerName = "Rider",
                        location = null,
                        note = "SOS Button Pressed"
                    )
                    val sosFrame = priorityManager.createEmergencyBroadcast(
                        senderId = "local-user",
                        audioData = ByteArray(160),
                        alertType = AlertType.CRASH_DETECTED,
                        sequence = 1L
                    )
                    priorityManager.shouldPlayFrame(sosFrame)
                }
            }

            HomeUiAction.ClearEmergency -> {
                viewModelScope.launch {
                    priorityManager.reset()
                }
            }

            is HomeUiAction.ToggleMessagesDialog -> {
                if (action.show) {
                    viewModelScope.launch {
                        meshTextManager.markAllAsRead()
                    }
                }
                _uiState.update { it.copy(showMessagesDialog = action.show) }
            }
        }
    }
}
