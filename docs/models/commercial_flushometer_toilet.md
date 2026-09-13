# Commercial Flushometer Toilet — closed dry ceramic cavity

Status: V1 static decorative block registered and exported for Forge 1.20.1. Game-client visual and Survival checks remain pending. The current approved direction is DRY: no water surface, exposed drain mechanism or central raised cap.

## V1 game integration

- Registry ID: `apocalypse_firstlight:commercial_flushometer_toilet`; building/decorative creative tab. English `Commercial Flushometer Toilet`, Chinese `商业马桶`.
- One ordinary block, no BlockEntity, animation, interaction, water, fluid or flush logic. Horizontal `facing` places the model front toward the player and rotates its selection/collision shape with the same NORTH origin. Model front=-Z/NORTH, wall-side flange=+Z/SOUTH. No wall-support requirement.
- Source-space extrema: X=-4.863948..4.863948, Y=0..15.75, Z=-6.598100..8 units. After centering in the Minecraft block, OBJ bounds are X=0.196003..0.803997, Y=0..0.984375, Z=0.087619..1.0. Thus occupancy is **1×1×1**; no upper half.
- Static rendering uses Forge's OBJ geometry loader, because the source has many non-cardinal and multiple-axis cube rotations that ordinary vanilla element rotation cannot represent. Blockbench's native exporter creates `models/block/commercial_flushometer_toilet.obj`; `commercial_flushometer_toilet.mtl` resolves the 128×128 atlas at `textures/block/commercial_flushometer_toilet.png`. The item model shares the block geometry. Runtime resources, not editable source geometry, are under `src/main/resources/assets/apocalypse_firstlight/`.
- North shape uses 12 simplified boxes: base, pedestal, ceramic under-bowl support, four perimeter sections preserving the open bowl mouth, rear ceramic connector, pipe, valve body and wall fitting. It is not a full-cube collision or outline. The same shape is used for collision and selection, rotated through `HorizontalShapeUtils`.
- Strength 2.5, resistance 4.0, stone sound. `requiresCorrectToolForDrops()`, `minecraft:mineable/pickaxe`, `minecraft:needs_iron_tool`; iron, diamond and netherite pickaxes should drop exactly one self item, while empty hand, wood and stone tools should not. One self-drop loot table, no Fortune or Silk Touch variant.
- No Worldgen/building NBT changes. No separate upper-half model, block entity or functional content.

The source had briefly been saved with two dry-cavity elements absent. On explicit user approval, `dry_shell_2_18` and `closed_ceramic_bottom` were restored from the approved construction parameters before export; the restored 203-cube source passed the dry-shell verifier. The 201-cube checkpoint is retained as `src/main/blockbench/previews/commercial_flushometer_toilet_before_dry_closure_restore.bbmodel`.

## Current source

- Source: `src/main/blockbench/commercial_flushometer_toilet.bbmodel`.
- Texture: `src/main/blockbench/textures/commercial_flushometer_toilet.png`; unchanged 128×128 atlas, embedded PNG matches external PNG.
- 203 cuboids total; no mesh or animation.
- Inner assembly: `bowl_inner/closed_ceramic_shell`, 60 sloping wall cuboids across three transitions and one `closed_ceramic_bottom`.
- Every inner cube has strictly positive X/Y/Z dimensions and six texture-bound faces. No zero-thickness internal planes.
- Wall thickness: 0.30 unit. Wall lengths extend 0.03 unit beyond each nominal segment end to overlap adjacent transitions.
- Bottom thickness: 0.35 unit; bounds [-1.175, 5.48, -1.925] to [1.175, 5.83, 0.925].
- The opening contracts into a neutral ceramic floor, not a fluid surface. Do not restore water without a new explicit request.
- Front=-Z, wall=+Z; base Y=0. Existing rim, thin open-front seat, pedestal and flushometer retained.

## Scope and preservation

The latest dry-shell operation changes only the cavity below the rim. All non-liner elements are compared by UUID against the live checkpoint `src/main/blockbench/previews/commercial_flushometer_toilet_before_closed_dry_shell.bbmodel`: coordinates, pivots, rotations, face bindings and visibility are unchanged. The manually removed drain cap and other live removals are not restored.

