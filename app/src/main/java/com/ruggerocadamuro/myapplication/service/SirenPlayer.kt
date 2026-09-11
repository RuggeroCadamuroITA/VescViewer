package com.ruggerocadamuro.myapplication.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sirena sintetizzata: genera un file WAV (onda sinusoidale con sweep
 * 600 -> 1300 Hz a ciclo continuo, classica "wail") nella cacheDir al primo
 * uso e lo riproduce in loop con MediaPlayer.
 *
 * Punti chiave:
 *  - AudioAttributes con USAGE_ALARM: la sirena va sul canale "sveglia",
 *    che NON viene mutato dalla modalita' silenziosa/Non disturbare standard
 *    (a meno che l'utente non silenzi esplicitamente anche gli allarmi).
 *  - Volume dello stream ALARM portato al massimo per la durata della sirena
 *    e ripristinato alla fine.
 *  - Audio focus richiesto in modalita' esclusiva per farsi sentire sugli
 *    altri media.
 */
class SirenPlayer(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 22050
        private const val DURATION_S = 3.0
        private const val FREQ_MIN = 600.0
        private const val FREQ_MAX = 1300.0
        private const val WAV_FILE = "siren.wav"
    }

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var previousAlarmVolume: Int = -1

    /** Genera (una sola volta) il WAV della sirena e ne restituisce il file. */
    private fun ensureWavFile(): File {
        val file = File(context.cacheDir, WAV_FILE)
        if (file.exists() && file.length() > 44) return file

        val samples = (SAMPLE_RATE * DURATION_S).toInt()
        val data = ByteArray(samples * 2)
        var phase = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            // sweep triangolare: sale e scende linearmente nel periodo
            val frac = (t % DURATION_S) / DURATION_S
            val f = if (frac < 0.5) {
                FREQ_MIN + (FREQ_MAX - FREQ_MIN) * (frac * 2.0)
            } else {
                FREQ_MAX - (FREQ_MAX - FREQ_MIN) * ((frac - 0.5) * 2.0)
            }
            phase += 2.0 * PI * f / SAMPLE_RATE
            val v = (sin(phase) * 0.85 * Short.MAX_VALUE).toInt()
            data[i * 2] = (v and 0xFF).toByte()
            data[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        writeWav(file, data)
        return file
    }

    /** Scrive un WAV PCM 16-bit mono con header RIFF standard. */
    private fun writeWav(file: File, pcm: ByteArray) {
        val totalLen = pcm.size + 36
        FileOutputStream(file).use { out ->
            out.write("RIFF".toByteArray())
            out.write(int32(totalLen))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(int32(16))                       // chunk PCM
            out.write(int16(1))                        // formato PCM
            out.write(int16(1))                        // canali: mono
            out.write(int32(SAMPLE_RATE))
            out.write(int32(SAMPLE_RATE * 2))          // byte rate
            out.write(int16(2))                        // block align
            out.write(int16(16))                       // bit per campione
            out.write("data".toByteArray())
            out.write(int32(pcm.size))
            out.write(pcm)
        }
    }

    private fun int32(v: Int) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    private fun int16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    /** Avvia la sirena (idempotente). */
    fun start() {
        if (player?.isPlaying == true) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // volume stream ALARM al massimo (e ripristino allo stop)
        if (previousAlarmVolume < 0) previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }

        // audio focus esclusivo: gli altri contenuti audio si abbassano
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus({ }, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
        }

        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(ensureWavFile().absolutePath)
            mp.isLooping = true
            mp.setVolume(1f, 1f)
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
            player = null
        }
    }

    /** Ferma la sirena e rilascia le risorse (idempotente). */
    fun stop() {
        player?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus({ })
        }
        if (previousAlarmVolume >= 0) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0) }
            previousAlarmVolume = -1
        }
    }
}
