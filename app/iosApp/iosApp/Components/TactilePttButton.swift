import SwiftUI

// MARK: - Native Tactile Push-To-Talk Button (iOS 18+ HIG)
// Fuses:
// - Skeuomorphism: Concentric knurled aluminum bezel & mechanical depression well.
// - Claymorphism: Soft 3D tactile convex button volume with dual specular/drop shadows.
// - Neobrutalism: Crisp 2-3pt high-contrast perimeter outline.
// - Liquid Glass: Specular top highlight simulating coated polycarbonate glass.
// - Tactile Haptics: Rigid impact on press, medium on release.
// Rule 1: 100% Native SwiftUI.

public struct TactilePttButton: View {
    public let isTransmitting: Bool
    public let onPressStart: () -> Void
    public let onPressEnd: () -> Void
    public let channelLabel: String
    public let subtitle: String

    public init(
        isTransmitting: Bool,
        onPressStart: @escaping () -> Void,
        onPressEnd: @escaping () -> Void,
        channelLabel: String = "CH 01",
        subtitle: String = "HOLD TO TALK"
    ) {
        self.isTransmitting = isTransmitting
        self.onPressStart = onPressStart
        self.onPressEnd = onPressEnd
        self.channelLabel = channelLabel
        self.subtitle = subtitle
    }

    public var body: some View {
        Button(action: {}) {
            ZStack {
                // Outer Concentric Skeuomorphic Bezel
                Circle()
                    .fill(TactileTokens.cockpitChassis)
                    .frame(width: TacticalTokens.masterPttDiameter, height: TacticalTokens.masterPttDiameter)
                    .skeuomorphicBezel(
                        rimColor: isTransmitting ? TacticalTokens.signalHotMicOrange : Color(red: 0.18, green: 0.20, blue: 0.24),
                        knurlColor: isTransmitting ? TacticalTokens.signalHotMicOrange.opacity(0.4) : Color.white.opacity(0.18),
                        thickness: 6
                    )

                // Inner Claymorphic Actuator
                Circle()
                    .fill(isTransmitting ? Color(red: 0.15, green: 0.08, blue: 0.08) : TacticalTokens.cockpitElevated)
                    .frame(width: TacticalTokens.masterPttDiameter - 24, height: TacticalTokens.masterPttDiameter - 24)
                    .claymorphicDepth(
                        surfaceColor: isTransmitting ? Color(red: 0.15, green: 0.08, blue: 0.08) : TacticalTokens.cockpitElevated,
                        isPressed: isTransmitting,
                        cornerRadius: (TacticalTokens.masterPttDiameter - 24) / 2
                    )
                    .neobrutalistOutline(
                        color: isTransmitting ? TacticalTokens.signalHotMicOrange : TacticalTokens.neobrutalBorderIdle,
                        lineWidth: isTransmitting ? 3 : 2,
                        cornerRadius: (TacticalTokens.masterPttDiameter - 24) / 2
                    )

                // High-Contrast Cockpit Labels
                VStack(spacing: 4) {
                    Text(channelLabel)
                        .font(.system(size: 20, weight: .black, design: .monospaced))
                        .foregroundColor(isTransmitting ? TacticalTokens.signalHotMicOrange : TacticalTokens.signalAmber)

                    Text(isTransmitting ? "TRANSMITTING" : subtitle)
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(isTransmitting ? .white : Color(red: 0.55, green: 0.58, blue: 0.62))
                }
            }
        }
        .buttonStyle(PttPressGestureStyle(
            onPressStart: {
                TactileHaptics.shared.pttEngage()
                onPressStart()
            },
            onPressEnd: {
                TactileHaptics.shared.pttRelease()
                onPressEnd()
            }
        ))
        .scaleEffect(isTransmitting ? 0.94 : 1.0)
        .animation(.spring(response: 0.25, dampingFraction: 0.65), value: isTransmitting)
    }
}

private struct PttPressGestureStyle: ButtonStyle {
    let onPressStart: () -> Void
    let onPressEnd: () -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { pressed in
                if pressed {
                    onPressStart()
                } else {
                    onPressEnd()
                }
            }
    }
}
