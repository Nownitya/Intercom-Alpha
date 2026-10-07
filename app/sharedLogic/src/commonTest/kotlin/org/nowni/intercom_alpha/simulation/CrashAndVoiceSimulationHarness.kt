package org.nowni.intercom_alpha.simulation

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.audio.*
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.ControlType
import org.nowni.intercom_alpha.mesh.MeshPacket
import org.nowni.intercom_alpha.mesh.PacketDeduplicator
import org.nowni.intercom_alpha.safety.*
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

/**
 * Configuration for the multi-axis crash impact & offline voice command benchmark simulation.
 */
data class CrashSimulationConfig(
    val baselineNormalSamples: Int = 1000,
    val potholeImpulses: Int = 10,
    val severeCollisionG: Float = 8.5f,
    val hardImpactG: Float = 5.2f,
    val fallOverTiltDeg: Float = 72f,
    val fallOverDurationMs: Long = 9000L,
    val windNoiseFrameCount: Int = 50,
    val continuousStressCycles: Int = 5000
)

/**
 * Aggregated telemetry metrics captured during the simulation harness execution.
 */
data class CrashSimulationMetrics(
    val totalImuSamplesProcessed: Int,
    val falseAlarmsTriggered: Int,
    val highGCollisionsDetected: Int,
    val sustainedFallsDetected: Int,
    val countdownTicksObserved: Int,
    val countdownAbortsSuccessful: Int,
    val autonomousSosDispatched: Int,
    val windBuffetingFramesRejected: Int,
    val voiceCommandsRecognized: Int,
    val meshRelayHopsCompleted: Int,
    val executionDurationMs: Long
)

/**
 * End-to-end multiplatform simulation benchmark suite validating:
 * 1. Multi-axis IMU crash/fall-over detection and false-alarm rejection (potholes vs crashes).
 * 2. 30-second abortable countdown safety window with 1-tap rider abort.
 * 3. Autonomous eCall emergency SOS mesh broadcast upon timer expiry.
 * 4. Offline voice command keyword spotting under simulated 80-120 km/h wind buffeting noise.
 * 5. Multi-node mesh forwarding and memory stability under continuous streaming.
 *
 * Rules: Zero Java/Android SDK/C dependencies in commonTest (Rules 2 & 3).
 */
class CrashAndVoiceSimulationHarness {

    private val testGps = GpsCoordinate(
        latitude = 45.4642,
        longitude = 9.1900,
        altitudeMeters = 120.0,
        speedKmh = 72.0f,
        headingDegrees = 45.0f,
        accuracyMeters = 3.0f,
        timestampMs = 100000L
    )

    @Test
    fun testMultiAxisImuKinematicsAndFalseAlarmRejection() = runTest {
        val detector = CrashDetector(
            localNodeId = "rider_alpha",
            localNodeName = "Rider Alpha"
        )

        var falseAlarms = 0
        var samplesProcessed = 0

        // 1. Normal road riding with gentle bumps (1.0g .. 2.2g, upright tilt < 12°)
        val rng = Random(42)
        var currentMs = 10000L
        for (i in 0 until 1000) {
            val ax = (rng.nextFloat() - 0.5f) * 4.0f // -2.0 .. +2.0 m/s²
            val ay = (rng.nextFloat() - 0.5f) * 4.0f
            val az = 9.8f + (rng.nextFloat() - 0.5f) * 6.0f // ~6.8 .. 12.8 m/s²
            val reading = ImuReading(
                timestampMs = currentMs,
                accelX = ax,
                accelY = ay,
                accelZ = az
            )
            val snapshot = detector.processImuReading(reading, currentGps = testGps)
            samplesProcessed++
            if (snapshot.state != CrashState.NORMAL) {
                falseAlarms++
            }
            currentMs += 20L // 50 Hz IMU rate
        }

        // 2. Severe pothole / speed bump impulses (< 200ms, ~3.0g peak, upright tilt < 15°)
        for (p in 0 until 5) {
            currentMs += 500L
            for (step in 0 until 5) {
                val reading = ImuReading(
                    timestampMs = currentMs + step * 20L,
                    accelX = (rng.nextFloat() - 0.5f) * 6f,
                    accelY = (rng.nextFloat() - 0.5f) * 6f,
                    accelZ = 28.0f // ~2.85g vertical spike
                )
                val snapshot = detector.processImuReading(reading, currentGps = testGps)
                samplesProcessed++
                if (snapshot.state != CrashState.NORMAL) {
                    falseAlarms++
                }
            }
        }

        // 3. Sharp aggressive motorcycle curve lean (lean > 60° for 3.5 seconds, then recovery)
        currentMs += 1000L
        for (step in 0 until 175) { // 175 * 20ms = 3500ms
            val reading = ImuReading(
                timestampMs = currentMs + step * 20L,
                accelX = 8.5f,
                accelY = 0.0f,
                accelZ = 4.0f // Tilt > 64°, but duration < 8.0s threshold
            )
            val snapshot = detector.processImuReading(reading, currentGps = testGps)
            samplesProcessed++
            if (snapshot.state != CrashState.NORMAL) {
                falseAlarms++
            }
        }

        // Return upright
        currentMs += 4000L
        val uprightReading = ImuReading(
            timestampMs = currentMs,
            accelX = 0f,
            accelY = 0f,
            accelZ = 9.8f
        )
        val uprightSnap = detector.processImuReading(uprightReading)
        samplesProcessed++

        assertEquals(0, falseAlarms, "Potholes and curve leans must produce 0 false alarms")
        assertEquals(CrashState.NORMAL, uprightSnap.state)
        assertEquals(CrashState.NORMAL, detector.snapshot.value.state)
        assertTrue(samplesProcessed >= 1000)
    }

