# AFL 原版生存体系重构 V0：只读审计

状态（2026-10-19）：**只读审计，未实施任何功能**。本文是 V1–V3 的设计依据，等用户拍板第 10 节的问题、批准 V1 后才动代码。

更新（2026-10-19）：V1 已在分支 `AFL-Vanilla-Rework` 实现，待实机验证，见 [vanilla_survival_rework_v1.md](vanilla_survival_rework_v1.md)。本文描述的是 V0 时的现状，以下几处 V1 已改：第 1 节的铁砧 / 附魔台拦截并进了统一清单；要塞的 `structure_set/strongholds.json` 换成空的 `has_structure/stronghold.json`；下界 / 末地拦截改读清单里的 `closed_dimensions`；两个配方进度覆盖（`advancements/recipes/decorations/anvil.json`、`enchanting_table.json`）已删除；工业熔炉的 `getResultItem` 问题已修。

- 依据：master `3a1641a3` 的代码和数据（含主工作区未提交改动），D 工作树 `AFL-survivor-v0`、C 工作树 `AFL-federal-worldgen-v1` 的未提交改动，`docs/` 现有文档。
- 本轮没有编译、没有跑 GameTest、没有进游戏。下文"代码确认"指读过代码或数据；"推断"指按 Forge 1.20.1 / 原版代码推出、没有实测；"仅文档"指只在文档里出现。
- 原版配方数量取自本机 `client-extra.jar`（1.20.1）的 `data/minecraft/recipes/`。

相关文档：[原版改动.md](../项目内容/01%20-%20设计/世界与环境/原版改动.md)（删减目标清单）、[蓝图.md](../项目内容/01%20-%20设计/制作与进度/蓝图.md)、[wood_system_plan_v1.md](../dev/wood_system_plan_v1.md)（工匠台 BOM）、[material_system_v1.md](material_system_v1.md)、[thirst_system_v1.md](thirst_system_v1.md)、[temperature_system_v1.md](temperature_system_v1.md)、[fuel_fire_v1.md](fuel_fire_v1.md)、D 分支 `docs/gameplay/survival_health_v2_design.md`（食物、医疗品草案）。

---

## 0. 结论摘要

1. **AFL 已经有三种原版删减手段，全部可复用**：
   - 数据包 `forge:false`：`data/minecraft/recipes/bucket.json`、`flint_and_steel.json`，110 个进度；
   - 配方加载阶段过滤：`world/VanillaRecipePruningEvents.java`（`RecipeManager.replaceRecipes` 去掉铁砧、附魔台）；
   - 交互拦截：`world/VanillaProgressionEvents.java`（附魔台、铁砧右键 DENY；经验清零）。
   - 获取过滤也有先例：全局战利品修改器 `remove_items`（`fluid/RemoveItemsLootModifier.java`），目前只去掉各种桶。
2. **原版注册内容不必删，也不应删。** 地形、道路、老宅、温度、饮水、砍树都直接读原版方块或物品（第 2 节），删注册会连带打断这些系统。
3. **原版 2×2 / 3×3 今天仍然是生存制造的主路径**：1174 个原版配方中只禁了 4 个；工作台、熔炉、烟熏炉、高炉、锻造台、切石机、砂轮、酿造台、织布机都能正常用。AFL 自己的制造站只有 5 台工业机器（本身没有配方，只能创造模式拿到），精密制造站是空壳方块，工匠台只在文档里。
4. **不能一次性禁掉原版基础配方。** 在 AFL 手工制造上线之前禁掉木板、木棍、工作台、熔炉，新世界会直接断链（第 3.6 节）。V1 只建立机制和清单，先禁"已有替代或本来就不该有"的部分（原版武器、盔甲、魔法、交易）；基础工具和工作站要等 V2/V3 的替代物上线再关。
5. **四个现在就存在的断点或问题**（第 9 节有完整列表）：
   - 柴炉只能用打火石或火焰弹点燃，打火石配方已关，火焰弹需要烈焰粉而下界已关。推断：**新世界生存里柴炉无法点火**。
   - 网格森林的 AFL 原木不在 `minecraft:logs` 物品标签里，也没有任何配方。推断：网格森林区域**拿不到木板、木棍和工作台**，只有少数仍长原版树的群系可以。
   - 81 个工业方块是 `needs_diamond_tool`。钢块、钢筋混凝土等 10 个方块的掉落表另外只认钻石镐、下界合金镐。"手工工具最高铁级"与现有挖掘规则直接冲突。
   - 疑似 bug：工业熔炉回退到原版熔炼时不调用 `assemble()`（`blockentity/IndustrialFurnaceBlockEntity.java:262-266`），所以有污染的脏水在工业熔炉里会变成干净的净水。
6. **推荐技术路线**（第 3 节）：以一份数据驱动的"退役清单"为唯一来源，分四层执行：
   - 配方：加载阶段只过滤 `minecraft:` 命名空间；
   - 工作站：交互拦截，方块保留；
   - 获取：战利品修改器、生物掉落、钓鱼、交易都按物品标签过滤；
   - 残留物品：不删，只按规则失效。
   - 创造模式和 OP 豁免，配置开关可以整体回退。

---

## 1. 现状：AFL 已有的原版删减（代码确认）

| 机制 | 实现 | 范围 |
|---|---|---|
| 配方 `forge:false` | `data/minecraft/recipes/bucket.json`、`flint_and_steel.json` | 只这两个 |
| 配方加载阶段过滤 | `world/VanillaRecipePruningEvents.java:25-28`：重载监听器和 `ServerStartedEvent` 里调用 `replaceRecipes`，登录时 `resetRecipes` | `minecraft:anvil`、`minecraft:enchanting_table` |
| 工作站交互拦截 | `world/VanillaProgressionEvents.java:49-57`：`RightClickBlock` 取消 | 附魔台、三种铁砧 |
| 经验 | 同一文件 21-47 行 | 获得清零、经验球不能捡、生物和方块不掉经验、登录清零（创造除外） |
| 进度 | `data/minecraft/advancements/`：112 个文件，110 个 `forge:false`；`recipes/decorations/anvil.json`、`enchanting_table.json` 去掉了奖励 | 其余 `recipes/*` 解锁进度没动；进度界面被 `client/AflAdvancementScreenEvents.java` 挡住 |
| 战利品过滤 | `data/forge/loot_modifiers/global_loot_modifiers.json` → `remove_buckets.json`（类型 `apocalypse_firstlight:remove_items`，无条件） | 空桶、水桶、岩浆桶、奶桶、细雪桶，作用于所有战利品掷骰（方块、箱子、生物、钓鱼） |
| 挤奶 | `thirst/NoMilkingEvents.java` | 牛、哞菇、山羊 |
| 原版结构 | 26 个 `tags/worldgen/biome/has_structure/*` 置空；`structure_set/strongholds.json` 频率 0 | 村庄、矿井、神殿、遗迹、沉船、要塞等；冰屋、古迹废墟靠群系过滤间接不生成 |
| 原版群系 | `world/biome/AflVanillaBiomePolicy.java` + 两个群系混入 | 只留平原、海洋、深海、沙滩、溶洞、繁茂洞穴 |
| 下界 / 末地 | `world/WorldAccessEvents.java`：取消所有传送门生成、取消玩家前往 | 全部 |
| 刷怪 | `world/WorldSpawnRules.java`：主世界自然刷怪只留僵尸；`WildlifeSpawnPolicy.java`：辐射区不刷动物 | 骷髅、苦力怕、蜘蛛、女巫等不再自然生成 |
| 刷怪房 | `forge/biome_modifier/disable_monster_rooms.json` | 全部 |
| 地面拾取 | `inventory/grid/GroundItems.java:45`：生存模式不能走过自动拾取 | 全部 |

**没有处理的原版内容（代码确认）**：
- 工作台、熔炉、高炉、烟熏炉、营火、锻造台、切石机、砂轮、酿造台、织布机、制图台、制箭台、讲台的交互和配方；
- 原版剑、盔甲、盾、弓、弩、箭、三叉戟、全部工具的配方；
- 末影箱配方（[survivor_death_succession_v0.md](survivor_death_succession_v0.md) 说"按原计划删除"，实际还在）；
- 钓鱼（可以钓到附魔书、弓、钓竿，推断）；
- 流浪商人（AFL 刷怪过滤不处理 EVENT 生成类型，推断仍会出现）；
- 僵尸自带的原版盔甲和武器（难度锁 Normal，`infected/InfectedEvents.java` 不动装备）；
- 原版食物和熔炉烹饪；
- 原版红石获取。

---

## 2. AFL 现有系统对原版内容的依赖清单

按"禁用后会坏什么"整理。✔ = 代码确认；◇ = 推断；□ = 仅文档。

### 2.1 制造与配方

