package org.nowni.intercom_alpha.mesh.routing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure KMP thread-safe routing table arbiter.
 * Manages loop-free active route entries, destination sequence numbers, precursor lists,
 * and link expiration.
 *
 * Rule 2 & 3: 100% pure Kotlin Multiplatform with zero Java or platform dependencies.
 */
class RoutingTable {
    private val mutex = Mutex()
    private val routes = mutableMapOf<String, RouteEntry>()

    suspend fun updateRoute(
        destinationId: String,
        nextHopId: String,
        hopCount: Int,
        destSeqNum: Long,
        metricCost: Double,
        currentTimeMs: Long,
        lifetimeMs: Long = 30_000L,
        precursors: Set<String> = emptySet()
    ): Boolean = mutex.withLock {
        val existing = routes[destinationId]

        val shouldUpdate = when {
            existing == null -> true
            destSeqNum > existing.destSeqNum -> true
            destSeqNum == existing.destSeqNum && metricCost < existing.metricCost -> true
            destSeqNum == existing.destSeqNum && existing.isExpired(currentTimeMs) -> true
            destSeqNum == existing.destSeqNum && existing.status != RouteStatus.ACTIVE -> true
            else -> false
        }

        if (shouldUpdate) {
            val mergedPrecursors = if (existing != null && existing.nextHopId == nextHopId) {
                existing.precursorNodes + precursors
            } else {
                precursors
            }

            routes[destinationId] = RouteEntry(
                destinationId = destinationId,
                nextHopId = nextHopId,
                hopCount = hopCount,
                destSeqNum = destSeqNum,
                metricCost = metricCost,
                lastActiveTimeMs = currentTimeMs,
                lifetimeMs = lifetimeMs,
                precursorNodes = mergedPrecursors,
                status = RouteStatus.ACTIVE
            )
            true
        } else {
            false
        }
    }

    suspend fun getRoute(destinationId: String, currentTimeMs: Long): RouteEntry? = mutex.withLock {
        val entry = routes[destinationId] ?: return@withLock null
        if (entry.status == RouteStatus.ACTIVE && entry.isFresh(currentTimeMs)) {
            entry
        } else {
            null
        }
    }

    suspend fun touchRoute(destinationId: String, currentTimeMs: Long): Boolean = mutex.withLock {
        val entry = routes[destinationId] ?: return@withLock false
        if (entry.status == RouteStatus.ACTIVE) {
            routes[destinationId] = entry.copy(lastActiveTimeMs = currentTimeMs)
            true
        } else {
            false
        }
    }

    suspend fun addPrecursor(destinationId: String, precursorId: String): Unit = mutex.withLock {
        val entry = routes[destinationId] ?: return@withLock
        routes[destinationId] = entry.copy(precursorNodes = entry.precursorNodes + precursorId)
    }

    suspend fun getPrecursors(destinationId: String): Set<String> = mutex.withLock {
        routes[destinationId]?.precursorNodes ?: emptySet()
    }

    suspend fun invalidateRoute(destinationId: String, incrementSeqNum: Boolean = true): RouteEntry? = mutex.withLock {
        val entry = routes[destinationId] ?: return@withLock null
        val updatedSeqNum = if (incrementSeqNum) entry.destSeqNum + 1 else entry.destSeqNum
        val invalidated = entry.copy(
            destSeqNum = updatedSeqNum,
            status = RouteStatus.INVALID
        )
        routes[destinationId] = invalidated
        invalidated
    }

    suspend fun invalidateRoutesViaNextHop(nextHopId: String): List<RouteEntry> = mutex.withLock {
        val affected = mutableListOf<RouteEntry>()
        for ((destId, entry) in routes) {
            if (entry.nextHopId == nextHopId && entry.status == RouteStatus.ACTIVE) {
                val updated = entry.copy(
                    destSeqNum = entry.destSeqNum + 1,
                    status = RouteStatus.INVALID
                )
                routes[destId] = updated
                affected.add(updated)
            }
        }
        affected
    }

    suspend fun pruneExpired(currentTimeMs: Long): List<String> = mutex.withLock {
        val expired = mutableListOf<String>()
        for ((destId, entry) in routes) {
            if (entry.status == RouteStatus.ACTIVE && entry.isExpired(currentTimeMs)) {
                routes[destId] = entry.copy(status = RouteStatus.EXPIRED)
                expired.add(destId)
            }
        }
        expired
    }

    suspend fun getAllActiveRoutes(currentTimeMs: Long): List<RouteEntry> = mutex.withLock {
        routes.values.filter { it.status == RouteStatus.ACTIVE && it.isFresh(currentTimeMs) }
    }

    suspend fun getAllRoutes(): List<RouteEntry> = mutex.withLock {
        routes.values.toList()
    }

    suspend fun clear(): Unit = mutex.withLock {
        routes.clear()
    }
}
