# 世界生成版本锁 V1

日期：2026-10-14（C1，分支 `feature/federal-worldgen-v1`）。状态：**已实现并通过编译；决策表离线测试通过；没有做实机测试**。实机验收要用一次性的测试世界，见文末。

这是 [ADR-11](worldgen_architecture_decisions_v1.md)（版本与旧世界）在世界层面的第一个实现，范围是 AFL Terrain V2 主世界。

## 解决什么问题

- **以前的问题**：Terrain V2 的规划缓存有版本号，即 `TerrainPlanV2.VERSION`、`TerrainPlanSurface.FORMAT` 和缓存文件名。但世界存档本身不记录"用哪套规则生成"。
- **后果**：规则一变，旧存档照常打开，新区块按新规则生成。和旧区块之间会出现断崖、断河和群系接缝，而且没有任何提示。
- **缓存版本不能代替世界兼容版本**：
  - 换存储格式但数值相同，不应该拦住世界；
  - 漏改版本号的规则变化，缓存版本根本发现不了。

## 记录什么

新世界在第一个区块生成之前写入记录，文件是 `<存档>/data/apocalypse_firstlight_worldgen_lock.dat`（SavedData，名字 `apocalypse_firstlight_worldgen_lock`）。

| 字段 | 内容 |
|---|---|
| `schema` / `origin` | 记录格式 1；`created`（新世界）或 `adopted_legacy`（锁出现之前的旧世界） |
| `created` / `createdBy` | 时间、AFL 与 Minecraft 版本 |
| `profile` / `seed` | `afl_terrain_v2`；世界种子 |
| `systems` | 各子系统声明的**世界兼容版本**（见下表） |
| `planCacheFormat` | 规划缓存的存储格式，**只作参考**，不参与判定 |
| `planDigest` | 规划数值摘要：高度、水面、山带、稳定层、离岸、河网、生态分区，SHA-256 前 16 位 |
| `probes` | `plan_queries`：规划运行期查询，32×32 格点加河网顶点<br>`density`：噪声路由最终密度，16×16 列 × 7 个高度<br>`biomes`：群系源，32×32 列 × 3 个高度 |
| `tiles` | 每 512 m 一块的摘要（平均高度 cm、水格数、河网顶点数、分区和），用来分辨浮点级差异，并指出差异的位置 |
| `history` | 采纳、软变化、接受变化的记录，最多 64 条，第一条永远保留 |

声明的版本（`worldgen/lock/WorldgenRules.java`）：

| 子系统 | 当前版本 | 级别 | 含义 |
|---|---|---|---|
| `national_template` | `macro_geography#3` | HARD | 海陆布局、三座卫星岛 |
| `terrain_plan` | `afl_terrain_plan_v2_r1#4` | HARD | 规划高度、水面、河网、生态分区 |
| `terrain_density` | `overworld_density#1` | HARD | 区块地形形状、洞穴、含水层、雕刻稳定层 |
| `river_carving` | `river_carver#1` | HARD | 区块里的河水与河岸 |
| `ecology` | `ecology_r1#1` | HARD | 群系映射、AFL 群系 JSON、主世界地表规则 |
| `forest` | `mesh_forest#<ForestPlan.VERSION>` 或 `vanilla_trees` | SOFT | 天然树木 |

`terrain_plan` 的历史版本：#1 Phase 2 r1 高度，#2 Phase 2b 河流，#3 岸线扭曲，#4 生态阶段。这几次都发生在 2026-10-10，当时还没有版本锁。规划器标签一直是 r1，所以早期世界无法事后区分是哪个版本，只能按旧世界（LEGACY）处理。

## 每次加载怎么判

判定逻辑在 `WorldgenLockPolicy.evaluate`，是纯函数。

- **时机**：`WorldgenLockEvents` 在主世界的 `LevelEvent.Load` 上运行，优先级最高。
- **为什么来得及**：这个事件在 `createLevels` 里触发，早于新世界设置出生点，也早于任何区块的生成或读取。
- **规划**：规划此时已经在内存里（RandomState 刚绑定过），算一次指纹约 0.1 s。

