# Terrain Smooth V1：B 方案自然地表平滑正式接入

日期：2026-10-11。

状态：
- **代码完成**。
- **离线验证通过**：`node tools/terrain-smooth/check.mjs`（发布接线静态审查 + 规则检查），`node tools/terrain-smooth/physics.mjs`（合成地形行走物理），`physics.mjs world`（`AFL_R4_01` 真实地形，只出报告）。
- **`compileJava --offline` 通过**。
- **游戏实机**：作者第一次实机（2026-10-11）用 TNT 炸坑，平滑效果不错，但坑壁和坑边有透明窟窿（见 Q 节），已修复；修复版本由作者提交（`4dc5e650`），同日复测后作者验收 **V1 PASS**，V1 冻结。**发布 JAR：未打包、未验证**（见 P 节）。
- **性能**：只有离线数字；作者没有报告游戏内 16 / 24 / 32 区块视距的帧时间、区块构建时间，专用服务器也没跑过（N 节第 1、6 条），这几项仍按**未测**记，不能说达标。
- **V1.1 生物寻路修正**（2026-10-11，R 节）：
  - 实机问题：僵尸每到斜坡都跳；
  - 根因：离线复现，原版用坡块的最高角当地面；
  - 修法：两个新 mixin，改为取生物站立处的坡顶高度；
  - 状态：代码完成、离线物理通过、编译通过；作者实机测试僵尸寻路 **PASS**（2026-10-11）。
- **透明面修复**（2026-10-12，S 节）：
  - 实机问题：树根、原木和放上去的透光方块旁边，坡面上有透明窗；
  - 根因：离线测量确认，坡块把侧面带以下交给"实心"邻格，这些邻格却挡不住；
  - 修法：只改渲染，坡块在这种邻格旁补画那一段；
  - 状态：代码完成、离线通过、编译通过，作者实机 **PASS**（2026-10-12）。
- **灰化土、泥、黏土加入平滑**（2026-10-12，T 节）：
  - 用户要求："地表平滑覆盖灰化土 / 泥 / 黏土一起做了"；
  - 平滑方块从 6 种变成 9 种；泥的碰撞比坡面低 2/16，和原版泥一样会陷一点；
  - 状态：代码完成、离线 `check.mjs` 和 `physics.mjs` 通过（含新的泥坡行走测试）、`compileJava --offline` 通过；用户 2026-10-12 实机 **PASS**（整轮验收：木材建筑方块 V1、砍树耗耐力、倒树砸伤、灰化土 / 泥 / 黏土平滑）。

前身：
- [P0 视觉原型](terrain_smooth_visual_prototype_p0.md)：用户选定 B 方案。
- [P1 碰撞与交互](terrain_smooth_p1_interaction.md)：用户实机 PASS。

V1 不改 B 方案的几何算法和外观，只做三件事：
- 从开发测试区域搬到正式代码，对整个主世界生效；
- 只平滑真正的自然地表；
- 让它在全世界规模下的性能和一致性站得住。

## A. 迁移范围

| 原位置（P1，开发包） | V1 | 说明 |
|---|---|---|
| `dev/terrainsmooth/SmoothRules` | `terrainsmooth/SmoothRules`（正式，纯 Java） | 几何算法不变；测试区域的概念删除，换成“自然地表”判定；新增 `TREE` 格类型；新增 `affected()`（改一格影响到哪些高度） |
| `dev/terrainsmooth/SmoothShapes` | `terrainsmooth/SmoothShapes` | 不变（8 × 8 碰撞、4 × 4 选中、抬升计算） |
| `dev/terrainsmooth/SmoothWorld` | `terrainsmooth/TerrainSmooth` + `SmoothChunkData` + `SmoothChunkHolder` | 两侧状态、方块分类、按区块的地表表和坡面缓存、mixin 回调、区块与服务器生命周期 |
| `dev/terrainsmooth/SmoothSurfaceModel`、`SinkPlantModel`、`SmoothModels`、`SmoothOutline` | `terrainsmooth/client/` 同名 | 生成的四边形不变；数据来源改成正式接口；去掉“只在开发运行”的开关 |
| `dev/terrainsmooth/SmoothPrototype` | `terrainsmooth/client/TerrainSmoothClient` | 跟随服务端状态、编辑后局部重建、本地玩家抬升、区块构建取数 |
| 开发 mixin（`apocalypse_firstlight_dev.mixins.json`，靠 `runClient` 参数加载） | `mixin/TerrainSmooth*Mixin`，写进正式 `apocalypse_firstlight.mixins.json` | 开发 mixin 配置和 `build.gradle` 里的 `--mixin.config` 参数已删除 |
| 开发命令 `/afl dev terrain_smooth`、诊断 `SmoothProbe` | 仍在 `src/dev`（`SmoothCommands`、`SmoothDevClient`、`SmoothProbe`） | 改用正式接口；`a`、`area`、`here` 删除（V1 只有 B、全世界） |
| `tools/terrain-smooth/` | 原地更新 | 包名改为正式包；新增地下、树冠、屋顶回归检查和发布接线审查 |

