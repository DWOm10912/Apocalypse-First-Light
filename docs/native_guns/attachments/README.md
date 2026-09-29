# 原生枪械配件总表

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **29**，客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

本页记录当前正式实现，并作为新增配件与属性的统一索引。模型制作记录不等于已装备接入；验证结果见各配件详情文档。本次整理仅修改文档，不新增游戏属性或数值效果。

## 配件目录

以下 ID 均使用命名空间 `apocalypse_firstlight:`；兼容性以枪械数据的 `*_slot.accepts` 为准。

| 配件 | Registry ID | 槽位 | 当前兼容枪械 | 当前主要效果 | 状态 / 详情 |
| --- | --- | --- | --- | --- | --- |
| 手枪微型红点瞄具 | `pistol_red_dot` | `SIGHT` | P9-01 | ADS 对准镜片中心；第一人称准直红点；不额外增加倍率 | 已接入 / [红点 V1](../pistol_red_dot_v1.md) |
| 步枪红点瞄具 | `rifle_red_dot_01` | `SIGHT` | BR51-01、HR55（接口 `rifle_optic_rail`） | 封闭短管微型红点；ADS 对准镜片中心；第一人称准直红点（圆形窗口）；不额外增加倍率 | 已接入；2026-09-28 资产为 Pure Mesh + PBR V1（待实机验收） / [Pure Mesh V1](rifle_red_dot_01_pure_mesh_v1.md)、[接入 V1](rifle_red_dot_01_v1.md) |
| 手枪消音器 | `pistol_suppressor_01` | `MUZZLE` | P9-01 | 游戏噪声半径 ×0.05；消音声与出口视觉切换 | 已接入；资产为 AFL 通用 9mm Pure Mesh（Model V2，待实机验收） / [手枪消音器 V1](pistol_suppressor_01_v1.md)、[Model V2](pistol_suppressor_01_model_v1.md) |
| 7.62×51mm 步枪消音器 | `rifle_suppressor_01` | `MUZZLE` | BR51-01（`rifle_fh_qd`，额定 7.62×51mm；HR55 已移除） | 游戏噪声半径 ×0.05（数据层）；消音声与出口视觉切换 | 已接入；2026-09-28 资产为 Pure Mesh + PBR V1（待实机验收） / [Pure Mesh V1](rifle_suppressor_01_pure_mesh_v1.md)、[接入 V1](rifle_suppressor_01_v1.md) |
| P9-01 28发扩容弹匣 | `p9_01_extended_magazine` | `MAGAZINE` | P9-01 | 弹匣容量覆盖为 28 发；Pure Mesh 加长钢匣 + 握把延长套 + 加厚底板 | 已接入（V2，待实机验收） / [扩容弹匣 V2](../p9_01_extended_magazine_v1.md) |

| BR51-01 50发弹鼓 | `br51_drum_magazine_50` | `MAGAZINE` | BR51-01 | 20→50；方形双鼓；空仓换弹改用专用拼接动画 `reload_empty_drum`（3.2 s），战术换弹复用原动画；维护台平放自动抬高避免穿垫 | 已接入（2026-09-28，待实机验收） / [弹鼓 V1](br51_drum_magazine_50_v1.md) |
| BR51-01 35发扩容弹匣 | `br51_extended_magazine_35` | `MAGAZINE` | BR51-01 | 20→35；替换枪内、换弹新匣和空仓旧匣三处弹匣；溢出安全返还；底板两侧黄褐识别条 | 已接入；2026-09-28 资产为 Pure Mesh + PBR V2（待实机验收） / [35R V2](br51_extended_magazine_35_v2.md)、[35R V1](br51_extended_magazine_35_v1.md) |

物品栏图标：所有配件统一朝向与大小（2026-09-28，`NativeAttachmentGuiFit`，最长边占格 85%），见 [枪械物品栏展示 · 配件物品栏图标](../native_gun_inventory_presentation_v1.md)。

兼容范围不按配件名称自动推断：当前手枪消音器和手枪红点不能装 BR51，步枪消音器和步枪红点不能装 P9；BR51 接受专属 `br51_extended_magazine_35`。未来步枪可通过自己的兼容声明、挂载点与 ADS 数据复用步枪配件。当前扩容弹匣另有 `NativeMagazineItem.accepts` 的逐附件枪型限制，不能仅改 JSON 就宣称支持其他枪。

## 当前属性明细

“倍率”“覆盖”“视觉”是文档分类，不代表已存在一套通用属性修改器。表内属性名优先使用实际代码字段；后续新增属性应先落实实现，再登记为已实现。

