# BR51-01 战斗步枪 HIP / READY 基线调整（2026-09-08）

> 当前正式型号：P9-01 制式手枪（p9_01）；BR51-01 战斗步枪（br51_01）。旧称仅作历史背景，当前映射与验证边界见 docs/native_guns/native_weapon_renaming_report.md。

实现已调整，视觉验收未完成。仅BR51_01第一人称；不改HUD/图标、第三人称、Java、FOV、摄像机或ADS。

## 参数

| Item Display | 旧 | 新 |
|---|---|---|
| translation | [2.7,-6.507,-15.6375] | [3.8,-7.2,-11.5] |
| rotation（度） | [0,0,0] | [0,4,0] |
| scale | [0.45,0.45,0.45] | 不变 |

translation差值[1.1,-0.693,4.1375]，即右移、下移、向镜头收近。偏航4度只作轻微构图倾向，不做中心机械瞄准。
旧脚本按枪托最远Z+8配置相机侧间距，会刻意把完整枪托放在镜头前；这不是重复缩放，而是展示整枪式定位。
移除该推导，使用 `tools/apply-br51_01-hip-pose.mjs` 的集中BR51_01_HIP常量。
它写入bbmodel和runtime item model两种firstperson上下文；完整迁移通过calibrate脚本调用，不再恢复旧公式。
未来ADS应另设基线/插值；本参数仍需用户目测确认后冻结。

## 最终链路

RenderHandEvent PoseStack → ConfiguredGunFirstPerson → NativeGunRecoil.applyViewmodel
→ ItemRenderer.renderStatic → BR51_01 Item Display T/R/S → GeckoLib模型基础变换
→ root/gun_and_righthand/br51_01或mag_and_lefthand等动画层级 → 几何。

手臂在对应driver/hand_pos下的hand_anchor矩阵处分支，translateToPivotPoint
→ NativePlayerArmRenderer取消继承uniform scale → AFL presentation(0.62,0.78,0.62)
→ NativeHandBinding → 完整玩家arm/sleeve。

没有发现第二次应用bbmodel Display；源Display是编辑器镜像，不被Java重复读取。
ConfiguredGunFirstPerson不套P9-01 Service Pistol composition offset；view/positioning是独立辅助分支，
不是枪体父链，不把旧TaCZ camera/view变换额外施加到镜头。

## 手臂与动画保护

左source anchor [6,20,4]、右[-6,20,4]，旋转[0,0,0]，局部offset均0，前后不变。
runtime镜像X后分别[-6,20,4]、[6,20,4]。本轮双手随整体HIP变换一起移动，不单独改变接触点。
先消除整体放置问题，避免未经目测的局部手偏移破坏抓弹匣；若左臂仍横穿，应在实机定位后单独适配。
没有新增状态层adapter；不能宣称左右手视觉已修正。
脚本断言源文件除Display外逐对象完全不变；runtime geo/animation未写入。
原8条关键帧、时长、换弹标准匣交接、第三人称参数均不变。

## 回归

static_idle、static_bolt_caught、shoot、reload_tactical、reload_empty、inspect、draw、put_away
全部使用同一外层基线，因此没有人为切换另一套Display；视觉过渡、穿镜头仍须测试。
Native recoil已接入，BR51_01仍复用现有手枪recoil参数，本轮不调强度；模型兼容NOT TESTED。
P9-01 Service Pistol文件/参数未修改；实机回归NOT TESTED。

构建证据：build/br51_01-hip-build.log。客户端日志：build/br51_01-hip-client.log。
compileJava/processResources/build离线通过（30秒），git diff --check通过。
启动前未发现旧Minecraft实例；已启动一个图形窗口PID27120，日志进入单人世界加载。
自动环境未完成原生游戏截图与操作，VISUAL_VALIDATION_DONE=NO，所有视觉项NOT TESTED。
请在客户端检查：idle枪托/机匣/双臂、走动转向、连续单发、非空R、打空R、空仓待机、
`/afl gun_inspect`、切入/切出；重点记录枪托裁切、换弹出画、握把脱离及结束回位。
这份实现不是视觉完成声明。
