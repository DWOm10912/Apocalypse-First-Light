# Beverage Cooler V2（饮料冷柜 V2：Mesh + PBR）

状态（2026-10-01）：
- **模型和渲染已重做，用户实机确认外观没问题**。用户开着光影截了图：两个朝向，玻璃可见，金属丝隔板的镂空正常，打开的门也正常。音效和交互提示也已实机 PASS。
- **摆放功能（60 格）已实现，用户 2026-10-01 实机确认 PASS**，见"摆放"。
- `compileJava --offline` 每次改动后 PASS（包括 `src/dev`），GameTest 按规则没有运行。

V2 换了模型和渲染，并加上了摆放。门的逻辑、四格结构、碰撞和选中形状、开门点击检测、挖掘规则都沿用 V1（见下面"沿用 V1 的运行逻辑"）。

V2 取代 V1 文档（原 `docs/beverage_cooler_model.md`，已删除，仍然有效的内容并入本文）。下面这些 V1 文件已删除（都在 git 里）：
- 方块源模型 `src/main/blockbench/afl_beverage_cooler.bbmodel` 及其贴图、备份、5 张预览图；
- 构建 / 烘焙脚本 `tools/build-beverage-cooler.bb.js`、`tools/bake-beverage-cooler-runtime.js`；
- GeckoLib 动画 `animations/beverage_cooler.animation.json` 和贴图 `textures/entity/beverage_cooler.png`；
- 渲染类 `BeverageCoolerRenderer`、`BeverageCoolerModel`、`BeverageCoolerItemRenderer`。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-beverage-cooler-v2.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 geo、sidecar 和贴图） |
| 成品 | **1544 个三角面**（body 1152、每扇门 196），23 个 Mesh part，没有共面重叠。V1 是 452 个方块（约 2700 个四边形），其中 5 层金属丝隔板就占 295 个方块 |
| 贴图 | 512 atlas，`textures/block/beverage_cooler{,_s,_n}.png`。左上 448×448 是 2 texel/px 的常规展开；底部一条 508×16 是金属丝隔板的镂空图案，5 层隔板的上下两面共用 |
| 可编辑源 | `src/main/blockbench/beverage_cooler.bbmodel`（Free Model，组 `body`、`left_door`、`right_door`）和 `src/main/blockbench/textures/beverage_cooler{,_s,_n}.png` |
| 运行时 | `geo/beverage_cooler.geo.json`（只有骨骼，覆盖了 V1 的 GeckoLib geo）、`meshes/beverage_cooler.aflmesh.json`（两块玻璃在 `translucent` 层，其余为 cutout）、`block_mesh_profiles/beverage_cooler.json`；方块模型 `models/block/beverage_cooler.json` 只有粒子贴图（原来是黑色混凝土，现在用新贴图） |
| 渲染 | AFL Animated Block Mesh Runtime，由主格（左下格）的方块实体画整台冷柜；四个格子仍是 `RenderShape.INVISIBLE`。通道 `left_open` / `right_open`：8 ticks（0.40 秒，与 `BeverageCoolerBlock.ANIMATION_TICKS` 相同），`ease_in_out`；左门绕 [6.87, 16, -7.495] 转 -95°，右门绕 [-22.87, 16, -7.495] 转 +95°（主格坐标） |
| 物品 | `models/item/beverage_cooler.json`：`builtin/entity` + `AflStaticMeshItemRenderer`（关着门的整台冷柜）。各视角都由生成器按投影范围居中。2026-10-01 修正朝向：V1 的显示角度是给左右镜像的 GeckoLib 模型设的，套在 V2 的 Mesh 上物品栏显示的是背面（用户发现），所以全部转 180°，正面朝向玩家，和原版方块物品一致。GUI 为 rotation `[25,225,0]`、translation `[-1.78,-3.254,0.613]`、scale 0.328；手持 `[75,225,0]` / `[0,225,0]`；展示框 `[0,0,0]` |

坐标：几何按 V1 源模型单位编写（px；X -8..24 横跨两列，Y 0..32，正面朝 -Z，铰链在外侧，左 = 正对冷柜时的左手边 = +X），输出时 X 平移 -16。所以运行时原点是主格的底面中心，第二列在 -X 方向，即 `FACING` 逆时针的一侧，和方块的格子布局一致。

