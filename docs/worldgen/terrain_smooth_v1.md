# Terrain Smooth V1：B 方案自然地表平滑正式接入

日期：2026-10-11。

状态：
- **代码完成**。
- **离线验证通过**：`node tools/terrain-smooth/check.mjs`（发布接线静态审查 + 规则检查），`node tools/terrain-smooth/physics.mjs`（合成地形行走物理），`physics.mjs world`（`AFL_R4_01` 真实地形，只出报告）。
- **`compileJava --offline` 通过**。
- **游戏实机**：作者第一次实机（2026-10-11）用 TNT 炸坑，平滑效果不错，但坑壁和坑边有透明窟窿（见 Q 节）。已修复，**修复后还没在游戏里跑过**；其余验收项（O 节）还没做完。**发布 JAR：未打包、未验证**（见 P 节）。
- **性能**：只有离线数字；游戏内 16 / 24 / 32 区块视距的帧时间和区块构建时间**没有测过**，不能说达标。

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
| `SmoothModels` | 每次模型烘焙（含 F3+T）包装 6 种地表方块和小植物的模型 |
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
  - 遇到的第一块如果是 6 种天然方块之一，就是自然地表；
  - 否则（水、任何建筑或人工方块、岩石悬垂）这一列**没有自然地表**。
- **两侧一致**：客户端和服务端都维护 `WORLD_SURFACE` 高度图（原版 CLIENT 类型的高度图，随区块包同步，方块变化时两侧同样更新）。扫描读的是同样的方块，所以结果相同。
- **一块参与平滑的条件**（`SmoothRules#geometry`）同时满足：
  - A：它是本列的自然地表块；
  - B：方块是 6 种天然方块；
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

- 5 个 mixin 写在正式配置 `apocalypse_firstlight.mixins.json` 的公共段（客户端和专用服务端都加载），`defaultRequire 1`：注入失败会直接报错，不会静默失效。
- 正式配置由 MixinGradle 写进 JAR 清单，不再依赖 `runClient` 的启动参数。开发 mixin 配置、`--mixin.config` 参数、对应的 JAR 排除行都已删除。
- refmap：编译生成的 `apocalypse_firstlight.refmap.json` 里有全部重定向和注入目标的 SRG 名。`BlockCollisions` 的两个影子字段 `pos`、`box` 在 `compileJava-mappings.tsrg` 里（`f_186395_`、`f_186392_`），由 `reobfJar` 处理，和项目已有 mixin 的影子字段同一路径。
- 专用服务端隔离：公共包不引用任何客户端类（静态审查逐个文件检查）；状态包用 `DistExecutor` 交给客户端，和 `AflNetwork` 其他包同一写法；模型、轮廓、客户端逻辑都标了 `Dist.CLIENT`。
- 开发命令和诊断仍在 `src/dev`，`build.gradle` 继续排除整个 `dev` 包。

## I. 性能优化与缓存生命周期

相对 P1 原型的改动：
- **大碰撞形状**：P1 让 6 种方块的所有状态都报“大碰撞形状”，全世界每次实体碰撞都要多查外圈的这些方块（地下的石头是大头）。V1 改在 `BlockCollisions` 的调用点：只在实体包围盒正下方那一层、只对这 6 种方块成立，其余外圈完全原版。离线物理在这条新规则下重跑：上坡、下坡、站立、斜走、64 条起伏路线、脚边挖放后抬升，结果和 P1 完全一致。
- **形状钩子**：P1 用可取消的注入，每次调用都新建回调对象；V1 改为在调用点重定向，不分配对象。非这 6 种方块只做一次比较。
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
- **Highway R4**：路面 Mesh、路基、`graded_turf` 都不在 6 种方块里。路基里的原版泥土、石头、砾石压在路面和 `graded_turf` 下面，不是自然地表，不平滑。自然地形接到 `graded_turf` 处钉成硬边；R4 的几何、碰撞、施工日志、回滚都没动。
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
  - 规则：直坡；角点一致；无缝；2 / 4 / 8 碰撞盒；编辑影响范围（含屋顶）；地下洞穴；树冠；屋顶；炸坑（重建标记覆盖、共用边、悬空下的侧面，见 Q 节）；关闭和未加载区块。
  - 结果：全部通过。
