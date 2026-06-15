#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

rm -rf deliverables/javadoc
./mvnw -DskipTests javadoc:javadoc

echo "Javadocs generated in deliverables/javadoc/apidocs"
