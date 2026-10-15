# Wood Furniture V2（卧室和储物家具，2026-10-15）

开局老宅（[老宅 B V1 计划](../worldgen/forest_start_home_b_v1_plan.md)）M4 第二轮之前补的内饰。用户 2026-10-15 的要求：

- 做 Mesh 木床、Mesh 衣柜、床头柜、工具架；
- 冷柜现在是商用的，做一台老式大铁柜；
- 原版箱子不要了，做自己的储物箱（Mesh），逻辑可以复用，ID 用自己的。

和 [Wood Furniture V1](wood_furniture_v1.md) 同一套做法：程序生成的 Mesh 拼块（`CabinPieces`，`cabin_mesh/furniture/`），朝北画，进区块画；木家具不分树种，用 V1 的中性成品木色。概念图 2026-10-15 发给了用户（`design/buildings/forest_start_home_v1/m4_sheets/fv2_sheet.png`），用户还没回复意见。

## 1. 方块

| ID | 名字 | 格 | 存储 | 质量 | 搬运 |
|---|---|---|---|---|---|
| `wooden_bed` | Wooden Bed / 木床 | 1 × 2（脚、头） | — | 40 kg | oversized |
| `wooden_wardrobe` | Wooden Wardrobe / 木衣柜 | 1 × 1 × 2 高 | 27 格 | 55 kg | oversized |
| `wooden_nightstand` | Wooden Nightstand / 木床头柜 | 1 | 9 格 | 9 kg | bulky |
| `wooden_tool_rack` | Wooden Tool Rack / 木质工具架 | 1，挂墙 | 5 个位置 | 3 kg | bulky |
| `wooden_storage_chest` | Wooden Storage Chest / 木储物箱 | 1，两个并排合成一个长箱 | 27 格（合起来 54） | 16 kg | oversized |
| `vintage_chest_freezer` | Vintage Chest Freezer / 老式冷柜 | 2 × 1 | 27 格 | 75 kg | oversized |

质量都是估计值（`item_mass/afl_content_v1.json` 里 `estimated: true`）。创造栏：AFL 建筑方块，跟在收音机后面（`CabinBlocks#tabs`）。

### 木床

- 原版床的全部行为（`WoodFurnitureV2.Bed` 继承 `BedBlock`）：睡觉跳夜、设重生点、下界和末地爆炸、跳上去会弹。
- 单人床，两格：`facing` 从床脚指向床头，`part` = `foot` / `head`。放的时候点的那格是床脚，床头在玩家看的方向。
- 样子：四根方柱；床头板（框 + 凹板）到 1.0 m，床尾板到 0.74 m；床边横档 0.22..0.36；条纹布床垫 0.32..0.54；苔绿色毛毯盖到床头前，白布翻边，一个枕头。
  - 床垫顶 0.54，接近原版床的 9/16，人躺上去不会浮着。
- 没有方块实体（不需要 `BedBlockEntity`，样子是 Mesh）；渲染形状是 `MODEL`。
- 物品是 `BedItem`，一组 1 个。内部颜色记成棕色（原版代码需要一个 `DyeColor`），不能染色。
- 村民不把它当床（村民的家只认原版床），这一版不需要。
- 选框：床头那格到床头板顶，床尾那格到床尾板顶，中间按床垫高。

### 木衣柜

- 两格高（`half` = `lower` / `upper`），96 cm 宽、57 cm 深（z 0.36 到 0.925），高 1.96 m：缩进的踢脚，一个通宽抽屉（0.11..0.33），两扇框板门（0.364..1.816，铁拉手在两门中间），顶上线脚；里面 1.62 一层帽架、1.55 一根挂衣杆。
- 27 格存储在下半格的方块实体（V1 的 `WoodCounterBlockEntity`）；对着上半格右键也打开同一个。
- 有人打开时两半格都 `open=true`，两扇门朝外开；没人开着就关上（和 V1 柜子一样的复查）。
- 开关声：V1 柜子的 `wood_cabinet_open` / `_close`，在门的高度（1.0 m），3 格，带同半径的感染者噪声。
- 放：上面那格要能放；放下时自动放上半格。
- 拆：一半没了另一半也没了（像门）：
  - 生存模式用手或斧拆任何一半：掉一个衣柜 + 里面的东西；
  - 创造模式拆上半格：什么都不掉（东西还是掉出来）。

### 木床头柜

- 一格，52 × 40 cm，台面高 0.66 m，上面一个抽屉，下面一层开放格。
- 9 格存储；打开时抽屉拉出 24 cm。开关声同衣柜，在 0.55 m。
- 收音机可以放在上面：`EmergencyRadio.Surface` 加了 `nightstand`，下沉 1 − 0.66 = 0.34 格（[Emergency Radio V1](emergency_radio_v1.md)）。