| AFL 系统 | 依赖的原版内容 | 位置 |
|---|---|---|
| 净水 | 原版**熔炉、烟熏炉、营火**的配方类型 SMELTING / SMOKING / CAMPFIRE_COOKING。`WaterBoilingRecipes` 是这三个原版类的子类，只换了序列化器和 `assemble()` | ✔ `thirst/WaterBoilingRecipes.java`、`registry/AflRecipes.java:33-42`、`recipes/purified_water_bottle_from_*.json` |
| 柴炉烹饪 | 原版 **SMOKING** 配方类型：凡是有烟熏配方的东西都能放上去，时间 2 倍 | ✔ `cabin/BoxStove.java:111-114`、`BoxStoveBlockEntity.java:133` |
| 工业熔炉 | 先找 AFL `industrial_smelting`，找不到再回退到原版 **BLASTING**、**SMELTING** | ✔ `blockentity/IndustrialFurnaceBlockEntity.java:242-260` |
| 钢坯 | `minecraft:iron_ingot` + 煤 / 木炭（`tags/items/steelmaking_carbon.json`） | ✔ `recipes/steel_billet_alloying.json` |
| 碳化钨粉 | 煤 / 木炭 | ✔ `recipes/tungsten_carbide_powder_alloying.json` |
| 简易护耳 | 铁锭、皮革 ×2、`#minecraft:wool` ×2，原版 3×3 有序配方 | ✔ `recipes/simple_hearing_protection.json` |
| 体温计 / 腕表温度计 | 玻璃板、红染料 / 皮革、玻璃板、铜锭，原版有序配方 | ✔ `recipes/clinical_thermometer.json`、`wrist_thermometer.json` |
| 路缘石 | 草方块，原版无序配方 | ✔ `recipes/curb_grass.json` |
| 铅屏蔽砖 | 原版 2×2 有序配方（输入是 AFL 铅砖） | ✔ `recipes/lead_shielding_bricks.json` |
| 化学反应器 | 流体 `minecraft:water` | ✔ 三个 `*_chemical_processing.json` |
| JEI | 从客户端 `RecipeManager` 读配方；工业熔炉也注册为 SMELTING / BLASTING 催化剂；柴炉没有注册为 SMOKING 催化剂 | ✔ `compat/jei/AflJeiPlugin.java:68-98` |
| 负重 | 2×2 合成格和 3×3 `CraftingMenu` 里的材料计入负重 | ✔ `weight/PlayerMassSources.java:43-45` |
| 配方书 | 配方书归还材料时，可用空间按网格空格计算 | ✔ `mixin/LockedInventoryRecipeMixin.java` |

### 2.2 工具与挖掘

| AFL 系统 | 依赖 | 位置 |
|---|---|---|
| 砍树 | 必须 `canPerformAction(ToolActions.AXE_DIG)`；每次砍的力度取 `TieredItem.getTier().getSpeed()`（不是 TieredItem 时取它挖橡木原木的速度）和**效率附魔**等级 | ✔ `tree/FellingServer.java:133-141` |
| 工业方块掉落 | 81 个方块在 `needs_diamond_tool`。钢块（含台阶、楼梯）、钢格栅、钢板（含台阶、楼梯）、钢筋混凝土（含台阶、楼梯）10 个方块的掉落表另外要求 `industrial_material_recovery_tools`，这个标签只有钻石镐和下界合金镐；钢门在代码里同样判断 | ✔ `tags/blocks/needs_diamond_tool.json`、`tags/items/industrial_material_recovery_tools.json`、`block/SteelDoorBlock.java:40-42,73` |
| 多格方块掉落 | 约 15 个多格方块在 `playerWillDestroy` 里用 `isCorrectToolForDrops` 决定掉落 | ✔ 例如 `RectMultiblockBlock.java:216`、`FuelDispenserBlock.java:411` |
| 隧道部件、油罐人孔盖 | 只认撬棍（按物品判断，与等级无关） | ✔ `block/TunnelFixtureBlock.java:57-66,143-155`、`FuelSumpCoverBlock.java:76` |
| GameTest | 17 个开发 GameTest 写死了原版镐、斧的等级 | ✔ `src/dev/java/.../dev/`（如 `VendingMachineGameTests`、`WorkstationGameTests`） |
| 挖掘规则重做 | 用户 2026-10-14 说"AFL 以后重做挖掘规则"；在那之前新方块等级随意 | □ `docs/models/rural_driveway_v1.md:7`、`wood_furniture_v2.md:193`、`forest_start_home_b_v1_plan.md:531` |

AFL 现在**没有任何工具物品**：没有 `TieredItem` / `DiggerItem` 子类，没有自定义 `Tier`，没有 `TierSortingRegistry`。撬棍是普通 `Item`（`item/CrowbarItem.java`），不参与挖掘等级。

### 2.3 战斗与装备

| AFL 系统 | 依赖 | 位置 |
|---|---|---|
| 枪伤 | `native_bullet` 不在 `bypasses_armor` 里，所以原版护甲值、韧性、保护附魔、盾牌都会减子弹伤害 | ✔ `tags/damage_type/*`、`weapon/NativeGunShot.java:143` |
| 护甲 HUD | 原版护甲值（满 20） | ✔ `client/SurvivalVitalsHud.java:119,128`、`StatusBadgeHud.java:394` |
| 保温 | 每件原版盔甲一个保温值 | ✔ `thermal_insulation/armor_v1.json`、`temperature/ThermalInsulation.java:56-60` |
| 负重 | 穿着的盔甲按 65% 计 | ✔ `item_mass/core_v1.json`（`armor_load_factor`） |
| 背包尺寸 | 剑、锹、锄 1×3；斧、镐 2×3；盔甲按 `forge:armors/*` 标签；弓、弩、盾、三叉戟单独定义 | ✔ `item_size/core_v1.json` |
| 武器栏 | `weapons/melee.json` = 撬棍 + `#minecraft:swords` + `#minecraft:axes` | ✔ `inventory/HotbarRoles.java:29,58` |
| 背包界面 | 原版胸甲槽显示为"防弹衣" | ✔ `client/inventory/AflInventoryScreen.java:599`、lang 595 行 |
| 尸体 | 读消失诅咒 | ✔ `survivor/SurvivorCorpses.java:148,171,182` |
| 第一人称 | 检查上弦的弩 | ✔ `client/FirstPersonBody.java:145-146`（只影响渲染） |
| D：健康 V1a | 原版治疗类效果仍按 20 点生命作用，在 100 点体系下只剩约 1/5 效力 | ✔ D 工作树 `health/PlayerHealth.java`；□ `survival_health_v1a.md` §0 |

AFL **没有护甲或防护服**：装备层只有 Curios 的背包、手腕、耳朵、胸挂、枪套五个槽，都不减伤。

### 2.4 流体、饮水、烹饪、热源

| AFL 系统 | 依赖 | 位置 |
|---|---|---|
| 灌瓶 | 原版**玻璃瓶**对准原版水源方块 → AFL 脏水瓶；喝完返还玻璃瓶 | ✔ `thirst/PlayerThirst.java:135-181`、`ThirstWaterItem.java:44-60` |
| 储液罐 | `FluidUtil.interactWithFluidHandler`：任何带 `FLUID_HANDLER_ITEM` 的物品都能装取，现在实际上只有原版桶。单格 800 mB，一桶 1000 mB 只能倒进相连的罐 | ✔ `block/FluidTankBlock.java:125-133`、`blockentity/FluidTankBlockEntity.java:40-51,142-144` |
| 热力发电机 | 煤 500、木炭 500、煤块 5000、**岩浆桶** 20000 FE（返还空桶）。管道里液态岩浆的能量也是从岩浆桶这一条换算出来的：删掉岩浆桶条目，液态岩浆就不再是燃料 | ✔ `energy/MachineBalanceManager.java:595-600`、`ThermalFuelDefinitions.java:15-16,43-51` |
| 工业废液桶 | 原版 `BucketItem`，`craftRemainder(Items.BUCKET)` | ✔ `registry/AflItems.java:112-114` |
| 汽油 / 柴油源方块 | 没有桶物品（`bucket` 为空）。原版空桶对着源方块用，源方块会被拿走但只给空气 | ◇ 原版 `LiquidBlock.pickupBlock` |
| 体温热源 | 按方块 ID 精确匹配：营火、灵魂营火（点燃时）、火、岩浆、岩浆炼药锅、岩浆块、点燃的熔炉 / 烟熏炉 / 高炉（10/r4）、火把、灯笼；AFL 柴炉 22/r6 | ✔ `temperature/temperature_v1.json`、`PlayerTemperature.java:304-337` |
| 燃料泄漏点燃 | 火方块标签、点燃的营火、火把、岩浆 | ✔ `fluid/FuelSpills.java:173-180` |
| 点火 | 柴炉只认打火石、火焰弹 | ✔ `cabin/BoxStove.java:109,291-299` |
| 柴炉燃料 | `stove_fuel/{logs,wood,kindling}` 标签，包含原版 `#logs_that_burn`、`#planks` | ✔ `tags/items/stove_fuel/*` |
| 食物 | AFL 没有自己的食物，没改原版 `FoodProperties`；饮水量表里有西瓜片、苹果、汤类、蜂蜜瓶 | ✔ `thirst/thirst_v1.json` |
| D：健康 | 原版食物回血已关（`FoodDataThirstMixin` 的 `isHurt` 恒为 false） | ✔ D 工作树，未合并 |

