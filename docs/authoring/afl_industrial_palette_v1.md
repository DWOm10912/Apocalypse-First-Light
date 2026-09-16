# AFL 工业建筑方块目录 V1

## 目的

本文件是给 Codex、`minecraft-builder-skill` 和 WorldEdit 建筑任务使用的方块选择与摆放知识入口，不是游戏逻辑定义文件。Registry ID、BlockState 和运行时行为仍以当前仓库源码与资源为最终真相。

本版只整理当前已注册、属于钢制工业结构体系的七个基础方块。审计来源包括：

- 注册与方块行为：`src/main/java/com/antaurora/apofirstlight/registry/AflBlocks.java`、`AflItems.java`、`AflCreativeTabs.java`
- 自定义行为：`src/main/java/com/antaurora/apofirstlight/block/SteelBeamBlock.java`、`FixedDiagonalBraceBlock.java`、`FixedDiagonalBraceShapes.java`
- 状态与模型：`src/main/resources/assets/apocalypse_firstlight/blockstates/`、`models/block/`
- 本地化：`src/main/resources/assets/apocalypse_firstlight/lang/zh_cn.json`、`en_us.json`
- 掉落与挖掘属性：`src/main/resources/data/apocalypse_firstlight/loot_tables/blocks/`、`src/main/resources/data/minecraft/tags/blocks/`

## 使用规则

1. AFL 建筑、结构、WorldEdit、NBT、`minecraft-builder-skill`、城市、工厂或信号塔任务开始前先读取本文件。
2. 优先使用下列 AFL 专属结构方块；不要默认用原版 `iron_bars` 模拟复杂工业主结构。
3. 对朝向敏感的方块必须写出完整 namespace 和明确 BlockState。
4. 不确定自定义模型视觉朝向时，先做小范围游戏内测试，不要直接整栋施工。
5. `steel_railing` 的连接状态依赖邻居更新；导入 NBT 或 WorldEdit 后要在游戏内确认连接结果。
6. 斜撑的 `joint_negative` / `joint_positive` 不是斜率选择；斜率由方块 ID 和 `horizontal_axis` 决定，joint 状态由相邻 `steel_beam` 决定。

## 工业结构 Palette

| Registry ID | 中文名 | English name | 分类 | 关键状态 | 主要用途 |
| --- | --- | --- | --- | --- | --- |
| `apocalypse_firstlight:steel_beam` | 细钢梁 | Steel Beam | 主承重结构、横梁 / 立柱 | `axis=x/y/z` | 工厂钢架、信号塔主腿、横梁 |
| `apocalypse_firstlight:diagonal_brace_a` | 钢制斜撑 | Diagonal Steel Brace | 单斜撑 A | `horizontal_axis=x/z`; `joint_negative/positive=false/true` | 单向斜撑、塔架分段支撑 |
| `apocalypse_firstlight:diagonal_brace_b` | 钢制斜撑 | Diagonal Steel Brace | 单斜撑 B | `horizontal_axis=x/z`; `joint_negative/positive=false/true` | 与 A 反向的单向斜撑 |
| `apocalypse_firstlight:cross_brace` | 钢制交叉撑 | Cross Steel Brace | X 型交叉撑 | `horizontal_axis=x/z`; `joint_negative/positive=false/true` | 塔架或大型钢架的清晰 X 撑 |
| `apocalypse_firstlight:steel_railing` | 钢栏杆 | Steel Railing | 平台护栏、细连接件 | `north/east/south/west`; `waterlogged` | 维护平台、楼梯边缘、天线细节 |
| `apocalypse_firstlight:steel_block` | 钢块 | Steel Block | 实体钢结构块、节点 / 基座 | 无自定义状态，blockstate key 为 `""` | 厚重节点、柱脚、设备基座 |
| `apocalypse_firstlight:steel_plate` | 钢制板材 | Steel Plate | 实体钢结构块、铺设面 | 无自定义状态，blockstate key 为 `""` | 地板、设备底板、局部实体平台 |

所有七个 ID 均在当前 `AflBlocks` / `AflItems` 中真实注册，并由 `AflCreativeTabs` 的 Blocks tab 展示。所有七个方块都设置了 `requiresCorrectToolForDrops()`，并出现在当前 `minecraft:mineable/pickaxe` 与 `minecraft:needs_diamond_tool` 标签中。

## 详细条目

### `apocalypse_firstlight:steel_beam`

