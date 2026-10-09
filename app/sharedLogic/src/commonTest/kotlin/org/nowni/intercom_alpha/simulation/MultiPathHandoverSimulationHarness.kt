package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.mesh.failover.FailoverMode
import org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 16-Node Convoy Highway Split & Multi-Path Seamless Failover Simulation Harness.
 *
 * Validates:
 * 1. 16-node tactical motorcycle convoy topology split into Lead Squad (nodes 0..7) and Tail Squad (nodes 8..15).
 * 2. Stage 1: Nominal convoy operating 100% over offline BLE mesh (BLE_DIRECT) with zero cellular data consumption.
 * 3. Stage 2: Highway drift & RF degradation (ETX > 2.5, RSSI -89 dBm) immediately triggering MULTI_PATH_HEDGED,
 *    dual-casting frames over BLE and Cloud Relay with receiver deduplication achieving 0% voice loss.
 * 4. Stage 3: Out-of-range convoy split (>1 km) cleanly transitioning to CLOUD_RELAY_ONLY via Ktor WebSocket relay.
 * 5. Stage 4: Mountain dead zone traversal gracefully transitioning to RESTRICTED_OFFLINE while preserving intra-group meshes.
 * 6. Stage 5: Re-convergence with 5,000ms hysteresis dampening preventing boundary flapping before safely de-escalating to BLE_DIRECT.
 * 7. Continuous active speech delivery under active handover with 100% frame arrival and 0 duplicate playback.
 *
 * Acceptance Criteria (Sprint 25 - ICA-2504):
 * - Simulates multi-node convoy drift, RF degradation, cloud handover, and re-entry hysteresis.
 * - Verifies 0% audio frame loss during active speech during BLE-to-Cloud transition.
 * - Validates hysteresis dampening prevents oscillation under fluctuating RSSI.
 * - Full multiplatform test suite pass across all targets.
 */
class MultiPathHandoverSimulationHarness {

    /**
     * Simulated convoy participant node.
     */
    class ConvoyParticipant(
        val nodeId: String,
        val isLeadGroup: Boolean
    ) {
        val arbiter = MultiPathFailoverArbiter()
        val deduplicator = PacketDeduplicator(windowMs = 10000L)

        val receivedAudioFrames = mutableListOf<MeshPacket.Audio>()
        var duplicatesSuppressed: Int = 0

        suspend fun receiveInbound(packet: MeshPacket.Audio, currentTimeMs: Long): Boolean {
            if (!deduplicator.shouldProcess(packet, now = currentTimeMs)) {
                duplicatesSuppressed++
                return false
            }
            receivedAudioFrames.add(packet)
            return true
        }

        suspend fun reset() {
            receivedAudioFrames.clear()
            duplicatesSuppressed = 0
            deduplicator.clear()
        }
    }

    /**
     * Simulated convoy communications environment managing 16 nodes.
     */
    class ConvoyHighwayEnvironment {
        val participants = (0..15).map { index ->
            ConvoyParticipant(
                nodeId = "rider-${index.toString().padStart(2, '0')}",
                isLeadGroup = index < 8
            )
        }

        var interGroupBleAvailable = true
        var interGroupBleEtx = 1.1
        var interGroupBleDeliveryRatio = 0.98
        var interGroupBleRssi = -65

        var cloudRelayAvailable = true
        var virtualClockMs: Long = 1000L

        var totalBleDispatches = 0
        var totalCloudDispatches = 0

        suspend fun syncAllArbiters() {
            for (node in participants) {
                node.arbiter.updateCloudStatus(cloudRelayAvailable, virtualClockMs)

                if (node.isLeadGroup) {
                    val peerCount = if (interGroupBleAvailable) 15 else 7
                    node.arbiter.updateMeshMetrics(
                        peerCount = peerCount,
                        avgEtx = if (interGroupBleAvailable) interGroupBleEtx else 1.1,
                        deliveryRatio = if (interGroupBleAvailable) interGroupBleDeliveryRatio else 0.98,
                        minRssiDbm = if (interGroupBleAvailable) interGroupBleRssi else -65,
                        currentTimeMs = virtualClockMs
                    )
                } else {
                    val peerCount = if (interGroupBleAvailable) 15 else 7
                    node.arbiter.updateMeshMetrics(
                        peerCount = peerCount,
                        avgEtx = if (interGroupBleAvailable) interGroupBleEtx else 1.1,
                        deliveryRatio = if (interGroupBleAvailable) interGroupBleDeliveryRatio else 0.98,
                        minRssiDbm = if (interGroupBleAvailable) interGroupBleRssi else -65,
                        currentTimeMs = virtualClockMs
                    )
                }
            }
        }

