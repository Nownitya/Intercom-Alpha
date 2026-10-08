package org.nowni.intercom_alpha.mesh.routing

import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket

/**
 * Pure KMP Directed Mesh Routing Dispatcher & RF Contention Mitigator.
 *
 * Coordinates between on-demand AODV-Lite route discovery, link quality metrics,
 * and packet dispatch. Suppresses redundant broadcast re-transmissions by selectively
 * directing point-to-point packets through discovered optimal multi-hop paths.
 *
 * Rule 2 & 3: 100% pure Kotlin Multiplatform with zero Java or platform dependencies.
 */

sealed class OutboundRouteDecision {
    data class SendUnicast(val nextHopId: String, val packet: MeshPacket.Control) : OutboundRouteDecision()
    data class DiscoverAndFloodFallback(
        val rreqPacket: MeshPacket.Control,
        val fallbackRelay: MeshPacket.Relay
    ) : OutboundRouteDecision()
}

sealed class InboundRouteDecision {
    data class DeliverLocal(val sourceId: String, val payloadType: String, val data: ByteArray) : InboundRouteDecision() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DeliverLocal) return false
            if (sourceId != other.sourceId) return false
            if (payloadType != other.payloadType) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sourceId.hashCode()
            result = 31 * result + payloadType.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    data class ForwardUnicast(val nextHopId: String, val packet: MeshPacket.Control) : InboundRouteDecision()
    data class BroadcastControl(val packet: MeshPacket.Control) : InboundRouteDecision()
    data class RouteEstablished(val destinationId: String, val route: RouteEntry) : InboundRouteDecision()
    data class Drop(val reason: String) : InboundRouteDecision()
}

