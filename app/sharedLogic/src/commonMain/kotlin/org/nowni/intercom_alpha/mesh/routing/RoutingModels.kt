package org.nowni.intercom_alpha.mesh.routing

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure KMP AODV-Lite Directed Routing Data Models.
 * Follows RFC 3561 concepts adapted for energy-constrained BLE mesh.
 * Rule 2 & 3: 100% pure Multiplatform Kotlin with zero Java or platform dependencies.
 */

private val routingJson = Json {
    classDiscriminator = "#type"
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
enum class RouteStatus {
    ACTIVE,
    EXPIRED,
    DISCOVERING,
    INVALID
}

@Serializable
data class RouteEntry(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val destSeqNum: Long,
    val metricCost: Double,
    val lastActiveTimeMs: Long,
    val lifetimeMs: Long = 30_000L,
    val precursorNodes: Set<String> = emptySet(),
    val status: RouteStatus = RouteStatus.ACTIVE
) {
    fun isFresh(currentTimeMs: Long): Boolean {
        return status == RouteStatus.ACTIVE && (currentTimeMs - lastActiveTimeMs) <= lifetimeMs
    }

    fun isExpired(currentTimeMs: Long): Boolean {
        return (currentTimeMs - lastActiveTimeMs) > lifetimeMs
    }
}

@Serializable
data class RouteRequestPayload(
    val rreqId: Long,
    val sourceId: String,
    val sourceSeqNum: Long,
    val destinationId: String,
    val destSeqNum: Long,
    val hopCount: Int,
    val metricCost: Double,
    val ttl: Int = 4
)

@Serializable
data class RouteReplyPayload(
    val sourceId: String,
    val destinationId: String,
    val destSeqNum: Long,
    val hopCount: Int,
    val metricCost: Double,
    val lifetimeMs: Long = 30_000L,
    val targetNextHop: String
)

@Serializable
data class UnreachableNode(
    val destinationId: String,
    val destSeqNum: Long
)

@Serializable
data class RouteErrorPayload(
    val unreachableDestinations: List<UnreachableNode>,
    val targetNextHop: String? = null
)

@Serializable
data class DirectedDataPayload(
    val sourceId: String,
    val destinationId: String,
    val nextHopId: String,
    val payloadType: String,
    val data: ByteArray,
    val sequence: Long,
    val ttl: Int = 4
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DirectedDataPayload) return false
        if (sourceId != other.sourceId) return false
        if (destinationId != other.destinationId) return false
        if (nextHopId != other.nextHopId) return false
        if (payloadType != other.payloadType) return false
        if (!data.contentEquals(other.data)) return false
        if (sequence != other.sequence) return false
        if (ttl != other.ttl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + destinationId.hashCode()
        result = 31 * result + nextHopId.hashCode()
        result = 31 * result + payloadType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + ttl
        return result
    }
}

@Serializable
data class RouteSummary(
    val destinationId: String,
    val nextHopId: String,
    val hopCount: Int,
    val metricCost: Double,
    val isValid: Boolean,
    val ageMs: Long
)

@Serializable
data class RoutingSnapshot(
    val localNodeId: String,
    val activeRoutes: List<RouteSummary>,
    val totalDirectedPackets: Long,
    val totalFloodedPackets: Long,
    val airtimeSavingsPercentage: Double,
    val timestampMs: Long
)

object RoutingSerializer {
    fun encodeRreq(payload: RouteRequestPayload): ByteArray {
        return routingJson.encodeToString(payload).encodeToByteArray()
    }

    fun decodeRreq(bytes: ByteArray): RouteRequestPayload? {
        return try {
            routingJson.decodeFromString<RouteRequestPayload>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun encodeRrep(payload: RouteReplyPayload): ByteArray {
        return routingJson.encodeToString(payload).encodeToByteArray()
    }

    fun decodeRrep(bytes: ByteArray): RouteReplyPayload? {
        return try {
            routingJson.decodeFromString<RouteReplyPayload>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun encodeRerr(payload: RouteErrorPayload): ByteArray {
        return routingJson.encodeToString(payload).encodeToByteArray()
    }

    fun decodeRerr(bytes: ByteArray): RouteErrorPayload? {
        return try {
            routingJson.decodeFromString<RouteErrorPayload>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }

    fun encodeDirectedData(payload: DirectedDataPayload): ByteArray {
        return routingJson.encodeToString(payload).encodeToByteArray()
    }

    fun decodeDirectedData(bytes: ByteArray): DirectedDataPayload? {
        return try {
            routingJson.decodeFromString<DirectedDataPayload>(bytes.decodeToString())
        } catch (_: Exception) {
            null
        }
    }
}
