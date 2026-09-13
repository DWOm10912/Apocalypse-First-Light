# Restroom Stall Door — model V1

Status: source model, texture, animations and Runtime V1 implemented. Graphical client acceptance remains pending. Locks, occupancy indicators and crowbar lock interactions are not implemented.

## Runtime V1 (2026-09-13)

### Corrective verification

- Mining particles now reference `apocalypse_firstlight:block/restroom_partition`, the pixel-identical texture stitched into the block atlas, rather than the unstitched entity texture. Door rendering keeps its entity texture.
- A single-neighbor endpoint turning into a perpendicular doorway uses its connected arm plus junction/support, with the unused forward half-panel and post removed. Generated collision boxes match. Door removal restores the ordinary End Post; source geometry, pivot and animations are unchanged.
- Open-leaf raycasting now passes an immutable owner position to `VoxelShape.clip`: `betweenClosed` reuses its mutable cursor and `BlockHitResult` retains the reference. Previously the returned position drifted to the end of the scan, preventing closing. The regression checks the exact position and closes using that hit in all eight facing/hinge combinations.
- Current resource verification passes for all 65 attachment variants, particle texture equality, forward-tail removal and unchanged source. `build/stall-door-fixes.log`: build tasks completed, relevant door/partition GameTests pass; full suite 71/72 with unrelated `vanillatntuseslivestrength` failure. No graphical client launched; visual/in-world acceptance remains pending. This supersedes the earlier full-suite result below.

- ID `apocalypse_firstlight:restroom_stall_door`; 厕所隔间门 / Restroom Stall Door, building/decorative creative tab. Strength 1.5, resistance 3, METAL sound, correct-tool drops required, `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`. Iron, diamond and netherite pickaxes yield one door; empty hand, wood and stone do not. No Fortune/Silk Touch special behavior.
- `src/main/java/com/antaurora/apofirstlight/block/RestroomStallDoorBlock.java`: true 1×1×2 LOWER/UPPER occupancy. Only LOWER owns `RestroomStallDoorBlockEntity`; upper clicks forward. Breaking either half removes the pair, with one qualified Survival drop; Creative drops none. Placement requires a sturdy floor, replaceable dry upper space and height/world-border/permission checks. Removing a partition does not remove the door; removing the floor does.
- BlockState: `facing=north/south/east/west`, `hinge=left/right`, `open=false/true`, `half=lower/upper`. Placement chooses the plane with most lateral restroom neighbors, preferring the player's facing on ties. One supporting side chooses that hinge; with both/neither, the clicked lateral half chooses the hinge. Existing doors do not rotate when neighbors change.
- The whole source door is centered in the cell without resizing. Export compensates GeckoLib's X inversion; renderer uses the same cardinal rotation as `HorizontalShapeUtils`. RIGHT mirrors the complete model about local X=8, including the hinge/animation. The authoritative `.bbmodel`, its geometry, pivot, texture and animations are unchanged.
- Right-click plays `restroom_stall_door_open` / `restroom_stall_door_close`, 0.35s (7 ticks). Server schedules completion, retains the previous OPEN/shape until completion, then updates both halves. A pending transition ignores repeated toggles. Stable loop poses are `door_open_pose` / `door_closed_pose`. Transitions are transient: unloading/reloading restores the last committed state, not a partially opened pose. No automatic close or redstone toggle.
- One vanilla iron-door sound per accepted transition, from the master, volume 0.6 and pitch 1.15. No new audio asset.
- NORTH closed leaf: X 1.5–14.5, Z 7.25–8.75, Y 3.5–29.5 (model units). NORTH open LEFT: X 12.8–14.3, Z -6.15–6.85; RIGHT: X 1.7–3.2, same Z/Y. Shapes are split at Y=16 into the two cells, then cardinally rotated. The lower gap and upper gap are empty; the open central doorway has no selection/collision air wall. Hardware protrusions do not add fine collision boxes. Animation uses stable endpoint shapes, not moving per-frame collision.
- Open leaves extend into the front cell. `RestroomDoorRaycast`, `RestroomDoorInput`, `RestroomDoorPickMixin` and `RestroomDoorC2SPacket` provide bounded supplemental picking/use with server reach and occlusion revalidation. No full doorway interaction box. Shared network protocol is 26; server/client must match.

### Doorway support contract

