# Apocalypse: First Light — Project Handoff Snapshot

Snapshot: 2026-08-18
Repository: D:\Minecraft Modding\Apocalypse First Light
HEAD: 97050c1 (+shelf single V1)
Working tree: clean when inspected. This snapshot does not start feature work.

## Identity/build

- Apocalypse: First Light / 黎明启示录
- Mod ID: apocalypse_firstlight
- Package: com.antaurora.apofirstlight
- Minecraft 1.20.1, Forge 47.4.22, Java 17
- TaCZ 1.1.8-hotfix is the only AFL-required external framework.
- JEI/Jade are runtimeOnly development helpers; AFL has no API references or mods.toml dependencies for them.
- TaCZ Gradle: implementation fg.deobf("curse.maven:timeless-and-classics-zero-1028108:8141310")
- mods.toml: Forge/Minecraft mandatory; TaCZ mandatory, versionRange [1.1.6,), AFTER, BOTH.
- Official mappings 1.20.1. Java toolchain 17.
- Build: .\gradlew.bat build --offline --stacktrace
- Client: .\gradlew.bat runClient --offline --stacktrace
- Client Mixin settings in build.gradle: mixin.env.remapRefMap=true and refMapRemappingFile=build/createSrgToMcp/output.srg. Preserve these; they fixed TaCZ dev refmap/remapping startup issues.
- ForgeGradle repositories: Curse Maven and BlameJared Maven.

## Philosophy observable in code

AFL is a server-authoritative Overworld apocalypse foundation: restricted vanilla progression/world access, noise-driven infected perception, controlled vision/search/breach, and industrial/materialized construction with salvage. TaCZ owns firearm mechanics; AFL consumes TaCZ events and public data. The code does not formally record every broader design principle, so future agents must ask before assuming balance or roadmap decisions.

## Source map

- Core: ApocalypseFirstLight.
- Registry: AflBlocks, AflItems, AflBlockEntities, AflCreativeTabs, AflBlockSetTypes.
- Blocks: SteelDoorBlock, SteelGrateBlock, IndustrialUtilityLightBlock, IndustrialElectricalBoxBlock, IndustrialLockerBlock, MetalLockerBlock, SupermarketShelfSingleBlock.
- Block entities: IndustrialLockerBlockEntity, MetalLockerBlockEntity.
- Noise: NoiseEvent, NoiseType, NoiseSystem, GunshotNoiseResolver, BlockBreakNoiseResolver, InteractionNoiseResolver/Events, movement noise, AcousticOcclusionResolver, TaczNoiseEvents.
- Infected: InfectedEntityRules, InfectedEvents, hearing, vision, AI investigation, breach authorization/goal/rules/entry seeking.
- World: WorldAccessEvents, WorldSpawnRules, IndustrialMaterialExplosionDrops, VanillaProgressionEvents, VanillaRecipePruningEvents.
- Client: render-type, HUD and advancement-screen events.

## Registries

Blocks:
- reinforced_concrete: Block, 6/15, stone, correct tool.
- reinforced_concrete_slab/stairs: standard SlabBlock/StairBlock copying base properties.
- steel_block: Block, 7/12, metal, correct tool.
- steel_block_slab/stairs: standard variants copying Steel Block.
- steel_plate: Block, 6/10, metal, correct tool.
- steel_plate_slab/stairs: standard variants copying Steel Plate.
- steel_grate: custom waterloggable grate, 5/7, metal, no occlusion.
- steel_door: vanilla-style 1x1x2 DoorBlock, 6/10, metal.
- industrial_utility_light: wall/ceiling utility block, 3/5, light 14.
- industrial_electrical_box: wall utility block, 5/8.
- industrial_locker: IndustrialLockerBlock, 1x1x2, 5/8, 54-slot BE.
- metal_locker: MetalLockerBlock, 1x1x2, 4/6, 36-slot BE.
- supermarket_shelf_single: custom 1x1x2 visual shelf, 3.5/6, no BE.

Every block has a BlockItem. Material Items: steel_scrap and concrete_rubble. No AFL custom entities.

Creative tabs:
- Blocks, icon Steel Block: concrete group, steel block group, steel plate group, grate, door, utility/furniture.
- Items, icon Steel Scrap: Steel Scrap and Concrete Rubble.

## Materials/loot/salvage

