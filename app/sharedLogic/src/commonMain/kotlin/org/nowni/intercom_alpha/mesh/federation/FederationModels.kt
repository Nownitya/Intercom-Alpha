package org.nowni.intercom_alpha.mesh.federation

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cross-group forwarding policy governing which audio and alerts
 * are permitted to bridge between disparate cryptographic mesh clusters.
 */
@Serializable
enum class CrossGroupPolicy {
    /**
     * Only road captains or designated squad leads transmit across meshes.
     */
    CAPTAIN_ONLY,

    /**
     * Only emergency priority alerts (crash detection, eCall SOS) bridge across meshes.
     */
    EMERGENCY_SOS_ONLY,

    /**
     * Full two-way bridge where all squad members hear and transmit across meshes.
     */
    ALL_MEMBERS_BRIDGED,

    /**
     * Inter-group gateway is disabled; zero cross-mesh packet leakage.
     */
    DISABLED
}

/**
 * Operational state of an inter-group federation bridge.
 */
@Serializable
enum class FederationBridgeState {
    DISCONNECTED,
    DISCOVERING,
    INVITE_PENDING,
    BRIDGED,
    SUSPENDED,
    ERROR
}

/**
 * Role of the local node in the federation bridge.
 * To avoid duplicate cross-group broadcasts, only one node in the group
 * acts as the active PRIMARY gateway; other nodes remain in STANDBY.
 */
@Serializable
enum class GatewayRole {
    PRIMARY,
    STANDBY,
    NON_GATEWAY
}

/**
 * Represents an active or pending federated sibling group.
 */
@Serializable
data class FederatedGroupInfo(
    val groupId: String,
    val groupName: String,
    val gatewayPeerId: String,
    val bridgeKeySha256: String,
    val policy: CrossGroupPolicy,
    val state: FederationBridgeState,
    val lastHeartbeatMs: Long,
    val linkRssi: Int = -70,
    val relayedPacketsCount: Long = 0L
)

/**
 * On-demand federation invitation payload exchanged via QR code or BLE broadcast.
 * Formatted as: FEDERATION:v1:<base64>
 */
@Serializable
data class GroupFederationInvite(
    val federationId: String,
    val initiatorGroupId: String,
    val initiatorGroupName: String,
    val initiatorLeaderId: String,
    val sharedSecret: String,
    val validUntilMs: Long,
    val defaultPolicy: CrossGroupPolicy = CrossGroupPolicy.CAPTAIN_ONLY
) {
    companion object {
        const val PREFIX = "FEDERATION:v1:"
        const val DEFAULT_EXPIRY_MS = 12 * 60 * 60 * 1000L // 12 hours
    }
}

/**
 * Cross-mesh encapsulated audio and alert packet.
 * Transported across groups over Control or DirectedData frames.
 */
@Serializable
data class CrossMeshAudioPacket(
    val federationId: String,
    val sourceGroupId: String,
    val targetGroupId: String,
    val originalSenderId: String,
    val isCaptain: Boolean,
    val isEmergency: Boolean,
    val sequence: Long,
    val timestampMs: Long,
    val pathTrace: List<String> = emptyList(),
    val payload: ByteArray // Encrypted voice or alert frame
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrossMeshAudioPacket) return false

        if (federationId != other.federationId) return false
        if (sourceGroupId != other.sourceGroupId) return false
        if (targetGroupId != other.targetGroupId) return false
        if (originalSenderId != other.originalSenderId) return false
        if (isCaptain != other.isCaptain) return false
        if (isEmergency != other.isEmergency) return false
        if (sequence != other.sequence) return false
        if (timestampMs != other.timestampMs) return false
        if (pathTrace != other.pathTrace) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = federationId.hashCode()
        result = 31 * result + sourceGroupId.hashCode()
        result = 31 * result + targetGroupId.hashCode()
        result = 31 * result + originalSenderId.hashCode()
        result = 31 * result + isCaptain.hashCode()
        result = 31 * result + isEmergency.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + pathTrace.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Diagnostic and reactive snapshot of the multi-group federation subsystem.
 */
@Serializable
data class FederationSnapshot(
    val localGroupId: String,
    val localRole: GatewayRole,
    val bridgeState: FederationBridgeState,
    val federatedGroups: List<FederatedGroupInfo>,
    val activePolicy: CrossGroupPolicy,
    val totalRelayedPacketsIn: Long,
    val totalRelayedPacketsOut: Long,
    val totalDroppedPolicyViolations: Long,
    val lastRelayedTimestampMs: Long
)

/**
 * Pure KMP JSON and Base64 serializer for federation protocol frames.
 */
@OptIn(ExperimentalEncodingApi::class)
object FederationSerializer {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        classDiscriminator = "#type"
    }

    fun encodeInvite(invite: GroupFederationInvite): String {
        val jsonStr = json.encodeToString(invite)
        val b64 = Base64.encode(jsonStr.encodeToByteArray())
        return "${GroupFederationInvite.PREFIX}$b64"
    }

    fun decodeInvite(code: String): GroupFederationInvite? {
        if (!code.startsWith(GroupFederationInvite.PREFIX)) return null
        val b64 = code.removePrefix(GroupFederationInvite.PREFIX)
        return runCatching {
            val bytes = Base64.decode(b64)
            json.decodeFromString<GroupFederationInvite>(bytes.decodeToString())
        }.getOrNull()
    }

    fun encodeCrossMeshPacket(packet: CrossMeshAudioPacket): ByteArray {
        return json.encodeToString(packet).encodeToByteArray()
    }

    fun decodeCrossMeshPacket(bytes: ByteArray): CrossMeshAudioPacket? {
        return runCatching {
            json.decodeFromString<CrossMeshAudioPacket>(bytes.decodeToString())
        }.getOrNull()
    }
}
