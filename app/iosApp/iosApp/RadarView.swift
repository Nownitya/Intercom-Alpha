import SwiftUI

public struct RadarView: View {
    @ObservedObject var vm: IntercomViewModel

    @State private var selectedRangeIndex: Int = 1
    private let ranges: [Double] = [15.0, 60.0, 150.0]

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                // Header
                HStack {
                    Text("PROXIMITY RADAR")
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTheme.textPrimary)

                    Spacer()

                    // Zoom Pill
                    Button(action: cycleZoom) {
                        HStack(spacing: 4) {
                            Image(systemName: "scope")
                                .font(.system(size: 11, weight: .bold))
                            Text("\(Int(ranges[selectedRangeIndex]))m")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                        }
                        .foregroundColor(TacticalTheme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(TacticalTheme.accentMuted)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Polar Radar Scope
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

                            // Distance Label
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
                        } else {
                            // Empty State
                            VStack(spacing: 6) {
                                Image(systemName: "antenna.radiowaves.left.and.right")
                                    .font(.system(size: 24))
                                    .foregroundColor(TacticalTheme.textMuted)
                                Text("SCANNING LOCAL BLE MESH")
                                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                                    .foregroundColor(TacticalTheme.textMuted)
                            }
                            .position(center)
                        }
                    }
                }
                .frame(maxHeight: .infinity)
                .padding(.horizontal, 16)
            }
        }
    }

    private func cycleZoom() {
        TactileHaptics.shared.channelDetentClick()
        selectedRangeIndex = (selectedRangeIndex + 1) % ranges.count
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

