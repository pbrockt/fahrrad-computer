package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import com.pbrockt.fahrradcomputer.recording.TrackSeries
import com.pbrockt.fahrradcomputer.ui.map.BikeMap
import com.pbrockt.fahrradcomputer.ui.map.mapAvailable
import java.util.Locale

@Composable
fun RideDetailScreen(vm: MainViewModel, ride: RideSummary, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()
    val maps by vm.offlineMaps.collectAsStateWithLifecycle()
    var track by remember(ride.id) { mutableStateOf<List<TrackPoint>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    LaunchedEffect(ride.id) { track = vm.loadTrack(ride.id) }
    val series = remember(track) { track?.let { TrackSeries.from(it) } }
    val zones = settings.zones

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            Text(rideTitle(ride), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        StatGrid(ride)

        track?.takeIf { it.size >= 2 }?.let { t ->
            val box = Modifier.fillMaxWidth().padding(top = 10.dp).aspectRatio(1.2f).clip(RoundedCornerShape(10.dp))
            if (mapAvailable(maps, settings.mapOnline)) {
                BikeMap(maps, settings.mapOnline, "fahrt", box, track = t, controls = true)
            } else {
                Box(box.background(AppColors.Panel).padding(12.dp)) { RouteSketch(t, Modifier.fillMaxSize()) }
            }
        }

        if (ride.zoneSeconds.size == 5 && ride.zoneSeconds.sum() > 0) ZoneBar(ride.zoneSeconds)

        series?.let { s ->
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LineChart("Geschwindigkeit", "km/h", s.minutes, s.speedKmh)
                if (s.hasHeartRate) {
                    val zoneColors = AppColors
                    LineChart("Puls", "bpm", s.minutes, s.heartRate) { zoneColors.zone(zones.zoneOf(it.toInt())) }
                }
                if (s.hasCadence) LineChart("Trittfrequenz", "U/min", s.minutes, s.cadence)
                if (s.hasPower) LineChart("Leistung", "W", s.minutes, s.power)
                if (s.hasAltitude) LineChart("Höhe", "m", s.minutes, s.altitude)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { sharing = true }, enabled = (track?.size ?: 0) >= 2, modifier = Modifier.weight(1f)) {
                Text("Teilen")
            }
            OutlinedButton(
                onClick = { vm.syncRide(ride.id) },
                enabled = health.connected,
                modifier = Modifier.weight(1f),
            ) { Text(if (ride.healthConnectSynced) "Erneut an HC" else "An Health Connect") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                Text("Löschen", color = AppColors.Stop)
            }
        }
        if (!health.connected) {
            Text(
                "Health Connect ist nicht verbunden – unter Einstellungen einrichten.",
                color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }

    if (sharing) {
        track?.let { ShareDialog(ride, it, onDismiss = { sharing = false }) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Fahrt löschen?") },
            text = { Text("${rideTitle(ride)} wird in dieser App endgültig gelöscht. Was schon in Health Connect liegt, bleibt dort.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteRide(ride.id); onBack() }) {
                    Text("Löschen", color = AppColors.Stop)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun StatGrid(r: RideSummary) {
    val items = buildList {
        add("Distanz" to "${formatDecimal(r.distanceM / 1000, 2)} km")
        add("Fahrzeit" to formatDuration(r.movingTimeMs))
        add("Ø / max" to "${formatDecimal(r.avgSpeedKmh)} / ${formatDecimal(r.maxSpeedKmh)} km/h")
        add("Höhenmeter" to "${"%.0f".format(r.elevationGainM)} m")
        r.avgHr?.let { add("Ø / max Puls" to "$it / ${r.maxHr} bpm") }
        r.avgCadence?.let { add("Ø Trittfrequenz" to "$it U/min") }
        r.avgPower?.let { add("Ø / max Leistung" to "$it / ${r.maxPower} W") }
        if (r.calories > 0) add("Kalorien" to "${"%.0f".format(r.calories)} kcal (${r.calorieSource.label})")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(AppColors.Panel).padding(10.dp)
                    ) {
                        Text(label, color = AppColors.Dim, fontSize = 12.sp)
                        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Zeit je Pulszone als gestapelter Balken mit Prozentangaben. */
@Composable
private fun ZoneBar(seconds: List<Long>) {
    val total = seconds.sum().toFloat()
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
            .background(AppColors.Panel).padding(12.dp)
    ) {
        Text("Zeit in Pulszonen", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 6.dp).clip(RoundedCornerShape(6.dp))) {
            seconds.forEachIndexed { i, s ->
                if (s > 0) Box(Modifier.weight(s / total).fillMaxHeight().background(AppColors.zone(i + 1)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            seconds.forEachIndexed { i, s ->
                Text(
                    "Z${i + 1} ${String.format(Locale.GERMANY, "%.0f", s / total * 100)} %",
                    color = AppColors.zone(i + 1), fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
