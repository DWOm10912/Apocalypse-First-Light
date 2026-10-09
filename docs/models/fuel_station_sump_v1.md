# Fuel Station Sump V1（泵井、卸油口和加油机底槽）

状态（2026-10-05）：**已实现**，`compileJava --offline` PASS。用户实机放过泵井那一套（放在地面上试的）；加油机底槽和演示指令是后加的，只做了离线预览。模型离线预览过：泵井、开着和关着的井盖、两种卸油口盖、底槽。后来用户实机测试过整套。用户 2026-10-08 说明：加油机、顶棚、加油岛、油罐、泵井这一整套之前都实机测试过，没问题；往卸油口倒油罐里的油能给地下罐加油。当时没有逐项记录看过哪些地方（生存挖掘掉落也没有单独的记录）。（这里原来列着一串"没有实机验证"的项目：放置和朝向、撬棍开盖、徒手开卸油口盖、潜油泵抽油、管道连接、底槽、油和电转给加油机、`/dev fuel station`、提示文字和 Jade、掉落、光影效果。）

## 是什么

用户 2026-10-05 定下加油站的地下布置：
- 地下油罐埋两格：地面一层是井盖和卸油口盖，下面一层是泵井和管道，再下面是罐顶；
- 泵井盖用撬棍撬开；撬棍动画先不做，先把模型做进游戏；
- 罐顶的接口装潜油泵；罐尾另开一个卸油口，管道接到地面的卸油口盖。
- 加油机下面加一个 UDC 底槽（同一天定）。加油机底下只有两个格子面，却要接汽油、柴油、电三样，所以由底槽在管道层统一接进来，再往上转给加油机。

```
加油机                [加油机]
地面层   [泵井盖]                  ……  [卸油口盖]   [底槽上格]
泵井层   [潜油泵] → 出油口接管道       [管道 V2]    [底槽下格] ← 两头接油管，侧面接电缆
罐顶     罐的接口格（along 3）    ……  罐的卸油口格（along 6）
```

| 注册 ID | 名称 | 方块类 |
|---|---|---|
| `apocalypse_firstlight:submersible_fuel_pump` | 潜油泵 / Submersible Fuel Pump | `block/SubmersibleFuelPumpBlock` + `blockentity/SubmersibleFuelPumpBlockEntity`（方块实体类型 `submersible_fuel_pump`） |
| `apocalypse_firstlight:pump_manhole_cover` | 泵井盖 / Pump Manhole Cover | `block/FuelSumpCoverBlock`（`Kind.MANHOLE`） |
| `apocalypse_firstlight:fuel_fill_cover_gasoline` | 汽油卸油口盖 / Gasoline Fill Cover | `block/FuelSumpCoverBlock`（`Kind.FILL`） |
| `apocalypse_firstlight:fuel_fill_cover_diesel` | 柴油卸油口盖 / Diesel Fill Cover | 同上 |
| `apocalypse_firstlight:fuel_dispenser_sump` | 加油机底槽 / Dispenser Sump | `block/FuelDispenserSumpBlock` + `blockentity/FuelDispenserSumpBlockEntity`（方块实体类型 `fuel_dispenser_sump`）+ `item/FuelDispenserSumpItem` |

五个物品放在创造标签页 `furniture`，在两种地下油罐之后。

## 潜油泵

- **位置**：放在地下罐接口格的正上方。它从下面那格方块实体的流体能力（朝上的面）抽，也就是地下罐的主格。别的方块实体只要在顶面给出流体能力，也能抽。
- **朝向**：`facing` 是出油口，放置时朝玩家视线方向。
- **接口**：
  - 出油口那一面是 AFL 流体接口，接管道 V2；
  - 对面是 AFL 电源接口，在接线盒上，接电缆。
- **运行**：没有开关，有电就转。数值复用取液泵的 `machine_balance/intake_pump.json`：
  - 每 tick 耗 8 FE，抽 5 mB；
  - 缓冲 2,000 FE，每 tick 最多收 32 FE；
  - 出口缓冲 250 mB，每 tick 往管网推（`FluidPipeTransfer`，每个口每 tick 最多 25 mB）；缓冲不从管道收液体。
  - 以后由加油机来控制它启停。
