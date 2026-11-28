import SwiftUI

public struct CockpitHeaderView: View {
    let groupName: String
    let peerCount: Int
    let activeChannelId: Int
    let phyMode: String
    let batteryPercentage: Int
    let isCloudConnected: Bool
    let onOpenRoster: () -> Void
    let onOpenDiagnostics: () -> Void
    let onOpenSettings: () -> Void

    public init(
        groupName: String,
        peerCount: Int,
        activeChannelId: Int,
        phyMode: String = "2M",
        batteryPercentage: Int = 85,
        isCloudConnected: Bool = false,
        onOpenRoster: @escaping () -> Void = {},
        onOpenDiagnostics: @escaping () -> Void = {},
        onOpenSettings: @escaping () -> Void = {}
    ) {
        self.groupName = groupName
        self.peerCount = peerCount
        self.activeChannelId = activeChannelId
        self.phyMode = phyMode
        self.batteryPercentage = batteryPercentage
        self.isCloudConnected = isCloudConnected
        self.onOpenRoster = onOpenRoster
        self.onOpenDiagnostics = onOpenDiagnostics
        self.onOpenSettings = onOpenSettings
    }

    public var body: some View {
        HStack(spacing: 8) {
            // Profile & Node count pill
            Button(action: onOpenSettings) {
                HStack(spacing: 6) {
                    Image(systemName: UiPreferencesManager.shared.profile.iconName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(TacticalTheme.accent)

                    Circle()
                        .fill(TacticalTheme.signalConnected)
                        .frame(width: 6, height: 6)

                    Text("\(peerCount)N")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(TacticalTheme.textPrimary)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(TacticalTheme.surfaceCard)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)

            Spacer()

            // Center Channel Indicator
            HStack(spacing: 4) {
                Text(String(format: "CH %02d", activeChannelId))
                    .font(.system(size: 13, weight: .heavy, design: .monospaced))
                    .foregroundColor(TacticalTheme.accent)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(TacticalTheme.accentMuted)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(TacticalTheme.accent.opacity(0.4), lineWidth: 1))

            Spacer()

            // Diagnostics & Battery Pill
            HStack(spacing: 6) {
                Button(action: onOpenDiagnostics) {
                    HStack(spacing: 4) {
                        Text(phyMode)
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTheme.signalConnected)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(TacticalTheme.signalConnected.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 3, style: .continuous))

                        Image(systemName: isCloudConnected ? "cloud.fill" : "antenna.radiowaves.left.and.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(isCloudConnected ? .cyan : TacticalTheme.textSecondary)

                        HStack(spacing: 2) {
                            Image(systemName: batteryIcon(for: batteryPercentage))
                                .font(.system(size: 10))
                                .foregroundColor(batteryColor(for: batteryPercentage))
                            Text(batteryPercentage >= 0 ? "\(batteryPercentage)%" : "—")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                                .foregroundColor(TacticalTheme.textPrimary)
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 6)
                    .background(TacticalTheme.surfaceCard)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
                }
                .buttonStyle(.plain)

                // Quick Settings Gear
                Button(action: onOpenSettings) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(TacticalTheme.textSecondary)
                        .padding(7)
                        .background(TacticalTheme.surfaceCard)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func batteryIcon(for level: Int) -> String {
        switch level {
        case 0..<20: return "battery.0percent"
        case 20..<50: return "battery.25percent"
        case 50..<80: return "battery.75percent"
        default: return "battery.100percent"
        }
    }

    private func batteryColor(for level: Int) -> Color {
        if level <= 15 { return TacticalTheme.signalEmergency }
        if level <= 30 { return TacticalTheme.signalWarning }
        return TacticalTheme.textPrimary
    }
}
