import SwiftUI
import SharedLogic
import CoreImage.CIFilterBuiltins

struct ContentView: View {
    @StateObject private var vm = IntercomViewModel()
    @State private var showPeersSheet: Bool = false
    @State private var showDiagnosticsSheet: Bool = false

    var body: some View {
        ZStack {
            // High-contrast tactical cockpit background
            Color(UIColor { traitCollection in
                traitCollection.userInterfaceStyle == .dark
                    ? UIColor(red: 0.05, green: 0.07, blue: 0.1, alpha: 1.0)
                    : UIColor(red: 0.94, green: 0.95, blue: 0.97, alpha: 1.0)
            })
            .ignoresSafeArea()

            VStack(spacing: 8) {
                // 1. Emergency Preemption Banner
                if vm.isEmergencyActive {
                    EmergencySosBanner(
                        blackboxCount: vm.blackboxBufferedCount,
                        onCancelSos: { vm.clearBlackboxCache() }
                    )
                }

                // 2. Top Tactile Avionics Header (iOS HIG / ADR-014 / ICA-3102)
                TactileAvionicsHeader(
                    groupName: vm.groupName,
                    peerCount: max(vm.peers.count, 1),
                    isCloudConnected: vm.failoverSnapshot?.isCloudConnected ?? false,
                    batteryPercentage: vm.accessoryState?.batteryLevel?.intValue ?? 85,
                    isLowPower: (vm.powerGovernorState?.dutyCycle.dutyCyclePercent ?? 100) < 50,
                    onOpenPeersSheet: { showPeersSheet = true },
                    onOpenTrailSheet: { vm.showTrailSheet = true },
                    onOpenShareSheet: { vm.showQrShare = true },
                    onOpenDiagnostics: { showDiagnosticsSheet = true }
                )
                .padding(.horizontal, 14)

                // 3. Tactile Skeuomorphic Channel Selector Dial (ADR-014 / ICA-3102)
                TactileChannelDial(
                    activeChannelId: vm.activeChannelId,
                    monitoredChannelIds: vm.monitoredChannelIds,
                    onSelectChannel: { id in vm.selectChannel(id: id) },
                    onToggleDualWatch: { id in vm.toggleDualWatch(channelId: id) }
                )
                .padding(.horizontal, 14)

                // Geofence Tether Breach or Convoy Split Warning Banner (Sprint 20 / ICA-2003)
                if let geofence = vm.geofenceSnapshot,
                   geofence.overallStatus == .breach || geofence.splitWarning != nil {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        VStack(alignment: .leading, spacing: 2) {
                            let title = geofence.splitWarning != nil ? "CONVOY SPLIT (\(Int(geofence.splitWarning?.clusterGapMeters ?? 0))m GAP)" : "TETHER BREACH (>600m)"
                            Text(title)
                                .font(.system(size: 10, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                            Text("Lead: \(Int(geofence.furthestRiderDistanceMeters))m • Check Radar")
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(.white.opacity(0.85))
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.red)
                    .cornerRadius(8)
                    .padding(.horizontal, 14)
                }

                // Voice Command Feedback Badge (Sprint 21 / ICA-2103)
                if let cmd = vm.latestVoiceCommand {
                    TacticalVoiceCommandBadge(command: cmd, onDismiss: { vm.dismissVoiceCommandBadge() })
                }

                // 4. Tactical Vector Radar with Liquid Glass HUD Card (ICA-3103)
                TactileRadarCard(
                    radarSnapshot: vm.radarSnapshot,
                    convoySnapshot: vm.convoySnapshot,
                    trailMapSnapshot: vm.trailMapSnapshot,
                    geofenceSnapshot: vm.geofenceSnapshot,
                    peersCount: vm.peers.count,
                    onCycleZoom: { vm.cycleRadarZoom() },
                    onOpenTrailCockpit: { vm.showTrailSheet = true }
                )
                .padding(.horizontal, 14)
                .padding(.vertical, 2)

                Spacer(minLength: 4)

                // 5. Zero-Scroll Master Tactical Controls (ADR-014 / ICA-3102)
                HStack(alignment: .center, spacing: 10) {
                    // Left: Guarded Emergency SOS Switch (Safety Flip-Cover)
                    GuardedSosSwitch(
                        isEmergencyActive: vm.isEmergencyActive,
                        onTriggerEmergency: { vm.triggerEmergencySos() },
                        onCancelEmergency: { vm.clearBlackboxCache() }
                    )

                    // Center: 160pt Master Tactile PTT Actuator
                    TactilePttButton(
                        isTransmitting: vm.isTransmitting,
                        onPressStart: { vm.startPtt() },
                        onPressEnd: { vm.stopPtt() },
                        channelLabel: "CH 0\(vm.activeChannelId)",
                        subtitle: vm.isVoxLocked ? "VOX LOCK" : "HOLD TO TALK"
                    )

                    // Right: Audio VU Level Meter, VOX Lock & Volume quick toggles
                    VStack(spacing: 8) {
                        TactileVuMeter(
                            currentRmsLevel: vm.inputLevel,
                            barCount: 10,
                            showDbLabel: true
                        )

                        HStack(spacing: 6) {
                            // Volume quick toggle button
                            Button(action: {
                                TactileHaptics.shared.channelDetentClick()
                                vm.cycleVolume()
                            }) {
                                Image(systemName: volumeIcon(for: vm.speakerVolume))
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.white)
                                    .frame(width: 36, height: 32)
                                    .background(Color(red: 0.12, green: 0.14, blue: 0.18))
                                    .neobrutalistOutline(
                                        color: TacticalTokens.neobrutalBorderIdle,
                                        lineWidth: 1.0,
                                        cornerRadius: 6
                                    )
                            }
                            .buttonStyle(.plain)

                            // VOX Lock toggle button
                            Button(action: {
                                TactileHaptics.shared.channelDetentClick()
                                vm.toggleVoxLock()
                            }) {
                                Text(vm.isVoxLocked ? "VOX ON" : "VOX OFF")
                                    .font(.system(size: 9, weight: .black, design: .monospaced))
                                    .foregroundColor(vm.isVoxLocked ? TacticalTokens.signalAmber : Color(red: 0.55, green: 0.58, blue: 0.62))
                                    .frame(height: 32)
                                    .padding(.horizontal, 6)
                                    .background(vm.isVoxLocked ? Color(red: 0.18, green: 0.10, blue: 0.07) : Color(red: 0.12, green: 0.14, blue: 0.18))
                                    .neobrutalistOutline(
                                        color: vm.isVoxLocked ? TacticalTokens.signalAmber : TacticalTokens.neobrutalBorderIdle,
                                        lineWidth: 1.0,
                                        cornerRadius: 6
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 6)
            }

            // Emergency Crash Countdown Card Overlay (Sprint 21 / ICA-2103)
            if vm.showCrashCountdownCard {
                CrashCountdownCardView(
                    snapshot: vm.crashSnapshot,
                    onCancel: { vm.cancelCrashCountdown() },
                    onSosNow: { vm.triggerManualCrashSos() }
                )
            }
        }
        .sheet(isPresented: $vm.showQrScanner) {
            QrScannerView { scannedCode in vm.joinGroup(inviteCode: scannedCode) }
        }
        .sheet(isPresented: $vm.showQrShare) {
            QrShareView(groupName: vm.groupName, inviteCode: vm.inviteCode)
        }
        .sheet(isPresented: $vm.showMessageDrawer) {
            MessageDrawerSheet(
                messages: vm.messages,
                onSendCustomText: { text in vm.sendCustomText(text) },
                onMarkAllRead: { vm.markAllMessagesAsRead() },
                onClearHistory: { vm.clearMessageHistory() }
            )
        }
        .sheet(isPresented: $showPeersSheet) {
            PeersSheetView(peers: vm.peers, isKeepAliveActive: vm.isKeepAliveActive)
        }
        .sheet(isPresented: $vm.showRepeaterSheet) {
            BasecampRepeaterView(vm: vm)
        }
        .sheet(isPresented: $vm.showTrailSheet) {
            OfflineTrailCockpitSheet(vm: vm)
        }
        .sheet(isPresented: $vm.showTrailManagerSheet) {
            TrailFileManagerSheet(vm: vm)
        }
        .sheet(isPresented: $vm.showRoutingSheet) {
            RoutingDiagnosticsSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showFederationSheet) {
            FederationDiagnosticsSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showQoSSheet) {
            TacticalQoSDiagnosticsSheetView(vm: vm)
        }
        .sheet(isPresented: $vm.showFailoverSheet) {
            TacticalTransportHandoverSheetView(vm: vm)
        }
        .sheet(isPresented: $showDiagnosticsSheet) {
            SubsystemDiagnosticsSheetView(vm: vm)
        }
        .alert("Intercom Alert", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { vm.errorMessage = nil }
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }

    private func volumeIcon(for volume: Float) -> String {
        if volume > 0.6 { return "speaker.wave.3.fill" }
        if volume > 0.2 { return "speaker.wave.2.fill" }
        return "speaker.wave.1.fill"
    }
}

// --- Crash Countdown Emergency Card (Sprint 21 / ICA-2103) ---

struct CrashCountdownCardView: View {
    let snapshot: CrashCountdownSnapshot?
    let onCancel: () -> Void
    let onSosNow: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.85)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.octagon.fill")
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(.red)
                    Text("VEHICULAR CRASH DETECTED")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(.red)
                }

                Text("\(snapshot?.remainingSeconds ?? 30)")
                    .font(.system(size: 64, weight: .black, design: .monospaced))
                    .foregroundColor(.white)

                let peakG = snapshot?.peakGForce ?? 0.0
                Text("SEVERITY: \(snapshot?.severity.name ?? "POTENTIAL_FALL_OVER") • PEAK: \(String(format: "%.1f", peakG))g")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(Color.red.opacity(0.85))

                Text("Automatic eCall mesh emergency beacon dispatching in \(snapshot?.remainingSeconds ?? 30)s unless cancelled.")
                    .font(.system(size: 11, weight: .medium))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.white.opacity(0.85))
                    .padding(.horizontal, 8)

                Button(action: {
                    UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                    onCancel()
                }) {
                    Text("I AM OK — CANCEL ALERT")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.green)
                        .cornerRadius(10)
                }

                Button(action: {
                    UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                    onSosNow()
                }) {
                    Text("DISPATCH SOS NOW")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.red)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Color.red.opacity(0.15))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.red, lineWidth: 1))
                }
            }
            .padding(22)
            .background(Color(red: 0.1, green: 0.04, blue: 0.04))
            .cornerRadius(18)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.red, lineWidth: 2))
            .padding(24)
        }
    }
}

