package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.headset.HelmetAccessoryManager
import org.nowni.intercom_alpha.headset.HelmetAccessoryState
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.MeshFileTransferManager
import org.nowni.intercom_alpha.map.TrailMapManager
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.TrailPolyline
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.RepeaterBeaconPayload
import org.nowni.intercom_alpha.mesh.RepeaterPongPayload
import org.nowni.intercom_alpha.mesh.RepeaterTelemetry
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
    const val TAG_REPEATER_PING: Byte = 0x04
    const val TAG_REPEATER_PONG: Byte = 0x05
    const val TAG_GEOFENCE_ALARM: Byte = 0x06
    const val TAG_POWER_STATUS: Byte = 0x07
}

/**
 * Platform hardware abstraction contract for Push-To-Talk, BLE Mesh peer roster,
 * audio RMS level metering, GPS coordinate streaming, non-audio control packet transmission,
 * connected helmet Bluetooth GATT accessories (Cardo / Sena / EJEAS), stationary basecamp repeater mode,
 * and cloud relay bridging.
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

    val helmetAccessoryState: StateFlow<HelmetAccessoryState>
    val isCloudBridgeConnected: StateFlow<Boolean>

    val isRepeaterModeActive: StateFlow<Boolean>
    val repeaterTelemetry: StateFlow<RepeaterTelemetry?>
    val incomingRepeaterPongs: SharedFlow<RepeaterPongPayload>
    val incomingRepeaterBeacons: SharedFlow<RepeaterBeaconPayload>
    val trailMapSnapshot: StateFlow<TrailMapSnapshot>
    val fileTransfers: StateFlow<List<FileTransferProgress>>
    val storedTrails: StateFlow<List<TrailPolyline>>
    val geofenceSnapshot: StateFlow<org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot>
    val powerGovernorState: StateFlow<org.nowni.intercom_alpha.power.PowerGovernorState>

    fun setPttActive(active: Boolean)
    fun setRepeaterModeActive(active: Boolean)
    fun updateRepeaterTelemetry(telemetry: RepeaterTelemetry?)
    fun updateTrailMapSnapshot(snapshot: TrailMapSnapshot)
    fun updateFileTransfers(transfers: List<FileTransferProgress>)
    fun updateStoredTrails(trails: List<TrailPolyline>)
    fun updateGeofenceSnapshot(snapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot)
    fun updatePowerGovernorState(state: org.nowni.intercom_alpha.power.PowerGovernorState)
    fun sendRepeaterPacket(packet: MeshPacket.Control)
    fun sendControlPacket(packet: MeshPacket.Control) {
        sendRepeaterPacket(packet)
    }
    fun sendControlPayload(tag: Byte, payload: ByteArray)
    fun attachSubsystems(
        channelManager: ChannelManager,
        blackboxRecorder: EmergencyBlackboxRecorder,
        helmetAccessoryManager: HelmetAccessoryManager? = null,
        repeaterService: MeshRepeaterService? = null,
        trailMapManager: TrailMapManager? = null,
        fileTransferManager: MeshFileTransferManager? = null,
        geofenceEngine: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine? = null,
        batteryGovernor: org.nowni.intercom_alpha.power.BatteryGovernor? = null
    ) {}
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

    protected val _helmetAccessoryState = MutableStateFlow(HelmetAccessoryState())
    override val helmetAccessoryState: StateFlow<HelmetAccessoryState> = _helmetAccessoryState.asStateFlow()

    protected val _isCloudBridgeConnected = MutableStateFlow(false)
    override val isCloudBridgeConnected: StateFlow<Boolean> = _isCloudBridgeConnected.asStateFlow()

    protected val _isRepeaterModeActive = MutableStateFlow(false)
    override val isRepeaterModeActive: StateFlow<Boolean> = _isRepeaterModeActive.asStateFlow()

    protected val _repeaterTelemetry = MutableStateFlow<RepeaterTelemetry?>(null)
    override val repeaterTelemetry: StateFlow<RepeaterTelemetry?> = _repeaterTelemetry.asStateFlow()

    protected val _incomingRepeaterPongs = MutableSharedFlow<RepeaterPongPayload>(extraBufferCapacity = 16)
    override val incomingRepeaterPongs: SharedFlow<RepeaterPongPayload> = _incomingRepeaterPongs.asSharedFlow()

    protected val _incomingRepeaterBeacons = MutableSharedFlow<RepeaterBeaconPayload>(extraBufferCapacity = 16)
    override val incomingRepeaterBeacons: SharedFlow<RepeaterBeaconPayload> = _incomingRepeaterBeacons.asSharedFlow()

    protected val _trailMapSnapshot = MutableStateFlow(TrailMapSnapshot())
    override val trailMapSnapshot: StateFlow<TrailMapSnapshot> = _trailMapSnapshot.asStateFlow()

    protected val _fileTransfers = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    override val fileTransfers: StateFlow<List<FileTransferProgress>> = _fileTransfers.asStateFlow()

    protected val _storedTrails = MutableStateFlow<List<TrailPolyline>>(emptyList())
    override val storedTrails: StateFlow<List<TrailPolyline>> = _storedTrails.asStateFlow()

    protected val _geofenceSnapshot = MutableStateFlow(org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot())
    override val geofenceSnapshot: StateFlow<org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot> = _geofenceSnapshot.asStateFlow()

    protected val _powerGovernorState = MutableStateFlow(org.nowni.intercom_alpha.power.PowerGovernorState())
    override val powerGovernorState: StateFlow<org.nowni.intercom_alpha.power.PowerGovernorState> = _powerGovernorState.asStateFlow()

    override fun setPttActive(active: Boolean) {
        _isTransmitting.value = active
    }

    override fun setRepeaterModeActive(active: Boolean) {
        _isRepeaterModeActive.value = active
    }

    override fun updateRepeaterTelemetry(telemetry: RepeaterTelemetry?) {
        _repeaterTelemetry.value = telemetry
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

    override fun updateGeofenceSnapshot(snapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot) {
        _geofenceSnapshot.value = snapshot
    }

    override fun updatePowerGovernorState(state: org.nowni.intercom_alpha.power.PowerGovernorState) {
        _powerGovernorState.value = state
    }

    fun simulateGeofenceSnapshot(snapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot) {
        _geofenceSnapshot.value = snapshot
    }

    fun simulatePowerGovernorState(state: org.nowni.intercom_alpha.power.PowerGovernorState) {
        _powerGovernorState.value = state
    }

    override fun sendRepeaterPacket(packet: MeshPacket.Control) {
        // No-op for testing / previews
    }

    override fun sendControlPacket(packet: MeshPacket.Control) {
        // No-op for testing / previews
    }

    override fun sendControlPayload(tag: Byte, payload: ByteArray) {
        // No-op for testing / previews
    }

    fun simulateFileTransfers(transfers: List<FileTransferProgress>) {
        _fileTransfers.value = transfers
    }

    fun simulateStoredTrails(trails: List<TrailPolyline>) {
        _storedTrails.value = trails
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

    fun simulateHelmetAccessoryState(state: HelmetAccessoryState) {
        _helmetAccessoryState.value = state
    }

    fun simulateCloudBridgeConnected(connected: Boolean) {
        _isCloudBridgeConnected.value = connected
    }

    fun simulateIncomingRepeaterPong(pong: RepeaterPongPayload) {
        _incomingRepeaterPongs.tryEmit(pong)
    }

    fun simulateIncomingRepeaterBeacon(beacon: RepeaterBeaconPayload) {
        _incomingRepeaterBeacons.tryEmit(beacon)
    }

    fun simulateTrailMapSnapshot(snapshot: TrailMapSnapshot) {
        _trailMapSnapshot.value = snapshot
    }
}
