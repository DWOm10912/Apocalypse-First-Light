# Small City Building Authoring Framework V1

## Purpose / status

Development asset production only: reference → build in a reserved development plot → user reviews → iterate draft → explicit user approval → validate/export standard Minecraft Structure NBT plus JSON. **Export does not register a city spawn pool.**

Implemented: gated authoring commands, bounded session reservation, finite particle guides, validation/export, explicit resume, and source import script. The separately authored [office_midrise_01 glass tower](office_midrise_01_authoring.md#current-formal-asset--2026-09-10) is now exported/imported and four-rotation tested as a source asset, **not registered in a city pool**. No runtime AI, city/road/lot/zoning generator, loot system, damage pass or Rural migration. Existing Rural placement/generation is untouched.

Development integration: [Minecraft Authoring MCP Bridge](../dev/minecraft_authoring_mcp_v1.md) provides optional loopback inspection and WorldEdit edits locked to these reservations; [Reference Map Importer](../dev/reference_map_importer_v1.md) uses a separate user-selected reference area and may not overlap an active draft. Neither tool exports automatically. The common framework still has no WorldEdit dependency. MCP `authoring_clear` uses reversible WorldEdit history; manual `/afl_author clear` below remains the token-confirmed, non-undoable framework operation.

MC 1.20.1 / Forge 47.4.22 / Java 17. Core uses Vanilla `StructureTemplate.fillFromWorld/save`, block states and BE data; no private structure format or WorldEdit API dependency. Test reload uses `StructureTemplateManager.readStructure`, Vanilla `StructurePlaceSettings`, `getBoundingBox`, `placeInWorld`, `Rotation` and a deterministic seed, matching the existing Rural template workflow. Rural terrain-adaptation helpers intentionally are **not invoked**: creating an authoring plot must not flatten terrain or overwrite anything.

## Enable / safety

Server config: `<world>/serverconfig/apocalypse_firstlight-authoring.toml`:

```toml
buildingAuthoringEnabled = false
```

For an explicitly chosen development world set true (restart/reload server config as appropriate). Both the command root and execution require enabled config **and** a player who is Creative **or** permission level >=2. Console/non-player execution is not supported. Dedicated servers default disabled; no client-class reference in the authoring package. Do not enable in an ordinary survival world.

- No permanent chunk tickets or automatic terrain generation for plots. All plot chunks must already be loaded before scanning/clearing/exporting.
- Creation reserves empty air only, rejecting blocks/entities, active session overlaps, unloaded chunks, world-border or height violations. It writes no building/foundation/debug blocks.
- Width/depth 1–128; height 2–192; up to 3,145,728 blocks. Large one-shot scans/exports may pause the development server; never scan a volume every tick/frame.
- Clear needs a session-specific confirmation token valid for 30 seconds. It affects only the capture volume, removes inventories without drops, and is not automatically undoable. Back up valuable work first. Players/entities must be outside it.
- No automatic export or automatic project import. No export overwrite command; archive old exports manually or use a new ID.
- Session reservations are per-player, server-memory only; logout/server stop releases the reservation but **does not erase blocks**. Record `info` coordinates; use `resume` to select the retained draft, then reconfigure its category/zones. Cancelling also leaves blocks intact.
- Multiple sessions cannot overlap. No session enables editing beyond ordinary game/build permissions; this tool does not implement protection claims.

## Coordinate convention

| Field | Convention |
| --- | --- |
| Front | SOUTH (+Z), main door/street façade |
| Origin | MIN_X / MIN_Y / MIN_Z of capture bounding box |
| +X | Across building width |
| +Y | Up |
| +Z | Toward main front/street |
| Size order in commands | width, depth, height |
| NBT `size` | width, height, depth (Vanilla X/Y/Z) |
| `surface_offset_y` | Road/surface plane Y minus NBT minimum Y; default 1 |

Local Y=0 is the embedded foundation/base; local Y=1 is the default surface/sidewalk/entrance level. Never infer surface height from the lowest occupied block. For deeper foundations explicitly provide the offset (0..height-1). A future placement uses `captureOriginY = roadSurfaceY - surface_offset_y`.

Rotation of SOUTH: NONE→SOUTH, CLOCKWISE_90→WEST, CLOCKWISE_180→NORTH, COUNTERCLOCKWISE_90→EAST. Use Vanilla rotation and transformed bounds; width/depth swap for quarter turns. No new placement system is implemented here.

## Commands

Optional development-only asset scaffold: [legacy office_midrise_01 PASS1](office_midrise_01_authoring.md#legacy-six-floor-draft-checkpoint-preserved) adds `/afl_author build office_midrise_01` in non-production runClient only. It requires the enabled framework and a matching empty active plot, builds only the old six-floor review draft, and never validates/exports/registers a pool. The builder is excluded from the release jar. It does **not** reconstruct the separately authored glass tower now exported under the same formal ID; do not run its build/upgrade commands against that tower.

All commands start with `/afl_author`:

| Command | Behavior |
| --- | --- |
| `create <id> <width> <depth> <height> [surface_offset_y]` | Reserve an empty plot: origin X=floor((playerX+32)/16)*16, Z=floor(playerZ/16)*16, Y=player block Y. No terrain edits. Failure asks user to move to empty loaded development space |
| `resume <id> <originX> <originY> <originZ> <width> <depth> <height> <surface_offset_y>` | Explicitly reserve existing draft bounds without modifying blocks; validates dimensions/loading/overlap, resets metadata to defaults; run configure afterwards |
| `configure <CATEGORY> <ZONES> <road_facing> <damage_compatible>` | Configure enum metadata; multiple zones must be quoted, e.g. `"CORE,MIXED"`. Invalidates prior validation |
| `bounds` | Toggle guides; enabling lasts 60 seconds, no permanent particles |
| `info` | ID, origin, size, front, surface offset, state, block count, category/zones. On-demand audit observes edits |
| `clear` | Show exact MIN/MAX bounds and a confirmation token; makes no edits |
| `clear <token>` | Within 30s, clear only current capture bounds, no drops; token consumed |
| `validate` | On-demand full validation; PASS→VALIDATED, failure→DRAFT |
| `export` | Explicit approval action; immediately revalidates, writes NBT+JSON, state EXPORTED |
| `cancel` | End reservation, no export and no block deletion |

Metadata defaults are FILLER / EDGE, road-facing=true, damage-compatible=true, loot-ready=false. Configure each real building before final approval. ID must match `[a-z][a-z0-9_]{0,63}` (flat filename, max 64 characters); path separators/namespaces are not allowed in building_id.

## Plot guides / state

Bounds are finite-lifetime Vanilla dust particles sent only to the session owner, once per second, fixed 9 samples per edge (not per-block). Red=origin; white=bounds; light gray=surface perimeter; yellow=+Z front ray. The guides write no blocks/entities/NBT. No concrete/wool debug markers are generated. Blocks intentionally used as architectural concrete/wool are not indiscriminately prohibited.

State: create→EMPTY; building/configuration→DRAFT; successful validate→VALIDATED; export→EXPORTED. Normal place/break events invalidate immediately; edits by external tools, commands or block entities are detected by on-demand `info`/`validate`/`export`, not continuous volume polling. Export never trusts a stale validation: it always captures and validates again synchronously. Validation fingerprints include standard captured NBT and metadata. Resume starts DRAFT. Entity appearance blocks export even if blocks are unchanged.

## Building metadata

Export `<gameDir>/afl_authoring_exports/<id>.nbt` and `<id>.json` on the server filesystem (integrated server: client game directory; dedicated: server game directory). Outputs are staged before publishing, no replacement of an existing pair. IO failure rolls back newly published first file; abrupt process termination can still leave an incomplete pair requiring manual cleanup. Never writes inside a mod jar.

```json
{
  "id": "office_midrise_01",
  "structure": "apocalypse_firstlight:office_midrise_01",
  "category": "HIGHRISE_OFFICE",
  "footprint": {"width": 35, "depth": 39},
  "height": 86,
  "front": "SOUTH",
  "surface_offset_y": 1,
  "allowed_rotations": ["NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"],
  "road_facing": true,
  "city_zones": ["CORE", "MIXED"],
  "damage_compatible": false,
  "loot_ready": false,
  "authoring_version": 1
}
```

This example matches the exported glass-tower asset `office_midrise_01` (2026-09-10), not its historical six-floor draft. Its four actual placement/rotation checks are recorded in the asset document. V1 always advertises all four rotations even before testing; other assets still require their own architectural/front and block-state checks. Metadata presence alone is not pool registration.

Categories (enum): HIGHRISE_OFFICE, HIGHRISE_APARTMENT, MIDRISE_OFFICE, MIDRISE_APARTMENT, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, WAREHOUSE, UTILITY, FILLER, SPECIAL_POI.

Zones (enum): CORE, MIXED, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, EDGE.

Examples: highrise office→CORE; midrise office→CORE/MIXED; convenience store→COMMERCIAL/MIXED/EDGE; gas station→EDGE/COMMERCIAL; suburban house→RESIDENTIAL/EDGE. These examples do not add pool entries.

## Validation / loot / damage boundary

Checks: legal ID and complete typed metadata, bounded dimensions, SOUTH contract, valid surface offset, accessible world bounds/loaded chunks, >=8 non-air blocks, no entities (including players/items/mobs), no command/chain/repeating command block, structure block, jigsaw, structure void, barrier, light block, bedrock, sponge or wet sponge placeholder.

Container validation rejects serialized LootTable tags, serialized item stacks, and nonempty Forge item-handler slots. Empty vanilla chest/barrel BE data is retained. Arbitrary third-party private storage formats are not guaranteed detectable; do not author with unreviewed custom storage. No temporary dungeon/village/AFL loot. Prefer decorative cabinets/shelves over misleading containers. `loot_ready=false` is fixed.

No generated debug block exists to leak into capture. Manually placed authoring placeholders must be removed; concrete/wool intended as architecture are allowed, so validation cannot infer artistic intent. Inspection still required for door/window traversal, stair widths, headroom, collision, floor continuity and roof completeness; no automatic aesthetic/structural proof.

Validation also does not prove attachment survival after placement. The `office_midrise_01` first test exposed unsupported sink buttons that passed capture validation; corrected backing walls and a fresh export were verified in all four live rotations. Compare actual placed states after normal neighbor updates, not only serialized NBT or transform math.

Exports are **INTACT BASE STRUCTURE**. No random damage/loot processing. Future FOOD/MEDICAL/TOOL/INDUSTRIAL/WEAPON/DOCUMENT/GENERAL loot markers and deterministic damage pass are documentation-only extension concepts, not live features. Hero damaged variants would be distinct explicitly authored IDs later.

## Asset production passes

1. **MASSING**: footprint, height/floors, silhouette, entry, setbacks, roof. Stop for user approval before detailed work.
2. **EXTERIOR**: façade/window rhythm, frames, awnings, original sign geometry, roof equipment/vents, fire escape/service entrance.
3. **INTERIOR**: NONE=no enterable interior; SHELL=enterable shell+stair core; BASIC=partitions/corridors/stairs/basic furniture (default); FULL=complete interior decoration. Highrises can use SHELL/BASIC.
4. **VALIDATION**: player scale, door/window/stair access, headroom, collision, roofs/floor continuity, bounds. Recommended clear floor height 3–4+ blocks; corridors/stairs >=2 blocks where feasible.
5. **EXPORT**: only after the user explicitly approves. No approval→continue current draft, no NBT or pool entry.

Visual direction: American brick apartments, concrete/glass-steel offices, older commercial blocks, storefronts, warehouses, motels, service stations, parking/municipal buildings. Use architectural types, not copied brands/logos or one-to-one copyrighted game buildings.

## Source import

```text
python scripts/import_authored_building.py <gameDir>/afl_authoring_exports/<id>.nbt
python scripts/import_authored_building.py <export.nbt> --project <projectRoot> [--overwrite]
```

Default project root derives from script location. Checks companion JSON, schema/enums/ID, standard compressed NBT root/size/entities, dimensions and safe destination paths. Outputs:

- `src/main/resources/data/apocalypse_firstlight/structures/<id>.nbt`
- `src/main/resources/data/apocalypse_firstlight/small_city/buildings/<id>.json`

No overwrite unless `--overwrite`; stages/backs up both files for exception rollback. Review changes before committing. Import is not runtime validation or worldgen registration. Never automatically import the test box into project resources.

## Single-building prompt template

```text
BUILDING ID: office_example_01
CATEGORY: MIDRISE_OFFICE
ZONE: CORE / MIXED
TARGET FOOTPRINT: 28 x 34
TARGET HEIGHT: 48
FRONT: SOUTH
SURFACE OFFSET: 1
ROAD FACING: true
INTERIOR LEVEL: BASIC
STYLE: American late-20th-century downtown office building
REFERENCE IMAGES: supplied by user
EXTERIOR REQUIREMENTS: ...
INTERIOR REQUIREMENTS: ...
SPECIAL REQUIREMENTS: ...
FORBIDDEN: ...
AUTHORING WORKFLOW: Use AFL Small City Building Authoring Framework V1.
IMPORTANT: Stop after MASSING for approval. Do not validate/export until user approves.
Export does not authorize Small City Building Pool Integration.
```

## Example workflow

Illustrative new asset only (not the exported tower or legacy builder): in a backed-up empty, loaded development area, enable config and enter Creative:

```text
/afl_author create office_example_01 28 34 48 1
/afl_author configure MIDRISE_OFFICE "CORE,MIXED" true true
/afl_author info
/afl_author bounds
```

Build PASS1 → user inspection/approval → PASS2/3/4 → user final approval. Stand **outside** capture bounds:

```text
/afl_author validate
/afl_author export
/afl_author cancel
```

Use import script only when approved. Future building-pool integration is a separate task. If leaving before export, record origin/size and later `resume`; no drafts are automatically exported.

## Implementation / tests

Production: `src/main/java/com/antaurora/apofirstlight/authoring/` (BuildingMetadata, BuildingAuthoringConfig, BuildingAuthoringSession, BuildingAuthoringService, BuildingAuthoringCommands); config registration only in `ApocalypseFirstLight.java`. No new block/item registry entry, therefore no new mining tool/tier/drop behavior to audit.

Tests: `src/dev/java/com/antaurora/apofirstlight/dev/BuildingAuthoringGameTests.java`, `src/dev/authoring-gametest.init.gradle`, `scripts/tests/test_import_authored_building.py`. Dev test class is excluded from release jar by existing `dev/**` rule.

Do not run Gradle compilation, clean, resource processing or deployment while a Minecraft client is loading this checkout's build outputs. The standalone Python importer tests use temporary directories and can run without touching those loaded outputs.

```text
gradlew.bat -I src/dev/authoring-gametest.init.gradle runGameTestServer --offline -PaflWithoutTacz
python scripts/tests/test_import_authored_building.py
gradlew.bat build --offline
```

Test fixture `authoring_test_box`: 16×20×12, foundation, wall, SOUTH door, glass window, second floor, empty barrel. Only in isolated generated GameTest world/export directory; never a shipped building/pool asset.

Historical framework verification: final headless workflow PASS, 1/1 required GameTest (`build/authoring-test-final.log`, 1m22s), covering commands, permission/default-off, token clear/outside sentinel, enum metadata, dirty audit, debug/loot/item/entity rejection, export/no-overwrite, manager reload, all rotations/front/surface, resume, and particle submission without NBT contamination. Initial test exposed comma parsing in zones; corrected to a quoted string and reran successfully. Import script 6/6 tests PASS and real exported NBT import to temporary project PASS. That framework checkpoint did not include graphical runClient testing or visual approval of a real building.

Later asset verification (2026-09-10): `office_midrise_01` source capture/validate/export/import, all four live placements with world-state readback and sampled screenshots PASS; importer unit tests rerun **6/6 PASS**. See the asset acceptance record for limits and cleanup. This does not retroactively verify particle-guide appearance, exhaustive player navigation or named-shader behavior.

Historical framework release build: PASS, `gradlew.bat build --offline` (`build/authoring-build.log`), 12 seconds. Existing projection math regression also PASS (9,078 cases). Artifact `build/libs/apocalypse_firstlight-1.0.0.jar`. No deployment to the user's launcher and no source import of the test fixture. No new Gradle build/deployment was run for the 2026-09-10 tower export while the client was running; formal source resources are not a claim of a newly packaged jar.
