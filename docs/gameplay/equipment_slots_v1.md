# AFL Equipment Slots V1（装备栏）

状态：**已实现（2026-10-03），编译通过，没有实机验证**。

用户的决定（2026-10-03）：
- 用 Curios 作为必需前置模组，只负责存放装备（同步、存档；死亡时栏位物品的去向 2026-10-14 起由幸存者系统接管，见 [survivor_death_succession_v0.md](survivor_death_succession_v0.md)），界面由 AFL 自己摆；
- 背包不能占用盔甲栏；
- 第一批 3 个栏位放在原版物品栏里，人物模型右边那一列空位（副手格上方），和左边的盔甲栏对称；
- 以后栏位多了再加“身体”选项卡（放其余栏位、身体部位伤害的小人、各项身体数字），不先做空的第二页；
- 简易隔音耳罩挪到耳部栏，可以和头盔一起戴。

## 栏位

| 栏位 | Curios 类型 id | 物品栏位置（GUI 坐标） | 现在能放什么 | 以后 |
|---|---|---|---|---|
| 背部 | `back`（Curios 预设） | x 77, y 8 | 带 `curios:back` 标签的物品（AFL 暂时没有；其它模组的背包可以） | AFL 背包：解锁储物格 |
| 手腕 | `bracelet`（Curios 预设） | x 77, y 26 | 带 `curios:bracelet` 标签的物品：腕式温度计（2026-10-03） | 生存监测仪 |
| 耳部 | `ears`（AFL 自定义） | x 77, y 44 | 简易隔音耳罩（`curios:ears` 标签） | 其它听力防护 |
| 胸挂 | `rig`（AFL 自定义，2026-10-14 网格背包 V0，分支未合并） | 原版物品栏页不显示（放在屏外）；AFL 背包界面里在中栏“胸挂” | 野战胸挂（`curios:rig` 标签） | 更多胸挂 |
| 枪套 | `holster`（AFL 自定义，2026-10-14 武器位，分支未合并，未经游戏内验证） | 原版物品栏页不显示；AFL 背包界面里在人物下方手枪框旁边 | 手枪枪套（`curios:holster` 标签）：右键穿上；手枪位（键 2）有东西时脱不下来 | 刀鞘（未定） |
| 挂点 × 2 | 不是 Curios 栏位：在穿着的胸挂上（2026-10-21 第二轮起，C 分支 `feature/linden-markings`，用户 2026-10-21 实机 PASS） | 原版物品栏页不显示；AFL 背包界面中栏“胸挂”下面一行两个格子，只在穿着带挂点的胸挂时出现 | `apocalypse_firstlight:clippable` 标签：盖革计数器、指南针、翻盖打火机（`flip_lighter`，标签里是可选项，C 分支还没有这件物品） | 手电、哨子；别的胸挂各有自己的挂点数（没做） |

2026-10-14 网格背包 V0（分支 `feature/inventory-grid-v0`，用户 2026-10-14 实机 PASS）：背部栏现在能放 AFL 的帆布背包、登山背包（`curios:back` 标签），胸挂栏放野战胸挂。它们的东西存在物品自己的 NBT 里（`Items` 列表），脱下、丢在地上、放进箱子都跟着走；网格物品不能放进网格。菜单里第 4 个 AFL 格排在索引 49（背部 46、手腕 47、耳部 48、胸挂 49）。生存 / 冒险按 E 打开 AFL 背包界面，四个栏位都由它摆放，见 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md)。

- 只装 Curios 本身不会给玩家任何栏位；这 3 个栏位由 AFL 的数据文件分配给玩家（`data/apocalypse_firstlight/curios/entities/afl_player.json`）。背部、手腕用 Curios 的预设类型，这样其它模组的背包、手镯也能放进来；耳部是 AFL 自己的类型。
- 3 个栏位的定义都设置了 `use_native_gui: false`，所以不显示在 Curios 自己的侧面板里。如果玩家的所有栏位都不在 Curios 面板上，物品栏里的 Curios 按钮也会隐藏；装了其它会加栏位的模组时，那些模组的栏位照常出现在 Curios 面板里，按钮也保留。
- 空栏位显示原版风格的灰色图标（背包、手表、耳罩，颜色 `#555555`，和原版盔甲栏图标一致）：`textures/item/empty_slot_back.png`、`empty_slot_wrist.png`、`empty_slot_ears.png`。原版物品栏背景在这一列没有格子框，由客户端按原版格子的样子补画。

