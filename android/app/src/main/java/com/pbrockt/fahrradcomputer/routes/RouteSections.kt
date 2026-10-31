package com.pbrockt.fahrradcomputer.routes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Ein Stueck mit etwa gleicher Steigung - im Bild ein Block in einer Farbe. */
data class Ramp(val startM: Double, val endM: Double, val startEle: Double, val endEle: Double) {
    val lengthM: Double get() = endM - startM
    val gainM: Double get() = endEle - startEle
    val grade: Double get() = if (lengthM < 1) 0.0 else gainM / lengthM * 100
}

enum class Trend { UP, FLAT, DOWN }

/**
 * Anstieg, Abfahrt oder flaches Stueck der Route, aufgeteilt in hoechstens
 * [RouteSections.MAX_RAMPS] Rampen. [climb] ist gesetzt, wenn es ein richtiger Anstieg
 * aus [RouteProfile.climbs] ist.
 */
data class Section(
    val trend: Trend,
    val startM: Double,
    val endM: Double,
    val gainM: Double,
    val ramps: List<Ramp>,
    val climb: RouteProfile.Climb? = null,
) {
    val lengthM: Double get() = endM - startM
    val grade: Double get() = if (lengthM < 1) 0.0 else gainM / lengthM * 100
}

/**
 * Teilt ein Hoehenprofil in Abschnitte: die Anstiege aus [RouteProfile.climbs] bleiben
 * am Stueck (auch mit kurzen flachen Stellen), dazwischen wird nach Trend getrennt.
 */
object RouteSections {
    const val MAX_RAMPS = 10
    private const val BASE_M = 100.0
    private const val FLAT_GRADE = 2.0
    private const val MIN_SECTION_M = 400.0
    private const val MIN_SECTION_GAIN_M = 8.0
    /** Rampen, deren Steigung sich weniger unterscheidet, werden zusammengelegt. */
    private const val MERGE_DIFF = 1.5
    private const val MIN_RAMP_M = 150.0

    fun of(p: RouteProfile): List<Section> {
        val out = mutableListOf<Section>()
        var cursor = 0.0
        for (c in p.climbs) {
            if (c.startM > cursor) out += trendSections(p, cursor, c.startM)
            out += Section(Trend.UP, c.startM, c.endM, c.gainM, ramps(p, c.startM, c.endM), c)
            cursor = c.endM
        }
        if (p.lengthM > cursor) out += trendSections(p, cursor, p.lengthM)
        return out
    }

    /** Abschnitte gleichen Trends zwischen [from] und [to]; zu kurze werden dem Nachbarn zugeschlagen. */
    private fun trendSections(p: RouteProfile, from: Double, to: Double): List<Section> {
        if (to - from < MIN_SECTION_M) return listOf(section(p, from, to))
        // Laeufe aus 100-m-Stuecken gleichen Trends
        val runs = mutableListOf<Triple<Double, Double, Trend>>()
        var a = from
        while (a < to - 1) {
            val b = min(a + BASE_M, to)
            val t = trend(p.gradeAt((a + b) / 2))
            val last = runs.lastOrNull()
            if (last != null && last.third == t) runs[runs.size - 1] = Triple(last.first, b, t)
            else runs += Triple(a, b, t)
            a = b
        }
        // Kurze Laeufe schlucken: in beide Nachbarn, wenn die gleich sind, sonst in den laengeren.
        while (runs.size > 1) {
            val i = runs.indices.filter { runs[it].second - runs[it].first < MIN_SECTION_M }
                .minByOrNull { runs[it].second - runs[it].first } ?: break
            val prev = runs.getOrNull(i - 1)
            val next = runs.getOrNull(i + 1)
            when {
                prev != null && next != null && prev.third == next.third -> {
                    runs[i - 1] = Triple(prev.first, next.second, prev.third)
                    runs.removeAt(i + 1); runs.removeAt(i)
                }
                next == null || (prev != null && prev.second - prev.first >= next.second - next.first) -> {
                    runs[i - 1] = Triple(prev!!.first, runs[i].second, prev.third)
                    runs.removeAt(i)
                }
                else -> {
                    runs[i + 1] = Triple(runs[i].first, next.second, next.third)
                    runs.removeAt(i)
                }
            }
        }
        // Nach dem Zusammenlegen zaehlt der Durchschnitt - gleiche Nachbarn verschmelzen.
        val sections = mutableListOf<Section>()
        for (r in runs) {
            val s = section(p, r.first, r.second)
            val last = sections.lastOrNull()
            sections += if (last != null && last.trend == s.trend) {
                sections.removeAt(sections.size - 1)
                section(p, last.startM, s.endM)
            } else s
        }
        return sections
    }

    private fun section(p: RouteProfile, from: Double, to: Double): Section {
        val gain = p.elevationAt(to) - p.elevationAt(from)
        val grade = if (to - from < 1) 0.0 else gain / (to - from) * 100
        val t = if (abs(gain) < MIN_SECTION_GAIN_M) Trend.FLAT else trend(grade)
        return Section(t, from, to, gain, ramps(p, from, to))
    }

    private fun trend(grade: Double) = when {
        grade >= FLAT_GRADE -> Trend.UP
        grade <= -FLAT_GRADE -> Trend.DOWN
        else -> Trend.FLAT
    }

    /**
     * Rampen zwischen [from] und [to]: erst 100-m-Stuecke, dann immer die beiden Nachbarn
     * mit der aehnlichsten Steigung zusammenlegen - bis es hoechstens [max] sind und sich
     * alle deutlich unterscheiden.
     */
    fun ramps(p: RouteProfile, from: Double, to: Double, max: Int = MAX_RAMPS): List<Ramp> {
        val total = to - from
        if (total < 1) return emptyList()
        fun ramp(a: Double, b: Double) = Ramp(a, b, p.elevationAt(a), p.elevationAt(b))
        // Bei langen Stuecken groebere Grundstuecke - spart Rechenzeit, sieht gleich aus.
        val base = max(BASE_M, total / 80)
        val rs = mutableListOf<Ramp>()
        var a = from
        while (a < to - 1) {
            val b = if (to - a < base * 1.5) to else a + base
            rs += ramp(a, b)
            a = b
        }
        val minLen = max(MIN_RAMP_M, total / 20)
        fun merge(i: Int) { rs[i] = ramp(rs[i].startM, rs[i + 1].endM); rs.removeAt(i + 1) }
        while (rs.size > 1) {
            val short = rs.indices.filter { rs[it].lengthM < minLen }.minByOrNull { rs[it].lengthM }
            if (short != null) {
                val left = if (short > 0) abs(rs[short].grade - rs[short - 1].grade) else Double.MAX_VALUE
                val right = if (short < rs.size - 1) abs(rs[short].grade - rs[short + 1].grade) else Double.MAX_VALUE
                merge(if (left <= right) short - 1 else short)
                continue
            }
            val j = (0 until rs.size - 1).minBy { abs(rs[it].grade - rs[it + 1].grade) }
            if (rs.size <= max && abs(rs[j].grade - rs[j + 1].grade) >= MERGE_DIFF) break
            merge(j)
        }
        return rs
    }
}
