# Underground Fuel Tank V1（地下油罐）

状态（2026-10-04；2026-10-05 加了卸油口）：**已实现**，`compileJava --offline` PASS（包括 `src/dev`）。模型和 PBR 只做了离线预览（剖开的土坑里两个罐，各接一根管道 V2）。**用户实机测试过**：用户 2026-10-08 说明：加油机、顶棚、加油岛、油罐、泵井这一整套之前都实机测试过，没问题；往卸油口倒油罐里的油能给地下罐加油。当时没有逐项记录看过哪些地方（生存挖掘掉落也没有单独的记录）。（这里原来写着"没有实机验证"。）

## 是什么

埋在加油站地坪下面的卧式油罐，加油站 V2 燃油系统的第一块。用户 2026-10-04 定下：
- 固定尺寸，一个物品放下就是整罐；
- 一罐只装一种油：汽油罐和柴油罐两个物品，用颜色标签区分；
- 新式双层玻璃钢罐（FRP）；
- 罐顶正中做一个流体管道接口，作为以后所有流体设备通用的接口标准，就像电缆的电源接口。见 [fluid_pipe_v2.md](fluid_pipe_v2.md) 的"流体接口规格"。
- 2026-10-05：罐埋两格深。地面一层是泵井盖和卸油口盖，下面一层是潜油泵和管道，再下面是罐顶。罐尾加一个卸油口，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md)。

| 注册 ID | 名称 | 装的油 | 标签颜色 |
|---|---|---|---|
| `apocalypse_firstlight:underground_fuel_tank_gasoline` | 汽油地下储罐 / Underground Gasoline Tank | 汽油（[fuel_fluids_v1.md](../gameplay/fuel_fluids_v1.md)） | 红 |
| `apocalypse_firstlight:underground_fuel_tank_diesel` | 柴油地下储罐 / Underground Diesel Tank | 柴油 | 黄 |

代码：
- `block/UndergroundFuelTankBlock`（两种罐共用，构造参数是油品）；
- `blockentity/UndergroundFuelTankBlockEntity`（方块实体类型 `underground_fuel_tank`，两个方块共用）；
- `item/UndergroundFuelTankItem`；
- `fluid/AflFluidPortBlock`（接口标准）。

物品放在创造标签页 `furniture` 里，加油站顶棚边檐之后，堆叠 1。

## 方块和放置

- **尺寸**：3（宽）× 3（高）× 7（长）格，共 63 格。
- **方块状态**：`axis`（x / z，罐的走向）、`flipped`（2026-10-08，见下面"朝向和旋转"）、`along` 0..6、`across` 0..2、`level` 0..2。
- **主格**：接口格（3, 1, 2），在罐顶正中。它画整个罐、持有方块实体，顶面是流体接口；其它格不渲染（`RenderShape.INVISIBLE`），模型是只有粒子贴图的 `underground_fuel_tank/cell`。
- **卸油口格**（2026-10-05）：罐尾顶上那格（6, 1, 2），`FILL_ALONG = 6`。顶面是第二个流体接口。它也有一个方块实体，但自己不存油：流体能力直接交给主格的罐（`UndergroundFuelTankBlock.isFill`）。
- **放置**：点击的位置是罐的底层正中格（3, 1, 0），罐身沿玩家视线方向，卸油口那头在远端（2026-10-08 起；以前卸油口总在南端或东端，朝北、朝西看着放时在玩家这头）。63 格必须都可替换、没有液体、没有实体挡着，否则放不下。现实里先挖一个 3×3×7 的坑，在坑底中间放。
- **拆除**：拆任意一格，整个罐一起消失，只掉一个物品。拆主格时走掉落表；拆别的格时，生存模式玩家用对的工具才掉。罐里的油丢失。
- **完整性检查**：缺格时剩下的格自己消失（每格在形状更新时安排一次 tick 检查）。
## 朝向和旋转（2026-10-08）

**原来的 bug**（2026-10-07 读代码查出，当时没修）：
- `rotate` 只把 `axis` 在 x / z 之间换一下，没有重算每一格的 `along` / `across`。
- 结构模板转 90° 或 180° 放下后，各格的编号和它们的新位置对不上，完整性检查第一 tick 就把整罐拆掉。只有不转时正常。
- 而且只有 `axis` 一个属性，罐只能表示两种朝向：卸油口在南端（z）或东端（x）。所以转 180° 时，卸油口必然跑到罐的另一头，接在它上面的管道和卸油口盖就对不上了。

