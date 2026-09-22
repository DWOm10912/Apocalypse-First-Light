# AFL 工业建筑方块目录 V1

## 目的

本文件是给 Codex、结构制作与 WorldEdit 建筑任务使用的方块选择入口。Registry ID、BlockState 和运行时行为仍以当前仓库源码与资源为最终真相。

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
| `apocalypse_firstlight:steel_plate` | 钢制板材 | 实体铺设面 | 无 | 平台、设备底板、封板 |
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
