package com.pbrockt.fahrradcomputer.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Zustand der Live-Uebertragung - fuer die Kachel und den RideService. */
enum class LiveState { OFF, CONNECTING, READY, LIVE, PAUSED, ENDED }

/**
 * Steuert die Live-Uebertragung unabhaengig von der Aufzeichnung: vorher verbinden, erst am
 * Treffpunkt starten, unterwegs pausieren (dann wird keine Position gesendet), beenden.
 * Der RideService liest [state] und [run] und sendet entsprechend.
 */
class LiveControl {

    /** Ein Durchgang auf dem Server. Neuer Start = neue [id], die Live-Seite faengt neu an. */
    data class Run(
        val id: String,
        /** true: ab Fahrtbeginn senden (mit "erste 500 m ausblenden"); false: erst ab jetzt. */
        val fromStart: Boolean,
    )

    private val _state = MutableStateFlow(LiveState.OFF)
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run.asStateFlow()

    /** Kurzer Text unter dem Zustand: Server, Fehler ... */
    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    /** Wann zuletzt erfolgreich gesendet wurde. */
    private val _lastSentMs = MutableStateFlow<Long?>(null)
    val lastSentMs: StateFlow<Long?> = _lastSentMs.asStateFlow()

    fun connecting() { _state.value = LiveState.CONNECTING; _info.value = null }

    fun connected(host: String) {
        if (_state.value == LiveState.CONNECTING) _state.value = LiveState.READY
        _info.value = "verbunden mit $host"
    }

    fun connectFailed(message: String) {
        if (_state.value == LiveState.CONNECTING) _state.value = LiveState.OFF
        _info.value = message
    }

    fun start(fromStart: Boolean) {
        _run.value = Run(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")), fromStart)
        _state.value = LiveState.LIVE
        _info.value = null
        _lastSentMs.value = null
    }

    fun pause() { if (_state.value == LiveState.LIVE) _state.value = LiveState.PAUSED }

    fun resume() { if (_state.value == LiveState.PAUSED) _state.value = LiveState.LIVE }

    fun end() {
        if (_state.value == LiveState.LIVE || _state.value == LiveState.PAUSED) _state.value = LiveState.ENDED
    }

    /** Nach der Fahrt: alles zuruecksetzen. */
    fun reset() {
        _state.value = LiveState.OFF
        _run.value = null
        _info.value = null
        _lastSentMs.value = null
    }

    fun sent(timeMs: Long) { _lastSentMs.value = timeMs; _info.value = null }

    fun sendFailed(message: String) { _info.value = message }

    /** Laeuft eine Uebertragung (auch pausiert)? */
    val active: Boolean get() = _state.value == LiveState.LIVE || _state.value == LiveState.PAUSED
}
