# office_midrise_01 — exported glass tower and preserved legacy draft

## Current formal asset — 2026-09-10

The user explicitly selected the current `modern_glass_tower_02` for export as **`office_midrise_01`**, while preserving the older six-floor draft. The formal resource is therefore the glass highrise described in [its construction history](modern_glass_tower_02_authoring.md), **not** the legacy development builder below. Export, source import and four actual in-world rotation tests are complete. **No Small City pool integration.**

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

Formal files:

- [Structure NBT](../../src/main/resources/data/apocalypse_firstlight/structures/office_midrise_01.nbt)
- [Building metadata](../../src/main/resources/data/apocalypse_firstlight/small_city/buildings/office_midrise_01.json)

Export pair: `run/afl_authoring_exports/office_midrise_01.{nbt,json}`. The NBT SHA-256 is `f4f287b4354f3189c35548c738cb5317c0326325e6b9423af4c936040d623e29`; export and formal source hashes match. Source readback matched all **117,390** NBT cells exactly and `validate` passed. The import script copied both files with explicit overwrite approval; metadata alone does not register any generation pool.

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
