# Office Cubicle Partition

- Registry ID: `apocalypse_firstlight:office_cubicle_partition`
- Editable source: `src/main/blockbench/office_cubicle_partition.bbmodel`
- Texture: `src/main/resources/assets/apocalypse_firstlight/textures/block/office_cubicle_partition.png`
- Final model size: one-block footprint, two blocks tall

## Runtime behavior

The block stores four self-only horizontal connection properties: `north`, `south`, `east`, and `west`.
Static multipart models cover SINGLE, END, STRAIGHT, CORNER, T-junction, and CROSS layouts. Straight
middle sections omit end posts; corner, T, and CROSS layouts share one central post and one foot.

Collision and selection use 16 cached connection-dependent `VoxelShape` values. Placement requires a
sturdy floor and empty collision space above the block because the approved model is two blocks tall.
Every position is an independent block; there is no multiblock controller, BlockEntity, tick, renderer,
energy capability, or power-network behavior.

The partition is lightweight furniture. It deliberately has no required tool, no
`requiresCorrectToolForDrops()`, and no `minecraft:mineable/*` or `minecraft:needs_*_tool` entry.
Survival empty-hand destruction drops exactly one partition through its standard loot table.

## Verification

- Runtime resource generator/verifier: PASS (five modular models, twelve multipart rules, no positive-volume overlap in straight/arm/junction modules)
- Forge build: PASS on 2026-09-10
- Dedicated GameTest: PASS on 2026-09-10 (SINGLE/END/STRAIGHT/CORNER/T/CROSS, placement and break updates, all 16 shapes, hand drop, upper-space guard, 49-block batch)
- Development-client resource reload/model bake: PASS on 2026-09-10 (no partition model, blockstate, or texture errors)
- In-world visual acceptance: not performed because the desktop controller did not expose the native client window
