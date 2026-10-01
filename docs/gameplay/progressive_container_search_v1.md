# AFL Progressive Container Search V1（逐格搜索）

状态（2026-09-29）：**通用框架已实现**。正式接入：`industrial_locker` V2（27 格 / 3 行，40 ticks/格，±15%），见 [industrial_locker_v2.md](../models/industrial_locker_v2.md)；`lead_chest`（2026-09-30 起 V2 交互，同日外观换成 V3；同样 27 格 / 40 ticks），见 [lead_chest_v3.md](../models/lead_chest_v3.md)；`industrial_electrical_box` V2（2026-09-30，9 格 / 3×3 发射器式布局，40 ticks/格），见 [industrial_electrical_box_v2.md](../models/industrial_electrical_box_v2.md)。
- **V1 实机验收：用户确认全部 PASS**（2026-09-29，用户测试，不是代理执行的测试）。
- 验收之后按用户要求做了一次小改动：搜索图标改为转圈放大镜，默认每格时长由 20 改为 40 ticks。改动后 `compileJava --offline` 一次 PASS，改动本身未经实机复测。
- 另有开发演示方块（见第 21 节）。

## 1. 目的

打开一个从未搜索过的世界战利品容器时，玩家一开始不知道里面有什么。系统按固定顺序自动逐格搜索：
- 每搜完一格，这格才变为已揭示：可能是真实物品，也可能是空格。
- 已揭示的格子马上可以正常拿取、放入；未揭示的格子完全未知、不可交互。
- 离开会中断搜索，但已揭示的格子永久保存，回来后继续搜剩下的。

玩家的决策是"留下继续搜，还是带着已发现的东西离开"，而不是逐格点击。本系统只借鉴"逐格揭示"的体验，UI、图标、音效、代码和布局都是 AFL 自己的。

## 2. 适用与不适用

AFL 的储物分为两类，两者并存，不强行统一：

| 类别 | 例子 | 规则 |
|---|---|---|
| **SEARCHABLE CLOSED STORAGE** | 储物柜、文件柜、厨房柜、药柜、工具箱、抽屉、衣柜、铅箱、军用储物箱、封闭冷柜、封闭设备柜 | 适合接入本系统 |
| **VISIBLE STORAGE** | 货架、武器架、自动售货机、开放展示柜 | 物品直接在世界里渲染、实时交互，**不接入**；这些方块会把物品 NBT 同步给客户端用于显示，本身就和隐藏格不兼容 |

只有显式实现 `AflSearchableContainer` 的 AFL 容器才使用本系统。原版箱子、木桶以及其它 AFL 容器行为完全不变，没有做全局替换。

## 3. 代码位置

公共代码（`src/main/java/com/antaurora/apofirstlight/containersearch/`）：

| 文件 | 职责 |
|---|---|
| `AflSearchableContainer` | 接入接口；资产方块实体在原有父类之外实现它，不需要新的基类 |
| `AflContainerSearchState` | 每个容器一个：持久化的 reveal 状态，加上共享的搜索会话（不持久化） |
| `AflContainerSearchSettings` | 每种资产的计时和噪声配置 |
| `AflContainerSearchSpeed` | 服务端唯一的计时入口，以及技能 / 环境修正的扩展点 |
| `AflContainerSearch` | 资产调用的静态入口：生命周期、菜单、自动化、破坏、比较器、debug |
| `AflContainerSearchView` | 服务端遮罩视图，菜单只能看到它 |
| `AflContainerSearchSlot` | 隐藏时完全惰性的格子 |
| `AflContainerSearchMenu` | 搜索菜单，两端共用；按 `AflContainerSearchLayout` 摆放格子（9×1–9×6 箱子网格，或 3×3 发射器网格） |
| `AflContainerSearchLayout` | 菜单布局：箱子网格的格子与背包坐标照原版 `ChestMenu`，3×3 照原版 `DispenserMenu`（2026-09-30 新增） |
| `AflContainerSearchItemHandler` | Forge 自动化看到的遮罩视图 |

