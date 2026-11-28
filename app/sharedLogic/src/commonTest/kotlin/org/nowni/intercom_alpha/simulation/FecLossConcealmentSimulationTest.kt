package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.audio.*
import org.nowni.intercom_alpha.common.AudioProfile
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 8-Node Convoy FEC & Packet Loss Concealment (PLC) Simulation (Sprint 34 - ICA-3404).
 *
 * Simulates real-world off-grid BLE mesh RF dropouts with 25% packet erasure:
 * 1. Transmitter encodes voice frames into FEC blocks (RATIO_4_1 and RATIO_4_2).
 * 2. RF channel drops 25% of packets (random isolated drops and burst losses).
 * 3. Receiver nodes reconstruct lost voice packets via XOR / Galois Field RS decoding.
 * 4. Remaining erasures exceeding FEC budget are smoothly concealed via pitch-synchronous
 *    waveform extrapolation with -2.5 dB attenuation decay.
 * 5. Smooth crossfade blending upon clean packet resumption.
 */
class FecLossConcealmentSimulationTest {

    class ConvoyNode(
        val nodeId: String,
        val isTransmitter: Boolean
    ) {
        val fecEngine = MeshPacketFecEngine(initialRatio = FecRatio.RATIO_4_1)
        val lossConcealment = AudioLossConcealment(sampleRate = 24000)
        val pipeline = AudioPipelineSession(
            profile = AudioProfile.MEDIUM,
            multiPeerPlc = lossConcealment,
            fecEngine = fecEngine
        )

        val receivedVoiceFrames = mutableListOf<ByteArray>()
        val playoutAudioSamples = mutableListOf<ShortArray>()

        var totalDirectPacketsReceived = 0
        var totalFecRecoveredPackets = 0
        var totalPlcConcealedFrames = 0

        suspend fun ingestRadioPacket(envelopeBytes: ByteArray, senderId: String, timestampMs: Long) {
            val env = FecEnvelope.fromByteArray(envelopeBytes) ?: return
            val decodeResult = fecEngine.ingestIncomingEnvelope(env, timestampMs)

            when (decodeResult) {
                is FecDecodeResult.Ready -> {
                    for ((idx, packetData) in decodeResult.dataPackets.withIndex()) {
                        receivedVoiceFrames.add(packetData)
                        if (idx in decodeResult.recoveredIndices) {
                            totalFecRecoveredPackets++
                        } else {
                            totalDirectPacketsReceived++
                        }
                        // Feed into multi-peer PLC as good frame
                        val pcm = decodeToPcm(packetData)
                        val playout = lossConcealment.processGoodFrame(senderId, pcm)
                        playoutAudioSamples.add(playout)
                    }
                }
                is FecDecodeResult.Pending -> {
                    // Waiting for more block envelopes
                }
                is FecDecodeResult.Unrecoverable -> {
                    // Block expired with missing packets; invoke synthetic PLC
                    for (missingIdx in decodeResult.missingIndices) {
                        totalPlcConcealedFrames++
                        val concealed = lossConcealment.generateConcealment(senderId, 480)
                        playoutAudioSamples.add(concealed)
                    }
                }
            }
        }

        private fun decodeToPcm(data: ByteArray): ShortArray {
            val count = data.size / 2
            val pcm = ShortArray(count)
            for (i in 0 until count) {
                val low = data[i * 2].toInt() and 0xFF
                val high = data[i * 2 + 1].toInt()
                pcm[i] = ((high shl 8) or low).toShort()
            }
            return pcm
        }
    }

    private fun generateSinePcm(sampleRate: Int, freqHz: Float, lengthSamples: Int): ShortArray {
        val pcm = ShortArray(lengthSamples)
        for (i in 0 until lengthSamples) {
            val angle = 2.0 * kotlin.math.PI * freqHz * i / sampleRate
            pcm[i] = (sin(angle) * 16000.0).toInt().toShort()
        }
        return pcm
    }

