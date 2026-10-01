# Industrial Electrical Box V2（配电箱 V2，可开门搜刮）

状态（2026-09-30）：**已实现，未实机验证**。`compileJava --offline` 一次 PASS。外观只看过离线预览（生成器的 `--preview` 输出）；游戏里的效果、四个朝向、开门动画、3×3 搜索界面和 PBR 都还没看过。

## 定位

- 配电箱**不属于电力系统**：AFL 的工业走线缆，普通电器装电池，配电箱不接电网。
- 它是挂墙的封闭小容器，打开门可以逐格搜刮。现在地堡结构里有 1 个。
- 搜刮出什么（电工废料的战利品表）等电子元件设计好之后再加；接上战利品表不需要改代码。

## 方块与交互

| 项目 | 值 |
|---|---|
| Registry ID | `apocalypse_firstlight:industrial_electrical_box`，不变 |
| blockstate | `facing`（north/east/south/west，门朝向的一面，墙在背后）、`open`、`locked`（默认 `true`；玩家放置时为 `false`）。**去掉了天花板安装**（V1 的 `facing=down`），因为动画 Mesh 运行时只支持水平朝向；地堡里那个本来就挂在墙上（`facing=east`），不受影响 |
| 放置 | 只能点在方块的侧面，背后必须是结实的面 |
| 方块实体 | `IndustrialElectricalBoxBlockEntity`：`RandomizableContainerBlockEntity`，9 格 |
| 渲染 | AFL Animated Block Mesh Runtime（`ENTITYBLOCK_ANIMATED`）。骨骼 `body`、`door`，以及挂在门下的 `latch`（转舌锁）。通道 `open`：10 ticks，`ease_in_out`，门绕铰链竖轴转 -100°；通道 `unlock`：4 ticks，`ease_in_out`，锁柄绕门的法线转 90°（竖直 = 上锁，水平 = 解锁），开门时锁柄随门一起转 |
| 搜索 | 接入 Progressive Container Search，3×3 发射器式布局（`AflContainerSearchLayout.GRID_3X3`），40 ticks/格，±15%，不发翻找噪音 |
| 挖掘与掉落 | 不变：硬度 5、抗性 8、`requiresCorrectToolForDrops()`、`mineable/pickaxe` + `needs_diamond_tool`。用正确工具挖掉、或墙没了，会弹出一个配电箱物品（代码手动弹出，掉落表为空）；被炸掉时不掉方块，掉 0–3 个钢废料（`IndustrialMaterialExplosionDrops`） |
| 内容物 | 所有移除路径（挖掉、爆炸、墙没了、`/setblock`）都走 `onRemove` → `dropContentsOnce`：已揭示的格子掉落，未揭示的 loot 随箱子销毁 |
| 比较器 | 只统计已揭示的格子 |
| 声音 | 方块音效仍是原版 `METAL`。拧锁、开门、关门有专属音效，见"音效" |

| 区域 + 状态 | 提示 | 右键 |
|---|---|---|
| `latch` 或 `door` + 关 + 上锁 | 拧开锁 / Unlock | `locked=false`，锁柄转到水平。上锁时门打不开 |
| `latch` + 关 + 已解锁 | 拧紧锁 / Lock | `locked=true`，锁柄转回竖直 |
| `door` + 关 + 已解锁 | 打开 / Open | `open=true`，播放开门动画 |
| `door` + 开 | 关上 / Close | `open=false`；正在查看的界面全部关闭（`stillValid` 要求 `open`）；关上后保持解锁 |
| `interior` + 开，还有未揭示的格子 | 搜索 / Search | 3×3 逐格搜索界面 |
| `interior` + 开，已全部揭示或是玩家放置的 | 查看 / View | 原版发射器式 3×3 界面（`DispenserMenu`） |

- 开着门时不能操作锁；锁只在关门时起作用。上锁只挡住开门，不影响漏斗等自动化（与原版容器相同）。
- 世界里生成的配电箱（包括地堡那个，结构里没写 `locked` 就取默认值）一开始是锁着的；玩家放置的一开始不锁。
- 玩家放置的配电箱不需要搜索；带 `LootTable` 的世界配电箱第一次接触时进入搜索。
- 地堡结构里的那个没有 `LootTable`，所以打开就是一个空的 9 格容器。以后给它加战利品，需要在结构 NBT 或结构处理器里写入 `LootTable`。

## 3×3 搜索界面

