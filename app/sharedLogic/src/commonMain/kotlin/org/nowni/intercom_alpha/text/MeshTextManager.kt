package org.nowni.intercom_alpha.text

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Standardized canned quick-action hazard codes for high-speed or noisy convoy riding.
 */
@Serializable
enum class CannedMessageCode {
    CUSTOM,
    FUEL_LOW,
    HAZARD_ROAD,
    STOP_IMMEDIATE,
    MECHANICAL_ISSUE,
    TURN_AROUND,
    REGROUP,
    ALL_CLEAR,
    NEED_ASSISTANCE,
    ROAD_POLICE_AHEAD,
    TAKING_NEXT_EXIT
}

/**
 * Functional category of mesh text transmission.
 */
@Serializable
enum class MessageCategory {
    CANNED_HAZARD,
    STATUS_UPDATE,
    DIRECT_PING,
    CUSTOM_TEXT
}

/**
 * Message delivery lifecycle tracking.
 */
@Serializable
enum class DeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    ACKNOWLEDGED,
    FAILED
}

/**
 * Rich domain model for conversational and hazard messages stored locally on device.
 */
@Serializable
data class MeshTextMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String? = null, // null denotes global convoy broadcast
    val cannedCode: CannedMessageCode = CannedMessageCode.CUSTOM,
    val category: MessageCategory = MessageCategory.CUSTOM_TEXT,
    val content: String,
    val timestamp: Long,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val isRead: Boolean = false,
    val acknowledgedAt: Long? = null
) {
    val isBroadcast: Boolean get() = recipientId == null
}

/**
 * Over-the-air packet type discriminator for mesh text payloads.
 */
@Serializable
enum class TextWireType {
    TEXT_PAYLOAD,
    DELIVERY_ACK
}

/**
 * Compact serializable wire frame transmitted across BLE mesh control characteristic.
 */
@Serializable
data class MeshTextWireFrame(
    val wireType: TextWireType,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val recipientId: String? = null,
    val cannedCode: CannedMessageCode = CannedMessageCode.CUSTOM,
    val category: MessageCategory = MessageCategory.CUSTOM_TEXT,
    val content: String = "",
    val timestamp: Long = 0L
)

/**
 * Decentralized offline mesh text and canned hazard messaging manager.
 *
 * Coordinates offline one-tap convoy alerts, custom chat payloads, automatic
 * receipt acknowledgments (ACKs), and bounded message history.
 */
