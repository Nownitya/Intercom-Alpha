import SwiftUI
import SharedLogic

// MARK: - Native Tactile Radar HUD Card (iOS 18+ HIG)
// Glove-Friendly Spatial Radar & Convoy Telemetry Liquid Glass Card.
// Adheres strictly to ADR-014, Apple HIG, and Ralph Story ICA-3103.
// Rule 1: 100% Native SwiftUI. No Compose Multiplatform on iOS.

public struct TactileRadarCard: View {
    public let radarSnapshot: RadarSnapshot?
    public let convoySnapshot: ConvoyTelemetrySnapshot?
    public let trailMapSnapshot: TrailMapSnapshot?
    public let geofenceSnapshot: ConvoyGeofenceSnapshot?
    public let peersCount: Int
    public let onCycleZoom: () -> Void
    public let onOpenTrailCockpit: () -> Void

    public init(
        radarSnapshot: RadarSnapshot?,
        convoySnapshot: ConvoyTelemetrySnapshot?,
        trailMapSnapshot: TrailMapSnapshot?,
        geofenceSnapshot: ConvoyGeofenceSnapshot?,
        peersCount: Int,
        onCycleZoom: @escaping () -> Void,
        onOpenTrailCockpit: @escaping () -> Void
    ) {
        self.radarSnapshot = radarSnapshot
        self.convoySnapshot = convoySnapshot
        self.trailMapSnapshot = trailMapSnapshot
        self.geofenceSnapshot = geofenceSnapshot
        self.peersCount = peersCount
        self.onCycleZoom = onCycleZoom
        self.onOpenTrailCockpit = onOpenTrailCockpit
    }

