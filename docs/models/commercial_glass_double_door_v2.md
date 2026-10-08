# Commercial Glass Double Door V2（玻璃双开门 V2：Mesh + PBR）

状态（2026-10-07）：**已实现，未实机验证**。
- `compileJava --offline` 通过；生成器 `--check` 通过；网格没有共面重叠（`zFightLevels` 为 0）。
- 导出的网格和贴图在离线 three.js 预览里看过：A1 门框里的正面、拉手特写、室内一侧，以及开门状态，银框、黑框两种都看了。
- 还没进游戏看过：光影下的 PBR、开门动画、碰撞、物品图标、挖掘掉落都没有实机检查。
- GameTest 按规则没有运行，但测试里的通道检查已经按新门扇改好。

## 为什么做

- V1 门是 GeckoLib 的纯 cube 模型：86 个 cube，128 × 128 贴图，没有 PBR。
- V1 的布局是 2 格宽的单元里，正中一对约 0.5 m 宽的窄门扇，两边各带一块固定玻璃。
- A1 便利店的两个正门就在门框正中，是立面上最显眼的东西，所以搭建筑之前先重置。

## 用户 2026-10-07 定下的方向

概念图对比了三个方案：
- A：中宽门框 + 竖向偏置拉手；
- B：窄门框 + 横向推拉杆；
- C：宽门框 + 中横档 + 长竖拉手。

用户的决定：
- **选 A**。
- **门扇占满整个单元**。店面玻璃已经在门两边，单元里再带固定玻璃是重复的。
- **两种颜色都改**，银框门保留。

## 外形（真实店面门的做法）

| 部件 | 尺寸 |
|---|---|
| 门框 | 两侧和门头各 1 px（6.25 cm），2 px 深。和店面玻璃同一种型材、同一个面：从 `facing` 那一面往里 3..5 px |
| 门扇 | 两扇，各约 0.93 m × 1.92 m，厚 0.71 px（1¾"），装在门框中线上，铰接在两侧门框上，中缝 0.1 px |
| 门边框和上档 | 3½"（1.42 px），中宽门框 |
| 底档 | 10"（4.06 px） |
| 玻璃 | 1" 中空玻璃，四周一圈黑色胶条 |
| 拉手 | 在 `facing` 一侧（拉门那一侧）：中缝门边上的竖向偏置拉手，Ø 2.5 cm，长 0.40 m，中心约 1.05 m 高 |
| 推杆 | 在另一侧（推门那一侧）：横穿门扇的推杆，1.05 m 高 |
| 合页 | 拉门一侧，每扇 3 个合页节，就是转轴 |
| 闭门器 | 推门一侧的上档上，靠铰链 |
| 门槛 | 铝门槛，0.2 px 高 |

## 开门

- 两扇门一起向 `facing` 那一侧转 90°，6 tick（0.3 秒），ease in out。V1 大约 5 tick 转到位。
- `facing` 是放门时朝向玩家的那一面。所以从门外面放，门就往外开，和真实店面门一样。
- 开门后每扇门伸出到前面一格约 0.72 格。A1 门框外凸的那一排深 1 格，放得下。
- **和 V1 不同**：V1 往背离 `facing` 的一侧开，门扇一直在门自己的格子里。V2 门扇更宽，开门后一定会伸出格子，所以改成朝放置的人一侧开。已经放在世界里的旧门（比如 `convenience_store_01.nbt` 里的银框门）也会按新方向开；如果门前面紧贴着东西，开门时门扇的碰撞会和它重叠。

## 方块（不变的部分）

- **不变**：ID、状态（`facing` / `open` / `part`）、2 × 2 部件、放置条件、开关门交互（点任一格两扇一起开、12 tick 防连点）、音效、掉落、挖掘规则、重量、搬运标签、方块实体类型。所以 MCP 固定件登记和已有的 NBT 都不用改。
- **挖掘规则**：仍是镐、铁级，这是 V1 文档里明确写的例外，GameTest 也按铁级检查。AGENTS.md 写的是"商业玻璃双开门属于铝制商业结构，钻石级"，两者冲突。用户 2026-10-07 决定先保持铁级。

## 方块（改动的部分）

- **碰撞和选框**：`CommercialGlassDoubleDoorBlock` 的 `lower` / `upper`，数值来自生成器的几何。
  - 关门：每格有自己那一侧的门框（px 0..1 或 15..16，z 3..5），加上门扇（z 3.64..4.36）。上层格还有门头（y 15..16）。
  - 开门：门扇转到门框内侧，master 格在 px 1.22..1.94、z −11.53..3.37，伸出格子。这是大碰撞形状，在一格以内，原版能正确处理。
  - 通道：开门后中间 x 2..16 都是空的，高 1.9 格。
