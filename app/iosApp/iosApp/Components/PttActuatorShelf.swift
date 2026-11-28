import SwiftUI

public struct PttActuatorShelf: View {
    let isTransmitting: Bool
    let isVoxLocked: Bool
    let activeChannelId: Int
    let onStartTransmit: () -> Void
    let onStopTransmit: () -> Void
    let onToggleVoxLock: () -> Void

    @GestureState private var isPressed: Bool = false

    public init(
        isTransmitting: Bool,
        isVoxLocked: Bool,
        activeChannelId: Int,
        onStartTransmit: @escaping () -> Void,
        onStopTransmit: @escaping () -> Void,
        onToggleVoxLock: @escaping () -> Void
    ) {
        self.isTransmitting = isTransmitting
        self.isVoxLocked = isVoxLocked
        self.activeChannelId = activeChannelId
        self.onStartTransmit = onStartTransmit
        self.onStopTransmit = onStopTransmit
        self.onToggleVoxLock = onToggleVoxLock
    }

    public var body: some View {
        HStack(spacing: 10) {
            // Master Glove-Friendly Capsule PTT Button
            let isHot = isTransmitting || isPressed || isVoxLocked

            Button(action: {}) {
                HStack(spacing: 12) {
                    Image(systemName: isHot ? "mic.fill" : "mic")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(isHot ? Color.black : TacticalTheme.accent)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(isHot ? "TRANSMITTING" : "HOLD TO TALK")
                            .font(.system(size: 16, weight: .black, design: .monospaced))
                            .foregroundColor(isHot ? Color.black : TacticalTheme.textPrimary)

                        Text(String(format: "CH %02d • 48 KBPS", activeChannelId))
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(isHot ? Color.black.opacity(0.8) : TacticalTheme.textMuted)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: TacticalTheme.pttShelfHeight)
                .background(isHot ? TacticalTheme.accent : TacticalTheme.surfaceCard)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(
                            isHot ? TacticalTheme.accent : TacticalTheme.surfaceBorder,
                            lineWidth: isHot ? 2 : 1
                        )
                )
                .scaleEffect(isPressed ? 0.97 : 1.0)
                .animation(.spring(response: 0.25, dampingFraction: 0.7), value: isPressed)
            }
            .buttonStyle(.plain)
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .updating($isPressed) { _, state, _ in
                        state = true
                    }
                    .onChanged { _ in
                        if !isTransmitting {
                            TacticalHaptics.shared.pttPressStart()
                            onStartTransmit()
                        }
                    }
                    .onEnded { _ in
                        TacticalHaptics.shared.pttPressEnd()
                        onStopTransmit()
                    }
            )

            // VOX Open-Mic Toggle Lock
            Button(action: {
                TacticalHaptics.shared.channelDetentClick()
                onToggleVoxLock()
            }) {
                VStack(spacing: 4) {
                    Image(systemName: isVoxLocked ? "lock.fill" : "lock.open")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(isVoxLocked ? TacticalTheme.accent : TacticalTheme.textMuted)

                    Text(isVoxLocked ? "VOX ON" : "VOX OFF")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(isVoxLocked ? TacticalTheme.accent : TacticalTheme.textMuted)
                }
                .frame(width: 68, height: TacticalTheme.pttShelfHeight)
                .background(isVoxLocked ? TacticalTheme.accentMuted : TacticalTheme.surfaceCard)
                .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                        .stroke(
                            isVoxLocked ? TacticalTheme.accent : TacticalTheme.surfaceBorder,
                            lineWidth: 1
                        )
                )
            }
            .buttonStyle(.plain)
        }
    }
}
