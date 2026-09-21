# Orthogonal Shoreline Bridgehead Search V1

## Scope and authority

`SatelliteHighwayRouting` now treats the unchanged Macro `CrossingCandidate` as a local search anchor plus source waterbody and mainland/satellite identity authority, not fixed banks with a 96-block adjustment. Qualified source waterSpan remains 300..600. No Macro Geography, Terrain, candidate creation, biome, or coastline changes.

National Highway V1 remains N/E/S/W only. No diagonal fallback. The graph remains seed-only and immutable; no WorldGenLevel, generated chunks, terrain engineering sample, or new global mutable cache. Only selected routes enter the existing graph and claims; alternatives never become claims. National Trunk generation and graph publishing are unchanged.

## Bounded shoreline search

- `BRIDGEHEAD_SEARCH_RADIUS=512`: each retained bank must be within Euclidean distance 512 of its corresponding original endpoint. This covers the audit's roughly 160..427-block displacement witnesses with margin, without searching the whole coast. Representative seeds are rechecked below; 512 is a bounded V1 policy, not a universal completeness claim.
- For each of at most two forward cardinal directions, sample both local 1024-wide windows on an absolute world 8-block grid. Shared transverse coordinates prevent independently sampled shores missing each other's X/Z buckets.
- Detect dry-to-nondry transitions along the bridge axis at step 8; refine the boundary at step 1 (at most seven probes). Try inland insets 0..192 at step 8. Inset candidates must still satisfy the radius and the existing 64-long, 65-wide dry approach probes.
- Stable `TreeMap` transverse buckets: Z buckets for E/W, X buckets for N/S. Each keeps up to four approach-valid banks nearest the corresponding source endpoint, with coordinate tie breaks. Mainland scans face the crossing; island scans face the mainland. Point lists and returned maps are immutable.
- Pair only matching buckets, at most 4×4 combinations per bucket. Require forward span 300..800, then water-line validation. Sort by combined displacement, actual span, direction and coordinates; keep up to 64 ranked viable pairs per source. The island-road and mainland-route planners consume that list. This is not an exhaustive continuous-space search; sub-grid coast details and alternatives discarded by caps are outside this bounded policy.

Maximum per source: two directions ×129 transverse buckets ×4 = 1032 retained points per side (directional counts, possible duplicate coordinates); at most 4128 axial combinations before validation and 64 pairs passed to routing. Boundary scan has at most 129×129 grid locations per side/direction. Each transition has at most 25 inset trials and the fixed approach probes. No unrestricted cross-product of whole shore lists.

Let W=129, K=4, I=25, A=fixed approach-probe count, L≤801 bridge samples, P≤64 and J=existing finite junction/template count. Shore work is bounded O(W²·I·A); pairing/water work O(W·K²·L), plus sorting at most 4128 pairs. Route work remains O(P·J·existing path-validation cost). This describes algorithmic bounds, not a runtime benchmark. No cache was added.

## Unified dry and water semantics

`isUsableDryLandOrCoast(sample, expectedLandmassId)` requires:

- expected ID is nonnegative and exactly matches the sample;
- surfaceClass is LAND or COAST;
- waterClass is NONE, waterbodyId is -1, coastDistance is nonnegative.

All shore endpoints, both approaches, mainland paths/turn zones and islandRoad use this predicate through `landPath`. Wet COAST, ocean, other islands and water-bearing samples cannot become road support. Existing longitudinal 8 / lateral 16 construction-envelope probes, startup reserve exclusion, and turn-zone probes remain unchanged. These are Macro dry semantics, not a claim about actual blocks or local aquifers.

Bridge centreline checks every integer block, including endpoints. It must progress mainland → water → target island with no land/water re-entry. Water must be the source waterbody's STRAIT or its unnumbered (`waterbodyId=0`) COASTAL_WATER fringe, and at least one sample must actually touch the source STRAIT. OPEN_OCEAN, other numbered bay/strait/inland water, a third landmass, and wrong endpoint identity reject the pair. Unnumbered coastal fringe is necessary because Macro's 96-wide diagonal strait label does not cover an entire axial crossing; the source windows, source-strait requirement and monotonic land/water sequence keep it local.

## Ranking and unchanged geometry

`Connection` exposes mainlandDisplacement, satelliteDisplacement and combinedDisplacement, calculated from the authoritative source endpoints. **The original displacement-first final route ordering is superseded by [Route Cost Fix](highway_v2_route_cost_fix.md).** Current full-plan order is networkCost(mainland length +64 per TURN) → extraDistance → turns → span → displacement → island length → stable full-plan ties. The shoreline pool itself still uses its original displacement-first ordering and64 cap; the route selector no longer locks a bank before comparing both parents.

Actual bank span remains 300..800. Mainland remains 0..2 TURN; island remains straight or at most one TURN. Reserved length 64 nominal / inclusive zone 65×65, turn spacing 128, junction spacing 192 and straight approach 64 are unchanged. Existing route conflicts and reserved-zone claims are unchanged.

Ranked alternatives are an immutable internal list, not new graph metadata. Future engineering preflight can consume it, but this version does not choose by engineering mode.

## Diagnostics / export

