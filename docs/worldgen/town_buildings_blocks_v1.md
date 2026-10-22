# 城镇房子方块清单 V1

状态：**第 1 批（住宅 A、B，25 个方块）和住宅 kit `MinimalTraditionalKit`（A）、`CapeCodKit`（B）已做，用户 2026-10-21 实机 PASS（town_buildings#7#5）**；第 2、3 批（商业楼）还是清单（2026-10-21，C 分支 `feature/c-next`）。第 3 节是做出来的样子，第 4、5 节还是计划。

2026-10-21 用户看过住宅 A 第一版以后（"栏杆有点问题，底部有断的，并且还有奇怪的橙色点"，"这原版的楼梯有点丑，能不能做mesh模型？多一点台阶"，"开始做款式B？现在都是一样的"）又做了一轮，都没进游戏：栏杆修了（3.6），门廊改用自己的 Mesh 混凝土台阶（3.9），加了住宅 B（3.10、8.2），A 和 B 在小住宅地块上各一半（8.0）。

用户 2026-10-21 选了方向（见 [概念 V1](town_buildings_concept_v1.md)）：

- 小住宅做 A（最简传统式）；同日用户要求加 B（科德角式），和 A 混排；
- 商业楼 A（1910 年代红砖）为主，约 6 成；B（1955 年改过的店面）混排，约 3 成；C（1960 年代）少量，约 1 成。

这份清单列出要盖这三种楼需要的方块：哪些用现有的，哪些要新做，每个新方块的 ID、形状、方块状态、材质、挖掘和重量。ID、数值都是建议，做的时候以代码为准，再回来改这份文档。

## 1. 原则

- **外立面全用 AFL 方块**，不用原版木头、原版砖。
- **木头分树种**：门廊、后楼梯、木平台这种建筑本体按树种做。第一批只做白栎一种，沿用老宅白栎件的颜色（`tools/cabin/cabin-art.mjs` 里的 `OAK`）。
- **刷漆的件不分树种**：油漆盖住了木纹，所以只按油漆颜色分。搭接木板、窗、门、店面护板、招牌板都是这样，和老宅窗的深色漆窗扇同理。
- **贴图密度**：人造材料 240 px + LabPBR（和外墙砖、老宅一样）。
- **新模型一次做细**：门要能开，窗有窗扇和窗套，栏杆有立柱和扶手，不做占位。
- **不加配方**，新方块只放创造栏。
- **挖掘**：暂时任何等级都可以（用户 2026-10-14），但每个方块都挂对应的 `minecraft:mineable/*`，保证能掉落。下表的等级按材料选：木头用斧、不要求工具；砖、石、混凝土用镐、任何等级；金属件用镐加 `needs_stone_tool`（和老宅铁皮屋面、铁丝网一样）。
- **重量**（`item_mass`）：方块按轻的标尺（泥土 0.15 kg、石头 0.25 kg、外墙砖 0.3 kg），都是估计值。
- **生成和放置**：世界生成不会触发邻居更新，所以凡是靠邻居决定形状的状态（左右相连、转角收边、栏杆连接），都由 `BuildingKit` 直接写好。玩家放置时由 `getStateForPlacement` / `updateShape` 自己算。

## 2. 直接用现有方块

| 用在 | 方块 |
|---|---|
| 住宅地板（橡木条地板） | `red_oak_planks` |
| 住宅卫生间地面 | `restroom_floor_tile` |
| 住宅门廊台阶 | 2026-10-21 起不用原版式楼梯了，改用自己的 `smooth_concrete` 和 `concrete_stoop_steps`（3.9） |
| 住宅室内楼梯（商业楼上楼） | `white_oak_stairs` |
| 商业楼 A/B 后巷木平台 | `white_oak_porch_deck`、`white_oak_porch_post`、`white_oak_porch_railing` |
| 商业楼后门 | `steel_door` |
| 商业楼首层后场库房地面 | `sealed_concrete_floor` |
| 商业楼营业厅地面（B、C） | `porcelain_floor_tile` |
| B、C 的铝框店面 | `storefront_glazing`（含 `transom`、`mullion`） |
| B、C 的雨篷 | `metal_eyebrow_canopy` |
| C 的外墙、檐口 | `face_brick_warm_gray`、`face_brick_charcoal`、`aluminum_cornice` |
| C 的玻璃门 | `commercial_glass_double_door`、`commercial_glass_double_door_black` |
| C 楼上带形窗 | 先用 `storefront_glazing` 横排；看着不对再单做 |
| 平屋面（C，以及翻修过的 A/B） | `roof_tpo` |
| 店名字 | `channel_letter`（不亮的用法），木招牌字见 4.6 |
| 人行道、路缘 | 镇子框架已有：`concrete_sidewalk`、`curb_sidewalk`、`curb_grass` |

所以 **商业楼 C 不需要新方块**。

## 3. 住宅 A、B 的方块（第 1 批，已做）