| 配件 | 属性 / 效果 | 类型 | 当前值 | 实际结果 / 边界 | 实现来源 |
| --- | --- | --- | --- | --- | --- |
| 手枪消音器 | `noiseRadiusMultiplier` | 倍率 | `0.05` | P9：64 → 3 格；最终取整并至少 1 格 | `NativeSuppressorItem` 代码默认值（无配件数据文件）、`NativeGunNoise` |
| 7.62×51mm 步枪消音器 | `noise_multiplier` | 倍率 | `0.05` | BR51：112 → 6 格；最终取整并至少 1 格 | `data/apocalypse_firstlight/native_attachments/rifle_suppressor_01.json` → `NativeAttachmentData`、`NativeSuppressorItem`、`NativeGunNoise` |
| 两种消音器 | `suppressesFireSound` | 开关 | `true` | 选择枪械定义的 `suppressed_fire_sound`，不是配件绑定同一声音 | `NativeSuppressorItem`、枪械 JSON |
| 两种消音器 | 玩家音频传播距离 | 保持 | 不乘 `0.05` | AI 听觉噪声半径与玩家音频衰减分开 | `NativeGunNoise` 与既有声音播放路径 |
| 两种消音器 | 枪口焰 / 出口 | 视觉 | 抑制裸枪焰，使用配件出口 | smoke / tracer 视觉从 `muzzle_exit_anchor` 出发，不改服务端命中规则 | `NativeMuzzleRendering`、既有 shot visual 路径 |
| 手枪红点 | ADS 光学轴 | 对齐 | P9 `ads_center = [0, 6.52778, 0.25339]` | 当前安装下镜片 `lens_center` 的枪模型坐标；不是伤害、精度或后坐力增益 | `NativeAdsProfile`、P9 `sight_slot` |
| 手枪红点 | 准直红点 | 视觉 | 0.4° 四边形（核心约 0.27°），颜色 `[255,38,30]`，光轴门限 12° | 仅第一人称；标记屏幕中心 / hitscan，只在视线穿过镜窗时显示；不改弹道 | `NativeCollimatedReticleRendering`、`optics/pistol_red_dot.json` |
| 手枪红点 | ADS FOV / 进入时间 | 保持 | 沿用 P9 `0.95` / `0.15 s` | 当前无配件额外倍率和举枪速度加成 | P9 `ads`、`NativeAdsProfile` |
| 步枪红点 | ADS 光学轴 | 对齐 | BR51 `[0,14.8125,3.70313]`；HR55 `[0,13.48438,-2.8438]` | 装上后镜片 `lens_center` 的枪模型坐标，不改变弹道 | 各枪 `sight_slot`、`NativeAdsProfile` |
| 步枪红点 | 准直红点 | 视觉 | 0.35° 四边形，颜色 `[255,38,30]`，光轴门限 12°，椭圆窗口半轴 0.51 | 仅第一人称；标记屏幕中心 / hitscan，只在视线穿过圆形镜窗时显示；不改弹道 | `NativeCollimatedReticleRendering`、`optics/rifle_red_dot_01.json` |
| 步枪红点 | 挂载接口 | 校验 | `rifle_optic_rail` | 槽位声明的接口不一致时拒绝加载该枪数据 | `native_attachments/rifle_red_dot_01.json` → `NativeAttachmentData`、`NativeSightMount` |
| 步枪红点 | ADS FOV / 进入时间 | 保持 | BR51 `0.89` / `0.2 s`；HR55 `0.92` / `0.2 s` | 无高倍率效果或数值加成 | 各枪 `ads` |
| P9 扩容弹匣 | `capacity()` | 覆盖 | `28` 发 | 原容量 17 → 28（差值 +11），不是全枪通用“+11”修改器 | `NativeMagazineItem`、`NativeGunAmmo` |
| P9 扩容弹匣 | 多余弹药处理 | 事务规则 | 容量降低时安全返还 | 安装不免费补弹；拆卸不吞掉超容量弹药 | `MaintenanceAttachmentTransaction` |

消音公式：`finalNoiseRadius = max(1, round(baseNoiseRadius * 0.05))`。上述数值只在实际装备且兼容的配件生效时使用。

当前六种配件均未引入额外伤害、射程、射速、散布、后坐力或换弹速度增减。不要从模型体积、重量感或现实配件效果推导出游戏数值。

## 装备入口与共用规则

| 项目 | 当前规则 |
| --- | --- |
| 装拆入口 | 枪械维护台 + Z Field Attachment View；V 快捷安装和拆卸保持移除，旧快捷网络请求不执行 |
| 维护台 | 三种槽位共用热点、上下文 HUD、候选页与服务端事务；只显示该枪支持的槽 |
| 维护台音效与提交 | 原版轻点击；共用 2.480 秒操作声；服务端等待 51 tick 后重新验证并提交 |
| 物品状态 | 配件保存在真实枪械 ItemStack 的 `AflAttachments` 中；无独立维护台视觉副本 |
| 服务端权威 | 共用兼容/源物品/库存事务；维护台校验 revision，Field 校验 token、主手槽、GeoItem ID、全栈快照及原栈对象；提交前重验 |
| 最大堆叠 | 当前六种配件均为 1 |
| 协议 | 当前 22；两端必须匹配 |

完整规则见 [维护台配件交互](gun_maintenance_attachment_interaction_v1.md) 与 [UI/SFX](gun_maintenance_attachment_ui_sfx_polish_v1.md)。旧快捷报文编号保留为空操作；当前协议为 29。Z 入口复用相同服务端核心，见 [Field Attachment View V1](../field_attachment_view_v1.md)。

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
