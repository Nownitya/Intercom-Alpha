package org.nowni.intercom_alpha.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.feature.home.state.HomeUiAction
import org.nowni.intercom_alpha.map.FileTransferType
import org.nowni.intercom_alpha.map.RadarRangeZoom
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.MeshTextManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-End Sub-Screen & Overlay Interaction Test (Sprint 32 - ICA-3204).
 * Verifies non-blocking state transitions for Offline Trail Cockpit, Trail File Manager,
 * Convoy Message Transcript, and Tactile Diagnostics Telemetry HUD sheets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubScreenInteractionTest {

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
        convoyGpsTracker = ConvoyGpsTracker("rider-alpha-01", "Squad Lead")
        meshTextManager = MeshTextManager("rider-alpha-01", "Squad Lead")
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
    fun testOfflineTrailSheetLifecycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailSheet, "Trail sheet must start dismissed")

        // 1. Open Offline Trail Cockpit Sheet
        viewModel.onAction(HomeUiAction.ToggleTrailSheet(true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showTrailSheet, "Trail sheet must be open")

        // 2. Cycle radar range zoom while trail sheet is active
        viewModel.onAction(HomeUiAction.CycleRadarZoom)
        testScheduler.advanceUntilIdle()

        // 3. Toggle overlay state
        val initialOverlay = viewModel.uiState.value.trailMapSnapshot.isOverlayEnabled
        viewModel.onAction(HomeUiAction.ToggleTrailOverlay(!initialOverlay))
        testScheduler.advanceUntilIdle()
        assertEquals(!initialOverlay, viewModel.uiState.value.trailMapSnapshot.isOverlayEnabled)

        // 4. Dismiss Trail sheet
        viewModel.onAction(HomeUiAction.ToggleTrailSheet(false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailSheet, "Trail sheet must dismiss cleanly")
    }

    @Test
    fun testTrailFileManagerSheetLifecycleAndTransfer() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailManagerSheet, "File manager sheet must start dismissed")

        // 1. Open Trail File Manager Sheet
        viewModel.onAction(HomeUiAction.ToggleTrailManagerSheet(true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showTrailManagerSheet, "File manager sheet must be open")

        // 2. Import sample route text
        val sampleGpx = """<gpx version="1.1"><trk><name>Test Pass</name></trk></gpx>"""
        viewModel.onAction(HomeUiAction.ImportTrailText(sampleGpx, "Test_Pass.gpx", FileTransferType.GPX))
        testScheduler.advanceUntilIdle()

        // 3. Trigger beam active trail
        viewModel.onAction(HomeUiAction.BeamActiveTrail(FileTransferType.GPX))
        testScheduler.advanceUntilIdle()

        // 4. Dismiss File Manager sheet
        viewModel.onAction(HomeUiAction.ToggleTrailManagerSheet(false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailManagerSheet, "File manager sheet must dismiss cleanly")
    }

    @Test
    fun testMessageTranscriptDialogLifecycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showMessagesDialog, "Messages dialog must start dismissed")

        // 1. Open messages dialog
        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMessagesDialog, "Messages dialog must be open")
        assertEquals(0, viewModel.uiState.value.unreadTextCount, "Opening messages must mark all as read")

        // 2. Send custom tactical text
        viewModel.onAction(HomeUiAction.SendCustomText("Hazard on corner 4, loose gravel"))
        testScheduler.advanceUntilIdle()

        // 3. Dismiss dialog
        viewModel.onAction(HomeUiAction.ToggleMessagesDialog(false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showMessagesDialog, "Messages dialog must dismiss cleanly")
    }

    @Test
    fun testTactileDiagnosticsSheetLifecycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDiagnosticsSheet, "Diagnostics sheet must start dismissed")

        // 1. Open Tactile Diagnostics Sheet
        viewModel.onAction(HomeUiAction.ToggleDiagnosticsSheet(true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDiagnosticsSheet, "Diagnostics sheet must be open")

        // 2. Dismiss Tactile Diagnostics Sheet
        viewModel.onAction(HomeUiAction.ToggleDiagnosticsSheet(false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDiagnosticsSheet, "Diagnostics sheet must dismiss cleanly")
    }

    @Test
    fun testAdaptiveBitrateControlsLifecycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        // 1. Initial state
        assertEquals(org.nowni.intercom_alpha.common.AudioProfile.MEDIUM, viewModel.uiState.value.bitrateTelemetry.activeProfile)
        assertFalse(viewModel.uiState.value.bitrateTelemetry.isPinned)

        // 2. Pin to ULTRA_LOW
        viewModel.onAction(HomeUiAction.PinAudioProfile(org.nowni.intercom_alpha.common.AudioProfile.ULTRA_LOW))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.bitrateTelemetry.isPinned)
        assertEquals(org.nowni.intercom_alpha.common.AudioProfile.ULTRA_LOW, viewModel.uiState.value.bitrateTelemetry.activeProfile)

        // 3. Release pin
        viewModel.onAction(HomeUiAction.PinAudioProfile(null))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.bitrateTelemetry.isPinned)

        // 4. Change cluster policy
        viewModel.onAction(HomeUiAction.SetClusterPolicy(org.nowni.intercom_alpha.audio.ClusterPolicy.CLUSTER_AVERAGE))
        testScheduler.advanceUntilIdle()
        assertEquals(org.nowni.intercom_alpha.audio.ClusterPolicy.CLUSTER_AVERAGE, viewModel.uiState.value.bitrateTelemetry.clusterPolicy)
    }
}

