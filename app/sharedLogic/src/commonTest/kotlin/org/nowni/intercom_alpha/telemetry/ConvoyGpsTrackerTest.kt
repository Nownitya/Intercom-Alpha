package org.nowni.intercom_alpha.telemetry

import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConvoyGpsTrackerTest {

    @Test
    fun testHaversineDistanceKnownPoints() {
        // Paris (48.8566, 2.3522) to London (51.5074, -0.1278) ~343.5 km
        val distanceMeters = ConvoyGpsTracker.calculateHaversineDistance(
            48.8566, 2.3522,
            51.5074, -0.1278
        )
        assertTrue(distanceMeters in 340_000.0..348_000.0, "Expected ~344km, got $distanceMeters")

        // Equator small distance: 100 meters
        val lat1 = 0.0
        val lon1 = 0.0
        val lat2 = 0.0
        val lon2 = 0.00089831528 // ~100m at equator
        val smallDist = ConvoyGpsTracker.calculateHaversineDistance(lat1, lon1, lat2, lon2)
        assertTrue(abs(smallDist - 100.0) < 1.0, "Expected ~100m, got $smallDist")
    }

    @Test
    fun testForwardAzimuthAndRelativeBearing() {
        // Due North from (0,0) to (1,0)
        val northAzimuth = ConvoyGpsTracker.calculateForwardAzimuth(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(northAzimuth - 0.0) < 0.1 || abs(northAzimuth - 360.0) < 0.1)

        // Due East from (0,0) to (0,1)
        val eastAzimuth = ConvoyGpsTracker.calculateForwardAzimuth(0.0, 0.0, 0.0, 1.0)
        assertTrue(abs(eastAzimuth - 90.0) < 0.1)

        // Relative Bearing:
        // Heading East (90 deg), target is East (90 deg) -> relative bearing = 0 (straight ahead)
        val relAhead = ConvoyGpsTracker.calculateRelativeBearing(90.0, 90f)
        assertTrue(abs(relAhead) < 0.01)

        // Heading North (0 deg), target is East (90 deg) -> relative bearing = +90 (to the right)
        val relRight = ConvoyGpsTracker.calculateRelativeBearing(90.0, 0f)
        assertEquals(90.0, relRight, 0.01)

        // Heading East (90 deg), target is North (0 deg) -> relative bearing = -90 (to the left)
        val relLeft = ConvoyGpsTracker.calculateRelativeBearing(0.0, 90f)
        assertEquals(-90.0, relLeft, 0.01)

        // Heading East (90 deg), target is West (270 deg) -> relative bearing = -180 or +180 (behind)
        val relBehind = ConvoyGpsTracker.calculateRelativeBearing(270.0, 90f)
        assertEquals(180.0, abs(relBehind), 0.01)
    }

    @Test
    fun testMotionStateBeaconIntervals() = runTest {
        val tracker = ConvoyGpsTracker("rider-lead", "Lead Rider")

        // 1. Stationary (0 km/h) -> triggers first beacon immediately, interval is 15s
        val coord1 = GpsCoordinate(37.7749, -122.4194, speedKmh = 0f, timestampMs = 1000L)
        val b1 = tracker.updateLocalLocation(coord1)
        assertTrue(b1)
        assertEquals(MotionState.STATIONARY, tracker.convoyState.value.localMotionState)

        // After 5s, still stationary -> no beacon
        val coord2 = GpsCoordinate(37.7749, -122.4194, speedKmh = 2f, timestampMs = 6000L)
        val b2 = tracker.updateLocalLocation(coord2)
        assertFalse(b2)

        // After 16s total (delta > 15s) -> triggers stationary beacon
        val coord3 = GpsCoordinate(37.7749, -122.4194, speedKmh = 0f, timestampMs = 17000L)
        val b3 = tracker.updateLocalLocation(coord3)
        assertTrue(b3)

        // 2. High speed (> 30 km/h) -> 3s interval
        val coordHighSpeed = GpsCoordinate(37.7750, -122.4190, speedKmh = 80f, timestampMs = 20000L)
        val b4 = tracker.updateLocalLocation(coordHighSpeed)
        assertTrue(b4)
        assertEquals(MotionState.HIGHWAY_CRUISE, tracker.convoyState.value.localMotionState)
    }

    @Test
    fun testEmergencyBrakingDetection() = runTest {
        val tracker = ConvoyGpsTracker("rider-1", "Cruiser")

        // Cruising at 90 km/h
        tracker.updateLocalLocation(
            GpsCoordinate(37.77, -122.41, speedKmh = 90f, timestampMs = 10000L)
        )
        tracker.outgoingFrames.receive() // drain initial beacon

        // Hard stop to 20 km/h in 800ms (speed delta = 70 km/h > 35 km/h threshold)
        val bEmergency = tracker.updateLocalLocation(
            GpsCoordinate(37.77, -122.41, speedKmh = 20f, timestampMs = 10800L)
        )
        assertTrue(bEmergency, "Expected immediate emergency beacon on hard deceleration")

        val frame = tracker.outgoingFrames.receive()
        assertTrue(frame.isEmergencyAlert)
        assertEquals(MotionState.EMERGENCY_ALERT, frame.motionState)
    }

    @Test
    fun testIncomingRemoteTelemetryAndRelativePositioning() = runTest {
        val tracker = ConvoyGpsTracker("local-rider", "Local Rider")

        // Local rider at (37.7749, -122.4194), heading North (0 deg)
        tracker.updateLocalLocation(
            GpsCoordinate(37.7749, -122.4194, speedKmh = 45f, headingDegrees = 0f, timestampMs = 1000L)
        )

        // Remote rider 1 is North (ahead) of local rider: (37.7800, -122.4194)
        val remoteFrameAhead = ConvoyTelemetryFrame(
            peerId = "rider-scout",
            peerName = "Lead Scout",
            coordinate = GpsCoordinate(37.7800, -122.4194, speedKmh = 50f, timestampMs = 1000L),
            motionState = MotionState.HIGHWAY_CRUISE
        )

        tracker.handleIncomingTelemetry(remoteFrameAhead)

        val snapshot = tracker.convoyState.value
        assertEquals(1, snapshot.activeCount)
        val scout = snapshot.riders.first()
        assertEquals("rider-scout", scout.peerId)
        assertTrue(scout.isAhead)
        assertFalse(scout.isBehind)
        assertTrue(scout.distanceMeters > 500.0)
        assertEquals(scout, snapshot.closestRider)
        assertEquals(scout, snapshot.leadRider)
    }

    @Test
    fun testLeadAndSweepRiderAssignment() = runTest {
        val tracker = ConvoyGpsTracker("local-mid", "Mid Rider")

        // Local rider at (0.0, 0.0), heading North (0 deg)
        tracker.updateLocalLocation(
            GpsCoordinate(0.0, 0.0, speedKmh = 30f, headingDegrees = 0f, timestampMs = 1000L)
        )

        // Rider Ahead (North): (0.01, 0.0) ~1.1 km
        val leadFrame = ConvoyTelemetryFrame(
            peerId = "scout-1",
            peerName = "Lead Scout",
            coordinate = GpsCoordinate(0.01, 0.0, timestampMs = 1000L),
            motionState = MotionState.HIGHWAY_CRUISE
        )

        // Rider Behind (South): (-0.005, 0.0) ~550m
        val sweepFrame = ConvoyTelemetryFrame(
            peerId = "tail-1",
            peerName = "Sweep Rider",
            coordinate = GpsCoordinate(-0.005, 0.0, timestampMs = 1000L),
            motionState = MotionState.HIGHWAY_CRUISE
        )

        tracker.handleIncomingTelemetry(leadFrame)
        tracker.handleIncomingTelemetry(sweepFrame)

        val snapshot = tracker.convoyState.value
        assertEquals(2, snapshot.activeCount)
        assertEquals("scout-1", snapshot.leadRider?.peerId)
        assertEquals("tail-1", snapshot.sweepRider?.peerId)
        assertEquals("tail-1", snapshot.closestRider?.peerId) // ~550m is closer than ~1100m
    }

    @Test
    fun testStaleRiderPruning() = runTest {
        val tracker = ConvoyGpsTracker("local-rider", "Local Rider", staleTimeoutMs = 5000L)

        tracker.updateLocalLocation(GpsCoordinate(0.0, 0.0, timestampMs = 1000L))

        val remote = ConvoyTelemetryFrame(
            peerId = "rider-ghost",
            peerName = "Ghost Rider",
            coordinate = GpsCoordinate(0.001, 0.0, timestampMs = 1000L),
            motionState = MotionState.STATIONARY
        )

        tracker.handleIncomingTelemetry(remote)
        assertEquals(1, tracker.convoyState.value.activeCount)

        // After 6s, prune
        tracker.pruneStaleRiders(now = 7500L)
        assertEquals(0, tracker.convoyState.value.activeCount)
        assertTrue(tracker.convoyState.value.riders.isEmpty())
    }

    @Test
    fun testWireFrameSerializationSymmetry() {
        val frame = ConvoyTelemetryFrame(
            peerId = "peer-alpha",
            peerName = "Alpha Rider",
            coordinate = GpsCoordinate(
                latitude = 34.0522,
                longitude = -118.2437,
                altitudeMeters = 71.0,
                speedKmh = 105.4f,
                headingDegrees = 270.5f,
                accuracyMeters = 3.5f,
                timestampMs = 1700000000L
            ),
            motionState = MotionState.HIGHWAY_CRUISE,
            isEmergencyAlert = false,
            timestampMs = 1700000000L
        )

        val bytes = ConvoyGpsTracker.encodeTelemetryFrame(frame)
        val decoded = ConvoyGpsTracker.decodeTelemetryFrame(bytes)
        assertEquals(frame, decoded)
    }
}
