package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.ClusterLinkHealth
import org.nowni.intercom_alpha.mesh.LinkQualityGrade
import org.nowni.intercom_alpha.mesh.PeerLinkScore

/**
 * Strategy for aggregating multiple peer link scores when selecting an adaptive bitrate.
 */
@Serializable
enum class ClusterPolicy {
    /** Adapts to the weakest active peer to ensure 100% convoy audibility. */
    WORST_LINK,

    /** Adapts to the cluster-wide average link quality score. */
    CLUSTER_AVERAGE
}

/**
 * Real-time telemetry snapshot of the dynamic bitrate engine.
 */
@Serializable
data class BitrateEngineTelemetry(
    val activeProfile: AudioProfile,
    val effectiveBitrateKbps: Int,
    val effectiveFrameDurationMs: Int,
    val candidateProfile: AudioProfile,
    val consecutivePristineCycles: Int,
    val hysteresisThresholdCycles: Int,
    val isPinned: Boolean,
    val clusterPolicy: ClusterPolicy,
    val governingScore: Int,
    val governingGrade: LinkQualityGrade,
    val lastTransitionReason: String,
    val timestampMs: Long
)

/**
 * Closed-Loop Dynamic Adaptive Bitrate & Frame Sizing Controller.
 *
 * Dynamically shifts audio compression bitrate (16 kbps .. 48 kbps) and packet consolidation
 * frame sizes (20ms .. 60ms) based on real-time composite [Q_link] metrics from [MeshLinkQualityScorer].
 *
 * Implements asymmetric hysteresis:
 * - Rapid step-down: Drops codec bitrate immediately on link degradation (<200ms) to protect stream continuity.
 * - Conservative step-up: Demands [hysteresisUpgradeCycles] (default 3) consecutive pristine evaluations before upgrading.
 *
 * 100% pure Kotlin Multiplatform with zero Java/C dependencies (Rule 3).
 */
