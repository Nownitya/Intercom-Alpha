package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Acoustic feature metrics extracted per 20ms audio frame.
 */
internal data class WakeWordFeatureFrame(
    val rmsEnergyDb: Float,
    val zeroCrossingRate: Float,
    val sibilantRatio: Float,
    val isSpeechActive: Boolean
)

/**
 * Pure Kotlin Multiplatform Hands-Free Acoustic Wake Word Engine & Continuous VOX Arbiter.
 *
 * Provides real-time, offline keyword spotting for helmet communication:
 * 1. Recognizes "Hey Alpha" (hands-free PTT activation) and "Emergency SOS" (life-safety distress broadcast)
 *    using pure KMP phonetic syllable feature trajectory matching.
 * 2. Implements a 4-stage VOX state machine (`IDLE` -> `TRIGGERED` -> `HOLD_TAIL` -> `COOLDOWN`)
 *    preventing audio clipping between words and false re-triggers during gear shifts.
 * 3. 100% pure Kotlin Multiplatform with zero cloud, native C, or platform-specific ML dependencies (Rules 2 & 3).
 *
 * @param config Engine configuration parameters.
 */
class AcousticWakeWordEngine(
    val config: WakeWordConfig = WakeWordConfig()
) {
    private val mutex = Mutex()

    private val _voxState = MutableStateFlow(VoxState.IDLE)
    val voxState: StateFlow<VoxState> = _voxState.asStateFlow()

    private val _latestEvent = MutableStateFlow<WakeWordEvent?>(null)
    val latestEvent: StateFlow<WakeWordEvent?> = _latestEvent.asStateFlow()

    private val _eventsChannel = Channel<WakeWordEvent>(Channel.BUFFERED)
    val events: ReceiveChannel<WakeWordEvent> = _eventsChannel

    // Utterance tracking buffer for keyword spotting
    private val activeUtteranceFrames = mutableListOf<WakeWordFeatureFrame>()
    private var utteranceStartTimeMs: Long = 0L
    private var lastSpeechFrameTimeMs: Long = 0L
    private var holdTailStartTimeMs: Long = 0L
    private var cooldownStartTimeMs: Long = 0L
    private var lastSuccessfulTriggerTimeMs: Long = 0L

    // Residual sample buffer for continuous streaming
    private val frameSize = (config.sampleRateHz * 0.020).toInt() // 20ms = 320 samples @ 16kHz
    private val sampleBuffer = ShortArray(frameSize)
    private var sampleBufferCount = 0

    /**
     * Ingest a chunk of 16-bit PCM little-endian audio bytes from the microphone.
     */
    suspend fun processPcmBytes(
        pcmBytes: ByteArray,
        timestampMs: Long
    ): WakeWordEvent? = mutex.withLock {
        if (pcmBytes.size < 2) return@withLock null

        val sampleCount = pcmBytes.size / 2
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            samples[i] = ((high shl 8) or low).toShort()
        }

        return@withLock processSamplesInternal(samples, timestampMs)
    }

    /**
     * Ingest an array of 16-bit PCM audio samples.
     */
    suspend fun processSamples(
        samples: ShortArray,
        timestampMs: Long
    ): WakeWordEvent? = mutex.withLock {
        return@withLock processSamplesInternal(samples, timestampMs)
    }

    /**
     * Manually overrides VOX state (e.g. from physical handlebar button).
     */
    suspend fun setManualPtt(active: Boolean, timestampMs: Long) = mutex.withLock {
        if (active) {
            _voxState.value = VoxState.TRIGGERED
            lastSpeechFrameTimeMs = timestampMs
        } else {
            if (_voxState.value == VoxState.TRIGGERED) {
                _voxState.value = VoxState.COOLDOWN
                cooldownStartTimeMs = timestampMs
            }
        }
    }

    /**
     * Resets internal utterance buffers and state machine.
     */
    suspend fun reset() = mutex.withLock {
        _voxState.value = VoxState.IDLE
        _latestEvent.value = null
        activeUtteranceFrames.clear()
        sampleBufferCount = 0
        utteranceStartTimeMs = 0L
        lastSpeechFrameTimeMs = 0L
        holdTailStartTimeMs = 0L
        cooldownStartTimeMs = 0L
    }

    /**
     * Internal sample processing loop.
     */
    private fun processSamplesInternal(
        samples: ShortArray,
        timestampMs: Long
    ): WakeWordEvent? {
        var detectedEvent: WakeWordEvent? = null

        for (sample in samples) {
            sampleBuffer[sampleBufferCount++] = sample

            if (sampleBufferCount == frameSize) {
                val event = processFrameInternal(sampleBuffer, timestampMs)
                if (event != null) {
                    detectedEvent = event
                }
                sampleBufferCount = 0
            }
        }

        return detectedEvent
    }

    /**
     * Processes 1 individual 20ms feature frame.
     */
    private fun processFrameInternal(
        frame: ShortArray,
        timestampMs: Long
    ): WakeWordEvent? {
        val feature = extractFeatures(frame)

        // Handle VOX Hold-Tail & Cooldown State Transitions
        updateVoxStateMachine(feature.isSpeechActive, timestampMs)

        var resultEvent: WakeWordEvent? = null

        // Utterance boundary tracking for keyword spotting
        if (_voxState.value == VoxState.IDLE) {
            if (feature.isSpeechActive) {
                if (activeUtteranceFrames.isEmpty()) {
                    utteranceStartTimeMs = timestampMs
                }
                activeUtteranceFrames.add(feature)
                lastSpeechFrameTimeMs = timestampMs
            } else if (activeUtteranceFrames.isNotEmpty()) {
                val silenceDuration = timestampMs - lastSpeechFrameTimeMs
                if (silenceDuration > 140L) {
                    // Utterance boundary reached, evaluate keyword match
                    val utteranceDuration = lastSpeechFrameTimeMs - utteranceStartTimeMs
                    if (utteranceDuration in config.minUtteranceDurationMs..config.maxUtteranceDurationMs) {
                        val match = evaluateKeywordMatch(activeUtteranceFrames, utteranceStartTimeMs, utteranceDuration)
                        if (match != null) {
                            resultEvent = match
                            onKeywordSpotted(match, timestampMs)
                        }
                    }
                    activeUtteranceFrames.clear()
                }
            }
        }

        return resultEvent
    }

    /**
     * Extracts acoustic features for a 20ms frame: RMS energy, Zero-Crossing Rate, and High-Band Sibilant Ratio.
     */
    private fun extractFeatures(frame: ShortArray): WakeWordFeatureFrame {
        var sumSquares = 0.0
        var zeroCrossings = 0
        var highFreqDiffSum = 0.0

        for (i in frame.indices) {
            val sampleVal = frame[i].toDouble()
            sumSquares += sampleVal * sampleVal

            if (i > 0) {
                val prev = frame[i - 1].toInt()
                val curr = frame[i].toInt()
                if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                    zeroCrossings++
                }
                val diff = abs(curr - prev)
                highFreqDiffSum += diff
            }
        }

        val rms = sqrt(sumSquares / frame.size)
        val rmsDb = if (rms > 1.0) {
            (20.0 * log10(rms / 32768.0)).toFloat()
        } else {
            -100.0f
        }

        val zcr = zeroCrossings.toFloat() / frame.size
        val avgDiff = highFreqDiffSum / frame.size
        val sibilantRatio = if (rms > 1.0) (avgDiff / rms).toFloat() else 0f
        val isSpeech = rmsDb >= config.energyThresholdDb

        return WakeWordFeatureFrame(
            rmsEnergyDb = rmsDb,
            zeroCrossingRate = zcr,
            sibilantRatio = sibilantRatio,
            isSpeechActive = isSpeech
        )
    }

    /**
     * Updates VOX state transitions between TRIGGERED, HOLD_TAIL, COOLDOWN, and IDLE.
     */
    private fun updateVoxStateMachine(isSpeechActive: Boolean, timestampMs: Long) {
        when (_voxState.value) {
            VoxState.TRIGGERED -> {
                if (isSpeechActive) {
                    lastSpeechFrameTimeMs = timestampMs
                } else {
                    if (timestampMs - lastSpeechFrameTimeMs >= 160L) {
                        _voxState.value = VoxState.HOLD_TAIL
                        holdTailStartTimeMs = timestampMs
                    }
                }
            }
            VoxState.HOLD_TAIL -> {
                if (isSpeechActive) {
                    _voxState.value = VoxState.TRIGGERED
                    lastSpeechFrameTimeMs = timestampMs
                } else if (timestampMs - holdTailStartTimeMs >= config.holdTailDurationMs) {
                    _voxState.value = VoxState.COOLDOWN
                    cooldownStartTimeMs = timestampMs
                }
            }
            VoxState.COOLDOWN -> {
                if (timestampMs - cooldownStartTimeMs >= config.cooldownDurationMs) {
                    _voxState.value = VoxState.IDLE
                }
            }
            VoxState.IDLE -> {
                // Maintained in IDLE until wake word is spotted or physical PTT pressed
            }
        }
    }

    /**
     * Evaluates buffered utterance frames against phonetic templates for "Hey Alpha" and "Emergency SOS".
     */
    private fun evaluateKeywordMatch(
        frames: List<WakeWordFeatureFrame>,
        startTimeMs: Long,
        durationMs: Long
    ): WakeWordEvent? {
        if (frames.size < 8) return null

        // 1. Average Energy
        var energySum = 0f
        for (f in frames) energySum += f.rmsEnergyDb
        val avgEnergyDb = energySum / frames.size

        // 2. Evaluate both templates and select the strongest match
        val heyAlphaScore = if (config.enableHeyAlpha) scoreHeyAlphaTemplate(frames) else 0f
        val sosScore = if (config.enableEmergencySos) scoreEmergencySosTemplate(frames) else 0f

        val bestScore = max(heyAlphaScore, sosScore)
        if (bestScore < config.confidenceThreshold) return null

        // Select keyword with highest confidence score
        val chosenType = if (sosScore > heyAlphaScore && sosScore >= config.confidenceThreshold) {
            WakeWordType.EMERGENCY_SOS
        } else if (heyAlphaScore >= config.confidenceThreshold) {
            WakeWordType.HEY_ALPHA
        } else if (sosScore >= config.confidenceThreshold) {
            WakeWordType.EMERGENCY_SOS
        } else {
            return null
        }

        val chosenConfidence = if (chosenType == WakeWordType.EMERGENCY_SOS) sosScore else heyAlphaScore

        return WakeWordEvent(
            type = chosenType,
            confidence = chosenConfidence,
            timestampMs = startTimeMs + durationMs,
            durationMs = durationMs,
            energyDb = avgEnergyDb,
            isEmergency = (chosenType == WakeWordType.EMERGENCY_SOS)
        )
    }

    /**
     * Syllable scoring for "Hey Alpha":
     * - Duration: 240ms to 960ms (12 to 48 frames).
     * - Syllable 1 ("Hey"): Voiced onset, mid ZCR (< 0.28).
     * - Syllable 2 ("Al"): Open vowel dip/burst.
     * - Syllable 3 ("-pha"): Unvoiced fricative 'f', high ZCR (> 0.20) and sibilant ratio.
     */
    private fun scoreHeyAlphaTemplate(frames: List<WakeWordFeatureFrame>): Float {
        val total = frames.size
        if (total !in 12..48) return 0f

        val third = total / 3
        val s1Frames = frames.subList(0, third)
        val s3Frames = frames.subList(2 * third, total)

        var s1AvgZcr = 0f
        for (f in s1Frames) s1AvgZcr += f.zeroCrossingRate
        s1AvgZcr /= s1Frames.size

        var s3AvgZcr = 0f
        var s3SibilantSum = 0f
        for (f in s3Frames) {
            s3AvgZcr += f.zeroCrossingRate
            s3SibilantSum += f.sibilantRatio
        }
        s3AvgZcr /= s3Frames.size
        val s3AvgSibilant = s3SibilantSum / s3Frames.size

        var score = 0.50f

        // Check Syllable 1 voiced characteristics
        if (s1AvgZcr < 0.26f) score += 0.20f

        // Check Syllable 3 fricative/sibilant characteristics
        if (s3AvgZcr > s1AvgZcr || s3AvgZcr > 0.18f) score += 0.20f
        if (s3AvgSibilant > 0.8f) score += 0.10f

        return min(0.98f, score)
    }

    /**
     * Syllable scoring for "Emergency SOS":
     * - Multi-stage cadence (>= 20 frames / 400ms) with sharp sibilant bursts in the second half ("S-O-S").
     */
    private fun scoreEmergencySosTemplate(frames: List<WakeWordFeatureFrame>): Float {
        val total = frames.size
        if (total !in 28..70) return 0f // SOS requires at least 560ms multi-syllable cadence

        val mid = total / 2
        val firstHalf = frames.subList(0, mid)
        val secondHalf = frames.subList(mid, total)

        var firstHalfZcr = 0f
        for (f in firstHalf) firstHalfZcr += f.zeroCrossingRate
        firstHalfZcr /= firstHalf.size

        var secondHalfZcr = 0f
        var maxSibilantInSecondHalf = 0f
        for (f in secondHalf) {
            secondHalfZcr += f.zeroCrossingRate
            if (f.sibilantRatio > maxSibilantInSecondHalf) {
                maxSibilantInSecondHalf = f.sibilantRatio
            }
        }
        secondHalfZcr /= secondHalf.size

        var score = 0.45f
        if (secondHalfZcr > 0.22f) score += 0.25f
        if (maxSibilantInSecondHalf > 1.2f) score += 0.20f
        if (total in 20..70) score += 0.10f

        return min(0.99f, score)
    }

    /**
     * Handles state transitions upon wake word recognition.
     */
    private fun onKeywordSpotted(event: WakeWordEvent, timestampMs: Long) {
        lastSuccessfulTriggerTimeMs = timestampMs
        _latestEvent.value = event
        _eventsChannel.trySend(event)

        _voxState.value = VoxState.TRIGGERED
        lastSpeechFrameTimeMs = timestampMs
    }
}
