package com.pbrockt.fahrradcomputer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.data.AppSettings
import com.pbrockt.fahrradcomputer.health.HealthAvailability
import com.pbrockt.fahrradcomputer.live.LiveSms
import com.pbrockt.fahrradcomputer.maps.MapRegions
import com.pbrockt.fahrradcomputer.maps.MapState
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.trainer.VirtualPower
import com.pbrockt.fahrradcomputer.update.UpdateChannel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** Die Unterseiten der Einstellungen. */
private enum class SettingsPage(val title: String) {
    ROOT("Einstellungen"),
    PROFILE("Profil"),
    ZONES("Zonen"),
    DASHBOARD("Tacho & Bedienung"),
    NAVIGATION("Navigation"),
    OFFLINE_MAPS("Offline-Karten"),
    WEATHER("Wetter"),
    LIVE_LINK("Live-Link"),
    LIVE_SMS("Live-SMS"),
    HEALTH("Health Connect"),
    UPDATES("Updates & Info"),
}

/**
 * Einstellungen als Liste mit Abschnitten: jede Zeile zeigt rechts
 * den aktuellen Stand und fuehrt auf eine eigene Unterseite. Sensoren und Heimtrainer
 * sind eigene Bildschirme ([onOpenSensors], [onOpenTrainer]).
 */
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onConnectHealth: () -> Unit,
    onEnableSms: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenTrainer: () -> Unit,
    onMenu: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
        Header(page, onMenu) { page = SettingsPage.ROOT }
        when (page) {
            SettingsPage.ROOT -> Root(vm, onOpenSensors, onOpenTrainer) { page = it }
            SettingsPage.PROFILE -> SubPage { ProfilePage(vm) }
            SettingsPage.ZONES -> SubPage { ZonesPage(vm) }
            SettingsPage.DASHBOARD -> SubPage { DashboardPage(vm) }
            SettingsPage.NAVIGATION -> SubPage { NavigationPage(vm) }
            SettingsPage.OFFLINE_MAPS -> SubPage { OfflineMapsPage(vm) }
            SettingsPage.WEATHER -> SubPage { WeatherPage(vm) }
            SettingsPage.LIVE_LINK -> SubPage { LiveLinkPage(vm) }
            SettingsPage.LIVE_SMS -> SubPage { LiveSmsPage(vm, onEnableSms) }
            SettingsPage.HEALTH -> SubPage { HealthPage(vm, onConnectHealth) }
            SettingsPage.UPDATES -> SubPage { UpdatesPage(vm) }
        }
    }
}

// --- Aufbau: Kopfzeile, Abschnitte, Zeilen ---

@Composable
private fun Header(page: SettingsPage, onMenu: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        if (page == SettingsPage.ROOT) {
            androidx.compose.material3.IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Menu, contentDescription = "Menü", tint = AppColors.Text)
            }
        } else {
            Row(
                Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = AppColors.Text)
                Text("Einstellungen", fontSize = 17.sp)
            }
        }
        Text(
            page.title.uppercase(Locale.GERMANY),
            fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 0.8.sp,
            modifier = Modifier.align(if (page == SettingsPage.ROOT) Alignment.Center else Alignment.CenterEnd)
                .padding(end = if (page == SettingsPage.ROOT) 0.dp else 16.dp),
        )
    }
    HorizontalDivider(color = AppColors.Line)
}

/** Grauer Abschnitt mit Ueberschrift in Grossbuchstaben. */
@Composable
private fun Section(title: String) {
    Box(Modifier.fillMaxWidth().background(AppColors.Panel).padding(start = 16.dp, top = 22.dp, bottom = 8.dp)) {
        Text(
            title.uppercase(Locale.GERMANY), fontFamily = Condensed, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, letterSpacing = 0.8.sp, color = AppColors.Dim,
        )
    }
    HorizontalDivider(color = AppColors.Line)
}

