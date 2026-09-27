package org.nowni.intercom_alpha.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.set
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
import platform.AVFAudio.*
import platform.Foundation.*
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
class AudioEngineImpl(
    private val scope: CoroutineScope
) : AudioEngine {

    private val mutex = Mutex()

    private val outputLevelChannel = Channel<Float>(Channel.CONFLATED)
    private val inputLevelChannel = Channel<Float>(Channel.CONFLATED)
    private val isRecordingChannel = Channel<Boolean>(Channel.CONFLATED)
    private val recordedAudioChannel = Channel<ShortArray>(Channel.BUFFERED)

    override val outputLevel: ReceiveChannel<Float> = outputLevelChannel
    override val inputLevel: ReceiveChannel<Float> = inputLevelChannel
    override val isRecording: ReceiveChannel<Boolean> = isRecordingChannel
    override val recordedAudio: ReceiveChannel<ShortArray> = recordedAudioChannel

    private var recorder: AudioRecorderIOS? = null
    private var player: AudioPlayerIOS? = null
    private var recordForwardJob: Job? = null

    private var voxEnabled: Boolean = false
    private var voxThresholdDb: Float = -40f
    private var outputVolume: Float = 1.0f

    override suspend fun start(config: AudioEngineConfig) = withContext(Dispatchers.Default) {
        mutex.withLock {
            voxEnabled = config.enableVox
            voxThresholdDb = config.voxThresholdDb

            stopInternal()

            val rec = AudioRecorderIOS(scope, config, voxEnabled, voxThresholdDb) { level, recording ->
                inputLevelChannel.trySend(level)
                isRecordingChannel.trySend(recording)
            }

            val play = AudioPlayerIOS(scope, config, outputVolume) { level ->
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

    override suspend fun encode(input: ShortArray, profile: AudioProfile): ByteArray {
        val bytes = ByteArray(input.size * 2)
        for (i in input.indices) {
            val sample = input[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    override suspend fun decode(input: ByteArray, profile: AudioProfile): ShortArray {
        val shortCount = input.size / 2
        val shorts = ShortArray(shortCount)
        for (i in 0 until shortCount) {
            val low = input[i * 2].toInt() and 0xFF
            val high = input[i * 2 + 1].toInt()
            shorts[i] = ((high shl 8) or low).toShort()
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

@OptIn(ExperimentalForeignApi::class)
class AudioRecorderIOS(
    private val scope: CoroutineScope,
    private val config: AudioEngineConfig,
    private var voxEnabled: Boolean,
    private var voxThresholdDb: Float,
    private val onLevelUpdate: (Float, Boolean) -> Unit
) : AudioRecorder {

    private val audioDataChannel = Channel<ShortArray>(Channel.BUFFERED)
    override val audioData: ReceiveChannel<ShortArray> = audioDataChannel

    private var audioEngine: AVAudioEngine? = null
    private var isRunning = false

    override suspend fun start() = withContext(Dispatchers.Default) {
        if (isRunning) return@withContext

        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                category = AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or
                        AVAudioSessionCategoryOptionAllowBluetooth or
                        AVAudioSessionCategoryOptionAllowBluetoothA2DP,
                error = null
            )
            audioSession.setMode(AVAudioSessionModeVoiceChat, error = null)
            audioSession.setActive(true, error = null)

            val engine = AVAudioEngine()
            audioEngine = engine

            val inputNode = engine.inputNode
            val bus = 0uL
            val inputFormat = inputNode.inputFormatForBus(bus)

            val frameSamples = (config.sampleRate * config.frameSizeMs) / 1000
            val bufferCapacity = max(512u, frameSamples.toUInt())

            inputNode.installTapOnBus(
                bus = bus,
                bufferSize = bufferCapacity,
                format = inputFormat
            ) { buffer, _ ->
                if (buffer != null && isRunning) {
                    processIncomingPcmBuffer(buffer)
                }
            }

            engine.prepare()
            val started = engine.startAndReturnError(null)
            if (started) {
                isRunning = true
            }
        } catch (e: Exception) {
            println("AudioRecorderIOS failed to start: ${e.message}")
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        isRunning = false
        try {
            audioEngine?.inputNode?.removeTapOnBus(0uL)
            audioEngine?.stop()
        } catch (e: Exception) {
            println("AudioRecorderIOS error stopping: ${e.message}")
        }
        audioEngine = null
    }

    fun setVoxEnabled(enabled: Boolean) {
        voxEnabled = enabled
    }

    fun setVoxThreshold(thresholdDb: Float) {
        voxThresholdDb = thresholdDb
    }

    private fun processIncomingPcmBuffer(buffer: AVAudioPCMBuffer) {
        val frameLength = buffer.frameLength.toInt()
        if (frameLength <= 0) return

        val channelData = buffer.floatChannelData ?: return
        val channel0 = channelData[0] ?: return

        val shorts = ShortArray(frameLength)
        var sumSquares = 0.0

        for (i in 0 until frameLength) {
            val floatSample = channel0[i].coerceIn(-1.0f, 1.0f)
            val shortVal = (floatSample * 32767.0f).toInt().toShort()
            shorts[i] = shortVal
            sumSquares += shortVal.toDouble() * shortVal.toDouble()
        }

        val rms = sqrt(sumSquares / frameLength)
        val db = if (rms <= 0.0) -100f else (20 * log10(rms / 32768.0)).toFloat()
        val normLevel = ((db + 60f) / 60f).coerceIn(0f, 1f)

        val active = if (voxEnabled) db >= voxThresholdDb else true
        onLevelUpdate(normLevel, active)

        if (active) {
            audioDataChannel.trySend(shorts)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
class AudioPlayerIOS(
    private val scope: CoroutineScope,
    private val config: AudioEngineConfig,
    private var initialVolume: Float,
    private val onLevelUpdate: (Float) -> Unit
) : AudioPlayer {

    private val playDataChannel = Channel<ShortArray>(Channel.BUFFERED)
    override val playChannel: SendChannel<ShortArray> = playDataChannel

    private var audioEngine: AVAudioEngine? = null
    private var playerNode: AVAudioPlayerNode? = null
    private var playbackJob: Job? = null
    private var audioFormat: AVAudioFormat? = null
    private var isRunning = false

    override suspend fun start() = withContext(Dispatchers.Default) {
        if (isRunning) return@withContext

        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                category = AVAudioSessionCategoryPlayAndRecord,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker or
                        AVAudioSessionCategoryOptionAllowBluetooth or
                        AVAudioSessionCategoryOptionAllowBluetoothA2DP,
                error = null
            )
            audioSession.setActive(true, error = null)

            val engine = AVAudioEngine()
            val player = AVAudioPlayerNode()

            val format = AVAudioFormat(
                standardFormatWithSampleRate = config.sampleRate.toDouble(),
                channels = 1u
            )

            engine.attachNode(player)
            engine.connect(player, to = engine.mainMixerNode, format = format)
            engine.mainMixerNode.outputVolume = initialVolume

            engine.prepare()
            val started = engine.startAndReturnError(null)
            if (started) {
                player.play()
                audioEngine = engine
                playerNode = player
                audioFormat = format
                isRunning = true

                playbackJob = scope.launch(Dispatchers.Default) {
                    for (shorts in playDataChannel) {
                        if (!isRunning) break
                        scheduleShortsBuffer(shorts, player, format)
                    }
                }
            }
        } catch (e: Exception) {
            println("AudioPlayerIOS failed to start: ${e.message}")
        }
    }

    override suspend fun stop() = withContext(Dispatchers.Default) {
        isRunning = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            playerNode?.stop()
            audioEngine?.stop()
        } catch (e: Exception) {
            println("AudioPlayerIOS error stopping: ${e.message}")
        }
        playerNode = null
        audioEngine = null
    }

    fun setVolume(volume: Float) {
        initialVolume = volume.coerceIn(0.0f, 1.0f)
        audioEngine?.mainMixerNode?.outputVolume = initialVolume
    }

    private fun scheduleShortsBuffer(shorts: ShortArray, player: AVAudioPlayerNode, format: AVAudioFormat) {
        val frameCount = shorts.size.toUInt()
        val buffer = AVAudioPCMBuffer(pCMFormat = format, frameCapacity = frameCount)
        buffer.frameLength = frameCount

        val channelData = buffer.floatChannelData ?: return
        val channel0 = channelData[0] ?: return

        var sumSquares = 0.0
        for (i in shorts.indices) {
            val floatVal = shorts[i] / 32768.0f
            channel0[i] = floatVal
            sumSquares += shorts[i].toDouble() * shorts[i].toDouble()
        }

        val rms = sqrt(sumSquares / shorts.size)
        val db = if (rms <= 0.0) -100f else (20 * log10(rms / 32768.0)).toFloat()
        val normLevel = ((db + 60f) / 60f).coerceIn(0f, 1f)
        onLevelUpdate(normLevel)

        player.scheduleBuffer(buffer, completionHandler = null)
    }
}
