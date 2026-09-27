package org.nowni.intercom_alpha.feature.home.state

import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.Peer

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
    val errorMessage: String? = null
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
}

sealed interface HomeUiEvent {
    data class ShowToast(val message: String) : HomeUiEvent
    data object NavigateToGroupCreate : HomeUiEvent
}
