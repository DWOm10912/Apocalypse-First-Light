# Native 姿态精度 V1

## 原实现与单位

NativeGunShot.spread原为固定圆锥采样：cos(theta)在cos(baseDegrees)到1之间均匀采样，方位角均匀。
spreadDegrees是**最大圆锥半角（度）**，不是高斯标准差、全角或Vanilla projectile inaccuracy。
原P9-01与BR51-01均为1.2°；无蹲姿、移动惩罚或恢复。
现保留采样算法，只改变输入半角。枪管姿态不参与命中方向。

## 每枪配置

| 状态 | 默认/P9倍率 | BR51倍率 | P9半角 | BR51半角 |
|---|---:|---:|---:|---:|
| CROUCH_STILL | .45 | .45 | .54° | .135° |
| CROUCH_MOVING | .65 | .65 | .78° | .195° |
| STAND_STILL | 1 | 1 | 1.2° | .30° |
| WALKING | 1.4 | 2.2 | 1.68° | .66° |
| SPRINTING | 2 | 4.5 | 2.4° | 1.35° |
| AIRBORNE | 2.5 | 7 | 3° | 2.10° |

基础半角：P9-01=1.2°，BR51-01=.30°。BR51采用BATTLE_RIFLE配置覆盖移动倍率，
以同时满足精准静止与明显较差跑射/空中目标；算法中没有Registry ID特判。
NativeAccuracyProfile支持其他武器复用DEFAULT或独立倍率。

## 移动与恢复

NativeStanceAccuracy在服务端PlayerTick END采样，取水平deltaMovement速度及位置差/经过tick数的较大值。
阈值0.01格/tick，严格大于才判moving；不是输入键检测。蹲着慢走仍判CROUCH_MOVING。
优先级空中>疾跑>水平移动>静止；地面移动/静止再按isCrouching分支。
同tick沿用已有速度样本，避免松键当tick立即满精度。位置突变超过4格按传送处理，不将其作为持续移动速度。

停止后：最终倍率=current + max(0,previousMovingMultiplier-current) * max(0,1-elapsed/duration)。
蹲姿3tick、站姿5tick（.15/.25秒）。移动或空中重新记录lastMoving；只在静止时衰减。
玩家WeakHashMap瞬态状态，不写ItemStack或网络；死亡、退出、换维度/时间回退重置。
没有射击bloom、ADS或技能稳定度。伤害、射程、弹药、模型、pose与recoil参数不变。

## 权威方向与调试

NativeGunShot.execute：eyePosition + spread(lookAngle, finalDegrees)。
没有从model transform、hand bone或枪口动画取射线方向；检查未发现固定ballistic offset，未加补偿。
Native recoil仍为成功开火后的独立镜头/武器反馈，不用于本发随机精度计算。

默认无日志/聊天。开发启动的游戏JVM加入 `-Dafl.nativeAccuracyDebug=true` 可输出成功射击时：
gun、base、stance、水平速度、recoveryDegrees、finalDegrees。
例如测试前设置JAVA_TOOL_OPTIONS包含该参数，测试后恢复原环境值；不是正式HUD。

## 验证

新增NativeAccuracyGameTests：优先级/抖动阈值/恢复/有限值；真实ServerPlayer位置差且velocity为零的移动检测；
两枪各6状态、20/40/60格、每组10000条射线，对0.6×1.8目标平面统计（固定种子5101）。
平面统计不含实体运动、遮挡、玩家瞄准误差或recoil，是采样器验证，不是实机人形目标射击验收。
构建日志native-accuracy-build.log；GameTest日志native-accuracy-gametest.log。
compileJava/processResources/build通过（37秒）。实机视觉/手感与远距离射击均NOT TESTED。
GameTest共61项，60通过；三项新增精度测试以及两枪既有战斗测试通过。
唯一失败nativenoiseflatdistances：Hearing boundary 20，未确定原因，不声称全回归通过，不扩大本轮修Noise。
git diff --check通过。

固定目标平面命中率（每格10000发；恢复完成；非实机）：

| 枪/距离 | 蹲静 | 蹲走 | 站静 | 走 | 跑 | 空中 |
|---|---:|---:|---:|---:|---:|---:|
| BR51/20 | 100% | 100% | 100% | 100% | 75.42% | 51.01% |
| BR51/40 | 100% | 100% | 100% | 76.84% | 38.82% | 16.20% |
| BR51/60 | 100% | 100% | 98.75% | 54.01% | 17.50% | 7.24% |
| P9/20 | 100% | 100% | 82.50% | 62.54% | 44.73% | 31.63% |
| P9/40 | 89.40% | 66.74% | 44.73% | 25.45% | 12.46% | 8.02% |
| P9/60 | 64.63% | 45.66% | 22.05% | 11.24% | 5.65% | 3.47% |

BR51蹲静60格最大圆锥半径约0.141格，旧1.2°约1.257格；这解释了随机偏离显著缩小，
但不保证有遮挡、目标运动或玩家瞄准误差时必定命中。

人工验收：BR51对20/40/60格静止人形中心，分别蹲静、站静、蹲走、走、跑、空中点射；
停止后等待至少5tick，再比较蹲静命中；回归P9射击、换弹、声音、抛壳和干击。
已启动一个可见客户端PID19500，进入单人游戏，日志native-accuracy-client.log；留待用户实机验收，尚未收到射击确认。
