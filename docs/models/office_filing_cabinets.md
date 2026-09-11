# Office Filing Cabinets

## Current status

The office filing cabinet family is implemented as two static, horizontally facing decorative blocks:

- `apocalypse_firstlight:low_filing_cabinet`: one-block, two-drawer cabinet.
- `apocalypse_firstlight:tall_filing_cabinet`: real lower/upper two-block, four-drawer cabinet.

Both use `minecraft:mineable/pickaxe`, require `minecraft:needs_iron_tool`, and have `requiresCorrectToolForDrops()` enabled. They drop one matching BlockItem when harvested correctly. The tall cabinet's lower half owns the loot; breaking either half clears the complete structure without a second item drop.

Each cabinet collision/outline is composed from a main cabinet-body shape plus one non-overlapping shape per drawer handle. The low cabinet exposes two individually addressable handle shapes; the tall cabinet exposes four global drawer-number handle shapes, split correctly between its lower and upper block states. Both blocks also expose the body-only shape separately. These shapes reserve future per-drawer hit detection without adding interaction behavior now.

The blocks have no BlockEntity, inventory, menu, GUI, animation, redstone, FE, or loot-content behavior.

## Assets

- Editable sources: `src/main/blockbench/low_filing_cabinet.bbmodel`, `src/main/blockbench/tall_filing_cabinet.bbmodel`
- Shared texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/office_filing_cabinet.png`
- Runtime exporter: `tools/export-filing-cabinets-runtime.mjs`
- Low runtime model: `src/main/resources/assets/apocalypse_firstlight/models/block/low_filing_cabinet.json`
- Tall runtime models: `src/main/resources/assets/apocalypse_firstlight/models/block/tall_filing_cabinet_lower.json`, `tall_filing_cabinet_upper.json`

The runtime exporter validates the approved 61/105-cube sources, shared 128x128 texture, independent drawer groups, drawer pivots, and the 16-unit lower/upper split before writing assets.

## Verification

Inventory GUI framing uses rotation `[25,135,0]` for both cabinets. Low cabinet scale is `[0.6,0.6,0.6]`, translation `[0,1,0]`; tall cabinet scale is `[0.4,0.4,0.4]`, translation `[0,-3,0]`. These GUI settings are retained by the runtime exporter. The GUI framing adjustment still requires in-game visual acceptance.

- Build: `gradlew.bat compileJava processResources build --no-daemon`
- Isolated GameTest: `gradlew.bat -I src/dev/filing-cabinet-gametest.init.gradle runGameTestServer --offline -PaflWithoutTacz`

The GameTest checks iron-versus-stone harvesting, four-facing rotated shapes, actual lower/upper occupancy, blocked-upper placement, single-item breaking from either half, creative no-drop, and removal of both halves.

2026-09-10 verification: the normal `compileJava processResources build` completed successfully, and the isolated filing-cabinet GameTest passed 1/1 required test. This is headless runtime verification; graphical client rendering and inventory GUI framing still require an in-game visual check.
