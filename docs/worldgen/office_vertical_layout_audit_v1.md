# Office vertical-layout audit V1 — 2026-09-12

Current status: **SUPERSEDED by the new nine-floor concrete building**, described in [office_concrete_01_authoring.md](office_concrete_01_authoring.md). The preserved86-block draft did receive its11-floor interior shell reset at Y=-33,-25,-18,-11,-4,3,10,17,24,31,38 with office top45; old slabs/soffits/stairs/furniture were removed inside its protected envelope, shafts and stair openings reserved. Saved readback:18,490 non-air, unchanged86-block height. It was not furnished or fully walk-tested. Its reservation has been released without clearing world blocks; the formal sixteen-floor NBT is unchanged. The plans below are historical and are not the current new-building design.

## Historical height-preserving internal plan — 8+7 / 11 floors

Scope: **planning only**. On2026-09-12, live bridge status confirmed the same overworld authoring copy/epoch and exact original bounds. Eight selected live horizontal slices rechecked base/podium/wing/main-roof levels. No world edits, camera movement, furnishing, build, NBT export or source-resource change were performed in this planning pass.

### Vertical schedule

Use floor-block Y consistently; walking surface is Y+1. Retain base Y=-33, original main exterior roof slab Y48, highest occupied cell Y52 / outer top Y53. Calculation: **8 + 10×7 + 8 =86**. The8-cell roof/service allowance is Y45..52 inclusive, including the office top slab, not8 clear-air blocks or an extra office storey.

| Level | Floor block Y | Walking surface Y | Next structural slab Y | Floor-to-floor | Clear height target |
| --- | --- | --- | --- | --- | --- |
| 1F main lobby | -33 | -32 | -25 | 8 | 7 |
| 2F | -25 | -24 | -18 | 7 | 6 |
| 3F | -18 | -17 | -11 | 7 | 6 |
| 4F | -11 | -10 | -4 | 7 | 6 |
| 5F | -4 | -3 | 3 | 7 | 6 |
| 6F | 3 | 4 | 10 | 7 | 6 |
| 7F | 10 | 11 | 17 | 7 | 6 |
| 8F | 17 | 18 | 24 | 7 | 6 |
| 9F | 24 | 25 | 31 | 7 | 6 |
| 10F | 31 | 32 | 38 | 7 | 6 |
| 11F | 38 | 39 | 45 | 7 | 6 |
| Office top / service-zone base | 45 | 46 | Existing roof48 | 3 | 2 locally below existing roof |

Target ceiling is the underside of a single1-block structural slab, not a second full-block soffit. Slim lights would reduce local clearance by their own projection only; no lights are placed now. Existing floor/ceiling/furniture removal is future implementation, not a completed action. Coincident old slabs at Y3/38 may be reused only after checking footprint, finish and core holes; coincidence is not acceptance of old soffits.

### Preserve stepped silhouette without short office floors

Live probes confirmed east-wing roof Y8, west-wing roof Y28, main roof Y48 and podium/foyer roof Y=-27. Those height-defining planes and the tower/crown outlines are retained in this proposal.

- **Main lobby8 applies inside the vertically continuous tower/wing envelope.** The entrance/podium perimeter outside that envelope remains a lower foyer under the original Y=-27 roof: floor-to-roof6, clear5 after removing an unnecessary soffit. Open it into the taller lobby; it is part of1F, not a mezzanine. An8-high ceiling everywhere under that low external roof is geometrically impossible without changing the silhouette; do not claim otherwise.
- **East wing:** highest full-height occupied floor is5F, Y=-4. With its original roofY8, the terminal space has11 clear blocks. Omit the6F slab atY3 within that wing and use a local tall volume;6F and higher remain in the main/west envelope. Do not create the4-clear-height short room that floorY3 under roofY8 would produce.
- **West wing:** highest full-height occupied floor is8F, Y17. Under roofY28, terminal space has10 clear blocks. Omit9F slabY24 within the wing;9F and higher remain in the main tower. A floorY24 under roofY28 would leave only3 clear blocks and is rejected.
- Close/guard the upper main-tower interfaces to those tall wing volumes. No unguarded doors into a void. Optional later terrace access needs a separate safe level-change route; no half-storey office floor or forced jump is introduced.
- Retain original main roofY48 and crown/equipment envelope throughY52. Introduce office top slabY45 internally; the2-clear-height space below the old roof is service/plenum only, not12F. Roof stair openings must provide headroom through the existing roof; preserve the exterior headhouse outline. Actual equipment arrangement is not part of this pass.

