package org.nowni.intercom_alpha.mesh.phy

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptivePhyArbiterTest {

    @Test
    fun testInitialStateAndPeerRegistration() = runTest {
        val arbiter = AdaptivePhyArbiter(localNodeId = "node-alpha")
        val peerId = "peer-bravo"

        val cap = PhyCapability(
            peerId = peerId,
            supportedModes = setOf(BlePhyMode.LE_1M, BlePhyMode.CODED_S2, BlePhyMode.CODED_S8)
        )
        arbiter.registerPeerCapability(cap)

        assertEquals(BlePhyMode.LE_1M, arbiter.peerPhyMap.value[peerId])
    }

    @Test
    fun testRangeStepUpToCodedS2AndS8() = runTest {
        val arbiter = AdaptivePhyArbiter(
            localNodeId = "node-alpha",
            cooldownPeriodMs = 1000L
        )
        val peerId = "peer-bravo"
        arbiter.registerPeerCapability(PhyCapability(peerId = peerId))

        var timestamp = 1000L

        // First weak sample -> not enough samples yet
        val sample1 = PhyLinkMetrics(
            peerId = peerId,
            currentPhy = BlePhyMode.LE_1M,
            rssi = -85,
            packetLossRatio = 0.15,
            timestampMs = timestamp
        )
        val res1 = arbiter.evaluateLinkSample(sample1)
        assertNull(res1)

        // Second weak sample -> triggers step-up to CODED_S2
        timestamp += 100L
        val sample2 = sample1.copy(timestampMs = timestamp)
        val res2 = arbiter.evaluateLinkSample(sample2)
        assertEquals(BlePhyMode.CODED_S2, res2)
        assertEquals(BlePhyMode.CODED_S2, arbiter.peerPhyMap.value[peerId])

        // Fast forward past cooldown
        timestamp += 1500L

        // Now signal degrades into extreme alpine range (-96 dBm)
        val sample3 = PhyLinkMetrics(
            peerId = peerId,
            currentPhy = BlePhyMode.CODED_S2,
            rssi = -94,
            packetLossRatio = 0.22,
            timestampMs = timestamp
        )
        val res3 = arbiter.evaluateLinkSample(sample3)
        assertNull(res3)

        // Second sample triggers CODED_S8
        timestamp += 100L
        val sample4 = sample3.copy(timestampMs = timestamp)
        val res4 = arbiter.evaluateLinkSample(sample4)
        assertEquals(BlePhyMode.CODED_S8, res4)
        assertEquals(BlePhyMode.CODED_S8, arbiter.peerPhyMap.value[peerId])
    }

    @Test
    fun testAlpineTerrainShadowingDiffractionPenalty() = runTest {
        val arbiter = AdaptivePhyArbiter(
            localNodeId = "node-alpha",
            initialTerrainProfile = TerrainShadowingProfile.MOUNTAIN_SWITCHBACK, // 10 dB penalty
            cooldownPeriodMs = 500L
        )
        val peerId = "peer-switchback"
        arbiter.registerPeerCapability(PhyCapability(peerId = peerId))

        // Raw RSSI is -75 dBm (which on open ridge would stay LE_1M),
        // but with 10 dB switchback diffraction penalty, effective RSSI becomes -85 dBm!
        var timestamp = 1000L
        val sample1 = PhyLinkMetrics(
            peerId = peerId,
            currentPhy = BlePhyMode.LE_1M,
            rssi = -75,
            packetLossRatio = 0.05,
            timestampMs = timestamp
        )
        arbiter.evaluateLinkSample(sample1)

        timestamp += 100L
        val sample2 = sample1.copy(timestampMs = timestamp)
        val res = arbiter.evaluateLinkSample(sample2)

        // Stepped up to CODED_S2 due to terrain anticipation!
        assertEquals(BlePhyMode.CODED_S2, res)
    }

    @Test
    fun testHysteresisDampeningPreventsPingPong() = runTest {
        val arbiter = AdaptivePhyArbiter(
            localNodeId = "node-alpha",
            initialTerrainProfile = TerrainShadowingProfile.OPEN_ALPINE_RIDGE,
            hysteresisMarginDb = 6,
            minSamplesBeforeSwitch = 3,
            cooldownPeriodMs = 1000L
        )
        val peerId = "peer-charlie"
        arbiter.registerPeerCapability(PhyCapability(peerId = peerId))

        var timestamp = 1000L

        // Step down to CODED_S8 on weak signal
        arbiter.evaluateLinkSample(PhyLinkMetrics(peerId, BlePhyMode.LE_1M, rssi = -95, packetLossRatio = 0.30, timestampMs = timestamp))
        assertEquals(BlePhyMode.CODED_S8, arbiter.peerPhyMap.value[peerId])

        // Advance past cooldown
        timestamp += 2000L

        // Signal improves to -88 dBm (above -92, but below -92 + 6 = -86 hysteresis margin!)
        val weakImprovement = PhyLinkMetrics(peerId, BlePhyMode.CODED_S8, rssi = -88, packetLossRatio = 0.02, timestampMs = timestamp)
        val r1 = arbiter.evaluateLinkSample(weakImprovement)
        assertNull(r1) // Must NOT switch due to hysteresis margin

        // Now signal reaches -82 dBm (well above -86 dBm margin)
        // Needs 3 consecutive samples
        timestamp += 100L
        val strongSample = PhyLinkMetrics(peerId, BlePhyMode.CODED_S8, rssi = -82, packetLossRatio = 0.01, timestampMs = timestamp)
        assertNull(arbiter.evaluateLinkSample(strongSample)) // Sample 1

        timestamp += 100L
        assertNull(arbiter.evaluateLinkSample(strongSample.copy(timestampMs = timestamp))) // Sample 2

        timestamp += 100L
        val res = arbiter.evaluateLinkSample(strongSample.copy(timestampMs = timestamp)) // Sample 3
        assertEquals(BlePhyMode.CODED_S2, res)
        assertEquals(BlePhyMode.CODED_S2, arbiter.peerPhyMap.value[peerId])
    }

    @Test
    fun testEmergencyStepUpOnCriticalLoss() = runTest {
        val arbiter = AdaptivePhyArbiter(localNodeId = "node-alpha")
        val peerId = "peer-delta"
        arbiter.registerPeerCapability(PhyCapability(peerId = peerId))

        // Sudden canyon dead zone: loss spikes to 35%
        val emergencySample = PhyLinkMetrics(
            peerId = peerId,
            currentPhy = BlePhyMode.LE_1M,
            rssi = -80,
            packetLossRatio = 0.35,
            timestampMs = 1000L
        )

        // Instant fast-track transition on sample 1
        val res = arbiter.evaluateLinkSample(emergencySample)
        assertEquals(BlePhyMode.CODED_S8, res)
        assertEquals(BlePhyMode.CODED_S8, arbiter.peerPhyMap.value[peerId])
    }

    @Test
    fun testPeerNegotiationPacketExchange() = runTest {
        val arbiterA = AdaptivePhyArbiter(localNodeId = "node-alpha")
        val arbiterB = AdaptivePhyArbiter(localNodeId = "node-bravo")

        val capB = PhyCapability(peerId = "node-bravo", supportedModes = setOf(BlePhyMode.LE_1M, BlePhyMode.CODED_S8))
        arbiterA.registerPeerCapability(capB)
        arbiterB.registerPeerCapability(PhyCapability(peerId = "node-alpha"))

        // Node A creates switch request to CODED_S8
        val req = arbiterA.createNegotiationRequest("node-bravo", BlePhyMode.CODED_S8, timestampMs = 5000L)
        assertFalse(req.isAcknowledgement)
        assertEquals(BlePhyMode.CODED_S8, req.requestedPhy)

        // Node B processes request and generates ACK
        val ack = arbiterB.handleNegotiationPacket(req)
        assertTrue(ack.isAcknowledgement)
        assertTrue(ack.accepted)
        assertEquals(BlePhyMode.CODED_S8, arbiterB.peerPhyMap.value["node-alpha"])

        // Node A processes ACK
        val finalAck = arbiterA.handleNegotiationPacket(ack)
        assertEquals(BlePhyMode.CODED_S8, arbiterA.peerPhyMap.value["node-bravo"])
    }

    @Test
    fun testRejectUnsupportedPhyMode() = runTest {
        val arbiter = AdaptivePhyArbiter(localNodeId = "node-alpha")
        // Remote peer only supports LE_1M (e.g. legacy BLE 4.2 chipset)
        arbiter.registerPeerCapability(
            PhyCapability(
                peerId = "legacy-peer",
                supportedModes = setOf(BlePhyMode.LE_1M)
            )
        )

        val req = PhyNegotiationPacket(
            sourceId = "legacy-peer",
            targetId = "node-alpha",
            requestedPhy = BlePhyMode.CODED_S8,
            sequenceNumber = 1L,
            timestampMs = 1000L
        )

        val ack = arbiter.handleNegotiationPacket(req)
        assertFalse(ack.accepted)
        assertEquals(BlePhyMode.LE_1M, arbiter.peerPhyMap.value["legacy-peer"])
    }
}
