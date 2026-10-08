package org.nowni.intercom_alpha.mesh.routing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Pure KMP Link Quality & Expected Transmission Count (ETX) Calculator.
 *
 * Dynamically computes link cost based on packet delivery ratio (PDR) and RSSI.
 * Detects consecutive transmission failures to trigger route self-healing (RERR).
 *
 * Rule 2 & 3: 100% pure Kotlin Multiplatform with zero Java or platform dependencies.
 */

@Serializable
data class PeerLinkStats(
    val peerId: String,
    val totalAttempts: Long = 0L,
    val successfulAcks: Long = 0L,
    val consecutiveFailures: Int = 0,
    val lastRssiDbm: Int = -75,
    val smoothedCost: Double = 1.0,
    val lastUpdatedMs: Long = 0L
)

data class LinkHealthUpdate(
    val isBroken: Boolean,
    val cost: Double,
    val consecutiveFailures: Int
)

class LinkMetricCalculator(
    val maxConsecutiveFailures: Int = 3,
    val alphaEma: Double = 0.25,
    val poorRssiThreshold: Int = -90
) {
    private val mutex = Mutex()
    private val statsMap = mutableMapOf<String, PeerLinkStats>()

    suspend fun recordTxAttempt(
        peerId: String,
        success: Boolean,
        rssiDbm: Int? = null,
        currentTimeMs: Long
    ): LinkHealthUpdate = mutex.withLock {
        val current = statsMap[peerId] ?: PeerLinkStats(peerId = peerId, lastUpdatedMs = currentTimeMs)

        val totalAttempts = current.totalAttempts + 1
        val successfulAcks = if (success) current.successfulAcks + 1 else current.successfulAcks
        val consecutiveFailures = if (success) 0 else current.consecutiveFailures + 1
        val effectiveRssi = rssiDbm ?: current.lastRssiDbm

        // Base ETX = 1.0 / PDR (bounded between 1.0 and 10.0)
        val pdr = if (totalAttempts > 0) {
            successfulAcks.toDouble() / totalAttempts.toDouble()
        } else {
            1.0
        }
        val rawEtx = (1.0 / pdr.coerceIn(0.1, 1.0)).coerceIn(1.0, 10.0)

        // RSSI penalty: penalize links worse than poorRssiThreshold
        val rssiPenalty = when {
            effectiveRssi >= -70 -> 0.0
            effectiveRssi >= -82 -> 0.3
            effectiveRssi >= -90 -> 1.0
            else -> 2.5
        }

        val instantaneousCost = rawEtx + rssiPenalty

        // Exponential Moving Average
        val smoothed = if (current.totalAttempts == 0L) {
            instantaneousCost
        } else {
            ((1.0 - alphaEma) * current.smoothedCost) + (alphaEma * instantaneousCost)
        }

        val isBroken = consecutiveFailures >= maxConsecutiveFailures

        val updated = PeerLinkStats(
            peerId = peerId,
            totalAttempts = totalAttempts,
            successfulAcks = successfulAcks,
            consecutiveFailures = consecutiveFailures,
            lastRssiDbm = effectiveRssi,
            smoothedCost = smoothed,
            lastUpdatedMs = currentTimeMs
        )
        statsMap[peerId] = updated

        LinkHealthUpdate(
            isBroken = isBroken,
            cost = smoothed,
            consecutiveFailures = consecutiveFailures
        )
    }

    suspend fun getLinkCost(peerId: String): Double = mutex.withLock {
        statsMap[peerId]?.smoothedCost ?: 1.0
    }

    suspend fun isLinkBroken(peerId: String): Boolean = mutex.withLock {
        (statsMap[peerId]?.consecutiveFailures ?: 0) >= maxConsecutiveFailures
    }

    suspend fun getStats(peerId: String): PeerLinkStats? = mutex.withLock {
        statsMap[peerId]
    }

    suspend fun getAllStats(): Map<String, PeerLinkStats> = mutex.withLock {
        statsMap.toMap()
    }

    suspend fun resetPeer(peerId: String) = mutex.withLock {
        statsMap.remove(peerId)
    }

    suspend fun clear() = mutex.withLock {
        statsMap.clear()
    }
}