**现在**：
- 新属性 `flipped`：+along（指向卸油口那头）是南 / 东，`flipped=true` 时反过来是北 / 西。旧存档里的罐没有这个属性，读成 false，不受影响。
- `across` 总是朝东（z）或朝南（x）增加。罐横截面左右对称，所以四个朝向就是同一个罐身转四次。
- `rotate` / `mirror`：每一格保留 `along`、`level`，按转过去的 +along 定 `axis` 和 `flipped`；+across 转成 −across 时 `across` 改成 2 − across。转过的模板里，每一格正好是转后那个罐的对应格，卸油口跟着转到该去的位置。
- 碰撞箱：四个朝向各一套，罐头的弧面在 along 0 和 6 的外侧。
- 方块状态（生成器 `tools/build-underground-fuel-tank-v1.mjs`）：主格罐体按 +along 转：南 y 0、东 y 270、北 y 180、西 y 90。
- 代码接口：`alongDir(axis, flipped)` / `alongDir(state)`，`cellPosition(root, alongDirection, along, across, level)`，`stateFor(alongDirection, …)`，`cells(root, alongDirection)`（整罐 63 格）。`fluid/FuelContainers`、`/dev fuel station`、建造工具都改用这套。
- 验证：
  - 离线推算 4 个朝向 × 4 种旋转 × 2 种镜像，504 格都能算回同一个罐、卸油口位置正确。
  - GameTest `dev/UndergroundFuelTankRotationGameTests`（`src/dev/underground-fuel-tank-gametest.init.gradle`）：
    - 纯计算部分：4 个朝向 × 3 种镜像 × 4 种旋转；
    - 世界部分：存成模板后转 90 / 180 / 270 度和左右镜像放下，几 tick 后检查 63 格都在、主格有方块实体。
    - **只编译，没有运行。**
- 还没实机看过：旋转后的模型方向。A1 加油区的罐都是 `flipped=true`，世界里卸油口朝北，见 [加油区施工记录](../worldgen/fuel_stop_a1_forecourt_build_v1.md)。

- **挖掘**：玻璃钢加钢件，算工业设施。`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`，`requiresCorrectToolForDrops()`，硬度 / 抗性 5.0 / 8.0，`SoundType.STONE`。
- **碰撞箱**：按圆截面近似。每格按 4 px 一列取圆弧内的高度；两头的格稍短；主格加上人孔和接口；卸油口格加上立管和接口。
- **重量**：`item_mass` 150 kg（估计），`carry/oversized`。

## 储存和接口

- **容量**（AFL 统一 1 mB = 1 升，见 [流体系统](../项目内容/01%20-%20设计/工业/流体系统.md) 第 0 节）：方块实体里一个 30,000 mB 的罐（2.4 m 直径、约 6.4 m 长，约 30 m³），只收这个罐自己的油（`FluidStack.getFluid().isSame(fuel)`）。
- **接口**：主格顶面和卸油口格顶面（`hasFluidPort(state, UP)`），通到同一个罐。管道 V2 从上面一格接下来，在 `FluidPipeBlock.isFluidPort` 里通过 `AflFluidPortBlock` 认出。流体能力也只开在这两个面（以及无方向的查询）。
- **被动储存**：别的设备（比如立式储罐）通过管道往里推，能灌进去。罐子自己不往外送；往外抽油靠装在主格上面的潜油泵（2026-10-05，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md)）。
- **Jade**（`compat/jade/UndergroundFuelTankJadeProvider`，2026-10-04）：对着罐的任何一格，都显示整罐的油品、存量 / 容量和液体条，样式同立式储罐（`FluidTankJadeComponentProvider.appendFluid` 共用）。只有接口格有方块实体，所以用 Jade 的射线回调把目标换成接口格；Jade 设置里单独一个开关"AFL 地下油罐"，并去掉 Jade 自带的通用液体行以免重复。没有实机验证。
- **开发指令**（`src/dev`，OP 2）：
  - `/dev fuel fill [mB]`：给看着的地下罐灌它自己的油，默认灌满；
  - `/dev fuel info`：查看罐里的油和量。

## 资产

