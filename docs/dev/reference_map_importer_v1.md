# AFL Reference Map Importer V1

## Purpose / current status

Offline Java world ZIP/folder → bounded Anvil block extraction → actual target registry audit → reviewed mappings → `REFERENCE_ONLY` intermediate JSON → WorldEdit's own Sponge serializer / scoped reference paste. No whole-world downgrade, no chunk/level.dat writes, no assets added to release resources or city pools. Implementation is development tooling, not gameplay. Real sample scan/extraction/audit is complete; live client paste/screenshot acceptance remains pending.

Python code: `tools/afl_reference_map_importer/` (Python3.10+, standard library only). Bridge: `src/dev/java/com/antaurora/apofirstlight/dev/authoring/bridge/ReferenceAdapter.java`, `ReferenceAreaCommands.java`, `RegistrySnapshot.java`. Node integration: `tools/afl_minecraft_mcp/reference_client.mjs`.

## Inputs and data safety

- CLI accepts a user-chosen Java world ZIP or folder. MCP accepts only numbered sources explicitly supplied via `--reference-source` at host launch, never arbitrary path browsing.
- ZIP checks all member paths before extraction: traversal, absolute/drive/backslash paths, symlinks, encrypted members, case-insensitive duplicates, extraction count/size/ratio limits. Windows `ZipInfo.orig_filename` is checked before its normalized filename can hide backslashes.
- Only `level.dat` and `region/*.mca` data are staged under `build/reference_imports/sources/<uuid>/`. Scripts, datapacks, executable files and playerdata are not extracted/executed. Folder sources are read-only; symlinks rejected.
- ZIP compressed limit512MiB; expanded total1GiB; per-member128MiB; max20,000 entries; >1MiB members with ratio>1000 rejected. NBT decompression16MiB, arrays2,000,000 entries, nesting64.
- Never open source world in target Minecraft, copy source `.mca` into an AFL save, spoof DataVersion, or import biomes/POI/ticks/lighting/entities/world metadata.

## Scan and candidate choice

From project root:

```powershell
python -m tools.afl_reference_map_importer scan 'E:\Download\Modern Office Building V.zip' --radius 128 --y-min 0 --y-max 160
```

Reports world name/DataVersion/source version/spawn/last played/WorldGenSettings (information only), available Vanilla dimension directories, region count, actual chunk DataVersions and candidates. Radius1..256; Y -64..319. Supports `--dimension overworld|the_nether|the_end`; custom dimensions unsupported in V1. Scanner is region-by-region, not an in-memory whole-world model. ZIP SHA256 is recorded; summary cache reuse is not implemented yet (each scan is fresh).

Discovery connects occupied16×16 chunk cells with >=16 strong artificial blocks, excludes candidates with <128 artificial blocks or height<5, and reports artificial palette/histogram/density. It is explicitly **LOW_HEURISTIC_REQUIRES_REVIEW**. Neighboring buildings/landscaping may merge. A single candidate still requires review. Multiple candidates never trigger automatic selection/paste.

Supported chunk schema: modern root `sections`, `xPos/zPos`, section `Y`, `block_states.palette/data`, padded long packing (minimum4 bits). Light-only boundary sections accepted explicitly. Gzip/zlib/uncompressed region chunks supported. Older `Level/Sections`, LZ4 compression4, external `.mcc`, malformed packing or missing selected chunks fail with source context; they are not silently skipped.

## Explicit extraction / ground policy

```powershell
python -m tools.afl_reference_map_importer extract 'E:\Download\Modern Office Building V.zip' --bounds -23 3 3 15 118 43 --reference-id modern_office_building_v_reference
```

Inclusive absolute bounds X1 Y1 Z1 X2 Y2 Z2. Existing output IDs are never overwritten. Output coordinates are local0-based; palette retains full source blockstate names/properties. Air is implicit. Max extraction2,000,000 cells; bridge paste max250,000, so choose smaller bounds if needed. No automatic large-batch import in V1.

Ground labels `BUILDING_ONLY`, `BUILDING_PLUS_PAD` (default), `FULL_SELECTION` record the user's chosen bounds. Padding must be explicitly included in bounds; the tool never guesses a foundation or deletes every natural material. Candidate bounds are not extraction bounds until approved. Discovery/extraction do not copy anything into Minecraft.

## Compatibility and mappings

