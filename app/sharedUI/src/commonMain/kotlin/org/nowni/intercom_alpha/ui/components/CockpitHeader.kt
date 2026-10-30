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
    phyMode: String = "2M",
    batteryPercentage: Int = 85,
    isCloudConnected: Boolean = false,
    onOpenRoster: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalTacticalColors.current

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

        // Diagnostics & Battery Pill
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
            // PHY mode tag
            Text(
                text = phyMode,
                color = colors.signalSuccess,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.signalSuccess.copy(alpha = 0.15f))
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

