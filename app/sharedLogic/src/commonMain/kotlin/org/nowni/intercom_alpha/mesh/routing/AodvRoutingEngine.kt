package org.nowni.intercom_alpha.mesh.routing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * Pure KMP AODV-Lite Directed Routing Engine.
 *
 * Implements RFC 3561 on-demand route discovery adapted for energy-efficient BLE mesh networks.
 * Selectively discovers, establishes, and maintains loop-free unicast/multicast paths,
 * dramatically mitigating RF packet collisions and airtime contention compared to blind flood relay.
 *
 * Rule 2 & 3: 100% pure Kotlin Multiplatform with zero Java or platform dependencies.
 */

@Serializable
data class AodvConfig(
    val routeLifetimeMs: Long = 30_000L,
    val rreqTimeoutMs: Long = 1_500L,
    val rreqDefaultTtl: Int = 4,
    val rreqMaxRetries: Int = 2,
    val minRssiThreshold: Int = -90
)

sealed class RreqResult {
    data class SendRrep(val payload: RouteReplyPayload, val targetNeighbor: String) : RreqResult()
    data class ForwardRreq(val payload: RouteRequestPayload) : RreqResult()
    object Dropped : RreqResult()
}

sealed class RrepResult {
    data class RouteEstablished(val destinationId: String, val route: RouteEntry) : RrepResult()
    data class ForwardRrep(val payload: RouteReplyPayload, val targetNeighbor: String) : RrepResult()
    object Dropped : RrepResult()
}

sealed class RerrResult {
    data class PropagateRerr(val payload: RouteErrorPayload, val precursors: Set<String>) : RerrResult()
    object Ignored : RerrResult()
}