- **高温液体**：抽到超过 400 K 的液体会被烧毁，和其他普通泵一样（`FluidHeat`）。地下罐只装汽油或柴油，正常用不会遇到。
- **Jade**（`compat/jade/SubmersibleFuelPumpJadeProvider`）显示：
  - 状态：运行中 / 无电力 / 下方没有储罐接口 / 储罐已空 / 出口未接管道或已满；
  - 取液：下面罐里的油；
  - 电量和出口缓冲。

  Jade 设置里有单独的开关"AFL 潜油泵"。
- **没有音效**：埋在地下，暂时不做。
- **碰撞箱**：井壁四面各 0.6 px，加上泵头、出口颈和接线盒。

## 泵井盖

- 地面层的一格：混凝土顶板中间开一个 r 6.6 的圆孔，镶一圈钢圈；下面是混凝土竖井，通到泵井；上面一块圆钢盖，带两道撬口。
- **开关**：主手拿撬棍右键，打开或盖上（`open`），铁活板门的声音，音高 0.8。不消耗撬棍耐久。空手对着关着的井盖，会提示"需要撬棍撬开"。
- **朝向**：`facing` 是放置时玩家的朝向。铰链在这一边（远离玩家那边），所以盖子朝远处掀开，竖起约 76°，会伸出方块上沿。
- **碰撞箱**：
  - 关着时是整格；
  - 开着时中间挖掉 9 × 9 px 的通孔。比玩家窄，人掉不下去，但视线能穿过去瞄准下面的泵。
  - 竖起的盖子没有碰撞。

## 卸油口盖

- 地面层的一格：混凝土顶板，中间一圈油品色的钢圈（汽油红 [166,46,38]，柴油黄 [204,158,34]，和罐上的标签同色）；下面是黑色 HDPE 防溢桶，桶底伸出卸油立管，顶上是同色的管帽；最上面一块小圆钢盖。
- **开关**：空手右键开关，铁活板门的声音，音高 1.15。铰链规则同泵井盖。开着时碰撞箱挖掉防溢桶那一块。
- **接口**：底面是 AFL 流体接口（`hasFluidPort(DOWN)`），管道 V2 从下面接上，往下通到罐的卸油口。
- **进出油**（2026-10-05）：打开时，顺着底面接口下面的管道通到地下油罐（找到的第一个液体容器，最多 64 节管道；`fluid/FuelCanTransfers.throughPipes`）。拿着有油的油壶对着它按住右键，油倒进罐里；上面可以装手摇油泵，从罐里往外抽。盖着时不通。卸油口盖本身仍没有方块实体。见 [Fuel Containers V1](fuel_containers_v1.md)。
- **还没做**：油罐车卸油。

## 加油机底槽（UDC）

- **尺寸**：沿加油岛 2 格、深 2 格（地面层和管道层），正好在加油机两格底下。格子命名同加油机：A 是主格那一列，B 在它顺时针一侧；0 是下格，1 是上格（`cell` = a0 / b0 / a1 / b1）。
- **放置**：点击位置是下层 A 格，朝向朝玩家。如果点击位置往上两格就是加油机的底层格，就自动对齐那台加油机：朝向相同，A、B 两列正好在加油机的 A、B 两列下面。先放加油机、再挖坑放底槽也行。四格都要能放，否则放不下。
- **接口**：都在下层格，也就是管道层：
  - 汽油：下层 A 格的 A 端（`facing` 逆时针那一面），AFL 流体接口，外面一圈红框；
  - 柴油：下层 B 格的 B 端（`facing` 顺时针那一面），AFL 流体接口，外面一圈黄框；
  - 电：下层 A 格的背面（`facing` 反面），AFL 电源接口。
