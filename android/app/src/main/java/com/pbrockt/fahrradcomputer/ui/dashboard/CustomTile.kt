package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.Condensed
import com.pbrockt.fahrradcomputer.ui.Palette
import com.pbrockt.fahrradcomputer.ui.formatDecimal
import com.pbrockt.fahrradcomputer.ui.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Werte fuer die "eigene Kachel": man stellt 1-3 davon zu einer Kachel zusammen.
 * [key] steht im gespeicherten Layout - nie umbenennen.
 */
enum class Metric(val key: String, val label: String, val unit: String, val value: (TileData) -> String?) {
    SPEED("speed", "Tempo", "km/h", { d -> d.riding { formatDecimal(d.stats.speedKmh) } }),
    AVG_SPEED("avg_speed", "Ø Tempo", "km/h", { d -> d.riding { formatDecimal(d.stats.avgSpeedKmh) } }),
    MAX_SPEED("max_speed", "Max. Tempo", "km/h", { d -> d.riding { formatDecimal(d.stats.maxSpeedKmh) } }),
    DISTANCE("distance", "Distanz", "km", { d -> formatDecimal(d.stats.distanceM / 1000, 2) }),
    MOVING_TIME("moving", "Fahrzeit", "", { d -> formatDuration(d.stats.movingTimeMs) }),
    CLOCK("clock", "Uhrzeit", "", { d -> CLOCK_FMT.format(Instant.ofEpochMilli(d.now).atZone(ZoneId.systemDefault())) }),
    HEART_RATE("hr", "Puls", "bpm", { d -> d.bpm?.toString() }),
    AVG_HR("avg_hr", "Ø Puls", "bpm", { d -> d.stats.avgHr?.toString() }),
    POWER("power", "Leistung", "W", { d -> d.watts?.toString() }),
    AVG_POWER("avg_power", "Ø Leistung", "W", { d -> d.stats.avgPower?.toString() }),
    POWER_KG("power_kg", "Leistung/kg", "W/kg", { d -> d.watts?.takeIf { d.weightKg > 0 }?.let { formatDecimal(it / d.weightKg) } }),
    CADENCE("cadence", "Trittfrequenz", "U/min", { d -> d.rpm?.toString() }),
    AVG_CADENCE("avg_cadence", "Ø Trittfrequenz", "U/min", { d -> d.stats.avgCadence?.toString() }),
    CALORIES("kcal", "Kalorien", "kcal", { d -> d.stats.calories.roundToInt().toString() }),
    ALTITUDE("altitude", "Höhe", "m", { d -> d.stats.altitude?.roundToInt()?.toString() }),
    GRADE("grade", "Steigung", "%", { d -> d.gradePct?.let { formatDecimal(it) } }),
    ASCENT("ascent", "Höhenmeter", "m", { d -> d.riding { d.climb.ascentM.roundToInt().toString() } }),
    DESCENT("descent", "Abstieg", "m", { d -> d.riding { d.climb.descentM.roundToInt().toString() } }),
    VAM("vam", "VAM", "m/h", { d -> d.riding { d.climb.vamMh?.roundToInt()?.toString() } }),
    REMAINING("remaining", "Rest der Route", "km", { d -> d.progress?.let { formatDecimal(it.remainingM / 1000) } }),
    REMAINING_ASCENT("rest_hm", "Rest Höhenmeter", "m", { d ->
        d.activeRoute?.profile?.ascentRemainingM(d.progress?.doneM ?: 0.0)?.roundToInt()?.toString()
    }),
    TEMPERATURE("temp", "Temperatur", "°C", { d -> d.weather?.temperatureC?.roundToInt()?.toString() }),
    PHONE_BATTERY("battery", "Handy-Akku", "%", { d -> d.phoneBattery?.toString() }),
    ;

    companion object {
        fun of(key: String?): Metric? = entries.firstOrNull { it.key == key }
    }
}

/** Farben fuer die eigene Kachel; [key] steht im Layout. */
enum class CustomColor(val key: String, val label: String, val color: (Palette) -> Color) {
    TEXT("text", "Standard", { it.Text }),
    ACCENT("accent", "Hervorhebung", { it.Accent }),
    BLUE("blue", "Blau", { it.Speed }),
    GREEN("green", "Grün", { it.Distance }),
    ORANGE("orange", "Orange", { it.Cadence }),
    RED("red", "Rot", { it.Stop }),
    PURPLE("purple", "Lila", { it.Altitude }),
    ;

    companion object {
        fun of(key: String?): CustomColor = entries.firstOrNull { it.key == key } ?: TEXT
    }
}

/** Optionen der eigenen Kachel im Layout. */
object CustomTileKeys {
    const val NAME = "name"
    val VALUES = listOf("v1", "v2", "v3")
    const val COLOR = "color"
}

private val CLOCK_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** Vor der Fahrt gibt es noch nichts zu zeigen. */
private inline fun TileData.riding(f: () -> String?): String? = if (phase == RidePhase.IDLE) null else f()

/** Die gewaehlten Werte einer eigenen Kachel; ohne Auswahl: Tempo. */
fun customMetrics(options: Map<String, String>): List<Metric> =
    CustomTileKeys.VALUES.mapNotNull { Metric.of(options[it]) }.ifEmpty { listOf(Metric.SPEED) }

/** Selbst zusammengestellte Kachel: ein Wert gross, zwei oder drei untereinander. */
@Composable
internal fun CustomTile(d: TileData, options: Map<String, String>, modifier: Modifier) {
    val metrics = customMetrics(options)
    val name = options[CustomTileKeys.NAME]?.trim().orEmpty()
    val color = CustomColor.of(options[CustomTileKeys.COLOR]).color(AppColors)
    if (metrics.size == 1) {
        val m = metrics[0]
        ValueTile(name.ifEmpty { m.label }, m.value(d) ?: "--", modifier, m.unit, color)
        return
    }
    BoxWithConstraints(modifier.background(AppColors.Card)) {
        val labelSize = (maxHeight.value * 0.09f).coerceIn(11f, 16f).sp
        // Zeilen teilen sich die Hoehe; die Zahl so gross, wie die Zeile es erlaubt.
        val valueSize = ((maxHeight.value - if (name.isEmpty()) 8f else 28f) / metrics.size * 0.62f).coerceIn(14f, 64f).sp
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (name.isNotEmpty()) TileLabel(name, "", color, labelSize)
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                for (m in metrics) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.label + if (m.unit.isNotEmpty()) " · ${m.unit}" else "", color = AppColors.Dim,
                            fontSize = labelSize, fontFamily = Condensed, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            m.value(d) ?: "--", color = color, fontSize = valueSize, lineHeight = valueSize,
                            fontFamily = Condensed, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
