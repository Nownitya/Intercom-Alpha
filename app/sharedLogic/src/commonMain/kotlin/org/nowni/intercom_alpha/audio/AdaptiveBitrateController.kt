package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.currentTimeMillis
import org.nowni.intercom_alpha.mesh.InterferenceLevel
import org.nowni.intercom_alpha.mesh.LinkQuality

/**
 * Controller operating mode.
 */
@Serializable
enum class AdaptationMode {
    /** Closed-loop automatic bitrate adaptation driven by RF metrics. */
    AUTO,

    /** Manually pinned to a specific AudioProfile ignoring RF diagnostics. */
    MANUAL_OVERRIDE
}

/**
 * Diagnostic snapshot of the dynamic bitrate controller state.
 */
@Serializable
data class BitrateAdaptationState(
    val currentProfile: AudioProfile,
    val candidateProfile: AudioProfile,
    val candidateCycles: Int,
    val mode: AdaptationMode,
    val lastAdjustmentTimestampMs: Long,
    val reason: String
)

/**
 * Closed-Loop Dynamic Adaptive Bitrate & Codec Profile Controller.
 *
 * Dynamically shifts [AudioProfile] (ULTRA_LOW, LOW, MEDIUM, HIGH) to maintain uninterrupted
 * voice communications under changing RF propagation conditions, packet collision rates,
 * and physical distance.
 *
 * Implements asymmetric hysteresis:
 * - Rapid step-down: Immediately protects stream integrity upon severe RF degradation or critical packet loss.
 * - Conservative step-up: Requires sustained pristine conditions for [hysteresisCycles] before upgrading profile.
 *
 * @param initialProfile Starting audio codec profile. Default: [AudioProfile.MEDIUM].
 * @param hysteresisCycles Number of consecutive evaluation cycles required to upgrade profile. Default: 3.
 */
