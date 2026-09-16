# Steel Railing

`apocalypse_firstlight:steel_railing` is a static industrial building block that directly uses the vanilla 1.20.1 `IronBarsBlock` implementation. It therefore retains vanilla four-way pane connections, waterlogging, collision/selection shape updates, and iron-bars sound behavior without a separate AFL connection system.

- Runtime texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/steel_railing.png`
- Blockstate: `src/main/resources/assets/apocalypse_firstlight/blockstates/steel_railing.json`
- Mining: pickaxe, Diamond tier or better (`minecraft:mineable/pickaxe` and `minecraft:needs_diamond_tool`)
- Drop: one `steel_railing` when mined with the correct tool
- No BlockEntity, ticking, GUI, animation, FE, or redstone behavior.