// --- Offline Voice Command Feedback Badge (Sprint 21 / ICA-2103) ---

struct TacticalVoiceCommandBadge: View {
    let command: CommandDetection
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "mic.fill")
                .font(.system(size: 11, weight: .black))
                .foregroundColor(.cyan)
            Text("VOICE: \(command.command.phrase.uppercased())")
                .font(.system(size: 10, weight: .black, design: .monospaced))
                .foregroundColor(.cyan)
            Text("(\(Int(command.confidence * 100))%)")
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(.cyan.opacity(0.8))
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.cyan.opacity(0.8))
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(Color(red: 0.04, green: 0.12, blue: 0.16))
        .cornerRadius(6)
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cyan.opacity(0.6), lineWidth: 1))
        .padding(.horizontal, 14)
    }
}

// --- Emergency Override Banner ---

struct EmergencySosBanner: View {
    let blackboxCount: Int
    let onCancelSos: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.octagon.fill")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)

            VStack(alignment: .leading, spacing: 2) {
                Text("EMERGENCY VOICE PREEMPTION ACTIVE")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(.white)
                Text("Audio channels locked • Blackbox: \(blackboxCount) frames (~60s)")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.white.opacity(0.85))
            }

            Spacer()

            Button(action: onCancelSos) {
                Text("CLEAR")
                    .font(.system(size: 10, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white)
                    .foregroundColor(.red)
                    .cornerRadius(6)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(Color.red)
        .cornerRadius(10)
        .padding(.horizontal)
    }
}

// --- Sub-Channel Dial Selector ---

struct ChannelDialView: View {
    let activeChannelId: Int
    let monitoredChannelIds: Set<Int>
    let isScanMode: Bool
    let onSelectChannel: (Int) -> Void
    let onToggleDualWatch: (Int) -> Void
    let onToggleScan: () -> Void

    private let channelPresets: [(id: Int, name: String)] = [
        (1, "1: Main"),
        (2, "2: Lead"),
        (3, "3: Sweep"),
        (4, "4: Support"),
        (5, "5: Convoy"),
        (6, "6: Trail"),
        (7, "7: City"),
        (8, "8: Off-Grid"),
        (16, "16: SOS")
    ]

