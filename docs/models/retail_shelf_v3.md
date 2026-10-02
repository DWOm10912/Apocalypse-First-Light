# Retail Shelf V3（零售货架 V3：Mesh + 前后两排）

状态（2026-09-30）：**已实现，用户实机试过，PASS**。`compileJava --offline` 一次 PASS（包括 `src/dev` 里改过的 GameTest），GameTest 按规则没有运行。

**2026-10-01 改动（没有实机验证）**：货架不再实时摆放真实物品，改成 9 格可搜索容器，隔板上画通用货物，见"内容与货物"。下面原来的"摆放"和"交互"两节已作废并删除。

原来的已知问题（摆一堆弹药会掉到 60 帧：弹药每颗 1000–1400 个三角面，每帧重新提交）随之消失：现在画的是每格 36–144 个三角面的固定货物模型，和里面装什么无关。

V3 取代 V2 文档（原 `docs/supermarket_shelf_v2_model.md`，已删除，仍然有效的内容并入本文）。Registry ID、方块、物品、方块实体、blockstate、碰撞形状、挖掘规则都不变。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-retail-shelf-v3.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 OBJ 和贴图，`--dry` 不写文件） |
| 输入 | V2 的方块源模型 `src/main/blockbench/afl_supermarket_shelf_v2_review.bbmodel`（171 个方块，13 个组，128 px 色带贴图）。V2 源和贴图 `afl_supermarket_shelf_v2.png` 保留，作为 V3 的输入 |
| 转换 | 每个方块按它的面用到的色带（V2 贴图是 8 条纯色竖带）归到一种材质；同一组、同一材质的方块合并成带倒角的板件（`tools/cube-slab-mesh-lib.mjs`，与原生枪械同一套）。倒角 0.12 px。共面重叠检查结果为 0，不需要偏移 |
| 去掉的细节 | 价签条上 5 根 0.07 px 的假高光细条 `price_rail_glint_01..05`：它们是用几何体画出来的高光，现在由倒角边接光 |
| 成品 | **2636 个三角面**，41 个部件，三角形和凸四边形（生成器会检查，没有 n 边形） |
| 运行时模型 | `models/block/retail_shelf_single.json`：`forge:obj` 加载 `retail_shelf_single.obj` / `.mtl`（方块单位，`flip_v: true`，`shade_quads: true`，`automatic_culling: false`，`ambientocclusion: false`）。display 和 V2 完全相同（含自定义的 `on_shelf`） |
| 渲染路径 | 烘焙的静态 OBJ（静态道具规则：进区块网格，没有每帧开销）。方块实体渲染器只画摆放的物品 |
| 贴图 | `textures/block/retail_shelf_single{,_s,_n}.png`，512 atlas，4 texel/px，1292 个 UV 岛。取代了 V2 的 128 色带贴图（同名文件） |
| 可编辑源 | `src/main/blockbench/retail_shelf_single.bbmodel`（Free Model，单组 `shelf`，内嵌 Base 贴图）和 `src/main/blockbench/textures/retail_shelf_single{,_s,_n}.png` |
| 上半格 | 不变：`retail_shelf_single_empty`（只有粒子贴图） |
| 物品模型 | 不变：父模型是方块模型，GUI 平移 `[-0.75,-2.9,0]` |

外形、尺寸、5 层隔板高度和白色塑料的样子都和 V2 一样。坐标：源模型 X/Z 居中，Y 0..32，正面朝 -Z；OBJ 用下半格的方块单位（x = px/16 + 0.5，y = px/16，z = px/16 + 0.5）。范围：X -7.59..7.59，Y 0..32，Z -2.096..8 px。

### 材质（LabPBR）

全部是塑料或喷涂面：电介质，F0 = 20，缎面光泽（smoothness 92–136）。Base Color 就是 V2 对应色带的颜色。`_n` 是平法线，B 通道存 AO。

| 材质 | V2 色带 | Base Color | smoothness |
|---|---|---|---|
| `plastic` | 暖白主体 | [210,208,199] | 118–132 |
| `trim` | 亮边、价签条 | [224,222,213] | 122–136 |
| `mid` | 侧板、背板 | [188,189,182] | 112–126 |
| `deck` | 隔板 | [165,170,167] | 108–124 |
| `label` | 标签 | [199,202,197] | 104–116 |
| `bluegrey` | 小件 | [150,159,159] | 104–118 |
| `dark` | 托架 | [98,108,108] | 100–116 |
| `plinth` | 底座 | [65,72,71] | 92–104 |

