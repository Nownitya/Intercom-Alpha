package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nowni.intercom_alpha.currentTimeMillis

/**
 * Sliding-window packet deduplication cache.
 *
 * Prevents broadcast storms in multi-hop mesh flooding by tracking recently seen
 * packets using composite keys and dropping duplicates within a configurable time window.
 */
class PacketDeduplicator(
    private val windowMs: Long = 5_000L,
    private val maxCacheSize: Int = 1_000
) {
    private val mutex = Mutex()
    private val seenPackets = mutableMapOf<String, Long>()

    /**
     * Determines whether the given [packet] should be processed and relayed.
     *
     * @return `true` if this packet is novel (not seen within [windowMs]), `false` if it is a duplicate.
     */
    suspend fun shouldProcess(packet: MeshPacket, now: Long = currentTimeMillis()): Boolean {
        val key = extractKey(packet) ?: return true // If no key can be derived, do not drop

        return mutex.withLock {
            purgeExpired(now)

            if (seenPackets.containsKey(key)) {
                false
            } else {
                if (seenPackets.size >= maxCacheSize) {
                    val oldestKey = seenPackets.minByOrNull { it.value }?.key
                    if (oldestKey != null) {
                        seenPackets.remove(oldestKey)
                    }
                }
                seenPackets[key] = now
                true
            }
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
     * Returns the current number of cached packet keys.
     */
    suspend fun size(): Int = mutex.withLock { seenPackets.size }

    private fun purgeExpired(now: Long) {
        val keysToRemove = mutableListOf<String>()
        for ((key, timestamp) in seenPackets) {
            if (now - timestamp > windowMs) {
                keysToRemove.add(key)
            }
        }
        for (key in keysToRemove) {
            seenPackets.remove(key)
        }
    }

    private fun extractKey(packet: MeshPacket): String? {
        return when (packet) {
            is MeshPacket.Audio -> "audio:${packet.senderId}:${packet.sequence}"
            is MeshPacket.Control -> "ctrl:${packet.senderId}:${packet.type}:${packet.payload.contentHashCode()}"
            is MeshPacket.Discovery -> "disc:${packet.senderId}:${packet.groupId}"
            is MeshPacket.Relay -> {
                val innerKey = extractKey(packet.packet)
                "relay:${packet.originalSenderId}:$innerKey"
            }
        }
    }
}