| 项 | 内容 |
|---|---|
| 生成器 | `tools/build-underground-fuel-tank-v1.mjs`（纯 Mesh，`tools/cube-slab-mesh-lib.mjs` + `tools/afl-fluid-port.mjs`；`--check`、`--preview DIR`） |
| 坐标 | 以接口格中心为原点，罐沿 z（`axis=z`）。罐轴在 y −20 px，占位 x ±24、y −40..8、z ±56 |
| 罐体（`TANK`，px） | 32 边形圆柱，半径 19，直段 ±47；两头椭圆碟形封头，深 8.9，10 圈；8 道加强肋（±11、±22、±33、±44，宽 3、高 1.3，倒角），人孔两侧不放；罐顶两端各一个起吊耳（z ±38.5） |
| 人孔（`MANWAY`） | 玻璃钢圆短管 r 8（y −2.5..2.0）；钢圆盖 r 9.6（y 2.0..3.2，倒角），12 颗螺栓在 r 8.8 上；方底板 ±5.8（y 3.2..3.9）带 4 颗螺母；方接管 ±4.2（y 3.9..7.4）；接口板（y 7.4..8，`afl-fluid-port`）。圆盖上焊一根方接管，和方截面的管道 V2 对得上 |
| 卸油口（`FILL`，2026-10-05） | 在 z +48（卸油口格正中）：玻璃钢底座 r 3.4（y −2.6..0.6），钢立管 r 2.6（y 0.6..7.4），方颈 ±4.2（y 6.8..7.4），接口板（y 7.4..8）；立管上一圈油品色带（y 3.6..5.2） |
| 标签 | 两侧各两块弧形色板（贴着罐身，在 22 和 33 两道肋之间，±14°），短管上一圈色带。汽油红 [166,46,38]，柴油黄 [204,158,34]，搪瓷，光滑度 150 |
| 材质（LabPBR） | 玻璃钢 [178,150,100]（低频斑驳加一点沿轴向的缠绕纹），肋 [166,138,90]，F0 24；钢盖、底板、起吊耳 [62,66,72]；方接管 [46,49,54]；接口板 [62,66,72]；螺栓 [124,128,133] F0 30。钢件和管道 V2 用同一套深色钢；没有画上去的装饰 |
| 模型 | `models/block/underground_fuel_tank/{gasoline,diesel}.obj/.json`（罐体 + 对应标签），`cell.json`（只有粒子贴图），共用 `underground_fuel_tank.mtl`；`forge:obj`，不用环境光遮蔽 |
| 方块状态 | `blockstates/underground_fuel_tank_{gasoline,diesel}.json`：multipart，所有格挂 `cell`，主格（`along=3, across=1, level=2`）再挂罐体，按 `axis` / `flipped` 转：z / false y 0，x / false y 270，z / true y 180，x / true y 90（后两种 2026-10-08 加）。2026-10-05 由 90 改成 270：加了卸油口以后模型前后不对称，转 90 会让卸油口落在罐的另一头，和卸油口格对不上 |
| 贴图 | `textures/block/underground_fuel_tank{,_s,_n}.png`（2048，7.75 texel/px，1202 个 UV 岛）；可编辑源 `src/main/blockbench/underground_fuel_tank_v1.bbmodel`，贴图 `src/main/blockbench/textures/underground_fuel_tank_v1{,_s,_n}.png` |
| 统计 | 三角形：罐体 5008，每套标签 352；共面 0 |
| 物品栏 | 父模型是对应的方块模型，rotation `[30,225,0]`，scale 0.13，translation `[0,1.589,0]`（加了卸油口后由生成器重新居中） |

## 已知问题 / 以后

- 整个罐由主格一个方块画。主格所在的区块段完全出了视野时，罐的其余部分也会一起不画（罐两头离主格 3.5 格）。
- 原版没有平滑法线光照，圆柱会看出棱面；光影下是圆的。
- 潜油泵、泵井盖、卸油口盖已做（2026-10-05，见 [fuel_station_sump_v1.md](fuel_station_sump_v1.md)）。罐里的油经潜油泵送到加油机，油枪能滋到地上、给地上的油壶和油桶加油；拿着油壶对着打开的卸油口倒油能灌进罐里，手摇泵能从卸油口往外抽（[Fuel Containers V1](fuel_containers_v1.md)，用户 2026-10-08 确认倒油能灌进罐）。还没做：油罐车卸油。
- 被挖掉时油直接丢失，不会洒出来。被子弹打中会漏油（玻璃钢，不打火花）；着火后会烧或爆炸，爆炸时油烧着洒出来，见 [fuel_fire_v1.md](../gameplay/fuel_fire_v1.md) 第二阶段（2026-10-05）。地面上的爆炸隔着土碰不到它。
- 2026-10-08 放进了作者工具（`AuthoringFixtureRegistry` 的格子布局 `FUEL_TANK`：`place_multiblock`，anchor 是底层正中格，facing 指向卸油口那头），见 [minecraft_authoring_mcp_v1.md](../dev/minecraft_authoring_mcp_v1.md)。

## 导出的建筑里（2026-10-09）

导出建筑时油量换成灌油标记（`AflFuelFill`，规则 `underground_tank`）：放下后主格第一次在服务端加载时掷一次，20% 空、否则 5–40%，油种按罐子本身（汽油罐 / 柴油罐）。见 《A1 设计》"导出 NBT 时的状态"（[fuel_stop_a1_design_v1.md](../worldgen/fuel_stop_a1_design_v1.md)）。GameTest PASS（`dev/ExportStateGameTests`、`dev/GasStation01GameTests`，2026-10-10）。
