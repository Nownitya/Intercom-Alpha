package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvoyMeshSimulationHarnessTest {

    @Test
    fun testEightNodeConvoyBenchmarkMultiHopAndLoopSuppression() = runTest {
        val harness = ConvoyMeshSimulationHarness(
            ConvoySimulationConfig(
                nodeCount = 8,
                maxDirectHopDistance = 2, // Each node reaches 2 adjacent peers directly
                defaultTtl = 4,
                packetDropRate = 0.0, // 0% drop to test deterministic multi-hop propagation
                enableOutOfOrderArrivals = false
            )
        )

        assertEquals(8, harness.nodes.size, "Must initialize exactly 8 virtual peers")

        // Lead rider (index 0) speaks 20 frames
        val metrics = harness.runConvoyVoiceStreamBenchmark(senderIndex = 0, frameCount = 20)

        // 1. Packets must reach tail node (node 7) via multi-hop relay
        assertTrue(
            metrics.tailNodeReceivedCount > 0,
            "Tail node must receive voice frames via multi-hop relay (got ${metrics.tailNodeReceivedCount})"
        )
        assertEquals(
            metrics.tailNodeReceivedCount,
            metrics.tailNodeDecryptedCount,
            "100% of received frames at tail node must be decrypted via PFS session ratchet"
        )

        // 2. Loop suppression and deduplication must have eliminated duplicate flooding echoes
        assertTrue(
            metrics.loopSuppressionDrops > 0,
            "Loop suppression must actively drop duplicate frames (${metrics.loopSuppressionDrops} dropped)"
        )

        // 3. TTL expiration must bound multi-hop propagation
        assertTrue(
            metrics.ttlExpirationDrops > 0,
            "TTL expiration must bound hop radius (${metrics.ttlExpirationDrops} dropped)"
        )

        // 4. Must execute under 5 seconds
        assertTrue(
            metrics.executionDurationMs < 5000L,
            "Benchmark execution (${metrics.executionDurationMs}ms) must complete under 5 seconds"
        )
    }

    @Test
    fun testTwentyFivePercentDropAndOutOfOrderRatchetRecovery() = runTest {
        val harness = ConvoyMeshSimulationHarness(
            ConvoySimulationConfig(
                nodeCount = 8,
                maxDirectHopDistance = 2,
                defaultTtl = 4,
                packetDropRate = 0.25, // 25% stochastic packet loss
                enableOutOfOrderArrivals = true, // Shuffled out-of-order delivery
                randomSeed = 1337L
            )
        )

        val metrics = harness.runConvoyVoiceStreamBenchmark(senderIndex = 0, frameCount = 30)

        // With 25% drop rate and out-of-order delivery, verify ratchet integrity:
        // Every single packet that successfully reached the tail node must be 100% decrypted!
        assertTrue(metrics.tailNodeReceivedCount > 0, "Tail node must receive packets despite 25% loss")
        assertEquals(
            metrics.tailNodeReceivedCount,
            metrics.tailNodeDecryptedCount,
            "Skipped-key recovery must decrypt 100% of received out-of-order packets (${metrics.tailNodeDecryptedCount}/${metrics.tailNodeReceivedCount})"
        )

        // Fast execution check (< 5 seconds)
        assertTrue(metrics.executionDurationMs < 5000L, "Simulation must execute within 5 seconds")
    }

    @Test
    fun testJitterBufferPlayoutOnVirtualNodes() = runTest {
        val harness = ConvoyMeshSimulationHarness(
            ConvoySimulationConfig(
                nodeCount = 4,
                maxDirectHopDistance = 1,
                defaultTtl = 3,
                packetDropRate = 0.0,
                enableOutOfOrderArrivals = false
            )
        )

        harness.runConvoyVoiceStreamBenchmark(senderIndex = 0, frameCount = 15)

        // Verify intermediate node playout
        val intermediateAudible = harness.pullPlayoutForNode(nodeIndex = 1, tickCount = 20)
        assertTrue(intermediateAudible > 0, "Intermediate node must successfully play out jitter buffer frames")
    }
}