## 内容与货物（2026-10-01）

用户决定：货架、饮料冷柜、售货机都改用逐格搜索 + 通用货物，不再实时摆放；玩家不能再在货架上摆自己的东西展示（用户觉得没人会去开店）。

- **容器**：下半格的方块实体（`RetailShelfSingleBlockEntity`，改为继承 `RandomizableContainerBlockEntity`）9 格，3×3 界面，接入逐格搜索：世界战利品要逐格搜，每格 20 ticks（1 秒，±15%；东西摆在明面上，比翻柜子快）；玩家放置的不用搜。
- **打开**：从正面瞄准隔板（`getClickedCell` 选得到格子的地方，上下两半都算）右键，显示"搜索 / Search"或"查看 / View"（画在准星处）。不再按格子拿放。
- **货物**：15 个格位（5 层 × 3 列）各摆一种共用商品库里的商品（[container_goods_v1.md](../gameplay/container_goods_v1.md)），每格一排，站在隔板前沿、按 1:1 的标准格位大小画。显示几格 = 有东西的格数 × 15 / 6，向上取整（6 格以上全摆满）。哪几格、摆什么、什么颜色按货架位置随机。
- **主题**：`generic`（通用）/ `grocery`（超市）：纸盒、罐头、瓶子、罐子、零食袋（超市多一种饮料盒）；`pharmacy`（药店）：药盒、药瓶、牙膏盒、纸盒；`hardware`（五金）和 `industrial`：油漆桶、喷漆罐、零件盒、纸盒。主题由战利品表文件夹决定（见战利品规则）。
- **战利品在第一次服务端 tick 时生成**（货架是开放的，一开始就要能看出满不满），只是生成，格子仍要搜。
- 同步给客户端：搜完没有、主题、显示几格；不含物品。
- 拆除：已揭示的格子掉落，没搜过的战利品随货架损毁；货架本身照旧掉一次。
- 漏斗：按搜索框架的规则存取。
- 渲染：`RetailShelfSingleBlockEntityRenderer` 只画货物（货架本体仍是烘焙的静态模型）。
- 老存档：`Items` 只读入前 9 格，后面的丢弃；`LegacyOverflow` 不再读取（开发阶段，不做迁移）。

## 不变的部分

- 两格高结构，只有下半格有方块实体。
- 碰撞和选择形状不变：背板、侧边、底座、顶盖、5 层隔板和价签唇边。四个水平朝向都旋转。
- 挖掘：硬度 1.5，抗性 4，`requiresCorrectToolForDrops()`，`minecraft:mineable/pickaxe` + `minecraft:needs_iron_tool`（白色塑料零售货架，不是工业设备，不走钻石级默认）。铁镐及以上掉货架。声音 `SoundType.WOOD`（最接近硬塑料的原版声音）。
- 挖掉任意一半会清掉两半，货架掉一次；里面的东西见"内容与货物"。
- 碎屑粒子：`RetailShelfParticleExtensions`，每半格最多 16 个，用模型的粒子贴图（现在是 512 atlas）。
- `convenience_store_01.nbt`、`gas_station_01.nbt` 等结构里的货架 ID 和朝向不需要迁移。

## 开发工具

`src/dev/java/com/antaurora/apofirstlight/dev/RetailShelfIntegrationGameTests.java` 2026-10-01 改写（**没有运行**）：

- 9 格、3×3 布局，玩家放置的不用搜；
- 四个朝向各 15 个格子的选格（平视落在背板、价签条、俯视隔板前后半都选中该格）；
- 真实碰撞射线瞄准格子能选中；
- 上半格点击打开界面、手上的东西不动；
- 世界战利品：第一次 tick 生成、需要搜索、货物数量等于按格数算出的值；
- 挖掉上半或下半时，里面的东西各掉一次。
- 删掉了：30 格、前后排拿放、前排优先真值表、老 12 格 NBT 和 `LegacyOverflow` 的检查；`block/DisplayDepthRule` 已删除。

离线射线模拟（临时脚本，不在仓库里）：站在 0.9–3.5 格远、眼高 1.62 和 1.27 时，瞄准前排物品中心或隔板前半，总能选中正确的格子。瞄准低层的后排时，如果站得太近，视线会先被上一层隔板挡住，选中的是上一层。这和准星实际指着的位置一致。

## 实机确认

用户实机试过后给了 PASS（2026-09-30），唯一提出的问题就是上面的弹药帧率。以后要放到货架上的商品模型要控制面数。
