package org.nowni.intercom_alpha.mesh.visual

import kotlinx.serialization.Serializable

/**
 * Format specification for low-bandwidth tactical snapshot images.
 */
@Serializable
enum class SnapshotMimeType {
    JPEG,
    WEBP,
    PNG,
    RAW_GRAYSCALE
}

/**
 * Direction of snapshot transport relative to local node.
 */
@Serializable
enum class SnapshotTransferDirection {
    OUTGOING,
    INCOMING
}

/**
 * Lifecycle status of an in-flight or completed snapshot transfer.
 */
@Serializable
enum class SnapshotTransferStatus {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Metadata header for an ultra-low-framerate tactical snapshot frame.
 * Emitted prior to or alongside chunk streams over BLE mesh.
 */
@Serializable
data class SnapshotHeader(
    val snapshotId: String,
    val senderId: String,
    val timestamp: Long,
    val totalBytes: Long,
    val totalChunks: Int,
    val mimeType: SnapshotMimeType = SnapshotMimeType.JPEG,
    val width: Int = 160,
    val height: Int = 120,
    val framerateLimitFps: Float = 0.5f,
    val adler32Checksum: Long
)

/**
 * MTU-bounded single chunk of a compressed tactical snapshot.
 * Bounded to <= 128 bytes to guarantee zero GATT transport fragmentation.
 */
@Serializable
data class SnapshotChunk(
    val snapshotId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val totalBytes: Long,
    val data: ByteArray,
    val chunkChecksum: Long,
    val payloadChecksum: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SnapshotChunk
        if (snapshotId != other.snapshotId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (totalBytes != other.totalBytes) return false
        if (!data.contentEquals(other.data)) return false
        if (chunkChecksum != other.chunkChecksum) return false
        if (payloadChecksum != other.payloadChecksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = snapshotId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + chunkChecksum.hashCode()
        result = 31 * result + payloadChecksum.hashCode()
        return result
    }
}

/**
 * Selective acknowledgment and loss recovery packet for snapshot transfer.
 */
@Serializable
data class SnapshotAck(
    val snapshotId: String,
    val chunkIndex: Int,
    val receivedChunks: List<Int> = emptyList(),
    val missingChunkIndices: List<Int> = emptyList(),
    val isComplete: Boolean = false
)

/**
 * Observable telemetry progress for UI cockpit and fleet visual monitoring.
 */
@Serializable
data class SnapshotTransferProgress(
    val snapshotId: String,
    val senderId: String,
    val recipientId: String? = null,
    val direction: SnapshotTransferDirection,
    val chunksTransferred: Int,
    val totalChunks: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: SnapshotTransferStatus,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalChunks > 0) (chunksTransferred.toFloat() / totalChunks).coerceIn(0f, 1f) else 0f
}
