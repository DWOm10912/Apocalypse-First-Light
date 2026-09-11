# BR51-01 战斗步枪 Cleanup Audit

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

审计日期：2026-09-08。工程：`D:/Minecraft Modding/Apocalypse First Light/`。外部资源目录：`E:/Download/合集/BR51_01/`。
本轮仅创建本报告，没有修改、移动、重命名或删除生产文件及外部资源；没有构建或启动客户端。

## 1. Executive Summary

**前提不成立：当前工作区没有已接入的 Native BR51_01 实现。** 对 src/main/java、src/main/resources、data、构建脚本及全工程非忽略文本搜索 BR51_01/wemql，未找到 BR51_01 注册、配置或资源映射。当前实现是 P9-01 Service Pistol 专用控制器与资源路径，不能把外部目录当作已运行的 BR51_01。以下清理建议仅针对“未来迁移集合”，不授权删除作者原始文件。

文件计数：外部 **21** 个（17 OGG、1 bbmodel、1 geo JSON、1 animation JSON、1 PNG），共 **2512646 bytes**；工程内 BR51_01 文件 **0**。
四类文件：KEEP **0**；MIGRATE / NORMALIZE **13**；REMOVE CANDIDATE **0**；REVIEW **8**。KEEP 按“当前 BR51_01 runtime 必需”定义，0 不代表原始模型没有价值。
另按动画条目计数：KEEP 0；MIGRATE / NORMALIZE 5；REMOVE CANDIDATE 11；REVIEW 4。文件与嵌套动画计数不可相加。
预计可安全立即减少的文件数/体积：**0 / 0 bytes**。未来选取 canonical 后可移除迁移副本中的11条动画，当前紧凑 JSON 条目合计约 **66108 bytes**（不等于原缩进文件或 bbmodel 节省量）。
最明显遗留：wemql_r 声音、tacz:scar_h 取放声音、.wav 式事件名、xmag/old/inspect 变体与 TaCZ 附件/展示 locator。
17 个 OGG SHA-256 均不同；与工程 src/main/resources 中 PNG/OGG 也无同哈希项。未做音频解码或听辨，不能认定近似音频冗余。
独立 PNG 与 bbmodel 内嵌 PNG 哈希不同（见第7节），不能擅自选一份覆盖。
无当前 BR51_01 双播：BR51_01 根本没有运行入口。未来同时启用时间线声音和 Java 声音时有双播风险。

## 2. Runtime Dependency Map

以下路径均相对上述工程根（外部路径在表中完整列出）：

- `src/main/java/com/antaurora/apofirstlight/registry/AflItems.java:17` 只注册 P9-01 Service Pistol 原生枪械（以及撬棍、弹药等），没有 BR51_01。
- `src/main/java/com/antaurora/apofirstlight/weapon/NativeGunDefinition.java:19` 仅 P9_01 静态定义，无 BR51_01、xmag 或可扫描外部枪包的注册表。
- `weapon/P901Actions.java:54–95`（包目录 `src/main/java/com/antaurora/apofirstlight/`）入口要求 instanceof P901Item；状态由 ammo/reload 决定，触发 fire、fire_last_round、reload、reload_empty。没有 inspect/draw/holster/xmag 请求。
- `weapon/P901Item.java:57–62` 显式映射到 animation.p9_01.*。同目录 `P901AnimationController.java:20–21` 在空仓静止时循环 empty_idle；Ready 是静态姿势，并非加载 static_idle 名称。
- `weapon/client/P901Model.java:10–19` 明确加载 `apocalypse_firstlight:geo/p9_01.geo.json`、`textures/item/p9_01.png`、`animations/p9_01.animation.json`。无由物品ID推导任意 BR51_01 文件名的加载路线。
- `weapon/client/P901Renderer.java:14–19` rig 为 gun_model_root/right_hand_anchor/left_hand_anchor/fp_root，`P901HandLayer.java:30–33` 读取左右 locator 世界矩阵。NativeGunRig 是显式字符串记录，不是旧枪包自动适配器。
- `weapon/client/NativeGunFxLayer.java:22` 仅赋予 muzzle_anchor/ejection_anchor 语义。旧 muzzle_pos/shell 不会自动映射。
- `weapon/P901Actions.java:71,95,117,123,127` 主动调用 AFL 干击、开火、退匣、插匣、拉套筒声音；`registry/AflSounds.java` 与 `src/main/resources/assets/apocalypse_firstlight/sounds.json` 没有 BR51_01/WEMQL 注册。
- 在 main Java 未找到 setSoundKeyframeHandler / soundKeyframe 注册。因此不能宣称原 sound_effects 已由 Native 执行。不能仅凭 GeckoLib 能解析键帧就认定事件可到达。
- `build.gradle` 保留可选的 **dev runtimeOnly** TaCZ 对比模组坐标，但默认开发环境不再加载；只有显式传入 `-PaflWithTacz` 且未传入 `-PaflWithoutTacz` 时才启用。它不是 BR51_01 Native API 依赖；main Java 无 TaCZ import，未恢复集成。

