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

# Development Wiki

The wiki lives in `wiki/` (VitePress, B design). Since 2026-10-19 (user) it is the user's private online development reference, deployed behind Cloudflare Access at `wiki.staticgrove.net`; see `docs/wiki/afl_wiki_v2_plan.md`. It replaces the 2026-10-18 pause: keep it current in every task. It is part of the Definition of Done for any change to features, values, controls, items, names, lore or settings.

Who does what:

- **C, D and other workspaces** update the matching `wiki/` pages on their own branch in the same task (both languages: `wiki/<page>.md` and `wiki/en/<page>.md`), so the merge brings code and wiki together. They may run `npm --prefix wiki run check` and `npm --prefix wiki run deploy -- --dry-run`, never a real deploy or an image upload.
- **Main** integrates, then runs `npm --prefix wiki run deploy` from the main checkout on master after each integration into master. Only Main uploads public images (`npm --prefix wiki run images:upload`). The scripts refuse other worktrees and branches.

Rules for pages:

- Status, never skipped: `<StatusTag s="verified" d="YYYY-MM-DD" />` = in master and the user checked it in game (date of that check); `integrated` = in master, not checked in game; `test` = test worlds / dev commands only, or still on a branch; `planned` = designed, not built. `creative` is an extra tag (creative-only for now). A branch never marks its own work `integrated` or `verified`: Main sets that when merging, `verified` only after a user in-game PASS. Never describe planned or draft content as current.
- Never hand-copy numbers: `<V d="folder/file#key.path" />` for datapack JSON, `<V j="Class.CONSTANT" />` for Java numeric constants; gun, ammo, attachment, biome, survivor and town tables come from the data automatically. Two tag-led lines in a row run together in markdown: write them as a list (`- <StatusTag ... /> ...`).
- New guns need `wiki/content/guns/<id>.zh.md` and `<id>.en.md`.
- Items and blocks are shown as 3D renders of the game model, never flat sprites: `<ItemIcon id="<item id>" />` in tables and lists, `<ModelShot id="<item id>" />` for a large picture (plus `model_<id>` in `images.json`). Any workspace may add them; Main renders missing ones in Blockbench (`wiki/scripts/icons.mjs`, `docs/wiki/afl_wiki_v2_plan.md` section 5.1) before deploying.
- Images: add them to `wiki/images.json` with a `category` (guns, vehicles, items, industry, survival, world, companies, ui) and a `visibility`. `public` images are uploaded to the public domain `img.staticgrove.net`; unreleased story, hidden places and anything confidential must be `internal` (bundled only into the Access-protected site). When unsure, use `internal`.
- Run `npm --prefix wiki run check` before finishing; it fails on missing values, translations, gun prose, invalid status tags and unbuilt images.
- The user's Obsidian overview under `docs/项目内容/` (for example `02 - 制作清单.md`) should be kept in step with the same change.

Report line: `WIKI UPDATED = YES/NO/NOT NEEDED (<why>)`; for Main, also whether it was deployed.

# Chinese Localization

All Simplified Chinese text (`lang/zh_cn.json`, UI, hints, docs meant for players) follows `docs/localization/zh_cn_style_guide_v1.md` (user-approved 2026-10-18).

Since 2026-10-19 (user, guide decisions 19–21) the independent GPT review in `docs/localization/gpt_review_2026-10-19/` is the reference baseline for zh_cn and wiki writing. Mechanics text (HUD status, attributes, Jade, errors, settings) uses precise written Chinese (移动速度降低, 无法自然恢复生命值). Spoken Chinese is for felt messages, backgrounds, characters and readable documents. Lore, people, companies and gun backgrounds keep their own voice and never read like HUD copy. Do not apply its phrasing mechanically. Acceptance is independent review plus how it reads in game.

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

# Mesh Items In Hand

Every new Mesh item must be fitted for first-person and third-person hands in the same task (user 2026-10-21). The
item must sit in the hand, never float above it. Never ship the material bake's default block-style held transforms.
Compute the held `display` from the item's grip point. The method and the reference grip are in
`docs/models/equipment_meshes_v1.md`, section "手持适配规则". Check the numbers offline before the in-game check.
