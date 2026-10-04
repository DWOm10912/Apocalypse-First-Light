# Checkout Counter V1（收银柜台、柜台通道门、收银背柜）

状态（2026-10-04）：**已实现，没有实机验证**。`compileJava --offline` PASS。外观只看过离线预览（生成器的 `--preview` 输出，用一个简单的光栅渲染器拼成 L 形柜台、内转角、通道门开 / 关和背柜）。用户定的方向：柜体深灰、不做挂钩式货架、价目带留空、要做通道门、柜台下的格子 3×3、背柜 3×4，一次做完资产 + PBR + 搜刮，基础色要有质感。

用途：便利店、加油站、药店、五金店的收银区。柜台可以拼成直线、L 形、U 形；通道门放在一条柜台里，让收银员进出；背柜靠墙放在收银员身后。

## 方块

| Registry ID | 名称 | 方块实体 | 容器 | 说明 |
|---|---|---|---|---|
| `apocalypse_firstlight:checkout_counter` | 收银柜台 / Checkout Counter | `CheckoutCounterBlockEntity`（与带货架款共用类型 `checkout_counter`） | 9 格，3×3 | 普通款，顾客面是平板 |
| `apocalypse_firstlight:checkout_counter_display` | 收银柜台（带货架）/ Checkout Counter (Display) | 同上 | 9 格，3×3 | 直段的顾客面挂三层冲动消费托盘 |
| `apocalypse_firstlight:checkout_counter_gate` | 柜台通道门 / Counter Gate | `CheckoutCounterGateBlockEntity`（只用来画动画） | 无 | 一段可以掀起台面、推开下门的柜台，开关有动画 |
| `apocalypse_firstlight:back_bar_shelf` | 收银背柜 / Back Bar Shelf | `BackBarShelfBlockEntity`（下半格） | 12 格，3×4 | 两格高的墙柜 |

- 代码：`block/CheckoutCounterBlock`、`block/CheckoutCounterGateBlock`、`block/BackBarShelfBlock`、`blockentity/CheckoutCounterBlockEntity`、`blockentity/CheckoutCounterGateBlockEntity`、`blockentity/BackBarShelfBlockEntity`、`client/CheckoutCounterRenderer`、`client/BackBarShelfRenderer`。
- 挖掘：四个方块都是零售家具，`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`，`requiresCorrectToolForDrops()`，和货架、收银机一样（不走工业设备的钻石级默认）。硬度 / 抗性：柜台、带货架柜台、通道门 2.0 / 4.0，背柜 1.5 / 4.0；声音 `SoundType.WOOD`（贴面板材）。
- 掉落：各自的方块掉落表（`loot_tables/blocks/<id>.json`），条件 `survives_explosion`；背柜只从下半格掉（`half=lower`）。铁镐及以上掉自己。背柜：生存模式用对工具敲上半格，下半格随邻居更新一起被拆、掉落；创造模式或工具不对时，上半格把下半格直接清掉，不掉东西。容器里已揭示的格子在任何拆除方式下都会掉出来，没搜过的战利品随方块损毁（`dropContentsOnce`）。没有实机验证生存模式的挖掘和掉落。
- 物品：柜台、带货架柜台、通道门可堆叠 16；背柜 1。
- 负重（`item_mass/afl_content_v1.json`，估算）：柜台 22 kg、带货架柜台 24 kg、通道门 12 kg、背柜 28 kg。搬运标签：三种柜台件在 `carry/bulky`（×1.10），背柜在 `carry/oversized`（×1.25）。
- 创造栏：家具与设施（`furniture`），排在货架后面、收银机前面。

## 柜台的连接

`FACING` 是顾客那一侧（放置时朝向玩家）。和原版楼梯一样，形状由邻居算出来，不能手动指定：

- `shape`：
  - `straight`：直段；
  - `outer_right` / `outer_left`：外转角（顾客面绕在转角外侧）。条件：身后（`FACING` 反方向）那格是柜台，朝向是 `FACING` 的顺时针 / 逆时针方向；并且这条线在转向的另一侧没有继续（否则是 T 字，保持直段）；
  - `inner_left` / `inner_right`：内转角（顾客站在拐角里）。条件：面前那格是柜台，朝向是 `FACING` 的逆时针 / 顺时针方向，同样排除 T 字。
  - 先判外转角，再判内转角。
