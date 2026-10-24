# AFL 原版生存改造 V1：原版装备与魔法退役

状态（2026-10-20）：在分支 `AFL-Vanilla-Rework`（工作树 `D:/Minecraft Modding/AFL-vanilla-rework`）上实现，**未合并 master**。`compileJava` 通过；**用户 2026-10-20 实机 PASS**（工作树开发客户端，旧存档和新世界）。GameTest `VanillaRetirementGameTests` 没跑。

**V1.1 原版木头退役（2026-10-20，用户："原版树种整套退役，通用木制品也一起退役，等 V2 制造台再用 AFL 配方补"）**：同一分支，见 4.6 节。`compileJava` 通过、联邦世界生成 `--check` 和网格森林检查通过；**用户 2026-10-20 实机 PASS**；GameTest 已按新规则改、没跑。

**V1.2 原版内容大清理（2026-10-20，用户："接下来我觉得可以一次删多一点"）**：同一分支，见 4.7 节。下界、末地、海晶、青金石、红石、幽匿、潜影盒、信标、功能性方块和容器、熔炉一类和营火、铁轨和矿车、弓弩箭退役；青金石矿和红石矿不再生成；原版生物不再进入世界（`minecraft:zombie` 除外，它是 AFL 的感染者）；刷怪蛋页隐藏。`compileJava` 通过；**用户 2026-10-20 实机 PASS**；GameTest 已按新规则改、没跑。原版玻璃这一轮没动（4.7 节）。

**V1.3 工具、食物和剩下的原版件（2026-10-20，用户："我们后面会做自己的工具，按照真实逻辑"）**：同一分支，见 4.8 节。原版全部工具（含所有斧、镐、锹、锄）、桶和桶装液体、全部食物和药水、TNT、铁门、铁活板门、避雷针、遮光玻璃、锁链、不死图腾、三叉戟、马鞍、烟花、骨粉、末影珍珠、山羊角、唱片、紫水晶退役；紫水晶晶洞不再生成。`compileJava` 通过；**用户 2026-10-20 实机 PASS**；GameTest 已按新规则改、没跑。

**V1.4 砖、砂岩、深板岩建筑件、铜建筑件、钻石和绿宝石（2026-10-20）**：同一分支，见 4.9 节。`compileJava` 通过；**用户 2026-10-20 实机 PASS**；GameTest 已按新规则改、没跑。

**V1.5 生物掉落、考古、奇幻植物、珊瑚、蘑菇方块、储存方块和装饰方块（2026-10-20）**：同一分支，见 4.10 节。会生成的荧光地衣、孢子花、垂滴叶、珊瑚、海泡菜、巨型蘑菇、粉红色花簇、化石、蓝冰一起去掉。`compileJava` 通过；**用户 2026-10-20 实机 PASS**，GameTest 已按新规则改、没跑。

**V1.6 铁栏杆、附魔书、创造栏标签页（2026-10-20）**：同一分支，见 4.11 节和 7.1 节。原版铁栏杆退役；附魔书、附魔之瓶从创造栏拿掉；原版战斗页、红石页只剩重复的东西，拿掉以后不再显示；创造栏标签页改成 AFL 在前、没有空位、一页 15 个（上 5、左 5、下 5）。`compileJava` 通过；**用户 2026-10-20 实机 PASS**，GameTest 已按新规则改、没跑。

**V1.9 原版植被退役（2026-10-23，用户："先退役原版植被，然后再设计各类植被"）**：分支 AFL-Workbench，见 4.13 节。原版的草、蕨、花、甘蔗、睡莲、海草、海带、竹子、藤蔓、仙人掌、小蘑菇不再生成、拿不到；地堡的 6 盆原版盆栽换成空花盆。只改数据，没有 Java 改动；**用户 2026-10-23 实机 PASS**，提交 9c6f24b2。AFL 植被另见 [vegetation_v1_design.md](vegetation_v1_design.md)。

依据：[V0 审计](vanilla_survival_rework_v0_audit.md)；用户 2026-10-19 的 V1 任务书和 V1a 补充。V2 的配方知识接口另见 [survivor_recipe_knowledge_v2_plan.md](survivor_recipe_knowledge_v2_plan.md)。

---

## 1. 范围和原则

- 原版剑、盾牌、盔甲在 AFL 正常生存里**完全不能用**，不只是不能合成。
- 附魔、铁砧、酿造、砂轮、锻造台升级、末影箱，在正常生存里不能用。
- **不删注册、不删物品、不改 NBT、不清装备栏。** 旧物品留着，只是没有效果、拿不到新的。
- 规则全部在服务端执行，按物品判断，所有游戏模式都生效。只有工作站交互对创造和旁观模式放行。
- 一份数据清单 + 一个总开关。开关关掉，恢复原版规则。
- V1 **不动**：2×2 / 3×3 合成本身、原版石 / 铁 / 金 / 钻石镐铲斧锄、熔炉、铁矿、钢坯流程（GameTest 里逐条检查这些配方还在）。
- 木材链和工作台起初也不动；**V1.1（2026-10-20）起原版树种和通用木制品一起退役**（4.6 节），生存里暂时没有木棍、工作台、火把、木工具，等 V2 制造台用 AFL 木材补。

## 2. 统一退役清单

都在 `src/main/resources/data/apocalypse_firstlight/` 下，标签都是 `replace: false`，其他数据包可以往里加。

| 文件 | 内容 | 用途 |
|---|---|---|
| `tags/items/retired/swords.json` | 木、石、铁、金、钻石、下界合金剑 | 剑的规则 |
| `tags/items/retired/shields.json` | `minecraft:shield` | 盾的规则 |
| `tags/items/retired/armor.json` | 皮革、锁链、铁、金、钻石、下界合金 × 头盔 / 胸甲 / 护腿 / 靴子，外加海龟壳 | 盔甲的规则 |
| `tags/items/retired/gear.json` | `#swords` `#shields` `#armor` | 属性、附魔、保暖、提示 |
| `tags/items/retired/wood.json` | 原版 11 个树种（橡、云杉、白桦、丛林、金合欢、深色橡、红树、樱花、竹、绯红、诡异）的全部方块物品：原木、木头、去皮、木板、楼梯、台阶、栅栏、栅栏门、门、活板门、按钮、压力板、告示牌、悬挂告示牌、船、运输船、树苗、树叶，加杜鹃、红树根、竹马赛克，共 206 个（V1.1） | 4.6 节 |
| `tags/items/retired/wood_products.json` | 通用木制品 44 个：木棍、碗、工作台、箱子、陷阱箱、木桶、书架、雕纹书架、讲台、梯子、堆肥桶、制图台、制箭台、织布机、音符盒、唱片机、蜂箱、16 色床、木镐 / 斧 / 锹 / 锄、火把、灵魂火把、物品展示框、荧光物品展示框、画、盔甲架、脚手架（V1.1） | 4.6 节 |
| `tags/items/retired/nether.json`、`end.json`、`shulker_boxes.json`、`prismarine.json`、`lapis.json`、`redstone.json`、`sculk.json`、`functional.json`、`ranged.json`、`spawn_eggs.json` | V1.2 的 10 份清单，共 333 个物品（4.7 节） | 4.7 节 |
| `tags/items/retired/unobtainable.json` | `#gear`、附魔书、附魔之瓶、`#wood`、`#wood_products`、V1.2 的 10 份、V1.3 的 4 份、V1.4 的 3 份和 V1.5 的 5 份清单 | 配方（按输出）、战利品、交易、出生装备 |
| `tags/items/retired/creative_hidden.json` | `#gear`、`#wood`、`#wood_products`、V1.2 的 10 份、V1.3 的 4 份、V1.4 的 3 份和 V1.5 的 5 份清单 | 创造栏隐藏（只是外观） |
| `tags/items/retired/misc_blocks.json`、`amethyst.json`、`tools.json`、`food.json` | V1.3 的 4 份清单，共 131 个物品（4.8 节） | 4.8 节 |
| `tags/items/retired/masonry.json`、`copper_blocks.json`、`gems.json` | V1.4 的 3 份清单，共 91 个物品（4.9 节） | 4.9 节 |
| `tags/items/retired/mob_drops.json`、`archaeology.json`、`nature.json`、`storage_blocks.json`、`decor_blocks.json` | V1.5 的 5 份清单，共 121 个物品（4.10 节） | 4.10 节 |
| `tags/entity_types/retired/mobs.json` | V1.2：78 种原版生物（除 `minecraft:zombie` 以外全部） | 不再进入世界（4.7 节） |
| `tags/blocks/retired/workstations.json` | 附魔台、铁砧（三种损坏程度）、酿造台、砂轮、锻造台、末影箱 | 生存 / 冒险模式拒绝交互 |
| `vanilla_retirement/v1.json` | `remove_recipes`、`remove_recipe_types`、`closed_dimensions` | 配方 ID、配方类型、关闭的维度 |
| `loot_modifiers/retired_vanilla.json` | `remove_items`，标签 `retired/unobtainable`，`"retirement": true` | 所有新生成的战利品 |

`vanilla_retirement/v1.json` 当前内容：

