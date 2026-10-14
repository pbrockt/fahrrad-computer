# Fahrrad-Computer

Android-App (Kotlin + Jetpack Compose) als Fahrrad-Computer für ein E-Bike mit Bosch SX:
modularer Bildschirm mit Geschwindigkeit, Trittfrequenz, Puls in Zonenfarben und Navigation,
Kalorienberechnung, Streckenaufzeichnung, Health Connect, Live-Standort und KI-Trainingsplan.

Die geplante Vorgehensweise steht in [KONZEPT.md](KONZEPT.md).

## Stand: Phase 5 + 6 (0.0.5-alpha.1)

| Bereich | Stand |
|---|---|
| Dashboard | Mehrere Seiten zum Wischen (Standard: Fahrt, Puls, Übersicht). Lange auf eine Kachel drücken öffnet den Editor: Kachel antippen = Inhalt wählen oder doppelte Breite; Zeilenmenü = Höhe, Kachel hinzufügen, verschieben, löschen; Seiten anlegen, umbenennen, löschen, auf Standard zurücksetzen. Gespeichert als JSON. Puls in Zonenfarbe (Z1–Z5), Ø/max Puls in Zonenfarbe, Streckenskizze als Platzhalter für die Navi. Display bleibt an. |
| Darstellung | Dunkel, Hell (für pralle Sonne) oder wie System. |
| Sensoren | Bluetooth-Standardprofile: Puls (Brustgurt, Uhren mit „HF senden“), Trittfrequenz/Speed (CSC), Leistung (Cycling Power). Liefert der Leistungsmesser Kurbeldaten, kommt die Trittfrequenz daraus. Bosch Smart System erscheint, sofern es diese Profile sendet (noch nicht am Rad geprüft). Automatisches Wiederverbinden. |
| Kalorien | Aus Fahrerleistung (kcal ≈ kJ), sonst Puls (Keytel: Gewicht, Alter, Geschlecht), sonst MET-Schätzung nach Geschwindigkeit (E-Bike). Die Kachel zeigt die Quelle. |
| Aufzeichnung | GPS im Vordergrunddienst (läuft bei aus­geschaltetem Display weiter), Start/Pause/Weiter/Stopp, Standzeiten zählen nicht als Fahrzeit, Zwischenspeichern jede Minute. |
| Fahrten | Gespeichert als GPX (Strecke mit Puls, Trittfrequenz, Leistung) plus JSON mit den Kennzahlen. Liste mit Summen für Woche und Monat; Detailansicht mit Kennzahlen, Streckenskizze, Zeit in Pulszonen und Verläufen (Geschwindigkeit, Puls in Zonenfarben, Trittfrequenz, Leistung, Höhe). Teilen, löschen. Abgebrochene Aufzeichnungen werden beim nächsten Start gerettet. |
| Health Connect | Fahrten als Training „Radfahren“ mit Strecke, Puls, Geschwindigkeit, Trittfrequenz, Leistung, Distanz, Höhenmetern und Kalorien – automatisch nach dem Stopp oder per Knopf. Gewicht wird von dort übernommen. Feste IDs: erneutes Senden überschreibt statt zu verdoppeln. |
| Einstellungen | Max. Herzfrequenz (Zonen), Gewicht, Geburtsjahr, Geschlecht, Update-Kanal Release/Alpha, optional GitHub-Token. |
| Live-Standort | Per SMS an eingetragene Nummern, alle 1–60 Minuten (Standard 10), optional „losgefahren“/„angekommen“. Text mit Uhrzeit, km, Geschwindigkeit, Rest der Route, Akku und Google-Maps-Link; passt in eine SMS. Test-SMS in den Einstellungen. |
| Karte & Route | Tab „Karten“: GPX-Routen importieren (Komoot, Strava, BRouter …), Vorschau, aktivieren. Dashboard-Kachel „Karte“ mit Route, gefahrener Strecke und Position (folgt mit), Hinweis bei mehr als 50 m Abweichung; Kachel „Rest der Route“. Offline-Karten von mapsforge.org (u. a. Rheinland-Pfalz, 183 MB) per Download, Fahrrad-Kartenstil; ohne Offline-Karte optional Online-Karte von openstreetmap.org. Abbiegehinweise folgen später. |
| Updates | Kanal Release ignoriert Alphas, Kanal Alpha nimmt die höchste Version. Workflow `release.yml` baut bei Tag `x.y.z` bzw. `x.y.z-alpha.N`. |

Noch nicht enthalten: Bosch-Unterstützungsstufe/Akku (braucht den nRF-Connect-Scan), Abbiegehinweise, Live-Tracker, Karte, KI – siehe Phasen im Konzept.

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
