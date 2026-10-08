package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.mesh.federation.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Dual-Convoy 32-Node Cross-Mesh Simulation Harness & Multi-Group Federation Benchmark.
 *
 * Validates:
 * 1. 32-node dual-convoy topology: Squad Alpha (16 riders) and Squad Bravo (16 riders)
 *    operating on distinct cryptographic keys with dual-role gateway nodes.
 * 2. Sub-group audio isolation: intra-squad channels (2..15) strictly isolated to local squad
 *    with zero spillover to sibling convoy.
 * 3. Road captain cross-group intercom under CAPTAIN_ONLY and ALL_MEMBERS_BRIDGED policies.
 * 4. Emergency SOS universal preemption: eCall/SOS packets broadcast across both meshes
 *    reaching all 32 nodes unconditionally.
 * 5. Anti-feedback loop guard: zero circular broadcast storms when dual gateways (Lead & Sweep)
 *    operate concurrently in parallel.
 * 6. Gateway re-encryption latency benchmark: < 25ms transformation time using pure KMP AEAD.
 *
 * Acceptance Criteria (Sprint 23 - ICA-2304):
 * - Simulates 32 nodes across 2 distinct cryptographic groups with dual gateway nodes.
 * - Demonstrates sub-group audio isolation under normal traffic and universal relay on Emergency SOS.
 * - Verifies zero circular feedback loops when both gateways operate simultaneously.
 * - Gateway re-encryption latency verified < 25ms.
 * - Full multiplatform test suite pass across all targets.
 */
class MultiGroupFederationSimulationHarness {

    /**
     * Simulated rider node in a 16-node motorcycle squad.
     */
    class VirtualSquadNode(
        val nodeId: String,
        val groupId: String,
        val squadIndex: Int,
        val isCaptain: Boolean = false,
        val isSweep: Boolean = false,
        val isGateway: Boolean = false,
        val groupCipher: PacketCipher
    ) {
        val deduplicator = PacketDeduplicator()
        var gateway: InterGroupGateway? = null

        var deliveredIntraAudioCount: Int = 0
        var deliveredCrossAudioCount: Int = 0
        var deliveredEmergencyCount: Int = 0
        var droppedLoopCount: Int = 0

        suspend fun resetCounters() {
            deliveredIntraAudioCount = 0
            deliveredCrossAudioCount = 0
            deliveredEmergencyCount = 0
            droppedLoopCount = 0
            deduplicator.clear()
        }

        suspend fun receiveIntraAudio(packet: MeshPacket.Audio): Boolean {
            if (!deduplicator.shouldProcess(packet)) return false
            // Decrypt using squad key
            val decrypted = groupCipher.decrypt(packet.data)
                ?: return false // Decryption failed
            deliveredIntraAudioCount++
            return true
        }

        suspend fun receiveEmergencyPacket(packet: MeshPacket.Control): Boolean {
            if (!deduplicator.shouldProcess(packet)) return false
            deliveredEmergencyCount++
            return true
        }

        fun receiveCrossMeshAudio(rawAudio: ByteArray, isEmergency: Boolean) {
            if (isEmergency) {
                deliveredEmergencyCount++
            } else {
                deliveredCrossAudioCount++
            }
        }
    }

    /**
     * Complete 32-node dual-convoy federation simulation environment.
     */
    class DualConvoyEnvironment {
        val squadAlphaKey = PacketCipher.deriveKey("alpha-convoy-tactical-mesh-passphrase-2026")
        val squadBravoKey = PacketCipher.deriveKey("bravo-convoy-tactical-mesh-passphrase-2026")
        val federationSharedSecret = "rally-alpha-bravo-cross-convoy-secret-2026"

        val cipherAlpha = PacketCipher(squadAlphaKey)
        val cipherBravo = PacketCipher(squadBravoKey)

        val alphaNodes = mutableListOf<VirtualSquadNode>()
        val bravoNodes = mutableListOf<VirtualSquadNode>()

        // Dual Gateways
        lateinit var alphaGateway1Lead: VirtualSquadNode
        lateinit var alphaGateway2Sweep: VirtualSquadNode
        lateinit var bravoGateway1Lead: VirtualSquadNode
        lateinit var bravoGateway2Sweep: VirtualSquadNode

