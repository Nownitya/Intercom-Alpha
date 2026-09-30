package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.text.CannedMessageCode

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
                text = "Quick Hazard & Messages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Messages Button with Unread Badge
                FilledTonalButton(
                    onClick = onOpenMessages,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (unreadCount > 0) "💬 Messages ($unreadCount)" else "💬 Messages",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Dedicated SOS Button
                Button(
                    onClick = onTriggerSos,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "🚨 SOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                HazardPill(
                    icon = "⛽",
                    label = "Fuel Low",
                    onClick = { onSendHazard(CannedMessageCode.FUEL_LOW) },
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                )
            }
            item {
                HazardPill(
                    icon = "⚠️",
                    label = "Road Hazard",
                    onClick = { onSendHazard(CannedMessageCode.HAZARD_ROAD) },
                    containerColor = Color(0xFFFFFDE7),
                    contentColor = Color(0xFFF57F17)
                )
            }
            item {
                HazardPill(
                    icon = "🛑",
                    label = "Stop Immediate",
                    onClick = { onSendHazard(CannedMessageCode.STOP_IMMEDIATE) },
                    containerColor = Color(0xFFFFEBEE),
                    contentColor = Color(0xFFC62828)
                )
            }
            item {
                HazardPill(
                    icon = "🔧",
                    label = "Bike Issue",
                    onClick = { onSendHazard(CannedMessageCode.MECHANICAL_ISSUE) },
                    containerColor = Color(0xFFEDE7F6),
                    contentColor = Color(0xFF4527A0)
                )
            }
            item {
                HazardPill(
                    icon = "🔄",
                    label = "Regroup",
                    onClick = { onSendHazard(CannedMessageCode.REGROUP) },
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun HazardPill(
    icon: String,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
