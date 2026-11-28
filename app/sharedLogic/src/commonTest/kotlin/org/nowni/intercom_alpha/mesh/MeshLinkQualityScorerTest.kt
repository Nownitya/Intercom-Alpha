package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshLinkQualityScorerTest {

    @Test
    fun testInitialClusterHealthIsEmpty() = runTest {
        val scorer = MeshLinkQualityScorer()
        val health = scorer.clusterHealth.value
        assertEquals(0, health.peerCount)
        assertEquals(100, health.averageScore)
        assertEquals(LinkQualityGrade.EXCELLENT, health.overallGrade)
        assertTrue(health.peerScores.isEmpty())
    }

    @Test
    fun testPristineDirectLinkProducesExcellentScore() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 1000L

        // Record 10 sequential packets with strong signal (-60 dBm) and low jitter (5ms)
        var lastScore: PeerLinkScore? = null
        for (seq in 1L..10L) {
            lastScore = scorer.recordPacket(
                peerId = "peer_alice",
                sequenceNumber = seq,
                rawRssiDbm = -60,
                transitTimeMs = 10L,
                hopCount = 1,
                timestampMs = now + (seq * 20L)
            )
        }

        assertNotNull(lastScore)
        assertEquals("peer_alice", lastScore.peerId)
        assertTrue(lastScore.score >= 85, "Expected EXCELLENT score >= 85, got ${lastScore.score}")
        assertEquals(LinkQualityGrade.EXCELLENT, lastScore.grade)
        assertEquals(1.0, lastScore.pdr)
        assertEquals(0L, lastScore.packetsLost)
        assertEquals(10L, lastScore.packetsReceived)
    }

    @Test
    fun testPacketLossDegradesPdrAndScore() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 1000L

        // Receive seq 1
        scorer.recordPacket("peer_bob", sequenceNumber = 1L, rawRssiDbm = -70, timestampMs = now)

        // Gap: next received is seq 10 (8 packets lost)
        val score = scorer.recordPacket("peer_bob", sequenceNumber = 10L, rawRssiDbm = -70, timestampMs = now + 200L)

        assertEquals(8L, score.packetsLost)
        assertEquals(2L, score.packetsReceived)
        assertEquals(0.2, score.pdr) // 2 / 10 = 0.2
        assertTrue(score.score < 60, "Expected degraded score with 80% loss, got ${score.score}")
        assertTrue(score.grade == LinkQualityGrade.POOR || score.grade == LinkQualityGrade.CRITICAL)
    }

    @Test
    fun testFadingRssiAttenuatesMarginFactor() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 1000L

        // Continuous weak RSSI (-92 dBm near sensitivity floor of -93 dBm)
        var lastScore: PeerLinkScore? = null
        for (seq in 1L..10L) {
            lastScore = scorer.recordPacket(
                peerId = "peer_charlie",
                sequenceNumber = seq,
                rawRssiDbm = -92,
                transitTimeMs = 12L,
                timestampMs = now + (seq * 20L)
            )
        }

        assertNotNull(lastScore)
        assertTrue(lastScore.linkMarginDb in 0.0..2.0)
        assertTrue(lastScore.score < 75, "Expected lower score due to weak link margin, got ${lastScore.score}")
    }

    @Test
    fun testJitterVarianceDegradation() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 1000L

        // Rapid jitter fluctuations (10ms -> 120ms -> 10ms -> 130ms)
        var lastScore: PeerLinkScore? = null
        for (seq in 1L..15L) {
            val transit = if (seq % 2 == 0L) 120L else 10L
            lastScore = scorer.recordPacket(
                peerId = "peer_jittery",
                sequenceNumber = seq,
                rawRssiDbm = -70,
                transitTimeMs = transit,
                timestampMs = now + (seq * 20L)
            )
        }

        assertNotNull(lastScore)
        assertTrue(lastScore.jitterMs > 25.0, "Expected elevated jitter, got ${lastScore.jitterMs}")
    }

    @Test
    fun testClusterAggregationComputesMinAvgMax() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 5000L

        // Peer 1: Pristine (-60 dBm, 1-hop)
        scorer.recordPacket("peer_strong", sequenceNumber = 1L, rawRssiDbm = -60, timestampMs = now)

        // Peer 2: Weak (-90 dBm, 3-hop)
        scorer.recordPacket("peer_weak", sequenceNumber = 1L, rawRssiDbm = -90, hopCount = 3, timestampMs = now)

        val health = scorer.clusterHealth.value
        assertEquals(2, health.peerCount)
        assertTrue(health.bestScore >= health.averageScore)
        assertTrue(health.averageScore >= health.worstScore)
    }

    @Test
    fun testStalePeerPruningRemovesInactivePeers() = runTest {
        val scorer = MeshLinkQualityScorer(peerTimeoutMs = 5000L)

        // Alice seen at t=1000
        scorer.recordPacket("peer_alice", sequenceNumber = 1L, rawRssiDbm = -65, timestampMs = 1000L)
        // Bob seen at t=5500
        scorer.recordPacket("peer_bob", sequenceNumber = 1L, rawRssiDbm = -70, timestampMs = 5500L)

        assertEquals(2, scorer.clusterHealth.value.peerCount)

        // At t=6500, Alice has timed out (6500 - 1000 = 5500 > 5000)
        scorer.pruneStalePeers(currentTimeMs = 6500L)

        assertEquals(1, scorer.clusterHealth.value.peerCount)
        assertEquals("peer_bob", scorer.clusterHealth.value.peerScores.first().peerId)
        assertNull(scorer.getPeerScore("peer_alice"))
        assertNotNull(scorer.getPeerScore("peer_bob"))
    }

    @Test
    fun testRecordLossAndReset() = runTest {
        val scorer = MeshLinkQualityScorer()
        val now = 1000L

        val score = scorer.recordLoss("peer_dropped", lostCount = 5L, timestampMs = now)
        assertEquals(5L, score.packetsLost)
        assertEquals(0L, score.packetsReceived)
        assertEquals(0.0, score.pdr)

        scorer.reset()
        assertEquals(0, scorer.clusterHealth.value.peerCount)
        assertNull(scorer.getPeerScore("peer_dropped"))
    }
}
