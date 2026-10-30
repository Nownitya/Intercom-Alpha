import SwiftUI

public struct ConvoyView: View {
    @ObservedObject var vm: IntercomViewModel
    let onHostQr: () -> Void
    let onJoinQr: () -> Void

    public init(
        vm: IntercomViewModel,
        onHostQr: @escaping () -> Void = {},
        onJoinQr: @escaping () -> Void = {}
    ) {
        self.vm = vm
        self.onHostQr = onHostQr
        self.onJoinQr = onJoinQr
    }

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            VStack(spacing: 14) {
                // Header
                HStack {
                    Text("CONVOY FLEET")
                        .font(.system(size: 14, weight: .black, design: .monospaced))
                        .foregroundColor(TacticalTheme.textPrimary)

                    Spacer()

                    Text("\(vm.peers.count) ONLINE")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(TacticalTheme.signalConnected)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                // Quick Action Bar: Host QR / Join Scan
                HStack(spacing: 10) {
                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onHostQr()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "qrcode")
                                .font(.system(size: 13, weight: .bold))
                            Text("SHARE INVITE")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                        }
                        .foregroundColor(TacticalTheme.accent)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(TacticalTheme.accentMuted)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(TacticalTheme.accent.opacity(0.4), lineWidth: 1))
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        TactileHaptics.shared.channelDetentClick()
                        onJoinQr()
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "qrcode.viewfinder")
                                .font(.system(size: 13, weight: .bold))
                            Text("SCAN QR")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                        }
                        .foregroundColor(TacticalTheme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(TacticalTheme.surfaceCard)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)

                // Roster List
                if vm.peers.isEmpty {
                    VStack(spacing: 8) {
                        Spacer()
                        Image(systemName: "person.2.slash")
                            .font(.system(size: 32))
                            .foregroundColor(TacticalTheme.textMuted)

                        Text("NO PEERS IN DIRECT MESH")
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                            .foregroundColor(TacticalTheme.textMuted)

                        Text("Tap 'Share Invite' or 'Scan QR' to pair riders.")
                            .font(.system(size: 11))
                            .foregroundColor(TacticalTheme.textSecondary)
                        Spacer()
                    }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(vm.peers, id: \.id) { peer in
                                PeerRosterRow(peer: peer)
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
            }
        }
    }
}

private struct PeerRosterRow: View {
    let peer: PeerItem

    var body: some View {
        HStack(spacing: 12) {
            // Signal dot
            Circle()
                .fill(peer.isConnected ? TacticalTheme.signalConnected : TacticalTheme.textMuted)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(peer.name)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(TacticalTheme.textPrimary)

                Text(String(format: "%d dBm • %d%% BAT", peer.rssi, max(peer.batteryLevel, 0)))
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(TacticalTheme.textMuted)
            }

            Spacer()

            // Status chip
            Text(peer.isConnected ? "CONNECTED" : "LOST")
                .font(.system(size: 9, weight: .black, design: .monospaced))
                .foregroundColor(peer.isConnected ? TacticalTheme.signalConnected : TacticalTheme.textMuted)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(TacticalTheme.surfaceBorder.opacity(0.4))
                .clipShape(Capsule())
        }
        .padding(12)
        .tacticalCard()
    }
}

