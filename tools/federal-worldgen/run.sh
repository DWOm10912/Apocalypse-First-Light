#!/usr/bin/env bash
# Offline federal map tools (C1 prototype; docs/worldgen/federal_six_state_c1.md). Pure Java, no Minecraft, no Gradle.
# Everything goes under build/federal-worldgen/ of this checkout.
#
#   tools/federal-worldgen/run.sh preview [seed] [roads]   political / physical / climate PNGs + stats (+ road plan)
#   tools/federal-worldgen/run.sh checks [seed...]          determinism, coverage, coast, islands, corridors, query speed
#   tools/federal-worldgen/run.sh lock                      the version lock's decision table (LockPolicyChecks)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/federal-worldgen"
CLS="$OUT/classes"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
mkdir -p "$CLS"
cd "$ROOT"
javac --release 17 -encoding UTF-8 -nowarn -d "$CLS" \
  -sourcepath "src/main/java${SEP}tools/federal-worldgen/java" \
  $(find tools/federal-worldgen/java -name '*.java')
what="${1:-preview}"; shift || true
case "$what" in
  preview) seed="${1:--295378578869149513}"
           java -Xmx2g -cp "$CLS" com.antaurora.apofirstlight.worldgen.federal.FederalPreview "$seed" "$OUT/preview" "${2:-roads}" ;;
  checks)  java -Xmx2g -cp "$CLS" com.antaurora.apofirstlight.worldgen.federal.FederalChecks "$@" ;;
  lock)    java -cp "$CLS" com.antaurora.apofirstlight.worldgen.lock.LockPolicyChecks ;;
  *)       echo "usage: run.sh preview|checks|lock" >&2; exit 2 ;;
esac
