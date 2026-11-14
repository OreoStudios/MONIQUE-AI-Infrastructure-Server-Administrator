#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if ! test -n "$(printenv OREO_CLI || true)"; then
  echo "Set OREO_CLI to an installed, licensed OreoObfuscator CLI launcher." >&2
  exit 2
fi
mvn -B -f backend/pom.xml clean test package
python3 scripts/protect_release.py --oreo-cli "$OREO_CLI"
echo "Unsigned candidate: backend/target/monique-backend-protected.jar"
echo "Java 21 + real Spring/DB/security smoke tests must pass before release."
