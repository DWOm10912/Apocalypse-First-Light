# 世界时间 V1 设计稿（C 分支 `feature/underground-u2`）

**状态：**
- T0（审计和设计）完成，2026-10-24。
- T1 用户 2026-10-24 批准，代码写完：编译通过，离线检查通过（第 13 节）。
- T2 用户 2026-10-24 实机 PASS："太阳月亮走得平"（第 13.5 节）。
- 24 小时制接口和 `/afl time set HH:MM`：用户要求，为以后做手表；用户 2026-10-25 实机 PASS（第 14 节）。
- 第 8 节的问题已发给 B 和 Main。B 已回复，6 条都定了；Main 转来用户的决定（发电机小时表按游戏小时显示）。都在第 8.4 节。

**依据：**
- 用户 2026-10-24 的要求：一天 60 分钟。
- 本文第一稿（第 1–7 节方案）的修订要求，原文要点见第 1 节。

**本轮边界（用户原话）：**
- "本轮仅执行：现有代码审计、设计文档修订、AflTime 接口规划、时间依赖清单、风险评估、跨工作区接口需求整理。"
- 不改 B 的生存系统、B 的物理水体、Main 的车辆和电力、现有燃油数值、天气倍率、植物生长参数。
- "等我批准 T1 后，再正式开始实现。"

## 1. 已定的事（用户 2026-10-24）

**时长：**
- 现实 60 分钟一天，是原版的 3 倍。一天仍是 24 000 个时间单位。
- 白天和夜里同一个倍数。
- 一天多长放进服务器配置，默认 60；以后可以选 40、90、120。

**跟着新时钟走：** 太阳、月亮、日期、时间显示。

**现在不做：** 季节、按纬度变化的昼长、真实月相（29.5 天）、复杂日历、天文计算。

**三种时间：**

| 类型 | 是什么 | 包括 |
|---|---|---|
| A 世界时间 World Time | 太阳和日历的钟，60 分钟一天 | 太阳、月亮、日期、天气和气候、以后的季节、部分生态 |
| B 模拟时间 Simulation Time | 现实 tick，每秒 20 次，和一天多长无关 | 实体、物理、枪械、动画、车辆（含油耗）、机器、电力 FE/t、电池、流体流动 |
| C 玩法时间 Gameplay Time | 每个系统自己定速率和规则 | 饥饿、口渴、愈合、植物、疲劳、疾病 |

C 类这一轮只审计，不改数值。

**其他规则：**
- **睡觉：** 只改世界时间，不快进模拟。按日历结算的系统，各自的结算规则以后和 B、Main 一起定。
- **天气和水：** 降雨 mm/h 指游戏小时还是现实小时，要和 B 定。降雨、积水、蒸发、入渗、河流水位、卸载后补算都要定单位。水的账本不能受倍率影响。
- **不能变慢的：** `getGameTime()` 和实体 tick。

## 2. 一天的时长

60 分钟一天时，游戏 1 小时 = 3 000 tick = 现实 2 分 30 秒。

| 段 | 时间单位 | 原版（20 分钟一天） | V1（60 分钟一天） |
|---|---|---|---|
| 白天 | 0 – 12 000 | 10 分钟 | 30 分钟 |
| 黄昏 | 12 000 – 13 000 | 50 秒 | 2 分 30 秒 |
| 夜里 | 13 000 – 23 000 | 8 分 20 秒 | 25 分钟 |
| 黎明 | 23 000 – 24 000 | 50 秒 | 2 分 30 秒 |
| 月相一轮（8 天） | 192 000 | 2 小时 40 分 | 8 小时 |

以后可选的长度：

| 一天 | 每天现实 tick | 每 tick 世界时间 | 游戏 1 小时 |
|---|---|---|---|
| 40 分钟 | 48 000 | 1/2 | 1 分 40 秒 |
| **60 分钟（默认）** | **72 000** | **1/3** | **2 分 30 秒** |
| 90 分钟 | 108 000 | 2/9 | 3 分 45 秒 |
| 120 分钟 | 144 000 | 1/6 | 5 分钟 |

## 3. 原版现在怎么走（Forge 47.4.20 源码，已核对）

- **服务器：** `ServerLevel.tickTime()` 每 tick 做三件事：
  - `gameTime + 1`；
  - 跑计划事件；
  - `doDaylightCycle` 开着时 `setDayTime(dayTime + 1)`。

  只有主世界真正存时间，其他维度的 `DerivedLevelData.setDayTime` 什么也不做。
