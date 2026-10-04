package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.audio.PlayoutFrame
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.protocol.DefaultSignalingJson
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.protocol.SignalingMessage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class MultiClusterBridgeTest {

    private val meshJson = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    @Test
    fun testClusterBridgeSerializationWithPoundTypeDiscriminator() {
        val bridge = SignalingMessage.ClusterBridge(
            bridgeId = "bridge-convoy-alpha",
            sourceClusterId = "cluster-north",
            targetClusterId = "cluster-south",
            targetRoomId = "Convoy-Channel-1",
            packetType = "AUDIO",
            payloadBase64 = "AQIDBAU=",
            hopLimit = 3,
            clusterPath = listOf("cluster-north"),
            memberPresence = listOf(
                PeerInfo(
                    peerId = "rider-1",
                    displayName = "Road Captain",
                    platform = "Android",
                    currentRoom = "Convoy-Channel-1",
                    clusterId = "cluster-north"
                )
            ),
            timestampMs = 1727734900000L
        )

        val encoded = DefaultSignalingJson.encodeToString<SignalingMessage>(bridge)
        assertTrue(
            encoded.contains("\"#type\":\"ClusterBridge\"") || encoded.contains("\"#type\": \"ClusterBridge\""),
            "Serialized message must include #type discriminator"
        )
        assertTrue(encoded.contains("cluster-north"))
        assertTrue(encoded.contains("cluster-south"))

        val decoded = DefaultSignalingJson.decodeFromString<SignalingMessage>(encoded)
        assertEquals(bridge, decoded)
    }

    @Test
    fun testRoutingLoopPrevention_OwnClusterDropped() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-alpha")
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 1L,
            timestamp = 1000L,
            data = byteArrayOf(0x11, 0x22),
            profile = AudioProfile.HIGH
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        // Message originated from our own cluster
        val incomingFromSelf = SignalingMessage.ClusterBridge(
            bridgeId = "b-1",
            sourceClusterId = "cluster-alpha",
            targetClusterId = null,
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 3,
            clusterPath = listOf("cluster-alpha"),
            timestampMs = 1000L
        )

        bridge.handleIncomingClusterBridge(incomingFromSelf)
        assertEquals(0L, bridge.stats.value.packetsReceivedFromCloud)
        assertEquals(0L, bridge.stats.value.clusterBridgesReceived)
    }

    @Test
    fun testRoutingLoopPrevention_VisitedPathDropped() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-beta")
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-2",
            sequence = 2L,
            timestamp = 2000L,
            data = byteArrayOf(0x33, 0x44),
            profile = AudioProfile.MEDIUM
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        // clusterPath already includes cluster-beta, indicating a circular loop
        val loopingBridge = SignalingMessage.ClusterBridge(
            bridgeId = "b-2",
            sourceClusterId = "cluster-gamma",
            targetClusterId = null,
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 2,
            clusterPath = listOf("cluster-gamma", "cluster-beta", "cluster-delta"),
            timestampMs = 2000L
        )

        bridge.handleIncomingClusterBridge(loopingBridge)
        assertEquals(0L, bridge.stats.value.packetsReceivedFromCloud)
        assertEquals(0L, bridge.stats.value.clusterBridgesReceived)
    }

    @Test
    fun testRoutingLoopPrevention_HopLimitZeroDropped() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-alpha")
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-3",
            sequence = 3L,
            timestamp = 3000L,
            data = byteArrayOf(0x55),
            profile = AudioProfile.LOW
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        // Expired TTL / hop limit
        val expiredBridge = SignalingMessage.ClusterBridge(
            bridgeId = "b-3",
            sourceClusterId = "cluster-beta",
            targetClusterId = null,
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 0,
            clusterPath = listOf("cluster-beta"),
            timestampMs = 3000L
        )

        bridge.handleIncomingClusterBridge(expiredBridge)
        assertEquals(0L, bridge.stats.value.packetsReceivedFromCloud)
    }

    @Test
    fun testTargetClusterFiltering() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-alpha")
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-4",
            sequence = 4L,
            timestamp = 4000L,
            data = byteArrayOf(0x66),
            profile = AudioProfile.HIGH
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        // Targeted strictly to cluster-charlie (not cluster-alpha)
        val wrongTarget = SignalingMessage.ClusterBridge(
            bridgeId = "b-4",
            sourceClusterId = "cluster-beta",
            targetClusterId = "cluster-charlie",
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 3,
            clusterPath = listOf("cluster-beta"),
            timestampMs = 4000L
        )

        bridge.handleIncomingClusterBridge(wrongTarget)
        assertEquals(0L, bridge.stats.value.packetsReceivedFromCloud)

        // Targeted directly to cluster-alpha
        val correctTarget = wrongTarget.copy(targetClusterId = "cluster-alpha", bridgeId = "b-5")
        bridge.handleIncomingClusterBridge(correctTarget)
        assertEquals(1L, bridge.stats.value.packetsReceivedFromCloud)
        assertEquals(1L, bridge.stats.value.clusterBridgesReceived)
    }

    @Test
    fun testTranscodeFreeAdpcmAudioBridgingAndJitterSmoothing() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-receiver")

        // 4-bit IMA ADPCM compressed frame (84 bytes for 20ms audio)
        val adpcmFrame = ByteArray(84) { index -> (index * 7 % 256).toByte() }
        val audioPacket = MeshPacket.Audio(
            senderId = "lead-convoy",
            sequence = 100L,
            timestamp = 1000L,
            data = adpcmFrame,
            profile = AudioProfile.HIGH
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        val incomingBridge = SignalingMessage.ClusterBridge(
            bridgeId = "bridge-audio-1",
            sourceClusterId = "cluster-sender",
            targetClusterId = "cluster-receiver",
            targetRoomId = "Convoy-1",
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 3,
            clusterPath = listOf("cluster-sender"),
            timestampMs = 1000L
        )

        bridge.handleIncomingClusterBridge(incomingBridge)

        // Packet is received without transcoding
        assertEquals(1L, bridge.stats.value.packetsReceivedFromCloud)
        val receivedPacket = bridge.incomingCloudPackets.receive()
        assertTrue(receivedPacket is MeshPacket.Audio)
        assertEquals("lead-convoy", receivedPacket.senderId)
        assertEquals(100L, receivedPacket.sequence)
        assertTrue(adpcmFrame.contentEquals(receivedPacket.data), "ADPCM payload must match byte-for-byte with zero transcoding")

        // Jitter buffer was populated with frame
        val now = org.nowni.intercom_alpha.currentTimeMillis()
        val frame = bridge.jitterBuffer.pull(currentTimeMs = now + 100L)
        assertTrue(frame is PlayoutFrame.Available, "Jitter buffer must smooth and sequence incoming audio frames")
        assertEquals(100L, frame.sequence)
        assertTrue(adpcmFrame.contentEquals(frame.data))
    }

    @Test
    fun testMultiClusterPeerPresenceAggregation() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-main")

        val clusterNorthPeers = listOf(
            PeerInfo(peerId = "north-1", displayName = "Scout 1", platform = "Android", clusterId = "cluster-north"),
            PeerInfo(peerId = "north-2", displayName = "Scout 2", platform = "iOS", clusterId = "cluster-north")
        )
        val clusterSouthPeers = listOf(
            PeerInfo(peerId = "south-1", displayName = "Tail 1", platform = "Android", clusterId = "cluster-south")
        )

        // Announce cluster-north presence
        val northPresence = SignalingMessage.ClusterBridge(
            bridgeId = "pres-1",
            sourceClusterId = "cluster-north",
            targetClusterId = null,
            targetRoomId = null,
            packetType = "PRESENCE",
            payloadBase64 = "",
            hopLimit = 2,
            clusterPath = listOf("cluster-north"),
            memberPresence = clusterNorthPeers,
            timestampMs = 1000L
        )
        bridge.handleIncomingClusterBridge(northPresence)

        // Announce cluster-south presence
        val southPresence = SignalingMessage.ClusterBridge(
            bridgeId = "pres-2",
            sourceClusterId = "cluster-south",
            targetClusterId = null,
            targetRoomId = null,
            packetType = "PRESENCE",
            payloadBase64 = "",
            hopLimit = 2,
            clusterPath = listOf("cluster-south"),
            memberPresence = clusterSouthPeers,
            timestampMs = 1050L
        )
        bridge.handleIncomingClusterBridge(southPresence)

        // Verify aggregation
        assertEquals(2, bridge.clusterPresence.value.size)
        assertEquals(2, bridge.stats.value.activeRemoteClusters)
        assertEquals(3, bridge.aggregatedPeers.value.size)
        assertTrue(bridge.aggregatedPeers.value.any { it.peerId == "north-1" })
        assertTrue(bridge.aggregatedPeers.value.any { it.peerId == "north-2" })
        assertTrue(bridge.aggregatedPeers.value.any { it.peerId == "south-1" })

        // Remove cluster-north
        bridge.removeClusterPresence("cluster-north")
        assertEquals(1, bridge.clusterPresence.value.size)
        assertEquals(1, bridge.stats.value.activeRemoteClusters)
        assertEquals(1, bridge.aggregatedPeers.value.size)
        assertEquals("south-1", bridge.aggregatedPeers.value[0].peerId)
    }

    @Test
    fun testDeduplicationAcrossBridges() = runTest {
        val bridge = CloudRelayBridge(initialClusterId = "cluster-charlie")
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-5",
            sequence = 505L,
            timestamp = 5000L,
            data = byteArrayOf(0x01, 0x02),
            profile = AudioProfile.MEDIUM
        )
        val payloadBase64 = Base64.encode(meshJson.encodeToString<MeshPacket>(audioPacket).encodeToByteArray())

        val bridgePathA = SignalingMessage.ClusterBridge(
            bridgeId = "path-a",
            sourceClusterId = "cluster-alpha",
            targetClusterId = "cluster-charlie",
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 3,
            clusterPath = listOf("cluster-alpha"),
            timestampMs = 5000L
        )
        val bridgePathB = SignalingMessage.ClusterBridge(
            bridgeId = "path-b",
            sourceClusterId = "cluster-beta",
            targetClusterId = "cluster-charlie",
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = payloadBase64,
            hopLimit = 2,
            clusterPath = listOf("cluster-beta"),
            timestampMs = 5010L
        )

        // First path processes successfully
        bridge.handleIncomingClusterBridge(bridgePathA)
        assertEquals(1L, bridge.stats.value.packetsReceivedFromCloud)
        assertEquals(0L, bridge.stats.value.duplicatePacketsDropped)

        // Second path with same packet is dropped as duplicate
        bridge.handleIncomingClusterBridge(bridgePathB)
        assertEquals(1L, bridge.stats.value.packetsReceivedFromCloud)
        assertEquals(1L, bridge.stats.value.duplicatePacketsDropped)
    }
}
