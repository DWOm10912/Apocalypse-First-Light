# 生存状态

AFL 的生存界面取代了原版的心形生命值、饥饿条和经验条：左下角是一个圆形模块，左弧显示生命值，右弧显示耐力，中间是体温表；旁边三个小圆分别是饥饿、口渴和负重，平时又小又淡，接近危险时会放大、显示数字并变红

<StatusTag s="integrated" /> 这些数值是测试用的初始值，之后还会调整

## 状态徽章

<StatusTag s="verified" d="2026-10-20" /> 状态徽章的提示规则

生存模块上方的一列圆形徽章表示现在身上的状态，没有状态就不显示。颜色表示种类：红色是危险，棕色是不利，蓝色是寒冷或潮湿，绿色是有利；越紧急的越靠近模块

- **静止的部分**：底色圆盘、左上的高光、外圈细边和白色图标，平时都不动
- **外圈扩散**：获得新状态，或者同一状态加重（例如口渴变成脱水、寒冷升一级）时，外圈扩散一次（约 0.7 秒），同时名称和效果从右侧滑出，停留 4 秒后收回
- **说明更新**：阶段没变、只是效果说明变了（例如伤口清洁或包扎了，从清醒恢复换成休息恢复），名称和效果再滑出 4 秒，外圈不扩散
- **外圈进度环**：有时限的状态（胃肠炎、身上沾了汽油或柴油）外圈显示剩余时间，旁边带时间；已清洁的感染和化脓外圈显示好转进度，走完就减轻一级或痊愈。抓伤没有进度环
- **按左 Alt**：所有徽章的名称和效果一起滑出 4 秒；6 秒内只能按一次（按键设置里叫"显示状态说明"，可以改键）
- **消失**：状态结束时徽章直接消失，上面的徽章滑下来补位。同一状态在 10 秒内以相同或更轻的程度再出现，不再提示

例：抓伤后先清洁再包扎，每处理一次都会再滑出一次说明（"已清洁，感染风险降低" → "已清洁并包扎"），徽章保持到抓伤愈合。在火边取暖时，身体还冷、体温在明显回升，就会出现绿色的"回暖"；体温回到不冷，或者已经接近火边能达到的温度、回升变慢，就会消失

## 生命值

- <StatusTag s="verified" d="2026-10-20" /> 生命与健康 V1a

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

- 受伤后 <V d="health/health_v1#regen.delay_seconds" u="秒" /> 内不恢复。之后清醒时每分钟恢复 <V d="health/health_v1#regen.awake_per_minute" /> 点，休息时（睡觉、坐在椅子上、坐在地上、乘坐载具但不驾驶）每分钟恢复 <V d="health/health_v1#regen.rest_per_minute" /> 点
- 饥饿值低于 <V d="health/health_v1#regen.min_food" />、脱水或体温达到冷 2 / 热 2 时，无法自然恢复生命值
- 睡觉跳过夜晚时，按跳过的时间一次性恢复，每分钟 <V d="health/health_v1#regen.sleep_per_minute" /> 点，最多 <V d="health/health_v1#regen.sleep_max" /> 点
- 原版的饱食回血已关闭

## 伤口

被感染者近战打中时，有 <V d="wounds/wounds_v1#scratch_chance" pct /> 的概率留下抓伤，身上最多 <V d="wounds/wounds_v1#max_wounds" /> 处伤口。伤口在状态图标里显示，离线时不会发展，死亡后清空

