package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.map.TrailMapSnapshot
import org.nowni.intercom_alpha.map.WaypointType
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact Tactical Spatial Radar & Convoy Telemetry View with Offline Vector Terrain & Trail Overlay.
 * Combines a circular range-ring radar display with topo contours, neon trail tracks, and live rider vectors.
 */
@Composable
fun ProximityRadarView(
    radarSnapshot: RadarSnapshot,
    convoySnapshot: ConvoyTelemetrySnapshot,
    trailMapSnapshot: TrailMapSnapshot = TrailMapSnapshot(),
    geofenceSnapshot: org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot = org.nowni.intercom_alpha.telemetry.ConvoyGeofenceSnapshot(),
    powerGovernorState: org.nowni.intercom_alpha.power.PowerGovernorState = org.nowni.intercom_alpha.power.PowerGovernorState(),
    onCycleZoom: () -> Unit = {},
    onOpenTrailSheet: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF14181D)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(Color(0xFF2B3848))
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Compact Circular Vector Radar Canvas (110dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                Canvas(modifier = Modifier.size(104.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.width / 2f - 4f
                    val maxRadarRange = trailMapSnapshot.activeRangeZoom.maxDistanceMeters

                    // ================= A. OFFLINE TOPO CONTOURS & TRAIL OVERLAY =================
                    if (trailMapSnapshot.isOverlayEnabled) {
                        // 1. Topographic Elevation Contours
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

                        // 2. Active Vector Trail Polyline
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
                                color = Color(0xFF00E5FF).copy(alpha = 0.75f),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }

                        // 3. Peer Convoy Breadcrumb Trails
                        for ((_, points) in trailMapSnapshot.peerBreadcrumbs) {
                            for (pt in points) {
                                if (pt.isVisibleOnRadar) {
                                    val px = center.x + (pt.normalizedX * maxRadius)
                                    val py = center.y + (pt.normalizedY * maxRadius)
                                    drawCircle(
                                        color = Color(0xFF00E676).copy(alpha = 0.35f),
                                        radius = 1.5.dp.toPx(),
                                        center = Offset(px, py)
                                    )
                                }
                            }
                        }

                        // 4. Tactical Waypoint Pins
                        for (pw in trailMapSnapshot.projectedWaypoints) {
                            val pt = pw.projectedPoint
                            if (pt.isVisibleOnRadar) {
                                val px = center.x + (pt.normalizedX * maxRadius)
                                val py = center.y + (pt.normalizedY * maxRadius)
                                val wpColor = when (pw.waypoint.type) {
                                    WaypointType.BASECAMP -> Color(0xFF00E676)
                                    WaypointType.OBSTACLE_HAZARD -> Color(0xFFFF5252)
                                    WaypointType.MOUNTAIN_PASS -> Color(0xFFFFB300)
                                    else -> Color(0xFF00E5FF)
                                }
                                drawCircle(
                                    color = wpColor,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }
                    }

                    // ================= B. RADAR RANGE RINGS & RETICLE =================
                    // Concentric Range Rings (Outer, Mid 66%, Inner 33%)
                    drawCircle(
                        color = Color(0xFF2B3848),
                        radius = maxRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF222D3B),
                        radius = maxRadius * 0.66f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFF1C242F),
                        radius = maxRadius * 0.33f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Crosshairs
                    drawLine(
                        color = Color(0xFF2B3848).copy(alpha = 0.5f),
                        start = Offset(center.x, 4f),
                        end = Offset(center.x, size.height - 4f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF2B3848).copy(alpha = 0.5f),
                        start = Offset(4f, center.y),
                        end = Offset(size.width - 4f, center.y),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Center User Heading Chevron (Pointing 0° / Up)
                    val chevronPath = Path().apply {
                        moveTo(center.x, center.y - 7f)
                        lineTo(center.x - 5f, center.y + 5f)
                        lineTo(center.x, center.y + 2f)
                        lineTo(center.x + 5f, center.y + 5f)
                        close()
                    }
                    drawPath(chevronPath, color = Color(0xFF00E5FF))

                    // ================= C. CONVOY RIDERS BLIPS =================
                    val riders = convoySnapshot.riders
                    for (rider in riders) {
                        val bearingRad = (rider.relativeBearingDegrees - 90.0) * (kotlin.math.PI / 180.0)
                        val normDist = (rider.distanceMeters / maxRadarRange).coerceIn(0.1, 1.0)
                        val r = (normDist * maxRadius).toFloat()

                        val px = center.x + (r * cos(bearingRad)).toFloat()
                        val py = center.y + (r * sin(bearingRad)).toFloat()

                        val dotColor = when {
                            rider.distanceMeters < 200.0 -> Color(0xFF00E676) // Phosphor Green (LOS)
                            rider.distanceMeters < 600.0 -> Color(0xFF00E5FF) // Electric Cyan (Mid)
                            else -> Color(0xFFFFB300) // Amber (Far)
                        }

                        drawCircle(
                            color = dotColor,
                            radius = 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    // Fallback to targets if no convoy riders yet
                    if (riders.isEmpty()) {
                        for (target in radarSnapshot.targets) {
                            val dotColor = when (target.zone) {
                                org.nowni.intercom_alpha.mesh.ProximityZone.NEAR -> Color(0xFF00E676)
                                org.nowni.intercom_alpha.mesh.ProximityZone.MEDIUM -> Color(0xFF00E5FF)
                                else -> Color(0xFFFFB300)
                            }
                            drawCircle(
                                color = dotColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(center.x + 20f, center.y - 25f)
                            )
                        }
                    }

                    // ================= D. TACTICAL GEOFENCE TETHER BOUNDARY RING =================
                    when (geofenceSnapshot.overallStatus) {
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.BREACH -> {
                            drawCircle(
                                color = Color(0xFFFF1744),
                                radius = maxRadius,
                                center = center,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.WARNING -> {
                            drawCircle(
                                color = Color(0xFFFF9100),
                                radius = maxRadius,
                                center = center,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                )
                            )
                        }
                        else -> {}
                    }

                    // ================= E. HAZARD WAYPOINT RADAR PROXIMITY BLIPS =================
                    for (hazard in geofenceSnapshot.activeHazardAlerts) {
                        val rad = (hazard.relativeBearingDegrees - 90.0) * (kotlin.math.PI / 180.0)
                        val norm = (hazard.distanceMeters / maxRadarRange).coerceIn(0.15, 0.95)
                        val r = (norm * maxRadius).toFloat()
                        val hx = center.x + (r * cos(rad)).toFloat()
                        val hy = center.y + (r * sin(rad)).toFloat()
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = 4.5.dp.toPx(),
                            center = Offset(hx, hy),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                // Interactive Radar Range Zoom Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1D2023),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    onClick = onCycleZoom,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = trailMapSnapshot.activeRangeZoom.label,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Right Side: Glanceable Live Rider & Trail Telemetry Strip
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONVOY RADAR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF00E5FF)
                    )

                    val activeCount = maxOf(radarSnapshot.activeRidersCount, convoySnapshot.activeCount)
                    Text(
                        text = "$activeCount NODES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }

                // Trail & Nearest Waypoint Strip (Clickable to open Trail Sheet)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1D2023),
                    border = BorderStroke(1.dp, Color(0xFF2B3848)),
                    onClick = onOpenTrailSheet
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val nearest = trailMapSnapshot.nearestWaypoint
                        if (nearest != null) {
                            val distMeters = trailMapSnapshot.nearestWaypointDistanceMeters?.toInt() ?: 0
                            val distLabel = if (distMeters < 1000) "${distMeters}m" else "${distMeters / 1000}km"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("📍", fontSize = 11.sp)
                                Column {
                                    Text(
                                        text = nearest.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ALT ${nearest.point.altitudeMeters.toInt()}m",
                                        fontSize = 8.sp,
                                        color = Color(0xFF7A8B9E)
                                    )
                                }
                            }
                            Text(
                                text = distLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        } else {
                            val trailName = trailMapSnapshot.activeTrail?.name ?: "OFFLINE MAP"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🗺️", fontSize = 11.sp)
                                Text(
                                    text = trailName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                            Text(
                                text = "OPEN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B9E)
                            )
                        }
                    }
                }

                val closest = convoySnapshot.closestRider
                if (closest != null) {
                    val arrow = getBearingArrow(closest.relativeBearingDegrees)
                    val distText = if (closest.distanceMeters < 1000.0) {
                        "${closest.distanceMeters.toInt()}m"
                    } else {
                        "${(closest.distanceMeters / 100.0).toInt() / 10.0}km"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1D2023),
                        border = BorderStroke(1.dp, Color(0xFF2B3848))
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = arrow.first,
                                    fontSize = 13.sp,
                                    color = Color(0xFF00E676)
                                )
                                Column {
                                    Text(
                                        text = closest.peerName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = arrow.second,
                                        fontSize = 7.sp,
                                        color = Color(0xFFB0B8C1)
                                    )
                                }
                            }

                            Text(
                                text = distText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                // Sub-Metrics Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tetherColor = when (geofenceSnapshot.overallStatus) {
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.IN_BOUNDS -> Color(0xFF00E676)
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.WARNING -> Color(0xFFFF9100)
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.BREACH -> Color(0xFFFF1744)
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.DISCONNECTED -> Color(0xFF7A8B9E)
                    }
                    val tetherText = when (geofenceSnapshot.overallStatus) {
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.IN_BOUNDS -> "TETHER: OK"
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.WARNING -> "TETHER: WARN"
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.BREACH -> "TETHER: BREACH"
                        org.nowni.intercom_alpha.telemetry.GeofenceTetherStatus.DISCONNECTED -> "TETHER: DISC"
                    }

                    val pwrText = when (powerGovernorState.policy) {
                        org.nowni.intercom_alpha.power.PowerPolicy.NORMAL -> "PWR: 100%"
                        org.nowni.intercom_alpha.power.PowerPolicy.CONSERVATIVE -> "PWR: CONS 50%"
                        org.nowni.intercom_alpha.power.PowerPolicy.EXTREME_SURVIVAL -> "PWR: SURV 20%"
                        org.nowni.intercom_alpha.power.PowerPolicy.REST_STOP_SLEEP -> "PWR: SLEEP 5%"
                        org.nowni.intercom_alpha.power.PowerPolicy.EMERGENCY_OVERRIDE -> "PWR: SOS"
                    }
                    val pwrColor = when (powerGovernorState.policy) {
                        org.nowni.intercom_alpha.power.PowerPolicy.NORMAL -> Color(0xFF00E5FF)
                        org.nowni.intercom_alpha.power.PowerPolicy.CONSERVATIVE -> Color(0xFFFFB300)
                        org.nowni.intercom_alpha.power.PowerPolicy.EXTREME_SURVIVAL -> Color(0xFFFF5252)
                        org.nowni.intercom_alpha.power.PowerPolicy.REST_STOP_SLEEP -> Color(0xFF7A8B9E)
                        org.nowni.intercom_alpha.power.PowerPolicy.EMERGENCY_OVERRIDE -> Color(0xFFFF1744)
                    }

                    Text(
                        text = tetherText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = tetherColor
                    )
                    Text(
                        text = pwrText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = pwrColor
                    )
                    Text(
                        text = if (trailMapSnapshot.isOverlayEnabled) "TOPO [ON]" else "TOPO [OFF]",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (trailMapSnapshot.isOverlayEnabled) Color(0xFF00E5FF) else Color(0xFF7A8B9E)
                    )
                }
            }
        }
    }
}

private fun getBearingArrow(bearingDegrees: Double): Pair<String, String> {
    val norm = ((bearingDegrees % 360.0) + 360.0) % 360.0
    return when {
        norm >= 337.5 || norm < 22.5 -> Pair("↑", "AHEAD (0°)")
        norm >= 22.5 && norm < 67.5 -> Pair("↗", "FLANK RIGHT (45°)")
        norm >= 67.5 && norm < 112.5 -> Pair("→", "RIGHT (90°)")
        norm >= 112.5 && norm < 157.5 -> Pair("↘", "REAR RIGHT (135°)")
        norm >= 157.5 && norm < 202.5 -> Pair("↓", "BEHIND (180°)")
        norm >= 202.5 && norm < 247.5 -> Pair("↙", "REAR LEFT (225°)")
        norm >= 247.5 && norm < 292.5 -> Pair("←", "LEFT (270°)")
        else -> Pair("↖", "FLANK LEFT (315°)")
    }
}
