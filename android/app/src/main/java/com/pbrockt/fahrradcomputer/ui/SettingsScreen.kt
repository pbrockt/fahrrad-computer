package com.pbrockt.fahrradcomputer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.health.HealthAvailability
import com.pbrockt.fahrradcomputer.live.LiveSms
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.update.UpdateChannel

@Composable
fun SettingsScreen(vm: MainViewModel, onConnectHealth: () -> Unit, onEnableSms: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Körper", fontWeight = FontWeight.Bold)
        NumberField("Maximale Herzfrequenz", s.maxHr.toString()) { it.toIntOrNull()?.let(vm::setMaxHr) }
        val t = s.zones.thresholds
        Text(
            "Zonen: Z1 < ${t[0]} · Z2 ${t[0]}–${t[1] - 1} · Z3 ${t[1]}–${t[2] - 1} · Z4 ${t[2]}–${t[3] - 1} · Z5 ab ${t[3]}",
            color = AppColors.Dim,
        )
        NumberField(
            "Gewicht (kg)",
            if (s.weightKg > 0) formatDecimal(s.weightKg) else "",
        ) { it.replace(',', '.').toDoubleOrNull()?.let(vm::setWeight) }
        NumberField("Geburtsjahr", if (s.birthYear > 0) s.birthYear.toString() else "") { text ->
            text.toIntOrNull()?.takeIf { it in 1900..2100 }?.let(vm::setBirthYear)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sex, label) in listOf(Sex.MALE to "Mann", Sex.FEMALE to "Frau")) {
                FilterChip(selected = s.sex == sex, onClick = { vm.setSex(sex) }, label = { Text(label) })
            }
        }
        Text(
            "Kalorien: mit Leistungsmesser bzw. Bosch-Fahrerleistung am genauesten, sonst aus dem " +
                "Puls (braucht Gewicht, Geburtsjahr, Geschlecht), sonst grob aus der Geschwindigkeit.",
            color = AppColors.Dim,
        )

        Text("Zonen", fontWeight = FontWeight.Bold)
        val estFtp = com.pbrockt.fahrradcomputer.trainer.VirtualPower.estimateFtp(s.body).roundToInt()
        NumberField("FTP in Watt (leer = geschätzt: $estFtp W)", if (s.trainerFtp > 0) s.trainerFtp.toString() else "") { text ->
            val v = text.toIntOrNull() ?: if (text.isBlank()) 0 else return@NumberField
            if (v == 0 || v in 50..600) vm.setTrainerFtp(v)
        }
        Text("Leistung: ${s.powerZones.format("W")}", color = AppColors.Dim, fontSize = 13.sp)
        TextSetting(
            "Trittfrequenz: Grenzen Z2–Z5 (U/min)", s.cadenceZones.thresholds.joinToString(", "), KeyboardType.Text,
        ) { vm.setCadenceZones(it) }
        Text(s.cadenceZones.format("U/min"), color = AppColors.Dim, fontSize = 13.sp)
        TextSetting(
            "Tempo: Grenzen Z2–Z5 (km/h)", s.speedZones.thresholds.joinToString(", "), KeyboardType.Text,
        ) { vm.setSpeedZones(it) }
        Text(s.speedZones.format("km/h"), color = AppColors.Dim, fontSize = 13.sp)
        Text(
            "Puls- und Leistungskacheln werden in der Farbe ihrer Zone gefüllt, Tempo und Trittfrequenz " +
                "zeigen unten eine Zonenleiste. Die FTP gilt auch für den Heimtrainer.",
            color = AppColors.Dim,
        )

        Text("Bedienung", fontWeight = FontWeight.Bold)
        Text("Tacho anpassen: so lange gedrückt halten")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((ms, label) in listOf(500 to "0,5 s", 1000 to "1 s", 2000 to "2 s", 3000 to "3 s")) {
                FilterChip(selected = s.editHoldMs == ms, onClick = { vm.setEditHoldMs(ms) }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Klingel bei Doppeltippen")
                Text("Zweimal auf den Tacho tippen. Lautstärke = Medienlautstärke.", color = AppColors.Dim, fontSize = 13.sp)
            }
            Switch(checked = s.bellOnDoubleTap, onCheckedChange = vm::setBellOnDoubleTap)
        }
        OutlinedButton(onClick = vm::ringBellNow, modifier = Modifier.fillMaxWidth()) { Text("Klingel testen") }

        Text("Wetter", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wetter und Wind laden")
                Text(
                    "Von Open-Meteo (kostenlos, ohne Konto). Gesendet wird nur der auf ~1 km gerundete Standort, " +
                        "während der Fahrt höchstens alle 15 Minuten. Für die Kacheln „Wind“ und „Temperatur“.",
                    color = AppColors.Dim, fontSize = 13.sp,
                )
            }
            Switch(checked = s.weatherEnabled, onCheckedChange = vm::setWeatherEnabled)
        }

        Text("Tacho-Layout", fontWeight = FontWeight.Bold)
        val layoutImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(vm::importLayout)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::exportLayout, modifier = Modifier.weight(1f)) { Text("Exportieren") }
            OutlinedButton(
                onClick = { layoutImporter.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.weight(1f),
            ) { Text("Importieren") }
        }
        Text(
            "Alle Tacho-Seiten als Datei sichern oder teilen. Importieren ersetzt die aktuellen Seiten.",
            color = AppColors.Dim,
        )

        Text("Darstellung", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (m in ThemeMode.entries) {
                FilterChip(
                    selected = s.themeMode == m,
                    onClick = { vm.setThemeMode(m) },
                    label = { Text(m.label) },
                )
            }
        }
        Text(
            "Hell ist in praller Sonne besser ablesbar. Das Dashboard passt du direkt auf dem " +
                "Fahrt-Bildschirm an: lange auf eine Kachel drücken.",
            color = AppColors.Dim,
        )

        Text("Live-Standort per SMS", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Während der Fahrt Standort senden", Modifier.weight(1f))
            Switch(
                checked = s.liveSmsEnabled,
                // Einschalten fragt zuerst nach der SMS-Berechtigung.
                onCheckedChange = { on -> if (on) onEnableSms() else vm.setLiveSmsEnabled(false) },
            )
        }
        if (s.liveSmsEnabled) {
            TextSetting("Empfänger (Nummern, mit Komma getrennt)", s.liveSmsNumbers, KeyboardType.Phone, vm::setLiveSmsNumbers)
            val numbers = LiveSms.parseNumbers(s.liveSmsNumbers)
            Text(
                if (numbers.isEmpty()) "Noch keine gültige Nummer." else "${numbers.size} Empfänger: ${numbers.joinToString()}",
                color = if (numbers.isEmpty()) AppColors.Stop else AppColors.Dim, fontSize = 13.sp,
            )
            TextSetting("Dein Name in der SMS", s.riderName, KeyboardType.Text, vm::setRiderName)
            Text("Abstand: alle ${s.liveSmsIntervalMin} Minuten")
            Slider(
                value = s.liveSmsIntervalMin.toFloat(),
                onValueChange = { vm.setLiveSmsInterval(it.roundToInt()) },
                valueRange = 1f..60f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("„Losgefahren“ und „angekommen“ melden", Modifier.weight(1f))
                Switch(checked = s.liveSmsStartStop, onCheckedChange = vm::setLiveSmsStartStop)
            }
            OutlinedButton(onClick = vm::sendTestSms, modifier = Modifier.fillMaxWidth()) { Text("Test-SMS senden") }
            Text(
                "Jede SMS kostet nach deinem Tarif. Beispiel: 2 Stunden mit 10-Minuten-Abstand = 14 SMS pro Empfänger. " +
                    "Inhalt: Uhrzeit, km, Geschwindigkeit, Rest der Route, Akku und ein Kartenlink.",
                color = AppColors.Dim, fontSize = 13.sp,
            )
        }

        Text("Health Connect", fontWeight = FontWeight.Bold)
        when (health.availability) {
            HealthAvailability.AVAILABLE -> {
                val n = health.granted.size
                Text(
                    if (n == 0) "Nicht verbunden. Fahrten werden dann nur in dieser App gespeichert."
                    else "Verbunden ($n von ${vm.healthPermissions.size} Datenarten erlaubt).",
                    color = if (n == 0) AppColors.Dim else AppColors.Go,
                )
                Button(onClick = onConnectHealth, modifier = Modifier.fillMaxWidth()) {
                    Text(if (n == 0) "Mit Health Connect verbinden" else "Berechtigungen ändern")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gewicht beim Start übernehmen", Modifier.weight(1f))
                    Switch(checked = s.healthWeightSync, onCheckedChange = vm::setHealthWeightSync)
                }
                OutlinedButton(onClick = vm::importWeightNow, enabled = health.connected, modifier = Modifier.fillMaxWidth()) {
                    Text("Gewicht jetzt übernehmen")
                }
                Text(
                    "Übertragen wird nur per Knopf – nach der Fahrt oder in der Fahrt-Ansicht: " +
                        "Trainingseinheit Radfahren mit Strecke, Puls, " +
                        "Geschwindigkeit, Trittfrequenz, Leistung, Distanz, Höhenmeter und Kalorien. " +
                        "Gelesen wird nur das Gewicht. Nichts davon verlässt das Handy.",
                    color = AppColors.Dim,
                )
            }
            else -> {
                Text(
                    if (health.availability == HealthAvailability.NEEDS_UPDATE) "Health Connect muss aktualisiert werden."
                    else "Health Connect ist auf diesem Handy nicht installiert.",
                    color = AppColors.Dim,
                )
                OutlinedButton(onClick = { openHealthConnectInStore(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Im Play Store öffnen")
                }
            }
        }

        Text("Updates", fontWeight = FontWeight.Bold)
        Text("Installiert: ${BuildConfig.VERSION_NAME}", color = AppColors.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in UpdateChannel.entries) {
                FilterChip(
                    selected = s.updateChannel == c,
                    onClick = { vm.setUpdateChannel(c) },
                    label = { Text(c.label) },
                )
            }
        }
        Text(
            if (s.updateChannel == UpdateChannel.RELEASE) "Nur stabile Versionen. Alphas werden ignoriert."
            else "Auch Test-Versionen (Alpha) – neue Funktionen früher, dafür evtl. Fehler.",
            color = AppColors.Dim,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Beim Start nach Updates suchen", Modifier.weight(1f))
            Switch(checked = s.autoUpdateCheck, onCheckedChange = vm::setAutoUpdateCheck)
        }
        TokenField(s.updateToken, vm::setUpdateToken)
        Button(onClick = { vm.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) {
            Text("Jetzt nach Updates suchen")
        }
    }
}

/**
 * Der Text bleibt lokal, damit Zwischenstaende beim Tippen nicht ueberschrieben werden;
 * gespeichert wird jeder lesbare Wert (der Store begrenzt ihn auf einen sinnvollen Bereich).
 */
@Composable
private fun NumberField(label: String, value: String, onValid: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.isEmpty()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onValid(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Textfeld, das lokal tippt und jeden Stand speichert (wie [NumberField]). */
@Composable
private fun TextSetting(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.isEmpty()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TokenField(value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.isEmpty()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text("GitHub-Token (nur bei privatem Repo nötig)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun openHealthConnectInStore(context: android.content.Context) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata"),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
