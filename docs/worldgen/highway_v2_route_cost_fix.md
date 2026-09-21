# Orthogonal Strategic Branch Route Cost Fix

## Scope and old-selection evidence

Only final complete-plan cost, parent selection, and corresponding info/TXT metadata change. The shoreline algorithm, radius512, step8/refine1, 64-pair cap/order, dry LAND/COAST predicates and water validation remain unchanged. Macro, Terrain, route templates, renderer, exactly two National Trunks, claims, and seed-only immutable graph architecture are unchanged.

A read-only probe of live compiled classes for seed `-4332662446239654818`, island1 enumerated 101768 geometrical full combinations from the existing retained bank pairs. It then checked land paths and reservation conflicts in sorted order. These were legal candidates, not just straight-line estimates:

| Old rank | Parent | Junction XZ | Turns | Mainland length | Span | Mainland displacement | Satellite displacement | Combined displacement |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | A | -2672,853 | 0 | 7861 | 642 | 80.545469 | 6.255194 | 86.800663 |
| 2 | B | -961,-6504 | 1 | 2215 | 642 | 80.545469 | 6.255194 | 86.800663 |
| 3 | B | -961,-6472 | 1 | 2247 | 642 | 80.545469 | 6.255194 | 86.800663 |
| 4 | B | -961,-6440 | 1 | 2279 | 642 | 80.545469 | 6.255194 | 86.800663 |
| 5 | B | -961,-6408 | 1 | 2311 | 642 | 80.545469 | 6.255194 | 86.800663 |

Old comparator tuple was `(combinedDisplacement, actualBankSpan, turns, mainlandLength, stable ties)`. Thus `(86.800663,642,0,7861)` beat `(86.800663,642,1,2215)`, even on the same banks. An additional bank-level displacement prune suppressed alternatives before parent/route evaluation.

The same probe ordered by length found these legal B-parent plans at junction(-961,-6504), all one-turn, length2138, mainland displacement0.489382 and extraDistance0:

| Span | Satellite displacement | Combined displacement | Length-first probe tuple (length,extra,turns,span,displacement) |
| ---: | ---: | ---: | --- |
| 627 | 87.065234 | 87.554616 | 2138,0,1,627,87.554616 |
| 635 | 86.339972 | 86.829354 | 2138,0,1,635,86.829354 |
| 643 | 86.352919 | 86.842301 | 2138,0,1,643,86.842301 |
| 651 | 87.103746 | 87.593127 | 2138,0,1,651,87.593127 |

The next length-ranked plan was length2145, span625, combined88.200900 (mainland8.548792 + satellite79.652108). These near-equal spans/displacements do not justify 5000+ additional mainland blocks. This was a bounded geometry probe, not a performance benchmark or terrain/engineering replay.

## Current full-route cost

Implementation: `SatelliteHighwayRouting.Connection` exposes quality metrics; `RouteCost` compares numeric tiers and `compareConnections` supplies stable final ties.

```text
TURN_COST_BLOCKS = 64 = OrthogonalHighwayPath.RESERVED_LENGTH
networkCost = mainlandRouteLength + 64 * mainlandTurnCount
ManhattanMinimum = abs(junction.x - mainlandBridgehead.x)
                 + abs(junction.z - mainlandBridgehead.z)
extraDistance = mainlandRouteLength - ManhattanMinimum

order = networkCost
      → extraDistance
      → mainlandTurnCount
      → actualBankSpan
      → combinedDisplacement
      → islandHighwayLength
      → stable full-plan ties
```

Length dominates network cost, with a bounded64-block equivalent penalty per TURN (one nominal reserved turn-zone length, not a claim about actual ramp construction cost). Since at most two turns exist, the total turn penalty is at most128: it cannot justify thousands of extra blocks, but a near-length tie can prefer fewer turns. Equal network cost then favors less detour and fewer turns. There is no additional3000/4000-style threshold, length hard rejection, or arbitrary long-branch penalty.

Use additive extraDistance instead of a ratio: it is directly measured in blocks and has no small-denominator problem. Both the old7861 straight and the short2215 L have extraDistance0, so detour alone cannot fix parent selection; high-priority network cost is essential. Island length is a late tie-break, after span/displacement, not a new island-road algorithm.

Stable ties: route ID, parent edge ID, integer parent station, mainland XZ, satellite XZ, canonical source record string, mainland point sequence, island point sequence. No map/hash iteration, random UUID or runtime randomness.

The bank displacement prune and old turn-first prune are removed. All retained pairs can compete across both trunks, legal junctions and existing0..2-turn templates. A candidate is skipped only when its full-route networkCost is strictly worse than an already feasible selected plan; equal-cost candidates still pass through all constraints and tie-breaks. This is global comparison within the existing bounded candidate pool, not a global optimum over every possible coastline/path.

