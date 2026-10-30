package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import org.nowni.intercom_alpha.ui.theme.LocalTacticalColors

@Composable
fun ChannelSelectorStrip(
    activeChannelId: Int,
    monitoredChannelIds: Set<Int> = emptySet(),
    onSelectChannel: (Int) -> Unit,
    onToggleDualWatch: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (channelId in 1..16) {
            val isActive = channelId == activeChannelId
            val isDualWatch = monitoredChannelIds.contains(channelId)

            val background = if (isActive) colors.signalActive else colors.surfaceCard
            val border = when {
                isActive -> colors.signalActive
                isDualWatch -> colors.signalActive.copy(alpha = 0.6f)
                else -> colors.surfaceBorder
            }
            val textColor = when {
                isActive -> androidx.compose.ui.graphics.Color.Black
                isDualWatch -> colors.signalActive
                else -> colors.textPrimary
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .border(1.dp, border, RoundedCornerShape(50))
                    .clickable { onSelectChannel(channelId) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channelId.toString().padStart(2, '0'),
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (isDualWatch && !isActive) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.signalActive)
                    )
                }
            }
        }
    }
}

