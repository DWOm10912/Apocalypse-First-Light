# Box Stove V1（铸铁箱式炉、隔热钢板、石板，2026-10-13）

森林开局 B「溪谷老宅」M2.2：老宅客厅的柴炉（[实现计划](../worldgen/forest_start_home_b_v1_plan.md) 包 F 的 F1「柴炉 + 烟管」）。用户 2026-10-13 定：
- 用概念 A（老式长箱，不带烤箱）；
- 隔热钢板做成单独的方块；
- 炉台换成平整石板。

概念图：`design/buildings/forest_start_home_v1/m2_sheets/m2_2_stove_A.png`（源页 `m2_source/stove_concepts.html`）。

状态：
- 代码、资源、离线检查、Java 编译都完成。
- 离线网页预览看过真实生成的拼块：开门、关门、炉膛里的柴、钢板拼接、烟管接口。图在 `m2_sheets/m2_2_v1_*.png`，源页 `m2_source/stove_preview.html`。
- 老宅脚本已经换成真炉子（`stove` 模式），`plan` 检查通过。
- 2026-10-13 已经摆进「house test」的老宅（见第 6 节）：
  - 读回的方块状态都对，支撑审计 0 个问题；
  - 截了一张图（光影状态没查）：炉子、石板炉台都正常显示，两边的钢板和炉子、第一节烟管背后的钢板连成一整张，看不出接缝；
  - Jade 能认出钢板的名字。
- 2026-10-13 用户实机：
  - 煮饭、烧水 **PASS**；取暖 **PASS**；
  - 烟囱不冒烟。有两个原因，两个都改了：
    - `FireFx`："头顶有没有东西"的判断按整格算，冒口在烟管那格里面，烟团一出来就停住（见"火、烟、声音"）；
    - 用户重启游戏后录视频，还是没烟：`BoxStoveBlockEntity.clientChimneyAt` 初始是 `Long.MIN_VALUE`，`now - clientChimneyAt` 溢出成负数，"每 2 秒找一次烟囱"从来不成立，冒口一直是空的，一团烟都没生成。现在冒口为空时先找一次；
    - 第二处修改时游戏开着，没编译，用户重启时启动流程编译；
    - 用户重测：烟囱冒烟 **PASS**（同日）；
  - 用户自己把烟管穿过的那格屋面修回去了；
  - 用户把烟管穿过的那格屋面换成了烟管，屋面上留了一个洞。原设计不用换：烟管正上方的屋面格自己画"烟管穿过 + 防水套 + 泛水板"（[Cabin Exterior V1](cabin_exterior_v1.md)，`roof/<材料>_pipe_<形状>`），烟管从它上面接着往上；
  - 提出缺煎肉之类的声音：现在只有柴火噼啪声，煮东西没有声音（见第 8 节）。2026-10-14 用用户的 Firefly 素材补上了炉火、煎炒、烧水循环和开关门、添柴（[老宅音效 V1](../audio/forest_home_sounds_v1.md)），用户 2026-10-15 实机听过，PASS。
- **还没单独验证**：开门的火焰和光、交互提示、炉子的 Jade（用户没提问题，但没有明确说测过）。
- 生存挖掘和掉落：2026-10-14 M2 收尾的 GameTest（`ForestHomeM2GameTests`）验过：炉子空手、石镐拆不掉东西，铁镐掉自己；钢板、烟管木镐不掉、石镐掉；石板空手不掉、木镐掉。没在游戏里实机确认。

## 1. 方块

| Registry ID | 中文 / English | 是什么 | 质量（估） | 搬运标签 |
|---|---|---|---|---|
| `apocalypse_firstlight:box_stove` | 铸铁箱式炉 / Cast Iron Box Stove | 烧柴的铸铁炉，炉顶能放两样东西煮 | 60 kg | `carry/oversized` |
| `apocalypse_firstlight:stove_heat_shield` | 隔热钢板 / Stove Heat Shield | 挂墙的镀锌钢板，几块拼成一整张 | 3 kg | `carry/bulky` |
| `apocalypse_firstlight:flagstone` | 石板 / Flagstone | 整格石块：顶面是平整的方石板，侧面是毛石 | 0.25 kg | — |