2026-10-21 做完，**用户 2026-10-21 实机 PASS（town_buildings#7#5）**。所有资源（贴图、PBR、模型、Mesh 部件、方块状态、掉落、`TownShapeData.java`）由 `tools/build-town-house-v1.mjs` 生成，带 `--check`。Java 在 `com.antaurora.apofirstlight.townhouse.TownHouseBlocks`。Mesh 方块走老宅的 Mesh 加载器（`CabinPieces`，部件在 `cabin_mesh/roof/` 和 `cabin_mesh/town/`）。只在创造栏（AFL 建筑方块，排在老宅方块后面），没有配方。

| ID | 中文名 / English | 做法 | 挖掘 | 重量 kg |
|---|---|---|---|---|
| `lap_siding_white` / `_cream` / `_sage` / `_blue_gray` / `_tan` | 白色、奶油色、灰绿色、灰蓝色、浅褐色搭接木板 / White, Cream, Sage, Blue Gray, Tan Lap Siding | 整格 JSON，带面布尔（3.1） | 斧，不要求工具，可燃 | 0.12 |
| `asphalt_shingle_roof_charcoal` / `_weathered_brown` / `_slate` | 深灰、旧褐色、石板灰沥青瓦屋面 / Charcoal, Weathered Brown, Slate Asphalt Shingle Roof | Mesh，老宅 1:2 屋面拼块（3.2） | 斧，不要求工具 | 0.1 |
| `white_double_hung_window` | 白色双悬窗 / White Double-Hung Window | Mesh（3.3） | 斧，不要求工具 | 0.4 |
| `white_picture_window` | 白色大玻璃窗 / White Picture Window | Mesh（3.4） | 斧，不要求工具 | 0.5 |
| `house_front_door` | 住宅前门 / House Front Door | Mesh 门（3.5） | 斧，不要求工具 | 1.5 |
| `house_back_door` | 住宅后门 / House Back Door | Mesh 门（3.5） | 斧，不要求工具 | 1.5 |
| `interior_panel_door` | 室内门 / Interior Panel Door | Mesh 门（3.5） | 斧，不要求工具 | 1.5 |
| `wrought_iron_railing` | 铁艺栏杆 / Wrought Iron Railing | Mesh（3.6） | 镐 + `needs_stone_tool`，要求工具 | 0.8 |
| `parged_foundation` | 抹灰基础墙 / Parged Foundation | 整格 JSON | 镐，任何等级，要求工具 | 0.25 |
| `plaster_partition` | 灰泥隔墙 / Plaster Partition | JSON 多部件（3.7） | 镐，任何等级，徒手也能拆 | 0.15 |
| `plaster_ceiling` | 灰泥顶棚 / Plaster Ceiling | JSON（3.7） | 镐，任何等级，徒手也能拆 | 0.15 |
| `linoleum_floor` | 油毡地面 / Linoleum Floor | 整格 JSON，顶面油毡、侧面木地板层 | 斧，不要求工具，可燃 | 0.15 |
| `red_brick_common` | 普通红砖 / Common Red Brick | 整格 JSON，带面布尔（3.1），烟囱用 | 镐，任何等级，要求工具 | 0.3 |
| `limestone_coping` | 石灰石压顶 / Limestone Coping | JSON 多部件（3.8） | 镐，任何等级，要求工具 | 0.25 |
| `smooth_concrete` | 平滑混凝土 / Smooth Concrete | 整格 JSON，住宅区的平滑混凝土：门廊平台和台阶下面的实心（3.9） | 镐，任何等级，要求工具 | 0.25 |
| `concrete_stoop_steps` | 混凝土门廊台阶 / Concrete Stoop Steps | Mesh，两格一段（3.9） | 镐，任何等级，要求工具 | 0.4 |
| `steep_asphalt_shingle_roof_charcoal` / `_weathered_brown` / `_slate` | 深灰、旧褐色、石板灰沥青瓦陡屋面 / Charcoal, Weathered Brown, Slate Steep Asphalt Shingle Roof | Mesh，1:1 屋面（3.10），住宅 B 用 | 斧，不要求工具 | 0.12 |

挖掘标签：斧的进 `minecraft:mineable/axe`，镐的进 `minecraft:mineable/pickaxe`，栏杆另进 `minecraft:needs_stone_tool`，三扇门进 `minecraft:wooden_doors`。每个方块掉落自己，门和台阶只从下面那格掉。重量是估计值（`item_mass/afl_content_v1.json`）。

### 3.1 带面布尔的墙（搭接木板、普通红砖）

