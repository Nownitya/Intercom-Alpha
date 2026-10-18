package org.nowni.intercom_alpha.feature.home.state

import org.nowni.intercom_alpha.audio.PriorityLevel
import org.nowni.intercom_alpha.channel.ChannelState
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.headset.HelmetAccessoryState
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.FileTransferType
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.TrailPolyline
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot
import org.nowni.intercom_alpha.telemetry.MotionState
import org.nowni.intercom_alpha.text.CannedMessageCode
import org.nowni.intercom_alpha.text.MeshTextMessage

data class HomeUiState(
    val isTransmitting: Boolean = false,
    val inputLevel: Float = 0.0f,
    val outputLevel: Float = 0.0f,
    val activeProfile: AudioProfile = AudioProfile.MEDIUM,
    val isGroupActive: Boolean = true,
    val groupName: String = "Rider Group 1",
    val inviteCode: String = "",
    val peers: List<Peer> = emptyList(),
    val showShareDialog: Boolean = false,
    val showJoinDialog: Boolean = false,
    val errorMessage: String? = null,

    // v2.0 Cockpit Subsystem State
    val channelState: ChannelState = ChannelState(),
    val radarSnapshot: RadarSnapshot = RadarSnapshot(0L),
    val convoySnapshot: ConvoyTelemetrySnapshot = ConvoyTelemetrySnapshot(null, MotionState.STATIONARY),
    val messages: List<MeshTextMessage> = emptyList(),
    val unreadTextCount: Int = 0,
    val isEmergencyActive: Boolean = false,
    val activePriorityLevel: PriorityLevel = PriorityLevel.NORMAL,
    val blackboxBufferedCount: Int = 0,
    val showMessagesDialog: Boolean = false,

    // Sprint 14 Advanced Mesh & Telemetry State
    val accessoryState: HelmetAccessoryState = HelmetAccessoryState(),
    val isCloudBridgeConnected: Boolean = false,
    val isPfsRatchetActive: Boolean = true,
    val isVoxLocked: Boolean = false,

    // Sprint 17 Basecamp Repeater HUD Telemetry (ICA-1704)
    val latestRepeaterBeacon: org.nowni.intercom_alpha.mesh.RepeaterBeaconPayload? = null,
    val repeaterPingLatencyMs: Long? = null,
    val isPingingRepeater: Boolean = false,

    // Sprint 18 Offline Vector Terrain & Trail Map State (ICA-1802)
    val trailMapSnapshot: TrailMapSnapshot = TrailMapSnapshot(),
    val showTrailSheet: Boolean = false,

    // Sprint 19 Over-The-Air BLE Mesh GPX/GeoJSON Trail Sharing (ICA-1902)
    val activeTransfers: List<FileTransferProgress> = emptyList(),
    val storedTrails: List<TrailPolyline> = emptyList(),
    val showTrailManagerSheet: Boolean = false,

    // Sprint 20 Tactical Convoy Geofencing & Battery Governor (ICA-2003)
    val geofenceSnapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot = org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot(),
    val powerGovernorState: org.nowni.intercom_alpha.power.PowerGovernorState = org.nowni.intercom_alpha.power.PowerGovernorState(),

    // Sprint 21 Autonomous Crash Impact Detection & Voice Command HUD (ICA-2103)
    val crashSnapshot: org.nowni.intercom_alpha.safety.CrashCountdownSnapshot = org.nowni.intercom_alpha.safety.CrashCountdownSnapshot(),
    val latestVoiceCommand: org.nowni.intercom_alpha.audio.CommandDetection? = null,
    val showCrashCountdownDialog: Boolean = false,

    // Sprint 22 Directed Mesh Routing State (ICA-2203)
    val routingSnapshot: org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot = org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot(
        localNodeId = "android-local",
        activeRoutes = emptyList(),
        totalDirectedPackets = 0L,
        totalFloodedPackets = 0L,
        airtimeSavingsPercentage = 0.0,
        timestampMs = 0L
    ),
    val showRoutingDiagnosticsSheet: Boolean = false,

    // Sprint 23 Multi-Group Inter-Convoy Federation State (ICA-2303)
    val federationSnapshot: org.nowni.intercom_alpha.mesh.federation.FederationSnapshot = org.nowni.intercom_alpha.mesh.federation.FederationSnapshot(
        localGroupId = "squad-alpha",
        localRole = org.nowni.intercom_alpha.mesh.federation.GatewayRole.PRIMARY,
        bridgeState = org.nowni.intercom_alpha.mesh.federation.FederationBridgeState.DISCONNECTED,
        federatedGroups = emptyList(),
        activePolicy = org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy.CAPTAIN_ONLY,
        totalRelayedPacketsIn = 0L,
        totalRelayedPacketsOut = 0L,
        totalDroppedPolicyViolations = 0L,
        lastRelayedTimestampMs = 0L
    ),
    val showFederationSheet: Boolean = false,
    val generatedFederationInvite: String? = null,

    // Sprint 24 Mesh Airtime QoS Traffic Shaping & Preemption HUD (ICA-2403)
    val qosSnapshot: org.nowni.intercom_alpha.mesh.qos.QoSSnapshot = org.nowni.intercom_alpha.mesh.qos.QoSSnapshot(),
    val showQoSSheet: Boolean = false,

    // Sprint 25 Automated Multi-Path Mesh Failover & Handover HUD (ICA-2503)
    val failoverSnapshot: org.nowni.intercom_alpha.mesh.failover.FailoverMetrics = org.nowni.intercom_alpha.mesh.failover.FailoverMetrics(),
    val showFailoverSheet: Boolean = false,

    // Sprint 32 Tactile Sub-Screens & Overlays (ICA-3202)
    val showDiagnosticsSheet: Boolean = false,

    // Sprint 33 Dynamic Adaptive Mesh Bitrate Tuning (ICA-3303)
    val linkQualityHealth: org.nowni.intercom_alpha.mesh.ClusterLinkHealth = org.nowni.intercom_alpha.mesh.ClusterLinkHealth(
        peerCount = 0,
        averageScore = 100,
        worstScore = 100,
        bestScore = 100,
        overallGrade = org.nowni.intercom_alpha.mesh.LinkQualityGrade.EXCELLENT,
        peerScores = emptyList()
    ),
    val bitrateTelemetry: org.nowni.intercom_alpha.audio.BitrateEngineTelemetry = org.nowni.intercom_alpha.audio.BitrateEngineTelemetry(
        activeProfile = org.nowni.intercom_alpha.common.AudioProfile.MEDIUM,
        effectiveBitrateKbps = 32,
        effectiveFrameDurationMs = 20,
        candidateProfile = org.nowni.intercom_alpha.common.AudioProfile.MEDIUM,
        consecutivePristineCycles = 0,
        hysteresisThresholdCycles = 3,
        isPinned = false,
        clusterPolicy = org.nowni.intercom_alpha.audio.ClusterPolicy.WORST_LINK,
        governingScore = 100,
        governingGrade = org.nowni.intercom_alpha.mesh.LinkQualityGrade.EXCELLENT,
        lastTransitionReason = "Initial baseline",
        timestampMs = 0L
    )
)

