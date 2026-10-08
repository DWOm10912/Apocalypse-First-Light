# Chest Freezer V2（冷冻冰柜 V2：Mesh + PBR、用电、状态灯、可搜索内容）

状态（2026-10-01）：
- **模型和渲染已重做**：Pure Mesh + LabPBR，两片玻璃滑盖由 AFL Animated Block Mesh Runtime 动画。
- **已接入**：标准电源接口（主格背面）、用电和压缩机周期、状态灯（温度显示屏 + 电源指示灯）、悬浮交互提示。
- **寒气（2026-10-01）**：通电时柜里有一层冷雾，开盖时冷雾从开口冒出、翻过围边往下流，见"寒气"。
- `compileJava --offline` PASS（main 源集包含 `src/dev`，GameTest 一起编译通过）；GameTest 按规则没有运行。
- **寒气用户实机 PASS**（2026-10-01）。
- **内容（2026-10-01）**：18 格可搜索容器（逐格搜索，6×3 界面），从开着那半的柜口打开；柜里画出的货物多少跟着装了几格走，见"内容与货物"。没有实机验证。
- 其它（模型、滑盖、接口、用电、状态灯、提示）都没有单独的实机验证记录。
- 不做实时摆放（货架那种）：用户决定用搜索 + 传统界面（2026-10-01）。V1 文档里计划的 "Display Storage V1" 作废。

V2 换了模型、渲染、物品图标，加了用电和提示。盖子状态机、两格结构、碰撞和选中形状、点击检测、滑盖声音、挖掘规则都沿用 V1（见"沿用 V1 的运行逻辑"）。

V2 取代 V1 文档（原 `docs/chest_freezer_model.md`，已删除，仍然有效的内容并入本文）。下面这些 V1 文件已删除（都在 git 里）：
- 方块源模型 `src/main/blockbench/afl_chest_freezer.bbmodel`、贴图 `src/main/blockbench/textures/afl_chest_freezer.png`、12 张预览图 `src/main/blockbench/previews/afl_chest_freezer_*.png`；
- 导出 / 重定时脚本 `scripts/export_chest_freezer_runtime.py`、`scripts/retime_chest_freezer_animations.py`（它们会用 V1 源模型覆盖新的 geo）；
- GeckoLib 动画 `animations/chest_freezer.animation.json` 和贴图 `textures/entity/chest_freezer.png`；
- 渲染类 `ChestFreezerModel`、`ChestFreezerItemRenderer`（`ChestFreezerRenderer` 重写，不再是 GeckoLib）。

> **2026-10-08 起改用电源线**（Power Outlets V1，用户实机 PASS）：背后的标准钢接口和它的开孔都去掉了，主格背面靠外侧的底角换成一个 IEC C14 电源插座，可拆的电源线一头是插在上面的 C13 接头，另一头是三孔插头，4 格长，插在墙上插座或插线板上取电；电缆接不上了。取电走配电盘"插座"那一路。交互：空手潜行右键电器，拿起、拔下或放回插头。见 [Power Outlets V1](power_outlets_v1.md)"插头电器"。下文"电源接口"一节是改之前的记录。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-chest-freezer-v2.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 geo、sidecar 和贴图） |
| 成品 | **3528 个三角面**：柜体 1184（body 1024、每片盖 56、状态灯两套各 24，同一时刻只画一套），货物 24 种摆法共 2344（每种 56–216，一台冰柜最多同时画 8 种）。97 个 Mesh part，没有共面重叠（货物按"同一隔间不会同时出现两种摆法"分三次检查）。V1 是 148 个方块 |
| 贴图 | 512 atlas，2.75 texel/px（加货物前是 3.5），`textures/block/chest_freezer{,_s,_n}.png` |
| 可编辑源 | `src/main/blockbench/chest_freezer.bbmodel`（Free Model，组 `body`、`left_lid`、`right_lid`、`lights`、`lights_lit`、`goods_0_0` … `goods_7_2`）和 `src/main/blockbench/textures/chest_freezer{,_s,_n}.png` |
| 运行时 | `geo/chest_freezer.geo.json`（只有骨骼，覆盖了 V1 的 GeckoLib geo；`lights_lit` 和所有 `goods_*` 标 `neverRender`，物品图标里没有）、`meshes/chest_freezer.aflmesh.json`（两块玻璃在 `translucent` 层，其余 cutout）、`block_mesh_profiles/chest_freezer.json`；方块模型 `models/block/chest_freezer.json` 只有粒子贴图（新贴图） |
| 渲染 | `client/ChestFreezerRenderer`：通用 AFL Animated Block Mesh 渲染器画整台，再在有电时画显示屏文字。由主格（LEFT）的方块实体渲染；两格仍是 `RenderShape.INVISIBLE`。渲染包围盒保持 V1 的手写值（GameTest 在服务端检查它） |
| 物品 | `models/item/chest_freezer.json`：`builtin/entity` + `AflStaticMeshItemRenderer`（盖子关着、状态灯是暗的那套）。各视角由生成器按投影范围居中，正面朝玩家：GUI rotation `[25,225,0]`、translation `[-2.5,-1.063,2.138]`、scale 0.442；手持 `[75,225,0]` / `[0,225,0]`；展示框 `[0,0,0]` |

