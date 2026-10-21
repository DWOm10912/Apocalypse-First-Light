# 生存状态

AFL 的生存界面取代了原版的心形生命值、饥饿条和经验条：左下角是一个圆形模块，左弧显示生命值，右弧显示耐力，中间是体温表；旁边三个小圆分别是饥饿、口渴和负重，平时又小又淡，接近危险时会放大、显示数字并变红

<StatusTag s="integrated" /> 这些数值是测试用的初始值，之后还会调整

## 耐力

上限 <V d="stamina/stamina_v1#max" />，停止活动 <V d="stamina/stamina_v1#delay_seconds" u="秒" /> 后每秒恢复 <V d="stamina/stamina_v1#regen" />（耗尽后需等待 <V d="stamina/stamina_v1#exhausted_delay_seconds" u="秒" />）

| 动作 | 消耗 |
|---|---|
| 步行 | <V d="stamina/stamina_v1#costs.walk" u="/秒" /> |
| 潜行 | <V d="stamina/stamina_v1#costs.sneak" u="/秒" /> |
| 疾跑 | <V d="stamina/stamina_v1#costs.sprint" u="/秒" /> |
| 游泳 | <V d="stamina/stamina_v1#costs.swim" u="/秒" /> |
| 攀爬 | <V d="stamina/stamina_v1#costs.climb" u="/秒" /> |
| 挖掘 | <V d="stamina/stamina_v1#costs.mining" u="/秒" /> |
| 跳跃（疾跑跳跃） | <V d="stamina/stamina_v1#costs.jump" />（<V d="stamina/stamina_v1#costs.sprint_jump" />） |
| 空手 / 持物 / 撬棍挥打 | <V d="stamina/stamina_v1#costs.melee_empty_hand" /> / <V d="stamina/stamina_v1#costs.melee" /> / <V d="stamina/stamina_v1#costs.melee_items.apocalypse_firstlight:crowbar" /> |
| 砍树（每下） | <V d="stamina/stamina_v1#costs.chop" /> |
| 射击（每发） | <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:9x19mm_round" />（9mm）到 <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:12_7x55mm_round" />（12.7mm） |

- 在水中耐力恢复 ×<V d="stamina/stamina_v1#regen_in_water" />；饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时耐力恢复 ×<V d="stamina/stamina_v1#regen_low_food" />
- **喘不过气**：耐力耗尽后，恢复到 <V d="stamina/stamina_v1#winded_resume" /> 之前无法疾跑，近战伤害 ×<V d="stamina/stamina_v1#winded.melee_damage" />，挖掘速度 ×<V d="stamina/stamina_v1#winded.dig_speed" />，附近的人能听到你的喘气声
- **疲劳**：耐力低于 <V d="stamina/stamina_v1#fatigue.start" /> 时，举枪会晃动，散布最多增加 <V d="stamina/stamina_v1#fatigue.spread_extra" pct />
- 负重越高，耐力消耗越多，恢复越慢，见 [背包与负重](./inventory)

## 口渴

满值 <V d="thirst/thirst_v1#max" />，每小时（现实时间）下降 <V d="thirst/thirst_v1#base_per_hour" />，每消耗 100 耐力额外下降 <V d="thirst/thirst_v1#exertion_per_100_stamina" />

| 口渴值 | 影响 |
|---|---|
| 低于 <V d="thirst/thirst_v1#thirsty_below" /> | 耐力恢复 ×<V d="thirst/thirst_v1#stamina_regen_thirsty" /> |
| 低于 <V d="thirst/thirst_v1#dehydrated_below" /> | 耐力恢复 ×<V d="thirst/thirst_v1#stamina_regen_dehydrated" />，无法自然恢复生命值 |
| 0 | 每 <V d="thirst/thirst_v1#damage_interval_seconds" u="秒" /> 失去 1 点生命值，直到渴死 |

**饮水**

