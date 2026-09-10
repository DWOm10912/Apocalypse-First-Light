# Modern Glass Tower 02 — live concept study

Status: **Exported and imported as `office_midrise_01` with explicit user approval; all four in-world rotation tests passed.** The finalization also corrected 15 unsupported WC buttons and re-exported. All 15 upper meeting rooms are open office areas with simple computer-desk placeholders; earlier meeting-room and no-export descriptions below are dated history. The older six-floor draft is preserved. No Small City pool integration. Exhaustive navigation, nighttime lighting, named-shader tests and previously deferred optional furniture/detail work are not claimed complete. See the [final asset and acceptance record](office_midrise_01_authoring.md#current-formal-asset--2026-09-10).

## Scope and dimensions

The user supplied the “Modern Glass Tower 01” concept sheet and requested a separate new building. Its 44-block height, 15 floors, and fully furnished 12×16 footprint cannot all be represented with walkable Minecraft rooms. This study prioritizes the stepped silhouette and usable floors, with increased dimensions. Previous buildings were preserved; only the previous authoring session was cancelled, which discards its bridge undo history without removing blocks.

| Field | Original accepted iteration (before the refinement below) |
| --- | --- |
| Authoring ID | `modern_glass_tower_02` |
| World / dimension | `新的世界` / `minecraft:overworld` |
| Session epoch | `38bbdeb6-b033-455b-ac5b-b5feff4cc937` |
| Plot bounds | (16,-33,-160) → (52,56,-120), inclusive |
| Plot dimensions | 37×90×41 (X×Y×Z) |
| Main entrance | South; local X16–20, Z35; central path to plaza |
| Podium | Local X3–33, Z5–35; floor Y0, roof Y6 |
| Main tower | Local X10–26, Z8–31; roof Y81 |
| Stepped wings | West roof Y61; east roof Y41 |
| Occupied layers | Lobby + 15 tower levels; standard floor plates at local Y6,11,…,76 |
| Upper-floor interval | 5 blocks; shallow ceiling in the main office zone leaves 3 clear blocks |
| Maximum occupied height | 86 blocks including base and stair headhouse cap |
| Original non-air count | 28,438 from actual world readback, not edit-response counter |

Construction coordinates in this history remain relative to the original plot minimum `(16,-33,-160)`. The final export instead uses tightened bounds `(17,-33,-159)` → `(51,52,-121)`, size **35×86×39**, retaining `surface_offset_y=1`. Previously unreviewed category/zones were explicitly configured as `HIGHRISE_OFFICE` / `CORE,MIXED` for export, with `road_facing=true`, `damage_compatible=false`, `loot_ready=false`.

## Original built content

- Blue glazed central facade, light-blue side glazing, light-gray frame, white stepped braces on front/back of staggered wings, concentrated crown louvers.
- Tall glazed lobby with reception, waiting furniture, cafe tables, central entrance, landscape planters and pavement lamps.
- Furnished upper levels: desks, seating, glass meeting-room partition, tea counter, small WC with birch privacy door, plants and lighting. Lower stepped wings contain additional lounge/project-office furniture.
- Two **decorative** enclosed lift shafts/door panels. No functioning elevators, machinery, plumbing, inventory or gameplay systems are claimed.
- Continuous two-flight stair with landings, separate roof access headhouse, parapets, simplified roof plant and duct shapes.
- Clean condition. No apocalypse damage applied.

## Original verification and stopping point

Live `we_batch_set` calls used exact-state cuboids, full-stage dry runs, existing bounds/entity checks and one history entry per batch. The current client was never compiled against, cleaned or redeployed during this task.

Stages executed: Massing → Exterior → Core → Interior → Lobby/Roof → **one completed correction round**. The correction replaced the heavy lobby/office soffits with shallow quartz ceilings and inset lights, and added WC privacy doors. An initial correction preflight was rejected because an entity occupied its work area; no writes had been attempted. A later full preflight passed, then the stage applied. No entity guard was bypassed and no entities were deleted by the tools. User was asked to move bats outside the plot.

After the user said “本轮目前没问题”, construction stopped. Proposed further roof refinement was **not applied**; the unused stage was removed from the recipe. No claim of two completed correction rounds is made.

| Evidence | Result / limit |
| --- | --- |
| Live world readback per stage | Counts and exact-state slices saved in `build/authoring_checks/modern_glass_tower_02/` |
| Massing screenshot | `571a5bb2-932e-41e5-9e2b-6f01e988bdce.png`: silhouette and height relations reviewed |
| Exterior screenshot | `67b2c39d-de07-4c18-bdff-22915657d46d.png`: frame, braces, entrance and crown reviewed |
| Lobby before correction | `9ba10708-ac0f-4ec3-9d49-9e1e25901037.png`: suspended light blocks/heavy ceiling identified |
| Roof overview | `90796267-d4d9-4ad7-a3db-310b97d2c64a.png`: headhouse, equipment and outline visible; chat partially covers lower image |
| Lobby after correction | `13bc85a6-e71e-4a06-a87d-7527e47c63a4.png`: inset lights and quartz ceiling visually confirmed |
| Stair audit | `final_readonly.json`: conservative block-grid path reaches lobby, 15 upper levels and roof with two-air-block headroom |
| Player physics / every room visual inspection | Not exhaustively tested; grid reachability is not a full collision/navigation simulation |
| Final entities | 2 within plot at last readback; not an export-ready entity audit |

Screenshot filenames above are under `run/afl_authoring_captures/38bbdeb6-b033-455b-ac5b-b5feff4cc937/`.

## Source and preservation

- `tools/afl_minecraft_mcp/modern_glass_02_live.mjs`: build recipe, exact world/plot guards, per-stage batch journals. Never blindly replay a completed/uncertain stage; `--retry-preflight` accepts only a journal showing no attempted writes.
- `tools/afl_minecraft_mcp/modern_glass_02_audit.mjs`: read-only world checks, saved reports and framebuffer capture. Does not move the player/camera.
- The original session was left active to preserve bridge undo availability. History is memory-only; subsequent world/client shutdown discarded it. Do not claim previous edits can still be undone through the bridge after restart.
- The original iteration used existing Vanilla definitions; the later lighting pass also uses the existing AFL industrial utility light. No block registration, mining tags, drop rules, or editable Blockbench model was changed by these construction passes; no new mining-tier validation applies.
- The user positioned the camera for all screenshots recorded above; this building task did not implement camera movement. The separate 2026-09-10 [restricted viewpoint bridge](../dev/minecraft_authoring_mcp_v1.md#restricted-inspection-viewpoints-2026-09-10) now provides explicit move/status/restore for later inspection. That implementation does not change this building or retroactively verify camera-operated screenshots.

## Requested facade / room refinement — 2026-09-10

The user then requested indoor AFL industrial lights, removal of exterior sea lanterns pending future streetlights, less interior glass, consistent treatment of all facades and more defined rooms/toilets. The plot, stepped silhouette, floors and neighboring buildings were preserved. Last actual readback was in epoch `337fcee5-8fc8-4fcf-b519-ec666e15681d`; this epoch is now historical because the user closed the client.

| Stage | Applied result / evidence |
| --- | --- |
| Facades | 4,118 changed cells, four batches; opaque spandrels, vertical mullions, slim projected sills/fins across the exposed tower and wing faces, podium infill; existing white diagonal braces retained. Exact-state readback: zero mismatches |
| Exterior lights | Removed two sea lanterns and their eight iron-bar post blocks; no replacement streetlights placed |
| Rooms | 3,824 changed cells, five batches; internal doubled wing glazing replaced with solid partitions and clear passages; meeting rooms now have quartz partitions and only two small observation panes each; improved 15 upper WCs plus a ground-floor public WC and janitor room, west-wing storage/print rooms, reception backing. Exact-state readback: zero mismatches |
| Last readback | 30,276 non-air blocks, zero entities reported, **208 remaining sea lanterns; no industrial lights placed** |
| Live visual checks | Opposing exterior views, standard office, meeting room, WC sink and lobby captured through restricted camera movement and inspected. Final furniture/route correction was interrupted by Esc; it was not applied |
| Camera return | No completed restore was recorded before client exit; the memory-only return anchor is no longer available after shutdown |

Audit source: `tools/afl_minecraft_mcp/modern_glass_02_refine.mjs`. Exact before/after snapshots, plans and per-batch ledgers are under `build/authoring_checks/modern_glass_tower_02/refine_20260910/`; both `facades_ledger.json` and `rooms_ledger.json` report `APPLIED` with no mismatches. Do not replay completed batches or reuse the old epoch without inspecting the restarted world.

The inspected exterior screenshots are `f6645d57-d9c5-4b29-ab59-95802ae124a3.png` (SE) and `d0870710-600c-441e-b8cd-fb02c6cad79b.png` (NW). Interior checks include `c131734b-35c1-4121-b736-59d79615d87b.png` (office), `c41efa69-bf6f-46a4-88d2-0bc49370c460.png` (meeting room), `656eca8c-1862-45ce-9372-a9736c9778ae.png` (WC sink) and `4a489b0a-2566-4611-a6ce-540553b4d1dd.png` (lobby). These are under `run/afl_authoring_captures/337fcee5-8fc8-4fcf-b519-ec666e15681d/`. They are not proof of a completed final stair/room navigation audit.

Industrial-light placement was initially blocked by the bridge's overly broad `light$` safety filter. The [industrial utility light correction](../dev/minecraft_authoring_mcp_v1.md#industrial-utility-light-safety-correction-2026-09-10) changed that filter to the exact Vanilla invisible-light block, with the client closed. Compiling that correction did not itself replace the 208 interior sea lanterns. The separate restarted-client construction pass below has now completed that replacement. No offline save edits or automatic export were part of either task.

## Live indoor industrial-light replacement — 2026-09-10

The user reopened the development client and requested the remaining indoor replacement. Fresh status/readback identified world `新的世界`, epoch `6ef9c05f-0913-4f9e-83be-af3d566abca2`. The existing plot was resumed through in-game chat without clearing any blocks, using `/afl_author resume modern_glass_tower_02 16 -33 -160 37 41 90 1`.

All **208** remaining sea lanterns were removed. Embedded ceiling/wall positions were restored to matching smooth quartz, polished andesite or gray concrete; formerly floating cubes were cleared. **239** existing `apocalypse_firstlight:industrial_utility_light` fixtures were installed at reviewed room-based spacing. This is deliberately not a one-for-one thin-fixture replacement inside the old ceiling voxels.

| Zone / mounting | Final arrangement |
| --- | --- |
| Lobby and ground-floor service rooms | 20 fixtures total: 12 main ceiling lights on a 7-block by 5–6-block grid, 5 north/service-room ceiling lights, 2 lift wall lights and 1 stair wall light |
| Each of 15 main upper levels | 11 fixtures: 4 open-office lights on a 5×4 grid, 2 meeting-room lights 3 blocks apart, 1 WC, 1 corridor, 2 lift wall lights, 1 stair wall light |
| West stepped wing | 3 ceiling lights per occupied level × 11 levels = 33; 6-block spacing, including storage rooms |
| East stepped wing | 3 ceiling lights per occupied level × 7 levels = 21; 6-block spacing |
| Ceiling mounting | `facing=down`, directly below a full supporting ceiling; main upper office fixtures at local floor-base + 3, wings at +4 |
| Core wall mounting | Lift fixtures `facing=south`, stair fixtures `facing=east`, all supported opposite their facing direction |
| Exterior | No new lights or streetlights; prior exterior sea-lantern removal preserved |

Pilot lobby/first-upper-level stage: **71** changed cells and **37** fixtures in one batch. Remaining stage: **360** changed cells and **202** fixtures in four batches. Both stages passed complete dry runs before writes and exact-state readback afterward. Fresh final full-plot readback confirmed **431** changed cells against the initial snapshot, **0** sea lanterns, **239** industrial lights, **30,447** non-air blocks, **0** expected-state mismatches, **0** unsupported fixture backings and **0** changed cells outside the lighting plans. Changed-block counters from edit replies were not used as the authority.

Live framebuffer screenshots were inspected for lobby, standard office, meeting room, WC ceiling attachment, west-wing storage and top-floor office. Examples under `run/afl_authoring_captures/6ef9c05f-0913-4f9e-83be-af3d566abca2/`:

- `9441f828-ba7b-48a5-b491-3fdfbdd9b9fc.png`: lobby ceiling/wall mounting.
- `7a95abdc-52d0-4431-9663-5a7619f0ab9d.png`: meeting-room pair.
- `4b5c5d3c-13bd-4f56-b5cd-a51afc11fd91.png`: WC fixture flush with ceiling.
- `06d70a4b-5ed4-4c59-9c03-7a002bdbbc28.png`: west storage ceiling fixture.
- `e996f1e7-c2b6-4662-839f-3b4cdc390c46.png`: top-floor office.
- `f8223976-1552-4a0e-acbb-0c50a8a598dd.png`: final restored original viewpoint.

Restricted `camera_restore` completed: feet position `(35.30167180211792,-32,-121.43749213103222)`, yaw `171.29178`, pitch `2.5500302`, original `flying=false`; subsequent `client_frame_ready=true`, `return_available=false` and actual screenshot confirmed the return. The plot session remains active; this pass's five batch history entries are memory-only and will not survive world/client shutdown.

Audit/recipe: `tools/afl_minecraft_mcp/modern_glass_02_lighting.mjs`; exact snapshots, reviewed plans, non-replayable stage ledgers, camera evidence and `final_audit.json` are under `build/authoring_checks/modern_glass_tower_02/lighting_20260910/`. Do not replay applied stages or reuse the epoch after restart.

No Gradle tasks, resource processing, compiled-class changes or deployment ran while Minecraft was open. No export, offline save manipulation, weather/time changes or new block definitions. These checks establish placement and sampled live appearance, not exhaustive nighttime illumination, named-shader compatibility, player navigation or completion of the separate furniture/detail pass.

## Meeting rooms replaced with open-office placeholders — 2026-09-10

The user requested batch removal of the cramped meeting rooms, leaving only simple computer-desk placeholder blocks. The same live epoch `6ef9c05f-0913-4f9e-83be-af3d566abca2` and original plot bounds were rechecked before construction. No restart, rebuild or plot clearing was performed.

- Removed the L-shaped quartz partitions, small observation panes, door halves and conference table/chairs from **all 15 upper meeting rooms**. The occupied edit envelope is local X21–25, Z22–29, floor-base +1…+3, with floor bases Y6,11,…,76. These are now open office extensions, not enclosed meeting rooms.
- Each former room has **two** simple 2-block-wide spruce top-slab desktops with one black-concrete monitor placeholder each (**30 new desk groups** total), aligned at local X23–24, Z24/28. No new chairs, cabinets, equipment interactions or functional computers. Existing desks elsewhere were left unchanged.
- Former partition/entry strip at local X21–22, Z22–29 has two-block-wide clearance with at least two air blocks of headroom on every upper level. This is an exact block-grid clearance check, not a full player-navigation test.
- Toilets, outer walls/glazing, floors, ceilings, shafts/stairs, lobby and all **239 industrial lights** remained unchanged. The former meeting-room ceiling lights now serve the open desk area. Sea lantern count remains **0**.

Applied pilot first upper level: **46 changed cells**, 11 exact-state operations, one history batch. After actual screenshot inspection, applied remaining 14 levels: **644 changed cells**, 154 operations, two batches. Every stage had a full dry run and fresh preflight before writes. Final whole-plot comparison: **690 changed cells**, **29,907 non-air blocks**, zero expected-state mismatches, zero changes outside the planned cells, zero checked aisle obstructions and zero light-state changes. No unrelated furniture-polish completion is claimed.

Readback/plans/journals and view metadata: `build/authoring_checks/modern_glass_tower_02/open_office_20260910/`, including `final_audit.json`. Construction/audit source: `tools/afl_minecraft_mcp/modern_glass_02_open_office.mjs`; stages reject blind replay. Three bridge history entries were available at that checkpoint only; subsequent export/test session switches discarded that memory-only history. Do not claim the removed room layout is still undoable through the bridge.

Actual framebuffer screenshots inspected under `run/afl_authoring_captures/6ef9c05f-0913-4f9e-83be-af3d566abca2/`:

- `62456523-593b-4ed2-a9fd-ac0a0049a2b2.png`: enclosed room before removal.
- `9310234e-1de7-489e-bf5d-0b0de009521b.png`: opened pilot office and new placeholder desks.
- `d508009d-4140-4fe0-95ad-e49fc2135de6.png`: completed top-floor office.
- `4f8d718b-885c-4775-9399-8a8c6a60010e.png`: restored original exterior viewpoint.

Restricted camera restoration returned to feet `(35.903277618446836,-33,-116.97046383095632)`, yaw `161.07349`, pitch `-3.4497163`, original `flying=false`; final `client_frame_ready=true`, `return_available=false`. Construction stopped for user inspection. No NBT export, Small City registration, offline save edits, Gradle tasks or deployment; no new block definitions/mining-tier changes.

## Formal export and rotation checkpoint — 2026-09-10

The user subsequently authorized bounds review, validation, export/import and four rotation tests, explicitly choosing this tower for the formal ID `office_midrise_01` while retaining the old six-floor draft. The first test exposed 15 unsupported sink buttons; the approved correction restored their quartz backings and moved the buttons one block south, changing exactly 30 cells. No other layout or fixtures changed.

Final result: **29,922 non-air blocks, 239 industrial lights, 15 supported sink buttons, zero sea lanterns/entities/block entities**. Full NBT/source comparison covered 117,390 cells with zero mismatches. `validate`, new export and source import passed. Metadata and NBT are in the formal resource paths; all four real `/place template` rotations passed complete world readback, with sampled framebuffer inspection including a rotated WC. The 88 normal leaf-distance/iron-bar neighbor updates per placement are explicitly accounted for in the audit.

See [office_midrise_01](office_midrise_01_authoring.md#current-formal-asset--2026-09-10) for exact bounds, metadata, hashes, test origins, screenshots and evidence paths. All test copies and staging templates were removed; original tower and previous-export backup remain. The camera was restored and the test session cancelled. No Gradle compilation/resource processing/deployment ran while the client was open, and **no city generation pool was changed**.
