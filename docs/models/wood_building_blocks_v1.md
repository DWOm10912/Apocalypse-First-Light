# Wood Building Blocks V1（木材建筑方块：木板、圆角方木、木梁）

日期：2026-10-12。

## 状态

| 项目 | 结果 |
|---|---|
| 代码和资源 | 完成 |
| 生成器 `node tools/build-wood-blocks-v1.mjs --check` | **PASS**（`CHECK OK (18 blocks)`：资源是最新的，挖掘标签、噪音标签、重量、两种语言的名字都齐，没有等级标签，不在原版 planks / logs 标签里） |
| `node tools/mesh-trees/check.mjs` | **PASS**（圆角方木的棱柱和六种原木一起检查：闭合、朝外、剔除面在面上、端面面积；`WoodModels` 记为客户端类） |
| Java 编译 | **PASS**：`compileJava --offline` 一次通过，编译前确认游戏已关闭 |
| 实机：外观 | 用户 2026-10-12 实机（Sundial）搭了圆角方木 + 竖木梁 + 木板地面，截图两张："材质还可以……虽然看起来有点高清，也不是所有人都能喜欢，但是我觉得还行，模拟现实的代价" |
| 实机验收 | 用户 2026-10-12 实机 **PASS**（整轮验收：木材建筑方块 V1、砍树耗耐力、倒树砸伤、灰化土 / 泥 / 黏土平滑）；用户没有逐项列出结果 |
| 光影下的样子、性能 | 未测 |

本轮没有执行 build、processResources、runClient、runServer、GameTest。

## 为什么做

- 木材体系规划（[wood_system_plan_v1](../dev/wood_system_plan_v1.md)）的第一批建筑方块。六种树（[Mesh Trees P4](../rendering/mesh_trees_p4_formal.md)）验收后开始。
- 用户 2026-10-12 批准的规格（要点）：
  - 六个树种全部制作，共 18 种：木板、圆角方木、木梁；
  - 木梁截面 8 × 8 像素，长 1 格，X / Y / Z 三轴，居中；选框和碰撞要和实际形状一致；
  - 圆角方木是去皮、刨平的加工木材：纵向是自然木纹，两端是年轮，要和带树皮的原木明显区分；
  - 优先复用六种树已有的颜色、贴图脚本和木材纹理；
  - 六个树种共用方块逻辑和几何模板，不为每个树种复制 Java；
  - 接入创造栏、本地化、Jade、重量、斧头挖掘规则和正常掉落；
  - 方块模型和状态要能给以后的建筑模板用。
- **不做**（用户明确排除）：
  - 制作、锯切、加工配方；
  - 锯材等中间物品；
  - 楼梯、台阶、围栏、工匠台；
  - 改六种树的砍伐、倒树、世界生成和原木逻辑；
  - 为这些方块提前迁移 Vextris，或重构通用 Mesh 碰撞。
- 贴图密度用户选了"240 像素 + LabPBR"。

## 方块（18 种）

| 树种 | 木板 | 圆角方木 | 木梁 |
|---|---|---|---|
| 白栎 | `apocalypse_firstlight:white_oak_planks` 白栎木板 | `apocalypse_firstlight:white_oak_rounded_timber` 白栎圆角方木 | `apocalypse_firstlight:white_oak_beam` 白栎木梁 |
| 白松 | `apocalypse_firstlight:white_pine_planks` 白松木板 | `apocalypse_firstlight:white_pine_rounded_timber` 白松圆角方木 | `apocalypse_firstlight:white_pine_beam` 白松木梁 |
| 北方红栎 | `apocalypse_firstlight:red_oak_planks` 北方红栎木板 | `apocalypse_firstlight:red_oak_rounded_timber` 北方红栎圆角方木 | `apocalypse_firstlight:red_oak_beam` 北方红栎木梁 |
| 裂皮山核桃 | `apocalypse_firstlight:shagbark_hickory_planks` 裂皮山核桃木板 | `apocalypse_firstlight:shagbark_hickory_rounded_timber` 裂皮山核桃圆角方木 | `apocalypse_firstlight:shagbark_hickory_beam` 裂皮山核桃木梁 |
| 红枫 | `apocalypse_firstlight:red_maple_planks` 红枫木板 | `apocalypse_firstlight:red_maple_rounded_timber` 红枫圆角方木 | `apocalypse_firstlight:red_maple_beam` 红枫木梁 |
| 加拿大铁杉 | `apocalypse_firstlight:eastern_hemlock_planks` 加拿大铁杉木板 | `apocalypse_firstlight:eastern_hemlock_rounded_timber` 加拿大铁杉圆角方木 | `apocalypse_firstlight:eastern_hemlock_beam` 加拿大铁杉木梁 |

