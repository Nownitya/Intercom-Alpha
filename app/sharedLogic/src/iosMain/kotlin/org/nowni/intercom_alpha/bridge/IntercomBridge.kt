package org.nowni.intercom_alpha.bridge

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import org.nowni.intercom_alpha.audio.AudioEngine
import org.nowni.intercom_alpha.audio.AudioEngineConfig
import org.nowni.intercom_alpha.audio.AudioEngineImpl
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.group.Group
import org.nowni.intercom_alpha.group.GroupConfig
import org.nowni.intercom_alpha.group.GroupManager
import org.nowni.intercom_alpha.group.GroupManagerImpl
import org.nowni.intercom_alpha.headset.HeadsetButtonEvent
import org.nowni.intercom_alpha.headset.HeadsetInfo
import org.nowni.intercom_alpha.headset.HeadsetManager
import org.nowni.intercom_alpha.headset.HeadsetManagerImpl
import org.nowni.intercom_alpha.mesh.ConnectionState
import org.nowni.intercom_alpha.mesh.MeshConfig
import org.nowni.intercom_alpha.mesh.MeshTransport
import org.nowni.intercom_alpha.mesh.MeshTransportImpl
import org.nowni.intercom_alpha.mesh.Peer

fun interface CancellationHandle {
    fun cancel()
}

@OptIn(ExperimentalForeignApi::class)
class IntercomBridge {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val audioEngine: AudioEngine = AudioEngineImpl(scope)
    val meshTransport: MeshTransport = MeshTransportImpl(scope)
    val headsetManager: HeadsetManager = HeadsetManagerImpl(scope)
    val groupManager: GroupManager = GroupManagerImpl(meshTransport)

    private var audioRxJob: Job? = null
    private var audioTxJob: Job? = null
    private var headsetEventsJob: Job? = null

    private var isPttActive: Boolean = false
    private var currentAudioProfile: AudioProfile = AudioProfile.default

    fun start(groupName: String = "Intercom Group") {
        scope.launch {
            // 1. Start audio and headset hardware managers
            headsetManager.start()
            audioEngine.start(AudioEngineConfig())

            // 2. Initialize or restore Group
            val group = groupManager.createGroup(groupName, GroupConfig(audioProfile = currentAudioProfile))
            
            // 3. Start BLE Mesh transport
            meshTransport.start(
                groupId = group.id,
                peerName = "iOS Rider",
                config = MeshConfig(
                    groupId = group.id,
                    peerName = "iOS Rider",
                    audioProfile = currentAudioProfile
                )
            )

            // 4. Pipe incoming Mesh audio packets directly into AudioEngine player
            audioRxJob?.cancel()
            audioRxJob = scope.launch(Dispatchers.Default) {
                for (packet in meshTransport.incomingAudio) {
                    val pcmShorts = audioEngine.decode(packet.data, packet.profile)
                    audioEngine.playAudio(pcmShorts)
                }
            }

            // 5. Pipe recorded AudioEngine microphone audio directly to MeshTransport
            audioTxJob?.cancel()
            audioTxJob = scope.launch(Dispatchers.Default) {
                for (pcmShorts in audioEngine.recordedAudio) {
                    if (isPttActive) {
                        val encoded = audioEngine.encode(pcmShorts, currentAudioProfile)
                        meshTransport.sendAudio(encoded, currentAudioProfile)
                    }
                }
            }

            // 6. Intercept physical headset buttons (e.g. helmet PTT or AirPods click)
            headsetEventsJob?.cancel()
            headsetEventsJob = scope.launch(Dispatchers.Main) {
                for (event in headsetManager.buttonEvents) {
                    when (event) {
                        HeadsetButtonEvent.PTT_PRESS -> setPttActive(true)
                        HeadsetButtonEvent.PTT_RELEASE -> setPttActive(false)
                        else -> {}
                    }
                }
            }
        }
    }

    fun stop() {
        audioRxJob?.cancel()
        audioTxJob?.cancel()
        headsetEventsJob?.cancel()

        scope.launch {
            audioEngine.stop()
            meshTransport.stop()
            headsetManager.stop()
        }
    }

    fun setPttActive(active: Boolean) {
        isPttActive = active
    }

    fun setVoxEnabled(enabled: Boolean, thresholdDb: Float = -40f) {
        audioEngine.setVoxEnabled(enabled)
        audioEngine.setVoxThreshold(thresholdDb)
    }

    fun setOutputVolume(volume: Float) {
        audioEngine.setOutputVolume(volume)
    }

    // --- Swift-Friendly Observers ---

    fun observeInputLevel(onLevel: (Float) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (level in audioEngine.inputLevel) {
                onLevel(level)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeOutputLevel(onLevel: (Float) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (level in audioEngine.outputLevel) {
                onLevel(level)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeIsRecording(onRecording: (Boolean) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (rec in audioEngine.isRecording) {
                onRecording(rec)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observePeers(onPeers: (List<Peer>) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (peers in meshTransport.peers) {
                onPeers(peers)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeConnectionState(onState: (ConnectionState) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (state in meshTransport.connectionState) {
                onState(state)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeActiveHeadset(onHeadset: (HeadsetInfo?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (headset in headsetManager.activeHeadset) {
                onHeadset(headset)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    fun observeCurrentGroup(onGroup: (Group?) -> Unit): CancellationHandle {
        val job = scope.launch(Dispatchers.Main) {
            for (grp in groupManager.currentGroup) {
                onGroup(grp)
            }
        }
        return CancellationHandle { job.cancel() }
    }

    // --- Group & QR Management ---

    fun createGroup(name: String, onComplete: (Group) -> Unit) {
        scope.launch(Dispatchers.Main) {
            val grp = groupManager.createGroup(name, GroupConfig(audioProfile = currentAudioProfile))
            meshTransport.updateConfig(
                MeshConfig(
                    groupId = grp.id,
                    peerName = "iOS Rider",
                    audioProfile = currentAudioProfile
                )
            )
            onComplete(grp)
        }
    }

    fun joinGroup(inviteCode: String, onComplete: (Group?, String?) -> Unit) {
        scope.launch(Dispatchers.Main) {
            try {
                val grp = groupManager.joinGroup(inviteCode)
                meshTransport.updateConfig(
                    MeshConfig(
                        groupId = grp.id,
                        peerName = "iOS Rider",
                        audioProfile = currentAudioProfile
                    )
                )
                onComplete(grp, null)
            } catch (e: Exception) {
                onComplete(null, e.message ?: "Failed to join group")
            }
        }
    }

    fun generateInviteCode(): String = groupManager.generateInviteCode()

    fun leaveGroup() {
        scope.launch(Dispatchers.Main) {
            groupManager.leaveGroup()
        }
    }
}
