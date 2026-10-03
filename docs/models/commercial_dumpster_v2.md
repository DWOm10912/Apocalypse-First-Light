# Commercial Dumpster V2（商业垃圾箱 V2：Mesh + PBR + 可搜索 + 四种颜色）

状态（2026-10-02）：
- **模型、两块盖子的开关动画、18 格可搜索容器、垃圾袋和纸箱、开关盖声音、四种颜色已实现，用户 2026-10-03 实机 PASS**（中间改过两次：打开那一半的形状、盖子悬空和点盖子关闭，见"交互"和"模型"）。
- `compileJava --offline` PASS（包括 `src/dev`）。GameTest 按规则没有运行。
- 用户 2026-10-02 定的范围：
  - 容器 3×6（18 格，6×3 界面）；
  - 两块盖子分开开关；
  - 做几种颜色的变体，各自放进创造模式标签页，名字一样，最后加括号写颜色（以后做城市生成时用）；
  - 里面放垃圾袋之类的东西，战利品越多垃圾越多；
  - 开盖、盖上的声音用用户生成的素材，搜索用共用的沙沙循环。
- 颜色用了我提的绿、蓝、棕、灰（用户只要求"几个颜色"）；箱体是低饱和涂层钢，盖子都是黑色塑料。

V2 取代 V1 文档（原 `docs/models/commercial_dumpster.md`，已删除）。V1 的这些文件已删除或被覆盖（都在 git 里）：
- 源模型 `src/main/blockbench/commercial_dumpster.bbmodel`（260 个方块，被 V2 生成的 Mesh 源覆盖）、贴图 `textures/block/commercial_dumpster.png`；
- 运行时模型 `models/block/commercial_dumpster/{master,secondary}.json`（删除）、`models/item/commercial_dumpster.json` 和方块状态文件（被 V2 覆盖）；
- 脚本 `tools/commercial-dumpster{,-finish,-close,-open-preview,-view}.blockbench.js`、`tools/export-commercial-dumpster.mjs`、`tools/export-commercial-dumpster-runtime.mjs`、`tools/verify-commercial-dumpster.mjs`。

## 颜色和名字

| 颜色 | Registry ID | 名字 | 箱体 Base Color |
|---|---|---|---|
| 绿 | `apocalypse_firstlight:commercial_dumpster`（V1 的 ID，没改，结构里已有的照常） | 垃圾箱（绿色） / Commercial Dumpster (Green) | 50, 74, 58 |
| 蓝 | `apocalypse_firstlight:commercial_dumpster_blue` | 垃圾箱（蓝色） / (Blue) | 44, 64, 92 |
| 棕 | `apocalypse_firstlight:commercial_dumpster_brown` | 垃圾箱（棕色） / (Brown) | 84, 66, 50 |
| 灰 | `apocalypse_firstlight:commercial_dumpster_gray` | 垃圾箱（灰色） / (Gray) | 86, 90, 94 |

- 中文名沿用 V1 的"垃圾箱"，后面加颜色。
- 四个都在"黎明启示录 · 家具与设施"标签页，按绿、蓝、棕、灰排在金属垃圾桶后面。
- 同一个方块类（`CommercialDumpsterBlock`，构造时传颜色）、同一个方块实体类型（`commercial_dumpster`，对四个方块都有效）、同一套模型；每个颜色有自己的贴图和 Mesh profile。以后加颜色：在生成器的 `COLOURS` 加一项，再注册一个方块、物品，补语言、挖掘标签和战利品表。

## 模型

- 生成器：`tools/build-commercial-dumpster-v2.mjs`（`--check`、`--preview DIR`）。
- 输出：
  - 可编辑源 `src/main/blockbench/commercial_dumpster.bbmodel`（绿色贴图），`src/main/blockbench/textures/commercial_dumpster_<颜色>{,_s,_n}.png`；
  - 运行时：共用的 `geo/commercial_dumpster.geo.json`、`meshes/commercial_dumpster.aflmesh.json`；每个颜色一份 `block_mesh_profiles/commercial_dumpster_<颜色>.json`、`textures/block/commercial_dumpster_<颜色>{,_s,_n}.png`；
  - 每个方块：`blockstates/<id>.json`（一个 `""` 变体）、`models/block/<id>.json`（只有粒子贴图）、`models/item/<id>.json`（`builtin/entity`；GUI rotation `[25,225,0]`、translation `[2.83,-0.187,-2.747]`、scale 0.477，盖子关着，没有垃圾）。