- `north` / `east` / `south` / `west`：那一侧是不是"柜台件"（柜台、带货架柜台或通道门）。一条线真正结束的那一端才加端头封板。
- 通道门算柜台件（两边的柜台不会朝它加封板），但它自己不参与转角判断。
- 旋转 / 镜像（结构、WorldEdit）：`rotate` 一起转四个侧面标记；`mirror` 还会交换转角的左右。

## 交互

- **柜台**：从非顾客的一侧（收银员那一侧；转角件除去它的两个顾客面）右键，打开搜索 / 查看界面（3×3）；带货架款的直段从顾客面（托盘）也能打开。点台面顶上不会打开。准星提示"搜索 / Search"或"查看 / View"。
- **台面放东西**：台面和方块顶面齐平（y 16），支撑形状是顶上一整块（`getBlockSupportShape` = `[0,15,0]..[16,16,16]`），所以收银机、灯笼这类要求下方顶面结实的方块都能放在柜台上，放上去贴着台面。侧面和底面不算结实。2026-10-04 用户实机发现收银机放不上去（原来台面高 15.1 px、没有支撑面），改成现在这样。
- **通道门**：任何一面右键都会开 / 关，播放专属开 / 关音效（见下面"通道门动画与音效"），并发出 `BLOCK_OPEN` / `BLOCK_CLOSE` 游戏事件；准星提示"打开 / Open"或"关闭 / Close"。
  - 门轴：`hinge=left` 在 `FACING` 的逆时针一侧。放置时朝向有柜台的一边（只有顺时针一侧有柜台时选 `right`）。
  - 开着时，台面翻过来叠在门轴那一侧的柜台上，下门转 90° 贴着门轴一侧，往收银员那边打开。开关都有动画；碰撞形状和原版门一样在点击时立刻切换。

### 通道门动画与音效（2026-10-04）

用户实机看过第一版（瞬间切换模型、原版栅栏门音效）后要求加流畅动画，并提供了两段音效素材（`E:/Download/开门.wav`、`关门.wav`）。

- 渲染：AFL Animated Block Mesh Runtime（[animated_block_mesh_runtime_v1.md](../rendering/animated_block_mesh_runtime_v1.md)）。方块返回 `ENTITYBLOCK_ANIMATED`，`CheckoutCounterGateBlockEntity` 按门轴选 profile（`block_mesh_profiles/checkout_counter_gate_left.json` / `_right.json`），用通用的 `AflAnimatedBlockMeshRenderer` 画；方块模型 `checkout_counter/gate.json` 只有粒子贴图。贴图和柜台共用 `checkout_counter` 图集。
- 骨骼和通道（两个通道都跟着 `open`，同时开始）：

| 骨骼 / 通道 | 绕哪转 | 角度 | 时长 | 缓动 |
|---|---|---|---|---|
| `flap`（翻板：台面 + 铝收边） | 门轴一侧的台面边线（x -8，y 16，沿 z） | 180°（`hinge=right` 为 -180°） | 16 ticks（0.8 秒） | `ease_in_out` |
| `door`（下门、细铝线、拉手、铰链） | 门轴（x -7.6，z -4.6，竖直） | -90°（`hinge=right` 为 90°） | 8 ticks（0.4 秒） | `ease_out` |