This yields **11 main building storeys**, not11 floors across every part of the stepped footprint. Tall wing volumes are a proposed structural treatment, not furnished atria or additional counted storeys.

### Window-band plan (no exterior blocks changed)

Typical main-tower repeat, for floor baseF and next floorF+7: slab/beam atF, opaque sill/spandrel atF+1, glazing F+2..F+6 (5 rows), next slab atF+7. This replaces the visual reading of the old5-block rhythm. Main window ranges:

| Floor | Proposed glass Y |
| --- | --- |
| 2F | -23..-19 |
| 3F | -16..-12 |
| 4F | -9..-5 |
| 5F | -2..2 |
| 6F | 5..9 |
| 7F | 12..16 |
| 8F | 19..23 |
| 9F | 26..30 |
| 10F | 33..37 |
| 11F | 40..44 |

The main lobby uses a taller glazed opening; the low entrance foyer keeps its existing roof line. Wing terminal glazing spans the local tall-volume height instead of showing false floor bands at the omitted slabs. Retain blue/light-blue glass, pale vertical piers, depth setbacks and silhouette-defining diagonal braces/crown. Internal-floor-aligned spandrel/infill adjustments are **proposed only**: preserving the exterior silhouette does not magically align every old horizontal band with the new floors. If every exterior block must also remain identical, the mismatch would remain and needs explicit design acceptance before implementation. No facade replacement is authorized as already completed by this document.

### Stair and core plan

Keep the core on the original north side. Do not replay the cancelled low-rise stair: its static audit failed at a return landing. Proposed enlarged stair enclosure stays within the upper main-tower envelope (main outer X=-39..-23, Z=-105..-82): enclosure X=-31..-23, Z=-104..-94; inner width7, depth9. Proposed flights X=-30..-29 and X=-25..-24 are each2 blocks wide, with the divider between and a full-width turning platform. This is a dimensional layout candidate, not collision-certified construction.

- Lobby rise8: **4+4**. Typical rise7: **4+3**, with separate arrival/departure landing geometry to absorb the asymmetric flight length.
- For each lower floorF, northbound steps use Z=-96,-97,-98,-99 at blockY=F+1..F+4; intermediate landing blockY=F+4 at Z=-102..-100. Southbound return starts Z=-99. For7-rise, step blocksY=F+5..F+7 end atZ=-97; for8-rise, the fourth endsZ=-96. Reserve full floor-level turn space atZ=-95 before the south core openingZ=-94. Do not insert a full-height floor cube into the last tread's low half.
- Intermediate landing blockYs: lobby -29; typical -21,-14,-7,0,7,14,21,28,35,42. Floor exits use the main schedule above, including theY45 service landing. Side-wing exits at omitted floors are closed/guarded.
- Retain decorative shaft horizontal positions where compatible; rebuild landing panels/headers and stair holes to new floor datums. Check the shared western stair wall against the eastern lift shaft rather than enlarging through it. No elevator function is added.
- Continue from serviceY45 to exterior roofY48 with a dedicated3-rise roof-access flight/opening, keeping existing headhouse topY52. Check1.8-block player body plus margin, roof-edge guarding and return path. This route has not been built or walked.

Required next test before formal internal rebuilding: collision-aware stair/landing prototype or equivalent guarded test, followed by actual non-flying ascent/descent; inspect the lower-foyer/high-lobby junction and both terminal wing voids. Current acceptance is **arithmetic/constraint planning only**. No claim of live8+7 floor plates, new facade, navigable new core, four-direction visual acceptance or completed Interior Layout.

## Measured baseline

Read all 86 horizontal exact-state slices (117,390 cells) of the live authoring copy in `新的世界`, overworld, epoch `dc8e97dc-0438-4667-97e5-79dad371ba00`. Bounds `(-48,-33,-112)` → `(-14,52,-74)`, X/Y/Z 35/86/39. The bridge's whole-footprint floor heuristic undercounts this stepped tower; actual floor columns and sections were used instead.

