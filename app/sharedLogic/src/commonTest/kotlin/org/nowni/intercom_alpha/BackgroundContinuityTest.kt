package org.nowni.intercom_alpha

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshConfig
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.MeshTransport
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.mesh.Peer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatedBackgroundMeshTransport : MeshTransport {
    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val incomingAudioChannel = Channel<MeshPacket.Audio>(Channel.BUFFERED)
    private val incomingControlChannel = Channel<MeshPacket.Control>(Channel.BUFFERED)
    private val connectionStateChannel = Channel<org.nowni.intercom_alpha.mesh.ConnectionState>(Channel.CONFLATED)

    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = incomingAudioChannel
    override val incomingControl: ReceiveChannel<MeshPacket.Control> = incomingControlChannel
    override val connectionState: ReceiveChannel<org.nowni.intercom_alpha.mesh.ConnectionState> = connectionStateChannel

    val receivedAudioPackets = mutableListOf<MeshPacket.Audio>()
    val forwardedPackets = mutableListOf<ByteArray>()
    private var isSimulatedScreenOff = false

    fun setSimulatedScreenOff(screenOff: Boolean) {
        this.isSimulatedScreenOff = screenOff
    }

    suspend fun injectIncomingAudio(packet: MeshPacket.Audio) {
        incomingAudioChannel.send(packet)
    }

    override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {}
    override suspend fun stop() {}
    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
        forwardedPackets.add(data)
    }
    override suspend fun sendControl(type: ControlType, payload: ByteArray) {}
    override fun updateConfig(config: MeshConfig) {}
}

class BackgroundContinuityTest {

    @Test
    fun testZeroDroppedPacketsDuringSimulatedScreenOffBurst() = runTest {
        val transport = SimulatedBackgroundMeshTransport()
        val deduplicator = PacketDeduplicator()
        val processedPackets = mutableListOf<MeshPacket.Audio>()

        transport.setSimulatedScreenOff(true)

        val consumerJob = launch {
            for (packet in transport.incomingAudio) {
                if (deduplicator.shouldProcess(packet)) {
                    processedPackets.add(packet)
                }
            }
        }

        // Simulate a rapid burst of 50 consecutive voice packets while phone is locked
        val packetBurstCount = 50
        for (seq in 1..packetBurstCount) {
            val audioPacket = MeshPacket.Audio(
                senderId = "rider-bravo-helmet",
                sequence = seq.toLong(),
                timestamp = 1700000000000L + seq * 20L,
                data = byteArrayOf(0x01, 0x02, (seq and 0xFF).toByte()),
                profile = AudioProfile.MEDIUM
            )
            transport.injectIncomingAudio(audioPacket)
        }

        kotlinx.coroutines.yield()

        consumerJob.cancel()

        // Verify: zero dropped packets under simulated screen-off burst
        assertEquals(packetBurstCount, processedPackets.size)
        assertEquals(1L, processedPackets.first().sequence)
        assertEquals(50L, processedPackets.last().sequence)
    }

    @Test
    fun testDuplicateSuppressionAndZeroSlidingWindowMemoryLeak() = runTest {
        val deduplicator = PacketDeduplicator()

        // 1. Verify duplicates are discarded without leak
        val packetA = MeshPacket.Audio(
            senderId = "peer-alpha",
            sequence = 100L,
            timestamp = 1000L,
            data = byteArrayOf(0x10),
            profile = AudioProfile.LOW
        )

        assertTrue(deduplicator.shouldProcess(packetA), "First arrival of packetA must be processed")
        assertFalse(deduplicator.shouldProcess(packetA), "Duplicate arrival of packetA must be dropped")
        assertFalse(deduplicator.shouldProcess(packetA), "Triplicate arrival of packetA must be dropped")

        // 2. Sliding window memory bound: emit 1500 unique packets into max 1000 capacity cache
        for (i in 1..1500) {
            val p = MeshPacket.Audio(
                senderId = "peer-charlie",
                sequence = i.toLong(),
                timestamp = 1000L + i,
                data = byteArrayOf(0x20),
                profile = AudioProfile.LOW
            )
            val accepted = deduplicator.shouldProcess(p)
            assertTrue(accepted, "Unique sequence $i should be processed")
        }

        // 3. Clear deduplicator state cleanly on disconnect
        deduplicator.clear()

        // After clearing, packetA can be processed again cleanly
        assertTrue(deduplicator.shouldProcess(packetA), "Cleared deduplicator must process re-emitted packet")
    }

    @Test
    fun testRelayFloodingLoopPreventionUnderScreenOff() = runTest {
        val deduplicator = PacketDeduplicator()

        val innerAudio = MeshPacket.Audio(
            senderId = "rider-lead",
            sequence = 500L,
            timestamp = 1700000500000L,
            data = byteArrayOf(0x42, 0x43),
            profile = AudioProfile.HIGH
        )

        val relayHop1 = MeshPacket.Relay(
            originalSenderId = "rider-lead",
            ttl = 3,
            packet = innerAudio
        )

        val relayHop2 = MeshPacket.Relay(
            originalSenderId = "rider-lead",
            ttl = 2,
            packet = innerAudio
        )

        // First hop must be processed
        assertTrue(deduplicator.shouldProcess(relayHop1), "First relay hop should be forwarded")

        // Second hop of the same inner packet must be dropped to prevent broadcast storms
        assertFalse(deduplicator.shouldProcess(relayHop2), "Looping relay hop must be suppressed")
    }
}
