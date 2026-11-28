package org.nowni.intercom_alpha.mesh

/**
 * Sub-frame classification discriminator embedded in packed BLE ATT MTU payloads.
 */
enum class SubFrameType(val code: Byte) {
    EMERGENCY_SOS(0x00),
    VOICE_AUDIO(0x01),
    CONTROL_SIGNALING(0x02),
    HAZARD_TEXT(0x03),
    FEC_PARITY(0x04),
    TELEMETRY(0x05);

    companion object {
        fun fromCode(code: Byte): SubFrameType =
            entries.firstOrNull { it.code == code } ?: VOICE_AUDIO
    }
}

/**
 * Individual logical frame encapsulated within a packed BLE ATT MTU packet.
 */
data class SubFrame(
    val type: SubFrameType,
    val payload: ByteArray,
    val timestampMs: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubFrame) return false
        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false
        return timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}

/**
 * Aggregated BLE MTU packet emitted for over-the-air transmission.
 */
data class PackedMtuPacket(
    val sequenceNumber: Int,
    val isEmergency: Boolean,
    val subFrames: List<SubFrame>,
    val rawBytes: ByteArray
) {
    val frameCount: Int get() = subFrames.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackedMtuPacket) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (isEmergency != other.isEmergency) return false
        if (subFrames != other.subFrames) return false
        return rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber
        result = 31 * result + isEmergency.hashCode()
        result = 31 * result + subFrames.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

/**
 * Real-time operational telemetry for BLE ATT MTU aggregation.
 */
data class BleMtuTelemetry(
    val negotiatedMtu: Int,
    val effectivePayloadCapacity: Int,
    val totalFramesIngested: Long,
    val totalPacketsEmitted: Long,
    val averageFramesPerPacket: Double,
    val packingEfficiencyPct: Double,
    val rfPacketReductionPct: Double
)

/**
 * Configuration options for BleMtuAggregator.
 */
data class BleMtuAggregatorConfig(
    val defaultMtu: Int = DEFAULT_BLE_ATT_MTU,
    val targetFramesPerPacket: Int = DEFAULT_TARGET_FRAMES,
    val maxHoldDelayMs: Long = DEFAULT_MAX_HOLD_DELAY_MS
) {
    companion object {
        const val DEFAULT_BLE_ATT_MTU = 247
        const val BLE_ATT_HEADER_BYTES = 3
        const val DEFAULT_TARGET_FRAMES = 3
        const val DEFAULT_MAX_HOLD_DELAY_MS = 25L
        const val MIN_BLE_ATT_MTU = 23
        const val MAX_BLE_ATT_MTU = 512
    }
}

/**
 * Multiplatform BLE ATT MTU Frame Aggregator & Sub-Packet Framer.
 *
 * Packs multiple compressed voice frames, FEC parity, or control signals into a single
 * negotiated BLE ATT MTU payload (up to 247 bytes), slashing packet transmission overhead
 * by over 50% and dramatically cutting 2.4 GHz RF medium contention.
 *
 * Binary Wire Format:
 * [0]     : Magic Byte (0xBE)
 * [1..2]  : Sequence Number (Big-Endian UInt16)
 * [3]     : Header Flags (bit 0..3: sub-frame count, bit 4: emergency flag, bit 5..7: reserved)
 * Repeated for each sub-frame:
 *   [0]   : Sub-frame Payload Length (1..255)
 *   [1]   : Sub-frame Type Discriminator (SubFrameType.code)
 *   [2..N]: Sub-frame Payload Bytes
 */
