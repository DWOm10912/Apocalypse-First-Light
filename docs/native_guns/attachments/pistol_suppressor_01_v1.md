# 手枪消音器可装备 V1

## 当前范围

- 物品/附件 ID：apocalypse_firstlight:pistol_suppressor_01，手枪消音器 / Pistol Suppressor，堆叠1，创造「AFL 武器与弹药」标签页。无新配方。
- 首批仅 P9：枪数据 muzzle_slot.anchor=muzzle_anchor，accepts=[apocalypse_firstlight:pistol_suppressor_01]。BR51不兼容。
- NativeAttachment 的 MUZZLE 与原 SIGHT 共用 NativeAttachments 和 AflAttachments NBT。独立 NativeSuppressorItem 定义 noiseRadiusMultiplier=0.05、suppressesFireSound=true；倍率不写进 P9 类。
- 主手枪、副手兼容附件、V 安装。生存消耗1，创造保留；已占槽拒绝，不替换。空副手拆回原附件（完整NBT），同时装两个时先SIGHT再MUZZLE。
- 复用 SightExchangePacket，协议17。客户端只发请求，服务端验证主副手完整快照、热键槽、主手NativeGun、兼容/槽、占用、存活/旁观及动作锁/容器状态；不同状态或重复过时请求拒绝。原红点消费/返还规则保持。

## 射击

NativeGunNoise.resolve 从服务器真实 ItemStack 取得有效 MUZZLE，最终半径 max(1,round(base×倍率))，P9 64→3。NativeGunShot 把最终值送给 NoiseSystem→InfectedHearingSystem；耳鸣也读取最终半径和抑音状态，P9原本 tinnitus=false。不新增第二个噪声事件。

P9 可选数据 suppressed_fire_sound=apocalypse_firstlight:p9_01_suppressed；无消音器仍为 p9_01_fire。未来其他兼容枪可声明自己的消音声，未声明时回退原枪声。声音播放音量/衰减沿用原流程，不乘0.05。导入用户 E:/Download/p9_01_suppressed.ogg，资源位于 sounds/weapons/p9_01/p9_01_suppressed.ogg，注册与 sounds.json 同步；导入前未发现相同音频。原文件为48kHz双声道，游戏内副本仅转单声道Vorbis以支持空间衰减，时长保持0.48秒；原文件未改，不额外降低音量。

伤害、射程、散布、ADS、后坐、射速、弹匣、换弹与camera/look弹道不改。

## 渲染与资产

可编辑源 src/main/blockbench/pistol_suppressor_01.bbmodel 与 geo、贴图保持原几何。独立静态Geo通过 NativeMuzzleRendering 挂到真实动画枪口锚点；P901SightLayer/NativeSightRendering 的共享路径被 P9、NativeAnimatedWeaponRenderer 和 MaintenanceGunRendering 复用。维护台储存的还是原 ItemStack，不修改取回/占位/归属事务。

从附件实际Geo遍历 muzzle_exit_anchor=(0,0,-9.1)，组合为视觉出口。裸枪闪光隐藏，出口生成一颗弱烟粒子；轨迹视觉起点使用新出口，弹壳路径不改。独立附件使用 builtin/entity item模型与静态Geo绘制，小型手持/掉落缩放0.7；GUI缩放1.4。已安装枪的GUI仍使用既有平面枪图标，不动态合成消音器图标。

未实现：维护台3D附件热点/鼠标装拆、安装手臂/旋紧动画、其他枪附件兼容、额外数值修改。

## 验证边界

- 服务端24/24 GameTest通过：build/suppressor-gametest.log。包括红点生存/创造回归、消音器消耗/占槽/过时请求/旁观拒绝、双槽序列化、箱子与掉落实体保存读取、拾回背包、两位FakePlayer维护台50次交错取回及fallback/重载/拆除，另含既有两枪射击/换弹/ADS与工作台回归。
- 真实 NativeGunShot.execute 下游日志明确记录 GUNSHOT Radius=3.0：build/suppressor-gametest/logs/debug.log；未额外承诺本轮重新逐距离测试僵尸导航。
- 隔离图形客户端通过：build/suppressor-client.log。真实C2S装拆请求和客户端同步、正常/消音SoundEvent进入播放路径、独立baked模型、出口同轴精确坐标、维护台真实双槽同步均通过。已检查截图中的第一/第三人称挂载、维护台俯视双附件、独立配件图标及开火烟雾。截图目录 build/thermal-fluid-client/screenshots/suppressor_*.png。
- 用户实机确认「看起来没问题了，可以完成」。未重新运行真实双客户端远端观察；服务器权威由真实C2S集成客户端与服务端事务测试覆盖。存档持久性使用ItemStack/容器/ItemEntity/BlockEntity序列化读回，不冒称本轮重复完整世界退出重进验收。
- 音频转单声道后的格式/时长已用ffprobe确认；此前客户端已验证声音选择和原音频解码，转换后未再次启动客户端做听感/距离验收。
- 最终离线构建日志 build/suppressor-final-build.log。P9 Geo/贴图/动画哈希保持不变。
- 测试过程说明：最初生成测试被structure namespace筛选导致0项，不计通过；修正后为24项。首个客户端因测试重编译重叠发生类加载失败，不计通过；随后串行运行成功。隔离客户端既有Native Gun Smoke相对源文件路径检查仍失败，与本附件测试分开记录。

未提交、未推送。维护台模型、取回生产代码与P9源动画未改。
