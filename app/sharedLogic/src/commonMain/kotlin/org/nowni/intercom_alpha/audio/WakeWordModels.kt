package org.nowni.intercom_alpha.audio

import kotlinx.serialization.Serializable

/**
 * Supported hands-free acoustic wake word keywords.
 */
@Serializable
enum class WakeWordType {
    /** Hands-free PTT activation ("Hey Alpha") */
    HEY_ALPHA,

    /** High-priority distress broadcast ("Emergency SOS") */
    EMERGENCY_SOS
}

/**
 * State machine status of the hands-free VOX arbiter.
 */
@Serializable
enum class VoxState {
    /** Inactive, listening for wake words or push-to-talk */
    IDLE,

    /** Wake word spotted and rider is actively transmitting voice */
    TRIGGERED,

    /** Rider paused speaking; holding transmit window before releasing */
    HOLD_TAIL,

    /** Transmission closed; refractory cooldown suppressing immediate re-triggers */
    COOLDOWN
}

/**
 * Event emitted upon successful acoustic keyword identification.
 *
 * @property type Keyword identified.
 * @property confidence Match confidence from 0.0 to 1.0.
 * @property timestampMs Epoch timestamp in milliseconds.
 * @property durationMs Duration of spotted acoustic utterance in ms.
 * @property energyDb Average energy in dBFS during utterance.
 * @property isEmergency Flag indicating life-safety emergency urgency.
 */
@Serializable
data class WakeWordEvent(
    val type: WakeWordType,
    val confidence: Float,
    val timestampMs: Long,
    val durationMs: Long,
    val energyDb: Float,
    val isEmergency: Boolean
)

/**
 * Configuration parameters for the acoustic wake word spotted and VOX arbiter.
 */
@Serializable
data class WakeWordConfig(
    val sampleRateHz: Int = 16000,
    val confidenceThreshold: Float = 0.65f,
    val energyThresholdDb: Float = -38.0f,
    val holdTailDurationMs: Long = 800L,
    val cooldownDurationMs: Long = 1000L,
    val minUtteranceDurationMs: Long = 300L,
    val maxUtteranceDurationMs: Long = 1400L,
    val enableEmergencySos: Boolean = true,
    val enableHeyAlpha: Boolean = true
)

/**
 * Real-time telemetry snapshot of VOX arbiter and hands-free keyword spotting.
 */
@Serializable
data class VoxStateSnapshot(
    val state: VoxState = VoxState.IDLE,
    val lastDetectedKeyword: WakeWordType? = null,
    val confidence: Float = 0.0f,
    val isPttActive: Boolean = false,
    val energyDb: Float = -100.0f
)
