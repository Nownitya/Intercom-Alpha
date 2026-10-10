package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.map.*
import org.nowni.intercom_alpha.mesh.*
import kotlin.test.*

class MeshTransportQoSTest {

    private class MockMeshTransport : MeshTransport {
        val sentPackets = mutableListOf<MeshPacket>()
        private val _peers = Channel<List<Peer>>(Channel.CONFLATED)
        private val _audio = Channel<MeshPacket.Audio>(Channel.BUFFERED)
        private val _control = Channel<MeshPacket.Control>(Channel.BUFFERED)
        private val _conn = Channel<ConnectionState>(Channel.CONFLATED)

        override val peers: ReceiveChannel<List<Peer>> = _peers
        override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = _audio
        override val incomingControl: ReceiveChannel<MeshPacket.Control> = _control
        override val connectionState: ReceiveChannel<ConnectionState> = _conn

        override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {}
        override suspend fun stop() {}

        override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {
            sentPackets.add(MeshPacket.Audio("mock-sender", 1L, 1000L, data, profile))
        }

        override suspend fun sendControl(type: ControlType, payload: ByteArray) {
            sentPackets.add(MeshPacket.Control("mock-sender", type, payload))
        }

        override fun updateConfig(config: MeshConfig) {}
    }

    @Test
    fun testOutboundPacketsRouteThroughQoSScheduler() = runTest {
        val mock = MockMeshTransport()
        val scheduler = MeshQoSScheduler()
        val transport = QoSAwareMeshTransport(underlyingTransport = mock, qosScheduler = scheduler)

        // 1. Send Audio frame
        transport.sendAudio(byteArrayOf(1, 2, 3, 4), AudioProfile.MEDIUM)
        var snap = scheduler.qosSnapshot.value
        assertEquals(1L, snap.totalPacketsEnqueued)
        assertTrue(snap.isVoiceActive)
        assertEquals(1, mock.sentPackets.size)
        assertTrue(mock.sentPackets.first() is MeshPacket.Audio)

        // 2. Send Control frame (Routing control)
        transport.sendControl(ControlType.ROUTE_REQUEST, byteArrayOf(9, 9))
        snap = scheduler.qosSnapshot.value
        assertEquals(2L, snap.totalPacketsEnqueued)
        assertEquals(2, mock.sentPackets.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testFileTransferThrottlingDuringActiveVoice() = runTest {
        val scheduler = MeshQoSScheduler(voiceHangoverMs = 400L)
        val fileManager = MeshFileTransferManager(qosScheduler = scheduler)

        // Initial state: no voice
        assertFalse(fileManager.isThrottled)

        val sampleTrail = TrailPolyline(
            id = "trail-1",
            name = "Peak Ascent",
            points = listOf(GeoPoint(45.0, 6.0, 2000.0), GeoPoint(45.1, 6.1, 2200.0))
        )

        val sentChunks = mutableListOf<FileTransferChunk>()
        fileManager.onSendChunk = { chunk, _ ->
            sentChunks.add(chunk)
        }

        // Active voice is triggered
        scheduler.markVoiceActive()
        assertTrue(fileManager.isThrottled)

        // Start send trail during active voice -> pacing delay must be applied
        val startTime = currentTime
        fileManager.startSendTrail(sampleTrail, format = FileTransferType.GPX)
        val elapsed = currentTime - startTime

        assertTrue(sentChunks.isNotEmpty())
        // During active voice, each chunk incurs >= 150ms pacing (<= 10% duty cycle)
        assertTrue(elapsed >= 150L, "Expected elapsed time >= 150ms due to voice airtime throttling, was: $elapsed ms")
    }

    @Test
    fun testZeroDroppedVoicePacketsDuringConcurrentFileTransfer() = runTest {
        val scheduler = MeshQoSScheduler(voiceHangoverMs = 400L)
        val mock = MockMeshTransport()
        val transport = QoSAwareMeshTransport(underlyingTransport = mock, qosScheduler = scheduler)

        // Enqueue 4 low-priority bulk file transfer packets
        for (i in 1..4) {
            scheduler.enqueuePacket(
                MeshPacket.Control("peer-file", ControlType.FILE_CHUNK, byteArrayOf(i.toByte())),
                explicitPriority = QoSPriority.BULK_TELEMETRY
            )
        }

        // Enqueue 2 high-priority voice frames
        scheduler.enqueuePacket(
            MeshPacket.Audio("rider-1", 101L, 1000L, byteArrayOf(10, 20), AudioProfile.MEDIUM),
            explicitPriority = QoSPriority.ACTIVE_VOICE
        )
        scheduler.enqueuePacket(
            MeshPacket.Audio("rider-1", 102L, 1020L, byteArrayOf(30, 40), AudioProfile.MEDIUM),
            explicitPriority = QoSPriority.ACTIVE_VOICE
        )

        // Dequeue should strictly prioritize voice frames first, holding bulk chunks
        val firstOut = transport.stepDispatch()
        assertNotNull(firstOut)
        assertEquals(QoSPriority.ACTIVE_VOICE, firstOut.priority)
        assertTrue(firstOut.packet is MeshPacket.Audio)

        val secondOut = transport.stepDispatch()
        assertNotNull(secondOut)
        assertEquals(QoSPriority.ACTIVE_VOICE, secondOut.priority)
        assertTrue(secondOut.packet is MeshPacket.Audio)

        // Voice was active, so bulk is throttled/held during voice hangover
        val snap = scheduler.qosSnapshot.value
        assertEquals(0, snap.voiceQueueDepth)
        assertEquals(4, snap.bulkQueueDepth)
        assertEquals(0L, snap.totalPacketsDropped) // Zero dropped voice packets!
    }

    @Test
    fun testDeficitStarvationProtectionForRoutingControlUnderVoiceFlood() = runTest {
        val queue = MeshQoSQueue(voiceDeficitThreshold = 4) // Serve 1 control packet every 4 voice packets

        // Enqueue 10 voice frames
        for (i in 1..10) {
            queue.enqueue(
                MeshPacket.Audio("rider-voice", i.toLong(), 1000L + i * 20, byteArrayOf(i.toByte()), AudioProfile.MEDIUM),
                explicitPriority = QoSPriority.ACTIVE_VOICE
            )
        }

        // Enqueue 1 routing control packet
        queue.enqueue(
            MeshPacket.Control("rider-router", ControlType.ROUTE_REQUEST, byteArrayOf(99)),
            explicitPriority = QoSPriority.ROUTING_CONTROL
        )

        // Dequeue items
        val dequeued = mutableListOf<QoSPriority>()
        for (i in 1..6) {
            val item = queue.dequeue(allowBulk = false)
            if (item != null) dequeued.add(item.priority)
        }

        // Within 6 dequeues, at least 1 ROUTING_CONTROL packet must be serviced
        assertTrue(dequeued.contains(QoSPriority.ROUTING_CONTROL), "Routing control was starved by voice flood!")
    }

    @Test
    fun testVoiceHangoverDecayResumesNormalFileTransfer() = runTest {
        var simulatedTime = 10_000L
        val scheduler = MeshQoSScheduler(voiceHangoverMs = 400L, timeProvider = { simulatedTime })
        val fileManager = MeshFileTransferManager(qosScheduler = scheduler)

        scheduler.markVoiceActive()
        assertTrue(fileManager.isThrottled)

        // Check pacing delay during active voice
        val throttledPacing = scheduler.calculatePacingDelayMs(QoSPriority.BULK_TELEMETRY)
        assertEquals(80L, throttledPacing)

        // Advance simulated time beyond 400ms hangover
        simulatedTime += 450L
        scheduler.dequeuePacket()

        assertFalse(fileManager.isThrottled)
        val normalPacing = scheduler.calculatePacingDelayMs(QoSPriority.BULK_TELEMETRY)
        assertEquals(20L, normalPacing)
    }

    @Test
    fun testEmergencyPacketPreemptsVoiceAndFileInstantly() = runTest {
        val scheduler = MeshQoSScheduler()
        val mock = MockMeshTransport()
        val transport = QoSAwareMeshTransport(underlyingTransport = mock, qosScheduler = scheduler)

        // Enqueue bulk and voice
        scheduler.enqueuePacket(MeshPacket.Control("bulk", ControlType.FILE_CHUNK), explicitPriority = QoSPriority.BULK_TELEMETRY)
        scheduler.enqueuePacket(MeshPacket.Audio("voice", 1L, 1000L, byteArrayOf(), AudioProfile.MEDIUM), explicitPriority = QoSPriority.ACTIVE_VOICE)

        // Enqueue Emergency CRASH_ALERT
        scheduler.enqueuePacket(MeshPacket.Control("sos", ControlType.CRASH_ALERT), explicitPriority = QoSPriority.EMERGENCY_CRITICAL)

        // Emergency must dequeue first
        val item = transport.stepDispatch()
        assertNotNull(item)
        assertEquals(QoSPriority.EMERGENCY_CRITICAL, item.priority)
        val control = item.packet as MeshPacket.Control
        assertEquals(ControlType.CRASH_ALERT, control.type)

        // Emergency pacing delay must be 0ms
        assertEquals(0L, scheduler.calculatePacingDelayMs(QoSPriority.EMERGENCY_CRITICAL))
    }
}