- `remove_recipes`：`minecraft:anvil`、`enchanting_table`、`brewing_stand`、`grindstone`、`smithing_table`、`ender_chest`、`shield_decoration`、`armor_dye`；V1.3 加了 11 个特殊配方：`banner_duplicate`、`book_cloning`、`decorated_pot`、`firework_rocket`、`firework_star`、`firework_star_fade`、`map_cloning`、`map_extending`、`shulker_box_coloring`、`suspicious_stew`、`tipped_arrow`（特殊配方的 `getResultItem` 是空的，按输出删不掉，只能按 ID）。
- `remove_recipe_types`：`minecraft:smithing_trim`（盔甲纹饰，按序列化器 ID 判断）。
- `closed_dimensions`：`minecraft:the_nether`、`minecraft:the_end`。

配方 ID 的命名空间和输出物品的注册 ID 是两回事。配方按三条规则任一命中就移除：
1. 配方 ID 在 `remove_recipes` 里；
2. 配方的序列化器 ID 在 `remove_recipe_types` 里；
3. 配方输出（`getResultItem`）在 `retired/unobtainable` 里，**不管配方是哪个命名空间的**。所以别的模组做铁剑的配方也会被去掉。日志会单独列出 minecraft 以外被去掉的配方：`[AFL RETIREMENT] removed N recipes (M outside minecraft: [...])`。

## 3. 总开关

- 文件：`config/apocalypse_firstlight-common.toml`，`[vanilla_retirement] enabled = true`（默认开）。
- COMMON 配置在数据包加载之前就读入，所以开关能决定配方修剪。代码：`world/VanillaRetirement.java`。
- 关掉之后：
  - 战利品、交易、属性、附魔、盾牌、剑、工作站、酿造、经验、维度：下次判断时立即恢复原版。
  - 配方：`/reload` 或重启后回来。
  - 已经穿在身上的盔甲、拿着的剑：属性修饰符要等换装或重进世界才刷新。
  - 创造栏：重进世界后恢复显示。
- GameTest 用 `VanillaRetirement.setTestOverrideOff(true)` 临时关掉，不改配置文件。

## 4. 规则

### 4.1 剑

- 所有属性修饰符清空（物品默认属性和 NBT 里的 `AttributeModifiers` 都走 `ItemAttributeModifierEvent`，优先级 LOWEST），伤害和攻速与空手相同。
- 附魔不生效：锋利、亡灵杀手、节肢杀手、击退、火焰附加、抢夺、横扫之刃（`mixin/RetiredGearEnchantmentMixin`）。
- 没有横扫：`SwordItem.canPerformAction` 对退役剑返回 false（`mixin/RetiredSwordMixin`），竹子也不能秒破。
- 挖掘和空手一样：`BreakSpeed` 里除掉剑自己的挖掘速度（蜘蛛网 15，其他剑类方块 1.5）；`HarvestCheck` 里剑不能收获需要正确工具的方块（蜘蛛网掉线）。
- 撬棍、斧头、空手、未来的近战武器都不在标签里，不受影响（GameTest 检查撬棍和铁斧的属性还在）。
- AFL 已经删掉副手；属性清空对任何槽位都生效，所以快捷栏、副手、盔甲槽都绕不过去。

### 4.2 盾牌

- 举不起来：`LivingEntityUseItemEvent.Start` 取消。
- 正在格挡的也作废：`ShieldBlockEvent` 取消。
- 主手、副手一样处理。将来的防暴盾是 AFL 自己的物品，不在标签里，可以照常实现格挡。

### 4.3 盔甲

- 装备槽策略：**允许穿戴，但完全没有防护**（当作历史物品）。没选"禁止穿戴"的理由：
  - 禁止穿戴要么弹出装备、要么拦截装备操作，都会碰到旧存档里已经穿着的盔甲，容易丢物品或卡物品；
  - 绑定诅咒的盔甲脱不下来；现在附魔等级读出来是 0，可以正常脱下；
  - 渲染、尸体、AFL 背包和 Curios 都不受影响；开关一关立即恢复。
- 护甲值、盔甲韧性、击退抗性：属性清空，玩家和生物都一样。
- 附魔：保护类、摔落保护、荆棘、水下呼吸、深海探索者、冰霜行者、灵魂疾行、耐久、绑定 / 消失诅咒都读成 0。NBT 不动。
- 特殊效果：
  - 皮革防冻（`mixin/RetiredFreezeImmunityMixin`）和皮靴走细雪（`mixin/RetiredPowderSnowMixin`）取消；
  - 海龟壳水下呼吸取消（`mixin/RetiredTurtleHelmetMixin`）；
  - 保暖为 0：`temperature/ThermalInsulation.of` 对退役装备返回 0。`thermal_insulation/armor_v1.json` 保留，开关关掉时还会用。
  - 猪灵对金甲中立：下界已关，不处理。
- AFL 装备（胸挂、背包、护耳罩、Curios 槽）不在标签里，不受影响。

### 4.4 附魔和魔法

| 内容 | V1 处理 |
|---|---|
| 附魔台 | 配方移除；生存 / 冒险模式拒绝交互，提示"无法使用" |
| 铁砧（三种） | 同上 |
| 酿造台 | 同上；另外 `PotionBrewEvent.Pre` 取消，漏斗喂料的酿造台也不会完成酿造 |
| 砂轮 | 同上（砂轮会去附魔并给经验） |
| 锻造台 | 同上；`smithing_trim` 配方类型移除；下界合金剑 / 盔甲的升级配方因输出退役而移除；下界合金工具升级配方保留，但锻造台用不了 |
| 末影箱 | 配方移除；生存 / 冒险模式拒绝交互。**不放进战利品过滤**，否则拆末影箱会让方块本身消失。旧末影箱里的东西留在玩家数据里，不删 |
| 附魔书、附魔之瓶 | 不可获取（配方、战利品、交易） |
| 经验 | 沿用原有规则（获得经验归零、经验球不能拾取、生物和方块不掉经验、登录清零），现在也受总开关控制 |

提示文字：`message.apocalypse_firstlight.retired_workstation`，en "Can't be used"，zh "无法使用"（快捷栏上方）。

### 4.5 下界和末地

- `closed_dimensions` 里的维度：下界传送门不会点亮（`BlockEvent.PortalSpawnEvent` 取消），任何实体都不能通过传送门进去（`EntityTravelToDimensionEvent` 取消）。玩家看到"下界无法进入" / "末地无法进入"。代码 `world/WorldAccessEvents.java`。
- 维度本身仍在世界预设里（见第 9 节）。

### 4.6 原版木头（V1.1，2026-10-20）

AFL 有自己的六个树种（白栎、白松、红栎、粗皮山核桃、红枫、东部铁杉，网格树），原版树种整套退役。通用木制品（原料只能是原版木板 / 原木的东西）也一起退役，V2 制造台上线时用 AFL 木材配方补（用户选的方案 B）。

- **清单**：`retired/wood.json`（树种，206 个）和 `retired/wood_products.json`（通用木制品，44 个），都接进 `unobtainable` 和 `creative_hidden`。清单是从原版 1.20.1 的物品标签（`logs`、`planks`、`wooden_*`、`fence_gates`、`signs`、`hanging_signs`、`boats`、`chest_boats`、`saplings`、`leaves`、`bamboo_blocks`）展开成**明确的 ID** 写进去的，不直接引用原版标签：以后 AFL 木板接进 `minecraft:planks` 时不能被一起退役。
- **效果**（和装备一样走第 2 节的三条规则）：
  - 输出这些物品的配方全部移除（木板、木棍、工作台、箱子、火把、木工具、船、门、栅栏……），客户端 JEI 和配方书也看不到；
  - 战利品、方块掉落、钓鱼不再给这些物品：**挖掉已经放下的原版木头方块不掉落**，原版树叶不再掉树苗和木棍（苹果照常）；
  - 创造栏隐藏，`/give` 照常能拿；
  - 物品本身**照常能用**：已有的木棍、火把、箱子、工作台能放能用，放下的工作台照常能合成（不进 `retired/workstations`）。
- **没退役**（配方大多因为缺木棍 / 木板实际做不了，但物品留着，等对应设计）：营火和灵魂营火（取暖、烧水、打火机能点）、弓、弩、箭、钓竿、胡萝卜钓竿、诡异菌钓竿、拉杆、绊线钩、铁轨类、活塞、阳光探测器、红石火把、旗帜。原版竹子（植物）也没退役。V1.2 起其中的营火、灵魂营火、弓、弩、箭、拉杆、绊线钩、铁轨类、活塞、阳光探测器、红石火把、旗帜也退役了（4.7 节）。
- **世界生成**：
  - 网格森林原来覆盖 11 个群系；联邦世界的 `alpine_meadow`（高山草甸）和 `lake_fen`（湖滨沼泽）还在长原版云杉、白桦，这次加进网格森林（`tools/federal-worldgen/build_federal_data.py` 的 `MESH_TREES`，生成 `forge/biome_modifier/mesh_forest.json`），`tree/ForestPlan` 加两个**过渡**林分，按原来的密度：高山草甸边缘零星白松–铁杉（约 0.15 棵 / 区块），湖滨沼泽零星红枫–铁杉–白松（约 0.25 棵 / 区块）。沼泽地表大多是泥，网格树不长在泥上，所以实际会更少（没实测）。真正的林分（矮曲林云杉–冷杉、美洲落叶松、黑云杉）等以后加树种。
  - 繁茂洞穴的杜鹃树（`minecraft:rooted_azalea_tree`）用 `forge/biome_modifier/remove_lush_cave_azalea_trees.json` 去掉。苔藓地上零星的杜鹃灌木（苔藓斑块的一部分）没去掉，挖掉不掉落。
  - 原版结构本来就全关了（第 9 节）。
  - 只影响新生成的区块；旧区块里已有的原版树留着，砍了不掉落。
  - `meshForestEnabled` 关掉时（开发用）所有群系回到原版树。
