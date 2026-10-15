# Rural Driveway V1（乡村车道口：镀锌管农场大门 + 乡村信箱）

状态（2026-10-14）：
- **已实现：两个方块、Mesh 模型、动画、交互、提示、掉落和挖掘规则；GameTest `forest_home_m3a` PASS；用户 2026-10-14 实机测试通过**（用户自己摆放、开关门、换牌、信箱，说"没问题"）。
- 这是溪谷老宅的 M3a 院落设施（[老宅计划](../worldgen/forest_start_home_b_v1_plan.md) 第 M3a 项）。概念图在 [design/buildings/forest_start_home_v1/m3_sheets/](../../design/buildings/forest_start_home_v1/m3_sheets/)：`m3a_driveway_mouth.png`、`m3a_dimensions_anchor.png`（概念），`m3a_game_preview.png`（游戏资源的预览渲染）。
- 还没有放进老宅测试世界，也没有放进老宅模板（M1 脚本）：车道口的位置要等 M3b 车道和 C 的县道定下来。
- 挖掘等级是临时的：用户 2026-10-14 说以后要重做 AFL 自己的挖掘规则，现在随便用一个合理的等级（镐 + 石级）。

和概念图不一样的地方（实现时改的）：
- 信箱是 5 格（漏斗界面），不是概念图写的 3 格：原版现成的小界面只有 5 格的漏斗界面，不用为 3 格单做界面。
- PRIVATE DRIVE 牌是三个状态（`sign=none|front|back`），不是概念图的 `sign=true`：大门可以从任意一边放，牌子要能朝公路那面。
- 门柱上只有铁链，没有挂锁。

## 模型

- 生成器：`tools/build-rural-driveway-v1.mjs`（`--check` 只检查不写；`--preview DIR` 另外写出关着 / 往前开 / 往后开的大门和开门的信箱的 OBJ，给 `design/buildings/forest_start_home_v1/m3_source/driveway_preview.html` 渲染预览图）。
- 牌面图：`src/main/blockbench/textures/private_drive_sign_art.png`（900 × 600，白底黑字黑边，`m3_source/sign_art.html` 画的），生成器把它采样到牌子正面。
- 每个方块的输出（`<id>` = `tube_farm_gate` / `rural_mailbox`）：
  - 可编辑源 `src/main/blockbench/<id>_v1.bbmodel`、`src/main/blockbench/textures/<id>_v1{,_s,_n}.png`；
  - 运行时 `geo/<id>.geo.json`、`meshes/<id>.aflmesh.json`、`block_mesh_profiles/<id>.json`、`textures/block/<id>{,_s,_n}.png`；
  - 方块模型 `models/block/<id>/mesh.json`（加载器 `apocalypse_firstlight:static_mesh`，主格用）、`cell.json`（其它格，只有粒子贴图）；方块状态只按 `cell` 分；
  - 物品模型 `models/item/<id>.json` → `models/block/<id>/item.json`（`forge:obj`，`item.obj` / `item.mtl`）。
- Pure Mesh + LabPBR，静止的部件由区块画，动着的由方块实体渲染器画（[Animated Block Mesh Runtime](../rendering/animated_block_mesh_runtime_v1.md)）。共面重叠检查 0 处。

| 方块 | 三角面 | 贴图 | 骨骼 | 动画通道 |
|---|---:|---|---|---|
| `tube_farm_gate` | 1952 | 2048 atlas，15 texel/px（牌面 5 倍） | `frame`（两根门柱、合页销、铁链），`swing`（纯转轴）→ `leaf`（门扇、合页套、横管、斜撑）→ `sign_front` / `sign_back` | `open_front`：`swing` +90°；`open_back`：`leaf` −90°；各 24 tick（1.2 s），ease_in_out |
| `rural_mailbox` | 528 | 1024 atlas，30 texel/px | `body`（木柱、托板、箱体、后板），`door`，`flag` | `open`：`door` 往下翻 95°，8 tick；`flag`：红旗竖起 90°，10 tick |

