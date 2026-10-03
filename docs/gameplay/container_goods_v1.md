# Container Goods V1（容器货物显示）

状态（2026-10-01）：**已实现**。货架、饮料冷柜、售货机这一轮（商品库）**用户实机 PASS**；冰柜和储物柜的货物没有单独的 PASS 记录。以后：等饮料、各种小物件的 Mesh 模型做多了，再回来换掉或扩充商品库（用户 2026-10-01：先保持现在这样）。`compileJava --offline` PASS（main 源集包含 `src/dev`）。已接入：
- 冷冻冰柜（[chest_freezer_v2.md](../models/chest_freezer_v2.md)）、工业储物柜（[industrial_locker_v2.md](../models/industrial_locker_v2.md)）、金属垃圾桶（[metal_trash_can_v2.md](../models/metal_trash_can_v2.md)，2026-10-02，三个黑色垃圾袋，没有实机验证）：各自生成器里的专用货物模型；
- 货架（[retail_shelf_v3.md](../models/retail_shelf_v3.md)）、饮料冷柜（[beverage_cooler_v2.md](../models/beverage_cooler_v2.md)）、自动售货机（[vending_machine_v2.md](../models/vending_machine_v2.md)）：共用的商品库，见"商品库"。这三个原来是实时摆放真实物品，2026-10-01 用户决定改成搜索 + 通用货物（以后的武器架仍实时显示）。

## 是什么

可搜索的容器（[progressive_container_search_v1.md](progressive_container_search_v1.md)）里面画一些通用的货物模型，让柜子看起来有东西。这些货物不是真实物品，看不出具体装的是什么：纸箱、袋子、衣服、安全帽之类。用户 2026-10-01 定下的规则：
- **有货才显示**，空了就不显示；
- **每个容器随机**：先填哪几处、每处摆什么，按容器的位置随机，同一个容器在所有客户端、重进存档后都一样；
- **东西多就摆得多**：显示几处跟着容器里有几格东西走，没搜过的格子也算。所以能看出满不满，看不出装的是什么。这和搜索系统"不泄露内容"的原则有出入，是用户要求的；
- **货物跟着地方走**（储物柜）：学校和工厂的柜子摆的东西不同，由战利品表决定主题，见下文。

## 共用规则

- 显示几处：
  - 冷冻冰柜、储物柜：有东西的格数 × 2 / 3，向上取整，不超过这个容器的位置数（`AflContainerGoods.shown(occupied, spots)`）。1 格 → 1，3 格 → 2，6 格 → 4，9 格 → 6，12 格 → 8。
  - 货架、饮料冷柜、售货机、垃圾桶：按比例，"装满几格就全摆满"由容器定（`AflContainerGoods.shown(occupied, spots, fullAt)`，向上取整，有东西时至少 1 处）：垃圾桶 3 个袋子 / 6 格满（从桶底往上叠，不随机），货架 15 处 / 6 格满，饮料冷柜 30 处 / 12 格满，售货机 12 处 / 6 格满。
- 拿走东西时，最后填上的那处先消失。
- 客户端只收到"显示几处"和主题名，物品本身不同步。
- 战利品要先生成才知道有几格，所以这些容器会提前生成战利品（只是生成，格子仍要搜才能看到）：冷冻冰柜、货架、饮料冷柜、售货机在第一次服务端 tick（从外面看得到里面），垃圾桶在第一次开盖时，储物柜在第一次开门时。
- 货物模型的骨骼都标 `neverRender`，物品图标里不出现；在世界里由方块实体的 `meshPartVisible` 决定显示哪些。

## 主题（goods themes）

- 数据：`data/<命名空间>/goods_themes/<主题>.json`，内容 `{"loot_tables": [...]}`，写完整的战利品表 ID；以 `/` 结尾的表示这个文件夹下的所有表。匹配最长的那条生效。主题名就是文件名（不看命名空间）。
- 没有匹配、或者没有战利品表（玩家放置的）：`generic`（通用）。
- 容器生成战利品时记下主题，存进存档，之后不再变；重载数据不会改已经记下的主题。
- 代码：`containersearch/AflGoodsThemes`（服务端数据，`/reload` 时重新读取）。
- 现在的两个主题文件（项目里还没有这些战利品表，等建筑战利品表做出来时按这个文件夹命名就会生效）：

