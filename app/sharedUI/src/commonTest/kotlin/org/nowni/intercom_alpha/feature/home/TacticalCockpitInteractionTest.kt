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
 * End-to-End Interaction Simulation Test for the Zero-Scroll Tactical Cockpit (Sprint 31 - ICA-3104).
 * Verifies PTT actuator engagement, rotary channel change detents, guarded SOS flip-cover
 * state transitions, sensory audio toggles, and glove-friendly spatial radar zoom cycling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TacticalCockpitInteractionTest {

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
    fun testPttActuatorEngagementCycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTransmitting, "Initial state must not be transmitting")

        // 1. Press and hold master PTT actuator
        viewModel.onAction(HomeUiAction.StartTransmitting)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isTransmitting, "Cockpit must reflect active transmission during PTT press")

        // 2. Release PTT actuator
        viewModel.onAction(HomeUiAction.StopTransmitting)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isTransmitting, "Cockpit must cease transmission upon PTT release")
    }

    @Test
    fun testRotaryChannelDialDetents() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.channelState.activeChannelId, "Default channel should be 1")

        // 1. Rotate dial to Channel 4
        viewModel.onAction(HomeUiAction.SelectChannel(4))
        testScheduler.advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.channelState.activeChannelId, "Active channel must be updated to 4")

        // 2. Enable Dual Watch on Channel 7
        viewModel.onAction(HomeUiAction.ToggleDualWatch(7))
        testScheduler.advanceUntilIdle()
        assertTrue(7 in viewModel.uiState.value.channelState.monitoredChannelIds, "Channel 7 must be in monitored channels")

        // 3. Toggle Dual Watch off for Channel 7
        viewModel.onAction(HomeUiAction.ToggleDualWatch(7))
        testScheduler.advanceUntilIdle()
        assertFalse(7 in viewModel.uiState.value.channelState.monitoredChannelIds, "Channel 7 must be removed from monitored channels")
    }

    @Test
    fun testGuardedSosFlipCoverTransitions() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEmergencyActive, "SOS must initially be inactive")

        // 1. Flip open safety cover and trigger SOS
        viewModel.onAction(HomeUiAction.TriggerSos)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEmergencyActive, "Emergency preemption banner must be active after SOS trigger")

        // 2. Clear emergency state
        viewModel.onAction(HomeUiAction.ClearEmergency)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isEmergencyActive, "Emergency must clear when dismissed by rider")
    }

    @Test
    fun testSensoryVoxLockToggle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isVoxLocked, "VOX lock must initially be off")

        // 1. Toggle VOX lock ON
        viewModel.onAction(HomeUiAction.ToggleVoxLock)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isVoxLocked, "VOX lock must be active")

        // 2. Toggle VOX lock OFF
        viewModel.onAction(HomeUiAction.ToggleVoxLock)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isVoxLocked, "VOX lock must be deactivated")
    }

    @Test
    fun testGloveFriendlyRadarZoomCycle() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        val initialZoom = viewModel.uiState.value.trailMapSnapshot.activeRangeZoom
        assertEquals(RadarRangeZoom.MEDIUM, initialZoom, "Initial radar zoom range should be MEDIUM (1.5km)")

        // 1. Cycle to WIDE
        viewModel.onAction(HomeUiAction.CycleRadarZoom)
        testScheduler.advanceUntilIdle()
        assertEquals(RadarRangeZoom.WIDE, viewModel.uiState.value.trailMapSnapshot.activeRangeZoom)

        // 2. Cycle to CLOSE
        viewModel.onAction(HomeUiAction.CycleRadarZoom)
        testScheduler.advanceUntilIdle()
        assertEquals(RadarRangeZoom.CLOSE, viewModel.uiState.value.trailMapSnapshot.activeRangeZoom)

        // 3. Cycle back to MEDIUM
        viewModel.onAction(HomeUiAction.CycleRadarZoom)
        testScheduler.advanceUntilIdle()
        assertEquals(RadarRangeZoom.MEDIUM, viewModel.uiState.value.trailMapSnapshot.activeRangeZoom)
    }

    @Test
    fun testZeroScrollOverlaySheetTransitions() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailSheet)

        // 1. Open Trail Cockpit Sheet
        viewModel.onAction(HomeUiAction.ToggleTrailSheet(true))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showTrailSheet, "Trail cockpit sheet must be presented")

        // 2. Dismiss Trail Cockpit Sheet
        viewModel.onAction(HomeUiAction.ToggleTrailSheet(false))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showTrailSheet, "Trail cockpit sheet must be dismissed")
    }
}
