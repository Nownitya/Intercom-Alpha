package org.nowni.intercom_alpha.mesh.routing

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AodvRoutingEngineTest {

    @Test
    fun testSerializationRoundTrip() {
        val rreq = RouteRequestPayload(
            rreqId = 101L,
            sourceId = "rider-alpha",
            sourceSeqNum = 5L,
            destinationId = "rider-omega",
            destSeqNum = 2L,
            hopCount = 1,
            metricCost = 1.5,
            ttl = 3
        )
        val rreqBytes = RoutingSerializer.encodeRreq(rreq)
        val decodedRreq = RoutingSerializer.decodeRreq(rreqBytes)
        assertNotNull(decodedRreq)
        assertEquals(rreq.rreqId, decodedRreq.rreqId)
        assertEquals(rreq.sourceId, decodedRreq.sourceId)
        assertEquals(rreq.destinationId, decodedRreq.destinationId)
        assertEquals(rreq.metricCost, decodedRreq.metricCost)

        val rrep = RouteReplyPayload(
            sourceId = "rider-alpha",
            destinationId = "rider-omega",
            destSeqNum = 3L,
            hopCount = 2,
            metricCost = 2.5,
            lifetimeMs = 25000L,
            targetNextHop = "rider-beta"
        )
        val rrepBytes = RoutingSerializer.encodeRrep(rrep)
        val decodedRrep = RoutingSerializer.decodeRrep(rrepBytes)
        assertNotNull(decodedRrep)
        assertEquals(rrep.sourceId, decodedRrep.sourceId)
        assertEquals(rrep.destinationId, decodedRrep.destinationId)
        assertEquals(rrep.targetNextHop, decodedRrep.targetNextHop)

        val rerr = RouteErrorPayload(
            unreachableDestinations = listOf(
                UnreachableNode(destinationId = "rider-omega", destSeqNum = 4L)
            ),
            targetNextHop = "rider-beta"
        )
        val rerrBytes = RoutingSerializer.encodeRerr(rerr)
        val decodedRerr = RoutingSerializer.decodeRerr(rerrBytes)
        assertNotNull(decodedRerr)
        assertEquals(1, decodedRerr.unreachableDestinations.size)
        assertEquals("rider-omega", decodedRerr.unreachableDestinations[0].destinationId)

        val directedData = DirectedDataPayload(
            sourceId = "rider-alpha",
            destinationId = "rider-omega",
            nextHopId = "rider-beta",
            payloadType = "TEXT",
            data = "HAZARD_AHEAD".encodeToByteArray(),
            sequence = 42L,
            ttl = 3
        )
        val dataBytes = RoutingSerializer.encodeDirectedData(directedData)
        val decodedData = RoutingSerializer.decodeDirectedData(dataBytes)
        assertNotNull(decodedData)
        assertEquals(directedData.sourceId, decodedData.sourceId)
        assertEquals(directedData.destinationId, decodedData.destinationId)
        assertEquals(directedData.payloadType, decodedData.payloadType)
        assertTrue(directedData.data.contentEquals(decodedData.data))
    }

    @Test
    fun testRoutingTableLoopFreedomAndMetrics() = runTest {
        val table = RoutingTable()
        val now = 1000L

        // Initial route install
        val added = table.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-b",
            hopCount = 2,
            destSeqNum = 10L,
            metricCost = 3.0,
            currentTimeMs = now
        )
        assertTrue(added)

        val route = table.getRoute("node-c", now)
        assertNotNull(route)
        assertEquals("node-b", route.nextHopId)
        assertEquals(10L, route.destSeqNum)
        assertEquals(3.0, route.metricCost)

        // Attempt update with older sequence number: should be rejected
        val rejectedOldSeq = table.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-d",
            hopCount = 1,
            destSeqNum = 9L,
            metricCost = 1.0,
            currentTimeMs = now
        )
        assertFalse(rejectedOldSeq)
        assertEquals("node-b", table.getRoute("node-c", now)?.nextHopId)

        // Attempt update with same sequence number but worse metric: rejected
        val rejectedWorseMetric = table.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-d",
            hopCount = 3,
            destSeqNum = 10L,
            metricCost = 4.5,
            currentTimeMs = now
        )
        assertFalse(rejectedWorseMetric)
        assertEquals(3.0, table.getRoute("node-c", now)?.metricCost)

        // Attempt update with same sequence number but strictly better metric: accepted
        val acceptedBetterMetric = table.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-d",
            hopCount = 1,
            destSeqNum = 10L,
            metricCost = 1.5,
            currentTimeMs = now
        )
        assertTrue(acceptedBetterMetric)
        val updatedRoute = table.getRoute("node-c", now)
        assertEquals("node-d", updatedRoute?.nextHopId)
        assertEquals(1.5, updatedRoute?.metricCost)

        // Attempt update with strictly higher sequence number: accepted regardless of metric
        val acceptedHigherSeq = table.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-e",
            hopCount = 3,
            destSeqNum = 12L,
            metricCost = 5.0,
            currentTimeMs = now
        )
        assertTrue(acceptedHigherSeq)
        assertEquals("node-e", table.getRoute("node-c", now)?.nextHopId)
        assertEquals(12L, table.getRoute("node-c", now)?.destSeqNum)
    }

    @Test
    fun testDirectTwoNodeDiscovery() = runTest {
        val nodeA = AodvRoutingEngine(localNodeId = "node-a")
        val nodeB = AodvRoutingEngine(localNodeId = "node-b")
        val now = 2000L

        // Node A initiates route discovery for Node B
        val rreq = nodeA.initiateRouteDiscovery("node-b", now)
        assertEquals("node-a", rreq.sourceId)
        assertEquals("node-b", rreq.destinationId)
        assertEquals(0, rreq.hopCount)

        // Node B receives RREQ from neighbor Node A with strong RSSI (-65 dBm)
        val rreqResult = nodeB.handleIncomingRreq(rreq, fromNeighborId = "node-a", linkRssi = -65, currentTimeMs = now)
        assertTrue(rreqResult is RreqResult.SendRrep)
        val rrep = rreqResult.payload
        assertEquals("node-a", rrep.sourceId)
        assertEquals("node-b", rrep.destinationId)
        assertEquals("node-a", rreqResult.targetNeighbor)

        // Node A processes RREP from Node B
        val rrepResult = nodeA.handleIncomingRrep(rrep, fromNeighborId = "node-b", linkRssi = -65, currentTimeMs = now + 20)
        assertTrue(rrepResult is RrepResult.RouteEstablished)
        assertEquals("node-b", rrepResult.destinationId)
        assertEquals("node-b", rrepResult.route.nextHopId)
        assertEquals(1, rrepResult.route.hopCount)

        // Node A now has a direct active route to Node B
        val activeRoute = nodeA.findRoute("node-b", now + 50)
        assertNotNull(activeRoute)
        assertEquals("node-b", activeRoute.nextHopId)
        assertEquals(1, activeRoute.hopCount)
    }

    @Test
    fun testMultiHopThreeNodeDiscovery() = runTest {
        val nodeA = AodvRoutingEngine(localNodeId = "node-a")
        val nodeB = AodvRoutingEngine(localNodeId = "node-b")
        val nodeC = AodvRoutingEngine(localNodeId = "node-c")
        val now = 5000L

        // Step 1: Node A initiates route discovery to Node C (out of direct RF range)
        val rreqA = nodeA.initiateRouteDiscovery("node-c", now)

        // Step 2: Node B (intermediate relay) receives RREQ from Node A
        val bResult = nodeB.handleIncomingRreq(rreqA, fromNeighborId = "node-a", linkRssi = -75, currentTimeMs = now + 10)
        assertTrue(bResult is RreqResult.ForwardRreq)
        val forwardedRreq = bResult.payload
        assertEquals(1, forwardedRreq.hopCount)
        assertEquals(3, forwardedRreq.ttl) // 4 - 1 = 3

        // Node B now has reverse route to Node A
        val reverseRouteOnB = nodeB.findRoute("node-a", now + 15)
        assertNotNull(reverseRouteOnB)
        assertEquals("node-a", reverseRouteOnB.nextHopId)

        // Step 3: Node C (destination) receives forwarded RREQ from Node B
        val cResult = nodeC.handleIncomingRreq(forwardedRreq, fromNeighborId = "node-b", linkRssi = -70, currentTimeMs = now + 20)
        assertTrue(cResult is RreqResult.SendRrep)
        val rrepC = cResult.payload
        assertEquals("node-a", rrepC.sourceId)
        assertEquals("node-c", rrepC.destinationId)
        assertEquals("node-b", cResult.targetNeighbor)

        // Step 4: Node B receives RREP from Node C and forwards it along reverse path to Node A
        val bRrepResult = nodeB.handleIncomingRrep(rrepC, fromNeighborId = "node-c", linkRssi = -70, currentTimeMs = now + 30)
        assertTrue(bRrepResult is RrepResult.ForwardRrep)
        val forwardedRrep = bRrepResult.payload
        assertEquals("node-a", bRrepResult.targetNeighbor)

        // Step 5: Node A receives forwarded RREP from Node B
        val aRrepResult = nodeA.handleIncomingRrep(forwardedRrep, fromNeighborId = "node-b", linkRssi = -75, currentTimeMs = now + 40)
        assertTrue(aRrepResult is RrepResult.RouteEstablished)

        // Verify Node A's route table has 2-hop route to Node C via next-hop Node B
        val routeToC = nodeA.findRoute("node-c", now + 50)
        assertNotNull(routeToC)
        assertEquals("node-b", routeToC.nextHopId)
        assertEquals(2, routeToC.hopCount)
        assertTrue(routeToC.metricCost > 2.0)
    }

    @Test
    fun testDuplicateRreqIsDropped() = runTest {
        val nodeB = AodvRoutingEngine(localNodeId = "node-b")
        val now = 3000L

        val rreq = RouteRequestPayload(
            rreqId = 1L,
            sourceId = "node-a",
            sourceSeqNum = 1L,
            destinationId = "node-c",
            destSeqNum = 0L,
            hopCount = 0,
            metricCost = 0.0,
            ttl = 4
        )

        val first = nodeB.handleIncomingRreq(rreq, "node-a", -70, now)
        assertTrue(first is RreqResult.ForwardRreq)

        // Duplicate incoming RREQ with identical sourceId and rreqId
        val second = nodeB.handleIncomingRreq(rreq, "node-a", -70, now + 100)
        assertTrue(second is RreqResult.Dropped)
    }

    @Test
    fun testLinkBreakAndRouteErrorPropagation() = runTest {
        val nodeA = AodvRoutingEngine(localNodeId = "node-a")
        val nodeB = AodvRoutingEngine(localNodeId = "node-b")
        val now = 10000L

        // Set up route A -> B -> C
        nodeA.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-b",
            hopCount = 2,
            destSeqNum = 5L,
            metricCost = 2.5,
            currentTimeMs = now
        )

        nodeB.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-c",
            hopCount = 1,
            destSeqNum = 5L,
            metricCost = 1.0,
            currentTimeMs = now,
            precursors = setOf("node-a")
        )

        // Verify initial route on A exists
        assertNotNull(nodeA.findRoute("node-c", now))

        // Node B detects link break with Node C
        val rerrPayload = nodeB.handleLinkBreak("node-c", now + 500)
        assertNotNull(rerrPayload)
        assertEquals(1, rerrPayload.unreachableDestinations.size)
        assertEquals("node-c", rerrPayload.unreachableDestinations[0].destinationId)

        // Node A receives RERR from Node B
        val aRerrResult = nodeA.handleIncomingRerr(rerrPayload, fromNeighborId = "node-b", currentTimeMs = now + 550)

        // Node A's route to Node C is now invalidated
        assertNull(nodeA.findRoute("node-c", now + 600))
    }

    @Test
    fun testRouteExpirationAndPruning() = runTest {
        val node = AodvRoutingEngine(
            localNodeId = "node-a",
            config = AodvConfig(routeLifetimeMs = 5000L)
        )
        val start = 1000L

        node.routingTable.updateRoute(
            destinationId = "node-b",
            nextHopId = "node-b",
            hopCount = 1,
            destSeqNum = 1L,
            metricCost = 1.0,
            currentTimeMs = start,
            lifetimeMs = 5000L
        )

        // Route is fresh at 3,000ms (touch refreshes lastActiveTimeMs to 3,000ms)
        assertNotNull(node.findRoute("node-b", 3000L))

        // Route has expired at 9,000ms (> 5,000ms past last active touch)
        assertNull(node.findRoute("node-b", 9000L))

        // Pruning removes it from active list
        val expired = node.pruneExpiredRoutes(9000L)
        assertEquals(listOf("node-b"), expired)
    }

    @Test
    fun testAirtimeEfficiencyMetrics() = runTest {
        val node = AodvRoutingEngine(localNodeId = "node-a")
        val now = 1000L

        node.routingTable.updateRoute(
            destinationId = "node-b",
            nextHopId = "node-b",
            hopCount = 1,
            destSeqNum = 1L,
            metricCost = 1.0,
            currentTimeMs = now
        )

        repeat(8) { node.recordDirectedPacket() }
        repeat(2) { node.recordFloodedPacket() }

        val snapshot = node.getSnapshot(now)
        assertEquals("node-a", snapshot.localNodeId)
        assertEquals(1, snapshot.activeRoutes.size)
        assertEquals(8L, snapshot.totalDirectedPackets)
        assertEquals(2L, snapshot.totalFloodedPackets)
        assertEquals(80.0, snapshot.airtimeSavingsPercentage)
    }
}
