import Foundation
import SwiftUI
import SharedLogic

@MainActor
final class IntercomViewModel: ObservableObject {
    // --- Core Audio & State ---
    @Published var isTransmitting: Bool = false
    @Published var isRecording: Bool = false
    @Published var inputLevel: Float = 0.0
    @Published var outputLevel: Float = 0.0
    @Published var groupName: String = "Rally Team Alpha"
    @Published var inviteCode: String = ""
    @Published var isConnected: Bool = false
    @Published var peers: [Peer] = []
    @Published var audioRoute: String = "Internal Audio"
    @Published var voxEnabled: Bool = false
    @Published var isKeepAliveActive: Bool = false
    @Published var showQrScanner: Bool = false
    @Published var showQrShare: Bool = false
    @Published var errorMessage: String? = nil

    // --- Sub-Channel Partitioning ---
    @Published var activeChannelId: Int = 1
    @Published var channels: [IntercomChannel] = []
    @Published var isScanMode: Bool = false
    @Published var monitoredChannelIds: Set<Int> = []

    // --- Radar & Convoy GPS Telemetry ---
    @Published var radarSnapshot: RadarSnapshot? = nil
    @Published var convoySnapshot: ConvoyTelemetrySnapshot? = nil

    // --- Mesh Text & Canned Hazards ---
    @Published var messages: [MeshTextMessage] = []
    @Published var unreadCount: Int = 0
    @Published var showMessageDrawer: Bool = false

    // --- Emergency Priority & Blackbox ---
    @Published var isEmergencyActive: Bool = false
    @Published var activePriority: PriorityLevel = .normal
    @Published var blackboxBufferedCount: Int = 0
    @Published var recentIncidents: [EncryptedIncidentReport] = []

    // --- Adaptive Bitrate ---
    @Published var currentAudioProfile: AudioProfile = .medium

    private let bridge = IntercomBridge(localPeerId: "ios-lead", localPeerName: "iOS Lead")
    private var cancellables: [CancellationHandle] = []

    init() {
        setupObservers()
        start()
    }

    deinit {
        cancellables.forEach { $0.cancel() }
        bridge.stop()
    }

    func start() {
        bridge.start(groupName: groupName)
        inviteCode = bridge.generateInviteCode()
    }

    func stop() {
        bridge.stop()
    }

    // --- Audio Controls ---

    func startPtt() {
        guard !isTransmitting else { return }
        isTransmitting = true
        bridge.setPttActive(active: true)
    }

    func stopPtt() {
        guard isTransmitting else { return }
        isTransmitting = false
        bridge.setPttActive(active: false)
    }

    func toggleVox() {
        voxEnabled.toggle()
        bridge.setVoxEnabled(enabled: voxEnabled, thresholdDb: -40.0)
    }

    func setVolume(_ volume: Float) {
        bridge.setOutputVolume(volume: volume)
    }

    // --- Channel Sub-Group Controls ---

    func selectChannel(id: Int) {
        activeChannelId = id
        bridge.selectChannel(channelId: Int32(id))
    }

    func toggleDualWatch(channelId: Int) {
        let isMonitored = monitoredChannelIds.contains(channelId)
        bridge.setDualWatch(enabled: !isMonitored, secondaryChannelId: Int32(channelId))
    }

    func toggleScanMode() {
        bridge.setScanMode(enabled: !isScanMode)
    }

    func toggleChannelMute(channelId: Int) {
        bridge.toggleChannelMute(channelId: Int32(channelId))
    }

    // --- GPS Telemetry Simulation / Updates ---

    func updateLocation(latitude: Double, longitude: Double, speedKmh: Float, heading: Float) {
        bridge.updateGpsLocation(
            latitude: latitude,
            longitude: longitude,
            altitudeMeters: 0.0,
            speedKmh: speedKmh,
            headingDegrees: heading
        )
    }

    // --- Text Messaging & Canned Alerts ---

    func sendCannedMessage(_ code: CannedMessageCode) {
        bridge.sendCannedMessage(cannedCode: code, recipientId: nil)
    }

    func sendCustomText(_ content: String, recipientId: String? = nil) {
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        bridge.sendCustomText(content: trimmed, recipientId: recipientId)
    }

    func markAllMessagesAsRead() {
        bridge.markAllMessagesAsRead()
    }

    func clearMessageHistory() {
        bridge.clearMessageHistory()
    }

    // --- Emergency SOS & Blackbox Cache ---

    func triggerEmergencySos() {
        bridge.triggerManualSos(note: "Rider initiated emergency SOS button from iOS cockpit")
    }

    func clearBlackboxCache() {
        bridge.clearBlackboxCache()
    }

    // --- Adaptive Bitrate Profile ---

    func setAudioProfile(_ profile: AudioProfile) {
        bridge.pinAudioProfile(profile: profile)
    }

