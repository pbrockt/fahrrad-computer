package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.sensors.SensorKind
import com.pbrockt.fahrradcomputer.trainer.BroadcastStatus
import com.pbrockt.fahrradcomputer.trainer.VirtualPower
import kotlin.math.roundToInt

/**
 * Heimtrainer ohne Leistungsmesser: virtuelle Watt aus Puls und Trittfrequenz, live an
 * Zwift, Rouvy oder MyWhoosh auf einem PC/Tablet gesendet.
 */
@Composable
fun TrainerScreen(vm: MainViewModel, onStart: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.trainerStats.collectAsStateWithLifecycle()
    val broadcast by vm.broadcast.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (stats.running) "${stats.watts}" else "–",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Accent,
                )
                Text("Watt", color = AppColors.Dim)
                if (stats.running) Text(stats.source.label, color = AppColors.Dim, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Value("Puls", stats.heartRate?.toString() ?: "–", "bpm")
                    Value("Trittfrequenz", stats.cadence?.toString() ?: "–", "U/min")
                    Value("Kalorien", stats.calories.roundToInt().toString(), "kcal")
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Value("Zeit", formatDuration(stats.elapsedMs), "")
                    Value("Ø Leistung", stats.avgWatts.toString(), "W")
                    Value("Max", stats.maxWatts.toString(), "W")
                }
            }
        }

        Text("Widerstandsstufe am Heimtrainer", fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { vm.setTrainerLevel(s.trainerLevel - 1) }, enabled = s.trainerLevel > 1) {
                Text("–", fontSize = 22.sp)
            }
            Text("${s.trainerLevel}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.setTrainerLevel(s.trainerLevel + 1) }, enabled = s.trainerLevel < 10) {
                Text("+", fontSize = 22.sp)
            }
            Text("von 10", color = AppColors.Dim)
        }
        Text(
            "Beim Drehen am Rad hier mitstellen - dann ändern sich die Watt sofort, nicht erst mit dem Puls.",
            color = AppColors.Dim, fontSize = 13.sp,
        )

        if (stats.running) {
            Button(
                onClick = vm::stopTrainer,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Stop),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Beenden", fontSize = 18.sp) }
        } else {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Go),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Starten & an Zwift senden", fontSize = 18.sp) }
        }
        BroadcastInfo(broadcast.status, broadcast.advertisedName, broadcast.clients, broadcast.error)

        val missing = buildList {
            if (s.device(SensorKind.HEART_RATE) == null) add("Pulsgurt")
            if (s.device(SensorKind.CADENCE_SPEED) == null) add("Trittfrequenz-Sensor")
        }
        if (missing.isNotEmpty()) {
            Text("Noch nicht gekoppelt: ${missing.joinToString(", ")} - unter „Sensoren“.", color = AppColors.Stop)
        }
        if (s.weightKg <= 0 || s.birthYear <= 0 || s.sex == null) {
            Text(
                "Für passende Watt und Kalorien bitte Gewicht, Geburtsjahr und Geschlecht in den Einstellungen eintragen.",
                color = AppColors.Stop,
            )
        }

        Text("Berechnung", fontWeight = FontWeight.Bold)
        IntField("Ruhepuls (morgens im Liegen)", s.restHr) { if (it in 30..110) vm.setRestHr(it) }
        Text("Maximale Herzfrequenz: ${s.maxHr} (in den Einstellungen)", color = AppColors.Dim)
        val estimated = VirtualPower.estimateFtp(s.body).roundToInt()
        IntField("FTP in Watt (leer = geschätzt: $estimated W)", s.trainerFtp.takeIf { it > 0 }) {
            // Leer = schaetzen; Zwischenstaende beim Tippen ("2" auf dem Weg zu "250") ignorieren.
            if (it == 0 || it in 50..600) vm.setTrainerFtp(it)
        }
        Text("Korrektur: ${s.trainerCalibration} %")
        Slider(
            value = s.trainerCalibration.toFloat(),
            onValueChange = { vm.setTrainerCalibration((it / 5).roundToInt() * 5) },
            valueRange = 50f..150f,
            steps = 19,
        )
        Text(
            "Fühlt sich Zwift dauerhaft zu leicht an, Korrektur runter; zu schwer, rauf.",
            color = AppColors.Dim, fontSize = 13.sp,
        )
        OutlinedButton(onClick = vm::resetTrainerLearning, enabled = !stats.running, modifier = Modifier.fillMaxWidth()) {
            Text("Gelerntes zurücksetzen")
        }
        Text(
            "So rechnet die App: Die Trittfrequenz (mal Widerstandsstufe) bestimmt sofort, wie sich die " +
                "Watt ändern. Der Puls legt über die Minuten das Niveau fest - die App lernt dabei, wie schwer " +
                "dein Heimtrainer ist, und merkt sich das für das nächste Mal. Die ersten 5-10 Minuten sind " +
                "deshalb noch ungenauer. Kalorien = Watt (kJ ≈ kcal). " +
                "Gelernt: ${if (s.trainerGain > 0) "${s.trainerGain.roundToInt()} W bei 90 U/min auf Stufe 5" else "noch nichts"}.",
            color = AppColors.Dim, fontSize = 13.sp,
        )

        Text("So koppelst du Zwift, Rouvy oder MyWhoosh", fontWeight = FontWeight.Bold)
        Text(
            "1. Hier „Starten“ tippen und kurz treten.\n" +
                "2. Am PC/Tablet in der App „Geräte koppeln“ öffnen.\n" +
                "3. Bei „Leistung“ und bei „Herzfrequenz“ dieses Handy wählen (Name siehe oben).\n" +
                "4. Den echten Brustgurt dort NICHT koppeln - den Puls schickt das Handy mit.\n" +
                "5. Trittfrequenz kommt automatisch mit dem Leistungsmesser.\n" +
                "Das Handy kann dabei gesperrt sein, die App sendet weiter.",
            color = AppColors.Dim,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Value(label: String, value: String, unit: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = AppColors.Dim, fontSize = 12.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (unit.isNotEmpty()) Text(unit, color = AppColors.Dim, fontSize = 12.sp)
    }
}

@Composable
private fun BroadcastInfo(status: BroadcastStatus, name: String?, clients: List<String>, error: String?) {
    val (text, color) = when (status) {
        BroadcastStatus.OFF -> "Sendet nicht." to AppColors.Dim
        BroadcastStatus.STARTING -> "Bluetooth wird vorbereitet …" to AppColors.Dim
        BroadcastStatus.ERROR -> (error ?: "Fehler beim Senden.") to AppColors.Stop
        BroadcastStatus.ADVERTISING -> if (clients.isEmpty()) {
            "Sichtbar${name?.let { " als „$it“" } ?: ""} - jetzt in Zwift koppeln." to AppColors.Accent
        } else {
            "Verbunden mit: ${clients.joinToString(", ")}" to AppColors.Go
        }
    }
    Text(text, color = color)
}

@Composable
private fun IntField(label: String, value: Int?, onValid: (Int) -> Unit) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(value) { if (text.isEmpty() && value != null) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            if (t.isBlank()) onValid(0) else t.toIntOrNull()?.let(onValid)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