## B. 正式入口和类结构

公共部分在 `src/main/java/com/antaurora/apofirstlight/terrainsmooth/`，客户端和专用服务端都加载：

| 类 | 作用 |
|---|---|
| `TerrainSmoothConfig` | SERVER 类型配置，见 K 节 |
| `TerrainSmooth` | 两侧状态 `CLIENT` / `SERVER`（开、碰撞开、版本号、计数）；方块分类 `cell()`；区块数据的生成和读取；坡面 `geometry()`（主线程缓存）；mixin 回调 `shape` / `notSuffocating` / `risesIntoBox` / `changed`；抬升 `lift()`；Forge 事件（服务器启动 / 停止、玩家登录、区块载入 / 卸载） |
| `SmoothChunkData` / `SmoothChunkHolder` | 挂在 `LevelChunk` 上的数据：256 列自然地表高度 + 坡面缓存，随区块释放 |
| `TerrainSmoothStatePacket` | 服务端 → 客户端的状态包，注册在 `AflNetwork`（协议号 37 → 38） |
| `SmoothRules`、`SmoothShapes` | 纯规则和盒子形状（离线检查直接用它们） |

客户端部分在 `terrainsmooth/client/`，只在客户端加载（`Dist.CLIENT`）：

| 类 | 作用 |
|---|---|
| `TerrainSmoothClient` | 收到状态后整体重建网格；方块变化后局部重建；本地玩家抬升；区块构建线程用的取数 `renderGeometry` |
| `SmoothModels` | 每次模型烘焙（含 F3+T）包装 9 种地表方块和小植物的模型 |
| `SmoothSurfaceModel` / `SinkPlantModel` | 斜面模型 / 植物下沉 |
| `SmoothOutline` | 斜面选中轮廓 |

Mixin 在 `src/main/java/com/antaurora/apofirstlight/mixin/`，都在正式配置的公共段：

| Mixin | 目标 |
|---|---|
| `TerrainSmoothBlockStateMixin` | `BlockStateBase` 里 `getCollisionShape`、`getShape` 调 `Block` 的那一处，`isSuffocating` 调判定的那一处（重定向，不新建回调对象） |
| `TerrainSmoothBlockCollisionsMixin` | `BlockCollisions#computeNext` 里的 `hasLargeCollisionShape()` 调用 |
| `TerrainSmoothLevelMixin` | `Level#onBlockStateChange`（客户端） |
| `TerrainSmoothServerLevelMixin` | `ServerLevel#onBlockStateChange`（服务端，它覆盖了上一个且不调用父类，P1 的 389 次回拉就出在这里） |
| `TerrainSmoothLevelChunkMixin` | `LevelChunk` 加一个字段，实现 `SmoothChunkHolder` |

## C. 自然地表识别

每一列只有一块可能参与平滑：这一列的**自然地表块**。

- **算法**（`TerrainSmooth#columnSurface`，规则 `SmoothRules#surface`）：
  - 从这一列的 `WORLD_SURFACE` 高度图（最高的非空气方块）往下扫，跳过空气、植物和雪层、树叶、树干（原木 / 木头 / 菌柄类、藤蔓、巨型蘑菇、可可、蜂巢）；
  - 遇到的第一块如果是 9 种天然方块之一（T 节），就是自然地表；
  - 否则（水、任何建筑或人工方块、岩石悬垂）这一列**没有自然地表**。
- **两侧一致**：客户端和服务端都维护 `WORLD_SURFACE` 高度图（原版 CLIENT 类型的高度图，随区块包同步，方块变化时两侧同样更新）。扫描读的是同样的方块，所以结果相同。
- **一块参与平滑的条件**（`SmoothRules#geometry`）同时满足：
  - A：它是本列的自然地表块；
  - B：方块是 9 种天然方块；
  - C：上方是空气或植物；
  - D：邻格关系按 B 方案规则产生了不平的角点。
- **邻列**：只有当邻列在这一高度附近的顶面也是它自己的自然地表时，才参与共享角点；否则该角点钉住（各自保持原高度）。
- **树冠下**：树叶和树干都算“看得见天”的一部分，所以树下的地面照常平滑；`canSeeSky()` 没有用。
- **复杂度**：区块载入时每列扫一次（开阔地 1–2 格，林下约 5–15 格），之后只在这一列有方块变化时重扫这一列。

## D. 地下 Cube 保护

不是本列自然地表的块一律保持原版 Cube、原版碰撞、原版窒息：
- 地下石层和矿；
- 天然洞穴、人工矿道、玩家挖出的地下空间：上面有岩层或土层，扫描先停在地表块上；
- 地堡地下室、地下工厂和管线空间、建筑地板：上面有人工方块，这一列没有自然地表；
- 岩石悬垂下的地面。

