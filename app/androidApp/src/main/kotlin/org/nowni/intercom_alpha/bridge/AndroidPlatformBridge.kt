package org.nowni.intercom_alpha.bridge

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.headset.HelmetAccessoryManager
import org.nowni.intercom_alpha.headset.HelmetAccessoryState
import org.nowni.intercom_alpha.map.FileTransferAck
import org.nowni.intercom_alpha.map.FileTransferChunk
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.MeshFileTransferEngine
import org.nowni.intercom_alpha.map.MeshFileTransferManager
import org.nowni.intercom_alpha.map.TrailMapManager
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.TrailPolyline
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.RepeaterBeaconPayload
import org.nowni.intercom_alpha.mesh.RepeaterPongPayload
import org.nowni.intercom_alpha.mesh.RepeaterTelemetry
import org.nowni.intercom_alpha.service.IntercomForegroundService
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetryFrame
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.MeshTextWireFrame

/**
 * Concrete Android implementation of [IntercomHardwareBridge].
 *
 * Coordinates live Bluetooth LE mesh hardware, AudioEngine mic/speaker I/O,
 * physical headset button triggers, and stationary basecamp repeater relay mode.
 */
class AndroidPlatformBridge private constructor() : IntercomHardwareBridge {

    private val TAG = "AndroidPlatformBridge"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isTransmitting = MutableStateFlow(false)
    override val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    override val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    private val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    private val _incomingTextFrames = MutableSharedFlow<MeshTextWireFrame>(extraBufferCapacity = 64)
    override val incomingTextFrames: SharedFlow<MeshTextWireFrame> = _incomingTextFrames.asSharedFlow()

    private val _incomingGpsFrames = MutableSharedFlow<ConvoyTelemetryFrame>(extraBufferCapacity = 64)
    override val incomingGpsFrames: SharedFlow<ConvoyTelemetryFrame> = _incomingGpsFrames.asSharedFlow()

    private val _incomingEmergencyAlerts = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val incomingEmergencyAlerts: SharedFlow<Unit> = _incomingEmergencyAlerts.asSharedFlow()

    private val _localGpsCoordinate = MutableStateFlow<GpsCoordinate?>(null)
    override val localGpsCoordinate: StateFlow<GpsCoordinate?> = _localGpsCoordinate.asStateFlow()

    private val _helmetAccessoryState = MutableStateFlow(HelmetAccessoryState())
    override val helmetAccessoryState: StateFlow<HelmetAccessoryState> = _helmetAccessoryState.asStateFlow()

    private val _isCloudBridgeConnected = MutableStateFlow(false)
    override val isCloudBridgeConnected: StateFlow<Boolean> = _isCloudBridgeConnected.asStateFlow()

    private val _isRepeaterModeActive = MutableStateFlow(false)
    override val isRepeaterModeActive: StateFlow<Boolean> = _isRepeaterModeActive.asStateFlow()

    private val _repeaterTelemetry = MutableStateFlow<RepeaterTelemetry?>(null)
    override val repeaterTelemetry: StateFlow<RepeaterTelemetry?> = _repeaterTelemetry.asStateFlow()

    private val _incomingRepeaterPongs = MutableSharedFlow<RepeaterPongPayload>(extraBufferCapacity = 16)
    override val incomingRepeaterPongs: SharedFlow<RepeaterPongPayload> = _incomingRepeaterPongs.asSharedFlow()

    private val _incomingRepeaterBeacons = MutableSharedFlow<RepeaterBeaconPayload>(extraBufferCapacity = 16)
    override val incomingRepeaterBeacons: SharedFlow<RepeaterBeaconPayload> = _incomingRepeaterBeacons.asSharedFlow()

    private val _trailMapSnapshot = MutableStateFlow(TrailMapSnapshot())
    override val trailMapSnapshot: StateFlow<TrailMapSnapshot> = _trailMapSnapshot.asStateFlow()

    private val _fileTransfers = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    override val fileTransfers: StateFlow<List<FileTransferProgress>> = _fileTransfers.asStateFlow()

    private val _storedTrails = MutableStateFlow<List<TrailPolyline>>(emptyList())
    override val storedTrails: StateFlow<List<TrailPolyline>> = _storedTrails.asStateFlow()

    var channelManager: ChannelManager? = null
        private set
    var blackboxRecorder: EmergencyBlackboxRecorder? = null
        private set
    var helmetAccessoryManager: HelmetAccessoryManager? = null
        private set
    var repeaterService: MeshRepeaterService? = null
        private set
    var trailMapManager: TrailMapManager? = null
        private set
    var fileTransferManager: MeshFileTransferManager? = null
        private set

    private var boundService: IntercomForegroundService? = null
    private var syncJob: Job? = null

    companion object {
        val instance by lazy { AndroidPlatformBridge() }
    }