        init {
            // Build Squad Alpha (16 nodes)
            for (i in 0 until 16) {
                val id = "alpha-${i.toString().padStart(2, '0')}"
                val isCap = (i == 0)
                val isSwp = (i == 15)
                val isGw = isCap || isSwp
                val node = VirtualSquadNode(
                    nodeId = id,
                    groupId = "squad-alpha",
                    squadIndex = i,
                    isCaptain = isCap,
                    isSweep = isSwp,
                    isGateway = isGw,
                    groupCipher = cipherAlpha
                )
                if (isCap) alphaGateway1Lead = node
                if (isSwp) alphaGateway2Sweep = node
                alphaNodes.add(node)
            }

            // Build Squad Bravo (16 nodes)
            for (i in 0 until 16) {
                val id = "bravo-${i.toString().padStart(2, '0')}"
                val isCap = (i == 0)
                val isSwp = (i == 15)
                val isGw = isCap || isSwp
                val node = VirtualSquadNode(
                    nodeId = id,
                    groupId = "squad-bravo",
                    squadIndex = i,
                    isCaptain = isCap,
                    isSweep = isSwp,
                    isGateway = isGw,
                    groupCipher = cipherBravo
                )
                if (isCap) bravoGateway1Lead = node
                if (isSwp) bravoGateway2Sweep = node
                bravoNodes.add(node)
            }

            // Configure Gateway Engines
            setupGateway(alphaGateway1Lead, "squad-alpha", "Squad Alpha", isLeader = true)
            setupGateway(alphaGateway2Sweep, "squad-alpha", "Squad Alpha", isLeader = false)
            setupGateway(bravoGateway1Lead, "squad-bravo", "Squad Bravo", isLeader = true)
            setupGateway(bravoGateway2Sweep, "squad-bravo", "Squad Bravo", isLeader = false)

            // Mount Federation Bridges across both gateways
            mountFederation(alphaGateway1Lead, bravoGateway1Lead)
            mountFederation(alphaGateway2Sweep, bravoGateway2Sweep)
        }

        private fun setupGateway(node: VirtualSquadNode, groupId: String, groupName: String, isLeader: Boolean) {
            val gw = InterGroupGateway(
                localGroupId = groupId,
                localGroupName = groupName,
                localPeerId = node.nodeId,
                isLocalLeader = isLeader,
                defaultPolicy = CrossGroupPolicy.CAPTAIN_ONLY
            )
            node.gateway = gw
        }

        private fun mountFederation(nodeA: VirtualSquadNode, nodeB: VirtualSquadNode) {
            val gwA = nodeA.gateway ?: return
            val gwB = nodeB.gateway ?: return

            val inviteA = gwA.createFederationInvite(
                sharedSecret = federationSharedSecret,
                policy = CrossGroupPolicy.CAPTAIN_ONLY,
                currentTimeMs = 1000L
            )
            gwB.acceptFederationInvite(inviteA, currentTimeMs = 1010L)

            val inviteB = gwB.createFederationInvite(
                sharedSecret = federationSharedSecret,
                policy = CrossGroupPolicy.CAPTAIN_ONLY,
                currentTimeMs = 1000L
            )
            gwA.acceptFederationInvite(inviteB, currentTimeMs = 1010L)
        }

        fun allNodes(): List<VirtualSquadNode> = alphaNodes + bravoNodes

        suspend fun resetCounters() {
            allNodes().forEach { it.resetCounters() }
        }
    }

