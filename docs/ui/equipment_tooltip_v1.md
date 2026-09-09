# 装备 Tooltip V1

## 当前布局

覆盖 P9-01、BR51-01 及六种 Native Attachment。保留 Vanilla ItemStack hover name / rarity，不重复追加名称。正文统一：灰色且显式非斜体介绍 → 深灰 20 字符横线 → 属性行 → 同款横线。使用普通 Component 与 Vanilla tooltip，AFL 装备最大宽度 300 GUI 像素并尊重更小的现有限制，按窗口边界自动换行；不覆盖其他物品的布局。

`src/main/java/com/antaurora/apofirstlight/tooltip/`：

- `AflEquipmentTooltip`：统一 description / separator / stat / gun / attachment formatter，纯读取，不修改 NBT。
- `AflTooltipStatType`：固定类别色，键名使用类别色，值统一 `#E0E0E0`。
- `AflEquipmentTooltipLayout`：客户端装备宽度限制，不接管原版边框或名称。

| 类别 | 颜色 | 本轮显示 |
| --- | --- | --- |
| DAMAGE | `#D97878` | Gun Definition baseDamage |
| AMMUNITION | `#D6B46A` | 正式 ammoType 对应弹药的 caliber 本地化；无 caliber 键回退弹药物品名称 |
| MAGAZINE | `#79AFC9` | 枪：NativeGunAmmo.capacity，P9 17/24、BR51 20/35；附件：兼容枪默认容量 → 附件容量 |
| FIRE_MODE | `#D79A68` | JSON fire.mode，经 NativeGunDefinition.fireMode 保存并本地化；当前验证器仍只接受 semi |
| RANGE | `#82B98A` | 全枪统一 effectiveRange，单位格；不混用 falloffStart/maxRange |
| RECOIL | `#C49567` | 仅保留类别，不编造单值评分 |
| NOISE | `#9B8FC3` | 枪：NativeGunNoise.resolve 的 AI 听觉范围；消音器：真实倍率的百分比变化 |
| ADS | `#79AAA7` | 两款现有红点显示“红点瞄具 / Red Dot”，不显示 anchor/offset 或未定义的数值增益 |

## 数据与边界

消音器按 `(multiplier - 1) × 100` 显示带符号百分比：0.05 → -95%，0.20 → -80%。这是 gameplay noise 范围，不是音量或音频衰减。枪械显示最终范围：P9 64→3，BR51 112→6。

弹匣从 `NativeMagazineItem.compatibleGun()` 查询 live `NativeGunData` 默认容量，从附件 `capacity()` 获取装备容量，得到 17→24 或 20→35。当前弹匣定义是一枪专用；未来多枪兼容须扩展正式兼容数据和行生成，不按枪名猜容量。属性生成返回 Stat 列表，一个附件可追加多个不同类别，formatter 不限制为单行。

本地化：`tooltip.apocalypse_firstlight.stat.*`、`value.*`、现有每物品 description，以及弹药物品 `.caliber`。旧 spec/type/调试教程不再被 Item 调用；旧文案键不代表现行布局。灰色介绍显式 `withItalic(false)`，属性及横线同样非斜体；不修改用户自定义名称、原版高级提示或第三方 Mod 自行追加的信息。

HUD 仍为单行“当前装弹 | 备弹”；容量仅在 Tooltip 属性区展示。没有改伤害、容量、噪声倍率、射程、射速、后坐力、ADS、兼容、换弹、维护台、弹药消耗或声音。新增 fireMode 和 compatibleGun 仅用于读取已存在的数据，不新增射击模式。装拆仍仅限维护台，无快捷安装说明。

## 验证

构建：`build/equipment-tooltip-build.log`。客户端测试与视觉检查结果在收尾补充；旧 Tooltip 清理文档的构建/测试记录不代替本轮验收。