坐标：和饮料冷柜、充电站同一套（`block/MeshSourceFrame`）。px；X -8..24 横跨两格，主格 = 正对冰柜时的左格 = x 8..24（+X 指向观察者左手）；Y 0..15.8；正面朝 -Z。输出时 X 平移 -16，运行时原点是主格底面中心。

外观沿用 V1 的配色和结构，为光影压暗：
- 暖浅灰柜体、米色顶部围边（缓冲条）、深色底座和四个橡胶脚；
- 白色内胆（和饮料冷柜同一个搪瓷材质）；金属丝隔架：中间一道纵向框，三道横向框，竖杆 0.2 px；
- 两片铝框玻璃滑盖：主格那片在下轨（框底 y 14.3），副格那片在上轨（y 14.9），握边在两片相接的一端；
- 正面左端（主格一侧）控制面板：深色面板、5 条百叶、温度显示屏、电源指示灯；
- 背面主格正中一个标准电源接口。

### 材质（LabPBR）

| 材质 | Base Color | smoothness | F0 |
|---|---|---|---|
| 柜体 `cabinet` | [168,166,158] 暖浅灰喷涂 | 112–130 | 20 |
| 围边 `rim` | [142,136,124] 米色 | 104–124 | 20 |
| 内胆 `liner` | [184,190,194] 白色搪瓷 | 122–134 | 20 |
| 盖框 `lid_frame` | [118,122,126] 涂层铝 | 140–160 | 20 |
| 玻璃 `glass` | [150,186,204] 淡蓝，**alpha 56/255**（饮料冷柜的配方） | 235 | 10 |
| 隔架 `wire` | [196,198,200] | 140 | 20 |
| 显示屏 `screen` / `screen_lit` | [16,22,24] 暗 / [24,70,66] 青色背光，自发光 120 | 200 | 20 |
| 指示灯 `led` / `led_lit` | [34,56,38] 暗 / [120,255,140] 绿，自发光 230 | 170 / 190 | 20 |
| 其它 | 底座、面板、百叶、握边、接口板、插座、橡胶脚 | — | 20 |

## 滑盖

- 状态仍是 V1 的 `lid` 方块状态：`closed` / `left_open` / `right_open`，没有两边同时开。
- 动画通道（profile）：`left_open` 让 `left_lid` 沿 -X 平移 14.15 px（滑到副格上方，在上轨那片下面），`right_open` 让 `right_lid` 沿 +X 平移 14.15 px；14 ticks（0.70 秒，与 `ChestFreezerBlock.ANIMATION_TICKS` 相同），`ease_in_out`。
- 同步：V1 用 GeckoLib `triggerAnim`。V2 和饮料冷柜一样用原版方块事件：
  - `ChestFreezerBlockEntity.startTransition` 发 `level.blockEvent`，事件 1，参数 = 目标状态的序号；
  - `ChestFreezerBlock.triggerEvent` 把事件转给主格方块实体；服务端返回 true，所以会广播给附近玩家；
  - 客户端记下"已宣布的目标"，动画立刻朝它播放；14 tick 后状态提交，和目标一致时清掉记录，之后跟随方块状态。
- `startTransition` / `completeDue` / `ticksUntilCompletion` 的签名不变（GameTest 在用）。

## 电源接口（2026-10-08 已改成电源线，下面是历史记录）

