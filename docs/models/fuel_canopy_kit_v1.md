# Fuel Canopy Kit V1（加油站顶棚套件）

状态（2026-10-04）：**已实现**，`compileJava --offline` PASS。用户实机看过三轮，第三轮 **PASS**：
- 第一轮：地面底座下面有一道能看穿的缝（生成器删隐藏面时把墩脚那一层的顶面也删了），底座不满一格、下面接电缆会露缝。已修：只删顶棚方块里的隐藏面，地面底座加满格基础垫。第二轮用户确认立柱正常。
- 第二轮：两套光影下吊顶都有棋盘格（相邻格亮暗交替）。原因见下面"光照"：平滑光照按顶点序号分角落亮度，OBJ 的顶点顺序不对。已修：顶点按原版 `FaceInfo` 顺序写出，边檐四个朝向预先转好。2026-10-04 修复后用户实机 **PASS**（吊顶不再有棋盘格）。嵌入路缘、Survival 挖掘掉落没有单独的检查记录。

## 是什么

加油站顶棚，用方块在游戏里搭（顶棚一般 20×10 米上下，以后也由程序生成）。顶棚一格厚，吊顶底面离地 5 格。用户 2026-10-04 定下：
- 拆成方块组件：立柱、吊顶、灯板、边檐；
- 灯嵌在吊顶里（不是现有的室内吸顶灯，也不是灯带）；
- 不走真实线缆：只在立柱底座的底面留一个电源接口，柱子和顶棚自己带线；
- 吊顶做成一整片平面（没有拼缝）；边檐用褪色红（和加油机顶部同色）；立柱保持深灰色。

| 注册 ID | 名称 | 堆叠 | 说明 |
|---|---|---|---|
| `apocalypse_firstlight:fuel_canopy_column` | 加油站顶棚立柱 / Fuel Canopy Column | 16 | 一格一节往上叠；最下面一节带混凝土墩和电源接口 |
| `apocalypse_firstlight:fuel_canopy_ceiling` | 加油站顶棚吊顶 / Fuel Canopy Ceiling | 64 | 下面是平整的吊顶，上面是屋面 |
| `apocalypse_firstlight:fuel_canopy_light` | 加油站顶棚灯板 / Fuel Canopy Light | 64 | 吊顶中间嵌一盏方形 LED 灯 |
| `apocalypse_firstlight:fuel_canopy_fascia` | 加油站顶棚边檐 / Fuel Canopy Fascia | 64 | 顶棚四周的褪色红边带，直段 / 外角自动 |

- 代码：`block/FuelCanopyNetwork`（连通和供电）、`block/FuelCanopyColumnBlock`、`block/FuelCanopyCeilingBlock`、`block/FuelCanopyLightBlock`、`block/FuelCanopyFasciaBlock`、`blockentity/FuelCanopyColumnBlockEntity`、`item/FuelCanopyColumnItem`；创造标签页 `furniture`，在防撞柱之后。
- 挖掘：钢结构，四个方块都是 `minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`，`requiresCorrectToolForDrops()`；硬度 / 抗性 5.0 / 6.0，`SoundType.METAL`。掉落表各掉自己（`survives_explosion`）。嵌在路缘里的立柱底座拆掉后，下一 tick 把路缘放回原处（和加油机一样，路缘不掉落）。
- 重量（`item_mass`，估计）：立柱 4、吊顶 1.0、灯板 1.2、边檐 1.2 kg（建材的搬运单位）。
- 都是 `noOcclusion()`：模型里凹进去的面（灯框、灯面）按方块自己位置的光照着色，实心方块会让它们发黑。所以顶棚对天光只挡 1 级。

## 方块状态

