#!/bin/bash
# Containertest der Keycloak-Erweiterung: offizielles Abbild quay.io/keycloak/keycloak:<Version> im Entwicklungsmodus (H2, keine
# Datenbank noetig) mit dem Jar aus bauen.sh, danach browser.spec.mjs (Playwright) in zwei Durchgaengen:
#   attrappe : Anmelde-Schritt + Registrierung gegen die Attrappe des Dienstes (stub-dienst.php, Port 18176)
#   echt     : gegen das echte Pruefprojekt 19 (Secret aus /mnt/user/appdata/geheim/captcha19.secret, per Datei in den Container)
#   flock /tmp/claude-0/ap-docker.lock integrationen/keycloak/test/lauf.sh [Keycloak-Version, Vorgabe 26.8.1] [alle|attrappe|echt] [behalten]
# Raeumt Container samt Volumes auf. Das gezogene Keycloak-Abbild bleibt liegen, wenn es schon da war; sonst wird es am Ende entfernt
# (BILD_BEHALTEN=1 verhindert das).
# Ist im docker.img zu wenig Platz (< 12 GB frei), wird nichts gezogen: dann laeuft dieselbe Keycloak-Ausgabe (Verteilung
# keycloak-<Version>.tar.gz von GitHub, dieselben Dateien wie im Abbild) mit einem Temurin-JDK 21 aus einem Ordner auf dem Cache
# (ARBEIT=/mnt/cache/appdata/hc-keycloak-work) in einem vorhandenen debian-Abbild. Erzwingen: VERTEILUNG=1.
set -e
T="$(cd "$(dirname "$0")" && pwd)"
V="${1:-26.8.1}"
WAS="${2:-alle}"
KC=w2-keycloak-app
PORT=18176
SECRET=/mnt/user/appdata/geheim/captcha19.secret
IMG=quay.io/keycloak/keycloak:$V
free -g | awk '/^Mem/ { if ($7 < 8) { print "zu wenig Speicher frei (" $7 " GB), abbrechen"; exit 1 } }'
FREI=$(df --output=avail -BG /var/lib/docker | tail -1 | tr -dc 0-9)
ARBEIT=/mnt/cache/appdata/hc-keycloak-work
if [ -z "$VERTEILUNG" ] && { [ -n "$(docker images -q $IMG)" ] || [ "$FREI" -ge 12 ]; }; then VERTEILUNG=; else VERTEILUNG=1; echo "Betrieb aus der Verteilung (docker.img: $FREI GB frei)"; fi
VORHER=$(docker images -q $IMG)
"$T/../bauen.sh" >/dev/null
JAR="$(ls "$T"/../dist/hostily-captcha-keycloak-*.jar | tail -1)"
aufraeumen() {
  docker rm -f -v $KC >/dev/null 2>&1 || true
  [ -n "$STUB" ] && { kill -- -$STUB 2>/dev/null || kill $STUB 2>/dev/null; } || true
  pkill -f "[S] 0.0.0.0:$PORT" 2>/dev/null || true
  [ -n "$VERTEILUNG" ] && rm -rf "$ARBEIT/lauf" || true
  if [ -z "$VERTEILUNG" ] && [ -z "$VORHER" ] && [ -z "$BILD_BEHALTEN" ] && [ "$3" != "behalten" ]; then docker rmi $IMG >/dev/null 2>&1 || true; fi
}
trap aufraeumen EXIT INT TERM
aufraeumen
# Attrappe des Dienstes auf dem Host; 3>&- : die Sperre nicht erben
setsid php -d auto_prepend_file= -S 0.0.0.0:$PORT "$T/stub-dienst.php" >/dev/null 2>&1 3>&- &
STUB=$!
if [ -z "$VERTEILUNG" ]; then
  docker run -d --name $KC -v "$JAR":/opt/keycloak/providers/hostily-captcha-keycloak.jar:ro \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
    $IMG start-dev --http-port=8080 >/dev/null
else
  rm -rf "$ARBEIT/lauf" && mkdir -p "$ARBEIT/lauf" "$ARBEIT/jdk"
  [ -x "$ARBEIT/jdk/bin/java" ] || tar xzf "$ARBEIT/jdk21.tgz" -C "$ARBEIT/jdk" --strip-components=1
  tar xzf "$ARBEIT/kc-$V.tgz" -C "$ARBEIT/lauf" && cp "$JAR" "$ARBEIT/lauf/keycloak-$V/providers/hostily-captcha-keycloak.jar"
  docker run -d --name $KC -v "$ARBEIT/lauf/keycloak-$V":/opt/keycloak -v "$ARBEIT/jdk":/jdk:ro -e JAVA_HOME=/jdk \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin \
    debian:bookworm-slim /opt/keycloak/bin/kc.sh start-dev --http-port=8080 >/dev/null
fi
for i in $(seq 1 90); do docker logs $KC 2>&1 | grep -q "Listening on" && break; sleep 2; done
docker logs $KC 2>&1 | grep -iE "hostily|Listening on|Keycloak [0-9.]+ on JVM" | head -5
run() {
  docker run --rm --network container:$KC -v "$T":/t:ro -v "$T":/out -v "$SECRET":/secret/captcha19.secret:ro -e MODUS=$1 -e STUB_URL=http://172.17.0.1:$PORT \
    mcr.microsoft.com/playwright:v1.55.0-noble \
    sh -c 'mkdir -p /w && cp /t/browser.spec.mjs /w/ && cd /w && npm init -y >/dev/null && npm i --ignore-scripts --no-audit --no-fund playwright@1.55.0 >/dev/null 2>&1 && node browser.spec.mjs'
}
RC=0
[ "$WAS" = alle ] || [ "$WAS" = attrappe ] || [ "$WAS" = echt ] || { echo "Aufruf: lauf.sh [Version] [alle|attrappe|echt]"; exit 2; }
if [ "$WAS" = alle ] || [ "$WAS" = attrappe ]; then run attrappe || RC=1; fi
if [ "$WAS" = alle ] || [ "$WAS" = echt ]; then run echt || RC=1; fi
echo "--- Meldungen der Erweiterung im Keycloak-Protokoll (ohne Secret) ---"
docker logs $KC 2>&1 | grep -i "hostily" | sed 's/^[0-9: ,.-]*//' | sort | uniq -c | sort -rn | head -12
exit $RC