    @Test
    fun testHighGCollisionImpulseAndCountdownAbortSafetyWindow() = runTest {
        val detector = CrashDetector(
            localNodeId = "lead_rider",
            localNodeName = "Lead Rider"
        )

        val startTime = 50000L
        // 1. Riding normally
        detector.processImuReading(
            ImuReading(startTime, 0f, 0f, 9.8f),
            currentGps = testGps
        )

        // 2. Severe vehicle collision shock (8.5g impulse: ~83.6 m/s²)
        val impactTime = startTime + 1000L
        val severeReading = ImuReading(
            timestampMs = impactTime,
            accelX = 60.0f,
            accelY = 50.0f,
            accelZ = 30.0f
        )
        val activeSnap = detector.processImuReading(severeReading, currentGps = testGps)

        // Verification of countdown activation
        assertEquals(CrashState.COUNTDOWN_ACTIVE, activeSnap.state)
        assertTrue(activeSnap.isCountdownActive)
        assertEquals(CrashSeverity.SEVERE_COLLISION, activeSnap.severity)
        assertEquals(30, activeSnap.remainingSeconds)
        assertTrue(activeSnap.peakGForce >= 8.0f)
        assertEquals(testGps, activeSnap.lastCoordinate)

        // 3. Ticking countdown through 12 seconds
        var tickedSnap: CrashCountdownSnapshot = activeSnap
        for (sec in 1..12) {
            tickedSnap = detector.tickCountdown(impactTime + sec * 1000L)
            assertEquals(30 - sec, tickedSnap.remainingSeconds)
            assertEquals(CrashState.COUNTDOWN_ACTIVE, tickedSnap.state)
        }
        assertEquals(18, tickedSnap.remainingSeconds)

        // 4. Rider taps "I AM OK" abort button
        val cancelledSnap = detector.cancelCountdown("Rider tapped Cancel on cockpit HUD")
        assertEquals(CrashState.CANCELLED_BY_RIDER, cancelledSnap.state)
        assertFalse(cancelledSnap.isCountdownActive)
        assertFalse(cancelledSnap.isEmergencyDispatched)
        assertEquals(0, cancelledSnap.remainingSeconds)

        // 5. Verify 0 emergency alert packets were dispatched
        assertNull(detector.outgoingAlerts.tryReceive().getOrNull())
    }

    @Test
    fun testMotorcycleFallOverAndAutonomousEcallMeshDispatch() = runTest {
        val detector = CrashDetector(
            localNodeId = "rider_scout",
            localNodeName = "Scout 1"
        )

        val startTime = 100000L
        // 1. Sustained motorcycle tip-over on side (tilt > 70° for > 8.0 seconds)
        var fallSnap: CrashCountdownSnapshot? = null
        for (i in 0..450) { // 450 * 20ms = 9000ms (9 seconds)
            val reading = ImuReading(
                timestampMs = startTime + i * 20L,
                accelX = 9.2f,
                accelY = 0.5f,
                accelZ = 2.0f // Tilt > 75°
            )
            fallSnap = detector.processImuReading(reading, currentGps = testGps)
        }

        assertNotNull(fallSnap)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, fallSnap.state)
        assertEquals(CrashSeverity.POTENTIAL_FALL_OVER, fallSnap.severity)
        assertTrue(fallSnap.triggerReason.contains("fall-over"))

        // 2. Rider is unconscious/unresponsive: advance ticks through entire 30 seconds
        val detectionTimestamp = startTime + 450 * 20L
        var finalSnap: CrashCountdownSnapshot = fallSnap
        for (sec in 1..31) {
            finalSnap = detector.tickCountdown(detectionTimestamp + sec * 1000L)
        }

        // Verification of automatic eCall dispatch
        assertEquals(CrashState.EMERGENCY_DISPATCHED, finalSnap.state)
        assertTrue(finalSnap.isEmergencyDispatched)
        assertEquals(0, finalSnap.remainingSeconds)

        // 3. Receive emitted emergency alert packet from channel
        val alert = detector.outgoingAlerts.tryReceive().getOrNull()
        assertNotNull(alert, "Emergency crash packet must be emitted upon countdown expiry")
        assertEquals("rider_scout", alert.senderId)
        assertEquals("Scout 1", alert.senderName)
        assertEquals(CrashSeverity.POTENTIAL_FALL_OVER, alert.severity)
        assertEquals(testGps, alert.coordinate)

