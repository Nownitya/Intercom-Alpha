package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compact Tactical Spatial Radar & Convoy Telemetry View for motorcycle cockpits.
 * Combines a circular range-ring radar display with live rider vector tags in a single 130dp envelope.
 */
@Composable
fun ProximityRadarView(
    radarSnapshot: RadarSnapshot,
    convoySnapshot: ConvoyTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF14181D)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2B3848))
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

                    // Concentric Range Rings (100m, 500m, 1.5km)
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

                    // Plot Convoy Riders
                    val riders = convoySnapshot.riders
                    for (rider in riders) {
                        val bearingRad = (rider.relativeBearingDegrees - 90.0) * (kotlin.math.PI / 180.0)
                        val normDist = (rider.distanceMeters / 1500.0).coerceIn(0.1, 1.0)
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
                }

                // Range Label
                Text(
                    text = "1.5km",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7A8B9E),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Right Side: Glanceable Live Rider Telemetry Strip
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3848))
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
                                Text(
                                    text = arrow.first,
                                    fontSize = 14.sp,
                                    color = Color(0xFF00E676)
                                )
                                Column {
                                    Text(
                                        text = closest.peerName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = arrow.second,
                                        fontSize = 8.sp,
                                        color = Color(0xFFB0B8C1)
                                    )
                                }
                            }

                            Text(
                                text = distText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "10Hz GNSS • Scanning formation vectors...",
                        fontSize = 10.sp,
                        color = Color(0xFF7A8B9E)
                    )
                }

                // Sub-Metrics Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0% LOSS · 18ms",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                    Text(
                        text = "BLE DIRECT [ACTIVE]",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
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