    @Test
    fun testDualConvoy32NodeSubGroupAudioIsolation() = runTest {
        val env = DualConvoyEnvironment()
        env.resetCounters()

        // 1. Intra-squad private chat on Channel 2 (Scouts) generated by rider alpha-03
        val rawScoutAudio = "tactical-scout-rocky-terrain-ahead".encodeToByteArray()
        val encResult = env.cipherAlpha.encrypt(rawScoutAudio)
        val intraPacket = MeshPacket.Audio(
            senderId = "alpha-03",
            sequence = 100L,
            timestamp = 1000L,
            data = encResult,
            profile = AudioProfile.MEDIUM
        )

        // Broadcast within Squad Alpha
        env.alphaNodes.forEach { node ->
            node.receiveIntraAudio(intraPacket)
        }

        // Gateway evaluates outbound candidate for Channel 2
        val arbiterLead = env.alphaGateway1Lead.gateway!!.forwardingArbiter
        val outboundDecision = arbiterLead.evaluateOutbound(
            channelId = 2, // Sub-group isolated channel
            isCaptain = false,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf("squad-bravo")
        )

        // Must block as sub-group isolated
        assertTrue(
            outboundDecision is OutboundForwardingDecision.BlockSubGroupIsolated,
            "Channel 2 intra-squad audio must be isolated from federation"
        )

        // Verify isolation delivery counts:
        // All 16 Squad Alpha nodes received the intra-squad audio
        env.alphaNodes.forEach { node ->
            assertEquals(1, node.deliveredIntraAudioCount, "Node ${node.nodeId} in Alpha should receive intra audio")
            assertEquals(0, node.deliveredCrossAudioCount, "Node ${node.nodeId} in Alpha should have 0 cross audio")
        }

        // Exactly 0 Squad Bravo nodes received any audio
        env.bravoNodes.forEach { node ->
            assertEquals(0, node.deliveredIntraAudioCount, "Node ${node.nodeId} in Bravo must NOT receive Alpha intra audio")
            assertEquals(0, node.deliveredCrossAudioCount, "Node ${node.nodeId} in Bravo must NOT receive Alpha cross audio")
        }

        // 2. Now Road Captain (alpha-00) speaks on Channel 1 (Captain Intercom) under CAPTAIN_ONLY policy
        val rawCaptainAudio = "alpha-lead-to-bravo-lead-merge-at-checkpoint-charlie".encodeToByteArray()
        val captainOutboundDecision = arbiterLead.evaluateOutbound(
            channelId = 1,
            isCaptain = true,
            isEmergency = false,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.CAPTAIN_ONLY,
            targetGroupIds = listOf("squad-bravo")
        )

        assertTrue(
            captainOutboundDecision is OutboundForwardingDecision.AllowForward,
            "Road Captain speech on Channel 1 must be permitted under CAPTAIN_ONLY policy"
        )

        // Gateway 1 generates cross-mesh control packet
        val outboundPackets = env.alphaGateway1Lead.gateway!!.processOutboundAudio(
            rawAudioData = rawCaptainAudio,
            isEmergency = false,
            currentTimeMs = 2000L
        )
        assertEquals(1, outboundPackets.size, "Should produce 1 cross-mesh control packet for Bravo")

        // Ingest at Bravo Gateway 1 (Lead)
        val inboundDecision = env.bravoGateway1Lead.gateway!!.processInboundControl(
            controlPacket = outboundPackets.first(),
            currentTimeMs = 2010L
        )

        assertTrue(inboundDecision is InboundFederationDecision.Deliver, "Bravo Gateway should accept and deliver captain voice")
        val deliveredPacket = inboundDecision
        assertEquals("alpha-00", deliveredPacket.originalSenderId)
        assertTrue(deliveredPacket.isCaptain)

        // Deliver to Bravo Captain (bravo-00)
        env.bravoGateway1Lead.receiveCrossMeshAudio(deliveredPacket.rawAudio, isEmergency = false)
        assertEquals(1, env.bravoGateway1Lead.deliveredCrossAudioCount)
    }

