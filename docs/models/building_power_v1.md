# Building Power V1：配电盘和电表箱

> **2026-10-08 渲染改动**（渲染性能 V1，用户实机 PASS）：配电盘和电表箱改成 `RenderShape.MODEL`，方块模型改用加载器 `apocalypse_firstlight:static_mesh`。静止、可见、不发光的零件由区块画，正在动的零件、玻璃和发光件仍由方块实体渲染器画，见 [Animated Block Mesh Runtime](../rendering/animated_block_mesh_runtime_v1.md)"静止部件由区块画"。下文里的 `ENTITYBLOCK_ANIMATED` / `INVISIBLE` 和"方块模型只有粒子贴图"是改之前的记录。

状态（2026-10-07）：**第一步写完，`compileJava --offline` 通过，未实机验证**。
- 规则见 [建筑供电规划](../gameplay/building_power_v1_plan.md) 的"V1 定案"。
- 生成器 `--check` 通过，两个模型都没有共面重叠。
- 网格和贴图在离线 three.js 预览里看过：A1 设备间（配电盘关门、开门、拉闸、两个接线盒）和后墙外（电表箱合上、断开、接能量单元）。
- 第二步的前一半（墙上插座、插线板和插头电源线）2026-10-08 做了，见 [Power Outlets V1](power_outlets_v1.md)；配电盘加了"插座"那一路的取电接口 `outletsLive()` / `drawOutlets()`。小电器改插头 2026-10-08 也做了。吸顶灯（照明那一路）2026-10-08 做了，见 [Building Lights V1](building_lights_v1.md)：配电盘加了 `lightingLive()` / `drawLighting()`。

## 这一步做了什么

| 方块 | ID | 作用 |
|---|---|---|
| 配电盘 | `distribution_panel` | 一栋楼的总闸和分路开关。底部进线，顶部出线接线缆设备 |
| 电表箱 | `service_meter_box` | 外墙进户点。底部接电，转送到同一栋楼的配电盘；红手柄是进户总开关 |

两个都是工业设施：镐 + 钻石级，`requiresCorrectToolForDrops()`，掉落自身（`survives_explosion`）。挂墙放置：点在墙的侧面，背后要有结实的面，墙没了会掉下来。配电盘 14 kg、电表箱 10 kg（估计），各叠 4 个，算 `carry/bulky`（单格 5–20 kg 的家具，见 [weight_system_v1.md](../gameplay/weight_system_v1.md)；第一版写成叠 16 个，同日改正），在创造栏工业页电缆后面。

旧的小配电箱 `industrial_electrical_box` 2026-10-07 已删除（[删除记录](industrial_electrical_box_v2.md)）。出生地堡里那个换成了配电盘：挂西墙，离地约 1.15–1.85 m，总闸断开、没有电。

## 配电盘

**外形**（`tools/build-building-power-v1.mjs`，px，正面朝 −Z，墙在 z = +8）：
- 北美商业配电盘，ANSI 61 浅灰喷涂，0.56 × 0.70 m，深 2.4 px（约 15 cm）。
- 门：左边两个合页、右边平嵌门锁，向左开 110°。门背面贴回路卡（只有横线，不写字）。
- 门里是浅灰内盖：上面一个两极总闸，下面左右两列各 6 个分路开关。
- 分路开关是黑色断路器加浅一点的拨杆：合闸时拨杆偏向中间，断开时偏向外侧，开门能看清哪一路断开了。没用到的备用位一直是断开。
- 接口：线管从箱底、箱顶出来，拐到这一格底面中心、顶面中心的小接线盒；接线盒朝外那一面是标准钢接口（`tools/afl-power-port.mjs`）。这是接口规格要求的：插座要在面中心，电缆在方块正中。
  - 顶部接线盒平时不画，上面接了电缆才出现（`meshPartVisible`）。
- 骨骼和通道：`door`（`open`），`main`（`main_off`），`b0`..`b11`（`b0_off`..`b11_off`，b0..b5 是看过去左边一列、从上往下），`port_up`。拨杆的合闸角度写在 profile 的 `rest` 里。

