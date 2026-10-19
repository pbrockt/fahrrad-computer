package com.pbrockt.fahrradcomputer.trainer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pbrockt.fahrradcomputer.FahrradApp
import com.pbrockt.fahrradcomputer.MainActivity
import com.pbrockt.fahrradcomputer.R
import com.pbrockt.fahrradcomputer.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Vordergrunddienst fuer den Heimtrainer: rechnet jede Sekunde die Watt und schickt sie
 * (mit Kurbeldaten und Puls) an Zwift & Co. - auch bei ausgeschaltetem Display.
 * Die Bluetooth-Rechte holt die Oberflaeche vor dem Start.
 */
class TrainerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: FahrradApp
    private val crank = CrankSimulator()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as FahrradApp
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Heimtrainer", NotificationManager.IMPORTANCE_LOW)
        )
        startInForeground()
        app.trainer.start(System.currentTimeMillis())
        app.broadcaster.start()
        scope.launch { loop() }
    }

    private suspend fun loop() {
        var ticks = 0
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val hr = app.sensors.heartRate.value.fresh(now)
            val cad = app.sensors.cadence.value.fresh(now)
            val watts = app.trainer.tick(now, hr, cad)
            crank.update(now, cad ?: 0)
            app.broadcaster.sendPower(GattEncoders.powerMeasurement(watts, crank.takeIf { cad != null }))
            // Zwei Benachrichtigungen direkt hintereinander verschluckt Android gern.
            delay(300)
            hr?.let { app.broadcaster.sendHeartRate(GattEncoders.heartRateMeasurement(it)) }
            if (++ticks % 5 == 0) notificationManager().notify(NOTIFICATION_ID, buildNotification())
            delay(700)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        app.broadcaster.stop()
        app.trainer.stop()
        app.saveTrainerGain()
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("InlinedApi")
    private fun startInForeground() {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun buildNotification(): Notification {
        val s = app.trainer.stats.value
        val text = String.format(
            Locale.GERMANY, "%d W · %s · %.0f kcal", s.watts, formatDuration(s.elapsedMs), s.calories,
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Heimtrainer sendet")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notificationManager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "heimtrainer"
        private const val NOTIFICATION_ID = 2
    }
}
