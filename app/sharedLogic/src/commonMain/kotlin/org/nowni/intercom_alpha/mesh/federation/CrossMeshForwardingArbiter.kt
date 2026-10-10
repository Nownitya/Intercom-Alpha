package org.nowni.intercom_alpha.mesh.federation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher

/**
 * Drop categorization for diagnostics and security telemetry.
 */
enum class DropCategory {
    MALFORMED,
    TARGET_MISMATCH,
    CIRCULAR_LOOP,
    HOP_LIMIT_EXCEEDED,
    DUPLICATE,
    SELF_ECHO,
    POLICY_VIOLATION,
    SUB_GROUP_ISOLATED,
    AUTH_FAILURE
}

/**
 * Outbound decision indicating whether locally generated squad audio
 * may be bridged to federated sibling convoys.
 */
sealed class OutboundForwardingDecision {
    data class AllowForward(
        val targetGroupIds: List<String>,
        val isEmergency: Boolean,
        val isCaptain: Boolean,
        val reason: String
    ) : OutboundForwardingDecision()

    data class BlockSubGroupIsolated(
        val channelId: Int?,
        val reason: String
    ) : OutboundForwardingDecision()

    data class BlockPolicyViolation(
        val policy: CrossGroupPolicy,
        val reason: String
    ) : OutboundForwardingDecision()

    data class BlockDisabled(val reason: String) : OutboundForwardingDecision()
    data class BlockStandbyGateway(val role: GatewayRole, val reason: String) : OutboundForwardingDecision()
}

/**
 * Inbound pre-decryption decision evaluating incoming cross-mesh frames
 * before executing cryptographic decryption.
 */
sealed class InboundForwardingDecision {
    data class AcceptForDecryption(
        val packet: CrossMeshAudioPacket,
        val updatedPathTrace: List<String>
    ) : InboundForwardingDecision()

    data class DropLoop(
        val loopResult: LoopCheckResult
    ) : InboundForwardingDecision()

    data class DropPolicyViolation(
        val policy: CrossGroupPolicy,
        val reason: String
    ) : InboundForwardingDecision()

    data class DropSubGroupIsolated(
        val reason: String
    ) : InboundForwardingDecision()

    data class DropTargetMismatch(
        val targetGroupId: String,
        val localGroupId: String,
        val reason: String
    ) : InboundForwardingDecision()

    data class DropMalformed(val reason: String) : InboundForwardingDecision()
}

/**
 * Final result of processing and decrypting an incoming cross-mesh audio frame.
 */
sealed class InboundRelayResult {
    data class Deliver(
        val rawAudio: ByteArray,
        val packet: CrossMeshAudioPacket,
        val updatedPathTrace: List<String>
    ) : InboundRelayResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Deliver) return false
            if (!rawAudio.contentEquals(other.rawAudio)) return false
            if (packet != other.packet) return false
            if (updatedPathTrace != other.updatedPathTrace) return false
            return true
        }

        override fun hashCode(): Int {
            var result = rawAudio.contentHashCode()
            result = 31 * result + packet.hashCode()
            result = 31 * result + updatedPathTrace.hashCode()
            return result
        }
    }

    data class Dropped(
        val category: DropCategory,
        val reason: String
    ) : InboundRelayResult()
}

/**
 * Telemetry snapshot of the forwarding arbiter.
 */
data class ForwardingAuditSnapshot(
    val totalInboundReceived: Long,
    val totalInboundDelivered: Long,
    val totalInboundDropped: Long,
    val totalOutboundAttempted: Long,
    val totalOutboundAllowed: Long,
    val totalOutboundBlocked: Long,
    val droppedByCategory: Map<DropCategory, Long>
)

/**
 * Arbiter for selective inter-group audio demuxing, sub-group isolation,
 * priority preemption, and pre-decryption security gating.
 */
