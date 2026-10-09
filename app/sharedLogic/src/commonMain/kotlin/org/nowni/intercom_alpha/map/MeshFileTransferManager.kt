package org.nowni.intercom_alpha.map

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutex-Coordinated Over-the-Air Route & Trail File Transfer Manager.
 * Orchestrates multi-node mesh chunking, loss-tolerant retransmissions,
 * and persistent in-memory trail libraries.
 */
class MeshFileTransferManager(
    val qosScheduler: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler? = null
) {

    private val mutex = Mutex()

    private val _activeTransfers = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    val activeTransfers: StateFlow<List<FileTransferProgress>> = _activeTransfers.asStateFlow()

    private val _storedTrails = MutableStateFlow<List<TrailPolyline>>(emptyList())
    val storedTrails: StateFlow<List<TrailPolyline>> = _storedTrails.asStateFlow()

    private val _completedTrailReceived = MutableSharedFlow<Pair<TrailPolyline, List<TrailWaypoint>>>(extraBufferCapacity = 16)
    val completedTrailReceived: SharedFlow<Pair<TrailPolyline, List<TrailWaypoint>>> = _completedTrailReceived.asSharedFlow()

    val isThrottled: Boolean
        get() = qosScheduler?.qosSnapshot?.value?.bulkThrottleActive == true

    // Active incoming reassembly buffers keyed by transferId
    private val incomingBuffers = HashMap<String, MeshFileTransferEngine.ReassemblyBuffer>()

    // Active outgoing chunk queues keyed by transferId
    private val outgoingQueues = HashMap<String, List<FileTransferChunk>>()

    // Outbound mesh transmission hooks
    var onSendChunk: (suspend (chunk: FileTransferChunk, recipientId: String?) -> Unit)? = null
    var onSendAck: (suspend (ack: FileTransferAck, recipientId: String?) -> Unit)? = null

    init {
        // Pre-populate with standard Alpine Pass route for instant zero-config testing
        val defaultTrail = TrailMapEngine.createSampleAlpineTrail(GeoPoint(37.7749, -122.4194, 2450.0)).first
        _storedTrails.value = listOf(defaultTrail)
    }

    private suspend fun applyQoSPacing() {
        val sched = qosScheduler ?: return
        val snapshot = sched.qosSnapshot.value
        if (snapshot.isVoiceActive || snapshot.bulkThrottleActive) {
            // Enforce <= 10% RF duty cycle during active voice or heavy airtime (pacing >= 150ms)
            val delayMs = sched.calculatePacingDelayMs(org.nowni.intercom_alpha.mesh.qos.QoSPriority.BULK_TELEMETRY).coerceAtLeast(150L)
            kotlinx.coroutines.delay(delayMs)
        } else {
            val delayMs = sched.calculatePacingDelayMs(org.nowni.intercom_alpha.mesh.qos.QoSPriority.BULK_TELEMETRY)
            if (delayMs > 0L) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    /**
     * Initiates an over-the-air transfer of a trail polyline to convoy peers over the BLE mesh.
     * Returns the assigned unique transferId.
     */
    suspend fun startSendTrail(
        trail: TrailPolyline,
        waypoints: List<TrailWaypoint> = emptyList(),
        format: FileTransferType = FileTransferType.GPX,
        recipientPeerId: String? = null
    ): String = mutex.withLock {
        val transferId = "tx-" + (trail.name.hashCode() xor format.hashCode()).toUInt().toString(16).take(8)
        val fileText = when (format) {
            FileTransferType.GPX -> MeshFileTransferEngine.exportToGpx(trail, waypoints)
            FileTransferType.GEOJSON -> MeshFileTransferEngine.exportToGeoJson(trail, waypoints)
            FileTransferType.WAYPOINT_PACK -> MeshFileTransferEngine.exportToGpx(trail, waypoints)
        }
        val fileBytes = fileText.encodeToByteArray()
        val extension = if (format == FileTransferType.GPX) "gpx" else "geojson"
        val fileName = "${trail.name.replace(" ", "_")}.$extension"

        val chunks = MeshFileTransferEngine.chunkData(
            bytes = fileBytes,
            fileName = fileName,
            fileType = format,
            transferId = transferId
        )

        outgoingQueues[transferId] = chunks

        val progress = FileTransferProgress(
            transferId = transferId,
            fileName = fileName,
            fileType = format,
            direction = FileTransferDirection.OUTGOING,
            peerId = recipientPeerId ?: "BROADCAST",
            chunksTransferred = 0,
            totalChunks = chunks.size,
            bytesTransferred = 0L,
            totalBytes = fileBytes.size.toLong(),
            status = FileTransferStatus.IN_PROGRESS
        )

        _activeTransfers.value = _activeTransfers.value.filter { it.transferId != transferId } + progress

        // Send initial window (up to first 4 chunks or all)
        val initialBatch = chunks.take(minOf(4, chunks.size))
        val sendAction = onSendChunk
        if (sendAction != null) {
            for (chunk in initialBatch) {
                applyQoSPacing()
                sendAction(chunk, recipientPeerId)
            }
        }

        updateProgress(transferId) { it.copy(chunksTransferred = initialBatch.size) }

        return@withLock transferId
    }

    /**
     * Ingests an incoming mesh file chunk from an over-the-air peer.
     * Returns an ACK or selective NACK if appropriate.
     */
    suspend fun handleIncomingChunk(
        chunk: FileTransferChunk,
        senderPeerId: String
    ): FileTransferAck? = mutex.withLock {
        var buffer = incomingBuffers[chunk.transferId]
        if (buffer == null) {
            buffer = MeshFileTransferEngine.ReassemblyBuffer(
                transferId = chunk.transferId,
                fileName = chunk.fileName,
                fileType = chunk.fileType,
                totalChunks = chunk.totalChunks,
                totalBytes = chunk.totalBytes,
                expectedChecksum = chunk.adler32Checksum
            )
            incomingBuffers[chunk.transferId] = buffer

            val progress = FileTransferProgress(
                transferId = chunk.transferId,
                fileName = chunk.fileName,
                fileType = chunk.fileType,
                direction = FileTransferDirection.INCOMING,
                peerId = senderPeerId,
                chunksTransferred = 0,
                totalChunks = chunk.totalChunks,
                bytesTransferred = 0L,
                totalBytes = chunk.totalBytes,
                status = FileTransferStatus.IN_PROGRESS
            )
            _activeTransfers.value = _activeTransfers.value.filter { it.transferId != chunk.transferId } + progress
        }

        val wasNew = buffer.addChunk(chunk)
        if (wasNew) {
            updateProgress(chunk.transferId) {
                it.copy(
                    chunksTransferred = buffer.receivedCount,
                    bytesTransferred = (it.bytesTransferred + chunk.data.size).coerceAtMost(chunk.totalBytes)
                )
            }
        }

        if (buffer.isComplete) {
            val bytes = buffer.reassemble()
            if (bytes != null) {
                val fileText = bytes.decodeToString()
                val parsed = when (chunk.fileType) {
                    FileTransferType.GPX -> MeshFileTransferEngine.parseGpx(fileText, chunk.fileName.removeSuffix(".gpx"))
                    FileTransferType.GEOJSON -> {
                        val (trails, waypoints) = TrailMapEngine.parseGeoJson(fileText)
                        Pair(trails.firstOrNull(), waypoints)
                    }
                    FileTransferType.WAYPOINT_PACK -> MeshFileTransferEngine.parseGpx(fileText, chunk.fileName)
                }

                val trail = parsed.first
                if (trail != null) {
                    _storedTrails.value = _storedTrails.value.filter { it.id != trail.id } + trail
                    _completedTrailReceived.tryEmit(Pair(trail, parsed.second))
                }

                updateProgress(chunk.transferId) {
                    it.copy(
                        status = FileTransferStatus.COMPLETED,
                        chunksTransferred = chunk.totalChunks,
                        bytesTransferred = chunk.totalBytes
                    )
                }

                incomingBuffers.remove(chunk.transferId)

                val ack = FileTransferAck(
                    transferId = chunk.transferId,
                    chunkIndex = chunk.chunkIndex,
                    missingChunks = emptyList(),
                    isComplete = true
                )
                onSendAck?.invoke(ack, senderPeerId)
                return@withLock ack
            } else {
                // Checksum failure
                updateProgress(chunk.transferId) {
                    it.copy(
                        status = FileTransferStatus.FAILED,
                        errorMessage = "Adler32 Checksum Integrity Mismatch"
                    )
                }
                incomingBuffers.remove(chunk.transferId)
                return@withLock null
            }
        }

        // Send intermediate ACK with selective NACK list if needed
        val missing = buffer.getMissingChunkIndices()
        val ack = FileTransferAck(
            transferId = chunk.transferId,
            chunkIndex = chunk.chunkIndex,
            missingChunks = missing,
            isComplete = false
        )
        onSendAck?.invoke(ack, senderPeerId)
        return@withLock ack
    }

    /**
     * Handles reception of a transfer ACK from a recipient peer.
     * Triggers retransmission of missing chunks if selective NACKs are present.
     */
    suspend fun handleIncomingAck(ack: FileTransferAck): Unit = mutex.withLock {
        val transfer = _activeTransfers.value.find { it.transferId == ack.transferId } ?: return@withLock
        if (transfer.direction != FileTransferDirection.OUTGOING) return@withLock

        if (ack.isComplete) {
            updateProgress(ack.transferId) {
                it.copy(
                    status = FileTransferStatus.COMPLETED,
                    chunksTransferred = it.totalChunks,
                    bytesTransferred = it.totalBytes
                )
            }
            outgoingQueues.remove(ack.transferId)
            return@withLock
        }

        // Retransmit requested missing chunks
        val queue = outgoingQueues[ack.transferId] ?: return@withLock
        val sendAction = onSendChunk
        if (sendAction != null && ack.missingChunks.isNotEmpty()) {
            for (missingIdx in ack.missingChunks) {
                val chunk = queue.getOrNull(missingIdx)
                if (chunk != null) {
                    applyQoSPacing()
                    sendAction(chunk, transfer.peerId.takeIf { it != "BROADCAST" })
                }
            }
        }
    }

    /**
     * Dispatches the next batch of un-transmitted chunks for an ongoing transfer,
     * applying QoS airtime throttling and pacing.
     */
    suspend fun continueTransfer(transferId: String, batchSize: Int = 4): Int = mutex.withLock {
        val transfer = _activeTransfers.value.find { it.transferId == transferId } ?: return@withLock 0
        if (transfer.direction != FileTransferDirection.OUTGOING || transfer.status != FileTransferStatus.IN_PROGRESS) {
            return@withLock 0
        }
        val queue = outgoingQueues[transferId] ?: return@withLock 0
        val startIndex = transfer.chunksTransferred
        if (startIndex >= queue.size) return@withLock 0

        val endIndex = minOf(startIndex + batchSize, queue.size)
        val batch = queue.subList(startIndex, endIndex)
        val sendAction = onSendChunk
        var dispatched = 0

        if (sendAction != null) {
            for (chunk in batch) {
                applyQoSPacing()
                sendAction(chunk, transfer.peerId.takeIf { it != "BROADCAST" })
                dispatched++
            }
        }

        updateProgress(transferId) {
            it.copy(
                chunksTransferred = endIndex,
                bytesTransferred = (it.bytesTransferred + batch.sumOf { c -> c.data.size.toLong() }).coerceAtMost(transfer.totalBytes)
            )
        }

        return@withLock dispatched
    }

    /**
     * Cancels an active file transfer.
     */
    suspend fun cancelTransfer(transferId: String): Unit = mutex.withLock {
        updateProgress(transferId) {
            it.copy(status = FileTransferStatus.CANCELLED)
        }
        incomingBuffers.remove(transferId)
        outgoingQueues.remove(transferId)
    }

    /**
     * Imports a trail directly from raw text (GPX or GeoJSON) and adds to the library.
     */
    suspend fun importTrailFromText(
        text: String,
        fileName: String,
        format: FileTransferType = FileTransferType.GPX
    ): TrailPolyline? = mutex.withLock {
        val parsed = when (format) {
            FileTransferType.GPX -> MeshFileTransferEngine.parseGpx(text, fileName.removeSuffix(".gpx"))
            FileTransferType.GEOJSON -> {
                val (trails, waypoints) = TrailMapEngine.parseGeoJson(text)
                Pair(trails.firstOrNull(), waypoints)
            }
            FileTransferType.WAYPOINT_PACK -> MeshFileTransferEngine.parseGpx(text, fileName)
        }

        val trail = parsed.first
        if (trail != null) {
            _storedTrails.value = _storedTrails.value.filter { it.id != trail.id } + trail
        }
        return@withLock trail
    }

    /**
     * Removes a trail from the stored library.
     */
    suspend fun deleteStoredTrail(trailId: String): Unit = mutex.withLock {
        _storedTrails.value = _storedTrails.value.filter { it.id != trailId }
    }

    private fun updateProgress(transferId: String, transform: (FileTransferProgress) -> FileTransferProgress) {
        _activeTransfers.value = _activeTransfers.value.map {
            if (it.transferId == transferId) transform(it) else it
        }
    }
}
