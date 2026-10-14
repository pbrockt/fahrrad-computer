package com.pbrockt.fahrradcomputer.routes

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

enum class TurnKind(val label: String, val arrow: String) {
    SLIGHT_LEFT("leicht links", "↖"),
    LEFT("links", "←"),
    SHARP_LEFT("scharf links", "↙"),
    SLIGHT_RIGHT("leicht rechts", "↗"),
    RIGHT("rechts", "→"),
    SHARP_RIGHT("scharf rechts", "↘"),
    U_TURN("wenden", "↶");

    companion object {
        /** [angle] in Grad, positiv = rechts. */
        fun of(angle: Double): TurnKind {
            val a = abs(angle)
            val right = angle > 0
            return when {
                a >= 160 -> U_TURN
                a >= 115 -> if (right) SHARP_RIGHT else SHARP_LEFT
                a >= 60 -> if (right) RIGHT else LEFT
                else -> if (right) SLIGHT_RIGHT else SLIGHT_LEFT
            }
        }
    }
}

/** Eine Abbiegung, [atM] Meter nach dem Start der Route. */
data class Turn(val atM: Double, val angle: Double, val kind: TurnKind, val lat: Double, val lon: Double)

/**
 * Findet Abbiegungen allein aus der Form der Route - GPX-Dateien von Komoot & Co.
 * enthalten meist keine Anweisungen, nur die Linie.
 *
 * 1. Die Route wird mit Douglas-Peucker vereinfacht: Punkte, die weniger als [SIMPLIFY_M]
 *    von der Linie abweichen, fallen weg. So erzeugt das Zittern der Aufzeichnung keine
 *    Scheinabbiegungen, echte Knicke bleiben als Eckpunkte stehen.
 * 2. An jedem Eckpunkt wird der Winkel zwischen ankommender und abgehender Richtung
 *    gemessen.
 * 3. Eckpunkte, die naeher als [MERGE_M] beieinander liegen, sind eine Abbiegung
 *    (z. B. zwei 45-Grad-Knicke an einer Einmuendung) - ihre Winkel werden addiert.
 * 4. Ab [MIN_ANGLE] Grad gilt es als Abbiegung.
 */
object TurnDetector {

    const val SIMPLIFY_M = 8.0
    const val MERGE_M = 25.0
    const val MIN_ANGLE = 35.0

    fun detect(route: Route): List<Turn> {
        val pts = route.points
        if (pts.size < 3) return emptyList()

        // Lokale Ebene in Metern um den Startpunkt - fuer Radtouren genau genug.
        val lat0 = pts.first().lat
        val mLat = 111_320.0
        val mLon = 111_320.0 * cos(Math.toRadians(lat0))
        val xs = DoubleArray(pts.size) { (pts[it].lon - pts.first().lon) * mLon }
        val ys = DoubleArray(pts.size) { (pts[it].lat - lat0) * mLat }

        val kept = simplify(xs, ys)
        if (kept.size < 3) return emptyList()

        // Winkel je Eckpunkt.
        class Corner(val index: Int, val angle: Double)
        val corners = (1 until kept.lastIndex).map { k ->
            val a = kept[k - 1]
            val b = kept[k]
            val c = kept[k + 1]
            Corner(b, turnAngle(bearing(xs, ys, a, b), bearing(xs, ys, b, c)))
        }

        // Nahe Eckpunkte zu einer Abbiegung zusammenfassen.
        val turns = mutableListOf<Turn>()
        var i = 0
        while (i < corners.size) {
            var j = i
            var sum = corners[i].angle
            while (j + 1 < corners.size &&
                route.cumulative[corners[j + 1].index] - route.cumulative[corners[j].index] < MERGE_M
            ) {
                j++
                sum += corners[j].angle
            }
            if (abs(sum) >= MIN_ANGLE) {
                val at = corners[i].index
                turns += Turn(route.cumulative[at], sum, TurnKind.of(sum), pts[at].lat, pts[at].lon)
            }
            i = j + 1
        }
        return turns
    }