    var body: some View {
        VStack(spacing: 6) {
            HStack {
                Text("Intercom Channel")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.secondary)

                Spacer()

                Button(action: onToggleScan) {
                    HStack(spacing: 4) {
                        Image(systemName: isScanMode ? "antenna.radiowaves.left.and.right" : "waveform.path")
                            .font(.system(size: 10))
                        Text(isScanMode ? "SCAN ON" : "SCAN")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundColor(isScanMode ? .green : .secondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(isScanMode ? Color.green.opacity(0.15) : Color(UIColor.secondarySystemBackground))
                    .cornerRadius(8)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(channelPresets, id: \.id) { ch in
                        let isActive = ch.id == activeChannelId
                        let isDual = monitoredChannelIds.contains(ch.id)

                        Button(action: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            onSelectChannel(ch.id)
                        }) {
                            VStack(spacing: 2) {
                                Text(ch.name)
                                    .font(.system(size: 11, weight: isActive ? .bold : .medium))
                                    .foregroundColor(isActive ? .white : .primary)

                                if isDual {
                                    Text("2-WATCH")
                                        .font(.system(size: 8, weight: .black))
                                        .foregroundColor(isActive ? .white : .blue)
                                }
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(isActive ? (ch.id == 16 ? Color.red : Color.blue) : Color(UIColor.secondarySystemBackground))
                            .cornerRadius(10)
                        }
                        .contextMenu {
                            Button(isDual ? "Disable Dual-Watch" : "Set as Dual-Watch") {
                                onToggleDualWatch(ch.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Subsystems & Diagnostics Sheet (Apple HIG) ---

struct SubsystemDiagnosticsSheetView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                // Section 1: Basecamp Stationary Repeater
                Section(header: Text("Basecamp Stationary Repeater")) {
                    if let beacon = vm.latestBeacon {
                        HStack {
                            Label("Repeater", systemImage: "antenna.radiowaves.left.and.right")
                            Spacer()
                            Text(beacon.repeaterName.isEmpty ? "BASECAMP" : beacon.repeaterName)
                                .foregroundColor(.secondary)
                        }
                        HStack {
                            Text("Battery Level")
                            Spacer()
                            Text("🔋 \(beacon.batteryPercent)%")
                                .foregroundColor(.green)
                        }
                        HStack {
                            Text("Power Source")
                            Spacer()
                            Text(beacon.isSolarPowered ? "☀️ Solar" : "External DC")
                                .foregroundColor(.secondary)
                        }
                        if let rtt = vm.lastPingLatencyMs {
                            HStack {
                                Text("Ping Latency")
                                Spacer()
                                Text("\(rtt) ms")
                                    .bold()
                                    .foregroundColor(.green)
                            }
                        }
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            vm.triggerDiagnosticPing()
                        }) {
                            HStack {
                                Spacer()
                                Text(vm.isPingingRepeater ? "Pinging Repeater..." : "Send Diagnostic Ping")
                                    .fontWeight(.bold)
                                    .foregroundColor(.cyan)
                                Spacer()
                            }
                        }
                    } else {
                        Text("No active repeater beacon detected in BLE range.")
                            .foregroundColor(.secondary)
                    }

                    Button(action: {
                        dismiss()
                        vm.showRepeaterSheet = true
                    }) {
                        Text("Open Full Repeater Station Cockpit")
                            .foregroundColor(.blue)
                    }
                }

                // Section 2: Audio & Helmet Accessory
                Section(header: Text("Audio & Helmet Accessory")) {
                    if let acc = vm.accessoryState, acc.isConnected {
                        HStack {
                            Label(acc.deviceName.isEmpty ? acc.brand.name : acc.deviceName, systemImage: "headphones")
                            Spacer()
                            Text("Connected")
                                .foregroundColor(.green)
                        }
                        HStack {
                            Text("Battery")
                            Spacer()
                            Text("\(acc.batteryLevel?.intValue ?? 100)%")
                                .foregroundColor(.green)
                        }
                    } else {
                        HStack {
                            Label("Device Speaker / Mic", systemImage: "speaker.wave.2")
                            Spacer()
                            Text("Direct Audio")
                                .foregroundColor(.secondary)
                        }
                    }
                }

                // Section 3: Mesh Routing (AODV-Lite)
                Section(header: Text("AODV-Lite Mesh Routing")) {
                    if let routing = vm.routingSnapshot {
                        HStack {
                            Text("Active Routes")
                            Spacer()
                            Text("\(routing.activeRoutes.count)")
                                .bold()
                        }
                        HStack {
                            Text("Airtime Savings")
                            Spacer()
                            Text("\(Int(routing.airtimeSavingsPercentage))%")
                                .foregroundColor(.cyan)
                        }
                        HStack {
                            Text("Packet Routing")
                            Spacer()
                            Text("\(routing.totalDirectedPackets) Dir / \(routing.totalFloodedPackets) Flood")
                                .foregroundColor(.secondary)
                        }
                        Button(action: {
                            dismiss()
                            vm.showRoutingSheet = true
                        }) {
                            Text("Open Routing Table & Path Inspector")
                                .foregroundColor(.blue)
                        }
                    } else {
                        Text("Mesh routing active on standard flood mode.")
                            .foregroundColor(.secondary)
                    }
                }

                // Section 4: Squad Federation
                Section(header: Text("Inter-Squad Federation")) {
                    if let fed = vm.federationSnapshot {
                        HStack {
                            Text("Federated Squads")
                            Spacer()
                            Text("\(fed.federatedGroups.count)")
                        }
                        HStack {
                            Text("Local Gateway Role")
                            Spacer()
                            Text(fed.localRole.name)
                                .foregroundColor(.blue)
                        }
                        HStack {
                            Text("Relayed Packets")
                            Spacer()
                            Text("\(fed.totalRelayedPacketsIn) in / \(fed.totalRelayedPacketsOut) out")
                                .foregroundColor(.secondary)
                        }
                        Button(action: {
                            dismiss()
                            vm.showFederationSheet = true
                        }) {
                            Text("Manage Federation & Squad Links")
                                .foregroundColor(.blue)
                        }
                    } else {
                        Text("Standalone Squad Mode (Unfederated).")
                            .foregroundColor(.secondary)
                    }
                }

                // Section 5: QoS & Airtime Management
                Section(header: Text("RF QoS & Traffic Shaping")) {
                    if let qos = vm.qosSnapshot {
                        HStack {
                            Text("Estimated Airtime")
                            Spacer()
                            Text("\(qos.estimatedAirtimeUtilizationPercent)%")
                                .foregroundColor(qos.isChannelCongested ? .red : .primary)
                        }
                        HStack {
                            Text("Channel Status")
                            Spacer()
                            Text(qos.isChannelCongested ? "CONGESTED" : "OPTIMAL")
                                .bold()
                                .foregroundColor(qos.isChannelCongested ? .red : .green)
                        }
                        if qos.voicePreemptionCount > 0 {
                            HStack {
                                Text("Voice Preemptions")
                                Spacer()
                                Text("\(qos.voicePreemptionCount)")
                                    .foregroundColor(.orange)
                            }
                        }
                        Button(action: {
                            dismiss()
                            vm.showQoSSheet = true
                        }) {
                            Text("Open QoS Diagnostics")
                                .foregroundColor(.blue)
                        }
                    }
                }

                // Section 6: Multi-Path Failover
                Section(header: Text("Transport Failover & Redundancy")) {
                    if let failover = vm.failoverSnapshot {
                        HStack {
                            Text("Current Path")
                            Spacer()
                            Text(failover.currentMode.name)
                                .bold()
                                .foregroundColor(.green)
                        }
                        HStack {
                            Text("Average ETX Score")
                            Spacer()
                            Text(String(format: "%.1f", failover.averageEtx))
                                .foregroundColor(.secondary)
                        }
                        HStack {
                            Text("Total Handovers")
                            Spacer()
                            Text("\(failover.totalHandovers)")
                        }
                        Button(action: {
                            dismiss()
                            vm.showFailoverSheet = true
                        }) {
                            Text("Open Failover Monitor")
                                .foregroundColor(.blue)
                        }
                    }
                }

                // Section 7: Battery Governor
                if let power = vm.powerGovernorState {
                    Section(header: Text("Battery Power Governor")) {
                        HStack {
                            Text("Active Policy")
                            Spacer()
                            Text(power.policy.name)
                                .foregroundColor(.orange)
                        }
                        HStack {
                            Text("Duty Cycle")
                            Spacer()
                            Text("\(Int(power.dutyCycle.dutyCyclePercent))%")
                        }
                    }
                }

                // Section 8: Quick Tools
                Section(header: Text("Cockpit Quick Actions")) {
                    Button(action: {
                        dismiss()
                        vm.showTrailManagerSheet = true
                    }) {
                        Label("OTA Trail Route Manager", systemImage: "arrow.triangle.2.circlepath")
                    }
                    Button(action: {
                        dismiss()
                        vm.showMessageDrawer = true
                    }) {
                        Label("Text Messages Drawer", systemImage: "bubble.left.and.bubble.right")
                    }
                    Button(action: {
                        dismiss()
                        vm.showQrScanner = true
                    }) {
                        Label("Scan QR to Join Squad", systemImage: "qrcode.viewfinder")
                    }
                }
            }
            .navigationTitle("Diagnostics & Tools")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        TactileHaptics.shared.channelDetentClick()
                        dismiss()
                    }
                }
            }
        }
    }
}

// --- Tactical Vector Radar View ---

struct TacticalVectorRadarView: View {
    let radarSnapshot: RadarSnapshot?
    let convoySnapshot: ConvoyTelemetrySnapshot?
    let trailMapSnapshot: TrailMapSnapshot?
    let geofenceSnapshot: ConvoyGeofenceSnapshot?
    let peersCount: Int
    let onCycleZoom: () -> Void
    let onOpenTrailCockpit: () -> Void

    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                // Outer circle background
                Circle()
                    .fill(Color(red: 0.04, green: 0.08, blue: 0.12))
                    .frame(width: 170, height: 170)
                    .overlay(Circle().stroke(Color.cyan.opacity(0.3), lineWidth: 1.5))

