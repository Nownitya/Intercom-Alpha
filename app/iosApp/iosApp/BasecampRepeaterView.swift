import SwiftUI
import SharedLogic

/// Native iOS 100% SwiftUI Basecamp Stationary Repeater & StandBy Cockpit View.
///
/// Complies with Architectural Rule 1 (Zero Compose Multiplatform imports).
/// Designed for dedicated off-grid stationary relay installations (mountain ridges, basecamps,
/// or parked convoy vehicles):
/// - OLED #000000 pure black power-saving layout for prolonged battery/solar endurance.
/// - Full-screen low-brightness Night Standby lock mode.
/// - Responsive landscape adaptation for iOS 17+ StandBy horizontal bedside/tent docks.
/// - Live RF packet telemetry, duty-cycle mode switcher, and remote diagnostic ping triggers.
struct BasecampRepeaterView: View {
    @ObservedObject var vm: IntercomViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    @State private var isNightStandbyActive: Bool = false
    @State private var pulseAnimation: Bool = false

    var body: some View {
        ZStack {
            // Pure OLED Black Background for power conservation
            Color.black.ignoresSafeArea()

            if isNightStandbyActive {
                // Ultra-dim Night Standby OLED Mode
                NightStandbyOverlayView(
                    vm: vm,
                    onWake: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        isNightStandbyActive = false
                    }
                )
            } else {
                if verticalSizeClass == .compact {
                    // Landscape Cockpit (iOS 17+ StandBy / Tent mount)
                    LandscapeStandbyCockpitView(
                        vm: vm,
                        pulseAnimation: pulseAnimation,
                        onEnterNightStandby: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            isNightStandbyActive = true
                        },
                        onDismiss: { dismiss() }
                    )
                } else {
                    // Portrait Cockpit
                    PortraitRepeaterCockpitView(
                        vm: vm,
                        pulseAnimation: pulseAnimation,
                        onEnterNightStandby: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            isNightStandbyActive = true
                        },
                        onDismiss: { dismiss() }
                    )
                }
            }
        }
        .onAppear {
            pulseAnimation = true
        }
    }
}

// MARK: - Portrait Cockpit Layout

private struct PortraitRepeaterCockpitView: View {
    @ObservedObject var vm: IntercomViewModel
    let pulseAnimation: Bool
    let onEnterNightStandby: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(vm.isRepeaterModeActive ? .green : .secondary)
                        Text("BASECAMP REPEATER")
                            .font(.system(size: 14, weight: .heavy, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    Text(vm.isRepeaterModeActive ? "STATIONARY PACKET RELAY ACTIVE" : "RELAY STANDBY (LOCAL MIC ON)")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundColor(vm.isRepeaterModeActive ? .green : .secondary)
                }

                Spacer()

                Button(action: onEnterNightStandby) {
                    HStack(spacing: 4) {
                        Image(systemName: "moon.stars.fill")
                            .font(.system(size: 10))
                        Text("NIGHT")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .foregroundColor(.amber)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(Color.amber.opacity(0.18))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.amber.opacity(0.4), lineWidth: 1))
                }

                Button(action: onDismiss) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 10)

            Divider().background(Color.white.opacity(0.15))

            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 14) {
                    // 1. Master Relay Actuator Card
                    RelayMasterCard(vm: vm, pulseAnimation: pulseAnimation)

                    // 2. Power & Duty Cycle Strip
                    PowerAndDutyCycleCard(vm: vm)

                    // 3. RF Telemetry 4-Stat Grid
                    RfTelemetryGridCard(telemetry: vm.repeaterTelemetry)

                    // 4. Remote Diagnostic Ping Card
                    DiagnosticPingCard(
                        lastPingLatencyMs: vm.lastPingLatencyMs,
                        onPing: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            vm.triggerDiagnosticPing()
                        },
                        onBeacon: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            vm.emitRepeaterBeacon()
                        }
                    )

                    // 5. Active Heard Neighbors Card
                    ActiveNeighborsRosterCard(neighbors: vm.repeaterTelemetry?.activeNeighbors ?? [])

                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
        }
    }
}

// MARK: - Landscape Cockpit Layout (iOS 17+ StandBy Dock)

