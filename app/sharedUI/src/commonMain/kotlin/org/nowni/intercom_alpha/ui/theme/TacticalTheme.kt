package org.nowni.intercom_alpha.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tactical Design Tokens implementing Anti-Slop laws & ADR-0021:
 * - 5 Static High-Contrast Themes (Tactical Amber, Alpine High-Vis, Forest Emerald, Desert Ochre, Monochrome)
 * - 5 Activity Profiles (Motorbike, Car, Bicycle, Hiking, Minimal)
 * - Shape Lock: Capsule (actions), 18dp (cards), 8dp (chips)
 * - Monospaced numeric typography for zero layout jitter
 */
enum class TacticalThemePreset {
    TACTICAL_AMBER,
    ALPINE_HIGH_VIS,
    FOREST_EMERALD,
    DESERT_OCHRE,
    MONOCHROME
}

enum class ActivityProfile(val displayName: String) {
    MOTORBIKE("Motorbike"),
    CAR("Car / 4x4"),
    BICYCLE("Bicycle"),
    HIKING("Mountain Trails"),
    MINIMAL("Minimal")
}

object IntercomTokens {
    // Default Canvas & Surfaces
    val CockpitObsidian = Color(0xFF000000)      // Pure pitch black for OLED zero-drain
    val SurfaceCard = Color(0xFF0F172A)           // Cool Slate 900
    val SurfaceBorder = Color(0xFF1E293B)         // Cool Slate 800
    val SurfaceElevated = Color(0xFF1E293B)       // Elevated components
    val AlpineSnowBase = Color(0xFFF8FAFC)        // High-vis daytime fallback

    // One Accent Locked (Anti-Slop Rule 2)
    val SignalAmber = Color(0xFFF59E0B)           // Primary interaction / PTT / Active tab
    val SignalAmberMuted = Color(0x33F59E0B)      // 20% amber tint for backgrounds

    // Semantic Status Colors
    val SignalConnectedEmerald = Color(0xFF10B981) // RF link healthy / nominal
    val SignalCodedPhyEmerald = Color(0xFF10B981)  // Backward compatibility token
    val SignalWarningAmber = Color(0xFFF59E0B)     // Slope >8% / CBR high / loss warning
    val SignalEmergencyCrimson = Color(0xFFEF4444) // Crash / SOS / Slope >12%
    val SignalHotMicOrange = Color(0xFFF97316)     // Open VOX broadcast

    // Text & Content Hierarchy (Cool Slate family)
    val TextPrimary = Color(0xFFF8FAFC)           // Slate 50
    val TextSecondary = Color(0xFF94A3B8)         // Slate 400
    val TextMuted = Color(0xFF64748B)             // Slate 500

    // Glove-Friendly Geometry & Shape Lock
    val MinGloveTouchTarget: Dp = 84.dp
    val PttMasterDiameter: Dp = 160.dp
    val PttShelfHeight: Dp = 84.dp
    val CardCornerRadius: Dp = 18.dp
    val ChipCornerRadius: Dp = 8.dp
    val NeobrutalBorderThickness: Dp = 2.dp
    val HeavyBorderThickness: Dp = 3.dp

    // Corner Shapes
    val ShapeCard = RoundedCornerShape(CardCornerRadius)
    val ShapeChip = RoundedCornerShape(ChipCornerRadius)
}

