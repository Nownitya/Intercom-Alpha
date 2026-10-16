package org.nowni.intercom_alpha.audio

import kotlinx.serialization.Serializable

/**
 * Noise suppression aggressiveness preset.
 */
@Serializable
enum class NoiseSuppressionMode {
    /** Pass-through without spectral subtraction */
    OFF,

    /** Mild suppression for city commute (<50 km/h) with low acoustic distortion */
    MILD,

    /** Standard aggressive suppression for highway speeds (80-100 km/h) with heavy wind turbulence */
    AGGRESSIVE,

    /** Maximum suppression for high-speed open-face helmets or mountain alpine storms (>120 km/h) */
    EXTREME_ALPINE
}

/**
 * Parameter profile configuring the pure KMP spectral subtraction engine.
 *
 * @property mode Preset operating mode.
 * @property overSubtractionAlpha Multiplier on estimated noise spectrum power (typically 1.0 to 3.5).
 * @property spectralFloorBeta Minimum power ratio relative to input to prevent musical noise artifacts.
 * @property noiseSmoothingAlpha Recursive smoothing coefficient when updating noise estimate during speech pauses.
 * @property snrThresholdDb Threshold in dB above estimated noise floor to classify frame as containing active speech.
 * @property minSpeechFreqHz Minimum speech formant protection frequency in Hz.
 * @property maxSpeechFreqHz Maximum speech formant protection frequency in Hz.
 */
@Serializable
data class SpectralNoiseProfile(
    val mode: NoiseSuppressionMode = NoiseSuppressionMode.AGGRESSIVE,
    val overSubtractionAlpha: Float = 2.0f,
    val spectralFloorBeta: Float = 0.015f,
    val noiseSmoothingAlpha: Float = 0.95f,
    val snrThresholdDb: Float = 4.5f,
    val minSpeechFreqHz: Float = 300f,
    val maxSpeechFreqHz: Float = 3500f
) {
    companion object {
        val OFF = SpectralNoiseProfile(
            mode = NoiseSuppressionMode.OFF,
            overSubtractionAlpha = 0.0f,
            spectralFloorBeta = 1.0f,
            noiseSmoothingAlpha = 0.99f,
            snrThresholdDb = 10.0f
        )

        val MILD = SpectralNoiseProfile(
            mode = NoiseSuppressionMode.MILD,
            overSubtractionAlpha = 1.2f,
            spectralFloorBeta = 0.035f,
            noiseSmoothingAlpha = 0.92f,
            snrThresholdDb = 3.5f
        )

        val AGGRESSIVE = SpectralNoiseProfile(
            mode = NoiseSuppressionMode.AGGRESSIVE,
            overSubtractionAlpha = 2.2f,
            spectralFloorBeta = 0.012f,
            noiseSmoothingAlpha = 0.95f,
            snrThresholdDb = 4.5f
        )

        val EXTREME_ALPINE = SpectralNoiseProfile(
            mode = NoiseSuppressionMode.EXTREME_ALPINE,
            overSubtractionAlpha = 3.5f,
            spectralFloorBeta = 0.005f,
            noiseSmoothingAlpha = 0.97f,
            snrThresholdDb = 5.5f
        )

        fun forMode(mode: NoiseSuppressionMode): SpectralNoiseProfile = when (mode) {
            NoiseSuppressionMode.OFF -> OFF
            NoiseSuppressionMode.MILD -> MILD
            NoiseSuppressionMode.AGGRESSIVE -> AGGRESSIVE
            NoiseSuppressionMode.EXTREME_ALPINE -> EXTREME_ALPINE
        }
    }
}

/**
 * Diagnostic metrics emitted per processed audio frame.
 */
@Serializable
data class SpectralFrameDiagnostics(
    val estimatedSnrDb: Float,
    val isSpeechPresent: Boolean,
    val averageNoisePower: Float,
    val inputRmsDb: Float,
    val outputRmsDb: Float,
    val attenuationDb: Float
)

/**
 * Cumulative tracking statistics for the spectral noise estimation engine.
 */
@Serializable
data class NoiseEstimationStats(
    val framesProcessed: Long,
    val speechFramesCount: Long,
    val noiseFramesCount: Long,
    val averageSnrDb: Float,
    val currentNoiseFloorRmsDb: Float
)

/**
 * Real-time snapshot of spectral noise suppression filter state.
 */
@Serializable
data class SpectralNoiseSnapshot(
    val mode: NoiseSuppressionMode = NoiseSuppressionMode.AGGRESSIVE,
    val attenuationDb: Float = 0.0f,
    val isSpeechPresent: Boolean = false,
    val estimatedSnrDb: Float = 0.0f,
    val inputRmsDb: Float = -100.0f,
    val outputRmsDb: Float = -100.0f
)
