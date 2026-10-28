package com.pbrockt.fahrradcomputer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.maps.MapRegion
import com.pbrockt.fahrradcomputer.maps.MapRegions
import com.pbrockt.fahrradcomputer.maps.MapState
import com.pbrockt.fahrradcomputer.routes.Route
import com.pbrockt.fahrradcomputer.routes.TurnDetector
import com.pbrockt.fahrradcomputer.ui.map.BikeMap
import com.pbrockt.fahrradcomputer.ui.map.mapAvailable
import kotlinx.coroutines.delay

/** Routen (GPX) und Offline-Karten verwalten. */
@Composable
fun MapsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importRoute)
    }

    val open = preview?.let { id -> routes.firstOrNull { it.id == id } }
    if (open != null) {
        BackHandler { preview = null }
        RoutePreview(vm, open, active = settings.activeRouteId == open.id, onBack = { preview = null })
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Routen", fontWeight = FontWeight.Bold)
        val active = routes.firstOrNull { it.id == settings.activeRouteId }
        Text(
            if (active == null) "Keine Route aktiv – Fahrten ohne Navigation."
            else "Aktiv: ${active.name} (${formatDecimal(active.lengthM / 1000)} km)",
            color = if (active == null) AppColors.Dim else AppColors.Go,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // GPX hat keinen einheitlichen Dateityp - deshalb alle Dateien anbieten.
            Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text("GPX importieren")
            }
            if (active != null) {
                OutlinedButton(onClick = { vm.activateRoute(null) }, modifier = Modifier.weight(1f)) {
                    Text("Route beenden")
                }
            }
        }
        Text(
            "GPX-Dateien z. B. aus Komoot, Strava oder BRouter. Während der Fahrt zeigt die Karte " +
                "die Route, die Kachel „Rest der Route“ die verbleibenden km und warnt, wenn du mehr " +
                "als 50 m abseits bist.",
            color = AppColors.Dim, fontSize = 13.sp,
        )
        for (r in routes) {
            Card(Modifier.fillMaxWidth().clickable { preview = r.id }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name)
                        Text(
                            "${formatDecimal(r.lengthM / 1000)} km" + (r.profile?.let { " · ${it.totalAscentM.toInt()} Hm" } ?: ""),
                            color = AppColors.Dim, fontSize = 13.sp,
                        )
                    }
                    if (r.id == settings.activeRouteId) Text("aktiv", color = AppColors.Go, fontSize = 13.sp)
                }
            }
        }

        Text(
            "Abbiegehinweise und Offline-Karten findest du unter Einstellungen → Navigation bzw. Offline-Karten.",
            color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun RegionRow(region: MapRegion, state: MapState, vm: MainViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(region.name)
                    Text(
                        when (state) {
                            MapState.Installed -> "installiert"
                            MapState.Missing -> "${region.sizeMb} MB"
                            is MapState.Downloading -> "wird geladen … ${state.percent?.let { "$it %" } ?: ""}"
                            is MapState.Failed -> state.reason
                        },
                        color = when (state) {
                            MapState.Installed -> AppColors.Go
                            is MapState.Failed -> AppColors.Stop
                            else -> AppColors.Dim
                        },
                        fontSize = 13.sp,
                    )
                }
                when (state) {
                    MapState.Installed -> OutlinedButton(onClick = { confirmDelete = true }) { Text("Löschen") }
                    is MapState.Downloading -> OutlinedButton(onClick = { vm.cancelMap(region) }) { Text("Abbrechen") }
                    else -> Button(onClick = { vm.downloadMap(region) }) { Text("Laden") }
                }
            }
            if (state is MapState.Downloading) {
                val p = state.percent
                if (p != null) {
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${region.name} löschen?") },
            text = { Text("Gibt ${region.sizeMb} MB frei. Du kannst die Karte jederzeit neu laden.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteMap(region) }) { Text("Löschen", color = AppColors.Stop) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RoutePreview(vm: MainViewModel, route: Route, active: Boolean, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val maps by vm.offlineMaps.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            Column {
                Text(route.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                val turnCount = remember(route) { TurnDetector.detect(route).size }
                Text(
                    "${formatDecimal(route.lengthM / 1000)} km" +
                        (route.profile?.let { " · ${it.totalAscentM.toInt()} Hm" } ?: "") +
                        " · $turnCount Abbiegungen erkannt",
                    color = AppColors.Dim, fontSize = 13.sp,
                )
            }
        }
        val mapModifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp))
        if (mapAvailable(maps, settings.mapOnline)) {
            BikeMap(maps, settings.mapOnline, "vorschau", mapModifier, route = route.points, controls = true)
        } else {
            Column(mapModifier, verticalArrangement = Arrangement.Center) {
                Text("Keine Karte – oben eine Offline-Karte laden oder die Online-Karte einschalten.", color = AppColors.Dim)
            }
        }
        val profile = route.profile
        if (profile != null) {
            Text(
                "HÖHENPROFIL · ${profile.minM.toInt()}–${profile.maxM.toInt()} m",
                fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp),
            )
            ElevationProfileChart(profile, Modifier.fillMaxWidth().height(140.dp).padding(top = 4.dp))
            if (profile.climbs.isNotEmpty()) {
                Text(
                    "ANSTIEGE · ${profile.climbs.size}",
                    fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
                )
                // Die Karte soll nicht verschwinden - lange Listen kuerzen.
                for (c in profile.climbs.take(4)) {
                    Text(
                        "${c.number}. bei km ${formatDecimal(c.startM / 1000)}: ${formatDecimal(c.lengthM / 1000)} km · " +
                            "${c.gainM.toInt()} Hm · Ø ${formatDecimal(c.avgGrade)} % · max ${formatDecimal(c.maxGrade)} %",
                        color = androidx.compose.ui.graphics.Color(
                            com.pbrockt.fahrradcomputer.routes.RouteProfile.GRADE_COLORS[com.pbrockt.fahrradcomputer.routes.RouteProfile.gradeLevel(c.avgGrade)]
                        ),
                        fontSize = 14.sp,
                    )
                }
                if (profile.climbs.size > 4) Text("… und ${profile.climbs.size - 4} weitere", color = AppColors.Dim, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                com.pbrockt.fahrradcomputer.routes.RouteProfile.GRADE_COLORS.forEachIndexed { i, c ->
                    Text(
                        "■ ${com.pbrockt.fahrradcomputer.routes.RouteProfile.GRADE_LABELS[i]}",
                        color = androidx.compose.ui.graphics.Color(c), fontSize = 10.sp,
                    )
                }
            }
        } else {
            Text(
                "Diese GPX-Datei hat keine Höhenangaben – das Profil wird geladen, sobald Internet da ist.",
                color = AppColors.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active) {
                OutlinedButton(onClick = { vm.activateRoute(null) }, modifier = Modifier.weight(1f)) { Text("Nicht mehr nutzen") }
            } else {
                Button(onClick = { vm.activateRoute(route.id); onBack() }, modifier = Modifier.weight(1f)) { Text("Diese Route fahren") }
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                Text("Löschen", color = AppColors.Stop)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Route „${route.name}“ löschen?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteRoute(route.id); onBack() }) {
                    Text("Löschen", color = AppColors.Stop)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}
