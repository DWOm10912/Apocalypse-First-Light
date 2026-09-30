# AFL 材料体系 V1（Material System V1）

状态（2026-09-30）：**已实现，未实机验证**。`compileJava --offline` 一次 PASS；配方、JEI、贴图、辐射屏蔽和挖掘掉落都没有在游戏里看过。

## 原则

- 不再给每种金属做"锭 + 板 + 存储块"三件套。旧的锭和板共用同一个形状，只是换颜色；存储块也只是换色方块，没有用途。
- 每个材料要有明确用途：要么是下一步加工的输入，要么是方块或设备的原料。还没有消费者的新中间品不注册。
- 每个新材料图标都有自己的形状，不靠颜色区分。
- 钢块、钢板、钢梁等钢结构方块不变。

## 当前材料

| Registry ID | 中文 / English | 来源 | 用途（消费者） |
|---|---|---|---|
| `steel_billet` | 钢坯 / Steel Billet | 合金炉：铁锭 + 煤炭 / 木炭 | **暂无** |
| `steel_scrap` | 钢废料 / Steel Scrap | 粉碎钢筋混凝土 | **暂无** |
| `galena` | 方铅矿 / Galena | 粉碎方铅矿矿石 | 工业熔炉 → 铅砖 |
| `lead_brick` | 铅砖 / Lead Brick | 工业熔炉：方铅矿 | 合成铅屏蔽砖 |
| `lead_shielding_bricks`（方块） | 铅屏蔽砖 / Lead Shielding Bricks | 4 × 铅砖（2×2 有序合成） | 辐射屏蔽建筑方块 |
| `pentlandite` | 镍黄铁矿 / Pentlandite | 粉碎镍黄铁矿矿石 | 化学反应器 → 电解镍 |
| `electrolytic_nickel` | 电解镍 / Electrolytic Nickel | 化学反应器：镍黄铁矿 + 水 | 硬质合金刀片的粘结金属 |
| `wolframite` | 黑钨矿 / Wolframite | 粉碎黑钨矿矿石 | 化学反应器 → 氧化钨 |
| `tungsten_oxide` | 氧化钨 / Tungsten Oxide | 化学反应器：黑钨矿 + 水 | 工业熔炉 → 钨粉 |
| `tungsten_powder` | 钨粉 / Tungsten Powder | 工业熔炉：氧化钨 | 钨丝、碳化钨粉 |
| `tungsten_filament` | 钨丝 / Tungsten Filament | 工业熔炉：钨粉（烧结） | **暂无** |
| `tungsten_carbide_powder` | 碳化钨粉 / Tungsten Carbide Powder | 合金炉：钨粉 + 煤炭 / 木炭 | 硬质合金刀片 |
| `cemented_carbide_insert` | 硬质合金刀片 / Cemented Carbide Insert | 合金炉：碳化钨粉 + 电解镍 | **暂无** |
| `spodumene_concentrate` | 锂辉石精矿 / Spodumene Concentrate | 粉碎锂辉石矿石 | 化学反应器 → 碳酸锂 |
| `lithium_carbonate` | 碳酸锂 / Lithium Carbonate | 化学反应器：锂辉石精矿 + 水 | **暂无** |
| `bauxite`、`alumina`、`sphalerite`、`cassiterite`、`silver_scrap` | 铝土、氧化铝、闪锌矿、锡石、银废料 | **暂无**（对应矿石只掉落自身，没有粉碎配方） | **暂无** |
| `concrete_rubble` | 混凝土碎块 / Concrete Rubble | 粉碎钢筋混凝土 | **暂无** |
| `plastic_scrap`、`plastic_pellets` | 废塑料、塑料颗粒 | **暂无** | **暂无** |

所有 Registry ID 的命名空间都是 `apocalypse_firstlight`。标 **暂无** 的材料保留了注册，但还没接上后续配方，不能把它们当作已经有用途。

## 生产链

