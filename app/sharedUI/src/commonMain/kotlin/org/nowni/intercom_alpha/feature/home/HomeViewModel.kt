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
import org.nowni.intercom_alpha.bridge.ControlPayloadTag
import org.nowni.intercom_alpha.bridge.IntercomHardwareBridge
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.headset.HelmetAccessoryManager
import org.nowni.intercom_alpha.headset.RemotePttAction
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
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
    val bitrateController: AdaptiveBitrateController = AdaptiveBitrateController(),
    val helmetAccessoryManager: HelmetAccessoryManager = HelmetAccessoryManager(),
    val hardwareBridge: IntercomHardwareBridge = NoOpIntercomHardwareBridge()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            groupName = "Rider Club Alpha",
            inviteCode = "INTERCOM:v1:eyJncm91cElkIjoiZ3JwLTEwMSIsImdyb3VwTmFtZSI6IlJpZGVyIENsdWIgQWxwaGEiLCJsZWFkZXJJZCI6InBlZXItMSIsImV4cGlyZXNBdCI6NDkyMDA4ODQwMDAwMH0=",
            peers = emptyList()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 1. Attach shared logic subsystems to platform hardware bridge
        hardwareBridge.attachSubsystems(channelManager, blackboxRecorder, helmetAccessoryManager)

        // 2. Synchronize shared logic subsystem flows
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

        // 3. Observe live platform hardware states (PTT, BLE peers, and mic/speaker RMS levels)
        viewModelScope.launch {
            hardwareBridge.isTransmitting.collect { transmitting ->
                _uiState.update { it.copy(isTransmitting = transmitting) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.connectedPeers.collect { livePeers ->
                _uiState.update { it.copy(peers = livePeers) }
                for (peer in livePeers) {
                    proximityRadar.updatePeerRssi(peer.id, peer.rssi, displayName = peer.name)
                }
            }
        }
        viewModelScope.launch {
            hardwareBridge.inputLevel.collect { level ->
                _uiState.update { it.copy(inputLevel = level) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.outputLevel.collect { level ->
                _uiState.update { it.copy(outputLevel = level) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.helmetAccessoryState.collect { accessory ->
                _uiState.update { it.copy(accessoryState = accessory) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.isCloudBridgeConnected.collect { connected ->
                _uiState.update { it.copy(isCloudBridgeConnected = connected) }
            }
        }
        viewModelScope.launch {
            helmetAccessoryManager.remotePttEvents.collect { event ->
                when (event.action) {
                    RemotePttAction.PRESSED -> onAction(HomeUiAction.StartTransmitting)
                    RemotePttAction.RELEASED -> onAction(HomeUiAction.StopTransmitting)
                    RemotePttAction.CLICK -> {
                        if (_uiState.value.isTransmitting) onAction(HomeUiAction.StopTransmitting)
                        else onAction(HomeUiAction.StartTransmitting)
                    }
                    else -> {}
                }
            }
        }

        // 4. Pipe outgoing canned hazard text messages to hardware BLE mesh
        viewModelScope.launch {
            for (wireFrame in meshTextManager.outgoingFrames) {
                val encoded = MeshTextManager.encodeWireFrame(wireFrame)
                hardwareBridge.sendControlPayload(ControlPayloadTag.TAG_TEXT, encoded)
            }
        }

        // 5. Pipe outgoing GPS telemetry frames to hardware BLE mesh
        viewModelScope.launch {
            for (telemetryFrame in convoyGpsTracker.outgoingFrames) {
                val encoded = ConvoyGpsTracker.encodeTelemetryFrame(telemetryFrame)
                hardwareBridge.sendControlPayload(ControlPayloadTag.TAG_GPS, encoded)
            }
        }

        // 6. Ingest incoming over-the-air text, GPS, and emergency alerts
        viewModelScope.launch {
            hardwareBridge.incomingTextFrames.collect { frame ->
                meshTextManager.handleIncomingWireFrame(frame)
            }
        }
        viewModelScope.launch {
            hardwareBridge.incomingGpsFrames.collect { frame ->
                convoyGpsTracker.handleIncomingTelemetry(frame)
            }
        }
        viewModelScope.launch {
            hardwareBridge.incomingEmergencyAlerts.collect {
                val sosFrame = priorityManager.createEmergencyBroadcast(
                    senderId = "remote-sos",
                    audioData = ByteArray(0),
                    alertType = AlertType.CRASH_DETECTED,
                    sequence = 1L
                )
                priorityManager.shouldPlayFrame(sosFrame)
            }
        }

        // 7. Ingest live local GPS fixes into convoy tracker
        viewModelScope.launch {
            hardwareBridge.localGpsCoordinate.collect { coord ->
                if (coord != null) {
                    convoyGpsTracker.updateLocalLocation(coord)
                }
            }
        }

        // 8. Ingest incoming Repeater Beacons and Pongs from hardware bridge (ICA-1704)
        viewModelScope.launch {
            hardwareBridge.incomingRepeaterBeacons.collect { beacon ->
                _uiState.update { it.copy(latestRepeaterBeacon = beacon) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.incomingRepeaterPongs.collect { pong ->
                val now = currentTimeMillis()
                val latency = (now - pong.pingTimestampMs).coerceAtLeast(0L)
                _uiState.update {
                    it.copy(
                        repeaterPingLatencyMs = latency,
                        isPingingRepeater = false
                    )
                }
            }
        }
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.StartTransmitting -> {
                hardwareBridge.setPttActive(true)
            }

            HomeUiAction.StopTransmitting -> {
                hardwareBridge.setPttActive(false)
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
                    hardwareBridge.sendControlPayload(ControlPayloadTag.TAG_EMERGENCY, ByteArray(1) { 1 })
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

            HomeUiAction.ToggleVoxLock -> {
                val newLocked = !_uiState.value.isVoxLocked
                _uiState.update { it.copy(isVoxLocked = newLocked) }
                if (newLocked) {
                    onAction(HomeUiAction.StartTransmitting)
                } else {
                    onAction(HomeUiAction.StopTransmitting)
                }
            }

            HomeUiAction.TriggerRepeaterPing -> {
                _uiState.update { it.copy(isPingingRepeater = true) }
                val pingPacket = MeshRepeaterService.createDiagnosticPing(senderId = "local-rider")
                hardwareBridge.sendRepeaterPacket(pingPacket)
            }
        }
    }
}
