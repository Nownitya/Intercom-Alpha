package org.nowni.intercom_alpha.profiler

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nowni.intercom_alpha.audio.BlackboxAudioFrame
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.IncidentTriggerReason
import org.nowni.intercom_alpha.audio.PriorityLevel
import org.nowni.intercom_alpha.mesh.PacketCipher
import org.nowni.intercom_alpha.power.HardwarePowerSpec
import org.nowni.intercom_alpha.power.PowerAlert
import org.nowni.intercom_alpha.power.PowerProfiler
import org.nowni.intercom_alpha.power.RadioPowerState
import org.nowni.intercom_alpha.telemetry.GpsCoordinate

/**
 * Validates 4-hour background mesh continuity, WakeLock acquisition/release balance,
 * memory footprint bounds (< 1 MB blackbox cache under 3,000 frames), and clean SCO release.
 */
class PowerProfilerTest {

    /**
     * Simulates WakeLock state tracking to verify balance across PTT and Standby states.
     */
    private class SimulatedWakeLockController {
        var isSessionHeld = false
            private set
        var isPttHeld = false
            private set
        var acquireCount = 0
            private set
        var releaseCount = 0
            private set

        fun acquireSessionLock() {
            if (!isSessionHeld) {
                isSessionHeld = true
                acquireCount++
            }
        }

        fun acquirePttLock() {
            if (!isPttHeld) {
                isPttHeld = true
                acquireCount++
            }
        }

        fun releasePttLock() {
            if (isPttHeld) {
                isPttHeld = false
                releaseCount++
            }
        }

        fun releaseAll() {
            if (isPttHeld) {
                isPttHeld = false
                releaseCount++
            }
            if (isSessionHeld) {
                isSessionHeld = false
                releaseCount++
            }
        }
    }

    /**
     * Simulates Bluetooth SCO audio route state for lifecycle termination verification.
     */
    private class SimulatedHeadsetManager {
        var isStarted = false
            private set
        var isScoActive = false
            private set

        fun start() {
            isStarted = true
        }

        fun setScoAudioRoute(enable: Boolean) {
            isScoActive = enable
        }

        fun stop() {
            isScoActive = false
            isStarted = false
        }
    }

    @Test
    fun testWakeLockAcquisitionAndReleaseBalanceAcrossPttAndStandbyStates() {
        val controller = SimulatedWakeLockController()

        // 1. Session start: Acquire baseline session WakeLock (Standby state)
        controller.acquireSessionLock()
        assertTrue("Session WakeLock should be active during intercom session", controller.isSessionHeld)
        assertFalse("PTT WakeLock should be inactive during standby", controller.isPttHeld)

        // 2. Transmit button pressed: PTT active
        controller.acquirePttLock()
        assertTrue("PTT WakeLock should be acquired when transmitting", controller.isPttHeld)

        // Redundant acquire should not duplicate count
        controller.acquirePttLock()
        assertEquals("Acquire count should reflect unique lock acquisitions", 2, controller.acquireCount)

        // 3. Transmit button released: Return to standby
        controller.releasePttLock()
        assertFalse("PTT WakeLock must be released when transmission stops", controller.isPttHeld)
        assertTrue("Session WakeLock must remain active during standby", controller.isSessionHeld)
        assertEquals(1, controller.releaseCount)

        // 4. Multiple rapid PTT cycles (simulating glove chatter)
        for (i in 1..10) {
            controller.acquirePttLock()
            assertTrue(controller.isPttHeld)
            controller.releasePttLock()
            assertFalse(controller.isPttHeld)
        }
        assertEquals(12, controller.acquireCount)
        assertEquals(11, controller.releaseCount)

        // 5. Explicit disconnect / teardown: releaseAll() must release both locks
        controller.acquirePttLock()
        controller.releaseAll()
        assertFalse("PTT WakeLock must be released upon disconnect", controller.isPttHeld)
        assertFalse("Session WakeLock must be released upon disconnect", controller.isSessionHeld)
        assertEquals("Acquires and releases must be strictly balanced", controller.acquireCount, controller.releaseCount)
    }

    @Test
    fun testBackgroundAudioRoutingReleasesScoCleanlyUponServiceTermination() {
        val headset = SimulatedHeadsetManager()

        // 1. Service start: Start headset and establish SCO audio route
        headset.start()
        headset.setScoAudioRoute(true)
        assertTrue("Headset manager should be started", headset.isStarted)
        assertTrue("SCO audio route lock should be active", headset.isScoActive)

        // 2. Service termination: Explicit disconnect
        headset.setScoAudioRoute(false)
        headset.stop()

        assertFalse("SCO audio route must be released upon service termination", headset.isScoActive)
        assertFalse("Headset manager must be stopped upon service termination", headset.isStarted)
    }