- **结构**：AFL 自己的结构 NBT 里的原版树种方块换成 AFL 方块（只改调色板里的方块 ID，属性不变）：
  - `structures/bunker.nbt`：云杉活板门、深色橡木活板门 → 铁活板门（桌面、柜门，本来就不用开关）；云杉楼梯 → `white_oak_stairs`；去皮云杉木 → `white_oak_rounded_timber`；盆栽橡树苗 → 盆栽蕨；橡木压力板 → 石质压力板。
  - `structures/forest_home_b.nbt`：一个云杉台阶 → `white_oak_slab`。
  - 剩下的通用木制品方块没换（AFL 还没有对应方块）：地堡的床、木桶、书架、织布机、蜂箱、音符盒，老宅的梯子。它们照常能用，挖掉不掉落。以后有 AFL 梯子、床等再换。
- **以后的建筑**（用户 2026-10-20）：不能再用原版树种的方块，只用 AFL 树种或其他 AFL 材料；缺零件（例如某个树种的活板门）就补 AFL 方块，不退回原版。写进了 `docs/authoring/afl_industrial_palette_v1.md`。
- **代码里借用原版木头的地方保留**（不会到玩家手里）：`cabin/CabinBlocks` 找不到方块时的兜底木板、商用木门复制橡木门的方块属性、砍树时的原版去皮原木粒子（`tree/FellingServer`）、着色器材质 ID 用原版树叶和原木（`tree/TreeShaderIds`）。
- **生存里的影响**：新世界里暂时做不出木棍、工作台、箱子、火把、木工具。等 V2 的背包手工制造和工匠台。
- 2026-10-20 Main 通知：用户要求删掉 AFL 的 6 个工作台配方（体温计、腕式温度计、简易护耳罩、两种路缘石、铅屏蔽砖），master 上已删。本分支没改过这 6 个文件，合并时以 master 的删除为准；所以"3×3 配方要有工作台"这件事已经不存在。

### 4.7 原版内容大清理（V1.2，2026-10-20）

用户 2026-10-20 一次列了一批："海晶类、下界类方块、末地方块、青金石系列（包括块 / 矿物生成）、潜影盒、红石（不符合世界观）、幽匿系列、铁砧 / 附魔台、信标、原版的所有功能性方块 / 容器、原版的弓 / 弩 / 箭"。追问后补充：熔炉、高炉、烟熏炉、营火**全部退**（"等删完之后我们就可以开始做自己的功能性方块了，所以配方不要紧"）；铁轨和矿车**全部退**（以后做网格铁路网）；"目前不需要刷出任何原版生物……最好把刷怪蛋那一页也隐藏掉"。

**物品清单**（都接进 `unobtainable` 和 `creative_hidden`，效果和 4.6 节一样：配方按输出移除、战利品和方块掉落不给、创造栏隐藏、`/give` 照常能拿、已有的照常能用、已经放下的照常能用但挖掉不掉落）。和木头一样写**明确的原版 ID**，用原版 1.20.1 的物品模型逐个核对过存在：

| 文件 | 个数 | 内容 |
|---|---|---|
| `retired/nether.json` | 77 | 下界岩、灵魂沙 / 土、玄武岩、黑石全家（含按钮、压力板）、下界砖和红色下界砖全家、石英（矿、石英、石英块全家、平滑石英全家）、下界金矿、疣块、菌岩、下界植物、菌光体、萤石和萤石粉、岩浆块、哭泣的黑曜石、重生锚、磁石、远古残骸、下界合金（碎片、锭、块、锻造模板、镐 / 斧 / 锹 / 锄）、灵魂灯笼、灵魂营火、下界之星。绯红 / 诡异木在 V1.1 的 `wood.json` 里 |
| `retired/end.json` | 21 | 末地石和末地石砖全家、紫珀全家、末地烛、紫颂植物 / 花 / 果 / 爆裂紫颂果、末地传送门框架、末影之眼、末影水晶、龙蛋、龙首、龙息、鞘翅 |
| `retired/shulker_boxes.json` | 18 | 潜影盒 + 16 色、潜影壳 |
| `retired/prismarine.json` | 14 | 海晶石、海晶石砖、暗海晶石全家、海晶灯、海晶碎片、海晶砂粒、潮涌核心 |
| `retired/lapis.json` | 4 | 青金石、青金石块、青金石矿、深层青金石矿 |
| `retired/redstone.json` | 31 | 红石粉、红石块、红石矿 ×2、红石火把、红石灯、中继器、比较器、活塞 ×2、侦测器、漏斗、发射器、投掷器、拉杆、石按钮、石质 / 轻质 / 重质压力板、绊线钩、标靶、阳光探测器、四种铁轨、五种矿车（普通、运输、动力、漏斗、TNT） |
| `retired/sculk.json` | 8 | 幽匿块、脉络、催发体、尖啸体、感测体、校频幽匿感测体、回响碎片、追溯指针 |
| `retired/functional.json` | 77 | 熔炉、高炉、烟熏炉、营火；附魔台、铁砧 ×3、酿造台、砂轮、锻造台、末影箱（V1 只拒绝交互，这次也拿不到、创造栏隐藏）；切石机、炼药锅、钟、信标、灯笼；蜡烛 + 16 色；旗帜 16 色和 6 种旗帜图案；花盆、饰纹陶罐、可疑的沙子 / 沙砾、蜂巢；6 种头颅；3 种蛙明灯；7 种虫蚀方块 |
| `retired/ranged.json` | 5 | 弓、弩、箭、光灵箭、药箭 |
| `retired/spawn_eggs.json` | 78 | 原版全部 77 种刷怪蛋和刷怪笼（原版 jar 里的 `template_spawn_egg` 只是模型模板，不是物品） |

**有意保留**（在原版功能性 / 红石页里，但不退；V1.3 起下面第二、三条里除荧光地衣、黏液块、蜂蜜块以外都已退役，见 4.8 节；V1.5 起这三样也退了，见 4.10 节）：
- 原版玻璃全部（含功能页里的遮光玻璃）：Main 2026-10-20 回复建议等 AFL 玻璃板做好再一起退，同一轮把感染者破窗、子弹打碎玻璃、感染者视线（`infected/vision/VisionOcclusionRules`）、温度遮蔽（`temperature/TemperatureShelter`）里写死的原版玻璃改成一个 AFL 标签。现在就退的话新建筑暂时没有玻璃板可用。
- TNT（打火机能点；现实里有炸药，以后可能换成 AFL 炸药）、避雷针（现实物件，闪电系统以后会用到金属）、铁门和铁活板门（建筑件，地堡在用；没有红石就打不开，以后做 AFL 门锁）、锁链、荧光地衣（洞穴植物）。
- 指南针、时钟（现实物件；配方要红石，所以生存里做不出）、黏液块、蜂蜜块、钓竿。
- 营火、蜡烛这些打火机能点的东西：物品退了，已经放下的照常能点（TNT 没退）。

**矿物生成**：`forge/biome_modifier/remove_retired_ores.json`（`forge:remove_features`，`biomes` 用 `forge:any`，步骤 `underground_ores`）去掉 `minecraft:ore_lapis`、`ore_lapis_buried`、`ore_redstone`、`ore_redstone_lower`。20 个联邦群系 JSON 里还写着这四个特征（C 的生成器输出），没改它们，由修改器统一去掉。只影响新区块；旧区块里的矿石留着，挖了不掉落。海底岩浆块（`underwater_magma`）照常生成，挖了不掉落。AFL 群系里没有幽匿特征（没有深暗之域）。

**原版生物**（`tags/entity_types/retired/mobs.json`，78 种：每种刷怪蛋对应的生物 + 末影龙、凋灵、幻术师、巨人，去掉 `minecraft:zombie`）：
- AFL 的感染者就是 `minecraft:zombie` 加 AFL 的 AI（`infected/InfectedEntityRules`），所以僵尸保留；尸壳、溺尸、僵尸村民都退。
- `world/VanillaRetirementEvents`：
  - `EntityJoinLevelEvent`（最高优先级，只在服务端）：清单里的生物**新进入世界一律取消**，不管从哪来：自然生成、区块生成、刷怪笼、刷怪蛋、`/summon`、繁殖、袭击、玩家搭的傀儡、闪电和转化。从存档加载的（`loadedFromDisk`）照常加载，旧存档里已有的原版生物不删。
  - `MobSpawnEvent.SpawnPlacementCheck`：自然生成和刷怪笼在建出生物之前就拒绝，省掉白建一个实体。
  - `LivingConversionEvent.Pre`：转化成清单里的生物一律取消，原来的生物保持原样（泡水的僵尸不会变成溺尸，600 tick 后再试一次，仍然被取消）。
