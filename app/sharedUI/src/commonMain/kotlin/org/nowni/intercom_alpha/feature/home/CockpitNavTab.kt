package org.nowni.intercom_alpha.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Bottom navigation tab for the Main Cockpit.
 *
 * Three tabs only: Peers, Messages, SOS.
 * SOS is always crimson (emergency affordance).
 * Active state indicated by 2dp neobrutal border + surface elevation.
 * 84dp minimum touch target for glove operation.
 */
@Composable
fun CockpitNavTab(
    icon: String,
    label: String,
    count: Int? = null,
    isSelected: Boolean = false,
    isEmergency: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = when {
        isEmergency -> if (isSelected) IntercomTokens.SignalEmergencyCrimson else Color(0x33FF3B30)
        isSelected -> IntercomTokens.SignalAmber
        else -> IntercomTokens.NeobrutalBorderIdle
    }

    val surfaceColor = when {
        isEmergency -> Color(0x1AFF3B30)
        isSelected -> IntercomTokens.CockpitSurfaceElevated
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .size(84.dp)
            .background(surfaceColor)
            .neobrutalistBorder(
                color = borderColor,
                thickness = if (isSelected) 2.dp else 1.dp
            )
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            color = when {
                isEmergency -> if (isSelected) Color(0xFFFF3B30) else Color(0xFF8B949E)
                isSelected -> Color(0xFF00E5FF)
                else -> Color(0xFF8B949E)
            }
        )
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isEmergency -> if (isSelected) Color(0xFFFF3B30) else Color(0xFF8B949E)
                isSelected -> Color(0xFF00E5FF)
                else -> Color(0xFF8B949E)
            }
        )
        count?.let { c ->
            if (c > 0) {
                Text(
                    text = c.toString(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = when {
                        isSelected -> Color(0xFF00E5FF)
                        else -> Color(0xFF58A6FF)
                    }
                )
            }
        }
    }
}
