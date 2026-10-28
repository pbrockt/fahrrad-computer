# Live-Link – Server-Teil

Die App schickt während der Fahrt die Position an `api.php`. Wer den Link kennt, sieht
auf `index.php` Karte, Strecke und Zahlen. Die Seite aktualisiert sich alle 15 Sekunden.

**Ohne MySQL und ohne phpMyAdmin:** Alles liegt in einer SQLite-Datei (`data/live.sqlite`).
Die Datei legt das Skript beim ersten Aufruf selbst an.

## Voraussetzungen

- PHP **8.1 oder neuer** mit `pdo_sqlite`. Das ist bei fast jedem Webhoster und in den
  üblichen PHP-Containern dabei.
- HTTPS. Die App sendet einen geheimen Token mit, also bitte nicht über unverschlüsseltes `http://`.
- Der Server muss aus dem Internet erreichbar sein, denn das Handy sendet über mobile Daten.

## Einrichten

1. Den Ordner `live/` auf den Webserver kopieren, z. B. nach `https://dein-server.de/live/`.
2. `https://dein-server.de/live/` im Browser öffnen. Die Seite zeigt:
   - ob PHP und SQLite passen
   - zwei frische Zufallswerte
3. `config.sample.php` nach `config.php` kopieren und die beiden Werte eintragen:
   - `upload_token`: kommt in die App, ist geheim.
   - `view_key`: steht im Link für Zuschauer und darf weitergegeben werden.
4. Optional in `config.php`:
   - `privacy_zones`: Kreis um die Haustür, in dem nie etwas gespeichert wird.
   - `keep_days`: Nach so vielen Tagen werden Fahrten gelöscht (Standard 2).
   - `show_heart_rate`: Puls auf der Live-Seite zeigen.
5. In der App unter **Einstellungen → Live-Link** eintragen:
   - Adresse: `https://dein-server.de/live`
   - Upload-Token
6. In der App **„Verbindung testen“** antippen. Klappt es, merkt sich die App den Link für
   Zuschauer. **„Link teilen“** schickt ihn per WhatsApp, SMS usw.

### Ordner `data/` schützen

- **Apache:** `data/.htaccess` sperrt den Ordner schon.
- **nginx:** In die Server-Konfiguration eintragen:
  ```nginx
  location ~ ^/live/(data/|config\.php|lib\.php) { deny all; }
  ```
- **Am sichersten:** In `config.php` unter `database` einen Pfad außerhalb des Web-Ordners angeben.

### Auf dem Unraid-Server

Jeder Container mit Webserver und PHP geht, z. B. „nginx + PHP“ oder „Apache + PHP“ aus den
Community Apps. Erreichbar von außen wird er über den vorhandenen Reverse-Proxy
(Nginx Proxy Manager, SWAG, Cloudflare Tunnel) mit HTTPS.

## Ausprobieren ohne App

```bash
URL=https://dein-server.de/live
TOKEN=dein-upload-token

# Verbindung testen – Antwort enthält den Link für Zuschauer
curl -s -X POST "$URL/api.php" -H "Authorization: Bearer $TOKEN" -d '{"ping":true}'

# Eine Test-Position senden
curl -s -X POST "$URL/api.php" -H "Authorization: Bearer $TOKEN" \
  -d '{"ride":"test-1","name":"Test","state":"riding","stats":{"distanceM":1200,"movingMs":300000,"speedKmh":22.5,"avgKmh":20.1,"ascentM":15},
       "points":[{"t":'$(date +%s000)',"lat":50.3512,"lon":7.5981}]}'
```

Danach `$URL/?k=<view_key>` im Browser öffnen.

## Was gespeichert wird

- Je Fahrt: Name, Zustand (unterwegs/Pause/angekommen) und die Zahlen.
- Je Punkt: Zeit, Position, Höhe, Puls nur wenn in der App eingeschaltet.

Die App sendet die ersten 500 m nicht (abschaltbar), damit der Wohnort nicht zu sehen ist.
Alte Fahrten löscht der Server nach `keep_days`.

## Schnittstelle

| Aufruf | Zweck |
|---|---|
| `POST api.php` mit `Authorization: Bearer <upload_token>` (oder `X-Live-Token`) | `{"ping":true}` oder Fahrtdaten (siehe oben). Antwort `{"ok":true,"view":"…"}` |
| `GET api.php?k=<view_key>&since=<id>` | Neueste Fahrt mit allen Punkten nach `id`; die Route nur bei `since=0` |