- 群系 JSON 里的刷怪表**没改**：怪物类本来就只放行僵尸（`world/WorldSpawnRules`），原版生物在放置检查时被拒绝，这样僵尸的实际密度和以前一样。如果从刷怪表里删掉别的怪，每次抽签都会抽到僵尸，感染者会变多。
- `world/WildlifeSpawnPolicy`（安全区放行原版动物、辐射区拒绝）在开关开着时实际不起作用了，原版动物在哪都不生成；开关关掉时它照旧。
- 开发：要原版生物时关掉总开关；GameTest 里生成牛、猪、羊、尸壳等原版生物的测试（`NativeHeadshotGameTests`、`NativeNoiseRegressionTests`、`InfectedAiPerformanceGameTests`）在开关开着时会生成失败，需要在测试里 `setTestOverrideOff(true)`（**没改**，本轮不跑 GameTest）。

**刷怪蛋页**：77 种刷怪蛋和刷怪笼都进了 `creative_hidden`，AFL 没有往这一页放东西，这一页空了，原版创造栏就不显示它（`CreativeModeTab#shouldDisplay`：空的分类页不显示）。没进游戏看过。

**结构**：AFL 结构里已经放下的原版功能方块没换（AFL 还没有对应方块），照常能用、挖掉不掉落：地堡里的熔炉 ×2、烟熏炉、炼药锅（有水）、钟、织布机、音符盒、木桶、蜂箱、书架、床、6 个盆栽、绊线钩、石质压力板，以及 V1 已经拒绝交互的铁砧、酿造台。地堡的熔炉成了新世界里少数能烧东西的地方。老宅只有梯子，加油站没有原版方块。

**生存里的影响**：
- 新世界里烧东西只剩柴炉（做饭、烧水，用烟熏炉配方）、要电的工业熔炉，和结构里已经放下的原版熔炉；原版的粗铁 → 铁锭在没电时做不了。用户说配方不要紧，等 AFL 自己的功能性方块。
- 没有原版动物：没有肉、皮、羊毛、蛋、牛奶，钓鱼照常（鱼是战利品，不是生物）。
- 没有骷髅、苦力怕、蜘蛛（本来就只刷僵尸），也没有村民、流浪商人、袭击。

### 4.8 工具、食物和剩下的原版件（V1.3，2026-10-20）

用户 2026-10-20 原话："TNT / 原版铁门/活版门/避雷针/遮光玻璃 / 锁链/原版的全部食物和药水 / 不死图腾/三叉戟/所有级别的斧头/马鞍/烟花火箭 / 原版的所有工具/各种桶装液体/烈焰弹/骨粉 / 末影珍珠 / 山羊角 / 唱片 / 紫水晶和洞穴也可以删除 / 我们后面会做自己的工具，按照真实逻辑"。追问后定：洞穴只删紫水晶晶洞，普通地下洞穴和峡谷照常；没有食物以后饥饿**照常**（会饿死，等 AFL 食物）。

**物品清单**（同样接进 `unobtainable` 和 `creative_hidden`，效果和 4.6、4.7 节一样）：

| 文件 | 个数 | 内容 |
|---|---|---|
| `retired/misc_blocks.json` | 7 | TNT、铁门、铁活板门、避雷针、遮光玻璃、锁链；V1.6 加了铁栏杆（4.11 节） |
| `retired/amethyst.json` | 7 | 紫水晶块、紫水晶母岩、紫水晶簇、大 / 中 / 小紫水晶芽、紫水晶碎片 |
| `retired/tools.json` | 73 | 石 / 铁 / 金 / 钻石的斧、镐、锹、锄（木和下界合金的已在 V1.1、V1.2 清单里）；剪刀、打火石、烈焰弹、钓竿、胡萝卜钓竿、诡异菌钓竿、刷子、指南针、时钟、望远镜、地图（空的和已填的）、拴绳、命名牌、收纳袋、书与笔、成书；桶和 10 种桶装（水、岩浆、牛奶、细雪、美西螈、鳕鱼、鲑鱼、河豚、热带鱼、蝌蚪）；骨粉、末影珍珠、山羊角、烟花火箭、烟火之星、马鞍、4 种马铠、不死图腾、三叉戟；17 张唱片和唱片残片 |
| `retired/food.json` | 45 | 原版全部能吃的东西（水果、蔬菜、生熟肉和鱼、面包、饼干、蛋糕、南瓜派、炖菜和汤、腐肉、蜘蛛眼、蜂蜜瓶……）、药水、喷溅药水、滞留药水、发酵蛛眼、闪烁的西瓜片。紫颂果在 `end.json` |

**没退**：玻璃瓶（口渴系统装水要用，算在玻璃那一轮）、原版玻璃和玻璃板、铁栏杆（V1.6 起退役）、小麦、种子、甘蔗、鸡蛋、雪球、荧光地衣、黏液块、蜂蜜块。

**紫水晶晶洞**：`forge/biome_modifier/remove_amethyst_geodes.json`（`forge:remove_features`，`forge:any`，步骤 `local_modifications`）去掉 `minecraft:amethyst_geode`；20 个联邦群系 JSON 没动。只影响新区块。普通地下洞穴（雕刻器 `cave`、`cave_extra_underground`、`canyon`）照常。

**代码和数据**：
- `world/VanillaRecipePruningEvents` 按输出删配方，特殊配方（烟花、地图复制、谜之炖菜、药箭、陶罐、潜影盒染色、旗帜复制、书复制）`getResultItem` 是空的，删不掉，所以写进 `vanilla_retirement/v1.json` 的 `remove_recipes`。`repair_item`（两件同种工具合成修复）没删，工具都拿不到了，留着无害。
- 战利品修改器会过滤僵尸掉的腐肉、胡萝卜、马铃薯，以及钓鱼钓上来的鱼；铁锭照常掉。
- 僵尸出生时随机拿的铁锹（原版规则）会被 `RetiredSpawnGearMixin` 拿掉，因为它现在拿不到了。
- 口渴系统的玻璃瓶装水在 `thirst/PlayerThirst` 里直接换成 AFL 的水瓶，不经过原版药水（水瓶），所以退役原版药水不影响装水。
- AFL 的工业废水桶用过以后还是退回原版空桶（`craftRemainder`），桶本身拿不到也不影响。
- 已经放下的 TNT 照常能用打火机点。

**生存里的影响**（用户接受，等 AFL 自己的工具和食物）：
- **挖不了矿、挖不了石头**：原版镐全退了，AFL 还没有自己的镐；要正确工具才掉落的方块（石头、矿石、工业方块）在新世界生存里挖不出东西。空手能挖的泥土、沙子、沙砾照常。
- **砍不了网格树**：砍树要斧（`tree/FellingServer`，Forge 的 `AXE_DIG`），原版斧全退了，AFL 没有斧，新世界生存里拿不到 AFL 原木。
- **钢门回收不了**：`tags/items/industrial_material_recovery_tools.json` 只有钻石镐和下界合金镐（`block/SteelDoorBlock`）。
- **没有食物，会饿死**：饥饿照常下降。口渴照常（AFL 的水）。
- 没有桶，流体照旧只能靠 AFL 的储罐（原版桶本来就关了）。
- 背包里、存档里已有的工具和食物照常能用。

### 4.9 砖、砂岩、深板岩建筑件、铜建筑件、钻石和绿宝石（V1.4，2026-10-20）

用户 2026-10-20 原话："接下来退役砖块/强化深板岩/砂岩/泥砖……原版的钻石/绿宝石什么的可以删掉了吧"。铜和深板岩问了 Claude 的意见，用户选了建议：铜只退建筑件，铜矿、粗铜、铜锭留着（AFL 的电线要铜；现实里不会用实心铜块盖房子）；深板岩只退装饰件，天然深板岩和深层矿石等地下重做时再定。

**物品清单**（接进 `unobtainable` 和 `creative_hidden`，效果同前）：

| 文件 | 个数 | 内容 |
|---|---|---|
| `retired/masonry.json` | 50 | 红砖（砖、砖块、楼梯、台阶、墙）；砂岩和红砂岩全家（普通、錾制、切制、平滑及其楼梯、台阶、墙）；泥坯、泥砖全家；强化深板岩；深板岩圆石、磨制深板岩、深板岩砖、深板岩瓦全家（含裂纹、錾制） |
| `retired/copper_blocks.json` | 33 | 铜块、切制铜块、切制铜楼梯、切制铜台阶，四种氧化程度 × 打蜡 / 不打蜡；粗铜块 |
| `retired/gems.json` | 8 | 钻石、钻石块、钻石矿、深层钻石矿、绿宝石、绿宝石块、绿宝石矿、深层绿宝石矿。钻石工具、盔甲、马铠在前面的清单里 |

**矿物生成**：`forge/biome_modifier/remove_retired_ores.json` 加了 `minecraft:ore_diamond`、`ore_diamond_large`、`ore_diamond_buried`、`ore_emerald`（联邦群系本来没有绿宝石矿，写上是给原版世界类型）。只影响新区块。

