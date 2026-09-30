package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.InterferenceLevel
import org.nowni.intercom_alpha.mesh.LinkQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveBitrateControllerTest {

    @Test
    fun testInitialState() {
        val controller = AdaptiveBitrateController(initialProfile = AudioProfile.MEDIUM)
        assertEquals(AudioProfile.MEDIUM, controller.currentProfile.value)
        assertEquals(AdaptationMode.AUTO, controller.adaptationState.value.mode)
        assertEquals(0, controller.adaptationState.value.candidateCycles)
    }

    @Test
    fun testRapidSevereDowngrade() = runTest {
        val controller = AdaptiveBitrateController(initialProfile = AudioProfile.HIGH)

        // Severe RF interference and critical packet drop
        val profile = controller.evaluateRfConditions(
            linkQuality = LinkQuality.CRITICAL,
            interferenceLevel = InterferenceLevel.SEVERE,
            pdr = 0.45,
            linkMarginDb = 1.5
        )

        // Must immediately step down to ULTRA_LOW to preserve stream connectivity
        assertEquals(AudioProfile.ULTRA_LOW, profile)
        assertEquals(AudioProfile.ULTRA_LOW, controller.currentProfile.value)
    }

    @Test
    fun testConservativeStepUpHysteresis() = runTest {
        val controller = AdaptiveBitrateController(
            initialProfile = AudioProfile.LOW,
            hysteresisCycles = 3
        )

        // Pristine RF conditions: EXCELLENT link, LOW noise, 98% PDR, 22 dB link margin
        // Cycle 1: Should NOT upgrade immediately
        val c1 = controller.evaluateRfConditions(LinkQuality.EXCELLENT, InterferenceLevel.LOW, 0.98, 22.0)
        assertEquals(AudioProfile.LOW, c1)
        assertEquals(AudioProfile.HIGH, controller.adaptationState.value.candidateProfile)
        assertEquals(1, controller.adaptationState.value.candidateCycles)

        // Cycle 2: Still sustained, candidate count advances to 2
        val c2 = controller.evaluateRfConditions(LinkQuality.EXCELLENT, InterferenceLevel.LOW, 0.98, 22.0)
        assertEquals(AudioProfile.LOW, c2)
        assertEquals(2, controller.adaptationState.value.candidateCycles)

        // Cycle 3: 3rd sustained cycle meets hysteresis threshold -> upgrades to HIGH!
        val c3 = controller.evaluateRfConditions(LinkQuality.EXCELLENT, InterferenceLevel.LOW, 0.98, 22.0)
        assertEquals(AudioProfile.HIGH, c3)
        assertEquals(AudioProfile.HIGH, controller.currentProfile.value)
        assertEquals(0, controller.adaptationState.value.candidateCycles)
    }

    @Test
    fun testFlappingSuppression() = runTest {
        val controller = AdaptiveBitrateController(
            initialProfile = AudioProfile.LOW,
            hysteresisCycles = 3
        )

        // Cycle 1: Pristine -> candidate = HIGH, count = 1
        controller.evaluateRfConditions(LinkQuality.EXCELLENT, InterferenceLevel.LOW, 0.99, 20.0)
        assertEquals(1, controller.adaptationState.value.candidateCycles)

        // Cycle 2: Glitch / multipath interference (drops to MARGINAL) -> resets upgrade candidate
        controller.evaluateRfConditions(LinkQuality.MARGINAL, InterferenceLevel.MODERATE, 0.75, 6.0)
        assertEquals(AudioProfile.LOW, controller.currentProfile.value)

        // Cycle 3: Pristine again -> resets to cycle 1 of candidate, does NOT prematurely jump
        controller.evaluateRfConditions(LinkQuality.EXCELLENT, InterferenceLevel.LOW, 0.99, 20.0)
        assertEquals(AudioProfile.LOW, controller.currentProfile.value)
        assertEquals(1, controller.adaptationState.value.candidateCycles)
    }

    @Test
    fun testManualOverride() = runTest {
        val controller = AdaptiveBitrateController(initialProfile = AudioProfile.MEDIUM)

        controller.setManualOverride(AudioProfile.HIGH)
        assertEquals(AudioProfile.HIGH, controller.currentProfile.value)
        assertEquals(AdaptationMode.MANUAL_OVERRIDE, controller.adaptationState.value.mode)

        // Severe RF degradation fed into controller
        val profile = controller.evaluateRfConditions(
            linkQuality = LinkQuality.CRITICAL,
            interferenceLevel = InterferenceLevel.SEVERE,
            pdr = 0.30,
            linkMarginDb = 0.0
        )
        // Locked in manual override; must remain HIGH
        assertEquals(AudioProfile.HIGH, profile)
        assertEquals(AudioProfile.HIGH, controller.currentProfile.value)

        // Switch back to auto mode and re-evaluate
        controller.setAutoMode()
        assertEquals(AdaptationMode.AUTO, controller.adaptationState.value.mode)

        val autoProfile = controller.evaluateRfConditions(
            linkQuality = LinkQuality.CRITICAL,
            interferenceLevel = InterferenceLevel.SEVERE,
            pdr = 0.30,
            linkMarginDb = 0.0
        )
        assertEquals(AudioProfile.ULTRA_LOW, autoProfile)
    }

    @Test
    fun testReset() = runTest {
        val controller = AdaptiveBitrateController(initialProfile = AudioProfile.HIGH)
        controller.setManualOverride(AudioProfile.ULTRA_LOW)

        controller.reset(initial = AudioProfile.MEDIUM)
        assertEquals(AudioProfile.MEDIUM, controller.currentProfile.value)
        assertEquals(AdaptationMode.AUTO, controller.adaptationState.value.mode)
        assertEquals(0, controller.adaptationState.value.candidateCycles)
    }
}
