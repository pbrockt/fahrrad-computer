package com.pbrockt.fahrradcomputer.recording

import android.content.Context
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.data.RideRepository
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.RouteProgress
import com.pbrockt.fahrradcomputer.routes.RouteTracker
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

enum class GpsStatus { OFF, SEARCHING, OK }

/**
 * Ergebnis einer beendeten Fahrt fuer die Zusammenfassung. [healthNote] sagt, ob die Fahrt
 * an Health Connect ging (oder warum nicht); null = nicht versucht.
 */
data class FinishedRide(val file: File?, val stats: RideStats, val healthNote: String? = null)

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

    private val _stats = MutableStateFlow(RideStats())
    val stats: StateFlow<RideStats> = _stats.asStateFlow()

    private val _gps = MutableStateFlow(GpsStatus.OFF)
    val gps: StateFlow<GpsStatus> = _gps.asStateFlow()

    /** Ausgeduennte Strecke fuer die Streckenskizze - nicht jeder Punkt muss neu gezeichnet werden. */
    private val _route = MutableStateFlow<List<TrackPoint>>(emptyList())
    val route: StateFlow<List<TrackPoint>> = _route.asStateFlow()

    private val _finished = MutableStateFlow<FinishedRide?>(null)
    val finished: StateFlow<FinishedRide?> = _finished.asStateFlow()

    /** Letzter angenommener GPS-Punkt - fuer Kartenposition und Live-SMS. */
    private val _lastPoint = MutableStateFlow<TrackPoint?>(null)
    val lastPoint: StateFlow<TrackPoint?> = _lastPoint.asStateFlow()

    /** Die Route, die nachgefahren wird, und wo man auf ihr steht. */
    private val _activeRoute = MutableStateFlow<Route?>(null)
    val activeRoute: StateFlow<Route?> = _activeRoute.asStateFlow()
    private val _progress = MutableStateFlow<RouteProgress?>(null)
    val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()
    private var routeTracker: RouteTracker? = null

    fun setRoute(route: Route?) {
        if (route?.id == _activeRoute.value?.id && route?.points == _activeRoute.value?.points) return
        _activeRoute.value = route
        routeTracker = route?.let(::RouteTracker)
        _progress.value = null
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
        _finished.value = null
        _lastPoint.value = null
        // Frisch zuordnen - sonst sucht der Tracker dort weiter, wo die letzte Fahrt endete.
        routeTracker = _activeRoute.value?.let(::RouteTracker)
        _progress.value = null
        file = File(ridesDir, "${LocalDateTime.now().format(FILE_NAME)}.gpx")
        lastSaveMs = System.currentTimeMillis()
        _phase.value = RidePhase.RECORDING
        ContextCompat.startForegroundService(context, Intent(context, RideService::class.java))
    }

    fun pause() {
        if (_phase.value != RidePhase.RECORDING) return
        tracker.pause()
        _stats.value = tracker.snapshot
        _phase.value = RidePhase.PAUSED
    }

    fun resume() {
        if (_phase.value != RidePhase.PAUSED) return
        tracker.resume()
        _phase.value = RidePhase.RECORDING
    }

    fun stop() {
        if (_phase.value == RidePhase.IDLE) return
        _phase.value = RidePhase.IDLE
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
            val note = if (saved != null && points.size >= 2) {
                val summary = RideSummaries.build(saved.nameWithoutExtension, points, stats, settings().zones)
                repository.onRideFinished(summary, points)
            } else null
            _finished.value = FinishedRide(saved, stats, note)
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
        tracker.onLocation(sample)
        _stats.value = tracker.snapshot
        val points = tracker.trackPoints
        val last = points.lastOrNull()
        if (last != null && last !== _lastPoint.value) {
            _lastPoint.value = last
            routeTracker?.let { _progress.value = it.update(last.lat, last.lon) }
        }
        if (points.size % ROUTE_EVERY == 1) _route.value = points.filterIndexed { i, _ -> i % ROUTE_EVERY == 0 }

        // Zwischenspeichern, damit bei einem Absturz nicht die ganze Fahrt verloren ist.
        if (sample.timeMs - lastSaveMs >= AUTOSAVE_MS) {
            lastSaveMs = sample.timeMs
            val snapshot = points.toList()
            file?.let { f -> scope.launch { save(partFile(f), snapshot) } }
        }
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
    }
}