Earlier work in this repair sequence replaced incomplete lower-bowl support pieces; that checkpoint is separately backed up as `commercial_flushometer_toilet_before_underbody_repair.bbmodel`. Those external changes predate the latest dry-shell freeze. No additional exterior redesign is part of the dry-shell operation.

The shell is an assembly of overlapping solid cuboids, not a welded manifold mesh. Its backing intersections remain internal construction geometry. The player-facing cavity is continuous and empty. No visible funnel/throat plumbing is added; `continuous_ceramic_throat` is the legacy name of external ceramic support, not an exposed drain pipe.

## Hierarchy

```text
commercial_toilet_root
  flushometer_assembly
    vertical_flush_pipe / valve_body / flush_handle
    wall_supply_pipe / wall_flange
  toilet_body_faceted
    bowl_rim / rim_edge_chamfers
    bowl_shell
    bowl_inner / closed_ceramic_shell
    pedestal / base / rear_connector
    continuous_ceramic_throat
  open_front_seat
```

## Verification and limits

Run `node tools/verify-commercial-toilet.mjs`, delegating to `tools/verify-commercial-toilet-dry-shell.mjs`.

Passed source checks:
- 61 solid cavity cubes; strictly positive dimensions and complete face bindings.
- 120 oriented-box intersection checks: 60 lateral joins, 40 inter-level joins, 20 bottom joins.
- All protected non-liner elements unchanged against the latest dry-shell backup.
- Texture references valid and embedded/external atlas identical.
- No water or former liner/funnel/drain-cap elements.

Native Blockbench sampled coverage: 14,256 rays from inside the isolated cavity, with all exterior geometry excluded, produced zero misses. This is sampled containment evidence, not an exhaustive proof of all camera angles, watertight mesh topology or exported runtime behavior.

Inspected cavity, isolated inner/outer shell and whole-model front/rear low-angle screenshots in Blockbench. Backing faces use deliberate cuboid intersections; the source is not a boolean-remeshed surface. Runtime rendering and flicker after export are **not verified in-world**.

`processResources`, `compileJava`, and `build` passed. The development client reached the existing test world with no logged toilet OBJ/material/texture load error. An unrelated native-gun smoke check logged a missing older P9 source path. The user was controlling the client when UI verification began, so in-world placement, four facings, shape inspection, wall fit, Survival mining/drop and visual texture checks remain pending; client startup alone does not prove them.

## Authoring, previews and recovery

Current tools:
- `tools/build-commercial-toilet-dry-shell.blockbench.js`: checkpoint-specific cavity authoring.
- `tools/check-commercial-toilet-dry-shell.blockbench.js`: isolated native ray sampling and captures.
- `tools/verify-commercial-toilet-dry-shell.mjs`: source preservation and intersection checks.
- `tools/restore-commercial-toilet-dry-closure.blockbench.js`: approved restoration of one missing inner-wall cube and the ceramic bottom.
- `tools/export-commercial-flushometer-toilet-obj.blockbench.js`: native Blockbench export from the saved production source to runtime OBJ/MTL, recentered in one block.
- `tools/capture-commercial-toilet-full-rebuild.blockbench.js`: standard views and wall preview.
- `tools/capture-commercial-toilet-underbody.blockbench.js`: six low-angle views.

Preview directory: `docs/models/previews/commercial_flushometer_toilet/`.
- Current standard views: `angle.png`, `front.png`, `side.png`, `back.png`, `top.png`, `cavity_check.png`, `wall_installation.png`.
- Current isolation views: `dry_shell_inside.png`, `dry_shell_outside.png`.
- Current audit: `dry_shell_coverage.json`.
- Current low views: `low_front.png`, `low_rear.png`, `low_left.png`, `low_right.png`, `low_front_center.png`, `low_rear_center.png`.

Recovery checkpoints are under `src/main/blockbench/previews/`. The wall preview includes reference wall/floor marked export=false and is not the production source.

The old wet 198-cube concept/liner version, exposed dry-drain version, concentric-course construction and their older verification outputs are obsolete. Older scripts and previews are historical records, not current acceptance evidence. Do not rerun checkpoint-specific or obsolete generators on the finished source.

BR51 was used only as a read-only faceted construction reference; no weapon geometry, bones or UVs were copied. The latest interior retains the existing directional faceting rather than replacing the asset with a cylinder or concentric stacked-ring construction.