路径链结论：外部 BR51_01 → **没有注册/适配入口** → 无 Native 渲染/动画/声音消费者。现有手枪资源与代码只用于取证，不在清理范围。

## 3. File-by-File Audit

所有路径为精确外部路径；大小单位 bytes。“无 Native 引用”不作为删除原始来源的充分条件。

| Path | Type / Bytes | Referenced By | Classification | Confidence | Reason |
|---|---|---|---|---|---|
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_draw.ogg` | ogg / 41908 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_draw_1.ogg` | ogg / 22606 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_1.ogg` | ogg / 22123 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_2.ogg` | ogg / 17409 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_3.ogg` | ogg / 22960 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_4.ogg` | ogg / 18402 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_shoot.ogg` | ogg / 189914 | 无 Native 直接/动态引用；未提供旧包配置 | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_shoot_3p.ogg` | ogg / 38398 | 无 Native 直接/动态引用；未提供旧包配置 | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_silence.ogg` | ogg / 168211 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_silence_3p.ogg` | ogg / 30369 | 无 Native 直接/动态引用；未提供旧包配置 | REVIEW | 中 | 无提供的确切事件映射；名称相近不等于冗余，先听辨/找源配置 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_1.ogg` | ogg / 20098 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_empty_1） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_2.ogg` | ogg / 19991 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_empty_2） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_3.ogg` | ogg / 31155 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_empty_3） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_4.ogg` | ogg / 46003 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_empty_4） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_1.ogg` | ogg / 33835 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_tactical_1） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_2.ogg` | ogg / 32076 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_tactical_2） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_3.ogg` | ogg / 31108 | br51_01_1.animation.json#/animations/*/sound_effects（wemql_r:br51_01_1/reload_tactical_3） | MIGRATE / NORMALIZE | 高（迁移价值） | 有开火或标准换弹功能价值，需建立 AFL 声音事件 |
| `E:/Download/合集/BR51_01/br51_01_1.animation.json` | json / 151893 | 无 Native 直接/动态引用；未提供旧包配置 | MIGRATE / NORMALIZE | 高（迁移价值） | 20动作的来源容器，不能整文件删除 |
| `E:/Download/合集/BR51_01/br51_01_1.png` | png / 18788 | bbmodel 纹理名称对应，但payload不同 | MIGRATE / NORMALIZE | 高（迁移价值） | 独立材质来源；与内嵌材质不同，需确认权威版本 |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | bbmodel / 1140814 | 作者编辑入口；无运行时引用 | MIGRATE / NORMALIZE | 高（迁移价值） | 编辑源，含模型/20动画/内嵌纹理；必须保留原件，未来转换 canonical rig |
| `E:/Download/合集/BR51_01/br51_01_1_geo.json` | json / 414585 | 无 Native 直接/动态引用；未提供旧包配置 | MIGRATE / NORMALIZE | 高（迁移价值） | 迁移几何来源，93骨骼；层级有继承依赖 |

## 4. Animation Audit

来源：`E:/Download/合集/BR51_01/br51_01_1.animation.json#/animations/<Animation>`，bbmodel 同名20条动画存在，但未证明源与导出逐帧等价。
“Duplicate Of”采用键排序后的完整 JSON 比较，包含声音和时长，不受键顺序影响。

