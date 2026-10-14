# 建筑暗线 V1（Building Wiring V1）

状态（2026-10-14）：
- **第 1 阶段「数据核心和拆除」**：用户提交了（add28f3d "暗线 V1 第 1 阶段"）。用户实机测过，4 路 PASS（第 1.2 节）。
- **第 2 阶段「配电箱模式和供电」已实现，没有提交**（第 5.1 节）：编译通过；布线 GameTest 9/9 通过（新增 `supply`）；A1 结构回归 `GasStation01GameTests` 通过。**没有实机验证**：配电箱界面的 4 路和外部馈线行、互连和未加载状态、Jade、扳手面板的配电箱行，都没在游戏里看过。
- **第 3 阶段「灯光组和开关」已实现，没有提交**（第 5.2 节）：编译通过；布线 GameTest 10/10 通过（新增 `groups`）。**没有实机验证**：墙上开关、无开关瓷灯座、配电箱界面的分组行都没在游戏里看过。
- 设计、取舍和后面各阶段的计划见 [建筑暗线 V1：技术设计](building_wiring_v1_design.md)。本文只写游戏里现在的实际行为。

**暗线现在能供电了，但只对 `WIRED` 配电箱**（第 5.1 节）：
- 新放的配电箱是 `WIRED`，只给暗线接到的灯、插座、应急灯供电，进线盒、电表箱用进线接到它；
- 旧存档、旧模板里的配电箱（A1 加油站、现在「house test」里那个）没有模式字段，是 `LEGACY_AREA`，还按原来的"范围暗线"（`BuildingPowerZone`）供电，墙里的暗线对它不起作用。
- 灯光组和墙上开关（第 5.2 节）：线头分 0 常亮、1–8 灯光组、15 光控；墙上开关扳的是配电箱里记着的组，插座不受影响。线头的组现在只能用开发指令设。

## 1. 用户的要求和这一轮的取舍

- 用户 2026-10-13 确认设计第二稿，要求按第 18 节做第 1 阶段，并且"加一个类似泰拉瑞亚里面放电线的扳手"。
- 我的取舍（用户没有反对）：
  - 工具是**扳手**，铜线只是耗材。设计稿里"拿着绝缘铜线就是布线工具"改成：扳手负责操作；扳手或铜线拿在任意一只手上，都能看见暗线。
  - 操作照泰拉瑞亚：按住右键扫过去就铺，左键剪，蹲下滚轮换回路。设计稿第 7.2 节的"起点 → 终点 → 预览 → 确认"流程没有做，放到第 5 阶段。
  - 透视提前做了一个基础版，不然铺了线看不见。"显示全部"、设备接线标记、选颜色、给线头选组，都还在第 5 阶段。

### 1.2 改成 4 路，颜色就是回路（2026-10-13 晚）

用户实机看过透视后问："为什么现在要弄12路的线？4种颜色，4路不就够了吗"。原来 12 路对应配电箱（Building Power V1）的 12 个分路开关，颜色只按回路号循环（1、5、9 路都是红），所以截图里会出现"第 8 路 · 黄"这种路号和颜色对不上的读法。一栋小屋用不上 12 路。用户确认改成 4 路。

- 第 1 路红、第 2 路蓝、第 3 路绿、第 4 路黄，和泰拉瑞亚的四种线一样，看颜色就知道是哪一路；进线是黑色（入户电缆）。
- 颜色不再单独存：节点的回路号就决定颜色（`WiringNode#colourOf`），原来的颜色位留空（写 0）。工具里"换回路"就是换颜色，第 5 阶段计划里单独的选色也就不做了。
- 回路号：1–4 路，5 = 进线（原来 1–12、13）。蹲下 + 滚轮在 1–4 路和进线之间循环。
- 旧存档（区块数据版本 1）读进来时按颜色并过去：1、5、9 路 → 第 1 路，2、6、10 路 → 第 2 路，以此类推，进线 13 → 进线。工具铺的线都是回路的默认颜色，所以一段线的连接不会断。同一格里两路并到一起时，连线、斜连线取两者的并集，线头先取原来那一路的，没有的再补上（`WiringNode#merge`）。用户说测试世界里还没铺线，这一步只是防万一。
- 配电箱在 `WIRED` 模式下给暗线 4 个分路开关，一路一个（第 5.1 节，2026-10-14 第 2 阶段）；A1 的 `LEGACY_AREA` 不变（照明、插座、外部馈线三路）。
- 用户 2026-10-14 实机 PASS（4 路）。
- GameTest 8/8：`nodes` 查 4 路和颜色对应、进线黑色；`save` 加了版本 1 存档的合并（红 1 和红 5 并成第 1 路、连线和线头都在，蓝 10 到第 2 路，进线不变）。

### 1.1 第一次实机测试后的修改（2026-10-13）

| 用户看到的 | 原因（查过数据和代码） | 改成 |
|---|---|---|
| 屋顶上的线"能摆但是不能拆" | 那一格里有两路红线（第 5 路和第 9 路，颜色一样）。用户选的是第 5 路，准星下画出来的是第 9 路，左键剪的是第 5 路，所以看上去剪不掉 | 透视只画当前回路（第 8 节）；准星格里有别的回路时，面板多一行"选第 N 路"，按中键切过去（第 3 节） |
| 希望屋顶的线顺着坡斜着走 | 1:2 屋顶的上半格和高一层的下半格只隔一条棱，中间两格都是空气，线连不上，铺出来是一段一段的 | 加"斜连线"（第 4.1 节），透视里屋顶的线画在屋面板中间，顺着坡斜着走 |
| 墙和地板的拐角接不上 | 墙上的线和地上的线是分两次铺的，两个线头只隔一条棱（对角），不是邻格 | 新铺的线头和对角的线头自动通过拐角那一格接上，多用 1 个铜线（第 3 节） |
| 视频里只显示准星对着的一段，"切换到某个颜色才显示那个颜色的一整段，但同时也显示其他颜色的线" | 当前选的第 11 路是空的，别的回路只在准星格里画，看上去像只剩准星那一段；切到某一路时那一路全画，别的回路仍在准星格里画 | 同第一行：只画当前回路 |
| 小屋很多方块没有精确选框和 Mesh 碰撞 | 原来是整格或粗略的盒子 | 原木墙、屋顶、门、窗、门廊、烟囱、桌子的选框和碰撞改成跟 Mesh 走，见 [Cabin Exterior V1](../models/cabin_exterior_v1.md) 的"选框和碰撞" |

另外：扳手在主手时，右键永远是铺线（扳手先于方块自己的右键），所以准星下的世界提示（开门、开柜子……）这时都不显示了。

## 2. 物品

