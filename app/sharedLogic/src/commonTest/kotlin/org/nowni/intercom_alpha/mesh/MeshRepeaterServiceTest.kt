package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.common.AudioProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshRepeaterServiceTest {

    private val json = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    @Test
    fun testAudioPacketRelayEncapsulationAndTtl() = runTest {
        val forwardedPackets = mutableListOf<MeshPacket>()
        val repeater = MeshRepeaterService(
            repeaterId = "basecamp-relay-alpha",
            repeaterName = "Peak Pass Station",
            defaultTtl = 3,
            batteryPercentProvider = { 95 },
            packetOutbox = { forwardedPackets.add(it) }
        )
        repeater.start()

        val audio = MeshPacket.Audio(
            senderId = "rider-scout-1",
            sequence = 101L,
            timestamp = 1000L,
            data = byteArrayOf(1, 2, 3, 4),
            profile = AudioProfile.MEDIUM
        )

        val relayed = repeater.handleIncomingPacket(audio)
        assertNotNull(relayed, "Audio packet must be relayed")
        assertTrue(relayed is MeshPacket.Relay, "Packet must be wrapped in Relay frame")
        assertEquals("rider-scout-1", relayed.originalSenderId)
        assertEquals(2, relayed.ttl, "TTL should be decremented from default 3 to 2")
        assertEquals(audio, relayed.packet)
        assertEquals(1, forwardedPackets.size)
        assertEquals(1L, repeater.telemetry.value.packetsForwarded)
    }

    @Test
    fun testRelayPacketTtlDecrementAndDropAtBoundary() = runTest {
        val forwardedPackets = mutableListOf<MeshPacket>()
        val repeater = MeshRepeaterService(
            repeaterId = "repeater-01",
            repeaterName = "Pass Relay",
            packetOutbox = { forwardedPackets.add(it) }
        )

        val innerAudio = MeshPacket.Audio(
            senderId = "rider-leader",
            sequence = 42L,
            timestamp = 2000L,
            data = byteArrayOf(9, 8, 7),
            profile = AudioProfile.LOW
        )

        // Case 1: Relay with TTL = 3 -> Decrement to 2
        val relayTtl3 = MeshPacket.Relay(originalSenderId = "rider-leader", ttl = 3, packet = innerAudio)
        val forwarded = repeater.handleIncomingPacket(relayTtl3)
        assertNotNull(forwarded)
        assertTrue(forwarded is MeshPacket.Relay)
        assertEquals(2, forwarded.ttl)

        // Case 2: Relay with TTL = 1 -> Must drop (expired)
        val expiredInner = MeshPacket.Audio(
            senderId = "rider-tail",
            sequence = 43L,
            timestamp = 2100L,
            data = byteArrayOf(5, 5, 5),
            profile = AudioProfile.LOW
        )
        val relayTtl1 = MeshPacket.Relay(originalSenderId = "rider-tail", ttl = 1, packet = expiredInner)
        val dropped = repeater.handleIncomingPacket(relayTtl1)
        assertNull(dropped, "Packet with TTL=1 must not be forwarded")
        assertEquals(1L, repeater.telemetry.value.packetsDroppedTtlExpired)
    }

    @Test
    fun testDuplicatePacketLoopSuppression() = runTest {
        val forwardedPackets = mutableListOf<MeshPacket>()
        val repeater = MeshRepeaterService(
            repeaterId = "repeater-01",
            repeaterName = "Pass Relay",
            packetOutbox = { forwardedPackets.add(it) }
        )

        val audio = MeshPacket.Audio(
            senderId = "rider-4",
            sequence = 777L,
            timestamp = 5000L,
            data = byteArrayOf(10, 20),
            profile = AudioProfile.MEDIUM
        )

        // 1. First reception: should forward
        val first = repeater.handleIncomingPacket(audio)
        assertNotNull(first, "First arrival of packet must be forwarded")

        // 2. Duplicate arrival (routing loop echo): must be suppressed
        val second = repeater.handleIncomingPacket(audio)
        assertNull(second, "Duplicate arrival must be dropped")
        assertEquals(1L, repeater.telemetry.value.packetsDroppedDuplicate)
        assertEquals(1L, repeater.telemetry.value.packetsForwarded)
    }

    @Test
    fun testSelfOriginPacketsDropped() = runTest {
        val repeater = MeshRepeaterService(
            repeaterId = "repeater-local",
            repeaterName = "Local Station"
        )

        val selfPacket = MeshPacket.Control(
            senderId = "repeater-local",
            type = ControlType.PING,
            payload = byteArrayOf()
        )

        val result = repeater.handleIncomingPacket(selfPacket)
        assertNull(result, "Packets originating from the repeater itself must never be forwarded")
    }

    @Test
    fun testMultiHopForwardingLatencyUnder15Ms() = runTest {
        var simulatedNow = 1000L
        val repeater = MeshRepeaterService(
            repeaterId = "relay-ridge",
            repeaterName = "Ridge Tower",
            timeProvider = { simulatedNow }
        )

        val packet = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 1L,
            timestamp = 1000L,
            data = byteArrayOf(1),
            profile = AudioProfile.ULTRA_LOW
        )

        val relayed = repeater.handleIncomingPacket(packet, arrivalTimeMs = 1000L)
        assertNotNull(relayed)
        assertTrue(
            repeater.telemetry.value.lastForwardLatencyMs < 15L,
            "Forwarding latency (${repeater.telemetry.value.lastForwardLatencyMs}ms) must be < 15ms"
        )
    }

    @Test
    fun testRepeaterBeaconEmissionAndDecoding() {
        var currentTime = 50000L
        val emittedPackets = mutableListOf<MeshPacket>()
        val repeater = MeshRepeaterService(
            repeaterId = "basecamp-01",
            repeaterName = "Basecamp Alpha",
            batteryPercentProvider = { 88 },
            timeProvider = { currentTime },
            packetOutbox = { emittedPackets.add(it) }
        )

        val beaconPacket = repeater.emitBeaconPacket()
        assertEquals(ControlType.REPEATER_BEACON, beaconPacket.type)
        assertEquals("basecamp-01", beaconPacket.senderId)
        assertEquals(1, emittedPackets.size)
        assertEquals(1L, repeater.telemetry.value.beaconEmissionCount)

        val decoded = repeater.decodeBeaconPayload(beaconPacket)
        assertNotNull(decoded)
        assertEquals("basecamp-01", decoded.repeaterId)
        assertEquals("Basecamp Alpha", decoded.repeaterName)
        assertEquals(88, decoded.batteryPercent)
        assertTrue(decoded.isSolarPowered)
        assertEquals(RepeaterDutyCycleMode.CONTINUOUS, decoded.dutyCycleMode)
    }

    @Test
    fun testDiagnosticPingTriggersPongResponse() = runTest {
        val outbox = mutableListOf<MeshPacket>()
        val repeater = MeshRepeaterService(
            repeaterId = "relay-canyon",
            repeaterName = "Canyon Relay",
            batteryPercentProvider = { 92 },
            packetOutbox = { outbox.add(it) }
        )

        val ping = RepeaterPingPayload(
            senderId = "rider-inspector",
            timestampMs = 12345L,
            sequence = 1L
        )
        val pingPacket = MeshPacket.Control(
            senderId = "rider-inspector",
            type = ControlType.REPEATER_PING,
            payload = json.encodeToString(ping).encodeToByteArray()
        )

        repeater.handleIncomingPacket(pingPacket, arrivalTimeMs = 12346L)

        // Outbox should contain: 1 forwarded Relay packet + 1 REPEATER_PONG packet
        val pongPacket = outbox.filterIsInstance<MeshPacket.Control>()
            .find { it.type == ControlType.REPEATER_PONG }
        assertNotNull(pongPacket, "Repeater must emit REPEATER_PONG in response to ping")
        assertEquals("relay-canyon", pongPacket.senderId)

        val pong = json.decodeFromString<RepeaterPongPayload>(pongPacket.payload.decodeToString())
        assertEquals("relay-canyon", pong.repeaterId)
        assertEquals("rider-inspector", pong.pingSenderId)
        assertEquals(12345L, pong.pingTimestampMs)
        assertEquals(92, pong.batteryPercent)
    }

    @Test
    fun testActiveNeighborTrackingAndStalePruning() = runTest {
        var mockTime = 10_000L
        val repeater = MeshRepeaterService(
            repeaterId = "relay-hub",
            repeaterName = "Hub Relay",
            timeProvider = { mockTime }
        )

        // 1. Ingest packets from two distinct riders
        val packetA = MeshPacket.Audio("rider-A", 1L, 1000L, byteArrayOf(1), AudioProfile.MEDIUM)
        val packetB = MeshPacket.Audio("rider-B", 1L, 1000L, byteArrayOf(2), AudioProfile.MEDIUM)
        repeater.handleIncomingPacket(packetA, arrivalTimeMs = mockTime)
        repeater.handleIncomingPacket(packetB, arrivalTimeMs = mockTime)

        assertEquals(2, repeater.telemetry.value.activeNeighborCount)

        // 2. Advance time past 60s stale threshold (75 seconds later)
        mockTime += 75_000L

        // Ingest new packet from rider-A only
        val packetA2 = MeshPacket.Audio("rider-A", 2L, 2000L, byteArrayOf(3), AudioProfile.MEDIUM)
        repeater.handleIncomingPacket(packetA2, arrivalTimeMs = mockTime)

        // rider-B should be pruned, neighbor count should be 1
        val beacon = repeater.emitBeaconPacket()
        val decoded = repeater.decodeBeaconPayload(beacon)
        assertNotNull(decoded)
        assertEquals(1, decoded.activeNeighborCount, "Stale neighbor B must be pruned")
    }
}
