package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.LocalTacticalColors

@Composable
fun PttActuatorShelf(
    isTransmitting: Boolean,
    isVoxLocked: Boolean,
    activeChannelId: Int,
    onStartTransmit: () -> Unit,
    onStopTransmit: () -> Unit,
    onToggleVoxLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current
    val isHot = isTransmitting || isVoxLocked

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Master Capsule PTT Actuator
        Box(
            modifier = Modifier
                .weight(1f)
                .height(IntercomTokens.PttShelfHeight)
                .clip(RoundedCornerShape(50))
                .background(if (isHot) colors.signalActive else colors.surfaceCard)
                .border(
                    if (isHot) 2.dp else 1.dp,
                    if (isHot) colors.signalActive else colors.surfaceBorder,
                    RoundedCornerShape(50)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onStartTransmit()
                            tryAwaitRelease()
                            onStopTransmit()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isHot) "TRANSMITTING" else "HOLD TO TALK",
                    color = if (isHot) Color.Black else colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    text = "CH ${activeChannelId.toString().padStart(2, '0')} • 48 KBPS",
                    color = if (isHot) Color.Black.copy(alpha = 0.8f) else colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // VOX Lock Button
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(IntercomTokens.PttShelfHeight)
                .clip(IntercomTokens.ShapeCard)
                .background(if (isVoxLocked) IntercomTokens.SignalAmberMuted else colors.surfaceCard)
                .border(
                    1.dp,
                    if (isVoxLocked) colors.signalActive else colors.surfaceBorder,
                    IntercomTokens.ShapeCard
                )
                .clickable { onToggleVoxLock() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isVoxLocked) "VOX ON" else "VOX OFF",
                    color = if (isVoxLocked) colors.signalActive else colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

