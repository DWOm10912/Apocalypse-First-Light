#!/usr/bin/env bash
# Terrain V2 research: compile the offline planner, export a plan, draw the national maps, measure the AFL windows
# with the same metrics as the USGS tiles and print the comparison table.
#   bash tools/terrain-v2-research/run_plan.sh SEED OUT_NAME
# Outputs under build/terrain-v2-research/ (not committed): <OUT_NAME>/ (rasters), png/<OUT_NAME>/, stats/
set -euo pipefail
cd "$(dirname "$0")/../.."
SEED=${1:--295378578869149513}
NAME=${2:-afl}
B=build/terrain-v2-research
T=tools/terrain-v2-research
javac -encoding UTF-8 --release 17 -nowarn -d $B/classes -sourcepath "src/main/java;$T/java" \
  $T/java/com/antaurora/apofirstlight/worldgen/terrain/v2/PlanV2Export.java
java -Xmx3g -cp $B/classes com.antaurora.apofirstlight.worldgen.terrain.v2.PlanV2Export "$SEED" $B/$NAME > $B/$NAME.log
python -I $T/national.py $B/$NAME $B/png/$NAME
for w in afl_plain afl_belt afl_coastal afl_foothill; do
  python -I $T/analyze.py afl $B/$NAME/$w.json $B/stats_$NAME > /dev/null
  cp $B/stats_$NAME/$w.json $B/stats/${NAME}_$w.json
  cp $B/stats_$NAME/$w.npz $B/stats/${NAME}_$w.npz
done
python -I $T/summary.py $B/stats mw1_darby_plains_oh_c600 mw3_bloomington_moraine_il_c600 ${NAME}_afl_plain \
  ap1_susquehanna_gaps_pa_x0.55 ${NAME}_afl_belt ap2_blue_mountain_front_pa_x0.55 ${NAME}_afl_foothill \
  cp1_york_pamunkey_va ${NAME}_afl_coastal > $B/cmp_$NAME.md
python -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); d.pop('windows'); print(json.dumps(d))" $B/$NAME/plan.json
