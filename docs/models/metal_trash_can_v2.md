# Metal Trash Can V2（金属垃圾桶 V2：Mesh + PBR + 可搜索）

状态（2026-10-02）：
- **模型、开盖动画、可搜索容器、垃圾袋已实现，用户 2026-10-02 实机 PASS**。
- `compileJava --offline` PASS（包括 `src/dev`）。GameTest 按规则没有运行。
- 用户 2026-10-02 定的范围：
  - 做成可搜索容器，3×3（9 格）；
  - 有东西才显示垃圾袋，袋子是系好口的实心袋，里面不放任何东西；
  - 第一版不做音效；用户 2026-10-02 PASS 后同一天加上了开关盖声、钣金方块声和所有容器共用的翻找声（见"声音"，没有实机验证）；
  - 颜色是我推荐的炭黑涂层钢，用户没有改。

V2 取代 V1 文档（原 `docs/models/city_trash_can.md`，已删除）。V1 的这些文件已删除或被覆盖（都在 git 里）：
- 源模型 `src/main/blockbench/city_trash_can.bbmodel`（204 个方块）、贴图 `textures/block/city_trash_can.png`；
- 脚本 `tools/city-trash-can{,-polish,-seal,-align-lid,-inspect}.blockbench.js`、`tools/export-city-trash-can.mjs`、`tools/export-city-trash-can-runtime.mjs`、`tools/verify-metal-trash-can.mjs`；
- 运行时模型 `models/block/metal_trash_can.json`、`models/item/metal_trash_can.json` 被 V2 生成的覆盖。

## 模型

- 生成器：`tools/build-metal-trash-can-v2.mjs`（`--check`、`--preview DIR`）。
- 输出：
  - 可编辑源 `src/main/blockbench/metal_trash_can.bbmodel`、`src/main/blockbench/textures/metal_trash_can{,_s,_n}.png`；
  - 运行时 `geo/metal_trash_can.geo.json`、`meshes/metal_trash_can.aflmesh.json`、`block_mesh_profiles/metal_trash_can.json`、`textures/block/metal_trash_can{,_s,_n}.png`；
  - 形状档案 `data/apocalypse_firstlight/mesh_shapes/metal_trash_can.json`；
  - 物品模型 `models/item/metal_trash_can.json`（`builtin/entity`，GUI rotation `[30,225,0]`、translation `[0,0.348,-0.079]`、scale 0.759；盖子关着、没有袋子）、方块模型 `models/block/metal_trash_can.json`（只有粒子贴图）。
- Pure Mesh，512 atlas，7.25 texel/px，9 个部件，共 2064 个三角面，共面重叠检查 0 处（垃圾桶本身，以及每个袋子和它一起）：

| 骨骼 | 内容 | 三角面 |
|---|---|---:|
| `body` | 桶身（外皮、内壁和桶底）、两侧把手、铰链座和销子 | 1136 |
| `lid` | 拱形盖、盖沿、浅色内面、盖提手、铰链片（绕铰链转，动画通道 `open`） | 400 |
| `goods_bag_0` … `goods_bag_2` | 三个垃圾袋（geo 里 `neverRender`，由方块实体决定显示） | 各 176 |

- 坐标（源 px）：方块底面中心是原点，正面朝 -Z，+X 是正对时的左手边。
- 桶身：24 段圆，底部一圈收脚，桶壁从半径 5.15（底）收到 5.7（上），三道外凸的压筋（y 3.2 / 6.9 / 10.6），桶口卷边（最大半径 5.95，桶口高 13.8）。里面是一层内壁和桶底（y 1.2），所以开盖能看到里面。
- 两侧把手在 y 11.9，各两个支座和一根横杆。
- 盖子：盖沿罩住桶口，拱顶最高 14.98，上面一根横提手（最高 15.95）。整个垃圾桶都在 1 格以内（V1 的提手高出 1.2 px）。
- 铰链在后面（源 z 6.42、y 13.85），盖子绕它向后掀起 **80°**：再大的话盖子会伸出方块后面，靠墙放时会插进墙里（生成器里检查了打开后盖子的所有顶点 z < 7.9）。打开后立起来的盖子最高到 y 26.3，高出方块是正常的。
- 动画：`open` 通道，8 tick，ease_in_out，`lid` 绕 X 轴转 80°（正角度是前沿抬起）。

