package com.pbrockt.fahrradcomputer.recording

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pbrockt.fahrradcomputer.routes.TurnAlert

/**
 * Spuerbare und hoerbare Hinweise fuers Abbiegen - am Lenker schaut man nicht staendig aufs
 * Display. Vibration: weit vorher einmal kurz, kurz davor zweimal, am Ziel dreimal lang.
 * Gepiepst wird nur, wenn man reagieren soll: jetzt abbiegen (zwei Pieptoene), abseits der
 * Route (ein langer tiefer Ton) und am Ziel (drei). Lautstaerke = Medienlautstaerke.
 */
class TurnSignals(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    // ToneGenerator kann auf manchen Geraeten fehlschlagen (Audio belegt) - dann eben ohne Ton.
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun signal(alert: TurnAlert, vibrate: Boolean, sound: Boolean) {
        if (vibrate) {
            val pattern = when (alert) {
                TurnAlert.FAR -> longArrayOf(0, 200)
                TurnAlert.NOW -> longArrayOf(0, 250, 150, 250)
                TurnAlert.ARRIVED -> longArrayOf(0, 500, 200, 500, 200, 500)
            }
            runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
        }
        if (sound) {
            when (alert) {
                TurnAlert.FAR -> Unit
                TurnAlert.NOW -> beeps(2)
                TurnAlert.ARRIVED -> beeps(3)
            }
        }
    }

    /** Von der Route abgekommen: ein langer, tiefer Ton und eine lange Vibration. */
    fun offRoute(vibrate: Boolean, sound: Boolean) {
        if (vibrate) runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE)) }
        if (sound) runCatching { tone?.startTone(ToneGenerator.TONE_CDMA_LOW_L, 900) }
    }

    /** Gleich kommt ein Anstieg: ein kurzer hoher Doppelton, zweimal kurz vibrieren. */
    fun climb(vibrate: Boolean, sound: Boolean) {
        if (vibrate) runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1)) }
        if (sound) {
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300) }
        }
    }

    /** Zum Ausprobieren in den Einstellungen. */
    fun test() = beeps(2)

    private fun beeps(count: Int) {
        for (i in 0 until count) {
            handler.postDelayed({ runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } }, i * 280L)
        }
    }

    fun release() {
        tone?.release()
    }
}