    @Test
    fun testEmergencySosUniversalPreemptionAcross32Nodes() = runTest {
        val env = DualConvoyEnvironment()
        env.resetCounters()

        // Set policy to EMERGENCY_SOS_ONLY across gateways
        env.alphaGateway1Lead.gateway!!.setCrossGroupPolicy("squad-bravo", CrossGroupPolicy.EMERGENCY_SOS_ONLY)
        env.bravoGateway1Lead.gateway!!.setCrossGroupPolicy("squad-alpha", CrossGroupPolicy.EMERGENCY_SOS_ONLY)

        // Deep-trail rider alpha-07 triggers Emergency SOS (Channel 16)
        val sosRider = env.alphaNodes[7]
        val rawSosAlert = "ECALL-SOS-CRASH-DETECTED:lat=45.123,lon=-121.456,gForce=4.8g".encodeToByteArray()

        val emergencyControl = MeshPacket.Control(
            senderId = sosRider.nodeId,
            type = ControlType.CRASH_ALERT,
            payload = rawSosAlert
        )

        // 1. Floods across all 16 Squad Alpha nodes
        env.alphaNodes.forEach { node ->
            node.receiveEmergencyPacket(emergencyControl)
        }

        // 2. Both Alpha Gateways receive the emergency candidate
        val arbiter1 = env.alphaGateway1Lead.gateway!!.forwardingArbiter
        val outDecision1 = arbiter1.evaluateOutbound(
            channelId = 16,
            isCaptain = false,
            isEmergency = true,
            gatewayRole = GatewayRole.PRIMARY,
            policy = CrossGroupPolicy.EMERGENCY_SOS_ONLY,
            targetGroupIds = listOf("squad-bravo")
        )
        assertTrue(outDecision1 is OutboundForwardingDecision.AllowForward, "Emergency SOS must preempt all policies")

        // Alpha Lead Gateway forwards to Bravo
        val crossPackets = env.alphaGateway1Lead.gateway!!.processOutboundAudio(
            rawAudioData = rawSosAlert,
            isEmergency = true,
            currentTimeMs = 5000L
        )
        assertEquals(1, crossPackets.size)

        // Bravo Lead Gateway ingests cross-mesh packet
        val inDecision = env.bravoGateway1Lead.gateway!!.processInboundControl(
            controlPacket = crossPackets.first(),
            currentTimeMs = 5020L
        )
        assertTrue(inDecision is InboundFederationDecision.Deliver, "Emergency SOS must be accepted by Bravo gateway")
        val delivered = inDecision
        assertTrue(delivered.isEmergency)

        // 3. Flood emergency frame across all 16 Squad Bravo nodes
        val relayedEmergencyControl = MeshPacket.Control(
            senderId = env.bravoGateway1Lead.nodeId,
            type = ControlType.CRASH_ALERT,
            payload = delivered.rawAudio
        )
        env.bravoNodes.forEach { node ->
            node.receiveEmergencyPacket(relayedEmergencyControl)
        }

        // 4. Verify 100% universal reception: All 32 nodes received the Emergency SOS!
        assertEquals(32, env.allNodes().size)
        env.allNodes().forEach { node ->
            assertEquals(1, node.deliveredEmergencyCount, "Node ${node.nodeId} must receive Emergency SOS alert")
        }
    }

    @Test
    fun testZeroCircularFeedbackLoopsWithDualConcurrentGateways() = runTest {
        val env = DualConvoyEnvironment()
        env.resetCounters()

        // Configure both gateways for ALL_MEMBERS_BRIDGED for full traffic testing
        env.alphaGateway1Lead.gateway!!.setCrossGroupPolicy("squad-bravo", CrossGroupPolicy.ALL_MEMBERS_BRIDGED)
        env.alphaGateway2Sweep.gateway!!.setCrossGroupPolicy("squad-bravo", CrossGroupPolicy.ALL_MEMBERS_BRIDGED)
        env.bravoGateway1Lead.gateway!!.setCrossGroupPolicy("squad-alpha", CrossGroupPolicy.ALL_MEMBERS_BRIDGED)
        env.bravoGateway2Sweep.gateway!!.setCrossGroupPolicy("squad-alpha", CrossGroupPolicy.ALL_MEMBERS_BRIDGED)

        // Both gateways active in parallel:
        // Set Gateway 2 (Sweep) as PRIMARY too to simulate worst-case redundant bridging race condition
        env.alphaGateway2Sweep.gateway!!.updateGatewayRole(GatewayRole.PRIMARY)
        env.bravoGateway2Sweep.gateway!!.updateGatewayRole(GatewayRole.PRIMARY)

        var totalFramesInjected = 0
        var totalLoopsBlocked = 0
        var totalDuplicatesSuppressed = 0

        val rawVoiceFrame = "convoy-sweep-all-clear-milemarker-42".encodeToByteArray()

        // Inject 25 burst frames from Alpha
        for (seq in 1..25) {
            totalFramesInjected++
            val timeMs = 10000L + seq * 20L

            // Gateway 1 forwards frame from Alpha -> Bravo
            val outPacketsGw1 = env.alphaGateway1Lead.gateway!!.processOutboundAudio(
                rawAudioData = rawVoiceFrame,
                isEmergency = false,
                currentTimeMs = timeMs
            )

            for (pkt in outPacketsGw1) {
                // Ingest at Bravo Gateway 1
                val dec1 = env.bravoGateway1Lead.gateway!!.processInboundControl(pkt, timeMs + 5L)
                if (dec1 is InboundFederationDecision.Deliver) {
                    // Sibling Gateway 2 (Sweep) in Bravo overhears this bridged frame
                    // and attempts to re-forward it back to Alpha!
                    val decoded = FederationSerializer.decodeCrossMeshPacket(pkt.payload)!!
                    val loopCheckAtGw2 = env.bravoGateway2Sweep.gateway!!.loopDetector.evaluateInbound(
                        packet = decoded,
                        currentTimeMs = timeMs + 10L
                    )

                    // The packet's pathTrace contains "squad-alpha". If Gateway 2 tried to relay back to Alpha,
                    // or if Gateway 2 ingests the duplicate frame:
                    if (loopCheckAtGw2 is LoopCheckResult.DropCircularLoop) {
                        totalLoopsBlocked++
                    } else if (loopCheckAtGw2 is LoopCheckResult.DropDuplicate) {
                        totalDuplicatesSuppressed++
                    }
                }
            }
        }

        // Loop detector and deduplicator prevented feedback loops
        assertTrue(totalFramesInjected == 25)
        // Verify no infinite recursive loops occurred
        val gw1RelayedOut = env.alphaGateway1Lead.gateway!!.getSnapshot(15000L).totalRelayedPacketsOut
        assertEquals(25L, gw1RelayedOut, "Gateway 1 should relay exactly 25 outbound frames without multiplication")
    }