- **客户端：** `ClientLevel.tickTime()` 同样 `gameTime + 1`，规则开着时 `dayTime + 1`，在两次同步之间自己推进。
- **同步：** `MinecraftServer` 每 20 tick 给每个维度发一次 `ClientboundSetTimePacket(gameTime, dayTime, doDaylightCycle)`。客户端收到后直接覆盖两个时间。玩家进世界时也发一次。
- **睡觉：** `ServerLevel.tick` 里够人数睡着、`doDaylightCycle` 开着时：
  - 新时间 = (`dayTime` + 24 000) 向下取整到 24 000 的倍数，交给 Forge `SleepFinishedTimeEvent` 改；
  - 然后叫醒所有人；`doWeatherCycle` 开着且在下雨时清掉原版的雨；
  - 这一步在当 tick 的 `tickTime()` 之前。
- **指令：**
  - `/time set` 直接 `setDayTime(值)`，绝对值，所以 `/time set day` 会把天数归零；
  - `/time add` 是加；`/time query` 读 `dayTime`；
  - 都是世界时间单位，倍数改了照常能用。
- **太阳和月亮：** `Level.getTimeOfDay(partialTick)` 只读整数 `dayTime`，不插值；月相是 `dayTime / 24000 % 8`。
  - 原版每 tick 走 1 个单位（0.015°），V1 是每 3 tick 走 1 个单位，看不出跳。

## 4. 做法：只让世界时间走慢

只改 `tickTime()` 里 `dayTime + 1` 那一步：每 tick 改成"这一 tick 走几个单位"，由一个整数累加器给出。

- **不碰：** `gameTime`、计划事件、实体、区块随机刻、物理。
- **自动跟着走：** 所有读 `getDayTime()` 的东西都自动跟着变慢，太阳、月相、真实黑暗、天气预报、体温昼夜曲线、幸存者天数和新时钟对得上，不用改。

**累加器（定点，不会漂）：**

```
每 tick：acc += 24000
         units = acc / ticksPerDay      // 整数除法
         acc   = acc % ticksPerDay
         dayTime += units
```

- `ticksPerDay = 天长分钟 × 1200`，全是整数。
- 60 分钟时每 3 tick 正好走 1 个单位，跑多久都不差一个单位。
- 40、90、120 分钟一样是整数，可以接受任何整数分钟（建议 10–240）。

**各种情况：**
- **`doDaylightCycle` 关掉：** 不加，`acc` 冻住。
- **`/time set`、`/time add`、睡觉：** 直接改 `dayTime`，`acc` 清零，并发一次时间跳变事件（第 5 节）。
- **存档：** `acc` 存进主世界的 SavedData（`afl_world_time`），连同当时的天长。
  - 读档后继续，不丢那不到 1 个单位的零头。
  - 换了天长，按比例换算 `acc`。
  - 旧存档没有这份数据，从 0 开始。
  - `dayTime` 本身照旧是原版数字，不坏档。
- **客户端：**
  - 进世界和改配置时，服务器发一个 AFL 包（天长）；客户端的 `ClientLevel.tickTime()` 用同样的累加器推进；
  - 每秒的原版同步照旧覆盖 `dayTime`，误差最多 1 个单位（0.015°），看不出来。
  - 太阳插值（T2 再看要不要）：`getTimeOfDay` 在客户端用 `(acc + partialTick × 24000) / ticksPerDay` 补零头，和帧率无关。
- **网络协议版本：** 加一个包要升一次。

**为什么不改别的：**
- 只拦 `dayTime + 1` 一处，原版指令、规则、睡觉、同步都不用重写。
- 别的 mod 读 `getDayTime()` 得到的是一致的世界时间。
- 唯一冲突的是自己每 tick 改 `dayTime` 的时间类 mod，现在没有装。
- 每 tick 几次整数运算，没有性能成本。

## 5. AflTime 接口规划

**位置：** 包 `com.antaurora.apofirstlight.time`，由 C 做。B、Main 只读接口，不写死 24 000 和 1 000。

