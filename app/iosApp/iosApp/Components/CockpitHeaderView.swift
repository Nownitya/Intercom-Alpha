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

    public init(
        groupName: String,
        peerCount: Int,
        activeChannelId: Int,
        phyMode: String = "2M",
        batteryPercentage: Int = 85,
        isCloudConnected: Bool = false,
        onOpenRoster: @escaping () -> Void = {},
        onOpenDiagnostics: @escaping () -> Void = {}
    ) {
        self.groupName = groupName
        self.peerCount = peerCount
        self.activeChannelId = activeChannelId
        self.phyMode = phyMode
        self.batteryPercentage = batteryPercentage
        self.isCloudConnected = isCloudConnected
        self.onOpenRoster = onOpenRoster
        self.onOpenDiagnostics = onOpenDiagnostics
    }

    public var body: some View {
        HStack(spacing: 8) {
            // Node count pill
            Button(action: onOpenRoster) {
                HStack(spacing: 5) {
                    Circle()
                        .fill(TacticalTheme.signalConnected)
                        .frame(width: 7, height: 7)
                    Text("\(peerCount) NODES")
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
            Button(action: onOpenDiagnostics) {
                HStack(spacing: 6) {
                    // PHY mode badge (2M / 1M / S8)
                    Text(phyMode)
                        .font(.system(size: 10, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTheme.signalConnected)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(TacticalTheme.signalConnected.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))

                    // Cloud or Mesh indicator
                    Image(systemName: isCloudConnected ? "cloud.fill" : "antenna.radiowaves.left.and.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(isCloudConnected ? .cyan : TacticalTheme.textSecondary)

                    // Battery percentage
                    HStack(spacing: 3) {
                        Image(systemName: batteryIcon(for: batteryPercentage))
                            .font(.system(size: 11))
                            .foregroundColor(batteryColor(for: batteryPercentage))
                        Text("\(batteryPercentage)%")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textPrimary)
                    }
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(TacticalTheme.surfaceCard)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
            }
            .buttonStyle(.plain)
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

