package com.pbrockt.fahrradcomputer.recording

import java.io.Reader
import java.time.Instant

/**
 * Liest die GPX-Dateien, die [GpxWriter] schreibt, zurueck in [TrackPoint]s.
 *
 * Bewusst kein XML-Parser: Unser Format hat genau einen <trkpt> pro Zeile, und so laeuft
 * der Leser identisch auf Android und in den JVM-Tests. Fremde GPX-Dateien (andere
 * Zeilenumbrueche) werden damit nicht unterstuetzt - die App liest nur ihre eigenen.
 */
object GpxReader {

    private val TRKPT = Regex("""<trkpt lat="([-0-9.]+)" lon="([-0-9.]+)">""")
    private val ELE = Regex("""<ele>([-0-9.]+)</ele>""")
    private val TIME = Regex("""<time>([^<]+)</time>""")
    private val HR = Regex("""<gpxtpx:hr>(\d+)</gpxtpx:hr>""")
    private val CAD = Regex("""<gpxtpx:cad>(\d+)</gpxtpx:cad>""")
    private val POWER = Regex("""<power>(\d+)</power>""")

    fun read(reader: Reader): List<TrackPoint> = reader.useLines { lines ->
        lines.mapNotNull { line ->
            val pt = TRKPT.find(line) ?: return@mapNotNull null
            val time = TIME.find(line)?.groupValues?.get(1)
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: return@mapNotNull null
            TrackPoint(
                timeMs = time,
                lat = pt.groupValues[1].toDouble(),
                lon = pt.groupValues[2].toDouble(),
                altitude = ELE.find(line)?.groupValues?.get(1)?.toDoubleOrNull(),
                heartRate = HR.find(line)?.groupValues?.get(1)?.toIntOrNull(),
                cadence = CAD.find(line)?.groupValues?.get(1)?.toIntOrNull(),
                power = POWER.find(line)?.groupValues?.get(1)?.toIntOrNull(),
            )
        }.toList()
    }
}
