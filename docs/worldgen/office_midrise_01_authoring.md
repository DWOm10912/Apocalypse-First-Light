# office_midrise_01 — modern concrete office, formal source

## Current formal asset — 2026-09-12

At the user's request, the current modern concrete office (including subsequent on-site adjustments after Facade Revision V2) was captured under `office_midrise_01` and **overwrote the former glass-tower source NBT and metadata**. This is a new building, not the old 86-block shell reset. Its live authoring plot is in `新的世界` / `minecraft:overworld`, inclusive bounds `(80,-33,-464)` → `(108,36,-442)`; captured NBT size is **29×70×23** (X×Y×Z), 46,690 cells, **15,747 non-air**, zero entities. The front is SOUTH, surface offset 1, category `HIGHRISE_OFFICE`, zones `CORE,MIXED`, road-facing true, damage-compatible false and loot-ready false. The authoring ID was changed from `office_concrete_01` to `office_midrise_01` before the explicit export; the world blocks were not moved or rebuilt for this rename.

The current formal files are [Structure NBT](../../src/main/resources/data/apocalypse_firstlight/structures/office_midrise_01.nbt) and [building metadata](../../src/main/resources/data/apocalypse_firstlight/small_city/buildings/office_midrise_01.json). Their source pair matches `run/afl_authoring_exports/office_midrise_01.{nbt,json}`; the NBT SHA-256 is `9a1b8508412e849982fa1b6fc7d495702d2a949222607f75de329ab4490b592b`. The prior formal pair and superseded export pair were preserved under `build/authoring_checks/office_midrise_01/pre_concrete_v2_replacement_20260912/`; the old formal NBT SHA-256 was `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29`.

The fresh live snapshot and the exported NBT matched **all 46,690 cells with zero differences**; NBT size, block palette and 0-entity requirement were checked, then the standard importer accepted the pair with explicit overwrite. The live authoring validation passed. This is a **source-resource replacement only**: no Small City spawn pool, runtime-loaded resource, Java, placement logic or gameplay behavior was changed. The current metadata lists all four Vanilla rotations by authoring convention, but **the new concrete building has not been tested at those rotations**. The old tower's four-rotation results below are historical and do not validate this replacement. Full navigation, worldgen placement, Survival drops and visuals after a client resource reload remain unverified.

See [the concrete building record](office_concrete_01_authoring.md) for floor plan, V2 facade and furnishing notes. This captured version contains **84 additional live cell differences** from the last saved V2 checkpoint, mainly lower-façade ribs and entrance adjustments; these current changes were included, not discarded.

## Latest development direction — 2026-09-12

The user superseded the old-envelope renovation with a completely new concrete office. See [office_concrete_01](office_concrete_01_authoring.md): nine usable floors, 70-block occupied height, live new plot `(80,-33,-464)` → `(108,36,-442)`, shell/core/first-pass furnishing built. **This paragraph records the earlier development checkpoint; the concrete building has now replaced the formal NBT, but has not entered a city pool.**

The old authoring copy at `(-48,-33,-112)` → `(-14,52,-74)` was subsequently reset to an eleven-floor empty interior shell (18,490 non-air in its saved final readback), preserving its 86-block external envelope. That supersedes earlier statements that the live draft still has sixteen floors. Its floor slabs are Y=-33,-25,-18,-11,-4,3,10,17,24,31,38 with office top slab45; stairs and furnishings were removed and core openings reserved. This draft's visual closeout was interrupted by the new-building request. Its reservation is now cancelled **with blocks preserved**, not an active plot and not an exported update. The previous formal resource had the sixteen levels described below; it is backed up, not current. Do not resume the obsolete36-block reduction or treat the11-floor draft as the new concrete building.

## Previous formal asset — 2026-09-10 (historical; superseded)

The user previously selected `modern_glass_tower_02` for export as **`office_midrise_01`**, while preserving the older six-floor draft. At that time the formal resource was the glass highrise described in [its construction history](modern_glass_tower_02_authoring.md), **not** the legacy development builder below. Its export, source import and four actual in-world rotation tests were complete before it was superseded. **No Small City pool integration.** The following table, hashes and tests describe that previous NBT only.

