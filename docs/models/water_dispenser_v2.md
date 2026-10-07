# Water Dispenser V2（饮水机 V2：Mesh + PBR + 接电）

状态（2026-10-01）：
- **模型、PBR、电源接口和指示灯已重做，用户 2026-10-02 实机暂时 PASS**（桶的透明度改过之后）。
- 用户 2026-10-02 实机看过（Sundial、Complementary、不开光影）：只提了桶在 Sundial 下太透明，已改（见"材质"），改后用户暂时 PASS。
- 2026-10-02 改：背面原来是一个深灰压缩机罩，接口板装在罩子上，用户说看着像音箱，要在机身后面做凹槽、不要凸起。现在整台往后挪，后背贴在方块边界上，接口和散热网格都嵌在后背的凹槽里（见"电源接口与指示灯"）。
- `compileJava --offline` PASS（包括 `src/dev`）。GameTest 按规则没有运行。
- 用户 2026-10-01 定的范围：只做模型 + 接电 + PBR，**完全不管桶里的水**（没有水体、没有水位、没有喝水），玩法以后再说。所以仍然是装饰方块，只是能接电亮指示灯。

V2 取代 V1 文档（原 `docs/models/water_dispenser.md`，已删除，仍然有效的内容并入本文）。V1 的这些文件已删除或被覆盖（都在 git 里）：
- 方块源模型 `src/main/blockbench/water_dispenser.bbmodel`（160 个方块，被 V2 生成的 Mesh 源覆盖）；
- V1 的建模 / 导出 / 检查脚本 `tools/water-dispenser-{body,bottle,detail,finish,hollow,nonoverlap,overlap-audit}.blockbench.js`、`tools/water-dispenser-mcp.mjs`、`tools/export-water-dispenser-render.mjs`、`tools/verify-water-dispenser.mjs` 已删除；
- 运行时模型 `models/block/water_dispenser_render.json`（Forge composite：不透明层 + 半透明桶）、`models/block/water_dispenser_empty.json`（上半的空模型）已删除；`models/block/water_dispenser.json`、`models/item/water_dispenser.json`、`textures/block/water_dispenser.png` 被 V2 覆盖；
- 方块状态文件改成一个 `""` 变体（只有粒子贴图，画面由方块实体渲染器画）。

## 模型

- 生成器：`tools/build-water-dispenser-v2.mjs`（`--check` 检查输出是否最新，`--preview DIR` 只把 geo / sidecar / 贴图写到别处看）。
- 输出：
  - 可编辑源 `src/main/blockbench/water_dispenser.bbmodel`、`src/main/blockbench/textures/water_dispenser{,_s,_n}.png`；
  - 运行时 `geo/water_dispenser.geo.json`、`meshes/water_dispenser.aflmesh.json`、`block_mesh_profiles/water_dispenser.json`、`textures/block/water_dispenser{,_s,_n}.png`；
  - 物品模型 `models/item/water_dispenser.json`（`builtin/entity`，各视角由生成器算好；GUI rotation `[25,225,0]`、translation `[0.801,-3.198,-0.172]`、scale 0.453，正面朝外）、方块模型 `models/block/water_dispenser.json`（只有粒子贴图）。
- Pure Mesh，512 atlas，5.75 texel/px，26 个部件，共 2588 个三角面，共面重叠检查 0 处：

| 骨骼 | 内容 | 三角面 |
|---|---|---:|
| `body` | 机身、底座、出水凹槽、出水口、接水盘、面板、下柜门、桶座、纸杯筒、背面两个凹槽、电源接口、散热网格 | 1996 |
| `bottle` | 19 升桶（半透明层） | 544 |
| `lights` | 两颗指示灯，暗的那套 | 24 |
| `lights_lit` | 同样的几何，亮的那套（geo 里 `neverRender`） | 24 |

- 没有动画。坐标（源 px）：方块底面中心是原点，y 0..32 占上下两格，正面朝 -Z，+X 是正对机器时的**左手边**。NORTH 朝向时方块内 px x = 源 x + 8、z = 源 z + 8。

### 布局（正对机器看）

- 机身：x ±5.6、z −3.0..8.0（后背贴在方块边界上，前面离方块正面 5 px：饮水机靠墙放，背后没有东西凸出来），四条竖棱是半径 1 的圆角；y 0.8..20.6，下面是略往里收的深色底座（高 0.8），上面一块带倒角的顶盖（20.6..21.2）。机身沿 y 分 7 段拼起来，每段按需要在前面切出水凹槽、在后面切凹槽。
- 出水凹槽：y 9.6..17.6、x ±4.0，往里 2 px（到 z −1.0），内壁、顶和底都是深灰。
  - 顶上挂两个按压式出水口（x ±1.9）：深灰出水口 + 往下的出水嘴，正面一个按钮，左边红色是热水，右边蓝色是冷水。不写字。
  - 底上是接水盘（x ±3.6，比凹槽口往外伸 0.35 px），里面 5 根格栅。
