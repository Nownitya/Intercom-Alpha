import SwiftUI

// MARK: - Tactical Design System Tokens (iOS 18+ HIG)
// Adheres strictly to ADR-014: Pragmatic Tactical Realism fusing Material 3 Expressive,
// Skeuomorphism, Neobrutalism, Claymorphism, and Liquid Glass.
// Rule 1: 100% Native SwiftUI. Zero Compose on iOS.

public enum TacticalTokens {
    // MARK: - Core Palette
    public static let cockpitObsidian = Color(red: 0.05, green: 0.07, blue: 0.09) // #0D1117
    public static let cockpitChassis = Color(red: 0.09, green: 0.11, blue: 0.13)  // #161B22
    public static let cockpitElevated = Color(red: 0.13, green: 0.15, blue: 0.18) // #21262D
    public static let cockpitPressed = Color(red: 0.07, green: 0.09, blue: 0.11)  // #13171D

    // Neobrutalist High-Contrast Perimeter Outlines
    public static let neobrutalBorderIdle = Color.white.opacity(0.20)
    public static let neobrutalBorderDark = Color.black
    public static let neobrutalBorderActive = Color(red: 1.0, green: 0.62, blue: 0.04)   // Tactical Safety Amber #FF9F0A
    public static let neobrutalBorderHotMic = Color(red: 1.0, green: 0.27, blue: 0.23)   // Hot-Mic Orange #FF453A
    public static let neobrutalBorderEmergency = Color(red: 1.0, green: 0.18, blue: 0.33)// Crimson SOS #FF2D55

    // Tactical Signal Accents
    public static let signalAmber = Color(red: 1.0, green: 0.62, blue: 0.04)
    public static let signalHotMicOrange = Color(red: 1.0, green: 0.27, blue: 0.23)
    public static let signalCodedPhyEmerald = Color(red: 0.19, green: 0.82, blue: 0.35)
    public static let signalSatelliteCyan = Color(red: 0.39, green: 0.82, blue: 1.0)
    public static let signalMeshViolet = Color(red: 0.75, green: 0.35, blue: 0.95)
    public static let signalEmergencyCrimson = Color(red: 1.0, green: 0.23, blue: 0.19)

    // Liquid Glass Materials
    public static let liquidGlassTintDark = Color(red: 0.16, green: 0.22, blue: 0.29).opacity(0.20)
    public static let liquidGlassSpecular = Color.white.opacity(0.25)

    // MARK: - Glove Dimensions (ADR-014)
    public static let minGloveTouchTarget: CGFloat = 84.0
    public static let masterPttDiameter: CGFloat = 160.0
    public static let neobrutalBorderWidth: CGFloat = 2.0
    public static let heavyBorderWidth: CGFloat = 3.0
    public static let cardCornerRadius: CGFloat = 18.0
}

// MARK: - View Modifiers

/// 1. Neobrutalist High-Contrast Border Modifier
public struct NeobrutalistOutlineModifier: ViewModifier {
    public let color: Color
    public let lineWidth: CGFloat
    public let cornerRadius: CGFloat

    public init(
        color: Color = TacticalTokens.neobrutalBorderIdle,
        lineWidth: CGFloat = TacticalTokens.neobrutalBorderWidth,
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius
    ) {
        self.color = color
        self.lineWidth = lineWidth
        self.cornerRadius = cornerRadius
    }

    public func body(content: Content) -> some View {
        content
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(color, lineWidth: lineWidth)
            )
    }
}

/// 2. Claymorphic Tactile Depth Modifier (Dual specular & recessed shadow simulation)
public struct ClaymorphicDepthModifier: ViewModifier {
    public let surfaceColor: Color
    public let isPressed: Bool
    public let cornerRadius: CGFloat

    public init(
        surfaceColor: Color = TacticalTokens.cockpitElevated,
        isPressed: Bool = false,
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius
    ) {
        self.surfaceColor = surfaceColor
        self.isPressed = isPressed
        self.cornerRadius = cornerRadius
    }

    public func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: isPressed
                                ? [surfaceColor.opacity(0.85), surfaceColor.opacity(0.98)]
                                : [surfaceColor.opacity(1.0), surfaceColor.opacity(0.82)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .shadow(
                        color: isPressed ? Color.black.opacity(0.2) : Color.black.opacity(0.55),
                        radius: isPressed ? 2 : 6,
                        x: isPressed ? 1 : 3,
                        y: isPressed ? 1 : 5
                    )
            )
            .overlay(
                // Top-left specular highlight rim
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: isPressed
                                ? [Color.black.opacity(0.4), Color.clear]
                                : [Color.white.opacity(0.28), Color.clear],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

/// 3. Skeuomorphic Machined Dial Bezel Modifier
public struct SkeuomorphicBezelModifier: ViewModifier {
    public let rimColor: Color
    public let knurlColor: Color
    public let thickness: CGFloat

    public init(
        rimColor: Color = Color(red: 0.18, green: 0.20, blue: 0.23),
        knurlColor: Color = Color.white.opacity(0.20),
        thickness: CGFloat = 4.0
    ) {
        self.rimColor = rimColor
        self.knurlColor = knurlColor
        self.thickness = thickness
    }

    public func body(content: Content) -> some View {
        content
            .overlay(
                Circle()
                    .stroke(rimColor, lineWidth: thickness)
            )
            .overlay(
                Circle()
                    .stroke(knurlColor, style: StrokeStyle(lineWidth: 1.0, dash: [2, 4]))
                    .padding(thickness)
            )
    }
}

// MARK: - View Extension Wrappers
public extension View {
    func neobrutalistOutline(
        color: Color = TacticalTokens.neobrutalBorderIdle,
        lineWidth: CGFloat = TacticalTokens.neobrutalBorderWidth,
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius
    ) -> some View {
        self.modifier(NeobrutalistOutlineModifier(color: color, lineWidth: lineWidth, cornerRadius: cornerRadius))
    }

    func claymorphicDepth(
        surfaceColor: Color = TacticalTokens.cockpitElevated,
        isPressed: Bool = false,
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius
    ) -> some View {
        self.modifier(ClaymorphicDepthModifier(surfaceColor: surfaceColor, isPressed: isPressed, cornerRadius: cornerRadius))
    }

    func skeuomorphicBezel(
        rimColor: Color = Color(red: 0.18, green: 0.20, blue: 0.23),
        knurlColor: Color = Color.white.opacity(0.20),
        thickness: CGFloat = 4.0
    ) -> some View {
        self.modifier(SkeuomorphicBezelModifier(rimColor: rimColor, knurlColor: knurlColor, thickness: thickness))
    }
}