AFL 的物品**都没有提供** `FLUID_HANDLER_ITEM`。油桶、60 L / 200 L 桶把 `FluidStack` 存在 NBT 里，只能往 `FuelCanTransfers` 认可的目标倒。方块侧 9 种方块实体提供 `FLUID_HANDLER`，管道可以接其他模组的罐。

### 2.5 世界生成

| 内容 | 依赖 | 位置 |
|---|---|---|
| 地表规则 | 石头、深板岩、基岩、草方块、泥土、砂土、灰化土、泥巴、黏土、沙、红沙、砂砾、砂岩、陶瓦、雪、冰、方解石、菌丝、水 | ✔ `minecraft/worldgen/noise_settings/overworld.json`、`apocalypse_firstlight/worldgen/noise_settings/federal_experimental.json` |
| 道路 | 自上而下：AFL 路面碰撞层、AFL 沥青、**砂砾**、**圆石**底基、**泥土**回填；草皮下是泥土；车道是砂砾 + 泥土；涵洞有砂砾、泥土、水。C 分支新增 3 格原版**石头**的路堑面 | ✔ `worldgen/network/RoadChunkPass.java:129-197,299-317`；C 工作树未提交 |
| 老宅场地 | 石头、泥土；NBT 里有泥土、草方块、砂土、土径、梯子、云杉台阶 | ✔ `worldgen/home/ForestHomeChunkPass.java:81,93`、`structures/forest_home_b.nbt` |
| 河流 | 水 | ✔ `worldgen/terrain/v2/RiverWaterFill.java:72` |
| 地形平滑 | 草方块、泥土、砂土、沙、砂砾、石头、灰化土、泥巴、黏土、雪块、红沙、陶瓦 | ✔ `terrainsmooth/TerrainSmooth.java:66-69,136-139` |
| 砍树 | 原版去皮橡木、白桦、云杉原木作替身 | ✔ `tree/FellingServer.java:216-218` |
| 原版矿石 | 每个 AFL 群系都列了煤、铁、金、红石、钻石、青金石、铜；`ore_veins_enabled: true`（大型铁、铜矿脉）；没有绿宝石 | ✔ `apocalypse_firstlight/worldgen/biome/*.json` |
| AFL 矿石 | 7 种都注册了、都是 `needs_diamond_tool`，但**没有任何世界生成** | ✔；□ `material_system_v1.md:157` |
| 原版树 | 网格森林替换了 11 个群系的树；`alpine_meadow`、`lake_fen`、繁茂洞穴杜鹃仍长原版树；关掉 `meshForestEnabled` 全部回到原版树 | ✔ `forge/biome_modifier/mesh_forest.json`、`tree/MeshForestConfig.java:21` |
| 原版工作站 | 只出现在**已退役**的 `structures/bunker.nbt`（熔炉、烟熏炉、木桶、酿造台、铁砧、织布机、床等）；老宅和加油站 NBT 里没有 | ✔ |
| 感染者破门 | 树叶、玻璃板、木门、活板门、栅栏、栅栏门 | ✔ `infected/breach/InfectedBreachRules.java:27-51` |
| 幸存者生成 | 避开树叶、原木、火、营火、岩浆块、仙人掌、细雪、浆果丛、岩浆 | ✔ `survivor/SurvivorSpawns.java:175-177` |

### 2.6 物资获取（生存玩家今天实际能拿到东西的途径）

代码确认：
- **没有任何箱子类战利品表**：`loot_tables/` 下 201 个文件全是方块自掉落。结构 NBT 里没有 `LootTable` 标签，货物主题指向的 `chests/...` 表都不存在。
- **没有开局物资**：幸存者背景、特质数据都没有物品字段。继任者空手开局，前任的东西在可搜索的尸体里。
- **没有村民和交易代码。**

所以今天的来源是：
- 挖方块、砍树；
- 原版 2×2 / 3×3 合成和熔炉；
- 23 个 AFL 配方（只有材料，没有机器）；
- 僵尸和安全区动物的原版掉落；
- 原版钓鱼；
- 前任尸体；
- 爆炸残骸（铁粒，`world/IndustrialMaterialExplosionDrops.java`）。

**AFL 机器、撬棍、枪械、绷带（D）在生存里都拿不到**，只能创造模式。

---

## 3. 技术路线：Forge 1.20.1 下安全禁用原版生存内容

### 3.1 各方法评估

| 方法 | 原理 | 优点 | 缺点 / 风险 | 误伤其他模组 | 可回退 |
|---|---|---|---|---|---|
| A. 数据包覆盖 + `forge:false` | 在 `data/minecraft/recipes/<id>.json` 放同名文件 | 精确到 ID；已有先例；不写 Java；客户端自动同步 | 1174 个原版配方要逐个出文件（需要生成器）；别的数据包覆盖同 ID 时按加载顺序互相覆盖；条件只在加载时评估，不能按存档或玩家切换；拦不住其他模组产出原版物品的配方 | 不会 | 删文件即可，需要 `/reload` |
| B. 配方加载阶段过滤 | 重载完成后 `RecipeManager.replaceRecipes(过滤后的列表)`（现有 `VanillaRecipePruningEvents`） | 规则化：按命名空间、类型、ID、输出物品标签；清单可以是数据文件；一处代码管全部 | 时序依赖：现有监听器追加在原版之后。推断 `SimpleReloadInstance` 按注册顺序串行执行 apply，所以排在 RecipeManager 后面；有 `ServerStartedEvent` 兜底；`/reload` 未实测。另一种是 Mixin `RecipeManager#apply` TAIL，更确定但多一个混入 | 只过滤 `minecraft:` 命名空间时不会 | 配置开关一关就恢复 |
| C. 工作站交互拦截 | `RightClickBlock` DENY（现有附魔台、铁砧） | 方块注册、世界生成、装饰外观全部保留；可以给提示 | 拦不住 2×2 背包合成（那是 `InventoryMenu`，不是方块）；拦不住漏斗自动化：漏斗给熔炉上料不需要右键，原版熔炉在没有玩家时照样冶炼 | 只针对原版方块，不会 | 改清单 |
| D. 输出物品使用限制 | 已存在的原版装备：去掉属性（`ItemAttributeModifierEvent`）、不能装备、不能攻击或挖掘 | 处理旧存档、创造带出、未清干净的来源 | 最容易误伤（其他模组的同类物品、AFL 自己读 `TieredItem` 的逻辑）；玩家体验差（东西在手里却没用） | 有可能 | 改清单 |
| E. 获取途径过滤 | 全局战利品修改器（扩展现有 `remove_items` 为标签驱动）、`LivingDropsEvent`、钓鱼、流浪商人 | 已有先例；只按物品标签，不碰注册 | 每多一种来源就多一个钩子；钓鱼走战利品表，可以一起处理 | 只按退役标签，不会 | 改标签 |

### 3.2 推荐：一份清单，四层执行

**唯一来源（数据驱动，放 `data/apocalypse_firstlight/`）**：
- 物品标签 `apocalypse_firstlight:retired/vanilla`，下分 `retired/weapons`、`retired/armor`、`retired/tools`、`retired/magic`、`retired/containers` 等，作为"这件原版物品不再正常获取"的清单。
- 规则文件 `vanilla_rules/recipes_v1.json`，草案：

```json
{
  "remove_namespace": "minecraft",
  "keep_ids": [],
  "keep_types": [],
  "remove_foreign_if_output_in": "apocalypse_firstlight:retired/vanilla",
  "log_foreign_outputs": true
}
```
  - `remove_foreign_if_output_in` 默认关；
  - `keep_ids` 用于分批：V1 先只移除清单里的一部分，基础配方暂时留着。
- 方块标签 `apocalypse_firstlight:retired_workstations`：交互要拦截的原版方块。
- COMMON 配置 `vanillaRetirement.enabled`（默认开，开发时可关）。COMMON 配置在数据包加载前就已读入；SERVER 配置在存档加载时才读，不能用于配方过滤。

