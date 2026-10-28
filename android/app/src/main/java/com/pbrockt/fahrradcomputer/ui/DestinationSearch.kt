package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.routes.BikeProfile
import com.pbrockt.fahrradcomputer.routes.Place
import com.pbrockt.fahrradcomputer.routes.PlaceCategory
import java.util.Locale

/**
 * Ziel suchen: rotes Lupenfeld, darunter Treffer mit Symbol, Adresse und
 * Entfernung; Antippen plant die Route vom Standort dorthin. Dazu Schnellsuche nach Art
 * (Café, Bäckerei, Trinkwasser ...).
 */
@Composable
fun DestinationSearch(vm: MainViewModel) {
    val places by vm.places.collectAsStateWithLifecycle()
    val busy by vm.searchBusy.collectAsStateWithLifecycle()
    val planning by vm.planning.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<Place?>(null) }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(56.dp).height(56.dp).background(AppColors.Accent)
                    .clickable { focus.clearFocus(); vm.searchPlaces(query) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Search, contentDescription = "Suchen", tint = Color.White)
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Ziel suchen – Ort, Adresse, Café …") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.searchPlaces(query) }),
                trailingIcon = {
                    if (query.isNotEmpty() || places.isNotEmpty()) {
                        IconButton(onClick = { query = ""; vm.clearPlaces() }) { Icon(Icons.Filled.Clear, contentDescription = "Leeren") }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.Panel, unfocusedContainerColor = AppColors.Panel,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).height(56.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (c in PlaceCategory.entries) {
                FilterChip(selected = false, onClick = { query = c.label; focus.clearFocus(); vm.searchPlaces("", c) }, label = { Text(c.label) })
            }
        }
        if (places.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(AppColors.Bg)) {
                for (p in places) {
                    PlaceRow(p) { target = p }
                    HorizontalDivider(color = AppColors.Line)
                }
            }
        }
    }

    target?.let { p ->
        PlanDialog(p, planning, onDismiss = { if (!planning) target = null }) { profile, roundTrip ->
            vm.planRoute(p, profile, roundTrip)
            target = null
        }
    }
}

@Composable
private fun PlaceRow(p: Place, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(iconFor(p.kind), contentDescription = null, tint = AppColors.Text, modifier = Modifier.padding(top = 2.dp, end = 12.dp).size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            if (p.address.isNotBlank()) Text(p.address, color = AppColors.Dim, fontSize = 14.sp)
        }
        p.distanceM?.let { d ->
            Text(
                "${formatDecimal(d / 1000, if (d < 10_000) 1 else 0)} KM",
                fontFamily = Condensed, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                modifier = Modifier.padding(start = 8.dp).background(AppColors.Panel).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun iconFor(kind: String): ImageVector = when (kind) {
    "cafe" -> Icons.Filled.LocalCafe
    "bakery" -> Icons.Filled.BakeryDining
    "ice_cream" -> Icons.Filled.Icecream
    "restaurant", "fast_food" -> Icons.Filled.Restaurant
    "biergarten", "pub", "bar" -> Icons.Filled.SportsBar
    "drinking_water", "water_point" -> Icons.Filled.WaterDrop
    "toilets" -> Icons.Filled.Wc
    "bicycle" -> Icons.Filled.PedalBike
    "charging_station" -> Icons.Filled.EvStation
    else -> Icons.Filled.Place
}

/** Profil und Hin-/Rueckweg waehlen, dann planen. */
@Composable
private fun PlanDialog(p: Place, planning: Boolean, onDismiss: () -> Unit, onPlan: (BikeProfile, Boolean) -> Unit) {
    var profile by rememberSaveable { mutableStateOf(BikeProfile.TOUR) }
    var roundTrip by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Route zu ${p.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.address.isNotBlank()) Text(p.address, color = AppColors.Dim, fontSize = 14.sp)
                Text("FAHRPROFIL", color = AppColors.Dim, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                for (b in BikeProfile.entries) {
                    FilterChip(selected = profile == b, onClick = { profile = b }, label = { Text(b.label) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hin und zurück", Modifier.weight(1f))
                    Switch(checked = roundTrip, onCheckedChange = { roundTrip = it })
                }
                Text(
                    "Geplant wird vom aktuellen Standort mit BRouter (OpenStreetMap). Die Route wird gespeichert und " +
                        "gleich aktiviert – mit Höhenprofil, Anstiegen und Abbiegehinweisen.",
                    color = AppColors.Dim, fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPlan(profile, roundTrip) }, enabled = !planning) {
                Text("Route planen".uppercase(Locale.GERMANY))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
