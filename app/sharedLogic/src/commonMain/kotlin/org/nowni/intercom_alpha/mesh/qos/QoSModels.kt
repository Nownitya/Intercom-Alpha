package org.nowni.intercom_alpha.mesh.qos

import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Traffic priority tiers for BLE GATT mesh packet forwarding and airtime scheduling.
 *
 * Lower rank indicates higher transmission priority.
 */
@Serializable
enum class QoSPriority(
    val rank: Int,
    val weight: Int,
    val maxQueueCapacity: Int
) {
    /**
     * Unconditional preemption: crash impact detection, eCall SOS, emergency geofence alarms.
     * Guaranteed immediate transmission with zero throttling.
     */
    EMERGENCY_CRITICAL(0, weight = 100, maxQueueCapacity = 64),

    /**
     * Real-time conversational voice frames (PCM/ADPCM audio, federation audio).
     * Bounded latency budget (<40ms transmission delay).
     */
    ACTIVE_VOICE(1, weight = 50, maxQueueCapacity = 128),

    /**
     * Mesh routing & topology maintenance: AODV RREQ/RREP/RERR, epoch re-keying,
     * federation handshakes, keep-alive pings. Protected against voice starvation.
     */
    ROUTING_CONTROL(2, weight = 20, maxQueueCapacity = 64),

    /**
     * Background telemetry & bulk transfers: OTA GPX/GeoJSON chunks, battery status,
     * stationary repeater diagnostics. Throttled during active voice transmission.
     */
    BULK_TELEMETRY(3, weight = 5, maxQueueCapacity = 256)
}

/**
 * Container for packets buffered inside the QoS queue.
 */
data class QueuedMeshPacket(
    val priority: QoSPriority,
    val packet: MeshPacket,
    val enqueueTimestampMs: Long,
    val targetPeerId: String? = null,
    val retryCount: Int = 0
)

/**
 * Reactive diagnostic snapshot of mesh airtime QoS traffic controller.
 */
@Serializable
data class QoSSnapshot(
    val emergencyQueueDepth: Int = 0,
    val voiceQueueDepth: Int = 0,
    val controlQueueDepth: Int = 0,
    val bulkQueueDepth: Int = 0,
    val totalPacketsEnqueued: Long = 0L,
    val totalPacketsDispatched: Long = 0L,
    val totalPacketsDropped: Long = 0L,
    val voicePreemptionCount: Long = 0L,
    val isVoiceActive: Boolean = false,
    val bulkThrottleActive: Boolean = false,
    val estimatedAirtimeUtilizationPercent: Int = 0
) {
    val totalPendingQueueDepth: Int
        get() = emergencyQueueDepth + voiceQueueDepth + controlQueueDepth + bulkQueueDepth

    val isChannelCongested: Boolean
        get() = estimatedAirtimeUtilizationPercent >= 75 || totalPacketsDropped > 0
}

/**
 * Utility helper to classify any incoming or outgoing [MeshPacket] into its [QoSPriority].
 */
object QoSPacketClassifier {

    fun classify(packet: MeshPacket): QoSPriority {
        val rootPacket = unwrapRelay(packet)
        return when (rootPacket) {
            is MeshPacket.Audio -> QoSPriority.ACTIVE_VOICE
            is MeshPacket.Discovery -> QoSPriority.ROUTING_CONTROL
            is MeshPacket.Control -> classifyControl(rootPacket.type)
            is MeshPacket.Relay -> QoSPriority.ROUTING_CONTROL // Fallback if double-wrapped
        }
    }

    private fun unwrapRelay(packet: MeshPacket): MeshPacket {
        var curr = packet
        while (curr is MeshPacket.Relay) {
            curr = curr.packet
        }
        return curr
    }

    private fun classifyControl(type: ControlType): QoSPriority {
        return when (type) {
            ControlType.CRASH_ALERT,
            ControlType.GEOFENCE_ALARM -> QoSPriority.EMERGENCY_CRITICAL

            ControlType.FEDERATION_AUDIO -> QoSPriority.ACTIVE_VOICE

            ControlType.FILE_CHUNK,
            ControlType.FILE_ACK,
            ControlType.POWER_STATUS,
            ControlType.REPEATER_BEACON -> QoSPriority.BULK_TELEMETRY

            ControlType.ROUTE_REQUEST,
            ControlType.ROUTE_REPLY,
            ControlType.ROUTE_ERROR,
            ControlType.DIRECTED_DATA,
            ControlType.EPOCH_ROLLOVER,
            ControlType.EPOCH_BEACON,
            ControlType.FEDERATION_INVITE,
            ControlType.FEDERATION_BRIDGE,
            ControlType.JOIN_REQUEST,
            ControlType.JOIN_ACCEPT,
            ControlType.JOIN_REJECT,
            ControlType.LEAVE,
            ControlType.PING,
            ControlType.PONG,
            ControlType.SYNC_REQUEST,
            ControlType.SYNC_RESPONSE,
            ControlType.REPEATER_PING,
            ControlType.REPEATER_PONG -> QoSPriority.ROUTING_CONTROL
        }
    }
}
