#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

mkdir -p deliverables/jar
find deliverables/jar -maxdepth 1 -type f -name "*.jar" -delete

echo "Building Linux x64 client fat JAR..."
./mvnw -Pclient-jar \
  -Djavafx.platform=linux \
  -Dclient.jar.name=mesos-client-linux-x64 \
  -DskipTests \
  clean package

echo "Building macOS x64 client fat JAR..."
./mvnw -Pclient-jar \
  -Djavafx.platform=mac \
  -Dclient.jar.name=mesos-client-mac-x64 \
  -DskipTests \
  clean package

echo "Building macOS ARM64 client fat JAR..."
./mvnw -Pclient-jar \
  -Djavafx.platform=mac-aarch64 \
  -Dclient.jar.name=mesos-client-mac-aarch64 \
  -DskipTests \
  clean package

echo "Building Windows x64 client fat JAR..."
./mvnw -Pclient-jar \
  -Djavafx.platform=win \
  -Dclient.jar.name=mesos-client-windows-x64 \
  -DskipTests \
  clean package

echo "Building server fat JAR..."
./mvnw -Pserver-jar \
  -Dserver.jar.name=mesos-server \
  -DskipTests \
  clean package

echo
echo "Created JAR files:"
ls -lh deliverables/jar