| ID | 名称 | 叠放 | 重量 | 说明 |
|---|---|---|---|---|
| `apocalypse_firstlight:insulated_copper_wire` | 绝缘铜线 / Insulated Copper Wire | 64 | 0.1 kg（估计） | 1 个 = 1 m = 一格里一路线。没有配方 |
| `apocalypse_firstlight:wiring_wrench` | 布线扳手 / Wiring Wrench | 1 | 0.35 kg（估计） | 铺线、剪线、换回路。没有配方 |

- 都在创造栏「工业」里，紧跟在 `power_cable` 后面。
- 重量写在 `data/apocalypse_firstlight/item_mass/afl_content_v1.json`，两项都标了 `estimated`。铜线按用户决定 4 取 0.1 kg。
- 两件都是 3D Mesh 物品：铜线是一小卷线圈，见 [Material Meshes V1](../models/material_meshes_v1.md)；扳手见 [Equipment Meshes V1](../models/equipment_meshes_v1.md)。

## 3. 扳手的操作

扳手拿在**主手**上时：

| 操作 | 效果 |
|---|---|
| 右键一格方块 | 把当前回路的线铺进这一格 |
| 右键一个设备 | 把线铺进这个设备的安装格（第 5 节），并给设备接线头 |
| 按住右键扫过去 | 连续铺线。两次铺线相隔不超过 8 tick 算同一笔；同一笔里新的一格和上一格不挨着，就用寻路补上中间的格子（最多 24 格，包围盒外扩 2 格，最多访问 4,000 格）。补不上就只铺这一格 |
| 左键一格方块 | 剪掉当前回路在这一格的线。方块不动，扳手永远不挖方块 |
| 左键一个设备 | 剪掉指向这个设备的线头（线留在墙里，不返还，线头不耗线） |
| 蹲下 + 滚轮 | 换回路：1–4 路和进线循环（换回路就是换颜色）。选择记在玩家身上（存档），不写进物品，所以扳手照样能叠放、交换 |
| 中键（选取方块键） | 准星格（对着设备时是它的安装格）里有别的回路的线时，换到那一路：从当前回路往后找下一路，到头绕回来。没有就什么都不做。拿着扳手时中键永远不会把方块放进快捷栏 |

- 每铺一格新线：
  - 和六个邻格里同一路的线自动连上（双向），也顺着屋顶坡和楼梯和斜上、斜下的那一格连上（第 4.1 节）；
  - 给装在这一格上、还没有线头的设备自动接线头。线头分组一律是 0（常亮）；
  - 进线只接配电箱和进线点（进线盒、电表箱），1–4 路只接配电箱和用电设备。每个设备只接一个线头，配电箱每一路一个。
- **拐角自动接线**（`BuildingWiring#cornerJoin`）：这一笔铺完以后，每一格新线如果是线头（最多一根线出去），并且隔一条棱（两个轴上各差一格）有同一路的另一个线头，两格之间的两个拐角格都没有这一路，两格也不是一条斜连线的两头，就在第一个能铺线的拐角格里铺一格，把两头接上。典型情况：墙上的线铺到墙脚，地板上的线从墙边开始，拐角格是墙脚下的基础。
  - 先试的拐角格：从新线头沿 x、y、z 里排在前面的那条轴挪一步的格子，不能铺线（比如空气）就用另一格。墙和地板的情况，先试的是新线头正上方或正下方那一格：新线头在地板上时上方是屋里的空气，于是用墙脚那一格。
  - 生存模式多扣 1 个铜线；铜线用完了就不接（这一格照样铺）。创造模式不扣。
  - 只看对角，不会跨两格以上去接。
- 线的颜色就是回路（2026-10-13 晚起）：第 1 路红、第 2 路蓝、第 3 路绿、第 4 路黄，进线黑。颜色没有单独的选择。
- 费用：生存模式每新建一格线扣 1 个绝缘铜线，先扣副手，再按背包顺序扣。一笔里要补的格子一起算，铜线不够就整段不铺，动作栏提示"导线不够"。已经有线的格子、连线、线头都不收费。创造模式不扣。
- 剪线返还：生存模式每剪掉一格返还 1 个，先进背包，放不下就掉在脚下。创造模式不返还。
- 不能铺的格子（空气、液体、玻璃、门等，见第 4 节），动作栏提示"这里不能布线"。
- 权限：要能在这里建造（`mayBuild`、`mayInteract`）；剪线还要在够得着的距离内。
- 换回路时动作栏显示"第 3 路 · 绿"、"进线 · 红"。

### 3.1 按键提示

扳手在主手、没打开界面、没隐藏 HUD 时，右上角的按键提示面板（[AFL Overlay UI Style V1](../ui/afl_overlay_ui_style_v1.md) 第 8 节）显示：

| 行 | 内容 |
|---|---|
| 状态行（没有键帽） | 当前回路和它的长度，比如"第 1 路 · 红 · 12 m" |
| 使用键 | 布线 |
| 攻击键 | 剪线 |
| 潜行 + 滚轮 | 换回路 |
| 选取方块键（只在能用时出现） | 选第 5 路 / 选进线 |
| 状态行（只在对着配电箱时出现，第 5.1 节） | 范围供电 · 暗线不生效 / 第 N 路互连 |

- 键帽跟着玩家改过的键位显示。
- 长度是客户端知道的这一路的节点数（1 个节点 = 1 m）：只算同步到客户端的区块（玩家周围半径 2 个区块），线走得更远的部分不算。
- 最后一行只在准星格里有别的回路时出现，写的是按下去会换到的那一路。
- 扳手在主手时，准星下的世界提示（开门、开柜子、插头……，`client/WorldInteractionHint`）都不显示，因为右键只会铺线。

## 4. 哪些格子能铺线

同时满足才能铺：
- 不是空气；
- 没有液体，含水方块也不行；
- 没有方块实体：机器、容器、设备本身都不行；
- 不在 `apocalypse_firstlight:wiring_devices` 里（设备从安装格接线头，线不进设备那一格）；
- 不在 `apocalypse_firstlight:no_wiring` 里；
- 有碰撞形状：台阶、楼梯、屋顶这类不满格的也行。

天然方块（泥土、石头）也能铺，用来埋地下线。