| 主题 | 匹配的战利品表 |
|---|---|
| `industrial` | `apocalypse_firstlight:chests/industrial/`、`apocalypse_firstlight:chests/factory/`、`apocalypse_firstlight:chests/warehouse/` 下的所有表 |
| `school` | `apocalypse_firstlight:chests/school/` 下的所有表 |
| `grocery` | `apocalypse_firstlight:chests/supermarket/`、`chests/grocery/`、`chests/store/` 下的所有表 |
| `pharmacy` | `apocalypse_firstlight:chests/pharmacy/`、`chests/hospital/` 下的所有表 |
| `hardware` | `apocalypse_firstlight:chests/hardware/` 下的所有表 |

- 每种容器自己决定每个主题下每种位置能摆什么；不认识的主题按 `generic` 处理。冷冻冰柜、饮料冷柜、售货机目前不分主题；货架认 `grocery`、`pharmacy`、`hardware`、`industrial`（同五金）。

## 道具模型

共用模块 `tools/afl-goods-props.mjs`：用盒子、圆柱、旋转体在局部坐标里拼出道具，再旋转、摆到位置上。不印字、不画图案；纸箱的黄胶带、反光背心的反光条是凸起的几何条，不是画上去的。材质 `PROP_MATS`（纯色，最亮约 190，避免光影下发白）。

| 道具 | 构成 |
|---|---|
| 纸箱 `carton` | 牛皮纸色纸箱，黄胶带沿顶部接缝并包过两端；可叠 2 层，越往上越小、略微转角 |
| 帆布旅行袋 `holdall` | 大倒角的软袋，两条深色带子绕一圈 |
| 帆布包 `tote`（挂钩） | 挂在挂钩上，两根带子越过挂钩 |
| 工具箱 `toolbox` | 红色箱体、深一点的盖子、两根立柱上的黑色提手 |
| 劳保靴 `boots` | 一双：橡胶底、圆角鞋面、靴筒 |
| 安全帽 `hard_hat` | 旋转体帽壳加帽檐（黄 / 白 / 橙）；挂在挂钩上时帽顶朝外 |
| 保温杯 + 饭盒 `thermos` | 带深色盖的圆柱保温杯，旁边一个带提手的饭盒 |
| 工作夹克 `jacket` | 衣架挂在挂衣杆上：V 领、袖子垂在两侧 |
| 反光背心 `vest` | 衣架挂着：橙色，前面两条反光条 |
| 劳保手套 `gloves` | 一双，袖口挂在挂钩上 |
| 书包 `backpack` | 圆角包身、前袋（另一种颜色）、提手；挂在挂钩上时提环越过挂钩 |
| 课本 `books` | 3–4 本平放叠起，封面加书脊，书页块略缩进 |
| 运动鞋 `sneakers` | 一双：白色鞋底、彩色鞋面、后跟 |
| 运动包 `sports_bag` | 躺着的圆筒包，两端深色，顶上一根提带 |
| 篮球 `basketball` | 旋转体球加两圈凸起的接缝 |

冷冻冰柜的冷冻货（纸盒、软袋、冰淇淋桶）写在它自己的生成器里（`tools/build-chest-freezer-v2.mjs` 的 `GOODS`）。

## 商品库（货架、饮料冷柜、售货机）

这三个容器的格位多（15–30 个）、尺寸相近，所以不像储物柜那样每个位置建一份骨骼，而是共用一个商品库：每种商品只建一次模，渲染器在每个显示的格位上画一遍，颜色用顶点色随机。

- 生成器：`tools/build-goods-library-v1.mjs`（`--check`、`--preview DIR`）。输出 `geo/goods_library.geo.json`、`meshes/goods_library.aflmesh.json`、`textures/block/goods_library{,_s,_n}.png`（512 atlas，13.25 texel/px），可编辑源 `src/main/blockbench/goods_library.bbmodel`。没有 profile，渲染器直接按骨骼画。
- 每种商品按标准格位建模：宽 4、高 4.6、深 8 px，正面朝 -z，原点在格位底面中心；容器按自己的格位缩放（货架 1.0、饮料冷柜 0.93、售货机 0.76：2026-10-01 售货机 V2 货道变窄，原来是 0.85），商品前沿对齐格位前沿。
- 每种商品两个骨骼：`<商品>`（固定颜色：瓶盖、金属盖、白纸、牛皮纸）和 `<商品>_tint`（浅中性色 214，按格位上色）。9 种包装色（暗红、蓝、绿、黄、橙、紫、青、白、棕，乘在 214 上，最亮约 184）。
- 为了每帧重画也不卡，面数压得很低，没有倒角：

