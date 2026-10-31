import SwiftUI

public struct ElevationView: View {
    @ObservedObject var vm: IntercomViewModel
    @ObservedObject var prefs = UiPreferencesManager.shared
    @State private var selectedZoomIndex: Int = 1
    private let zoomScales: [String] = ["1 KM", "5 KM", "15 KM"]

    public init(vm: IntercomViewModel) {
        self.vm = vm
    }

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                // Header
                HStack {
                    Text("TRAIL ELEVATION")
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTheme.textPrimary)

                    Spacer()

                    // Zoom Picker
                    HStack(spacing: 4) {
                        ForEach(0..<zoomScales.count, id: \.self) { idx in
                            let isSel = idx == selectedZoomIndex
                            Button(action: {
                                TacticalHaptics.selection()
                                selectedZoomIndex = idx
                            }) {
                                Text(zoomScales[idx])
                                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                                    .foregroundColor(isSel ? Color.black : TacticalTheme.textSecondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(isSel ? TacticalTheme.accent : TacticalTheme.surfaceCard)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Telemetry Stats Row (Text-First, Battery Friendly)
                let grade = vm.elevationProfileSnapshot?.runningGradePercent ?? 4.2
                let deltaH = vm.elevationProfileSnapshot?.leadToTailAltitudeDeltaMeters ?? 45.0
                let altitude = vm.convoySnapshot?.localRider?.altitudeMeters ?? 1840.0

                HStack(spacing: 10) {
                    // Grade Card
                    VStack(alignment: .leading, spacing: 2) {
                        Text("SLOPE GRADE")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textMuted)

                        Text(String(format: "%+.1f%%", grade))
                            .font(.system(size: 18, weight: .black, design: .monospaced))
                            .foregroundColor(gradeColor(for: grade))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .tacticalCard()

                    // Delta-h Card
                    VStack(alignment: .leading, spacing: 2) {
                        Text("LEAD/TAIL ΔH")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textMuted)

                        Text(String(format: "%.0fm", deltaH))
                            .font(.system(size: 18, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTheme.accent)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .tacticalCard()

                    // Current Altitude Card
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ALTITUDE")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textMuted)

                        Text(String(format: "%.0fm", altitude))
                            .font(.system(size: 18, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTheme.textPrimary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .tacticalCard()
                }
                .padding(.horizontal, 16)

                // Elevation Profile Graph Canvas (Static, zero GPU animation loop)
                ZStack {
                    TacticalTheme.surfaceCard
                        .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                                .stroke(TacticalTheme.surfaceBorder, lineWidth: 1)
                        )

                    GeometryReader { geo in
                        let w = geo.size.width
                        let h = geo.size.height

                        // Static Elevation Curve Path
                        Path { path in
                            path.move(to: CGPoint(x: 0, y: h * 0.75))
                            path.addCurve(
                                to: CGPoint(x: w * 0.5, y: h * 0.25),
                                control1: CGPoint(x: w * 0.2, y: h * 0.70),
                                control2: CGPoint(x: w * 0.35, y: h * 0.30)
                            )
                            path.addCurve(
                                to: CGPoint(x: w, y: h * 0.60),
                                control1: CGPoint(x: w * 0.65, y: h * 0.20),
                                control2: CGPoint(x: w * 0.85, y: h * 0.50)
                            )
                        }
                        .stroke(
                            LinearGradient(
                                colors: [TacticalTheme.signalConnected, TacticalTheme.accent],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            lineWidth: 3
                        )

                        // Rider Pins along the contour
                        // Lead rider pin
                        Circle()
                            .fill(TacticalTheme.accent)
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(Color.black, lineWidth: 1.5))
                            .position(x: w * 0.5, y: h * 0.25)

                        // Self rider pin
                        Circle()
                            .fill(TacticalTheme.signalConnected)
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(Color.black, lineWidth: 1.5))
                            .position(x: w * 0.35, y: h * 0.37)
                    }
                    .padding(16)
                }
                .frame(maxHeight: .infinity)
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private func gradeColor(for grade: Double) -> Color {
        if abs(grade) >= 12.0 { return TacticalTheme.signalEmergency }
        if abs(grade) >= 8.0 { return TacticalTheme.signalWarning }
        return TacticalTheme.signalConnected
    }
}