## 挂点（2026-10-21）

用户 2026-10-21：“我们能不能做一个挂件curios槽位，放盖格计数器/罗盘/打火机这些小玩意？”，同日定了方案：“罗盘条要指南针挂着或拿着才显示，挂点 2 个，盖格计数器、指南针、打火机挂上去各有用处，新做指南针物品（创造栏，老宅放一个）”。**用户 2026-10-21 实机 PASS（“先这样吧，PASS，明天还需要优化细节”），细节下一轮再调**。

第二轮（同日）：用户 2026-10-21：“我觉得挂件栏位，应该是需要玩家装备了胸挂之后才能出现的，因为如果玩家把挂件挂在身上，会浮空。如果挂在任意胸挂上，就会好看很多，并且自动贴合”。挂点从 Curios 栏位 `clip` 改成胸挂自带的，**用户 2026-10-21 实机 PASS**。第一轮挂在腰带前面的画法已经去掉。

- **挂点在胸挂上**（`inventory/grid/RigClips.java`）：野战胸挂两条肩带上各 1 个（`GridGearItem.clips()`，野战胸挂 2，背包 0）。挂着的东西存在胸挂自己的 NBT 里（`Clips` 列表，每项是物品存档加 `Slot`），所以脱下胸挂、丢在地上、放进背包、进尸体时都跟着胸挂走；脱胸挂不用先取下挂件。胸挂提示里多一行“肩带上有 2 个挂点”。
- 没穿胸挂（或胸挂没有挂点）时没有挂点：背包界面不显示这一行，格子不能放东西，挂件也不工作。
- 每个挂点挂 1 件。拖进去挂上，拖出来取下；从物品栏 Shift 点击能挂的物品，会先挂到空的挂点上。捡起来的东西**不会**自动挂上（和胸挂、背包不同）。
- 菜单索引 51、52（`AflInventoryScreen.CLIP_SLOT`），排在枪套（50）后面，只在 AFL 背包界面里显示，在“胸挂”那一行下面；空格子图标 `textures/item/empty_slot_clip.png`（登山扣），提示“挂点 / 挂盖革计数器、指南针或打火机”。能挂的物品提示里多一行“可以挂在胸挂上”（`client/ClipTooltip.java`）。
- 挂上去的东西算进胸挂的负重（`AflCarriedContents` 给野战胸挂注册了内容：网格里的加挂着的）。
- **画在身上**（`client/ClipRenderer`，是野战胸挂的 Curios 渲染器：先画胸挂，再画挂件）：挂点 0 在右肩带，挂点 1 在左肩带，都在胸口上方肩带平直的一段（y -0.9 往下），贴着肩带正面。指南针用织带片和登山扣挂着，正面朝外，一直竖直；盖革计数器竖着别在肩带上，正面朝外、顶端朝上，自己挂着的那台液晶照常显示读数。同时背着背包时，压在背包肩带上、往外挪一点。细节见 `docs/models/equipment_meshes_v1.md`「2026-10-21 第二轮」。
- 老测试世界里第一轮放在 Curios 挂点里的东西：栏位删掉后 Curios 不再显示它们（只影响这个分支的测试世界）。
- **挂着也在工作**（`AflEquipmentSlots.inUse`：主手、副手或穿着的胸挂的挂点上；`AflEquipmentSlots.clip(entity, i)` 读挂点）：
  - 盖革计数器：挂着也会响、也显示读数，服务端照常发读数（`RadiationSyncEvents`、`GeigerSoundController`、`GeigerHudOverlay`）；带辐射的物品提示里的读数，挂点上的盖革计数器也算。
  - 指南针（`apocalypse_firstlight:compass`，新物品）：拿着或挂着时屏幕顶部显示罗盘条（`docs/ui/afl_map_v1_plan.md` 第 6.1 节）。
  - 翻盖打火机：**没做**。打火机在 master 上，C 分支还没有；挂着时一个键直接掏出来点亮，要等这件物品到了这个分支再做。