| 分组 | 接口 | 说明 |
|---|---|---|
| 配置 | `dayMinutes()` | 服务器配置的天长（分钟），默认 60；客户端用同步来的值 |
| | `ticksPerDay()` | `dayMinutes × 1200`，现实 tick |
| | `rate()` | 每 tick 走几个世界时间单位（60 分钟是 1/3），只用来显示和换算 |
| 世界时间 | `dayTime(level)` | 世界时间原值（就是 `getDayTime()`） |
| | `day(level)` | 第几天：`dayTime / 24000` |
| | `timeOfDay(level)` | 当天的单位：`dayTime % 24000` |
| | `dayProgress(level)` | 当天进度 0–1 |
| | `hourOfDay(level)` | 钟面小时 0–24，0 是午夜：`(timeOfDay / 1000 + 6) % 24` |
| 模拟时间 | `gameTime(level)` | 服务器 tick 计数（就是 `getGameTime()`，不变慢） |
| | `SIM_DT = 0.05` | 每 tick 的模拟秒数 |
| 换算 | `ticksPerGameHour()` | `ticksPerDay / 24`（60 分钟时 3 000） |
| | `realSecondsPerGameHour()` | 60 分钟时 150 |
| | `gameHoursToTicks(h)` / `ticksToGameHours(t)` | 游戏小时和现实 tick 互换 |
| | `perTick(perGameHour)` | "每游戏小时多少"换成"每 tick 多少"（降雨、蒸发用） |
| 事件 | `AflTimeJumpEvent(level, from, to, cause)` | 世界时间跳变：`SLEEP` / `COMMAND` / `OTHER`。只通知，不替别人快进 |
| | `AflTimeRateEvent(oldMinutes, newMinutes)` | 天长改了（配置重载） |

**要求：**
- **服务器说了算：** 客户端只拿天长做预测。
- **和帧率无关：** 换算全按 tick；跳变事件在服务器主线程发。
- **小数积累：** 调用方需要小数时，像 B 的 `rainCarry` 一样自己存零头；接口给整数 tick 或 double，不替调用方攒。
- **玩法时间：** `AflTime` 不定"一天要喝多少水"，只给换算。每个系统用世界时间还是现实 tick，由负责的工作区定（第 6、7 节）。

**T1 另外加的：**
- 服务器配置 `world_time.day_minutes`；
- 调试指令 `/afl time`：显示天长、`acc`、钟面时间、游戏 1 小时多少现实秒；
- 离线检查：累加器跑 10⁷ tick 的整除结果，加上换天长后的换算。

## 6. 时间依赖清单（三个工作区，2026-10-24 审计）

每个 24 000、1 000、1 200、72 000 都按它的意思判断，不机械替换。"要改"只是指明以后谁来改，这一轮都不改。

### 6.1 世界时间（自动跟着走，不用改）

| 系统名称 | 当前计时来源 | 预计使用的时间类型 | 是否需要修改 | 负责人 | 风险等级 |
|---|---|---|---|---|---|
| 太阳、月亮、月相（原版） | `dayTime` | 世界时间 | 否 | C | 低 |
| 真实黑暗昼夜和月光 `TrueDarkness` | `getTimeOfDay(partialTick)` | 世界时间 | 否 | C | 低 |
| Aureveil 光影的太阳角 | 客户端世界时间 | 世界时间 | 否 | C | 低 |
| 光控灯：`BuildingLightBlock`、`LightPoleBaseBlockEntity`、`PriceSignBlockEntity`、`BuildingSupply`（`isDay()`） | 世界时间 | 世界时间 | 否 | Main | 低 |
| 体温昼夜曲线 `PlayerTemperature.dayCurve` | `dayTime % 24000` | 世界时间 | 否 | B | 低 |
| 区域天气预报 `RegionalWeather`（`HOUR = 1000`、`DAY = 24000`）、`AflWeather`、`WeatherCommands` | `dayTime` | 世界时间 | 否 | B | 低 |
| 幸存者天数 `SurvivorRecord.DAY_TICKS`、开局 / 死亡时间、`SurvivorCommands`、`SurvivorRagdolls` | `dayTime` 差 | 世界时间 | 否 | B | 低 |
| 原版村民作息、怪物刷新、床能不能睡 | `dayTime` | 世界时间 | 否 | 原版 | 低 |

### 6.2 模拟时间（照旧按现实 tick，不用改）