其他注册：方块实体类型 `apocalypse_firstlight:box_stove`。

炉子和钢板都是 Mesh（`CabinPieces`），都画成朝北：前面在 z 0，墙在 z 1。游戏里按 `facing` 转。下面的尺寸都按朝北写。

## 2. 铸铁箱式炉

### 外形

- 炉身：
  - x 0..0.70，z 0.30..0.62，y 0.165..0.53；
  - 炉顶板到 0.56；
  - 四条外撇的铸铁腿，高 15 cm，带垫脚。
- 铸件细节：
  - 侧板上有凸起的铸框，边角有圆棱；
  - 所有边都倒了斜角，暗铁靠棱上的高光读出形状。
- 炉顶：
  - 一个煮锅盖，在 (0.20, 0.46)；
  - 烟口在格子正中 (0.5, 0.5)，有一圈烟口座；
  - 一段烟管短节，半径 7.55 cm（15 cm，6 英寸），一直到格子顶 y 1.0，上面接小屋的 `stove_pipe`；
  - 短节上有风门：一根横杆，在 y 0.86，带弹簧圈把手。
- 装柴门在 x 0 那一端：
  - 铰链在靠墙那边（z 0.58）。门往墙那边开，屋里的人能看见火；
  - 门上有一个旋转进风口，6 个孔；
  - 门上有一个弹簧圈把手和门闩；
  - 门下面有一块接灰的托板。
  - 门打开时伸进旁边那格 25 cm。那格离地 0.45 m 以下要空着。
  - 老宅里炉子朝南，门在东端，朝着炉边的两把椅子。
- 炉膛：
  - 耐火砖内衬，底下是灰床；
  - `load` 1..3 时画 1..3 根劈柴。
- 碰撞：
  - 炉身加炉顶：x 0..0.712，y 0..0.56，z 0.288..0.632；
  - 短节：x / z 0.42..0.58，y 0.56..1.0。
- 生物不寻路穿过。

### 方块状态

| 属性 | 值 | 意思 |
|---|---|---|
| `facing` | 水平四向 | 炉子的长边朝哪边（朝屋里）。放的时候朝着放的人 |
| `open` | true / false | 装柴门开着 |
| `lit` | true / false | 在烧 |
| `load` | 0..3 | 炉膛里还剩几根柴（按剩下的燃料折算，进位） |

### 交互

右键（只认主手；潜行时不出提示）。按下面的顺序找第一条能做的：

1. 对着炉顶靠门那一半（框架 x ≤ 0.42，炉顶面上）：
   - 空手：拿下那里的东西；
   - 拿着有烟熏配方的东西：放上去。对准的位置有东西就放另一个位置，两个都满就不放。
2. 门关着：开门。
3. 门开着：
   - 拿着柴：添进去。装不下一半就不收；装得下一半但装不满全部时，多出的部分浪费；
   - 拿着打火石或火焰弹，炉子没烧、里面有柴：点火。打火石掉 1 点耐久，火焰弹用掉 1 个；
   - 其他：关门。

声音（2026-10-14 起，[老宅音效 V1](../audio/forest_home_sounds_v1.md)；声音和感染者噪声同一半径）：
- 开关门：`box_stove_door_open` / `_close`，4 格；
- 添柴：`box_stove_add_wood`，3 格；
- 点火：打火石 / 火焰弹（原版）；
- 放上：放金属；
- 拿下：捡东西。

### 燃料

三种物品标签，在 `data/apocalypse_firstlight/tags/items/stove_fuel/`：

| 标签 | 包括 | 一个烧多久 |
|---|---|---|
| `stove_fuel/logs` | `#minecraft:logs_that_burn`、6 种 AFL `<树种>_log`、`firewood_stack` | 2400 tick（游戏里 2 小时） |
| `stove_fuel/wood` | `#minecraft:planks`、`#minecraft:wooden_slabs`、`#minecraft:wooden_stairs`、AFL 各树种的 `_planks` / `_rounded_timber` / `_beam`、`white_oak_stairs`、`white_oak_slab` | 600 tick |
| `stove_fuel/kindling` | `minecraft:stick`、6 种 AFL `<树种>_branch` | 200 tick |