其它文件：
- 菜单类型注册：`registry/AflMenus.java` 的 `SEARCHABLE_CONTAINERS`。
- 客户端：`client/AflContainerSearchScreen.java`（开发占位遮罩），在 `client/AflMenuScreens.java` 注册。

新增 Registry ID：菜单类型 `apocalypse_firstlight:searchable_container_9x1` 到 `apocalypse_firstlight:searchable_container_9x6`，以及 `apocalypse_firstlight:searchable_container_3x3`（2026-09-30）。和原版 `GENERIC_9xN` / `GENERIC_3x3` 一样按布局各注册一个类型，打开时不附带额外数据。这样原版 `player.openMenu(be)` 和 Forge `NetworkHooks.openScreen` 两种打开方式都能正常使用，客户端不会因为缺少附加数据而崩溃。

## 4. 接入 contract

资产方块实体（例如已继承 `RandomizableContainerBlockEntity` 的类）做以下几件事：

```java
public class XxxBlockEntity extends RandomizableContainerBlockEntity implements AflSearchableContainer {
    private final AflContainerSearchState search = new AflContainerSearchState();

    public AflContainerSearchState aflSearchState() { return search; }
    public AflContainerSearchSettings aflSearchSettings() { return SETTINGS; }
    public boolean aflSearchRequiredOnInit() { return lootTable != null; }   // 世界 loot = 需要搜索

    @Override public void unpackLootTable(@Nullable Player p) { AflContainerSearch.beforeLootUnpack(this); super.unpackLootTable(p); }
    @Override public boolean canPlaceItem(int s, ItemStack st) { return AflContainerSearch.canPlaceItem(this, s) && super.canPlaceItem(s, st); }
    @Override public boolean canTakeItem(Container t, int s, ItemStack st) { return AflContainerSearch.canTakeItem(this, s) && super.canTakeItem(t, s, st); }
    @Override protected IItemHandler createUnSidedHandler() { return AflContainerSearch.itemHandler(this); }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inv) { return AflContainerSearch.createMenu(id, inv, this); }
    // load(): super.load + 物品/loot 读取 + search.load(tag)
    // saveAdditional(): super + 物品/loot 写入 + search.save(tag)
}
```

- `getLevel()`、`getBlockPos()`、`getBlockState()`、`isRemoved()`、`setChanged()` 由 BlockEntity 自带实现，不需要额外代码。
- 方块被移除时（玩家破坏、爆炸、失去支撑），改用 `AflContainerSearch.dropContentsOnBreak(level, pos, be)` 代替原来的 `Containers.dropContents`。
- `getUpdateTag()` / `getUpdatePacket()` **不能包含物品**。框架无法强制这一点，接入时必须检查；RandomizableContainerBlockEntity 的默认实现不含物品。
- 容器格数必须是 9、18、27、36、45 或 54，才能用通用的箱子网格菜单。其它布局见第 22 节。

完整参考实现：`src/dev/java/com/antaurora/apofirstlight/dev/containersearch/DevSearchCrateBlockEntity.java`。

## 5. 世界生成与玩家放置

"是否需要搜索"不以 `lootTable != null` 作为永久判断（loot 解包后这个字段会被清空），而是由框架自己的持久状态决定：

- **未初始化**：NBT 里没有 `AflContainerSearch`。老世界、刚放下的方块、从未被接触过的世界容器都属于这种状态。
- **第一次接触时初始化一次**，以先发生者为准：
  - loot 解包（原版 `getItem`、`setItem`、`removeItem`、`isEmpty`、带玩家的 `createMenu` 都会经过 `unpackLootTable`）；
  - 打开菜单；
  - 自动化访问；
  - 破坏。
- 初始化时询问资产的 `aflSearchRequiredOnInit()`：
  - `unpackLootTable` 覆写的第一行就调用 `beforeLootUnpack`，所以此时 loot table 仍然存在，典型实现返回 `lootTable != null`，也就是"世界 loot 容器需要搜索"。
  - 答案连同 seed 一起持久化，之后不再改变。
