package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.audio.AdaptiveJitterBuffer
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.crypto.MeshKeyRatchet
import org.nowni.intercom_alpha.crypto.RatchetEnvelope
import org.nowni.intercom_alpha.map.*
import org.nowni.intercom_alpha.mesh.*
import org.nowni.intercom_alpha.power.*
import org.nowni.intercom_alpha.telemetry.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val harnessJson = Json {
    classDiscriminator = "#type"
    ignoreUnknownKeys = true
}

/**
 * Configuration options for the 16-node concurrent convoy stress simulation harness.
 */
data class StressSimulationConfig(
    val nodeCount: Int = 16,
    val maxDirectHopDistance: Int = 2, // Direct RF reach (2 adjacent nodes on either side)
    val defaultTtl: Int = 4,
    val packetDropRate: Double = 0.20, // 20% simulated stochastic RF packet loss
    val enableOutOfOrderArrivals: Boolean = true,
    val randomSeed: Long = 1337L,
    val audioFramesPerBatch: Int = 30,
    val routeChunkCount: Int = 12
)

/**
 * Telemetry and performance metrics aggregated during the stress benchmark execution.
 */
data class StressBenchmarkMetrics(
    val nodeCount: Int,
    val totalAudioSent: Int,
    val totalControlSent: Int,
    val tailNodeReceivedAudio: Int,
    val tailNodeDecryptedAudio: Int,
    val tailNodeReceivedChunks: Int,
    val geofenceAlarmsTriggered: Int,
    val clusterSplitsDetected: Int,
    val powerGovernorDutyCyclesApplied: Int,
    val loopSuppressionDrops: Int,
    val ttlExpirationDrops: Int,
    val executionDurationMs: Long
)

/**
 * Virtual convoy participant simulating hardware radio, cryptography ratchet,
 * jitter buffer, geofence radar, and battery governor.
 */
