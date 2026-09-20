# 装备 Tooltip / Native Gun Tooltip V2.3 — Final

## 当前布局

所有调用 `AflEquipmentTooltip.gun` 的 Native Gun 共用冻结顺序：原版枪械名称 → 原始本地化 Subtitle → 真实 16×16 弹药 ItemStack 与弹药类型 → 枪械伤害 → 开火方式 → 有效射程 → 声音半径。介绍和弹药行之间、弹药行和属性之间由少量纵向留白区分；枪械不插入横向分割线。保留 Vanilla ItemStack hover name / rarity，不重复追加名称。

普通枪械 Tooltip 只显示上述四个文字属性；不显示武器类型、文字“弹药类型”属性行、弹匣容量、最大射程、伤害衰减起点、最低伤害倍率、ADS、后坐力、散布或爆头倍率。更详细数据留给枪械维护台，不再继续给普通悬停增加字段。声音半径显示影响感染者感知的真实 Noise Radius；底层数据、HUD、维护台、兼容和战斗逻辑未改。

枪械 Subtitle 恢复为原始本地化 Component，不再进行 240px 预拆行；需要时由原版 Tooltip 根据屏幕边界自然换行。Tooltip 总宽度由名称、完整介绍、弹药组件和最长属性行自然决定，尊重第三方既有限制及原版屏幕边界，不截断正文。Native Attachment 仅保留原版名称和灰色介绍，不再添加属性行或横向分割线；原有 300px 上限仍保留。不覆盖其他物品、原版高级提示和第三方追加信息。

`src/main/java/com/antaurora/apofirstlight/tooltip/`：

- `AflEquipmentTooltip`：统一 description / gun stat / attachment formatter，纯读取，不修改 NBT。
- `AflTooltipStatType`：固定类别色，键名使用类别色，值统一 `#E0E0E0`。
- `AflEquipmentTooltipLayout`：客户端弹药行混排及附件宽度限制，不接管枪械 Subtitle、原版边框或名称。
- `GunAmmoTooltipComponent`：common/server-safe 数据，只保存复制的 ItemStack 与 Component，不引用客户端渲染器。
- `NativeGunNoise.resolve(stack, definition)`：沿用真实枪械/枪口附件噪声计算结果；Tooltip 只格式化半径数值。

| 类别 | 颜色 | 本轮显示 |
| --- | --- | --- |
| DAMAGE | `#D97878` | Gun Definition baseDamage |
| AMMUNITION | `#D6B46A` | 正式 ammoType 对应弹药的 caliber 本地化；无 caliber 键回退弹药物品名称 |
| FIRE_MODE | `#D79A68` | NativeFireModes.current(stack, definition)，只读当前/默认合法模式，标签“开火方式” |
| RANGE | `#8FB58A` | 标签“有效射程”，读取 `NativeGunDefinition.effectiveRange()`；数值浅灰白 |
| RECOIL | `#C49567` | 仅保留类别，不编造单值评分 |
| SOUND_RADIUS | `#A395B8` | 标签“声音半径”，附件修正后的真实声音半径，`声音半径：N格`；数值浅灰白 |

## 数据与边界

有效射程读取 live `NativeGunDefinition.effectiveRange()`，该字段由各枪 `src/main/resources/data/apocalypse_firstlight/native_guns/*.json` 的 `damage.effective_range` 解析得到；仅格式化为 `有效射程：N格`，不从 `max_range` 或 `falloff_start` 推算，不维护 Tooltip 专用数值。声音半径读取 `NativeGunNoise.resolve(stack, definition).radius()`，与 Noise System 使用的枪械定义和枪口附件修正路径一致，按 `number(...)` 格式化后显示 `声音半径：N格`。正式 JSON 当前基础半径：C.A.T 6格、P9-01 64格、BR51-01 112格、HR55 128格。安装消音器后显示其有效半径。没有 weapon-ID 特判或真实数值修改；原声响等级分档 helper 已删除。

### 图形弹药行

