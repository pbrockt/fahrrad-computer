package com.pbrockt.fahrradcomputer.maps

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Eine herunterladbare Offline-Karte. Quelle ist mapsforge.org: OpenStreetMap-Daten je
 * Bundesland als eine Datei, woechentlich aktualisiert, ausdruecklich zum Herunterladen
 * gedacht (anders als die Kartenbilder von openstreetmap.org).
 */
data class MapRegion(val id: String, val name: String, val sizeMb: Int) {
    val url: String get() = "https://download.mapsforge.org/maps/v5/europe/germany/$id.map"
}

object MapRegions {
    val ALL = listOf(
        MapRegion("rheinland-pfalz", "Rheinland-Pfalz", 183),
        MapRegion("saarland", "Saarland", 35),
        MapRegion("hessen", "Hessen", 219),
        MapRegion("baden-wuerttemberg", "Baden-Württemberg", 406),
        MapRegion("nordrhein-westfalen", "Nordrhein-Westfalen", 586),
        MapRegion("bayern", "Bayern", 551),
    )
}

sealed interface MapState {
    data object Missing : MapState
    data class Downloading(val percent: Int?) : MapState
    data object Installed : MapState
    data class Failed(val reason: String) : MapState
}

/**
 * Laedt Offline-Karten ueber den Android-DownloadManager: Der laeuft im Hintergrund
 * weiter, auch wenn die App geschlossen wird, und zeigt den Fortschritt in der
 * Benachrichtigungsleiste. Die Datei landet erst als `.download` und wird nach dem
 * Abschluss umbenannt - so liest die Karte nie eine halbe Datei.
 */
class OfflineMaps(private val context: Context) {

    val dir: File = (context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR)).apply { mkdirs() }

    private val downloads get() = context.getSystemService(DownloadManager::class.java)

    private val _installed = MutableStateFlow(scanInstalled())
    /** Fertige Kartendateien - die Kartenansicht baut sich neu, wenn sich die Liste aendert. */
    val installed: StateFlow<List<File>> = _installed.asStateFlow()

    private val _states = MutableStateFlow<Map<String, MapState>>(emptyMap())
    val states: StateFlow<Map<String, MapState>> = _states.asStateFlow()

    /** Fehlgeschlagene Downloads bleiben sichtbar, bis neu gestartet wird. */
    private val failures = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun file(region: MapRegion) = File(dir, "${region.id}.map")
    private fun partName(region: MapRegion) = "${region.id}.map.download"

    private fun scanInstalled(): List<File> =
        dir.listFiles { f -> f.extension == "map" }.orEmpty().sortedBy { it.name }

    fun start(region: MapRegion, wifiOnly: Boolean) {
        failures.remove(region.id)
        File(dir, partName(region)).delete()
        val request = DownloadManager.Request(Uri.parse(region.url))
            .setTitle("Offline-Karte ${region.name}")
            .setDescription("OpenStreetMap-Daten für den Fahrrad-Computer")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, DIR, partName(region))
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        downloads.enqueue(request)
        _states.value = _states.value + (region.id to MapState.Downloading(0))
    }

    fun cancel(region: MapRegion) {
        findDownload(region)?.let { downloads.remove(it.id) }
        File(dir, partName(region)).delete()
        refreshBlocking()
    }

    fun delete(region: MapRegion) {
        file(region).delete()
        refreshBlocking()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshBlocking() }

    /**
     * Fragt den DownloadManager nach dem Stand. Fertige Downloads werden hier umbenannt -
     * deshalb auch beim App-Start aufrufen, falls der Download im Hintergrund fertig wurde.
     */
    private fun refreshBlocking() {
        val result = mutableMapOf<String, MapState>()
        for (region in MapRegions.ALL) {
            val d = findDownload(region)
            val part = File(dir, partName(region))
            result[region.id] = when {
                d?.status == DownloadManager.STATUS_SUCCESSFUL && part.exists() -> {
                    part.renameTo(file(region))
                    // remove() loescht die Datei unter dem alten Namen - die gibt es nach dem
                    // Umbenennen nicht mehr, die Karte bleibt also erhalten.
                    downloads.remove(d.id)
                    MapState.Installed
                }
                d != null && d.status in RUNNING -> MapState.Downloading(d.percent)
                d?.status == DownloadManager.STATUS_FAILED -> {
                    downloads.remove(d.id)
                    part.delete()
                    failures[region.id] = "Download fehlgeschlagen (Fehler ${d.reason})."
                    MapState.Failed(failures.getValue(region.id))
                }
                file(region).exists() -> MapState.Installed
                failures.containsKey(region.id) -> MapState.Failed(failures.getValue(region.id))
                else -> MapState.Missing
            }
        }
        _states.value = result
        val now = scanInstalled()
        if (now != _installed.value) _installed.value = now
    }

    private data class Download(val id: Long, val status: Int, val percent: Int?, val reason: Int)

    private fun findDownload(region: MapRegion): Download? {
        downloads.query(DownloadManager.Query()).use { c ->
            val uriCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            while (c.moveToNext()) {
                if (c.getString(uriCol) != region.url) continue
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                return Download(
                    id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    percent = if (total > 0) (done * 100 / total).toInt() else null,
                    reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                )
            }
        }
        return null
    }

    companion object {
        private const val DIR = "karten"
        private val RUNNING = setOf(
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
        )
    }
}
