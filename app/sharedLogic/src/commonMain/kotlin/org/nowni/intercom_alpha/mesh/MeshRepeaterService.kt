package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Operating duty cycle modes for autonomous mesh repeater nodes.
 */
@Serializable
enum class RepeaterDutyCycleMode {
    /** 100% radio readiness, lowest forwarding latency (< 5ms), suitable for continuous vehicle power. */
    CONTINUOUS,

    /** Balanced 50% duty cycle, optimal for standalone battery packs. */
    POWER_SAVER,

    /** Ultra-low duty cycle, extended survival mode for remote basecamp solar stations. */
    EXTREME_SURVIVAL
}

/**
 * Compact OTA broadcast payload emitted periodically by stationary repeater nodes.
 */
@Serializable
data class RepeaterBeaconPayload(
    val repeaterId: String,
    val repeaterName: String,
    val batteryPercent: Int,
    val activeNeighborCount: Int,
    val packetsForwarded: Long,
    val uptimeSeconds: Long,
    val dutyCycleMode: RepeaterDutyCycleMode = RepeaterDutyCycleMode.CONTINUOUS,
    val isSolarPowered: Boolean = false,
    val locationLat: Double? = null,
    val locationLon: Double? = null
)

/**
 * Diagnostic ping request sent by mobile rider nodes to interrogate repeater health.
 */
@Serializable
data class RepeaterPingPayload(
    val senderId: String,
    val timestampMs: Long,
    val sequence: Long
)

/**
 * Diagnostic pong response returned by repeater node with real-time operational telemetry.
 */
@Serializable
data class RepeaterPongPayload(
    val repeaterId: String,
    val pingSenderId: String,
    val pingTimestampMs: Long,
    val pongTimestampMs: Long,
    val batteryPercent: Int,
    val packetsForwarded: Long,
    val rssi: Int? = null
)

/**
 * Real-time operational telemetry snapshot of the repeater service.
 */
@Serializable
data class RepeaterTelemetry(
    val repeaterId: String,
    val repeaterName: String,
    val isStationary: Boolean,
    val activeNeighborCount: Int,
    val packetsForwarded: Long,
    val packetsDroppedDuplicate: Long,
    val packetsDroppedTtlExpired: Long,
    val lastForwardLatencyMs: Long,
    val beaconEmissionCount: Long,
    val batteryPercent: Int,
    val isRunning: Boolean,
    val activeNeighbors: List<String> = emptyList()
)

/**
 * Autonomous Mesh Repeater & Basecamp Beacon Service.
 *
 * Designed for dedicated low-power stationary relay installations (mountain ridges, basecamps,
 * or parked convoy vehicles):
 * - Zero Audio Buffer Allocations: Completely bypasses microphone capture and audio playout pipelines,
 *   dedicating 100% of processing and radio airtime to low-latency BLE GATT flooding relay.
 * - Sub-15ms Multi-Hop Forwarding: Relays valid packets across the mesh with instant in-memory routing
 *   and loop suppression.
 * - Broadcasts periodic low-overhead [RepeaterBeaconPayload] informing mobile riders of mesh backbone coverage.
 * - Responds to remote diagnostic pings ([RepeaterPingPayload] / [RepeaterPongPayload]) for link testing.
 */