英文名：`White Oak Planks`、`White Oak Rounded Timber`、`White Oak Beam`，其余树种同理。

## 方块逻辑（`tree/WoodBlocks.java`）

- **一类一种，六个树种共用**：
  - `Planks`、`RoundedTimber`、`Beam` 三个类；
  - 注册时按树种循环（`RegisterEvent`，MOD 总线），ID 是 `<树种>_planks`、`_rounded_timber`、`_beam`。
- **共同属性**（原版木头的值）：
  - 硬度 2.0；抗性：木板 3.0（同原版木板），方木和木梁 2.0（同原版原木）；
  - 木头音效、低音音符盒乐器、岩浆能点燃；
  - 地图颜色取该树种原木端面的颜色（`TreeLogBlock.endColor`）。
- **燃烧**：木板引燃 5、烧毁 20（同原版木板）；方木和木梁 5 / 5（同原版原木）。
- **木板**：普通满格方块。
- **圆角方木**（`RotatedPillarBlock`，状态 `axis`）：
  - 碰撞、选框、支撑都是满格，可以堆、站、挂火把和门；完全挡光，和原木一样；
  - 遮挡形状是去掉圆角的十字：每个侧面中间的平面会和邻格互相剔除，圆角那一条不剔除（和 `TreeLogBlock` 同一做法）。
- **木梁**（`RotatedPillarBlock` + `SimpleWaterloggedBlock`，状态 `axis`、`waterlogged`）：
  - 截面 8 × 8 / 16，居中（4..12），沿轴向占满 1 格；
  - 碰撞和选框就是梁本身：竖梁旁边可以走过，横梁上可以站；
  - 光从旁边透过；能放进水里（同原版锁链）；
  - 不算可寻路的地面（`isPathfindable` 返回 false）；
  - 梁的两端会和相邻梁的端头、满格方块互相剔除。
- **给建筑模板用**：
  - 状态只有 `axis`（木梁另有 `waterlogged`）；
  - 结构旋转和镜像走原版 `RotatedPillarBlock` 的规则；
  - NBT 模板里写 Registry ID 加 `axis` 就够了。

## 外观

- **木板**：`minecraft:block/cube_all` 的 JSON 模型。
- **圆角方木**：和原木同一个圆角方形棱柱。
  - `TreeLogModel` 新增一个构造器，参数是半径和侧面 / 端面贴图；
  - `LogShape.build(float r)`：原来的 `build(Species)` 改成调它，原木的输出不变；
  - 圆角半径 `WoodBlocks.TIMBER_RADIUS` = 1.5/16，六个树种相同，比任何原木（2.5/16–6/16）都方；
  - `tree/WoodModels.java`（客户端，`ModelEvent.ModifyBakingResult`）把所有方块状态和物品模型都换成这个棱柱；
  - JSON 模型只提供粒子贴图和物品显示变换（父模型 `minecraft:block/block`）；
  - 四边形缓存的键是"6 + 树种序号"，和原木（0..5）分开。`TreeModels` 每次烘焙都清空这些缓存。
