package org.nowni.intercom_alpha.mesh.wearable

import kotlinx.serialization.Serializable

/**
 * Types of wearable companion devices supported by Intercom-Alpha.
 */
@Serializable
enum class WearableDeviceType(val wireCode: Int) {
    APPLE_WATCH(1),
    WEAR_OS(2),
    GARMIN_CONNECT_IQ(3),
    CUSTOM_BLE_REMOTE(4);

    companion object {
        fun fromWireCode(code: Int): WearableDeviceType =
            entries.firstOrNull { it.wireCode == code } ?: CUSTOM_BLE_REMOTE
    }
}

/**
 * State of physical or capacitive PTT handlebar/watch buttons.
 */
@Serializable
enum class PttButtonState(val wireCode: Int) {
    RELEASED(0),
    PRESSED(1),
    DOUBLE_TAP_SOS(2),
    LONG_PRESS_MUTE(3);

    companion object {
        fun fromWireCode(code: Int): PttButtonState =
            entries.firstOrNull { it.wireCode == code } ?: RELEASED
    }
}

/**
 * Haptic buzz vibration patterns sent back to the wearable device.
 */
@Serializable
enum class HapticFeedbackPattern(val wireCode: Int) {
    CONFIRM_TRANSMIT(1),
    TRANSMIT_RELEASE(2),
    DISTRESS_WARNING(3),
    EMERGENCY_SOS_ALERT(4),
    BATTERY_LOW(5);

    companion object {
        fun fromWireCode(code: Int): HapticFeedbackPattern =
            entries.firstOrNull { it.wireCode == code } ?: CONFIRM_TRANSMIT
    }
}

/**
 * Biometric vitals captured from smartwatch optical/ECG sensors.
 */
@Serializable
data class RiderBiometricVitals(
    val heartRateBpm: Int,
    val spo2Percent: Int = 98,
    val skinTemperatureCelsius: Double = 36.5,
    val stressLevelIndex: Int = 20,
    val isFallDetected: Boolean = false,
    val batteryPercent: Int = 100,
    val timestampMs: Long = 0L
) {
    fun isElevated(): Boolean = heartRateBpm > 160 || isFallDetected
    fun isCritical(): Boolean = heartRateBpm > 185 || isFallDetected || spo2Percent < 90
}

/**
 * Evaluated distress condition of the rider.
 */
@Serializable
enum class RiderDistressStatus {
    NORMAL,
    FATIGUE_WARNING,
    CRITICAL_TACHYCARDIA,
    FALL_CONFIRMED,
    EMERGENCY_SOS
}

/**
 * Wearable Companion Packet exchanged over BLE GATT / L2CAP.
 */
@Serializable
data class WearableCompanionPacket(
    val deviceId: String,
    val riderId: String,
    val deviceType: WearableDeviceType,
    val pttState: PttButtonState,
    val vitals: RiderBiometricVitals? = null,
    val sequenceNumber: Long = 0L,
    val timestampMs: Long = 0L
) {
    /**
     * Compact binary frame encoding (<= 16 bytes for zero-latency BLE airtime).
     *
     * Wire layout:
     * Byte 0: Protocol version (0x01)
     * Byte 1: Flags:
     *   Bits 0-1: deviceType wireCode (1..4)
     *   Bits 2-3: pttState wireCode (0..3)
     *   Bit 4: isFallDetected (0 or 1)
     *   Bit 5: hasVitals (0 or 1)
     * Byte 2: heartRateBpm (0..255)
     * Byte 3: spo2Percent (0..100)
     * Byte 4: batteryPercent (0..100)
     * Byte 5: stressLevelIndex (0..100)
     * Bytes 6-7: skinTemperature * 10 (Short, big-endian)
     * Bytes 8-11: sequenceNumber (UInt, big-endian)
     */
    fun toCompactBinary(): ByteArray {
        val bytes = ByteArray(12)
        bytes[0] = 0x01 // Version

        val devCode = (deviceType.wireCode and 0x03)
        val pttCode = (pttState.wireCode and 0x03) shl 2
        val fallBit = if (vitals?.isFallDetected == true) 1 shl 4 else 0
        val vitalsBit = if (vitals != null) 1 shl 5 else 0
        bytes[1] = (devCode or pttCode or fallBit or vitalsBit).toByte()

        bytes[2] = (vitals?.heartRateBpm ?: 0).coerceIn(0, 255).toByte()
        bytes[3] = (vitals?.spo2Percent ?: 0).coerceIn(0, 100).toByte()
        bytes[4] = (vitals?.batteryPercent ?: 100).coerceIn(0, 100).toByte()
        bytes[5] = (vitals?.stressLevelIndex ?: 0).coerceIn(0, 100).toByte()

        val tempScaled = ((vitals?.skinTemperatureCelsius ?: 36.5) * 10).toInt().coerceIn(0, 65535)
        bytes[6] = ((tempScaled shr 8) and 0xFF).toByte()
        bytes[7] = (tempScaled and 0xFF).toByte()

        val seq = (sequenceNumber and 0xFFFFFFFFL).toInt()
        bytes[8] = ((seq shr 24) and 0xFF).toByte()
        bytes[9] = ((seq shr 16) and 0xFF).toByte()
        bytes[10] = ((seq shr 8) and 0xFF).toByte()
        bytes[11] = (seq and 0xFF).toByte()

        return bytes
    }

    companion object {
        fun fromCompactBinary(
            deviceId: String,
            riderId: String,
            bytes: ByteArray,
            timestampMs: Long
        ): WearableCompanionPacket? {
            if (bytes.size < 12 || bytes[0] != 0x01.toByte()) return null

            val flags = bytes[1].toInt() and 0xFF
            val devCode = flags and 0x03
            val pttCode = (flags shr 2) and 0x03
            val isFall = (flags and (1 shl 4)) != 0
            val hasVitals = (flags and (1 shl 5)) != 0

            val deviceType = WearableDeviceType.fromWireCode(devCode)
            val pttState = PttButtonState.fromWireCode(pttCode)

            val vitals = if (hasVitals) {
                val hr = bytes[2].toInt() and 0xFF
                val spo2 = bytes[3].toInt() and 0xFF
                val bat = bytes[4].toInt() and 0xFF
                val stress = bytes[5].toInt() and 0xFF
                val tempRaw = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
                val tempCelsius = tempRaw / 10.0

                RiderBiometricVitals(
                    heartRateBpm = hr,
                    spo2Percent = spo2,
                    skinTemperatureCelsius = tempCelsius,
                    stressLevelIndex = stress,
                    isFallDetected = isFall,
                    batteryPercent = bat,
                    timestampMs = timestampMs
                )
            } else null

            val seq = ((bytes[8].toLong() and 0xFF) shl 24) or
                    ((bytes[9].toLong() and 0xFF) shl 16) or
                    ((bytes[10].toLong() and 0xFF) shl 8) or
                    (bytes[11].toLong() and 0xFF)

            return WearableCompanionPacket(
                deviceId = deviceId,
                riderId = riderId,
                deviceType = deviceType,
                pttState = pttState,
                vitals = vitals,
                sequenceNumber = seq,
                timestampMs = timestampMs
            )
        }
    }
}

/**
 * Real-time telemetry snapshot of connected wearable device state.
 */
@Serializable
data class WearableCompanionSnapshot(
    val deviceType: WearableDeviceType = WearableDeviceType.APPLE_WATCH,
    val pttState: PttButtonState = PttButtonState.RELEASED,
    val distressStatus: RiderDistressStatus = RiderDistressStatus.NORMAL,
    val vitals: RiderBiometricVitals? = null
)