- 凹槽上方一块深灰面板，上面两颗指示灯：左边加热（橙），右边制冷（绿），各在对应出水口上方。
- 凹槽下方是下柜门（略凸 0.12 px 的一块门板），顶边中间一个深色抠手。
- 右侧面（正对时的右手边）挂一根纸杯筒：x −6.6、z 0.8、半径 0.95，y 11.6..18.2，两端深色箍，底下露出一截纸杯，两个卡子连到机身。
- 顶上：深灰桶座（半径 2.4，高 0.7，在机身正中 z 2.5），19 升桶倒插在上面：瓶颈朝下、肩部、桶身（半径 4.7）两道握手凹槽、平底朝上，最高 31.6。桶身 16 段。
- 背面是平的，在方块边界上，开两个凹槽：
  - 上面的凹槽 x ±4.4、y 12.2..20.0、深 1 px，里面是黑色冷凝散热网格（两根竖框、10 根横线、6 根竖管），全部在边界以内；
  - 下面的凹槽 x ±3.6、y 4.4..11.6、深 1.2 px，里面是标准电源接口（见"电源接口与指示灯"）。

### 材质

全部是涂层 / 电介质（LabPBR F0 20，桶 10），没有印刷、标签和标志：

| 材质 | 用在 | Base Color | 光滑度（面 / 倒角） |
|---|---|---|---|
| `shell` | 机身、顶盖、下柜门 | 150, 148, 140（旧米灰塑料，哑光，不用白色免得光影下发白） | 92 / 108 |
| `panel` | 凹槽、面板、桶座、杯筒箍、卡子、抠手 | 46, 48, 52 | 100 / 118 |
| `plinth` | 底座 | 36, 37, 40 | 60 / 64 |
| `tap` | 出水口 | 60, 62, 66 | 110 / 128 |
| `hot` / `cold` | 两个按钮 | 150, 54, 44 / 52, 86, 150 | 120 / 136 |
| `tray` / `grate` | 接水盘 / 格栅 | 72, 74, 78 / 104, 106, 110 | 104–120 |
| `tube` / `cup` | 纸杯筒 / 纸杯 | 164, 162, 154 / 188, 184, 174 | 120 / 70 |
| `grille` | 散热网格 | 24, 25, 27 | 90 |
（凹槽的内壁是机身的米灰塑料；接口后面的支座是 `panel`。）
| `bottle` | 桶 | 104, 156, 200，alpha 110 | 165（F0 10） |

桶是一层半透明的空壳，里面没有水。第一版是 124, 170, 204、alpha 72、光滑度 228：用户实机（2026-10-02）发现 Sundial 下背景是天空时桶几乎看不见（又薄又光滑，只剩对天空的反射），Complementary 下也偏淡，不开光影没问题；所以改得更实、更蓝、不那么光滑。改后用户暂时 PASS。以后做水的时候，桶和水要各自单独成骨骼（这次没做）。

## 电源接口与指示灯（2026-10-01，只有灯）

- 接口：`tools/afl-power-port.mjs` 的标准接口（`addPowerPort`，默认深度），下半格背面中心（源 x 0、y 8），接口本身（6 × 6 px 钢板、r 1.95 插座、触点、深 0.6 px）和其他机器一样。它嵌在后背的凹槽里：凹槽比接口板每边大 0.6 px、深 1.2 px，接口板面在方块边界（z 8）上、和后背齐平，四周露出一圈凹槽缝；板子后面一块深灰支座连到凹槽底。线缆的插头正好顶到边界，和接口板面贴上。
- 修改经过（2026-10-02）：第一版机身后背在 z 5.4，接口装在一个深灰压缩机罩（8 × 11 px）上凸到边界，浅色接口板加黑色圆插座装在黑罩子上，用户说像音箱。我先改成一块机身同色的凸起适配座，用户说意思是"在机身后面做凹陷，不是凸起来"。所以整台往后挪 2.6 px，后背放到边界上，接口嵌进凹槽。用户定的规则：接口形状每台都一样，接口怎么装到机身上可以按方块自己的样子做。
- 接口板面必须在方块边界上，线缆的插头才能贴上（插头只画到边界），所以"凹槽"是接口周围凹下去，接口板本身和后背齐平，不往里缩。
- 代码：`WaterDispenserBlock` 实现 `AflPowerPortBlock`，`hasPowerPort` 只对下半格的背面返回 true；能量能力只在这一面给（`WaterDispenserBlockEntity#getCapability`），只接收不输出。
- 数值在 `data/apocalypse_firstlight/machine_balance/water_dispenser.json`（缺失或无效时用同样的默认值）：内部缓冲 20 FE、最大输入 32 FE/t、灯 1 FE/t。和售货机一样用 `energy/CompressorAppliance` 的只有灯模式，没有压缩机运转、没有声音。亮灯规则相同：缓冲够付灯的电就一直亮；灭了以后等缓冲充满才重新亮；断线后最多再亮约 1 秒。
- 亮灯时：上下两半方块状态 `lit=true`；两颗指示灯换成亮的那套（加热橙 236, 132, 52、制冷绿 84, 212, 112），全亮度渲染，贴图 `_s` 带 LabPBR 自发光 210。**不发方块光**（灯很小）。
- 没电时：暗的那套（暗红、暗绿）。
- 两颗灯现在一起亮灭。以后做冷水 / 热水时可以分开控制（要把两颗灯拆成两组部件）。
- 存档保存缓冲电量（`EnergyStored`）；拆掉时缓冲丢失。

