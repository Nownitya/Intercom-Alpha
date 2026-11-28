import SwiftUI
import Combine

// MARK: - Activity Profiles (ADR-0021)
public enum ActivityProfile: String, CaseIterable, Identifiable, Codable {
    case motorbike = "Motorbike"
    case car = "Car / 4x4"
    case bicycle = "Bicycle"
    case hiking = "Mountain Trails"
    case minimal = "Minimal"

    public var id: String { rawValue }

    public var iconName: String {
        switch self {
        case .motorbike: return "motorcycle"
        case .car: return "car.fill"
        case .bicycle: return "bicycle"
        case .hiking: return "figure.hiking"
        case .minimal: return "antenna.radiowaves.left.and.right"
        }
    }

    public var shortDescription: String {
        switch self {
        case .motorbike: return "84pt PTT • Slope warnings • Tactical Cockpit"
        case .car: return "Large buttons • Convoy spacing • Driving focus"
        case .bicycle: return "Audio-first • Buddy proximity • VOX toggle"
        case .hiking: return "Elevation contour • Compass • Battery saver"
        case .minimal: return "Single screen • Channel & PTT only"
        }
    }
}

// MARK: - Static Theme Presets (ADR-0021)
public enum TacticalThemePreset: String, CaseIterable, Identifiable, Codable {
    case tacticalAmber = "Tactical Amber"
    case alpineHighVis = "Alpine High-Vis"
    case forestEmerald = "Forest Emerald"
    case desertOchre = "Desert Ochre"
    case monochrome = "Monochrome"

    public var id: String { rawValue }

    public var previewAccentHex: String {
        switch self {
        case .tacticalAmber: return "#F59E0B"
        case .alpineHighVis: return "#00E5FF"
        case .forestEmerald: return "#10B981"
        case .desertOchre: return "#FBBF24"
        case .monochrome: return "#FFFFFF"
        }
    }
}

// MARK: - Observable UI Preferences & State Manager
public final class UiPreferencesManager: ObservableObject {
    public static let shared = UiPreferencesManager()

    @Published public var profile: ActivityProfile {
        didSet {
            UserDefaults.standard.set(profile.rawValue, forKey: "ica_activity_profile")
            applyProfileDefaults(profile)
        }
    }

    @Published public var theme: TacticalThemePreset {
        didSet {
            UserDefaults.standard.set(theme.rawValue, forKey: "ica_theme_preset")
        }
    }

    @Published public var lowPowerMode: Bool {
        didSet {
            UserDefaults.standard.set(lowPowerMode, forKey: "ica_low_power_mode")
        }
    }

    @Published public var showVuMeter: Bool {
        didSet {
            UserDefaults.standard.set(showVuMeter, forKey: "ica_show_vu_meter")
        }
    }

    @Published public var showRadar: Bool {
        didSet {
            UserDefaults.standard.set(showRadar, forKey: "ica_show_radar")
        }
    }

    @Published public var showElevation: Bool {
        didSet {
            UserDefaults.standard.set(showElevation, forKey: "ica_show_elevation")
        }
    }

    @Published public var showConvoyMap: Bool {
        didSet {
            UserDefaults.standard.set(showConvoyMap, forKey: "ica_show_convoy_map")
        }
    }

    @Published public var showRfDiagnostics: Bool {
        didSet {
            UserDefaults.standard.set(showRfDiagnostics, forKey: "ica_show_rf_diagnostics")
        }
    }

    public init() {
        let savedProfile = UserDefaults.standard.string(forKey: "ica_activity_profile")
        self.profile = savedProfile.flatMap(ActivityProfile.init(rawValue:)) ?? .motorbike

        let savedTheme = UserDefaults.standard.string(forKey: "ica_theme_preset")
        self.theme = savedTheme.flatMap(TacticalThemePreset.init(rawValue:)) ?? .tacticalAmber

        self.lowPowerMode = UserDefaults.standard.object(forKey: "ica_low_power_mode") as? Bool ?? true
        self.showVuMeter = UserDefaults.standard.object(forKey: "ica_show_vu_meter") as? Bool ?? false
        self.showRadar = UserDefaults.standard.object(forKey: "ica_show_radar") as? Bool ?? true
        self.showElevation = UserDefaults.standard.object(forKey: "ica_show_elevation") as? Bool ?? true
        self.showConvoyMap = UserDefaults.standard.object(forKey: "ica_show_convoy_map") as? Bool ?? true
        self.showRfDiagnostics = UserDefaults.standard.object(forKey: "ica_show_rf_diagnostics") as? Bool ?? false
    }

    public func applyProfileDefaults(_ profile: ActivityProfile) {
        switch profile {
        case .motorbike:
            showRadar = true
            showElevation = true
            showConvoyMap = true
            showVuMeter = false
        case .car:
            showRadar = false
            showElevation = false
            showConvoyMap = true
            showVuMeter = false
        case .bicycle:
            showRadar = true
            showElevation = false
            showConvoyMap = false
            showVuMeter = false
        case .hiking:
            showRadar = true
            showElevation = true
            showConvoyMap = false
            showVuMeter = false
        case .minimal:
            showRadar = false
            showElevation = false
            showConvoyMap = false
            showVuMeter = false
        }
    }
}

