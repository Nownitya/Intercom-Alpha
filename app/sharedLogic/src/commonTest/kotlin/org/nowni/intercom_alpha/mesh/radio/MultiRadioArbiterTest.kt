package org.nowni.intercom_alpha.mesh.radio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.*

class MultiRadioArbiterTest {

    @Test
    fun testDefaultStateInitializesWithBleMesh() = runTest {
        val arbiter = MultiRadioArbiter()
        val initialState = arbiter.state.value

        assertTrue(initialState.activeRadios.contains(RadioType.BLE_MESH))
        assertFalse(initialState.wifiDirectConnected)
        assertFalse(initialState.cellularConnected)
        assertEquals(RadioType.BLE_MESH, initialState.voicePreferredRadio)
        assertEquals(RadioType.BLE_MESH, initialState.bulkTransferPreferredRadio)
    }

    @Test
    fun testPayloadClassificationLogic() {
        val arbiter = MultiRadioArbiter()

        // Emergency
        val sosClass = arbiter.classifyPayload(sizeBytes = 64, isEmergency = true)
        assertEquals(PayloadBandwidthClass.EMERGENCY_CRITICAL, sosClass)

        // Voice
        val voiceClass = arbiter.classifyPayload(sizeBytes = 120, isVoice = true)
        assertEquals(PayloadBandwidthClass.VOICE_REALTIME, voiceClass)

        // Small control
        val controlClass = arbiter.classifyPayload(sizeBytes = 40, isControl = true)
        assertEquals(PayloadBandwidthClass.NETWORK_CONTROL, controlClass)

        // Bulk data (large payload)
        val bulkClass = arbiter.classifyPayload(sizeBytes = 1200)
        assertEquals(PayloadBandwidthClass.BULK_HIGH_BANDWIDTH, bulkClass)
    }

    @Test
    fun testMeshPacketClassification() {
        val arbiter = MultiRadioArbiter()

        // Audio packet
        val audioPacket = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 100L,
            timestamp = 1000L,
            data = ByteArray(80),
            profile = AudioProfile.HIGH
        )
        assertEquals(PayloadBandwidthClass.VOICE_REALTIME, arbiter.classifyPacket(audioPacket))

        // Crash Alert Control packet
        val crashPacket = MeshPacket.Control(
            senderId = "rider-2",
            type = ControlType.CRASH_ALERT,
            payload = ByteArray(32)
        )
        assertEquals(PayloadBandwidthClass.EMERGENCY_CRITICAL, arbiter.classifyPacket(crashPacket))

        // File Chunk Control packet
        val fileChunkPacket = MeshPacket.Control(
            senderId = "scout-1",
            type = ControlType.FILE_CHUNK,
            payload = ByteArray(100)
        )
        assertEquals(PayloadBandwidthClass.BULK_HIGH_BANDWIDTH, arbiter.classifyPacket(fileChunkPacket))

