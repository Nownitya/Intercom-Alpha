package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetryFrame
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.MeshTextWireFrame

/**
 * Discriminator byte tags for multiplexed non-audio mesh control payloads.
 */
object ControlPayloadTag {
    const val TAG_TEXT: Byte = 0x01
    const val TAG_GPS: Byte = 0x02
    const val TAG_EMERGENCY: Byte = 0x03
}

/**
 * Platform hardware abstraction contract for Push-To-Talk, BLE Mesh peer roster,
 * audio RMS level metering, GPS coordinate streaming, and non-audio control packet transmission.
 */
interface IntercomHardwareBridge {
    val isTransmitting: StateFlow<Boolean>
    val connectedPeers: StateFlow<List<Peer>>
    val inputLevel: StateFlow<Float>
    val outputLevel: StateFlow<Float>

    val incomingTextFrames: SharedFlow<MeshTextWireFrame>
    val incomingGpsFrames: SharedFlow<ConvoyTelemetryFrame>
    val incomingEmergencyAlerts: SharedFlow<Unit>
    val localGpsCoordinate: StateFlow<GpsCoordinate?>

    fun setPttActive(active: Boolean)
    fun sendControlPayload(tag: Byte, payload: ByteArray)
    fun attachSubsystems(channelManager: ChannelManager, blackboxRecorder: EmergencyBlackboxRecorder) {}
}

/**
 * Default no-op / test mock implementation of [IntercomHardwareBridge] used for
 * unit tests, previews, and environments without platform audio/BLE.
 */
open class NoOpIntercomHardwareBridge(
    initialTransmitting: Boolean = false,
    initialPeers: List<Peer> = emptyList()
) : IntercomHardwareBridge {

    protected val _isTransmitting = MutableStateFlow(initialTransmitting)
    override val isTransmitting: StateFlow<Boolean> = _isTransmitting.asStateFlow()

    protected val _connectedPeers = MutableStateFlow(initialPeers)
    override val connectedPeers: StateFlow<List<Peer>> = _connectedPeers.asStateFlow()

    protected val _inputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()

    protected val _outputLevel = MutableStateFlow(0f)
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    protected val _incomingTextFrames = MutableSharedFlow<MeshTextWireFrame>(extraBufferCapacity = 64)
    override val incomingTextFrames: SharedFlow<MeshTextWireFrame> = _incomingTextFrames.asSharedFlow()

    protected val _incomingGpsFrames = MutableSharedFlow<ConvoyTelemetryFrame>(extraBufferCapacity = 64)
    override val incomingGpsFrames: SharedFlow<ConvoyTelemetryFrame> = _incomingGpsFrames.asSharedFlow()

    protected val _incomingEmergencyAlerts = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val incomingEmergencyAlerts: SharedFlow<Unit> = _incomingEmergencyAlerts.asSharedFlow()

    protected val _localGpsCoordinate = MutableStateFlow<GpsCoordinate?>(null)
    override val localGpsCoordinate: StateFlow<GpsCoordinate?> = _localGpsCoordinate.asStateFlow()

    override fun setPttActive(active: Boolean) {
        _isTransmitting.value = active
    }

    override fun sendControlPayload(tag: Byte, payload: ByteArray) {
        // No-op for testing / previews
    }

    fun updatePeers(peers: List<Peer>) {
        _connectedPeers.value = peers
    }

    fun updateLevels(input: Float, output: Float) {
        _inputLevel.value = input
        _outputLevel.value = output
    }

    fun simulateIncomingText(frame: MeshTextWireFrame) {
        _incomingTextFrames.tryEmit(frame)
    }

    fun simulateIncomingGps(frame: ConvoyTelemetryFrame) {
        _incomingGpsFrames.tryEmit(frame)
    }

    fun simulateLocalGps(coord: GpsCoordinate) {
        _localGpsCoordinate.value = coord
    }
}
