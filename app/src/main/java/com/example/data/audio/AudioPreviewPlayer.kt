package com.example.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

object AudioPreviewPlayer {
    private var currentTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId: StateFlow<String?> = _currentlyPlayingId.asStateFlow()

    fun isPlaying(assetId: String): Boolean = _currentlyPlayingId.value == assetId

    fun togglePlayAsset(assetId: String) {
        if (_currentlyPlayingId.value == assetId) {
            stop()
        } else {
            playAsset(assetId)
        }
    }

    fun playAsset(assetId: String) {
        stop()

        playbackJob = scope.launch {
            _currentlyPlayingId.value = assetId
            try {
                val sampleRate = 44100
                val samples = generateSamplesForAsset(assetId, sampleRate)

                val bufferSize = samples.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                currentTrack = track
                track.write(samples, 0, samples.size)
                track.play()

                val durationMs = (samples.size.toFloat() / sampleRate * 1000).toLong()
                delay(durationMs + 100)
            } catch (e: Exception) {
                Log.e("AudioPreviewPlayer", "Error playing preview: ${e.message}")
            } finally {
                if (_currentlyPlayingId.value == assetId) {
                    _currentlyPlayingId.value = null
                }
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            currentTrack?.stop()
            currentTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        currentTrack = null
        _currentlyPlayingId.value = null
    }

    private fun generateSamplesForAsset(assetId: String, sampleRate: Int): ShortArray {
        return when (assetId) {
            "sfx-1" -> generateWhoosh(sampleRate, 0.45f)
            "sfx-2" -> generateCashRegister(sampleRate, 0.85f)
            "sfx-3" -> generateVinylScratch(sampleRate, 0.65f)
            "sfx-4" -> generateSubBass808(sampleRate, 1.25f)
            "sfx-pop" -> generatePopBubble(sampleRate, 0.22f)
            "sfx-camera" -> generateCameraSnap(sampleRate, 0.35f)
            "sfx-boom" -> generateCinematicBoom(sampleRate, 1.4f)
            "sfx-glitch" -> generateGlitch(sampleRate, 0.5f)
            "bgm-1" -> generatePhonkBeat(sampleRate, 3.2f)
            "bgm-2" -> generateLofiBeat(sampleRate, 3.2f)
            "bgm-3" -> generateCinematicPulse(sampleRate, 3.0f)
            "bgm-4" -> generateTrapBoom(sampleRate, 3.0f)
            else -> generatePopBubble(sampleRate, 0.3f)
        }
    }

    // Heavy Whoosh: Swept noise and low-mid swoosh
    private fun generateWhoosh(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)
        var lastNoise = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            val envelope = sin(progress * PI)

            // Frequency sweep 300Hz -> 1600Hz -> 400Hz
            val freq = 300.0 + 1300.0 * sin(progress * PI)
            val sinePart = sin(2.0 * PI * freq * t) * 0.4

            // Filtered white noise
            val rawNoise = Random.nextDouble(-1.0, 1.0)
            lastNoise = lastNoise * 0.85 + rawNoise * 0.15

            val mixed = (sinePart + lastNoise * 0.6) * envelope
            buffer[i] = (mixed.coerceIn(-1.0, 1.0) * 28000).toInt().toShort()
        }
        return buffer
    }