### 材质

全部是涂层 / 电介质（LabPBR F0 20），没有全金属面，没有印刷、脏污和锈：

| 材质 | 用在 | Base Color | 光滑度（面 / 倒角） |
|---|---|---|---|
| `coat` | 桶身外皮、盖子 | 44, 47, 52（炭黑粉末涂层，和售货机一样） | 100 / 120 |
| `inside` | 桶的内壁和底、盖子内面 | 80, 82, 86（浅一点的灰，黑色袋子才看得出来） | 96 / 104 |
| `fitting` | 把手、铰链、盖提手 | 60, 62, 66 | 112 / 132 |
| `bag` | 垃圾袋 | 24, 24, 27（黑色塑料膜，稍有光泽） | 160 |

第一版内壁和袋子都是很深的黑，离线预览里袋子在桶里完全看不出来，所以把内壁改浅。

### 垃圾袋

- 三个系好口的黑色垃圾袋，从桶底往上叠：
  - 第一个是大袋，占到桶的四分之三高（y 1.2..10.6）；
  - 第二、三个是小一点的袋子，分别叠在左右两边，最后一个到桶口（扎口最高 13.62，在盖子下面）。
- 袋身是 8 段的旋转体，带一点不规则的鼓包，往上收成扭起来的袋口，顶上一个结和两只"耳朵"。袋子里面不建任何东西。
- 第一版的袋子比较小，一个袋子沉在桶底，站着看不到，两个也只露出扎口。现在一个就能看到扎口，两个能看到袋身，三个左右两个都露出来。
- 生成器检查：袋子不穿出桶的内壁，最上面的结在关着的盖子下面。

## 交互（Mesh Shape Runtime）

形状档案（`mesh_shapes/metal_trash_can.json`，单格，状态 `closed` / `open`），规则见 [mesh_shape_runtime_v1.md](../rendering/mesh_shape_runtime_v1.md)：

| 状态 | 瞄准 | 动作 | 提示 |
|---|---|---|---|
| 关着 | 整个垃圾桶（区域 `lid`） | 打开盖子 | 打开 |
| 开着 | 桶口里面（区域 `mouth`，从上面往里看） | 打开搜索界面 | 搜索 / 查看（全部搜完后） |
| 开着 | 桶身其他地方和立着的盖子（区域 `lid`） | 盖上 | 盖上 |

- 提示画在区域的锚点上：关着时在盖子前沿，开着时"搜索"在桶口前沿、"盖上"在立起来的盖子上。翻译键 `hint.apocalypse_firstlight.trash_can.{open,search,view,close}`。
- 盖上会关掉所有人打开的界面（`stillValid` 要求开着），搜索会暂停。
- 碰撞 / 选中框：关着是桶身 + 盖子 + 提手（选中框再加两侧把手和铰链）；开着是桶身 + 立起来的盖子的包围盒。超出方块的部分（打开的盖子高出 1 格）由运行时裁到本格；交互区域不裁。
- 方块状态：`facing`（放置时正面朝向玩家，铰链在后面）、`open`。V1 没有方块状态。

## 容器

- `MetalTrashCanBlockEntity`：9 格，接入逐格搜索（[progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)），3×3 界面（`GRID_3X3`），每格 40 tick（2 秒），±15%。搜索时放框架共用的翻找声（见"声音"）。
- 世界战利品在第一次打开盖子时生成（和储物柜一样），格子仍要搜。玩家放置的不用搜。
- 垃圾袋数量：按比例，6 格以上三个都显示（`AflContainerGoods.shown(occupied, 3, 6)`）：1–2 格 → 1 个，3–4 格 → 2 个，5 格以上 → 3 个。只在盖子开着或正在动的时候画。客户端只收到袋子数量，不收到物品。
- 拆掉时（任何方式）：看过的格子掉出来，没搜到的战利品丢失（`dropContentsOnce`，`onRemove` 里调用）。
- 项目里现在还没有任何箱子战利品表，世界里的垃圾桶都是空的。测试用开发指令：`/dev container_search spawn apocalypse_firstlight:metal_trash_can fill <格数>`。

## 声音（2026-10-02，没有实机验证）

