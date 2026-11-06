#!/bin/sh
# Baut die Erweiterung: dist/hostily-captcha-keycloak-<Version>.jar (dist/ ist nicht eingecheckt) und kopiert sie nach
# ../../website/downloads/hostily-captcha-keycloak.zip (Zip mit dem Jar, README und Lizenz).
# Gebaut wird im maven-Container (kein Java auf dem Host noetig); die Java-Bibliothek ../bibliotheken/java wird dafuer
# zuerst in ein lokales Maven-Verzeichnis installiert und in das Jar eingeschweisst.
#   flock /tmp/claude-0/ap-docker.lock integrationen/keycloak/bauen.sh [Keycloak-API-Version fuer die Uebersetzung (Kompilierung), Vorgabe 25.0.6]
set -e
cd "$(dirname "$0")"
KC="${1:-25.0.6}"
M2="${M2_DIR:-/tmp/claude-0/hc-keycloak-m2}"
mkdir -p "$M2" dist
free -g | awk '/^Mem/ { if ($7 < 4) { print "zu wenig Speicher frei (" $7 " GB), abbrechen"; exit 1 } }'
rm -rf dist/target
docker run --rm -v "$PWD/..":/src:ro -v "$M2":/root/.m2 -v "$PWD/dist":/out maven:3.9-eclipse-temurin-17 sh -c '
  set -e
  mkdir -p /w && cp -r /src/bibliotheken/java /w/lib && cp -r /src/keycloak /w/ext
  (cd /w/lib && mvn -q -B -DskipTests install >/dev/null)
  cd /w/ext && rm -rf dist target && mvn -B -Dkeycloak.version='"$KC"' package 2>&1 | grep -E "Tests run:|BUILD|ERROR\\]" 
  cp target/hostily-captcha-keycloak-0.2.0.jar /out/
'
J=$(ls dist/hostily-captcha-keycloak-*.jar | tail -1)
echo "$J"
python3 - "$J" <<'PY'
import sys, os, zipfile
jar = sys.argv[1]
out = "dist/hostily-captcha-keycloak.zip"
if os.path.exists(out): os.remove(out)
def add(z, name, data):
    i = zipfile.ZipInfo(name, (2026, 10, 28, 0, 0, 0)); i.external_attr = 0o644 << 16; i.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(i, data)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    add(z, "hostily-captcha-keycloak/" + os.path.basename(jar), open(jar, "rb").read())
    add(z, "hostily-captcha-keycloak/README.md", open("README.md", "rb").read())
    add(z, "hostily-captcha-keycloak/LICENSE", open("LICENSE", "rb").read())
print(out)
PY
unzip -l dist/hostily-captcha-keycloak.zip 2>/dev/null || python3 -m zipfile -l dist/hostily-captcha-keycloak.zip
mkdir -p ../../website/downloads
cp dist/hostily-captcha-keycloak.zip ../../website/downloads/hostily-captcha-keycloak.zip