- **转给加油机**：底槽自己不存东西。两个下层格的方块实体按接口把能力转给上方加油机的主格（`FuelDispenserSumpBlock.dispenserAbove`）：
  - 汽油口给加油机的汽油管线，柴油口给柴油管线，都只进不出；
  - 电源口给加油机顶棚灯用的电量缓冲。加油机主格底面原来的电源接口还在，两个都能用。
  - 上面没有加油机时，三个接口什么都不给。
- **加油机这边**（`FuelDispenserBlockEntity`）：汽油、柴油各一个 200 mB 的管线缓冲（`LINE_MB`），只收对应的油，存档时保存。油枪按住右键能把管线里的油滋到地上（2026-10-05，见 [fuel_dispenser_v1.md](fuel_dispenser_v1.md) "滋油"），还不能加进容器。
- **Jade**：
  - 加油机：显示"汽油管线 / 柴油管线：x / 200 mB"，对准任何一格都显示主格的；
  - 底槽：显示"已连接加油机"或"上方没有加油机"。
  - Jade 设置里各有开关"AFL 加油机""AFL 加油机底槽"。
- **结构**：任何一格缺了，其余格自己移除。生存模式用对的工具拆任何一格，掉一个底槽。活塞推不动。每格碰撞是整格。

## 演示结构（`/dev fuel station`，开发用）

用户 2026-10-05 要一个完整的演示：汽油、柴油两个罐，连到加油机。作者工具（MCP）的白名单放不了 63 格的地下罐，所以做成开发指令（OP 2，`src/dev` 的 `dev/fuel/DevFuelStation`），在玩家东边 3 格开始搭：

- 先清空玩家东边 x +1..+11、上下 −4..+4、z −2..+12 的区域。坑不回填，所有部件都露在外面。
- 地面层取玩家脚下那格；离世界底部太近时整套往上抬，保证罐底在世界内。
- 汽油罐在西、柴油罐在东，都沿南北放，各灌 20,000 mB。
- 每个罐上面一台潜油泵，出油口朝北；上面是打开的泵井盖。罐尾（南端）上面一节管道，再上面是卸油口盖。
- 北边是加油岛：两头端头、两段路缘，路缘下面垫原版浅灰混凝土（工业方块目录里没有 AFL 混凝土）。中间是加油机，朝北，下面是底槽。
- 汽油管从汽油泵往北走，再拐进底槽西头；柴油管从柴油泵往北直通底槽东头。
- 一个充满电、放电模式的能量单元，电缆接到两台泵和底槽侧面的电源口。
- 管道和电缆的连接直接按线路写好，不靠放置时的自动判断。

## 挖掘和重量

| 方块 | 挖掘 | 硬度 / 抗性 | 声音 | 重量（`item_mass`，估计） | 堆叠 |
|---|---|---|---|---|---|
| 潜油泵 | `mineable/pickaxe` + `needs_diamond_tool`，`requiresCorrectToolForDrops()` | 5.0 / 6.0 | METAL | 35 kg，`carry/oversized` | 1 |
| 泵井盖 | 同上 | 5.0 / 8.0 | STONE | 12 kg | 64 |
| 卸油口盖（两种） | 同上 | 5.0 / 8.0 | STONE | 4 kg | 64 |
| 加油机底槽 | 同上 | 5.0 / 6.0 | STONE | 30 kg，`carry/oversized` | 1 |

掉落表：都掉自己，要求没被爆炸炸毁（`survives_explosion`）。泵、井盖、卸油口盖都是 `noOcclusion`，遮挡形状为空，所以邻格的面照常画。

## 资产

