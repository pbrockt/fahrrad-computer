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
import com.pbrockt.fahrradcomputer.live.LiveLink
import com.pbrockt.fahrradcomputer.live.LiveSms
import com.pbrockt.fahrradcomputer.routes.TurnAnnouncer
import com.pbrockt.fahrradcomputer.live.SmsSender
import com.pbrockt.fahrradcomputer.sensors.SensorReading
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
    private lateinit var app: FahrradApp
    private lateinit var sms: SmsSender
    private lateinit var signals: TurnSignals
    private val announcer = TurnAnnouncer()
    /** Nur wer "losgefahren" bekommen hat, bekommt auch "angekommen". */
    private var startSmsSent = false
    private var locationManager: LocationManager? = null
    private var lastFixMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as FahrradApp
        session = app.ride
        sms = SmsSender(this)
        signals = TurnSignals(this)
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
        scope.launch { liveSmsLoop() }
        scope.launch { liveLinkLoop() }
        // Abbiegehinweise ankuendigen - nicht, solange man abseits der Route ist.
        scope.launch {
            session.guidance.filterNotNull().collect { g ->
                if (session.progress.value?.offRoute == true || session.phase.value != RidePhase.RECORDING) return@collect
                val alert = announcer.check(g) ?: return@collect
                val s = app.currentSettings.value
                signals.signal(alert, s.navVibrate, s.navSound)
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
            }
        }
        // Gleich kommt ein Anstieg: Ton bzw. Vibration wie bei den Abbiegehinweisen.
        scope.launch {
            session.climbNotice.filterNotNull().collect {
                if (session.phase.value != RidePhase.RECORDING) return@collect
                val s = app.currentSettings.value
                signals.climb(s.navVibrate, s.navSound)
            }
        }
        // Abseits der Route: einmal melden, wenn man abkommt - nicht bei jedem GPS-Punkt.
        scope.launch {
            var wasOff = false
            session.progress.collect { p ->
                val off = p?.offRoute == true
                if (off && !wasOff && session.phase.value == RidePhase.RECORDING) {
                    val s = app.currentSettings.value
                    if (s.navOffRouteBeep) signals.offRoute(s.navVibrate, s.navSound)
                }
                wasOff = off
            }
        }
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

    // --- Live-Link zum eigenen Server ---

    /** Id der Fahrt auf dem Server; eine neue je Start des Dienstes. */
    private val liveRide = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    private var liveSentUpTo = 0
    private var liveStartIdx: Int? = null
    private var liveRouteSent = false
    private var liveEverSent = false

    /**
     * Schickt alle [LiveLink]-Sekunden die neuen Punkte. Klappt es nicht (Funkloch), bleiben
     * sie liegen und gehen beim naechsten Mal mit - der Server ignoriert doppelte.
     */
    private suspend fun liveLinkLoop() {
        while (true) {
            val s = app.currentSettings.value
            if (s.liveLinkEnabled && s.liveLinkUrl.isNotBlank() && s.liveLinkToken.isNotBlank()) {
                val body = liveBody(finished = false)
                if (body != null) {
                    val (json, count) = body
                    val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        LiveLink.post(s.liveLinkUrl, s.liveLinkToken, json).isSuccess
                    }
                    if (ok) {
                        liveSentUpTo += count
                        liveRouteSent = true
                        liveEverSent = true
                    }
                }
            }
            delay(app.currentSettings.value.liveLinkIntervalS * 1000L)
        }
    }

    /** JSON fuer den naechsten Versand und wie viele neue Punkte drin sind; null = noch nichts zu senden. */
    private fun liveBody(finished: Boolean): Pair<String, Int>? {
        val s = app.currentSettings.value
        val track = session.route.value
        if (liveStartIdx == null) {
            liveStartIdx = if (s.liveLinkHideStart) LiveLink.firstVisible(track) else 0
        }
        // Noch in der Naehe des Starts: gar nichts senden, auch keine Kennzahlen.
        val from = liveStartIdx ?: return null
        val begin = maxOf(liveSentUpTo, from)
        val batch = if (begin < track.size) track.subList(begin, minOf(track.size, begin + 1000)) else emptyList()
        if (liveSentUpTo < from) liveSentUpTo = from
        val stats = session.stats.value
        val state = when {
            finished -> "finished"
            session.phase.value == RidePhase.PAUSED -> "paused"
            else -> "riding"
        }
        val json = LiveLink.payload(
            ride = liveRide,
            name = s.riderName,
            state = state,
            stats = LiveLink.Stats(
                distanceM = stats.distanceM,
                movingMs = stats.movingTimeMs,
                speedKmh = stats.speedKmh,
                avgKmh = stats.avgSpeedKmh,
                ascentM = session.climb.value.ascentM,
                remainingM = session.progress.value?.remainingM,
                battery = sms.batteryPercent(),
                heartRate = app.sensors.heartRate.value.fresh(System.currentTimeMillis()),
            ),
            points = batch,
            route = if (liveRouteSent) null else session.activeRoute.value?.points,
            includeHeartRate = s.liveLinkHeartRate,
        )
        return json to batch.size
    }

    /** Zum Schluss "angekommen" melden - der Dienst endet gleich, daher in einem eigenen Thread. */
    private fun sendLiveFinished() {
        val s = app.currentSettings.value
        if (!liveEverSent || !s.liveLinkEnabled || s.liveLinkUrl.isBlank()) return
        val body = liveBody(finished = true)?.first ?: return
        Thread { LiveLink.post(s.liveLinkUrl, s.liveLinkToken, body) }.start()
    }

    override fun onDestroy() {
        // Nur bei einem echten Stopp - wird der Dienst vom System beendet, laeuft die Fahrt
        // noch (Phase != IDLE) und "angekommen" waere falsch.
        if (startSmsSent && session.phase.value == RidePhase.IDLE) {
            val s = app.currentSettings.value
            val p = session.lastPoint.value
            sms.send(
                LiveSms.parseNumbers(s.liveSmsNumbers),
                LiveSms.stop(s.riderName, System.currentTimeMillis(), session.stats.value, p?.lat, p?.lon),
            )
        }
        if (session.phase.value == RidePhase.IDLE) sendLiveFinished()
        locationManager?.removeUpdates(locationListener)
        signals.release()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Live-Standort per SMS: auf Wunsch "losgefahren", dann im eingestellten Abstand der
     * Standort. Einstellungen werden bei jeder Runde neu gelesen - Aenderungen wirken sofort.
     */
    private suspend fun liveSmsLoop() {
        val s = app.currentSettings.value
        if (!s.liveSmsEnabled || LiveSms.parseNumbers(s.liveSmsNumbers).isEmpty() || !sms.canSend()) return
        if (s.liveSmsStartStop) {
            // Kurz auf den ersten GPS-Punkt warten, damit schon die erste SMS einen Standort hat.
            val p = withTimeoutOrNull(START_FIX_WAIT_MS) { session.lastPoint.filterNotNull().first() }
            sms.send(
                LiveSms.parseNumbers(s.liveSmsNumbers),
                LiveSms.start(s.riderName, System.currentTimeMillis(), p?.lat, p?.lon, s.liveSmsIntervalMin) +
                    (if (s.liveLinkEnabled && s.liveLinkViewUrl.isNotBlank()) " Live-Karte: ${s.liveLinkViewUrl}" else ""),
            )
            startSmsSent = true
        }
        while (true) {
            delay(app.currentSettings.value.liveSmsIntervalMin * 60_000L)
            val cur = app.currentSettings.value
            val p = session.lastPoint.value
            if (!cur.liveSmsEnabled || p == null) continue
            sms.send(
                LiveSms.parseNumbers(cur.liveSmsNumbers),
                LiveSms.position(
                    cur.riderName, System.currentTimeMillis(), session.stats.value, p.lat, p.lon,
                    sms.batteryPercent(), session.progress.value?.remainingM,
                ),
            )
        }
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
                    altitude = mslAltitude(location),
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    speedMps = if (location.hasSpeed()) location.speed else null,
                )
            )
        }

        override fun onProviderDisabled(provider: String) = session.onGpsStatus(GpsStatus.OFF)
        override fun onProviderEnabled(provider: String) = session.onGpsStatus(GpsStatus.SEARCHING)
    }

    /**
     * Hoehe ueber dem Meeresspiegel. GPS liefert die Hoehe ueber dem Rechenmodell der Erde
     * (Ellipsoid) - in Deutschland rund 47 m hoeher. Ab Android 14 rechnet das System selbst um.
     */
    private fun mslAltitude(location: Location): Double? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude() -> location.mslAltitudeMeters
        location.hasAltitude() -> location.altitude
        else -> null
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
            .setContentTitle(navigationTitle() ?: "Fahrt läuft")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Auf dem Sperrbildschirm die naechste Abbiegung zeigen statt nur "Fahrt laeuft". */
    private fun navigationTitle(): String? {
        if (session.activeRoute.value == null) return null
        if (session.progress.value?.offRoute == true) return "Abseits der Route"
        return session.guidance.value?.text
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
        private const val START_FIX_WAIT_MS = 120_000L
    }
}