`data/apocalypse_firstlight/tags/blocks/no_wiring.json`：
- 原版标签：`#minecraft:doors`、`trapdoors`、`fence_gates`、`fences`、`leaves`、`ice`、`impermeable`（原版玻璃）；`#forge:glass`、`#forge:glass_panes`（`required: false`）；
- 原版方块：铁栏杆、锁链、脚手架、黏液块、蜂蜜块、基岩、屏障；
- AFL 玻璃幕墙、窗、门、闸门：`storefront_glazing`、`white_oak_double_hung_window`、`commercial_glass_double_door`、`commercial_glass_double_door_black`、`restroom_stall_door`、`checkout_counter_gate`、`enclosure_gate`；
- AFL 栏杆和花格墙：`steel_railing`、`white_oak_porch_railing`、`cmu_screen_wall`；
- 柴炉配件：`stove_pipe`、`stove_heat_shield`；
- 家具：`wooden_table`、`wooden_chair`、`wooden_kitchen_counter`、`wooden_sink_counter`、`modern_office_chair`、`modern_office_desk`、`office_cubicle_partition`、`restroom_partition`、`storage_rack`、`back_bar_shelf`、`checkout_counter`、`checkout_counter_display`。

### 4.1 斜坡：屋顶和楼梯（2026-10-13）

1:2 屋顶和一段楼梯是"走一格、升一格"的：上半格和高一层的下一格只隔一条棱，中间的两个拐角格是空气。这两格之间可以有一条**斜连线**，线顺着坡直接过去。

- 条件（`BuildingWiring#slope`）：两格都在 `apocalypse_firstlight:wiring_slopes` 里，并且两格的 `facing` 都在这一步的水平方向那条轴上（顺着坡走）。所以两片并排的屋顶、横着摆的楼梯之间不会斜着连。
- `data/apocalypse_firstlight/tags/blocks/wiring_slopes.json`：`green_standing_seam_roof`、`galvanized_corrugated_roof`、`rusty_corrugated_roof`、`#minecraft:stairs`。
- 铺线时自动连（和普通连线一样，两头各记一位）。拐角格里已经有这一路的线时不连：那时普通连线已经拐过去了，不要第二条路。
- 剪线、拆方块会同时删掉另一头的斜连线。
- 一头换成了别的方块（还能铺线，比如屋顶换成石头）或者屋顶转了方向：线留着，斜连线两头一起删（`BuildingWiring#checkSlopes`，在改方块钩子和透视同步第一次发区块时检查）。另一头的区块没加载就先不动，等它加载后检查。
- 寻路也能走斜连线（第 10 节）。
- 拐角自动接线不会去接一条斜连线的两头。

## 5. 设备和线头

`data/apocalypse_firstlight/tags/blocks/wiring_devices.json` 列出能接暗线的固定设备：

| 种类 | 方块 | 能接的线 |
|---|---|---|
| 配电箱 `PANEL` | `distribution_panel`、`load_center` | 每一路一个线头，加进线一个 |
| 进线点 `ENTRY` | `service_meter_box`、`generator_inlet_box` | 只接进线 |
| 用电设备 `LOAD` | `wall_outlet`、`industrial_utility_light`、`linear_light`、`emergency_light`、`porcelain_lampholder`、`keyless_lampholder`、`wall_pack`、`canopy_downlight`、`wall_switch` | 只接 1–4 路，一个线头 |

- **安装格**：方块有 `facing` 属性的，是 `facing` 反方向的那一格（挂墙的是背后的墙，朝下吸顶的是天花板）；没有 `facing` 的，是上面一格。
- 线头记在安装格的节点上，不占格子、不耗线。设备拆了，线头留在墙里，换一个装上就接回去。
- 线头带一个分组（第 5.2 节）。墙上开关也是一个 `LOAD`：它不取电，线头的组就是它扳的组。

### 5.1 配电箱模式和供电（第 2 阶段，2026-10-14）

**模式**（配电箱存档字段 `SupplyMode`，值 `wired` / `legacy_area`）：

| 模式 | 谁是 | 供电 |
|---|---|---|
| `WIRED` | 新放的配电箱、负载中心（方块实体新建时就是，第一次存档写进字段） | 只认暗线：没接暗线的灯、插座没电。不登记范围 |
| `LEGACY_AREA` | 读档时没有这个字段的：旧存档、旧模板（A1 加油站） | 原来的范围（`BuildingPowerZone`），完全不变。墙里的暗线对它不起作用 |

- 模式只能明确改：开发指令 `/afl dev panel_mode`（第 9 节）或开发桥 `place_fixture` 的 `supply_mode`。铺线、检测到暗线都不会自动切换。
- 改模式时：断路器全部合上，旧模式登记的东西撤掉，新模式下一 tick 开始登记。总闸、跳闸、储能不变。
- A1 的建造脚本（`tools/afl_minecraft_mcp/fuel_stop_a1_store.mjs`）放配电盘时写明 `supply_mode: 'legacy_area'`，不然重跑会变成 `WIRED`、整个加油站断电。

**求解**（`wiring/WiringSupply`，只有 `WIRED`）：
- 起点：配电箱安装格里，线头指向配电箱、回路号为 N 的节点就是第 N 路的出线。没有这个线头的回路算没接线。
- 沿连线和斜连线做广度优先搜索，只走已加载的区块，每一路最多 4,096 个节点。收集所有线头：设备还装在这个线头上（它的安装格就是这一格）才算；1–4 路收用电设备，进线收进线盒和电表箱。设备拆了、线头空着，就跳过。
- 结果写进这一维度的**设备索引**：设备格子 → (配电箱, 回路, 分组)；进线点 → 配电箱。同一个设备被两个箱子登记时，先登记的算。
- **互连**：一路的搜索碰到另一个配电箱的线头（两个箱子接到了一起），这一路标"互连"，这个箱子不登记这一路的设备，也不给它供电。对方箱子求解时同样碰到这个箱子，也一样。
- **未加载**：线走进没加载的区块时，那边的设备没电，配电箱标"线路未加载"。
- 搜索经过的格子如果已经不能铺线（WorldEdit 快速模式这种绕过了改方块钩子的），线删掉，不返还（设计第 8.4 节）。
- 什么时候重算：布线修订号变了的下一 tick（任何地方铺线、剪线、清线、区块的布线数据加载，`WiringStorage#revision`），平时每 100 tick 一次（新装了设备、区块卸载）。设计稿写的"只把 64 格内的配电箱标脏"没做：任何布线改动都让所有 `WIRED` 配电箱各重算一次。
- 配电箱拆掉、区块卸载、改成 `LEGACY_AREA`：它登记的索引一起撤掉。墙里的线和线头不动。

**设备怎么找电**（`energy/BuildingSupply`，原来的六处查询都换成它）：
1. 先查设备索引：命中而且那个配电箱已加载、是 `WIRED`，就是那一路；
2. 没命中，再按范围找（`BuildingPowerZone.panelServing`），只认 `LEGACY_AREA` 配电箱。

