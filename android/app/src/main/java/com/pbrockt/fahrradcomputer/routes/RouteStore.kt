package com.pbrockt.fahrradcomputer.routes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Importierte GPX-Routen. Die Datei wird beim Import in den App-Speicher kopiert - so
 * bleibt die Route nutzbar, auch wenn das Original verschoben oder geloescht wird.
 */
class RouteStore(private val context: Context) {

    private val dir = File(context.filesDir, "routen").apply { mkdirs() }

    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _routes.value = dir.listFiles { f -> f.extension == "gpx" }.orEmpty()
            .mapNotNull { load(it) }
            .sortedBy { it.name.lowercase() }
    }

    fun byId(id: String?): Route? = id?.let { i -> _routes.value.firstOrNull { it.id == i } }

    /** Importiert eine GPX-Datei. Gibt die Route zurueck oder eine Fehlermeldung. */
    suspend fun import(uri: Uri): Result<Route> = withContext(Dispatchers.IO) {
        runCatching {
            val display = displayName(uri)?.substringBeforeLast('.') ?: "Route"
            val id = uniqueId(display)
            val target = File(dir, "$id.gpx")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Datei lässt sich nicht öffnen." }
                target.outputStream().use { input.copyTo(it) }
            }
            val route = load(target)
            if (route == null) {
                target.delete()
                error("Keine Strecke in der Datei gefunden (GPX mit Track- oder Routenpunkten nötig).")
            }
            refresh()
            route
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.gpx").delete()
        profileFile(id).delete()
        refresh()
    }

    /**
     * Holt fuer Routen ohne Hoehen in der GPX-Datei das Hoehenprofil aus dem Netz und legt
     * es neben die Datei. Ohne Internet passiert nichts - beim naechsten Mal wieder.
     */
    suspend fun fillMissingProfiles() = withContext(Dispatchers.IO) {
        var changed = false
        for (r in _routes.value) {
            if (r.profile != null) continue
            val profile = runCatching { ElevationApi.profileFor(r) }.getOrNull() ?: continue
            profileFile(r.id).writeText(
                profile.distanceM.indices.joinToString("\n") { "${profile.distanceM[it]};${profile.elevationM[it]}" }
            )
            changed = true
        }
        if (changed) refresh()
    }

    private fun profileFile(id: String) = File(dir, "$id.hoehe")

    private fun load(file: File): Route? = runCatching {
        val route = file.inputStream().buffered().use {
            GpxRouteParser.parse(it, file.nameWithoutExtension, file.nameWithoutExtension)
        } ?: return@runCatching null
        val extra = profileFile(route.id).takeIf { it.exists() }?.let { f ->
            val rows = f.readLines().mapNotNull { line ->
                val (d, e) = line.split(';').takeIf { it.size == 2 } ?: return@mapNotNull null
                (d.toDoubleOrNull() ?: return@mapNotNull null) to (e.toDoubleOrNull() ?: return@mapNotNull null)
            }
            runCatching { RouteProfile(rows.map { it.first }.toDoubleArray(), rows.map { it.second }.toDoubleArray()) }.getOrNull()
        }
        if (extra != null) route.copy(externalProfile = extra) else route
    }.getOrNull()

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    /** Dateiname aus dem Anzeigenamen, ohne Zeichen, die im Dateisystem stoeren. */
    private fun uniqueId(name: String): String {
        val base = name.replace(Regex("[^A-Za-z0-9ÄÖÜäöüß _-]"), "_").trim().take(60).ifEmpty { "Route" }
        var id = base
        var n = 2
        while (File(dir, "$id.gpx").exists()) id = "$base ($n)".also { n++ }
        return id
    }
}