**执行层**：
1. **配方层**（方法 B）：把 `VanillaRecipePruningEvents` 推广为读规则文件。只移除 `minecraft:` 命名空间，不碰其他模组的配方；其他模组产出退役物品的配方只写日志。补一个 GameTest：`/reload` 后仍然过滤。
2. **工作站层**（方法 C）：`retired_workstations` 标签里的方块右键 DENY，客户端、服务端两侧都取消，避免界面闪开。可以给一行短提示（例如"用不了"）。方块本身可以放置、拆除，作为世界装饰。漏斗自动化：拦截后原版熔炉没有配方可用，自然失效。
3. **获取层**（方法 E）：
   - `remove_items` 改为读 `retired/vanilla` 标签，覆盖箱子、生物、钓鱼、方块掉落；
   - 新增 `LivingEquipmentChange` / `FinalizeSpawn` 清理僵尸自带的原版装备；
   - 流浪商人在 `EntityJoinLevelEvent` 取消（先实测它会不会出现）。
4. **残留层**（方法 D，最后才用）：已经在玩家手里的原版物品**不删**，默认保持原效果。是否失效由用户决定（第 10 节问题 2）。

**豁免**：
- 创造模式和旁观模式：不受工作站拦截，JEI 和 `/give` 照常。现有 `PlayerStorageCapacity.unrestricted` 已经是这个口径。
- OP 命令不受影响。

### 3.3 不建议的做法

- 删除或替换原版注册（`MissingMappings`、注册替换）：地形和道路直接写原版方块，温度、饮水、砍树读原版方块和物品，旧存档里的原版物品会变成空气。
- 用 Mixin 改 `CraftingMenu` / `AbstractFurnaceBlockEntity` 的核心逻辑：配方层和交互层已经够用，改核心容易和 JEI、其他模组冲突。
- 一次性禁用全部 1174 个原版配方（见 3.6）。

### 3.4 客户端 / 服务端职责

- 配方过滤只在服务端做；客户端经原版配方同步包拿到过滤后的列表。推断：`/reload` 时同步包在重载完成后才发。JEI 读客户端 `RecipeManager`，所以被过滤的配方会自动从 JEI 消失，不用改插件。
- 交互拦截：服务端是权威，客户端同时取消只是为了不闪界面。
- 退役标签、规则文件都是数据包，服务端加载。客户端只在需要提示时读标签（标签本身随原版同步）。
- 专用服务器是支持目标（`mods.toml` 没有设 clientSideOnly，有 `server` 运行配置）。V1 必须在专用服务器 + 客户端上跑一遍。

### 3.5 原版配方规模（1.20.1，本机 jar 统计）

| 类型 | 数量 | 备注 |
|---|---:|---|
| crafting_shaped | 590 | |
| crafting_shapeless | 232 | |
| stonecutting | 201 | 切石机 |
| smelting | 70 | 其中含食物 |
| blasting | 24 | 工业熔炉会回退到这里 |
| smithing_trim / transform | 16 / 9 | 锻造台 |
| smoking / campfire_cooking | 9 / 9 | 柴炉读 smoking |
| crafting_special_* 等 | 14 | 含 `repairitem`（2×2 合并修复工具）、`armordye`、`tippedarrow` |
| **合计** | **1174** | 三类烧制里分类为 `food` 的共 27 个 |

工作台类成品 22 个，武器盔甲类 27 个，工具类 28 个（只计 crafting_shaped / shapeless 的成品）。

### 3.6 为什么不能一次性禁掉基础配方

今天新世界的生存路线全靠原版配方：原木 → 木板 → 木棍、工作台 → 木镐 → 石镐 → 熔炉 → 铁锭 → 铁镐 → 钻石镐。

- 禁掉这一串而没有 AFL 替代，玩家**挖不了石头以外的东西**；
- 拿不到铁锭，钢坯（AFL 唯一的钢）也断；
- 拿不到钻石镐，81 个工业方块拆不下来。

所以 V1 只移除"本来就不该有"的部分，基础链等 V2 的燧石工具、背包手工制造和基础制造台上线时一起切换。

---

## 4. 统一制造系统：数据架构建议

现有基础：
- 5 个 AFL 配方类型都是 `RecipeType.simple` + 手写 JSON，没有 datagen（`src/generated` 是空的）；
- JEI 插件按类型运行时读取；
- [蓝图.md](../项目内容/01%20-%20设计/制作与进度/蓝图.md) 要求蓝图按技术组解锁、服务端按玩家存；
- [wood_system_plan_v1.md](../dev/wood_system_plan_v1.md) §4 要求工匠台不摆格子，用材料清单显示有 / 需要、自动扣料，可批量、分类、搜索、标签替代。

### 4.1 一个制造配方类型，按"站点能力"区分

建议注册一个 `apocalypse_firstlight:fabrication`，不再给每台制造台各开一个类型。一张配方声明它需要的**站点能力**，方块声明它提供哪些能力；手工能做的东西，制造台也能做。

草案字段：

```json
{
  "type": "apocalypse_firstlight:fabrication",
  "station": "apocalypse_firstlight:hand",
  "category": "apocalypse_firstlight:tools",
  "tech_group": "apocalypse_firstlight:primitive_tools",
  "skill": { "id": "apocalypse_firstlight:crafting", "min": 0 },
  "inputs": [
    { "item": "minecraft:flint", "count": 2 },
    { "tag": "apocalypse_firstlight:branches", "count": 1 },
    { "tag": "apocalypse_firstlight:cordage", "count": 1 }
  ],
  "tools": [ { "tag": "apocalypse_firstlight:tools/knife", "damage": 1 } ],
  "fluids": [],
  "outputs": [ { "item": "apocalypse_firstlight:flint_hatchet", "count": 1 } ],
  "time": 60,
  "energy": 0
}
```

| 字段 | 含义 | 对接系统 |
|---|---|---|
| `station` | 需要的能力，例如 `hand`（背包手工）、`basic_bench`、`precision_bench`、`machine/<id>`；能力由方块标签 `apocalypse_firstlight:stations/<能力>` 声明 | 制造台、机器 |
| `category` | 界面分组 | BOM 界面、JEI 分类 |
| `tech_group` | 蓝图技术组，省略 = 天生就会 | 蓝图系统（未做） |
| `skill` | 幸存者成长要求，可选 | `survivor_growth` |
| `inputs` | 物品或标签 + 数量，可选 NBT 匹配 | 背包网格扣料 |
| `tools` | 需要但不消耗的工具，带耐久消耗 | AFL 工具（V2） |
| `fluids` | 流体输入，以 mB（= 升）计 | Forge `FluidStack` |
| `outputs` | 多产物，可带几率 | 负重守恒（[weight_system_v1.md](weight_system_v1.md)） |
| `time` / `energy` | 制作时间；FE 消耗（0 = 不需要电） | 现有 FE 机器 |

规则：
- 服务端权威：选配方、校验材料和工具、扣料、产出都在服务端。客户端只画材料清单，点"制作"时发一个配方 ID + 数量的包。
- 配方经原版配方同步到客户端（序列化器实现 `toNetwork` / `fromNetwork`），不另开同步包。蓝图解锁状态存玩家数据，单独同步。
- 现有 5 个机器类型（crushing、alloying 等）**保留不迁移**：它们是连续加工的机器，不是一次性制造。
- JEI：照现有插件读 `RecipeManager`。按蓝图显示锁定状态是 JEI 的显示问题，可以后做；用户在 `afl_inventory_grid_v0_plan.md:305` 表示过不想让 JEI 成为主要入口（第 10 节问题 8）。

### 4.2 烹饪配方类型

单独注册 `apocalypse_firstlight:cooking`，不复用原版烧制类型：

| 字段 | 含义 |
|---|---|
| `vessel` | 器具标签：锅、平底锅、水壶 |
| `heat` | 最低热量等级（篝火 / 柴炉 / 电炉的输出等级） |
| `inputs` / `fluids` | 食材、水（mB） |
| `time` | 烹饪时间 |
| `result` | 成品；像现有烧水一样走 `assemble()`，把污染等级、生熟程度、保质期写进结果 |

净水迁移到这个类型后，原版 SMELTING / SMOKING / CAMPFIRE_COOKING 上的三个 `boiling_*` 配方就可以删。在那之前**不能**先删原版烟熏配方，否则柴炉什么都做不了。

### 4.3 退役清单与制造数据的关系

退役清单只管"原版东西怎么退出"；AFL 配方只管"AFL 东西怎么做"。两者不要混在一个文件里，这样以后回退、调整任何一边都不会影响另一边。

---

## 5. 熔炉、烹饪、桶与流体的替换建议

### 5.1 熔炉 / 烟熏炉 / 高炉 / 营火