- 位置：主格背面正中（源坐标 x 16、y 8）。副格没有接口。
- 外形和充电站、饮料冷柜相同（`tools/afl-power-port.mjs`）：6 × 6 px 钢板、r 1.95 的插座和触点。后壁本身贴在方块边界上，所以在后壁上开 6.1 × 6.1 px 的方孔（`portHole`），接口板嵌进去，板面正好在边界上。
- 代码：`ChestFreezerBlock` 实现 `AflPowerPortBlock`，`hasPowerPort` 只对主格的背面返回 true，能源线缆接过来时会插上插头。放置冰柜后通知主格周围的方块更新形状，冰柜后面原本就有的线缆也会接上。
- 接口后面是主格方块实体的能量缓冲，只接收、不输出。

## 用电与状态灯

数值在 `data/apocalypse_firstlight/machine_balance/chest_freezer.json`（缺失或无效时用同样的默认值）：

| 项目 | 值 |
|---|---:|
| 内部缓冲 | 20 FE（约 1 秒的用量，断电后很快熄灭） |
| 最大输入 | 32 FE/t |
| 状态灯 | 1 FE/t（有电就耗） |
| 压缩机 | 6 FE/t（运行时；比饮料冷柜的 4 高，冷冻要更低的温度） |
| 压缩机周期 | 运行 600 tick（30 秒）、停 600 tick（30 秒） |
| 平均 | 约 4 FE/t；一台热力发电机（16 FE/t）能带 4 台左右 |

- 逻辑和饮料冷柜共用 `energy/CompressorAppliance`（2026-10-01 从饮料冷柜里抽出来，饮料冷柜行为不变）：
  - 缓冲够付状态灯的电就一直"有电"；断了以后要等缓冲充满才恢复，供电不足时不会一闪一闪；
  - 压缩机在每个运行段开头启动，运行段结束或缓冲付不起 6 FE/t 时停机，一个周期最多启动一次；恢复供电时从周期开头算，所以一来电就启动；
  - 有电时每分钟会听到一次停机声，是周期里的正常停机，状态灯不受影响。
- "有电"保存在主格方块实体（NBT `Powered`），同步给客户端；不是方块状态，冰柜不发光（饮料冷柜亮灯时有 10 级方块光，冰柜只有小屏幕和指示灯，没加）。
- **有电时**：
  - 显示屏和指示灯换成亮的那套（`lights_lit`）：青色背光的屏幕、绿色指示灯，全亮度渲染，贴图 `_s` 带 LabPBR 自发光（屏幕 120、指示灯 230）；
  - `ChestFreezerRenderer` 在屏幕上画 "-18°C"（浅青色 `0xA8F4EC`，字高约 0.75 px，全亮度，原版字体）；阴影 pass 里不画。固定显示，不是真的温度。
- **没电时**：暗的那套，屏幕不显示文字，压缩机停。
- 存档：缓冲电量、周期位置、压缩机是否在转（`EnergyStored`、`CompressorCycle`、`CompressorRunning`，和饮料冷柜同样的键）、`Powered`；拆掉时缓冲丢失。
- 还没有玩法效果（腐败 / 搜刮系统以后做）。

## 压缩机声音

复用饮料冷柜的三段声音（[Beverage Cooler V2](beverage_cooler_v2.md) "压缩机声音"），音高压低到 0.9，听起来更沉：

| 事件 | 用法 | 音高 | 可听半径 |
|---|---|---|---|
| `beverage_cooler_compressor_start` | 压缩机启动（服务端播放） | 0.88–0.92 | 10 格 |
| `beverage_cooler_compressor_stop` | 压缩机停机（服务端播放） | 0.88–0.92 | 10 格 |
| `beverage_cooler_compressor_loop` | 运行中循环，客户端 `client/BlockLoopSoundController` 按同步的"压缩机在转"状态播放 | 0.9 | 8 格 |

- 位置：主格低处正中（源坐标 16, 3, 0），在控制面板后面。
- `BlockLoopSoundController` 的每个声源现在带自己的音高（充电站、饮料冷柜都是 1.0）。
- 字幕沿用饮料冷柜的"冷柜压缩机启动 / 停止 / 运转"。
- 没有实机试听。

## 内容与货物（2026-10-01）

用户决定：冰柜做成可搜索的容器，用传统界面，不做货架那种实时摆放；界面用 6×3（用户觉得 9 列太宽）；柜里画的货物"有货才显示"，摆几样、摆什么每台随机，柜里刷出的东西多就摆得多。