- Reinforced Concrete: Diamond+ recovery; explosion Concrete Rubble 2–4.
- Steel Block: Diamond+; Steel Scrap 2–4.
- Steel Plate: Diamond+; Steel Scrap 1–3.
- Steel Grate: Diamond+; Steel Scrap 1–2.
- Slabs/stairs copy base properties; double slabs use count 2.
- Industrial Locker: explosion Steel Scrap 0–3; contents once.
- Metal Locker: iron/diamond/netherite pickaxe recovery tag; explosion vanilla Iron Nuggets 2–6; contents once.
- Utility Light: existing Steel Scrap chance path.
- Electrical Box: Steel Scrap 0–3.
- Steel Door: Steel Scrap 1–2, canonicalized for its two halves.
- Concrete Rubble has a recipe back to reinforced concrete.
- Plastic/petroleum systems are not implemented.

## Central explosion system

Only world/IndustrialMaterialExplosionDrops handles ExplosionEvent.Detonate, server side. It uses per-event position sets, upper-to-lower normalization, markExplosion/clearExplosionMarks and BlockEntity dropContentsOnce.

Metal Locker has two salvage branches:
1. direct hit on either half -> normalize lower -> dedupe -> mark -> contents -> Items.IRON_NUGGET 2–6;
2. support destruction -> inspect lower above support -> dedupe -> mark -> contents -> Items.IRON_NUGGET 2–6.

Industrial Locker has the same lifecycle but Steel Scrap 0–3. Other assets must not be changed accidentally.

Known bugs:
- Metal Locker once dropped two intact items because playerWillDestroy popResource was combined with a non-empty loot table. Fixed by letting the loot table handle normal item recovery.
- One Metal Locker explosion branch once remained Steel Scrap. Both paths now use Iron Nuggets.
- Never add a second explosion handler.

## Double-height templates

Industrial Locker is the mature container template:
- horizontal FACING + DoubleBlockHalf;
- floor-only placement; context clicked position is LOWER and upper is above;
- front faces player;
- LOWER owns BE; UPPER redirects;
- support/partner checks and whole-structure cleanup;
- full lower model, empty upper model;
- empty upper particle texture;
- AO false and namespace-qualified textures;
- prior raw 32x64 UV problem was fixed by UV normalization.

Metal Locker mirrors this lifecycle with separate classes and 36-slot menu:
new ChestMenu(MenuType.GENERIC_9x4, id, inventory, this, 4) is required because the fourRows helper lacks a container overload. Its supplied model is 32-high, AO false, has cleaned namespace/#missing references and particle-safe empty upper model. Full live GUI/explosion regression is not recorded.

Supermarket Shelf Single is the no-container template:
- horizontal FACING + HALF; lower and upper partner lifecycle;
- visual model spans Y 0–32, four shelf levels, 128x128 texture, texture_size [128,128], AO false;
- no BE, inventory, display slots or GUI;
- custom lower/upper VoxelShapes rotated from NORTH;
- floor-only placement, faces player, partner removal suppresses drops.
Future no-GUI fixed display slots/lower-only BE/click-region separation are plans only.

## Noise system

NoiseType: GUNSHOT, BLOCK_BREAK, FOOTSTEP, LANDING, INTERACTION.
NoiseEvent: source, position, type, game time, optional source ID, radius; negative radius rejected.
NoiseSystem logs DEBUG and dispatches server-player events to hearing.

TaCZ:
- GunShootEvent -> IGun#getGunId;
- reference radius: m1911 64, ump45 72, ak47 96, kar98 112, m107 160;
- unknown common-gun type fallback and default 80;
- public muzzle silence modifier addend; final radius base + addend, minimum 8.

Movement: sneak 2, walk 4, sprint 8; landing 5/8/12. Block break is tag-category based. Interaction has toggle/container/place resolvers. Acoustic occlusion samples every 0.5 blocks using AFL dampening tags. No separate intensity, weather attenuation, smell or explosion-noise source is implemented.

## Infected AI/perception

Current infected is exactly vanilla EntityType.ZOMBIE; no custom infected entity.