| 现在承担的功能 | 替代 | 前置 |
|---|---|---|
| 净水（三种 `boiling_*`） | 柴炉 / 篝火 + 锅或水壶，`cooking` 类型 | 4.2；柴炉改读 `cooking` |
| 原版食物烧制（27 个 food 配方） | 锅、平底锅 + 热源 | D 的 V2 食物设计（生肉食物中毒、烹饪去风险） |
| 铁矿石 → 铁锭、铜、金 | AFL 冶炼路线（第 10 节问题 4） | 用户拍板 |
| 原版砂、黏土、圆石烧制（玻璃、砖、石头） | 建筑材料走 AFL 制造或工业熔炉 | 建筑材料清单 |
| 体温热源（点燃的熔炉） | 柴炉已经是热源 22/r6；新热源方块需要 `LIT` 属性 + `temperature_v1.json` 条目 + 考虑加入 `FuelSpills.flameAt` | 无 |
| 工业熔炉回退原版 BLASTING / SMELTING | 改成只读 AFL 类型，并修 `assemble()` 问题 | 冶炼路线 |

原版**营火**：
- 建议近期 KEEP：它是最早期的热源、净水点，原版配方需要 `minecraft:logs` 物品标签和煤 / 木炭，以后可以换成 AFL 篝火方块。
- `FuelSpills`、`SurvivorSpawns` 已按 `BlockTags.CAMPFIRES` 读它；AFL 篝火加入这个标签即可沿用。

### 5.2 桶与流体

原则：**退役原版桶这件物品，不退役 Forge 流体能力。**

1. AFL 液体容器（水壶、提桶、油桶等）必须提供 `ForgeCapabilities.FLUID_HANDLER_ITEM`（`IFluidHandlerItem`），这样储液罐的 `FluidUtil.interactWithFluidHandler` 不用改就能用，其他模组的罐和管道也能接。现有油桶把 `FluidStack` 存在 NBT 里，可以加一个包装成能力的适配层，不改存档格式。
2. 不封锁原版 `BucketItem` 本身的行为，只断它的获取（已做：配方关、战利品去掉）。旧存档里的桶照常用。
3. 要一起处理的耦合：
   - 热力发电机的岩浆桶条目和液态岩浆的能量换算：拆成独立的 `lava_mb` 能量值；
   - `industrial_waste_bucket` 的 `craftRemainder`；
   - 汽油 / 柴油源方块被空桶吞掉（推断）：给这两种流体一个"无桶时禁止拾取"的处理，或直接给 AFL 容器；
   - 储液罐单格 800 mB 和 1000 mB 桶不匹配（AFL 容器可以按升来定容量）。
4. 净水、烧水需要的接口：`cooking` 配方的 `fluids` 字段 + 器具里存 `FluidStack`（同样以能力暴露）。可操作的 Mesh 液体容器只需要把能力和液位渲染接上，与配方系统解耦。

---

## 6. 兼容风险

### 6.1 旧存档

- 不删注册，所以旧存档里的原版物品、方块全部保留。
- 配方过滤是运行时的，存档里不存配方；玩家已解锁的配方书条目指向不存在的配方。推断无害，原版会跳过找不到的配方。
- 世界里已有的原版工作站：拦截后变成装饰。
- 现有 `VanillaProgressionEvents` 每次登录清零经验；新规则也应在登录时做一次性处理（如果用户选择让残留装备失效）。
- 物品失效（方法 D）一旦上线就是存档语义变化，要能关掉。

### 6.2 创造模式与开发

- 创造标签页只有 AFL 物品，原版标签页没有被隐藏，创造模式仍能拿原版物品。
- 开发命令没有发物品的（只有 `src/dev` 的容器搜索测试用原版食物，不打进 jar）。
- 17 个 GameTest 写死了原版镐、斧的等级；改挖掘规则时要一起改。

### 6.3 其他模组

- 当前依赖：GeckoLib、Vextris、TerraBlender、Curios（双端必需），JEI、Jade（客户端可选）。它们都不提供生存配方。
- 只过滤 `minecraft:` 命名空间，不会误伤其他模组的配方。代价是其他模组如果提供"原版钻石剑"之类的配方，会绕过退役清单；用日志列出，由用户决定是否追加。
- 原版物品标签（`#minecraft:swords`、`#minecraft:logs` 等）不要改成 `replace: true`，其他模组靠它们识别物品。AFL 原木故意不进 `minecraft:logs` **物品**标签（[mesh_trees_p2_felling.md](../rendering/mesh_trees_p2_felling.md):474），这正是"不打开原版配方"的做法，以后沿用。

---

## 7. Main / C / D 需要协调的接口

| 接口 | 现在属于 | 影响 | 建议时机 |
|---|---|---|---|
| 退役清单标签、规则文件、拦截器 | Main（`world/Vanilla*Events`） | 所有工作区的 GameTest 和测试世界 | V1 在 Main 做，合并后通知 C、D |
| `minecraft:` 挖掘标签、`needs_*_tool`、`industrial_material_recovery_tools` | Main（AGENTS.md 规则） | C 的道路方块（`road_structure` 是钻石级）、D 不涉及 | V2 定工具规则时，同时改 AGENTS.md 的默认等级规则 |
| 地形材料（石头、泥土、砂砾、圆石、水） | C | 不受配方禁用影响；**不能**改成不可挖或不掉落 | 不动 |
| 原版矿石、AFL 矿石的世界生成 | C | 原版矿石继续生成；7 种 AFL 矿石至今不生成，冶炼路线依赖它们 | V3 前由 C 排期 |
| 开局物资、继任物资 | D（幸存者） | 现在没有物资；V2 后开局需要什么工具由 V2 决定 | V2 与 D 对齐 |
| 食物、医疗品配方（绷带、医用酒精只在创造标签页，没有配方） | D（健康 V1a / V2 草案） | 需要 `fabrication` 和 `cooking` 类型 | V2/V3 接口定好后 D 只写 JSON |
| `FoodData` 混入（D 关掉了原版食物回血） | D | 与原版食物退役同向，不冲突 | — |
| 背包 2×2 面板、网格、负重 | Main（已合并） | 背包手工制造要替换 2×2 面板 | V2 |
| 网络协议号 | 共用 | 主工作区未提交改动已是 59，D 分支也是 59（D 文档写了合并时改 60）；V1 若加提示包也要动协议号 | 合并顺序由 Main 排 |
| 砍树（`ToolActions.AXE_DIG` + 等级速度） | Main（树） | AFL 斧头必须提供 `AXE_DIG`，并实现 `TieredItem` 或在 `getDestroySpeed(橡木原木)` 返回合理速度 | V2 |
| 载具维修 | Main | 还没有维修系统；以后用 `fabrication` 的 `tools` 字段 | 后续 |
| Vextris | Vextris A | 不涉及配方；Mesh 工具模型需要的共享网格能力归 Vextris | V2 |

C 工作树（`feature/linden-markings`）和 D 工作树（`feature/survival-health-v1a`）都是未提交改动、0 个领先提交。本轮没有发现与本计划直接冲突的改动，只记录上表的接口。

---

## 8. 分阶段路线

### V1：退役机制 + 第一批（不改玩法主链）

- **内容**：
  - 清单标签和规则文件；
  - 推广配方过滤（含 `/reload`）；
  - 工作站拦截标签；
  - 战利品修改器改为标签驱动；
  - 清理僵尸原版装备；
  - 处理流浪商人（先实测）；
  - 修工业熔炉 `assemble()` 问题。
- **第一批移除**：
  - 原版剑、盾、弓、弩、箭、三叉戟、全部原版盔甲（含锁链甲来源）；
  - 附魔书、经验瓶来源；
  - 酿造台、药水、金苹果、不死图腾、末影箱、末影珍珠来源；
  - 拦截酿造台、砂轮、锻造台、制图台、织布机交互。
  - 工具、工作台、熔炉**不动**。
- **前置**：用户拍板第 10 节问题 1、2、3（至少确认 V1 不动工具）。
- **代码类别**：`world/` 事件、`fluid/RemoveItemsLootModifier` 扩展、数据标签、COMMON 配置、1 个 GameTest 类。
- **影响**：
  - 枪伤与护甲平衡：原版盔甲退出后，子弹几乎没有减伤来源，直到 AFL 防弹衣出现；
  - 保温表里的原版盔甲条目留着不删。
- **新世界**：不需要。
- **存档风险**：低，没有删除任何东西。
- **测试**：
  - GameTest：过滤后指定 ID 不存在、AFL 配方都在、`/reload` 后仍过滤；
  - 拦截方块右键返回失败；
  - 战利品修改器去掉标签物品；
  - 工业熔炉污染水保持污染。
  - 专用服务器 + 客户端启动一次。
- **实机验收**：
  - 生存新世界，原版武器盔甲做不出来、JEI 看不到；
  - 工作台、熔炉仍正常；
  - 酿造台等右键无反应；
  - 创造模式不受影响。
- **集成**：Main 做完合并，再通知 C、D 刷新测试世界（不需要新世界）。

### V2：燧石工具、铁制 Mesh 工具、背包手工制造与基础制造台