逐格搜索原来只支持 9×1–9×6 的箱子网格。这次新增 3×3 布局，见 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)：
- 格子和背包的位置照原版 `DispenserMenu`。
- 搜索时用原版 `dispenser.png` 背景，标题居中，右上角显示"已揭示/9"。遮罩和转圈的放大镜与箱子网格相同。
- 搜完后再打开，直接用原版 `DispenserMenu`。

## 模型

生成器：`tools/build-industrial-electrical-box-v2.mjs`。
- `--check`：校验输出是否最新。
- `--preview DIR`：只写 geo、Mesh 和贴图到 DIR，用于离线预览。

可编辑源：
- `src/main/blockbench/industrial_electrical_box.bbmodel`
- `src/main/blockbench/textures/industrial_electrical_box{,_s,_n}.png`

运行时文件：
- `geo/industrial_electrical_box.geo.json`
- `meshes/industrial_electrical_box.aflmesh.json`
- `block_mesh_profiles/industrial_electrical_box.json`
- `textures/block/industrial_electrical_box{,_s,_n}.png`
- `data/apocalypse_firstlight/mesh_shapes/industrial_electrical_box.json`
- `models/item/industrial_electrical_box.json`
- 方块模型 `models/block/industrial_electrical_box.json` 只剩粒子贴图；blockstate 所有状态都指向它

V1 的 cube 模型和 32×32 贴图已被替换。

坐标：单位 px，原点在方块底面中心，正面朝 -Z，墙在 z = +8，+X 是站在正面时的左手边。

**620 个三角面**，14 个 Mesh part，没有共面重叠，贴图密度 9 texel/px（512 atlas）；警示牌正面单独一块 44×44 的高密度区域。各骨骼面数：body 516、door 52、latch 52。

| 部位 | 几何 |
|---|---|
| 箱体 | 喷涂钢板箱，10 × 12 × 4 px（V1 是 2 px 厚，加深是为了开门后内部有空间），壁厚 0.4，开口一圈浅灰边框 |
| 门 | 9.5 × 11.5 × 0.5 px 浅灰门板，中间一块凸起的炭灰面板；面板上部是黑底黄闪电的电气警示牌（3.2 px 见方）；左侧（+X）两个合页 |
| 转舌锁 | 右侧，独立骨骼 `latch`，转轴 [-4.3, 7.6, 3.0]（门的法线方向）：圆形锁座 + T 形锁柄（1.1 px 长） |
| 内部 | 镀锌安装板；DIN 导轨上一排 6 个断路器（白色外壳、黑色拨杆）；下面是灰色线槽和黑色端子排 |
| 底部 | 一个电缆接头 |

风格沿用 V1：灰色喷涂箱体、浅灰边框、炭灰面板、黑底黄闪电警示牌。

**Render bounds**（NORTH 方向，方块局部坐标）：`[0.172, 0.072, 0.100, 0.990, 0.891, 1.014]`，包含锁柄转到水平后再随门转开的位置。打开的门向左转出，仍在方块范围内。

## 材质与 PBR（LabPBR）

| 材质 | Base Color | `_s` 光滑度 R / F0 G |
|---|---|---|
| `shell` 箱体 | [110,115,120] | 92 / 20（粉末喷涂，非金属，缎面） |
| `frame` 开口边框、门板 | [150,155,162] | 92 / 20 |
| `panel` 门上面板 | [69,70,79] | 88 / 20 |
| `sign` 警示牌 | 黑 [30,31,34]、黄 [188,172,72]，闪电边缘按距离抗锯齿 | 104 / 20 |
| `hardware` 合页、锁、导轨、电缆接头 | [140,143,147] | 118 / 255（镀锌金属） |
| `plate` 安装板 | [126,130,134] | 86 / 255（镀锌板） |
| `breaker` 断路器外壳 | [196,196,190] | 128 / 20（注塑塑料） |
| `dark` 拨杆、端子排 | [48,49,53] | 104 / 20 |
| `duct` 线槽 | [118,122,126] | 96 / 20（PVC） |

- 箱体和门是喷涂件，按非金属处理；只有五金和镀锌板是金属。
- `_n` 是平直法线，B 通道为 AO：箱体内壁更暗。
- 除警示牌外，没有文字、锈迹或噪点。

## 形状与交互（Mesh Shape Runtime）

以下坐标都是 NORTH 朝向的模型坐标，单位 px。

