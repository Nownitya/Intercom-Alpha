package org.nowni.intercom_alpha.signaling

import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.protocol.SignalingMessage
import java.util.concurrent.ConcurrentHashMap

class SignalingServerManager {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private val activeSessions = ConcurrentHashMap<String, PeerSession>()
    private val roomMembers = ConcurrentHashMap<String, ConcurrentHashMap.KeySetView<String, Boolean>>()
    private val mutex = Mutex()

    suspend fun registerPeer(peerSession: PeerSession) {
        val peerId = peerSession.info.peerId
        activeSessions[peerId] = peerSession

        val activePeersList = activeSessions.values.map { it.info }
        val activeRoomsList = roomMembers.keys.toList()

        val response = SignalingMessage.Registered(
            peerId = peerId,
            activePeers = activePeersList,
            activeRooms = activeRoomsList
        )
        sendToPeer(peerId, response)
    }

    suspend fun handleMessage(fromPeerId: String, textMessage: String) {
        val message = try {
            json.decodeFromString<SignalingMessage>(textMessage)
        } catch (e: Exception) {
            sendToPeer(fromPeerId, SignalingMessage.ErrorMessage(400, "Invalid JSON payload: ${e.message}"))
            return
        }

        when (message) {
            is SignalingMessage.Register -> {
                val existing = activeSessions[fromPeerId]
                if (existing != null) {
                    existing.info = existing.info.copy(
                        displayName = message.displayName,
                        platform = message.platform
                    )
                    registerPeer(existing)
                }
            }
            is SignalingMessage.JoinRoom -> {
                joinRoom(fromPeerId, message.roomId)
            }
            is SignalingMessage.LeaveRoom -> {
                leaveRoom(fromPeerId, message.roomId)
            }
            is SignalingMessage.Offer -> {
                sendToPeer(message.toPeerId, message)
            }
            is SignalingMessage.Answer -> {
                sendToPeer(message.toPeerId, message)
            }
            is SignalingMessage.IceCandidate -> {
                sendToPeer(message.toPeerId, message)
            }
            is SignalingMessage.TransmissionState -> {
                val session = activeSessions[fromPeerId]
                if (session != null) {
                    session.info = session.info.copy(isTransmitting = message.isTransmitting)
                    broadcastToRoom(message.roomId, message)
                }
            }
            is SignalingMessage.AudioPing -> {
                sendToPeer(fromPeerId, SignalingMessage.AudioPong(fromPeerId, message.timestampMs))
            }
            is SignalingMessage.MeshRelay -> {
                broadcastToRoom(message.roomId, message, excludePeerId = fromPeerId)
            }
            else -> {
                // Unknown or unhandled message type
            }
        }
    }

    private suspend fun joinRoom(peerId: String, roomId: String) {
        mutex.withLock {
            val session = activeSessions[peerId] ?: return
            val oldRoom = session.info.currentRoom
            if (oldRoom != null && oldRoom != roomId) {
                leaveRoomInternal(peerId, oldRoom)
            }

            session.info = session.info.copy(currentRoom = roomId)
            val members = roomMembers.computeIfAbsent(roomId) { ConcurrentHashMap.newKeySet() }
            members.add(peerId)

            val memberInfos = members.mapNotNull { activeSessions[it]?.info }
            val roomState = SignalingMessage.RoomState(roomId, memberInfos)

            // Broadcast to room members that a new peer joined
            broadcastToRoom(roomId, SignalingMessage.PeerJoined(roomId, session.info))
            sendToPeer(peerId, roomState)
        }
    }

    private suspend fun leaveRoom(peerId: String, roomId: String) {
        mutex.withLock {
            leaveRoomInternal(peerId, roomId)
        }
    }

    private suspend fun leaveRoomInternal(peerId: String, roomId: String) {
        val session = activeSessions[peerId]
        if (session != null) {
            session.info = session.info.copy(currentRoom = null)
        }

        val members = roomMembers[roomId]
        if (members != null) {
            members.remove(peerId)
            if (members.isEmpty()) {
                roomMembers.remove(roomId)
            } else {
                broadcastToRoom(roomId, SignalingMessage.PeerLeft(roomId, peerId))
            }
        }
    }

    suspend fun unregisterPeer(peerId: String) {
        mutex.withLock {
            val session = activeSessions.remove(peerId)
            val currentRoom = session?.info?.currentRoom
            if (currentRoom != null) {
                leaveRoomInternal(peerId, currentRoom)
            }
        }
    }

    suspend fun sendToPeer(peerId: String, message: SignalingMessage) {
        val session = activeSessions[peerId] ?: return
        val text = json.encodeToString(message)
        try {
            session.session.send(Frame.Text(text))
        } catch (_: Exception) {
            // Socket write failed
        }
    }

    private suspend fun broadcastToRoom(roomId: String, message: SignalingMessage, excludePeerId: String? = null) {
        val members = roomMembers[roomId] ?: return
        val text = json.encodeToString(message)
        members.forEach { memberId ->
            if (memberId != excludePeerId) {
                val session = activeSessions[memberId]
                if (session != null) {
                    try {
                        session.session.send(Frame.Text(text))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun getMetrics(): Map<String, Any> {
        return mapOf(
            "activePeers" to activeSessions.size,
            "activeRooms" to roomMembers.size,
            "rooms" to roomMembers.mapValues { it.value.size }
        )
    }
}
