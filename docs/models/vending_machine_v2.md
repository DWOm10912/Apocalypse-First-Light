# Vending Machine V2（自动售货机 V2：Mesh + PBR）

状态（2026-10-01）：
- **模型、渲染、灯光、电源接口已重做，用户 2026-10-01 实机 PASS**。第一次看时只有商品和螺旋穿模，同一天改好（见"螺旋和商品"）后用户 PASS。
- `compileJava --offline` PASS（包括 `src/dev`）。GameTest 按规则没有运行。
- 用户 2026-10-01 决定"完全重新设计"（原来 Codex 做的 V1 太丑），要求：保留音效和撬棍砸玻璃动作；砸碎后的锯齿玻璃全部去掉；用电只做灯光。
- 内容（9 格可搜索容器、通用货物）是 2026-10-01 早些时候改的，用户实机 PASS；V2 只改了货道位置和货物缩放。

V2 取代 V1 文档（原 `docs/vending_machine_model.md` 和 `docs/vending_machine_runtime.md`，已删除，仍然有效的内容并入本文）。下面这些 V1 文件已删除（都在 git 里）：
- 方块源模型 `src/main/blockbench/afl_vending_machine_{intact,broken}.bbmodel`、贴图 `src/main/blockbench/textures/afl_vending_machine.png`、5 张预览图 `src/main/blockbench/previews/afl_vending_machine_*.png`；
- 脚本 `scripts/build_vending_machine_blockbench.js`、`capture_vending_machine_previews.js`、`export_vending_machine_runtime.js`、`tune_vending_machine_liner.js`（`scripts/vending-tests.init.gradle` 保留）；
- 运行时模型 `models/block/vending_machine_{body,intact_glass,broken_glass}.json`、贴图 `textures/entity/vending_machine.png`，以及它在 `assets/minecraft/atlases/blocks.json` 里的条目。

## 模型

- 生成器：`tools/build-vending-machine-v2.mjs`（`--check` 检查输出是否最新，`--preview DIR` 只把 geo / sidecar / 贴图写到别处看）。
- 输出：
  - 可编辑源 `src/main/blockbench/vending_machine.bbmodel` 和 `src/main/blockbench/textures/vending_machine{,_s,_n}.png`；
  - 运行时 `geo/vending_machine.geo.json`、`meshes/vending_machine.aflmesh.json`、`block_mesh_profiles/vending_machine.json`、`textures/block/vending_machine{,_s,_n}.png`；
  - 物品模型 `models/item/vending_machine.json`（`builtin/entity`，各视角由生成器算好）、方块模型 `models/block/vending_machine.json`（只有粒子贴图）。
- Pure Mesh，512 atlas，3.75 texel/px，42 个部件，共 2352 个三角面：

| 骨骼 | 内容 | 三角面 |
|---|---|---:|
| `body` | 柜体、窗框、货道、螺旋的后段、支付区、取货口、电源接口 | 1692（其中螺旋后段 576） |
| `coil_0` … `coil_11` | 每条货道螺旋的前段（商品站的位置），见"螺旋和商品" | 各 48 |
| `glass` | 一整片玻璃（半透明层） | 12 |
| `lights` | 灯箱、灯条、显示屏，暗的那套 | 36 |
| `lights_lit` | 同样的几何，亮的那套（geo 里 `neverRender`） | 36 |

- 没有动画。共面重叠检查 0 处。
- 坐标（源 px）：方块底面中心是原点，y 0..32 占上下两格，正面朝 -Z，+X 是正对机器时的**左手边**。NORTH 朝向时方块内 px x = 源 x + 8。

### 布局（正对机器看）

- 柜体 x ±7.8、z −7.8..7.7、高 31.8，底座高 1.4（略往里收）。
- 左边是玻璃窗：门框外沿 x −3.4..7.3、y 7.0..27.2，开口 x −2.8..6.8、y 7.6..26.6；玻璃 z −7.45..−7.35，门框支付区一侧有一个锁扣。
- 窗里是深色内衬和 4 层 × 3 列货道：每层一块托盘，托盘前沿一条空白价签条（不印字），每条货道一根弹簧螺旋。
  - 货道宽 3.2 px，中心 x = 5.2 / 2.0 / −1.2（第 0 列在左）；托盘顶面 y = 7.9 / 12.65 / 17.4 / 22.15。
  - 螺旋是沿着圈走的细带子（宽 0.18 px，两面都画），半径 1.15，螺距 2 px，每圈 8 段，从 z −6.2 到 5.8 共 6 圈，每条 96 个三角面：前 3 圈（z −6.2..−0.2）是这条货道自己的骨骼 `coil_<货道>`，后 3 圈在 `body`。
