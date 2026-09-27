import SwiftUI
import SharedLogic
import CoreImage.CIFilterBuiltins

struct ContentView: View {
    @StateObject private var vm = IntercomViewModel()

    var body: some View {
        ZStack {
            Color(UIColor.systemBackground).ignoresSafeArea()

            VStack(spacing: 16) {
                // Top Action Bar
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(vm.groupName)
                            .font(.system(size: 22, weight: .bold))
                            .lineLimit(1)
                        HStack(spacing: 6) {
                            Circle()
                                .fill(vm.isConnected ? Color.green : Color.orange)
                                .frame(width: 8, height: 8)
                            Text(vm.isConnected ? "GATT MESH ONLINE" : "SCANNING MESH")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.secondary)
                        }
                    }

                    Spacer()

                    // Quick Actions (QR Scanner, QR Share, VOX)
                    HStack(spacing: 8) {
                        Button(action: { vm.toggleVox() }) {
                            Image(systemName: vm.voxEnabled ? "waveform.badge.mic" : "mic.slash")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(vm.voxEnabled ? .green : .secondary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }

                        Button(action: { vm.showQrShare = true }) {
                            Image(systemName: "qrcode")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }

                        Button(action: { vm.showQrScanner = true }) {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                                .padding(8)
                                .background(Color(UIColor.secondarySystemBackground))
                                .clipShape(Circle())
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top, 4)

                // Audio Route Chip
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "headphones")
                            .font(.system(size: 12))
                        Text(vm.audioRoute)
                            .font(.system(size: 11, weight: .medium))
                    }
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(UIColor.secondarySystemBackground))
                    .cornerRadius(12)

                    Spacer()

                    if vm.voxEnabled {
                        Text("VOX ACTIVE (-40 dB)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.green)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.green.opacity(0.12))
                            .cornerRadius(8)
                    }
                }
                .padding(.horizontal)

                // Audio level meters
                VStack(spacing: 10) {
                    AudioMeterView(label: "Microphone In", level: vm.inputLevel, activeColor: .blue)
                    AudioMeterView(label: "Speaker Out", level: vm.outputLevel, activeColor: .green)
                }
                .padding(.horizontal)

                Spacer()

                // Push-To-Talk Button
                PttButtonView(
                    isTransmitting: vm.isTransmitting,
                    onPress: { vm.startPtt() },
                    onRelease: { vm.stopPtt() }
                )

                Spacer()

                // Peers List
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("Mesh Peers (\(vm.peers.count))")
                            .font(.system(size: 14, weight: .bold))
                        Spacer()
                        Text("24 kHz Opus Low-Latency")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal)

                    if vm.peers.isEmpty {
                        HStack {
                            Spacer()
                            Text("No nearby riders detected. Share QR or scan invite.")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                                .padding(.vertical, 20)
                            Spacer()
                        }
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(10)
                        .padding(.horizontal)
                    } else {
                        ScrollView {
                            VStack(spacing: 8) {
                                ForEach(vm.peers, id: \.id) { peer in
                                    PeerRowView(peer: peer)
                                }
                            }
                            .padding(.horizontal)
                        }
                        .frame(maxHeight: 160)
                    }
                }
                .padding(.bottom, 8)
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

struct AudioMeterView: View {
    let label: String
    let level: Float
    let activeColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(Int(level * 100))%")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundColor(.secondary)
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color(UIColor.secondarySystemBackground))
                        .frame(height: 6)

                    RoundedRectangle(cornerRadius: 3)
                        .fill(activeColor)
                        .frame(width: max(0, min(geometry.size.width * CGFloat(level), geometry.size.width)), height: 6)
                        .animation(.linear(duration: 0.1), value: level)
                }
            }
            .frame(height: 6)
        }
    }
}

struct PttButtonView: View {
    let isTransmitting: Bool
    let onPress: () -> Void
    let onRelease: () -> Void

    var body: some View {
        ZStack {
            Circle()
                .fill(isTransmitting ? Color.red.opacity(0.25) : Color.blue.opacity(0.12))
                .frame(width: 190, height: 190)
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
                .frame(width: 150, height: 150)
                .shadow(color: isTransmitting ? Color.red.opacity(0.4) : Color.blue.opacity(0.3), radius: isTransmitting ? 16 : 8, y: 4)

            VStack(spacing: 4) {
                Image(systemName: isTransmitting ? "waveform" : "mic.fill")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)

                Text(isTransmitting ? "TRANSMITTING" : "PUSH TO TALK")
                    .font(.system(size: 13, weight: .heavy))
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

struct PeerRowView: View {
    let peer: Peer

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(peer.name)
                    .font(.system(size: 14, weight: .semibold))
                Text("RSSI: \(peer.rssi) dBm • Profile: \(peer.audioProfile.name)")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Text(peer.isConnected ? "ONLINE" : "OFFLINE")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(peer.isConnected ? .green : .secondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(peer.isConnected ? Color.green.opacity(0.12) : Color.secondary.opacity(0.1))
                .cornerRadius(8)
        }
        .padding(12)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(10)
    }
}

struct QrShareView: View {
    let groupName: String
    let inviteCode: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
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