- `node tools/terrain-smooth/physics.mjs`（真实碰撞代码 + 正式版底层大形状规则）：上坡、冲刺、下坡、斜走、站立、三种脚边编辑、64 条起伏路线，全部通过。
- `physics.mjs world`（`AFL_R4_01` 测试区，树干单独分类后重新导出）：
  - 草、泥土、沙、砾石在 B 下都比原版走得远、停顿少，进水比例和原版相同；
  - 3645 个沙 / 砾石站立点里，站立期间抬升 0 次；会移动的 10 个点都是落在树干或台阶边上、被原版推出逻辑推下去（测试放人方式的问题，P1 时已确认）。
- **没能离线验证的**：两侧在真实服务端上的一致性（离线只有一份世界）、渲染、光影、地图、性能。

## M. 文件

- **新增，正式代码**：`terrainsmooth/`（`SmoothRules`、`SmoothShapes`、`SmoothChunkData`、`SmoothChunkHolder`、`TerrainSmooth`、`TerrainSmoothConfig`、`TerrainSmoothStatePacket`）；`terrainsmooth/client/`（`TerrainSmoothClient`、`SmoothSurfaceModel`、`SinkPlantModel`、`SmoothModels`、`SmoothOutline`）；`mixin/TerrainSmooth*Mixin` 5 个。
- **修改**：`apocalypse_firstlight.mixins.json`、`AflNetwork`、`ApocalypseFirstLight`（注册配置）、`build.gradle`（删开发 mixin 参数和排除行）。
- **开发包**：`SmoothCommands` 重写；新增 `SmoothDevClient`；`SmoothProbe` 改用正式接口。删除已迁移的 8 个类、3 个开发 mixin 和开发 mixin 配置。
- **工具**：`tools/terrain-smooth/` 下 `check.mjs`、`SmoothCheck.java`、`PhysicsCheck.java`、`physics.mjs`、`export-area.mjs`。

## N. 尚未实测的风险

1. **性能**：全世界区块构建时，每个地表方块都多一次模型数据调用（被埋住的块一次读就结束）。还需要实机测 16 / 24 / 32 区块视距的帧时间、区块加载速度、服务端 tick 时间和 GC。
2. **Embeddium 和光影下的区块构建**：数据流和 P0 / P1 相同（区块模型路径），但现在覆盖全世界，林下、雪地、沙滩、河岸要实机看。
3. **高度图依赖**：自然地表依赖 `WORLD_SURFACE` 高度图。高度图异常的区块（例如被外部工具改过）会按高度图判断。
4. **生物**：寻路仍按整格（P1 已知）；刷怪检查在“高出本格”的坡段可能被挡（刷怪空间被坡面占了一点）。要实机看刷怪是否明显变少。
5. **雪层**：雪层仍按原版整格画，坡上会浮空或陷入（P0 已知）。
6. **专用服务器**：没有跑过。代码上已隔离客户端类，但要实际启动一次确认。
7. **其他模组的 mixin**：如果有别的模组重定向同样的调用点，会冲突报错，不会静默失效。当前开发环境里没有这类模组。

## O. 最短实机验收

1. `.\gradlew.bat runClient`，**新建一个 AFL 世界**（不输入任何开发命令），再打开一次旧存档 `AFL_R4_01`。
2. **自然地表**：平原和丘陵的 1 格台阶应自动是 B 缓坡；上坡、下坡、横穿、斜走都不跳；树下照样平滑；河岸沙、砾石上走、挖、放都不被拉回。
3. **地下**：挖一个矿道、找一个洞穴，地面应全是方块。
4. **建筑**：Fuel Stop A1 的地板、墙、停车场外观不变，草地和路缘、建筑交界是硬边。
5. **Highway R4**：路面和 `graded_turf` 边坡正常，自然地面和边坡交界合理。
6. **Sundial**：开光影，没有黑面、缺贴图；Xaero 小地图颜色正常；按一次 F3+T。
7. **关闭**：退出世界，把该世界 `serverconfig/apocalypse_firstlight-terrain.toml` 改成 `terrainSmoothEnabled = false`，再进：地形恢复原版方块，碰撞和选中原版，没有回拉。
8. **性能**：分别在 16 / 24 / 32 区块视距下飞一段森林、平原、丘陵，看帧率和区块加载；执行 `/afl dev terrain_smooth status` 记下数字。

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
