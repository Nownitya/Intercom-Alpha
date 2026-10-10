package org.nowni.intercom_alpha.bridge

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nowni.intercom_alpha.audio.*
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.channel.ChannelState
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.group.Group
import org.nowni.intercom_alpha.group.GroupConfig
import org.nowni.intercom_alpha.group.GroupManager
import org.nowni.intercom_alpha.group.GroupManagerImpl
import org.nowni.intercom_alpha.crypto.MeshKeyRatchet
import org.nowni.intercom_alpha.headset.HeadsetButtonEvent
import org.nowni.intercom_alpha.headset.HeadsetInfo
import org.nowni.intercom_alpha.headset.HeadsetManager
import org.nowni.intercom_alpha.headset.HeadsetManagerImpl
import org.nowni.intercom_alpha.headset.HelmetAccessoryManager
import org.nowni.intercom_alpha.headset.HelmetAccessoryState
import org.nowni.intercom_alpha.headset.RemotePttAction
import org.nowni.intercom_alpha.map.FileTransferAck
import org.nowni.intercom_alpha.map.FileTransferChunk
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.FileTransferType
import org.nowni.intercom_alpha.map.GeoPoint
import org.nowni.intercom_alpha.map.MeshFileTransferEngine
import org.nowni.intercom_alpha.map.MeshFileTransferManager
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapManager
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.TrailPolyline
import org.nowni.intercom_alpha.map.TrailWaypoint
import org.nowni.intercom_alpha.map.WaypointType
import org.nowni.intercom_alpha.mesh.*
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.power.BackgroundKeepAlive
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.CannedMessageCode
import org.nowni.intercom_alpha.text.MeshTextManager
import org.nowni.intercom_alpha.text.MeshTextMessage

