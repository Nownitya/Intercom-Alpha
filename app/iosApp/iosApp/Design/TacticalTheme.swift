import SwiftUI

// MARK: - Tactical Design System Tokens (Anti-Slop Craft Compliant)
// 1. One Accent Locked: Tactical Amber (#F59E0B)
// 2. Single Neutral Family: Cool Slate on Pitch Black (#000000)
// 3. Shape Lock: Capsule (actions), 16pt (cards), 8pt (chips)
// 4. Zero emoji in chrome; 100% SF Symbols
public enum TacticalTheme {
    // Canvas & Surfaces
    public static let background = Color.black // Pure pitch black for OLED zero battery drain
    public static let surfaceCard = Color(red: 15/255, green: 23/255, blue: 42/255) // Slate 900
    public static let surfaceBorder = Color(red: 30/255, green: 41/255, blue: 59/255) // Slate 800
    public static let surfaceElevated = Color(red: 30/255, green: 41/255, blue: 59/255) // Slate 800

    // One Accent Locked (Anti-Slop Rule 2)
    public static let accent = Color(red: 245/255, green: 158/255, blue: 11/255) // Tactical Amber (#F59E0B)
    public static let accentMuted = Color(red: 245/255, green: 158/255, blue: 11/255).opacity(0.18)

    // Semantic Status Colors (strictly status-only, never generic decorative accents)
    public static let signalConnected = Color(red: 16/255, green: 185/255, blue: 129/255) // Emerald (#10B981)
    public static let signalWarning = Color(red: 245/255, green: 158/255, blue: 11/255)   // Amber (#F59E0B)
    public static let signalEmergency = Color(red: 239/255, green: 68/255, blue: 68/255)  // Crimson (#EF4444)
    public static let signalHotMic = Color(red: 249/255, green: 115/255, blue: 22/255)    // Orange (#F97316)

    // Text & Content Hierarchy (Cool Slate family)
    public static let textPrimary = Color(red: 248/255, green: 250/255, blue: 252/255) // Slate 50
    public static let textSecondary = Color(red: 148/255, green: 163/255, blue: 184/255) // Slate 400
    public static let textMuted = Color(red: 100/255, green: 116/255, blue: 139/255) // Slate 500

    // Shape Radii
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
    func tacticalCard() -> some View {
        modifier(TacticalCardModifier())
    }

    func tacticalChip() -> some View {
        modifier(TacticalChipModifier())
    }
}

