import SwiftUI
import UIKit

// MARK: - Tactical Sensory Haptics Engine
// Calibrated specifically for thick motorcycle leather gloves and extreme alpine vibration.
// High-energy, crisp impact patterns distinguishing PTT transmission, VOX activation, and SOS warnings.

public final class TactileHaptics {
    public static let shared = TactileHaptics()

    private let rigidImpact = UIImpactFeedbackGenerator(style: .rigid)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let notificationFeedback = UINotificationFeedbackGenerator()
    private let selectionFeedback = UISelectionFeedbackGenerator()

    private init() {
        // Pre-warm haptic engines for sub-millisecond response latency
        prepare()
    }

    public func prepare() {
        rigidImpact.prepare()
        heavyImpact.prepare()
        mediumImpact.prepare()
        notificationFeedback.prepare()
        selectionFeedback.prepare()
    }

    /// Master PTT Actuator Pressed - Solid rigid mechanical snap
    public func pttEngage() {
        rigidImpact.impactOccurred(intensity: 1.0)
    }

    /// Master PTT Actuator Released - Lighter mechanical spring release
    public func pttRelease() {
        mediumImpact.impactOccurred(intensity: 0.7)
    }

    /// Emergency SOS Switch Triggered - Maximum heavy double pulse
    public func emergencySos() {
        heavyImpact.impactOccurred(intensity: 1.0)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [weak self] in
            self?.heavyImpact.impactOccurred(intensity: 1.0)
        }
    }

    /// Rotary Channel Selector Detent Click - Crisp mechanical detent tick
    public func channelDetentClick() {
        selectionFeedback.selectionChanged()
    }

    /// Life-Safety Alert / Geofence Breach Notification
    public func warningAlert() {
        notificationFeedback.notificationOccurred(.warning)
    }

    /// Success / Group Rekeyed / Satellite Beacon Confirmed
    public func successAlert() {
        notificationFeedback.notificationOccurred(.success)
    }
}
