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
 * Spuerbare Ansagen fuers Abbiegen - am Lenker schaut man nicht staendig aufs Display.
 * Weit vorher einmal kurz, kurz davor zweimal, am Ziel dreimal lang.
 */
class TurnSignals(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    // ToneGenerator kann auf manchen Geraeten fehlschlagen (Audio belegt) - dann eben ohne Ton.
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

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
            val t = if (alert == TurnAlert.FAR) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
            runCatching { tone?.startTone(t, 300) }
        }
    }

    fun release() {
        tone?.release()
    }
}