**没退 / 照常生成**：
- 天然深板岩（y=0 以下那一层）和所有深层矿石照常生成；挖天然深板岩本来掉深板岩圆石，圆石退役以后**不掉落**（精准采集除外）。原版镐 V1.3 起也拿不到了。
- 天然砂岩、红砂岩照常生成：联邦噪声设置 `worldgen/noise_settings/federal_experimental.json`（C 的生成器 `tools/federal-worldgen/build_federal_data.py` 从原版主世界复制来的地表规则）在沙子下面铺砂岩、在恶地铺红砂岩，没改，挖了不掉落。改地表规则归 C，和地下重做一起定。
- 泥（`mud`）照常；石砖、安山岩 / 闪长岩 / 花岗岩建筑件、陶瓦、混凝土没退。
- 铜矿、深层铜矿、粗铜、铜锭保留；避雷针 V1.3 已退。

**代码**：只有识别用的引用，不受影响：`worldgen/roads/construction/RoadConstructionPlanner` 认矿石和砂岩，`registry/AflBlocks` 四个砖墙方块复制 `Blocks.BRICKS` 的方块属性，`vehicle/physics/LevelVehicleWorld` 按名字里的 sandstone / brick 定轮胎抓地力。AFL 结构里没有这批方块。

### 4.10 生物掉落、考古、奇幻植物、珊瑚、蘑菇方块、储存方块和装饰方块（V1.5，2026-10-20）

用户 2026-10-20 问"原版还有什么东西不符合世界观"，Claude 列了"建议退"和"等地下重做"两组；用户原话："做 V1.5：建议退的那一组全部退役，世界里会生成的一起去掉"。骨块、天然方块、石砖、陶瓦、混凝土等在"等地下重做 / 先留着"那一组，这一轮没动。

**物品清单**（接进 `unobtainable` 和 `creative_hidden`，效果同前）：

| 文件 | 个数 | 内容 |
|---|---|---|
| `retired/mob_drops.json` | 11 | 烈焰棒、烈焰粉、岩浆膏、恶魂之泪、幻翼膜、兔子脚、海洋之心、鹦鹉螺壳、鳞甲、荧光墨囊、黏液球 |
| `retired/archaeology.json` | 36 | 20 种陶片、16 种盔甲纹饰锻造模板（从原版物品模型按后缀 `_pottery_sherd`、`_armor_trim_smithing_template` 展开） |
| `retired/nature.json` | 50 | 凋零玫瑰、火把花和种子、瓶子草和荚果、孢子花、荧光地衣、大 / 小垂滴叶、粉红色花簇、海泡菜；嗅探兽蛋、青蛙卵、海龟蛋；5 种珊瑚 × 珊瑚块 / 珊瑚 / 珊瑚扇 × 活的 / 失活的（30 个）；海绵、湿海绵；棕色 / 红色蘑菇方块、蘑菇柄、菌丝 |
| `retired/storage_blocks.json` | 5 | 铁块、金块、煤炭块、粗铁块、粗金块 |
| `retired/decor_blocks.json` | 19 | 蓝冰、黏液块、蜂蜜块、16 色带釉陶瓦 |

**世界生成**：`forge/biome_modifier/remove_fantasy_features.json`（`forge:remove_features`，`forge:any`），20 个联邦群系 JSON 没动，只影响新区块：

| 特征 | 生成步骤 | 放的是什么 |
|---|---|---|
| `minecraft:glow_lichen` | `vegetal_decoration` | 荧光地衣（联邦 19 个群系都有） |
| `minecraft:spore_blossom` | `vegetal_decoration` | 孢子花（繁茂洞穴） |
| `minecraft:lush_caves_clay` | `vegetal_decoration` | 带垂滴叶的黏土片和黏土水池（繁茂洞穴；垂滴叶只在这里生成） |
| `minecraft:warm_ocean_vegetation`、`sea_pickle` | `vegetal_decoration` | 珊瑚礁、海泡菜（暖水海洋） |
| `minecraft:mushroom_island_vegetation` | `vegetal_decoration` | 巨型蘑菇（蘑菇岛） |
| `minecraft:flower_cherry` | `vegetal_decoration` | 粉红色花簇（樱花树林） |
| `minecraft:fossil_upper`、`fossil_lower` | `underground_structures` | 化石（骨块、煤矿、钻石矿） |
| `minecraft:iceberg_blue`、`blue_ice` | `local_modifications`、`surface_structures` | 蓝冰冰山、蓝冰块（冰冻海洋）；浮冰冰山照常 |

除荧光地衣外这些特征都只在原版群系里（联邦群系本来没有），写上是给原版世界类型。火把花、瓶子草、嗅探兽蛋、青蛙卵、海龟蛋、凋零玫瑰在世界里只靠生物或考古出现，生物 V1.2 已拦截、刷子 V1.3 已退役，可疑的沙子和沙砾掉的东西也经过战利品修改器。

**没退 / 照常**：
- 小蘑菇（棕色、红色）照常生成。
- 菌丝只在原版蘑菇岛的地表规则里，联邦群系没有；原版世界类型里的蘑菇岛照常铺菌丝，挖了不掉落。
- **矿脉里的粗铁块**：联邦噪声设置 `worldgen/noise_settings/federal_experimental.json` 开着 `ore_veins_enabled`（原版大型矿脉，铁矿脉 y −60 到 −8、铜矿脉 y 0 到 50），矿脉里会夹粗铁块和粗铜块（粗铜块 V1.4 已退），挖了不掉落。改噪声设置归 C，和地下重做一起定。
- 骨块、黏土、苔藓、发光浆果藤蔓、浮冰、冰、雪照常。

**代码和数据**：只有识别用的引用，不受影响：`authoring/BuildingAuthoringService` 把海绵算作非建筑方块；`world/bunker/BunkerPlacementManager`、`worldgen/roads/RoadTerrainQuery` 认蓝冰是冰面；`worldgen/home/ForestHomeChunkPass`、`worldgen/network/RoadChunkPass`、`client/vehicle/TruckSounds` 认菌丝是草地；`registry/AflBlocks` 84 个方块复制 `Blocks.IRON_BLOCK` 的方块属性；`temperature/temperature_v1.json` 蓝冰是冷源；`tags/blocks/no_wiring.json` 有黏液块和蜂蜜块；`item_mass`、噪声标签里有它们的条目。热力发电机（`energy/MachineBalanceManager`、`machine_balance/thermal_generator.json`、`client/ThermalGeneratorRenderer`）还认煤炭块当燃料，煤炭块拿不到以后只剩煤和木炭，已有的煤炭块照常能烧。AFL 结构（`.nbt`）里没有这批方块。

**配方**：按输出删掉铁块、金块、煤炭块、粗铁块、粗金块、黏液块、蜂蜜块、蓝冰、16 色带釉陶瓦、烈焰粉、岩浆膏、黏液球、海绵（湿海绵烧干）等；用铁块拆铁锭（`iron_ingot_from_iron_block`）这类输出没退役的配方留着，只是材料拿不到。用凋零玫瑰、火把花、瓶子草、粉红色花簇做染料的配方同理留着。

### 4.11 铁栏杆、附魔书和创造栏清理（V1.6，2026-10-20）

用户 2026-10-20 看了 V1.5 的创造栏以后，原话："可以删掉原版的铁栏杆 / 鸡蛋和雪球可以留着，但是不要再让战斗物品单独放两个物品了 / 然后删掉原版所有的附魔书/附魔瓶 / 红石方块里面也不要放羊毛和线了，删除这个标签页 / 然后把我们的AFL标签页往第一页移动，自动补齐 / 左边也可以放标签页"。

- **铁栏杆**：加进 `retired/misc_blocks.json`（现在 7 个），做不出来、不掉落、创造栏隐藏。AFL 的钢栏杆 `apocalypse_firstlight:steel_railing`（`registry/AflBlocks`，复制原版铁栏杆的方块属性）照常。AFL 结构（`.nbt`）里没有原版铁栏杆。
- **附魔书、附魔之瓶**：V1 起就拿不到（在 `unobtainable` 里），但原版原料页还摆着全部附魔书和附魔之瓶；现在也加进 `creative_hidden.json`，创造栏和搜索里都没有了。
- **战斗页、红石页**：其他东西退役以后，战斗页只剩雪球和鸡蛋，红石页只剩白色羊毛和线，这几样原料页、彩色方块页里本来就有。`client/VanillaRetirementClient.CreativeTabs` 在开关开着时把这两页里的原版物品全部拿掉，空页自己不显示；雪球、鸡蛋、线还在原料页，羊毛还在彩色方块页。其他模组加进这两页的物品不动。
- 标签页排布见 7.1 节。

### 4.12 只有工具能挖（V1.8，2026-10-23）

用户 2026-10-23 原话："禁止空手直接挖掘任何东西"，随后："拿着不是工具的东西也不能挖，只有工具能挖"，实机看过后："接下来直接阻止玩家在没有正确的工具下挖掘，包括播放音效和粒子，以及手臂动作，不会有任何的事情发生"。（V1.7 的编号已被土方 R1c 的原版石头建材退役用掉。）

**状态：** 两轮都做完，用户 2026-10-23 实机 PASS，提交 9c6f24b2。

