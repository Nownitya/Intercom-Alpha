package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Forward Error Correction (FEC) Redundancy Modes for BLE Mesh Voice Audio.
 *
 * @property dataCount (K) Number of voice data packets in each FEC block.
 * @property parityCount (P) Number of redundant parity packets in each FEC block.
 */
enum class FecRatio(val dataCount: Int, val parityCount: Int) {
    /** 0% overhead. Raw transmission without FEC parity. */
    DISABLED(dataCount = 1, parityCount = 0),

    /** 25% overhead. 1 parity packet per 4 voice packets. Tolerates 1 erasure per block. */
    RATIO_4_1(dataCount = 4, parityCount = 1),

    /** 33% overhead. 1 parity packet per 3 voice packets. Tolerates 1 erasure per block. */
    RATIO_3_1(dataCount = 3, parityCount = 1),

    /** 50% overhead. 1 parity packet per 2 voice packets. Tolerates 1 erasure per block. */
    RATIO_2_1(dataCount = 2, parityCount = 1),

    /** 50% overhead. 2 Galois parity packets per 4 voice packets. Tolerates ANY 2 erasures per block. */
    RATIO_4_2(dataCount = 4, parityCount = 2);

    val totalCount: Int get() = dataCount + parityCount
    val redundancyPercent: Int get() = if (dataCount == 0) 0 else ((parityCount.toDouble() / dataCount.toDouble()) * 100.0).toInt()
}

/**
 * Compact FEC envelope containing voice payload or mathematical parity data.
 */
data class FecEnvelope(
    val blockId: Long,
    val indexInBlock: Int,
    val dataCount: Int,
    val parityCount: Int,
    val originalSize: Int,
    val payload: ByteArray
) {
    val isParity: Boolean get() = indexInBlock >= dataCount

    /**
     * Serializes this FEC envelope into a binary packet for BLE mesh transport.
     *
     * Wire Format (14-byte header + payload):
     * [0]: Magic byte (0xFE)
     * [1..8]: blockId (Long, big-endian)
     * [9]: indexInBlock (Byte)
     * [10]: dataCount (Byte)
     * [11]: parityCount (Byte)
     * [12..13]: originalSize (Short, big-endian)
     * [14..]: payload bytes
     */
    fun toByteArray(): ByteArray {
        val out = ByteArray(HEADER_SIZE + payload.size)
        out[0] = MAGIC_BYTE
        for (i in 0..7) {
            out[1 + i] = ((blockId ushr (56 - (i * 8))) and 0xFF).toByte()
        }
        out[9] = indexInBlock.toByte()
        out[10] = dataCount.toByte()
        out[11] = parityCount.toByte()
        out[12] = ((originalSize ushr 8) and 0xFF).toByte()
        out[13] = (originalSize and 0xFF).toByte()
        payload.copyInto(out, destinationOffset = HEADER_SIZE)
        return out
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FecEnvelope) return false
        return blockId == other.blockId &&
            indexInBlock == other.indexInBlock &&
            dataCount == other.dataCount &&
            parityCount == other.parityCount &&
            originalSize == other.originalSize &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = blockId.hashCode()
        result = 31 * result + indexInBlock
        result = 31 * result + dataCount
        result = 31 * result + parityCount
        result = 31 * result + originalSize
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC_BYTE: Byte = 0xFE.toByte()
        const val HEADER_SIZE: Int = 14

        /**
         * Parses a binary packet into an [FecEnvelope] if valid, or null if not an FEC envelope.
         */
        fun fromByteArray(bytes: ByteArray): FecEnvelope? {
            if (bytes.size < HEADER_SIZE || bytes[0] != MAGIC_BYTE) return null

            var bId = 0L
            for (i in 0..7) {
                bId = (bId shl 8) or (bytes[1 + i].toLong() and 0xFFL)
            }
            val index = bytes[9].toInt() and 0xFF
            val k = bytes[10].toInt() and 0xFF
            val p = bytes[11].toInt() and 0xFF
            val origSize = ((bytes[12].toInt() and 0xFF) shl 8) or (bytes[13].toInt() and 0xFF)

            val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
            return FecEnvelope(
                blockId = bId,
                indexInBlock = index,
                dataCount = k,
                parityCount = p,
                originalSize = origSize,
                payload = payload
            )
        }
    }
}

/**
 * Result of decoding an incoming FEC envelope.
 */
sealed class FecDecodeResult {
    /** The block is not yet complete or erasable; awaiting further packets. */
    data class Pending(val blockId: Long, val receivedCount: Int, val requiredCount: Int) : FecDecodeResult()

