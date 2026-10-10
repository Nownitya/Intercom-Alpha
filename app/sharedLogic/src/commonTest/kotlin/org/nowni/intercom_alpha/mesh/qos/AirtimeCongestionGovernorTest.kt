package org.nowni.intercom_alpha.mesh.qos

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AirtimeCongestionGovernorTest {

    @Test
    fun testInitialState() {
        val governor = AirtimeCongestionGovernor()
        val snapshot = governor.metrics.value

        assertEquals(0.0f, snapshot.channelBusyRatio)
        assertEquals(0.0f, snapshot.estimatedCollisionRate)
        assertEquals(ChannelCongestionLevel.UNCONGESTED, snapshot.congestionLevel)
        assertEquals(FecRedundancyLevel.NONE, snapshot.fecRedundancy)
        assertEquals(20L, snapshot.bulkTelemetryPacingMs)
        assertEquals(0, snapshot.activeVoiceSpeakers)
    }

    @Test
    fun testCbrProgressionThroughCongestionTiers() = runTest {
        val config = CongestionGovernorConfig(
            evaluationWindowMs = 1000L,
            cbrModerateThreshold = 0.25f,
            cbrHighThreshold = 0.50f,
            cbrCriticalThreshold = 0.75f
        )
        val governor = AirtimeCongestionGovernor(config)
        var timeMs = 1000L

        // 1. Light traffic: 10 packets x 10,000 micros = 100,000 micros (CBR = 0.10)
        for (i in 0 until 10) {
            governor.recordAirtimeEvent(10_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)
            timeMs += 10L
        }
        assertEquals(ChannelCongestionLevel.UNCONGESTED, governor.metrics.value.congestionLevel)
        assertEquals(FecRedundancyLevel.NONE, governor.metrics.value.fecRedundancy)

        // 2. Moderate traffic: Add 25 packets x 10,000 micros = total 350,000 micros (CBR = 0.35)
        for (i in 0 until 25) {
            governor.recordAirtimeEvent(10_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)
            timeMs += 10L
        }
        assertEquals(ChannelCongestionLevel.MODERATE, governor.metrics.value.congestionLevel)
        assertEquals(FecRedundancyLevel.LIGHT, governor.metrics.value.fecRedundancy)
        assertEquals(120L, governor.metrics.value.bulkTelemetryPacingMs)

        // 3. High traffic: Add 25 packets x 10,000 micros = total 600,000 micros (CBR = 0.60)
        for (i in 0 until 25) {
            governor.recordAirtimeEvent(10_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)
            timeMs += 10L
        }
        assertEquals(ChannelCongestionLevel.HIGH, governor.metrics.value.congestionLevel)
        assertEquals(FecRedundancyLevel.MEDIUM, governor.metrics.value.fecRedundancy)
        assertEquals(600L, governor.metrics.value.bulkTelemetryPacingMs)

        // 4. Critical traffic: Add 25 packets x 10,000 micros = total 850,000 micros (CBR = 0.85)
        for (i in 0 until 25) {
            governor.recordAirtimeEvent(10_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)
            timeMs += 10L
        }
        assertEquals(ChannelCongestionLevel.CRITICAL, governor.metrics.value.congestionLevel)
        assertEquals(FecRedundancyLevel.HEAVY, governor.metrics.value.fecRedundancy)
        assertEquals(Long.MAX_VALUE, governor.metrics.value.bulkTelemetryPacingMs)
    }

    @Test
    fun testCollisionRateElevatesCongestionEvenUnderLowCbr() = runTest {
        val governor = AirtimeCongestionGovernor()
        val timeMs = 2000L

        // Record minimal airtime (CBR ~ 0.05)
        governor.recordAirtimeEvent(50_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)

        // Record high packet loss / collision rate (35 dropped out of 100 expected = 35%)
        governor.recordPacketLoss(lostPackets = 35, expectedPackets = 100, timestampMs = timeMs)

        val snapshot = governor.metrics.value
        assertEquals(ChannelCongestionLevel.CRITICAL, snapshot.congestionLevel)
        assertEquals(FecRedundancyLevel.HEAVY, snapshot.fecRedundancy)
        assertTrue(snapshot.estimatedCollisionRate >= 0.34f)
    }

    @Test
    fun testThrottleAdmissionPolicy() = runTest {
        val governor = AirtimeCongestionGovernor()
        val timeMs = 3000L

        // Force critical congestion
        governor.recordAirtimeEvent(850_000L, true, QoSPriority.ACTIVE_VOICE, timeMs)
        assertEquals(ChannelCongestionLevel.CRITICAL, governor.metrics.value.congestionLevel)

        // EMERGENCY_CRITICAL and ACTIVE_VOICE are NEVER throttled
        assertFalse(governor.shouldThrottle(QoSPriority.EMERGENCY_CRITICAL, timeMs - 5, timeMs))
        assertFalse(governor.shouldThrottle(QoSPriority.ACTIVE_VOICE, timeMs - 5, timeMs))

        // BULK_TELEMETRY is completely blocked under CRITICAL
        assertTrue(governor.shouldThrottle(QoSPriority.BULK_TELEMETRY, timeMs - 10000, timeMs))

        // ROUTING_CONTROL is throttled if interval < 250ms
        assertTrue(governor.shouldThrottle(QoSPriority.ROUTING_CONTROL, timeMs - 100, timeMs))
        assertFalse(governor.shouldThrottle(QoSPriority.ROUTING_CONTROL, timeMs - 300, timeMs))
    }

    @Test
    fun testActiveSpeakerTrackingAndExpiry() = runTest {
        val governor = AirtimeCongestionGovernor()

        governor.recordActiveVoiceSpeaker("rider-01", 1000L)
        governor.recordActiveVoiceSpeaker("rider-02", 1100L)
        governor.recordActiveVoiceSpeaker("rider-03", 1200L)

        governor.recordAirtimeEvent(5000L, true, QoSPriority.ACTIVE_VOICE, 1200L)
        assertEquals(3, governor.metrics.value.activeVoiceSpeakers)

        // Advance time beyond 600ms speaker expiry for rider-01 and rider-02
        governor.recordActiveVoiceSpeaker("rider-03", 1850L)
        governor.recordAirtimeEvent(5000L, true, QoSPriority.ACTIVE_VOICE, 1850L)
        assertEquals(1, governor.metrics.value.activeVoiceSpeakers)
    }

    @Test
    fun testResetClearsMetrics() = runTest {
        val governor = AirtimeCongestionGovernor()
        governor.recordAirtimeEvent(500_000L, true, QoSPriority.ACTIVE_VOICE, 1000L)
        governor.recordPacketLoss(20, 50, 1000L)
        governor.recordActiveVoiceSpeaker("lead", 1000L)

        assertTrue(governor.metrics.value.channelBusyRatio > 0.0f)

        governor.reset()
        val resetSnapshot = governor.metrics.value
        assertEquals(0.0f, resetSnapshot.channelBusyRatio)
        assertEquals(ChannelCongestionLevel.UNCONGESTED, resetSnapshot.congestionLevel)
        assertEquals(0, resetSnapshot.activeVoiceSpeakers)
    }
}
