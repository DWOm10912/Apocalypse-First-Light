# 生存状态

原版的心、饥饿条和经验条被 AFL 的生存界面取代：左下角是一个圆形模块，左弧是生命，右弧是体力，中间是体温表；旁边三个小圆是饥饿、口渴和负重，平时又小又淡，快不行时会变大、显示数字、变红

<StatusTag s="new" /> 这些数值是测试起始值，之后还会调

## 体力

上限 <V d="stamina/stamina_v1#max" />，停下 <V d="stamina/stamina_v1#delay_seconds" u="秒" /> 后每秒恢复 <V d="stamina/stamina_v1#regen" />（用光以后要等 <V d="stamina/stamina_v1#exhausted_delay_seconds" u="秒" />）

| 动作 | 消耗 |
|---|---|
| 走路 | <V d="stamina/stamina_v1#costs.walk" u="/秒" /> |
| 潜行 | <V d="stamina/stamina_v1#costs.sneak" u="/秒" /> |
| 跑步 | <V d="stamina/stamina_v1#costs.sprint" u="/秒" /> |
| 游泳 | <V d="stamina/stamina_v1#costs.swim" u="/秒" /> |
| 攀爬 | <V d="stamina/stamina_v1#costs.climb" u="/秒" /> |
| 挖掘 | <V d="stamina/stamina_v1#costs.mining" u="/秒" /> |
| 跳跃（跑跳） | <V d="stamina/stamina_v1#costs.jump" />（<V d="stamina/stamina_v1#costs.sprint_jump" />） |
| 空手 / 持物 / 撬棍挥打 | <V d="stamina/stamina_v1#costs.melee_empty_hand" /> / <V d="stamina/stamina_v1#costs.melee" /> / <V d="stamina/stamina_v1#costs.melee_items.apocalypse_firstlight:crowbar" /> |
| 砍树一下 | <V d="stamina/stamina_v1#costs.chop" /> |
| 开一枪 | <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:9x19mm_round" />（9mm）到 <V d="stamina/stamina_v1#costs.shots_by_ammo.apocalypse_firstlight:12_7x55mm_round" />（12.7mm） |

- 在水里恢复 ×<V d="stamina/stamina_v1#regen_in_water" />，饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时恢复 ×<V d="stamina/stamina_v1#regen_low_food" />
- **喘不过气**：体力用光后，到恢复到 <V d="stamina/stamina_v1#winded_resume" /> 之前不能跑，近战伤害 ×<V d="stamina/stamina_v1#winded.melee_damage" />，挖掘速度 ×<V d="stamina/stamina_v1#winded.dig_speed" />，附近的人能听见你喘气
- **疲劳**：体力低于 <V d="stamina/stamina_v1#fatigue.start" /> 时，举枪会晃，散布最多增加 <V d="stamina/stamina_v1#fatigue.spread_extra" pct />
- 背得越重，体力消耗越多、恢复越慢，见 [背包与负重](./inventory)

## 口渴

满值 <V d="thirst/thirst_v1#max" />，每小时（现实时间）下降 <V d="thirst/thirst_v1#base_per_hour" />，每消耗 100 体力再多降 <V d="thirst/thirst_v1#exertion_per_100_stamina" />

| 口渴值 | 影响 |
|---|---|
| 低于 <V d="thirst/thirst_v1#thirsty_below" /> | 体力恢复 ×<V d="thirst/thirst_v1#stamina_regen_thirsty" /> |
| 低于 <V d="thirst/thirst_v1#dehydrated_below" /> | 体力恢复 ×<V d="thirst/thirst_v1#stamina_regen_dehydrated" />，不再自然回血 |
| 0 | 每 <V d="thirst/thirst_v1#damage_interval_seconds" u="秒" /> 掉 1 点生命，直到渴死 |

**怎么喝水**

- 玻璃瓶对水源方块使用，装一瓶 **脏水**。它会带上取水地点的辐射
- 脏水放进熔炉（10 秒）、烟熏炉（5 秒）或营火（30 秒）。干净的水煮成 **纯净水**；有辐射的水煮成 **煮沸水**，辐射还在
- 喝一瓶 +<V d="thirst/thirst_v1#bottle" />；潜行空手对水源喝一口 +<V d="thirst/thirst_v1#sip" />
- 西瓜片 +<V d="thirst/thirst_v1#foods.minecraft:melon_slice" />、苹果 +<V d="thirst/thirst_v1#foods.minecraft:apple" />、炖菜和汤 +<V d="thirst/thirst_v1#foods.minecraft:mushroom_stew" />、蜂蜜瓶 +<V d="thirst/thirst_v1#foods.minecraft:honey_bottle" />。牛奶不解渴

**闹肚子**：喝脏水有 <V d="thirst/thirst_v1#sickness.chance_bottle" pct /> 的概率（喝生水一口 <V d="thirst/thirst_v1#sickness.chance_sip" pct />）得肠胃感染，持续 <V d="thirst/thirst_v1#sickness.duration_seconds" u="秒" />：口渴加倍，每隔一阵恶心、掉口渴和饥饿。牛奶治不了

## 体温

核心体温从 <V d="temperature/temperature_v1#normal" u="°C" /> 开始，会慢慢向“体感温度”决定的目标靠：气温、遮挡、是否淋湿、附近的热源、运动和衣服都算。体感在 <V d="temperature/temperature_v1#comfort_low" u="°C" /> 到 <V d="temperature/temperature_v1#comfort_high" u="°C" /> 之间最舒服（衣服保暖会降低下限）

| 阶段 | 核心体温 | 影响 |
|---|---|---|
| 冷 1 | 低于 <V d="temperature/temperature_v1#cold_stages.0.below" u="°C" /> | 体力恢复变慢 |
| 冷 2 | 低于 <V d="temperature/temperature_v1#cold_stages.1.below" u="°C" /> | 再加走路变慢、更容易饿、不自然回血 |
| 冷 3 | 低于 <V d="temperature/temperature_v1#cold_stages.2.below" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 掉 1 点生命 |
| 热 1 | 高于 <V d="temperature/temperature_v1#heat_stages.0.above" u="°C" /> | 更容易渴，体力消耗变多 |
| 热 2 | 高于 <V d="temperature/temperature_v1#heat_stages.1.above" u="°C" /> | 再加走路变慢、不自然回血 |
| 热 3 | 高于 <V d="temperature/temperature_v1#heat_stages.2.above" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 掉 1 点生命 |

- 冷的时候屏幕边缘结霜，热的时候发光发晃
- 淋雨大约一分钟就湿透，湿了更冷；没有火的话大约 <V d="temperature/temperature_v1#wetness.dry_seconds" u="秒" /> 才能干
- 营火、火、熔炉、AFL 的箱式炉子都能取暖
- <StatusTag s="new" /> 坐在 T15 的驾驶室里不会淋雨，车里有自己的气温，可以开暖风或冷气（见 [T15](../vehicles/t15)）
- **看读数**：腕式温度计戴在手腕上显示气温；体温计按住 3 秒量体温。两样都没有时界面不显示数字

## 饥饿

饥饿用的是原版系统，AFL 暂时没有自己的食物。饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时体力恢复减半，闹肚子和冷 2 以上会额外消耗饥饿

<StatusTag s="planned" /> 食物腐败和储存
