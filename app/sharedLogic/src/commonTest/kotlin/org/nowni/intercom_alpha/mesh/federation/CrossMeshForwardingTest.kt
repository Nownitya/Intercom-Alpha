package org.nowni.intercom_alpha.mesh.federation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher
import kotlin.test.*

class CrossMeshForwardingTest {

    private val localGroupId = "group-squad-alpha"
    private val localPeerId = "peer-alpha-lead"
    private val siblingGroupId = "group-squad-bravo"
    private val testSharedSecret = "top-secret-convoy-bridge-key"

    @Test
    fun testSelfEchoSuppression() = runTest {
        val loopDetector = FederationLoopDetector(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        val echoPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = localPeerId, // Self origin!
            isCaptain = true,
            isEmergency = false,
            sequence = 1L,
            timestampMs = 1000L,
            pathTrace = listOf(siblingGroupId),
            payload = byteArrayOf(1, 2, 3)
        )

        val result = loopDetector.evaluateInbound(echoPacket, currentTimeMs = 1010L)
        assertTrue(result is LoopCheckResult.DropSelfEcho, "Self-originated packet must be dropped to prevent acoustic echo")
        assertEquals(localPeerId, (result as LoopCheckResult.DropSelfEcho).originalSenderId)
    }

    @Test
    fun testCircularRelayLoopDetection() = runTest {
        val loopDetector = FederationLoopDetector(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Packet already traversed localGroupId
        val loopedPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-bravo-01",
            isCaptain = true,
            isEmergency = false,
            sequence = 10L,
            timestampMs = 2000L,
            pathTrace = listOf(localGroupId, siblingGroupId), // Contains localGroupId!
            payload = byteArrayOf(4, 5, 6)
        )

        val result = loopDetector.evaluateInbound(loopedPacket, currentTimeMs = 2010L)
        assertTrue(result is LoopCheckResult.DropCircularLoop, "Packet traversing local group must be dropped as circular loop")
        assertTrue((result as LoopCheckResult.DropCircularLoop).pathTrace.contains(localGroupId))
    }

    @Test
    fun testCorruptedOrCyclicPathTraceDetection() = runTest {
        val loopDetector = FederationLoopDetector(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Contains duplicate hops (e.g. Charlie -> Bravo -> Charlie)
        val cyclicPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-bravo-01",
            isCaptain = false,
            isEmergency = false,
            sequence = 15L,
            timestampMs = 3000L,
            pathTrace = listOf("group-charlie", siblingGroupId, "group-charlie"),
            payload = byteArrayOf(7, 8, 9)
        )

        val result = loopDetector.evaluateInbound(cyclicPacket, currentTimeMs = 3010L)
        assertTrue(result is LoopCheckResult.DropCircularLoop, "Path trace with internal loop cycles must be dropped")
    }

    @Test
    fun testMaxHopLimitExceeded() = runTest {
        val loopDetector = FederationLoopDetector(
            localGroupId = localGroupId,
            localPeerId = localPeerId,
            maxHops = 3
        )

        // Exactly 3 hops already traversed (Group 1 -> Group 2 -> Group 3)
        val deepPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-deep-node",
            isCaptain = true,
            isEmergency = false,
            sequence = 20L,
            timestampMs = 4000L,
            pathTrace = listOf("group-1", "group-2", "group-3"),
            payload = byteArrayOf(10, 11)
        )

