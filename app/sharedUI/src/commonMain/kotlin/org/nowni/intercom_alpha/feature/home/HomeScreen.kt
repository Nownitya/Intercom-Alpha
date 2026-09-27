package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with Group & Quick Actions
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
                        text = "BLE Mesh — ${uiState.activeProfile.name}",
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

            Spacer(modifier = Modifier.height(16.dp))

            AudioLevelMeter(
                label = "Microphone In",
                level = if (uiState.isTransmitting) 0.70f else uiState.inputLevel
            )
            AudioLevelMeter(
                label = "Speaker Out",
                level = uiState.outputLevel
            )
        }

        PttButton(
            isPressed = uiState.isTransmitting,
            onPressStart = { onAction(HomeUiAction.StartTransmitting) },
            onPressEnd = { onAction(HomeUiAction.StopTransmitting) }
        )

        PeerList(
            peers = uiState.peers,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }

    // QR Share Dialog
    if (uiState.showShareDialog) {
        QrShareDialog(
            groupName = uiState.groupName,
            inviteCode = uiState.inviteCode,
            onDismiss = { onAction(HomeUiAction.ToggleShareDialog(false)) }
        )
    }

    // QR Join Dialog
    if (uiState.showJoinDialog) {
        QrJoinDialog(
            onJoin = { onAction(HomeUiAction.JoinGroup(it)) },
            onDismiss = { onAction(HomeUiAction.ToggleJoinDialog(false)) }
        )
    }
}
