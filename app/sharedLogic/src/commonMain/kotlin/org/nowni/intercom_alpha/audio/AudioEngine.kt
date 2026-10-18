package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.common.AudioProfile

interface AudioEngine {
    val outputLevel: ReceiveChannel<Float>
    val inputLevel: ReceiveChannel<Float>
    val isRecording: ReceiveChannel<Boolean>
    val recordedAudio: ReceiveChannel<ShortArray>

    suspend fun start(config: AudioEngineConfig)
    suspend fun stop()
    suspend fun playAudio(data: ShortArray)
    suspend fun encode(input: ShortArray, profile: AudioProfile): ByteArray
    suspend fun decode(input: ByteArray, profile: AudioProfile): ShortArray
    fun setVoxEnabled(enabled: Boolean)
    fun setVoxThreshold(thresholdDb: Float)
    fun setOutputVolume(volume: Float)
    fun getOutputVolume(): Float
}

@Serializable
data class AudioEngineConfig(
    val sampleRate: Int = 24000,
    val channels: Int = 1,
    val frameSizeMs: Int = 20,
    val bitrateKbps: Int = 32,
    val enableDtx: Boolean = true,
    val enableFec: Boolean = true,
    val enableVox: Boolean = false,
    val voxThresholdDb: Float = -40f,
    val enableAgc: Boolean = true,
    val agcTargetDb: Float = -18f,
    val enableDrc: Boolean = true
)

interface AudioRecorder {
    val audioData: ReceiveChannel<ShortArray>
    suspend fun start()
    suspend fun stop()
}

interface AudioPlayer {
    val playChannel: SendChannel<ShortArray>
    suspend fun start()
    suspend fun stop()
}

/**
 * End-to-end full duplex audio processing pipeline session.
 * Integrates:
 * - Outgoing path: [NoiseGate] (acoustic noise suppression & VOX) -> [AutomaticGainControl] (speech normalization)
 *   -> [DynamicRangeCompressor] (soft-knee compression & -0.5 dBFS peak limiter) -> [AudioCodec] (multi-rate compression).
 * - Incoming path: [AdaptiveJitterBuffer] (RFC 3550 playout sequencing) -> [AudioCodec] (decoding)
 *   -> [PacketLossConcealment] (waveform extrapolation & crossfading) -> [DynamicRangeCompressor] (acoustic protection).
 */
