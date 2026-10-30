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
 * Tactical Design Tokens implementing Anti-Slop laws:
 * - One Accent Locked: Tactical Amber (#F59E0B)
 * - Single Neutral Family: Cool Slate (#000000, #0F172A, #1E293B, #64748B, #F8FAFC)
 * - Shape Lock: Capsule (actions), 16dp (cards), 8dp (chips)
 * - Monospaced numeric typography for zero layout jitter
 */
object IntercomTokens {
    // Canvas & Surfaces
    val CockpitObsidian = Color(0xFF000000)      // Pure pitch black for OLED zero-drain
    val SurfaceCard = Color(0xFF0F172A)           // Cool Slate 900
    val SurfaceBorder = Color(0xFF1E293B)         // Cool Slate 800
    val SurfaceElevated = Color(0xFF1E293B)       // Elevated components
    val AlpineSnowBase = Color(0xFFF8FAFC)        // High-vis daytime fallback

    // One Accent Locked (Anti-Slop Rule 2)
    val SignalAmber = Color(0xFFF59E0B)           // Primary interaction / PTT / Active tab
    val SignalAmberMuted = Color(0x33F59E0B)      // 20% amber tint for backgrounds

    // Semantic Status Colors (strictly status-only, never generic decorative accents)
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

val DarkTacticalColors = TacticalColorPalette(
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

val LightTacticalColors = TacticalColorPalette(
    chassisBackground = IntercomTokens.AlpineSnowBase,
    surfaceCard = Color(0xFFFFFFFF),
    surfaceBorder = Color(0xFFE2E8F0),
    signalActive = IntercomTokens.SignalAmber,
    signalSuccess = IntercomTokens.SignalConnectedEmerald,
    signalWarning = IntercomTokens.SignalWarningAmber,
    signalEmergency = IntercomTokens.SignalEmergencyCrimson,
    signalHotMic = IntercomTokens.SignalHotMicOrange,
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8)
)

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tacticalColors = if (darkTheme) DarkTacticalColors else LightTacticalColors

    val materialColors: ColorScheme = if (darkTheme) {
        darkColorScheme(
            background = tacticalColors.chassisBackground,
            surface = tacticalColors.surfaceCard,
            primary = tacticalColors.signalActive,
            onBackground = tacticalColors.textPrimary,
            onSurface = tacticalColors.textPrimary
        )
    } else {
        lightColorScheme(
            background = tacticalColors.chassisBackground,
            surface = tacticalColors.surfaceCard,
            primary = tacticalColors.signalActive,
            onBackground = tacticalColors.textPrimary,
            onSurface = tacticalColors.textPrimary
        )
    }

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
