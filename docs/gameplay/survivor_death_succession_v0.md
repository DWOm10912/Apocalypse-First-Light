# 幸存者死亡与随机继任 V0

状态：**开发中（独立 Worktree `feature/survivor-v0`，未合并 Main）**。只做过一次离线编译检查，见文末“验证”；没有实机测试，下面写的是代码行为，不是实机结论。

首次公开测试的 P0：玩家扮演的是“一个幸存者”，不是不死的账号。幸存者死亡是永久的；普通模式由随机继任者接手同一世界，困难模式整局结束。

代码：`src/main/java/com/antaurora/apofirstlight/survivor/`。

## 1. 范围

本轮包含：模式数据、幸存者身份与生命记录、死亡接入、状态重置、持久尸体与搜刮、随机继任、困难模式终止、AFL 死亡结算界面。

本轮不包含：Mesh 人物、骨骼、IK、布娃娃、腐烂、死亡镜头、过场、剧情模式、技能系统、职业/特质/身体系统、出身州与背景的实际内容。

## 2. 模式

`SurvivorMode`：`UNSET` / `NORMAL` / `HARD`，存在世界的幸存者数据里，与原版难度和原版极限标志分开。一旦确定就锁定，游戏内没有任何途径改回。

| 情况 | 模式 |
|---|---|
| 原版极限（hardcore）世界 | 固定 HARD，不询问 |
| 专用服务器新世界 | 读 SERVER 配置 `apocalypse_firstlight-survivor.toml` 的 `dedicatedServerMode`（默认 NORMAL），只读一次 |
| 本系统出现前就存在的世界（没有 fresh 标记） | 开服时直接定为 NORMAL |
| 单人新世界 | 保持 UNSET，等房主首次进入时选择 |
| 选择前就有玩家死亡 | 锁定为 NORMAL，日志警告 |

“新世界”由 `LevelEvent.CreateSpawnPosition` 判定（只在创建世界时触发；Terrain V2 会取消这个事件，所以监听时 `receiveCanceled = true`），写入 `freshWorld`。

| 模式 | 死亡后 |
|---|---|
| NORMAL | 该幸存者永久死亡；世界继续；死亡瞬间决定一位随机继任者，重生即由继任者接手 |
| HARD | 该幸存者永久死亡；该玩家这一局结束；不再生成继任者，不能重生；世界保留作纪念存档，不删除任何东西 |

没有剧情模式。

## 3. 数据结构

世界数据 `SurvivorWorldData`（`SavedData`，跟随主世界保存为 `<世界>/data/apocalypse_firstlight_survivors.dat`）：

- `Mode`：模式；`Fresh`：新世界标记。
- `Lineages`：每个账号 UUID 一条“传承”（`SurvivorLineage`）：账号名、状态、全部生命记录（旧→新）、最后一位死者的床位置。
- 传承状态：`ACTIVE`（当前幸存者存活）、`AWAITING_SUCCESSOR`（NORMAL：已死亡，继任者已决定，等待重生）、`ENDED`（HARD：结束）。

生命记录 `SurvivorRecord`：

| 字段 | 含义 |
|---|---|
| `Id` | 幸存者唯一 ID |
| `First` / `Last` | 姓名 |
| `Life` | 职业生涯序号：该玩家在本世界的第 n 位幸存者，从 1 开始 |
| `Roll` | 生成该幸存者的随机种子（审计用） |
| `State` | `PENDING`（已决定未接手）/ `ALIVE` / `DEAD` |
| `StartGame` / `StartDay` | 开始的世界游戏刻 / 世界日时间 |
| `DeathGame` / `DeathDay` | 死亡的世界游戏刻 / 世界日时间 |
| 存活天数 | `(DeathDay - StartDay) / 24000`，不小于 0（按世界日时间计，睡觉跳过的夜晚也算） |
| `Cause` / `CauseId` | 死亡消息（Component JSON，受害者名字换成幸存者姓名）/ 伤害类型 msgId |
| `DeathDim` / `DeathPos` | 最终死亡维度与方块位置 |
| `Corpse` | 尸体 ID |
| `PlanX` / `PlanZ` | 继任者出生规划点（死亡时决定，不加载区块） |
| `SpawnDim` / `SpawnPos` / `SpawnSource` | 实际出生点与来源（提供者 ID、`fallback` 或 `initial`） |
| `OriginState` / `Background` / `Profession` / `Traits` / `Body` | 预留字段，V0 一律为空，没有任何系统读取 |

玩家自身持久数据根下写入 `apocalypse_firstlight:survivor_id`（该玩家存档属于哪位幸存者），用于识别崩溃后过期的玩家存档。

## 4. 死亡流程（服务器权威）

只有生存/冒险模式的玩家受幸存者规则约束；创造/旁观模式死亡保持原版。

1. `LivingDeathEvent`（最低优先级；被其他模组取消的死亡不处理）：
   - 当前记录必须是 `ALIVE`，否则什么都不做——记录状态本身就是防重复结算的账本，同一幸存者第二次死亡事件不会再结算。
   - 写入死亡时间、死因、位置；记住床位置后清除玩家的重生点（重生不会回到旧床，新身体也不会继承它）。
   - NORMAL：传承进入 `AWAITING_SUCCESSOR`；HARD：传承进入 `ENDED`。
2. 本服务器刻结束时：先立刻写入幸存者数据文件，再保存所有玩家存档（`PlayerList.saveAll()`）。

首位幸存者：玩家第一次进入世界时随机生成（只有姓名），记录开始时间，来源 `initial`。V0 不提供姓名选择。

## 7. 状态重置

死亡即换人。继任者是新的身体，不继承：

| 系统 | 死亡后 | 代码 |
|---|---|---|
| 累计辐射剂量 | **清零（本轮改动）** | `radiation/RadiationExposureEvents.copyOnClone` |
| 残留污染 | 清零（原有） | 同上 |
| 口渴 / 体温 / 体力 | 重置（原有） | `PlayerThirst` / `PlayerTemperature` / `PlayerStamina` 的 Clone 处理 |
| 饥饿 / 生命 | 原版新玩家默认值 | 原版 `restoreFrom` |
| 耳鸣 | 清除（原有） | `GunshotExposureTracker.onRespawn`、客户端 `ExplosionTinnitusClientState` |
| 经验 | AFL 原本就不给经验（`VanillaProgressionEvents`） | — |
| 技能成长 / 伤病 / 疲劳 | 当前不存在这些系统 | — |

辐射设计文档 `docs/项目内容/01 - 设计/辐射/辐射系统.md` 的保存规则表与“死亡不能清除累计剂量”已同步改为死亡清零。

## 9. 命令

只读审计，权限 2：`/afl survivor`、`/afl survivor player <玩家>`、`/afl survivor list`。没有任何命令能改模式、重抽继任者或复活。

## 验证

- 阶段 A：仅代码自查，未编译、未实机。
