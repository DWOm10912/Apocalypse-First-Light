# 感染者 Z0 设计样本 r1（2026-10-26）

设计说明见 `docs/gameplay/afl_infected_z0_design.md`。

- 贴图：`node tools/build-infected-z0-skins.mjs` 生成 `z0_a.png`、`z0_b.png`、`z0_c.png`（128×128，原版 64 网格放大一倍）。
- r1 审图：在 Blender 5.x 里执行 `z0_blender_build.py`。它新开一个空文件，搭旧方块身体（Main 的肩 / 髋关节环），渲染 `z0_lineup_r1.png` 和 `z0_fronts_r1.png`，存成 `z0_infected_r1.blend`。
- 对比的第 4 版是现在替换原版的 `src/main/resources/assets/minecraft/textures/entity/zombie/zombie.png`。
- 刚感染变体（r2 起）：生成器同时写 `z0_f2.png`–`z0_f7.png`（第 1 个就是 `z0_a.png`）；在 Blender 里执行 `z0_fresh_build.py`，渲染 `z0_fresh_<轮次>.png`（全身）和 `z0_faces_<轮次>.png`（头部近景），存成 `z0_fresh.blend`。`z0_fresh_r2.png` 是 r2 的旧审图。
- 两个 Blender 脚本共用 `z0_blender_lib.py`（模型、UV、材质、光照）。
- 状态：只是样本，游戏不读这些文件。
- r6 概念（和玩家同一画风、感染者自己的骨架）：在 Blender 里执行 `z0_concept_r6_build.py`，再调 `build_all()`、`pose_all()`、`render_final()`；单张渲染在 `r6/`，审图 `z0_concept_r6.png`、`z0_concept_r6_silhouettes.png`，场景 `z0_concept_r6.blend`。参考图板在 `reference/`（内部参考，不进公开内容）。
- r5 的 `z0_concept_build.py` 和 `z0_concept_r5*.png` 已被否，只留作记录。