**容器**
- 主格方块实体（`ChestFreezerBlockEntity`，改为继承 `RandomizableContainerBlockEntity`）18 格，接入逐格搜索（[progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)）：世界战利品要逐格搜，每格 40 ticks（2 秒，±15%），搜满约 36 秒；玩家放置的不用搜。
- 界面：AFL 自己的 6×3 布局（`AflContainerSearchLayout.GRID_6X3`，菜单类型 `apocalypse_firstlight:searchable_container_6x3`），左边 3 列、右边 3 列，对应冰柜两半。两半共用一个容器。搜完后仍用这个界面，所有格子可以正常拿放。
- 打开：先滑开一半盖子，瞄准开着那半的柜口（内胆里、盖子轨道以下）右键。盖子滑动中不能打开；盖子关上（提交为 `closed`）时界面自动关闭，搜索暂停。
- 拆掉 / 爆炸 / 失去支撑：已揭示的格子掉在主格位置，没搜过的战利品随冰柜损毁；冰柜本身照旧只掉一台。比较器只算已揭示的格子。漏斗按搜索框架的规则存取。
- 同步给客户端的只有：电量和压缩机状态、有没有电、搜完没有、货物个数；物品本身不同步。
- 世界战利品在冰柜第一次服务端 tick 时就生成（玩家进入模拟距离时），不等第一次打开，这样一开始货物就能按实际数量显示。还没有冰柜专用的战利品表，结构里的冰柜暂时是空的。

**货物（看得到的"有货"）**
- 8 个隔间（金属丝隔架分出的左右各 4 格），每个隔间 3 种摆法，都是看不出具体是什么的冷冻货：
  - 纸盒：2–3 层，越往上越小、略微转角；
  - 软袋：2–3 个，大倒角，一层压一层交叉放；
  - 冰淇淋桶：两个并排带盖，60% 再叠一个；
  - 颜色压暗（最亮不超过柜体），不印字、不画图案；每种摆法的尺寸、角度、颜色由生成器按固定种子随机，8 个隔间各不相同。
- 每台冰柜按位置随机：隔间的填充顺序打乱，每个隔间选一种摆法。同一台冰柜在所有客户端、重进存档后都一样。
- 显示几个隔间 = 柜里有东西的格数（没搜过的格子也算）× 2 / 3，向上取整，最多 8（共用规则 `AflContainerGoods.shown`，见 [container_goods_v1.md](../gameplay/container_goods_v1.md)；冰柜不分主题）：1 格 → 1，3 格 → 2，6 格 → 4，9 格 → 6，12 格以上 → 8；空了就不显示。拿走东西时，最后填上的隔间先消失。
- 这样隔着玻璃能看出满不满，看不出装的是什么。这一点和搜索系统"不泄露内容"的原则有出入，是用户要求的（2026-10-01）：数量可见，物品不可见。
- 骨骼 `goods_<隔间>_<摆法>`，隔间 = 列（从副格那端数，0–3）× 2 + 行（0 前、1 后）；显示由 `meshPartVisible` 决定。生成器：`tools/build-chest-freezer-v2.mjs` 的 `GOODS`。

**测试**：开发指令 `/dev container_search spawn apocalypse_firstlight:chest_freezer fill <格数>` 刷出一台装了指定格数测试食物、需要搜索的冰柜；`loot <表>` 用指定战利品表（见搜索文档第 21 节）。

## 交互提示

`WorldInteractionHint` 加了冰柜分支，判定和点击一致（`ChestFreezerBlock.prompt`，和 `use()` 用同一套 `clickTarget` / `inOpenWell`）：
- 盖子关着：瞄准哪半的盖子就显示"打开 / Open"；
- 一半开着：瞄准另一半叠在一起的盖子显示"关上 / Close"；瞄准开着那半的柜口显示"搜索 / Search"（还有没搜的格子）或"查看 / View"，画在那半柜口的中间；
- 盖子正在滑动时不提示（`ChestFreezerBlockEntity.lidMoving()`：服务端是 pending，客户端是已宣布还没提交）；
- 提示画在要移动的那片盖子的握边上方 0.5 px（左盖握边中心源坐标 x 8.25、y 14.8，右盖 x 7.75、y 15.4；开着的盖子按平移 14.15 px 后的位置）。
- `ChestFreezerBlock.prompt` / `Prompt`（原 `promptLid` / `LidPrompt`）。语言键：`hint.apocalypse_firstlight.chest_freezer.open` / `.close` / `.search` / `.view`（打开 / 关上 / 搜索 / 查看）。
- 没有实机验证。