        // Relay wrapped Audio packet
        val relayPacket = MeshPacket.Relay(
            originalSenderId = "rider-1",
            ttl = 2,
            packet = audioPacket
        )
        assertEquals(PayloadBandwidthClass.VOICE_REALTIME, arbiter.classifyPacket(relayPacket))
    }

    @Test
    fun testBulkOffloadToWifiDirectWhenConnected() = runTest {
        val arbiter = MultiRadioArbiter()

        // Establish Wi-Fi Direct connection with high signal quality
        arbiter.updateLinkMetrics(
            RadioLinkMetrics(
                radioType = RadioType.WIFI_DIRECT,
                state = RadioLinkState.CONNECTED,
                peerId = "scout-lead",
                rssi = -62, // Strong signal > -80 dBm threshold
                estimatedBandwidthKbps = 25000,
                lastSeenTimestamp = 1000L
            )
        )

        val updatedState = arbiter.state.value
        assertTrue(updatedState.wifiDirectConnected)
        assertEquals("scout-lead", updatedState.wifiDirectPeerId)
        assertEquals(RadioType.WIFI_DIRECT, updatedState.bulkTransferPreferredRadio)

        // Arbitrate high-bandwidth bulk payload (tactical snapshot)
        val bulkDecision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.BULK_HIGH_BANDWIDTH,
            currentTimeMs = 1200L
        )

        assertEquals(RadioType.WIFI_DIRECT, bulkDecision.primaryRadio)
        assertNull(bulkDecision.hedgeRadio)
        assertTrue(bulkDecision.reason.contains("Wi-Fi Direct"))

        // Voice must remain anchored to BLE Mesh (zero channel contention)
        val voiceDecision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.VOICE_REALTIME,
            currentTimeMs = 1200L
        )

        assertEquals(RadioType.BLE_MESH, voiceDecision.primaryRadio)
        assertTrue(voiceDecision.reason.contains("low-latency BLE mesh"))
    }

    @Test
    fun testGracefulFallbackToBleWhenWifiDirectDisconnects() = runTest {
        val arbiter = MultiRadioArbiter()

        // Initially Wi-Fi Direct is connected
        arbiter.updateLinkMetrics(
            RadioLinkMetrics(
                radioType = RadioType.WIFI_DIRECT,
                state = RadioLinkState.CONNECTED,
                peerId = "scout-lead",
                rssi = -60,
                lastSeenTimestamp = 2000L
            )
        )

        var bulkDecision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.BULK_HIGH_BANDWIDTH,
            currentTimeMs = 2100L
        )
        assertEquals(RadioType.WIFI_DIRECT, bulkDecision.primaryRadio)

        // Wi-Fi Direct disconnects (peers drift apart)
        arbiter.notifyRadioDisconnected(RadioType.WIFI_DIRECT, currentTimeMs = 2500L)

        val stateAfterDisconnect = arbiter.state.value
        assertFalse(stateAfterDisconnect.wifiDirectConnected)
        assertEquals(RadioType.BLE_MESH, stateAfterDisconnect.bulkTransferPreferredRadio)

        // Bulk transfer gracefully falls back to BLE Mesh
        bulkDecision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.BULK_HIGH_BANDWIDTH,
            currentTimeMs = 2600L
        )

        assertEquals(RadioType.BLE_MESH, bulkDecision.primaryRadio)
        assertTrue(bulkDecision.reason.contains("falling back to BLE mesh"))
    }

    @Test
    fun testFallbackToBleWhenWifiDirectRssiDegradesBelowThreshold() = runTest {
        val arbiter = MultiRadioArbiter(
            config = MultiRadioArbiterConfig(wifiDirectRssiThreshold = -80)
        )

        // Signal degrades to -86 dBm (below -80 dBm threshold)
        arbiter.updateLinkMetrics(
            RadioLinkMetrics(
                radioType = RadioType.WIFI_DIRECT,
                state = RadioLinkState.DEGRADED,
                peerId = "scout-lead",
                rssi = -86,
                lastSeenTimestamp = 3000L
            )
        )

        val decision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.BULK_HIGH_BANDWIDTH,
            currentTimeMs = 3100L
        )

        // Must reject degraded Wi-Fi and route to BLE mesh
        assertEquals(RadioType.BLE_MESH, decision.primaryRadio)
        assertTrue(decision.reason.contains("below threshold"))
    }

    @Test
    fun testEmergencyCriticalRoutesToBleWithCellularHedge() = runTest {
        val arbiter = MultiRadioArbiter()

        // Cellular SFU connected
        arbiter.updateLinkMetrics(
            RadioLinkMetrics(
                radioType = RadioType.CELLULAR_SFU,
                state = RadioLinkState.CONNECTED,
                lastSeenTimestamp = 5000L
            )
        )

        val emergencyDecision = arbiter.arbitrateRoute(
            payloadClass = PayloadBandwidthClass.EMERGENCY_CRITICAL,
            currentTimeMs = 5100L
        )

        assertEquals(RadioType.BLE_MESH, emergencyDecision.primaryRadio)
        assertEquals(RadioType.CELLULAR_SFU, emergencyDecision.hedgeRadio)
        assertTrue(emergencyDecision.reason.contains("cellular hedging"))
    }
}
