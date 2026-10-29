# 白尾鹿 D0 概念图

设计稿：docs/gameplay/afl_whitetail_deer_d0_design.md（第 10 节）。

- `deer_lib.py`：管子部件（关节环）、贴图色阶和笔触、骨架和姿态
- `deer_build.py`：雄鹿、雌鹿、玩家（m01 皮肤），姿态，渲染
  - `blender -b --factory-startup --python deer_build.py -- final`（`preview` / `poses` 是快速预览）
- `deer_compose.py`：把 r1/ 里的渲染拼成三张审图（`python deer_compose.py`）
- 审图：`deer_d0_r1_views.png`、`deer_d0_r1_scale.png`、`deer_d0_r1_poses.png`
- `r1/deer_buck_atlas.png`、`r1/deer_doe_atlas.png`：两张 256 贴图；`deer_d0_concept.blend`：最后一次的场景
