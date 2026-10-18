package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.audio.AdaptiveBitrateEngine
import org.nowni.intercom_alpha.audio.AudioPipelineSession
import org.nowni.intercom_alpha.audio.ClusterPolicy
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.LinkQualityGrade
import org.nowni.intercom_alpha.mesh.MeshLinkQualityScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 8-Node Convoy Dynamic Adaptive Bitrate & RF Hardening Simulation (Sprint 33 - ICA-3304).
 *
 * Simulates real-world dynamic RF channel degradation, fading canyon traversal,
 * rapid step-down protection (<200ms), conservative hysteresis recovery,
 * multi-peer link aggregation, and audio pipeline profile switching across 8 convoy riders.
 */
class AdaptiveBitrateSimulationTest {

    class SimulatedRider(
        val riderId: String,
        val isLead: Boolean
    ) {
        val scorer = MeshLinkQualityScorer()
        val engine = AdaptiveBitrateEngine(
            initialProfile = AudioProfile.HIGH,
            hysteresisUpgradeCycles = 3,
            defaultClusterPolicy = ClusterPolicy.WORST_LINK
        )
        val pipeline = AudioPipelineSession(profile = AudioProfile.HIGH)

        val transmittedPackets = mutableListOf<ByteArray>()
        var totalAudibleDecodes: Int = 0

        fun transmitSpeech(rawPcm: ShortArray): ByteArray? {
            val targetProfile = engine.currentProfile.value
            pipeline.updateActiveProfile(targetProfile)
            val packet = pipeline.processOutgoingMicrophone(rawPcm, targetProfile)
            if (packet != null) {
                transmittedPackets.add(packet)
            }
            return packet
        }

        fun decodeIncoming(packet: ByteArray): Boolean {
            if (packet.isEmpty()) return false
            // Decodes through audio pipeline codec
            val decoded = pipeline.codec.decode(packet)
            if (decoded.isNotEmpty()) {
                totalAudibleDecodes++
                return true
            }
            return false
        }
    }

