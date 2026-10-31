package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.MainViewModel
import com.pbrockt.fahrradcomputer.ui.AppColors

private typealias Edit = ((DashboardLayout) -> DashboardLayout) -> Unit

/**
 * Eine Seite im Bearbeiten-Modus: Kachel antippen = Inhalt/Breite aendern,
 * Menue rechts neben der Zeile = Zeile verschieben, Hoehe, Kachel dazu, loeschen.
 * Die Kacheln zeigen dabei weiter echte Werte - so sieht man sofort, wie es aussieht.
 */
@Composable
fun EditablePage(page: DashboardPage, pageIndex: Int, data: TileData, onEdit: Edit) {
    var tileDialog by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    if (page.rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Leere Seite – unten „+ Zeile“ antippen.", color = AppColors.Dim)
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        page.rows.forEachIndexed { r, row ->
            Row(
                Modifier.weight(row.height.weight).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.tiles.forEachIndexed { c, slot ->
                    Tile(
                        slot.type, data,
                        Modifier.weight(slot.units.toFloat()).fillMaxSize()
                            .clip(TILE_SHAPE).border(2.dp, AppColors.Accent, TILE_SHAPE)
                            .clickable { tileDialog = r to c },
                        options = slot.options,
                    )
                }
                RowMenu(page, pageIndex, r, onEdit)
            }
        }
    }

    tileDialog?.let { (r, c) ->
        // Fehlt die Kachel (gerade entfernt), einfach keinen Dialog zeigen.
        val slot = page.rows.getOrNull(r)?.tiles?.getOrNull(c)
        if (slot != null) {
            val rowTiles = page.rows[r].tiles.size
            TileDialog(
                slot = slot,
                canWiden = LayoutEditor.canWiden(page.rows[r], c),
                move = TileMoves(
                    left = if (c > 0) ({ onEdit { LayoutEditor.moveTile(it, pageIndex, r, c, -1) }; tileDialog = r to c - 1 }) else null,
                    right = if (c < rowTiles - 1) ({ onEdit { LayoutEditor.moveTile(it, pageIndex, r, c, 1) }; tileDialog = r to c + 1 }) else null,
                    up = (r - 1).takeIf { LayoutEditor.canMoveToRow(layoutOf(page), 0, r, c, it) }?.let { t ->
                        { val (_, pos) = LayoutEditor.moveTileToRow(layoutOf(page), 0, r, c, t); onEdit { LayoutEditor.moveTileToRow(it, pageIndex, r, c, t).first }; tileDialog = pos }
                    },
                    down = (r + 1).takeIf { LayoutEditor.canMoveToRow(layoutOf(page), 0, r, c, it) }?.let { t ->
                        { val (_, pos) = LayoutEditor.moveTileToRow(layoutOf(page), 0, r, c, t); onEdit { LayoutEditor.moveTileToRow(it, pageIndex, r, c, t).first }; tileDialog = pos }
                    },
                    ownRow = if (rowTiles > 1) ({ onEdit { LayoutEditor.moveTileToNewRow(it, pageIndex, r, c) }; tileDialog = r + 1 to 0 }) else null,
                ),
                onType = { t -> onEdit { LayoutEditor.setTileType(it, pageIndex, r, c, t) } },
                onToggleWide = { onEdit { LayoutEditor.toggleWide(it, pageIndex, r, c) } },
                onOption = { key, value -> onEdit { LayoutEditor.setTileOption(it, pageIndex, r, c, key, value) } },
                onRemove = {
                    tileDialog = null
                    onEdit { LayoutEditor.removeTile(it, pageIndex, r, c) }
                },
                onDismiss = { tileDialog = null },
            )
        }
    }
}

/** Die Seite allein als Layout - fuer die Berechnung, wo eine verschobene Kachel landet. */
private fun layoutOf(page: DashboardPage) = DashboardLayout(listOf(page))

/** Wohin sich die Kachel verschieben laesst; null = geht gerade nicht. */
private class TileMoves(
    val left: (() -> Unit)?,
    val right: (() -> Unit)?,
    val up: (() -> Unit)?,
    val down: (() -> Unit)?,
    val ownRow: (() -> Unit)?,
)

