import UIKit

public final class TacticalHaptics {
    public static let shared = TacticalHaptics()

    private let lightImpact = UIImpactFeedbackGenerator(style: .light)
    private let mediumImpact = UIImpactFeedbackGenerator(style: .medium)
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let selectionFeedback = UISelectionFeedbackGenerator()
    private let notificationFeedback = UINotificationFeedbackGenerator()

    private init() {
        prepare()
    }

    public func prepare() {
        lightImpact.prepare()
        mediumImpact.prepare()
        heavyImpact.prepare()
        selectionFeedback.prepare()
        notificationFeedback.prepare()
    }

    public func pttPressStart() {
        heavyImpact.impactOccurred()
    }

    public func pttPressEnd() {
        mediumImpact.impactOccurred()
    }

    public func channelDetentClick() {
        selectionFeedback.selectionChanged()
    }

    public func emergencyAlert() {
        notificationFeedback.notificationOccurred(.error)
    }

    public func warningAlert() {
        notificationFeedback.notificationOccurred(.warning)
    }

    public func successAck() {
        notificationFeedback.notificationOccurred(.success)
    }

    // Static Convience Callers
    public static func selection() {
        shared.channelDetentClick()
    }

    public static func pttStart() {
        shared.pttPressStart()
    }

    public static func pttEnd() {
        shared.pttPressEnd()
    }

    public static func emergency() {
        shared.emergencyAlert()
    }
}