尺寸（毫米，取自生成器）：
- 大门：12 ft 五横管镀锌门，门扇 3.56 m × 1.27 m（离地 150 mm 到 1420 mm），外框管 Ø42，中间横管 Ø33，竖撑 + 斜撑；两根 Ø160 圆木门柱，高 1.65 m，柱心距 3.84 m；合页在 400 / 1200 mm 高；门扣端立柱上挂 6 节铁链。牌子 450 × 300 × 2 mm 铝牌，U 形卡子卡在横管上，4 颗螺栓。
- 信箱：美国乡村标准信箱，483 × 165 × 229 mm，黑漆钢，拱顶，箱底离地 1.07 m；89 × 89 木柱（4 × 4），顶上 140 × 38 × 420 托板；红旗在站在门前（公路上）的人的右手边。

## 方块

### `tube_farm_gate`（镀锌管农场大门）

- 类：`block/TubeFarmGateBlock`（`RectMultiblockBlock`），方块实体 `blockentity/TubeFarmGateBlockEntity`（`AflAnimatedMeshBlockEntity`，只在主格）。
- 4 × 2 格（`cell=c0r0..c3r1`）。放的时候 `facing` 朝放的人（这一面叫"前面"），点的那一格是主格 `c0r0`，放合页门柱；列沿 `facing` 的顺时针方向排，第 4 列是门扣柱和铁链。门扇在格子中线上。
- 状态：`open`、`swing=front|back`（开向哪边）、`sign=none|front|back`。所有格子的状态一起改。
- 交互：
  - 右键任意一格：开门 / 关门。开的时候往离人远的那边转：人在前面就往后开（`swing=back`），人在后面就往前开；关门不改 `swing`。声音：`tube_farm_gate_open` / `_close`（用户的录音，2026-10-14，[老宅音效 V1](../audio/forest_home_sounds_v1.md)），在门扇中间，8 格，同时发同半径的感染者噪声；关门那一下"哐"落在门扇停住的 1.15 s。原来是原版铁门声 + 铁链声。
  - 潜行 + 空手右键：换牌子，无 → 前面 → 后面 → 无；手里拿着东西时不响应（交给物品）。声音是原版铁链放置声，3 格（带噪声）。
- 碰撞（结构坐标 px，x 从第一列西边算）：两根门柱各 2.56 × 2.56、高 26.4；关着时门扇 z 7–9、高 24（1.5 格，跳不过去）；开着时门扇只在合页那一列里（往前开 z 0–8，往后开 z 8–16），伸出格子外的 3.2 m 没有碰撞（同围栏钢门的做法）。
- 质量 45 kg（估计），一组 1 个。

### `rural_mailbox`（乡村信箱）

- 类：`block/RuralMailboxBlock`（`RectMultiblockBlock`），方块实体 `blockentity/RuralMailboxBlockEntity`（`RandomizableContainerBlockEntity` + `AflAnimatedMeshHost`，只在主格）。
- 1 × 2 格（`cell=r0|r1`），下面一格是主格。放的时候门朝放的人：站在公路上放，门就朝公路。
- 状态：`open`（只在主格，有人开着就是 true）、`flag`（两格一起改）。
- 交互：
  - 右键任意一格：打开 5 格的漏斗界面，普通储物，不是渐进搜索容器；有人开着时门放下来，关掉界面门收回去（和木桶一样用 `ContainerOpenersCounter`）。声音：还是原版铁活板门开关声（信箱门没有素材），音调高一点，4 格，同时发同半径的感染者噪声（2026-10-14）。
  - 潜行 + 空手右键：红旗竖起 / 放下。声音 `rural_mailbox_flag_up` / `_down`（用户的录音），咔哒落在红旗转到位的 0.42 s，3 格（带噪声）。
  - 打掉：信箱和里面的东西都掉出来。
