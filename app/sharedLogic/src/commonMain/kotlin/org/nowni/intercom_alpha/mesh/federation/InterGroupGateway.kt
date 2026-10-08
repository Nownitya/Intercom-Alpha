package org.nowni.intercom_alpha.mesh.federation

import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.randomUUID
import kotlin.random.Random

/**
 * Primary coordinator and state machine for inter-group mesh federation.
 * Manages active federated groups, gateway role arbitration, policy enforcement,
 * and cross-mesh packet flow.
 */
class InterGroupGateway(
    val localGroupId: String,
    val localGroupName: String,
    val localPeerId: String,
    var isLocalLeader: Boolean = false,
    var defaultPolicy: CrossGroupPolicy = CrossGroupPolicy.CAPTAIN_ONLY
) {
    var localRole: GatewayRole = if (isLocalLeader) GatewayRole.PRIMARY else GatewayRole.STANDBY
    val relayEngine: CrossMeshRelayEngine = CrossMeshRelayEngine(localGroupId, localPeerId)
    val loopDetector: FederationLoopDetector = FederationLoopDetector(localGroupId, localPeerId)
    val forwardingArbiter: CrossMeshForwardingArbiter = CrossMeshForwardingArbiter(localGroupId, localPeerId, loopDetector)

    private val federatedGroups = mutableMapOf<String, FederatedGroupInfo>()
    private var sequenceNumber: Long = 0L

    private var totalRelayedIn: Long = 0L
    private var totalRelayedOut: Long = 0L
    private var totalDroppedPolicyViolations: Long = 0L
    private var lastRelayedTimestampMs: Long = 0L

    /**
     * Generates a new cryptographically bound GroupFederationInvite.
     */
    fun createFederationInvite(
        sharedSecret: String = generateRandomSecret(),
        policy: CrossGroupPolicy = defaultPolicy,
        validDurationMs: Long = GroupFederationInvite.DEFAULT_EXPIRY_MS,
        currentTimeMs: Long = 0L
    ): GroupFederationInvite {
        val federationId = "fed-${randomUUID().take(8)}"
        val invite = GroupFederationInvite(
            federationId = federationId,
            initiatorGroupId = localGroupId,
            initiatorGroupName = localGroupName,
            initiatorLeaderId = localPeerId,
            sharedSecret = sharedSecret,
            validUntilMs = currentTimeMs + validDurationMs,
            defaultPolicy = policy
        )

        // Pre-register federation cipher
        relayEngine.registerFederationCipher(federationId, sharedSecret)
        return invite
    }

    /**
     * Accepts and mounts an incoming federation invite from a sibling group.
     */
    fun acceptFederationInvite(
        invite: GroupFederationInvite,
        currentTimeMs: Long
    ): Boolean {
        if (currentTimeMs > invite.validUntilMs) {
            return false // Expired
        }
        if (invite.initiatorGroupId == localGroupId) {
            return false // Cannot federate with self
        }

        relayEngine.registerFederationCipher(invite.federationId, invite.sharedSecret)

        val info = FederatedGroupInfo(
            groupId = invite.initiatorGroupId,
            groupName = invite.initiatorGroupName,
            gatewayPeerId = invite.initiatorLeaderId,
            bridgeKeySha256 = PacketCipher.deriveKey(invite.sharedSecret).toHexPrefix(),
            policy = invite.defaultPolicy,
            state = FederationBridgeState.BRIDGED,
            lastHeartbeatMs = currentTimeMs,
            linkRssi = -70,
            relayedPacketsCount = 0L
        )

        federatedGroups[invite.initiatorGroupId] = info
        return true
    }

    /**
     * Updates cross-group forwarding policy for a specific federated group.
     */
    fun setCrossGroupPolicy(groupId: String, policy: CrossGroupPolicy) {
        val existing = federatedGroups[groupId]
        if (existing != null) {
            federatedGroups[groupId] = existing.copy(policy = policy)
        }
    }

    /**
     * Updates gateway role (e.g. following gateway leader election).
     */
    fun updateGatewayRole(role: GatewayRole) {
        this.localRole = role
    }

    /**
     * Formats outbound local audio into cross-mesh packets for all active federated groups.
     * Only PRIMARY gateways broadcast across meshes to prevent duplicate storms.
     */
    fun processOutboundAudio(
        rawAudioData: ByteArray,
        isEmergency: Boolean,
        currentTimeMs: Long
    ): List<MeshPacket.Control> {
        if (localRole != GatewayRole.PRIMARY) {
            return emptyList()
        }

        val results = mutableListOf<MeshPacket.Control>()
        val seq = ++sequenceNumber

        for ((targetGroupId, info) in federatedGroups) {
            if (info.state != FederationBridgeState.BRIDGED) continue

            val controlPacket = relayEngine.createOutboundCrossMeshPacket(
                federationId = info.groupId, // using groupId as identifier mapping
                targetGroupId = targetGroupId,
                rawAudioData = rawAudioData,
                isCaptain = isLocalLeader,
                isEmergency = isEmergency,
                sequence = seq,
                policy = info.policy,
                currentTimeMs = currentTimeMs
            )

            if (controlPacket != null) {
                results.add(controlPacket)
                totalRelayedOut++
                lastRelayedTimestampMs = currentTimeMs
                federatedGroups[targetGroupId] = info.copy(
                    relayedPacketsCount = info.relayedPacketsCount + 1,
                    lastHeartbeatMs = currentTimeMs
                )
            } else {
                totalDroppedPolicyViolations++
            }
        }

        return results
    }

    /**
     * Ingests and processes an inbound cross-mesh frame.
     */
    fun processInboundControl(
        controlPacket: MeshPacket.Control,
        currentTimeMs: Long
    ): InboundFederationDecision {
        val crossMeshPacket = FederationSerializer.decodeCrossMeshPacket(controlPacket.payload)
            ?: return InboundFederationDecision.DropMalformed("Failed to decode CrossMeshAudioPacket")

        val info = federatedGroups[crossMeshPacket.sourceGroupId]
        val activePolicy = info?.policy ?: defaultPolicy

        val decision = relayEngine.processInboundCrossMeshPacket(controlPacket, activePolicy)
        when (decision) {
            is InboundFederationDecision.Deliver -> {
                totalRelayedIn++
                lastRelayedTimestampMs = currentTimeMs
                if (info != null) {
                    federatedGroups[crossMeshPacket.sourceGroupId] = info.copy(
                        relayedPacketsCount = info.relayedPacketsCount + 1,
                        lastHeartbeatMs = currentTimeMs
                    )
                }
            }
            is InboundFederationDecision.DropPolicyViolation -> {
                totalDroppedPolicyViolations++
            }
            else -> {
                // Drop without incrementing policy count
            }
        }

        return decision
    }

    /**
     * Emits a reactive snapshot for telemetry and cockpit presentation.
     */
    fun getSnapshot(currentTimeMs: Long): FederationSnapshot {
        val overallState = when {
            federatedGroups.isEmpty() -> FederationBridgeState.DISCONNECTED
            federatedGroups.values.any { it.state == FederationBridgeState.BRIDGED } -> FederationBridgeState.BRIDGED
            else -> FederationBridgeState.DISCONNECTED
        }

        return FederationSnapshot(
            localGroupId = localGroupId,
            localRole = localRole,
            bridgeState = overallState,
            federatedGroups = federatedGroups.values.toList(),
            activePolicy = defaultPolicy,
            totalRelayedPacketsIn = totalRelayedIn,
            totalRelayedPacketsOut = totalRelayedOut,
            totalDroppedPolicyViolations = totalDroppedPolicyViolations,
            lastRelayedTimestampMs = lastRelayedTimestampMs
        )
    }

    private fun generateRandomSecret(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        return bytes.toHexPrefix()
    }

    private fun ByteArray.toHexPrefix(): String {
        return joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
