package org.nowni.intercom_alpha.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.bridge.IntercomHardwareBridge
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.feature.home.HomeViewModel
import org.nowni.intercom_alpha.mesh.MeshRepeaterService
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.MeshTextManager
import org.nowni.intercom_alpha.ui.repeater.RepeaterViewModel

fun createSharedUiModule(
    hardwareBridge: IntercomHardwareBridge = NoOpIntercomHardwareBridge()
) = module {
    single { ChannelManager() }
    single { ProximityRadar() }
    single { ConvoyGpsTracker(localPeerId = "android-local", localPeerName = "Android Rider") }
    single { MeshTextManager(localPeerId = "android-local", localPeerName = "Android Rider") }
    single { PriorityBroadcastManager() }
    single { EmergencyBlackboxRecorder() }
    single { AdaptiveBitrateController() }
    single { org.nowni.intercom_alpha.headset.HelmetAccessoryManager() }
    single { MeshRepeaterService(repeaterId = "basecamp-relay-1", repeaterName = "Basecamp Relay Alpha") }
    single { org.nowni.intercom_alpha.map.TrailMapManager() }
    single { org.nowni.intercom_alpha.map.MeshFileTransferManager() }
    single { org.nowni.intercom_alpha.telemetry.ConvoyGeofenceEngine() }
    single { org.nowni.intercom_alpha.power.BatteryGovernor(nodeId = "android-local") }
    single { org.nowni.intercom_alpha.safety.CrashDetector() }
    single { org.nowni.intercom_alpha.audio.VoiceCommandRecognizer() }
    single { org.nowni.intercom_alpha.mesh.routing.DirectedMeshRouter(localNodeId = "android-local") }
    single {
        org.nowni.intercom_alpha.mesh.federation.InterGroupGateway(
            localGroupId = "squad-alpha",
            localGroupName = "Rider Club Alpha",
            localPeerId = "android-local",
            isLocalLeader = true
        )
    }
    single { org.nowni.intercom_alpha.mesh.qos.MeshQoSScheduler() }
    single { org.nowni.intercom_alpha.mesh.failover.MultiPathFailoverArbiter() }
    single { org.nowni.intercom_alpha.mesh.MeshLinkQualityScorer() }
    single { org.nowni.intercom_alpha.audio.AdaptiveBitrateEngine() }
    single { org.nowni.intercom_alpha.audio.MeshPacketFecEngine() }
    single { org.nowni.intercom_alpha.audio.AudioLossConcealment() }
    single<IntercomHardwareBridge> { hardwareBridge }

    viewModel {
        HomeViewModel(
            channelManager = get(),
            proximityRadar = get(),
            convoyGpsTracker = get(),
            meshTextManager = get(),
            priorityManager = get(),
            blackboxRecorder = get(),
            bitrateController = get(),
            helmetAccessoryManager = get(),
            trailMapManager = get(),
            fileTransferManager = get(),
            geofenceEngine = get(),
            batteryGovernor = get(),
            crashDetector = get(),
            voiceCommandRecognizer = get(),
            directedRouter = get(),
            interGroupGateway = get(),
            qosScheduler = get(),
            failoverArbiter = get(),
            linkQualityScorer = get(),
            bitrateEngine = get(),
            fecEngine = get(),
            lossConcealment = get(),
            hardwareBridge = get()
        )
    }
    viewModelOf(::RepeaterViewModel)
}

val sharedUiModule = createSharedUiModule()