class AdaptiveBitrateController(
    initialProfile: AudioProfile = AudioProfile.MEDIUM,
    val hysteresisCycles: Int = 3
) {
    private val mutex = Mutex()

    private var currentAudioProfile: AudioProfile = initialProfile
    private var candidateAudioProfile: AudioProfile = initialProfile
    private var candidateCycleCounter: Int = 0
    private var mode: AdaptationMode = AdaptationMode.AUTO
    private var manualProfile: AudioProfile = initialProfile
    private var lastAdjustmentTime: Long = 0L
    private var lastDecisionReason: String = "Initial profile established"

    private val _currentProfile = MutableStateFlow(initialProfile)
    val currentProfile: StateFlow<AudioProfile> = _currentProfile.asStateFlow()

    private val _adaptationState = MutableStateFlow(
        BitrateAdaptationState(
            currentProfile = initialProfile,
            candidateProfile = initialProfile,
            candidateCycles = 0,
            mode = AdaptationMode.AUTO,
            lastAdjustmentTimestampMs = 0L,
            reason = lastDecisionReason
        )
    )
    val adaptationState: StateFlow<BitrateAdaptationState> = _adaptationState.asStateFlow()

    /**
     * Feed RF diagnostics and evaluate whether an adaptation step is required.
     *
     * @param linkQuality Lowest or cluster-averaged link health.
     * @param interferenceLevel Measured 2.4 GHz spectrum interference.
     * @param pdr Measured packet delivery ratio (0.0 .. 1.0).
     * @param linkMarginDb Receiver link margin relative to sensitivity floor.
     * @return The updated [AudioProfile] after evaluation.
     */
    suspend fun evaluateRfConditions(
        linkQuality: LinkQuality,
        interferenceLevel: InterferenceLevel,
        pdr: Double,
        linkMarginDb: Double
    ): AudioProfile = mutex.withLock {
        if (mode == AdaptationMode.MANUAL_OVERRIDE) {
            return@withLock currentAudioProfile
        }

        val recommended = mapMetricsToOptimalProfile(linkQuality, interferenceLevel, pdr, linkMarginDb)
        val now = currentTimeMillis()

        if (recommended == currentAudioProfile) {
            // Settled at current profile; reset candidate counter
            candidateAudioProfile = recommended
            candidateCycleCounter = 0
            updateStateInternal(now, "Maintained ${currentAudioProfile.name} under stable conditions")
            return@withLock currentAudioProfile
        }

        // Asymmetric Hysteresis Logic:
        val isDowngrade = recommended.bitrateKbps < currentAudioProfile.bitrateKbps

        if (isDowngrade) {
            // Rapid downgrade: step down immediately or if dropping severely
            val isSevereDrop = recommended == AudioProfile.ULTRA_LOW || interferenceLevel == InterferenceLevel.SEVERE
            if (isSevereDrop || candidateAudioProfile == recommended) {
                // Immediate step-down to prevent audio loss
                performProfileSwitch(recommended, now, "Downgraded due to RF degradation (PDR: ${(pdr * 100).toInt()}%, Margin: ${linkMarginDb.toInt()}dB)")
            } else {
                // Register candidate step-down
                candidateAudioProfile = recommended
                candidateCycleCounter = 1
                updateStateInternal(now, "Candidate step-down to ${recommended.name} detected")
            }
        } else {
            // Conservative upgrade: requires [hysteresisCycles] sustained cycles
            if (candidateAudioProfile == recommended) {
                candidateCycleCounter++
                if (candidateCycleCounter >= hysteresisCycles) {
                    performProfileSwitch(recommended, now, "Upgraded to ${recommended.name} after $candidateCycleCounter sustained clean cycles")
                } else {
                    updateStateInternal(now, "Upgrading to ${recommended.name}: cycle $candidateCycleCounter/$hysteresisCycles")
                }
            } else {
                candidateAudioProfile = recommended
                candidateCycleCounter = 1
                updateStateInternal(now, "Candidate upgrade to ${recommended.name} registered (1/$hysteresisCycles)")
            }
        }

        currentAudioProfile
    }

    /**
     * Lock the controller into a manual profile override, bypassing automatic adaptation.
     */
    suspend fun setManualOverride(profile: AudioProfile) = mutex.withLock {
        mode = AdaptationMode.MANUAL_OVERRIDE
        manualProfile = profile
        candidateAudioProfile = profile
        candidateCycleCounter = 0
        performProfileSwitch(profile, currentTimeMillis(), "Manual override locked to ${profile.name}")
    }

    /**
     * Resume closed-loop automatic RF adaptation.
     */
    suspend fun setAutoMode() = mutex.withLock {
        mode = AdaptationMode.AUTO
        candidateCycleCounter = 0
        updateStateInternal(currentTimeMillis(), "Automatic adaptation mode restored")
    }

    /**
     * Reset adaptation state to initial defaults.
     */
    suspend fun reset(initial: AudioProfile = AudioProfile.MEDIUM) = mutex.withLock {
        mode = AdaptationMode.AUTO
        currentAudioProfile = initial
        candidateAudioProfile = initial
        candidateCycleCounter = 0
        lastAdjustmentTime = currentTimeMillis()
        lastDecisionReason = "Controller reset"
        _currentProfile.value = initial
        updateStateInternal(lastAdjustmentTime, lastDecisionReason)
    }

    private fun mapMetricsToOptimalProfile(
        linkQuality: LinkQuality,
        interferenceLevel: InterferenceLevel,
        pdr: Double,
        linkMarginDb: Double
    ): AudioProfile {
        return when {
            // Severe degradation / survival mode
            linkQuality == LinkQuality.CRITICAL ||
            linkQuality == LinkQuality.LOST ||
            interferenceLevel == InterferenceLevel.SEVERE ||
            pdr < 0.65 ||
            linkMarginDb < 3.0 -> AudioProfile.ULTRA_LOW

            // Marginal conditions / range priority
            linkQuality == LinkQuality.MARGINAL ||
            pdr < 0.80 ||
            linkMarginDb < 8.0 -> AudioProfile.LOW

            // Pristine, clean RF channel
            linkQuality == LinkQuality.EXCELLENT &&
            interferenceLevel == InterferenceLevel.LOW &&
            pdr >= 0.95 &&
            linkMarginDb >= 15.0 -> AudioProfile.HIGH

            // Standard good channel / balanced default
            else -> AudioProfile.MEDIUM
        }
    }

    private fun performProfileSwitch(target: AudioProfile, timestampMs: Long, reason: String) {
        currentAudioProfile = target
        candidateAudioProfile = target
        candidateCycleCounter = 0
        lastAdjustmentTime = timestampMs
        lastDecisionReason = reason
        _currentProfile.value = target
        updateStateInternal(timestampMs, reason)
    }

    private fun updateStateInternal(timestampMs: Long, reason: String) {
        _adaptationState.value = BitrateAdaptationState(
            currentProfile = currentAudioProfile,
            candidateProfile = candidateAudioProfile,
            candidateCycles = candidateCycleCounter,
            mode = mode,
            lastAdjustmentTimestampMs = lastAdjustmentTime,
            reason = reason
        )
    }
}
