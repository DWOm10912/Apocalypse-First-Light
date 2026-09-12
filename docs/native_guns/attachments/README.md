# 原生枪械配件总表

> 协议更新（Inspect V1）：当前共享通道为 **23**，新增轻量检视请求，客户端/服务端须匹配。下文关于协议 22 的描述属于历史版本；配件仍仅通过维护台安装，V 现用于检视。见 docs/native_guns/native_inspect_v1.md。

本页记录当前正式实现，并作为新增配件与属性的统一索引。模型制作记录不等于已装备接入；验证结果见各配件详情文档。本次整理仅修改文档，不新增游戏属性或数值效果。

## 配件目录

以下 ID 均使用命名空间 `apocalypse_firstlight:`；兼容性以枪械数据的 `*_slot.accepts` 为准。

| 配件 | Registry ID | 槽位 | 当前兼容枪械 | 当前主要效果 | 状态 / 详情 |
| --- | --- | --- | --- | --- | --- |
| 手枪微型红点瞄具 | `pistol_red_dot` | `SIGHT` | P9-01 | 使用红点光学轴进行 ADS 对齐；不额外增加倍率 | 已接入 / [红点 V1](../pistol_red_dot_v1.md) |
| 步枪红点瞄具 | `rifle_red_dot_01` | `SIGHT` | BR51-01 | 原 BR51 红点独立资产化；光学轴 ADS，无额外倍率 | 已接入 / [步枪红点 V1](rifle_red_dot_01_v1.md) |
| 手枪消音器 | `pistol_suppressor_01` | `MUZZLE` | P9-01 | 游戏噪声半径 ×0.05；消音声与出口视觉切换 | 已接入 / [手枪消音器 V1](pistol_suppressor_01_v1.md) |
| 步枪消音器 | `rifle_suppressor_01` | `MUZZLE` | BR51-01 | 游戏噪声半径 ×0.05；消音声与出口视觉切换 | 已接入，非 BR51 专属实现 / [步枪消音器 V1](rifle_suppressor_01_v1.md) |
| P9-01 扩容弹匣 | `p9_01_extended_magazine` | `MAGAZINE` | P9-01 | 弹匣容量覆盖为 24 发，替换弹匣外观 | 已接入 / [扩容弹匣 V1](../p9_01_extended_magazine_v1.md) |

| BR51-01 35发扩容弹匣 | `br51_extended_magazine_35` | `MAGAZINE` | BR51-01 | 20→35；独立 mag2 与换弹副本替换；溢出安全返还 | 已接入 / [35R V1](br51_extended_magazine_35_v1.md) |

兼容范围不按配件名称自动推断：当前手枪消音器和手枪红点不能装 BR51，步枪消音器和步枪红点不能装 P9；BR51 接受专属 `br51_extended_magazine_35`。未来步枪可通过自己的兼容声明、挂载点与 ADS 数据复用步枪配件。当前扩容弹匣另有 `NativeMagazineItem.accepts` 的逐附件枪型限制，不能仅改 JSON 就宣称支持其他枪。

## 当前属性明细

“倍率”“覆盖”“视觉”是文档分类，不代表已存在一套通用属性修改器。表内属性名优先使用实际代码字段；后续新增属性应先落实实现，再登记为已实现。

| 配件 | 属性 / 效果 | 类型 | 当前值 | 实际结果 / 边界 | 实现来源 |
| --- | --- | --- | --- | --- | --- |
| 手枪消音器 | `noiseRadiusMultiplier` | 倍率 | `0.05` | P9：64 → 3 格；最终取整并至少 1 格 | `NativeSuppressorItem`、`NativeGunNoise` |
| 步枪消音器 | `noiseRadiusMultiplier` | 倍率 | `0.05` | BR51：112 → 6 格；最终取整并至少 1 格 | `NativeSuppressorItem`、`NativeGunNoise` |
| 两种消音器 | `suppressesFireSound` | 开关 | `true` | 选择枪械定义的 `suppressed_fire_sound`，不是配件绑定同一声音 | `NativeSuppressorItem`、枪械 JSON |
| 两种消音器 | 玩家音频传播距离 | 保持 | 不乘 `0.05` | AI 听觉噪声半径与玩家音频衰减分开 | `NativeGunNoise` 与既有声音播放路径 |
| 两种消音器 | 枪口焰 / 出口 | 视觉 | 抑制裸枪焰，使用配件出口 | smoke / tracer 视觉从 `muzzle_exit_anchor` 出发，不改服务端命中规则 | `NativeMuzzleRendering`、既有 shot visual 路径 |
| 手枪红点 | ADS 光学轴 | 对齐 | P9 `ads_center = [-2.98,12.15,8.13]` | 模型坐标；不是伤害、精度或后坐力增益 | `NativeAdsProfile`、P9 `sight_slot` |
| 手枪红点 | ADS FOV / 进入时间 | 保持 | 沿用 P9 `0.95` / `0.15 s` | 当前无配件额外倍率和举枪速度加成 | P9 `ads`、`NativeAdsProfile` |
| 步枪红点 | ADS 光学轴 | 对齐 | BR51 `[0,14.8125,3.70313]` | 原源模型瞄准点中心，不改变弹道 | BR51 `sight_slot`、`NativeAdsProfile` |
| 步枪红点 | ADS FOV / 进入时间 | 保持 | BR51 `0.89` / `0.2 s` | 无高倍率效果或数值加成 | BR51 `ads` |
| P9 扩容弹匣 | `capacity()` | 覆盖 | `24` 发 | 原容量 17 → 24（差值 +7），不是全枪通用“+7”修改器 | `NativeMagazineItem`、`NativeGunAmmo` |
| P9 扩容弹匣 | 多余弹药处理 | 事务规则 | 容量降低时安全返还 | 安装不免费补弹；拆卸不吞掉超容量弹药 | `MaintenanceAttachmentTransaction` |

