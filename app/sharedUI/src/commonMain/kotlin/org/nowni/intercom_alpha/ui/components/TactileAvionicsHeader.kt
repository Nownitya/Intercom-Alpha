package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Tactical Top Avionics Header for Outdoor Cockpit.
 * High-contrast, glanceable telemetry chips:
 * - Group Name & Leader badge
 * - Live Peer Count Chip
 * - RF / Mesh Link Status Chip (Coded PHY / Mesh / Cellular)
 * - Battery / Power Mode Chip
 */
@Composable
fun TactileAvionicsHeader(
    groupName: String,
    peerCount: Int,
    isCloudConnected: Boolean,
    batteryPercentage: Int,
    isLowPower: Boolean,
    onOpenPeersSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .claymorphic(
                surfaceColor = IntercomTokens.CockpitChassis,
                shape = RoundedCornerShape(14.dp)
            )
            .neobrutalistBorder(
                color = IntercomTokens.NeobrutalBorderIdle,
                thickness = 1.5.dp,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Group Name & Tactical Call-Sign
        Column {
            Text(
                text = groupName.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(IntercomTokens.SignalCodedPhyEmerald)
                )
                Text(
                    text = if (isCloudConnected) "HYBRID MESH+CLOUD" else "OFF-GRID BLE MESH",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = IntercomTokens.SignalCodedPhyEmerald
                )
            }
        }

        // 2. Right Telemetry Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Peer Count Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF21262D))
                    .neobrutalistBorder(
                        color = IntercomTokens.SignalAmber,
                        thickness = 1.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onOpenPeersSheet() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${peerCount} NODES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = IntercomTokens.SignalAmber
                )
            }

            // Battery / Power Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isLowPower) Color(0xFF2E1912) else Color(0xFF21262D))
                    .neobrutalistBorder(
                        color = if (isLowPower) IntercomTokens.SignalAmber else Color(0x33FFFFFF),
                        thickness = 1.dp,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${batteryPercentage}%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (isLowPower) IntercomTokens.SignalAmber else Color(0xFFE2E8F0)
                )
            }
        }
    }
}
