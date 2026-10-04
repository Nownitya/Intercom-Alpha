package org.nowni.intercom_alpha.text

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshTextManagerTest {

    @Test
    fun testSendCannedMessage() = runTest {
        val manager = MeshTextManager("rider-alpha", "Alpha Rider")

        val sent = manager.sendCannedMessage(CannedMessageCode.FUEL_LOW)
        assertEquals("Need Fuel / Low Tank", sent.content)
        assertEquals(CannedMessageCode.FUEL_LOW, sent.cannedCode)
        assertEquals(MessageCategory.STATUS_UPDATE, sent.category)
        assertEquals(DeliveryStatus.SENT, sent.status)
        assertTrue(sent.isRead)
        assertTrue(sent.isBroadcast)

        val wireFrame = manager.outgoingFrames.receive()
        assertEquals(TextWireType.TEXT_PAYLOAD, wireFrame.wireType)
        assertEquals(sent.id, wireFrame.messageId)
        assertEquals("rider-alpha", wireFrame.senderId)
        assertEquals("Alpha Rider", wireFrame.senderName)
        assertEquals("Need Fuel / Low Tank", wireFrame.content)
    }

    @Test
    fun testSendHazardCannedMessageCategory() = runTest {
        val manager = MeshTextManager("rider-alpha", "Alpha Rider")

        val sent = manager.sendCannedMessage(CannedMessageCode.HAZARD_ROAD)
        assertEquals("Caution: Hazard in Road", sent.content)
        assertEquals(MessageCategory.CANNED_HAZARD, sent.category)
    }

    @Test
    fun testSendCustomText() = runTest {
        val manager = MeshTextManager("rider-alpha", "Alpha Rider")

        val sent = manager.sendCustomText("  Meet at mile marker 42  ", recipientId = "rider-bravo")
        assertEquals("Meet at mile marker 42", sent.content)
        assertEquals(MessageCategory.DIRECT_PING, sent.category)
        assertEquals("rider-bravo", sent.recipientId)
        assertFalse(sent.isBroadcast)

        val wire = manager.outgoingFrames.receive()
        assertEquals("rider-bravo", wire.recipientId)
        assertEquals("Meet at mile marker 42", wire.content)

        assertFailsWith<IllegalArgumentException> {
            manager.sendCustomText("   ")
        }
    }

    @Test
    fun testIncomingBroadcastMessage() = runTest {
        val manager = MeshTextManager("rider-bravo", "Bravo Rider")

        val frame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "msg-101",
            senderId = "rider-alpha",
            senderName = "Alpha Rider",
            recipientId = null,
            cannedCode = CannedMessageCode.STOP_IMMEDIATE,
            category = MessageCategory.CANNED_HAZARD,
            content = "Stop Immediately / Pull Over",
            timestamp = 1000L
        )

        val received = manager.handleIncomingWireFrame(frame)
        assertNotNull(received)
        assertEquals("msg-101", received.id)
        assertEquals("rider-alpha", received.senderId)
        assertEquals("Stop Immediately / Pull Over", received.content)
        assertEquals(DeliveryStatus.DELIVERED, received.status)
        assertFalse(received.isRead)

        assertEquals(1, manager.unreadCount.value)
        assertEquals(1, manager.messages.value.size)

        // Drop own messages
        val ownFrame = frame.copy(senderId = "rider-bravo")
        assertNull(manager.handleIncomingWireFrame(ownFrame))
    }

    @Test
    fun testIncomingDirectMessageAndAckGeneration() = runTest {
        val manager = MeshTextManager("rider-bravo", "Bravo Rider")

        val frame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "direct-404",
            senderId = "rider-alpha",
            senderName = "Alpha Rider",
            recipientId = "rider-bravo",
            content = "Check your rear tire",
            timestamp = 2000L
        )

        val received = manager.handleIncomingWireFrame(frame)
        assertNotNull(received)

        // Automatic ACK generation check
        val ack = manager.outgoingFrames.receive()
        assertEquals(TextWireType.DELIVERY_ACK, ack.wireType)
        assertEquals("direct-404", ack.messageId)
        assertEquals("rider-bravo", ack.senderId)
        assertEquals("rider-alpha", ack.recipientId)
    }

    @Test
    fun testIncomingAckUpdatesSentMessage() = runTest {
        val manager = MeshTextManager("rider-alpha", "Alpha Rider")

        val sent = manager.sendCustomText("Are you ready?", recipientId = "rider-bravo")
        assertEquals(DeliveryStatus.SENT, sent.status)
        manager.outgoingFrames.receive() // drain wire frame

        val ackFrame = MeshTextWireFrame(
            wireType = TextWireType.DELIVERY_ACK,
            messageId = sent.id,
            senderId = "rider-bravo",
            senderName = "Bravo Rider",
            recipientId = "rider-alpha",
            timestamp = 3500L
        )

        val updated = manager.handleIncomingWireFrame(ackFrame)
        assertNotNull(updated)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, updated.status)
        assertEquals(3500L, updated.acknowledgedAt)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, manager.messages.value.first().status)
    }

    @Test
    fun testIncomingMessageDropForDifferentRecipient() = runTest {
        val manager = MeshTextManager("rider-charlie", "Charlie Rider")

        val frame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "secret-1",
            senderId = "rider-alpha",
            senderName = "Alpha Rider",
            recipientId = "rider-bravo",
            content = "Private message"
        )

        val received = manager.handleIncomingWireFrame(frame)
        assertNull(received)
        assertTrue(manager.messages.value.isEmpty())
    }

    @Test
    fun testDuplicateMessageSuppression() = runTest {
        val manager = MeshTextManager("rider-bravo", "Bravo Rider")

        val frame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "dup-01",
            senderId = "rider-alpha",
            senderName = "Alpha Rider",
            content = "Duplicate test"
        )

        manager.handleIncomingWireFrame(frame)
        manager.handleIncomingWireFrame(frame)

        assertEquals(1, manager.messages.value.size)
        assertEquals(1, manager.unreadCount.value)
    }

    @Test
    fun testMarkAsReadAndMarkAll() = runTest {
        val manager = MeshTextManager("rider-bravo", "Bravo Rider")

        val frame1 = MeshTextWireFrame(TextWireType.TEXT_PAYLOAD, "m1", "rider-alpha", "Alpha", content = "1")
        val frame2 = MeshTextWireFrame(TextWireType.TEXT_PAYLOAD, "m2", "rider-alpha", "Alpha", content = "2")

        manager.handleIncomingWireFrame(frame1)
        manager.handleIncomingWireFrame(frame2)
        assertEquals(2, manager.unreadCount.value)

        manager.markAsRead("m1")
        assertEquals(1, manager.unreadCount.value)
        assertTrue(manager.messages.value.first { it.id == "m1" }.isRead)
        assertFalse(manager.messages.value.first { it.id == "m2" }.isRead)

        manager.markAllAsRead()
        assertEquals(0, manager.unreadCount.value)
        assertTrue(manager.messages.value.all { it.isRead })

        manager.clearHistory()
        assertTrue(manager.messages.value.isEmpty())
    }

    @Test
    fun testBoundedHistoryEviction() = runTest {
        val manager = MeshTextManager("rider-alpha", "Alpha", maxHistorySize = 3)

        manager.sendCustomText("Msg 1")
        manager.sendCustomText("Msg 2")
        manager.sendCustomText("Msg 3")
        assertEquals(3, manager.messages.value.size)

        manager.sendCustomText("Msg 4")
        assertEquals(3, manager.messages.value.size)
        assertEquals("Msg 2", manager.messages.value[0].content)
        assertEquals("Msg 3", manager.messages.value[1].content)
        assertEquals("Msg 4", manager.messages.value[2].content)
    }

    @Test
    fun testWireFrameSerializationSymmetry() {
        val frame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "test-frame-1",
            senderId = "rider-1",
            senderName = "Lead Scout",
            recipientId = "rider-2",
            cannedCode = CannedMessageCode.MECHANICAL_ISSUE,
            category = MessageCategory.CANNED_HAZARD,
            content = "Bike / Vehicle Issue",
            timestamp = 1700000000L
        )

        val bytes = MeshTextManager.encodeWireFrame(frame)
        val decoded = MeshTextManager.decodeWireFrame(bytes)
        assertEquals(frame, decoded)
    }
}
