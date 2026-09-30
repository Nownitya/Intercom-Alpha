package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.ui.components.*

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Emergency Preemption Banner
            EmergencyPreemptionBanner(
                isEmergencyActive = uiState.isEmergencyActive,
                blackboxFrameCount = uiState.blackboxBufferedCount,
                onTriggerSos = { onAction(HomeUiAction.TriggerSos) },
                onClearEmergency = { onAction(HomeUiAction.ClearEmergency) }
            )

            // 2. Header with Group Name & Profile & Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.groupName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "BLE Mesh — Codec: ${uiState.activeProfile.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onAction(HomeUiAction.ToggleShareDialog(true)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Share QR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = { onAction(HomeUiAction.ToggleJoinDialog(true)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Join", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 3. Multi-Channel Selector Bar with Dual-Watch & Scan
            ChannelSelectorBar(
                channelState = uiState.channelState,
                onSelectChannel = { onAction(HomeUiAction.SelectChannel(it)) },
                onToggleDualWatch = { onAction(HomeUiAction.ToggleDualWatch(it)) },
                onToggleScanMode = { onAction(HomeUiAction.ToggleScanMode) }
            )

            // 4. Proximity Radar & Convoy GPS Telemetry
            ProximityRadarView(
                radarSnapshot = uiState.radarSnapshot,
                convoySnapshot = uiState.convoySnapshot
            )

            // 5. Audio Level Meters
            Column(modifier = Modifier.fillMaxWidth()) {
                AudioLevelMeter(
                    label = "Microphone In",
                    level = if (uiState.isTransmitting) 0.70f else uiState.inputLevel
                )
                AudioLevelMeter(
                    label = "Speaker Out",
                    level = uiState.outputLevel
                )
            }

            // 6. Push-To-Talk Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                PttButton(
                    isPressed = uiState.isTransmitting,
                    onPressStart = { onAction(HomeUiAction.StartTransmitting) },
                    onPressEnd = { onAction(HomeUiAction.StopTransmitting) }
                )
            }

            // 7. Quick Hazard Alert Bar & Mesh Text Access
            QuickHazardBar(
                unreadCount = uiState.unreadTextCount,
                onSendHazard = { onAction(HomeUiAction.SendCannedHazard(it)) },
                onOpenMessages = { onAction(HomeUiAction.ToggleMessagesDialog(true)) },
                onTriggerSos = { onAction(HomeUiAction.TriggerSos) }
            )

            // 8. Active Group Peers List
            PeerList(
                peers = uiState.peers,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
            )
        }

        // Dialogs
        if (uiState.showShareDialog) {
            QrShareDialog(
                groupName = uiState.groupName,
                inviteCode = uiState.inviteCode,
                onDismiss = { onAction(HomeUiAction.ToggleShareDialog(false)) }
            )
        }

        if (uiState.showJoinDialog) {
            QrJoinDialog(
                onJoin = { onAction(HomeUiAction.JoinGroup(it)) },
                onDismiss = { onAction(HomeUiAction.ToggleJoinDialog(false)) }
            )
        }

        if (uiState.showMessagesDialog) {
            MessageHistoryDialog(
                messages = uiState.messages,
                onSendCustomText = { onAction(HomeUiAction.SendCustomText(it)) },
                onDismiss = { onAction(HomeUiAction.ToggleMessagesDialog(false)) }
            )
        }
    }
}
