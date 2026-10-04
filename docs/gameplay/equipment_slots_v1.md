# AFL Equipment Slots V1（装备栏）

状态：**已实现（2026-10-03），编译通过，没有实机验证**。

用户的决定（2026-10-03）：
- 用 Curios 作为必需前置模组，只负责存放装备（同步、存档、死亡掉落、保留物品栏规则），界面由 AFL 自己摆；
- 背包不能占用盔甲栏；
- 第一批 3 个栏位放在原版物品栏里，人物模型右边那一列空位（副手格上方），和左边的盔甲栏对称；
- 以后栏位多了再加“身体”选项卡（放其余栏位、身体部位伤害的小人、各项身体数字），不先做空的第二页；
- 简易隔音耳罩挪到耳部栏，可以和头盔一起戴。

## 栏位

| 栏位 | Curios 类型 id | 物品栏位置（GUI 坐标） | 现在能放什么 | 以后 |
|---|---|---|---|---|
| 背部 | `back`（Curios 预设） | x 77, y 8 | 带 `curios:back` 标签的物品（AFL 暂时没有；其它模组的背包可以） | AFL 背包：解锁储物格 |
| 手腕 | `bracelet`（Curios 预设） | x 77, y 26 | 带 `curios:bracelet` 标签的物品（AFL 暂时没有） | 生存监测仪 |
| 耳部 | `ears`（AFL 自定义） | x 77, y 44 | 简易隔音耳罩（`curios:ears` 标签） | 其它听力防护 |

- 只装 Curios 本身不会给玩家任何栏位；这 3 个栏位由 AFL 的数据文件分配给玩家（`data/apocalypse_firstlight/curios/entities/afl_player.json`）。背部、手腕用 Curios 的预设类型，这样其它模组的背包、手镯也能放进来；耳部是 AFL 自己的类型。
- 3 个栏位的定义都设置了 `use_native_gui: false`，所以不显示在 Curios 自己的侧面板里。如果玩家的所有栏位都不在 Curios 面板上，物品栏里的 Curios 按钮也会隐藏；装了其它会加栏位的模组时，那些模组的栏位照常出现在 Curios 面板里，按钮也保留。
- 空栏位显示原版风格的灰色图标（背包、手表、耳罩，颜色 `#555555`，和原版盔甲栏图标一致）：`textures/item/empty_slot_back.png`、`empty_slot_wrist.png`、`empty_slot_ears.png`。原版物品栏背景在这一列没有格子框，由客户端按原版格子的样子补画。

## 规则

- 每个栏位放 1 件。能不能放由 Curios 的规则决定（物品标签 `curios:<类型 id>`，以及物品自己的 canEquip / canUnequip）。
- **Shift + 点击**：从物品栏、快捷栏或副手 Shift 点击一件能放进栏位的物品，会先放进对应的空栏位；栏位满了才按原版规则移动。从栏位 Shift 点击取下，按原版规则放回物品栏。
- **右键装备**：耳罩拿在手上右键，会戴到耳部栏；耳部栏原来有东西时，会和手上的交换（同原版盔甲）。
- 菜单里新增的 3 格排在原版 46 格之后（索引 46–48），原版格子的编号都不变。
- **创造 / 旁观模式**：栏位隐藏、不能操作（创造模式物品栏是另一套布局，V1 不处理）。已经戴着的东西照常生效；创造模式下右键装备也可以用，但没有界面取下。
- 死亡掉落、保留物品栏：沿用 Curios 的设置（默认跟随 keepInventory 游戏规则）。

## 和其它系统的关系

- **储物容量**（`locked_inventory_slots_v1.md`）：`PlayerStorageCapacity.getUnlockedInventorySlots` 现在是“快捷栏 9 格 + 背部栏物品提供的格数”，最多 36。背部栏物品要实现 `inventory/StorageExpander` 才会加格子；V1 还没有这样的物品，所以生存模式仍然是 9 格。
- **负重**（`weight_system_v1.md`）：玩家所有 Curios 栏位里的物品都计入携带质量（来源 `equipment:apocalypse_firstlight:curios`，负担系数 1.0）。
- **体温保暖**（`temperature_system_v1.md`）：所有 Curios 栏位里的物品按 `thermal_insulation` 数据计入保暖，和盔甲相加。耳罩保暖 1，以前戴在头上时算，现在戴在耳部栏也算。
- **听力防护**（`../equipment/simple_hearing_protection_v1.md`）：先看耳部栏，没有再看头盔栏。旧存档里戴在头上的耳罩仍然有效。

## 文件

Java 路径前缀：`src/main/java/com/antaurora/apofirstlight/`。

| 文件 | 职责 |
|---|---|
| `equipment/AflEquipmentSlots.java` | 栏位 id、读取栏位、能否放入 / 取下、右键装备；启动时把 Curios 栏位接入负重和保暖 |
| `inventory/AflEquipmentSlot.java` | 物品栏页上的一个格子，每次读写都直接访问 Curios 里的那一格 |
| `mixin/InventoryMenuEquipmentMixin.java` | 把 3 个格子加进原版物品栏菜单；Shift 点击优先放进空栏位 |
| `client/ClientEquipmentSlots.java` | 补画格子框；隐藏没用的 Curios 按钮；把耳部栏的耳罩画在头上（和原版头部物品同样的画法） |
| `inventory/StorageExpander.java` | 以后背包用的接口：背部栏物品提供多少储物格 |
| `inventory/PlayerStorageCapacity.java` | 容量 = 9 + 背部栏提供的格数 |
| `item/SimpleHearingProtectionItem.java` | 改为 Curios 物品，右键戴到耳部栏 |
| `equipment/HearingProtectionManager.java` | 先读耳部栏，再读头盔栏 |

资源和数据：
- `data/apocalypse_firstlight/curios/slots/{back,bracelet,ears}.json`、`data/apocalypse_firstlight/curios/entities/afl_player.json`；
- `data/curios/tags/items/ears.json`；
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
6. 死亡：默认规则下耳罩会掉落；开启 keepInventory 时保留。
7. 退出重进、换维度：耳部栏里的耳罩还在。
8. `/aflweight`：耳罩的质量计入（来源 `equipment:apocalypse_firstlight:curios`）。
9. 切创造模式：物品栏里看不到这 3 格，快捷栏显示正常、没有被盖住。
10. 旧存档里戴在头上的耳罩：仍然有隔音效果，可以从头盔栏取下。