@Immutable
data class TacticalColorPalette(
    val chassisBackground: Color,
    val surfaceCard: Color,
    val surfaceBorder: Color,
    val signalActive: Color,
    val signalSuccess: Color,
    val signalWarning: Color,
    val signalEmergency: Color,
    val signalHotMic: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

// 1. Tactical Amber Palette (Default Dark)
val TacticalAmberPalette = TacticalColorPalette(
    chassisBackground = IntercomTokens.CockpitObsidian,
    surfaceCard = IntercomTokens.SurfaceCard,
    surfaceBorder = IntercomTokens.SurfaceBorder,
    signalActive = IntercomTokens.SignalAmber,
    signalSuccess = IntercomTokens.SignalConnectedEmerald,
    signalWarning = IntercomTokens.SignalWarningAmber,
    signalEmergency = IntercomTokens.SignalEmergencyCrimson,
    signalHotMic = IntercomTokens.SignalHotMicOrange,
    textPrimary = IntercomTokens.TextPrimary,
    textSecondary = IntercomTokens.TextSecondary,
    textMuted = IntercomTokens.TextMuted
)

// 2. Alpine High-Vis Palette (Daylight Glare)
val AlpineHighVisPalette = TacticalColorPalette(
    chassisBackground = IntercomTokens.AlpineSnowBase,
    surfaceCard = Color(0xFFFFFFFF),
    surfaceBorder = Color(0xFFCBD5E1),
    signalActive = Color(0xFF0284C7),
    signalSuccess = Color(0xFF16A34A),
    signalWarning = Color(0xFFD97706),
    signalEmergency = Color(0xFFDC2626),
    signalHotMic = Color(0xFF0284C7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8)
)

// 3. Forest Emerald Palette
val ForestEmeraldPalette = TacticalColorPalette(
    chassisBackground = Color(0xFF0A130C),
    surfaceCard = Color(0xFF112214),
    surfaceBorder = Color(0xFF1F3824),
    signalActive = Color(0xFF10B981),
    signalSuccess = Color(0xFF10B981),
    signalWarning = Color(0xFFF59E0B),
    signalEmergency = Color(0xFFEF4444),
    signalHotMic = Color(0xFF10B981),
    textPrimary = Color(0xFFF0FDF4),
    textSecondary = Color(0xFF86EFAC),
    textMuted = Color(0xFF4ADE80)
)

// 4. Desert Ochre Palette
val DesertOchrePalette = TacticalColorPalette(
    chassisBackground = Color(0xFF120E0B),
    surfaceCard = Color(0xFF1E1713),
    surfaceBorder = Color(0xFF332720),
    signalActive = Color(0xFFFBBF24),
    signalSuccess = Color(0xFF14B8A6),
    signalWarning = Color(0xFFF97316),
    signalEmergency = Color(0xFFEF4444),
    signalHotMic = Color(0xFFFBBF24),
    textPrimary = Color(0xFFFFFBEB),
    textSecondary = Color(0xFFFDE68A),
    textMuted = Color(0xFFD97706)
)

// 5. Monochrome Minimal Palette
val MonochromeMinimalPalette = TacticalColorPalette(
    chassisBackground = Color(0xFF000000),
    surfaceCard = Color(0xFF121212),
    surfaceBorder = Color(0xFF262626),
    signalActive = Color(0xFFFFFFFF),
    signalSuccess = Color(0xFF94A3B8),
    signalWarning = Color(0xFFE2E8F0),
    signalEmergency = Color(0xFFEF4444),
    signalHotMic = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA3A3A3),
    textMuted = Color(0xFF737373)
)

val DarkTacticalColors = TacticalAmberPalette
val LightTacticalColors = AlpineHighVisPalette

fun getTacticalPalette(preset: TacticalThemePreset): TacticalColorPalette {
    return when (preset) {
        TacticalThemePreset.TACTICAL_AMBER -> TacticalAmberPalette
        TacticalThemePreset.ALPINE_HIGH_VIS -> AlpineHighVisPalette
        TacticalThemePreset.FOREST_EMERALD -> ForestEmeraldPalette
        TacticalThemePreset.DESERT_OCHRE -> DesertOchrePalette
        TacticalThemePreset.MONOCHROME -> MonochromeMinimalPalette
    }
}

val LocalTacticalColors = staticCompositionLocalOf { DarkTacticalColors }

val IntercomTacticalTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    // Monospaced labels for jitter-free telemetry and RF numbers
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun IntercomTheme(
    themePreset: TacticalThemePreset = TacticalThemePreset.TACTICAL_AMBER,
    content: @Composable () -> Unit
) {
    val tacticalColors = getTacticalPalette(themePreset)

    val materialColors: ColorScheme = darkColorScheme(
        background = tacticalColors.chassisBackground,
        surface = tacticalColors.surfaceCard,
        primary = tacticalColors.signalActive,
        onBackground = tacticalColors.textPrimary,
        onSurface = tacticalColors.textPrimary
    )

    CompositionLocalProvider(
        LocalTacticalColors provides tacticalColors
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = IntercomTacticalTypography,
            content = content
        )
    }
}