- 指南针：创造栏「近战与装备」地图后面；不能叠；0.06 kg（估的）；网格背包 1 × 1；没有配方。模型是网格做的行军指南针（橄榄色涂层铝壳、黑色转动表圈、玻璃、米色浮动刻度盘、红白指针、黄铜轴帽、能竖起来的钢环），LabPBR；卡盘真的会指北（拿着、挂着、放在地上、展示框里、物品栏里都是），见 `docs/models/equipment_meshes_v1.md`「2026-10-21」。生成器 `tools/build-equipment-meshes-v1.mjs compass`，源文件 `src/main/blockbench/compass.bbmodel`。**老宅里放一个还没做**：老宅模板归 Main，交给 Main 放。

## 规则

- 每个栏位放 1 件。能不能放由 Curios 的规则决定（物品标签 `curios:<类型 id>`，以及物品自己的 canEquip / canUnequip）。
- **Shift + 点击**：从物品栏、快捷栏或副手 Shift 点击一件能放进栏位的物品，会先放进对应的空栏位；栏位满了才按原版规则移动。从栏位 Shift 点击取下，按原版规则放回物品栏。
- **右键装备**：耳罩拿在手上右键，会戴到耳部栏；耳部栏原来有东西时，会和手上的交换（同原版盔甲）。
- 菜单里新增的 3 格排在原版 46 格之后（索引 46–48），原版格子的编号都不变。
- **创造 / 旁观模式**：栏位隐藏、不能操作（创造模式物品栏是另一套布局，V1 不处理）。已经戴着的东西照常生效；创造模式下右键装备也可以用，但没有界面取下。
- 死亡：2026-10-14 起（幸存者死亡与随机继任 V0）生存 / 冒险模式死亡时，栏位里的物品（含装饰栏）一律进入尸体，不论 keepInventory 或 Curios 的保留设置；诅咒消失的物品照原版消失。创造 / 旁观模式死亡仍按 Curios 的设置。见 [survivor_death_succession_v0.md](survivor_death_succession_v0.md)。

## 和其它系统的关系

- **储物容量**（`locked_inventory_slots_v1.md`）：`PlayerStorageCapacity.getUnlockedInventorySlots` 现在是“快捷栏 9 格 + 背部栏物品提供的格数”，最多 36。背部栏物品要实现 `inventory/StorageExpander` 才会加格子；V1 还没有这样的物品，所以生存模式仍然是 9 格。
- **负重**（`weight_system_v1.md`）：玩家所有 Curios 栏位里的物品都计入携带质量（来源 `equipment:apocalypse_firstlight:curios`，负担系数 1.0）。
- **体温保暖**（`temperature_system_v1.md`）：所有 Curios 栏位里的物品按 `thermal_insulation` 数据计入保暖，和盔甲相加。耳罩保暖 1，以前戴在头上时算，现在戴在耳部栏也算。
- **听力防护**（`../equipment/simple_hearing_protection_v1.md`）：先看耳部栏，没有再看头盔栏。旧存档里戴在头上的耳罩仍然有效。

## 文件

Java 路径前缀：`src/main/java/com/antaurora/apofirstlight/`。

