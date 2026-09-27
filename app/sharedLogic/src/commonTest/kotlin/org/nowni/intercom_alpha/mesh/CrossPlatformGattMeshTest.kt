package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.group.GroupConfig
import org.nowni.intercom_alpha.group.GroupManagerImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeMeshTransport : MeshTransport {
    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val incomingAudioChannel = Channel<MeshPacket.Audio>(Channel.BUFFERED)
    private val incomingControlChannel = Channel<MeshPacket.Control>(Channel.BUFFERED)
    private val connectionStateChannel = Channel<ConnectionState>(Channel.CONFLATED)

    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = incomingAudioChannel
    override val incomingControl: ReceiveChannel<MeshPacket.Control> = incomingControlChannel
    override val connectionState: ReceiveChannel<ConnectionState> = connectionStateChannel

    var sentPackets = mutableListOf<ByteArray>()

    override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {}
    override suspend fun stop() {}
    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
        sentPackets.add(data)
    }
    override suspend fun sendControl(type: ControlType, payload: ByteArray) {
        sentPackets.add(payload)
    }
    override fun updateConfig(config: MeshConfig) {}
}

class CrossPlatformGattMeshTest {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "#type"
    }

    @Test
    fun testSharedGattServiceAndCharacteristicUuids() {
        val expectedServiceUuid = "0000180d-0000-1000-8000-00805f9b34fb"
        val expectedCharacteristicUuid = "00002a37-0000-1000-8000-00805f9b34fb"

        assertEquals(expectedServiceUuid, MeshTransport.GATT_SERVICE_UUID)
        assertEquals(expectedCharacteristicUuid, MeshTransport.GATT_CHARACTERISTIC_UUID)
        assertEquals(0x0991, MeshTransport.BLE_MANUFACTURER_ID)
    }

    @Test
    fun testAudioPacketSerializationRoundTrip() {
        val originalPacket = MeshPacket.Audio(
            senderId = "peer-iphone-15",
            sequence = 42L,
            timestamp = 1727330000000L,
            data = byteArrayOf(0x12, 0x34, 0x56, 0x78),
            profile = AudioProfile.MEDIUM
        )

        val serialized = json.encodeToString<MeshPacket>(originalPacket)
        val deserialized = json.decodeFromString<MeshPacket>(serialized)

        assertTrue(deserialized is MeshPacket.Audio)
        assertEquals(originalPacket.senderId, deserialized.senderId)
        assertEquals(originalPacket.sequence, deserialized.sequence)
        assertEquals(originalPacket.timestamp, deserialized.timestamp)
        assertTrue(originalPacket.data.contentEquals(deserialized.data))
        assertEquals(originalPacket.profile, deserialized.profile)
        assertEquals(originalPacket.profile.sampleRateHz, deserialized.profile.sampleRateHz)
        assertEquals(originalPacket.profile.bitrateKbps, deserialized.profile.bitrateKbps)
    }

    @Test
    fun testControlPacketSerializationAllTypes() {
        for (controlType in ControlType.entries) {
            val originalPacket = MeshPacket.Control(
                senderId = "peer-android-galaxy",
                type = controlType,
                payload = "status=ok".encodeToByteArray()
            )

            val serialized = json.encodeToString<MeshPacket>(originalPacket)
            val deserialized = json.decodeFromString<MeshPacket>(serialized)

            assertTrue(deserialized is MeshPacket.Control)
            assertEquals(originalPacket.senderId, deserialized.senderId)
            assertEquals(originalPacket.type, deserialized.type)
            assertTrue(originalPacket.payload.contentEquals(deserialized.payload))
        }
    }

    @Test
    fun testDiscoveryAndRelayPacketSerialization() {
        val discovery = MeshPacket.Discovery(
            senderId = "sender-1",
            groupId = "group-alps-trek",
            peerName = "Alpha Leader",
            capabilities = Capabilities(
                supportsBle = true,
                supportsWifiDirect = true,
                supportsRelay = true,
                maxPeers = 8
            )
        )

        val relay = MeshPacket.Relay(
            originalSenderId = "sender-1",
            ttl = 3,
            packet = discovery
        )

        val serialized = json.encodeToString<MeshPacket>(relay)
        val deserialized = json.decodeFromString<MeshPacket>(serialized)

        assertTrue(deserialized is MeshPacket.Relay)
        assertEquals(3, deserialized.ttl)
        assertEquals("sender-1", deserialized.originalSenderId)
        assertTrue(deserialized.packet is MeshPacket.Discovery)
        val innerDiscovery = deserialized.packet
        assertEquals("group-alps-trek", innerDiscovery.groupId)
        assertEquals("Alpha Leader", innerDiscovery.peerName)
        assertEquals(8, innerDiscovery.capabilities.maxPeers)
    }

    @Test
    fun testInviteCodeGenerationAndParsingCrossPlatform() = runTest {
        val transport = FakeMeshTransport()
        val groupManager = GroupManagerImpl(transport, "local-peer-test")

        val initialGroup = groupManager.createGroup(
            name = "Rally Team Alpha",
            config = GroupConfig()
        )

        val inviteCode = groupManager.generateInviteCode()
        assertTrue(inviteCode.startsWith("INTERCOM:v1:"))

        val parsed = groupManager.parseInviteCode(inviteCode)
        assertNotNull(parsed)
        assertEquals(initialGroup.id, parsed.groupId)
        assertEquals(initialGroup.name, parsed.groupName)
        assertEquals(initialGroup.leaderId, parsed.leaderId)
        assertTrue(parsed.expiresAt > 0)
    }

    @Test
    fun testLittleEndianPcmConversion() {
        val inputShorts = shortArrayOf(0, 1000, -1000, 32767, -32768)
        val bytes = ByteArray(inputShorts.size * 2)

        for (i in inputShorts.indices) {
            val sample = inputShorts[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val decodedShorts = ShortArray(inputShorts.size)
        for (i in decodedShorts.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt()
            decodedShorts[i] = ((high shl 8) or low).toShort()
        }

        for (i in inputShorts.indices) {
            assertEquals(inputShorts[i], decodedShorts[i])
        }
    }

    @Test
    fun testMultiHopRelayTtlDecrementAndLocalDelivery() = runTest {
        val nodeA = SimulatedMeshNode("node-a", "Peer A")
        val nodeB = SimulatedMeshNode("node-b", "Peer B (Relay)")
        val nodeC = SimulatedMeshNode("node-c", "Peer C")

        // Topology: A <-> B <-> C (A cannot reach C directly)
        nodeA.peers.add(nodeB)
        nodeB.peers.addAll(listOf(nodeA, nodeC))
        nodeC.peers.add(nodeB)

        val audio = MeshPacket.Audio(
            senderId = nodeA.id,
            sequence = 101L,
            timestamp = 1727330000000L,
            data = byteArrayOf(0x01, 0x02, 0x03, 0x04),
            profile = AudioProfile.MEDIUM
        )

        val relayPacket = MeshPacket.Relay(
            originalSenderId = nodeA.id,
            ttl = 3,
            packet = audio
        )

        // A sends to B
        nodeB.receivePacket(relayPacket, fromPeerId = nodeA.id)

        // B should deliver inner packet locally
        assertEquals(1, nodeB.incomingAudio.size)
        assertEquals(101L, nodeB.incomingAudio[0].sequence)

        // B should forward with TTL = 2
        assertEquals(1, nodeB.forwardedPackets.size)
        val forwarded = nodeB.forwardedPackets[0] as MeshPacket.Relay
        assertEquals(2, forwarded.ttl)
        assertEquals(nodeA.id, forwarded.originalSenderId)

        // C should have received and delivered inner audio
        assertEquals(1, nodeC.incomingAudio.size)
        assertEquals(101L, nodeC.incomingAudio[0].sequence)
    }

    @Test
    fun testSlidingWindowDeduplicationDropsBroadcastLoops() = runTest {
        val deduplicator = PacketDeduplicator(windowMs = 5000L)

        val audioPacket = MeshPacket.Audio(
            senderId = "peer-x",
            sequence = 77L,
            timestamp = 1000L,
            data = byteArrayOf(0x55),
            profile = AudioProfile.LOW
        )

        val relayPacket = MeshPacket.Relay(
            originalSenderId = "peer-x",
            ttl = 3,
            packet = audioPacket
        )

        // First arrival -> should process
        assertTrue(deduplicator.shouldProcess(relayPacket, now = 1000L))

        // Duplicate arrivals within 5000ms window -> must drop
        for (t in listOf(1500L, 2000L, 3500L, 4999L)) {
            assertEquals(false, deduplicator.shouldProcess(relayPacket, now = t))
        }

        // After window expires (> 5000ms TTL) -> should accept again
        assertTrue(deduplicator.shouldProcess(relayPacket, now = 6500L))
    }

    @Test
    fun testMultiHopEndToEndEncryptedMeshAudio() = runTest {
        val groupSecret = "ALPHA-EXPEDITION-KEY-2026"
        val cipherA = PacketCipher(PacketCipher.deriveKey(groupSecret))
        val cipherC = PacketCipher(PacketCipher.deriveKey(groupSecret))

        val nodeA = SimulatedMeshNode("node-a", "Peer A", cipher = cipherA)
        val nodeB = SimulatedMeshNode("node-b", "Peer B (Untrusted Relay)") // Has no group cipher
        val nodeC = SimulatedMeshNode("node-c", "Peer C", cipher = cipherC)

        nodeA.peers.add(nodeB)
        nodeB.peers.addAll(listOf(nodeA, nodeC))
        nodeC.peers.add(nodeB)

        val rawPcm = "PCM-SPEECH-FRAME-48KHZ".encodeToByteArray()
        val encryptedData = cipherA.encrypt(rawPcm)

        val secureAudioPacket = MeshPacket.Audio(
            senderId = nodeA.id,
            sequence = 1L,
            timestamp = 2000L,
            data = encryptedData,
            profile = AudioProfile.HIGH
        )

        val relayPacket = MeshPacket.Relay(
            originalSenderId = nodeA.id,
            ttl = 3,
            packet = secureAudioPacket
        )

        // Transmit across relay
        nodeB.receivePacket(relayPacket, fromPeerId = nodeA.id)

        // Intermediate relay node B forwarded the packet without modifying payload
        assertEquals(1, nodeB.forwardedPackets.size)

        // Destination node C received encrypted packet and successfully decrypted original PCM
        assertEquals(1, nodeC.incomingAudio.size)
        val receivedPacket = nodeC.incomingAudio[0]
        val decryptedPcm = cipherC.decrypt(receivedPacket.data)
        assertNotNull(decryptedPcm)
        assertEquals("PCM-SPEECH-FRAME-48KHZ", decryptedPcm.decodeToString())
    }
}

class SimulatedMeshNode(
    val id: String,
    val name: String,
    val cipher: PacketCipher? = null,
    val enableRelay: Boolean = true
) {
    val deduplicator = PacketDeduplicator()
    val incomingAudio = mutableListOf<MeshPacket.Audio>()
    val incomingControl = mutableListOf<MeshPacket.Control>()
    val forwardedPackets = mutableListOf<MeshPacket>()
    val peers = mutableListOf<SimulatedMeshNode>()

    suspend fun receivePacket(packet: MeshPacket, fromPeerId: String) {
        if (!deduplicator.shouldProcess(packet)) {
            return
        }

        when (packet) {
            is MeshPacket.Audio -> incomingAudio.add(packet)
            is MeshPacket.Control -> incomingControl.add(packet)
            is MeshPacket.Discovery -> {}
            is MeshPacket.Relay -> {
                when (val inner = packet.packet) {
                    is MeshPacket.Audio -> incomingAudio.add(inner)
                    is MeshPacket.Control -> incomingControl.add(inner)
                    else -> {}
                }

                if (packet.ttl > 1 && enableRelay) {
                    val forwarded = packet.copy(ttl = packet.ttl - 1)
                    forwardedPackets.add(forwarded)
                    for (peer in peers) {
                        if (peer.id != fromPeerId) {
                            peer.receivePacket(forwarded, id)
                        }
                    }
                }
            }
        }
    }
}