- **方块实体**：`CommercialGlassDoubleDoorBlockEntity` 改为继承 `AflAnimatedMeshBlockEntity`。
  - profile 按方块选，银框 / 黑框两个。
  - 通道 `open` 跟着 `OPEN` 走，开关状态同步到客户端后动画自动播放，不再需要 GeckoLib 的触发动画。
  - 渲染范围仍然显式写出（两列两层、深度各留一格），服务器端没有 mesh profile 也不受影响。
- **渲染**：`AflBlockEntityRenderers` 用通用的 `AflAnimatedBlockMeshRenderer` 渲染这个方块实体。
- **物品**：`CommercialGlassDoubleDoorItemRenderer` 改成一个工厂，按方块返回 `AflStaticMeshItemRenderer`，用门的网格和对应颜色的贴图，门是关着的。物品模型的各个视角由生成器拟合。
- **删掉的类**：`client/CommercialGlassDoubleDoorRenderer.java`、`client/CommercialGlassDoubleDoorModel.java`。这两个是 GeckoLib 渲染器和模型，方块实体不再是 GeckoLib 的之后就编译不过了。

## 材质（LabPBR）

- 1024 × 1024 贴图，银框、黑框各一套（`_s` / `_n`），UV 布局完全一样。
- 精度：每 px 10 个 texel（每米 160）。材质都是纯色，没有逐像素噪点。

| 材质 | 颜色 | 光滑度 | F0 | 说明 |
|---|---|---|---|---|
| 门框和门扇（黑框） | [36,38,41] | 76 | 14 | 和店面玻璃的黑色阳极氧化铝相同 |
| 门框和门扇（银框） | [172,176,180] | 120 | 232 | 本色阳极氧化铝，LabPBR 金属铝 |
| 玻璃 | [176,204,204]，alpha 46 | 240 | 10 | 和店面玻璃相同，半透明层 |
| 胶条 | [22,23,25] | 50 | 14 | — |
| 拉手、推杆、合页 | [168,171,175] | 150 | 255 | 缎面不锈钢 |
| 门槛 | [150,153,156] | 110 | 232 | 铝本色 |

## 文件

- **生成器**：`tools/build-commercial-glass-double-door-v2.mjs`（`--check` 校验，`--preview DIR` 离线预览）。
  - 可编辑源：`src/main/blockbench/commercial_glass_double_door.bbmodel`（Free Model）。
  - 贴图副本：`src/main/blockbench/textures/commercial_glass_double_door{,_black}{,_s,_n}.png`。
- **运行时**：
  - `geo/commercial_glass_double_door.geo.json`：V2 的骨骼 `frame`、`leaf_a`、`leaf_b`，覆盖了 V1 的 GeckoLib geo；
  - `meshes/commercial_glass_double_door.aflmesh.json`：玻璃在半透明层；
  - `block_mesh_profiles/commercial_glass_double_door{,_black}.json`；
  - `textures/block/commercial_glass_double_door{,_black}{,_s,_n}.png`；
  - `models/item/…`、`models/block/…`：只有粒子，用新的方块贴图。
- **黑框门的旧贴图**：`textures/entity/commercial_glass_double_door_black.png` 和 `src/main/blockbench/textures/commercial_glass_swing_door_black.png`，原来由店面玻璃生成器重新上色得到。这两个文件已删除，`assets/minecraft/atlases/blocks.json` 里对应的那一行也去掉了。店面玻璃生成器不再写门的贴图。
- **V1 文件**：用户 2026-10-07 同意删除，已删（都在 git 历史里）：
  - `animations/commercial_glass_double_door.animation.json`；
  - `textures/entity/commercial_glass_double_door.png`，atlas 里它那一行也去掉了；
  - `src/main/blockbench/commercial_glass_swing_door.bbmodel` 和 `src/main/blockbench/textures/commercial_glass_swing_door.png`；
  - `tools/export-commercial-glass-double-door.mjs`（V1 导出工具）。
  - `tools/commercial-storefront-door.blockbench.js`（在 Blockbench 里生成 V1 源模型的脚本），用户同意后同日删除。

## 已知问题 / 以后

- 闭门器只有机身，没有连杆。
- 两扇门的中缝没有密封条。
- 没有门禁、营业时间之类的贴纸（按"不做彩绘装饰"的原则）。
