# 商用玻璃双开门 V1（已实现）

> **2026-10-07：模型、渲染、门扇布局和开门方向已由 V2 取代，见 [commercial_glass_double_door_v2.md](models/commercial_glass_double_door_v2.md)。** 下面仍然有效的是：ID、方块状态、四格结构、放置条件、开关交互和 12 tick 防连点、音效、掉落、挖掘（铁级例外）、重量和搬运标签。已经过时的是：GeckoLib 模型和动画、正中一对窄门扇加两侧固定玻璃的布局、"往背离 facing 一侧开"的碰撞、粒子用 `textures/entity` 贴图、物品用 GeckoLib 渲染器、V1 的源文件和导出工具。下文"源与运行时资源"里列的 V1 源文件、导出工具、动画和 `textures/entity` 贴图已于 2026-10-07 删除（用户同意）。挖掘等级用户决定先保持铁级。

## 当前行为

- 注册 ID：`apocalypse_firstlight:commercial_glass_double_door`。这是铝色框架的双扇铰链门，不是滑门，也没有自动感应。
- 占地及外框保持 2（宽）×1（深）×2（高）格。四个方块状态共享 `facing`、`open` 与 `part`；下层左侧是唯一的 Block Entity / 动画主节点。
- 放置时需同时有四格空间、两格结实支撑面，并检查流体、世界边界及玩家使用权限；任一位置不合格则拒绝放置。
- 右击任一组成部分统一切换双扇开合。服务端同步四格状态，由主节点触发 GeckoLib 动画和一次相应音效；12 游戏刻内重复点击不再切换。
- 顶部横窗与横梁下缘位于约 1.875 格高度，外框顶端仍为 2 格；门扇没有整体拉伸。开门时中央碰撞区域清空，侧框、顶部横梁和向侧面旋转的门扇仍有碰撞。
- 破坏任一部分会清理整组。生存模式使用正确工具时掉落一个门物品；创造模式不掉落。挖掘要求镐及铁级或以上，依照本版门的任务要求；虽然铝质工业结构的通用默认规则是钻石级，本门采用明确指定的铁级例外。
- 物品在创意栏可取得，提供中英文名称。贴图与音效资源随 Mod 打包。
- Block Entity 的渲染边界按四朝向覆盖完整双格、双层门及门扇转开空间，避免主方块离开视锥时整门被提前裁剪。
- 方块状态引用仅含 `particle` 的无几何模型，粒子使用门自己的贴图 `textures/entity/commercial_glass_double_door.png`（2026-09-30 起；原来借用的铝块贴图随铝块一起被 Material System V1 删除）；静态方块模型不参与门的绘制，实际门体仍由 GeckoLib 渲染。

## 源与运行时资源

- 可编辑源：`src/main/blockbench/commercial_glass_swing_door.bbmodel`；作者贴图：`src/main/blockbench/textures/commercial_glass_swing_door.png`。
- 导出同步：`tools/export-commercial-glass-double-door.mjs`。运行时几何、动画、透明贴图分别位于 `src/main/resources/assets/apocalypse_firstlight/{geo,animations,textures/entity}/commercial_glass_double_door.*`。
- 音效注册为 `commercial_glass_double_door_open` 与 `commercial_glass_double_door_close`，资源位于 `sounds/glass_door_open.ogg`、`sounds/glass_door_close.ogg`。
- 方块、主 Block Entity 和渲染实现分别位于 `CommercialGlassDoubleDoorBlock`、`CommercialGlassDoubleDoorBlockEntity`、`CommercialGlassDoubleDoorRenderer`。物品使用专用 GeckoLib 渲染器。
- 粒子模型：`src/main/resources/assets/apocalypse_firstlight/models/block/commercial_glass_double_door.json`，由同名 `blockstates` 文件引用。物品模型的粒子也使用这张门贴图。门贴图不在 `textures/block/` 下，所以在 `assets/minecraft/atlases/blocks.json` 里以 `single` 来源加入方块图集，粒子才能取到。粒子会随机取到门贴图的一小块，可能取到半透明玻璃区域；换贴图后的粒子外观未实机查看。

## 验证边界

- Gradle 构建通过。专用 GameTest 通过四朝向、四部分放置与清理、渲染边界覆盖四个 part、开门至 1.875 格的中央碰撞净空、关门碰撞恢复、12 刻防连点、受阻放置、铁级工具与石级工具的掉落要求。
- 客户端启动日志没有本门缺失贴图警告。2026-09-12 用户在客户端复测近距离转动视角和挖掘粒子，反馈两项均正常；这是用户实机验收，不是本次自动化截图验收。此前开发客户端还确认过实际摆放、透明显示、开门后的中央通道外观及再次右击后的闭合外观。实际玩家穿行和真实声学听感尚未单独验收。

## 黑框变体（2026-10-06）

新增 `apocalypse_firstlight:commercial_glass_double_door_black`（玻璃双开门（黑框）），配合店面玻璃幕墙。银框门保留不变。
- 两种门共用一个类、方块实体类型、GeckoLib 模型、动画和音效；按方块选贴图 `textures/entity/commercial_glass_double_door_black.png`（由 `tools/build-storefront-glazing-v1.mjs` 从银框贴图重新上色）。
- 掉落改为掉门自己的物品（原来三处写死银框门），所以银框门的行为不变，黑框门掉黑框门。
- 挖掘等级（铁级）、重量、搬运标签和银框门一样。
- 详见 [storefront_glazing_v1.md](models/storefront_glazing_v1.md)。没有实机验证；编译要等同期的道路代码补全后再确认。