外观沿用 V1：
- 炭灰色柜体，顶部蓝色灯箱（无字、不发光），底部百叶通风口和温控小面板，背面检修板和百叶；
- 白色内胆、后部中间风道、两根隔板立柱、两侧前角的竖条灯罩、顶灯罩（都不发光）；
- 5 层金属丝隔板：前后横梁、两侧边梁、3 根横撑和前面的标签槽是实体；中间的铁丝网是一对贴镂空图案的面（铁丝沿前后方向，间距 0.65 px，粗 0.18 px），不再是 44 根方块；
- 两扇铝框玻璃门：门框带倒角，门框内侧颜色压暗；玻璃内凹在门框里；外侧两个铰链筒，中间门梃上各一根不锈钢拉手。

### 材质（LabPBR）

| 材质 | Base Color | smoothness | F0 |
|---|---|---|---|
| 柜体 `cabinet` | [44,47,52] 炭灰喷涂钢 | 96–118 | 20 |
| 灯箱 `lightbox` | [64,104,138] 蓝色（不发光） | 118 | 20 |
| 内胆 `liner` | [184,190,194] 白色搪瓷 | 122–134 | 20 |
| 隔板框 `rail` / 铁丝 `wire` | [204,208,210] / [208,212,214] 环氧涂层 | 142–156 / 150 | 20 |
| 门框 `door` | [40,43,48] | 100–124 | 20 |
| 玻璃 `glass` | [150,186,204] 淡蓝，**alpha 56/255** | 235 | 10 |
| 拉手、铰链 `metal` | [168,171,175] 不锈钢 | 150–170 | 255 |
| 其它 | 百叶、温控屏、橡胶脚、灯罩、标签条等 | — | 20 |

玻璃沿用步枪红点镜片的做法（`docs/native_guns/transparent_hybrid_mesh_runtime_v1.md`）：
- 半透明层，`entityNoOutline` 渲染，阴影 pass 里跳过；
- smoothness 235，F0 10；
- alpha 高于光影包半透明 0.1 的阈值。红点镜片 alpha 40，冷柜门面积大，取 56，让玻璃看得出来。

光影下的效果和红点一样还没实机验证。

## 音效（2026-10-01）

**可听半径**（2026-10-01）：在 `sounds.json` 里用 `attenuation_distance` 设定（原来是原版默认的 16 格），声音随距离线性变小，到半径处听不到；播放音量都不超过 1，所以半径就是实际范围。开门、关门 10 格。

生成器：`tools/build-beverage-cooler-sounds-v1.mjs [源目录]`（默认 `E:/Download`，需要 ffmpeg）。响度和混音用共享库 `tools/sound-mix-lib.mjs`，方法与收银机、配电箱相同：K 加权，先把素材拉到相同的 100 ms 响度，再按关键帧对齐，峰值不超过 -1 dBFS，输出单声道 48 kHz Ogg Vorbis。左右两扇门共用。

| 事件 | 文件 | 源文件（SHA-256 前 16 位） | 对齐 | 成品响度 |
|---|---|---|---|---|
| `beverage_cooler_door_open` | `sounds/beverage_cooler/door_open.ogg`（0.50 s） | `cooler_door_open.wav` 686d16426f3353a9：0.06 s 手碰门把，0.17 s 密封条脱开的"啵"，门晃动余音到 0.34 s | 剪掉前 0.14 s（手碰门把的声音不能早于点击），"啵"落在 0.05 s，即慢慢起动的门离开密封条的时候 | -25.5 LUFS（目标：配电箱开门 -1 LU） |
| `beverage_cooler_door_close` | `sounds/beverage_cooler/door_close.ogg`（0.87 s） | `cooler_door_close.wav` 6955d69fafa362f4：0.10 s 贴上，0.14 s 密封条吸合，0.30 s 前停稳 | 吸合落在 0.40 s，即关门动画最后一帧；前面 0.3 秒是静音 | -24.5 LUFS（目标：配电箱关门 -1 LU） |