    /** Douglas-Peucker ohne Rekursion (lange Routen wuerden sonst den Stack sprengen). */
    private fun simplify(xs: DoubleArray, ys: DoubleArray): List<Int> {
        val keep = BooleanArray(xs.size)
        keep[0] = true
        keep[xs.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to xs.lastIndex)
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            var maxD = 0.0
            var maxI = -1
            for (k in from + 1 until to) {
                val d = distanceToSegment(xs[k], ys[k], xs[from], ys[from], xs[to], ys[to])
                if (d > maxD) { maxD = d; maxI = k }
            }
            if (maxI >= 0 && maxD > SIMPLIFY_M) {
                keep[maxI] = true
                stack.addLast(from to maxI)
                stack.addLast(maxI to to)
            }
        }
        return keep.indices.filter { keep[it] }
    }

    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Richtung von a nach b in Grad, 0 = Nord, im Uhrzeigersinn. */
    private fun bearing(xs: DoubleArray, ys: DoubleArray, a: Int, b: Int): Double =
        Math.toDegrees(atan2(xs[b] - xs[a], ys[b] - ys[a]))

    /** Richtungsaenderung in (-180, 180], positiv = rechts. */
    private fun turnAngle(inDeg: Double, outDeg: Double): Double {
        var d = outDeg - inDeg
        while (d > 180) d -= 360
        while (d <= -180) d += 360
        return d
    }
}

/** Was als Naechstes kommt: eine Abbiegung oder das Ziel. */
data class Guidance(
    /** null = keine Abbiegung mehr, es geht zum Ziel. */
    val turn: Turn?,
    /** Index der Abbiegung in der Liste - damit jede nur einmal angekuendigt wird. */
    val turnIndex: Int,
    val distanceM: Double,
) {
    val destination: Boolean get() = turn == null

    /** "→ 180 m rechts" bzw. "Ziel in 350 m". */
    val text: String
        get() = if (turn == null) "Ziel in ${formatMeters(distanceM)}"
        else "${turn.kind.arrow} ${formatMeters(distanceM)} ${turn.kind.label}"

    companion object {
        /** Unter 1 km auf 10 m gerundet, darueber in km mit einer Stelle. */
        fun formatMeters(m: Double): String = when {
            m < 1000 -> "${(m / 10).toInt() * 10} m"
            else -> String.format(Locale.GERMANY, "%.1f km", m / 1000)
        }
    }
}

object Navigator {

    /** Eine Abbiegung gilt noch als "jetzt", bis man sie um so viele Meter hinter sich hat. */
    private const val PASSED_M = 10.0

    fun next(turns: List<Turn>, doneM: Double, lengthM: Double): Guidance {
        val i = turns.indexOfFirst { it.atM > doneM - PASSED_M }
        return if (i >= 0) {
            Guidance(turns[i], i, (turns[i].atM - doneM).coerceAtLeast(0.0))
        } else {
            Guidance(null, turns.size, (lengthM - doneM).coerceAtLeast(0.0))
        }
    }
}

/** Wann gemeldet wird: einmal weit vorher, einmal kurz davor - je Abbiegung nur einmal. */
enum class TurnAlert { FAR, NOW, ARRIVED }

class TurnAnnouncer {

    private val done = mutableSetOf<Pair<Int, TurnAlert>>()

    fun check(g: Guidance): TurnAlert? {
        val alert = when {
            g.destination && g.distanceM <= ARRIVED_M -> TurnAlert.ARRIVED
            g.destination -> null
            g.distanceM <= NOW_M -> TurnAlert.NOW
            g.distanceM <= FAR_M -> TurnAlert.FAR
            else -> null
        } ?: return null
        // Wer erst bei 20 m auf die Abbiegung trifft, bekommt nur "jetzt", nicht beides.
        if (alert == TurnAlert.NOW) done += g.turnIndex to TurnAlert.FAR
        return alert.takeIf { done.add(g.turnIndex to it) }
    }

    fun reset() = done.clear()

    companion object {
        const val FAR_M = 150.0
        const val NOW_M = 30.0
        const val ARRIVED_M = 20.0
    }
}
