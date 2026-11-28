package org.nowni.intercom_alpha.mesh

/**
 * Configuration options for time-diversity block interleaving.
 *
 * @property depthM Number of consecutive packets in an interleaving matrix block (typically 2 or 3).
 * @property framesPerPacketN Number of frames per packet (typically 2 or 3).
 * @property maxBlockAssemblyTimeoutMs Maximum hold time before emitting incomplete block frames at receiver.
 */
data class InterleaverConfig(
    val depthM: Int = DEFAULT_DEPTH_M,
    val framesPerPacketN: Int = DEFAULT_FRAMES_PER_PACKET_N,
    val maxBlockAssemblyTimeoutMs: Long = DEFAULT_ASSEMBLY_TIMEOUT_MS
) {
    val totalBlockFrames: Int get() = depthM * framesPerPacketN

    companion object {
        const val DEFAULT_DEPTH_M = 3
        const val DEFAULT_FRAMES_PER_PACKET_N = 3
        const val DEFAULT_ASSEMBLY_TIMEOUT_MS = 80L
    }
}

/**
 * Interleaved packet ready for transmission over BLE mesh.
 */
data class InterleavedPacket(
    val blockId: Int,
    val packetIndexInBlock: Int, // 0 until totalPacketsInBlock
    val totalPacketsInBlock: Int,
    val totalFramesInBlock: Int,
    val isEmergency: Boolean,
    val frames: List<SubFrame>
)

/**
 * Result of de-interleaving at receiver.
 */
data class DeinterleavedFrame(
    val frameIndex: Int,
    val isMissing: Boolean,
    val subFrame: SubFrame?
)

/**
 * Time-Diversity Matrix Block Interleaver & Playout De-jittering Unpacker.
 *
 * Disperses consecutive voice frames across M distinct BLE packets so that physical
 * radio burst dropouts (which wipe out consecutive packets) result in isolated, non-adjacent
 * frame erasures at the receiver. This allows FEC and PLC to recover audio with near-100% fidelity.
 */