                // Topographic Contours (Subtle Dashes)
                if let trails = trailMapSnapshot, trails.isOverlayEnabled {
                    ForEach(trails.projectedContours, id: \.elevationMeters) { contour in
                        Path { path in
                            var isFirst = true
                            for pt in contour.points {
                                let x = 85.0 + CGFloat(pt.normalizedX) * 75.0
                                let y = 85.0 + CGFloat(pt.normalizedY) * 75.0
                                if isFirst {
                                    path.move(to: CGPoint(x: x, y: y))
                                    isFirst = false
                                } else {
                                    path.addLine(to: CGPoint(x: x, y: y))
                                }
                            }
                        }
                        .stroke(Color.gray.opacity(0.35), style: StrokeStyle(lineWidth: 0.8, dash: [3, 3]))
                        .frame(width: 170, height: 170)
                    }

                    // Vector Trail Tracks (Neon Cyan)
                    ForEach(trails.projectedTrails, id: \.trailId) { seg in
                        Path { path in
                            var isFirst = true
                            for pt in seg.points {
                                let x = 85.0 + CGFloat(pt.normalizedX) * 75.0
                                let y = 85.0 + CGFloat(pt.normalizedY) * 75.0
                                if isFirst {
                                    path.move(to: CGPoint(x: x, y: y))
                                    isFirst = false
                                } else {
                                    path.addLine(to: CGPoint(x: x, y: y))
                                }
                            }
                        }
                        .stroke(Color(red: 0.0, green: 0.9, blue: 1.0).opacity(0.85), style: StrokeStyle(lineWidth: 2.0, lineCap: .round, lineJoin: .round))
                        .frame(width: 170, height: 170)
                    }

                    // Local Rider Breadcrumbs
                    ForEach(0..<trails.localBreadcrumbs.count, id: \.self) { idx in
                        let pt = trails.localBreadcrumbs[idx]
                        let x = CGFloat(pt.normalizedX) * 75.0
                        let y = CGFloat(pt.normalizedY) * 75.0
                        Circle()
                            .fill(Color.cyan.opacity(0.45))
                            .frame(width: 3, height: 3)
                            .offset(x: x, y: y)
                    }

                    // Waypoints
                    ForEach(trails.projectedWaypoints, id: \.waypoint.id) { pw in
                        let x = CGFloat(pw.projectedPoint.normalizedX) * 75.0
                        let y = CGFloat(pw.projectedPoint.normalizedY) * 75.0
                        ZStack {
                            Circle()
                                .fill(waypointColor(for: pw.waypoint.type))
                                .frame(width: 6, height: 6)
                            Circle()
                                .stroke(Color.white, lineWidth: 1)
                                .frame(width: 6, height: 6)
                        }
                        .offset(x: x, y: y)
                    }
                }

                // Distance concentric rings
                Circle()
                    .stroke(Color.cyan.opacity(0.15), lineWidth: 1)
                    .frame(width: 120, height: 120)

                Circle()
                    .stroke(Color.cyan.opacity(0.12), lineWidth: 1)
                    .frame(width: 65, height: 65)

                // Convoy Tether Perimeter Ring & Hazard Alarms (Sprint 20 - ICA-2003)
                if let geofence = geofenceSnapshot {
                    let maxDist = trailMapSnapshot?.activeRangeZoom.maxDistanceMeters ?? 1500.0
                    let tetherDiameter = CGFloat(min(600.0 / maxDist, 1.0)) * 150.0
                    Circle()
                        .stroke(
                            geofence.overallStatus == .breach ? Color.red : (geofence.overallStatus == .warning ? Color.orange : Color.cyan.opacity(0.2)),
                            style: StrokeStyle(lineWidth: geofence.overallStatus == .breach ? 1.5 : 1.0, dash: [4, 4])
                        )
                        .frame(width: tetherDiameter, height: tetherDiameter)

                    // Active Hazard Radar Icons
                    ForEach(geofence.activeHazardAlerts, id: \.waypointId) { hazard in
                        let dist = min(max(hazard.distanceMeters, 10.0), maxDist)
                        let normalized = CGFloat(dist / maxDist)
                        let rad = CGFloat(hazard.relativeBearingDegrees * .pi / 180.0)
                        let r = normalized * 75.0
                        let x = r * sin(rad)
                        let y = -r * cos(rad)

                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 8))
                            .foregroundColor(.yellow)
                            .offset(x: x, y: y)
                    }
                }

                // Crosshairs
                Rectangle()
                    .fill(Color.cyan.opacity(0.15))
                    .frame(width: 1, height: 170)
                Rectangle()
                    .fill(Color.cyan.opacity(0.15))
                    .frame(width: 170, height: 1)

                // Range Labels
                let rangeLabel = trailMapSnapshot?.activeRangeZoom.label ?? "1.5km"
                Text(rangeLabel)
                    .font(.system(size: 7, weight: .bold, design: .monospaced))
                    .foregroundColor(.cyan.opacity(0.6))
                    .offset(y: -75)

                // Heading North Chevron
                Image(systemName: "chevron.up")
                    .font(.system(size: 9, weight: .black))
                    .foregroundColor(.cyan)
                    .offset(y: -78)

                // Center local rider dot
                Circle()
                    .fill(Color.blue)
                    .frame(width: 10, height: 10)
                    .overlay(Circle().stroke(Color.white, lineWidth: 1.5))
                    .shadow(color: .blue, radius: 4)

                // Plotted convoy riders
                if let riders = convoySnapshot?.riders {
                    ForEach(riders, id: \.peerId) { rider in
                        let maxDist = trailMapSnapshot?.activeRangeZoom.maxDistanceMeters ?? 1500.0
                        let dist = min(max(rider.distanceMeters, 10.0), maxDist)
                        let normalized = CGFloat(dist / maxDist)
                        let rad = CGFloat(rider.relativeBearingDegrees * .pi / 180.0)
                        let r = normalized * 75.0
                        let x = r * sin(rad)
                        let y = -r * cos(rad)

                        let dotColor: Color = rider.distanceMeters < 100 ? .green : (rider.distanceMeters < 500 ? .cyan : .orange)

                        Circle()
                            .fill(dotColor)
                            .frame(width: 8, height: 8)
                            .overlay(Circle().stroke(Color.black, lineWidth: 1))
                            .offset(x: x, y: y)
                    }
                }

                // Interactive Radar Zoom Badge (Upper Right)
                VStack {
                    HStack {
                        Spacer()
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            onCycleZoom()
                        }) {
                            Text(trailMapSnapshot?.activeRangeZoom.label ?? "1.5km")
                                .font(.system(size: 8, weight: .black, design: .monospaced))
                                .foregroundColor(.cyan)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.black.opacity(0.6))
                                .cornerRadius(4)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.cyan.opacity(0.4), lineWidth: 0.8))
                        }
                        .padding(6)
                    }
                    Spacer()
                }
            }
            .frame(width: 170, height: 170)
            .clipShape(Circle())

            // Compact Telemetry Info Strip Under Radar with Trail Launcher
            HStack(spacing: 8) {
                if let geofence = geofenceSnapshot, geofence.overallStatus == .breach || geofence.overallStatus == .warning {
                    HStack(spacing: 3) {
                        Image(systemName: geofence.overallStatus == .breach ? "exclamationmark.octagon.fill" : "bell.fill")
                            .font(.system(size: 8))
                            .foregroundColor(geofence.overallStatus == .breach ? .red : .orange)
                        Text(geofence.overallStatus == .breach ? "BREACH \(Int(geofence.furthestRiderDistanceMeters))m" : "WARN \(Int(geofence.furthestRiderDistanceMeters))m")
                            .font(.system(size: 8, weight: .black, design: .monospaced))
                            .foregroundColor(geofence.overallStatus == .breach ? .red : .orange)
                    }
                    .padding(.horizontal, 4)
                    .padding(.vertical, 2)
                    .background((geofence.overallStatus == .breach ? Color.red : Color.orange).opacity(0.15))
                    .cornerRadius(4)
                }

                if let trail = trailMapSnapshot?.activeTrail {
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        onOpenTrailCockpit()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "point.topleft.down.to.point.bottomright.curvepath")
                                .font(.system(size: 9))
                                .foregroundColor(.cyan)
                            Text(trail.name)
                                .font(.system(size: 9, weight: .bold, design: .monospaced))
                                .foregroundColor(.cyan)
                                .lineLimit(1)
                            if let wpDist = trailMapSnapshot?.nearestWaypointDistanceMeters {
                                Text("• Next: \(Int(truncating: wpDist))m")
                                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                                    .foregroundColor(.white)
                            }
                        }
                    }
                } else if let closest = convoySnapshot?.closestRider {
                    HStack(spacing: 4) {
                        Image(systemName: "location.north.line.fill")
                            .font(.system(size: 9))
                            .foregroundColor(.cyan)
                        Text("\(closest.peerName.ifBlank { closest.peerId }): \(Int(closest.distanceMeters))m")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.cyan)
                    }
                } else {
                    HStack(spacing: 4) {
                        Circle().fill(Color.green).frame(width: 5, height: 5)
                        Text("RADAR SCANNING • \(peersCount) RIDERS")
                            .font(.system(size: 9, weight: .bold, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 3)
            .background(Color.black.opacity(0.2))
            .cornerRadius(6)
        }
    }

    private func waypointColor(for type: WaypointType?) -> Color {
        guard let type = type else { return .cyan }
        switch type {
        case .waterSource: return .blue
        case .camp: return .green
        case .hazard: return .red
        case .summit: return Color(red: 1.0, green: 0.7, blue: 0.0)
        default: return .cyan
        }
    }
}

