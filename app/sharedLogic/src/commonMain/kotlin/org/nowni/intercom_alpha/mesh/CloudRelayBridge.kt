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
import org.nowni.intercom_alpha.audio.AdaptiveJitterBuffer
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.protocol.SignalingMessage
import org.nowni.intercom_alpha.signaling.IntercomSignalingClient
import org.nowni.intercom_alpha.signaling.SignalingConnectionState
import org.nowni.intercom_alpha.mesh.failover.FailoverMode
import org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter
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
    val clusterBridgesForwarded: Long = 0L,
    val clusterBridgesReceived: Long = 0L,
    val activeRemoteClusters: Int = 0,
    val activeRoomId: String? = null,
    val localClusterId: String? = null,
    val lastRelayedTimestampMs: Long = 0L
)

/**
 * Bridges local BLE mesh clusters across wide-area cellular networks via Ktor WebSocket signaling.
 *
 * Implements ADR-009 cloud relay fallback, serializing and deserializing encrypted [MeshPacket] frames,
 * deduplicating cross-network packet echoes using [PacketDeduplicator], preventing cross-mesh routing loops,
 * aggregating multi-cluster peer presence, and providing jitter smoothing for transcode-free 4-bit ADPCM audio.
 */
@OptIn(ExperimentalEncodingApi::class)
class CloudRelayBridge(
    private val signalingClient: IntercomSignalingClient = IntercomSignalingClient(),
    private val deduplicator: PacketDeduplicator = PacketDeduplicator(),
    val initialClusterId: String = "cluster-local",
    val jitterBuffer: AdaptiveJitterBuffer = AdaptiveJitterBuffer(),
    private val failoverArbiter: MultiPathFailoverArbiter? = null
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

    private val _stats = MutableStateFlow(CloudRelayStats(localClusterId = initialClusterId))
    val stats: StateFlow<CloudRelayStats> = _stats.asStateFlow()

    private val _clusterPresence = MutableStateFlow<Map<String, List<PeerInfo>>>(emptyMap())
    val clusterPresence: StateFlow<Map<String, List<PeerInfo>>> = _clusterPresence.asStateFlow()

    private val _aggregatedPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val aggregatedPeers: StateFlow<List<PeerInfo>> = _aggregatedPeers.asStateFlow()

    private var activeRoomId: String? = null
    private var localPeerId: String? = null
    var currentClusterId: String = initialClusterId
        private set
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
        mode: CloudRelayMode = CloudRelayMode.HYBRID_ALWAYS,
        clusterId: String? = null
    ) {
        mutex.withLock {
            localPeerId = peerId
            activeRoomId = roomId
            currentMode = mode
            if (clusterId != null) {
                currentClusterId = clusterId
            }

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
                        activeRoomId = if (connected) activeRoomId else null,
                        localClusterId = currentClusterId
                    )
                }
            }

            // Ingest and deduplicate remote mesh packets and cluster bridges
            scope.launch {
                signalingClient.incomingMessages.collect { message ->
                    when (message) {
                        is SignalingMessage.MeshRelay -> handleIncomingRelay(message)
                        is SignalingMessage.ClusterBridge -> handleIncomingClusterBridge(message)
                        else -> {}
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
        if (currentMode == CloudRelayMode.FALLBACK_ONLY && failoverArbiter?.metrics?.value?.currentMode == FailoverMode.BLE_DIRECT) {
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
            val packetType = packetTypeString(packet)

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
     * Forwards a [MeshPacket] across independent BLE mesh clusters via a [SignalingMessage.ClusterBridge].
     *
     * Encapsulates transcode-free routing of 4-bit ADPCM packets with hop limits and cluster path tracking.
     */
    suspend fun forwardClusterBridge(
        packet: MeshPacket,
        targetClusterId: String? = null,
        targetRoomId: String? = null,
        hopLimit: Int = 3
    ): Boolean {
        if (currentMode == CloudRelayMode.BLE_ONLY) {
            return false
        }
        if (currentMode == CloudRelayMode.FALLBACK_ONLY && failoverArbiter?.metrics?.value?.currentMode == FailoverMode.BLE_DIRECT) {
            return false
        }

        if (!_stats.value.isConnected) {
            return false
        }

        return try {
            val jsonString = meshJson.encodeToString(packet)
            val base64Payload = Base64.encode(jsonString.encodeToByteArray())
            val packetType = packetTypeString(packet)
            val now = currentTimeMillis()

            val bridgeMessage = SignalingMessage.ClusterBridge(
                bridgeId = "${currentClusterId}-$now-${packet.hashCode()}",
                sourceClusterId = currentClusterId,
                targetClusterId = targetClusterId,
                targetRoomId = targetRoomId ?: activeRoomId,
                packetType = packetType,
                payloadBase64 = base64Payload,
                hopLimit = hopLimit,
                clusterPath = listOf(currentClusterId),
                timestampMs = now
            )

            signalingClient.sendMessage(bridgeMessage)

            mutex.withLock {
                _stats.value = _stats.value.copy(
                    packetsForwardedToCloud = _stats.value.packetsForwardedToCloud + 1L,
                    clusterBridgesForwarded = _stats.value.clusterBridgesForwarded + 1L,
                    lastRelayedTimestampMs = now
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Broadcasts the peer presence of this local cluster to remote clusters.
     */
    suspend fun broadcastClusterPresence(localPeers: List<PeerInfo>): Boolean {
        if (currentMode == CloudRelayMode.BLE_ONLY || !_stats.value.isConnected) {
            return false
        }
        if (currentMode == CloudRelayMode.FALLBACK_ONLY && failoverArbiter?.metrics?.value?.currentMode == FailoverMode.BLE_DIRECT) {
            return false
        }

        return try {
            val now = currentTimeMillis()
            val presenceMessage = SignalingMessage.ClusterBridge(
                bridgeId = "presence-${currentClusterId}-$now",
                sourceClusterId = currentClusterId,
                targetClusterId = null,
                targetRoomId = activeRoomId,
                packetType = "PRESENCE",
                payloadBase64 = "",
                hopLimit = 2,
                clusterPath = listOf(currentClusterId),
                memberPresence = localPeers,
                timestampMs = now
            )

            signalingClient.sendMessage(presenceMessage)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes an offline or unlinked cluster's presence from aggregated state.
     */
    fun removeClusterPresence(clusterId: String) {
        val updatedMap = _clusterPresence.value.toMutableMap()
        if (updatedMap.remove(clusterId) != null) {
            _clusterPresence.value = updatedMap
            _aggregatedPeers.value = updatedMap.values.flatten()
            _stats.value = _stats.value.copy(activeRemoteClusters = updatedMap.size)
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
            _clusterPresence.value = emptyMap()
            _aggregatedPeers.value = emptyList()
            _stats.value = _stats.value.copy(isConnected = false, activeRoomId = null, activeRemoteClusters = 0)
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

            // Jitter buffer smoothing for transcode-free ADPCM audio
            if (packet is MeshPacket.Audio) {
                jitterBuffer.push(packet, currentTimeMillis())
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

    suspend fun handleIncomingClusterBridge(message: SignalingMessage.ClusterBridge) {
        val myCluster = currentClusterId

        // 1. Loop Prevention: Drop if packet originated from our own cluster
        if (message.sourceClusterId == myCluster) return

        // 2. Loop Prevention: Drop if our cluster is already in the traversed clusterPath
        if (message.clusterPath.contains(myCluster)) return

        // 3. Target Routing: Drop if addressed specifically to another cluster
        val targetCluster = message.targetClusterId
        if (targetCluster != null && targetCluster != myCluster) return

        // 4. Hop Limit check
        if (message.hopLimit <= 0) return

        // 5. Aggregate Peer Presence
        if (message.memberPresence.isNotEmpty()) {
            mutex.withLock {
                val updatedMap = _clusterPresence.value.toMutableMap()
                updatedMap[message.sourceClusterId] = message.memberPresence
                _clusterPresence.value = updatedMap
                _aggregatedPeers.value = updatedMap.values.flatten()
                _stats.value = _stats.value.copy(activeRemoteClusters = updatedMap.size)
            }
        }

        // If this was purely a presence announcement, record metrics and return
        if (message.packetType == "PRESENCE") {
            mutex.withLock {
                _stats.value = _stats.value.copy(
                    clusterBridgesReceived = _stats.value.clusterBridgesReceived + 1L,
                    lastRelayedTimestampMs = currentTimeMillis()
                )
            }
            return
        }

        // 6. Decode and Deduplicate bridged packet
        try {
            val jsonBytes = Base64.decode(message.payloadBase64)
            val jsonString = jsonBytes.decodeToString()
            val packet = meshJson.decodeFromString<MeshPacket>(jsonString)

            if (!deduplicator.shouldProcess(packet)) {
                mutex.withLock {
                    _stats.value = _stats.value.copy(
                        duplicatePacketsDropped = _stats.value.duplicatePacketsDropped + 1L
                    )
                }
                return
            }

            // 7. Transcode-free routing of 4-bit ADPCM audio with jitter smoothing
            if (packet is MeshPacket.Audio) {
                jitterBuffer.push(packet, currentTimeMillis())
            }

            mutex.withLock {
                _stats.value = _stats.value.copy(
                    packetsReceivedFromCloud = _stats.value.packetsReceivedFromCloud + 1L,
                    clusterBridgesReceived = _stats.value.clusterBridgesReceived + 1L,
                    lastRelayedTimestampMs = currentTimeMillis()
                )
            }

            _incomingCloudPackets.send(packet)
        } catch (_: Exception) {
            // Malformed bridge payload
        }
    }

    private fun packetTypeString(packet: MeshPacket): String = when (packet) {
        is MeshPacket.Audio -> "AUDIO"
        is MeshPacket.Control -> "CONTROL"
        is MeshPacket.Discovery -> "DISCOVERY"
        is MeshPacket.Relay -> "RELAY"
    }
}
