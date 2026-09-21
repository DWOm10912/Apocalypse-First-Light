# Highway V2 — Branch Junction / Turn Ramp V1

## Current status: implemented, static verification; live acceptance pending

The user explicitly authorized axial seam claims of at most 7 blocks between each existing clipped port and the unchanged 65x65 core, using the existing 65-block construction width. This resolves the original Stop Condition C below. **HighwayRouteGraph, OrthogonalHighwayPath, route costs, junctions, TURN nodes and bridgeheads are unchanged.** This is a local construction consumer, not routing.

### Ramp Grade Continuity Fix V1

- Ramp geometry no longer has to absorb the complete height difference between independently engineered axial edges. `HighwayBranchGrade` now measures blend distance from the owning Junction/TURN node rather than treating a world-axis station as a zero-based distance.
- At a Branch Junction, the first 64 blocks of the strategic branch inherit the parent Highway engineering plane. From 64 through 256 blocks, the existing smoothstep blend returns the axial branch to its own authoritative terrain-engineered grade. The Junction Ramp therefore enters and exits on the shared parent grade; the parent trunk is never adjusted by the branch.
- At a TURN, the two incident strategic edges sample their deterministic raw engineering grades at the unchanged turn node. Their rounded arithmetic mean is the shared `turnNodeGrade`. Both axial edges hold that grade for 64 blocks from the node and use the same existing 64-to-256-block blend to return to their own grade. The TURN Ramp consequently changes direction around one shared grade instead of climbing between two endpoint grades.
- `CorridorEngineeringSegment.ENGINEERING_VERSION` is 4 so cached pre-fix profiles cannot be reused after restart. Reserved Zone remains 65x65; RouteGraph, Junction/TURN coordinates, seam claims, Bridgeheads and Ramp centerline/detail geometry are unchanged.
- `compileJava` passed. No resources changed and `processResources` was not run. The requested live dry replays at `-961,-6504` and `-2592,-6504` remain pending user verification after loading the new classes; no runClient or world generation was performed by this task.

### Current implementation

- `HighwayReservedSeams`: deterministic half-open strips, excluding both the core and existing edge endpoint. Rejects negative or >7 lengths. `HighwaySpatialClaimProvider` publishes separate HARD INFRASTRUCTURE claims, existing owner/version/priority/Y[-64,320) unchanged; seam-only queries work. No full-square enlargement, no sea claim.
- `HighwayRampGeometry`: distinct `BRANCH_JUNCTION_RAMP` and `TURN_RAMP`. Connects actual clipped stations. A TURN uses incoming port -> 16 blocks before node -> 16 blocks after node on outgoing axis -> outgoing port. Junction uses parent station -32 -> -16 -> outgoing-axis 16 -> branch port. Parent mainline remains straight and intact. Stable IDs `ramp/junction/<nodeId>` / `ramp/turn/<nodeId>`.
- `HighwayGeometry.localRamp`: axial / 45-degree / axial, two short 16-block transitions and a 16*sqrt(2) diagonal leg. First/last legs are 16..24 blocks. This package-local factory limits extent to 40 per axis and raster expansion to 14.5 (23-wide pavement +3 ROW); it does not relax the existing long-route constructor's 32-block minimum/transition. All diagonal lies in the core; seam strips are axial. Existing Ribbon projection, median, marking models and station-based dash function are reused.
- `HighwayRampModules`: immutable 128-block spatial index, bounded 16-graph cache. Natural generation queries nearby modules even where there is no route edge. `NaturalHighwayCacheManager.WorldCache` has a bounded single-flight module engineering cache, no level references in values, no mutable route registry or chunk-dependent planning.
- `HighwayRampEngineering`: builds endpoint segments through the existing `segmentForChunk`/`CorridorEngineeringSegment` path. Actual profile roadY at each clipped station is authoritative, not terrain height or Macro planning Y. Parent profile is retained for the merge plane.
- `HighwayRampGrade`: smoothstep endpoint interpolation for TURN; junction preserves the whole parent plane through lateral distance14, then blends to exact outgoing deckY. Conservative local feasibility gate uses the existing debug profile's one block per8 station units (natural anchor interpolation itself has no universal hard slope cap), plus sampled eight-unit grade windows and all adjacent ROW cells <=1 Y difference. Infeasible input returns `GRADE_INFEASIBLE`, emits no partial module and does not change endpoint Y or reroute. **Actual two-seed endpoint Y/grade feasibility remains unverified without live terrain replay.** Synthetic rejection is not evidence that either real seed is infeasible.
- `HighwayProfile.ramp`: existing profile/corridor/renderer reuse, explicitly disables Ramp Tunnel. Pre-decoration terrain samples determine bounded local cut/fill; fill above7 or viaduct endpoints select local viaduct deck. Support stations every32 reuse `HighwayPierGeometry`, sampled solid foundations and existing 128-depth search. Existing pier-failure reporting remains; no new sea bridge or fluid sealing.
- `HighwayRampRenderer`: finite claim + local ROW + ChunkOwned restriction. Parent pavement, median and lane markings remain authoritative; branch competing furniture/paint is suppressed in merge and gore. Only branch-side parent parapet is removed where the ramp opens into it. Wedge-shaped gore is paved within the ribbon, excludes competing divider/median and gets white ribbon boundary markings. TURN median/paint and final ribbon edge reuse existing detail geometry. Local cut/clearance stays inside the authorized envelope, never a new long-distance diagonal corridor.
- `NaturalHighwayGenerationAdapter`: consumes modules after existing edge construction/hygiene; explicit warning on rejected grade. No second worldgen feature.
- Info/export report `JUNCTION_RAMP_V1` / `TURN_RAMP_V1`, module ID/type, node, directions, core/seam bounds and `gradeStatus=RUNTIME_CHECK_REQUIRED`. The frozen graph record's old planning status is not a rendering-status authority. Map symbols remain approximate reserved-zone symbols, not proof of placed roads.
- `/afl highway_network diagnose <x> <z>` now requires an already-loaded target chunk for module engineering, checks biome eligibility and uses an isolated in-memory cache. It reports moduleFound/cells/ownedCells, actual gradeStart/gradeEnd, status/wouldRender and FIRST DROP POINT. No render/write/forced chunk load. An unloaded chunk reports CHUNK_NOT_LOADED, not an invented grade. Info/export remain world-independent geometry reports.

