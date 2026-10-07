package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.WaypointType

/**
 * Tactical Offline Vector Terrain & Trail Cockpit Sheet.
 * Displays offline trail metrics, elevation profile, and waypoint roster with zero cellular connectivity.
 */
@Composable
fun OfflineTrailCockpitSheet(
    trailMapSnapshot: TrailMapSnapshot,
    onCycleZoom: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onLoadSampleTrail: () -> Unit,
    onOpenTrailManager: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF14181D)
            ),
            border = BorderStroke(1.dp, Color(0xFF2B3848))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OFFLINE TRAIL COCKPIT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = trailMapSnapshot.activeTrail?.name ?: "No Offline Trail Loaded",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onOpenTrailManager != null) {
                            FilledTonalButton(
                                onClick = onOpenTrailManager,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    contentColor = Color(0xFF00E5FF)
                                )
                            ) {
                                Text("⚡ OTA ROUTES", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        TextButton(onClick = onDismiss) {
                            Text("CLOSE", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = Color(0xFF2B3848)
                )

                // 2. Metrics Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Elevation
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1D2023),
                        border = BorderStroke(1.dp, Color(0xFF2B3848))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("CURRENT ALT", fontSize = 8.sp, color = Color(0xFF7A8B9E), fontWeight = FontWeight.Bold)
                            Text(
                                text = "${trailMapSnapshot.currentElevationMeters.toInt()}m",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E676)
                            )
                        }
                    }

                    // Total Ascent
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1D2023),
                        border = BorderStroke(1.dp, Color(0xFF2B3848))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("ELEV GAIN", fontSize = 8.sp, color = Color(0xFF7A8B9E), fontWeight = FontWeight.Bold)
                            val gain = trailMapSnapshot.activeTrail?.totalElevationGainMeters?.toInt() ?: 0
                            Text(
                                text = "+${gain}m",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }

                    // Radar Zoom Button
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1D2023),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        onClick = onCycleZoom
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("RADAR ZOOM", fontSize = 8.sp, color = Color(0xFF7A8B9E), fontWeight = FontWeight.Bold)
                            Text(
                                text = trailMapSnapshot.activeRangeZoom.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Quick Control Actions
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
                            text = "Vector Radar Overlay",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Switch(
                            checked = trailMapSnapshot.isOverlayEnabled,
                            onCheckedChange = onToggleOverlay,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.4f)
                            )
                        )
                    }

                    if (trailMapSnapshot.activeTrail == null) {
                        FilledTonalButton(
                            onClick = onLoadSampleTrail,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1D2023),
                                contentColor = Color(0xFF00E5FF)
                            )
                        ) {
                            Text("Load Alpine Trail", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Waypoints Title
                Text(
                    text = "TRAIL WAYPOINTS & HAZARDS (${trailMapSnapshot.projectedWaypoints.size})",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF7A8B9E)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Waypoints Roster
                if (trailMapSnapshot.projectedWaypoints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No offline waypoints loaded.\nTap 'Load Alpine Trail' to populate trail tracks.",
                            fontSize = 11.sp,
                            color = Color(0xFF7A8B9E),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(trailMapSnapshot.projectedWaypoints) { pw ->
                            val wp = pw.waypoint
                            val proj = pw.projectedPoint
                            val icon = when (wp.type) {
                                WaypointType.BASECAMP -> "⛺"
                                WaypointType.WATER_SOURCE -> "💧"
                                WaypointType.TRAILHEAD -> "🚩"
                                WaypointType.CHECKPOINT -> "📍"
                                WaypointType.OBSTACLE_HAZARD -> "⚠️"
                                WaypointType.MOUNTAIN_PASS -> "⛰️"
                                WaypointType.SHELTER -> "🛖"
                                WaypointType.CUSTOM -> "📌"
                            }

                            val distText = if (proj.distanceMeters < 1000.0) {
                                "${proj.distanceMeters.toInt()}m"
                            } else {
                                "${(proj.distanceMeters / 100.0).toInt() / 10.0}km"
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1D2023),
                                border = BorderStroke(1.dp, Color(0xFF2B3848))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = icon, fontSize = 14.sp)
                                        Column {
                                            Text(
                                                text = wp.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            if (wp.description.isNotEmpty()) {
                                                Text(
                                                    text = wp.description,
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF7A8B9E)
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = distText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF00E5FF)
                                        )
                                        Text(
                                            text = if (proj.isVisibleOnRadar) "ON RADAR" else "OUT OF RANGE",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (proj.isVisibleOnRadar) Color(0xFF00E676) else Color(0xFF7A8B9E)
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
