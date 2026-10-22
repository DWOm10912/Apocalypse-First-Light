# 耳鸣 V2

**状态：已实现，用户 2026-10-21 实机 PASS（D 分支 `feature/tinnitus-v2`，从 master `d77f53a0` 开出，已在分支提交，等 Main 合并）。** 用户 2026-10-21 批准第 6 节全部按推荐（「4份音效做好了，接下来接入游戏，按照你的设计去做」），并要求删掉旧贴图（「之前codex做的layout记得删，没啥用」）。实际做法见第 7 节；第 2 节是改之前的样子。网络协议 60 → 61，合并时要告诉 Main。

## 1. 用户的要求（2026-10-21）

- 「现在感觉有点简陋，并且 codex 之前还给 layout 层画了一个贴图，不知道是啥意思」
- 「我想让耳鸣变成负面状态，如果玩家做了某些事情，就会偶尔有声音，比如说在爆炸旁边，持续很久开大口径的枪械，并且不戴耳罩」

## 2. 现在的耳鸣（读代码，2026-10-21）

| 部分 | 代码 | 做了什么 |
|---|---|---|
| 爆炸 | `explosion/ExplosionTinnitusEvents`、`ExplosionTinnitusProfile` | 每次爆炸，对同一维度的玩家按距离算强度：`(1 − 距离 / (爆炸半径 × 3))²`，到 0.15 就响 |
| 枪声 | `tinnitus/GunshotExposureTracker`、`GunshotExposureAccumulator`、`GunshotTinnitusProfile` | 只有枪的 JSON 里 `noise.tinnitus: true` 的枪才算（BR51、HR55、Silverwood 12、Blackridge .50；P9、Cat 不算）。每发按枪的噪声半径和距离（16 格内）累加，攒够了响一次；累积量大约两分钟内就掉光 |
| 播放 | `client/ExplosionTinnitusClientState`、`ExplosionTinnitusEnvelope`、`ExplosionTinnitusSound` | 两种来源都发同一个包，客户端放同一段 10 s 的 `explosion_tinnitus.ogg`，按强度定音量和长度（2–8 s 后淡出） |
| 贴图 | `client/ExplosionTinnitusOverlay`、`textures/gui/effects/explosion_tinnitus_overlay.png` | 见下 |
| 耳罩 | `SimpleHearingProtectionItem` | 世界声音 ×0.5，耳鸣强度 ×0.5 |
| 特质 | `survivor_traits/hard_of_hearing.json` | 耳鸣 ×0.7，听力 ×0.8 |

**那张贴图是什么**：一张 256 × 256 的灰色暗角，中间全透明，越往四周越灰（边上大约 50% 不透明）。耳鸣一响就把它盖在整个屏幕上，最高再乘 0.6，跟着耳鸣一起淡出，看起来就是"屏幕四周发灰"。它想表达的是爆炸后的发懵，但耳鸣本身是听觉症状，跟屏幕变灰没有关系，而且枪声耳鸣也会触发它。

**为什么显得简陋**：

- 只是一声"嗡"，几秒就没了，没有状态，HUD 上看不到，下线就忘。
- 对玩法没有任何影响：响完就和没事一样，所以耳罩戴不戴都无所谓。
- P9 这种手枪完全不算；大口径连开几十发和开几发，差别只在多响几次。
- 所有情况都是同一段声音。

## 3. 新的设计（推荐）

### 3.1 一句话

耳朵会积累"听力负荷"。枪声、爆炸、落雷都会往里加，越响、越近、越密，加得越多；耳罩能挡掉大部分。负荷够了就得到负面状态"耳鸣"（轻 / 中 / 重三档），状态期间耳边**时不时**响起耳鸣，档越高越频繁、越响，周围的声音也会变小。负荷随时间慢慢消退（睡觉更快），状态跟着降档直到消失。

另外，近距离爆炸和落雷当场"震耳"：一声尖锐的耳鸣，周围的声音一下子几乎听不见，十来秒内恢复。

### 3.2 每次巨响加多少（声音剂量）

按真实的听力保护规则：声音每大 3 分贝，对耳朵的伤害翻倍。每把枪在 JSON 里写开枪人耳边的声压（`noise.ear_db`，替换现在的 `noise.tinnitus` 开关），一发的剂量是 `10^((ear_db − 170) / 10)`，也就是 170 分贝的一发算 1。