离线检查（`underground`）：1216 块洞穴地面，其中 1004 块紧挨着 1 格台阶，全部保持 Cube；洞穴上方的地表一块都没变。

## E. B 方案视觉和碰撞保留

- **几何不变**：角点规则（B 取四列平均、两格以上高差或非自然列钉住）、几何键、8 × 8 碰撞、4 × 4 选中、斜面轮廓、植物下沉、侧面草边、倾斜明暗都和 P1 相同，四边形生成代码是逐行搬过来的。
- **离线数字和 P1 相同**：8 × 8 盒子 p95 0.063、最大下陷 0.094、最大浮空 0.125；盒子间台阶 1/8；直坡最大台阶 1/16。
- **2 格及以上高差**：保持硬边。树干、水边、建筑和道路边界照旧钉住。
- **防窒息**：只对“本身就是平滑坡面”的块生效；普通地下整块仍按原版窒息。
- **抬升**：只针对本地玩家，只在脚陷进平滑坡面的碰撞盒时触发，最多抬 1 格，抬起后会撞东西就不动。没有改 `stepHeight`。

## F. 挖掘、放置和局部刷新

- 挖、放仍是一次一个原版方块、标准坐标，掉落、工具、放置规则都是原版。
- 方块变化时（两侧的 `onBlockStateChange`）：
  1. 重扫这一列的自然地表；
  2. 清掉 x、z 各 ±1 列所在区块（最多 4 个）的坡面缓存；
  3. 客户端只在附近真有坡面会变时，才标记重建 x ±1、z ±1，高度覆盖 `SmoothRules.affected()`（改动处下 2 格到上 1 格，以及这一列原地表、新地表各下 2 格到上 1 格），再往上加 2 格给植物。

  离线检查（`edits`）：每种模式各约 380 次随机挖、放、在地表上方加屋顶，被改变的坡面都落在这个范围内。
- 沙子、砾石下落是两次 `setBlock`（原位变空气、落点出现方块），同样走这条路径。
- 玩家放的泥土和天然泥土状态完全一样，**系统不区分来源**：天然材料满足条件就参与，旁边有人工方块就钉成硬边。

## G. 客户端和服务端一致性

- **规则**：两侧用同一套纯规则，读各自世界的同样方块和同样高度图。
- **开关**：服务端启动时读配置，登录时发给客户端，客户端只跟随；开发命令改状态时也会发给所有玩家。客户端没有自己的开关。
- **方块变化**：两侧都挂了钩子（`ServerLevel` 单独一个），这次的静态审查会检查这一点。
- **缓存键**：用进入时读出的整数坐标，不保存任何可变坐标对象。
- **缓存与线程**：坡面缓存只在本侧主线程读写，其他线程只读地表表、不写缓存。服务端用 `getChunkNow`，不会加载区块，非主线程直接返回空。
- **未加载的区块**：当作“未知”，角点钉住；区块载入或卸载时清掉 8 个邻区块的缓存。
- **客户端区块对象复用**：客户端收到同一区块的新数据时可能复用同一个区块对象，所以载入时一律重算该区块的地表表。

## H. Mixin 打包与加载

- 7 个 mixin 写在正式配置 `apocalypse_firstlight.mixins.json` 的公共段（客户端和专用服务端都加载），`defaultRequire 1`：注入失败会直接报错，不会静默失效。
  - V1 有 5 个；
  - V1.1 加了 `TerrainSmoothMoveControlMixin` 和 `TerrainSmoothPathNavigationMixin`（R 节），refmap 里有它们的目标 `MoveControl#m_8126_`、`VoxelShape#m_83297_`、`PathNavigation#m_183345_`、`WalkNodeEvaluator#m_77611_`。
- 正式配置由 MixinGradle 写进 JAR 清单，不再依赖 `runClient` 的启动参数。开发 mixin 配置、`--mixin.config` 参数、对应的 JAR 排除行都已删除。
- refmap：编译生成的 `apocalypse_firstlight.refmap.json` 里有全部重定向和注入目标的 SRG 名。`BlockCollisions` 的两个影子字段 `pos`、`box` 在 `compileJava-mappings.tsrg` 里（`f_186395_`、`f_186392_`），由 `reobfJar` 处理，和项目已有 mixin 的影子字段同一路径。
- 专用服务端隔离：公共包不引用任何客户端类（静态审查逐个文件检查）；状态包用 `DistExecutor` 交给客户端，和 `AflNetwork` 其他包同一写法；模型、轮廓、客户端逻辑都标了 `Dist.CLIENT`。
- 开发命令和诊断仍在 `src/dev`，`build.gradle` 继续排除整个 `dev` 包。

## I. 性能优化与缓存生命周期

