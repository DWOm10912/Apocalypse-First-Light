# AFL 原版生存改造 V1：原版装备与魔法退役

状态（2026-10-20）：在分支 `AFL-Vanilla-Rework`（工作树 `D:/Minecraft Modding/AFL-vanilla-rework`）上实现，**未合并 master**。`compileJava` 通过；**用户 2026-10-20 实机 PASS**（工作树开发客户端，旧存档和新世界）。GameTest `VanillaRetirementGameTests` 没跑。

依据：[V0 审计](vanilla_survival_rework_v0_audit.md)；用户 2026-10-19 的 V1 任务书和 V1a 补充。V2 的配方知识接口另见 [survivor_recipe_knowledge_v2_plan.md](survivor_recipe_knowledge_v2_plan.md)。

---

## 1. 范围和原则

- 原版剑、盾牌、盔甲在 AFL 正常生存里**完全不能用**，不只是不能合成。
- 附魔、铁砧、酿造、砂轮、锻造台升级、末影箱，在正常生存里不能用。
- **不删注册、不删物品、不改 NBT、不清装备栏。** 旧物品留着，只是没有效果、拿不到新的。
- 规则全部在服务端执行，按物品判断，所有游戏模式都生效。只有工作站交互对创造和旁观模式放行。
- 一份数据清单 + 一个总开关。开关关掉，恢复原版规则。
- V1 **不动**：木材链、工作台、2×2 / 3×3 合成、原版镐 / 铲 / 斧 / 锄、熔炉、铁矿、钢坯流程（GameTest 里逐条检查这些配方还在）。

## 2. 统一退役清单

都在 `src/main/resources/data/apocalypse_firstlight/` 下，标签都是 `replace: false`，其他数据包可以往里加。

| 文件 | 内容 | 用途 |
|---|---|---|
| `tags/items/retired/swords.json` | 木、石、铁、金、钻石、下界合金剑 | 剑的规则 |
| `tags/items/retired/shields.json` | `minecraft:shield` | 盾的规则 |
| `tags/items/retired/armor.json` | 皮革、锁链、铁、金、钻石、下界合金 × 头盔 / 胸甲 / 护腿 / 靴子，外加海龟壳 | 盔甲的规则 |
| `tags/items/retired/gear.json` | `#swords` `#shields` `#armor` | 属性、附魔、保暖、提示 |
| `tags/items/retired/unobtainable.json` | `#gear`、附魔书、附魔之瓶 | 配方（按输出）、战利品、交易、出生装备 |
| `tags/items/retired/creative_hidden.json` | `#gear` | 创造栏隐藏（只是外观） |
| `tags/blocks/retired/workstations.json` | 附魔台、铁砧（三种损坏程度）、酿造台、砂轮、锻造台、末影箱 | 生存 / 冒险模式拒绝交互 |
| `vanilla_retirement/v1.json` | `remove_recipes`、`remove_recipe_types`、`closed_dimensions` | 配方 ID、配方类型、关闭的维度 |
| `loot_modifiers/retired_vanilla.json` | `remove_items`，标签 `retired/unobtainable`，`"retirement": true` | 所有新生成的战利品 |

`vanilla_retirement/v1.json` 当前内容：

- `remove_recipes`：`minecraft:anvil`、`enchanting_table`、`brewing_stand`、`grindstone`、`smithing_table`、`ender_chest`、`shield_decoration`、`armor_dye`。
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

## 5. 获取途径覆盖

| 途径 | 处理 | 说明 |
|---|---|---|
| 配方 | 三条规则移除（第 2 节） | 客户端随配方同步一起去掉，JEI 和配方书也看不到 |
| 战利品箱、方块掉落、实体战利品表、钓鱼 | `retired_vanilla` 全局战利品修改器 | 只处理新生成的战利品；箱子里已有的、身上已有的不动 |
| 生物出生装备 | `mixin/RetiredSpawnGearMixin`：在 `Mob.populateDefaultEquipmentEnchantments` 开头脱掉随机出来的退役装备 | 只动出生随机这一步。`/summon` 写的装备、生物捡起的物品、旧区块里的生物不动 |
| 生物死亡掉落装备 | 不单独处理 | 出生时已经没有；捡起的玩家物品照常掉落，避免吞物品 |
| 村民、流浪商人交易 | 每个交易项包一层，结果在 `retired/unobtainable` 里就不生成这项 | 只影响新生成的交易；见第 10 节 |
| 世界结构 | 已有规则：原版结构几乎都关了（第 9 节） | |
| 附魔装备 | 退役装备整件拿不到；附魔书拿不到 | 附魔的弓、钓竿、工具不在 V1 范围 |
| 进度奖励 | 原版进度已全部用 `forge:false` 关掉 | |

## 6. 旧存档

- 不删任何物品、方块、NBT。旧剑、旧盔甲、旧盾留在原处，没有效果；附魔留在 NBT 里。
- 已经放下的附魔台、铁砧等方块留着，生存模式打不开；能正常挖掉。
- 末影箱里的东西在玩家数据里，开关关掉或用创造模式还能拿到。
- 生物身上已有的装备不动。