**功能**（`DistributionPanelBlock`、`DistributionPanelBlockEntity`）：
- **进线**：底面接口收电，存进 4,096 FE 的缓冲，每 tick 最多收 512 FE。电表箱送来的电也进这里。
- **总闸**：新放的配电盘总闸是断开的，要在界面里合上。合上并且没跳闸，楼里才有电。
- **分路**：固定三路，照明、插座、外接电缆。第二步加上插座和电器后，插上的大电器会各占一路，最多 12 路。
- **外接电缆**：顶面接口。总闸和这一路都合着时，配电盘像发电机一样把缓冲里的电送进上面那张电缆网，每 tick 最多 512 FE。
- **跳闸**：合着闸、有电进来，但用电把进来的电全吃掉、缓冲一直不到 5%，持续 3 秒就跳闸，也就是电源带不动。电源没电不算跳闸，只是楼里没电。跳闸后总闸先回到断开，再合一次。
- **界面**：右键打开现代风格界面（`DistributionPanelScreen`）。看界面时门是开着的，关掉界面门就关上。
  - 门的音效：用已删除的配电箱的三段音效（`sounds/distribution_panel/{latch,open,close}.ogg`，生成器 `tools/build-distribution-panel-sounds-v1.mjs`），门同样是 10 tick。开门先响门锁再响开门，关门只有关门声。
  - 界面上有：进线功率、缓冲、用电、总闸、范围状态、12 个分路开关，开关一点就切换。
  - 状态都在服务端，界面只发按钮。
  - **尺寸和滚动（2026-10-07 修）**：用户在大界面缩放下看到面板超出屏幕、"外接电缆（顶部出线口）"和开关叠在一起。现在：
    - 宽度固定 300；高度取"内容全高"和"屏幕高 − 16"里小的那个。
    - 放不下时，标题栏下面的内容区可以用滚轮滚动，每格 14 像素，右边有滚动条。超出内容区的部分裁掉不画。
    - 只有内容区里看得见的开关能点。
    - 去掉了底部的接口说明。分路名称缩短为"外接电缆 / Cable"。
    - `compileJava --offline` 通过，没有进游戏看。
- **范围**（`energy/BuildingPowerZone`）：
  - 从配电盘那一列出发，往四周找"有屋顶"的列：配电盘上方 1..12 格里有建筑方块就算。空气、液体、天然地形和植物（标签 `apocalypse_firstlight:not_building_cover`）不算。所以墙、屋顶、贴着的雨篷都在范围里，室外地面、树、山坡不算。
  - 设备要在这些列里、高度在配电盘下 6 格到上 12 格之间。
  - 超过 4,096 列（空地、洞穴）算"没找到屋顶或范围太大"，暗线不工作，外接电缆照常。
  - 一栋楼只认一个配电盘：第二个显示"这栋楼已经有配电盘"，不工作。
  - 每 5 秒重算一次。
- **存档**：总闸、跳闸、各分路开关、缓冲电量都存在方块实体里，建筑 NBT 会保留。

## 电表箱

**外形**：
- 左边圆形电表（玻璃罩、表盘上 5 个计数小盘和转盘窗，印刷，不写字），在电表座上；
- 右边进户总开关，红手柄在右侧，往上是合上，往下是断开；
- 电表下面一个发电机插座盒，带翻盖；
- 电表和总开关之间一段短管；总开关底下的线管接到这一格底面中心的接线盒，接线盒底面是标准钢接口。
- 骨骼 `body`、`handle`（通道 `off`）。

**功能**（`ServiceMeterBoxBlock`、`ServiceMeterBoxBlockEntity`）：
- 底面接口收电，不存电，直接送进配电盘的进线。要送到哪个配电盘：电表箱背后那面墙所在的列在哪个配电盘的范围里，就是哪个。
- 右键拉上 / 拉下总开关，断开时不收电。
- 记累计经过的电量（电表读数），存档。
- 没有界面。准星提示只写动作：合着时"拉下总开关"，断开时"合上总开关"（2026-10-08 改短；原来带去向和累计读数，用户觉得太复杂，读数以后放 wiki 或 Jade）。电表读数仍然记着、存档。