| 设备 | `WIRED` 下有电的条件 | 取电记在 |
|---|---|---|
| 灯（`BuildingLightBlock`：`industrial_utility_light`、`linear_light`、`wall_pack`、`canopy_downlight`、`porcelain_lampholder`） | 总闸合、没跳闸、这一路的断路器合、没有互连、缓冲里有电；拉线灯还要拉绳开着，户外灯天黑才亮（光控照旧） | 第 N 路 |
| 应急灯 | 同上就充电 | 第 N 路 |
| 墙上插座 | 同上；插线板、插头电器照旧插插座 | 第 N 路 |
| 进线盒、电表箱 | 进线连到的配电箱就是送电的对象 | — |

- 招牌字（`ChannelLetterBlock`，也是 `BuildingLightBlock`）不在 `wiring_devices` 里，接不了线头：`WIRED` 下永远不亮，只在 `LEGACY_AREA` 的范围里亮（A1 的招牌照旧）。
- `WIRED` 下设备都按**自己的格子**查索引；`LEGACY_AREA` 下还按原来的位置查范围（外墙上的灯用它挂的那面墙，`wiringPosition`）。
- 灯的取电量、"两个周期的电才点亮"、计划刻的节奏、查不到时亮着的灯等一次再灭，都和以前一样（[Building Lights V1](../models/building_lights_v1.md)）。
- 过载跳闸还是整箱一个规则；每一路的负载分开统计、在界面上显示。

**顶部外部馈线**：
- `WIRED`：不占 4 个断路器，单独一行"外部馈线"，没有开关（点它没反应），只跟总闸和跳闸走；负载算进整箱。
- `LEGACY_AREA`：不变，还是第 3 个断路器。

**界面**（`client/DistributionPanelScreen`，[Building Power V1](../models/building_power_v1.md)）：
- `WIRED`：支路是"第 1 路"到"第 4 路"，颜色就是线的颜色（红、蓝、绿、黄），再加"外部馈线"一行（紫色小色块代替开关）。没有"备用"那一行。互连的那一路，右边写"互连"（琥珀色），开关显示合着。
- 状态词新增"线路互连"和"线路未加载"（琥珀色），排在"无进线"后面、"重复配电盘"前面。
- `LEGACY_AREA`：和以前一样。

**提示**：
- Jade（`compat/jade/DistributionPanelJadeProvider`）：`LEGACY_AREA` 的配电箱显示"范围供电"；`WIRED` 的只在出问题时显示："第 N 路互连"、"线路未加载"。
- 扳手面板（第 3.1 节）：对着配电箱时多一行状态，`LEGACY_AREA` 是"范围供电 · 暗线不生效"，`WIRED` 有互连时是"第 N 路互连"。客户端靠配电箱同步来的 `WiringConflict`（每一路一位，只在同步包里，不存档）。
- 进线盒的 Jade "墙后没有配电盘"改成"没接配电盘"（两种模式都对）。

### 5.2 灯光组和墙上开关（第 3 阶段，2026-10-14）

**灯光组** = (配电箱, 第 N 路, 组 1–8)。一路上可以有几个互不相干的组。线头的分组（`WiringNode` 每个线头 4 位）决定设备跟哪个组：

| 分组 | 灯 | 墙上开关 | 插座、应急灯 |
|---|---|---|---|
| 0 常亮 | 这一路有电就亮 | 什么也不管，只扳自己 | 一律按 0 算 |
| 1–8 | 这个组开着才亮 | 扳它就是扳这个组 | （一律按 0 算） |
| 15 光控 | 这一路有电、天黑（`!level.isDay()`）才亮 | 不能用（按 0 算） | （一律按 0 算） |

- 设备实际跟的组是求解时算的（`WiringSupply#effectiveGroup`），存进设备索引 `Feed(panel, circuit, group, role)`；`role` 是灯 `LIGHT`（所有 `BuildingLightBlock`）、开关 `SWITCH`、别的 `OTHER`（插座、应急灯）。所以**开关关不掉插座**，应急灯照样充电。
- **组的开关状态存在配电箱里**：`DistributionPanelBlockEntity` 的 `GroupOff`（4 路 × 8 位，第 (N − 1) × 8 + g − 1 位；默认全开，全开时不写这个字段）。存档，所以停电以后再来电还是原来的样子。`setMode` 换模式时清零。
- 灯（`BuildingLights.period`）先看组：组关着、或者光控遇上白天，直接是 DARK，不取电；再看这一路有没有电。拉线瓷灯座的拉绳和灯自带的光控（壁灯、雨棚筒灯）叠在上面，都要开着才亮。`LEGACY_AREA` 没有组。

**墙上开关 `wall_switch`**（`block/WallSwitchBlock`，没有方块实体）：
- 北美单联扳动开关，白色尼龙面板，和墙上插座同一块板，实物 × 1.5（面板 69.9 × 114.3 mm、扳柄伸出 17 mm）。用户 2026-10-14 看过 A / B / C 三种概念图后选 A（`design/buildings/forest_start_home_v1/m2_sheets/m2_5_switch_concepts.png`，页面 `m2_source/switch_concepts.html`）。
- 只能挂墙（`facing` 朝外），面板中心在格子里 0.2 m 高：装在地板上面第二格就是离地 1.2 m。
- 方块状态：`facing`、`on`（扳柄朝上 = 开）、`wired`（它管着一个组）、`log_mount`（原木墙上站在垫块上，垫块种类 11）。
- 右键：
  - 管着组的开关：配电箱里这个组翻转（`toggleGroup`），同一组的所有开关一起扳过去，同一组的灯马上重新检查（`BuildingLightBlock#recheck`，一盏灯不用等下一个 40 tick 周期）。
  - 停电也能扳：状态记在配电箱里，来电后按记住的状态亮。
  - 没接线、或者线头是 0 组的开关：只扳自己，什么也不管。
  - 声音暂时是原版拉杆声（要一条 Firefly 录音换掉）。
- 没加载的开关：配电箱下一次求解（布线修订号变了，或者每 100 tick）时，把和组状态不一样的开关扳过去、标上 `wired`；不再管组的开关（剪了线、改成 0 组）去掉 `wired`。
- Jade（`compat/jade/WallSwitchJadeProvider`，只用客户端的方块状态）：开 / 关；没有 `wired` 时加一行"未接线"。按键提示：开灯 / 关灯。
- 挖掘：塑料小件，同插座：空手就能拆（硬度 0.3），不要求工具，不进 `mineable/*`，掉自身（爆炸后不掉）；没有碰撞。
- 模型：`tools/build-power-outlets-v1.mjs`（和插座同一个生成器），开 / 关两个 OBJ，加原木墙垫块上的 `_log` 版本；可编辑源 `src/main/blockbench/wall_switch_v1.bbmodel`。