private struct LandscapeStandbyCockpitView: View {
    @ObservedObject var vm: IntercomViewModel
    let pulseAnimation: Bool
    let onEnterNightStandby: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            // Left Pane: Controls & Status
            VStack(spacing: 10) {
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(vm.isRepeaterModeActive ? .green : .secondary)
                        Text("STANDBY RELAY")
                            .font(.system(size: 12, weight: .heavy, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    Spacer()
                    Button(action: onEnterNightStandby) {
                        Image(systemName: "moon.stars.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.amber)
                            .padding(6)
                            .background(Color.amber.opacity(0.2))
                            .clipShape(Circle())
                    }
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }

                RelayMasterCard(vm: vm, pulseAnimation: pulseAnimation, compact: true)

                PowerAndDutyCycleCard(vm: vm, compact: true)

                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            Divider().background(Color.white.opacity(0.15))

            // Right Pane: Telemetry & Neighbors
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 10) {
                    RfTelemetryGridCard(telemetry: vm.repeaterTelemetry)

                    DiagnosticPingCard(
                        lastPingLatencyMs: vm.lastPingLatencyMs,
                        onPing: {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            vm.triggerDiagnosticPing()
                        },
                        onBeacon: {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            vm.emitRepeaterBeacon()
                        }
                    )

                    ActiveNeighborsRosterCard(neighbors: vm.repeaterTelemetry?.activeNeighbors ?? [])
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// MARK: - Relay Master Actuator Card

private struct RelayMasterCard: View {
    @ObservedObject var vm: IntercomViewModel
    let pulseAnimation: Bool
    var compact: Bool = false

    var body: some View {
        VStack(spacing: compact ? 8 : 12) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("REPEATER FORWARDING ENGINE")
                        .font(.system(size: 10, weight: .black, design: .monospaced))
                        .foregroundColor(.secondary)

                    Text(vm.isRepeaterModeActive ? "RELAY TRANSMITTING" : "REPEATER IDLE")
                        .font(.system(size: compact ? 15 : 18, weight: .heavy, design: .monospaced))
                        .foregroundColor(vm.isRepeaterModeActive ? .green : .white)
                }

                Spacer()

                // Master Toggle Switch
                Toggle("", isOn: Binding(
                    get: { vm.isRepeaterModeActive },
                    set: { newVal in
                        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
                        vm.setRepeaterActive(newVal)
                    }
                ))
                .labelsHidden()
                .toggleStyle(SwitchToggleStyle(tint: .green))
            }

            if vm.isRepeaterModeActive {
                // Animated RF Wave & Audio Bypass Banner
                HStack(spacing: 8) {
                    HStack(spacing: 3) {
                        ForEach(0..<5) { index in
                            RoundedRectangle(cornerRadius: 1.5)
                                .fill(Color.green)
                                .frame(width: 3, height: pulseAnimation ? CGFloat([14, 22, 10, 26, 16][index]) : 8)
                                .animation(
                                    .easeInOut(duration: 0.6)
                                    .repeatForever(autoreverses: true)
                                    .delay(Double(index) * 0.12),
                                    value: pulseAnimation
                                )
                        }
                    }
                    .frame(width: 30)

                    VStack(alignment: .leading, spacing: 1) {
                        Text("AUDIO ENGINE SUSPENDED • 100% AIRTIME TO RELAY")
                            .font(.system(size: 8, weight: .black, design: .monospaced))
                            .foregroundColor(.green)
                        Text("Microphone & speaker pipelines disabled to extend battery.")
                            .font(.system(size: 8, weight: .medium))
                            .foregroundColor(.white.opacity(0.75))
                    }
                    Spacer()
                }
                .padding(compact ? 6 : 8)
                .background(Color.green.opacity(0.12))
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.green.opacity(0.3), lineWidth: 1))
            }
        }
        .padding(compact ? 10 : 14)
        .background(Color(red: 0.07, green: 0.08, blue: 0.1))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(vm.isRepeaterModeActive ? Color.green.opacity(0.4) : Color.white.opacity(0.1), lineWidth: 1)
        )
    }
}

// MARK: - Power & Duty Cycle Card

private struct PowerAndDutyCycleCard: View {
    @ObservedObject var vm: IntercomViewModel
    var compact: Bool = false

