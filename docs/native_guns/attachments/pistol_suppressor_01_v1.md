# 手枪消音器可装备 V1

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **31**（2026-10-02 加入容器搜索声音包后），客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](README.md)。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | 手枪消音器 / `apocalypse_firstlight:pistol_suppressor_01` |
| 槽位 / 当前兼容 | `MUZZLE` / P9-01；不兼容 BR51。资产为 AFL 通用 9mm 手枪消音器（2026-09-27 Model V2），其他 9mm 手枪声明 `accepts` 并把锚点放在自己的枪口 crown 即可复用 |
| 噪声半径倍率 | `0.05`；P9 `64 → 3` 格 |
| 消音声 | 枪械定义 `apocalypse_firstlight:p9_01_suppressed` |
| 玩家音频距离 | 不乘降噪倍率 |
| 视觉出口 | 配件 `muzzle_exit_anchor` `(0,0,-7.85)`；隐藏裸枪焰并生成弱烟 |
| 伤害 / 射程 / 后坐力 / 射速 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |

## 实现说明

当前物品 Tooltip 仅显示“枪口装置｜手枪”和一句降噪/削弱枪口焰描述，不再包含 V 键、主副手或拆卸顺序教学；附件倍率与安装逻辑保持不变。统一规则见 [Tooltip Cleanup V1](../native_gun_attachment_tooltip_cleanup_v1.md)。

## 当前范围

- 物品/附件 ID：apocalypse_firstlight:pistol_suppressor_01，手枪消音器 / Pistol Suppressor，堆叠1，创造「黎明启示录 · 配件」标签页（2026-09-28 起，原「AFL 武器与弹药」已拆分）。无新配方。
- 首批仅 P9：枪数据 muzzle_slot.anchor=muzzle_anchor，accepts=[apocalypse_firstlight:pistol_suppressor_01]。BR51不兼容。
- NativeAttachment 的 MUZZLE 与原 SIGHT 共用 NativeAttachments 和 AflAttachments NBT。独立 NativeSuppressorItem 定义 noiseRadiusMultiplier=0.05、suppressesFireSound=true；倍率不写进 P9 类。
- 玩家必须在枪械维护台安装、拆卸和更换；按候选来源消耗物品，返还保留完整 NBT，共用 51 tick 延迟提交。V 快捷装拆已移除。
- 当前通道协议22；SightExchangePacket 保留报文编号但不再执行变更，旧请求不能绕过维护台。真实装拆统一使用 MaintenanceActionRequest 的服务端验证与延迟提交。

## 射击

NativeGunNoise.resolve 从服务器真实 ItemStack 取得有效 MUZZLE，最终半径 max(1,round(base×倍率))，P9 64→3。NativeGunShot 把最终值送给 NoiseSystem→InfectedHearingSystem；耳鸣也读取最终半径和抑音状态，P9原本 tinnitus=false。不新增第二个噪声事件。

P9 可选数据 suppressed_fire_sound=apocalypse_firstlight:p9_01_suppressed；无消音器仍为 p9_01_fire。未来其他兼容枪可声明自己的消音声，未声明时回退原枪声。声音播放音量不变，传播距离不乘 0.05：消音枪声 16 格（用户 2026-10-09 定，`NativeGunNoise.SUPPRESSED_SOUND_RADIUS`），噪声 3 格；不装消音器时开火声传到噪声半径 64 格（`noise/RangedSound`）。导入用户 E:/Download/p9_01_suppressed.ogg，资源位于 sounds/weapons/p9_01/p9_01_suppressed.ogg，注册与 sounds.json 同步；导入前未发现相同音频。原文件为48kHz双声道，游戏内副本仅转单声道Vorbis以支持空间衰减，时长保持0.48秒；原文件未改，不额外降低音量。

伤害、射程、散布、ADS、后坐、射速、弹匣、换弹与camera/look弹道不改。

## 渲染与资产

