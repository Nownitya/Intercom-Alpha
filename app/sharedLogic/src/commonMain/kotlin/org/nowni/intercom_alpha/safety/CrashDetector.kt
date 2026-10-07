package org.nowni.intercom_alpha.safety

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import kotlin.math.max

/**
 * Pure Kotlin Multiplatform crash detection and motorcycle fall-over state machine.
 *
 * Evaluates real-time IMU accelerometer/gyroscope kinematics, vehicle speed deceleration,
 * and motorcycle tilt to autonomously detect collision impacts and stationary drops.
 * Manages an abortable 30-second pre-alert countdown before dispatching emergency SOS
 * beacons over the BLE mesh.
 *
 * Rules: Zero Java, Android SDK, or C/JNI dependencies in commonMain (Rules 2 & 3).
 */
class CrashDetector(
    val config: CrashConfig = CrashConfig(),
    val localNodeId: String = "local_rider",
    val localNodeName: String = "Local Rider"
) {
    private val mutex = Mutex()

    private val json = Json {
        classDiscriminator = "#type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _snapshot = MutableStateFlow(CrashCountdownSnapshot())
    val snapshot: StateFlow<CrashCountdownSnapshot> = _snapshot.asStateFlow()
    val countdownSnapshot: StateFlow<CrashCountdownSnapshot> get() = snapshot

    private val _outgoingAlerts = Channel<CrashAlertPacket>(Channel.BUFFERED)
    val outgoingAlerts: ReceiveChannel<CrashAlertPacket> = _outgoingAlerts
    val alertPackets: Flow<CrashAlertPacket> = _outgoingAlerts.receiveAsFlow()

    private val _alertChimeTriggers = Channel<Int>(Channel.BUFFERED)
    val alertChimeTriggers: ReceiveChannel<Int> = _alertChimeTriggers

    // State tracking variables
    private var tiltStartMs: Long? = null
    private var peakGForceRecorded: Float = 1.0f
    private var detectedAtTimestampMs: Long = 0L
    private var lastRecordedCoordinate: GpsCoordinate? = null
    private var incidentCounter: Long = 0L

    // Rolling speed history for emergency deceleration detection
    private data class SpeedSample(val speedKmh: Float, val timestampMs: Long)
    private val speedHistory = ArrayDeque<SpeedSample>()

    /**
     * Ingest an IMU sensor reading from the device's accelerometer and gyroscope.
     */
    suspend fun processImuReading(
        reading: ImuReading,
        currentGps: GpsCoordinate? = null
    ): CrashCountdownSnapshot = mutex.withLock {
        if (currentGps != null) {
            lastRecordedCoordinate = currentGps
        }

        val gForce = reading.totalGForce
        if (gForce > peakGForceRecorded) {
            peakGForceRecorded = gForce
        }

        val currentState = _snapshot.value.state

        // If emergency is already dispatched, maintain state but record peak G-force
        if (currentState == CrashState.EMERGENCY_DISPATCHED) {
            val updated = _snapshot.value.copy(
                peakGForce = max(_snapshot.value.peakGForce, gForce),
                lastCoordinate = lastRecordedCoordinate ?: _snapshot.value.lastCoordinate
            )
            _snapshot.value = updated
            return@withLock updated
        }

        // If countdown is active, check if another even harder impact occurs and continue ticking
        if (currentState == CrashState.COUNTDOWN_ACTIVE) {
            val updated = _snapshot.value.copy(
                peakGForce = max(_snapshot.value.peakGForce, gForce),
                lastCoordinate = lastRecordedCoordinate ?: _snapshot.value.lastCoordinate
            )
            _snapshot.value = updated
            return@withLock updated
        }

        // 1. Check for Severe Impact Collision (> severeImpactGThreshold, default 7.0g)
        if (gForce >= config.severeImpactGThreshold) {
            return@withLock initiateCountdownInternal(
                reading.timestampMs,
                CrashSeverity.SEVERE_COLLISION,
                "Severe impact detected (${(gForce * 10).toInt() / 10f}g >= ${config.severeImpactGThreshold}g)",
                gForce
            )
        }

        // 2. Check for Hard Impact Collision (> impactGThreshold, default 4.5g)
        if (gForce >= config.impactGThreshold) {
            return@withLock initiateCountdownInternal(
                reading.timestampMs,
                CrashSeverity.HARD_IMPACT,
                "Hard impact shock (${(gForce * 10).toInt() / 10f}g >= ${config.impactGThreshold}g)",
                gForce
            )
        }

        // 3. Check for Motorcycle Fall-Over (sustained tilt > fallOverTiltThresholdDeg for > fallOverDurationMs)
        val tilt = reading.tiltAngleDegrees
        if (tilt >= config.fallOverTiltThresholdDeg) {
            val start = tiltStartMs
            if (start == null) {
                tiltStartMs = reading.timestampMs
            } else if (reading.timestampMs - start >= config.fallOverDurationMs) {
                return@withLock initiateCountdownInternal(
                    reading.timestampMs,
                    CrashSeverity.POTENTIAL_FALL_OVER,
                    "Motorcycle fall-over detected (tilt ${tilt.toInt()}° >= ${config.fallOverTiltThresholdDeg.toInt()}° for ${(reading.timestampMs - start) / 1000}s)",
                    peakGForceRecorded
                )
            }
        } else {
            tiltStartMs = null
        }

        // Maintain current state snapshot
        _snapshot.value
    }

    /**
     * Ingest a speed update to monitor rapid deceleration anomalies.
     */
    suspend fun processSpeedUpdate(
        speedKmh: Float,
        timestampMs: Long = currentTimeMillis(),
        currentGps: GpsCoordinate? = null
    ): CrashCountdownSnapshot = mutex.withLock {
        if (currentGps != null) {
            lastRecordedCoordinate = currentGps
        }

        // Prune speed history older than the configured deceleration window
        speedHistory.addLast(SpeedSample(speedKmh, timestampMs))
        val cutoff = timestampMs - config.decelerationTimeWindowMs
        while (speedHistory.isNotEmpty() && speedHistory.first().timestampMs < cutoff) {
            speedHistory.removeFirst()
        }

        // Check for sudden severe deceleration (> decelerationDropKmh within window)
        if (speedHistory.size >= 2) {
            val oldest = speedHistory.first()
            val speedDrop = oldest.speedKmh - speedKmh
            if (speedDrop >= config.decelerationDropKmh && _snapshot.value.state == CrashState.NORMAL) {
                // Deceleration spike alone combined with any modest G-force or tilt
                if (peakGForceRecorded >= 2.5f) {
                    return@withLock initiateCountdownInternal(
                        timestampMs,
                        CrashSeverity.HARD_IMPACT,
                        "Emergency deceleration (-${speedDrop.toInt()} km/h in ${timestampMs - oldest.timestampMs}ms)",
                        peakGForceRecorded
                    )
                }
            }
        }

        _snapshot.value
    }

    suspend fun processSpeedUpdate(
        speedKmh: Double,
        timestampMs: Long = currentTimeMillis(),
        currentGps: GpsCoordinate? = null
    ): CrashCountdownSnapshot = processSpeedUpdate(speedKmh.toFloat(), timestampMs, currentGps)

    /**
     * Advances the countdown timer based on the current wall-clock timestamp.
     */
    suspend fun tickCountdown(currentTimestampMs: Long): CrashCountdownSnapshot = mutex.withLock {
        val current = _snapshot.value
        if (current.state != CrashState.COUNTDOWN_ACTIVE) {
            return@withLock current
        }

        val elapsedSeconds = ((currentTimestampMs - detectedAtTimestampMs) / 1000L).toInt()
        val remaining = (config.countdownDurationSeconds - elapsedSeconds).coerceAtLeast(0)

        if (remaining > 0) {
            val updated = current.copy(remainingSeconds = remaining)
            _snapshot.value = updated
            _alertChimeTriggers.trySend(remaining)
            return@withLock updated
        } else {
            // Countdown reached zero: Dispatch emergency crash alert over mesh!
            return@withLock dispatchEmergencyInternal(currentTimestampMs)
        }
    }

    /**
     * Rider explicitly cancels the pre-alert countdown (false alarm or minor drop).
     */
    suspend fun cancelCountdown(reason: String = "Cancelled by rider"): CrashCountdownSnapshot = mutex.withLock {
        if (_snapshot.value.state == CrashState.COUNTDOWN_ACTIVE) {
            val updated = _snapshot.value.copy(
                state = CrashState.CANCELLED_BY_RIDER,
                remainingSeconds = 0,
                triggerReason = reason
            )
            _snapshot.value = updated
            tiltStartMs = null
            peakGForceRecorded = 1.0f
            return@withLock updated
        }
        _snapshot.value
    }

    /**
     * Manually triggers an immediate crash emergency alert.
     */
    suspend fun triggerManualCrashAlert(
        coordinate: GpsCoordinate? = null,
        timestampMs: Long = 0L
    ): CrashCountdownSnapshot = mutex.withLock {
        if (coordinate != null) {
            lastRecordedCoordinate = coordinate
        }
        val now = if (timestampMs > 0L) timestampMs else detectedAtTimestampMs
        return@withLock dispatchEmergencyInternal(now, manual = true)
    }

    /**
     * Resets detector back to normal idle monitoring.
     */
    suspend fun reset(): CrashCountdownSnapshot = mutex.withLock {
        tiltStartMs = null
        peakGForceRecorded = 1.0f
        detectedAtTimestampMs = 0L
        speedHistory.clear()
        val clean = CrashCountdownSnapshot(state = CrashState.NORMAL)
        _snapshot.value = clean
        clean
    }

    /**
     * Encodes a CrashAlertPacket into raw byte payload for ControlType.CRASH_ALERT.
     */
    fun encodeAlertPayload(packet: CrashAlertPacket): ByteArray = Companion.encodeAlertPayload(packet)

    /**
     * Parses an incoming ControlType.CRASH_ALERT payload from bytes.
     */
    fun parseAlertPayload(payload: ByteArray): CrashAlertPacket? = Companion.parseAlertPayload(payload)

    companion object {
        private val alertJson = Json {
            classDiscriminator = "#type"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encodeAlertPayload(packet: CrashAlertPacket): ByteArray {
            val jsonStr = alertJson.encodeToString(CrashAlertPacket.serializer(), packet)
            return jsonStr.encodeToByteArray()
        }

        fun parseAlertPayload(payload: ByteArray): CrashAlertPacket? {
            if (payload.isEmpty()) return null
            return try {
                val jsonStr = payload.decodeToString()
                alertJson.decodeFromString(CrashAlertPacket.serializer(), jsonStr)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun initiateCountdownInternal(
        detectedAtMs: Long,
        severity: CrashSeverity,
        reason: String,
        peakG: Float
    ): CrashCountdownSnapshot {
        detectedAtTimestampMs = detectedAtMs
        peakGForceRecorded = max(peakGForceRecorded, peakG)
        val snapshot = CrashCountdownSnapshot(
            state = CrashState.COUNTDOWN_ACTIVE,
            remainingSeconds = config.countdownDurationSeconds,
            peakGForce = peakGForceRecorded,
            severity = severity,
            triggerReason = reason,
            detectedAtMs = detectedAtMs,
            lastCoordinate = lastRecordedCoordinate
        )
        _snapshot.value = snapshot
        _alertChimeTriggers.trySend(config.countdownDurationSeconds)
        return snapshot
    }

    private fun dispatchEmergencyInternal(
        timestampMs: Long,
        manual: Boolean = false
    ): CrashCountdownSnapshot {
        incidentCounter++
        val incidentId = "crash_${localNodeId}_${incidentCounter}_${timestampMs}"
        val current = _snapshot.value
        val severity = if (manual) CrashSeverity.SEVERE_COLLISION else current.severity
        val reason = if (manual) "Manual emergency crash SOS triggered by rider" else current.triggerReason

        val packet = CrashAlertPacket(
            incidentId = incidentId,
            senderId = localNodeId,
            senderName = localNodeName,
            peakGForce = current.peakGForce,
            severity = severity,
            triggerReason = reason,
            timestampMs = timestampMs,
            coordinate = lastRecordedCoordinate ?: current.lastCoordinate
        )

        val updated = current.copy(
            state = CrashState.EMERGENCY_DISPATCHED,
            remainingSeconds = 0,
            severity = severity,
            triggerReason = reason
        )
        _snapshot.value = updated
        _outgoingAlerts.trySend(packet)
        _alertChimeTriggers.trySend(0) // 0 signals emergency dispatch alarm tone
        return updated
    }
}
