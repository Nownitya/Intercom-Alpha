import SwiftUI

// MARK: - Live Speaker Status Hero Card (ADR-0021)
// Clean, zero-clutter hero display showing current audio transmission state.
// Replaces twitching 60fps waveforms with discrete high-contrast state cards.
public struct LiveSpeakerCard: View {
    let isTransmitting: Bool
    let remoteSpeakerName: String?
    let activeChannelId: Int
    let peerCount: Int
    let showDiscreteLed: Bool
    let audioLevel: Float

    public init(
        isTransmitting: Bool,
        remoteSpeakerName: String?,
        activeChannelId: Int = 1,
        peerCount: Int = 1,
        showDiscreteLed: Bool = false,
        audioLevel: Float = 0.0
    ) {
        self.isTransmitting = isTransmitting
        self.remoteSpeakerName = remoteSpeakerName
        self.activeChannelId = activeChannelId
        self.peerCount = peerCount
        self.showDiscreteLed = showDiscreteLed
        self.audioLevel = audioLevel
    }

    private var isRemoteActive: Bool {
        !isTransmitting && remoteSpeakerName != nil
    }

    public var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                // Tactical Icon Container
                ZStack {
                    Circle()
                        .fill(stateColor.opacity(0.16))
                        .frame(width: 44, height: 44)

                    Image(systemName: stateIcon)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(stateColor)
                }

                // Primary Audio State Labels
                VStack(alignment: .leading, spacing: 2) {
                    Text(titleText)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(TacticalTheme.textPrimary)
                        .lineLimit(1)

                    Text(subtitleText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(TacticalTheme.textSecondary)
                        .lineLimit(1)
                }

                Spacer()

                // State Badge
                HStack(spacing: 4) {
                    Circle()
                        .fill(stateColor)
                        .frame(width: 8, height: 8)

                    Text(stateBadgeText)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(stateColor)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(stateColor.opacity(0.12))
                .clipShape(Capsule())
            }

            // Optional 4-Segment Discrete LED Level Indicator (Low-Power, no continuous 60fps loop)
            if showDiscreteLed {
                HStack(spacing: 4) {
                    ForEach(0..<4) { index in
                        RoundedRectangle(cornerRadius: 2)
                            .fill(segmentColor(for: index))
                            .frame(height: 6)
                    }
                }
                .padding(.top, 2)
            }
        }
        .padding(14)
        .background(TacticalTheme.surfaceCard)
        .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                .stroke(isTransmitting || isRemoteActive ? stateColor : TacticalTheme.surfaceBorder, lineWidth: isTransmitting || isRemoteActive ? 1.5 : 1)
        )
    }

    // MARK: - State Mappings
    private var stateColor: Color {
        if isTransmitting {
            return TacticalTheme.accent
        } else if isRemoteActive {
            return TacticalTheme.signalConnected
        } else {
            return TacticalTheme.textSecondary
        }
    }

    private var stateIcon: String {
        if isTransmitting {
            return "mic.fill"
        } else if isRemoteActive {
            return "speaker.wave.2.fill"
        } else {
            return "antenna.radiowaves.left.and.right"
        }
    }

    private var titleText: String {
        if isTransmitting {
            return "YOU ARE TRANSMITTING"
        } else if let remote = remoteSpeakerName {
            return remote
        } else {
            return "CHANNEL \(String(format: "%02d", activeChannelId))"
        }
    }

    private var subtitleText: String {
        if isTransmitting {
            return "Broadcasting to \(peerCount) nearby riders"
        } else if isRemoteActive {
            return "Receiving live audio • Priority Normal"
        } else {
            return "Channel ready • Hold PTT to talk"
        }
    }

    private var stateBadgeText: String {
        if isTransmitting {
            return "TX ACTIVE"
        } else if isRemoteActive {
            return "RX AUDIO"
        } else {
            return "READY"
        }
    }

    private func segmentColor(for index: Int) -> Color {
        let normalizedLevel = Double(min(max(audioLevel, 0.0), 1.0))
        let threshold = Double(index + 1) * 0.25

        if normalizedLevel >= threshold {
            if index == 3 {
                return TacticalTheme.signalWarning
            }
            return isTransmitting ? TacticalTheme.accent : TacticalTheme.signalConnected
        } else {
            return TacticalTheme.surfaceBorder
        }
    }
}

