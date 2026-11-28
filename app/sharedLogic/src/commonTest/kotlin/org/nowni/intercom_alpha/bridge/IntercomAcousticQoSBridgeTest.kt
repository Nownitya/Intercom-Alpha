package org.nowni.intercom_alpha.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.audio.AcousticWakeWordEngine
import org.nowni.intercom_alpha.audio.NoiseSuppressionMode
import org.nowni.intercom_alpha.audio.SpectralNoiseSnapshot
import org.nowni.intercom_alpha.audio.SpectralNoiseSuppressor
import org.nowni.intercom_alpha.audio.VoxState
import org.nowni.intercom_alpha.audio.VoxStateSnapshot
import org.nowni.intercom_alpha.audio.WakeWordType
import org.nowni.intercom_alpha.mesh.qos.AirtimeCongestionGovernor
import org.nowni.intercom_alpha.mesh.qos.AirtimeCongestionSnapshot
import org.nowni.intercom_alpha.mesh.qos.ChannelCongestionLevel
import org.nowni.intercom_alpha.mesh.qos.FecRedundancyLevel
import org.nowni.intercom_alpha.mesh.qos.QoSPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IntercomAcousticQoSBridgeTest {

    @Test
    fun testSpectralNoiseSuppressionBridgeWiring() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial State
        val initialNoise = bridge.spectralNoiseSnapshot.first()
        assertEquals(NoiseSuppressionMode.AGGRESSIVE, initialNoise.mode)
        assertFalse(initialNoise.isSpeechPresent)

        // 2. Direct Update
        val updatedSnapshot = SpectralNoiseSnapshot(
            mode = NoiseSuppressionMode.EXTREME_ALPINE,
            attenuationDb = 18.5f,
            isSpeechPresent = true,
            estimatedSnrDb = 14.2f,
            inputRmsDb = -22.0f,
            outputRmsDb = -28.0f
        )
        bridge.updateSpectralNoiseSnapshot(updatedSnapshot)
        assertEquals(NoiseSuppressionMode.EXTREME_ALPINE, bridge.spectralNoiseSnapshot.value.mode)
        assertEquals(18.5f, bridge.spectralNoiseSnapshot.value.attenuationDb)
        assertTrue(bridge.spectralNoiseSnapshot.value.isSpeechPresent)

        // 3. Mode Change
        bridge.setNoiseSuppressionMode(NoiseSuppressionMode.MILD)
        assertEquals(NoiseSuppressionMode.MILD, bridge.spectralNoiseSnapshot.value.mode)

        bridge.setNoiseSuppressionMode(NoiseSuppressionMode.OFF)
        assertEquals(NoiseSuppressionMode.OFF, bridge.spectralNoiseSnapshot.value.mode)
    }

    @Test
    fun testVoxStateAndSensitivityBridgeWiring() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial VOX State
        val initialVox = bridge.voxStateSnapshot.first()
        assertEquals(VoxState.IDLE, initialVox.state)
        assertFalse(initialVox.isPttActive)

        // 2. Direct Snapshot Update
        val triggeredVox = VoxStateSnapshot(
            state = VoxState.TRIGGERED,
            lastDetectedKeyword = WakeWordType.HEY_ALPHA,
            confidence = 0.88f,
            isPttActive = true,
            energyDb = -24.0f
        )
        bridge.updateVoxStateSnapshot(triggeredVox)
        assertEquals(VoxState.TRIGGERED, bridge.voxStateSnapshot.value.state)
        assertEquals(WakeWordType.HEY_ALPHA, bridge.voxStateSnapshot.value.lastDetectedKeyword)
        assertTrue(bridge.voxStateSnapshot.value.isPttActive)
        assertEquals(0.88f, bridge.voxStateSnapshot.value.confidence)

        // 3. Sensitivity Tuning
        bridge.setVoxSensitivity(-32.0f)
        assertEquals(-32.0f, bridge.voxStateSnapshot.value.energyDb)
    }

    @Test
    fun testAirtimeCongestionSnapshotBridgeWiring() = runTest {
        val bridge = NoOpIntercomHardwareBridge()

        // 1. Initial Congestion Snapshot
        val initialCongestion = bridge.airtimeCongestionSnapshot.first()
        assertEquals(ChannelCongestionLevel.UNCONGESTED, initialCongestion.congestionLevel)
        assertEquals(0.0f, initialCongestion.channelBusyRatio)

        // 2. Direct Congestion Update
        val congestedSnapshot = AirtimeCongestionSnapshot(
            channelBusyRatio = 0.82f,
            estimatedCollisionRate = 0.12f,
            congestionLevel = ChannelCongestionLevel.HIGH,
            fecRedundancy = FecRedundancyLevel.MEDIUM,
            bulkTelemetryPacingMs = 150L,
            activeVoiceSpeakers = 4,
            packetsObservedPerSec = 45,
            lastEvaluationTimestampMs = 1000L
        )
        bridge.updateAirtimeCongestionSnapshot(congestedSnapshot)
        assertEquals(ChannelCongestionLevel.HIGH, bridge.airtimeCongestionSnapshot.value.congestionLevel)
        assertEquals(0.82f, bridge.airtimeCongestionSnapshot.value.channelBusyRatio)
        assertEquals(4, bridge.airtimeCongestionSnapshot.value.activeVoiceSpeakers)
        assertEquals(150L, bridge.airtimeCongestionSnapshot.value.bulkTelemetryPacingMs)
    }

    @Test
    fun testRealSubsystemsIntegration() = runTest {
        val suppressor = SpectralNoiseSuppressor()
        val wakeWord = AcousticWakeWordEngine()
        val governor = AirtimeCongestionGovernor()

        // Check suppressor snapshot
        assertEquals(NoiseSuppressionMode.AGGRESSIVE, suppressor.getSnapshot().mode)
        suppressor.setMode(NoiseSuppressionMode.EXTREME_ALPINE)
        assertEquals(NoiseSuppressionMode.EXTREME_ALPINE, suppressor.getSnapshot().mode)

        // Check wake word snapshot and sensitivity
        assertEquals(VoxState.IDLE, wakeWord.getSnapshot().state)
        wakeWord.setSensitivity(-35.0f)
        assertEquals(-35.0f, wakeWord.getSnapshot().energyDb)

        // Check governor metrics
        governor.recordAirtimeEvent(
            durationMicros = 20000L,
            isTransmit = true,
            priority = QoSPriority.ACTIVE_VOICE,
            timestampMs = 1000L
        )
        val metrics = governor.metrics.first()
        assertNotNull(metrics)
    }
}
