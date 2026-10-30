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
import com.pbrockt.fahrradcomputer.recording.Altitude
import com.pbrockt.fahrradcomputer.recording.TrackColoring
import com.pbrockt.fahrradcomputer.recording.TrackSeries
import androidx.compose.material3.FilterChip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pbrockt.fahrradcomputer.ui.map.BikeMap
import com.pbrockt.fahrradcomputer.ui.map.mapAvailable
import java.util.Locale

@Composable
fun RideDetailScreen(vm: MainViewModel, ride: RideSummary, onBack: () -> Unit, summary: Boolean = false) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()
    val maps by vm.offlineMaps.collectAsStateWithLifecycle()
    var track by remember(ride.id) { mutableStateOf<List<TrackPoint>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var colorMode by remember { mutableStateOf(ColorMode.PLAIN) }

    LaunchedEffect(ride.id) { track = vm.loadTrack(ride.id) }
    val series = remember(track) { track?.let { TrackSeries.from(it, maxPoints = 1500) } }
    val zoom = remember(ride.id) { ChartZoom() }
    val zones = settings.zones

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        if (summary) {
            // Direkt nach der Fahrt: grosse Ueberschrift statt Zurueck-Pfeil.
            Text(
                "Geschafft!", fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 34.sp,
                color = AppColors.Accent, modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                rideTitle(ride) + if (ride.healthConnectSynced) " · an Health Connect gesendet" else "",
                color = AppColors.Dim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                Text(rideTitle(ride), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }

        StatGrid(ride)

        track?.takeIf { it.size >= 2 }?.let { t ->
            val hasHr = remember(t) { t.any { it.heartRate != null } }
            if (mapAvailable(maps, settings.mapOnline)) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ColorMode.entries.filter { it != ColorMode.HEART_RATE || hasHr }.forEach { m ->
                        FilterChip(selected = colorMode == m, onClick = { colorMode = m }, label = { Text(m.label) })
                    }
                }
            }
            val zoneColors = AppColors.Zones.map { it.toArgb() }
            val colors = remember(t, colorMode, zones) {
                when (colorMode) {
                    ColorMode.PLAIN -> null
                    ColorMode.SPEED -> TrackColoring.bySpeed(t).map { it to SPEED_COLORS[it.level] }
                    ColorMode.HEART_RATE -> TrackColoring.byHeartRate(t, zones).map { it to zoneColors[it.level] }
                }
            }
            val box = Modifier.fillMaxWidth().padding(top = 6.dp).aspectRatio(1.2f).clip(RoundedCornerShape(10.dp))
            if (mapAvailable(maps, settings.mapOnline)) {
                BikeMap(maps, settings.mapOnline, "fahrt", box, track = t, trackColors = colors, controls = true)
                Legend(colorMode, t)
            } else {
                Box(box.background(AppColors.Panel).padding(12.dp)) { RouteSketch(t, Modifier.fillMaxSize()) }
            }
        }

        series?.let { s ->
            val c = AppColors
            Column(Modifier.padding(top = 10.dp)) {
                if (ride.zoneSeconds.size == 5) ZoneTimeBar("Zeit in Pulszonen", ride.zoneSeconds)
                if (s.hasPower) ZoneTimeBar("Zeit in Leistungszonen", zoneSeconds(s.minutes, s.power, settings.powerZones))
                if (s.hasCadence) ZoneTimeBar(
                    "Zeit in Trittfrequenz-Zonen", zoneSeconds(s.minutes, s.cadence, settings.cadenceZones) { it < 1f },
                )
                ZoneTimeBar("Zeit in Tempozonen", zoneSeconds(s.minutes, s.speedKmh, settings.speedZones) { it < 3f })
                Text(
                    if (zoom.zoomed) "Gezoomt – doppelt tippen zeigt wieder alles." else "Mit zwei Fingern in die Verläufe zoomen.",
                    color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
                LineChart("Tempo", "km/h", s.minutes, s.speedKmh, zoom, color = c.Speed, zones = settings.speedZones)
                if (s.hasHeartRate) LineChart("Puls", "bpm", s.minutes, s.heartRate, zoom, color = c.Stop, zones = zones.asZones)
                if (s.hasCadence) LineChart("Trittfrequenz", "U/min", s.minutes, s.cadence, zoom, color = c.Cadence, zones = settings.cadenceZones)
                if (s.hasPower) LineChart("Leistung", "W", s.minutes, s.power, zoom, color = c.Go, zones = settings.powerZones)
                if (s.hasAltitude) LineChart(
                    "Höhe", "m", s.minutes, s.altitude, zoom, color = c.Altitude,
                    minRange = Altitude.MIN_PROFILE_RANGE_M.toFloat(),
                )
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
        if (summary) {
            androidx.compose.material3.Button(
                onClick = onBack,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(bottom = 4.dp),
            ) { Text("Fertig", fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) }
            Text(
                "Die Fahrt steht auch später unter Verlauf.",
                color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 16.dp),
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

private enum class ColorMode(val label: String) { PLAIN("Strecke"), SPEED("Tempo"), HEART_RATE("Puls") }

/** Tempo-Farben von langsam (blau) nach schnell (rot). */
private val SPEED_COLORS = listOf(0xFF3FA7D6, 0xFF46B97A, 0xFFE8C547, 0xFFF0973A, 0xFFE5484D).map { it.toInt() }

@Composable
private fun Legend(mode: ColorMode, track: List<TrackPoint>) {
    when (mode) {
        ColorMode.PLAIN -> Unit
        ColorMode.SPEED -> {
            val range = remember(track) { TrackColoring.speedRange(track) } ?: return
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${formatDecimal(range.first, 0)} km/h", color = AppColors.Dim, fontSize = 12.sp)
                Row(Modifier.weight(1f).padding(horizontal = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    SPEED_COLORS.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().background(Color(c))) }
                }
                Text("${formatDecimal(range.second, 0)} km/h", color = AppColors.Dim, fontSize = 12.sp)
            }
        }
        ColorMode.HEART_RATE -> Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AppColors.Zones.forEachIndexed { i, c -> Text("■ Z${i + 1}", color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun StatGrid(r: RideSummary) {
    val c = AppColors
    val items = buildList {
        add(Triple("Distanz", "${formatDecimal(r.distanceM / 1000, 2)} km", c.Distance))
        add(Triple("Fahrzeit", formatDuration(r.movingTimeMs), c.Text))
        add(Triple("Ø / max Tempo", "${formatDecimal(r.avgSpeedKmh)} / ${formatDecimal(r.maxSpeedKmh)}", c.Speed))
        add(Triple("Höhenmeter", "${"%.0f".format(r.elevationGainM)} m", c.Distance))
        r.avgHr?.let { add(Triple("Ø / max Puls", "$it / ${r.maxHr}", c.Stop)) }
        r.avgCadence?.let { add(Triple("Ø Trittfrequenz", "$it", c.Cadence)) }
        r.avgPower?.let { add(Triple("Ø / max Leistung", "$it / ${r.maxPower} W", c.Go)) }
        if (r.calories > 0) add(Triple("Kalorien · ${r.calorieSource.label}", "${"%.0f".format(r.calories)}", c.Calories))
    }
    // Kacheln wie auf dem Tacho: feine Linien statt Karten.
    Column(Modifier.background(c.Line), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { (label, value, color) ->
                    Column(
                        Modifier.weight(1f).background(c.Bg).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label.uppercase(Locale.GERMANY), color = color, fontSize = 13.sp,
                            fontFamily = Condensed, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
                        )
                        Text(value, color = color, fontSize = 30.sp, fontFamily = Condensed, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f).background(c.Bg))
            }
        }
    }
}
