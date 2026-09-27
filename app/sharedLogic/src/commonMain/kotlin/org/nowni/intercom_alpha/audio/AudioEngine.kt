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
    val voxThresholdDb: Float = -40f
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