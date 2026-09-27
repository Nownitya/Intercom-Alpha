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
}
