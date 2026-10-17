package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.WaypointType
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot
import org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glove-Friendly Spatial Radar & Telemetry Liquid Glass Card.
 * Complies with ADR-014, Material 3 Design Kit, and Ralph Story ICA-3103.
 *
 * Features:
 * - Liquid glass floating HUD card (semi-translucent frosted chassis)
 * - Glove-friendly 84dp quick zoom toggle button for coarse riding gloves
 * - Monospaced tabular telemetry readouts for jitter-free spatial tracking
 * - Integrated topo contours, active trail polyline, and convoy vectors
 */
@Composable
fun TactileRadarCard(
    radarSnapshot: RadarSnapshot,
    convoySnapshot: ConvoyTelemetrySnapshot,
    trailMapSnapshot: TrailMapSnapshot = TrailMapSnapshot(),
    geofenceSnapshot: ConvoyGeofenceSnapshot = ConvoyGeofenceSnapshot(),
    onCycleZoom: () -> Unit = {},
    onOpenTrailCockpit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEE11161D)
        ),
        border = BorderStroke(1.dp, Color(0xFF2B3A4C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ================= 1. TOP HEADER & GLOVE-FRIENDLY ZOOM =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tactical Mode & Compass Heading Chip
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
                        text = "SPATIAL RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "• 360° VECTORS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8B949E)
                    )
                }

                // Glove-Friendly 84dp Range Zoom Button (ICA-3103 requirement)
                Surface(
                    onClick = onCycleZoom,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1D2631),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                    modifier = Modifier.widthIn(min = 84.dp).heightIn(min = 36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ZOOM ${trailMapSnapshot.activeRangeZoom.label}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // ================= 2. SPATIAL VECTOR RADAR CANVAS =================
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF090D12))
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f - 4f
                    val maxRadarRange = trailMapSnapshot.activeRangeZoom.maxDistanceMeters

                    // Concentric Range Rings
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        radius = maxRadius * 0.33f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.16f),
                        radius = maxRadius * 0.66f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.28f),
                        radius = maxRadius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    // Crosshair Reticles
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        start = Offset(center.x, center.y - maxRadius),
                        end = Offset(center.x, center.y + maxRadius),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                        start = Offset(center.x - maxRadius, center.y),
                        end = Offset(center.x + maxRadius, center.y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Topo Contours & Trail Polyline (if overlay active)
                    if (trailMapSnapshot.isOverlayEnabled) {
                        for (contour in trailMapSnapshot.projectedContours) {
                            val contourPath = Path()
                            var started = false
                            for (pt in contour.points) {
                                val px = center.x + (pt.normalizedX * maxRadius)
                                val py = center.y + (pt.normalizedY * maxRadius)
                                if (!started) {
                                    contourPath.moveTo(px, py)
                                    started = true
                                } else {
                                    contourPath.lineTo(px, py)
                                }
                            }
                            drawPath(
                                path = contourPath,
                                color = Color(0xFF2A394A).copy(alpha = 0.7f),
                                style = Stroke(
                                    width = 0.8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                                )
                            )
                        }

                        // Active Trail Polyline
                        for (trail in trailMapSnapshot.projectedTrails) {
                            val trailPath = Path()
                            var started = false
                            for (pt in trail.points) {
                                val px = center.x + (pt.normalizedX * maxRadius)
                                val py = center.y + (pt.normalizedY * maxRadius)
                                if (!started) {
                                    trailPath.moveTo(px, py)
                                    started = true
                                } else {
                                    trailPath.lineTo(px, py)
                                }
                            }
                            drawPath(
                                path = trailPath,
                                color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 3f))
                                )
                            )
                        }
                    }

                    // Geofence Convoy Tether Ring
                    val tetherRadius = (600.0 / maxRadarRange).toFloat().coerceIn(0.1f, 1.0f) * maxRadius
                    val tetherColor = when (geofenceSnapshot.overallStatus) {
                        GeofenceTetherStatus.BREACH -> Color(0xFFFF1744)
                        GeofenceTetherStatus.WARNING -> Color(0xFFFF9100)
                        else -> Color(0xFF00E676).copy(alpha = 0.35f)
                    }
                    drawCircle(
                        color = tetherColor,
                        radius = tetherRadius,
                        center = center,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                    )

                    // Plotted Convoy Riders
                    for (rider in convoySnapshot.riders) {
                        val distance = rider.distanceMeters.coerceAtLeast(10.0).coerceAtMost(maxRadarRange)
                        val norm = (distance / maxRadarRange).toFloat()
                        val rad = (rider.relativeBearingDegrees - 90.0) * (kotlin.math.PI / 180.0)
                        val r = norm * maxRadius
                        val rx = center.x + (r * cos(rad)).toFloat()
                        val ry = center.y + (r * sin(rad)).toFloat()

                        val riderColor = when {
                            rider.distanceMeters < 100 -> Color(0xFF00E676)
                            rider.distanceMeters < 500 -> Color(0xFF00E5FF)
                            else -> Color(0xFFFFB300)
                        }

                        drawCircle(
                            color = riderColor,
                            radius = 4.dp.toPx(),
                            center = Offset(rx, ry)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 4.dp.toPx(),
                            center = Offset(rx, ry),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Local Observer Center Dot
                    drawCircle(
                        color = Color(0xFF2979FF),
                        radius = 5.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // ================= 3. MONOSPACED TABULAR TELEMETRY READOUTS =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Closest Rider Tabular Chip
                val closest = convoySnapshot.closestRider
                if (closest != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161F28),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("📍", fontSize = 10.sp)
                            Text(
                                text = "${closest.peerName.ifBlank { "NODE" }}: ${closest.distanceMeters.toInt()}m @ ${closest.relativeBearingDegrees.toInt()}°",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "${convoySnapshot.riders.size} RIDERS IN MESH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF8B949E)
                    )
                }

                // Active Trail Launcher / Status
                val activeTrail = trailMapSnapshot.activeTrail
                if (activeTrail != null) {
                    Surface(
                        onClick = onOpenTrailCockpit,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0E2822),
                        border = BorderStroke(1.dp, Color(0xFF00E676))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🗺️", fontSize = 10.sp)
                            Text(
                                text = activeTrail.name.take(12),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onOpenTrailCockpit,
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1D2024)
                    ) {
                        Text(
                            text = "TRAIL COCKPIT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFB0B8C1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
