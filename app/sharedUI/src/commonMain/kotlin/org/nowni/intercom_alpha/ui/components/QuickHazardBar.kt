package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.text.CannedMessageCode

/**
 * 1-Tap Tactical Hazard Macro Bar for motorcycle cockpits.
 * Single-row 4-column layout guaranteeing 52dp minimum touch envelopes for gloved hands.
 */
@Composable
fun QuickHazardBar(
    unreadCount: Int,
    onSendHazard: (CannedMessageCode) -> Unit,
    onOpenMessages: () -> Unit,
    onTriggerSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TACTICAL HAZARD MACROS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color(0xFFFA5C22)
            )

            // Direct link to chat / custom messages drawer
            TextButton(
                onClick = onOpenMessages,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (unreadCount > 0) "💬 Messages ($unreadCount)" else "💬 Log",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E5FF)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4-Column Glove-Friendly Action Row (52dp height)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Road Hazard
            HazardMacroButton(
                icon = "⚠️",
                title = "HAZARD",
                subtext = "Debris",
                borderColor = Color(0xFFFA5C22),
                modifier = Modifier.weight(1f),
                onClick = { onSendHazard(CannedMessageCode.HAZARD_ROAD) }
            )

            // 2. Low Fuel
            HazardMacroButton(
                icon = "⛽",
                title = "FUEL",
                subtext = "Pitstop",
                borderColor = Color(0xFF00E5FF),
                modifier = Modifier.weight(1f),
                onClick = { onSendHazard(CannedMessageCode.FUEL_LOW) }
            )

            // 3. Stop Immediate
            HazardMacroButton(
                icon = "🛑",
                title = "STOP",
                subtext = "Regroup",
                borderColor = Color(0xFFFF5252),
                containerColor = Color(0xFF4A1010),
                modifier = Modifier.weight(1f),
                onClick = { onSendHazard(CannedMessageCode.STOP_IMMEDIATE) }
            )

            // 4. Bike Mechanical
            HazardMacroButton(
                icon = "🔧",
                title = "BIKE",
                subtext = "Repair",
                borderColor = Color(0xFF00E676),
                modifier = Modifier.weight(1f),
                onClick = { onSendHazard(CannedMessageCode.MECHANICAL_ISSUE) }
            )
        }
    }
}

@Composable
private fun HazardMacroButton(
    icon: String,
    title: String,
    subtext: String,
    borderColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF1D2023),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(text = icon, fontSize = 13.sp)
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Text(
                text = subtext,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B8C1),
                textAlign = TextAlign.Center
            )
        }
    }
}
