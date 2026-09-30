package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProximityRadarTest {

    @Test
    fun testInitialRadarSnapshot() {
        val radar = ProximityRadar()
        val snapshot = radar.radarFlow.value
        assertEquals(0, snapshot.targets.size)
        assertNull(snapshot.closestTarget)
        assertEquals(0, snapshot.activeRidersCount)
    }

    @Test
    fun testZoneClassification() = runTest {
        val radar = ProximityRadar()

        // 1. Close proximity / pack riding (RSSI >= -60 dBm -> distance < 15m)
        radar.updatePeerRssi("rider-near", rssi = -55, timestamp = 1000L)
        // 2. Medium distance / visible convoy (RSSI -75 dBm)
        radar.updatePeerRssi("rider-medium", rssi = -75, timestamp = 1000L)
        // 3. Extended distance (RSSI -87 dBm)
        radar.updatePeerRssi("rider-far", rssi = -87, timestamp = 1000L)
        // 4. Out of range / border (RSSI -94 dBm)
        radar.updatePeerRssi("rider-out", rssi = -94, timestamp = 1000L)

        val snapshot = radar.radarFlow.value
        assertEquals(4, snapshot.targets.size)

        val nearTarget = snapshot.targets.first { it.peerId == "rider-near" }
        assertEquals(ProximityZone.NEAR, nearTarget.zone)
        assertTrue(nearTarget.estimatedDistanceMeters < 15.0)
        assertTrue(nearTarget.signalStrengthPercent > 80)

        val medTarget = snapshot.targets.first { it.peerId == "rider-medium" }
        assertEquals(ProximityZone.MEDIUM, medTarget.zone)
        assertTrue(medTarget.estimatedDistanceMeters in 15.0..60.0)

        val farTarget = snapshot.targets.first { it.peerId == "rider-far" }
        assertEquals(ProximityZone.FAR, farTarget.zone)
        assertTrue(farTarget.estimatedDistanceMeters in 60.0..150.0)

        val outTarget = snapshot.targets.first { it.peerId == "rider-out" }
        assertEquals(ProximityZone.OUT_OF_RANGE, outTarget.zone)
        assertTrue(outTarget.estimatedDistanceMeters >= 150.0)

        assertEquals(3, snapshot.activeRidersCount)
        assertEquals("rider-near", snapshot.closestTarget?.peerId)
    }

    @Test
    fun testStaleTimeoutPruning() = runTest {
        val radar = ProximityRadar(staleTimeoutMs = 5000L)
        radar.updatePeerRssi("rider-1", rssi = -58, timestamp = 1000L)

        // At 3000ms (not timed out yet)
        radar.pruneStalePeers(now = 3000L)
        assertEquals(ProximityZone.NEAR, radar.radarFlow.value.targets.first().zone)
        assertEquals(1, radar.radarFlow.value.activeRidersCount)

        // At 7000ms (6000ms elapsed > 5000ms timeout)
        radar.pruneStalePeers(now = 7000L)
        assertEquals(ProximityZone.OUT_OF_RANGE, radar.radarFlow.value.targets.first().zone)
        assertEquals(0, radar.radarFlow.value.activeRidersCount)
    }

    @Test
    fun testDirectionalTrendDetection() = runTest {
        val radar = ProximityRadar()

        // Initial measurement
        radar.updatePeerRssi("rider-charlie", rssi = -80, timestamp = 1000L)
        assertFalse(radar.radarFlow.value.targets.first().isApproaching)

        // Approaching: signal strengthens consecutively
        radar.updatePeerRssi("rider-charlie", rssi = -72, timestamp = 1500L)
        radar.updatePeerRssi("rider-charlie", rssi = -65, timestamp = 2000L)
        assertTrue(radar.radarFlow.value.targets.first().isApproaching)

        // Receding: signal weakens consecutively
        radar.updatePeerRssi("rider-charlie", rssi = -78, timestamp = 2500L)
        radar.updatePeerRssi("rider-charlie", rssi = -88, timestamp = 3000L)
        assertFalse(radar.radarFlow.value.targets.first().isApproaching)
    }

    @Test
    fun testUpdateFromDiagnosticsReport() = runTest {
        val radar = ProximityRadar()
        val peerStats = listOf(
            PeerStats(
                peerId = "peer-alpha",
                packetsReceived = 100L,
                packetsExpected = 100L,
                packetsLost = 0L,
                duplicatesFiltered = 2L,
                currentRssi = -60,
                minRssi = -65,
                maxRssi = -58,
                smoothedRssi = -60.0,
                pdr = 1.0,
                lastHopCount = 1,
                hopDistribution = mapOf(1 to 100L),
                minLatencyMs = 20L,
                maxLatencyMs = 35L,
                avgLatencyMs = 25.0,
                jitterMs = 2.0,
                estimatedDistanceMeters = 2.0,
                linkMarginDb = 33.0,
                linkQuality = LinkQuality.EXCELLENT,
                lastSeenTimestamp = 5000L
            ),
            PeerStats(
                peerId = "peer-bravo",
                packetsReceived = 90L,
                packetsExpected = 100L,
                packetsLost = 10L,
                duplicatesFiltered = 0L,
                currentRssi = -85,
                minRssi = -88,
                maxRssi = -80,
                smoothedRssi = -85.0,
                pdr = 0.9,
                lastHopCount = 2,
                hopDistribution = mapOf(2 to 90L),
                minLatencyMs = 45L,
                maxLatencyMs = 70L,
                avgLatencyMs = 52.0,
                jitterMs = 5.0,
                estimatedDistanceMeters = 85.0,
                linkMarginDb = 8.0,
                linkQuality = LinkQuality.GOOD,
                lastSeenTimestamp = 5000L
            )
        )

        val report = MeshDiagnosticsReport(
            timestamp = 5000L,
            activePeerCount = 2,
            totalPacketsReceived = 190L,
            totalPacketsLost = 10L,
            totalDuplicatesFiltered = 2L,
            overallPdr = 0.95,
            avgHopCount = 1.47,
            avgLinkMarginDb = 20.5,
            peerStats = peerStats
        )

        val displayNames = mapOf(
            "peer-alpha" to "Lead Rider (Dave)",
            "peer-bravo" to "Sweep Rider (Bob)"
        )

        radar.updateFromDiagnostics(report, displayNames, timestamp = 5000L)

        val snapshot = radar.radarFlow.value
        assertEquals(2, snapshot.targets.size)
        assertEquals("Lead Rider (Dave)", snapshot.targets[0].displayName)
        assertEquals(ProximityZone.NEAR, snapshot.targets[0].zone)
        assertEquals(1, snapshot.targets[0].hopCount)

        assertEquals("Sweep Rider (Bob)", snapshot.targets[1].displayName)
        assertEquals(ProximityZone.FAR, snapshot.targets[1].zone)
        assertEquals(2, snapshot.targets[1].hopCount)

        assertEquals("peer-alpha", snapshot.closestTarget?.peerId)
    }

    @Test
    fun testRemoveAndClear() = runTest {
        val radar = ProximityRadar()
        radar.updatePeerRssi("rider-1", rssi = -60)
        radar.updatePeerRssi("rider-2", rssi = -70)
        assertEquals(2, radar.radarFlow.value.targets.size)

        radar.removePeer("rider-1")
        assertEquals(1, radar.radarFlow.value.targets.size)
        assertEquals("rider-2", radar.radarFlow.value.targets.first().peerId)

        radar.clear()
        assertEquals(0, radar.radarFlow.value.targets.size)
        assertNull(radar.radarFlow.value.closestTarget)
    }
}