| Measurement | Actual value |
| --- | --- |
| Base floor block Y / walking surface | -33 / -32 |
| Highest occupied block / outer top surface | 52 / 53 |
| Total occupied height | 86 blocks |
| Main occupied floors | 16: lobby plus 15 offices |
| Floor block Y sequence | -33; -27,-22,-17,-12,-7,-2,3,8,13,18,23,28,33,38,43 |
| Main roof slab block Y | 48 |
| Lobby floor-to-floor | 6 |
| Typical floor-to-floor | 5, including top occupied floor to roof |
| Structural floor thickness | 1 full block |
| Main office ceiling package | Additional 1-block quartz soffit below the next floor; 2 blocks combined floor/soffit |
| Main office unobstructed clear height | 3 blocks before the thin light fixture projection |
| Side-wing clear height | 4 blocks before fixtures, without that office soffit |
| Lobby main clear height | 4 blocks below quartz ceiling; localized other ceiling conditions remain |
| Stepped wing roof slabs | West Y28, east Y8 |
| Wing occupied levels | West lobby +11 office levels; east lobby +7; these are shared building storeys, not additional main floors |

Representative live column `X=-31,Z=-86`: floor -27, air -26/-25/-24, quartz -23, next floor -22. Lobby at the same column: floor -33, air -32..-29, quartz -28, next floor -27. West column `X=-43,Z=-92`: floor -27, air -26..-23, next floor -22.

Typical facade sample `X=-32,Z=-82`: gray beam at Y=-27, cyan opaque spandrel -26, blue glass -25..-23, next gray beam -22. Thus the current vertical repeat is **1 beam +1 spandrel +3 glass =5**, with mullions replacing selected glass columns and side fins/sills tied to the same levels. This repeat cannot simply stay while floors switch to 6.

Roof slab Y48 occupies one block. Parapet/crown occupy Y49–50; plant uses Y49–51; stair headhouse walls reach Y51 and top-slab cap is in Y52. Reserve **5 block cells including roof slab**, not an extra occupied floor. Side roofs have similar smaller plant/parapet arrangements.

## Core / stair constraints

Live sections confirm two decorative enclosed lift shafts, iron-block landing door panels, and a separate two-flight stair. Lift interior probes `(-37,Y,-101)` and `(-32,Y,-101)` are air at lobby levels. These are not functional elevators; no functional lift/MEP system was found or introduced. No additional dedicated service shaft was identified in the readback; a future services route remains design work.

- Existing stair void: X=-29..-25, Z=-103..-98, with two-block-wide flights and adjoining landing/corridor. Lobby currently uses 3+3 rising steps; typical storeys use 3+2. Actual lobby steps run north at Y=-32..-30 and south at -29..-27.
- Standard 6-rise stairs can be redesigned around a 3+3 flight pair, but all landings and slab holes must move to new floor Y. Do not stretch individual stair blocks.
- Lobby 7-rise needs a 3+4 or equivalent layout and a revised exit landing. The fourth return step approaches the existing landing, so landing position/headroom must be redesigned and collision-tested, not assumed valid.
- Vertical shaft walls can retain their horizontal alignment and be shortened. Lift-door panels, corridor openings, WC door heights, lights and landing interfaces must be rebuilt from each new floor datum.
- Roof access/headhouse must be relocated onto the new roof. Full stair continuity, two-block player clearance and room access are **pending rebuild-stage tests**.

## Primary test and recommendation

**Historical recommendation, now superseded: 5 usable storeys, lobby7 + four typical6.** This was a preference-backed design recommendation informed by the measured35×39 footprint and oversized existing tower, not a claim that the old envelope cannot fit six. It is no longer the approved target: the latest instruction preserves the original height and silhouette.

All heights below use **floor-block Y**; finished walking surface is Y+1.

| Level | Proposed floor block Y | Floor-to-floor to next |
| --- | --- | --- |
| 1F Lobby | -33 | 7 |
| 2F Open office | -26 | 6 |
| 3F Office / meeting | -20 | 6 |
| 4F Office / archive | -14 | 6 |
| 5F Management | -8 | 6 |
| Main roof slab | -2 | Roof module only |
| Highest proposed headhouse-cap cell | 2 | Outer top surface 3 |

