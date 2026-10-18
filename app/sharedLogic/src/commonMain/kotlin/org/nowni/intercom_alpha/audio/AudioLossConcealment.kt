package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real-time operational telemetry for the Packet Loss Concealment (PLC) subsystem.
 */
data class PlcTelemetry(
    val totalFramesConcealed: Long = 0L,
    val totalGoodFramesProcessed: Long = 0L,
    val activeConcealingPeers: Int = 0,
    val lastConcealedPeerId: String? = null,
    val concealmentRatePercent: Float = 0.0f
)

/**
 * Multiplatform Multi-Peer Packet Loss Concealment (PLC) & Waveform Interpolator.
 *
 * Provides pitch-synchronous waveform extrapolation, crossfade smoothing, and progressive
 * attenuation decay per transmitting peer in an off-grid BLE mesh intercom convoy.
 *
 * Features:
 * - Independent per-peer pitch tracking and history ring buffers (preventing cross-talk artifacts)
 * - Pitch-synchronous waveform extrapolation when frames are dropped
 * - 2.5 dB (~0.75 linear gain) progressive attenuation decay per consecutive lost frame up to [maxConsecutiveFrames]
 * - Smooth crossfade blending upon real voice resumption to prevent DC offset clicks
 * - Zero Java (`java.security.*`) or C/JNI dependencies (Rule 3)
 */
class AudioLossConcealment(
    val sampleRate: Int = 24000,
    val maxConsecutiveLosses: Int = 4,
    val lossAttenuationFactor: Float = 0.7498942f, // -2.5 dB per frame
    val crossfadeMs: Float = 4.0f
) {
    private val mutex = Mutex()

    private class PeerPlcState(
        val peerId: String,
        val plc: PacketLossConcealment,
        var consecutiveLosses: Int = 0,
        var totalConcealed: Long = 0L,
        var totalGood: Long = 0L
    )

    private val peerStates = mutableMapOf<String, PeerPlcState>()

    private var globalConcealed: Long = 0L
    private var globalGood: Long = 0L
    private var lastPeerId: String? = null

    private val _telemetry = MutableStateFlow(PlcTelemetry())
    val telemetry: StateFlow<PlcTelemetry> = _telemetry.asStateFlow()

    /**
     * Ingests a received PCM voice frame for [peerId].
     * If the peer was previously in concealment mode, performs smooth crossfade blending
     * over the start of the frame to eliminate discontinuity clicks.
     *
     * @param peerId Unique transmitting peer identifier.
     * @param pcmSamples Raw 16-bit PCM voice audio samples.
     * @return Blended PCM samples ready for DAC playback.
     */
    suspend fun processGoodFrame(peerId: String, pcmSamples: ShortArray): ShortArray = mutex.withLock {
        if (pcmSamples.isEmpty()) return@withLock pcmSamples

        val state = peerStates.getOrPut(peerId) {
            PeerPlcState(
                peerId = peerId,
                plc = PacketLossConcealment(
                    sampleRate = sampleRate,
                    maxConsecutiveLosses = maxConsecutiveLosses,
                    lossAttenuationFactor = lossAttenuationFactor,
                    crossfadeMs = crossfadeMs
                )
            )
        }

        val output = state.plc.pushGoodFrame(pcmSamples)
        state.consecutiveLosses = 0
        state.totalGood++
        globalGood++
        publishTelemetry()
        return@withLock output
    }

    /**
     * Synthetically generates a replacement PCM audio frame when a packet is dropped
     * or unrecoverable via FEC.
     *
     * @param peerId Unique transmitting peer identifier whose frame was dropped.
     * @param frameSize Number of PCM samples required (e.g. 480 samples for 20ms at 24kHz).
     * @return Synthesized PCM samples with progressive pitch attenuation.
     */
    suspend fun generateConcealment(peerId: String, frameSize: Int): ShortArray = mutex.withLock {
        if (frameSize <= 0) return@withLock ShortArray(0)

        val state = peerStates.getOrPut(peerId) {
            PeerPlcState(
                peerId = peerId,
                plc = PacketLossConcealment(
                    sampleRate = sampleRate,
                    maxConsecutiveLosses = maxConsecutiveLosses,
                    lossAttenuationFactor = lossAttenuationFactor,
                    crossfadeMs = crossfadeMs
                )
            )
        }

        val concealedSamples = state.plc.conceal(frameSize)
        state.consecutiveLosses = state.plc.consecutiveLossCount
        state.totalConcealed++
        globalConcealed++
        lastPeerId = peerId
        publishTelemetry()
        return@withLock concealedSamples
    }

    /**
     * Returns the current consecutive lost frame count for a specific peer.
     */
    suspend fun getConsecutiveLossCount(peerId: String): Int = mutex.withLock {
        peerStates[peerId]?.consecutiveLosses ?: 0
    }

    /**
     * Resets PLC history for a specific peer.
     */
    suspend fun resetPeer(peerId: String) = mutex.withLock {
        peerStates[peerId]?.plc?.reset()
        peerStates.remove(peerId)
        publishTelemetry()
    }

    /**
     * Resets all peer PLC states and telemetry.
     */
    suspend fun resetAll() = mutex.withLock {
        peerStates.values.forEach { it.plc.reset() }
        peerStates.clear()
        globalConcealed = 0L
        globalGood = 0L
        lastPeerId = null
        publishTelemetry()
    }

    private fun publishTelemetry() {
        val total = globalConcealed + globalGood
        val rate = if (total == 0L) 0.0f else ((globalConcealed.toFloat() / total.toFloat()) * 100.0f)
        val activeConcealing = peerStates.values.count { it.consecutiveLosses > 0 }

        _telemetry.value = PlcTelemetry(
            totalFramesConcealed = globalConcealed,
            totalGoodFramesProcessed = globalGood,
            activeConcealingPeers = activeConcealing,
            lastConcealedPeerId = lastPeerId,
            concealmentRatePercent = rate
        )
    }
}