/** Zeile mit Bezeichnung links, aktuellem Stand rechts und Pfeil - fuehrt auf eine Unterseite. */
@Composable
private fun NavRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = AppColors.Dim, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(
            value.uppercase(Locale.GERMANY), color = valueColor, fontSize = 14.sp, fontFamily = Condensed,
            fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, textAlign = TextAlign.End, maxLines = 1,
            modifier = Modifier.weight(0.8f, fill = false).padding(start = 8.dp),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.Dim)
    }
    HorizontalDivider(color = AppColors.Line)
}

/** Zeile mit Schalter. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontSize = 17.sp)
            if (hint != null) Text(hint, color = AppColors.Dim, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Ueberschrift innerhalb einer Unterseite. */
@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(Locale.GERMANY), fontFamily = Condensed, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, letterSpacing = 0.8.sp, color = AppColors.Dim, modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun Hint(text: String, color: androidx.compose.ui.graphics.Color = AppColors.Dim) {
    Text(text, color = color, fontSize = 13.sp)
}

@Composable
private fun SubPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

// --- Startliste ---

@Composable
private fun Root(vm: MainViewModel, onOpenSensors: () -> Unit, onOpenTrainer: () -> Unit, open: (SettingsPage) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()
    val maps by vm.offlineMaps.collectAsStateWithLifecycle()
    fun onOff(b: Boolean) = if (b) "An" else "Aus"
    Column(Modifier.fillMaxSize().background(AppColors.Panel).verticalScroll(rememberScrollState())) {
        Column(Modifier.background(AppColors.Bg)) {
            Section("Persönlich")
            NavRow("Profil", profileSummary(s)) { open(SettingsPage.PROFILE) }
            NavRow("Zonen", "Max ${s.maxHr} bpm · FTP ${s.ftp.roundToInt()} W") { open(SettingsPage.ZONES) }

            Section("Geräte")
            val paired = SensorKind.entries.count { s.device(it) != null }
            NavRow("Sensoren", if (paired == 0) "keine" else "$paired gekoppelt", onClick = onOpenSensors)
            NavRow("Heimtrainer", "Zwift · Rouvy · MyWhoosh", onClick = onOpenTrainer)

            Section("Fahrt")
            NavRow("Tacho & Bedienung", s.themeMode.label) { open(SettingsPage.DASHBOARD) }
            NavRow("Navigation", if (s.navSound) "Piepen an" else "Piepen aus") { open(SettingsPage.NAVIGATION) }
            NavRow("Offline-Karten", if (maps.isEmpty()) "keine" else "${maps.size} geladen") { open(SettingsPage.OFFLINE_MAPS) }
            NavRow("Wetter", onOff(s.weatherEnabled)) { open(SettingsPage.WEATHER) }

            Section("Teilen")
            NavRow("Live-Link", onOff(s.liveLinkEnabled)) { open(SettingsPage.LIVE_LINK) }
            NavRow("Live-SMS", onOff(s.liveSmsEnabled)) { open(SettingsPage.LIVE_SMS) }
            NavRow("Health Connect", if (health.connected) "verbunden" else "nicht verbunden") { open(SettingsPage.HEALTH) }

            Section("App")
            NavRow("Updates & Info", "${BuildConfig.VERSION_NAME} · ${s.updateChannel.label}") { open(SettingsPage.UPDATES) }
        }
        Box(Modifier.height(24.dp))
    }
}

private fun profileSummary(s: AppSettings): String = buildList {
    if (s.weightKg > 0) add("${formatDecimal(s.weightKg)} kg")
    if (s.birthYear > 0) add("${java.time.LocalDate.now().year - s.birthYear} J.")
    s.sex?.let { add(if (it == Sex.MALE) "Mann" else "Frau") }
}.joinToString(" · ").ifEmpty { "fehlt" }

// --- Unterseiten ---