| Final contract | Value |
| --- | --- |
| ID / structure | `office_midrise_01` / `apocalypse_firstlight:office_midrise_01` |
| World / dimension | `新的世界` / `minecraft:overworld` |
| Inclusive capture bounds | `(17,-33,-159)` → `(51,52,-121)` |
| Size X×Y×Z / volume | **35×86×39** / **117,390** cells |
| Footprint / height | 35×39 / 86 |
| Category / zones | `HIGHRISE_OFFICE` / `CORE, MIXED` |
| Front / surface offset | `SOUTH` / `1` (world surface Y=-32) |
| Occupied levels | Lobby + 15 tower levels; stepped wings |
| Non-air / entities / block entities | **29,922 / 0 / 0** |
| Industrial utility lights / sea lanterns | **239 / 0** |
| road_facing / damage_compatible / loot_ready | `true / false / false` |
| authoring_version / rotations | `1` / all four Vanilla rotations |

The tightened bounds remove only empty margins of the original 37×90×41 plot. Historical construction recipes still use the old origin `(16,-33,-160)`; do not reinterpret those local coordinates against this export origin. The `midrise` filename is the user-selected ID, not the tower's architectural category.

Formal file locations at that checkpoint (these links now resolve to the concrete replacement; use the preserved backup for the historical bytes):

- [Structure NBT](../../src/main/resources/data/apocalypse_firstlight/structures/office_midrise_01.nbt)
- [Building metadata](../../src/main/resources/data/apocalypse_firstlight/small_city/buildings/office_midrise_01.json)

Historical export pair is backed up under `build/authoring_checks/office_midrise_01/pre_concrete_v2_replacement_20260912/`; the live `run/afl_authoring_exports/office_midrise_01.{nbt,json}` pair is now the concrete replacement. The historical NBT SHA-256 is `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29`; its export and formal source hashes matched at that time. Source readback matched all **117,390** NBT cells exactly and `validate` passed. The import script copied both files with explicit overwrite approval; metadata alone does not register any generation pool.

### Approved sink-button correction

The first 0° placement revealed **15 unsupported upper-floor sink buttons**: each occupied a hole in its backing wall. With explicit user approval, at Y=-25,-20,…,45 the cell `(29,Y,-142)` was restored to `minecraft:smooth_quartz`, and the button moved to `(29,Y,-141)` with `face=wall,facing=south,powered=false`. Exactly **30 cells** changed; no other layout or light states changed. Whole-source readback reported zero mismatches, followed by a fresh validation/export/import.

The superseded export pair is retained in `run/afl_authoring_exports/pre_button_fix_20260910/`; it is not the formal asset. Its NBT SHA-256 is `32fb87c6d07fe48eb6e2b0afe4e3eab96cb8862b9e90e1e0f61fe24a3b1974d4`.

### Actual rotation acceptance

In live epoch `6ef9c05f-0913-4f9e-83be-af3d566abca2`, game UI `/place template` commands placed the **newly exported NBT** under temporary ID `afl_rotation_acceptance:office_midrise_01_20260910_fixed`. The test reservation `(17,120,-159)` → `(55,205,-121)` was checked empty before placement and cleared between runs. No overlap with the source tower, whose highest block is Y=52.

| Rotation / command argument | Placement origin | Front | Live readback result |
| --- | --- | --- | --- |
| 0° / `none` | `(17,120,-159)` | SOUTH | PASS; 29,922 non-air, 239 lights, 15 buttons |
| 90° / `clockwise_90` | `(55,120,-159)` | WEST | PASS; 29,922 non-air, 239 lights, 15 buttons |
| 180° / `180` | `(51,120,-121)` | NORTH | PASS; 29,922 non-air, 239 lights, 15 buttons |
| 270° / `counterclockwise_90` | `(17,120,-125)` | EAST | PASS; 29,922 non-air, 239 lights, 15 buttons |

Every full test-volume readback had **zero mismatches against the rotated, normally settled template**, zero entities and zero block entities. This checks state rotation as well as positions, including doors, stairs, wall buttons and industrial-light facing. Vanilla updates 72 persistent leaves from `distance=1` to `7` and connects 16 iron bars to neighboring bars after placement: **88 expected state updates**, not lost blocks or unexplained differences. Source NBT preserves the authored states; the audit explicitly derives these normal placement updates rather than ignoring arbitrary differences.

Live screenshots were inspected for each exterior and for the repaired source/90° WC button. Capture directory: `run/afl_authoring_captures/6ef9c05f-0913-4f9e-83be-af3d566abca2/`.

