package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.currentTimeMillis
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Traffic priority tier for voice and telemetry packets.
 */
@Serializable
enum class PriorityLevel(val rank: Int) {
    /** Standard conversational voice packets. */
    NORMAL(1),

    /** High-priority lead rider guidance or navigation audio. */
    PRIORITY(2),

    /** Preemptive emergency broadcast (crash, hazard, immediate safety alert). */
    EMERGENCY_OVERRIDE(3)
}

/**
 * Categorization of hazard and safety events for synthesized acoustic cues.
 */
@Serializable
enum class AlertType {
    NONE,
    HAZARD_WARNING,
    CRASH_DETECTED,
    EMERGENCY_STOP
}

/**
 * Wire-level prioritized audio container.
 */
@Serializable
data class PrioritizedAudioFrame(
    val priority: PriorityLevel,
    val senderId: String,
    val sequence: Long,
    val timestamp: Long,
    val data: ByteArray,
    val alertType: AlertType = AlertType.NONE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrioritizedAudioFrame) return false
        if (priority != other.priority) return false
        if (senderId != other.senderId) return false
        if (sequence != other.sequence) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false
        if (alertType != other.alertType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = priority.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + sequence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + alertType.hashCode()
        return result
    }
}

/**
 * Pure Multiplatform Acoustic Chime Synthesizer.
 *
 * Generates 16-bit 48kHz PCM alert tones mathematically without requiring external audio asset files.
 */
object AlertChimeSynthesizer {
    const val SAMPLE_RATE = 48000

    /**
     * Synthesizes a mono 16-bit little-endian PCM sine tone with smooth attack and release envelopes.
     *
     * @param freqHz Frequency of the tone in Hertz.
     * @param durationMs Length of the audio tone in milliseconds.
     * @param amplitude Maximum peak amplitude (0.0 to 1.0).
     */
    fun generateTone(
        freqHz: Double,
        durationMs: Int,
        amplitude: Double = 0.8
    ): ByteArray {
        val totalSamples = (SAMPLE_RATE * (durationMs / 1000.0)).roundToInt()
        val pcmBytes = ByteArray(totalSamples * 2)

        val rampSamples = (totalSamples * 0.08).roundToInt().coerceAtLeast(1) // 8% smooth fade in/out

        for (i in 0 until totalSamples) {
            val env = when {
                i < rampSamples -> i.toDouble() / rampSamples.toDouble()
                i > (totalSamples - rampSamples) -> (totalSamples - i).toDouble() / rampSamples.toDouble()
                else -> 1.0
            }

            val angle = 2.0 * PI * freqHz * (i.toDouble() / SAMPLE_RATE.toDouble())
            val sampleVal = (sin(angle) * amplitude * env * 32767.0).roundToInt().coerceIn(-32768, 32767)

            val byteIndex = i * 2
            pcmBytes[byteIndex] = (sampleVal and 0xFF).toByte()
            pcmBytes[byteIndex + 1] = ((sampleVal shr 8) and 0xFF).toByte()
        }

        return pcmBytes
    }

    /**
     * Synthesizes a single 880Hz (A5) hazard attention chime (150ms).
     */
    fun generateHazardChime(): ByteArray {
        return generateTone(freqHz = 880.0, durationMs = 150, amplitude = 0.85)
    }

    /**
     * Synthesizes an urgent ascending two-tone emergency chime (880Hz -> 1760Hz, 250ms total).
     */
    fun generateEmergencyChime(): ByteArray {
        val tone1 = generateTone(freqHz = 880.0, durationMs = 120, amplitude = 0.9)
        val tone2 = generateTone(freqHz = 1760.0, durationMs = 130, amplitude = 0.95)
        val combined = ByteArray(tone1.size + tone2.size)
        tone1.copyInto(combined, 0)
        tone2.copyInto(combined, tone1.size)
        return combined
    }
}

