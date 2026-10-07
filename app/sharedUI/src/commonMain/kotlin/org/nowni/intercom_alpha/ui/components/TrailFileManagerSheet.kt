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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nowni.intercom_alpha.map.FileTransferDirection
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.FileTransferStatus
import org.nowni.intercom_alpha.map.FileTransferType
import org.nowni.intercom_alpha.map.TrailPolyline

/**
 * Tactical Compose Multiplatform Over-The-Air Trail File Manager & Beam Cockpit Sheet.
 * Provides riders with a 1-tap route beaming actuator, live chunk-by-chunk OTA progress bars,
 * and an offline GPX/GeoJSON trail storage library manager.
 */
@Composable
fun TrailFileManagerSheet(
    storedTrails: List<TrailPolyline>,
    activeTransfers: List<FileTransferProgress>,
    activeTrailId: String?,
    onSelectTrail: (String) -> Unit,
    onBeamTrail: (FileTransferType) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onImportTrail: (fileName: String, text: String, format: FileTransferType) -> Unit,
    onDeleteTrail: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFormat by remember { mutableStateOf(FileTransferType.GPX) }
    var showImportDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14181D)),
            border = BorderStroke(1.dp, Color(0xFF2B3848))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TRAIL FILE MANAGER",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF00E5FF)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1B3B32),
                                border = BorderStroke(0.5.dp, Color(0xFF00E676))
                            ) {
                                Text(
                                    text = "OFF-GRID OTA",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "BLE Mesh Chunked GPX/GeoJSON Exchange",
                            fontSize = 10.sp,
                            color = Color(0xFF7A8B9E)
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("CLOSE", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF), fontSize = 11.sp)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color(0xFF2B3848)
                )

                // 2. Beam Active Trail & Format Selector Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1D2023),
                    border = BorderStroke(1.dp, Color(0xFF2B3848)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "FORMAT:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8B9E)
                            )
                            FilterChip(
                                selected = selectedFormat == FileTransferType.GPX,
                                onClick = { selectedFormat = FileTransferType.GPX },
                                label = { Text("GPX", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF00E5FF)
                                )
                            )
                            FilterChip(
                                selected = selectedFormat == FileTransferType.GEOJSON,
                                onClick = { selectedFormat = FileTransferType.GEOJSON },
                                label = { Text("GeoJSON", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                    selectedLabelColor = Color(0xFF00E5FF)
                                )
                            )
                        }

                        Button(
                            onClick = { onBeamTrail(selectedFormat) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF14181D)
                            )
                        ) {
                            Text("⚡ BEAM TO CONVOY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Live Active Transfers Section
                if (activeTransfers.isNotEmpty()) {
                    Text(
                        text = "LIVE MESH TRANSFERS (${activeTransfers.size})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (transfer in activeTransfers) {
                            ActiveTransferCard(
                                transfer = transfer,
                                onCancel = { onCancelTransfer(transfer.transferId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 4. Stored Routes Library Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STORED TRAILS LIBRARY (${storedTrails.size})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF7A8B9E)
                    )

                    TextButton(
                        onClick = { showImportDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("+ Quick Import", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Stored Trails List
                if (storedTrails.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No routes stored in local library.\nTap '+ Quick Import' or beam from convoy rider.",
                            fontSize = 11.sp,
                            color = Color(0xFF7A8B9E),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(storedTrails) { trail ->
                            val isActive = trail.id == activeTrailId
                            StoredTrailCard(
                                trail = trail,
                                isActive = isActive,
                                onSelect = { onSelectTrail(trail.id) },
                                onBeam = { onBeamTrail(selectedFormat) },
                                onDelete = { onDeleteTrail(trail.id) },
                                canDelete = storedTrails.size > 1
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Import Text Modal
    if (showImportDialog) {
        QuickImportGpxDialog(
            onImport = { fileName, text, format ->
                onImportTrail(fileName, text, format)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }
}

@Composable
private fun ActiveTransferCard(
    transfer: FileTransferProgress,
    onCancel: () -> Unit
) {
    val isOutgoing = transfer.direction == FileTransferDirection.OUTGOING
    val statusColor = when (transfer.status) {
        FileTransferStatus.IN_PROGRESS -> Color(0xFF00E5FF)
        FileTransferStatus.COMPLETED -> Color(0xFF00E676)
        FileTransferStatus.FAILED -> Color(0xFFFF5252)
        FileTransferStatus.CANCELLED -> Color(0xFF7A8B9E)
        FileTransferStatus.PENDING -> Color(0xFFFFB300)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1D2023),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
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
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isOutgoing) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF00E676).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isOutgoing) "⬆ BEAMING" else "⬇ RECEIVING",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isOutgoing) Color(0xFF00E5FF) else Color(0xFF00E676),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = transfer.fileName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (transfer.status == FileTransferStatus.IN_PROGRESS) {
                    FilledTonalButton(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2B3848),
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text("CANCEL", fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    Text(
                        text = transfer.status.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { transfer.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = statusColor,
                trackColor = Color(0xFF2B3848)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${transfer.chunksTransferred}/${transfer.totalChunks} Chunks (${(transfer.progressPercent * 100).toInt()}%)",
                    fontSize = 9.sp,
                    color = Color(0xFF7A8B9E),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transfer.bytesTransferred} / ${transfer.totalBytes} B · Peer: ${transfer.peerId}",
                    fontSize = 8.sp,
                    color = Color(0xFF7A8B9E)
                )
            }
        }
    }
}

@Composable
private fun StoredTrailCard(
    trail: TrailPolyline,
    isActive: Boolean,
    onSelect: () -> Unit,
    onBeam: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) Color(0xFF1B2E3B) else Color(0xFF1D2023),
        border = BorderStroke(1.dp, if (isActive) Color(0xFF00E5FF) else Color(0xFF2B3848)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = trail.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, Color(0xFF00E676))
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00E676),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val distKm = (trail.totalDistanceMeters / 100.0).toInt() / 10.0
                    Text(
                        text = "📏 ${distKm}km",
                        fontSize = 9.sp,
                        color = Color(0xFF7A8B9E),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⛰️ +${trail.totalElevationGainMeters.toInt()}m",
                        fontSize = 9.sp,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📍 ${trail.points.size} pts",
                        fontSize = 9.sp,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isActive) {
                    FilledTonalButton(
                        onClick = onSelect,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2B3848),
                            contentColor = Color(0xFF00E5FF)
                        )
                    ) {
                        Text("LOAD", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = onBeam,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        contentColor = Color(0xFF00E5FF)
                    )
                ) {
                    Text("BEAM", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                if (canDelete && !isActive) {
                    FilledTonalButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF2B3848),
                            contentColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text("✕", fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickImportGpxDialog(
    onImport: (fileName: String, text: String, format: FileTransferType) -> Unit,
    onDismiss: () -> Unit
) {
    var trailName by remember { mutableStateOf("Alpine Trail Track") }

    val sampleGpxText = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="IntercomAlpha">
          <metadata><name>$trailName</name></metadata>
          <trk>
            <name>$trailName</name>
            <trkseg>
              <trkpt lat="37.7749" lon="-122.4194"><ele>2450.0</ele></trkpt>
              <trkpt lat="37.7760" lon="-122.4210"><ele>2480.0</ele></trkpt>
              <trkpt lat="37.7780" lon="-122.4230"><ele>2520.0</ele></trkpt>
              <trkpt lat="37.7800" lon="-122.4250"><ele>2560.0</ele></trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "IMPORT GPX ROUTE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF00E5FF)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Name of route to add to local off-grid library:",
                    fontSize = 11.sp,
                    color = Color.White
                )
                OutlinedTextField(
                    value = trailName,
                    onValueChange = { trailName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2B3848),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Text(
                    text = "Simulates instant offline file import of raw GPX 1.1 coordinates.",
                    fontSize = 9.sp,
                    color = Color(0xFF7A8B9E)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeName = trailName.ifBlank { "Imported_Trail" }
                    onImport("${safeName.replace(" ", "_")}.gpx", sampleGpxText, FileTransferType.GPX)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF14181D))
            ) {
                Text("IMPORT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF7A8B9E))
            }
        },
        containerColor = Color(0xFF14181D),
        shape = RoundedCornerShape(12.dp)
    )
}
