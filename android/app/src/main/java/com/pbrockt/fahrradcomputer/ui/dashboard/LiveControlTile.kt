package com.pbrockt.fahrradcomputer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbrockt.fahrradcomputer.live.LiveState
import com.pbrockt.fahrradcomputer.recording.RidePhase
import com.pbrockt.fahrradcomputer.ui.AppColors
import com.pbrockt.fahrradcomputer.ui.Condensed

/**
 * Live-Uebertragung von Hand: vorher verbinden, am Treffpunkt starten, unterwegs pausieren
 * (dann geht keine Position raus), beenden. Oben der Zustand, unten die passenden Knoepfe.
 */
@Composable
internal fun LiveControlTile(live: LiveUi, phase: RidePhase, now: Long, modifier: Modifier) {
    val (status, dot) = when {
        !live.configured -> "nicht eingerichtet" to AppColors.Dim
        live.state == LiveState.OFF -> "aus" to AppColors.Dim
        live.state == LiveState.CONNECTING -> "verbinde …" to AppColors.Dim
        live.state == LiveState.READY -> "bereit" to AppColors.Go
        live.state == LiveState.LIVE && phase == RidePhase.IDLE -> "startet mit der Fahrt" to AppColors.Accent
        live.state == LiveState.LIVE -> "live" + (live.lastSentMs?.let { " · vor ${((now - it) / 1000).coerceAtLeast(0)} s" } ?: "") to Color(0xFFE53935)
        live.state == LiveState.PAUSED -> "pausiert – Position wird nicht gesendet" to Color(0xFFF08A00)
        else -> "beendet" to AppColors.Dim
    }
    BoxWithConstraints(modifier.background(AppColors.Card)) {
        val labelSize = (maxHeight.value * 0.12f).coerceIn(12f, 17f).sp
        Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Text(
                    "  Live · $status", color = AppColors.Text, fontSize = labelSize, fontFamily = Condensed,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            live.info?.let {
                Text(it, color = AppColors.Dim, fontSize = labelSize * 0.85f, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                @Composable
                fun Btn(text: String, color: Color, weight: Float = 1f, textColor: Color = Color.White, onClick: () -> Unit) {
                    Box(
                        Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(color).clickable(onClick = onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, color = textColor, fontSize = 19.sp, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                when {
                    !live.configured -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Einstellungen → Live-Link: QR-Code scannen", color = AppColors.Dim, fontSize = labelSize,
                            fontFamily = Condensed,
                        )
                    }
                    live.state == LiveState.OFF -> {
                        Btn("Verbinden", AppColors.Panel, textColor = AppColors.Text, onClick = live.onConnect)
                        Btn("Live starten", AppColors.Accent, 1.4f, onClick = live.onStart)
                    }
                    live.state == LiveState.CONNECTING -> Btn("Verbinde …", AppColors.Panel, textColor = AppColors.Dim) {}
                    live.state == LiveState.READY -> Btn("Live starten", AppColors.Accent, onClick = live.onStart)
                    live.state == LiveState.LIVE -> {
                        Btn("Pause", Color(0xFFF08A00), 1.4f, onClick = live.onPause)
                        Btn("Beenden", AppColors.Stop, onClick = live.onEnd)
                    }
                    live.state == LiveState.PAUSED -> {
                        Btn("Weiter", AppColors.Go, 1.4f, onClick = live.onResume)
                        Btn("Beenden", AppColors.Stop, onClick = live.onEnd)
                    }
                    else -> Btn("Neu starten", AppColors.Accent, onClick = live.onStart)
                }
            }
        }
    }
}
