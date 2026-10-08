package org.nowni.intercom_alpha.mesh.routing

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectedForwardingTest {

    @Test
    fun testLinkMetricCalculatorEtxAndFailureThreshold() = runTest {
        val calc = LinkMetricCalculator(maxConsecutiveFailures = 3)
        val now = 1000L

        // Record initial successful transmissions with good RSSI (-65 dBm)
        val update1 = calc.recordTxAttempt("peer-1", success = true, rssiDbm = -65, currentTimeMs = now)
        assertFalse(update1.isBroken)
        assertEquals(0, update1.consecutiveFailures)
        assertTrue(update1.cost < 1.5)

        // Record transmission failure 1
        val updateFail1 = calc.recordTxAttempt("peer-1", success = false, currentTimeMs = now + 10)
        assertFalse(updateFail1.isBroken)
        assertEquals(1, updateFail1.consecutiveFailures)

        // Record transmission failure 2
        val updateFail2 = calc.recordTxAttempt("peer-1", success = false, currentTimeMs = now + 20)
        assertFalse(updateFail2.isBroken)
        assertEquals(2, updateFail2.consecutiveFailures)

        // Record transmission failure 3 -> triggers link break!
        val updateFail3 = calc.recordTxAttempt("peer-1", success = false, currentTimeMs = now + 30)
        assertTrue(updateFail3.isBroken)
        assertEquals(3, updateFail3.consecutiveFailures)
        assertTrue(calc.isLinkBroken("peer-1"))

        // Reconnect / successful transmission resets failure count
        val recovery = calc.recordTxAttempt("peer-1", success = true, rssiDbm = -60, currentTimeMs = now + 100)
        assertFalse(recovery.isBroken)
        assertEquals(0, recovery.consecutiveFailures)
        assertFalse(calc.isLinkBroken("peer-1"))
    }

    @Test
    fun testOutboundRoutingKnownVsUnknownPath() = runTest {
        val router = DirectedMeshRouter(localNodeId = "node-a")
        val now = 2000L
        val testData = "HAZARD_GRAVEL".encodeToByteArray()

        // 1. Unknown destination -> triggers on-demand discovery + flood fallback
        val unknownDecision = router.routeOutboundPayload(
            destinationId = "node-z",
            payloadType = "TEXT",
            data = testData,
            sequence = 1L,
            currentTimeMs = now
        )
        assertTrue(unknownDecision is OutboundRouteDecision.DiscoverAndFloodFallback)
        assertEquals(ControlType.ROUTE_REQUEST, unknownDecision.rreqPacket.type)
        assertEquals(ControlType.DIRECTED_DATA, (unknownDecision.fallbackRelay.packet as MeshPacket.Control).type)

        // 2. Install active route to node-c via node-b
        router.routingEngine.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-b",
            hopCount = 2,
            destSeqNum = 5L,
            metricCost = 2.0,
            currentTimeMs = now
        )

        // 3. Known destination -> triggers selective unicast directly to next-hop node-b
        val knownDecision = router.routeOutboundPayload(
            destinationId = "node-c",
            payloadType = "TEXT",
            data = testData,
            sequence = 2L,
            currentTimeMs = now + 10
        )
        assertTrue(knownDecision is OutboundRouteDecision.SendUnicast)
        assertEquals("node-b", knownDecision.nextHopId)
        assertEquals(ControlType.DIRECTED_DATA, knownDecision.packet.type)

        val directedPayload = RoutingSerializer.decodeDirectedData(knownDecision.packet.payload)
        assertNotNull(directedPayload)
        assertEquals("node-a", directedPayload.sourceId)
        assertEquals("node-c", directedPayload.destinationId)
        assertEquals("node-b", directedPayload.nextHopId)
    }

    @Test
    fun testContentionMitigationOverheardPacketDropped() = runTest {
        // Node D is an overheard bystander in the BLE mesh range
        val routerD = DirectedMeshRouter(localNodeId = "node-d")
        val now = 3000L

        // Directed packet targeted from Node A to Node C, designated next-hop is Node B
        val directedPayload = DirectedDataPayload(
            sourceId = "node-a",
            destinationId = "node-c",
            nextHopId = "node-b",
            payloadType = "TEXT",
            data = "STORM_ALERT".encodeToByteArray(),
            sequence = 10L,
            ttl = 4
        )
        val packet = MeshPacket.Control(
            senderId = "node-a",
            type = ControlType.DIRECTED_DATA,
            payload = RoutingSerializer.encodeDirectedData(directedPayload)
        )

        // Node D overhears packet: because nextHopId is "node-b" != "node-d", Node D drops it!
        val decision = routerD.processIncomingControl(packet, fromNeighborId = "node-a", linkRssi = -70, currentTimeMs = now)
        assertTrue(decision is InboundRouteDecision.Drop)
        assertTrue(decision.reason.contains("Overheard directed transmission"))
    }

    @Test
    fun testEndToEndThreeNodeDirectedDelivery() = runTest {
        val nodeA = DirectedMeshRouter(localNodeId = "node-a")
        val nodeB = DirectedMeshRouter(localNodeId = "node-b")
        val nodeC = DirectedMeshRouter(localNodeId = "node-c")
        val now = 5000L

        // 1. Establish routes:
        // Node A knows route to Node C via Node B
        nodeA.routingEngine.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-b",
            hopCount = 2,
            destSeqNum = 1L,
            metricCost = 2.0,
            currentTimeMs = now
        )
        // Node B knows route to Node C directly via Node C
        nodeB.routingEngine.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-c",
            hopCount = 1,
            destSeqNum = 1L,
            metricCost = 1.0,
            currentTimeMs = now
        )

        // 2. Node A dispatches directed text message to Node C
        val messageBytes = "ALL_CLEAR".encodeToByteArray()
        val outboundA = nodeA.routeOutboundPayload(
            destinationId = "node-c",
            payloadType = "TEXT",
            data = messageBytes,
            sequence = 100L,
            currentTimeMs = now
        )
        assertTrue(outboundA is OutboundRouteDecision.SendUnicast)
        assertEquals("node-b", outboundA.nextHopId)

        // 3. Node B receives directed packet from Node A
        val inboundB = nodeB.processIncomingControl(
            packet = outboundA.packet,
            fromNeighborId = "node-a",
            linkRssi = -68,
            currentTimeMs = now + 10
        )
        // Node B forwards it to Node C
        assertTrue(inboundB is InboundRouteDecision.ForwardUnicast)
        assertEquals("node-c", inboundB.nextHopId)

        // 4. Node C receives forwarded packet from Node B
        val inboundC = nodeC.processIncomingControl(
            packet = inboundB.packet,
            fromNeighborId = "node-b",
            linkRssi = -65,
            currentTimeMs = now + 20
        )
        // Node C is the final destination and delivers the payload locally
        assertTrue(inboundC is InboundRouteDecision.DeliverLocal)
        assertEquals("node-a", inboundC.sourceId)
        assertEquals("TEXT", inboundC.payloadType)
        assertTrue(messageBytes.contentEquals(inboundC.data))
    }

    @Test
    fun testLinkFailureGeneratesRouteError() = runTest {
        val routerA = DirectedMeshRouter(localNodeId = "node-a")
        val now = 10000L

        // Route A -> B -> C
        routerA.routingEngine.routingTable.updateRoute(
            destinationId = "node-c",
            nextHopId = "node-b",
            hopCount = 2,
            destSeqNum = 5L,
            metricCost = 2.0,
            currentTimeMs = now
        )

        // Confirm route is active
        assertNotNull(routerA.routingEngine.findRoute("node-c", now))

        // Simulate 2 transmission failures to node-b (not broken yet)
        assertNull(routerA.onTxFeedback("node-b", success = false, rssiDbm = -92, currentTimeMs = now + 10))
        assertNull(routerA.onTxFeedback("node-b", success = false, rssiDbm = -92, currentTimeMs = now + 20))

        // 3rd failure breaks link -> onTxFeedback emits RERR
        val rerrPacket = routerA.onTxFeedback("node-b", success = false, rssiDbm = -92, currentTimeMs = now + 30)
        assertNotNull(rerrPacket)
        assertEquals(ControlType.ROUTE_ERROR, rerrPacket.type)

        // Route to node-c is now invalidated on routerA
        assertNull(routerA.routingEngine.findRoute("node-c", now + 40))
    }
}
