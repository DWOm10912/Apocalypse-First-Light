# 感染体

<StatusTag s="new" /> 现在的感染体用的是原版僵尸的身体和属性，AFL 改的是它们怎么看、怎么听、怎么破门

## 基本属性

| 项目 | 数值 |
|---|---|
| 生命 | 20 |
| 攻击 | 3 |
| 移动速度 | 0.23 |

- 白天不会被太阳烧
- 不会召唤增援；小僵尸会长大
- 世界里自然刷出的敌对生物只有感染体

## 视觉

- 能看 <V j="InfectedVisionSystem.MAX_VISION_DISTANCE" u="格" /> 远，视野 <V j="InfectedVisionSystem.HORIZONTAL_FOV_DEGREES" u="°" />：从背后靠近不容易被发现
- 离它 <V j="InfectedVisionSystem.CLOSE_AWARENESS_DISTANCE" u="格" /> 以内，不看也会察觉
- 被墙挡住就看不见；要盯着你一会儿才会锁定
- 跟丢以后还会追 <V j="InfectedVisionSystem.LOST_VISUAL_GRACE_TICKS" x="0.05" u="秒" />

## 听觉

感染体会走到声音传来的地方，到了以后等 <V j="InvestigateNoiseGoal.WAIT_TICKS" x="0.05" u="秒" />，再在周围 <V j="InvestigateNoiseGoal.SEARCH_RADIUS" u="格" /> 内找 <V j="InvestigateNoiseGoal.SEARCH_POINT_COUNT" /> 个地方

你的动作声音能传多远：

| 声音 | 范围 |
|---|---|
| 潜行 | 2 格 |
| 走路 | 4 格 |
| 跑步 | 8 格 |
| 落地 | 5 / <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> / <V j="MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS" u="格" /> |
| 拉绳启动发电机 | <V j="PortableDieselGeneratorBlockEntity.PULL_NOISE" u="格" /> |
| 车辆发动机 | <V j="PickupTruckEntity.ENGINE_NOISE" u="格" /> |
| 喇叭 | <V j="PickupTruckEntity.HORN_NOISE" u="格" /> |

- 枪声的范围写在每把枪的参数表里，装了消音器会小很多，见 [枪械](../arms/)
- 走在羊毛和地毯上声音更小
- 你能听见的声音范围和感染体一样：你听得见的，它们也听得见

## 破门

声音强度达到 <V j="InfectedBreachAuthorization.HIGH_INTENSITY_BREACH_THRESHOLD" /> 以上时，感染体会为了靠近声音而破坏挡路的方块：

| 方块 | 需要时间 |
|---|---|
| 树叶、玻璃板 | 1 秒 |
| 玻璃 | 1.5 秒 |
| 木活板门 | 2 秒 |
| 栅栏门 | 2.25 秒 |
| 木门 | 2.5 秒 |
| 木栅栏 | 3 秒 |

石头、铁门和其他方块破坏不了

<Shot k="infected" zh="感染体（截图待补）" en="Infected (screenshot coming)" />

## 还没有

<StatusTag s="planned" /> AFL 自己的感染体模型、不同的感染体种类和威胁等级
