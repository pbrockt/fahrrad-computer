<?php
// Fahrrad-Computer Live-Link - Einstellungen.
// Diese Datei nach config.php kopieren und die beiden Schluessel ersetzen.
// Neue Schluessel erzeugen z. B. mit:  php -r 'echo bin2hex(random_bytes(16)), "\n";'
// (oder einfach index.php ohne config.php aufrufen - die Seite schlaegt welche vor).

return [
    // Damit sendet die App (in der App unter Einstellungen > Live-Link eintragen).
    // Geheim halten - wer ihn kennt, kann Positionen hochladen.
    'upload_token' => 'HIER-EINEN-LANGEN-ZUFALLSWERT-EINTRAGEN',

    // Damit schauen die Zuschauer zu: https://dein-server/live/?k=<view_key>
    // Anderer Wert als upload_token - der Link darf weitergegeben werden.
    'view_key' => 'HIER-EINEN-ANDEREN-ZUFALLSWERT-EINTRAGEN',

    // Ueberschrift der Live-Seite.
    'title' => 'Live-Fahrt',

    // Alte Fahrten nach so vielen Tagen loeschen (Datenschutz). 0 = nie.
    'keep_days' => 5,

    // Puls auf der Live-Seite zeigen (nur, wenn die App ihn mitsendet).
    'show_heart_rate' => false,

    // Bereiche, in denen keine Positionen gespeichert werden - z. B. rund um die Haustuer.
    // Jeweils [Breite, Laenge, Radius in Metern]. Beispiel: [[50.3512, 7.5981, 400]]
    'privacy_zones' => [],

    // Ordner fuer die Fahrten (je Fahrt eine CSV- und eine JSON-Datei). Muss fuer PHP
    // beschreibbar sein. Am besten ausserhalb des Web-Ordners; sonst schuetzt
    // data/.htaccess (Apache) - bei nginx siehe README.
    'data_dir' => __DIR__ . '/data',
];
