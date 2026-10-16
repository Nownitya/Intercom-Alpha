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
    val crashSnapshot: StateFlow<org.nowni.intercom_alpha.safety.CrashCountdownSnapshot>
    val latestVoiceCommand: StateFlow<org.nowni.intercom_alpha.audio.CommandDetection?>
    val routingSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot>
    val federationSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.federation.FederationSnapshot>
    val qosSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.qos.QoSSnapshot>
    val failoverSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.failover.FailoverMetrics>
    val snapshotProgress: StateFlow<org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?>
    val multiRadioState: StateFlow<org.nowni.intercom_alpha.mesh.radio.MultiRadioState>
    val rekeyEpochSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot>
    val satelliteBridgeStatus: StateFlow<org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus>
    val phyNegotiationSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot>
    val wearableCompanionSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot>

    fun setPttActive(active: Boolean)
    fun setRepeaterModeActive(active: Boolean)
    fun updateRepeaterTelemetry(telemetry: RepeaterTelemetry?)
    fun updateTrailMapSnapshot(snapshot: TrailMapSnapshot)
    fun updateFileTransfers(transfers: List<FileTransferProgress>)
    fun updateStoredTrails(trails: List<TrailPolyline>)
    fun updateGeofenceSnapshot(snapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot)
    fun updatePowerGovernorState(state: org.nowni.intercom_alpha.power.PowerGovernorState)
    fun updateCrashSnapshot(snapshot: org.nowni.intercom_alpha.safety.CrashCountdownSnapshot)
    fun updateLatestVoiceCommand(detection: org.nowni.intercom_alpha.audio.CommandDetection?)
    fun updateRoutingSnapshot(snapshot: org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot)
    fun updateFederationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.federation.FederationSnapshot)
    fun updateQoSSnapshot(snapshot: org.nowni.intercom_alpha.mesh.qos.QoSSnapshot)
    fun updateFailoverSnapshot(metrics: org.nowni.intercom_alpha.mesh.failover.FailoverMetrics)
    fun updateSnapshotProgress(progress: org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?)
    fun updateMultiRadioState(state: org.nowni.intercom_alpha.mesh.radio.MultiRadioState)
    fun updateRekeyEpochSnapshot(snapshot: org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot)
    fun updateSatelliteBridgeStatus(status: org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus)
    fun updatePhyNegotiationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot)
    fun updateWearableCompanionSnapshot(snapshot: org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot)
    fun setFailoverModeOverride(mode: org.nowni.intercom_alpha.mesh.failover.FailoverMode?) {}
    fun setFederationPolicy(groupId: String, policy: org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy) {}
    fun createFederationInvite(): String? = null
    fun acceptFederationInvite(inviteCode: String): Boolean = false
    fun initiateRouteDiscovery(destinationId: String) {}
    fun initiateManualRekey(reason: org.nowni.intercom_alpha.mesh.security.RekeyTriggerReason = org.nowni.intercom_alpha.mesh.security.RekeyTriggerReason.MANUAL_REKEY): org.nowni.intercom_alpha.mesh.security.RekeyControlPacket? = null
    fun setPreferredRadioForBulk(radio: org.nowni.intercom_alpha.mesh.radio.RadioType) {}
    fun triggerSatelliteDistressBeacon(message: String = "EMERGENCY SOS"): Boolean = false
    fun processWearablePttClick(pressed: Boolean) {}
    fun cancelCrashCountdown()
    fun triggerManualCrashSos()
    fun processImuReading(reading: org.nowni.intercom_alpha.safety.ImuReading) {}
    fun processVoiceAudio(samples: ShortArray, timestampMs: Long) {}
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
        batteryGovernor: org.nowni.intercom_alpha.power.BatteryGovernor? = null,
        crashDetector: org.nowni.intercom_alpha.safety.CrashDetector? = null,
        voiceCommandRecognizer: org.nowni.intercom_alpha.audio.VoiceCommandRecognizer? = null,
        directedRouter: org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter? = null,
        interGroupGateway: org.nowni.intercom_alpha.mesh.federation.InterGroupGateway? = null,
        qosScheduler: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler? = null,
        failoverArbiter: org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter? = null,
        snapshotManager: org.nowni.intercom_alpha.mesh.visual.SnapshotTransferManager? = null,
        multiRadioArbiter: org.nowni.intercom_alpha.mesh.radio.MultiRadioArbiter? = null,
        groupRekeyProtocol: org.nowni.intercom_alpha.mesh.security.GroupRekeyProtocol? = null,
        satelliteBridge: org.nowni.intercom_alpha.mesh.satellite.SatelliteGatewayBridge? = null,
        phyArbiter: org.nowni.intercom_alpha.mesh.phy.AdaptivePhyArbiter? = null,
        wearableProtocol: org.nowni.intercom_alpha.mesh.wearable.WearableCompanionProtocol? = null
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

    protected val _crashSnapshot = MutableStateFlow(org.nowni.intercom_alpha.safety.CrashCountdownSnapshot())
    override val crashSnapshot: StateFlow<org.nowni.intercom_alpha.safety.CrashCountdownSnapshot> = _crashSnapshot.asStateFlow()

    protected val _latestVoiceCommand = MutableStateFlow<org.nowni.intercom_alpha.audio.CommandDetection?>(null)
    override val latestVoiceCommand: StateFlow<org.nowni.intercom_alpha.audio.CommandDetection?> = _latestVoiceCommand.asStateFlow()

    protected val _routingSnapshot = MutableStateFlow(
        org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot(
            localNodeId = "local-rider",
            activeRoutes = emptyList(),
            totalDirectedPackets = 0L,
            totalFloodedPackets = 0L,
            airtimeSavingsPercentage = 0.0,
            timestampMs = 0L
        )
    )
    override val routingSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot> = _routingSnapshot.asStateFlow()

    protected val _federationSnapshot = MutableStateFlow(
        org.nowni.intercom_alpha.mesh.federation.FederationSnapshot(
            localGroupId = "squad-main",
            localRole = org.nowni.intercom_alpha.mesh.federation.GatewayRole.PRIMARY,
            bridgeState = org.nowni.intercom_alpha.mesh.federation.FederationBridgeState.DISCONNECTED,
            federatedGroups = emptyList(),
            activePolicy = org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy.CAPTAIN_ONLY,
            totalRelayedPacketsIn = 0L,
            totalRelayedPacketsOut = 0L,
            totalDroppedPolicyViolations = 0L,
            lastRelayedTimestampMs = 0L
        )
    )
    override val federationSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.federation.FederationSnapshot> = _federationSnapshot.asStateFlow()

    protected val _qosSnapshot = MutableStateFlow(org.nowni.intercom_alpha.mesh.qos.QoSSnapshot())
    override val qosSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.qos.QoSSnapshot> = _qosSnapshot.asStateFlow()

    protected val _failoverSnapshot = MutableStateFlow(org.nowni.intercom_alpha.mesh.failover.FailoverMetrics())
    override val failoverSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.failover.FailoverMetrics> = _failoverSnapshot.asStateFlow()

    protected val _snapshotProgress = MutableStateFlow<org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?>(null)
    override val snapshotProgress: StateFlow<org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?> = _snapshotProgress.asStateFlow()

    protected val _multiRadioState = MutableStateFlow(org.nowni.intercom_alpha.mesh.radio.MultiRadioState())
    override val multiRadioState: StateFlow<org.nowni.intercom_alpha.mesh.radio.MultiRadioState> = _multiRadioState.asStateFlow()

    protected val _rekeyEpochSnapshot = MutableStateFlow(org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot())
    override val rekeyEpochSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot> = _rekeyEpochSnapshot.asStateFlow()

    protected val _satelliteBridgeStatus = MutableStateFlow(org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus())
    override val satelliteBridgeStatus: StateFlow<org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus> = _satelliteBridgeStatus.asStateFlow()

    protected val _phyNegotiationSnapshot = MutableStateFlow(org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot())
    override val phyNegotiationSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot> = _phyNegotiationSnapshot.asStateFlow()

    protected val _wearableCompanionSnapshot = MutableStateFlow(org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot())
    override val wearableCompanionSnapshot: StateFlow<org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot> = _wearableCompanionSnapshot.asStateFlow()

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

    override fun updateCrashSnapshot(snapshot: org.nowni.intercom_alpha.safety.CrashCountdownSnapshot) {
        _crashSnapshot.value = snapshot
    }

    override fun updateLatestVoiceCommand(detection: org.nowni.intercom_alpha.audio.CommandDetection?) {
        _latestVoiceCommand.value = detection
    }

    override fun updateRoutingSnapshot(snapshot: org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot) {
        _routingSnapshot.value = snapshot
    }

    fun simulateRoutingSnapshot(snapshot: org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot) {
        _routingSnapshot.value = snapshot
    }

    override fun updateFederationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.federation.FederationSnapshot) {
        _federationSnapshot.value = snapshot
    }

    fun simulateFederationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.federation.FederationSnapshot) {
        _federationSnapshot.value = snapshot
    }

    override fun updateQoSSnapshot(snapshot: org.nowni.intercom_alpha.mesh.qos.QoSSnapshot) {
        _qosSnapshot.value = snapshot
    }

    fun simulateQoSSnapshot(snapshot: org.nowni.intercom_alpha.mesh.qos.QoSSnapshot) {
        _qosSnapshot.value = snapshot
    }

    override fun updateFailoverSnapshot(metrics: org.nowni.intercom_alpha.mesh.failover.FailoverMetrics) {
        _failoverSnapshot.value = metrics
    }

    fun simulateFailoverSnapshot(metrics: org.nowni.intercom_alpha.mesh.failover.FailoverMetrics) {
        _failoverSnapshot.value = metrics
    }

    override fun updateSnapshotProgress(progress: org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?) {
        _snapshotProgress.value = progress
    }

    fun simulateSnapshotProgress(progress: org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress?) {
        _snapshotProgress.value = progress
    }

    override fun updateMultiRadioState(state: org.nowni.intercom_alpha.mesh.radio.MultiRadioState) {
        _multiRadioState.value = state
    }

    fun simulateMultiRadioState(state: org.nowni.intercom_alpha.mesh.radio.MultiRadioState) {
        _multiRadioState.value = state
    }

    override fun updateRekeyEpochSnapshot(snapshot: org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot) {
        _rekeyEpochSnapshot.value = snapshot
    }

    fun simulateRekeyEpochSnapshot(snapshot: org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot) {
        _rekeyEpochSnapshot.value = snapshot
    }

    override fun setPreferredRadioForBulk(radio: org.nowni.intercom_alpha.mesh.radio.RadioType) {
        _multiRadioState.value = _multiRadioState.value.copy(bulkTransferPreferredRadio = radio)
    }

    override fun updateSatelliteBridgeStatus(status: org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus) {
        _satelliteBridgeStatus.value = status
    }

    fun simulateSatelliteBridgeStatus(status: org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus) {
        _satelliteBridgeStatus.value = status
    }

    override fun updatePhyNegotiationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot) {
        _phyNegotiationSnapshot.value = snapshot
    }

    fun simulatePhyNegotiationSnapshot(snapshot: org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot) {
        _phyNegotiationSnapshot.value = snapshot
    }

    override fun updateWearableCompanionSnapshot(snapshot: org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot) {
        _wearableCompanionSnapshot.value = snapshot
    }

    fun simulateWearableCompanionSnapshot(snapshot: org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot) {
        _wearableCompanionSnapshot.value = snapshot
    }

    override fun triggerSatelliteDistressBeacon(message: String): Boolean {
        _satelliteBridgeStatus.value = _satelliteBridgeStatus.value.copy(
            linkState = org.nowni.intercom_alpha.mesh.satellite.SatelliteLinkState.TRANSMITTING,
            totalDispatchedCount = _satelliteBridgeStatus.value.totalDispatchedCount + 1
        )
        return true
    }

    override fun processWearablePttClick(pressed: Boolean) {
        _wearableCompanionSnapshot.value = _wearableCompanionSnapshot.value.copy(
            pttState = if (pressed) org.nowni.intercom_alpha.mesh.wearable.PttButtonState.PRESSED else org.nowni.intercom_alpha.mesh.wearable.PttButtonState.RELEASED
        )
        _isTransmitting.value = pressed
    }

    override fun setFederationPolicy(groupId: String, policy: org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy) {
        val current = _federationSnapshot.value
        val updatedList = current.federatedGroups.map {
            if (it.groupId == groupId) it.copy(policy = policy) else it
        }
        _federationSnapshot.value = current.copy(federatedGroups = updatedList, activePolicy = policy)
    }

    override fun cancelCrashCountdown() {
        _crashSnapshot.value = _crashSnapshot.value.copy(
            state = org.nowni.intercom_alpha.safety.CrashState.CANCELLED_BY_RIDER,
            remainingSeconds = 0
        )
    }

    override fun triggerManualCrashSos() {
        _crashSnapshot.value = _crashSnapshot.value.copy(
            state = org.nowni.intercom_alpha.safety.CrashState.EMERGENCY_DISPATCHED,
            remainingSeconds = 0
        )
    }

    fun simulateGeofenceSnapshot(snapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot) {
        _geofenceSnapshot.value = snapshot
    }

    fun simulatePowerGovernorState(state: org.nowni.intercom_alpha.power.PowerGovernorState) {
        _powerGovernorState.value = state
    }

    fun simulateCrashSnapshot(snapshot: org.nowni.intercom_alpha.safety.CrashCountdownSnapshot) {
        _crashSnapshot.value = snapshot
    }

    fun simulateVoiceCommand(detection: org.nowni.intercom_alpha.audio.CommandDetection?) {
        _latestVoiceCommand.value = detection
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
