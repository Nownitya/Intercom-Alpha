import SwiftUI

// MARK: - Native Tactile Avionics Header (iOS 18+ HIG)
// Tactical Top Avionics Header for Outdoor Cockpit.
// High-contrast, glanceable telemetry chips:
// - Group Name & Tactical Call-Sign
// - Live Peer Count Chip (clickable for PeersSheet)
// - RF / Mesh Link Status Indicator (Coded PHY / Mesh / Cloud)
// - Battery / Power Mode Chip
// Rule 1: 100% Native SwiftUI. Adheres strictly to ADR-014.

public struct TactileAvionicsHeader: View {
    public let groupName: String
    public let peerCount: Int
    public let isCloudConnected: Bool
    public let batteryPercentage: Int
    public let isLowPower: Bool
    public let onOpenPeersSheet: () -> Void

    public init(
        groupName: String,
        peerCount: Int,
        isCloudConnected: Bool,
        batteryPercentage: Int,
        isLowPower: Bool = false,
        onOpenPeersSheet: @escaping () -> Void
    ) {
        self.groupName = groupName
        self.peerCount = peerCount
        self.isCloudConnected = isCloudConnected
        self.batteryPercentage = batteryPercentage
        self.isLowPower = isLowPower
        self.onOpenPeersSheet = onOpenPeersSheet
    }

    public var body: some View {
        HStack(alignment: .center, spacing: 10) {
            // 1. Group Name & Tactical Call-Sign
            VStack(alignment: .leading, spacing: 2) {
                Text(groupName.uppercased())
                    .font(.system(size: 13, weight: .black, design: .monospaced))
                    .foregroundColor(.white)
                    .lineLimit(1)

                HStack(spacing: 5) {
                    Circle()
                        .fill(TacticalTokens.signalCodedPhyEmerald)
                        .frame(width: 6, height: 6)

                    Text(isCloudConnected ? "HYBRID MESH+CLOUD" : "OFF-GRID BLE MESH")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(TacticalTokens.signalCodedPhyEmerald)
                        .tracking(0.3)
                }
            }

            Spacer(minLength: 6)

            // 2. Right Telemetry Chips
            HStack(spacing: 6) {
                // Peer Count Chip
                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    onOpenPeersSheet()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 9, weight: .bold))
                        Text("\(peerCount) NODES")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(TacticalTokens.signalAmber)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color(red: 0.13, green: 0.15, blue: 0.18))
                    .neobrutalistOutline(
                        color: TacticalTokens.signalAmber,
                        lineWidth: 1.0,
                        cornerRadius: 6
                    )
                }
                .buttonStyle(.plain)

                // Battery / Power Chip
                HStack(spacing: 4) {
                    Image(systemName: isLowPower ? "battery.25" : "battery.100")
                        .font(.system(size: 9, weight: .bold))
                    Text("\(batteryPercentage)%")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                }
                .foregroundColor(isLowPower ? TacticalTokens.signalAmber : Color(red: 0.89, green: 0.91, blue: 0.94))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(isLowPower ? Color(red: 0.18, green: 0.10, blue: 0.07) : Color(red: 0.13, green: 0.15, blue: 0.18))
                .neobrutalistOutline(
                    color: isLowPower ? TacticalTokens.signalAmber : Color.white.opacity(0.20),
                    lineWidth: 1.0,
                    cornerRadius: 6
                )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .claymorphicDepth(
            surfaceColor: TacticalTokens.cockpitChassis,
            cornerRadius: 14
        )
        .neobrutalistOutline(
            color: TacticalTokens.neobrutalBorderIdle,
            lineWidth: 1.5,
            cornerRadius: 14
        )
    }
}