class MeshTextManager(
    val localPeerId: String,
    val localPeerName: String,
    private val maxHistorySize: Int = 100
) {
    private val mutex = Mutex()
    private var sequenceCounter: Long = 0L

    private val _messages = MutableStateFlow<List<MeshTextMessage>>(emptyList())
    val messages: StateFlow<List<MeshTextMessage>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _outgoingFrames = Channel<MeshTextWireFrame>(Channel.BUFFERED)
    val outgoingFrames: ReceiveChannel<MeshTextWireFrame> = _outgoingFrames

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun getCannedText(code: CannedMessageCode): String = when (code) {
            CannedMessageCode.FUEL_LOW -> "Need Fuel / Low Tank"
            CannedMessageCode.HAZARD_ROAD -> "Caution: Hazard in Road"
            CannedMessageCode.STOP_IMMEDIATE -> "Stop Immediately / Pull Over"
            CannedMessageCode.MECHANICAL_ISSUE -> "Bike / Vehicle Issue"
            CannedMessageCode.TURN_AROUND -> "Turn Around / Missed Route"
            CannedMessageCode.REGROUP -> "Regroup at Next Safe Spot"
            CannedMessageCode.ALL_CLEAR -> "All Clear / Road Safe"
            CannedMessageCode.NEED_ASSISTANCE -> "Need Rider Assistance"
            CannedMessageCode.ROAD_POLICE_AHEAD -> "Notice: Checkpoint Ahead"
            CannedMessageCode.TAKING_NEXT_EXIT -> "Taking Next Exit"
            CannedMessageCode.CUSTOM -> ""
        }

        fun encodeWireFrame(frame: MeshTextWireFrame): ByteArray =
            json.encodeToString(MeshTextWireFrame.serializer(), frame).encodeToByteArray()

        fun decodeWireFrame(bytes: ByteArray): MeshTextWireFrame =
            json.decodeFromString(MeshTextWireFrame.serializer(), bytes.decodeToString())
    }

    /**
     * Send a standardized canned convoy hazard or quick-status message.
     */
    suspend fun sendCannedMessage(
        code: CannedMessageCode,
        recipientId: String? = null
    ): MeshTextMessage = mutex.withLock {
        val now = currentTimeMillis()
        val msgId = generateMessageId(now)
        val text = getCannedText(code)
        val category = when (code) {
            CannedMessageCode.HAZARD_ROAD,
            CannedMessageCode.STOP_IMMEDIATE,
            CannedMessageCode.NEED_ASSISTANCE -> MessageCategory.CANNED_HAZARD
            else -> MessageCategory.STATUS_UPDATE
        }

        val msg = MeshTextMessage(
            id = msgId,
            senderId = localPeerId,
            senderName = localPeerName,
            recipientId = recipientId,
            cannedCode = code,
            category = category,
            content = text,
            timestamp = now,
            status = DeliveryStatus.SENT,
            isRead = true
        )

        val wireFrame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = msgId,
            senderId = localPeerId,
            senderName = localPeerName,
            recipientId = recipientId,
            cannedCode = code,
            category = category,
            content = text,
            timestamp = now
        )

        appendMessageInternal(msg)
        _outgoingFrames.send(wireFrame)
        msg
    }

    /**
     * Send a custom text message across the mesh (max 280 characters).
     */
    suspend fun sendCustomText(
        text: String,
        recipientId: String? = null
    ): MeshTextMessage = mutex.withLock {
        val trimmed = text.trim().take(280)
        require(trimmed.isNotEmpty()) { "Message content cannot be blank" }

        val now = currentTimeMillis()
        val msgId = generateMessageId(now)
        val category = if (recipientId != null) MessageCategory.DIRECT_PING else MessageCategory.CUSTOM_TEXT

        val msg = MeshTextMessage(
            id = msgId,
            senderId = localPeerId,
            senderName = localPeerName,
            recipientId = recipientId,
            cannedCode = CannedMessageCode.CUSTOM,
            category = category,
            content = trimmed,
            timestamp = now,
            status = DeliveryStatus.SENT,
            isRead = true
        )

        val wireFrame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = msgId,
            senderId = localPeerId,
            senderName = localPeerName,
            recipientId = recipientId,
            cannedCode = CannedMessageCode.CUSTOM,
            category = category,
            content = trimmed,
            timestamp = now
        )

        appendMessageInternal(msg)
        _outgoingFrames.send(wireFrame)
        msg
    }

    /**
     * Ingest an incoming wire frame received from the mesh transport.
     */
    suspend fun handleIncomingWireFrame(frame: MeshTextWireFrame): MeshTextMessage? = mutex.withLock {
        // Drop frames sent by ourselves
        if (frame.senderId == localPeerId) return@withLock null

        when (frame.wireType) {
            TextWireType.DELIVERY_ACK -> {
                // Process receipt acknowledgment for a message sent by us
                val existing = _messages.value.find { it.id == frame.messageId }
                if (existing != null && existing.senderId == localPeerId) {
                    val updated = existing.copy(
                        status = DeliveryStatus.ACKNOWLEDGED,
                        acknowledgedAt = frame.timestamp
                    )
                    _messages.value = _messages.value.map { if (it.id == frame.messageId) updated else it }
                    return@withLock updated
                }
                null
            }

            TextWireType.TEXT_PAYLOAD -> {
                // Verify recipient: must be broadcast (null) or addressed specifically to local peer
                if (frame.recipientId != null && frame.recipientId != localPeerId) {
                    return@withLock null
                }

                // Check for duplicate arrivals
                val existing = _messages.value.find { it.id == frame.messageId }
                if (existing != null) {
                    // If addressed to us, ensure the remote sender received the ACK
                    if (frame.recipientId == localPeerId) {
                        sendAckInternal(frame.messageId, frame.senderId)
                    }
                    return@withLock existing
                }

                val now = currentTimeMillis()
                val incomingMsg = MeshTextMessage(
                    id = frame.messageId,
                    senderId = frame.senderId,
                    senderName = frame.senderName,
                    recipientId = frame.recipientId,
                    cannedCode = frame.cannedCode,
                    category = frame.category,
                    content = frame.content,
                    timestamp = frame.timestamp,
                    status = DeliveryStatus.DELIVERED,
                    isRead = false
                )

                appendMessageInternal(incomingMsg)
                _unreadCount.value += 1

                // If addressed directly to local peer, emit an automatic ACK receipt
                if (frame.recipientId == localPeerId) {
                    sendAckInternal(frame.messageId, frame.senderId)
                }

                incomingMsg
            }
        }
    }

    /**
     * Mark a specific message as read and adjust unread counter.
     */
    suspend fun markAsRead(messageId: String) = mutex.withLock {
        var unreadDelta = 0
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId && !msg.isRead) {
                unreadDelta++
                msg.copy(isRead = true)
            } else {
                msg
            }
        }
        if (unreadDelta > 0) {
            _unreadCount.value = (_unreadCount.value - unreadDelta).coerceAtLeast(0)
        }
    }

    /**
     * Mark all stored messages as read.
     */
    suspend fun markAllAsRead() = mutex.withLock {
        _messages.value = _messages.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
    }

    /**
     * Clear message history.
     */
    suspend fun clearHistory() = mutex.withLock {
        _messages.value = emptyList()
        _unreadCount.value = 0
    }

    private fun generateMessageId(timestamp: Long): String {
        sequenceCounter++
        return "${localPeerId}_${timestamp}_$sequenceCounter"
    }

    private fun appendMessageInternal(message: MeshTextMessage) {
        val current = _messages.value
        val updated = if (current.size >= maxHistorySize) {
            current.drop(current.size - maxHistorySize + 1) + message
        } else {
            current + message
        }
        _messages.value = updated
    }

    private suspend fun sendAckInternal(messageId: String, targetSenderId: String) {
        val ackFrame = MeshTextWireFrame(
            wireType = TextWireType.DELIVERY_ACK,
            messageId = messageId,
            senderId = localPeerId,
            senderName = localPeerName,
            recipientId = targetSenderId,
            timestamp = currentTimeMillis()
        )
        _outgoingFrames.send(ackFrame)
    }
}