**无开关瓷灯座 `keyless_lampholder`**：主屋用的灯，靠墙上开关的组开关；见 [Building Lights V1](../models/building_lights_v1.md)「F 无开关瓷灯座」。

**配电箱界面**：`WIRED` 的每一路下面，只列这一路真的有灯或开关用着的组，一组一行：
- 常亮：几盏灯，有插座或应急灯时加"其他 N"；
- 组 N：几盏灯，右边"开 / 关"（只显示，扳要去开关那里）；
- 光控：几盏灯。
- 没用到的组不显示。数据走菜单同步（`DATA_GROUPS` 起，每一路 13 个数：用到的组、关着的组、其他设备数、每组的灯数）。

**怎么设线头的组**：第 5 阶段扳手才有选组。现在用开发指令 `/afl dev wiring group <设备> <组>`（第 9 节）；扳手和 `lay` / `route` 铺出来的线头都是 0 组。

## 6. 拆除和清理

| 怎么没的 | 线路 | 返还 |
|---|---|---|
| 玩家生存模式拆方块 | 删这一格所有回路的线，邻格指向这里的连线一起删 | 节点数 × 1 个铜线，掉在这一格 |
| 玩家创造模式拆方块 | 同上 | 不掉 |
| 扳手剪线（生存） | 只删当前回路在这一格的线，方块不动 | 1 个，进背包，满了掉脚下 |
| 爆炸、活塞、`/setblock`、`/fill`、流体、火、代码里的 `setBlock` | 新方块不能铺线就删 | 不返还 |
| 换成另一种能铺线的方块 | 保留 | — |
| 拆的是设备 | 线和线头都留在墙里 | — |

- 玩家拆方块：`wiring/WiringRemoval#broken`，`BlockEvent.BreakEvent` 最低优先级，被取消了就不处理。
- 其他改方块：`mixin/WiringServerLevelMixin` 注入 `ServerLevel#onBlockStateChange`（`markAndNotifyBlock` 每次改方块都调用它，标志位里有 16 也一样）。这一格没有线就直接返回。
- 不会重复返还：返还数就是这次真正删掉的节点数，后来的那一次（同一 tick 先剪再拆、拆完再走改方块钩子）找不到节点，返还 0。

## 7. 存储

- 每个区块一份：`mixin/WiringLevelChunkMixin` 给 `LevelChunk` 加了 `afl$wiring` 字段（`wiring/WiringChunk`，`Int2LongOpenHashMap`）。
  - 键：区块内 x(4) z(4) y+2048(12) 回路号(4)；
  - 值：一个节点（`wiring/WiringNode`，50 位的 `long`）：回路号 4 位（1–4，5 = 进线；版本 1 是 1–12、13）+ 2 位不用（版本 1 是颜色）+ 连线 6 位 + 线头 6 位 + 6 个线头分组各 4 位（0 常亮、1–8 灯光组、15 光控）+ 斜连线 8 位（第 42–49 位，第 `2 × 水平方向号 + (向上 ? 1 : 0)` 位）。
  - 旧存档里的节点没有第 42 位以上的内容，读进来就是"没有斜连线"，不用转换。
- 存档：`wiring/WiringStorage`，`ChunkDataEvent.Save / Load`，写在区块数据的 `apocalypse_firstlight:wiring` 下：`{v: 1, keys: int[], nodes: long[]}`。没有线的区块不写；读到坏的条目（键和节点的回路号对不上、回路号越界）丢掉。
- 只有完整的 `LevelChunk` 带布线数据，世界生成中的 `ProtoChunk` 不带。
- 每个区块有一个修订号（只在内存里，给透视同步用）。

## 8. 透视（基础版）

**同步**（`wiring/WiringSync`、`wiring/client/WiringView`，包在 `wiring/WiringPackets`，AFL 主频道协议 38 → 39）：
- 扳手或铜线拿在任意一只手上，客户端发 Hello；服务端回当前回路，和玩家周围 5 × 5 个区块（半径 2）的线。
- 之后每 5 tick 检查一次，修订号变了的区块整块重发。
- 离玩家超过 3 个区块的，两边都丢掉，回来时重发。
- 放下扳手和铜线、换维度、退出：客户端全部清空。平时客户端没有任何布线数据。
- 发的是**整列区块**，不是设计稿说的上下各 32 格。
- 每个区块第一次发给某个玩家时，顺便检查一遍里面的线：所在格子已经不能铺线的（比如 WorldEdit 快速模式绕过了改方块钩子），删掉，不返还。

**画法**（`wiring/client/WiringXray`、`WiringRenderTypes`）：
- **只画当前回路**：附近所有的线都画，墙后面的也画，不透明度 0.95。别的回路一概不画（2026-10-13 起；以前准星格里会画别的回路，和当前回路混在一起像断线）。找别的回路的线：对着它按中键（第 3 节）。
- 线是 2 cm 粗的 8 边形圆管，颜色是回路的颜色（红、蓝、绿、黄，进线黑），按朝向打一点固定的明暗。
  - 每个节点有一个**锚点**：一般是格子中心；屋顶格是屋面板厚度的中间（`CabinBlocks.Roof#wireHeight`：下半格离格底 0.15 格，上半格和屋脊格 0.65 格），所以屋顶上的线顺着坡斜着走；
  - 从锚点画到每个连着的邻格（普通连线和斜连线）的锚点的一半（邻格画另一半），所以两格锚点高度不同时这一段是斜的；锚点上有一个稍粗的小方块当接头；
  - 线头从锚点沿线头方向直着伸出去，到离格子中心 0.62 格（伸进设备那一格），末端一个黄铜色的小接线端子；
  - 既没连线、斜连线，也没线头的节点画成一个小短桩。
- 每一路在 x、y、z 上各有一个固定偏移（±1.5 cm、±4.5 cm 四档，加在锚点上），每一格都一样，所以两半对得上。
- 只画 48 格以内的。
- 关深度测试、不写深度，按远近排序后画（`sortOnUpload`），所以墙后面的线和看得见的一样亮，设计稿说的"墙后暗 50 %"没做。
- 在 `AFTER_TRANSLUCENT_BLOCKS` 画；光影的阴影 pass 跳过（`AflShaderCompat.activeShadowPass()`）。
- 每帧直接重画，没有缓存顶点缓冲。性能没测。

## 9. 开发指令

`/afl dev wiring`，只在开发环境注册（`FMLEnvironment.production` 为假），要 2 级权限。代码在 `src/dev/java/com/antaurora/apofirstlight/dev/wiring/WiringCommands.java`。

