package org.nowni.intercom_alpha.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.geo.ConvoyElevationProfile
import org.nowni.intercom_alpha.geo.GradeSeverity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tactile Trail Elevation HUD Overlay & Cockpit Sheet Presentation (Sprint 36 - ICA-3603).
 *
 * Renders high-contrast OLED altitude profile line chart with glove-friendly 64dp zoom/scale toggles,
 * real-time convoy rider altitude pins along the profile curve, steep alpine grade warnings (>12%),
 * and vertical altitude separation meters between lead and tail riders.
 */
@Composable
fun TrailElevationHudOverlay(
    elevationProfile: ConvoyElevationProfile,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomScaleIndex by remember { mutableStateOf(1) } // 0: 1km, 1: 5km, 2: 15km
    val zoomScales = listOf("1 KM", "5 KM", "15 KM")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFF0D1117), // Deep tactical OLED background
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0xFF30363D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .background(Color(0xFF8B949E), RoundedCornerShape(2.5.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALPINE ELEVATION PROFILE",
                        color = Color(0xFF58A6FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${elevationProfile.localAltitudeMeters.roundToInt()}m ALTITUDE",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // 64dp Glove-Friendly Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF21262D), CircleShape)
                        .border(1.dp, Color(0xFF30363D), CircleShape)
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metric telemetry row: Slope Grade % and Vertical Separation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Slope Grade Card
                val absGrade = kotlin.math.abs(elevationProfile.currentGradePercentage)
                val gradeColor = when {
                    absGrade >= 12.0 -> Color(0xFFFF5252) // Red extreme
                    absGrade >= 8.0 -> Color(0xFFFFB74D)  // Orange caution
                    else -> Color(0xFF00E676)            // Green normal
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "SLOPE GRADE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )
                        val direction = if (elevationProfile.currentGradePercentage >= 0) "▲" else "▼"
                        Text(
                            text = "$direction ${((absGrade * 10.0).roundToInt() / 10.0)}%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = gradeColor
                        )
                    }
                }

                // Lead vs Tail Vertical Separation Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "LEAD/TAIL SPREAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Δ ${elevationProfile.leadTailVerticalSeparationMeters.roundToInt()}m",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }
            }

            // Alpine Hazard Warning Banner
            if (elevationProfile.activeWarnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                val topWarning = elevationProfile.activeWarnings.first()
                val warnBg = if (topWarning.severity == GradeSeverity.EXTREME_ALPINE_WARNING) {
                    Color(0xFF7F1D1D)
                } else {
                    Color(0xFF78350F)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = warnBg,
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = topWarning.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Elevation Profile Line Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF090D12), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF21262D), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val points = elevationProfile.profilePoints
                    if (points.size >= 2) {
                        val minAlt = elevationProfile.minAltitudeMeters
                        val maxAlt = max(minAlt + 50.0, elevationProfile.maxAltitudeMeters)
                        val maxDist = max(100.0, points.last().distanceMeters)

                        // 1. Draw horizontal grid lines
                        val gridLineColor = Color(0xFF21262D)
                        drawLine(gridLineColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1f)
                        drawLine(gridLineColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                        drawLine(gridLineColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1f)

                        // 2. Draw elevation profile line path
                        val path = Path()
                        val fillPath = Path()

                        for ((idx, pt) in points.withIndex()) {
                            val x = (pt.distanceMeters / maxDist * w).toFloat().coerceIn(0f, w)
                            val altNorm = ((pt.altitudeMeters - minAlt) / (maxAlt - minAlt)).toFloat().coerceIn(0f, 1f)
                            val y = h - (altNorm * (h - 20f)) - 10f

                            if (idx == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        // Close fill path
                        fillPath.lineTo(w, h)
                        fillPath.close()

                        // Draw gradient-like filled area under curve
                        drawPath(fillPath, color = Color(0xFF00E5FF).copy(alpha = 0.12f))

                        // Draw profile stroke
                        drawPath(path, color = Color(0xFF00E5FF), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                        // 3. Draw Convoy Rider Pins along the curve
                        val riderColors = listOf(
                            Color(0xFF00E676), // Lead rider green
                            Color(0xFFFFD600), // Mid rider yellow
                            Color(0xFFFF5252), // Tail rider red
                            Color(0xFFE040FB)  // Other rider purple
                        )

                        for ((idx, rider) in elevationProfile.riders.withIndex()) {
                            val riderX = (rider.distanceAlongRouteMeters / maxDist * w).toFloat().coerceIn(10f, w - 10f)
                            val altNorm = ((rider.altitudeMeters - minAlt) / (maxAlt - minAlt)).toFloat().coerceIn(0f, 1f)
                            val riderY = h - (altNorm * (h - 20f)) - 10f
                            val dotColor = riderColors[idx % riderColors.size]

                            // Outer glow / halo
                            drawCircle(dotColor.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = Offset(riderX, riderY))
                            // Center pin dot
                            drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(riderX, riderY))
                        }
                    } else {
                        // Flat line placeholder if no GPS route loaded
                        val midY = h * 0.5f
                        drawLine(
                            color = Color(0xFF30363D),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Control Row: 64dp Glove-Friendly Zoom Toggle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HORIZON ZOOM",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zoomScales.forEachIndexed { idx, label ->
                        val isSelected = zoomScaleIndex == idx
                        Button(
                            onClick = { zoomScaleIndex = idx },
                            modifier = Modifier
                                .height(44.dp)
                                .wrapContentWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF58A6FF) else Color(0xFF21262D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
