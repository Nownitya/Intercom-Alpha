import SwiftUI

// MARK: - Proximity Radar & Buddy Vector View (ADR-0021)
// Supports both a battery-saving Text-First Vector List and a Polar Scope.
public struct RadarView: View {
    @ObservedObject var vm: IntercomViewModel
    @ObservedObject var prefs = UiPreferencesManager.shared

    @State private var viewMode: RadarDisplayMode = .textList
    @State private var selectedRangeIndex: Int = 1
    private let ranges: [Double] = [15.0, 60.0, 150.0]

    private enum RadarDisplayMode: String, CaseIterable {
        case textList = "Vector List"
        case polarScope = "Scope"
    }

    public init(vm: IntercomViewModel) {
        self.vm = vm
    }

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                // Header & Mode Switcher
                HStack(spacing: 8) {
                    Text("PROXIMITY RADAR")
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTheme.textPrimary)

                    Spacer()

                    // Low-Power Mode Switcher
                    Picker("Mode", selection: $viewMode) {
                        ForEach(RadarDisplayMode.allCases, id: \.self) { mode in
                            Text(mode.rawValue).tag(mode)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .frame(width: 160)

                    // Zoom Pill
                    Button(action: cycleZoom) {
                        HStack(spacing: 4) {
                            Image(systemName: "scope")
                                .font(.system(size: 10, weight: .bold))
                            Text("\(Int(ranges[selectedRangeIndex]))m")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                        }
                        .foregroundColor(TacticalTheme.accent)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(TacticalTheme.accentMuted)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                if viewMode == .textList || prefs.lowPowerMode {
                    // Low-Power Battery-Friendly Text-First Vector List
                    textFirstVectorListView
                } else {
                    // Polar Radar Scope
                    polarScopeView
                }
            }
        }
    }