### 木质工具架

- 挂墙，`facing` = 背对墙的方向，身后那格要有朝这边的结实面；放法、墙拆了掉下来都和 V1 枪架一样。
- 一块背板，三对木钉（x 0.2 / 0.5 / 0.8，高 0.745），上面一块小搁板（顶 0.882）。
- 位置：
  - 0..2 是三对木钉：一次一个、不是方块、不是枪的物品（斧、镐、锹、锄、撬棍、剑、钓竿、剪刀……）；
  - 3、4 是搁板左、右（从正面看）：任何不是方块的物品，整组放；
  - 枪不上工具架（用枪架）。
- 操作（同枪架，方块实体也是枪架那个类，`WoodToolRackBlockEntity` 只是换了类型）：
  - 看准位置：0.86 以上是搁板（左半 / 右半），下面按横向最近的那对木钉；
  - 手拿东西：看准的那格能放而且空就放那里，否则放同类第一个空位；满了什么也不做；
  - 空手取下看准的那件；放、取用原版展示框的声音。
- 显示：
  - 木钉上的工具用物品展示框模型（FIXED），转 45°，让原版工具图标的"左下手柄 → 右上头"那条对角线竖直，头朝上，挂在两根木钉之间，缩到 0.58（对角线约 0.82 m）；
  - 搁板上的东西平躺，和枪架搁板一样；
  - 静止的东西由区块画（V1 枪架的录制，`WoodGunRackChunking`），录制按方块状态记，原木墙上外移的也算在内。
- 圆木墙上：站在垫块上，垫块种类 12（`LogMount.PAD_TOOL_RACK`，背板和搁板背后 0.2, 10.6 → 15.8, 14.6 px，见 [小屋外饰 V1](cabin_exterior_v1.md) 壁挂件垫块）。不加垫块的话，背板会埋进原木鼓出的 7 cm 里。
- 选框：背板加搁板；碰撞只有搁板那一段（上面 10.88..14.4 px），人能贴墙走。

### 木储物箱

- 原版箱子的全部逻辑（`WoodFurnitureV2.StorageChest` 继承 `ChestBlock`，方块实体 `StorageChestBlockEntity` 继承 `ChestBlockEntity`），只是 ID、样子、方块实体类型是自己的：
  - 27 格；两个并排同朝向就合成一个 54 格的长箱（`type` = `single` / `left` / `right`，和原版一样）；潜行放就不合；
  - 漏斗、比较器、含水、上面压着实心方块或猫就打不开，都照原版；
  - 开关声是原版箱子的声音，不带 AFL 噪声。
- 样子：木板箱，92 × 52 cm，高约 50 cm：6 cm 底座，箱身到 0.47，2.5 cm 带边的面板盖，背后两条铁合页，正面搭扣，两端吊环提手；合成长箱时是一整个 192 cm 的箱子和一整块盖。
- 有人打开时 `open=true`，盖子绕后边的合页转 75°；合起来的两半一起开。没有盖子动画（原版的盖子动画是箱子渲染器画的，这里不用），开关是一下子换。
- 原版箱子还在游戏里，配方也没动，见第 7 节。

### 老式冷柜

- 六十年代的卧式冷柜，两格宽：`part` = `left`（主格，方块实体在这）/ `right`。左右是方块自己的左右，和原版箱子一样：从正面看，`left` 在右边，`right` 在 `facing.getClockWise()`。
- 160 × 74 cm，高 90 cm：圆角的奶白搪瓷柜体，下面一条深色百叶踢脚板；一整块盖子在后边合页上，打开转 80°；盖子正面中间一个镀铬拉手带锁；里面是结霜的白内胆，非主格那头（从正面看左边）挂一个铁丝筐。
- 正面上部、主格那头（从正面看右边）一个指示灯和温控旋钮。
- 27 格存储；有人打开时两半都 `open=true`，盖子掀起。
- 电（和商用冷柜 `chest_freezer` 用同一套 `CompressorAppliance`）：
  - 耗电沿用商用冷柜的平衡文件 `machine_balance/chest_freezer.json`；
  - 背后低处一个 IEC C14 插座（主格 x 0.38、高 0.16），可拆电源线；潜行空手右键冷柜：把插头拿到手上 / 放回，和其他电器一样；
  - 有电时指示灯亮（`lit=true`，全亮贴图 `radio/lamp_glow`）；
  - 压缩机按周期开停，声音是饮料柜压缩机那一套，音高 0.82（商用冷柜 0.9）；
  - 有电而且盖子开着：冷雾从井口冒出来、从前沿和两端漫下去，掀盖那一下冒一团（`ColdMist.vintageFreezerTick`）；盖子是钢的，关着时看不到雾。