相对 P1 原型的改动：
- **大碰撞形状**：P1 让 6 种方块的所有状态都报“大碰撞形状”，全世界每次实体碰撞都要多查外圈的这些方块（地下的石头是大头）。V1 改在 `BlockCollisions` 的调用点：只在实体包围盒正下方那一层、只对这些平滑方块成立（2026-10-12 起 9 种，T 节），其余外圈完全原版。离线物理在这条新规则下重跑：上坡、下坡、站立、斜走、64 条起伏路线、脚边挖放后抬升，结果和 P1 完全一致。
- **形状钩子**：P1 用可取消的注入，每次调用都新建回调对象；V1 改为在调用点重定向，不分配对象。不是平滑方块的只做一次比较（`TerrainSmooth#ground`，9 种）。
- **快速拒绝**：区块构建时先看上方一格是不是空气或植物，被埋住的块一次读就结束；其次看地表表，不是本列自然地表就结束。碰撞查询直接看地表表。地下石头、矿洞墙壁都在这里结束，不会进入坡面计算。
- **坡面缓存**：
  - 只缓存自然地表块，按区块存（每区块最多 4096 条，超了清空）；
  - 本侧主线程使用，不加锁；
  - 方块变化只清附近区块，不再像 P1 那样一改就清全世界的缓存；
  - 区块卸载时随区块释放，不会无限增长；
  - 功能开关或手动刷新时用版本号整体作废；
  - 不跨世界复用，因为数据挂在各世界自己的区块对象上。
- **地表表**：每区块 256 个整数（1 KB）。客户端在区块载入时生成（区块构建线程要读）；服务端在第一次查询时生成，没有实体碰撞的区块不花这份钱。
- **四边形缓存**：每个包装模型最多 32 768 条，超了清空。
- **离线数字**：规则每个自然地表块约 458 ns（合成世界，每次都重新扫地表；游戏里地表从表里读，但每格是一次 `getBlockState`）。
- **没有测的（要实机）**：区块构建帧时间（16 / 24 / 32 区块视距，森林 / 平原 / 丘陵）、服务端每 tick 用时、GC。`/afl dev terrain_smooth status` 会报两侧的查询数、缓存命中率、单次计算耗时、区块地表生成耗时，以及区块构建里自然地表块的数量和耗时。

## J. 高速、建筑和河岸

- **建筑**：墙、地板、地下室、基础、停车场、机器基础都是非天然方块，或者在人工方块下面。它们本身不平滑，旁边的天然地表在交界处钉成硬边。
- **Highway R4**：路面 Mesh、路基、`graded_turf` 都不在 9 种平滑方块里。路基里的原版泥土、石头、砾石压在路面和 `graded_turf` 下面，不是自然地表，不平滑。自然地形接到 `graded_turf` 处钉成硬边；R4 的几何、碰撞、施工日志、回滚都没动。
- **河岸**：水列让共享角点钉住，岸边保持整格高；沙、砾石岸坡只在 1 格高差处平滑。P1 的砾石河岸问题是服务端缓存不失效（已修），离线用真实河岸地形复测，沙、砾石比原版更好走。

## K. Forge 配置开关

- **文件**：每个世界的 `serverconfig/apocalypse_firstlight-terrain.toml`，`[terrain_smooth]` 下的 `terrainSmoothEnabled = true`（默认开）。
- **旧存档**：第一次用新版本打开时自动生成这个文件，默认开，不用重建世界。
- **读取时机**：服务器启动时读一次；运行中改文件不会生效，避免两侧中途不一致，重开世界后生效。
- **范围**：只对主世界生效，下界和末地不平滑。
- **关闭**：世界数据完全不变，因为 V1 不写任何方块或区块数据。关闭后外观、碰撞、选中恢复原版；客户端收到状态时整体重建网格，旧网格不会残留；碰撞两侧同时回到原版，不会产生回拉。
- **开发命令**（只在开发环境）：`/afl dev terrain_smooth on|off` 只改本次会话，不改配置；`collision on|off` 切换碰撞；另有 `status`、`probe`、`refresh`、`ao on|off`。

## L. 离线验证

- `node tools/terrain-smooth/check.mjs`：
  - 静态审查：
    - 正式 mixin 配置列出全部 5 个 mixin，其中服务端钩子确实指向 `ServerLevel#onBlockStateChange`；
    - 没有残留的开发 mixin 配置和启动参数；
    - JAR 排除 `dev` 包、保留正式包；
    - 公共包不引用客户端类，正式代码不引用开发类；
    - 配置和状态包已注册。
  - 规则：直坡；角点一致；无缝；2 / 4 / 8 碰撞盒；编辑影响范围（含屋顶）；地下洞穴；树冠；屋顶；炸坑（重建标记覆盖、共用边、悬空下的侧面，见 Q 节）；透光邻格（树干、树桩、原木、透光方块旁的侧面，见 S 节）；关闭和未加载区块。
  - 结果：全部通过。
- `node tools/terrain-smooth/physics.mjs`（真实碰撞代码 + 正式版底层大形状规则）：上坡、冲刺、下坡、斜走、站立、三种脚边编辑、64 条起伏路线，全部通过。
  - V1.1 加了第 4 部分“生物”：僵尸追逐，按原版 `MoveControl` 的跳跃判断和 `getGroundY`。结果见 R 节。
