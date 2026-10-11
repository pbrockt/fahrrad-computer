package com.pbrockt.fahrradcomputer

import com.pbrockt.fahrradcomputer.recording.GpxWriter
import com.pbrockt.fahrradcomputer.recording.TrackPoint
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class GpxWriterTest {

    @Test
    fun writesPointsWithHeartRate() {
        val out = StringWriter()
        GpxWriter.write(
            out, "Test & Tour",
            listOf(
                TrackPoint(1_760_000_000_000, 53.1234567, 7.5, 12.34, 130),
                TrackPoint(1_760_000_001_000, 53.1234667, 7.5, null, null),
            ),
        )
        val gpx = out.toString()
        assertTrue(gpx.contains("<name>Test &amp; Tour</name>"))
        assertTrue(gpx.contains("""<trkpt lat="53.1234567" lon="7.5000000"><ele>12.3</ele><time>2025-10-09T08:53:20Z</time>"""))
        assertTrue(gpx.contains("<gpxtpx:hr>130</gpxtpx:hr>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
        assertTrue(gpx.indexOf("<gpxtpx:hr>") == gpx.lastIndexOf("<gpxtpx:hr>"))
    }
}