- Pure Mesh，512 atlas，3.75 texel/px，16 个部件，共 1860 个三角面，共面重叠检查 0 处（垃圾箱本身，以及每个货物骨骼和它一起）：

| 骨骼 | 内容 | 三角面 |
|---|---|---:|
| `body` | 箱体（前后壁、两侧斜顶侧壁、箱底）、上沿卷边、正面 4 条竖加强筋、两侧叉车槽、3 条底部滑橇、后横梁和它背面的铰链板 | 484 |
| `lid_left` / `lid_right` | 盖板（沿斜面，前面有下翻的唇边）、3 条加强筋、前把手 | 各 132 |
| `goods_left_0..3` / `goods_right_0..3` | 每边两个大袋子、一个上层袋子、一块压扁纸箱（geo 里 `neverRender`，由方块实体决定显示） | 袋子各 176，纸箱 28 |

- 坐标（源 px）：**主格**（MASTER）的底面中心是原点，正面朝 -Z，+X 是正对时的左手边。主格是右半边（x −8..8），副格是左半边（x 8..24）。副格在主格的顺时针方向，和 V1 一样。
- 箱体：x −7..23（宽 30 px）、z −6.5..7.4、底在 y 1.6（下面是滑橇）；上沿是斜的，前面高 17.2、后面高 20.8；壁厚 0.6。两侧叉车槽（y 6..9.6，前后贯通的套管）的外沿正好在两格边上，整个箱子不出两格。
- 盖子：沿上沿的斜面，厚 0.9，前面有一条下翻的唇边罩住上沿，3 条加强筋，前面一根把手；后端停在 z 6.4，离箱子后沿还有 1 px。
- 后横梁（2026-10-03 加）：深色钢，沿箱子后沿横跨整个宽度（z 6.43..7.8），底面贴着斜上沿，顶面（y 21.49）和关着的盖子齐平；背面有每块盖子两片铰链板。
- 铰链在盖子**顶面的后沿**（左盖 x 15.5、右盖 x 0.5，y 21.49，z 6.4），也就是后横梁的前上沿。盖子向后掀起 **95°**：铰链放在顶面后沿，盖子转起来不会往后伸；打开后盖子正好立在后横梁的前上沿上。生成器检查了两块盖子开到 95° 时所有顶点 z < 7.95，靠墙放也不会插进墙里。打开后的盖子最高到 y 36.06（高出方块是正常的）。
- 第一版（2026-10-02）盖子一直伸到后沿、铰链在 y 21.72 z 7.3，打开后盖子底边比后沿高出约 0.9 px，从后面看是悬空的（用户 2026-10-03 实机发现），所以缩短盖子、加了后横梁。改后用户 PASS。
- 动画：通道 `left_open` / `right_open`，10 tick（0.5 秒），ease_in_out，盖子绕 X 轴转 95°（正角度是前沿抬起）。

### 材质

全部是涂层 / 电介质（LabPBR F0 20），没有全金属面，没有印刷、标贴、脏污和锈：

| 材质 | 用在 | Base Color | 光滑度（面 / 倒角） |
|---|---|---|---|
| `paint` | 箱体、上沿、加强筋 | 每个颜色不同（见上表），低饱和粉末涂层 | 96 / 116 |
| `steel` | 叉车槽、滑橇、铰链板 | 56, 58, 62（深色涂层） | 104 / 124 |
| `lid` | 盖子、把手 | 30, 31, 34（黑色 HDPE） | 112 / 128 |
| `bag` | 垃圾袋 | 24, 24, 27（和垃圾桶的一样） | 160 |
| `carton` | 压扁纸箱 | 138, 110, 78（牛皮纸） | 70 / 74 |

### 垃圾

- 每边四个位置：
  - 0、1：两个大袋子放在箱底（高 11 / 10.4 px）；
  - 2：一个袋子叠在上面（从 y 9.6 开始，扎口最高约 17.6）；
  - 3：一块压扁的纸箱斜靠在后壁上（到 y 15）。
- 袋子和金属垃圾桶的同一种：有一点鼓包的袋身、扭起来的袋口、系好的结和两只"耳朵"，里面不建任何东西。
- 生成器检查：袋子不穿出箱壁，都在关着的盖子下面。
- 填充顺序：两边轮流，每边按 两个底袋 → 纸箱 → 上层袋。先填哪一边由位置决定，所有客户端、重进存档都一样。
- 数量：`AflContainerGoods.shown(occupied, 8, 14)`，大约每 1.75 格多一件，14 格以上 8 件全显示。
- 只在那一边的盖子开着或正在动的时候画。

