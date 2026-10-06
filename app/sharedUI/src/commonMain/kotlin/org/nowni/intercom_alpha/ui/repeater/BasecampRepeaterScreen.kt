package org.nowni.intercom_alpha.ui.repeater

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.mesh.RepeaterDutyCycleMode

/**
 * Basecamp Stationary Repeater UI & Dedicated Low-Power Cockpit Mode.
 *
 * Designed with a pure OLED black theme (#000000) for minimum power draw
 * when running continuously on mountain ridges, basecamps, or vehicle batteries.
 */
@Composable
fun BasecampRepeaterScreen(
    uiState: RepeaterUiState,
    onAction: (RepeaterUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.isNightStandbyLocked) {
        NightStandbyScreen(
            uiState = uiState,
            onUnlock = { onAction(RepeaterUiAction.ToggleNightStandby(false)) },
            modifier = modifier
        )
    } else {
        StandardRepeaterCockpit(
            uiState = uiState,
            onAction = onAction,
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    }
}

/**
 * Full-screen ultra-low-brightness OLED night standby display.
 * Shuts off all unnecessary pixels to minimize battery drain overnight.
 */
@Composable
private fun NightStandbyScreen(
    uiState: RepeaterUiState,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .clickable(onClick = onUnlock)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Faint status heartbeat
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (uiState.isRunning) Color(0xFF00E676) else Color(0xFFFF5252))
            )

            Text(
                text = "REPEATER STANDBY LOCK",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color(0xFF4A6572)
            )

            Text(
                text = uiState.repeaterName.uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = Color(0xFF81C784)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isSolarPowered) "☀️ SOLAR" else "🔋 BATT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB300)
                )
                Text(
                    text = "${uiState.batteryPercent}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "${uiState.packetsForwarded} PKTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB0BEC5)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0A0F14),
                border = BorderStroke(1.dp, Color(0xFF1B2836))
            ) {
                Text(
                    text = "TAP SCREEN TO UNLOCK COCKPIT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFF607D8B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Standard interactive cockpit view for configuring and monitoring the repeater station.
 */
@Composable
private fun StandardRepeaterCockpit(
    uiState: RepeaterUiState,
    onAction: (RepeaterUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= 1. HEADER BAR =================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onNavigateBack,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF14191F),
                            contentColor = Color(0xFF00E5FF)
                        )
                    ) {
                        Text("← HUD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isRunning) Color(0xFF00E676).copy(alpha = pulseAlpha)
                                    else Color(0xFFFF5252)
                                )
                        )
                        Text(
                            text = "STATION: ${uiState.repeaterId.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color(0xFFCFD8DC)
                        )
                    }

                    FilledTonalButton(
                        onClick = { onAction(RepeaterUiAction.ToggleNightStandby(true)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF14191F),
                            contentColor = Color(0xFFFFB300)
                        )
                    ) {
                        Text("🌙 STANDBY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Station Title & Duty Cycle Mode Selector
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0A0F14),
                    border = BorderStroke(1.dp, Color(0xFF1A2633))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = uiState.repeaterName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFE0E0E0)
                            )
                            Text(
                                text = uiState.statusMessage,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (uiState.isRunning) Color(0xFF00E676) else Color(0xFF90A4AE)
                            )
                        }

                        // Duty Cycle Chip (1-Tap Cycle)
                        val nextMode = when (uiState.dutyCycleMode) {
                            RepeaterDutyCycleMode.CONTINUOUS -> RepeaterDutyCycleMode.POWER_SAVER
                            RepeaterDutyCycleMode.POWER_SAVER -> RepeaterDutyCycleMode.EXTREME_SURVIVAL
                            RepeaterDutyCycleMode.EXTREME_SURVIVAL -> RepeaterDutyCycleMode.CONTINUOUS
                        }
                        val modeColor = when (uiState.dutyCycleMode) {
                            RepeaterDutyCycleMode.CONTINUOUS -> Color(0xFF00E676)
                            RepeaterDutyCycleMode.POWER_SAVER -> Color(0xFF00E5FF)
                            RepeaterDutyCycleMode.EXTREME_SURVIVAL -> Color(0xFFFFB300)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF121B24),
                            border = BorderStroke(1.dp, modeColor),
                            modifier = Modifier.clickable { onAction(RepeaterUiAction.SetDutyCycle(nextMode)) }
                        ) {
                            Text(
                                text = uiState.dutyCycleMode.name.replace("_", " "),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = modeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ================= 2. TELEMETRY & RF METRICS CARDS =================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Power & Solar Telemetry
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF080C10),
                    border = BorderStroke(1.dp, Color(0xFF1B2836))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BATTERY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
                            Text("${uiState.batteryPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("POWER SOURCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
                            Text(
                                if (uiState.isSolarPowered) "☀️ SOLAR" else "🔋 BATTERY",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isSolarPowered) Color(0xFFFFB300) else Color(0xFFCFD8DC)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATIONARY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
                            Text(
                                if (uiState.isStationary) "MOUNTED" else "MOBILE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784)
                            )
                        }
                    }
                }

                // Packet Relay Counters & Latency
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF080C10),
                    border = BorderStroke(1.dp, Color(0xFF1B2836))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MESH RF RELAY COUNTERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = Color(0xFF90A4AE)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn("FORWARDED", "${uiState.packetsForwarded}", Color(0xFF00E676))
                            MetricColumn("SUPPRESSED", "${uiState.packetsDroppedDuplicate}", Color(0xFFFFAB40))
                            MetricColumn("TTL DROPS", "${uiState.packetsDroppedTtlExpired}", Color(0xFFFF5252))
                            MetricColumn("RELAY RTT", "${uiState.lastForwardLatencyMs}ms", Color(0xFF00E5FF))
                        }
                    }
                }

                // Active Convoy Neighbors Roster
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF080C10),
                    border = BorderStroke(1.dp, Color(0xFF1B2836))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE CONVOY RIDERS HEARD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = Color(0xFF90A4AE)
                            )
                            Text(
                                text = "${uiState.activeNeighborCount} NODES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF)
                            )
                        }

                        if (uiState.activeNeighbors.isEmpty()) {
                            Text(
                                text = "Listening on BLE Mesh... No mobile riders currently in RF range.",
                                fontSize = 11.sp,
                                color = Color(0xFF546E7A),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(uiState.activeNeighbors) { neighborId ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF101820),
                                        border = BorderStroke(1.dp, Color(0xFF263238))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF00E676))
                                            )
                                            Text(
                                                text = neighborId,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFECEFF1)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= 3. ACTIONS & MASTER SWITCH =================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Diagnostic Ping & Beacon Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAction(RepeaterUiAction.TriggerDiagnosticPing) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF14202B),
                            contentColor = Color(0xFF00E5FF)
                        )
                    ) {
                        Text(
                            text = if (uiState.isPinging) "PINGING..."
                            else if (uiState.lastPingLatencyMs != null) "PING (${uiState.lastPingLatencyMs}ms)"
                            else "DIAGNOSTIC PING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = { onAction(RepeaterUiAction.EmitBeacon) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF14202B),
                            contentColor = Color(0xFFFFB300)
                        )
                    ) {
                        Text(
                            text = "EMIT BEACON (${uiState.beaconEmissionCount})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Master Relay Switch Button
                Button(
                    onClick = {
                        if (uiState.isRunning) onAction(RepeaterUiAction.StopRepeater)
                        else onAction(RepeaterUiAction.StartRepeater)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRunning) Color(0xFFB71C1C) else Color(0xFF1B5E20),
                        contentColor = Color.White
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.isRunning) "STOP REPEATER MODE" else "ENGAGE REPEATER MODE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (uiState.isRunning) "Audio hardware released • 100% radio airtime for mesh"
                            else "Decouples audio mic/speaker to conserve battery for relay",
                            fontSize = 9.sp,
                            color = Color(0xFFECEFF1)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
    }
}
