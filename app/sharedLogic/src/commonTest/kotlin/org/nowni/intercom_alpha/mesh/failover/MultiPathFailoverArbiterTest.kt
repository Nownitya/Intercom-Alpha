package org.nowni.intercom_alpha.mesh.failover

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiPathFailoverArbiterTest {

    @Test
    fun testInitialRestrictedOfflineState() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        val metrics = arbiter.metrics.value
        assertEquals(FailoverMode.RESTRICTED_OFFLINE, metrics.currentMode)
        assertEquals(0, metrics.meshPeerCount)
        assertFalse(metrics.isCloudConnected)
    }

    @Test
    fun testBleDirectWhenBleHealthyAndCloudConnected() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        val time = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = time)
        val mode = arbiter.updateMeshMetrics(
            peerCount = 3,
            avgEtx = 1.1,
            deliveryRatio = 0.98,
            minRssiDbm = -65,
            currentTimeMs = time
        )

        assertEquals(FailoverMode.BLE_DIRECT, mode)
        val metrics = arbiter.metrics.value
        assertEquals(FailoverMode.BLE_DIRECT, metrics.currentMode)
        assertEquals(3, metrics.meshPeerCount)
        assertTrue(metrics.isCloudConnected)
        assertFalse(metrics.isBleDegraded)
    }

    @Test
    fun testImmediateEscalationToHedgedOnDegradedLink() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        var time = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = time)
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.2, deliveryRatio = 0.95, minRssiDbm = -70, currentTimeMs = time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Case A: High ETX degradation
        time += 1000L
        val modeEtx = arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 3.2, deliveryRatio = 0.90, minRssiDbm = -70, currentTimeMs = time)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, modeEtx)
        assertTrue(arbiter.metrics.value.isBleDegraded)

        // Reset to healthy
        time += 6000L
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.1, deliveryRatio = 0.95, minRssiDbm = -70, currentTimeMs = time)
        time += 5100L
        arbiter.evaluateState(time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Case B: High packet loss (delivery ratio < 0.80)
        time += 500L
        val modeLoss = arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.5, deliveryRatio = 0.65, minRssiDbm = -70, currentTimeMs = time)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, modeLoss)

        // Case C: Weak RSSI (e.g. -92 dBm)
        time += 500L
        val modeRssi = arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.5, deliveryRatio = 0.90, minRssiDbm = -95, currentTimeMs = time)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, modeRssi)
    }

    @Test
    fun testEscalationToCloudRelayOnlyWhenBlePeersLostOrTimedOut() = runTest {
        val arbiter = MultiPathFailoverArbiter(config = FailoverConfig(bleTimeoutMs = 4000L))
        var time = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = time)
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.2, deliveryRatio = 0.95, minRssiDbm = -70, currentTimeMs = time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Peers drop to 0
        time += 500L
        val modeZeroPeers = arbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = time)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, modeZeroPeers)

        // Peers back, but time out without updates
        time += 500L
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 1.2, deliveryRatio = 0.95, minRssiDbm = -70, currentTimeMs = time)
        time += 6000L // Exceeds 5000ms hysteresis
        arbiter.evaluateState(time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Timeout past 4000ms without new BLE update
        time += 4500L
        val modeTimeout = arbiter.evaluateState(time)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, modeTimeout)
    }

    @Test
    fun testHysteresisPreventsFlappingWhenRejoiningBle() = runTest {
        val config = FailoverConfig(hysteresisDurationMs = 5000L)
        val arbiter = MultiPathFailoverArbiter(config = config)
        var time = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = time)
        // Convoy split: Cloud only
        arbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = time)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, arbiter.metrics.value.currentMode)

        // BLE peers reappear with clean link
        time += 1000L
        val modeFirstSeen = arbiter.updateMeshMetrics(peerCount = 3, avgEtx = 1.1, deliveryRatio = 0.95, minRssiDbm = -65, currentTimeMs = time)
        // Should STILL be in CLOUD_RELAY_ONLY to prevent flapping
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, modeFirstSeen)

        // 3 seconds in (less than 5s hysteresis): still CLOUD_RELAY_ONLY
        time += 3000L
        val mode3s = arbiter.evaluateState(time)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, mode3s)

        // 5.1 seconds in (hysteresis expired): cleanly transitions to BLE_DIRECT
        time += 2100L
        val mode5s = arbiter.evaluateState(time)
        assertEquals(FailoverMode.BLE_DIRECT, mode5s)
    }

    @Test
    fun testRestrictedOfflineWhenBothBleAndCloudLost() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        val time = 1000L

        arbiter.updateCloudStatus(connected = false, currentTimeMs = time)
        val mode = arbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = time)
        assertEquals(FailoverMode.RESTRICTED_OFFLINE, mode)
    }

    @Test
    fun testForceModeOverrideAndHedgedPacketTracking() = runTest {
        val arbiter = MultiPathFailoverArbiter()
        var time = 1000L

        arbiter.updateCloudStatus(connected = true, currentTimeMs = time)
        arbiter.updateMeshMetrics(peerCount = 3, avgEtx = 1.1, deliveryRatio = 0.95, minRssiDbm = -65, currentTimeMs = time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        // Force to CLOUD_RELAY_ONLY
        time += 500L
        arbiter.forceMode(FailoverMode.CLOUD_RELAY_ONLY, currentTimeMs = time)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, arbiter.metrics.value.currentMode)

        // Track hedged packets
        arbiter.recordHedgedPacket()
        arbiter.recordHedgedPacket()
        assertEquals(2L, arbiter.metrics.value.hedgedPacketsCount)

        // Release forced mode
        time += 500L
        arbiter.forceMode(null, currentTimeMs = time)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)
    }
}