| 枪 | 口径 | `ear_db`（推荐，参考公开的实测量级） | 一发的剂量 | 攒到轻度耳鸣（10）大约要连开 |
|---|---|---|---|---|
| HR55 | 12.7×55 | 170 | 1.0 | 10 发 |
| BR51 | 7.62×51 | 167 | 0.5 | 20 发 |
| Blackridge .50 | .50 AE | 165 | 0.32 | 31 发 |
| P9-01 | 9×19 | 159 | 0.08 | 125 发 |
| Silverwood 12 | 12 号霰弹 | 158 | 0.06 | 160 发 |
| Cat | 7.62×51，整体消音 | 134 | ≈0 | 不会 |

- **消音器**：再减 25 分贝（一发剂量 ×0.003），基本不伤耳朵。
- **别人开枪**：按距离平方衰减（开枪人自己算 1 格；4 格外剩 1/16），32 格外不算。
- **室内**（头顶看不到天）：×2，回声让耳朵受得更多。
- **爆炸**：`500 × (爆炸半径 / 4)² / max(1, 距离)²`。TNT（半径 4）在 2 格是 125（重度），4 格 31（中度），6 格 14（轻度），8 格 8（不到轻度，但已经积累了）。
- **落雷**：按半径 4 的爆炸算（雷电 V1 已有落点）。
- **耳罩**：所有剂量乘耳罩的系数。简易耳罩推荐 ×0.1（挡掉 10 分贝；真实的工业耳罩能挡 20–30 分贝，简易的打个折扣）。现在是 ×0.5，太弱，戴不戴区别不大。
- **特质**：重听（`hard_of_hearing`）照旧 ×0.7。

### 3.3 三档状态

负荷 E（数值和阈值都在新的 `data/apocalypse_firstlight/hearing/tinnitus_v2.json`）：

| 档 | E | 耳鸣多久响一次 | 一次响多久 | 周围的声音 |
|---|---|---|---|---|
| 轻度耳鸣 | ≥ 10 | 90–180 s | 3–5 s，轻 | 不变 |
| 耳鸣 | ≥ 30 | 40–90 s | 5–8 s | ×0.85 |
| 严重耳鸣 | ≥ 60 | 15–40 s | 6–10 s，响 | ×0.7 |

- 刚进入新的一档时马上响一次，玩家知道发生了什么。
- 每次随机在左耳、右耳或两耳响（声音放在头的一侧）。
- "周围的声音变小"走现有的听力倍率（`HearingProtectionAudio#listenerMultiplier`），和耳罩、听力属性相乘；丧尸靠声音找人，听不清它们就是真正的代价。
- **震耳**：一次事件的剂量 ≥ 3（近距离爆炸、身边落雷）时，当场放一声 10 s 的尖锐耳鸣（现在那段 `explosion_tinnitus.ogg`），周围的声音降到 ×0.3，6–10 s 内回到所在档的值。

### 3.4 恢复

- E 按半衰期消退：推荐半衰期 6600 tick（游戏里约 6.6 小时，现实约 5.5 分钟）；睡觉 ×3。
- 举例：TNT 在 4 格炸（31，中度）→ 约 9 分钟后掉到轻度以下；在 2 格炸（125，重度）→ 大约一个游戏日才完全好。BR51 连开 30 发（15，轻度）→ 约 3 分钟后消失。
- 存档（玩家持久数据 `afl_tinnitus`），死亡清空；下线不消退（和伤口、骨折一样只在线时计时）。
- 不做永久听力损伤（健康 V2 设计稿第 7.5 节说过只做小幅、可选；V2 先不做）。

### 3.5 界面

- 状态徽章：负面（BAD 色），新的耳朵图标，三档名字"轻度耳鸣 / 耳鸣 / 严重耳鸣"，效果一行："偶尔耳鸣" / "耳鸣，周围的声音变小" / "耳鸣不断，周围的声音明显变小"；旁边显示大概还剩多久。
- 人物页状态面板的说明：哪些会伤耳朵、戴耳罩、睡觉恢复更快。
- wiki 状态效果页、装备页（耳罩）中英文同步。

### 3.6 那张贴图

推荐**删掉**（`ExplosionTinnitusOverlay` 和贴图）：耳鸣只做听觉。爆炸后的发懵、视物模糊以后放到"脑震荡"（健康 V2 设计稿），用伤口视觉已经有的模糊做，不再用这种灰边。

## 4. 声音（需要你录）

按"一条录音切几段"的老规矩，用 Firefly 录，不用合成：

| 用途 | 内容 | 长度 |
|---|---|---|
| 阵发耳鸣（4 个变体） | 耳朵里的高频耳鸣：稳定的尖细高音、略带起伏的高音、更高更细的、偏低带一点沙沙声的；慢慢出现、慢慢消失 | 每段 4–10 s |
| 震耳 | 现在的 `explosion_tinnitus.ogg`（10 s）保留；如果你想换，录一段"爆炸后耳朵嗡的一声，很尖，然后慢慢变弱" | 10 s |

