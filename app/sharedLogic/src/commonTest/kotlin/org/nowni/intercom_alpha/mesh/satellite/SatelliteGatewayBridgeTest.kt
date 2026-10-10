package org.nowni.intercom_alpha.mesh.satellite

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SatelliteGatewayBridgeTest {

    @Test
    fun testCrc16DeterministicAndTamperDetection() {
        val sampleData = "IRIDIUM-SBD-TEST-PAYLOAD-ALPHA".encodeToByteArray()
        val crc1 = SatelliteCrc16.calculate(sampleData)
        val crc2 = SatelliteCrc16.calculate(sampleData)
        assertEquals(crc1, crc2)

        val tamperedData = sampleData.copyOf()
        tamperedData[3] = (tamperedData[3].toInt() xor 0xFF).toByte()
        val crcTampered = SatelliteCrc16.calculate(tamperedData)
        assertTrue(crc1 != crcTampered)
    }

    @Test
    fun testEncodeDecodeSbdFrameRoundtrip() {
        val payload = SatelliteDispatchPayload(
            dispatchId = "sat-99210-scout",
            senderRiderId = "rider-lead-01",
            groupId = "group-alps-09",
            timestamp = 1728560000000L,
            priority = SatelliteDispatchPriority.LIFE_SAFETY_CRASH,
            location = SatelliteGpsLocation(
                latitude = 45.8326,
                longitude = 6.8652,
                altitudeMeters = 3842.0,
                accuracyMeters = 3.5f
            ),
            impactGForce = 7.8f,
            batteryPercent = 84
        )

        val wireFrame = SatelliteGatewayBridge.encodeSbdFrame(payload)
        assertTrue(wireFrame.size <= 120, "Iridium SBD frame must fit within 120 bytes (got ${wireFrame.size})")

        val decoded = SatelliteGatewayBridge.decodeSbdFrame(wireFrame)
        assertNotNull(decoded)
        assertEquals(payload.dispatchId, decoded.dispatchId)
        assertEquals(payload.senderRiderId, decoded.senderRiderId)
        assertEquals(payload.groupId, decoded.groupId)
        assertEquals(payload.timestamp, decoded.timestamp)
        assertEquals(payload.priority, decoded.priority)
        assertEquals(payload.location.latitude, decoded.location.latitude, 0.0001)
        assertEquals(payload.location.longitude, decoded.location.longitude, 0.0001)
        assertEquals(3842.0, decoded.location.altitudeMeters, 1.0)
        assertNotNull(decoded.impactGForce)
        assertEquals(7.8f, decoded.impactGForce, 0.01f)
        assertEquals(84, decoded.batteryPercent)
    }

    @Test
    fun testDecodeRejectsCorruptedCrc() {
        val payload = SatelliteDispatchPayload(
            dispatchId = "sat-11223-lead",
            senderRiderId = "rider-02",
            groupId = "group-01",
            timestamp = 100_000L,
            priority = SatelliteDispatchPriority.EMERGENCY_SOS_MANUAL,
            location = SatelliteGpsLocation(46.0, 7.0, 2000.0)
        )

        val validWire = SatelliteGatewayBridge.encodeSbdFrame(payload)
        val corruptedWire = validWire.copyOf()
        // Corrupt payload byte
        corruptedWire[10] = (corruptedWire[10].toInt() xor 0xAA).toByte()

        val decoded = SatelliteGatewayBridge.decodeSbdFrame(corruptedWire)
        assertNull(decoded, "Corrupted wire payload must be rejected by CRC-16 check")
    }

    @Test
    fun testBridgeQueueAndDispatchLifecycle() = runTest {
        var simulatedTime = 50_000L
        val bridge = SatelliteGatewayBridge(
            localNodeId = "rider-lead-01",
            timeProvider = { simulatedTime }
        )

        assertEquals(SatelliteLinkState.OFFLINE, bridge.status.value.linkState)

        val dispatchedBytes = mutableListOf<ByteArray>()
        bridge.rawTransceiverSend = { bytes ->
            dispatchedBytes.add(bytes)
            true
        }

        val payload = SatelliteDispatchPayload(
            dispatchId = "sat-evt-001",
            senderRiderId = "rider-lead-01",
            groupId = "group-alpine",
            timestamp = simulatedTime,
            priority = SatelliteDispatchPriority.LIFE_SAFETY_CRASH,
            location = SatelliteGpsLocation(45.923, 6.868, 2800.0),
            impactGForce = 6.2f
        )

        bridge.enqueueEmergencyDispatch(payload)
        assertEquals(1, bridge.status.value.pendingQueueSize)

        // Process queue
        val sentCount = bridge.processQueue()
        assertEquals(1, sentCount)
        assertEquals(1, dispatchedBytes.size)
        assertEquals(SatelliteLinkState.TRANSMIT_SUCCESS, bridge.status.value.linkState)
        assertEquals(1, bridge.status.value.totalDispatchedCount)
        assertEquals(0, bridge.status.value.pendingQueueSize)

        // Ground station ACK
        simulatedTime += 2000L
        val ack = SatelliteDispatchAck(
            dispatchId = "sat-evt-001",
            constellationTimestamp = simulatedTime,
            sbdSequenceNumber = 9012L,
            isDeliveredToRescueCenter = true
        )
        val ackHandled = bridge.handleIncomingSatelliteAck(ack)
        assertTrue(ackHandled)
        assertEquals(1, bridge.status.value.totalAcksReceivedCount)
    }

    @Test
    fun testBridgeRetryOnTransceiverFailure() = runTest {
        val bridge = SatelliteGatewayBridge(
            localNodeId = "rider-scout",
            maxRetries = 2
        )

        var attempts = 0
        bridge.rawTransceiverSend = {
            attempts++
            false // Simulate modem link failure
        }

        val payload = SatelliteDispatchPayload(
            dispatchId = "sat-retry-01",
            senderRiderId = "rider-scout",
            groupId = "group-01",
            timestamp = 1000L,
            priority = SatelliteDispatchPriority.CONVOY_LOST_BEACON,
            location = SatelliteGpsLocation(45.0, 6.5)
        )

        bridge.enqueueEmergencyDispatch(payload)

        // Attempt 1 -> fails, reenqueued with retryCount 1
        bridge.processQueue()
        assertEquals(SatelliteLinkState.TRANSMIT_FAILED, bridge.status.value.linkState)
        assertEquals(1, bridge.status.value.pendingQueueSize)

        // Attempt 2 -> fails, reenqueued with retryCount 2
        bridge.processQueue()
        assertEquals(1, bridge.status.value.pendingQueueSize)

        // Attempt 3 -> exceeds maxRetries 2, dropped
        bridge.processQueue()
        assertEquals(0, bridge.status.value.pendingQueueSize)
        assertEquals(3, attempts)
    }

    @Test
    fun testConvertCrashAlertToSatelliteDispatch() {
        val bridge = SatelliteGatewayBridge(localNodeId = "rider-lead")
        val crashPacket = MeshPacket.Control(
            senderId = "rider-fallen",
            type = ControlType.CRASH_ALERT
        )

        val dispatch = bridge.convertEmergencyControlPacket(
            packet = crashPacket,
            groupId = "expedition-mont-blanc",
            latitude = 45.83,
            longitude = 6.86,
            altitudeMeters = 3800.0,
            impactGForce = 8.1f
        )

        assertNotNull(dispatch)
        assertEquals(SatelliteDispatchPriority.LIFE_SAFETY_CRASH, dispatch.priority)
        assertEquals("rider-fallen", dispatch.senderRiderId)
        assertEquals("expedition-mont-blanc", dispatch.groupId)
        assertEquals(8.1f, dispatch.impactGForce)
    }
}
