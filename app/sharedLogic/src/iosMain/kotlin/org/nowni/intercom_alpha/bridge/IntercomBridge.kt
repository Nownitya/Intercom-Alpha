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
                        else -> {}
                    }
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
}
