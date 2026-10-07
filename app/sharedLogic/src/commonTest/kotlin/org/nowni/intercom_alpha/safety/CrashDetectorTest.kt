package org.nowni.intercom_alpha.safety

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.telemetry.GpsCoordinate
import kotlin.test.*

class CrashDetectorTest {

    private lateinit var detector: CrashDetector
    private val config = CrashConfig(
        impactGThreshold = 4.5f,
        severeImpactGThreshold = 7.0f,
        fallOverTiltThresholdDeg = 60f,
        fallOverDurationMs = 8000L,
        decelerationDropKmh = 35f,
        decelerationTimeWindowMs = 1500L,
        countdownDurationSeconds = 30
    )

    @BeforeTest
    fun setup() {
        detector = CrashDetector(
            config = config,
            localNodeId = "peer_rider_1",
            localNodeName = "Lead Scout"
        )
    }

    @Test
    fun testNormalRidingVibrationsDoNotTriggerCrash() = runTest {
        // Normal upright riding with small bumps (1.2g .. 2.5g)
        for (i in 0 until 50) {
            val reading = ImuReading(
                timestampMs = 1000L + i * 100L,
                accelX = 2.0f,
                accelY = 1.5f,
                accelZ = 9.8f // ~10.1 m/s² ~= 1.03g
            )
            val snapshot = detector.processImuReading(reading)
            assertEquals(CrashState.NORMAL, snapshot.state)
            assertFalse(snapshot.isCountdownActive)
            assertFalse(snapshot.isEmergencyDispatched)
        }
    }