class BleFrameInterleaver(
    val config: InterleaverConfig = InterleaverConfig()
) {
    private var currentBlockId = 0
    private val inputBuffer = mutableListOf<SubFrame>()

    // Receiver state: Map of blockId to ReceiverBlock
    private val receiverBlocks = mutableMapOf<Int, ReceiverBlock>()

    private class ReceiverBlock(
        val blockId: Int,
        val totalPacketsInBlock: Int,
        val totalFramesInBlock: Int,
        val creationTimeMs: Long
    ) {
        val columns = arrayOfNulls<List<SubFrame>>(totalPacketsInBlock)
        var receivedCount = 0
    }

    /**
     * Ingests a sub-frame into the transmitter interleaver.
     * If an EMERGENCY_SOS frame arrives, any buffered frames are interleaved and flushed immediately,
     * and the emergency frame is returned as a 1-packet un-interleaved priority packet.
     *
     * @return List of [InterleavedPacket]s emitted if an interleaving block completed.
     */
    fun ingestFrame(frame: SubFrame): List<InterleavedPacket> {
        val emitted = mutableListOf<InterleavedPacket>()

        if (frame.type == SubFrameType.EMERGENCY_SOS) {
            // Flush partial block if any
            if (inputBuffer.isNotEmpty()) {
                emitted.addAll(flush())
            }
            // Emit emergency immediately with index 0, total 1
            emitted.add(
                InterleavedPacket(
                    blockId = currentBlockId++,
                    packetIndexInBlock = 0,
                    totalPacketsInBlock = 1,
                    totalFramesInBlock = 1,
                    isEmergency = true,
                    frames = listOf(frame)
                )
            )
            return emitted
        }

        inputBuffer.add(frame)

        if (inputBuffer.size >= config.totalBlockFrames) {
            emitted.addAll(interleaveAndEmit())
        }

        return emitted
    }

    /**
     * Forces emission of any remaining buffered frames as a partial or padded block.
     */
    fun flush(): List<InterleavedPacket> {
        if (inputBuffer.isEmpty()) return emptyList()
        return interleaveAndEmit()
    }

    private fun interleaveAndEmit(): List<InterleavedPacket> {
        val blockId = currentBlockId++
        val totalFrames = inputBuffer.size
        val m = config.depthM
        val n = config.framesPerPacketN
        val emitted = mutableListOf<InterleavedPacket>()

        // Fill matrix of size M rows x N cols
        val matrix = Array(m) { arrayOfNulls<SubFrame>(n) }
        var inputIdx = 0

        for (r in 0 until m) {
            for (c in 0 until n) {
                if (inputIdx < totalFrames) {
                    matrix[r][c] = inputBuffer[inputIdx++]
                }
            }
        }
        inputBuffer.clear()

        // Count how many non-empty columns exist
        var nonEmptyCols = 0
        for (c in 0 until n) {
            var colHasData = false
            for (r in 0 until m) {
                if (matrix[r][c] != null) colHasData = true
            }
            if (colHasData) nonEmptyCols++
        }

        // Transmit each column
        for (c in 0 until nonEmptyCols) {
            val colFrames = mutableListOf<SubFrame>()
            for (r in 0 until m) {
                matrix[r][c]?.let { colFrames.add(it) }
            }
            if (colFrames.isNotEmpty()) {
                emitted.add(
                    InterleavedPacket(
                        blockId = blockId,
                        packetIndexInBlock = c,
                        totalPacketsInBlock = nonEmptyCols,
                        totalFramesInBlock = totalFrames,
                        isEmergency = false,
                        frames = colFrames
                    )
                )
            }
        }

        return emitted
    }

    // ================= RECEIVER DE-INTERLEAVING & RESEQUENCING =================

    /**
     * Receives an incoming [InterleavedPacket], placing it in the receiver block matrix.
     * If the block is complete, immediately reconstructs and returns all frames in sequential playout order.
     */
    fun receivePacket(packet: InterleavedPacket, nowMs: Long = 0L): List<DeinterleavedFrame> {
        // Emergency packets bypass matrix reassembly and play out instantly
        if (packet.isEmergency) {
            return packet.frames.mapIndexed { idx, frame ->
                DeinterleavedFrame(frameIndex = idx, isMissing = false, subFrame = frame)
            }
        }

        val block = receiverBlocks.getOrPut(packet.blockId) {
            ReceiverBlock(
                blockId = packet.blockId,
                totalPacketsInBlock = packet.totalPacketsInBlock,
                totalFramesInBlock = packet.totalFramesInBlock,
                creationTimeMs = nowMs
            )
        }

        if (packet.packetIndexInBlock in 0 until block.totalPacketsInBlock) {
            if (block.columns[packet.packetIndexInBlock] == null) {
                block.columns[packet.packetIndexInBlock] = packet.frames
                block.receivedCount++
            }
        }

        // If all columns received, de-interleave block immediately
        return if (block.receivedCount >= block.totalPacketsInBlock) {
            receiverBlocks.remove(packet.blockId)
            deinterleaveBlock(block)
        } else {
            emptyList()
        }
    }

    /**
     * Checks for blocks that exceeded [InterleaverConfig.maxBlockAssemblyTimeoutMs] and flushes them,
     * emitting frames with `isMissing = true` for dropped column elements.
     */
    fun checkTimeouts(nowMs: Long): List<DeinterleavedFrame> {
        val timedOutBlockIds = mutableListOf<Int>()
        for ((id, block) in receiverBlocks) {
            if (nowMs - block.creationTimeMs >= config.maxBlockAssemblyTimeoutMs) {
                timedOutBlockIds.add(id)
            }
        }

        val result = mutableListOf<DeinterleavedFrame>()
        for (id in timedOutBlockIds) {
            receiverBlocks.remove(id)?.let { block ->
                result.addAll(deinterleaveBlock(block))
            }
        }
        return result
    }

    private fun deinterleaveBlock(block: ReceiverBlock): List<DeinterleavedFrame> {
        val totalCols = block.totalPacketsInBlock
        val totalFrames = block.totalFramesInBlock
        val maxRows = (totalFrames + totalCols - 1) / totalCols
        val output = mutableListOf<DeinterleavedFrame>()
        var globalIndex = 0

        // In reverse of interleaving: original order is Row 0 (all cols), Row 1 (all cols), ...
        for (r in 0 until maxRows) {
            for (c in 0 until totalCols) {
                if (globalIndex >= totalFrames) break

                val col = block.columns[c]
                if (col != null && r < col.size) {
                    output.add(
                        DeinterleavedFrame(
                            frameIndex = globalIndex++,
                            isMissing = false,
                            subFrame = col[r]
                        )
                    )
                } else {
                    // Frame was in a lost packet column
                    output.add(
                        DeinterleavedFrame(
                            frameIndex = globalIndex++,
                            isMissing = true,
                            subFrame = null
                        )
                    )
                }
            }
        }

        return output
    }

    fun clear() {
        inputBuffer.clear()
        receiverBlocks.clear()
        currentBlockId = 0
    }
}
