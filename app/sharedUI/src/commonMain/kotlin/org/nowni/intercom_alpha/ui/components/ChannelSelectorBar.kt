package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.channel.ChannelState
import org.nowni.intercom_alpha.channel.IntercomChannel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelSelectorBar(
    channelState: ChannelState,
    onSelectChannel: (Int) -> Unit,
    onToggleDualWatch: (Int) -> Unit,
    onToggleScanMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = channelState.activeChannel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (channelState.monitoredChannelIds.isNotEmpty()) {
                        "Dual-Watch: Ch ${channelState.monitoredChannelIds.joinToString(", ")}"
                    } else {
                        "Single Watch (Long-press channel for DW)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilterChip(
                selected = channelState.isScanMode,
                onClick = onToggleScanMode,
                label = {
                    Text(
                        text = if (channelState.isScanMode) "SCANNING" else "SCAN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(channelState.channels, key = { it.id }) { channel ->
                val isActive = channel.id == channelState.activeChannelId
                val isMonitored = channelState.monitoredChannelIds.contains(channel.id)
                val isEmergency = channel.isEmergencyChannel

                Surface(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onSelectChannel(channel.id) },
                            onLongClick = { onToggleDualWatch(channel.id) }
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isActive && isEmergency -> Color(0xFFD32F2F)
                        isActive -> MaterialTheme.colorScheme.primary
                        isEmergency -> Color(0xFFFFEBEE)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = when {
                        isMonitored -> BorderStroke(2.dp, MaterialTheme.colorScheme.secondary)
                        else -> null
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CH ${channel.id}",
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isActive -> Color.White
                                isEmergency -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        if (isMonitored) {
                            Text(
                                text = "DW",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isActive) Color.White else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