- **规则：** 生存和冒险模式下，主手不是工具（空手，或者拿着方块、食物、材料、枪、书这类东西）时左键对着方块什么都不发生：不开始挖、没有裂纹、没有敲击声、没有碎屑粒子、手臂不挥、没有提示，草、花这类一碰就碎的方块也一样。拿着工具照旧挖，速度和掉落按原来的规则（AFL 工具不是原版挖掘工具的，速度和空手一样）。创造模式和旁观照旧。跟着总开关 `vanilla_retirement.enabled` 走。
- **什么算工具：** 物品标签 `apocalypse_firstlight:tools/digging`（`data/apocalypse_firstlight/tags/items/tools/digging.json`），改这个文件就能加减。现在是原版的镐、斧、锹、锄四类标签和剪刀（都已退役拿不到，只照顾旧存档），加上 AFL 的撬棍、圆头铲、鹤嘴锄、伐木斧、羊角锤、建筑锤、手锯、折刀、一字螺丝刀、布线扳手。剑已退役、挖掘和空手一样，不算工具；枪、打火机、钥匙、书不算。
- **客户端（`client/NoToolDigClient`，第二轮）：** `InputEvent.InteractionKeyMappingTriggered` 最后处理（LOWEST），左键、准星对着方块、`cannotDig` 时取消并且不挥手（`setSwingHand(false)`）；第一下（`startAttack`）和按住（`continueAttack`）都走这个事件，所以挖掘根本不开始，声音、粒子、挥手都没有，也不发包给服务端。按住左键时从工具换成别的，正在挖的那一下中止（`stopDestroyBlock`）。枪、门、撬棍砸售货机、环绕镜头转身这些先拿走左键的照旧；对着实体、对着空气照常挥。环绕镜头里按住左键人物仍会先转向中心点。
- **服务端（`world/VanillaRetirementEvents`）：** `cannotDig(player)`，挡住不经过上面客户端的情况（改过的客户端、别的模组）。`PlayerEvent.BreakSpeed` 最先处理（HIGHEST），不是工具就取消，速度变成 -1，客户端和服务端都不长进度，后面的耐力、骨折处理也收不到（空挥不扣耐力）；`BlockEvent.BreakEvent` 同样最先取消，兜住不经过进度的破坏。第一轮的动作栏提示「得用工具才能挖」按用户要求去掉了（语言键 `message.apocalypse_firstlight.needs_tool` 已删）。
- **受影响的玩法：** 拆自己放下的家具、灯具这类方块，现在手里得拿着工具；捡散石、喝生水、捡地上的物品是使用键，不受影响；砍树本来就要斧头，土方的地面本来就要铲子（拿着别的工具时仍提示要铲子，不是工具时什么都不发生）。
- **GameTest：** 按 AGENTS.md 挖掘审计"空手拆一次看掉不掉"的测试会和这条冲突，开关开着时会失败：`ForestHomeM2GameTests`（收音机空手拆、挖掘审计）、`ForestHomeM3aGameTests`（挖掘审计）、`FurnitureV2GameTests`（冰柜右半、床尾、储物箱右半空手拆）、`CommercialWallMountedSinkGameTests`、`CashRegisterIntegrationGameTests`、`ChestFreezerGameTests`（工具列表里的空手）。本轮没改、没跑，等用户定是改成"空手拆不动"还是测试时关掉这条。

### 4.13 原版植被（V1.9，2026-10-23）

用户 2026-10-23 原话："我指的是先退役原版植被，然后再设计各类植被 / 甘蔗什么的也可以做，但是要做afl的"，随后："先退役原版植被（P0）"。AFL 自己的植被见 [vegetation_v1_design.md](vegetation_v1_design.md)（P1 起）。

- **物品清单**：`retired/vegetation.json`，30 个明确 ID，接进 `unobtainable` 和 `creative_hidden`：草、高草、蕨、大型蕨、枯萎的灌木；12 种小花（蒲公英、虞美人、兰花、绒球葱、蓝花美耳草、4 种郁金香、滨菊、矢车菊、铃兰）、4 种高花（向日葵、丁香、玫瑰丛、牡丹）；甘蔗、睡莲、海草、海带、竹子、藤蔓、仙人掌、棕色和红色小蘑菇。甜浆果、发光浆果、干海带、骨粉在 V1.3 已退。
- **效果**（第 2 节三条规则）：战利品和方块掉落不再给这些物品（挖草不掉草，挖花不掉花）；创造栏隐藏，`/give` 照常能拿。没有原版配方输出它们，所以配方表不变；以它们为原料的配方（纸、糖、染料）留着，只是原料拿不到。
- **世界生成**：`forge/biome_modifier/remove_vanilla_vegetation.json`（`forge:remove_features`，`forge:any`，`vegetal_decoration` 步骤），54 个原版地物，ID 按 1.20.1 官方映射表 `VegetationPlacements` / `AquaticPlacements` 核对：`patch_grass_*`（8 个）、`patch_tall_grass*`、`patch_large_fern`、`patch_dead_bush*`、`patch_sunflower`、`flower_*`（含 `flower_forest_flowers`）、`forest_flowers`、`patch_berry_*`、`patch_waterlily`、`patch_sugar_cane*`、`patch_cactus_*`、`bamboo`、`bamboo_light`、`vines`、`brown_mushroom_*` / `red_mushroom_*`（下界的不算）、`seagrass_*`（9 个）、`kelp_cold`、`kelp_warm`。20 个联邦群系 JSON 没动，只影响新区块。和总开关无关（同 V1.5 的地物修改器），数据加载就生效。
- **没动的**：农作物和野生南瓜、西瓜（`patch_pumpkin`、`patch_melon*`，留给农业设计）；树和带树的混合地物（`trees_*`、`bamboo_vegetation`、`dark_forest_vegetation`）；洞穴植被（繁茂洞穴的苔藓、洞穴藤蔓、垂根、杜鹃丛，归地下重做）；`disk_grass`（草方块圆盘，是地面不是植物）。
- **结构**：`structures/bunker.nbt` 调色板里的盆栽兰花（2）、盆栽虞美人、盆栽蕨、盆栽滨菊、盆栽矢车菊 → `minecraft:flower_pot`（空花盆，6 个方块，只改调色板名字），有 AFL 植物以后再种回去。`forest_home_b.nbt`、`gas_station_01.nbt` 里没有原版植物（脚本读调色板确认）。
- **代码里认原版植物的地方保留**（只用来识别，旧存档里还有）：`block/FlattenedPlantBlock`（车压倒的草、蕨、花）、`survivor/SurvivorSpawns`（仙人掌算危险）、`world/bunker/BunkerPlacementHygiene`、`BunkerSurfaceIntegration`、`dev/worldgen/GradedTurfGameTests`（直接放原版草做样本，不受影响）。
- **空窗期**：P1 / P2 之前新世界地面只有草皮，没有地上植物（植被设计问题 1，用户同意先单独做 P0）。
- **GameTest**：没改。`VanillaRetirementGameTests` 的配方清单只列被移除的配方，这批没有被移除的配方。

## 5. 获取途径覆盖

| 途径 | 处理 | 说明 |
|---|---|---|
| 配方 | 三条规则移除（第 2 节） | 客户端随配方同步一起去掉，JEI 和配方书也看不到 |
| 战利品箱、方块掉落、实体战利品表、钓鱼 | `retired_vanilla` 全局战利品修改器 | 只处理新生成的战利品；箱子里已有的、身上已有的不动 |
| 生物出生装备 | `mixin/RetiredSpawnGearMixin`：在 `Mob.populateDefaultEquipmentEnchantments` 开头脱掉随机出来的退役装备 | 只动出生随机这一步。`/summon` 写的装备、生物捡起的物品、旧区块里的生物不动 |
| 生物死亡掉落装备 | 不单独处理 | 出生时已经没有；捡起的玩家物品照常掉落，避免吞物品 |
| 村民、流浪商人交易 | 每个交易项包一层，结果在 `retired/unobtainable` 里就不生成这项 | 只影响新生成的交易；见第 10 节 |
| 世界结构 | 已有规则：原版结构几乎都关了（第 9 节） | |
| 原版树（V1.1） | 网格森林覆盖所有长树的 AFL 群系；繁茂洞穴杜鹃树去掉（4.6 节） | 只影响新区块 |
| 青金石矿、红石矿（V1.2） | `remove_retired_ores` 群系修改器 | 只影响新区块 |
| 钻石矿、绿宝石矿（V1.4） | `remove_retired_ores` 群系修改器 | 只影响新区块 |
| 荧光地衣、孢子花、垂滴叶、珊瑚、海泡菜、巨型蘑菇、粉红色花簇、化石、蓝冰（V1.5） | `remove_fantasy_features` 群系修改器 | 只影响新区块 |
| 原版草、花、蕨、甘蔗、睡莲、海草、海带、竹子、藤蔓、仙人掌、小蘑菇（V1.9） | `remove_vanilla_vegetation` 群系修改器 | 只影响新区块 |
| 紫水晶晶洞（V1.3） | `remove_amethyst_geodes` 群系修改器 | 只影响新区块 |
| 原版生物（V1.2） | 进入世界时取消、转化取消（4.7 节） | 存档里已有的照常加载 |
| 附魔装备 | 退役装备整件拿不到；附魔书拿不到 | 附魔的弓、钓竿、工具不在 V1 范围 |
| 进度奖励 | 原版进度已全部用 `forge:false` 关掉 | |