<StatusTag s="verified" d="2026-10-20" /> 每处伤口有部位：多在手臂上，其次是胸部、腹部和腿，不会在头上。打开背包能在身体图上看到伤在哪里（见 [背包](./inventory#身体图)）

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
| <ItemIcon id="bandage" /> 绷带 | 按住右键包扎一处伤口：手臂 <V j="MedicalItem.BANDAGE_TICKS" x="0.05" u="秒" />，头 <V j="MedicalItem.BANDAGE_HEAD_TICKS" x="0.05" u="秒" />，胸部或腹部 <V j="MedicalItem.BANDAGE_TORSO_TICKS" x="0.05" u="秒" />，腿 <V j="MedicalItem.BANDAGE_LEG_TICKS" x="0.05" u="秒" />（头、胸、腹、腿的时长 <StatusTag s="test" />）。化脓的伤口要先清洁 |
| <ItemIcon id="splint" /> 夹板 | <StatusTag s="verified" d="2026-10-20" /> 固定一处骨折（先固定腿）。腿要坐在地上打：站着按右键会先坐下，坐好后按住右键 <V j="SplintItem.LEG_USE_TICKS" x="0.05" u="秒" />，打完继续坐着。手臂站着或坐着都能打，按住右键 <V j="SplintItem.USE_TICKS" x="0.05" u="秒" /> |

每用完一次消耗一个，中途松开不消耗，创造模式不消耗。游泳、在水下或驾驶时不能使用

使用时能看到完整的处理过程，第一人称和第三人称是同一套动作，第一人称会自动低头看着手臂。伤在手臂上时：绷带是伸出左前臂，右手拿着纱布卷绕三圈，布从卷上拉出来缠到手臂上，最后按住布头；医用酒精是拧下瓶盖拿在左手，把酒精倒在左前臂上，再把瓶盖拧回去

包扎好以后，纱布会一直缠在伤口所在的部位，自己和其他玩家都看得到。没有包扎着的伤口时纱布消失：伤口愈合、感染后包扎失效，或者死亡

<StatusTag s="verified" d="2026-10-20" /> 纱布缠在伤口所在的手臂或腿上

- <StatusTag s="test" /> 伤在别的部位时，在那个部位上处理（左撇子左右反过来）：
  - 头：先把纱布垫按在伤口上，两只手轮流把纱布卷从脑后递过去，在额头到头顶缠三圈。倒酒精时头往一边歪，倒在朝上的那一侧
  - 胸部、腹部：先按纱布垫，两只手轮流从背后递卷，绕身体缠三圈。倒酒精时身体稍微往后仰，倒在前面
  - 腿：要坐在地上处理，站着按右键会先坐下，坐好后再按住右键，处理完继续坐着。包扎时抬起膝盖，两只手轮流从小腿下面递卷；倒酒精时顺着小腿往下倒
  - 胸部和腹部的伤只会在前面和两侧。背枪被雷击烫出的伤在背上，自己够不到，没法清洁和包扎，只能等它自己愈合，背包身体图上会写明

- <StatusTag s="test" /> 伤口画在皮肤上，比皮肤细一档（一个皮肤像素 4 × 4，和皮肤一样是清楚的像素），每处伤口位置固定：抓伤是三道两头尖的爪痕，有血珠和擦伤，之后结痂、留下淡色细线；感染后伤口周围一片红肿；化脓时伤口最深处有几颗奶黄色的脓点；慢慢愈合时颜色变淡，好了就消失。头部、胸部、腹部包扎后在伤口上盖一块纱布贴片，两边贴着胶带，透出一点血色（感染是黄褐色）；手臂和腿包扎后照旧缠纱布。皮肤第二层和护甲盖住的地方看不到。背包身体图上能看到正面的伤痕

## 骨折

- <StatusTag s="verified" d="2026-10-20" /> 骨折与夹板
- <StatusTag s="verified" d="2026-10-20" /> 坐在地上：按 N 坐下，再按 N、跳跃或潜行站起。只能坐在方块的碰撞面上（不在空中、水里或梯子上），坐着算休息。见 [操作按键](./controls)

从高处摔下、扣了血时，落地分两种：

- <StatusTag s="verified" d="2026-10-20" /> **硬着陆**：站稳了，屈膝往下一沉。只可能摔断腿
- <StatusTag s="verified" d="2026-10-20" /> **摔倒**：没站稳，向前扑倒，一只手先撑地，跪趴一会儿再站起来，这几秒里不能移动，也不能用东西。只有摔倒时才可能摔断手臂，断的是撑地那只手

摔倒的概率 =（扣掉的生命值 − <V d="health/health_v1#fracture.fall_over.chance.0" />）÷ <V d="health/health_v1#fracture.fall_over.chance.1" />，最高 <V d="health/health_v1#fracture.fall_over.chance.2" pct />，再按落地时的情况加减：

- 腿本来就骨折：×<V d="health/health_v1#fracture.fall_over.broken_leg" />
- 负重超过轻载：+<V d="health/health_v1#fracture.fall_over.load" pct />；疾跑着跳下去：+<V d="health/health_v1#fracture.fall_over.speed" pct />；落在方块边缘或斜面上：+<V d="health/health_v1#fracture.fall_over.edge" pct />
- 落地时按住潜行：×<V d="health/health_v1#fracture.fall_over.sneak" />
- 这一下摔断了腿，一定会摔倒

骨折按实际扣掉的生命值判定，所以护甲、落进水里都会降低概率：

- **腿**（左右随机）：概率 =（扣掉的生命值 − <V d="health/health_v1#fracture.leg_fall.0" />）÷ <V d="health/health_v1#fracture.leg_fall.1" />，最高 <V d="health/health_v1#fracture.leg_fall.2" pct />
- **手臂**（只在摔倒时，撑地那只手）：概率 =（扣掉的生命值 − <V d="health/health_v1#fracture.arm_fall.0" />）÷ <V d="health/health_v1#fracture.arm_fall.1" />，最高 <V d="health/health_v1#fracture.arm_fall.2" pct />
- 已经骨折的部位再摔断：愈合进度倒退 <V d="health/health_v1#fracture.rebreak_setback" pct />，夹板松脱，要重新打

| 影响 | 未固定 | 用夹板固定后 |
|---|---|---|
| 腿：移动速度 | ×<V d="health/health_v1#fracture.leg.move.0" /> | ×<V d="health/health_v1#fracture.leg.move.1" /> |
| 腿：跳跃高度（仍能跳上一格） | ×<V d="health/health_v1#fracture.leg.jump.0" /> | ×<V d="health/health_v1#fracture.leg.jump.1" /> |
| 腿：疾跑 | 愈合到 <V d="health/health_v1#fracture.sprint_from.0" pct /> 后可以 | 愈合到 <V d="health/health_v1#fracture.sprint_from.1" pct /> 后可以 |
| 腿：走动消耗的耐力 | ×<V d="health/health_v1#fracture.leg.stamina.0" /> | ×<V d="health/health_v1#fracture.leg.stamina.1" /> |
| 拿枪的手臂：瞄准晃动 | ×<V d="health/health_v1#fracture.main_arm.sway.0" /> | ×<V d="health/health_v1#fracture.main_arm.sway.1" /> |
| 拿枪的手臂：挖掘速度 / 近战伤害 | ×<V d="health/health_v1#fracture.main_arm.dig.0" /> / ×<V d="health/health_v1#fracture.main_arm.melee.0" /> | ×<V d="health/health_v1#fracture.main_arm.dig.1" /> / ×<V d="health/health_v1#fracture.main_arm.melee.1" /> |
| 另一只手臂：瞄准晃动 | ×<V d="health/health_v1#fracture.support_arm.sway.0" /> | ×<V d="health/health_v1#fracture.support_arm.sway.1" /> |
| 手臂：爬梯子和藤蔓 | ×<V d="health/health_v1#fracture.main_arm.climb.0" /> | ×<V d="health/health_v1#fracture.main_arm.climb.1" /> |
| 生命值自然恢复上限（腿 / 手臂） | <V d="health/health_v1#fracture.leg.cap.0" /> / <V d="health/health_v1#fracture.main_arm.cap.0" /> | <V d="health/health_v1#fracture.leg.cap.1" /> / <V d="health/health_v1#fracture.main_arm.cap.1" /> |

- 腿骨折时从 2 格以上落地会多受一些伤
- **愈合**：用夹板固定后，腿约 <V d="health/health_v1#fracture.leg_heal_days" /> 个游戏日、手臂约 <V d="health/health_v1#fracture.arm_heal_days" /> 个游戏日长好；不固定愈合速度 ×<V d="health/health_v1#fracture.unsplinted" />；休息时 ×<V d="health/health_v1#fracture.rest" />。愈合到 <V d="health/health_v1#fracture.fade_from" pct /> 以后影响逐渐消失。和伤口一样只在线时计时，睡觉跳过的夜晚也算
- 打上的夹板留在那条腿或手臂上，长好后消失。死亡后骨折清空
- <StatusTag s="verified" d="2026-10-20" /> 骨折的那一刻能听到骨头断裂的声音，附近 <V j="MovementNoiseEvents.VERY_HEAVY_LANDING_RADIUS" /> 格内的人也听得到
- <StatusTag s="verified" d="2026-10-20" /> 摔倒时手撑地那一下有身体着地的声音，附近 <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> 格内听得到
- 换弹不会因为手臂骨折变慢（以后再做）

## 受击和倒地

- <StatusTag s="test" /> 受击和倒地 V1：只在分支上，还没进游戏验证

**受击**：被打、被枪打中、被炸或被车撞时，身体会往远离来源的方向晃一下：正面中弹往后仰，背后中弹往前扑，侧面中弹往另一侧歪。伤得越重晃得越大，几下伤会叠在一起。镜头不动。摔落和雷击没有来源方向，不晃。

**被打倒**：伤害不致死时可能被打倒。人往远离来源的方向倒下，躺一会儿再自己爬起来，这几秒里不能移动，也不能用东西。手里的东西有 <V d="health/health_v1#knockdown.drop" pct /> 的几率脱手飞出去。

- 爆炸、倒下的树和车辆撞击：扣血至少 <V d="health/health_v1#knockdown.always_min" /> 就一定倒
- 其他有来源的伤害：概率 =（扣掉的生命值 − <V d="health/health_v1#knockdown.chance.0" />）÷ <V d="health/health_v1#knockdown.chance.1" />，最高 <V d="health/health_v1#knockdown.chance.2" pct />。负重超过轻载 +<V d="health/health_v1#knockdown.load" pct />，腿骨折 ×<V d="health/health_v1#knockdown.broken_leg" />，疾跑中 +<V d="health/health_v1#knockdown.sprint" pct />，潜行 ×<V d="health/health_v1#knockdown.sneak" />。感染者一爪打不倒人，BR-51 一枪一定倒
- 来源在正前方 <V d="health/health_v1#knockdown.front_cone" u="°" ue="°" /> 以内往后倒，在正后方往前扑，在两侧往另一侧倒
- 倒地那一下有身体着地的声音，附近 <V j="MovementNoiseEvents.HEAVY_LANDING_RADIUS" /> 格内听得到
- 在水里、坐着或已经趴在地上时不会被打倒

**被车撞飞**：<StatusTag s="test" /> 被车撞了没死时，人会像布娃娃一样被撞飞、落地、翻滚，停下来以后从躺着的姿势自己爬起来。第一人称镜头跟着头一起飞出去。飞着和爬起来时不能移动，也不能用东西。停下时生命值只剩 <V d="health/health_v1#collapse.at" /> 或更少就爬不起来，直接瘫倒（见下）。飞着时被撞死，尸体接着往前飞

**重伤瘫倒**：一下受伤后生命值只剩 <V d="health/health_v1#collapse.at" /> 或更少（又没死）时，膝盖一软跪下，往前栽倒，只能趴着爬：

- 爬得更慢（×<V d="health/health_v1#collapse.crawl" />），不能跳，不能疾跑
- 可以用东西，比如给自己包扎
- 至少趴 <V d="health/health_v1#collapse.min_seconds" u="秒" ue="s" />，之后生命值回到 <V d="health/health_v1#collapse.up_at" /> 就站起来；趴满 <V d="health/health_v1#collapse.max_seconds" u="秒" ue="s" /> 也会缓过来自己站起。头顶太低时接着爬，出来再站
- 饥饿、口渴、冻伤、中暑和雷击这类伤害不会让人瘫倒
- 下线再上线不会接着趴

## 耳鸣

- <StatusTag s="test" /> 耳鸣 V2：负面状态。用户 2026-10-21 在分支上实机通过，等合并进主线

近处的枪声、爆炸和雷击会累积听力负荷，负荷到一定程度就会耳鸣。耳鸣时耳朵会不时响起来，随机在左耳、右耳或两耳；档位越高，响得越频繁，周围的声音也越小

| 状态 | 听力负荷 | 两次耳鸣间隔 | 每次持续 | 周围声音 |
|---|---|---|---|---|
| 轻度耳鸣 | ≥ <V d="hearing/tinnitus_v2#stages.0" /> | <V d="hearing/tinnitus_v2#interval.0.0" />–<V d="hearing/tinnitus_v2#interval.0.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.0.0" />–<V d="hearing/tinnitus_v2#length.0.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.0" /> |
| 耳鸣 | ≥ <V d="hearing/tinnitus_v2#stages.1" /> | <V d="hearing/tinnitus_v2#interval.1.0" />–<V d="hearing/tinnitus_v2#interval.1.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.1.0" />–<V d="hearing/tinnitus_v2#length.1.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.1" /> |
| 严重耳鸣 | ≥ <V d="hearing/tinnitus_v2#stages.2" /> | <V d="hearing/tinnitus_v2#interval.2.0" />–<V d="hearing/tinnitus_v2#interval.2.1" u="秒" ue="s" /> | <V d="hearing/tinnitus_v2#length.2.0" />–<V d="hearing/tinnitus_v2#length.2.1" u="秒" ue="s" /> | ×<V d="hearing/tinnitus_v2#hearing.2" /> |

听力负荷最多 <V d="hearing/tinnitus_v2#max_load" />。档位升高时立刻耳鸣一次

- **枪声**：每发的负荷 = 10^((耳边声压 − <V d="hearing/tinnitus_v2#reference_db" />) ÷ 10)。耳边声压见各枪械页的数据表，例如 170 dB 每发 1，160 dB 每发 0.1。装消音器减 <V d="hearing/tinnitus_v2#suppressor_db" u="dB" />。别人开枪按距离的平方衰减，<V d="hearing/tinnitus_v2#range" u="格" ue="blocks" /> 以外不算
- **爆炸**：负荷 = <V d="hearing/tinnitus_v2#blast" /> ×（爆炸半径 ÷ <V d="hearing/tinnitus_v2#blast_radius" />）² ÷ 距离²，低于 <V d="hearing/tinnitus_v2#blast_min" /> 不算。落雷按半径 <V d="hearing/tinnitus_v2#lightning_radius" /> 的爆炸算
- 头顶看不到天（室内）时负荷 ×<V d="hearing/tinnitus_v2#indoor" />
- 戴 [简易护耳罩](../arms/equipment#工具) 时每份负荷 ×<V j="SimpleHearingProtectionItem.IMPULSE" />；幸存者特质也会改变耳鸣倍率
- **震耳**：一次负荷达到 <V d="hearing/tinnitus_v2#jolt_from" /> 会立刻耳鸣，周围的声音降到 ×<V d="hearing/tinnitus_v2#jolt_hearing" />，<V d="hearing/tinnitus_v2#jolt_recover.0" />–<V d="hearing/tinnitus_v2#jolt_recover.1" u="秒" ue="s" /> 内恢复，负荷越大恢复越慢
- **恢复**：听力负荷每 <V d="hearing/tinnitus_v2#half_life_ticks" x="0.05" u="秒" ue="s" /> 减半，睡觉时快 <V d="hearing/tinnitus_v2#sleep" /> 倍，只在在线时计时。死亡后清空
- 状态徽章外圈显示耳鸣有多重：开枪、爆炸会让它变满，之后随恢复慢慢变短，不显示倒计时

## 雷击伤

- <StatusTag s="test" /> 雷击伤 V1：只在分支上，还没进游戏验证

落点按滚球法算（闪电 V2，只在分支上，用户 2026-10-22 实机验证过）：每道雷的击距在 <V d="lightning/lightning_injury_v1#strike.distance.0" />–<V d="lightning/lightning_injury_v1#strike.distance.2" u="m" /> 之间，最常见 <V d="lightning/lightning_injury_v1#strike.distance.1" u="m" />，先导下来时最先进入击距的东西被劈。高度和孤立决定落点，金属不吸雷。站在空地、山顶、孤树底下最危险；身边有更高的树、房子或避雷针时，雷会先打它们。落雷前没有任何预兆，详见[天气](../world/weather#落点和避雷针)

被雷击分三种：

| 方式 | 条件 | 当场死亡 | 失去生命值 | 昏迷 | 双腿麻痹 | 鼓膜穿孔 |
|---|---|---|---|---|---|---|
| 直击 | 被雷正中 | <V d="lightning/lightning_injury_v1#direct.death" pct="true" /> | <V d="lightning/lightning_injury_v1#direct.health.0" />–<V d="lightning/lightning_injury_v1#direct.health.1" /> | <V d="lightning/lightning_injury_v1#direct.unconscious.0" />–<V d="lightning/lightning_injury_v1#direct.unconscious.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#direct.paralysis.0" />–<V d="lightning/lightning_injury_v1#direct.paralysis.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#direct.eardrum" pct="true" /> |
| 侧闪 | 落点 <V d="lightning/lightning_injury_v1#side_range" u="格" ue="blocks" /> 内 | <V d="lightning/lightning_injury_v1#side.death" pct="true" /> | <V d="lightning/lightning_injury_v1#side.health.0" />–<V d="lightning/lightning_injury_v1#side.health.1" /> | <V d="lightning/lightning_injury_v1#side.unconscious_chance" pct="true" /> 概率，<V d="lightning/lightning_injury_v1#side.unconscious.0" />–<V d="lightning/lightning_injury_v1#side.unconscious.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#side.paralysis_chance" pct="true" /> 概率，<V d="lightning/lightning_injury_v1#side.paralysis.0" />–<V d="lightning/lightning_injury_v1#side.paralysis.1" u="秒" ue="s" /> | <V d="lightning/lightning_injury_v1#side.eardrum" pct="true" /> |
| 地电流 | 落点 <V d="lightning/lightning_injury_v1#ground_range" u="格" ue="blocks" /> 内，站在地上或水里（水里 <V d="lightning/lightning_injury_v1#water_range" u="格" ue="blocks" />） | 不会 | <V d="lightning/lightning_injury_v1#ground.health.0" />（近）–<V d="lightning/lightning_injury_v1#ground.health.1" />（远） | 不会 | <V d="lightning/lightning_injury_v1#ground.paralysis_chance" pct="true" /> 概率，<V d="lightning/lightning_injury_v1#ground.paralysis.0" />–<V d="lightning/lightning_injury_v1#ground.paralysis.1" u="秒" ue="s" /> | 不会 |

- 被雷击中的人身上不会着火，身边被雷点着的火会马上熄灭；离得远的草照样会烧起来。直击和侧闪会让耳鸣直接到严重耳鸣
- 坐在关着门的车厢里不受伤；室内头顶看不到天时不会被直击或侧闪
- 被击中的一瞬间眼前一片白光。昏迷时屏幕全黑，周围的声音变得很远，无法移动、使用物品或打开背包；受到至少 <V d="lightning/lightning_injury_v1#wake_damage" u="点伤害" ue="damage" /> 会醒过来。醒来后视线模糊 <V d="lightning/lightning_injury_v1#wake_blur" u="秒" ue="s" />
- **双腿麻痹**：只能趴着爬，速度 ×<V d="lightning/lightning_injury_v1#crawl" />，无法跳跃。结束后从地上站起来，之后腿软 <V d="lightning/lightning_injury_v1#weak_legs.0" />–<V d="lightning/lightning_injury_v1#weak_legs.1" u="秒" ue="s" />：移动速度 ×<V d="lightning/lightning_injury_v1#weak_move" />，无法疾跑和跳跃，耐力消耗 ×<V d="lightning/lightning_injury_v1#weak_stamina" />
- **鼓膜穿孔**：一侧耳朵听不清，周围的声音 ×<V d="lightning/lightning_injury_v1#eardrum_hearing" />，持续 <V d="lightning/lightning_injury_v1#eardrum_days" u="天" ue="days" />（在线时间），后一半时间慢慢恢复。耳鸣多半出现在这只耳朵
- **雷击后遗**：直击、侧闪，或落点 <V d="lightning/lightning_injury_v1#ground_aftermath" u="格" ue="blocks" /> 内的地电流会留下，持续 <V d="lightning/lightning_injury_v1#aftermath_days" u="天" ue="days" />。期间不时头痛，视线模糊几秒（间隔 <V d="lightning/lightning_injury_v1#headache_gap.0" />–<V d="lightning/lightning_injury_v1#headache_gap.1" u="秒" ue="s" />），瞄准晃动 ×<V d="lightning/lightning_injury_v1#aftermath_sway" />，耐力恢复 ×<V d="lightning/lightning_injury_v1#aftermath_stamina_regen" />，生命恢复 ×<V d="lightning/lightning_injury_v1#aftermath_health_regen" />
- 睡觉时鼓膜和雷击后遗恢复快 <V d="lightning/lightning_injury_v1#sleep" /> 倍。死亡后全部清空
- 在 <V d="lightning/lightning_injury_v1#afterimage_range" u="格" ue="blocks" /> 内看到落雷，眼里会留下几秒紫色残影，越近越久
- 其他生物也会被击中：侧闪扣最大生命值的 <V d="lightning/lightning_injury_v1#mob_side" pct="true" />，地电流扣 <V d="lightning/lightning_injury_v1#mob_ground" pct="true" />；四足动物受地电流的范围 ×<V d="lightning/lightning_injury_v1#animal_ground" />
- **爬行**：在陆地上趴着（双腿麻痹、钻 1 格高的缝、昏迷）时是真的在地上爬：麻痹时用两只手肘轮流把身体往前拉，腿拖在后面；钻缝时手肘和膝盖一起出力。伸手时几乎不动，拉的那一下最快。视角可以先转，身体跟在后面慢慢转过去。爬的时候手里的东西收起来，停下才能用。第一人称看出去，伸出去的手在画面下方，低头能看到两只手轮流撑地。水里游泳不变
- **身上湿着**：淋着雨或站在水里被击中，电流更多从皮肤表面流过：直击当场死亡的概率降到 <V d="lightning/lightning_injury_v1#direct.wet_death" pct="true" />，侧闪降到 <V d="lightning/lightning_injury_v1#side.wet_death" pct="true" />，但浅表烧伤多 <V d="lightning/lightning_injury_v1#wet_burns" /> 处
- **雷击烧伤**：电流进出身体的地方。直击 <V d="lightning/lightning_injury_v1#direct.burns" /> 处（头或手臂进，腿出），侧闪 <V d="lightning/lightning_injury_v1#side.burns" /> 处（靠近落点那侧的手臂或胸），落点 <V d="lightning/lightning_injury_v1#ground_burn_range" u="格" ue="blocks" /> 内的地电流 <V d="lightning/lightning_injury_v1#ground.burns" /> 处（腿）。<V d="wounds/wounds_v1#burn.heal_days.0" />–<V d="wounds/wounds_v1#burn.heal_days.1" u="天" ue="days" /> 自己好；没包扎的话，第 <V d="wounds/wounds_v1#burn.check_days" u="天" ue="days" /> 时有 <V d="wounds/wounds_v1#burn.infect_chance" pct="true" /> 的概率感染，用绷带包扎就不会。最多 <V d="wounds/wounds_v1#burn.max_burns" /> 处，不占抓伤的位置，背包身体图上能看到
- **金属烫伤**：直击和侧闪时，背着的枪（胸）、枪套里的手枪（右腿）、胸挂里的弹匣（腹）会被烧烫，各烫出一处更深的伤，每处多失去 <V d="lightning/lightning_injury_v1#metal_health" /> 点生命值。<V d="wounds/wounds_v1#burn.deep_heal_days.0" />–<V d="wounds/wounds_v1#burn.deep_heal_days.1" u="天" ue="days" /> 才好，没包扎时感染概率 <V d="wounds/wounds_v1#burn.deep_infect_chance" pct="true" />
- **手里的东西**：直击和侧闪时手猛地一松，主手的东西掉在 <V d="lightning/lightning_injury_v1#drop_range.0" />–<V d="lightning/lightning_injury_v1#drop_range.1" u="格" ue="blocks" /> 外
- **皮肤上的痕迹**：雷击烧伤是中间焦黑开裂、外面一圈湿红创面和翘起的白边、周围发红的小块，金属烫伤是烫它的东西的形状（背上一道斜条、右大腿外侧一块竖长方形、腹前一两个小竖条），边缘清楚，中间像皮革；先结深色的痂，再变成粉色或浅褐的疤，最后消失。直击和侧闪后，从电流进来的地方往肩、胸、背散开一片红纹（雷击纹）：几根主枝一分为二、再一分为二，越往外越细越密，最后像羽毛的细尖，<V d="lightning/lightning_injury_v1#lichtenberg_days" u="天" ue="days" /> 内淡掉，不疼，不影响任何数值
- **电池放电**：直击和侧闪时，身上每块能源电池和充电的工具各有 <V d="lightning/lightning_injury_v1#drain_chance" pct="true" /> 的概率电量清零，不会坏

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

满值 <V d="thirst/thirst_v1#max" />，站着不动每游戏小时下降 <V d="thirst/thirst_v1#base_per_game_hour" />（约 3 个游戏日喝空），每消耗 100 耐力额外下降 <V d="thirst/thirst_v1#exertion_per_100_stamina" />

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

饥饿沿用原版系统，AFL 目前还没有自己的食物；原版食物也已无法获得（<StatusTag s="test" />），新世界里暂时没有吃的。<StatusTag s="test" /> 站着不动也会慢慢饿：每游戏小时下降 <V d="health/health_v1#hunger.base_per_game_hour" />（先扣饱和度），约 3 个游戏日饿空，和口渴一样；跑、跳、挖、打会饿得更快。饥饿值 ≤ <V d="stamina/stamina_v1#low_food_level" /> 时耐力恢复 ×<V d="stamina/stamina_v1#regen_low_food" />；低于 <V d="health/health_v1#regen.min_food" /> 时无法自然恢复生命值；肠胃感染和冷 2 及以上会额外消耗饥饿值

<StatusTag s="planned" /> 食物腐败和储存