Normal partition graph remains self-only. `RestroomPartitionBlock.DOOR_SUPPORT` is an independent 0–15 attachment mask (N=1,S=2,E=4,W=8), not a panel connection. Only adjacent LOWER doors whose facing is perpendicular to that direction qualify. Placement and neighbor updates recompute the mask. Door removal returns it to zero and restores the ordinary model/shape.

For a door in the center cell, adjacent support posts occupy the former preview positions: centers X=0.75 and 15.25, inner edges 1.3 and 14.7. The unchanged 13-unit leaf spans 1.5–14.5, leaving 0.2-unit seams. On an isolated neighbor, the facing post is moved 1.5 units into the doorway; the panel/rail endpoint is extended to meet it. Other graph layouts receive the same terminal position regardless of their original end-post offset. The old facing post/foot is removed, not duplicated. Only partition resources/shape own supports; the door source contains none.

`tools/build-restroom-doorway-runtime.mjs` generates 65 disjoint graph/attachment variants (`models/block/restroom_partition/doorway_<graph>_<support>.json`) and `block/RestroomDoorwayShapes.java` from the exact same boxes. With zero attachment, original multipart rules and inherited shapes are used unchanged. Running the existing restroom base generator also invokes this layer, so it cannot silently erase attachment resources.

The canonical east-side support variant also has an editable, generated companion at `src/main/blockbench/restroom_partition_doorway_support.bbmodel`. It reproduces `doorway_0_4`; the generator is authoritative for the other graph/rotation combinations. The original partition and door sources are not overwritten.

### Runtime files and checks

- Common: `block/RestroomStallDoorBlock.java`, `block/RestroomPartitionBlock.java`, generated `block/RestroomDoorwayShapes.java`, `block/RestroomDoorRaycast.java`, `blockentity/RestroomStallDoorBlockEntity.java`, `item/RestroomStallDoorBlockItem.java` under `src/main/java/com/antaurora/apofirstlight/`.
- Client: `client/RestroomStallDoorModel.java`, `client/RestroomStallDoorRenderer.java`, `client/RestroomStallDoorItemRenderer.java`, `client/RestroomDoorInput.java`, `mixin/client/RestroomDoorPickMixin.java`; networking: `network/RestroomDoorC2SPacket.java`, `network/AflNetwork.java`. Registrations: AflBlocks/AflItems/AflCreativeTabs/AflBlockEntities and AflBlockEntityRenderers.
- Assets: `geo/restroom_stall_door.geo.json`, `animations/restroom_stall_door.animation.json`, `textures/entity/restroom_stall_door.png`, blockstate/particle-only block model and builtin/entity item model. Loot and both mining tags live under `src/main/resources/data/`; both language files updated.
- `node tools/verify-restroom-doorway-runtime.mjs`: source-to-Geo transform/pivot, texture equality, bounded inverse clips, 65 model-state mappings and world-grid support seams. `node tools/verify-office-cubicle-partition-runtime.mjs [restroom_partition]` checks ordinary partition resources. `RestroomStallDoorGameTests` checks real server placement, 8 facing/hinge transitions, spam, delayed commit, rays/shapes, support restoration and Survival tools on both halves.
- Graphical client not launched: final render/texture, item inventory appearance, mirrored animation feel, actual walking clearance, and client-side picking remain user acceptance checks. Headless tests do not prove these visual results.

### Final verification

- Four Node resource/source verifiers PASS; `git diff --check` PASS (line-ending warnings only). Door source SHA-256: `d07a6dc600225c85ec7f9d8b9af6bff187f7c534cbfecfbcba1ec8e2d56cc377`.
- `processResources compileJava build --offline`: PASS; independent build log `build/stall-door-build.log`.
- Final headless run `build/stall-door-acceptance.log`: stall door, restroom partition and office partition tests PASS. It additionally checks real partition placement after a door, floor removal, partition removal retaining the door, and Creative upper-half no-drop. All 12 Survival combinations (six tools × two halves) PASS.
- Full suite is **70/72**, not a global PASS: `vanillatntuseslivestrength` and `nativenoiseflatdistances` fail outside this feature. The preceding run had 71/72 with only TNT failing. These systems were not modified. No graphical client was launched, and no gameplay/visual PASS is inferred from headless results.

## Animation V1

