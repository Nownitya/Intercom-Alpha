package org.nowni.intercom_alpha.mesh.federation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.*

class InterGroupGatewayTest {

    @Test
    fun testFederationInviteEncodingDecodingAndExpiry() = runTest {
        val invite = GroupFederationInvite(
            federationId = "fed-alpha-bravo",
            initiatorGroupId = "group-alpha",
            initiatorGroupName = "Squad Alpha",
            initiatorLeaderId = "captain-01",
            sharedSecret = "super-secret-key-12345",
            validUntilMs = 5000L,
            defaultPolicy = CrossGroupPolicy.CAPTAIN_ONLY
        )

        val encoded = FederationSerializer.encodeInvite(invite)
        assertTrue(encoded.startsWith("FEDERATION:v1:"))

        val decoded = FederationSerializer.decodeInvite(encoded)
        assertNotNull(decoded)
        assertEquals(invite.federationId, decoded.federationId)
        assertEquals(invite.initiatorGroupId, decoded.initiatorGroupId)
        assertEquals(invite.initiatorGroupName, decoded.initiatorGroupName)
        assertEquals(invite.initiatorLeaderId, decoded.initiatorLeaderId)
        assertEquals(invite.sharedSecret, decoded.sharedSecret)
        assertEquals(invite.validUntilMs, decoded.validUntilMs)
        assertEquals(invite.defaultPolicy, decoded.defaultPolicy)

        // Gateway accept test
        val gatewayBravo = InterGroupGateway(
            localGroupId = "group-bravo",
            localGroupName = "Squad Bravo",
            localPeerId = "captain-02",
            isLocalLeader = true
        )

        // Expired invite rejection
        assertFalse(gatewayBravo.acceptFederationInvite(decoded, currentTimeMs = 6000L))

        // Valid invite acceptance
        assertTrue(gatewayBravo.acceptFederationInvite(decoded, currentTimeMs = 4000L))

        val snapshot = gatewayBravo.getSnapshot(currentTimeMs = 4000L)
        assertEquals(FederationBridgeState.BRIDGED, snapshot.bridgeState)
        assertEquals(1, snapshot.federatedGroups.size)
        assertEquals("group-alpha", snapshot.federatedGroups.first().groupId)
        assertEquals(CrossGroupPolicy.CAPTAIN_ONLY, snapshot.federatedGroups.first().policy)
    }

    @Test
    fun testCrossMeshRelayEnginePolicyEnforcement() = runTest {
        val relayEngine = CrossMeshRelayEngine(
            localGroupId = "group-alpha",
            localPeerId = "member-05"
        )
        relayEngine.registerFederationCipher("fed-test", "federation-secret-999")

        val rawAudio = "PCM_SAMPLE_DATA_16BIT".encodeToByteArray()

        // 1. Policy: CAPTAIN_ONLY - Non-captain fails
        val regularPacket = relayEngine.createOutboundCrossMeshPacket(
            federationId = "fed-test",
            targetGroupId = "group-bravo",
            rawAudioData = rawAudio,
            isCaptain = false,
            isEmergency = false,
            sequence = 1L,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            currentTimeMs = 1000L
        )
        assertNull(regularPacket, "Regular member audio must be blocked under CAPTAIN_ONLY")

        // 2. Policy: CAPTAIN_ONLY - Captain succeeds
        val captainPacket = relayEngine.createOutboundCrossMeshPacket(
            federationId = "fed-test",
            targetGroupId = "group-bravo",
            rawAudioData = rawAudio,
            isCaptain = true,
            isEmergency = false,
            sequence = 2L,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            currentTimeMs = 1000L
        )
        assertNotNull(captainPacket, "Captain audio must pass under CAPTAIN_ONLY")

        // 3. Policy: CAPTAIN_ONLY - Emergency SOS from regular member succeeds
        val sosPacket = relayEngine.createOutboundCrossMeshPacket(
            federationId = "fed-test",
            targetGroupId = "group-bravo",
            rawAudioData = rawAudio,
            isCaptain = false,
            isEmergency = true,
            sequence = 3L,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            currentTimeMs = 1000L
        )
        assertNotNull(sosPacket, "Emergency SOS must bridge across under CAPTAIN_ONLY")

        // 4. Policy: EMERGENCY_SOS_ONLY - Normal captain audio fails
        val captainNormalUnderSosOnly = relayEngine.createOutboundCrossMeshPacket(
            federationId = "fed-test",
            targetGroupId = "group-bravo",
            rawAudioData = rawAudio,
            isCaptain = true,
            isEmergency = false,
            sequence = 4L,
            policy = CrossGroupPolicy.EMERGENCY_SOS_ONLY,
            currentTimeMs = 1000L
        )
        assertNull(captainNormalUnderSosOnly, "Normal audio must be blocked under EMERGENCY_SOS_ONLY")

        // 5. Policy: ALL_MEMBERS_BRIDGED - Regular member succeeds
        val memberUnderAllBridged = relayEngine.createOutboundCrossMeshPacket(
            federationId = "fed-test",
            targetGroupId = "group-bravo",
            rawAudioData = rawAudio,
            isCaptain = false,
            isEmergency = false,
            sequence = 5L,
            policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
            currentTimeMs = 1000L
        )
        assertNotNull(memberUnderAllBridged, "Regular audio must pass under ALL_MEMBERS_BRIDGED")
    }

