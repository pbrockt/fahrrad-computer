<?php
// Gemeinsame Teile fuer api.php und index.php. Kein MySQL, kein phpMyAdmin:
// alles liegt in einer SQLite-Datei (PHP-Erweiterung pdo_sqlite, fast ueberall dabei).

declare(strict_types=1);

const LIVE_VERSION = 1;

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
        'keep_days' => 2,
        'show_heart_rate' => false,
        'privacy_zones' => [],
        'database' => __DIR__ . '/data/live.sqlite',
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

function live_db(array $c): PDO
{
    if (!in_array('sqlite', PDO::getAvailableDrivers(), true)) {
        throw new RuntimeException('PHP-Erweiterung pdo_sqlite fehlt.');
    }
    $dir = dirname($c['database']);
    if (!is_dir($dir)) {
        mkdir($dir, 0770, true);
    }
    $db = new PDO('sqlite:' . $c['database'], null, null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
    $db->exec('PRAGMA journal_mode = WAL');
    $db->exec('PRAGMA busy_timeout = 5000');
    $db->exec('CREATE TABLE IF NOT EXISTS rides (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL DEFAULT \'\',
        state TEXT NOT NULL DEFAULT \'riding\',
        updated INTEGER NOT NULL,
        stats TEXT NOT NULL DEFAULT \'{}\',
        route TEXT
    )');
    $db->exec('CREATE TABLE IF NOT EXISTS points (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ride TEXT NOT NULL,
        t INTEGER NOT NULL,
        lat REAL NOT NULL,
        lon REAL NOT NULL,
        ele REAL,
        hr INTEGER
    )');
    // Gleicher Zeitpunkt zweimal (App sendet nach Netzproblem erneut) -> nur einmal speichern.
    $db->exec('CREATE UNIQUE INDEX IF NOT EXISTS points_ride_t ON points (ride, t)');
    return $db;
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

/** Fahrten loeschen, die laenger als keep_days nicht aktualisiert wurden. */
function live_cleanup(PDO $db, array $c): void
{
    $days = (int)$c['keep_days'];
    if ($days <= 0) {
        return;
    }
    $limit = (int)(microtime(true) * 1000) - $days * 86400 * 1000;
    $old = $db->prepare('SELECT id FROM rides WHERE updated < ?');
    $old->execute([$limit]);
    $ids = $old->fetchAll(PDO::FETCH_COLUMN);
    foreach ($ids as $id) {
        $db->prepare('DELETE FROM points WHERE ride = ?')->execute([$id]);
        $db->prepare('DELETE FROM rides WHERE id = ?')->execute([$id]);
    }
}