- 播放：服务端，点击开始转门时（`startDoor` 成功后）播放，和门动画同时开始。声音位置在被点的那扇门所在列的中间（离地 1 格）。音量 0.8，音高 0.98–1.02，不让对好的时间点偏移。
- 字幕："饮料冷柜门打开 / 饮料冷柜门关上"（Cooler door opens / closes）。
- 两段素材的左右相关度都在 0.99 以上，转单声道时取左右平均。
- 用户 2026-10-01 实机试听后确认 PASS。
- 以后接上电池、亮灯时，可以再加一段只在有电时循环的轻微压缩机声，现在没做。

## 交互提示（2026-10-01）

V1 / V2 原来没有交互提示：其它可开关的方块走 Mesh Shape 运行时的提示接口，冷柜是四格结构，用自己的点击检测，没接上。现在 `WorldInteractionHint` 里加了冷柜分支，判定顺序和点击一致：
- 先像 `BeverageCoolerDoorInput` 一样用 `BeverageCoolerDoorRaycast` 检测开着的门板（包括伸出本格的部分），命中就显示"关上 / Close"；
- 否则看原版瞄准结果：落在冷柜某一格、并且在 `hitDoor` 的门区域内，按这扇门已提交的状态显示"打开 / Open"或"关上 / Close"。瞄准柜体其它部分不提示。
- 提示画在这扇门的拉手位置（源坐标 x 8.36 / 7.64、y 16.3、z -8）；门开着时按铰链转 ±95° 后的拉手位置。
- 新增 `BeverageCoolerBlock.promptDoor` / `promptAnchor`，供提示使用，和 `hitDoor` 用同一套坐标。
- 门转动的 8 tick 里状态还没提交，提示仍按转动前的状态显示。
- 语言键：`hint.apocalypse_firstlight.beverage_cooler.open` / `.close`。
- 没有实机验证。

## 门动画同步（代码改动）

V1 用 GeckoLib 的 `triggerAnim` 让客户端在点击时开始播放动画，8 tick 后服务端才提交方块状态。V2 改用原版方块事件：
- `BeverageCoolerBlockEntity.startDoor` 发 `level.blockEvent`，事件 1 = 左门、2 = 右门，参数 1 = 开、0 = 关；
- `BeverageCoolerBlock.triggerEvent` 把事件转给主格方块实体；服务端返回 true，所以会广播给 64 格内的玩家；
- 客户端记下"已宣布的门目标"，动画立刻朝它播放；8 tick 后状态提交，和目标一致时清掉记录，之后跟随方块状态；
- 在宣布范围外、或者 chunk 在开门过程中重载的玩家，等到状态提交时才看到门开始动。

方块实体的渲染包围盒保持 V1 的手写值（覆盖两列和门的摆动范围），没有改用 profile 的范围，因为服务端没有 profile 缓存，GameTest 会检查这个包围盒。

## 沿用 V1 的运行逻辑

- `BeverageCoolerBlock`：`FACING`、`PART`（`LOWER_LEFT` / `LOWER_RIGHT` / `UPPER_LEFT` / `UPPER_RIGHT`）、`LEFT_OPEN`、`RIGHT_OPEN`。四个格子的朝向和门状态始终一致；左下格是主格，只有它有方块实体。右列在 `FACING` 的逆时针一侧，上排在下排正上方。
- 放置：`BeverageCoolerBlockItem` 检查四个位置、两个地面支撑、区块和世界边界、权限、可替换、流体和遮挡后一次写入四格，失败回滚。
- 开关门：右键门的区域开始 0.40 秒的动画，8 tick 后服务端提交方块状态、碰撞和静止姿势。每扇门有 pending 标记，动画期间重复点击无效，另一扇门可以同时动。过渡标记不存盘，chunk 在过渡中重载会回到上次提交的状态。
- 开着的门伸出本格，`BeverageCoolerDoorRaycast` / `BeverageCoolerDoorInput` / `BeverageCoolerDoorC2SPacket` 负责在客户端检测门板并发包，服务端复核距离、门板和遮挡。
- 碰撞 / 选中按格子 × 朝向 × 两个门状态缓存：侧壁、后壁、底座、顶部、简化的隔板，关着的门各有一块薄板，开着的门是约 95° 的侧摆薄板，选中额外包含门轴附近的一小块。
- 挖掉任一格会移除全部四格；只有被挖的那一格走掉落表，掉一台冷柜。创造模式不掉。镐子 + 铁级（`requiresCorrectToolForDrops`、`mineable/pickaxe`、`needs_iron_tool`）。方块音效是原版金属声；开关门音效见"音效"。
- 开发：`src/dev/java/com/antaurora/apofirstlight/dev/BeverageCoolerGameTests.java`（`src/dev/beverage-cooler-gametest.init.gradle`）原有部分没有改，加了摆放检查（见"摆放"），都没有运行。

