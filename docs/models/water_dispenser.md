# Water Dispenser — static environment block V1.2

Status: editable art, runtime assets and static block registration implemented on 2026-09-10. `gradlew build` passed, and a development-client startup reached resource reload without a water-dispenser model or texture error. In-world placement, transparency, mining and world-reload behavior still require a dedicated check.

| Property | Current implementation |
| --- | --- |
| Registry ID | `apocalypse_firstlight:water_dispenser` |
| Names | 饮水机 / Water Dispenser |
| Size | 1×1×2; real LOWER/UPPER occupancy |
| Facing | Horizontal; front faces the placing player |
| Function | Static environment decoration only |
| Tool / tier | Pickaxe, iron tier or higher; `requiresCorrectToolForDrops()` |
| Block properties | Strength 3.0, blast resistance 5.0, metal sound, non-occluding |
| Drop | One item from the LOWER half; breaking UPPER removes LOWER and returns one item only with the correct tool |
| Rendering | Forge composite baked model: 129 opaque elements on `solid`, 31 bottle elements on `translucent`; ambient occlusion disabled; no BlockEntity or renderer |
| Selection / collision | Thermal-generator pattern: one model-fitted closed targeting envelope per half, with separate model-derived collision shapes (three lower and seven upper envelopes) |
| Creative tab | AFL Blocks |
| Handheld transforms | First person scale 0.30; third person scale 0.25; both hands synchronized from the Blockbench source by the composite export helper |

## Assets

| Artifact | Path |
| --- | --- |
| Editable Blockbench source | `src/main/blockbench/water_dispenser.bbmodel` |
| Runtime block model | `src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser.json` |
| Empty upper-half model | `src/main/resources/assets/apocalypse_firstlight/models/block/water_dispenser_empty.json` |
| Item model | `src/main/resources/assets/apocalypse_firstlight/models/item/water_dispenser.json` |
| Texture | `src/main/resources/assets/apocalypse_firstlight/textures/block/water_dispenser.png` |
| Blockstate | `src/main/resources/assets/apocalypse_firstlight/blockstates/water_dispenser.json` |
| Loot table | `src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/water_dispenser.json` |

The source and runtime model contain 160 cubes in 22 groups. Geometry and per-face UVs are identical between both exports, and the external PNG is byte-identical to the source's embedded texture.

## Bottle transparency and fluid cavity

V1.2 replaces the overlapping translucent barrel strips with 20 non-overlapping wall pieces, two complete thin sealed caps, eight hollow neck/collar walls and one faded label. The full top and bottom caps close the former corner holes while the central barrel remains empty for a later, separate fluid layer.

Automated geometry audit results:

| Check | Result |
| --- | --- |
| Positive-volume overlaps in bottle geometry | 0 |
| Contacts with both coplanar faces still rendered | 0 |
| Conservative fluid-box intersections | 0 |
| Fluid-safe box | min `(4.2, 21.55, 4.2)`, max `(11.8, 31.15, 11.8)` model units |

This removes the asset-level Z-fighting cause. Minecraft translucent sorting with a future dynamic fluid layer still needs an in-game regression test. The pre-V1.2 assets are backed up under `build/asset_checks/water_dispenser/pre_nonoverlap/`.

The block follows the thermal generator's two-part correction. Targeting and interaction use one closed, model-fitted outer envelope per block half, so hovering cannot expose internal cabinet, connector or bottle seams. Physical collision remains separate and follows the plinth, cabinet, front projection, bottle socket, body, shoulders and cap. Rendering also uses Forge's composite loader: opaque cabinet geometry writes to the solid layer before the bottle's translucent geometry, instead of forcing the entire appliance through `RenderType.translucent`. The runtime models set `ambientocclusion: false`, matching the industrial-locker treatment so the translucent top cap is not darkened by baked ambient occlusion.

No GUI, BlockEntity, storage, drinking, hot/cold water, fluid capability, power, animation, interaction sound, recipe, world generation or Small City pool entry is implemented.