Implemented:
- hearing with persisted last-heard state, wool occlusion, investigate/search;
- local entry seeking and post-breach continuation;
- vision: max 32 blocks, horizontal FOV 120 degrees, close awareness 3 blocks, scan interval 4 ticks, detection accumulation/decay, visual grace 60 ticks;
- breach authorization threshold 10 effective radius;
- infected_breakable tag-controlled block breaking;
- daylight burn suppression intended to preserve ordinary fire/lava;
- zombie reinforcement suppression;
- adult-only conversion on join;
- AFL targeting/goals.

These are code-backed, but not all have complete live regression tests in this snapshot.

## World rules/terrain/progression

- Nether portal creation canceled.
- Player travel to Nether/End canceled.
- Natural Overworld monsters filtered so only vanilla zombie remains.
- Vanilla structure has_structure tags and strongholds.json are present.
- Never disable Stronghold with concentric_rings=0 or random_spread frequency=0; both were historical invalid/problematic approaches.
- Terrain resource exists at data/minecraft/worldgen/density_function/overworld/continents.json. Terrain work is experimental/paused; do not assume unfinished V1B/V1C changes.
- No Lithostitched dependency.
- VanillaProgressionEvents handles XP/mob XP/workstation restrictions.
- VanillaRecipePruningEvents handles selected recipe pruning on reload/start/login. Do not claim all vanilla recipes/advancements are removed.

## Resource/model rules

- Namespace apocalypse_firstlight; no .png in resource locations; no absolute Windows paths.
- Related language entries stay grouped.
- Hand-authored JSON is generally two-space; older Blockbench exports may have tabs.
- Complex furniture often uses ambientocclusion:false.
- Full-height double blocks: lower full model + empty upper; empty upper should have particle texture.
- Slabs/stairs use vanilla parents, base textures and actual 1.20.1 rotation/uvlock patterns.
- Tags generally use replace:false; preserve existing values.
- No Curios, Patchouli, GeckoLib or Lithostitched dependency.

## Planned/not implemented

plastic_scrap, plastic_pellets, plastic_sheet, plastic recycler/former, petroleum route, formal intensity/attenuation/weather profiles, smell, custom infected entities, shelf inventory/display/search, city terrain overhaul, larger worldgen, additional gun gameplay and gun packs.

## Known pitfalls

- Do not combine manual popResource with a non-empty loot table.
- Audit every direct/support explosion branch for double blocks.
- Normalize upper to lower before dedupe.
- Preserve dropContentsOnce and explosion marks.
- Empty upper particle texture prevents purple/black particles.
- Never leave E:/Download paths or #missing in model JSON.
- Larger atlas UVs need normalization/texture_size awareness.
- Stairs must match vanilla rotations and uvlock.
- Stronghold numeric-zero hacks are invalid/problematic.
- Preserve TaCZ refmap remapping settings.
- Forge 1.20.1 four-row container menus need the generic MenuType constructor with the container.
- Do not claim a runClient GUI/world test when only startup was reached.

## Verified/uncertain

Verified from current records:
- Forge/Java project builds successfully offline.
- TaCZ resolves and runClient reaches Forge/Minecraft startup.
- JEI/Jade are runtime-only helpers.
- TaCZ gun IDs, reference radii and suppressor addend were runtime-tested earlier.
- Current registrations/resources compile.
- Industrial Locker/Metal Locker slot sizes are 54/36.
- Latest recorded build after current fixes: BUILD SUCCESSFUL.

Not fully verified:
- complete live Metal Locker lower/upper/support/explosion tests;
- every shelf visual/collision facing;
- fresh-world progression/worldgen QA;
- dedicated-server QA for all event paths;
- final visual QA for every furniture model.

## Backlog/reuse

The explicit current task is pause/handoff. Do not continue development automatically.

On resume: read git status/HEAD, run offline build, audit only the requested subsystem, and distinguish implemented/compiled/launched/manually tested.

Reuse:
- double-height containers -> IndustrialLockerBlock lifecycle;
- no-container double-height furniture -> SupermarketShelfSingleBlock;
- explosion salvage -> IndustrialMaterialExplosionDrops only;
- wall/ceiling -> Utility Light/Electrical Box patterns;
- facing/shape -> actual vanilla 1.20.1 blockstates;
- mining -> existing tags/loot;
- TaCZ noise -> TaczNoiseEvents, IGun, TimelessAPI and public modifier data;
- structure disabling -> legal datapack/tag methods only.

This document is a handoff aid; always re-read the live files after branch switches or merges.

