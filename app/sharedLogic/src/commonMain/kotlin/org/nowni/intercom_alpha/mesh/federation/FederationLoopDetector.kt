package org.nowni.intercom_alpha.mesh.federation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Result of evaluating an incoming or transit cross-mesh packet for loops and echoes.
 */
sealed class LoopCheckResult {
    /**
     * Packet is valid, novel, and permitted to be processed or forwarded.
     * @property updatedPathTrace The extended path trace including [localGroupId].
     */
    data class Accept(val updatedPathTrace: List<String>) : LoopCheckResult()

    /**
     * Packet has already traversed the local group; dropped to prevent circular acoustic/relay loop.
     */
    data class DropCircularLoop(val pathTrace: List<String>, val reason: String) : LoopCheckResult()

    /**
     * Packet has exceeded the maximum allowable federation hop count.
     */
    data class DropHopLimitExceeded(val hops: Int, val maxHops: Int, val reason: String) : LoopCheckResult()

    /**
     * Packet was already received within the deduplication window (e.g. from a redundant sibling gateway).
     */
    data class DropDuplicate(val cacheKey: String, val reason: String) : LoopCheckResult()

    /**
     * Packet originated from this exact node; dropped to suppress acoustic self-echo.
     */
    data class DropSelfEcho(val originalSenderId: String, val reason: String) : LoopCheckResult()
}

/**
 * Anti-feedback loop guard and cross-group deduplication engine.
 *
 * Prevents broadcast storms, multi-hop circular relay cycles, and acoustic feedback
 * across complex multi-convoy federation topologies by inspecting monotonic [pathTrace]s
 * and maintaining a sliding-window deduplication cache.
 */
class FederationLoopDetector(
    val localGroupId: String,
    val localPeerId: String,
    val maxHops: Int = DEFAULT_MAX_HOPS,
    val dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
    val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE
) {
    companion object {
        const val DEFAULT_MAX_HOPS = 3
        const val DEFAULT_DEDUP_WINDOW_MS = 5_000L
        const val DEFAULT_MAX_CACHE_SIZE = 2_000
    }

    private val mutex = Mutex()
    private val seenPackets = mutableMapOf<String, Long>()

    /**
     * Evaluates an incoming [CrossMeshAudioPacket] prior to cryptographic decryption.
     *
     * Drops packets that:
     * 1. Originated from [localPeerId] (self-echo suppression).
     * 2. Already traversed [localGroupId] (circular relay loop suppression).
     * 3. Contain internal duplicate group hops in [CrossMeshAudioPacket.pathTrace].
     * 4. Exceed [maxHops] federation hops.
     * 5. Were already received within [dedupWindowMs] (concurrent gateway deduplication).
     */
    suspend fun evaluateInbound(
        packet: CrossMeshAudioPacket,
        currentTimeMs: Long = currentTimeMillis()
    ): LoopCheckResult {
        // 1. Acoustic Self-Echo Check
        if (packet.originalSenderId == localPeerId) {
            return LoopCheckResult.DropSelfEcho(
                originalSenderId = packet.originalSenderId,
                reason = "Packet originated from local peer ($localPeerId); dropped to prevent acoustic self-echo"
            )
        }

        // 2. Circular Relay Loop Check
        if (packet.pathTrace.contains(localGroupId)) {
            return LoopCheckResult.DropCircularLoop(
                pathTrace = packet.pathTrace,
                reason = "Circular relay detected: packet pathTrace ${packet.pathTrace} already includes local group $localGroupId"
            )
        }

        // 3. Duplicate Hops in Path Trace
        if (packet.pathTrace.size != packet.pathTrace.distinct().size) {
            return LoopCheckResult.DropCircularLoop(
                pathTrace = packet.pathTrace,
                reason = "Corrupted or cyclic path trace detected: ${packet.pathTrace}"
            )
        }

        // 4. Maximum Hop Limit Check
        if (packet.pathTrace.size >= maxHops) {
            return LoopCheckResult.DropHopLimitExceeded(
                hops = packet.pathTrace.size,
                maxHops = maxHops,
                reason = "Federation hop limit ($maxHops) exceeded: packet traversed ${packet.pathTrace.size} hops"
            )
        }

        // 5. Sliding-Window Deduplication Check
        val cacheKey = buildCacheKey(packet)
        val isNovel = mutex.withLock {
            purgeExpired(currentTimeMs)
            if (seenPackets.containsKey(cacheKey)) {
                false
            } else {
                if (seenPackets.size >= maxCacheSize) {
                    val oldestKey = seenPackets.minByOrNull { it.value }?.key
                    if (oldestKey != null) {
                        seenPackets.remove(oldestKey)
                    }
                }
                seenPackets[cacheKey] = currentTimeMs
                true
            }
        }

        if (!isNovel) {
            return LoopCheckResult.DropDuplicate(
                cacheKey = cacheKey,
                reason = "Duplicate cross-mesh packet already processed within ${dedupWindowMs}ms window"
            )
        }

        val updatedTrace = packet.pathTrace + localGroupId
        return LoopCheckResult.Accept(updatedPathTrace = updatedTrace)
    }

    /**
     * Constructs the outbound path trace for locally generated cross-mesh packets.
     */
    fun createOutboundPathTrace(): List<String> {
        return listOf(localGroupId)
    }

    /**
     * Extends an existing path trace for transit forwarding.
     */
    fun extendPathTrace(currentTrace: List<String>): List<String> {
        return if (currentTrace.contains(localGroupId)) {
            currentTrace
        } else {
            currentTrace + localGroupId
        }
    }

    /**
     * Clears all cached packet identifiers.
     */
    suspend fun clear() {
        mutex.withLock {
            seenPackets.clear()
        }
    }

    /**
     * Returns the number of currently tracked packet keys in the deduplication cache.
     */
    suspend fun cacheSize(): Int = mutex.withLock { seenPackets.size }

    /**
     * Generates a composite deduplication key for a cross-mesh packet.
     */
    fun buildCacheKey(packet: CrossMeshAudioPacket): String {
        return "${packet.federationId}:${packet.sourceGroupId}:${packet.originalSenderId}:${packet.sequence}"
    }

    private fun purgeExpired(now: Long) {
        val cutoff = now - dedupWindowMs
        val iterator = seenPackets.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < cutoff) {
                iterator.remove()
            }
        }
    }
}