- 右边是窄支付列（x −7.3..−3.4）：从上到下是小显示屏、4 × 3 按键、投币口、刷卡口、退币口。
- 顶部是空白灯箱（不印字、没有品牌）。
- 窗下是取货口（开口 x −2.6..6.6、y 2.6..6.2，往里 3.6 px），里面一块推板和下沿的金属边。
- 背面：下半格背面中心一个标准电源接口（见"电源接口"）。

### 材质

全部是涂层 / 电介质（LabPBR F0 20，玻璃 10），没有全金属面，没有印刷和标志：

| 材质 | 用在 | Base Color | 光滑度（面 / 倒角） |
|---|---|---|---|
| `coat` | 柜体、正面 | 44, 47, 52（深炭灰粉末涂层，和饮料冷柜一样） | 100 / 120 |
| `trim` | 窗框 | 36, 38, 42 | 112 / 132 |
| `plinth`、`dark`、`slot` | 底座、灯箱底、刷卡器、各种口 | 18–30 | 60–90 |
| `liner` | 窗里内衬 | 58, 62, 68 | 96 / 110 |
| `tray` | 托盘 | 112, 116, 120 | 120 / 140 |
| `label` | 价签条 | 150, 152, 148 | 90 / 100 |
| `wire` | 螺旋 | 168, 170, 172 | 150 |
| `key`、`zinc` | 按键、投币板、锁扣、退币口、取货口边 | 138–158 | 120–150 |
| `flap` | 取货推板 | 52, 55, 60 | 104 / 120 |
| `glass` | 玻璃 | 150, 186, 204，alpha 56 | 235（F0 10） |

### 螺旋和商品（2026-10-01 改）

商品库的商品按 0.76 缩放后宽 3.04 px、高 3.5 px，比螺旋（外径约 2.5 px）大，套不进螺旋里；第一版螺旋整条都画，从商品中间穿出来（用户实机发现穿模，第一排罐子顶上能看到几道白线）。现在：

- 每条货道螺旋的前段单独一个骨骼 `coil_<货道>`（货道 = 层 × 3 + 列，第 0 层在下，第 0 列在左），范围 z −6.2..−0.2，正好盖住商品（前沿 −6.4，后沿 −6.4 + 8 × 0.76 = −0.32）。
- 这条货道有货时不画前段（`VendingMachineBlockEntity#meshPartVisible`；哪些货道有货是渲染器每帧画货物时顺便记下的），后段一直画，所以斜着看商品后面还有螺旋；空货道是完整的螺旋。
- 物品图标里螺旋都是完整的。

### 砸碎后

方块状态 `broken=true` 时 `glass` 骨骼不画，窗口只剩空门框，没有锯齿玻璃残片（用户 2026-10-01 要求去掉）。砸的那一下还是撬棍动作发出的 12 个玻璃粒子。

## 电源接口与灯光（2026-10-01，只有灯光）

- 接口：`tools/afl-power-port.mjs` 的标准接口，下半格背面中心（源 x 0、y 8）。后壁在 z 7.2..7.7，没到方块边界，所以在后壁上开 6.1 × 6.1 px 的方孔，接口板嵌在孔里、往后凸到方块边界（z 8）。上半格没有接口。
- 代码：`VendingMachineBlock` 实现 `AflPowerPortBlock`，`hasPowerPort` 只对下半格的背面返回 true；能量能力只在这一面给（`VendingMachineBlockEntity#getCapability`），只接收不输出。
- 数值在 `data/apocalypse_firstlight/machine_balance/vending_machine.json`（缺失或无效时用同样的默认值）：

| 项目 | 值 |
|---|---:|
| 内部缓冲 | 20 FE（约 1 秒的用量） |
| 最大输入 | 32 FE/t |
| 灯 | 1 FE/t（亮着就耗） |

