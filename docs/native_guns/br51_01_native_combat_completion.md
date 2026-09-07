# BR51-01 战斗步枪 Native 战斗接入（2026-09-08）

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

## 当前实现与边界

`apocalypse_firstlight:br51_01` 改为 `ConfiguredNativeGunItem implements NativeGunItem`，不继承动画测试物品。
复用原 `P901Actions` 的服务端验证、动作锁、扣弹、`NativeGunShot` hitscan/伤害/Noise 和
`AflNetwork.sendNativeShot` 成功射击通知。保留历史类名以兼容现有包与测试，不复制第二套射击系统。
`NativeAnimatedWeaponItem.Profile` 仅复用视觉配置数据；调试物品的动画命令不接受生产 BR51_01。

临时参数：20 发；半自动（按下沿），间隔 4 tick；基础伤害 18 Minecraft HP。
弹药实际 ID 为 `apocalypse_firstlight:9mm_round`；沿用 `AflGunAmmo/ammoInMagazine`，新物品默认满弹，已有空仓不会初始化补满。
衰减、射程、Noise、recoil/trail暂为共享占位；BR51-01基础散布现为0.30°半角，采用通用姿态精度与BATTLE_RIFLE倍率，见native_stance_accuracy_v1.md。
BR51_01已接入用户专用HUD剪影与GUI图标，详见br51_01_third_person_and_icons.md。
7.62×51mm弹药、对应弹壳和最终平衡均为后续，不是当前接入的阻塞点。

## 状态与时间

### 单次动作播放修正（2026-09-08）

源模型shoot原为loop、两条reload原为hold；GeckoLib4.7.4的thenPlay沿用资源DEFAULT，并不强制单次。
现将源shoot/reload改为once，运行时对应loop=false；inspect/draw/put_away维持单次。
生产控制器所有非待机动作显式使用 `Animation.LoopType.PLAY_ONCE`，仅两个static状态循环。
迁移脚本同步规范播放模式，避免重新导出恢复旧循环；本修正不改关键帧、时长、音效marker或参考臂。
修复的是已触发动作不停循环/停留末帧；未曾射击便首次触发shoot的独立问题尚无复现证据。
本次 `compileJava processResources build --offline --stacktrace` 38秒通过，源/运行时8条动画播放模式检查通过，
`git diff --check` 通过。未启动客户端，本次循环修复尚未实机验收。

| 触发 | 动画 | 时间/行为 |
|---|---|---|
| 有弹待机 | static_idle | 原始循环 |
| 成功射击 | shoot | 0.8333s；机械间隔4tick允许重新触发，不等待整段结束 |
| 最后一发后，shoot结束 | static_bolt_caught | 空弹匣权威循环，不用Java硬改枪机 |
| 非空未满且有备用弹，R | reload_tactical | 2.6s → 52tick，结束时补弹 |
| 空仓且有备用弹，R | reload_empty | 2.8333s → ceil(56.666)=57tick，结束时补弹 |
| 装备/切入 | draw | 0.7333s → 15tick，期间禁止开火/换弹 |
| `/afl gun_inspect`，空闲时 | inspect | 5.25s → 105tick，动作锁，无复杂新按键 |
| 切出 | put_away | 0.4333s → 9tick资源，发送旧物品动画触发；原版切枪可能先停止绘制旧物品，完整可见收枪未保证 |

`NativeGunAnimations` 从打包 JSON 读取时长与声音标记（服务端权威，不支持资源包热替换服务端节拍）。
普通/空仓补弹都在完成时重新计算背包库存，最多补至20；重复请求、换枪、死亡、跨维度取消不能复制弹药。
没有9mm不能开始换弹；创意模式同样按既有库存消耗语义，不额外制造弹药。

## 声音与FX

- 成功射击只由服务端播放 `apocalypse_firstlight:br51_01_fire` → `sounds/br51_01/fire.ogg`，源为 `br51_01_shoot.ogg`。
- 生产 controller 不注册 GeckoLib sound handler，shoot JSON 原marker保留但不消费，避免与真实枪声双播。
- reload/inspect/draw 的 `sound_effects` 由真实动作会话按20Hz逐条播放，取消动作即取消未播放标记。
- put_away 的首个标记（0.0333s）在切出通知时播放，误差不足1tick；不创建新hotbar过渡系统。
- dry-fire 复用 `apocalypse_firstlight:p9_01_dry_fire` 与6tick防刷；无伤害、正常枪声、shoot或抛壳。
- 10个 BR51_01 event：`br51_01_fire`、`br51_01_reload_empty_1..4`、`br51_01_reload_tactical_1..3`、`br51_01_draw`、`br51_01_put_away`。
- inspect的两个旧wav引用使用目录现有 tactical1/2；draw/put-away采用br51_01_draw/br51_01_draw_1；为替代映射，音频文件未重新编码。
- 通用渲染器把源 `shell` / `muzzle_pos` 当帧矩阵映射到共享 FX 消费者的 ejection/muzzle 语义。
  每次成功射击沿用 `NativeGunFx` 的9mm casing、重力、弹跳、音效与上限；失败/换弹不发送FX通知。

