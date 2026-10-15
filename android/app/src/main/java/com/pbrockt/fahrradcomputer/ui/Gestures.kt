package com.pbrockt.fahrradcomputer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Halten und Doppeltippen auf derselben Flaeche:
 *
 * - [holdMs] lang gedrueckt halten, ohne zu wischen -> [onHold] (Editor oeffnen). Laenger
 *   als die uebliche halbe Sekunde, damit es am Lenker nicht versehentlich passiert.
 * - Zweimal kurz tippen -> [onDoubleTap] (Klingel).
 *
 * Wischen (Seite wechseln) verbraucht der Pager; dann loest hier nichts aus.
 */
@Composable
fun Modifier.holdAndDoubleTap(holdMs: Long, onHold: () -> Unit, onDoubleTap: () -> Unit): Modifier {
    val hold by rememberUpdatedState(onHold)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    return pointerInput(holdMs) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var released = false
            val endedBeforeTimeout = withTimeoutOrNull(holdMs) {
                released = waitForUpOrCancellation() != null
                true
            }
            if (endedBeforeTimeout == null) {
                hold()
                return@awaitEachGesture
            }
            // null = abgebrochen, z. B. weil der Pager das Wischen uebernommen hat.
            if (!released) return@awaitEachGesture
            withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false)
            } ?: return@awaitEachGesture
            // Schon beim zweiten Antippen klingeln - nicht erst beim Loslassen.
            doubleTap()
            waitForUpOrCancellation()
        }
    }
}
