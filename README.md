# Fahrrad-Computer

Android-App (Kotlin + Jetpack Compose) als Fahrrad-Computer für ein E-Bike mit Bosch SX:
modularer Bildschirm mit Geschwindigkeit, Trittfrequenz, Puls in Zonenfarben und Navigation,
Kalorienberechnung, Streckenaufzeichnung, Health Connect, Live-Standort und KI-Trainingsplan.

Die geplante Vorgehensweise steht in [KONZEPT.md](KONZEPT.md).

## Stand: 0.0.21-alpha.1

| Bereich | Stand |
|---|---|
| Bedienung (seit 0.0.18) | Menü links (☰): Computer, Verlauf, Routen, Heimtrainer, Sensoren, Einstellungen. Hervorhebungsfarbe wählbar (13 Farben, Standard Petrol). Start-Seite: großer Knopf „Losfahren“ und Routen-Knopf (in der Hervorhebungsfarbe, wenn eine Route geladen ist; ohne Route geht es zur Auswahl und danach zurück, mit Route fragt ein Menü: Route ändern, Karte & Höhenprofil, Route umkehren, Route entfernen). Oben in der schmalen Leiste: vor der Fahrt ein runder ▶-Knopf, während der Fahrt Pause und Stopp, in der Pause ein grüner ▶-Knopf (weiter) und Stopp – alles nur als Symbole; beim Blättern kurz ein Schild „Seitenname x/y“. Einstellungen als Liste mit Abschnitten (Persönlich, Geräte, Fahrt, Teilen, App) und Unterseiten: Profil, Zonen, Sensoren, Heimtrainer, Tacho & Bedienung, Navigation, Offline-Karten, Wetter, Live-Link, Live-SMS, Health Connect, Updates & Info. |
| Akku & Display (seit 0.0.21) | Nach „Beenden“ eine Zusammenfassung („Geschafft!“) mit Zahlen, Karte, Zonen und Verläufen, Teilen, Health Connect, Fertig. Akku-Warnung während der Fahrt: Handy bei 20 % und 10 %, Sensoren unter 15 % – einmal mit Hinweis, Ton und Vibration (abschaltbar). Display während der Fahrt: immer an, aus und bei Hinweisen kurz an (Standard), oder aus; die Aufzeichnung läuft immer weiter (CPU bleibt wach), der Tacho erscheint über dem Sperrbildschirm. |
| Komfort (seit 0.0.20) | Einrichtung beim ersten Start (Profil mit Max-Puls-Schätzung aus dem Alter, Sensoren koppeln, Standort, Farbe; überspringbar). Auto-Pause: nach 5 s unter 2,5 km/h pausiert die Aufzeichnung, ab zwei Punkten über 6 km/h geht sie weiter (Pause per Knopf bleibt; abschaltbar unter Tacho & Bedienung). Größere Knöpfe oben (48 dp). Lautstärketasten blättern während der Fahrt die Tacho-Seiten (leiser = vor, lauter = zurück; abschaltbar). Anstiege ankündigen: 500 m vor einem Anstieg der Route Hinweis „Anstieg in … m“ mit Länge, Ø Steigung und Hm in Steigungsfarbe, dazu Ton/Vibration (abschaltbar unter Navigation). |
| Kachel-Einstellungen | Im Kachel-Dialog je nach Kachel: Wind relativ/absolut und km/h oder Beaufort, Gegenwind km/h oder Beaufort, Zeitraum der Verläufe (1–60 min oder ganze Fahrt), Ausschnitt des Höhenprofils (ganz, 5 km, 2 km), Fahrtrichtung als Pfeil/Himmelsrichtung/Grad, Zonenfarbe bei Puls und Leistung (füllen, nur Zahl, aus), Zonenleiste bei Tempo und Trittfrequenz, Kartenausrichtung je Karte. Gespeichert im Layout (auch beim Export). |
| Kacheln | Im Editor nach Kategorien (Tempo, Distanz & Zeit, Puls, Leistung, Trittfrequenz, Höhe & Anstiege, Karte & Navigation, Wetter & Umgebung, Sonstiges). Neu: Anstieg aktuell/nächster (wie ClimbPro: Rest-Hm, Rest-km, Ø/max Steigung, Profil des Anstiegs), Abstieg, VAM, Gesamtzeit, Ankunft, Fahrtrichtung, Gegenwind, Sonnenuntergang (offline berechnet), Leistung pro kg, % FTP, Tempo-/Leistungsverlauf, Puls-/Leistungszonen-Diagramm, Handy-Akku. Neue Standard-Seiten: Fahrt, Navi, Berg, Leistung & Puls, Übersicht. Anstiege der Route auch in der Routenvorschau. |
| Bedienung (alt) | Kein Menü unten mehr: Fahrten & Training, Karten & Routen, Sensoren und Einstellungen im Menü links (☰). Fahrt-Bildschirm: ganz links die Start-Seite (großer Start-Knopf, während der Fahrt Pause/Stopp; Übersicht Woche/Monat, Training heute, Route, Sensoren, letzte Fahrt), rechts die Tacho-Seiten. Nach Start blättert die App zum Tacho. Oben eine schmale Leiste mit kleinem ▶/⏸. Kachel „Start / Pause / Stopp“. Doppeltippen = Fahrradklingel (abschaltbar). Editor erst nach einstellbarer Haltedauer (0,5–3 s, Standard 1 s). |
| Dashboard | Mehrere Seiten zum Wischen (Standard: Fahrt, Puls, Übersicht). Lange auf eine Kachel drücken öffnet den Editor: Kachel antippen = Inhalt wählen oder doppelte Breite; Zeilenmenü = Höhe, Kachel hinzufügen, verschieben, löschen; Seiten anlegen, umbenennen, löschen, auf Standard zurücksetzen. Gespeichert als JSON. Puls in Zonenfarbe (Z1–Z5), Ø/max Puls in Zonenfarbe, Streckenskizze als Platzhalter für die Navi. Display bleibt an. |
| Darstellung (seit 0.0.19 eigenes Design) | Schmale Schrift (Barlow Condensed, SIL OFL), abgerundete Tacho-Kacheln mit schmalem Abstand, Bezeichnung links oben und Wert in der Farbe der Messgröße (Tempo blau, Distanz grün, Trittfrequenz orange …), Puls und Leistung als in Zonenfarbe gefüllte Kacheln, Zonenleiste unter Tempo und Trittfrequenz, Seitenanzeige als Punkte, GPS-Punkt. Dunkel (Anthrazit), Hell (helles Grau mit weißen Kacheln) oder wie System. Neues Standard-Layout mit Seite „Berg“ – bestehende Layouts bleiben, „Auf Standard zurücksetzen“ im Editor holt das neue. |
| Zonen | Puls (Max-Puls), Leistung (FTP, eingegeben oder aus Gewicht/Alter/Geschlecht geschätzt; 55/75/90/105 %), Trittfrequenz und Tempo (Grenzen einstellbar, Standard 60/75/90/105 U/min und 10/18/25/32 km/h). Fahrt-Ansicht: Zeit in Zonen für alle vier. |
| Höhenprofil | Aus der Route: Höhen aus der GPX-Datei, sonst einmalig vom Höhenmodell (Open-Meteo, Copernicus 90 m). Profil nach Steigung eingefärbt mit Position, schon Gefahrenes grau, Überschrift „noch x km · y Hm“, Ausschnitt ganze Route, von hier bis zum Ziel, nächste 5 oder 2 km; Kacheln Steigung (auf der Route aus dem Profil, sonst Ausgleichsgerade über die letzten 200 m GPS-Höhe), gefahrene Höhenmeter, Rest-Höhenmeter. Profil auch in Routenvorschau und auf der Start-Seite. |
| Wetter | Kacheln Wind (Pfeil relativ zur Fahrtrichtung, Gegen-/Rücken-/Seitenwind, Böen) und Temperatur; Wetterkarte auf der Start-Seite. Open-Meteo, Standort auf ~1 km gerundet, höchstens alle 15 min bzw. 5 km; abschaltbar. |
| Diagramme | Fahrt-Ansicht: Verläufe mit zwei Fingern zoomen, mit einem Finger verschieben, doppelt tippen = alles; alle Diagramme zoomen gemeinsam. Linien in Zonenfarben vor blassen Zonenbändern. Kachel „Pulsverlauf“ (10 min) mit Zonenbändern. |
| Sensoren | Akkustand (Battery-Service) beim Verbinden und alle 10 min; unter 20 % rot in der Leiste oben. Bluetooth-Standardprofile: Puls (Brustgurt, Uhren mit „HF senden“), Trittfrequenz/Speed (CSC), Leistung (Cycling Power). Liefert der Leistungsmesser Kurbeldaten, kommt die Trittfrequenz daraus. Bosch Smart System erscheint, sofern es diese Profile sendet (noch nicht am Rad geprüft). Automatisches Wiederverbinden. |
| Heimtrainer | Menü „Heimtrainer“: virtuelle Watt für einen Heimtrainer ohne Leistungsmesser (wie HR2VP). Trittfrequenz × Widerstandsstufe (1–10, in der App mitstellen) gibt sofortige Änderungen, der Puls (Herzfrequenzreserve aus Ruhe-/Maxpuls, auf die FTP abgebildet) legt das Niveau fest; die App lernt daraus, wie schwer der Trainer ist, und merkt es sich. FTP eigene oder geschätzt (Gewicht, Alter, Geschlecht), Korrektur 50–150 %. Das Handy bietet sich per Bluetooth als Leistungsmesser (Cycling Power mit Kurbeldaten → Trittfrequenz) und Pulsgurt an: in Zwift, Rouvy, MyWhoosh auf PC/Tablet koppeln. Läuft im Vordergrunddienst auch bei gesperrtem Handy. Anzeige Watt, Puls, Trittfrequenz, kcal, Zeit, Ø/max Watt. Wird nicht als Fahrt gespeichert (das macht Zwift). |
| Kalorien | Aus Fahrerleistung (kcal ≈ kJ), sonst Puls (Keytel: Gewicht, Alter, Geschlecht), sonst MET-Schätzung nach Geschwindigkeit (E-Bike). Die Kachel zeigt die Quelle. |
| Höhe | GPS-Höhe wird geglättet (Median gegen Ausreißer, gleitender Mittelwert über 15 s); Höhenmeter zählen erst ab 8 m Anstieg, dann vom Tal bis zum Gipfel. Profile zeigen mindestens 60 m Spanne. Ab Android 14 Höhe über dem Meeresspiegel. Ältere Fahrten werden beim Öffnen nachgerechnet. |
| Aufzeichnung | GPS im Vordergrunddienst (läuft bei aus­geschaltetem Display weiter), Start/Pause/Weiter/Stopp, Standzeiten zählen nicht als Fahrzeit, Zwischenspeichern jede Minute. |
| Fahrten | Gespeichert als GPX (Strecke mit Puls, Trittfrequenz, Leistung) plus JSON mit den Kennzahlen. Liste mit Summen für Woche und Monat; Detailansicht mit Kennzahlen, Streckenskizze, Zeit in Pulszonen und Verläufen (Geschwindigkeit, Puls in Zonenfarben, Trittfrequenz, Leistung, Höhe). Teilen, löschen. Abgebrochene Aufzeichnungen werden beim nächsten Start gerettet. |
| Teilen | In der Fahrt-Detailansicht: Bild als Poster (OSM-Karte über das ganze Bild, Titel/Distanz oben und Höhenprofil/Kennzahlen unten in halbtransparenten Kästen; Strecke auf OpenStreetMap-Karte – Kacheln von tile.openstreetmap.org mit App-Kennung und 14 Tagen Cache, Quellenangabe im Bild; ohne Karte umschaltbar – plus Höhenprofil und Kennzahlen), Video (Punkt fährt die Strecke ab, Zahlen zählen mit, MP4 über den H.264-Encoder des Handys) oder GPX. Vorher die Frage „Start und Ende ausblenden?“ – Nein / 250 m / 500 m (vorausgewählt) / 1 km, für alle Formate. |
| FIT | Teilen → Format „FIT“ (mit derselben Start/Ende-Kürzung): Datensatz pro Punkt mit Position, Höhe, Distanz, Tempo, Puls, Trittfrequenz, Leistung, dazu Runde und Sitzung mit Summen und Kalorien; wahlweise als E-Bike-Fahrt (Standard) oder normale Radfahrt. Geschrieben mit Garmins FIT-SDK (`com.garmin:fit`). Fahrten → „FIT-Datei importieren“: übernimmt Aktivitäten von Garmin, Wahoo, Zwift … (nur Punkte mit GPS-Position); Kennzahlen rechnet die App selbst. |
| Tacho-Layout | Einstellungen → Tacho-Layout: alle Seiten als JSON-Datei exportieren (teilen/sichern) oder importieren (ersetzt die aktuellen Seiten). |
| Health Connect | Optional „Fahrten automatisch übertragen“ (Standard aus). Einstellungen zeigen das neueste Gewicht aus Health Connect mit Datum neben dem Gewicht der App. Fahrten als Training „Radfahren“ mit Strecke, Puls, Geschwindigkeit, Trittfrequenz, Leistung, Distanz, Höhenmetern und Kalorien – nur per Knopf (nach der Fahrt im Dialog oder in der Fahrt-Ansicht), nie automatisch. Gewicht wird von dort übernommen. Feste IDs: erneutes Senden überschreibt statt zu verdoppeln. |
| Einstellungen | Max. Herzfrequenz (Zonen), Gewicht, Geburtsjahr, Geschlecht, Update-Kanal Release/Alpha, optional GitHub-Token. |
| Live-Link | Eigener Server statt Fremddienst: `server/live` (PHP ≥ 8.1, SQLite, kein MySQL/phpMyAdmin) nimmt die Positionen an, Zuschauer sehen unter `…/live/?k=<view_key>` Karte, Strecke, geplante Route und Zahlen in abgerundeten Kacheln wie in der App (Aktualisierung alle 15 s). App: Einstellungen → Live-Link mit Adresse, Upload-Token, Intervall 15 s–2 min, erste 500 m ausblenden (Standard), Puls optional, „Verbindung testen“ und „Link teilen“; der Link steht auch in der „losgefahren“-SMS. Nicht gesendete Punkte gehen beim nächsten Mal mit. Anleitung: `server/live/README.md`. |
| Live-Standort | Per SMS an eingetragene Nummern, alle 1–60 Minuten (Standard 10), optional „losgefahren“/„angekommen“. Text mit Uhrzeit, km, Geschwindigkeit, Rest der Route, Akku und Google-Maps-Link; passt in eine SMS. Test-SMS in den Einstellungen. |
| Zielsuche | Routen → Feld „Ziel suchen“ (Ort, Adresse, Name) und Schnellsuche Café, Bäckerei, Eis, Biergarten, Restaurant, Trinkwasser, Toilette, Fahrradladen, E-Bike laden – über Photon (komoot, OpenStreetMap), nach Nähe sortiert, mit Entfernung. Antippen → Fahrprofil (Tour, Schnell, Ruhig & sicher, MTB, Kürzeste) und optional hin & zurück → Route vom Standort über BRouter (brouter.de), gespeichert, aktiviert und in der Vorschau mit Höhenprofil und Anstiegen. |
| Karte & Route | Tab „Karten“: GPX-Routen importieren (Komoot, Strava, BRouter …), Vorschau, aktivieren. Dashboard-Kachel „Karte“ mit Route, gefahrener Strecke und Position (folgt mit), Hinweis bei mehr als 50 m Abweichung; Kachel „Rest der Route“. Offline-Karten von mapsforge.org (alle Bundesländer und die Nachbarländer NL, BE, LU, DK, AT, CH) per Download, Fahrrad-Kartenstil; ohne Offline-Karte optional Online-Karte von openstreetmap.org. |
| Karte bedienen | Tacho-Karte frei verschiebbar; Knopf oben rechts: Norden oben ↔ Fahrtrichtung oben, nach dem Verschieben zurück zur Position. In der Fahrt-Ansicht Strecke einfärbbar nach Tempo (5 Stufen) oder Puls (Zonenfarben) mit Legende. |
| Abbiegehinweise | Aus der Form der Route erkannt (Douglas-Peucker, Winkel ab 35°, nahe Knicke zusammengefasst): leicht/normal/scharf links/rechts, wenden. Seite „Navi“ mit großem Pfeil und Entfernung, Hinweis auch auf der Karte und in der Benachrichtigung (Sperrbildschirm). Vibration 150 m vorher und kurz davor, am Ziel. Piepen (Standard an, Medienlautstärke) nur, wenn man reagieren soll: jetzt abbiegen (2 Töne), von der Route abgekommen (langer tiefer Ton), Ziel (3). Ohne Straßennamen. |
| Trainingsplan | Tab „Fahrten“ → Reiter „Training“: Ziel, Trainingstage, Hinweise; Wochenplan ab heute über die Claude-API (Claude Opus 5.5, strukturierte JSON-Ausgabe, automatisches Ausweichen bei Fehl-Ablehnungen). Gesendet werden nur Trainingswerte der letzten 6 Wochen, Alter, Gewicht, Ziel – Vorschau „Was wird gesendet?“. Eigener API-Key, im Android Keystore verschlüsselt; Anleitung und Key-Prüfung in der App. Einheiten werden mit Fahrten abgeglichen (erledigt/ausgelassen); Kachel „Trainingsziel heute“. |
| Updates | Kanal Release ignoriert Alphas, Kanal Alpha nimmt die höchste Version. Workflow `release.yml` baut bei Tag `x.y.z` bzw. `x.y.z-alpha.N`. |

Noch nicht enthalten: Bosch-Unterstützungsstufe/Akku (braucht den nRF-Connect-Scan), Straßennamen bei Abbiegehinweisen.

## Design prüfen ohne Handy

```bash
cd android
./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Dscreenshots=/tmp/tacho
# PNGs der Standard-Seiten (hell/dunkel) mit Beispielwerten
```

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
