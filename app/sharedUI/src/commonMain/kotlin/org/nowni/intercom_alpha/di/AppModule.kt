package org.nowni.intercom_alpha.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.bridge.IntercomHardwareBridge
import org.nowni.intercom_alpha.bridge.NoOpIntercomHardwareBridge
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.feature.home.HomeViewModel
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.MeshTextManager

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
    single<IntercomHardwareBridge> { hardwareBridge }

    viewModelOf(::HomeViewModel)
}

val sharedUiModule = createSharedUiModule()