- 没有压缩机、没有声音（售货机不制冷）。用的是饮料冷柜的 `energy/CompressorAppliance` 的"只有灯"模式（不给启停声音的构造函数），亮灯规则和冷柜一样：缓冲够付灯的电就一直亮；灭了以后要等缓冲充满才重新亮，供电不足时不会一闪一闪；断线后灯最多再亮约 1 秒。
- 亮灯时：
  - 上下两半方块状态 `lit=true`，发出 8 级方块光；
  - 灯箱面板、窗口上沿里面的 LED 灯条、支付区显示屏换成亮的那套，全亮度渲染，贴图 `_s` 带 LabPBR 自发光（灯箱 180、LED 230、显示屏 140，显示屏是暗青色）；
  - 货道里的商品按 14 级方块光渲染，隔着玻璃看是亮的。
- 没电时：暗的那套（灯箱灰、灯条和屏幕近黑），方块光 0，商品按周围环境的亮度渲染。
- LED 灯条在玻璃后面。Sundial 下玻璃后面的自发光会被盖掉的问题，靠通用渲染器在玻璃之后再画一遍自发光部件解决（饮料冷柜那次已修，用户实机 PASS；售货机还没实机看过）。
- 存档保存缓冲电量（`EnergyStored`，另外两个压缩机键恒为 0 / false）；拆掉时缓冲丢失。

## 方块、交互和内容（沿用 V1，除了标出的改动）

- 唯一方块 / 物品 ID：`apocalypse_firstlight:vending_machine`；"黎明启示录 · 家具与设施" Creative Tab（见 `docs/ui/creative_tabs_v1.md`），中英文名称已注册。
- 属性：`facing=north/east/south/west`、`half=lower/upper`、`broken=false/true`，**V2 新加 `lit=false/true`**（两半同步，`VendingMachineBlock#setLit`；上半在 `updateShape` 里也跟着下半）。新取得的机器玻璃完整；破损机器的物品带 `AflBrokenGlass` 标记，重放时两半都维持破损。下半是唯一的方块实体，上半跟随状态；空间不足不放置。
- 撬棍砸玻璃（不变）：主手拿 `apocalypse_firstlight:crowbar` 右键正面完整玻璃，服务端预约这台机器并启动独立第一人称 `smash_glass`；第 12 tick（0.60 秒）重新验证目标后才破碎，两半同步，内容保留。动作共 33 tick（1.65 秒）。侧面、背面、支付区、机顶、取货口不触发。动作期间、取消条件、提示样式见 [crowbar_first_person_smash_v1.md](../crowbar_first_person_smash_v1.md)。
- **玻璃区（V2 改）**：`VendingMachineBlock.frontPoint` 只认窗口开口，NORTH 局部方块坐标 x 5.2..14.8 px、y 7.6..26.6 px（两格合起来算），正面 z −0.02..0.08 格。原来是 V1 玻璃的 4.26..14.33 × 7.78..26.68。
- 音效（不变）：`apocalypse_firstlight:vending_machine_break`，来自用户提供的 `E:/Download/vending_machine_break.ogg`，未转换，SHA256 `19c346082141e16317e1dfdf2a7b363fdaffee05de3d2d748f886d7c98c1ad9a`，1.027483 秒，碎裂起音约 0.04765 秒。起手静音；命中确认时一起启动声音、命中姿势和破碎显示。
- 提示（不变）：玻璃完好时"破坏玻璃"；砸碎后"搜索 / 查看"。都在 `WorldInteractionHint`。
- 内容（不变，2026-10-01 用户实机 PASS）：下半的方块实体是 9 格可搜索容器（3×3 界面，每格 20 ticks / 1 秒，±15%）。玻璃完好时打不开，漏斗和物品能力也拿不到东西；砸碎后瞄准窗口右键打开，之后按搜索框架的规则。世界战利品在第一次服务端 tick 时生成（隔着玻璃一开始就能看出满不满），只是生成，格子仍要搜。玩家放置的不用搜。
- 玻璃后面的货物（[container_goods_v1.md](../gameplay/container_goods_v1.md)）：12 条货道各一种，易拉罐、瓶子、零食袋；显示几条 = 有东西的格数 × 12 / 6，向上取整（6 格以上全摆满）。**V2 改**：
  - 位置跟新货道走（`VendingMachineBlockEntity.laneX / laneY / LANE_FRONT_Z`，源 px：货道中心、托盘顶面、商品前沿 z −6.4）；
  - 缩放从 0.85 改成 0.76：货道宽 3.2 px，商品 4 px 宽 → 3.04 px；高 3.5 px，层间净空 4.45 px；深 6.1 px。商品坐在螺旋里，和真机一样。
