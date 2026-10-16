package org.nowni.intercom_alpha.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Master Tactical Design Tokens for Intercom-Alpha Cockpit.
 * Adheres strictly to ADR-014: Pragmatic Tactical Realism fusing Material 3 Expressive,
 * Skeuomorphism, Neobrutalism, Claymorphism, and Liquid Glass.
 *
 * Designed specifically for high-contrast outdoor glanceability under direct sunlight,
 * through tinted motorcycle helmet visors, and with heavy winter/leather riding gloves.
 */
object IntercomTokens {

    // MARK: - Core Palette (Tactical High-Visibility)
    val CockpitObsidian = Color(0xFF0D1117)
    val CockpitChassis = Color(0xFF161B22)
    val CockpitSurfaceElevated = Color(0xFF21262D)
    val CockpitSurfacePressed = Color(0xFF13171D)

    // Neobrutalist High-Contrast Perimeter Outlines
    val NeobrutalBorderIdle = Color(0x33FFFFFF)       // Crisp white rim 20%
    val NeobrutalBorderDark = Color(0xFF000000)       // Pitch black contrast stroke
    val NeobrutalBorderActive = Color(0xFFFF9F0A)     // Tactical Safety Amber
    val NeobrutalBorderHotMic = Color(0xFFFF453A)     // Hot Mic Orange-Red
    val NeobrutalBorderEmergency = Color(0xFFFF2D55)  // Life-Safety SOS Crimson

    // High-Sun Glare Backgrounds (Light Mode)
    val AlpineSnowBase = Color(0xFFF0F4F8)
    val AlpineSnowChassis = Color(0xFFE2E8F0)
    val AlpineSnowCard = Color(0xFFFFFFFF)

    // Tactical Signal Accents
    val SignalAmber = Color(0xFFFF9F0A)
    val SignalHotMicOrange = Color(0xFFFF453A)
    val SignalCodedPhyEmerald = Color(0xFF30D158)
    val SignalSatelliteCyan = Color(0xFF64D2FF)
    val SignalMeshViolet = Color(0xFFBF5AF2)
    val SignalEmergencyCrimson = Color(0xFFFF3B30)

    // Specular Liquid Glass Overlays
    val LiquidGlassBaseDark = Color(0x1F2A3749)      // Frosted translucent blur
    val LiquidGlassSpecularEdge = Color(0x40FFFFFF)  // 25% specular glass reflection
    val LiquidGlassBaseLight = Color(0x33E2E8F0)

    // MARK: - Ergonomic Glove Touch Dimensions
    val MinGloveTouchTarget: Dp = 84.dp             // Well above standard 48dp for heavy leather gloves
    val PttMasterDiameter: Dp = 160.dp             // Giant glove-actuated cockpit master actuator
    val NeobrutalBorderThickness: Dp = 2.dp        // Hard 2dp high-sun outline
    val HeavyBorderThickness: Dp = 3.dp            // 3dp for hot-mic and emergency states
    val CardCornerRadius: Dp = 18.dp               // M3 Expressive squircle pill curvature
    val PttCornerRadius: Dp = 999.dp               // Concentric circular actuator
}

/**
 * Extended Color Roles for Tactical Intercom Cockpit.
 */
@Immutable
data class IntercomTacticalColors(
    val chassisBackground: Color,
    val surfaceElevated: Color,
    val surfacePressed: Color,
    val neobrutalOutline: Color,
    val signalActive: Color,
    val signalHotMic: Color,
    val signalSuccess: Color,
    val signalEmergency: Color,
    val liquidGlassTint: Color,
    val specularRim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMonospaceTactical: Color
)

val DarkTacticalColors = IntercomTacticalColors(
    chassisBackground = IntercomTokens.CockpitObsidian,
    surfaceElevated = IntercomTokens.CockpitSurfaceElevated,
    surfacePressed = IntercomTokens.CockpitSurfacePressed,
    neobrutalOutline = IntercomTokens.NeobrutalBorderIdle,
    signalActive = IntercomTokens.SignalAmber,
    signalHotMic = IntercomTokens.SignalHotMicOrange,
    signalSuccess = IntercomTokens.SignalCodedPhyEmerald,
    signalEmergency = IntercomTokens.SignalEmergencyCrimson,
    liquidGlassTint = IntercomTokens.LiquidGlassBaseDark,
    specularRim = IntercomTokens.LiquidGlassSpecularEdge,
    textPrimary = Color(0xFFF0F6FC),
    textSecondary = Color(0xFF8B949E),
    textMonospaceTactical = Color(0xFF58A6FF)
)

val LightTacticalColors = IntercomTacticalColors(
    chassisBackground = IntercomTokens.AlpineSnowBase,
    surfaceElevated = IntercomTokens.AlpineSnowCard,
    surfacePressed = IntercomTokens.AlpineSnowChassis,
    neobrutalOutline = IntercomTokens.NeobrutalBorderDark,
    signalActive = Color(0xFFD97706),
    signalHotMic = Color(0xFFDC2626),
    signalSuccess = Color(0xFF059669),
    signalEmergency = Color(0xFFB91C1C),
    liquidGlassTint = IntercomTokens.LiquidGlassBaseLight,
    specularRim = Color(0x66FFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMonospaceTactical = Color(0xFF1D4ED8)
)

val LocalTacticalColors = staticCompositionLocalOf { DarkTacticalColors }

/**
 * Standard M3 ColorScheme mapping.
 */
val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = IntercomTokens.SignalAmber,
    onPrimary = Color.Black,
    primaryContainer = IntercomTokens.CockpitChassis,
    onPrimaryContainer = IntercomTokens.SignalAmber,
    secondary = IntercomTokens.SignalCodedPhyEmerald,
    onSecondary = Color.Black,
    error = IntercomTokens.SignalEmergencyCrimson,
    onError = Color.White,
    background = IntercomTokens.CockpitObsidian,
    onBackground = Color(0xFFF0F6FC),
    surface = IntercomTokens.CockpitChassis,
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = IntercomTokens.CockpitSurfaceElevated,
    onSurfaceVariant = Color(0xFF8B949E),
    outline = IntercomTokens.NeobrutalBorderIdle
)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = Color.White,
    primaryContainer = IntercomTokens.AlpineSnowChassis,
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    error = Color(0xFFB91C1C),
    onError = Color.White,
    background = IntercomTokens.AlpineSnowBase,
    onBackground = Color(0xFF0F172A),
    surface = IntercomTokens.AlpineSnowCard,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = IntercomTokens.AlpineSnowChassis,
    onSurfaceVariant = Color(0xFF475569),
    outline = IntercomTokens.NeobrutalBorderDark
)
