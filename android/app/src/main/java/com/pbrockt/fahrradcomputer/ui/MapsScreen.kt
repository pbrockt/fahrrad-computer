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
    val states by vm.mapStates.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf<String?>(null) }

    // Downloads laufen im Hintergrund weiter - solange der Bildschirm offen ist, Fortschritt holen.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshMaps()
            delay(1_000)
        }
    }

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
                        Text("${formatDecimal(r.lengthM / 1000)} km", color = AppColors.Dim, fontSize = 13.sp)
                    }
                    if (r.id == settings.activeRouteId) Text("aktiv", color = AppColors.Go, fontSize = 13.sp)
                }
            }
        }

        Text("Abbiegehinweise", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vibrieren", Modifier.weight(1f))
            Switch(checked = settings.navVibrate, onCheckedChange = vm::setNavVibrate)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ton", Modifier.weight(1f))
            Switch(checked = settings.navSound, onCheckedChange = vm::setNavSound)
        }
        Text(
            "Die App erkennt Abbiegungen aus der Form der Route und meldet sich 150 m vorher (einmal) " +
                "und kurz davor (zweimal), am Ziel dreimal. Die Seite „Navi“ im Fahrt-Bildschirm zeigt " +
                "Pfeil und Entfernung. Straßennamen kennt die App dabei nicht.",
            color = AppColors.Dim, fontSize = 13.sp,
        )

        Text("Offline-Karten", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "OpenStreetMap-Karten von mapsforge.org – danach braucht die Karte kein Internet mehr. " +
                "Mehrere Länder lassen sich kombinieren.",
            color = AppColors.Dim, fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nur über WLAN laden", Modifier.weight(1f))
            Switch(checked = settings.mapsWifiOnly, onCheckedChange = vm::setMapsWifiOnly)
        }
        for (region in MapRegions.ALL) {
            RegionRow(region, states[region.id] ?: MapState.Missing, vm)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Online-Karte ohne Offline-Karte")
                Text("Lädt Kartenbilder von openstreetmap.org (mobile Daten).", color = AppColors.Dim, fontSize = 13.sp)
            }
            Switch(checked = settings.mapOnline, onCheckedChange = vm::setMapOnline)
        }
        Text(
            "Kartendaten © OpenStreetMap-Mitwirkende (ODbL), Kartendateien: mapsforge.org.",
            color = AppColors.Dim, fontSize = 12.sp,
        )
    }
}

@Composable
private fun RegionRow(region: MapRegion, state: MapState, vm: MainViewModel) {
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
                Text("${formatDecimal(route.lengthM / 1000)} km · $turnCount Abbiegungen erkannt", color = AppColors.Dim, fontSize = 13.sp)
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
