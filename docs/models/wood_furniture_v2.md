# Wood Furniture V2（卧室和储物家具，2026-10-15）

开局老宅（[老宅 B V1 计划](../worldgen/forest_start_home_b_v1_plan.md)）M4 第二轮之前补的内饰。用户 2026-10-15 的要求：

- 做 Mesh 木床、Mesh 衣柜、床头柜、工具架；
- 冷柜现在是商用的，做一台老式大铁柜；
- 原版箱子不要了，做自己的储物箱（Mesh），逻辑可以复用，ID 用自己的。

和 [Wood Furniture V1](wood_furniture_v1.md) 同一套做法：几何由生成器程序生成，朝北画，静止时进区块画。床和工具架是 Mesh 拼块（`CabinPieces`，`cabin_mesh/furniture/`）；衣柜、床头柜、储物箱、冷柜从 2026-10-15 起是 AFL 动画网格，门、抽屉、盖子平滑开合（第 2 节）；木家具不分树种，和 V1 共用一套木色（2026-10-15 起是胡桃木油饰面，第 3 节"家具木色"；2026-10-20 起木纹改用 CC0 实拍胡桃木单板，见 [Wood Furniture V1](wood_furniture_v1.md) 的"成品木色"）。概念图 2026-10-15 发给了用户（`design/buildings/forest_start_home_v1/m4_sheets/fv2_sheet.png`），用户还没回复意见。

## 1. 方块

| ID | 名字 | 格 | 存储 | 质量 | 搬运 |
|---|---|---|---|---|---|
| `wooden_bed` | Wooden Bed / 木床 | 1 × 2（脚、头） | — | 40 kg | oversized |
| `wooden_wardrobe` | Wooden Wardrobe / 木衣柜 | 1 × 1 × 2 高 | 27 格 | 55 kg | oversized |
| `wooden_nightstand` | Wooden Nightstand / 木床头柜 | 1 | 9 格 | 9 kg | bulky |
| `wooden_tool_rack` | Wooden Tool Rack / 木质工具架 | 1，挂墙 | 5 个位置 | 3 kg | bulky |
| `wooden_storage_chest` | Wooden Storage Chest / 木储物箱 | 2 × 1（2026-10-15 起；原来 1 格、两个并排合箱） | 27 格 | 30 kg | oversized |
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
- 27 格存储在下半格的方块实体（V1 的 `WoodCounterBlockEntity`）；对着上半格操作也是同一个。
- 开合按[可搜索容器的统一两步](../gameplay/progressive_container_search_v1.md)（`cabin/FurnitureOpening`）（2026-10-15 补）：对着门右键开门（两半格都 `open=true`，两扇门朝外开，不开界面）；门开着时对着两门之间的柜里右键开界面；对着开着的门右键关门。关掉界面门还开着。
- 开关声：V1 柜子的 `wood_cabinet_open` / `_close`，在门的高度（1.0 m），3 格，带同半径的感染者噪声。
- 放：上面那格要能放；放下时自动放上半格。
- 拆：一半没了另一半也没了（像门）：
  - 生存模式用手或斧拆任何一半：掉一个衣柜 + 里面的东西；
  - 创造模式拆上半格：什么都不掉（东西还是掉出来）。

### 木床头柜

