# AFL 工业建筑方块目录 V1

## 目的

本文件是给 Codex、结构制作与 WorldEdit 建筑任务使用的方块选择入口。Registry ID、BlockState 和运行时行为仍以当前仓库源码与资源为最终真相。

## 2026-09-21 旧钢结构移除状态

旧 `steel_beam`、`diagonal_brace_a`、`diagonal_brace_b`、`cross_brace` 方块及其专用 Blockbench 源、模型变体、joint 变换、碰撞形状和生成工具已从 live repo 删除。这些 Registry ID 不再兼容旧开发世界，也不应继续用于结构、命令、NBT 或文档示例。

后续钢梁与钢斜撑会以当前 `steel_cable` 的简洁、低噪声 Minecraft 风格重新设计；本次删除没有提供替代 Registry ID。`src/main/blockbench/steel_cable.bbmodel` 与 `steel_cable_64.png` 是受保护的新方向制作资产，目前不能据此假定游戏内已经注册 `steel_cable` 方块。

## 使用规则

1. AFL 建筑、结构、WorldEdit、NBT、城市、工厂或信号塔任务开始前先读取本文件。
2. 只能使用当前仓库实际存在的 Registry ID；不得引用已移除的旧钢梁/斜撑 ID。
3. 不要用 `steel_cable` 替代已移除资产，直到它完成独立注册与运行时验收。
4. 对朝向或连接敏感的方块必须写出完整 namespace 和明确 BlockState。
5. `steel_railing` 的连接状态依赖邻居更新；导入 NBT 或 WorldEdit 后要在游戏内确认连接结果。

## 当前工业钢结构 Palette

| Registry ID | 中文名 | 分类 | 关键状态 | 主要用途 |
| --- | --- | --- | --- | --- |
| `apocalypse_firstlight:steel_block` | 钢块 | 实体钢结构 | 无 | 厚重节点、柱脚、设备基座 |
| `apocalypse_firstlight:steel_block_slab` | 钢块台阶 | 实体钢结构变体 | `type`, `waterlogged` | 半高结构面 |
| `apocalypse_firstlight:steel_block_stairs` | 钢块楼梯 | 实体钢结构变体 | 原版楼梯状态 | 楼梯与结构收边 |
| `apocalypse_firstlight:steel_plate` | 钢制板材 | 实体铺设面 | 无 | 平台、设备底板、封板 |
| `apocalypse_firstlight:steel_plate_slab` | 钢板台阶 | 板材变体 | `type`, `waterlogged` | 半高平台 |
| `apocalypse_firstlight:steel_plate_stairs` | 钢板楼梯 | 板材变体 | 原版楼梯状态 | 平台楼梯 |
| `apocalypse_firstlight:steel_grate` | 钢格栅 | 镂空平台 | 专用连接/形状以源码为准 | 工业走道与格栅面 |
| `apocalypse_firstlight:steel_railing` | 钢栏杆 | 连接型细部件 | `north/east/south/west`, `waterlogged` | 平台和楼梯护栏 |
| `apocalypse_firstlight:steel_door` | 工业门 | 门 | 原版门状态 | 工业出入口 |

这些 live 方块保留现有注册、模型、贴图、掉落与挖掘标签。本轮没有修改其视觉、碰撞或玩法行为。

## 关键注意事项

- `steel_railing` 继承连接型栏杆行为；邻居全部写入后复核四向状态。
- 实体钢结构与高价值工业回收仍按当前 Diamond-tier 镐标签与 loot 规则执行，具体以资源文件为准。
- 不要用原版 `iron_bars` 冒充未来的新钢梁、钢斜撑或钢缆体系。
- 第三方预览器可能无法显示 AFL 自定义模型；预览只用于几何检查，游戏内实机仍是最终视觉验收。
- `steel_cable` 当前只代表受保护的新美术方向资产，不是本轮新增的可用游戏方块。

## 后续状态

旧钢梁/斜撑体系已删除。新钢结构需另立任务完成设计、注册、模型资源、挖掘/掉落审计和游戏内视觉验收；在此之前，现行文档不得把任何新钢梁或新钢斜撑描述为已实现。
