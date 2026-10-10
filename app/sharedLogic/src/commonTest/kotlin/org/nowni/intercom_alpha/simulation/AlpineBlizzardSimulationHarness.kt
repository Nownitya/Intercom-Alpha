package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.audio.AcousticWakeWordEngine
import org.nowni.intercom_alpha.audio.NoiseSuppressionMode
import org.nowni.intercom_alpha.audio.SpectralNoiseProfile
import org.nowni.intercom_alpha.audio.SpectralNoiseSuppressor
import org.nowni.intercom_alpha.audio.VoxState
import org.nowni.intercom_alpha.audio.WakeWordConfig
import org.nowni.intercom_alpha.audio.WakeWordType
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.mesh.qos.AirtimeCongestionGovernor
import org.nowni.intercom_alpha.mesh.qos.ChannelCongestionLevel
import org.nowni.intercom_alpha.mesh.qos.FecRedundancyLevel
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 32-Node Alpine Blizzard Acoustic & RF Contention Benchmark Simulation Harness.
 *
 * Validates under severe sub-zero storm conditions:
 * 1. 32-node convoy scaling through extreme aerodynamic wind turbulence (>100 km/h).
 * 2. Pure KMP Spectral Subtraction speech enhancement reducing noise power by > 2x across all nodes.
 * 3. Offline hands-free keyword spotting ("Hey Alpha" and "Emergency SOS") with continuous VOX state tracking.
 * 4. Channel Busy Ratio (CBR) monitoring, dynamic FEC adaptation, and bulk airtime pacing under peak contention.
 * 5. Immediate emergency SOS preemption broadcasting lifeline distress packets with 100% receipt.
 */
class AlpineBlizzardSimulationHarness {

    data class BlizzardBenchmarkTelemetry(
        var totalAudioFramesEnhanced: Int = 0,
        var totalWakeWordsSpotted: Int = 0,
        var totalCongestionAlertsTriggered: Int = 0,
        var totalPacketsDelivered: Int = 0,
        var totalEmergencyAlertsReceived: Int = 0
    )

    class VirtualBlizzardNode(
        val nodeIndex: Int,
        val nodeId: String,
        val riderName: String,
        var virtualClock: () -> Long
    ) {
        val deduplicator = PacketDeduplicator(windowMs = 15_000L, maxCacheSize = 1000)
        val noiseSuppressor = SpectralNoiseSuppressor(
            sampleRateHz = 16000,
            fftSize = 256,
            initialProfile = SpectralNoiseProfile.EXTREME_ALPINE
        )
        val wakeWordEngine = AcousticWakeWordEngine(
            WakeWordConfig(
                sampleRateHz = 16000,
                confidenceThreshold = 0.60f,
                energyThresholdDb = -45.0f,
                holdTailDurationMs = 600L,
                cooldownDurationMs = 500L
            )
        )
        val congestionGovernor = AirtimeCongestionGovernor()

        val receivedAudioPackets = mutableListOf<MeshPacket.Audio>()
        val receivedEmergencyPackets = mutableListOf<MeshPacket.Control>()

        suspend fun receivePacket(packet: MeshPacket) {
            if (!deduplicator.shouldProcess(packet, virtualClock())) return

            when (packet) {
                is MeshPacket.Audio -> receivedAudioPackets.add(packet)
                is MeshPacket.Control -> {
                    if (packet.type == org.nowni.intercom_alpha.mesh.ControlType.CRASH_ALERT) {
                        receivedEmergencyPackets.add(packet)
                    }
                }
                else -> {}
            }
        }
    }

    class SimulatedBlizzardConvoy(val nodeCount: Int = 32) {
        var currentClockMs: Long = 10_000L
        val telemetry = BlizzardBenchmarkTelemetry()

        val nodes: List<VirtualBlizzardNode> = (1..nodeCount).map { index ->
            val id = "blizzard-rider-${index.toString().padStart(2, '0')}"
            VirtualBlizzardNode(
                nodeIndex = index,
                nodeId = id,
                riderName = "Rider-$index",
                virtualClock = { currentClockMs }
            )
        }

        fun advanceTime(deltaMs: Long) {
            currentClockMs += deltaMs
        }

        suspend fun broadcastPacket(sender: VirtualBlizzardNode, packet: MeshPacket, priority: QoSPriority) {
            // Check airtime governor throttling on sender
            if (sender.congestionGovernor.shouldThrottle(priority, currentClockMs - 50, currentClockMs)) {
                return
            }

            // Record airtime event across all nodes in mutual RF range
            val packetAirtimeMicros = when (packet) {
                is MeshPacket.Audio -> 2500L
                is MeshPacket.Control -> 1200L
                else -> 800L
            }
            nodes.forEach { node ->
                node.congestionGovernor.recordAirtimeEvent(
                    durationMicros = packetAirtimeMicros,
                    isTransmit = (node.nodeId == sender.nodeId),
                    priority = priority,
                    timestampMs = currentClockMs
                )
            }

            // Flood packet across all nodes in the mesh
            nodes.forEach { recipient ->
                if (recipient.nodeId != sender.nodeId) {
                    recipient.receivePacket(packet)
                    telemetry.totalPacketsDelivered++
                    if (priority == QoSPriority.EMERGENCY_CRITICAL) {
                        telemetry.totalEmergencyAlertsReceived++
                    }
                }
            }
        }
    }

