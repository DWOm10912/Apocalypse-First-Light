# AFL Road Surface Assets V1 — 道路静态资产契约

> **已移除（2026-10-08，用户要求）**：`road_asphalt_surface`、`road_sidewalk_surface`、`road_utility_surface`、`road_curb` 四个方块已经删掉。
> - 删除的东西：方块类、注册、物品、方块状态、112 个面层模型、掉落表、挖掘标签、中英文名，以及生成器 `tools/generate-road-surface-assets.mjs`。
> - 原因：贴图是临时的（人行道就是钢筋混凝土贴图），除了实机验收没通过的道路 V1-B 施工代码之外没有别的用处。以后路面、路缘做成正式的 mesh 模型。
> - 道路 V1-B 的规划代码保留：`RoadConstructionMaterials` 改成满格占位，沥青用 `asphalt`，人行道和路缘用 `reinforced_concrete`，设施带用草方块。规划的 1/16 高度还检查，但不再建出来。
> - A1 店铺的人行道删之前已在开发存档里换成 `reinforced_concrete`（188 格），外观相同。
> - 开发存档里别处如果还有这四种方块（比如道路 V1-B 的测试路段），进游戏后会变成空气。
>
> 下面是当时的内容，留作参考，不再是现行状态。

状态：**已实现代码、注册与静态资源；美术为最小可用版本，实机放置、行走、采掘掉落和视觉尚未验证。**

本文服务于[北美道路 V1-B](north_american_roads_v1b_implementation.md)，服从[统一道路与地块规格 V1](north_american_roads_and_lots_spec_v1.md)。四个 Registry ID 是正式接口，后续 Claude 替换美术不更换 ID、状态名、坐标或碰撞契约。

## 1. Registry、BlockState 与资产路径

下面 ID 均属于 `apocalypse_firstlight`，同时有同名 BlockItem，加入 `AFL · 建筑方块` 创造栏。`n` 代表整数 `1..16`。

| Registry ID | BlockState / 默认状态 | 方块模型路径（assets/apocalypse_firstlight/ 内） | 当前贴图 |
|---|---|---|---|
| `road_asphalt_surface` | `layers=1..16`；默认13 | `models/block/road_surfaces/road_asphalt_surface_<n>.json` | `textures/block/asphalt.png` |
| `road_sidewalk_surface` | `layers=1..16`；默认16 | `models/block/road_surfaces/road_sidewalk_surface_<n>.json` | `textures/block/reinforced_concrete.png`（临时复用） |
| `road_utility_surface` | `layers=1..16`；默认16 | `models/block/road_surfaces/road_utility_surface_<n>.json` | `minecraft:textures/block/dirt.png`（裸土设施带基础） |
| `road_curb` | `layers=1..16,facing=north/east/south/west,shape=straight/inner/outer/driveway`；默认 `16,north,straight` | `models/block/road_surfaces/road_curb_<shape>_<n>.json` | `textures/block/reinforced_concrete.png`（临时复用） |

每个 ID 的 BlockState 文件为 `blockstates/<id>.json`，物品模型为 `models/item/<id>.json`，掉落表为 `data/apocalypse_firstlight/loot_tables/blocks/<id>.json`。当前不新增或修改 PNG；人行道与路缘不是钢筋混凝土工业方块的行为变体，只暂时引用其贴图。

源码：

- `src/main/java/com/antaurora/apofirstlight/block/RoadSurfaceBlock.java`
- `src/main/java/com/antaurora/apofirstlight/block/RoadCurbBlock.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/roads/construction/RoadConstructionMaterials.java`
- 注册：`registry/AflBlocks.java`、`registry/AflItems.java`、`registry/AflCreativeTabs.java`
- 静态资产生成器：`tools/generate-road-surface-assets.mjs`，输出112个模型、4个blockstate、4个item model、4个loot table。不会执行世界施工。

## 2. 高度与碰撞

表面方块采用格内 `[0,16]` 模型坐标；`layers=n` 的顶面为块位 `Y+n/16`。普通沥青、人行道和设施带的模型及 VoxelShape 均为 `Block.box(0,0,0,16,n,16)`，没有水浸状态、自动叠层或随机厚度。

绝对表面高度 `topH16` 转换：

```text
blockY = floorDiv(topH16 - 1, 16)
layers = floorMod(topH16 - 1, 16) + 1
```

该规则同样适用于负世界高度；恰好整数顶面放在下一格的下方，使用 `layers=16`，不能写无效 `layers=0`。

平坦断面：