@Composable
private fun RowMenu(page: DashboardPage, pageIndex: Int, r: Int, onEdit: Edit) {
    var open by remember { mutableStateOf(false) }
    val row = page.rows[r]
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.width(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Zeile bearbeiten")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Item(text: String, enabled: Boolean = true, change: (DashboardLayout) -> DashboardLayout) {
                DropdownMenuItem(
                    text = { Text(text) },
                    enabled = enabled,
                    onClick = { open = false; onEdit(change) },
                )
            }
            Item("Höhe: ${row.height.label} → ${row.height.next().label}") {
                LayoutEditor.cycleHeight(it, pageIndex, r)
            }
            Item("Kachel hinzufügen", enabled = row.units < DashboardLayout.MAX_ROW_UNITS) {
                LayoutEditor.addTile(it, pageIndex, r)
            }
            Item("Nach oben", enabled = r > 0) { LayoutEditor.moveRow(it, pageIndex, r, -1) }
            Item("Nach unten", enabled = r < page.rows.lastIndex) { LayoutEditor.moveRow(it, pageIndex, r, 1) }
            HorizontalDivider()
            Item("Zeile löschen") { LayoutEditor.removeRow(it, pageIndex, r) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TileDialog(
    slot: TileSlot,
    canWiden: Boolean,
    move: TileMoves,
    onType: (TileType) -> Unit,
    onToggleWide: () -> Unit,
    onOption: (String, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kachel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Verschieben ganz oben - das braucht man beim Sortieren immer wieder.
                Text("Verschieben", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    @Composable
                    fun MoveChip(text: String, action: (() -> Unit)?) {
                        androidx.compose.material3.AssistChip(onClick = { action?.invoke() }, enabled = action != null, label = { Text(text) })
                    }
                    MoveChip("← Links", move.left)
                    MoveChip("Rechts →", move.right)
                    MoveChip("↑ Zeile darüber", move.up)
                    MoveChip("↓ Zeile darunter", move.down)
                    MoveChip("Eigene Zeile", move.ownRow)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (slot.type == TileType.CUSTOM) {
                    CustomTileSettings(slot.options, onOption)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                // Einstellungen der gewaehlten Kachel zuerst - die lange Liste kommt danach.
                val opts = TileOptions.forType(slot.type)
                if (opts.isNotEmpty()) {
                    Text(
                        "EINSTELLUNGEN: ${slot.type.label.uppercase(java.util.Locale.GERMANY)}", color = AppColors.Accent, fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    for (o in opts) {
                        Text(o.label, modifier = Modifier.padding(top = 6.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        ) {
                            for ((value, label) in o.choices) {
                                androidx.compose.material3.FilterChip(
                                    selected = slot.options.opt(o) == value,
                                    onClick = { onOption(o.key, value) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                for ((category, types) in TileType.entries.groupBy { it.category }) {
                  Text(
                      category.label.uppercase(java.util.Locale.GERMANY), color = AppColors.Dim, fontSize = 13.sp,
                      fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                      modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                  )
                  for (t in types) {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = slot.type == t, role = Role.RadioButton) { onType(t) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = slot.type == t, onClick = null)
                        Text(t.label, Modifier.padding(start = 8.dp))
                    }
                  }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Doppelte Breite")
                        if (!canWiden) Text("Zeile ist voll", color = AppColors.Dim, fontSize = 12.sp)
                    }
                    Switch(checked = slot.wide, onCheckedChange = { onToggleWide() }, enabled = canWiden)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = {
            TextButton(onClick = onRemove) { Text("Entfernen", color = AppColors.Stop) }
        },
    )
}

/** Eigene Kachel: Name, bis zu drei Werte, Farbe. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomTileSettings(options: Map<String, String>, onOption: (String, String) -> Unit) {
    Text("EIGENE KACHEL", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    var name by remember { mutableStateOf(options[CustomTileKeys.NAME].orEmpty()) }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(30); onOption(CustomTileKeys.NAME, name) },
        label = { Text("Name (leer = Name des Werts)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CustomTileKeys.VALUES.forEachIndexed { i, key ->
        Text(if (i == 0) "Wert 1" else "Wert ${i + 1} (optional)", modifier = Modifier.padding(top = 8.dp))
        var open by remember { mutableStateOf(false) }
        val current = Metric.of(options[key])
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(current?.label ?: if (i == 0) "Tempo" else "– keiner –")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (i > 0) DropdownMenuItem(text = { Text("– keiner –") }, onClick = { open = false; onOption(key, "") })
                for (m in Metric.entries) {
                    DropdownMenuItem(
                        text = { Text(m.label + if (m.unit.isNotEmpty()) " (${m.unit})" else "") },
                        onClick = { open = false; onOption(key, m.key) },
                    )
                }
            }
        }
    }
    Text("Farbe", modifier = Modifier.padding(top = 8.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val selected = CustomColor.of(options[CustomTileKeys.COLOR])
        for (c in CustomColor.entries) {
            androidx.compose.material3.FilterChip(
                selected = selected == c,
                onClick = { onOption(CustomTileKeys.COLOR, c.key) },
                label = { Text(c.label, color = c.color(AppColors)) },
            )
        }
    }
}

/** Leiste unten im Bearbeiten-Modus - ersetzt dort Start/Pause/Stopp. */
@Composable
fun EditBar(
    layout: DashboardLayout,
    /** Index der bearbeiteten Dashboard-Seite (ohne die Start-Seite). */
    page: Int,
    scrollToPage: suspend (Int) -> Unit,
    vm: MainViewModel,
    onDone: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var scrollToLast by remember { mutableStateOf(false) }
    var choosingTemplate by remember { mutableStateOf(false) }
    // Nach "Seite verschieben" mitblaettern, sobald das neue Layout da ist.
    var pendingScroll by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(layout) {
        pendingScroll?.let { scrollToPage(it); pendingScroll = null }
    }

    // Nach "Neue Seite" dorthin blaettern, sobald sie im gespeicherten Layout angekommen ist.
    LaunchedEffect(layout.pages.size) {
        if (scrollToLast) {
            scrollToPage(layout.pages.lastIndex)
            scrollToLast = false
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { vm.editDashboard { LayoutEditor.addRow(it, page) } },
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text("+ Zeile") }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Seite …")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Neue Seite …") }, onClick = { menu = false; choosingTemplate = true })
                DropdownMenuItem(
                    text = { Text("← Seite nach links") },
                    enabled = page > 0,
                    onClick = { menu = false; pendingScroll = page - 1; vm.editDashboard { LayoutEditor.movePage(it, page, -1) } },
                )
                DropdownMenuItem(
                    text = { Text("Seite nach rechts →") },
                    enabled = page < layout.pages.lastIndex,
                    onClick = { menu = false; pendingScroll = page + 1; vm.editDashboard { LayoutEditor.movePage(it, page, 1) } },
                )
                DropdownMenuItem(text = { Text("Umbenennen") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(
                    text = { Text("Seite löschen") },
                    enabled = layout.pages.size > 1,
                    onClick = { menu = false; confirmDelete = true },
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Alles auf Standard") }, onClick = { menu = false; confirmReset = true })
            }
        }
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Go, contentColor = AppColors.OnZone),
            modifier = Modifier.weight(1f).height(56.dp),
        ) { Text("Fertig", fontWeight = FontWeight.Bold) }
    }

    if (choosingTemplate) {
        AlertDialog(
            onDismissRequest = { choosingTemplate = false },
            title = { Text("Neue Seite") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Als Vorlage nehmen – danach frei anpassen:", color = AppColors.Dim)
                    @Composable
                    fun Choice(title: String, detail: String, add: (DashboardLayout) -> DashboardLayout) {
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                choosingTemplate = false
                                scrollToLast = true
                                vm.editDashboard(add)
                            }.padding(vertical = 10.dp),
                        ) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(detail, color = AppColors.Dim, fontSize = 13.sp)
                        }
                        HorizontalDivider()
                    }
                    Choice("Leer", "Eine Kachel – alles Weitere selbst") { LayoutEditor.addPage(it, "Seite ${it.pages.size + 1}") }
                    for (t in PageTemplates.ALL) {
                        Choice(t.name, t.rows.flatMap { r -> r.tiles.map { it.type.label.substringBefore(" (") } }.joinToString(", ")) {
                            LayoutEditor.addPageFrom(it, t)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingTemplate = false }) { Text("Abbrechen") } },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(layout.pages.getOrNull(page)?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Seite umbenennen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    vm.editDashboard { LayoutEditor.renamePage(it, page, name) }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Seite „${layout.pages.getOrNull(page)?.name}“ löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.editDashboard { LayoutEditor.removePage(it, page) }
                }) { Text("Löschen", color = AppColors.Stop) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Alles auf Standard?") },
            text = { Text("Alle Seiten werden durch die mitgelieferten ersetzt.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetDashboard() }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
        )
    }
}