class MeshRepeaterService(
    val repeaterId: String,
    val repeaterName: String,
    val defaultTtl: Int = 3,
    val isStationary: Boolean = true,
    var dutyCycleMode: RepeaterDutyCycleMode = RepeaterDutyCycleMode.CONTINUOUS,
    private val batteryPercentProvider: () -> Int = { 100 },
    private val deduplicator: PacketDeduplicator = PacketDeduplicator(),
    private val timeProvider: () -> Long = { currentTimeMillis() },
    private val packetOutbox: ((MeshPacket) -> Unit)? = null
) {
    private val json = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    private val stateMutex = Mutex()
    private val startTimeMs: Long = timeProvider()

    private var isRunning: Boolean = false
    private var packetsForwarded: Long = 0L
    private var packetsDroppedDuplicate: Long = 0L
    private var packetsDroppedTtlExpired: Long = 0L
    private var lastForwardLatencyMs: Long = 0L
    private var beaconEmissionCount: Long = 0L

    // Active neighbor table: Peer ID -> Last Seen Timestamp (ms)
    private val activeNeighbors = mutableMapOf<String, Long>()
    private val neighborTimeoutMs: Long = 60_000L // 60 seconds neighbor stale threshold

    private val _telemetry = MutableStateFlow(buildTelemetrySnapshot())
    val telemetry: StateFlow<RepeaterTelemetry> = _telemetry.asStateFlow()

    fun start() {
        isRunning = true
        updateTelemetry()
    }

    fun stop() {
        isRunning = false
        updateTelemetry()
    }

    /**
     * Ingests and processes an incoming mesh packet.
     *
     * Evaluates loop suppression, decrements TTL, updates neighbor presence, and
     * forwards the packet over connected mesh links.
     *
     * @return The relayed [MeshPacket] that was forwarded, or `null` if the packet was dropped.
     */
    suspend fun handleIncomingPacket(packet: MeshPacket, arrivalTimeMs: Long = timeProvider()): MeshPacket? {
        val now = arrivalTimeMs

        // 1. Loop suppression and deduplication check
        val isNovel = deduplicator.shouldProcess(packet, now)
        if (!isNovel) {
            stateMutex.withLock {
                packetsDroppedDuplicate++
            }
            updateTelemetry()
            return null
        }

        // 2. Identify sender and update active neighbor table
        val senderId = extractSenderId(packet)
        if (senderId != null && senderId != repeaterId) {
            recordNeighbor(senderId, now)
        }

        // 3. Drop self-origin packets
        if (senderId == repeaterId) {
            return null
        }

        // 4. Handle Repeater Diagnostic Ping
        if (packet is MeshPacket.Control && packet.type == ControlType.REPEATER_PING) {
            handleDiagnosticPing(packet, now)
        }

        // 5. Evaluate forwarding / TTL decrement
        val packetToForward: MeshPacket? = when (packet) {
            is MeshPacket.Relay -> {
                if (packet.ttl <= 1) {
                    stateMutex.withLock {
                        packetsDroppedTtlExpired++
                    }
                    null
                } else {
                    MeshPacket.Relay(
                        originalSenderId = packet.originalSenderId,
                        ttl = packet.ttl - 1,
                        packet = packet.packet
                    )
                }
            }
            is MeshPacket.Audio, is MeshPacket.Control, is MeshPacket.Discovery -> {
                // Initial relay hop encapsulation
                val origSender = senderId ?: "unknown"
                MeshPacket.Relay(
                    originalSenderId = origSender,
                    ttl = defaultTtl - 1,
                    packet = packet
                )
            }
        }

        if (packetToForward != null) {
            val forwardStart = timeProvider()
            packetOutbox?.invoke(packetToForward)
            val latency = (timeProvider() - forwardStart).coerceAtLeast(0L)

            stateMutex.withLock {
                packetsForwarded++
                lastForwardLatencyMs = latency
            }
            updateTelemetry()
        } else {
            updateTelemetry()
        }

        return packetToForward
    }

    /**
     * Generates a compact basecamp repeater beacon packet.
     */
    fun emitBeaconPacket(): MeshPacket.Control {
        val now = timeProvider()
        val uptimeSeconds = ((now - startTimeMs) / 1000L).coerceAtLeast(0L)
        pruneStaleNeighbors(now)

        val payload = RepeaterBeaconPayload(
            repeaterId = repeaterId,
            repeaterName = repeaterName,
            batteryPercent = batteryPercentProvider(),
            activeNeighborCount = activeNeighbors.size,
            packetsForwarded = packetsForwarded,
            uptimeSeconds = uptimeSeconds,
            dutyCycleMode = dutyCycleMode,
            isSolarPowered = isStationary
        )

        val encodedPayload = json.encodeToString(payload).encodeToByteArray()
        val beaconPacket = MeshPacket.Control(
            senderId = repeaterId,
            type = ControlType.REPEATER_BEACON,
            payload = encodedPayload
        )

        beaconEmissionCount++
        packetOutbox?.invoke(beaconPacket)
        updateTelemetry()

        return beaconPacket
    }

    fun setDutyCycle(mode: RepeaterDutyCycleMode) {
        dutyCycleMode = mode
        updateTelemetry()
    }

    /**
     * Creates a diagnostic ping packet directed across the mesh to interrogate repeater latency.
     */
    fun createDiagnosticPing(senderId: String = repeaterId, sequence: Long = 0L): MeshPacket.Control {
        return Companion.createDiagnosticPing(senderId, sequence, timeProvider())
    }

    /**
     * Decodes an incoming diagnostic pong packet.
     */
    fun decodePongPayload(packet: MeshPacket.Control): RepeaterPongPayload? {
        return Companion.decodePongPayload(packet)
    }

    /**
     * Decodes an incoming beacon payload.
     */
    fun decodeBeaconPayload(packet: MeshPacket.Control): RepeaterBeaconPayload? {
        return Companion.decodeBeaconPayload(packet)
    }

    private fun handleDiagnosticPing(packet: MeshPacket.Control, now: Long) {
        val pingPayload = try {
            json.decodeFromString<RepeaterPingPayload>(packet.payload.decodeToString())
        } catch (_: Exception) {
            null
        } ?: return

        val pong = RepeaterPongPayload(
            repeaterId = repeaterId,
            pingSenderId = pingPayload.senderId,
            pingTimestampMs = pingPayload.timestampMs,
            pongTimestampMs = now,
            batteryPercent = batteryPercentProvider(),
            packetsForwarded = packetsForwarded
        )

        val encodedPong = json.encodeToString(pong).encodeToByteArray()
        val pongPacket = MeshPacket.Control(
            senderId = repeaterId,
            type = ControlType.REPEATER_PONG,
            payload = encodedPong
        )

        packetOutbox?.invoke(pongPacket)
    }

    private fun recordNeighbor(senderId: String, now: Long) {
        activeNeighbors[senderId] = now
        pruneStaleNeighbors(now)
    }

    private fun pruneStaleNeighbors(now: Long) {
        val iterator = activeNeighbors.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > neighborTimeoutMs) {
                iterator.remove()
            }
        }
    }

    private fun extractSenderId(packet: MeshPacket): String? {
        return when (packet) {
            is MeshPacket.Audio -> packet.senderId
            is MeshPacket.Control -> packet.senderId
            is MeshPacket.Discovery -> packet.senderId
            is MeshPacket.Relay -> packet.originalSenderId
        }
    }

    private fun updateTelemetry() {
        _telemetry.value = buildTelemetrySnapshot()
    }

    private fun buildTelemetrySnapshot(): RepeaterTelemetry {
        return RepeaterTelemetry(
            repeaterId = repeaterId,
            repeaterName = repeaterName,
            isStationary = isStationary,
            activeNeighborCount = activeNeighbors.size,
            packetsForwarded = packetsForwarded,
            packetsDroppedDuplicate = packetsDroppedDuplicate,
            packetsDroppedTtlExpired = packetsDroppedTtlExpired,
            lastForwardLatencyMs = lastForwardLatencyMs,
            beaconEmissionCount = beaconEmissionCount,
            batteryPercent = batteryPercentProvider(),
            isRunning = isRunning,
            activeNeighbors = activeNeighbors.keys.toList()
        )
    }

    companion object {
        private val helperJson = Json {
            classDiscriminator = "#type"
            ignoreUnknownKeys = true
        }

        fun createDiagnosticPing(
            senderId: String,
            sequence: Long = 0L,
            timestampMs: Long = currentTimeMillis()
        ): MeshPacket.Control {
            val payload = RepeaterPingPayload(
                senderId = senderId,
                timestampMs = timestampMs,
                sequence = sequence
            )
            val encoded = helperJson.encodeToString(payload).encodeToByteArray()
            return MeshPacket.Control(
                senderId = senderId,
                type = ControlType.REPEATER_PING,
                payload = encoded
            )
        }

        fun decodePongPayload(packet: MeshPacket.Control): RepeaterPongPayload? {
            if (packet.type != ControlType.REPEATER_PONG || packet.payload.isEmpty()) return null
            return try {
                helperJson.decodeFromString<RepeaterPongPayload>(packet.payload.decodeToString())
            } catch (_: Exception) {
                null
            }
        }

        fun decodeBeaconPayload(packet: MeshPacket.Control): RepeaterBeaconPayload? {
            if (packet.type != ControlType.REPEATER_BEACON || packet.payload.isEmpty()) return null
            return try {
                helperJson.decodeFromString<RepeaterBeaconPayload>(packet.payload.decodeToString())
            } catch (_: Exception) {
                null
            }
        }
    }
}