## 模型与手臂

保留上一轮 BR51_01 driver hierarchy、所有8条原运动轨道与 AFL hand anchor 适配。
真实玩家 arm/sleeve 仍由 `NativePlayerArmRenderer` 绘制，没有 TaCZ 手臂运行时。
新增配置枪第一人称入口独占双手pass，不套用手枪专用 composition offset；手枪入口/资源不改。
`bolt2`、`charger` 源中本就不存在，按用户确认不新增几何或强绑其它部件。
additional_magazine原为空骨骼，现已补齐标准旧弹匣子树，只在reload_empty前段显示；
原动画轨道、普通换弹和战斗语义不变，详见 `br51_01_visual_animation_fix_report.md`。
V1 runtime现仅标准弹匣及空仓临时标准匣；扩容款、sight/laser子树和grip_default仅保留在源中，详见 `br51_01_v1_white_gun_asset_cleanup.md`。
白版外观、空仓换弹交接、移除前握把后的手部接触与切出动画仍需逐段实机验收。

## 文件

### 2026-09-08 静态握持与显示适配校正

- 左右hand_anchor由Y=8、Z旋转180度改为Y=20、旋转零，沿原手臂驱动骨骼绑定握持端；不交换左右手名称。
- 同步源参考臂和runtime geo；参考臂仍export=false，真实玩家手臂仍使用共享绘制规则。
- 第一人称旧Display [2.7,-6.507,-15.6375]/旋转零已被HIP基线替代：位置[3.8,-7.2,-11.5]、旋转[0,4,0]、比例0.45。见 `br51_01_first_person_hip_pose_fix.md`；视觉仍待确认。
- 第三人称采用CROSSBOW_HOLD（ConfiguredNativeGunItem主手），Display现旋转[0,0,0]、位置[0,-1.82,-4.24]、比例0.4，撤销导致竖枪的额外-90度pitch。仍需实机确认接触与朝向，不代表动画化第三人称手臂已实现。
- 原8条动画关键帧、时长和声音标记不变；没有修改runtime animation文件、共享手臂比例或制式手枪资源。
- 实现脚本：tools/calibrate-br51_01-binding.mjs；完整迁移末尾调用该校准脚本。
- compileJava/processResources/build离线通过（42秒），git diff --check通过；本次未启动图形客户端，视觉验收待完成。

- 注册：`src/main/java/com/antaurora/apofirstlight/registry/AflItems.java`
- 公共战斗：`weapon/NativeGunItem.java`、`NativeGunDefinition.java`、`P901Actions.java`
- 新生产物品：`weapon/ConfiguredNativeGunItem.java`；源时间线：`weapon/NativeGunAnimations.java`
- 输入/渲染：`weapon/client/P901Input.java`、`ConfiguredGunFirstPerson.java`、`NativeAnimatedWeaponRenderer.java`
- inspect入口：`weapon/NativeAnimationCommands.java`；中英tooltip：`assets/apocalypse_firstlight/lang/{en_us,zh_cn}.json`
- 测试：`src/dev/java/com/antaurora/apofirstlight/dev/BR5101CombatGameTests.java`、`src/dev/br51_01-combat-gametest.init.gradle`

## 验证（等待用户实机，不作为全部完成声明）

- compileJava/processResources/build任务通过；随后隔离GameTest为57/58通过，因此组合命令最终退出非零。
- 新增3项BR51_01测试全部通过：生产类型/20发/4tick/18HP参数、真实入口扣弹与干击无补弹、52/57tick完成补弹与防重复、切出取消、部分备用弹、声音event注册。
- 原有P9-01 Service Pistol弹量/伤害测试通过；唯一失败 `nativenoiseflatdistances: Hearing boundary 40`，日志显示40格监听者 `registered=false`。原因未在本轮确定，不能声称全回归通过。
- 证据：`build/br51_01-combat-check.log`；不把测试失败说成编译错误，也不把已知失败当作通过。
- 13个源声音标记对应的10个event均存在，所有OGG路径存在；BR51_01动画无旧namespace引用。
- `runClient --offline -PaflWithoutTacz` 已启动可见窗口。截图工具获取到CS2画面而非Minecraft，因此未继续操作；用户回复“等会我测试”，客户端留待用户测试。
- 客户端日志有既有开发smoke的 `Ready muzzle ray calibration at reference plane` 失败，未改手枪构图或降低检查阈值；未发现BR51_01旧namespace或missing sound错误，但尚未触发全套实机声音，不能据此宣称听感通过。
- 听感/抛壳/完整八状态与P9-01 Service Pistol实机回归仍为NOT TESTED；构建不等于图形验收。
- FX骨骼名称已从渲染器字面量移入每枪Profile（值仍是muzzle_pos/shell）；上述单次播放修正构建时已一起编译。构建前确认没有运行中的Java客户端。
- 可复现：创造栏取得BR51_01与9mm；等待draw结束；左键点射、按住不连发；非空R、打空后R；无弹R与dry-fire；切出取消换弹；`/afl gun_inspect`；对照P9-01 Service Pistol同组检查。

COMMIT = NO；PUSH = NO。
