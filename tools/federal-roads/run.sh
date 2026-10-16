#!/usr/bin/env bash
# C3 national road network offline tools (docs/worldgen/federal_highways_c3.md). Pure Java, no Minecraft, no Gradle.
# Reads a cached federal C2 plan (the isolated dev client writes it on first world load); output under build/federal-roads/.
#
#   tools/federal-roads/run.sh routes [seed] [plan-cache]   C3.1 route recomputation: report, JSON, maps, profiles
#   tools/federal-roads/run.sh works  [seed] [plan-cache]   C3.2 terrain works + pavement mesh check of the built routes
#   tools/federal-roads/run.sh edges  seed states.bin [plan-cache]   C3.3a B: turf edge against the natural ground of an exported area
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/federal-roads"
CLS="$OUT/classes"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
mkdir -p "$CLS"
cd "$ROOT"
javac --release 17 -encoding UTF-8 -nowarn -d "$CLS" \
  -sourcepath "src/main/java${SEP}tools/federal-roads/java${SEP}tools/federal-worldgen/java" \
  $(find tools/federal-roads/java -name '*.java')
what="${1:-routes}"; shift || true
case "$what" in
  routes) seed="${1:--295378578869149513}"
          cache="${2:-build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_$(java -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.route.RoadsPreview unsigned "$seed" 2>/dev/null || echo 18151365494840402103).bin}"
          java -Xmx4g -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.route.RoadsPreview "$seed" "$cache" "$OUT/routes" ;;
  edges)  seed="${1:--295378578869149513}"; states="$2"
          cache="${3:-build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_$(java -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.route.RoadsPreview unsigned "$seed" 2>/dev/null || echo 18151365494840402103).bin}"
          java -Xmx4g -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.road.TurfEdgeCheck "$seed" "$cache" "$states" ;;
  works)  seed="${1:--295378578869149513}"
          cache="${2:-build/federal-c2-client/afl_cache/terrain_plan/afl_federal_plan_c2_v1_$(java -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.route.RoadsPreview unsigned "$seed" 2>/dev/null || echo 18151365494840402103).bin}"
          java -Xmx4g -cp "$CLS" com.antaurora.apofirstlight.worldgen.network.road.RoadWorksCheck "$seed" "$cache" "$OUT/works" ;;
  *)      echo "usage: run.sh routes|works|edges [seed] [plan-cache]" >&2; exit 2 ;;
esac