- 状态 `north`/`east`/`south`/`west`（布尔）：`true` = 这一面朝外，画木板或砖；`false` = 朝屋里，画灰泥。顶面和底面是灰泥。玩家放置时四面都是 `true`；`updateShape` 不改它们，只有建筑 kit（以后加上建筑锤）设。
- 搭接木板：两个相邻的面都朝外时，这条竖边上加一根白色转角板（方块状态的多部件模型）。
- `rotate()` / `mirror()` 把四个布尔一起转。
- 屋面格子下面是这种墙时，屋面把三角形山墙补成这面墙的样子：朝外的面用墙的材料，朝里的面用灰泥（部件 `town/infill_<材料>_<lower|upper|ridge|steep>_<n|e|s|w>`，材料 `lap_<色>`、`brick_red`、`plaster`；陡屋面和 lower 格一样没有 `n`）。窗或外门的上半格在下面时，按同一道墙旁边那格的墙补。

### 3.2 沥青瓦屋面

- 沿用老宅 1:2 屋面 `CabinBlocks.Roof`（状态 `facing`、`shape` lower/upper/ridge、`beam`，dynamicShape，四级台阶挡光），`ShingleRoof` 是它的子类。三片瓦错缝，贴图 240 px + LabPBR。
- 边上的件：檐口是白漆封檐板加滴水边，山墙边是白色封檐板，屋脊是压脊瓦，屋面碰到墙的地方是阶梯泛水，炉管穿过有防水套。K 形檐沟没做，留到以后。
- 屋脊两边的坡都把屋脊当作自己的坡顶，所以后坡靠屋脊那格不画顶边。
- 新状态 `valley`：`none` / `neg` / `pos` / `ridge`，是前凸山墙的坡和主屋面相交的那格，只由建筑 kit 设（玩家放的是 `none`）。`facing` 和 `shape` 用主屋面的；`neg` 是朝主屋面 `facing` 逆时针方向的山墙坡，`pos` 是顺时针方向的，`ridge` 是山墙屋脊插进主屋面的那格（主屋面的 upper 格）。天沟格本身不画边，对两边的屋面都算作相连。它的形状是两块坡板的合集。
- 天沟格不补山墙。lower 格朝檐口那一面也不补（那里是屋面板本身）。

### 3.3 双悬窗

- 沿用老宅窗的拼法：同一扇窗、同朝向上下两格拼成 1 × 2 的高窗，Mesh，`facing` = 朝外的方向。白漆窗扇、窗套、窗台、内窗台、裙板。
- 状态 `lites`：`one_over_one` / `six_over_six`。
- 清单阶段写过 `surround`（砖墙洞里的石窗台、石过梁），第 1 批没做，留给第 2 批商业楼。
- 檐下（2026-10-21）：窗最上面那格的正上方是同朝向的沥青瓦屋面（`asphalt_shingle_roof_*` 或 `steep_asphalt_shingle_roof_*`）时，不画外侧的窗头贴脸和滴水帽，两边的贴脸停在起始瓦下面（部件 `town/window_<lites>_<single|top>_eave`）。改之前住宅 A 正墙的窗头会从檐口里穿出来。
- 状态 `shutters`（2026-10-21，住宅 B 用）：`none` / `white` / `green` / `wine` / `black`。窗套两边各一扇百叶窗，平贴在墙上：梃、冒头、一排斜百叶、两道条形合页、百叶钩。高度跟整扇窗走（单格 0.9 格，两格 1.9 格），檐下短一截。部件 `town/shutters_<色>_<single|bottom|top>[_eave]`。百叶窗不算进轮廓和碰撞。
- 不挡视线，不窒息，不导红石，透光。中文名定为"白色双悬窗"，和老宅的"白栎双悬窗"一致。

### 3.4 大玻璃窗

- 固定大玻璃，`facing` = 朝外。上下左右同一种窗、同朝向的格子自己拼成一整块（中间不画窗框），四边画窗头、窗台、窗套。没有 `left`/`right` 状态：方块绘制时自己看邻居。
- 起居室前凸山墙正面用 3 格宽、2 格高。

### 3.5 门

- 原版门的行为（橡木音效），老宅门的画法：门框、门套、门槛画在门里，开着的门转 90° 靠在门轴那边的门框上。
- `house_front_door`：三格小窗、四块镶板，铜门把手。状态 `paint`：`white` / `green` / `wine` / `black`。
  - 状态 `surround`（布尔，2026-10-21，住宅 B 用）：门套外面再加一圈白漆殖民复兴式门罩：两根壁柱（带柱础和柱头）、楣板、檐口，上面一个浅三角楣（宽 1.66 格、高 0.26 格），伸到门上面那一格里，比墙面凸出 10 cm。部件 `town/door_frame_surround_<lower|upper>`（替换普通门框）。玩家放的是 `false`。
- `house_back_door`：上半九格玻璃、下半两块镶板，白漆。状态 `screen`（布尔）：门外加一扇木框纱门，跟着门一起开关。
- `interior_panel_door`：五道横镶板，白漆，玻璃门把手；门框在格子正中，放在隔墙里，往放置那一侧开。
- 轮廓和碰撞：门框，加上门扇实际画的位置；后门关着时加上纱门。

### 3.6 铁艺栏杆