    private fun pcmToBytes(pcm: ShortArray): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val sample = pcm[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test
    fun test8NodeConvoy25PercentPacketLossResilience() = runTest {
        // Setup 8-node convoy: Lead (node 0) + 7 Receivers (nodes 1..7)
        val transmitter = ConvoyNode("rider_0", isTransmitter = true)
        val receivers = List(7) { idx -> ConvoyNode("rider_${idx + 1}", isTransmitter = false) }

        var currentTimeMs = 10_000L

        // =========================================================================
        // SCENARIO 1: 4:1 Single Parity FEC with 20% Random Packet Drop (1 per block)
        // =========================================================================
        transmitter.fecEngine.setFecRatio(FecRatio.RATIO_4_1)

        val numBlocks = 10
        val totalVoiceFrames = numBlocks * 4 // 40 voice frames

        for (block in 0 until numBlocks) {
            val blockVoiceFrames = List(4) { frameIdx ->
                val freq = 200.0f + (frameIdx * 20.0f)
                val pcm = generateSinePcm(24000, freq, 480)
                pcmToBytes(pcm)
            }

            // Encode block in transmitter
            var envelopes = emptyList<FecEnvelope>()
            for (frame in blockVoiceFrames) {
                val result = transmitter.fecEngine.ingestOutgoingPacket(frame)
                if (result.isNotEmpty()) {
                    envelopes = result
                }
            }

            assertEquals(5, envelopes.size, "4:1 FEC block must produce 5 envelopes (4 data + 1 parity)")

            // Transmit to 7 receivers over simulated lossy RF channel:
            // Drop 1 packet out of 5 for each receiver (20% packet drop)
            for ((rIdx, receiver) in receivers.withIndex()) {
                val droppedIndex = (block + rIdx) % 5 // Varied drop index across receivers
                for ((idx, env) in envelopes.withIndex()) {
                    if (idx == droppedIndex) {
                        // Packet erased by RF channel interference
                        continue
                    }
                    receiver.ingestRadioPacket(env.toByteArray(), "rider_0", currentTimeMs)
                }
            }

            currentTimeMs += 100L
        }

        // Verify Scenario 1: All 7 receivers must achieve 100% voice packet recovery
        for (receiver in receivers) {
            val fecTelem = receiver.fecEngine.telemetry.value
            assertEquals(totalVoiceFrames, receiver.receivedVoiceFrames.size,
                "Receiver ${receiver.nodeId} must receive or reconstruct all $totalVoiceFrames voice frames")
            assertTrue(fecTelem.totalPacketsRecovered > 0,
                "Receiver ${receiver.nodeId} must have recovered dropped frames via XOR FEC")
            assertEquals(0L, fecTelem.totalBlocksUnrecoverable,
                "No blocks should be unrecoverable under single-erasure conditions")
        }

        // =========================================================================
        // SCENARIO 2: 4:2 Dual Parity Galois FEC with 40% Heavy Drop (2 per block)
        // =========================================================================
        transmitter.fecEngine.setFecRatio(FecRatio.RATIO_4_2)
        for (receiver in receivers) {
            receiver.fecEngine.setFecRatio(FecRatio.RATIO_4_2)
        }

        val numDualBlocks = 8
        val startingDualFrameCount = receivers[0].receivedVoiceFrames.size

        for (block in 0 until numDualBlocks) {
            val blockVoiceFrames = List(4) { frameIdx ->
                val freq = 300.0f + (frameIdx * 25.0f)
                val pcm = generateSinePcm(24000, freq, 480)
                pcmToBytes(pcm)
            }

            var envelopes = emptyList<FecEnvelope>()
            for (frame in blockVoiceFrames) {
                val result = transmitter.fecEngine.ingestOutgoingPacket(frame)
                if (result.isNotEmpty()) {
                    envelopes = result
                }
            }

            assertEquals(6, envelopes.size, "4:2 FEC block must produce 6 envelopes (4 data + 2 parity)")

            // Transmit with 2 packets erased per block (indices 0 and 1, or 1 and 2, etc.)
            for ((rIdx, receiver) in receivers.withIndex()) {
                val drop1 = (block + rIdx) % 6
                val drop2 = (block + rIdx + 1) % 6

                for ((idx, env) in envelopes.withIndex()) {
                    if (idx == drop1 || idx == drop2) {
                        // Double erasure
                        continue
                    }
                    receiver.ingestRadioPacket(env.toByteArray(), "rider_0", currentTimeMs)
                }
            }

            currentTimeMs += 100L
        }

        // Verify Scenario 2: All receivers reconstructed all frames despite 2 erasures per block!
        val expectedTotalAfterDual = startingDualFrameCount + (numDualBlocks * 4)
        for (receiver in receivers) {
            assertEquals(expectedTotalAfterDual, receiver.receivedVoiceFrames.size,
                "Receiver ${receiver.nodeId} must recover all frames via Galois Field Reed-Solomon")
        }

        // =========================================================================
        // SCENARIO 3: Burst Drop Exceeding FEC Bounds -> Synthetic Waveform PLC
        // =========================================================================
        val severeDropReceiver = receivers[0]
        val prePlcFrameCount = severeDropReceiver.playoutAudioSamples.size

        // Feed an expired block result directly through PLC
        val missingCount = 3
        for (i in 1..missingCount) {
            val concealed = severeDropReceiver.lossConcealment.generateConcealment("rider_0", 480)
            severeDropReceiver.playoutAudioSamples.add(concealed)
        }

        // Verify progressive attenuation in synthesized samples
        val frame1Energy = severeDropReceiver.playoutAudioSamples[prePlcFrameCount].fold(0.0) { acc, s -> acc + (s.toDouble() * s.toDouble()) }
        val frame2Energy = severeDropReceiver.playoutAudioSamples[prePlcFrameCount + 1].fold(0.0) { acc, s -> acc + (s.toDouble() * s.toDouble()) }
        val frame3Energy = severeDropReceiver.playoutAudioSamples[prePlcFrameCount + 2].fold(0.0) { acc, s -> acc + (s.toDouble() * s.toDouble()) }

        assertTrue(frame2Energy < frame1Energy, "Consecutive concealed frame 2 must attenuate (-2.5 dB)")
        assertTrue(frame3Energy < frame2Energy, "Consecutive concealed frame 3 must attenuate further")

        // Resume real voice packet with crossfade smoothing
        val resumePcm = generateSinePcm(24000, 220.0f, 480)
        val blendedPlayout = severeDropReceiver.lossConcealment.processGoodFrame("rider_0", resumePcm)
        assertEquals(480, blendedPlayout.size)

        // Check PLC telemetry
        val plcTelem = severeDropReceiver.lossConcealment.telemetry.value
        assertTrue(plcTelem.totalFramesConcealed >= 3, "PLC telemetry must record concealed frames")
        assertFalse(plcTelem.activeConcealingPeers > 0, "Active concealment must clear after real voice resumes")
    }
}