- `NativeGunDefinition.ammoType()` → ForgeRegistries.ITEMS → 数量 1 的真实弹药 stack；使用 caliber 本地化短名称，缺失时回退弹药物品名称。
- Common formatter 插入可读的 `%s` 翻译标记 `gun_ammo_row`；客户端 `AflEquipmentTooltipLayout` 在 Forge `RenderTooltipEvent.GatherComponents` 中原位替换为 `GunAmmoTooltipComponent`。避免 `getTooltipImage()` 默认将组件插在名称之后，不重复插入或替换其他组件。
- `client/ClientGunAmmoTooltipComponent.java` 通过 `Dist.CLIENT` MOD subscriber 的 `RegisterClientTooltipComponentFactoriesEvent` 注册。
- 不绘制弹药槽位背景、边框或额外图片，保留原版 Tooltip 自身背景/边框。
- `GuiGraphics.renderItem(ammoStack, x, y+3)` 使用 16×16 GUI 渲染，复用真实弹药的现有 3D item model 与 `ItemDisplayContext.GUI`，不复制 renderer。
- 名称在 `x+21`，与 16px 图标垂直居中，整行名称用 `#D6B46A`。组件行高 22px，上下各留 3px；宽度为 21 + 名称宽度。
- 空 stack/未注册弹药只显示名称，不画图标，不保留左侧空白。仍可回退现有 caliber/name 翻译；都不可用时显示“未知弹药 / Unknown ammunition”。
- 不要求存在玩家或世界。第三方如果只取普通文字而不调用 Forge gather，则仍能读到弹药名称。库存、创造背包和 JEI/EMI 的运行/视觉兼容尚待实机验证。

简易隔音耳罩 V1 仅显示 `AflEquipmentTooltip.addDescription` 的灰色非斜体介绍；介绍已同时写明缓解耳鸣和降低外界声音，不再重复追加功能行。Tooltip 不公开精确倍率；没有新增全局属性颜色。物品与防护行为见 [简易隔音耳罩 V1](../equipment/simple_hearing_protection_v1.md)。

消音器 Tooltip 不再显示噪声百分比；其 gameplay noise 倍率及枪械 Tooltip 中由真实噪声路径算出的有效声音半径均保持不变。

弹匣 Tooltip 不再显示 17→24 或 20→35 的容量变化；附件的真实容量、兼容和换弹行为不变。瞄具 Tooltip 不再显示重复的“瞄准：红点瞄具”；三类配件均仅保留介绍。

本地化：枪械仍使用所需的 `tooltip.apocalypse_firstlight.stat.*`、`value.noise_radius`、`gun_ammo_row`、`unknown_ammunition` 与弹药物品 `.caliber`；配件保留各自的 description，已移除仅供配件额外属性使用的 `stat.magazine`、`stat.noise`、`stat.ads`、`value.red_dot` 中英文键。旧 spec/type/技术属性文案键保留不代表现行枪械布局。介绍和枪械属性均非斜体；不修改自定义名称、原版高级提示或第三方追加信息。

普通枪械 Tooltip V2.3 正式冻结为名称、Subtitle、弹药 Item + 弹药类型、枪械伤害、开火方式、有效射程、声音半径。固定低饱和颜色规范：Damage `#D97878`、Ammo `#D6B46A`、Fire Mode `#D79A68`、Effective Range `#8FB58A`、Sound Radius `#A395B8`；颜色统一由 `AflTooltipStatType` 定义，数值/内容沿用 `AflEquipmentTooltip.VALUE_COLOR` `#E0E0E0`。不按射程或半径大小切换颜色。更完整技术参数归枪械维护台职责，Native Gun HUD 负责当前战斗状态。本轮未新增维护台参数面板，也未改变 HUD 显示内容。没有改伤害、容量、噪声倍率、实际射程、射速、后坐力、ADS、兼容、换弹、维护台、弹药消耗或声音。装拆仍仅限维护台。

## 验证

V2.3 仅新增有效射程显示并更新两项标签颜色；其余布局、弹药组件、Subtitle 和附件 Tooltip 保持不变。`compileJava` / `processResources` 结果见本轮执行报告；未运行客户端、GameTest、截图或展示渲染。Dedicated Server 安全边界采用 common 数据与 Dist.CLIENT 注册隔离，未实际启动服务器；编译不代替实机视觉验收。
