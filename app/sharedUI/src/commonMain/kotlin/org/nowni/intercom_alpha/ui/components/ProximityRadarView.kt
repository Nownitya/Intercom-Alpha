package org.nowni.intercom_alpha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nowni.intercom_alpha.mesh.ProximityZone
import org.nowni.intercom_alpha.mesh.RadarSnapshot
import org.nowni.intercom_alpha.mesh.RadarTarget
import org.nowni.intercom_alpha.telemetry.ConvoyRiderPosition
import org.nowni.intercom_alpha.telemetry.ConvoyTelemetrySnapshot

@Composable
fun ProximityRadarView(
    radarSnapshot: RadarSnapshot,
    convoySnapshot: ConvoyTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                    Text(
                        text = "Rider Radar & Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val activeCount = maxOf(radarSnapshot.activeRidersCount, convoySnapshot.activeCount)
                Text(
                    text = "$activeCount in range",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Closest rider / convoy strip
            val closestRider = convoySnapshot.closestRider
            val closestTarget = radarSnapshot.closestTarget

            if (closestRider != null) {
                RiderTelemetryRow(rider = closestRider)
            } else if (closestTarget != null) {
                RadarTargetRow(target = closestTarget)
            } else {
                Text(
                    text = "No convoy members detected within RF proximity.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (convoySnapshot.riders.size > 1) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(convoySnapshot.riders, key = { it.peerId }) { rider ->
                        CompactRiderPill(rider = rider)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderTelemetryRow(rider: ConvoyRiderPosition) {
    val bearingArrow = getBearingArrow(rider.relativeBearingDegrees)
    val distanceDisplay = if (rider.distanceMeters < 1000.0) {
        "${rider.distanceMeters.toInt()}m"
    } else {
        "${(rider.distanceMeters / 1000.0 * 10).toInt() / 10.0}km"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = bearingArrow.first,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = rider.peerName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = bearingArrow.second,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = distanceDisplay,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RadarTargetRow(target: RadarTarget) {
    val zoneColor = when (target.zone) {
        ProximityZone.NEAR -> Color(0xFF43A047)
        ProximityZone.MEDIUM -> Color(0xFF1E88E5)
        ProximityZone.FAR -> Color(0xFFFB8C00)
        ProximityZone.OUT_OF_RANGE -> Color.Gray
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = target.displayName ?: target.peerId,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Margin: ${target.linkMarginDb.toInt()} dB • RSSI: ${target.rssi} dBm",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = zoneColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = "${target.estimatedDistanceMeters.toInt()}m (${target.zone.name})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = zoneColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun CompactRiderPill(rider: ConvoyRiderPosition) {
    val arrow = getBearingArrow(rider.relativeBearingDegrees).first
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = arrow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = rider.peerName, fontSize = 11.sp)
            Text(
                text = "${rider.distanceMeters.toInt()}m",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun getBearingArrow(relativeBearing: Double): Pair<String, String> {
    return when {
        relativeBearing in -22.5..22.5 -> "↑" to "Ahead"
        relativeBearing in 22.5..67.5 -> "↗" to "Front-Right"
        relativeBearing in 67.5..112.5 -> "→" to "Right"
        relativeBearing in 112.5..157.5 -> "↘" to "Behind-Right"
        relativeBearing in -67.5..-22.5 -> "↖" to "Front-Left"
        relativeBearing in -112.5..-67.5 -> "←" to "Left"
        relativeBearing in -157.5..-112.5 -> "↙" to "Behind-Left"
        else -> "↓" to "Behind"
    }
}