    @Test
    fun testEndToEndDualGatewayAudioTranslationAndLoopGuard() = runTest {
        val sharedSecret = "shared-rally-secret-2026"
        val fedId = "group-bravo"

        val gatewayAlpha = InterGroupGateway(
            localGroupId = "group-alpha",
            localGroupName = "Squad Alpha",
            localPeerId = "captain-alpha",
            isLocalLeader = true
        )
        gatewayAlpha.relayEngine.registerFederationCipher(fedId, sharedSecret)

        val gatewayBravo = InterGroupGateway(
            localGroupId = "group-bravo",
            localGroupName = "Squad Bravo",
            localPeerId = "captain-bravo",
            isLocalLeader = true
        )

        // Establish federation
        val inviteAlpha = GroupFederationInvite(
            federationId = fedId,
            initiatorGroupId = "group-alpha",
            initiatorGroupName = "Squad Alpha",
            initiatorLeaderId = "captain-alpha",
            sharedSecret = sharedSecret,
            validUntilMs = 100000L,
            defaultPolicy = CrossGroupPolicy.CAPTAIN_ONLY
        )
        assertTrue(gatewayBravo.acceptFederationInvite(inviteAlpha, currentTimeMs = 1000L))

        // Also register Bravo on Alpha's table
        val inviteBravo = GroupFederationInvite(
            federationId = "group-bravo",
            initiatorGroupId = "group-bravo",
            initiatorGroupName = "Squad Bravo",
            initiatorLeaderId = "captain-bravo",
            sharedSecret = sharedSecret,
            validUntilMs = 100000L,
            defaultPolicy = CrossGroupPolicy.CAPTAIN_ONLY
        )
        assertTrue(gatewayAlpha.acceptFederationInvite(inviteBravo, currentTimeMs = 1000L))

        // Captain Alpha transmits audio
        val testPcm = "TEST_CAPTAIN_ALPHA_SPEECH_VOICE".encodeToByteArray()
        val outboundPackets = gatewayAlpha.processOutboundAudio(
            rawAudioData = testPcm,
            isEmergency = false,
            currentTimeMs = 2000L
        )
        assertEquals(1, outboundPackets.size)
        val crossMeshControl = outboundPackets.first()
        assertEquals(ControlType.FEDERATION_AUDIO, crossMeshControl.type)

        // Gateway Bravo receives and processes packet
        val decisionBravo = gatewayBravo.processInboundControl(crossMeshControl, currentTimeMs = 2020L)
        assertTrue(decisionBravo is InboundFederationDecision.Deliver)
        assertEquals("group-alpha", decisionBravo.sourceGroupId)
        assertEquals("captain-alpha", decisionBravo.originalSenderId)
        assertTrue(decisionBravo.isCaptain)
        assertFalse(decisionBravo.isEmergency)
        assertTrue(testPcm.contentEquals(decisionBravo.rawAudio))

        // Test Anti-Feedback Loop: Send packet back to Gateway Alpha
        // A looped packet will have pathTrace containing "group-alpha"
        val loopedPacket = FederationSerializer.decodeCrossMeshPacket(crossMeshControl.payload)!!
        val simulatedLoopedPacket = loopedPacket.copy(
            pathTrace = listOf("group-alpha", "group-bravo")
        )
        val loopedControl = MeshPacket.Control(
            senderId = "captain-bravo",
            type = ControlType.FEDERATION_AUDIO,
            payload = FederationSerializer.encodeCrossMeshPacket(simulatedLoopedPacket)
        )

        val loopDecision = gatewayAlpha.processInboundControl(loopedControl, currentTimeMs = 2050L)
        assertTrue(loopDecision is InboundFederationDecision.DropFeedbackLoop, "Must drop packet when pathTrace includes localGroupId")
    }