- 碰撞（px）：木柱 7.29–8.71 × 8.25–9.67、高 0–16.56；托板；箱体 x 6.68–9.32、y 17.12–20.78、z 4.14–11.86。开着的门没有碰撞。
- 质量 8 kg（估计），一组 4 个。

## 挖掘和掉落

- 两个都是：`minecraft:mineable/pickaxe` + `minecraft:needs_stone_tool`，`requiresCorrectToolForDrops()`。空手、木镐打掉不掉；石镐以上掉自己。这是临时等级（见上面"状态"），没有走 AGENTS 里钢件默认钻石级的规则。
- 掉落表 `loot_tables/blocks/<id>.json`（只有主格掉，经得起爆炸）；从别的格打掉时，在主格位置掉一个，所有格一起消失。
- 硬度：大门 3 / 抗爆 6，信箱 2 / 3。没有合成配方（用户以后要重做配方）。

## 接入

- 注册：`AflBlocks` / `AflItems`（`RectMultiblockBlockItem`）/ `AflBlockEntities` / `AflBlockEntityRenderers`（通用 `AflAnimatedBlockMeshRenderer`）；创造栏"家具"页，在金属垃圾桶后面。
- 提示（`WorldInteractionHint#ruralDriveway`）：大门"打开 / 关闭"，潜行空手"挂牌 / 翻面 / 摘牌"（显示潜行 + 使用两个键）；信箱"打开"，潜行空手"竖起红旗 / 放下红旗"。
- Jade：任意格都显示成主格（`RectMultiblockBlock` 的通用重定向，`PriceSignJadeProvider#registerRedirect`），没有额外信息。
- 标签：`apocalypse_firstlight:no_wiring`（不能布暗线）。
- 质量：`data/apocalypse_firstlight/item_mass/afl_content_v1.json`。
- 语言：`block.apocalypse_firstlight.tube_farm_gate` 镀锌管农场大门 / Tube Farm Gate，`block.apocalypse_firstlight.rural_mailbox` 乡村信箱 / Rural Mailbox，提示键 `hint.apocalypse_firstlight.tube_farm_gate.{open,close,sign.front,sign.back,sign.none}`、`hint.apocalypse_firstlight.rural_mailbox.{open,flag.raise,flag.lower}`。
- 建造桥（开发用，`AuthoringFixtureRegistry`）：`tube_farm_gate`（`place_multiblock`，锚点 `c0r0`，`sign` 可选，放下关着）；`rural_mailbox`（锚点 `r0`，`flag` 可选，放在地上）。

## 测试

- `src/dev/java/com/antaurora/apofirstlight/dev/ForestHomeM3aGameTests.java`，用 `src/dev/forest-home-m3a-gametest.init.gradle` 跑（`runGameTestServer --offline -PaflWithoutShaders`），2026-10-14 PASS：
  - 大门：整体放下 8 格、主格才有方块实体；从前面开往后转、从后面开往前转，关着和开着的碰撞，方块实体报的动画通道和牌子骨骼；潜行空手换牌三次、每格都改、不会开门，拿着东西不响应；从最远的上格用石镐打掉，8 格都没了，只掉 1 个；
  - 信箱：整体放下 2 格；从上格打开是 5 格漏斗界面、门开着，关界面门收回；潜行扳红旗两格一起改、不开界面；打掉掉信箱 + 里面的东西；
  - 挖掘：两个方块的工具标签、等级标签、需要工具；空手、木镐不掉，石镐掉。
- 客户端里的模型、动画、牌面朝向、提示条由用户 2026-10-14 实机看过，没问题。

## 已知限制

- 门扇开着时伸出格子 3.2 m，那部分没有碰撞；门扇转过去的路上有方块也照样转（穿模）。
- 往前开和往后开是两个通道：如果一边的关门动画没放完就从另一边开，会有一瞬间两个通道叠在一起。
- 牌子是大门的状态，生存里不用消耗任何东西就能挂上。
