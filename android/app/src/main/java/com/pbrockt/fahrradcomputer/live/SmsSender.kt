package com.pbrockt.fahrradcomputer.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Verschickt SMS ueber den Handytarif. Laeuft ohne Internet - Mobilfunk genuegt.
 * Lange Texte werden automatisch in mehrere Teile zerlegt.
 */
class SmsSender(private val context: Context) {

    fun canSend(): Boolean {
        // Die genauere Kennung "kann SMS" gibt es erst ab Android 13; davor nur "hat Mobilfunk".
        val feature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.FEATURE_TELEPHONY_MESSAGING
        } else {
            PackageManager.FEATURE_TELEPHONY
        }
        return context.packageManager.hasSystemFeature(feature) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    /** Schickt [text] an alle [numbers]. Gibt die Zahl der erfolgreich uebergebenen SMS zurueck. */
    fun send(numbers: List<String>, text: String): Int {
        if (!canSend()) return 0
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return 0
        val parts = sms.divideMessage(text)
        return numbers.count { number ->
            runCatching { sms.sendMultipartTextMessage(number, null, parts, null, null) }.isSuccess
        }
    }

    fun batteryPercent(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
}
