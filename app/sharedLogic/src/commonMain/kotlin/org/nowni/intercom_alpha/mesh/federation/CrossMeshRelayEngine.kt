package org.nowni.intercom_alpha.mesh.federation

import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher

/**
 * Result of evaluating and processing an incoming cross-mesh federation frame.
 */
sealed class InboundFederationDecision {
    data class Deliver(
        val rawAudio: ByteArray,
        val federationId: String,
        val sourceGroupId: String,
        val originalSenderId: String,
        val isCaptain: Boolean,
        val isEmergency: Boolean,
        val sequence: Long
    ) : InboundFederationDecision() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Deliver) return false
            if (!rawAudio.contentEquals(other.rawAudio)) return false
            if (federationId != other.federationId) return false
            if (sourceGroupId != other.sourceGroupId) return false
            if (originalSenderId != other.originalSenderId) return false
            if (isCaptain != other.isCaptain) return false
            if (isEmergency != other.isEmergency) return false
            if (sequence != other.sequence) return false
            return true
        }

        override fun hashCode(): Int {
            var result = rawAudio.contentHashCode()
            result = 31 * result + federationId.hashCode()
            result = 31 * result + sourceGroupId.hashCode()
            result = 31 * result + originalSenderId.hashCode()
            result = 31 * result + isCaptain.hashCode()
            result = 31 * result + isEmergency.hashCode()
            result = 31 * result + sequence.hashCode()
            return result
        }
    }

    data class DropFeedbackLoop(val pathTrace: List<String>, val reason: String) : InboundFederationDecision()
    data class DropPolicyViolation(val policy: CrossGroupPolicy, val reason: String) : InboundFederationDecision()
    data class DropAuthFailure(val reason: String) : InboundFederationDecision()
    data class DropMalformed(val reason: String) : InboundFederationDecision()
}

/**
 * Cryptographic translation and encapsulation engine for bridging audio frames
 * across distinct group mesh clusters.
 */
class CrossMeshRelayEngine(
    val localGroupId: String,
    val localPeerId: String
) {
    /**
     * Map of federationId -> PacketCipher (derived from shared federation secret).
     */
    private val federationCiphers = mutableMapOf<String, PacketCipher>()

    fun registerFederationCipher(federationId: String, sharedSecret: String) {
        val key = PacketCipher.deriveKey(sharedSecret)
        federationCiphers[federationId] = PacketCipher(key)
    }

    fun unregisterFederationCipher(federationId: String) {
        federationCiphers.remove(federationId)
    }

    fun getCipher(federationId: String): PacketCipher? = federationCiphers[federationId]

    /**
     * Evaluates whether audio transmission is permitted under the given policy.
     */
    fun isTransmissionAllowed(
        isCaptain: Boolean,
        isEmergency: Boolean,
        policy: CrossGroupPolicy
    ): Boolean {
        return when (policy) {
            CrossGroupPolicy.ALL_MEMBERS_BRIDGED -> true
            CrossGroupPolicy.CAPTAIN_ONLY -> isCaptain || isEmergency
            CrossGroupPolicy.EMERGENCY_SOS_ONLY -> isEmergency
            CrossGroupPolicy.DISABLED -> false
        }
    }

    /**
     * Encapsulates raw audio or alert into a cross-mesh control frame.
     */
    fun createOutboundCrossMeshPacket(
        federationId: String,
        targetGroupId: String,
        rawAudioData: ByteArray,
        isCaptain: Boolean,
        isEmergency: Boolean,
        sequence: Long,
        policy: CrossGroupPolicy,
        currentTimeMs: Long
    ): MeshPacket.Control? {
        if (!isTransmissionAllowed(isCaptain, isEmergency, policy)) {
            return null
        }

        val cipher = federationCiphers[federationId] ?: return null
        val encryptedPayload = cipher.encrypt(rawAudioData)

        val crossMeshPacket = CrossMeshAudioPacket(
            federationId = federationId,
            sourceGroupId = localGroupId,
            targetGroupId = targetGroupId,
            originalSenderId = localPeerId,
            isCaptain = isCaptain,
            isEmergency = isEmergency,
            sequence = sequence,
            timestampMs = currentTimeMs,
            pathTrace = listOf(localGroupId),
            payload = encryptedPayload
        )

        val encodedBytes = FederationSerializer.encodeCrossMeshPacket(crossMeshPacket)
        return MeshPacket.Control(
            senderId = localPeerId,
            type = ControlType.FEDERATION_AUDIO,
            payload = encodedBytes
        )
    }

    /**
     * Processes and decrypts an incoming cross-mesh frame.
     */
    fun processInboundCrossMeshPacket(
        controlPacket: MeshPacket.Control,
        activePolicy: CrossGroupPolicy
    ): InboundFederationDecision {
        if (controlPacket.type != ControlType.FEDERATION_AUDIO) {
            return InboundFederationDecision.DropMalformed("Packet is not FEDERATION_AUDIO")
        }

        val crossMeshPacket = FederationSerializer.decodeCrossMeshPacket(controlPacket.payload)
            ?: return InboundFederationDecision.DropMalformed("Failed to decode CrossMeshAudioPacket")

        // 1. Anti-Feedback Loop Check: Drop if packet previously visited localGroupId
        if (crossMeshPacket.pathTrace.contains(localGroupId)) {
            return InboundFederationDecision.DropFeedbackLoop(
                pathTrace = crossMeshPacket.pathTrace,
                reason = "Circular relay detected: packet already traversed $localGroupId"
            )
        }

        // 2. Policy Enforcement
        if (!isTransmissionAllowed(crossMeshPacket.isCaptain, crossMeshPacket.isEmergency, activePolicy)) {
            return InboundFederationDecision.DropPolicyViolation(
                policy = activePolicy,
                reason = "Cross-group policy ($activePolicy) rejected incoming packet (captain=${crossMeshPacket.isCaptain}, sos=${crossMeshPacket.isEmergency})"
            )
        }

        // 3. Cryptographic Verification & Decryption
        val cipher = federationCiphers[crossMeshPacket.federationId]
            ?: return InboundFederationDecision.DropAuthFailure("Unknown federationId: ${crossMeshPacket.federationId}")

        val decryptedAudio = cipher.decrypt(crossMeshPacket.payload)
            ?: return InboundFederationDecision.DropAuthFailure("Authentication tag verification failed for federation frame")

        return InboundFederationDecision.Deliver(
            rawAudio = decryptedAudio,
            federationId = crossMeshPacket.federationId,
            sourceGroupId = crossMeshPacket.sourceGroupId,
            originalSenderId = crossMeshPacket.originalSenderId,
            isCaptain = crossMeshPacket.isCaptain,
            isEmergency = crossMeshPacket.isEmergency,
            sequence = crossMeshPacket.sequence
        )
    }
}