| 商品 | 构成 | 三角面 |
|---|---|---|
| `boxes` | 三个高纸盒前后排 | 36 |
| `cans` | 2×2 易拉罐，金属顶 | 144 |
| `bottles` | 两个塑料瓶：瓶身、瓶颈、白盖 | 136 |
| `jars` | 两个罐子，金属盖 | 112 |
| `bags` | 两个零食袋（小倒角） | 56 |
| `pill_boxes` | 2×3 小药盒，中间一层白色 | 72 |
| `med_bottles` | 三个白药瓶，彩色盖 | 120 |
| `tubes` | 牙膏盒式长盒，三层两排 | 72 |
| `paint_cans` | 两个金属油漆桶，彩色标签带，盖 | 128 |
| `spray_cans` | 三个喷漆罐，白盖 | 120 |
| `part_boxes` | 2×2 牛皮纸零件盒（不上色） | 48 |
| `cartons` | 2×2 屋顶形饮料盒，白顶 | 112 |

- 材质都是电介质（F0 20）：纸 72–84、塑料 130–140、薄膜 150–156、印刷金属 150–160 光滑度；没有自发光。
- 一个摆满的货架约 1100–1500 个三角面，饮料冷柜摆满约 3700，售货机约 1500。
- 每格的商品、颜色和 ±4° 的小转角由 `containersearch/AflGoodsState` 按位置和主题算出（客户端算，服务端只同步主题和显示几格）；`client/goods/AflGoodsLibrary` 负责画。
- 每个容器能用的商品：

| 容器 | 主题 → 商品 |
|---|---|
| 货架 | `generic`：纸盒、罐头、瓶子、罐子、零食袋；`grocery`：再加饮料盒；`pharmacy`：药盒、药瓶、牙膏盒、纸盒；`hardware` / `industrial`：油漆桶、喷漆罐、零件盒、纸盒 |
| 饮料冷柜 | 易拉罐、瓶子、饮料盒 |
| 售货机 | 易拉罐、瓶子、零食袋 |

## 测试

开发指令（开发环境，OP 2 级，看着一个方块执行）：

```
/dev container_search spawn <方块> [theme <主题>] [loot <战利品表> | fill <格数>]
```

- `theme <主题>`：直接指定货物主题，自动补全已加载的主题（`generic`、`grocery`、`hardware`、`industrial`、`pharmacy`、`school`）。不写的话按战利品表决定；用 `fill` 时没有真正的战利品表，所以是 `generic`。储物柜、货架、饮料冷柜、售货机都接受（后两个不分主题，设了也一样）；冰柜会提示"忽略"。
- 例：`/dev container_search spawn apocalypse_firstlight:retail_shelf_single theme pharmacy fill 6` 刷一个药店货架、15 格全摆。
- `fill <格数>`：随机放这么多格测试食物，用来看"东西越多摆得越满"。
- 例：`/dev container_search spawn apocalypse_firstlight:industrial_locker theme school fill 12` 刷一个学校主题、装 12 格、需要搜索的储物柜（12 格 → 全部 7 处都摆）。
- 详见搜索文档第 21 节。

## 代码

| 文件 | 内容 |
|---|---|
| `containersearch/AflContainerGoods.java` | 显示几处的规则（两种）、数格子、`Themed` 接口（开发指令直接设主题用） |
| `containersearch/AflGoodsState.java` | 商品库容器的主题、显示几格、同步、按位置排好的格位 |
| `client/goods/AflGoodsLibrary.java` | 画商品库 |
| `tools/build-goods-library-v1.mjs` | 商品库模型 |
| `containersearch/AflGoodsThemes.java` | 主题数据的读取和匹配 |
| `data/apocalypse_firstlight/goods_themes/*.json` | `industrial`、`school` |
| `tools/afl-goods-props.mjs` | 道具模型 |
| `blockentity/IndustrialLockerBlockEntity.java`、`blockentity/ChestFreezerBlockEntity.java` | 各自的位置、主题表、显示 |
| `blockentity/RetailShelfSingleBlockEntity.java`、`BeverageCoolerBlockEntity.java`、`VendingMachineBlockEntity.java` 及各自的渲染器 | 商品库容器 |
