package org.nowni.intercom_alpha.telemetry

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.map.GeoPoint
import org.nowni.intercom_alpha.map.TrailWaypoint
import org.nowni.intercom_alpha.map.WaypointType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConvoyGeofenceEngineTest {

    private val localCoord = GpsCoordinate(
        latitude = 45.0000,
        longitude = 7.0000,
        altitudeMeters = 1500.0,
        speedKmh = 45f,
        headingDegrees = 0f,
        timestampMs = 1000L
    )

    @Test
    fun testInitialState() = runTest {
        val engine = ConvoyGeofenceEngine()
        val snapshot = engine.geofenceState.value

        assertEquals(GeofenceTetherStatus.IN_BOUNDS, snapshot.overallStatus)
        assertEquals(0.0, snapshot.furthestRiderDistanceMeters)
        assertTrue(snapshot.tetherAlerts.isEmpty())
        assertTrue(snapshot.activeHazardAlerts.isEmpty())
    }

    @Test
    fun testTetherWarningAndBreachEvaluation() = runTest {
        val engine = ConvoyGeofenceEngine(warningThresholdMeters = 300.0, breachThresholdMeters = 600.0)

        val rider1 = ConvoyRiderPosition(
            peerId = "peer-1",
            peerName = "Rider Close",
            coordinate = GpsCoordinate(45.0010, 7.0000), // ~111m
            distanceMeters = 111.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = true,
            isBehind = false,
            motionState = MotionState.HIGHWAY_CRUISE,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L
        )

        val rider2 = ConvoyRiderPosition(
            peerId = "peer-2",
            peerName = "Rider Mid",
            coordinate = GpsCoordinate(45.0035, 7.0000), // ~389m
            distanceMeters = 389.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = true,
            isBehind = false,
            motionState = MotionState.HIGHWAY_CRUISE,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L
        )

        val snapshot = engine.evaluate(localCoord, listOf(rider1, rider2))
        assertEquals(GeofenceTetherStatus.WARNING, snapshot.overallStatus)
        assertEquals(389.0, snapshot.furthestRiderDistanceMeters)
        assertEquals(2, snapshot.tetherAlerts.size)

        val alert2 = snapshot.tetherAlerts.find { it.peerId == "peer-2" }
        assertNotNull(alert2)
        assertEquals(GeofenceTetherStatus.WARNING, alert2.status)

        // Now test breach
        val riderBreach = ConvoyRiderPosition(
            peerId = "peer-3",
            peerName = "Rider Far",
            coordinate = GpsCoordinate(45.0070, 7.0000), // ~778m
            distanceMeters = 778.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = false,
            isBehind = true,
            motionState = MotionState.CITY_CONVOY,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L
        )

        val breachSnapshot = engine.evaluate(localCoord, listOf(rider1, rider2, riderBreach))
        assertEquals(GeofenceTetherStatus.BREACH, breachSnapshot.overallStatus)
        assertEquals(778.0, breachSnapshot.furthestRiderDistanceMeters)

        // Verify alarm was sent
        val alarm = engine.outgoingAlarms.tryReceive().getOrNull()
        assertNotNull(alarm)
        assertEquals(GeofenceAlarmType.TETHER_BREACH, alarm.alarmType)
        assertEquals("peer-3", alarm.targetId)
    }

    @Test
    fun testStaleRiderDisconnectedStatus() = runTest {
        val engine = ConvoyGeofenceEngine()
        val staleRider = ConvoyRiderPosition(
            peerId = "stale-peer",
            peerName = "Stale Rider",
            coordinate = GpsCoordinate(45.0100, 7.0000),
            distanceMeters = 1100.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = false,
            isBehind = true,
            motionState = MotionState.STATIONARY,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L,
            isStale = true
        )

        val snapshot = engine.evaluate(localCoord, listOf(staleRider))
        val alert = snapshot.tetherAlerts.firstOrNull()
        assertNotNull(alert)
        assertEquals(GeofenceTetherStatus.DISCONNECTED, alert.status)
        // Stale rider alone should not force overall breach
        assertEquals(GeofenceTetherStatus.IN_BOUNDS, snapshot.overallStatus)
    }

    @Test
    fun testHazardWaypointProximityDetection() = runTest {
        val engine = ConvoyGeofenceEngine(hazardProximityRadiusMeters = 200.0)

        // Point ~111m ahead of localCoord (45.0000, 7.0000)
        val hazardWp = TrailWaypoint(
            id = "wp-hazard-1",
            name = "Fallen Rock Zone",
            point = GeoPoint(45.0010, 7.0000, 1520.0),
            type = WaypointType.OBSTACLE_HAZARD,
            description = "Loose gravel and rockfall"
        )

        val farWp = TrailWaypoint(
            id = "wp-camp-1",
            name = "Basecamp Alpha",
            point = GeoPoint(45.0100, 7.0000, 1600.0), // ~1110m
            type = WaypointType.BASECAMP
        )

        val snapshot = engine.evaluate(localCoord, emptyList(), listOf(hazardWp, farWp))
        assertEquals(1, snapshot.activeHazardAlerts.size)

        val alert = snapshot.activeHazardAlerts.first()
        assertEquals("wp-hazard-1", alert.waypointId)
        assertTrue(alert.distanceMeters in 100.0..120.0)
        assertEquals(WaypointType.OBSTACLE_HAZARD, alert.waypointType)

        // Verify alarm was generated for hazardous obstacle
        val alarm = engine.outgoingAlarms.tryReceive().getOrNull()
        assertNotNull(alarm)
        assertEquals(GeofenceAlarmType.HAZARD_PROXIMITY, alarm.alarmType)
        assertEquals("wp-hazard-1", alarm.targetId)
    }

    @Test
    fun testConvoySplitBifurcationDetection() = runTest {
        val engine = ConvoyGeofenceEngine(splitClusterMinDistanceMeters = 500.0)

        // Local cluster: rider at (45.0000, 7.0000) and (45.0010, 7.0000) [~111m away]
        val cluster1Peer = ConvoyRiderPosition(
            peerId = "peer-c1",
            peerName = "Lead Partner",
            coordinate = GpsCoordinate(45.0010, 7.0000),
            distanceMeters = 111.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = true,
            isBehind = false,
            motionState = MotionState.HIGHWAY_CRUISE,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L
        )

        // Remote cluster: rider at (45.0090, 7.0000) [~1000m away, gap between clusters ~889m]
        val cluster2Peer = ConvoyRiderPosition(
            peerId = "peer-c2",
            peerName = "Tail Scout",
            coordinate = GpsCoordinate(45.0090, 7.0000),
            distanceMeters = 1000.0,
            absoluteBearingDegrees = 0.0,
            relativeBearingDegrees = 0.0,
            isAhead = true,
            isBehind = false,
            motionState = MotionState.HIGHWAY_CRUISE,
            isEmergencyAlert = false,
            lastUpdatedMs = 1000L
        )

        val snapshot = engine.evaluate(localCoord, listOf(cluster1Peer, cluster2Peer))
        val splitWarning = snapshot.splitWarning
        assertNotNull(splitWarning)
        assertTrue(splitWarning.isSplitDetected)
        assertEquals(2, splitWarning.clusterCount)
        assertTrue(splitWarning.clusterGapMeters >= 500.0)

        // Verify split alarm was dispatched
        var splitAlarmFound = false
        while (true) {
            val a = engine.outgoingAlarms.tryReceive().getOrNull() ?: break
            if (a.alarmType == GeofenceAlarmType.CONVOY_SPLIT) {
                splitAlarmFound = true
                break
            }
        }
        assertTrue(splitAlarmFound)
    }

    @Test
    fun testWireSerializationSymmetry() {
        val packet = GeofenceAlarmPacket(
            senderId = "rider-alpha",
            alarmType = GeofenceAlarmType.HAZARD_PROXIMITY,
            targetId = "wp-42",
            title = "HAZARD AHEAD",
            details = "Sharp blind hairpin curve",
            distanceMeters = 125.5,
            timestampMs = 1728280000000L
        )

        val bytes = ConvoyGeofenceEngine.serializeAlarmPacket(packet)
        val decoded = ConvoyGeofenceEngine.deserializeAlarmPacket(bytes)

        assertEquals(packet.senderId, decoded.senderId)
        assertEquals(packet.alarmType, decoded.alarmType)
        assertEquals(packet.targetId, decoded.targetId)
        assertEquals(packet.title, decoded.title)
        assertEquals(packet.details, decoded.details)
        assertEquals(packet.distanceMeters, decoded.distanceMeters, 0.01)
        assertEquals(packet.timestampMs, decoded.timestampMs)
    }
}
