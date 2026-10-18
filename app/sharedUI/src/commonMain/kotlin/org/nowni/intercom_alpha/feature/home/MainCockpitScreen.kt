package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.ui.components.ExpressivePttActuator
import org.nowni.intercom_alpha.ui.components.TactileRadarCard
import org.nowni.intercom_alpha.ui.components.MessageHistoryDialog
import org.nowni.intercom_alpha.ui.components.OfflineTrailCockpitSheet
import org.nowni.intercom_alpha.ui.components.TrailFileManagerSheet
import org.nowni.intercom_alpha.ui.components.TactileDiagnosticsSheet
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.IntercomTacticalTypography
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Unified Main Cockpit Screen — S01.
 *
 * One screen, one job. Three regions:
 *   1. Status Strip — group, channel, battery, link quality
 *   2. Spatial Radar — 126dp canvas, rider blips, inline zoom
 *   3. PTT Actuator — 160dp primary action, claymorphic + skeuomorphic
 *   4. Bottom Navigation — Peers, Messages, SOS (3 tabs only)
 *
 * Designed for single-thumb motorcycle operation with heavy gloves.
 * No decorative filler. No redundant data. 84dp minimum touch targets.
 */
@Composable
fun MainCockpitScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateToRepeater: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IntercomTokens.CockpitObsidian)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================= 1. STATUS STRIP =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.groupName.uppercase(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                    Text(
                        text = "CH 04",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "🔋 ${uiState.batteryPercentage()}%",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFF8B949E)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0x33FFFFFF)
            )

            // ================= 2. SPATIAL RADAR =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                TactileRadarCard(
                    radarSnapshot = uiState.radarSnapshot,
                    convoySnapshot = uiState.convoySnapshot,
                    trailMapSnapshot = uiState.trailMapSnapshot,
                    onCycleZoom = { onAction(HomeUiAction.CycleRadarZoom) },
                    modifier = Modifier.size(126.dp)
                )
            }

            // ================= 3. PTT ACTUATOR (PRIMARY) =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                ExpressivePttActuator(
                    isTransmitting = uiState.isTransmitting,
                    onPressStart = { onAction(HomeUiAction.StartTransmitting) },
                    onPressEnd = { onAction(HomeUiAction.StopTransmitting) },
                    modifier = Modifier.size(160.dp),
                    channelLabel = "CH 04",
                    subtitle = "HOLD TO TALK"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================= 4. BOTTOM NAVIGATION =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CockpitNavTab(
                    icon = "📋",
                    label = "PEERS",
                    count = uiState.peers.size,
                    isSelected = false,
                    onClick = { onAction(HomeUiAction.ToggleMessagesDialog(true)) }
                )
                CockpitNavTab(
                    icon = "💬",
                    label = "MSGS",
                    count = uiState.unreadTextCount,
                    isSelected = false,
                    onClick = { onAction(HomeUiAction.ToggleMessagesDialog(true)) }
                )
                CockpitNavTab(
                    icon = "⚡",
                    label = "DIAG",
                    isSelected = uiState.showDiagnosticsSheet,
                    onClick = { onAction(HomeUiAction.ToggleDiagnosticsSheet(true)) }
                )
                CockpitNavTab(
                    icon = "🆘",
                    label = "SOS",
                    isSelected = false,
                    isEmergency = true,
                    onClick = { onAction(HomeUiAction.TriggerSos) }
                )
            }
        }

        // ================= OVERLAYS & SHEETS =================
        if (uiState.showMessagesDialog) {
            MessageHistoryDialog(
                messages = uiState.messages,
                onSendCustomText = { onAction(HomeUiAction.SendCustomText(it)) },
                onDismiss = { onAction(HomeUiAction.ToggleMessagesDialog(false)) }
            )
        }

        if (uiState.showDiagnosticsSheet) {
            TactileDiagnosticsSheet(
                failoverMetrics = uiState.failoverSnapshot,
                powerState = uiState.powerGovernorState,
                routingSnapshot = uiState.routingSnapshot,
                peers = uiState.peers,
                linkHealth = uiState.linkQualityHealth,
                bitrateTelemetry = uiState.bitrateTelemetry,
                onPinBitrate = { onAction(HomeUiAction.PinAudioProfile(it)) },
                fecTelemetry = uiState.fecTelemetry,
                plcTelemetry = uiState.plcTelemetry,
                onSetFecRatio = { onAction(HomeUiAction.SetFecRatio(it)) },
                onDismiss = { onAction(HomeUiAction.ToggleDiagnosticsSheet(false)) }
            )
        }

        if (uiState.showTrailSheet) {
            OfflineTrailCockpitSheet(
                trailMapSnapshot = uiState.trailMapSnapshot,
                onCycleZoom = { onAction(HomeUiAction.CycleRadarZoom) },
                onToggleOverlay = { onAction(HomeUiAction.ToggleTrailOverlay(it)) },
                onLoadSampleTrail = { onAction(HomeUiAction.LoadSampleTrail) },
                onOpenTrailManager = { onAction(HomeUiAction.ToggleTrailManagerSheet(true)) },
                onDismiss = { onAction(HomeUiAction.ToggleTrailSheet(false)) }
            )
        }

        if (uiState.showTrailManagerSheet) {
            TrailFileManagerSheet(
                storedTrails = uiState.storedTrails,
                activeTransfers = uiState.activeTransfers,
                activeTrailId = uiState.trailMapSnapshot.activeTrail?.id,
                onSelectTrail = { onAction(HomeUiAction.SelectStoredTrail(it)) },
                onBeamTrail = { onAction(HomeUiAction.BeamActiveTrail(it)) },
                onCancelTransfer = { onAction(HomeUiAction.CancelTransfer(it)) },
                onImportTrail = { name, text, format ->
                    onAction(HomeUiAction.ImportTrailText(text, name, format))
                },
                onDeleteTrail = { onAction(HomeUiAction.DeleteStoredTrail(it)) },
                onDismiss = { onAction(HomeUiAction.ToggleTrailManagerSheet(false)) }
            )
        }
    }
}

/**
 * Helper: returns battery percentage from UI state.
 * Falls back to 87% when power governor state is not yet available.
 */
private fun HomeUiState.batteryPercentage(): Int {
    return powerGovernorState.batteryPercent
}
