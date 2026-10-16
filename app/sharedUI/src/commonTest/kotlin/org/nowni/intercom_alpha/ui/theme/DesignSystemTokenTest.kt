package org.nowni.intercom_alpha.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignSystemTokenTest {

    @Test
    fun testTacticalTokensGloveAffordance() {
        // Enforce ADR-014 glove requirement: minimum touch target must be at least 84.dp
        assertTrue(IntercomTokens.MinGloveTouchTarget >= 84.dp, "Glove touch target must be at least 84dp")
        assertTrue(IntercomTokens.PttMasterDiameter >= 150.dp, "Master PTT button must be oversized for gloved actuation")
        assertEquals(2.dp, IntercomTokens.NeobrutalBorderThickness, "Neobrutalist border must be 2dp for high-sun legibility")
    }

    @Test
    fun testTacticalColorContrastPalettes() {
        // Verify dark tactical theme contracts
        val dark = DarkTacticalColors
        assertEquals(IntercomTokens.CockpitObsidian, dark.chassisBackground)
        assertEquals(IntercomTokens.SignalAmber, dark.signalActive)
        assertEquals(IntercomTokens.SignalHotMicOrange, dark.signalHotMic)
        assertEquals(IntercomTokens.SignalCodedPhyEmerald, dark.signalSuccess)
        assertEquals(IntercomTokens.SignalEmergencyCrimson, dark.signalEmergency)

        // Verify light tactical theme contracts
        val light = LightTacticalColors
        assertEquals(IntercomTokens.AlpineSnowBase, light.chassisBackground)
        assertTrue(light.textPrimary != dark.textPrimary, "Light and dark text must differ for contrast")
    }

    @Test
    fun testExpressiveTypographyHierarchy() {
        val typography = IntercomTacticalTypography
        // Verify tabular labels use monospace font for jitter-free telemetry readouts
        assertTrue(typography.labelLarge.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace)
        assertTrue(typography.labelMedium.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace)
        assertTrue(typography.labelSmall.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}