## 文件

- 生成器：`tools/build-building-power-v1.mjs`（`--check`、`--preview DIR`）；可编辑源 `src/main/blockbench/{distribution_panel,service_meter_box}_v1.bbmodel` 和贴图副本。
- 运行时：`geo/`、`meshes/`、`block_mesh_profiles/`、`textures/block/*{,_s,_n}.png`、`models/block`（只有粒子）、`models/item`（`builtin/entity`）、`blockstates/`。
- 代码：
  - `block/DistributionPanelBlock`、`block/ServiceMeterBoxBlock`；
  - `blockentity/DistributionPanelBlockEntity`、`blockentity/ServiceMeterBoxBlockEntity`；
  - `energy/BuildingPowerZone`；
  - `menu/DistributionPanelMenu`、`client/DistributionPanelScreen`；
  - `item/AflMeshBlockItem`（通用的网格方块物品）；
  - 准星提示在 `client/WorldInteractionHint`；
  - 注册：`AflBlocks`、`AflItems`、`AflBlockEntities`、`AflMenus`、`AflMenuScreens`、`AflBlockEntityRenderers`、`AflCreativeTabs`。
- 数据：
  - 掉落表；`mineable/pickaxe` 和 `needs_diamond_tool`；
  - `tags/blocks/not_building_cover.json`（新）；
  - `item_mass/afl_content_v1.json`；
  - 中英文名、界面文字、提示。
- 建造工具：两个都登记成 SAFE_FIXTURE，挂墙支撑（`ATTACHED_OPPOSITE_FACING`）；电表箱可以指定 `on`。

## 实机验收清单（还没做完）

- 2026-10-08 已确认：A1 里电缆从热能发电机接到电表箱底部，电表箱把电送进配电盘；合闸后"照明"那一路供电，灯亮（用户实机）。
- 2026-10-08：A1 加油区改由配电盘的"外接电缆"供电（用户定的：电表箱通电时加油区一起工作）。电缆从顶面接口出来，沿设备间天花板、穿地面，在地下走到加油区电缆总管，见 [加油区施工记录](../worldgen/fuel_stop_a1_forecourt_build_v1.md)"供电"。同日已写进世界；外接电缆这一路还没实机带过负载。

- 挂墙放置、四个朝向，墙拆掉会掉下来；挖掘要钻石镐，掉落自身。
- 配电盘：右键开界面、门开关、总闸和分路开关的拨杆动作。顶部接电缆后接线盒才出现。
- 测试电源：放一个能量单元，准星对着它输入 `/dev energy fill`（开发指令，`dev/DevEnergyCommands`，2026-10-08），充满并设成放电模式。
- 能量单元 → 电缆 → 配电盘底部；合闸后，顶部出线口 → 电缆 → 冷柜能工作（冷柜第二步才改插头，现在还是钢接口）。
- 电源带不动时跳闸，再合一次恢复。
- 电表箱：接能量单元，配电盘有进线；拉下总开关就断；提示里的读数会涨。
- 范围：A1 店里显示"这栋楼"；放在空地上显示"没找到屋顶"；同一栋楼放第二个配电盘显示不工作。
- 光影和无光影下的外观、物品图标。

## 下一步

1. ~~插座（只装在墙上，双联三孔）和插头电源线~~：2026-10-08 已做，用户实机 PASS（[Power Outlets V1](power_outlets_v1.md)）；
2. ~~冷柜、冰柜、售货机、饮水机改成插头取电（去掉背后的钢接口）~~：2026-10-08 已做，电源线 4 格，用户实机 PASS（[Power Outlets V1](power_outlets_v1.md)）；
3. ~~吸顶灯重做，吸顶自动亮，接到"照明"那一路~~：2026-10-08 已做（方形面板灯、线形灯、应急灯），未实机验证，见 [Building Lights V1](building_lights_v1.md)；
4. A1：设备间换配电盘、后墙外换电表箱（建造脚本 `power` 模式已写好，还没运行），布置插座，接能量单元实测。