class AudioPipelineSession(
    val profile: AudioProfile = AudioProfile.MEDIUM,
    val sampleRate: Int = profile.sampleRateHz,
    val frameSizeSamples: Int = (profile.sampleRateHz * profile.frameSizeMs) / 1000,
    val jitterBuffer: AdaptiveJitterBuffer = AdaptiveJitterBuffer(frameDurationMs = profile.frameSizeMs.toLong()),
    val plc: PacketLossConcealment = PacketLossConcealment(sampleRate = sampleRate),
    val noiseGate: NoiseGate = NoiseGate(sampleRate = sampleRate),
    val codec: AudioCodec = AudioCodec(defaultProfile = profile),
    val agc: AutomaticGainControl = AutomaticGainControl(sampleRate = sampleRate),
    val drc: DynamicRangeCompressor = DynamicRangeCompressor(sampleRate = sampleRate),
    val playoutLimiter: DynamicRangeCompressor = DynamicRangeCompressor(sampleRate = sampleRate),
    val multiPeerPlc: AudioLossConcealment = AudioLossConcealment(sampleRate = sampleRate),
    val fecEngine: MeshPacketFecEngine = MeshPacketFecEngine(),
    val mtuAggregator: org.nowni.intercom_alpha.mesh.BleMtuAggregator = org.nowni.intercom_alpha.mesh.BleMtuAggregator(),
    val frameInterleaver: org.nowni.intercom_alpha.mesh.BleFrameInterleaver = org.nowni.intercom_alpha.mesh.BleFrameInterleaver()
) {
    var activeProfile: AudioProfile = profile

    /**
     * Packs outgoing voice/control payload into negotiated BLE ATT MTU packet(s).
     */
    fun packOutgoingPayload(
        payload: ByteArray,
        type: org.nowni.intercom_alpha.mesh.SubFrameType = org.nowni.intercom_alpha.mesh.SubFrameType.VOICE_AUDIO,
        timestampMs: Long = 0L
    ): List<org.nowni.intercom_alpha.mesh.PackedMtuPacket> {
        return mtuAggregator.addFrame(payload, type, timestampMs)
    }

    /**
     * Unpacks an over-the-air raw packed BLE ATT MTU packet into individual sub-frames.
     */
    fun unpackIncomingPayload(packedBytes: ByteArray): List<org.nowni.intercom_alpha.mesh.SubFrame> {
        return mtuAggregator.unpack(packedBytes)
    }

    /**
     * Updates the current negotiated BLE ATT MTU.
     */
    fun setNegotiatedMtu(mtu: Int) {
        mtuAggregator.setNegotiatedMtu(mtu)
    }

    /**
     * Retrieves current BLE ATT MTU packing telemetry.
     */
    fun getMtuTelemetry(): org.nowni.intercom_alpha.mesh.BleMtuTelemetry {
        return mtuAggregator.getTelemetry()
    }

    /**
     * Processes encoded voice payload through the FEC engine, generating FEC parity packets when a block completes.
     */
    suspend fun encodeOutgoingFec(payload: ByteArray): List<FecEnvelope> {
        return fecEngine.ingestOutgoingPacket(payload)
    }

    /**
     * Ingests incoming wire payload through FEC erasure reconstruction.
     */
    suspend fun decodeIncomingFec(envelope: FecEnvelope, timestampMs: Long): FecDecodeResult {
        return fecEngine.ingestIncomingEnvelope(envelope, timestampMs)
    }

    /**
     * Updates active codec profile for outgoing transmission on the fly.
     */
    fun updateActiveProfile(newProfile: AudioProfile) {
        activeProfile = newProfile
    }

    /**
     * Processes raw microphone input. Returns compressed byte packet, or null if gated by VOX/DTX.
     */
    fun processOutgoingMicrophone(rawPcm: ShortArray, dynamicProfile: AudioProfile = activeProfile): ByteArray? {
        val gated = noiseGate.process(rawPcm)
        if (!noiseGate.isOpen && noiseGate.currentEnvelopeDb < -55.0f) {
            return null // Discontinuous Transmission (DTX) suppresses background silence packets
        }
        val leveled = agc.process(gated)
        val compressed = drc.process(leveled)
        return codec.encode(compressed, dynamicProfile)
    }

    /**
     * Enqueues incoming mesh audio packet into the jitter buffer.
     */
    suspend fun ingestIncomingPacket(packet: org.nowni.intercom_alpha.mesh.MeshPacket.Audio, arrivalTimestampMs: Long): Boolean {
        return jitterBuffer.push(packet, arrivalTimestampMs)
    }

    /**
     * Pulls the next sequenced audio frame on the local playout clock deadline.
     */
    suspend fun pullPlayoutFrame(currentTimeMs: Long): ShortArray {
        val frame = jitterBuffer.pull(currentTimeMs)
        val rawPlayout = when (frame) {
            is PlayoutFrame.Available -> {
                val payload = frame.data
                val decoded = if (payload.isNotEmpty() && payload[0] == AudioCodec.MAGIC_BYTE) {
                    codec.decode(payload)
                } else {
                    decodeLegacyPcm(payload)
                }
                plc.pushGoodFrame(decoded)
            }
            is PlayoutFrame.Concealment -> {
                plc.conceal(frameSizeSamples)
            }
            is PlayoutFrame.Buffering, is PlayoutFrame.Empty -> {
                ShortArray(frameSizeSamples)
            }
        }
        return playoutLimiter.process(rawPlayout)
    }

    private fun decodeLegacyPcm(payload: ByteArray): ShortArray {
        val count = payload.size / 2
        val out = ShortArray(count)
        for (i in 0 until count) {
            val low = payload[i * 2].toInt() and 0xFF
            val high = payload[i * 2 + 1].toInt()
            out[i] = ((high shl 8) or low).toShort()
        }
        return out
    }
}