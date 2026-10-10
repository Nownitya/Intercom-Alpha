package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.common.AudioProfile

@Serializable
sealed class MeshPacket {
    @Serializable
    data class Audio(
        val senderId: String,
        val sequence: Long,
        val timestamp: Long,
        val data: ByteArray,
        val profile: AudioProfile
    ) : MeshPacket()

    @Serializable
    data class Control(
        val senderId: String,
        val type: ControlType,
        val payload: ByteArray = ByteArray(0)
    ) : MeshPacket()

    @Serializable
    data class Discovery(
        val senderId: String,
        val groupId: String,
        val peerName: String,
        val capabilities: Capabilities
    ) : MeshPacket()

    @Serializable
    data class Relay(
        val originalSenderId: String,
        val ttl: Int,
        val packet: MeshPacket
    ) : MeshPacket()
}

@Serializable
enum class ControlType {
    JOIN_REQUEST,
    JOIN_ACCEPT,
    JOIN_REJECT,
    LEAVE,
    PING,
    PONG,
    SYNC_REQUEST,
    SYNC_RESPONSE,
    REPEATER_BEACON,
    REPEATER_PING,
    REPEATER_PONG,
    EPOCH_ROLLOVER,
    EPOCH_BEACON,
    FILE_CHUNK,
    FILE_ACK,
    GEOFENCE_ALARM,
    POWER_STATUS,
    CRASH_ALERT,
    ROUTE_REQUEST,
    ROUTE_REPLY,
    ROUTE_ERROR,
    DIRECTED_DATA,
    FEDERATION_INVITE,
    FEDERATION_BRIDGE,
    FEDERATION_AUDIO
}

@Serializable
data class Capabilities(
    val supportsBle: Boolean = true,
    val supportsWifiDirect: Boolean = false,
    val supportsRelay: Boolean = true,
    val maxPeers: Int = 8
)

@Serializable
data class Peer(
    val id: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long,
    val isConnected: Boolean,
    val audioProfile: AudioProfile,
    val batteryLevel: Int = -1
)

interface MeshTransport {
    val peers: ReceiveChannel<List<Peer>>
    val incomingAudio: ReceiveChannel<MeshPacket.Audio>
    val incomingControl: ReceiveChannel<MeshPacket.Control>
    val connectionState: ReceiveChannel<ConnectionState>

    val qosScheduler: org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler? get() = null

    suspend fun start(groupId: String, peerName: String, config: MeshConfig)
    suspend fun stop()
    suspend fun sendAudio(data: ByteArray, profile: AudioProfile)
    suspend fun sendControl(type: ControlType, payload: ByteArray)
    fun updateConfig(config: MeshConfig)

    suspend fun sendPacket(packet: MeshPacket) {
        when (packet) {
            is MeshPacket.Audio -> sendAudio(packet.data, packet.profile)
            is MeshPacket.Control -> sendControl(packet.type, packet.payload)
            is MeshPacket.Discovery -> {}
            is MeshPacket.Relay -> {
                when (val inner = packet.packet) {
                    is MeshPacket.Audio -> sendAudio(inner.data, inner.profile)
                    is MeshPacket.Control -> sendControl(inner.type, inner.payload)
                    else -> {}
                }
            }
        }
    }

    companion object {
        const val GATT_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        const val GATT_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
        const val BLE_MANUFACTURER_ID = 0x0991
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

@Serializable
data class MeshConfig(
    val groupId: String,
    val peerName: String,
    val audioProfile: AudioProfile,
    val enableRelay: Boolean = true,
    val maxPeers: Int = 8,
    val preferWifiDirect: Boolean = false
)