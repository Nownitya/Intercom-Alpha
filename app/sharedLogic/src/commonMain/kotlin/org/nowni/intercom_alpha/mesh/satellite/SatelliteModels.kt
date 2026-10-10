package org.nowni.intercom_alpha.mesh.satellite

import kotlinx.serialization.Serializable

/**
 * Supported satellite transceivers and constellations.
 * Rule 2 & 3: Pure Kotlin Multiplatform with zero Java/C dependencies.
 */
@Serializable
enum class SatelliteModemType {
    /** Iridium 9602/9603 Short Burst Data (SBD) direct transceiver */
    IRIDIUM_SBD,

    /** Garmin inReach Bluetooth / NMEA serial companion interface */
    GARMIN_INREACH,

    /** Somewear Global satellite hotspot interface */
    SOMEWEAR_GLOBAL,

    /** 3GPP Release 17 Non-Terrestrial Network (Direct-to-Cell satellite) */
    DIRECT_TO_CELL_NTN
}

/**
 * Satellite link state machine lifecycle status.
 */
@Serializable
enum class SatelliteLinkState {
    OFFLINE,
    SEARCHING_CONSTELLATION,
    SATELLITE_ACQUIRED,
    TRANSMITTING,
    TRANSMIT_SUCCESS,
    TRANSMIT_FAILED
}

/**
 * Priority classification for satellite telemetry dispatch.
 */
@Serializable
enum class SatelliteDispatchPriority {
    LIFE_SAFETY_CRASH,
    EMERGENCY_SOS_MANUAL,
    CONVOY_LOST_BEACON,
    STANDARD_CHECKIN
}

/**
 * Geographic location fix packaged for satellite burst transmission.
 */
@Serializable
data class SatelliteGpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 0f
)

/**
 * High-reliability satellite emergency payload formatted for low-bandwidth burst transmission.
 */
@Serializable
data class SatelliteDispatchPayload(
    val dispatchId: String,
    val senderRiderId: String,
    val groupId: String,
    val timestamp: Long,
    val priority: SatelliteDispatchPriority,
    val location: SatelliteGpsLocation,
    val impactGForce: Float? = null,
    val batteryPercent: Int = 100,
    val messageText: String = "",
    val retryCount: Int = 0
)

/**
 * Acknowledgment returned by satellite gateway or constellation ground station.
 */
@Serializable
data class SatelliteDispatchAck(
    val dispatchId: String,
    val constellationTimestamp: Long,
    val sbdSequenceNumber: Long,
    val isDeliveredToRescueCenter: Boolean,
    val responseMessage: String? = null
)

/**
 * Real-time telemetry snapshot of the satellite emergency bridge.
 */
@Serializable
data class SatelliteBridgeStatus(
    val modemType: SatelliteModemType = SatelliteModemType.IRIDIUM_SBD,
    val linkState: SatelliteLinkState = SatelliteLinkState.OFFLINE,
    val signalBars: Int = 0,
    val pendingQueueSize: Int = 0,
    val totalDispatchedCount: Int = 0,
    val totalAcksReceivedCount: Int = 0,
    val lastTransmissionTimestamp: Long = 0L,
    val lastAckTimestamp: Long = 0L
)

/**
 * Pure Kotlin CRC-16 CCITT (polynomial 0x1021) calculator for satellite SBD frame integrity.
 */
object SatelliteCrc16 {
    fun calculate(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            val byte = b.toInt() and 0xFF
            for (i in 0 until 8) {
                val bit = ((byte ushr (7 - i)) and 1) == 1
                val c15 = ((crc ushr 15) and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) {
                    crc = crc xor 0x1021
                }
            }
        }
        return crc and 0xFFFF
    }
}
