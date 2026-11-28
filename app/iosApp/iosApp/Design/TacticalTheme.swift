import SwiftUI

// MARK: - Tactical Design System Tokens (ADR-0017 & ADR-0021)
// 1. One Accent Locked per Theme Preset
// 2. High Contrast Surfaces (7:1+ ratio under sunlight)
// 3. Shape Lock: Capsule (actions), 16pt (cards), 8pt (chips)
// 4. Zero emoji in chrome; 100% vector SF Symbols
// 5. Monospaced tabular figures for all numeric telemetry
public enum TacticalTheme {
    // Dynamic Theme Resolution
    public static var currentTheme: TacticalThemePreset {
        UiPreferencesManager.shared.theme
    }

    // Canvas & Surfaces
    public static var background: Color {
        switch currentTheme {
        case .tacticalAmber: return Color.black
        case .alpineHighVis: return Color(red: 11/255, green: 19/255, blue: 43/255)
        case .forestEmerald: return Color(red: 10/255, green: 19/255, blue: 12/255)
        case .desertOchre: return Color(red: 18/255, green: 14/255, blue: 11/255)
        case .monochrome: return Color.black
        }
    }

    public static var surfaceCard: Color {
        switch currentTheme {
        case .tacticalAmber: return Color(red: 15/255, green: 23/255, blue: 42/255) // Slate 900
        case .alpineHighVis: return Color(red: 28/255, green: 37/255, blue: 65/255) // Deep Slate
        case .forestEmerald: return Color(red: 17/255, green: 34/255, blue: 20/255) // Woodland
        case .desertOchre: return Color(red: 30/255, green: 23/255, blue: 19/255)   // Umber
        case .monochrome: return Color(red: 18/255, green: 18/255, blue: 18/255)   // Neutral 900
        }
    }

    public static var surfaceBorder: Color {
        switch currentTheme {
        case .tacticalAmber: return Color(red: 30/255, green: 41/255, blue: 59/255) // Slate 800
        case .alpineHighVis: return Color(red: 58/255, green: 80/255, blue: 107/255) // Steel
        case .forestEmerald: return Color(red: 31/255, green: 56/255, blue: 36/255) // Pine
        case .desertOchre: return Color(red: 51/255, green: 39/255, blue: 32/255)   // Sandstone
        case .monochrome: return Color(red: 38/255, green: 38/255, blue: 38/255)   // Neutral 800
        }
    }

    public static var surfaceElevated: Color {
        surfaceBorder
    }

    // Locked Interaction Accent per Preset
    public static var accent: Color {
        switch currentTheme {
        case .tacticalAmber: return Color(red: 245/255, green: 158/255, blue: 11/255) // Tactical Amber (#F59E0B)
        case .alpineHighVis: return Color(red: 0/255, green: 229/255, blue: 255/255)  // Cyber Cyan (#00E5FF)
        case .forestEmerald: return Color(red: 16/255, green: 185/255, blue: 129/255) // Trail Green (#10B981)
        case .desertOchre: return Color(red: 251/255, green: 191/255, blue: 36/255)   // Mojave Gold (#FBBF24)
        case .monochrome: return Color.white                                           // Pure White (#FFFFFF)
        }
    }

    public static var accentMuted: Color {
        accent.opacity(0.18)
    }

    // Semantic Status Colors
    public static var signalConnected: Color {
        switch currentTheme {
        case .alpineHighVis: return Color(red: 163/255, green: 230/255, blue: 53/255) // Lime
        case .monochrome: return Color(red: 148/255, green: 163/255, blue: 184/255)   // Slate 400
        default: return Color(red: 16/255, green: 185/255, blue: 129/255)             // Emerald (#10B981)
        }
    }

    public static var signalWarning: Color {
        Color(red: 245/255, green: 158/255, blue: 11/255) // Amber (#F59E0B)
    }

    public static var signalEmergency: Color {
        Color(red: 239/255, green: 68/255, blue: 68/255) // Crimson (#EF4444)
    }

    public static var signalHotMic: Color {
        Color(red: 249/255, green: 115/255, blue: 22/255) // Orange (#F97316)
    }

    // Text & Content Hierarchy
    public static var textPrimary: Color {
        switch currentTheme {
        case .forestEmerald: return Color(red: 240/255, green: 253/255, blue: 244/255)
        case .desertOchre: return Color(red: 255/255, green: 251/255, blue: 235/255)
        default: return Color(red: 248/255, green: 250/255, blue: 252/255) // Slate 50
        }
    }

    public static var textSecondary: Color {
        switch currentTheme {
        case .forestEmerald: return Color(red: 134/255, green: 239/255, blue: 172/255)
        case .desertOchre: return Color(red: 253/255, green: 230/255, blue: 138/255)
        case .monochrome: return Color(red: 163/255, green: 163/255, blue: 163/255)
        default: return Color(red: 148/255, green: 163/255, blue: 184/255) // Slate 400
        }
    }

    public static var textMuted: Color {
        switch currentTheme {
        case .monochrome: return Color(red: 115/255, green: 115/255, blue: 115/255)
        default: return Color(red: 100/255, green: 116/255, blue: 139/255) // Slate 500
        }
    }

    // Shape Radii & Metrics
    public static let cardRadius: CGFloat = 16
    public static let chipRadius: CGFloat = 8
    public static let borderWidth: CGFloat = 1

    // Glove-friendly minimum touch geometry
    public static let minTouchTarget: CGFloat = 64
    public static let pttShelfHeight: CGFloat = 84
}

// MARK: - View Modifiers for Shape Lock
public struct TacticalCardModifier: ViewModifier {
    public func body(content: Content) -> some View {
        content
            .background(TacticalTheme.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                    .stroke(TacticalTheme.surfaceBorder, lineWidth: TacticalTheme.borderWidth)
            )
    }
}

public struct TacticalChipModifier: ViewModifier {
    public func body(content: Content) -> some View {
        content
            .background(TacticalTheme.surfaceBorder.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous)
                    .stroke(TacticalTheme.surfaceBorder, lineWidth: TacticalTheme.borderWidth)
            )
    }
}

public extension View {
    func tacticalOutline(color: Color, lineWidth: CGFloat, cornerRadius: CGFloat) -> some View {
        overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(color, lineWidth: lineWidth)
        )
    }

    func tacticalCard() -> some View {
        modifier(TacticalCardModifier())
    }

    func tacticalChip() -> some View {
        modifier(TacticalChipModifier())
    }
}