## 方块（沿用 V1，除了标出的改动）

- Registry ID `apocalypse_firstlight:water_dispenser`；名字 饮水机 / Water Dispenser；"黎明启示录 · 家具与设施" Creative Tab。
- 1×1×2，上下两半真实占格；朝向放置者。属性 `facing`、`half`，**V2 新加 `lit`**（两半同步：`WaterDispenserBlock#setLit`，上半在 `updateShape` 里也跟着下半）。
- **V2 改**：下半有方块实体 `WaterDispenserBlockEntity`（注册 ID `water_dispenser`），渲染形状 `ENTITYBLOCK_ANIMATED`，由通用的 `AflAnimatedBlockMeshRenderer` 画；物品由 `AflStaticMeshItemRenderer` 画（`client/AflStaticMeshItemClient` 绑定），灯是暗的。
- **V2 改**：选中框和碰撞用同一套贴合模型的盒子（北向方块 px）：
  - 下半：机身 2.4..13.6 × 0..16 × 4.65..16（含接水盘的边，后背到边界），纸杯筒 0.4..2.4 × 10.9..16 × 7.7..9.9；
  - 上半：机身顶部（含面板和指示灯）2.4..13.6 × 0..4.6 × 4.75..16，顶盖 2.7..13.3 × 4.6..5.2 × 5.3..15.7，纸杯筒 0.4..2.4 × 0..2.3 × 7.7..9.9，桶座和桶 3.3..12.7 × 5.2..15.6 × 5.8..15.2；
  - 其他朝向由北向旋转得到。V1 是"一个外包络做选中框 + 多个盒子做碰撞"，现在两者相同。
- 挖掘（不变，2026-10-01 复查）：硬度 3、爆炸抗性 5，`requiresCorrectToolForDrops()`；`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`（铁镐及以上）。它是办公家电（塑料机身），不按工业设备的钻石级算。掉落：下半按战利品表 `loot_tables/blocks/water_dispenser.json` 掉 1 个；挖上半时由方块代码移除下半，工具对才掉 1 个；创造模式不掉。
- 没有 GUI、储物、喝水、冷热水、流体能力、动画、交互音效、配方。世界生成里有它的结构（`convenience_store_01`、`gas_station_01`、已于 2026-10-06 退役的 `office_midrise_01` 历史 NBT）沿用原来的方块状态，`lit` 取默认值 false，放置时会创建方块实体。

### 已知问题：V2 之前放下的饮水机

V1 没有方块实体。V2 之前已经放在存档里的饮水机（例如开发世界里搭好的办公楼）读档时不会自动补上方块实体，所以**看不见**（碰撞和选中框还在）。重新放一次，或者把建筑重新粘贴一次就好。新放置的、结构生成的都正常。开发阶段，没有做自动迁移。

## 代码与资源

| 文件 | 内容 |
|---|---|
| `tools/build-water-dispenser-v2.mjs` | 模型、贴图、geo / sidecar / profile、物品模型 |
| `block/WaterDispenserBlock.java` | 两半结构、`lit`、`setLit`、电源接口、形状、掉落 |
| `blockentity/WaterDispenserBlockEntity.java` | Mesh 宿主（两套灯）、只有灯的用电 |
| `registry/AflBlockEntities.java` | `WATER_DISPENSER` |
| `client/AflBlockEntityRenderers.java`、`client/AflStaticMeshItemClient.java` | 渲染器、物品渲染绑定 |
| `energy/MachineBalanceManager.java` | `waterDispenser()` |
| `data/apocalypse_firstlight/machine_balance/water_dispenser.json` | 用电数值 |
| `src/dev/.../authoring/bridge/FixtureBridgeGameTests.java` | 现在有方块实体，`we_set` 会先拒绝成 `UNSAFE_OR_DYNAMIC_BLOCK`；测"多格要用 place_multiblock"的例子换成 `poplar_door`（没有运行） |

## 验证

- `node tools/build-water-dispenser-v2.mjs --check`：CHECK OK。
- `compileJava --offline`：PASS。
- 离线渲染（`afl2obj` + 简单光栅器，不是游戏画面）看过正面、3/4、亮灯、背面。
- **没有实机验证**：外观、光影下的桶和指示灯、线缆接上、物品图标、选中框和碰撞都还没在游戏里看过。背面凹槽的改动只看过离线渲染。
