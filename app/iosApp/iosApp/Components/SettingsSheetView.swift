import SwiftUI

// MARK: - Tactical Settings & Customization Sheet (ADR-0021)
// Allows user to choose their activity profile, theme preset, and toggle HUD components.
public struct SettingsSheetView: View {
    @ObservedObject var prefs = UiPreferencesManager.shared
    @Environment(\.dismiss) private var dismiss

    public init() {}

    public var body: some View {
        NavigationView {
            ZStack {
                TacticalTheme.background.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        // SECTION 1: Activity Profile
                        sectionHeader(title: "ACTIVITY PROFILE", icon: "figure.run")

                        VStack(spacing: 8) {
                            ForEach(ActivityProfile.allCases) { profile in
                                profileRow(profile)
                            }
                        }

                        // SECTION 2: Tactical Color Theme
                        sectionHeader(title: "TACTICAL COLOR THEME", icon: "paintpalette.fill")

                        VStack(spacing: 8) {
                            ForEach(TacticalThemePreset.allCases) { theme in
                                themeRow(theme)
                            }
                        }

                        // SECTION 3: Performance & Battery Saver
                        sectionHeader(title: "BATTERY & PERFORMANCE", icon: "bolt.fill")

                        VStack(spacing: 12) {
                            toggleRow(
                                title: "Low-Power Utilitarian Mode",
                                subtitle: "Disables 60fps canvas animations; uses static cards",
                                isOn: $prefs.lowPowerMode
                            )
                        }
                        .padding(14)
                        .background(TacticalTheme.surfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                                .stroke(TacticalTheme.surfaceBorder, lineWidth: 1)
                        )

                        // SECTION 4: Visible HUD Components
                        sectionHeader(title: "VISIBLE HUD COMPONENTS", icon: "slider.horizontal.3")

                        VStack(spacing: 12) {
                            toggleRow(
                                title: "Audio VU / Level Meter",
                                subtitle: "Discrete 4-segment audio level indicator",
                                isOn: $prefs.showVuMeter
                            )

                            Divider().background(TacticalTheme.surfaceBorder)

                            toggleRow(
                                title: "Proximity Radar Compass",
                                subtitle: "360° relative vector compass & peer list",
                                isOn: $prefs.showRadar
                            )

                            Divider().background(TacticalTheme.surfaceBorder)

                            toggleRow(
                                title: "Alpine Elevation HUD",
                                subtitle: "Trail elevation profile & slope gradient %",
                                isOn: $prefs.showElevation
                            )

                            Divider().background(TacticalTheme.surfaceBorder)

                            toggleRow(
                                title: "Convoy Fleet Roster",
                                subtitle: "Inter-rider distances & convoy lead/sweep tracking",
                                isOn: $prefs.showConvoyMap
                            )

                            Divider().background(TacticalTheme.surfaceBorder)

                            toggleRow(
                                title: "RF Diagnostics & Link Health",
                                subtitle: "PHY rate, packet loss %, and SNR telemetry",
                                isOn: $prefs.showRfDiagnostics
                            )
                        }
                        .padding(14)
                        .background(TacticalTheme.surfaceCard)
                        .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: TacticalTheme.cardRadius, style: .continuous)
                                .stroke(TacticalTheme.surfaceBorder, lineWidth: 1)
                        )
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Tactical Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { dismiss() }) {
                        Text("Done")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(TacticalTheme.accent)
                    }
                }
            }
        }
    }

    // MARK: - Row Helpers
    private func sectionHeader(title: String, icon: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(TacticalTheme.accent)

            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(TacticalTheme.textSecondary)
        }
        .padding(.top, 4)
    }

    private func profileRow(_ profile: ActivityProfile) -> some View {
        let isSelected = prefs.profile == profile

        return Button(action: {
            TacticalHaptics.selection()
            prefs.profile = profile
        }) {
            HStack(spacing: 12) {
                Image(systemName: profile.iconName)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(isSelected ? TacticalTheme.accent : TacticalTheme.textSecondary)
                    .frame(width: 32)

                VStack(alignment: .leading, spacing: 2) {
                    Text(profile.rawValue)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(TacticalTheme.textPrimary)

                    Text(profile.shortDescription)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(TacticalTheme.textSecondary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(TacticalTheme.accent)
                }
            }
            .padding(12)
            .background(isSelected ? TacticalTheme.accentMuted : TacticalTheme.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous)
                    .stroke(isSelected ? TacticalTheme.accent : TacticalTheme.surfaceBorder, lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func themeRow(_ theme: TacticalThemePreset) -> some View {
        let isSelected = prefs.theme == theme

        return Button(action: {
            TacticalHaptics.selection()
            prefs.theme = theme
        }) {
            HStack(spacing: 12) {
                // Color swatch pill
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: theme.previewAccentHex))
                    .frame(width: 24, height: 24)

                Text(theme.rawValue)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(TacticalTheme.textPrimary)

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(TacticalTheme.accent)
                }
            }
            .padding(12)
            .background(isSelected ? TacticalTheme.accentMuted : TacticalTheme.surfaceCard)
            .clipShape(RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: TacticalTheme.chipRadius, style: .continuous)
                    .stroke(isSelected ? TacticalTheme.accent : TacticalTheme.surfaceBorder, lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func toggleRow(title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(TacticalTheme.textPrimary)

                Text(subtitle)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(TacticalTheme.textSecondary)
            }
        }
        .toggleStyle(SwitchToggleStyle(tint: TacticalTheme.accent))
    }
}

// MARK: - Color Hex Initializer
private extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let r, g, b: UInt64
        switch hex.count {
        case 6: // RGB (24-bit)
            (r, g, b) = (int >> 16, int >> 8 & 0xFF, int & 0xFF)
        default:
            (r, g, b) = (245, 158, 11)
        }
        self.init(
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255
        )
    }
}