    var body: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                // Battery Gauge
                HStack(spacing: 5) {
                    let batt = vm.repeaterTelemetry?.batteryPercent ?? 100
                    Image(systemName: batt > 60 ? "battery.100" : (batt > 20 ? "battery.50" : "battery.25"))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(batt > 20 ? .green : .red)
                    Text("\(batt)%")
                        .font(.system(size: 13, weight: .heavy, design: .monospaced))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(Color.white.opacity(0.08))
                .cornerRadius(6)

                // Solar Indicator
                HStack(spacing: 4) {
                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 11))
                    Text("SOLAR PV")
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                }
                .foregroundColor(.amber)
                .padding(.horizontal, 7)
                .padding(.vertical, 5)
                .background(Color.amber.opacity(0.15))
                .cornerRadius(6)

                Spacer()

                // Duty Cycle Indicator
                Text(dutyCycleTitle(vm.repeaterDutyCycle))
                    .font(.system(size: 9, weight: .black, design: .monospaced))
                    .foregroundColor(.cyan)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(Color.cyan.opacity(0.15))
                    .cornerRadius(6)
            }

            // Duty Cycle Selector
            HStack(spacing: 6) {
                ForEach([RepeaterDutyCycleMode.continuous, .powerSaver, .extremeSurvival], id: \.self) { mode in
                    let isSelected = vm.repeaterDutyCycle == mode
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        vm.setRepeaterDutyCycle(mode)
                    }) {
                        VStack(spacing: 2) {
                            Text(dutyCycleShort(mode))
                                .font(.system(size: 9, weight: isSelected ? .black : .bold, design: .monospaced))
                                .foregroundColor(isSelected ? .white : .secondary)
                            Text(dutyCycleSub(mode))
                                .font(.system(size: 7, weight: .medium))
                                .foregroundColor(isSelected ? .white.opacity(0.8) : .secondary.opacity(0.7))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 5)
                        .background(isSelected ? Color.blue : Color.white.opacity(0.06))
                        .cornerRadius(6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(isSelected ? Color.cyan : Color.clear, lineWidth: 1)
                        )
                    }
                }
            }
        }
        .padding(compact ? 8 : 12)
        .background(Color(red: 0.05, green: 0.06, blue: 0.08))
        .cornerRadius(10)
    }

    private func dutyCycleTitle(_ mode: RepeaterDutyCycleMode) -> String {
        switch mode {
        case .continuous: return "100% READY"
        case .powerSaver: return "50% DUTY"
        case .extremeSurvival: return "SURVIVAL"
        default: return "READY"
        }
    }

    private func dutyCycleShort(_ mode: RepeaterDutyCycleMode) -> String {
        switch mode {
        case .continuous: return "CONTINUOUS"
        case .powerSaver: return "POWER SAVER"
        case .extremeSurvival: return "SURVIVAL"
        default: return "AUTO"
        }
    }

    private func dutyCycleSub(_ mode: RepeaterDutyCycleMode) -> String {
        switch mode {
        case .continuous: return "<5ms Relay"
        case .powerSaver: return "Balanced"
        case .extremeSurvival: return "Solar Reserve"
        default: return ""
        }
    }
}

// MARK: - RF Telemetry 4-Stat Grid Card

private struct RfTelemetryGridCard: View {
    let telemetry: RepeaterTelemetry?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("RF RELAY TELEMETRY")
                .font(.system(size: 10, weight: .black, design: .monospaced))
                .foregroundColor(.secondary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                StatCard(
                    title: "FORWARDED",
                    value: "\(telemetry?.packetsForwarded ?? 0)",
                    subtitle: "Sub-15ms hops",
                    accent: .green
                )
                StatCard(
                    title: "DUPLICATES DROPPED",
                    value: "\(telemetry?.packetsDroppedDuplicate ?? 0)",
                    subtitle: "Loop suppression",
                    accent: .orange
                )
                StatCard(
                    title: "LAST HOP LATENCY",
                    value: "\(telemetry?.lastForwardLatencyMs ?? 0) ms",
                    subtitle: "BLE GATT Flooding",
                    accent: .cyan
                )
                StatCard(
                    title: "BEACONS EMITTED",
                    value: "\(telemetry?.beaconEmissionCount ?? 0)",
                    subtitle: "OTA Broadcasts",
                    accent: .blue
                )
            }
        }
        .padding(12)
        .background(Color(red: 0.05, green: 0.06, blue: 0.08))
        .cornerRadius(10)
    }
}