- **内容**：
  - `fabrication` 配方类型 + BOM 界面（背包内手工 + 基础制造台，即工匠台）；
  - 燧石工具（镐、斧、铲、锄、刀）、AFL 铁制 Mesh 工具；
  - 自定义 `Tier`（`TierSortingRegistry`，铁级为手工上限）；
  - 新的工业方块拆卸规则；
  - AFL 原木 → 木板、木棍的配方（解决当前木材断点）；
  - AFL 点火物（打火机 / 火柴）。
  - 切换时移除原版 2×2 / 3×3 基础配方、原版工具配方，拦截工作台。
- **前置**：
  - V1；
  - 用户拍板问题 3、4、5、7；
  - 工具 Mesh 模型（Blockbench 源放 `src/main/blockbench/`）；
  - Vextris 是否需要新能力。
- **代码类别**：配方类型和序列化器、制造台方块和菜单和界面、工具物品、挖掘标签（81 个钻石级方块重新定级）、`industrial_material_recovery_tools`、17 个 GameTest、AGENTS.md 挖掘规则、背包 2×2 面板替换、JEI 分类。
- **影响**：砍树（新斧头要 `AXE_DIG` + 速度）、负重（制造输入计重）、配方书（退出）、D 的开局物资。
- **新世界**：不需要；老存档里的原版工具保持原样（按问题 2 的决定）。
- **存档风险**：中。挖掘等级改动影响已建建筑能否拆下。
- **测试**：
  - GameTest：每个 AFL 配方能在对应站点做出、材料不够做不出、工具耐久扣减、等级标签与 `requiresCorrectToolForDrops` 一致（沿用 AGENTS.md 挖掘审计）；
  - 从零生存到铁工具的路线脚本化检查。
- **实机验收**：新世界从空手到铁工具、拆下一台工业机器、砍一棵网格树。
- **集成**：
  - 先合并配方类型和界面，再合并工具；
  - D 的开局物资在类型合并后接；
  - C 的道路方块等级随挖掘规则一起改。

### V3：烹饪、流体容器、原版工作站退场

- **内容**：
  - `cooking` 配方类型；
  - 锅、平底锅、水壶（Mesh，带 `FLUID_HANDLER_ITEM`）；
  - 柴炉和篝火改读 `cooking`；
  - 净水迁移，删除三个 `boiling_*` 配方；
  - AFL 液体容器替代原版桶，修热力发电机和工业废液桶的耦合；
  - 移除原版熔炼、烟熏、营火食物配方；
  - 拦截熔炉、烟熏炉、高炉；
  - 工业熔炉只读 AFL 类型；
  - 早期冶炼路线。
- **前置**：V2；D 的食物 V2 设计定稿；C 排期 AFL 矿石生成（如果冶炼路线依赖它们）。
- **代码类别**：配方类型、柴炉方块实体、新器具物品和方块、流体能力适配、温度热源表、热力发电机燃料表、JEI。
- **影响**：
  - 饮水（全部净水来源）；
  - 体温（熔炉热源随拦截失去，原版熔炉不再被点燃）；
  - D 的食物和医疗；
  - 储液罐交互。
- **新世界**：不需要；只有改矿石世界生成时才需要新区块（C 的范围）。
- **存档风险**：中。世界里已有的脏水、半熟食物要能继续用；原版桶留着能用。
- **测试**：
  - GameTest：污染水煮后保持等级、器具装取流体走 `FluidUtil`、储液罐与新容器互通、热源表包含新方块；
  - 专用服务器流体同步。
- **实机验收**：烧水、煮肉、用新容器从水源和储液罐装取、篝火取暖。
- **集成**：与 D 的食物 V2 同步合并。

### 后续

- 工业动力工具（钻、角磨机、液压剪），用于拆卸工业方块和高等级挖掘；
- 高强度合金（硬质合金坯件已有，现在没有用途）用于钻头、刀具、耐磨件；
- 精密制造台（枪械、配件、弹药）；
- 蓝图解锁；
- 载具维修；
- 原版红石的去留；
- AFL 生物替换后，皮革、羊毛、羽毛来源要重新设计（[afl_fauna_plan_v0.md](afl_fauna_plan_v0.md)）。

---

## 9. 不建议删除或替换的原版底层机制

| 机制 | 原因 |
|---|---|
| 原版方块、物品注册 | 地形、道路、老宅、地形平滑、感染者破门、幸存者生成都直接引用；旧存档 |
| 原版地形方块的挖掘和掉落 | 道路施工、地形材料、玩家建造 |
| 原版配方类型 SMELTING / SMOKING / CAMPFIRE_COOKING 的注册 | 净水和柴炉在 V3 迁移前依赖它们 |
| `RecipeManager`、配方同步、配方序列化器机制 | AFL 自己的配方和 JEI 都走这条路 |
| 原版挖掘等级体系（`mineable/*`、`needs_*_tool`、`TierSortingRegistry`） | AFL 工具应**挂进**这套体系，而不是另起一套；多格方块、掉落表都按它判断 |
| `ToolActions`（`AXE_DIG` 等） | 砍树按它识别斧头 |
| Forge 流体能力（`FLUID_HANDLER`、`FLUID_HANDLER_ITEM`、`FluidUtil`） | 管道、储液罐、化学反应器、加油站；和其他模组兼容 |
| 护甲属性（`generic.armor`、韧性）与伤害类型标签 | AFL 防弹衣以后可以直接用属性；子弹伤害计算依赖它 |
| 原版战利品表 / 全局修改器机制 | 容器搜索靠原版 `RandomizableContainerBlockEntity` 的战利品表 |
| 原版物品标签（`#minecraft:logs`、`#minecraft:planks` 等） | 柴炉燃料、木屋方块判断、其他模组；不要 `replace: true` |
| 红石信号机制 | AFL 门继承 `DoorBlock` 响应红石；容器比较器输出（下面的文档漂移）；布线系统以后可能联动 |
| 玻璃瓶 | 现在唯一的饮水容器 |
| 煤、木炭、铁锭、铜锭、皮革、羊毛、玻璃板 | 是现实材料，AFL 配方在用；只调整获取路径，不退役物品 |
| 附魔注册本身 | 砍树读效率、尸体读消失诅咒；只断附魔的获取 |

---

## 10. 需要用户拍板的设计问题

1. **禁用节奏**：V1 只退役战斗和魔法类、基础工具和工作站等 V2 替代上线再关（推荐），还是 V1 就全关？
2. **已存在的原版装备**（旧存档、创造带出、漏网来源）：保持原效果（推荐，最安全）/ 属性清零 / 不能装备？
3. **工业方块拆卸**：81 个钻石级方块，以及只认钻石、下界合金镐的 10 个钢和混凝土方块，手工上限改为铁级后用什么拆？
   - 铁镐慢拆；
   - 撬棍、扳手类拆卸工具；
   - V2 先保留钻石镐、后续换动力工具。
   - AGENTS.md 的默认"钻石级"规则要一起改写。
4. **早期铁从哪来**：原版熔炉退场后，铁矿石怎么变成铁？
   - 简易锻炉 / 坩埚；
   - 只能搜刮废金属；
   - 工业熔炉（需要电）。
   - 原版铁、铜、金、钻石、红石、青金石矿是否继续生成（C 的范围）？挖出来之后各自是什么用途？
5. **点火**：新世界的柴炉现在点不着（推断：打火石配方关了，火焰弹拿不到），AFL 打火机 / 火柴要不要提前到 V1？还是 V1 先临时恢复某个点火来源？
6. **原版工作站方块**：拦截后完全没反应，还是给一行提示？拆下时掉落自己还是掉落拆解材料？
7. **背包手工制造**：去掉 2×2 格子、改成和工匠台同样的材料清单界面（推荐，与 wood_system_plan_v1 §4 一致）？
8. **JEI**：保留作为查询工具并显示蓝图锁定状态，还是只在创造模式 / 开发时用？
9. **原版食物**：V3 之前，原版生肉、熔炉烹饪是否保持现状？
10. **原版获取途径**：钓鱼、剪羊毛、动物掉落在 AFL 生物替换前保持现状，还是 V1 就过滤退役物品？
11. **原版红石**：作为玩家可用的电路保留，还是退出获取，等 AFL 布线联动？
12. **木材断点**：网格森林原木没有配方，是先加一个临时的"原木 → 木板"手工配方（wood_system_plan_v1 §6 提过），还是等 V2 一起做？

---

## 11. 审计矩阵

分类：**KEEP** 保留；**DISABLE** 禁用正常获取或使用；**REPLACE** 用 AFL 内容替换；**INTERNAL** 保留底层能力，不作为正式玩法；**DEFER** 暂不处理，等待其他系统。复杂度：低 / 中 / 高。"待验证"表示结论未实测。

### 11.1 武器与防具