| View | Screenshot |
| --- | --- |
| Repaired source WC | `150c7320-cf16-4a7c-a4ea-c322c601663e.png` |
| 0° exterior | `a22443a6-5a5d-4fa4-86d6-94cda2bb9819.png` |
| 90° exterior / WC | `a22eacac-ee44-49c5-b415-62a74666ba1c.png` / `0e3494b6-2bcb-4ac4-bacb-2f7c4c1276b2.png` |
| 180° exterior | `9f3b82ba-84b8-483c-b944-9397aa581199.png` |
| 270° exterior | `eeecdf39-c366-4870-9e41-3886d89a4d80.png` |
| Restored original viewpoint | `5a108950-562b-4273-9414-70d942c52f41.png` |

Evidence: `build/authoring_checks/office_midrise_01/export_20260910/fixed/`, especially `button_repair_audit.json`, `export_audit.json`, `rotation_0_audit.json`…`rotation_3_audit.json`, and `clear_0_after.json`…`clear_3_after.json`. Harness: `tools/afl_minecraft_mcp/office_midrise_01_acceptance.mjs` (world/session guarded; do not blindly replay completed mutations).

All four temporary world copies were cleared with actual zero-non-air readback. Both temporary `generated/afl_rotation_acceptance/structures/office_midrise_01_20260910*.nbt` staging files were removed after exact hash checks; their contents remain recoverable from the current export and pre-fix backup. Original camera position was restored, the test reservation cancelled, and construction stopped. Original tower and legacy draft were preserved; cancelled-session undo history is not persistent recovery.

Verification limits: importer unit tests **6/6 PASS** and harness syntax check PASS in this finalization. No Gradle task, loaded `build/classes`/`build/resources` write, launcher deployment, offline region edit or pool integration. The resources are ready in **source**, not claimed packaged into a newly built jar. Actual placement/screenshots do not constitute exhaustive room navigation, nighttime lighting or named-shader testing. No new block definitions, mining tags or drop behavior changed.

### Live authoring copy — 2026-09-12

In the user's open development client, world `新的世界` / `minecraft:overworld`, the current formal `apocalypse_firstlight:office_midrise_01` template was placed into a **new, previously empty** authoring reservation at `(-48,-33,-112)` through `(-14,52,-74)` (35×86×39). Source and loaded `build/resources/main` NBT both had SHA-256 `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29` before placement. The prior tower at `(17,-33,-159)` through `(51,52,-121)` was not overwritten; both appeared in the captured live view `run/afl_authoring_captures/81edb1c6-8fa8-4319-926c-e3fb0d08f3dc/4e2a96e1-55f6-478c-b26f-fd4eb2d5bf9d.png`.

Live readback of the new reservation found **29,922 non-air blocks, 239 industrial utility lights, 15 polished-blackstone buttons, zero entities and zero block entities**. The active session was refreshed to `DRAFT` and configured to match the formal metadata: `HIGHRISE_OFFICE`, zones `CORE,MIXED`, `road_facing=true`, `damage_compatible=false`. The temporary inspection camera was restored to the player's original position, with no return point remaining. This is a world authoring copy for further edits, **not a new export or city-pool registration**. No source NBT, metadata JSON, Java, or runtime resource was changed by the placement. If the client/world session is closed, the in-memory reservation must be resumed at these exact bounds before further authoring; the world blocks themselves remain.

### Vertical-layout study — 2026-09-12

The [measured audit and historical proposal](office_vertical_layout_audit_v1.md) tested176 temporary floor-line markers and recommended lobby7 + typical6 with five usable floors and total height36. **That reduced-height proposal was superseded during the subsequent rebuild; the markers are now removed.** Current live copy and formal NBT are again86 high with16 original levels. The old5-block facade repeat versus a revised internal module remains a planning constraint, not permission to shorten the tower.

### Structural rebuild superseded / original tower restored — 2026-09-12

The initially authorized five-storey shell was applied only to the authoring copy in epoch `dc8e97dc-0438-4667-97e5-79dad371ba00`. It changed30,639 cells in21 WorldEdit batches, with exact readback matching the temporary10,055-block shell. A static route audit identified a lobby return-flight collision; physical traversal and four-facade acceptance had **not** passed. Before further corrections, the user changed direction: **keep the original overall height and tower silhouette; replan only internal floors**. The36-block design is cancelled, not a completed building or a pending export.

Recovery checked that all117,390 cells still matched the applied plan, then undid exactly those21 batches. Full readback matched the pre-rebuild snapshot with zero mismatches. The176 obsolete audit-marker cells were subsequently verified against their expected colors and removed in one scoped batch. Another complete comparison against baseline minus those markers found **zero mismatches**: live copy is restored to **29,922 non-air blocks,86 occupied blocks high, original16 levels, original exterior/core/interior**. Nothing was restored by editing offline save files. The source tower elsewhere and formal resource NBT were not overwritten.

