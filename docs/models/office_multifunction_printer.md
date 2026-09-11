# Office Multifunction Printer

## Current status

`apocalypse_firstlight:office_multifunction_printer` is implemented as a static, horizontally facing office decoration. It occupies a real lower/upper two-block column and renders the approved freestanding copier model split at the 16-unit block boundary.

The block has only `facing` and `half` states. Placement requires a sturdy floor and an empty upper position. Breaking either half clears the complete printer; the lower half owns the loot, so correct harvesting returns one item without a duplicate upper-half drop.

It has no BlockEntity, inventory, menu, GUI, animation, interaction, redstone, FE, printing, copying, salvage, or office-supply loot behavior.

## Mining and shapes

- Tool: pickaxe.
- Required tier: iron or higher.
- Tags: `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`.
- Properties: strength 3.5, blast resistance 6.0, metal sound, non-occluding.
- Lower shape: inset plinth, cabinet body, and the front output-column projection.
- Upper shape: upper cabinet, scanner deck, lid/hinges, ADF body and trays, and the forward control panel.
- Both shapes rotate with `facing` and remain inside their own block half.

## Assets

- Editable source: `src/main/blockbench/office_multifunction_printer.bbmodel`
- Runtime exporter: `tools/export-office-multifunction-printer-runtime.mjs`
- Runtime models: `src/main/resources/assets/apocalypse_firstlight/models/block/office_multifunction_printer_lower.json` and `office_multifunction_printer_upper.json`
- Item model: `src/main/resources/assets/apocalypse_firstlight/models/item/office_multifunction_printer.json`
- Texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/office_multifunction_printer.png`
- Blockstate: `src/main/resources/assets/apocalypse_firstlight/blockstates/office_multifunction_printer.json`
- Loot table: `src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/office_multifunction_printer.json`

The source contains 296 cubes and one embedded 128x128 texture. The exporter validates required future-facing groups and texture bindings, splits unrotated cubes crossing Y=16, keeps supported 22.5-degree control-panel rotations, and flattens the ten unsupported 8-degree ADF feed-tray rotations for the vanilla block-model format.

## Verification

- Runtime export synchronization (`node tools/export-office-multifunction-printer-runtime.mjs --check`): passed on 2026-09-11. The source has 296 cubes; the generated lower/upper models have 196/122 elements, and all JSON resources parse successfully.
- `gradlew.bat compileJava processResources build --no-daemon -PaflWithoutTacz`: passed on 2026-09-11.
- Dedicated headless GameTest: 1/1 required tests passed on 2026-09-11. It covers iron-tier harvesting, four-facing shape rotation, real lower/upper occupancy, one-drop cleanup from either half, no drops in Creative or with an incorrect tool, and blocked-upper placement rejection.
- Graphical client rendering and inventory framing: not tested in this implementation pass. A successful headless GameTest does not by itself verify those visual results.