Proposed total occupied height: **36 blocks** (-33..2 inclusive), compared with86 now. Keeping86 with this programme leaves50 surplus blocks; no giant empty upper shell/equipment zone is recommended. A 7+5 alternative would worsen surplus height and reduce clearance, so it was **not tested**.

Spatial target with a single structural slab and no extra full-block soffit: lobby6 clear, typical5 clear before slim light projection. Two-block-tall cabinets/partitions/copiers then have about3 blocks above them before fixtures. Existing narrow side wings remain narrow: increasing height does not solve horizontal circulation, which needs later planning. Do not duplicate lobby-style full-height glass on every office floor; revise the typical facade to a controlled 6-block beam/spandrel/window repeat, with roughly4 glass rows where appropriate. Retain blue glazing, pale piers and stepped silhouette language, but reassign lower wing terraces to new level datums rather than globally scaling the old geometry.

**Primary result: EXTERIOR_CONFLICT with the retained old 5-block window grid; 7+6 remains the recommended module.** No evidence from this line study justified reducing the module to5. Full rebuilt-massing appearance and furnished-room comfort are not proven by temporary lines; those await the separately approved rebuild.

## Temporary in-world test / recovery

Only **176 previously-air cells** were filled, using44 exact cuboid operations in one bridge batch after dry-run. Full post-edit readback of117,390 cells found exactly176 intended differences, zero original non-air replacements and zero out-of-plan changes. Non-air count29922 →30098; zero block entities. One entity was reported in the overall plot before/after, but none blocked the scoped edit preflight; no entities were removed. The bridge edit-response counter reported0, so exact world readback, not that counter, is the evidence.

- Yellow: proposed 2F floor Y=-26 (lobby7).
- Cyan: Y=-20,-14,-8.
- Magenta: proposed roof Y=-2.
- Front exterior lines: X=-37..-25, Z=-78 at each of those five heights.
- West exterior lines: X=-48, Z=-91..-83 at the same heights. Vertical light-gray ruler at X=-48,Z=-91, Y=-32..2 between colored bands.
- Lime datum: X=-48,Y=-32,Z=-90..-86 marks ground **walking-surface level**, not another floor block. White cap line at the same X/Z span, Y2.
- Short indoor line fragments at Z=-83, X=-36..-28, only where original air allowed: 8 cells at Y=-26,9 at -20,9 at -14. At -8 the soffit and at -2 an old floor already occupy the desired line; these were deliberately not overwritten. The exterior lines remain continuous.

These are **markers, not slabs or usable new rooms**. Existing floors, soffits, furniture, facade, stairs and neighboring original tower remain. No scale furniture was added; the existing room and lamp were inspected as the baseline. Old slabs prevent standing inside a genuinely rebuilt 6-high room in this non-destructive test, so future room-comfort validation is explicitly pending.

Evidence directory: `build/authoring_checks/office_midrise_01/vertical_20260912/`. `marker_plan.json` records the preflight plan; `result.json` is the applied/verified result with exact176 before/after cells. For cleanup, verify the current epoch/bounds and expected marker state before reverting **only those recorded cells** to their original air. Do not clear a bounding box. One bridge undo entry is available only while this session/history survives; persistent recovery uses the recorded cell manifest and conflict checks.

Live screenshots under `run/afl_authoring_captures/dc8e97dc-0438-4667-97e5-79dad371ba00/` were inspected:

- `6355f55c-316a-42fa-8d64-3ee5bc2f0a48.png`: exterior, proposed bands against unchanged dense facade.
- `083fa774-7274-4260-952b-6eb8e705cfea.png`: existing office soffit/light and yellow floor-line fragments through/inside glazing.

At the audit checkpoint, camera was restored to `(-29.89671704925935,-32,-73.88660247564438)`, yaw-179.53722, pitch7.0792804, flying=false, return_available=false. Markers were then left for review, but **have since been removed during the superseding rollback**. Session remains DRAFT. Resuming after re-entry resets authoring metadata defaults, so category/zones must be reconfigured before any future export (none authorized here).

Source and loaded-runtime NBT retain SHA-256 `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29`. No Gradle tasks, offline save editing, NBT overwrite, furnishing, damage, loot, containers, new blocks or mining changes. Next step, **only after user approval**: Office Building Vertical Layout Rebuild, including floor/soffit, facade and stair/core redesign followed by navigation and visual checks.