Evidence: `build/authoring_checks/office_midrise_01/structural_v1_20260912/` contains `baseline.json`, `plan.json`, `ledger.json`, `route_audit.json`, `rollback_ledger.json` (`RESTORED_BASELINE_VERIFIED`), `restored.json` and `original_tower_restored.json`. Baseline includes the historical176 markers; the last snapshot does not. Cancelled recipe/recovery harness: `tools/afl_minecraft_mcp/office_structural_rebuild_v1.mjs`; static-only test: `office_structural_route_audit.mjs`. Do not replay the cancelled low-rise recipe. Marker cleanup has a current-session undo entry; the exact snapshot records remain the persistent recovery evidence.

No formal furnishing, damage pass, final Promo NBT export, Worldgen NBT overwrite, city-pool change, Java build or runtime deployment. Formal NBT SHA-256 remains `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29`. The user has since specified the revised internal target below. No revised internal structure has yet been applied.

### Height-preserving8+7 / eleven-storey planning — 2026-09-12

The latest explicit target is **lobby8, typical7,11 usable main storeys**, retaining the original86-block height, footprint and tower/stepped-roof silhouette. This supersedes both the cancelled36-block proposal and the earlier7+6 dimensions. Floor-block Ys: **-33,-25,-18,-11,-4,3,10,17,24,31,38**; office top/service baseY45. Existing exterior roofY48 and highest cellY52 remain. Calculation8+10×7+8=86; the roof/service allowanceY45..52 includes structural slabs and crown, not an additional office floor.

