package org.nowni.intercom_alpha.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder
import org.nowni.intercom_alpha.ui.theme.skeuomorphicBezel

/**
 * Production Tactile Push-To-Talk Master Actuator.
 * Combines:
 * - Skeuomorphism: Concentric knurled outer chassis bezel and physical depression well.
 * - Claymorphism: Soft 3D tactile convex button volume with dual specular/drop shadows.
 * - Neobrutalism: Crisp 2-3dp high-contrast perimeter outline (Amber idle, Hot-Mic Orange transmitting).
 * - Liquid Glass: Specular top highlight simulating coated polycarbonate glass.
 * - Glove Sizing: Enforces 160dp diameter master touch zone.
 */
@Composable
fun ExpressivePttActuator(
    isTransmitting: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    channelLabel: String = "CH 01",
    subtitle: String = "HOLD TO TALK"
) {
    // Physical depression scale animation using spring physics
    val scaleAnim = remember { Animatable(1.0f) }

    LaunchedEffect(isTransmitting) {
        if (isTransmitting) {
            scaleAnim.animateTo(
                targetValue = 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Dynamic Hot-Mic pulse glow
    val infiniteTransition = rememberInfiniteTransition(label = "pttHotMicPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val borderColor = if (isTransmitting) {
        IntercomTokens.SignalHotMicOrange.copy(alpha = pulseAlpha)
    } else {
        IntercomTokens.NeobrutalBorderIdle
    }

    val rimColor = if (isTransmitting) {
        IntercomTokens.SignalHotMicOrange
    } else {
        Color(0xFF21262D)
    }

    Box(
        modifier = modifier
            .size(IntercomTokens.PttMasterDiameter)
            .scale(scaleAnim.value)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        tryAwaitRelease()
                        onPressEnd()
                    }
                )
            }
            .skeuomorphicBezel(
                rimColor = rimColor,
                knurlColor = if (isTransmitting) IntercomTokens.SignalHotMicOrange.copy(alpha = 0.4f) else Color(0x33FFFFFF),
                thickness = 6.dp
            )
            .neobrutalistBorder(
                color = borderColor,
                thickness = if (isTransmitting) 3.dp else 2.dp,
                shape = CircleShape
            )
            .claymorphic(
                surfaceColor = if (isTransmitting) Color(0xFF1E1313) else IntercomTokens.CockpitSurfaceElevated,
                isPressed = isTransmitting,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = channelLabel,
                color = if (isTransmitting) IntercomTokens.SignalHotMicOrange else IntercomTokens.SignalAmber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isTransmitting) "TRANSMITTING" else subtitle,
                color = if (isTransmitting) Color.White else Color(0xFF8B949E),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}