- **玩家放置的容器**：没有 loot table，初始化为不需要搜索，永远是普通容器。
- **老世界里没有 search NBT 的容器**：
  - 若仍带未解包的 LootTable，第一次接触时进入搜索；
  - 若已解包或本来就是玩家的储物，则全部揭示，不隐藏任何已有物品。
- 在客户端或没有 level 时不会初始化，只按资产回答临时判断，不写入任何状态。
- 初始化之后再通过命令设置新的 loot table，不会重新进入搜索（V1 不支持重新布防）。测试时可以用 `/data remove block <x y z> AflContainerSearch` 让容器回到未初始化状态。

## 6. LootTable 生命周期

以下事实已对照本机 Forge 47.4.22 / 1.20.1 official mappings 的 `RandomizableContainerBlockEntity` 源码核对：

- `unpackLootTable(Player)` 在以下调用中触发：
  - `isEmpty`、`getItem`、`removeItem`、`removeItemNoUpdate`、`setItem`，都以 `unpackLootTable(null)` 触发；
  - 3 参数的 `createMenu(id, inv, player)`：先检查 `canOpen`，再用打开者调用 `unpackLootTable`，然后调用 2 参数的 `createMenu`。
- `clearContent` 在 1.20.1 **不**解包；`canOpen` 在 loot 未解包时拒绝旁观者。
- 解包只发生一次：先把 `lootTable` 置 null，再 `fill`，seed 是保存下来的 `LootTableSeed`。

本系统"逐格搜索"不等于"逐格 roll loot"：
- **第一次打开**（搜索开始）时，服务端按原版流程用打开者的幸运值完整解包，真实库存在服务端一次生成。
- 之后逐格揭示的只是"客户端被允许知道什么"。真实库存和客户端已知库存是两回事。
- 其它服务端路径也可能先触发解包。例如往一个全部隐藏的容器里塞东西的漏斗，会经原版回退路径的 `getItem` 解包，此时没有玩家，也不计幸运值（和原版漏斗相同）。
- 无论 loot 何时生成，访问权限的唯一来源都是 reveal mask，安全性不依赖"loot 还没生成"。

## 7. 服务端权威与隐藏格网络安全

- 菜单拿到的容器是 `AflContainerSearchView`：隐藏格的 `getItem`、`removeItem`、`removeItemNoUpdate` 返回空，`setItem` 忽略，`canPlaceItem`、`canTakeItem` 为 false。容器格也被替换为 `AflContainerSearchSlot`。
- 原版菜单同步（`sendAllDataToRemote`、`broadcastChanges`、`broadcastFullState`，以及 `getItems`、slot listener）读取的都是 `Slot.getItem()`，所以隐藏格发给客户端的永远是 `ItemStack.EMPTY`：
  - 物品 ID、数量、NBT、耐久、附魔、稀有度、capability 内容都不会发送；
  - 客户端菜单背后的 `SimpleContainer` 里只有服务端发过去的东西。
- 同步给客户端的只有：reveal mask、当前格序号、时长、开始 tick 低 16 位、标志位。这些都与内容无关（见第 9、10 节）。
- 揭示由服务端状态决定；客户端只做表现，从不自行判断何时揭示。

### CLIENT HIDDEN ITEM SECURITY

**结论**：通过本系统的菜单、Forge 物品接口和方块实体的 `canPlaceItem`/`canTakeItem`，未揭示格的真实 ItemStack 不会发送给普通客户端。这个结论只针对这三条路径，并且是代码层面的静态核对，没有做抓包实测。

仍然存在的路径（V1 未封堵，列出以免误判为"绝对安全"）：