The [updated vertical plan](office_vertical_layout_audit_v1.md#current-height-preserving-internal-plan--87--11-floors) records floor/clear-height tables, proposed7-block window bands and4+4/4+3 staircase/landing geometry. Fixed low roofs need explicit exceptions: lower entry foyer beneath podiumY=-27 opens into main8-high lobby; east wing terminates with a tall5F volume below roofY8, west with a tall8F volume below roofY28. These avoid short offices while preserving roof outlines. Main tower continues through11F. Void-edge guarding and roof-access transitions require subsequent design/testing.

**Planning only:** current world still contains the restored original16-level tower. This pass refreshed live status and read eight selected horizontal slices without changing world blocks or resources. No new floor plates, facade bands, core, test markers, furnishings or NBT exports. New stair collision/player traversal and redesigned interior visual acceptance remain pending; this document is not permission to report them passed.

## Legacy six-floor draft checkpoint (preserved)

**Everything below describes the older six-floor development scaffold, not the formal NBT above.** Its world origin, dimensions, builder commands and old verification records are preserved for reference. Do not run `build` or `upgrade office_midrise_01` to reconstruct or modify the exported glass tower.

MODE B: development-only direct builder. PASS1 MASSING was placed in the user's world and its proportions approved (origin64,-33,16, SOUTH). PASS2/BASIC upgrade code and static-collision headless test were added, but the turn switched to MCP Bridge work before user-world upgrade/visual acceptance; that legacy checkpoint remains pending. No NBT of this six-floor draft was exported/imported and no pool registration occurred; the current same-name resource instead contains the separately approved glass tower above.

Pending upgrade: restart development client, stand outside the existing building, `/afl_author resume office_midrise_01 64 -33 16 28 34 40 1` (only if no active session), then `/afl_author upgrade office_midrise_01`. Upgrade checks baseline→detail delta atomically, refuses conflicts with manual edits, preserves unrelated cells, and does not export. Adds BASIC lobby/offices/corridors, six two-wide stair flights to roof, decorative elevator, lighting and HVAC. Headless static collision connectivity passed; user-controlled navigation/visual review not performed. This task is paused while the optional [MCP Bridge](../dev/minecraft_authoring_mcp_v1.md) is completed.

Reference: user image `E:/Download/ChatGPT Image 2026年9月9日 21_36_33.png` (American late-20th-century six-storey office, brick base/piers, pale frame, horizontal dark glazing, central glass lobby, flat roof/HVAC). Exact requested dimensions take precedence over image pixel proportions. Building is intact, not randomly damaged.

| Contract | Value |
| --- | --- |
| Legacy builder ID / historically proposed structure | office_midrise_01 / apocalypse_firstlight:office_midrise_01 (formal ID later reassigned to the glass tower) |
| Category / zones | MIDRISE_OFFICE / CORE, MIXED |
| Capture size X/Z/Y | 28 / 34 / 40 |
| Local capture bounds | X=0..27, Z=0..33, Y=0..39 |
| Origin / front | MIN_X_MIN_Y_MIN_Z / SOUTH (+Z) |
| Surface offset | 1 |
| Floors | 1,6,11,16,21,26; four empty blocks above each interior floor |
| Roof / parapet | slab Y31, roof gravel Y32, perimeter Y32–33 |
| Roof massing | two gray HVAC blocks; rear-west future stair-access headhouse up to Y37 |
| Interior | PASS1 empty; optional upgrade implements BASIC, pending user-world acceptance |
| road_facing / damage_compatible / loot_ready | true / true / false |
| Version / rotations | 1 / all four Vanilla rotations (eventual metadata) |

## User placement (not automatic)

The current session had an open Gradle `runClient`, but no exposed Minecraft command-control interface. Restart that development client after compilation to load the new command. In a backed-up development world enable `buildingAuthoringEnabled=true` in its `serverconfig/apocalypse_firstlight-authoring.toml`. Use Creative or op permission >=2. This builder is intentionally **absent from the release jar**, and also rejects production environments at command registration.

Choose empty loaded space; the framework reserves a 16-grid plot roughly 32 blocks east at the player's block Y. It does not flatten terrain. Stand outside the capture volume:

```text
/afl_author create office_midrise_01 28 34 40 1
/afl_author build office_midrise_01
/afl_author info
```

Builder assigns MIDRISE_OFFICE / CORE,MIXED and sets DRAFT; origin is printed in chat. Inspect the SOUTH side (world Z=originZ+33), from 20–60 blocks away and above for skyline. `bounds` toggles the framework's temporary guides.

Existing occupied session: do not automatically clear/rebuild. A repeated build rejects nonempty bounds. The framework's explicit token-confirmed clear is available only if the user intentionally discards that draft. After logout, use `resume` with recorded origin/dimensions; ordinary manual edits remain in-world. No live-world overwrite occurred in this implementation task.

## PASS 1 implemented / review requested

- Stone-brick foundation, six full smooth-stone floor plates, pale shell with brick vertical piers.
- Three SOUTH window bays per upper floor, continuous side bands, reduced rear glazing.
- Brick ground floor, glass lobby flanks, centered four-wide three-high open entry and inset dark canopy. Entrance is an opening for massing, not a finished door assembly.
- Complete roof, two-block parapet profile, gravel surface, coarse HVAC/access silhouette.
- No furniture, containers, entities, functional machines, loot or debug blocks. Concrete is architectural material, not a debug marker.

**Pause here for massing approval.** First-floor lobby can be entered; upper floors currently require Creative flight. Stair core, elevator/core, roof access, offices, lights, detailed HVAC, fire escape and weathering are intentionally pending PASS2/3. Do not claim navigable 1F→roof or BASIC interior yet.

Review width/height ratio, six-floor rhythm, three-bay facade, entrance and skyline before detailed work. After approval: PASS2 facade/service/fire-escape/HVAC refinement, then PASS3 BASIC rooms/corridors/stairs and roof access, then navigation review. Only explicit final approval authorizes `/afl_author validate`, export/import and rotation tests. Export still never authorizes city-pool integration.

## Source and verification

- `src/dev/java/com/antaurora/apofirstlight/dev/OfficeMidrise01DraftBuilder.java`: deterministic bounded blueprint, foundation/floor/frame/windows/lobby/roof methods. Only writes into an empty, loaded active session; full blueprint bounds checked before writes.
- `BuildingAuthoringCommands.active`: small permission-checked session accessor in the existing framework; no framework replacement.
- `src/dev/java/com/antaurora/apofirstlight/dev/OfficeMidrise01MassingGameTests.java`: isolated one-command build, floor/clearance/bays/entry/bounds/material checks, no BE/entities/export, duplicate-build refusal.
- `src/dev/office-massing-gametest.init.gradle`: isolated headless test world; no current player world writes.

Historical scaffold verification: PASS, 1/1 isolated GameTest (`build/office-massing-test.log`, 1m9s); 12,208 occupied blocks, six floors, four-block interior clearances, front bays/entry, outside sentinel preserved, duplicate build refused, no BE/entities/export. Build: PASS (`build/office-massing-build.log`, 16s), including existing projection math tests. That jar inspection confirmed OfficeMidrise01 dev builder/test classes were absent; office NBT and metadata resources did not yet exist. At that initial build checkpoint, graphical runClient/user-world result was NOT_TESTED; the later massing was placed and its proportions approved as recorded above. Legacy NBT EXPORT: NO. These old test/build results are not a build or BASIC-interior acceptance of the current glass-tower resource.