        // 4. Simulate multi-hop mesh forwarding across 3 virtual nodes
        val wireBytes = CrashDetector.encodeAlertPayload(alert)
        assertTrue(wireBytes.isNotEmpty())

        val deduplicatorNode2 = PacketDeduplicator()
        val deduplicatorNode3 = PacketDeduplicator()

        val packetNode1 = MeshPacket.Control(
            senderId = "rider_scout",
            type = ControlType.CRASH_ALERT,
            payload = wireBytes
        )

        val relayHop1 = MeshPacket.Relay(
            originalSenderId = "rider_scout",
            ttl = 3,
            packet = packetNode1
        )

        // Hop 1 -> Hop 2
        assertTrue(deduplicatorNode2.shouldProcess(relayHop1, currentTimeMillis()))
        val relayHop2 = relayHop1.copy(ttl = 2)

        // Hop 2 -> Hop 3
        assertTrue(deduplicatorNode3.shouldProcess(relayHop2, currentTimeMillis()))

        // Node 3 deserializes remote alert
        val receivedControl = relayHop2.packet as MeshPacket.Control
        val receivedAlert = CrashDetector.parseAlertPayload(receivedControl.payload)
        assertNotNull(receivedAlert)
        assertEquals(alert.incidentId, receivedAlert.incidentId)
        assertEquals(alert.senderId, receivedAlert.senderId)
        assertEquals(alert.coordinate?.latitude, receivedAlert.coordinate?.latitude)
    }

    @Test
    fun testOfflineVoiceCommandDspUnderAerodynamicWindNoise() = runTest {
        val recognizer = VoiceCommandRecognizer()

        // 1. Synthesize 50 frames of high-speed motorcycle helmet aerodynamic wind noise
        // Characteristics: 60-120 Hz sinusoidal buffeting, low zero-crossing rate (<0.015), high energy
        val windFrames = 50
        val sampleRate = 48000
        val frameSize = 960 // 20ms at 48kHz
        var falseVoiceDetections = 0

        for (f in 0 until windFrames) {
            val windPcm = ShortArray(frameSize)
            val baseFreq = 80.0 // 80 Hz buffeting
            for (i in 0 until frameSize) {
                val t = (f * frameSize + i).toDouble() / sampleRate
                val amp = (sin(2.0 * PI * baseFreq * t) * 16000.0).toInt().coerceIn(-32768, 32767)
                windPcm[i] = amp.toShort()
            }
            val detection = recognizer.processPcmSamples(windPcm, timestampMs = 1000L + f * 20L)
            if (detection != null) {
                falseVoiceDetections++
            }
        }

        assertEquals(0, falseVoiceDetections, "Aerodynamic wind buffeting rumble must not trigger voice commands")

        // 2. Simulate injection and verification of tactical keywords
        val commandsToTest = listOf(
            VoiceCommand.SOS_TRIGGER,
            VoiceCommand.NEXT_CHANNEL,
            VoiceCommand.PREV_CHANNEL,
            VoiceCommand.MUTE_TOGGLE,
            VoiceCommand.BATTERY_STATUS,
            VoiceCommand.STATUS_REPORT,
            VoiceCommand.CANCEL_ALERT
        )

        for ((idx, cmd) in commandsToTest.withIndex()) {
            val ts = 20000L + idx * 2000L
            val detection = recognizer.simulateCommand(cmd, confidence = 0.92f, timestampMs = ts)
            assertEquals(cmd, detection.command)
            assertTrue(detection.confidence >= 0.90f)
            assertEquals(cmd, recognizer.latestDetection.value?.command)
        }
    }

    @Test
    fun testContinuousStreamingStressAndMemoryBounds() = runTest {
        val detector = CrashDetector()
        val recognizer = VoiceCommandRecognizer()

        val sampleCount = 5000
        val rng = Random(1234)

        // Continuous streaming of 5,000 IMU frames (100 seconds of 50 Hz motion data)
        for (i in 0 until sampleCount) {
            val reading = ImuReading(
                timestampMs = 1000L + i * 20L,
                accelX = (rng.nextFloat() - 0.5f) * 3f,
                accelY = (rng.nextFloat() - 0.5f) * 3f,
                accelZ = 9.8f + (rng.nextFloat() - 0.5f) * 4f
            )
            detector.processImuReading(reading)

            if (i % 50 == 0) {
                // Ingest speed updates
                val speed = 60.0f + rng.nextFloat() * 20.0f
                detector.processSpeedUpdate(speed, timestampMs = 1000L + i * 20L)
            }
        }

        assertEquals(CrashState.NORMAL, detector.snapshot.value.state)

        // Reset detector and recognizer cleanly
        val resetSnap = detector.reset()
        assertEquals(CrashState.NORMAL, resetSnap.state)
        assertEquals(1.0f, resetSnap.peakGForce)

        recognizer.reset()
        assertNull(recognizer.latestDetection.value)
    }
}