@Composable
private fun ProfilePage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    TextSetting("Dein Name (für Live-SMS und Live-Link)", s.riderName, KeyboardType.Text, vm::setRiderName)
    NumberField("Gewicht (kg)", if (s.weightKg > 0) formatDecimal(s.weightKg) else "") {
        it.replace(',', '.').toDoubleOrNull()?.let(vm::setWeight)
    }
    NumberField("Geburtsjahr", if (s.birthYear > 0) s.birthYear.toString() else "") { text ->
        text.toIntOrNull()?.takeIf { it in 1900..2100 }?.let(vm::setBirthYear)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((sex, label) in listOf(Sex.MALE to "Mann", Sex.FEMALE to "Frau")) {
            FilterChip(selected = s.sex == sex, onClick = { vm.setSex(sex) }, label = { Text(label) })
        }
    }
    Hint(
        "Gewicht, Alter und Geschlecht brauchen die Kalorien aus dem Puls, die geschätzte FTP und der " +
            "Heimtrainer. Mit Leistungsmesser bzw. Bosch-Fahrerleistung werden die Kalorien am genauesten.",
    )
}

@Composable
private fun ZonesPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Group("Puls")
    NumberField("Maximale Herzfrequenz", s.maxHr.toString()) { it.toIntOrNull()?.let(vm::setMaxHr) }
    NumberField("Ruhepuls", s.restHr.toString()) { t -> t.toIntOrNull()?.takeIf { it in 30..110 }?.let(vm::setRestHr) }
    ZoneLegend(s.zones.asZones.format("bpm"))

    Group("Leistung")
    val est = VirtualPower.estimateFtp(s.body).roundToInt()
    NumberField("FTP in Watt (leer = geschätzt: $est W)", if (s.trainerFtp > 0) s.trainerFtp.toString() else "") { text ->
        val v = text.toIntOrNull() ?: if (text.isBlank()) 0 else return@NumberField
        if (v == 0 || v in 50..600) vm.setTrainerFtp(v)
    }
    ZoneLegend(s.powerZones.format("W"))

    Group("Trittfrequenz")
    TextSetting("Grenzen Z2–Z5 (U/min)", s.cadenceZones.thresholds.joinToString(", "), KeyboardType.Text) { vm.setCadenceZones(it) }
    ZoneLegend(s.cadenceZones.format("U/min"))

    Group("Tempo")
    TextSetting("Grenzen Z2–Z5 (km/h)", s.speedZones.thresholds.joinToString(", "), KeyboardType.Text) { vm.setSpeedZones(it) }
    ZoneLegend(s.speedZones.format("km/h"))
    Hint("Puls- und Leistungskacheln werden in der Farbe ihrer Zone gefüllt. Die FTP gilt auch für den Heimtrainer.")
}