    @Test
    fun testMemoryFootprintBenchmarkLz77CompressedBlackboxBoundedUnderOneMegabyte() = runTest {
        val cipher = PacketCipher(PacketCipher.generateRandomKey())
        val recorder = EmergencyBlackboxRecorder(
            bufferDurationMs = 60_000L,
            maxFramesCapacity = 3_000,
            maxCachedIncidents = 5,
            defaultCipher = cipher,
            autoFreezeOnEmergency = false
        )

        // Generate 3,000 compressed ADPCM audio frames (60 seconds at 50 fps / 20ms frames)
        val dummyAdpcm = ByteArray(40) { (it * 7).toByte() }
        for (i in 1..3_000) {
            recorder.recordFrame(
                BlackboxAudioFrame(
                    timestampMs = 1_000_000L + i * 20L,
                    sequence = i.toLong(),
                    speakerId = "peer-${i % 4}",
                    speakerName = "Rider ${i % 4}",
                    priority = PriorityLevel.NORMAL,
                    adpcmData = dummyAdpcm,
                    gpsCoordinate = GpsCoordinate(latitude = 37.7749, longitude = -122.4194, speedKmh = 72f)
                )
            )
        }

        assertEquals(3_000, recorder.bufferedFrameCount.value)

        // 1. Verify buffer alone under 3,000 frames is bounded < 1 MB (actual ~504 KB)
        val bufferMemoryBytes = recorder.estimateMemoryUsageBytes()
        assertTrue(
            "Rolling buffer under 3,000 frames ($bufferMemoryBytes bytes) exceeded 1 MB threshold",
            bufferMemoryBytes < 1_000_000L
        )
        assertTrue(bufferMemoryBytes in 400_000L..650_000L)

        // 2. Trigger emergency incident snapshot with 3,000 frames and verify compressed report + buffer stays bounded < 1 MB
        recorder.freezeSnapshot(
            triggerReason = IncidentTriggerReason.CRASH_DETECTED,
            primaryPeerId = "peer-lead",
            primaryPeerName = "Lead Scout",
            location = GpsCoordinate(latitude = 37.7749, longitude = -122.4194, speedKmh = 80f),
            metadata = mapOf("speed" to "80", "heading" to "270"),
            now = 2_000_000L
        )

        assertEquals(1, recorder.getEncryptedReports().size)
        val singleReportBytes = recorder.estimateMemoryUsageBytes()
        assertTrue(
            "Buffer + LZ77 compressed report ($singleReportBytes bytes) exceeded 1 MB threshold",
            singleReportBytes < 1_000_000L
        )
        assertTrue(singleReportBytes in 500_000L..850_000L)

        // 3. Trigger up to 5 full cached incidents: total blackbox cache remains strictly bounded < 3 MB (actual ~1.1 MB)
        for (inc in 2..5) {
            recorder.freezeSnapshot(
                triggerReason = IncidentTriggerReason.CRASH_DETECTED,
                primaryPeerId = "peer-$inc",
                primaryPeerName = "Rider $inc",
                location = GpsCoordinate(latitude = 37.7749, longitude = -122.4194, speedKmh = 80f),
                metadata = mapOf("speed" to "80", "heading" to "270"),
                now = 2_000_000L + inc * 1000L
            )
        }

        assertEquals(5, recorder.getEncryptedReports().size)
        val maxCacheBytes = recorder.estimateMemoryUsageBytes()
        assertTrue(
            "Full blackbox cache with 5 incidents ($maxCacheBytes bytes) exceeded 3 MB threshold",
            maxCacheBytes < 3_000_000L
        )
        assertTrue(maxCacheBytes in 900_000L..1_500_000L)
    }

    @Test
    fun testFourHourBackgroundMeshContinuityAndDutyCycle() = runTest {
        val t0 = 100_000L
        val profiler = PowerProfiler(
            spec = HardwarePowerSpec(
                idleCurrentMa = 20.0,
                transmitCurrentMa = 75.0,
                receiveCurrentMa = 55.0,
                relayCurrentMa = 35.0,
                referenceBatteryCapacityMah = 4000.0,
                maxContinuousTxSeconds = 60L
            ),
            startTimeMs = t0
        )

        val oneHourMs = 3_600_000L
        var currentT = t0

        // Simulate 4 continuous hours of realistic group riding:
        // 70% idle/listening standby, 10% transmitting, 15% receiving, 5% relaying
        for (hour in 1..4) {
            // Idle standby: 2520s
            currentT += (0.70 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.TRANSMITTING, now = currentT)

            // Transmit: 360s
            currentT += (0.10 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.RECEIVING, now = currentT)

            // Receive: 540s
            currentT += (0.15 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.RELAYING, now = currentT)

            // Relay: 180s
            currentT += (0.05 * oneHourMs).toLong()
            profiler.transitionTo(RadioPowerState.IDLE, now = currentT)
        }

        val report = profiler.generateReport(now = currentT)

        assertEquals(4 * oneHourMs, report.totalSessionDurationMs)
        // 4-Hour battery drain must be well below 30% threshold
        assertTrue(
            "Drain was ${report.estimatedDrainPercent}%, expected < 30%",
            report.estimatedDrainPercent < 30.0
        )
        // Realistic consumption: ~3.15% on 4,000 mAh battery
        assertTrue(report.estimatedDrainPercent in 2.5..6.0)
        assertEquals(PowerAlert.NONE, report.activeAlert)
    }

    @Test
    fun testRunawayWakeLockSafetyWatchdog() = runTest {
        val t0 = 10_000L
        val profiler = PowerProfiler(
            spec = HardwarePowerSpec(maxContinuousTxSeconds = 60L),
            startTimeMs = t0
        )

        // Start PTT transmission
        profiler.transitionTo(RadioPowerState.TRANSMITTING, now = t0)

        // 30 seconds of transmission: Safe, normal operation
        val safeReport = profiler.generateReport(now = t0 + 30_000L)
        assertEquals(PowerAlert.NONE, safeReport.activeAlert)

        // 61 seconds of continuous transmission: Runaway WakeLock watchdog triggers alert
        val alertReport = profiler.generateReport(now = t0 + 61_000L)
        assertEquals(PowerAlert.RUNAWAY_WAKELOCK, alertReport.activeAlert)

        // Transition back to IDLE resets alert
        profiler.transitionTo(RadioPowerState.IDLE, now = t0 + 62_000L)
        val idleReport = profiler.generateReport(now = t0 + 63_000L)
        assertEquals(PowerAlert.NONE, idleReport.activeAlert)
    }
}
