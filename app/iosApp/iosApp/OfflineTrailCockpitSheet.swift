import SwiftUI
import SharedLogic

/// Native iOS 100% SwiftUI Offline Vector Terrain & Trail Cockpit Sheet.
///
/// Complies with Architectural Rule 1 (Zero Compose Multiplatform imports).
/// Designed for glove-friendly motorcycle handlebar cockpit HUD interaction:
/// - Displays offline vector trail metrics, elevation profile, and waypoint roster with zero cellular connectivity.
/// - Tactical high-contrast dark aesthetic (#14181D container, #00E5FF cyan phosphors, #00E676 green badges).
/// - Dynamic radar range zoom switcher (500m / 1.5km / 5.0km).
/// - Live nearest waypoint distance & bearing guidance.
struct OfflineTrailCockpitSheet: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            Color(red: 0.08, green: 0.09, blue: 0.11)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(Color(red: 0.0, green: 0.9, blue: 1.0))
                                .frame(width: 8, height: 8)
                            Text("OFFLINE TRAIL COCKPIT")
                                .font(.system(size: 13, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                        }
                        Text(vm.trailMapSnapshot?.activeTrail?.name ?? "Alpine Ridge Pass // Offline Vector Map")
                            .font(.system(size: 10, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.8))
                    }

                    Spacer()

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        vm.showTrailManagerSheet = true
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "bolt.horizontal.fill")
                                .font(.system(size: 10, weight: .bold))
                            Text("OTA ROUTES")
                                .font(.system(size: 9, weight: .black, design: .monospaced))
                        }
                        .foregroundColor(Color(red: 0.0, green: 0.9, blue: 0.46))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color(red: 0.0, green: 0.9, blue: 0.46).opacity(0.15))
                        .cornerRadius(6)
                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(red: 0.0, green: 0.9, blue: 0.46).opacity(0.35), lineWidth: 1))
                    }

                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.gray)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 14) {
                        // 1. Elevation & Trail Metrics Card
                        elevationMetricsCard

                        // 2. Tactical Radar Range Zoom & Overlay Controls
                        radarControlsCard

                        // 3. Nearest Waypoint Guidance Banner
                        nearestWaypointCard

                        // 4. Waypoint Roster
                        waypointRosterCard

                        // 5. Offline Demo Action
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            vm.loadSampleAlpineTrail()
                        }) {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.system(size: 12, weight: .bold))
                                Text("RELOAD SAMPLE ALPINE TRAIL & TOPO")
                                    .font(.system(size: 11, weight: .black, design: .monospaced))
                            }
                            .foregroundColor(.cyan)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.cyan.opacity(0.12))
                            .cornerRadius(8)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cyan.opacity(0.3), lineWidth: 1))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .sheet(isPresented: $vm.showTrailManagerSheet) {
            TrailFileManagerSheet(vm: vm)
        }
    }

    // --- Sub-Components ---

    private var elevationMetricsCard: some View {
        let snapshot = vm.trailMapSnapshot
        let currentAlt = Int(snapshot?.currentElevationMeters ?? 2450.0)
        let leadDelta = Int(snapshot?.elevationDeltaToLeadMeters ?? 0.0)
        let trailDistKm = String(format: "%.1f", (snapshot?.activeTrail?.totalDistanceMeters ?? 4800.0) / 1000.0)
        let ascentM = Int(snapshot?.activeTrail?.totalElevationGainMeters ?? 380.0)

        return VStack(spacing: 10) {
            HStack {
                Text("ELEVATION & TRAIL METRICS")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.gray)
                Spacer()
                Text("OFFLINE GPS ACTIVE")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(Color(red: 0.0, green: 0.9, blue: 0.46))
            }

            HStack(spacing: 8) {
                metricTile(title: "CURRENT ALT", value: "\(currentAlt)m", color: .cyan)
                metricTile(
                    title: "LEAD DELTA",
                    value: leadDelta >= 0 ? "+\(leadDelta)m" : "\(leadDelta)m",
                    color: leadDelta >= 0 ? Color(red: 0.0, green: 0.9, blue: 0.46) : .orange
                )
                metricTile(title: "DISTANCE", value: "\(trailDistKm)km", color: .white)
                metricTile(title: "ASCENT", value: "+\(ascentM)m", color: Color(red: 1.0, green: 0.7, blue: 0.0))
            }
        }
        .padding(12)
        .background(Color(red: 0.08, green: 0.1, blue: 0.12))
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }

    private func metricTile(title: String, value: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 8, weight: .bold, design: .monospaced))
                .foregroundColor(.gray)
            Text(value)
                .font(.system(size: 13, weight: .black, design: .monospaced))
                .foregroundColor(color)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.25))
        .cornerRadius(6)
    }

    private var radarControlsCard: some View {
        let snapshot = vm.trailMapSnapshot
        let currentZoom = snapshot?.activeRangeZoom ?? .medium
        let isOverlayEnabled = snapshot?.isOverlayEnabled ?? true

        return VStack(spacing: 10) {
            HStack {
                Text("RADAR RANGE & OVERLAY")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.gray)
                Spacer()
                Toggle("", isOn: Binding(
                    get: { isOverlayEnabled },
                    set: { enabled in
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        vm.setTrailOverlayEnabled(enabled)
                    }
                ))
                .labelsHidden()
                .scaleEffect(0.8)
            }

            HStack(spacing: 8) {
                zoomButton(label: "500m CLOSE", isSelected: currentZoom == .close) {
                    vm.setRadarZoom(.close)
                }
                zoomButton(label: "1.5km MID", isSelected: currentZoom == .medium) {
                    vm.setRadarZoom(.medium)
                }
                zoomButton(label: "5.0km WIDE", isSelected: currentZoom == .wide) {
                    vm.setRadarZoom(.wide)
                }
            }
        }
        .padding(12)
        .background(Color(red: 0.08, green: 0.1, blue: 0.12))
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }

    private func zoomButton(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            action()
        }) {
            Text(label)
                .font(.system(size: 10, weight: .black, design: .monospaced))
                .foregroundColor(isSelected ? .black : .white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(isSelected ? Color.cyan : Color.white.opacity(0.1))
                .cornerRadius(6)
        }
    }

    private var nearestWaypointCard: some View {
        let snapshot = vm.trailMapSnapshot
        let nearestWp = snapshot?.nearestWaypoint
        let dist = snapshot?.nearestWaypointDistanceMeters != nil ? Int(snapshot!.nearestWaypointDistanceMeters!.doubleValue) : 340

        return HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(Color(red: 1.0, green: 0.7, blue: 0.0).opacity(0.2))
                    .frame(width: 36, height: 36)
                Text(waypointSymbol(for: nearestWp?.type))
                    .font(.system(size: 16))
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("NEXT WAYPOINT")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
                Text(nearestWp?.name ?? "Lookout Point #1")
                    .font(.system(size: 12, weight: .black, design: .monospaced))
                    .foregroundColor(.white)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text("\(dist)m")
                    .font(.system(size: 14, weight: .black, design: .monospaced))
                    .foregroundColor(Color(red: 1.0, green: 0.7, blue: 0.0))
                Text("AHEAD")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
            }
        }
        .padding(12)
        .background(Color(red: 0.12, green: 0.1, blue: 0.06))
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 1.0, green: 0.7, blue: 0.0).opacity(0.3), lineWidth: 1))
    }

    private var waypointRosterCard: some View {
        let waypoints = vm.trailMapSnapshot?.projectedWaypoints ?? []

        return VStack(alignment: .leading, spacing: 8) {
            Text("TACTICAL WAYPOINT ROSTER (\(waypoints.count))")
                .font(.system(size: 10, weight: .black, design: .monospaced))
                .foregroundColor(.gray)

            if waypoints.isEmpty {
                VStack(spacing: 6) {
                    Text("No waypoints loaded on current trail.")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            } else {
                VStack(spacing: 6) {
                    ForEach(waypoints, id: \.waypoint.id) { pw in
                        waypointRow(pw: pw)
                    }
                }
            }
        }
        .padding(12)
        .background(Color(red: 0.08, green: 0.1, blue: 0.12))
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }

    private func waypointRow(pw: ProjectedWaypoint) -> some View {
        let wp = pw.waypoint
        let dist = Int(pw.projectedPoint.distanceMeters)
        let alt = Int(wp.elevationMeters)

        return HStack(spacing: 8) {
            Text(waypointSymbol(for: wp.type))
                .font(.system(size: 12))

            VStack(alignment: .leading, spacing: 1) {
                Text(wp.name)
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Text("Elev: \(alt)m • \(wp.description_)")
                    .font(.system(size: 9, design: .monospaced))
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }

            Spacer()

            Text("\(dist)m")
                .font(.system(size: 11, weight: .black, design: .monospaced))
                .foregroundColor(.cyan)
        }
        .padding(8)
        .background(Color.black.opacity(0.2))
        .cornerRadius(6)
    }

    private func waypointSymbol(for type: WaypointType?) -> String {
        guard let type = type else { return "📍" }
        switch type {
        case .waterSource: return "💧"
        case .camp: return "⛺"
        case .hazard: return "⚠️"
        case .summit: return "🏔️"
        case .scenicViewpoint: return "🔭"
        case .intersection: return "🔀"
        default: return "📍"
        }
    }
}