| 情况 | 结果 |
|---|---|
| 没有记录，世界还没初始化 | **CREATE**：新世界，立即写记录（不等自动保存） |
| 没有记录，世界已初始化 | **ADOPT_LEGACY**：锁出现之前的旧世界。把当前规则当作它以后的基线，记录 `adopted_legacy`，日志写警告。这只是领养：旧区块是哪套规则生成的仍然未知，世界永远算 LEGACY（ADR-11） |
| 版本和输出都一样 | **MATCH**：什么都不写。只是缓存格式变了的，记一条注释，**不算变化** |
| 只有 SOFT 变化 | **UPDATE_SOFT**：照常打开，日志警告，写入历史，基线前移 |
| 有 HARD 变化 | **BLOCK**：**不打开世界**，什么都不写、不迁移、不删除。日志和崩溃报告写清原因与令牌 |
| HARD 变化，且该世界的 serverconfig 里填了**这一次变化**的令牌 | **ACCEPT_HARD**：打开，写入历史，基线前移 |

细则：

- **漏改版本号也能发现**：
  - 规划数值变了而 `terrain_plan` 和 `national_template` 都没升版本，按 HARD 处理，并按分块摘要报出大致坐标范围。
  - 如果只是浮点级差异（没有任何一块平均高度超过 2 cm、水格超过 2、河顶点超过 4、分区和超过 4，且变化块数 ≤ 1%），只算 SOFT。这种情况通常是另一种 CPU 架构或 JVM 数学库生成的规划。
- **探针变化**：
  - 能被已声明的变化解释的，只记注释；
  - 不能解释的按 HARD 处理，并说明原因，比如另一个模组或数据包替换了主世界噪声设置或群系，或者 AFL 有一次没声明的修改。
- **种子或 profile 不同**：HARD。
- **未知子系统**：新出现的、或者消失的子系统 id 一律按 HARD 处理。
- **记录文件存在但读不出来**：拦截，文件原样保留。
- **不受锁的世界**：没有 Terrain V2 规划的主世界（超平坦、数据包世界）不上锁。

## 拦截之后怎么办

日志和崩溃报告会写出：

- 记录里的指纹，以及当前构建的指纹；
- 逐项变化；
- 一个 8 位十六进制令牌。

令牌是 `sha16(旧指纹 id + ">" + 新指纹 id)` 的前 8 位，只对这一对新旧规则有效。

1. 想保持原样：用生成这个世界的那个 AFL 版本打开。
2. 想强行继续：
   - 先备份存档；
   - 在 `<存档>/serverconfig/apocalypse_firstlight-worldgen.toml` 里写 `acceptWorldgenChange = "<令牌>"`；
   - 再打开。
   - 接受后基线前移，下次加载就是 MATCH。令牌留在配置里也没有影响，因为它只对那一次变化有效。

版本锁**不会**自动恢复旧生成器。旧规划器要是还在当前构建里，就不需要锁。要安全地继续生成旧地形，只能用旧版本。

查询状态：`/afl worldgen lock`（权限 2，只读）。它显示：

- 记录来源与基线指纹；
- 本次判定与逐项差异；
- 历史的最后几条。

## 升级规则（开发约定）

- **什么时候升**：区块的高度、水面、洞穴、群系、地表方块或树木可能和以前不同的修改，要在 `WorldgenRules` 里把对应子系统 `#` 后的数字加一。
- **只增不复用**，同时在本文档的历史表里加一行。
- **HARD 还是 SOFT**：会造成接缝的用 HARD；只影响新区块装饰的用 SOFT。
- **改存储格式**（`TerrainPlanSurface.FORMAT`、缓存文件名、压缩方式）时不要升兼容版本：数值摘要相同，锁就会判 MATCH。
- **新的世界生成系统**（例如六州国家模板、国家公路实体）接入正式生成时，在 `WorldgenRules.current` 里加一个子系统 id。旧世界没有这个 id，会被拦截。这是有意的：新系统只能用于新世界，或者显式接受。

## 文件

