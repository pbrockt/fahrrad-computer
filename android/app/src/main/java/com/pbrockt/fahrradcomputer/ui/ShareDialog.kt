package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.share.PrivacyTrim
import com.pbrockt.fahrradcomputer.share.ShareContent
import com.pbrockt.fahrradcomputer.share.ShareExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ShareFormat(val label: String) { IMAGE("Bild"), VIDEO("Video"), GPX("GPX-Datei") }

/**
 * Teilen mit Datenschutz-Frage: Vor dem Erzeugen wird gefragt, ob Start und Ende
 * ausgeblendet werden sollen - vorausgewaehlt sind 500 m, damit der Wohnort nicht
 * erkennbar ist. Das gilt fuer alle Formate, auch fuer die GPX-Datei.
 */
@Composable
fun ShareDialog(ride: RideSummary, points: List<TrackPoint>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(ShareFormat.IMAGE) }
    var trimM by remember { mutableStateOf(500) }
    var withMap by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = progress != null

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Fahrt teilen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Format", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShareFormat.entries.forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, enabled = !busy, label = { Text(f.label) })
                    }
                }
                Text("Start und Ende ausblenden?", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Nein", 250 to "250 m", 500 to "500 m", 1000 to "1 km").forEach { (m, label) ->
                        FilterChip(selected = trimM == m, onClick = { trimM = m }, enabled = !busy, label = { Text(label) })
                    }
                }
                Text(
                    if (trimM == 0) "Achtung: Start und Ziel sind dann genau zu sehen – oft ist das die eigene Haustür."
                    else "Die ersten und letzten $trimM m werden weggelassen, damit man nicht sieht, wo du wohnst.",
                    color = if (trimM == 0) AppColors.Stop else AppColors.Dim, fontSize = 13.sp,
                )
                when (format) {
                    ShareFormat.VIDEO -> Text("Ein Punkt fährt die Strecke in 6 Sekunden ab, die Zahlen zählen mit.", color = AppColors.Dim, fontSize = 13.sp)
                    ShareFormat.GPX -> Text("Für Strava, Komoot & Co. – mit Puls, Trittfrequenz und Leistung.", color = AppColors.Dim, fontSize = 13.sp)
                    ShareFormat.IMAGE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mit Karte (OpenStreetMap)")
                            Text("Lädt die Karte für das Bild – braucht Internet.", color = AppColors.Dim, fontSize = 13.sp)
                        }
                        Switch(checked = withMap, onCheckedChange = { withMap = it }, enabled = !busy)
                    }
                }
                progress?.let { p ->
                    Text(if (format == ShareFormat.VIDEO) "Video wird erstellt … ${(p * 100).toInt()} %" else "Wird erstellt …")
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = AppColors.Stop, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    error = null
                    val trimmed = PrivacyTrim.trim(points, trimM.toDouble(), trimM.toDouble())
                    if (trimmed.size < 2) {
                        error = "Die Fahrt ist zu kurz, um je $trimM m wegzulassen."
                        return@TextButton
                    }
                    progress = 0f
                    scope.launch {
                        try {
                            val content = ShareContent(ride, trimmed, trimM)
                            when (format) {
                                ShareFormat.IMAGE -> ShareExporter.share(
                                    context, ShareExporter.image(context, content, withMap), "image/png", "Bild teilen",
                                )
                                ShareFormat.VIDEO -> ShareExporter.share(
                                    context, ShareExporter.video(context, content) { progress = it }, "video/mp4", "Video teilen",
                                )
                                ShareFormat.GPX -> ShareExporter.share(
                                    context, ShareExporter.gpx(context, ride.id, trimmed), "application/gpx+xml", "GPX teilen",
                                )
                            }
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "Konnte nicht erstellt werden: ${e.message ?: e.javaClass.simpleName}" +
                                if (format == ShareFormat.IMAGE && withMap) "\nTipp: „Mit Karte“ ausschalten und nochmal versuchen." else ""
                        } finally {
                            progress = null
                        }
                    }
                },
            ) { Text("Erstellen & teilen") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Abbrechen") } },
    )
}
