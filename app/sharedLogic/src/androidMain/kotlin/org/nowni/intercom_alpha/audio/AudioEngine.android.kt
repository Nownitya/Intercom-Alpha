package org.nowni.intercom_alpha.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nowni.intercom_alpha.common.AudioProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class AudioEngineImpl(
    private val context: Context,
    private val scope: CoroutineScope
) : AudioEngine {

    private val TAG = "AudioEngineImpl"
    private val mutex = Mutex()

    private val outputLevelChannel = Channel<Float>(Channel.CONFLATED)
    private val inputLevelChannel = Channel<Float>(Channel.CONFLATED)
    private val isRecordingChannel = Channel<Boolean>(Channel.CONFLATED)
    private val recordedAudioChannel = Channel<ShortArray>(Channel.BUFFERED)

    override val outputLevel: ReceiveChannel<Float> = outputLevelChannel
    override val inputLevel: ReceiveChannel<Float> = inputLevelChannel
    override val isRecording: ReceiveChannel<Boolean> = isRecordingChannel
    override val recordedAudio: ReceiveChannel<ShortArray> = recordedAudioChannel

    private var recorder: AudioRecorderAndroid? = null
    private var player: AudioPlayerAndroid? = null
    private var recordForwardJob: Job? = null

    private var voxEnabled: Boolean = false
    private var voxThresholdDb: Float = -40f
    private var outputVolume: Float = 1.0f

    override suspend fun start(config: AudioEngineConfig) = withContext(Dispatchers.Default) {
        mutex.withLock {
            Log.d(TAG, "Starting AudioEngine with config: $config")
            voxEnabled = config.enableVox
            voxThresholdDb = config.voxThresholdDb

            stopInternal()

            val rec = AudioRecorderAndroid(context, scope, config, voxEnabled, voxThresholdDb) { level, recording ->
                inputLevelChannel.trySend(level)
                isRecordingChannel.trySend(recording)
            }
            val play = AudioPlayerAndroid(context, scope, config, outputVolume) { level ->
                outputLevelChannel.trySend(level)
            }

            recorder = rec
            player = play

            recordForwardJob = scope.launch(Dispatchers.Default) {
                for (chunk in rec.audioData) {
                    recordedAudioChannel.trySend(chunk)
                }
            }

            rec.start()
            play.start()
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        mutex.withLock {
            Log.d(TAG, "Stopping AudioEngine")
            stopInternal()
        }
    }

    override suspend fun playAudio(data: ShortArray) {
        player?.playChannel?.send(data)
    }

    private suspend fun stopInternal() {
        recordForwardJob?.cancel()
        recordForwardJob = null
        recorder?.stop()
        recorder = null
        player?.stop()
        player = null
        isRecordingChannel.trySend(false)
        inputLevelChannel.trySend(0.0f)
        outputLevelChannel.trySend(0.0f)
    }

    private val audioCodec = AudioCodec()

    override suspend fun encode(input: ShortArray, profile: AudioProfile): ByteArray {
        return audioCodec.encode(input, profile)
    }

    override suspend fun decode(input: ByteArray, profile: AudioProfile): ShortArray {
        if (input.isNotEmpty() && input[0] == AudioCodec.MAGIC_BYTE) {
            return audioCodec.decode(input)
        }
        val shortCount = input.size / 2
        val shorts = ShortArray(shortCount)
        val buffer = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            shorts[i] = buffer.short
        }
        return shorts
    }

    override fun setVoxEnabled(enabled: Boolean) {
        voxEnabled = enabled
        recorder?.setVoxEnabled(enabled)
    }

    override fun setVoxThreshold(thresholdDb: Float) {
        voxThresholdDb = thresholdDb
        recorder?.setVoxThreshold(thresholdDb)
    }

    override fun setOutputVolume(volume: Float) {
        outputVolume = volume.coerceIn(0.0f, 1.0f)
        player?.setVolume(outputVolume)
    }

    override fun getOutputVolume(): Float = outputVolume
}

class AudioRecorderAndroid(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: AudioEngineConfig,
    private var voxEnabled: Boolean,
    private var voxThresholdDb: Float,
    private val onLevelUpdate: (Float, Boolean) -> Unit
) : AudioRecorder {

    private val TAG = "AudioRecorderAndroid"
    private val audioDataChannel = Channel<ShortArray>(Channel.BUFFERED)
    override val audioData: ReceiveChannel<ShortArray> = audioDataChannel

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRunning = false

    @SuppressLint("MissingPermission")
    override suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val sampleRate = config.sampleRate
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val frameSamples = (sampleRate * config.frameSizeMs) / 1000
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, frameSamples * 2 * 4)

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            rec.release()
            return@withContext
        }

        audioRecord = rec
        rec.startRecording()
        isRunning = true

        recordingJob = scope.launch(Dispatchers.IO) {
            val pcmBuffer = ShortArray(frameSamples)
            while (isActive && isRunning) {
                val readSamples = rec.read(pcmBuffer, 0, frameSamples)
                if (readSamples > 0) {
                    val db = calculateRmsDb(pcmBuffer, readSamples)
                    val normLevel = ((db + 60f) / 60f).coerceIn(0f, 1f)

                    val active = if (voxEnabled) db >= voxThresholdDb else true
                    onLevelUpdate(normLevel, active)

                    if (active) {
                        val frameCopy = pcmBuffer.copyOf(readSamples)
                        audioDataChannel.trySend(frameCopy)
                    }
                }
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    fun setVoxEnabled(enabled: Boolean) {
        voxEnabled = enabled
    }

    fun setVoxThreshold(thresholdDb: Float) {
        voxThresholdDb = thresholdDb
    }

    private fun calculateRmsDb(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / size)
        if (rms <= 0.0) return -100f
        return (20 * log10(rms / 32768.0)).toFloat()
    }
}

class AudioPlayerAndroid(
    private val context: Context,
    private val scope: CoroutineScope,
    private val config: AudioEngineConfig,
    private var initialVolume: Float,
    private val onLevelUpdate: (Float) -> Unit
) : AudioPlayer {

    private val TAG = "AudioPlayerAndroid"
    private val playDataChannel = Channel<ShortArray>(Channel.BUFFERED)
    override val playChannel: SendChannel<ShortArray> = playDataChannel

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isRunning = false

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext

        val sampleRate = config.sampleRate
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufferSize, (sampleRate * config.frameSizeMs / 1000) * 2 * 4)

        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed")
            track.release()
            return@withContext
        }

        track.setVolume(initialVolume)
        track.play()
        audioTrack = track
        isRunning = true

        playbackJob = scope.launch(Dispatchers.IO) {
            for (pcmBuffer in playDataChannel) {
                if (!isRunning) break
                val written = track.write(pcmBuffer, 0, pcmBuffer.size)
                if (written > 0) {
                    val db = calculateRmsDb(pcmBuffer, written)
                    val normLevel = ((db + 60f) / 60f).coerceIn(0f, 1f)
                    onLevelUpdate(normLevel)
                }
            }
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        isRunning = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack = null
    }

    fun setVolume(volume: Float) {
        initialVolume = volume
        audioTrack?.setVolume(volume)
    }

    private fun calculateRmsDb(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val rms = sqrt(sum / size)
        if (rms <= 0.0) return -100f
        return (20 * log10(rms / 32768.0)).toFloat()
    }
}