    // MARK: - Text-First Vector List (Zero GPU Animation, Battery Saver)
    private var textFirstVectorListView: some View {
        VStack(spacing: 8) {
            if let targets = vm.radarSnapshot?.targets, !targets.isEmpty {
                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(targets, id: \.peerId) { target in
                            HStack(spacing: 12) {
                                // Direction Arrow Pill
                                ZStack {
                                    Circle()
                                        .fill(TacticalTheme.surfaceBorder)
                                        .frame(width: 36, height: 36)

                                    Image(systemName: bearingArrowIcon(for: target.relativeBearingDegrees))
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(TacticalTheme.accent)
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(target.peerId.uppercased())
                                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                                        .foregroundColor(TacticalTheme.textPrimary)

                                    Text(bearingDescription(for: target.relativeBearingDegrees))
                                        .font(.system(size: 11, weight: .medium))
                                        .foregroundColor(TacticalTheme.textSecondary)
                                }

                                Spacer()

                                // Distance & Link Health
                                VStack(alignment: .trailing, spacing: 2) {
                                    Text("\(Int(target.estimatedDistanceMeters))m")
                                        .font(.system(size: 15, weight: .black, design: .monospaced))
                                        .foregroundColor(TacticalTheme.textPrimary)

                                    HStack(spacing: 4) {
                                        Circle()
                                            .fill(TacticalTheme.signalConnected)
                                            .frame(width: 6, height: 6)
                                        Text("\(target.rssi) dBm")
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .foregroundColor(TacticalTheme.signalConnected)
                                    }
                                }
                            }
                            .padding(12)
                            .background(TacticalTheme.surfaceCard)
                            .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous)
                                    .stroke(TacticalTheme.surfaceBorder, lineWidth: 1)
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                }
            } else {
                VStack(spacing: 8) {
                    Spacer()
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 28))
                        .foregroundColor(TacticalTheme.textMuted)
                    Text("NO RIDERS WITHIN \(Int(ranges[selectedRangeIndex]))m")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundColor(TacticalTheme.textMuted)
                    Text("Scanning decentralized BLE mesh...")
                        .font(.system(size: 11))
                        .foregroundColor(TacticalTheme.textMuted)
                    Spacer()
                }
            }
        }
    }

    // MARK: - Polar Scope View
    private var polarScopeView: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height) - 32
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            let currentMaxDistance = ranges[selectedRangeIndex]

            ZStack {
                // Concentric Range Rings
                ForEach(1...3, id: \.self) { ring in
                    let ringRadius = (size / 2) * (CGFloat(ring) / 3.0)
                    Circle()
                        .stroke(TacticalTheme.surfaceBorder, lineWidth: 1)
                        .frame(width: ringRadius * 2, height: ringRadius * 2)

                    Text("\(Int(currentMaxDistance * (Double(ring) / 3.0)))m")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(TacticalTheme.textMuted)
                        .position(x: center.x, y: center.y - ringRadius + 8)
                }

                // Compass Crosshairs
                Path { path in
                    path.move(to: CGPoint(x: center.x, y: center.y - size / 2))
                    path.addLine(to: CGPoint(x: center.x, y: center.y + size / 2))
                    path.move(to: CGPoint(x: center.x - size / 2, y: center.y))
                    path.addLine(to: CGPoint(x: center.x + size / 2, y: center.y))
                }
                .stroke(TacticalTheme.surfaceBorder.opacity(0.5), lineWidth: 1)

                // Center Local Rider (Self)
                Circle()
                    .fill(TacticalTheme.accent)
                    .frame(width: 12, height: 12)
                    .position(center)

                // Remote Convoy Riders
                if let targets = vm.radarSnapshot?.targets, !targets.isEmpty {
                    ForEach(targets, id: \.peerId) { target in
                        let normalizedDistance = min(CGFloat(target.estimatedDistanceMeters / currentMaxDistance), 1.0)
                        let radius = (size / 2) * normalizedDistance
                        let angleRad = CGFloat(target.relativeBearingDegrees - 90.0) * .pi / 180.0
                        let x = center.x + radius * cos(angleRad)
                        let y = center.y + radius * sin(angleRad)

                        RadarBlipView(
                            name: target.peerId,
                            distanceMeters: target.estimatedDistanceMeters,
                            rssi: target.rssi
                        )
                        .position(x: x, y: y)
                    }
                }
            }
        }
        .frame(maxHeight: .infinity)
        .padding(.horizontal, 16)
    }

    private func cycleZoom() {
        TacticalHaptics.selection()
        selectedRangeIndex = (selectedRangeIndex + 1) % ranges.count
    }

    private func bearingArrowIcon(for degrees: Double) -> String {
        switch degrees {
        case 337.5...360, 0..<22.5: return "arrow.up"
        case 22.5..<67.5: return "arrow.up.right"
        case 67.5..<112.5: return "arrow.right"
        case 112.5..<157.5: return "arrow.down.right"
        case 157.5..<202.5: return "arrow.down"
        case 202.5..<247.5: return "arrow.down.left"
        case 247.5..<292.5: return "arrow.left"
        case 292.5..<337.5: return "arrow.up.left"
        default: return "arrow.up"
        }
    }

    private func bearingDescription(for degrees: Double) -> String {
        switch degrees {
        case 337.5...360, 0..<22.5: return "Directly Ahead"
        case 22.5..<67.5: return "Ahead Right"
        case 67.5..<112.5: return "To the Right"
        case 112.5..<157.5: return "Behind Right"
        case 157.5..<202.5: return "Directly Behind"
        case 202.5..<247.5: return "Behind Left"
        case 247.5..<292.5: return "To the Left"
        case 292.5..<337.5: return "Ahead Left"
        default: return "Ahead"
        }
    }
}

private struct RadarBlipView: View {
    let name: String
    let distanceMeters: Double
    let rssi: Int

    var body: some View {
        VStack(spacing: 2) {
            Circle()
                .fill(TacticalTheme.signalConnected)
                .frame(width: 10, height: 10)
                .overlay(Circle().stroke(Color.black, lineWidth: 1.5))

            Text(name.prefix(4).uppercased())
                .font(.system(size: 9, weight: .black, design: .monospaced))
                .foregroundColor(TacticalTheme.textPrimary)

            Text("\(Int(distanceMeters))m")
                .font(.system(size: 8, weight: .medium, design: .monospaced))
                .foregroundColor(TacticalTheme.textSecondary)
        }
    }
}