2026-09-27 起资产为 Model V2（AFL 通用 9mm Pure Mesh，见 [Model V2](pistol_suppressor_01_model_v1.md)），由 `tools/build-pistol-suppressor-01.mjs` 生成源、Geo（仅骨骼）、`meshes/pistol_suppressor_01.aflmesh.json` 和 512×512 Base Color / `_s` / `_n`。`NativeMuzzleRendering.drawItem` 现在先查 `AflMeshCache`：附件 Geo 有 AFL Hybrid Mesh sidecar 时走 `AflHybridMeshRendering.renderAtCurrentPose`（同一 `entityCutoutNoCull` 贴图，调用者给出锚点姿态），没有 sidecar 的附件（`rifle_suppressor_01`、Geo 红点、扩容弹匣等）继续走原 GeckoLib Cube 路径，没有任何枪专属 renderer。附件通过 NativeMuzzleRendering 挂到真实动画枪口锚点；当前 P9 由 NativeAnimatedWeaponRenderer 调用 NativeSightRendering，并与 MaintenanceGunRendering 复用同一附件渲染路径。旧 P901SightLayer 已随专用 Renderer 退役。维护台储存的还是原 ItemStack，不修改取回/占位/归属事务。

从附件实际Geo遍历 muzzle_exit_anchor=(0,0,-7.85)（V1 为 -9.1，已废弃），组合为视觉出口。裸枪闪光隐藏，出口生成一颗弱烟粒子；轨迹视觉起点使用新出口，弹壳路径不改。独立附件使用 builtin/entity item模型，经同一 drawItem（现为 Mesh 路径）绘制，并以出口锚点居中；小型手持/掉落缩放0.7；GUI缩放1.6（V1 为 1.4，模型由 9.1 缩短到 7.85）。V2 资产与 Mesh 路径只通过了离线生成校验和 compileJava，手持、维护台、掉落物、GUI 与光影 PBR 尚未实机验收。已安装枪的GUI仍使用既有平面枪图标，不动态合成消音器图标。

维护台 P9 的 3D 附件热点/鼠标装拆现见 [附件交互 V1](gun_maintenance_attachment_interaction_v1.md)。安装手臂/旋紧动画、其他枪附件兼容仍未实现；本轮无附件数值修改。

## 验证边界

以下记录属于 V1 cube 资产时期（锚点、出口 -9.1、GUI 1.4），不代表 Model V2 已实机验收。

- 服务端24/24 GameTest通过：build/suppressor-gametest.log。包括红点生存/创造回归、消音器消耗/占槽/过时请求/旁观拒绝、双槽序列化、箱子与掉落实体保存读取、拾回背包、两位FakePlayer维护台50次交错取回及fallback/重载/拆除，另含既有两枪射击/换弹/ADS与工作台回归。
- 真实 NativeGunShot.execute 下游日志明确记录 GUNSHOT Radius=3.0：build/suppressor-gametest/logs/debug.log；未额外承诺本轮重新逐距离测试僵尸导航。
- 隔离图形客户端通过：build/suppressor-client.log。真实C2S装拆请求和客户端同步、正常/消音SoundEvent进入播放路径、独立baked模型、出口同轴精确坐标、维护台真实双槽同步均通过。已检查截图中的第一/第三人称挂载、维护台俯视双附件、独立配件图标及开火烟雾。截图目录 build/thermal-fluid-client/screenshots/suppressor_*.png。
- 用户实机确认「看起来没问题了，可以完成」。未重新运行真实双客户端远端观察；服务器权威由真实C2S集成客户端与服务端事务测试覆盖。存档持久性使用ItemStack/容器/ItemEntity/BlockEntity序列化读回，不冒称本轮重复完整世界退出重进验收。
- 音频转单声道后的格式/时长已用ffprobe确认；此前客户端已验证声音选择和原音频解码，转换后未再次启动客户端做听感/距离验收。
- 最终离线构建日志 build/suppressor-final-build.log。P9 Geo/贴图/动画哈希保持不变。
- 测试过程说明：最初生成测试被structure namespace筛选导致0项，不计通过；修正后为24项。首个客户端因测试重编译重叠发生类加载失败，不计通过；随后串行运行成功。隔离客户端既有Native Gun Smoke相对源文件路径检查仍失败，与本附件测试分开记录。

未提交、未推送。维护台模型、取回生产代码与P9源动画未改。
> Historical protocol: **22** added P9 MAGAZINE support. Current protocol is **29**; matching client/server required. Field Attachment View runtime acceptance is pending.