1. **直接读取方块实体 `Container` 的服务端代码**，不经过菜单或物品接口：
   - AFL 自己的 `ContainerRadiation`：loot 解包后，隐藏格里的放射性物品照样计入辐射剂量（物理辐射），玩家能间接感知"里面有辐射源"，但拿不到物品数据。V1 有意保留，未修改辐射系统。
   - 第三方服务端模组若直接读 `Container`（而不是 capability）再把结果发给客户端（例如某些容器内容 HUD 模组），会泄露。未逐个验证。
   - OP 的 `/data get block` 能看到完整 NBT。
2. **资产自己的 `getUpdateTag`/`getUpdatePacket`**：如果把物品写进去就会泄露，所以第 4 节把"不能包含物品"列为硬性要求。
3. 原版漏斗回退路径的 `isFullContainer` 会在服务端读取真实格子判断是否已满，但只影响漏斗是否继续尝试，不发送给客户端，也不搬运隐藏物品。

## 8. Reveal mask 与 NBT

标签 `AflContainerSearch`（与物品、LootTable 并列，写在资产自己的 `saveAdditional` 里）：

| 键 | 类型 | 说明 |
|---|---|---|
| `Format` | int | 1 |
| `Required` | byte | 初始化时是否需要搜索 |
| `Seed` | long | 搜索顺序和 jitter 的种子 |
| `Revealed` | long[] | `BitSet.toLongArray()`，第 i 位 = 第 i 格已揭示；`Required=false` 时不写 |
| `Slots` | int | 写入 mask 时的格数，用于容器扩容或缩容 |

- **不持久化**的内容：当前格进度、客户端动画进度、viewer 列表。
- **搜索完成**不单独存储，由"`[0, 格数)` 全部置位"推导，reveal 数据不会被删除。

格数变化与异常处理（不崩溃、不重新生成 loot，优先保护物品）：

| 情况 | 处理 |
|---|---|
| 超出当前格数的位 | 丢弃，不会越界 |
| 容器扩容 | 旧 mask 已完成时，新格揭示（不可能有 loot 在里面）；否则新格隐藏 |
| 缺少 `Slots` | 视为未知，只丢弃越界位 |
| 缺少 `Seed` | 用方块坐标推导一个固定 seed，下次保存时写入 |
| 有标签但缺少 `Revealed`（且 `Required=true`） | 全部隐藏：物品保留，可以重新搜索，不会 roll |
| `Revealed` 长度超过 64 个 long（4096 格） | 视为损坏，截断 |
| `/data merge` 等运行中重新 load | 当前格进度清零，正在查看的菜单继续工作 |

## 9. 搜索顺序

- 用 `Seed` 对 `0..格数-1` 做 Fisher–Yates 洗牌（SplitMix64，不依赖原版随机实现），每次按需重建，不存数组。
- 同一容器重进世界后顺序不变。已揭示的格子自动跳过。
- 顺序只取决于 seed 和格数，**不看任何格子内容**，所有格子机会均等，不会优先搜有物品的格子。

## 10. 搜索计时

统一入口是 `AflContainerSearchSpeed.slotDurationTicks`，只在服务端、每格开始时计算一次：

`时长 = baseTicksPerSlot × (1 + durationJitter × u(seed, 格)) / (玩家修正 × 环境修正)`，结果限制在 1–32767 ticks。

- `u` 是由 seed 和格序号算出的 [-1, 1) 值，**与格子内容完全无关**。时长不会因为格里有枪、稀有物资或者是空格而改变，所以计时不会泄露内容。
- `durationJitter` 允许 0–0.5，框架默认 0.15。框架默认 `baseTicksPerSlot` 为 40（2026-09-29 按用户反馈由 20 调整，约 2 秒/格），这只是占位，具体资产的平衡另定。
- 修正入口：
  - `playerMultiplier(ServerPlayer, 容器)`：未来的搜刮技能、特质；
  - `environmentMultiplier(ServerLevel, 容器)`：未来的黑暗、手电、疲劳、伤势。
  - V1 两者都返回 1.0；非有限值或非正数一律按 1.0 处理。
- 多人不叠加：取所有有效搜索者中最高的玩家修正（V1 全部是 1.0）。
- 屏幕和渲染器不参与计算。