| 文件 | 作用 |
|---|---|
| `worldgen/lock/WorldgenRules.java` | 声明的版本与级别 |
| `worldgen/lock/WorldgenSystem.java` | 子系统记录、级别查询（纯 Java） |
| `worldgen/lock/WorldgenFingerprint.java` | 指纹值；`id()` 不含缓存格式和分块摘要 |
| `worldgen/lock/WorldgenProbe.java` | 当前世界的指纹：规划摘要、密度探针、群系探针 |
| `worldgen/lock/WorldgenLockPolicy.java` | 判定（纯 Java） |
| `worldgen/lock/WorldgenLockRecord.java` / `WorldgenLockData.java` | 记录与 SavedData（NBT） |
| `worldgen/lock/WorldgenLockEvents.java` | 加载时执行判定，写记录或拦截 |
| `worldgen/lock/WorldgenLockConfig.java` | 每个世界的 SERVER 配置 `acceptWorldgenChange`，注册在 `ApocalypseFirstLight` 构造函数里 |
| `worldgen/lock/WorldgenLockException.java` | 拦截用的异常 |
| `worldgen/lock/WorldgenLockCommand.java` | `/afl worldgen lock` |
| `worldgen/terrain/v2/TerrainPlanFingerprint.java` | 规划数值摘要、查询摘要、分块摘要与比较 |
| `tools/federal-worldgen/java/.../lock/LockPolicyChecks.java` | 判定表离线测试（`bash tools/federal-worldgen/run.sh lock`） |

## 已验证

- `compileJava --offline` 通过。
- 判定表离线测试全部通过，用的是合成指纹（`run.sh lock`）。覆盖的情况：
  - 新建、领养、相同、只换缓存格式、树木软变化；
  - 声明的规划变化：拦截、错误令牌、正确令牌，以及令牌绑定到具体变化；
  - 浮点级差异、未声明的规划变化（含位置）、未声明的密度变化（提示另一个模组）、声明的密度变化；
  - 换种子、新出现的子系统；
  - 领养后保持 LEGACY；历史有上限。
- 规划指纹的确定性（离线，种子 -295378578869149513）：
  - 两次独立构建的 r1 规划指纹相同：`8d712b1dd53f3bd3 / 90287ece05fe7a5c`；
  - 尺度测试用的参数化副本也得到同一个指纹；
  - 算一次约 0.07–0.09 s。

## 未验证 / 已知缺口

- **没有实机测试**。还没确认的有：
  - `LevelEvent.Load` 的实际时机；
  - NBT 写入与读回；
  - 密度与群系探针在真实世界里的稳定性（比如两次启动是否一致）；
  - 拦截时的界面表现。
- **集成服务器**（单人游戏）里拦截会让服务器启动失败，客户端大概率也随之进入崩溃报告界面，而不是一个友好的提示框。后续可以加客户端侧的打开前检查界面（未做）。
- **覆盖不到的规则**：雕刻器、地表规则、地物（features）只靠声明的版本号覆盖，探针测不到。
- **旧世界领养**：领养不等于验证。2026-10-14 之前的 Terrain V2 世界（包括用户的测试世界）在第一次用新构建打开时会被领养，数据目录里多一个小文件，其他都不动。如果它们是在 `terrain_plan` #1–#3 时期生成的，旧区块与新区块之间的接缝早已存在，锁无法发现。
- **ADR-11 的其他部分还没有接这套锁**：Highway、道路施工快照、结构资产 digest。

## 实机验收建议（由用户决定是否进行）

用全新的测试世界，不用正式存档：

1. 新建世界，日志出现 `new world: recorded`，存档 `data/` 下有记录文件。
2. 退出重进，日志出现 `matches`。
3. 在开发构建里把 `WorldgenRules.TERRAIN_DENSITY` 临时改成 2，再打开同一个世界：应当拒绝打开，日志给出令牌。
4. 填入令牌后能打开，`/afl worldgen lock` 能看到历史记录。
5. 测试完成后把版本号改回原值，并删除这个测试世界（改回去本身也是一次 HARD 变化）。