        suspend fun transmitVoice(senderNodeIndex: Int, sequence: Long, frameData: ByteArray = byteArrayOf(0x01, 0x02)) {
            val sender = participants[senderNodeIndex]
            val packet = MeshPacket.Audio(
                senderId = sender.nodeId,
                sequence = sequence,
                timestamp = virtualClockMs,
                data = frameData,
                profile = AudioProfile.MEDIUM
            )

            val mode = sender.arbiter.metrics.value.currentMode

            val sendBle = mode == FailoverMode.BLE_DIRECT || mode == FailoverMode.MULTI_PATH_HEDGED || mode == FailoverMode.RESTRICTED_OFFLINE
            val sendCloud = (mode == FailoverMode.MULTI_PATH_HEDGED || mode == FailoverMode.CLOUD_RELAY_ONLY) && cloudRelayAvailable

            if (sendBle) {
                totalBleDispatches++
                // In BLE transmission, broadcast to reachable nodes
                for (receiver in participants) {
                    if (receiver.nodeId == sender.nodeId) continue
                    val canReach = if (sender.isLeadGroup == receiver.isLeadGroup) {
                        true // Intra-group BLE always succeeds in this simulation
                    } else {
                        interGroupBleAvailable && (interGroupBleDeliveryRatio > 0.5)
                    }

                    if (canReach) {
                        receiver.receiveInbound(packet, virtualClockMs)
                    }
                }
            }

            if (sendCloud) {
                totalCloudDispatches++
                sender.arbiter.recordHedgedPacket()
                // In Cloud transmission, relay to all other cloud-connected nodes
                for (receiver in participants) {
                    if (receiver.nodeId == sender.nodeId) continue
                    receiver.receiveInbound(packet, virtualClockMs + 35L) // Cloud arrival has slight network latency
                }
            }
        }
    }

