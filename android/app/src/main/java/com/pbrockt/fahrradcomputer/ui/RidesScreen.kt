package com.pbrockt.fahrradcomputer.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Die gespeicherten Fahrten als GPX-Dateien. Auswertung mit Kennzahlen folgt in Phase 4;
 * bis dahin lassen sie sich teilen, z. B. an Strava, Komoot oder die Trainingsauswertung.
 */
@Composable
fun RidesScreen(vm: MainViewModel) {
    val rides by vm.rides.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toDelete by remember { mutableStateOf<File?>(null) }

    if (rides.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Noch keine Fahrten aufgezeichnet.", color = AppColors.Dim)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rides, key = { it.name }) { file ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title(file))
                        Text("${file.length() / 1024} KB · GPX", color = AppColors.Dim)
                    }
                    IconButton(onClick = { share(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Teilen")
                    }
                    IconButton(onClick = { toDelete = file }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Fahrt löschen?") },
            text = { Text("${title(file)} wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRide(file); toDelete = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
        )
    }
}

private val FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
private val DISPLAY = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy · HH:mm 'Uhr'", java.util.Locale.GERMANY)

private fun title(file: File): String =
    runCatching { LocalDateTime.parse(file.nameWithoutExtension, FILE_NAME).format(DISPLAY) }
        .getOrDefault(file.nameWithoutExtension)

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Fahrt teilen"))
}
