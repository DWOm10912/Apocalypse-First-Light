# Lead Chest V2（铅箱 V2，方案 A：屏蔽储存罐）

状态（2026-09-30）：**已实现，未实机验证**。`compileJava --offline` 一次 PASS。本轮不做音效。

## 不变与改变

| 项目 | 状态 |
|---|---|
| Registry ID | `apocalypse_firstlight:lead_chest`，不变 |
| 容量 | 27 格，不变 |
| 辐射 | 不变：`ContainerRadiation` 仍按 `LeadChestBlockEntity` 取透射率 0.50，不形成双箱。开盖**不**改变屏蔽 |
| 挖掘与掉落 | 不变：硬度 3、抗性 6、`requiresCorrectToolForDrops()`、`mineable/pickaxe` + `needs_diamond_tool`，方块掉落表不变 |
| 方块类 | 原来继承原版 `ChestBlock` / `ChestBlockEntity`，由 GeckoLib 渲染；现在是普通方块 + `RandomizableContainerBlockEntity`，由 Animated Block Mesh Runtime 渲染。blockstate 属性从 `facing / type / waterlogged` 改为 `facing / open`，**不再能含水** |
| 交互 | 右键直接打开界面 → 先开盖，再搜索或查看（与储物柜相同） |
| 逐格搜索 | 接入 Progressive Container Search |
| 声音 | 原来的原版木箱开合声随 `ChestBlock` 一起移除；新音效待定，目前开合**没有声音** |

## 模型

生成器：`tools/build-lead-chest-v2.mjs`（`--check` 校验输出是否最新）。

可编辑源：
- `src/main/blockbench/lead_chest.bbmodel`
- `src/main/blockbench/textures/lead_chest{,_s,_n}.png`

运行时文件：
- `geo/lead_chest.geo.json`（覆盖旧 GeckoLib geo）
- `meshes/lead_chest.aflmesh.json`
- `block_mesh_profiles/lead_chest.json`
- `textures/block/lead_chest{,_s,_n}.png`
- `data/apocalypse_firstlight/mesh_shapes/lead_chest.json`

坐标与储物柜相同：单位 px，原点在方块底面中心，正面朝 -Z，+X 是站在正面时的左手边。

**772 个三角面**，14 个 Mesh part，没有共面重叠，贴图密度 6.75 texel/px（512 atlas）。各 bone 面数：body 372、lid 256、两个压紧扣各 72。

| 部位 | 几何 |
|---|---|
| 铅体 | 14×12 px，底板 2 px；四壁 2 px，高度 1.5–10.5。开盖后能看到浅色的铅断面箱口 |
| 盖子 | 铅盖，高度 10.6–13.0（与箱口留 0.1 缝）；底面带一圈塞子，每边比箱口内壁小 0.15，关盖时塞进箱口 |
| 钢框 | 四角护角、箱体两道钢带、盖子一道钢带、底部两根叉车橇轨 |
| 五金 | 正面两个压紧扣（底座板 + 扳杆 + 盖上的扣座）、盖上两个提手、背面两组铰链 |

**动画**（通道 `open`，14 ticks，`ease_in_out`）：
- 盖子绕后铰链轴转 +100°，pivot [0, 10.55, 5.95]。
- 两个压紧扣各绕自己的下端 pivot 转 -120°，向前翻下。
- 两者同时进行，因为运行时 V1 每个部件只有一条时间线。

**Render bounds**（NORTH 方向，方块局部坐标）：`[0.053, -0.016, -0.144, 0.947, 1.428, 1.203]`。打开的盖子会伸到上方和背后。

## 材质

| 材质 | Base Color | `_s`（R 光滑度 / G） |
|---|---|---|
| `lead` 铅 | 外壁 [80,75,106]，外壁有从下到上的低频明暗；朝上的面 [88,83,114]；箱口断面、盖底塞子 [110,106,124]（新切开的铅偏亮）；箱内 [56,52,76]，越往下越暗 | 62 / **235**（LabPBR 预设金属"铅"），倒角 96 |
| `steel` 钢框 | 深炭灰涂层 [46,49,54] | 104 / 24 |
| `steelDark` 橇轨 | [34,36,40] | 80 / 24 |
| `zinc` 压紧扣、提手、铰链叶片 | [142,146,150] | 118 / 255 |
| `zincDark` 铰链轴 | [100,103,108] | 110 / 255 |

