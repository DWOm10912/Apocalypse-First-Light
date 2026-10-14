# 建筑暗线 V1（Building Wiring V1）

状态（2026-10-13）：**第 1 阶段「数据核心和拆除」已实现，没有提交**。
- 用户在「house test」里实机测过第一版，提了 5 个问题，当天修了第二版（第 1.1 节）。
- 第二版：编译通过；布线 GameTest 8/8 通过；A1 回归 `GasStation01GameTests` 通过。
- **第二版没有实机验证**。没看过的有：屋顶上斜着走的线、只画当前回路的透视、中键选回路、面板上的米数和"选第 N 路"行、拿扳手时世界提示消失、小屋方块的新选框和碰撞。
- 设计、取舍和后面各阶段的计划见 [建筑暗线 V1：技术设计](building_wiring_v1_design.md)。本文只写游戏里现在的实际行为。

**暗线现在还不供电**。墙里可以铺线、剪线、拆墙返还，透视看得见，但灯、插座、配电箱都还按原来的"范围暗线"（`BuildingPowerZone`）取电。配电箱模式和按暗线供电是第 2 阶段。

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
- 配电箱（第 2 阶段）在 WIRED 模式下只给暗线 4 个分路开关；A1 的 LEGACY_AREA 不变（它的分路由配电箱按范围自动列出）。
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
| 用电设备 `LOAD` | `wall_outlet`、`industrial_utility_light`、`linear_light`、`emergency_light`、`porcelain_lampholder`、`wall_pack`、`canopy_downlight` | 只接 1–4 路，一个线头 |

- **安装格**：方块有 `facing` 属性的，是 `facing` 反方向的那一格（挂墙的是背后的墙，朝下吸顶的是天花板）；没有 `facing` 的，是上面一格。
- 线头记在安装格的节点上，不占格子、不耗线。设备拆了，线头留在墙里，换一个装上就接回去。
- 线头分组这一轮固定是 0（常亮），灯光组和开关是第 3 阶段。

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

`circuit` 是 1–5，5 = 进线。指令都不返还铜线。

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

init 脚本用一个新的独立世界，跑完检查日志里八个 `[AFL WIRING TEST] PASS <名字>` 都在，少一个就让任务失败。

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

2026-10-13 第二版结果：8/8 通过。A1 回归 `GasStation01GameTests`：通过（4 个旋转，91,380 项检查，512 种方块状态，邻格更新没有改变任何方块状态）。

## 12. 已知问题和没做的

- 暗线不供电（第 2 阶段）。
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
- WorldEdit 快速模式改掉的格子，只在透视同步第一次发那个区块时清理；第 2 阶段配电箱求解时会再查一次。
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
| `wiring/client/WiringClient.java` | 左键剪线、蹲下滚轮换回路、中键选准星格里的别的回路、按键提示面板 |
| `item/WiringWrenchItem.java` | 扳手物品（右键先于方块自己的右键，所以点灯座、配电箱也是铺线） |
| `client/WorldInteractionHint.java` | 扳手在主手时不显示方块的世界提示 |
| `cabin/CabinBlocks.java`（`Roof#wireHeight`） | 屋顶格里线的高度（透视用） |
| `registry/AflItems`、`AflCreativeTabs`、`client/AflStaticMeshItemClient` | 注册、创造栏、3D 物品绑定 |

资源：
- `data/apocalypse_firstlight/tags/blocks/no_wiring.json`、`wiring_devices.json`、`wiring_slopes.json`；
- 两件物品的 `models/item`、`textures/item`、`geo`、`meshes` 和 Mesh 贴图，可编辑源在 `src/main/blockbench/insulated_copper_wire.bbmodel`、`wiring_wrench.bbmodel`；
- 生成脚本 `tools/build-material-meshes-v1.mjs`（铜线）、`tools/build-equipment-meshes-v1.mjs`（扳手）；
- 语言：`item.apocalypse_firstlight.insulated_copper_wire`、`wiring_wrench`；`message.apocalypse_firstlight.wiring.*`（回路名、四种颜色和进线的黑色、不能布线、导线不够、`length` 回路加米数）；`hint.apocalypse_firstlight.wiring.lay / cut / select / pick / pick_feeder`。

开发：`src/dev/java/com/antaurora/apofirstlight/dev/wiring/WiringCommands.java`、`dev/WiringGameTests.java`、`src/dev/wiring-gametest.init.gradle`。

## 相关文档
- [建筑暗线 V1：技术设计](building_wiring_v1_design.md)：完整设计和第 2–7 阶段。
- [Building Power V1](../models/building_power_v1.md)：配电箱、电表箱，现在还按范围供电。
- [Building Lights V1](../models/building_lights_v1.md)、[Power Outlets V1](../models/power_outlets_v1.md)、[Generator Inlet V1](../models/generator_inlet_v1.md)。
