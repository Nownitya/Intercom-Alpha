package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.feature.home.state.HomeUiState
import org.nowni.intercom_alpha.ui.components.*

/**
 * The Master Zero-Scroll Tactical Motorcycle Cockpit HUD for Intercom-Alpha.
 * Engineered for single-viewport RAM-mount operation and coarse leather riding gloves.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateToRepeater: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D10))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= 1. TOP AVIONICS TELEMETRY HEADER =================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Primary App Status Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Text(
                            text = "TAC-MESH // COMMS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFFFFB59E)
                        )
                        Text(
                            text = "ALPHA-01",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Top Quick Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = { onAction(HomeUiAction.ToggleTrailSheet(true)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (uiState.trailMapSnapshot.isOverlayEnabled) Color(0xFF1B3B32) else Color(0xFF1D2023),
                                contentColor = if (uiState.trailMapSnapshot.isOverlayEnabled) Color(0xFF00E5FF) else Color(0xFFB0B8C1)
                            )
                        ) {
                            Text("Trail", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = onNavigateToRepeater,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1D2023),
                                contentColor = Color(0xFFFFB300)
                            )
                        ) {
                            Text("Relay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { onAction(HomeUiAction.ToggleShareDialog(true)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1D2023),
                                contentColor = Color(0xFF00E5FF)
                            )
                        ) {
                            Text("QR Code", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { onAction(HomeUiAction.ToggleJoinDialog(true)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1D2023),
                                contentColor = Color(0xFF00E676)
                            )
                        ) {
                            Text("Join", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Helmet Accessory & Mesh Cluster Status Bar
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF14181D),
                    border = BorderStroke(1.dp, Color(0xFF2B3848))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Helmet Accessory Info
                        val accessory = uiState.accessoryState
                        val deviceName = if (accessory.deviceName.isNotEmpty()) {
                            accessory.deviceName
                        } else {
                            "Cardo PackTalk Edge"
                        }
                        val batteryText = if (accessory.batteryPercent > 0) {
                            "🔋 ${accessory.batteryPercent}%"
                        } else {
                            "🔋 85%"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🎧", fontSize = 11.sp)
                            Text(
                                text = deviceName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "• $batteryText",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }

                        // Mesh Cluster & Transport Status
                        val peerCount = maxOf(uiState.peers.size, 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$peerCount PEERS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = if (uiState.isCloudBridgeConnected) "• SFU CLOUD" else "• BLE MESH",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB0B8C1)
                            )
                        }
                    }
                }

                // Basecamp Stationary Repeater Beacon Strip (Sprint 17 - ICA-1704)
                if (uiState.latestRepeaterBeacon != null) {
                    val beacon = uiState.latestRepeaterBeacon
                    Surface(
                        onClick = { onAction(HomeUiAction.TriggerRepeaterPing) },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF141C16),
                        border = BorderStroke(1.dp, Color(0xFF1B5E20))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("⛺", fontSize = 11.sp)
                                Text(
                                    text = beacon.repeaterName.ifBlank { "BASECAMP" },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "• 🔋 ${beacon.batteryPercent}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                                if (beacon.isSolarPowered) {
                                    Text(
                                        text = "• ☀️ SOLAR",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB300)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.repeaterPingLatencyMs != null) {
                                    Text(
                                        text = "RTT ${uiState.repeaterPingLatencyMs}ms",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF00E676)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (uiState.isPingingRepeater) Color(0xFF00E5FF) else Color(0xFF2E7D32)
                                ) {
                                    Text(
                                        text = if (uiState.isPingingRepeater) "PINGING..." else "PING",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 2. EMERGENCY PREEMPTION BANNER =================
            if (uiState.isEmergencyActive) {
                EmergencyPreemptionBanner(
                    isEmergencyActive = true,
                    blackboxFrameCount = uiState.blackboxBufferedCount,
                    onTriggerSos = { onAction(HomeUiAction.TriggerSos) },
                    onClearEmergency = { onAction(HomeUiAction.ClearEmergency) }
                )
            }

            // ================= 3. 16-CHANNEL SELECTOR RACK =================
            ChannelSelectorBar(
                channelState = uiState.channelState,
                onSelectChannel = { onAction(HomeUiAction.SelectChannel(it)) },
                onToggleDualWatch = { onAction(HomeUiAction.ToggleDualWatch(it)) },
                onToggleScanMode = { onAction(HomeUiAction.ToggleScanMode) }
            )

            // ================= 4. COMPACT CONVOY SPATIAL RADAR =================
            ProximityRadarView(
                radarSnapshot = uiState.radarSnapshot,
                convoySnapshot = uiState.convoySnapshot,
                trailMapSnapshot = uiState.trailMapSnapshot,
                onCycleZoom = { onAction(HomeUiAction.CycleRadarZoom) },
                onOpenTrailSheet = { onAction(HomeUiAction.ToggleTrailSheet(true)) }
            )

            // ================= 5. MASTER 140DP TACTICAL PTT ACTUATOR =================
            PttButton(
                isPressed = uiState.isTransmitting,
                onPressStart = { onAction(HomeUiAction.StartTransmitting) },
                onPressEnd = { onAction(HomeUiAction.StopTransmitting) },
                isVoxLocked = uiState.isVoxLocked,
                onToggleVoxLock = { onAction(HomeUiAction.ToggleVoxLock) },
                channelLabel = "CH 0${uiState.channelState.activeChannelId}"
            )

            // ================= 6. SINGLE-ROW 4-MACRO HAZARD STRIP =================
            QuickHazardBar(
                unreadCount = uiState.unreadTextCount,
                onSendHazard = { onAction(HomeUiAction.SendCannedHazard(it)) },
                onOpenMessages = { onAction(HomeUiAction.ToggleMessagesDialog(true)) },
                onTriggerSos = { onAction(HomeUiAction.TriggerSos) }
            )

            // ================= 7. PINNED EMERGENCY SOS OVERRIDE STRIP =================
            Surface(
                onClick = { onAction(HomeUiAction.TriggerSos) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2A0A0A),
                border = BorderStroke(1.dp, Color(0xFFFF1744))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🚨", fontSize = 14.sp)
                        Column {
                            Text(
                                text = "EMERGENCY SOS OVERRIDE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFFFF5252)
                            )
                            Text(
                                text = "PRIORITY-0 // BLACKBOX AUDIO CACHE ARMED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFCDD2)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFD32F2F)
                    ) {
                        Text(
                            text = "HOLD SOS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // ================= DIALOGS =================
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

        if (uiState.showTrailSheet) {
            OfflineTrailCockpitSheet(
                trailMapSnapshot = uiState.trailMapSnapshot,
                onCycleZoom = { onAction(HomeUiAction.CycleRadarZoom) },
                onToggleOverlay = { onAction(HomeUiAction.ToggleTrailOverlay(it)) },
                onLoadSampleTrail = { onAction(HomeUiAction.LoadSampleTrail) },
                onDismiss = { onAction(HomeUiAction.ToggleTrailSheet(false)) }
            )
        }
    }
}
