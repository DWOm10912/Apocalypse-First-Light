# office_midrise_01 — authoring draft

## Current checkpoint

MODE B: development-only direct builder. PASS1 MASSING was placed in the user's world and its proportions approved (origin64,-33,16, SOUTH). PASS2/BASIC upgrade code and static-collision headless test were added, but the turn switched to MCP Bridge work before user-world upgrade/visual acceptance; that checkpoint remains pending. No office Structure NBT or metadata resource has been exported/imported, no pool registration.

Pending upgrade: restart development client, stand outside the existing building, `/afl_author resume office_midrise_01 64 -33 16 28 34 40 1` (only if no active session), then `/afl_author upgrade office_midrise_01`. Upgrade checks baseline→detail delta atomically, refuses conflicts with manual edits, preserves unrelated cells, and does not export. Adds BASIC lobby/offices/corridors, six two-wide stair flights to roof, decorative elevator, lighting and HVAC. Headless static collision connectivity passed; user-controlled navigation/visual review not performed. This task is paused while the optional [MCP Bridge](../dev/minecraft_authoring_mcp_v1.md) is completed.

Reference: user image `E:/Download/ChatGPT Image 2026年9月9日 21_36_33.png` (American late-20th-century six-storey office, brick base/piers, pale frame, horizontal dark glazing, central glass lobby, flat roof/HVAC). Exact requested dimensions take precedence over image pixel proportions. Building is intact, not randomly damaged.

| Contract | Value |
| --- | --- |
| ID / future structure | office_midrise_01 / apocalypse_firstlight:office_midrise_01 |
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

Test: PASS, 1/1 isolated GameTest (`build/office-massing-test.log`, 1m9s); 12,208 occupied blocks, six floors, four-block interior clearances, front bays/entry, outside sentinel preserved, duplicate build refused, no BE/entities/export. Build: PASS (`build/office-massing-build.log`, 16s), including existing projection math tests. Jar inspection confirms OfficeMidrise01 dev builder/test classes are absent; office NBT and metadata resources do not exist. Graphical runClient/user-world result: NOT_TESTED, awaiting user restart and command execution. NBT EXPORT: NO. This is a scaffold checkpoint, not a finished BASIC-interior asset.