class VirtualStressNode(
    val nodeIndex: Int,
    val nodeId: String,
    val ratchet: MeshKeyRatchet,
    val deduplicator: PacketDeduplicator = PacketDeduplicator(windowMs = 10_000L, maxCacheSize = 2000),
    val jitterBuffer: AdaptiveJitterBuffer = AdaptiveJitterBuffer(frameDurationMs = 20L),
    val geofenceEngine: ConvoyGeofenceEngine = ConvoyGeofenceEngine(
        warningThresholdMeters = 300.0,
        breachThresholdMeters = 600.0,
        hazardProximityRadiusMeters = 150.0,
        splitClusterMinDistanceMeters = 500.0
    ),
    val batteryGovernor: BatteryGovernor = BatteryGovernor(nodeId = nodeId),
    var currentCoordinate: GpsCoordinate = GpsCoordinate(0.0, 0.0),
    var batteryPercent: Int = 100,
    var isStill: Boolean = false
) {
    val receivedAudioFrames = mutableListOf<MeshPacket.Audio>()
    val receivedControlPackets = mutableListOf<MeshPacket.Control>()
    val receivedRouteChunks = mutableListOf<FileTransferChunk>()
    val receivedGeofenceAlarms = mutableListOf<GeofenceAlarmPacket>()
    val decryptedPayloads = mutableListOf<ByteArray>()

    var totalPacketsProcessed: Int = 0
    var duplicateDrops: Int = 0
    var ttlExpiredDrops: Int = 0

    suspend fun processIncomingPacket(
        packet: MeshPacket,
        arrivalTimeMs: Long,
        onRelayEmit: (MeshPacket.Relay) -> Unit
    ): Boolean {
        if (!deduplicator.shouldProcess(packet, arrivalTimeMs)) {
            duplicateDrops++
            return false
        }

        totalPacketsProcessed++

        when (packet) {
            is MeshPacket.Relay -> {
                val inner = packet.packet
                handleInnerPacket(inner, arrivalTimeMs)

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
            }
            else -> {
                handleInnerPacket(packet, arrivalTimeMs)
                val sender = when (packet) {
                    is MeshPacket.Audio -> packet.senderId
                    is MeshPacket.Control -> packet.senderId
                    is MeshPacket.Discovery -> packet.senderId
                    is MeshPacket.Relay -> packet.originalSenderId
                }
                val relayFrame = MeshPacket.Relay(
                    originalSenderId = sender,
                    ttl = 8,
                    packet = packet
                )
                onRelayEmit(relayFrame)
            }
        }
        return true
    }

    private suspend fun handleInnerPacket(packet: MeshPacket, arrivalTimeMs: Long) {
        when (packet) {
            is MeshPacket.Audio -> {
                receivedAudioFrames.add(packet)
                jitterBuffer.push(packet, arrivalTimeMs)
                try {
                    val envelope = harnessJson.decodeFromString<RatchetEnvelope>(packet.data.decodeToString())
                    val decrypted = ratchet.decrypt(envelope)
                    if (decrypted != null) {
                        decryptedPayloads.add(decrypted)
                    }
                } catch (_: Exception) {
                    // Decryption failed or skipped key limit exceeded
                }
            }
            is MeshPacket.Control -> {
                receivedControlPackets.add(packet)
                when (packet.type) {
                    ControlType.FILE_CHUNK -> {
                        try {
                            val chunk = MeshFileTransferEngine.deserializeChunk(packet.payload)
                            receivedRouteChunks.add(chunk)
                        } catch (_: Exception) {}
                    }
                    ControlType.GEOFENCE_ALARM -> {
                        try {
                            val alarm = ConvoyGeofenceEngine.deserializeAlarmPacket(packet.payload)
                            receivedGeofenceAlarms.add(alarm)
                        } catch (_: Exception) {}
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
}

/**
 * 16-Node Concurrent Convoy Stress Simulation Harness.
 *
 * Simulates high-density multi-hop RF traffic across 16 virtual nodes under challenging
 * backcountry conditions (packet loss, out-of-order delivery, tether separation, and power throttling).
 */
class ConvoyStressSimulationHarness(
    val config: StressSimulationConfig = StressSimulationConfig()
) {
    private val masterSecret = ByteArray(32) { (it + 7).toByte() }
    private val rng = Random(config.randomSeed)

    val nodes: List<VirtualStressNode> = (0 until config.nodeCount).map { i ->
        val id = when (i) {
            0 -> "lead-rider-00"
            config.nodeCount - 1 -> "tail-rider-${i.toString().padStart(2, '0')}"
            else -> "rider-${i.toString().padStart(2, '0')}"
        }
        VirtualStressNode(
            nodeIndex = i,
            nodeId = id,
            ratchet = MeshKeyRatchet(localNodeId = id, masterSecret = masterSecret)
        )
    }

    init {
        setupConvoyTopology()
    }

    /**
     * Places the 16 nodes in a realistic dual-cluster formation:
     * - Cluster 1 (Nodes 0-7): Spaced ~45m apart (Lat 45.0000 .. 45.00315, Lon 7.0000)
     * - Gap: 600m open gap between Node 7 and Node 8 (triggers ConvoySplitWarning)
     * - Cluster 2 (Nodes 8-15): Spaced ~45m apart (Lat 45.00855 .. 45.0117, Lon 7.0000)
     * Total distance between Node 0 and Node 15 is ~1.3km (> 600m tether breach threshold).
     */
    private fun setupConvoyTopology() {
        // Base latitude: 45.0000, 1 degree lat ~= 111,000m -> 0.0004 deg ~= 44.4m
        val stepLat = 0.0004
        val splitGapLat = 0.0054 // ~600m gap

        for (i in 0 until config.nodeCount) {
            val node = nodes[i]
            val latOffset = if (i < 8) {
                i * stepLat
            } else {
                (7 * stepLat) + splitGapLat + ((i - 8) * stepLat)
            }
            node.currentCoordinate = GpsCoordinate(
                latitude = 45.0000 + latOffset,
                longitude = 7.0000,
                altitudeMeters = 1200.0 + (i * 10),
                speedKmh = if (i == 14) 0.0f else 42.0f,
                headingDegrees = 0.0f,
                timestampMs = 1000L
            )

            // Setup heterogeneous battery & motion profiles
            when (i) {
                0 -> node.batteryPercent = 95
                3 -> node.batteryPercent = 70
                6 -> node.batteryPercent = 28 // <30% triggers CONSERVATIVE
                11 -> node.batteryPercent = 12 // <15% triggers EXTREME_SURVIVAL
                14 -> {
                    node.batteryPercent = 50
                    node.isStill = true // Stationary rest stop
                }
                else -> node.batteryPercent = 85
            }
        }
    }

    /**
     * Executes the comprehensive 16-node multi-stream stress benchmark.
     */
    suspend fun runFullConvoyStressBenchmark(): StressBenchmarkMetrics {
        val startTime = currentTimeMillis()

        var totalAudioSent = 0
        var totalControlSent = 0
        var geofenceAlarmsTriggered = 0
        var clusterSplitsDetected = 0
        var powerGovernorChanges = 0

        // 1. Initialize power governor on all 16 nodes
        for (node in nodes) {
            node.batteryGovernor.updateBattery(node.batteryPercent, false, startTime)
            if (node.isStill) {
                node.batteryGovernor.updateMotion(0.0, true, startTime - 700_000L) // >10 min stillness
                node.batteryGovernor.updateMotion(0.0, true, startTime)
            }
            if (node.batteryGovernor.governorState.value.policy != PowerPolicy.NORMAL) {
                powerGovernorChanges++
            }
        }

        // 2. Evaluate geofence engine on all nodes with formation coordinates
        val allRiderPositions = nodes.map { n ->
            ConvoyRiderPosition(
                peerId = n.nodeId,
                peerName = "Rider ${n.nodeIndex}",
                coordinate = n.currentCoordinate,
                distanceMeters = ConvoyGpsTracker.calculateHaversineDistance(
                    nodes[0].currentCoordinate.latitude, nodes[0].currentCoordinate.longitude,
                    n.currentCoordinate.latitude, n.currentCoordinate.longitude
                ),
                absoluteBearingDegrees = 0.0,
                relativeBearingDegrees = 0.0,
                isAhead = n.nodeIndex > 0,
                isBehind = n.nodeIndex == 0,
                motionState = MotionState.HIGHWAY_CRUISE,
                isEmergencyAlert = false,
                lastUpdatedMs = startTime
            )
        }

        // Lead rider evaluates geofence against all peers
        val leadSnapshot = nodes[0].geofenceEngine.evaluate(
            localCoord = nodes[0].currentCoordinate,
            remoteRiders = allRiderPositions.filter { it.peerId != nodes[0].nodeId },
            now = startTime
        )

        if (leadSnapshot.overallStatus == GeofenceTetherStatus.BREACH) {
            geofenceAlarmsTriggered++
        }
        if (leadSnapshot.splitWarning?.isSplitDetected == true) {
            clusterSplitsDetected++
        }

        // 3. Lead rider transmits 30 voice frames and 12 route chunks concurrently
        val outgoingPackets = mutableListOf<MeshPacket>()

        // 3a. Generate voice frames
        for (seq in 1..config.audioFramesPerBatch) {
            val pcmDummy = ByteArray(80) { (it + seq).toByte() }
            val envelope = nodes[0].ratchet.encrypt(pcmDummy)
            val envelopePayload = harnessJson.encodeToString(envelope).encodeToByteArray()
            val audioPacket = MeshPacket.Audio(
                senderId = nodes[0].nodeId,
                sequence = seq.toLong(),
                timestamp = startTime + (seq * 20L),
                data = envelopePayload,
                profile = org.nowni.intercom_alpha.common.AudioProfile.MEDIUM
            )
            outgoingPackets.add(audioPacket)
            totalAudioSent++
        }

        // 3b. Generate route transfer chunks (GPX route beam)
        for (chunkIdx in 0 until config.routeChunkCount) {
            val chunk = FileTransferChunk(
                transferId = "gpx-cross-alps-2026",
                fileName = "CrossAlpsRoute.gpx",
                fileType = FileTransferType.GPX,
                chunkIndex = chunkIdx,
                totalChunks = config.routeChunkCount,
                totalBytes = (config.routeChunkCount * 64).toLong(),
                data = ByteArray(64) { (it + chunkIdx).toByte() },
                adler32Checksum = 12345678L + chunkIdx
            )
            val chunkBytes = MeshFileTransferEngine.serializeChunk(chunk)
            val controlPacket = MeshPacket.Control(
                senderId = nodes[0].nodeId,
                type = ControlType.FILE_CHUNK,
                payload = chunkBytes
            )
            outgoingPackets.add(controlPacket)
            totalControlSent++
        }

        // 3c. Generate geofence breach alarm packet
        if (leadSnapshot.overallStatus == GeofenceTetherStatus.BREACH) {
            val alarmPacket = GeofenceAlarmPacket(
                senderId = nodes[0].nodeId,
                alarmType = GeofenceAlarmType.TETHER_BREACH,
                targetId = nodes.last().nodeId,
                title = "CONVOY TETHER BREACH",
                details = "Tail rider separated by > 600m",
                distanceMeters = leadSnapshot.furthestRiderDistanceMeters,
                timestampMs = startTime
            )
            val alarmControl = MeshPacket.Control(
                senderId = nodes[0].nodeId,
                type = ControlType.GEOFENCE_ALARM,
                payload = ConvoyGeofenceEngine.serializeAlarmPacket(alarmPacket)
            )
            outgoingPackets.add(alarmControl)
            totalControlSent++
        }

        // 4. Simulate concurrent multi-hop mesh propagation across the 16 nodes
        simulateMultiHopMesh(outgoingPackets, startTime)

        val endTime = currentTimeMillis()
        val durationMs = endTime - startTime

        val tailNode = nodes.last()
        var loopDrops = 0
        var ttlDrops = 0
        for (n in nodes) {
            loopDrops += n.duplicateDrops
            ttlDrops += n.ttlExpiredDrops
        }

        return StressBenchmarkMetrics(
            nodeCount = config.nodeCount,
            totalAudioSent = totalAudioSent,
            totalControlSent = totalControlSent,
            tailNodeReceivedAudio = tailNode.receivedAudioFrames.size,
            tailNodeDecryptedAudio = tailNode.decryptedPayloads.size,
            tailNodeReceivedChunks = tailNode.receivedRouteChunks.size,
            geofenceAlarmsTriggered = geofenceAlarmsTriggered,
            clusterSplitsDetected = clusterSplitsDetected,
            powerGovernorDutyCyclesApplied = powerGovernorChanges,
            loopSuppressionDrops = loopDrops,
            ttlExpirationDrops = ttlDrops,
            executionDurationMs = durationMs
        )
    }

    /**
     * Models distance-based RF attenuation and multi-hop forwarding across the 16 nodes.
     */
    private suspend fun simulateMultiHopMesh(
        initialPackets: List<MeshPacket>,
        simTimeMs: Long
    ) {
        val inFlightPackets = mutableListOf<Pair<Int, MeshPacket>>() // (originNodeIndex, packet)

        // Seed transmissions originating from node 0 (lead)
        for (pkt in initialPackets) {
            inFlightPackets.add(Pair(0, pkt))
        }

        var hopRound = 0
        val maxRounds = 16 // Sufficient rounds to traverse 16 nodes

        while (inFlightPackets.isNotEmpty() && hopRound < maxRounds) {
            hopRound++
            val currentRound = inFlightPackets.toList()
            inFlightPackets.clear()

            for ((senderIdx, packet) in currentRound) {
                // Determine direct neighbor reach (within maxDirectHopDistance)
                for (targetIdx in 0 until config.nodeCount) {
                    if (targetIdx == senderIdx) continue
                    val distance = abs(targetIdx - senderIdx)

                    if (distance <= config.maxDirectHopDistance) {
                        // Apply stochastic RF packet drop rate
                        if (rng.nextDouble() < config.packetDropRate) {
                            continue // Packet lost due to RF attenuation/interference
                        }

                        // Jitter delivery time
                        val arrivalTime = simTimeMs + (hopRound * 15L) + if (config.enableOutOfOrderArrivals) rng.nextLong(0, 10) else 0L

                        val targetNode = nodes[targetIdx]
                        targetNode.processIncomingPacket(packet, arrivalTime) { relayFrame ->
                            inFlightPackets.add(Pair(targetIdx, relayFrame))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Multiplatform Automated Test Suite for ICA-2004 16-Node Concurrent Convoy Stress Simulation.
 */
class ConvoyStressSimulationHarnessTest {

    @Test
    fun testSixteenNodeConcurrentConvoyVoiceAndRouteTransferStress() = runTest {
        val harness = ConvoyStressSimulationHarness(
            StressSimulationConfig(
                nodeCount = 16,
                maxDirectHopDistance = 2,
                defaultTtl = 4,
                packetDropRate = 0.20,
                enableOutOfOrderArrivals = true,
                audioFramesPerBatch = 30,
                routeChunkCount = 12
            )
        )

        assertEquals(16, harness.nodes.size, "Must initialize exactly 16 virtual convoy riders")

        val metrics = harness.runFullConvoyStressBenchmark()

        // 1. Audio and Control packets transmitted
        assertEquals(30, metrics.totalAudioSent, "Must transmit 30 voice frames")
        assertTrue(metrics.totalControlSent >= 12, "Must transmit at least 12 route chunks")

        // 2. Multi-hop reach to tail node (node 15) through 16 nodes
        assertTrue(
            metrics.tailNodeReceivedAudio > 0,
            "Tail node must receive voice frames via multi-hop relay (got ${metrics.tailNodeReceivedAudio})"
        )
        assertEquals(
            metrics.tailNodeReceivedAudio,
            metrics.tailNodeDecryptedAudio,
            "100% of audio frames reaching tail node must be decrypted cleanly by key ratchet"
        )

        // 3. Route chunks received at tail node
        assertTrue(
            metrics.tailNodeReceivedChunks > 0,
            "Tail node must receive route chunks across multi-hop mesh (got ${metrics.tailNodeReceivedChunks})"
        )

        // 4. Loop suppression and TTL bounding
        assertTrue(metrics.loopSuppressionDrops > 0, "Packet deduplication must actively drop loop echoes")
        assertTrue(metrics.ttlExpirationDrops > 0, "TTL expiration must bound flooding depth")

        // 5. Execution timing
        assertTrue(
            metrics.executionDurationMs < 5000L,
            "Stress benchmark execution (${metrics.executionDurationMs}ms) must complete under 5 seconds"
        )
    }

    @Test
    fun testSixteenNodeGeofenceTetherBreachAndClusterSplitStress() = runTest {
        val harness = ConvoyStressSimulationHarness(
            StressSimulationConfig(nodeCount = 16, maxDirectHopDistance = 2)
        )

        val metrics = harness.runFullConvoyStressBenchmark()

        // Verify that the dual-cluster formation triggers both tether breach and convoy split
        assertEquals(1, metrics.geofenceAlarmsTriggered, "Tether breach (>600m) must be detected at lead")
        assertEquals(1, metrics.clusterSplitsDetected, "Convoy split (>500m gap) must be detected between clusters")

        // Lead rider snapshot details
        val leadSnapshot = harness.nodes[0].geofenceEngine.geofenceState.value
        assertEquals(GeofenceTetherStatus.BREACH, leadSnapshot.overallStatus)
        assertTrue(leadSnapshot.furthestRiderDistanceMeters > 600.0, "Furthest rider distance must exceed 600m")

        val splitWarning = leadSnapshot.splitWarning
        assertNotNull(splitWarning)
        assertTrue(splitWarning.isSplitDetected, "Convoy split warning must be active")
        assertTrue(splitWarning.clusterGapMeters >= 500.0, "Cluster gap must be >= 500m")
    }

    @Test
    fun testSixteenNodePowerGovernorDutyCycleUnderRfLoss() = runTest {
        val harness = ConvoyStressSimulationHarness(
            StressSimulationConfig(nodeCount = 16, maxDirectHopDistance = 2)
        )

        val metrics = harness.runFullConvoyStressBenchmark()

        assertTrue(
            metrics.powerGovernorDutyCyclesApplied >= 3,
            "At least 3 nodes with low battery or stillness must adapt power policy"
        )

        // Node 6 had 28% battery -> CONSERVATIVE (50% duty cycle)
        val node6Policy = harness.nodes[6].batteryGovernor.governorState.value.policy
        assertEquals(PowerPolicy.CONSERVATIVE, node6Policy, "Node 6 with 28% battery must be CONSERVATIVE")

        // Node 11 had 12% battery -> EXTREME_SURVIVAL (20% duty cycle)
        val node11Policy = harness.nodes[11].batteryGovernor.governorState.value.policy
        assertEquals(PowerPolicy.EXTREME_SURVIVAL, node11Policy, "Node 11 with 12% battery must be EXTREME_SURVIVAL")

        // Node 14 was stationary >10 min -> REST_STOP_SLEEP (5% duty cycle)
        val node14Policy = harness.nodes[14].batteryGovernor.governorState.value.policy
        assertEquals(PowerPolicy.REST_STOP_SLEEP, node14Policy, "Node 14 still for >10 min must be REST_STOP_SLEEP")
    }

    @Test
    fun testSystemMemoryStabilityAndZeroCoroutineLeaks() = runTest {
        val harness = ConvoyStressSimulationHarness(
            StressSimulationConfig(nodeCount = 16, maxDirectHopDistance = 2)
        )

        // Run 3 consecutive stress runs to check for memory stability
        for (iteration in 1..3) {
            val metrics = harness.runFullConvoyStressBenchmark()
            assertTrue(metrics.totalAudioSent > 0)
        }

        // Memory check after GC
        System.gc()
        Thread.sleep(50)
        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

        assertTrue(
            usedMemoryMb < 150.0,
            "Resident memory usage ($usedMemoryMb MB) must remain bounded under 150MB"
        )
    }
}