/**
 * Preemptive Audio Priority and Emergency Hazard Broadcast Manager.
 *
 * Implements deterministic audio stream preemption:
 * - When an [PriorityLevel.EMERGENCY_OVERRIDE] frame arrives, ongoing lower-priority audio streams
 *   are immediately suppressed/preempted.
 * - Manages audio hangover windows so conversational gaps in emergency broadcasts do not
 *   allow normal chatter to leak through.
 */
class PriorityBroadcastManager(
    val hangoverDurationMs: Long = 600L
) {
    private val mutex = Mutex()

    private var activeSenderId: String? = null
    private var currentPriority: PriorityLevel = PriorityLevel.NORMAL
    private var activeUntilTimestamp: Long = 0L

    private val _activePriority = MutableStateFlow(PriorityLevel.NORMAL)
    val activePriority: StateFlow<PriorityLevel> = _activePriority.asStateFlow()

    private val _isEmergencyActive = MutableStateFlow(false)
    val isEmergencyActive: StateFlow<Boolean> = _isEmergencyActive.asStateFlow()

    /**
     * Evaluates incoming [frame] against active priority session.
     *
     * @return true if frame should be routed to jitter buffer and speaker playout; false if preempted/suppressed.
     */
    suspend fun shouldPlayFrame(
        frame: PrioritizedAudioFrame,
        now: Long = currentTimeMillis()
    ): Boolean {
        mutex.withLock {
            val isSessionActive = now < activeUntilTimestamp

            if (!isSessionActive) {
                // Previous session expired; accept frame and start new session
                updateSessionLocked(frame, now)
                return true
            }

            // Session is currently active; compare priority rank
            if (frame.priority.rank > currentPriority.rank) {
                // Higher priority preempts current stream immediately
                updateSessionLocked(frame, now)
                return true
            } else if (frame.priority.rank == currentPriority.rank) {
                // Same priority: allow if same sender, or refresh session
                if (frame.senderId == activeSenderId || currentPriority == PriorityLevel.NORMAL) {
                    activeSenderId = frame.senderId
                    activeUntilTimestamp = now + hangoverDurationMs
                    return true
                }
                // If two distinct senders try to emit same priority simultaneously, prioritize earliest
                return false
            } else {
                // Lower priority is preempted and suppressed
                return false
            }
        }
    }

    /**
     * Synthesizes an emergency override broadcast packet prepended with the appropriate alert chime.
     */
    fun createEmergencyBroadcast(
        senderId: String,
        audioData: ByteArray,
        alertType: AlertType,
        sequence: Long,
        timestamp: Long = currentTimeMillis(),
        includePreRollChime: Boolean = true
    ): PrioritizedAudioFrame {
        val payload = if (includePreRollChime && alertType != AlertType.NONE) {
            val chime = when (alertType) {
                AlertType.HAZARD_WARNING -> AlertChimeSynthesizer.generateHazardChime()
                AlertType.CRASH_DETECTED, AlertType.EMERGENCY_STOP -> AlertChimeSynthesizer.generateEmergencyChime()
                AlertType.NONE -> ByteArray(0)
            }
            val combined = ByteArray(chime.size + audioData.size)
            chime.copyInto(combined, 0)
            audioData.copyInto(combined, chime.size)
            combined
        } else {
            audioData
        }

        return PrioritizedAudioFrame(
            priority = PriorityLevel.EMERGENCY_OVERRIDE,
            senderId = senderId,
            sequence = sequence,
            timestamp = timestamp,
            data = payload,
            alertType = alertType
        )
    }

    /**
     * Clears current active priority lock (e.g. on explicit transmission termination).
     */
    suspend fun reset() {
        mutex.withLock {
            activeSenderId = null
            currentPriority = PriorityLevel.NORMAL
            activeUntilTimestamp = 0L
            _activePriority.value = PriorityLevel.NORMAL
            _isEmergencyActive.value = false
        }
    }

    private fun updateSessionLocked(frame: PrioritizedAudioFrame, now: Long) {
        activeSenderId = frame.senderId
        currentPriority = frame.priority
        activeUntilTimestamp = now + hangoverDurationMs

        _activePriority.value = frame.priority
        _isEmergencyActive.value = (frame.priority == PriorityLevel.EMERGENCY_OVERRIDE)
    }
}
