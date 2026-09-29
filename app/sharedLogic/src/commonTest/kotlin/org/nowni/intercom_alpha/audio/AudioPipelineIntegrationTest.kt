package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.test.runTest
import org.nowni.intercom_alpha.common.AudioProfile
import org.nowni.intercom_alpha.mesh.MeshPacket
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioPipelineIntegrationTest {

    private fun generateSineWave(freq: Double, sampleRate: Int, samples: Int, amplitude: Short = 16000): ShortArray {
        val result = ShortArray(samples)
        for (i in 0 until samples) {
            val angle = 2.0 * PI * freq * i / sampleRate
            result[i] = (sin(angle) * amplitude).toInt().toShort()
        }
        return result
    }

    private fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) {
            sum += s.toDouble() * s.toDouble()
        }
        return sqrt(sum / samples.size)
    }

    @Test
    fun testFullDuplexCodecRoundtrip() = runTest {
        val session = AudioPipelineSession(profile = AudioProfile.MEDIUM)
        val speech = generateSineWave(440.0, 24000, 480, amplitude = 12000)

        // 1. Mic capture through noise gate + codec
        val compressed = session.processOutgoingMicrophone(speech)
        assertNotNull(compressed, "Speech above threshold must produce compressed packet")
        assertTrue(compressed.size in 100..150, "Compressed size (${compressed.size}) must fit BLE MTU")

        // 2. Ingest into receiver jitter buffer
        val packet = MeshPacket.Audio(
            senderId = "node-alpha",
            sequence = 1L,
            timestamp = 1000L,
            data = compressed,
            profile = AudioProfile.MEDIUM
        )
        session.ingestIncomingPacket(packet, arrivalTimestampMs = 1000L)

        // Advance clock past initial target delay (target delay is ~50ms, so tick at t=1060ms)
        val played = session.pullPlayoutFrame(currentTimeMs = 1060L)
        assertEquals(480, played.size)
        assertTrue(calculateRms(played) > 3000.0, "Playout frame must contain restored audio energy")
    }

    @Test
    fun testContinuousAudioPlayoutUnderLossAndJitter() = runTest {
        val session = AudioPipelineSession(profile = AudioProfile.MEDIUM)
        val totalFrames = 50 // 1000ms of continuous 20ms audio frames
        val packets = ArrayList<MeshPacket.Audio>()

        // Pre-generate 50 continuous audio frames
        for (i in 1..totalFrames) {
            val pcm = generateSineWave(350.0 + (i % 5) * 50.0, 24000, 480, amplitude = 14000)
            val encoded = session.codec.encode(pcm, AudioProfile.MEDIUM)
            packets.add(
                MeshPacket.Audio(
                    senderId = "node-peer",
                    sequence = i.toLong(),
                    timestamp = i * 20L,
                    data = encoded,
                    profile = AudioProfile.MEDIUM
                )
            )
        }

        // Simulate mesh network delivery: 20% loss (10 frames dropped) + random jitter (0..50ms)
        val random = Random(42) // Fixed seed for reproducible test
        val scheduledArrivals = ArrayList<Pair<MeshPacket.Audio, Long>>()

        for (i in 0 until totalFrames) {
            val isDropped = (i % 5 == 2) // Exactly 20% packet drop rate
            if (!isDropped) {
                val packet = packets[i]
                val jitterMs = random.nextInt(0, 50).toLong()
                val arrivalTime = packet.timestamp + jitterMs
                scheduledArrivals.add(Pair(packet, arrivalTime))
            }
        }

        var continuousPlayoutFrames = 0
        var totalConcealedFrames = 0
        var arrivalCursor = 0

        // Playout loop runs at steady 20ms clock ticks from t=0 to t=1300ms
        for (tick in 1..65) {
            val currentTimeMs = tick * 20L

            // Feed all packets that have arrived by currentTimeMs
            while (arrivalCursor < scheduledArrivals.size && scheduledArrivals[arrivalCursor].second <= currentTimeMs) {
                val (packet, arrivalTime) = scheduledArrivals[arrivalCursor]
                session.ingestIncomingPacket(packet, arrivalTime)
                arrivalCursor++
            }

            // Pull playout frame at current clock tick
            val frame = session.pullPlayoutFrame(currentTimeMs)
            assertEquals(480, frame.size, "Every playout tick must output exactly 480 samples")

            val rms = calculateRms(frame)
            if (rms > 500.0) {
                continuousPlayoutFrames++
            }
            if (session.plc.consecutiveLossCount > 0) {
                totalConcealedFrames++
            }
        }

        // Verify: Even with 20% drops and 50ms network jitter, the user experienced smooth audio
        assertTrue(continuousPlayoutFrames >= 35, "Playout must maintain voice continuity ($continuousPlayoutFrames >= 35 frames)")
        assertTrue(totalConcealedFrames > 0, "PLC must have actively concealed the dropped frames ($totalConcealedFrames concealed)")
    }

    @Test
    fun testDtxSuppressesBackgroundSilence() {
        val session = AudioPipelineSession(profile = AudioProfile.MEDIUM)

        // Extremely low background wind noise (-65 dBFS)
        val windNoise = generateSineWave(80.0, 24000, 480, amplitude = 50)
        val packet = session.processOutgoingMicrophone(windNoise)

        // DTX must trigger and return null to prevent mesh airtime saturation
        assertNull(packet, "DTX must suppress transmission during background silence")

        // Once loud speech arrives, gate opens and packets resume
        val speech = generateSineWave(500.0, 24000, 480, amplitude = 12000)
        val speechPacket = session.processOutgoingMicrophone(speech)
        assertNotNull(speechPacket, "Speech must pass through DTX and generate packet")
    }

    @Test
    fun testMultipleAudioProfilesInPipeline() = runTest {
        val profiles = listOf(AudioProfile.ULTRA_LOW, AudioProfile.LOW, AudioProfile.MEDIUM, AudioProfile.HIGH)

        for (profile in profiles) {
            val session = AudioPipelineSession(profile = profile)
            val samples = (profile.sampleRateHz * profile.frameSizeMs) / 1000
            val pcm = generateSineWave(440.0, profile.sampleRateHz, samples, amplitude = 10000)

            val packet = session.processOutgoingMicrophone(pcm)
            assertNotNull(packet, "Profile ${profile.name} must encode audio")
            assertTrue(packet.size <= 260, "Profile ${profile.name} size (${packet.size}) must fit single MTU")

            session.ingestIncomingPacket(
                MeshPacket.Audio(
                    senderId = "node-1",
                    sequence = 1L,
                    timestamp = 100L,
                    data = packet,
                    profile = profile
                ),
                arrivalTimestampMs = 100L
            )

            // Tick past delay
            val delay = session.jitterBuffer.currentTargetDelayMs
            val played = session.pullPlayoutFrame(currentTimeMs = 100L + delay + 10L)
            assertEquals(samples, played.size, "Profile ${profile.name} must output exactly $samples samples")
        }
    }
}
