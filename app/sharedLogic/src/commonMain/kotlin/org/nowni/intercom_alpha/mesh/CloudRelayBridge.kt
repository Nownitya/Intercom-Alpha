package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.protocol.SignalingMessage
import org.nowni.intercom_alpha.signaling.IntercomSignalingClient
import org.nowni.intercom_alpha.signaling.SignalingConnectionState
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Operating mode for the cloud relay bridge.
 */
@Serializable
enum class CloudRelayMode {
    /** Cloud relay disabled; all packets remain on local BLE mesh */
    BLE_ONLY,

    /** Cloud relay active only as fallback when local BLE links degrade */
    FALLBACK_ONLY,

    /** Hybrid dual-transport: simultaneously forward packets to BLE mesh and cloud relay */
    HYBRID_ALWAYS
}

/**
 * Real-time telemetry snapshot of cloud relay activity.
 */
@Serializable
data class CloudRelayStats(
    val isConnected: Boolean = false,
    val currentMode: CloudRelayMode = CloudRelayMode.HYBRID_ALWAYS,
    val packetsForwardedToCloud: Long = 0L,
    val packetsReceivedFromCloud: Long = 0L,
    val duplicatePacketsDropped: Long = 0L,
    val activeRoomId: String? = null,
    val lastRelayedTimestampMs: Long = 0L
)

/**
 * Bridges local BLE mesh clusters across wide-area cellular networks via Ktor WebSocket signaling.
 *
 * Implements ADR-009 cloud relay fallback, serializing and deserializing encrypted [MeshPacket] frames
 * and deduplicating cross-network packet echoes using [PacketDeduplicator].
 */
@OptIn(ExperimentalEncodingApi::class)
class CloudRelayBridge(
    private val signalingClient: IntercomSignalingClient = IntercomSignalingClient(),
    private val deduplicator: PacketDeduplicator = PacketDeduplicator()
) {
    // Gotcha 2: Always supply #type discriminator to avoid collision with MeshPacket.Control.type
    private val meshJson = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val mutex = Mutex()
    private var bridgeScope: CoroutineScope? = null

    private val _incomingCloudPackets = Channel<MeshPacket>(Channel.BUFFERED)
    val incomingCloudPackets: ReceiveChannel<MeshPacket> = _incomingCloudPackets

    private val _stats = MutableStateFlow(CloudRelayStats())
    val stats: StateFlow<CloudRelayStats> = _stats.asStateFlow()

    private var activeRoomId: String? = null
    private var localPeerId: String? = null
    private var currentMode: CloudRelayMode = CloudRelayMode.HYBRID_ALWAYS

    /**
     * Initializes and connects the cloud relay gateway.
     */
    suspend fun start(
        host: String,
        port: Int,
        peerId: String,
        displayName: String,
        platform: String,
        roomId: String,
        mode: CloudRelayMode = CloudRelayMode.HYBRID_ALWAYS
    ) {
        mutex.withLock {
            localPeerId = peerId
            activeRoomId = roomId
            currentMode = mode

            bridgeScope?.cancel()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            bridgeScope = scope

            // Observe signaling connection state
            scope.launch {
                signalingClient.connectionState.collect { connState ->
                    val connected = connState is SignalingConnectionState.Connected
                    _stats.value = _stats.value.copy(
                        isConnected = connected,
                        currentMode = currentMode,
                        activeRoomId = if (connected) activeRoomId else null
                    )
                }
            }

            // Ingest and deduplicate remote mesh packets
            scope.launch {
                signalingClient.incomingMessages.collect { message ->
                    if (message is SignalingMessage.MeshRelay) {
                        handleIncomingRelay(message)
                    }
                }
            }

            // Connect to server and join the mesh room
            signalingClient.connect(host, port, peerId, displayName, platform)
            signalingClient.joinRoom(peerId, roomId)
        }
    }

    /**
     * Forwards an outgoing local [MeshPacket] to remote mesh nodes via cloud relay.
     *
     * @return `true` if forwarded, `false` if skipped due to mode or disconnection.
     */
    suspend fun forwardToCloud(packet: MeshPacket): Boolean {
        if (currentMode == CloudRelayMode.BLE_ONLY) {
            return false
        }

        val roomId = activeRoomId ?: return false
        val peerId = localPeerId ?: return false

        if (!_stats.value.isConnected) {
            return false
        }

        return try {
            val jsonString = meshJson.encodeToString(packet)
            val base64Payload = Base64.encode(jsonString.encodeToByteArray())
            val packetType = when (packet) {
                is MeshPacket.Audio -> "AUDIO"
                is MeshPacket.Control -> "CONTROL"
                is MeshPacket.Discovery -> "DISCOVERY"
                is MeshPacket.Relay -> "RELAY"
            }

            val relayMessage = SignalingMessage.MeshRelay(
                roomId = roomId,
                fromPeerId = peerId,
                packetType = packetType,
                payloadBase64 = base64Payload,
                timestampMs = currentTimeMillis()
            )

            signalingClient.sendMessage(relayMessage)

            mutex.withLock {
                _stats.value = _stats.value.copy(
                    packetsForwardedToCloud = _stats.value.packetsForwardedToCloud + 1L,
                    lastRelayedTimestampMs = currentTimeMillis()
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Updates operating relay mode (e.g. dynamically switching from BLE_ONLY to FALLBACK_ONLY).
     */
    suspend fun setRelayMode(mode: CloudRelayMode) {
        mutex.withLock {
            currentMode = mode
            _stats.value = _stats.value.copy(currentMode = mode)
        }
    }

    /**
     * Closes the relay bridge, disconnects from the signaling gateway, and releases channels.
     */
    suspend fun stop() {
        mutex.withLock {
            val peerId = localPeerId
            val roomId = activeRoomId
            if (peerId != null && roomId != null) {
                try {
                    signalingClient.leaveRoom(peerId, roomId)
                } catch (_: Exception) {}
            }

            signalingClient.disconnect()
            bridgeScope?.cancel()
            bridgeScope = null
            activeRoomId = null
            localPeerId = null
            _stats.value = _stats.value.copy(isConnected = false, activeRoomId = null)
        }
    }

    private suspend fun handleIncomingRelay(message: SignalingMessage.MeshRelay) {
        // Do not process our own bounced relays
        if (message.fromPeerId == localPeerId) return

        try {
            val jsonBytes = Base64.decode(message.payloadBase64)
            val jsonString = jsonBytes.decodeToString()
            val packet = meshJson.decodeFromString<MeshPacket>(jsonString)

            // Deduplication gate: drop if packet was already heard over local BLE or earlier cloud relay
            if (!deduplicator.shouldProcess(packet)) {
                mutex.withLock {
                    _stats.value = _stats.value.copy(
                        duplicatePacketsDropped = _stats.value.duplicatePacketsDropped + 1L
                    )
                }
                return
            }

            mutex.withLock {
                _stats.value = _stats.value.copy(
                    packetsReceivedFromCloud = _stats.value.packetsReceivedFromCloud + 1L,
                    lastRelayedTimestampMs = currentTimeMillis()
                )
            }

            _incomingCloudPackets.send(packet)
        } catch (_: Exception) {
            // Malformed relay payload
        }
    }
}