- 黑色方钢扶手、底横杆、竖条，端头立柱。贴着格边放，`facing` = 贴哪条边（玩家面朝的方向）。黑铁漆，2026-10-21 去掉了贴图上的锈点（用户看到的"奇怪的橙色点"）。
- 清单阶段写的 `shape`、`post` 状态没做成状态，绘制时自己看邻居：
  - 下面是混凝土门廊台阶的上面那格、或者原版下半台阶，顺着栏杆方向爬升：扶手和底横杆顺着台阶斜下来，竖条落在底横杆上（2026-10-21 起底横杆也画，改之前竖条直接落在踏步上，用户看着"底部有断的"）；
  - 下面是台阶的下面那格（脚）：扶手斜下来，停在最后一级踏步上的一根端头立柱上；
  - 一段栏杆的两头（旁边同层、上一层、下一层都没有同朝向的栏杆）各一根立柱；平的一段接着斜的一段时，平的那头也有立柱。
- 碰撞沿着扶手，1.5 格高，不可寻路通过。

### 3.7 灰泥隔墙、顶棚

- `plaster_partition`：25 cm 厚，在格子正中，像玻璃板一样和别的隔墙、门、墙面相连（`north`/`east`/`south`/`west`）。清单阶段写的"贴在格边"改成了居中，这样室内门的门框正好在墙里。
  - 碰撞和选框跟模型一样厚，都是 6..10 px。2026-10-21 用户在游戏里发现碰撞不对：原版 `CrossCollisionBlock` 的宽度参数是半宽，原来传了 4，碰撞成了 4..12（50 cm），墙两边各多出 2 px 的空气墙。现在传 2。
  - `base`：下面是地板时墙脚画一道白漆踢脚板（门上和门洞上方的那一段没有）。
  - `ceiling`：最上一层那段自带 12 cm 顶棚，隔墙停在顶棚下面。由建筑 kit 设，玩家放置时是 `false`。
  - 可含水。
- `plaster_ceiling`：12 cm 厚，在格子的顶上（放在房间最上一格里，不是阁楼那一格），底面灰泥，顶面是搁栅和保温。

### 3.8 石灰石压顶

- 12 cm 厚，每边比砖挑出 6 cm。状态 `axis`（x/z）、`left`、`right`（这一头没有压顶接着，画端头的挑出）、`flue`（中间一节 18 cm 高的陶土烟道）。
- `left`/`right` 由 `updateShape` 按邻居算，`flue` 由建筑 kit 设。

### 3.9 混凝土门廊台阶（2026-10-21）

- 用户嫌原版式楼梯丑，要 Mesh、"多一点台阶"。`concrete_stoop_steps` 是两格一段的现浇混凝土台阶：从高一格的平台下到地面，6 级踢面（每级 1/6 格），5 个踏面（每个 1/3 格），下面那格最后三分之一是地面。踏面前沿有一道小圆角，侧面是平的混凝土。
- 状态 `facing`（高的那边，和原版楼梯一样）、`part`（`upper` / `lower`）。玩家放的是下面那格，上面那格自动放在 `facing` 方向；拆哪一格两格都没，只从下面那格掉落（和门一样）。旁边有同一段同一格的台阶时不画侧面，所以几段并排就是一道宽台阶。
- 碰撞是踏步本身，不可推动。
- `smooth_concrete`：平滑混凝土，整格，门廊平台和台阶下面的实心，以后住宅区别的平滑混凝土也用它。台阶用同一张贴图 `town/smooth_concrete`：浅暖灰，抹平或光面模板的面，只有很淡的大块斑驳和细砂，在反照率里；没有气孔、没有扫帚纹，法线几乎是平的。
  - 2026-10-21 用户看过第一版：原来叫 `stoop_concrete`（门廊混凝土），贴图每格约 200 个气孔，还压了 AO 和法线，在 AFL 光影下满是黑点。改名成 `smooth_concrete`，贴图换成上面的平滑面（`tools/town/town-art.mjs` 的 `smoothConcrete()`）。旧 ID 没留，用旧 ID 生成的测试世界里平台会变成空气。
- 住宅 A、B 的前门廊：3 格宽的平台（平台高到地板 y = 0），前面一排 3 段台阶。后门廊：1 格宽的平台加一段台阶。

### 3.10 沥青瓦陡屋面（2026-10-21，住宅 B）

- `SteepShingleRoof`，Java 在 `TownHouseBlocks`。1:1（12:12），一格升一格，只有一种格子（没有 lower / upper）。状态只有 `facing` = 檐口的方向（下坡）；玩家从檐口下面朝坡上看着放。
- 瓦和 1:2 屋面同一套三片瓦，按坡长换了一张贴图：一格坡长 1.41 m，12 道瓦（每道 11.8 cm，偶数道才能半张错缝），`shingle_<色>_steep`。
- 边上的件和 1:2 屋面一样（白漆封檐板加滴水边、山墙封檐板、碰墙的阶梯泛水），封檐板和滴水边的高度按 1:1 重新算过，不露缝也不穿出起始瓦。
- 屋脊在两格交界处：两格朝向相反、背靠背时，各画半边压脊瓦（`_steep_cap`，山墙那头再加封檐板的收头 `_steep_cap_rake_neg/pos`）。
- 下面是墙（或者墙里的窗、外门上半格）时，和 1:2 屋面一样补山墙三角，部件 `town/infill_<材料>_steep_<e|s|w>`。补墙的判断和 1:2 屋面共用一段代码（`gableInfill` / `addInfill`），旁边是陡屋面的格子也算接着补。
- 形状：8 级台阶的坡板，下面是墙时往下补满；挡光、遮挡和 1:2 屋面一样（dynamicShape，给光照 4 级台阶，给区块网格空形状，天光照下去）。

