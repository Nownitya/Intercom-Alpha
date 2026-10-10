package org.nowni.intercom_alpha.mesh.visual

import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.map.Adler32

/**
 * Pure Kotlin Multiplatform Protocol for Ultra-Low-Framerate Tactical Snapshot Transport.
 *
 * Implements:
 * - MTU-bounded segmented chunking (<= 128 bytes)
 * - Dual Adler-32 checksum integrity verification (per-chunk + full-payload)
 * - Out-of-order loss-tolerant reassembly
 * - Selective NACK generation for multi-hop BLE mesh channels
 */
object TacticalSnapshotProtocol {

    const val DEFAULT_CHUNK_SIZE_BYTES: Int = 100
    const val MAX_CHUNK_SIZE_BYTES: Int = 128
    const val MAX_PAYLOAD_BYTES: Int = 1536
    const val DEFAULT_FRAMERATE_FPS: Float = 0.5f
    const val MIN_FRAME_INTERVAL_MS: Long = 2000L

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "#type"
    }

    /**
     * Splits a binary compressed snapshot payload into MTU-bounded chunks with Adler-32 validation.
     */
    fun chunkSnapshot(
        payload: ByteArray,
        snapshotId: String,
        senderId: String,
        timestamp: Long = 0L,
        chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES,
        mimeType: SnapshotMimeType = SnapshotMimeType.JPEG,
        width: Int = 160,
        height: Int = 120
    ): Pair<SnapshotHeader, List<SnapshotChunk>> {
        val safeChunkSize = chunkSize.coerceIn(16, MAX_CHUNK_SIZE_BYTES)
        val payloadChecksum = Adler32.checksum(payload)
        val totalBytes = payload.size.toLong()

        if (payload.isEmpty()) {
            val emptyChecksum = Adler32.checksum(ByteArray(0))
            val header = SnapshotHeader(
                snapshotId = snapshotId,
                senderId = senderId,
                timestamp = timestamp,
                totalBytes = 0L,
                totalChunks = 1,
                mimeType = mimeType,
                width = width,
                height = height,
                framerateLimitFps = DEFAULT_FRAMERATE_FPS,
                adler32Checksum = emptyChecksum
            )
            val chunk = SnapshotChunk(
                snapshotId = snapshotId,
                chunkIndex = 0,
                totalChunks = 1,
                totalBytes = 0L,
                data = ByteArray(0),
                chunkChecksum = emptyChecksum,
                payloadChecksum = emptyChecksum
            )
            return Pair(header, listOf(chunk))
        }

        val totalChunks = (payload.size + safeChunkSize - 1) / safeChunkSize
        val header = SnapshotHeader(
            snapshotId = snapshotId,
            senderId = senderId,
            timestamp = timestamp,
            totalBytes = totalBytes,
            totalChunks = totalChunks,
            mimeType = mimeType,
            width = width,
            height = height,
            framerateLimitFps = DEFAULT_FRAMERATE_FPS,
            adler32Checksum = payloadChecksum
        )

        val chunks = ArrayList<SnapshotChunk>(totalChunks)
        for (i in 0 until totalChunks) {
            val start = i * safeChunkSize
            val end = minOf(start + safeChunkSize, payload.size)
            val chunkData = payload.copyOfRange(start, end)
            val chunkChecksum = Adler32.checksum(chunkData)

            chunks.add(
                SnapshotChunk(
                    snapshotId = snapshotId,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    totalBytes = totalBytes,
                    data = chunkData,
                    chunkChecksum = chunkChecksum,
                    payloadChecksum = payloadChecksum
                )
            )
        }

        return Pair(header, chunks)
    }

    /**
     * Loss-tolerant in-memory reassembly buffer for incoming chunked snapshot frames.
     */
    class ReassemblyBuffer(
        val header: SnapshotHeader
    ) {
        val snapshotId: String get() = header.snapshotId
        val totalChunks: Int get() = header.totalChunks
        val totalBytes: Long get() = header.totalBytes
        val expectedChecksum: Long get() = header.adler32Checksum

        private val chunks = HashMap<Int, ByteArray>()

        val receivedCount: Int
            get() = chunks.size

        val isComplete: Boolean
            get() = chunks.size == totalChunks

        /**
         * Ingests a chunk. Validates ID, bounds, and chunk checksum.
         * Returns true if a previously unseen, valid chunk was ingested.
         */
        fun addChunk(chunk: SnapshotChunk): Boolean {
            if (chunk.snapshotId != snapshotId) return false
            if (chunk.chunkIndex < 0 || chunk.chunkIndex >= totalChunks) return false
            if (chunk.payloadChecksum != expectedChecksum) return false

            // Verify per-chunk Adler-32 integrity
            val computedChunkChecksum = Adler32.checksum(chunk.data)
            if (computedChunkChecksum != chunk.chunkChecksum) {
                return false
            }

            if (chunks.containsKey(chunk.chunkIndex)) {
                return false // Duplicate chunk
            }

            chunks[chunk.chunkIndex] = chunk.data
            return true
        }

        fun getReceivedChunkIndices(): List<Int> = chunks.keys.sorted()

        fun getMissingChunkIndices(): List<Int> {
            val missing = ArrayList<Int>()
            for (i in 0 until totalChunks) {
                if (!chunks.containsKey(i)) {
                    missing.add(i)
                }
            }
            return missing
        }

        fun createAck(lastReceivedIndex: Int = -1): SnapshotAck {
            return SnapshotAck(
                snapshotId = snapshotId,
                chunkIndex = if (lastReceivedIndex >= 0) lastReceivedIndex else (chunks.keys.maxOrNull() ?: 0),
                receivedChunks = getReceivedChunkIndices(),
                missingChunkIndices = getMissingChunkIndices(),
                isComplete = isComplete
            )
        }

        /**
         * Reassembles full snapshot byte payload if all chunks have arrived.
         * Verifies Adler-32 checksum against header.
         */
        fun reassemble(): ByteArray? {
            if (!isComplete) return null

            val totalSize = chunks.values.sumOf { it.size }
            val output = ByteArray(totalSize)
            var offset = 0

            for (i in 0 until totalChunks) {
                val chunkData = chunks[i] ?: return null
                chunkData.copyInto(output, offset)
                offset += chunkData.size
            }

            val computedChecksum = Adler32.checksum(output)
            if (computedChecksum != expectedChecksum) {
                return null // Corrupted payload reassembly
            }

            return output
        }
    }

    // --- Wire Serialization Helpers ---

    fun serializeHeader(header: SnapshotHeader): ByteArray =
        json.encodeToString(SnapshotHeader.serializer(), header).encodeToByteArray()

    fun deserializeHeader(bytes: ByteArray): SnapshotHeader =
        json.decodeFromString(SnapshotHeader.serializer(), bytes.decodeToString())

    fun serializeChunk(chunk: SnapshotChunk): ByteArray =
        json.encodeToString(SnapshotChunk.serializer(), chunk).encodeToByteArray()

    fun deserializeChunk(bytes: ByteArray): SnapshotChunk =
        json.decodeFromString(SnapshotChunk.serializer(), bytes.decodeToString())

    fun serializeAck(ack: SnapshotAck): ByteArray =
        json.encodeToString(SnapshotAck.serializer(), ack).encodeToByteArray()

    fun deserializeAck(bytes: ByteArray): SnapshotAck =
        json.decodeFromString(SnapshotAck.serializer(), bytes.decodeToString())
}
