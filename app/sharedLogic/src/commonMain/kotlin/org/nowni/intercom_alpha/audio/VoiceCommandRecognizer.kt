package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure Kotlin Multiplatform offline voice command & keyword spotting DSP engine.
 *
 * Processes continuous 48kHz 16-bit PCM voice streams to recognize tactical hands-free
 * intercom keywords ("Intercom SOS", "Next Channel", "Prev Channel", "Toggle Mute",
 * "Battery Status", "Cancel Alert") using energy envelope extraction, zero-crossing rate (ZCR),
 * and syllable phonetic template alignment.
 *
 * Rules: Zero cloud dependencies, zero external ML runtimes, zero Java/C dependencies in commonMain (Rules 2 & 3).
 */
class VoiceCommandRecognizer(
    val config: VoiceCommandConfig = VoiceCommandConfig()
) {
    private val mutex = Mutex()

    private val _latestDetection = MutableStateFlow<CommandDetection?>(null)
    val latestDetection: StateFlow<CommandDetection?> = _latestDetection.asStateFlow()

    private val _detectionsChannel = Channel<CommandDetection>(Channel.BUFFERED)
    val detections: ReceiveChannel<CommandDetection> = _detectionsChannel

    // Utterance tracking buffer
    private val activeUtteranceFrames = mutableListOf<AudioFeatureFrame>()
    private var utteranceStartTimeMs: Long = 0L
    private var lastSpokenTimeMs: Long = 0L
    private var lastSuccessfulDetectionMs: Long = 0L

    /**
     * Ingest a chunk of 16-bit PCM little-endian audio bytes from the microphone.
     */
    suspend fun processPcmBytes(
        pcmBytes: ByteArray,
        timestampMs: Long
    ): CommandDetection? = mutex.withLock {
        if (pcmBytes.size < 2) return@withLock null

        val sampleCount = pcmBytes.size / 2
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val byteLow = pcmBytes[i * 2].toInt() and 0xFF
            val byteHigh = pcmBytes[i * 2 + 1].toInt()
            samples[i] = ((byteHigh shl 8) or byteLow).toShort()
        }

        return@withLock processPcmSamplesInternal(samples, timestampMs)
    }

    /**
     * Ingest an array of 16-bit PCM audio samples directly.
     */
    suspend fun processPcmSamples(
        samples: ShortArray,
        timestampMs: Long
    ): CommandDetection? = mutex.withLock {
        return@withLock processPcmSamplesInternal(samples, timestampMs)
    }

    /**
     * Programmatically inject a command detection (useful for synthetic benchmarks and tests).
     */
    suspend fun simulateCommand(
        command: VoiceCommand,
        confidence: Float = 0.95f,
        timestampMs: Long = 0L
    ): CommandDetection = mutex.withLock {
        val detection = CommandDetection(
            command = command,
            confidence = confidence.coerceIn(0f, 1f),
            timestampMs = timestampMs,
            phraseSpoken = command.phrase,
            durationMs = 600L
        )
        _latestDetection.value = detection
        _detectionsChannel.trySend(detection)
        lastSuccessfulDetectionMs = timestampMs
        return@withLock detection
    }

    /**
     * Resets the recognizer internal feature buffers and state.
     */
    suspend fun reset() = mutex.withLock {
        activeUtteranceFrames.clear()
        utteranceStartTimeMs = 0L
        lastSpokenTimeMs = 0L
        _latestDetection.value = null
    }

    // --- Internal DSP Processing ---

    private fun processPcmSamplesInternal(
        samples: ShortArray,
        timestampMs: Long
    ): CommandDetection? {
        val feature = extractFeatureFrame(samples, timestampMs)

        // Check if audio frame has speech energy
        val isSpeechActive = feature.rmsDb >= config.energyThresholdDb

        // Wind noise rejection: aerodynamic helmet rumble is low-frequency (<200 Hz, ZCR < 0.015 at 48kHz)
        val isWindRumble = config.windNoiseSuppression &&
                feature.lowBandEnergyRatio > 0.75f &&
                feature.zeroCrossingRate < 0.015f

        if (isSpeechActive && !isWindRumble) {
            if (activeUtteranceFrames.isEmpty()) {
                utteranceStartTimeMs = timestampMs
            }
            activeUtteranceFrames.add(feature)
            lastSpokenTimeMs = timestampMs

            val currentDuration = timestampMs - utteranceStartTimeMs
            if (currentDuration > config.maxUtteranceDurationMs) {
                // Utterance too long (e.g. continuous chatter), evaluate and flush
                val detection = evaluateUtterance(activeUtteranceFrames, utteranceStartTimeMs, timestampMs)
                activeUtteranceFrames.clear()
                return detection
            }
        } else {
            // Silence, sub-threshold frame, or inter-syllable pause
            if (activeUtteranceFrames.isNotEmpty()) {
                activeUtteranceFrames.add(feature)
                val silenceGap = timestampMs - lastSpokenTimeMs
                if (silenceGap >= 160L) {
                    // Utterance boundary reached (end of phrase)
                    val duration = lastSpokenTimeMs - utteranceStartTimeMs
                    if (duration >= config.minUtteranceDurationMs) {
                        // Retain frames up to the last spoken sample (prune trailing silence)
                        val validFrames = activeUtteranceFrames.filter { it.timestampMs <= lastSpokenTimeMs }
                        val detection = evaluateUtterance(validFrames, utteranceStartTimeMs, lastSpokenTimeMs)
                        activeUtteranceFrames.clear()
                        return detection
                    } else {
                        // Utterance too short to be a valid keyword
                        activeUtteranceFrames.clear()
                    }
                }
            }
        }

        return null
    }

    private fun extractFeatureFrame(samples: ShortArray, timestampMs: Long): AudioFeatureFrame {
        if (samples.isEmpty()) {
            return AudioFeatureFrame(timestampMs, -96f, 0f, 0f, 0f, 0f)
        }

        var sumSquare = 0.0
        var zeroCrossings = 0
        var prevSign = if (samples[0] >= 0) 1 else -1

        // 1-pole recursive filter for 3-band spectral energy decomposition:
        // low cutoff ~ 300 Hz (alphaLow ~ 0.04 at 48kHz)
        // mid cutoff ~ 2500 Hz (alphaMid ~ 0.30 at 48kHz)
        var lowState = 0.0
        var midState = 0.0
        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0

        for (i in samples.indices) {
            val sample = samples[i].toDouble()
            sumSquare += sample * sample

            val currentSign = if (samples[i] >= 0) 1 else -1
            if (currentSign != prevSign) {
                zeroCrossings++
                prevSign = currentSign
            }

            lowState += 0.04 * (sample - lowState)
            midState += 0.30 * (sample - midState)

            val low = lowState
            val mid = midState - lowState
            val high = sample - midState

            lowEnergy += low * low
            midEnergy += mid * mid
            highEnergy += high * high
        }

        val n = samples.size
        val rms = sqrt(sumSquare / n)
        val rmsDb = if (rms > 0.0) {
            (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(-96f, 0f)
        } else {
            -96f
        }

        val zcr = (zeroCrossings.toFloat() / (n - 1)).coerceIn(0f, 1f)
        val totalBandEnergy = (lowEnergy + midEnergy + highEnergy).coerceAtLeast(1.0)

        val lowRatio = (lowEnergy / totalBandEnergy).toFloat()
        val midRatio = (midEnergy / totalBandEnergy).toFloat()
        val highRatio = (highEnergy / totalBandEnergy).toFloat()

        return AudioFeatureFrame(
            timestampMs = timestampMs,
            rmsDb = rmsDb,
            zeroCrossingRate = zcr,
            lowBandEnergyRatio = lowRatio,
            midBandEnergyRatio = midRatio,
            highBandEnergyRatio = highRatio
        )
    }

    private fun evaluateUtterance(
        frames: List<AudioFeatureFrame>,
        startMs: Long,
        endMs: Long
    ): CommandDetection? {
        val durationMs = endMs - startMs
        if (durationMs < config.minUtteranceDurationMs || frames.size < 5) return null

        // Check cooldown from previous command
        if (endMs - lastSuccessfulDetectionMs < config.cooldownMs) return null

        // 1. Calculate summary acoustic statistics across voiced speech frames
        val voicedFrames = frames.filter { it.rmsDb >= config.energyThresholdDb }
        val effectiveFrames = if (voicedFrames.isNotEmpty()) voicedFrames else frames

        val avgZcr = effectiveFrames.map { it.zeroCrossingRate }.average().toFloat()
        val maxZcr = frames.maxOfOrNull { it.zeroCrossingRate } ?: 0f
        val avgMidRatio = effectiveFrames.map { it.midBandEnergyRatio }.average().toFloat()
        val avgHighRatio = effectiveFrames.map { it.highBandEnergyRatio }.average().toFloat()

        // Count energy envelope peaks (syllable detection)
        val syllableCount = countSyllablePeaks(frames)

        // Segment utterance into thirds (onset, nucleus, coda)
        val thirdSize = (frames.size / 3).coerceAtLeast(1)
        val onsetFrames = frames.take(thirdSize).filter { it.rmsDb >= config.energyThresholdDb }.ifEmpty { frames.take(thirdSize) }
        val nucleusFrames = frames.drop(thirdSize).take(thirdSize).filter { it.rmsDb >= config.energyThresholdDb }.ifEmpty { frames.drop(thirdSize).take(thirdSize) }
        val codaFrames = frames.takeLast(thirdSize).filter { it.rmsDb >= config.energyThresholdDb }.ifEmpty { frames.takeLast(thirdSize) }

        val onsetZcr = onsetFrames.map { it.zeroCrossingRate }.average().toFloat()
        val nucleusMid = nucleusFrames.map { it.midBandEnergyRatio }.average().toFloat()
        val codaZcr = codaFrames.map { it.zeroCrossingRate }.average().toFloat()

        var bestMatch: VoiceCommand? = null
        var bestConfidence = 0.0f

        // --- Template Matching Rules ---

        // 1. SOS_TRIGGER ("Intercom SOS")
        // Distinctive acoustic signature: 2-4 syllables, sharp high-ZCR fricatives at end ('S-O-S'), strong nucleus mid-formants
        if (syllableCount in 2..4 && codaZcr >= 0.18f && maxZcr >= 0.25f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 3,
                durationMs = durationMs, targetDurationMs = 800L,
                formantRatio = nucleusMid, targetFormant = 0.40f,
                fricativeRatio = codaZcr, targetFricative = 0.25f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.SOS_TRIGGER
            }
        }

        // 2. CANCEL_ALERT ("Cancel Alert")
        // Distinctive acoustic signature: 3-5 syllables, medium duration (600-1600ms), non-fricative coda
        if (syllableCount in 3..5 && durationMs in 600L..1600L && codaZcr < 0.20f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 4,
                durationMs = durationMs, targetDurationMs = 1000L,
                formantRatio = avgMidRatio, targetFormant = 0.35f,
                fricativeRatio = avgHighRatio, targetFricative = 0.20f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.CANCEL_ALERT
            }
        }

        // 3. NEXT_CHANNEL ("Next Channel")
        // Distinctive acoustic signature: 2-3 syllables, sharp stop/fricative in onset/nucleus ('ne-xt'), low ZCR coda
        if (syllableCount in 2..3 && maxZcr >= 0.20f && codaZcr < 0.20f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 3,
                durationMs = durationMs, targetDurationMs = 750L,
                formantRatio = nucleusMid, targetFormant = 0.35f,
                fricativeRatio = onsetZcr, targetFricative = 0.20f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.NEXT_CHANNEL
            }
        }

        // 4. PREV_CHANNEL ("Prev Channel")
        // Distinctive acoustic signature: 2-3 syllables, lower onset and coda ZCR, strong mid-band energy
        if (syllableCount in 2..3 && onsetZcr < 0.20f && codaZcr < 0.18f && maxZcr < 0.22f && avgMidRatio >= 0.30f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 3,
                durationMs = durationMs, targetDurationMs = 700L,
                formantRatio = avgMidRatio, targetFormant = 0.38f,
                fricativeRatio = avgHighRatio, targetFricative = 0.15f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.PREV_CHANNEL
            }
        }

        // 5. BATTERY_STATUS ("Battery Status")
        // Distinctive acoustic signature: 4-6 syllables, longer duration (850-1600ms)
        if (syllableCount in 4..6 && durationMs >= 850L) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 4,
                durationMs = durationMs, targetDurationMs = 1100L,
                formantRatio = avgMidRatio, targetFormant = 0.40f,
                fricativeRatio = codaZcr, targetFricative = 0.18f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.BATTERY_STATUS
            }
        }

        // 6. MUTE_TOGGLE ("Toggle Mute")
        // Distinctive acoustic signature: 2-3 syllables, compact duration (400-850ms), low ZCR throughout
        if (syllableCount in 2..3 && durationMs in 400L..850L && maxZcr < 0.20f && codaZcr < 0.18f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 3,
                durationMs = durationMs, targetDurationMs = 600L,
                formantRatio = avgMidRatio, targetFormant = 0.32f,
                fricativeRatio = avgHighRatio, targetFricative = 0.15f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.MUTE_TOGGLE
            }
        }

        // 7. STATUS_REPORT ("Status Report")
        // Distinctive acoustic signature: 4 syllables ("Sta-tus Re-port"), starts with high ZCR ('s-')
        if (syllableCount in 4..5 && durationMs >= 850L && onsetZcr >= 0.18f) {
            val score = calculateMatchScore(
                syllableCount = syllableCount, targetSyllables = 4,
                durationMs = durationMs, targetDurationMs = 1050L,
                formantRatio = nucleusMid, targetFormant = 0.35f,
                fricativeRatio = onsetZcr, targetFricative = 0.20f
            )
            if (score > bestConfidence) {
                bestConfidence = score
                bestMatch = VoiceCommand.STATUS_REPORT
            }
        }

        if (bestMatch != null && bestConfidence >= config.minConfidence) {
            val detection = CommandDetection(
                command = bestMatch,
                confidence = bestConfidence.coerceIn(0f, 1f),
                timestampMs = endMs,
                phraseSpoken = bestMatch.phrase,
                durationMs = durationMs
            )
            lastSuccessfulDetectionMs = endMs
            _latestDetection.value = detection
            _detectionsChannel.trySend(detection)
            return detection
        }

        return null
    }

    private fun countSyllablePeaks(frames: List<AudioFeatureFrame>): Int {
        if (frames.size < 3) return 1

        var peaks = 0
        var aboveThreshold = false

        for (frame in frames) {
            val isSpeech = frame.rmsDb >= (config.energyThresholdDb + 3.0f)
            if (isSpeech && !aboveThreshold) {
                peaks++
                aboveThreshold = true
            } else if (!isSpeech && frame.rmsDb < config.energyThresholdDb) {
                aboveThreshold = false
            }
        }

        // Check if there are intra-utterance energy envelope peaks in continuous speech
        var intraPeaks = 0
        var rising = false
        for (i in 1 until frames.size) {
            val prev = frames[i - 1].rmsDb
            val curr = frames[i].rmsDb
            if (curr > prev + 2.5f) {
                rising = true
            } else if (rising && curr < prev - 2.0f) {
                intraPeaks++
                rising = false
            }
        }

        return maxOf(peaks, intraPeaks).coerceAtLeast(1)
    }

    private fun calculateMatchScore(
        syllableCount: Int,
        targetSyllables: Int,
        durationMs: Long,
        targetDurationMs: Long,
        formantRatio: Float,
        targetFormant: Float,
        fricativeRatio: Float,
        targetFricative: Float
    ): Float {
        // Syllable match component (30%)
        val syllableDiff = kotlin.math.abs(syllableCount - targetSyllables)
        val syllableScore = (1.0f - (syllableDiff * 0.25f)).coerceIn(0.2f, 1.0f)

        // Duration match component (25%)
        val durationRatio = min(durationMs.toFloat(), targetDurationMs.toFloat()) /
                max(durationMs.toFloat(), targetDurationMs.toFloat())
        val durationScore = durationRatio.coerceIn(0.3f, 1.0f)

        // Formant match component (25%)
        val formantDiff = kotlin.math.abs(formantRatio - targetFormant)
        val formantScore = (1.0f - (formantDiff * 2.0f)).coerceIn(0.2f, 1.0f)

        // Fricative match component (20%) - reward exceeding target fricative for sibilant words
        val fricativeDiff = if (targetFricative >= 0.20f && fricativeRatio >= targetFricative) {
            0.0f
        } else {
            kotlin.math.abs(fricativeRatio - targetFricative)
        }
        val fricativeScore = (1.0f - (fricativeDiff * 2.5f)).coerceIn(0.2f, 1.0f)

        return (syllableScore * 0.30f +
                durationScore * 0.25f +
                formantScore * 0.25f +
                fricativeScore * 0.20f)
    }
}