Get `target_registry_1_20_1.json` using MCP `export_target_registry`, based on the live `BuiltInRegistries.BLOCK` IDs/properties/possible values and BE presence. A registry snapshot from a headless AFL1.20.1 instance is also valid for offline audit, but refresh against the intended client if mod lists differ.

```powershell
python -m tools.afl_reference_map_importer audit build/reference_imports/modern_office_building_v_reference/reference_structure.json --registry run/afl_authoring_bridge/target_registry_1_20_1.json --mapping tools/afl_reference_map_importer/mappings/mc_1_21_4_to_1_20_1.json --output build/reference_imports/modern_office_building_v_reference/audit-reviewed.json
python -m tools.afl_reference_map_importer prepare build/reference_imports/modern_office_building_v_reference/reference_structure.json --registry run/afl_authoring_bridge/target_registry_1_20_1.json --mapping tools/afl_reference_map_importer/mappings/mc_1_21_4_to_1_20_1.json --output build/reference_imports/modern_office_building_v_reference/prepared-reviewed.json
```

| Result | Meaning |
| --- | --- |
| COMPATIBLE_EXACT | Target ID and every supplied property/value accepted |
| COMPATIBLE_PROPERTY_ADJUST | Explicit approved same-ID property adjustment |
| REPLACEMENT_APPROVED | Explicit approved different target ID |
| REPLACEMENT_REQUIRED | Missing target ID or unsafe source geometry |
| UNKNOWN | Property cannot be safely expressed without a reviewed rule |

Unknown never becomes air. Mapping requires `approved:true` **and** a reason, with `target`, optional `drop_properties`, optional `properties`. Final targets are validated again. No model-generated speculative replacement is auto-approved. New cases stop for review. MCP V1 mapping edits are CLI/reviewed-file workflow, not an arbitrary mapping-path tool.

Safety audit is separate from version presence: command/structure/jigsaw/barrier/TNT/fire/portal/piston/sculk sensor/shrieker/spawner geometry requires inert reviewed replacement even when its registry ID exists. Prepare fails unresolved reports. Bridge additionally re-parses all target states with actual WorldEdit and refuses unsafe geometry.

## Block entities, text, provenance

`SAFE_ONLY` is implemented conservatively: empty block geometry only; **all source block-entity NBT is stripped**. Containers become empty, sign text and banner patterns are not retained in V1. No inventory, loot, commands, books, custom names, or any entities imported. This loses some decorative detail intentionally; it does not pretend to convert arbitrary BE schemas.

Original sign data is retained only as explicitly untrusted provenance report data (max128 signs), never executed or interpreted as instructions. `reference_manifest.json` retains source filename/path/hash, user-supplied optional URL/author, version, timestamp, bounds, `reference_only=true`, `redistribution_allowed=unknown`. The prepared `.manifest.json` adds actual replacements and target registry source. No license inference or automatic redistribution.

## MCP tools / stage / schematic / paste

Setup: [MCP bridge activation](minecraft_authoring_mcp_v1.md). Tools: `reference_import_status`, `reference_map_scan`, `reference_candidates`, `reference_extract`, `reference_compatibility_report`, `reference_prepare_paste`, `reference_write_schematic`, `reference_paste`, `reference_remove`.

`reference_prepare_paste` stages a reviewed artifact as `<gameDir>/afl_reference_import/prepared/<reference_id>.json`, refusing overwrite. For CLI-prepared artifacts, manually copy the approved JSON there under that exact ID; no arbitrary source path is accepted by the Java endpoint.

`reference_write_schematic` calls **WorldEdit7.2.15 `BuiltInClipboardFormat.SPONGE_SCHEMATIC` writer**, not a guessed schema implementation. Output `<gameDir>/config/worldedit/schematics/afl_references/<id>.schem`, no overwrite. Nothing is written to `src/main/resources/data/.../structures/`.

To paste, the user first chooses empty, loaded reference space and explicitly runs:

```text
/afl_reference_area set <x> <y> <z> <width> <height> <depth>
```

This only reserves an in-memory reference area, not a block edit. It rejects active Authoring Session overlap, occupied cells, entities, unloaded chunks and world bounds. For this sample reserve at least39×116×41 (41×116×41 accommodates90° rotation). Do **not** blindly use example coordinates in an existing world. Target Y+115 must remain below320. Stand outside the area.