- AFL 的条目都是 `required: false`。
- 炉膛最多装 7200 tick，等于三根原木。
- 关着门每 tick 烧 1，开着门每 tick 烧 2（进风多）。
- 烧到 0 自动熄灭。

### 做饭、煮水

- 炉顶两个位置，每个放一件。凡是有原版 `minecraft:smoking`（烟熏炉）配方的东西都能放，包括食物，也包括 AFL 的脏水煮沸配方（它是烟熏配方）。
- 速度跟熔炉一样：配方烟熏时间的两倍。
- 用配方自己的 `assemble` 出结果，所以煮水的污染规则照旧。
- 熟了留在炉顶，等人拿走。
- 不烧的时候，进度每 tick 退 2。
- 拆掉炉子时，炉顶的东西掉出来。

### 热、光、烫

- 温度系统热源（`temperature/temperature_v1.json`）：
  - `{"block": "apocalypse_firstlight:box_stove", "strength": 22, "radius": 6, "lit": true}`；
  - 只在 `lit=true` 时算，比营火（20 / 6）稍强。
- 方块光：开门烧 12，关门烧 6，熄灭 0。
- 炉顶烫：烧着时站上去每次扣 1 点（`hotFloor`，像岩浆块），潜行不扣。

### 火、烟、声音（客户端 `client/BoxStoveFire`）

火焰和光晕画在火的世界渲染里（`ClientFuelStains`，在方块实体之后），48 格内画：

| | 门开着 | 门关着 |
|---|---|---|
| 火焰 | 劈柴上 1..3 团柔和火焰（`FuelFlames.flame`，团数等于 `load`） | — |
| 光晕 | 灰床上一片，门前地上一片很淡的 | 只有进风口的 6 个孔发光 |
| 火星 | 偶尔有一颗溅到炉台上（每 tick 2 %） | — |

烟：
- 柔和的木烟（`FireFx`，不是原版像素烟）；
- 从烟囱顶冒：顺着炉子上面的烟管往上找，烟管穿过的那格屋面也算（屋脊格不能穿烟管，不算）；
- 最后一节烟管有防雨帽：烟从帽子下面的缝（管口 0.55 到帽沿 0.62）冒出，绕着帽沿外面一圈（离中心 0.175..0.195，帽沿半径 0.16）往上升；
- 烟管停在一格屋面里（屋面那格画的烟管顶上开口）时，从那格顶上冒；
- 没接烟管时，从短节口冒；
- 每 2 秒重新找一次烟囱；
- 每 3 tick 一团；刚点火后 300 tick 内每 2 tick 一团，而且更浓；
- 96 格内能看见。

声音（2026-10-14 起不在 `BoxStoveFire` 里，原来的随机营火噼啪声删掉了）：
- 烧着：炉火循环 `box_stove_fire_loop`，4 格；
- 炉上有脏水在煮：烧水循环 `box_stove_boil_loop`，6 格；有别的东西在煮：煎炒循环 `box_stove_fry_loop`，8 格；煮好了就停；
- 循环由 `client/BlockLoopSoundController` 放，淡入 10 tick；服务端每 40 tick 发一次 `MACHINE` 噪声，半径取正在放的最响的那个；
- 开门关门炉火一样响。

### 提示、Jade

交互提示（`WorldInteractionHint`）：

| 动作 | 中文 | English | 位置 |
|---|---|---|---|
| 开门 | 打开 | Open | 门的中间 |
| 关门 | 关上 | Close | 门的中间 |
| 添柴 | 添柴 | Add wood | 门的中间 |
| 点火 | 点火 | Light | 门的中间 |
| 放上 | 放上 | Put on | 那个炉顶位置 |
| 拿下 | 取下 | Take | 那个炉顶位置 |

装不下的时候不出提示。