    /** All data packets in the block are cleanly present or successfully reconstructed. */
    data class Ready(
        val blockId: Long,
        val dataPackets: List<ByteArray>,
        val recoveredIndices: List<Int>
    ) : FecDecodeResult()

    /** Block expired before receiving enough packets to reconstruct; frames lost. */
    data class Unrecoverable(
        val blockId: Long,
        val receivedIndices: List<Int>,
        val missingIndices: List<Int>
    ) : FecDecodeResult()
}

/**
 * Real-time operational telemetry for the FEC subsystem.
 */
data class FecTelemetry(
    val activeRatio: FecRatio,
    val totalPacketsEncoded: Long,
    val totalParityGenerated: Long,
    val totalPacketsReceived: Long,
    val totalPacketsRecovered: Long,
    val totalBlocksCompleted: Long,
    val totalBlocksRecovered: Long,
    val totalBlocksUnrecoverable: Long
)

/**
 * Pure Kotlin Multiplatform Forward Error Correction (FEC) Engine for BLE Mesh Intercom.
 *
 * Implements block erasure coding over voice frames:
 * - Single-parity XOR reconstruction ($P_1 = \bigoplus D_i$)
 * - Dual-parity Galois Field $GF(2^8)$ Reed-Solomon reconstruction for arbitrary 2-packet erasure
 * - Zero Java (`java.security.*`) or C/JNI dependencies (Rule 3)
 */