| Animation | Native State / Caller | Used? | Duplicate Of | Classification | Notes |
|---|---|---|---|---|---|
| static_idle | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | MIGRATE / NORMALIZE | 时长 未显式指定；保留迁移价值或等待功能决定 |
| reload_empty | 无当前BR51_01调用；未来 reload_empty 候选 | NO | 无完整相同项 | MIGRATE / NORMALIZE | 时长 2.8333；保留迁移价值或等待功能决定 |
| reload_empty_xmag_1 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3.0714；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| reload_empty_xmag_2 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3.2793；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| reload_empty_xmag_3 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3.5417；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| reload_tactical | 无当前BR51_01调用；未来 reload 候选 | NO | 无完整相同项 | MIGRATE / NORMALIZE | 时长 2.6；保留迁移价值或等待功能决定 |
| reload_tactical_xmag_1 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 2.7857；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| reload_tactical_xmag_2 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| reload_tactical_xmag_3 | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3.2083；去掉时间戳后骨骼通道/值序列仍不同，不是已证明的均匀缩时副本 |
| static_bolt_caught | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | MIGRATE / NORMALIZE | 时长 未显式指定；保留迁移价值或等待功能决定 |
| reload_empty_old | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 4.2083；旧动作无Native映射，且声音指向缺失旧路径 |
| reload_tactical_old | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REMOVE CANDIDATE | 时长 3.2407；旧动作无Native映射，且声音指向缺失旧路径 |
| inspect | 无当前BR51_01调用；无现成同名映射 | NO | inspect_xmag | REVIEW | 时长 5.25；可在迁移副本合并；原文件先留存 |
| inspect_xmag | 无当前BR51_01调用；无现成同名映射 | NO | inspect | REMOVE CANDIDATE | 时长 5.25；可在迁移副本合并；原文件先留存 |
| inspect_empty | 无当前BR51_01调用；无现成同名映射 | NO | inspect_empty_xmag_12, inspect_empty_xmag_3 | REVIEW | 时长 6.375；可在迁移副本合并；原文件先留存 |
| inspect_empty_xmag_12 | 无当前BR51_01调用；无现成同名映射 | NO | inspect_empty, inspect_empty_xmag_3 | REMOVE CANDIDATE | 时长 6.375；可在迁移副本合并；原文件先留存 |
| inspect_empty_xmag_3 | 无当前BR51_01调用；无现成同名映射 | NO | inspect_empty, inspect_empty_xmag_12 | REMOVE CANDIDATE | 时长 6.375；可在迁移副本合并；原文件先留存 |
| shoot | 无当前BR51_01调用；未来 fire 候选 | NO | 无完整相同项 | MIGRATE / NORMALIZE | 时长 0.8333；保留迁移价值或等待功能决定 |
| put_away | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REVIEW | 时长 0.4333；保留迁移价值或等待功能决定 |
| draw | 无当前BR51_01调用；无现成同名映射 | NO | 无完整相同项 | REVIEW | 时长 0.7333；保留迁移价值或等待功能决定 |

六个 reload_xmag 是命名上与扩容相关的旧变体，模型也有 mag_extended_1/2/3，但缺少原 gun config，无法证明每一级容量、附件条件或速度规则。当前 Native 无这些状态。**可不迁入 V1，不可声称只改播放速度即可完全等价。** inspect 系列确有完整重复：inspect=inspect_xmag；inspect_empty=inspect_empty_xmag_12=inspect_empty_xmag_3。若V1不做inspect，可先全部不迁入，但两条canonical原稿保留。

## 5. Sound Audit

全部17音频哈希如下；无同哈希文件。不同哈希不排除重新编码同一波形。近似重复尚未听辨确认；br51_01_draw 与 br51_01_draw_1、带br51_01_前缀与不带前缀的reload_empty_*是待比较组，不能据名称删除。