| 系统名称 | 当前计时来源 | 预计使用的时间类型 | 是否需要修改 | 负责人 | 风险等级 |
|---|---|---|---|---|---|
| 车辆油耗 `Drivetrain`（`/ 3600 × dt`，按现实秒） | 现实秒 | 模拟时间 | 否 | Main | 低 |
| 应急灯 `EmergencyLightBlockEntity` CAPACITY = 12 000 | tick | 模拟时间 | 否 | Main | 低 |
| 应急收音机 `EmergencyRadio` CAPACITY = 24 000（注释"满电 20 分钟"，指现实） | tick | 模拟时间 | 否 | Main | 低 |
| 楼宇照明供电 `BuildingLights` FE_PER_TICK、9 V 电池、盖革计数器耗电 | tick | 模拟时间 | 否 | Main | 低 |
| 箱式炉 `BoxStove` LOG_TICKS = 2 400 等燃烧时长 | tick | 模拟时间 | 否 | Main | 低 |
| 便携发电机预热 `WARM_TICKS = 1200`、拉绳、起动 | tick | 模拟时间 | 否 | Main | 低 |
| B 液体引擎 `LiquidEngine`（DT = 0.05 s，流动和扩散） | tick | 模拟时间 | 否（卸载补算待定，第 8 节） | B | 中 |
| 耐力 `PlayerStamina`（每秒速率 / 20） | tick | 模拟时间 | 否 | B | 低 |
| 体温变化 `PlayerTemperature`（dt = 现实秒） | 现实秒 | 模拟时间 | 否 | B | 低 |
| 雨的贴图滚动 `RainRenderer`（`getGameTime() % 24000`） | tick | 模拟时间 | 否 | C | 低 |
| 阴影遮挡预算 `AflShadowOcclusion`、焦痕 `Scorches`、油渍 `FuelStainIndex`、修路 `RoadConstructionConfig`、`RegionalWeather` 的 ±12 000 坐标 | 和一天无关 | 模拟时间 | 否 | 各自 | 低 |
| 伐木账本保留 `FellingLedger.KEEP_SETTLED = 24000` | tick | 模拟时间 | 否 | C / B | 低 |
| 动作、动画、换弹、凿击、挖土、声音 | tick | 模拟时间 | 否 | 各自 | 低 |

### 6.3 要负责方定的（以后改，这一轮不动）

| 系统名称 | 当前计时来源 | 预计使用的时间类型 | 是否需要修改 | 负责人 | 风险等级 |
|---|---|---|---|---|---|
| 睡觉回血和骨折愈合 `PlayerHealth.sleepFinished`：跳过的单位当 tick 用，`Fractures.step(player, skipped, true)`，回血 `skipped / 1200 × sleepPerMinute` | 世界时间单位当成 tick | 玩法时间 | 是：按新的睡觉结算规则改 | B | 高 |
| 伤口、骨折、雷击伤 `WoundConfig.dayTicks = 24000`（"几天"乘 24 000 当 tick） | tick | 玩法时间 | 是 | B | 中 |
| 水体降雨 `LiquidUnits.ML_PER_TICK_PER_MM_H = 1.0`（注释"1 mm/h 在 1 m² 上是每游戏小时 1 L，即 1 000 tick"） | 默认游戏 1 小时 = 1 000 tick | 世界时间 → 模拟时间的换算 | 是：60 分钟一天时差 3 倍 | B | 高 |
| 雷击频率 `AflWeather.tick`（每现实分钟 perMinute / 1200） | 现实分钟 | 待定 | 是 | B | 中 |
| 口渴 `PlayerThirst`（`basePerHour / 72000`，现实小时） | 现实小时 | 玩法时间 | 待定 | B | 中 |
| 饥饿（原版食物消耗 × `hunger_rate`，按动作） | 动作 / tick | 玩法时间 | 待定 | B | 低 |
| 清醒和休息回血 `awakePerMinute` / `restPerMinute`（现实分钟） | 现实分钟 | 玩法时间 | 待定 | B | 低 |
| 踩倒的植物恢复 `MeshPlantBlock` TROD_TICKS = 24000、WHEEL_TICKS = 48000、RISE_TICKS = 12000 | tick | 玩法时间 | 待定 | B | 低 |
| 发电机油耗和运转小时表 `DieselGeneratorBlockEntity` / `PortableDieselGeneratorBlockEntity`（`HOUR_TICKS = 1000`，注释"每游戏小时多少升"，小时表"游戏小时"） | 默认游戏 1 小时 = 1 000 tick | 模拟时间（用户定的分类） | 待定：只是小时表的显示 | Main | 中 |

**没有的系统：** 疲劳、疾病（只有口渴里的"生病"倍数）、食物腐烂、作物和树苗生长（没有用随机刻的 AFL 植物；植被只在生成世界时摆）。以后做的时候按第 7 节分类。

**原版随机刻：** 速度不变。剩下的原版作物、树苗如果还拿得到，按游戏天算会长慢 3 倍。原版作物已经在退役，影响小。

## 7. 玩法时间的分法（建议，由 B 定数值）

