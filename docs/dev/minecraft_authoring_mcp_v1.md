# Minecraft Authoring MCP Bridge V1

## Purpose and scope

Development authoring only: Node STDIO MCP → authenticated `127.0.0.1` HTTP → integrated-server task queue / render-thread screenshot → WorldEdit 7.2.15 + existing AFL Authoring Framework. No AI API, save-file editing, worldgen, export tool or release runtime dependency. All bridge Java is under `src/dev/java/com/antaurora/apofirstlight/dev/authoring/bridge/`, excluded from release jars; `FMLEnvironment.production` and client-only lifecycle gates apply. No dedicated-server HTTP listener. Published LAN worlds are rejected.

Implementation exists; **V1 acceptance remains partial while selection/write/world-change and final isolation regressions are completed**. Live HTTP status/player and current framebuffer capture were verified in the user's development world on2026-09-09 (2560×1417, non-black image inspected). Compilation, headless WorldEdit behavior and graphical acceptance are distinct checks.

## Prerequisites / activation

1. Java 17, Forge 47.4.22, MC 1.20.1, development `runClient`, WorldEdit Forge 7.2.15. The project already supplies the mapped dev jar; `-PaflWithoutWorldEdit` omits its runtime for isolation tests. Release AFL requires no WorldEdit.
2. Node executable on this computer: `C:\Program Files\nodejs\node.exe`. Run `npm ci --ignore-scripts` in `tools/afl_minecraft_mcp/` (lockfile pinned).
3. Launch development Minecraft once. It creates `run/config/apocalypse_firstlight-authoring-bridge.properties` with defaults:

   ```properties
   authoringAgentBridgeEnabled=false
   authoringAgentBridgePort=0
   ```

   In this chosen development instance set **`authoringAgentBridgeEnabled=true`**. Config is polled once per second; `0` asks the OS for a free loopback port. Changing port requires leaving/re-entering the world. The endpoint exists only while a private integrated world is loaded. Turn the property false to stop it.
4. For authoring writes, enable `buildingAuthoringEnabled=true` in that world's `serverconfig/apocalypse_firstlight-authoring.toml`. Player must be Creative or op2. This is separate from the bridge switch.
5. Build/start from the project root:

   ```powershell
   $env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot'
   $env:GRADLE_USER_HOME="$PWD/.gradle-user"
   .\gradlew.bat build --offline
   .\gradlew.bat runClient --offline
   ```

   **Before compiling, cleaning, processing resources or redeploying, fully exit any Minecraft client loading this checkout's classes/resources. Never build against a running development client.** Start it only after those tasks finish. A release jar does **not** contain this bridge; replacing a PCL release jar will not activate it.

## Add to Codex / Astra

