package org.nowni.intercom_alpha.mesh

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.mesh.phy.BlePhyMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdaptivePhySwitcherTest {

    @Test
    fun testInitialStateDefaultsToLe1M() = runTest {
        val switcher = AdaptivePhySwitcher()
        assertEquals(BlePhyMode.LE_1M, switcher.activePhy.value)
        assertEquals(BlePhyMode.LE_1M, switcher.getPeerPhy("peer-1"))
    }

    @Test
    fun testSwitchToLe2MOnHighLinkQualityAndRssi() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                samplesRequiredForUpgrade = 2,
                cooldownPeriodMs = 1000L
            )
        )
        val peerId = "peer-1"

        // Sample 1: Link quality > 85 and RSSI > -65
        var timestamp = 1000L
        var switched = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 90,
            rssiDbm = -60,
            packetLossRatio = 0.01,
            timestampMs = timestamp
        )
        assertFalse(switched, "Should not switch on first sample due to sample dampening")
        assertEquals(BlePhyMode.LE_1M, switcher.getPeerPhy(peerId))

        // Sample 2: Sustained strong link
        timestamp += 100L
        switched = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 92,
            rssiDbm = -58,
            packetLossRatio = 0.00,
            timestampMs = timestamp
        )
        assertTrue(switched, "Should switch to LE_2M on sustained high quality link")
        assertEquals(BlePhyMode.LE_2M, switcher.getPeerPhy(peerId))
        assertEquals(BlePhyMode.LE_2M, switcher.activePhy.value)
    }

    @Test
    fun testSwitchToCodedS8OnLowRssi() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                cooldownPeriodMs = 1000L
            )
        )
        val peerId = "peer-alpine"

        // Degrades below -85 dBm
        val switched = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 40,
            rssiDbm = -88,
            packetLossRatio = 0.05,
            timestampMs = 1000L
        )
        assertTrue(switched, "Should step down to CODED_S8 when RSSI < -85 dBm")
        assertEquals(BlePhyMode.CODED_S8, switcher.getPeerPhy(peerId))
        assertEquals(BlePhyMode.CODED_S8, switcher.activePhy.value)
    }

    @Test
    fun testSwitchToCodedS8OnHighPacketDrop() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                cooldownPeriodMs = 1000L
            )
        )
        val peerId = "peer-drop"

        // RSSI is acceptable (-78 dBm) but packet drop exceeds 15% (0.18)
        val switched = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 55,
            rssiDbm = -78,
            packetLossRatio = 0.18,
            timestampMs = 1000L
        )
        assertTrue(switched, "Should step down to CODED_S8 when packet drop > 15%")
        assertEquals(BlePhyMode.CODED_S8, switcher.getPeerPhy(peerId))
    }

    @Test
    fun testHysteresisDampeningPreventsRapidFlapping() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                samplesRequiredForUpgrade = 3,
                cooldownPeriodMs = 5000L,
                rssiUpgradeThresholdDbm = -65,
                rssiDowngradeThresholdDbm = -70,
                scoreUpgradeThreshold = 85,
                scoreDowngradeThreshold = 75
            )
        )
        val peerId = "peer-flap"
        var timestamp = 10_000L

        // Upgrade to LE_2M with 3 good samples
        repeat(3) {
            timestamp += 100L
            switcher.evaluatePeerLink(
                peerId = peerId,
                linkQualityScore = 90,
                rssiDbm = -60,
                packetLossRatio = 0.0,
                timestampMs = timestamp
            )
        }
        assertEquals(BlePhyMode.LE_2M, switcher.getPeerPhy(peerId))

        // Single slight dip during cooldown (timestamp + 1000ms < cooldown 5000ms)
        timestamp += 1000L
        val switchedDip = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 80,
            rssiDbm = -67,
            packetLossRatio = 0.02,
            timestampMs = timestamp
        )
        assertFalse(switchedDip, "Should not switch down during cooldown for minor fluctuation")
        assertEquals(BlePhyMode.LE_2M, switcher.getPeerPhy(peerId))
    }

    @Test
    fun testAsymmetricDowngradeFasterThanUpgrade() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                samplesRequiredForUpgrade = 3,
                samplesRequiredForDowngrade = 1,
                cooldownPeriodMs = 2000L
            )
        )
        val peerId = "peer-asym"
        var timestamp = 10_000L

        // First reach LE_2M
        repeat(3) {
            timestamp += 100L
            switcher.evaluatePeerLink(
                peerId = peerId,
                linkQualityScore = 95,
                rssiDbm = -55,
                packetLossRatio = 0.0,
                timestampMs = timestamp
            )
        }
        assertEquals(BlePhyMode.LE_2M, switcher.getPeerPhy(peerId))

        // After cooldown expires, single severe drop should trigger immediate step-down
        timestamp += 2500L
        val severeDrop = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 20,
            rssiDbm = -92,
            packetLossRatio = 0.25,
            timestampMs = timestamp
        )
        assertTrue(severeDrop, "Immediate downgrade to CODED_S8 on severe link degradation")
        assertEquals(BlePhyMode.CODED_S8, switcher.getPeerPhy(peerId))

        // Now test recovery: 1 good sample should NOT immediately jump back to 2M or 1M
        timestamp += 2500L
        val recoverSample1 = switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 90,
            rssiDbm = -60,
            packetLossRatio = 0.0,
            timestampMs = timestamp
        )
        assertFalse(recoverSample1, "Single good sample must not trigger upgrade from CODED_S8")
        assertEquals(BlePhyMode.CODED_S8, switcher.getPeerPhy(peerId))
    }

    @Test
    fun testDiagnosticsSnapshotAndEventEmission() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(
                samplesRequiredForUpgrade = 1,
                cooldownPeriodMs = 500L
            )
        )
        val peerId = "peer-telemetry"

        switcher.evaluatePeerLink(
            peerId = peerId,
            linkQualityScore = 92,
            rssiDbm = -58,
            packetLossRatio = 0.0,
            timestampMs = 1000L
        )

        val snapshot = switcher.getSnapshot()
        assertNotNull(snapshot)
        assertEquals(BlePhyMode.LE_2M, snapshot.activePhy)
        assertEquals(1, snapshot.totalPhySwitches)
        assertEquals(1, snapshot.peerPhyModes.size)
        assertEquals(BlePhyMode.LE_2M, snapshot.peerPhyModes[peerId])
        assertEquals("LE_2M", snapshot.activePhyName)
    }

    @Test
    fun testReset() = runTest {
        val switcher = AdaptivePhySwitcher(
            config = AdaptivePhyConfig(samplesRequiredForUpgrade = 1)
        )
        switcher.evaluatePeerLink(
            peerId = "peer-reset",
            linkQualityScore = 95,
            rssiDbm = -55,
            packetLossRatio = 0.0,
            timestampMs = 1000L
        )
        assertEquals(BlePhyMode.LE_2M, switcher.activePhy.value)

        switcher.reset()
        assertEquals(BlePhyMode.LE_1M, switcher.activePhy.value)
        assertEquals(0, switcher.getSnapshot().totalPhySwitches)
    }
}
