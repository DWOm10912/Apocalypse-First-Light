# Restroom Partition V1

## Doorway endpoint correction (2026-09-13)

For a one-neighbor endpoint with a perpendicular `door_support`, the generated variant uses only the real connected arm, junction and doorway support. It no longer retains the ordinary end module's unused forward half-panel/post. `RestroomDoorwayShapes.java` is generated from these same boxes. Door removal restores the normal End Post, and `door_support=0`/ordinary partition connection rules are unchanged. The original source models and door animations are unchanged.

All 65 variants pass resource checks, including absent forward tails and legitimate foot extents. The latest `build/stall-door-fixes.log` has relevant door/partition GameTests passing and 71/72 overall (unrelated TNT regression fails); build tasks completed. No graphical client was launched; user visual acceptance is pending. This supersedes the older full-suite count below.

Status: registered static decoration block; uses the approved 42-cube source and a dedicated laminate texture.

The matching [Restroom Stall Door](restroom_stall_door.md) now has Runtime V1 and open/close animations. Graphical acceptance remains pending; locks and occupancy are not implemented.

## Source and dimensions

- Template: `src/main/blockbench/office_cubicle_partition.bbmodel` (unchanged).
- Source: `src/main/blockbench/restroom_partition.bbmodel`.
- Editable texture: `src/main/blockbench/textures/restroom_partition.png`, 128×128 PNG, also embedded in source.
- Runtime texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/restroom_partition.png`.
- 42 cubes, 17 groups. Root `restroom_partition_root`; fabric groups/cubes renamed laminate.
- Bounds: X 0–16, Y 0–32, Z 6.7–9.3. Width 1 block, height 2 blocks.
- Panel thickness 1.2 pixels (0.075 block); frame 1.9 pixels; maximum foot thickness 2.6 pixels (0.1625 block). Foot extent intentionally preserved rather than forcing all parts under 0.12 block.
- Panel bottom 3.2 pixels; lower frame underside 2.3 pixels; feet touch Y=0. The unobstructed clearance below the lower rail is 2.3 pixels, unchanged from the approved template.
- Geometry modifications: none. All cube bounds, rotations, origins, face UVs and group pivots match template exactly. Root/group origin remains [8,0,8].

## Materials

Smooth warm grey HPL/waterproof laminate panel: #C5C4BC to #BEBEB7, no fabric or random noise. Mid-grey frame #696E71 with restrained highlights and darker #565D61 feet / #41484C pads. Existing single texture atlas layout retained; all 252 faces remain bound. No door, hinge, latch, animation or world generation.

## Runtime behavior

- Registry ID: `apocalypse_firstlight:restroom_partition`; names: 厕所隔板 / Restroom Partition.
- Implementation: `RestroomPartitionBlock` extends `OfficeCubiclePartitionBlock` without a second connection algorithm. The inherited NORTH/SOUTH/EAST/WEST properties connect only to the same block instance; adjacent office partitions do not join.
- Independent one-block placement, about two-block visual and collision height. Requires a sturdy floor and empty collision above. No BlockEntity, animation, tick, or stall door.
- With `door_support=0`, the inherited 16 cached connection-dependent VoxelShapes and ordinary multipart rules remain unchanged. An independent attachment bitmask (N=1,S=2,E=4,W=8) selects 65 doorway-specific end/support variants when a lateral lower stall door is adjacent. The door never enters the ordinary connection graph. Only the facing end post/foot is replaced and its panel endpoint extended; removal of the door restores the original end. Runtime models and generated VoxelShapes use the same boxes. See the linked door contract for dimensions and files.
- Creative tab: AFL building/decorative items (beside office partition). Strength 0.8, blast resistance 1.5, metal sound, no required mining tool, no `requiresCorrectToolForDrops()`, no `mineable/*` or `needs_*_tool` tags. Survival empty-hand breaking drops one restroom partition via its loot table.

## Verification and next integration

`node tools/build-restroom-previews.mjs`: exact geometry/UV/pivot comparison PASS. Blockbench MCP source loaded with 42 cubes / one texture; front, back, side and oblique material views inspected.

Connection previews derive from the existing office connection preview layout, removing desks/chairs. The runtime generator is `tools/build-office-cubicle-partition-runtime.mjs restroom_partition`; the resource verifier uses the same asset argument. The screenshots below are Blockbench model previews, not in-world acceptance evidence.

Separate preview sources (not the production single module):
- `src/main/blockbench/previews/restroom_preview_straight_2.bbmodel`
- `src/main/blockbench/previews/restroom_preview_corner_L.bbmodel`
- `src/main/blockbench/previews/restroom_preview_U_cubicle.bbmodel`

Blockbench screenshots inspected: [front](previews/restroom_partition/front.png), [back](previews/restroom_partition/back.png), [side](previews/restroom_partition/side.png), [oblique](previews/restroom_partition/angle.png), [two straight](previews/restroom_partition/straight_2.png), [corner](previews/restroom_partition/corner_L.png), [open cubicle](previews/restroom_partition/U_cubicle.png).

## Original partition verification (2026-09-13, before stall-door integration)

- Both office and restroom runtime resource verifiers: PASS; five models and twelve multipart rules each, with no positive-volume junction overlaps.
- `processResources compileJava build`: PASS. Restroom and office partition GameTests: PASS on the headless Forge server. These check real BlockItem placement, all four directions, straight/corner/T/cross, 16 collision shapes, neighbor rebuilding, no cross-material connection, upper-space rejection, and Survival empty-hand drops.
- The complete 71-test GameTest run returns failure because the unrelated `vanillatntuseslivestrength` TNT event-count test fails consistently. `nativenoiseflatdistances` noise-AI movement also failed in one earlier run, then passed on the final rerun. These failures do not change the PASS result for the two partition tests.
- Graphical client model baking, in-world texture appearance, creative inventory appearance, and player-feel collision remain unverified in this integration run. Existing Blockbench previews are not a substitute for in-world acceptance.

## Stall-door integration regression (2026-09-13)

Ordinary office/restroom partition GameTests still PASS after adding doorway attachments. Resource checks cover the original five base models/twelve ordinary clauses plus 65 doorway variants. Stall-door tests confirm attachment after placement and exact restoration after door removal, with the ordinary graph remaining zero for door-only neighbors. The final complete suite is 70/72 due to unrelated TNT and noise-distance failures; the relevant tests pass. Build passes; graphical client acceptance remains pending. See `restroom_stall_door.md` for the exact generated geometry, support bitmask and validation logs.
