package org.nowni.intercom_alpha.safety

import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Standard gravitational constant (1g) in meters per second squared.
 */
const val STANDARD_GRAVITY_MPS2 = 9.80665f

/**
 * Inertial Measurement Unit (IMU) sensor reading from accelerometer and gyroscope.
 */
@Serializable
data class ImuReading(
    val timestampMs: Long,
    val accelX: Float, // m/s²
    val accelY: Float, // m/s²
    val accelZ: Float, // m/s²
    val gyroX: Float = 0f, // rad/s
    val gyroY: Float = 0f, // rad/s
    val gyroZ: Float = 0f  // rad/s
) {
    /**
     * Total resultant acceleration magnitude in meters per second squared.
     */
    val totalAccelMps2: Float
        get() = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)

    /**
     * Total resultant acceleration expressed in standard G-force units (1g ≈ 9.80665 m/s²).
     */
    val totalGForce: Float
        get() = totalAccelMps2 / STANDARD_GRAVITY_MPS2

    /**
     * Tilt angle in degrees relative to the vertical gravity vector.
     * 0° indicates upright; 90° indicates completely horizontal (fallen on side).
     */
    val tiltAngleDegrees: Float
        get() {
            val total = totalAccelMps2
            if (total < 0.1f) return 0f // Free-fall or near-zero acceleration
            // Assuming Z axis is vertical in upright phone mount; clamp ratio to [-1.0, 1.0]
            val cosTheta = (kotlin.math.abs(accelZ) / total).coerceIn(0f, 1f)
            val rad = acos(cosTheta)
            return (rad * 180f / PI.toFloat()).coerceIn(0f, 90f)
        }
}

/**
 * Assessed severity of a detected vehicular impact or fall-over event.
 */
@Serializable
enum class CrashSeverity {
    NONE,
    POTENTIAL_FALL_OVER,
    HARD_IMPACT,
    SEVERE_COLLISION
}

/**
 * State machine status for the crash detection and emergency dispatch lifecycle.
 */
@Serializable
enum class CrashState {
    /** Normal riding/monitoring state; no anomaly detected. */
    NORMAL,

    /** High-G impact or fall-over verified; 30-second abortable countdown running. */
    COUNTDOWN_ACTIVE,

    /** Countdown reached zero; emergency SOS beacon dispatched over BLE mesh. */
    EMERGENCY_DISPATCHED,

    /** Countdown cancelled by rider tap before timer expiry (false alarm or minor drop). */
    CANCELLED_BY_RIDER
}

/**
 * Configuration thresholds for motorcycle crash and tip-over detection.
 */
@Serializable
data class CrashConfig(
    /** Peak G-force threshold triggering a potential hard impact (default: 4.5g / ~44.1 m/s²). */
    val impactGThreshold: Float = 4.5f,

    /** Severe collision G-force threshold triggering an immediate high-priority alert (default: 7.0g). */
    val severeImpactGThreshold: Float = 7.0f,

    /** Tilt angle threshold relative to vertical indicating motorcycle fall-over (default: 60°). */
    val fallOverTiltThresholdDeg: Float = 60f,

    /** Minimum duration the motorcycle must remain tilted to confirm a sustained tip-over (default: 8000ms). */
    val fallOverDurationMs: Long = 8000L,

    /** Speed drop in km/h representing emergency deceleration threshold (default: 35 km/h). */
    val decelerationDropKmh: Float = 35f,

    /** Time window within which a deceleration drop is evaluated (default: 1500ms). */
    val decelerationTimeWindowMs: Long = 1500L,

    /** Pre-alert countdown window in seconds allowing rider to abort false alarms (default: 30s). */
    val countdownDurationSeconds: Int = 30,

    /** Minimum post-impact quiet window in ms before fall-over confirmation (default: 1000ms). */
    val postImpactStillnessWindowMs: Long = 1000L
)

/**
 * Immutable snapshot of the crash detection state machine and countdown progress.
 */
@Serializable
data class CrashCountdownSnapshot(
    val state: CrashState = CrashState.NORMAL,
    val remainingSeconds: Int = 0,
    val peakGForce: Float = 1.0f,
    val severity: CrashSeverity = CrashSeverity.NONE,
    val triggerReason: String = "",
    val detectedAtMs: Long = 0L,
    val lastCoordinate: GpsCoordinate? = null
) {
    val isCountdownActive: Boolean
        get() = state == CrashState.COUNTDOWN_ACTIVE

    val isEmergencyDispatched: Boolean
        get() = state == CrashState.EMERGENCY_DISPATCHED
}

/**
 * Serialized emergency crash alert broadcasted over the BLE mesh when countdown expires.
 */
@Serializable
data class CrashAlertPacket(
    val incidentId: String,
    val senderId: String,
    val senderName: String,
    val peakGForce: Float,
    val severity: CrashSeverity,
    val triggerReason: String,
    val timestampMs: Long,
    val coordinate: GpsCoordinate?
)
