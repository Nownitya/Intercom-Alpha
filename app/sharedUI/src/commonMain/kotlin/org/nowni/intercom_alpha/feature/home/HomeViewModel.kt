package org.nowni.intercom_alpha.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.mesh.Peer

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            groupName = "Rider Club Alpha",
            inviteCode = "INTERCOM:v1:eyJncm91cElkIjoiZ3JwLTEwMSIsImdyb3VwTmFtZSI6IlJpZGVyIENsdWIgQWxwaGEiLCJsZWFkZXJJZCI6InBlZXItMSIsImV4cGlyZXNBdCI6NDkyMDA4ODQwMDAwMH0=",
            peers = listOf(
                Peer("peer-1", "Rider Alpha (Lead)", -55, 0L, true, AudioProfile.MEDIUM),
                Peer("peer-2", "Rider Bravo", -68, 0L, true, AudioProfile.MEDIUM)
            )
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.StartTransmitting -> {
                _uiState.update { it.copy(isTransmitting = true) }
            }

            HomeUiAction.StopTransmitting -> {
                _uiState.update { it.copy(isTransmitting = false) }
            }

            is HomeUiAction.SelectAudioProfile -> {
                _uiState.update { it.copy(activeProfile = action.profile) }
            }

            HomeUiAction.LeaveGroup -> {
                _uiState.update { it.copy(isGroupActive = false, peers = emptyList(), inviteCode = "") }
            }

            is HomeUiAction.ToggleShareDialog -> {
                _uiState.update { it.copy(showShareDialog = action.show) }
            }

            is HomeUiAction.ToggleJoinDialog -> {
                _uiState.update { it.copy(showJoinDialog = action.show) }
            }

            is HomeUiAction.JoinGroup -> {
                _uiState.update {
                    it.copy(
                        groupName = "Joined Mesh Group",
                        inviteCode = action.inviteCode,
                        isGroupActive = true,
                        showJoinDialog = false
                    )
                }
            }

            HomeUiAction.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }
}
