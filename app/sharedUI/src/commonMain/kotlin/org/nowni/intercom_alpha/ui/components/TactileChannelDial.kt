package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Tactical Skeuomorphic Channel Selector with Rotary Detent Affordance.
 * Provides instant channel switching between channels 1 through 8 with Dual-Watch indicators.
 */
@Composable
fun TactileChannelDial(
    activeChannelId: Int,
    monitoredChannelIds: Set<Int>,
    onSelectChannel: (Int) -> Unit,
    onToggleDualWatch: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .claymorphic(
                surfaceColor = IntercomTokens.CockpitChassis,
                shape = RoundedCornerShape(12.dp)
            )
            .neobrutalistBorder(
                color = IntercomTokens.NeobrutalBorderIdle,
                thickness = 1.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (ch in 1..8) {
            val isActive = ch == activeChannelId
            val isDualWatch = monitoredChannelIds.contains(ch)

            val bgColor = when {
                isActive -> IntercomTokens.SignalAmber
                isDualWatch -> Color(0xFF1E3A2E)
                else -> Color(0xFF161B22)
            }

            val textColor = when {
                isActive -> Color.Black
                isDualWatch -> IntercomTokens.SignalCodedPhyEmerald
                else -> Color(0xFF8B949E)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .neobrutalistBorder(
                        color = if (isActive) Color.Black else if (isDualWatch) IntercomTokens.SignalCodedPhyEmerald else Color(0x22FFFFFF),
                        thickness = 1.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (isActive) {
                            onToggleDualWatch(ch)
                        } else {
                            onSelectChannel(ch)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CH$ch",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }
    }
}
