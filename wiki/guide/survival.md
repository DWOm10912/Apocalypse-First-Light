# 生存状态

AFL 的生存界面取代了原版的心形生命值、饥饿条和经验条：左下角是一个圆形模块，左弧显示生命值，右弧显示耐力，中间是体温表；旁边三个小圆分别是饥饿、口渴和负重，平时又小又淡，接近危险时会放大、显示数字并变红

<StatusTag s="integrated" /> 这些数值是测试用的初始值，之后还会调整

## 状态徽章

<StatusTag s="test" /> 状态徽章的提示规则（分支上，未合并）

生存模块上方的一列圆形徽章表示现在身上的状态，没有状态就不显示。颜色表示种类：红色是危险，棕色是不利，蓝色是寒冷或潮湿，绿色是有利；越紧急的越靠近模块

- **静止的部分**：底色圆盘、左上的高光、外圈细边和白色图标，平时都不动
- **外圈扩散**：获得新状态，或者同一状态加重（例如口渴变成脱水、寒冷升一级）时，外圈扩散一次（约 0.7 秒），同时名称和效果从右侧滑出，停留 4 秒后收回
- **说明更新**：阶段没变、只是效果说明变了（例如伤口清洁或包扎了，从清醒恢复换成休息恢复），名称和效果再滑出 4 秒，外圈不扩散
- **外圈进度环**：有时限的状态（胃肠炎、身上沾了汽油或柴油）外圈显示剩余时间，旁边带时间；已清洁的感染和化脓外圈显示好转进度，走完就减轻一级或痊愈。抓伤没有进度环
- **按左 Alt**：所有徽章的名称和效果一起滑出 4 秒；6 秒内只能按一次（按键设置里叫"显示状态说明"，可以改键）
- **消失**：状态结束时徽章直接消失，上面的徽章滑下来补位。同一状态在 10 秒内以相同或更轻的程度再出现，不再提示

例：抓伤后先清洁再包扎，每处理一次都会再滑出一次说明（"已清洁，感染风险降低" → "已清洁并包扎"），徽章保持到抓伤愈合。在火边取暖时，身体还冷、体温在明显回升，就会出现绿色的"回暖"；体温回到不冷，或者已经接近火边能达到的温度、回升变慢，就会消失

## 生命值

- <StatusTag s="test" /> 生命与健康 V1a 还在分支上，没有合并，也没有进游戏验收

生命值上限为 <V d="health/health_v1#max_health" />，所有幸存者相同。生命弧每格 10 点，弧下方显示"当前/上限"。生命值低于上限的 20% 时，生命弧闪动

**受到的伤害**：护甲、抗性和伤害吸收仍按原版计算，剩下的伤害再换算成 100 点制，每次只换算一次。只换算玩家受到的伤害，枪械对感染者的伤害不变

| 来源 | 失去的生命值 |
|---|---|
| 感染者近战 | 原版伤害 ×<V d="health/health_v1#damage.rules.0.scale" /> |
| 枪弹（打中玩家） | P9-01 约 <V d="health/health_v1#damage.rules.1.curve.1.1" />，BR-51 约 <V d="health/health_v1#damage.rules.1.curve.2.1" />，HR-55 约 <V d="health/health_v1#damage.rules.1.curve.3.1" /> |
| 雷击 | <V d="health/health_v1#damage.rules.2.scale" /> 倍，必死 |
| 摔落 | 原版伤害 ×<V d="health/health_v1#damage.rules.3.scale" /> |
| 其他 | 原版伤害 ×<V d="health/health_v1#damage.default_scale" /> |

**自然恢复**

- 受伤后 <V d="health/health_v1#regen.delay_seconds" u="秒" /> 内不恢复。之后清醒时每分钟恢复 <V d="health/health_v1#regen.awake_per_minute" /> 点，休息时（睡觉、坐在椅子上、乘坐载具但不驾驶）每分钟恢复 <V d="health/health_v1#regen.rest_per_minute" /> 点
- 饥饿值低于 <V d="health/health_v1#regen.min_food" />、脱水或体温达到冷 2 / 热 2 时，无法自然恢复生命值
- 睡觉跳过夜晚时，按跳过的时间一次性恢复，每分钟 <V d="health/health_v1#regen.sleep_per_minute" /> 点，最多 <V d="health/health_v1#regen.sleep_max" /> 点
- 原版的饱食回血已关闭

## 伤口

被感染者近战打中时，有 <V d="wounds/wounds_v1#scratch_chance" pct /> 的概率留下抓伤，身上最多 <V d="wounds/wounds_v1#max_wounds" /> 处伤口。伤口在状态图标里显示，离线时不会发展，死亡后清空

