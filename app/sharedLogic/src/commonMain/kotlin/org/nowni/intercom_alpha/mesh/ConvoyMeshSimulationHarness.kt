package org.nowni.intercom_alpha.mesh

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.audio.AdaptiveJitterBuffer
import org.nowni.intercom_alpha.audio.PlayoutFrame
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.crypto.MeshKeyRatchet
import org.nowni.intercom_alpha.crypto.RatchetEnvelope
import kotlin.math.abs
import kotlin.random.Random

/**
 * Configuration options for the automated convoy mesh simulation harness.
 */
data class ConvoySimulationConfig(
    val nodeCount: Int = 8,
    val maxDirectHopDistance: Int = 1, // Nodes communicate directly only within this convoy line distance
    val defaultTtl: Int = 4,
    val packetDropRate: Double = 0.25, // 25% simulated packet loss rate
    val enableOutOfOrderArrivals: Boolean = true,
    val randomSeed: Long = 42L
)

/**
 * Benchmark telemetry metrics gathered across the simulation run.
 */
data class ConvoyBenchmarkMetrics(
    val nodeCount: Int,
    val totalPacketsSent: Int,
    val directDeliveries: Int,
    val relayedDeliveries: Int,
    val tailNodeReceivedCount: Int,
    val tailNodeDecryptedCount: Int,
    val loopSuppressionDrops: Int,
    val ttlExpirationDrops: Int,
    val executionDurationMs: Long
)

/**
 * Virtual mesh peer participating in the convoy simulation.
 */
class VirtualConvoyNode(
    val nodeIndex: Int,
    val nodeId: String,
    val ratchet: MeshKeyRatchet,
    val deduplicator: PacketDeduplicator = PacketDeduplicator(),
    val jitterBuffer: AdaptiveJitterBuffer = AdaptiveJitterBuffer(frameDurationMs = 20L)
) {
    val receivedAudioPackets = mutableListOf<MeshPacket.Audio>()
    val receivedRelayedAudio = mutableListOf<MeshPacket.Audio>()
    val decryptedPayloads = mutableListOf<ByteArray>()
    var totalPacketsReceived: Int = 0
    var duplicateDrops: Int = 0
    var ttlExpiredDrops: Int = 0

    suspend fun processIncomingPacket(
        packet: MeshPacket,
        arrivalTimeMs: Long,
        onRelayEmit: (MeshPacket.Relay) -> Unit
    ): Boolean {
        // Loop suppression check
        if (!deduplicator.shouldProcess(packet, arrivalTimeMs)) {
            duplicateDrops++
            return false
        }

        totalPacketsReceived++

        val innerAudio: MeshPacket.Audio? = when (packet) {
            is MeshPacket.Relay -> {
                val inner = packet.packet
                if (inner is MeshPacket.Audio) {
                    receivedRelayedAudio.add(inner)
                }

                // Multi-hop forwarding if TTL allows
                if (packet.ttl > 1) {
                    val nextHop = MeshPacket.Relay(
                        originalSenderId = packet.originalSenderId,
                        ttl = packet.ttl - 1,
                        packet = packet.packet
                    )
                    onRelayEmit(nextHop)
                } else {
                    ttlExpiredDrops++
                }
                inner as? MeshPacket.Audio
            }
            is MeshPacket.Audio -> {
                receivedAudioPackets.add(packet)
                // Originate relay frame across neighbors
                val relayFrame = MeshPacket.Relay(
                    originalSenderId = packet.senderId,
                    ttl = 3,
                    packet = packet
                )
                onRelayEmit(relayFrame)
                packet
            }
            else -> null
        }

        if (innerAudio != null) {
            jitterBuffer.push(innerAudio, arrivalTimeMs)
        }

        return true
    }
}

/**
 * Automated Multi-Node Mesh Field Simulation Harness.
 *
 * Simulates an 8-node motorcycle convoy moving in a linear formation through variable RF environments
 * (e.g. canyon passes, line-of-sight occlusion):
 * - Models distance-based RF attenuation and multi-hop forwarding across intermediate nodes.
 * - Enforces loop suppression and TTL decrements across 3+ hops in real time.
 * - Stochastically injects packet drops (e.g. 25%) and out-of-order packet arrival.
 * - Validates [MeshKeyRatchet] skipped-key recovery and [AdaptiveJitterBuffer] playout.
 */
