# Convenience Store 01 asset (2026-09-13)

The current editable development-world building at `新的世界`, bounds
`(541, -33, -256)` through `(565, -23, -219)`, was captured through the
Small City authoring framework and imported as
`src/main/resources/data/apocalypse_firstlight/structures/convenience_store_01.nbt`.
This replaces the earlier source NBT content under the same registry ID. The
pre-replacement NBT is preserved outside disposable build outputs at
`run/afl_authoring_exports/archive/convenience_store_01_before_20260913.nbt`.

- Capture size (X/Y/Z): 25 × 11 × 38 blocks; source origin is the minimum corner.
- Front: SOUTH; `surface_offset_y`: 0.
- Captured 5,073 non-air blocks and 170 block-entity payloads; no entities.
- Metadata: `COMMERCIAL`; city zones `COMMERCIAL`, `MIXED`, `EDGE`;
  road-facing true; damage-compatible false; loot-ready false.
- AFL assets present in the exported palette include retail shelves, beverage
  coolers, chest freezers, cash registers, the commercial glass double door,
  toilets, wall-mounted sinks, water dispensers, trash cans, industrial lights,
  the 2-block commercial dumpster and its quartz-brick enclosure, and
  reinforced concrete. The dumpster's master and secondary halves are at
  relative `(1, 0, 36)` and `(2, 0, 36)`.

The authoring validator passed and the exported NBT was read back with the
expected dimensions and counts. This is a source asset import, **not** a new
Small City pool registration. Fresh placement, all four rotations, player
traversal, and visual review of the imported replacement have not been run;
the metadata's rotation list does not prove those checks.