### Verification boundary

Verification was stopped at the user's request; in-game acceptance is handed over to the user. An earlier full regression run completed successfully. The subsequent run passed compileJava, highwayRampTest (855937 checks), geometry contracts, branch contracts, viaduct and orthogonal routing checks, then was manually interrupted during highwayNetworkExportTest. Its cancellation is not a compilation failure. Final source changes to module query bounds/indexing and gore marking RISES, plus the added enclosed-hole test, were made after that compilation and have NOT been compiled or tested. No further validation will run in this task.

`highwayRampTest` runs with `scripts/highway-branch-tests.init.gradle` and depends on `compileJava`, not processResources. It covers the two exact seeds below (9 zones/14 seams/83 slices), seam-only claim queries, unchanged graph/trunks, complete23-wide ports, 4-connected pavement/median, exact chunk pavement union, all endpoint grid remainders and orientations, positive/negative/zero synthetic grade and infeasible rejection, parent plane preservation, gore suppression and spatial lookup. Runtime block placement, visual gore/guardrails, actual foundation support and real-world driveability still require user acceptance; no screenshot is claimed.

The original 1909 missing road columns are now covered by authorized seam claims (5395 full-envelope XZ columns =83*65). Claims alone are not placed asphalt. Sea Bridge, Fluid Safety, City and Terrain/Macro changes remain outside scope. No runClient, clean, GameTest, new world, bulk chunk generation, benchmark, screenshot or commit/push. Resources unchanged; processResources not run. `.obsidian/workspace.json` has pre-existing user changes and was not edited.

## Historical pre-authorization audit (superseded stop, evidence retained)

The initial attempt stopped at **Stop Condition C** before production changes. The following sections record that earlier state only; the current implementation and verification limits above supersede its NOT IMPLEMENTED / waiting-for-permission statements.

## Read-only evidence

The current Route Cost Fix graph was rebuilt with the existing compiled pure Java classes, without a world, chunks, terrain sampler or files generated by the probe. Compiled graph/routing classes were newer than their inspected sources. For both requested seeds the probe:

1. Enumerated each existing ReservedZone and its incident Strategic Branch edges.
2. Used edge.startStation/endStation (not just logical nodes) to find each actual constructed endpoint.
3. Constructed the union of **all** graph edge.bounds(CONSTRUCTION_HALF_WIDTH=32) and all reserved-zone bounds, matching the XZ authority in HighwaySpatialClaimProvider.
4. Checked every intervening centerline block and all23 road columns per longitudinal slice for membership in that union.

Result:9 reserved zones,14 incident branch ports, all with2..7 unclaimed longitudinal slices. There are83 missing centerline positions and1909 unclaimed standard-road XZ columns across these ports. These are planning/claim measurements, not observed live-world block holes.

### Seed -4332662446239654818 — connected1/1, mainland length2138, one TURN unchanged

| Module node XZ | Incident edge | Actual endpoint XZ | Distance from node | Unclaimed slices | Unclaimed road columns |
| --- | --- | --- | ---: | ---: | ---: |
| junction(-961,-6504) | satellite_1/mainland_0 | -1000,-6504 | 39 | 6 | 138 |
| turn(-2592,-6504) | satellite_1/mainland_0 | -2552,-6504 | 40 | 7 | 161 |
| turn(-2592,-6504) | satellite_1/mainland_1 | -2592,-6544 | 40 | 7 | 161 |

Exact examples:

- Junction zone covers X[-993,-929], Z[-6536,-6472]. Branch ends at X=-1000, so X=-999..-994 at Z=-6504 is outside both zone and every existing edge claim.
- TURN zone covers X[-2624,-2560], Z[-6536,-6472]. Incoming constructed endpoint is(-2552,-6504): X=-2559..-2553 is unclaimed. Outgoing endpoint is(-2592,-6544): Z=-6543..-6537 is unclaimed.

