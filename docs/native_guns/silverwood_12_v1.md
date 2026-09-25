# Silverwood 12 V2 正式资源

状态：正式物品 `apocalypse_firstlight:silverwood_12` 已接入 Hybrid Mesh Hero Remaster V1 几何，保留 Reload Presentation V2 及后续检视弹仓时序修正。此前的临时测试物品及专属资源已移除。新几何的游戏内画面、音效与交互仍待用户实机复测；静态检查和资源处理不等于该验收。

## 正式绑定与资源

- 正式 Registry ID、Native Gun 数据和物品类仍为 `apocalypse_firstlight:silverwood_12`、`data/apocalypse_firstlight/native_guns/silverwood_12.json`、`ConfiguredNativeGunItem`。创造模式武器栏只保留这一个 Silverwood。
- 可编辑源：`src/main/blockbench/silverwood_12_hybrid_hero_remaster_v1.bbmodel`，为当前权威源；此前 Reload Presentation V2 源仍保留供追溯。新源中的 `inspect` 上下 live/spent shell 与 extractor 时间点已同步正式运行时的 2.717 / 2.783 / 2.817 秒修正，其余七段动画沿用现有正式版本。
- 运行时 geometry：`assets/apocalypse_firstlight/geo/silverwood_12.geo.json`；Hybrid sidecar：`assets/apocalypse_firstlight/meshes/silverwood_12.aflmesh.json`；七动画：`assets/apocalypse_firstlight/animations/silverwood_12.animation.json`；1024×1024 atlas：`assets/apocalypse_firstlight/textures/item/silverwood_12.png`。Hero Remaster 当前为 45 Mesh part、5232 triangle、66 导出 Cube；atlas 像素与上版相同，正式动画 JSON 未重新导出。旧 V1 的同名资源已被后续版本覆盖，不再参与运行。
- 背包/HUD：`textures/item/silverwood_12_inventory.png`、`textures/gui/gun/silverwood_12_hud.png`；物品模型：`models/item/silverwood_12.json`、`silverwood_12_in_hand.json`。正式路径均使用 V2 测试版图像及显示变换。
- 枪声继续使用正式 `silverwood_12_fire` 的 accepted-shot 路径。音效时间线使用 `silverwood_12_open`、`silverwood_12_eject`、`silverwood_12_shell_insert`、`silverwood_12_close` 四个机械事件；旧 V1 的整段 reload、draw、put-away、inspect 音轨与事件已移除。`shoot` 动画保留 fire cue 供资源一致性检查，但服务端 cue 队列过滤它，避免重复枪声。两段 reload 另有独立视觉 cue `chamber_eject_fx`（0.583 秒），不加入服务端声音队列。
- `right_hand_anchor`、`left_hand_anchor`、双枪口锚点和四个 live/spent shell 节点保持 V2 测试版合同。Hybrid Mesh Runtime、共享 renderer、维护台适配和 12 Gauge 弹药/空壳资源均未改。

## 玩法与 ADS

- 武器为 SHOTGUN、BREAK_ACTION、SEMI；容量 2，6 tick/发，使用 `12_gauge_round` / `12_gauge_casing`。每发 8 颗弹丸，单颗基础伤害 4.5、爆头 1.25×；12 格起衰减、28 格有效、48 格最大、最低 0.35×。同目标逐颗计算后一次结算。噪声 104 格并启用耳鸣。
- 每发的 8 颗弹丸继续各自使用既有腰射/ADS 圆锥散布与独立服务端射线；服务端现在把 8 个真实停止点随一次开火确认发给客户端。第一人称冻结枪口或第三人称当前枪口锚点是同发 8 条短、淡轨迹的共同视觉起点。`presentation.trail = subtle_buckshot`（18 格/tick、1.5 格长度、核心宽 0.008/alpha 0.48、外层宽 0.018/alpha 0.07）；不改变单颗伤害 4.5、全中基础总伤害 36、射程、后坐或弹药消耗。共享网络协议现为 30，两端须使用同版；此项完成静态检查，但指定的离线编译在 Gradle 8.8 Wrapper 下载阶段被环境阻断，Java 尚未编译，8 条轨迹的实机显示待用户验收。
- 轨迹起点修正：`subtle_buckshot` 的隐藏近端由 0.40 格改为 0，第一人称冻结轨迹也不再额外前移 0.20 格。火焰与轨迹仍复用同一枪口快照，第三人称仍复用当前枪口锚点；真实服务端眼位射线和停止点不变。因此近端不再被人为截出约 0.60 格空隙，但从枪口连到眼位射线终点的近距离视差仍可能使视觉线相对枪管有角度。本次使用项目本地 Gradle 缓存运行一次 `compileJava --offline` 成功；画面对齐仍待用户实机复核。
- 膛室状态仍由 `NativeGunAmmo.read` 决定：2 发时上下 LIVE，1 发时上 LIVE/下 SPENT，0 发时上下 SPENT；第一枪下膛，第二枪上膛。射击不自动抽壳/抛物品；换弹动画表现视觉抛壳。
- 空膛 `reload_empty` 60 tick、第 43 tick 结算；一发 `reload_tactical` 51 tick、第 32 tick 结算。沿用现有中断、背包储备和创造模式无限备弹规则。
- ADS 锥形半角 1.65°，腰射 2.25°；进入/退出 0.22 秒，FOV 0.92。正式 V2 沿用用户在测试版验收的 `ads.profile.eye_relief = 0.4`，瞄准参考点 `[0,9.88,3.5]`、缩放 0.45、hip translation `[3.6,-7.2,-12]` 不变。这个视距值是已验收配置，不再使用此前短暂试验的 `1.05`。
- 第一人称呼吸摆动由 `weapon/client/NativeWeaponSway.java` 控制：满 ADS 的 `BREAK_ACTION` 整类跳过已移除；正式 Silverwood 在 ADS 过渡中平滑混合到横向 0.65×、纵向 0.80×、频率 0.75×、roll 0.50× 的通用 ADS sway。腰射沿用通用参数，BR51、HR55 和 P9 的摆动参数不变。此视觉变换只作用于渲染，不改变弹道或后坐数值。
- 后坐仍为垂直 2.2°–2.8°、水平 ±0.10°–0.35°，上限垂直 8°/水平 1.8°；枪模 roll 为 0。HUD 沿用 NativeGunHud 的真实装弹数与备弹数，Tooltip 沿用正式 Silverwood 文案。

