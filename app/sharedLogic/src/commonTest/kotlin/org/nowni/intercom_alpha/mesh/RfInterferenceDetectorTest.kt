package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RfInterferenceDetectorTest {

    @Test
    fun testInitialState() {
        val detector = RfInterferenceDetector()
        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.LOW, advice.interferenceLevel)
        assertEquals(1, advice.recommendedRedundancyCopies)
        assertEquals(100L, advice.recommendedScanIntervalMs)
        assertEquals(0L, advice.recommendedBackoffMs)
        assertEquals(1.0, advice.pdr)
    }

    @Test
    fun testCleanRfEnvironment() = runTest {
        val detector = RfInterferenceDetector()

        // 10 successful packets with stable RSSI around -65 dBm
        for (i in 1..10) {
            detector.recordPacketOutcome(wasReceived = true, rssi = -65, timestamp = 1000L + i * 100)
        }

        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.LOW, advice.interferenceLevel)
        assertEquals(1, advice.recommendedRedundancyCopies)
        assertEquals(1.0, advice.pdr)
        assertEquals(0.0, advice.rssiVariance)
    }

    @Test
    fun testModerateInterferenceDetection() = runTest {
        val detector = RfInterferenceDetector()

        // 8 received, 2 lost = 80% PDR
        detector.recordBatchOutcomes(
            receivedCount = 8,
            lostCount = 2,
            rssiSamples = listOf(-70, -71, -69, -70, -72, -69, -71, -70),
            timestamp = 2000L
        )

        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.MODERATE, advice.interferenceLevel)
        assertEquals(2, advice.recommendedRedundancyCopies)
        assertEquals(60L, advice.recommendedScanIntervalMs)
        assertEquals(15L, advice.recommendedBackoffMs)
        assertEquals(0.8, advice.pdr)
    }

    @Test
    fun testSevereInterferenceDetectionByPacketLoss() = runTest {
        val detector = RfInterferenceDetector()

        // 4 received, 6 lost = 40% PDR (< 70% threshold)
        detector.recordBatchOutcomes(
            receivedCount = 4,
            lostCount = 6,
            rssiSamples = listOf(-85, -86, -84, -85),
            timestamp = 3000L
        )

        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.SEVERE, advice.interferenceLevel)
        assertEquals(3, advice.recommendedRedundancyCopies)
        assertEquals(40L, advice.recommendedScanIntervalMs)
        assertEquals(40L, advice.recommendedBackoffMs)
        assertEquals(0.4, advice.pdr)
    }

    @Test
    fun testSevereInterferenceDetectionByRssiFlutter() = runTest {
        val detector = RfInterferenceDetector()

        // 100% PDR, but wildly oscillating RSSI (-45 dBm to -90 dBm) indicative of severe multipath fading
        val flutterRssi = listOf(-45, -90, -48, -92, -50, -88)
        detector.recordBatchOutcomes(
            receivedCount = 6,
            lostCount = 0,
            rssiSamples = flutterRssi,
            timestamp = 4000L
        )

        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.SEVERE, advice.interferenceLevel)
        assertTrue(advice.rssiVariance > 7.0)
        assertEquals(3, advice.recommendedRedundancyCopies)
    }

    @Test
    fun testSampleRetentionPurgeAllowsRecovery() = runTest {
        val detector = RfInterferenceDetector(sampleRetentionMs = 5000L)

        // Severe burst at t=1000ms
        detector.recordBatchOutcomes(receivedCount = 2, lostCount = 8, timestamp = 1000L)
        assertEquals(InterferenceLevel.SEVERE, detector.policyFlow.value.interferenceLevel)

        // At t=8000ms (> 5000ms later), record clean burst
        detector.recordBatchOutcomes(receivedCount = 10, lostCount = 0, timestamp = 8000L)

        // Old lost packets are purged, RF advice recovers to clean LOW
        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.LOW, advice.interferenceLevel)
        assertEquals(1.0, advice.pdr)
        assertEquals(1, advice.recommendedRedundancyCopies)
    }

    @Test
    fun testReset() = runTest {
        val detector = RfInterferenceDetector()
        detector.recordBatchOutcomes(receivedCount = 1, lostCount = 9)
        assertEquals(InterferenceLevel.SEVERE, detector.policyFlow.value.interferenceLevel)

        detector.reset()
        val advice = detector.policyFlow.value
        assertEquals(InterferenceLevel.LOW, advice.interferenceLevel)
        assertEquals(1.0, advice.pdr)
    }
}
