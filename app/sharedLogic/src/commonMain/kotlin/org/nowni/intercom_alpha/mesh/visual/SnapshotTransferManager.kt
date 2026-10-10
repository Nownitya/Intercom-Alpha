package org.nowni.intercom_alpha.mesh.visual

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler
import org.nowni.intercom_alpha.mesh.qos.QoSPriority

/**
 * Mutex-Coordinated Tactical Snapshot Transfer Manager.
 * Orchestrates progressive transmission of compressed 0.5 fps thumbnail snapshots
 * over BLE mesh, with loss-tolerant selective NACK recovery and QoS airtime pacing.
 */
class SnapshotTransferManager(
    val qosScheduler: MeshQoSScheduler? = null
) {
    private val mutex = Mutex()

    private val _activeTransfers = MutableStateFlow<List<SnapshotTransferProgress>>(emptyList())
    val activeTransfers: StateFlow<List<SnapshotTransferProgress>> = _activeTransfers.asStateFlow()

    private val _completedSnapshotsReceived = MutableSharedFlow<Pair<SnapshotHeader, ByteArray>>(extraBufferCapacity = 16)
    val completedSnapshotsReceived: SharedFlow<Pair<SnapshotHeader, ByteArray>> = _completedSnapshotsReceived.asSharedFlow()

    private val _latestReceivedSnapshot = MutableStateFlow<Pair<SnapshotHeader, ByteArray>?>(null)
    val latestReceivedSnapshot: StateFlow<Pair<SnapshotHeader, ByteArray>?> = _latestReceivedSnapshot.asStateFlow()

    // Active incoming reassembly buffers keyed by snapshotId
    private val incomingBuffers = HashMap<String, TacticalSnapshotProtocol.ReassemblyBuffer>()

    // Active outgoing queues and headers keyed by snapshotId
    private val outgoingQueues = HashMap<String, List<SnapshotChunk>>()
    private val outgoingHeaders = HashMap<String, SnapshotHeader>()

    // Outbound mesh transmission hooks
    var onSendHeader: (suspend (header: SnapshotHeader, recipientId: String?) -> Unit)? = null
    var onSendChunk: (suspend (chunk: SnapshotChunk, recipientId: String?) -> Unit)? = null
    var onSendAck: (suspend (ack: SnapshotAck, recipientId: String?) -> Unit)? = null

    // Rate limiter state (0.5 fps = min 2000ms frame interval)
    private var lastOutboundTimestamp: Long = 0L
    var minFrameIntervalMs: Long = TacticalSnapshotProtocol.MIN_FRAME_INTERVAL_MS

    val isThrottled: Boolean
        get() = qosScheduler?.qosSnapshot?.value?.bulkThrottleActive == true

    private suspend fun applyQoSPacing() {
        val sched = qosScheduler ?: return
        val snapshot = sched.qosSnapshot.value
        if (snapshot.isVoiceActive || snapshot.bulkThrottleActive) {
            // Enforce <= 10% RF duty cycle during active voice or heavy airtime
            val delayMs = sched.calculatePacingDelayMs(QoSPriority.BULK_TELEMETRY).coerceAtLeast(150L)
            kotlinx.coroutines.delay(delayMs)
        } else {
            val delayMs = sched.calculatePacingDelayMs(QoSPriority.BULK_TELEMETRY)
            if (delayMs > 0L) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    /**
     * Initiates progressive over-the-air transmission of a tactical snapshot frame.
     * Returns the assigned unique snapshotId.
     */
    suspend fun startSendSnapshot(
        payload: ByteArray,
        senderId: String,
        recipientId: String? = null,
        mimeType: SnapshotMimeType = SnapshotMimeType.JPEG,
        width: Int = 160,
        height: Int = 120,
        chunkSize: Int = TacticalSnapshotProtocol.DEFAULT_CHUNK_SIZE_BYTES,
        timestamp: Long = 0L
    ): String = mutex.withLock {
        val snapshotId = "snap-" + (senderId.hashCode() xor payload.size xor timestamp.hashCode()).toUInt().toString(16).take(8)
        val (header, chunks) = TacticalSnapshotProtocol.chunkSnapshot(
            payload = payload,
            snapshotId = snapshotId,
            senderId = senderId,
            timestamp = timestamp,
            chunkSize = chunkSize,
            mimeType = mimeType,
            width = width,
            height = height
        )

        outgoingHeaders[snapshotId] = header
        outgoingQueues[snapshotId] = chunks
        lastOutboundTimestamp = timestamp

        val progress = SnapshotTransferProgress(
            snapshotId = snapshotId,
            senderId = senderId,
            recipientId = recipientId ?: "BROADCAST",
            direction = SnapshotTransferDirection.OUTGOING,
            chunksTransferred = 0,
            totalChunks = chunks.size,
            bytesTransferred = 0L,
            totalBytes = payload.size.toLong(),
            status = SnapshotTransferStatus.IN_PROGRESS
        )

        _activeTransfers.value = _activeTransfers.value.filter { it.snapshotId != snapshotId } + progress

        // Send header broadcast
        onSendHeader?.invoke(header, recipientId)

        // Send initial burst (up to first 4 chunks or all)
        val initialBatch = chunks.take(minOf(4, chunks.size))
        val sendAction = onSendChunk
        if (sendAction != null) {
            for (chunk in initialBatch) {
                applyQoSPacing()
                sendAction(chunk, recipientId)
            }
        }

        updateProgress(snapshotId) {
            it.copy(
                chunksTransferred = initialBatch.size,
                bytesTransferred = initialBatch.sumOf { c -> c.data.size.toLong() }.coerceAtMost(it.totalBytes)
            )
        }

        return@withLock snapshotId
    }

    /**
     * Ingests a snapshot header packet from an over-the-air peer.
     * Pre-allocates the reassembly buffer before chunks arrive.
     */
    suspend fun handleIncomingHeader(
        header: SnapshotHeader,
        senderPeerId: String
    ): Unit = mutex.withLock {
        if (!incomingBuffers.containsKey(header.snapshotId)) {
            incomingBuffers[header.snapshotId] = TacticalSnapshotProtocol.ReassemblyBuffer(header)
            val progress = SnapshotTransferProgress(
                snapshotId = header.snapshotId,
                senderId = header.senderId,
                recipientId = senderPeerId,
                direction = SnapshotTransferDirection.INCOMING,
                chunksTransferred = 0,
                totalChunks = header.totalChunks,
                bytesTransferred = 0L,
                totalBytes = header.totalBytes,
                status = SnapshotTransferStatus.IN_PROGRESS
            )
            _activeTransfers.value = _activeTransfers.value.filter { it.snapshotId != header.snapshotId } + progress
        }
    }

    /**
     * Ingests an incoming snapshot chunk from an over-the-air peer.
     * Generates a reception ACK or selective NACK list for missing chunks.
     */
    suspend fun handleIncomingChunk(
        chunk: SnapshotChunk,
        senderPeerId: String,
        header: SnapshotHeader? = null
    ): SnapshotAck? = mutex.withLock {
        var buffer = incomingBuffers[chunk.snapshotId]
        if (buffer == null) {
            val effHeader = header ?: SnapshotHeader(
                snapshotId = chunk.snapshotId,
                senderId = senderPeerId,
                timestamp = 0L,
                totalBytes = chunk.totalBytes,
                totalChunks = chunk.totalChunks,
                mimeType = SnapshotMimeType.JPEG,
                adler32Checksum = chunk.payloadChecksum
            )
            buffer = TacticalSnapshotProtocol.ReassemblyBuffer(effHeader)
            incomingBuffers[chunk.snapshotId] = buffer

            val progress = SnapshotTransferProgress(
                snapshotId = chunk.snapshotId,
                senderId = effHeader.senderId,
                recipientId = senderPeerId,
                direction = SnapshotTransferDirection.INCOMING,
                chunksTransferred = 0,
                totalChunks = chunk.totalChunks,
                bytesTransferred = 0L,
                totalBytes = chunk.totalBytes,
                status = SnapshotTransferStatus.IN_PROGRESS
            )
            _activeTransfers.value = _activeTransfers.value.filter { it.snapshotId != chunk.snapshotId } + progress
        }

        val wasNew = buffer.addChunk(chunk)
        if (wasNew) {
            updateProgress(chunk.snapshotId) {
                it.copy(
                    chunksTransferred = buffer.receivedCount,
                    bytesTransferred = (it.bytesTransferred + chunk.data.size).coerceAtMost(chunk.totalBytes)
                )
            }
        }

        if (buffer.isComplete) {
            val payload = buffer.reassemble()
            if (payload != null) {
                _latestReceivedSnapshot.value = Pair(buffer.header, payload)
                _completedSnapshotsReceived.tryEmit(Pair(buffer.header, payload))

                updateProgress(chunk.snapshotId) {
                    it.copy(
                        status = SnapshotTransferStatus.COMPLETED,
                        chunksTransferred = chunk.totalChunks,
                        bytesTransferred = chunk.totalBytes
                    )
                }

                incomingBuffers.remove(chunk.snapshotId)

                val ack = SnapshotAck(
                    snapshotId = chunk.snapshotId,
                    chunkIndex = chunk.chunkIndex,
                    receivedChunks = (0 until chunk.totalChunks).toList(),
                    missingChunkIndices = emptyList(),
                    isComplete = true
                )
                onSendAck?.invoke(ack, senderPeerId)
                return@withLock ack
            } else {
                updateProgress(chunk.snapshotId) {
                    it.copy(
                        status = SnapshotTransferStatus.FAILED,
                        errorMessage = "Adler-32 Checksum Integrity Mismatch"
                    )
                }
                incomingBuffers.remove(chunk.snapshotId)
                return@withLock null
            }
        }

        // Return intermediate ACK with missing chunks list (selective NACK)
        val ack = buffer.createAck(lastReceivedIndex = chunk.chunkIndex)
        onSendAck?.invoke(ack, senderPeerId)
        return@withLock ack
    }

    /**
     * Handles reception of a transfer ACK from a recipient peer.
     * Triggers retransmission of missing chunks if selective NACKs are present.
     */
    suspend fun handleIncomingAck(ack: SnapshotAck): Unit = mutex.withLock {
        val transfer = _activeTransfers.value.find { it.snapshotId == ack.snapshotId } ?: return@withLock
        if (transfer.direction != SnapshotTransferDirection.OUTGOING) return@withLock

        if (ack.isComplete) {
            updateProgress(ack.snapshotId) {
                it.copy(
                    status = SnapshotTransferStatus.COMPLETED,
                    chunksTransferred = it.totalChunks,
                    bytesTransferred = it.totalBytes
                )
            }
            outgoingQueues.remove(ack.snapshotId)
            outgoingHeaders.remove(ack.snapshotId)
            return@withLock
        }

        // Retransmit requested missing chunks
        val queue = outgoingQueues[ack.snapshotId] ?: return@withLock
        val sendAction = onSendChunk
        if (sendAction != null && ack.missingChunkIndices.isNotEmpty()) {
            for (missingIdx in ack.missingChunkIndices) {
                val chunk = queue.getOrNull(missingIdx)
                if (chunk != null) {
                    applyQoSPacing()
                    sendAction(chunk, transfer.recipientId?.takeIf { it != "BROADCAST" })
                }
            }
        }
    }

    /**
     * Dispatches the next batch of un-transmitted chunks for an ongoing transfer.
     */
    suspend fun continueTransfer(snapshotId: String, batchSize: Int = 4): Int = mutex.withLock {
        val transfer = _activeTransfers.value.find { it.snapshotId == snapshotId } ?: return@withLock 0
        if (transfer.direction != SnapshotTransferDirection.OUTGOING || transfer.status != SnapshotTransferStatus.IN_PROGRESS) {
            return@withLock 0
        }
        val queue = outgoingQueues[snapshotId] ?: return@withLock 0
        val startIndex = transfer.chunksTransferred
        if (startIndex >= queue.size) return@withLock 0

        val endIndex = minOf(startIndex + batchSize, queue.size)
        val batch = queue.subList(startIndex, endIndex)
        val sendAction = onSendChunk
        var dispatched = 0

        if (sendAction != null) {
            for (chunk in batch) {
                applyQoSPacing()
                sendAction(chunk, transfer.recipientId?.takeIf { it != "BROADCAST" })
                dispatched++
            }
        }

        updateProgress(snapshotId) {
            it.copy(
                chunksTransferred = endIndex,
                bytesTransferred = (it.bytesTransferred + batch.sumOf { c -> c.data.size.toLong() }).coerceAtMost(transfer.totalBytes)
            )
        }

        return@withLock dispatched
    }

    /**
     * Cancels an active snapshot transfer.
     */
    suspend fun cancelTransfer(snapshotId: String): Unit = mutex.withLock {
        updateProgress(snapshotId) {
            it.copy(status = SnapshotTransferStatus.CANCELLED)
        }
        incomingBuffers.remove(snapshotId)
        outgoingQueues.remove(snapshotId)
        outgoingHeaders.remove(snapshotId)
    }

    private fun updateProgress(snapshotId: String, transform: (SnapshotTransferProgress) -> SnapshotTransferProgress) {
        _activeTransfers.value = _activeTransfers.value.map {
            if (it.snapshotId == snapshotId) transform(it) else it
        }
    }
}