## 4. 商业楼 A 要新做的方块

### 4.1 红砖外墙（3 色）

- ID：`red_brick_common`、`red_brick_dark`、`buff_brick`
- 名称：普通红砖 / Common Red Brick；深色红砖 Dark Red Brick；米黄砖 Buff Brick
- 形状：整格，JSON。1910 年代用美国标准砖，每格 15 层（概念阶段外墙砖的方案 A）。砌法是美式砌法：每 6 层一道丁砖。砂浆偏白、凹缝；砖色每块有深浅差，跨格连续，看不出格子边界（做法同 `facade_brick_v1.md`）。
- 和住宅一样，可以有室内面：首层营业厅露砖（不用切换），楼上公寓那几格用灰泥面。状态同 3.1 的四个面布尔，默认四面都是砖。
- 挖掘：镐，任何等级，要求工具（普通装饰砖，按原版砖）。重量 0.3 kg。

### 4.2 叠涩砖檐口

- ID：`corbelled_brick_cornice_common`、`_dark`、`_buff`（各配一种砖色）
- 名称：叠涩砖檐口 / Corbelled Brick Cornice
- 形状：一格高，砖一层层向外挑出（三层叠涩），中间一道齿砖。状态 `facing`、`shape`：`straight` / `outer_left` / `outer_right` / `end_left` / `end_right`（做法参照 `aluminum_cornice`）。
- 挖掘、重量同 4.1。

### 4.3 石窗台、石过梁

- 不单做方块：画在 `white_double_hung_window` 的 `surround=masonry` 里（3.4）。窗洞上沿是一条平的石过梁，下沿是挑出 2 px 的石窗台。窗本身往墙里退 4 px。

### 4.4 石压顶、年份石

| ID | 名称 | 形状 |
|---|---|---|
| `limestone_coping` | 石灰石压顶 / Limestone Coping | 女儿墙顶上的压顶石，半格高，两边各挑出 1 px、带滴水槽；状态 `axis`（x/z）、`left`/`right`（端头收口） |
| `limestone_date_stone` | 年份石 / Date Stone | 嵌在檐口正中的整格石块，刻着楼名和年份（例如 `1912`）。字是真的几何凹刻（法线和高度都有），不是画上去的线条。状态 `facing`、`year`（几个年份，1898–1928 之间挑） |

- 挖掘：镐，任何等级，要求工具。重量 0.25 kg。

### 4.5 木框店面

- ID：`wood_storefront`
- 名称：木框店面 / Wood Storefront
- 形状：Mesh。结构参照 `storefront_glazing`，一格一块，按格子位置拼成整面：
  - `part`：`bulkhead`（底下 0.6 m 带镶板的护板，上面是橱窗玻璃）/ `glass`（整格橱窗）/ `transom`（上沿一排小格棱镜玻璃气窗）；
  - `left`、`right`：左右相连时去掉中间的边框，连起来时每隔一格留一根细竖框；
  - `facing`；
  - `paint`：深绿、酒红、黑（概念图里的三套）。
- 凹入式入口两侧的斜玻璃，在格子里做成转了 90° 的同一种方块。
- 挖掘：斧，不要求工具。重量 0.6 kg。

### 4.6 店门、入口地面、招牌

| ID | 名称 | 形状 |
|---|---|---|
| `wood_storefront_door` | 木框玻璃店门 / Wood Storefront Door | 木框、大玻璃、底下一块踢板，铜推板；状态 `paint` 同店面；沿用 `CabinDoor` 的开关和两格高。挖掘：斧，不要求工具。重量 1.5 kg |
| `hex_tile_entry_floor` | 六角马赛克地面 / Hex Tile Entry Floor | 整格，顶面是白色小六角马赛克加黑色回纹边框（1910 年代店门口的真实做法）；状态 `facing`、`border`（有没有边框）。挖掘：镐，任何等级，要求工具。重量 0.25 kg |
| `painted_sign_band` | 招牌板 / Painted Sign Band | 气窗上方的木招牌板，带上下两道线脚；状态 `facing`、`left`、`right`、`paint`。挖掘：斧，不要求工具。重量 0.4 kg |
| `wood_sign_letter` | 木招牌字 / Wood Sign Letter | 钉在招牌板上的木刻字，金色或白色漆。做法参照 `channel_letter`（每块一个字母，拼出店名）。挖掘：斧，不要求工具。重量 0.05 kg |