- `physics.mjs world`（`AFL_R4_01` 测试区，树干单独分类后重新导出）：
  - 草、泥土、沙、砾石在 B 下都比原版走得远、停顿少，进水比例和原版相同；
  - 3645 个沙 / 砾石站立点里，站立期间抬升 0 次；会移动的 10 个点都是落在树干或台阶边上、被原版推出逻辑推下去（测试放人方式的问题，P1 时已确认）。
- **没能离线验证的**：两侧在真实服务端上的一致性（离线只有一份世界）、渲染、光影、地图、性能。

## M. 文件

- **新增，正式代码**：`terrainsmooth/`（`SmoothRules`、`SmoothShapes`、`SmoothChunkData`、`SmoothChunkHolder`、`TerrainSmooth`、`TerrainSmoothConfig`、`TerrainSmoothStatePacket`）；`terrainsmooth/client/`（`TerrainSmoothClient`、`SmoothSurfaceModel`、`SinkPlantModel`、`SmoothModels`、`SmoothOutline`）；`mixin/TerrainSmooth*Mixin` 5 个。
- **修改**：`apocalypse_firstlight.mixins.json`、`AflNetwork`、`ApocalypseFirstLight`（注册配置）、`build.gradle`（删开发 mixin 参数和排除行）。
- **V1.1**：
  - 新增 `mixin/TerrainSmoothMoveControlMixin`、`mixin/TerrainSmoothPathNavigationMixin`；
  - `TerrainSmooth` 加 `standingTop`；
  - `apocalypse_firstlight.mixins.json` 加两项；
  - `tools/terrain-smooth/PhysicsCheck.java` 加僵尸追逐；`check.mjs` 的静态审查加这两个 mixin。
- **透明面修复**（2026-10-12，S 节）：
  - `TerrainSmoothClient` 加 `underSides`；
  - `SmoothSurfaceModel` 补画侧面带以下的部分（模型数据 `UNDER`，缓存键重排：几何 0-47 位、渲染层 48-50、光影包 51、补画 52-63）；
  - `tools/terrain-smooth/SmoothCheck.java` 加 `seeThroughNeighbours`。
- **开发包**：`SmoothCommands` 重写；新增 `SmoothDevClient`；`SmoothProbe` 改用正式接口。删除已迁移的 8 个类、3 个开发 mixin 和开发 mixin 配置。
- **工具**：`tools/terrain-smooth/` 下 `check.mjs`、`SmoothCheck.java`、`PhysicsCheck.java`、`physics.mjs`、`export-area.mjs`。

## N. 尚未实测的风险

1. **性能**：全世界区块构建时，每个地表方块都多一次模型数据调用（被埋住的块一次读就结束）。还需要实机测 16 / 24 / 32 区块视距的帧时间、区块加载速度、服务端 tick 时间和 GC。
2. **Embeddium 和光影下的区块构建**：数据流和 P0 / P1 相同（区块模型路径），但现在覆盖全世界，林下、雪地、沙滩、河岸要实机看。
3. **高度图依赖**：自然地表依赖 `WORLD_SURFACE` 高度图。高度图异常的区块（例如被外部工具改过）会按高度图判断。
4. **生物**：寻路仍按整格（P1 已知）；刷怪检查在“高出本格”的坡段可能被挡（刷怪空间被坡面占了一点）。要实机看刷怪是否明显变少。
   - **实机确认（作者，2026-10-11）**：僵尸追人时，每到斜坡都跳着走。**下一轮要解决**。
   - 初步推断（未验证）：原版寻路的路径点在整数格高上，生物在坡上实际站在半格高度；往上走时，下一个路径点比脚下高出超过跨步高度（僵尸 0.6），移动控制就会让它跳。
   - 下一轮先在离线行走物理（`tools/terrain-smooth/physics.mjs`）里复现，再决定改寻路的地面高度，还是改移动控制的跳跃判断；所有生物都要看，不只僵尸。
   - **已离线复现，已修**（V1.1，R 节）：上面的推断只对了一半。
     - 真正的原因：原版两处都用了坡块的**最高角**：路径点的目标高度，和移动控制里“脚下方块比脚高”的判断。
     - 两处都改成取生物站立处的坡顶高度。
     - 作者实机 **PASS**（2026-10-11）。
   - 刷怪在坡段是否变少，仍未看。
5. **雪层**：雪层仍按原版整格画，坡上会浮空或陷入（P0 已知）。
6. **专用服务器**：没有跑过。代码上已隔离客户端类，但要实际启动一次确认。
7. **其他模组的 mixin**：如果有别的模组重定向同样的调用点，会冲突报错，不会静默失效。当前开发环境里没有这类模组。
8. **只平滑 9 种方块**（设计如此）：草方块、泥土、砂土、沙、砾石、石头，2026-10-12 起加上灰化土、泥、黏土（T 节）。其余自然地表方块保持原版整格：
   - 雪块、砂岩等原版群系方块。

   原记录：作者在 Mesh Trees P1 的白松林截图里看到灰化土台阶（2026-10-11），当时灰化土、泥、黏土都不平滑。

