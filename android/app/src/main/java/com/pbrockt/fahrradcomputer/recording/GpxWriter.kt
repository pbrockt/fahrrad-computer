package com.pbrockt.fahrradcomputer.recording

import java.io.Writer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Schreibt eine Fahrt als GPX 1.1. Der Puls steht in der Garmin-TrackPointExtension -
 * so lesen ihn Strava, Komoot und die Trainingsauswertung.
 */
object GpxWriter {

    fun write(out: Writer, name: String, points: List<TrackPoint>) {
        out.write(
            """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Fahrrad-Computer"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
  <metadata><name>${escape(name)}</name>${points.firstOrNull()?.let { "<time>${time(it.timeMs)}</time>" } ?: ""}</metadata>
  <trk>
    <name>${escape(name)}</name>
    <type>cycling</type>
    <trkseg>
"""
        )
        for (p in points) {
            out.write("""      <trkpt lat="${coord(p.lat)}" lon="${coord(p.lon)}">""")
            p.altitude?.let { out.write("<ele>${String.format(Locale.ROOT, "%.1f", it)}</ele>") }
            out.write("<time>${time(p.timeMs)}</time>")
            p.heartRate?.let {
                out.write("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>$it</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>")
            }
            out.write("</trkpt>\n")
        }
        out.write("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.7f", v)

    private fun time(ms: Long) = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