## 11. 暂停、继续与多人

- 搜索状态属于容器本身，全服共享：
  - A 搜出 18/54 格后，B 打开时直接看到这 18 格，剩下 36 格仍然未知。
  - 同时打开时只有一条全局进程，速度不会因为人多而加倍：会话每个 game tick 最多推进一步，按 game time 去重。
- 推进由正在查看的菜单在每 tick 同步时驱动，不需要 BlockEntity ticker，没有人查看时不消耗任何开销。
- **有效搜索者**：菜单仍然打开、玩家没有被移除、玩家存活、不是旁观者。
  - 旁观者可以看到已揭示的内容，但不推动搜索。
  - 死亡玩家不算搜索者。
- 搜索与具体玩家 UUID 无关。
- **暂停**：最后一名有效搜索者关闭菜单时立即暂停。以下情况都会触发：
  - 正常关闭、超出距离被系统关闭、断线（`Player.remove` 会关闭菜单）、死亡；
  - 方块实体被移除（`stillValid` 失败，每次推进时也会检查 `isRemoved`）。
- 正在搜的那一格进度清零，仍然是 HIDDEN；下次从 0% 重新搜同一格。已揭示的格子永久保留。
- 服务器关闭时只会丢失当前格进度。
- 搜索过程中可以随时拿走已揭示的物品，也可以放入物品，同时搜索继续。

## 12. 菜单交互拦截

| 路径 | 处理 |
|---|---|
| 左键、右键拿取或放入 | 隐藏格 `getItem` 为空，`mayPickup`/`mayPlace` 为 false，`safeInsert` 原样退回 |
| Shift Click / `quickMoveStack` | 从隐藏格发起：`mayPickup` 为 false 直接返回；转入容器：合并阶段读到空，空格阶段 `mayPlace` 为 false，都会跳过 |
| 数字键、副手 F 交换（SWAP） | 菜单 `clicked` 直接忽略隐藏格；格子本身也拒绝 |
| 拖拽（QUICK_CRAFT） | `clicked` 忽略隐藏格的加入步骤；`canDragTo` 为 false；分配阶段 `mayPlace` 为 false |
| 双击收集（PICKUP_ALL） | `hasItem` 为 false，`canTakeItemForPickAll` 为 false |
| 丢出（THROW） | `clicked` 忽略；`tryRemove` 受 `mayPickup` 限制 |
| 创造模式中键复制（CLONE） | `clicked` 忽略；`hasItem` 为 false |
| 物品自定义堆叠覆写、Forge `onItemStackedOn` | 在 `clicked` 前置拦截下都不会执行；即使绕过，写入也会被遮罩视图拒绝 |
| 伪造点击包 | 服务端同样执行以上检查，客户端声称的格子内容会被下一次同步纠正 |

已揭示的格子恢复原版规则。玩家之后放进去的物品永远可见，因为揭示状态属于格子，不属于原来的战利品。搜索完成后再次打开，直接使用原版菜单：箱子网格用 `ChestMenu`（`GENERIC_9xN`），3×3 用 `DispenserMenu`（`GENERIC_3x3`）。

## 13. 漏斗、Forge ItemHandler 与自动化

对照 Forge 47.4.22 源码：

- **漏斗抽取**和**漏斗矿车**：走 `VanillaInventoryCodeHooks.extractHook`，只要存在 capability 就不会走原版回退。遮罩后的 `AflContainerSearchItemHandler` 对隐藏格：`getStackInSlot` 返回空、`extractItem` 返回空、`insertItem` 原样退回、`setStackInSlot` 忽略、`isItemValid` 为 false。
- **漏斗插入**：`insertHook` 通过 capability 插入失败时返回 false，原版随后会**回退**，把方块实体直接当作 `Container`，经 `tryMoveInItem` 插入。这条回退路径首先检查 `canPlaceItem`，因此资产必须覆写 `canPlaceItem`（第 4 节），否则隐藏格会被塞进物品或被合并。
- 原版回退抽取所检查的 `canTakeItem` 也做了覆写，作为兜底。
- **投掷器**：`dropperInsertHook` 在有 capability 时由 Forge 处理，不会回退。
- 代码搜索（`ITEM_HANDLER`、`getContainerAt`、`instanceof Container`）没有发现项目自己的管道或机器会从相邻容器搬运物品。`BuildingAuthoringService` 只在创作工具里通过 capability 检查"不可含物品"。
- 结果：未搜索的柜子下面放漏斗吸不出任何隐藏物品，也无法往隐藏格里塞东西；已揭示的格子正常参与自动化。

