package org.nowni.intercom_alpha.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalingProtocolTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testRegisterMessageSerialization() {
        val register: SignalingMessage = SignalingMessage.Register(
            peerId = "peer-123",
            displayName = "Alice",
            platform = "Android"
        )
        val encoded = json.encodeToString(register)
        assertTrue(encoded.contains("Register"))
        
        val decoded = json.decodeFromString<SignalingMessage>(encoded)
        assertEquals(register, decoded)
    }

    @Test
    fun testOfferMessageSerialization() {
        val offer: SignalingMessage = SignalingMessage.Offer(
            fromPeerId = "peer-1",
            toPeerId = "peer-2",
            sdp = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-"
        )
        val encoded = json.encodeToString(offer)
        val decoded = json.decodeFromString<SignalingMessage>(encoded)
        assertEquals(offer, decoded)
    }

    @Test
    fun testTransmissionStateSerialization() {
        val state: SignalingMessage = SignalingMessage.TransmissionState(
            peerId = "peer-1",
            roomId = "MainRoom",
            isTransmitting = true
        )
        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<SignalingMessage>(encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun testMeshRelayMessageSerialization() {
        val relay: SignalingMessage = SignalingMessage.MeshRelay(
            roomId = "Convoy-Delta",
            fromPeerId = "rider-101",
            packetType = "AUDIO",
            payloadBase64 = "AQIDBAU=",
            timestampMs = 1727734800000L
        )
        val encoded = json.encodeToString(relay)
        assertTrue(encoded.contains("MeshRelay"))
        val decoded = json.decodeFromString<SignalingMessage>(encoded)
        assertEquals(relay, decoded)
    }

    @Test
    fun testClusterBridgeMessageSerialization() {
        val bridge: SignalingMessage = SignalingMessage.ClusterBridge(
            bridgeId = "bridge-alpha-beta",
            sourceClusterId = "cluster-alpha",
            targetClusterId = "cluster-beta",
            targetRoomId = "Convoy-1",
            packetType = "AUDIO",
            payloadBase64 = "UklGRg==",
            hopLimit = 2,
            clusterPath = listOf("cluster-alpha"),
            memberPresence = listOf(
                PeerInfo(
                    peerId = "peer-101",
                    displayName = "Lead",
                    platform = "Android",
                    currentRoom = "Convoy-1",
                    clusterId = "cluster-alpha"
                )
            ),
            timestampMs = 1727734900000L
        )

        val encoded = DefaultSignalingJson.encodeToString(bridge)
        assertTrue(encoded.contains("\"#type\":\"ClusterBridge\"") || encoded.contains("\"#type\": \"ClusterBridge\""))
        assertTrue(encoded.contains("cluster-alpha"))
        assertTrue(encoded.contains("cluster-beta"))

        val decoded = DefaultSignalingJson.decodeFromString<SignalingMessage>(encoded)
        assertEquals(bridge, decoded)
    }

    @Test
    fun testClassDiscriminatorUsesPoundType() {
        val ping: SignalingMessage = SignalingMessage.AudioPing(
            fromPeerId = "peer-alpha",
            timestampMs = 12345L
        )
        val encoded = DefaultSignalingJson.encodeToString(ping)
        assertTrue(encoded.contains("\"#type\""))
        val decoded = DefaultSignalingJson.decodeFromString<SignalingMessage>(encoded)
        assertEquals(ping, decoded)
    }
}
