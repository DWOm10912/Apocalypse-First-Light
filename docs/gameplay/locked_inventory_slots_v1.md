# Locked Inventory Slots V1

状态：**Locked Inventory Slots V1 Result = 全部 PASS**。2026-09-29 用户确认已完成实机测试，并要求将本轮验收项目全部标记为 PASS。此前唯一一次 `compileJava --offline` 已通过；本次仅更新验收记录，没有重新编译或启动测试。

## 2026-10-15 容器界面里的网格、配件返还（分支 `feature/inventory-grid-v0`，代码完成，未经游戏内验证）

见 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md) 第 13 节：

- 网格动作（`GridActions`）不再只认背包界面：任何打开的、菜单里有玩家物品栏格子的容器界面都可以，光标用那个界面自己的。
- 新动作 `TO_CONTAINER`：Shift 点网格里的东西，按容器菜单自己的 Shift 规则放进去。做法是把它临时放进一个空的锁定主栏格让菜单搬；菜单没搬走的、搬到玩家其它格子的，都拿回网格原位。搬的过程中 `LockedInventoryMenuMixin` 不把剩余塞进网格（`GridActions.movingIntoContainer()`）。
- `AttachmentInteractionCore`：配件来源可以是网格里的东西（`GridSource` 编码）；换下的配件和多余子弹按正常拾取顺序放回（`Inventory.add`，生存 / 冒险进网格），放不下才掉地上。以前是直接写进开放的前 4 格，会落进武器位或“手上”。失败回滚同时恢复 36 格和网格（`PlayerGrids.snapshot`）。

## 2026-10-14 武器位和快捷绑定（分支 `feature/inventory-grid-v0`，代码完成，未经游戏内验证）

替换了下一节里“快捷栏分工”和“只找 6–9”两条，见 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md) 第 10 节：

- 生存 / 冒险开放 4 格（`getUnlockedInventorySlots` = 4）：0 主武器（枪上要有背带标记 `AflSling`）、1 手枪（要穿手枪套，Curios 栏 `holster`）、2 近战、3 “手上”（快捷绑定 4–9 和 E 界面“手上”框共用）。4–8 和 9–35 一样锁定，里面的旧东西是溢出。
- `HotbarRoles.accepts(player, index, stack)` 是“这格收不收这件”的判断，`PlayerStorageCapacity.rejects` 调它；“手上”收任何东西，但只有绑定和“手上”框会往里放。
- `isGenericTarget` 在生存 / 冒险一格都不算：`getFreeSlot`、合并都找不到快捷栏，捡到的普通物品直接进网格，也不会叠到手上那叠里。武器照旧先进它的空武器位（`InventoryGridMixin`）。
- 界面里 Shift 转移（`moveItemStackTo`）不进“手上”；原版找不到位置的剩余，在服务器上放进网格（`LockedInventoryMenuMixin`）。
- 配方书的空位预检在生存 / 冒险按网格空格数（`PlayerGrids.View.freeCells`）。
- 不该在快捷栏里的东西（旧存档、卸了背带的枪）不在手上时，`Loadout` 每秒尝试放进网格，放不下就留着当溢出。

## 2026-10-14 网格背包 V0 改动（分支 `feature/inventory-grid-v0`，阶段 1，用户 2026-10-14 实机 PASS；前两条已被上一节替换）

见 [afl_inventory_grid_v0_plan.md](../ui/afl_inventory_grid_v0_plan.md)。本页下面的规则仍然有效，改了这些：

- **快捷栏有了分工**（`inventory/HotbarRoles.java`）：键 1–2 主武器、3–4 副武器、5 近战，6–9 什么都能放。武器栏只收对应武器（物品标签 `apocalypse_firstlight:weapons/primary|secondary|melee`；没打标签的 Native 枪按类型：手枪算副武器，其它算主武器）。`PlayerStorageCapacity.rejects(...)` 是“这格收不收这件”的统一判断：锁定格，或武器栏放非对应物品，都拒绝。旧存档里放在武器栏的非武器算溢出：能取，不能放。
- **自动找位置只找 6–9**（`isGenericTarget`）：`getFreeSlot`、合并、配方书空位统计都不再把武器栏算进去。捡起武器时先放进它那一类的空武器栏（`InventoryGridMixin`），满了才走普通流程。
- **放不下的进网格**：`Inventory.add` 原版流程结束后还有剩余，就按 口袋 → 胸挂 → 背包 放进网格（服务端、生存 / 冒险）。`placeItemBackInInventory`（关闭界面时手上的东西、合成剩余）也走同一路径，都放不下才丢在地上。
- **背包不再解锁原版格子**：`StorageExpander` 接口还在，但 AFL 的背包用自己的网格，所以生存 / 冒险的原版容量固定是快捷栏 9 格；9–35 只剩“溢出”用途，在新 E 界面右边的“溢出”区显示，只能取出。
- **生存 / 冒险按 E 打开的是 AFL 背包界面**，不再是原版物品栏；灰色遮罩和锁定提示现在只在箱子、机器等容器界面里出现。`locked.hint` 文案改为“物品放进口袋、胸挂和背包的格子”。
- 创造 / 旁观不变：完整 36 格，没有武器栏分工。