```text
铁锭 + 煤炭/木炭 ──合金炉 200t──> 钢坯

方铅矿矿石 ──粉碎机 200t──> 方铅矿 ×1–3 ──工业熔炉 200t──> 铅砖 ──4 块 2×2──> 铅屏蔽砖

镍黄铁矿矿石 ──粉碎机──> 镍黄铁矿 ×1–3 ──化学反应器 + 水 1000 mB 300t──> 电解镍 (+ 工业废液 1000 mB)

黑钨矿矿石 ──粉碎机──> 黑钨矿 ×1–3 ──化学反应器 + 水──> 氧化钨 ──工业熔炉 300t──> 钨粉
    钨粉 ──工业熔炉 400t（烧结）──> 钨丝
    钨粉 + 煤炭/木炭 ──合金炉 600t──> 碳化钨粉
    碳化钨粉 + 电解镍 ──合金炉 800t──> 硬质合金刀片

锂辉石矿石 ──粉碎机──> 锂辉石精矿 ×1–3 ──化学反应器 + 水 300t──> 碳酸锂 (+ 工业废液)
```

机器功率、每条配方的总能耗见 [机器.md](../项目内容/01%20-%20设计/方块/机器.md)。钨粉烧结只选了钨丝这一个产物，没有同时做"烧结钨坯"。

## 铅屏蔽砖

| 项目 | 值 |
|---|---|
| Registry ID | `apocalypse_firstlight:lead_shielding_bricks`（方块 + 方块物品） |
| 硬度 / 爆炸抗性 | 4.0 / 12.0 |
| 声音 | `SoundType.METAL` |
| 挖掘 | `requiresCorrectToolForDrops()`；`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool` |
| 掉落 | 自身 ×1（`survives_explosion`），钻石镐以下不掉落 |
| 辐射 | 在 `apocalypse_firstlight:radiation_shielding` 标签里；单层透射率 **0.15**（钢筋混凝土 0.35） |
| 噪音 | 与钢块一样不在 `noise_metal_blocks` 里，破坏噪音走默认类别 |

- 辐射：`RadiationShielding.transmission(BlockState)` 按方块返回单层透射率；环境辐射的 14 条射线采样和容器点源的单条射线都用它。标签里的其它方块仍是 0.35。规则见 [辐射系统.md](../项目内容/01%20-%20设计/辐射/辐射系统.md) 第 6 节。
- 贴图是中性铅灰，不是旧铅块的紫色。铅箱（`lead_chest`）的紫灰色本轮不改。

## 删除的内容

删除的方块（方块、方块物品、blockstate、模型、贴图、掉落表、挖掘标签、语言键全部移除）：

`aluminum_block`、`lead_block`、`zinc_block`、`tin_block`、`nickel_block`、`silver_block`、`tungsten_block`

删除的物品（模型、贴图、语言键、创造栏条目全部移除）：

| 类别 | ID |
|---|---|
| 板材（9） | `steel_sheet`、`aluminum_sheet`、`lead_sheet`、`zinc_sheet`、`tin_sheet`、`nickel_sheet`、`silver_sheet`、`tungsten_sheet`、`plastic_sheet` |
| 无用途的锭（4） | `aluminum_ingot`、`zinc_ingot`、`tin_ingot`、`silver_ingot` |

换成新 ID 的物品（旧 ID 已不存在）：

| 旧 ID | 新 ID |
|---|---|
| `steel_ingot` | `steel_billet` |
| `lead_ingot` | `lead_brick` |
| `nickel_ingot` | `electrolytic_nickel` |
| `tungsten_ingot` | `tungsten_filament` |
| `cemented_carbide_ingot` | `cemented_carbide_insert` |
| `kunzite` | `spodumene_concentrate` |
| `lead_block`（方块） | `lead_shielding_bricks`（方块） |

## 配方变更

`src/main/resources/data/apocalypse_firstlight/recipes/`：

