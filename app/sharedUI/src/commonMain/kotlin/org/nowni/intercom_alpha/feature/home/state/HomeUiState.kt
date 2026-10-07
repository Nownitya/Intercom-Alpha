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
    val showTrailManagerSheet: Boolean = false
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
}

sealed interface HomeUiEvent {
    data class ShowToast(val message: String) : HomeUiEvent
    data object NavigateToGroupCreate : HomeUiEvent
}
