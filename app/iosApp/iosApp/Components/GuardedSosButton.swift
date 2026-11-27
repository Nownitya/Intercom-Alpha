import SwiftUI

public struct GuardedSosButton: View {
    let isEmergencyActive: Bool
    let onTriggerEmergency: () -> Void
    let onCancelEmergency: () -> Void

    @State private var isGuardOpen: Bool = false
    @State private var countdownRemaining: Int = 15
    @State private var timerActive: Bool = false

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
        VStack(spacing: 6) {
            if isEmergencyActive {
                // Emergency SOS In-Progress Banner
                HStack(spacing: 8) {
                    Image(systemName: "sos.circle.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    Text("EMERGENCY BEACON ACTIVE")
                        .font(.system(size: 11, weight: .black, design: .monospaced))
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: onCancelEmergency) {
                        Text("CANCEL")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTheme.signalEmergency)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.white)
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(TacticalTheme.signalEmergency)
                .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous))
            } else {
                // Normal Guarded SOS Trigger Pill
                HStack {
                    if isGuardOpen {
                        Button(action: {
                            TacticalHaptics.shared.emergencyAlert()
                            onTriggerEmergency()
                            isGuardOpen = false
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.octagon.fill")
                                    .font(.system(size: 14, weight: .bold))
                                Text("CONFIRM SOS BROADCAST")
                                    .font(.system(size: 11, weight: .black, design: .monospaced))
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(TacticalTheme.signalEmergency)
                            .clipShape(Capsule())
                        }

                        Button(action: {
                            isGuardOpen = false
                        }) {
                            Image(systemName: "xmark")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(TacticalTheme.textMuted)
                                .frame(width: 36, height: 36)
                                .background(TacticalTheme.surfaceCard)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
                        }
                    } else {
                        Button(action: {
                            TacticalHaptics.shared.channelDetentClick()
                            isGuardOpen = true
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "shield.fill")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(TacticalTheme.signalEmergency)
                                Text("eCALL SOS")
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(TacticalTheme.textSecondary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(TacticalTheme.textMuted)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(TacticalTheme.surfaceCard)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}
