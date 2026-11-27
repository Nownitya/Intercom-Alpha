import SwiftUI

public struct DualVuMeterView: View {
    let localRmsLevel: Float
    let remoteRmsLevel: Float
    let remoteSpeakerName: String?
    let isVoiceActive: Bool
    let voxThreshold: Float

    public init(
        localRmsLevel: Float = 0.0,
        remoteRmsLevel: Float = 0.0,
        remoteSpeakerName: String? = nil,
        isVoiceActive: Bool = false,
        voxThreshold: Float = 0.35
    ) {
        self.localRmsLevel = localRmsLevel
        self.remoteRmsLevel = remoteRmsLevel
        self.remoteSpeakerName = remoteSpeakerName
        self.isVoiceActive = isVoiceActive
        self.voxThreshold = voxThreshold
    }

    public var body: some View {
        VStack(spacing: 8) {
            // Remote Incoming Speaker Meter
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(isVoiceActive ? TacticalTheme.accent : TacticalTheme.textMuted)
                        Text(remoteSpeakerName?.uppercased() ?? "CHANNEL READY")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(isVoiceActive ? TacticalTheme.accent : TacticalTheme.textSecondary)
                    }
                    Spacer()
                    Text(dbText(for: remoteRmsLevel))
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(TacticalTheme.textMuted)
                }

                VuProgressBar(
                    level: remoteRmsLevel,
                    activeColor: TacticalTheme.accent,
                    threshold: nil
                )
            }

            // Local Microphone Level Meter
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "mic.fill")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(TacticalTheme.textMuted)
                        Text("MIC INPUT")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textMuted)
                    }
                    Spacer()
                    Text("VOX: -35dB")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(TacticalTheme.textMuted)
                }

                VuProgressBar(
                    level: localRmsLevel,
                    activeColor: TacticalTheme.signalConnected,
                    threshold: voxThreshold
                )
            }
        }
        .padding(12)
        .tacticalCard()
    }

    private func dbText(for level: Float) -> String {
        if level <= 0.01 { return "-∞ dB" }
        let db = 20.0 * log10(max(level, 0.001))
        return String(format: "%.1f dB", db)
    }
}

private struct VuProgressBar: View {
    let level: Float
    let activeColor: Color
    let threshold: Float?

    private let segmentCount = 24

    var body: some View {
        GeometryReader { geo in
            let totalWidth = geo.size.width
            let spacing: CGFloat = 2
            let segmentWidth = (totalWidth - CGFloat(segmentCount - 1) * spacing) / CGFloat(segmentCount)

            HStack(spacing: spacing) {
                ForEach(0..<segmentCount, id: \.self) { index in
                    let fraction = Float(index + 1) / Float(segmentCount)
                    let isLit = level >= fraction
                    let isWarning = fraction > 0.85

                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(segmentColor(isLit: isLit, isWarning: isWarning))
                        .frame(width: segmentWidth, height: 8)
                }
            }
        }
        .frame(height: 8)
    }

    private func segmentColor(isLit: Bool, isWarning: Bool) -> Color {
        if !isLit {
            return TacticalTheme.surfaceBorder.opacity(0.4)
        }
        if isWarning {
            return TacticalTheme.signalEmergency
        }
        return activeColor
    }
}
