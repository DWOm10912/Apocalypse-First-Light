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

   Restart an already-running client to load new Java code. A release jar does **not** contain this bridge; replacing a PCL release jar will not activate it.

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
| `authoring_create/info/validate/cancel` | Existing lifecycle; no export; create reserves vacant air, cancel leaves blocks |
| `authoring_clear` | WorldEdit undoable clear of active plot, **different from the manual framework's non-undoable token clear** |
| `we_set/replace/walls/faces` | Exact namespaced block states; optional inclusive absolute `min`/`max`, otherwise whole authoring plot |
| `we_copy/paste/rotate_clipboard` | Isolated own-draft clipboard, never reference source; `to` is paste minimum; 0/90/180/270 Y rotation |
| `we_stack/move` | Explicit block-unit `offset`; stack `count`1..128; checked whole affected bounds |
| `we_undo/redo` | Separate bridge history; manual conflicts reject instead of clobbering user edits |
| `we_batch_set` | 1–128 ordered exact-state cuboids (`min`, `max`, `block`); summed volume including overlaps ≤250,000. Full preflight before writing, one undo entry, same scope/entity/material restrictions and rollback on failure. Dry run also checks history capacity. |
| `export_target_registry` | Actual running block IDs/properties, dedicated JSON file, no world edits |

Read targets: `REFERENCE_SELECTION` (default), `AUTHORING_SESSION`, `REFERENCE_AREA`. Optional `min`/`max` crops must be entirely inside the target. Downsample1..32 is nearest-grid sampling, not majority aggregation. Legend: `#` solid, `G` glass, `D` door, `S` stairs, `A` air, `W` wood/furniture, `M` metal, `?` other. Categories are heuristics; exact palettes remain authoritative.

Writes always require AUTHORING_SESSION (except the explicitly separate reference paste workflow). Both source and destination must fit. Reads/edits max250,000 blocks; never silently load chunks. Bounds, permission, entities, unsafe states and all destination blocks are checked before editing. Pattern V1 deliberately supports one exact state, not arbitrary WorldEdit expressions/NBT. Regular draft edits reject existing block entities, fluids, falling blocks and hazardous dynamic machinery. No new gameplay blocks added, so no mining/tier changes.

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

Current evidence: MCP SDK stdio handshake/tool catalog/error path and mock epoch guard tested (4 Node tests); WorldEdit edits/history/conflict/scope/slices tested in headless GameTest. Real HTTP status/player and current framebuffer non-black screenshot were verified in the user's private development world; WorldEdit selection initially incomplete. Live world-change guard and user-world write/undo acceptance remain pending. Do not infer these from a Java build or mock transport test. See final test log names in the task handoff. During initial connection the agent called the same Node bridge client directly; this is not a claim that the server is already registered in the current Codex tool catalog.

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

Optional automatic multi-view camera capture and edit batches are not implemented in V1.
