package org.nowni.intercom_alpha.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin Multiplatform Real-Time Spectral Subtraction Noise Suppressor.
 *
 * Implements frequency-domain noise reduction designed for helmet intercoms:
 * 1. Radix-2 Cooley-Tukey FFT / IFFT with zero per-frame runtime allocations.
 * 2. Symmetric square-root Hanning analysis and synthesis windows with 50% Overlap-Add (OLA)
 *    guaranteeing exact unity gain reconstruction under bypass.
 * 3. Continuous recursive tracking of the noise power spectral density (PSD) with speech presence detection.
 * 4. Berouti over-subtraction factor (alpha) and spectral floor parameter (beta) eliminating
 *    residual musical noise while preserving speech clarity in 80-120 km/h wind conditions.
 *
 * Conforms to Project Architectural Rules 2 & 3: 100% pure Kotlin, zero Java/C dependencies.
 *
 * @param sampleRateHz Audio sampling frequency in Hz (typically 16000 or 24000). Default: 16000 Hz.
 * @param fftSize FFT window length (must be a power of 2, e.g. 256 or 512). Default: 256.
 * @param initialProfile Starting suppression profile. Default: [SpectralNoiseProfile.AGGRESSIVE].
 */
class SpectralNoiseSuppressor(
    val sampleRateHz: Int = 16000,
    val fftSize: Int = 256,
    initialProfile: SpectralNoiseProfile = SpectralNoiseProfile.AGGRESSIVE
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a positive power of 2, got $fftSize"
        }
    }

    private val mutex = Mutex()
    private val hopSize = fftSize / 2
    private val numBins = fftSize / 2 + 1
    private val binResolutionHz = sampleRateHz.toFloat() / fftSize

    var profile: SpectralNoiseProfile = initialProfile
        private set

    // Windows
    private val sqrtHannWindow = FloatArray(fftSize) { n ->
        val hann = 0.5f - 0.5f * cos((2.0 * PI * n) / fftSize).toFloat()
        sqrt(max(0f, hann))
    }

    // FFT Precomputations (Bit reversal & Twiddle factors)
    private val bitReversalIndices = IntArray(fftSize) { i ->
        var rev = 0
        var temp = i
        var bits = 0
        var n = fftSize
        while (n > 1) {
            bits++
            n = n shr 1
        }
        for (b in 0 until bits) {
            rev = (rev shl 1) or (temp and 1)
            temp = temp shr 1
        }
        rev
    }

    private val twiddleCos = FloatArray(fftSize / 2) { k ->
        cos((-2.0 * PI * k) / fftSize).toFloat()
    }
    private val twiddleSin = FloatArray(fftSize / 2) { k ->
        sin((-2.0 * PI * k) / fftSize).toFloat()
    }

    // Scratch buffers (reused to ensure zero allocations per frame)
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val powerSpectrum = FloatArray(numBins)
    private val noisePowerSpectrum = FloatArray(numBins)
    private val spectralGain = FloatArray(numBins)

    // Streaming Overlap-Add buffers
    private val inputBuffer = FloatArray(fftSize)
    private var inputBufferedCount = 0
    private val synthesisBuffer = FloatArray(fftSize)

    // Speech & Noise Tracking
    private var isCalibrated = false
    private var calibrationFramesRemaining = 6
    private var totalFramesProcessed = 0L
    private var totalSpeechFrames = 0L
    private var totalNoiseFrames = 0L
    private var cumulativeSnrSum = 0.0

    private val _latestDiagnostics = MutableStateFlow(
        SpectralFrameDiagnostics(
            estimatedSnrDb = 0f,
            isSpeechPresent = false,
            averageNoisePower = 0f,
            inputRmsDb = -100f,
            outputRmsDb = -100f,
            attenuationDb = 0f
        )
    )
    val latestDiagnostics: StateFlow<SpectralFrameDiagnostics> = _latestDiagnostics.asStateFlow()

    /**
     * Updates active noise suppression profile.
     */
    suspend fun updateProfile(newProfile: SpectralNoiseProfile) = mutex.withLock {
        profile = newProfile
    }

    /**
     * Resets internal noise floor estimates and overlap-add history.
     */
    suspend fun reset() = mutex.withLock {
        inputBuffer.fill(0f)
        inputBufferedCount = 0
        synthesisBuffer.fill(0f)
        noisePowerSpectrum.fill(0f)
        isCalibrated = false
        calibrationFramesRemaining = 6
        totalFramesProcessed = 0L
        totalSpeechFrames = 0L
        totalNoiseFrames = 0L
        cumulativeSnrSum = 0.0
    }

    /**
     * Processes 16-bit PCM little-endian byte array, returning enhanced 16-bit PCM bytes.
     */
    suspend fun processPcmBytes(pcmBytes: ByteArray): ByteArray = mutex.withLock {
        if (pcmBytes.size < 2 || profile.mode == NoiseSuppressionMode.OFF) {
            return@withLock pcmBytes
        }

        val sampleCount = pcmBytes.size / 2
        val inputSamples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            inputSamples[i] = ((high shl 8) or low).toShort()
        }

        val enhancedSamples = processSamplesInternal(inputSamples)

        val outputBytes = ByteArray(enhancedSamples.size * 2)
        for (i in enhancedSamples.indices) {
            val sample = enhancedSamples[i].toInt()
            outputBytes[i * 2] = (sample and 0xFF).toByte()
            outputBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return@withLock outputBytes
    }

    /**
     * Processes an array of 16-bit audio samples, returning enhanced 16-bit samples.
     */
    suspend fun processSamples(samples: ShortArray): ShortArray = mutex.withLock {
        if (profile.mode == NoiseSuppressionMode.OFF) {
            return@withLock samples.copyOf()
        }
        return@withLock processSamplesInternal(samples)
    }

    /**
     * Internal streaming processing loop with 50% overlap-add framing.
     */
    private fun processSamplesInternal(samples: ShortArray): ShortArray {
        val output = ShortArray(samples.size)
        var outputWritten = 0

        for (sample in samples) {
            inputBuffer[inputBufferedCount++] = sample.toFloat() / 32768.0f

            if (inputBufferedCount == fftSize) {
                // Process 1 full window of fftSize
                processFftFrame()

                // Extract hopSize processed samples to output
                val toExtract = min(hopSize, samples.size - outputWritten)
                for (h in 0 until toExtract) {
                    val floatSample = synthesisBuffer[h]
                    val clamped = max(-1.0f, min(1.0f, floatSample))
                    output[outputWritten++] = (clamped * 32767.0f).toInt().toShort()
                }

                // Shift synthesis buffer by hopSize
                for (i in 0 until (fftSize - hopSize)) {
                    synthesisBuffer[i] = synthesisBuffer[i + hopSize]
                }
                for (i in (fftSize - hopSize) until fftSize) {
                    synthesisBuffer[i] = 0f
                }

                // Shift input buffer by hopSize (50% overlap)
                for (i in 0 until (fftSize - hopSize)) {
                    inputBuffer[i] = inputBuffer[i + hopSize]
                }
                inputBufferedCount = fftSize - hopSize
            }
        }

        // If output buffer is not fully filled due to filter latency, pass through buffered level
        while (outputWritten < samples.size) {
            val fallbackIndex = outputWritten % hopSize
            val floatSample = synthesisBuffer[fallbackIndex]
            val clamped = max(-1.0f, min(1.0f, floatSample))
            output[outputWritten++] = (clamped * 32767.0f).toInt().toShort()
        }

        return output
    }

    /**
     * Executes spectral analysis, noise estimation, gain calculation, and synthesis for one frame.
     */
    private fun processFftFrame() {
        totalFramesProcessed++

        // 1. Apply Analysis Window
        var inputEnergy = 0.0f
        for (i in 0 until fftSize) {
            val winVal = inputBuffer[i] * sqrtHannWindow[i]
            fftReal[i] = winVal
            fftImag[i] = 0.0f
            inputEnergy += winVal * winVal
        }

        // 2. Forward FFT
        forwardFft(fftReal, fftImag)

        // 3. Compute Power Spectrum
        var totalPower = 0.0f
        for (k in 0 until numBins) {
            val re = fftReal[k]
            val im = fftImag[k]
            val power = re * re + im * im
            powerSpectrum[k] = power
            totalPower += power
        }

        // 4. Initial Noise Floor Calibration
        if (!isCalibrated) {
            for (k in 0 until numBins) {
                noisePowerSpectrum[k] = max(1e-9f, powerSpectrum[k])
            }
            calibrationFramesRemaining--
            if (calibrationFramesRemaining <= 0) {
                isCalibrated = true
            }
        }

        // 5. SNR & Speech Presence Estimation
        var totalNoisePower = 0.0f
        for (k in 0 until numBins) {
            totalNoisePower += noisePowerSpectrum[k]
        }
        val avgNoise = max(1e-9f, totalNoisePower / numBins)
        val avgSignal = max(1e-9f, totalPower / numBins)
        val frameSnrRatio = avgSignal / avgNoise
        val estimatedSnrDb = (10.0f * log10(max(0.01f, frameSnrRatio)))
        cumulativeSnrSum += estimatedSnrDb

        val isSpeech = estimatedSnrDb >= profile.snrThresholdDb
        if (isSpeech) {
            totalSpeechFrames++
        } else {
            totalNoiseFrames++
        }

        // 6. Recursive Noise PSD Tracking
        val smoothingAlpha = profile.noiseSmoothingAlpha
        val speechMinBin = (profile.minSpeechFreqHz / binResolutionHz).toInt()
        val speechMaxBin = min(numBins - 1, (profile.maxSpeechFreqHz / binResolutionHz).toInt())

        for (k in 0 until numBins) {
            val isSpeechBand = k in speechMinBin..speechMaxBin
            if (!isSpeech || !isSpeechBand) {
                // Smooth noise spectrum update during speech pauses or out-of-speech bands
                noisePowerSpectrum[k] = smoothingAlpha * noisePowerSpectrum[k] + (1.0f - smoothingAlpha) * powerSpectrum[k]
            }
        }

        // 7. Spectral Subtraction & Gain Computation
        val alpha = profile.overSubtractionAlpha
        val beta = profile.spectralFloorBeta
        val sqrtBeta = sqrt(beta)

        for (k in 0 until numBins) {
            val currentPower = powerSpectrum[k]
            val estNoise = noisePowerSpectrum[k]

            // Subtraction with over-subtraction factor and spectral floor
            val subtractedPower = currentPower - alpha * estNoise
            val floorPower = beta * currentPower

            val cleanPower = max(subtractedPower, floorPower)
            val gain = if (currentPower > 1e-9f) {
                sqrt(cleanPower / currentPower)
            } else {
                sqrtBeta
            }

            // Protect speech formants from aggressive attenuation during active voice
            val effectiveGain = if (isSpeech && k in speechMinBin..speechMaxBin) {
                max(gain, 0.40f)
            } else {
                max(sqrtBeta, min(1.0f, gain))
            }

            spectralGain[k] = effectiveGain
        }

        // 8. Apply Gain in Frequency Domain
        for (k in 0 until numBins) {
            val g = spectralGain[k]
            fftReal[k] *= g
            fftImag[k] *= g

            // Conjugate symmetry for real-valued inverse FFT
            if (k > 0 && k < fftSize / 2) {
                fftReal[fftSize - k] = fftReal[k]
                fftImag[fftSize - k] = -fftImag[k]
            }
        }

        // 9. Inverse FFT
        inverseFft(fftReal, fftImag)

        // 10. Synthesis Windowing & Overlap-Add
        var outputEnergy = 0.0f
        for (i in 0 until fftSize) {
            val synthVal = fftReal[i] * sqrtHannWindow[i]
            synthesisBuffer[i] += synthVal
            outputEnergy += synthVal * synthVal
        }

        // 11. Emit Diagnostics
        val inRmsDb = 10.0f * log10(max(1e-9f, inputEnergy / fftSize))
        val outRmsDb = 10.0f * log10(max(1e-9f, outputEnergy / fftSize))
        val attenuationDb = max(0f, inRmsDb - outRmsDb)

        _latestDiagnostics.value = SpectralFrameDiagnostics(
            estimatedSnrDb = estimatedSnrDb,
            isSpeechPresent = isSpeech,
            averageNoisePower = avgNoise,
            inputRmsDb = inRmsDb,
            outputRmsDb = outRmsDb,
            attenuationDb = attenuationDb
        )
    }

    /**
     * In-place Radix-2 Cooley-Tukey forward FFT.
     */
    private fun forwardFft(real: FloatArray, imag: FloatArray) {
        // Bit-reversal permutation
        for (i in 0 until fftSize) {
            val j = bitReversalIndices[i]
            if (i < j) {
                val tempRe = real[i]
                real[i] = real[j]
                real[j] = tempRe

                val tempIm = imag[i]
                imag[i] = imag[j]
                imag[j] = tempIm
            }
        }

        // Cooley-Tukey butterfly stages
        var stageLen = 2
        while (stageLen <= fftSize) {
            val halfLen = stageLen shr 1
            val twiddleStep = fftSize / stageLen

            var i = 0
            while (i < fftSize) {
                var k = 0
                for (j in 0 until halfLen) {
                    val cosW = twiddleCos[k]
                    val sinW = twiddleSin[k]
                    k += twiddleStep

                    val idxB = i + j + halfLen
                    val idxA = i + j

                    val tr = cosW * real[idxB] - sinW * imag[idxB]
                    val ti = cosW * imag[idxB] + sinW * real[idxB]

                    real[idxB] = real[idxA] - tr
                    imag[idxB] = imag[idxA] - ti

                    real[idxA] += tr
                    imag[idxA] += ti
                }
                i += stageLen
            }
            stageLen = stageLen shl 1
        }
    }

    /**
     * In-place Radix-2 Cooley-Tukey inverse FFT with 1/N scaling.
     */
    private fun inverseFft(real: FloatArray, imag: FloatArray) {
        // Conjugate imaginary component
        for (i in 0 until fftSize) {
            imag[i] = -imag[i]
        }

        // Forward FFT on conjugated input
        forwardFft(real, imag)

        // Conjugate and scale by 1 / fftSize
        val scale = 1.0f / fftSize
        for (i in 0 until fftSize) {
            real[i] = real[i] * scale
            imag[i] = -imag[i] * scale
        }
    }

    /**
     * Returns cumulative performance statistics.
     */
    suspend fun getStats(): NoiseEstimationStats = mutex.withLock {
        val avgSnr = if (totalFramesProcessed > 0) {
            (cumulativeSnrSum / totalFramesProcessed).toFloat()
        } else 0f

        var totalNoise = 0.0f
        for (p in noisePowerSpectrum) totalNoise += p
        val noiseRmsDb = 10.0f * log10(max(1e-9f, totalNoise / numBins))

        return@withLock NoiseEstimationStats(
            framesProcessed = totalFramesProcessed,
            speechFramesCount = totalSpeechFrames,
            noiseFramesCount = totalNoiseFrames,
            averageSnrDb = avgSnr,
            currentNoiseFloorRmsDb = noiseRmsDb
        )
    }
}
