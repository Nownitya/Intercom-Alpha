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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.nowni.intercom_alpha.map.FileTransferDirection
import org.nowni.intercom_alpha.map.FileTransferProgress
import org.nowni.intercom_alpha.map.FileTransferStatus
import org.nowni.intercom_alpha.map.FileTransferType
import org.nowni.intercom_alpha.map.TrailPolyline
import org.nowni.intercom_alpha.ui.theme.IntercomTokens
import org.nowni.intercom_alpha.ui.theme.claymorphic
import org.nowni.intercom_alpha.ui.theme.neobrutalistBorder

/**
 * Tactical Compose Multiplatform Over-The-Air Trail File Manager & Beam Cockpit Sheet.
 * Provides riders with a 1-tap route beaming actuator, live chunk-by-chunk OTA progress bars,
 * and an offline GPX/GeoJSON trail storage library manager styled with ADR-014 design tokens.
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
        Box(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
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
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                color = IntercomTokens.SignalSatelliteCyan
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = IntercomTokens.SignalCodedPhyEmerald.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, IntercomTokens.SignalCodedPhyEmerald)
                            ) {
                                Text(
                                    text = "OFF-GRID OTA",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = IntercomTokens.SignalCodedPhyEmerald,
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
                        Text(
                            text = "CLOSE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = IntercomTokens.SignalSatelliteCyan,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = IntercomTokens.CockpitSurfaceElevated
                )

                // 2. Beam Active Trail & Format Selector Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = IntercomTokens.CockpitSurfaceElevated,
                    border = BorderStroke(1.dp, IntercomTokens.NeobrutalBorderIdle),
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
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF7A8B9E)
                            )
                            FilterChip(
                                selected = selectedFormat == FileTransferType.GPX,
                                onClick = { selectedFormat = FileTransferType.GPX },
                                label = { Text("GPX", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IntercomTokens.SignalSatelliteCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = IntercomTokens.SignalSatelliteCyan
                                )
                            )
                            FilterChip(
                                selected = selectedFormat == FileTransferType.GEOJSON,
                                onClick = { selectedFormat = FileTransferType.GEOJSON },
                                label = { Text("GeoJSON", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IntercomTokens.SignalSatelliteCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = IntercomTokens.SignalSatelliteCyan
                                )
                            )
                        }

                        Button(
                            onClick = { onBeamTrail(selectedFormat) },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IntercomTokens.SignalSatelliteCyan,
                                contentColor = IntercomTokens.CockpitObsidian
                            )
                        ) {
                            Text(
                                text = "⚡ BEAM TO CONVOY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
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
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        color = IntercomTokens.SignalSatelliteCyan
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
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF7A8B9E)
                    )

                    TextButton(
                        onClick = { showImportDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "+ Quick Import",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = IntercomTokens.SignalSatelliteCyan
                        )
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
        FileTransferStatus.IN_PROGRESS -> IntercomTokens.SignalSatelliteCyan
        FileTransferStatus.COMPLETED -> IntercomTokens.SignalCodedPhyEmerald
        FileTransferStatus.FAILED -> IntercomTokens.SignalEmergencyCrimson
        FileTransferStatus.CANCELLED -> Color(0xFF7A8B9E)
        FileTransferStatus.PENDING -> IntercomTokens.SignalAmber
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = IntercomTokens.CockpitSurfaceElevated,
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
                        color = if (isOutgoing) IntercomTokens.SignalSatelliteCyan.copy(alpha = 0.2f) else IntercomTokens.SignalCodedPhyEmerald.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isOutgoing) "⬆ BEAMING" else "⬇ RECEIVING",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOutgoing) IntercomTokens.SignalSatelliteCyan else IntercomTokens.SignalCodedPhyEmerald,
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
                            containerColor = IntercomTokens.CockpitChassis,
                            contentColor = IntercomTokens.SignalEmergencyCrimson
                        )
                    ) {
                        Text("CANCEL", fontSize = 8.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Text(
                        text = transfer.status.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
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
                trackColor = IntercomTokens.CockpitChassis
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
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transfer.bytesTransferred} / ${transfer.totalBytes} B · Peer: ${transfer.peerId}",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
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
        color = if (isActive) IntercomTokens.CockpitSurfacePressed else IntercomTokens.CockpitSurfaceElevated,
        border = BorderStroke(1.dp, if (isActive) IntercomTokens.SignalSatelliteCyan else IntercomTokens.NeobrutalBorderIdle),
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
                            color = IntercomTokens.SignalCodedPhyEmerald.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, IntercomTokens.SignalCodedPhyEmerald)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = IntercomTokens.SignalCodedPhyEmerald,
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
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⛰️ +${trail.totalElevationGainMeters.toInt()}m",
                        fontSize = 9.sp,
                        color = IntercomTokens.SignalAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📍 ${trail.points.size} pts",
                        fontSize = 9.sp,
                        color = IntercomTokens.SignalSatelliteCyan,
                        fontFamily = FontFamily.Monospace,
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
                            containerColor = IntercomTokens.CockpitChassis,
                            contentColor = IntercomTokens.SignalSatelliteCyan
                        )
                    ) {
                        Text("LOAD", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                FilledTonalButton(
                    onClick = onBeam,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = IntercomTokens.SignalSatelliteCyan.copy(alpha = 0.2f),
                        contentColor = IntercomTokens.SignalSatelliteCyan
                    )
                ) {
                    Text("BEAM", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                if (canDelete && !isActive) {
                    FilledTonalButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = IntercomTokens.CockpitChassis,
                            contentColor = IntercomTokens.SignalEmergencyCrimson
                        )
                    ) {
                        Text("✕", fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
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
                fontFamily = FontFamily.Monospace,
                color = IntercomTokens.SignalSatelliteCyan
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
                        focusedBorderColor = IntercomTokens.SignalSatelliteCyan,
                        unfocusedBorderColor = IntercomTokens.NeobrutalBorderIdle,
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = IntercomTokens.SignalSatelliteCyan,
                    contentColor = IntercomTokens.CockpitObsidian
                )
            ) {
                Text("IMPORT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color(0xFF7A8B9E), fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = IntercomTokens.CockpitChassis,
        shape = RoundedCornerShape(12.dp)
    )
}
