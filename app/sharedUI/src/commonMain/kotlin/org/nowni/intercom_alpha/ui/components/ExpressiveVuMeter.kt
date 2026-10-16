package org.nowni.intercom_alpha.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder
import kotlin.math.max

/**
 * Tactical Real-Time Audio Spectrum & VU Peak Meter.
 * Features:
 * - 16 segmented LED-style bars.
 * - Dynamic color transitions: Emerald (nominal) -> Amber (hot) -> Crimson (clipping).
 * - Analog phosphor decay decay animation and peak-hold indicator.
 * - Tabular monospaced dB display.
 */
@Composable
fun ExpressiveVuMeter(
    currentRmsLevel: Float, // Normalized 0.0f .. 1.0f
    modifier: Modifier = Modifier,
    barCount: Int = 14,
    showDbLabel: Boolean = true
) {
    val peakLevel = remember { Animatable(0f) }

    LaunchedEffect(currentRmsLevel) {
        if (currentRmsLevel > peakLevel.value) {
            peakLevel.snapTo(currentRmsLevel)
        } else {
            peakLevel.animateTo(
                targetValue = max(0f, currentRmsLevel),
                animationSpec = tween(durationMillis = 350)
            )
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F1318))
            .neobrutalistBorder(
                color = IntercomTokens.NeobrutalBorderIdle,
                thickness = 1.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val activeBars = (currentRmsLevel.coerceIn(0f, 1f) * barCount).toInt()
        val peakBar = (peakLevel.value.coerceIn(0f, 1f) * barCount).toInt()

        for (i in 0 until barCount) {
            val isActive = i < activeBars
            val isPeak = i == peakBar

            val barColor = when {
                i >= barCount - 2 -> IntercomTokens.SignalEmergencyCrimson // Clipping alert
                i >= barCount - 5 -> IntercomTokens.SignalAmber            // High level warning
                else -> IntercomTokens.SignalCodedPhyEmerald              // Nominal speech
            }

            val finalColor = when {
                isActive -> barColor
                isPeak -> barColor.copy(alpha = 0.85f)
                else -> Color(0xFF1E242C) // Inactive dark slot
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isPeak) 18.dp else 14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(finalColor)
            )
        }

        if (showDbLabel) {
            val dbValue = if (currentRmsLevel > 0.01f) {
                (20 * kotlin.math.log10(currentRmsLevel.toDouble())).toInt()
            } else {
                -60
            }
            Text(
                text = "${dbValue}dB",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentRmsLevel >= 0.85f) IntercomTokens.SignalEmergencyCrimson else Color(0xFF8B949E),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
