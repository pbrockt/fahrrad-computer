package com.pbrockt.fahrradcomputer.recording

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pbrockt.fahrradcomputer.FahrradApp
import com.pbrockt.fahrradcomputer.MainActivity
import com.pbrockt.fahrradcomputer.R
import com.pbrockt.fahrradcomputer.ui.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Vordergrunddienst waehrend einer Fahrt: haelt GPS und Pulsgurt am Leben, auch wenn das
 * Display aus ist oder eine andere App vorne liegt. Er sammelt nur Messwerte und gibt sie
 * an [RideSession] weiter - die Rechnerei passiert dort.
 */
class RideService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: RideSession
    private var locationManager: LocationManager? = null
    private var lastFixMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as FahrradApp
        session = app.ride
        createChannel()
        startInForeground()
        startLocation()

        // Jeder neue Sensorwert geht an die Fahrt; doppelte Meldungen (gleicher Zeitstempel) nicht.
        fun forward(flow: StateFlow<SensorReading>, sink: (Int, Long) -> Unit) = scope.launch {
            flow.distinctUntilChangedBy { it.updatedAtMs }
                .collect { r -> r.value?.let { sink(it, r.updatedAtMs) } }
        }
        forward(app.sensors.heartRate, session::onHeartRate)
        forward(app.sensors.cadence, session::onCadence)
        forward(app.sensors.power, session::onPower)
        // Benachrichtigung und GPS-Status regelmaessig auffrischen.
        scope.launch {
            while (true) {
                delay(5_000)
                val stale = System.currentTimeMillis() - lastFixMs > GPS_STALE_MS
                if (stale) session.onGpsStatus(GpsStatus.SEARCHING)
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        locationManager?.removeUpdates(locationListener)
        scope.cancel()
        super.onDestroy()
    }

    // Die Typ-Konstanten gibt es erst ab Android 10; ServiceCompat ignoriert sie darunter.
    @SuppressLint("InlinedApi")
    private fun startInForeground() {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        // connectedDevice verlangt (ab Android 14) die Bluetooth-Berechtigung - ohne sie
        // wuerde startForeground abstuerzen. Die Fahrt laeuft dann eben ohne Pulsgurt.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
    }

    @SuppressLint("MissingPermission") // geprueft ueber granted()
    private fun startLocation() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            session.onGpsStatus(GpsStatus.OFF)
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = lm
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            session.onGpsStatus(GpsStatus.OFF)
        } else {
            session.onGpsStatus(GpsStatus.SEARCHING)
        }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, locationListener, Looper.getMainLooper())
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFixMs = System.currentTimeMillis()
            session.onGpsStatus(GpsStatus.OK)
            session.onLocation(
                LocationSample(
                    timeMs = location.time,
                    lat = location.latitude,
                    lon = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    speedMps = if (location.hasSpeed()) location.speed else null,
                )
            )
        }

        override fun onProviderDisabled(provider: String) = session.onGpsStatus(GpsStatus.OFF)
        override fun onProviderEnabled(provider: String) = session.onGpsStatus(GpsStatus.SEARCHING)
    }

    private fun buildNotification(): Notification {
        val stats = session.stats.value
        val paused = session.phase.value == RidePhase.PAUSED
        val text = String.format(
            Locale.GERMANY, "%.2f km · %s · %.0f kcal%s",
            stats.distanceM / 1000, formatDuration(stats.movingTimeMs), stats.calories,
            if (paused) " · Pause" else "",
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Fahrt läuft")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Fahrtaufzeichnung", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notificationManager() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "fahrt"
        private const val NOTIFICATION_ID = 1
        private const val GPS_STALE_MS = 10_000L
    }
}
