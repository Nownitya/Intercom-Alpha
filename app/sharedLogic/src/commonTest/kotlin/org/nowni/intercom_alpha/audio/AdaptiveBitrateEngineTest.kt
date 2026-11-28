package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ClusterLinkHealth
import org.nowni.intercom_alpha.mesh.LinkQualityGrade
import org.nowni.intercom_alpha.mesh.PeerLinkScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveBitrateEngineTest {

    @Test
    fun testInitialDefaultProfile() = runTest {
        val engine = AdaptiveBitrateEngine(initialProfile = AudioProfile.MEDIUM)
        assertEquals(AudioProfile.MEDIUM, engine.currentProfile.value)
        assertEquals(32, engine.effectiveBitrateKbps.value)
        assertEquals(20, engine.effectiveFrameDurationMs.value)
        assertFalse(engine.telemetry.value.isPinned)
    }

    @Test
    fun testRapidStepDownOnDegradation() = runTest {
        val engine = AdaptiveBitrateEngine(initialProfile = AudioProfile.HIGH)
        val now = 1000L

        // Severe loss (Q_link = 25) drops immediately to ULTRA_LOW on 1 cycle
        val profile = engine.evaluateScore(25, timestampMs = now)

        assertEquals(AudioProfile.ULTRA_LOW, profile)
        assertEquals(AudioProfile.ULTRA_LOW, engine.currentProfile.value)
        assertEquals(16, engine.effectiveBitrateKbps.value)
        assertEquals(60, engine.effectiveFrameDurationMs.value)
        assertTrue(engine.telemetry.value.lastTransitionReason.contains("Rapid step-down"))
    }

    @Test
    fun testConservativeStepUpRequiresHysteresisCycles() = runTest {
        val engine = AdaptiveBitrateEngine(initialProfile = AudioProfile.LOW, hysteresisUpgradeCycles = 3)
        var now = 1000L

        // Cycle 1: Pristine condition (Q_link = 95). Should damp, NOT upgrade yet.
        var profile = engine.evaluateScore(95, timestampMs = now)
        assertEquals(AudioProfile.LOW, profile)
        assertEquals(1, engine.telemetry.value.consecutivePristineCycles)
        assertEquals(AudioProfile.HIGH, engine.telemetry.value.candidateProfile)

        // Cycle 2: Pristine condition again (Q_link = 92). Still damped.
        now += 1000L
        profile = engine.evaluateScore(92, timestampMs = now)
        assertEquals(AudioProfile.LOW, profile)
        assertEquals(2, engine.telemetry.value.consecutivePristineCycles)

        // Cycle 3: Pristine condition 3rd time (Q_link = 90). Now upgrades to HIGH!
        now += 1000L
        profile = engine.evaluateScore(90, timestampMs = now)
        assertEquals(AudioProfile.HIGH, profile)
        assertEquals(AudioProfile.HIGH, engine.currentProfile.value)
        assertEquals(48, engine.effectiveBitrateKbps.value)
        assertEquals(20, engine.effectiveFrameDurationMs.value)
        assertTrue(engine.telemetry.value.lastTransitionReason.contains("Conservative step-up"))
    }

    @Test
    fun testIntermittentDropResetsHysteresisCount() = runTest {
        val engine = AdaptiveBitrateEngine(initialProfile = AudioProfile.LOW, hysteresisUpgradeCycles = 3)
        var now = 1000L

        // Cycle 1: candidate HIGH
        engine.evaluateScore(90, timestampMs = now)
        assertEquals(1, engine.telemetry.value.consecutivePristineCycles)

        // Cycle 2: candidate HIGH
        now += 1000L
        engine.evaluateScore(90, timestampMs = now)
        assertEquals(2, engine.telemetry.value.consecutivePristineCycles)

        // Cycle 3: sudden drop back to LOW (Q_link = 55)
        now += 1000L
        val profile = engine.evaluateScore(55, timestampMs = now)
        assertEquals(AudioProfile.LOW, profile)
        assertEquals(0, engine.telemetry.value.consecutivePristineCycles)
    }

    @Test
    fun testManualOverridePinning() = runTest {
        val engine = AdaptiveBitrateEngine(initialProfile = AudioProfile.MEDIUM)
        val now = 1000L

        // Pin to ULTRA_LOW
        engine.pinProfile(AudioProfile.ULTRA_LOW, timestampMs = now)
        assertTrue(engine.telemetry.value.isPinned)
        assertEquals(AudioProfile.ULTRA_LOW, engine.currentProfile.value)

        // Input pristine link (Q_link = 99); should be ignored because pinned
        val profile = engine.evaluateScore(99, timestampMs = now + 1000L)
        assertEquals(AudioProfile.ULTRA_LOW, profile)
        assertEquals(AudioProfile.ULTRA_LOW, engine.currentProfile.value)

        // Release pin; now evaluates dynamically
        engine.releasePin(timestampMs = now + 2000L)
        assertFalse(engine.telemetry.value.isPinned)
    }

    @Test
    fun testClusterPolicyWorstLinkVsAverage() = runTest {
        val engine = AdaptiveBitrateEngine(
            initialProfile = AudioProfile.HIGH,
            defaultClusterPolicy = ClusterPolicy.WORST_LINK
        )
        val now = 1000L

        // Cluster has 1 strong peer (95) and 1 weak peer (35)
        val clusterHealth = ClusterLinkHealth(
            peerCount = 2,
            averageScore = 65,
            worstScore = 35,
            bestScore = 95,
            overallGrade = LinkQualityGrade.FAIR,
            peerScores = listOf(
                createMockPeerScore("peer_1", 95),
                createMockPeerScore("peer_2", 35)
            )
        )

        // In WORST_LINK mode, adapts to worst (35 -> ULTRA_LOW)
        val profileWorst = engine.evaluateClusterHealth(clusterHealth, timestampMs = now)
        assertEquals(AudioProfile.ULTRA_LOW, profileWorst)

        // Switch to CLUSTER_AVERAGE (65 -> LOW)
        engine.setClusterPolicy(ClusterPolicy.CLUSTER_AVERAGE)
        // Stepping down to LOW from initial would be immediate, or stepping up from ULTRA_LOW takes cycles.
        // Let's create an engine starting at HIGH with CLUSTER_AVERAGE:
        val engineAvg = AdaptiveBitrateEngine(
            initialProfile = AudioProfile.HIGH,
            defaultClusterPolicy = ClusterPolicy.CLUSTER_AVERAGE
        )
        val profileAvg = engineAvg.evaluateClusterHealth(clusterHealth, timestampMs = now)
        assertEquals(AudioProfile.LOW, profileAvg)
    }

    private fun createMockPeerScore(peerId: String, score: Int) = PeerLinkScore(
        peerId = peerId,
        score = score,
        grade = LinkQualityGrade.fromScore(score),
        smoothedRssiDbm = -70.0,
        linkMarginDb = 23.0,
        pdr = score / 100.0,
        jitterMs = 10.0,
        etx = 1.0,
        packetsReceived = 100L,
        packetsLost = 0L,
        lastSeenTimestampMs = 1000L
    )
}