// --- Tactical Push-To-Talk Button ---

struct TacticalPttButton: View {
    let isTransmitting: Bool
    let isVoxLocked: Bool
    let onPress: () -> Void
    let onRelease: () -> Void

    var body: some View {
        ZStack {
            // Ripple wave when transmitting
            if isTransmitting {
                Circle()
                    .stroke(Color.red.opacity(0.4), lineWidth: 6)
                    .frame(width: 168, height: 168)
                    .scaleEffect(1.15)
                    .animation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true), value: isTransmitting)
            }

            // Outer Glow ring
            Circle()
                .fill(isTransmitting ? Color.red.opacity(0.2) : Color.blue.opacity(0.1))
                .frame(width: 154, height: 154)

            // Main 140pt tactile button
            Circle()
                .fill(
                    LinearGradient(
                        colors: isTransmitting
                            ? [Color(red: 0.95, green: 0.2, blue: 0.2), Color(red: 0.7, green: 0.05, blue: 0.05)]
                            : [Color(red: 0.15, green: 0.5, blue: 0.95), Color(red: 0.08, green: 0.28, blue: 0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 138, height: 138)
                .overlay(
                    Circle().stroke(isTransmitting ? Color.white.opacity(0.8) : Color.cyan.opacity(0.6), lineWidth: 2)
                )
                .shadow(color: isTransmitting ? Color.red.opacity(0.6) : Color.blue.opacity(0.4), radius: isTransmitting ? 18 : 10, y: 4)

            VStack(spacing: 4) {
                Image(systemName: isTransmitting ? "waveform" : "mic.fill")
                    .font(.system(size: 32, weight: .black))
                    .foregroundColor(.white)

                Text(isTransmitting ? "HOT MIC" : "PUSH TO TALK")
                    .font(.system(size: 11, weight: .heavy, design: .monospaced))
                    .foregroundColor(.white)
                    .tracking(1.2)
            }
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isTransmitting && !isVoxLocked {
                        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                        onPress()
                    }
                }
                .onEnded { _ in
                    if isTransmitting {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onRelease()
                    }
                }
        )
    }
}

// --- Tactical Quick Hazard Bar ---

struct TacticalQuickHazardBar: View {
    let onSelectCanned: (CannedMessageCode) -> Void

    private let hazards: [(code: CannedMessageCode, icon: String, label: String, color: Color)] = [
        (.hazardRoad, "exclamationmark.triangle.fill", "HAZARD", Color(red: 1.0, green: 0.8, blue: 0.1)),
        (.fuelLow, "fuelpump.fill", "FUEL", Color(red: 1.0, green: 0.55, blue: 0.1)),
        (.stopImmediate, "hand.raised.fill", "STOP", Color(red: 0.95, green: 0.2, blue: 0.2)),
        (.mechanicalIssue, "wrench.and.screwdriver.fill", "BIKE", Color(red: 0.2, green: 0.65, blue: 1.0))
    ]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(hazards, id: \.label) { h in
                Button(action: { onSelectCanned(h.code) }) {
                    VStack(spacing: 3) {
                        Image(systemName: h.icon)
                            .font(.system(size: 18, weight: .bold))
                        Text(h.label)
                            .font(.system(size: 11, weight: .black, design: .monospaced))
                    }
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .foregroundColor(h.color)
                    .background(h.color.opacity(0.16))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(h.color.opacity(0.4), lineWidth: 1.2)
                    )
                }
            }
        }
    }
}

// --- Tactical SOS Bar ---

struct TacticalSosBar: View {
    let isEmergencyActive: Bool
    let bufferedFrames: Int
    let onTriggerSos: () -> Void

    var body: some View {
        Button(action: onTriggerSos) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.octagon.fill")
                    .font(.system(size: 16, weight: .black))
                    .foregroundColor(.white)

                Text(isEmergencyActive ? "EMERGENCY PREEMPTION ACTIVE" : "EMERGENCY SOS BROADCAST")
                    .font(.system(size: 11, weight: .black, design: .monospaced))
                    .foregroundColor(.white)

                Spacer()

                Text("BLACKBOX ~60s")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .background(Color.red)
            .cornerRadius(8)
            .shadow(color: Color.red.opacity(0.4), radius: 6, y: 2)
        }
    }
}

// --- Peers Sheet View ---