- 一格，52 × 40 cm，台面高 0.66 m，上面一个抽屉，下面一层开放格。
- 9 格存储；开合同衣柜的两步：对着抽屉面右键拉出 24 cm，对着拉出来的抽屉里面（从上面看）右键开界面，对着抽屉面再右键推回去。开关声同衣柜，在 0.55 m。
- 台面不是交互区域：拿着收音机对着台面右键照常放上去。
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
  - 伐木斧例外（2026-10-20）：按它自己的网格竖着挂，斧柄从两根木钉中间穿过，斧头架在钉上，全长约 0.96 m（[伐木斧 V1](../gameplay/felling_axe_v1.md) 第 6 节）；
  - 羊角锤也按自己的网格挂（2026-10-21，[AFL 工作台 V1](../gameplay/afl_workbench_v1_design.md) 第 14 节）：锤柄竖直在两根钉中间，锤头横架在钉上，锤面朝 -x、羊角朝 +x，按真实大小（约 33 cm）；用户 2026-10-21 对工作台和羊角锤 PASS（工具架上的挂法没单独确认）；
  - 圆头铲按自己的网格挂（2026-10-22，[土方 A2](../worldgen/earthwork_a2.md) 4.3 节）：D 形把手挂在每对里靠中间的那根钉上，铲头朝下、垂到下一格约 67 cm；中间一格往外挂 7.2 cm，两把铲子并排不会叠在一起；装着土的不收，搁板不收铲子；没进游戏看；
  - 搁板上的东西平躺，和枪架搁板一样；
  - 静止的东西由区块画（V1 枪架的录制，`WoodGunRackChunking`），录制按方块状态记，原木墙上外移的也算在内。
- 圆木墙上：站在垫块上，垫块种类 12（`LogMount.PAD_TOOL_RACK`，背板和搁板背后 0.2, 10.6 → 15.8, 14.6 px，见 [小屋外饰 V1](cabin_exterior_v1.md) 壁挂件垫块）。不加垫块的话，背板会埋进原木鼓出的 7 cm 里。
- 选框：背板加搁板；碰撞只有搁板那一段（上面 10.88..14.4 px），人能贴墙走。

### 木储物箱

2026-10-15 重做（用户："有点像棺材，并且太小了"；选了方案 A，"直接做成多方块箱子就行，不要做2合1"）。原来是原版箱子的逻辑（继承 `ChestBlock`，92 × 52 × 50 cm 的板箱，两个并排合成 192 cm 的长箱），现在是两格宽的框板毯子箱：

- 两格宽，同冷柜（`WoodFurnitureV2.Wide`，冷柜也改成继承它）：`part` = `left`（主格，方块实体在这）/ `right`，`right` 在 `facing.getClockWise()`（从正面看 `left` 在右边）。从左格放，右格跟着放；拆一半另一半也没了，主格掉 1 个箱子和里面的东西；创造模式拆右半不掉箱子。不再合箱，没有 `type`。
- 样子（`tools/build-wood-furniture-v2.mjs` `CHEST`）：124 × 50 cm，盖顶 0.598 m（毯子箱实物多在 109–124 × 46–53 cm、高 51–58 cm），两格居中：
  - 8 cm 的支架脚（四角，内端退一级），上面底线脚；
  - 箱身 2 cm 板，外面框板：正面三块凹板（立梃、上下帽头、两根中梃，框高出板面 1 cm），两端各一块；
  - 腰线脚绕正面和两端；3.3 cm 的盖，前、两端出 2.5 cm，盖下两端两条压条，背后两个合页（轴 y 0.565、z 0.758）；
  - 上帽头中间一个铁锁眼盖，两端上帽头上铁吊环提手；
  - 里面：箱底 0.135，主格那头（从正面看右端）一个搁格盘（敞口托盘）。
- 27 格，存在主格的方块实体（`StorageChestBlockEntity`，现在继承 `WoodCounterBlockEntity`）。方块实体的 ID 没变，以前存的箱子读档时东西还在；但方块状态变了（`type` 没了，`part` 默认 `left`），以前放下的箱子只剩一个左半，要拆了重放。老宅模板（`forest_home_b.nbt`）里没有这个箱子。
- 开合按[可搜索容器的统一两步](../gameplay/progressive_container_search_v1.md)（`cabin/FurnitureOpening`）：对着盖子右键掀盖（两半都 `open=true`，盖子转 80°），对着箱口右键开界面，对着掀起的盖子右键合上；关界面盖子还开着。带战利品表、又不是玩家放的要逐格搜。
- 掀起的盖子靠在后面，顶到 1.10 m，高出本格：准星对着上面那格里的盖子也能选中（`AflOverhangPickBlock`）。选框和碰撞按开关两种：关着到盖顶，开着是箱身加后面立着的盖子。
- 开关声：原版箱子的开、关声，在 0.6 m，带同半径的感染者噪声（原来不带）。
- 原版箱子还在游戏里，配方也没动，见第 7 节。