## 5. 实现要点（批准后）

- 服务端新建 `hearing/`：`TinnitusConfig`（读 `tinnitus_v2.json`）、`HearingLoad`（每个玩家的 E、存档、消退、档位）、`HearingExposure`（枪声、爆炸、落雷三个入口，算剂量，乘耳罩、特质、室内）。
- 替换：`tinnitus/` 三个类、`ExplosionTinnitusEvents` / `Profile` 改成调 `HearingExposure`；枪 JSON 的 `noise.tinnitus` 换成 `noise.ear_db`，`NativeGunShot` 改传它和消音器。
- 包：`TinnitusState(E, 档)` 给本人（变化时和每 5 s）、`TinnitusJolt(强度)` 给本人。旧的 `ExplosionTinnitusS2CPacket` 删掉，协议号合并时加 1。
- 客户端：`TinnitusClient` 按档随机安排阵发、选变体和左右耳、算听力倍率（接进 `HearingProtectionAudio`），震耳那 10 s 的曲线；删掉 `ExplosionTinnitusOverlay`、`ExplosionTinnitusEnvelope`，状态徽章加耳鸣。
- 调试命令：`/afl hearing set <E>`、`/afl hearing clear`、`/afl hearing jolt <强度>`。
- 旧的 dev 测试（`GunshotTinnitusGameTests`、`ExplosionTinnitusGameTests`、`TinnitusTimelineTest` 等）跟着删掉或改写。
- 文档：本文、`simple_hearing_protection_v1.md`、`survival_health_v2_design.md` 第 1.8 节、枪械框架文档里的 `noise.tinnitus`；wiki 状态效果页和装备页中英文；02 制作清单。

## 6. 用户的决定（2026-10-21：全部按推荐）

1. 整体思路（第 3.1 节）：累积负荷 → 三档负面状态 → 状态期间阵发耳鸣，加上近距离爆炸的震耳。
2. 第 3.2 节每把枪的分贝和剂量，尤其是：P9 和霰弹枪也算，但要开一百多发才会轻度耳鸣，可以吗？
3. 简易耳罩从 ×0.5 改成 ×0.1。
4. 周围声音变小（第 3.3 节）：只把音量调小（推荐，简单稳），还是再做"闷住"的感觉（给所有声音加低通滤波，更真实，但要动声音底层，可能和别的声音模组冲突）。
5. 删掉那张灰色暗角贴图。
6. 阵发耳鸣的 4 段录音由你用 Firefly 录。

## 7. 实现（2026-10-21）

数值全部在 `src/main/resources/data/apocalypse_firstlight/hearing/tinnitus_v2.json`（`hearing/TinnitusConfig.java` 的默认值相同；客户端用同一个对象，单人是集成服务器读到的值，连专用服务器时是默认值）。

**服务端**（`com.antaurora.apofirstlight.hearing`）

- `HearingExposure`：`gunshot(shooter, at, earDb, suppressed)` 由 `NativeGunShot` 每发调用；`ExplosionEvent.Detonate` 按爆炸半径算（`ExplosionAccessor`）；闪电实体进入世界时按半径 `lightning_radius` 的爆炸算（读档加载的不算）。头顶看不到天 ×`indoor`。
- `HearingLoad`：每份剂量 × 戴着的耳罩 `impulseProtectionMultiplier` × 幸存者属性 `apocalypse_firstlight:tinnitus`，封顶 `max_load`；单份 ≥ `jolt_from` 发震耳包，强度 `clamp(剂量 / jolt_full, jolt_least, 1)`。每秒按半衰期消退一次（睡觉 ×`sleep`），只在线时计时。存玩家持久数据 `afl_tinnitus` {`v`, `load`}；死亡清空，从末地回来照旧。同步给本人：档位变化、负荷变化 ≥ 0.5、有负荷时每 5 s。
- 枪 JSON：`noise.tinnitus`（布尔）换成 `noise.ear_db`（0–250，必填），数值同第 3.2 节；`NativeGunDefinition.earDb()`。
- 包：`HearingPackets.State(load, stage)`、`HearingPackets.Jolt(strength)`，注册在 `HealthPackets` 之后；旧的 `ExplosionTinnitusS2CPacket` 删除。协议 `AflNetwork.PROTOCOL` = "61"。
- 简易耳罩：`SimpleHearingProtectionItem.IMPULSE` = 0.10（原 0.50），`WORLD_SOUND` = 0.50 不变；物品说明中英文改成"挡住枪声和爆炸对耳朵的大部分伤害"。
- 调试命令（op）：`/afl hearing` 显示负荷和档位，`/afl hearing clear`，`/afl hearing load <值>` 直接设负荷，`/afl hearing dose <值>` 按一次巨响加剂量（会乘耳罩和属性，够大会震耳）。

