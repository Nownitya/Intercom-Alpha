package org.nowni.intercom_alpha.power

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Autonomous battery duty-cycle governor and power mode manager.
 *
 * Dynamically scales BLE radio duty cycles based on battery percentages, detects
 * prolonged rest-stop stillness (>10 min) to enter low-power sleep, and unconditionally
 * restores full-bandwidth radio preemption during emergency SOS broadcasts.
 */
class BatteryGovernor(
    val nodeId: String,
    val config: GovernorConfig = GovernorConfig()
) {
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private var batteryPercent: Int = 100
    private var isCharging: Boolean = false
    private var isStill: Boolean = false
    private var currentSpeedKmh: Double = 0.0
    private var stillnessStartTimestampMs: Long? = null
    private var isEmergencyActive: Boolean = false
    private var manualOverridePolicy: PowerPolicy? = null
    private var currentPolicy: PowerPolicy = PowerPolicy.NORMAL
    private var lastWakeReason: String? = null
    private var lastTransitionTimestampMs: Long = currentTimeMillis()

    private val _governorState = MutableStateFlow(
        PowerGovernorState(
            policy = PowerPolicy.NORMAL,
            batteryPercent = 100,
            isCharging = false,
            isStill = false,
            currentSpeedKmh = 0.0,
            stillDurationMs = 0L,
            isEmergencyActive = false,
            dutyCycle = config.dutyCycleNormal,
            wakeReason = null,
            lastTransitionTimestampMs = lastTransitionTimestampMs
        )
    )
    val governorState: StateFlow<PowerGovernorState> = _governorState.asStateFlow()

    /**
     * Updates local node battery metrics.
     */
    suspend fun updateBattery(
        percent: Int,
        charging: Boolean = false,
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        batteryPercent = percent.coerceIn(0, 100)
        isCharging = charging
        recomputeState(timestampMs)
    }

    /**
     * Updates node movement and stillness metrics.
     */
    suspend fun updateMotion(
        speedKmh: Double,
        still: Boolean,
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        val wasSleeping = currentPolicy == PowerPolicy.REST_STOP_SLEEP
        currentSpeedKmh = speedKmh.coerceAtLeast(0.0)

        if (still && speedKmh <= config.restStopSpeedThresholdKmh) {
            isStill = true
            if (stillnessStartTimestampMs == null) {
                stillnessStartTimestampMs = timestampMs
            }
        } else {
            isStill = false
            stillnessStartTimestampMs = null
            if (wasSleeping && (speedKmh >= config.minMotionWakeSpeedKmh || !still)) {
                lastWakeReason = "MOTION_DETECTED"
            }
        }

        recomputeState(timestampMs)
    }

    /**
     * Updates active emergency SOS status. Unconditionally preempts power-saving when true.
     */
    suspend fun updateEmergencyStatus(
        emergencyActive: Boolean,
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        val wasSleeping = currentPolicy == PowerPolicy.REST_STOP_SLEEP
        isEmergencyActive = emergencyActive
        if (emergencyActive && wasSleeping) {
            lastWakeReason = "EMERGENCY_OVERRIDE"
        }
        recomputeState(timestampMs)
    }

    /**
     * Wakes the radio immediately upon user PTT keypress.
     */
    suspend fun onPttPressed(
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        if (currentPolicy == PowerPolicy.REST_STOP_SLEEP) {
            lastWakeReason = "PTT_PRESSED"
            stillnessStartTimestampMs = null
            isStill = false
        }
        recomputeState(timestampMs)
    }

    /**
     * Sets or clears a manual policy override.
     */
    suspend fun setManualOverride(
        policy: PowerPolicy?,
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        manualOverridePolicy = policy
        recomputeState(timestampMs)
    }

    /**
     * Periodic evaluation tick to check stillness expiration or time transitions.
     */
    suspend fun tick(
        timestampMs: Long = currentTimeMillis()
    ): PowerGovernorState = mutex.withLock {
        recomputeState(timestampMs)
    }

    /**
     * Creates a serializable power status packet for mesh broadcast.
     */
    fun createPowerStatusPacket(
        timestampMs: Long = currentTimeMillis()
    ): PowerStatusPacket {
        val state = _governorState.value
        return PowerStatusPacket(
            nodeId = nodeId,
            batteryPercent = state.batteryPercent,
            policy = state.policy,
            isCharging = state.isCharging,
            timestampMs = timestampMs
        )
    }

    /**
     * Serializes a power status packet to JSON.
     */
    fun serializePowerPacket(packet: PowerStatusPacket): String {
        return json.encodeToString(PowerStatusPacket.serializer(), packet)
    }

    /**
     * Deserializes a power status packet from JSON.
     */
    fun parsePowerPacket(jsonString: String): PowerStatusPacket? {
        return try {
            json.decodeFromString(PowerStatusPacket.serializer(), jsonString)
        } catch (_: Exception) {
            null
        }
    }

    private fun recomputeState(timestampMs: Long): PowerGovernorState {
        val stillDuration = if (isStill && stillnessStartTimestampMs != null) {
            (timestampMs - (stillnessStartTimestampMs ?: timestampMs)).coerceAtLeast(0L)
        } else {
            0L
        }

        val evaluatedPolicy = when {
            isEmergencyActive -> PowerPolicy.EMERGENCY_OVERRIDE
            manualOverridePolicy != null -> manualOverridePolicy ?: PowerPolicy.NORMAL
            isCharging -> PowerPolicy.NORMAL
            isStill && stillDuration >= config.restStopStillnessDurationMs -> PowerPolicy.REST_STOP_SLEEP
            batteryPercent < config.survivalBatteryThresholdPercent -> PowerPolicy.EXTREME_SURVIVAL
            batteryPercent < config.conservativeBatteryThresholdPercent -> PowerPolicy.CONSERVATIVE
            else -> PowerPolicy.NORMAL
        }

        if (evaluatedPolicy != currentPolicy) {
            currentPolicy = evaluatedPolicy
            lastTransitionTimestampMs = timestampMs
        }

        val dutyCycleProfile = when (evaluatedPolicy) {
            PowerPolicy.EMERGENCY_OVERRIDE -> config.dutyCycleEmergency
            PowerPolicy.NORMAL -> config.dutyCycleNormal
            PowerPolicy.CONSERVATIVE -> config.dutyCycleConservative
            PowerPolicy.EXTREME_SURVIVAL -> config.dutyCycleSurvival
            PowerPolicy.REST_STOP_SLEEP -> config.dutyCycleSleep
        }

        val newState = PowerGovernorState(
            policy = evaluatedPolicy,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            isStill = isStill,
            currentSpeedKmh = currentSpeedKmh,
            stillDurationMs = stillDuration,
            isEmergencyActive = isEmergencyActive,
            dutyCycle = dutyCycleProfile,
            wakeReason = lastWakeReason,
            lastTransitionTimestampMs = lastTransitionTimestampMs
        )

        _governorState.value = newState
        return newState
    }
}