/** Zonen als farbige Kaestchen nebeneinander. */
@Composable
private fun ZoneLegend(text: String) {
    val parts = text.split(" · ")
    Column {
        parts.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp).heightIn(min = 14.dp).background(AppColors.zone(i + 1)).padding(horizontal = 6.dp)) {
                    Text("Z${i + 1}", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontFamily = Condensed, fontWeight = FontWeight.SemiBold)
                }
                Text(p.removePrefix("Z${i + 1} "), fontSize = 14.sp)
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun DashboardPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    Group("Darstellung")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in ThemeMode.entries) {
            FilterChip(selected = s.themeMode == m, onClick = { vm.setThemeMode(m) }, label = { Text(m.label) })
        }
    }
    Hint("Hell ist in praller Sonne besser ablesbar.")

    Group("Hervorhebungsfarbe")
    // Farbfelder; die gewaehlte Farbe hat einen Rahmen.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (a in AccentColor.entries) {
            val color = androidx.compose.ui.graphics.Color(if (AppColors.isDark) a.dark else a.light)
            val selected = s.accentColor == a
            Box(
                Modifier.height(44.dp).width(96.dp)
                    .background(color)
                    .then(if (selected) Modifier.border(3.dp, AppColors.Text) else Modifier)
                    .clickable { vm.setAccentColor(a) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (if (selected) "✓ " else "") + a.label.uppercase(Locale.GERMANY), color = androidx.compose.ui.graphics.Color.White,
                    fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
            }
        }
    }
    Hint("Für Start-Leiste, Knöpfe, Seitenbanner und Menü. Zonenfarben und Warnungen bleiben gleich.")

    Group("Tacho-Seiten")
    Hint("Bearbeiten direkt auf dem Tacho: so lange gedrückt halten, dann Kachel antippen.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((ms, label) in listOf(500 to "0,5 s", 1000 to "1 s", 2000 to "2 s", 3000 to "3 s")) {
            FilterChip(selected = s.editHoldMs == ms, onClick = { vm.setEditHoldMs(ms) }, label = { Text(label) })
        }
    }
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
    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Auf Standard-Seiten zurücksetzen") }
    Hint("Export sichert alle Seiten als Datei. Import und Zurücksetzen ersetzen die aktuellen Seiten.")

    Group("Klingel")
    SwitchRow("Auto-Pause", s.autoPause, "Nach 5 s Stillstand pausieren, beim Losrollen weiter. Eine Pause per Knopf bleibt, bis du weiterdrückst.", vm::setAutoPause)
    SwitchRow("Seiten mit Lautstärketasten blättern", s.volumeKeyPaging, "Nur während der Fahrt: leiser = nächste Seite, lauter = vorige.", vm::setVolumeKeyPaging)
    SwitchRow("Klingel bei Doppeltippen", s.bellOnDoubleTap, "Zweimal auf den Tacho tippen. Lautstärke = Medienlautstärke.", vm::setBellOnDoubleTap)
    OutlinedButton(onClick = vm::ringBellNow, modifier = Modifier.fillMaxWidth()) { Text("Klingel testen") }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Alle Seiten auf Standard?") },
            text = { Text("Deine Seiten werden durch die mitgelieferten ersetzt. Vorher ggf. exportieren.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetDashboard() }) { Text("Zurücksetzen") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun NavigationPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Group("Abbiegehinweise")
    SwitchRow("Vibrieren", s.navVibrate, "150 m vorher einmal, kurz davor zweimal, am Ziel dreimal.", vm::setNavVibrate)
    SwitchRow("Piepen, wenn du reagieren sollst", s.navSound, "Jetzt abbiegen: 2 Töne · Ziel: 3 Töne.", vm::setNavSound)
    SwitchRow("Warnen bei Abweichung von der Route", s.navOffRouteBeep, "Ein langer tiefer Ton ab 50 m abseits.", vm::setNavOffRouteBeep)
    SwitchRow("Anstiege ankündigen", s.climbAlert, "500 m vorher: Hinweis mit Länge, Steigung und Höhenmetern, dazu ein Ton.", vm::setClimbAlert)
    OutlinedButton(onClick = vm::testNavBeep, modifier = Modifier.fillMaxWidth()) { Text("Piepton testen") }
    Hint("Lautstärke = Medienlautstärke. Abbiegungen erkennt die App aus der Form der Route, Straßennamen kennt sie nicht.")

    Group("Karte")
    SwitchRow("Online-Karte ohne Offline-Karte", s.mapOnline, "Lädt Kartenbilder von openstreetmap.org (mobile Daten).", vm::setMapOnline)
}

@Composable
private fun OfflineMapsPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val states by vm.mapStates.collectAsStateWithLifecycle()
    // Downloads laufen im Hintergrund weiter - solange die Seite offen ist, Fortschritt holen.
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshMaps()
            delay(1_000)
        }
    }
    Hint("OpenStreetMap-Karten von mapsforge.org – danach braucht die Karte kein Internet mehr. Mehrere lassen sich kombinieren.")
    SwitchRow("Nur über WLAN laden", s.mapsWifiOnly, null, vm::setMapsWifiOnly)
    for (region in MapRegions.ALL) RegionRow(region, states[region.id] ?: MapState.Missing, vm)
    Hint("Kartendaten © OpenStreetMap-Mitwirkende (ODbL), Kartendateien: mapsforge.org.")
}