    // Ka-Ching / Cash Register: Dual chime bells + coin rattling
    private fun generateCashRegister(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            // Main bell at 0.0s
            if (t >= 0.0) {
                val dt = t
                val decay1 = exp(-6.0 * dt)
                sample += (sin(2.0 * PI * 1760.0 * dt) * 0.5 + sin(2.0 * PI * 2640.0 * dt) * 0.4) * decay1
            }

            // Coin drop 1 at 0.18s
            if (t >= 0.18) {
                val dt = t - 0.18
                val decay = exp(-12.0 * dt)
                sample += sin(2.0 * PI * 3520.0 * dt) * 0.3 * decay
            }

            // Coin drop 2 at 0.28s
            if (t >= 0.28) {
                val dt = t - 0.28
                val decay = exp(-15.0 * dt)
                sample += sin(2.0 * PI * 4400.0 * dt) * 0.25 * decay
            }

            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 27000).toInt().toShort()
        }
        return buffer
    }

    // Pop Bubble: Rapid upward pitch chirp
    private fun generatePopBubble(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            val freq = 260.0 + 650.0 * progress
            val env = exp(-18.0 * progress) * sin(progress * PI)
            val sample = sin(2.0 * PI * freq * t) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return buffer
    }

    // Sub Bass 808: Deep booming sine sliding downward
    private fun generateSubBass808(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            // Pitch falls from 135Hz down to 42Hz
            val freq = 135.0 * exp(-2.2 * progress)
            val env = exp(-1.8 * progress)

            var sample = sin(2.0 * PI * freq * t) * env
            // Soft saturation for warm punch
            sample = tanh(sample * 1.8) * 0.85
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return buffer
    }

    // Vinyl Scratch: Modulated pitch with flutter
    private fun generateVinylScratch(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            val freq = 450.0 + 320.0 * sin(progress * 8.0 * PI)
            val noise = Random.nextDouble(-0.3, 0.3)
            val env = (1.0 - progress).coerceAtLeast(0.0)
            val sample = (sin(2.0 * PI * freq * t) * 0.7 + noise) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 27000).toInt().toShort()
        }
        return buffer
    }

    // Camera Snap
    private fun generateCameraSnap(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var s = 0.0
            // Shutter click 1 at 0s
            if (t < 0.05) {
                s += Random.nextDouble(-1.0, 1.0) * exp(-80.0 * t)
            }
            // Motor/mirror snap at 0.08s
            if (t in 0.08..0.18) {
                val dt = t - 0.08
                s += (sin(2.0 * PI * 880.0 * dt) * 0.6 + Random.nextDouble(-0.5, 0.5)) * exp(-35.0 * dt)
            }
            buffer[i] = (s.coerceIn(-1.0, 1.0) * 28000).toInt().toShort()
        }
        return buffer
    }

    // Cinematic Boom
    private fun generateCinematicBoom(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val progress = t / durationSec
            val freq = 80.0 * exp(-1.2 * progress) + 30.0
            val sub = sin(2.0 * PI * freq * t) * exp(-1.5 * progress)
            val impact = if (t < 0.08) Random.nextDouble(-1.0, 1.0) * exp(-30.0 * t) * 0.5 else 0.0
            val sample = sub * 0.8 + impact
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return buffer
    }

    // Digital Glitch
    private fun generateGlitch(sampleRate: Int, durationSec: Float): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val step = ((t * 20).toInt()) % 4
            val freq = when (step) {
                0 -> 440.0
                1 -> 880.0
                2 -> 220.0
                else -> 1200.0
            }
            val square = if (sin(2.0 * PI * freq * t) > 0) 0.5 else -0.5
            val noise = Random.nextDouble(-0.3, 0.3)
            val env = 1.0 - (t / durationSec)
            val s = (square + noise) * env
            buffer[i] = (s.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
        }
        return buffer
    }

    // Phonk Gym Motivation Beat Preview (Rhythmic punch loop)
    private fun generatePhonkBeat(sampleRate: Int, durationSec: Float): ShortArray {
        return generateDrumGroove(sampleRate, durationSec, bpm = 132, bassFreq = 55.0, cowbell = true)
    }

    // Lo-Fi Chill Beat Preview
    private fun generateLofiBeat(sampleRate: Int, durationSec: Float): ShortArray {
        return generateDrumGroove(sampleRate, durationSec, bpm = 84, bassFreq = 65.0, cowbell = false)
    }

    // Cinematic Pulse
    private fun generateCinematicPulse(sampleRate: Int, durationSec: Float): ShortArray {
        return generateDrumGroove(sampleRate, durationSec, bpm = 110, bassFreq = 48.0, cowbell = false)
    }

    // Trap Viral Boom
    private fun generateTrapBoom(sampleRate: Int, durationSec: Float): ShortArray {
        return generateDrumGroove(sampleRate, durationSec, bpm = 140, bassFreq = 50.0, cowbell = false)
    }

    private fun generateDrumGroove(
        sampleRate: Int,
        durationSec: Float,
        bpm: Int,
        bassFreq: Double,
        cowbell: Boolean
    ): ShortArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)
        val beatIntervalSec = 60.0 / bpm

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var s = 0.0

            val beatTime = t % beatIntervalSec
            val currentBeatIndex = (t / beatIntervalSec).toInt() % 4

            // Kick drum on beat 0 and beat 2
            if (currentBeatIndex == 0 || currentBeatIndex == 2) {
                if (beatTime < 0.25) {
                    val kickFreq = 120.0 * exp(-18.0 * beatTime) + 45.0
                    val kick = sin(2.0 * PI * kickFreq * beatTime) * exp(-8.0 * beatTime)
                    s += kick * 0.7
                }
            }

            // Snare on beat 1 and beat 3
            if (currentBeatIndex == 1 || currentBeatIndex == 3) {
                if (beatTime < 0.22) {
                    val snareNoise = Random.nextDouble(-1.0, 1.0) * exp(-16.0 * beatTime)
                    val snareBody = sin(2.0 * PI * 185.0 * beatTime) * exp(-12.0 * beatTime)
                    s += (snareNoise * 0.5 + snareBody * 0.4)
                }
            }

            // Hi-hat every 8th note
            val halfBeatTime = t % (beatIntervalSec / 2.0)
            if (halfBeatTime < 0.04) {
                val hat = Random.nextDouble(-1.0, 1.0) * exp(-60.0 * halfBeatTime)
                s += hat * 0.2
            }

            // Phonk cowbell syncopation
            if (cowbell && (currentBeatIndex == 1 || currentBeatIndex == 3)) {
                if (beatTime in 0.12..0.28) {
                    val dt = beatTime - 0.12
                    val cb = (sin(2.0 * PI * 587.0 * dt) + sin(2.0 * PI * 845.0 * dt)) * exp(-12.0 * dt)
                    s += cb * 0.25
                }
            }

            // Bass pulse
            val bass = sin(2.0 * PI * bassFreq * t) * 0.35
            s += bass

            buffer[i] = (s.coerceIn(-1.0, 1.0) * 28000).toInt().toShort()
        }
        return buffer
    }
}
