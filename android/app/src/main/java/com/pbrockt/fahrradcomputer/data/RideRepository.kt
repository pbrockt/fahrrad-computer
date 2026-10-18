package com.pbrockt.fahrradcomputer.data

import com.pbrockt.fahrradcomputer.health.HealthConnect
import com.pbrockt.fahrradcomputer.recording.GpxReader
import com.pbrockt.fahrradcomputer.recording.RideSummaries
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Die gespeicherten Fahrten: je Fahrt `<id>.gpx` (die Strecke) und `<id>.json` (die
 * Kennzahlen, [RideSummary]). Fehlt die JSON-Datei - Fahrten aus 0.0.3 und frueher -,
 * wird sie beim ersten Laden aus der GPX-Datei berechnet und abgelegt.
 */
class RideRepository(
    private val dir: File,
    private val health: HealthConnect,
    private val settings: () -> AppSettings,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    private val _rides = MutableStateFlow<List<RideSummary>>(emptyList())
    /** Alle Fahrten, neueste zuerst. */
    val rides: StateFlow<List<RideSummary>> = _rides.asStateFlow()

    fun gpxFile(id: String) = File(dir, "$id.gpx")
    private fun jsonFile(id: String) = File(dir, "$id.json")

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ids = dir.listFiles { f -> f.extension == "gpx" }.orEmpty().map { it.nameWithoutExtension }
            _rides.value = ids.mapNotNull { loadOrBuild(it) }.sortedByDescending { it.startMs }
        }
    }

    private fun loadOrBuild(id: String): RideSummary? {
        jsonFile(id).takeIf { it.exists() }?.let { f ->
            runCatching { json.decodeFromString<RideSummary>(f.readText()) }.getOrNull()?.let { s ->
                if (s.elevationMethod >= RideSummaries.ELEVATION_METHOD) return s
                // Hoehenmeter mit dem neuen, geglaetteten Verfahren nachrechnen - der Rest bleibt.
                val points = readTrackBlocking(id)
                return s.copy(
                    elevationGainM = RideSummaries.elevationGain(points),
                    elevationMethod = RideSummaries.ELEVATION_METHOD,
                ).also(::write)
            }
        }
        val points = readTrackBlocking(id)
        if (points.size < 2) return null
        val s = settings()
        return RideSummaries.fromTrack(id, points, s.body, s.zones).also(::write)
    }

    /**
     * Fahrten, deren Aufzeichnung abgebrochen ist (App beendet, Akku leer), liegen noch als
     * Zwischenstand vor. Nur beim App-Start aufrufen - dann laeuft sicher keine Fahrt.
     */
    suspend fun recoverInterrupted() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }.orEmpty().forEach { part ->
                val target = File(dir, part.name.removeSuffix(PART_SUFFIX))
                if (target.exists()) part.delete() else part.renameTo(target)
            }
        }
    }

    suspend fun readTrack(id: String): List<TrackPoint> = withContext(Dispatchers.IO) { readTrackBlocking(id) }

    private fun readTrackBlocking(id: String): List<TrackPoint> =
        runCatching { gpxFile(id).bufferedReader().use(GpxReader::read) }.getOrDefault(emptyList())

    /**
     * Nach einer Fahrt: Kennzahlen ablegen. Nach Health Connect geht die Fahrt bewusst nicht
     * automatisch - nur, wenn man es per Knopf ausloest.
     */
    suspend fun onRideFinished(summary: RideSummary) {
        withContext(Dispatchers.IO) { mutex.withLock { write(summary) } }
        refresh()
    }

    /** Sendet eine Fahrt an Health Connect. null = ok, sonst Fehlermeldung. */
    suspend fun sync(id: String, knownPoints: List<TrackPoint>? = null): String? {
        val summary = _rides.value.firstOrNull { it.id == id }
            ?: withContext(Dispatchers.IO) { loadOrBuild(id) }
            ?: return "Fahrt nicht gefunden."
        val points = knownPoints ?: readTrack(id)
        val error = health.writeRide(summary, points)
        if (error == null) {
            withContext(Dispatchers.IO) { mutex.withLock { write(summary.copy(healthConnectSynced = true)) } }
            refresh()
        }
        return error
    }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                gpxFile(id).delete()
                jsonFile(id).delete()
            }
        }
        refresh()
    }

    companion object {
        const val PART_SUFFIX = ".laufend"
    }

    private fun write(summary: RideSummary) {
        // Erst Temp-Datei, dann umbenennen - nie eine halbe JSON-Datei.
        val target = jsonFile(summary.id)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.encodeToString(summary))
        tmp.renameTo(target)
    }
}