- 店名、字都是英文（招牌规则）。

### 4.7 后巷木楼梯

- ID：`white_oak_open_stair`
- 名称：白栎室外楼梯 / White Oak Open Stair
- 形状：Mesh，两根斜梁、踏板、敞开的踢面，每格升一格（45°）。状态 `facing`、`rail_left`、`rail_right`（这一侧有没有扶手和立柱）、`part`：`bottom` / `middle` / `top`（底下落在地面上的支座、顶上接平台）。
- 平台和平台栏杆用现有老宅门廊件（第 2 节）。
- 碰撞：踏板做成四级台阶（同老宅屋面），走上去不用跳。
- 挖掘：斧，不要求工具。重量 0.8 kg。

### 4.8 平屋面

- ID：`built_up_gravel_roof`
- 名称：油毡碎石屋面 / Built-up Gravel Roof
- 形状：整格，JSON；深灰沥青油毡上铺一层碎石，边角露出油毡。1910 年代楼的原始屋面；翻修过的楼用 `roof_tpo`。
- 挖掘：镐，任何等级，要求工具。重量 0.25 kg。

## 5. 商业楼 B 首层要新做的方块

B 的楼上和 A 一样（红砖、白窗、叠涩檐口或下面的压型金属檐口），只多首层几种：

| ID | 名称 | 形状 | 挖掘 | 重量 |
|---|---|---|---|---|
| `porcelain_enamel_sign_band` | 搪瓷招牌带 / Porcelain Enamel Sign Band | 1955 年的搪瓷钢板，一块块拼，边上有压条和螺钉；状态 `facing`、`left`、`right`、`color`（奶油、松石绿、红） | 镐 + `needs_stone_tool` | 0.5 kg |
| `pressed_metal_cornice` | 压型金属檐口 / Pressed Metal Cornice | 铁皮压成的檐口，带托架和齿饰，刷漆；状态 `facing`、`shape`（同 4.2） | 镐 + `needs_stone_tool` | 0.4 kg |
| `black_structural_glass` | 黑色饰面玻璃 / Black Structural Glass | 店面下面的护墙，亮面黑玻璃板（1930–50 年代常见），PBR 高光；整格，JSON | 镐，任何等级，要求工具 | 0.3 kg |

- 字用现有 `channel_letter`。
- 窗户封木板（概念图里那一扇）放到以后的"废墟化"一轮，这里不做。

## 6. 数量和分批

| 批次 | 内容 | 新方块 ID 数 |
|---|---|---|
| 第 1 批：住宅 A、B（已做，用户 2026-10-21 实机 PASS） | 第 3 节：搭接木板 ×5、沥青瓦 ×3、陡屋面 ×3、双悬窗（带百叶窗状态）、大玻璃窗、门 ×3（前门带门罩状态）、铁艺栏杆、抹灰基础、隔墙和顶棚、油毡地面、普通红砖（烟囱）、石灰石压顶（烟囱顶）、门廊混凝土、门廊台阶 | 25 |
| 第 2 批：商业楼 A | 4.1 另外两种砖、4.2 檐口 ×3、4.4 年份石、4.5 店面、4.6 门 / 地面 / 招牌板 / 招牌字、4.7 楼梯、4.8 平屋面 | 13 |
| 第 3 批：商业楼 B 首层 | 第 5 节 | 3 |

一共 41 个新 ID（住宅 B 加了 3 个陡屋面，住宅门廊加了 2 个），外加屋面下的补墙部件（3.1）。按"每轮检查 2–3 个包"的习惯，第 1 批做完写住宅 `BuildingKit` 进一次游戏；第 2、3 批一起做完写商业楼 `BuildingKit` 再进一次。

## 7. 代码和生成器归属

第 1 批是这样做的：

- 只用了一个生成器 `tools/build-town-house-v1.mjs`，整格 JSON 方块和 Mesh 方块都由它写：贴图和 PBR、模型、Mesh 部件、方块状态、掉落、`TownShapeData.java`（窗、门、栏杆的轮廓）。它复用 `tools/cabin/` 的几何和画图工具、`tools/town/town-art.mjs` 的贴图。挖掘标签、重量和两种语言的名字是一次写进去的，生成器的 `--check` 会核对它们还在。清单阶段计划的 `tools/build-town-facade-v1.mjs` 没有建。
- Java 在 `com.antaurora.apofirstlight.townhouse.TownHouseBlocks`（陡屋面 `SteepShingleRoof`、台阶 `StoopSteps` 也在里面）。`CabinBlocks.Roof` 改成可以继承（`ShingleRoof` 是它的子类，所以炉火和电线透视照样认得这种屋面），新增 `Roof.slabShape`。`id`、`turn`、`turned`、`mirroredX` 改成 public。老宅的 `Window`、`CabinDoor` 没改，住宅的窗和门是自己的类，用的是同一套画法和工具。
- 带面布尔的墙是 `FacedWall`：`updateShape` 不改面布尔，`rotate()` / `mirror()` 转布尔。
- Jade 已经从开发环境里撤掉（2026-10-21），所以没有补 Jade 信息；交互提示和原版门、方块一样，没有新提示。

