package org.nowni.intercom_alpha.signaling

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.protocol.PeerInfo
import org.nowni.intercom_alpha.protocol.SignalingMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalingServerManagerTest {

    @Test
    fun testRoomBridgingManagement() {
        val manager = SignalingServerManager()

        assertEquals(emptySet(), manager.getBridgedRooms("Room-A"))
        manager.bridgeRooms("Room-A", "Room-B")

        assertEquals(setOf("Room-B"), manager.getBridgedRooms("Room-A"))
        assertEquals(setOf("Room-A"), manager.getBridgedRooms("Room-B"))

        val metrics = manager.getMetrics()
        assertTrue(metrics.containsKey("bridgedRooms"))

        manager.unbridgeRooms("Room-A", "Room-B")
        assertEquals(emptySet(), manager.getBridgedRooms("Room-A"))
        assertEquals(emptySet(), manager.getBridgedRooms("Room-B"))
    }

    @Test
    fun testClusterBridgeHopLimitAndMetrics() = runTest {
        val manager = SignalingServerManager()

        val expiredBridge = SignalingMessage.ClusterBridge(
            bridgeId = "b-expired",
            sourceClusterId = "cluster-1",
            targetClusterId = "cluster-2",
            targetRoomId = null,
            packetType = "AUDIO",
            payloadBase64 = "AQID",
            hopLimit = 0,
            clusterPath = listOf("cluster-1"),
            timestampMs = 1000L
        )

        // Hop limit 0 is dropped and not routed
        manager.handleClusterBridge("peer-fake", expiredBridge)
        val metrics = manager.getMetrics()
        assertEquals(0L, metrics["clusterBridgesRouted"])

        // Valid hop limit increments routed count
        val validBridge = expiredBridge.copy(bridgeId = "b-valid", hopLimit = 2)
        manager.handleClusterBridge("peer-fake", validBridge)
        val metricsAfter = manager.getMetrics()
        assertEquals(1L, metricsAfter["clusterBridgesRouted"])
    }
}
