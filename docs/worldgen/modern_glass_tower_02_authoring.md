# Modern Glass Tower 02 — live concept study

Status: **Construction stopped; user reported this iteration looks fine.** No NBT export, Small City registration, runtime resource changes or release packaging. This is a building in the development world, not a generated-world feature.

## Scope and dimensions

The user supplied the “Modern Glass Tower 01” concept sheet and requested a separate new building. Its 44-block height, 15 floors, and fully furnished 12×16 footprint cannot all be represented with walkable Minecraft rooms. This study prioritizes the stepped silhouette and usable floors, with increased dimensions. Previous buildings were preserved; only the previous authoring session was cancelled, which discards its bridge undo history without removing blocks.

| Field | Current world state |
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
| Final non-air count | 28,438 from actual world readback, not edit-response counter |

Local coordinates in this document are relative to plot minimum. The registration's `surface_offset_y=1` is unchanged. Category/zones remain unreviewed defaults and are not an approval for export.

## Built content

- Blue glazed central facade, light-blue side glazing, light-gray frame, white stepped braces on front/back of staggered wings, concentrated crown louvers.
- Tall glazed lobby with reception, waiting furniture, cafe tables, central entrance, landscape planters and pavement lamps.
- Furnished upper levels: desks, seating, glass meeting-room partition, tea counter, small WC with birch privacy door, plants and lighting. Lower stepped wings contain additional lounge/project-office furniture.
- Two **decorative** enclosed lift shafts/door panels. No functioning elevators, machinery, plumbing, inventory or gameplay systems are claimed.
- Continuous two-flight stair with landings, separate roof access headhouse, parapets, simplified roof plant and duct shapes.
- Clean condition. No apocalypse damage applied.

## Verification and stopping point

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
- Authoring session remains active to preserve current bridge undo availability; undo history is memory-only and will not survive restarting the world.
- All placed blocks are existing Vanilla definitions. No block registration, mining tags, drop rules, editable Blockbench model, or runtime behavior was changed; no new mining-tier validation applies.
- User-authorized autonomous inspection camera movement is a separate future bridge change. It is **not implemented here**. The user positioned the camera for these screenshots.

No further construction or export should occur without a new user request.