| 指令 | 作用 |
|---|---|
| `lay <pos> <circuit>` | 铺一格，和扳手一样连线、接线头，不收费 |
| `route <from> <to> <circuit>` | 用寻路铺一段（最多 96 格，外扩 8 格，最多访问 200,000 格） |
| `cut <pos> <circuit>` | 剪一格的一路 |
| `clear <from> [<to>]` | 清空一格或一个范围里的所有线（最多 32,768 格） |
| `read <pos>` | 读回一格：每一路的颜色、连线方向、线头方向和分组 |
| `stats` | 周围 9 × 9 个区块的节点数，和自己扳手当前的回路 |
| `group <device> <group>` | 第 3 阶段：把这个设备的线头改成第 `group` 组（0–15；插座、应急灯只能 0，开关不能 15；设备没有线头时报错）。改了算布线改动，配电箱下次求解就按新组 |
| `feed <pos>` | 这个设备的电从哪来：`WIRED` 配电箱和第几路、分组，或者进线点接的配电箱，或者按范围找到的配电箱；后面写现在有没有电 |

`circuit` 是 1–5，5 = 进线。指令都不返还铜线。

`/afl dev panel_mode <pos> [wired|legacy_area]`（第 2 阶段，同样只在开发环境、2 级权限）：
- 不带模式：读回配电箱的模式；`WIRED` 的再列出上一次求解里每一路接到几个设备（进线是几个进线点）、哪一路互连、有没有线走进没加载的区块，以及每一路用到的组：几盏灯、几个开关、开着还是关着（第 3 阶段）；
- 带模式：改成这个模式（第 5.1 节）。

开发桥 `place_fixture` 多一个参数 `supply_mode`（`wired` / `legacy_area`，`tools/afl_minecraft_mcp/models.mjs` 和 `src/dev/.../authoring/bridge/FixtureAdapter.java`）：只能给配电箱、负载中心用，别的方块报 `SUPPLY_MODE_NOT_A_PANEL`；不写就是新放的默认 `WIRED`。放完返回里带 `supply_mode`。撤销照旧一步。

## 10. 寻路

`wiring/WiringRouter`，服务端 A*，扳手补格和开发指令共用：
- 只走已加载区块里能铺线的格子，起点、终点也要能铺线；
- 每一步走六个邻格之一，或者顺着斜坡走一步（第 4.1 节，一共 14 种走法）；
- 代价：每走一格 1；走同一路已经有的线 +0.2；走已经有别的回路的格子 +0.1；每拐一次弯 +0.4（斜着走一步算它的水平方向，屋顶上"平走一格、斜上一格"交替不算拐弯）；
- 搜索范围、访问上限、路径长度由调用方给（扳手和指令的数值见上面）。

## 11. GameTest

`src/dev/java/com/antaurora/apofirstlight/dev/WiringGameTests.java`，模板 `afl_wiring_tests:wiring_empty`（20 × 8 × 20，开服时生成），命名空间 `afl_wiring_tests`。先关掉客户端再跑：

```bash
./gradlew runGameTestServer --offline -PaflWithoutShaders --init-script src/dev/wiring-gametest.init.gradle
```

init 脚本用一个新的独立世界，跑完检查日志里十个 `[AFL WIRING TEST] PASS <名字>` 都在，少一个就让任务失败。

| 测试 | 内容 |
|---|---|
| `nodes` | 节点打包和拆包（50 位）、4 路的颜色和黑色进线；可布线规则（石头、台阶、泥土可以；空气、玻璃、箱子、门、冰、树叶、含水楼梯不行）；连线双向、同一格多路互不相连；剪线和清格同时删掉邻格的连线，别的回路不受影响；配电箱每路一个线头、灯只接一个、进线不接灯、1–4 路不接进线盒；设备那一格不能铺线；绕过玻璃的寻路（11 格），墙被玻璃封死时找不到路 |
| `save` | 一段跨区块边界的线，真实存档（`ChunkSource.save(true)`）后从区块文件读回，每个区块的节点一致；坏条目被丢掉、其余照常读；版本 1 的存档（12 路）按颜色并成 4 路；没有线的区块不写条目 |
| `survival` | 没有铜线铺不了；生存模式扳手一笔铺 4 格（中间两格靠寻路补齐、连好）扣 4 个；蹲下滚轮换到第 2 路再铺；剪线返还 1 个进背包；拆一格有两路线的方块，邻格的连线断开，加上另一格一共在原地掉 3 个 |
| `creative` | 创造模式铺线不扣、拆方块不掉 |
| `cleanup` | 爆炸、活塞推走、`setBlock` 成空气或玻璃：线删掉、不掉落；换成木板：线保留 |
| `slopes` | 8 个斜连线位各自能存能清、算作出线、不碰别的字段，8 位全开仍在 50 位内；用真的 AFL 屋顶方块搭一段 1:2 屋顶（下、上、高一层的下、上）：上半格到高一层下半格是斜坡，横着不是；寻路正好走这 4 格；铺线后普通连线和斜连线都对；剪掉上半格，两边邻格的连线都没了，再铺回来；高一层的下半格换成石头：线和普通连线留着，斜连线两头都删；三级楼梯一路斜连；拐角格里有这一路时只用普通连线 |
| `corner` | 墙脚和地板之间：生存模式铺地板第一格，自动通过墙脚下的基础接上墙上的线，一共扣 2 个；只剩 1 个铜线时这一格照铺，拐角不接 |
| `shapes` | 原木墙一段：选框到原木外凸（z 方向超出格子），仍是实心（各面能挂东西、挡视线、导红石、挡光），但不窒息，旁边那格不算"会窒息"（否则客户端会把站在那格的玩家推开一格；对照：石头那格算），生物不当它是空地，能铺线；墙角：两端露出的原木头只在两个空着的面伸出去；屋顶下半格是屋面板（屋檐那边低于格底），上半格离格底 0.3 格以上，墙上的上半格填到格底；屋顶底面仍能吸顶装灯；门：不是整格，关着时门洞中间有碰撞，开着时没有；窗：不是整格、生物不穿、不窒息；门廊栏杆：选框不到 1 格高，碰撞 1.5 格；命中网格（2026-10-13 晚）：向下的射线落在下半格屋面的斜面上（法线斜、高度在屋面板里），屋顶格的轮廓有边，水平射线停在原木弧顶（格子外），枪架没有命中网格 |
| `supply` | 第 2 阶段：新放的负载中心是 `WIRED`；第 1 路接一个插座和一个拉线灯座，进线接进线盒：求解结果第 1 路 2 个设备、进线 1 个；插座有电、能取电，灯座点亮；线外面的插座没电；进线盒找到这个配电箱（它自己 tick 一次也找到）；第 1 路断路器分闸，插座和灯座都没电；外部馈线那一行切不了，顶部照样出电；第二个负载中心接到第 1 路上：互连（掩码第 1 位）、插座没电、索引里没有它，进线不受影响；第二个箱子拆掉、随便改一格线以后又有电；改成 `LEGACY_AREA`：索引撤掉，暗线上的插座没电；一间砖顶小屋里的 `LEGACY_AREA` 配电箱按范围给屋里的插座供电（不碰屋外那个），改回 `WIRED` 后没线的那个插座没电；存档写 `SupplyMode: wired`，去掉这个字段读回是 `LEGACY_AREA` |

