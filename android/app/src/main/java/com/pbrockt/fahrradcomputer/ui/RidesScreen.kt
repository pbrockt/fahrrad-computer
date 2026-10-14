package com.pbrockt.fahrradcomputer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.recording.RideSummary
import com.pbrockt.fahrradcomputer.recording.Totals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Die Auswertung: Summen fuer Woche und Monat, darunter alle Fahrten. Tipp = Details. */
@Composable
fun RidesScreen(vm: MainViewModel) {
    val rides by vm.rides.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    val open = selected?.let { id -> rides.firstOrNull { it.id == id } }
    if (open != null) {
        BackHandler { selected = null }
        RideDetailScreen(vm, open, onBack = { selected = null })
        return
    }

    if (rides.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Noch keine Fahrten aufgezeichnet.", color = AppColors.Dim)
        }
        return
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val week = Totals.week(today, zone).let { (a, b) -> Totals.of(rides, a, b) }
    val month = Totals.month(today, zone).let { (a, b) -> Totals.of(rides, a, b) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalsCard("Diese Woche", week, Modifier.weight(1f))
                TotalsCard(today.format(MONTH), month, Modifier.weight(1f))
            }
        }
        items(rides, key = { it.id }) { ride ->
            RideRow(ride, Modifier.fillMaxWidth().clickable { selected = ride.id })
        }
        item { Text("", Modifier.padding(4.dp)) }
    }
}

private val MONTH = DateTimeFormatter.ofPattern("LLLL", Locale.GERMANY)
private val DAY = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy · HH:mm", Locale.GERMANY)

fun rideTitle(r: RideSummary): String =
    Instant.ofEpochMilli(r.startMs).atZone(ZoneId.systemDefault()).format(DAY)

@Composable
private fun TotalsCard(title: String, t: Totals, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title.replaceFirstChar { it.uppercase() }, color = AppColors.Dim, fontSize = 13.sp)
            Text("${formatDecimal(t.distanceM / 1000)} km", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(
                "${t.rides} ${if (t.rides == 1) "Fahrt" else "Fahrten"} · ${formatDuration(t.movingTimeMs)}" +
                    if (t.calories > 0) " · ${"%.0f".format(t.calories)} kcal" else "",
                color = AppColors.Dim, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun RideRow(r: RideSummary, modifier: Modifier) {
    Card(modifier) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rideTitle(r))
                Text(
                    "${formatDecimal(r.distanceM / 1000, 2)} km · ${formatDuration(r.movingTimeMs)} · " +
                        "Ø ${formatDecimal(r.avgSpeedKmh)} km/h" +
                        (r.avgHr?.let { " · ♥ $it" } ?: "") +
                        if (r.calories > 0) " · ${"%.0f".format(r.calories)} kcal" else "",
                    color = AppColors.Dim, fontSize = 13.sp,
                )
            }
            if (r.healthConnectSynced) Text("✓ HC", color = AppColors.Go, fontSize = 12.sp)
        }
    }
}