| 种类 | 建议 | 理由 |
|---|---|---|
| 一个动作里的事（耐力、体温变化、换弹） | 模拟时间 | 和一天多长无关 |
| 按"天"说的事（骨折几天好、伤口几天愈合、以后的食物腐烂、植物长回来） | 世界时间：用 `AflTime.gameHoursToTicks` | 和太阳、睡觉、日历对得上 |
| 按"小时"说的生理（口渴、饥饿） | B 定：按游戏小时（一天喝几次水和昼夜对上）或按现实小时（现实里玩一小时喝几次） | 两种都说得通，影响一局的节奏 |

**现在的口渴：** `basePerHour = 100` 按现实小时扣，原版一天（20 分钟）扣 33，60 分钟一天时一天扣 100。

## 8. 跨工作区要定的接口（T3 发给 B 和 Main）

### 8.1 给 B：天气和水的时间单位

1. **降雨强度 mm/h 的"小时"：** 建议指游戏小时（世界时间），和天气预报的小时一致。
   - 一场雨下几个游戏小时、下多少毫米，和天长无关。
   - 换成每 tick：`AflTime.perTick(mm_h × 1000 mL)`，60 分钟时 1/3 mL/tick，零头存在现有的 `rainCarry`。
   - 如果照现在每 tick 1 mL，60 分钟一天时每场雨的总量是 3 倍。
2. **积水、蒸发、入渗、河流水位：**
   - 建议速率都按游戏小时（世界时间）定，换成每 tick 用同一个接口；
   - 流动和扩散照旧按模拟时间（DT = 0.05 s）。
3. **账本不受倍率影响：**
   - 进出的水量都是整 mL 加零头，零头存档；
   - 换天长只改每 tick 的换算，不改总量。
4. **卸载后补算：** 区块卸载期间世界时间走了多少（`dayTime` 差，包括睡觉跳过），降雨和蒸发按天气模型在这段世界时间里的积分一次结算，不按 tick 数，也不跑物理。B 定要不要做、什么时候做。
5. **睡觉跳过：**
   - 跳过的几小时雨和蒸发按第 4 条一次结算，只改账本，不快进流动；
   - 原版睡醒清雨（`doWeatherCycle`）对 AFL 的区域天气有没有影响，请 B 确认。

### 8.2 给 B：生存系统

- 睡觉回血和骨折（第 6.3 节第 1 行）要定新规则：
  - 现在睡一夜按"跳过的单位数"当 tick 愈合，等于一个原版夜晚的 tick 数；
  - 60 分钟一天时，醒着熬一夜是 3 倍的 tick，睡觉反而愈合得少；
  - 建议改成按跳过的游戏小时结算（`AflTime.gameHoursToTicks`），系数 B 定。
- `WoundConfig.dayTicks`、口渴、雷击频率、踩倒的植物：按第 7 节定用哪种时间，接口用 `AflTime`。
- 订阅 `AflTimeJumpEvent`，代替现在 `SleepFinishedTimeEvent` 里自己算差值；`/time add` 也会发。

### 8.3 给 Main：车辆和电力

- 车辆、发电机油耗、电池、FE/t 都是模拟时间。数值不改，"按现实 tick"写进各自注释。
- 发电机注释写的是"每游戏小时多少升（1 000 tick）"，小时表也按 1 000 tick 记一小时。60 分钟一天时，天上过 1 小时，小时表走 3 小时。
  - 要不要让小时表改按 `AflTime.ticksPerGameHour()` 显示（只改显示，不改油耗），由 Main 定。

### 8.4 B 和 Main 的回复（2026-10-24）

B 只写了文档 `docs/gameplay/world_time_b_units_v1.md`（B 工作区 `AFL-vanilla-rework`），代码不改。等 T1 合并、用户放开限制后再接，数值放在 T4。

| 条 | B 的决定 |
|---|---|
| 降雨 mm/h | 同意按游戏小时。T1 合并后 `LiquidUnits.ML_PER_TICK_PER_MM_H` 换成 `AflTime.perTick(mm_h × 1000)`，零头进 `rainCarry` |
| 积水、蒸发、入渗、天然水位 | 同意按游戏小时；流动、扩散、找平按模拟时间；账本不受倍率影响 |
| 卸载后补算 | 同意，放在水体第 3 包（汇水 + 入渗）：区块加载时按卸载期间走过的 `dayTime` 对天气模型积分，一次记账，不跑物理 |
| 睡觉跳过 | 同意只改世界时间。原版睡醒清雨对 `AflWeather` 没影响：原版全局天气一直被 `ServerLevelWeatherMixin` 关着，各地天气由 `RegionalWeather` 按 `dayTime` 算。T1 合并后进游戏看一次 |
| 睡觉愈合 | 改成按跳过的游戏小时算：`Fractures.step(player, AflTime.gameHoursToTicks(skipped / 1000.0), true)`，回血同样换算，系数和醒着时一样，外加原来的睡着加成；以后订阅 `AflTimeJumpEvent`，只认 `SLEEP` |
| `WoundConfig.dayTicks` | 世界时间（`AflTime.ticksPerDay()`） |
| 口渴、饥饿 | 按游戏小时（每天要喝多少水是"每天"的量） |
| 雷击频率 | 保持模拟时间（打雷频率是看得到的物理量）；一场雷暴的总数会是 3 倍，T4 再看 |
| `MeshPlantBlock` 回弹 | 保持模拟时间（物理回弹） |

