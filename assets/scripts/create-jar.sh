#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

mkdir -p deliverables/jar
find deliverables/jar -maxdepth 1 -type f -name "*.jar" -delete

# -Djavafx.platform is passed explicitly so the correct natives are bundled even when
# cross-building (e.g. producing the Windows/Linux jars on a Mac). A CLI user property
# takes precedence over the host auto-detection profiles and is honoured by transitive
# OpenJFX/controlsfx native resolution, unlike a property set only inside a profile.
./mvnw -DskipTests clean package -Pclient-jar,client-linux-x64    -Djavafx.platform=linux
./mvnw -DskipTests clean package -Pclient-jar,client-mac-x64      -Djavafx.platform=mac
./mvnw -DskipTests clean package -Pclient-jar,client-mac-aarch64  -Djavafx.platform=mac-aarch64
./mvnw -DskipTests clean package -Pclient-jar,client-windows-x64  -Djavafx.platform=win
./mvnw -DskipTests clean package -Pserver-jar,server-release

ls -lh deliverables/jar