| 事件 | 声音 | 来源 / 处理 |
|---|---|---|
| 开盖 | `metal_trash_can_open` → `sounds/metal_trash_can/open.ogg`（0.86 s） | `E:/Download/trash_can_open.wav` a1c12935ae1871b5；主声（盖子离开桶口）对在 0.10 s，铰链声跟着盖子转；比储物柜开门低 2 LU（−24.3 LUFS） |
| 盖上 | `metal_trash_can_close` → `sounds/metal_trash_can/close.ogg`（1.01 s） | `E:/Download/trash_can_close.wav` 1666e17482e00ca1；撞击对在 0.40 s（8 tick 动画的最后一帧，盖子落到桶口）；目标比储物柜关门低 2 LU，峰值限到 −1 dBFS 后实际 −22.6 LUFS |
| 搜索 | 框架共用的搜索声 `container_search_rummage`（沙沙声无缝循环，搜索进行时在容器处循环） | 见 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md) 17a |
| 挖掘 / 破坏 / 放置 / 脚步 / 摔落 | `AflSoundTypes.SHEET_METAL`（储物柜那套空心钣金声，原来是原版金属声） | 不需要新素材 |

- 生成器 `tools/build-metal-trash-can-sounds-v1.mjs`（共享库 `tools/sound-mix-lib.mjs`，需要 ffmpeg）；素材是用户用 AI 音效工具生成的 1 s 立体声 WAV，混成单声道。
- 播放：`MetalTrashCanBlock.setOpen`，服务端，音量 0.8，音高 0.98–1.02（不要把对好的时间点带偏）。可听 12 格，和储物柜门一样。字幕"垃圾桶盖打开 / 垃圾桶盖盖上"。

## 方块

- Registry ID `apocalypse_firstlight:metal_trash_can`；"黎明启示录 · 家具与设施" Creative Tab。
- 方块声：`AflSoundTypes.SHEET_METAL`（2026-10-02 起）。
- 挖掘（不变，2026-10-02 复查）：硬度 3、爆炸抗性 5，`requiresCorrectToolForDrops()`，`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`（铁镐及以上；普通城市设施，不按工业设备的钻石级算）。掉落：战利品表 `loot_tables/blocks/metal_trash_can.json`，掉自身 1 个（爆炸里不掉），里面的东西另外按上面的规则掉。
- 渲染：`ENTITYBLOCK_ANIMATED`，通用的 `AflAnimatedBlockMeshRenderer`；物品由 `AflStaticMeshItemRenderer`（`client/AflStaticMeshItemClient` 绑定）。
- 建筑工具（`dev/authoring`）：改成 `STORAGE_WITH_INVENTORY`，四个朝向，`open=false`，必须是空的。

### 已知问题：V2 之前放下的垃圾桶

V1 没有方块实体。V2 之前已经放在存档里的垃圾桶读档时不会自动补上方块实体，所以**看不见**，也打不开（碰撞还在）。重新放一次，或者把建筑重新粘贴一次就好。新放置的、结构生成的都正常（结构里的旧方块状态没有 `facing` / `open`，读成朝北、关着）。

## 代码与资源

| 文件 | 内容 |
|---|---|
| `tools/build-metal-trash-can-v2.mjs` | 模型、贴图、geo / sidecar / profile、形状档案、物品模型 |
| `block/MetalTrashCanBlock.java` | 状态、开关盖、区域 → 动作、提示、形状、掉落 |
| `blockentity/MetalTrashCanBlockEntity.java` | 9 格搜索容器、垃圾袋数量、Mesh 宿主（盖子动画、袋子显示） |
| `registry/AflBlockEntities.java` | `METAL_TRASH_CAN` |
| `client/AflBlockEntityRenderers.java`、`client/AflStaticMeshItemClient.java` | 渲染器、物品渲染绑定 |
| `lang/{zh_cn,en_us}.json` | 四个提示 |
| `src/dev/.../authoring/bridge/AuthoringFixtureRegistry.java` | 建筑工具的登记 |

## 验证

- `node tools/build-metal-trash-can-v2.mjs --check`：CHECK OK。
- `compileJava --offline`：PASS。
- 离线渲染（不是游戏画面）看过关盖、开盖和 1 / 2 / 3 个袋子。
- 用户 2026-10-02 实机 PASS。
