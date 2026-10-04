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
}