### 老式冷柜

- 六十年代的卧式冷柜，两格宽：`part` = `left`（主格，方块实体在这）/ `right`。左右是方块自己的左右，和原版箱子一样：从正面看，`left` 在右边，`right` 在 `facing.getClockWise()`。
- 160 × 74 cm，高 90 cm：圆角的奶白搪瓷柜体，下面一条深色百叶踢脚板；一整块盖子在后边合页上，打开转 80°；盖子正面中间一个镀铬拉手带锁；里面是结霜的白内胆，非主格那头（从正面看左边）挂一个铁丝筐。
- 正面上部、主格那头（从正面看右边）一个指示灯和温控旋钮。
- 27 格存储；开合同衣柜的两步：对着盖子右键掀盖（两半都 `open=true`），对着井口右键开界面，对着掀起的盖子右键合上。两半格各自认整块盖子和整个井口。
- 盖子掀起来高过柜子 0.61 格（到 1.61），落在上面那格里：那一格是空气时，准星对着盖子照样能选中、能合上（`AflOverhangPickBlock`）。
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

## 2. 网格

### 动画网格（Furniture Mesh V1，2026-10-15）

为什么改：用户 2026-10-15 实机发现这批家具开合只有"两帧"——门、抽屉、盖子是关 / 开两套拼块一下子换，不像以前的柜门、箱盖那样平滑转。于是改走已经实机 PASS 的 [AFL Animated Block Mesh Runtime](../rendering/animated_block_mesh_runtime_v1.md)（用户选的方案 A），没有另写一套动画。

- 衣柜、床头柜、储物箱、冷柜（还有 V1 的两种柜子，见 [Wood Furniture V1](wood_furniture_v1.md)）是 AFL 动画网格方块：主格的方块实体是宿主（`AflAnimatedMeshHost`），通道 `open` 跟着方块状态 `open`，静止时由区块画，动的时候由 `AflAnimatedBlockMeshRenderer` 画；非主格不画东西。
- 几何没有重画：还是生成器里同一套部件函数（`wardrobeBody`、`wardrobeDoor`、`nightstandDrawer`、`chestLid`、`freezerBody` 等），交给共用导出器 `tools/furniture/furniture-mesh.mjs`。导出器按骨骼分组，把每种材质（240 px LabPBR，`_s` / `_n`）原样拼进这一件自己的图集（每格 256 px：240 + 四周各 8 px 环绕边），UV 还是原来的平面投影（1 格 = 一张材质），所以木纹、比例和以前一样。法线变成面法线（运行时按面算），和原来拼块的平滑法线略有差别。
- 骨骼和动画（`block_mesh_profiles/<id>.json`）：

  | 家具 | 骨骼 | 通道 `open` | 图集 | 三角形 |
  |---|---|---|---|---|
  | 衣柜 | `body`；`door_left` / `door_right`（铰在两侧门框前沿） | 两扇门各转 90° 朝外，10 tick | 768 × 512 | 444 |
  | 床头柜 | `body`；`drawer` | 抽屉往前拉 0.24 格，7 tick | 768 × 512 | 266 |
  | 储物箱 | `body`；`lid`（铰在后沿） | 盖子转 80°，12 tick | 768 × 768 | 706 |
  | 冷柜 | `body`；`lid`（铰在后沿）；`lights` / `lights_lit` | 盖子转 `FREEZER.open`，14 tick | 768 × 768 | 1710 |

