package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun CockpitHeader(
    groupName: String,
    peerCount: Int,
    activeChannelId: Int,
    phyMode: String = "1M",
    channelUtilization: Float = 0.0f,
    isCongested: Boolean = false,
    batteryPercentage: Int = 85,
    isCloudConnected: Boolean = false,
    onOpenRoster: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current

    val phyColor = when (phyMode.uppercase()) {
        "2M", "LE_2M" -> colors.signalSuccess
        "1M", "LE_1M" -> colors.signalActive
        "S8", "CODED_S8", "S2", "CODED_S2" -> colors.signalWarning
        else -> colors.signalActive
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Node count pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceCard)
                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(50))
                .clickable { onOpenRoster() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.signalSuccess)
            )
            Text(
                text = "$peerCount NODES",
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        // Active Channel Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(IntercomTokens.SignalAmberMuted)
                .border(1.dp, colors.signalActive.copy(alpha = 0.4f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CH ${activeChannelId.toString().padStart(2, '0')}",
                color = colors.signalActive,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }

        // Diagnostics, RF PHY & Battery Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceCard)
                .border(1.dp, colors.surfaceBorder, RoundedCornerShape(50))
                .clickable { onOpenDiagnostics() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Channel Congestion Warning Badge (if RF saturation > 70% or throttled)
            if (isCongested || channelUtilization >= 0.70f) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.signalEmergency.copy(alpha = 0.2f))
                        .border(1.dp, colors.signalEmergency.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.signalEmergency)
                    )
                    Text(
                        text = "CBR ${(channelUtilization * 100).toInt()}%",
                        color = colors.signalEmergency,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // Tactile RF PHY mode pill (2M / 1M / S8)
            Text(
                text = phyMode.removePrefix("LE_").removePrefix("CODED_"),
                color = phyColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(phyColor.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )

            // Battery %
            Text(
                text = "$batteryPercentage%",
                color = if (batteryPercentage <= 20) colors.signalEmergency else colors.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