- **立柱** `fuel_canopy_column`：`facing`、`segment`（`base` / `shaft`）、`island`、`top`。
  - `segment` 自动：下面不是立柱就是 `base`（满格基础垫、混凝土墩、底板和四个地脚螺母，底面中心是电源接口），否则 `shaft`（光管）。
  - `top` 自动：上面是顶棚方块（吊顶 / 灯板 / 边檐）时加柱头（加宽箍带和盖板，盖板下四个螺母）。
  - `island`：对着直段路缘的顶面放置时（`FuelCanopyColumnItem`），底座占掉那一格路缘，`facing` 跟路缘一样，模型自己画那一段路缘，电源接口嵌在路缘底层的方孔里。加油岛端头会把它当成岛的一段来接（`FuelIslandCurbBlock.isIslandPiece`）。
  - 碰撞箱：管 3.5..12.5，地面底座加满格基础垫 y 0..1、墩 1.5..14.5 到 6.4，路缘上的底座是满格路缘 0..3 加墩 3..9.4，柱头 2.4..13.6（y 14.6..16）。
  - 立柱叠到 5 节（地面 0..4 格），第 5 格就是吊顶层。
- **吊顶** `fuel_canopy_ceiling`：没有状态，整格。
- **灯板** `fuel_canopy_light`：`lit`，由网络控制，亮时亮度 15。
- **边檐** `fuel_canopy_fascia`：`facing`（红面朝外）、`shape`（`straight` / `outer_left` / `outer_right`）。
  - 放置时自动朝外：一边是顶棚方块、对边不是，就朝没有顶棚的那边，否则朝玩家。
  - `shape` 像楼梯那样算：身后那格是边檐、并且朝向是自己的左边（逆时针）或右边，就是外角（同时露出那一面）。
  - 只有外角，不做内角（L 形、凹进去的顶棚还不支持）。

## 供电（顶棚自带线）

- 电源接口只有一个：立柱底座（`segment=base`）的**底面**，标准 AFL 接口（[power_cable_v2.md](power_cable_v2.md) 的"地下供电设备"例外），电缆从下面一格的地下接上来。
- 连通（`FuelCanopyNetwork`）：立柱只和上下相邻的立柱 / 顶棚方块连；吊顶、灯板、边檐和六个方向相邻的顶棚方块连。连在一起的是一个网络，最多扫描 4096 格（只算已加载的区块）。
- 每 20 ticks，每个底座扫描自己的网络；位置最小的底座是控制者：
  - 本周期用电 = 灯数 × `light_fe_per_tick` × 20，从网络里所有底座的缓存里扣；
  - 灯亮着时，够付就继续亮；灭着时，要攒够两个周期的电（最多所有底座的容量）才亮，弱供电不闪；
  - 整个网络的灯一起亮、一起灭。
- 顶棚或立柱被拆掉后，下一 tick 检查断开后的每一块：没有底座的那块，灯全部熄灭。
- 数值：`data/apocalypse_firstlight/machine_balance/fuel_canopy.json`（`MachineBalanceManager.fuelCanopy()`，lights-only）：每个底座 `capacity_fe` 2000、`max_receive_fe_per_tick` 200，每盏灯 `light_fe_per_tick` 1。一个底座最多带 100 盏灯，更多就要再接一根立柱。

## 资产