class CrossMeshForwardingArbiter(
    val localGroupId: String,
    val localPeerId: String,
    val loopDetector: FederationLoopDetector = FederationLoopDetector(localGroupId, localPeerId),
    initialIsolatedChannels: Set<Int> = DEFAULT_ISOLATED_CHANNELS,
    val mainConvoyChannelId: Int = 1,
    val emergencyChannelId: Int = 16
) {
    companion object {
        /**
         * By default, channels 2 through 15 are isolated sub-groups (scouts, sweep,
         * support vehicle, rider-to-pillion private chat).
         * Only Channel 1 (Convoy Main) and Channel 16 (Emergency SOS) bridge across convoys.
         */
        val DEFAULT_ISOLATED_CHANNELS: Set<Int> = (2..15).toSet()
    }

    private val mutex = Mutex()
    private val isolatedChannels = initialIsolatedChannels.toMutableSet()

    private var totalInboundReceived = 0L
    private var totalInboundDelivered = 0L
    private var totalInboundDropped = 0L
    private var totalOutboundAttempted = 0L
    private var totalOutboundAllowed = 0L
    private var totalOutboundBlocked = 0L
    private val dropCounts = mutableMapOf<DropCategory, Long>()

    fun isChannelIsolated(channelId: Int): Boolean {
        return isolatedChannels.contains(channelId)
    }

    fun setIsolatedChannels(channelIds: Set<Int>) {
        isolatedChannels.clear()
        isolatedChannels.addAll(channelIds)
    }

    /**
     * Evaluates whether local audio is permitted to bridge outbound across meshes.
     *
     * Enforces:
     * 1. Standby gateway suppression: only PRIMARY gateways broadcast.
     * 2. Emergency preemption: SOS audio overrides all channel isolation.
     * 3. Sub-group isolation: private squad chatter stays internal.
     * 4. Cross-group policy (CAPTAIN_ONLY, ALL_MEMBERS_BRIDGED, EMERGENCY_SOS_ONLY, DISABLED).
     */
    fun evaluateOutbound(
        channelId: Int = mainConvoyChannelId,
        isPrivateSubGroup: Boolean = false,
        isCaptain: Boolean,
        isEmergency: Boolean,
        gatewayRole: GatewayRole,
        policy: CrossGroupPolicy,
        targetGroupIds: List<String>
    ): OutboundForwardingDecision {
        totalOutboundAttempted++

        // 1. Standby gateway suppression
        if (gatewayRole != GatewayRole.PRIMARY) {
            totalOutboundBlocked++
            return OutboundForwardingDecision.BlockStandbyGateway(
                role = gatewayRole,
                reason = "Only PRIMARY gateway forwards outbound audio across meshes"
            )
        }

        // 2. Disabled policy
        if (policy == CrossGroupPolicy.DISABLED) {
            totalOutboundBlocked++
            return OutboundForwardingDecision.BlockDisabled("Cross-group federation is DISABLED")
        }

        // 3. Emergency Preemption: overrides channel restrictions
        val effectiveEmergency = isEmergency || (channelId == emergencyChannelId)
        if (effectiveEmergency) {
            totalOutboundAllowed++
            return OutboundForwardingDecision.AllowForward(
                targetGroupIds = targetGroupIds,
                isEmergency = true,
                isCaptain = isCaptain,
                reason = "Emergency SOS broadcast permitted across convoys"
            )
        }

        // 4. Policy: EMERGENCY_SOS_ONLY
        if (policy == CrossGroupPolicy.EMERGENCY_SOS_ONLY) {
            totalOutboundBlocked++
            return OutboundForwardingDecision.BlockPolicyViolation(
                policy = policy,
                reason = "Cross-group policy is EMERGENCY_SOS_ONLY; normal audio blocked"
            )
        }

        // 5. Sub-Group Isolation: private channels must NEVER bridge to sibling convoys
        if (isPrivateSubGroup || isChannelIsolated(channelId)) {
            totalOutboundBlocked++
            return OutboundForwardingDecision.BlockSubGroupIsolated(
                channelId = channelId,
                reason = "Sub-group channel $channelId is isolated; private squad chatter remains local"
            )
        }

        // 6. Policy: CAPTAIN_ONLY
        if (policy == CrossGroupPolicy.CAPTAIN_ONLY) {
            if (!isCaptain) {
                totalOutboundBlocked++
                return OutboundForwardingDecision.BlockPolicyViolation(
                    policy = policy,
                    reason = "Non-captain member audio blocked under CAPTAIN_ONLY policy"
                )
            }
            totalOutboundAllowed++
            return OutboundForwardingDecision.AllowForward(
                targetGroupIds = targetGroupIds,
                isEmergency = false,
                isCaptain = true,
                reason = "Captain audio permitted to bridge across convoys on main channel"
            )
        }

        // 7. Policy: ALL_MEMBERS_BRIDGED
        if (policy == CrossGroupPolicy.ALL_MEMBERS_BRIDGED) {
            totalOutboundAllowed++
            return OutboundForwardingDecision.AllowForward(
                targetGroupIds = targetGroupIds,
                isEmergency = false,
                isCaptain = isCaptain,
                reason = "All-members audio permitted to bridge across convoys on main channel"
            )
        }

        totalOutboundBlocked++
        return OutboundForwardingDecision.BlockPolicyViolation(policy, "Unknown policy state")
    }

    /**
     * Evaluates an incoming cross-mesh frame prior to cryptographic decryption.
     * Drops unauthorized, looped, or duplicate packets without wasting CPU/power on decryption.
     */
    suspend fun evaluateInboundPreDecryption(
        packet: CrossMeshAudioPacket,
        activePolicy: CrossGroupPolicy,
        currentTimeMs: Long = currentTimeMillis()
    ): InboundForwardingDecision {
        totalInboundReceived++

        // 1. Target Group Match (must match localGroupId or wildcard)
        if (packet.targetGroupId != localGroupId && packet.targetGroupId != "*" && packet.targetGroupId != "BROADCAST") {
            recordDrop(DropCategory.TARGET_MISMATCH)
            return InboundForwardingDecision.DropTargetMismatch(
                targetGroupId = packet.targetGroupId,
                localGroupId = localGroupId,
                reason = "Target group ${packet.targetGroupId} does not match local group $localGroupId"
            )
        }

        // 2. Anti-Feedback Loop & Deduplication Check
        val loopResult = loopDetector.evaluateInbound(packet, currentTimeMs)
        when (loopResult) {
            is LoopCheckResult.DropCircularLoop -> {
                recordDrop(DropCategory.CIRCULAR_LOOP)
                return InboundForwardingDecision.DropLoop(loopResult)
            }
            is LoopCheckResult.DropHopLimitExceeded -> {
                recordDrop(DropCategory.HOP_LIMIT_EXCEEDED)
                return InboundForwardingDecision.DropLoop(loopResult)
            }
            is LoopCheckResult.DropDuplicate -> {
                recordDrop(DropCategory.DUPLICATE)
                return InboundForwardingDecision.DropLoop(loopResult)
            }
            is LoopCheckResult.DropSelfEcho -> {
                recordDrop(DropCategory.SELF_ECHO)
                return InboundForwardingDecision.DropLoop(loopResult)
            }
            is LoopCheckResult.Accept -> {
                // Passed loop & dedup checks
            }
        }

        // 3. Pre-Decryption Policy Enforcement
        if (activePolicy == CrossGroupPolicy.DISABLED) {
            recordDrop(DropCategory.POLICY_VIOLATION)
            return InboundForwardingDecision.DropPolicyViolation(activePolicy, "Cross-group federation is DISABLED")
        }

        if (activePolicy == CrossGroupPolicy.EMERGENCY_SOS_ONLY && !packet.isEmergency) {
            recordDrop(DropCategory.POLICY_VIOLATION)
            return InboundForwardingDecision.DropPolicyViolation(
                activePolicy,
                "Incoming non-emergency packet rejected under EMERGENCY_SOS_ONLY policy"
            )
        }

        if (activePolicy == CrossGroupPolicy.CAPTAIN_ONLY && !packet.isCaptain && !packet.isEmergency) {
            recordDrop(DropCategory.POLICY_VIOLATION)
            return InboundForwardingDecision.DropPolicyViolation(
                activePolicy,
                "Incoming non-captain packet rejected under CAPTAIN_ONLY policy"
            )
        }

        val updatedTrace = loopResult.updatedPathTrace
        return InboundForwardingDecision.AcceptForDecryption(packet, updatedTrace)
    }

    /**
     * Executes the complete ingestion and decryption pipeline for an incoming Control frame.
     */
    suspend fun processInboundPacket(
        controlPacket: MeshPacket.Control,
        activePolicy: CrossGroupPolicy,
        cipher: PacketCipher?,
        currentTimeMs: Long = currentTimeMillis()
    ): InboundRelayResult {
        if (controlPacket.type != ControlType.FEDERATION_AUDIO) {
            recordDrop(DropCategory.MALFORMED)
            return InboundRelayResult.Dropped(DropCategory.MALFORMED, "Packet type is not FEDERATION_AUDIO")
        }

        val packet = FederationSerializer.decodeCrossMeshPacket(controlPacket.payload)
        if (packet == null) {
            recordDrop(DropCategory.MALFORMED)
            return InboundRelayResult.Dropped(DropCategory.MALFORMED, "Failed to deserialize CrossMeshAudioPacket")
        }

        // Pre-decryption evaluation
        val preDecryptionDecision = evaluateInboundPreDecryption(packet, activePolicy, currentTimeMs)
        when (preDecryptionDecision) {
            is InboundForwardingDecision.DropLoop -> {
                val category = when (preDecryptionDecision.loopResult) {
                    is LoopCheckResult.DropCircularLoop -> DropCategory.CIRCULAR_LOOP
                    is LoopCheckResult.DropHopLimitExceeded -> DropCategory.HOP_LIMIT_EXCEEDED
                    is LoopCheckResult.DropDuplicate -> DropCategory.DUPLICATE
                    is LoopCheckResult.DropSelfEcho -> DropCategory.SELF_ECHO
                    else -> DropCategory.CIRCULAR_LOOP
                }
                return InboundRelayResult.Dropped(category, "Dropped by loop detector")
            }
            is InboundForwardingDecision.DropPolicyViolation -> {
                return InboundRelayResult.Dropped(DropCategory.POLICY_VIOLATION, preDecryptionDecision.reason)
            }
            is InboundForwardingDecision.DropSubGroupIsolated -> {
                return InboundRelayResult.Dropped(DropCategory.SUB_GROUP_ISOLATED, preDecryptionDecision.reason)
            }
            is InboundForwardingDecision.DropTargetMismatch -> {
                return InboundRelayResult.Dropped(DropCategory.TARGET_MISMATCH, preDecryptionDecision.reason)
            }
            is InboundForwardingDecision.DropMalformed -> {
                return InboundRelayResult.Dropped(DropCategory.MALFORMED, preDecryptionDecision.reason)
            }
            is InboundForwardingDecision.AcceptForDecryption -> {
                // Proceed with authenticated decryption
            }
        }

        if (cipher == null) {
            recordDrop(DropCategory.AUTH_FAILURE)
            return InboundRelayResult.Dropped(DropCategory.AUTH_FAILURE, "No cipher registered for federationId ${packet.federationId}")
        }

        val decrypted = cipher.decrypt(packet.payload)
        if (decrypted == null) {
            recordDrop(DropCategory.AUTH_FAILURE)
            return InboundRelayResult.Dropped(DropCategory.AUTH_FAILURE, "Decryption authentication failed")
        }

        totalInboundDelivered++
        val updatedTrace = preDecryptionDecision.updatedPathTrace
        return InboundRelayResult.Deliver(
            rawAudio = decrypted,
            packet = packet,
            updatedPathTrace = updatedTrace
        )
    }

    private fun recordDrop(category: DropCategory) {
        totalInboundDropped++
        dropCounts[category] = (dropCounts[category] ?: 0L) + 1
    }

    fun getAuditSnapshot(): ForwardingAuditSnapshot {
        return ForwardingAuditSnapshot(
            totalInboundReceived = totalInboundReceived,
            totalInboundDelivered = totalInboundDelivered,
            totalInboundDropped = totalInboundDropped,
            totalOutboundAttempted = totalOutboundAttempted,
            totalOutboundAllowed = totalOutboundAllowed,
            totalOutboundBlocked = totalOutboundBlocked,
            droppedByCategory = dropCounts.toMap()
        )
    }
}