- 本地化：zh_cn `细钢梁`；en_us `Steel Beam`。
- 分类：主承重结构、横梁 / 立柱。
- BlockState：`axis=x`、`axis=y`、`axis=z`。它继承 `RotatedPillarBlock`；`y` 为竖直立柱，`x` 为 X 向横梁，`z` 为 Z 向横梁。
- 模型：`blockstates/steel_beam.json` 按 axis 选择同一 `models/block/steel_beam.json`，模型是细的、非完整方块包络；不要把它当作实体墙。
- 自动连接：没有 north/east/south/west 或自定义 joint 状态；模型本身提供端部和凹面细节。斜撑的 joint 检测只认相邻的 `steel_beam`。
- 建筑建议：四根连续主腿、规则水平横梁、工厂桁架、设备架主骨架。WorldEdit / NBT 中必须显式写 axis。
- 不建议：不用 `iron_bars` 替代它作为主承重结构；不用 `steel_plate` 或整块 `steel_block` 取代大量细梁。
- 备注：当前 Java 碰撞/选择包络按轴向细梁处理；视觉结果依赖实际资源模型，复杂连接应做游戏内小样。

### `apocalypse_firstlight:diagonal_brace_a`

- 本地化：zh_cn `钢制斜撑`；en_us `Diagonal Steel Brace`。
- 分类：单斜撑 A。
- BlockState：`horizontal_axis=x/z`、`joint_negative=false/true`、`joint_positive=false/true`。
- 放置/朝向：`horizontal_axis=x` 选择 X-Y 竖直平面模型，通常从 Z 方向观察；`horizontal_axis=z` 选择 Z-Y 竖直平面模型，通常从 X 方向观察。水平轴由点击 EAST/WEST 或 NORTH/SOUTH 的结构面决定。
- 斜率：A 的本地固定形状沿 `+x,+y` 或 `+z,+y` 上升；即低端在对应轴的 0 端，高端在 14 端。实际模型固定为一个 8 步、16×16 单元内的单斜撑。
- Joint：`horizontal_axis=x` 时 negative/positive 分别检查 WEST/EAST 相邻方块；`horizontal_axis=z` 时分别检查 NORTH/SOUTH。只有相邻 `steel_beam` 才会显示对应 joint。
- 自动连接：不是铁栅栏连接方块；只自动更新两个 joint 布尔状态，不连接到任意邻居。
- 建筑建议：每 3–5 格节奏中的少量连续单斜撑；必须让端点落在 `steel_beam` 上或由实际节点支承。
- 不建议：不要只写 `/` 或 `\\` 作为方向说明；不要把 A 当作 X 撑；不要让它悬空或连续堆满每个格子造成噪点。
- 备注：固定 1×1 模型，视觉斜率需要结合游戏内模型确认；NBT 中不要猜 joint 状态。

### `apocalypse_firstlight:diagonal_brace_b`

- 本地化：zh_cn `钢制斜撑`；en_us `Diagonal Steel Brace`。
- 分类：单斜撑 B。
- BlockState 与自动 joint 行为：同 A，即 `horizontal_axis=x/z` 和两个 joint 布尔状态；joint 方向由轴向决定，而不是由 A/B 决定。
- 斜率：B 的本地固定形状沿 `-x,+y` 或 `-z,+y` 上升；即低端在对应轴的 14 端，高端在 0 端。它与 A 是两种固定反向斜率。
- 建筑建议：与 A 成对使用以形成清晰的交叉或镜像支撑；适合塔架相邻节段的反向斜撑。
- 不建议：不要用 B 单独填满四面；不要把它当作可任意旋转的楼梯；不要忽略端点支承。
- 备注：固定 1×1 模型；`horizontal_axis=x` 对应 X-Y 平面，`horizontal_axis=z` 对应 Z-Y 平面。视觉朝向仍应以游戏内小样为最终确认。

### `apocalypse_firstlight:cross_brace`

- 本地化：zh_cn `钢制交叉撑`；en_us `Cross Steel Brace`。
- 分类：X 型交叉撑。
- BlockState：`horizontal_axis=x/z`、`joint_negative=false/true`、`joint_positive=false/true`。
- 放置/朝向：与 A/B 相同；`x` 为 X-Y 平面，`z` 为 Z-Y 平面。模型由 A、B 两个固定方向的斜撑形状合并而成。
- Joint：与 A/B 相同，只检查所在轴向两端的相邻 `steel_beam`；它不是通用邻居连接系统。
- 建筑建议：较宽塔节或主桁架中少量使用，一个完整 X 应连接明确的上下节点；远看应优先读出主腿和平台，再读出 X 撑。
- 不建议：不要把它当作完整墙面或连续铁网；不要与大量零碎 `iron_bars` 叠加；不要在没有主梁节点的位置悬空使用。
- 备注：固定 1×1 模型；复杂朝向和 joint 变化要在游戏内确认。