- 冷柜指示灯：`lights`（灭）和 `lights_lit`（亮，全亮度 + LabPBR 发光）两套，`VintageFreezerBlockEntity#meshPartVisible` 按通没通电切换，`meshPartEmissive` 标亮的那套。
- 两格的家具（衣柜、储物箱、冷柜）由主格整件画；渲染范围是 profile 的 `bounds`（静止、全开、半开三个姿势的包围盒外扩 1/4 px），冷柜再并上电源线的范围。
- 物品：`builtin/entity` + `AflStaticMeshItemRenderer`（`AflStaticMeshItemClient` 绑定），画关着的样子、灯灭；各视角由生成器按尺寸拟合。
- 可编辑源：`src/main/blockbench/furniture/<id>.bbmodel`（Blockbench Free Model，每个骨骼 + 材质一个元素）和 `textures/<id>{,_s,_n}.png`。源是生成器写出来的，改几何还是改生成器。

### 拼块（床、工具架）

床和工具架没有活动部件，仍是 `CabinPieces`，`cabin_mesh/furniture/` 4 个拼块，共 752 个四边形：

| 家具 | 拼块 | 四边形 |
|---|---|---|
| 床 | `bed_head`、`bed_foot` | 504 |
| 工具架 | `tool_rack`、`tool_rack_log`（往外移 8 cm 的同一块） | 248 |

- 床在生成器里整件画，再按格切开（`cut`、`clipped`）。
- 合页、锁扣这类跟着盖子动的零件分在盖子骨骼里；合页固定在箱身上的那片留在箱身。
- 已删除（2026-10-15，改成动画网格后不再生成）：`wardrobe_*`、`nightstand_*`、`chest_*`、`freezer_*` 共 23 个拼块。

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

### 家具木色（2026-10-15 改成胡桃木）

用户觉得家具颜色丑，让我查现实里木家具是什么颜色。看了对比图后定了胡桃木（F）。

- 现在的木色（V1、V2 共用，`tools/furniture/wood-furniture-art.mjs`）：胡桃木油饰面，`FACE` [106,82,63]（CIELAB L 36.9、a 7.1、b 15.0），`LATE` [80,57,38]。
  - 和六种树种的木板（L 62–85）、浅色原木墙、白橡地板深浅分得开。
  - 变的只有五张木贴图 `wood`、`wood_v`、`wood_top`、`wood_panel`、`wood_inside`。铁件、搪瓷、布料没动，拼块、模型没变（`--check` V1 82、V2 69 OK）。
  - 改色前后都没在游戏里看过，只看了预览页的对比图。
- 改之前的木色：`FACE` [142,112,82]、`LATE` [112,86,62]。
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
- 制作：用户 2026-10-15 定的做法：在木匠台上，用任意树种的 AFL 木头加油漆做。木匠台和家具用的油漆都还没有（现在只有路面漆），配方等木匠台那一轮（[木材体系计划](../dev/wood_system_plan_v1.md)）。

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
  | 衣柜的门、床头柜的抽屉、储物箱和冷柜的盖子（关着） | 打开 / Open，显示在拉手 / 盖子前沿 |
  | 同上（开着） | 关上 / Close |
  | 开着的柜里、抽屉里、井口 | 搜索 / Search（还有没搜的格）或 查看 / View |
  | 床 | 睡觉 / Sleep（新键 `hint.apocalypse_firstlight.bed.sleep`） |
  | 工具架 | 挂上 / Hang、放上 / Put、取下 / Take，显示在那个位置 |
  | 冷柜，潜行空手 | 插头的提示（拿起 / 放回 / 拔下），和其他电器一样 |

- Jade：对着衣柜上半格、储物箱和冷柜的右半格时，改成显示存东西的那一格（`compat/jade/WoodFurnitureV2JadeRedirect`）。其余用默认的方块名和容器内容。
- 冷柜：
  - `VintageFreezerRenderer` 先画电源线（`PlugCordRenderer`），再交给 `AflAnimatedBlockMeshRenderer` 画动着的盖子和亮着的指示灯；静止的柜体、盖子、灭着的灯在区块里；
  - 压缩机循环声加进 `BlockLoopSoundController` 的声源表。
