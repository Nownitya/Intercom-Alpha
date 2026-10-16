package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.phy.BlePhyMode
import org.nowni.intercom_alpha.mesh.phy.PhyNegotiationEvent
import org.nowni.intercom_alpha.mesh.phy.PhyNegotiationSnapshot
import org.nowni.intercom_alpha.mesh.phy.TerrainShadowingProfile
import org.nowni.intercom_alpha.mesh.satellite.SatelliteBridgeStatus
import org.nowni.intercom_alpha.mesh.satellite.SatelliteLinkState
import org.nowni.intercom_alpha.mesh.satellite.SatelliteModemType
import org.nowni.intercom_alpha.mesh.wearable.PttButtonState
import org.nowni.intercom_alpha.mesh.wearable.RiderBiometricVitals
import org.nowni.intercom_alpha.mesh.wearable.RiderDistressStatus
import org.nowni.intercom_alpha.mesh.wearable.WearableCompanionSnapshot
import org.nowni.intercom_alpha.mesh.wearable.WearableDeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IntercomSatellitePhyBridgeTest {

    @Test
    fun testSatelliteBridgeStatusAndDistressBeacon() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial State
        val initialStatus = bridge.satelliteBridgeStatus.first()
        assertEquals(SatelliteModemType.IRIDIUM_SBD, initialStatus.modemType)
        assertEquals(SatelliteLinkState.OFFLINE, initialStatus.linkState)
        assertEquals(0, initialStatus.totalDispatchedCount)

        // 2. Direct Status Update
        val updatedStatus = SatelliteBridgeStatus(
            modemType = SatelliteModemType.DIRECT_TO_CELL_NTN,
            linkState = SatelliteLinkState.SATELLITE_ACQUIRED,
            signalBars = 4,
            pendingQueueSize = 1,
            totalDispatchedCount = 3
        )
        bridge.updateSatelliteBridgeStatus(updatedStatus)
        assertEquals(SatelliteLinkState.SATELLITE_ACQUIRED, bridge.satelliteBridgeStatus.value.linkState)
        assertEquals(4, bridge.satelliteBridgeStatus.value.signalBars)

        // 3. Trigger Satellite Distress Beacon
        val triggerResult = bridge.triggerSatelliteDistressBeacon("MAYDAY AVALANCHE")
        assertTrue(triggerResult)
        assertEquals(SatelliteLinkState.TRANSMITTING, bridge.satelliteBridgeStatus.value.linkState)
        assertEquals(4, bridge.satelliteBridgeStatus.value.totalDispatchedCount)
    }

    @Test
    fun testPhyNegotiationSnapshotSimulation() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial PHY Snapshot
        val initialPhy = bridge.phyNegotiationSnapshot.first()
        assertTrue(initialPhy.activeModes.isEmpty())
        assertEquals(TerrainShadowingProfile.OPEN_ALPINE_RIDGE, initialPhy.terrainProfile)

        // 2. Update PHY Snapshot with Coded PHY S=8
        val event = PhyNegotiationEvent(
            peerId = "node-bravo",
            previousPhy = BlePhyMode.LE_1M,
            targetPhy = BlePhyMode.CODED_S8,
            reason = "Deep canyon gorge shadowing detected",
            timestampMs = 12345678L
        )
        val phySnapshot = PhyNegotiationSnapshot(
            activeModes = mapOf("node-bravo" to BlePhyMode.CODED_S8),
            lastEvent = event,
            terrainProfile = TerrainShadowingProfile.DEEP_CANYON_GORGE
        )
        bridge.updatePhyNegotiationSnapshot(phySnapshot)

        val currentPhy = bridge.phyNegotiationSnapshot.value
        assertEquals(BlePhyMode.CODED_S8, currentPhy.activeModes["node-bravo"])
        assertEquals(TerrainShadowingProfile.DEEP_CANYON_GORGE, currentPhy.terrainProfile)
        assertNotNull(currentPhy.lastEvent)
        assertEquals("node-bravo", currentPhy.lastEvent!!.peerId)
    }

    @Test
    fun testWearableCompanionSnapshotAndPttTrigger() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial State
        val initialWearable = bridge.wearableCompanionSnapshot.first()
        assertEquals(WearableDeviceType.APPLE_WATCH, initialWearable.deviceType)
        assertEquals(PttButtonState.RELEASED, initialWearable.pttState)
        assertEquals(RiderDistressStatus.NORMAL, initialWearable.distressStatus)
        assertFalse(bridge.isTransmitting.value)

        // 2. Biometric Vitals & Distress Update
        val vitals = RiderBiometricVitals(
            heartRateBpm = 175,
            spo2Percent = 95,
            isFallDetected = true,
            timestampMs = 999999L
        )
        val updatedWearable = WearableCompanionSnapshot(
            deviceType = WearableDeviceType.APPLE_WATCH,
            pttState = PttButtonState.DOUBLE_TAP_SOS,
            distressStatus = RiderDistressStatus.FALL_CONFIRMED,
            vitals = vitals
        )
        bridge.updateWearableCompanionSnapshot(updatedWearable)

        val currentWearable = bridge.wearableCompanionSnapshot.value
        assertEquals(RiderDistressStatus.FALL_CONFIRMED, currentWearable.distressStatus)
        assertNotNull(currentWearable.vitals)
        assertTrue(currentWearable.vitals!!.isFallDetected)
        assertEquals(175, currentWearable.vitals!!.heartRateBpm)

        // 3. Wearable PTT Press
        bridge.processWearablePttClick(true)
        assertEquals(PttButtonState.PRESSED, bridge.wearableCompanionSnapshot.value.pttState)
        assertTrue(bridge.isTransmitting.value)

        // 4. Wearable PTT Release
        bridge.processWearablePttClick(false)
        assertEquals(PttButtonState.RELEASED, bridge.wearableCompanionSnapshot.value.pttState)
        assertFalse(bridge.isTransmitting.value)
    }
}