## 寒气（2026-10-01）

用户提出给制冷的冷柜加寒气效果。饮料冷柜不加：它只是冷藏（用户确认）。只有冷冻冰柜有，而且只在通电（`powered`）时有；断电后不再生成，已有的雾自己散掉。

| 情况 | 效果 |
|---|---|
| 一直（盖子关着也有） | 柜底一层很淡的雾，慢慢翻动，隔着玻璃能看到：整个内胆范围内每秒约 2.4 团，每团活 4.5–7 秒 |
| 一半开着 | 开口处的雾（每秒约 4 团，在柜口下面一点慢慢翻动）；冷雾从开口翻过围边（正面每秒约 3 团，背面约 1 团，外端约 0.6 团）：先落到围边顶上，滑到外沿，再贴着柜子外面往下流，到离地 2–6 px 时淡出，基本落不到地 |
| 盖子正在滑开 | 刚露出来的那条开口往上冒一团（整个开盖过程约 16 团），三分之一往正面飘 |

开着的那半按"露出多少"算：主格的盖子往副格方向滑，开口从主格外端往接缝方向变大；副格的盖子反过来。露出的宽度取自 Mesh 动画的当前进度，所以寒气跟画面上的盖子同步。

- 粒子 `apocalypse_firstlight:cold_mist`（`client/ColdMistParticle`）：
  - 贴图 `textures/particle/cold_mist_{0..3}.png`（64×64，白色，形状在 alpha 里，四种略扁的软雾团，平滑噪声，没有颗粒），由 `tools/build-cold-mist-sprites.mjs` 生成（`--check` 校验，`--preview FILE` 出预览）；`particles/cold_mist.json` 列出这四张；
  - 半透明，淡蓝白色（顶点色约 0.88 / 0.94 / 1.0），先淡入、再变大、慢慢下沉并随机飘动，后半段淡出；
  - 有方块碰撞（物理点是 0.02 的小盒子），所以盖子关着时雾被柜壁和盖子挡在里面，落到东西上会向外铺开一点；
  - 画的时候整团往上抬半个尺寸，落在地上的雾躺在表面上，而不是一半插进地里；
  - 不做视锥剔除（物理小盒子比画面小得多，屏幕边上会突然消失；粒子数量少，不影响性能）。
- 生成：`client/ColdMist`，在主格方块实体的客户端 tick 里（`ChestFreezerBlock#getTicker` 的客户端分支，和热力发电机的粒子一样）。玩家 32 格外不生成；粒子设置"最少"时不生成，"减少"时减半。一台冰柜同时存在的雾大约 15–40 团。
- 每种雾的参数（寿命、大小、变大倍数、最浓时的透明度、下沉、阻力、飘动）在 `ColdMist.Kind`：`LAYER`（柜底）、`WISP`（开口）、`SPILL`（翻出围边）、`BURST`（开盖那一下）。
- `block/MeshSourceFrame` 新增 `toWorldDirection`，把源坐标方向（速度）换到世界方向。
- 没有实机验证。光影风险：Sundial 下粒子和玻璃的先后不确定，盖子关着时柜里那层雾可能被玻璃盖掉，或者显得太亮。翻出围边的轨迹只用不带随机飘动的数值模拟核对过。

## 沿用 V1 的运行逻辑