- 渲染：`client/VendingMachineRenderer` 先画货物并刷新，再交给通用的 `AflAnimatedBlockMeshRenderer` 画柜体、玻璃、灯（玻璃是半透明层，所以货物要先画完，否则从玻璃外面看会没有玻璃色）。渲染范围是 profile 的 bounds（两格高）。
- 物品：`AflStaticMeshItemRenderer`，用方块自己的 mesh 和贴图，灯是暗的；破损机器的物品（`AflBrokenGlass`）不画半透明层，也就是没有玻璃（`AflStaticMeshItemRenderer#opaqueOnly`，2026-10-01 为售货机加的）。物品视角由生成器算：GUI rotation `[25,225,0]`、translation `[-0.014,-2.883,-1.412]`、scale 0.397，正面朝外。
- 碰撞 / 选框（不变）：每半 [.18,0,.18]..[15.82,16,15.82] px 的柜体盒，砸碎后也不能走进机体。上下任一半被挖走会清理另一半。
- 没有付费系统、开门、worldgen。

## 挖掘及掉落（不变）

硬度 3、爆炸抗性 5；`requiresCorrectToolForDrops()`，`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`（2026-10-01 复查过，标签还在）。钻石 / 下界合金镐生存挖掘任一半只掉 1 台机器，破损状态写进掉落物 `AflBrokenGlass=true`，再次放置保留破损；空手、木 / 石 / 铁镐不掉机器；创造拆除不掉机器。内容按搜索框架掉落一次（看过的格子掉，没搜到的战利品丢失），包括错误工具或外部移除。方块战利品表 `loot_tables/blocks/vending_machine.json` 是空表，玩家拆机掉落在方块代码里。

## 代码与资源

| 文件 | 内容 |
|---|---|
| `tools/build-vending-machine-v2.mjs` | 模型、贴图、geo / sidecar / profile、物品模型 |
| `block/VendingMachineBlock.java` | 两半结构、`broken` / `lit`、`setLit`、电源接口、`frontPoint`、交互、掉落 |
| `blockentity/VendingMachineBlockEntity.java` | 9 格搜索容器、货物、Mesh 宿主（玻璃、两套灯、有货货道的螺旋前段）、只有灯的用电、货道位置 |
| `client/VendingMachineRenderer.java` | 货物 + 通用 Mesh 渲染器 |
| `item/VendingMachineBlockItem.java` | 破损标记、物品渲染 |
| `client/AflStaticMeshItemRenderer.java` | 新增 `opaqueOnly`（指定的物品不画半透明层） |
| `energy/CompressorAppliance.java` | 新增只有灯的模式 |
| `energy/MachineBalanceManager.java` | `vendingMachine()`、`loadLightsOnly` |
| `registry/AflBlocks.java` | 光照等级 `lit ? 8 : 0` |
| `data/apocalypse_firstlight/machine_balance/vending_machine.json` | 用电数值 |
| `interaction/CrowbarSmashAction.java`、`client/CrowbarSmashClient.java`、`client/WorldInteractionHint.java` | 砸玻璃和提示，没改 |
| `src/dev/java/.../dev/VendingMachineGameTests.java` | GameTest，没改；它瞄的点（NORTH 局部 x 0.55 格、y 0.7 / 1.2 / 1.3 格）都在新窗口里 |

## 验证

- `node tools/build-vending-machine-v2.mjs --check`：CHECK OK。
- `compileJava --offline`：PASS。
- 离线渲染（`afl2obj` + 简单光栅器，不是游戏画面）看过正面、两个 3/4 视角、背面和亮灯的样子。
- 实机（用户 2026-10-01）：第一版只有螺旋穿模，改后 PASS。
- 2026-10-01 改写的 GameTest（玻璃完好时锁住、砸碎后上下两半都能打开界面、NBT、掉落）没有运行。V1 时期的 GameTest 运行记录（`build runGameTestServer --offline -I scripts/vending-tests.init.gradle`，综合场景 1/1）只对当时的代码有效。
