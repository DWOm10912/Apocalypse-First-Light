# Commercial Wall-Mounted Sink — source asset

Status: Commercial Wall-Mounted Sink V1 is a registered, static, dry decoration block. It has no BlockEntity, animation, interaction, fluid system, or water variant.

- Source: `src/main/blockbench/commercial_wall_mounted_sink.bbmodel`
- Independent texture: `src/main/blockbench/textures/commercial_wall_mounted_sink.png` (128 × 128, embedded in source as well).
- Registry ID: `apocalypse_firstlight:commercial_wall_mounted_sink`; item in the existing Blocks/decoration creative tab. English: Commercial Wall-Mounted Sink; Chinese: 商业壁挂式洗手台.
- Runtime: Forge static OBJ/MTL under `assets/apocalypse_firstlight/models/block/`, block/item JSON, blockstate JSON, and pixel-identical `textures/block/commercial_wall_mounted_sink.png`. `tools/export-commercial-wall-sink-obj.blockbench.js` regenerates the 250-object OBJ from the saved source. OBJ is used because the approved cube geometry has multi-axis faceted rotations that ordinary block-element JSON cannot represent.
- Geometry: 250 cubes, no zero or negative axis dimensions.
- Transformed bounding size: 14.4277 wide × 11.7956 high × 10.2139 deep model units.
- Bounds: X −7.21385…7.21385; Y 6.77789…18.57350 after an internal +5 model-unit rise; Z −2.21385…8. The basin rim is at Y≈15.03. Front is −Z; wall plane is Z=8.
- Runtime OBJ bounds after X/Z recentering, relative to the placed lower cell: X 0.04913…0.95087, Y 0.42362…1.16084, Z 0.36163…1.00000. Occupancy is one horizontal cell and two vertical cells, starting in the ground-level placement cell. `HALF=LOWER` is master and alone renders the complete OBJ; `HALF=UPPER` is an invisible occupancy block using `commercial_wall_mounted_sink_upper.json` (no geometry). The upper cell must be empty/replaceable and fluid-free; the two halves remove each other when one is broken or otherwise removed. Default front is NORTH (−Z); wall side is SOUTH (+Z). Blockstate `FACING` rotates the model and both shape halves 90° for EAST, 180° for SOUTH and 270° for WEST. Placement faces the placing player. A rear wall is not required and removal of one does not break the sink.
- Shape: 13 simplified original boxes (ceramic basin floor and four surrounding sides, faucet base/spout, narrow drain tailpiece/P-trap/outlet and two wall brackets) all rise by +5 model units. The full shape is clipped at Y=16 into lower and upper VoxelShapes; the upper shape contains only the protruding faucet, not an invisible full cube. The basin opening and most of the underside stay air; occlusion shape is empty. North shapes rotate in code with `FACING`.
- Mining: hardness 2.5, resistance 4.0, `SoundType.STONE`, `requiresCorrectToolForDrops()`, `minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool`. Iron, diamond and netherite pickaxes are the intended self-drop tools; hand, wood and stone do not qualify. Breaking either half removes both and yields at most one self item under the correct Survival tool; Creative yields none. Loot is a self item with explosion survival condition; no Fortune/Silk Touch special behavior.

## Groups

`sink_root` contains `ceramic_body` (outer rim, inner basin, underside shell and rear deck), `faucet_assembly` (base, spout and single lever), `drain_assembly` (dry strainer, P trap and wall connector), and `wall_mount_support`. No floor pedestal or water geometry is present.

## Support face correction

Both `wall_bracket_*` rear faces terminate at Z=7.96 rather than Z=8. This separates them from the ceramic back face by 0.04 unit and removes the reported coplanar overlap. Bracket front faces, ceramic silhouette, faucet and pipe geometry are unchanged by this correction.

## Verification

Native Blockbench previews are under `docs/models/previews/commercial_wall_mounted_sink/` (angle, front, side, top, underside and back); the raised asset was recaptured and the angle preview inspected. The OBJ export was checked for 250 objects, 924 faces and the two-cell vertical boundary. `tools/verify-commercial-wall-sink-runtime.mjs` passes texture equality among source/embedded/runtime PNGs, MTL binding, all eight blockstate variants, the invisible upper model, loot and both mining tags, plus en_us/zh_cn key set, order, duplicates and local insertion position (same ordered keys, zero duplicates). Gradle `processResources compileJava build` passed after the rise.

`src/dev/java/com/antaurora/apofirstlight/dev/CommercialWallMountedSinkGameTests.java`, run with `src/dev/commercial-wall-sink-gametest.init.gradle`, passed 1/1 headless Forge GameTest after the rise: four actual BlockItem placements with aligned lower/upper halves, wall addition/removal, blocked upper placement, empty basin and underside collision probes, lower rim/pipe and upper faucet collision probes, plus twelve actual Survival breaks (both halves × hand, wood, stone, iron, diamond and netherite) checking peer removal and single/no drop. A prior client run entered the existing test world and showed the sink item textured in the hotbar, but that was before the height rise. In-world raised placement, four-direction visual review, wall seam, color and hand/ground item rendering remain unverified. A headless test does not establish visual fidelity.