| 阶段 | 影响 | 发展 |
|---|---|---|
| 抓伤 | 不扣生命值 | 约 <V d="wounds/wounds_v1#scratch.check_days" /> 个游戏日后判定一次感染：未处理 <V d="wounds/wounds_v1#scratch.infect_chance" pct />，清洁后 ×<V d="wounds/wounds_v1#scratch.cleaned_factor" />，包扎后 ×<V d="wounds/wounds_v1#scratch.bandaged_factor" />，清洁并包扎则不会感染。约 <V d="wounds/wounds_v1#scratch.heal_days" /> 个游戏日愈合，处理后更快 |
| 伤口感染 | 耐力恢复 ×<V d="wounds/wounds_v1#infection.stamina_regen" />，视线不时模糊一阵，几秒后恢复 | 每个游戏日有 <V d="wounds/wounds_v1#infection.worsen_chance" pct /> 的概率化脓（已包扎 <V d="wounds/wounds_v1#infection.worsen_chance_bandaged" pct />），<V d="wounds/wounds_v1#infection.resolve_chance" pct /> 的概率自行好转。清洁后约 <V d="wounds/wounds_v1#infection.recover_days_cleaned" /> 个游戏日好转，清洁并包扎约 <V d="wounds/wounds_v1#infection.recover_days_both" /> 个游戏日 |
| 伤口化脓 | 耐力恢复 ×<V d="wounds/wounds_v1#suppuration.stamina_regen" />，生命恢复 ×<V d="wounds/wounds_v1#suppuration.health_regen" />，高烧：画面发灰，亮处刺眼，视线模糊更频繁、更严重并伴随头晕，偶尔一阵寒战 | 不清洁很难好转（每个游戏日 <V d="wounds/wounds_v1#suppuration.ease_chance" pct /> 的概率减轻为感染）。清洁后先减轻为感染，再按感染好转 |

同一处抓伤再次被抓时，伤势加重，感染概率提高。屏幕效果可以在 AFL 设置的"身体"一栏调弱或关闭

**医疗用品**（目前只在创造模式物品栏里，没有配方）

| 物品 | 用法 |
|---|---|
| <ItemIcon id="medical_alcohol" /> 医用酒精 | 按住右键 <V j="MedicalItem.ALCOHOL_TICKS" x="0.05" u="秒" />，清洁一处伤口（先处理最严重的） |
| <ItemIcon id="bandage" /> 绷带 | 按住右键 <V j="MedicalItem.BANDAGE_TICKS" x="0.05" u="秒" />，包扎一处伤口。化脓的伤口要先清洁 |

每用完一次消耗一个，中途松开不消耗，创造模式不消耗。游泳、在水下或驾驶时不能使用

使用时能看到完整的处理过程，第一人称和第三人称是同一套动作，第一人称会自动低头看着手臂：绷带是伸出左前臂，右手拿着纱布卷绕三圈，布从卷上拉出来缠到手臂上，最后按住布头；医用酒精是拧下瓶盖拿在左手，把酒精倒在左前臂上，再把瓶盖拧回去

包扎好以后，纱布会一直缠在左前臂上（左撇子是右前臂），自己和其他玩家都看得到。没有包扎着的伤口时纱布消失：伤口愈合、感染后包扎失效，或者死亡

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
| 0 | 每 <V d="thirst/thirst_v1#damage_interval_seconds" u="秒" /> 失去 <V d="health/health_v1#damage.default_scale" /> 点生命值，直到渴死 |

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
| 冷 3 | 低于 <V d="temperature/temperature_v1#cold_stages.2.below" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 失去 <V d="health/health_v1#damage.default_scale" /> 点生命值 |
| 热 1 | 高于 <V d="temperature/temperature_v1#heat_stages.0.above" u="°C" /> | 水分消耗加快，耐力消耗增加 |
| 热 2 | 高于 <V d="temperature/temperature_v1#heat_stages.1.above" u="°C" /> | 在此基础上移动速度降低，无法自然恢复生命值 |
| 热 3 | 高于 <V d="temperature/temperature_v1#heat_stages.2.above" u="°C" /> | 每 <V d="temperature/temperature_v1#damage_interval_seconds" u="秒" /> 失去 <V d="health/health_v1#damage.default_scale" /> 点生命值 |

- 寒冷时屏幕边缘结霜，炎热时画面泛光、晃动
- 淋雨大约一分钟就会湿透，潮湿时体温下降更快；没有火源时，大约 <V d="temperature/temperature_v1#wetness.dry_seconds" u="秒" /> 才能干
- 营火、火、熔炉和 AFL 的铸铁箱式炉都能取暖
- <StatusTag s="verified" d="2026-10-19" /> 坐在 T15 驾驶室里不会淋雨，车内有独立的气温，可以开暖风或冷气（见 [T15](../vehicles/t15)）
- <ItemIcon id="wrist_thermometer" /><ItemIcon id="clinical_thermometer" />**查看读数**：腕式温度计戴在手腕上显示气温；体温计按住 3 秒测量体温。两样都没有时，界面不显示数字

## 饥饿

饥饿沿用原版系统，AFL 目前还没有自己的食物。饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时耐力恢复 ×<V d="stamina/stamina_v1#regen_low_food" />；低于 <V d="health/health_v1#regen.min_food" /> 时无法自然恢复生命值；肠胃感染和冷 2 及以上会额外消耗饥饿值

<StatusTag s="planned" /> 食物腐败和储存