Jade（`compat/jade/BoxStoveJadeProvider`，配置名 AFL 箱式炉）显示：
- 燃烧中 / 熄灭；
- 还剩多少柴：「柴 x.x 小时」，游戏里 1000 tick 算一小时；
- 炉顶在煮什么、煮了百分之几。

## 3. 隔热钢板

为什么要有：
- 原木往炉子那格里鼓 7 cm，炉背离原木只剩约 31 cm；
- 不加防护时，可燃物要离 91 cm（36 英寸）；
- 墙上挂一块离墙的钢板（下面进风、上面出风）后，只要 30 cm（12 英寸）。

外形：
- 镀锌钢板，在 z 0.896..0.900，离格子的墙面 10 cm，离原木的鼓面 3 cm；
- 后面是瓷垫脚（半径 1.4 cm，在 x 0.25 / 0.75、y 0.5），一直顶到墙，正面有螺钉头；
- 不和别的板拼的边退进 3 cm，有一道卷边（半径 4.2 mm）；
- 上下都留空，空气从下面进、从上面出。

放置：
- `facing` = 背对墙的方向。像枪架那样：从玩家看的方向找第一面能挂的墙；
- 背后那格的面要结实。墙没了，钢板就掉。
- 碰撞：x 0..1，y 0..1，z 0.89..1。

拼接（`BoxStove#panelMask`）：
- 同一朝向的钢板，左右、上下挨着就连成一整张，中间没有边。共 16 种拼块 `shield/panel_<mask>`：
  - 1：框架 x−，也就是 `facing` 的逆时针方向；
  - 2：x+；
  - 4：上；
  - 8：下。
- 炉子和烟管：
  - 朝向一样的炉子，或任何烟管，只要左边或右边有钢板，它的格子里也在背后画钢板；
  - 这样炉子和第一节烟管背后不断开。
  - 一格只能放一个方块，所以只能这样接着画。
- 规则：
  - 钢板看旁边：钢板、朝向一样的炉子、烟管都算连上；
  - 炉子和烟管看旁边：只认钢板；
  - 上、下：算钢板，也算背后画着钢板的炉子 / 烟管。
- 所有判断都只看相邻一格。
- 烟管的拼块旗标是 `(on ? 0 : 1) | pipePanel << 1`：
  - `pipePanel` 为 0 表示没有钢板；
  - 否则是 `1 | turn << 1 | mask << 3`，取第一个旁边有钢板的朝向（[Cabin Exterior V1](cabin_exterior_v1.md)「其他」）。

## 4. 石板

- 原版整格方块，任意镐、需要正确工具，掉自己。
- 顶面：
  - 是平整的方形青石板，乱尺寸、分行铺：
    - 每行 72..144 texel 宽，每块 72..180 texel 长；
    - 3 texel 灰缝，4 texel 窄斜棱，灰缝比石面低 3；
  - 一张 720 × 720 的无缝贴图（3 × 3 格），切成 `stove/flagstone_top_0..8`（各有 `_s` / `_n`）。
- 侧面：用毛石的 6 块贴图（`cabin/fieldstone_0..5`），埋进地面以后和勒脚一样。
- 模型加载器：`macro_tile`（`client/MacroTileModel`）。这次加了单独的顶面组：
  - 可选的 `top_columns` / `top_rows` 和贴图 `top0..`，只用在朝上的面；
  - 顶面按世界坐标选块：(z mod 3) × 3 + (x mod 3)；
  - 不写这两个字段时，顶面照旧用侧面那组（毛石不变）。

## 5. 挖掘、重量、噪声（AGENTS.md 审查）

| 方块 | 工具 | 等级标签 | 需要正确工具 | 掉落 |
|---|---|---|---|---|
| `box_stove` | `minecraft:mineable/pickaxe` | `minecraft:needs_iron_tool` | 是 | 自己，加上炉顶的东西 |
| `stove_heat_shield` | `minecraft:mineable/pickaxe` | `minecraft:needs_stone_tool` | 是 | 自己 |
| `flagstone` | `minecraft:mineable/pickaxe` | 无（任意镐） | 是 | 自己 |