| 状态 | Physical / Selection | 交互区域 → 锚点 |
|---|---|---|
| closed | 整个箱子 [-5,2,2.7 → 5,14,7.98] | `latch`：锁柄周围 [-4.85,6.85,2.55 → -3.75,8.35,3.5] → `latch` [-4.3,7.6,2.7]；`door`：整个正面 [-5,2,2.6 → 5,14,7.98] → `latch` |
| open | 箱体 [-5,2,4 → 5,14,7.98] + 打开的门 [4.76,2.25,-5.88 → 7.59,13.75,3.53] | `interior`：箱体 → `opening` [0,8,3.8]；`door`：打开的门 → `door_edge` [7.54,7.6,-5.24] |

关门状态下锁柄区域比门区域再往前 0.05 px：交互取视线上最近的区域，所以瞄准锁柄时命中 `latch`，瞄准门的其它位置命中 `door`。上锁时两个区域都执行"拧开锁"，提示都显示在锁柄上。

**注意（2026-09-30 修复）**：运行时只接受比选择框表面最多再远 0.03 格（0.48 px）的区域命中。选择框的正面就是锁柄最凸出的位置（z = 2.7），所以两个区域的正面都必须在它之前。第一版把门区域放在门板表面（z = 3.4），比选择框深 0.7 px，从正面瞄准时门区域永远不算命中，解锁后只能从侧面开门（侧面与选择框共面，所以侧面能开）。现在门区域从 z = 2.6 开始。

## 音效（2026-09-30）

生成器：`tools/build-industrial-electrical-box-sounds-v1.mjs [源目录]`（默认 `E:/Download`，需要 ffmpeg）。响度和混音用共享库 `tools/sound-mix-lib.mjs`，方法与铅箱相同（K 加权、按关键帧对齐、峰值不超过 -1 dBFS）。源素材是用户生成的三段单一事件录音（1 秒，立体声，48 kHz，左右相关度 0.87–0.97）：

| 事件 | 文件 | 源文件（SHA-256 前 16 位） | 对齐 | 成品响度 |
|---|---|---|---|---|
| `industrial_electrical_box_latch`（拧开、拧紧共用） | `sounds/industrial_electrical_box/latch.ogg`（0.30 s） | `ebox_latch.wav` 998ad17092b1f13e | 咔嗒落在 0.19 s，即锁柄 4 tick 转动的末尾 | -26.4 LUFS（储物柜开门 -4 LU） |
| `industrial_electrical_box_open` | `sounds/industrial_electrical_box/open.ogg`（0.91 s） | `ebox_door_open.wav` cbbec9c349ddb9a0 | 合页吱呀在门转动的 0.06–0.4 s；素材里的轻碰落在 0.50 s，即门开到位、停下的那一帧 | -24.6 LUFS（目标：储物柜开门 -2 LU） |
| `industrial_electrical_box_close` | `sounds/industrial_electrical_box/close.ogg`（0.67 s） | `ebox_door_close.wav` a88eadc2bed9fb24 | 门撞上门框落在 0.50 s，即动画最后一帧 | -23.6 LUFS（目标 -22.2，峰值先到上限） |

- 门比储物柜小，所以整体比储物柜低 2 LU，锁的咔嗒声再低一些。开门素材很轻，提了 9.6 dB。
- 播放：服务端，音量 0.8。开关门音高 0.98–1.02，避免把对好的时间点带偏。锁的拧开用音高 1.04、拧紧用 0.96（各再加最多 +0.02 的随机），同一段素材听起来略有区别。
- 字幕："配电箱锁扣转动 / 配电箱打开 / 配电箱关上"。
- 替换了之前临时复用的储物柜开、关门音效。
- 没有实机试听。

## 开发工具

- `AuthoringFixtureRegistry`（作者工具的方块登记表）里，配电箱从"安全装饰、5 个朝向"改为"带容器、4 个水平朝向、挂墙、初始关门且上锁"（`open=false`、`locked=true`）。
- 同时修正了两条过期登记：铅箱的固定属性改为 `open=false`（V2 起已没有 `type` / `waterlogged`）；储物柜的备注从 54 格改为 27 格。

## 需要实机验证

1. 四个朝向下贴墙正确，门朝外；开门方向向左，门不穿墙、不穿箱体。
2. 3×3 搜索界面：遮罩、放大镜、计数是否正确；搜完后变成原版发射器界面；Shift 点击转移正常。
3. 地堡里那个配电箱的朝向和外观。
4. 拧锁：锁柄动画方向、提示（上锁时瞄准整个箱子都提示"拧开锁"）；锁的咔嗒声和锁柄转到位是否同时；开、关门音效是否对上动画。
5. 物品栏图标的大小和角度。
6. 箱子网格的搜索（储物柜、铅箱）在菜单改为按布局摆放后是否仍然一切正常。
