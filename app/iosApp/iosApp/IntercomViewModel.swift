import Foundation
import SwiftUI
import ActivityKit
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

    // --- Sprint 14 & 15 Telemetry & Cockpit Subsystems ---
    @Published var accessoryState: HelmetAccessoryState? = nil
    @Published var isPfsRatchetActive: Bool = true
    @Published var isVoxLocked: Bool = false
    @Published var speakerVolume: Float = 0.8

    // --- Basecamp Stationary Repeater Subsystem (Phase 15 / ICA-1703) ---
    @Published var isRepeaterModeActive: Bool = false
    @Published var repeaterTelemetry: RepeaterTelemetry? = nil
    @Published var lastPingLatencyMs: Int64? = nil
    @Published var latestBeacon: RepeaterBeaconPayload? = nil
    @Published var repeaterDutyCycle: RepeaterDutyCycleMode = .continuous
    @Published var showRepeaterSheet: Bool = false

    // --- Offline Vector Terrain & Trail Map Subsystem (Sprint 18 / ICA-1804) ---
    @Published var trailMapSnapshot: TrailMapSnapshot? = nil
    @Published var showTrailSheet: Bool = false

    // --- Over-The-Air BLE Mesh Trail Sharing Subsystem (Sprint 19 / ICA-1904) ---
    @Published var activeTransfers: [FileTransferProgress] = []
    @Published var storedTrails: [TrailPolyline] = []
    @Published var showTrailManagerSheet: Bool = false

    // --- Tactical Convoy Geofence & Battery Governor (Sprint 20 / ICA-2003) ---
    @Published var geofenceSnapshot: ConvoyGeofenceSnapshot? = nil
    @Published var powerGovernorState: PowerGovernorState? = nil

    // --- Autonomous Crash Detection & Voice Command Subsystems (Sprint 21 / ICA-2103) ---
    @Published var crashSnapshot: CrashCountdownSnapshot? = nil
    @Published var latestVoiceCommand: CommandDetection? = nil
    @Published var showCrashCountdownCard: Bool = false

    // --- Directed Mesh Routing Subsystem (Sprint 22 / ICA-2203) ---
    @Published var routingSnapshot: RoutingSnapshot? = nil
    @Published var showRoutingSheet: Bool = false

    private let bridge = IntercomBridge(localPeerId: "ios-lead", localPeerName: "iOS Lead")
    private var cancellables: [CancellationHandle] = []
    private var activeActivity: Activity<IntercomActivityAttributes>? = nil

    init() {
        setupObservers()
        start()
    }

    deinit {
        endLiveActivity()
        cancellables.forEach { $0.cancel() }
        bridge.stop()
    }

    func start() {
        bridge.start(groupName: groupName)
        inviteCode = bridge.generateInviteCode()
        startLiveActivity()
    }

    func stop() {
        endLiveActivity()
        bridge.stop()
    }

    // --- Audio Controls ---

    func startPtt() {
        guard !isTransmitting else { return }
        isTransmitting = true
        bridge.setPttActive(active: true)
        updateLiveActivity()
    }

    func stopPtt() {
        guard isTransmitting else { return }
        isTransmitting = false
        bridge.setPttActive(active: false)
        updateLiveActivity()
    }

    func toggleVox() {
        voxEnabled.toggle()
        bridge.setVoxEnabled(enabled: voxEnabled, thresholdDb: -40.0)
    }

    func setVolume(_ volume: Float) {
        speakerVolume = volume
        bridge.setOutputVolume(volume: volume)
    }

    func cycleVolume() {
        let newVol: Float
        if speakerVolume < 0.35 {
            newVol = 0.6
        } else if speakerVolume < 0.75 {
            newVol = 1.0
        } else {
            newVol = 0.3
        }
        setVolume(newVol)
    }

    func toggleVoxLock() {
        isVoxLocked.toggle()
        if isVoxLocked {
            bridge.setVoxEnabled(enabled: false, thresholdDb: -40.0)
            bridge.setPttActive(active: false)
            isTransmitting = false
        } else {
            bridge.setVoxEnabled(enabled: voxEnabled, thresholdDb: -40.0)
        }
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
        bridge.updateTrailLocalGps(lat: latitude, lon: longitude, alt: 0.0, heading: heading)
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

    // --- Crash Detection & Voice Command Actions (Sprint 21 / ICA-2103) ---

    func cancelCrashCountdown() {
        bridge.cancelCrashCountdown()
        showCrashCountdownCard = false
    }

    func triggerManualCrashSos() {
        bridge.triggerManualCrashSos()
        showCrashCountdownCard = false
    }

    func dismissVoiceCommandBadge() {
        latestVoiceCommand = nil
    }

    private func handleVoiceCommand(_ command: VoiceCommand) {
        switch command {
        case .sosTrigger:
            triggerEmergencySos()
            bridge.triggerManualCrashSos()
        case .nextChannel:
            let next = (activeChannelId >= 8) ? 1 : activeChannelId + 1
            selectChannel(next)
        case .prevChannel:
            let prev = (activeChannelId <= 1) ? 8 : activeChannelId - 1
            selectChannel(prev)
        case .muteToggle:
            if isTransmitting {
                stopPtt()
            } else {
                startPtt()
            }
        case .cancelAlert:
            cancelCrashCountdown()
        default:
            break
        }
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

    // --- Basecamp Repeater Controls (Phase 15 / ICA-1703) ---

    func setRepeaterActive(_ active: Bool) {
        isRepeaterModeActive = active
        bridge.setRepeaterModeActive(active: active)
    }

    func toggleRepeaterMode() {
        setRepeaterActive(!isRepeaterModeActive)
    }

    func setRepeaterDutyCycle(_ mode: RepeaterDutyCycleMode) {
        repeaterDutyCycle = mode
        bridge.setRepeaterDutyCycle(mode: mode)
    }

    func triggerDiagnosticPing() {
        bridge.triggerDiagnosticPing()
    }

    func emitRepeaterBeacon() {
        bridge.emitRepeaterBeacon()
    }

    // --- Trail Map Controls (Sprint 18 / ICA-1804) ---

    func cycleRadarZoom() {
        bridge.cycleRadarZoom()
    }

    func setRadarZoom(_ zoom: RadarRangeZoom) {
        bridge.setRadarZoom(zoom: zoom)
    }

    func setTrailOverlayEnabled(_ enabled: Bool) {
        bridge.setTrailOverlayEnabled(enabled: enabled)
    }

    func loadSampleAlpineTrail() {
        bridge.loadSampleAlpineTrail(lat: 37.7749, lon: -122.4194, alt: 2500.0)
    }

    func toggleTrailSheet() {
        showTrailSheet.toggle()
    }

    // --- Over-The-Air Trail Sharing Controls (Sprint 19 / ICA-1904) ---

    func beamActiveTrail(format: FileTransferType = .gpx) {
        if let activeTrail = trailMapSnapshot?.activeTrail {
            let waypoints = trailMapSnapshot?.waypoints ?? []
            bridge.sendTrailOverMesh(trail: activeTrail, waypoints: waypoints, format: format)
        } else if let first = storedTrails.first {
            bridge.sendTrailOverMesh(trail: first, waypoints: [], format: format)
        }
    }

    func beamTrail(trail: TrailPolyline, format: FileTransferType = .gpx) {
        let waypoints = trailMapSnapshot?.waypoints ?? []
        bridge.sendTrailOverMesh(trail: trail, waypoints: waypoints, format: format)
    }

    func cancelTransfer(transferId: String) {
        bridge.cancelFileTransfer(transferId: transferId)
    }

    func importTrailText(text: String, fileName: String, format: FileTransferType = .gpx) {
        bridge.importTrailFromText(text: text, fileName: fileName, format: format)
    }

    func deleteStoredTrail(trailId: String) {
        bridge.deleteStoredTrail(trailId: trailId)
    }

    func selectStoredTrail(trail: TrailPolyline) {
        bridge.loadTrail(trail: trail)
    }

    func toggleTrailManagerSheet() {
        showTrailManagerSheet.toggle()
    }

    // --- Tactical Geofence & Battery Governor Controls (Sprint 20 / ICA-2003) ---

    func setPowerPolicyOverride(policy: PowerPolicy?) {
        bridge.setPowerPolicyOverride(policy: policy)
    }

    func setGeofenceLeadRider(leadPeerId: String?) {
        bridge.setGeofenceLeadRider(leadPeerId: leadPeerId)
    }

    func updateBattery(percent: Int, isCharging: Bool = false) {
        bridge.updateBattery(percent: Int32(percent), isCharging: isCharging)
    }

    func initiateRouteDiscovery(destinationId: String) {
        bridge.initiateRouteDiscovery(destinationId: destinationId)
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

        let accessorySub = bridge.observeHelmetAccessory { [weak self] state in
            Task { @MainActor in
                self?.accessoryState = state
            }
        }
        cancellables.append(accessorySub)

        // --- Basecamp Repeater Observers (Phase 15 / ICA-1703) ---
        let repeaterActiveSub = bridge.observeRepeaterModeActive { [weak self] active in
            Task { @MainActor in
                self?.isRepeaterModeActive = active.boolValue
            }
        }
        cancellables.append(repeaterActiveSub)

        let repeaterTelemetrySub = bridge.observeRepeaterTelemetry { [weak self] tele in
            Task { @MainActor in
                self?.repeaterTelemetry = tele
            }
        }
        cancellables.append(repeaterTelemetrySub)

        let pingLatencySub = bridge.observeLastPingLatency { [weak self] latency in
            Task { @MainActor in
                if let latency = latency {
                    self?.lastPingLatencyMs = latency.int64Value
                } else {
                    self?.lastPingLatencyMs = nil
                }
            }
        }
        cancellables.append(pingLatencySub)

        let beaconSub = bridge.observeLatestBeacon { [weak self] beacon in
            Task { @MainActor in
                self?.latestBeacon = beacon
            }
        }
        cancellables.append(beaconSub)

        let trailMapSub = bridge.observeTrailMapSnapshot { [weak self] snapshot in
            Task { @MainActor in
                self?.trailMapSnapshot = snapshot
            }
        }
        cancellables.append(trailMapSub)

        // --- Over-The-Air File Transfer Observers (Sprint 19 / ICA-1904) ---
        let fileTransfersSub = bridge.observeFileTransfers { [weak self] transfers in
            Task { @MainActor in
                self?.activeTransfers = transfers
            }
        }
        cancellables.append(fileTransfersSub)

        let storedTrailsSub = bridge.observeStoredTrails { [weak self] trails in
            Task { @MainActor in
                self?.storedTrails = trails
            }
        }
        cancellables.append(storedTrailsSub)

        // --- Tactical Convoy Geofence & Battery Governor Observers (Sprint 20 / ICA-2003) ---
        let geofenceSub = bridge.observeGeofenceSnapshot { [weak self] snapshot in
            Task { @MainActor in
                self?.geofenceSnapshot = snapshot
            }
        }
        cancellables.append(geofenceSub)

        let powerGovernorSub = bridge.observeBatteryGovernorState { [weak self] state in
            Task { @MainActor in
                self?.powerGovernorState = state
            }
        }
        cancellables.append(powerGovernorSub)

        // --- Autonomous Crash Detection & Voice Command Observers (Sprint 21 / ICA-2103) ---
        let crashSub = bridge.observeCrashSnapshot { [weak self] snapshot in
            Task { @MainActor in
                self?.crashSnapshot = snapshot
                self?.showCrashCountdownCard = (snapshot.state == CrashState.countdownActive)
            }
        }
        cancellables.append(crashSub)

        let voiceSub = bridge.observeVoiceCommands { [weak self] command in
            Task { @MainActor in
                self?.latestVoiceCommand = command
                if let cmd = command {
                    self?.handleVoiceCommand(cmd.command)
                }
            }
        }
        cancellables.append(voiceSub)

        // --- Directed Mesh Routing Observers (Sprint 22 / ICA-2203) ---
        let routingSub = bridge.observeRoutingSnapshot { [weak self] snapshot in
            Task { @MainActor in
                self?.routingSnapshot = snapshot
            }
        }
        cancellables.append(routingSub)

        isPfsRatchetActive = bridge.isPfsRatchetActive()
    }

    // --- ActivityKit Live Activity & Dynamic Island ---

    func startLiveActivity() {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        guard activeActivity == nil else { return }

        let attributes = IntercomActivityAttributes(
            groupName: groupName,
            localRiderName: "iOS Lead"
        )
        let initialContentState = makeActivityContentState()

        do {
            let activity = try Activity.request(
                attributes: attributes,
                content: .init(state: initialContentState, staleDate: nil)
            )
            activeActivity = activity
        } catch {
            print("Failed to start Intercom Live Activity: \(error.localizedDescription)")
        }
    }

    func updateLiveActivity() {
        guard let activity = activeActivity else { return }
        let updatedState = makeActivityContentState()
        Task {
            await activity.update(.init(state: updatedState, staleDate: nil))
        }
    }

    func endLiveActivity() {
        guard let activity = activeActivity else { return }
        let finalState = makeActivityContentState()
        Task {
            await activity.end(.init(state: finalState, staleDate: nil), dismissalPolicy: .immediate)
        }
        activeActivity = nil
    }

    private func makeActivityContentState() -> IntercomActivityAttributes.ContentState {
        let activeChan = channels.first(where: { $0.id == activeChannelId })
        let closestRider = convoySnapshot?.closestRider

        return IntercomActivityAttributes.ContentState(
            isTransmitting: isTransmitting,
            activeSpeaker: isTransmitting ? "You (Transmitting)" : nil,
            activeChannelName: activeChan?.name ?? "Convoy Main",
            activeChannelId: activeChannelId,
            peerCount: peers.count,
            isEmergencyActive: isEmergencyActive,
            leadDistanceMeters: closestRider?.distanceMeters,
            relativeBearingDegrees: closestRider?.relativeBearingDegrees,
            batteryPercent: 95,
            unreadMessageCount: unreadCount
        )
    }
}