- 交互区域（2026-10-15 补）：Mesh Shape profile `data/apocalypse_firstlight/mesh_shapes/{wooden_wardrobe,wooden_nightstand,wooden_storage_chest,vintage_chest_freezer}.json`，由生成器从画出来的拼块取包围盒写出（衣柜 `cells_y` 2；储物箱、冷柜各四个状态 `{left,right}_{closed,open}`，右半格在自己的坐标里往左挪一格），区域 `door` / `drawer` / `lid` / `interior`，比拼块大 1 cm 好瞄。没对准任何区域时 `use` 返回 PASS。碰撞还是原来手写的形状，profile 只管交互。准星、选框、子弹从 2026-10-15 起按动画网格算（[Mesh Hit Runtime V1](../rendering/mesh_hit_runtime_v1.md) 0.6 节，`AnimatedMeshHits`；非主格经 `MeshHitMultiCell` 取主格整件），开着的门 / 盖子按开着的姿势算，动画进行中那几 tick 退回方盒子。
- 语言：方块名在 `wooden_gun_rack` 后面，`bed.sleep` 在 `cabinet.open` 后面，`cabinet.close` / `search` / `view`（关上 / 搜索 / 查看）跟在 `cabinet.open` 后面，en_us / zh_cn 逐行对齐。
- 开发搭建桥（`AuthoringFixtureRegistry`）：六件都登记了：
  - 床：`part` 两格，锚点是床脚；
  - 衣柜：`TWO_TALL`；
  - 冷柜：两格，锚点是主格，右半在 `facing.getClockWise()`；
  - 储物箱：两格，同冷柜；
  - 工具架：要背后的墙。
- 收音机放在床头柜上：见第 1 节。

## 6. 文件

- 代码：
  - `cabin/WoodFurnitureV2.java`：六个方块类、三个方块实体类型的注册；`Cabinet`（衣柜、床头柜、储物箱、冷柜）实现 `MeshHitMultiCell`，不再是 `CabinPieces`；
  - `cabin/WoodToolRackBlockEntity.java`、`StorageChestBlockEntity.java`（2026-10-15 起继承 `WoodCounterBlockEntity`）、`VintageFreezerBlockEntity.java`；
  - `cabin/WoodFurniture.java`：新接口 `Storage`（格数、开关、声音），V1 的柜子、V2 的衣柜 / 床头柜 / 冷柜都实现它；V1 柜子的方块实体类型也收了衣柜和床头柜；
  - `cabin/FurnitureOpening.java`（2026-10-15 补）：两步开合，按瞄准的区域决定开 / 关 / 搜索 / 查看，`use` 和提示共用；
  - `cabin/WoodCounterBlockEntity.java`：格数和声音从 `Storage` 读（9 / 18 / 27 格），加了带类型的构造（冷柜用）；接逐格搜索（`AflSearchableContainer`，40 ticks/格 ±15%），门由玩家开关（`setDoors`），不再按有没有人开着自动关；
  - `cabin/WoodGunRackBlockEntity.java`：加了带类型的构造（工具架用）；
  - `cabin/CabinBlocks.java`：六个 ID、物品（床 `BedItem`，衣柜 `DoubleHighBlockItem`）；
  - `cabin/EmergencyRadio.java`：`Surface.NIGHTSTAND`；
  - `block/LogMount.java`：`PAD_TOOL_RACK = 12`；
  - `cabin/WoodCounterBlockEntity.java` 实现 `AflAnimatedMeshHost`（profile 按方块 ID，通道 `open`），衣柜、床头柜、储物箱、冷柜的方块实体都继承它；
  - 客户端：`WoodGunRackRenderer`（工具架的画法）、`WoodGunRackChunking`（录制按方块状态记）、`VintageFreezerRenderer`、`ColdMist`、`BlockLoopSoundController`、`AflBlockEntityRenderers`（柜子类型、储物箱类型挂 `AflAnimatedBlockMeshRenderer`）、`AflStaticMeshItemClient`（四件的物品）、`WorldInteractionHint`；
  - `compat/jade/WoodFurnitureV2JadeRedirect.java`、`AflJadePlugin`。
