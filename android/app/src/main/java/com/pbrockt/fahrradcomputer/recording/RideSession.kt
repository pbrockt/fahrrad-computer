package com.pbrockt.fahrradcomputer.recording

import android.content.Context
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.data.RideRepository
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RouteProgress
import com.pbrockt.fahrradcomputer.routes.RouteTracker
import com.pbrockt.fahrradcomputer.routes.Guidance
import com.pbrockt.fahrradcomputer.routes.Navigator
import com.pbrockt.fahrradcomputer.routes.Turn
import com.pbrockt.fahrradcomputer.routes.TurnDetector
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class RidePhase { IDLE, RECORDING, PAUSED }

/** Ein Anstieg liegt [aheadM] voraus; gemeldet zur Zeit [timeMs]. */
data class ClimbNotice(val climb: com.pbrockt.fahrradcomputer.routes.RouteProfile.Climb, val aheadM: Double, val timeMs: Long)

enum class GpsStatus { OFF, SEARCHING, OK }

/** Ergebnis einer beendeten Fahrt fuer die Zusammenfassung; [file] null = nichts gespeichert. */
data class FinishedRide(val file: File?, val stats: RideStats) {
    /** Id der gespeicherten Fahrt (Dateiname ohne Endung). */
    val id: String? get() = file?.nameWithoutExtension
}

/**
 * Die laufende Fahrt. Lebt in der Application, damit Dashboard und [RideService] auf
 * denselben Stand schauen; der Dienst liefert die Messwerte, die Oberflaeche liest nur.
 */
