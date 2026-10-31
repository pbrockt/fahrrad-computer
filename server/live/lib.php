<?php
// Gemeinsame Teile fuer api.php und index.php. Keine Datenbank: je Fahrt eine CSV-Datei
// mit den Punkten und eine kleine JSON-Datei mit Schluesseln, Name, Zustand und Zahlen - im Ordner data/.
// Laeuft so auf jedem Webspace mit PHP, ohne SQLite oder MySQL.

declare(strict_types=1);

const LIVE_VERSION = 3;

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

/** Admin-Passwort gesetzt und kein Platzhalter mehr? */
function live_configured(?array $c): bool
{
    if ($c === null) {
        return false;
    }
    $p = (string)($c['admin_password'] ?? '');
    return strlen($p) >= 8 && !str_starts_with($p, 'HIER-');
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

// --- Fahrten ---------------------------------------------------------------
// Jede Fahrt bekommt auf der Startseite eigene Schluessel: "<id>-<geheim>". Die id
// (8 Hex-Zeichen) nennt die Dateien data/s-<id>.json und data/s-<id>.csv.

function live_session_file(string $dir, string $id, string $ext): string
{
    return $dir . '/s-' . $id . '.' . $ext;
}

function live_session_create(string $dir): array
{
    $id = bin2hex(random_bytes(4));
    $now = (int)(microtime(true) * 1000);
    $meta = [
        'id' => $id,
        'upload' => $id . '-' . bin2hex(random_bytes(16)),
        'view' => $id . '-' . bin2hex(random_bytes(8)),
        'created' => $now,
        'updated' => 0,
        'rideId' => null,
        'name' => '',
        'state' => 'new',
        'stats' => [],
        'route' => null,
        'lastT' => 0,
        'count' => 0,
    ];
    live_write_meta(live_session_file($dir, $id, 'json'), $meta);
    return $meta;
}

function live_session_load(string $dir, string $id): ?array
{
    return preg_match('/^[0-9a-f]{8}$/', $id) ? live_read_meta(live_session_file($dir, $id, 'json')) : null;
}

/**
 * Fahrt zu einem Schluessel. [$field] ist 'upload' (die App) oder 'view' (Zuschauer).
 * Stehen in config.php noch feste Schluessel (upload_token/view_key), gelten die als
 * Dauer-Fahrt "standard" - so bleibt eine schon eingerichtete App gueltig.
 */
function live_session_by_key(string $dir, array $c, string $key, string $field): ?array
{
    $legacyUp = (string)($c['upload_token'] ?? '');
    $legacyView = (string)($c['view_key'] ?? '');
    $legacy = $field === 'upload' ? $legacyUp : $legacyView;
    if (strlen($legacyUp) >= 16 && strlen($legacyView) >= 16 && !str_starts_with($legacy, 'HIER-') && hash_equals($legacy, $key)) {
        $file = $dir . '/s-standard.json';
        $meta = live_read_meta($file);
        if ($meta === null) {
            $meta = ['id' => 'standard', 'created' => (int)(microtime(true) * 1000), 'updated' => 0, 'rideId' => null,
                'name' => '', 'state' => 'new', 'stats' => [], 'route' => null, 'lastT' => 0, 'count' => 0];
        }
        return ['upload' => $legacyUp, 'view' => $legacyView] + $meta;
    }
    if (!preg_match('/^([0-9a-f]{8})-[0-9a-f]{16,64}$/', $key, $m)) {
        return null;
    }
    $meta = live_session_load($dir, $m[1]);
    return $meta !== null && hash_equals((string)($meta[$field] ?? ''), $key) ? $meta : null;
}

/** Alle Fahrten, neueste zuerst. */
function live_sessions(string $dir): array
{
    $all = [];
    foreach (glob($dir . '/s-*.json') ?: [] as $f) {
        $m = live_read_meta($f);
        if ($m !== null && ($m['id'] ?? '') !== 'standard') {
            $all[] = $m;
        }
    }
    usort($all, fn($a, $b) => ($b['created'] ?? 0) <=> ($a['created'] ?? 0));
    return $all;
}

function live_session_delete(string $dir, string $id): void
{
    if (preg_match('/^[0-9a-f]{8}$/', $id)) {
        @unlink(live_session_file($dir, $id, 'json'));
        @unlink(live_session_file($dir, $id, 'csv'));
    }
}

/** Basis-Adresse dieses Ordners, z. B. https://domain.de/live */
function live_base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
    $host = $_SERVER['HTTP_X_FORWARDED_HOST'] ?? $_SERVER['HTTP_HOST'] ?? 'localhost';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return ($https ? 'https' : 'http') . '://' . $host . $dir;
}

/** Link fuer Zuschauer einer Fahrt. */
function live_view_url(array $session): string
{
    return live_base_url() . '/?k=' . rawurlencode((string)$session['view']);
}

/** Link, der die App direkt einrichtet (auch Inhalt des QR-Codes). */
function live_app_url(array $session): string
{
    return 'fahrradcomputer://live?url=' . rawurlencode(live_base_url()) . '&token=' . rawurlencode((string)$session['upload']);
}

// --- Anmeldung fuer die Startseite ---------------------------------------------

const LIVE_COOKIE = 'live_admin';

function live_admin_cookie_value(array $c): string
{
    return hash_hmac('sha256', 'fahrrad-live-admin', (string)$c['admin_password']);
}

function live_is_admin(array $c): bool
{
    return hash_equals(live_admin_cookie_value($c), (string)($_COOKIE[LIVE_COOKIE] ?? ''));
}

function live_set_admin_cookie(array $c, bool $on): void
{
    $path = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/') . '/';
    setcookie(LIVE_COOKIE, $on ? live_admin_cookie_value($c) : '', [
        'expires' => $on ? time() + 400 * 86400 : 1,
        'path' => $path,
        'secure' => str_starts_with(live_base_url(), 'https'),
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
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
