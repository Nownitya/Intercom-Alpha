import SwiftUI
import SharedLogic

/// Native iOS 100% SwiftUI Trail File Manager & Over-The-Air Beam Cockpit Sheet.
///
/// Complies with Architectural Rule 1 (Zero Compose Multiplatform imports).
/// Designed for motorcycle handlebar cockpit interaction:
/// - Allows riders to beam active or stored trails over BLE mesh chunks to convoy peers.
/// - Live transmission progress bars displaying chunk sequence and Adler-32 integrity states.
/// - Offline storage library manager for GPX 1.1 and RFC 7946 GeoJSON routes.
/// - Raw text import dialog for rapid offline route pasting.
/// - Styled with ADR-014 Tactical Design Tokens (TacticalTokens, claymorphic depth, neobrutalist outlines).
struct TrailFileManagerSheet: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var selectedFormat: FileTransferType = .gpx
    @State private var showImportModal: Bool = false
    @State private var importName: String = ""
    @State private var importText: String = ""
    @State private var importFormat: FileTransferType = .gpx

    var body: some View {
        ZStack {
            TacticalTokens.cockpitObsidian
                .ignoresSafeArea()

            VStack(spacing: 12) {
                // 1. Header Bar
                headerView
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 14) {
                        // 2. Beam Active Route & Format Selector
                        beamActuatorCard

                        // 3. Live Over-The-Air File Transfers (if any active)
                        if !vm.activeTransfers.isEmpty {
                            activeTransfersCard
                        }

                        // 4. Stored Routes Library
                        storedRoutesCard

                        // 5. Quick Sample Trail Seed Action
                        quickSampleButton
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
        .sheet(isPresented: $showImportModal) {
            importRouteModal
        }
    }

    // --- Sub-Components ---

    private var headerView: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(TacticalTokens.signalSatelliteCyan)
                        .frame(width: 8, height: 8)
                    Text("TRAIL FILE MANAGER")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(.white)

                    Text("OFF-GRID OTA")
                        .font(.system(size: 8, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTokens.signalCodedPhyEmerald)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(TacticalTokens.signalCodedPhyEmerald.opacity(0.15))
                        .cornerRadius(4)
                }

                Text("BLE Mesh Chunked GPX/GeoJSON Exchange")
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .foregroundColor(TacticalTokens.signalSatelliteCyan.opacity(0.85))
            }

            Spacer()

            Button(action: {
                TactileHaptics.shared.channelDetentClick()
                dismiss()
            }) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.gray)
            }
        }
    }

    private var beamActuatorCard: some View {
        let activeTrail = vm.trailMapSnapshot?.activeTrail

        return VStack(spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("ACTIVE COCKPIT ROUTE")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(.gray)
                    Text(activeTrail?.name ?? "No Route Active")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(activeTrail != nil ? .white : .gray)
                        .lineLimit(1)
                }
                Spacer()

                // Format Picker (GPX vs GeoJSON)
                HStack(spacing: 4) {
                    formatButton(title: "GPX", type: .gpx)
                    formatButton(title: "GEOJSON", type: .geojson)
                }
            }

            HStack(spacing: 8) {
                // 1-Tap Beam to Convoy
                Button(action: {
                    TactileHaptics.shared.pttReleaseThump()
                    vm.beamActiveTrail(format: selectedFormat)
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("BEAM TO CONVOY")
                            .font(.system(size: 11, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(TacticalTokens.signalSatelliteCyan)
                    .cornerRadius(8)
                }

                // Import Text Button
                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    showImportModal = true
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 11, weight: .bold))
                        Text("IMPORT")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(TacticalTokens.signalSatelliteCyan)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 11)
                    .background(TacticalTokens.signalSatelliteCyan.opacity(0.12))
                    .neobrutalistOutline(
                        color: TacticalTokens.signalSatelliteCyan.opacity(0.35),
                        lineWidth: 1,
                        cornerRadius: 8
                    )
                }
            }
        }
        .padding(12)
        .claymorphicDepth(surfaceColor: TacticalTokens.cockpitElevated, cornerRadius: 10)
        .neobrutalistOutline(color: TacticalTokens.signalSatelliteCyan.opacity(0.3), lineWidth: 1, cornerRadius: 10)
    }

    private func formatButton(title: String, type: FileTransferType) -> some View {
        let isSelected = selectedFormat == type
        return Button(action: {
            TactileHaptics.shared.channelDetentClick()
            selectedFormat = type
        }) {
            Text(title)
                .font(.system(size: 9, weight: .black, design: .monospaced))
                .foregroundColor(isSelected ? .black : .white)
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(isSelected ? TacticalTokens.signalSatelliteCyan : Color.white.opacity(0.1))
                .cornerRadius(5)
        }
    }

    private var activeTransfersCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("LIVE OTA TRANSFERS (\(vm.activeTransfers.count))")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(TacticalTokens.signalAmber)
                Spacer()
            }

            VStack(spacing: 8) {
                ForEach(vm.activeTransfers, id: \.transferId) { transfer in
                    transferRow(transfer: transfer)
                }
            }
        }
        .padding(12)
        .claymorphicDepth(surfaceColor: TacticalTokens.cockpitElevated, cornerRadius: 10)
        .neobrutalistOutline(color: TacticalTokens.signalAmber.opacity(0.35), lineWidth: 1, cornerRadius: 10)
    }

    private func transferRow(transfer: FileTransferProgress) -> some View {
        let isOutgoing = transfer.direction == .outgoing
        let percent = Int(transfer.progressPercent * 100)

        return VStack(spacing: 6) {
            HStack {
                Image(systemName: isOutgoing ? "arrow.up.circle.fill" : "arrow.down.circle.fill")
                    .foregroundColor(isOutgoing ? TacticalTokens.signalAmber : TacticalTokens.signalSatelliteCyan)
                    .font(.system(size: 14))

                VStack(alignment: .leading, spacing: 1) {
                    Text(transfer.fileName)
                        .font(.system(size: 11, weight: .black, design: .monospaced))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        Text(isOutgoing ? "OUTGOING CHUNKS" : "INCOMING CHUNKS")
                            .font(.system(size: 8, weight: .bold, design: .monospaced))
                            .foregroundColor(.gray)

                        Text("• \(transfer.chunksTransferred)/\(transfer.totalChunks) chunks")
                            .font(.system(size: 8, weight: .medium, design: .monospaced))
                            .foregroundColor(.gray)
                    }
                }

                Spacer()

                Text("\(percent)%")
                    .font(.system(size: 13, weight: .black, design: .monospaced))
                    .foregroundColor(isOutgoing ? TacticalTokens.signalAmber : TacticalTokens.signalSatelliteCyan)

                Button(action: {
                    TactileHaptics.shared.channelDetentClick()
                    vm.cancelTransfer(transferId: transfer.transferId)
                }) {
                    Image(systemName: "xmark.circle")
                        .font(.system(size: 14))
                        .foregroundColor(TacticalTokens.signalEmergencyCrimson)
                }
            }

            // Progress Bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.white.opacity(0.1))
                        .frame(height: 6)

                    RoundedRectangle(cornerRadius: 3)
                        .fill(isOutgoing ? TacticalTokens.signalAmber : TacticalTokens.signalSatelliteCyan)
                        .frame(width: max(0, min(geo.size.width * CGFloat(transfer.progressPercent), geo.size.width)), height: 6)
                }
            }
            .frame(height: 6)
        }
        .padding(8)
        .background(Color.black.opacity(0.35))
        .cornerRadius(8)
    }

    private var storedRoutesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("STORED ROUTES LIBRARY (\(vm.storedTrails.count))")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.gray)
                Spacer()
            }

            if vm.storedTrails.isEmpty {
                VStack(spacing: 6) {
                    Text("No stored routes available.")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(.gray)
                    Text("Beam a trail from a peer or import text below.")
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.gray.opacity(0.7))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            } else {
                VStack(spacing: 8) {
                    ForEach(vm.storedTrails, id: \.id) { trail in
                        storedTrailRow(trail: trail)
                    }
                }
            }
        }
        .padding(12)
        .claymorphicDepth(surfaceColor: TacticalTokens.cockpitElevated, cornerRadius: 10)
        .neobrutalistOutline(color: TacticalTokens.neobrutalBorderIdle, lineWidth: 1, cornerRadius: 10)
    }

    private func storedTrailRow(trail: TrailPolyline) -> some View {
        let isActive = trail.id == vm.trailMapSnapshot?.activeTrail?.id
        let distKm = String(format: "%.1f", trail.totalDistanceMeters / 1000.0)
        let ascentM = Int(trail.totalElevationGainMeters)

        return HStack(spacing: 10) {
            // Radio/Active Indicator
            Button(action: {
                TactileHaptics.shared.channelDetentClick()
                vm.selectStoredTrail(trail: trail)
            }) {
                Image(systemName: isActive ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isActive ? TacticalTokens.signalSatelliteCyan : .gray)
                    .font(.system(size: 16))
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(trail.name)
                    .font(.system(size: 11, weight: .black, design: .monospaced))
                    .foregroundColor(.white)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    difficultyBadge(for: trail.difficulty)

                    Text("\(distKm) km")
                        .font(.system(size: 8, weight: .bold, design: .monospaced))
                        .foregroundColor(.gray)

                    Text("+\(ascentM)m")
                        .font(.system(size: 8, weight: .bold, design: .monospaced))
                        .foregroundColor(.gray)

                    Text("\(trail.points.count) pts")
                        .font(.system(size: 8, design: .monospaced))
                        .foregroundColor(.gray)
                }
            }

            Spacer()

            // Beam Action Button
            Button(action: {
                TactileHaptics.shared.pttReleaseThump()
                vm.beamTrail(trail: trail, format: selectedFormat)
            }) {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(TacticalTokens.signalSatelliteCyan)
                    .padding(6)
                    .background(TacticalTokens.signalSatelliteCyan.opacity(0.15))
                    .clipShape(Circle())
            }

            // Delete Action Button
            Button(action: {
                TactileHaptics.shared.channelDetentClick()
                vm.deleteStoredTrail(trailId: trail.id)
            }) {
                Image(systemName: "trash")
                    .font(.system(size: 12))
                    .foregroundColor(TacticalTokens.signalEmergencyCrimson.opacity(0.85))
                    .padding(6)
            }
        }
        .padding(9)
        .background(isActive ? TacticalTokens.signalSatelliteCyan.opacity(0.08) : Color.black.opacity(0.2))
        .cornerRadius(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(isActive ? TacticalTokens.signalSatelliteCyan.opacity(0.4) : Color.clear, lineWidth: 1))
    }

    private func difficultyBadge(for diff: TrailDifficulty) -> some View {
        let (label, color) = difficultyDisplay(for: diff)
        return Text(label)
            .font(.system(size: 7, weight: .black, design: .monospaced))
            .foregroundColor(color)
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(color.opacity(0.15))
            .cornerRadius(3)
    }

    private func difficultyDisplay(for diff: TrailDifficulty) -> (String, Color) {
        switch diff {
        case .easy: return ("EASY", TacticalTokens.signalCodedPhyEmerald)
        case .moderate: return ("MODERATE", TacticalTokens.signalSatelliteCyan)
        case .difficult: return ("HARD", TacticalTokens.signalAmber)
        case .extremeTechnical: return ("EXPERT", TacticalTokens.signalHotMicOrange)
        default: return ("TRAIL", .gray)
        }
    }

    private var quickSampleButton: some View {
        Button(action: {
            TactileHaptics.shared.channelDetentClick()
            vm.loadSampleAlpineTrail()
        }) {
            HStack(spacing: 8) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 12, weight: .bold))
                Text("RELOAD SAMPLE ALPINE TRAIL & TOPO")
                    .font(.system(size: 11, weight: .black, design: .monospaced))
            }
            .foregroundColor(TacticalTokens.signalSatelliteCyan)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(TacticalTokens.signalSatelliteCyan.opacity(0.12))
            .neobrutalistOutline(
                color: TacticalTokens.signalSatelliteCyan.opacity(0.35),
                lineWidth: 1,
                cornerRadius: 8
            )
        }
    }

    // --- Route Text Import Modal ---

    private var importRouteModal: some View {
        ZStack {
            TacticalTokens.cockpitObsidian
                .ignoresSafeArea()

            VStack(spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("IMPORT ROUTE TEXT")
                            .font(.system(size: 13, weight: .black, design: .monospaced))
                            .foregroundColor(.white)
                        Text("Paste raw GPX XML or GeoJSON FeatureCollection")
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Button("Cancel") {
                        TactileHaptics.shared.channelDetentClick()
                        showImportModal = false
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.gray)
                }

                // Name & Format
                HStack(spacing: 8) {
                    TextField("Route Name (e.g. Pass 4)", text: $importName)
                        .font(.system(size: 11, design: .monospaced))
                        .padding(8)
                        .background(Color.black.opacity(0.35))
                        .cornerRadius(6)
                        .foregroundColor(.white)

                    HStack(spacing: 4) {
                        Button(action: {
                            TactileHaptics.shared.channelDetentClick()
                            importFormat = .gpx
                        }) {
                            Text("GPX")
                                .font(.system(size: 9, weight: .black, design: .monospaced))
                                .foregroundColor(importFormat == .gpx ? .black : .white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(importFormat == .gpx ? TacticalTokens.signalSatelliteCyan : Color.white.opacity(0.1))
                                .cornerRadius(5)
                        }
                        Button(action: {
                            TactileHaptics.shared.channelDetentClick()
                            importFormat = .geojson
                        }) {
                            Text("GEOJSON")
                                .font(.system(size: 9, weight: .black, design: .monospaced))
                                .foregroundColor(importFormat == .geojson ? .black : .white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 6)
                                .background(importFormat == .geojson ? TacticalTokens.signalSatelliteCyan : Color.white.opacity(0.1))
                                .cornerRadius(5)
                        }
                    }
                }

                // Payload Editor
                TextEditor(text: $importText)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.white)
                    .padding(8)
                    .background(Color.black.opacity(0.35))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white.opacity(0.15), lineWidth: 1))

                // Import Button
                Button(action: {
                    guard !importText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                    TactileHaptics.shared.pttReleaseThump()
                    let name = importName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Imported Trail" : importName
                    vm.importTrailText(text: importText, fileName: name, format: importFormat)
                    showImportModal = false
                    importText = ""
                    importName = ""
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "square.and.arrow.down.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("IMPORT TO ROUTE LIBRARY")
                            .font(.system(size: 11, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(TacticalTokens.signalSatelliteCyan)
                    .cornerRadius(8)
                }
            }
            .padding(16)
        }
    }
}