## 唯一容量策略

2026-10-03 更新：生存 / 冒险模式的容量改为“快捷栏 9 格 + 背部栏物品提供的格数”，最多 36（`inventory/StorageExpander`，见 `equipment_slots_v1.md`）。V1 还没有提供格数的背包，所以下表的 9 格仍然是当前实际值。

`src/main/java/com/antaurora/apofirstlight/inventory/PlayerStorageCapacity.java` 的 `getUnlockedInventorySlots(Player)` 是 Player Storage Capacity Policy 的唯一入口。

| 模式 | 普通储物容量 | 锁定行为 |
|---|---|---|
| Survival / Adventure | 9 | 内部索引 0–8 开放；9–35 锁定 |
| Creative / Spectator | 36，原版完整容量 | 所有容量限制、遮罩和提示关闭 |

使用 `Player.isCreative()` / `isSpectator()`；当前 Minecraft 服务端读取真实 game mode，客户端读取同步的 PlayerInfo game mode。不用 `mayBuild` 推断模式。每次交互与界面绘制即时查询，无 tick 轮询、额外网络包或独立玩家存档。

Slot 必须由 `Inventory` 容器承载，并通过 `getContainerSlot()` 取得内部储物索引；不依据菜单中的 `Slot.index` 或 GUI 坐标。36 格原版数组与布局保留；盔甲、合成、容器及机器槽不锁定。副手 2026-10-16 起整个去掉（任何模式都放不进，见 `docs/ui/afl_inventory_grid_v0_plan.md` 第 16 节）。

## 锁定及 Overflow

- **LOCKED_EMPTY**：拒绝放入、合并、Shift 转入、数字键反向交换、拖拽分配和自动拾取分配。
- **LOCKED_OCCUPIED**：保留原 ItemStack/NBT 与索引；可整组、拆分、Shift 或双击收集取出，不可向该槽追加/换入物品。取空后自然成为 LOCKED_EMPTY。
- 切换模式不搬运、清除或抛出已有物品。读取、存档、`setItem` 状态恢复及 `replaceWith` 未被限制；管理员直接写入锁定槽可形成 Overflow。`/clear` 保持原版。
- 容量机制本身不另作死亡处理。2026-10-14 起生存 / 冒险模式的死亡由幸存者死亡与随机继任 V0 接管：全部 36 格（含锁定格 Overflow）、盔甲、副手与 Curios 物品进入尸体，keepInventory 不再保留物品，继任者空手开始，见 [survivor_death_succession_v0.md](survivor_death_succession_v0.md)。Overflow 仍保存在原版物品栏，[Weight Core V1](weight_system_v1.md) 独立读取全部36格计重，不改变这里的容量规则。
- `/give`、关闭容器返还与现有交易的“无空间”回退沿用原有逻辑。地面拾取在无合法目标时保留地面实体，不做拾取后立即丢出的处理。拾取不再合并进副手（2026-10-16 起没有副手）。

## 共用执行路径

项目已有 Mixin 基础设施；Forge 拾取事件无法覆盖原版点击、合并及内部插入，因此只在既有操作入口加入容量判断，不重写点击引擎。

所有 Mixin 位于 `src/main/java/com/antaurora/apofirstlight/mixin/`：

- `LockedInventoryMixin`：限制 `getFreeSlot` / `getSlotWithRemainingSpace` 的查找范围；拒绝 `add(index, stack)` 的锁定目标；约束 pick-block 反向交换。不修改实际列表尺寸或任意取出操作。
- `LockedInventorySlotMixin`：`mayPlace` 与 `safeInsert` 拒绝锁定目标；仅在 `tryRemove` 内放宽原版与 mayPlace 耦合的部分取出判断，仍检查 mayPickup。
- `LockedInventoryMenuMixin`：点击/拖拽/数字键及容器空槽转移验证；Shift 转移的合并阶段另行跳过锁定目标，因为原版该分支不查询 mayPlace。还拒绝伪造 SWAP packet 将锁定索引作为交换目的地。
- `LockedInventoryWrapperMixin`：Forge `InvWrapper.insertItem` / `isItemValid` 共用策略，覆盖 PlayerMainInvWrapper / PlayerInvWrapper 的正常插入，包括 simulate；提取及直接状态恢复不变。
- `LockedInventoryRecipeMixin`：配方书清空合成格的空间预检只统计合法空位，避免把锁定空格误判成可用空间；不锁定合成格。