## 14. 破坏、爆炸、移动与 NBT 复制

`AflContainerSearch.dropContentsOnBreak` 的处理：
- 已揭示的格子照常掉落，包括玩家后来放进去的物品。
- 隐藏格是从未被看过的世界 loot，随容器一起销毁。
- 如果 loot table 还没解包，就直接取消（`setLootTable(null, 0)`），不会先 roll 再丢。

这样拆方块不能绕过搜索。方块本身掉不掉落由资产自己的规则决定；掉出的方块物品不带任何状态，重新放置后是玩家储物，不可能重复 roll loot。

| 情况 | 结果 |
|---|---|
| 精准采集 | 不适用：`industrial_locker` 掉的是一个全新的方块物品，不复制方块实体 NBT；接入的资产不得改成连同方块实体 NBT 一起掉落 |
| clone、结构方块保存/加载、`/data`、`/setblock` 带 NBT、创造模式 Ctrl+中键取方块 | 都是管理员或创造模式工具，会原样复制 search 状态、seed 和物品，不构成生存模式复制漏洞 |
| 结构模板里带着一个已初始化的容器 | 所有实例共享同一个 seed 和状态。这是创作错误，`BuildingAuthoringService` 已禁止模板包含物品或 loot |

## 15. 比较器

目前没有任何 AFL 容器提供比较器输出，V1 没有修改原版比较器。扩展点 `AflContainerSearch.revealedAnalogSignal(be)`：用原版满度公式、只统计已揭示的格子。以后可搜索容器如果要支持比较器，应在 `getAnalogOutputSignal` 里使用它，而不是 `AbstractContainerMenu.getRedstoneSignalFromBlockEntity`（后者会读到隐藏格）。

## 16. 与 Locked Inventory Slots V1 的兼容

- Locked Inventory 只作用于由 `Inventory` 承载的玩家格子，判断依据是 `PlayerStorageCapacity.isLocked(slot)`。搜索格由遮罩视图或客户端镜像承载，永远不会被判定为锁定。
- 两个系统的 Mixin 和覆写在同一个点击流程里各管各的：
  - `LockedInventoryMenuMixin` 对 `mayPlace`/`getItem` 的重定向会以虚方法调用到搜索格的覆写；
  - `LockedInventoryWrapperMixin` 只处理 `getInv() instanceof Inventory`。
- 背包锁定格和容器隐藏格可以同时存在（例如 Shift 把已揭示物品转入玩家背包时，锁定格照常被跳过），两个系统互不耦合。

## 17. 噪声 hook

- `AflContainerSearchSettings.noiseRadius` 和 `noiseIntervalTicks` 都大于 0 时，搜索进行中每隔 interval 发出一次噪声，经现有 `NoiseSystem.emit` 送入感染者听觉。
- 噪声类型为 `INTERACTION`，声源是当前搜索者，位置是容器中心，sourceId 是方块 ID。
- 两者任一为 0 即关闭；框架默认关闭。
- 没有新增音效，也没有定正式数值；以后由资产配置，例如钢柜较响、木柜较轻。

## 18. 事件 hook

`AflSearchableContainer` 的默认空方法都在服务端调用：

