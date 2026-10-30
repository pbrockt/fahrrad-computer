package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import com.pbrockt.fahrradcomputer.ui.dashboard.WindArrow
import com.pbrockt.fahrradcomputer.weather.Wind
import kotlin.math.roundToInt

import androidx.compose.foundation.background
import com.pbrockt.fahrradcomputer.routes.Route
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.coach.PlanMatching
import com.pbrockt.fahrradcomputer.coach.SessionKind
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.recording.Totals
import com.pbrockt.fahrradcomputer.sensors.SensorConnection
import java.time.LocalDate
import java.time.ZoneId

/**
 * Start-Seite links vom Tacho: oben "Losfahren" und der Routen-Knopf, darunter der
 * Ueberblick. Pause und Stopp liegen waehrend der Fahrt oben in der schmalen Leiste.
 */
@Composable
fun HomePage(
    vm: MainViewModel,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowRide: () -> Unit,
    onOpen: (AppScreen) -> Unit,
    onPickRoute: () -> Unit = { onOpen(AppScreen.MAPS) },
    onPreviewRoute: (String) -> Unit = { onOpen(AppScreen.MAPS) },
) {
    val phase by vm.ride.phase.collectAsStateWithLifecycle()
    val stats by vm.ride.stats.collectAsStateWithLifecycle()
    val rides by vm.rides.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val route by vm.activeRoute.collectAsStateWithLifecycle()
    val hr by vm.hr.collectAsStateWithLifecycle()
    val cadence by vm.cadence.collectAsStateWithLifecycle()
    val power by vm.power.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    LaunchedEffect(settings.weatherEnabled) { vm.refreshWeather() }
    var routeMenu by remember { mutableStateOf(false) }
    // Ohne Route: gleich zur Auswahl. Mit Route: erst fragen, was passieren soll.
    val onRoute = { if (route == null) onPickRoute() else routeMenu = true }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (phase == RidePhase.IDLE) {
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StartButton(Modifier.weight(1.7f), onStart)
                RouteButton(route, Modifier.weight(1f), onRoute)
            }
        }
        // Laufende Fahrt - Pause und Stopp liegen oben in der Leiste.
        if (phase != RidePhase.IDLE) {
            Card(Modifier.fillMaxWidth().clickable(onClick = onShowRide)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (phase == RidePhase.PAUSED) "Fahrt pausiert" else "Fahrt läuft",
                        color = if (phase == RidePhase.PAUSED) AppColors.Zones[3] else AppColors.Go,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${formatDecimal(stats.distanceM / 1000, 2)} km · ${formatDuration(stats.movingTimeMs)}",
                        fontSize = 30.sp, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                    )
                    Text("Nach rechts wischen oder hier tippen = zum Tacho", color = AppColors.Dim, fontSize = 13.sp)
                }
            }
            RouteButton(route, Modifier.fillMaxWidth().height(64.dp), onRoute)
        }

        // Training heute
        val today = PlanMatching.today(plan?.plan, LocalDate.now())
        if (today != null) {
            InfoCard("Training heute", onClick = { onOpen(AppScreen.RIDES) }) {
                if (today.kind == SessionKind.REST) {
                    Text("Ruhetag – gut erholen.")
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${today.title} · ${today.durationMin} min", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(AppColors.zone(today.targetZone))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Z${today.targetZone}", color = AppColors.OnZone, fontWeight = FontWeight.Bold) }
                    }
                    if (today.description.isNotBlank()) Text(today.description, color = AppColors.Dim, fontSize = 13.sp)
                }
            }
        }

        // Woche / Monat
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalsCard("Diese Woche", Totals.week(day, zone).let { (a, b) -> Totals.of(rides, a, b) }, Modifier.weight(1f))
            TotalsCard("Dieser Monat", Totals.month(day, zone).let { (a, b) -> Totals.of(rides, a, b) }, Modifier.weight(1f))
        }

        // Wetter
        weather?.let { w ->
            InfoCard("Wetter", onClick = {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WindArrow(Wind.arrowDeg(w, null), AppColors.Speed, Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            "${"%.0f".format(w.temperatureC)}° · ${w.description}",
                            fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                        )
                        Text(
                            "Wind aus ${Wind.compass(w.windFromDeg)} ${"%.0f".format(w.windKmh)} km/h, Böen ${"%.0f".format(w.gustKmh)}",
                            color = AppColors.Dim, fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Hoehenprofil der geladenen Route
        route?.let { r ->
            r.profile?.let { p ->
                InfoCard("Höhenprofil · ${p.totalAscentM.roundToInt()} Hm", onClick = onRoute) {
                    ElevationProfileChart(p, Modifier.fillMaxWidth().height(90.dp).padding(top = 4.dp))
                }
            }
        }

        // Sensoren
        InfoCard("Sensoren", onClick = { onOpen(AppScreen.SENSORS) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for ((label, r) in listOf("♥ Puls" to hr, "⟳ Tritt" to cadence, "⚡ Watt" to power)) {
                    val (text, color) = when (r.connection) {
                        SensorConnection.CONNECTED -> (r.value?.toString() ?: "ok") to AppColors.Go
                        SensorConnection.CONNECTING -> "verbinde" to AppColors.Zones[3]
                        SensorConnection.NONE -> "–" to AppColors.Dim
                        else -> "Fehler" to AppColors.Stop
                    }
                    Column {
                        Text(label, color = AppColors.Dim, fontSize = 12.sp)
                        Text(text, color = color, fontWeight = FontWeight.Bold)
                        r.battery?.let { b ->
                            Text("Akku $b %", color = if (b < 20) AppColors.Stop else AppColors.Dim, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (settings.liveSmsEnabled) Text("Live-SMS ist eingeschaltet.", color = AppColors.Dim, fontSize = 12.sp)
        }

        // Letzte Fahrt
        rides.firstOrNull()?.let { last ->
            InfoCard("Letzte Fahrt", onClick = { onOpen(AppScreen.RIDES) }) {
                Text(rideTitle(last), color = AppColors.Dim, fontSize = 13.sp)
                Text(
                    "${formatDecimal(last.distanceM / 1000, 1)} km · ${formatDuration(last.movingTimeMs)} · Ø ${formatDecimal(last.avgSpeedKmh)} km/h",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (routeMenu) route?.let { r ->
        RouteMenu(
            r,
            onDismiss = { routeMenu = false },
            onChange = { routeMenu = false; onPickRoute() },
            onPreview = { routeMenu = false; onPreviewRoute(r.id) },
            onReverse = { routeMenu = false; vm.reverseActiveRoute() },
            onRemove = { routeMenu = false; vm.activateRoute(null) },
        )
    }
}

private val BUTTON_SHAPE = RoundedCornerShape(18.dp)

/** Grosser Knopf "Losfahren" in der Hervorhebungsfarbe. */
@Composable
private fun StartButton(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxHeight().clip(BUTTON_SHAPE).background(AppColors.Accent).clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        Column(Modifier.padding(start = 6.dp)) {
            Text("Losfahren", color = Color.White, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, maxLines = 1)
            Text("Draußen mit GPS", color = Color.White.copy(alpha = 0.8f), fontFamily = Condensed, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/**
 * Routen-Knopf: ist eine Route geladen, leuchtet er in der Hervorhebungsfarbe und zeigt
 * Name und Laenge; sonst ist er hell und fuehrt zur Auswahl.
 */
@Composable
private fun RouteButton(route: Route?, modifier: Modifier, onClick: () -> Unit) {
    val loaded = route != null
    val fg = if (loaded) Color.White else AppColors.Text
    Row(
        modifier.fillMaxHeight().clip(BUTTON_SHAPE).background(if (loaded) AppColors.Accent else AppColors.Panel)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Route, contentDescription = null,
            tint = if (loaded) Color.White else AppColors.Accent, modifier = Modifier.size(26.dp),
        )
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                route?.name ?: "Route", color = fg, fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                route?.let { r ->
                    "${formatDecimal(r.lengthM / 1000)} km" + (r.profile?.let { " · ${it.totalAscentM.roundToInt()} Hm" } ?: "")
                } ?: "keine geladen",
                color = fg.copy(alpha = 0.75f), fontFamily = Condensed, fontSize = 14.sp, maxLines = 1,
            )
        }
    }
}

/** Was mit der geladenen Route passieren soll. */
@Composable
private fun RouteMenu(
    route: Route,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onPreview: () -> Unit,
    onReverse: () -> Unit,
    onRemove: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(route.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "${formatDecimal(route.lengthM / 1000)} km" +
                        (route.profile?.let { " · ${it.totalAscentM.roundToInt()} Hm" } ?: ""),
                    color = AppColors.Dim, modifier = Modifier.padding(bottom = 8.dp),
                )
                MenuRow(Icons.Filled.SwapHoriz, "Route ändern", "Andere Route wählen, planen oder importieren", AppColors.Text, onChange)
                MenuRow(Icons.Filled.Map, "Karte & Höhenprofil", "Vorschau mit Anstiegen", AppColors.Text, onPreview)
                MenuRow(Icons.Filled.SwapVert, "Route umkehren", "Dieselbe Strecke in Gegenrichtung", AppColors.Text, onReverse)
                MenuRow(Icons.Filled.Close, "Route entfernen", "Ohne Navigation fahren", AppColors.Stop, onRemove)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, hint: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (color == AppColors.Text) AppColors.Accent else color, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(hint, color = AppColors.Dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title, color = AppColors.Dim, fontSize = 14.sp,
                fontFamily = Condensed, fontWeight = FontWeight.Medium,
            )
            content()
        }
    }
}