class AdaptiveBitrateEngine(
    val initialProfile: AudioProfile = AudioProfile.MEDIUM,
    val hysteresisUpgradeCycles: Int = 3,
    val defaultClusterPolicy: ClusterPolicy = ClusterPolicy.WORST_LINK
) {
    private val mutex = Mutex()

    private var activeProfile: AudioProfile = initialProfile
    private var candidateProfile: AudioProfile = initialProfile
    private var pristineCycleCount: Int = 0
    private var isPinned: Boolean = false
    private var pinnedProfile: AudioProfile? = null
    private var clusterPolicy: ClusterPolicy = defaultClusterPolicy
    private var lastReason: String = "Engine initialized"

    private val _currentProfile = MutableStateFlow(initialProfile)
    val currentProfile: StateFlow<AudioProfile> = _currentProfile.asStateFlow()

    private val _effectiveBitrateKbps = MutableStateFlow(initialProfile.bitrateKbps)
    val effectiveBitrateKbps: StateFlow<Int> = _effectiveBitrateKbps.asStateFlow()

    private val _effectiveFrameDurationMs = MutableStateFlow(initialProfile.frameSizeMs)
    val effectiveFrameDurationMs: StateFlow<Int> = _effectiveFrameDurationMs.asStateFlow()

    private val _telemetry = MutableStateFlow(
        BitrateEngineTelemetry(
            activeProfile = initialProfile,
            effectiveBitrateKbps = initialProfile.bitrateKbps,
            effectiveFrameDurationMs = initialProfile.frameSizeMs,
            candidateProfile = initialProfile,
            consecutivePristineCycles = 0,
            hysteresisThresholdCycles = hysteresisUpgradeCycles,
            isPinned = false,
            clusterPolicy = defaultClusterPolicy,
            governingScore = 100,
            governingGrade = LinkQualityGrade.EXCELLENT,
            lastTransitionReason = lastReason,
            timestampMs = 0L
        )
    )
    val telemetry: StateFlow<BitrateEngineTelemetry> = _telemetry.asStateFlow()

    /**
     * Evaluates link health telemetry and executes bitrate adaptation.
     *
     * @param clusterHealth Aggregated link quality report from [MeshLinkQualityScorer].
     * @param timestampMs Current local system timestamp in milliseconds.
     * @return The updated active [AudioProfile].
     */
    suspend fun evaluateClusterHealth(
        clusterHealth: ClusterLinkHealth,
        timestampMs: Long
    ): AudioProfile = mutex.withLock {
        if (isPinned && pinnedProfile != null) {
            return@withLock activeProfile
        }

        val governingScore = if (clusterHealth.peerCount == 0) {
            100
        } else when (clusterPolicy) {
            ClusterPolicy.WORST_LINK -> clusterHealth.worstScore
            ClusterPolicy.CLUSTER_AVERAGE -> clusterHealth.averageScore
        }

        val targetProfile = mapScoreToProfile(governingScore)
        applyAdaptation(targetProfile, governingScore, timestampMs)
    }

    /**
     * Evaluates link quality from an individual [PeerLinkScore].
     */
    suspend fun evaluatePeerScore(
        peerScore: PeerLinkScore,
        timestampMs: Long
    ): AudioProfile = mutex.withLock {
        if (isPinned && pinnedProfile != null) {
            return@withLock activeProfile
        }

        val targetProfile = mapScoreToProfile(peerScore.score)
        applyAdaptation(targetProfile, peerScore.score, timestampMs)
    }

    /**
     * Evaluates an explicit raw Q_link numerical score in [0, 100].
     */
    suspend fun evaluateScore(
        qLinkScore: Int,
        timestampMs: Long
    ): AudioProfile = mutex.withLock {
        if (isPinned && pinnedProfile != null) {
            return@withLock activeProfile
        }

        val targetProfile = mapScoreToProfile(qLinkScore)
        applyAdaptation(targetProfile, qLinkScore, timestampMs)
    }

    private fun mapScoreToProfile(score: Int): AudioProfile = when {
        score >= 85 -> AudioProfile.HIGH
        score >= 70 -> AudioProfile.MEDIUM
        score >= 50 -> AudioProfile.LOW
        else -> AudioProfile.ULTRA_LOW
    }

    private fun applyAdaptation(
        targetProfile: AudioProfile,
        governingScore: Int,
        timestampMs: Long
    ): AudioProfile {
        if (targetProfile == activeProfile) {
            candidateProfile = targetProfile
            pristineCycleCount = 0
            publishState(governingScore, timestampMs, "Stable at ${activeProfile.name}")
            return activeProfile
        }

        // Downshift is immediate (rapid step-down to prevent audio loss)
        if (targetProfile.bitrateKbps < activeProfile.bitrateKbps) {
            val oldProfile = activeProfile
            activeProfile = targetProfile
            candidateProfile = targetProfile
            pristineCycleCount = 0
            lastReason = "Rapid step-down: $oldProfile -> $activeProfile (Q_link: $governingScore)"
            publishState(governingScore, timestampMs, lastReason)
            return activeProfile
        }

        // Upshift requires sustained pristine condition across hysteresis cycles
        if (targetProfile.bitrateKbps > activeProfile.bitrateKbps) {
            if (candidateProfile == targetProfile) {
                pristineCycleCount++
            } else {
                candidateProfile = targetProfile
                pristineCycleCount = 1
            }

            if (pristineCycleCount >= hysteresisUpgradeCycles) {
                val oldProfile = activeProfile
                activeProfile = targetProfile
                pristineCycleCount = 0
                lastReason = "Conservative step-up: $oldProfile -> $activeProfile after $hysteresisUpgradeCycles pristine cycles"
                publishState(governingScore, timestampMs, lastReason)
                return activeProfile
            } else {
                lastReason = "Hysteresis dampening: candidate $targetProfile ($pristineCycleCount/$hysteresisUpgradeCycles cycles)"
                publishState(governingScore, timestampMs, lastReason)
                return activeProfile
            }
        }

        return activeProfile
    }

    /**
     * Pins the codec to a specific profile, bypassing closed-loop RF adaptation.
     */
    suspend fun pinProfile(profile: AudioProfile, timestampMs: Long) = mutex.withLock {
        isPinned = true
        pinnedProfile = profile
        activeProfile = profile
        candidateProfile = profile
        pristineCycleCount = 0
        lastReason = "Manual override: pinned to ${profile.name}"
        publishState(100, timestampMs, lastReason)
    }

    /**
     * Releases manual pin and returns to autonomous closed-loop RF adaptation.
     */
    suspend fun releasePin(timestampMs: Long) = mutex.withLock {
        isPinned = false
        pinnedProfile = null
        candidateProfile = activeProfile
        pristineCycleCount = 0
        lastReason = "Manual override released: resumed autonomous closed-loop adaptation"
        publishState(100, timestampMs, lastReason)
    }

    /**
     * Sets the multi-peer cluster aggregation policy.
     */
    suspend fun setClusterPolicy(policy: ClusterPolicy) = mutex.withLock {
        clusterPolicy = policy
    }

    private fun publishState(score: Int, timestampMs: Long, reason: String) {
        _currentProfile.value = activeProfile
        _effectiveBitrateKbps.value = activeProfile.bitrateKbps
        _effectiveFrameDurationMs.value = activeProfile.frameSizeMs

        _telemetry.value = BitrateEngineTelemetry(
            activeProfile = activeProfile,
            effectiveBitrateKbps = activeProfile.bitrateKbps,
            effectiveFrameDurationMs = activeProfile.frameSizeMs,
            candidateProfile = candidateProfile,
            consecutivePristineCycles = pristineCycleCount,
            hysteresisThresholdCycles = hysteresisUpgradeCycles,
            isPinned = isPinned,
            clusterPolicy = clusterPolicy,
            governingScore = score,
            governingGrade = LinkQualityGrade.fromScore(score),
            lastTransitionReason = reason,
            timestampMs = timestampMs
        )
    }
}
