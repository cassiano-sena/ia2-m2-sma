#!/usr/bin/env bash
# Run SMA Generator Rental GUI (Linux/macOS/Git Bash)
set -euo pipefail
cd "$(dirname "$0")"

if [[ -f ./mvnw ]]; then
  MVN="./mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
elif [[ -n "${MAVEN_HOME:-}" && -x "${MAVEN_HOME}/bin/mvn" ]]; then
  MVN="${MAVEN_HOME}/bin/mvn"
else
  echo "[ERRO] Maven nao encontrado. Use ./mvnw ou instale Maven no PATH." >&2
  exit 1
fi

if [[ -f ./run-gui.local.sh ]]; then
  # shellcheck source=/dev/null
  source ./run-gui.local.sh
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[ERRO] Java nao encontrado. Instale JDK 21+ ou defina JAVA_HOME." >&2
  exit 1
fi

echo "Compilando..."
"$MVN" -f pom.xml compile

echo
echo "Iniciando SMA Generator Rental GUI..."
exec "$MVN" -f pom.xml exec:java \
  -Dexec.mainClass=br.univali.cc.ia2.m2.sma.gui.TrafficControlApp \
  -Dexec.cleanupDaemonThreads=false