    @Test
    fun test16NodeConvoyHighwaySplitAndRejoinLifecycle() = runTest {
        val env = ConvoyHighwayEnvironment()

        // ================= STAGE 1: NOMINAL TIGHT CONVOY (BLE_DIRECT) =================
        env.interGroupBleAvailable = true
        env.interGroupBleEtx = 1.1
        env.interGroupBleDeliveryRatio = 1.0
        env.interGroupBleRssi = -60
        env.cloudRelayAvailable = true
        env.virtualClockMs = 1000L
        env.syncAllArbiters()

        for (node in env.participants) {
            assertEquals(FailoverMode.BLE_DIRECT, node.arbiter.metrics.value.currentMode)
            assertFalse(node.arbiter.metrics.value.isBleDegraded)
        }

        // Rider 00 (Lead) transmits 10 voice frames
        for (seq in 1L..10L) {
            env.transmitVoice(senderNodeIndex = 0, sequence = seq)
            env.virtualClockMs += 40L
        }

        // All 15 other nodes received exactly 10 frames
        for (i in 1..15) {
            assertEquals(10, env.participants[i].receivedAudioFrames.size)
            assertEquals(0, env.participants[i].duplicatesSuppressed)
        }
        // Zero cloud packets were dispatched in BLE_DIRECT mode
        assertEquals(0, env.totalCloudDispatches)
        assertEquals(10, env.totalBleDispatches)

        // ================= STAGE 2: HIGHWAY DRIFT & RF DEGRADATION (MULTI_PATH_HEDGED) =================
        // Tail squad lags behind; inter-group link degrades (high ETX, loss, weak RSSI)
        env.interGroupBleEtx = 3.4
        env.interGroupBleDeliveryRatio = 0.65
        env.interGroupBleRssi = -89
        env.virtualClockMs += 500L
        env.syncAllArbiters()

        for (node in env.participants) {
            assertEquals(FailoverMode.MULTI_PATH_HEDGED, node.arbiter.metrics.value.currentMode)
            assertTrue(node.arbiter.metrics.value.isBleDegraded)
        }

        // Rider 00 transmits 10 voice frames under degraded RF
        for (seq in 11L..20L) {
            env.transmitVoice(senderNodeIndex = 0, sequence = seq)
            env.virtualClockMs += 40L
        }

        // Due to dual-casting and receiver deduplication, 0% audio frames lost across the entire convoy
        for (i in 1..15) {
            assertEquals(20, env.participants[i].receivedAudioFrames.size)
        }
        // Tail group (nodes 8..15) successfully deduplicated the hedged arrival
        for (i in 8..15) {
            assertTrue(env.participants[i].duplicatesSuppressed > 0)
        }
        assertTrue(env.totalCloudDispatches >= 10)

        // ================= STAGE 3: OUT-OF-RANGE CONVOY SPLIT (CLOUD_RELAY_ONLY) =================
        // Tail squad is separated by > 1km beyond hills; BLE inter-group radio link completely broken
        env.interGroupBleAvailable = false
        env.interGroupBleDeliveryRatio = 0.0
        env.virtualClockMs += 5000L // Exceeds bleTimeoutMs (4,000ms)
        // Simulate Lead and Tail groups sensing peer count drop
        for (node in env.participants) {
            node.arbiter.updateCloudStatus(connected = true, currentTimeMs = env.virtualClockMs)
            node.arbiter.updateMeshMetrics(
                peerCount = 0, // Inter-group out-of-range
                avgEtx = 1.0,
                deliveryRatio = 1.0,
                minRssiDbm = -75,
                currentTimeMs = env.virtualClockMs
            )
        }

        for (node in env.participants) {
            assertEquals(FailoverMode.CLOUD_RELAY_ONLY, node.arbiter.metrics.value.currentMode)
        }

        // Rider 00 transmits 10 voice frames while completely out of BLE range
        for (seq in 21L..30L) {
            env.transmitVoice(senderNodeIndex = 0, sequence = seq)
            env.virtualClockMs += 40L
        }

        // Tail group received every packet through Cloud Relay with zero loss
        for (i in 8..15) {
            assertEquals(30, env.participants[i].receivedAudioFrames.size)
        }

        // ================= STAGE 4: ZERO-CONNECTIVITY DEAD ZONE (RESTRICTED_OFFLINE) =================
        // Convoy enters long mountain tunnel with zero cellular coverage
        env.cloudRelayAvailable = false
        env.virtualClockMs += 1000L
        for (node in env.participants) {
            node.arbiter.updateCloudStatus(connected = false, currentTimeMs = env.virtualClockMs)
            node.arbiter.updateMeshMetrics(
                peerCount = 0,
                avgEtx = 1.0,
                deliveryRatio = 1.0,
                minRssiDbm = -75,
                currentTimeMs = env.virtualClockMs
            )
        }

        for (node in env.participants) {
            assertEquals(FailoverMode.RESTRICTED_OFFLINE, node.arbiter.metrics.value.currentMode)
        }

        // ================= STAGE 5: RE-CONVERGENCE & 5,000MS HYSTERESIS DAMPENING =================
        // Convoy exits tunnel into open air; cellular restored and tail squad catches up to lead squad
        env.cloudRelayAvailable = true
        env.interGroupBleAvailable = true
        env.interGroupBleEtx = 1.1
        env.interGroupBleDeliveryRatio = 1.0
        env.interGroupBleRssi = -62
        val rejoinTimeMs = env.virtualClockMs + 2000L
        env.virtualClockMs = rejoinTimeMs

        // Initial radio contact at tunnel exit has fringe RF degradation, immediately engaging dual-cast hedging
        for (node in env.participants) {
            node.arbiter.updateCloudStatus(connected = true, currentTimeMs = env.virtualClockMs)
            node.arbiter.updateMeshMetrics(
                peerCount = 15,
                avgEtx = 3.2,
                deliveryRatio = 0.80,
                minRssiDbm = -90,
                currentTimeMs = env.virtualClockMs
            )
        }
        // Advance clock and settle RF link to nominal healthy metrics: triggers 5,000ms hysteresis dampening
        env.virtualClockMs += 200L
        for (node in env.participants) {
            node.arbiter.updateMeshMetrics(
                peerCount = 15,
                avgEtx = 1.1,
                deliveryRatio = 1.0,
                minRssiDbm = -62,
                currentTimeMs = env.virtualClockMs
            )
        }

        // 1. Initial healthy tick: Arbiter must remain in MULTI_PATH_HEDGED during hysteresis window
        for (node in env.participants) {
            assertEquals(FailoverMode.MULTI_PATH_HEDGED, node.arbiter.metrics.value.currentMode)
        }

        // 2. Advance 3,000ms: Still within 5,000ms hysteresis window
        env.virtualClockMs += 3000L
        for (node in env.participants) {
            node.arbiter.evaluateState(env.virtualClockMs)
            assertEquals(FailoverMode.MULTI_PATH_HEDGED, node.arbiter.metrics.value.currentMode)
        }

        // 3. Advance past 5,000ms (5,200ms total elapsed): Hysteresis satisfied!
        env.virtualClockMs += 2200L
        for (node in env.participants) {
            node.arbiter.evaluateState(env.virtualClockMs)
            assertEquals(FailoverMode.BLE_DIRECT, node.arbiter.metrics.value.currentMode)
        }

        // 4. Verify post-handover transmission is once again 100% BLE with zero cellular usage
        val initialCloudCount = env.totalCloudDispatches
        for (seq in 31L..40L) {
            env.transmitVoice(senderNodeIndex = 0, sequence = seq)
            env.virtualClockMs += 40L
        }
        assertEquals(initialCloudCount, env.totalCloudDispatches) // No new cloud packets
        for (i in 1..15) {
            assertEquals(40, env.participants[i].receivedAudioFrames.size)
        }
    }

