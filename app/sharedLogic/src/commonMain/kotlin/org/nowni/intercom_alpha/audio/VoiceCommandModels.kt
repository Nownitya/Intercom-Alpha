package org.nowni.intercom_alpha.audio

import kotlinx.serialization.Serializable

/**
 * Standardized tactical hands-free offline voice commands.
 */
@Serializable
enum class VoiceCommand(val phrase: String, val description: String) {
    /** Emergency distress override broadcast trigger. */
    SOS_TRIGGER("Intercom SOS", "Triggers immediate emergency SOS broadcast"),

    /** Switch active intercom sub-channel forward. */
    NEXT_CHANNEL("Next Channel", "Switches forward to next sub-channel"),

    /** Switch active intercom sub-channel backward. */
    PREV_CHANNEL("Prev Channel", "Switches backward to previous sub-channel"),

    /** Toggle microphone mute state. */
    MUTE_TOGGLE("Toggle Mute", "Toggles microphone mute/unmute state"),

    /** Check battery percentage and power governor status. */
    BATTERY_STATUS("Battery Status", "Queries battery percentage and duty-cycle state"),

    /** Query general convoy status and connected peer count. */
    STATUS_REPORT("Status Report", "Announces convoy connection and leader status"),

    /** Cancel an active crash pre-alert countdown or emergency preemption. */
    CANCEL_ALERT("Cancel Alert", "Aborts active crash countdown or alarm")
}

/**
 * Result of a recognized offline voice command keyword spotting match.
 */
@Serializable
data class CommandDetection(
    val command: VoiceCommand,
    val confidence: Float,
    val timestampMs: Long,
    val phraseSpoken: String = command.phrase,
    val durationMs: Long = 0L
)

/**
 * Acoustic configuration parameters for the offline keyword spotter DSP engine.
 */
@Serializable
data class VoiceCommandConfig(
    val sampleRate: Int = 48000,
    val frameSizeMs: Int = 20, // 960 samples per frame at 48kHz
    val minConfidence: Float = 0.70f,
    val cooldownMs: Long = 1500L,
    val energyThresholdDb: Float = -38.0f,
    val minUtteranceDurationMs: Long = 300L,
    val maxUtteranceDurationMs: Long = 2500L,
    val windNoiseSuppression: Boolean = true
)

/**
 * Acoustic feature frame extracted from 20ms of audio samples.
 */
data class AudioFeatureFrame(
    val timestampMs: Long,
    val rmsDb: Float,
    val zeroCrossingRate: Float,
    val lowBandEnergyRatio: Float,  // < 800 Hz (vowels, fundamentals)
    val midBandEnergyRatio: Float,  // 800 Hz .. 2500 Hz (speech formants)
    val highBandEnergyRatio: Float  // > 2500 Hz (fricatives: s, sh, t)
)