@Composable
private fun WeatherPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SwitchRow("Wetter und Wind laden", s.weatherEnabled, null, vm::setWeatherEnabled)
    Hint(
        "Von Open-Meteo (kostenlos, ohne Konto). Gesendet wird nur der auf ~1 km gerundete Standort, während " +
            "der Fahrt höchstens alle 15 Minuten. Für die Kacheln Wind, Gegenwind, Temperatur und die Start-Seite.",
    )
}

@Composable
private fun LiveLinkPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SwitchRow(
        "Während der Fahrt an den Server senden", s.liveLinkEnabled,
        "Zuschauer sehen Strecke, Position und Zahlen auf einer Karte. Den Server-Teil (PHP, ohne Datenbank-Server) gibt es im Repo unter server/live.",
        vm::setLiveLinkEnabled,
    )
    Group("Server")
    TextSetting("Server-Adresse, z. B. https://mein-server.de/live", s.liveLinkUrl, KeyboardType.Uri, vm::setLiveLinkUrl)
    TokenField(s.liveLinkToken, label = "Upload-Token (aus config.php)", onChange = vm::setLiveLinkToken)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = vm::testLiveLink, modifier = Modifier.weight(1f)) { Text("Verbindung testen") }
        OutlinedButton(onClick = vm::shareLiveLink, enabled = s.liveLinkViewUrl.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Link teilen") }
    }
    Hint(
        if (s.liveLinkViewUrl.isBlank()) "Nach dem Test steht hier der Link für Zuschauer."
        else "Link für Zuschauer: ${s.liveLinkViewUrl}\nIst Live-SMS an, steht er auch in der „losgefahren“-SMS.",
    )
    Group("Senden")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (sec in listOf(15, 30, 60, 120)) {
            FilterChip(
                selected = s.liveLinkIntervalS == sec, onClick = { vm.setLiveLinkInterval(sec) },
                label = { Text(if (sec < 60) "alle $sec s" else "alle ${sec / 60} min") },
            )
        }
    }
    SwitchRow("Die ersten 500 m nicht senden", s.liveLinkHideStart, "Damit niemand sieht, wo du wohnst.", vm::setLiveLinkHideStart)
    SwitchRow("Puls mitsenden", s.liveLinkHeartRate, null, vm::setLiveLinkHeartRate)
}

@Composable
private fun LiveSmsPage(vm: MainViewModel, onEnableSms: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    // Einschalten fragt zuerst nach der SMS-Berechtigung.
    SwitchRow("Während der Fahrt Standort per SMS senden", s.liveSmsEnabled, null) { on ->
        if (on) onEnableSms() else vm.setLiveSmsEnabled(false)
    }
    TextSetting("Empfänger (Nummern, mit Komma getrennt)", s.liveSmsNumbers, KeyboardType.Phone, vm::setLiveSmsNumbers)
    val numbers = LiveSms.parseNumbers(s.liveSmsNumbers)
    Hint(
        if (numbers.isEmpty()) "Noch keine gültige Nummer." else "${numbers.size} Empfänger: ${numbers.joinToString()}",
        if (numbers.isEmpty()) AppColors.Stop else AppColors.Dim,
    )
    Text("Abstand: alle ${s.liveSmsIntervalMin} Minuten")
    Slider(value = s.liveSmsIntervalMin.toFloat(), onValueChange = { vm.setLiveSmsInterval(it.roundToInt()) }, valueRange = 1f..60f)
    SwitchRow("„Losgefahren“ und „angekommen“ melden", s.liveSmsStartStop, null, vm::setLiveSmsStartStop)
    OutlinedButton(onClick = vm::sendTestSms, modifier = Modifier.fillMaxWidth()) { Text("Test-SMS senden") }
    Hint(
        "Jede SMS kostet nach deinem Tarif. Beispiel: 2 Stunden mit 10-Minuten-Abstand = 14 SMS pro Empfänger. " +
            "Inhalt: Uhrzeit, km, Tempo, Rest der Route, Akku und ein Kartenlink. Der Name kommt aus dem Profil.",
    )
}

