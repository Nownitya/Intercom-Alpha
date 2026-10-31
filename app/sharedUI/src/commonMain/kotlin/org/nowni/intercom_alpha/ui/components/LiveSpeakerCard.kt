package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.LocalTacticalColors

/**
 * Clean Live Speaker Status Card implementing ADR-0021.
 * High-contrast, zero-slop hero status card.
 */
@Composable
fun LiveSpeakerCard(
    isTransmitting: Boolean,
    remoteSpeakerName: String?,
    activeChannelId: Int,
    peerCount: Int,
    showDiscreteLed: Boolean = false,
    audioLevel: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current
    val isRemoteActive = !isTransmitting && remoteSpeakerName != null

    val stateColor = when {
        isTransmitting -> colors.signalActive
        isRemoteActive -> colors.signalSuccess
        else -> colors.textSecondary
    }

    val titleText = when {
        isTransmitting -> "YOU ARE TRANSMITTING"
        isRemoteActive -> remoteSpeakerName
        else -> "CHANNEL ${activeChannelId.toString().padStart(2, '0')}"
    }

    val subtitleText = when {
        isTransmitting -> "Broadcasting to $peerCount nearby riders"
        isRemoteActive -> "Receiving live audio • Priority Normal"
        else -> "Channel ready • Hold PTT to talk"
    }

    val badgeText = when {
        isTransmitting -> "TX ACTIVE"
        isRemoteActive -> "RX AUDIO"
        else -> "READY"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(IntercomTokens.ShapeCard)
            .background(colors.surfaceCard)
            .border(
                width = if (isTransmitting || isRemoteActive) 1.5.dp else 1.dp,
                color = if (isTransmitting || isRemoteActive) stateColor else colors.surfaceBorder,
                shape = IntercomTokens.ShapeCard
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // State Icon Indicator Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(stateColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
            }

            // Labels
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = titleText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }

            // Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(stateColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = stateColor
                )
            }
        }

        // Optional 4-Segment Discrete LED Bar
        if (showDiscreteLed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0..3) {
                    val threshold = (i + 1) * 0.25f
                    val isSegmentActive = audioLevel >= threshold
                    val segmentColor = when {
                        !isSegmentActive -> colors.surfaceBorder
                        i == 3 -> colors.signalWarning
                        isTransmitting -> colors.signalActive
                        else -> colors.signalSuccess
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(segmentColor)
                    )
                }
            }
        }
    }
}
