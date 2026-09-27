package org.nowni.intercom_alpha.signaling

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.protocol.SignalingMessage

sealed class SignalingConnectionState {
    object Disconnected : SignalingConnectionState()
    object Connecting : SignalingConnectionState()
    data class Connected(val peerId: String, val activePeers: List<PeerInfo>) : SignalingConnectionState()
    data class Error(val message: String) : SignalingConnectionState()
}

class IntercomSignalingClient(
    private val client: HttpClient = HttpClient { install(WebSockets) }
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private val _connectionState = MutableStateFlow<SignalingConnectionState>(SignalingConnectionState.Disconnected)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SignalingMessage> = _incomingMessages.asSharedFlow()

    private val _currentRoomState = MutableStateFlow<SignalingMessage.RoomState?>(null)
    val currentRoomState: StateFlow<SignalingMessage.RoomState?> = _currentRoomState.asStateFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var clientScope: CoroutineScope? = null

    suspend fun connect(
        host: String,
        port: Int,
        peerId: String,
        displayName: String,
        platform: String
    ) {
        if (_connectionState.value is SignalingConnectionState.Connected) return

        _connectionState.value = SignalingConnectionState.Connecting

        try {
            clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            session = client.webSocketSession(
                host = host,
                port = port,
                path = "/ws/intercom?peerId=$peerId&name=$displayName&platform=$platform"
            )

            _connectionState.value = SignalingConnectionState.Connected(peerId, emptyList())

            clientScope?.launch {
                listenIncoming()
            }
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.Error("Connection failed: ${e.message}")
        }
    }

    private suspend fun listenIncoming() {
        val currentSession = session ?: return
        try {
            for (frame in currentSession.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val message = json.decodeFromString<SignalingMessage>(text)

                    when (message) {
                        is SignalingMessage.Registered -> {
                            _connectionState.value = SignalingConnectionState.Connected(message.peerId, message.activePeers)
                        }
                        is SignalingMessage.RoomState -> {
                            _currentRoomState.value = message
                        }
                        is SignalingMessage.PeerJoined -> {
                            val cur = _currentRoomState.value
                            if (cur != null && cur.roomId == message.roomId) {
                                val updated = cur.members + message.peer
                                _currentRoomState.value = cur.copy(members = updated)
                            }
                        }
                        is SignalingMessage.PeerLeft -> {
                            val cur = _currentRoomState.value
                            if (cur != null && cur.roomId == message.roomId) {
                                val updated = cur.members.filterNot { it.peerId == message.peerId }
                                _currentRoomState.value = cur.copy(members = updated)
                            }
                        }
                        else -> {
                            // Forward all other signaling messages (Offer, Answer, ICE, etc.)
                        }
                    }
                    _incomingMessages.emit(message)
                }
            }
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.Error("Session error: ${e.message}")
        } finally {
            _connectionState.value = SignalingConnectionState.Disconnected
            _currentRoomState.value = null
        }
    }

    suspend fun joinRoom(peerId: String, roomId: String) {
        sendMessage(SignalingMessage.JoinRoom(peerId, roomId))
    }

    suspend fun leaveRoom(peerId: String, roomId: String) {
        sendMessage(SignalingMessage.LeaveRoom(peerId, roomId))
    }

    suspend fun sendTransmissionState(peerId: String, roomId: String, isTransmitting: Boolean) {
        sendMessage(SignalingMessage.TransmissionState(peerId, roomId, isTransmitting))
    }

    suspend fun sendOffer(fromPeerId: String, toPeerId: String, sdp: String) {
        sendMessage(SignalingMessage.Offer(fromPeerId, toPeerId, sdp))
    }

    suspend fun sendAnswer(fromPeerId: String, toPeerId: String, sdp: String) {
        sendMessage(SignalingMessage.Answer(fromPeerId, toPeerId, sdp))
    }

    suspend fun sendIceCandidate(fromPeerId: String, toPeerId: String, candidate: String, sdpMid: String? = null, sdpMLineIndex: Int? = null) {
        sendMessage(SignalingMessage.IceCandidate(fromPeerId, toPeerId, candidate, sdpMid, sdpMLineIndex))
    }

    suspend fun sendMessage(message: SignalingMessage) {
        val currentSession = session ?: return
        try {
            val text = json.encodeToString(message)
            currentSession.send(Frame.Text(text))
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.Error("Send failed: ${e.message}")
        }
    }

    suspend fun disconnect() {
        try {
            session?.close()
        } catch (_: Exception) {}
        clientScope?.cancel()
        session = null
        _connectionState.value = SignalingConnectionState.Disconnected
        _currentRoomState.value = null
    }
}
