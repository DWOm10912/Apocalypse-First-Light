# Cedar Office 06 — live authoring study

Status: PARTIAL, not exported, not ready for acceptance.

User requested an original six-storey American office, studying but not copying the imported Modern Office Building V. Reference world slices show repeated approximately five-block floor spacing, a concentrated core, perimeter office areas and glazed facade repetition. Sampled south facade glass percentage was 46.55%, including padding; this is not a net window-to-wall ratio. Detailed reference roof equipment interpretation remains incomplete.

Independent design: 31×23 building on a 37×31 plot, six-block ground floor and five five-block upper floors; brick end walls, grouped windows, off-centre south entrance, rear-west stair and cross-corridor office layout. These are design intentions, not all built.

Live plot `office_cedar_06`: overworld minimum (48,-33,96), maximum (84,4,126). World session `9ab75b9a-b4cc-4f50-9e87-7e9b51cc6c65`. Existing imported reference is untouched.

Current live state: 6,361 non-air blocks. Base and seven horizontal slabs are placed; ground facade and part of next level are built. Screenshot and world readback confirmed this partial shell. Interior, roof equipment, and two review/correction rounds are NOT completed.

Blocker: bridge rejects further edits at 32 retained operations (`HISTORY_LIMIT`). No history reset, session cycling or direct save modification was used to evade the guard. Need a reviewed bounded batch-edit workflow before continuing.

Construction recipe: `tools/afl_minecraft_mcp/cedar_live.mjs`. It contains unexecuted stages and is NOT safely resumable from the start of a partially completed stage. Do not blindly rerun. Current screenshot: `run/afl_authoring_captures/9ab75b9a-b4cc-4f50-9e87-7e9b51cc6c65/75c68cd3-52c3-4e48-ba49-f10f2626b0d9.png`.

Only existing Vanilla blocks were placed; no new block definitions, tool tiers or loot behavior were introduced. No Export invoked.
