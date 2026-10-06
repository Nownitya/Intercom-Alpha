package org.nowni.intercom_alpha.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-performance, tactile master Push-To-Talk actuator for motorcycle cockpits.
 * Calibrated for coarse leather riding gloves with visual pulse cues and flanking controls.
 */
@Composable
fun PttButton(
    isPressed: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isVoxLocked: Boolean = false,
    onToggleVoxLock: () -> Unit = {},
    channelLabel: String = "CH 04",
    volumeLevel: Float = 0.85f,
    onToggleVolume: () -> Unit = {}
) {
    val isHotMic = isPressed || isVoxLocked

    // Pulse animation for hot-mic radio transmission
    val infiniteTransition = rememberInfiniteTransition(label = "pttPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Flanking Control: Quick Speaker Volume
        FilledTonalIconButton(
            onClick = onToggleVolume,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF1D2023),
                contentColor = Color(0xFF00E5FF)
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🔊", fontSize = 16.sp)
                Text(
                    text = "${(volumeLevel * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBDF4FF)
                )
            }
        }

        // 2. Center 140dp Circular PTT Actuator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(156.dp)
        ) {
            // Concentric Hot-Mic Pulse Ring
            if (isHotMic) {
                Box(
                    modifier = Modifier
                        .size(152.dp)
                        .scale(pulseScale)
                        .border(
                            width = 2.dp,
                            color = Color(0xFFFA5C22).copy(alpha = pulseAlpha),
                            shape = CircleShape
                        )
                )
            }

            // Outer Knurled Industrial Ring
            Box(
                modifier = Modifier
                    .size(142.dp)
                    .background(Color(0xFF111417), CircleShape)
                    .border(
                        BorderStroke(
                            3.dp,
                            if (isHotMic) Color(0xFFFA5C22) else Color(0xFF2B3848)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Main Interactive Core Button
                Surface(
                    modifier = Modifier
                        .size(126.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onPressStart()
                                    tryAwaitRelease()
                                    onPressEnd()
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (isHotMic) Color(0xFFE14A0E) else Color(0xFF1D2023),
                    shadowElevation = if (isHotMic) 12.dp else 4.dp,
                    border = BorderStroke(
                        1.5.dp,
                        if (isHotMic) Color(0xFFFFB59E) else Color(0xFF323538)
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isHotMic) {
                                        listOf(Color(0xFFFA5C22), Color(0xFFD84315))
                                    } else {
                                        listOf(Color(0xFF272A2E), Color(0xFF18202A))
                                    }
                                )
                            )
                            .padding(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isHotMic) "🎙️" else "📻",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "PUSH TO TALK",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isVoxLocked) "VOX LOCKED" else if (isPressed) "TRANSMITTING" else "$channelLabel • ACTIVE",
                                color = if (isHotMic) Color(0xFFFFDBCE) else Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 3. Right Flanking Control: Hands-Free VOX Latch Toggle
        FilledTonalIconButton(
            onClick = onToggleVoxLock,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isVoxLocked) Color(0xFFFA5C22) else Color(0xFF1D2023),
                contentColor = if (isVoxLocked) Color.White else Color(0xFF00E676)
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isVoxLocked) "🔒" else "🔓", fontSize = 16.sp)
                Text(
                    text = if (isVoxLocked) "VOX ON" else "VOX",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