- **木梁**：JSON 单元素 4..12。
  - 侧面用方木侧面贴图中间那一半（UV [4, 0, 12, 16]），端面用年轮中心（UV [4, 4, 12, 12]）；
  - 方块状态：`axis=y` 原样，`axis=z` 绕 x 转 90°，`axis=x` 绕 x 转 90° 再绕 y 转 90°。
- **Blockbench**：三种几何都是程序生成的简单形状（满格、棱柱、单个盒子），生成器就是可编辑源，没有 `.bbmodel`（和外墙砖同一做法）。

## 贴图：AFL 风格

- **生成器**：`tools/build-wood-blocks-v1.mjs`；240 像素的公共部分（周期噪声、周期板块、超采样光栅化和 LabPBR 输出）2026-10-12 抽到 `tools/mesh-trees/hd-art.mjs`，贴图逐字节不变。
  - 每格 240 × 240 texel，LabPBR `_s` / `_n`，没有逐像素噪点；
  - 颜色和年轮参数取树木自己的调色板（`tools/mesh-trees/felling-art.mjs`、`species-art.mjs`），所以木板、方木和它出自的原木看起来是同一种木头；
  - 年轮间距 = 原木端面的间距 × 3.6，约 7–10 texel。
- **2026-10-20 起三种贴图（木板、方木侧面、方木端面）都改成 CC0 实拍**，由 `tools/build-wood-photo-v1.mjs` 生成，`build-wood-blocks-v1.mjs` 只写模型、方块状态和掉落表（[木材贴图重做 V1](wood_texture_rework_v1_plan.md) 第 4.1、4.2 节）。下面"风格"和程序画法是 2026-10-12 的记录，已不再生成。
- **风格**（用户 2026-10-12："我们可以大胆做自己的风格"；**已被实拍替换**）：
  - 和外墙砖、商业木门同一档：干净、克制，不是木头的照片；
  - 画面主要靠排布：板缝、错开的端头接缝，读法和原版木板一样；
  - 木纹安静：直纹、低对比、年轮宽度有变化，不画山形大花纹、节疤、色斑；
  - 板和板之间差一个小色阶，像砖；
  - 树种靠颜色、年轮间距和端面区分。
  - **为什么改**：第一版按真实的平切山形纹、心材和边材混搭来画。预览里像照片，还有大色块，放在 AFL 其他 240 像素建筑旁边不统一，所以改成现在的样子。
- **木板**（`<树种>_planks`）：
  - **2026-10-20 起改成实拍**（用户："现在各类木板没有换贴图"）：
    - 排布不变：每格 4 块板（一块 25 cm），每块板一个接缝，相邻两排的接缝错开；
    - 每块板从该树种的实拍刨光木料（和方木侧面同一张）里截一条，位置各不相同，木纹顺着板长；木纹每米斜出 5–14 texel，所以接缝两边的纹路断开；
    - 每块板自己的明暗（0.88–1.10）和冷暖（只往暖偏 0–5 %，偏冷会发青）；松木照片里有青色污渍，取色时把绿限制在棕黄范围内；
    - 板缝细：1.2 texel 只比木头暗一些（×0.72），加 2.6 texel 软倒角，法线里有一点起伏，不再是深色粗线；
    - `_s` 光滑度 108（板缝 60），孔隙 30；还没进游戏看过。
  - 下面两条是原来的程序画法，已不再生成：
  - 每块板有自己的色阶（±4.5%）和心材 / 边材比例；
  - 板缝是 1.6 texel 的深色槽，加 3.2 texel 倒角，有法线、AO、高度；
  - 现实里的橡木地板多为 6–13 cm 宽。这里用 4 块是为了和原版木板读法一致、远看不碎。以后可以另做窄条地板。