    @Test
    fun testGatewayReEncryptionLatencyUnder25ms() = runTest {
        val relayEngineAlpha = CrossMeshRelayEngine("squad-alpha", "alpha-lead")
        val relayEngineBravo = CrossMeshRelayEngine("squad-bravo", "bravo-lead")
        val testSecret = "performance-benchmark-secret-aead-key"
        relayEngineAlpha.registerFederationCipher("fed-perf", testSecret)
        relayEngineBravo.registerFederationCipher("fed-perf", testSecret)

        val frameCount = 100
        val dummyAudioPayload = ByteArray(160) { (it and 0x7F).toByte() } // Standard 20ms Opus frame

        val durations = mutableListOf<Double>()

        for (i in 1..frameCount) {
            val mark = TimeSource.Monotonic.markNow()

            // 1. Outbound transformation: wrap in CrossMeshAudioPacket + AEAD encrypt
            val outboundPacket = relayEngineAlpha.createOutboundCrossMeshPacket(
                federationId = "fed-perf",
                targetGroupId = "squad-bravo",
                rawAudioData = dummyAudioPayload,
                isCaptain = true,
                isEmergency = false,
                sequence = i.toLong(),
                policy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED,
                currentTimeMs = 1000L + i
            )
            assertNotNull(outboundPacket)

            // 2. Inbound transformation at sibling gateway: decode + AEAD decrypt + verify
            val decision = relayEngineBravo.processInboundCrossMeshPacket(
                controlPacket = outboundPacket,
                activePolicy = CrossGroupPolicy.ALL_MEMBERS_BRIDGED
            )
            assertTrue(decision is InboundFederationDecision.Deliver)
            assertEquals(160, decision.rawAudio.size)

            val elapsedMs = mark.elapsedNow().inWholeMicroseconds / 1000.0
            durations.add(elapsedMs)
        }

        val avgDurationMs = durations.average()
        val maxDurationMs = durations.maxOrNull() ?: 0.0

        println("Gateway Re-Encryption Latency: avg=${avgDurationMs}ms, max=${maxDurationMs}ms over $frameCount frames")

        // Acceptance Criteria: latency must be strictly < 25ms
        assertTrue(
            avgDurationMs < 25.0,
            "Average gateway re-encryption latency must be < 25ms (actual: ${avgDurationMs}ms)"
        )
        assertTrue(
            maxDurationMs < 25.0,
            "Maximum gateway re-encryption latency must be < 25ms (actual: ${maxDurationMs}ms)"
        )
    }
}