    @Test
    fun testRapidRfFluctuationHysteresisDampening() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        var clockMs = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = clockMs)
        arbiter.updateMeshMetrics(peerCount = 5, avgEtx = 1.2, deliveryRatio = 0.98, minRssiDbm = -70, currentTimeMs = clockMs)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Drop to degraded RF
        clockMs += 1000L
        arbiter.updateMeshMetrics(peerCount = 5, avgEtx = 3.0, deliveryRatio = 0.70, minRssiDbm = -91, currentTimeMs = clockMs)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, arbiter.metrics.value.currentMode)

        // Fluctuate RSSI every 500ms between healthy and degraded for 4 seconds
        for (cycle in 0..7) {
            clockMs += 500L
            val isHealthyTick = (cycle % 2 == 0)
            arbiter.updateMeshMetrics(
                peerCount = 5,
                avgEtx = if (isHealthyTick) 1.2 else 3.0,
                deliveryRatio = if (isHealthyTick) 0.98 else 0.70,
                minRssiDbm = if (isHealthyTick) -70 else -91,
                currentTimeMs = clockMs
            )
            // MUST stay continuously in MULTI_PATH_HEDGED without flapping back and forth to BLE_DIRECT!
            assertEquals(FailoverMode.MULTI_PATH_HEDGED, arbiter.metrics.value.currentMode)
        }

        // Total handovers must be exactly 1 (the initial escalation) despite 8 boundary fluctuations!
        assertEquals(1L, arbiter.metrics.value.totalHandovers)
    }

    @Test
    fun testZeroAudioDropoutDuringContinuousSpeechHandover() = runTest {
        val leadArbiter = MultiPathFailoverArbiter()
        val receiverDeduplicator = PacketDeduplicator(windowMs = 5000L)
        val receivedFrames = mutableListOf<Long>()

        var clockMs = 1000L
        leadArbiter.updateCloudStatus(connected = true, currentTimeMs = clockMs)
        leadArbiter.updateMeshMetrics(peerCount = 3, avgEtx = 1.1, deliveryRatio = 1.0, minRssiDbm = -65, currentTimeMs = clockMs)

        // Continuous speech: 50 packets transmitted at 40ms intervals (2.0 seconds of talk time)
        for (frame in 1L..50L) {
            clockMs += 40L

            // At frame 25, simulate sharp RF degradation (convoy starts drifting apart)
            if (frame == 25L) {
                leadArbiter.updateMeshMetrics(peerCount = 3, avgEtx = 3.5, deliveryRatio = 0.60, minRssiDbm = -92, currentTimeMs = clockMs)
            }
            // At frame 40, complete BLE break occurs
            if (frame == 40L) {
                leadArbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, minRssiDbm = -75, currentTimeMs = clockMs)
            }

            val mode = leadArbiter.metrics.value.currentMode
            val packet = MeshPacket.Audio(
                senderId = "lead-rider",
                sequence = frame,
                timestamp = clockMs,
                data = byteArrayOf(0x01),
                profile = AudioProfile.MEDIUM
            )

            // Simulate transport paths according to mode
            when (mode) {
                FailoverMode.BLE_DIRECT -> {
                    if (receiverDeduplicator.shouldProcess(packet, now = clockMs)) {
                        receivedFrames.add(packet.sequence)
                    }
                }
                FailoverMode.MULTI_PATH_HEDGED -> {
                    // Both BLE and Cloud deliver packet
                    if (receiverDeduplicator.shouldProcess(packet, now = clockMs)) {
                        receivedFrames.add(packet.sequence)
                    }
                    // Second arrival via cloud (e.g. +30ms later)
                    if (receiverDeduplicator.shouldProcess(packet, now = clockMs + 30L)) {
                        receivedFrames.add(packet.sequence)
                    }
                }
                FailoverMode.CLOUD_RELAY_ONLY -> {
                    if (receiverDeduplicator.shouldProcess(packet, now = clockMs + 30L)) {
                        receivedFrames.add(packet.sequence)
                    }
                }
                FailoverMode.RESTRICTED_OFFLINE -> {}
            }
        }

        // Verify exactly 50 distinct frames received with ZERO dropouts and ZERO duplicates
        assertEquals(50, receivedFrames.size)
        assertEquals((1L..50L).toList(), receivedFrames)
    }
}