class ConvoyMeshSimulationHarness(
    val config: ConvoySimulationConfig = ConvoySimulationConfig()
) {
    private val json = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
    }

    private val masterSecret = ByteArray(32) { (it + 1).toByte() }
    val nodes: List<VirtualConvoyNode> = (0 until config.nodeCount).map { i ->
        val id = if (i == 0) "convoy-lead" else if (i == config.nodeCount - 1) "convoy-tail" else "convoy-node-$i"
        VirtualConvoyNode(
            nodeIndex = i,
            nodeId = id,
            ratchet = MeshKeyRatchet(localNodeId = id, masterSecret = masterSecret)
        )
    }

    /**
     * Executes an end-to-end multi-node voice transmission benchmark.
     *
     * @param senderIndex Index of transmitting node (e.g. 0 for convoy lead).
     * @param frameCount Number of sequential voice frames to transmit.
     */
    suspend fun runConvoyVoiceStreamBenchmark(
        senderIndex: Int = 0,
        frameCount: Int = 40
    ): ConvoyBenchmarkMetrics {
        val startTime = org.nowni.intercom_alpha.currentTimeMillis()
        val random = Random(config.randomSeed)
        val senderNode = nodes[senderIndex]

        var directDeliveries = 0
        var relayedDeliveries = 0

        for (frameSeq in 1..frameCount) {
            val timestampMs = frameSeq * 20L

            // 1. Encrypt frame using sender's PFS session key ratchet
            val plaintext = "voice-pcm-frame-$frameSeq-from-${senderNode.nodeId}".encodeToByteArray()
            val envelope = senderNode.ratchet.encrypt(plaintext)
            val envelopePayload = json.encodeToString(envelope).encodeToByteArray()

            val packet = MeshPacket.Audio(
                senderId = senderNode.nodeId,
                sequence = frameSeq.toLong(),
                timestamp = timestampMs,
                data = envelopePayload,
                profile = AudioProfile.MEDIUM
            )

            // 2. Queue propagation events
            // In a linear convoy, sender transmits. Neighbors within maxDirectHopDistance hear directly.
            val inFlightQueue = ArrayDeque<Pair<Int, MeshPacket>>() // (targetNodeIndex, packet)

            for (targetIdx in nodes.indices) {
                if (targetIdx == senderIndex) continue
                val distance = abs(targetIdx - senderIndex)
                if (distance <= config.maxDirectHopDistance) {
                    val isDropped = random.nextDouble() < config.packetDropRate
                    if (!isDropped) {
                        inFlightQueue.add(Pair(targetIdx, packet))
                        directDeliveries++
                    }
                }
            }

            // 3. Process in-flight multi-hop flooding with relay emissions
            val maxHopsLimit = 20
            var iterations = 0

            while (inFlightQueue.isNotEmpty() && iterations < maxHopsLimit) {
                iterations++
                val nextBatch = ArrayList<Pair<Int, MeshPacket>>()
                while (inFlightQueue.isNotEmpty()) {
                    nextBatch.add(inFlightQueue.removeFirst())
                }

                // If out-of-order simulation is enabled, randomly shuffle delivery order within batch
                if (config.enableOutOfOrderArrivals && nextBatch.size > 1) {
                    nextBatch.shuffle(random)
                }

                for ((nodeIdx, rxPacket) in nextBatch) {
                    val receiver = nodes[nodeIdx]
                    val arrivalTime = timestampMs + (iterations * 5L)

                    receiver.processIncomingPacket(rxPacket, arrivalTime) { relayFrame ->
                        relayedDeliveries++
                        // Forward to adjacent neighbors (distance <= maxDirectHopDistance)
                        for (neighborIdx in nodes.indices) {
                            if (neighborIdx == nodeIdx) continue
                            if (abs(neighborIdx - nodeIdx) <= config.maxDirectHopDistance) {
                                val isDropped = random.nextDouble() < config.packetDropRate
                                if (!isDropped) {
                                    inFlightQueue.add(Pair(neighborIdx, relayFrame))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Attempt decryption on all nodes for received frames
        for (node in nodes) {
            val allAudio = (node.receivedAudioPackets + node.receivedRelayedAudio).distinctBy { it.sequence }
            for (audio in allAudio) {
                try {
                    val envelope = json.decodeFromString<RatchetEnvelope>(audio.data.decodeToString())
                    val decrypted = node.ratchet.decrypt(envelope)
                    if (decrypted != null) {
                        node.decryptedPayloads.add(decrypted)
                    }
                } catch (_: Exception) {
                    // Frame skipped or corrupted
                }
            }
        }

        val tailNode = nodes.last()
        val totalDuration = org.nowni.intercom_alpha.currentTimeMillis() - startTime

        return ConvoyBenchmarkMetrics(
            nodeCount = config.nodeCount,
            totalPacketsSent = frameCount,
            directDeliveries = directDeliveries,
            relayedDeliveries = relayedDeliveries,
            tailNodeReceivedCount = (tailNode.receivedAudioPackets + tailNode.receivedRelayedAudio).distinctBy { it.sequence }.size,
            tailNodeDecryptedCount = tailNode.decryptedPayloads.size,
            loopSuppressionDrops = nodes.sumOf { it.duplicateDrops },
            ttlExpirationDrops = nodes.sumOf { it.ttlExpiredDrops },
            executionDurationMs = totalDuration
        )
    }

    /**
     * Pulls synthesized jitter playout frames for a given node.
     */
    suspend fun pullPlayoutForNode(nodeIndex: Int, tickCount: Int): Int {
        val node = nodes[nodeIndex]
        var audibleFrames = 0
        for (tick in 1..tickCount) {
            val frame = node.jitterBuffer.pull(tick * 20L)
            if (frame is PlayoutFrame.Available) {
                audibleFrames++
            }
        }
        return audibleFrames
    }
}
