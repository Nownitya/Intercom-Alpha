package org.nowni.intercom_alpha.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.CannedMessageCode
import org.nowni.intercom_alpha.text.MeshTextManager
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

        viewModel = HomeViewModel(
            channelManager = channelManager,
            proximityRadar = proximityRadar,
            convoyGpsTracker = convoyGpsTracker,
            meshTextManager = meshTextManager,
            priorityManager = priorityManager,
            blackboxRecorder = blackboxRecorder,
            bitrateController = bitrateController
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

        // Send custom message
        viewModel.onAction(HomeUiAction.SendCustomText("Taking next exit for gas"))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.messages.size)

        // Toggle messages dialog
        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMessagesDialog)
        assertEquals(0, viewModel.uiState.value.unreadTextCount)

        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(false))
        assertFalse(viewModel.uiState.value.showMessagesDialog)
    }

    @Test
    fun testEmergencyPreemptionAndReset() = runTest(testDispatcher) {
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
        assertTrue(viewModel.uiState.value.isTransmitting)

        viewModel.onAction(HomeUiAction.StopTransmitting)
        assertFalse(viewModel.uiState.value.isTransmitting)

        viewModel.onAction(HomeUiAction.SelectAudioProfile(AudioProfile.ULTRA_LOW))
        advanceUntilIdle()
        assertEquals(AudioProfile.ULTRA_LOW, viewModel.uiState.value.activeProfile)
    }
}