**B 另外提醒：** 雨区漂移和锋面按 `dayTime` 走，现实里看起来慢 3 倍（风速是 m/s）。要不要改成按模拟时间漂，放进 T4 让用户看。

**Main 转来用户的决定：** 原话"柴油发电机小时计按游戏内小时显示，使用 AflTime.ticksPerGameHour()；如果底层需要，可以保留真实 tick 统计，但对玩家展示按游戏时间走。"
- `DieselGeneratorBlockEntity` / `PortableDieselGeneratorBlockEntity` 给玩家看的小时表用 `AflTime.ticksPerGameHour()` 换算；
- 存档里的统计可以照旧按现实 tick，存档字段不改；
- 油耗不变（模拟时间）；
- Main 在 C 合并后改自己的文件。

### 8.5 合并时间

`AflTime` 在 C 分支上做。B、Main 要等 C 合并进 master 才能用。

- C 的 R0 合并在等 Main 的 Mesh 人物。
- T1 可以先做，合并另找时间。B 的水体先按第 8.1 节的单位写，接口名照第 5 节。

## 9. 兼容

| 场景 | 处理 |
|---|---|
| Forge 1.20.1 | 只拦 `ServerLevel.tickTime()` 和 `ClientLevel.tickTime()` 里 `setDayTime(dayTime + 1)` 那一处，其余原版代码不动 |
| 单人 | 服务器配置在存档的 `serverconfig` 里，每个存档自己定天长 |
| 专用服务器 | 同上；客户端进服时拿天长 |
| 读档 | `acc` 和天长在 SavedData；旧存档从 0 开始 |
| 多人同步 | 原版每秒同步照旧，AFL 只多发天长；客户端误差最多 1 个单位 |
| 睡觉 | 原版规则（`playersSleepingPercentage`）照旧，跳到早上；`acc` 清零，发 `SLEEP` 跳变事件 |
| 指令 | `/time set` / `add` / `query` 照常；清零 `acc`，发 `COMMAND` 跳变事件 |
| `doDaylightCycle` | 关着不走；客户端从原版包里的标志知道 |
| 非整数倍数 | 整数累加器，没有浮点误差（第 4 节） |
| 太阳月亮插值 | 默认不插（每单位 0.015°）；T2 实机看，需要时客户端按 `acc` 和 `partialTick` 补 |
| 别的 mod | 读 `getDayTime()` 的都一致；会自己改 `dayTime` 的时间类 mod 会冲突，现在没有装 |
| 性能 | 每 tick 几次整数运算 |

## 10. 风险

| 风险 | 等级 | 说明和对策 |
|---|---|---|
| 睡觉愈合的比例变了 | 高 | 第 8.2 节；B 改之前，60 分钟一天时睡一夜愈合得比熬一夜少 |
| 降雨总量差 3 倍 | 高 | 第 8.1 节；B 的水体还没上线，单位先定好就不会出事 |
| 伤口、骨折"几天"变成现实时长 | 中 | `WoundConfig.dayTicks` 是现实 tick；60 分钟一天时，3 天的骨折一个游戏天就好了 |
| 雷击按现实分钟 | 中 | 一场雷雨的游戏小时数不变，现实时长变成 3 倍，雷击数也是 3 倍 |
| 发电机小时表 | 中 | 第 8.3 节，只影响显示 |
| 夜里 25 分钟 | 中 | 真实黑暗下更难熬，要实机感受；V1 白天和夜里同一个倍数（用户定的） |
| `/time set day` 把天数归零 | 低 | 原版就这样；天气预报和幸存者天数会跟着跳。T2 测指令时记下来 |
| 写死成"N 秒"、其实指几小时的地方搜不出来 | 低 | 本次只能搜到数字；各工作区接入时自己再查 |
| 原版失眠（幻翼）按 tick 统计 | 低 | 3 个游戏天的门槛变成约 1 个游戏天；看幻翼是否还开着 |
| 网络协议版本 | 低 | 加包时升一次，记在交接里 |