| `groups` | 第 3 阶段：一个负载中心第 1 路上接两盏无开关瓷灯座（组 1、组 2）、一个拉线瓷灯座（组 1）、一盏光控（15）、两个组 1 的开关、一个插座，另有一个没接线的开关：插座设不了组 1、开关设不了 15、没线头的开关设不了；求解数对（组 1 两盏灯、两个开关，组 2 一盏，光控一盏，其他 1 个，组 3 没用到），接线的开关标上 `wired`、没接的没有；全开时都亮，光控跟昼夜；扳一个开关：组 1 关、两个开关一起扳下、只有组 1 的灯灭、插座照样有电；另一个开关扳回来；拉绳拉灭只灭那一盏；总闸断开时开关照样能扳，`GroupOff` 存档、读回，来电后组 1 还关着、组 2 亮；没接线的开关只扳自己；后来放的开关下一次求解跟上组的状态 |

2026-10-13 第二版结果：8/8 通过。A1 回归 `GasStation01GameTests`：通过（4 个旋转，91,380 项检查，512 种方块状态，邻格更新没有改变任何方块状态）。

2026-10-14 第 2 阶段结果：9/9 通过。`GasStation01GameTests` 通过（同上的数字）。注意它只查 A1 结构的旋转和方块状态，不查供电；`LEGACY_AREA` 的范围供电靠 `supply` 里的小屋那一段查。

2026-10-14 第 3 阶段结果：10/10 通过，服务器正常关闭（约 1 分钟）。第一次跑 10/10 通过，但服务器卡在 "Saving worlds" 不退出：光照引擎的工作线程问配电箱的形状，配电箱的形状要读邻格（接线盒），而邻格所在的区块要等主线程，主线程正在关服。改成配电箱直接回答"天光照下去"（`DistributionPanelBlock#propagatesSkylightDown` = true，不再读形状；配电箱本来就不占满格子，光照结果不变）。这一轮没有跑 `GasStation01GameTests`。

## 12. 已知问题和没做的

- 第 2 阶段没有实机验证：配电箱界面（4 路颜色、外部馈线行、互连、两个新状态词）、Jade、扳手面板的配电箱行，以及暗线供电在真房子里的表现，都只在 GameTest 里查过代码路径。
- 第 3 阶段没有实机验证：墙上开关和无开关瓷灯座的外观、垫块、选框、挖掘，扳开关时的手感，配电箱界面的分组行，Jade 和提示都没看过。
- 墙上开关的声音暂时是原版拉杆声，要一条 Firefly 录音换掉。
- 线头的组只能用开发指令 `group` 设，扳手铺出来的都是 0 组（第 5 阶段加选组）；开发桥也还没有铺线、设组的操作（第 4 阶段要用时再加）。
- 配电箱界面的组只显示开 / 关，不能在界面上扳。
- 光控看的是 `level.isDay()`，不看灯那一格的天光。
- 「house test」里现在的负载中心是旧档、`LEGACY_AREA`：第 4 阶段接线前要先用 `/afl dev panel_mode` 改成 `wired`。
- 任何布线改动都让所有 `WIRED` 配电箱各重算一次（没按 64 格范围标脏）；一栋房子几百个节点不算什么，大量配电箱时的开销没测。
- 新装一个设备到已有线头上，最多 5 秒（100 tick）后才通电；拆掉的设备在下一次求解前还留在索引里（它已经不在了，没有人会来查）。
- 线走进没加载的区块时，那边的设备没电，等区块加载、下一次求解后才恢复。
- 招牌字（`ChannelLetterBlock`）接不了暗线，`WIRED` 房子里不会亮。
- 没有配方，没有音效（要用 Firefly 录一条，不自己合成）。
- 第二版的斜着走的线、只画当前回路、中键选回路、面板的米数和"选第 N 路"、小屋方块的新选框和碰撞，都没实机看过；扳手第一、第三人称的显示变换是估计值。
- 别的回路的线在透视里看不见：两路线走同一格时，只能靠面板上的"选第 N 路"发现。第 5 阶段的"显示全部"会补上。
- 面板上的米数只算客户端周围的区块。
- 4 路（第 1.2 节）用户 2026-10-14 PASS；进线的黑色在暗处、深色原木前面可能不好认（没看过）。版本 1 存档的合并只在 GameTest 里用手写的数据查过。
- Sundial 下透视的线不显示（用户 2026-10-13 实机）。不修：用户在同步开发自己的光影包。原版下正常。
- 拐角自动接线只看"两个线头隔一条棱"：两段不想连的同一路线头正好对角时也会被接上（剪掉拐角那一格就断开）。
- 斜连线只认 `facing` 在坡向上的屋顶和楼梯；屋顶的屋脊格两边都是下半格时，那一段在透视里是平的。
- 线形灯连成一排时，每一节都是一个设备，每一节都要自己的线头。
- 别的模组如果用 `BreakEvent` 只是"问一下能不能拆"（没真的拆），这一格的线也会被清掉（生存模式还会掉铜线）。
- WorldEdit 快速模式改掉的格子，在透视同步第一次发那个区块时、和 `WIRED` 配电箱求解经过时清理；两样都没碰到的格子会一直留着。
- 透视每帧重画、同步整列区块，线很多时的开销没测（第 7 阶段实测）。
- `tools/check-item-mass.mjs` 本来就不通过：它只读 `AflItems.java`，不认 `CabinBlocks` 注册的物品，和这一轮无关。

## 13. 文件

Java 路径前缀：`src/main/java/com/antaurora/apofirstlight/`。

