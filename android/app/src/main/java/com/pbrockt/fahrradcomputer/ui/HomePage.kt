package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import com.pbrockt.fahrradcomputer.ui.dashboard.WindArrow
import com.pbrockt.fahrradcomputer.weather.Wind
import kotlin.math.roundToInt

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
 * Start-Seite links vom Tacho: Ueberblick und Bedienung. Hier liegt der grosse
 * Start-Knopf - und waehrend der Fahrt Pause und Stopp, damit der Tacho selbst frei bleibt.
 */
@Composable
fun HomePage(
    vm: MainViewModel,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowRide: () -> Unit,
    onOpen: (AppScreen) -> Unit,
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Bedienung
        if (phase == RidePhase.IDLE) {
            BigButton("Fahrt starten", AppColors.Go, Modifier.fillMaxWidth().height(80.dp), onStart)
        } else {
            Card(Modifier.fillMaxWidth().clickable(onClick = onShowRide)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (phase == RidePhase.PAUSED) "Fahrt pausiert" else "Fahrt läuft",
                        color = if (phase == RidePhase.PAUSED) AppColors.Zones[3] else AppColors.Go,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${formatDecimal(stats.distanceM / 1000, 2)} km · ${formatDuration(stats.movingTimeMs)}",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    )
                    Text("Nach rechts wischen oder hier tippen = zum Tacho", color = AppColors.Dim, fontSize = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (phase == RidePhase.RECORDING) {
                    BigButton("Pause", AppColors.Accent, Modifier.weight(2f).height(72.dp), vm::pauseRide)
                } else {
                    BigButton("Weiter", AppColors.Go, Modifier.weight(2f).height(72.dp), vm::resumeRide)
                }
                BigButton("Stopp", AppColors.Stop, Modifier.weight(1f).height(72.dp), onStop)
            }
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

        // Route
        InfoCard("Route", onClick = { onOpen(AppScreen.MAPS) }) {
            val r = route
            if (r == null) Text("Keine Route aktiv – antippen zum Auswählen.", color = AppColors.Dim)
            else {
                Text("${r.name} · ${formatDecimal(r.lengthM / 1000)} km" +
                    (r.profile?.let { " · ${it.totalAscentM.roundToInt()} Hm" } ?: ""), fontWeight = FontWeight.Bold)
                r.profile?.let { p ->
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
}

@Composable
private fun BigButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = AppColors.OnZone),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier,
    ) {
        Text(
            text.uppercase(java.util.Locale.GERMANY), fontSize = 24.sp, fontFamily = Condensed,
            fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun InfoCard(title: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.uppercase(java.util.Locale.GERMANY), color = AppColors.Dim, fontSize = 14.sp,
                fontFamily = Condensed, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
            )
            content()
        }
    }
}