## O. 最短实机验收

1. `.\gradlew.bat runClient`，**新建一个 AFL 世界**（不输入任何开发命令），再打开一次旧存档 `AFL_R4_01`。
2. **自然地表**：平原和丘陵的 1 格台阶应自动是 B 缓坡；上坡、下坡、横穿、斜走都不跳；树下照样平滑；河岸沙、砾石上走、挖、放都不被拉回。
3. **地下**：挖一个矿道、找一个洞穴，地面应全是方块。
4. **建筑**：Fuel Stop A1 的地板、墙、停车场外观不变，草地和路缘、建筑交界是硬边。
5. **Highway R4**：路面和 `graded_turf` 边坡正常，自然地面和边坡交界合理。
6. **Sundial**：开光影，没有黑面、缺贴图；Xaero 小地图颜色正常；按一次 F3+T。
7. **关闭**：退出世界，把该世界 `serverconfig/apocalypse_firstlight-terrain.toml` 改成 `terrainSmoothEnabled = false`，再进：地形恢复原版方块，碰撞和选中原版，没有回拉。
8. **性能**：分别在 16 / 24 / 32 区块视距下飞一段森林、平原、丘陵，看帧率和区块加载；执行 `/afl dev terrain_smooth status` 记下数字。

## R. V1.1：生物在坡上跳着走（原因与修复）

- **现象**（作者实机，2026-10-11）：僵尸追人时，每到平滑过的斜坡都跳着走。
- **离线复现**（`tools/terrain-smooth/PhysicsCheck.java` 第 4 部分）：
  - 按原版 1.20.1 的 `MoveControl#tick` 跳跃判断模拟僵尸（速度 0.23）；
  - 目标高度按 `PathNavigation#getGroundY` → `WalkNodeEvaluator.getFloorLevel` 计算；
  - 在 1:2 的平滑山坡上，上坡跳 3 次、下坡跳 1 次；64 条起伏路线共跳 433 次。和实机一致。
- **根因**：原版有两处读坡块碰撞形状的最高点（`VoxelShape#max(Y)`），也就是坡的最高角：
  1. **目标高度**：路径点的目标 Y = 脚下方块的碰撞顶 = 最高角。生物站在坡的低处时，这个高度比脚高出超过跨步高度（0.6），原版就跳。
  2. **脚下判断**：移动控制里，“脚所在方块有碰撞、且脚低于它的最高点”也会让生物跳。站在坡上时，脚总低于最高角。
- **修法**（只对 Terrain Smooth 正在平滑的自然地表块生效，其余原版不变）：
  - `TerrainSmooth.standingTop(block, getter, pos, fx, fz)`：给定生物在格内的水平位置，返回坡在那一点的顶高（和碰撞同一个坡面形状）；不是平滑块时返回 NaN。
  - `TerrainSmoothPathNavigationMixin`：`getGroundY` 取路径点所在格中心的坡顶，不再取最高角。
  - `TerrainSmoothMoveControlMixin`：跳跃判断里，脚所在方块的“顶”取生物所在位置的坡顶。
  - 真正的 1 格台阶，以及不平滑的方块（建筑、树、未知区块），照旧会跳。
  - 寻路网格本身仍按整格（路径区域里平滑返回“无”），所以路线怎么选和原版一样，只是不再在坡上跳。
- **离线结果**（`physics.mjs`）：
  - 上坡：原版跳 3 次 → 修后 0 次；
  - 下坡：1 → 0；
  - 同一座山换成原版方块台阶时，修后仍跳 6 次（台阶该跳）；
  - 64 条起伏路线：433 → 0，卡住的时间窗 0，62/64 条追出 15 格以上。
- **覆盖范围**：所有用原版 `MoveControl` 和 `PathNavigation#getGroundY` 的地面生物（僵尸、骷髅、苦力怕、村民、动物等）。自己重写了移动控制的生物（史莱姆、飞行和水生生物）不受影响，它们本来也不走这条路。
- **状态**：代码完成、离线通过、编译通过；作者实机（2026-10-11，`AFL_TREE_FOREST_01`）测试僵尸寻路 **PASS**。验收方法：生存模式下 `/summon zombie`，在平滑山坡上让它追，看上下坡还跳不跳（[Mesh Trees P1 L 节](../rendering/mesh_trees_p1_forest.md)第 4 步同时看树林）。

## Q. 实机问题：TNT 炸坑后坑壁透明窟窿（原因与修复）

- **现象**（作者截图，2026-10-11）：
  - 坑壁上有方块大小的透明“窗”，坑边有三角形的透明缺口，透过去能看到地形内部后面的天空和雾；
  - 在旁边挖一块方块后，那一处就好了。