@Composable
private fun HealthPage(vm: MainViewModel, onConnectHealth: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val health by vm.healthState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
            Group("Fahrten")
            SwitchRow(
                "Fahrten automatisch übertragen", s.healthAutoSync,
                if (s.healthAutoSync) "Jede Fahrt geht nach „Beenden“ von selbst an Health Connect."
                else "Aus: nur per Knopf – nach der Fahrt oder in der Fahrt-Ansicht.",
                vm::setHealthAutoSync,
            )
            Hint(
                "Übertragen wird eine Trainingseinheit Radfahren mit Strecke, Puls, Tempo, Trittfrequenz, Leistung, " +
                    "Distanz, Höhenmetern und Kalorien. Erneutes Senden überschreibt statt zu verdoppeln.",
            )

            Group("Gewicht")
            val hw by vm.healthWeight.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.refreshHealth() }
            val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Hint("IN HEALTH CONNECT")
                    Text(
                        hw?.let { "${formatDecimal(it.first)} kg" } ?: "–",
                        fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
                    )
                    Hint(hw?.let { "vom ${it.second.atZone(java.time.ZoneId.systemDefault()).format(dateFmt)}" } ?: "kein Eintrag oder keine Leseberechtigung")
                }
                Column(Modifier.weight(1f)) {
                    Hint("IN DER APP")
                    Text(
                        if (s.weightKg > 0) "${formatDecimal(s.weightKg)} kg" else "–",
                        fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
                        color = if (hw != null && s.weightKg > 0 && kotlin.math.abs(hw!!.first - s.weightKg) >= 0.1) AppColors.Stop else AppColors.Text,
                    )
                    Hint("damit rechnen Kalorien und FTP")
                }
            }
            SwitchRow("Gewicht beim Start übernehmen", s.healthWeightSync, null, vm::setHealthWeightSync)
            OutlinedButton(onClick = vm::importWeightNow, enabled = health.connected, modifier = Modifier.fillMaxWidth()) {
                Text("Gewicht jetzt übernehmen")
            }
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
}

@Composable
private fun UpdatesPage(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Text("Installiert: ${BuildConfig.VERSION_NAME}", fontSize = 17.sp)
    Group("Update-Kanal")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in UpdateChannel.entries) {
            FilterChip(selected = s.updateChannel == c, onClick = { vm.setUpdateChannel(c) }, label = { Text(c.label) })
        }
    }
    Hint(
        if (s.updateChannel == UpdateChannel.RELEASE) "Nur stabile Versionen. Alphas werden ignoriert."
        else "Auch Test-Versionen (Alpha) – neue Funktionen früher, dafür evtl. Fehler.",
    )
    SwitchRow("Beim Start nach Updates suchen", s.autoUpdateCheck, null, vm::setAutoUpdateCheck)
    Button(onClick = { vm.checkForUpdates() }, modifier = Modifier.fillMaxWidth()) { Text("Jetzt nach Updates suchen") }
    TokenField(s.updateToken, onChange = vm::setUpdateToken)
    Group("Info")
    Hint(
        "Fahrrad-Computer · github.com/pbrockt/fahrrad-computer\n" +
            "Schrift: Barlow Condensed (SIL Open Font License) · Karten © OpenStreetMap-Mitwirkende · " +
            "Wetter und Höhen: Open-Meteo · FIT: Garmin FIT SDK",
    )
}

// --- Eingabefelder ---

/**
 * Der Text bleibt lokal, damit Zwischenstaende beim Tippen nicht ueberschrieben werden;
 * gespeichert wird jeder lesbare Wert (der Store begrenzt ihn auf einen sinnvollen Bereich).
 */
@Composable
internal fun NumberField(label: String, value: String, onValid: (String) -> Unit) {
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
private fun TokenField(value: String, label: String = "GitHub-Token (nur bei privatem Repo nötig)", onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.isEmpty()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
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
