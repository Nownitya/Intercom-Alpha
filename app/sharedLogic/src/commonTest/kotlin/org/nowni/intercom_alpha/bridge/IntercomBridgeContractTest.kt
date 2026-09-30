package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.nowni.intercom_alpha.audio.*
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.mesh.ProximityZone
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.telemetry.MotionState
import org.nowni.intercom_alpha.text.*
import kotlin.test.*

class IntercomBridgeContractTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCancellationHandleStopsObserverUpdates() = runTest {
        val flow = MutableStateFlow("initial")
        val received = mutableListOf<String>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { received.add(it) }
        }
        val handle = CancellationHandle { job.cancel() }

        // Initial emission received
        assertEquals(listOf("initial"), received)

        flow.value = "update-1"
        assertEquals(listOf("initial", "update-1"), received)

        // Cancel subscription
        handle.cancel()

        flow.value = "update-2"
        // Should not have received update-2
        assertEquals(listOf("initial", "update-1"), received)
    }

    @Test
    fun testChannelManagementBridgeContract() = runTest {
        val channelManager = ChannelManager()

        // 1. Initial active channel should be 1
        assertEquals(1, channelManager.channelState.value.activeChannelId)

        // 2. Select channel 3
        channelManager.selectActiveChannel(3)
        assertEquals(3, channelManager.channelState.value.activeChannelId)

        // 3. Configure Dual-Watch monitoring
        channelManager.setMonitoredChannels(setOf(1, 16))
        assertEquals(setOf(1, 16), channelManager.channelState.value.monitoredChannelIds)

        // 4. Toggle Scan Mode
        assertFalse(channelManager.channelState.value.isScanMode)
        channelManager.setScanMode(true)
        assertTrue(channelManager.channelState.value.isScanMode)

        // 5. Toggle Mute
        channelManager.toggleChannelMute(3)
        val ch3 = channelManager.channelState.value.channels.first { it.id == 3 }
        assertTrue(ch3.isMuted)
    }

    @Test
    fun testProximityRadarAndTelemetryBridgeContract() = runTest {
        val radar = ProximityRadar()
        val gpsTracker = ConvoyGpsTracker("rider-alpha", "Alpha Leader")

        // 1. Update peer RSSI in radar
        radar.updatePeerRssi("rider-bravo", -50, hopCount = 1, displayName = "Bravo Scout")
        val radarSnapshot = radar.radarFlow.value
        assertEquals(1, radarSnapshot.activeRidersCount)
        assertEquals(ProximityZone.NEAR, radarSnapshot.closestTarget?.zone)

        // 2. Update local GPS location
        gpsTracker.updateLocalLocation(
            GpsCoordinate(latitude = 37.7749, longitude = -122.4194, speedKmh = 60f)
        )

        // 3. Process remote rider telemetry
        gpsTracker.handleIncomingTelemetry(
            org.nowni.intercom_alpha.telemetry.ConvoyTelemetryFrame(
                peerId = "rider-bravo",
                peerName = "Bravo Scout",
                coordinate = GpsCoordinate(latitude = 37.7755, longitude = -122.4190, speedKmh = 62f),
                motionState = MotionState.HIGHWAY_CRUISE
            )
        )

        val convoySnapshot = gpsTracker.convoyState.value
        assertEquals(1, convoySnapshot.activeCount)
        val closest = convoySnapshot.closestRider
        assertNotNull(closest)
        assertEquals("rider-bravo", closest.peerId)
        assertTrue(closest.distanceMeters > 0.0)
    }

    @Test
    fun testMeshTextMessagingAndAckBridgeContract() = runTest {
        val textManager = MeshTextManager("rider-alpha", "Alpha Leader")

        // 1. Send canned hazard
        val sentMsg = textManager.sendCannedMessage(CannedMessageCode.HAZARD_ROAD)
        assertEquals(CannedMessageCode.HAZARD_ROAD, sentMsg.cannedCode)
        assertEquals(DeliveryStatus.SENT, sentMsg.status)
        assertEquals(1, textManager.messages.value.size)

        // 2. Process delivery ACK
        val ackFrame = MeshTextWireFrame(
            wireType = TextWireType.DELIVERY_ACK,
            messageId = sentMsg.id,
            senderId = "rider-bravo",
            senderName = "Bravo Scout",
            content = "",
            timestamp = 1000L
        )
        val ackedMsg = textManager.handleIncomingWireFrame(ackFrame)
        assertNotNull(ackedMsg)
        assertEquals(DeliveryStatus.ACKNOWLEDGED, ackedMsg.status)

        // 3. Receive incoming message from remote peer
        val incomingFrame = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "remote-msg-1",
            senderId = "rider-bravo",
            senderName = "Bravo Scout",
            cannedCode = CannedMessageCode.FUEL_LOW,
            content = "Low Fuel Warning",
            timestamp = 2000L
        )
        textManager.handleIncomingWireFrame(incomingFrame)
        assertEquals(1, textManager.unreadCount.value)

        // 4. Mark all as read
        textManager.markAllAsRead()
        assertEquals(0, textManager.unreadCount.value)
    }

    @Test
    fun testPriorityPreemptionAndBlackboxBridgeContract() = runTest {
        val priorityManager = PriorityBroadcastManager(hangoverDurationMs = 500L)
        val blackboxRecorder = EmergencyBlackboxRecorder()

        // 1. Initial state
        assertFalse(priorityManager.isEmergencyActive.value)
        assertEquals(0, blackboxRecorder.bufferedFrameCount.value)

        // 2. Record normal audio frames into blackbox
        blackboxRecorder.recordFrame(
            BlackboxAudioFrame(
                timestampMs = 1000L,
                sequence = 1L,
                speakerId = "rider-bravo",
                adpcmData = ByteArray(80)
            ),
            now = 1000L
        )
        assertEquals(1, blackboxRecorder.bufferedFrameCount.value)

        // 3. Create and evaluate emergency override broadcast
        val emergencyFrame = priorityManager.createEmergencyBroadcast(
            senderId = "rider-charlie",
            audioData = ByteArray(160),
            alertType = AlertType.CRASH_DETECTED,
            sequence = 10L,
            timestamp = 1100L
        )

        val shouldPlay = priorityManager.shouldPlayFrame(emergencyFrame, now = 1100L)
        assertTrue(shouldPlay)
        assertTrue(priorityManager.isEmergencyActive.value)
        assertEquals(PriorityLevel.EMERGENCY_OVERRIDE, priorityManager.activePriority.value)

        // 4. Record emergency frame to blackbox -> auto incident freeze
        val incident = blackboxRecorder.recordPrioritizedFrame(
            frame = emergencyFrame,
            speakerName = "Charlie Sweep",
            now = 1100L
        )
        assertNotNull(incident)
        assertEquals(IncidentTriggerReason.CRASH_DETECTED, incident.triggerReason)

        // 5. Reset emergency
        priorityManager.reset()
        assertFalse(priorityManager.isEmergencyActive.value)
        assertEquals(PriorityLevel.NORMAL, priorityManager.activePriority.value)
    }

    @Test
    fun testAdaptiveBitrateBridgeContract() = runTest {
        val bitrateController = AdaptiveBitrateController(initialProfile = AudioProfile.MEDIUM)

        assertEquals(AudioProfile.MEDIUM, bitrateController.currentProfile.value)

        // Manual pin to ULTRA_LOW
        bitrateController.setManualOverride(AudioProfile.ULTRA_LOW)
        assertEquals(AudioProfile.ULTRA_LOW, bitrateController.currentProfile.value)
        assertEquals(AdaptationMode.MANUAL_OVERRIDE, bitrateController.adaptationState.value.mode)

        // Reset to AUTO
        bitrateController.setAutoMode()
        assertEquals(AdaptationMode.AUTO, bitrateController.adaptationState.value.mode)
    }
}
