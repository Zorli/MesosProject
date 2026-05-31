#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

mkdir -p deliverables/jar
find deliverables/jar -maxdepth 1 -type f -name "*.jar" -delete

./mvnw -DskipTests clean package -Pclient-jar,client-linux-x64
./mvnw -DskipTests clean package -Pclient-jar,client-mac-x64
./mvnw -DskipTests clean package -Pclient-jar,client-mac-aarch64
./mvnw -DskipTests clean package -Pclient-jar,client-windows-x64
./mvnw -DskipTests clean package -Pserver-jar,server-release

ls -lh deliverables/jar
