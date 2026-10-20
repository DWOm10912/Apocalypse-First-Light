# 感染体

<StatusTag s="integrated" /> 目前的感染体沿用原版僵尸的身体和属性，AFL 改写了它们的视觉、听觉和破门行为

## 基本属性

| 项目 | 数值 |
|---|---|
| 生命值 | 20 |
| 攻击伤害 | 3 |
| 移动速度 | 0.23 |

- 白天不会在阳光下燃烧
- 不会召唤增援；小僵尸会长大
- 世界里自然生成的敌对生物只有感染体

## 视觉

- 视距 <V j="InfectedVisionSystem.MAX_VISION_DISTANCE" u="格" />，视野 <V j="InfectedVisionSystem.HORIZONTAL_FOV_DEGREES" u="°" />，从背后靠近不容易被发现
- 进入 <V j="InfectedVisionSystem.CLOSE_AWARENESS_DISTANCE" u="格" /> 以内，即使不在视野里也会被察觉
- 视线被墙挡住就看不见；需要持续看到你一段时间才会锁定
- 丢失目标后仍会继续追击 <V j="InfectedVisionSystem.LOST_VISUAL_GRACE_TICKS" x="0.05" u="秒" />

## 听觉

感染体会前往声源位置，到达后等待 <V j="InvestigateNoiseGoal.WAIT_TICKS" x="0.05" u="秒" />，再在周围 <V j="InvestigateNoiseGoal.SEARCH_RADIUS" u="格" /> 内搜索 <V j="InvestigateNoiseGoal.SEARCH_POINT_COUNT" /> 个位置

你的动作声音的传播范围：

| 声音 | 范围 |
|---|---|
| 潜行 | 2 格 |
| 走路 | 4 格 |
| 疾跑 | 8 格 |
| 落地 | 5 / <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> / <V j="MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS" u="格" /> |
| 拉绳启动发电机 | <V j="PortableDieselGeneratorBlockEntity.PULL_NOISE" u="格" /> |
| 车辆发动机 | <V j="PickupTruckEntity.ENGINE_NOISE" u="格" /> |
| 喇叭 | <V j="PickupTruckEntity.HORN_NOISE" u="格" /> |

- 枪声传播范围见每把枪的参数表，装上消音器后会小很多，见 [枪械](../arms/)
- 走在羊毛和地毯上声音更小
- 你能听见声音的范围和感染体相同：你听得见的，它们也听得见

## 破门

声音强度达到 <V j="InfectedBreachAuthorization.HIGH_INTENSITY_BREACH_THRESHOLD" /> 以上时，感染体会破坏挡路的方块，以便靠近声源：

| 方块 | 需要时间 |
|---|---|
| 树叶、玻璃板 | 1 秒 |
| 玻璃 | 1.5 秒 |
| 木活板门 | 2 秒 |
| 栅栏门 | 2.25 秒 |
| 木门 | 2.5 秒 |
| 木栅栏 | 3 秒 |

感染体无法破坏石头、铁门和其他方块

<Shot k="infected" zh="感染体（截图待补）" en="Infected (screenshot coming)" />

## 还没有

<StatusTag s="planned" /> AFL 自制的感染体模型、不同的感染体种类和威胁等级
