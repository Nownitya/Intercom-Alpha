import SwiftUI
import SharedLogic

public struct ContentView: View {
    @StateObject private var vm = IntercomViewModel()

    @State private var selectedTab: TabDestination = .cockpit
    @State private var showQrShare: Bool = false
    @State private var showQrScanner: Bool = false

    public enum TabDestination: Hashable {
        case cockpit
        case radar
        case elevation
        case convoy
    }

    public init() {}

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            TabView(selection: $selectedTab) {
                // Tab 1: Tactical Cockpit HUD
                CockpitView(
                    vm: vm,
                    onOpenRoster: { selectedTab = .convoy },
                    onOpenDiagnostics: {}
                )
                .tabItem {
                    Label("Intercom", systemImage: "antenna.radiowaves.left.and.right")
                }
                .tag(TabDestination.cockpit)

                // Tab 2: Proximity Radar
                RadarView(vm: vm)
                    .tabItem {
                        Label("Radar", systemImage: "scope")
                    }
                    .tag(TabDestination.radar)

                // Tab 3: Alpine Trail Elevation
                ElevationView(vm: vm)
                    .tabItem {
                        Label("Elevation", systemImage: "chart.line.uptrend.xyaxis")
                    }
                    .tag(TabDestination.elevation)

                // Tab 4: Convoy Roster & Pairing
                ConvoyView(
                    vm: vm,
                    onHostQr: { showQrShare = true },
                    onJoinQr: { showQrScanner = true }
                )
                .tabItem {
                    Label("Convoy", systemImage: "person.2.fill")
                }
                .tag(TabDestination.convoy)
            }
            .accentColor(TacticalTheme.accent)

            // Crash Impact Emergency Countdown Overlay (Sprint 21 / ICA-2103)
            if vm.showCrashCountdownCard {
                CrashCountdownBanner(
                    onCancel: { vm.cancelCrashCountdown() },
                    onTriggerNow: { vm.triggerManualCrashSos() }
                )
                .padding(.horizontal, 16)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .sheet(isPresented: $showQrScanner) {
            QrScannerView { scannedCode in
                vm.joinGroup(inviteCode: scannedCode)
                showQrScanner = false
            }
        }
        .sheet(isPresented: $showQrShare) {
            QrShareView(groupName: vm.groupName, inviteCode: vm.inviteCode)
        }
    }
}

private struct CrashCountdownBanner: View {
    let onCancel: () -> Void
    let onTriggerNow: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)

                Text("CRASH IMPACT DETECTED")
                    .font(.system(size: 13, weight: .black, design: .monospaced))
                    .foregroundColor(.white)

                Spacer()
            }

            Text("Broadcasting eCall emergency SOS across mesh in 15 seconds unless cancelled.")
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.9))

            HStack(spacing: 10) {
                Button(action: onTriggerNow) {
                    Text("BROADCAST NOW")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(Color.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.white)
                        .clipShape(Capsule())
                }

                Button(action: onCancel) {
                    Text("I AM OK (CANCEL)")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.4))
                        .clipShape(Capsule())
                }
            }
        }
        .padding(14)
        .background(TacticalTheme.signalEmergency)
        .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
        .shadow(radius: 12)
    }
}