消音公式：`finalNoiseRadius = max(1, round(baseNoiseRadius * 0.05))`。上述数值只在实际装备且兼容的配件生效时使用。

当前六种配件均未引入额外伤害、射程、射速、散布、后坐力或换弹速度增减。不要从模型体积、重量感或现实配件效果推导出游戏数值。

## 装备入口与共用规则

| 项目 | 当前规则 |
| --- | --- |
| 唯一装拆入口 | 枪械维护台；V 快捷安装和拆卸已移除，旧快捷网络请求不再执行 |
| 维护台 | 三种槽位共用热点、上下文 HUD、候选页与服务端事务；只显示该枪支持的槽 |
| 维护台音效与提交 | 原版轻点击；共用 2.480 秒操作声；服务端等待 51 tick 后重新验证并提交 |
| 物品状态 | 配件保存在真实枪械 ItemStack 的 `AflAttachments` 中；无独立维护台视觉副本 |
| 服务端权威 | 校验兼容、源物品、枪械快照与维护台 revision；拒绝过期事务 |
| 最大堆叠 | 当前六种配件均为 1 |
| 协议 | 当前 22；两端必须匹配 |

完整规则见 [维护台配件交互](gun_maintenance_attachment_interaction_v1.md) 与 [UI/SFX](gun_maintenance_attachment_ui_sfx_polish_v1.md)。旧快捷报文编号保留为空操作，协议仍为 22；不再提供绕过维护台的玩家入口。

## 后续属性扩展模板

新增配件先加入目录；每个属性单独一行，正面收益和负面代价都记录。没有实现依据的数值填“待定”，状态写“计划”，不得混入上面的当前属性表。

| 配件 ID | 属性 | 运算方式 | 值 / 单位 | 生效条件 | 叠加 / 上限规则 | 实现位置 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `<新配件 ID>` | `<属性名>` | `<加算 / 乘算 / 覆盖 / 开关>` | 待定 | `<兼容枪械、槽位、动作条件>` | 待定 | 未实现 | 计划 |

可按后续设计登记伤害、后坐力、散布、ADS 时间、换弹时间、容量、噪声等属性；这只是文档可用字段，不表示已承诺或实现这些修改器。

每次接入核对：兼容范围、基础值与最终值、单位、取整、冲突及叠加顺序、服务端生效、客户端显示、拆卸恢复、存档和多人验证。若某项未测试，明确写“未测试”。

## 维护依据

| 内容 | 代码 / 数据路径 |
| --- | --- |
| 槽位与默认语义 | `src/main/java/com/antaurora/apofirstlight/weapon/NativeAttachment.java` |
| 装备、兼容、存储 | `src/main/java/com/antaurora/apofirstlight/weapon/NativeAttachments.java` |
| 消音属性 | `src/main/java/com/antaurora/apofirstlight/weapon/NativeSuppressorItem.java` |
| 弹匣容量与限制 | `src/main/java/com/antaurora/apofirstlight/weapon/NativeMagazineItem.java` |
| 红点对齐 | `src/main/java/com/antaurora/apofirstlight/weapon/client/NativeAdsProfile.java` |
| P9 配置 | `src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json` |
| BR51 配置 | `src/main/resources/data/apocalypse_firstlight/native_guns/br51_01.json` |