class RideSession(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Aktuelle Einstellungen: Koerperdaten fuer die Kalorien, Pulszonen fuer die Auswertung. */
    private val settings: () -> AppSettings,
    private val repository: RideRepository,
) {
    private val _phase = MutableStateFlow(RidePhase.IDLE)
    val phase: StateFlow<RidePhase> = _phase.asStateFlow()
    /** true, wenn die Pause von selbst kam (Stillstand) - dann geht es beim Losrollen weiter. */
    private val _autoPaused = MutableStateFlow(false)
    val autoPaused: StateFlow<Boolean> = _autoPaused.asStateFlow()
    private val autoPause = AutoPause()
    /** Hinweis "gleich kommt ein Anstieg" - der Tacho zeigt ihn kurz, der Dienst piept. */
    private val _climbNotice = MutableStateFlow<ClimbNotice?>(null)
    val climbNotice: StateFlow<ClimbNotice?> = _climbNotice.asStateFlow()
    private val announcedClimbs = mutableSetOf<Int>()

    private val _stats = MutableStateFlow(RideStats())
    val stats: StateFlow<RideStats> = _stats.asStateFlow()

    private val _gps = MutableStateFlow(GpsStatus.OFF)
    val gps: StateFlow<GpsStatus> = _gps.asStateFlow()

    /** Ausgeduennte Strecke fuer die Streckenskizze - nicht jeder Punkt muss neu gezeichnet werden. */
    private val _route = MutableStateFlow<List<TrackPoint>>(emptyList())
    val route: StateFlow<List<TrackPoint>> = _route.asStateFlow()

    private val _finished = MutableStateFlow<FinishedRide?>(null)
    val finished: StateFlow<FinishedRide?> = _finished.asStateFlow()

    /** Fahrtrichtung in Grad (0 = Nord); null, solange man steht. Fuer "Fahrtrichtung oben". */
    private val _climb = MutableStateFlow(Climb())
    /** Hoehenmeter bisher und aktuelle Steigung - alle paar Punkte neu berechnet. */
    val climb: StateFlow<Climb> = _climb.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    /** Letzter angenommener GPS-Punkt - fuer Kartenposition und Live-SMS. */
    private val _lastPoint = MutableStateFlow<TrackPoint?>(null)
    val lastPoint: StateFlow<TrackPoint?> = _lastPoint.asStateFlow()

    /** Die Route, die nachgefahren wird, und wo man auf ihr steht. */
    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()
    private val _progress = MutableStateFlow<RouteProgress?>(null)
    val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()
    private var routeTracker: RouteTracker? = null

    /** Abbiegungen der aktiven Route, einmal beim Setzen der Route berechnet. */
    private val _turns = MutableStateFlow<List<Turn>>(emptyList())
    val turns: StateFlow<List<Turn>> = _turns.asStateFlow()
    private val _guidance = MutableStateFlow<Guidance?>(null)
    /** Naechste Abbiegung bzw. das Ziel; null ohne Route oder ohne Position. */
    val guidance: StateFlow<Guidance?> = _guidance.asStateFlow()

    fun setRoute(route: Route?) {
        if (route?.id == _activeRoute.value?.id && route?.points == _activeRoute.value?.points) return
        _activeRoute.value = route
        routeTracker = route?.let(::RouteTracker)
        _turns.value = route?.let(TurnDetector::detect).orEmpty()
        _progress.value = null
        _guidance.value = null
        announcedClimbs.clear()
        _climbNotice.value = null
    }

    private var tracker = RideTracker()
    private var file: File? = null
    private var lastSaveMs = 0L

    val ridesDir: File get() = File(context.filesDir, "fahrten").apply { mkdirs() }

    fun start() {
        if (_phase.value != RidePhase.IDLE) return
        tracker = RideTracker(settings().body)
        _stats.value = RideStats()
        _route.value = emptyList()
        _climb.value = Climb()
        _finished.value = null
        _lastPoint.value = null
        // Frisch zuordnen - sonst sucht der Tracker dort weiter, wo die letzte Fahrt endete.
        routeTracker = _activeRoute.value?.let(::RouteTracker)
        announcedClimbs.clear()
        _climbNotice.value = null
        _progress.value = null
        _guidance.value = null
        file = File(ridesDir, "${LocalDateTime.now().format(FILE_NAME)}.gpx")
        lastSaveMs = System.currentTimeMillis()
        _phase.value = RidePhase.RECORDING
        _autoPaused.value = false
        autoPause.reset()
        ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
    }

    /** Pause per Knopf - geht nur per Knopf weiter. */
    fun pause() = pause(auto = false)

    private fun pause(auto: Boolean) {
        if (_phase.value != RidePhase.RECORDING) return
        tracker.pause()
        _stats.value = tracker.snapshot
        _autoPaused.value = auto
        _phase.value = RidePhase.PAUSED
    }

    fun resume() {
        if (_phase.value != RidePhase.PAUSED) return
        tracker.resume()
        _autoPaused.value = false
        autoPause.reset()
        _phase.value = RidePhase.RECORDING
    }

    fun stop() {
        if (_phase.value == RidePhase.IDLE) return
        _phase.value = RidePhase.IDLE
        _autoPaused.value = false
        _gps.value = GpsStatus.OFF
        context.stopService(Intent(context, RideService::class.java))
        val points = tracker.trackPoints.toList()
        val stats = tracker.snapshot
        val target = file
        scope.launch {
            // Ohne einen einzigen GPS-Punkt gibt es nichts zu speichern.
            val saved = if (points.isEmpty()) null else target?.also { save(it, points) }
            target?.let { partFile(it).delete() }
            // Die Kennzahlen gleich mit ablegen - die Fahrtenliste liest nur diese.
            if (saved != null && points.size >= 2) {
                repository.onRideFinished(RideSummaries.build(saved.nameWithoutExtension, points, stats, settings().zones))
            }
            _finished.value = FinishedRide(saved, stats)
        }
    }

    fun dismissFinished() {
        _finished.value = null
    }

    fun onGpsStatus(status: GpsStatus) {
        if (_phase.value != RidePhase.IDLE) _gps.value = status
    }

    fun onLocation(sample: LocationSample) {
        if (_phase.value == RidePhase.IDLE) return
        // Auto-Pause: im Stand pausieren, beim Losrollen weiter. Eine Pause per Knopf bleibt.
        if (settings().autoPause) {
            when (_phase.value) {
                RidePhase.RECORDING -> if (autoPause.shouldPause(sample)) pause(auto = true)
                RidePhase.PAUSED -> if (_autoPaused.value) {
                    if (autoPause.shouldResume(sample)) resume()
                } else {
                    autoPause.speedKmh(sample) // nur den letzten Punkt merken
                }
                RidePhase.IDLE -> Unit
            }
        }
        tracker.onLocation(sample)
        _stats.value = tracker.snapshot
        val points = tracker.trackPoints
        val last = points.lastOrNull()
        if (last != null && last !== _lastPoint.value) {
            _lastPoint.value = last
            // Beim Stehen die letzte Richtung behalten - sonst springt die Karte an der Ampel auf Nord.
            Heading.of(points.takeLast(60))?.let { _heading.value = it }
            routeTracker?.let { tracker ->
                val p = tracker.update(last.lat, last.lon)
                _progress.value = p
                _activeRoute.value?.let { r ->
                    _guidance.value = Navigator.next(_turns.value, p.doneM, r.lengthM)
                    if (!p.offRoute && settings().climbAlert) announceClimb(r, p.doneM, sample.timeMs)
                }
            }
        }
        if (points.size % ROUTE_EVERY == 1) {
            _route.value = points.filterIndexed { i, _ -> i % ROUTE_EVERY == 0 }
            _climb.value = Climbs.of(points)
        }

        // Zwischenspeichern, damit bei einem Absturz nicht die ganze Fahrt verloren ist.
        if (sample.timeMs - lastSaveMs >= AUTOSAVE_MS) {
            lastSaveMs = sample.timeMs
            val snapshot = points.toList()
            file?.let { f -> scope.launch { save(partFile(f), snapshot) } }
        }
    }

    /** Liegt der Anfang des naechsten Anstiegs weniger als [CLIMB_ALERT_M] voraus: einmal melden. */
    private fun announceClimb(route: Route, doneM: Double, timeMs: Long) {
        val next = route.profile?.nextClimb(doneM) ?: return
        val ahead = next.startM - doneM
        if (ahead > CLIMB_ALERT_M || !announcedClimbs.add(next.number)) return
        _climbNotice.value = ClimbNotice(next, ahead, timeMs)
    }

    fun onHeartRate(bpm: Int, timeMs: Long) {
        if (_phase.value == RidePhase.IDLE) return
        tracker.onHeartRate(bpm, timeMs)
        _stats.value = tracker.snapshot
    }

    fun onCadence(rpm: Int, timeMs: Long) {
        if (_phase.value == RidePhase.IDLE) return
        tracker.onCadence(rpm, timeMs)
        _stats.value = tracker.snapshot
    }

    fun onPower(watts: Int, timeMs: Long) {
        if (_phase.value == RidePhase.IDLE) return
        tracker.onPower(watts, timeMs)
        _stats.value = tracker.snapshot
    }

    /**
     * Waehrend der Fahrt wird hierhin zwischengespeichert, nicht in die endgueltige Datei -
     * sonst stuende eine halbe Fahrt in der Fahrtenliste. Nach einem Absturz rettet
     * [RideRepository.recoverInterrupted] sie beim naechsten Start.
     */
    private fun partFile(f: File) = File(f.parentFile, f.name + RideRepository.PART_SUFFIX)

    private suspend fun save(target: File, points: List<TrackPoint>) = withContext(Dispatchers.IO) {
        // Erst in eine Temp-Datei, dann umbenennen - so liegt nie eine halbe GPX-Datei da.
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.bufferedWriter().use { GpxWriter.write(it, "Radfahrt ${target.nameWithoutExtension}", points) }
        tmp.renameTo(target)
    }

    companion object {
        private val FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        private const val ROUTE_EVERY = 5
        private const val AUTOSAVE_MS = 60_000L
        const val CLIMB_ALERT_M = 500.0
    }
}
