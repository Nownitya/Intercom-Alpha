package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.radio.MultiRadioArbiter
import org.nowni.intercom_alpha.mesh.radio.MultiRadioState
import org.nowni.intercom_alpha.mesh.radio.RadioType
import org.nowni.intercom_alpha.mesh.security.GroupRekeyProtocol
import org.nowni.intercom_alpha.mesh.security.RekeyEpochSnapshot
import org.nowni.intercom_alpha.mesh.security.RekeyTriggerReason
import org.nowni.intercom_alpha.mesh.visual.SnapshotTransferDirection
import org.nowni.intercom_alpha.mesh.visual.SnapshotTransferManager
import org.nowni.intercom_alpha.mesh.visual.SnapshotTransferProgress
import org.nowni.intercom_alpha.mesh.visual.SnapshotTransferStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit verification test suite for Sprint 29 Hardware Bridge Wiring (ICA-2901).
 * Validates reactive StateFlow updates, simulation hooks, and subsystem attachments
 * for Tactical Snapshots, Multi-Radio Arbiter, and OTA Group Rekeying.
 */
class IntercomHardwareBridgeWiringTest {

    @Test
    fun testInitialBridgeStateForNewSubsystems() {
        val bridge = NoOpIntercomHardwareBridge()

        // Initial Snapshot state should be null
        assertNull(bridge.snapshotProgress.value, "Initial snapshot progress must be null")

        // Initial MultiRadio state should be default
        val radioState = bridge.multiRadioState.value
        assertEquals(RadioType.BLE_MESH, radioState.bulkTransferPreferredRadio)
        assertEquals(RadioType.BLE_MESH, radioState.voicePreferredRadio)
        assertFalse(radioState.wifiDirectConnected)

        // Initial Rekey state
        val rekeyState = bridge.rekeyEpochSnapshot.value
        assertEquals(1L, rekeyState.currentEpoch)
        assertFalse(rekeyState.isGracePeriodActive)
    }

    @Test
    fun testSnapshotProgressUpdatesAndSimulation() {
        val bridge = NoOpIntercomHardwareBridge()

        val progress = SnapshotTransferProgress(
            snapshotId = "snap-001",
            senderId = "rider-alpha",
            direction = SnapshotTransferDirection.OUTGOING,
            chunksTransferred = 6,
            totalChunks = 12,
            bytesTransferred = 768L,
            totalBytes = 1536L,
            status = SnapshotTransferStatus.IN_PROGRESS
        )

        bridge.updateSnapshotProgress(progress)
        assertEquals("snap-001", bridge.snapshotProgress.value?.snapshotId)
        assertEquals(0.5f, bridge.snapshotProgress.value?.progressPercent)
        assertEquals(SnapshotTransferStatus.IN_PROGRESS, bridge.snapshotProgress.value?.status)

        // Test simulation hook
        val completedProgress = progress.copy(
            chunksTransferred = 12,
            bytesTransferred = 1536L,
            status = SnapshotTransferStatus.COMPLETED
        )
        bridge.simulateSnapshotProgress(completedProgress)
        assertEquals(1.0f, bridge.snapshotProgress.value?.progressPercent)
        assertEquals(SnapshotTransferStatus.COMPLETED, bridge.snapshotProgress.value?.status)
    }

    @Test
    fun testMultiRadioStateUpdatesAndBulkPreference() {
        val bridge = NoOpIntercomHardwareBridge()

        val activeState = MultiRadioState(
            bleActivePeers = 8,
            wifiDirectConnected = true,
            wifiDirectPeerId = "p2p-lead",
            cellularConnected = false,
            activeRadios = listOf(RadioType.BLE_MESH, RadioType.WIFI_DIRECT),
            bulkTransferPreferredRadio = RadioType.WIFI_DIRECT,
            voicePreferredRadio = RadioType.BLE_MESH
        )

        bridge.updateMultiRadioState(activeState)
        assertEquals(8, bridge.multiRadioState.value.bleActivePeers)
        assertTrue(bridge.multiRadioState.value.wifiDirectConnected)
        assertEquals(RadioType.WIFI_DIRECT, bridge.multiRadioState.value.bulkTransferPreferredRadio)

        // Override bulk preference
        bridge.setPreferredRadioForBulk(RadioType.BLE_MESH)
        assertEquals(RadioType.BLE_MESH, bridge.multiRadioState.value.bulkTransferPreferredRadio)
    }

    @Test
    fun testRekeyEpochSnapshotUpdatesAndProtocolSnapshotGeneration() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        val snapshot = RekeyEpochSnapshot(
            currentEpoch = 4L,
            isGracePeriodActive = true,
            previousEpoch = 3L,
            gracePeriodRemainingSeconds = 8,
            lastRekeyTimestampMs = 123456789L,
            lastRekeyReason = RekeyTriggerReason.MEMBER_LEFT
        )

        bridge.updateRekeyEpochSnapshot(snapshot)
        assertEquals(4L, bridge.rekeyEpochSnapshot.value.currentEpoch)
        assertTrue(bridge.rekeyEpochSnapshot.value.isGracePeriodActive)
        assertEquals(3L, bridge.rekeyEpochSnapshot.value.previousEpoch)
        assertEquals(8, bridge.rekeyEpochSnapshot.value.gracePeriodRemainingSeconds)
        assertEquals(RekeyTriggerReason.MEMBER_LEFT, bridge.rekeyEpochSnapshot.value.lastRekeyReason)

        // Test GroupRekeyProtocol.getSnapshot()
        val baseKey = ByteArray(32) { 0xAA.toByte() }
        val protocol = GroupRekeyProtocol(
            groupId = "squad-test",
            localNodeId = "node-test",
            baseKeyInput = baseKey,
            initialEpoch = 1L
        )

        val protoSnapshot = protocol.getSnapshot()
        assertEquals(1L, protoSnapshot.currentEpoch)
        assertFalse(protoSnapshot.isGracePeriodActive)
        assertNull(protoSnapshot.previousEpoch)

        // Rotate epoch
        val packet = protocol.initiateRekey(reason = RekeyTriggerReason.MANUAL_REKEY)
        assertEquals(2L, packet.targetEpoch)

        val updatedProtoSnapshot = protocol.getSnapshot()
        assertEquals(2L, updatedProtoSnapshot.currentEpoch)
        assertTrue(updatedProtoSnapshot.isGracePeriodActive)
        assertEquals(1L, updatedProtoSnapshot.previousEpoch)
    }

    @Test
    fun testAttachSubsystemsAcceptsNewManagers() {
        val bridge = NoOpIntercomHardwareBridge()

        val snapshotManager = SnapshotTransferManager()
        val multiRadioArbiter = MultiRadioArbiter()
        val rekeyProtocol = GroupRekeyProtocol(
            groupId = "group-sub",
            localNodeId = "node-sub",
            baseKeyInput = ByteArray(32) { 0x55.toByte() }
        )

        // attachSubsystems should accept the new managers without throwing
        bridge.attachSubsystems(
            channelManager = org.nowni.intercom_alpha.channel.ChannelManager(),
            blackboxRecorder = org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder(),
            snapshotManager = snapshotManager,
            multiRadioArbiter = multiRadioArbiter,
            groupRekeyProtocol = rekeyProtocol
        )

        assertNotNull(bridge)
    }
}
