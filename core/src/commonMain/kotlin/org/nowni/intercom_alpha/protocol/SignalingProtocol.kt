package org.nowni.intercom_alpha.protocol

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PeerInfo(
    val peerId: String,
    val displayName: String,
    val platform: String,
    val currentRoom: String? = null,
    val isTransmitting: Boolean = false
)

@Serializable
sealed class SignalingMessage {

    @Serializable
    @SerialName("Register")
    data class Register(
        val peerId: String,
        val displayName: String,
        val platform: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("Registered")
    data class Registered(
        val peerId: String,
        val activePeers: List<PeerInfo>,
        val activeRooms: List<String>
    ) : SignalingMessage()

    @Serializable
    @SerialName("JoinRoom")
    data class JoinRoom(
        val peerId: String,
        val roomId: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("LeaveRoom")
    data class LeaveRoom(
        val peerId: String,
        val roomId: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("RoomState")
    data class RoomState(
        val roomId: String,
        val members: List<PeerInfo>
    ) : SignalingMessage()

    @Serializable
    @SerialName("Offer")
    data class Offer(
        val fromPeerId: String,
        val toPeerId: String,
        val sdp: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("Answer")
    data class Answer(
        val fromPeerId: String,
        val toPeerId: String,
        val sdp: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("IceCandidate")
    data class IceCandidate(
        val fromPeerId: String,
        val toPeerId: String,
        val candidate: String,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null
    ) : SignalingMessage()

    @Serializable
    @SerialName("TransmissionState")
    data class TransmissionState(
        val peerId: String,
        val roomId: String,
        val isTransmitting: Boolean
    ) : SignalingMessage()

    @Serializable
    @SerialName("PeerJoined")
    data class PeerJoined(
        val roomId: String,
        val peer: PeerInfo
    ) : SignalingMessage()

    @Serializable
    @SerialName("PeerLeft")
    data class PeerLeft(
        val roomId: String,
        val peerId: String
    ) : SignalingMessage()

    @Serializable
    @SerialName("AudioPing")
    data class AudioPing(
        val fromPeerId: String,
        val timestampMs: Long
    ) : SignalingMessage()

    @Serializable
    @SerialName("AudioPong")
    data class AudioPong(
        val fromPeerId: String,
        val timestampMs: Long
    ) : SignalingMessage()

    @Serializable
    @SerialName("ErrorMessage")
    data class ErrorMessage(
        val code: Int,
        val message: String
    ) : SignalingMessage()
}
