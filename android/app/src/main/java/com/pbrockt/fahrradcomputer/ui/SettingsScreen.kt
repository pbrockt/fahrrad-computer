package com.pbrockt.fahrradcomputer.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.update.UpdateChannel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()

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
            "Gewicht (kg) – für die Kalorien",
            if (s.weightKg > 0) formatDecimal(s.weightKg) else "",
        ) { it.replace(',', '.').toDoubleOrNull()?.let(vm::setWeight) }

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

@Composable
private fun TokenField(value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text.isEmpty()) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text("GitHub-Token (Repo ist privat)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