`restroom_stall_door_open` and `restroom_stall_door_close` each last 0.35 seconds. Only `door_leaf` has rotation keys: Y 0 to -90 degrees and -90 to 0 respectively. Two Bezier keys per clip use symmetric horizontal endpoint tangents (time handles ±0.1166667 seconds), producing eased starts/stops without overshoot. Clips hold their final pose; neither loops nor automatically closes. Handle and latch follow the parent, with no independent animation, position or scale keys.

The existing left hinge pivot [14.6,0,6.95], geometry, base pose and texture are unchanged. Runtime V1 now implements the completion-before-OPEN/shape-commit contract and the right-hinge mirrored transform described above.

Blockbench played five complete open/close cycles and reached exactly -90/0 degrees without accumulated drift. Animation previews: [closed](previews/restroom_stall_door/animation_closed.png), [open](previews/restroom_stall_door/animation_open.png). No game client or build was run for this model-only change.

## Deliverables

- Production source: `src/main/blockbench/restroom_stall_door.bbmodel` (Blockbench free format, cube geometry and hierarchical pivots).
- Texture: `src/main/blockbench/textures/restroom_stall_door.png`, 128×128; pixel-identical to the approved restroom partition atlas and embedded in the source.
- Installation preview only: `src/main/blockbench/previews/restroom_stall_door_installation_preview.bbmodel`.
- Build script: `tools/restroom-stall-door.blockbench.js`, executed through Blockbench MCP.
- Capture script: `tools/capture-restroom-stall-door.blockbench.js`.
- Geometry/material verification: `node tools/verify-restroom-stall-door.mjs`.

## Dimensions and orientation

- Leaf including thin metal edging: X 1.5–14.5, Y 3.5–29.5, Z 7.25–8.75.
- Leaf width 13 units, height 26 units, thickness 1.5 units (0.8125 × 1.625 × 0.09375 blocks). Hardware protrusions are separate: total Z extent 6.2–9.55.
- Bottom clearance 3.5 units; top clearance relative to the 32-unit partition is 2.5 units.
- The source partition's post inner edges are X=1.3 and X=14.7. Its 13.4-unit aperture permits a 13-unit leaf with 0.2-unit gaps on both sides. This actual aperture takes priority over the suggested 13.5–14.5-unit leaf width.
- Front is viewed from negative Z toward positive Z. The LEFT hinge is on positive X when viewed from this front; do not mirror based only on a group label from the partition source.
- `restroom_stall_door_root` and `door_leaf` pivot: [14.6, 0, 6.95]. Closed rotation [0,0,0]. Rotating `door_leaf` by Y=-90 degrees opens toward negative Z around the hinge axis without a position offset. Right-hinge variants can later mirror about X=8, including the pivot.

## Structure and material

42 cubes, 8 groups, one atlas, all 252 faces bound to its texture UUID in Blockbench. `door_leaf` contains `panel`, `frame`, `hinge_upper`, `hinge_lower`, `handle`, and `latch`.

Warm-grey smooth laminate, restrained grey metal edging, two hinges, front vertical pull, and a neutral-grey rear slide latch. No red/green indicator, text, glass, dirt, or damage pass. The final model contains no partition posts, feet, or partition top rail.

Reference posts/feet exist only in the separate preview source under `preview_only_posts`; its reference group and 22 reference cubes have export disabled. They are not door runtime geometry. The original preview is not a world-grid layout; Runtime V1 supplies the partition-owned doorway variants to reproduce its post spacing in actual neighboring cells.

## Verification

- Source checks PASS: cube count, valid bounds, 128×128 texture, embedded/external PNG equality, texture bindings, correct hinge pivot, zero base rotations, two rotation-only animations, and no production posts.
- Static 0–90 degree sweep in 1-degree steps PASS for leaf/panel/edging/handle/latch versus reference posts. Hinge mounting hardware is excluded from this geometric clearance test.
- Model V1 front/back/side/oblique/installation screenshots were inspected. Animation V1 adds formal open/close clips and endpoint previews; the older pivot-check image remains a static reference.
- Historical model-only delivery did not build or run a client. Runtime V1 validation is described above; client verification remains pending.

Previews: [front](previews/restroom_stall_door/front.png), [back](previews/restroom_stall_door/back.png), [side](previews/restroom_stall_door/side.png), [oblique](previews/restroom_stall_door/angle.png), [installation](previews/restroom_stall_door/installation.png), [installation front](previews/restroom_stall_door/installation_front.png), [temporary pivot check](previews/restroom_stall_door/pivot_check_90deg.png).