## 7. 创造模式、开发和客户端隐藏

- 创造和旁观模式可以打开退役工作站，方便检查旧建筑。
- 装备规则在**所有模式**下都生效：用 `/give` 拿到退役物品后切换模式，规则照样生效，绕不过去。
- `/give`、`/summon` 都保留。
- 创造栏隐藏（`client/VanillaRetirementClient.CreativeTabs`）：
  - 在客户端 MOD 总线订阅 `BuildCreativeModeTabContentsEvent`，优先级 LOWEST，把 `retired/creative_hidden` 里的物品从每个标签页拿掉；搜索页由各标签页汇总，所以搜索里也没有。
  - 只是外观，不是安全校验。服务端规则不依赖它。
  - 读的是这台客户端自己的 COMMON 开关，不走网络、不同步；改开关后重进世界生效。
  - 加载顺序晚于 AFL 的模组再往标签页里加的同类物品可能漏掉（已知限制）。
- 提示行（`client/VanillaRetirementClient.Tooltip`）：退役装备多一行灰字 `tooltip.apocalypse_firstlight.retired_gear`，en "No effect when used or worn"，zh "使用或穿戴时没有任何效果"。

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
- 漏斗喂料的酿造台：酿造被取消，但燃料仍会扣掉（原版先扣燃料再发事件）。
- 附魔的弓、钓竿、工具（钓鱼宝藏、生物出生附魔）不在 V1 范围。
- 流浪商人的隐身药水、马铠、弓、箭、三叉戟、金苹果、不死图腾、末影珍珠不在 V1 范围。
- 直接调用 `ItemStack.getEnchantmentLevel`（Forge 方法）而不经过 `EnchantmentHelper` 的代码读得到附魔。AFL 自己只有 `tree/FellingServer` 这样读（斧头，不在退役范围）。
- 加载顺序晚于 AFL 的模组追加的创造栏条目可能漏网（只影响外观）。

## 11. 柴炉点火和 AFL 原木

- 柴炉只认打火石和火焰弹。打火石配方是 `forge:false`；火焰弹要烈焰粉，下界已关；两者都没有战利品表和开局物资。所以新世界生存里**柴炉点不着**（代码层面确认，未实机）。
- 2026-10-20 更新：加了翻盖打火机（[flip_lighter_v1.md](flip_lighter_v1.md)），点着的打火机能点柴炉（`BoxStove#igniter`，扣燃油）。但它现在也只能从创造栏拿，生存里照样点不着，等下面的获取渠道。
- V1 没有实现点火物，因为 AFL 现在没有任何获取渠道，任何方案都要碰别人的范围。可选方案：
  1. 开局物资给一盒火柴或打火机（D 的开局物资）；
  2. 老宅战利品里放（Main 的老宅）；
  3. V2 制造：打火机 / 火柴配方，和配方知识一起做。
- 网格森林的 AFL 原木没有配方，也不在 `minecraft:logs` 物品标签里。这是有意设计（`docs/rendering/mesh_trees_p2_felling.md`；`docs/dev/wood_system_plan_v1.md` §6 计划在 V2 做）。V1 不改标签。

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
- 覆盖：退役配方不在、木材 / 工具 / 熔炉 / 铁锭 / 钢坯配方在、没有纹饰配方、没有配方输出退役物品；剑和胸甲属性清空（含 NBT 属性），撬棍和铁斧不受影响；锋利、保护、附魔等级为 0，NBT 不变；没有横扫；剑挖蜘蛛网和空手一样、收获不了；盾牌举不起来；皮靴防冻和走细雪取消；皮甲保暖为 0；生存模式铁砧被拒绝、创造模式放行、工作台不受影响；战利品过滤（读真正的 `retired_vanilla.json`，并确认它在 `global_loot_modifiers.json` 里）；盔甲匠、武器匠、图书管理员交易不出退役物品和附魔书；酿造被取消；下界、末地关闭；工业熔炉保留污染；最后关掉开关，上面每一条恢复原版。
- 不覆盖、要进游戏看：自然生成的僵尸和骷髅不带退役装备、海龟壳、真实攻击下的盾牌、创造栏隐藏、提示行、工作站提示文字、要塞不再计算。

## 14. 代码和资源

- `world/VanillaRetirement.java`：标签、开关、`closed_dimensions`、判断方法。
- `world/VanillaRetirementEvents.java`：属性、盾牌、剑挖掘、交易、酿造。
- `world/VanillaRecipePruningEvents.java`：读清单、修剪配方。
- `world/VanillaProgressionEvents.java`：经验、工作站。
- `world/WorldAccessEvents.java`：下界、末地。
- `fluid/RemoveItemsLootModifier.java`：新增 `tag` 和 `retirement` 字段，`remove_buckets.json` 写法不变。
- `mixin/Retired*Mixin.java`（6 个）。
- `client/VanillaRetirementClient.java`：创造栏隐藏、提示行。
- `temperature/ThermalInsulation.java`：退役装备保暖为 0。
- `blockentity/IndustrialFurnaceBlockEntity.java`：`assemble` 修复。
- `ApocalypseFirstLight.java`：注册 COMMON 配置。