## 11. 分阶段（每段单独验收）

| 阶段 | 做什么 | 谁验收 |
|---|---|---|
| **T0** | 审计、本文、接口规划、依赖清单、风险、跨工作区需求（本轮，完成） | 用户批准 T1 |
| T1 | `AflTime`、服务器配置、两处混入、累加器和 SavedData、客户端同步包、跳变事件、`/afl time`、离线检查 | 编译和离线检查 |
| T2 | 实机：太阳和月亮走得平、一天 60 分钟、日期、睡觉、`/time`、`doDaylightCycle`、存档读档、专用服务器 | 用户实机 |
| T3 | 把第 8 节发给 B 和 Main，B、Main 各改各的 | 各工作区 |
| T4 | 调玩法时间（第 7 节的数值） | B，用户定 |

## 12. 第一稿里换掉的内容

第一稿（同一文件，2026-10-24 早些时候）的第 4 节把油耗、电池、植物都放在"跟游戏钟走"。用户的修订把车辆、机器、电力、电池归到模拟时间，植物和生理归到玩法时间，本文按修订写。第一稿第 7 节的四个问题，用户已经定了：60 分钟、白天和夜里同一个倍数、三种时间分法、放进服务器配置。

## 13. T1 记录（2026-10-24，用户批准后）

### 13.1 代码（包 `time`，C 分支，没提交）

| 文件 | 做什么 |
|---|---|
| `time/WorldClock.java` | 定点累加器（纯 Java）：`step()`、`reset()`、`set(minutes, acc)`、`changeMinutes(m)`；天长 10–240 分钟，默认 60 |
| `time/AflTimeConfig.java` | 服务器配置 `apocalypse_firstlight-time.toml`，`[world_time] day_minutes` |
| `time/AflTimeData.java` | 主世界 SavedData `apocalypse_firstlight_world_time`：`Minutes`、`Acc` |
| `time/AflTime.java` | 第 5 节的接口，名字和发给 B 的一致 |
| `time/AflTimeJumpEvent.java` / `AflTimeRateEvent.java` | Forge 总线事件 |
| `time/WorldTimeServer.java` | 服务器时钟：推进、跳变检测、读档、登录发包、配置重载、`/afl time` |
| `time/WorldTimeClientState.java` / `WorldTimePacket.java` / `client/WorldTimeClientEvents.java` | 客户端时钟、同步包、退出世界时恢复默认 |
| `mixin/WorldTimeServerLevelMixin.java` | `ServerLevel.tickTime`（`m_8809_`）里 `setDayTime`（`m_8615_`）的参数；`setDayTime` 前后各一个注入，看跳变 |
| `mixin/client/WorldTimeClientLevelMixin.java` | `ClientLevel.tickTime`（`m_104826_`）里 `setDayTime`（`m_104746_`）的参数 |
| `network/AflNetwork.java` | 注册 `WorldTimePacket`；协议 72 → 73 |

### 13.2 行为

- **推进：** 只有主世界推进累加器，其他维度原样。`gameTime`、计划事件、实体都不动。
- **跳变：** 主世界的 `setDayTime` 不是自己这一步调的，就算跳变：时间真的变了才清零 `acc`，在设置完以后发 `AflTimeJumpEvent`。
- **原因：**
  - `SleepFinishedTimeEvent`（最低优先级）记 `SLEEP`；
  - `CommandEvent` 里匹配 `time set|add`（包括 `execute … run time …`）记 `COMMAND`；
  - 其余是 `OTHER`；
  - 记下的原因每 tick 清掉。
- **读档：** 服务器启动后读 SavedData。存的天长和配置不一样时，按比例换算 `acc`，并发 `AflTimeRateEvent`。
- **配置重载：** 服务器运行时保存配置文件（Forge 监视 `serverconfig`），在主线程换天长，发给所有玩家，并发事件。
- **客户端：** 进世界时拿天长，在两次原版同步之间按同一个累加器推进；退出世界恢复 60。
- **`/afl time`（权限 2）：** 显示第几天、钟面时刻、`dayTime`、天长、游戏 1 小时多少秒、累加器。

### 13.3 检查

