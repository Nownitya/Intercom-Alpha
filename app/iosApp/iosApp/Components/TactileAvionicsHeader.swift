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
    public let onOpenTrailSheet: (() -> Void)?
    public let onOpenShareSheet: (() -> Void)?
    public let onOpenDiagnostics: (() -> Void)?

    public init(
        groupName: String,
        peerCount: Int,
        isCloudConnected: Bool,
        batteryPercentage: Int,
        isLowPower: Bool = false,
        onOpenPeersSheet: @escaping () -> Void,
        onOpenTrailSheet: (() -> Void)? = nil,
        onOpenShareSheet: (() -> Void)? = nil,
        onOpenDiagnostics: (() -> Void)? = nil
    ) {
        self.groupName = groupName
        self.peerCount = peerCount
        self.isCloudConnected = isCloudConnected
        self.batteryPercentage = batteryPercentage
        self.isLowPower = isLowPower
        self.onOpenPeersSheet = onOpenPeersSheet
        self.onOpenTrailSheet = onOpenTrailSheet
        self.onOpenShareSheet = onOpenShareSheet
        self.onOpenDiagnostics = onOpenDiagnostics
    }

    public var body: some View {
        HStack(alignment: .center, spacing: 8) {
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

            Spacer(minLength: 4)

            // 2. Right Telemetry & Action Buttons (iOS HIG)
            HStack(spacing: 6) {
                // Peer Count Chip
                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    onOpenPeersSheet()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 9, weight: .bold))
                        Text("\(peerCount)")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(TacticalTokens.signalAmber)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(Color(red: 0.13, green: 0.15, blue: 0.18))
                    .neobrutalistOutline(
                        color: TacticalTokens.signalAmber,
                        lineWidth: 1.0,
                        cornerRadius: 6
                    )
                }
                .buttonStyle(.plain)

                // Trail Cockpit Action
                if let onTrail = onOpenTrailSheet {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onTrail()
                    }) {
                        Image(systemName: "map")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 5)
                            .background(Color(red: 0.13, green: 0.15, blue: 0.18))
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }

                // QR Share Action
                if let onShare = onOpenShareSheet {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onShare()
                    }) {
                        Image(systemName: "qrcode")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 5)
                            .background(Color(red: 0.13, green: 0.15, blue: 0.18))
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }

                // Subsystems Diagnostics Gear
                if let onDiag = onOpenDiagnostics {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onDiag()
                    }) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(TacticalTokens.signalCodedPhyEmerald)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 5)
                            .background(Color(red: 0.13, green: 0.15, blue: 0.18))
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }
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