| 方法 | 调用时机 |
|---|---|
| `onAflSearchStarted(ServerLevel)` | 会话开始 |
| `onAflSearchStopped(ServerLevel)` | 未完成就暂停 |
| `onAflSearchSlotRevealed(ServerLevel, slot)` | 揭示了一格 |
| `onAflSearchCompleted(ServerLevel)` | 全部完成 |

资产可以在这里播放声音。框架不控制门、动画或方块状态。

## 19. 客户端表现 contract

同步通道是原版菜单 data slot，**没有新增网络包**，也没有使用项目的 `AflNetwork` 频道。data slot 的顺序：

| 顺序 | 内容 |
|---|---|
| 1 | reveal mask，每 16 位一个 data slot（54 格共 4 个） |
| 2 | 当前格（-1 = 无） |
| 3 | 当前格时长 |
| 4 | 当前格开始 tick 的低 16 位 |
| 5 | 标志位（搜索中 / 已完成 / 已同步），放在最后，客户端据此知道初始状态已经收齐 |

同步开销：
- 只有值变化时才发送。
- 每揭示一格，每个查看者大约收到：1 个 slot 包 + 1 个 mask 包 + 当前格、开始 tick、时长各 1 个包。
- 一格搜索的过程中不发任何包，也不会每 tick 发送整个库存或整个 mask。
- 客户端用自己同步的 game time 按"开始 tick + 时长"插值计算进度（有符号 16 位差值，时钟略微落后时进度为 0，不会倒跳）。

`AflContainerSearchMenu` 在客户端提供的 API：
- `isSlotRevealed(slot)`
- `currentSearchSlot()`
- `currentSlotProgress(partialTick)`：0–1
- `isSearching()`
- `isSearchComplete()`
- `revealedCount()`
- `revealAge(slot, partialTick)`：本客户端看到揭示后经过的 tick 数；打开时已经揭示的格子为无穷大，所以打开界面时不会给所有格子重播揭示动画。

当前 `AflContainerSearchScreen` 只是**开发占位**，以后的正式 UI 替换这个 Screen 即可，数据 contract 不变：
- 原版箱子背景；
- 隐藏格：只有深色遮罩，不再显示 "?"；
- 当前格：遮罩上一个 7×7 像素的小放大镜，围绕格子中心以 2 GUI 像素半径缓慢转圈，每 1.6 秒一圈（2026-09-29 取代原来从下往上的进度填充；储物柜接入时由 1 秒放慢到 1.6 秒）。放大镜下方的格子底边有一条很淡的 1 像素进度线（alpha 0x70），这是唯一的进度表现。纯色块绘制，没有贴图；
- 刚揭示的格子：约 6 tick 的淡出闪光；
- 标题行右侧显示 `已揭示/总数`。

## 20. 与 Animated Block Mesh Runtime 的关系

**Animated Block Mesh Runtime 不是 Container Search System**，两者是可以组合的独立模块：
- 搜索系统不要求容器使用 AFL Animated Mesh，纯 cube 或原版外观的箱子也能接入。
- 会动画的柜子也可以是玩家自己的普通储物，不做搜索。
- 搜索系统不负责门的角度、铰链 pivot 或 mesh 动画，最多提供第 18 节的 hook。以后储物柜的开门动画应该走"查看者计数 → `OPEN` → 动画运行时"这条路径，与 reveal 无关。

## 21. 开发演示与 debug

开发演示方块 `apocalypse_firstlight:dev_search_crate`：
- 代码位于 `src/dev/java/com/antaurora/apofirstlight/dev/containersearch/`，发布 jar 通过 `exclude 'com/antaurora/apofirstlight/dev/**'` 排除，只在开发环境注册。
- 18 格（2 行），40 ticks/格（与框架默认一致），jitter 0.2，噪声 8 格/40 ticks。
- 没有物品、模型、贴图、语言键或掉落表，显示为缺失模型。
- 这是开发专用方块，不是生存内容，所以没有挖掘标签，用手即可破坏。

