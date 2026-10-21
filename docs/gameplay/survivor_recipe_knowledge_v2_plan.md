# 幸存者配方知识 V2：接口规划

状态（2026-10-19）：**只是设计，没有任何代码。** 写于 V1 原版退役分支 `AFL-Vanilla-Rework`（[vanilla_survival_rework_v1.md](vanilla_survival_rework_v1.md)），依据用户 V1a 补充里的 V2 约束。下文"现状"一节读过 master 上 `survivor/` 的代码；其余都是规划。

## 1. 约束（用户 2026-10-19）

- 配方知识属于**具体的一位幸存者**（`SurvivorRecord`），不是世界公共知识，也不按玩家 UUID 记。
- 首个幸存者按背景得到基础知识。
- 解锁途径：拾取材料、阅读图纸、完成前置制造。
- 没解锁的配方默认看不见；解锁了但缺材料的，看得见、做不了。
- 服务端权威校验。
- 死亡继任：新幸存者的知识独立初始化，不继承。世界里实际留下的纸质图纸可以再学。不能通过共享储存或玩家 UUID 隐式继承。
- 重连后恢复同一位幸存者的知识。
- 旧存档迁移：**尚待 D / Main 协商**，不默认全部解锁。
- 多人：账户不等于幸存者；中途加入的玩家单独初始化；管理员调试解锁可以有，但不默认共享。
- 不改 `SurvivorRecord` 的序列化格式。

## 2. 现状（读过代码）

| 事实 | 位置 |
|---|---|
| 每位幸存者有固定的 `UUID id`，由世界种子、玩家 UUID 和第几任算出；`SurvivorRecord.id()` 公开可读 | `survivor/SurvivorRecord.java`、`Survivors.firstSurvivor` / `roll` |
| 玩家的全部幸存者是一条 `SurvivorLineage`（按玩家 UUID），`lives` 按顺序排；`current()` 是正在玩或最后玩的那位，`pending()` 是等着接班的继任者 | `survivor/SurvivorLineage.java` |
| 所有谱系存在世界的 `SurvivorWorldData`（SavedData） | `survivor/SurvivorWorldData.java` |
| 玩家实体上记着当前幸存者：`getPersistentData()` 的 `PLAYER_SURVIVOR_KEY` = `record.id` | `Survivors.takeOver` |
| 首个幸存者：第一次登录时建（`ensureSurvivor`，状态 ALIVE，`setup = CHOOSING`），背景在设置界面确认时才定（`SurvivorSetupFlow.confirm` → `SurvivorSetup.confirm`） | `survivor/Survivors.java`、`SurvivorSetupFlow.java` |
| 继任者：死亡时按种子随机（`roll`，PENDING，背景随之确定），复活时接班（`takeOver` → ALIVE） | `survivor/Survivors.java` |
| 重连：谱系和 `current()` 都在世界存档里，登录时原样读回 | `SurvivorEvents.onLogin` |
| 多人：谱系按玩家 UUID，每个玩家各自一条；中途加入的玩家第一次登录时单独建首个幸存者 | `Survivors.ensureSurvivor` |

结论：用 `SurvivorRecord.id` 做键就能满足"属于具体幸存者、继任不继承、重连恢复、多人各自独立"，**不需要改身份契约**，所以这一轮没有向 D / Main 发询问。

## 3. 存储

- 单独的 SavedData，例如 `apocalypse_firstlight_recipe_knowledge`，键是 `SurvivorRecord.id`，值是这位幸存者已知的知识 ID 集合，每条带来源（`background` / `pickup` / `blueprint` / `crafted` / `admin`）和获得时间。
- 不往 `SurvivorRecord` 里加字段，不改 `SurvivorWorldData` 的格式。
- 死去的幸存者的知识保留在存档里（尸体、死亡总结以后可能要显示），但没人能再用：查询只认当前 ALIVE 的那位。
- 不按玩家 UUID 建任何索引，避免隐式继承。

## 4. 知识和配方的关系

- 知识 ID 和配方 ID 分开：一条知识可以解锁一组配方（例如"基础木工"解锁木板、木棍、工作台……），由数据文件定义，例如 `data/<ns>/recipe_knowledge/*.json`：知识 ID、包含的配方、解锁条件（拾取哪些物品、读哪张图纸、做过哪个配方）。
- 背景的初始知识写在背景数据里（`SurvivorCatalog` 的背景条目加一个知识列表，由 D 维护），或者单独一份背景 → 知识的映射文件（不碰 D 的文件）。哪种方式要和 D 定。
- V1 退役的配方不进任何知识。

## 5. 时机

| 时刻 | 动作 |
|---|---|
| 首个幸存者确认设置（`SurvivorSetupFlow.confirm` 之后） | 按背景写入初始知识 |
| 继任者接班（`Survivors.takeOver`，背景在 `roll` 时已定） | 按它自己的背景写入初始知识；**不看**上一任 |
| 重连 | 不写入，只把当前幸存者的知识发给客户端 |
| 拾取、读图纸、完成制造 | 服务端判断条件，写给当前 ALIVE 的幸存者 |
| 死亡 | 不动；下一任从零开始 |

挂钩方式待定：最好由 D 在这两个时刻发一个事件（例如"幸存者开始"，带 `ServerPlayer` 和 `SurvivorRecord`），知识系统订阅它，而不是知识系统去改 `survivor/` 的代码。这是 V2 开工时唯一需要 D 配合的地方。

## 6. 校验和显示

- 服务端：制造时检查当前幸存者是否知道这条配方，不知道就不给结果。客户端只负责显示。
- 配方书 / JEI：未解锁的不显示；已解锁、缺材料的显示为灰色。原版配方书可以用 `ServerPlayer.awardRecipes` / `resetRecipes` 按幸存者同步（切换幸存者时整体重发）。JEI 要另做隐藏（V2 再定）。
- 图纸是世界里的物品，读过的那位学会；图纸本身留着，别人（或下一任）捡到还能学。

## 7. 多人和管理员

- 一个账户在一个世界里先后是多位幸存者；同一时刻只有一位。
- 管理员调试解锁：命令写入指定玩家**当前幸存者**的知识，来源记 `admin`，不默认给所有人。
- 不提供"整个世界共享知识"的开关；以后要做团队共享，也是通过实际的物品（图纸、笔记）传递。

## 8. 旧存档迁移（待协商）

- 已经在玩的幸存者没有知识记录。**不默认全部解锁。**
- 可能的做法：第一次加载时按这位幸存者的背景补初始知识；或者按 V1 前已经做过的东西补。具体规则 **尚待 D / Main 协商**。
