package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.phy.BlePhyMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FleetMeshCongestionSimulationTest {
    @Test
    fun denseMixedTrafficBacksOffAndPreservesEmergencyRelay() = runTest {
        val random = Random(3704)
        val governor = MeshCongestionGovernor(randomSupplier = { random.nextDouble() })
        val timestampMs = 10_000L
        val traffic = (1..24).flatMap { node ->
            val sender = "rider-$node"
            listOf(
                Transmission(MeshPacket.Audio(sender, 1, timestampMs, ByteArray(80), AudioProfile.MEDIUM), 24_000L),
                Transmission(MeshPacket.Control(sender, ControlType.POWER_STATUS), 8_000L),
                Transmission(MeshPacket.Control(sender, ControlType.PING), 6_000L)
            )
        }
        val baseline = governor.metrics.value

        // All riders offer all three traffic classes within the same measurement window.
        // Airtime is synthetic offered load, not a measurement of BLE packet durations.
        for (transmission in traffic) {
            governor.recordAirtimeUsage(transmission.airtimeMicros, timestampMs = timestampMs)
        }
        for (node in 1..24) {
            governor.recordNeighborPresence("rider-$node", timestampMs = timestampMs)
        }

        val congested = governor.metrics.value
        assertEquals(72, traffic.size)
        assertEquals(24, congested.activeNodeCount)
        assertEquals(0.912f, congested.channelUtilization, absoluteTolerance = 0.001f)
        assertTrue(congested.isThrottled)
        assertTrue(congested.currentAdvIntervalMs > baseline.currentAdvIntervalMs)

        val admitted = mutableListOf<Transmission>()
        for (transmission in traffic) {
            if (governor.shouldRelay(transmission.packet, now = timestampMs)) {
                admitted += transmission
            }
        }
        assertEquals((traffic.size - admitted.size).toLong(), governor.metrics.value.totalPacketsDampened)
        assertTrue(admitted.sumOf { it.airtimeMicros } < traffic.sumOf { it.airtimeMicros })
        assertTrue(admitted.any { it.packet is MeshPacket.Audio })
        val emergency = MeshPacket.Control("rider-24", ControlType.CRASH_ALERT)
        assertTrue(governor.shouldRelay(emergency, now = timestampMs))
        assertEquals(1L, governor.metrics.value.emergencyPacketsBypassed)

        governor.recordNeighborPresence("rider-24", timestampMs = timestampMs + 1_001)
        val afterAirtimeExpires = governor.metrics.value
        assertEquals(0.0f, afterAirtimeExpires.channelUtilization)
        assertTrue(afterAirtimeExpires.isHighDensity)
        assertTrue(afterAirtimeExpires.currentAdvIntervalMs > baseline.currentAdvIntervalMs)
        assertTrue(afterAirtimeExpires.currentAdvIntervalMs < congested.currentAdvIntervalMs)

        governor.recordNeighborPresence("rider-24", timestampMs = timestampMs + 10_001)
        assertFalse(governor.metrics.value.isThrottled)
        assertEquals(baseline.currentAdvIntervalMs, governor.metrics.value.currentAdvIntervalMs)
    }

    @Test
    fun simulatedRangeSweepSelectsCodedPhyForWeakLinks() = runTest {
        val switcher = AdaptivePhySwitcher()
        val links = (1..24).map { node ->
            when (node % 3) {
                0 -> SimulatedLink("rider-$node", -60, 92, 0.01, BlePhyMode.LE_2M)
                1 -> SimulatedLink("rider-$node", -75, 78, 0.08, BlePhyMode.LE_1M)
                else -> SimulatedLink("rider-$node", -89, 45, 0.20, BlePhyMode.CODED_S8)
            }
        }

        // Models near, intermediate, and obstructed links with explicit RF inputs.
        // RSSI buckets do not establish physical distance or device PHY support.
        repeat(2) { sample ->
            for (link in links) {
                switcher.evaluatePeerLink(link.id, link.score, link.rssiDbm, link.loss, 10_000L + sample * 100)
            }
        }

        for (link in links) {
            assertEquals(link.expectedPhy, switcher.getPeerPhy(link.id), link.id)
        }

        val previouslyStrong = links.filter { it.expectedPhy == BlePhyMode.LE_2M }
        val afterCooldownMs = 10_100L + switcher.config.cooldownPeriodMs + 1
        for (link in previouslyStrong) {
            assertTrue(switcher.evaluatePeerLink(link.id, 55, -78, 0.20, afterCooldownMs))
            assertEquals(BlePhyMode.CODED_S8, switcher.getPeerPhy(link.id), link.id)
        }
    }

    private data class Transmission(val packet: MeshPacket, val airtimeMicros: Long)

    private data class SimulatedLink(
        val id: String,
        val rssiDbm: Int,
        val score: Int,
        val loss: Double,
        val expectedPhy: BlePhyMode
    )
}