    func resetAudioProfileToAuto() {
        bridge.resetAudioProfileToAuto()
    }

    // --- Group & QR Management ---

    func joinGroup(inviteCode: String) {
        bridge.joinGroup(inviteCode: inviteCode) { [weak self] group, error in
            Task { @MainActor in
                if let error = error {
                    self?.errorMessage = error
                } else if let group = group {
                    self?.groupName = group.name
                    self?.inviteCode = inviteCode
                    self?.isConnected = true
                }
            }
        }
    }

    func createNewGroup(name: String) {
        bridge.createGroup(name: name) { [weak self] group in
            Task { @MainActor in
                self?.groupName = group.name
                self?.inviteCode = self?.bridge.generateInviteCode() ?? ""
            }
        }
    }

    // --- Reactive Observers Setup ---

    private func setupObservers() {
        let inputSub = bridge.observeInputLevel { [weak self] level in
            Task { @MainActor in
                self?.inputLevel = level.floatValue
            }
        }
        cancellables.append(inputSub)

        let outputSub = bridge.observeOutputLevel { [weak self] level in
            Task { @MainActor in
                self?.outputLevel = level.floatValue
            }
        }
        cancellables.append(outputSub)

        let recordingSub = bridge.observeIsRecording { [weak self] rec in
            Task { @MainActor in
                self?.isRecording = rec.boolValue
            }
        }
        cancellables.append(recordingSub)

        let peersSub = bridge.observePeers { [weak self] peerList in
            Task { @MainActor in
                self?.peers = peerList
                self?.isConnected = !peerList.isEmpty
            }
        }
        cancellables.append(peersSub)

        let headsetSub = bridge.observeActiveHeadset { [weak self] headset in
            Task { @MainActor in
                if let headset = headset {
                    self?.audioRoute = "\(headset.name) (SCO Active)"
                } else {
                    self?.audioRoute = "Built-in Speaker"
                }
            }
        }
        cancellables.append(headsetSub)

        let groupSub = bridge.observeCurrentGroup { [weak self] grp in
            Task { @MainActor in
                if let grp = grp {
                    self?.groupName = grp.name
                }
            }
        }
        cancellables.append(groupSub)

        let keepAliveSub = bridge.observeKeepAliveState { [weak self] active in
            Task { @MainActor in
                self?.isKeepAliveActive = active.boolValue
            }
        }
        cancellables.append(keepAliveSub)

        // --- Subsystems v2.0 Observers ---

        let channelSub = bridge.observeChannelState { [weak self] state in
            Task { @MainActor in
                self?.activeChannelId = Int(state.activeChannelId)
                self?.channels = state.channels
                self?.isScanMode = state.isScanMode
                var monitored = Set<Int>()
                for id in state.monitoredChannelIds {
                    if let num = id as? NSNumber {
                        monitored.insert(num.intValue)
                    }
                }
                self?.monitoredChannelIds = monitored
            }
        }
        cancellables.append(channelSub)

        let radarSub = bridge.observeRadarSnapshot { [weak self] snapshot in
            Task { @MainActor in
                self?.radarSnapshot = snapshot
            }
        }
        cancellables.append(radarSub)

        let convoySub = bridge.observeConvoyTelemetry { [weak self] snapshot in
            Task { @MainActor in
                self?.convoySnapshot = snapshot
            }
        }
        cancellables.append(convoySub)

        let msgSub = bridge.observeMessages { [weak self] msgs in
            Task { @MainActor in
                self?.messages = msgs
            }
        }
        cancellables.append(msgSub)

        let unreadSub = bridge.observeUnreadCount { [weak self] count in
            Task { @MainActor in
                self?.unreadCount = Int(truncating: count)
            }
        }
        cancellables.append(unreadSub)

        let prioritySub = bridge.observePriorityLevel { [weak self] priority in
            Task { @MainActor in
                self?.activePriority = priority
            }
        }
        cancellables.append(prioritySub)

        let emergencySub = bridge.observeIsEmergencyActive { [weak self] active in
            Task { @MainActor in
                self?.isEmergencyActive = active.boolValue
            }
        }
        cancellables.append(emergencySub)

        let blackboxSub = bridge.observeBlackboxReports { [weak self] reports in
            Task { @MainActor in
                self?.recentIncidents = reports
            }
        }
        cancellables.append(blackboxSub)

        let blackboxCountSub = bridge.observeBlackboxBufferedCount { [weak self] count in
            Task { @MainActor in
                self?.blackboxBufferedCount = Int(truncating: count)
            }
        }
        cancellables.append(blackboxCountSub)

        let bitrateSub = bridge.observeCurrentAudioProfile { [weak self] profile in
            Task { @MainActor in
                self?.currentAudioProfile = profile
            }
        }
        cancellables.append(bitrateSub)
    }
}
