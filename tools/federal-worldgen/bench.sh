#!/usr/bin/env bash
# C1 national-scale bench (docs/worldgen/federal_six_state_c1.md). Offline, pure Java, nothing under src/ changes:
# a COPY of the Terrain V2 planner goes to build/federal-worldgen/bench/src/ with three textual patches
#   N      -> Integer.getInteger("afl.planN", 1408)      (grid size per run)
#   ORIGIN -> -CELL * N / 2                                (r1: -11264, unchanged)
#   geo.sample( -> BenchMask.sample(geo, seed,             (-Dafl.planMask=federal: the federal map as the mask)
# and is compiled next to an UNPATCHED build, so the r1 parity of the copy is checked (same fingerprint).
#
#   tools/federal-worldgen/bench.sh all   [seed] [scales...]   size, parity, r1 run, federal runs (+ roads on the heights)
#   tools/federal-worldgen/bench.sh floor LABEL [seed]          smallest -Xmx that builds the plan (r1 | federal_<scale>)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/build/federal-worldgen/bench"
SRC_REL=build/federal-worldgen/bench/src   # relative: Windows javac does not read MSYS paths inside -sourcepath
SRC="$ROOT/$SRC_REL"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
V2=com/antaurora/apofirstlight/worldgen/terrain/v2
BENCH=tools/federal-worldgen/bench/java
MAIN=com.antaurora.apofirstlight.worldgen.terrain.v2.PlanScaleBench
cd "$ROOT"

compile() {
  rm -rf "$SRC" "$OUT/classes-patched" "$OUT/classes-r1"
  mkdir -p "$SRC/$V2"
  for f in TerrainPlanV2 TerrainPlanSurface RiverNetwork RiverCarver EcologyPlan EcologyBiomes PlanNoise TerrainPlanFingerprint; do
    cp "src/main/java/$V2/$f.java" "$SRC/$V2/"
  done
  local p="$SRC/$V2/TerrainPlanV2.java"
  sed -i -e 's|public static final int N = 1408;|public static final int N = Integer.getInteger("afl.planN", 1408);|' \
         -e 's|public static final double CELL = 16, ORIGIN = -11264;|public static final double CELL = 16, ORIGIN = -CELL * N / 2;|' \
         -e 's|geo\.sample(|BenchMask.sample(geo, seed, |' "$p"
  [ "$(grep -c 'Integer.getInteger("afl.planN"\|ORIGIN = -CELL \* N / 2\|BenchMask.sample(geo, seed, ' "$p")" = 3 ] || { echo "planner patch did not apply" >&2; exit 1; }
  javac --release 17 -encoding UTF-8 -nowarn -d "$OUT/classes-patched" -sourcepath "$SRC_REL${SEP}$BENCH${SEP}src/main/java" "$BENCH/$V2/PlanScaleBench.java"
  javac --release 17 -encoding UTF-8 -nowarn -d "$OUT/classes-r1" -sourcepath "src/main/java${SEP}$BENCH" "$BENCH/$V2/PlanScaleBench.java"
}

what="${1:-all}"; shift || true
case "$what" in
  all)
    SEED="${1:--295378578869149513}"; shift || true
    SCALES=("$@"); [ ${#SCALES[@]} -gt 0 ] || SCALES=(1.0 1.3 2.0)
    compile
    java -Xmx2g -cp "$OUT/classes-patched" $MAIN size "$SEED" "${SCALES[@]}" | tee "$OUT/size.txt"
    echo "== parity: unpatched r1 planner x2, patched copy x1"
    java -Xmx4g -cp "$OUT/classes-r1" $MAIN fingerprint "$SEED" 2 | tee "$OUT/parity.txt"
    java -Xmx4g -cp "$OUT/classes-patched" $MAIN fingerprint "$SEED" 1 | tee -a "$OUT/parity.txt"
    echo "== r1"
    java -Xmx6g -cp "$OUT/classes-patched" $MAIN run "$SEED" "$OUT" r1
    for s in "${SCALES[@]}"; do
      N=$(awk -v s="$s" '$1=="N" && $2==s {print $3}' "$OUT/size.txt")
      echo "== federal scale $s (N=$N)"
      java -Xmx10g -Dafl.planMask=federal -Dafl.planScale="$s" -Dafl.planN="$N" -cp "$OUT/classes-patched" $MAIN run "$SEED" "$OUT" "federal_$s" roads
    done ;;
  floor)
    LABEL="$1"; SEED="${2:--295378578869149513}"
    [ -d "$OUT/classes-patched" ] || compile
    ARGS=()
    if [ "$LABEL" != r1 ]; then
      s="${LABEL#federal_}"; N=$(awk -v s="$s" '$1=="N" && $2==s {print $3}' "$OUT/size.txt")
      ARGS=(-Dafl.planMask=federal -Dafl.planScale="$s" -Dafl.planN="$N")
    fi
    mkdir -p "$OUT/floor"
    for x in 256m 384m 512m 768m 1024m 1536m 2048m 3072m 4096m; do
      if java -Xmx$x "${ARGS[@]}" -cp "$OUT/classes-patched" $MAIN run "$SEED" "$OUT/floor" "${LABEL}_xmx$x" > "$OUT/floor/${LABEL}_xmx$x.log" 2>&1; then
        echo "$LABEL: builds with -Xmx$x ($(grep -o 'time: .*' "$OUT/floor/${LABEL}_xmx$x.log"))" | tee -a "$OUT/floor.txt"; break
      else
        echo "$LABEL: fails with -Xmx$x ($(grep -m1 -o 'OutOfMemoryError[^\r]*' "$OUT/floor/${LABEL}_xmx$x.log" || echo error))" | tee -a "$OUT/floor.txt"
      fi
    done ;;
  *) echo "usage: bench.sh all|floor" >&2; exit 2 ;;
esac
