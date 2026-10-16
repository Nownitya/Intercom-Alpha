import SwiftUI

// MARK: - Native Tactile Guarded SOS Switch (iOS 18+ HIG)
// Hinged safety flip-cover that prevents accidental emergency broadcast.
// Rule 1: 100% Native SwiftUI.

public struct GuardedSosSwitch: View {
    public let isEmergencyActive: Bool
    public let onTriggerEmergency: () -> Void
    public let onCancelEmergency: () -> Void

    @State private var isCoverOpen: Bool = false

    public init(
        isEmergencyActive: Bool,
        onTriggerEmergency: @escaping () -> Void,
        onCancelEmergency: @escaping () -> Void
    ) {
        self.isEmergencyActive = isEmergencyActive
        self.onTriggerEmergency = onTriggerEmergency
        self.onCancelEmergency = onCancelEmergency
    }

    public var body: some View {
        ZStack {
            // Base Chassis Housing
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(isEmergencyActive ? Color(red: 0.16, green: 0.05, blue: 0.07) : TacticalTokens.cockpitChassis)
                .frame(width: 96, height: 96)
                .neobrutalistOutline(
                    color: isEmergencyActive ? TacticalTokens.signalEmergencyCrimson : TacticalTokens.neobrutalBorderIdle,
                    lineWidth: isEmergencyActive ? 3 : 2,
                    cornerRadius: 16
                )
                .claymorphicDepth(
                    surfaceColor: isEmergencyActive ? Color(red: 0.16, green: 0.05, blue: 0.07) : TacticalTokens.cockpitChassis,
                    cornerRadius: 16
                )

            // Inner SOS Actuator Button
            Button(action: {
                if isCoverOpen {
                    TactileHaptics.shared.emergencySos()
                    if isEmergencyActive {
                        onCancelEmergency()
                    } else {
                        onTriggerEmergency()
                    }
                } else {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                        isCoverOpen = true
                    }
                }
            }) {
                VStack(spacing: 2) {
                    Text(isEmergencyActive ? "ACTIVE" : "SOS")
                        .font(.system(size: 16, weight: .black, design: .monospaced))
                        .foregroundColor(.white)

                    Text(isEmergencyActive ? "CANCEL" : "PRESS")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(.white.opacity(0.8))
                }
                .frame(width: 76, height: 76)
                .background(isEmergencyActive ? TacticalTokens.signalEmergencyCrimson : Color(red: 0.55, green: 0.12, blue: 0.17))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)

            // Hinged Protective Safety Flip Guard
            if !isCoverOpen {
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                        isCoverOpen = true
                    }
                }) {
                    VStack(spacing: 2) {
                        Text("LIFT GUARD")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTokens.signalAmber)

                        Text("SAFETY FLIP")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundColor(.white.opacity(0.6))
                    }
                    .frame(width: 84, height: 84)
                    .background(Color(red: 0.15, green: 0.17, blue: 0.20))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .neobrutalistOutline(
                        color: TacticalTokens.signalAmber,
                        lineWidth: 2,
                        cornerRadius: 12
                    )
                }
                .buttonStyle(.plain)
                .transition(.scale.combined(with: .opacity))
            }
        }
        .frame(width: 96, height: 96)
    }
}