商业楼（第 2、3 批）还按这个做法：继续往 `build-town-house-v1.mjs` 和 `TownHouseBlocks` 里加。

## 8. `BuildingKit`

### 8.0 小住宅选 A 还是 B：`SmallHouseKit`（2026-10-21）

- `house_small` 的 kit 是 `worldgen/building/SmallHouseKit.java`：地块够住宅 B（宽 ≥ 13、深 ≥ 14）时，按地块种子（加盐，另起一个随机流，不影响房子自己的挑选）一半盖 A、一半盖 B；只有 12 格宽的地块总是 A。`height()` 和 `build()` 用同一个选择。
- 两种房子共用 `HousePlan`（平面坐标、格子、门、门廊、隔墙、外墙面和隔墙接头、铺到画布上），各自只写自己的外形和房间。
- 版本：`BuildingKits.VERSION` 2 = 住宅 A；3 = 门廊改用混凝土门廊台阶（3.9）；4 = A、B 混排；5 = 门廊平台换成 `smooth_concrete`（3.9）。`TownBuildings.VERSION` 7 = `house_small` 深 17 格、前退线 6.5 m（原来 16 格、7.5 m：房子还在原来的位置，前面多出两格台阶的地方）。现在是 `town_buildings#7#5`，要新建世界。

### 8.1 住宅 `MinimalTraditionalKit`（已做，用户 2026-10-21 实机 PASS）

`worldgen/building/MinimalTraditionalKit.java`，替换了 `house_small` 的 `WhiteBoxKit`（2026-10-21 起由 `SmallHouseKit` 调用）。

坐标和画布一样：u 是从街上看过去往右（朝北画时是西），w 是往地块里（南）。主体的正墙在 w = 3（2026-10-21 从 2 改成 3，前面留出平台加两格台阶）。

- **主体**：宽 W × 深 11，W = 13；地块只有 12 格宽时 W = 12。一层。
  - 地板在 y = 0，比院子高 1 格，露出抹灰基础。
  - 墙在 y = 1–3，y = 3 那格顶上是灰泥顶棚，上面是阁楼。
  - y = −2、−1 整片铺抹灰基础，写入时会往下补到地面。
- **屋面**：侧山墙，1:2，从檐口 y = 4 起，屋脊在 y = 6（第 6 排）。
- **前凸山墙**：在主体左边，7 × 2，屋脊顺着 w，在 y = 5。
  - 它的坡和主屋面的交线用 `valley`：两个角是 `pos`/`neg` 的 lower 格，第 3 列是 `ridge`。
  - 正面是 3 × 2 的大玻璃窗，两侧各一扇双悬窗。
- **门廊**：山墙右边，3 格宽的平台（`smooth_concrete`，w = 2）加 3 段两格长的混凝土台阶（`concrete_stoop_steps`，w = 0–1，6 级）。右侧（敞开的那边）平台和台阶上一排铁艺栏杆，左边靠山墙。前门开在主体正墙上。
- **后门**：通厨房，在 w = 13；门外一格平台（w = 14）加一段台阶（w = 15–16）。
- **烟囱**：在左山墙外、起居室旁边（w = 6），普通红砖从 y = −2 砌到 y = 7。顶上是带烟道的石灰石压顶（y = 8）。地块宽度够多 1 格时才有。
- **房间**：隔墙都是 `plaster_partition`。
  - 左边 u = 1–4：起居室（连着山墙里的凸出部分），后面是厨房。两者之间是一个 2 格宽的门洞，只有过梁那一段墙。
  - u = 5 是一道纵向隔墙；山墙内角那格多一段短墙，接到外墙。
  - 右边 u = 6 到右墙：前面是卧室 1，中间一条走廊，后面是卧室 2 和卫生间（右边两格）。
  - 四扇室内门：走廊通起居室一扇，通两间卧室和卫生间各一扇。门都往房间里开，通起居室那扇往走廊里开。
- **地面**：
  - 红橡木条地板 `red_oak_planks`；
  - 厨房 `linoleum_floor`；
  - 卫生间 `restroom_floor_tile`。
- **窗**：
  - 山墙两侧各 1 扇；
  - 前墙卧室 1 并排 2 扇；
  - 左山墙起居室 2 扇（烟囱两边）、厨房 1 扇；
  - 右山墙卧室 1 和卫生间各 1 扇；
  - 后墙厨房、卧室 2 各 1 扇。
- **每块地的种子挑选**：
  - 搭接木板 5 色；
  - 屋面 3 色；
  - 前门 4 色；
  - 窗格三分之一是 6-over-6，其余 1-over-1；
  - 后门有没有纱门；
  - 镜像（一半的房子山墙在右边）。

    镜像是在 kit 里把 u 翻过来，方块状态用 `mirror(FRONT_BACK)` 翻，然后交给画布旋转。
