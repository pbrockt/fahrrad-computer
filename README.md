# Fahrrad-Computer

Android-App (Kotlin + Jetpack Compose) als Fahrrad-Computer für ein E-Bike mit Bosch SX:
modularer Bildschirm mit Geschwindigkeit, Trittfrequenz, Puls in Zonenfarben und Navigation,
Kalorienberechnung, Streckenaufzeichnung, Health Connect, Live-Standort und KI-Trainingsplan.

Die geplante Vorgehensweise steht in [KONZEPT.md](KONZEPT.md).

## Stand: 0.0.12-alpha.1

| Bereich | Stand |
|---|---|
| Bedienung | Kein Menü unten mehr: Fahrten & Training, Karten & Routen, Sensoren und Einstellungen im Menü links (☰). Fahrt-Bildschirm: ganz links die Start-Seite (großer Start-Knopf, während der Fahrt Pause/Stopp; Übersicht Woche/Monat, Training heute, Route, Sensoren, letzte Fahrt), rechts die Tacho-Seiten. Nach Start blättert die App zum Tacho. Oben eine schmale Leiste mit kleinem ▶/⏸. Kachel „Start / Pause / Stopp“. Doppeltippen = Fahrradklingel (abschaltbar). Editor erst nach einstellbarer Haltedauer (0,5–3 s, Standard 1 s). |
| Dashboard | Mehrere Seiten zum Wischen (Standard: Fahrt, Puls, Übersicht). Lange auf eine Kachel drücken öffnet den Editor: Kachel antippen = Inhalt wählen oder doppelte Breite; Zeilenmenü = Höhe, Kachel hinzufügen, verschieben, löschen; Seiten anlegen, umbenennen, löschen, auf Standard zurücksetzen. Gespeichert als JSON. Puls in Zonenfarbe (Z1–Z5), Ø/max Puls in Zonenfarbe, Streckenskizze als Platzhalter für die Navi. Display bleibt an. |
| Darstellung | Dunkel, Hell (für pralle Sonne) oder wie System. |
| Sensoren | Bluetooth-Standardprofile: Puls (Brustgurt, Uhren mit „HF senden“), Trittfrequenz/Speed (CSC), Leistung (Cycling Power). Liefert der Leistungsmesser Kurbeldaten, kommt die Trittfrequenz daraus. Bosch Smart System erscheint, sofern es diese Profile sendet (noch nicht am Rad geprüft). Automatisches Wiederverbinden. |
| Kalorien | Aus Fahrerleistung (kcal ≈ kJ), sonst Puls (Keytel: Gewicht, Alter, Geschlecht), sonst MET-Schätzung nach Geschwindigkeit (E-Bike). Die Kachel zeigt die Quelle. |
| Höhe | GPS-Höhe wird geglättet (Median gegen Ausreißer, gleitender Mittelwert über 15 s); Höhenmeter zählen erst ab 8 m Anstieg, dann vom Tal bis zum Gipfel. Profile zeigen mindestens 60 m Spanne. Ab Android 14 Höhe über dem Meeresspiegel. Ältere Fahrten werden beim Öffnen nachgerechnet. |
| Aufzeichnung | GPS im Vordergrunddienst (läuft bei aus­geschaltetem Display weiter), Start/Pause/Weiter/Stopp, Standzeiten zählen nicht als Fahrzeit, Zwischenspeichern jede Minute. |
| Fahrten | Gespeichert als GPX (Strecke mit Puls, Trittfrequenz, Leistung) plus JSON mit den Kennzahlen. Liste mit Summen für Woche und Monat; Detailansicht mit Kennzahlen, Streckenskizze, Zeit in Pulszonen und Verläufen (Geschwindigkeit, Puls in Zonenfarben, Trittfrequenz, Leistung, Höhe). Teilen, löschen. Abgebrochene Aufzeichnungen werden beim nächsten Start gerettet. |
| Teilen | In der Fahrt-Detailansicht: Bild als Poster (OSM-Karte über das ganze Bild, Titel/Distanz oben und Höhenprofil/Kennzahlen unten in halbtransparenten Kästen; Strecke auf OpenStreetMap-Karte – Kacheln von tile.openstreetmap.org mit App-Kennung und 14 Tagen Cache, Quellenangabe im Bild; ohne Karte umschaltbar – plus Höhenprofil und Kennzahlen), Video (Punkt fährt die Strecke ab, Zahlen zählen mit, MP4 über den H.264-Encoder des Handys) oder GPX. Vorher die Frage „Start und Ende ausblenden?“ – Nein / 250 m / 500 m (vorausgewählt) / 1 km, für alle Formate. |
| Health Connect | Fahrten als Training „Radfahren“ mit Strecke, Puls, Geschwindigkeit, Trittfrequenz, Leistung, Distanz, Höhenmetern und Kalorien – nur per Knopf (nach der Fahrt im Dialog oder in der Fahrt-Ansicht), nie automatisch. Gewicht wird von dort übernommen. Feste IDs: erneutes Senden überschreibt statt zu verdoppeln. |
| Einstellungen | Max. Herzfrequenz (Zonen), Gewicht, Geburtsjahr, Geschlecht, Update-Kanal Release/Alpha, optional GitHub-Token. |
| Live-Standort | Per SMS an eingetragene Nummern, alle 1–60 Minuten (Standard 10), optional „losgefahren“/„angekommen“. Text mit Uhrzeit, km, Geschwindigkeit, Rest der Route, Akku und Google-Maps-Link; passt in eine SMS. Test-SMS in den Einstellungen. |
| Karte & Route | Tab „Karten“: GPX-Routen importieren (Komoot, Strava, BRouter …), Vorschau, aktivieren. Dashboard-Kachel „Karte“ mit Route, gefahrener Strecke und Position (folgt mit), Hinweis bei mehr als 50 m Abweichung; Kachel „Rest der Route“. Offline-Karten von mapsforge.org (u. a. Rheinland-Pfalz, 183 MB) per Download, Fahrrad-Kartenstil; ohne Offline-Karte optional Online-Karte von openstreetmap.org. |
| Karte bedienen | Tacho-Karte frei verschiebbar; Knopf oben rechts: Norden oben ↔ Fahrtrichtung oben, nach dem Verschieben zurück zur Position. In der Fahrt-Ansicht Strecke einfärbbar nach Tempo (5 Stufen) oder Puls (Zonenfarben) mit Legende. |
| Abbiegehinweise | Aus der Form der Route erkannt (Douglas-Peucker, Winkel ab 35°, nahe Knicke zusammengefasst): leicht/normal/scharf links/rechts, wenden. Seite „Navi“ mit großem Pfeil und Entfernung, Hinweis auch auf der Karte und in der Benachrichtigung (Sperrbildschirm). Vibration 150 m vorher und kurz davor, am Ziel; Ton optional. Ohne Straßennamen. |
| Trainingsplan | Tab „Fahrten“ → Reiter „Training“: Ziel, Trainingstage, Hinweise; Wochenplan ab heute über die Claude-API (Claude Opus 5.5, strukturierte JSON-Ausgabe, automatisches Ausweichen bei Fehl-Ablehnungen). Gesendet werden nur Trainingswerte der letzten 6 Wochen, Alter, Gewicht, Ziel – Vorschau „Was wird gesendet?“. Eigener API-Key, im Android Keystore verschlüsselt; Anleitung und Key-Prüfung in der App. Einheiten werden mit Fahrten abgeglichen (erledigt/ausgelassen); Kachel „Trainingsziel heute“. |
| Updates | Kanal Release ignoriert Alphas, Kanal Alpha nimmt die höchste Version. Workflow `release.yml` baut bei Tag `x.y.z` bzw. `x.y.z-alpha.N`. |

Noch nicht enthalten: Bosch-Unterstützungsstufe/Akku (braucht den nRF-Connect-Scan), Straßennamen bei Abbiegehinweisen, Live-Tracker, Karte, KI – siehe Phasen im Konzept.

## Bauen

```bash
cd android
./gradlew testDebugUnitTest assembleRelease
# APK: android/app/build/outputs/apk/release/app-release.apk
```

## Signatur

Der Signaturschlüssel liegt **nicht** im Repo. GitHub Actions holt ihn aus den Secrets
`SIGNING_KEYSTORE_BASE64` (Schlüsseldatei, base64) und `SIGNING_PASSWORD`.
Lokal: Datei `android/app/release.keystore` (ignoriert) und `signing.password=…` in
`android/local.properties`. Ohne beides wird mit dem Debug-Schlüssel signiert.

## Veröffentlichen

1. `versionName`/`versionCode` in `android/app/build.gradle.kts` erhöhen
2. Commit, Tag = versionName (z. B. `0.1.0-alpha.2` oder `0.1.0`), Push mit Tag
3. GitHub Actions baut das APK und legt das (Pre-)Release an
