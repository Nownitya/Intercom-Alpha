package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.failover.FailoverMetrics
import org.nowni.intercom_alpha.mesh.routing.RoutingSnapshot
import org.nowni.intercom_alpha.power.PowerGovernorState
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Tactical Subsystem Diagnostics & Telemetry HUD Sheet.
 * Displays real-time RF link quality, multi-path failover state,
 * power governor airtime duty-cycles, and AODV-lite routing statistics.
 */
@Composable
fun TactileDiagnosticsSheet(
    failoverMetrics: FailoverMetrics,
    powerState: PowerGovernorState,
    routingSnapshot: RoutingSnapshot,
    peers: List<Peer>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qScore = (failoverMetrics.averageDeliveryRatio * 100).toInt()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .claymorphic(
                    surfaceColor = IntercomTokens.CockpitChassis,
                    shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
                )
                .neobrutalistBorder(
                    color = IntercomTokens.NeobrutalBorderIdle,
                    thickness = IntercomTokens.NeobrutalBorderThickness,
                    shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SYSTEM DIAGNOSTICS",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                color = IntercomTokens.SignalSatelliteCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = IntercomTokens.SignalCodedPhyEmerald.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, IntercomTokens.SignalCodedPhyEmerald)
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = IntercomTokens.SignalCodedPhyEmerald,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "RF Airtime, Failover & Routing Telemetry",
                            fontSize = 10.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CLOSE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = IntercomTokens.SignalSatelliteCyan
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = IntercomTokens.CockpitSurfaceElevated
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Link Quality & Failover
                    item {
                        TelemetryCard(title = "RF LINK QUALITY & ARBITER") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell(
                                    label = "DELIVERY RATIO",
                                    value = "$qScore%",
                                    color = when {
                                        qScore >= 70 -> IntercomTokens.SignalCodedPhyEmerald
                                        qScore >= 30 -> IntercomTokens.SignalAmber
                                        else -> IntercomTokens.SignalEmergencyCrimson
                                    }
                                )
                                MetricCell(
                                    label = "ACTIVE ROUTE",
                                    value = failoverMetrics.currentMode.name,
                                    color = IntercomTokens.SignalSatelliteCyan
                                )
                                MetricCell(
                                    label = "CLOUD SFU",
                                    value = if (failoverMetrics.isCloudConnected) "ONLINE" else "OFFLINE",
                                    color = if (failoverMetrics.isCloudConnected) IntercomTokens.SignalCodedPhyEmerald else Color(0xFF7A8B9E)
                                )
                            }
                        }
                    }

                    // 2. Power Governor & Duty Cycle
                    item {
                        TelemetryCard(title = "POWER GOVERNOR & BATTERY OPTIMIZER") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell(
                                    label = "DUTY CYCLE",
                                    value = "${powerState.dutyCycle.dutyCyclePercent}%",
                                    color = IntercomTokens.SignalAmber
                                )
                                MetricCell(
                                    label = "POLICY",
                                    value = powerState.policy.name,
                                    color = IntercomTokens.SignalSatelliteCyan
                                )
                                MetricCell(
                                    label = "BATTERY",
                                    value = "${powerState.batteryPercent}%",
                                    color = IntercomTokens.SignalCodedPhyEmerald
                                )
                            }
                        }
                    }

                    // 3. AODV-Lite Directed Routing
                    item {
                        TelemetryCard(title = "AODV-LITE DIRECTED ROUTING (AIRTIME SAVINGS)") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricCell(
                                    label = "SAVINGS",
                                    value = "${routingSnapshot.airtimeSavingsPercentage.toInt()}%",
                                    color = IntercomTokens.SignalCodedPhyEmerald
                                )
                                MetricCell(
                                    label = "DIRECTED",
                                    value = "${routingSnapshot.totalDirectedPackets}",
                                    color = IntercomTokens.SignalSatelliteCyan
                                )
                                MetricCell(
                                    label = "FLOODED",
                                    value = "${routingSnapshot.totalFloodedPackets}",
                                    color = Color(0xFF7A8B9E)
                                )
                            }
                        }
                    }

                    // 4. Connected Convoy Nodes
                    item {
                        TelemetryCard(title = "ACTIVE MESH PEERS (${peers.size})") {
                            if (peers.isEmpty()) {
                                Text(
                                    text = "No convoy peers in radio range.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7A8B9E),
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    peers.forEach { peer ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = peer.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "RSSI: ${peer.rssi} dBm · BAT: ${if (peer.batteryLevel >= 0) "${peer.batteryLevel}%" else "N/A"}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = IntercomTokens.SignalSatelliteCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = IntercomTokens.CockpitSurfaceElevated,
        border = BorderStroke(1.dp, IntercomTokens.NeobrutalBorderIdle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                color = Color(0xFF7A8B9E)
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF7A8B9E)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}