    @Test
    fun testHardImpactCollisionTriggersCountdown() = runTest {
        val initialReading = ImuReading(
            timestampMs = 1000L,
            accelX = 0f,
            accelY = 0f,
            accelZ = 9.8f
        )
        detector.processImuReading(initialReading)

        // Hard impact impulse: 5.0g (49.0 m/s²)
        val impactReading = ImuReading(
            timestampMs = 2000L,
            accelX = 35.0f,
            accelY = 25.0f,
            accelZ = 25.0f // sqrt(35^2 + 25^2 + 25^2) = sqrt(2475) ≈ 49.75 m/s² ≈ 5.07g
        )
        val gps = GpsCoordinate(
            latitude = 45.1234,
            longitude = 6.5432,
            speedKmh = 65f,
            timestampMs = 2000L
        )

        val snapshot = detector.processImuReading(impactReading, currentGps = gps)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, snapshot.state)
        assertTrue(snapshot.isCountdownActive)
        assertEquals(CrashSeverity.HARD_IMPACT, snapshot.severity)
        assertEquals(30, snapshot.remainingSeconds)
        assertTrue(snapshot.peakGForce >= 4.5f)
        assertEquals(gps, snapshot.lastCoordinate)
    }

    @Test
    fun testSevereImpactCollisionTriggersSevereSeverity() = runTest {
        // Severe impact: 8.5g (~83 m/s²)
        val severeReading = ImuReading(
            timestampMs = 1500L,
            accelX = 60.0f,
            accelY = 50.0f,
            accelZ = 30.0f // sqrt(3600 + 2500 + 900) = sqrt(7000) ≈ 83.66 m/s² ≈ 8.53g
        )

        val snapshot = detector.processImuReading(severeReading)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, snapshot.state)
        assertEquals(CrashSeverity.SEVERE_COLLISION, snapshot.severity)
        assertTrue(snapshot.peakGForce >= 7.0f)
    }

    @Test
    fun testMotorcycleFallOverTiltRequiresSustainedDuration() = runTest {
        // 1. Sharp turn lean angle for 3 seconds (tilt > 60° but duration < 8s)
        for (i in 0 until 30) {
            val tiltReading = ImuReading(
                timestampMs = 1000L + i * 100L, // 1000..3900ms (2.9s)
                accelX = 8.5f,
                accelY = 0f,
                accelZ = 4.0f // cosTheta = 4.0 / sqrt(8.5^2 + 4^2) = 4.0 / 9.39 ≈ 0.425 => tilt ≈ 64.8°
            )
            val snapshot = detector.processImuReading(tiltReading)
            assertEquals(CrashState.NORMAL, snapshot.state) // Must not trigger false positive in curve
        }

        // Return upright
        val uprightReading = ImuReading(
            timestampMs = 4500L,
            accelX = 0f,
            accelY = 0f,
            accelZ = 9.8f
        )
        detector.processImuReading(uprightReading)

        // 2. Real bike fall-over: sustained tilt >60° for >8 seconds
        var finalSnapshot: CrashCountdownSnapshot? = null
        for (i in 0..90) {
            val fallenReading = ImuReading(
                timestampMs = 5000L + i * 100L, // 5000..14000ms (9.0s)
                accelX = 9.0f,
                accelY = 0f,
                accelZ = 2.0f // tilt > 75°
            )
            finalSnapshot = detector.processImuReading(fallenReading)
            if (i < 80) {
                // Not yet reached 8s
                assertEquals(CrashState.NORMAL, finalSnapshot.state)
            }
        }

        assertNotNull(finalSnapshot)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, finalSnapshot.state)
        assertEquals(CrashSeverity.POTENTIAL_FALL_OVER, finalSnapshot.severity)
        assertTrue(finalSnapshot.triggerReason.contains("fall-over"))
    }

    @Test
    fun testCountdownTickingAndRiderCancellation() = runTest {
        // Trigger impact
        val impact = ImuReading(
            timestampMs = 10000L,
            accelX = 35.0f,
            accelY = 25.0f,
            accelZ = 25.0f
        )
        detector.processImuReading(impact)
        assertEquals(30, detector.snapshot.value.remainingSeconds)

        // Advance 10 seconds (timestamp 20000L)
        val tick1 = detector.tickCountdown(20000L)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, tick1.state)
        assertEquals(20, tick1.remainingSeconds)

        // Advance another 5 seconds (timestamp 25000L)
        val tick2 = detector.tickCountdown(25000L)
        assertEquals(15, tick2.remainingSeconds)

        // Rider cancels countdown (e.g. false alarm or minor drop without injury)
        val cancelSnapshot = detector.cancelCountdown("Minor drop, rider okay")
        assertEquals(CrashState.CANCELLED_BY_RIDER, cancelSnapshot.state)
        assertEquals(0, cancelSnapshot.remainingSeconds)

        // Outgoing channel must have 0 emergency alerts dispatched
        assertNull(detector.outgoingAlerts.tryReceive().getOrNull())
    }

    @Test
    fun testCountdownExpiryDispatchesEmergencyAlert() = runTest {
        val impactTime = 50000L
        val impact = ImuReading(
            timestampMs = impactTime,
            accelX = 40.0f,
            accelY = 30.0f,
            accelZ = 20.0f
        )
        val gps = GpsCoordinate(
            latitude = 46.5000,
            longitude = 8.2000,
            speedKmh = 0f,
            timestampMs = impactTime
        )
        detector.processImuReading(impact, currentGps = gps)

        // Tick past 30 seconds: 50000L + 31000L = 81000L
        val finalSnapshot = detector.tickCountdown(81000L)
        assertEquals(CrashState.EMERGENCY_DISPATCHED, finalSnapshot.state)
        assertTrue(finalSnapshot.isEmergencyDispatched)
        assertEquals(0, finalSnapshot.remainingSeconds)

        // Verify alert packet was queued in outgoingAlerts channel
        val alert = detector.outgoingAlerts.tryReceive().getOrNull()
        assertNotNull(alert)
        assertEquals("peer_rider_1", alert.senderId)
        assertEquals("Lead Scout", alert.senderName)
        assertEquals(CrashSeverity.HARD_IMPACT, alert.severity)
        assertEquals(gps, alert.coordinate)
        assertTrue(alert.incidentId.startsWith("crash_peer_rider_1"))
    }

    @Test
    fun testEmergencyDecelerationWithShockTriggersCountdown() = runTest {
        // Riding at 80 km/h
        detector.processSpeedUpdate(80f, 1000L)

        // Sudden emergency brake/collision stop down to 20 km/h in 800ms (>35 km/h drop)
        // Record intermediate bump of 2.8g
        detector.processImuReading(
            ImuReading(
                timestampMs = 1500L,
                accelX = 15f,
                accelY = 15f,
                accelZ = 15f // ~2.65g
            )
        )

        val snapshot = detector.processSpeedUpdate(20f, 1800L)
        assertEquals(CrashState.COUNTDOWN_ACTIVE, snapshot.state)
        assertTrue(snapshot.triggerReason.contains("deceleration"))
    }

    @Test
    fun testManualCrashAlertImmediatelyDispatches() = runTest {
        val gps = GpsCoordinate(
            latitude = 45.0,
            longitude = 7.0,
            speedKmh = 0f,
            timestampMs = 123456L
        )

        val snapshot = detector.triggerManualCrashAlert(coordinate = gps, timestampMs = 123456L)
        assertEquals(CrashState.EMERGENCY_DISPATCHED, snapshot.state)
        assertEquals(CrashSeverity.SEVERE_COLLISION, snapshot.severity)

        val alert = detector.outgoingAlerts.tryReceive().getOrNull()
        assertNotNull(alert)
        assertEquals("peer_rider_1", alert.senderId)
        assertEquals(gps, alert.coordinate)
    }

    @Test
    fun testWirePacketSerializationSymmetry() {
        val packet = CrashAlertPacket(
            incidentId = "crash_node_42_1_99999",
            senderId = "node_42",
            senderName = "Alex",
            peakGForce = 5.8f,
            severity = CrashSeverity.HARD_IMPACT,
            triggerReason = "Hard impact shock (5.8g)",
            timestampMs = 99999L,
            coordinate = GpsCoordinate(45.5, 9.2, 120.0, 0f, 180f, 3f, 99999L)
        )

        val encoded = detector.encodeAlertPayload(packet)
        assertTrue(encoded.isNotEmpty())

        val decoded = detector.parseAlertPayload(encoded)
        assertNotNull(decoded)
        assertEquals(packet.incidentId, decoded.incidentId)
        assertEquals(packet.senderId, decoded.senderId)
        assertEquals(packet.senderName, decoded.senderName)
        assertEquals(packet.peakGForce, decoded.peakGForce, 0.01f)
        assertEquals(packet.severity, decoded.severity)
        assertEquals(packet.triggerReason, decoded.triggerReason)
        assertEquals(packet.coordinate, decoded.coordinate)
    }

    @Test
    fun testResetRestoresNormalState() = runTest {
        detector.triggerManualCrashAlert()
        assertEquals(CrashState.EMERGENCY_DISPATCHED, detector.snapshot.value.state)

        val resetSnapshot = detector.reset()
        assertEquals(CrashState.NORMAL, resetSnapshot.state)
        assertEquals(1.0f, resetSnapshot.peakGForce)
        assertEquals(0, resetSnapshot.remainingSeconds)
    }
}