    @Test
    fun test32NodeAlpineBlizzardFullLifecycle() = runTest {
        val convoy = SimulatedBlizzardConvoy(nodeCount = 32)
        val random = Random(777)

        assertEquals(32, convoy.nodes.size)
        val leadNode = convoy.nodes.first()
        val tailNode = convoy.nodes.last()

        // -----------------------------------------------------------------------------------------
        // Stage 1: Storm Ascent & Acoustic Noise Suppression across All 32 Nodes
        // -----------------------------------------------------------------------------------------
        val blizzardNoiseSamples = ShortArray(1024) { random.nextInt(-4000, 4000).toShort() }

        var totalPreEnergy = 0.0
        var totalPostEnergy = 0.0

        for (node in convoy.nodes) {
            val processed = node.noiseSuppressor.processSamples(blizzardNoiseSamples)
            assertEquals(blizzardNoiseSamples.size, processed.size)
            convoy.telemetry.totalAudioFramesEnhanced++

            for (i in 512 until blizzardNoiseSamples.size) {
                totalPreEnergy += blizzardNoiseSamples[i].toDouble() * blizzardNoiseSamples[i].toDouble()
                totalPostEnergy += processed[i].toDouble() * processed[i].toDouble()
            }
        }

        val attenuationFactor = totalPreEnergy / (totalPostEnergy + 1.0)
        assertTrue(
            attenuationFactor > 2.0,
            "Spectral noise suppressor must reduce blizzard storm noise by > 2x (observed: $attenuationFactor)"
        )

        // -----------------------------------------------------------------------------------------
        // Stage 2: Hands-Free Voice Activation ("Hey Alpha") on Lead Rider
        // -----------------------------------------------------------------------------------------
        convoy.advanceTime(100L)
        val frameSamples = 320 // 20ms @ 16kHz
        var leadWakeWordTriggered = false

        // Syllable 1 ("Hey"): Voiced tone (200ms)
        for (f in 0 until 10) {
            val samples = ShortArray(frameSamples) { i ->
                (12000 * sin(2.0 * PI * 440.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            leadNode.wakeWordEngine.processSamples(samples, convoy.currentClockMs)
            convoy.advanceTime(20L)
        }

        // Syllable 2 ("Al"): Open vowel (160ms)
        for (f in 0 until 8) {
            val samples = ShortArray(frameSamples) { i ->
                (10000 * sin(2.0 * PI * 800.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            leadNode.wakeWordEngine.processSamples(samples, convoy.currentClockMs)
            convoy.advanceTime(20L)
        }

        // Syllable 3 ("-pha"): Fricative high-frequency noise (160ms)
        for (f in 0 until 8) {
            val samples = ShortArray(frameSamples) { random.nextInt(-8000, 8000).toShort() }
            leadNode.wakeWordEngine.processSamples(samples, convoy.currentClockMs)
            convoy.advanceTime(20L)
        }

        // Trailing silence (160ms)
        for (f in 0 until 8) {
            val silence = ShortArray(frameSamples) { 0 }
            val evt = leadNode.wakeWordEngine.processSamples(silence, convoy.currentClockMs)
            if (evt != null && evt.type == WakeWordType.HEY_ALPHA) {
                leadWakeWordTriggered = true
                convoy.telemetry.totalWakeWordsSpotted++
            }
            convoy.advanceTime(20L)
        }

        assertTrue(leadWakeWordTriggered, "Lead rider must trigger hands-free 'Hey Alpha' VOX transmission")
        assertEquals(VoxState.TRIGGERED, leadNode.wakeWordEngine.voxState.value)

        // Lead transmits 5 voice packets to the other 31 nodes
        for (seq in 1..5) {
            val audioPacket = MeshPacket.Audio(
                senderId = leadNode.nodeId,
                sequence = seq.toLong(),
                timestamp = convoy.currentClockMs,
                data = ByteArray(64) { 0x55.toByte() },
                profile = AudioProfile.MEDIUM
            )
            convoy.broadcastPacket(leadNode, audioPacket, QoSPriority.ACTIVE_VOICE)
            convoy.advanceTime(20L)
        }

        // Verify that all other 31 nodes received the voice packets
        for (node in convoy.nodes) {
            if (node.nodeId != leadNode.nodeId) {
                assertEquals(5, node.receivedAudioPackets.size, "Rider ${node.nodeId} must receive all 5 voice packets")
            }
        }

        // -----------------------------------------------------------------------------------------
        // Stage 3: Severe Mesh Airtime Contention & CBR Congestion Shaper
        // -----------------------------------------------------------------------------------------
        // Simulate 12 concurrent nodes transmitting burst audio and telemetry, spiking shared RF airtime
        for (step in 0 until 35) {
            for (speakerIdx in 0 until 12) {
                val spk = convoy.nodes[speakerIdx]
                convoy.nodes.forEach { node ->
                    node.congestionGovernor.recordAirtimeEvent(
                        durationMicros = 20_000L,
                        isTransmit = (node.nodeId == spk.nodeId),
                        priority = QoSPriority.ACTIVE_VOICE,
                        timestampMs = convoy.currentClockMs
                    )
                }
            }
            convoy.advanceTime(15L)
        }

        // Check tail node congestion state
        val tailCongestion = tailNode.congestionGovernor.metrics.value
        assertTrue(
            tailCongestion.congestionLevel == ChannelCongestionLevel.HIGH ||
            tailCongestion.congestionLevel == ChannelCongestionLevel.CRITICAL,
            "High RF airtime load must drive congestion governor to HIGH or CRITICAL"
        )
        assertTrue(
            tailCongestion.fecRedundancy == FecRedundancyLevel.MEDIUM ||
            tailCongestion.fecRedundancy == FecRedundancyLevel.HEAVY,
            "Congestion governor must scale FEC redundancy to protect voice under contention"
        )
        // Bulk telemetry is paced or suspended
        assertTrue(
            tailNode.congestionGovernor.shouldThrottle(QoSPriority.BULK_TELEMETRY, convoy.currentClockMs - 100, convoy.currentClockMs),
            "Bulk telemetry must be throttled during high blizzard airtime congestion"
        )

        // -----------------------------------------------------------------------------------------
        // Stage 4: Crevasse Fall Emergency SOS Priority Preemption
        // -----------------------------------------------------------------------------------------
        val crevasseVictim = convoy.nodes[16] // Rider 17
        convoy.advanceTime(200L)

        // Victim speaks "Emergency SOS": 14 frames voiced + 16 frames sibilant SOS bursts + silence
        for (f in 0 until 14) {
            val samples = ShortArray(frameSamples) { i ->
                (10000 * sin(2.0 * PI * 600.0 * (f * frameSamples + i) / 16000)).toInt().toShort()
            }
            crevasseVictim.wakeWordEngine.processSamples(samples, convoy.currentClockMs)
            convoy.advanceTime(20L)
        }
        for (f in 0 until 16) {
            val samples = ShortArray(frameSamples) { i ->
                val sign = if (i % 2 == 0) 1 else -1
                (sign * (7000 + random.nextInt(1500))).toShort()
            }
            crevasseVictim.wakeWordEngine.processSamples(samples, convoy.currentClockMs)
            convoy.advanceTime(20L)
        }

        var sosSpotted = false
        for (f in 0 until 8) {
            val silence = ShortArray(frameSamples) { 0 }
            val evt = crevasseVictim.wakeWordEngine.processSamples(silence, convoy.currentClockMs)
            if (evt != null && evt.type == WakeWordType.EMERGENCY_SOS) {
                sosSpotted = true
                convoy.telemetry.totalWakeWordsSpotted++
            }
            convoy.advanceTime(20L)
        }
        assertTrue(sosSpotted, "Crevasse victim must spot 'Emergency SOS' acoustic pattern")

        // Broadcast EMERGENCY_CRITICAL packet across the congested mesh
        val emergencyPacket = MeshPacket.Control(
            senderId = crevasseVictim.nodeId,
            type = org.nowni.intercom_alpha.mesh.ControlType.CRASH_ALERT,
            payload = "CREVASSE_FALL:LAT=45.9238:LON=6.8693:ELEV=3850M".encodeToByteArray()
        )

        // EMERGENCY_CRITICAL is never throttled even under maximum congestion
        assertFalse(
            crevasseVictim.congestionGovernor.shouldThrottle(QoSPriority.EMERGENCY_CRITICAL, convoy.currentClockMs, convoy.currentClockMs),
            "Emergency SOS must NEVER be throttled by congestion governor"
        )

        convoy.broadcastPacket(crevasseVictim, emergencyPacket, QoSPriority.EMERGENCY_CRITICAL)

        // Verify that all other 31 nodes received the emergency distress packet
        for (node in convoy.nodes) {
            if (node.nodeId != crevasseVictim.nodeId) {
                assertEquals(1, node.receivedEmergencyPackets.size, "Node ${node.nodeId} must receive the Emergency SOS packet")
            }
        }

        assertEquals(31, convoy.telemetry.totalEmergencyAlertsReceived)
        assertTrue(convoy.telemetry.totalAudioFramesEnhanced >= 32)
        assertTrue(convoy.telemetry.totalWakeWordsSpotted >= 2)
    }
}
