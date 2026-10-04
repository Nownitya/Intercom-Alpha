package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.common.AudioProfile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class CloudRelayBridgeTest {

    private val meshJson = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    @Test
    fun testInitialStatsState() {
        val bridge = CloudRelayBridge()
        val stats = bridge.stats.value

        assertFalse(stats.isConnected)
        assertEquals(CloudRelayMode.HYBRID_ALWAYS, stats.currentMode)
        assertEquals(0L, stats.packetsForwardedToCloud)
        assertEquals(0L, stats.packetsReceivedFromCloud)
        assertEquals(0L, stats.duplicatePacketsDropped)
    }

    @Test
    fun testRelayModeSwitching() = runTest {
        val bridge = CloudRelayBridge()
        assertEquals(CloudRelayMode.HYBRID_ALWAYS, bridge.stats.value.currentMode)

        bridge.setRelayMode(CloudRelayMode.BLE_ONLY)
        assertEquals(CloudRelayMode.BLE_ONLY, bridge.stats.value.currentMode)

        bridge.setRelayMode(CloudRelayMode.FALLBACK_ONLY)
        assertEquals(CloudRelayMode.FALLBACK_ONLY, bridge.stats.value.currentMode)
    }

    @Test
    fun testBleOnlyModeSuppressesForwarding() = runTest {
        val bridge = CloudRelayBridge()
        bridge.setRelayMode(CloudRelayMode.BLE_ONLY)

        val packet = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 101L,
            timestamp = 1000L,
            data = byteArrayOf(0x01, 0x02, 0x03),
            profile = AudioProfile.MEDIUM
        )

        val forwarded = bridge.forwardToCloud(packet)
        assertFalse(forwarded)
        assertEquals(0L, bridge.stats.value.packetsForwardedToCloud)
    }

    @Test
    fun testPacketSerializationSymmetry() {
        val originalAudio = MeshPacket.Audio(
            senderId = "lead-rider",
            sequence = 42L,
            timestamp = 1727734800000L,
            data = byteArrayOf(0x10, 0x20, 0x30, 0x40),
            profile = AudioProfile.HIGH
        )

        val jsonStr = meshJson.encodeToString<MeshPacket>(originalAudio)
        val base64 = Base64.encode(jsonStr.encodeToByteArray())

        val decodedBytes = Base64.decode(base64)
        val decodedPacket = meshJson.decodeFromString<MeshPacket>(decodedBytes.decodeToString())

        assertTrue(decodedPacket is MeshPacket.Audio)
        assertEquals(originalAudio.senderId, decodedPacket.senderId)
        assertEquals(originalAudio.sequence, decodedPacket.sequence)
        assertEquals(originalAudio.timestamp, decodedPacket.timestamp)
        assertTrue(originalAudio.data.contentEquals(decodedPacket.data))
        assertEquals(originalAudio.profile, decodedPacket.profile)

        val originalControl = MeshPacket.Control(
            senderId = "rider-2",
            type = ControlType.PING,
            payload = byteArrayOf(0xAA.toByte())
        )
        val controlJson = meshJson.encodeToString<MeshPacket>(originalControl)
        val decodedControl = meshJson.decodeFromString<MeshPacket>(controlJson)
        assertTrue(decodedControl is MeshPacket.Control)
        assertEquals(ControlType.PING, decodedControl.type)
    }

    @Test
    fun testDeduplicatorPreventsDuplicateProcessing() = runTest {
        val deduplicator = PacketDeduplicator(windowMs = 5000L)
        val packet = MeshPacket.Audio(
            senderId = "rider-3",
            sequence = 777L,
            timestamp = 1000L,
            data = byteArrayOf(0x0A),
            profile = AudioProfile.LOW
        )

        // First arrival is novel
        assertTrue(deduplicator.shouldProcess(packet, now = 1000L))

        // Second arrival within window is a duplicate
        assertFalse(deduplicator.shouldProcess(packet, now = 1050L))
    }
}