## Hard constraints and determinism

Unchanged: actual bridge span300..800, mainland0..2 TURN, island≤1 TURN, turn spacing128, junction spacing192, straight approach≥64,65-wide dry-land envelope, correct landmass/source waterbody, OPEN_OCEAN exclusion,512 bank radius and existing reserved-zone conflicts. Cost never turns an invalid pair/junction/route into a legal one.

Island processing remains sorted by stable ID; prior selected junctions and route reservations are checked before accepting later islands. Reversing input islands/candidates must produce identical complete connections. This is deterministic greedy island allocation, not a new joint multi-island optimizer.

## Debug, tests and acceptance boundary

`highway_network info` and connected TXT records now include selectedParentTrunk, mainlandRouteLength, turnCount, extraDistance and networkCost, retaining island length, actualBankSpan and all displacement fields. Existing PNG layers are unchanged. BridgeApproachEngineeringStatus remains UNKNOWN.

`HighwayRouteCostTest` adds near/far turn-cost comparisons, span/displacement/detour tiers, stable ties, real five-seed quality summaries, original two-trunk equality, reversed-input determinism, reservation conflicts, hard-span/junction checks, and the control seed optimum. Existing Shoreline/Orthogonal/Satellite/Phase1/2A/geometry/viaduct/export suites are retained.

## Representative route quality

All rows are connected. A/B mean national_trunk_a/b. ExtraDistance is0 for every selected row. No seed-specific selection logic exists.

| Seed / island | Old parent/turns/length | New parent | Turns | Mainland length | Island length | Span | Combined displacement | Network cost |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 /1 | B/1/6702 | A | 2 | 5203 | 424 | 750 | 521.801542 | 5331 |
| 2 /1 | B/0/5833 | A | 1 | 4565 | 416 | 729 | 443.575171 | 4629 |
| 2 /2 | B/1/286 | B | 1 | 261 | 656 | 683 | 45.315772 | 325 |
| 42 /1 | B/0/7859 | A | 1 | 4152 | 688 | 626 | 160.239140 | 4216 |
| 42 /2 | B/1/3158 | B | 1 | 3090 | 656 | 651 | 182.821119 | 3154 |
| 42 /3 | B/1/4447 | B | 1 | 4411 | 288 | 685 | 419.359788 | 4475 |
| -645704099691625981 /1 | A/1/477 | A | 1 | 439 | 544 | 641 | 295.281761 | 503 |
| -645704099691625981 /2 | A/1/2027 | A | 1 | 2009 | 624 | 774 | 490.413411 | 2073 |
| -645704099691625981 /3 | B/1/3000 | B | 1 | 2976 | 736 | 627 | 42.448681 | 3040 |
| -4332662446239654818 /1 | A/0/7861 | B | 1 | 2138 | 728 | 627 | 87.554616 | 2202 |

Connectivity stays1/1,2/2,3/3,3/3,1/1 =10/10. The control route saves5723 blocks (~72.8%), with junction(-961,-6504), without hardcoding B. All ten mainland lengths are lower than the pre-fix baseline. Some legal branches remain4000–5203 blocks: this change does not promise a universal short-branch ceiling or search outside the frozen candidate pool. Measured results do not justify an arbitrary extra length threshold.

The Turns column counts mainland turns. Island -645... #2 now uses the already-supported one-turn island road (624 blocks); all other selected island roads are straight. Its total graph turn count is therefore4 across three islands, not an illegal mainland route with more than two turns. The island geometry/templates were not changed.

Full suite BUILD SUCCESSFUL: compileJava; RouteCost initially111 checks; Shoreline6432; Orthogonal126; Satellite165; Viaduct181656; Geometry32 cases/500227 checks plus integration; Phase2A30; Export67. Phase1 snapshot and original-trunk equality remain intact. Initial test compilation caught a long-to-int station assignment, corrected with Math.toIntExact before the successful full rerun. Existing deprecation/unchecked/terminal warnings remain. No resources changed; processResources was not run. The final targeted run additionally checks a genuinely shorter geometric route at an illegal trunk endpoint junction.

Final targeted compileJava + highwayRouteCostTest: BUILD SUCCESSFUL, **113 checks passed** (including the shorter-but-invalid endpoint junction case). Production source was unchanged after the full regression run. Stopped for user Route Quality export acceptance.

This does not fix Bridge Approach TUNNEL, Ramp, Sea Bridge or Fluid Safety. No City work, WorldGenLevel/terrain-aware routing, runClient, clean, GameTest, new world, bulk chunk generation, benchmark, screenshot, commit or push. `.obsidian/workspace.json` is not edited. Pure planning results still need user export and live-world acceptance.
