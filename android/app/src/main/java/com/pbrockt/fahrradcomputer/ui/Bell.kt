package com.pbrockt.fahrradcomputer.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Fahrradklingel, direkt berechnet statt als Tondatei: Eine Glocke klingt nach einigen
 * Teiltoenen ueber einem hohen Grundton, die unterschiedlich schnell ausklingen. Zwei
 * Anschlaege kurz hintereinander ergeben das typische "kling-kling".
 */
object BellSynth {

    const val SAMPLE_RATE = 44_100

    /** (Frequenz in Hz, Lautstaerke, Abklingzeit in s) - Teiltoene einer kleinen Glocke. */
    private val PARTIALS = listOf(
        Triple(2_600.0, 1.0, 0.55),
        Triple(3_950.0, 0.55, 0.35),
        Triple(6_190.0, 0.35, 0.22),
        Triple(8_060.0, 0.2, 0.12),
    )
    private val STRIKES = listOf(0.0, 0.17)
    private const val LENGTH_S = 1.0
    private const val ATTACK_S = 0.003

    fun ring(): ShortArray {
        val n = (SAMPLE_RATE * LENGTH_S).toInt()
        val buf = DoubleArray(n)
        for (start in STRIKES) {
            val first = (start * SAMPLE_RATE).toInt()
            for (i in first until n) {
                val t = (i - first).toDouble() / SAMPLE_RATE
                // Kurzer Anstieg statt Sprung - sonst knackt es.
                val attack = if (t < ATTACK_S) t / ATTACK_S else 1.0
                var v = 0.0
                for ((f, a, d) in PARTIALS) v += a * exp(-t / d) * sin(2 * PI * f * t)
                buf[i] += attack * v
            }
        }
        val peak = buf.maxOf { kotlin.math.abs(it) }.let { max(it, 1e-9) }
        return ShortArray(n) { (buf[it] / peak * 0.9 * Short.MAX_VALUE).toInt().toShort() }
    }
}

/** Spielt die Klingel ueber die Medien-Lautstaerke. */
class BellPlayer {

    private val samples by lazy { BellSynth.ring() }
    private var track: AudioTrack? = null

    fun ring() {
        val t = track ?: AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(BellSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size); track = it }
        runCatching {
            // Schnell nacheinander klingeln: von vorne beginnen statt zu warten.
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        track?.release()
        track = null
    }
}