Then explicitly `reference_paste(reference_id, dry_run=true)` and, after reviewing, `reference_paste(reference_id, rotation=0|90|180|270)`. Paste minimum is the reference area's minimum; no inferred SOUTH orientation or mirror. All entities/biomes excluded. Native WorldEdit rotation handles blockstate orientation. Paste operation ID, bounds and rotation recorded under `afl_reference_import/paste_history/`.

Only one live reference paste history is retained per bridge. `reference_remove` undoes it, refusing a changed area/world, manual state/NBT changes or entities in the area. History does not survive game close; never fall back to fill-air in a new world. Reference clipboard is never exposed to authoring `we_copy/paste`. No automatic paste or new original-building construction follows a scan/import.

Read pasted region using inspector `target=REFERENCE_AREA`; own draft uses `AUTHORING_SESSION`; manually selected external references use `REFERENCE_SELECTION`. Capture current view, inspect slices/facades, and create a separate study only on request. This importer does not automatically create an AFL building.

## Actual sample: Modern Office Building V

| Field | Observed / approved |
| --- | --- |
| Source | `E:/Download/Modern Office Building V.zip` (3,328,778 bytes) |
| World name / source version | Modern Office Building V / 1.21.4 |
| level.dat DataVersion | 4189; individual chunks have their own recorded versions |
| Spawn | -4,4,44 |
| Overworld region files | 16 |
| Principal candidate | candidate_01; -19,4,7 → 11,118,39; 31×115×33 |
| User-approved extraction | -23,3,3 → 15,118,43; 39×116×41; 4-block horizontal pad, foundation Y3 |
| Non-air / state palette | 64,025 / 129 |
| Original registry-only exact | 64,013 blocks; this included unsafe command blocks and was **not a paste safety approval** |
| Final unchanged target blocks | 63,971 (99.9157%) |
| Approved short grass mapping | 12 short_grass → grass |
| Approved safety mapping | 42 command_block → smooth_stone; conditional/facing properties removed |
| Final property-unknown / unapproved replacement | 0 / 0 after both reviewed rules |
| Current prepared artifact | `build/reference_imports/modern_office_building_v_reference/prepared-safe.json` |
| Provenance | `prepared-safe.manifest.json` in same directory |
| Old intermediate | `prepared.json` / initial `compatibility_report.json` predate safety audit; superseded, **do not stage** |
| User-world paste | Live bridge paste completed in user-reserved area 15,-33,152 → 53,82,192; independent inspection confirmed 64,025 non-air blocks. Operation 0377fec2-89b8-424d-9943-45de6f89fe93. |

Live test caveat: paste response incorrectly reported `changed_blocks=0`; the independent world inspector confirmed 64,025 non-air blocks. Treat the response counter as a known issue, not evidence of an empty paste. Current camera capture did not show the reference building; user visual review and live removal/undo verification remain pending. The pasted reference is retained for inspection.

Real sample headless schematic/paste/undo test is separate from user-world and screenshot verification. Consult the task's final test report; do not count a generic `BUILD SUCCESSFUL` as a passed GameTest if logs contain load/test failures.

## Tests / limits / troubleshooting

`python -m unittest discover -s tools/afl_reference_map_importer/tests -v`: ZIP/folder, malicious paths, level/chunk, padded indices, negative/high Y, properties, inventory/entities stripping, exact/mapping/unknown handling. `src/dev/bridge-gametest.init.gradle` additionally tests the prepared actual sample when `prepared-safe.json` exists.

Explicit errors include ZIP_INVALID, LEVEL_DAT_MISSING, REGION_MISSING, CHUNK_DECODE_FAILED, UNSUPPORTED_CHUNK_SCHEMA, NO_STRUCTURE_CANDIDATE/MULTIPLE_CANDIDATES, TARGET_REGISTRY_UNAVAILABLE, REPLACEMENT_REVIEW_REQUIRED, MCP_NOT_CONNECTED, REFERENCE_AREA_NOT_SET and PASTE_SCOPE_REJECTED. Read the specific error; never solve a decode failure by opening the high-version save in1.20.1.

Not implemented: custom dimensions/old chunk schemas/LZ4/external chunks; cached scans; auto terrain separation; arbitrary pattern mappings; automatic batched huge paste; source entities; text/banner NBT conversion. Reference tools are not a general filesystem browser or a third-party asset licensing/import workflow.