- 运行时不支持延迟起步，所以先后顺序靠时长和缓动：开门时下门很快推开、慢慢停住，翻板抬起、竖直、再翻过去，0.8 秒落到旁边柜台上；关门时下门越关越快，0.4 秒"咔"地碰到挡块，翻板 0.8 秒落回。翻板始终在台面平面（y 16）以上，下门最高 14.8，两者转动时不会互相穿过（生成器会检查）。
- 渲染包围盒（朝北、方块局部坐标）：左门轴 `[-1.016,0.059,0.119]..[1.016,2.016,1.178]`，右门轴是它的镜像，包含翻板转到竖直时伸到上面一格、落到旁边一格的整个行程。
- 音效：`checkout_counter_gate_open` / `checkout_counter_gate_close`（`sounds/checkout_counter_gate/{open,close}.ogg`，单声道 48 kHz，衰减距离 12），字幕"柜台通道门打开 / Counter gate opens""柜台通道门关上 / Counter gate closes"。由 `tools/build-checkout-counter-gate-sounds-v1.mjs` 从两段素材切出各个声音、放到动画的对应时刻：开门 0.08 秒抬板的轻响、0.30 秒前后下门转开时的铰链声、0.80 秒翻板落到邻居台面上；关门 0.40 秒下门碰到挡块、0.80 秒翻板落回。同一段素材里的几个声音保持原来的相对响度；整体响度比工业储物柜门低 3 LU（贴面柜台比钢柜轻）。每次音调随机 ±3%。
- 物品模型：关着的通道门（`checkout_counter/gate_item.obj`，静态）。
- 可编辑源：`src/main/blockbench/checkout_counter_gate_v1.bbmodel`（左门轴的骨骼版：组 `flap`、`door`，组原点就是转轴；右门轴由生成器镜像）；整套柜台的 `checkout_counter_v1.bbmodel` 里也有这两个组（`gate_flap`、`gate_door`）。
- **背柜**：从正面（上下两半都行）右键，打开 3×4 搜索 / 查看界面，准星提示同上。

## 容器与货物

都接入逐格搜索（[progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md)）和通用货物（[container_goods_v1.md](../gameplay/container_goods_v1.md)）：

| | 柜台（两款） | 背柜 |
|---|---|---|
| 格数 / 布局 | 9，3×3（`GRID_3X3`） | 12，3×4（`GRID_3X4`，2026-10-04 新增：3 列摆在原版 4 行箱子面板正中） |
| 搜索速度 | 每格 20 ticks（1 秒），±15%；开放的格子和托盘，比翻柜子快 | 同左 |
| 战利品 | 第一次服务端 tick 时生成（从外面就能看出满不满），格子仍要搜；玩家放置的不用搜 | 同左 |
| 货物格位 | 柜台下 8 格（两个格子 × 底板 / 隔板 × 每层两件，正面朝收银员，缩放 0.8）；带货架款再加托盘 15 格（三层 × 五件，矮而浅：x 0.58、y 0.62、z 按托盘深度） | 4 层隔板 × 5 = 20 格（x 0.68、y 0.72、z 0.6） |
| 全部摆满 | 6 格有东西 | 8 格有东西 |
| 主题 → 商品 | `generic`：纸盒、零食袋、药盒、易拉罐；`grocery`：再加饮料盒；`pharmacy`：药盒、药瓶、牙膏盒；`hardware` / `industrial`：零件盒、喷漆罐、纸盒 | `generic`：纸盒、牙膏盒、药盒、药瓶；`grocery`：纸盒、牙膏盒、药盒、零食袋；`pharmacy`：药盒、药瓶、牙膏盒；`hardware` / `industrial`：零件盒、喷漆罐、纸盒 |