sealed interface HomeUiAction {
    data object StartTransmitting : HomeUiAction
    data object StopTransmitting : HomeUiAction
    data class SelectAudioProfile(val profile: AudioProfile) : HomeUiAction
    data object LeaveGroup : HomeUiAction
    data class ToggleShareDialog(val show: Boolean) : HomeUiAction
    data class ToggleJoinDialog(val show: Boolean) : HomeUiAction
    data class JoinGroup(val inviteCode: String) : HomeUiAction
    data object ClearError : HomeUiAction

    // v2.0 Cockpit Actions
    data class SelectChannel(val channelId: Int) : HomeUiAction
    data class ToggleDualWatch(val channelId: Int) : HomeUiAction
    data object ToggleScanMode : HomeUiAction
    data class SendCannedHazard(val code: CannedMessageCode) : HomeUiAction
    data class SendCustomText(val content: String) : HomeUiAction
    data object MarkMessagesAsRead : HomeUiAction
    data object TriggerSos : HomeUiAction
    data object ClearEmergency : HomeUiAction
    data class ToggleMessagesDialog(val show: Boolean) : HomeUiAction
    data object ToggleVoxLock : HomeUiAction
    data object TriggerRepeaterPing : HomeUiAction

    // Sprint 18 Trail Map Actions
    data object CycleRadarZoom : HomeUiAction
    data class SetRadarZoom(val zoom: RadarRangeZoom) : HomeUiAction
    data class ToggleTrailOverlay(val enabled: Boolean) : HomeUiAction
    data class ToggleTrailSheet(val show: Boolean) : HomeUiAction
    data object LoadSampleTrail : HomeUiAction

