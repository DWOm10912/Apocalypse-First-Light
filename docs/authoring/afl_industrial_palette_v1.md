# AFL 工业建筑方块目录 V1

## 目的

本文件是给 Codex、结构制作与 WorldEdit 建筑任务使用的方块选择入口。Registry ID、BlockState 和运行时行为仍以当前仓库源码与资源为最终真相。

道路V1-B新增永久`road_asphalt_surface`、`road_sidewalk_surface`、`road_utility_surface`、`road_curb`，统一layers=1..16及G/S高度；路缘另有facing/shape。它们采用普通道路镐/铲规则，不继承工业Diamond门槛。模型、贴图、碰撞、转角及Claude后续美术替换接口见[道路V1-B](../worldgen/north_american_roads_v1b_implementation.md)与[道路静态资产](../worldgen/road_surface_assets_v1.md)，实际走行和Survival掉落仍待用户验收。

## 2026-09-21 钢结构资产状态

旧 `steel_beam`、`diagonal_brace_a`、`diagonal_brace_b`、`cross_brace` 方块及其专用 Blockbench 源、模型变体、joint 变换、碰撞形状和生成工具已从 live repo 删除。这些 Registry ID 不再兼容旧开发世界，也不应继续用于结构、命令、NBT 或文档示例。

`steel_beam` 与 `steel_brace` 使用 `axis=x/y/z`，按点击面轴向放置。`steel_cable` 使用 V1.1B 的 `facing` 与 `segment`；玩家仍只放竖直，桥梁生成使用相位斜索。三者没有自动连接或 BlockEntity。V1.1C 斜段可编辑源为 `src/main/blockbench/steel_cable_sloped/*.bbmodel`（Free mesh），运行时为原 model JSON 引用 `models/block/steel_cable_sloped/*.obj`，通过 Forge 原生静态 OBJ loader 烘焙。详见 [V1.1C](../worldgen/highway_v2_sea_bridge_v1_1c.md)。

## 使用规则

1. AFL 建筑、结构、WorldEdit、NBT、城市、工厂或信号塔任务开始前先读取本文件。
2. 只能使用当前仓库实际存在的 Registry ID；不得引用已移除的 `diagonal_brace_a`、`diagonal_brace_b`、`cross_brace` ID。
3. `steel_beam` 与 `steel_brace` 仅支持正交三轴；`steel_cable` 手动默认竖直，斜段必须遵循 V1.1B 的有限坡度/相位契约，不支持任意角度或自动连接。
4. 对朝向或连接敏感的方块必须写出完整 namespace 和明确 BlockState。
5. `steel_railing` 的连接状态依赖邻居更新；导入 NBT 或 WorldEdit 后要在游戏内确认连接结果。

## 当前工业钢结构 Palette

| Registry ID | 中文名 | 分类 | 关键状态 | 主要用途 |
| --- | --- | --- | --- | --- |
| `apocalypse_firstlight:steel_block` | 钢块 | 实体钢结构 | 无 | 厚重节点、柱脚、设备基座 |
| `apocalypse_firstlight:steel_cable` | 钢缆 | 细钢结构件 | `facing=north/south/east/west`, `segment=vertical/r1/r2_0/r2_1/r3_0/r3_1/r3_2` | 桥索、拉索与细支撑；玩家默认竖直 |
| `apocalypse_firstlight:steel_beam` | 钢横梁 | 四向内凹钢梁 | `axis=x/y/z` | 桥塔、主梁与结构立柱 |
| `apocalypse_firstlight:steel_brace` | 钢斜撑 | 镂空斜撑框架 | `axis=x/y/z` | 桥梁与塔架加固 |
| `apocalypse_firstlight:steel_block_slab` | 钢块台阶 | 实体钢结构变体 | `type`, `waterlogged` | 半高结构面 |
| `apocalypse_firstlight:steel_block_stairs` | 钢块楼梯 | 实体钢结构变体 | 原版楼梯状态 | 楼梯与结构收边 |
| `apocalypse_firstlight:steel_plate` | 钢制板材 | 实体铺设面 | 无 | 平台、设备底板、封板；贴图（台阶、楼梯共用）是一格一整块螺栓钢板，四角 2×2 螺栓，比钢块暗、偏冷（2026-09-30 重画，生成器 `tools/build-material-textures-v1.mjs`；旧版是 3×2 小板加成排铆钉点） |
| `apocalypse_firstlight:steel_plate_slab` | 钢板台阶 | 板材变体 | `type`, `waterlogged` | 半高平台 |
| `apocalypse_firstlight:steel_plate_stairs` | 钢板楼梯 | 板材变体 | 原版楼梯状态 | 平台楼梯 |
| `apocalypse_firstlight:steel_grate` | 钢格栅 | 镂空平台 | 专用连接/形状以源码为准 | 工业走道与格栅面 |
| `apocalypse_firstlight:steel_railing` | 钢栏杆 | 连接型细部件 | `north/east/south/west`, `waterlogged` | 平台和楼梯护栏 |
| `apocalypse_firstlight:steel_door` | 工业门 | 门 | 原版门状态 | 工业出入口 |

三种新钢结构件均使用 `7.0F / 12.0F` 硬度/爆炸抗性、`requiresCorrectToolForDrops()`、`minecraft:mineable/pickaxe` 与 `minecraft:needs_diamond_tool`，并掉落自身。`steel_cable` 的碰撞外包围为 `[7,0,7]..[9,16,9]`；`steel_beam` 与 `steel_brace` 的 Y/X/Z 轴碰撞范围分别为 `[6.5,0,6.5]..[9.5,16,9.5]`、`[0,6.5,6.5]..[16,9.5,9.5]`、`[6.5,6.5,0]..[9.5,9.5,16]`。三者均使用非满方块遮挡处理。

