package org.nowni.intercom_alpha.mesh.failover

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.*
import org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import org.nowni.intercom_alpha.protocol.SignalingMessage
import org.nowni.intercom_alpha.signaling.IntercomSignalingClient
import org.nowni.intercom_alpha.signaling.SignalingConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeRouterMeshTransport : MeshTransport {
    private val peersChannel = Channel<List<Peer>>(Channel.CONFLATED)
    private val incomingAudioChannel = Channel<MeshPacket.Audio>(Channel.BUFFERED)
    private val incomingControlChannel = Channel<MeshPacket.Control>(Channel.BUFFERED)
    private val connectionStateChannel = Channel<ConnectionState>(Channel.CONFLATED)

    override val peers: ReceiveChannel<List<Peer>> = peersChannel
    override val incomingAudio: ReceiveChannel<MeshPacket.Audio> = incomingAudioChannel
    override val incomingControl: ReceiveChannel<MeshPacket.Control> = incomingControlChannel
    override val connectionState: ReceiveChannel<ConnectionState> = connectionStateChannel

    val sentPackets = mutableListOf<MeshPacket>()

    override suspend fun start(groupId: String, peerName: String, config: MeshConfig) {}
    override suspend fun stop() {}
    override suspend fun sendAudio(data: ByteArray, profile: AudioProfile) {}
    override suspend fun sendControl(type: ControlType, payload: ByteArray) {}
    override fun updateConfig(config: MeshConfig) {}

    override suspend fun sendPacket(packet: MeshPacket) {
        sentPackets.add(packet)
    }
}

class FakeRouterSignalingClient : IntercomSignalingClient() {
    val sentMessages = mutableListOf<SignalingMessage>()
    var mockConnected = true

    override suspend fun connect(
        host: String,
        port: Int,
        peerId: String,
        displayName: String,
        platform: String
    ) {
        if (mockConnected) {
            _connectionState.value = SignalingConnectionState.Connected(peerId, emptyList())
        }
    }

    override suspend fun sendMessage(message: SignalingMessage) {
        sentMessages.add(message)
    }
}

class HybridTransportRouterTest {

    @Test
    fun testBleDirectModeRoutesSolelyViaBle() = runTest {
        val transport = FakeRouterMeshTransport()
        val arbiter = MultiPathFailoverArbiter()
        val router = HybridTransportRouter(
            meshTransport = transport,
            cloudRelayBridge = null,
            failoverArbiter = arbiter
        )

        val now = 1000L
        arbiter.updateCloudStatus(connected = true, currentTimeMs = now)
        arbiter.updateMeshMetrics(peerCount = 3, avgEtx = 1.1, deliveryRatio = 0.98, currentTimeMs = now)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        val audioPacket = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 10L,
            timestamp = now,
            data = byteArrayOf(0x01, 0x02),
            profile = AudioProfile.MEDIUM
        )

        val result = router.routePacket(audioPacket, QoSPriority.ACTIVE_VOICE)