    // Sprint 19 Trail File Sharing Actions (ICA-1902)
    data class BeamActiveTrail(val type: FileTransferType = FileTransferType.GPX) : HomeUiAction
    data class CancelTransfer(val transferId: String) : HomeUiAction
    data class ImportTrailText(val text: String, val fileName: String, val format: FileTransferType = FileTransferType.GPX) : HomeUiAction
    data class SelectStoredTrail(val trailId: String) : HomeUiAction
    data class DeleteStoredTrail(val trailId: String) : HomeUiAction
    data class ToggleTrailManagerSheet(val show: Boolean) : HomeUiAction

    // Sprint 20 Geofence & Power Governor Actions (ICA-2003)
    data class SetPowerPolicyOverride(val policy: org.nowni.intercom_alpha.power.PowerPolicy?) : HomeUiAction
    data class SetGeofenceLeadRider(val leadPeerId: String?) : HomeUiAction

    // Sprint 21 Crash Impact & Voice Command Actions (ICA-2103)
    data object CancelCrashCountdown : HomeUiAction
    data object TriggerManualCrashSos : HomeUiAction
    data object DismissVoiceCommandBadge : HomeUiAction

    // Sprint 22 Directed Mesh Routing Actions (ICA-2203)
    data class ToggleRoutingDiagnosticsSheet(val show: Boolean) : HomeUiAction
    data class InitiateRouteDiscovery(val destinationId: String) : HomeUiAction

    // Sprint 23 Multi-Group Inter-Convoy Federation Actions (ICA-2303)
    data class ToggleFederationSheet(val show: Boolean) : HomeUiAction
    data class SetFederationPolicy(val groupId: String, val policy: org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy) : HomeUiAction
    data object CreateFederationInvite : HomeUiAction
    data class AcceptFederationInvite(val inviteCode: String) : HomeUiAction
    data object DismissFederationInviteDialog : HomeUiAction

    // Sprint 24 Mesh Airtime QoS Traffic Shaping Actions (ICA-2403)
    data class ToggleQoSSheet(val show: Boolean) : HomeUiAction

    // Sprint 25 Automated Multi-Path Failover Actions (ICA-2503)
    data class ToggleFailoverSheet(val show: Boolean) : HomeUiAction
    data class SetFailoverModeOverride(val mode: org.nowni.intercom_alpha.mesh.failover.FailoverMode?) : HomeUiAction

    // Sprint 32 Tactile Diagnostics Action (ICA-3202)
    data class ToggleDiagnosticsSheet(val show: Boolean) : HomeUiAction

    // Sprint 33 Dynamic Adaptive Bitrate Actions (ICA-3303)
    data class PinAudioProfile(val profile: org.nowni.intercom_alpha.common.AudioProfile?) : HomeUiAction
    data class SetClusterPolicy(val policy: org.nowni.intercom_alpha.audio.ClusterPolicy) : HomeUiAction
}

sealed interface HomeUiEvent {
    data class ShowToast(val message: String) : HomeUiEvent
    data object NavigateToGroupCreate : HomeUiEvent
}