| 文件 | 职责 |
|---|---|
| `wiring/WiringNode.java` | 节点编码：回路、颜色、连线、线头、分组、斜连线 |
| `wiring/WiringChunk.java`、`WiringChunkHolder.java` | 一个区块的节点表、修订号、存读 |
| `mixin/WiringLevelChunkMixin.java` | `LevelChunk` 上的 `afl$wiring` 字段 |
| `wiring/WiringStorage.java` | 区块存档事件 |
| `wiring/BuildingWiring.java` | 可布线规则、斜坡、设备种类和安装格、所有改动（铺、连、斜连、拐角接线、接线头、剪、清格、检查斜连线） |
| `wiring/WiringRouter.java` | A* 寻路（含斜坡走法） |
| `wiring/WiringTool.java` | 扳手服务端：铺、拐角接线、剪、换回路（滚轮和中键）、扣线和返还 |
| `wiring/WiringRemoval.java`、`mixin/WiringServerLevelMixin.java` | 拆除和清理 |
| `wiring/WiringSync.java`、`wiring/WiringPackets.java` | 透视同步、扳手的包 |
| `wiring/client/WiringView.java` | 客户端的布线数据、每一路的长度、某一格里有哪几路 |
| `wiring/client/WiringXray.java`、`WiringRenderTypes.java` | 透视渲染（只画当前回路，屋顶锚点，斜段） |
| `wiring/client/WiringClient.java` | 左键剪线、蹲下滚轮换回路、中键选准星格里的别的回路、按键提示面板（含对着配电箱时的状态行） |
| `block/WallSwitchBlock.java`、`compat/jade/WallSwitchJadeProvider.java` | 第 3 阶段：墙上开关和它的 Jade |
| `block/KeylessLampholderBlock.java` | 第 3 阶段：无开关瓷灯座 |
| `wiring/WiringSupply.java` | 第 3 阶段加了线头的组和设备的角色（`Feed.group / role`、`effectiveGroup`、每组的灯和开关数）。第 2 阶段：`WIRED` 配电箱的求解、设备索引（设备 → 配电箱和回路、进线点 → 配电箱）、互连、未加载 |
| `wiring/WiringStorage.java`（`revision()`） | 全局布线修订号，配电箱据此重算 |
| `energy/BuildingSupply.java` | 设备找电的统一入口：先暗线索引（`WIRED`），再范围（`LEGACY_AREA`） |
| `energy/BuildingLights.java`、`energy/PowerPlugs.java`、`blockentity/EmergencyLightBlockEntity.java`、`GeneratorInletBoxBlockEntity.java`、`ServiceMeterBoxBlockEntity.java`、`block/BuildingLightBlock.java`、`PorcelainLampholderBlock.java` | 原来的六处查询，改走 `BuildingSupply` |
| `blockentity/DistributionPanelBlockEntity.java` | `SupplyMode`、两种模式的断路器、`circuitLive / drawCircuit`、求解时机、`WiringConflict` 同步；第 3 阶段 `GroupOff`、`toggleGroup`、开关对齐（`alignSwitches`）、界面的分组数据 |
| `block/DistributionPanelBlock.java`（`propagatesSkylightDown`） | 第 3 阶段：光照不读配电箱的形状（关服卡死的修复，第 11 节） |
| `client/DistributionPanelScreen.java` | 4 路和外部馈线行、互连、两个新状态；第 3 阶段每一路下面用到的组 |
| `compat/jade/DistributionPanelJadeProvider.java` | 范围供电、第 N 路互连、线路未加载 |
| `item/WiringWrenchItem.java` | 扳手物品（右键先于方块自己的右键，所以点灯座、配电箱也是铺线） |
| `client/WorldInteractionHint.java` | 扳手在主手时不显示方块的世界提示 |
| `cabin/CabinBlocks.java`（`Roof#wireHeight`） | 屋顶格里线的高度（透视用） |
| `registry/AflItems`、`AflCreativeTabs`、`client/AflStaticMeshItemClient` | 注册、创造栏、3D 物品绑定 |

资源：
- `data/apocalypse_firstlight/tags/blocks/no_wiring.json`、`wiring_devices.json`、`wiring_slopes.json`；
- 两件物品的 `models/item`、`textures/item`、`geo`、`meshes` 和 Mesh 贴图，可编辑源在 `src/main/blockbench/insulated_copper_wire.bbmodel`、`wiring_wrench.bbmodel`；
- 生成脚本 `tools/build-material-meshes-v1.mjs`（铜线）、`tools/build-equipment-meshes-v1.mjs`（扳手）；
- 语言：`item.apocalypse_firstlight.insulated_copper_wire`、`wiring_wrench`；`message.apocalypse_firstlight.wiring.*`（回路名、四种颜色和进线的黑色、不能布线、导线不够、`length` 回路加米数）；`hint.apocalypse_firstlight.wiring.lay / cut / select / pick / pick_feeder`；第 2 阶段：`message.apocalypse_firstlight.wiring.legacy_panel / conflict`、`screen.apocalypse_firstlight.distribution_panel.kind_4`–`kind_8`、`branch_conflict`、`status_conflict`、`status_partial`、`jade.apocalypse_firstlight.distribution_panel.legacy / conflict / partial`、`config.jade.plugin_apocalypse_firstlight.distribution_panel`；第 3 阶段：`block.apocalypse_firstlight.wall_switch`、`keyless_lampholder`、`hint.apocalypse_firstlight.wall_switch.on / off`、`jade.apocalypse_firstlight.wall_switch.on / off / unwired`、`config.jade.plugin_apocalypse_firstlight.wall_switch`、`screen.apocalypse_firstlight.distribution_panel.group_always / group_n / group_photocell / group_lights / group_others / group_on / group_off`。
- 第 3 阶段的两个方块：`wiring_devices.json` 里加了，战利品表、`item_mass`（无开关瓷灯座 0.22 kg、开关 0.08 kg）；模型由 `tools/build-building-lights-v1.mjs`（F）和 `tools/build-power-outlets-v1.mjs`（开关）生成，可编辑源 `src/main/blockbench/keyless_lampholder_v1.bbmodel`、`wall_switch_v1.bbmodel`；原木墙垫块第 11 种由 `tools/build-cabin-exterior-v1.mjs` 生成。

开发：`src/dev/java/com/antaurora/apofirstlight/dev/wiring/WiringCommands.java`（含 `panel_mode`）、`dev/WiringGameTests.java`、`src/dev/wiring-gametest.init.gradle`、`dev/authoring/bridge/FixtureAdapter.java`（`supply_mode`）、`AuthoringFixtureRegistry.java`（第 3 阶段登记开关和无开关瓷灯座）、`tools/afl_minecraft_mcp/models.mjs`、`fuel_stop_a1_store.mjs`。

## 相关文档
- [建筑暗线 V1：技术设计](building_wiring_v1_design.md)：完整设计和第 2–7 阶段。
- [Building Power V1](../models/building_power_v1.md)：配电箱、电表箱；`LEGACY_AREA` 的范围供电就是它。
- [Building Lights V1](../models/building_lights_v1.md)、[Power Outlets V1](../models/power_outlets_v1.md)、[Generator Inlet V1](../models/generator_inlet_v1.md)。