## 交互

方块状态：`facing`、`part`（master / secondary）、**V2 新加** `left_open`、`right_open`（两格同步）。

| 瞄准的那一半 | 盖子 | 动作 | 提示 |
|---|---|---|---|
| 箱体任意地方 | 关着 | 打开这一边的盖子 | 打开 |
| 箱体任意地方（里面、箱沿、外壁） | 开着 | 打开搜索界面 | 搜索 / 查看（全部搜完后） |
| 立起来的盖子 | 开着 | 盖上这一边 | 盖上 |

- 打开的盖子立在两格上面（y 21.5..36），不在垃圾箱的格子里，原版准星点不到它。所以垃圾箱实现了 `meshshape/AflOverhangPickBlock`（2026-10-03 新加的通用接口）：盖子开着时，每格把自己那一边立起来的盖子的包围盒交给准星检测（`client/AflMeshShapePicking`，也检测视线经过的格子下面 1–2 格的方块），准星对着盖子就选中垃圾箱那一格。
- 判断点的是什么用**玩家自己的视线**（`CommercialDumpsterBlock.aimed`）：视线先碰到哪个就是哪个，打开的盖子（盖上）或者垃圾箱自己的形状（`target`）。原因是准星检测会把盖子上的命中点夹回格子里（服务端拒绝离方块中心超过 1 格的点），只看命中点分不出点的是盖子。视线什么都没碰到时（延迟）退回命中点判断。
- 哪一半由源坐标 x 判断（≥ 8 是左半边）。主格和副格都能点，都转到主格处理。
- 提示画在：打开 → 那块盖子的把手；搜索 → 那一边桶口的中间；盖上 → 立起来的盖子中间。翻译键 `hint.apocalypse_firstlight.dumpster.{open,search,view,close}`。客户端提示（`WorldInteractionHint#dumpsterLid`）和服务端 `use()` 用同一个判断。
- 两块盖子都关上时，所有人打开的界面都会关掉（`stillValid` 要求至少一边开着），搜索暂停。
- 碰撞和选中框（`CommercialDumpsterBlock.cell`，北向方块 px，按朝向旋转），每格按自己那一边的盖子：
  - 关着：箱体分 4 段（每段高到这一段后沿的上沿高度），再加盖子和把手（最高约 22.2 px）；
  - 开着：和模型一样的空心箱子，就是箱底（到 y 2.2）、带箱沿的前壁（17.2）和后壁（20.8）、后横梁（到 21.49）、外侧壁（顶边沿斜口分 4 段）；两半之间不隔开；
  - 都加上这一侧的叉车槽。打开的盖子没有碰撞，只用来让准星选中（见上）。
- 2026-10-03 改（用户实机）：
  - 第一版打开的那一半仍是实心的台阶块，选中框盖在敞开的箱口上，和模型对不上（关着的没问题），改成空心箱子；
  - 用户要点盖子来关，不要点外壁："盖上"改到立起来的盖子上，箱体任何地方都是搜索。改后用户 PASS。

## 容器

- `CommercialDumpsterBlockEntity`（在主格）：18 格，接入逐格搜索（[progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)），6×3 界面（`GRID_6X3`），每格 40 tick（2 秒），±15%。搜索时放共用的沙沙循环。
- 世界战利品在第一次打开任一边盖子时生成，格子仍要搜。玩家放置的不用搜。
- 拆掉（任何方式）：看过的格子掉出来，没搜到的战利品丢失。比较器只按已经看到的格子输出。
- 项目里现在还没有任何箱子战利品表，世界里的垃圾箱都是空的。测试：`/dev container_search spawn apocalypse_firstlight:commercial_dumpster fill <格数>`（其他颜色换成对应 ID）。

## 声音

| 事件 | 声音 | 来源 / 处理 |
|---|---|---|
| 开盖 | `commercial_dumpster_open` → `sounds/commercial_dumpster/open.ogg`（0.92 s） | `E:/Download/dumpster_open.wav` 1306616133c4afb4；第一下撞击对在 0.06 s（盖子刚离开上沿），后面的吱呀跟着盖子转；和储物柜开门一样响（−22.3 LUFS） |
| 盖上 | `commercial_dumpster_close` → `sounds/commercial_dumpster/close.ogg`（1.2 s） | `E:/Download/dumpster_close.wav` 45dc8e3678cfd82a；"砰"对在 0.50 s（10 tick 动画的最后一帧，盖子落到上沿）；比储物柜关门响 1 LU（−19.2 LUFS） |
| 搜索 | 共用的沙沙循环 `container_search_rummage` | 见 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md) 17a |
| 挖掘 / 破坏 / 放置 / 脚步 / 摔落 | `AflSoundTypes.SHEET_METAL`（空心钣金声，V1 是原版金属声） | 不需要新素材 |