class AodvRoutingEngine(
    val localNodeId: String,
    val config: AodvConfig = AodvConfig(),
    val routingTable: RoutingTable = RoutingTable()
) {
    private val mutex = Mutex()
    private var localSeqNum: Long = 1L
    private var rreqIdSequence: Long = 1L

    private val recentRreqs = mutableMapOf<String, Long>()
    private val pendingDiscoveries = mutableMapOf<String, Long>()

    private var totalDirectedPackets: Long = 0L
    private var totalFloodedPackets: Long = 0L
    private var rreqSentCount: Long = 0L
    private var rrepSentCount: Long = 0L
    private var rerrSentCount: Long = 0L

    companion object {
        fun calculateHopCost(rssiDbm: Int): Double {
            return when {
                rssiDbm >= -70 -> 1.0       // Excellent line of sight link
                rssiDbm >= -82 -> 1.5       // Good stable link
                rssiDbm >= -90 -> 2.5       // Moderate link with potential packet retry
                else -> 4.5                 // Weak edge link
            }
        }
    }

    suspend fun findRoute(destinationId: String, currentTimeMs: Long): RouteEntry? {
        val entry = routingTable.getRoute(destinationId, currentTimeMs)
        if (entry != null) {
            routingTable.touchRoute(destinationId, currentTimeMs)
        }
        return entry
    }

    suspend fun initiateRouteDiscovery(destinationId: String, currentTimeMs: Long): RouteRequestPayload = mutex.withLock {
        localSeqNum += 1
        val rreqId = rreqIdSequence++
        rreqSentCount += 1

        val knownRoute = routingTable.getAllRoutes().find { it.destinationId == destinationId }
        val targetDestSeqNum = knownRoute?.destSeqNum ?: 0L

        pendingDiscoveries[destinationId] = currentTimeMs
        recentRreqs["$localNodeId:$rreqId"] = currentTimeMs

        RouteRequestPayload(
            rreqId = rreqId,
            sourceId = localNodeId,
            sourceSeqNum = localSeqNum,
            destinationId = destinationId,
            destSeqNum = targetDestSeqNum,
            hopCount = 0,
            metricCost = 0.0,
            ttl = config.rreqDefaultTtl
        )
    }

    suspend fun handleIncomingRreq(
        rreq: RouteRequestPayload,
        fromNeighborId: String,
        linkRssi: Int,
        currentTimeMs: Long
    ): RreqResult = mutex.withLock {
        val rreqKey = "${rreq.sourceId}:${rreq.rreqId}"
        val lastSeen = recentRreqs[rreqKey]
        if (lastSeen != null && (currentTimeMs - lastSeen) < 5_000L) {
            return@withLock RreqResult.Dropped
        }
        recentRreqs[rreqKey] = currentTimeMs

        // Update reverse path to source
        val hopCost = calculateHopCost(linkRssi)
        val reverseMetric = rreq.metricCost + hopCost
        routingTable.updateRoute(
            destinationId = rreq.sourceId,
            nextHopId = fromNeighborId,
            hopCount = rreq.hopCount + 1,
            destSeqNum = rreq.sourceSeqNum,
            metricCost = reverseMetric,
            currentTimeMs = currentTimeMs,
            lifetimeMs = config.routeLifetimeMs
        )

        // Case 1: We are the destination
        if (rreq.destinationId == localNodeId) {
            if (localSeqNum < rreq.destSeqNum) {
                localSeqNum = rreq.destSeqNum
            } else {
                localSeqNum += 1
            }
            rrepSentCount += 1

            val rrep = RouteReplyPayload(
                sourceId = rreq.sourceId,
                destinationId = localNodeId,
                destSeqNum = localSeqNum,
                hopCount = 0,
                metricCost = 0.0,
                lifetimeMs = config.routeLifetimeMs,
                targetNextHop = fromNeighborId
            )
            return@withLock RreqResult.SendRrep(rrep, fromNeighborId)
        }

        // Case 2: We have an active, fresh route to the destination with sufficient sequence number
        val activeRoute = routingTable.getRoute(rreq.destinationId, currentTimeMs)
        if (activeRoute != null && activeRoute.destSeqNum >= rreq.destSeqNum && activeRoute.nextHopId != fromNeighborId) {
            rrepSentCount += 1
            routingTable.addPrecursor(rreq.destinationId, fromNeighborId)
            routingTable.addPrecursor(rreq.sourceId, activeRoute.nextHopId)

            val intermediateRrep = RouteReplyPayload(
                sourceId = rreq.sourceId,
                destinationId = rreq.destinationId,
                destSeqNum = activeRoute.destSeqNum,
                hopCount = activeRoute.hopCount,
                metricCost = activeRoute.metricCost,
                lifetimeMs = config.routeLifetimeMs,
                targetNextHop = fromNeighborId
            )
            return@withLock RreqResult.SendRrep(intermediateRrep, fromNeighborId)
        }

        // Case 3: Forward RREQ
        if (rreq.ttl <= 1) {
            return@withLock RreqResult.Dropped
        }

        val forwardedRreq = rreq.copy(
            hopCount = rreq.hopCount + 1,
            metricCost = rreq.metricCost + hopCost,
            ttl = rreq.ttl - 1
        )
        rreqSentCount += 1
        return@withLock RreqResult.ForwardRreq(forwardedRreq)
    }

    suspend fun handleIncomingRrep(
        rrep: RouteReplyPayload,
        fromNeighborId: String,
        linkRssi: Int,
        currentTimeMs: Long
    ): RrepResult = mutex.withLock {
        val hopCost = calculateHopCost(linkRssi)
        val forwardMetric = rrep.metricCost + hopCost

        val routeUpdated = routingTable.updateRoute(
            destinationId = rrep.destinationId,
            nextHopId = fromNeighborId,
            hopCount = rrep.hopCount + 1,
            destSeqNum = rrep.destSeqNum,
            metricCost = forwardMetric,
            currentTimeMs = currentTimeMs,
            lifetimeMs = rrep.lifetimeMs
        )

        // Case 1: We are the source that initiated discovery
        if (rrep.sourceId == localNodeId) {
            pendingDiscoveries.remove(rrep.destinationId)
            val establishedRoute = routingTable.getRoute(rrep.destinationId, currentTimeMs)
            return@withLock if (establishedRoute != null) {
                RrepResult.RouteEstablished(rrep.destinationId, establishedRoute)
            } else {
                RrepResult.Dropped
            }
        }

        // Case 2: Intermediate node forwarding RREP back to source along reverse route
        val reverseRoute = routingTable.getRoute(rrep.sourceId, currentTimeMs)
        if (reverseRoute != null) {
            routingTable.addPrecursor(rrep.sourceId, fromNeighborId)
            routingTable.addPrecursor(rrep.destinationId, reverseRoute.nextHopId)

            val forwardedRrep = rrep.copy(
                hopCount = rrep.hopCount + 1,
                metricCost = forwardMetric,
                targetNextHop = reverseRoute.nextHopId
            )
            rrepSentCount += 1
            return@withLock RrepResult.ForwardRrep(forwardedRrep, reverseRoute.nextHopId)
        } else {
            return@withLock RrepResult.Dropped
        }
    }

    suspend fun handleIncomingRerr(
        rerr: RouteErrorPayload,
        fromNeighborId: String,
        currentTimeMs: Long
    ): RerrResult = mutex.withLock {
        val affectedPrecursors = mutableSetOf<String>()
        val invalidatedNodes = mutableListOf<UnreachableNode>()

        for (unreachable in rerr.unreachableDestinations) {
            val route = routingTable.getRoute(unreachable.destinationId, currentTimeMs)
            if (route != null && route.nextHopId == fromNeighborId) {
                val precursors = routingTable.getPrecursors(unreachable.destinationId)
                affectedPrecursors.addAll(precursors)
                routingTable.invalidateRoute(unreachable.destinationId, incrementSeqNum = true)
                invalidatedNodes.add(unreachable.copy(destSeqNum = route.destSeqNum + 1))
            }
        }

        if (invalidatedNodes.isNotEmpty() && affectedPrecursors.isNotEmpty()) {
            rerrSentCount += 1
            val propagated = RouteErrorPayload(
                unreachableDestinations = invalidatedNodes
            )
            return@withLock RerrResult.PropagateRerr(propagated, affectedPrecursors)
        } else {
            return@withLock RerrResult.Ignored
        }
    }

    suspend fun handleLinkBreak(brokenNeighborId: String, currentTimeMs: Long): RouteErrorPayload? = mutex.withLock {
        val invalidated = routingTable.invalidateRoutesViaNextHop(brokenNeighborId)
        if (invalidated.isEmpty()) return@withLock null

        rerrSentCount += 1
        val unreachableList = invalidated.map {
            UnreachableNode(destinationId = it.destinationId, destSeqNum = it.destSeqNum)
        }
        RouteErrorPayload(unreachableDestinations = unreachableList)
    }

    suspend fun recordDirectedPacket() = mutex.withLock {
        totalDirectedPackets += 1
    }

    suspend fun recordFloodedPacket() = mutex.withLock {
        totalFloodedPackets += 1
    }

    suspend fun pruneExpiredRoutes(currentTimeMs: Long): List<String> = mutex.withLock {
        // Also prune old recentRreq entries
        recentRreqs.entries.removeAll { (currentTimeMs - it.value) > 10_000L }
        routingTable.pruneExpired(currentTimeMs)
    }

    suspend fun getSnapshot(currentTimeMs: Long): RoutingSnapshot = mutex.withLock {
        val activeList = routingTable.getAllActiveRoutes(currentTimeMs).map {
            RouteSummary(
                destinationId = it.destinationId,
                nextHopId = it.nextHopId,
                hopCount = it.hopCount,
                metricCost = it.metricCost,
                isValid = it.status == RouteStatus.ACTIVE && it.isFresh(currentTimeMs),
                ageMs = currentTimeMs - it.lastActiveTimeMs
            )
        }

        val total = totalDirectedPackets + totalFloodedPackets
        val savings = if (total > 0) {
            (totalDirectedPackets.toDouble() / total.toDouble()) * 100.0
        } else {
            0.0
        }

        RoutingSnapshot(
            localNodeId = localNodeId,
            activeRoutes = activeList,
            totalDirectedPackets = totalDirectedPackets,
            totalFloodedPackets = totalFloodedPackets,
            airtimeSavingsPercentage = savings,
            timestampMs = currentTimeMs
        )
    }

    suspend fun clear() = mutex.withLock {
        routingTable.clear()
        recentRreqs.clear()
        pendingDiscoveries.clear()
    }
}