    @Test
    fun test8NodeConvoyBitrateAdaptationLifecycle() = runTest {
        // Setup 8-node convoy: Lead (node 0) + 7 followers (nodes 1..7)
        val riders = List(8) { id -> SimulatedRider("rider_$id", isLead = (id == 0)) }
        val leadRider = riders[0]

        val syntheticVoicePcm = ShortArray(480) { (it % 100 * 100).toShort() } // 20ms at 24kHz
        var timestamp = 10_000L

        val nextSeq = mutableMapOf<String, Long>()
        for (i in 1..7) nextSeq["rider_$i"] = 1L

        // =========================================================================
        // STAGE 1: Pristine Open Highway (Full Bitrate 48 kbps)
        // =========================================================================
        for (i in 1..7) {
            for (seq in 1L..10L) {
                leadRider.scorer.recordPacket(
                    peerId = "rider_$i",
                    sequenceNumber = seq,
                    rawRssiDbm = -60,
                    transitTimeMs = 8L,
                    hopCount = 1,
                    timestampMs = timestamp + (seq * 20L)
                )
            }
            nextSeq["rider_$i"] = 11L
        }
        timestamp += 200L

        val stage1Health = leadRider.scorer.clusterHealth.value
        assertEquals(7, stage1Health.peerCount)
        assertTrue(stage1Health.worstScore >= 85, "Expected pristine scores >= 85, got ${stage1Health.worstScore}")
        assertEquals(LinkQualityGrade.EXCELLENT, stage1Health.overallGrade)

        val stage1Profile = leadRider.engine.evaluateClusterHealth(stage1Health, timestamp)
        assertEquals(AudioProfile.HIGH, stage1Profile)
        assertEquals(48, leadRider.engine.effectiveBitrateKbps.value)

        // Transmit speech at HIGH profile
        val stage1Packet = leadRider.transmitSpeech(syntheticVoicePcm)
        assertTrue(stage1Packet != null && stage1Packet.isNotEmpty())

        // Verify all 7 followers can decode the packet cleanly
        for (follower in riders.subList(1, 8)) {
            val decoded = follower.decodeIncoming(stage1Packet)
            assertTrue(decoded, "Follower ${follower.riderId} should decode pristine packet")
        }

        // =========================================================================
        // STAGE 2: Deep Canyon Fading (Immediate Rapid Step-Down to ULTRA_LOW)
        // =========================================================================
        // Severe RF degradation across all riders: jump sequence by 15 (loss) + weak RSSI -92 dBm
        for (i in 1..7) {
            val current = nextSeq["rider_$i"] ?: 11L
            val canyonSeq = current + 15L // 15 lost packets detected by gap
            leadRider.scorer.recordPacket(
                peerId = "rider_$i",
                sequenceNumber = canyonSeq,
                rawRssiDbm = -92,
                transitTimeMs = 80L,
                hopCount = 3,
                timestampMs = timestamp
            )
            nextSeq["rider_$i"] = canyonSeq + 1L
        }
        timestamp += 500L

        val stage2Health = leadRider.scorer.clusterHealth.value
        assertTrue(stage2Health.worstScore < 50, "Expected degraded score < 50 (ULTRA_LOW bracket), got ${stage2Health.worstScore}")

        // Engine must downshift IMMEDIATELY on cycle 1 to protect voice continuity
        val stage2Profile = leadRider.engine.evaluateClusterHealth(stage2Health, timestamp)
        assertEquals(AudioProfile.ULTRA_LOW, stage2Profile, "Expected rapid step-down to ULTRA_LOW")
        assertEquals(16, leadRider.engine.effectiveBitrateKbps.value)
        assertEquals(60, leadRider.engine.effectiveFrameDurationMs.value)
        assertTrue(leadRider.engine.telemetry.value.lastTransitionReason.contains("Rapid step-down"))

        // Transmit speech in degraded canyon at ULTRA_LOW profile
        val stage2Packet = leadRider.transmitSpeech(syntheticVoicePcm)
        assertTrue(stage2Packet != null && stage2Packet.isNotEmpty())
        assertTrue(stage2Packet.size < stage1Packet.size, "Compressed ULTRA_LOW frame must be smaller than HIGH frame")

        // =========================================================================
        // STAGE 3: Canyon Exit & Asymmetric Hysteresis Recovery
        // =========================================================================
        // Open plain restored: 0% loss, strong -58 dBm signal with contiguous sequence stream
        suspend fun feedPristinePackets(t: Long, count: Int = 70) {
            for (p in 0 until count) {
                for (i in 1..7) {
                    val s = nextSeq["rider_$i"] ?: 1L
                    nextSeq["rider_$i"] = s + 1L
                    leadRider.scorer.recordPacket(
                        peerId = "rider_$i",
                        sequenceNumber = s,
                        rawRssiDbm = -58,
                        transitTimeMs = 6L,
                        hopCount = 1,
                        timestampMs = t + (p * 20L)
                    )
                }
            }
        }

        // Cycle 1: Pristine condition restored. Must DAMPEN and stay ULTRA_LOW.
        timestamp += 1000L
        feedPristinePackets(timestamp)
        var recoveryHealth = leadRider.scorer.clusterHealth.value
        var profile = leadRider.engine.evaluateClusterHealth(recoveryHealth, timestamp)
        assertEquals(AudioProfile.ULTRA_LOW, profile, "Cycle 1 must remain damped at ULTRA_LOW")
        assertEquals(1, leadRider.engine.telemetry.value.consecutivePristineCycles)

        // Cycle 2: Pristine condition maintained. Still damped.
        timestamp += 1000L
        feedPristinePackets(timestamp)
        recoveryHealth = leadRider.scorer.clusterHealth.value
        profile = leadRider.engine.evaluateClusterHealth(recoveryHealth, timestamp)
        assertEquals(AudioProfile.ULTRA_LOW, profile, "Cycle 2 must remain damped at ULTRA_LOW")
        assertEquals(2, leadRider.engine.telemetry.value.consecutivePristineCycles)

        // Cycle 3: 3rd sustained pristine cycle. Now safely upgrades to HIGH!
        timestamp += 1000L
        feedPristinePackets(timestamp)
        recoveryHealth = leadRider.scorer.clusterHealth.value
        profile = leadRider.engine.evaluateClusterHealth(recoveryHealth, timestamp)
        assertEquals(AudioProfile.HIGH, profile, "Cycle 3 must successfully upgrade to HIGH")
        assertEquals(48, leadRider.engine.effectiveBitrateKbps.value)
        assertTrue(leadRider.engine.telemetry.value.lastTransitionReason.contains("Conservative step-up"))

        // =========================================================================
        // STAGE 4: Single Weak Straggler Protection (WORST_LINK vs CLUSTER_AVERAGE)
        // =========================================================================
        // Riders 1..5 remain pristine, while Riders 6 and 7 drop into shadow behind a ridge
        for (i in 6..7) {
            leadRider.scorer.recordLoss("rider_$i", lostCount = 300L, timestampMs = timestamp)
            val current = nextSeq["rider_$i"] ?: 100L
            val droppedSeq = current + 100L // 99 lost packets (within 1..200 gap window)
            leadRider.scorer.recordPacket(
                peerId = "rider_$i",
                sequenceNumber = droppedSeq,
                rawRssiDbm = -92,
                transitTimeMs = 85L,
                hopCount = 2,
                timestampMs = timestamp
            )
            nextSeq["rider_$i"] = droppedSeq + 1L
        }
        timestamp += 200L

        val stragglerHealth = leadRider.scorer.clusterHealth.value
        assertTrue(stragglerHealth.worstScore < 50, "Straggler worst score must be in ULTRA_LOW bracket (< 50), got ${stragglerHealth.worstScore}")
        assertTrue(stragglerHealth.averageScore in 70..84, "Cluster average falls into MEDIUM bracket (70..84), got ${stragglerHealth.averageScore}")

        // In WORST_LINK mode, lead rider immediately downshifts to protect the straggler
        assertEquals(ClusterPolicy.WORST_LINK, leadRider.engine.telemetry.value.clusterPolicy)
        val worstLinkProfile = leadRider.engine.evaluateClusterHealth(stragglerHealth, timestamp)
        assertEquals(AudioProfile.ULTRA_LOW, worstLinkProfile, "WORST_LINK must adapt down to save straggler")

        // Switch to CLUSTER_AVERAGE: evaluates based on cluster average (70..84 -> MEDIUM)
        leadRider.engine.setClusterPolicy(ClusterPolicy.CLUSTER_AVERAGE)
        val engineAvg = AdaptiveBitrateEngine(initialProfile = AudioProfile.HIGH, defaultClusterPolicy = ClusterPolicy.CLUSTER_AVERAGE)
        val avgProfile = engineAvg.evaluateClusterHealth(stragglerHealth, timestamp)
        assertEquals(AudioProfile.MEDIUM, avgProfile, "CLUSTER_AVERAGE adapts to MEDIUM based on average")
    }
}
