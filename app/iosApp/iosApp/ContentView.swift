import SwiftUI
import SharedLogic
import CoreImage.CIFilterBuiltins

struct ContentView: View {
    @StateObject private var vm = IntercomViewModel()
    @State private var quickTextDraft: String = ""

    var body: some View {
        ZStack {
            Color(UIColor.systemBackground).ignoresSafeArea()

            VStack(spacing: 12) {
                // Emergency Priority Banner
                if vm.isEmergencyActive {
                    EmergencySosBanner(
                        blackboxCount: vm.blackboxBufferedCount,
                        onCancelSos: { vm.clearBlackboxCache() }
                    )
                }

                // Top Action Bar
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(vm.groupName)
                            .font(.system(size: 20, weight: .bold))
                            .lineLimit(1)
                        HStack(spacing: 6) {
                            Circle()
                                .fill(vm.isConnected ? Color.green : Color.orange)
                                .frame(width: 8, height: 8)
                            Text(vm.isConnected ? "MESH ONLINE (\(vm.peers.count))" : "SCANNING MESH")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    // Cockpit Action Buttons
                    HStack(spacing: 8) {
                        // Text Messages Drawer Button with Unread Badge
                        Button(action: { vm.showMessageDrawer = true }) {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: "bubble.left.and.exclamationmark.bubble.right.fill")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(.primary)
                                    .padding(8)
                                    .background(Color(UIColor.secondarySystemBackground))
                                    .clipShape(Circle())

                                if vm.unreadCount > 0 {
                                    Text("\(vm.unreadCount)")
                                        .font(.system(size: 9, weight: .black))
                                        .foregroundColor(.white)
                                        .padding(4)
                                        .background(Color.red)
                                        .clipShape(Circle())
                                        .offset(x: 4, y: -4)
                                }
                            }
                        }

                        // VOX Toggle
                        Button(action: { vm.toggleVox() }) {
                            Image(systemName: vm.voxEnabled ? "waveform.badge.mic" : "mic.slash")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(vm.voxEnabled ? .green : .secondary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }

                        // QR Share
                        Button(action: { vm.showQrShare = true }) {
                            Image(systemName: "qrcode")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }

                        // QR Scanner
                        Button(action: { vm.showQrScanner = true }) {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }

                        // Emergency SOS Button
                        Button(action: {
                            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                            vm.triggerEmergencySos()
                        }) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                                .padding(8)
                                .background(Color.red)
                                .clipShape(Circle())
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 4)

                // Sub-Channel Dial Selector
                ChannelDialView(
                    activeChannelId: vm.activeChannelId,
                    monitoredChannelIds: vm.monitoredChannelIds,
                    isScanMode: vm.isScanMode,
                    onSelectChannel: { id in vm.selectChannel(id: id) },
                    onToggleDualWatch: { id in vm.toggleDualWatch(channelId: id) },
                    onToggleScan: { vm.toggleScanMode() }
                )
                .padding(.horizontal)

                // Convoy Radar & GPS Bearings Strip
                if let convoy = vm.convoySnapshot, !convoy.riders.isEmpty {
                    ConvoyRadarStripView(convoySnapshot: convoy)
                        .padding(.horizontal)
                }

                // Canned Hazard Quick Bar
                QuickHazardBar(onSelectCanned: { code in
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    vm.sendCannedMessage(code)
                })
                .padding(.horizontal)

                // Audio Status & VU Meters
                VStack(spacing: 6) {
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: "headphones")
                                .font(.system(size: 11))
                            Text(vm.audioRoute)
                                .font(.system(size: 10, weight: .medium))
                        }
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(10)

                        Spacer()

                        // Adaptive Bitrate Profile Chip
                        Text("PROFILE: \(vm.currentAudioProfile.name)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.blue)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.blue.opacity(0.12))
                            .cornerRadius(8)
                    }

                    AudioMeterView(label: "Mic", level: vm.inputLevel, activeColor: .blue)
                    AudioMeterView(label: "Out", level: vm.outputLevel, activeColor: .green)
                }
                .padding(.horizontal)

                Spacer(minLength: 4)

                // Central Push-To-Talk Button
                PttButtonView(
                    isTransmitting: vm.isTransmitting,
                    onPress: { vm.startPtt() },
                    onRelease: { vm.stopPtt() }
                )

                Spacer(minLength: 4)

                // Connected Peers Section
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Active Mesh Riders (\(vm.peers.count))")
                            .font(.system(size: 13, weight: .bold))
                        Spacer()
                        if vm.isKeepAliveActive {
                            HStack(spacing: 4) {
                                Circle().fill(Color.green).frame(width: 6, height: 6)
                                Text("SCREEN-OFF SAFE")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.green)
                            }
                        }
                    }
                    .padding(.horizontal)

                    if vm.peers.isEmpty {
                        HStack {
                            Spacer()
                            Text("No nearby riders detected. Share QR or scan invite.")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                                .padding(.vertical, 12)
                            Spacer()
                        }
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(10)
                        .padding(.horizontal)
                    } else {
                        ScrollView {
                            VStack(spacing: 6) {
                                ForEach(vm.peers, id: \.id) { peer in
                                    PeerRowView(peer: peer)
                                }
                            }
                            .padding(.horizontal)
                        }
                        .frame(maxHeight: 120)
                    }
                }
                .padding(.bottom, 6)
            }
        }
        .sheet(isPresented: $vm.showQrScanner) {
            QrScannerView { scannedCode in
                vm.joinGroup(inviteCode: scannedCode)
            }
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
        .alert("Intercom Alert", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { vm.errorMessage = nil }
        } message: {
            Text(vm.errorMessage ?? "")
        }
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

// --- Convoy Radar & GPS Bearing Strip ---

struct ConvoyRadarStripView: View {
    let convoySnapshot: ConvoyTelemetrySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Convoy Radar (\(convoySnapshot.riders.count))")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.secondary)
                Spacer()
                if let closest = convoySnapshot.closestRider {
                    Text("Closest: \(closest.peerName.ifBlank { closest.peerId }) • \(Int(closest.distanceMeters))m")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(.blue)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(convoySnapshot.riders, id: \.peerId) { rider in
                        HStack(spacing: 6) {
                            // Bearing direction arrow
                            Image(systemName: bearingArrow(for: rider.relativeBearingDegrees))
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.blue)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(rider.peerName.ifBlank { rider.peerId })
                                    .font(.system(size: 11, weight: .semibold))
                                    .lineLimit(1)
                                Text("\(Int(rider.distanceMeters))m • \(Int(rider.relativeBearingDegrees))°")
                                    .font(.system(size: 9))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(8)
                    }
                }
            }
        }
    }

    private func bearingArrow(for bearing: Double) -> String {
        switch bearing {
        case -22.5...22.5: return "arrow.up"
        case 22.5...67.5: return "arrow.up.right"
        case 67.5...112.5: return "arrow.right"
        case 112.5...157.5: return "arrow.down.right"
        case -67.5 ... -22.5: return "arrow.up.left"
        case -112.5 ... -67.5: return "arrow.left"
        case -157.5 ... -112.5: return "arrow.down.left"
        default: return "arrow.down"
        }
    }
}