    public var body: some View {
        VStack(spacing: 8) {
            // 1. Top HUD Header & 84pt Glove-Friendly Zoom Toggle
            HStack(alignment: .center) {
                HStack(spacing: 5) {
                    Circle()
                        .fill(TacticalTokens.signalCyan)
                        .frame(width: 7, height: 7)

                    Text("SPATIAL RADAR")
                        .font(.system(size: 11, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTokens.signalCyan)

                    Text("• 360° VECTORS")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.6))
                }

                Spacer()

                // Glove-Friendly 84pt Interactive Zoom Cycle Button (ICA-3103)
                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    onCycleZoom()
                }) {
                    Text("ZOOM \(trailMapSnapshot?.activeRangeZoom.label ?? "1.5km")")
                        .font(.system(size: 10, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTokens.signalCyan)
                        .frame(minWidth: 84, minHeight: 34)
                        .padding(.horizontal, 8)
                        .background(Color(red: 0.12, green: 0.15, blue: 0.20))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(TacticalTokens.signalCyan.opacity(0.6), lineWidth: 1.0)
                        )
                }
                .buttonStyle(.plain)
            }

            // 2. Vector Radar Display Canvas
            ZStack {
                // Background compass dial
                Circle()
                    .fill(Color(red: 0.04, green: 0.06, blue: 0.08))
                    .frame(width: 140, height: 140)

                // Concentric range rings
                Circle()
                    .stroke(TacticalTokens.signalCyan.opacity(0.12), lineWidth: 1)
                    .frame(width: 50, height: 50)

                Circle()
                    .stroke(TacticalTokens.signalCyan.opacity(0.18), lineWidth: 1)
                    .frame(width: 95, height: 95)

                Circle()
                    .stroke(TacticalTokens.signalCyan.opacity(0.28), lineWidth: 1.2)
                    .frame(width: 136, height: 136)

                // Reticle Crosshairs
                Rectangle()
                    .fill(TacticalTokens.signalCyan.opacity(0.12))
                    .frame(width: 1, height: 136)
                Rectangle()
                    .fill(TacticalTokens.signalCyan.opacity(0.12))
                    .frame(width: 136, height: 1)

                // Topo Elevation Contours & Trails (if active)
                if let trails = trailMapSnapshot, trails.isOverlayEnabled {
                    // Contours
                    ForEach(0..<trails.projectedContours.count, id: \.self) { idx in
                        let contour = trails.projectedContours[idx]
                        Path { path in
                            var isFirst = true
                            for pt in contour.points {
                                let x = 70.0 + CGFloat(pt.normalizedX) * 64.0
                                let y = 70.0 + CGFloat(pt.normalizedY) * 64.0
                                if isFirst {
                                    path.move(to: CGPoint(x: x, y: y))
                                    isFirst = false
                                } else {
                                    path.addLine(to: CGPoint(x: x, y: y))
                                }
                            }
                        }
                        .stroke(Color.gray.opacity(0.35), style: StrokeStyle(lineWidth: 0.8, dash: [4, 4]))
                        .frame(width: 140, height: 140)
                    }

                    // Active Trail Polyline
                    ForEach(0..<trails.projectedTrails.count, id: \.self) { idx in
                        let trail = trails.projectedTrails[idx]
                        Path { path in
                            var isFirst = true
                            for pt in trail.points {
                                let x = 70.0 + CGFloat(pt.normalizedX) * 64.0
                                let y = 70.0 + CGFloat(pt.normalizedY) * 64.0
                                if isFirst {
                                    path.move(to: CGPoint(x: x, y: y))
                                    isFirst = false
                                } else {
                                    path.addLine(to: CGPoint(x: x, y: y))
                                }
                            }
                        }
                        .stroke(TacticalTokens.signalCyan.opacity(0.85), style: StrokeStyle(lineWidth: 2.0, lineCap: .round, dash: [6, 3]))
                        .frame(width: 140, height: 140)
                    }
                }

                // Geofence Tether Perimeter Ring
                if let geofence = geofenceSnapshot {
                    let maxDist = trailMapSnapshot?.activeRangeZoom.maxDistanceMeters ?? 1500.0
                    let tetherDiameter = CGFloat(min(600.0 / maxDist, 1.0)) * 136.0
                    Circle()
                        .stroke(
                            geofence.overallStatus == .breach ? Color.red : (geofence.overallStatus == .warning ? Color.orange : TacticalTokens.signalCodedPhyEmerald.opacity(0.3)),
                            style: StrokeStyle(lineWidth: geofence.overallStatus == .breach ? 1.5 : 1.0, dash: [5, 4])
                        )
                        .frame(width: tetherDiameter, height: tetherDiameter)
                }

                // Plotted Convoy Riders
                if let riders = convoySnapshot?.riders {
                    ForEach(riders, id: \.peerId) { rider in
                        let maxDist = trailMapSnapshot?.activeRangeZoom.maxDistanceMeters ?? 1500.0
                        let dist = min(max(rider.distanceMeters, 10.0), maxDist)
                        let normalized = CGFloat(dist / maxDist)
                        let rad = CGFloat((rider.relativeBearingDegrees - 90.0) * .pi / 180.0)
                        let r = normalized * 64.0
                        let x = r * cos(rad)
                        let y = r * sin(rad)

                        let dotColor: Color = rider.distanceMeters < 100 ? TacticalTokens.signalCodedPhyEmerald : (rider.distanceMeters < 500 ? TacticalTokens.signalCyan : TacticalTokens.signalAmber)

                        Circle()
                            .fill(dotColor)
                            .frame(width: 7, height: 7)
                            .overlay(Circle().stroke(Color.black, lineWidth: 1))
                            .offset(x: x, y: y)
                    }
                }

                // Local Rider Center Indicator
                Circle()
                    .fill(Color(red: 0.16, green: 0.47, blue: 1.0))
                    .frame(width: 9, height: 9)
                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                    .shadow(color: .blue, radius: 4)
            }
            .frame(width: 140, height: 140)

            // 3. Monospaced Tabular Telemetry Readout Strip
            HStack(spacing: 8) {
                // Closest Rider Tabular Distance & Bearing
                if let closest = convoySnapshot?.closestRider {
                    HStack(spacing: 4) {
                        Image(systemName: "location.north.line.fill")
                            .font(.system(size: 8))
                            .foregroundColor(TacticalTokens.signalCyan)

                        Text("\(closest.peerName.ifBlank { "NODE" }): \(Int(closest.distanceMeters))m @ \(Int(closest.relativeBearingDegrees))°")
                            .font(.system(size: 9, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTokens.signalCyan)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(Color(red: 0.10, green: 0.14, blue: 0.19))
                    .cornerRadius(6)
                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(TacticalTokens.signalCyan.opacity(0.35), lineWidth: 1))
                } else {
                    Text("\(convoySnapshot?.riders.count ?? 0) RIDERS IN MESH")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(Color.white.opacity(0.6))
                }

                Spacer()

                // Trail Progress Launcher
                if let trail = trailMapSnapshot?.activeTrail {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onOpenTrailCockpit()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "map.fill")
                                .font(.system(size: 8))
                                .foregroundColor(TacticalTokens.signalCodedPhyEmerald)
                            Text(trail.name.prefix(12))
                                .font(.system(size: 9, weight: .black, design: .monospaced))
                                .foregroundColor(TacticalTokens.signalCodedPhyEmerald)
                        }
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(Color(red: 0.08, green: 0.16, blue: 0.12))
                        .cornerRadius(6)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(TacticalTokens.signalCodedPhyEmerald.opacity(0.4), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onOpenTrailCockpit()
                    }) {
                        Text("TRAIL COCKPIT")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundColor(Color.white.opacity(0.6))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.08))
                            .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(10)
        .liquidGlassBackground(cornerRadius: 14)
        .neobrutalistOutline(
            color: TacticalTokens.neobrutalBorderIdle,
            lineWidth: 1.2,
            cornerRadius: 14
        )
    }
}