**客户端**

- `client/TinnitusClient`：收到档位后按档的 `interval` 随机安排下一次阵发，长度 `length`、音量 `volume`；档位升高时立刻响一次（刚登录收到的第一个状态不算升高）。每次随机左耳、右耳或两耳（相对位置 x = −1 / 1 / 0，无距离衰减）。两次同步之间本地按半衰期倒数，供徽章计时。登出清空。
- 声音事件 `apocalypse_firstlight:tinnitus`：只用 `sounds/hearing/tinnitus_2.ogg`（"更高更细"，单声道）。用户录了 4 段 Firefly，2026-10-21 实机后留下"起伏高音" = tinnitus_1 和 tinnitus_2，"稳定高音"和"偏低带沙"听着奇怪不用（「不要再使用“偏低带沙”和“稳定高音”了」）；2026-10-22 用户又定「tinnitus_1不要再用了，只用2」，`sounds.json` 里去掉 tinnitus_1（ogg 文件还留在资源里，没有引用）。`tools/build-tinnitus-sounds-v2.cjs` 切段、淡入淡出、主体响度统一。字幕"耳鸣 / Ears ring"。震耳仍用 `explosion_tinnitus`，长 4 + 6 × 强度 秒。
- 音量（2026-10-22 用户「耳鸣的声音太大了，压低一点」，都减半，约 −6 dB；用户 2026-10-22 实机 PASS）：阵发耳鸣 `volume` 每档 0.12 / 0.2 / 0.28（原 0.25 / 0.4 / 0.55）；震耳新加配置 `jolt_volume` [0.12, 0.4]，按强度插值（原写死 0.25 + 0.55 × 强度）。`explosion_tinnitus.ogg` 本身比 tinnitus_2 响约 6 dB（平均 −14.6 dB 对 −20.9 dB）。
- 周围声音：`HearingProtectionAudio.listenerMultiplier()` 再乘 `TinnitusClient.hearingMultiplier()` = 档的 `hearing` × 震耳的下沉（降到 `jolt_hearing`，`jolt_recover` 秒内平滑回来）。耳鸣声和震耳声本身不受这个倍率影响，也不再被耳罩压低。只调音量，没有做低通"闷住"。
- 状态徽章（`StatusBadgeHud`）：key `tinnitus`，BAD 色，新的 EAR 图标（侧面的耳朵加两道声波）；名字和效果"轻度耳鸣 / 偶尔耳鸣"、"耳鸣 / 耳鸣，周围声音变小"、"严重耳鸣 / 频繁耳鸣，周围声音明显变小"；外圈 = 耳鸣有多重：负荷从轻度下限（空）到 `max_load`（满），按对数，开枪会变满，之后随消退慢慢变短；旁边不显示时间（2026-10-21 用户：每开一枪倒计时都往上加；照 master 脱水徽章的改法，只留外圈）。说明 `hud.apocalypse_firstlight.status.tinnitus.detail`。

**删除**

- `client/ExplosionTinnitusClientState`、`ExplosionTinnitusEnvelope`、`ExplosionTinnitusOverlay`（那张灰色暗角）、`ExplosionTinnitusSound`；`explosion/ExplosionTinnitusEvents`、`ExplosionTinnitusProfile`；`tinnitus/` 整个包；贴图 `textures/gui/effects/explosion_tinnitus_overlay.png`；`HearingProtectionManager.effectiveTinnitusSeverity`、`HearingProtectionAudio.tinnitusVolumeMultiplier`。
- dev：`ExplosionTinnitusGameTests`、`GunshotTinnitusGameTests`、`TinnitusAudioRegressionTest`、`TinnitusTimelineTest` 和两个 `*tinnitus-gametest.init.gradle`；`NativeRifleBalanceTests` / `NativeSuppressorTests` 改查 `earDb()`。

**雷击伤 V1**（[lightning_injury_v1_plan.md](lightning_injury_v1_plan.md)，分支 `feature/lightning-injury`，从这个分支开，未合并、未实机）：`HearingLoad.atLeast` 让直击和侧闪把负荷直接拉到严重耳鸣并震一下；`TinnitusClient.hearingMultiplier()` 再乘鼓膜穿孔（×0.7）和昏迷时的 ×0.12；鼓膜穿孔期间耳鸣阵发 60% 落在破的那只耳朵。协议在那个分支上是 62。

**验证**：`compileJava` 和 `compileDevJava` 通过。第一轮实机后用户提了两处（每开一枪倒计时往上加；"稳定高音""偏低带沙"两段听着奇怪），改完后用户 2026-10-21 实机 PASS。
