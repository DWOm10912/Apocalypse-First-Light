# 世界生成版本锁 V1

日期：2026-10-14（C1，分支 `feature/federal-worldgen-v1`；C2 同日补联邦 profile 和实测）。状态：**已实现；决策表离线测试通过；C2 在隔离开发客户端里实测了新建、重开、LEGACY、BLOCK（§已验证）**。没有在专用服务器上测过。

这是 [ADR-11](worldgen_architecture_decisions_v1.md)（版本与旧世界）在世界层面的第一个实现，范围是 AFL Terrain V2 主世界。

2026-10-14 联邦分支合并进 master，**新建普通世界默认是联邦六州**（[C2](federal_six_state_c2.md) 第 12 节）：新世界记录的 profile 是 `afl_federal_experimental`（id 里的"experimental"是 C2 留下的，已有世界的记录和 level.dat 都写着它，所以不改名）。r1 的 profile `afl_terrain_v2` 照旧：已有的 r1 世界在 level.dat 里存着自己的生成器（`minecraft:overworld` 噪声设置），默认改了也还是按 r1 打开，锁照常比对 r1 的规则；世界类型按钮里的"AFL r1（旧地形）"（原版的 `minecraft:normal`）也还能手动选。

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
| `profile` / `seed` | `afl_terrain_v2`（r1）或 `afl_federal_experimental`（联邦六州，2026-10-14 起新建普通世界的默认）；世界种子 |
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

联邦世界（C2，profile `afl_federal_experimental`，[federal_six_state_c2.md](federal_six_state_c2.md)；2026-10-14 起是新建普通世界的默认）有自己的一套子系统 id，和 r1 的同名但取值不同，所以两种世界永远不会共用基线（profile 本身也是 HARD）：

| 子系统 | 当前版本 | 级别 | 含义 |
|---|---|---|---|
| `national_template` | `federal_template#federal_template_v1_proto#1` | HARD | 联邦海岸、六州、地貌区、气候 |
| `terrain_plan` | `afl_federal_plan_c2_v1#1` | HARD | 规划高度、水面、湖泊、河网、生态分区 |
| `terrain_density` | `federal_density#1` | HARD | 联邦噪声设置、`density_function/federal/*`、抬高的顶部 slide |
| `river_carving` | `federal_water#1` | HARD | 区块里的河水、湖水、河岸与湖岸 |
| `ecology` | `ecology_federal#1` | HARD | 联邦分区、地表群系映射、联邦群系 JSON、地表规则分支 |
| `forest` | 同 r1 | SOFT | 天然树木 |
| `road_network`（开关，C3.2） | `afl_road_network_c3_v1#c3.2#interstate_ew:county_seat-valley_town#surface4` | HARD | 公共道路路线、路面、碰撞、桥（[C3](federal_highways_c3.md) 第 10 节；surface2 = 桥改成网格 + `road_structure` 碰撞，2026-10-14；surface3 = 桥下天然地面保留、碰撞跟网格、翼墙压顶，2026-10-15；surface4 = 结构方块站在地面顶块上，不再顶掉它，2026-10-15） |
| `terrain_works`（开关，C3.2） | `road_works_r4#1` | HARD | 道路挖填、边坡、边沟进入地形 |

联邦世界还有探针 `road_network`：路网 JSON 的摘要（开关子系统的探针，世界没有这个开关时去掉）。第一次加载一个种子会规划一次路网（约 8 s），之后读 `afl_cache/road_network/` 的缓存。

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
| 有 HARD 变化 | **BLOCK**：**不打开世界**，不迁移、不删除；AFL 和服务器什么都不写（C2：拦截后的停服也不再存盘，`WorldgenLockSaveMixin`）。日志和崩溃报告写清原因与令牌。唯一的写入来自原版单人客户端：它打开**任何**世界时、服务器启动之前，会把 `level.dat` 重写一次（实测只改 `LastPlayed`），原文件留作 `level.dat_old`。锁在这之后才运行，管不到 |
| HARD 变化，且该世界的 serverconfig 里填了**这一次变化**的令牌 | **ACCEPT_HARD**：打开，写入历史，基线前移 |

细则：

- **漏改版本号也能发现**：
  - 规划数值变了而 `terrain_plan` 和 `national_template` 都没升版本，按 HARD 处理，并按分块摘要报出大致坐标范围。
  - 如果只是浮点级差异（没有任何一块平均高度超过 2 cm、水格超过 2、河顶点超过 4、分区和超过 4，且变化块数 ≤ 1%），只算 SOFT。这种情况通常是另一种 CPU 架构或 JVM 数学库生成的规划。
