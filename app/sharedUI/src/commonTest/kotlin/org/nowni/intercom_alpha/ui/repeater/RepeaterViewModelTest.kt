package org.nowni.intercom_alpha.ui.repeater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.RepeaterDutyCycleMode
import org.nowni.intercom_alpha.mesh.RepeaterPongPayload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RepeaterViewModelTest {

    private lateinit var repeaterService: MeshRepeaterService
    private lateinit var hardwareBridge: NoOpIntercomHardwareBridge
    private lateinit var viewModel: RepeaterViewModel

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repeaterService = MeshRepeaterService(
            repeaterId = "test-repeater-01",
            repeaterName = "Peak Relay Alpha",
            batteryPercentProvider = { 94 }
        )
        hardwareBridge = NoOpIntercomHardwareBridge()

        viewModel = RepeaterViewModel(
            repeaterService = repeaterService,
            hardwareBridge = hardwareBridge
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiState() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals("test-repeater-01", state.repeaterId)
        assertEquals("Peak Relay Alpha", state.repeaterName)
        assertFalse(state.isRunning)
        assertTrue(state.isStationary)
        assertTrue(state.isSolarPowered)
        assertEquals(94, state.batteryPercent)
        assertEquals(RepeaterDutyCycleMode.CONTINUOUS, state.dutyCycleMode)
        assertEquals(0L, state.packetsForwarded)
        assertEquals(0, state.activeNeighborCount)
        assertFalse(state.isNightStandbyLocked)
        assertFalse(state.isPinging)
    }

    @Test
    fun testStartRepeaterBypassesAudioAndEngagesBridge() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(RepeaterUiAction.StartRepeater)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRunning, "Repeater should be marked running")
        assertTrue(hardwareBridge.isRepeaterModeActive.value, "Bridge should activate repeater mode (bypassing audio)")
        assertTrue(viewModel.uiState.value.statusMessage.contains("Repeater Active"))
    }

    @Test
    fun testStopRepeaterRestoresIntercomAudioPipelines() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.startRepeater()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.onAction(RepeaterUiAction.StopRepeater)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRunning, "Repeater should be stopped")
        assertFalse(hardwareBridge.isRepeaterModeActive.value, "Bridge should deactivate repeater mode (restoring audio)")
        assertEquals("Standby Ready", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testSetDutyCycleModeUpdatesStateAndService() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(RepeaterUiAction.SetDutyCycle(RepeaterDutyCycleMode.POWER_SAVER))
        advanceUntilIdle()

        assertEquals(RepeaterDutyCycleMode.POWER_SAVER, viewModel.uiState.value.dutyCycleMode)
        assertEquals(RepeaterDutyCycleMode.POWER_SAVER, repeaterService.dutyCycleMode)

        viewModel.onAction(RepeaterUiAction.SetDutyCycle(RepeaterDutyCycleMode.EXTREME_SURVIVAL))
        advanceUntilIdle()

        assertEquals(RepeaterDutyCycleMode.EXTREME_SURVIVAL, viewModel.uiState.value.dutyCycleMode)
        assertEquals(RepeaterDutyCycleMode.EXTREME_SURVIVAL, repeaterService.dutyCycleMode)
    }

    @Test
    fun testEmitBeaconIncrementsCounter() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(RepeaterUiAction.EmitBeacon)
        advanceUntilIdle()

        assertEquals(1L, viewModel.uiState.value.beaconEmissionCount)
        assertTrue(viewModel.uiState.value.statusMessage.contains("Beacon Broadcast Emitted"))

        viewModel.onAction(RepeaterUiAction.EmitBeacon)
        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.beaconEmissionCount)
    }

    @Test
    fun testTriggerDiagnosticPingAndHandlePongResponse() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(RepeaterUiAction.TriggerDiagnosticPing)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPinging, "UI state should flag active ping")
        assertTrue(viewModel.uiState.value.statusMessage.contains("Sending Diagnostic Ping"))

        // Simulate incoming pong response from remote repeater
        val simulatedPong = RepeaterPongPayload(
            repeaterId = "test-repeater-01",
            pingSenderId = "test-repeater-01",
            pingTimestampMs = 1000L,
            pongTimestampMs = 1018L,
            batteryPercent = 90,
            packetsForwarded = 42L
        )

        hardwareBridge.simulateIncomingRepeaterPong(simulatedPong)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPinging, "Ping flag should reset after pong")
        assertNotNull(viewModel.uiState.value.lastPingLatencyMs, "Round-trip latency should be recorded")
        assertTrue(viewModel.uiState.value.statusMessage.contains("Diagnostic Pong"))
    }

    @Test
    fun testToggleNightStandbyLock() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNightStandbyLocked)

        viewModel.onAction(RepeaterUiAction.ToggleNightStandby(true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isNightStandbyLocked)

        viewModel.onAction(RepeaterUiAction.ToggleNightStandby(false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNightStandbyLocked)
    }

    @Test
    fun testExternalBridgeActivationSyncsViewModel() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRunning)

        // Simulate external bridge (e.g. system notification or physical switch) activating repeater mode
        hardwareBridge.setRepeaterModeActive(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRunning, "ViewModel should automatically mirror external bridge activation")
    }
}