- 生成器：
  - `tools/build-wood-furniture-v2.mjs`：部件、拼块（床、工具架）、四件的动画网格规格（`meshes()`、`MESH_IDS`）、方块 / 物品模型、方块状态、战利品表、交互 profile；
  - `tools/furniture/furniture-mesh.mjs`（2026-10-15）：V1 / V2 共用的动画网格导出器，写 Blockbench 源、geo、sidecar、profile、图集、方块 / 物品模型；
  - `tools/furniture/furniture-shapes.mjs`：V1 / V2 共用，把拼块的包围盒换成 profile 的像素坐标；`--check` 核对输出并审标签、质量、搬运标签、语言；
  - `tools/furniture/wood-furniture-v2-art.mjs`：贴图；
  - `tools/build-cabin-exterior-v1.mjs`：`PADS` 加了第 12 种，写出 `log_wall/pad_12`。
  - 生成器就是可编辑源；四件动画网格另有生成器写出的 `src/main/blockbench/furniture/<id>.bbmodel`。
- 资源：`blockstates/`、`models/block/furniture/`、`models/item/` 下六件的文件（四件动画网格的方块模型是 `static_mesh` 加载器，物品是 `builtin/entity`），`cabin_mesh/furniture/` 4 个拼块，四件的 `geo/`、`meshes/`、`block_mesh_profiles/`、`textures/block/furniture/<id>{,_s,_n}.png`，`loot_tables/blocks/` 六个。
- 数据：`mineable/axe`、`mineable/pickaxe`、`needs_iron_tool`、`noise_wood_blocks`、`carry/bulky`、`carry/oversized`、`item_mass/afl_content_v1.json`。
- 测试：`src/dev/java/.../dev/FurnitureV2GameTests.java`，`src/dev/furniture-v2-gametest.init.gradle`。
- 离线预览：`design/buildings/forest_start_home_v1/m4_source/furniture_v2_preview.html`（three.js，在 `m1_source/serve.cjs` 上开），图在 `m4_sheets/fv2_*.png`。不是游戏截图：没有光照贴图、法线贴图，工具架上没有工具。2026-10-15 起衣柜、床头柜、储物箱、冷柜的拼块删了，这个页面（和 M2 的 `m2_source/furniture_preview.html` 里的两种柜子）读不到它们，只剩床、工具架（和桌椅、枪架）；没改页面。

## 7. 没做 / 已知限制

- 没有合成配方。用户 2026-10-15 定的做法：在木匠台上，用任意树种的 AFL 木头加油漆做。木匠台和家具用的油漆都还没有（现在只有路面漆），配方等木匠台那一轮（[木材体系计划](../dev/wood_system_plan_v1.md)）。
- 原版箱子没有下掉：用户说不需要了，但它的配方、世界里已有的箱子、战利品结构都还在。要不要关掉原版箱子的配方、老宅模板里换掉原版箱子，等用户定。
- 冷柜：
  - 盖子开关声是占位（原版铁活板门），要一条 Firefly 录的声音；
  - 没有腐坏系统，冷不冷对食物没有影响。