// --- Quick Canned Hazard Bar ---

struct QuickHazardBar: View {
    let onSelectCanned: (CannedMessageCode) -> Void

    private let hazards: [(code: CannedMessageCode, icon: String, label: String, color: Color)] = [
        (.fuelLow, "fuelpump.fill", "Fuel Low", .orange),
        (.hazardRoad, "exclamationmark.triangle.fill", "Road Hazard", .yellow),
        (.stopImmediate, "hand.raised.fill", "Stop", .red),
        (.mechanicalIssue, "wrench.and.screwdriver.fill", "Bike Issue", .orange),
        (.turnAround, "arrow.uturn.left", "Turn Around", .blue),
        (.regroup, "person.3.fill", "Regroup", .green)
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(hazards, id: \.label) { h in
                    Button(action: { onSelectCanned(h.code) }) {
                        HStack(spacing: 4) {
                            Image(systemName: h.icon)
                                .font(.system(size: 10))
                            Text(h.label)
                                .font(.system(size: 10, weight: .semibold))
                        }
                        .foregroundColor(h.color)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(h.color.opacity(0.12))
                        .cornerRadius(8)
                    }
                }
            }
        }
    }
}

// --- Audio Level Meter ---

struct AudioMeterView: View {
    let label: String
    let level: Float
    let activeColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(Int(level * 100))%")
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .foregroundColor(.secondary)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color(UIColor.secondarySystemBackground))
                        .frame(height: 5)

                    RoundedRectangle(cornerRadius: 3)
                        .fill(activeColor)
                        .frame(width: max(0, min(geometry.size.width * CGFloat(level), geometry.size.width)), height: 5)
                        .animation(.linear(duration: 0.1), value: level)
                }
            }
            .frame(height: 5)
        }
    }
}

// --- Push-To-Talk Button ---

struct PttButtonView: View {
    let isTransmitting: Bool
    let onPress: () -> Void
    let onRelease: () -> Void

    var body: some View {
        ZStack {
            Circle()
                .fill(isTransmitting ? Color.red.opacity(0.25) : Color.blue.opacity(0.12))
                .frame(width: 170, height: 170)
                .scaleEffect(isTransmitting ? 1.15 : 1.0)
                .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: isTransmitting)

            Circle()
                .fill(
                    LinearGradient(
                        gradient: Gradient(colors: isTransmitting ? [Color(red: 0.95, green: 0.25, blue: 0.25), Color(red: 0.8, green: 0.1, blue: 0.1)] : [Color(red: 0.15, green: 0.5, blue: 0.95), Color(red: 0.1, green: 0.35, blue: 0.85)]),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .frame(width: 135, height: 135)
                .shadow(color: isTransmitting ? Color.red.opacity(0.4) : Color.blue.opacity(0.3), radius: isTransmitting ? 16 : 8, y: 4)

            VStack(spacing: 4) {
                Image(systemName: isTransmitting ? "waveform" : "mic.fill")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)

                Text(isTransmitting ? "TRANSMITTING" : "PUSH TO TALK")
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundColor(.white)
                    .tracking(1.0)
            }
        }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isTransmitting {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onPress()
                    }
                }
                .onEnded { _ in
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onRelease()
                }
        )
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