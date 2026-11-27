import SwiftUI
import SharedLogic

/// Native iOS 100% SwiftUI Offline Vector Terrain & Trail Cockpit Sheet.
///
/// Complies with Architectural Rule 1 (Zero Compose Multiplatform imports).
/// Designed for glove-friendly motorcycle handlebar cockpit HUD interaction:
/// - Displays offline vector trail metrics, elevation profile, and waypoint roster with zero cellular connectivity.
/// - Tactical high-contrast ADR-014 design system (claymorphic cards, neobrutalist outlines, monospace telemetry).
/// - Dynamic radar range zoom switcher (500m / 1.5km / 5.0km).
/// - Live nearest waypoint distance & bearing guidance.
struct OfflineTrailCockpitSheet: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            TacticalTheme.background
                .ignoresSafeArea()

            VStack(spacing: 12) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(Color.cyan)
                                .frame(width: 8, height: 8)
                            Text("OFFLINE TRAIL COCKPIT")
                                .font(.system(size: 13, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                        }
                        Text(vm.trailMapSnapshot?.activeTrail?.name ?? "Alpine Ridge Pass // Offline Vector Map")
                            .font(.system(size: 10, weight: .semibold, design: .monospaced))
                            .foregroundColor(Color.cyan.opacity(0.85))
                    }

                    Spacer()

                    Button(action: {
                        TacticalHaptics.shared.channelDetentClick()
                        vm.showTrailManagerSheet = true
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "bolt.horizontal.fill")
                                .font(.system(size: 10, weight: .bold))
                            Text("OTA ROUTES")
                                .font(.system(size: 9, weight: .black, design: .monospaced))
                        }
                        .foregroundColor(TacticalTheme.signalConnected)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(TacticalTheme.signalConnected.opacity(0.15))
                        .neobrutalistOutline(
                            color: TacticalTheme.signalConnected.opacity(0.35),
                            lineWidth: 1,
                            cornerRadius: 6
                        )
                    }

                    Button(action: {
                        TacticalHaptics.shared.channelDetentClick()
                        dismiss()
                    }) {
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
                            TacticalHaptics.shared.channelDetentClick()
                            vm.loadSampleAlpineTrail()
                        }) {
                            HStack(spacing: 8) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                    .font(.system(size: 12, weight: .bold))
                                Text("RELOAD SAMPLE ALPINE TRAIL & TOPO")
                                    .font(.system(size: 11, weight: .black, design: .monospaced))
                            }
                            .foregroundColor(Color.cyan)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.cyan.opacity(0.12))
                            .neobrutalistOutline(
                                color: Color.cyan.opacity(0.35),
                                lineWidth: 1,
                                cornerRadius: 8
                            )
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
                    .foregroundColor(TacticalTheme.signalConnected)
            }

            HStack(spacing: 8) {
                metricTile(title: "CURRENT ALT", value: "\(currentAlt)m", color: Color.cyan)
                metricTile(
                    title: "LEAD DELTA",
                    value: leadDelta >= 0 ? "+\(leadDelta)m" : "\(leadDelta)m",
                    color: leadDelta >= 0 ? TacticalTheme.signalConnected : TacticalTheme.signalHotMic
                )
                metricTile(title: "DISTANCE", value: "\(trailDistKm)km", color: .white)
                metricTile(title: "ASCENT", value: "+\(ascentM)m", color: TacticalTheme.signalWarning)
            }
        }
        .padding(12)
        .tacticalCard()
        .neobrutalistOutline(color: TacticalTheme.surfaceBorder, lineWidth: 1, cornerRadius: 10)
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
        .background(Color.black.opacity(0.35))
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
                        TacticalHaptics.shared.channelDetentClick()
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
        .tacticalCard()
        .neobrutalistOutline(color: TacticalTheme.surfaceBorder, lineWidth: 1, cornerRadius: 10)
    }

    private func zoomButton(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: {
            TacticalHaptics.shared.channelDetentClick()
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
        let distStr = dist >= 1000 ? String(format: "%.1f km", Double(dist) / 1000.0) : "\(dist) m"

        return HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(TacticalTheme.signalWarning.opacity(0.15))
                    .frame(width: 36, height: 36)
                Image(systemName: "flag.fill")
                    .font(.system(size: 14))
                    .foregroundColor(TacticalTheme.signalWarning)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("NEXT WAYPOINT")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
                Text(nearestWp?.name ?? "Eagle Peak Junction")
                    .font(.system(size: 12, weight: .black, design: .monospaced))
                    .foregroundColor(.white)
                    .lineLimit(1)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(distStr)
                    .font(.system(size: 13, weight: .black, design: .monospaced))
                    .foregroundColor(Color.cyan)
                Text(nearestWp?.type.name ?? "CHECKPOINT")
                    .font(.system(size: 7, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
            }
        }
        .padding(12)
        .tacticalCard()
        .neobrutalistOutline(color: TacticalTheme.surfaceBorder, lineWidth: 1, cornerRadius: 10)
    }

    private var waypointRosterCard: some View {
        let waypoints = vm.trailMapSnapshot?.projectedWaypoints ?? []

        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("TRAIL WAYPOINTS & ROSTER")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.gray)
                Spacer()
                Text("\(waypoints.count) WAYPOINTS")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
            }

            if waypoints.isEmpty {
                VStack(spacing: 6) {
                    Text("No waypoints loaded in active trail.")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundColor(.gray)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            } else {
                VStack(spacing: 6) {
                    ForEach(Array(waypoints.prefix(6)), id: \.waypoint.id) { pw in
                        waypointRow(pw: pw)
                    }
                }
            }
        }
        .padding(12)
        .tacticalCard()
        .neobrutalistOutline(color: TacticalTheme.surfaceBorder, lineWidth: 1, cornerRadius: 10)
    }

    private func waypointRow(pw: ProjectedWaypoint) -> some View {
        let wp = pw.waypoint
        let proj = pw.projectedPoint
        let dist = Int(proj.distanceMeters)
        let distStr = dist >= 1000 ? String(format: "%.1f km", Double(dist) / 1000.0) : "\(dist) m"

        return HStack(spacing: 10) {
            Text(waypointIcon(for: wp.type))
                .font(.system(size: 13))
                .frame(width: 22)

            VStack(alignment: .leading, spacing: 1) {
                Text(wp.name)
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                    .lineLimit(1)
                Text(wp.type.name)
                    .font(.system(size: 7, weight: .bold, design: .monospaced))
                    .foregroundColor(.gray)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 1) {
                Text(distStr)
                    .font(.system(size: 11, weight: .black, design: .monospaced))
                    .foregroundColor(Color.cyan)
                Text(proj.isVisibleOnRadar ? "ON RADAR" : "OUT OF RANGE")
                    .font(.system(size: 7, weight: .bold, design: .monospaced))
                    .foregroundColor(proj.isVisibleOnRadar ? TacticalTheme.signalConnected : .gray)
            }
        }
        .padding(.vertical, 4)
        .padding(.horizontal, 6)
        .background(Color.black.opacity(0.2))
        .cornerRadius(6)
    }

    private func waypointIcon(for type: WaypointType) -> String {
        switch type {
        case .basecamp: return "⛺"
        case .waterSource: return "💧"
        case .trailhead: return "🚩"
        case .checkpoint: return "📍"
        case .obstacleHazard: return "⚠️"
        case .mountainPass: return "⛰️"
        case .shelter: return "🛖"
        default: return "📌"
        }
    }
}
