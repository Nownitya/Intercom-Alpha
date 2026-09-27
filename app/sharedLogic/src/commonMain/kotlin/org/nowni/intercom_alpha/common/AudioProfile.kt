package org.nowni.intercom_alpha.common

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class AudioProfile(val bitrateKbps: Int, val frameSizeMs: Int, val sampleRateHz: Int, val channels: Int, val description: String) {
    ULTRA_LOW(16, 60, 16000, 1, "Ultra Low (16 kbps) - Maximum range"),
    LOW(24, 60, 16000, 1, "Low (24 kbps) - Long range"),
    MEDIUM(32, 20, 24000, 1, "Medium (32 kbps) - Balanced"),
    HIGH(48, 20, 24000, 1, "High (48 kbps) - Clear voice"),
    MUSIC(64, 20, 48000, 2, "Music (64 kbps) - Stereo music");

    companion object {
        fun fromBitrate(bitrate: Int): AudioProfile = values().minByOrNull { abs(it.bitrateKbps - bitrate) } ?: MEDIUM
        val default: AudioProfile = MEDIUM
    }
}

@Serializable
data class AudioConfig(
    val profile: AudioProfile = AudioProfile.default,
    val enableVox: Boolean = false,
    val voxThresholdDb: Float = -40f,
    val enableDtx: Boolean = true,
    val enableFec: Boolean = true
)