| 项 | 内容 |
|---|---|
| 生成器 | `tools/build-fuel-station-sump-v1.mjs`：纯 Mesh，用 `cube-slab-mesh-lib`、`afl-fluid-port`、`afl-power-port`；支持 `--check` 和 `--preview DIR` |
| 部件（三角形） | `submersible_fuel_pump/body` 1116<br>`pump_manhole_cover/frame` 464，`lid_closed` / `lid_open` 各 152<br>`fuel_fill_cover/frame_gasoline` / `frame_diesel` 各 744，`lid_closed` / `lid_open` 各 96<br>`fuel_dispenser_sump/body` 688<br>共面 0 |
| 泵（px，格子中心为原点，出油口朝 −Z） | 底法兰 ±5.4，盖住罐接口的螺柱<br>立柱 r 3.0；泵壳 r 4.6；泵盖 r 5.0，6 颗螺栓<br>出油嘴 r 2.0，出口颈 ±5.6（挡住接口板背面和喉管），流体接口在 −Z 面正中<br>接线盒 ±2.6 × ±2.8，电源接口在 +Z 面正中<br>井壁四面，厚 0.6，上下开口 |
| 底槽（px，下层 A 格中心为原点，B 在 +X，正面朝 −Z） | HDPE 槽体 x −7.4..23.4、y −8..23、z ±7.4；钢法兰 x −7.9..23.9、y 22.8..24、z ±7.9，加油机就坐在它上面<br>汽油口在 −X 面、柴油口在 +X 面，都在下层格正中，外面一圈 ±6.3 的油品色框；电源口在 +Z 面正中 |
| 盖子 | 开着的盖子绕铰链转 76°：泵井盖铰链在 z −6.0，卸油口盖在 z −4.6 |
| 材质（LabPBR） | 泵壳 [70,74,80]，泵盖 [56,59,64]，接线盒 [40,42,46]<br>HDPE 井壁和防溢桶 [31,32,34]<br>井盖和钢圈 [86,90,96]，混凝土 [146,144,138]，撬口 [20,21,23]<br>油品色同地下罐标签；接口件用 AFL 接口标准色。没有画上去的装饰 |
| 模型 | `models/block/submersible_fuel_pump/body`<br>`models/block/fuel_dispenser_sump/body` 和只有粒子贴图的 `cell.json`<br>`models/block/pump_manhole_cover/{frame,lid_closed,lid_open,item}`<br>`models/block/fuel_fill_cover/{frame_gasoline,frame_diesel,lid_closed,lid_open,item_gasoline,item_diesel}`<br>都是 `.obj/.mtl/.json`，`forge:obj`，不用环境光遮蔽，共用一张贴图 |
| 方块状态 | `submersible_fuel_pump.json` 按 `facing` 转<br>`fuel_dispenser_sump.json`：a0 格画整个底槽并按 `facing` 转，其它格用 `cell`<br>`pump_manhole_cover.json`、`fuel_fill_cover_{gasoline,diesel}.json`：multipart，底座加上按 `open` 选的盖子，按 `facing` 转 |
| 物品模型 | 泵用整体模型；井盖和卸油口盖是底座加关着的盖子；rotation `[30,225,0]`，scale 0.625<br>底槽：父模型是整个底槽，rotation `[30,225,0]`，scale 0.3，translation `[1.7,-1.23,-2.67]`（把 2 × 2 × 1 的中心挪到格子中心） |
| 贴图 | `textures/block/fuel_station_sump{,_s,_n}.png`（1024，6 texel/px，加了底槽后从 7.75 降下来）<br>可编辑源 `src/main/blockbench/fuel_station_sump_v1.bbmodel`，贴图 `src/main/blockbench/textures/fuel_station_sump_v1{,_s,_n}.png` |

## 已知问题 / 以后

- 潜油泵没有开关、没有音效，有电、有油、出口有地方去就一直抽。
- 卸油口盖：拿油壶倒油能灌进地下罐（2026-10-06，用户 2026-10-08 确认），装上手摇泵能往外抽；还没做油罐车卸油。（原来这里写着"还不能卸油"。）
- 撬棍开井盖没有动画（用户 2026-10-05：先不做）。
- 放置不检查上下关系：泵、井盖、卸油口盖都能放在任何地方，按上面的示意图自己搭。
- 油已经能送到加油机，油枪能滋到地上，也能给地上的油壶和油桶加油（[Fuel Containers V1](fuel_containers_v1.md)，2026-10-05）。
- 演示指令只往东边搭，不随玩家朝向旋转。