| 项 | 内容 |
|---|---|
| 生成器 | `tools/build-fuel-canopy-v1.mjs`（纯 Mesh，`tools/cube-slab-mesh-lib.mjs` + `tools/afl-power-port.mjs`；`--check` 校验输出，`--preview DIR` 只写预览） |
| 立柱（`COLUMN`，px） | 倒角方管 r 4.5（倒角 0.9）；地面底座的基础垫满格 16×16、高 1.0，接口嵌在它底面的方孔里；墩 r 6.5（倒角 1.2）顶在 5.0，上面一层 r 6.0 高 0.6；底板 r 5.4 厚 0.4；地脚螺母在对角线 ±4.45；柱头箍带 r 4.9（y 14.6..15.4），盖板 r 5.6（y 15.4..15.96），螺母 ±4.75 |
| 吊顶 / 灯板（`LIGHT`） | 吊顶板 y 0..0.6，屋面 0.6..16；灯孔 ±5.7，灯框 y −0.2..0.6（灯孔四周四条矩形，不倒角），灯面 ±5.07 凹进 0.3 |
| 边檐（`FASCIA`） | 红面板 z −8..−7.0、y 1.1..14.9；滴水边 z −8.35..−7.0、y −0.4..1.1（比吊顶低 0.4）；压顶 z −8.35..−5.9、y 14.9..16.15；后面接吊顶和屋面。各件只贴合不重叠；外角的每一件是两个盒子拼成的 L |
| 材质 | 边檐褪色红 [142,58,50]（越往上越浅）；铝色压边 [118,121,123]；吊顶 [140,142,140]；屋面 [92,94,95]；立柱深灰 [118,122,126]；钢板 [104,108,112]；灯框 [120,123,126]；灯面未亮 [168,170,166]，亮 [255,246,228]。连续的件（吊顶、屋面、边檐、立柱管）没有边缘高光，颜色不随延伸方向变化，相邻方块之间看不出接缝。没有画上去的装饰 |
| 自发光 | 亮的灯面 `_s` alpha 254（LabPBR 满自发光），模型 `light_lens_lit.mtl` 带 `Ka 1 1 1`（Forge 全亮烘焙） |
| 可编辑源 | `src/main/blockbench/fuel_canopy_v1.bbmodel`，贴图 `src/main/blockbench/textures/fuel_canopy_v1{,_s,_n}.png` |
| 运行时模型 | `models/block/fuel_canopy/{column_base,column_base_curb,column_shaft,column_head,ceiling,light,light_lens,light_lens_lit,light_item}.obj/.mtl/.json`，边檐 `fascia_{north,east,south,west}` 和外角 `fascia_corner_{north,east,south,west}`（朝向预先转好，blockstate 不再用 y 旋转；外角 `outer_left` 用自己朝向的那个，`outer_right` 用顺时针下一个）；`forge:obj`。blockstates `fuel_canopy_{column,ceiling,light,fascia}.json`（立柱、灯板 multipart） |
| 光照 | 吊顶、灯板、边檐的模型开平滑光照（`ambientocclusion: true`；平直光照下每格吊顶只取一个光照值，一格一格深浅不一）。原版平滑光照（`ModelBlockRenderer.AmbientOcclusionFace`）按顶点序号、以 `FaceInfo` 的顺序分给四个角亮度；原版方块都按这个顺序烘焙，Forge 的 OBJ 照搬文件里的顺序，blockstate 旋转也不重排，顺序不对时每格的明暗方向错开，拼起来成棋盘格（2026-10-04 实机）。所以这三个模型只用轴对齐矩形（`quadBox`，不用三角化的盖面），导出时每个面按 `FaceInfo` 顺序重排（`FACE_INFO`、`faceInfoOrder`，不是矩形或绕向不对就报错），边檐的旋转写进模型。立柱和灯面仍是平直光照 |
| 贴图 | `textures/block/fuel_canopy{,_s,_n}.png`（2048，14.75 texel/px，839 个 UV 岛） |
| 统计 | 三角形：底座 664、嵌路缘底座 716、柱管 28、柱头 288、吊顶 20、灯板 90 + 灯面 10、边檐 68、外角 104；共面 0 |
| 物品栏 | rotation `[30,225,0]`，scale 0.62；translation：立柱 `[0,0.866,0]`、吊顶 / 灯板 `[0,0,0]`、边檐 `[-0.077,0.146,0]` |

## 作者工具

`AuthoringFixtureRegistry`：`fuel_canopy_column`（`segment` / `top` 由邻居算，`island` 固定 false）、`fuel_canopy_ceiling`、`fuel_canopy_light`（`lit` 固定 false）、`fuel_canopy_fascia`（`shape` 由邻居算）。

## 已知问题 / 以后

- 没有实机验证。
- 边檐只有直段和外角，没有内角。
- 顶棚只挡 1 级天光（`noOcclusion`）。
- 灯只看整个网络够不够电，不分区。
