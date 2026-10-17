import SwiftUI

// MARK: - Native Tactile Channel Selector Dial (iOS 18+ HIG)
// Tactical Skeuomorphic Channel Selector with Rotary Detent Affordance.
// Provides instant channel switching between channels 1 through 8 with Dual-Watch indicators.
// Rule 1: 100% Native SwiftUI. Adheres strictly to ADR-014.

public struct TactileChannelDial: View {
    public let activeChannelId: Int
    public let monitoredChannelIds: Set<Int>
    public let onSelectChannel: (Int) -> Void
    public let onToggleDualWatch: (Int) -> Void

    public init(
        activeChannelId: Int,
        monitoredChannelIds: Set<Int>,
        onSelectChannel: @escaping (Int) -> Void,
        onToggleDualWatch: @escaping (Int) -> Void
    ) {
        self.activeChannelId = activeChannelId
        self.monitoredChannelIds = monitoredChannelIds
        self.onSelectChannel = onSelectChannel
        self.onToggleDualWatch = onToggleDualWatch
    }

    public var body: some View {
        HStack(spacing: 5) {
            ForEach(1...8, id: \.self) { ch in
                let isActive = ch == activeChannelId
                let isDualWatch = monitoredChannelIds.contains(ch)

                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    if isActive {
                        onToggleDualWatch(ch)
                    } else {
                        onSelectChannel(ch)
                    }
                }) {
                    Text("CH\(ch)")
                        .font(.system(size: 11, weight: .black, design: .monospaced))
                        .foregroundColor(
                            isActive
                                ? Color.black
                                : (isDualWatch ? TacticalTokens.signalCodedPhyEmerald : Color(red: 0.55, green: 0.58, blue: 0.62))
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            isActive
                                ? TacticalTokens.signalAmber
                                : (isDualWatch ? Color(red: 0.12, green: 0.23, blue: 0.18) : Color(red: 0.09, green: 0.11, blue: 0.13))
                        )
                        .neobrutalistOutline(
                            color: isActive
                                ? Color.black
                                : (isDualWatch ? TacticalTokens.signalCodedPhyEmerald : Color.white.opacity(0.12)),
                            lineWidth: 1.0,
                            cornerRadius: 8
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .claymorphicDepth(
            surfaceColor: TacticalTokens.cockpitChassis,
            cornerRadius: 12
        )
        .neobrutalistOutline(
            color: TacticalTokens.neobrutalBorderIdle,
            lineWidth: 1.0,
            cornerRadius: 12
        )
    }
}