## 关键注意事项

- `steel_railing` 继承连接型栏杆行为；邻居全部写入后复核四向状态。
- 实体钢结构与高价值工业回收仍按当前 Diamond-tier 镐标签与 loot 规则执行，具体以资源文件为准。
- 不要用原版 `iron_bars` 冒充新钢梁、钢斜撑或钢缆体系。
- 第三方预览器可能无法显示 AFL 自定义模型；预览只用于几何检查，游戏内实机仍是最终视觉验收。
- 当前没有对三种新钢结构件执行游戏内放置、碰撞或视觉验收；静态资源与构建通过不等同于实机验收。
- Beam / Brace 的方向能力仅限正交三轴；斜向钢缆已由 BridgeCableGeometry 与 steel_cable 的有限坡度/相位实现。
- Beam / Brace 的 X/Y/Z 轴变体不使用 `uvlock`；钢材纹理会随模型轴向一起旋转，不能恢复为世界方向锁定。

## 后续状态

V1.1B 已实现 steel_cable 斜向状态与桥索规划，仍只有原 Item；旧无属性状态按默认竖直加载。上述钢缆细柱 shape 仅指 vertical。V1.1C 可见斜段为连续四侧面直索，碰撞仍用每格 16 个细 AABB 近似；两者相对 V1.1B 均向 facing 平移 0.5 格、下移 0.25 格，以进入桥面混凝土及塔侧钢架。斜段局部几何允许沿 facing 超出所属块 0.5 格，横向仍在 ±14 内。贴图复用原 steel_cable.png。采掘仍沿用 Diamond-tier 镐与普通自身掉落；用户确认 V1.1B 大尺度生成成立，V1.1C 近景/碰撞仍待用户验收。

## 2026-10-06 加油站展示场景试搭（已叫停）

为什么记：用户想要一个"小加油站 + 便利店"场景给发布页截图，用 afl_minecraft 建造工具试搭，用户看了之后叫停：便利店外墙、屋顶直接用 `reinforced_concrete`，整片 `asphalt` 高出草地一格，"现代建筑哪有外壳直接是钢筋混凝土的"。等以后做新的建造 MCP 工具再搭。

这次发现的限制（给新工具和以后的建筑任务参考）：
- **外墙材料缺口**：没有现代商业建筑的立面方块（金属外墙板、带框的店面玻璃幕墙、招牌、檐口 / 女儿墙收边）。2026-10-06 起店面玻璃幕墙有了：`apocalypse_firstlight:storefront_glazing`（状态 `facing`、`left/right/up/down`、`mullion=edge|center|none`、`transom`，见 [storefront_glazing_v1.md](../models/storefront_glazing_v1.md)），以及黑框门 `commercial_glass_double_door_black`；外墙砖 `face_brick_warm_gray`（主墙面）、`face_brick_charcoal`（壁柱、转角）也有了（普通整格方块，见 [facade_brick_v1.md](../models/facade_brick_v1.md)）；勒脚 `ground_face_block`（状态 `facing`、`shape`、`cap`，上面不是自己或玻璃时自动出浅色压顶，见 [facade_masonry_base_v1.md](../models/facade_masonry_base_v1.md)）；铝檐口 `aluminum_cornice`（墙顶整格，状态 `facing`、`shape`、`band`，见 [aluminum_cornice_v1.md](../models/aluminum_cornice_v1.md)）；深灰金属墙板 `metal_wall_panel`（横向条板整格，状态 `cap` 和四个方向，上面没有墙板时自动出铝压顶）和门框侧板 `metal_panel_jamb`（贴格边的 5 px 薄板，状态 `facing`，见 [metal_wall_panel_v1.md](../models/metal_wall_panel_v1.md)）；其余仍缺。`reinforced_concrete` 是结构 / 工业材料，不能当现代建筑的外墙。做现代建筑前先补这类方块。
- **建造区不能低于地面**（`authoring_create` 的 `surface_offset_y` 不能为负），地下储罐、埋地管道无处可放，只能整片垫高。
- **支撑检查**：`FLOOR` 规则要求下面是完整顶面（`isFaceSturdy`），所以 `fuel_canopy_column` 只能放第一节、叠不上去；`fuel_island_bollard` 放不到路缘上（实际游戏里可以）。代码在 `src/dev/.../authoring/bridge/AuthoringFixtureRegistry.java` 和 `FixtureAdapter.java`。
- **白名单缺项**：`underground_fuel_tank_*`、`fuel_fill_cover_*`、`pump_manhole_cover`、`jerry_can`、`*fuel_drum`、`charging_station` 不能放。
- 已验证可用：`fuel_dispenser`（facing=south，b 列在西侧）、`fuel_island_curb` / `fuel_island_end`（东端 facing=north 时半圆朝东）拼成的加油岛外观正确；`commercial_glass_double_door` facing=north 装在南墙上正确。

试搭留在用户的超平坦世界里（建造区 `afl_fuel_station_showcase`，368..399, -51..-32, 64..89），是否清理由用户决定。

后续（2026-10-06）：按真实北美规划文件重新做了加油站设计稿 [AFL Fuel Stop A1](../worldgen/fuel_stop_a1_design_v1.md)，里面列了这栋楼需要补的外墙材料（P0 / P1 / P2）。还在等用户审方案，没有施工。