- 食物不会坏（还没有腐坏系统），冷柜现在就是会用电的储物。
- 盖子开关声：临时用原版铁活板门的声音，音高 0.7，在 0.85 m 高，3 格，带感染者噪声。要换成一条 Firefly 录的声音。
- 拆：一半没了另一半也没了：
  - 生存模式用铁镐以上拆任何一半：掉一台冷柜 + 里面的东西；
  - 用手或低一级的镐拆右半：不掉冷柜（和原版铁门一样），东西照样掉出来；
  - 拆的时候电源线收回（插在插座上的话拔出来）。

## 2. 拼块

`cabin_mesh/furniture/` 新加 30 个拼块，共 2969 个四边形：

| 家具 | 拼块 | 四边形 |
|---|---|---|
| 床 | `bed_head`、`bed_foot` | 504 |
| 衣柜 | `wardrobe_body_*`、`wardrobe_doors_{closed,open}_*`，各分 `lower` / `upper` | 350 |
| 床头柜 | `nightstand_body`、`nightstand_drawer_{closed,open}` | 172 |
| 工具架 | `tool_rack`、`tool_rack_log`（往外移 8 cm 的同一块） | 248 |
| 储物箱 | `chest_{single,left,right}_body`、`_lid_{closed,open}` | 589 |
| 冷柜 | `freezer_body_*`、`freezer_lid_{closed,open}_*`（`left` / `right`），`freezer_lamp_{off,lit}` | 1106 |

- 两格的家具（床、衣柜、冷柜、合起来的箱子）在生成器里整件画，再按格切开（`cut`、`clipped`）。
- 合页、锁扣这类跟着盖子动的零件分在盖子里；合页固定在箱身上的那片留在箱身。

## 3. 材质

贴图在 `textures/block/furniture/`，240 texel = 1 m，LabPBR（`_s`、`_n`），新的由 `tools/furniture/wood-furniture-v2-art.mjs` 生成，都不做凹凸（高度 0）：

| 贴图 | 用在 |
|---|---|
| `blanket` | 毛毯：苔绿色羊毛 |
| `linen` | 床单翻边、枕头 |
| `ticking` | 床垫：蓝灰条纹布 |
| `freezer_liner` | 冷柜内胆：白色，一片片霜 |
| `rubber` | 冷柜盖的密封条、C14 插座 |

沿用 V1 的 `wood`、`wood_v`、`wood_top`、`wood_panel`、`wood_inside`、`iron`、`cast_iron`、`enamel`，收音机的 `radio/chrome`、`radio/lamp`、`radio/lamp_glow`。

### 家具木色（2026-10-15 研究中，游戏贴图没改）

用户觉得家具颜色丑，让我查现实里木家具是什么颜色。

- 现在的木色：`tools/furniture/wood-furniture-art.mjs` 的 `FACE` [142,112,82]、`LATE` [112,86,62]，V1、V2 共用。
  - 量出来是 CIELAB L 49.4、a 7.6、b 21.2，彩度 C 22.5。
  - 比清漆橡木、蜂蜜色暗（它们 L 57–63），彩度又比所有真实饰面都低（26–41，只有胡桃木更低，但它暗得多）。
  - 结果是发灰的中棕：哪种真木头都不像。
- 北美老农舍常见的饰面：
  - 50 年代松木配琥珀虫胶（偏橙的蜂蜜色）；
  - 70 年代蜂蜜色 / Golden Oak 橡木、枫木；
  - 清漆原色橡木；
  - 晒深的樱桃木（红棕）；
  - 胡桃木（巧克力棕）。
- 对比：
  - 脚本 `design/buildings/forest_start_home_v1/m4_source/wood_finish_swatches.mjs` 按目标 Lab 算出每种的 `FACE`/`LATE`（晚材 L−11，a、b ×1.05），写进 `m4_sheets/finish_<key>.png` 和 `finish_table.json`。
  - 每种的五张木贴图写进 `m4_source/finish_tex/<key>/`。为此 `wood-furniture-art.mjs` 加了只给设计用的 `setFinish(face, late)`，游戏输出不变（`--check` V1 82、V2 69 OK）。
  - 预览页 `furniture_v2_preview.html` 的 `window.finishSheet(view, keys, labels, file)` 换贴图出对比图：`m4_sheets/wood_finish_room.png`、`wood_finish_chests.png`。