- 只有直段画货物；转角件的内部是死角，搜得到但不画货物。
- 渲染：`AflGoodsLibrary.drawPlaced`（2026-10-04 新增）按格位给出位置、朝向和三个方向的缩放，托盘上的货物因此可以是矮而浅的。柜体本身是烘焙的静态模型。
- 测试：`/dev container_search spawn apocalypse_firstlight:checkout_counter_display theme grocery fill 6`、`/dev container_search spawn apocalypse_firstlight:back_bar_shelf fill 8`。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-checkout-counter-v1.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 OBJ 和贴图） |
| 可编辑源 | `src/main/blockbench/checkout_counter_v1.bbmodel`（Free Model，每个零件一个组：`straight`、`rack`、`outer`、`inner`、`cap`、`gate_flap`、`gate_door`）、`src/main/blockbench/checkout_counter_gate_v1.bbmodel`（通道门的动画骨骼版）、`src/main/blockbench/back_bar_shelf_v1.bbmodel`，以及 `src/main/blockbench/textures/<id>_v1{,_s,_n}.png` |
| 运行时模型 | `models/block/checkout_counter/{straight,rack,outer,inner,cap_left,cap_right,gate_item,item,item_display}.obj/.mtl/.json`，通道门的方块模型 `checkout_counter/gate.json`（只有粒子贴图）和动画资产 `geo/checkout_counter_gate_{left,right}.geo.json`、`meshes/checkout_counter_gate_{left,right}.aflmesh.json`、`block_mesh_profiles/checkout_counter_gate_{left,right}.json`；`models/block/back_bar_shelf/lower.obj`（整个背柜）和 `upper.json`（只有粒子贴图）。全部是 `forge:obj`（方块单位，`flip_v`、`shade_quads`、`automatic_culling: false`、`ambientocclusion: false`） |
| 方块状态 | 生成器写出：柜台两款是 multipart（按 `facing` + `shape` 放零件，带货架款在直段加托盘，没连接的一侧加封板）；通道门所有状态都用同一个粒子模型（由方块实体渲染器画）；背柜按 `facing` / `half` |
| 贴图 | `textures/block/checkout_counter{,_s,_n}.png`（1024，8.75 texel/px，490 个 UV 岛）；`textures/block/back_bar_shelf{,_s,_n}.png`（1024，13.5 texel/px，258 个 UV 岛） |
| 三角面 | 直段 264、托盘 248、外转角 228、内转角 168、封板 28、通道门 180；背柜 520 |
| 共面重叠 | 0（每个零件单独检查） |

坐标：格子底面中心是原点，px，`FACING=north` 时顾客在 -Z、收银员在 +Z。

- **直段**：台面 y 15–16（和方块顶面齐平），向顾客一侧伸到 z -5.6，前沿包一条铝收边（z -5.85..-5.6，y 14.85–16）；顾客面板 z -5..-4.2；黑色踢脚线 y 0–1.2，前后都往里缩；顾客面 y 2–2.35 一条细铝线。收银员一侧是开放格：两个格子（中间隔板），底板 y 1.2–2.0，一块隔板 y 7.0–7.4，上沿一根横档（y 13.6–15）。两端是 0.8 px 的侧板，连成一排时背靠背。
- **托盘**（带货架款的直段）：三层，底面 y 2.6 / 6.2 / 9.8，深 2.8 / 2.6 / 2.4 px，前沿挡边高 0.65 px，两侧立柱。
- **外转角**：顾客面朝 -Z 和 +X，这条线往 -X 延伸，拐过去的那条往 +Z 延伸。台面外角是半径 1.6 px 的圆角，铝收边和细铝线沿外沿绕过去不断开；内角有一根小立柱，挡住两边柜台之间的缝。
- **内转角**：顾客面朝 -Z（x < -5 的一段）和 -X（z < -5 的一段），往 -X 和 -Z 两个方向延伸；收银员一侧（+Z、+X）是封闭的柜板。
- **封板**：-X 端，0.35 px 厚，伸出格子 0.35 px，盖住台面和面板的端面；+X 端用镜像。转角件没连接的一端用同一块封板，按那条线的朝向转过去。
- **通道门**：台面（带铝收边）是可以掀起的翻板；下门 x -7.6..7.6，y 1.2–14.8，顾客面上有一条细铝线，收银员一侧一个铝拉手，门轴一侧两个钢铰链。开着时：翻板绕门轴一侧的台面边线（x -8，y 16）翻 180°，叠在邻居的台面上（y 16–17，高出方块顶面 1 px）；下门绕 (x -7.6, z -4.6) 转 90°，伸向收银员一侧，伸出格子 2.6 px。`hinge=right` 是镜像。开关过程见"通道门动画与音效"。
- **背柜**：背板 z 7.2–8；下面是带两扇门的柜子（门 z -1.0..-0.4，竖向铝拉手），台面 y 10–10.8 带铝收边；上面两侧立板，4 层浅隔板（y 13.5 / 17.8 / 22.1 / 26.4，深 z 2.0..7.2），每层前沿一条空白的铝价签槽；顶上 y 30–32 是深色的空白价目带。

### 材质（LabPBR）