- **方木侧面**（`<树种>_timber_side`，木梁侧面也用它）：
  - **2026-10-20 起改成 CC0 实拍木料**（`tools/build-wood-photo-v1.mjs`，[木材贴图重做 V1](wood_texture_rework_v1_plan.md) 第 4 节）：
    - 每个树种用一张实拍刨光单板，铺满 1 m，竖纹，调成该树种锯材的颜色；
    - 东部铁杉和北美白松共用松木照片，只是截取的位置和颜色不同；
    - `_s` / `_n` 和原来一样；还没进游戏看过；
  - 下面两条是原来的程序直纹，已不再生成：
  - 直纹顺着长度方向，用该树种锯材的颜色；
  - 不画干裂：四个侧面共用一张贴图，裂缝会在四面重复。
- **方木端面**（`<树种>_timber_end`，木梁端面用中间部分）：
  - **2026-10-20 起改成实拍**：从该树种的原木截面照片（ambientCG `TreeEnd002` / `TreeEnd003`，和原木端面同一张）里，以髓心为中心截一个正方形，四个圆角在木头最窄半径的 0.84 以内，不带树皮；颜色和原木端面一样；平法线，`_s` 光滑度 72、孔隙 50；还没进游戏看过。
  - 下面三条是原来的程序画法，已不再生成：
  - 年轮绕着中心的髓心，边材在四角；有径向干裂；
  - 两种栎树有木射线，白松有树脂点；
  - 这是方木和带树皮原木一眼就能分开的地方。
- **各树种锯材的边材比例**（0 全是心材，1 全是边材）：

  | 树种 | 比例 | 板间差异 |
  |---|---|---|
  | 白栎 | 0.15 | ±0.12 |
  | 白松 | 0.55 | ±0.2 |
  | 北方红栎 | 0.1 | ±0.1 |
  | 裂皮山核桃 | 0.35 | ±0.3（心材、边材混用，是山核桃的特点） |
  | 红枫 | 0.85 | ±0.12（卖的是浅色边材） |
  | 加拿大铁杉 | 0.5 | ±0.15 |

- **PBR**：
  - `_s`：R 光滑度（刨光面 108–112、端面 72、缝 55），G = 10（F0，非金属），B 孔隙度（30 / 50）；
  - `_n`：DirectX 法线，B 是 AO，A 是高度；只有板缝、倒角和干裂有起伏，年轮只是颜色（掠射光下会变成硬线）。

## 挖掘与掉落（AGENTS 方块审计）

- 木头按原版规则：
  - `minecraft:mineable/axe`；
  - 不加 `needs_*_tool` 等级标签；
  - 不要求正确工具（徒手也能挖，也会掉，只是慢）。
- 18 种都写进 `data/minecraft/tags/blocks/mineable/axe.json`，原有的 `commercial_wood_door` 保留。
- 掉落表：`data/apocalypse_firstlight/loot_tables/blocks/<id>.json`，每种掉自身，带 `survives_explosion`。
- **不进** `#minecraft:planks` / `#minecraft:logs`：进了就会触发原版配方（木棍、工作台等），这一轮不要任何配方。生成器 `--check` 会检查。
- 没有台阶、楼梯等变体，所以不存在同步问题。
- 生存模式挖掘和掉落**还没在实机检查**。

## 其他接入

- **创造栏**：AFL 建筑方块，放在钢栏杆后面，按树种排：木板、圆角方木、木梁。
- **本地化**：`en_us` / `zh_cn` 的树木分区，加在倒树实体后面；两个文件都是 851 行，逐行对齐。
- **重量**（`data/apocalypse_firstlight/item_mass/afl_content_v1.json`，估计值）：
  - 木板 0.1 kg：同原版木板；
  - 圆角方木 0.35 kg：原木 0.4 kg 去掉树皮和圆角；
  - 木梁 0.1 kg：大约是方木四分之一的截面。
- **噪音**：18 种逐个写进 `noise_wood_blocks`，因为它们不在原版的 planks / logs 标签里。打碎时按木头算，半径 6。
- **Jade**：每种都是独立方块，Jade 默认的名字和图标就是对的，没有加专门的提示。
- **感染者破坏、建筑遮蔽**：不在 `infected_breakable` 里（和原版木板一样）；不在 `not_building_cover` 里，所以算建筑遮蔽。