正式 ID 的静止 ADS、连续开火、腰射、双膛状态、开合/装填、手臂、音效、背包/HUD、第三人称、维护台及资源热重载仍待用户最终实机复测。

2026-09-24 Reload Presentation V2 同步：仅从当前可编辑源更新正式 `animations/silverwood_12.animation.json`。`reload_empty`、`reload_tactical`、`inspect` 的机械/手部/相机轨道和事件时间发生变化；两段换弹的 eject cue 从 0.427 秒移至 0.543 秒。七个动画片段名称与时长不变，`static_idle`、`draw`、`put_away`、`shoot` 的运行时轨道不变。正式 GEO、AFLMESH、贴图、ADS `eye_relief = 0.4`、Native Gun 玩法及 Hybrid Runtime 均未改；本轮同步与编译不代表新换弹构图已通过游戏内验收。

2026-09-24 检视弹仓呈现修正：在可编辑 `.bbmodel` 和正式动画 JSON 中，将 `inspect` 的上下 live/spent shell 与 extractor 的前移起点从 3.10 秒提前到枪管开始开合的 2.717 秒，并于 2.817 秒前完成可见伸出。此前枪管约 2.917 秒已开到位、弹壳却要等到约 3.117 秒才伸出，造成开膛后短暂空仓画面。只改这五个 inspect position 轨道；弹药真值、显隐规则、换弹/开火轨道与机械音效时刻不变。满 ADS 呼吸摆动及检视弹仓时序均待用户实机验收。

2026-09-24 [Chamber Gas FX V1.2](chamber_gas_fx_v1.md)：两段换弹使用 0.583 秒独立视觉 cue，按现有 SPENT 状态逐膛喷气；LIVE 与 inspect 不触发。无几何 `upper_chamber_fx` / `lower_chamber_fx` 挂在 `ammo_state`，原骨骼与原动画关键帧保留。V1.1 实机反馈烟墙过浓，现每膛改为 3 个轴向 Jet Core + 3 个更小、更淡、寿命更短的 Expansion Cloud，随后约 1 秒间隔递增的淡余烟；已出生烟雾独立世界运动。Jet 速度、膛口位置、+Z 方向与去除轴长的变换不变。开发轴线默认关闭，仅开发环境可通过 `-Dafl.chamberGasAxisDebug=true` 开启。未接管世界弹壳；V1.2 仅静态及编译验证，实机烟量与遮挡待复测。

2026-09-24 Hero Remaster V1 接入：正式 ID 直接使用新源的低面数双管、枪口、木托、护木、机匣与扳机护圈 Mesh。移除原先随 `chamber_upper` / `chamber_lower` 状态骨骼缩放的 48 个膛尾 Cube；新的膛尾轮廓固定在枪管 Mesh 上，状态骨骼只负责暗色膛室遮罩，避免开膛时轮廓突然长高。旧开锁拨片的一个 Cube 由 Mesh 取代。木托五个骨骼的 bind pivot 随新源下移 1 个 Blockbench 单位，枪托垫的 12 个 Cube 同步下移；双枪口、手部和 Chamber Gas FX 锚点保持原值。原先四处深棕色贴面花纹 Mesh 已去除，露出连续木纹表面。七段正式动画、枪械数据、ADS 与特效逻辑均未改变；只将正式 `inspect` 已有的弹仓时序修正写回新可编辑源。新几何在游戏内的材质与动作表现待实机验收。
