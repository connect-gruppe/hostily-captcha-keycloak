<?php
// Attrappe des Dienstes (Router fuer `php -S`): /api/v1/pruefen mit Verhalten je nach secret/response,
// /_count und /_last fuer die Pruefungen der Anfragen, /_reset.
$dir   = sys_get_temp_dir();   // Dateien je Port, damit sich parallele Tests nicht stoeren
$count = $dir . '/hc-stub-18203-count';
$last  = $dir . '/hc-stub-18203-last';
$path  = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

header('Content-Type: application/json');
if ($path === '/_count') {
    echo (int) @file_get_contents($count);
    return true;
}
if ($path === '/_last') {
    echo @file_get_contents($last);
    return true;
}
if ($path === '/_reset') {
    @unlink($count);
    @unlink($last);
    echo 'ok';
    return true;
}
if ($path !== '/api/v1/pruefen' || $_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(404);
    echo '{}';
    return true;
}
file_put_contents($count, (int) @file_get_contents($count) + 1);
$raw  = file_get_contents('php://input');
$body = json_decode($raw, true);
file_put_contents($last, json_encode(array(
    'body'  => $body,
    'ua'    => isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : '',
    'ctype' => isset($_SERVER['CONTENT_TYPE']) ? $_SERVER['CONTENT_TYPE'] : '',
)));
$secret = isset($body['secret']) ? $body['secret'] : '';
$token  = isset($body['response']) ? $body['response'] : '';
if ($secret === 'http500') {
    http_response_code(500);
    echo '{"error":"boom"}';
} elseif ($secret === 'garbage') {
    echo '<html>nope</html>';
} elseif ($secret === 'redirect') {
    http_response_code(302);
    header('Location: /elsewhere');
} elseif ($secret === 'bad') {
    echo json_encode(array('success' => false, 'error-codes' => array('invalid-input-secret')));
} elseif ($secret === 'badreq') {
    echo json_encode(array('success' => false, 'error-codes' => array('bad-request')));
} elseif ($token === '') {
    echo json_encode(array('success' => false, 'error-codes' => array('missing-input-response'), 'gruende' => array('token_fehlt')));
} elseif ($token === 'good' || strpos($token, 'good-') === 0) {
    echo json_encode(array('success' => true));
} elseif ($token === 'used') {
    echo json_encode(array('success' => false, 'gruende' => array('token_bereits_verwendet')));
} elseif ($token === 'expired') {
    echo json_encode(array('success' => false, 'gruende' => array('token_abgelaufen')));
} elseif ($token === 'spam') {
    echo json_encode(array('success' => false, 'gruende' => array('inhalt_spam')));
} else {
    echo json_encode(array('success' => false, 'gruende' => array('token_ungueltig')));
}
return true;
