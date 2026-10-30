package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.LocalTacticalColors
import kotlin.math.log10
import kotlin.math.max

@Composable
fun DualVuMeter(
    localRmsLevel: Float,
    remoteRmsLevel: Float,
    remoteSpeakerName: String? = null,
    isVoiceActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(IntercomTokens.ShapeCard)
            .background(colors.surfaceCard)
            .border(1.dp, colors.surfaceBorder, IntercomTokens.ShapeCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Remote Speaker Row
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = remoteSpeakerName?.uppercase() ?: "CHANNEL READY",
                    color = if (isVoiceActive) colors.signalActive else colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = formatDb(remoteRmsLevel),
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
            }
            VuSegmentsBar(
                level = remoteRmsLevel,
                activeColor = colors.signalActive,
                inactiveColor = colors.surfaceBorder.copy(alpha = 0.4f),
                warningColor = colors.signalEmergency
            )
        }

        // Local Microphone Input Row
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIC INPUT",
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "VOX: -35dB",
                    color = colors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
            }
            VuSegmentsBar(
                level = localRmsLevel,
                activeColor = colors.signalSuccess,
                inactiveColor = colors.surfaceBorder.copy(alpha = 0.4f),
                warningColor = colors.signalEmergency
            )
        }
    }
}

@Composable
private fun VuSegmentsBar(
    level: Float,
    activeColor: Color,
    inactiveColor: Color,
    warningColor: Color,
    modifier: Modifier = Modifier,
    segmentCount: Int = 24
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until segmentCount) {
            val fraction = (i + 1).toFloat() / segmentCount.toFloat()
            val isLit = level >= fraction
            val isWarning = fraction > 0.85f

            val color = when {
                !isLit -> inactiveColor
                isWarning -> warningColor
                else -> activeColor
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

private fun formatDb(level: Float): String {
    if (level <= 0.01f) return "-∞ dB"
    val db = 20.0 * log10(max(level.toDouble(), 0.001))
    return "${db.toInt()} dB"
}