class MeshPacketFecEngine(
    initialRatio: FecRatio = FecRatio.RATIO_4_1,
    val blockTimeoutMs: Long = 500L
) {
    private val mutex = Mutex()

    private val _activeRatio = MutableStateFlow(initialRatio)
    val activeRatio: StateFlow<FecRatio> = _activeRatio.asStateFlow()

    private var currentBlockId: Long = 1L
    private val pendingSendPackets = mutableListOf<ByteArray>()

    // Tracking stats
    private var packetsEncoded: Long = 0L
    private var parityGenerated: Long = 0L
    private var packetsReceived: Long = 0L
    private var packetsRecovered: Long = 0L
    private var blocksCompleted: Long = 0L
    private var blocksRecovered: Long = 0L
    private var blocksUnrecoverable: Long = 0L

    private val _telemetry = MutableStateFlow(
        FecTelemetry(
            activeRatio = initialRatio,
            totalPacketsEncoded = 0L,
            totalParityGenerated = 0L,
            totalPacketsReceived = 0L,
            totalPacketsRecovered = 0L,
            totalBlocksCompleted = 0L,
            totalBlocksRecovered = 0L,
            totalBlocksUnrecoverable = 0L
        )
    )
    val telemetry: StateFlow<FecTelemetry> = _telemetry.asStateFlow()

    // Galois Field GF(2^8) with irreducible polynomial 0x11D (x^8 + x^4 + x^3 + x^2 + 1)
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        initGaloisField()
    }

    private fun initGaloisField() {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfExp[i + 255] = x
            gfLog[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        gfLog[0] = 0 // By convention
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun gfDiv(a: Int, b: Int): Int {
        if (a == 0) return 0
        if (b == 0) throw ArithmeticException("Division by zero in GF(2^8)")
        return gfExp[gfLog[a] - gfLog[b] + 255]
    }

    /**
     * Updates the active FEC ratio dynamically (e.g. governed by composite Q_link score).
     */
    suspend fun setFecRatio(ratio: FecRatio) = mutex.withLock {
        _activeRatio.value = ratio
        publishTelemetry()
    }

    // =========================================================================
    // ENCODER PIPELINE
    // =========================================================================

    /**
     * Ingests an outgoing voice packet.
     * When the buffer reaches [FecRatio.dataCount], emits the full block of envelopes (data + parity).
     *
     * @param voicePacket Compressed or raw voice packet.
     * @return List of [FecEnvelope] packets ready to transmit, or empty if block is still buffering.
     */
    suspend fun ingestOutgoingPacket(voicePacket: ByteArray): List<FecEnvelope> = mutex.withLock {
        val ratio = _activeRatio.value
        if (ratio == FecRatio.DISABLED) {
            packetsEncoded++
            val env = FecEnvelope(
                blockId = currentBlockId++,
                indexInBlock = 0,
                dataCount = 1,
                parityCount = 0,
                originalSize = voicePacket.size,
                payload = voicePacket.copyOf()
            )
            publishTelemetry()
            return@withLock listOf(env)
        }

        pendingSendPackets.add(voicePacket.copyOf())
        if (pendingSendPackets.size >= ratio.dataCount) {
            val envelopes = encodeCurrentBlock(ratio)
            pendingSendPackets.clear()
            publishTelemetry()
            return@withLock envelopes
        }

        return@withLock emptyList()
    }

    /**
     * Flushes any remaining buffered outgoing packets into an immediate partial/padded FEC block.
     */
    suspend fun flushOutgoing(): List<FecEnvelope> = mutex.withLock {
        if (pendingSendPackets.isEmpty()) return@withLock emptyList()
        val ratio = _activeRatio.value
        val envelopes = encodeCurrentBlock(ratio)
        pendingSendPackets.clear()
        publishTelemetry()
        return@withLock envelopes
    }

    private fun encodeCurrentBlock(ratio: FecRatio): List<FecEnvelope> {
        val k = pendingSendPackets.size
        val p = ratio.parityCount
        val bId = currentBlockId++
        packetsEncoded += k

        if (k == 0) return emptyList()

        val maxLen = pendingSendPackets.maxOf { it.size }
        val padded = Array(k) { i ->
            val src = pendingSendPackets[i]
            if (src.size == maxLen) src else src.copyOf(maxLen)
        }

        val result = mutableListOf<FecEnvelope>()

        // 1. Data envelopes
        for (i in 0 until k) {
            result.add(
                FecEnvelope(
                    blockId = bId,
                    indexInBlock = i,
                    dataCount = k,
                    parityCount = p,
                    originalSize = pendingSendPackets[i].size,
                    payload = pendingSendPackets[i]
                )
            )
        }

        // 2. Parity 1 (Bitwise XOR of all K data packets)
        if (p >= 1) {
            val parity1 = ByteArray(maxLen)
            for (byteIdx in 0 until maxLen) {
                var xorAcc = 0
                for (packetIdx in 0 until k) {
                    xorAcc = xorAcc xor (padded[packetIdx][byteIdx].toInt() and 0xFF)
                }
                parity1[byteIdx] = xorAcc.toByte()
            }
            parityGenerated++
            result.add(
                FecEnvelope(
                    blockId = bId,
                    indexInBlock = k,
                    dataCount = k,
                    parityCount = p,
                    originalSize = maxLen,
                    payload = parity1
                )
            )
        }

        // 3. Parity 2 (Galois GF(2^8) weighted multiplication: P2 = sum( alpha^i * D_i ))
        if (p >= 2) {
            val parity2 = ByteArray(maxLen)
            for (byteIdx in 0 until maxLen) {
                var gfAcc = 0
                for (packetIdx in 0 until k) {
                    val factor = gfExp[packetIdx] // alpha^i
                    val dVal = padded[packetIdx][byteIdx].toInt() and 0xFF
                    gfAcc = gfAcc xor gfMul(dVal, factor)
                }
                parity2[byteIdx] = gfAcc.toByte()
            }
            parityGenerated++
            result.add(
                FecEnvelope(
                    blockId = bId,
                    indexInBlock = k + 1,
                    dataCount = k,
                    parityCount = p,
                    originalSize = maxLen,
                    payload = parity2
                )
            )
        }

        return result
    }

    // =========================================================================
    // DECODER PIPELINE
    // =========================================================================

    private class BlockRxTracker(
        val blockId: Long,
        val dataCount: Int,
        val parityCount: Int,
        val creationTimestampMs: Long
    ) {
        val envelopes = mutableMapOf<Int, FecEnvelope>()
        var isEmitted: Boolean = false
    }

    private val rxBlocks = mutableMapOf<Long, BlockRxTracker>()

    /**
     * Ingests an incoming [FecEnvelope] packet from the mesh radio.
     *
     * @param envelope The parsed FEC envelope.
     * @param timestampMs Local reception timestamp.
     * @return [FecDecodeResult.Ready] if complete or reconstructed, [FecDecodeResult.Pending] if awaiting more packets.
     */
    suspend fun ingestIncomingEnvelope(
        envelope: FecEnvelope,
        timestampMs: Long
    ): FecDecodeResult = mutex.withLock {
        packetsReceived++

        val tracker = rxBlocks.getOrPut(envelope.blockId) {
            BlockRxTracker(
                blockId = envelope.blockId,
                dataCount = envelope.dataCount,
                parityCount = envelope.parityCount,
                creationTimestampMs = timestampMs
            )
        }

        if (tracker.isEmitted) {
            // Already processed and emitted
            return@withLock FecDecodeResult.Pending(envelope.blockId, tracker.envelopes.size, tracker.dataCount)
        }

        tracker.envelopes[envelope.indexInBlock] = envelope

        // Check if all K data packets are received directly (no reconstruction needed)
        val hasAllData = (0 until tracker.dataCount).all { it in tracker.envelopes }
        if (hasAllData) {
            tracker.isEmitted = true
            blocksCompleted++
            val orderedData = (0 until tracker.dataCount).map { idx ->
                val env = tracker.envelopes.getValue(idx)
                env.payload.copyOf(env.originalSize)
            }
            publishTelemetry()
            return@withLock FecDecodeResult.Ready(
                blockId = tracker.blockId,
                dataPackets = orderedData,
                recoveredIndices = emptyList()
            )
        }

        // Check if we have enough total packets (data + parity >= K) to reconstruct missing data
        val totalReceived = tracker.envelopes.size
        val k = tracker.dataCount
        val missingDataIndices = (0 until k).filter { it !in tracker.envelopes }

        if (totalReceived >= k && missingDataIndices.isNotEmpty()) {
            val reconstructed = tryReconstructBlock(tracker, missingDataIndices)
            if (reconstructed != null) {
                tracker.isEmitted = true
                blocksCompleted++
                blocksRecovered++
                packetsRecovered += missingDataIndices.size
                publishTelemetry()
                return@withLock reconstructed
            }
        }

        return@withLock FecDecodeResult.Pending(
            blockId = tracker.blockId,
            receivedCount = tracker.envelopes.size,
            requiredCount = tracker.dataCount
        )
    }

    private fun tryReconstructBlock(
        tracker: BlockRxTracker,
        missingDataIndices: List<Int>
    ): FecDecodeResult.Ready? {
        val k = tracker.dataCount
        val maxLen = tracker.envelopes.values.maxOf { it.payload.size }

        // Case 1: Single erasure reconstruction using Parity 1 (XOR)
        if (missingDataIndices.size == 1 && k in tracker.envelopes) {
            val missingIdx = missingDataIndices[0]
            val p1 = tracker.envelopes.getValue(k).payload
            val recovered = ByteArray(maxLen)

            for (byteIdx in 0 until maxLen) {
                var acc = p1[byteIdx].toInt() and 0xFF
                for (i in 0 until k) {
                    if (i != missingIdx) {
                        val d = tracker.envelopes[i]?.payload
                        val bVal = if (d != null && byteIdx < d.size) d[byteIdx].toInt() and 0xFF else 0
                        acc = acc xor bVal
                    }
                }
                recovered[byteIdx] = acc.toByte()
            }

            // Assemble ordered data packets
            val ordered = (0 until k).map { idx ->
                if (idx == missingIdx) {
                    val originalSize = inferOriginalSize(tracker, missingIdx, maxLen)
                    recovered.copyOf(originalSize)
                } else {
                    val env = tracker.envelopes.getValue(idx)
                    env.payload.copyOf(env.originalSize)
                }
            }

            return FecDecodeResult.Ready(
                blockId = tracker.blockId,
                dataPackets = ordered,
                recoveredIndices = listOf(missingIdx)
            )
        }

        // Case 1b: Single erasure reconstruction using Parity 2 (Galois GF(2^8)) when Parity 1 is erased
        if (missingDataIndices.size == 1 && (k + 1) in tracker.envelopes) {
            val missingIdx = missingDataIndices[0]
            val p2 = tracker.envelopes.getValue(k + 1).payload
            val alpha = gfExp[missingIdx]
            val recovered = ByteArray(maxLen)

            for (byteIdx in 0 until maxLen) {
                var acc = p2[byteIdx].toInt() and 0xFF
                for (i in 0 until k) {
                    if (i != missingIdx) {
                        val d = tracker.envelopes[i]?.payload
                        val bVal = if (d != null && byteIdx < d.size) d[byteIdx].toInt() and 0xFF else 0
                        acc = acc xor gfMul(bVal, gfExp[i])
                    }
                }
                recovered[byteIdx] = gfDiv(acc, alpha).toByte()
            }

            val ordered = (0 until k).map { idx ->
                if (idx == missingIdx) {
                    val originalSize = inferOriginalSize(tracker, missingIdx, maxLen)
                    recovered.copyOf(originalSize)
                } else {
                    val env = tracker.envelopes.getValue(idx)
                    env.payload.copyOf(env.originalSize)
                }
            }

            return FecDecodeResult.Ready(
                blockId = tracker.blockId,
                dataPackets = ordered,
                recoveredIndices = listOf(missingIdx)
            )
        }

        // Case 2: Double erasure reconstruction using Parity 1 and Parity 2 (Galois Field GF(2^8))
        if (missingDataIndices.size == 2 && k in tracker.envelopes && (k + 1) in tracker.envelopes) {
            val m1 = missingDataIndices[0]
            val m2 = missingDataIndices[1]
            val alpha1 = gfExp[m1]
            val alpha2 = gfExp[m2]
            val alphaDiff = alpha1 xor alpha2

            val p1 = tracker.envelopes.getValue(k).payload
            val p2 = tracker.envelopes.getValue(k + 1).payload

            val rec1 = ByteArray(maxLen)
            val rec2 = ByteArray(maxLen)

            for (byteIdx in 0 until maxLen) {
                var s1 = p1[byteIdx].toInt() and 0xFF
                var s2 = p2[byteIdx].toInt() and 0xFF

                for (i in 0 until k) {
                    if (i != m1 && i != m2) {
                        val env = tracker.envelopes[i]
                        val d = env?.payload
                        val bVal = if (d != null && byteIdx < d.size) d[byteIdx].toInt() and 0xFF else 0
                        s1 = s1 xor bVal
                        s2 = s2 xor gfMul(bVal, gfExp[i])
                    }
                }

                // D_m2 = (S_2 ^ (alpha_1 * S_1)) / (alpha_1 ^ alpha_2)
                val num = s2 xor gfMul(s1, alpha1)
                val dm2 = gfDiv(num, alphaDiff)
                val dm1 = s1 xor dm2

                rec1[byteIdx] = dm1.toByte()
                rec2[byteIdx] = dm2.toByte()
            }

            val ordered = (0 until k).map { idx ->
                when (idx) {
                    m1 -> {
                        val size = inferOriginalSize(tracker, m1, maxLen)
                        rec1.copyOf(size)
                    }
                    m2 -> {
                        val size = inferOriginalSize(tracker, m2, maxLen)
                        rec2.copyOf(size)
                    }
                    else -> {
                        val env = tracker.envelopes.getValue(idx)
                        env.payload.copyOf(env.originalSize)
                    }
                }
            }

            return FecDecodeResult.Ready(
                blockId = tracker.blockId,
                dataPackets = ordered,
                recoveredIndices = listOf(m1, m2)
            )
        }

        return null
    }

    private fun inferOriginalSize(tracker: BlockRxTracker, missingIdx: Int, maxLen: Int): Int {
        // Voice frames in the same FEC block typically have uniform size
        val knownData = tracker.envelopes.values.firstOrNull { !it.isParity }
        return knownData?.originalSize ?: maxLen
    }

    /**
     * Periodic pruning of timed-out FEC blocks.
     * If a block times out before receiving K packets, emits [FecDecodeResult.Unrecoverable].
     */
    suspend fun pruneTimedOutBlocks(currentTimeMs: Long): List<FecDecodeResult.Unrecoverable> = mutex.withLock {
        val timedOut = rxBlocks.entries.filter { (_, tracker) ->
            !tracker.isEmitted && (currentTimeMs - tracker.creationTimestampMs > blockTimeoutMs)
        }

        val unrecoverableResults = mutableListOf<FecDecodeResult.Unrecoverable>()

        for ((bId, tracker) in timedOut) {
            tracker.isEmitted = true
            blocksUnrecoverable++
            val received = tracker.envelopes.keys.toList()
            val missing = (0 until tracker.dataCount).filter { it !in tracker.envelopes }
            unrecoverableResults.add(
                FecDecodeResult.Unrecoverable(
                    blockId = bId,
                    receivedIndices = received,
                    missingIndices = missing
                )
            )
        }

        // Clean up emitted blocks older than 2x timeout
        rxBlocks.entries.removeAll { (_, tracker) ->
            tracker.isEmitted && (currentTimeMs - tracker.creationTimestampMs > blockTimeoutMs * 2)
        }

        if (unrecoverableResults.isNotEmpty()) {
            publishTelemetry()
        }

        return@withLock unrecoverableResults
    }

    /**
     * Resets internal encoder and decoder states.
     */
    suspend fun reset() = mutex.withLock {
        pendingSendPackets.clear()
        rxBlocks.clear()
        currentBlockId = 1L
        packetsEncoded = 0L
        parityGenerated = 0L
        packetsReceived = 0L
        packetsRecovered = 0L
        blocksCompleted = 0L
        blocksRecovered = 0L
        blocksUnrecoverable = 0L
        publishTelemetry()
    }

    private fun publishTelemetry() {
        _telemetry.value = FecTelemetry(
            activeRatio = _activeRatio.value,
            totalPacketsEncoded = packetsEncoded,
            totalParityGenerated = parityGenerated,
            totalPacketsReceived = packetsReceived,
            totalPacketsRecovered = packetsRecovered,
            totalBlocksCompleted = blocksCompleted,
            totalBlocksRecovered = blocksRecovered,
            totalBlocksUnrecoverable = blocksUnrecoverable
        )
    }
}