## 6. 旧存档

- 不删任何物品、方块、NBT。旧剑、旧盔甲、旧盾留在原处，没有效果；附魔留在 NBT 里。
- 已经放下的附魔台、铁砧等方块留着，生存模式打不开；能正常挖掉。
- 末影箱里的东西在玩家数据里，开关关掉或用创造模式还能拿到。
- 生物身上已有的装备不动。
- V1.1：已经放下的原版木头方块（含旧区块里的原版树、玩家用原版木板盖的房子）留着、照常能用，但挖掉**不掉落**；背包里已有的木头物品照常能用。
- V1.2：同上，已经放下的熔炉等功能方块、红石电路、铁轨照常工作，挖掉不掉落；旧区块里的青金石矿、红石矿留着，挖了不掉落；存档里已有的原版生物（包括玩家养的动物）照常加载，但不能繁殖出新的。
- V1.9：旧区块里已有的原版草、花、甘蔗等留在原地，打掉不掉落；打掉旧盆栽只掉花盆；右键把花取出来仍会给花（原版直接给物品，不经过战利品修改器），只有旧存档里的盆栽会这样，记进第 10 节的覆盖不到的入口。

## 7. 创造模式、开发和客户端隐藏

- 创造和旁观模式可以打开退役工作站，方便检查旧建筑。
- 装备规则在**所有模式**下都生效：用 `/give` 拿到退役物品后切换模式，规则照样生效，绕不过去。
- `/give` 保留。`/summon` 命令本身保留，但 V1.2 起召唤原版生物（僵尸以外）会被取消；要原版生物时关掉总开关。
- 创造栏隐藏（`client/VanillaRetirementClient.CreativeTabs`）：
  - 在客户端 MOD 总线订阅 `BuildCreativeModeTabContentsEvent`，优先级 LOWEST，把 `retired/creative_hidden` 里的物品从每个标签页拿掉；搜索页由各标签页汇总，所以搜索里也没有。
  - 只是外观，不是安全校验。服务端规则不依赖它。
  - 读的是这台客户端自己的 COMMON 开关，不走网络、不同步；改开关后重进世界生效。
  - 加载顺序晚于 AFL 的模组再往标签页里加的同类物品可能漏掉（已知限制）。
- V1.6 起：创造栏还拿掉附魔书和附魔之瓶（列在 `creative_hidden.json` 里），以及原版战斗页、红石页里的原版物品（4.11 节）。
- 提示行（`client/VanillaRetirementClient.Tooltip`）：退役装备多一行灰字 `tooltip.apocalypse_firstlight.retired_gear`，en "No effect when used or worn"，zh "使用或穿戴时没有任何效果"。

### 7.1 创造栏标签页排布（V1.6，2026-10-20）

`client/CreativeTabLayout` + `mixin/client/CreativeTabLayoutMixin`（`CreativeModeInventoryScreen`），和总开关无关，一直生效：

- **顺序**：Forge 排好的标签页里，先放 AFL 的（命名空间 `apocalypse_firstlight`），再放其他的（原版剩下的建筑方块、彩色方块、自然方块、原料，以及其他模组的）。
- **没有空位**：建页时跳过没有东西的标签页（`CreativeModeTab#shouldDisplay`），退役清空的原版页不再留空格子。
- **一页 15 个**：Forge 原来 10 个一页、只用上下两排；现在上排 5 个、左侧一列 5 个、下排 5 个，按这个顺序填。现在 AFL 10 个 + 原版 4 个，一页放得下，没有翻页按钮；多出来的照 Forge 的方式翻页。
- 搜索、快捷栏、物品栏、管理员物品这 4 个 Forge 固定页还在右边原来的位置，不参与排布。
- 左侧的标签用上排标签的贴图沿对角线翻过来画，开口朝面板，选中时和面板边框接上；可点范围 28 × 26，图标居中。JEI 的物品列表和书签避开这一列（`compat/jei/AflJeiPlugin` 的额外区域）。
- 只在打开创造栏时建页：建好以后才出现内容的标签页照 Forge 原来的位置画，重开创造栏就排进去。

## 8. 工业熔炉修复

`blockentity/IndustrialFurnaceBlockEntity.fromVanillaRecipe` 改用 `recipe.assemble(input, registryAccess)` 取结果，不再用 `getResultItem`。原因：口渴系统的烧水配方（`thirst/WaterBoilingRecipes`）按输入决定结果，带辐射的脏水应该煮成带辐射的开水；以前工业熔炉直接给净水，相当于洗掉了污染。普通原版配方结果不变。GameTest `industrialFurnace` 检查带辐射和不带辐射两种情况。

## 9. 已有拦截的整理（2026-10-19，用户要求）

以前做过的拦截核对后整理如下：

| 以前 | 现在 | 理由 |
|---|---|---|
| `VanillaRecipePruningEvents` 写死铁砧、附魔台两个配方 ID | 并进 `vanilla_retirement/v1.json` 的 `remove_recipes` | 一份清单 |
| `VanillaProgressionEvents` 写死附魔台和三种铁砧 | 改读 `retired/workstations` 方块标签 | 一份清单；创造 / 旁观放行是有意的改动 |
| `advancements/recipes/decorations/anvil.json`、`enchanting_table.json` 覆盖：去掉解锁配方的奖励 | **删除** | 配方已被移除，奖励解锁一个不存在的配方时原版直接忽略；而且开关关掉时这两个覆盖会让铁砧和附魔台配方解锁不了 |
| `WorldAccessEvents` 写死下界和末地，只拦玩家 | 读 `closed_dimensions`，受总开关控制；拦所有实体，只给玩家发提示 | 一份清单；用命令放的传送门也不能把生物和物品送进去 |
| `worldgen/structure_set/strongholds.json`：环形分布 128 个、频率 0 | **删除**，改为 `tags/worldgen/biome/has_structure/stronghold.json` 置空，和其他 26 个原版结构一样 | 频率 0 只是不生成，但世界加载时仍按环形算 128 个要塞位置，每个位置在半径 112 格内找生物群系（后台线程），第一个区块生成要等它算完（`ChunkGeneratorStructureState.getRingPositionsFor` 里 `join()`）。没有可放的生物群系时原版直接跳过整个计算。耗时**没有实测** |

保持不变的：

- 打火石配方 `forge:false`（2026-10-05 设计决定："没受过训练的幸存者不会打火"），不属于原版装备退役，不受总开关控制。
- 桶配方 `forge:false` 和 `remove_buckets` 战利品修改器（流体 1 mB = 1 L）。
- 110 个原版进度的 `forge:false` 覆盖。
- 26 个 `has_structure` 置空标签。

可选的下一步（**没做**，涉及世界预设，需要 Main / C 同意）：联邦世界预设 `worldgen/world_preset/federal_experimental.json` 里还带着下界和末地两个维度，每个世界都会建这两个空维度（存档里有 `DIM-1` / `DIM1`；推断每 tick 有少量开销，没有实测）。从预设里去掉只影响新世界，旧存档按 `level.dat` 照旧。去掉后传送拦截仍作为兜底。

## 10. 已知限制和覆盖不到的入口

- 运行中改开关：已穿戴装备的属性修饰符要换装或重进世界才刷新。
- 已经生成交易的村民（旧存档）保留原来的交易。AFL 世界从 2026-08-16 起不生成村庄，实际只剩治愈的僵尸村民。
- V1.9 旧存档里的原版盆栽：右键取花时原版直接把花塞给玩家，不经过战利品修改器，所以还能拿到原版花。新生成的地堡已经换成空花盆。
- 漏斗喂料的酿造台：酿造被取消，但燃料仍会扣掉（原版先扣燃料再发事件）。
- 附魔的弓、钓竿、工具（钓鱼宝藏、生物出生附魔）不在 V1 范围。
- 流浪商人的隐身药水、马铠、弓、箭、三叉戟、金苹果、不死图腾、末影珍珠不在 V1 范围。
- 直接调用 `ItemStack.getEnchantmentLevel`（Forge 方法）而不经过 `EnchantmentHelper` 的代码读得到附魔。AFL 自己只有 `tree/FellingServer` 这样读（斧头，不在退役范围）。
- 加载顺序晚于 AFL 的模组追加的创造栏条目可能漏网（只影响外观）。
- V1.1：用木棍 / 木板做、但输出没退役的配方（钓竿等）还在 JEI 里，只是材料拿不到（拉杆、铁轨、弓、箭、营火、旗帜 V1.2 起已退役）。
- V1.1：其他模组的木头不在清单里（清单是明确的原版 ID）。
- 旧 authoring 脚本 `tools/afl_minecraft_mcp/modern_glass_02_open_office.mjs`（办公楼 V2 测试世界用的）还写着云杉台阶、白桦门，没改；以后不要照它用原版木头。
- V1.2：输出没退役、但材料退役了的配方（指南针、时钟、铁门要红石……）还在 JEI 里，只是做不了。
- V1.2：原版生物被取消时，刷怪笼、刷怪蛋照样消耗一次（刷怪蛋在生存里本来拿不到）。
- V1.2：GameTest 里生成原版生物的测试在开关开着时会失败（4.7 节），没改。
- V1.3：`repair_item` 特殊配方保留；其他模组的工具和食物不在清单里（清单是明确的原版 ID），砍树认任何能 `AXE_DIG` 的模组斧。
- V1.2：城镇白模（C）有没有用到这次退役的原版方块没查（C 的分支）；已经通知 C。