- `gradlew compileJava` 通过，只有原有的警告。
- refmap 里四个目标都映射到了原版源码里的 SRG 名（`m_8809_`、`m_8615_`、`m_104826_`、`m_104746_`）。
- `gradlew worldClockCheck`（`src/test/.../time/WorldClockCheck.java`）通过：
  - 10、20、40、60、90、120、240 分钟各跑 10⁷ tick，走过的单位数正好是 ⌊tick × 24 000 / 每天 tick⌋，一天的 tick 正好 24 000 单位；
  - 20 分钟每 tick 走 1 单位；60 分钟只有每第 3 tick 走 1 单位；
  - 换天长保留零头、不越界；存档里错误的累加器从 0 开始。
- wiki `world/weather.md`（中英同行）加了"昼夜"一节，标"测试中"；`check-wiki` 没有新错误（只剩原有的截图缺失）。

### 13.4 T2 实机要看的

1. 太阳和月亮走得平，不跳、不倒退；Aureveil 和不开光影都看。
2. 用 `/afl time` 看：一天约 60 分钟，游戏 1 小时约 150 秒。
3. 睡觉跳到早上，接着正常走；`/time set`、`/time add`、`/time query` 照常。
4. `/gamerule doDaylightCycle false` 停住，打开后接着走。
5. 存档、退出、读档后接着走，`/afl time` 的累加器接上。
6. 改 `day_minutes` 保存，`/afl time` 马上变；需要时再试专用服务器。
7. 夜里 25 分钟在真实黑暗下的感受。

### 13.5 T2 实机（2026-10-24）

- 用户结论：PASS，"太阳月亮走得平"。
- 日志里两次 `/afl time` 相隔 8.5 秒（170 tick），世界时间从 57 + 0/72 000 走到 113 + 48 000/72 000，正好 56.67 个单位，和 1/3 的倍数对上。
- 连续 23 次 `/time add 1000` 照常，没有报错。
- 睡觉、`doDaylightCycle`、读档、改 `day_minutes` 这次没在日志里测到，留到用的时候看。

## 14. 24 小时制（2026-10-24，用户要求，为以后做手表）

用户："我想让游戏里面的时间改成 24 小时制……这样后面做手表就容易的多"。

**内部不改：** 原版时间单位不变（0 是早上 6 点），太阳、床、怪物、`/time`、存档和别的 mod 都照旧。24 小时制只是读法。

**接口：**

| 接口 | 说明 |
|---|---|
| `ClockTime`（纯 Java 记录） | `date`（午夜换日）、`secondOfDay`（0–86 400，带小数）；`hour()`、`minute()`、`second()`、`hours()`（时针用）、`hhmm()`、`hhmmss()` |
| `ClockTime.at(dayTime, fraction)` | 世界时间加上不到一个单位的零头 → 时刻 |
| `ClockTime.timeOfDayAt(h, m)` / `dayTimeAt(date, h, m)` | 24 小时制 → 世界时间单位：钟面第一次读到这一分钟的那个单位 |
| `AflTime.date(level)` | 午夜换日的日期；原版的 `AflTime.day` 在 06:00 换，不改 |
| `AflTime.clock(level)` | 到整个单位（3.6 秒）的时刻 |
| `AflTime.clock(level, partialTick)` | 平滑时刻，给秒针用：客户端加累加器零头和这一帧的 partial tick，只往前走；服务器只加零头；昼夜循环关着时不加 |

**换算：**
- 1 个单位 = 钟面 3.6 秒；1 分钟 ≈ 16.7 个单位。
- 60 分钟一天时，钟面 1 分钟 = 现实 2.5 秒，1 秒 ≈ 0.83 tick。

**指令：**
- `/afl time set HH:MM`（权限 2）：把所有维度设成今天（午夜换日的日期）这个时刻，可能往回走，和原版 `/time set` 一样。会发 `COMMAND` 跳变事件。
- `/afl time`：多显示午夜换日的日期和 `HH:MM:SS`，原版天数也留着。

**检查：** `worldClockCheck` 加了几项，全部通过：
- 定点：0 → 06:00:00，6 000 → 12:00，12 000 → 18:00，17 999 → 23:59:56，18 000 → 第 1 天 00:00，-6 001 → 第 -1 天 23:59:56；
- 零头跨过下一个单位时进到下一天；
- 1 440 个分钟都能来回换算；
- 秒针跨单位、跨日期不倒退。

**实机（2026-10-25）：** 用户结论 PASS。日志里的读数和换算一致：
- `/afl time` 显示 `date 1, 05:40:57`（时间 23 682）；
- `set 14:30` → 32 500，`set 23:59` → 41 984；
- 约 16 秒后 `set 06:00` 落在第 2 天（48 000），说明日期在午夜换了。