        val result = loopDetector.evaluateInbound(deepPacket, currentTimeMs = 4010L)
        assertTrue(result is LoopCheckResult.DropHopLimitExceeded, "Packets meeting or exceeding max hops must be dropped")
        assertEquals(3, (result as LoopCheckResult.DropHopLimitExceeded).hops)
    }

    @Test
    fun testConcurrentGatewayDeduplication() = runTest {
        val loopDetector = FederationLoopDetector(
            localGroupId = localGroupId,
            localPeerId = localPeerId,
            dedupWindowMs = 3000L
        )

        val originalPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-bravo-captain",
            isCaptain = true,
            isEmergency = false,
            sequence = 101L,
            timestampMs = 5000L,
            pathTrace = listOf(siblingGroupId),
            payload = byteArrayOf(12, 13, 14)
        )

        // First arrival: Accepted
        val firstResult = loopDetector.evaluateInbound(originalPacket, currentTimeMs = 5010L)
        assertTrue(firstResult is LoopCheckResult.Accept)
        assertEquals(listOf(siblingGroupId, localGroupId), (firstResult as LoopCheckResult.Accept).updatedPathTrace)

        // Second arrival from redundant gateway: Dropped as duplicate
        val duplicateResult = loopDetector.evaluateInbound(originalPacket, currentTimeMs = 5020L)
        assertTrue(duplicateResult is LoopCheckResult.DropDuplicate, "Second arrival must be dropped as duplicate")

        // Arrival after window expiration: Accepted again
        val afterExpiryResult = loopDetector.evaluateInbound(originalPacket, currentTimeMs = 8500L)
        assertTrue(afterExpiryResult is LoopCheckResult.Accept, "Arrival after dedup window expiration must be accepted")
    }

    @Test
    fun testSubGroupPrivateChannelsStayInternal() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Channel 1 (Convoy Main) under ALL_MEMBERS_BRIDGED: Allowed
        val mainChannelOut = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = false,
            isCaptain = false,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(mainChannelOut is OutboundForwardingDecision.AllowForward)

        // Channel 2 (Scouts & Lead) under ALL_MEMBERS_BRIDGED: Isolated, blocked from bridging
        val scoutChannelOut = arbiter.evaluateOutbound(
            channelId = 2,
            isPrivateSubGroup = false,
            isCaptain = false,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(scoutChannelOut is OutboundForwardingDecision.BlockSubGroupIsolated)
        assertEquals(2, (scoutChannelOut as OutboundForwardingDecision.BlockSubGroupIsolated).channelId)

        // Channel 1 with isPrivateSubGroup = true (e.g. Rider-Pillion): Isolated, blocked
        val privateSubGroupOut = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = true,
            isCaptain = true,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(privateSubGroupOut is OutboundForwardingDecision.BlockSubGroupIsolated)
    }

    @Test
    fun testCaptainOnlyPolicyOutbound() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Regular squad member on Channel 1 under CAPTAIN_ONLY: Blocked
        val memberDecision = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = false,
            isCaptain = false,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(memberDecision is OutboundForwardingDecision.BlockPolicyViolation)

        // Captain on Channel 1 under CAPTAIN_ONLY: Allowed
        val captainDecision = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = false,
            isCaptain = true,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(captainDecision is OutboundForwardingDecision.AllowForward)
    }

    @Test
    fun testEmergencySosPreemptionOverridesAll() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Emergency SOS from regular member on sub-group Channel 3 under EMERGENCY_SOS_ONLY
        val emergencyOnSubChannel = arbiter.evaluateOutbound(
            channelId = 3,
            isPrivateSubGroup = true,
            isCaptain = false,
            isEmergency = true, // Emergency preemption active!
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.EMERGENCY_SOS_ONLY,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(emergencyOnSubChannel is OutboundForwardingDecision.AllowForward)
        assertTrue((emergencyOnSubChannel as OutboundForwardingDecision.AllowForward).isEmergency)

        // Channel 16 (Emergency SOS channel) without explicit boolean isEmergency under CAPTAIN_ONLY
        val ch16Decision = arbiter.evaluateOutbound(
            channelId = 16,
            isPrivateSubGroup = false,
            isCaptain = false,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(ch16Decision is OutboundForwardingDecision.AllowForward)
        assertTrue((ch16Decision as OutboundForwardingDecision.AllowForward).isEmergency)
    }

    @Test
    fun testStandbyGatewayAndDisabledPolicyOutbound() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // Standby gateway blocked
        val standbyDecision = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = false,
            isCaptain = true,
            isEmergency = true,
            gatewayRole = GatewayRole.STANDBY,
            policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(standbyDecision is OutboundForwardingDecision.BlockStandbyGateway)

        // Policy DISABLED blocked
        val disabledDecision = arbiter.evaluateOutbound(
            channelId = 1,
            isPrivateSubGroup = false,
            isCaptain = true,
            isEmergency = true,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.DISABLED,
            targetGroupIds = listOf(siblingGroupId)
        )
        assertTrue(disabledDecision is OutboundForwardingDecision.BlockDisabled)
    }

    @Test
    fun testPreDecryptionInboundSecurityGate() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        // 1. Target mismatch dropped before decrypting
        val mismatchPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = "other-squad-charlie", // Not our group!
            originalSenderId = "peer-bravo-captain",
            isCaptain = true,
            isEmergency = false,
            sequence = 201L,
            timestampMs = 6000L,
            pathTrace = listOf(siblingGroupId),
            payload = byteArrayOf(99)
        )
        val mismatchDecision = arbiter.evaluateInboundPreDecryption(mismatchPacket, CrossGroupPolicy.ALL_MEMBERS_BRIDGED)
        assertTrue(mismatchDecision is InboundForwardingDecision.DropTargetMismatch)

        // 2. Policy violation: non-captain packet dropped under CAPTAIN_ONLY before decrypting
        val memberPacket = CrossMeshAudioPacket(
            federationId = "fed-rally",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-bravo-member",
            isCaptain = false,
            isEmergency = false,
            sequence = 202L,
            timestampMs = 6010L,
            pathTrace = listOf(siblingGroupId),
            payload = byteArrayOf(99)
        )
        val policyViolationDecision = arbiter.evaluateInboundPreDecryption(memberPacket, CrossGroupPolicy.CAPTAIN_ONLY)
        assertTrue(policyViolationDecision is InboundForwardingDecision.DropPolicyViolation)

        // 3. Legitimate packet accepted for decryption
        val captainPacket = memberPacket.copy(isCaptain = true, sequence = 203L)
        val acceptedDecision = arbiter.evaluateInboundPreDecryption(captainPacket, CrossGroupPolicy.CAPTAIN_ONLY)
        assertTrue(acceptedDecision is InboundForwardingDecision.AcceptForDecryption)
        assertEquals(listOf(siblingGroupId, localGroupId), (acceptedDecision as InboundForwardingDecision.AcceptForDecryption).updatedPathTrace)
    }

    @Test
    fun testEndToEndIngestionPipelineAndAuditTelemetry() = runTest {
        val arbiter = CrossMeshForwardingArbiter(
            localGroupId = localGroupId,
            localPeerId = localPeerId
        )

        val key = PacketCipher.deriveKey(testSharedSecret)
        val cipher = PacketCipher(key)
        val rawAudio = "SECURE_CROSS_MESH_AUDIO_SAMPLE".encodeToByteArray()
        val encryptedPayload = cipher.encrypt(rawAudio)

        val validPacket = CrossMeshAudioPacket(
            federationId = "fed-test",
            sourceGroupId = siblingGroupId,
            targetGroupId = localGroupId,
            originalSenderId = "peer-bravo-lead",
            isCaptain = true,
            isEmergency = false,
            sequence = 301L,
            timestampMs = 7000L,
            pathTrace = listOf(siblingGroupId),
            payload = encryptedPayload
        )

        val controlPacket = MeshPacket.Control(
            senderId = "peer-bravo-lead",
            type = ControlType.FEDERATION_AUDIO,
            payload = FederationSerializer.encodeCrossMeshPacket(validPacket)
        )

        // 1. Successful delivery
        val deliveryResult = arbiter.processInboundPacket(
            controlPacket = controlPacket,
            activePolicy = CrossGroupPolicy.CAPTAIN_ONLY,
            cipher = cipher,
            currentTimeMs = 7020L
        )
        assertTrue(deliveryResult is InboundRelayResult.Deliver)
        assertTrue(rawAudio.contentEquals((deliveryResult as InboundRelayResult.Deliver).rawAudio))

        // 2. Duplicate packet delivery attempt: Dropped as DUPLICATE
        val duplicateResult = arbiter.processInboundPacket(
            controlPacket = controlPacket,
            activePolicy = CrossGroupPolicy.CAPTAIN_ONLY,
            cipher = cipher,
            currentTimeMs = 7030L
        )
        assertTrue(duplicateResult is InboundRelayResult.Dropped)
        assertEquals(DropCategory.DUPLICATE, (duplicateResult as InboundRelayResult.Dropped).category)

        // 3. Tampered payload: Dropped as AUTH_FAILURE
        val tamperedPayload = encryptedPayload.copyOf()
        tamperedPayload[tamperedPayload.size - 1] = (tamperedPayload[tamperedPayload.size - 1] + 1).toByte()
        val tamperedPacket = validPacket.copy(sequence = 302L, payload = tamperedPayload)
        val tamperedControl = MeshPacket.Control(
            senderId = "peer-bravo-lead",
            type = ControlType.FEDERATION_AUDIO,
            payload = FederationSerializer.encodeCrossMeshPacket(tamperedPacket)
        )
        val tamperedResult = arbiter.processInboundPacket(
            controlPacket = tamperedControl,
            activePolicy = CrossGroupPolicy.CAPTAIN_ONLY,
            cipher = cipher,
            currentTimeMs = 7040L
        )
        assertTrue(tamperedResult is InboundRelayResult.Dropped)
        assertEquals(DropCategory.AUTH_FAILURE, (tamperedResult as InboundRelayResult.Dropped).category)

        // Verify audit snapshot metrics
        val audit = arbiter.getAuditSnapshot()
        assertEquals(3, audit.totalInboundReceived)
        assertEquals(1, audit.totalInboundDelivered)
        assertEquals(2, audit.totalInboundDropped)
        assertEquals(1, audit.droppedByCategory[DropCategory.DUPLICATE])
        assertEquals(1, audit.droppedByCategory[DropCategory.AUTH_FAILURE])
    }
}
