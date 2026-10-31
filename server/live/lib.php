<?php
// Gemeinsame Teile fuer api.php und index.php. Keine Datenbank: je Fahrt eine CSV-Datei
// mit den Punkten und eine kleine JSON-Datei mit Name, Zustand und Zahlen - im Ordner data/.
// Laeuft so auf jedem Webspace mit PHP, ohne SQLite oder MySQL.

declare(strict_types=1);

const LIVE_VERSION = 2;

function live_config(): ?array
{
    $file = __DIR__ . '/config.php';
    if (!is_file($file)) {
        return null;
    }
    $c = require $file;
    if (!is_array($c)) {
        return null;
    }
    return $c + [
        'title' => 'Live-Fahrt',
        'keep_days' => 5,
        'show_heart_rate' => false,
        'privacy_zones' => [],
        'data_dir' => __DIR__ . '/data',
    ];
}

/** Sind die Schluessel gesetzt und keine Platzhalter mehr? */
function live_configured(?array $c): bool
{
    if ($c === null) {
        return false;
    }
    foreach (['upload_token', 'view_key'] as $k) {
        $v = (string)($c[$k] ?? '');
        if (strlen($v) < 16 || str_starts_with($v, 'HIER-')) {
            return false;
        }
    }
    return $c['upload_token'] !== $c['view_key'];
}

/** Datenordner, angelegt und beschreibbar - sonst eine verstaendliche Ausnahme. */
function live_dir(array $c): string
{
    $dir = rtrim((string)$c['data_dir'], '/');
    if (!is_dir($dir) && !@mkdir($dir, 0755, true)) {
        throw new RuntimeException('Ordner ' . basename($dir) . '/ lässt sich nicht anlegen.');
    }
    if (!is_writable($dir)) {
        throw new RuntimeException('Ordner ' . basename($dir) . '/ ist nicht beschreibbar (Rechte 755 oder 775 setzen).');
    }
    return $dir;
}

/** Dateiname einer Fahrt - nur harmlose Zeichen, kein "..". */
function live_ride_file(string $dir, string $ride, string $ext): string
{
    return $dir . '/' . preg_replace('/[^A-Za-z0-9_-]/', '_', $ride) . '.' . $ext;
}

function live_read_meta(string $file): ?array
{
    $raw = @file_get_contents($file);
    $m = $raw === false ? null : json_decode($raw, true);
    return is_array($m) ? $m : null;
}

/** Schreibt erst in eine Hilfsdatei und benennt sie dann um - Zuschauer lesen nie eine halbe Datei. */
function live_write_meta(string $file, array $meta): void
{
    $tmp = $file . '.tmp';
    file_put_contents($tmp, json_encode($meta, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), LOCK_EX);
    rename($tmp, $file);
}

/** Die zuletzt aktualisierte Fahrt: [Meta, CSV-Datei] oder null. */
function live_latest_ride(string $dir): ?array
{
    $best = null;
    foreach (glob($dir . '/*.json') ?: [] as $f) {
        $m = live_read_meta($f);
        if ($m !== null && ($best === null || ($m['updated'] ?? 0) > ($best[0]['updated'] ?? 0))) {
            $best = [$m, substr($f, 0, -5) . '.csv'];
        }
    }
    return $best;
}

/** Entfernung in Metern (Haversine). */
function live_distance(float $lat1, float $lon1, float $lat2, float $lon2): float
{
    $r = 6371000.0;
    $dLat = deg2rad($lat2 - $lat1);
    $dLon = deg2rad($lon2 - $lon1);
    $a = sin($dLat / 2) ** 2 + cos(deg2rad($lat1)) * cos(deg2rad($lat2)) * sin($dLon / 2) ** 2;
    return 2 * $r * asin(min(1.0, sqrt($a)));
}

function live_in_privacy_zone(array $c, float $lat, float $lon): bool
{
    foreach ($c['privacy_zones'] as $z) {
        if (is_array($z) && count($z) >= 3 && live_distance((float)$z[0], (float)$z[1], $lat, $lon) <= (float)$z[2]) {
            return true;
        }
    }
    return false;
}

function live_json(array $data, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** Adresse der Live-Seite fuer die Zuschauer, aus der aktuellen Anfrage gebaut. */
function live_view_url(array $c): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
    $host = $_SERVER['HTTP_X_FORWARDED_HOST'] ?? $_SERVER['HTTP_HOST'] ?? 'localhost';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return ($https ? 'https' : 'http') . '://' . $host . $dir . '/?k=' . rawurlencode($c['view_key']);
}

/** Fahrten loeschen, die laenger als keep_days nicht aktualisiert wurden (nach Dateidatum). */
function live_cleanup(string $dir, array $c): void
{
    $days = (int)$c['keep_days'];
    if ($days <= 0) {
        return;
    }
    $limit = time() - $days * 86400;
    // Kein GLOB_BRACE - das fehlt auf manchen Systemen.
    foreach (array_merge(glob($dir . '/*.json') ?: [], glob($dir . '/*.csv') ?: [], glob($dir . '/*.tmp') ?: []) as $f) {
        if (@filemtime($f) < $limit) {
            @unlink($f);
        }
    }
}

/** Was der Server kann - fuer die Einrichtungs- und Fehlerseite. */
function live_checks_html(?array $c = null): void
{
    $ok = fn(bool $b, string $yes, string $no) => $b ? "$yes ✓" : "<b>$no</b>";
    $dir = rtrim((string)($c['data_dir'] ?? __DIR__ . '/data'), '/');
    $rows = [
        'PHP' => $ok(version_compare(PHP_VERSION, '8.1.0', '>='), PHP_VERSION, PHP_VERSION . ' – mindestens 8.1 nötig'),
        'Ordner data/ beschreibbar' => $ok(is_dir($dir) && is_writable($dir), 'ja', 'nein – Rechte 755 oder 775 setzen'),
    ];
    echo '<h2>Server-Prüfung</h2><table>';
    foreach ($rows as $k => $v) {
        echo '<tr><td>' . htmlspecialchars($k) . ':&nbsp;</td><td>' . $v . '</td></tr>';
    }
    echo '</table>';
}