class BleMtuAggregator(
    val config: BleMtuAggregatorConfig = BleMtuAggregatorConfig()
) {
    companion object {
        const val MAGIC_BYTE: Byte = 0xBE.toByte()
        const val ENVELOPE_HEADER_SIZE = 4 // Magic(1) + Seq(2) + Flags(1)
        const val SUBFRAME_HEADER_SIZE = 2 // Length(1) + Type(1)
    }

    private var activeMtu: Int = config.defaultMtu.coerceIn(
        BleMtuAggregatorConfig.MIN_BLE_ATT_MTU,
        BleMtuAggregatorConfig.MAX_BLE_ATT_MTU
    )

    private val effectiveCapacity: Int
        get() = (activeMtu - BleMtuAggregatorConfig.BLE_ATT_HEADER_BYTES - ENVELOPE_HEADER_SIZE).coerceAtLeast(0)

    private val pendingBuffer = mutableListOf<SubFrame>()
    private var sequenceCounter = 0
    private var oldestBufferedTimestampMs: Long = 0L

    // Telemetry tracking
    private var totalFramesIngested: Long = 0L
    private var totalPacketsEmitted: Long = 0L
    private var totalRawBytesPacked: Long = 0L
    private var totalPayloadBytesPacked: Long = 0L

    /**
     * Updates the current negotiated BLE ATT MTU (e.g. following onMtuChanged).
     */
    fun setNegotiatedMtu(mtu: Int) {
        activeMtu = mtu.coerceIn(
            BleMtuAggregatorConfig.MIN_BLE_ATT_MTU,
            BleMtuAggregatorConfig.MAX_BLE_ATT_MTU
        )
    }

    fun getNegotiatedMtu(): Int = activeMtu

    /**
     * Adds an incoming sub-frame into the aggregation buffer.
     * If the buffer reaches target capacity, or if adding this frame would overflow MTU limits,
     * or if the frame is an EMERGENCY_SOS, a [PackedMtuPacket] is immediately emitted.
     */
    fun addFrame(
        payload: ByteArray,
        type: SubFrameType = SubFrameType.VOICE_AUDIO,
        timestampMs: Long = 0L
    ): List<PackedMtuPacket> {
        val emitted = mutableListOf<PackedMtuPacket>()
        totalFramesIngested++

        val frame = SubFrame(type, payload, timestampMs)
        val frameWireSize = SUBFRAME_HEADER_SIZE + payload.size

        // If Emergency SOS: flush any pending non-emergency frames, then flush the emergency frame immediately
        if (type == SubFrameType.EMERGENCY_SOS) {
            if (pendingBuffer.isNotEmpty()) {
                flushBuffer()?.let { emitted.add(it) }
            }
            pendingBuffer.add(frame)
            flushBuffer()?.let { emitted.add(it) }
            return emitted
        }

        // Check if adding this frame exceeds effective MTU capacity
        val currentSize = currentPendingPayloadSize()
        if (currentSize + frameWireSize > effectiveCapacity && pendingBuffer.isNotEmpty()) {
            flushBuffer()?.let { emitted.add(it) }
        }

        // Add to buffer
        if (pendingBuffer.isEmpty()) {
            oldestBufferedTimestampMs = timestampMs
        }
        pendingBuffer.add(frame)

        // Check if target frames per packet reached
        if (pendingBuffer.size >= config.targetFramesPerPacket) {
            flushBuffer()?.let { emitted.add(it) }
        }

        return emitted
    }

    /**
     * Checks if the pending buffer exceeds the maximum hold delay and flushes if necessary.
     */
    fun flushPending(currentTimeMs: Long): PackedMtuPacket? {
        if (pendingBuffer.isEmpty()) return null
        val elapsed = currentTimeMs - oldestBufferedTimestampMs
        return if (elapsed >= config.maxHoldDelayMs) {
            flushBuffer()
        } else {
            null
        }
    }

    /**
     * Forces immediate emission of any buffered frames.
     */
    fun flush(): PackedMtuPacket? = flushBuffer()

    private fun flushBuffer(): PackedMtuPacket? {
        if (pendingBuffer.isEmpty()) return null

        val seq = (sequenceCounter++) and 0xFFFF
        val isEmergency = pendingBuffer.any { it.type == SubFrameType.EMERGENCY_SOS }
        val count = pendingBuffer.size.coerceAtMost(15)

        // Calculate total buffer length
        val payloadLength = currentPendingPayloadSize()
        val totalLength = ENVELOPE_HEADER_SIZE + payloadLength
        val buffer = ByteArray(totalLength)

        // Envelope Header
        buffer[0] = MAGIC_BYTE
        buffer[1] = ((seq shr 8) and 0xFF).toByte()
        buffer[2] = (seq and 0xFF).toByte()

        var flags = count and 0x0F
        if (isEmergency) flags = flags or 0x10
        buffer[3] = flags.toByte()

        // Serialize sub-frames
        var offset = ENVELOPE_HEADER_SIZE
        var rawSubPayloadBytes = 0L

        for (frame in pendingBuffer) {
            val len = frame.payload.size.coerceAtMost(255)
            buffer[offset++] = len.toByte()
            buffer[offset++] = frame.type.code
            frame.payload.copyInto(buffer, offset, 0, len)
            offset += len
            rawSubPayloadBytes += len
        }

        val packedPacket = PackedMtuPacket(
            sequenceNumber = seq,
            isEmergency = isEmergency,
            subFrames = pendingBuffer.toList(),
            rawBytes = buffer
        )

        // Update telemetry
        totalPacketsEmitted++
        totalRawBytesPacked += totalLength
        totalPayloadBytesPacked += rawSubPayloadBytes

        pendingBuffer.clear()
        oldestBufferedTimestampMs = 0L

        return packedPacket
    }

    /**
     * Unpacks an over-the-air raw packed MTU byte array back into individual logical [SubFrame] items.
     * Validates magic byte, extracts envelope metadata, and parses sub-frames.
     */
    fun unpack(packedPayload: ByteArray): List<SubFrame> {
        if (packedPayload.size < ENVELOPE_HEADER_SIZE) return emptyList()
        if (packedPayload[0] != MAGIC_BYTE) return emptyList()

        val flags = packedPayload[3].toInt() and 0xFF
        val frameCount = flags and 0x0F

        val result = mutableListOf<SubFrame>()
        var offset = ENVELOPE_HEADER_SIZE

        while (offset + SUBFRAME_HEADER_SIZE <= packedPayload.size && result.size < frameCount) {
            val length = packedPayload[offset].toInt() and 0xFF
            val typeCode = packedPayload[offset + 1]
            offset += SUBFRAME_HEADER_SIZE

            if (offset + length > packedPayload.size) break

            val payload = packedPayload.copyOfRange(offset, offset + length)
            offset += length

            result.add(
                SubFrame(
                    type = SubFrameType.fromCode(typeCode),
                    payload = payload
                )
            )
        }

        return result
    }

    private fun currentPendingPayloadSize(): Int {
        var size = 0
        for (f in pendingBuffer) {
            size += SUBFRAME_HEADER_SIZE + f.payload.size
        }
        return size
    }

    /**
     * Emits the current real-time operational telemetry.
     */
    fun getTelemetry(): BleMtuTelemetry {
        val avgFrames = if (totalPacketsEmitted > 0) {
            totalFramesIngested.toDouble() / totalPacketsEmitted.toDouble()
        } else {
            0.0
        }

        val packingEfficiency = if (totalRawBytesPacked > 0) {
            (totalPayloadBytesPacked.toDouble() / totalRawBytesPacked.toDouble()) * 100.0
        } else {
            0.0
        }

        val reduction = if (totalFramesIngested > 0) {
            val savedPackets = (totalFramesIngested - totalPacketsEmitted).coerceAtLeast(0)
            (savedPackets.toDouble() / totalFramesIngested.toDouble()) * 100.0
        } else {
            0.0
        }

        return BleMtuTelemetry(
            negotiatedMtu = activeMtu,
            effectivePayloadCapacity = effectiveCapacity,
            totalFramesIngested = totalFramesIngested,
            totalPacketsEmitted = totalPacketsEmitted,
            averageFramesPerPacket = avgFrames,
            packingEfficiencyPct = packingEfficiency,
            rfPacketReductionPct = reduction
        )
    }

    fun resetTelemetry() {
        totalFramesIngested = 0L
        totalPacketsEmitted = 0L
        totalRawBytesPacked = 0L
        totalPayloadBytesPacked = 0L
    }
}
