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
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.service.IntercomForegroundService
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetryFrame
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.MeshTextWireFrame

/**
 * Concrete Android implementation of [IntercomHardwareBridge].
 *
 * Coordinates live Bluetooth LE mesh hardware, AudioEngine mic/speaker I/O,
 * and physical headset button triggers with Compose UI presentation models.
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

    var channelManager: ChannelManager? = null
        private set
    var blackboxRecorder: EmergencyBlackboxRecorder? = null
        private set

    private var boundService: IntercomForegroundService? = null
    private var syncJob: Job? = null

    companion object {
        val instance by lazy { AndroidPlatformBridge() }
    }

    override fun attachSubsystems(channelManager: ChannelManager, blackboxRecorder: EmergencyBlackboxRecorder) {
        this.channelManager = channelManager
        this.blackboxRecorder = blackboxRecorder
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
}