- Registry ID：`apocalypse_firstlight:chest_freezer`；2×1×1；中文 `冷冻冰柜`、英文 `Chest Freezer`；创造栏在"家具与设施"。
- `LEFT` 是正对冰柜时的左格，是唯一的主格（方块实体、渲染、电源）；`RIGHT` 是右格。NORTH 朝向时主格在东、副格在西（`FACING.getCounterClockWise()` 一侧）。
- 放置：`ChestFreezerBlockItem` 检查两格位置、地面支撑、区块和世界边界、权限、可替换、流体和遮挡后一次写入两格，失败回滚。
- 开关：右键盖子的投影区域（`localHit`：主格外端 = 0，接缝 = 16，副格外端 = 32；高度 14.1–16.1 px）。关着时点哪半开哪半；开着一半时点另一半的叠放盖关上；跨侧切换要先关再开。点击后 14 tick 服务端提交两格的 `lid` 状态，动画期间维持上一稳定状态的碰撞和选中形状，重复点击被 pending 拒绝。
- 滑盖声音：每次成功开始开 / 关时，服务端在两格接缝处（离地 0.85 格）播放 `apocalypse_firstlight:chest_freezer_slide`（`sounds/chest_freezer.ogg`，约 0.718 秒），音量 0.8、音高 1.0，可听半径 12 格；字幕"冷冻冰柜滑盖移动"。
- 形状：底、四壁、上沿、中轨和两片薄盖；盖子选中范围各半 X [0,14.25] / [1.75,16]、Z [1.55,14.45]，关着时 Y [14.3,14.8] / [14.9,15.4]。打开的一半不覆盖盖子，另一半是 Y [14.3,15.4] 的叠放盖。以 NORTH 为母版旋转到四个朝向。V2 模型的盖子位置按这些形状做。
- 挖掘（审计未变）：`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool` + `requiresCorrectToolForDrops()`，铁镐及以上掉落。和饮料冷柜同属商店电器，不按工业机器的钻石级默认。挖任一半两半一起移除，只掉一台；创造模式不掉；爆炸受掉落表 `survives_explosion` 控制。内容物另算，见"内容与货物"。
- 开发：`src/dev/java/com/antaurora/apofirstlight/dev/ChestFreezerGameTests.java`（`src/dev/chest-freezer-gametest.init.gradle`）没有改，用到的 API 都保留（没有覆盖内容和搜索）；`AuthoringFixtureRegistry` 的冰柜夹具（固定 `lid=closed`）不变。

## 代码

| 文件 | 内容 |
|---|---|
| `block/ChestFreezerBlock.java` | V1 状态机和形状；新增电源接口、服务端 ticker（战利品、用电）和客户端 ticker（寒气）、`compressorPosition`、`triggerEvent` 转发、`clickTarget` / `inOpenWell` / `prompt`、柜口打开界面、`setPlacedBy`（玩家放置不用搜）、拆除时掉内容、比较器，放置后更新邻居形状 |
| `blockentity/ChestFreezerBlockEntity.java` | 重写：18 格可搜索容器、盖子过渡 + 方块事件、Mesh 宿主（通道、状态灯两套切换、货物）、`CompressorAppliance`、`Powered`、只含状态的同步 |
| `containersearch/AflContainerSearchLayout.java` 等 | 6×3 布局（`GRID_6X3`），菜单类型 `searchable_container_6x3`（`registry/AflMenus`），界面背景（`client/AflContainerSearchScreen`），搜完不换原版菜单（`AflContainerSearch.createMenu`） |
| `energy/CompressorAppliance.java` | 冷柜类电器的缓冲、亮灯滞回、压缩机周期和启停声音、能量 capability、存档（饮料冷柜、冷冻冰柜共用） |
| `energy/MachineBalanceManager.java` | `ApplianceBalance`（原 `BeverageCoolerBalance` 改名，两台共用）、`chestFreezer()`、`chest_freezer.json` 加载 |
| `client/ChestFreezerRenderer.java` | Mesh + "-18°C" |
| `client/BlockLoopSoundController.java` | 冰柜压缩机循环（音高 0.9） |
| `client/WorldInteractionHint.java` | 冰柜提示 |
| `item/ChestFreezerBlockItem.java` | `AflStaticMeshItemRenderer` |
| `client/ColdMist.java`、`client/ColdMistParticle.java` | 寒气的生成和粒子；粒子类型注册在 `registry/AflParticles`（`COLD_MIST`），贴图集在 `client/AflParticleProviders` |

## 待实机验证

- 外观：开光影 / 不开光影，四个朝向；玻璃（Sundial 下玻璃后面的内胆在移动时可能有噪点，和饮料冷柜的已知问题相同）；
- 滑盖动画方向和碰撞一致（打开哪半，哪半的盖子和碰撞一起移开），动画和滑盖声音同步；
- 接线：线缆接到主格背面插上插头，副格背面不接；
- 有电 / 断电：显示屏文字方向和位置、指示灯、断线后约 1 秒熄灭；
- 压缩机声音的音高和位置；
- 提示位置和文字；
- 物品栏图标的居中（不应出现货物）；
- 内容：开盖后瞄准柜口的"搜索 / 查看"、6×3 界面的样子、逐格搜索、盖子关上时界面关闭；货物个数随格数变化、拿空后消失；玩家放置的不用搜；拆掉时掉落；
- 寒气：关盖时柜里的雾层浓淡、开盖时翻出围边往下流的样子、开盖那一下；光影下雾和玻璃的先后；粒子设置"减少 / 最少"。