@OptIn(ExperimentalForeignApi::class)
class IntercomBridge(
    val localPeerId: String = "ios-local",
    val localPeerName: String = "iOS Rider"
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val audioEngine: AudioEngine = AudioEngineImpl(scope)
    val meshTransport: MeshTransport = MeshTransportImpl(scope)
    val headsetManager: HeadsetManager = HeadsetManagerImpl(scope)
    val groupManager: GroupManager = GroupManagerImpl(meshTransport)
    val keepAlive: BackgroundKeepAlive = BackgroundKeepAlive()

    // v2.0 & Sprint 14 Subsystems
    val channelManager: ChannelManager = ChannelManager()
    val proximityRadar: ProximityRadar = ProximityRadar()
    val convoyGpsTracker: ConvoyGpsTracker = ConvoyGpsTracker(localPeerId, localPeerName)
    val meshTextManager: MeshTextManager = MeshTextManager(localPeerId, localPeerName)
    val priorityManager: PriorityBroadcastManager = PriorityBroadcastManager()
    val blackboxRecorder: EmergencyBlackboxRecorder = EmergencyBlackboxRecorder()
    val bitrateController: AdaptiveBitrateController = AdaptiveBitrateController()
    val helmetAccessoryManager: HelmetAccessoryManager = HelmetAccessoryManager()
    val keyRatchet: MeshKeyRatchet = MeshKeyRatchet(
        localNodeId = localPeerId,
        masterSecret = "intercom-mesh-master-salt-default".encodeToByteArray()
    )
    val repeaterService: MeshRepeaterService = MeshRepeaterService(
        repeaterId = "$localPeerId-repeater",
        repeaterName = "$localPeerName Basecamp",
        packetOutbox = { packet ->
            scope.launch(Dispatchers.Default) {
                if (packet is MeshPacket.Control) {
                    meshTransport.sendControl(packet.type, packet.payload)
                }
            }
        }
    )
    val trailMapManager: TrailMapManager = TrailMapManager()
    val fileTransferManager: MeshFileTransferManager = MeshFileTransferManager()
    val geofenceEngine: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine = org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine()
    val batteryGovernor: org.nowni.intercom_alpha.power.BatteryGovernor = org.nowni.intercom_alpha.power.BatteryGovernor(nodeId = localPeerId)
    val crashDetector: org.nowni.intercom_alpha.safety.CrashDetector = org.nowni.intercom_alpha.safety.CrashDetector()
    val voiceCommandRecognizer: org.nowni.intercom_alpha.audio.VoiceCommandRecognizer = org.nowni.intercom_alpha.audio.VoiceCommandRecognizer()
    val directedRouter: org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter = org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter(localPeerId)
    val interGroupGateway: org.nowni.intercom_alpha.mesh.federation.InterGroupGateway = org.nowni.intercom_alpha.mesh.federation.InterGroupGateway(
        localGroupId = "squad-ios",
        localGroupName = "$localPeerName Squad",
        localPeerId = localPeerId,
        isLocalLeader = true
    )

    private val _isRepeaterModeActive = MutableStateFlow(false)
    val isRepeaterModeActive: StateFlow<Boolean> = _isRepeaterModeActive.asStateFlow()

    private val _lastPingLatencyMs = MutableStateFlow<Long?>(null)
    val lastPingLatencyMs: StateFlow<Long?> = _lastPingLatencyMs.asStateFlow()

    private val _latestBeacon = MutableStateFlow<RepeaterBeaconPayload?>(null)
    val latestBeacon: StateFlow<RepeaterBeaconPayload?> = _latestBeacon.asStateFlow()

    private var audioRxJob: Job? = null
    private var audioTxJob: Job? = null
    private var headsetEventsJob: Job? = null
    private var bitrateSyncJob: Job? = null
    private var repeaterControlJob: Job? = null

    private var isPttActive: Boolean = false
    private var currentAudioProfile: AudioProfile = AudioProfile.default

    fun start(groupName: String = "Intercom Group") {
        scope.launch {
            // 1. Start audio and headset hardware managers
            headsetManager.start()
            audioEngine.start(AudioEngineConfig())

            // 2. Initialize or restore Group
            val group = groupManager.createGroup(groupName, GroupConfig(audioProfile = currentAudioProfile))

            // 3. Start BLE Mesh transport
            meshTransport.start(
                groupId = group.id,
                peerName = localPeerName,
                config = MeshConfig(
                    groupId = group.id,
                    peerName = localPeerName,
                    audioProfile = currentAudioProfile
                )
            )

            // 4 & 5. Pipe incoming and outgoing Mesh audio pipelines
            startAudioStreamingPipelines()

            // 6. Intercept physical headset buttons and remote BLE accessory PTT
            headsetEventsJob?.cancel()
            headsetEventsJob = scope.launch(Dispatchers.Main) {
                launch {
                    for (event in headsetManager.buttonEvents) {
                        when (event) {
                            HeadsetButtonEvent.PTT_PRESS -> setPttActive(true)
                            HeadsetButtonEvent.PTT_RELEASE -> setPttActive(false)
                            else -> {}
                        }
                    }
                }
                launch {
                    helmetAccessoryManager.remotePttEvents.collect { event ->
                        when (event.action) {
                            RemotePttAction.PRESSED -> setPttActive(true)
                            RemotePttAction.RELEASED -> setPttActive(false)
                            RemotePttAction.CLICK -> {
                                setPttActive(true)
                                delay(300)
                                setPttActive(false)
                            }
                            else -> {}
                        }
                    }
                }
            }

            // 7. Synchronize dynamic bitrate profile
            bitrateSyncJob?.cancel()
            bitrateSyncJob = scope.launch(Dispatchers.Main) {
                bitrateController.currentProfile.collect { profile ->
                    currentAudioProfile = profile
                }
            }

            // 8. Monitor incoming Repeater control packets (Diagnostic Pongs and Basecamp Beacons)
            repeaterControlJob?.cancel()
            repeaterControlJob = scope.launch(Dispatchers.Default) {
                for (packet in meshTransport.incomingControl) {
                    when (packet.type) {
                        ControlType.REPEATER_PONG -> {
                            val pong = MeshRepeaterService.decodePongPayload(packet)
                            if (pong != null && pong.pingSenderId == localPeerId) {
                                val latency = (currentTimeMillis() - pong.pingTimestampMs).coerceAtLeast(0L)
                                _lastPingLatencyMs.value = latency
                            }
                        }
                        ControlType.REPEATER_BEACON -> {
                            val beacon = MeshRepeaterService.decodeBeaconPayload(packet)
                            if (beacon != null) {
                                _latestBeacon.value = beacon
                            }
                        }
                        ControlType.FILE_CHUNK -> {
                            try {
                                val chunk = MeshFileTransferEngine.deserializeChunk(packet.payload)
                                fileTransferManager.handleIncomingChunk(chunk, packet.senderId)
                            } catch (_: Exception) {}
                        }
                        ControlType.FILE_ACK -> {
                            try {
                                val ack = MeshFileTransferEngine.deserializeAck(packet.payload)
                                fileTransferManager.handleIncomingAck(ack)
                            } catch (_: Exception) {}
                        }
                        else -> {}
                    }
                }
            }

            // 9. Connect Mesh File Transfer Manager outbound hooks
            fileTransferManager.onSendChunk = { chunk, _ ->
                val bytes = MeshFileTransferEngine.serializeChunk(chunk)
                meshTransport.sendControl(ControlType.FILE_CHUNK, bytes)
            }
            fileTransferManager.onSendAck = { ack, _ ->
                val bytes = MeshFileTransferEngine.serializeAck(ack)
                meshTransport.sendControl(ControlType.FILE_ACK, bytes)
            }
            scope.launch {
                fileTransferManager.completedTrailReceived.collect { (trail, _) ->
                    trailMapManager.loadTrail(trail)
                }
            }

            // 10. Forward autonomous crash SOS alert packets over BLE Mesh
            scope.launch {
                crashDetector.alertPackets.collect { alert ->
                    val payload = org.nowni.intercom_alpha.safety.CrashDetector.encodeAlertPayload(alert)
                    meshTransport.sendControl(ControlType.CRASH_ALERT, payload)
                }
            }
        }
    }

    private fun startAudioStreamingPipelines() {
        audioRxJob?.cancel()
        audioRxJob = scope.launch(Dispatchers.Default) {
            for (packet in meshTransport.incomingAudio) {
                keepAlive.withKeepAlive("IntercomAudioRxBurst") {
                    val pcmShorts = audioEngine.decode(packet.data, packet.profile)
                    audioEngine.playAudio(pcmShorts)

                    // Record to rolling blackbox buffer
                    blackboxRecorder.recordFrame(
                        BlackboxAudioFrame(
                            timestampMs = currentTimeMillis(),
                            sequence = packet.sequence,
                            speakerId = packet.senderId,
                            speakerName = "",
                            priority = PriorityLevel.NORMAL,
                            adpcmData = packet.data
                        )
                    )
                }
            }
        }

        audioTxJob?.cancel()
        audioTxJob = scope.launch(Dispatchers.Default) {
            for (pcmShorts in audioEngine.recordedAudio) {
                // Pipe local microphone PCM into offline keyword recognizer
                voiceCommandRecognizer.processPcmSamples(pcmShorts, currentTimeMillis())

                if (isPttActive) {
                    keepAlive.withKeepAlive("IntercomAudioTxBurst") {
                        val encoded = audioEngine.encode(pcmShorts, currentAudioProfile)
                        meshTransport.sendAudio(encoded, currentAudioProfile)
                    }
                }
            }
        }
    }

    fun stop() {
        audioRxJob?.cancel()
        audioTxJob?.cancel()
        headsetEventsJob?.cancel()
        bitrateSyncJob?.cancel()
        repeaterControlJob?.cancel()
        repeaterService.stop()
        _isRepeaterModeActive.value = false
        keepAlive.endAssertion()

        scope.launch {
            audioEngine.stop()
            meshTransport.stop()
            headsetManager.stop()
            proximityRadar.clear()
        }
    }

    fun setPttActive(active: Boolean) {
        isPttActive = active
        if (active) {
            scope.launch {
                batteryGovernor.onPttPressed()
            }
        }
    }

    fun setVoxEnabled(enabled: Boolean, thresholdDb: Float = -40f) {
        audioEngine.setVoxEnabled(enabled)
        audioEngine.setVoxThreshold(thresholdDb)
    }

    fun setOutputVolume(volume: Float) {
        audioEngine.setOutputVolume(volume)
    }

    // --- Swift-Friendly Core Observers ---

    fun observeInputLevel(onLevel: (Float) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (level in audioEngine.inputLevel) {
                onLevel(level)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeOutputLevel(onLevel: (Float) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (level in audioEngine.outputLevel) {
                onLevel(level)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeIsRecording(onRecording: (Boolean) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (rec in audioEngine.isRecording) {
                onRecording(rec)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observePeers(onPeers: (List<Peer>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (peers in meshTransport.peers) {
                onPeers(peers)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeConnectionState(onState: (ConnectionState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (state in meshTransport.connectionState) {
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeActiveHeadset(onHeadset: (HeadsetInfo?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (headset in headsetManager.activeHeadset) {
                onHeadset(headset)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeCurrentGroup(onGroup: (Group?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (grp in groupManager.currentGroup) {
                onGroup(grp)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Channel Sub-Group Observers & Commands ---

    fun observeChannelState(onState: (ChannelState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            channelManager.channelState.collect { state ->
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun selectChannel(channelId: Int) {
        scope.launch(Dispatchers.Main) {
            channelManager.selectActiveChannel(channelId)
        }
    }

    fun setDualWatch(enabled: Boolean, secondaryChannelId: Int = 1) {
        scope.launch(Dispatchers.Main) {
            channelManager.setMonitoredChannels(if (enabled) setOf(secondaryChannelId) else emptySet())
        }
    }

    fun setScanMode(enabled: Boolean) {
        scope.launch(Dispatchers.Main) {
            channelManager.setScanMode(enabled)
        }
    }

    fun toggleChannelMute(channelId: Int) {
        scope.launch(Dispatchers.Main) {
            channelManager.toggleChannelMute(channelId)
        }
    }

    // --- Proximity Radar & Telemetry Observers & Commands ---

    fun observeRadarSnapshot(onSnapshot: (RadarSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            proximityRadar.radarFlow.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeConvoyTelemetry(onSnapshot: (ConvoyTelemetrySnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            convoyGpsTracker.convoyState.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun updateGpsLocation(
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = 0.0,
        speedKmh: Float = 0f,
        headingDegrees: Float = 0f
    ) {
        scope.launch(Dispatchers.Main) {
            convoyGpsTracker.updateLocalLocation(
                GpsCoordinate(
                    latitude = latitude,
                    longitude = longitude,
                    altitudeMeters = altitudeMeters,
                    speedKmh = speedKmh,
                    headingDegrees = headingDegrees,
                    accuracyMeters = 5f,
                    timestampMs = currentTimeMillis()
                )
            )
        }
    }

    fun updatePeerRssi(peerId: String, peerName: String, rssi: Int) {
        scope.launch(Dispatchers.Main) {
            proximityRadar.updatePeerRssi(
                peerId = peerId,
                rssi = rssi,
                displayName = peerName
            )
        }
    }

    // --- Mesh Text & Canned Hazard Observers & Commands ---

    fun observeMessages(onMessages: (List<MeshTextMessage>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            meshTextManager.messages.collect { msgs ->
                onMessages(msgs)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeUnreadCount(onCount: (Int) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            meshTextManager.unreadCount.collect { count ->
                onCount(count)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun sendCannedMessage(cannedCode: CannedMessageCode, recipientId: String? = null) {
        scope.launch(Dispatchers.Main) {
            meshTextManager.sendCannedMessage(cannedCode, recipientId)
        }
    }

    fun sendCustomText(content: String, recipientId: String? = null) {
        scope.launch(Dispatchers.Main) {
            meshTextManager.sendCustomText(content, recipientId)
        }
    }

    fun markMessageAsRead(messageId: String) {
        scope.launch(Dispatchers.Main) {
            meshTextManager.markAsRead(messageId)
        }
    }

    fun markAllMessagesAsRead() {
        scope.launch(Dispatchers.Main) {
            meshTextManager.markAllAsRead()
        }
    }

    fun clearMessageHistory() {
        scope.launch(Dispatchers.Main) {
            meshTextManager.clearHistory()
        }
    }

    // --- Priority & Emergency Audio Observers & Commands ---

    fun observePriorityLevel(onPriority: (PriorityLevel) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            priorityManager.activePriority.collect { priority ->
                onPriority(priority)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeIsEmergencyActive(onEmergency: (Boolean) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            priorityManager.isEmergencyActive.collect { active ->
                onEmergency(active)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Emergency Blackbox Observers & Commands ---

    fun observeBlackboxReports(onReports: (List<EncryptedIncidentReport>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            blackboxRecorder.encryptedReports.collect { reports ->
                onReports(reports)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeBlackboxBufferedCount(onCount: (Int) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            blackboxRecorder.bufferedFrameCount.collect { count ->
                onCount(count)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun triggerManualSos(note: String = "Manual SOS initiated") {
        scope.launch(Dispatchers.Main) {
            blackboxRecorder.triggerManualSos(
                peerId = localPeerId,
                peerName = localPeerName,
                note = note
            )
        }
    }

    fun clearBlackboxCache() {
        scope.launch(Dispatchers.Main) {
            blackboxRecorder.clearIncidentCache()
        }
    }

    // --- Dynamic Adaptive Bitrate Observers & Commands ---

    fun observeCurrentAudioProfile(onProfile: (AudioProfile) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            bitrateController.currentProfile.collect { profile ->
                onProfile(profile)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeAdaptationState(onState: (BitrateAdaptationState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            bitrateController.adaptationState.collect { state ->
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun pinAudioProfile(profile: AudioProfile) {
        scope.launch(Dispatchers.Main) {
            bitrateController.setManualOverride(profile)
        }
    }

    fun resetAudioProfileToAuto() {
        scope.launch(Dispatchers.Main) {
            bitrateController.setAutoMode()
        }
    }

    // --- Group & QR Management ---

    fun createGroup(name: String, onComplete: (Group) -> Unit) {
        scope.launch(Dispatchers.Main) {
            val grp = groupManager.createGroup(name, GroupConfig(audioProfile = currentAudioProfile))
            meshTransport.updateConfig(
                MeshConfig(
                    groupId = grp.id,
                    peerName = localPeerName,
                    audioProfile = currentAudioProfile
                )
            )
            onComplete(grp)
        }
    }

    fun joinGroup(inviteCode: String, onComplete: (Group?, String?) -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                val grp = groupManager.joinGroup(inviteCode)
                meshTransport.updateConfig(
                    MeshConfig(
                        groupId = grp.id,
                        peerName = localPeerName,
                        audioProfile = currentAudioProfile
                    )
                )
                onComplete(grp, null)
            } catch (e: Exception) {
                onComplete(null, e.message ?: "Failed to join group")
            }
        }
    }

    fun generateInviteCode(): String = groupManager.generateInviteCode()

    fun leaveGroup() {
        scope.launch(Dispatchers.Main) {
            groupManager.leaveGroup()
        }
    }

    // --- Background Keep-Alive Assertions ---

    fun isKeepAliveActive(): Boolean = keepAlive.isActive

    fun beginKeepAlive(taskName: String = "IntercomAlpha.ManualKeepAlive"): Boolean =
        keepAlive.beginAssertion(taskName)

    fun endKeepAlive() = keepAlive.endAssertion()

    fun observeKeepAliveState(onStateChange: (Boolean) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            keepAlive.isActiveFlow.collect { active ->
                onStateChange(active)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Helmet Accessory Observers & PFS Ratchet Status ---

    fun observeHelmetAccessory(onState: (HelmetAccessoryState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            helmetAccessoryManager.accessoryState.collect { state ->
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun isPfsRatchetActive(): Boolean = !keyRatchet.isWiped

    // --- Basecamp Repeater & Stationary Cockpit Mode ---

    fun setRepeaterModeActive(active: Boolean) {
        _isRepeaterModeActive.value = active
        if (active) {
            repeaterService.start()
            // Release audio recording and playout pipelines to save battery and dedicate RF to relay
            audioRxJob?.cancel()
            audioRxJob = null
            audioTxJob?.cancel()
            audioTxJob = null
            scope.launch {
                audioEngine.stop()
            }
        } else {
            repeaterService.stop()
            scope.launch {
                audioEngine.start(AudioEngineConfig())
                startAudioStreamingPipelines()
            }
        }
    }

    fun setRepeaterDutyCycle(mode: RepeaterDutyCycleMode) {
        repeaterService.setDutyCycle(mode)
    }

    fun triggerDiagnosticPing() {
        val ping = repeaterService.createDiagnosticPing(senderId = localPeerId)
        scope.launch(Dispatchers.Default) {
            meshTransport.sendControl(ping.type, ping.payload)
        }
    }

    fun emitRepeaterBeacon() {
        repeaterService.emitBeaconPacket()
    }

    // --- Swift-Friendly Repeater Observers ---

    fun observeRepeaterTelemetry(onTelemetry: (RepeaterTelemetry) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            repeaterService.telemetry.collect { tele ->
                onTelemetry(tele)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeRepeaterModeActive(onActive: (Boolean) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            _isRepeaterModeActive.collect { active ->
                onActive(active)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeLastPingLatency(onLatency: (Long?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            _lastPingLatencyMs.collect { latency ->
                onLatency(latency)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeLatestBeacon(onBeacon: (RepeaterBeaconPayload?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            _latestBeacon.collect { beacon ->
                onBeacon(beacon)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Swift-Friendly Trail Map Observers & Controls (Sprint 18 / ICA-1802) ---

    fun observeTrailMapSnapshot(onSnapshot: (TrailMapSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            trailMapManager.snapshot.collect { snap ->
                onSnapshot(snap)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun cycleRadarZoom() {
        scope.launch {
            trailMapManager.cycleRangeZoom()
        }
    }

    fun setRadarZoom(zoom: RadarRangeZoom) {
        scope.launch {
            trailMapManager.setRangeZoom(zoom)
        }
    }

    fun setTrailOverlayEnabled(enabled: Boolean) {
        scope.launch {
            trailMapManager.setOverlayEnabled(enabled)
        }
    }

    fun loadSampleAlpineTrail(lat: Double = 37.7749, lon: Double = -122.4194, alt: Double = 2500.0) {
        scope.launch {
            trailMapManager.loadSampleAlpineTrail(GeoPoint(lat, lon, alt))
        }
    }

    fun updateTrailLocalGps(lat: Double, lon: Double, alt: Double, heading: Float) {
        scope.launch {
            trailMapManager.updateLocalPosition(lat, lon, alt, heading)
        }
    }

    // --- Swift-Friendly File Transfer Observers & Controls (Sprint 19 / ICA-1902) ---

    fun observeFileTransfers(onTransfers: (List<FileTransferProgress>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            fileTransferManager.activeTransfers.collect { transfers ->
                onTransfers(transfers)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeStoredTrails(onTrails: (List<TrailPolyline>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            fileTransferManager.storedTrails.collect { trails ->
                onTrails(trails)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun sendTrailOverMesh(
        trail: TrailPolyline,
        waypoints: List<TrailWaypoint> = emptyList(),
        format: FileTransferType = FileTransferType.GPX
    ) {
        scope.launch {
            fileTransferManager.startSendTrail(trail, waypoints, format)
        }
    }

    fun cancelFileTransfer(transferId: String) {
        scope.launch {
            fileTransferManager.cancelTransfer(transferId)
        }
    }

    fun importTrailFromText(text: String, fileName: String, format: FileTransferType = FileTransferType.GPX) {
        scope.launch {
            fileTransferManager.importTrailFromText(text, fileName, format)
        }
    }

    fun deleteStoredTrail(trailId: String) {
        scope.launch {
            fileTransferManager.deleteStoredTrail(trailId)
        }
    }

    fun loadTrail(trail: TrailPolyline) {
        scope.launch {
            trailMapManager.loadTrail(trail)
        }
    }

    // --- Swift-Friendly Geofence & Battery Governor Observers & Controls (Sprint 20 / ICA-2003) ---

    fun observeGeofenceSnapshot(onSnapshot: (org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            geofenceEngine.geofenceState.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeBatteryGovernorState(onState: (org.nowni.intercom_alpha.power.PowerGovernorState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            batteryGovernor.governorState.collect { state ->
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun updateBattery(percent: Int, isCharging: Boolean = false) {
        scope.launch {
            batteryGovernor.updateBattery(percent, isCharging)
        }
    }

    fun setPowerPolicyOverride(policy: org.nowni.intercom_alpha.power.PowerPolicy?) {
        scope.launch {
            batteryGovernor.setManualOverride(policy)
        }
    }

    fun setGeofenceLeadRider(leadPeerId: String?) {
        scope.launch {
            geofenceEngine.setLeadRider(leadPeerId)
        }
    }

    fun updateLocalGpsWithMotion(lat: Double, lon: Double, alt: Double, speedKmh: Float, heading: Float) {
        scope.launch {
            trailMapManager.updateLocalPosition(lat, lon, alt, heading)
            geofenceEngine.updateLocalPosition(latitude = lat, longitude = lon, altitudeMeters = alt, speedKmh = speedKmh, headingDegrees = heading)
            batteryGovernor.updateMotion(speedKmh.toDouble(), speedKmh <= 2.0f)
            crashDetector.processSpeedUpdate(speedKmh.toDouble())
        }
    }

    // --- Swift-Friendly Crash Detection & Voice Command Observers & Controls (Sprint 21 / ICA-2103) ---

    fun observeCrashSnapshot(onSnapshot: (org.nowni.intercom_alpha.safety.CrashCountdownSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            crashDetector.countdownSnapshot.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeVoiceCommands(onCommand: (org.nowni.intercom_alpha.audio.CommandDetection?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            voiceCommandRecognizer.latestDetection.collect { detection ->
                onCommand(detection)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun cancelCrashCountdown() {
        scope.launch(Dispatchers.Main) {
            crashDetector.cancelCountdown()
        }
    }

    fun triggerManualCrashSos() {
        scope.launch(Dispatchers.Main) {
            crashDetector.triggerManualCrashAlert()
        }
    }

    fun processImuReading(
        ax: Double, ay: Double, az: Double,
        gx: Double = 0.0, gy: Double = 0.0, gz: Double = 0.0,
        timestampMs: Long = currentTimeMillis()
    ) {
        scope.launch {
            val reading = org.nowni.intercom_alpha.safety.ImuReading(
                timestampMs = timestampMs,
                accelX = ax.toFloat(),
                accelY = ay.toFloat(),
                accelZ = az.toFloat(),
                gyroX = gx.toFloat(),
                gyroY = gy.toFloat(),
                gyroZ = gz.toFloat()
            )
            crashDetector.processImuReading(reading)
        }
    }

    fun processVoiceAudio(samples: ShortArray, timestampMs: Long = currentTimeMillis()) {
        scope.launch {
            voiceCommandRecognizer.processPcmSamples(samples, timestampMs)
        }
    }

    // --- Swift-Friendly Directed Mesh Routing Observers & Controls (Sprint 22 / ICA-2203) ---

    fun observeRoutingSnapshot(onSnapshot: (org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val snapshot = directedRouter.getSnapshot(currentTimeMillis())
                onSnapshot(snapshot)
                delay(2000L)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun initiateRouteDiscovery(destinationId: String) {
        scope.launch {
            val rreq = directedRouter.routingEngine.initiateRouteDiscovery(destinationId, currentTimeMillis())
            val payload = org.nowni.intercom_alpha.mesh.routing.RoutingSerializer.encodeRreq(rreq)
            meshTransport.sendControl(ControlType.ROUTE_REQUEST, payload)
        }
    }

    // --- Swift-Friendly Multi-Group Federation Gateway Observers & Controls (Sprint 23 / ICA-2303) ---

    fun observeFederationSnapshot(onSnapshot: (org.nowni.intercom_alpha.mesh.federation.FederationSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val snapshot = interGroupGateway.getSnapshot(currentTimeMillis())
                onSnapshot(snapshot)
                delay(2000L)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun setFederationPolicy(groupId: String, policy: org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy) {
        interGroupGateway.setCrossGroupPolicy(groupId, policy)
    }

    fun createFederationInvite(): String {
        val invite = interGroupGateway.createFederationInvite(currentTimeMs = currentTimeMillis())
        return org.nowni.intercom_alpha.mesh.federation.FederationSerializer.encodeInvite(invite)
    }

    fun acceptFederationInvite(inviteCode: String): Boolean {
        val invite = org.nowni.intercom_alpha.mesh.federation.FederationSerializer.decodeInvite(inviteCode) ?: return false
        return interGroupGateway.acceptFederationInvite(invite, currentTimeMillis())
    }

    // --- Swift-Friendly Mesh Airtime QoS Traffic Shaping Observers & Controls (Sprint 24 / ICA-2403) ---

    val qosScheduler: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler =
        meshTransport.qosScheduler ?: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler()

    fun observeQoSSnapshot(onSnapshot: (org.nowni.intercom_alpha.mesh.qos.QoSSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            qosScheduler.qosSnapshot.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Swift-Friendly Multi-Path Failover & Handover Observers & Controls (Sprint 25 / ICA-2503) ---

    val failoverArbiter: org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter =
        org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter()

    fun observeFailoverSnapshot(onSnapshot: (org.nowni.intercom_alpha.mesh.failover.FailoverMetrics) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            failoverArbiter.metrics.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun setFailoverModeOverride(mode: org.nowni.intercom_alpha.mesh.failover.FailoverMode?) {
        scope.launch {
            failoverArbiter.forceMode(mode, currentTimeMillis())
        }
    }

    // --- Swift-Friendly Tactical Snapshot & Visual Telemetry Observers (Sprint 26 / Sprint 29) ---

    val snapshotManager: org.nowni.intercom_alpha.mesh.visual.SnapshotTransferManager =
        org.nowni.intercom_alpha.mesh.visual.SnapshotTransferManager(qosScheduler = qosScheduler)

    fun observeSnapshotTransfers(onTransfers: (List<org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            snapshotManager.activeTransfers.collect { transfers ->
                onTransfers(transfers)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeLatestReceivedSnapshot(onSnapshot: (Pair<org.nowni.intercom_alpha.mesh.visual.SnapshotHeader, ByteArray>?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            snapshotManager.latestReceivedSnapshot.collect { snapshot ->
                onSnapshot(snapshot)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Swift-Friendly Concurrent Multi-Radio Arbiter Observers & Controls (Sprint 26 / Sprint 29) ---

    val multiRadioArbiter: org.nowni.intercom_alpha.mesh.radio.MultiRadioArbiter =
        org.nowni.intercom_alpha.mesh.radio.MultiRadioArbiter()

    fun observeMultiRadioState(onState: (org.nowni.intercom_alpha.mesh.radio.MultiRadioState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            multiRadioArbiter.state.collect { state ->
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Swift-Friendly Group Rekey Protocol Observers & Controls (Sprint 26 / Sprint 29) ---

    val rekeyProtocol: org.nowni.intercom_alpha.mesh.security.GroupRekeyProtocol =
        org.nowni.intercom_alpha.mesh.security.GroupRekeyProtocol(
            groupId = "squad-ios",
            localNodeId = localPeerId,
            baseKeyInput = ByteArray(32) { 0x42.toByte() }
        )

    fun observeRekeyEpochSnapshot(onSnapshot: (org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            onSnapshot(rekeyProtocol.getSnapshot())
            rekeyProtocol.events.collect { _ ->
                onSnapshot(rekeyProtocol.getSnapshot())
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun initiateManualRekey(reason: org.nowni.intercom_alpha.mesh.security.RekeyTriggerReason = org.nowni.intercom_alpha.mesh.security.RekeyTriggerReason.MANUAL_REKEY): Boolean {
        scope.launch {
            try {
                val packet = rekeyProtocol.initiateRekey(reason = reason)
                meshTransport.sendControl(ControlType.EPOCH_ROLLOVER, packet.epochKeyDerivationSalt)
            } catch (_: Exception) {
            }
        }
        return true
    }
}

