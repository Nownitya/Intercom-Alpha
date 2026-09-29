package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MeshDiagnosticsTest {

    @Test
    fun testRecordPacketsInOrder() = runTest {
        val diagnostics = MeshDiagnostics(peerTimeoutMs = 5_000L)

        val t0 = 1000L
        for (seq in 1L..10L) {
            diagnostics.recordPacketReceived(
                senderId = "rider-alpha",
                sequence = seq,
                hopCount = 1,
                rssi = -65,
                arrivalTimeMs = t0 + seq * 20,
                transitLatencyMs = 15L
            )
        }

        val stats = diagnostics.getPeerStats("rider-alpha", now = t0 + 300)
        assertNotNull(stats)
        assertEquals(10L, stats.packetsReceived)
        assertEquals(10L, stats.packetsExpected)
        assertEquals(0L, stats.packetsLost)
        assertEquals(1.0, stats.pdr)
        assertEquals(-65, stats.currentRssi)
        assertEquals(1, stats.lastHopCount)
        assertEquals(10L, stats.hopDistribution[1])
        assertEquals(LinkQuality.EXCELLENT, stats.linkQuality)
        assertTrue(stats.linkMarginDb > 20.0) // -65 - (-93) = 28 dB
    }

    @Test
    fun testPacketLossDetection() = runTest {
        val diagnostics = MeshDiagnostics()

        val t0 = 1000L
        // Send packets 1, 2, then skip to 5 (missing 3 and 4)
        diagnostics.recordPacketReceived("rider-beta", 1L, 1, -75, t0)
        diagnostics.recordPacketReceived("rider-beta", 2L, 1, -75, t0 + 20)
        diagnostics.recordPacketReceived("rider-beta", 5L, 1, -75, t0 + 80)

        val stats = diagnostics.getPeerStats("rider-beta", now = t0 + 100)
        assertNotNull(stats)
        assertEquals(3L, stats.packetsReceived)
        assertEquals(2L, stats.packetsLost)
        assertEquals(5L, stats.packetsExpected)
        assertEquals(3.0 / 5.0, stats.pdr) // 60% PDR
        assertEquals(LinkQuality.CRITICAL, stats.linkQuality) // PDR < 70%
    }

    @Test
    fun testMultiHopDistribution() = runTest {
        val diagnostics = MeshDiagnostics()

        // 5 packets at 1 hop, 3 at 2 hops, 2 at 3 hops
        for (i in 1..5) diagnostics.recordPacketReceived("convoy-lead", i.toLong(), 1)
        for (i in 6..8) diagnostics.recordPacketReceived("convoy-lead", i.toLong(), 2)
        for (i in 9..10) diagnostics.recordPacketReceived("convoy-lead", i.toLong(), 3)

        val stats = diagnostics.getPeerStats("convoy-lead")
        assertNotNull(stats)
        assertEquals(5L, stats.hopDistribution[1])
        assertEquals(3L, stats.hopDistribution[2])
        assertEquals(2L, stats.hopDistribution[3])
        assertEquals(3, stats.lastHopCount)
    }

    @Test
    fun testDistanceEstimationAndLinkMargin() {
        val diagnostics = MeshDiagnostics(
            referenceRssiAtOneMeter = -59.0,
            pathLossExponent = 2.5,
            receiverSensitivityDb = -93.0
        )

        // At 1m reference RSSI (-59 dBm) distance should be 1.0m
        val d1 = diagnostics.estimateDistance(-59.0)
        assertEquals(1.0, d1)

        // At closer distance (-50 dBm) should clamp to 1.0m
        val dClose = diagnostics.estimateDistance(-50.0)
        assertEquals(1.0, dClose)

        // At -84 dBm: (-59 - (-84)) / (10 * 2.5) = 25 / 25 = 1.0 -> 10^1 = 10.0m
        val d10 = diagnostics.estimateDistance(-84.0)
        assertTrue(d10 in 9.9..10.1)

        // Link margin: -75 - (-93) = 18 dB
        val margin = diagnostics.calculateLinkMargin(-75.0)
        assertEquals(18.0, margin)
    }

    @Test
    fun testTimeoutTransitionsToLost() = runTest {
        val diagnostics = MeshDiagnostics(peerTimeoutMs = 2_000L)

        val t0 = 10_000L
        diagnostics.recordPacketReceived("rider-charlie", 1L, 1, -70, arrivalTimeMs = t0)

        // Check immediately: should be active
        val statsActive = diagnostics.getPeerStats("rider-charlie", now = t0 + 500)
        assertNotNull(statsActive)
        assertTrue(statsActive.linkQuality != LinkQuality.LOST)

        // Check after timeout (2500ms > 2000ms): should be LOST
        val statsLost = diagnostics.getPeerStats("rider-charlie", now = t0 + 2500)
        assertNotNull(statsLost)
        assertEquals(LinkQuality.LOST, statsLost.linkQuality)
    }

    @Test
    fun testReportGenerationAndMarkdownOutput() = runTest {
        val diagnostics = MeshDiagnostics()

        diagnostics.recordPacketReceived("peer-1", 1L, 1, -65, arrivalTimeMs = 1000L, transitLatencyMs = 20L)
        diagnostics.recordPacketReceived("peer-1", 2L, 1, -67, arrivalTimeMs = 1020L, transitLatencyMs = 22L)
        diagnostics.recordDuplicateFiltered("peer-1")

        diagnostics.recordPacketReceived("peer-2", 1L, 2, -85, arrivalTimeMs = 1000L, transitLatencyMs = 60L)

        val report = diagnostics.generateReport(now = 1100L)
        assertEquals(2, report.activePeerCount)
        assertEquals(3L, report.totalPacketsReceived)
        assertEquals(1L, report.totalDuplicatesFiltered)
        assertEquals(1.0, report.overallPdr)
        assertEquals(2, report.peerStats.size)

        val markdown = report.toMarkdownSummary()
        assertTrue(markdown.contains("Mesh Network Diagnostics Report"))
        assertTrue(markdown.contains("peer-1"))
        assertTrue(markdown.contains("peer-2"))
    }

    @Test
    fun testReset() = runTest {
        val diagnostics = MeshDiagnostics()
        diagnostics.recordPacketReceived("p1", 1L, 1)
        diagnostics.recordDuplicateFiltered("p1")

        val before = diagnostics.generateReport()
        assertEquals(1, before.activePeerCount)

        diagnostics.reset()
        val after = diagnostics.generateReport()
        assertEquals(0, after.activePeerCount)
        assertEquals(0L, after.totalPacketsReceived)
        assertEquals(0L, after.totalDuplicatesFiltered)
    }
}