- 铅的颜色沿用旧铅块贴图的紫灰色（平均 [75,72,104]），并降低了饱和度。旧铅块已被 Material System V1 删除（换成中性灰的铅屏蔽砖），铅箱的颜色本轮不变。
- 铅用 LabPBR 的预设金属"铅"（G=235），光滑度很低，避免在光影下像大块金属板那样反光发白。
- `_n` 是平直法线，B 通道为 AO。
- 没有涂装、锈迹或文字。

## 形状与交互（Mesh Shape Runtime）

以下坐标都是 NORTH 朝向的模型坐标，单位 px。

| 状态 | Physical（碰撞） | Selection（选中/轮廓） | 交互区域 → 锚点 |
|---|---|---|---|
| closed | 整个铅箱 [-6.9,0,-6.3 → 6.9,13,6.4] | 铅箱 + 提手 | `lid`：整个铅箱 → `latch` [0,11.8,-6.3]（两个压紧扣中间） |
| open | 箱体（到箱口）+ 竖起来的盖子 [-6.75,10.11,5.19 → 6.75,22.17,11.0] | 同 physical | `interior`：箱体和箱口 → `opening` [0,10.8,-3]；`lid`：竖起来的盖子 → `lid_edge`（开盖后盖子前缘的位置） |

| 区域 + 状态 | 提示 | 右键 |
|---|---|---|
| `lid` + 关 | 打开 / Open | `open=true`，播放开盖动画。上方是实心方块时打不开，和原版箱子一样 |
| `lid` + 开 | 盖上 / Close | `open=false`；正在查看的界面全部关闭（`stillValid` 要求 `open`） |
| `interior` + 开，还有未揭示的格子 | 搜索 / Search | 逐格搜索界面（3 行） |
| `interior` + 开，已全部揭示或是玩家放置的 | 查看 / View | 普通 3 行界面 |

- 开盖时发出 3 格的 `INTERACTION` 噪音，和原来作为原版箱子"打开箱子"时的噪音相同，感染者的反应保持不变。
- 世界内提示改为通用实现：新增接口 `meshshape/AflMeshInteractionBlock`，方块根据"瞄中的区域 + 自身状态"返回提示文字键。`WorldInteractionHint` 不再为储物柜单独写分支，储物柜和铅箱都实现这个接口。

## 搜索

- 按 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md) 的 contract 接入，节奏与储物柜相同：40 ticks/格，±15%，不发翻找噪音。
- 玩家放置时在 `setPlacedBy` 里写入"不需要搜索"；带 `LootTable` 的世界铅箱第一次接触时进入搜索。
- 所有移除路径（破坏、爆炸、`/setblock` 替换）都走 `onRemove` → `dropContentsOnce` → `dropContentsOnBreak`：已揭示的格子掉落，未揭示的 loot 随铅箱销毁。
- 比较器仍有输出（原版箱子就有），但只统计已揭示的格子（`revealedAnalogSignal`），不会暴露未搜索的内容。
- 客户端只同步一个"已搜完"标记，不同步物品。

## 移除的旧文件

- `client/LeadChestRenderer.java`、`LeadChestGeoModel.java`、`LeadChestItemRenderer.java`（GeckoLib 渲染）
- `client/LeadChestModel.java`（此前已无任何引用的原版箱子模型）
- `animations/lead_chest.animation.json`
- `textures/entity/storage/lead_chest.png`

物品改由 `AflStaticMeshItemRenderer` 画同一份 Mesh（关盖姿态），`models/item/lead_chest.json` 的 display 变换不变。

破坏粒子：`models/block/lead_chest.json` 和 `models/item/lead_chest.json` 的 `particle` 原来借用 `block/lead_block`，2026-09-30 起改为铅箱自己的 `block/lead_chest`（Material System V1 删除了铅块）。

## 需要实机重点验证

1. 四个朝向下，铰链在背面、压紧扣在正面，开盖方向正确。
2. 盖子、箱口的提示与动作是否一致；站在铅箱后面时，竖起的盖子有碰撞。
3. 物品栏图标的大小（display 沿用旧值，新模型比原版箱子矮）。
4. 光影下铅的质感（LabPBR 预设金属"铅"）。