- 等用户选。选定后只改 `FACE`/`LATE`，重新生成 V1、V2 贴图。

## 4. 挖掘和掉落（AGENTS.md 审计）

| 方块 | 工具 | 等级 | 要用对工具才掉 | 硬度 |
|---|---|---|---|---|
| 床 | 斧 | — | 否 | 0.8 |
| 衣柜、储物箱 | 斧 | — | 否 | 2.5 |
| 床头柜、工具架 | 斧 | — | 否 | 2.0 |
| 冷柜 | 镐 | 铁（`needs_iron_tool`） | 是 | 3.0（抗爆 6） |

- 木家具照原版木头的规则（在 `minecraft:mineable/axe`，不在等级标签），可燃 20 / 蔓延 5，在 `noise_wood_blocks`；冷柜和商用冷柜一样，不可燃。
- 掉落（`survives_explosion`）：床只从床头掉，衣柜只从下半格掉，冷柜只从主格掉；容器把东西掉出来，工具架把挂着的东西掉出来。
- 用户 2026-10-14 说以后要换掉原版挖掘等级，新方块用什么等级都行；这里先按 AGENTS.md 的默认规则。

## 5. 接入

- 提示（`WorldInteractionHint#woodFurniture`，一个动作词）：

  | 对着 | 提示 |
  |---|---|
  | 衣柜、床头柜、储物箱、冷柜 | 打开 / Open（潜行时不提示） |
  | 床 | 睡觉 / Sleep（新键 `hint.apocalypse_firstlight.bed.sleep`） |
  | 工具架 | 挂上 / Hang、放上 / Put、取下 / Take，显示在那个位置 |
  | 冷柜，潜行空手 | 插头的提示（拿起 / 放回 / 拔下），和其他电器一样 |

- Jade：对着衣柜上半格、冷柜右半格时，改成显示存东西的那一格（`compat/jade/WoodFurnitureV2JadeRedirect`）。其余用默认的方块名和容器内容。
- 冷柜：
  - 电源线由 `VintageFreezerRenderer` 画（`PlugCordRenderer`），柜体、盖子、指示灯都在区块里；
  - 压缩机循环声加进 `BlockLoopSoundController` 的声源表。
- 语言：方块名在 `wooden_gun_rack` 后面，`bed.sleep` 在 `cabinet.open` 后面，en_us / zh_cn 逐行对齐。
- 开发搭建桥（`AuthoringFixtureRegistry`）：六件都登记了：
  - 床：`part` 两格，锚点是床脚；
  - 衣柜：`TWO_TALL`；
  - 冷柜：两格，锚点是主格，右半在 `facing.getClockWise()`；
  - 储物箱：只登记单个；
  - 工具架：要背后的墙。
- 收音机放在床头柜上：见第 1 节。

## 6. 文件

- 代码：
  - `cabin/WoodFurnitureV2.java`：六个方块类、三个方块实体类型的注册；
  - `cabin/WoodToolRackBlockEntity.java`、`StorageChestBlockEntity.java`、`VintageFreezerBlockEntity.java`；
  - `cabin/WoodFurniture.java`：新接口 `Storage`（格数、开关、声音），V1 的柜子、V2 的衣柜 / 床头柜 / 冷柜都实现它；V1 柜子的方块实体类型也收了衣柜和床头柜；
  - `cabin/WoodCounterBlockEntity.java`：格数和声音从 `Storage` 读（9 / 18 / 27 格），加了带类型的构造（冷柜用）；
  - `cabin/WoodGunRackBlockEntity.java`：加了带类型的构造（工具架用）；
  - `cabin/CabinBlocks.java`：六个 ID、物品（床 `BedItem`，衣柜 `DoubleHighBlockItem`）；
  - `cabin/EmergencyRadio.java`：`Surface.NIGHTSTAND`；
  - `block/LogMount.java`：`PAD_TOOL_RACK = 12`；
  - 客户端：`WoodGunRackRenderer`（工具架的画法）、`WoodGunRackChunking`（录制按方块状态记）、`VintageFreezerRenderer`、`ColdMist`、`BlockLoopSoundController`、`AflBlockEntityRenderers`、`WorldInteractionHint`；
  - `compat/jade/WoodFurnitureV2JadeRedirect.java`、`AflJadePlugin`。
- 生成器：
  - `tools/build-wood-furniture-v2.mjs`：拼块、方块 / 物品模型、方块状态、战利品表；`--check` 核对输出并审标签、质量、搬运标签、语言；
  - `tools/furniture/wood-furniture-v2-art.mjs`：贴图；
  - `tools/build-cabin-exterior-v1.mjs`：`PADS` 加了第 12 种，写出 `log_wall/pad_12`。
  - 生成器就是可编辑源，没有 `.bbmodel`。