- **排查**（离线，`check.mjs` 新增的 `explosions` 检查：40 个随机炸坑，按客户端 `Explosion#finalizeExplosion` 的随机顺序逐块删除，地表表逐块更新，模拟原版和本模组的重建标记）：
  - **不是**重建漏标：炸坑后外观变化的 9510 块全部在被标记的区段里。
  - **不是**共用边对不上：同一高度相邻两块在共用边上全部吻合。
  - Embeddium 的重建流程（标记时先作废区段拷贝；提交任务时不会吞掉待重建标记）和它的拷贝范围（区段外 2 格）也都核对过，没有问题。
- **根因**：坡块的侧面只画一条“侧面带”，从邻列的地表高度画到自己的顶面；侧面带以下的部分默认被邻列挡住，因为正常地形里邻列从下往上都是实心的。TNT 会把坑边掏空，留下**下面是空气的顶块**（悬空）。邻列顶比本块高、本块这一层却是空洞时，这部分侧面就没人画，成了透明窟窿。旁边挖一块会改变附近的结构，窟窿随之消失，所以看起来像“没刷新”。离线检查在 40 个炸坑里测到 175 处。
- **修复**（`SmoothRules`）：读邻列时顺带记下“它在本块这一层是否实心”。邻列顶比本块所在格高、这一层却不是实心时，本块那一侧的侧面从底部画起。
  - 只会多画，不会少画；多出的部分要么正好补上空洞，要么藏在实心方块之间。
  - 碰撞只用坡顶角点，不受影响；规则读的格子没超出原来的范围，挖放后的刷新范围不变。
  - 离线：炸坑漏画 175 → 0、缝 0；正常地形的数字和修复前完全相同（6367 块平滑），行走物理全部通过。

## P. 正式发布 JAR 仍需执行的验证

- `.\gradlew.bat build`（含 `reobfJar`），检查：
  - JAR 里有 `terrainsmooth/` 和 5 个 mixin 类；
  - 没有 `dev/` 包；
  - 清单 `MixinConfigs` 只列 `apocalypse_firstlight.mixins.json`；
  - refmap 里有本文 H 节的条目；
  - 混淆后的 mixin 类里影子字段已改成 SRG 名。
- 用打好的 JAR 放进**非开发环境**的 Forge 1.20.1-47.4.22 客户端，启动一次，确认 mixin 全部应用、地形平滑生效。
- 用同一个 JAR 启动一次**专用服务器**，客户端连上走一段坡，确认没有客户端类加载错误、没有回拉。
- 以上都没做，发布 JAR **未验证**。

## S. 实机问题：树根、原木和放置方块旁边的透明面（2026-10-12，原因与修复）

- **现象**（作者在 Mesh 森林里跑图时发现，见 [mesh_trees_p4_formal U1 节](../rendering/mesh_trees_p4_formal.md)）：
  - 树根周围、平放的原木旁边，以及玩家在坡上放方块的地方，偶尔出现方块大小的透明窗；
  - 都在坡块和不变形的格子交界处。
- **记录时的推测被测量否定**：
  - 当时推测：硬边方块朝坡块的面被剔掉，而坡面比格顶低，露出的部分没人画。
  - 离线检查（`check.mjs` 已有的 `seams`，以及下面新场景里的同一项）测到 0 处：规则在人工方块、树干旁边把共用的角点钉在格顶，坡块在共用边上始终是满高。
- **根因**（离线测量确认）：
  - 坡块的侧面只画一条"侧面带"，侧面带以下交给同一层的邻格去挡，前提是规则认为那个邻格是实心的；
  - 规则里的实心是"自然地表 / 其他方块 / 树木"三类（`SmoothRules`），只看方块类别，不看形状；
  - 很多这类方块挡不住旁边的面：
    - Mesh 树的树根、树干、树桩（遮挡形状为空）；
    - AFL 原木（十字形遮挡）；
    - 玻璃、台阶、栅栏、火把等不满格的方块。
  - 坡块旁边是这种邻格时，侧面带以下谁也不画，就成了透明窗。和 Q 节是同一类问题：Q 节的邻格是空洞，这次的邻格是"实心但透光"。
  - 树根放在地面上一格，旁边高一格的地面是同层的坡块，所以森林里的树根边上很常见。
- **离线测量**：`check.mjs` 新增 `see-through neighbours` 检查：
  - 40 个起伏地形场景，在第一个空格放 Mesh 树（树根 + 树干）、树桩、横放三格的原木、透光方块，再放满格方块做对照；
  - 修复前共 2014 处透明侧面：
    - 树干 / 树根旁 743；
    - 树桩旁 253；
    - 横放原木旁 501；
    - 玻璃、台阶、火把类旁 517；
    - 满格方块旁 0（对照）。
