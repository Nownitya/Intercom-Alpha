import SwiftUI

// MARK: - Native Tactile Audio VU Meter (iOS 18+ HIG)
// 14-segment LED bar with phosphor decay and clipping alert indicators.
// Rule 1: 100% Native SwiftUI.

public struct TactileVuMeter: View {
    public let currentRmsLevel: Float // 0.0 .. 1.0
    public let barCount: Int
    public let showDbLabel: Bool

    public init(
        currentRmsLevel: Float,
        barCount: Int = 14,
        showDbLabel: Bool = true
    ) {
        self.currentRmsLevel = currentRmsLevel
        self.barCount = barCount
        self.showDbLabel = showDbLabel
    }

    public var body: some View {
        HStack(spacing: 3) {
            let activeBars = Int(max(0.0, min(1.0, currentRmsLevel)) * Float(barCount))

            ForEach(0..<barCount, id: \.self) { index in
                let isActive = index < activeBars
                let barColor: Color = {
                    if index >= barCount - 2 {
                        return TacticalTokens.signalEmergencyCrimson
                    } else if index >= barCount - 5 {
                        return TacticalTokens.signalAmber
                    } else {
                        return TacticalTokens.signalCodedPhyEmerald
                    }
                }()

                RoundedRectangle(cornerRadius: 1, style: .continuous)
                    .fill(isActive ? barColor : Color(red: 0.12, green: 0.14, blue: 0.17))
                    .frame(width: 4, height: 14)
            }

            if showDbLabel {
                let db = currentRmsLevel > 0.01 ? Int(20.0 * log10(Double(currentRmsLevel))) : -60
                Text("\(db)dB")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(currentRmsLevel >= 0.85 ? TacticalTokens.signalEmergencyCrimson : Color(red: 0.55, green: 0.58, blue: 0.62))
                    .padding(.leading, 4)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(Color(red: 0.06, green: 0.08, blue: 0.10))
        .neobrutalistOutline(
            color: TacticalTokens.neobrutalBorderIdle,
            lineWidth: 1,
            cornerRadius: 8
        )
    }
}