- 资源：`blockstates/`、`models/block/furniture/`、`models/item/` 下六件的文件，`cabin_mesh/furniture/` 30 个拼块，`loot_tables/blocks/` 六个。
- 数据：`mineable/axe`、`mineable/pickaxe`、`needs_iron_tool`、`noise_wood_blocks`、`carry/bulky`、`carry/oversized`、`item_mass/afl_content_v1.json`。
- 测试：`src/dev/java/.../dev/FurnitureV2GameTests.java`，`src/dev/furniture-v2-gametest.init.gradle`。
- 离线预览：`design/buildings/forest_start_home_v1/m4_source/furniture_v2_preview.html`（three.js，在 `m1_source/serve.cjs` 上开），图在 `m4_sheets/fv2_*.png`。不是游戏截图：没有光照贴图、法线贴图，工具架上没有工具。

## 7. 没做 / 已知限制

- 没有合成配方（和 V1 一样，等工匠台那一轮）。
- 原版箱子没有下掉：用户说不需要了，但它的配方、世界里已有的箱子、战利品结构都还在。要不要关掉原版箱子的配方、老宅模板里换掉原版箱子，等用户定。
- 冷柜：
  - 盖子开关声是占位（原版铁活板门），要一条 Firefly 录的声音；
  - 没有腐坏系统，冷不冷对食物没有影响。
- 工具架：工具按"原版工具图标的对角线"挂。自己有 3D 模型的物品（AFL 的 Mesh 物品）可能挂歪或穿进背板，没验证。
- 开合（门、抽屉、盖子）是一下子换的，没有动画（V1 柜子也是这样）。
- 容器不接逐格搜索（同 V1），开局老宅的容器打开就是普通储物。
- 开发搭建桥的自检 GameTest（`bridge-fixture-gametest`）本来就不通过：18 个旧问题（`jerry_can`、`emergency_radio`、木门等没登记），没有 V2 的。这轮没修。
- 用户 2026-10-15 实机 PASS，材质还要精修（下一轮），见第 8 节。

## 8. 验证记录

- 2026-10-15（游戏外，开着的是 PCL 的另一个实例 `D:\Games\MC TEST`，不是开发客户端）：
  - `node tools/build-wood-furniture-v2.mjs --check`：CHECK OK（69 个输出），审计 0 问题；V1 `--check` OK（82）；小屋外饰 `--check` OK（354）；
  - `compileJava` 通过；
  - `FurnitureV2GameTests`（`-PaflWithoutShaders`，测试服务器，新建的测试世界）**PASS**：
    - 用物品放：床（脚在点的格、头朝看的方向）、衣柜（上半格跟着放）、冷柜（右半在东边）、两个储物箱并排合成 `left` / `right`；
    - 界面：衣柜 27（从上半格开）、床头柜 9、冷柜 27（从右半格开）、单箱 27、合箱 54；开着时每格 `open=true`，关上后都回到 false；没插电的冷柜不亮；
    - 拆：生存斧拆衣柜上半格 → 两格都没了，掉 1 个衣柜 + 里面的胸甲；铁镐拆冷柜右半 → 掉 1 台冷柜 + 5 块牛肉；空手拆冷柜右半 → 不掉冷柜，2 条鳕鱼照样掉；拆床脚 → 床头也没了，掉 1 张床；拆合箱的一半 → 另一半变回单箱；
    - 工具架：铁斧挂到看准的 0 号木钉、7 根木棍放到第一个空搁板位、圆石放不上、空手取回铁斧；墙拆了 → 工具架和木棍都掉下来；
    - 收音机放在床头柜上：`surface=nightstand`；
    - 30 个拼块都在；
    - 生存挖掘：六件的工具 / 等级标签、`requiresCorrectToolForDrops`、空手、低一级、对的工具拆的掉落都对（6 个方块 13 次拆）。
  - `ForestHomeM2GameTests` 重跑 **PASS**（收音机的 `surface` 加了一个值以后）。
  - 没测（要客户端看）：所有样子、开合换模型、提示、Jade、工具架上的工具怎么挂、区块画工具、冷雾、电源线、压缩机声音、指示灯、睡觉。
- 2026-10-15（同日，用户实机）：**PASS**（"pass，明天继续精修材质和摆放"）。用户没有逐项报告，上面没测的各项没有单独确认。下一轮：精修材质，在「house test」里摆放。
