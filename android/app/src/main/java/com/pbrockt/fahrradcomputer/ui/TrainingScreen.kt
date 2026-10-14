package com.pbrockt.fahrradcomputer.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.coach.PlanMatching
import com.pbrockt.fahrradcomputer.coach.PlannedSession
import com.pbrockt.fahrradcomputer.coach.SessionKind
import com.pbrockt.fahrradcomputer.coach.SessionStatus
import com.pbrockt.fahrradcomputer.coach.StoredPlan
import com.pbrockt.fahrradcomputer.recording.RideSummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val GOAL_PRESETS = listOf("Ausdauer aufbauen", "Abnehmen", "Fitter für Tagestouren", "100 km am Stück")

@Composable
fun TrainingScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stored by vm.plan.collectAsStateWithLifecycle()
    val hasKey by vm.hasCoachKey.collectAsStateWithLifecycle()
    val busy by vm.coachBusy.collectAsStateWithLifecycle()
    val rides by vm.rides.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stored?.let { PlanView(it, rides, vm) }

        if (!hasKey) KeySetup(vm) else KeyRow(vm, busy)

        Text("Dein Ziel", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            GOAL_PRESETS.take(2).forEach { g ->
                FilterChip(selected = settings.coachGoal == g, onClick = { vm.setCoachGoal(g) }, label = { Text(g) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            GOAL_PRESETS.drop(2).forEach { g ->
                FilterChip(selected = settings.coachGoal == g, onClick = { vm.setCoachGoal(g) }, label = { Text(g) })
            }
        }
        SavedTextField("oder eigenes Ziel", settings.coachGoal, vm::setCoachGoal)

        Text("Trainingstage", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in DayOfWeek.entries) {
                FilterChip(
                    selected = d in settings.coachDays,
                    onClick = { vm.toggleCoachDay(d) },
                    label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.GERMANY).take(2)) },
                )
            }
        }
        SavedTextField("Hinweise (z. B. Knie, wenig Zeit unter der Woche)", settings.coachNotes, vm::setCoachNotes)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { preview = vm.coachPreview() }, modifier = Modifier.weight(1f)) {
                Text("Was wird gesendet?")
            }
            Button(onClick = vm::createPlan, enabled = hasKey && !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (stored == null) "Plan erstellen" else "Neuer Plan ab heute")
            }
        }
        Text(
            "Der Plan wird mit Claude (Anthropic) erstellt. Gesendet werden nur Trainingswerte der " +
                "letzten 6 Wochen, Alter, Gewicht und dein Ziel – kein Name, keine Strecken oder Orte. " +
                "Kosten: etwa 5–15 Cent pro Plan, abgerechnet über dein Anthropic-Konto.",
            color = AppColors.Dim, fontSize = 13.sp,
        )
    }

    preview?.let { text ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Das geht an Claude") },
            text = {
                Text(
                    text, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("OK") } },
        )
    }
}

/** Schritt-fuer-Schritt-Anleitung zum API-Key - direkt in der App, ohne Vorwissen. */
@Composable
private fun KeySetup(vm: MainViewModel) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Einrichtung: API-Key von Anthropic", fontWeight = FontWeight.Bold)
            Text(
                "1. Konto anlegen bzw. anmelden (Knopf unten).\n" +
                    "2. Unter „Billing“ Guthaben aufladen, z. B. 5 \$, und ein Monatslimit setzen.\n" +
                    "3. Unter „API Keys“ auf „Create Key“, Name z. B. „Fahrrad-Computer“.\n" +
                    "4. Den Key kopieren – er wird nur einmal angezeigt – und hier einfügen.",
                fontSize = 14.sp,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Anthropic-Konsole öffnen") }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("API-Key (beginnt mit sk-ant-)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.setCoachKey(key); key = "" },
                enabled = key.startsWith("sk-ant-"),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern und prüfen") }
            Text(
                "Der Key wird verschlüsselt nur auf diesem Handy gespeichert.",
                color = AppColors.Dim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun KeyRow(vm: MainViewModel, busy: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✓ API-Key gespeichert", color = AppColors.Go, modifier = Modifier.weight(1f))
        TextButton(onClick = vm::checkCoachKey, enabled = !busy) { Text("Prüfen") }
        TextButton(onClick = { confirm = true }) { Text("Entfernen") }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("API-Key entfernen?") },
            text = { Text("Danach lassen sich keine neuen Pläne erstellen. Der Key selbst bleibt bei Anthropic gültig – dort kannst du ihn bei Bedarf löschen.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.setCoachKey("") }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE dd.MM.", Locale.GERMANY)

@Composable
private fun PlanView(stored: StoredPlan, rides: List<RideSummary>, vm: MainViewModel) {
    val plan = stored.plan
    val today = LocalDate.now()
    var confirmDelete by remember { mutableStateOf(false) }
    Text("Dein Wochenplan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    Text(plan.focus, fontWeight = FontWeight.Bold)
    Text(plan.assessment, color = AppColors.Dim)
    for (s in plan.sessions) SessionRow(s, PlanMatching.status(s, rides, today), s.localDate == today)
    if (plan.tips.isNotEmpty()) {
        Text("Tipps", fontWeight = FontWeight.Bold)
        plan.tips.forEach { Text("• $it", fontSize = 14.sp) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val created = Instant.ofEpochMilli(stored.createdAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        Text(
            "Erstellt am ${created.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}" +
                (stored.costUsd?.let { " · Kosten ca. ${String.format(Locale.GERMANY, "%.2f", it)} \$" } ?: ""),
            color = AppColors.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { confirmDelete = true }) { Text("Plan löschen") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Plan löschen?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlan() }) { Text("Löschen", color = AppColors.Stop) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SessionRow(s: PlannedSession, status: SessionStatus, isToday: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    (s.localDate?.format(DAY) ?: s.date) + if (isToday) " · heute" else "",
                    color = if (isToday) AppColors.Accent else AppColors.Dim, fontSize = 13.sp,
                )
                Text(
                    if (s.kind == SessionKind.REST) "Ruhetag" else "${s.title} · ${s.durationMin} min",
                    fontWeight = FontWeight.Bold,
                )
                if (s.kind != SessionKind.REST && s.description.isNotBlank()) {
                    Text(s.description, fontSize = 13.sp, color = AppColors.Dim)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (s.kind != SessionKind.REST && s.targetZone in 1..5) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(AppColors.zone(s.targetZone))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("Z${s.targetZone}", color = AppColors.OnZone, fontWeight = FontWeight.Bold) }
                }
                when (status) {
                    SessionStatus.DONE -> Text("✓ erledigt", color = AppColors.Go, fontSize = 12.sp)
                    SessionStatus.MISSED -> Text("ausgelassen", color = AppColors.Stop, fontSize = 12.sp)
                    else -> Unit
                }
            }
        }
    }
}

/** Textfeld, das lokal tippt und jeden Stand speichert. */
@Composable
private fun SavedTextField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (text != value && text.isEmpty()) text = value }
    // Chips setzen das Ziel von aussen - dann das Feld mitziehen.
    LaunchedEffect(value) { if (value in GOAL_PRESETS) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}