## 摆放（2026-10-01）

规则和零售货架 V3 一样：准星指哪拿/放哪；先拿前排，前排拿完才能拿后排；不自动补货。两者共用 `block/DisplayDepthRule`（从货架里抽出来的，货架行为不变）。

| 项目 | 值 |
|---|---|
| 容量 | 5 层 × 6 列 × 前后 2 排 = **60 格**，每格 1 个物品。什么物品都能放 |
| 格子编号 | `cell = 层 × 6 + 列`；前排 0–29，后排 30–59（`BeverageCoolerLayout.slot / cellOf / depthOf`） |
| 列 | 0–2 在右门后面，3–5 在左门（主格一侧）后面。每半边 14.15 px 宽，三等分，列中心（源坐标）x = -3.79、0.93、5.64、10.36、15.07、19.79 |
| 高度 | 隔板顶面（源坐标）y = 4.16、9.01、13.86、18.71、23.56；物品立在隔板上，中心比顶面高 1.92 px。每层到上一层横梁底下 4.49 px |
| 深度 | 金属丝网 z -4.4..4.5，前后各一半：前排中心 z -2.175，后排 2.275，分界 0.05 |
| 物品大小 | 0.24，朝冷柜正面，和货架一样 |
| 存档 | 主格方块实体的 `Items`；同步给客户端用于渲染（`getUpdateTag`）；读档时一格多个的只保留 1 个 |
| 漏斗 | 主格方块实体实现 `Container`：一格最多 1 个，只能放进空格，取的时候按格子顺序（前排先） |
| 拆除 | 主格被移除时（挖任一格、爆炸、`/setblock` 等都会连带移除主格），每个物品掉落一次 |

交互：
- 瞄准门（`hitDoor` 的门区域）时照旧开关门；只有不在门区域、并且门开着时才是放 / 拿。
- 选格（`BeverageCoolerLayout.targetCell`）：和货架一样，物品不挡视线，沿视线找最先进入的格子空间（一层一列、前后两排连在一起），被实体面挡住的不算。只考虑开着的门后面的格子：关着的那半边拿不到，也放不进去。
- 前后：两排都空时看准星落点，落在分界之后（隔板后半、后壁）放后排，否则放前排。平视时视线落在后壁上，所以第一件放后排。
- 门转动的 8 tick 里状态还没提交：开门时要等门开完才能拿；关门时转动期间还能拿。
- 放、拿没有交互提示（和货架一致）。

渲染：`client/BeverageCoolerRenderer` 先画物品，再交给通用的动画 Mesh 渲染器画柜体和玻璃。通用渲染器会立刻提交半透明的玻璃，如果物品在它之后提交，就会不带玻璃颜色地显示在玻璃前面。所以物品画完后先提交（`endBatch`），再画柜体。满载时 60 个物品每帧都要渲染，以后的饮料模型要控制面数（单件 300 面以内，罐、瓶做到一两百面）。

开发：
- `AuthoringFixtureRegistry`：冷柜从"安全装饰"改为"带容器"（`STORAGE_WITH_INVENTORY`），初始为空。
- `BeverageCoolerGameTests` 加了摆放检查（**没有运行**）：
  - 60 格、编号规则；
  - 左门开时平视落在后壁上，先放后排、再放前排，先拿前排；
  - 关着的右门那半边拿不到；
  - 挖掉主格时物品掉一次。

以后：世界生成时往冷柜里填东西和货架填充一起做；不放弹药。

## 实机确认

用户 2026-10-01 确认"没问题"。没有单独报告的细项：物品栏图标、关光影下的玻璃、开门动画的时机（点击即开始、8 tick 后提交状态时不跳）、远处金属丝闪烁。