Merge the `[mcp_servers.afl_minecraft]` table from `tools/afl_minecraft_mcp/codex.mcp.example.toml` into `C:\Users\willi\.codex\config.toml` (or the trusted project's `.codex/config.toml`), preserving other settings. Paths in the example match this workstation. Relaunch/reload the MCP host so its tool catalog picks up the server. Do not copy the game token into Codex config.

Alternative CLI registration:

```powershell
codex mcp add afl_minecraft -- 'C:\Program Files\nodejs\node.exe' 'D:\Minecraft Modding\Apocalypse First Light\tools\afl_minecraft_mcp\server.mjs' --game-dir 'D:\Minecraft Modding\Apocalypse First Light\run' --python 'C:\Users\willi\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' --reference-source 'E:\Download\Modern Office Building V.zip'
```

Then set this server's `tool_timeout_sec=150` for offline scans. The provided TOML also uses `default_tools_approval_mode='writes'`. No global MCP registration is performed automatically by repository builds. Configuration format checked using the OpenAI Docs skill against [official MCP configuration documentation](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

## Tools and coordinate contracts

Start every round with `minecraft_status`. This binds the external client to the current world epoch and dimension. After a world/dimension change, review a new status before continuing.

| Tools | Contract |
| --- | --- |
| `minecraft_status`, `get_player_state` | World, versions, player, mode, active session, camera/size; shader state explicitly UNKNOWN, not guessed |
| `get_worldedit_selection` | Exact cuboid only; unsupported selections fail, never silently converted |
| `inspect_selection` | Counts, palette (top256), percentages, BE/entities, occupied height, per-Y density; floor estimate explicitly low-confidence heuristic |
| `get_horizontal_slice`, `get_vertical_slice` | Category or exact palette; max4096 grid samples; `coordinate` absolute unless `relative=true`; Y descending for vertical views |
| `inspect_facade` | NORTH/SOUTH/EAST/WEST, first occupied depth1..16, sample proportions/bands; not perspective imagery |
| `capture_current_view` | Main Minecraft framebuffer on render thread; local PNG path, no OS screenshot or camera movement |
| `camera_move` | Restricted Creative first-person viewpoint positioning; explicit absolute feet `position:[x,y,z]`, `yaw`, `pitch`, optional `dry_run`. Saves the first return anchor; enables Creative flight, never changes gamemode/blocks |
| `camera_restore` | Returns to that anchor and restores its angles/flying flag, with destination checks repeated; optional `dry_run` |
| `camera_status` | Current feet position/angles/flight, `return_available`, `return_position`, `plot_id`, `client_frame_ready` |
| `authoring_create/info/validate/cancel` | Existing lifecycle; no export; create reserves vacant air, cancel leaves blocks |
| `authoring_clear` | WorldEdit undoable clear of active plot, **different from the manual framework's non-undoable token clear** |
| `we_set/replace/walls/faces` | Exact namespaced block states; optional inclusive absolute `min`/`max`, otherwise whole authoring plot |
| `we_copy/paste/rotate_clipboard` | Isolated own-draft clipboard, never reference source; `to` is paste minimum; 0/90/180/270 Y rotation |
| `we_stack/move` | Explicit block-unit `offset`; stack `count`1..128; checked whole affected bounds |
| `we_undo/redo` | Separate bridge history; manual conflicts reject instead of clobbering user edits |
| `we_batch_set` | 1–128 ordered exact-state cuboids (`min`, `max`, `block`); summed volume including overlaps ≤250,000. Full preflight before writing, one undo entry, same scope/entity/material restrictions and rollback on failure. Dry run also checks history capacity. |
| `export_target_registry` | Actual running block IDs/properties, dedicated JSON file, no world edits |

Read targets: `REFERENCE_SELECTION` (default), `AUTHORING_SESSION`, `REFERENCE_AREA`. Optional `min`/`max` crops must be entirely inside the target. Downsample1..32 is nearest-grid sampling, not majority aggregation. Legend: `#` solid, `G` glass, `D` door, `S` stairs, `A` air, `W` wood/furniture, `M` metal, `?` other. Categories are heuristics; exact palettes remain authoritative.

Block writes always require AUTHORING_SESSION (except the explicitly separate reference paste workflow). Both source and destination must fit. Reads/edits max250,000 blocks; never silently load chunks. Bounds, permission, entities, unsafe states and all destination blocks are checked before editing. Pattern V1 deliberately supports one exact state, not arbitrary WorldEdit expressions/NBT. Regular draft edits reject existing block entities, fluids, falling blocks and hazardous dynamic machinery. No new gameplay blocks added, so no mining/tier changes.

### Industrial utility light safety correction (2026-09-10)

Current visual asset: `apocalypse_firstlight:industrial_utility_light` now uses the 23-cube modern square ceiling panel from `src/main/blockbench/modern_square_ceiling_light.bbmodel` and its 128×128 texture. `tools/export-modern-square-ceiling-light.mjs` converts the ceiling-authored source into the existing floor-facing runtime model at `src/main/resources/assets/apocalypse_firstlight/models/block/industrial_utility_light.json`; it also installs the matching texture as `textures/block/industrial_utility_light.png`. The existing blockstate rotations, block/item registry IDs, placement, light emission, shapes, tool tiers, drops, and item display transforms remain unchanged. Its current names are 室内吸顶灯 / ceiling-mounted square LED panel light. This resource replacement has not yet been verified in a graphical client.

`WorldEditAdapter.safeState` now rejects the exact Vanilla invisible editor block `minecraft:light` using `Blocks.LIGHT`, rather than rejecting every registry ID ending in `light`. The old suffix check incorrectly blocked the ordinary AFL fixture `apocalypse_firstlight:industrial_utility_light` both as a new material and inside an existing source/edit region. This is a development-bridge validation fix, not a change to lamp geometry, light strength, drops or mining tiers.

- Exact fixture states such as `apocalypse_firstlight:industrial_utility_light[facing=down]` are allowed, subject to all other existing checks. Supported facing values are `down`, `north`, `south`, `east`, `west`; `up` is not a valid state.
- Place ceiling fixtures **below** a sturdy supporting ceiling with `facing=down`; wall fixtures need support opposite their facing direction. Do not replace an embedded sea-lantern ceiling voxel with a thin fixture in the same voxel and assume correct attachment/appearance. Restore the ceiling material and choose supported fixture positions.
- Block entities, fluids (including waterlogged states), falling blocks, command/structure blocks, invisible light, barriers, TNT and the other existing hazard filters remain rejected. No blanket mod-ID whitelist or bypass was added. The same check still protects material parsing, source regions, batch edits and history operations.
- Restart the development client after compiling. A release jar excludes this bridge; no PCL jar replacement is needed. The tower's separate [live indoor replacement](../worldgen/modern_glass_tower_02_authoring.md#live-indoor-industrial-light-replacement--2026-09-10) is now complete after restart: 208 sea lanterns removed, 239 supported industrial fixtures, zero remaining sea lanterns by full-plot readback. Construction was not performed by this code fix or by rebuilding; no compilation ran during the live construction pass.

Regression entry point: `gradlew -I src/dev/industrial-light-gametest.init.gradle runGameTestServer --offline -PaflWithoutTacz`. This requires actual WorldEdit and uses a fresh isolated world plus namespace `afl_bridge_light_tests`. `BridgeGameTests.java` verifies five exact facing states, support/emission, dry-run non-mutation, copying/replacing an existing fixture, undo/redo and rejection of the invisible light plus 16 other hazard/BE/fluid/falling examples. Safety rejection may come from AFL's state guard or WorldEdit's own configured denylist; unknown-state/other parse errors are not accepted as passing safety tests.

The pre-fix run reproduced `UNSAFE_OR_DYNAMIC_BLOCK: apocalypse_firstlight:industrial_utility_light` (`build/industrial-light-regression-before.log`). During the broader post-fix run, industrial-light checks passed, but the later reference-building undo check failed with `REFERENCE_HISTORY_CONFLICT: user edits preserved` (`build/industrial-light-regression-final.log`). **That broader suite is not green.** The reference-history failure remains a separate unresolved regression; its guard was not disabled or changed for this lamp fix. Test worlds are under `build/`, not the user's building save. No graphical lighting appearance check or in-world replacement is implied by these headless tests.

Final targeted result: **1/1 GameTest group passed** with actual WorldEdit (`build/industrial-light-targeted-verified.log`); Node MCP tests also passed **5/5**. An initial standalone test configuration selected zero cases because the template namespace differed; that run is not acceptance evidence. The corrected template namespace and init-script completion-marker check prevent a zero-test run from being reported as passing this regression.

`gradlew build --offline` passed (`build/industrial-light-build-final.log`, including 9,078 projection-math cases). The resulting `build/libs/apocalypse_firstlight-1.0.0.jar` was checked and contains no `com/antaurora/apofirstlight/dev/**` entries. The corrected bridge is compiled into development classes for the next `runClient`; nothing was deployed into a running client or PCL instance. All compilation and headless tests ran after the user exited Minecraft.

## Restricted inspection viewpoints (2026-09-10)

`camera_move` moves the **actual local player**, not a detached spectator camera. It exists only in the development bridge; there is no generic teleport, arbitrary command/chat, gamemode, FOV, noclip or entity-deletion tool. WorldEdit is not required for these three camera tools. The existing token/world-epoch/dimension handshake, request deduplication and no-retry timeout contract also apply to movement.

| Restriction | Current rule |
| --- | --- |
| Access | Private integrated world, bridge enabled, authoring enabled, active authoring reservation in the same dimension; player Creative with flight permission |
| View/input | First person with player as camera entity; close chat, menus and containers before a movement request. Riding, sleeping, dead or carrying passengers is rejected |
| Coordinates | Absolute player **feet** coordinates, finite JSON numbers only, no `~`/`^`; yaw −180…180°, pitch −90…90°. Yaw 0 faces south, ±180 north, −90 east, +90 west; positive pitch looks down |
| Authoring vicinity | Move feet must be within the continuous plot bounds expanded 64 blocks in X/Z, 16 below and 48 above; this is not an unrestricted-world teleport |
| Travel / loading | At most 256 blocks per move or restore; destination player bounding-box chunks must already be loaded. No explicit chunk generation/tickets by the bridge; normal player movement may cause Minecraft's ordinary surrounding chunk streaming |
| Destination | Player fits inside build height/world border; body must avoid collisions, fluids and other entities. Portal/fire/cactus/magma/powder-snow/berry-bush/wither-rose blocks in the checked footprint are rejected. No claim of exhaustive third-party hazard detection |
| Movement effects | Zero velocity and fall distance; Creative flight enabled so roof/exterior viewpoints do not immediately fall. Player mode, world blocks, time/weather, FOV, HUD and shader options stay unchanged |
| Return anchor | First non-preview move saves position, angles and original flying flag; later moves do not replace it. Restore rechecks distance/loading/obstructions; a rejected restore keeps the anchor. Only that saved origin may be outside the plot margin |
| Lifecycle | Anchor is memory-only and bound to the player, reservation and bridge lifetime. Successful restore consumes it. Cancel/new reservation invalidates it; world close/bridge stop discards it. No automatic teleport on cancel/exit and no claim that it survives restart |
| Frame readiness | After a successful move/restore, require **two consecutive rendered frames** matching the requested camera pose (position tolerance 0.03 blocks, angle tolerance 0.15°). `capture_current_view` rejects `CAMERA_NOT_SETTLED` until then; a server acknowledgement alone is not screenshot evidence |

Use `dry_run:true` first: it performs the same checks but does not move, change flight, save a return point or arm frame waiting. This is an instantaneous safe-endpoint move, not a smooth path or obstacle-avoiding flight. Do not move the mouse/player during capture settling. The tool does not lock user input; if readiness stays false, inspect state, close menus or choose a new safe viewpoint rather than polling indefinitely.

Example calls for the approved `modern_glass_tower_02` plot (not automatic execution):

1. `minecraft_status` and `authoring_info`; confirm the world and plot, Creative first person. After restart, the user can re-reserve the existing building without clearing it using `/afl_author resume modern_glass_tower_02 16 -33 -160 37 41 90 1`, then close chat. Do not cancel a different session without checking it first.
2. `camera_move({"position":[34,15,-90],"yaw":-180,"pitch":0,"dry_run":true})`. This is a south-side exterior view; acceptance still depends on current distance, loaded chunks and occupancy.
3. Repeat without `dry_run` to move. Query `camera_status` at short, bounded intervals until `client_frame_ready:true`, then `capture_current_view` and inspect the returned actual PNG. Read world slices separately; the screenshot does not replace block-state verification.
4. Repeat for selected facade/interior/roof viewpoints. This is tool orchestration, not an automatic multi-view renderer or preset generator.
5. `camera_restore({"dry_run":true})`, then `camera_restore({})`; wait for readiness before a final capture. Restore **before** cancelling the authoring session or closing the world. If the original point is blocked, do not clear blocks or force teleport; let the user resolve it. If over 256 blocks away, first move to a safe allowed point nearer the anchor.

Implementation: `AuthoringCamera.java` (server-side constraints/anchor), `CameraFrameGate.java` (thread-safe immutable pose handoff), `AuthoringBridgeClient.java` (client/private-world gate and rendered-camera observation), `BridgeRouter.java` (dispatch), all under `src/dev/java/com/antaurora/apofirstlight/dev/authoring/bridge/`. MCP schemas are in `tools/afl_minecraft_mcp/models.mjs`. No additional settings or release-jar deployment are needed; restart the development client after compiling and refresh the MCP host tool catalog if needed.

## Reference → original authoring workflow

1. Select reference using ordinary WorldEdit in-game. Call status, selection, summary, four facades, selected slices and screenshot.
2. Summarize transferable principles; explicitly redesign footprint, floor/core/circulation, entrance, segmentation, windows, roof and material ratios.
3. `authoring_create`, then bounded WorldEdit edits (`dry_run=true` for large edits/clear/paste/move).
4. Inspect own draft, capture, revise; stop for user review. Never copy the reference into the own-draft clipboard or call export autonomously.
5. User alone runs `/afl_author export` after review/validation. Bridge exposes no export tool.

See [Reference Map Importer](reference_map_importer_v1.md) for offline ZIPs and the separate in-game reference area.

## Authentication / lifecycle / limits

- Fresh random 256-bit token and world UUID each endpoint/world session. Discovery at `run/afl_authoring_bridge/session.json`; token never logged/returned by status, never checked into Git. Only loopback bind. No CORS; browser Origin requests rejected. Bearer auth required; request body64KiB maximum.
- HTTP executor does not read world/player or perform edits. Server tasks validate world/epoch/dimension again; framebuffer uses client/render task queue. Queued requests expire before execution at15s. External timeout does **not** imply rollback: `TIMEOUT_OUTCOME_UNKNOWN_DO_NOT_RETRY_WRITE` requires inspecting the world. No automatic write retries.
- Last128 request IDs deduplicated within endpoint lifetime. This is not durable transaction recovery. Normal disconnect/config disable removes discovery; a process crash can leave a stale file, but no active server/token reuse; new endpoint overwrites discovery.
- History is memory-only, max32 accepted world edits and approximately1,000,000 recorded changes. New edit clears redo. Cancel/new authoring session invalidates clipboard/history; world close invalidates all bridge history. Save/backup reviewed work before closing. Never present Undo as persistent across restarts.
- Only WorldEdit lighting side effect enabled; neighbor/physics updates suppressed for bounded construction. Complex redstone/physics builds are outside this architectural V1.
- Without WorldEdit: native authoring-session inspection and screenshot remain available; WE selection/edit fail explicitly. Dedicated/headless tests do not load client endpoint classes.

## Verification and troubleshooting

Commands: `npm test` in MCP folder; `python -m unittest discover -s tools/afl_reference_map_importer/tests -v`; `gradlew -I src/dev/bridge-gametest.init.gradle runGameTestServer --offline -PaflWithoutTacz`; repeat with `-PaflWithoutWorldEdit`. Init script uses a fresh isolated world for each run.

Current evidence: MCP SDK stdio handshake/tool catalog/error path, camera schemas and mock epoch guard tested (5 Node tests; stale epoch rejects `camera_move`/`camera_restore` before HTTP). A sandboxed loopback transport run failed once; the final outside-sandbox rerun passed all 5. WorldEdit edits/history/conflict/scope/slices were tested in earlier headless GameTests. Real HTTP status/player and current framebuffer non-black screenshot were verified in the user's private development world; WorldEdit selection initially incomplete. Live world-change guard and user-world write/undo acceptance remain pending. Do not infer these from a Java build or mock transport test. During initial connection the agent called the same Node bridge client directly; this is not a claim that the server is already registered in the current Codex tool catalog.

Camera-specific regression (2026-09-10): `gradlew -I src/dev/camera-gametest.init.gradle runGameTestServer --offline -PaflWithoutTacz -PaflWithoutWorldEdit` passed **2/2** test groups in a fresh isolated world, recorded in `build/camera-gametest-final.log`. `CameraGameTests.java` covers explicit poses, dry runs, strict numeric/argument rejection, plot/travel/height/world-border limits, unloaded chunks without loading, collision/liquid/entity/hazard rejection, dimension mismatch, permission/menu/riding guards, first-anchor retention, blocked restoration, original flight restoration, reservation invalidation and the two-render-frame handoff math. Forge fake-player transport normally no-ops teleport; the test substitutes Vanilla's actual server packet listener with outbound packet sends stubbed, not a real client. No graphical client, shader, packet round-trip or full bridge/reference-importer regression is claimed by these isolated tests.

Separate live evidence (2026-09-10): in epoch `6ef9c05f-0913-4f9e-83be-af3d566abca2`, the `modern_glass_tower_02` lighting pass completed restricted camera movement → client-frame readiness → inspected framebuffer screenshots → successful return to the original position/angles and `flying=false`. Final status reported `client_frame_ready=true` and `return_available=false`; screenshot `f8223976-1552-4a0e-acbb-0c50a8a598dd.png` under that epoch confirms the restored view. This validates that live inspection sequence, not every camera error path, named shaders, or the unresolved reference-history regression. Per-view status and captures are recorded in `build/authoring_checks/modern_glass_tower_02/lighting_20260910/`.

Final `gradlew build --offline` also passed (`build/camera-build-final.log`, including the existing 9,078 projection-math cases). The release jar still excludes all `com/antaurora/apofirstlight/dev/**` classes. All compilation/testing finished with the user's graphical client closed; the existing building/save was not edited or exported by this camera implementation.

| Error | Action |
| --- | --- |
| MCP_NOT_CONNECTED | Check development client, private world, bridge properties and gameDir; release jar cannot host bridge |
| WORLD_SESSION_CHANGED / DIMENSION_MISMATCH | Call status, review current world/session; do not retry stale coordinates |
| NO_ACTIVE_AUTHORING_SESSION | Create/resume your draft in correct dimension |
| CHUNK_NOT_LOADED | Load the intended area manually; bridge never forces chunks |
| ENTITY_IN_EDIT_REGION | Stand outside target volume; move entities yourself |
| HISTORY_CONFLICT | Manual edits preserved; inspect before deciding what to change |
| MAX_EDIT_LIMIT | Split region; do not raise it blindly |
| WORLDEDIT_REQUIRED | Install/use development WorldEdit, not a runtime dependency for players |
| CAMERA_CREATIVE_REQUIRED / CAMERA_PLAYER_BUSY | Use Creative with flight permission, dismount/wake and close the container; the tool never changes gamemode |
| CAMERA_PRIVATE_WORLD_REQUIRED / CAMERA_FIRST_PERSON_REQUIRED / CAMERA_CLOSE_SCREEN_FIRST | Private development world only; switch to player first-person view and close chat/menus |
| CAMERA_OUTSIDE_PLOT_MARGIN / CAMERA_TRAVEL_LIMIT_256 | Choose a nearer, plot-scoped viewpoint; check reservation and coordinates first |
| CAMERA_WORLD_BOUNDS / CAMERA_CHUNK_NOT_LOADED | Choose a loaded position within build height/world border; no forced chunk loading |
| CAMERA_DESTINATION_OBSTRUCTED / CAMERA_ENTITY_AT_DESTINATION / CAMERA_UNSAFE_DESTINATION | Choose a clear safe viewpoint; never bypass by clearing blocks or deleting entities |
| CAMERA_NO_RETURN_POINT | No successful move saved an anchor, it was consumed, or the reservation/world/bridge changed; inspect current position |
| CAMERA_NOT_SETTLED | Wait briefly for two matching rendered frames; check `camera_status.client_frame_ready`, do not screenshot a stale frame or poll forever |

Restricted explicit camera move/status/restore and `we_batch_set` are implemented. Automatic viewpoint generation, smooth camera paths, detached cameras and a one-call multi-view capture tool are not implemented.