- **kit 直接写的状态**：世界生成不触发邻居更新，所以墙的外侧面、隔墙的连接、踢脚和顶棚、压顶的烟道都由 kit 写。窗、大玻璃窗、栏杆、屋面的边是绘制时自己看邻居的，不用写。
- **退回白模**：地块比 12 格窄或比 17 格浅时，还是原来的白模（`grey_board_and_batten`）。

  住宅地块是 24 m（±15 %），扣掉车道以后房子能用 12–14 格宽、17 格深，所以正常地块都会盖成住宅 A 或 B。

### 8.2 住宅 B `CapeCodKit`（2026-10-21，用户同日实机 PASS）

`worldgen/building/CapeCodKit.java`。科德角式小屋（1930–1950）。和概念图（11 × 9 m、一层半、两个老虎窗）比，第 1 轮先做一层：主体 13 × 8，阁楼不装修，没有楼梯和老虎窗。

- **主体**：宽 13 × 深 8，正墙在 w = 3，左右对称。一层，地板 y = 0、墙 y = 1–3、y = 3 顶上灰泥顶棚，和住宅 A 一样。
- **屋面**：侧山墙，1:1 陡屋面（3.10），前坡从正墙上面 y = 4 起，w = 3–6 每格升一格，后坡对称（w = 7–10），屋脊在 y = 8。两头的山墙一直砌到屋面下面，屋面格子把山墙三角补成墙的样子。
- **正面**：前门在正中（u = 6），带门罩（`surround`）；两边各两扇双悬窗（u = 2、4 和 8、10），都带百叶窗。窗头在檐下，用檐下的窗套。
- **门廊**：前门外 3 格宽的平台（w = 2）加 3 段台阶（w = 0–1），两边各一排铁艺栏杆。
- **后门**：在走廊尽头（u = 6，w = 10），门外一格平台加一段台阶（w = 11–13）。
- **烟囱**：左山墙外，两格宽，骑在屋脊下面（w = 6–7），普通红砖从 y = −2 砌到 y = 8，顶上石灰石压顶（y = 9，后面那格带烟道）。地块宽 14 格以上才有。
- **窗**：
  - 左山墙：起居室 1 扇、厨房 1 扇；阁楼两扇单格窗（y = 5，屋脊两边）；
  - 右山墙：卧室 1 扇；阁楼两扇单格窗（y = 5，靠屋脊）；
  - 后墙：厨房 1 扇；卫生间一扇单格高窗（y = 3）。
- **房间**：
  - 起居室在前面左半边（u = 1–6，w = 4–6），前门直接进起居室；
  - 厨房在它后面（u = 1–4，w = 8–9），中间一道隔墙（w = 7），开一个 2 格宽、只有过梁的门洞；
  - 走廊（u = 6，w = 7–9）从起居室通到后门，厨房的门开在走廊里；
  - u = 7 一道纵向隔墙，右边是卧室（u = 8–11，w = 4–6，后面 u = 11 一条壁橱凹进去）和卫生间（u = 8–9，w = 8–9）；卧室门开在起居室里，卫生间门开在走廊里。
- **地面**：红橡木条地板；厨房油毡；卫生间瓷砖。
- **每块地的种子挑选**：搭接木板 5 色；陡屋面 3 色；百叶窗 3 色（绿、黑、酒红）；前门一半和百叶窗同色，另一半 4 色里随便挑；窗格三分之二是 6-over-6，其余 1-over-1；后门有没有纱门；镜像（烟囱在左边或右边）。
- **退回**：地块比 13 格窄（只有 12 格宽）时由 `SmallHouseKit` 盖成 A；比 14 格浅时是白模（正常地块不会）。

### 8.3 商业楼 `TwoPartCommercialKit`（计划）

- 替换 `shop`。A / B / C 由地块种子挑，比例 6 : 3 : 1；24 m 地块偏向 C。
- 变化项：砖色、檐口种类、店面漆色、招牌字、年份。同一条街上相邻两栋不重复砖色。
- 店面要做 `left`/`right` 状态的话，镜像时由 kit 换过来。
- 世界锁：`BuildingKits.VERSION` 再升一次。
- 屋里第一轮只放隔墙、门、楼梯、地面和顶棚；家具、灯和配电是下一轮（住宅也是）。

## 9. 还没定的（做到那一步再说）

- 搭接木板的 5 个颜色、沥青瓦的 3 个颜色：贴图已经做了，色板还没单独给用户看，进游戏时一起看。
- 年份石的楼名用什么（镇名加 "BLOCK"，还是业主姓氏），做第 2 批时定。
- 住宅 B 第 2 轮：屋里的楼梯、两个老虎窗、阁楼两间卧室（概念图的一层半），等用户看过第 1 轮再做。
