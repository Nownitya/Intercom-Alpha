package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Result of polling a frame from the AdaptiveJitterBuffer at a playout deadline.
 */
sealed class PlayoutFrame {
    /** Valid audio frame ready to be decoded and played */
    data class Available(
        val sequence: Long,
        val timestamp: Long,
        val data: ByteArray,
        val profile: AudioProfile
    ) : PlayoutFrame() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Available) return false
            if (sequence != other.sequence) return false
            if (timestamp != other.timestamp) return false
            if (!data.contentEquals(other.data)) return false
            return profile == other.profile
        }

        override fun hashCode(): Int {
            var result = sequence.hashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + profile.hashCode()
            return result
        }
    }

    /** Playout deadline reached but expected sequence is missing; triggers PLC interpolation */
    data class Concealment(val expectedSequence: Long) : PlayoutFrame()

    /** Buffer is building up initial target delay */
    object Buffering : PlayoutFrame()

    /** Buffer has no frames queued */
    object Empty : PlayoutFrame()
}

/**
 * Adaptive Jitter Buffer implementing dynamic playout delay adjustment
 * and RFC 3550 inter-arrival jitter variance estimation.
 *
 * Guarantees smooth, continuous audio playout across erratic BLE mesh connection intervals
 * and multi-hop relay forwarding hops.
 */
class AdaptiveJitterBuffer(
    val minDelayMs: Long = 40L,
    val maxDelayMs: Long = 150L,
    val baseDelayMs: Long = 50L,
    val frameDurationMs: Long = 20L,
    val maxCapacity: Int = 100
) {
    private val mutex = Mutex()

    // Internal queued frame representation sorted by sequence
    private val buffer = mutableListOf<MeshPacket.Audio>()

    // Playout state
    private var expectedSequence: Long = -1L
    private var playoutStartTime: Long = -1L
    private var lastPlayoutTime: Long = -1L

    // RFC 3550 Jitter Estimation State
    private var lastTransitTimeDiff: Double = 0.0
    private var lastArrivalLocalTime: Long = -1L
    private var lastPacketSenderTime: Long = -1L
    private var runningJitterMs: Double = 0.0

    val currentJitterMs: Double
        get() = runningJitterMs

    var currentTargetDelayMs: Long = baseDelayMs
        private set

    /**
     * Pushes an incoming audio packet into the jitter buffer.
     * Computes inter-arrival jitter variance and updates target playout delay.
     * Drops late arrivals past the current expected playout sequence.
     */
    suspend fun push(packet: MeshPacket.Audio, arrivalTimeMs: Long): Boolean = mutex.withLock {
        // 1. Calculate RFC 3550 inter-arrival jitter
        if (lastArrivalLocalTime > 0 && lastPacketSenderTime > 0) {
            val senderDelta = packet.timestamp - lastPacketSenderTime
            val arrivalDelta = arrivalTimeMs - lastArrivalLocalTime
            val transitDiff = abs((arrivalDelta - senderDelta).toDouble())

            // Running average: J = J + (|D| - J) / 16
            runningJitterMs += (transitDiff - runningJitterMs) / 16.0

            // Adapt target delay dynamically: baseDelay + 3 * Jitter
            val calculatedTarget = (baseDelayMs + (3.0 * runningJitterMs).roundToLong())
            currentTargetDelayMs = calculatedTarget.coerceIn(minDelayMs, maxDelayMs)
        }

        lastArrivalLocalTime = arrivalTimeMs
        lastPacketSenderTime = packet.timestamp

        // 2. Reject stale packets arriving past playout deadline
        if (expectedSequence >= 0 && packet.sequence < expectedSequence) {
            return@withLock false
        }

        // 3. Reject duplicate packets already in buffer
        if (buffer.any { it.sequence == packet.sequence }) {
            return@withLock false
        }

        // 4. Insert in ascending sequence order
        val insertIndex = buffer.indexOfFirst { it.sequence > packet.sequence }
        if (insertIndex >= 0) {
            buffer.add(insertIndex, packet)
        } else {
            buffer.add(packet)
        }

        // 5. Overflow eviction: prevent unbounded queue growth
        if (buffer.size > maxCapacity) {
            buffer.removeAt(0)
        }

        // 6. First packet initialization
        if (expectedSequence < 0) {
            expectedSequence = packet.sequence
            playoutStartTime = arrivalTimeMs + currentTargetDelayMs
            lastPlayoutTime = playoutStartTime - frameDurationMs
        }

        return@withLock true
    }

    /**
     * Polls the next frame to be played out at the given local system time.
     * Returns Available, Concealment, Buffering, or Empty.
     */
    suspend fun pull(currentTimeMs: Long): PlayoutFrame = mutex.withLock {
        if (buffer.isEmpty() && expectedSequence < 0) {
            return@withLock PlayoutFrame.Empty
        }

        // Check if initial buffering target delay has elapsed
        if (currentTimeMs < playoutStartTime) {
            return@withLock PlayoutFrame.Buffering
        }

        // Check if buffer is empty but a session is active
        if (buffer.isEmpty()) {
            val missedSeq = expectedSequence++
            lastPlayoutTime = currentTimeMs
            return@withLock PlayoutFrame.Concealment(missedSeq)
        }

        val firstInQueue = buffer.first()

        // Case 1: First packet matches expected sequence
        if (firstInQueue.sequence == expectedSequence) {
            buffer.removeAt(0)
            expectedSequence++
            lastPlayoutTime = currentTimeMs
            return@withLock PlayoutFrame.Available(
                sequence = firstInQueue.sequence,
                timestamp = firstInQueue.timestamp,
                data = firstInQueue.data,
                profile = firstInQueue.profile
            )
        }

        // Case 2: Packet in queue has an older sequence than expected (stale leftover)
        if (firstInQueue.sequence < expectedSequence) {
            buffer.removeAt(0)
            // Immediately retry next packet in queue
            return@withLock if (buffer.isNotEmpty() && buffer.first().sequence == expectedSequence) {
                val next = buffer.removeAt(0)
                expectedSequence++
                lastPlayoutTime = currentTimeMs
                PlayoutFrame.Available(next.sequence, next.timestamp, next.data, next.profile)
            } else {
                val missedSeq = expectedSequence++
                lastPlayoutTime = currentTimeMs
                PlayoutFrame.Concealment(missedSeq)
            }
        }

        // Case 3: First packet in queue has sequence > expectedSequence (missing gap!)
        // If the gap is massive (> 50 frames ~ 1 sec), resync to avoid playing seconds of silence
        if (firstInQueue.sequence - expectedSequence > 50) {
            expectedSequence = firstInQueue.sequence
            val packet = buffer.removeAt(0)
            expectedSequence++
            lastPlayoutTime = currentTimeMs
            return@withLock PlayoutFrame.Available(packet.sequence, packet.timestamp, packet.data, packet.profile)
        }

        // Normal sequence gap: trigger PLC concealment for the missing frame
        val missingSeq = expectedSequence++
        lastPlayoutTime = currentTimeMs
        return@withLock PlayoutFrame.Concealment(missingSeq)
    }

    /**
     * Resets the jitter buffer state between talkspurts.
     */
    suspend fun reset() = mutex.withLock {
        buffer.clear()
        expectedSequence = -1L
        playoutStartTime = -1L
        lastPlayoutTime = -1L
        lastArrivalLocalTime = -1L
        lastPacketSenderTime = -1L
        runningJitterMs = 0.0
        currentTargetDelayMs = baseDelayMs
    }

    /**
     * Returns the current number of frames queued in the buffer.
     */
    suspend fun size(): Int = mutex.withLock {
        buffer.size
    }
}