    @Test
    fun testStandbyGatewayDoesNotTransmitOutboundAudio() = runTest {
        val standbyGateway = InterGroupGateway(
            localGroupId = "group-alpha",
            localGroupName = "Squad Alpha",
            localPeerId = "tail-gunner-08",
            isLocalLeader = false // Standby role
        )
        assertEquals(GatewayRole.STANDBY, standbyGateway.localRole)

        standbyGateway.acceptFederationInvite(
            GroupFederationInvite(
                federationId = "group-bravo",
                initiatorGroupId = "group-bravo",
                initiatorGroupName = "Squad Bravo",
                initiatorLeaderId = "captain-bravo",
                sharedSecret = "secret-standby",
                validUntilMs = 50000L
            ),
            currentTimeMs = 1000L
        )

        val packets = standbyGateway.processOutboundAudio(
            rawAudioData = "AUDIO_DATA".encodeToByteArray(),
            isEmergency = false,
            currentTimeMs = 2000L
        )
        assertTrue(packets.isEmpty(), "Standby gateway must suppress outbound transmissions to avoid duplicate storms")

        // Promote to PRIMARY
        standbyGateway.updateGatewayRole(GatewayRole.PRIMARY)
        assertEquals(GatewayRole.PRIMARY, standbyGateway.localRole)
        standbyGateway.isLocalLeader = true // Enable captain PTT

        val packetsAfterPromotion = standbyGateway.processOutboundAudio(
            rawAudioData = "AUDIO_DATA".encodeToByteArray(),
            isEmergency = false,
            currentTimeMs = 2010L
        )
        assertEquals(1, packetsAfterPromotion.size)
    }

    @Test
    fun testTamperedFederationPayloadRejection() = runTest {
        val gateway = InterGroupGateway(
            localGroupId = "group-alpha",
            localGroupName = "Squad Alpha",
            localPeerId = "captain-alpha",
            isLocalLeader = true
        )
        val invite = gateway.createFederationInvite(
            sharedSecret = "authentic-secret-1234",
            currentTimeMs = 1000L
        )

        // Valid packet creation
        val validPacket = gateway.relayEngine.createOutboundCrossMeshPacket(
            federationId = invite.federationId,
            targetGroupId = "group-bravo",
            rawAudioData = "SENSITIVE_VOICE_DATA".encodeToByteArray(),
            isCaptain = true,
            isEmergency = false,
            sequence = 1L,
            policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
            currentTimeMs = 1100L
        )!!

        // Tamper with encrypted ciphertext payload
        val decoded = FederationSerializer.decodeCrossMeshPacket(validPacket.payload)!!
        val tamperedCiphertext = decoded.payload.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0] + 1).toByte()
        val tamperedCrossMesh = decoded.copy(payload = tamperedCiphertext)
        val tamperedControl = validPacket.copy(payload = FederationSerializer.encodeCrossMeshPacket(tamperedCrossMesh))

        val gatewayBravo = InterGroupGateway(
            localGroupId = "group-bravo",
            localGroupName = "Squad Bravo",
            localPeerId = "captain-bravo",
            isLocalLeader = true
        )
        gatewayBravo.acceptFederationInvite(invite, currentTimeMs = 1000L)

        val decision = gatewayBravo.relayEngine.processInboundCrossMeshPacket(
            tamperedControl,
            CrossGroupPolicy.ALL_MEMBERS_BRIDGED
        )
        // Either decode fails or AEAD decrypt fails
        assertTrue(
            decision is InboundFederationDecision.DropAuthFailure || decision is InboundFederationDecision.DropMalformed,
            "Tampered packet must fail authentication, was: $decision"
        )
    }
}
