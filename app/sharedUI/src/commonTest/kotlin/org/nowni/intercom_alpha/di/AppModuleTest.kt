package org.nowni.intercom_alpha.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.nowni.intercom_alpha.audio.AdaptiveBitrateController
import org.nowni.intercom_alpha.audio.EmergencyBlackboxRecorder
import org.nowni.intercom_alpha.audio.PriorityBroadcastManager
import org.nowni.intercom_alpha.channel.ChannelManager
import org.nowni.intercom_alpha.feature.home.HomeViewModel
import org.nowni.intercom_alpha.mesh.ProximityRadar
import org.nowni.intercom_alpha.telemetry.ConvoyGpsTracker
import org.nowni.intercom_alpha.text.MeshTextManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun testSharedUiModuleResolvesAllSubsystemDependenciesAndHomeViewModel() {
        val koinApp = startKoin {
            modules(sharedUiModule)
        }
        val koin = koinApp.koin

        val channelManager: ChannelManager = koin.get()
        val proximityRadar: ProximityRadar = koin.get()
        val convoyGpsTracker: ConvoyGpsTracker = koin.get()
        val meshTextManager: MeshTextManager = koin.get()
        val priorityManager: PriorityBroadcastManager = koin.get()
        val blackboxRecorder: EmergencyBlackboxRecorder = koin.get()
        val bitrateController: AdaptiveBitrateController = koin.get()
        val trailMapManager: org.nowni.intercom_alpha.map.TrailMapManager = koin.get()
        val hardwareBridge: org.nowni.intercom_alpha.bridge.IntercomHardwareBridge = koin.get()
        val homeViewModel: HomeViewModel = koin.get()

        assertNotNull(channelManager)
        assertNotNull(proximityRadar)
        assertNotNull(convoyGpsTracker)
        assertNotNull(meshTextManager)
        assertNotNull(priorityManager)
        assertNotNull(blackboxRecorder)
        assertNotNull(bitrateController)
        assertNotNull(trailMapManager)
        assertNotNull(hardwareBridge)
        assertNotNull(homeViewModel)
    }
}