- **探针变化**：
  - 能被已声明的变化解释的，只记注释；
  - 不能解释的按 HARD 处理，并说明原因，比如另一个模组或数据包替换了主世界噪声设置或群系，或者 AFL 有一次没声明的修改。
- **种子或 profile 不同**：HARD。
- **未知子系统**：新出现的、或者消失的子系统 id 一律按 HARD 处理。**按世界开关的子系统例外**（见下一节）：记录里没有的，对这个世界是关的，不算变化。
- **记录文件存在但读不出来**：拦截，文件原样保留。
- **不受锁的世界**：没有 Terrain V2 规划的主世界（超平坦、数据包世界）不上锁。

## 按世界的开关（C3.1，2026-10-14）

[Main/C3 接口契约](main_c3_interface_contract_v1.md)第 10 节、第 12 节第 5 条：开关和版本锁由 C 统一负责，只有这一份。

**开关子系统**（`WorldgenGates.GATED`）：

| id | 归属 | 内容 |
|---|---|---|
| `road_network` | C | 公共道路网络（路线、路面、桥、排水） |
| `terrain_works` | C | 施工层：路基等工程怎样应用到密度 |
| `start_area` | Main | 老宅开局：选址、预留、放置（Main 按这里的规则登记） |

**规则**：

- 新建世界：这个构建声明了的开关子系统全部打开，并记进记录；
- 已有记录的世界：记录里有这个子系统才打开；版本不同照常算 HARD；记录里有、构建里没有的，也照常算 HARD；
- 记录里没有的：对这个世界关闭，**不算变化**（结果仍是 MATCH，只记一条注释）。旧世界的新区块不会长出半截路或半栋房子；
- 领养的旧世界：全部关闭；
- SOFT 变化让基线前移时，也不会把关着的开关打开。

**实现**：

- 判定用这个世界的**有效指纹**（`WorldgenGates.effective`）：运行中的规则，去掉这个世界没有的开关子系统和它们的探针（开关子系统的探针 id 和子系统 id 相同）；`Verdict.effective` 就是要写进记录的指纹；
- 开关子系统的探针变了而没有声明版本变化，按 HARD 报"输出变了但没声明"；
- `WorldgenLockEvents` 在判定之后、第一个区块之前，把这个世界打开的开关绑到主世界的 RandomState（`RandomStateSeedAccess.apocalypse$setWorldgenGates`）；不受锁的主世界和其他维度没有绑定，全部关闭；
- 生成代码查询：`WorldgenGateState.enabled(level, WorldgenGates.ROAD_NETWORK)`，或者区块生成里拿得到的 `RandomState`；
- `/afl worldgen lock` 多一行"gated systems on"。

**现在的状态**（C3.2，2026-10-14）：联邦规则声明了 `road_network` 和 `terrain_works`。C3.2 之后新建的联邦世界两者都打开并记录；之前建的联邦世界（记录里没有）照旧关闭，加载时只多一条说明；r1 世界没有这两个子系统。`start_area` 由 Main 登记。绑定开关时同时绑定路网（`RoadNetworkRuntime.bind`）：施工层挂到这个世界的 RandomState 上，在第一个区块之前。

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
- **新的世界生成系统**（例如六州国家模板）接入正式生成时，在 `WorldgenRules.current` 里加一个子系统 id。旧世界没有这个 id，会被拦截。这是有意的：新系统只能用于新世界，或者显式接受。
- **能对旧世界关掉的新系统**（道路、施工层、老宅开局）用开关子系统：id 加进 `WorldgenGates.GATED`，旧世界自动关闭，不会被拦。生成代码必须查 `WorldgenGateState.enabled`，关着时完全不生成。

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
| `mixin/WorldgenLockSaveMixin.java` | 被拦截的服务器停服时跳过 `saveAllChunks`（C2） |
| `worldgen/lock/WorldgenLockConfig.java` | 每个世界的 SERVER 配置 `acceptWorldgenChange`，注册在 `ApocalypseFirstLight` 构造函数里 |
| `worldgen/lock/WorldgenLockException.java` | 拦截用的异常 |
| `worldgen/lock/WorldgenLockCommand.java` | `/afl worldgen lock` |
| `worldgen/lock/WorldgenGates.java` | 开关子系统清单、有效指纹（纯 Java，C3.1） |
| `worldgen/lock/WorldgenGateState.java` | 绑到 RandomState 的开关、`enabled` 查询（C3.1） |
| `worldgen/RandomStateSeedAccess.java` / `mixin/RandomStateSeedMixin.java` | 开关集合存在 RandomState 上（C3.1 新增两个方法） |
| `worldgen/terrain/v2/TerrainPlanFingerprint.java` | 规划数值摘要、查询摘要、分块摘要与比较 |
| `tools/federal-worldgen/java/.../lock/LockPolicyChecks.java` | 判定表离线测试（`bash tools/federal-worldgen/run.sh lock`） |