- 工具架：工具按"原版工具图标的对角线"挂。自己有 3D 模型的物品（AFL 的 Mesh 物品）可能挂歪或穿进背板，没验证。伐木斧已经单独适配（2026-10-20，没进游戏看）；撬棍还是斜挂。
- ~~开合（门、抽屉、盖子）是一下子换的，没有动画~~：2026-10-15 改成动画网格（第 2 节），V1 柜子同时改了。箱式柴炉的炉门、收音机的摇柄同日也改成动画网格（[Box Stove V1](box_stove_v1.md) 第 7 节、[Emergency Radio V1](emergency_radio_v1.md) 第 4 节）。小屋木门、收音机的拨动开关仍是一下子换的拼块（小屋门没有方块实体，要改得先加）。
- 逐格搜索（2026-10-15 接上）：衣柜、床头柜、储物箱、冷柜（和 V1 柜子）带战利品表、又不是玩家放的，第一次开界面要逐格搜；玩家放的打开就是普通储物，空的（没战利品表）也不用搜。开局老宅自家容器不用搜的豁免（计划 P5，按战利品表前缀放行）**还没做**：老宅里的家具要是给了战利品表，现在会要搜。
- 储物箱方案图（2026-10-15，离线预览，不是游戏截图）：`design/buildings/forest_start_home_v1/m4_sheets/chest_concepts.png`，模型 `m4_source/chest_concepts.mjs`、页面 `m4_source/chest_concepts_preview.html`（页面里"现在的箱子"用的旧拼块已经删了，图留作记录）。用户选了 A，做成两格（见第 1 节「木储物箱」）：
  - 参考的实物尺寸（商家 / 博物馆的单件，不是标准）：毯子箱（blanket / hope chest）多在 109–124 × 46–53 cm、高 51–56 cm；北卡约 1785 年的六板箱 119 × 46 × 58 cm；18 世纪末两抽屉毯子箱 127 × 58 × 74 cm；二战前后的军用脚柜 81 × 42 × 33 cm；
  - 原来的箱子单个 92 × 52 × 50 cm 和实物差不多，显小、像棺材主要因为：高度只有半格；直上直下的板箱，没有脚、没有线脚；合箱 192 × 52 × 50 cm 正是棺材的长宽高；
  - A 框板毯子箱（支架脚、腰线、两块凸板、搭扣锁眼）96 × 48 × 59；B 两抽屉毯子箱 96 × 50 × 79；C 铁包角平顶行李箱（深盖、木条、铁角）90 × 52 × 58。三个都是一格、不再合箱。
- 开发搭建桥的自检 GameTest（`bridge-fixture-gametest`）本来就不通过：18 个旧问题（`jerry_can`、`emergency_radio`、木门等没登记），没有 V2 的。这轮没修。
- 用户 2026-10-15 实机 PASS，材质还要精修（下一轮），见第 8 节。
- 贴图密度对比（2026-10-15，离线预览）：家具贴图从 240 px/m 缩到 128 / 64 / 32（墙和地板不变），`m4_sheets/texture_res_near.png`、`texture_res_room.png`。正常距离下 240 和 128 看不出差别，64 / 32 只是木纹糊掉、更像塑料（缩图，不是按 32 px 重画的像素木纹）。结论：显得格格不入不是分辨率造成的。另外 120 px 不行：图集的 mipmap 级数受每张贴图边长最低的 2 的幂位限制，120 只到 3 级（240 是 4 级），会拖低整张方块图集；要降只能 128。

## 8. 验证记录

- 2026-10-15（游戏外，开着的是 PCL 的另一个实例 `D:\Games\MC TEST`，不是开发客户端）：
  - `node tools/build-wood-furniture-v2.mjs --check`：CHECK OK（69 个输出），审计 0 问题；V1 `--check` OK（82）；小屋外饰 `--check` OK（354）；
  - `compileJava` 通过；
  - `FurnitureV2GameTests`（`-PaflWithoutShaders`，测试服务器，新建的测试世界）**PASS**：
    - 用物品放：床（脚在点的格、头朝看的方向）、衣柜（上半格跟着放）、冷柜（右半在东边）、两个储物箱并排合成 `left` / `right`（当时的单格箱子）；
    - 界面：衣柜 27（从上半格开）、床头柜 9、冷柜 27（从右半格开）、单箱 27、合箱 54；开着时每格 `open=true`，关上后都回到 false；没插电的冷柜不亮；
    - 拆：生存斧拆衣柜上半格 → 两格都没了，掉 1 个衣柜 + 里面的胸甲；铁镐拆冷柜右半 → 掉 1 台冷柜 + 5 块牛肉；空手拆冷柜右半 → 不掉冷柜，2 条鳕鱼照样掉；拆床脚 → 床头也没了，掉 1 张床；拆合箱的一半 → 另一半变回单箱；
    - 工具架：铁斧挂到看准的 0 号木钉、7 根木棍放到第一个空搁板位、圆石放不上、空手取回铁斧；墙拆了 → 工具架和木棍都掉下来；
    - 收音机放在床头柜上：`surface=nightstand`；
    - 30 个拼块都在；
    - 生存挖掘：六件的工具 / 等级标签、`requiresCorrectToolForDrops`、空手、低一级、对的工具拆的掉落都对（6 个方块 13 次拆）。
  - `ForestHomeM2GameTests` 重跑 **PASS**（收音机的 `surface` 加了一个值以后）。
  - 没测（要客户端看）：所有样子、开合换模型、提示、Jade、工具架上的工具怎么挂、区块画工具、冷雾、电源线、压缩机声音、指示灯、睡觉。