Rejections: NO_SHORE_POINTS, NO_AXIAL_BRIDGE_PAIR, ACTUAL_SPAN_TOO_LONG, INVALID_WATER_CROSSING, NO_ISLAND_APPROACH, NO_MAINLAND_ROUTE, NO_VALID_JUNCTION, ROUTE_CONFLICT.

Failed-island `firstFailureReason` names the primary blocking stage across the bounded search, not literally the first rejected trial. No shore points or common axis are reported before span/water; when both reject, water takes precedence because some pairs reached that stage. After viable pairs, report island approach, junction, dry mainland route, or conflict in progression. Counts aggregate qualified sources. `viablePairs` counts water-valid pairs before the 64 cap; `islandApproaches`, `junctions`, `landRoutes` count actual route trials (not unique junction IDs). `rejected` is the tested-candidate histogram, not a census of continuous-space alternatives. Empty stages are represented by counts and the primary failure.

`highway_network info` and Export V2 TXT include selected source, banks, per-bank/combined displacement, actual span and axis; failures retain shorePointsMainland, shorePointsSatellite, axialPairs, other stage counts, primary failure and sourceCandidates. Export preserves the router's reason rather than replacing it with a generic failure. Visual export still draws only the selected final graph. Missing graph diagnostics remain UNKNOWN, not an invented rejection.

## Historical Shoreline Search V1 pure-planning results (before Route Cost Fix)

The following spans, selected coordinates, displacement maxima and turn counts are the pre-cost-fix baseline, not current route selections. Current per-island quality is recorded in [Route Cost Fix](highway_v2_route_cost_fix.md); connectivity remains10/10, and shoreline collection counts/semantics are unchanged.

| Seed | Before | Now | Actual spans, island ID order |
| --- | --- | --- | --- |
| 0 | 0/1 | 1/1 | 728 |
| 2 | 1/2 | 2/2 | 699, 675 |
| 42 | 2/3 | 3/3 | 621, 652, 649 |
| -645704099691625981 | 1/3 | 3/3 | 575, 724, 625 |
| -4332662446239654818 | 1/1 | 1/1 | 642 |

Total 5 connected /5 unconnected → 10 connected /0 unconnected. No seed-specific production logic. Control seed remains connected with a different bank pair. For -645... #2, search finds mainland/satellite point counts 766/629, 284 axial trials, 48 water-valid pairs (33 overlong and 203 wrong-water rejections); islandRoad and the existing ≤2-turn mainland planner both accept a final route. No Bridge Approach engineering conclusion follows from this result.

Selected witness banks (XZ) from the pure planning test:

| Seed / island | Mainland → satellite | Mainland / satellite displacement | Mainland turns |
| --- | --- | --- | --- |
| 0 / #1 | (7384,3620) → (7384,4348) | 396.17 / 109.92 | 1 |
| 2 / #1 | (5078,-5456) → (5777,-5456) | 321.66 / 112.57 | 0 |
| 42 / #3 | (5248,3773) → (5248,4422) | 304.03 / 104.15 | 1 |
| -645704099691625981 / #1 | (-6953,-1272) → (-7528,-1272) | 123.78 / 149.69 | 1 |
| -645704099691625981 / #2 | (6343,-2824) → (7067,-2824) | 357.37 / 111.46 | 1 |
| -4332662446239654818 / #1 | (-2672,-7008) → (-2672,-7650) | 80.55 / 6.26 | 0 |

All selected island roads in these five seeds are straight. Maximum selected per-bank displacement is 396.18 blocks rounded upward, inside the independently chosen 512 bound. Shoreline test also checks the audit's off-grid dry-COAST approach witnesses directly, rather than claiming every possible continuous bank is sampled.

## Historical verification and remaining work

Verified opt-in pure contract suite with `scripts/highway-branch-tests.init.gradle`: compileJava, highwayShorelineSearchTest, highwayOrthogonalRoutingTest, highwaySatelliteRoutingTest, highwayViaductTest, highwayGeometryContractTest, highwayBranchContractTest, highwayNetworkExportTest — BUILD SUCCESSFUL. Shoreline 6432 checks, Orthogonal 126, Satellite 171, Viaduct 181656, Geometry 32 cases /500227 checks plus integration, Phase2A 30, Export 64. Phase1 national trunk snapshot and equality checks remain intact. Existing deprecation/unchecked and terminal warnings remain; no new resource work, so processResources was not run.

The added shoreline suite covers dry LAND/COAST, wet coast/ocean/identity rejection, same-X/Z pairs, radius/bucket/pair caps, deterministic ordering, displacement ranking, actual span, water authority, both dry-COAST approaches, islandRoad startup, all representative island connectivity, all eight failure stages and an actual wrong-water candidate failure through TXT export. No world or output images are created by this suite.

Bridge Approach final engineering status remains UNKNOWN; the known bridge approach TUNNEL problem is not fixed. Ramp/physical TURN modules and Sea Bridge are not implemented here. Fluid Safety and City/Port are out of scope. Static graph connectivity does not prove natural-world placement, driveable continuity, or user-visible geometry. User export and in-world acceptance remain pending.

No runClient, clean, GameTest, new world, bulk chunk generation, benchmark, screenshot, commit or push. No editing of `.obsidian/workspace.json`.