private struct StatCard: View {
    let title: String
    let value: String
    let subtitle: String
    let accent: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.system(size: 8, weight: .black, design: .monospaced))
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(size: 15, weight: .heavy, design: .monospaced))
                .foregroundColor(accent)
            Text(subtitle)
                .font(.system(size: 7, weight: .medium))
                .foregroundColor(.white.opacity(0.5))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Color.white.opacity(0.04))
        .cornerRadius(8)
    }
}

// MARK: - Remote Diagnostic Ping Card

private struct DiagnosticPingCard: View {
    let lastPingLatencyMs: Int64?
    let onPing: () -> Void
    let onBeacon: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("DIAGNOSTIC LINK VERIFICATION")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.secondary)
                Spacer()
                if let latency = lastPingLatencyMs {
                    Text("RTT: \(latency) ms")
                        .font(.system(size: 10, weight: .heavy, design: .monospaced))
                        .foregroundColor(.green)
                }
            }

            HStack(spacing: 8) {
                Button(action: onPing) {
                    HStack(spacing: 5) {
                        Image(systemName: "dot.radiowaves.left.and.right")
                            .font(.system(size: 12))
                        Text("PING RELAY")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .foregroundColor(.white)
                    .background(Color.blue)
                    .cornerRadius(8)
                }

                Button(action: onBeacon) {
                    HStack(spacing: 5) {
                        Image(systemName: "beacon.max.fill")
                            .font(.system(size: 12))
                        Text("EMIT BEACON")
                            .font(.system(size: 10, weight: .black, design: .monospaced))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .foregroundColor(.amber)
                    .background(Color.amber.opacity(0.18))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.amber.opacity(0.4), lineWidth: 1))
                }
            }
        }
        .padding(12)
        .background(Color(red: 0.05, green: 0.06, blue: 0.08))
        .cornerRadius(10)
    }
}

// MARK: - Active Neighbors Roster Card

private struct ActiveNeighborsRosterCard: View {
    let neighbors: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("HEARD MESH NODES (\(neighbors.count))")
                    .font(.system(size: 10, weight: .black, design: .monospaced))
                    .foregroundColor(.secondary)
                Spacer()
                Text("ACTIVE <60s")
                    .font(.system(size: 8, weight: .bold, design: .monospaced))
                    .foregroundColor(.green)
            }

            if neighbors.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "antenna.radiowaves.left.and.right.slash")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                    Text("No mobile rider nodes currently within direct RF range.")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            } else {
                VStack(spacing: 6) {
                    ForEach(neighbors, id: \.self) { node in
                        HStack {
                            Circle().fill(Color.green).frame(width: 6, height: 6)
                            Text(node)
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundColor(.white)
                            Spacer()
                            Text("FORWARDING")
                                .font(.system(size: 8, weight: .black, design: .monospaced))
                                .foregroundColor(.cyan)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.cyan.opacity(0.15))
                                .cornerRadius(4)
                        }
                        .padding(6)
                        .background(Color.white.opacity(0.04))
                        .cornerRadius(6)
                    }
                }
            }
        }
        .padding(12)
        .background(Color(red: 0.05, green: 0.06, blue: 0.08))
        .cornerRadius(10)
    }
}

// MARK: - Ultra-Dim Night Standby Overlay

private struct NightStandbyOverlayView: View {
    @ObservedObject var vm: IntercomViewModel
    let onWake: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                // Minimal Clock
                Text(Date(), style: .time)
                    .font(.system(size: 54, weight: .thin, design: .monospaced))
                    .foregroundColor(Color.amber.opacity(0.75))

                // Dim Status
                VStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(vm.isRepeaterModeActive ? Color.green.opacity(0.8) : Color.orange.opacity(0.8))
                            .frame(width: 8, height: 8)
                        Text(vm.isRepeaterModeActive ? "BASECAMP RELAY ACTIVE" : "RELAY PAUSED")
                            .font(.system(size: 12, weight: .bold, design: .monospaced))
                            .foregroundColor(.white.opacity(0.6))
                    }

                    Text("BATTERY \(vm.repeaterTelemetry?.batteryPercent ?? 100)% • \(vm.repeaterTelemetry?.activeNeighborCount ?? 0) NODES HEARD")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundColor(.white.opacity(0.4))
                }

                Spacer()

                // Wake Cue
                Text("TAP SCREEN TO WAKE COCKPIT")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.3))
                    .padding(.bottom, 24)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onWake()
        }
    }
}

// MARK: - Color Palette Extensions

private extension Color {
    static let amber = Color(red: 1.0, green: 0.75, blue: 0.1)
}