- 2026-10-15（同日，用户实机）：**PASS**（"pass，明天继续精修材质和摆放"）。用户没有逐项报告，上面没测的各项没有单独确认。下一轮：精修材质，在「house test」里摆放。
- 2026-10-15（同日，两步开合 + 搜索，游戏关着）：
  - V1 / V2 生成器 `--check`：CHECK OK（83 / 72 个输出）；`compileJava compileDevJava` 通过；
  - `FurnitureV2GameTests` **PASS**（假玩家按视线瞄准）：衣柜从上半格对门开门、不出界面，对柜里出 27 格（上、下半格都试了），关界面门还开着，对左门关上；床头柜对台面右键 PASS，对抽屉拉出、从上面对抽屉里出 9 格、对抽屉推回；一个用 `setBlock` 放、带战利品表的床头柜出的是搜索界面；冷柜对右半格的盖子掀盖、不出界面，对井口出 27 格（左右半格都试了），对掀起的盖子合上；其余同上一条；
  - `ForestHomeM2GameTests` **PASS**：两种柜子对门开门、对柜里出 18 / 9 格、对开着的门关上；
  - 没测（要客户端看）：提示的字和位置、准星对着掀起的冷柜盖、开合声、多人同时开。
- 2026-10-15（同日，两格储物箱，游戏关着）：
  - `--check` CHECK OK（70 个输出），审计 0 问题；`compileJava compileDevJava` 通过；
  - `FurnitureV2GameTests` **PASS**：放下右半在东边、右半没有方块实体、主格位置对；对右半格的盖子掀盖（两半 `open=true`、不出界面），从左右两半格对箱口都出 27 格，对掀起的盖子合上；空手拆右半 → 两格都没了，掉 1 个箱子 + 16 支箭；27 个拼块都在；生存挖掘同上；
  - 离线预览看过样子（`m4_sheets/chest_v2b.png`：关着、打开、箱内、床尾），不是游戏截图；
  - 没测（要客户端看）：游戏里的样子和光照、提示、准星对着高出本格的盖子、开关声、Jade、旧存档里的箱子。
- 2026-10-15（同日，Furniture Mesh V1：改成动画网格，游戏关着；开着的是 `D:\Games\MC TEST` 的另一个实例）：
  - V1 / V2 生成器写出后 `--check`：CHECK OK（99 / 87 个输出）；`compileJava compileDevJava`（`-PaflWithoutShaders`）通过；
  - `FurnitureV2GameTests` **PASS**：开合、界面、搜索、拆和掉落、生存挖掘同上；床、工具架的拼块都在，四件动画网格的 profile、geo、sidecar、图集都在 jar 里，主格方块实体都是 `AflAnimatedMeshHost`（共 20 个文件）；
  - `ForestHomeM2GameTests` **PASS**；`ForestHomeM4GameTests` **PASS**（2/2）；
  - 没测（要客户端看）：动画是不是平滑、门 / 抽屉 / 盖子转的方向和铰链位置、静止时区块画和动的时候渲染器画之间的交接、物品图标的视角、指示灯发光、和原来拼块比的光照差别（面法线）、准星按动画网格选中。
- 2026-10-15（同日，Furniture Mesh V1 连同柴炉门、收音机摇柄）：用户 2026-10-15 实机 **PASS**（"先这样吧，PASS"），没有逐项报告，上面没测的各项没有单独确认。