### `apocalypse_firstlight:steel_railing`

- 本地化：zh_cn `钢栏杆`；en_us `Steel Railing`。
- 分类：平台护栏、细连接件。
- BlockState：继承原版 `IronBarsBlock` 的 `north/east/south/west` 连接布尔状态与 `waterlogged` 状态。blockstate 使用这些方向组合选择 post、cap、side 模型。
- 自动连接：是。Java 注册直接使用 `new IronBarsBlock(...)`，因此沿用原版铁栅栏的邻居连接逻辑；连接结果还会受邻居方块和水状态影响。
- 建筑建议：维护平台边缘、楼梯边缘、少量天线支架或细连接件；WorldEdit / NBT 中应显式写 `waterlogged=false`，并在邻居写入完成后检查方向状态。
- 不建议：不再用它模拟信号塔四根主腿、长斜撑或主体桁架；不要把整座工业建筑做成铁丝网。
- 备注：资源模型位于 `models/block/steel_railing_*.json`；这是连接型细部件，不是实体承重块。

### `apocalypse_firstlight:steel_block`

- 本地化：zh_cn `钢块`；en_us `Steel Block`。
- 分类：实体钢结构块、节点 / 基座。
- BlockState：无自定义状态，blockstate 变体 key 为 `""`。
- 自动连接：无自定义连接行为。
- 建筑建议：柱脚、加固节点、实体设备基座、局部厚重结构；适合作为 `steel_beam` 和斜撑端点的视觉节点。
- 不建议：不要用它替代全部细梁；不要把它当作薄板或护栏。
- 备注：当前 loot table 要求匹配 `apocalypse_firstlight:industrial_material_recovery_tools` 才掉落本体；注册属性包含正确工具掉落要求，且当前挖掘标签为钻石级镐。

### `apocalypse_firstlight:steel_plate`

- 本地化：zh_cn `钢制板材`；en_us `Steel Plate`。
- 分类：实体钢结构块、铺设面。
- BlockState：无自定义状态，blockstate 变体 key 为 `""`。
- 自动连接：无自定义连接行为。
- 建筑建议：设备底板、局部实体地面、平台铺设面和结构封板；与 `steel_beam` 主骨架组合使用。
- 不建议：不要把它当作主承重梁；不要用整块板材封死需要保持镂空的塔架。
- 备注：当前 loot table 要求匹配 `apocalypse_firstlight:industrial_material_recovery_tools` 才掉落本体；注册属性包含正确工具掉落要求，且当前挖掘标签为钻石级镐。

## 信号塔推荐组合 V1

这是材料组合建议，不是建筑结构或世界内容：

- 主腿 / 横梁：`apocalypse_firstlight:steel_beam`，分别使用 `axis=y`、`axis=x/z`。
- 单斜撑：`apocalypse_firstlight:diagonal_brace_a` / `diagonal_brace_b`，按实际平面设置 `horizontal_axis`。
- X 撑：`apocalypse_firstlight:cross_brace`，只在少量主塔节使用。
- 平台护栏：`apocalypse_firstlight:steel_railing`，显式处理四向连接和 `waterlogged`。
- 实体节点 / 基座：`apocalypse_firstlight:steel_block`。
- 平台 / 铺设面：`apocalypse_firstlight:steel_plate`；若需要薄层或楼梯形变体，另查当前已注册的 `steel_plate_slab` / `steel_plate_stairs`。

## Builder / WorldEdit 注意事项

- Mod 方块 ID 必须使用完整 `apocalypse_firstlight:` namespace。
- `steel_beam` 必须明确 `axis`；斜撑必须明确 `horizontal_axis`，不要凭名称猜平面。
- `joint_negative` / `joint_positive` 必须来自实际相邻 `steel_beam` 状态或游戏内更新，不要把它们当作 A/B 斜率。
- `steel_railing` 的四向连接和 `waterlogged` 必须在邻居全部写入后复核。
- 第三方 Builder 预览器可能无法显示 AFL 自定义模型；预览只能作为几何检查，游戏内实机 / NBT 才是最终视觉验收。
- 不确定视觉朝向时先做一个小测试柱节，确认后再批量施工。

## 已知待补充

- 当前仓库已确认注册、lang、blockstate、主要模型路径、Creative Tab、挖掘标签与 loot table；本文件没有声称完成游戏内视觉截图验收。
- A/B/C 的固定斜率已由 `FixedDiagonalBraceShapes` 的实际端点逻辑记录；不同资源包或光照下的远距离可读性仍需要游戏内参考图确认。
- `steel_railing` 的实际邻居连接结果需要在目标游戏版本中用小范围放置测试确认。
