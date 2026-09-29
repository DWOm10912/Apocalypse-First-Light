# Locked Inventory Slots V1

状态：代码已实现，客户端、真实服务端交互及存档回归待用户实机验证。本轮仅运行一次 `compileJava --offline`，结果见交付报告；没有执行 build、GameTest 或 runClient。

## 唯一容量策略

`src/main/java/com/antaurora/apofirstlight/inventory/PlayerStorageCapacity.java` 的 `getUnlockedInventorySlots(Player)` 是 Player Storage Capacity Policy 的唯一入口。

| 模式 | 普通储物容量 | 锁定行为 |
|---|---|---|
| Survival / Adventure | 9 | 内部索引 0–8 开放；9–35 锁定 |
| Creative / Spectator | 36，原版完整容量 | 所有容量限制、遮罩和提示关闭 |

使用 `Player.isCreative()` / `isSpectator()`；当前 Minecraft 服务端读取真实 game mode，客户端读取同步的 PlayerInfo game mode。不用 `mayBuild` 推断模式。每次交互与界面绘制即时查询，无 tick 轮询、额外网络包或独立玩家存档。

Slot 必须由 `Inventory` 容器承载，并通过 `getContainerSlot()` 取得内部储物索引；不依据菜单中的 `Slot.index` 或 GUI 坐标。36 格原版数组与布局保留；盔甲、副手、合成、容器及机器槽不锁定。

## 锁定及 Overflow

- **LOCKED_EMPTY**：拒绝放入、合并、Shift 转入、数字键反向交换、拖拽分配和自动拾取分配。
- **LOCKED_OCCUPIED**：保留原 ItemStack/NBT 与索引；可整组、拆分、Shift 或双击收集取出，不可向该槽追加/换入物品。取空后自然成为 LOCKED_EMPTY。
- 切换模式不搬运、清除或抛出已有物品。读取、存档、`setItem` 状态恢复及 `replaceWith` 未被限制；管理员直接写入锁定槽可形成 Overflow。`/clear` 保持原版。
- 死亡沿用原版掉落/keepInventory 与 Forge clone 语义，无新增复制或迁移；容量机制本身不另作死亡掉落。Overflow 仍保存在原版物品栏，未来重量统计可以读取。
- `/give`、关闭容器返还与现有交易的“无空间”回退沿用原有逻辑。地面拾取在无合法目标时保留地面实体，不做拾取后立即丢出的处理。副手已有兼容堆叠仍可按原版合并。

## 共用执行路径

项目已有 Mixin 基础设施；Forge 拾取事件无法覆盖原版点击、合并及内部插入，因此只在既有操作入口加入容量判断，不重写点击引擎。

所有 Mixin 位于 `src/main/java/com/antaurora/apofirstlight/mixin/`：

- `LockedInventoryMixin`：限制 `getFreeSlot` / `getSlotWithRemainingSpace` 的查找范围；拒绝 `add(index, stack)` 的锁定目标；约束 pick-block 反向交换。不修改实际列表尺寸或任意取出操作。
- `LockedInventorySlotMixin`：`mayPlace` 与 `safeInsert` 拒绝锁定目标；仅在 `tryRemove` 内放宽原版与 mayPlace 耦合的部分取出判断，仍检查 mayPickup。
- `LockedInventoryMenuMixin`：点击/拖拽/数字键及容器空槽转移验证；Shift 转移的合并阶段另行跳过锁定目标，因为原版该分支不查询 mayPlace。还拒绝伪造 SWAP packet 将锁定索引作为交换目的地。
- `LockedInventoryWrapperMixin`：Forge `InvWrapper.insertItem` / `isItemValid` 共用策略，覆盖 PlayerMainInvWrapper / PlayerInvWrapper 的正常插入，包括 simulate；提取及直接状态恢复不变。
- `LockedInventoryRecipeMixin`：配方书清空合成格的空间预检只统计合法空位，避免把锁定空格误判成可用空间；不锁定合成格。

这些共同侧校验在服务端执行，客户端镜像用于正常操作预测。现有 AFL 售货/货架物品返还调用 Inventory.add；维护台 `GunMaintenanceBenchBlockEntity.takeBack` 用 getFreeSlot 查找回退空位。`AttachmentInteractionCore` 的附件/多余弹药返还只在统一容量内合并和找空位，失败回滚仍完整恢复 36 格快照，取用 Overflow 中的附件仍允许。枪械与附件玩法参数不变。

## GUI 与提示

`src/main/java/com/antaurora/apofirstlight/client/LockedInventorySlotRendering.java` 使用 Forge `ContainerScreenEvent.Render.Foreground`，对当前画面的玩家储物槽绘制灰色遮罩和一条 1 GUI px、左下至右上的斜线。空槽使用 `0x80252A30` 遮罩；Occupied 降为 `0x30252A30`，保持物品可读。无锁头、X、动画或新 PNG。

`mixin/client/LockedInventoryTooltipMixin.java` 在 AbstractContainerScreen 接入空槽提示，并为 Overflow 保留原物品 tooltip 后追加说明。箱子和机器界面沿用同一逻辑。语言键前缀为 `tooltip.apocalypse_firstlight.inventory.`：

| 状态 | 中文 | English |
|---|---|---|
| locked | 未解锁的背包栏位 | Locked inventory slot |
| locked.hint | 装备背包可增加储物空间 | Equip a backpack to increase storage capacity |
| overflow | 超出当前储物容量 | Exceeds current storage capacity |
| overflow.hint | 可以取出物品，但无法放入新的物品 | Items can be removed, but new items cannot be inserted |

背包提示为已约定的后续玩法文案；**Backpack、Weight、Stamina、扩容物品及 Custom Inventory 均未实现**。后续 18/27/36 容量只应扩展同一策略入口。

## 验证边界

已静态核对本机 Forge 47.4.22 / Minecraft 1.20.1 的 Slot、Inventory、AbstractContainerMenu、InvWrapper 与配方书方法，以及 AFL 的返还和 clone 入口。编译不能证明 Mixin 实际加载、GUI 外观或多人交互通过。

手测重点：生存/冒险 9 格；锁定槽拒绝点击、拖拽、数字键放入；满快捷栏地面拾取/箱子与机器 Shift 转入失败；旧存档与创造切生存的物品保留且可部分/双击取出；转创造遮罩消失；旁观不受本机制限制；keepInventory 两种死亡规则沿用原版。

第三方模组若直接修改 Inventory.items / setItem 或自行重写转移且绕开上述插入接口，需单独接入同一策略。本实现有意保留这些底层写入能力供存档同步、管理员写入和事务回滚，不能把任意直接写入一概视为正常玩家插入。
