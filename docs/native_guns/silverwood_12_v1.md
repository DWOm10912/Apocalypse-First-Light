# Silverwood 12 V2 正式资源

状态：正式物品 `apocalypse_firstlight:silverwood_12` 已切换为 Hybrid Mesh V2；此前的临时测试物品及专属资源已移除。测试版的主要实机表现由用户验收，正式 ID 切换后的画面、音效与交互仍待用户最终实机复测；静态检查和 Java 编译不等于该验收。

## 正式绑定与资源

- 正式 Registry ID、Native Gun 数据和物品类仍为 `apocalypse_firstlight:silverwood_12`、`data/apocalypse_firstlight/native_guns/silverwood_12.json`、`ConfiguredNativeGunItem`。创造模式武器栏只保留这一个 Silverwood。
- 可编辑源：`src/main/blockbench/silverwood_12_hybrid_claude_reload_presentation_v2.bbmodel`，为当前权威源；旧 Astra Medium V1 Blockbench 源已从工作区移除，未归档。与上一版 handpolish_v4 相比，模型、Rig、Anchor、UV、贴图和 Display 均未改变；当前动画已更新为 Reload Presentation V2。
- 运行时 geometry：`assets/apocalypse_firstlight/geo/silverwood_12.geo.json`；Hybrid sidecar：`assets/apocalypse_firstlight/meshes/silverwood_12.aflmesh.json`；七动画：`assets/apocalypse_firstlight/animations/silverwood_12.animation.json`；1024×1024 atlas：`assets/apocalypse_firstlight/textures/item/silverwood_12.png`。资源来自已验收测试版，保留 46 Mesh part、4088 triangle、115 导出 Cube。旧 V1 的同名 geometry、动画和 atlas 已被 V2 覆盖，不再参与运行。
- 背包/HUD：`textures/item/silverwood_12_inventory.png`、`textures/gui/gun/silverwood_12_hud.png`；物品模型：`models/item/silverwood_12.json`、`silverwood_12_in_hand.json`。正式路径均使用 V2 测试版图像及显示变换。
- 枪声继续使用正式 `silverwood_12_fire` 的 accepted-shot 路径。动画时间线只使用 `silverwood_12_open`、`silverwood_12_eject`、`silverwood_12_shell_insert`、`silverwood_12_close` 四个机械事件；旧 V1 的整段 reload、draw、put-away、inspect 音轨与事件已移除。`shoot` 动画保留 fire cue 供资源一致性检查，但服务端 cue 队列过滤它，避免重复枪声。
- `right_hand_anchor`、`left_hand_anchor`、双枪口锚点和四个 live/spent shell 节点保持 V2 测试版合同。Hybrid Mesh Runtime、共享 renderer、维护台适配和 12 Gauge 弹药/空壳资源均未改。

## 玩法与 ADS

- 武器为 SHOTGUN、BREAK_ACTION、SEMI；容量 2，6 tick/发，使用 `12_gauge_round` / `12_gauge_casing`。每发 8 颗弹丸，单颗基础伤害 4.5、爆头 1.25×；12 格起衰减、28 格有效、48 格最大、最低 0.35×。同目标逐颗计算后一次结算。噪声 104 格并启用耳鸣。
- 膛室状态仍由 `NativeGunAmmo.read` 决定：2 发时上下 LIVE，1 发时上 LIVE/下 SPENT，0 发时上下 SPENT；第一枪下膛，第二枪上膛。射击不自动抽壳/抛物品；换弹动画表现视觉抛壳。
- 空膛 `reload_empty` 60 tick、第 43 tick 结算；一发 `reload_tactical` 51 tick、第 32 tick 结算。沿用现有中断、背包储备和创造模式无限备弹规则。
- ADS 锥形半角 1.65°，腰射 2.25°；进入/退出 0.22 秒，FOV 0.92。正式 V2 沿用用户在测试版验收的 `ads.profile.eye_relief = 0.4`，瞄准参考点 `[0,9.88,3.5]`、缩放 0.45、hip translation `[3.6,-7.2,-12]` 不变。这个视距值是已验收配置，不再使用此前短暂试验的 `1.05`。
- 后坐仍为垂直 2.2°–2.8°、水平 ±0.10°–0.35°，上限垂直 8°/水平 1.8°；枪模 roll 为 0。HUD 沿用 NativeGunHud 的真实装弹数与备弹数，Tooltip 沿用正式 Silverwood 文案。

正式 ID 的静止 ADS、连续开火、腰射、双膛状态、开合/装填、手臂、音效、背包/HUD、第三人称、维护台及资源热重载仍待用户最终实机复测。

2026-09-24 Reload Presentation V2 同步：仅从当前可编辑源更新正式 `animations/silverwood_12.animation.json`。`reload_empty`、`reload_tactical`、`inspect` 的机械/手部/相机轨道和事件时间发生变化；两段换弹的 eject cue 从 0.427 秒移至 0.543 秒。七个动画片段名称与时长不变，`static_idle`、`draw`、`put_away`、`shoot` 的运行时轨道不变。正式 GEO、AFLMESH、贴图、ADS `eye_relief = 0.4`、Native Gun 玩法及 Hybrid Runtime 均未改；本轮同步与编译不代表新换弹构图已通过游戏内验收。
