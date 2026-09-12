# AFL cash register — static block V1

## Status

Implemented as `apocalypse_firstlight:cash_register`, a single-cell static countertop block and BlockItem.
It has horizontal facing, five-box collision/selection, a self-drop with the correct tool,
metal placement/break sounds, and a 16-particle destruction budget. It has no BlockEntity,
GUI, inventory, interaction, drawer animation, receipt, dedicated sound event, salvage loot,
or worldgen placement.

## Assets

- Source: `src/main/blockbench/afl_cash_register.bbmodel`
- Source texture: `src/main/blockbench/textures/afl_cash_register.png` (128 × 128)
- Runtime model: `src/main/resources/assets/apocalypse_firstlight/models/block/cash_register.json`
- Runtime texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/cash_register.png`
- Blockstate: `src/main/resources/assets/apocalypse_firstlight/blockstates/cash_register.json`
- Item model: `src/main/resources/assets/apocalypse_firstlight/models/item/cash_register.json`
- Self-drop: `src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/cash_register.json`
- Front preview: `src/main/blockbench/previews/afl_cash_register_front.png`
- Rear preview: `src/main/blockbench/previews/afl_cash_register_rear.png`
- Construction script: `tools/build-cash-register.bb.js`; requires an empty generic Blockbench project. When sending through MCP risky_eval, strip the script's comments as required by the tool.
- Export script: `node tools/export-cash-register.mjs --write` regenerates runtime model/texture;
  `--check` verifies they still match the source.

145 cubes, no meshes, one texture. All 870 cube faces reference the texture.
The source uses the generic/free format, with a separate static runtime Minecraft JSON.
The operating panel uses per-cube X rotation of -22.5 degrees around a shared pivot,
preserved by the export script.
The display stand's redundant coplanar side trim was removed, and its collar
lower edge was shifted down by 0.03 unit to avoid overlapping the screen bezel.
Front faces NORTH (-Z); bottom is Y=0. Root pivot is [8,0,8].
Overall bounds are approximately 14.05 W × 12.48 D × 11.93 H units,
or 0.878 × 0.780 × 0.746 blocks. No receipt, brand, cable or separate accessories.

## Organization and material

`cash_register_root` contains `base_body`, `front_drawer`, `keypad_panel`,
`display_mount`, `display_screen`, `side_shell_left`, `side_shell_right`,
`covered_service_module`, and `rear_details`.

Charcoal casing, restrained gray edge highlights, gray unlettered keys,
blue-black screen, and a small gray lock. The atlas has clean material swatches
with subtle edge shading, without random noise, rust or wear.

## Verification boundary

Live Blockbench front/rear previews and texture UUID coverage checked.
The model stays below the 300-cube ceiling; the suggested 180–260 range was
not padded with unnecessary cubes. Registered block hardness is 2.5, explosion
resistance 4.0, and SoundType is METAL. `requiresCorrectToolForDrops()` combines
with `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`: bare hands,
wood, and stone do not yield the block; iron, diamond, and netherite pickaxes
yield one. Creative tab: AFL Blocks. Translation: 收银机 / Cash Register.
The model starts at Y=0 and uses a sturdy top-face support. On a full-block
countertop it sits at the block boundary; partial-height countertops need a
separate fit check before use. Headless GameTest checks placement, four shape
orientations, tags, and mining drops. Client visuals and physical counter
placement remain separate checks.
