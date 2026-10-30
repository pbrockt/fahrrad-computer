package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.recording.Sex
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Einrichtung beim ersten Start: Profil, Sensoren, Standort, fertig. Jeder Schritt laesst
 * sich ueberspringen; alles steht spaeter auch in den Einstellungen.
 */
@Composable
fun SetupScreen(
    vm: MainViewModel,
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onScanRequested: (SensorKind) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish: () -> Unit = { vm.setSetupDone(true) }

    Column(Modifier.fillMaxSize().background(AppColors.Bg).padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Fortschritt als Punkte, der aktuelle Schritt als Pille.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until STEPS) {
                Box(
                    Modifier.padding(horizontal = 3.dp).size(width = if (i == step) 22.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(50)).background(if (i <= step) AppColors.Accent else AppColors.Line)
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> ProfileStep(vm)
                1 -> Column {
                    StepTitle("Sensoren koppeln", "Brustgurt, Trittfrequenz oder Leistungsmesser. Geht auch später unter Sensoren.")
                    Box(Modifier.weight(1f)) { SensorsScreen(vm, onScanRequested) }
                }
                2 -> LocationStep(locationGranted, onRequestLocation)
                else -> DoneStep(vm)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step < STEPS - 1) {
                TextButton(onClick = finish) { Text("Überspringen", color = AppColors.Dim) }
            } else if (step > 0) {
                TextButton(onClick = { step-- }) { Text("Zurück", color = AppColors.Dim) }
            }
            Box(Modifier.weight(1f))
            Button(
                onClick = { if (step < STEPS - 1) step++ else finish() },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    if (step < STEPS - 1) "Weiter" else "Los geht's",
                    fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
    }
}

private const val STEPS = 4

@Composable
private fun StepTitle(title: String, text: String) {
    Text(title, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = AppColors.Text)
    Text(text, color = AppColors.Dim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
}

@Composable
private fun ProfileStep(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepTitle(
            "Willkommen!",
            "Ein paar Angaben zu dir – daraus rechnet die App Kalorien, Pulszonen und die Watt am Heimtrainer.",
        )
        NumberField("Gewicht (kg)", if (s.weightKg > 0) formatDecimal(s.weightKg) else "") {
            it.replace(',', '.').toDoubleOrNull()?.takeIf { kg -> kg in 30.0..250.0 }?.let(vm::setWeight)
        }
        NumberField("Geburtsjahr", if (s.birthYear > 0) s.birthYear.toString() else "") { text ->
            text.toIntOrNull()?.takeIf { it in 1920..LocalDate.now().year - 8 }?.let { year ->
                vm.setBirthYear(year)
                // Max-Puls aus dem Alter schaetzen (Tanaka), solange niemand einen eigenen eintraegt.
                vm.setMaxHr((208 - 0.7 * (LocalDate.now().year - year)).roundToInt())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((sex, label) in listOf(Sex.MALE to "Mann", Sex.FEMALE to "Frau")) {
                FilterChip(selected = s.sex == sex, onClick = { vm.setSex(sex) }, label = { Text(label) })
            }
        }
        NumberField("Maximaler Puls (falls bekannt)", s.maxHr.toString()) { text ->
            text.toIntOrNull()?.takeIf { it in 120..230 }?.let(vm::setMaxHr)
        }
        Text(
            "Den maximalen Puls schätzt die App aus dem Geburtsjahr. Kennst du deinen echten Wert, trag ihn ein – " +
                "die Pulszonen werden dann genauer.",
            color = AppColors.Dim, fontSize = 13.sp,
        )
    }
}

@Composable
private fun LocationStep(granted: Boolean, onRequest: () -> Unit) {
    Column {
        StepTitle(
            "Standort erlauben",
            "Ohne GPS keine Strecke, kein Tempo und keine Navigation. Der Standort bleibt auf dem Handy – " +
                "außer du schaltest Live-SMS oder Live-Link ein.",
        )
        if (granted) {
            Text("✓ Standort ist erlaubt", color = AppColors.Go, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        } else {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) { Text("Standort erlauben", fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp) }
        }
    }
}

@Composable
private fun DoneStep(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StepTitle("Fertig!", "Auf der Start-Seite: ▶ Losfahren. Pause und Stopp liegen dann oben rechts.")
        for (tip in listOf(
            "Auto-Pause: An der Ampel hält die Aufzeichnung von selbst an.",
            "Lautstärketasten blättern während der Fahrt die Seiten.",
            "Tacho lange drücken: Kacheln und Seiten anpassen.",
            "Route-Knopf auf der Start-Seite: Ziel suchen oder GPX laden.",
        )) Text("• $tip", color = AppColors.Text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
        Text("Farbe", color = AppColors.Dim, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in listOf(AccentColor.PETROL, AccentColor.BLUE, AccentColor.GREEN, AccentColor.ORANGE, AccentColor.RED, AccentColor.PURPLE)) {
                val c = Color(if (AppColors.isDark) a.dark else a.light)
                // Gewaehlte Farbe: Ring in Schriftfarbe.
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (s.accentColor == a) AppColors.Text else c)
                        .clickable { vm.setAccentColor(a) }.padding(if (s.accentColor == a) 3.dp else 0.dp)
                        .clip(CircleShape).background(c),
                )
            }
        }
    }
}

