import SwiftUI

// MARK: - Tactical Liquid Glass View & Modifiers (iOS 18+ HIG)
// Implements ultra-thin frosted glass materials with specular edge reflections.
// Floats gracefully over real-time tactical trail maps and spatial radar without visual obstruction.

public struct LiquidGlassBackground: View {
    public let cornerRadius: CGFloat
    public let tintColor: Color
    public let specularHighlight: Color

    public init(
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius,
        tintColor: Color = TacticalTokens.liquidGlassTintDark,
        specularHighlight: Color = TacticalTokens.liquidGlassSpecular
    ) {
        self.cornerRadius = cornerRadius
        self.tintColor = tintColor
        self.specularHighlight = specularHighlight
    }

    public var body: some View {
        ZStack {
            // Native iOS Ultra-Thin Frosted Material
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)

            // Deep tactical tint overlay
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(tintColor)

            // Specular light reflection rim (1pt top specular edge)
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(
                    LinearGradient(
                        colors: [
                            specularHighlight,
                            specularHighlight.opacity(0.10),
                            Color.clear
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: 1.0
                )
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

public struct LiquidGlassModifier: ViewModifier {
    public let cornerRadius: CGFloat
    public let tintColor: Color

    public init(
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius,
        tintColor: Color = TacticalTokens.liquidGlassTintDark
    ) {
        self.cornerRadius = cornerRadius
        self.tintColor = tintColor
    }

    public func body(content: Content) -> some View {
        content
            .background(
                LiquidGlassBackground(cornerRadius: cornerRadius, tintColor: tintColor)
            )
    }
}

public extension View {
    func liquidGlassBackground(
        cornerRadius: CGFloat = TacticalTokens.cardCornerRadius,
        tintColor: Color = TacticalTokens.liquidGlassTintDark
    ) -> some View {
        self.modifier(LiquidGlassModifier(cornerRadius: cornerRadius, tintColor: tintColor))
    }
}