| 文件 | 职责 |
|---|---|
| `equipment/AflEquipmentSlots.java` | 栏位 id、读取栏位、能否放入 / 取下、右键装备；启动时把 Curios 栏位接入负重和保暖；挂点 `clip` / `clipped` / `inUse`（读穿着的胸挂） |
| `inventory/AflEquipmentSlot.java` | 物品栏页上的一个格子，每次读写都直接访问 Curios 里的那一格；挂点（序号 0 / 1）读写穿着的胸挂上的那个挂点 |
| `inventory/grid/RigClips.java` | 胸挂上的挂点：读写胸挂 NBT `Clips`，`clippable` 标签；同一份数据返回同一个物品堆对象（指南针摆动、盖革计数器认自己） |
| `client/ClipRenderer.java` | 野战胸挂的渲染器：胸挂网格，加肩带挂点上的指南针、盖革计数器 |
| `client/CompassItemRenderer.java` | 指南针网格，卡盘实时指北 |
| `mixin/InventoryMenuEquipmentMixin.java` | 把 3 个格子加进原版物品栏菜单；Shift 点击优先放进空栏位 |
| `client/ClientEquipmentSlots.java` | 补画格子框；隐藏没用的 Curios 按钮；把耳部栏的耳罩画在头上（和原版头部物品同样的画法） |
| `inventory/StorageExpander.java` | 以后背包用的接口：背部栏物品提供多少储物格 |
| `inventory/PlayerStorageCapacity.java` | 容量 = 9 + 背部栏提供的格数 |
| `item/SimpleHearingProtectionItem.java` | 改为 Curios 物品，右键戴到耳部栏 |
| `equipment/HearingProtectionManager.java` | 先读耳部栏，再读头盔栏 |

资源和数据：
- `data/apocalypse_firstlight/curios/slots/{back,bracelet,ears}.json`、`data/apocalypse_firstlight/curios/entities/afl_player.json`；
- `data/curios/tags/items/ears.json`、`bracelet.json`（腕式温度计）；
- 语言：`curios.identifier.ears`（“耳部” / “Ears”）。

依赖：`build.gradle` 加了 Illusive Soulworks 的 Maven 仓库，编译只用 Curios 的 API（`curios-forge:5.14.1+1.20.1:api`），开发环境运行时加载完整的 Curios；`mods.toml` 声明 `curios` 为必需前置（`[5.14.1,)`）。

## 没做的

- “身体”选项卡（第 4 个栏位出现时再做）。
- AFL 自己的背包、生存监测仪。
- 创造模式物品栏里的栏位。

## 验收（待用户执行）

1. 打开物品栏：人物模型右边、副手格上方多出 3 个格子，空的时候显示背包、手表、耳罩的灰色图标；格子框和原版一样。左上没有 Curios 按钮。
2. 打开配方书：3 个格子跟着物品栏一起移动，位置正确。
3. 耳罩：拿在手上右键，戴到耳部栏，有皮革装备声；戴上铁头盔后，两者同时戴着，人物模型上耳罩还在。世界声音减半、耳鸣减弱照旧。
4. 从物品栏 Shift 点击耳罩：放进耳部栏；再 Shift 点击耳部栏：取回物品栏（生存模式下只能放进开放的 9 格）。
5. 普通物品（比如泥土）放不进这 3 格。
6. 死亡：（2026-10-03 验收时的规则：默认掉落，keepInventory 保留；已过时）2026-10-14 起耳罩随其它物品进入尸体，见 [survivor_death_succession_v0.md](survivor_death_succession_v0.md)。
7. 退出重进、换维度：耳部栏里的耳罩还在。
8. `/aflweight`：耳罩的质量计入（来源 `equipment:apocalypse_firstlight:curios`）。
9. 切创造模式：物品栏里看不到这 3 格，快捷栏显示正常、没有被盖住。
10. 旧存档里戴在头上的耳罩：仍然有隔音效果，可以从头盔栏取下。

2026-10-14 武器位（同一分支，代码完成，未经游戏内验证）：新加枪套栏 `holster`（`data/apocalypse_firstlight/curios/slots/holster.json`，`afl_player.json` 加入），菜单索引 50（背部 46、手腕 47、耳部 48、胸挂 49、枪套 50）。穿着手枪套，手枪才能放进手枪位（键 2），见 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md) 第 10 节。

2026-10-14 身上的装备（同一分支，代码完成，未经游戏内验证）：胸挂、帆布背包、登山背包、手枪枪套、隔音耳罩换成 AFL 网格 + LabPBR，戴着时由各自的 Curios 渲染器（`ClientEquipmentSlots.WornMeshRenderer`）画在躯干 / 右腿 / 头上，跟着身体动。见 [装备网格 V1](../models/equipment_meshes_v1.md) 和 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md) 第 11 节。