### Seed -645704099691625981 — connected3/3 unchanged

| Module node XZ | Incident edge | Actual endpoint XZ | Unclaimed slices |
| --- | --- | --- | ---: |
| junction(-6832,-916) | satellite_1/mainland_0 | -6832,-952 | 3 |
| junction(6224,-916) | satellite_2/mainland_0 | 6224,-952 | 3 |
| junction(-879,5584) | satellite_3/mainland_0 | -840,5584 | 6 |
| mainland turn(-6832,-1240) | satellite_1/mainland_0 | -6832,-1200 | 7 |
| mainland turn(-6832,-1240) | satellite_1/mainland_1 | -6872,-1240 | 7 |
| island turn(7227,-2808) | satellite_2/island_0 | 7192,-2808 | 2 |
| island turn(7227,-2808) | satellite_2/island_1 | 7227,-2848 | 7 |
| mainland turn(6224,-2808) | satellite_2/mainland_0 | 6224,-2768 | 7 |
| mainland turn(6224,-2808) | satellite_2/mainland_1 | 6264,-2808 | 7 |
| mainland turn(1672,5584) | satellite_3/mainland_0 | 1632,5584 | 7 |
| mainland turn(1672,5584) | satellite_3/mainland_1 | 1672,5624 | 7 |

Module and edge IDs in the tables abbreviate the common strategic_branch/ prefix. Junction IDs remain the existing parent/main/junction/station identifiers. The island#2 TURN is included, not omitted.

## Cause in live code

- `OrthogonalHighwayPath.zone`: node±32 inclusive,65×65.
- `OrthogonalHighwayPath.edgeBounds`: first removes33 blocks from an incident endpoint, then snaps the usable interval inward to the global8-block station grid. This places an actual endpoint33..40 blocks from its logical node.
- `HighwayRouteGraph.addConnectionEdges`: publishes those clipped station intervals unchanged.
- `HighwayRouteGraph.Edge.bounds(halfWidth)`: expands transversely only for AXIAL edges; no longitudinal overlap/padding back toward the reserved zone.
- `HighwaySpatialClaimProvider`: claims those exact finite edge bounds and zone bounds, with no hidden longitudinal allowance.
- `FiniteRouteHighwayWriter`: also enforces the finite edge envelope; ChunkOwned ownership does not authorize the intervening unclaimed columns.

Thus node±32 plus an endpoint at distance40 leaves distances33..39 without ownership. The parent trunk's claim does not cover the missing junction strips either; the probe tested the union of all claims, not just the branch.

## Boundary decision needed before implementation

Recommended narrow permission change, **not implemented**:

- Keep logical nodes, parent selection, branch edges, bridgeheads and65×65 core unchanged.
- Explicitly authorize and claim each finite axial seam strip between the core and its actual constructed endpoint; missing length is0..7 blocks in general,2..7 in these fixtures. Standard pavement alone needs23-wide coverage; a conservative construction envelope would retain the existing65-wide corridor allowance and must be checked for conflicts.
- This would enlarge the claim union and allow module-owned construction immediately outside the65 core, so it requires user approval under this task's strict boundaries. It is not silently treated as an existing claim.

Size interpretation: to cover through distance39 with one centered square would require79×79, adjacent to an existing endpoint at distance40; to include the endpoint itself requires81×81. These are **endpoint-coverage bounds only**, not proof that a full gore/median/guardrail module, support footprint, or grade transition fits. Do not blindly enlarge reserved zones: doing so while deriving edge clipping from the same larger reserve would move the endpoints again and repeat the seam problem.

An alternative would change endpoint clipping, which this task also freezes. It is not implemented or recommended without rechecking the8-grid engineering assumptions. No claim that65×65 can or cannot fit every possible local turning curve is made by this claim-only probe.

## Deferred implementation / verification

JUNCTION_RAMP and TURN_RAMP remain unimplemented. Local diagonal geometry, continuous23-wide footprint, gore, median, station-based markings, shoulder/guardrail suppression, endpoint grade sampling/interpolation, grade infeasibility, cut/fill, local viaduct support and chunk-union tests have **not** been accepted. The grade outcome is UNKNOWN; no runtime Y was inferred from Macro or planning data. Natural generation still consumes edges, not a new ramp module. Info/export/diagnose are not relabeled as implemented.

No Java/resource edits: compileJava and processResources were not run. The full regression suite was not rerun; the two-seed read-only claim/port probe above is the only new verification. No live behavior changed. Routing/shoreline/cost/geometry constants and all graph coordinates remain unchanged.

No runClient, clean, GameTest, new world, bulk chunk generation, benchmark, screenshot, Fluid Safety, Sea Bridge, City, Terrain/Macro changes, commit or push. `.obsidian/workspace.json` was not edited. Stop here for the user's boundary decision, **not** for new-world Ramp acceptance.