- 生成器 `tools/build-commercial-dumpster-sounds-v1.mjs`（共享库 `tools/sound-mix-lib.mjs`，需要 ffmpeg）；素材是用户用 AI 音效工具生成的 1 s 立体声 WAV（左右相关度 0.93 / 0.89），混成单声道。四个颜色共用。
- 播放：服务端，从那一边盖子的中间发出，音量 0.9，音高 0.98–1.02。可听 16 格（比储物柜门的 12 格远，因为更响）。字幕"垃圾箱盖打开 / 垃圾箱盖盖上"。

## 方块

- 放置、两格结构、只有主格掉落、挖副格时由方块代码掉主格的物品（V2 起掉自己颜色的物品）、防止只剩一半、不能被活塞推：沿用 V1。
- 挖掘（2026-10-02 复查，四个颜色一样）：硬度 3.5、爆炸抗性 6，`requiresCorrectToolForDrops()`，`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`（铁镐及以上；城市设施，不按工业设备的钻石级算）。每个颜色一份战利品表 `loot_tables/blocks/<id>.json`（主格掉自身 1 个，爆炸里不掉）。
- 渲染：主格 `ENTITYBLOCK_ANIMATED`，由通用的 `AflAnimatedBlockMeshRenderer` 画整个垃圾箱；副格 `INVISIBLE`。物品由 `AflStaticMeshItemRenderer`（`client/AflStaticMeshItemClient` 绑定，每个颜色自己的贴图）。
- 建筑工具（`dev/authoring`）：四个颜色都是 `STORAGE_WITH_INVENTORY`，两格（`DUMPSTER` 多方块定义不变），`left_open=false`、`right_open=false`，必须是空的。

### 已知问题：V2 之前放下的垃圾箱

V1 没有方块实体。V2 之前已经放在存档里的绿色垃圾箱读档时不会自动补上方块实体，所以**看不见**，也打不开（碰撞还在）。重新放一次，或者把建筑重新粘贴一次就好。新放置的、结构生成的都正常（`convenience_store_01`、`gas_station_01` 里有它，旧方块状态没有 `left_open` / `right_open`，读成关着）。

## 代码与资源

| 文件 | 内容 |
|---|---|
| `tools/build-commercial-dumpster-v2.mjs` | 模型、四份贴图、geo / sidecar、四份 profile、方块状态、方块和物品模型 |
| `tools/build-commercial-dumpster-sounds-v1.mjs` | 开关盖声音 |
| `block/CommercialDumpsterBlock.java` | 两格结构、两块盖子、区域 → 动作、提示、形状、掉落、颜色 |
| `blockentity/CommercialDumpsterBlockEntity.java` | 18 格搜索容器、货物顺序和数量、Mesh 宿主 |
| `registry/AflBlocks.java`、`AflItems.java`、`AflBlockEntities.java`、`AflCreativeTabs.java`、`AflSounds.java` | 四个方块和物品、方块实体类型、标签页、声音 |
| `client/AflBlockEntityRenderers.java`、`client/AflStaticMeshItemClient.java`、`client/WorldInteractionHint.java` | 渲染器、物品渲染、提示 |
| `lang/{zh_cn,en_us}.json`、`sounds.json`、挖掘标签、战利品表 | 资源 |
| `src/dev/.../authoring/bridge/AuthoringFixtureRegistry.java` | 建筑工具的登记 |

## 验证

- `node tools/build-commercial-dumpster-v2.mjs --check`：CHECK OK。
- `compileJava --offline`：前几轮 PASS；最后一轮（点盖子关闭）在我这边失败，报错全在当时另一个会话正在写的 `weight/` 包里，不在这次的文件里；用户之后实机运行过。
- 离线渲染（不是游戏画面）看过关盖、开左盖带垃圾、四种颜色。
- 实机：用户 2026-10-03 PASS（第一版的打开形状、盖子悬空、点盖子关闭三处改过之后）。
