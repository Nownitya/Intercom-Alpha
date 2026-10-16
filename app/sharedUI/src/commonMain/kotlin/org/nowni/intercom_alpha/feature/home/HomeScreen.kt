package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import org.nowni.intercom_alpha.ui.theme.IntercomTokens

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
                            onClick = { onAction(HomeUiAction.ToggleTrailManagerSheet(true)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (uiState.activeTransfers.isNotEmpty()) Color(0xFF1B3B32) else Color(0xFF1D2023),
                                contentColor = if (uiState.activeTransfers.isNotEmpty()) Color(0xFF00E676) else Color(0xFF00E5FF)
                            )
                        ) {
                            Text(
                                if (uiState.activeTransfers.isNotEmpty()) "OTA (${uiState.activeTransfers.size})" else "Routes",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
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

                // Offline Voice Command Feedback Badge (Sprint 21 - ICA-2103)
                if (uiState.latestVoiceCommand != null) {
                    val cmd = uiState.latestVoiceCommand
                    Surface(
                        onClick = { onAction(HomeUiAction.DismissVoiceCommandBadge) },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0B2228),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🎙️", fontSize = 11.sp)
                                Text(
                                    text = "VOICE: ${cmd.command.phrase.uppercase()}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00E5FF)
                                )
                                Text(
                                    text = "(${kotlin.math.round(cmd.confidence * 100).toInt()}%)",
                                    fontSize = 9.sp,
                                    color = Color(0xFF80DEEA)
                                )
                            }
                            Text("✕", fontSize = 10.sp, color = Color(0xFF80DEEA))
                        }
                    }
                }

                // Directed AODV-Lite Mesh Routing Strip (Sprint 22 - ICA-2203)
                Surface(
                    onClick = { onAction(HomeUiAction.ToggleRoutingDiagnosticsSheet(true)) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F1A15),
                    border = BorderStroke(1.dp, Color(0xFF00E676))
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
                            Text("⚡", fontSize = 11.sp)
                            Text(
                                text = "AODV ROUTING: ${uiState.routingSnapshot.activeRoutes.size} ROUTES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            val savings = kotlin.math.round(uiState.routingSnapshot.airtimeSavingsPercentage).toInt()
                            Text(
                                text = "• $savings% RF SAVED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${uiState.routingSnapshot.totalDirectedPackets} DIR / ${uiState.routingSnapshot.totalFloodedPackets} FLD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1B5E20)
                            ) {
                                Text(
                                    text = "DIAG",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Multi-Group Federation Strip (Sprint 23 - ICA-2303)
                Surface(
                    onClick = { onAction(HomeUiAction.ToggleFederationSheet(true)) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF101A24),
                    border = BorderStroke(1.dp, Color(0xFF29B6F6))
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
                            Text("🌐", fontSize = 11.sp)
                            Text(
                                text = "FEDERATION: ${uiState.federationSnapshot.federatedGroups.size} SQUADS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (uiState.federationSnapshot.localRole == org.nowni.intercom_alpha.mesh.federation.GatewayRole.PRIMARY) Color(0xFF0288D1) else Color(0xFF455A64)
                            ) {
                                Text(
                                    text = uiState.federationSnapshot.localRole.name,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "• ${uiState.federationSnapshot.activePolicy.name}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81D4FA)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${uiState.federationSnapshot.totalRelayedPacketsIn}↓ / ${uiState.federationSnapshot.totalRelayedPacketsOut}↑",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF80D8FF)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF0277BD)
                            ) {
                                Text(
                                    text = "HUD",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Mesh Airtime QoS Traffic Shaping Strip (Sprint 24 - ICA-2403)
                val qos = uiState.qosSnapshot
                Surface(
                    onClick = { onAction(HomeUiAction.ToggleQoSSheet(true)) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (qos.isChannelCongested) Color(0xFF2A1010) else Color(0xFF131B24),
                    border = BorderStroke(1.dp, if (qos.isChannelCongested) Color(0xFFFF5252) else (if (qos.estimatedAirtimeUtilizationPercent > 50) Color(0xFFFFB300) else Color(0xFF00E5FF)))
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
                            Text("📊", fontSize = 11.sp)
                            Text(
                                text = "QoS AIRTIME: ${qos.estimatedAirtimeUtilizationPercent}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (qos.isChannelCongested) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = Color(0xFFD32F2F)
                                ) {
                                    Text(
                                        text = "CONGESTED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else if (qos.voicePreemptionCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = Color(0xFF0288D1)
                                ) {
                                    Text(
                                        text = "PREEMPT (${qos.voicePreemptionCount})",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (qos.bulkThrottleActive) {
                                Text(
                                    text = "• THROTTLED",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Q:${qos.totalPendingQueueDepth} • DR:${qos.totalPacketsDropped}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (qos.totalPacketsDropped > 0) Color(0xFFFF5252) else Color(0xFF80D8FF)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (qos.isChannelCongested) Color(0xFFB71C1C) else Color(0xFF00838F)
                            ) {
                                Text(
                                    text = "QoS",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Multi-Path Mesh Failover & Handover Strip (Sprint 25 - ICA-2503)
                val failover = uiState.failoverSnapshot
                Surface(
                    onClick = { onAction(HomeUiAction.ToggleFailoverSheet(true)) },
                    shape = RoundedCornerShape(6.dp),
                    color = when (failover.currentMode) {
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF0D2319)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFF281E0D)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFF20132B)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF1E1E1E)
                    },
                    border = BorderStroke(1.dp, when (failover.currentMode) {
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF00E676)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFFFFB300)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFFB388FF)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF757575)
                    })
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
                            Text(
                                text = when (failover.currentMode) {
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> "📡"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> "⚡"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> "☁️"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> "🚫"
                                },
                                fontSize = 11.sp
                            )
                            Text(
                                text = when (failover.currentMode) {
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> "PATH: BLE DIRECT (OFFLINE)"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> "PATH: HEDGED DUAL-STREAM"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> "PATH: CLOUD RELAY (SFU)"
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> "PATH: RESTRICTED OFFLINE"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ETX:${(failover.averageEtx * 10).toInt() / 10.0} • H:${failover.totalHandovers}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (failover.isBleDegraded) Color(0xFFFFB300) else Color(0xFFB9F6CA)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (failover.currentMode) {
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF007E33)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFFFF8800)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFF7C4DFF)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF424242)
                                }
                            ) {
                                Text(
                                    text = when (failover.currentMode) {
                                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> "BLE"
                                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> "HEDGED"
                                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> "CLOUD"
                                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> "OFFLINE"
                                    },
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

            // ================= 2. EMERGENCY PREEMPTION BANNER =================
            if (uiState.isEmergencyActive) {
                EmergencyPreemptionBanner(
                    isEmergencyActive = true,
                    blackboxFrameCount = uiState.blackboxBufferedCount,
                    onTriggerSos = { onAction(HomeUiAction.TriggerSos) },
                    onClearEmergency = { onAction(HomeUiAction.ClearEmergency) }
                )
            }

            // ================= 2B. GEOFENCE TETHER BREACH / SPLIT BANNER =================
            if (uiState.geofenceSnapshot.overallStatus == org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.BREACH ||
                uiState.geofenceSnapshot.splitWarning != null
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF33080C),
                    border = BorderStroke(1.dp, Color(0xFFFF1744))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚠️", fontSize = 14.sp)
                            Column {
                                val split = uiState.geofenceSnapshot.splitWarning
                                val title = if (split != null) {
                                    "CONVOY SPLIT DETECTED (${split.clusterGapMeters.toInt()}m GAP)"
                                } else {
                                    "TETHER BREACH (>600m FROM LEAD)"
                                }
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFFF5252)
                                )
                                Text(
                                    text = "Lead: ${uiState.geofenceSnapshot.furthestRiderDistanceMeters.toInt()}m | Check radar vectors",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // ================= 3. TACTICAL CHANNEL SELECTOR DIAL =================
            TactileChannelDial(
                activeChannelId = uiState.channelState.activeChannelId,
                monitoredChannelIds = uiState.channelState.monitoredChannelIds,
                onSelectChannel = { onAction(HomeUiAction.SelectChannel(it)) },
                onToggleDualWatch = { onAction(HomeUiAction.ToggleDualWatch(it)) }
            )

            // ================= 4. COMPACT CONVOY SPATIAL RADAR =================
            ProximityRadarView(
                radarSnapshot = uiState.radarSnapshot,
                convoySnapshot = uiState.convoySnapshot,
                trailMapSnapshot = uiState.trailMapSnapshot,
                geofenceSnapshot = uiState.geofenceSnapshot,
                powerGovernorState = uiState.powerGovernorState,
                onCycleZoom = { onAction(HomeUiAction.CycleRadarZoom) },
                onOpenTrailSheet = { onAction(HomeUiAction.ToggleTrailSheet(true)) }
            )

            // ================= 5. MASTER TACTICAL PTT & SENSORY CONTROLS =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Guarded Emergency SOS Switch
                GuardedSosSwitch(
                    isEmergencyActive = uiState.isEmergencyActive,
                    onTriggerEmergency = { onAction(HomeUiAction.TriggerSos) },
                    onCancelEmergency = { onAction(HomeUiAction.TriggerSos) }
                )

                // Center: 160dp Master Expressive PTT Actuator
                ExpressivePttActuator(
                    isTransmitting = uiState.isTransmitting,
                    onPressStart = { onAction(HomeUiAction.StartTransmitting) },
                    onPressEnd = { onAction(HomeUiAction.StopTransmitting) },
                    channelLabel = "CH 0${uiState.channelState.activeChannelId}",
                    subtitle = if (uiState.isVoxLocked) "VOX LOCK" else "HOLD PTT"
                )

                // Right: Audio VU Level Meter & VOX Lock Toggle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveVuMeter(
                        currentRmsLevel = uiState.inputLevel,
                        barCount = 10,
                        showDbLabel = true
                    )

                    Surface(
                        onClick = { onAction(HomeUiAction.ToggleVoxLock) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isVoxLocked) Color(0xFF2E1912) else Color(0xFF1E242C),
                        border = BorderStroke(1.dp, if (uiState.isVoxLocked) IntercomTokens.SignalAmber else Color(0x33FFFFFF))
                    ) {
                        Text(
                            text = if (uiState.isVoxLocked) "VOX ON" else "VOX OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isVoxLocked) IntercomTokens.SignalAmber else Color(0xFF8B949E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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

        // ================= CRASH COUNTDOWN DIALOG (Sprint 21 - ICA-2103) =================
        if (uiState.showCrashCountdownDialog) {
            CrashCountdownDialog(
                snapshot = uiState.crashSnapshot,
                onCancel = { onAction(HomeUiAction.CancelCrashCountdown) },
                onSosNow = { onAction(HomeUiAction.TriggerManualCrashSos) }
            )
        }

        // ================= ROUTING DIAGNOSTICS DIALOG (Sprint 22 - ICA-2203) =================
        if (uiState.showRoutingDiagnosticsSheet) {
            RoutingDiagnosticsDialog(
                snapshot = uiState.routingSnapshot,
                onDismiss = { onAction(HomeUiAction.ToggleRoutingDiagnosticsSheet(false)) },
                onDiscoverRoute = { destId -> onAction(HomeUiAction.InitiateRouteDiscovery(destId)) }
            )
        }

        // ================= FEDERATION DIAGNOSTICS DIALOG (Sprint 23 - ICA-2303) =================
        if (uiState.showFederationSheet) {
            FederationDiagnosticsDialog(
                snapshot = uiState.federationSnapshot,
                generatedInvite = uiState.generatedFederationInvite,
                onDismiss = { onAction(HomeUiAction.ToggleFederationSheet(false)) },
                onSetPolicy = { gid, policy -> onAction(HomeUiAction.SetFederationPolicy(gid, policy)) },
                onCreateInvite = { onAction(HomeUiAction.CreateFederationInvite) },
                onAcceptInvite = { code -> onAction(HomeUiAction.AcceptFederationInvite(code)) },
                onDismissInvite = { onAction(HomeUiAction.DismissFederationInviteDialog) }
            )
        }

        // ================= QoS DIAGNOSTICS DIALOG (Sprint 24 - ICA-2403) =================
        if (uiState.showQoSSheet) {
            QoSDiagnosticsDialog(
                snapshot = uiState.qosSnapshot,
                onDismiss = { onAction(HomeUiAction.ToggleQoSSheet(false)) }
            )
        }

        // ================= MULTI-PATH FAILOVER DIAGNOSTICS DIALOG (Sprint 25 - ICA-2503) =================
        if (uiState.showFailoverSheet) {
            FailoverDiagnosticsDialog(
                snapshot = uiState.failoverSnapshot,
                onDismiss = { onAction(HomeUiAction.ToggleFailoverSheet(false)) },
                onSetOverride = { mode -> onAction(HomeUiAction.SetFailoverModeOverride(mode)) }
            )
        }
    }
}

@Composable
fun RoutingDiagnosticsDialog(
    snapshot: org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot,
    onDismiss: () -> Unit,
    onDiscoverRoute: (String) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10151B),
            border = BorderStroke(1.dp, Color(0xFF00E5FF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ AODV-LITE ROUTING HUD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 0.5.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Text("✕", fontSize = 12.sp, color = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF17202A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val savings = kotlin.math.round(snapshot.airtimeSavingsPercentage).toInt()
                        Text(
                            text = "AIRTIME SAVINGS OVER FLOODING: $savings%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = "Directed Packets: ${snapshot.totalDirectedPackets} • Flooded: ${snapshot.totalFloodedPackets}",
                            fontSize = 10.sp,
                            color = Color(0xFFB0BEC5)
                        )
                    }
                }

                Text(
                    text = "ACTIVE ROUTING ENTRIES (${snapshot.activeRoutes.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90A4AE)
                )

                if (snapshot.activeRoutes.isEmpty()) {
                    Text(
                        text = "No active directed routes currently established. Unicast data frames initiate on-demand RREQ discovery automatically.",
                        fontSize = 10.sp,
                        color = Color(0xFF78909C)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (route in snapshot.activeRoutes) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF141A22),
                                border = BorderStroke(0.5.dp, Color(0xFF263238)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "DEST: ${route.destinationId}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Next: ${route.nextHopId} • Hops: ${route.hopCount} • ETX: ${route.metricCost}",
                                            fontSize = 9.sp,
                                            color = Color(0xFF80DEEA)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (route.isValid) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                    ) {
                                        Text(
                                            text = if (route.isValid) "ACTIVE" else "INVALID",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("CLOSE HUD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CrashCountdownDialog(
    snapshot: org.nowni.intercom_alpha.safety.CrashCountdownSnapshot,
    onCancel: () -> Unit,
    onSosNow: () -> Unit
) {
    val peakG = kotlin.math.round(snapshot.peakGForce * 10) / 10.0
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A0A0A),
            border = BorderStroke(2.dp, Color(0xFFFF1744)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "⚠️ VEHICULAR CRASH DETECTED",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFF1744),
                    letterSpacing = 1.sp
                )

                Text(
                    text = "${snapshot.remainingSeconds}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "SEVERITY: ${snapshot.severity.name} • PEAK: ${peakG}g",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8A80)
                )

                Text(
                    text = "Automatic eCall mesh emergency alert dispatching in ${snapshot.remainingSeconds}s unless aborted.",
                    fontSize = 11.sp,
                    color = Color(0xFFCFD8DC),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = "I AM OK — CANCEL ALERT",
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onSosNow,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text(
                        text = "DISPATCH SOS NOW",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FederationDiagnosticsDialog(
    snapshot: org.nowni.intercom_alpha.mesh.federation.FederationSnapshot,
    generatedInvite: String?,
    onDismiss: () -> Unit,
    onSetPolicy: (String, org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy) -> Unit,
    onCreateInvite: () -> Unit,
    onAcceptInvite: (String) -> Unit,
    onDismissInvite: () -> Unit
) {
    var inviteInputText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10151B),
            border = BorderStroke(1.dp, Color(0xFF29B6F6)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🌐", fontSize = 14.sp)
                        Text(
                            text = "FEDERATION GATEWAY HUD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF29B6F6),
                            letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Text("✕", fontSize = 12.sp, color = Color.White)
                    }
                }

                // Gateway Node Status Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF17202A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LOCAL SQUAD: ${snapshot.localGroupId}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (snapshot.localRole == org.nowni.intercom_alpha.mesh.federation.GatewayRole.PRIMARY) Color(0xFF0288D1) else Color(0xFF546E7A)
                            ) {
                                Text(
                                    text = snapshot.localRole.name,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Relayed In: ${snapshot.totalRelayedPacketsIn} • Relayed Out: ${snapshot.totalRelayedPacketsOut} • Policy Drops: ${snapshot.totalDroppedPolicyViolations}",
                            fontSize = 9.sp,
                            color = Color(0xFF81D4FA)
                        )
                    }
                }

                // Federated Sibling Groups List
                Text(
                    text = "FEDERATED SIBLING CONVOYS (${snapshot.federatedGroups.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90A4AE)
                )

                if (snapshot.federatedGroups.isEmpty()) {
                    Text(
                        text = "No active sibling convoys connected. Create or accept a federation invite code below to link disparate squads.",
                        fontSize = 10.sp,
                        color = Color(0xFF78909C)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (grp in snapshot.federatedGroups) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF141A22),
                                border = BorderStroke(0.5.dp, Color(0xFF263238)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = grp.groupName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${grp.linkRssi} dBm",
                                            fontSize = 9.sp,
                                            color = Color(0xFF00E676)
                                        )
                                    }
                                    Text(
                                        text = "ID: ${grp.groupId} • Gateway: ${grp.gatewayPeerId} • Relayed: ${grp.relayedPacketsCount}",
                                        fontSize = 9.sp,
                                        color = Color(0xFFB0BEC5)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (policy in org.nowni.intercom_alpha.mesh.federation.CrossGroupPolicy.entries) {
                                            val isSelected = grp.policy == policy
                                            Surface(
                                                onClick = { onSetPolicy(grp.groupId, policy) },
                                                shape = RoundedCornerShape(3.dp),
                                                color = if (isSelected) Color(0xFF0288D1) else Color(0xFF263238)
                                            ) {
                                                Text(
                                                    text = policy.name.replace("_", " ").take(10),
                                                    fontSize = 7.sp,
                                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Generated Invite Display
                if (generatedInvite != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF003756),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "ACTIVE FEDERATION INVITE CODE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80D8FF)
                            )
                            Text(
                                text = generatedInvite,
                                fontSize = 8.sp,
                                color = Color.White
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(
                                    onClick = onDismissInvite,
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("DONE", fontSize = 8.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Action Buttons: Create Invite & Accept Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateInvite,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("CREATE INVITE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = inviteInputText,
                        onValueChange = { inviteInputText = it },
                        placeholder = { Text("Paste FEDERATION:v1: code...", fontSize = 9.sp, color = Color(0xFF78909C)) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (inviteInputText.isNotBlank()) {
                                onAcceptInvite(inviteInputText.trim())
                                inviteInputText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("JOIN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun QoSDiagnosticsDialog(
    snapshot: org.nowni.intercom_alpha.mesh.qos.QoSSnapshot,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10151B),
            border = BorderStroke(1.dp, if (snapshot.isChannelCongested) Color(0xFFFF5252) else Color(0xFF00E5FF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📊", fontSize = 14.sp)
                        Text(
                            text = "MESH AIRTIME & QoS HUD",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (snapshot.isChannelCongested) Color(0xFFFF5252) else Color(0xFF00E5FF),
                            letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Text("✕", fontSize = 12.sp, color = Color.White)
                    }
                }

                // Airtime Utilization Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF17202A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "AIRTIME UTILIZATION",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB0BEC5)
                            )
                            Text(
                                text = "${snapshot.estimatedAirtimeUtilizationPercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (snapshot.estimatedAirtimeUtilizationPercent >= 75) Color(0xFFFF5252) else Color(0xFF00E676)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (snapshot.estimatedAirtimeUtilizationPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (snapshot.estimatedAirtimeUtilizationPercent >= 75) Color(0xFFFF5252) else (if (snapshot.estimatedAirtimeUtilizationPercent > 50) Color(0xFFFFB300) else Color(0xFF00E5FF)),
                            trackColor = Color(0xFF263238)
                        )
                    }
                }

                // Channel Congestion Alert
                if (snapshot.isChannelCongested) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF3E1212),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚠️", fontSize = 12.sp)
                            Text(
                                text = "CHANNEL CONGESTION ACTIVE: Voice preemption arbiter is throttling bulk transfers and dropping non-critical frames to protect audio latency.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFCDD2)
                            )
                        }
                    }
                }

                // 4-Tier Queue Depths Breakdown
                Text(
                    text = "QoS QUEUE DEPTHS (TOTAL: ${snapshot.totalPendingQueueDepth})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90A4AE)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141A22),
                    border = BorderStroke(0.5.dp, Color(0xFF263238)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1. Emergency Critical (SOS/Crash)", fontSize = 9.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                            Text("${snapshot.emergencyQueueDepth} pkts", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("2. Active Voice (Audio Streams)", fontSize = 9.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                            Text("${snapshot.voiceQueueDepth} pkts", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("3. Routing Control (AODV/Epoch)", fontSize = 9.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                            Text("${snapshot.controlQueueDepth} pkts", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("4. Bulk Telemetry (GPX/Status)", fontSize = 9.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                            Text("${snapshot.bulkQueueDepth} pkts", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Preemption & Drop Telemetry
                Text(
                    text = "ARBITER & LIFECYCLE COUNTERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90A4AE)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141A22),
                    border = BorderStroke(0.5.dp, Color(0xFF263238)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Enqueued / Dispatched", fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.totalPacketsEnqueued} / ${snapshot.totalPacketsDispatched}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Dropped Packets", fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.totalPacketsDropped}", fontSize = 9.sp, color = if (snapshot.totalPacketsDropped > 0) Color(0xFFFF5252) else Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voice Preemption Events", fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.voicePreemptionCount}", fontSize = 9.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bulk Pacing Throttle", fontSize = 9.sp, color = Color(0xFFB0BEC5))
                            Text(if (snapshot.bulkThrottleActive) "ACTIVE (<=10%)" else "OFF (NOMINAL)", fontSize = 9.sp, color = if (snapshot.bulkThrottleActive) Color(0xFFFFB300) else Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dismiss Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================= MULTI-PATH FAILOVER DIAGNOSTICS DIALOG (Sprint 25 - ICA-2503) =================

@Composable
fun FailoverDiagnosticsDialog(
    snapshot: org.nowni.intercom_alpha.mesh.failover.FailoverMetrics,
    onDismiss: () -> Unit,
    onSetOverride: (org.nowni.intercom_alpha.mesh.failover.FailoverMode?) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F141A),
            border = BorderStroke(1.dp, when (snapshot.currentMode) {
                org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF00E676)
                org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFFFFB300)
                org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFFB388FF)
                org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF757575)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = when (snapshot.currentMode) {
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> "📡"
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> "⚡"
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> "☁️"
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> "🚫"
                            },
                            fontSize = 20.sp
                        )
                        Column {
                            Text(
                                text = "MULTI-PATH HANDOVER",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "AUTONOMOUS RF / SFU ARBITRATION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Active Mode Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (snapshot.currentMode) {
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF003314)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFF332000)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFF26103D)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF212121)
                    },
                    border = BorderStroke(1.dp, when (snapshot.currentMode) {
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF00E676)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFFFFB300)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFFB388FF)
                        org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFF757575)
                    }),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE ROUTING MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFB0BEC5)
                            )
                            Text(
                                text = snapshot.currentMode.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = when (snapshot.currentMode) {
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> Color(0xFF00E676)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> Color(0xFFFFB300)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> Color(0xFFB388FF)
                                    org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> Color(0xFFE0E0E0)
                                }
                            )
                        }
                        Text(
                            text = when (snapshot.currentMode) {
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT -> "Local BLE mesh is nominal. Zero cellular airtime or cloud bandwidth is consumed."
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.MULTI_PATH_HEDGED -> "RF degradation detected. Audio packets are dual-cast over BLE and Cloud with receiver deduplication to prevent dropouts."
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY -> "BLE mesh unreachable (convoy split / out-of-range). Routing 100% via Ktor Cloud Relay WebSocket."
                                org.nowni.intercom_alpha.mesh.failover.FailoverMode.RESTRICTED_OFFLINE -> "Zero connectivity. Operating best-effort offline."
                            },
                            fontSize = 10.sp,
                            color = Color(0xFFCFD8DC)
                        )
                    }
                }

                // Telemetry Matrix Card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF151C24),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("TELEMETRY & LINK METRICS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF80D8FF))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("BLE Mesh Peers in Range", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.meshPeerCount}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Average Expected Transmissions (ETX)", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text("${(snapshot.averageEtx * 100).toInt() / 100.0}", fontSize = 10.sp, color = if (snapshot.isBleDegraded) Color(0xFFFFB300) else Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Packet Delivery Ratio (PDR)", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text("${(snapshot.averageDeliveryRatio * 100).toInt()}%", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cloud Relay Status", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text(if (snapshot.isCloudConnected) "CONNECTED" else "DISCONNECTED", fontSize = 10.sp, color = if (snapshot.isCloudConnected) Color(0xFF00E676) else Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Handover Transitions", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.totalHandovers}", fontSize = 10.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dual-Stream Hedged Frames", fontSize = 10.sp, color = Color(0xFFB0BEC5))
                            Text("${snapshot.hedgedPacketsCount}", fontSize = 10.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Manual Override Controls
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MANUAL ROUTING OVERRIDE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFFB0BEC5))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { onSetOverride(org.nowni.intercom_alpha.mesh.failover.FailoverMode.BLE_DIRECT) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("BLE ONLY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onSetOverride(org.nowni.intercom_alpha.mesh.failover.FailoverMode.CLOUD_RELAY_ONLY) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A148C)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("CLOUD ONLY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onSetOverride(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Close Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("CLOSE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