这些共同侧校验在服务端执行，客户端镜像用于正常操作预测。现有 AFL 售货/货架物品返还调用 Inventory.add；维护台 `GunMaintenanceBenchBlockEntity.takeBack` 2026-10-15 起：放枪的人取回时原位空着并且按武器位规则收这把枪才回原位（原位也可以是网格里的位置），否则走 `Inventory.add`，都放不下掉在脚边；不再用 getFreeSlot（第 10 节以后它在生存模式找不到格子，枪只会掉地上）。维护台底部改成只列枪的选枪栏，见 [维护台附件交互](../native_guns/attachments/gun_maintenance_attachment_interaction_v1.md)“选枪栏和取回”。`AttachmentInteractionCore` 的附件/多余弹药返还 2026-10-15 起走 `Inventory.add`（生存 / 冒险进网格，见本页开头），失败回滚恢复 36 格和网格快照，取用 Overflow 中的附件仍允许。枪械与附件玩法参数不变。

## GUI 与提示

`src/main/java/com/antaurora/apofirstlight/client/LockedInventorySlotRendering.java` 使用 Forge `ContainerScreenEvent.Render.Foreground`，对当前画面的玩家储物槽绘制灰色遮罩和一条 1 GUI px、左下至右上的斜线。空槽使用 `0x80252A30` 遮罩；Occupied 降为 `0x30252A30`，保持物品可读。无锁头、X、动画或新 PNG。

`mixin/client/LockedInventoryTooltipMixin.java` 在 AbstractContainerScreen 接入空槽提示，并为 Overflow 保留原物品 tooltip 后追加说明。箱子和机器界面沿用同一逻辑。语言键前缀为 `tooltip.apocalypse_firstlight.inventory.`：

| 状态 | 中文 | English |
|---|---|---|
| locked | 这一格不能用（2026-10-18 前：未解锁的背包栏位） | Unused slot（2026-10-18 前：Locked inventory slot） |
| locked.hint | 请将物品放入口袋、胸挂或背包（2026-10-18 前：物品放进口袋、胸挂和背包的格子；2026-10-14 前：装备背包可增加储物空间） | Items go into the pocket, rig and backpack grids |
| overflow | 超出当前储物容量 | Exceeds current storage capacity |
| overflow.hint | 只能取出，不能放入（2026-10-18 前：可以取出物品，但无法放入新的物品） | Items can be removed, but new items cannot be inserted |

2026-10-18 本地化批次 2（[zh_cn_polish_batch2_v1.md](../localization/zh_cn_polish_batch2_v1.md) #34–#36）改了提示文字：生存模式里这些格子永远不用，原来的"未解锁 / Locked"会让人以为以后能解锁。机制没变。

背包提示为已约定的后续玩法文案；**Backpack、Stamina、扩容物品及 Custom Inventory 均未实现**。[Weight Core V1](weight_system_v1.md) 已实现独立质量计算/同步/debug，实机待验证，尚无负重惩罚或正式HUD。后续 18/27/36 容量只应扩展同一策略入口。

## 验收结果

已静态核对本机 Forge 47.4.22 / Minecraft 1.20.1 的 Slot、Inventory、AbstractContainerMenu、InvWrapper 与配方书方法，以及 AFL 的返还和 clone 入口。实机验收依据为用户在 2026-09-29 的确认：“把Locked Inventory Slots V1 Result标记为全部pass，我测试过了”。以下均按该确认记录，不记为 Codex 自动化测试或 GameTest 结果。

| 验收项目 | 结果 |
|---|---|
| 统一 Player Storage Capacity Policy；生存/冒险 9 格 | PASS |
| Creative / Spectator unrestricted | PASS |
| Locked Empty / Locked Occupied；Overflow 可取出、不可插入 | PASS |
| 服务端限制；点击、Shift、数字键、拖拽、双击 | PASS |
| 自动拾取、容器转移及现有 AFL 返还路径 | PASS |
| 模式切换、已有存档、死亡/重生安全 | PASS |
| 盔甲、副手、合成及其他非储物槽不受锁定影响 | PASS |
| 灰色遮罩、单斜线、中英文 Tooltip | PASS |
| `compileJava --offline`（此前一次编译） | PASS |

该次用户验收范围仅为 Locked Inventory Slots V1。Backpack / Stamina 继续保持未实现；后来新增的 Weight Core V1 使用独立验收，不继承本表 PASS。

第三方模组若直接修改 Inventory.items / setItem 或自行重写转移且绕开上述插入接口，需单独接入同一策略。本实现有意保留这些底层写入能力供存档同步、管理员写入和事务回滚，不能把任意直接写入一概视为正常玩家插入。

## 与容器逐格搜索的关系（2026-09-29）

[Progressive Container Search V1](progressive_container_search_v1.md) 的"隐藏格"是另一套独立系统，两者不共用代码或状态：
- 隐藏格由遮罩视图或客户端镜像承载，不是 `Inventory`，所以 `PlayerStorageCapacity.isLocked` 对它们永远为 false。
- 本文所列的 Mixin 仍然作用于搜索菜单里的玩家背包格。
- 两套限制可以同时生效，例如把已揭示的物品 Shift 转入背包时，锁定格照常被跳过。
- 本节只记录兼容关系；Locked Inventory 的行为和验收结果没有任何改动。