struct PeersSheetView: View {
    let peers: [Peer]
    let isKeepAliveActive: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 12) {
                if peers.isEmpty {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "person.2.slash")
                            .font(.system(size: 40))
                            .foregroundColor(.secondary)
                        Text("No Mesh Peers Connected")
                            .font(.system(size: 15, weight: .bold))
                        Text("Other riders running Intercom-Alpha will appear here via BLE mesh automatically.")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    Spacer()
                } else {
                    List(peers, id: \.id) { peer in
                        PeerRowView(peer: peer)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Connected Riders (\(peers.count))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

// --- Peer Row ---

struct PeerRowView: View {
    let peer: Peer

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(peer.name)
                    .font(.system(size: 13, weight: .semibold))
                Text("RSSI: \(peer.rssi) dBm • \(peer.audioProfile.name)")
                    .font(.system(size: 10))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(peer.isConnected ? "ONLINE" : "OFFLINE")
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(peer.isConnected ? .green : .secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(peer.isConnected ? Color.green.opacity(0.12) : Color.secondary.opacity(0.1))
                .cornerRadius(6)
        }
        .padding(10)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(8)
    }
}

// --- Text Messages Drawer Sheet ---

struct MessageDrawerSheet: View {
    let messages: [MeshTextMessage]
    let onSendCustomText: (String) -> Void
    let onMarkAllRead: () -> Void
    let onClearHistory: () -> Void

    @State private var draftText: String = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if messages.isEmpty {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "bubble.left.and.bubble.right")
                            .font(.system(size: 36))
                            .foregroundColor(.secondary)
                        Text("No Mesh Text Messages")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                } else {
                    List {
                        ForEach(messages, id: \.id) { msg in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(msg.senderName.ifBlank { msg.senderId })
                                        .font(.system(size: 12, weight: .bold))
                                    Spacer()
                                    Text(msg.status.name)
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(.secondary)
                                }
                                Text(msg.content)
                                    .font(.system(size: 13))
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    .listStyle(.plain)
                }

                // Quick Send Bar
                HStack(spacing: 8) {
                    TextField("Send quick text to mesh...", text: $draftText)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 13))

                    Button(action: {
                        let text = draftText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !text.isEmpty {
                            onSendCustomText(text)
                            draftText = ""
                        }
                    }) {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.blue)
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
            }
            .navigationTitle("Mesh Text Messages")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button("Mark All as Read", action: onMarkAllRead)
                        Button("Clear History", role: .destructive, action: onClearHistory)
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
    }
}

// --- QR Share View ---

struct QrShareView: View {
    let groupName: String
    let inviteCode: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Scan to Join \(groupName)")
                    .font(.system(size: 18, weight: .bold))
                    .padding(.top, 20)

                if let qrImage = generateQrCode(from: inviteCode) {
                    Image(uiImage: qrImage)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 220, height: 220)
                        .padding()
                        .background(Color.white)
                        .cornerRadius(16)
                        .shadow(radius: 6)
                }

                Text(inviteCode)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Button("Done") {
                    dismiss()
                }
                .padding(.horizontal, 32)
                .padding(.vertical, 12)
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)

                Spacer()
            }
            .navigationTitle("Invite QR Code")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func generateQrCode(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(Data(string.utf8), forKey: "inputMessage")

        if let outputImage = filter.outputImage {
            if let cgImage = context.createCGImage(outputImage, from: outputImage.extent) {
                return UIImage(cgImage: cgImage)
            }
        }
        return nil
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}

// --- String Convenience Extension ---

extension String {
    func ifBlank(_ fallback: () -> String) -> String {
        self.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? fallback() : self
    }
}

// --- Directed Mesh Routing Diagnostics Sheet (Sprint 22 / ICA-2203) ---