| 原版内容 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 剑（6 种材质） | 可合成、近战槽可放 | `weapons/melee.json` 含 `#minecraft:swords`；尺寸、重量 | DISABLE（配方 + 获取） | 原创近战（撬棍已有，其他待设计） | 低：撬棍在生存里拿不到，近期近战只剩空手和斧 | 低 | V1 |
| 盾牌 | 可合成 | 尺寸；会挡子弹 | DISABLE | 暂无（防暴盾以后再说） | 低 | 低 | V1 |
| 弓、弩、箭、光灵箭、药箭 | 可合成 | 第一人称渲染读上弦弩 | DISABLE | 枪械 | 低（骷髅已不刷，箭只剩配方） | 低 | V1 |
| 三叉戟 | 溺尸掉落 | 无 | DISABLE（获取） | — | 低 | 低 | V1 |
| 皮革、锁链、铁、金、钻石、下界合金盔甲 | 可合成；僵尸会自带 | 护甲值 HUD、子弹减伤、保温表、负重 65%、"防弹衣"槽标签 | DISABLE（获取）；护甲属性机制 INTERNAL | 防护服、防弹衣、防弹插板 | 中：退出后子弹几乎没有减伤；保温依赖原版盔甲 | 中 | V1 获取 / 后续替代 |
| 皮革马铠等马具 | 可合成 | 无 | DISABLE | — | 低 | 低 | V1 |
| 僵尸自带原版装备 | Normal 难度随机生成 | 无 | DISABLE（生成时清理） | AFL 感染者服饰 | 低 | 低 | V1 |
| 锻造模板、盔甲纹饰 | 原版结构拿不到 | 无 | DISABLE（配方） | — | 低 | 低 | V1 |

### 11.2 工具

| 原版内容 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 木、石、金制工具 | 生存开局主路径 | 砍树读等级速度；17 个 GameTest | DISABLE（V2 切换时） | 燧石工具 | 高：在替代前禁用会断开局 | 低 | V1 机制 / V2 生效 |
| 铁制工具 | 标准工具 | `needs_iron_tool` 27 个方块 | REPLACE | AFL 铁制 Mesh 工具（同铁级） | 中 | 中 | V2 |
| 钻石、下界合金工具 | 工业方块唯一能拆的工具 | 81 个 `needs_diamond_tool`；`industrial_material_recovery_tools` 只认这两种镐 | DEFER → REPLACE | 工业拆卸工具、动力工具 | 高：拿掉后工业方块拆不下来 | 高 | V2 定规则 / 后续 |
| 锄 | 耕地 | 无 | REPLACE | Mesh 锄 | 低 | 低 | V2 |
| 剪刀 | 剪羊毛、树叶 | 羊毛是护耳配方的原料 | DEFER | AFL 剪切工具 | 低 | 低 | V2 / fauna |
| 钓竿 | 钓鱼（食物、附魔书） | 无 | DEFER；钓到的退役物品 DISABLE | AFL 钓具 | 低 | 低 | V1 过滤 / 后续 |
| 打火石 | 柴炉唯一点火物（+ 火焰弹） | `cabin/BoxStove.java:109`；配方已关 | REPLACE | 打火机、火柴 | **已断：新世界柴炉推断无法点火**（待验证） | 低 | V1–V2（问题 5） |
| 火焰弹 | 柴炉点火 | 需要烈焰粉，下界已关 | INTERNAL | 同上 | 已不可得 | 低 | — |
| 刷子、望远镜、指南针、时钟、拴绳、鞍 | 各自用途 | 无 | DEFER | 视需要设计 | 低 | 低 | 后续 |
| 2×2 修复配方（`repairitem`） | 合并修复工具 | 无 | DISABLE（随 V2） | AFL 维修 | 低 | 低 | V2 |

### 11.3 特殊物品与材料

| 原版内容 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 红石粉、红石组件 | 原版电路 | AFL 能源、布线不用；AFL 门响应红石；6 种容器有比较器输出 | INTERNAL（机制）；获取 DEFER | AFL 布线控制 | 低 | 低 | 后续（问题 11） |
| 空桶、水桶、岩浆桶等 | 已不能合成、战利品已去除 | 储液罐 `FluidUtil`；热力发电机岩浆桶 + 液态岩浆换算；工业废液桶 `craftRemainder`；汽油柴油源方块被吞（推断） | REPLACE；Forge 能力 KEEP | 带 `FLUID_HANDLER_ITEM` 的 AFL 容器 | 中 | 中 | V3 |
| 钻石 | 钻石工具 | 间接：工业方块拆卸 | DEFER | 高强度合金不做钻石等级，只做加工件 | 高（与工具规则绑定） | 中 | V2 |
| 下界合金 | — | 下界已关，事实上拿不到 | INTERNAL | — | 无 | — | — |
| 附魔书、经验瓶 | 钓鱼、（关掉的）结构 | 附魔台、铁砧已拦 | DISABLE（获取） | — | 低 | 低 | V1 |
| 药水、酿造材料 | 酿造台 | 水瓶已被 AFL 拦截；D：原版治疗只剩约 1/5 效力 | DISABLE | AFL 医疗品（D） | 低 | 低 | V1 |
| 金苹果、附魔金苹果、不死图腾 | 治疗、复活 | D：原版效果按 20 点作用 | DISABLE | AFL 医疗 | 低 | 低 | V1 |
| 末影箱、末影珍珠、末影之眼 | 跨地存储、传送 | 尸体继任文档说"按原计划删除" | DISABLE | — | 低 | 低 | V1 |
| 煤、木炭 | 熔炉燃料 | 钢坯碳源、热力发电机燃料 | KEEP | — | — | — | — |
| 铁锭、铜锭、铁粒 | 原版熔炼产物 | 钢坯、护耳、腕表温度计；爆炸残骸给铁粒 | KEEP（物品）；获取路径 REPLACE | AFL 冶炼路线 | 高：禁原版熔炼后钢链断 | 中 | V3（问题 4） |
| 皮革、羊毛、玻璃板、红染料、草方块 | 原版材料 | AFL 配方输入 | KEEP；来源 DEFER | — | 低 | 低 | fauna 后 |
| 玻璃瓶 | 酿造 | 唯一的饮水容器 | KEEP | 以后可加 Mesh 水壶 | 高（禁了就断饮水） | — | — |
| 原版水瓶（`minecraft:potion`） | 酿造 | 不给水分；炼药锅、发射器仍会产生 | INTERNAL | AFL 水瓶 | 低 | 低 | — |
| 石英 | — | 原版石英拿不到（只能拆建筑） | INTERNAL | 高纯石英砂（未接配方） | — | — | — |

### 11.4 原版工作站

| 原版内容 | 原版用途 / 配方类型 | AFL 当前依赖 | 玩家交互 | 世界生成里的装饰 | 推荐处理 | 未来替代 | 兼容风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|---|---|
| 工作台 | 3×3 shaped / shapeless | 6 个 AFL 配方用原版合成类型（护耳、两种温度计、两种路缘石、铅屏蔽砖；哪些放不进 2×2 未逐个核对）；负重计入格子 | 允许 → V2 拦截 | 未使用 | DISABLE（交互 + 配方），方块保留 | 基础制造台（工匠台） | 中 | 低 | V2 |
| 背包 2×2 | 2×2 | 路缘石、铅屏蔽砖等配方可 2×2 做 | 允许 | — | REPLACE | 背包手工制造（BOM） | 中 | 中 | V2 |
| 熔炉 | smelting | 净水、热源（点燃时） | 允许 → V3 拦截 | 退役地堡 | DISABLE 交互；配方类型 INTERNAL | 柴炉 + 锅、AFL 冶炼 | 高（净水、铁锭） | 中 | V3 |
| 烟熏炉 | smoking | 柴炉读 smoking 配方；净水；热源 | 允许 → V3 拦截 | 退役地堡 | 同上 | 柴炉 + `cooking` | 高（柴炉） | 中 | V3 |
| 高炉 | blasting | 工业熔炉回退；热源 | 允许 → V3 拦截 | 未使用 | DISABLE | 工业熔炉 | 中 | 低 | V3 |
| 营火、灵魂营火 | campfire_cooking | 净水、热源、燃料点火源、幸存者生成规避 | 允许 | 未使用 | KEEP → 以后 REPLACE | AFL 篝火（加入 `#campfires`） | 中 | 中 | V3 |
| 锻造台 | smithing_transform / trim | 无 | 允许 → 拦截 | 未使用 | DISABLE | 精密制造台 | 低 | 低 | V1 |
| 切石机 | stonecutting（201） | 无；AFL 建筑方块没有配方 | 允许 | 未使用 | DEFER（等建筑材料制造） | 基础制造台的石材类 | 低 | 低 | V2 |
| 砂轮 | 去附魔、修复 | 无 | 允许 → 拦截 | 未使用 | DISABLE | AFL 维修 | 低 | 低 | V1 |
| 铁砧 | 修复、附魔 | 已拦截、配方已去 | 已拦截 | 退役地堡 | KEEP（已完成） | — | — | — | 已做 |
| 附魔台 | 附魔 | 已拦截、配方已去 | 已拦截 | 未使用 | KEEP（已完成） | — | — | — | 已做 |
| 酿造台 | 酿造 | 无 | 允许 → 拦截 | 退役地堡 | DISABLE | AFL 医疗制造 | 低 | 低 | V1 |
| 织布机 | 旗帜图案 | 无 | 允许 → 拦截 | 退役地堡 | DISABLE | — | 低 | 低 | V1 |
| 制图台 | 地图 | 无 | 允许 → 拦截 | 未使用 | DISABLE | AFL 地图 / 导航（未设计） | 低 | 低 | V1 |
| 制箭台、讲台、堆肥桶 | 村民职业方块等 | 无 | 允许 | 未使用 | DEFER | — | 低 | 低 | 后续 |
| 箱子、木桶 | 储物 | 容器搜索不覆盖原版箱子；退役地堡用木桶 | 允许 | 退役地堡 | KEEP 方块；配方 DEFER | AFL 家具容器 | 低 | 低 | V2 |
| 信标 | 增益 | 无 | 允许 | 未使用 | DISABLE（配方） | — | 低 | 低 | V1 |

