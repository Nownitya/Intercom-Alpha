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
import org.nowni.intercom_alpha.map.GeoPoint
import org.nowni.intercom_alpha.map.MeshFileTransferEngine
import org.nowni.intercom_alpha.map.MeshFileTransferManager
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapManager
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
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
    val trailMapManager: TrailMapManager = TrailMapManager(),
    val fileTransferManager: MeshFileTransferManager = MeshFileTransferManager(),
    val geofenceEngine: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine = org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine(),
    val batteryGovernor: org.nowni.intercom_alpha.power.BatteryGovernor = org.nowni.intercom_alpha.power.BatteryGovernor("android-local"),
    val crashDetector: org.nowni.intercom_alpha.safety.CrashDetector = org.nowni.intercom_alpha.safety.CrashDetector(),
    val voiceCommandRecognizer: org.nowni.intercom_alpha.audio.VoiceCommandRecognizer = org.nowni.intercom_alpha.audio.VoiceCommandRecognizer(),
    val directedRouter: org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter = org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter("android-local"),
    val interGroupGateway: org.nowni.intercom_alpha.mesh.federation.InterGroupGateway = org.nowni.intercom_alpha.mesh.federation.InterGroupGateway(
        localGroupId = "squad-alpha",
        localGroupName = "Rider Club Alpha",
        localPeerId = "android-local",
        isLocalLeader = true
    ),
    val qosScheduler: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler = org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler(),
    val failoverArbiter: org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter = org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter(),
    val linkQualityScorer: org.nowni.intercom_alpha.mesh.MeshLinkQualityScorer = org.nowni.intercom_alpha.mesh.MeshLinkQualityScorer(),
    val bitrateEngine: org.nowni.intercom_alpha.audio.AdaptiveBitrateEngine = org.nowni.intercom_alpha.audio.AdaptiveBitrateEngine(),
    val fecEngine: org.nowni.intercom_alpha.audio.MeshPacketFecEngine = org.nowni.intercom_alpha.audio.MeshPacketFecEngine(),
    val lossConcealment: org.nowni.intercom_alpha.audio.AudioLossConcealment = org.nowni.intercom_alpha.audio.AudioLossConcealment(),
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
        hardwareBridge.attachSubsystems(
            channelManager,
            blackboxRecorder,
            helmetAccessoryManager,
            trailMapManager = trailMapManager,
            fileTransferManager = fileTransferManager,
            geofenceEngine = geofenceEngine,
            batteryGovernor = batteryGovernor,
            crashDetector = crashDetector,
            voiceCommandRecognizer = voiceCommandRecognizer,
            directedRouter = directedRouter,
            interGroupGateway = interGroupGateway,
            qosScheduler = qosScheduler,
            failoverArbiter = failoverArbiter
        )

        viewModelScope.launch {
            failoverArbiter.metrics.collect { snapshot ->
                _uiState.update { it.copy(failoverSnapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.failoverSnapshot.collect { snapshot ->
                _uiState.update { it.copy(failoverSnapshot = snapshot) }
            }
        }

        viewModelScope.launch {
            linkQualityScorer.clusterHealth.collect { health ->
                _uiState.update { it.copy(linkQualityHealth = health) }
                bitrateEngine.evaluateClusterHealth(health, currentTimeMillis())
            }
        }

        viewModelScope.launch {
            bitrateEngine.telemetry.collect { telemetry ->
                _uiState.update { it.copy(bitrateTelemetry = telemetry) }
                hardwareBridge.updateAdaptiveBitrateSnapshot(telemetry)
            }
        }

        viewModelScope.launch {
            hardwareBridge.linkQualitySnapshot.collect { health ->
                _uiState.update { it.copy(linkQualityHealth = health) }
            }
        }

        viewModelScope.launch {
            hardwareBridge.adaptiveBitrateSnapshot.collect { telemetry ->
                _uiState.update { it.copy(bitrateTelemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            fecEngine.telemetry.collect { telemetry ->
                _uiState.update { it.copy(fecTelemetry = telemetry) }
                hardwareBridge.updateFecTelemetry(telemetry)
            }
        }

        viewModelScope.launch {
            lossConcealment.telemetry.collect { telemetry ->
                _uiState.update { it.copy(plcTelemetry = telemetry) }
                hardwareBridge.updatePlcTelemetry(telemetry)
            }
        }

        viewModelScope.launch {
            hardwareBridge.fecTelemetrySnapshot.collect { telemetry ->
                _uiState.update { it.copy(fecTelemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            hardwareBridge.plcTelemetrySnapshot.collect { telemetry ->
                _uiState.update { it.copy(plcTelemetry = telemetry) }
            }
        }

        viewModelScope.launch {
            hardwareBridge.federationSnapshot.collect { snapshot ->
                _uiState.update { it.copy(federationSnapshot = snapshot) }
            }
        }

        viewModelScope.launch {
            geofenceEngine.geofenceState.collect { snapshot ->
                _uiState.update { it.copy(geofenceSnapshot = snapshot) }
            }
        }

        viewModelScope.launch {
            batteryGovernor.governorState.collect { state ->
                _uiState.update { it.copy(powerGovernorState = state) }
            }
        }

        // 2. Synchronize shared logic subsystem flows
        viewModelScope.launch {
            trailMapManager.snapshot.collect { snapshot ->
                _uiState.update { it.copy(trailMapSnapshot = snapshot) }
            }
        }
        viewModelScope.launch {
            fileTransferManager.activeTransfers.collect { transfers ->
                _uiState.update { it.copy(activeTransfers = transfers) }
            }
        }
        viewModelScope.launch {
            fileTransferManager.storedTrails.collect { trails ->
                _uiState.update { it.copy(storedTrails = trails) }
            }
        }
        viewModelScope.launch {
            fileTransferManager.completedTrailReceived.collect { (trail, waypoints) ->
                trailMapManager.loadTrail(trail)
                for (wpt in waypoints) {
                    trailMapManager.addWaypoint(wpt)
                }
            }
        }
        fileTransferManager.onSendChunk = { chunk, _ ->
            val bytes = MeshFileTransferEngine.serializeChunk(chunk)
            hardwareBridge.sendControlPacket(MeshPacket.Control(senderId = "local-rider", type = ControlType.FILE_CHUNK, payload = bytes))
        }
        fileTransferManager.onSendAck = { ack, _ ->
            val bytes = MeshFileTransferEngine.serializeAck(ack)
            hardwareBridge.sendControlPacket(MeshPacket.Control(senderId = "local-rider", type = ControlType.FILE_ACK, payload = bytes))
        }
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
                batteryGovernor.updateEmergencyStatus(active)
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
                trailMapManager.updatePeerPosition(
                    peerId = frame.peerId,
                    latitude = frame.coordinate.latitude,
                    longitude = frame.coordinate.longitude,
                    altitudeMeters = frame.coordinate.altitudeMeters
                )
                geofenceEngine.handleRemoteTelemetry(frame)
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

        // 7. Ingest live local GPS fixes into convoy tracker, trail map, geofence & battery governor
        viewModelScope.launch {
            hardwareBridge.localGpsCoordinate.collect { coord ->
                if (coord != null) {
                    convoyGpsTracker.updateLocalLocation(coord)
                    trailMapManager.updateLocalPosition(
                        latitude = coord.latitude,
                        longitude = coord.longitude,
                        altitudeMeters = coord.altitudeMeters,
                        headingDegrees = coord.headingDegrees,
                        timestampMs = coord.timestampMs
                    )
                    geofenceEngine.updateLocalPosition(
                        latitude = coord.latitude,
                        longitude = coord.longitude,
                        altitudeMeters = coord.altitudeMeters,
                        speedKmh = coord.speedKmh,
                        headingDegrees = coord.headingDegrees,
                        timestampMs = coord.timestampMs
                    )
                    batteryGovernor.updateMotion(
                        speedKmh = coord.speedKmh.toDouble(),
                        still = coord.speedKmh <= 2.0f,
                        timestampMs = coord.timestampMs
                    )
                    crashDetector.processSpeedUpdate(coord.speedKmh.toDouble())
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

        // 9. Synchronize Crash Detection state & Countdown HUD (ICA-2103)
        viewModelScope.launch {
            crashDetector.countdownSnapshot.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        crashSnapshot = snapshot,
                        showCrashCountdownDialog = snapshot.state == org.nowni.intercom_alpha.safety.CrashState.COUNTDOWN_ACTIVE
                    )
                }
            }
        }

        // 10. Synchronize Voice Command Recognizer & Dispatch Actions (ICA-2103)
        viewModelScope.launch {
            voiceCommandRecognizer.latestDetection.collect { detection ->
                _uiState.update { it.copy(latestVoiceCommand = detection) }
                if (detection != null) {
                    handleVoiceCommand(detection.command)
                }
            }
        }

        // 11. Synchronize Directed Mesh Routing Snapshot (ICA-2203)
        viewModelScope.launch {
            hardwareBridge.routingSnapshot.collect { snapshot ->
                _uiState.update { it.copy(routingSnapshot = snapshot) }
            }
        }

        // 12. Synchronize Mesh Airtime QoS Traffic Snapshot (ICA-2403)
        viewModelScope.launch {
            hardwareBridge.qosSnapshot.collect { snapshot ->
                _uiState.update { it.copy(qosSnapshot = snapshot) }
            }
        }

        // 13. Synchronize FEC, PLC, and BLE MTU Snapshots (ICA-3403 / ICA-3503)
        viewModelScope.launch {
            hardwareBridge.fecTelemetrySnapshot.collect { snapshot ->
                _uiState.update { it.copy(fecTelemetry = snapshot) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.plcTelemetrySnapshot.collect { snapshot ->
                _uiState.update { it.copy(plcTelemetry = snapshot) }
            }
        }
        viewModelScope.launch {
            hardwareBridge.bleMtuSnapshot.collect { snapshot ->
                _uiState.update { it.copy(bleMtuTelemetry = snapshot) }
            }
        }
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.StartTransmitting -> {
                hardwareBridge.setPttActive(true)
                viewModelScope.launch {
                    batteryGovernor.onPttPressed()
                }
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

            // Sprint 18 Trail Map Actions (ICA-1802)
            HomeUiAction.CycleRadarZoom -> {
                viewModelScope.launch {
                    trailMapManager.cycleRangeZoom()
                }
            }

            is HomeUiAction.SetRadarZoom -> {
                viewModelScope.launch {
                    trailMapManager.setRangeZoom(action.zoom)
                }
            }

            is HomeUiAction.ToggleTrailOverlay -> {
                viewModelScope.launch {
                    trailMapManager.setOverlayEnabled(action.enabled)
                }
            }

            is HomeUiAction.ToggleTrailSheet -> {
                _uiState.update { it.copy(showTrailSheet = action.show) }
            }

            HomeUiAction.LoadSampleTrail -> {
                viewModelScope.launch {
                    val coord = _uiState.value.convoySnapshot.localCoordinate
                    val center = if (coord != null) {
                        GeoPoint(coord.latitude, coord.longitude, coord.altitudeMeters)
                    } else {
                        GeoPoint(37.7749, -122.4194, 2500.0)
                    }
                    trailMapManager.loadSampleAlpineTrail(center)
                }
            }

            // Sprint 19 Trail File Sharing Actions (ICA-1902)
            is HomeUiAction.BeamActiveTrail -> {
                viewModelScope.launch {
                    val trail = _uiState.value.trailMapSnapshot.activeTrail
                        ?: _uiState.value.storedTrails.firstOrNull()
                    if (trail != null) {
                        val waypoints = _uiState.value.trailMapSnapshot.projectedWaypoints.map { it.waypoint }
                        fileTransferManager.startSendTrail(
                            trail = trail,
                            waypoints = waypoints,
                            format = action.type
                        )
                    }
                }
            }

            is HomeUiAction.CancelTransfer -> {
                viewModelScope.launch {
                    fileTransferManager.cancelTransfer(action.transferId)
                }
            }

            is HomeUiAction.ImportTrailText -> {
                viewModelScope.launch {
                    fileTransferManager.importTrailFromText(action.text, action.fileName, action.format)
                }
            }

            is HomeUiAction.SelectStoredTrail -> {
                viewModelScope.launch {
                    val trail = _uiState.value.storedTrails.find { it.id == action.trailId }
                    if (trail != null) {
                        trailMapManager.loadTrail(trail)
                    }
                }
            }

            is HomeUiAction.DeleteStoredTrail -> {
                viewModelScope.launch {
                    fileTransferManager.deleteStoredTrail(action.trailId)
                }
            }

            is HomeUiAction.ToggleTrailManagerSheet -> {
                _uiState.update { it.copy(showTrailManagerSheet = action.show) }
            }

            is HomeUiAction.SetPowerPolicyOverride -> {
                viewModelScope.launch {
                    batteryGovernor.setManualOverride(action.policy)
                }
            }

            is HomeUiAction.SetGeofenceLeadRider -> {
                viewModelScope.launch {
                    geofenceEngine.setLeadRider(action.leadPeerId)
                }
            }

            HomeUiAction.CancelCrashCountdown -> {
                viewModelScope.launch {
                    crashDetector.cancelCountdown()
                    hardwareBridge.cancelCrashCountdown()
                }
                _uiState.update { it.copy(showCrashCountdownDialog = false) }
            }

            HomeUiAction.TriggerManualCrashSos -> {
                viewModelScope.launch {
                    crashDetector.triggerManualCrashAlert()
                    hardwareBridge.triggerManualCrashSos()
                }
                onAction(HomeUiAction.TriggerSos)
                _uiState.update { it.copy(showCrashCountdownDialog = false) }
            }

            HomeUiAction.DismissVoiceCommandBadge -> {
                _uiState.update { it.copy(latestVoiceCommand = null) }
            }

            is HomeUiAction.ToggleRoutingDiagnosticsSheet -> {
                _uiState.update { it.copy(showRoutingDiagnosticsSheet = action.show) }
            }

            is HomeUiAction.InitiateRouteDiscovery -> {
                hardwareBridge.initiateRouteDiscovery(action.destinationId)
            }

            is HomeUiAction.ToggleFederationSheet -> {
                _uiState.update { it.copy(showFederationSheet = action.show) }
            }

            is HomeUiAction.SetFederationPolicy -> {
                interGroupGateway.setCrossGroupPolicy(action.groupId, action.policy)
                hardwareBridge.setFederationPolicy(action.groupId, action.policy)
                val snapshot = interGroupGateway.getSnapshot(currentTimeMillis())
                hardwareBridge.updateFederationSnapshot(snapshot)
                _uiState.update { it.copy(federationSnapshot = snapshot) }
            }

            HomeUiAction.CreateFederationInvite -> {
                val invite = interGroupGateway.createFederationInvite(currentTimeMs = currentTimeMillis())
                val code = org.nowni.intercom_alpha.mesh.federation.FederationSerializer.encodeInvite(invite)
                _uiState.update { it.copy(generatedFederationInvite = code) }
            }

            is HomeUiAction.AcceptFederationInvite -> {
                val invite = org.nowni.intercom_alpha.mesh.federation.FederationSerializer.decodeInvite(action.inviteCode)
                if (invite != null) {
                    val ok = interGroupGateway.acceptFederationInvite(invite, currentTimeMillis())
                    if (ok) {
                        val snapshot = interGroupGateway.getSnapshot(currentTimeMillis())
                        hardwareBridge.updateFederationSnapshot(snapshot)
                        _uiState.update { it.copy(federationSnapshot = snapshot) }
                    }
                }
            }

            HomeUiAction.DismissFederationInviteDialog -> {
                _uiState.update { it.copy(generatedFederationInvite = null) }
            }

            is HomeUiAction.ToggleQoSSheet -> {
                _uiState.update { it.copy(showQoSSheet = action.show) }
            }

            is HomeUiAction.ToggleFailoverSheet -> {
                _uiState.update { it.copy(showFailoverSheet = action.show) }
            }

            is HomeUiAction.SetFailoverModeOverride -> {
                viewModelScope.launch {
                    failoverArbiter.forceMode(action.mode, currentTimeMillis())
                }
            }

            is HomeUiAction.ToggleDiagnosticsSheet -> {
                _uiState.update { it.copy(showDiagnosticsSheet = action.show) }
            }

            is HomeUiAction.PinAudioProfile -> {
                viewModelScope.launch {
                    if (action.profile != null) {
                        bitrateEngine.pinProfile(action.profile, currentTimeMillis())
                        hardwareBridge.pinAudioBitrateProfile(action.profile)
                    } else {
                        bitrateEngine.releasePin(currentTimeMillis())
                        hardwareBridge.pinAudioBitrateProfile(null)
                    }
                }
            }

            is HomeUiAction.SetClusterPolicy -> {
                viewModelScope.launch {
                    bitrateEngine.setClusterPolicy(action.policy)
                    hardwareBridge.setClusterPolicy(action.policy)
                }
            }

            is HomeUiAction.SetFecRatio -> {
                viewModelScope.launch {
                    fecEngine.setFecRatio(action.ratio)
                    hardwareBridge.setFecRatio(action.ratio)
                }
            }

            is HomeUiAction.SetNegotiatedMtu -> {
                hardwareBridge.setNegotiatedMtu(action.mtu)
            }
        }
    }

    private fun handleVoiceCommand(command: org.nowni.intercom_alpha.audio.VoiceCommand) {
        when (command) {
            org.nowni.intercom_alpha.audio.VoiceCommand.SOS_TRIGGER -> {
                onAction(HomeUiAction.TriggerSos)
                viewModelScope.launch {
                    crashDetector.triggerManualCrashAlert()
                }
            }
            org.nowni.intercom_alpha.audio.VoiceCommand.NEXT_CHANNEL -> {
                val current = _uiState.value.channelState.activeChannelId
                val next = if (current >= 8) 1 else current + 1
                onAction(HomeUiAction.SelectChannel(next))
            }
            org.nowni.intercom_alpha.audio.VoiceCommand.PREV_CHANNEL -> {
                val current = _uiState.value.channelState.activeChannelId
                val prev = if (current <= 1) 8 else current - 1
                onAction(HomeUiAction.SelectChannel(prev))
            }
            org.nowni.intercom_alpha.audio.VoiceCommand.MUTE_TOGGLE -> {
                if (_uiState.value.isTransmitting) {
                    onAction(HomeUiAction.StopTransmitting)
                } else {
                    onAction(HomeUiAction.StartTransmitting)
                }
            }
            org.nowni.intercom_alpha.audio.VoiceCommand.CANCEL_ALERT -> {
                onAction(HomeUiAction.CancelCrashCountdown)
            }
            org.nowni.intercom_alpha.audio.VoiceCommand.BATTERY_STATUS,
            org.nowni.intercom_alpha.audio.VoiceCommand.STATUS_REPORT -> {
                // Handled via UI badge and tone feedback
            }
        }
    }
}
