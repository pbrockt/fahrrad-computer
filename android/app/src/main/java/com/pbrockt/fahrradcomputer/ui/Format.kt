package com.pbrockt.fahrradcomputer.ui

import java.util.Locale

/** 1:05:09 bzw. 05:09 unter einer Stunde. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.ROOT, "%02d:%02d", m, sec)
}

/** Deutsche Schreibweise mit Komma. */
fun formatDecimal(value: Double, digits: Int = 1): String =
    String.format(Locale.GERMANY, "%.${digits}f", value)