## 文件

- **新增**：
  - `src/main/java/com/antaurora/apofirstlight/tree/WoodBlocks.java`
  - `src/main/java/com/antaurora/apofirstlight/tree/WoodModels.java`
  - `tools/build-wood-blocks-v1.mjs`
  - 生成器写出的 126 个文件：
    - `assets/apocalypse_firstlight/textures/block/wood/`：54 张贴图（18 张基础贴图，各带 `_s`、`_n`）；
    - `models/block/wood/`：18 个；`models/item/`：18 个；`blockstates/`：18 个；
    - `data/apocalypse_firstlight/loot_tables/blocks/`：18 个。
- **修改**：
  - `tree/TreeLogModel.java`（第二个构造器，按 id / 半径 / 贴图缓存）；
  - `tree/LogShape.java`（`build(float r)`）；
  - `tree/TreeLogBlock.java`（`endColor` 改为包内可见）；
  - `data/minecraft/tags/blocks/mineable/axe.json`；
  - `data/apocalypse_firstlight/tags/blocks/noise_wood_blocks.json`；
  - `data/apocalypse_firstlight/item_mass/afl_content_v1.json`；
  - `lang/en_us.json`、`lang/zh_cn.json`；
  - `tools/mesh-trees/check.mjs`（`WoodModels` 记为客户端类）；
  - `tools/mesh-trees/FellCheck.java`（棱柱检查加上圆角方木半径）。

## 创造模式测试方法

- **拿方块**：
  - 创造栏 → AFL 建筑方块 → 钢栏杆后面那 18 格；
  - 或者用命令，例如：
    ```
    /give @s apocalypse_firstlight:white_oak_rounded_timber 64
    ```
- **要看的**：
  1. 对着不同的面放方木和木梁，X / Y / Z 三个方向都对；
  2. 木梁：选框只框住梁；能站在横梁上；能贴着竖梁走过去；放进水里会含水；
  3. 圆角方木和原木放在一起，能一眼分清（没有树皮，端面是方的、有年轮）；
  4. 几块圆角方木并排，平的侧面之间没有缝和闪烁，圆角形成 V 形槽；
  5. 木板铺成墙和地，看接缝错开、远看不碎；开、关光影各看一次；
  6. 生存模式：用斧头挖、徒手挖，都掉自身；
  7. 两种语言下的名字，Jade 显示的名字和图标。

## 以后

- 配方、锯材、工匠台（规划第四节）；
- 台阶、楼梯、围栏，以及窄条地板；
- 六种树自己的贴图（原木、树皮）提高到 240 像素：2026-10-12 试过，用户实机不通过（法线太强，画法和像素树叶不统一）。定下的划分：树和原料（树皮、原木端面、树桩截面、砍口）用 32 像素像素画，加工品（本文的木板、圆角方木、木梁，以后的去皮原木、台阶、楼梯、门）保持 240，见 [mesh_trees_p4_formal W 节](../rendering/mesh_trees_p4_formal.md)。
- 去皮原木 `<树种>_debarked_log`（木屋用的加工品，240 像素），和台阶、楼梯、门放在下一批。
- **低像素材质包**（用户 2026-10-12 提出，以后如果很多玩家不喜欢高清材质再做）：
  - 做法：模组内置一个可选资源包（默认关闭，在资源包菜单里打开），按同样的路径覆盖 240 像素贴图；几何不变（圆角、木梁截面照旧）。
  - 不能直接把 240 缩到 16：1.6 texel 的板缝会消失，年轮会变成噪点。平铺材质要按像素风重新画一遍；按材质族给生成器加像素模式，六个树种共用。
  - 同时要给出同尺寸的 `_n` / `_s`，不要让 16 像素底图配上 240 像素的法线和高光。
  - 范围是所有 240 像素的人造材质（外墙砖、地面、木材等），不只木材；网格设备的贴图集可以直接缩小。
