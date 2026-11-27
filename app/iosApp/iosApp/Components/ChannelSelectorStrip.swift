import SwiftUI

public struct ChannelSelectorStrip: View {
    let activeChannelId: Int
    let monitoredChannelIds: Set<Int>
    let onSelectChannel: (Int) -> Void
    let onToggleDualWatch: (Int) -> Void

    public init(
        activeChannelId: Int,
        monitoredChannelIds: Set<Int> = [],
        onSelectChannel: @escaping (Int) -> Void,
        onToggleDualWatch: @escaping (Int) -> Void = { _ in }
    ) {
        self.activeChannelId = activeChannelId
        self.monitoredChannelIds = monitoredChannelIds
        self.onSelectChannel = onSelectChannel
        self.onToggleDualWatch = onToggleDualWatch
    }

    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(1...16, id: \.self) { channelId in
                    let isActive = channelId == activeChannelId
                    let isDualWatch = monitoredChannelIds.contains(channelId)

                    Button(action: {
                        TacticalHaptics.shared.channelDetentClick()
                        onSelectChannel(channelId)
                    }) {
                        HStack(spacing: 4) {
                            Text(String(format: "%02d", channelId))
                                .font(.system(size: 13, weight: .bold, design: .monospaced))
                                .foregroundColor(isActive ? Color.black : (isDualWatch ? TacticalTheme.accent : TacticalTheme.textPrimary))

                            if isDualWatch && !isActive {
                                Circle()
                                    .fill(TacticalTheme.accent)
                                    .frame(width: 5, height: 5)
                            }
                        }
                        .frame(minWidth: 44, minHeight: 38)
                        .padding(.horizontal, 10)
                        .background(isActive ? TacticalTheme.accent : TacticalTheme.surfaceCard)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(
                                    isActive ? TacticalTheme.accent : (isDualWatch ? TacticalTheme.accent.opacity(0.6) : TacticalTheme.surfaceBorder),
                                    lineWidth: 1
                                )
                        )
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(action: {
                            TacticalHaptics.shared.channelDetentClick()
                            onToggleDualWatch(channelId)
                        }) {
                            Label(
                                isDualWatch ? "Remove Dual-Watch" : "Monitor (Dual-Watch)",
                                systemImage: isDualWatch ? "eye.slash" : "eye"
                            )
                        }
                    }
                }
            }
            .padding(.horizontal, 2)
        }
    }
}
