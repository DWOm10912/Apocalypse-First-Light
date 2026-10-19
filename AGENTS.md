# Documentation Sync

Documentation is part of the Definition of Done for every implementation task that changes final live behavior.

This includes new or removed systems and features, gameplay or balance changes, architecture changes, command changes, registry/name changes, behavior-changing bug fixes, and features that are postponed after previously being documented as current.

Before finishing any implementation task:

1. Identify which `docs/*.md` files describe the modified system.
2. Update only those directly related documents to match the final live behavior.
3. Update implemented, partial, planned, obsolete, and known-issue status accurately.
4. Update every changed value, formula, command, registry ID, name, and relevant code/resource path.
5. Remove obsolete behavior descriptions or clearly mark them obsolete.
6. Never describe an unimplemented plan as current behavior.
7. Never claim runtime verification when runtime verification was not performed.
8. If no documentation change is needed, explicitly explain why in the final report.

If live behavior changed but its documentation was not synchronized, the task is not fully complete.

Every implementation-task final report must include:

```text
DOCUMENTATION:

DOCS UPDATED = YES/NO

Updated:
- <path>

Reason if NO:
<why this task does not affect documentation>

WIKI UPDATED = YES/NO/NOT NEEDED
```

# Player Wiki

The player wiki lives in `wiki/` (VitePress, see `docs/wiki/afl_wiki_v1_plan.md`). It is part of the Definition of Done for player-visible changes.

**Paused (user, 2026-10-18):** until shortly before the V1 test release, no session updates the wiki, local `wiki/` included; one session will then bring it up to date in one pass. Until then the rules below do not apply; report `WIKI UPDATED = NOT NEEDED (paused until V1 test release)`.

- When a task changes something a player can see or feel (values, controls, items, names, recipes, how a system behaves, whether something is in the game), update the matching `wiki/` page in both languages (`wiki/<page>.md` and `wiki/en/<page>.md`) in the same task.
- Never hand-copy numbers: use `<V d="folder/file#key.path" />` for datapack JSON and `<V j="Class.CONSTANT" />` for Java numeric constants. Gun, ammo, attachment and biome tables come from the data automatically.
- New guns need `wiki/content/guns/<id>.zh.md` and `<id>.en.md`.
- Mark unfinished content with `<StatusTag s="creative|test|planned|new|live" />`; never describe planned content as current.
- Run `npm --prefix wiki run check` before finishing; it fails on missing values, missing translations, missing gun prose and unbuilt images.
- The user's Obsidian overview under `docs/项目内容/` (for example `02 - 制作清单.md`) should be kept in step with the same change.

# Chinese Localization

All Simplified Chinese text (`lang/zh_cn.json`, UI, hints, docs meant for players) follows `docs/localization/zh_cn_style_guide_v1.md` (user-approved 2026-10-18).

Core rule for localization and narrative writing (user, 2026-10-18): every text reads like the real kind of text it is (guide section 2.0). Before writing, settle what kind of text it is, who wrote it, and who it is for.

- Write en_us and zh_cn in the same task, in the same section and line.
- Never change keys, placeholders (`%s`, `%1$s`, `%%`), mechanics or numeric meaning while rewording.
- Check the glossary (section 5) first. Stamina is 耐力. Sprint is 疾跑. Cross-fed is 线路互连.
- World signs, roads, tunnels and place names are English in every language. Chinese UI, item names and hints are Chinese.
- Store signs, road signs, logos, vehicle badges and poster brand lettering stay English. Documents the player opens (newspapers, notes, diaries, manuals, notices, texts, emails, radio subtitles) follow the game language, with confirmed Chinese company names (guide section 2.6).
- Tone is restrained and realistic, but humour is not banned. Characters, pre-war ads and the document's source decide how much. No narrator-wide satire, and no forced joke in every lore text (guide section 2.11).
- Companies and brands use their Chinese names in Chinese text (glossary 5.9, `docs/world/federation_companies_v1.md` section 6). Product names and model codes (P9-01, Tamarack T15, Silverwood 12) stay English.
- Narrative text (death screen, backgrounds, radio, story) gets its own review pass, separate from UI copy.

# Blockbench Source Models

- All editable Blockbench source models belong under `src/main/blockbench/`.
- Editable Blockbench sources and runtime Minecraft model JSON are different artifacts.
- Do not place or overwrite editable Blockbench source geometry under `src/main/resources/assets/.../models/`.
- BER / `ENTITYBLOCK_ANIMATED` blocks may intentionally use particle-only or builtin runtime model JSON.
- When a model geometry changes, preserve/update the editable source under `src/main/blockbench/` and update its runtime representation only when the task requires it.

# Mining Tools and Salvage Tiers

- Every new block must explicitly audit its intended mining tool, required tier, `requiresCorrectToolForDrops()`, matching `minecraft:mineable/*` tag, and matching `minecraft:needs_*_tool` tag when applicable.
- Industrial machines, industrial infrastructure, reinforced structures, and high-value metal salvage default to `minecraft:mineable/pickaxe` plus `minecraft:needs_diamond_tool`.
- Complete industrial or structural blocks based on steel, aluminum, lead, zinc, tin, nickel, silver, or tungsten default to Diamond-tier unless explicitly designed as low-tier content. Aluminum commercial or industrial structures, including the Commercial Glass Double Door, are Diamond-tier.
- All AFL industrial ores, including bauxite, galena, sphalerite, cassiterite, pentlandite, spodumene, and wolframite, default to Diamond-tier unless explicitly overridden.
- Natural terrain, road markings, wood, leaves, saplings, and ordinary decorative blocks follow material-appropriate Vanilla-style tool rules instead of inheriting the industrial Diamond-tier default.
- Slab, stair, and door variants must synchronize mining tags and tier requirements with their parent material in the same task.
- A block task is not complete until Survival mining and drop behavior has been checked.

### AFL building tasks

For AFL building, structure, WorldEdit, NBT, `minecraft-builder-skill`, city, factory, or signal-tower tasks, first read:

`docs/authoring/afl_industrial_palette_v1.md`

Use the current repository Registry IDs and BlockState definitions as the source of truth. Do not substitute vanilla blocks for documented AFL structure blocks unless the task explicitly requires it. If a custom block orientation is visually uncertain, build a small in-game test before large-scale construction.