基础色都带低频纹理（模型坐标里的平滑值噪声，一个纹理像素一个值，没有逐像素噪点）：

| 材质 | 用在 | Base Color | 纹理 | smoothness / F0 |
|---|---|---|---|---|
| `laminate` | 柜体、侧板、门、背柜柜体 | 深灰 [60,62,66] | 沿零件长边的横向细纹（两种尺度，±5% / ±2.5%）加柔和的云纹（±2%） | 98–120 / 21 |
| `top` | 台面、背柜台面 | 暖浅灰 [186,183,176] | 人造石一样的云纹（±4.5%），再叠两层更小的柔和斑块（±2.5%、±1.2%） | 134–150 / 21 |
| `trim` | 铝收边、细铝线、拉手、价签槽 | [164,168,174] | 沿长边的拉丝纹（±4.5%） | 150–170 / 255（金属） |
| `kick` | 踢脚线 | [31,32,34] | 无 | 66–74 / 20 |
| `shelf` | 柜内底板、隔板、背柜隔板 | [122,126,132] | 喷涂面的轻微斑驳（±2.5%） | 112–132 / 24 |
| `rack` | 托盘、立柱 | [86,90,96] | 同上（±1.5%） | 118–138 / 24 |
| `header` | 背柜价目带 | [38,40,44] | 很淡的横纹 | 72–84 / 20 |
| `steel` | 通道门铰链 | [138,142,148] | 无 | 140–160 / 255 |

`_n` 是平法线，B 通道存 AO；不印字、不画 logo、没有磨损。

### 碰撞 / 选中形状

每种形状一到两个盒子，和模型一起按方块状态旋转（px，`FACING=north`）：直段 `[0,0,2.15]..[16,16,15.6]`（带货架款从 z 0.2 开始）；外转角 `[0,0,2.15]..[13.85,16,16]`；内转角两个盒子 `[0,0,2.15]..[15.6,16,15.6]` + `[2.15,0,0]..[15.6,16,2.15]`；通道门关着同直段，开着只剩门轴一侧的门（`[0,0,3.2]..[1.3,14.8,16]`，`hinge=right` 镜像），可以走过去；背柜下半格 `[0,0,6.5]..[16,10.95,16]` + `[0,10.95,9.4]..[16,16,16]`，上半格 `[0,0,9.2]..[16,16,16]`。

## 建造桥（开发工具）

`AuthoringFixtureRegistry` 加了四个 fixture（类别 `retail`）：两种柜台是 `STORAGE_WITH_INVENTORY`，`shape` 和四个侧面标记由邻居计算（不要手填，先摆好整条线，转角会自己出来）；通道门是 `SAFE_FIXTURE`，变体只有 `hinge`，`open` 固定为 `false`；背柜是两格高的 `STORAGE_WITH_INVENTORY`。柜台加入了 `shapeSafe`（它的 `updateShape` 只算连接，不会掉东西），可以被 `reconcile_shapes` 重算。见 [minecraft_authoring_mcp_v1.md](../dev/minecraft_authoring_mcp_v1.md)。

## 已知限制

- 转角件不画货物（内部是死角）。
- 通道门开着时，翻板叠在门轴一侧的邻居上；门轴一侧没有柜台时，翻板悬在半空。
- 两条朝向不同的柜台并排挨着时，彼此算"有连接"，不会加封板，但几何上接不上（和原版楼梯拼法的局限类似）。
- 没有比较器输出。

## 需要实机验证

- 直线、L 形（外转角）、内转角、U 形拼法下的形状和封板是否都对，放置顺序不同时结果是否一样；
- 通道门：放置时门轴选得对不对，开 / 关的动画（翻板和下门的先后、落点）、音效和动画是否对上、碰撞（开着能走过去）和提示；
- 柜台和背柜的搜索 / 查看、3×4 界面、货物显示（柜台下、托盘、背柜隔板）和主题；
- 台面上放收银机等物品（2026-10-04 改过：台面加高到 16 px 并加了顶面支撑，没有实机验证）；
- 生存模式挖掘（铁镐）和掉落，背柜敲上半格时的掉落；
- 光影下的材质：台面云纹、柜体细纹、铝收边。