- **修复**（只改渲染，`SmoothRules` 和碰撞都不动）：
  - `TerrainSmoothClient#underSides` 判断每个坡块：哪些侧面的侧面带以下有内容，并且同一层邻格是规则里的实心、但不是满格不透明方块（`isSolidRender` 为假）；
  - `SmoothSurfaceModel` 为这些侧面补画侧面带以下的部分（只到本格高度，贴图接着侧面带往下）。
  - 补画的面放在该方向的剔除列表里：之后若在那里放上满格方块，原版和 Embeddium 照常剔掉它。
  - 光照：AFL 原木会完全挡光，按原版平光照，剔除列表里的面取邻格亮度，会变成全黑。所以补画的面烘焙了坡顶上方格子的亮度。Forge 和 Embeddium 都对烘焙亮度和计算亮度取最大值，补画的面不会比坡顶更暗。
  - 普通地形不受影响：邻格都是满格的天然方块，不补画，模型缓存的键也和原来一样。
- **修复后（离线）**：
  - 透明侧面 2014 → 0；
  - 普通地形补画 0 处、缝 0 处；
  - 原有检查（直坡、角点、无缝、碰撞、编辑范围、洞穴、树冠、屋顶、炸坑）结果和修复前相同。
- **状态**：代码完成、离线检查通过、`compileJava --offline` 通过；作者实机 **PASS**（2026-10-12）。
- **实机验收方法**：
  - 回到截图里的地点：树根旁、横放原木旁；
  - 在坡上放玻璃、台阶、火把各一个，看旁边的坡面上还有没有透明窗；
  - 再看原木旁补画的那一块，不应比周围的坡面暗。

## T. 灰化土、泥、黏土加入平滑（2026-10-12）

用户在木材建筑方块 V1 那一轮顺带要求："地表平滑覆盖灰化土 / 泥 / 黏土一起做了"。

状态：代码完成；离线 `check.mjs`、`physics.mjs` 通过；`compileJava --offline` 通过；用户 2026-10-12 实机 **PASS**（整轮验收：木材建筑方块 V1、砍树耗耐力、倒树砸伤、灰化土 / 泥 / 黏土平滑）。

- **为什么**：
  - AFL 群系里这三种是大面积的自然地表：
    - 灰化土：铁杉谷地、混合中生林；
    - 泥：沿海沼泽林、湿草原、潮汐沼泽；
    - 黏土：溪床。
  - 以前它们保持整格，旁边的草和泥土是坡，接起来就是台阶（作者 2026-10-11 在白松林里看到的灰化土台阶，N 节第 8 条）。
- **改动**：
  - `TerrainSmooth.GROUND` 和 `ground()` 加上 `PODZOL`、`MUD`、`CLAY`，共 9 种；
  - 自然地表识别、坡面几何、模型包装（`SmoothModels`）、选中轮廓、底层大碰撞形状的判断都跟着这个名单走，B 方案的算法不变。
- **泥的碰撞**：
  - 原版泥的碰撞顶只有 14/16，生物会陷进去一点；
  - 平滑后泥的碰撞是坡面碰撞柱整体下移 `SmoothShapes.MUD_SINK` = 2/16（`SmoothShapes#sunk`，单独缓存），低到没有的柱子就没有碰撞；
  - 外观、选中框仍是完整坡面，和原版泥"看着满格、踩着下陷"一致；
  - 站立高度（`standingTop`，寻路用）和本地玩家抬升（`lift`）用同一个下陷后的碰撞（`TerrainSmooth#collision`），所以生物在泥坡上不会每步被抬起再落下。
- **离线**：
  - `tools/terrain-smooth/export-area.mjs` 的天然方块编码加上灰化土 7、泥 8、黏土 9；
  - `PhysicsCheck.java` 加泥（碰撞顶 14/16 的原版泥块，和下陷后的坡面碰撞），抬升检查也按泥的碰撞算；
  - 新的第 2b 部分"泥坡行走"（1:2 坡，上下各走一遍）：

    | 方向 | 高差 | 距离 | 推出 | 停顿窗口 | 抬升 |
    |---|---|---|---|---|---|
    | 上坡 | +6.00 | 25.6 | 0 | 0 | 0 |
    | 下坡 | −6.00 | 31.6 | 0 | 0 | 0 |

  - 第一次跑时抬升 58 次：检查里的抬升还按普通坡面碰撞算，和游戏里泥的碰撞对不上。改成和游戏同一套之后是 0。
  - `check.mjs`、`physics.mjs` 全部通过。
- **文件**：
  - `terrainsmooth/TerrainSmooth.java`、`terrainsmooth/SmoothShapes.java`；
  - 只改注释：`terrainsmooth/client/SmoothModels.java`、`mixin/TerrainSmoothBlockCollisionsMixin.java`、`mixin/TerrainSmoothBlockStateMixin.java`；
  - `tools/terrain-smooth/export-area.mjs`、`tools/terrain-smooth/PhysicsCheck.java`。
- **实机验收**：
  - 铁杉谷地、混合中生林的灰化土和草地交界处，不再有台阶；
  - 沼泽、湿草原的泥坡上走，会陷一点，不卡、不被抬起；
  - 溪床的黏土坡；
  - 在三种方块上挖和放，看坡面是否局部刷新。
