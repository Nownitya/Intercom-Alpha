package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nowni.intercom_alpha.text.DeliveryStatus
import org.nowni.intercom_alpha.text.MeshTextMessage
import org.nowni.intercom_alpha.text.MessageCategory
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Tactical Convoy Text & Hazard Transcript Dialog.
 * Displays real-time BLE mesh messages with high-contrast neobrutalist bubbles,
 * monospace tabular timestamps, and glove-friendly hazard chips.
 */
@Composable
fun MessageHistoryDialog(
    messages: List<MeshTextMessage>,
    onSendCustomText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .claymorphic(
                    surfaceColor = IntercomTokens.CockpitChassis,
                    shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
                )
                .neobrutalistBorder(
                    color = IntercomTokens.NeobrutalBorderIdle,
                    thickness = IntercomTokens.NeobrutalBorderThickness,
                    shape = RoundedCornerShape(IntercomTokens.CardCornerRadius)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONVOY MESH MESSAGES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                            color = IntercomTokens.SignalSatelliteCyan
                        )
                        Text(
                            text = "Off-Grid BLE Mesh Text Channel",
                            fontSize = 10.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CLOSE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = IntercomTokens.SignalSatelliteCyan
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = IntercomTokens.CockpitSurfaceElevated
                )

                // Messages list
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages exchanged yet.\nUse quick hazard buttons or type below.",
                            fontSize = 12.sp,
                            color = Color(0xFF7A8B9E),
                            fontFamily = FontFamily.Monospace,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true
                    ) {
                        items(messages.reversed(), key = { it.id }) { message ->
                            MessageItem(message = message)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Text input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Tactical mesh message...",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF7A8B9E)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IntercomTokens.SignalSatelliteCyan,
                            unfocusedBorderColor = IntercomTokens.NeobrutalBorderIdle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendCustomText(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IntercomTokens.SignalSatelliteCyan,
                            contentColor = IntercomTokens.CockpitObsidian,
                            disabledContainerColor = IntercomTokens.CockpitSurfaceElevated,
                            disabledContentColor = Color(0xFF7A8B9E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "SEND",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: MeshTextMessage) {
    val isHazard = message.category == MessageCategory.CANNED_HAZARD
    val bubbleBackground = if (isHazard) {
        IntercomTokens.SignalHotMicOrange.copy(alpha = 0.15f)
    } else {
        IntercomTokens.CockpitSurfaceElevated
    }

    val borderColor = if (isHazard) {
        IntercomTokens.SignalHotMicOrange
    } else {
        IntercomTokens.NeobrutalBorderIdle
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = bubbleBackground,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isHazard) {
                        Text(text = "⚠️", fontSize = 12.sp)
                    }
                    Text(
                        text = message.senderName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isHazard) IntercomTokens.SignalHotMicOrange else IntercomTokens.SignalSatelliteCyan
                    )
                }

                Text(
                    text = when (message.status) {
                        DeliveryStatus.ACKNOWLEDGED -> "✓✓ ACK"
                        DeliveryStatus.DELIVERED -> "✓ DELIV"
                        DeliveryStatus.SENT -> "SENT"
                        DeliveryStatus.FAILED -> "FAILED"
                        DeliveryStatus.PENDING -> "PEND"
                    },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = when (message.status) {
                        DeliveryStatus.ACKNOWLEDGED -> IntercomTokens.SignalCodedPhyEmerald
                        DeliveryStatus.FAILED -> IntercomTokens.SignalEmergencyCrimson
                        else -> Color(0xFF7A8B9E)
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = message.content,
                fontSize = 13.sp,
                fontWeight = if (isHazard) FontWeight.Black else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }
    }
}
