# Highway V2 — Sea Bridge V1

Status: implemented natural-generation consumer; compile and bounded headless checks completed below. New-world visual/driving acceptance remains with the user. No historical chunks are upgraded.

Current extension: [V1.1A H-pylon landmark](highway_v2_sea_bridge_v1_1a.md) is integrated into `SeaBridgeEngineering.plan/render`. Eligible bridges receive two 64-block H pylons and a 256-block open main span after all 132 footing columns pass. Ordinary piers intersecting the main span / tower foundations are suppressed only when enabled; otherwise the V1 behavior below is retained. [V1.1B](highway_v2_sea_bridge_v1_1b.md) adds endpoint-constrained Semi-Fan steel cables. No old-chunk retrofits are implemented; V1.1B visual acceptance remains pending.

## Scope and integration

`SeaBridgeGeometry` consumes the existing immutable `SatelliteHighwayRouting.Connection`: crossing ID, route, two bridgeheads, four-direction axis and actualBankSpan. It does not add a RouteGraph edge or change routing, nodes, mainland/island shapes, biome rules, bridgehead search or route cost.

`NaturalHighwayGenerationAdapter` includes sea crossings in its initial chunk query/fast reject. A bounded world cache stores `SeaBridgeEngineering`, using the same pre-decoration sampler and existing land-edge engineering. The bridge renders after road hygiene and local modules through the existing Highway feature. The existing `primary_highway_generation` biome tag already includes Overworld oceans; no resource change or separate feature is needed.

Axis only: (±1,0)/(0,±1). Deck nominal width is 23 blocks, reusing `HighwayProfile`, `HighwayCorridor` and `HighwayRenderer` Axis Viaduct placement: asphalt carriageways, existing lane divider/yellow and white edge markings, median, shoulder, reinforced-concrete structure and outer barriers. Existing Viaduct outside-edge cells at lateral ±12 are retained; 23 is the nominal road width, not total structural width. No second deck or marking renderer.

## Grade, shore seams and abutments

Both road endpoint Y values come from existing `CorridorEngineeringSegment`/`HighwayProfile` engineering, not a placed-world heightmap. The bridge uses ascending world-axis stations and rounded linear interpolation between these anchors. Slopes above 1/8 fail with `GRADE_INFEASIBLE`, without moving shore nodes or adjusting parent grades.

Orthogonal road construction ends are clipped to the global eight-block grid. Bridgeheads themselves need not be grid aligned. Geometry therefore reaches the actual road endpoint by extending up to seven blocks inland at each end; the original bridgeheads and `actualBankSpan` remain unchanged. Grade interpolation covers this complete deck/abutment length. Dashes and pier phase retain global station coordinates.

Each abutment occupies its shore seam plus four longitudinal deck stations, across lateral −12…12. Reinforced concrete is placed from a sampled solid foundation to roadY−4; the existing deck supplies roadY−3…roadY. Nothing from the abutment crosses the drivable surface. Each support column has its own bounded foundation result and failed columns are skipped.

Surface→Bridge, Viaduct→Bridge and axis Tunnel→Bridge are allowed. At the endpoint the existing finite core-road clearance opens the bridge side of the portal; bridge profile tunnel creation is disabled. No independent portal architecture or road relocation is introduced. Existing protected block entities/unbreakable obstructions remain protected and can require live inspection.

## Piers and foundations

Reuse the Axis Viaduct global 32-block station grid, reinforced concrete, 19×3 cap (roadY−5…−4), 5×3 shaft, 9×7 bottom foundation and 7×5 upper footing. Piers stay at least four stations beyond the end of each abutment footprint, with a minimum eight-block end margin; no duplicate shore pier.

`HighwayTerrainSampler.pierFoundation` samples immutable base columns, scanning downward at most 128 blocks, bounded by min build height. It skips fluids, air, leaves, replaceable and non-colliding material. No deeper search or new geology rule was added. The result is cached in the bridge profile; the axis pier writer consumes this planned result instead of reading a possibly unloaded/generated neighboring chunk. Other axis viaduct behavior is unchanged.

No foundation: do not place that pier/support column. `PARTIAL_FOUNDATION_FAILED` and the failure count are reported/logged; the deck and supported components can still render. This status is not full structural acceptance. `pierCount` counts found foundations; `plannedPierCount` is geometric only. There is no navigable-waterway span planning in V1.

## Ownership and claim

Whole-bridge geometry, grade, pier positions and foundations are deterministic. `FiniteRouteHighwayWriter` bounds every operation; `ChunkOwnedHighwayWriter` writes only the target chunk. The claim is a narrow 29-block strip (lateral ±14), from the two actual road endpoints, covering deck, two-block outer-edge safety margin, piers and abutments. It uses `sea_bridge_v1`, the existing Highway owner, HARD INFRASTRUCTURE priority and Overworld Y range [−64,320). Existing route/node/seam claims are unchanged. No large sea-area reservation.

## Export and diagnose

`HighwayNetworkExport` retains the cyan dashed planning overlay, now labelled `SEA_BRIDGE_V1 (plan)`. Text includes crossingId, bridgeAxis, actualBankSpan, bridgeType, generationStatus, grade endpoints, pierCount/plannedPierCount and both abutment statuses. Offline export deliberately uses `ENGINEERING_NOT_SAMPLED` and UNKNOWN grades/foundation-qualified pierCount; it is not evidence of blocks placed.

`/afl highway_network info` advertises SEA_BRIDGE_V1. `/afl highway_network diagnose [x z]` in a loaded bridge chunk uses isolated dry engineering and reports crossingId, generationStatus, deckCells, ownedCells, pierCount, plannedLocalPiers, foundationFailures, abutments, gradeStart/gradeEnd, wouldRender and FIRST DROP POINT. Grade start/end follow ascending axis, not necessarily mainland→island. NONE/wouldRender refers to owned deck cells; always also inspect PARTIAL_FOUNDATION_FAILED. The command neither loads missing chunks nor places blocks.

## Bounded verification

Only `compileJava` and `seaBridgeV1Test` via `scripts/highway-branch-tests.init.gradle`. The runner compiles the contract sources but executes only this test, not the other Highway suites.

- A: one real satellite geometry, synthetic grades 70/78 and seabed Y=40; nonzero deck, 19 planned/supported piers, two abutments and actual clipped road endpoint agreement. This is not live terrain foundation verification.
- B: one adjacent chunk pair at world-axis seam −7568; invokes the existing final placement writer with actual AFL palette suppliers into an in-memory world proxy and checks asphalt on both sides, ≤1 grade step and chunk ownership. No chunks/world are created.
- C: seed `-4332662446239654818`, crossing `sea_crossing/satellite_1`, mainland (−2592,−7011), satellite (−2592,−7638), axis (0,−1), unchanged actualBankSpan=627. Deck plus clipped-end abutment connection runs from Z=−7640 to −7008, length=632, with 19 pier stations.

No live endpoint grade/seabed or in-game driving/visual verification is claimed. Resources unchanged; processResources not run. No multi-seed matrix, full regression, GameTest, runClient, screenshots, benchmark, batch chunk generation, clean, commit or push.

H-pylon main spans are implemented by V1.1A. The verification above records the original V1 stage, not V1.1A acceptance; see the linked report for current checks. Sloping cables, Landmark Arch and suspension/hangers are not implemented. Fluid Safety (water/lava sealing) is not handled. Ramp polish, City and Vehicle remain out of scope.