struct RoutingDiagnosticsSheetView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.05, green: 0.07, blue: 0.1).ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Summary Card
                        VStack(alignment: .leading, spacing: 8) {
                            Text("RF AIRTIME CONTENTION MITIGATION")
                                .font(.system(size: 11, weight: .black, design: .monospaced))
                                .foregroundColor(.cyan)

                            let savings = Int(vm.routingSnapshot?.airtimeSavingsPercentage ?? 0)
                            HStack {
                                Text("\(savings)%")
                                    .font(.system(size: 36, weight: .black, design: .monospaced))
                                    .foregroundColor(.green)
                                Text("Airtime Savings Over Flooding")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.secondary)
                            }

                            HStack(spacing: 16) {
                                VStack(alignment: .leading) {
                                    Text("DIRECTED")
                                        .font(.system(size: 9, weight: .black, design: .monospaced))
                                        .foregroundColor(.secondary)
                                    Text("\(vm.routingSnapshot?.totalDirectedPackets ?? 0)")
                                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                                        .foregroundColor(.white)
                                }
                                VStack(alignment: .leading) {
                                    Text("FLOODED")
                                        .font(.system(size: 9, weight: .black, design: .monospaced))
                                        .foregroundColor(.secondary)
                                    Text("\(vm.routingSnapshot?.totalFloodedPackets ?? 0)")
                                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                                        .foregroundColor(.white)
                                }
                                VStack(alignment: .leading) {
                                    Text("ACTIVE ROUTES")
                                        .font(.system(size: 9, weight: .black, design: .monospaced))
                                        .foregroundColor(.secondary)
                                    Text("\(vm.routingSnapshot?.activeRoutes.count ?? 0)")
                                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                                        .foregroundColor(.cyan)
                                }
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(12)

                        // Active Routes Table
                        Text("ACTIVE AODV-LITE ROUTES")
                            .font(.system(size: 11, weight: .black, design: .monospaced))
                            .foregroundColor(.secondary)

                        if let routes = vm.routingSnapshot?.activeRoutes, !routes.isEmpty {
                            ForEach(routes, id: \.destinationId) { route in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("DEST: \(route.destinationId)")
                                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                                            .foregroundColor(.white)
                                        Text("Next Hop: \(route.nextHopId) • Hops: \(route.hopCount)")
                                            .font(.system(size: 11, weight: .medium, design: .monospaced))
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text(route.isValid ? "ACTIVE" : "INVALID")
                                            .font(.system(size: 10, weight: .black, design: .monospaced))
                                            .foregroundColor(route.isValid ? .green : .red)
                                        Text(String(format: "ETX %.1f", route.metricCost))
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .foregroundColor(.cyan)
                                    }
                                }
                                .padding()
                                .background(Color.white.opacity(0.04))
                                .cornerRadius(8)
                            }
                        } else {
                            Text("No active directed routes currently established. Broadcast discovery triggers automatically when sending directed frames.")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                                .padding()
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("AODV Routing HUD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

// ================= MULTI-GROUP FEDERATION GATEWAY SHEET (Sprint 23 / ICA-2303) =================

struct FederationDiagnosticsSheetView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var inviteInputText: String = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Local Gateway Card
                    if let fed = vm.federationSnapshot {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text("LOCAL SQUAD: \(fed.localGroupId)")
                                    .font(.system(size: 13, weight: .black, design: .monospaced))
                                    .foregroundColor(.white)
                                Spacer()
                                Text(fed.localRole.name)
                                    .font(.system(size: 10, weight: .black, design: .monospaced))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(fed.localRole == .primary ? Color.blue : Color.gray)
                                    .cornerRadius(4)
                            }

                            Divider().background(Color.white.opacity(0.1))

                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("RELAYED IN")
                                        .font(.system(size: 8, weight: .bold))
                                        .foregroundColor(.secondary)
                                    Text("\(fed.totalRelayedPacketsIn) pkts")
                                        .font(.system(size: 12, weight: .black, design: .monospaced))
                                        .foregroundColor(.cyan)
                                }
                                Spacer()
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("RELAYED OUT")
                                        .font(.system(size: 8, weight: .bold))
                                        .foregroundColor(.secondary)
                                    Text("\(fed.totalRelayedPacketsOut) pkts")
                                        .font(.system(size: 12, weight: .black, design: .monospaced))
                                        .foregroundColor(.green)
                                }
                                Spacer()
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("DROPPED")
                                        .font(.system(size: 8, weight: .bold))
                                        .foregroundColor(.secondary)
                                    Text("\(fed.totalDroppedPolicyViolations) pkts")
                                        .font(.system(size: 12, weight: .black, design: .monospaced))
                                        .foregroundColor(.orange)
                                }
                            }
                        }
                        .padding()
                        .background(Color.white.opacity(0.06))
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blue.opacity(0.4), lineWidth: 1))
                    }

                    // Connected Sibling Convoys Section
                    VStack(alignment: .leading, spacing: 10) {
                        Text("FEDERATED SIBLING CONVOYS (\(vm.federationSnapshot?.federatedGroups.count ?? 0))")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        if let groups = vm.federationSnapshot?.federatedGroups, !groups.isEmpty {
                            ForEach(groups, id: \.groupId) { grp in
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack {
                                        Text(grp.groupName)
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(.white)
                                        Spacer()
                                        Text("\(grp.linkRssi) dBm")
                                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                                            .foregroundColor(.green)
                                    }

                                    Text("ID: \(grp.groupId) • Gateway: \(grp.gatewayPeerId) • Relayed: \(grp.relayedPacketsCount)")
                                        .font(.system(size: 10, design: .monospaced))
                                        .foregroundColor(.secondary)

                                    HStack(spacing: 6) {
                                        Button("CAPTAIN") {
                                            vm.setFederationPolicy(groupId: grp.groupId, policy: .captainOnly)
                                        }
                                        .buttonStyle(.bordered)
                                        .tint(grp.policy == .captainOnly ? .blue : .gray)

                                        Button("SOS ONLY") {
                                            vm.setFederationPolicy(groupId: grp.groupId, policy: .emergencySosOnly)
                                        }
                                        .buttonStyle(.bordered)
                                        .tint(grp.policy == .emergencySosOnly ? .blue : .gray)

                                        Button("ALL BRIDGED") {
                                            vm.setFederationPolicy(groupId: grp.groupId, policy: .allMembersBridged)
                                        }
                                        .buttonStyle(.bordered)
                                        .tint(grp.policy == .allMembersBridged ? .blue : .gray)
                                    }
                                    .font(.system(size: 9, weight: .bold))
                                }
                                .padding()
                                .background(Color.white.opacity(0.04))
                                .cornerRadius(8)
                            }
                        } else {
                            Text("No active sibling convoys connected. Create or accept an invite code below to bridge motorcycle squads.")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                                .padding()
                        }
                    }

                    // Active Generated Invite Code
                    if let inviteCode = vm.generatedFederationInvite {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("ACTIVE SQUAD INVITE CODE")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.cyan)

                            Text(inviteCode)
                                .font(.system(size: 9, design: .monospaced))
                                .foregroundColor(.white)
                                .padding(8)
                                .background(Color.black.opacity(0.4))
                                .cornerRadius(6)

                            HStack {
                                Spacer()
                                Button("Done") {
                                    vm.dismissFederationInviteDialog()
                                }
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.cyan)
                            }
                        }
                        .padding()
                        .background(Color.blue.opacity(0.2))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cyan, lineWidth: 1))
                    }

                    // Invite Actions
                    VStack(spacing: 10) {
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            vm.createFederationInvite()
                        }) {
                            HStack {
                                Image(systemName: "qrcode")
                                Text("CREATE SQUAD INVITE")
                            }
                            .font(.system(size: 12, weight: .black))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.blue)
                            .cornerRadius(10)
                        }

                        HStack(spacing: 8) {
                            TextField("Paste FEDERATION:v1: code...", text: $inviteInputText)
                                .textFieldStyle(.roundedBorder)
                                .font(.system(size: 11, design: .monospaced))

                            Button("JOIN") {
                                if !inviteInputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    _ = vm.acceptFederationInvite(code: inviteInputText.trimmingCharacters(in: .whitespacesAndNewlines))
                                    inviteInputText = ""
                                }
                            }
                            .font(.system(size: 12, weight: .bold))
                            .buttonStyle(.borderedProminent)
                            .tint(.green)
                        }
                    }
                    .padding(.top, 8)
                }
                .padding()
            }
            .navigationTitle("Federation Gateway HUD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

// --- Tactical Mesh Airtime & QoS Diagnostics Sheet (Sprint 24 / ICA-2403) ---

struct TacticalQoSDiagnosticsSheetView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    let qos = vm.qosSnapshot ?? QoSSnapshot(
                        emergencyQueueDepth: 0,
                        voiceQueueDepth: 0,
                        controlQueueDepth: 0,
                        bulkQueueDepth: 0,
                        totalPacketsEnqueued: 0,
                        totalPacketsDispatched: 0,
                        totalPacketsDropped: 0,
                        voicePreemptionCount: 0,
                        isVoiceActive: false,
                        bulkThrottleActive: false,
                        estimatedAirtimeUtilizationPercent: 0
                    )

                    // 1. Airtime Utilization Progress Card
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("AIRTIME UTILIZATION")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(qos.estimatedAirtimeUtilizationPercent)%")
                                .font(.system(size: 14, weight: .black, design: .monospaced))
                                .foregroundColor(qos.estimatedAirtimeUtilizationPercent >= 75 ? .red : (qos.estimatedAirtimeUtilizationPercent > 50 ? .orange : .green))
                        }

                        ProgressView(value: Double(qos.estimatedAirtimeUtilizationPercent), total: 100.0)
                            .tint(qos.estimatedAirtimeUtilizationPercent >= 75 ? .red : (qos.estimatedAirtimeUtilizationPercent > 50 ? .orange : .cyan))
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)

                    // 2. Channel Congestion Warning
                    if qos.isChannelCongested {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.red)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("CHANNEL CONGESTION ACTIVE")
                                    .font(.system(size: 11, weight: .black, design: .monospaced))
                                    .foregroundColor(.red)
                                Text("Bulk packets throttled (\(qos.bulkThrottleActive ? "<=10%" : "off")) • Protecting voice latency budget")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.white.opacity(0.85))
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.18))
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.red.opacity(0.4), lineWidth: 1))
                    }

                    // 3. 4-Tier QoS Queue Depths
                    VStack(alignment: .leading, spacing: 10) {
                        Text("QoS QUEUE DEPTHS (TOTAL: \(qos.totalPendingQueueDepth))")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        VStack(spacing: 6) {
                            queueRow(title: "1. Emergency Critical (SOS/Crash)", count: qos.emergencyQueueDepth, color: .red)
                            queueRow(title: "2. Active Voice (Audio Streams)", count: qos.voiceQueueDepth, color: .cyan)
                            queueRow(title: "3. Routing Control (AODV/Epoch)", count: qos.controlQueueDepth, color: .green)
                            queueRow(title: "4. Bulk Telemetry (GPX/Status)", count: qos.bulkQueueDepth, color: .orange)
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)

                    // 4. Arbiter & Performance Telemetry
                    VStack(alignment: .leading, spacing: 10) {
                        Text("ARBITER STATISTICS")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        VStack(spacing: 6) {
                            statRow(title: "Total Packets Enqueued", value: "\(qos.totalPacketsEnqueued)")
                            statRow(title: "Total Packets Dispatched", value: "\(qos.totalPacketsDispatched)")
                            statRow(title: "Total Packets Dropped", value: "\(qos.totalPacketsDropped)", isWarning: qos.totalPacketsDropped > 0)
                            statRow(title: "Voice Preemption Events", value: "\(qos.voicePreemptionCount)")
                            statRow(title: "Voice Stream Active", value: qos.isVoiceActive ? "YES" : "IDLE")
                            statRow(title: "Bulk OTA Throttling", value: qos.bulkThrottleActive ? "ACTIVE (<=10%)" : "NOMINAL")
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)
                }
                .padding()
            }
            .navigationTitle("Mesh Airtime & QoS HUD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private func queueRow(title: String, count: Int32, color: Color) -> some View {
        HStack {
            Circle()
                .fill(color)
                .frame(width: 6, height: 6)
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white)
            Spacer()
            Text("\(count) pkts")
                .font(.system(size: 11, weight: .black, design: .monospaced))
                .foregroundColor(color)
        }
    }

    private func statRow(title: String, value: String, isWarning: Bool = false) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.system(size: 11, weight: .black, design: .monospaced))
                .foregroundColor(isWarning ? .red : .white)
        }
    }
}