用法：
- 世界 loot（需要搜索）：`/setblock ~ ~ ~ apocalypse_firstlight:dev_search_crate{LootTable:"minecraft:chests/simple_dungeon"}`
- 玩家储物（完全揭示）：不带 NBT 放置。
- 查看状态：`/dev container_search info`（OP 2 级，看向容器）。输出 required、seed、revealed/total、complete、running、current、progress、searchers。此命令只读，不会触发初始化。

发布路径没有常规日志输出。

## 22. V1 明确不支持 / 已知限制

- 只提供两种布局：9×1–9×6 的箱子网格，以及 3×3 的发射器网格（2026-09-30）。
  - 默认按格数推出箱子网格：`AflSearchableContainer.aflSearchLayout()` 默认对 9、18……54 格返回对应行数，其它格数返回 null，菜单不会打开。
  - 9 格的容器想用 3×3，就覆写 `aflSearchLayout()`，返回 `AflContainerSearchLayout.GRID_3X3`。
  - 其它布局（例如 5 格工具箱）仍需在 `AflContainerSearchLayout` 里补一种，并配上对应的原版背景。
- 2026-09-30 为支持 3×3，`AflContainerSearchMenu` 从继承 `ChestMenu` 改为继承 `AbstractContainerMenu`，自己按布局摆格子。箱子网格的格子坐标、Shift 点击转移、`stillValid`、关闭时的 `stopOpen` 都与原版 `ChestMenu` 逐行一致，搜索同步逻辑没有改动；菜单注册名不变。界面（`AflContainerSearchScreen`）对 3×3 使用原版 `dispenser.png` 背景，标题居中，和原版发射器界面相同。
- 半格进度不持久化；没有按玩家分别记录的搜索；没有重新布防。
- 以下功能都没有做：
  - 快速搜索与仔细搜索双模式；
  - 隐藏夹层、撬锁、钥匙、保险箱；
  - 第一人称手部翻找动画；
  - 正式 UI 美术；
  - 技能、特质、光照或手电修正（只留了入口）。
- 没有修改 LootTable、建筑 loot 或世界生成；没有修改货架、售货机、展示柜；没有全局替换原版箱子。
- 仍然存在的信息路径见第 7 节。

## 23. industrial_locker 接入（2026-09-29 已完成，以下为当时的计划）

1. `IndustrialLockerBlockEntity` 实现 `AflSearchableContainer`，加入状态字段、settings 和 `aflSearchRequiredOnInit() = lootTable != null`；按第 4 节补齐 5 个覆写，以及 load/save。54 格对应 6 行。
2. `IndustrialLockerBlock` 里的 `playerWillDestroy`、`destroyFromSupport`，以及 `IndustrialMaterialExplosionDrops` 中的储物柜分支，把原来的 `dropContentsOnce` 改为调用 `dropContentsOnBreak`，保留原有的"只掉一次"保护。方块物品的掉落规则不变。
3. 开门动画单独接：查看者计数 → `OPEN` → Animated Block Mesh Runtime，与本系统无关。
4. 确定钢柜的计时和金属翻找噪声参数，并在正式 UI 美术完成后替换占位 Screen。
5. 保持 `pickaxe` + `needs_diamond_tool` 标签不变，做一次生存模式挖掘和掉落检查。

## 24. 验证记录

- 以本机 Forge 47.4.22 / Minecraft 1.20.1 mappings 源码静态核对了以下类：
  - `RandomizableContainerBlockEntity`、`BaseContainerBlockEntity`
  - `HopperBlockEntity`、`VanillaInventoryCodeHooks`、`InvWrapper`
  - `AbstractContainerMenu`、`ChestMenu`、`Slot`、`Container`
  - `ServerPlayer`、`Player`、`ClientboundContainerSetDataPacket`
  - `AbstractContainerScreen`
- `compileJava --offline` 一次，PASS。
- 2026-09-29 用户实机测试 V1，确认全部 PASS。代理自己没有进行实机、GameTest、多人或抓包验证。
- 之后的图标与 40 ticks 调整：`compileJava --offline` 一次 PASS，未实机复测。
