package org.nowni.intercom_alpha.mesh.satellite

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Pure KMP Satellite Communicator Emergency Gateway Bridge.
 *
 * Translates life-safety mesh emergency packets (CRASH_ALERT, SOS beacons) into
 * compact binary Iridium SBD / Garmin inReach frames, manages satellite transmission
 * queues, handles timeouts, and routes constellation ACKs back to local mesh riders.
 *
 * Rules 2 & 3: 100% Pure Kotlin Multiplatform with zero Java/C dependencies.
 */
class SatelliteGatewayBridge(
    val localNodeId: String,
    val modemType: SatelliteModemType = SatelliteModemType.IRIDIUM_SBD,
    val maxRetries: Int = 3,
    private val timeProvider: () -> Long = { currentTimeMillis() }
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow(
        SatelliteBridgeStatus(
            modemType = modemType,
            linkState = SatelliteLinkState.OFFLINE
        )
    )
    val status: StateFlow<SatelliteBridgeStatus> = _status.asStateFlow()

    private val _incomingAcks = MutableSharedFlow<SatelliteDispatchAck>(extraBufferCapacity = 16)
    val incomingAcks: SharedFlow<SatelliteDispatchAck> = _incomingAcks.asSharedFlow()

    private val dispatchQueue = ArrayDeque<SatelliteDispatchPayload>()
    private val pendingAcks = HashMap<String, SatelliteDispatchPayload>()

    /**
     * Raw transceiver transmit hook, wired to physical hardware (BLE Serial / NMEA / AT modem)
     * or simulated in unit tests.
     */
    var rawTransceiverSend: (suspend (payloadBytes: ByteArray) -> Boolean)? = null

    /**
     * Queues an emergency dispatch payload for satellite transmission.
     */
    suspend fun enqueueEmergencyDispatch(payload: SatelliteDispatchPayload): Boolean = mutex.withLock {
        dispatchQueue.addLast(payload)
        updateStatusInternal {
            it.copy(
                pendingQueueSize = dispatchQueue.size,
                linkState = if (it.linkState == SatelliteLinkState.OFFLINE) SatelliteLinkState.SEARCHING_CONSTELLATION else it.linkState
            )
        }
        return true
    }

    /**
     * Processes pending messages in the dispatch queue.
     * Attempts transmission via the registered transceiver hook.
     *
     * @return Number of payloads successfully dispatched to satellite modem.
     */
    suspend fun processQueue(): Int = mutex.withLock {
        if (dispatchQueue.isEmpty()) return 0
        val sender = rawTransceiverSend ?: return 0

        var dispatchedCount = 0
        val now = timeProvider()

        val itemsToProcess = dispatchQueue.toList()
        dispatchQueue.clear()

        for (item in itemsToProcess) {
            updateStatusInternal { it.copy(linkState = SatelliteLinkState.TRANSMITTING) }

            val wireBytes = encodeSbdFrame(item)
            val success = sender.invoke(wireBytes)

            if (success) {
                dispatchedCount++
                pendingAcks[item.dispatchId] = item
                updateStatusInternal {
                    it.copy(
                        linkState = SatelliteLinkState.TRANSMIT_SUCCESS,
                        totalDispatchedCount = it.totalDispatchedCount + 1,
                        lastTransmissionTimestamp = now,
                        pendingQueueSize = dispatchQueue.size
                    )
                }
            } else {
                if (item.retryCount < maxRetries) {
                    dispatchQueue.addLast(item.copy(retryCount = item.retryCount + 1))
                }
                updateStatusInternal {
                    it.copy(
                        linkState = SatelliteLinkState.TRANSMIT_FAILED,
                        pendingQueueSize = dispatchQueue.size
                    )
                }
            }
        }

        return dispatchedCount
    }

    /**
     * Ingests a satellite acknowledgment received from constellation ground station.
     */
    suspend fun handleIncomingSatelliteAck(ack: SatelliteDispatchAck): Boolean = mutex.withLock {
        val pending = pendingAcks.remove(ack.dispatchId)
        val now = timeProvider()

        _incomingAcks.tryEmit(ack)
        updateStatusInternal {
            it.copy(
                totalAcksReceivedCount = it.totalAcksReceivedCount + 1,
                lastAckTimestamp = now
            )
        }
        return pending != null
    }

    /**
     * Updates external modem telemetry (signal bars, link state).
     */
    suspend fun updateModemLinkState(state: SatelliteLinkState, signalBars: Int) = mutex.withLock {
        updateStatusInternal {
            it.copy(
                linkState = state,
                signalBars = signalBars.coerceIn(0, 5)
            )
        }
    }

    /**
     * Converts a mesh CRASH_ALERT or emergency Control packet into a satellite dispatch payload.
     */
    fun convertEmergencyControlPacket(
        packet: MeshPacket.Control,
        groupId: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double = 0.0,
        impactGForce: Float? = null,
        batteryPercent: Int = 100
    ): SatelliteDispatchPayload? {
        val priority = when (packet.type) {
            ControlType.CRASH_ALERT -> SatelliteDispatchPriority.LIFE_SAFETY_CRASH
            ControlType.GEOFENCE_ALARM -> SatelliteDispatchPriority.CONVOY_LOST_BEACON
            else -> return null
        }

        val now = timeProvider()
        return SatelliteDispatchPayload(
            dispatchId = "sat-$now-${packet.senderId.take(6)}",
            senderRiderId = packet.senderId,
            groupId = groupId,
            timestamp = now,
            priority = priority,
            location = SatelliteGpsLocation(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters
            ),
            impactGForce = impactGForce,
            batteryPercent = batteryPercent,
            messageText = "SOS Alert: ${packet.type.name}"
        )
    }

    private fun updateStatusInternal(transform: (SatelliteBridgeStatus) -> SatelliteBridgeStatus) {
        _status.value = transform(_status.value)
    }

    companion object {
        private const val MAGIC_BYTE_1: Byte = 0x53 // 'S'
        private const val MAGIC_BYTE_2: Byte = 0x42 // 'B'

        /**
         * Encodes a [SatelliteDispatchPayload] into a compact binary Iridium SBD frame (<= 120 bytes).
         */
        fun encodeSbdFrame(payload: SatelliteDispatchPayload): ByteArray {
            val senderBytes = payload.senderRiderId.encodeToByteArray()
            val groupBytes = payload.groupId.encodeToByteArray()
            val dispatchBytes = payload.dispatchId.encodeToByteArray()

            val safeSenderLen = minOf(senderBytes.size, 16)
            val safeGroupLen = minOf(groupBytes.size, 16)
            val safeDispatchLen = minOf(dispatchBytes.size, 20)

            // Header (2) + Priority (1) + Timestamp (8) + Lat (8) + Lon (8) + Alt (4) + GForce (4) + Batt (1)
            // + ID Lens (3) + IDs + CRC (2)
            val contentSize = 2 + 1 + 8 + 8 + 8 + 4 + 4 + 1 + 3 + safeSenderLen + safeGroupLen + safeDispatchLen
            val buffer = ByteArray(contentSize + 2) // + 2 for CRC

            var offset = 0
            buffer[offset++] = MAGIC_BYTE_1
            buffer[offset++] = MAGIC_BYTE_2
            buffer[offset++] = payload.priority.ordinal.toByte()

            writeLongBe(buffer, offset, payload.timestamp); offset += 8
            writeDoubleBe(buffer, offset, payload.location.latitude); offset += 8
            writeDoubleBe(buffer, offset, payload.location.longitude); offset += 8
            writeFloatBe(buffer, offset, payload.location.altitudeMeters.toFloat()); offset += 4
            writeFloatBe(buffer, offset, payload.impactGForce ?: -1.0f); offset += 4
            buffer[offset++] = payload.batteryPercent.toByte()

            buffer[offset++] = safeSenderLen.toByte()
            senderBytes.copyInto(buffer, offset, 0, safeSenderLen); offset += safeSenderLen

            buffer[offset++] = safeGroupLen.toByte()
            groupBytes.copyInto(buffer, offset, 0, safeGroupLen); offset += safeGroupLen

            buffer[offset++] = safeDispatchLen.toByte()
            dispatchBytes.copyInto(buffer, offset, 0, safeDispatchLen); offset += safeDispatchLen

            // Calculate CRC-16 over content bytes
            val dataSlice = buffer.copyOfRange(0, offset)
            val crc = SatelliteCrc16.calculate(dataSlice)
            buffer[offset++] = ((crc ushr 8) and 0xFF).toByte()
            buffer[offset++] = (crc and 0xFF).toByte()

            return buffer
        }

        /**
         * Decodes an Iridium SBD frame back into a [SatelliteDispatchPayload], validating CRC-16.
         */
        fun decodeSbdFrame(data: ByteArray): SatelliteDispatchPayload? {
            if (data.size < 40) return null
            if (data[0] != MAGIC_BYTE_1 || data[1] != MAGIC_BYTE_2) return null

            val contentLen = data.size - 2
            val calculatedCrc = SatelliteCrc16.calculate(data.copyOfRange(0, contentLen))
            val expectedCrc = ((data[contentLen].toInt() and 0xFF) shl 8) or (data[contentLen + 1].toInt() and 0xFF)

            if (calculatedCrc != expectedCrc) {
                return null // CRC integrity mismatch
            }

            var offset = 2
            val priorityOrdinal = data[offset++].toInt()
            val priority = SatelliteDispatchPriority.values().getOrNull(priorityOrdinal) ?: SatelliteDispatchPriority.STANDARD_CHECKIN

            val timestamp = readLongBe(data, offset); offset += 8
            val lat = readDoubleBe(data, offset); offset += 8
            val lon = readDoubleBe(data, offset); offset += 8
            val alt = readFloatBe(data, offset).toDouble(); offset += 4
            val gForceRaw = readFloatBe(data, offset); offset += 4
            val gForce = if (gForceRaw < 0f) null else gForceRaw
            val battery = data[offset++].toInt() and 0xFF

            val senderLen = data[offset++].toInt() and 0xFF
            if (offset + senderLen > contentLen) return null
            val senderId = data.copyOfRange(offset, offset + senderLen).decodeToString(); offset += senderLen

            val groupLen = data[offset++].toInt() and 0xFF
            if (offset + groupLen > contentLen) return null
            val groupId = data.copyOfRange(offset, offset + groupLen).decodeToString(); offset += groupLen

            val dispatchLen = data[offset++].toInt() and 0xFF
            if (offset + dispatchLen > contentLen) return null
            val dispatchId = data.copyOfRange(offset, offset + dispatchLen).decodeToString()

            return SatelliteDispatchPayload(
                dispatchId = dispatchId,
                senderRiderId = senderId,
                groupId = groupId,
                timestamp = timestamp,
                priority = priority,
                location = SatelliteGpsLocation(
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = alt
                ),
                impactGForce = gForce,
                batteryPercent = battery
            )
        }

        private fun writeLongBe(buf: ByteArray, offset: Int, value: Long) {
            for (i in 0 until 8) {
                buf[offset + i] = ((value ushr ((7 - i) * 8)) and 0xFF).toByte()
            }
        }

        private fun readLongBe(buf: ByteArray, offset: Int): Long {
            var res = 0L
            for (i in 0 until 8) {
                res = (res shl 8) or (buf[offset + i].toLong() and 0xFF)
            }
            return res
        }

        private fun writeDoubleBe(buf: ByteArray, offset: Int, value: Double) {
            writeLongBe(buf, offset, value.toBits())
        }

        private fun readDoubleBe(buf: ByteArray, offset: Int): Double {
            return Double.fromBits(readLongBe(buf, offset))
        }

        private fun writeFloatBe(buf: ByteArray, offset: Int, value: Float) {
            val bits = value.toBits()
            buf[offset] = ((bits ushr 24) and 0xFF).toByte()
            buf[offset + 1] = ((bits ushr 16) and 0xFF).toByte()
            buf[offset + 2] = ((bits ushr 8) and 0xFF).toByte()
            buf[offset + 3] = (bits and 0xFF).toByte()
        }

        private fun readFloatBe(buf: ByteArray, offset: Int): Float {
            val bits = ((buf[offset].toInt() and 0xFF) shl 24) or
                    ((buf[offset + 1].toInt() and 0xFF) shl 16) or
                    ((buf[offset + 2].toInt() and 0xFF) shl 8) or
                    (buf[offset + 3].toInt() and 0xFF)
            return Float.fromBits(bits)
        }
    }
}