        assertTrue(result.sentViaBle)
        assertFalse(result.sentViaCloud)
        assertFalse(result.isHedged)
        assertEquals(FailoverMode.BLE_DIRECT, result.activeMode)
        assertEquals(1, transport.sentPackets.size)
        assertEquals(audioPacket, transport.sentPackets.first())
    }

    @Test
    fun testMultiPathHedgedModeDualCastsAcrossBleAndCloud() = runTest {
        val transport = FakeRouterMeshTransport()
        val arbiter = MultiPathFailoverArbiter()

        val fakeClient = FakeRouterSignalingClient()
        val cloudBridge = CloudRelayBridge(
            signalingClient = fakeClient,
            failoverArbiter = arbiter
        )
        // Simulate active cloud session
        cloudBridge.start(
            host = "127.0.0.1",
            port = 8080,
            peerId = "rider-1",
            displayName = "Lead Rider",
            platform = "android",
            roomId = "room-alpha",
            clusterId = "node-cluster"
        )

        val router = HybridTransportRouter(
            meshTransport = transport,
            cloudRelayBridge = cloudBridge,
            failoverArbiter = arbiter
        )

        var now = 1000L
        arbiter.updateCloudStatus(connected = true, currentTimeMs = now)
        // Degraded link (ETX 3.5, delivery ratio 0.65)
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 3.5, deliveryRatio = 0.65, minRssiDbm = -90, currentTimeMs = now)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, arbiter.metrics.value.currentMode)

        val audioPacket = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 42L,
            timestamp = now,
            data = byteArrayOf(0x0A, 0x0B, 0x0C),
            profile = AudioProfile.HIGH
        )

        val result = router.routePacket(audioPacket, QoSPriority.ACTIVE_VOICE)

        assertTrue(result.sentViaBle)
        assertTrue(result.sentViaCloud)
        assertTrue(result.isHedged)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, result.activeMode)
        assertEquals(1, transport.sentPackets.size)
        assertEquals(1L, arbiter.metrics.value.hedgedPacketsCount)
        assertEquals(1, fakeClient.sentMessages.filterIsInstance<SignalingMessage.MeshRelay>().size)

        // Verify receiver deduplication
        val receiverDeduplicator = PacketDeduplicator(windowMs = 5000L)
        // First arrival (e.g. from BLE) accepted
        assertTrue(receiverDeduplicator.shouldProcess(audioPacket, now = now))
        // Second arrival (e.g. from Cloud) dropped transparently
        assertFalse(receiverDeduplicator.shouldProcess(audioPacket, now = now + 40L))
    }

    @Test
    fun testCloudRelayOnlyModeSkipsBleWhenCloudUp() = runTest {
        val transport = FakeRouterMeshTransport()
        val arbiter = MultiPathFailoverArbiter()

        val fakeClient = FakeRouterSignalingClient()
        val cloudBridge = CloudRelayBridge(
            signalingClient = fakeClient,
            failoverArbiter = arbiter
        )
        cloudBridge.start(
            host = "127.0.0.1",
            port = 8080,
            peerId = "rider-1",
            displayName = "Lead Rider",
            platform = "android",
            roomId = "room-alpha",
            clusterId = "node-cluster"
        )

        val router = HybridTransportRouter(
            meshTransport = transport,
            cloudRelayBridge = cloudBridge,
            failoverArbiter = arbiter
        )

        val now = 1000L
        arbiter.updateCloudStatus(connected = true, currentTimeMs = now)
        // BLE peers dropped to 0 (convoy split)
        arbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = now)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, arbiter.metrics.value.currentMode)

        val audioPacket = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 100L,
            timestamp = now,
            data = byteArrayOf(0x20),
            profile = AudioProfile.LOW
        )

        val result = router.routePacket(audioPacket, QoSPriority.ACTIVE_VOICE)

        assertFalse(result.sentViaBle)
        assertTrue(result.sentViaCloud)
        assertFalse(result.isHedged)
        assertEquals(FailoverMode.CLOUD_RELAY_ONLY, result.activeMode)
        assertEquals(0, transport.sentPackets.size)
        assertEquals(1, fakeClient.sentMessages.filterIsInstance<SignalingMessage.MeshRelay>().size)
    }

    @Test
    fun testRestrictedOfflineModeFallsBackToBestEffortBle() = runTest {
        val transport = FakeRouterMeshTransport()
        val arbiter = MultiPathFailoverArbiter()
        val router = HybridTransportRouter(
            meshTransport = transport,
            cloudRelayBridge = null,
            failoverArbiter = arbiter
        )

        val now = 1000L
        arbiter.updateCloudStatus(connected = false, currentTimeMs = now)
        arbiter.updateMeshMetrics(peerCount = 0, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = now)
        assertEquals(FailoverMode.RESTRICTED_OFFLINE, arbiter.metrics.value.currentMode)

        val packet = MeshPacket.Control(
            senderId = "rider-1",
            type = ControlType.PING,
            payload = byteArrayOf(0x01)
        )

        val result = router.routePacket(packet, QoSPriority.ROUTING_CONTROL)

        assertTrue(result.sentViaBle)
        assertFalse(result.sentViaCloud)
        assertFalse(result.isHedged)
        assertEquals(1, transport.sentPackets.size)
    }

    @Test
    fun testQoSSchedulerIntegration() = runTest {
        val transport = FakeRouterMeshTransport()
        val arbiter = MultiPathFailoverArbiter()
        val scheduler = MeshQoSScheduler()
        val router = HybridTransportRouter(
            meshTransport = transport,
            cloudRelayBridge = null,
            failoverArbiter = arbiter,
            qosScheduler = scheduler
        )

        val now = 1000L
        arbiter.updateMeshMetrics(peerCount = 1, avgEtx = 1.0, deliveryRatio = 1.0, currentTimeMs = now)

        val packet = MeshPacket.Audio(
            senderId = "rider-1",
            sequence = 5L,
            timestamp = now,
            data = byteArrayOf(0x10),
            profile = AudioProfile.MEDIUM
        )

        router.routePacket(packet, QoSPriority.ACTIVE_VOICE)

        // Should be queued in QoS scheduler under ACTIVE_VOICE queue
        assertEquals(1, scheduler.qosSnapshot.value.voiceQueueDepth)
        val dequeued = scheduler.dequeuePacket()
        assertEquals(packet, dequeued?.packet)
    }

    @Test
    fun testCloudRelayBridgeHonorsFallbackOnlyModeWithArbiter() = runTest {
        val fakeClient = FakeRouterSignalingClient()
        val arbiter = MultiPathFailoverArbiter()
        val bridge = CloudRelayBridge(
            signalingClient = fakeClient,
            failoverArbiter = arbiter
        )
        bridge.start(
            host = "127.0.0.1",
            port = 8080,
            peerId = "node-1",
            displayName = "Tester",
            platform = "jvm",
            roomId = "room-1",
            clusterId = "cluster-1"
        )
        bridge.setRelayMode(CloudRelayMode.FALLBACK_ONLY)

        val packet = MeshPacket.Audio(
            senderId = "node-1",
            sequence = 1L,
            timestamp = 1000L,
            data = byteArrayOf(0x01),
            profile = AudioProfile.MEDIUM
        )

        // When in BLE_DIRECT, forwardToCloud should return false
        val now = 1000L
        arbiter.updateCloudStatus(connected = true, currentTimeMs = now)
        arbiter.updateMeshMetrics(peerCount = 3, avgEtx = 1.1, deliveryRatio = 0.95, currentTimeMs = now)
        assertEquals(FailoverMode.BLE_DIRECT, arbiter.metrics.value.currentMode)

        val sentInDirect = bridge.forwardToCloud(packet)
        assertFalse(sentInDirect)

        // When in MULTI_PATH_HEDGED, forwardToCloud should return true
        arbiter.updateMeshMetrics(peerCount = 2, avgEtx = 3.5, deliveryRatio = 0.60, currentTimeMs = now + 1000L)
        assertEquals(FailoverMode.MULTI_PATH_HEDGED, arbiter.metrics.value.currentMode)

        val sentInHedged = bridge.forwardToCloud(packet)
        assertTrue(sentInHedged)
    }
}