- 炉子和钢板低于 AGENTS 对钢结构默认的钻石级。用户在 B V1 计划的等级表里批准过（"柴炉：镐 + 铁级"）。钢板是薄钢皮，和铁皮屋面、烟管同一档（石级）。
- 硬度：炉子 3.0（爆炸抗性 6.0），钢板按金属默认。
- 噪声：炉子和钢板在 `noise_metal_blocks`，石板在 `noise_stone_blocks`。
- 重量：`item_mass/afl_content_v1.json` 里炉子 60、钢板 3、石板 0.25 kg（都是估的）。
- 战利品表：三个都掉自己。

## 6. 老宅里（M2.2）

脚本：`tools/afl_minecraft_mcp/forest_home_b_m1.mjs`

- 炉台：(7..9, 0, 1) 从毛石换成石板。
- 炉子：替掉 M0 的钢块，用 `place_fixture` 放在 (8, 1, 1)，朝南。
- 钢板：后墙上炉子两边 (7, 1..2, 1)、(9, 1..2, 1)，朝南，2 m 高。
  - 炉子和 (8, 2, 1) 那节烟管背后也画钢板，连成一张 3 × 2 m。
- 炉子东边原来的木桶去掉了，那里是门打开的位置。
- 检查：
  - "烟管没支撑"承认下面是炉子 fixture；
  - 新加"钢板背后没有墙"。
- 新模式 `stove`：在已经搭好的房子里只换这几处：
  - 清掉炉子那格 (8, 1, 1)；
  - 写 13 格：3 格石板、4 块钢板（(9, 1, 1) 那块盖掉原来木桶的位置）、6 节烟管；
    - 烟管要重写，因为「house test」里屋里那段烟管已经没了（[M1 外壳](../worldgen/forest_home_b_m1_exterior.md)「实机换家具 M2.1」）；
  - 再放炉子。

在「house test」里搭时，要先开游戏、续上地块：

```
/afl_author resume forest_home_b_m1 -785 83 -144 37 26 13 3
```

再运行：

```bash
node tools/afl_minecraft_mcp/forest_home_b_m1.mjs stove
```

2026-10-13 实际摆进「house test」时没跑 `stove` 模式：
- 用户自己删了一部分占位，桌椅也按自己的想法重新摆过，只要接入新的家具。
- 先读回炉子那一块：
  - 炉子那格和 4 个钢板格是空的；
  - 炉台还是毛石；
  - 屋里的烟管只缺炉子上面那一节 (8, 2, 1)，k 3..5 和屋顶外的都在；
  - 用户在 (7, 3, 1) 的后墙上挂了一支火把，不在钢板格里，没动。
- 所以只写有变化的 7 格：3 格石板、4 块钢板、1 节烟管。再用 `place_fixture` 放炉子（世界坐标 (−767, 87, −133)，朝南）。
- 读回一致，支撑审计 0 个问题。
- 脚本里桌椅的位置已经和世界里不一样了。在「house test」里不要再跑 `furniture` 或 `build`，会把用户摆的桌椅改回去。

开发工具：
- authoring fixture `box_stove`，安全、utility，朝向四向；
- 放下时冷炉、空炉、门关着；
- 要立在地上（`Support.FLOOR`）。

## 7. 技术

- 准星和选框（2026-10-13 晚）：炉子、隔热板按拼块网格选中，选框是网格的折边，子弹也按网格打（[Mesh Hit Runtime V1](../rendering/mesh_hit_runtime_v1.md) 0.7 节）；碰撞不变。放锅 / 取锅的位置（`spotAt`）按命中点算：网格的炉顶板在 0.56、炉盖 0.5615 … 0.567，都在 `TOP − 0.01 … TOP + 0.02` 里，所以照常能用；锅是方块实体画的，不在网格里，瞄锅时射线落到下面的炉顶上，取的就是那个位置。没在游戏里看过。
- 生成器 `tools/build-box-stove-v1.mjs`，贴图在 `tools/stove/box-stove-art.mjs`。
  - `node tools/build-box-stove-v1.mjs` 写出全部；
  - `--check` 核对输出是否最新，也审查手改的数据：标签、等级、噪声、重量、搬运、语言、燃料标签、温度热源。当前 `CHECK OK (91 outputs)`。