// --- Tactical Transport Handover & Multi-Path Failover HUD Sheet (Sprint 25 / ICA-2503) ---

struct TacticalTransportHandoverSheetView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    let failover = vm.failoverSnapshot ?? FailoverMetrics(
                        currentMode: .restrictedOffline,
                        meshPeerCount: 0,
                        averageEtx: 1.0,
                        averageDeliveryRatio: 1.0,
                        lastBleActivityMs: 0,
                        isCloudConnected: false,
                        timeInCurrentModeMs: 0,
                        totalHandovers: 0,
                        hedgedPacketsCount: 0,
                        isBleDegraded: false
                    )

                    // 1. Current Active Path Banner
                    HStack(spacing: 12) {
                        Image(systemName: modeIcon(failover.currentMode))
                            .font(.system(size: 26, weight: .bold))
                            .foregroundColor(modeColor(failover.currentMode))

                        VStack(alignment: .leading, spacing: 3) {
                            Text(modeTitle(failover.currentMode))
                                .font(.system(size: 15, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                            Text(modeDescription(failover.currentMode))
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }
                    .padding()
                    .background(modeColor(failover.currentMode).opacity(0.12))
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(modeColor(failover.currentMode).opacity(0.35), lineWidth: 1.5))

                    // 2. Link Quality Warning Banner
                    if failover.isBleDegraded {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.orange)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("BLE RF DEGRADATION DETECTED")
                                    .font(.system(size: 11, weight: .black, design: .monospaced))
                                    .foregroundColor(.orange)
                                Text("Dual-stream packet hedging active to protect voice continuity")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.white.opacity(0.85))
                            }
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.orange.opacity(0.18))
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.orange.opacity(0.4), lineWidth: 1))
                    }

                    // 3. Telemetry & Link Metrics
                    VStack(alignment: .leading, spacing: 10) {
                        Text("TELEMETRY & LINK METRICS")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        VStack(spacing: 6) {
                            metricRow(title: "BLE Mesh Peers in Range", value: "\(failover.meshPeerCount)")
                            metricRow(
                                title: "Average Expected Transmissions (ETX)",
                                value: String(format: "%.2f", failover.averageEtx),
                                isWarning: failover.isBleDegraded
                            )
                            metricRow(
                                title: "Packet Delivery Ratio (PDR)",
                                value: "\(Int(failover.averageDeliveryRatio * 100))%"
                            )
                            metricRow(
                                title: "Cloud Relay WebSocket Status",
                                value: failover.isCloudConnected ? "CONNECTED" : "DISCONNECTED",
                                isWarning: !failover.isCloudConnected
                            )
                            metricRow(
                                title: "Total Handover Transitions",
                                value: "\(failover.totalHandovers)"
                            )
                            metricRow(
                                title: "Dual-Stream Hedged Frames",
                                value: "\(failover.hedgedPacketsCount)"
                            )
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)

                    // 4. Manual Routing Policy Override
                    VStack(alignment: .leading, spacing: 10) {
                        Text("MANUAL ROUTING OVERRIDE")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)

                        HStack(spacing: 8) {
                            overrideButton(
                                title: "BLE DIRECT",
                                isSelected: failover.currentMode == .bleDirect,
                                color: .green
                            ) {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                vm.setFailoverModeOverride(.bleDirect)
                            }

                            overrideButton(
                                title: "CLOUD ONLY",
                                isSelected: failover.currentMode == .cloudRelayOnly,
                                color: .purple
                            ) {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                vm.setFailoverModeOverride(.cloudRelayOnly)
                            }

                            overrideButton(
                                title: "AUTO (ARBITER)",
                                isSelected: false,
                                color: .blue
                            ) {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                vm.setFailoverModeOverride(nil)
                            }
                        }
                    }
                    .padding()
                    .background(Color.white.opacity(0.04))
                    .cornerRadius(10)
                }
                .padding()
            }
            .navigationTitle("Transport Handover HUD")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private func modeIcon(_ mode: FailoverMode) -> String {
        switch mode {
        case .bleDirect: return "antenna.radiowaves.left.and.right"
        case .multiPathHedged: return "arrow.triangle.branch"
        case .cloudRelayOnly: return "cloud.fill"
        case .restrictedOffline: return "bolt.slash.fill"
        default: return "network"
        }
    }

    private func modeColor(_ mode: FailoverMode) -> Color {
        switch mode {
        case .bleDirect: return .green
        case .multiPathHedged: return .orange
        case .cloudRelayOnly: return .purple
        case .restrictedOffline: return .gray
        default: return .cyan
        }
    }

    private func modeTitle(_ mode: FailoverMode) -> String {
        switch mode {
        case .bleDirect: return "BLE DIRECT (OFFLINE)"
        case .multiPathHedged: return "MULTI-PATH HEDGED"
        case .cloudRelayOnly: return "CLOUD RELAY (SFU)"
        case .restrictedOffline: return "RESTRICTED OFFLINE"
        default: return "UNKNOWN"
        }
    }

    private func modeDescription(_ mode: FailoverMode) -> String {
        switch mode {
        case .bleDirect: return "Zero cellular usage • 100% peer-to-peer BLE mesh."
        case .multiPathHedged: return "Degraded RF link • Hedging frames across BLE & Cloud Relay."
        case .cloudRelayOnly: return "Local mesh unreachable • Cellular SFU websocket fallback."
        case .restrictedOffline: return "No active mesh or cloud paths available."
        default: return "Arbitrating..."
        }
    }

    private func metricRow(title: String, value: String, isWarning: Bool = false) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.system(size: 11, weight: .black, design: .monospaced))
                .foregroundColor(isWarning ? .orange : .white)
        }
    }

    private func overrideButton(title: String, isSelected: Bool, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundColor(isSelected ? .black : color)
                .frame(maxWidth: .infinity)
                .frame(height: 34)
                .background(isSelected ? color : color.opacity(0.15))
                .cornerRadius(6)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(color.opacity(0.5), lineWidth: 1))
        }
    }
}
