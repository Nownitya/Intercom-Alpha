package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshCongestionGovernorTest {

    @Test
    fun testInitialSnapshotBaseline() = runTest {
        val governor = MeshCongestionGovernor()
        val metrics = governor.metrics.value

        assertEquals(0.0f, metrics.channelUtilization)
        assertEquals(0, metrics.activeNodeCount)
        assertEquals(20L, metrics.currentAdvIntervalMs)
        assertEquals(1.0f, metrics.currentRelayProbability)
        assertFalse(metrics.isThrottled)
        assertFalse(metrics.isHighDensity)
        assertEquals(0L, metrics.totalPacketsEvaluated)
        assertEquals(0L, metrics.totalPacketsRelayed)
        assertEquals(0L, metrics.totalPacketsDampened)
        assertEquals(0L, metrics.emergencyPacketsBypassed)
    }

    @Test
    fun testAdvertisingIntervalThrottlingAbove70Percent() = runTest {
        val governor = MeshCongestionGovernor()

        // Below or at 70% channel utilization: interval remains at 20ms baseline (with node count <= 8)
        assertEquals(20L, governor.calculateAdvInterval(channelUtilization = 0.0f, activeNodeCount = 4))
        assertEquals(20L, governor.calculateAdvInterval(channelUtilization = 0.30f, activeNodeCount = 4))
        assertEquals(20L, governor.calculateAdvInterval(channelUtilization = 0.50f, activeNodeCount = 4))
        assertEquals(20L, governor.calculateAdvInterval(channelUtilization = 0.70f, activeNodeCount = 4))

        // Above 70% channel utilization: interval throttles dynamically up to 250ms
        val interval75 = governor.calculateAdvInterval(channelUtilization = 0.75f, activeNodeCount = 4)
        val interval85 = governor.calculateAdvInterval(channelUtilization = 0.85f, activeNodeCount = 4)
        val interval95 = governor.calculateAdvInterval(channelUtilization = 0.95f, activeNodeCount = 4)
        val interval100 = governor.calculateAdvInterval(channelUtilization = 1.00f, activeNodeCount = 4)

        assertTrue(interval75 > 20L, "Interval at 75% should exceed 20ms, was $interval75")
        assertTrue(interval85 > interval75, "Interval at 85% ($interval85) should exceed interval at 75% ($interval75)")
        assertTrue(interval95 > interval85, "Interval at 95% ($interval95) should exceed interval at 85% ($interval85)")
        assertEquals(250L, interval100, "Interval at 100% saturation must reach maxAdvIntervalMs (250ms)")

        // Update state and verify reactive state snapshot
        governor.updateMetrics(channelUtilization = 0.85f, activeNodeCount = 4)
        assertTrue(governor.isThrottled, "isThrottled must be true when utilization > 70%")
        assertEquals(interval85, governor.currentAdvIntervalMs)
    }

    @Test
    fun testAdvertisingIntervalScalingWithNodeDensity() = runTest {
        val governor = MeshCongestionGovernor()

        // When node count exceeds highDensityNodeThreshold (8 nodes), baseline interval scales between 20ms and 50ms
        val intervalSparse = governor.calculateAdvInterval(channelUtilization = 0.20f, activeNodeCount = 4)
        val interval16Nodes = governor.calculateAdvInterval(channelUtilization = 0.20f, activeNodeCount = 16)
        val interval24Nodes = governor.calculateAdvInterval(channelUtilization = 0.20f, activeNodeCount = 24)

        assertEquals(20L, intervalSparse)
        assertTrue(interval16Nodes in 21L..40L, "16 nodes should mildly scale baseline, was $interval16Nodes")
        assertEquals(50L, interval24Nodes, "24 nodes should reach max density base interval (50ms)")

        // Under high density AND high utilization (>70%), throttling reaches full 250ms at 100%
        val throttledHighDensity = governor.calculateAdvInterval(channelUtilization = 1.00f, activeNodeCount = 24)
        assertEquals(250L, throttledHighDensity)
    }

    @Test
    fun testProbabilisticRelayDampeningInHighDensityCluster() = runTest {
        val governor = MeshCongestionGovernor()

        // Sparse cluster (<= 8 nodes, <= 70% util): 100% relay probability
        assertEquals(1.0f, governor.calculateRelayProbability(channelUtilization = 0.40f, activeNodeCount = 4))
        assertEquals(1.0f, governor.calculateRelayProbability(channelUtilization = 0.70f, activeNodeCount = 8))

        // High density cluster (16 nodes, 40% util): factor = 8 / 16 = 0.50
        val prob16 = governor.calculateRelayProbability(channelUtilization = 0.40f, activeNodeCount = 16)
        assertEquals(0.50f, prob16)

        // High density + high utilization (24 nodes, 85% util)
        // densityFactor = 8 / 24 = 0.333, utilFactor = 1.0 - 0.5 * ((0.85 - 0.70) / 0.30) = 0.75
        // combined = 0.333 * 0.75 = 0.25
        val prob24 = governor.calculateRelayProbability(channelUtilization = 0.85f, activeNodeCount = 24)
        assertTrue(prob24 in 0.24f..0.26f, "Expected ~0.25 relay probability, got $prob24")

        // Extreme density (64 nodes, 100% util): must not drop below minRelayProbability (0.20)
        val probExtreme = governor.calculateRelayProbability(channelUtilization = 1.00f, activeNodeCount = 64)
        assertEquals(0.20f, probExtreme, "Must be clamped to minRelayProbability floor")
    }

    @Test
    fun testEmergencyPacketsBypassDampeningWithZeroLatency() = runTest {
        // Configure random supplier to always return 1.0 (which would drop any non-emergency packet when prob < 1.0)
        val governor = MeshCongestionGovernor(
            randomSupplier = { 1.0 }
        )

        // Set governor to extreme congestion (100% saturation, 32 nodes)
        governor.updateMetrics(channelUtilization = 1.00f, activeNodeCount = 32)
        assertEquals(0.20f, governor.currentRelayProbability)

        // Emergency packet 1: Crash Alert Control Packet
        val crashPacket = MeshPacket.Control(
            senderId = "rider_bob",
            type = ControlType.CRASH_ALERT,
            payload = byteArrayOf(0x01, 0x02)
        )
        val relayCrash = governor.shouldRelay(crashPacket)
        assertTrue(relayCrash, "Emergency crash alert must be relayed unconditionally")

        // Emergency packet 2: Geofence Alarm Control Packet
        val geofencePacket = MeshPacket.Control(
            senderId = "rider_charlie",
            type = ControlType.GEOFENCE_ALARM
        )
        val relayGeofence = governor.shouldRelay(geofencePacket)
        assertTrue(relayGeofence, "Emergency geofence alarm must be relayed unconditionally")

        // Emergency packet 3: Wrapped inside MeshPacket.Relay multi-hop envelope
        val wrappedCrash = MeshPacket.Relay(
            originalSenderId = "rider_bob",
            ttl = 3,
            packet = crashPacket
        )
        val relayWrapped = governor.shouldRelay(wrappedCrash)
        assertTrue(relayWrapped, "Relay-wrapped emergency packet must bypass dampening")

        // Verify metrics counters: 3 emergency bypasses, 0 dampened
        val metrics = governor.metrics.value
        assertEquals(3L, metrics.emergencyPacketsBypassed)
        assertEquals(3L, metrics.totalPacketsRelayed)
        assertEquals(0L, metrics.totalPacketsDampened)
        assertEquals(3L, metrics.totalPacketsEvaluated)
    }

    @Test
    fun testNonEmergencyPacketsRelayedAndDampenedCounters() = runTest {
        // Roll sequence: 0.1 (relayed), 0.9 (dampened), 0.2 (relayed), 0.8 (dampened)
        val rolls = mutableListOf(0.1, 0.9, 0.2, 0.8)
        val governor = MeshCongestionGovernor(
            randomSupplier = { if (rolls.isNotEmpty()) rolls.removeAt(0) else 0.5 }
        )

        // 16 nodes -> relay probability = 0.50f
        governor.updateMetrics(channelUtilization = 0.50f, activeNodeCount = 16)
        assertEquals(0.50f, governor.currentRelayProbability)

        val voicePacket = MeshPacket.Audio(
            senderId = "rider_alice",
            sequence = 100L,
            timestamp = 1000L,
            data = byteArrayOf(0x00, 0x01),
            profile = AudioProfile.MEDIUM
        )

        // 1st packet: roll 0.1 <= 0.50 -> Relayed
        val res1 = governor.shouldRelay(voicePacket)
        assertTrue(res1)

        // 2nd packet: roll 0.9 > 0.50 -> Dampened
        val res2 = governor.shouldRelay(voicePacket)
        assertFalse(res2)

        // 3rd packet: roll 0.2 <= 0.50 -> Relayed
        val res3 = governor.shouldRelay(voicePacket)
        assertTrue(res3)

        // 4th packet: roll 0.8 > 0.50 -> Dampened
        val res4 = governor.shouldRelay(voicePacket)
        assertFalse(res4)

        val metrics = governor.metrics.value
        assertEquals(4L, metrics.totalPacketsEvaluated)
        assertEquals(2L, metrics.totalPacketsRelayed)
        assertEquals(2L, metrics.totalPacketsDampened)
        assertEquals(0L, metrics.emergencyPacketsBypassed)
    }

    @Test
    fun testNeighborPresenceAndWindowPruning() = runTest {
        val config = MeshCongestionGovernorConfig(
            neighborRetentionWindowMs = 5000L
        )
        val governor = MeshCongestionGovernor(config = config)

        val t0 = 10_000L
        governor.recordNeighborPresence("node_1", rssi = -65, timestampMs = t0)
        governor.recordNeighborPresence("node_2", rssi = -70, timestampMs = t0 + 1000L)
        governor.recordNeighborPresence("node_3", rssi = -75, timestampMs = t0 + 2000L)

        assertEquals(3, governor.metrics.value.activeNodeCount)
        assertFalse(governor.isHighDensity)

        // Add 6 more nodes at t0 + 2000L to trigger high-density (total 9 nodes > 8)
        for (i in 4..9) {
            governor.recordNeighborPresence("node_$i", rssi = -60, timestampMs = t0 + 2000L)
        }
        assertEquals(9, governor.metrics.value.activeNodeCount)
        assertTrue(governor.isHighDensity)

        // Advance time to t0 + 6000L -> node_1 (seen at t0) has expired (retention window = 5000L)
        governor.recordNeighborPresence("node_2", rssi = -68, timestampMs = t0 + 6000L)
        assertEquals(8, governor.metrics.value.activeNodeCount)
        assertFalse(governor.isHighDensity)
    }

    @Test
    fun testAirtimeUsageAccumulationAndWindowPruning() = runTest {
        val config = MeshCongestionGovernorConfig(
            airtimeEvaluationWindowMs = 1000L // 1,000ms = 1,000,000 micros window
        )
        val governor = MeshCongestionGovernor(config = config)

        val t0 = 50_000L
        // Record 2 bursts of 200,000 micros (total 400,000 micros in 1,000,000 micros = 40% utilization)
        governor.recordAirtimeUsage(200_000L, timestampMs = t0)
        governor.recordAirtimeUsage(200_000L, timestampMs = t0 + 200L)

        val metrics40 = governor.metrics.value
        assertEquals(0.40f, metrics40.channelUtilization)
        assertFalse(metrics40.isThrottled)

        // Add 400,000 micros more -> total 800,000 micros in 1,000,000 micros = 80% utilization (> 70%)
        governor.recordAirtimeUsage(400_000L, timestampMs = t0 + 400L)
        val metrics80 = governor.metrics.value
        assertEquals(0.80f, metrics80.channelUtilization)
        assertTrue(metrics80.isThrottled)
        assertTrue(governor.currentAdvIntervalMs > 20L)

        // Advance time past t0 + 1000L: first two events expire, leaving only the 400,000 event at t0 + 400L
        governor.recordAirtimeUsage(0L, timestampMs = t0 + 1300L)
        val metricsAfterPrune = governor.metrics.value
        assertEquals(0.40f, metricsAfterPrune.channelUtilization)
        assertFalse(metricsAfterPrune.isThrottled)
    }

    @Test
    fun testExplicitOverridesAndClear() = runTest {
        val governor = MeshCongestionGovernor()

        governor.updateMetrics(channelUtilization = 0.90f, activeNodeCount = 20)
        val overridden = governor.metrics.value
        assertEquals(0.90f, overridden.channelUtilization)
        assertEquals(20, overridden.activeNodeCount)
        assertTrue(overridden.isThrottled)
        assertTrue(overridden.isHighDensity)

        // Clear overrides
        governor.clearExplicitOverrides()
        val cleared = governor.metrics.value
        assertEquals(0.0f, cleared.channelUtilization)
        assertEquals(0, cleared.activeNodeCount)
        assertFalse(cleared.isThrottled)
        assertFalse(cleared.isHighDensity)
    }

    @Test
    fun testResetRestoresInitialState() = runTest {
        val governor = MeshCongestionGovernor()
        governor.updateMetrics(channelUtilization = 0.80f, activeNodeCount = 15)
        governor.recordNeighborPresence("node_x", rssi = -50)
        governor.recordAirtimeUsage(500_000L)

        val dummyControl = MeshPacket.Control(
            senderId = "test",
            type = ControlType.PING
        )
        governor.shouldRelay(dummyControl)

        // Call reset
        governor.reset()

        val metrics = governor.metrics.value
        assertEquals(0.0f, metrics.channelUtilization)
        assertEquals(0, metrics.activeNodeCount)
        assertEquals(20L, metrics.currentAdvIntervalMs)
        assertEquals(1.0f, metrics.currentRelayProbability)
        assertFalse(metrics.isThrottled)
        assertEquals(0L, metrics.totalPacketsEvaluated)
        assertEquals(0L, metrics.totalPacketsRelayed)
        assertEquals(0L, metrics.totalPacketsDampened)
        assertEquals(0L, metrics.emergencyPacketsBypassed)
    }

    @Test
    fun testFastNonSuspendingRelayCheck() {
        val governor = MeshCongestionGovernor(randomSupplier = { 0.3 })

        // Emergency packet fast check
        val emergency = MeshPacket.Control(
            senderId = "sos_node",
            type = ControlType.CRASH_ALERT
        )
        assertTrue(governor.shouldRelayFast(emergency))

        // Normal packet fast check
        val normal = MeshPacket.Control(
            senderId = "ping_node",
            type = ControlType.PING
        )
        assertTrue(governor.shouldRelayFast(normal))
    }

    @Test
    fun testCustomEmergencyPredicate() = runTest {
        // Custom predicate recognizes ROUTE_ERROR as emergency
        val governor = MeshCongestionGovernor(
            customEmergencyPredicate = { packet ->
                packet is MeshPacket.Control && packet.type == ControlType.ROUTE_ERROR
            },
            randomSupplier = { 1.0 } // Would drop non-emergency
        )

        governor.updateMetrics(channelUtilization = 1.0f, activeNodeCount = 30)

        val routeErrorPacket = MeshPacket.Control(
            senderId = "node_a",
            type = ControlType.ROUTE_ERROR
        )
        assertTrue(governor.isEmergencyPacket(routeErrorPacket))
        assertTrue(governor.shouldRelay(routeErrorPacket))
    }
}