| Sound / Event | Referenced From | Hash Duplicate | Namespace | Native Reachable? | Classification |
|---|---|---|---|---|---|
| `wemql_r:br51_01_1/reload_empty_1` | `br51_01_1.animation.json#/animations`：reload_empty@0.0667, reload_empty_xmag_1@0.1429, reload_empty_xmag_2@0.0, reload_empty_xmag_3@0.125 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_empty_2` | `br51_01_1.animation.json#/animations`：reload_empty@0.7, reload_empty_xmag_1@0.8929, reload_empty_xmag_2@0.9615, reload_empty_xmag_3@1.0417 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_empty_3` | `br51_01_1.animation.json#/animations`：reload_empty@1.1667, reload_empty_xmag_1@1.3214, reload_empty_xmag_2@1.4231, reload_empty_xmag_3@1.6667 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_empty_4` | `br51_01_1.animation.json#/animations`：reload_empty@1.9667, reload_empty_xmag_1@2.1071, reload_empty_xmag_2@2.2692, reload_empty_xmag_3@2.625 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_tactical_1` | `br51_01_1.animation.json#/animations`：reload_tactical@0.3, reload_tactical_xmag_1@0.3214, reload_tactical_xmag_2@0.3462, reload_tactical_xmag_3@0.375 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_tactical_2` | `br51_01_1.animation.json#/animations`：reload_tactical@1.4, reload_tactical_xmag_1@1.5, reload_tactical_xmag_2@1.6154, reload_tactical_xmag_3@1.75 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01_1/reload_tactical_3` | `br51_01_1.animation.json#/animations`：reload_tactical@2.0333, reload_tactical_xmag_1@2.25, reload_tactical_xmag_2@2.4231, reload_tactical_xmag_3@2.625 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_1_r3_66407976.wav` | `br51_01_1.animation.json#/animations`：reload_empty_old@1.25 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | REVIEW |
| `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_2_r3_365873168.wav` | `br51_01_1.animation.json#/animations`：reload_empty_old@1.9583 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | REVIEW |
| `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_3_r3_53342596.wav` | `br51_01_1.animation.json#/animations`：reload_empty_old@2.75 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | REVIEW |
| `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` | `br51_01_1.animation.json#/animations`：reload_tactical_old@0.6154, inspect@0.625, inspect_xmag@0.625, inspect_empty@0.625, inspect_empty_xmag_12@0.625, inspect_empty_xmag_3@0.625 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | REVIEW |
| `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` | `br51_01_1.animation.json#/animations`：reload_tactical_old@1.9231, inspect@2.4583, inspect_xmag@2.4583, inspect_empty@2.4583, inspect_empty_xmag_12@2.4583, inspect_empty_xmag_3@2.4583 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | REVIEW |
| `wemql_r:br51_01/br51_01_draw` | `br51_01_1.animation.json#/animations`：inspect@3.5, inspect_xmag@3.5, inspect_empty@3.5, inspect_empty_xmag_12@3.5, inspect_empty_xmag_3@3.5 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `wemql_r:br51_01/mo` | `br51_01_1.animation.json#/animations`：inspect_empty@4.2917, inspect_empty_xmag_12@4.2917, inspect_empty_xmag_3@4.2917 | 事件名≠文件哈希 | wemql_r | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `shoot` | `br51_01_1.animation.json#/animations`：shoot@0.0 | 事件名≠文件哈希 | 无namespace（shoot动态旧别名） | NO；无事件注册/调用映射 | MIGRATE / NORMALIZE |
| `tacz:scar_h/p05_ar_schotel_ubgl_drop_rattle` | `br51_01_1.animation.json#/animations`：put_away@0.0333 | 事件名≠文件哈希 | tacz | NO；无事件注册/调用映射 | REVIEW |
| `tacz:scar_h/p05_ar_schotel_reload_raise` | `br51_01_1.animation.json#/animations`：draw@0.0667 | 事件名≠文件哈希 | tacz | NO；无事件注册/调用映射 | REVIEW |

