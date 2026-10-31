import SwiftUI

public struct CockpitView: View {
    @ObservedObject var vm: IntercomViewModel
    @ObservedObject var prefs = UiPreferencesManager.shared
    @State private var showSettings: Bool = false

    let onOpenRoster: () -> Void
    let onOpenDiagnostics: () -> Void

    public init(
        vm: IntercomViewModel,
        onOpenRoster: @escaping () -> Void = {},
        onOpenDiagnostics: @escaping () -> Void = {}
    ) {
        self.vm = vm
        self.onOpenRoster = onOpenRoster
        self.onOpenDiagnostics = onOpenDiagnostics
    }

    public var body: some View {
        ZStack {
            TacticalTheme.background.ignoresSafeArea()

            VStack(spacing: 12) {
                // Top Avionics Status Cluster
                CockpitHeaderView(
                    groupName: vm.groupName,
                    peerCount: max(vm.peers.count, 1),
                    activeChannelId: vm.activeChannelId,
                    phyMode: vm.failoverSnapshot?.activePhy ?? "2M",
                    batteryPercentage: vm.accessoryState?.batteryLevel?.intValue ?? 85,
                    isCloudConnected: vm.failoverSnapshot?.isCloudConnected ?? false,
                    onOpenRoster: onOpenRoster,
                    onOpenDiagnostics: onOpenDiagnostics,
                    onOpenSettings: { showSettings = true }
                )
                .padding(.horizontal, 16)
                .padding(.top, 4)

                // Emergency SOS Banner / Guarded Latch
                GuardedSosButton(
                    isEmergencyActive: vm.isEmergencyActive,
                    onTriggerEmergency: { vm.triggerEmergencySos() },
                    onCancelEmergency: { vm.clearBlackboxCache() }
                )
                .padding(.horizontal, 16)

                // Live Speaker Hero Card (ADR-0021)
                LiveSpeakerCard(
                    isTransmitting: vm.isTransmitting,
                    remoteSpeakerName: vm.isTransmitting ? nil : (vm.peers.first?.name),
                    activeChannelId: vm.activeChannelId,
                    peerCount: max(vm.peers.count, 1),
                    showDiscreteLed: prefs.showVuMeter,
                    audioLevel: vm.inputLevel
                )
                .padding(.horizontal, 16)

                // Optional Dual VU Acoustic Level Visualizer (if enabled and not in low power mode)
                if prefs.showVuMeter && !prefs.lowPowerMode {
                    DualVuMeterView(
                        localRmsLevel: vm.inputLevel,
                        remoteRmsLevel: vm.isTransmitting ? 0.0 : vm.inputLevel,
                        remoteSpeakerName: vm.isTransmitting ? nil : (vm.peers.first?.name),
                        isVoiceActive: vm.inputLevel > 0.05,
                        voxThreshold: 0.35
                    )
                    .padding(.horizontal, 16)
                }

                // Sub-Channel 1-8 Quick Strip
                ChannelSelectorStrip(
                    activeChannelId: vm.activeChannelId,
                    monitoredChannelIds: vm.monitoredChannelIds,
                    onSelectChannel: { channelId in vm.selectChannel(id: channelId) },
                    onToggleDualWatch: { channelId in vm.toggleDualWatch(channelId: channelId) }
                )
                .padding(.horizontal, 16)

                // Quick Convoy Hazard Buttons (Anti-slop: 100% SF Symbols, zero emoji)
                HStack(spacing: 8) {
                    HazardQuickButton(title: "HAZARD", icon: "exclamationmark.triangle.fill", color: TacticalTheme.signalWarning) {
                        vm.sendCustomText("ROAD HAZARD AHEAD")
                    }
                    HazardQuickButton(title: "FUEL LOW", icon: "fuelpump.fill", color: TacticalTheme.accent) {
                        vm.sendCustomText("NEED FUEL STOP")
                    }
                    HazardQuickButton(title: "STOP NOW", icon: "hand.raised.fill", color: TacticalTheme.signalEmergency) {
                        vm.sendCustomText("CONVOY STOP IMMEDIATE")
                    }
                }
                .padding(.horizontal, 16)

                Spacer()

                // Master PTT Actuator Shelf (Bottom Primary Action Zone - 84pt Height)
                PttActuatorShelf(
                    isTransmitting: vm.isTransmitting,
                    isVoxLocked: vm.isVoxLocked,
                    activeChannelId: vm.activeChannelId,
                    onStartTransmit: { vm.startPtt() },
                    onStopTransmit: { vm.stopPtt() },
                    onToggleVoxLock: { vm.toggleVoxLock() }
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsSheetView()
        }
    }
}

private struct HazardQuickButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: {
            TacticalHaptics.selection()
            action()
        }) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .bold))
                Text(title)
                    .font(.system(size: 10, weight: .black, design: .monospaced))
            }
            .foregroundColor(color)
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .background(TacticalTheme.surfaceCard)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(TacticalTheme.surfaceBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