    override fun attachSubsystems(
        channelManager: ChannelManager,
        blackboxRecorder: EmergencyBlackboxRecorder,
        helmetAccessoryManager: HelmetAccessoryManager?,
        repeaterService: MeshRepeaterService?,
        trailMapManager: TrailMapManager?,
        fileTransferManager: MeshFileTransferManager?
    ) {
        this.channelManager = channelManager
        this.blackboxRecorder = blackboxRecorder
        this.helmetAccessoryManager = helmetAccessoryManager
        this.repeaterService = repeaterService
        this.trailMapManager = trailMapManager
        this.fileTransferManager = fileTransferManager

        helmetAccessoryManager?.let { accessoryMgr ->
            scope.launch {
                accessoryMgr.accessoryState.collect { state ->
                    _helmetAccessoryState.value = state
                }
            }
        }

        trailMapManager?.let { mapMgr ->
            scope.launch {
                mapMgr.snapshot.collect { snap ->
                    _trailMapSnapshot.value = snap
                }
            }
        }

        fileTransferManager?.let { ftm ->
            scope.launch {
                ftm.activeTransfers.collect { transfers ->
                    _fileTransfers.value = transfers
                }
            }
            scope.launch {
                ftm.storedTrails.collect { trails ->
                    _storedTrails.value = trails
                }
            }
            ftm.onSendChunk = { chunk, _ ->
                val bytes = MeshFileTransferEngine.serializeChunk(chunk)
                sendControlPacket(MeshPacket.Control(senderId = "android-local", type = ControlType.FILE_CHUNK, payload = bytes))
            }
            ftm.onSendAck = { ack, _ ->
                val bytes = MeshFileTransferEngine.serializeAck(ack)
                sendControlPacket(MeshPacket.Control(senderId = "android-local", type = ControlType.FILE_ACK, payload = bytes))
            }
        }
    }

    override fun updateTrailMapSnapshot(snapshot: TrailMapSnapshot) {
        _trailMapSnapshot.value = snapshot
    }

    override fun updateFileTransfers(transfers: List<FileTransferProgress>) {
        _fileTransfers.value = transfers
    }

    override fun updateStoredTrails(trails: List<TrailPolyline>) {
        _storedTrails.value = trails
    }

    fun bindService(service: IntercomForegroundService) {
        Log.d(TAG, "Binding IntercomForegroundService to AndroidPlatformBridge")
        boundService = service
        syncJob?.cancel()

        syncJob = scope.launch {
            // 1. Sync PTT state (including headset hardware button events)
            launch {
                service.isTransmitting.collect { active ->
                    _isTransmitting.value = active
                }
            }

            // 2. Sync BLE Mesh connected peers
            launch {
                service.connectedPeers.collect { peers ->
                    _connectedPeers.value = peers
                }
            }

            // 3. Sync real-time microphone RMS level
            launch(Dispatchers.Default) {
                try {
                    for (level in service.audioEngine.inputLevel) {
                        _inputLevel.value = level
                    }
                } catch (_: ClosedReceiveChannelException) {
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "Audio inputLevel channel closed", e)
                }
            }

            // 4. Sync real-time speaker RMS level
            launch(Dispatchers.Default) {
                try {
                    for (level in service.audioEngine.outputLevel) {
                        _outputLevel.value = level
                    }
                } catch (_: ClosedReceiveChannelException) {
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "Audio outputLevel channel closed", e)
                }
            }
        }
    }

    fun unbindService() {
        Log.d(TAG, "Unbinding IntercomForegroundService from AndroidPlatformBridge")
        syncJob?.cancel()
        syncJob = null
        boundService = null
        _isTransmitting.value = false
        _inputLevel.value = 0f
        _outputLevel.value = 0f
    }

    override fun setPttActive(active: Boolean) {
        Log.d(TAG, "setPttActive($active) requested from UI")
        _isTransmitting.value = active
        boundService?.setTransmitting(active)
    }

    override fun setRepeaterModeActive(active: Boolean) {
        Log.d(TAG, "setRepeaterModeActive($active) requested from UI")
        _isRepeaterModeActive.value = active
        boundService?.setRepeaterMode(active)
    }

    override fun updateRepeaterTelemetry(telemetry: RepeaterTelemetry?) {
        _repeaterTelemetry.value = telemetry
    }

    override fun sendRepeaterPacket(packet: MeshPacket.Control) {
        sendControlPacket(packet)
    }

    override fun sendControlPacket(packet: MeshPacket.Control) {
        scope.launch(Dispatchers.IO) {
            val service = boundService
            if (service == null) {
                Log.w(TAG, "Cannot sendControlPacket: No service bound")
                return@launch
            }
            try {
                service.meshTransport.sendControl(packet.type, packet.payload)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting control packet (${packet.type})", e)
            }
        }
    }

    override fun sendControlPayload(tag: Byte, payload: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val service = boundService
            if (service == null) {
                Log.w(TAG, "Cannot sendControlPayload: No service bound")
                return@launch
            }
            try {
                val tagged = ByteArray(payload.size + 1)
                tagged[0] = tag
                System.arraycopy(payload, 0, tagged, 1, payload.size)
                service.meshTransport.sendControl(ControlType.SYNC_REQUEST, tagged)
            } catch (e: Exception) {
                Log.e(TAG, "Error broadcasting control payload (tag=$tag)", e)
            }
        }
    }

    fun postIncomingText(wireFrame: MeshTextWireFrame) {
        _incomingTextFrames.tryEmit(wireFrame)
    }

    fun postIncomingGps(telemetryFrame: ConvoyTelemetryFrame) {
        _incomingGpsFrames.tryEmit(telemetryFrame)
    }

    fun postEmergencyAlert() {
        _incomingEmergencyAlerts.tryEmit(Unit)
    }

    fun postLocalGps(coord: GpsCoordinate) {
        _localGpsCoordinate.value = coord
    }

    fun postIncomingRepeaterPong(pong: RepeaterPongPayload) {
        _incomingRepeaterPongs.tryEmit(pong)
    }

    fun postIncomingRepeaterBeacon(beacon: RepeaterBeaconPayload) {
        _incomingRepeaterBeacons.tryEmit(beacon)
    }

    fun postIncomingFileChunk(chunk: FileTransferChunk, senderId: String) {
        scope.launch {
            fileTransferManager?.handleIncomingChunk(chunk, senderId)
        }
    }

    fun postIncomingFileAck(ack: FileTransferAck) {
        scope.launch {
            fileTransferManager?.handleIncomingAck(ack)
        }
    }
}