- 输出：
  - 贴图：`textures/block/stove/*.png`（`cast_iron`、`cast_iron_edge`、`soot`、`nickel`、`firebrick`、`ash`、`firewood_bark`、`firewood_end`、`heat_shield`、`ceramic`、`flagstone_top_0..8`），每张带 `_s` / `_n`（LabPBR）。
  - 拼块：
    - `cabin_mesh/stove/`：`body` 698 个四边形，`door_closed` / `door_open` 各 334，`load_1..3`；
    - `cabin_mesh/shield/`：`panel_0..15`。
  - 方块 / 物品模型、方块状态、战利品表。
- 材质：
  - 黑铁是 [40, 40, 41]，光滑度 88，F0 10；
  - 镀锌板是 [116, 120, 122]，光滑度 96，F0 10；
  - 都按非金属 F0。大块金属 F0 在光影下会发白；
  - 只有镍件用金属 F0。
- Java：
  - `cabin/BoxStove`：方块、钢板、拼接、动作；
  - `cabin/BoxStoveBlockEntity`：燃料、做饭、存档、同步；
  - `client/BoxStoveRenderer`：炉顶的东西平放，按 FIXED 模型缩到 18 cm；
  - `client/BoxStoveFire`：火、光晕、烟、声音；
  - `compat/jade/BoxStoveJadeProvider`；
  - 注册在 `CabinBlocks`，烟管的钢板旗标在 `CabinBlocks.StovePipe`。
- 离线预览 `design/buildings/forest_start_home_v1/m2_source/stove_preview.html`：
  - 由 `m1_source/serve.cjs` 提供，three.js；
  - 拼块、钢板旗标和老宅一样，左边开门烧着（火焰是示意），右边关门冷炉。

## 8. 已知问题、以后

- 开头「状态」里列的"还没验证"。
- ~~煮东西没有声音~~：2026-10-14 接了用户的煎炒、烧水素材（[老宅音效 V1](../audio/forest_home_sounds_v1.md)），用户 2026-10-15 实机听过，PASS。
- 炉火循环不随开门关门变响。
- 烟管穿屋面靠屋面格自己画烟管：烟管格不能当屋面用，换掉屋面就是一个洞。
- ~~烟管穿楼板（老宅阁楼 (8, 4, 1)）的那格没有楼板，烟管周围是一个 1 m 的方洞~~。2026-10-14 修了：烟管穿木板楼板（2026-10-14，M2 收尾）：烟管格上下都是烟管、同高的四个邻格里至少两面是木板（AFL 各树种的木板 `WoodBlocks.Planks`，或原版 `minecraft:planks`）时，这一格画成楼板：白栎木板面（直接用 `block/wood/white_oak_planks` 的贴图，按方块面投影，和旁边的楼板接得上）围着 30 cm 见方的钢支撑盒，天花板下一块 40 cm 见方的黑钢装饰板，楼板上一块同样大的防火封板，烟管从中间穿过；碰撞和选框是整格，阁楼上能踩。零件 `cabin_mesh/pipe/stove_pipe_floor`，`CabinBlocks.StovePipe` 的 flags 第 8 位。生成器检查、编译通过，**还没进游戏看**；老宅里那一格本来就是烟管，世界不用改，重启后生效。
- 楼板格的木板面固定是白栎；别的树种的楼板里穿烟管，颜色会对不上（V1 老宅只有白栎）。
- 烟管方块不挡光（`noOcclusion`），楼板格会漏一点光到阁楼 / 屋里。
- 一格只能放一个方块，所以炉子和烟管背后的钢板只有旁边有钢板时才画。钢板只挂一边也行，两边都挂最完整。
- 装不满的那部分柴直接浪费，没有提示。
- 风门只是外观，不影响烧得快慢。
- 炉顶只放一件，不按锅、壶区分。
- 没有灰要清，V2 再说。
