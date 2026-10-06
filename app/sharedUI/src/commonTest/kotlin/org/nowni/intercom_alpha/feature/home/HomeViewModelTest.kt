package org.nowni.intercom_alpha.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.mesh.Peer
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.mesh.RepeaterBeaconPayload
import org.nowni.intercom_alpha.mesh.RepeaterDutyCycleMode
import org.nowni.intercom_alpha.mesh.RepeaterPongPayload
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import org.nowni.intercom_alpha.text.CannedMessageCode
import org.nowni.intercom_alpha.text.MeshTextManager
import org.nowni.intercom_alpha.text.MeshTextWireFrame
import org.nowni.intercom_alpha.text.TextWireType
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var channelManager: ChannelManager
    private lateinit var proximityRadar: ProximityRadar
    private lateinit var convoyGpsTracker: ConvoyGpsTracker
    private lateinit var meshTextManager: MeshTextManager
    private lateinit var priorityManager: PriorityBroadcastManager
    private lateinit var blackboxRecorder: EmergencyBlackboxRecorder
    private lateinit var bitrateController: AdaptiveBitrateController
    private lateinit var hardwareBridge: NoOpIntercomHardwareBridge
    private lateinit var viewModel: HomeViewModel

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        channelManager = ChannelManager()
        proximityRadar = ProximityRadar()
        convoyGpsTracker = ConvoyGpsTracker("test-peer", "Test Rider")
        meshTextManager = MeshTextManager("test-peer", "Test Rider")
        priorityManager = PriorityBroadcastManager()
        blackboxRecorder = EmergencyBlackboxRecorder()
        bitrateController = AdaptiveBitrateController()
        hardwareBridge = NoOpIntercomHardwareBridge()

        viewModel = HomeViewModel(
            channelManager = channelManager,
            proximityRadar = proximityRadar,
            convoyGpsTracker = convoyGpsTracker,
            meshTextManager = meshTextManager,
            priorityManager = priorityManager,
            blackboxRecorder = blackboxRecorder,
            bitrateController = bitrateController,
            hardwareBridge = hardwareBridge
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals("Rider Club Alpha", state.groupName)
        assertEquals(1, state.channelState.activeChannelId)
        assertFalse(state.isEmergencyActive)
        assertEquals(0, state.unreadTextCount)
        assertFalse(state.showMessagesDialog)
    }

    @Test
    fun testChannelSelectionAndDualWatchStateFlow() = runTest(testDispatcher) {
        // Select channel 4
        viewModel.onAction(HomeUiAction.SelectChannel(4))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.channelState.activeChannelId)

        // Toggle Dual-Watch for channel 2
        viewModel.onAction(HomeUiAction.ToggleDualWatch(2))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.channelState.monitoredChannelIds.contains(2))

        // Toggle Dual-Watch off for channel 2
        viewModel.onAction(HomeUiAction.ToggleDualWatch(2))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.channelState.monitoredChannelIds.contains(2))

        // Toggle Scan Mode
        assertFalse(viewModel.uiState.value.channelState.isScanMode)
        viewModel.onAction(HomeUiAction.ToggleScanMode)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.channelState.isScanMode)
    }

    @Test
    fun testTextMessagingAndDialogFlow() = runTest(testDispatcher) {
        // Send canned hazard
        viewModel.onAction(HomeUiAction.SendCannedHazard(CannedMessageCode.FUEL_LOW))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)
        assertEquals(CannedMessageCode.FUEL_LOW, viewModel.uiState.value.messages.first().cannedCode)

        // Mark read
        viewModel.onAction(HomeUiAction.MarkMessagesAsRead)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.unreadTextCount)

        // Send custom text
        viewModel.onAction(HomeUiAction.SendCustomText("Need a break in 5 miles"))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertEquals("Need a break in 5 miles", viewModel.uiState.value.messages.last().content)

        // Open/Close messages dialog
        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMessagesDialog)

        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showMessagesDialog)
    }

    @Test
    fun testEmergencySosTriggerAndPreemption() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isEmergencyActive)

        // Trigger SOS
        viewModel.onAction(HomeUiAction.TriggerSos)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmergencyActive)

        // Clear Emergency
        viewModel.onAction(HomeUiAction.ClearEmergency)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmergencyActive)
    }

    @Test
    fun testPttTransmissionAndProfileSelection() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isTransmitting)

        viewModel.onAction(HomeUiAction.StartTransmitting)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTransmitting)
        assertTrue(hardwareBridge.isTransmitting.value)

        viewModel.onAction(HomeUiAction.StopTransmitting)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTransmitting)
        assertFalse(hardwareBridge.isTransmitting.value)

        viewModel.onAction(HomeUiAction.SelectAudioProfile(AudioProfile.ULTRA_LOW))
        advanceUntilIdle()
        assertEquals(AudioProfile.ULTRA_LOW, viewModel.uiState.value.activeProfile)
    }

    @Test
    fun testHardwareBridgeLivePeersAndLevelsStreaming() = runTest(testDispatcher) {
        assertEquals(0, viewModel.uiState.value.peers.size)

        // 1. Simulate real BLE mesh peers discovery
        val livePeers = listOf(
            Peer(id = "peer-real-1", name = "Captain Alpha", rssi = -58, lastSeen = 1000L, isConnected = true, audioProfile = AudioProfile.MEDIUM),
            Peer(id = "peer-real-2", name = "Sweep Bravo", rssi = -72, lastSeen = 1000L, isConnected = true, audioProfile = AudioProfile.MEDIUM)
        )
        hardwareBridge.updatePeers(livePeers)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.peers.size)
        assertEquals("Captain Alpha", viewModel.uiState.value.peers[0].name)
        assertEquals(2, viewModel.uiState.value.radarSnapshot.targets.size)

        // 2. Simulate live microphone and speaker RMS audio levels
        hardwareBridge.updateLevels(input = 0.82f, output = 0.44f)
        advanceUntilIdle()

        assertEquals(0.82f, viewModel.uiState.value.inputLevel)
        assertEquals(0.44f, viewModel.uiState.value.outputLevel)
    }

    @Test
    fun testHardwareBridgeOtaControlPacketIngestion() = runTest(testDispatcher) {
        // 1. Ingest incoming remote text message over OTA bridge
        val incomingWire = MeshTextWireFrame(
            wireType = TextWireType.TEXT_PAYLOAD,
            messageId = "ota-msg-1",
            senderId = "peer-scout",
            senderName = "Scout Lead",
            cannedCode = CannedMessageCode.HAZARD_ROAD,
            content = "Caution: Hazard in Road",
            timestamp = 1000L
        )
        hardwareBridge.simulateIncomingText(incomingWire)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.messages.size)
        assertEquals(1, viewModel.uiState.value.unreadTextCount)
        assertEquals("Scout Lead", viewModel.uiState.value.messages.first().senderName)

        // 2. Ingest incoming local GPS coordinate from Android LocationManager
        val loc = GpsCoordinate(
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 15.0,
            speedKmh = 65.0f,
            headingDegrees = 180.0f,
            accuracyMeters = 3.5f,
            timestampMs = 2000L
        )
        hardwareBridge.simulateLocalGps(loc)
        advanceUntilIdle()

        val convoySnapshot = viewModel.uiState.value.convoySnapshot
        assertNotNull(convoySnapshot.localCoordinate)
        assertEquals(37.7749, convoySnapshot.localCoordinate?.latitude)
    }

    @Test
    fun testSprint14TelemetryAndVoxLock() = runTest(testDispatcher) {
        // 1. Helmet accessory state sync
        val dummyAccessory = org.nowni.intercom_alpha.headset.HelmetAccessoryState(
            brand = org.nowni.intercom_alpha.headset.HelmetBrand.CARDO,
            deviceName = "Cardo PACKTALK EDGE",
            isConnected = true,
            batteryPercent = 85
        )
        hardwareBridge.simulateHelmetAccessoryState(dummyAccessory)
        advanceUntilIdle()

        assertEquals("Cardo PACKTALK EDGE", viewModel.uiState.value.accessoryState.deviceName)
        assertEquals(85, viewModel.uiState.value.accessoryState.batteryPercent)
        assertTrue(viewModel.uiState.value.accessoryState.isConnected)

        // 2. Cloud bridge state sync
        hardwareBridge.simulateCloudBridgeConnected(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCloudBridgeConnected)

        // 3. Vox Lock action
        assertFalse(viewModel.uiState.value.isVoxLocked)
        viewModel.onAction(HomeUiAction.ToggleVoxLock)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isVoxLocked)
        assertTrue(viewModel.uiState.value.isTransmitting)

        viewModel.onAction(HomeUiAction.ToggleVoxLock)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isVoxLocked)
        assertFalse(viewModel.uiState.value.isTransmitting)
    }

    @Test
    fun testRepeaterBeaconIngestionAndDiagnosticPingFlow() = runTest(testDispatcher) {
        assertNull(viewModel.uiState.value.latestRepeaterBeacon)
        assertNull(viewModel.uiState.value.repeaterPingLatencyMs)
        assertFalse(viewModel.uiState.value.isPingingRepeater)

        // 1. Simulate incoming basecamp beacon
        val beacon = RepeaterBeaconPayload(
            repeaterId = "basecamp-stationary-1",
            repeaterName = "Basecamp Alpha",
            batteryPercent = 94,
            activeNeighborCount = 3,
            packetsForwarded = 450L,
            uptimeSeconds = 3600L,
            dutyCycleMode = RepeaterDutyCycleMode.CONTINUOUS,
            isSolarPowered = true
        )
        hardwareBridge.simulateIncomingRepeaterBeacon(beacon)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.latestRepeaterBeacon)
        assertEquals("Basecamp Alpha", viewModel.uiState.value.latestRepeaterBeacon?.repeaterName)
        assertEquals(94, viewModel.uiState.value.latestRepeaterBeacon?.batteryPercent)
        assertTrue(viewModel.uiState.value.latestRepeaterBeacon?.isSolarPowered == true)

        // 2. Trigger diagnostic ping action
        viewModel.onAction(HomeUiAction.TriggerRepeaterPing)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isPingingRepeater)

        // 3. Simulate incoming diagnostic pong response
        val now = org.nowni.intercom_alpha.currentTimeMillis()
        val pong = RepeaterPongPayload(
            repeaterId = "basecamp-stationary-1",
            pingSenderId = "local-rider",
            pingTimestampMs = now - 15L,
            pongTimestampMs = now - 5L,
            batteryPercent = 94,
            packetsForwarded = 451L
        )
        hardwareBridge.simulateIncomingRepeaterPong(pong)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPingingRepeater)
        assertNotNull(viewModel.uiState.value.repeaterPingLatencyMs)
        assertTrue((viewModel.uiState.value.repeaterPingLatencyMs ?: 0L) >= 0L)
    }

    @Test
    fun testTrailMapSnapshotCoordinationAndActions() = runTest(testDispatcher) {
        val initialSnap = viewModel.uiState.value.trailMapSnapshot
        assertNotNull(initialSnap)
        assertEquals(org.nowni.intercom_alpha.map.RadarRangeZoom.MEDIUM, initialSnap.activeRangeZoom)
        assertTrue(initialSnap.isOverlayEnabled)
        assertFalse(viewModel.uiState.value.showTrailSheet)

        // 1. Toggle trail sheet dialog
        viewModel.onAction(HomeUiAction.ToggleTrailSheet(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showTrailSheet)

        // 2. Cycle radar zoom
        viewModel.onAction(HomeUiAction.CycleRadarZoom)
        advanceUntilIdle()
        assertEquals(org.nowni.intercom_alpha.map.RadarRangeZoom.WIDE, viewModel.uiState.value.trailMapSnapshot.activeRangeZoom)

        // 3. Toggle overlay visibility
        viewModel.onAction(HomeUiAction.ToggleTrailOverlay(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.trailMapSnapshot.isOverlayEnabled)

        // 4. Ingest local GPS coordinate
        val loc = GpsCoordinate(
            latitude = 45.0,
            longitude = 10.0,
            altitudeMeters = 800.0,
            speedKmh = 40.0f,
            headingDegrees = 45.0f,
            accuracyMeters = 5.0f,
            timestampMs = 5000L
        )
        hardwareBridge.simulateLocalGps(loc)
        advanceUntilIdle()
        assertEquals(800.0, viewModel.uiState.value.trailMapSnapshot.currentElevationMeters)

        // 5. Load sample alpine trail
        viewModel.onAction(HomeUiAction.LoadSampleTrail)
        advanceUntilIdle()
        val updatedSnap = viewModel.uiState.value.trailMapSnapshot
        assertNotNull(updatedSnap.activeTrail)
        assertEquals("Black Bear Pass Trail", updatedSnap.activeTrail?.name)
        assertTrue(updatedSnap.projectedWaypoints.isNotEmpty())
    }
}
