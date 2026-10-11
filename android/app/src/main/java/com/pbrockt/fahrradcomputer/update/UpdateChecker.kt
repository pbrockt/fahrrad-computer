package com.pbrockt.fahrradcomputer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pbrockt.fahrradcomputer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Ein Release von GitHub, auf das Noetigste reduziert. */
data class ReleaseInfo(
    val version: AppVersion,
    val prerelease: Boolean,
    val notes: String,
    val assetName: String?,
    /** API-Url des APK - nur ueber sie kommt man auch an Dateien privater Repos. */
    val assetApiUrl: String?,
)

sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Sucht auf GitHub nach einer neueren Version - je nach Kanal:
 *
 * - **Release:** nur regulaere Releases. Alphas werden ignoriert.
 * - **Alpha:** Alphas und Releases, die hoechste Version gewinnt.
 *
 * Beide Kanaele lesen dieselbe Liste `/releases`; die Auswahl macht [pick]. Ein Release
 * gilt nur als Alpha-Kanal-Kandidat, wenn GitHub es als Pre-Release fuehrt *oder* die
 * Versionsnummer "-alpha" traegt - so rutscht keine Alpha in den Release-Kanal, auch wenn
 * das Haekchen auf GitHub einmal fehlt.
 */
class UpdateChecker(private val context: Context) {

    private val repo = BuildConfig.UPDATE_REPO

    suspend fun check(channel: UpdateChannel, token: String?): UpdateCheck = withContext(Dispatchers.IO) {
        val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
            ?: return@withContext UpdateCheck.Failed("Eigene Version nicht lesbar: ${BuildConfig.VERSION_NAME}")

        val body = try {
            httpGet("https://api.github.com/repos/$repo/releases?per_page=30", token)
        } catch (e: NotFound) {
            return@withContext UpdateCheck.Failed(
                "Repo nicht gefunden." + if (token.isNullOrBlank()) {
                    " Solange es privat ist, braucht die Update-Prüfung einen GitHub-Token."
                } else ""
            )
        } catch (e: Unauthorized) {
            return@withContext UpdateCheck.Failed(
                "GitHub hat den Zugriff abgelehnt - Token fehlt, ist abgelaufen oder hat keine Leserechte auf $repo."
            )
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed("Update-Prüfung fehlgeschlagen: ${e.message ?: "keine Verbindung"}")
        }

        val releases = try {
            parseReleases(body)
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed("Antwort von GitHub nicht lesbar: ${e.message}")
        }

        val best = pick(releases, channel, installed) ?: return@withContext UpdateCheck.UpToDate
        if (best.assetApiUrl == null) {
            return@withContext UpdateCheck.Failed("Im Release ${best.version} liegt kein APK.")
        }
        UpdateCheck.Available(best)
    }

    /**
     * Laedt das APK herunter und uebergibt es dem System-Installer. Android fragt dabei
     * selbst nach - eine App kann sich nicht still ersetzen.
     */
    suspend fun downloadAndInstall(release: ReleaseInfo, token: String?): String? = withContext(Dispatchers.IO) {
        val url = release.assetApiUrl ?: return@withContext "Kein APK im Release."
        try {
            val dir = File(context.cacheDir, "updates").apply {
                deleteRecursively()
                mkdirs()
            }
            val target = File(dir, release.assetName ?: "update.apk")
            openAssetStream(url, token).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() <= 0) return@withContext "Die heruntergeladene Datei ist leer."

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            null
        } catch (e: Exception) {
            "Download fehlgeschlagen: ${e.message ?: "keine Verbindung"}"
        }
    }

    private class NotFound : Exception()
    private class Unauthorized : Exception()

    private fun httpGet(url: String, token: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Fahrrad-Computer")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }
        when (conn.responseCode) {
            in 200..299 -> return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            401, 403 -> throw Unauthorized()
            404 -> throw NotFound()
            else -> throw Exception("HTTP ${conn.responseCode}")
        }
    }

    /**
     * GitHub leitet die Asset-Url auf einen Speicherdienst weiter. Die Weiterleitung wird
     * von Hand aufgeloest, weil der Authorization-Header dorthin nicht mitgehen darf -
     * der Speicherdienst bringt seine eigene Signatur in der Url mit.
     */
    private fun openAssetStream(url: String, token: String?): InputStream {
        var current = url
        var useToken = true
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Fahrrad-Computer")
                if (useToken && !token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            when (val code = conn.responseCode) {
                in 200..299 -> return conn.inputStream
                301, 302, 303, 307, 308 -> {
                    current = conn.getHeaderField("Location") ?: throw Exception("Weiterleitung ohne Ziel")
                    conn.disconnect()
                    useToken = false
                }
                401, 403 -> throw Unauthorized()
                404 -> throw NotFound()
                else -> throw Exception("HTTP $code")
            }
        }
        throw Exception("Zu viele Weiterleitungen")
    }

    companion object {

        fun parseReleases(json: String): List<ReleaseInfo> {
            val arr = JSONArray(json)
            return (0 until arr.length()).mapNotNull { i ->
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft")) return@mapNotNull null
                val version = AppVersion.parse(r.optString("tag_name")) ?: return@mapNotNull null
                val assets = r.optJSONArray("assets")
                val apk = assets?.let { a ->
                    (0 until a.length()).map { a.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                }
                ReleaseInfo(
                    version = version,
                    prerelease = r.optBoolean("prerelease"),
                    notes = r.optString("body").take(600),
                    assetName = apk?.optString("name"),
                    assetApiUrl = apk?.optString("url"),
                )
            }
        }

        /** Die beste neuere Version fuer den Kanal, oder null, wenn nichts Neueres da ist. */
        fun pick(releases: List<ReleaseInfo>, channel: UpdateChannel, installed: AppVersion): ReleaseInfo? =
            releases
                .filter { channel == UpdateChannel.ALPHA || (!it.prerelease && !it.version.isAlpha) }
                .filter { it.version > installed }
                .maxByOrNull { it.version }
    }
}