- G 为整数，沥青 S=G−3/16：在 `Y=G−1` 放 `road_asphalt_surface[layers=13]`。
- 人行道、设施带最高表面与路缘顶取 G：同 Y 使用 `layers=16`。
- 车辆入口3格量化过渡：最高表面依次14/16、15/16、16/16；使用完整平顶 `shape=driveway` 或对应面层，不能把带凸唇的直线路缘横放在入口净宽内。
- 纵坡允许1/16量化高度；相邻柱的支撑、上下格转换和下层填充由施工器负责。资产不自行修改邻居或地形。

路缘占用规划预留的一格，但物理凸唇仅宽4px、高最多3px。令 `b=max(0,n−3)`，其基础覆盖整格 `[0,0,0]..[16,b,16]`，凸唇补到 `n`。`n≤3` 时基座在本格为零，凸唇跨格下方的支撑由施工器负责；该格不伸出所属格去写负Y模型。整体最高表面仍严格等于指定高度。

Java 预计算并缓存全部形状，选择框与碰撞使用同一 VoxelShape；静态模型使用相同盒子的无重叠分解。四种方块均无 BlockEntity、无自定义动态 renderer、无 tick。

## 3. 直线、转角与车辆入口

`facing` 表示直线路缘的**沥青侧**。标准模型 `facing=north` 的局部北侧是 `z=0`，朝向其他方向仅绕格中心水平旋转。

| shape | NORTH 标准模型的凸唇范围（高度 b..n） | 规则 |
|---|---|---|
| `straight` | `x=0..16,z=12..16` | 后侧连续4px条；沥青在北侧，设施带在南侧 |
| `inner` | `z=12..16` 与 `x=12..16` 的并集 | 两条相交成L形；另一条位于标准模型东侧 |
| `outer` | `x=12..16,z=12..16` 的交集 | 凸角4×4端块 |
| `driveway` | 整格 `[0,0,0]..[16,n,16]` | 完整平顶，没有额外凸唇；施工器用不同layers形成入口过渡 |

EAST/SOUTH/WEST 使用模型 Y 旋转90/180/270度，Java形状使用同方向变换。`inner`/`outer` 具有方向性，镜像时也旋转角部朝向，避免直接镜像 facing 后改变角部手性。形状由施工计划显式指定，**没有邻居自动连接或自动改形**。施工器如何从节点分区选择转角，见V1-B实施记录；资产拥有转角状态不代表所有路口已实机验收。

当前是正交静态最小路缘，不是高质量圆弧/磨损模型。Claude可细化可见表面，但必须维持最高顶面、格边接口及可行走区域，不能在道路车道内增加超出当前碰撞的突出物。

## 4. 施工 API

`RoadConstructionMaterials` 对美术文件无依赖，公开：

```java
asphalt(int layers)
sidewalk(int layers)
utility(int layers)
surface(Material material, int layers) // ASPHALT / SIDEWALK / UTILITY
curb(int layers, Direction facing, RoadCurbBlock.Shape shape)
```

全部返回 BlockState；layers必须1..16，curb仅接受水平方向。API不拆世界高度、不放置方块、不加载区块；这些属于独立施工核心。

## 5. 采掘与掉落契约

| 方块 | 工具/最低门槛 | requiresCorrectToolForDrops | 标签 | 硬度/抗爆 |
|---|---|---|---|---|
| 沥青、人行道、路缘 | 普通镐，木镐即可；无高等级门槛 | YES | `minecraft:mineable/pickaxe`；不加入needs_stone/iron/diamond | 1.5 / 6.0 |
| 设施带裸土 | 铲加速，手挖可掉落 | NO | `minecraft:mineable/shovel`；无needs标签 | 0.5 / 0.5（DIRT） |

每块掉落同名物品1个，并以 `minecraft:copy_state` 保留 layers，路缘另保留 facing和shape；不因薄面层重复掉落完整下方路基。不新增配方。创造栏物品按默认高度显示；命令、保留状态的物品或施工器可使用其他高度。

**静态采掘审计已完成：注册属性、工具标签和loot一致；Survival实机采掘与掉落仍待用户验证。** 不把静态资源通过写成生存测试PASS。

## 6. Claude 美术接入边界与验收

允许后续补充独立道路PNG，并改模型textures引用、细化材质、增加对应LabPBR companion；现有共享 `reinforced_concrete.png` 不应为了道路效果被整体覆盖。ID、layers、facing、shape和高度契约不变时，施工算法不需要重写。

当前最小验收：平坦13/16沥青对16/16人行道/路缘、14→15→16入口、四向直线/内外转角、正负世界Y量化、相邻chunk走行、Survival木镐/徒手/铲掉落、保存重载及资源渲染。未启动游戏，以上都不能标记为已实机通过。