## 11. 柴炉点火和 AFL 原木

- 柴炉只认打火石和火焰弹。打火石配方是 `forge:false`；火焰弹要烈焰粉，下界已关；两者都没有战利品表和开局物资。所以新世界生存里**柴炉点不着**（代码层面确认，未实机）。
- 2026-10-20 更新：加了翻盖打火机（[flip_lighter_v1.md](flip_lighter_v1.md)），点着的打火机能点柴炉（`BoxStove#igniter`，扣燃油）。但它现在也只能从创造栏拿，生存里照样点不着，等下面的获取渠道。
- V1 没有实现点火物，因为 AFL 现在没有任何获取渠道，任何方案都要碰别人的范围。可选方案：
  1. 开局物资给一盒火柴或打火机（D 的开局物资）；
  2. 老宅战利品里放（Main 的老宅）；
  3. V2 制造：打火机 / 火柴配方，和配方知识一起做。
- 网格森林的 AFL 原木没有配方，也不在 `minecraft:logs` 物品标签里。这是有意设计（`docs/rendering/mesh_trees_p2_felling.md`；`docs/dev/wood_system_plan_v1.md` §6 计划在 V2 做）。V1 不改标签。
- V1.1（2026-10-20）原版木头退役以后，原版原木也拿不到了：柴炉的燃料只剩 AFL 原木、树枝、木柴垛和 AFL 木制方块（`stove_fuel/*` 标签里的原版条目留着，已有的原版木头照常能烧）。

## 12. 替代装备方向（未实现，只记录）

- 剑 → 现代近战武器（撬棍已有，其他待做）。
- 盾 → 防暴盾、防弹盾。
- 盔甲 → 防弹衣、防护服、防辐射装备。普通防化服（挡污染、化学品）和辐射屏蔽（铅衬、挡外照射）要分开设计。

## 13. 测试

- `src/dev/java/com/antaurora/apofirstlight/dev/VanillaRetirementGameTests.java`，命名空间 `afl_vanilla_retirement_tests`，单独跑：

  ```text
  .\gradlew.bat runGameTestServer -PaflWithoutShaders -I src/dev/vanilla-retirement-gametest.init.gradle
  ```

  通过时日志有 `[AFL VANILLA RETIREMENT TEST] PASS`。**本轮没跑。**
- 覆盖：退役配方不在（V1.1 起包括原版木板、木棍、工作台、箱子、火把、木镐、梯子、床、书架、船、门、楼梯；V1.2 起包括熔炉、烟熏炉、高炉、营火、灯笼、信标、切石机、炼药锅、红石块、活塞、漏斗、铁轨、矿车、青金石块、石英块、紫珀块、海晶石、潜影盒、弓、弩、箭、旗帜、蜡烛、下界合金镐）、铁锭 / 钢坯 / 玻璃瓶 / 玻璃板配方在（V1.3 起石 / 铁工具、锁链、TNT、避雷针、铁门、铁活板门、遮光玻璃、紫水晶块、面包、蛋糕、饼干、金苹果、熟牛肉、剪刀、指南针、时钟、钓竿、望远镜、拴绳、烟花、骨粉的配方也要不在；V1.4 起红砖、砂岩、红砂岩、泥坯、泥砖、磨制深板岩、切制铜、粗铜块、钻石块、绿宝石块也要不在；粗铜烧铜锭要在；V1.5 起烈焰粉、岩浆膏、黏液球、铁块、金块、煤炭块、粗铁块、粗金块、黏液块、蜂蜜块、蓝冰、带釉陶瓦、海绵也要不在；V1.6 起铁栏杆也要不在）、没有纹饰配方、没有配方输出退役物品；剑和胸甲属性清空（含 NBT 属性），撬棍和铁斧不受影响；锋利、保护、附魔等级为 0，NBT 不变；没有横扫；剑挖蜘蛛网和空手一样、收获不了；盾牌举不起来；皮靴防冻和走细雪取消；皮甲保暖为 0；生存模式铁砧被拒绝、创造模式放行、已放下的工作台不受影响；战利品过滤（V1.3 起面包也被过滤，只剩铁锭；读真正的 `retired_vanilla.json`，并确认它在 `global_loot_modifiers.json` 里）；盔甲匠、武器匠、图书管理员交易不出退役物品和附魔书；酿造被取消；下界、末地关闭；工业熔炉保留污染；V1.2 新生成的牛被拒绝、僵尸照常进入世界（交易测试改成不把村民放进世界）；最后关掉开关，上面每一条恢复原版。
- 不覆盖、要进游戏看：V1.6 的创造栏排布（AFL 在前、没有空位、左侧一列、点击和悬停提示）、战斗页和红石页消失、原料页没有附魔书和附魔之瓶；V1.5 的创造栏隐藏（自然方块页少了珊瑚、奇幻植物、蘑菇方块，建筑页少了储存方块和带釉陶瓦）、新区块没有荧光地衣、孢子花、垂滴叶、珊瑚、化石、蓝冰；V1.3 的创造栏隐藏（工具、食物、战斗页基本清空）、新区块没有紫水晶晶洞、特殊配方在 JEI 里消失；V1.2 的创造栏隐藏和刷怪蛋页消失、新区块不再有青金石矿和红石矿、自然生成里没有原版动物、僵尸照常生成且密度不变、泡水的僵尸不变成溺尸；V1.1 的创造栏隐藏、原版木头方块挖掉不掉落、两个联邦群系和繁茂洞穴不再长原版树、地堡和老宅换过的方块朝向；自然生成的僵尸和骷髅不带退役装备、海龟壳、真实攻击下的盾牌、创造栏隐藏、提示行、工作站提示文字、要塞不再计算。

## 14. 代码和资源

- `world/VanillaRetirement.java`：标签、开关、`closed_dimensions`、判断方法。
- `world/VanillaRetirementEvents.java`：属性、盾牌、剑挖掘、交易、酿造；V1.2 原版生物（进入世界、放置检查、转化）；V1.8 只有工具能挖（`cannotDig`、`DIGGING_TOOLS`）。
- `client/NoToolDigClient.java`：V1.8 没拿工具时客户端不开始挖、不挥手。
- `world/VanillaRecipePruningEvents.java`：读清单、修剪配方。
- `world/VanillaProgressionEvents.java`：经验、工作站。
- `world/WorldAccessEvents.java`：下界、末地。
- `fluid/RemoveItemsLootModifier.java`：新增 `tag` 和 `retirement` 字段，`remove_buckets.json` 写法不变。
- `mixin/Retired*Mixin.java`（6 个）。
- `client/VanillaRetirementClient.java`：创造栏隐藏、提示行。
- `temperature/ThermalInsulation.java`：退役装备保暖为 0。
- `blockentity/IndustrialFurnaceBlockEntity.java`：`assemble` 修复。
- `ApocalypseFirstLight.java`：注册 COMMON 配置。
- V1.1：`tags/items/retired/wood.json`、`wood_products.json`（从原版 1.20.1 物品标签展开，一次性生成，原版版本不变就不用重生成）；`forge/biome_modifier/mesh_forest.json`（由 `tools/federal-worldgen/build_federal_data.py` 生成）、`forge/biome_modifier/remove_lush_cave_azalea_trees.json`；`tree/ForestPlan.java` 两个过渡林分；`structures/bunker.nbt`、`forest_home_b.nbt` 调色板换方块。
- V1.2：`tags/items/retired/` 下 10 份新清单、`tags/entity_types/retired/mobs.json`（一次性生成，原版版本不变就不用重生成）；`forge/biome_modifier/remove_retired_ores.json`；`world/VanillaRetirement.java` 的 `MOBS`、`isRetiredMob`。
- V1.3：`tags/items/retired/misc_blocks.json`、`amethyst.json`、`tools.json`、`food.json`（一次性生成）；`forge/biome_modifier/remove_amethyst_geodes.json`；`vanilla_retirement/v1.json` 加 11 个特殊配方。
- V1.4：`tags/items/retired/masonry.json`、`copper_blocks.json`、`gems.json`（一次性生成）；`remove_retired_ores.json` 加钻石、绿宝石矿。
- V1.5：`tags/items/retired/mob_drops.json`、`archaeology.json`、`nature.json`、`storage_blocks.json`、`decor_blocks.json`（一次性生成）；`forge/biome_modifier/remove_fantasy_features.json`。
- V1.6：`retired/misc_blocks.json` 加铁栏杆，`creative_hidden.json` 加附魔书、附魔之瓶；`client/VanillaRetirementClient.CreativeTabs` 拿掉战斗页、红石页的原版物品；`client/CreativeTabLayout`、`mixin/client/CreativeTabLayoutMixin`（标签页排布）；`compat/jei/AflJeiPlugin` 加左侧标签列的额外区域。
- V1.9：`tags/items/retired/vegetation.json`（手写，30 个）；`forge/biome_modifier/remove_vanilla_vegetation.json`（54 个地物）；`structures/bunker.nbt` 调色板 5 项改名。