class DirectedMeshRouter(
    val localNodeId: String,
    val routingEngine: AodvRoutingEngine = AodvRoutingEngine(localNodeId),
    val linkMetrics: LinkMetricCalculator = LinkMetricCalculator()
) {

    /**
     * Determines whether to route an outbound point-to-point payload selectively along
     * a discovered path, or initiate on-demand discovery while falling back to controlled flooding.
     */
    suspend fun routeOutboundPayload(
        destinationId: String,
        payloadType: String,
        data: ByteArray,
        sequence: Long,
        currentTimeMs: Long
    ): OutboundRouteDecision {
        val activeRoute = routingEngine.findRoute(destinationId, currentTimeMs)

        return if (activeRoute != null && !linkMetrics.isLinkBroken(activeRoute.nextHopId)) {
            routingEngine.recordDirectedPacket()

            val directedPayload = DirectedDataPayload(
                sourceId = localNodeId,
                destinationId = destinationId,
                nextHopId = activeRoute.nextHopId,
                payloadType = payloadType,
                data = data,
                sequence = sequence,
                ttl = 4
            )
            val controlPacket = MeshPacket.Control(
                senderId = localNodeId,
                type = ControlType.DIRECTED_DATA,
                payload = RoutingSerializer.encodeDirectedData(directedPayload)
            )
            OutboundRouteDecision.SendUnicast(activeRoute.nextHopId, controlPacket)
        } else {
            routingEngine.recordFloodedPacket()

            // Route missing or link broken: initiate discovery
            val rreqPayload = routingEngine.initiateRouteDiscovery(destinationId, currentTimeMs)
            val rreqPacket = MeshPacket.Control(
                senderId = localNodeId,
                type = ControlType.ROUTE_REQUEST,
                payload = RoutingSerializer.encodeRreq(rreqPayload)
            )

            // Wrap payload in standard flooding relay fallback
            val innerControl = MeshPacket.Control(
                senderId = localNodeId,
                type = ControlType.DIRECTED_DATA,
                payload = data
            )
            val fallbackRelay = MeshPacket.Relay(
                originalSenderId = localNodeId,
                ttl = 3,
                packet = innerControl
            )

            OutboundRouteDecision.DiscoverAndFloodFallback(rreqPacket, fallbackRelay)
        }
    }

    /**
     * Inspects incoming control frames.
     * Selectively forwards, drops overheard non-target packets, delivers final destinations,
     * and processes AODV discovery frames.
     */
    suspend fun processIncomingControl(
        packet: MeshPacket.Control,
        fromNeighborId: String,
        linkRssi: Int,
        currentTimeMs: Long
    ): InboundRouteDecision {
        return when (packet.type) {
            ControlType.DIRECTED_DATA -> {
                val directed = RoutingSerializer.decodeDirectedData(packet.payload)
                    ?: return InboundRouteDecision.Drop("Invalid directed data payload")

                // Step 1: Contention mitigation — drop if we are not the designated next-hop
                if (directed.nextHopId != localNodeId) {
                    return InboundRouteDecision.Drop("Overheard directed transmission not addressed to local node")
                }

                // Step 2: Final destination delivery
                if (directed.destinationId == localNodeId) {
                    return InboundRouteDecision.DeliverLocal(
                        sourceId = directed.sourceId,
                        payloadType = directed.payloadType,
                        data = directed.data
                    )
                }

                // Step 3: Intermediate forwarding along active path
                if (directed.ttl <= 1) {
                    return InboundRouteDecision.Drop("TTL expired on directed transmission")
                }

                val nextRoute = routingEngine.findRoute(directed.destinationId, currentTimeMs)
                if (nextRoute != null && !linkMetrics.isLinkBroken(nextRoute.nextHopId)) {
                    routingEngine.recordDirectedPacket()

                    val forwardedPayload = directed.copy(
                        nextHopId = nextRoute.nextHopId,
                        ttl = directed.ttl - 1
                    )
                    val forwardPacket = MeshPacket.Control(
                        senderId = localNodeId,
                        type = ControlType.DIRECTED_DATA,
                        payload = RoutingSerializer.encodeDirectedData(forwardedPayload)
                    )
                    InboundRouteDecision.ForwardUnicast(nextRoute.nextHopId, forwardPacket)
                } else {
                    // Route lost mid-path: trigger RERR and drop
                    val rerr = routingEngine.handleLinkBreak(fromNeighborId, currentTimeMs)
                    if (rerr != null) {
                        val rerrPacket = MeshPacket.Control(
                            senderId = localNodeId,
                            type = ControlType.ROUTE_ERROR,
                            payload = RoutingSerializer.encodeRerr(rerr)
                        )
                        InboundRouteDecision.BroadcastControl(rerrPacket)
                    } else {
                        InboundRouteDecision.Drop("No route forward to ${directed.destinationId}")
                    }
                }
            }

            ControlType.ROUTE_REQUEST -> {
                val rreq = RoutingSerializer.decodeRreq(packet.payload)
                    ?: return InboundRouteDecision.Drop("Invalid RREQ payload")

                when (val result = routingEngine.handleIncomingRreq(rreq, fromNeighborId, linkRssi, currentTimeMs)) {
                    is RreqResult.SendRrep -> {
                        val rrepPacket = MeshPacket.Control(
                            senderId = localNodeId,
                            type = ControlType.ROUTE_REPLY,
                            payload = RoutingSerializer.encodeRrep(result.payload)
                        )
                        InboundRouteDecision.ForwardUnicast(result.targetNeighbor, rrepPacket)
                    }
                    is RreqResult.ForwardRreq -> {
                        val forwardedPacket = MeshPacket.Control(
                            senderId = localNodeId,
                            type = ControlType.ROUTE_REQUEST,
                            payload = RoutingSerializer.encodeRreq(result.payload)
                        )
                        InboundRouteDecision.BroadcastControl(forwardedPacket)
                    }
                    is RreqResult.Dropped -> {
                        InboundRouteDecision.Drop("Duplicate or expired RREQ")
                    }
                }
            }

            ControlType.ROUTE_REPLY -> {
                val rrep = RoutingSerializer.decodeRrep(packet.payload)
                    ?: return InboundRouteDecision.Drop("Invalid RREP payload")

                when (val result = routingEngine.handleIncomingRrep(rrep, fromNeighborId, linkRssi, currentTimeMs)) {
                    is RrepResult.RouteEstablished -> {
                        InboundRouteDecision.RouteEstablished(result.destinationId, result.route)
                    }
                    is RrepResult.ForwardRrep -> {
                        val forwardPacket = MeshPacket.Control(
                            senderId = localNodeId,
                            type = ControlType.ROUTE_REPLY,
                            payload = RoutingSerializer.encodeRrep(result.payload)
                        )
                        InboundRouteDecision.ForwardUnicast(result.targetNeighbor, forwardPacket)
                    }
                    is RrepResult.Dropped -> {
                        InboundRouteDecision.Drop("Unrouted RREP")
                    }
                }
            }

            ControlType.ROUTE_ERROR -> {
                val rerr = RoutingSerializer.decodeRerr(packet.payload)
                    ?: return InboundRouteDecision.Drop("Invalid RERR payload")

                when (val result = routingEngine.handleIncomingRerr(rerr, fromNeighborId, currentTimeMs)) {
                    is RerrResult.PropagateRerr -> {
                        val propPacket = MeshPacket.Control(
                            senderId = localNodeId,
                            type = ControlType.ROUTE_ERROR,
                            payload = RoutingSerializer.encodeRerr(result.payload)
                        )
                        InboundRouteDecision.BroadcastControl(propPacket)
                    }
                    is RerrResult.Ignored -> {
                        InboundRouteDecision.Drop("RERR did not affect active routes")
                    }
                }
            }

            else -> {
                InboundRouteDecision.Drop("Non-routing control packet")
            }
        }
    }

    /**
     * Reports link feedback upon packet transmission ACK/NACK.
     * Triggers route break invalidation and RERR generation on persistent failure.
     */
    suspend fun onTxFeedback(
        targetPeerId: String,
        success: Boolean,
        rssiDbm: Int?,
        currentTimeMs: Long
    ): MeshPacket.Control? {
        val health = linkMetrics.recordTxAttempt(targetPeerId, success, rssiDbm, currentTimeMs)
        if (health.isBroken) {
            val rerr = routingEngine.handleLinkBreak(targetPeerId, currentTimeMs)
            if (rerr != null) {
                return MeshPacket.Control(
                    senderId = localNodeId,
                    type = ControlType.ROUTE_ERROR,
                    payload = RoutingSerializer.encodeRerr(rerr)
                )
            }
        }
        return null
    }

    suspend fun getSnapshot(currentTimeMs: Long): RoutingSnapshot {
        return routingEngine.getSnapshot(currentTimeMs)
    }

    suspend fun clear() {
        routingEngine.clear()
        linkMetrics.clear()
    }
}