| Audio path | SHA-256 | Classification |
|---|---|---|
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_draw.ogg` | `8ae72d8f7b4dd9d110d92a96ed2d3597e46d70be5bccf5354a8d603a545963ea` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_draw_1.ogg` | `8f84d3fbb10daeb07c1c0b65db8351949696d46715640bd09963bc5246092650` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_1.ogg` | `7dbf0bde8949501dffa712cdf93cec5dde37cbc29c6a83f26a61e46c04ee6b38` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_2.ogg` | `0b6ab7996ad2d5d50ac8200f9e0fc274719292f8322bbba264e1f74b4d99cf22` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_3.ogg` | `6d2f6713c0342a74e7fbc4471aafe3eb54e93a250b2b5bc46ce02666ab15acc1` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_reload_empty_4.ogg` | `d14ea39cd7652cfe60b3645de5f49b684970c80f5ef1472361d7ecd217ff08df` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_shoot.ogg` | `ea467e89d3f7600b6414e9290c5da5de744668c6a6d57c642fbc9c13c911fddd` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_shoot_3p.ogg` | `64577ead5d5b163c109b92b5032be50b52cc923ab0f8ccd1b60db586137b1016` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_silence.ogg` | `79617a347d257b15cde2e599e3b7de7f5a8deb8707e9f137fef040103d967c13` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/br51_01_silence_3p.ogg` | `b78fd4df501287cddf4fe5afbeeeb95378c2d7b966e0db20e63c5e50a145b71b` | REVIEW |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_1.ogg` | `b576e67721ed5cc378f21905308df3e923f9da10d242418dc25c17230a5a7525` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_2.ogg` | `755a0da2603ad621055228b413b54f035907d978d1cddb19cf02fbbaab77ce71` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_3.ogg` | `a87df766a3f7c0ccd00ac5e115c1e836f355d278266d9e7affedc0d323c57f0e` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_empty_4.ogg` | `e5b0710a155b58b5fbba6330ec08faccf7a1f935a11b2fd9aba4fc2f312a4db2` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_1.ogg` | `09292c2a6c5ed05d782192b608e6cc302b1310304812fff1a0795fa6b937ab40` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_2.ogg` | `91ac808ec9fa5640561b503aefa076855e110f53dae3636ad5cc1bfab6b452d2` | MIGRATE / NORMALIZE |
| `E:/Download/合集/BR51_01/br51_01_1/reload_tactical_3.ogg` | `2c54c0fd16d4e25d96ebf4dfe5ecd156b91fbc48ef3a3ac577c40e0c1b99da3a` | MIGRATE / NORMALIZE |