### 11.5 配方与制造机制

| 原版内容或机制 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| crafting_shaped / shapeless（822） | 主要制造方式 | 6 个 AFL 配方用这两种类型 | DISABLE `minecraft:` 命名空间（分批）；类型 INTERNAL | `fabrication` | 高（分批切换可控） | 中 | V1 机制 / V2 主体 |
| AFL 自己用原版类型的 6 个配方 | 体温计、护耳、路缘石、铅屏蔽砖 | — | REPLACE（迁移到 `fabrication`） | — | 低 | 低 | V2 |
| smelting / blasting / smoking / campfire（112） | 冶炼、烹饪 | 净水、柴炉、工业熔炉 | DISABLE（V3）；类型 INTERNAL | `cooking`、AFL 冶炼 | 高 | 中 | V3 |
| stonecutting（201） | 石材变体 | 无 | DEFER | 制造台石材类 | 低 | 低 | V2 |
| smithing（25） | 下界合金升级、纹饰 | 无 | DISABLE | — | 低 | 低 | V1 |
| crafting_special（14） | 染色、修复、复制地图等 | 无 | DISABLE（随 V2） | — | 低 | 低 | V2 |
| 配方书 | 工作台、熔炉界面 | AFL 背包界面没有配方书；`LockedInventoryRecipeMixin` | DISABLE（随工作站） | BOM 界面 | 低 | 低 | V2 |
| `recipes/*` 解锁进度 | 解锁配方书条目 | 配方删了就不会解锁 | INTERNAL（不处理） | — | 无 | — | — |
| `RecipeManager` 与配方同步 | 全部配方 | AFL 配方、JEI | KEEP | — | — | — | — |
| AFL 机器配方类型（5 个）+ `boiling_*`（3 个） | 工业链、净水 | 机器、JEI | KEEP；`boiling_*` V3 迁移 | — | — | — | V3 |
| JEI | 查配方 | AFL 插件只加 AFL 分类 | KEEP（问题 8） | 显示蓝图锁定 | 低 | 低 | 后续 |
| 经验、进度 | — | 已禁 | KEEP（已完成） | — | — | — | 已做 |

### 11.6 获取途径

| 原版内容或机制 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 原版箱子战利品 | 结构箱子 | 原版结构已全部关闭 | KEEP（事实已无） | AFL 建筑战利品表 | — | — | — |
| 全局战利品修改器 | 去桶、污染 | `remove_items` | REPLACE（改为读退役标签） | — | 低 | 低 | V1 |
| 钓鱼 | 食物、宝藏 | 无 | 退役物品 DISABLE，其余 DEFER | — | 低 | 低 | V1 |
| 生物掉落 | 皮革、羊毛、腐肉、铁锭（僵尸稀有掉落）等 | AFL 配方用皮革、羊毛 | DEFER；退役物品过滤 | AFL 生物 | 低 | 低 | V1 过滤 / fauna |
| 流浪商人 | 交易 | 推断仍生成（待验证） | DISABLE | — | 低 | 低 | V1 |
| 村庄、村民 | 交易 | 村庄已关 | KEEP（已完成） | — | — | — | 已做 |
| 原版刷怪笼 / 刷怪房 | — | 已关 | KEEP（已完成） | — | — | — | 已做 |

### 11.7 原版方块与世界生成

| 原版内容 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 石头、泥土、草、沙、砂砾、圆石、水等地形方块 | 地形、道路、老宅 | 地表规则、`RoadChunkPass`、`TerrainSmooth`、温度、饮水 | KEEP（世界生成 + 挖掘 + 掉落） | — | 极高 | — | 不动 |
| 原版矿石（煤、铁、铜、金、红石、青金石、钻石） | 世界生成，玩家可挖 | 群系 JSON、矿脉；道路施工把它们当作天然地面 | 世界生成 KEEP（C）；用途按材料分别处理 | AFL 冶炼路线 | 中 | 中 | V3 / C |
| AFL 7 种矿石 | 已注册 | 没有世界生成 | DEFER（C 排期） | — | — | 中 | V3 前 |
| 原版树 | 少数群系仍生成 | 关掉网格森林配置就全部回到原版树 | DEFER（树系统） | 网格树 | 低 | — | — |
| 原版木材方块与 `#logs` / `#planks` 标签 | 原版配方、柴炉燃料 | 木屋判断、柴炉、砍树地面判断 | KEEP（标签） | AFL 木材体系 | 中 | — | — |
| 原版工作站方块 | 装饰 | 只在退役地堡 | KEEP 方块，拦截交互 | — | 低 | 低 | V1–V3 |
| 下界、末地维度 | — | 已关闭（代码确认） | KEEP（已完成） | — | — | — | 已做 |

### 11.8 食物与流体

| 原版内容 | 当前用途 | 当前实际依赖 | 推荐处理 | 未来 AFL 替代 | 禁用风险 | 复杂度 | 阶段 |
|---|---|---|---|---|---|---|---|
| 原版食物 | 唯一食物来源 | 饮水量表；D 关闭了原版食物回血 | DEFER → REPLACE | AFL 食物、腐败（D 的 V2 草案） | 高（不能先关） | 高 | V3 |
| 熔炉烹饪 | 煮肉 | 柴炉读 smoking 配方 | REPLACE | 锅、平底锅 + 热源 | 中 | 中 | V3 |
| 原版水源方块 `Fluids.WATER` | 饮水、灌瓶 | `PlayerThirst` | KEEP | — | 极高 | — | 不动 |
| 原版岩浆 | 热力发电机液体燃料 | 泵、管道 | KEEP | — | — | — | — |
| 炼药锅 | 存水 | 会产生原版水瓶 | DEFER | — | 低 | 低 | 后续 |
| 牛奶 | 解除效果 | 已禁挤奶 | KEEP（已完成） | 纸盒牛奶（计划） | — | — | 已做 |

---

## 12. 本轮发现的问题和文档漂移（未修）

| # | 问题 | 位置 | 类别 |
|---|---|---|---|
| 1 | 工业熔炉回退到原版熔炼时用 `getResultItem` 不用 `assemble()`，有污染的脏水会变成干净净水 | `blockentity/IndustrialFurnaceBlockEntity.java:262-266` | 代码确认，疑似 bug |
| 2 | 柴炉只能用打火石或火焰弹点燃；打火石配方已关、火焰弹不可得 | `cabin/BoxStove.java:109` | 推断：新世界无法点火，待实机确认 |
| 3 | 网格森林原木没有配方、不在 `minecraft:logs` 物品标签 | `tags/` 无 `data/minecraft/tags/items` | 推断：网格森林区拿不到木板，待实机确认 |
| 4 | 汽油 / 柴油源方块没有桶物品，空桶对着用会吞掉源方块 | `registry/AflFluids.java:28-41` | 推断，待实测 |
| 5 | 流浪商人可能仍会出现 | 刷怪过滤不含 EVENT 类型 | 推断，待实测 |
| 6 | `04 - CurseForge 等 mod 发布页.md:83` 写"112 个进度全部 forge:false"，实际 110 个；L87 写"下界是否关闭未确认"，代码已关闭 | 文档 | 漂移 |
| 7 | `progressive_container_search_v1.md:251` 写"没有容器有比较器输出"，实际 6 种容器有 | 文档 | 漂移 |
| 8 | `forest_start_home_b_v1_plan.md:356` 写管式农场门是铁级，标签里是石级 | 文档 | 漂移 |
| 9 | 末影箱配方仍在，与继任文档"按原计划删除"不符 | `survivor_death_succession_v0.md:213,269` | 计划未落实 |
| 10 | 压缩机没有任何配方，但 JEI 分类仍注册 | `compat/jei/AflJeiPlugin.java` | 已知（material_system_v1.md） |

以上都没有在本轮修改，等 V1 一起处理或另行安排。
