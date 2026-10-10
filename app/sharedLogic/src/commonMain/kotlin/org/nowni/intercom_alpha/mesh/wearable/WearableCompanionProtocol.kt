package org.nowni.intercom_alpha.mesh.wearable

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure KMP Smartwatch and Handlebar Companion Protocol.
 *
 * Provides sub-millisecond PTT debouncing, haptic feedback triggers,
 * biometric vitals telemetry classification, and rider distress/fall detection.
 *
 * 100% Pure Kotlin Multiplatform with zero Java/C dependencies (Rules 2 & 3).
 */
class WearableCompanionProtocol(
    val localRiderId: String,
    val localDeviceId: String,
    val deviceType: WearableDeviceType = WearableDeviceType.APPLE_WATCH,
    val debounceIntervalMs: Long = 50L
) {
    private val mutex = Mutex()

    private val _pttState = MutableStateFlow(PttButtonState.RELEASED)
    val pttState: StateFlow<PttButtonState> = _pttState.asStateFlow()

    private val _riderDistressStatus = MutableStateFlow(RiderDistressStatus.NORMAL)
    val riderDistressStatus: StateFlow<RiderDistressStatus> = _riderDistressStatus.asStateFlow()

    private val _hapticCommands = MutableSharedFlow<HapticFeedbackPattern>(extraBufferCapacity = 32)
    val hapticCommands: SharedFlow<HapticFeedbackPattern> = _hapticCommands.asSharedFlow()

    private val _latestVitals = MutableStateFlow<RiderBiometricVitals?>(null)
    val latestVitals: StateFlow<RiderBiometricVitals?> = _latestVitals.asStateFlow()

    private var lastPttTimestampMs: Long = 0L
    private var sequenceCounter: Long = 0L

    /**
     * Processes a PTT button interaction with hardware debouncing and haptic dispatch.
     */
    suspend fun processPttEvent(newState: PttButtonState, timestampMs: Long): Boolean = mutex.withLock {
        // Debounce check
        if ((timestampMs - lastPttTimestampMs) < debounceIntervalMs && newState == _pttState.value) {
            return@withLock false
        }

        lastPttTimestampMs = timestampMs
        _pttState.value = newState

        // Determine haptic response
        val haptic = when (newState) {
            PttButtonState.PRESSED -> HapticFeedbackPattern.CONFIRM_TRANSMIT
            PttButtonState.RELEASED -> HapticFeedbackPattern.TRANSMIT_RELEASE
            PttButtonState.DOUBLE_TAP_SOS -> {
                _riderDistressStatus.value = RiderDistressStatus.EMERGENCY_SOS
                HapticFeedbackPattern.EMERGENCY_SOS_ALERT
            }
            PttButtonState.LONG_PRESS_MUTE -> HapticFeedbackPattern.CONFIRM_TRANSMIT
        }

        _hapticCommands.tryEmit(haptic)
        return@withLock true
    }

    /**
     * Processes incoming biometric vitals and classifies rider distress conditions.
     */
    suspend fun processBiometricVitals(vitals: RiderBiometricVitals): RiderDistressStatus = mutex.withLock {
        _latestVitals.value = vitals

        val status = when {
            vitals.isFallDetected -> {
                _hapticCommands.tryEmit(HapticFeedbackPattern.EMERGENCY_SOS_ALERT)
                RiderDistressStatus.FALL_CONFIRMED
            }
            vitals.heartRateBpm > 185 || vitals.spo2Percent < 88 -> {
                _hapticCommands.tryEmit(HapticFeedbackPattern.DISTRESS_WARNING)
                RiderDistressStatus.CRITICAL_TACHYCARDIA
            }
            vitals.heartRateBpm > 160 || vitals.stressLevelIndex > 80 -> {
                RiderDistressStatus.FATIGUE_WARNING
            }
            vitals.batteryPercent <= 15 -> {
                _hapticCommands.tryEmit(HapticFeedbackPattern.BATTERY_LOW)
                RiderDistressStatus.NORMAL
            }
            else -> RiderDistressStatus.NORMAL
        }

        _riderDistressStatus.value = status
        return@withLock status
    }

    /**
     * Encodes current PTT state and vitals into a compact binary frame for transmission.
     */
    suspend fun createOutgoingFrame(
        pttState: PttButtonState,
        vitals: RiderBiometricVitals?,
        timestampMs: Long
    ): ByteArray = mutex.withLock {
        sequenceCounter++
        val packet = WearableCompanionPacket(
            deviceId = localDeviceId,
            riderId = localRiderId,
            deviceType = deviceType,
            pttState = pttState,
            vitals = vitals,
            sequenceNumber = sequenceCounter,
            timestampMs = timestampMs
        )
        packet.toCompactBinary()
    }

    /**
     * Parses an incoming compact binary frame from a wearable peripheral.
     */
    suspend fun processIncomingFrame(
        deviceId: String,
        riderId: String,
        bytes: ByteArray,
        timestampMs: Long
    ): WearableCompanionPacket? {
        val packet = WearableCompanionPacket.fromCompactBinary(deviceId, riderId, bytes, timestampMs) ?: return null

        processPttEvent(packet.pttState, timestampMs)
        if (packet.vitals != null) {
            processBiometricVitals(packet.vitals)
        }

        return packet
    }
}