## 已验证

- `compileJava --offline` 通过。
- 判定表离线测试全部通过，用的是合成指纹（`run.sh lock`）。覆盖的情况：
  - 新建、领养、相同、只换缓存格式、树木软变化；
  - 声明的规划变化：拦截、错误令牌、正确令牌，以及令牌绑定到具体变化；
  - 浮点级差异、未声明的规划变化（含位置）、未声明的密度变化（提示另一个模组）、声明的密度变化；
  - 换种子、新出现的子系统；
  - 领养后保持 LEGACY；历史有上限；
  - 开关（C3.1，17 条）：新世界打开并记录；领养全部关闭、探针去掉、基线等于没有开关时的指纹；旧记录 MATCH 且关闭（只有注释）；旧记录上的 SOFT 变化不会打开；带开关创建的世界保持打开；开关版本升级 BLOCK 且探针差异被声明解释；开关输出变了没声明 BLOCK；带开关的世界被没有开关的构建打开 BLOCK。
- 规划指纹的确定性（离线，种子 -295378578869149513）：
  - 两次独立构建的 r1 规划指纹相同：`8d712b1dd53f3bd3 / 90287ece05fe7a5c`；
  - 尺度测试用的参数化副本也得到同一个指纹；
  - 算一次约 0.07–0.09 s。

- **实测**（C2，2026-10-14，隔离开发客户端 `build/federal-c2-client/`，全新测试存档，种子 -295378578869149513，报告在 `design/worldgen/federal_c2/probe/`）：
  - 新建 r1 世界：`new world: recorded`，记录在出生点设置和第一个区块之前写入；C1 构建和 C2 构建的 r1 世界指纹相同（`49c55f90d2a4677a`）；
  - 重开（r1 和联邦，多次、两次启动之间）：MATCH，说明 NBT 读回、密度与群系探针在真实世界里稳定；
  - LEGACY：删掉记录的旧世界副本被领养（`adopted_legacy`，日志警告）；
  - BLOCK：联邦世界副本换成 r1 的记录后打开，6 项 HARD、给出令牌、世界未打开；156 个文件里只有 `level.dat` 的 `LastPlayed` 变了（原版客户端打开世界时写的），`level.dat_old` 是原文件；
  - 联邦新建：profile `afl_federal_experimental`，指纹 `b7ba3090bb2309d7`。

## 未验证 / 已知缺口

- **专用服务器**没有实际启动过（需要接受 EULA，没有替用户接受）。2026-10-14 只在测试客户端里用 `server.properties` 的同一段解析代码验了默认 `level-type`；开发用 GameTest 服务器（专用服务器的启动流程）也按默认建了联邦世界，之后改成 GameTest 服务器保持 r1（[C2](federal_six_state_c2.md) 第 12 节）。
- **集成服务器**（单人游戏）里拦截会让服务器启动失败，客户端随之崩溃退出并写崩溃报告（C2 实测），而不是一个友好的提示框。后续可以加客户端侧的打开前检查界面（未做）。
- **ACCEPT_HARD 的实机流程**（填令牌后打开）没有实测，只有离线判定表测试。
- **覆盖不到的规则**：雕刻器、地表规则、地物（features）只靠声明的版本号覆盖，探针测不到。
- **旧世界领养**：领养不等于验证。2026-10-14 之前的 Terrain V2 世界（包括用户的测试世界）在第一次用新构建打开时会被领养，数据目录里多一个小文件，其他都不动。如果它们是在 `terrain_plan` #1–#3 时期生成的，旧区块与新区块之间的接缝早已存在，锁无法发现。
- **ADR-11 的其他部分还没有接这套锁**：Highway、道路施工快照、结构资产 digest。
- **开关的实机流程**：C3.2 在隔离客户端里测了新建联邦世界（开关打开、道路生成）和重开；C3.2 之前建的联邦世界重开（开关应保持关闭）见 [C3](federal_highways_c3.md) 10.8。

## 实机验收建议（由用户决定是否进行；新建、重开、LEGACY、BLOCK 已由 C2 测试客户端测过）

用全新的测试世界，不用正式存档：

1. 新建世界，日志出现 `new world: recorded`，存档 `data/` 下有记录文件。
2. 退出重进，日志出现 `matches`。
3. 在开发构建里把 `WorldgenRules.TERRAIN_DENSITY` 临时改成 2，再打开同一个世界：应当拒绝打开，日志给出令牌。
4. 填入令牌后能打开，`/afl worldgen lock` 能看到历史记录。
5. 测试完成后把版本号改回原值，并删除这个测试世界（改回去本身也是一次 HARD 变化）。
