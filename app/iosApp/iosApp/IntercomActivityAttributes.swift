import Foundation
import ActivityKit

/**
 * ActivityKit attributes and dynamic state model for Intercom-Alpha Live Activities.
 */
public struct IntercomActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var isTransmitting: Bool
        public var activeSpeaker: String?
        public var activeChannelName: String
        public var activeChannelId: Int
        public var peerCount: Int
        public var isEmergencyActive: Bool
        public var leadDistanceMeters: Double?
        public var relativeBearingDegrees: Double? // -180.0 ... +180.0
        public var batteryPercent: Int
        public var unreadMessageCount: Int

        public init(
            isTransmitting: Bool = false,
            activeSpeaker: String? = nil,
            activeChannelName: String = "Convoy Main",
            activeChannelId: Int = 1,
            peerCount: Int = 0,
            isEmergencyActive: Bool = false,
            leadDistanceMeters: Double? = nil,
            relativeBearingDegrees: Double? = nil,
            batteryPercent: Int = 100,
            unreadMessageCount: Int = 0
        ) {
            self.isTransmitting = isTransmitting
            self.activeSpeaker = activeSpeaker
            self.activeChannelName = activeChannelName
            self.activeChannelId = activeChannelId
            self.peerCount = peerCount
            self.isEmergencyActive = isEmergencyActive
            self.leadDistanceMeters = leadDistanceMeters
            self.relativeBearingDegrees = relativeBearingDegrees
            self.batteryPercent = batteryPercent
            self.unreadMessageCount = unreadMessageCount
        }
    }

    public var groupName: String
    public var localRiderName: String

    public init(groupName: String, localRiderName: String) {
        self.groupName = groupName
        self.localRiderName = localRiderName
    }
}