提供目录无 sounds.json。wemql_r:br51_01_1/reload_empty_1 可对应同名 reload_empty_1.ogg 的路径意图，但**没有事件定义不能确认实际绑定**；不能把它自动解释为 br51_01_reload_empty_1.ogg。旧 wemql_r:br51_01/*.wav、wemql_r:br51_01/mo、tacz:scar_h/* 没有提供对应资源。shoot 为旧动态别名，没有 BR51_01 gun config 解析依据。

双播：目前未发现可发生的 BR51_01 双播。未来如果保留 reload_empty/tactical sound_effects 并新增 Java 退匣/插匣/拉栓声音，或将 shoot@0 与成功射击声音都启用，会重复触发；必须选择唯一声音驱动。取放/inspect 同理，不要简单同时迁入两条路径。

## 6. Bone / Model Structure Audit

来源：`E:/Download/合集/BR51_01/br51_01_1_geo.json#/minecraft:geometry/0/bones`。共93骨骼。下表“使用”仅指外部动画直接引用，**不表示 Native 可到达**。未直接动画驱动的几何仍通过父骨骼继承运动，不可直接删除。
constraint 是 br51_01 子节点；gun_and_righthand 是 br51_01 与 righthand 的共同父；mag_and_lefthand 是 magazine_bullet 与 lefthand 的共同父。这些不是现有 AFL 特殊名称，但是真实变换层级依赖。

| Bone | Model Exists | Animations Using It | Native Semantic Dependency | Classification |
|---|---|---|---|---|
| camera | YES；parent=-；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, reload_empty_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3, shoot, put_away, draw | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| root | YES；parent=-；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, reload_empty_old, reload_tactical_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3, shoot, put_away, draw | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| gun_and_righthand | YES；parent=root；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| br51_01 | YES；parent=gun_and_righthand；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| constraint | YES；parent=br51_01；0 cubes | static_idle, reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| additional_magazine | YES；parent=br51_01；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, reload_empty_old, reload_tactical_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| bolt | YES；parent=br51_01；11 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, static_bolt_caught, reload_empty_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3, shoot | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| bullet_in_barrel | YES；parent=bolt；12 cubes | static_bolt_caught, reload_empty_old, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| octagon7 | YES；parent=bolt；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone10 | YES；parent=bolt；7 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| br51_01_default | YES；parent=br51_01；2 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone5 | YES；parent=br51_01_default；55 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon9 | YES；parent=bone5；8 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone6 | YES；parent=br51_01_default；34 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone | YES；parent=br51_01_default；23 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone2 | YES；parent=bone；27 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone3 | YES；parent=bone；23 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone4 | YES；parent=br51_01_default；81 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| group49 | YES；parent=br51_01_default；41 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| group2 | YES；parent=br51_01_default；13 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| group3 | YES；parent=br51_01_default；13 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone8 | YES；parent=br51_01_default；20 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone7 | YES；parent=br51_01_default；15 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| attachment_adapter_1 | YES；parent=br51_01_default；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| ar_stock_adapter_1 | YES；parent=attachment_adapter_1；5 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| stock_default | YES；parent=ar_stock_adapter_1；36 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| qianguan | YES；parent=br51_01_default；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon8 | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon3 | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon5 | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon4 | YES；parent=qianguan；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon2 | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| octagon6 | YES；parent=qianguan；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone13 | YES；parent=br51_01_default；5 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone11 | YES；parent=bone13；3 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone12 | YES；parent=bone13；3 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone14 | YES；parent=bone13；6 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone20 | YES；parent=br51_01_default；5 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone21 | YES；parent=br51_01_default；5 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| sight | YES；parent=br51_01；28 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone15_illuminated | YES；parent=sight；1 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone16 | YES；parent=sight；21 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| laser_lopro_mini | YES；parent=sight；61 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| laser_illuminated | YES；parent=laser_lopro_mini；1 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone9 | YES；parent=laser_lopro_mini；6 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone15 | YES；parent=laser_lopro_mini；5 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| flashlight_illuminated | YES；parent=bone15；1 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone17 | YES；parent=laser_lopro_mini；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone18 | YES；parent=laser_lopro_mini；3 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bone19 | YES；parent=laser_lopro_mini；3 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| positioning2 | YES；parent=br51_01；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| muzzle_flash | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| scope_pos | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| laser_pos | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| grip_pos | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| muzzle_pos | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| shell | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| stock_pos | YES；parent=positioning2；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| muzzle_default | YES；parent=br51_01；36 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| grip_default | YES；parent=br51_01；46 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| righthand | YES；parent=gun_and_righthand；0 cubes | static_idle, reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, static_bolt_caught, reload_empty_old, reload_tactical_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3, put_away, draw | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| righthand_pos | YES；parent=righthand；1 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| mag_and_lefthand | YES；parent=root；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, reload_empty_old, reload_tactical_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| magazine_bullet | YES；parent=mag_and_lefthand；0 cubes | reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3 | 无当前BR51_01适配；重要运动/可见性层级，暂不可删 | REVIEW |
| bullet | YES；parent=magazine_bullet；0 cubes | static_bolt_caught | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| 2 | YES；parent=bullet；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| 3 | YES；parent=2；12 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| 7 | YES；parent=2；12 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| magazine | YES；parent=magazine_bullet；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| mag_standard | YES；parent=magazine；20 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| hu2 | YES；parent=mag_standard；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| mag_extended_1 | YES；parent=magazine；20 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| hu3 | YES；parent=mag_extended_1；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| mag_extended_2 | YES；parent=magazine；20 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| hu7 | YES；parent=mag_extended_2；4 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| mag_extended_3 | YES；parent=magazine；51 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| lefthand | YES；parent=mag_and_lefthand；0 cubes | static_idle, reload_empty, reload_empty_xmag_1, reload_empty_xmag_2, reload_empty_xmag_3, reload_tactical, reload_tactical_xmag_1, reload_tactical_xmag_2, reload_tactical_xmag_3, static_bolt_caught, reload_empty_old, reload_tactical_old, inspect, inspect_xmag, inspect_empty, inspect_empty_xmag_12, inspect_empty_xmag_3, put_away, draw | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| lefthand_pos | YES；parent=lefthand；1 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| positioning | YES；parent=-；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| thirdperson_hand | YES；parent=positioning；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| ground | YES；parent=positioning；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| fixed | YES；parent=positioning；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| view | YES；parent=-；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| idle_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| iron_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_grip_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_stock_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_muzzle_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_scope_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_laser_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| refit_extended_mag_view | YES；parent=view；0 cubes | 无直接轨道；仍检查父级继承 | 无当前BR51_01适配；几何/层级/定位用途需迁移验证 | REVIEW |
| bolt2 | NO | shoot | 轨道目标缺失；需检查源导出版本或旧附件依赖 | REVIEW |
| charger | NO | shoot | 轨道目标缺失；需检查源导出版本或旧附件依赖 | REVIEW |

static_bolt_caught 使用 bolt/bullet_in_barrel/bullet，不能因静态命名删除。mag_extended_*及子骨骼属于未来标准弹匣版本的可排除候选，但必须验证 scale/visibility、标准匣和换弹复制匣，不能直接删除父 magazine_bullet。sight、laser_lopro_mini、muzzle_default、grip_default 有实质几何；没有附件系统不意味着可以破坏现有选定外形。
当前 Native 需要的 right_hand_anchor/left_hand_anchor/muzzle_anchor/ejection_anchor 与本模型命名不一致。不能按字符串一键改名后宣称 B_skin、枪焰和抛壳等价。

## 7. TaCZ / WEMQL Residue

动画 JSON 的全部旧引用已逐事件列于第5节，位置为 `/animations/<name>/sound_effects/<time>/effect`。下面列出 bbmodel 内所有匹配的原值与精确JSON指针，避免仅审查导出文件：

| Source path | JSON pointer | Value |
|---|---|---|
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/1/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/1/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/1/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/1/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_4` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/2/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/2/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/2/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/2/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_4` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/3/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/3/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/3/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/3/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_4` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/4/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/4/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/4/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/4/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01_1/reload_empty_4` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/5/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/5/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/5/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/6/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/6/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/6/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/7/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/7/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/7/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/8/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_1` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/8/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_2` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/8/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01_1/reload_tactical_3` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/10/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_1_r3_66407976.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/10/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_2_r3_365873168.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/10/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magoutempty_3_r3_53342596.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/11/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/11/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/12/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/12/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/12/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/br51_01_draw` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/13/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/13/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/13/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/br51_01_draw` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/14/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/14/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/14/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/br51_01_draw` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/14/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01/mo` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/15/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/15/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/15/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/br51_01_draw` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/15/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01/mo` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/16/animators/effects/keyframes/0/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_1_r3_810721330.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/16/animators/effects/keyframes/1/data_points/0/effect` | `wemql_r:br51_01/wpn_br51_01_reload_magout_2_r3_16953615.wav` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/16/animators/effects/keyframes/2/data_points/0/effect` | `wemql_r:br51_01/br51_01_draw` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/16/animators/effects/keyframes/3/data_points/0/effect` | `wemql_r:br51_01/mo` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/18/animators/effects/keyframes/0/data_points/0/effect` | `tacz:scar_h/p05_ar_schotel_ubgl_drop_rattle` |
| `E:/Download/合集/BR51_01/br51_01_1_geo.bbmodel` | `/animations/19/animators/effects/keyframes/0/data_points/0/effect` | `tacz:scar_h/p05_ar_schotel_reload_raise` |

未提供 gun config、附件条件、旧 pack manifest、recipes/tags/lang/item model/sounds.json，不能凭动画名补造原包映射。工程内没有 BR51_01 对应这些文件。
外部PNG SHA-256：`bf5b0f41feb51f15e2e315b453b0e04a98b951b0a456d4efa2b8ed52f93ac9ba`。
bbmodel内嵌PNG SHA-256：`fc59914ff3a337d0abdeb7f2f1febb20ef74b78f263fcd1e02a70ef55cdc8fce`。
哈希不同仅证明字节不同，未证明像素不同；未做解码逐像素比较，列 REVIEW。bbmodel 内嵌纹理不是可直接删除的“重复资源”，它支持源文件独立编辑。

## 8. Recommended Minimal BR51_01 Asset Set

这只是未来迁移建议，当前未实施，也没有宣称BR51_01能运行：

- 保留原始整个目录作为来源；确认第三方资源授权，不推定可商用。
- 项目编辑源放 src/main/blockbench/；一份选定姿势/纹理的BR51_01源。
- 一份 AFL geo、单一权威纹理、一个原生item模型及实际需要的GUI图标；现在尚无这些注册资源。
- 动作最小集合：静态Ready（由static_idle迁移）、shoot→fire、reload_tactical→reload、reload_empty、空仓bolt状态（static_bolt_caught内容）。最后一发/空仓衔接须设计映射，现有20动作没有原生fire_last_round命名。
- V1先不迁入6条reload_xmag、2条old、3条重复inspect；独立inspect/draw/put_away保留原稿但不进入不支持的状态。
- 声音候选：br51_01_shoot，按需求评估br51_01_shoot_3p；标准reload_empty_1..4和reload_tactical_1..3。全部先确认事件与音频内容，再注册AFL事件；silence/draw/旧前缀换弹留原来源等待决策。
- 不要把 P901Model/Actions 的硬编码直接视作通用BR51_01支持。未来注册和适配是新实施任务，不属于清理。

## 9. Safe Cleanup Plan

**Phase 1：无引用/hash重复。** 当前零个文件可据哈希直接删除。仅迁移副本合并三个inspect完全重复条目；回归canonical逐帧内容/声音/时长。原目录不动。
**Phase 2：old/xmag。** 在确认标准弹匣V1后排除6条reload扩容和2条old；回归标准/空仓换弹、弹匣可见性、最后一发、回位。不能靠全局播放速度冒充等价动作。
**Phase 3：namespace/声音迁移。** 先建立明确的AFL事件与单一声音触发源，逐个对齐时刻，再移除迁移副本旧键；检查射击、空仓、普通/空仓换弹、多人远近声音且每事件只响一次。
**Phase 4：bone精简。** 最后进行。先烘焙/保持父矩阵，再考虑附件/展示locator；对所有时间点比较手、枪、匣、bolt和装弹可见性；第一/第三人称、GUI/ground分别验收。constraint等必须以等价性证据为准。

## 10. Do Not Delete Yet

- 全部原始21文件；当前无 Native BR51_01，不存在“删除不会影响BR51_01”的有意义运行验证。
- bbmodel、geo、独立及内嵌纹理；源/导出权威性与像素关系尚未确认。
- constraint / gun_and_righthand / mag_and_lefthand / additional_magazine / magazine_bullet / bullet_in_barrel / bolt 和所有带几何的未直接动画骨骼。
- 8个REVIEW音频：draw两份、br51_01_reload_empty四份、silence两份；没有音频同哈希证据，也没有完整旧sounds/config。
- shoot 中 bolt2/charger 缺失轨道，先查版本或用途再处理。
- 旧 .wav 和 tacz:scar_h 事件不要“自动修成”近似音频：缺原事件定义。
- P9-01 Service Pistol及其全部代码、资源不在本次清理范围。

验证边界：完成文件枚举、JSON解析、名称/路径和显式动态映射审计、哈希及动画结构比较；未进行音频试听、模型预览、运行时BR51_01验收。报告本身是唯一新增文件。
