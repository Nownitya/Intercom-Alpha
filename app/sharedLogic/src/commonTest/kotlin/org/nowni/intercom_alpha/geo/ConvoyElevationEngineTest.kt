package org.nowni.intercom_alpha.geo

import org.nowni.intercom_alpha.map.GeoPoint
import kotlin.test.*

class ConvoyElevationEngineTest {

    private lateinit var engine: ConvoyElevationEngine

    @BeforeTest
    fun setUp() {
        engine = ConvoyElevationEngine(
            localPeerId = "lead-01",
            localCallsign = "Squad Lead"
        )
    }

    @Test
    fun testInitialElevationState() {
        val snapshot = engine.elevationProfile.value
        assertEquals(0.0, snapshot.localAltitudeMeters)
        assertEquals(0.0, snapshot.currentGradePercentage)
        assertEquals(0.0, snapshot.leadTailVerticalSeparationMeters)
        assertTrue(snapshot.activeWarnings.isEmpty())
    }

    @Test
    fun testMovingGradeCalculationAndAlpineWarning() {
        // Point 1: basecamp altitude 1000m
        val p1 = GeoPoint(latitude = 45.0000, longitude = 6.0000, altitudeMeters = 1000.0)
        engine.ingestLocalPosition(p1, timestampMs = 1000L)

        // Point 2: 100m away horizontally, 15m gain -> 15% grade (Extreme alpine climb)
        // 0.0009 degrees latitude is approx 100m
        val p2 = GeoPoint(latitude = 45.0009, longitude = 6.0000, altitudeMeters = 1015.0)
        engine.ingestLocalPosition(p2, timestampMs = 5000L)

        val profile = engine.elevationProfile.value
        assertEquals(1015.0, profile.localAltitudeMeters)
        assertTrue(profile.currentGradePercentage > 12.0, "Grade should be > 12%, was ${profile.currentGradePercentage}")

        // Verify extreme alpine climb warning triggered
        assertEquals(1, profile.activeWarnings.size)
        val warning = profile.activeWarnings[0]
        assertEquals(GradeSeverity.EXTREME_ALPINE_WARNING, warning.severity)
        assertTrue(warning.isClimb)
        assertTrue(warning.message.contains("STEEP CLIMB"))
    }

    @Test
    fun testSteepDescentWarning() {
        // Steep descent: 10m loss over 100m -> -10% grade (Caution)
        val p1 = GeoPoint(latitude = 45.0000, longitude = 6.0000, altitudeMeters = 2000.0)
        engine.ingestLocalPosition(p1, timestampMs = 1000L)

        val p2 = GeoPoint(latitude = 45.0009, longitude = 6.0000, altitudeMeters = 1990.0)
        engine.ingestLocalPosition(p2, timestampMs = 5000L)

        val profile = engine.elevationProfile.value
        assertTrue(profile.currentGradePercentage < -8.0 && profile.currentGradePercentage > -12.0)
        assertEquals(1, profile.activeWarnings.size)
        val warning = profile.activeWarnings[0]
        assertEquals(GradeSeverity.STEEP_CAUTION, warning.severity)
        assertFalse(warning.isClimb)
        assertTrue(warning.message.contains("Descent"))
    }

    @Test
    fun testConvoyLeadTailVerticalSeparation() {
        // Lead starts at trailhead (altitude 1,800m) and ascends to summit (2,500m)
        val trailhead = GeoPoint(latitude = 45.0000, longitude = 6.0000, altitudeMeters = 1800.0)
        engine.ingestLocalPosition(trailhead, timestampMs = 500L)

        val leadPos = GeoPoint(latitude = 45.0100, longitude = 6.0100, altitudeMeters = 2500.0)
        engine.ingestLocalPosition(leadPos, timestampMs = 1000L)

        // Mid rider at 2,200m
        val midPos = GeoPoint(latitude = 45.0050, longitude = 6.0050, altitudeMeters = 2200.0)
        engine.ingestPeerPosition(
            peerId = "mid-02",
            callsign = "Scout",
            point = midPos,
            distanceAlongRouteMeters = 500.0,
            timestampMs = 1000L
        )

        // Tail sweep rider at pass base 1,800m
        val tailPos = GeoPoint(latitude = 45.0000, longitude = 6.0000, altitudeMeters = 1800.0)
        engine.ingestPeerPosition(
            peerId = "tail-03",
            callsign = "Sweep",
            point = tailPos,
            distanceAlongRouteMeters = 100.0,
            timestampMs = 1000L
        )

        val profile = engine.elevationProfile.value
        assertEquals(3, profile.riders.size)

        // Vertical separation between lead (2,500m) and tail (1,800m) = 700m
        assertEquals(700.0, profile.leadTailVerticalSeparationMeters, 0.01)

        // Check tail rider delta to lead is -700m
        val tailState = profile.riders.find { it.peerId == "tail-03" }
        assertNotNull(tailState)
        assertEquals(-700.0, tailState.verticalDeltaToLeadMeters, 0.01)
    }

    @Test
    fun testPlannedRouteSummitDetection() {
        // Route points: 1000m -> 1500m -> 2200m (summit) -> 1900m -> 1600m
        val route = listOf(
            GeoPoint(45.000, 6.000, 1000.0),
            GeoPoint(45.001, 6.000, 1500.0),
            GeoPoint(45.002, 6.000, 2200.0), // Summit!
            GeoPoint(45.003, 6.000, 1900.0),
            GeoPoint(45.004, 6.000, 1600.0)
        )
        engine.setPlannedRoute(route)

        // Start local rider at beginning of route
        engine.ingestLocalPosition(route[0], timestampMs = 1000L)

        val profile = engine.elevationProfile.value
        assertEquals(5, profile.profilePoints.size)
        assertEquals(1000.0, profile.minAltitudeMeters)
        assertEquals(2200.0, profile.maxAltitudeMeters)

        // Total climb: (1500-1000) + (2200-1500) = 1200m
        assertEquals(1200.0, profile.totalClimbMeters, 0.01)
        // Total descent: (2200-1900) + (1900-1600) = 600m
        assertEquals(600.0, profile.totalDescentMeters, 0.01)

        // Upcoming summit should be detected at 2200m
        assertNotNull(profile.upcomingSummitAltitudeMeters)
        assertEquals(2200.0, profile.upcomingSummitAltitudeMeters!!)
        assertTrue(profile.upcomingSummitDistanceMeters!! > 0.0)
    }
}
