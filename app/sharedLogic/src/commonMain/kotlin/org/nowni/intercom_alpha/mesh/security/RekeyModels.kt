package org.nowni.intercom_alpha.mesh.security

import kotlinx.serialization.Serializable

/**
 * Reason triggering a fleet-wide cryptographic epoch rekey.
 */
@Serializable
enum class RekeyTriggerReason {
    SCHEDULED_ROTATION,
    MEMBER_LEFT,
    COMPROMISE_SUSPECTED,
    MANUAL_REKEY
}

/**
 * Lifecycle event emitted during epoch rotation.
 */
@Serializable
enum class RekeyEvent {
    EPOCH_TRANSITION_STARTED,
    EPOCH_TRANSITION_COMPLETED,
    OLD_EPOCH_EXPIRED,
    REKEY_PACKET_REJECTED
}

/**
 * Cryptographic metadata and parameters for an active or previous epoch.
 */
@Serializable
data class EpochKeyDescriptor(
    val epoch: Long,
    val key: ByteArray,
    val createdAtTimestamp: Long,
    val expiresAtTimestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpochKeyDescriptor) return false
        if (epoch != other.epoch) return false
        if (!key.contentEquals(other.key)) return false
        if (createdAtTimestamp != other.createdAtTimestamp) return false
        if (expiresAtTimestamp != other.expiresAtTimestamp) return false
        return true
    }

    override fun hashCode(): Int {
        var result = epoch.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + createdAtTimestamp.hashCode()
        result = 31 * result + expiresAtTimestamp.hashCode()
        return result
    }
}

/**
 * Over-the-air control packet flooded across the mesh to announce a forward-secret epoch transition.
 */
@Serializable
data class RekeyControlPacket(
    val groupId: String,
    val initiatorId: String,
    val targetEpoch: Long,
    val epochKeyDerivationSalt: ByteArray,
    val timestamp: Long,
    val antiReplayCounter: Long,
    val authSignature: ByteArray,
    val reason: RekeyTriggerReason = RekeyTriggerReason.SCHEDULED_ROTATION
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RekeyControlPacket) return false
        if (groupId != other.groupId) return false
        if (initiatorId != other.initiatorId) return false
        if (targetEpoch != other.targetEpoch) return false
        if (!epochKeyDerivationSalt.contentEquals(other.epochKeyDerivationSalt)) return false
        if (timestamp != other.timestamp) return false
        if (antiReplayCounter != other.antiReplayCounter) return false
        if (!authSignature.contentEquals(other.authSignature)) return false
        if (reason != other.reason) return false
        return true
    }

    override fun hashCode(): Int {
        var result = groupId.hashCode()
        result = 31 * result + initiatorId.hashCode()
        result = 31 * result + targetEpoch.hashCode()
        result = 31 * result + epochKeyDerivationSalt.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + antiReplayCounter.hashCode()
        result = 31 * result + authSignature.contentHashCode()
        result = 31 * result + reason.hashCode()
        return result
    }
}

/**
 * Dual-epoch key buffer state providing zero-drop transitions during rotation.
 */
@Serializable
data class DualEpochKeyBuffer(
    val activeEpoch: Long,
    val activeKey: ByteArray,
    val previousEpoch: Long? = null,
    val previousKey: ByteArray? = null,
    val gracePeriodExpiresAt: Long = 0L
) {
    val isGracePeriodActive: Boolean
        get() = previousEpoch != null && previousKey != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DualEpochKeyBuffer) return false
        if (activeEpoch != other.activeEpoch) return false
        if (!activeKey.contentEquals(other.activeKey)) return false
        if (previousEpoch != other.previousEpoch) return false
        if (previousKey != null) {
            if (other.previousKey == null || !previousKey.contentEquals(other.previousKey)) return false
        } else if (other.previousKey != null) return false
        if (gracePeriodExpiresAt != other.gracePeriodExpiresAt) return false
        return true
    }

    override fun hashCode(): Int {
        var result = activeEpoch.hashCode()
        result = 31 * result + activeKey.contentHashCode()
        result = 31 * result + (previousEpoch?.hashCode() ?: 0)
        result = 31 * result + (previousKey?.contentHashCode() ?: 0)
        result = 31 * result + gracePeriodExpiresAt.hashCode()
        return result
    }
}

/**
 * Result of attempting to process an incoming OTA rekey packet.
 */
sealed interface RekeyResult {
    data class Accepted(val epoch: Long, val key: ByteArray) : RekeyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Accepted) return false
            return epoch == other.epoch && key.contentEquals(other.key)
        }
        override fun hashCode(): Int = 31 * epoch.hashCode() + key.contentHashCode()
    }

    data class Rejected(val reason: String) : RekeyResult
}
