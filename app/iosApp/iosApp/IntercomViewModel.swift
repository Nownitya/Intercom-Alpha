import Foundation
import SwiftUI
import SharedLogic

@MainActor
final class IntercomViewModel: ObservableObject {
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

    private let bridge = IntercomBridge()
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
    }
}