| 变更 | 配方 |
|---|---|
| 删除 | 8 个 `*_sheet_compressing`（钢、铝、铅、锌、锡、镍、银、钨）；`steel_ingot_from_alloying`；`cemented_carbide_alloying` |
| 新增 | `steel_billet_alloying`、`cemented_carbide_insert_alloying`、`galena_ore_crushing`、`lead_brick_smelting`、`lead_shielding_bricks`、`pentlandite_ore_crushing`、`pentlandite_chemical_processing`、`spodumene_ore_crushing`、`spodumene_chemical_processing` |
| 修改 | `tungsten_powder_sintering`：输出从钨锭改为钨丝，400 ticks 不变 |
| 不变 | `wolframite_ore_crushing`、`wolframite_chemical_processing`、`tungsten_oxide_reduction`、`tungsten_carbide_powder_alloying`、`reinforced_concrete_crushing`、`simple_hearing_protection` |

压缩机现在没有任何配方（机器、GUI 和配方类型保留）。

## JEI

`compat/jei/AflJeiPlugin` 在运行时用 `RecipeManager.getAllRecipesFor(...)` 读取五种 AFL 配方类型，催化剂只注册机器方块，没有写死任何材料物品。因此删除或新增配方 JSON 就会同步到 JEI，代码不用改。压缩分类仍然注册，但没有条目。以上未在游戏里打开 JEI 确认。

## 贴图

生成器：`tools/build-material-textures-v1.mjs`（`--check` 检查 PNG 是否与生成结果一致）。全部是 16×16，用小的几何体生成：斜投影的棱柱，超采样填充，外加一圈原版风格的深色描边。没有噪点、污渍或文字。

| 贴图 | 形状 |
|---|---|
| `item/steel_billet` | 方截面长条，斜向远处；靠近观察者的一端是亮的锯切面 |
| `item/lead_brick` | 厚重的铸造砖，一端 V 形凸出、另一端 V 形凹口（互锁屏蔽砖），中性铅灰 |
| `item/electrolytic_nickel` | 几块又厚又平的方形阴极片，偏暖的银色 |
| `item/spodumene_concentrate` | 一小堆块状的浅灰绿解理碎粒，不是粉末锥堆 |
| `item/tungsten_filament` | 两根支架之间的螺旋线圈；前半圈亮、后半圈暗 |
| `item/cemented_carbide_insert` | 平放的 80° 菱形可转位刀片，深灰，中心有压紧孔 |
| `block/lead_shielding_bricks` | 错缝砌筑的铅砖，竖缝是 ">" 形的 V 口，干砌细缝；每块砖只有很小的整体明暗差 |

新物品不再共用旧的锭、板或粉末形状。旧的锭、板贴图和 7 张金属块贴图已删除。

## 外部引用迁移

- 商用玻璃双开门：`models/block|item/commercial_glass_double_door.json` 的 `particle` 从 `block/aluminum_block` 改为门自己的 `entity/commercial_glass_double_door`，并在 `assets/minecraft/atlases/blocks.json` 里把这张贴图加入方块图集。
- 铅箱：`models/block|item/lead_chest.json` 的 `particle` 从 `block/lead_block` 改为 `block/lead_chest`。
- 开发用 GameTest `TungstenProductionGameTests`：改用新 ID 和新配方 ID；删除了钨板压制的检查（没有这个配方了）；测试方法 `crusherCompressorAndAlloyCompleteTungstenRecipes` 改名为 `crusherAndAlloyFurnaceCompleteTungstenRecipes`。本轮没有运行 GameTest。
- 结构 NBT（地堡等）只用到钢块、钢格栅、钢门和钢筋混凝土，没有用到被删除的方块。

## 开发存档

旧存档里被删除的方块会在加载时变成空气，被删除的物品会从物品栏和容器里消失；换了 ID 的材料（例如钢锭 → 钢坯）不会自动转换。项目仍在开发期，不做迁移。

## 本轮没有做

矿石世界生成、蓝图、机器和家具的合成配方、电子元件、电池、枪械配方、新建筑、铅箱模型重做、新 GUI。

## 需要实机验证

1. 七个新贴图在物品栏里是否清楚，形状能不能一眼区分。
2. JEI：五个分类的配方条目、压缩分类是否因为没有配方而被隐藏。
3. 铅屏蔽砖：钻石镐挖掘掉落、铁镐不掉落；辐射 HUD 在一层铅砖后面的读数。
4. 玻璃门和铅箱的破坏粒子。
