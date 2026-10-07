package org.nowni.intercom_alpha.power

import kotlinx.serialization.Serializable

/**
 * Operating radio power policy enforced by the battery governor.
 */
@Serializable
enum class PowerPolicy {
    /** 100% duty cycle, high scan rate, full audio bandwidth for normal riding conditions. */
    NORMAL,

    /** 50% duty cycle, throttled scanning when battery drops below 30%. */
    CONSERVATIVE,

    /** 20% duty cycle, minimum keepalive when battery is critically low (<15%). */
    EXTREME_SURVIVAL,

    /** Ultra low-power 5% duty cycle during sustained rest stops (>10 min stillness). */
    REST_STOP_SLEEP,

    /** Unconditional 100% duty cycle preemption overriding all power-saving during emergencies. */
    EMERGENCY_OVERRIDE
}

/**
 * BLE radio timing parameters and duty cycle allocation.
 */
@Serializable
data class RadioDutyCycleProfile(
    val scanWindowMs: Long,
    val scanIntervalMs: Long,
    val advertisingIntervalMs: Long,
    val gpsBeaconIntervalMs: Long,
    val dutyCyclePercent: Double,
    val maxAudioProfile: String
)

/**
 * Configuration thresholds for autonomous power governing.
 */
@Serializable
data class GovernorConfig(
    val conservativeBatteryThresholdPercent: Int = 30,
    val survivalBatteryThresholdPercent: Int = 15,
    val restStopStillnessDurationMs: Long = 600_000L, // 10 minutes
    val restStopSpeedThresholdKmh: Double = 3.0,
    val minMotionWakeSpeedKmh: Double = 5.0,
    val dutyCycleNormal: RadioDutyCycleProfile = RadioDutyCycleProfile(
        scanWindowMs = 100L,
        scanIntervalMs = 100L,
        advertisingIntervalMs = 100L,
        gpsBeaconIntervalMs = 4_000L,
        dutyCyclePercent = 100.0,
        maxAudioProfile = "HIGH"
    ),
    val dutyCycleConservative: RadioDutyCycleProfile = RadioDutyCycleProfile(
        scanWindowMs = 100L,
        scanIntervalMs = 200L,
        advertisingIntervalMs = 250L,
        gpsBeaconIntervalMs = 12_000L,
        dutyCyclePercent = 50.0,
        maxAudioProfile = "MEDIUM"
    ),
    val dutyCycleSurvival: RadioDutyCycleProfile = RadioDutyCycleProfile(
        scanWindowMs = 100L,
        scanIntervalMs = 500L,
        advertisingIntervalMs = 500L,
        gpsBeaconIntervalMs = 30_000L,
        dutyCyclePercent = 20.0,
        maxAudioProfile = "LOW"
    ),
    val dutyCycleSleep: RadioDutyCycleProfile = RadioDutyCycleProfile(
        scanWindowMs = 100L,
        scanIntervalMs = 2_000L,
        advertisingIntervalMs = 2_000L,
        gpsBeaconIntervalMs = 60_000L,
        dutyCyclePercent = 5.0,
        maxAudioProfile = "ULTRA_LOW"
    ),
    val dutyCycleEmergency: RadioDutyCycleProfile = RadioDutyCycleProfile(
        scanWindowMs = 100L,
        scanIntervalMs = 100L,
        advertisingIntervalMs = 100L,
        gpsBeaconIntervalMs = 2_000L,
        dutyCyclePercent = 100.0,
        maxAudioProfile = "HIGH"
    )
)

/**
 * Live snapshot of battery governor state for telemetry and UI HUD.
 */
@Serializable
data class PowerGovernorState(
    val policy: PowerPolicy = PowerPolicy.NORMAL,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isStill: Boolean = false,
    val currentSpeedKmh: Double = 0.0,
    val stillDurationMs: Long = 0L,
    val isEmergencyActive: Boolean = false,
    val dutyCycle: RadioDutyCycleProfile = GovernorConfig().dutyCycleNormal,
    val wakeReason: String? = null,
    val lastTransitionTimestampMs: Long = 0L
)

/**
 * Compact power status packet for multi-node telemetry over mesh control channels.
 */
@Serializable
data class PowerStatusPacket(
    val nodeId: String,
    val batteryPercent: Int,
    val policy: PowerPolicy,
    val isCharging: Boolean,
    val timestampMs: Long
)
