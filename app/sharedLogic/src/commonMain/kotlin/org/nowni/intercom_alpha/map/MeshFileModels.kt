package org.nowni.intercom_alpha.map

import kotlinx.serialization.Serializable

/**
 * Supported over-the-air off-grid trail and route exchange formats.
 */
@Serializable
enum class FileTransferType {
    GPX,
    GEOJSON,
    WAYPOINT_PACK
}

/**
 * Direction of file transfer relative to the local node.
 */
@Serializable
enum class FileTransferDirection {
    OUTGOING,
    INCOMING
}

/**
 * Lifecycle states of an over-the-air file transfer.
 */
@Serializable
enum class FileTransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * MTU-bounded single chunk of a route or trail file transferred over the BLE mesh.
 * Engineered for sub-128 byte GATT payloads to guarantee zero packet fragmentation.
 */
@Serializable
data class FileTransferChunk(
    val transferId: String,
    val fileName: String,
    val fileType: FileTransferType,
    val chunkIndex: Int,
    val totalChunks: Int,
    val totalBytes: Long,
    val data: ByteArray,
    val adler32Checksum: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FileTransferChunk
        if (transferId != other.transferId) return false
        if (fileName != other.fileName) return false
        if (fileType != other.fileType) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (totalBytes != other.totalBytes) return false
        if (!data.contentEquals(other.data)) return false
        if (adler32Checksum != other.adler32Checksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transferId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + totalBytes.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + adler32Checksum.hashCode()
        return result
    }
}

/**
 * Reception acknowledgment and selective NACK report for lost chunk recovery.
 */
@Serializable
data class FileTransferAck(
    val transferId: String,
    val chunkIndex: Int,
    val missingChunks: List<Int> = emptyList(),
    val isComplete: Boolean = false
)

/**
 * Real-time observable progress model for cockpit presentation layers.
 */
@Serializable
data class FileTransferProgress(
    val transferId: String,
    val fileName: String,
    val fileType: FileTransferType,
    val direction: FileTransferDirection,
    val peerId: String,
    val chunksTransferred: Int,
    val totalChunks: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: FileTransferStatus,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalChunks > 0) (chunksTransferred.toFloat() / totalChunks).coerceIn(0f, 1f) else 0f
}

/**
 * Pure Kotlin Multiplatform Adler-32 integrity checksum validator.
 * Conforms to RFC 1950 with zero Java (java.util.zip) or native C/JNI dependencies.
 */
object Adler32 {
    private const val MOD_ADLER = 65521L

    fun checksum(data: ByteArray): Long {
        var a = 1L
        var b = 0L
        for (byte in data) {
            val ubyte = byte.toLong() and 0xFFL
            a = (a + ubyte) % MOD_ADLER
            b = (b + a) % MOD_ADLER
        }
        return (b shl 16) or a
    }
}