- 用玻璃瓶对水源方块使用，装一瓶 **瓶装脏水**。它带有取水地点的辐射
- 把脏水放进熔炉（10 秒）、烟熏炉（5 秒）或营火（30 秒）。没有辐射的水煮成 **瓶装净水**；有辐射的水煮成 **瓶装煮沸水**，辐射仍然保留
- 喝一瓶 +<V d="thirst/thirst_v1#bottle" />；潜行时空手对水源喝一口 +<V d="thirst/thirst_v1#sip" />
- 西瓜片 +<V d="thirst/thirst_v1#foods.minecraft:melon_slice" />、苹果 +<V d="thirst/thirst_v1#foods.minecraft:apple" />、炖菜和汤 +<V d="thirst/thirst_v1#foods.minecraft:mushroom_stew" />、蜂蜜瓶 +<V d="thirst/thirst_v1#foods.minecraft:honey_bottle" />。牛奶不解渴

**肠胃感染**：喝脏水有 <V d="thirst/thirst_v1#sickness.chance_bottle" pct /> 的概率（直接喝一口生水为 <V d="thirst/thirst_v1#sickness.chance_sip" pct />）患上肠胃感染，持续 <V d="thirst/thirst_v1#sickness.duration_seconds" u="秒" />：口渴速度翻倍，每隔一段时间恶心一次，同时损失口渴值和饥饿值。牛奶无法治疗

## 体温

核心体温从 <V d="temperature/temperature_v1#normal" u="°C" /> 开始，逐渐趋向由“体感温度”决定的目标值。体感温度综合考虑气温、遮挡、是否淋湿、附近热源、运动和衣物。体感温度在 <V d="temperature/temperature_v1#comfort_low" u="°C" /> 到 <V d="temperature/temperature_v1#comfort_high" u="°C" /> 之间最舒适（衣物保暖会降低下限）

| 阶段 | 核心体温 | 影响 |
|---|---|---|
| 冷 1 | 低于 <V d="temperature/temperature_v1#cold_stages.0.below" u="°C" /> | 耐力恢复减缓 |
| 冷 2 | 低于 <V d="temperature/temperature_v1#cold_stages.1.below" u="°C" /> | 在此基础上移动速度降低、饥饿加快，无法自然恢复生命值 |
| 冷 3 | 低于 <V d="temperature/temperature_v1#cold_stages.2.below" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 失去 1 点生命值 |
| 热 1 | 高于 <V d="temperature/temperature_v1#heat_stages.0.above" u="°C" /> | 水分消耗加快，耐力消耗增加 |
| 热 2 | 高于 <V d="temperature/temperature_v1#heat_stages.1.above" u="°C" /> | 在此基础上移动速度降低，无法自然恢复生命值 |
| 热 3 | 高于 <V d="temperature/temperature_v1#heat_stages.2.above" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 失去 1 点生命值 |

- 寒冷时屏幕边缘结霜，炎热时画面泛光、晃动
- 淋雨大约一分钟就会湿透，潮湿时体温下降更快；没有火源时，大约 <V d="temperature/temperature_v1#wetness.dry_seconds" u="秒" /> 才能干
- 营火、火、熔炉和 AFL 的铸铁箱式炉都能取暖
- <StatusTag s="verified" d="2026-10-19" /> 坐在 T15 驾驶室里不会淋雨，车内有独立的气温，可以开暖风或冷气（见 [T15](../vehicles/t15)）
- <ItemIcon id="wrist_thermometer" /><ItemIcon id="clinical_thermometer" />**查看读数**：腕式温度计戴在手腕上显示气温；体温计按住 3 秒测量体温。两样都没有时，界面不显示数字

## 饥饿

饥饿沿用原版系统，AFL 目前还没有自己的食物；原版食物也已无法获得（<StatusTag s="test" />），新世界里暂时没有吃的。饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时耐力恢复 ×<V d="stamina/stamina_v1#regen_low_food" />；肠胃感染和冷 2 及以上会额外消耗饥饿值

<StatusTag s="planned" /> 食物腐败和储存
