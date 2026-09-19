# Rural Generator Audit V1（2026-09-13，静态只读）

现行冲突更新：已接入 [Highway ↔ Rural Spatial Conflict V1](highway_rural_spatial_conflict_v1.md)。本文下方无 Highway 避让、RURAL_HIGHWAY_INTEGRATION=NONE 等描述均为历史审计快照，已由规划期整体 reservation 避让替代；其他 POI 集成仍未实现。新实现仅编译通过，未补实机验证。

现行道路更新：已接入 [Rural Road Framework V1 Core](rural_road_framework_v1.md)。下文固定碎石路、无 graph、事后 driveway 等结论为历史审计快照；新计划已有类型化路网、路肩/过渡、保存的 access 与计划内占位。六资产 legacy 选择仍在使用；本次仅编译，未补做实机验证。

后续状态：本页保留 WG-06 前的只读审查快照。WG-06 已冻结 24 个 Natural legacy 计划案例；WG-07/07.1 后八资产 metadata 与七个机械验证 socket 已发布，Legacy 配方仍只有旧六资产。两个 `_02` 的数据来自用户人工视觉 QA，四向游戏实测仍未执行。Natural 输出与旧 driveway 行为未变。当前边界见 [WG-06 说明](rural_legacy_regression_wg06.md) 和 [WG-07 说明](rural_metadata_recipe_migration_wg07.md)；下文原始审查证据不回写为新实机验收。

## 1. Executive Summary

当前 `apocalypse_firstlight:rural` 是 **Vanilla Structure/StructureSet 自然生成**，并保留独立的 **开发命令 `/afl rural`**。两条路径共用六栋 NBT、`RuralStructurePool`、`RuralLayoutPlanner`、部分地形及农田代码，但不是同一套选择/提交逻辑。自然路径使用 40/20 chunk random spread，按世界 seed 选择四档规模、计划道路和农田，再由一件覆盖预留区的 `StructurePiece` 按 chunk 回放。命令路径使用固定 128×128 预留区、加权抽取与预提交校验；不能把它的保护视作自然生成的保证。

关键审查结果：自然路径的池是 Java 硬编码，`Definition.weight/maxCount` 在自然选择中没有按命令路径的语义执行；自然计划没有调用现成的 `RuralFoundationSupport.evaluate`；仅在自身计划内查道路/建筑/农田重叠，未见与 Highway、City 或其他 POI 的共享占位；自然回放限制在 chunk，但宽预留区和逐 chunk 遍历使性能风险为 **MEDIUM**（大规模/高频日志时可升高）。本轮只做静态代码、资源、Git 历史审查及 NBT 解码，未新建世界或执行自然生成回归。

## 2. Code / Resource Inventory

`RURAL_CODE_FILES`：

- 注册/入口：`src/main/java/com/antaurora/apofirstlight/ApocalypseFirstLight.java`；`src/dev/java/com/antaurora/apofirstlight/worldgen/rural/RuralNaturalWorldgen.java`、`RuralNaturalStructure.java`、`RuralNaturalPiece.java`；`src/dev/java/com/antaurora/apofirstlight/dev/AflDevCommands.java`。
- 计划/池：同一 `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/` 下的 `RuralNaturalGenerator.java`、`RuralGenerator.java`、`RuralLayoutPlanner.java`、`RuralStructurePool.java`、`RuralScaleTier.java`、`RuralPlan.java`。
- 地形/农田：同目录下的 `RuralTerrainSource.java`、`RuralTerrainSampler.java`、`RuralTerrainProbeCache.java`、`RuralTerrainAdapter.java`、`RuralFoundationSupport.java`、`RuralFarmPlanner.java`、`RuralFarmPlot.java`、`FarmPlotCommitJournal.java`。
- 邻接但**不是 Rural 链路**：`src/dev/java/com/antaurora/apofirstlight/dev/SettlementPrototype.java`、`SettlementSurfaceSampler.java`；`src/main/java/com/antaurora/apofirstlight/world/biome/StartupSettlementProtection.java`、`StartupPlainsEnclave.java`；近地表水相关 `src/main/java/com/antaurora/apofirstlight/mixin/NoiseChunkScorchedAquiferMixin.java`。Highway 仅作交叉引用检查，未审其内部算法。

`RURAL_RESOURCE_FILES`：

- `src/main/resources/data/apocalypse_firstlight/worldgen/structure_set/rural.json`、`worldgen/structure/rural.json`、`tags/worldgen/biome/has_structure/rural.json`。
- `src/main/resources/data/apocalypse_firstlight/structures/` 下六份正式 NBT：`rural_farmhouse_01.nbt`、`rural_barn_large_01.nbt`、`rural_house_small_01.nbt`、`rural_storage_small_01.nbt`、`rural_grain_silo_01.nbt`、`rural_water_tower_01.nbt`。`suburban_house_01.nbt` 存在，但**不在 Rural 池**。农田和道路是代码生成，非 NBT。

`RURAL_DOC_FILES`：此前无专门描述当前 Rural 自然算法的 `docs/worldgen/*rural*.md`。相关背景是 `docs/worldgen/small_city_building_authoring_v1.md`（明确 City 尚未迁移 Rural）、`docs/worldgen/startup_enclave_bunker_regression_fix_v1.md`（仅提到 Rural 自然生成仍执行），以及 `docs/03 - 制作清单.md`、`docs/04 - 注意事项 重要内容.md`（任务/回归要求，不是现行参数说明）。本报告是新增审查记录。

构建边界：`build.gradle` 把 `src/dev/java` 也加入 main 编译，但发布 jar 只排除包路径 `com/antaurora/apofirstlight/dev/**`；Rural 类位于 `.../worldgen/rural/**`，且从主 Mod 类注册，故不能仅因源目录名叫 `src/dev` 就断言发布版不含 Rural。命令类处于被排除的 `.../dev/**` 包。

## 3. Entry & Placement Flow

`ENTRY_POINT` = 主 Mod 构造器注册 `RuralNaturalWorldgen` 的 StructureType/PieceType；数据包 StructureSet 使用 Vanilla `minecraft:random_spread`，Structure 为 `apocalypse_firstlight:rural`、step=`surface_structures`。不是每 chunk Forge event、tick 或 biome modifier。另有仅开发命令 `/afl rural plan|generate [x y z]` 直接调用 `RuralGenerator`。

```text
StructureSet random_spread(40,20,salt=1374512467)
  -> RuralNaturalStructure.findGenerationPoint(candidate chunk middle)
  -> RuralNaturalGenerator.plan(seed, noise-column terrain, six NBT)
  -> site/tier/road/lot/farm checks -> Vanilla validBiome check
  -> RuralNaturalPiece(serialized plan, reservation XZ × full build-height)
  -> postProcess per intersecting chunk
  -> RuralGenerator.generateNaturalChunk(chunkBox)
  -> road + driveways -> lot earthwork + template -> farm earthwork + cells

DEV /afl rural generate -> RuralGenerator.plan(ServerLevel)
  -> separate weighted/forced pool path -> preCommitValidate
  -> road + driveways -> earthwork -> full NBT placement -> farms
```

`DIMENSION_GATE` = 自然 Structure 类没有显式 Overworld 判断；有效生物群系标签仅含 `minecraft:plains`、`apocalypse_firstlight:fallout_barrens`，通常使它实际上只在含这些 biome 的维度通过。开发命令代码未见维度硬门。`BIOME_GATE` = Vanilla 的 Structure `validBiome` 对 stub 的**一个** biome 执行检查，不是整个 56–128 格预留区 biome 扫描。`SPAWN_DISTANCE_RULE` = Rural 没有独立出生距离或 `StartupSettlementProtection` 判定。Plains/Fallout 缓冲通过改写 biome **间接**影响候选合法性；`SettlementPrototype` 的专用保护不传递给 Rural。

`PLACEMENT_GRID` = random spread region；`SPACING` = 40 chunks（名义 640 blocks）；`SEPARATION` = 20 chunks（名义 320 blocks）；`ATTEMPT_FREQUENCY` = 每个结构候选 region 最多一个候选 chunk，候选必须通过规划和 biome，不是每 chunk 概率掷骰。`BASE_PROBABILITY` = 无额外生成概率字段；StructureSet 唯一结构权重 1。`DETERMINISTIC_BY_SEED` = YES，salt/seed/中心坐标确定候选、档位、道路和分配；重启同配置稳定。配置/NBT/算法变更会改变尚未生成区的结果。四档权重 40/30/22/8 是**档位选择**，不是 StructureSet 生成概率。

## 4. Rural Structure Pool

NBT 尺寸由本轮只读解码正式文件得出，顺序为 X×Y×Z；数值不是外部 metadata。全部默认正面/`frontDirection=SOUTH`、可由 `Rotation.values()` 对齐四个水平道路方向，`Mirror.NONE`；没有逐建筑 allowed-rotations 字段。`min_count` 下表按**自然路径**实际必需角色/档位说明，不表示通用 metadata。所有 ID、role、weight、maxCount、groundAnchorOffsetY 来源均为 **HARDCODED** 的 `RuralStructurePool`；尺寸来源为 **NBT**。

| ID（省略 namespace） | NBT 尺寸 | Role | weight / maxCount（Java 声明） | 自然路径实际最低/唯一性 | anchor Y offset | BE |
| --- | --- | --- | --- | --- | --- | ---: |
| rural_farmhouse_01 | 20×17×14 | FARMHOUSE | 0 / 1 | 非 isolated 档指定候选；自然逻辑不保证它一定成功，整体仅要求住宅 | 0 | 49 |
| rural_barn_large_01 | 21×14×33 | AGRICULTURAL_LARGE | 0 / 1 | Farmstead/Full 必须成功；Cluster 要任一农业建筑；Isolated 可无 | 0 | 34 |
| rural_house_small_01 | 17×13×11 | RESIDENTIAL | 100 / 3 | Full 必须有非 farmhouse 住宅；自然路径未显式执行 maxCount | 1 | 37 |
| rural_storage_small_01 | 13×11×17 | AGRICULTURAL_UTILITY | 60 / 1 | 0；可重复性仅受候选/重叠限制 | 0 | 23 |
| rural_grain_silo_01 | 16×23×15 | AGRICULTURAL_UTILITY | 50 / 1 | 0；可重复性仅受候选/重叠限制 | 1 | 4 |
| rural_water_tower_01 | 13×31×13 | LANDMARK | 25 / 1 | 0；可重复性仅受候选/重叠限制 | 0 | 87 |

`weight/maxCount` 在**开发命令**的 `weightedPick/availableFor` 执行；自然生成的 `definitionFor` 按角色筛选后用 seed 与 index 对列表取模，没读取这些权重或计数上限。自然的 `addRequired` 只为特定候选预先排入 farmhouse/barn；未对失败进行同 ID 的全局“必须重试到成功”。六份 NBT 含方块实体；自然 `template.placeInWorld` 的旋转/BE 兼容需真实游戏内测试，静态审查不能保证每种多方块资产在四向都正确。当前六份 NBT 的 occupied 坐标均未越过各自 `size`；个别模板留有空白边（例如 farmhouse 占用 X 0..18 而 size X=20），因此实际碰撞按整个 NBT 框更保守。`suburban_house_01` 不被加载到该池；没有独立 Small Warehouse 或 Utility/Shed NBT，`rural_storage_small_01` 是现有小仓储角色。

四档 `RuralScaleTier`：Isolated 1 栋/1 田/56 格预留/18×3 主路；Farmstead 2–4 栋/1–2 田/72 格/32×3 主路；Cluster 4–6 栋/1–3 田/96 格/56×5 主路；Full 6–8 栋/1–3 田/128 格/80×5 主路加 40×5 分支。Cluster 仅当 `(seed & 1)==0` 才有 24×3 分支。道路尺寸为代码常量。

## 5. Footprint / Rotation / Anchor

`FOOTPRINT_SOURCE` = `StructureTemplate.getBoundingBox(StructurePlaceSettings(rotation, Mirror.NONE), origin)`，由 NBT `size` 及 Vanilla 旋转计算，非手写建筑宽深；田地另由程序化 `RuralFarmPlot.bounds`。建筑占用内的 porch、台阶、突出块若已在 NBT 内会被包含，超出 NBT `size` 的东西不会；真实资源解码未见越界方块。四份 NBT 存有显式 `minecraft:air`（farmhouse 652、house 305、storage 775、silo 822 格），自然 `placeInWorld` 未设置 `STRUCTURE_VOID`/air 排除器；因此建筑内空腔可能主动清除原地形或后续已放块，这不是纯空白元数据。地下 Y 尺寸在模板包围盒中，但 Rural 的 `inside/intersects2d` 只比较 XZ，不校验 reservation 的垂直区间；自然 Piece 外框覆盖完整世界高度。

`ROTATED_BOUNDS_CORRECT` = 按 Vanilla 模板 API 计算，90°/270°宽深交换由 API 负责；未做本轮四向实机验收。`ANCHOR_CONTRACT` = 候选是模板放置 `origin` 的 X/Z，而非“建筑正面中央”；Y 为九点足迹高度中位数减 `groundAnchorOffsetY`（自然），命令路径取最大样本高度减 offset。旋转后仍使用相同候选 origin，实际包围盒可能向负方向展开；边界判定依据旋转后的 box。`OFF_BY_ONE_RISK` = 预留区 `center-half .. center+half-1` 为含端点 56/72/96/128；道路长度也在长轴终点减 1，而偶数道路宽 `width/2` 两侧会变成 width+1，当前道路宽均为奇数。没有发现明确的越界 off-by-one，但命令路径对图纸前端/道路接点的语义仍属约定，不是模板 metadata。

`ROTATION_SUPPORT` = 四个水平旋转，`Mirror.NONE`。`StructurePlaceSettings` 处理朝向方块、楼梯及常规结构 BE 的模板变换；没有 Rural 专属多方块配对修复。当前六份 Rural 模板均有 BE，且 City 当前的商用双开门、双格设备**不在本池**；未来共享注册表时必须做四向主/副块与 BE 内容回归。

## 6. Layout / Roads

`RuralLayoutPlanner` 按主道路方向和固定偏移产生 farmhouse、barn、utility、residential、flex、landmark 候选。Barn 使用模板尺寸与道路包围盒寻找外侧锚点；其他候选多为硬编码离路距离（18/30 等）。路先**规划**，命令提交和自然回放也先**写**主路/支路，再接每栋建筑前框中点到最近道路单元的 3 格宽碎石 driveway，随后地形/模板/农田。主路和分支均 `GRAVEL`，农田自带路格和围栏/门/灌溉。Cluster 的可选分支与 Full 的 T 型主支路是不同布局；没有路口实体或 Highway 接口。

房屋正面通过 `rotationFor(templateFront, roadFacing)` 朝向候选指定道路侧。`ROAD_SOCKET_SYSTEM = NOT_IMPLEMENTED`：没有门精确坐标/入口 socket metadata，driveway 用 `frontMidpoint(bounds, roadFacing)` 推导；规划碰撞只覆盖建筑包围盒与道路，driveway 是事后路径，**未见 driveway 对第三栋建筑/田地的预留碰撞测试**，因此存在穿插风险。`ROAD_WIDTH`、候选偏移和路形均 Java 常量。

## 7. Collision / Spacing

`COLLISION_TEST` = 计划内旋转后模板 XZ box 与道路/已接纳 lot 进行二维扩张相交；田地另查道路、建筑及既有田地单元。`PADDING/BUILDING_PADDING` = 建筑对建筑及道路 `LOT_MARGIN=2`；`intersects2d(a,b,margin)` 只扩张待测的 `a`，因此在单轴分离时需至少隔 **2 个空方块** 才不被拒绝；农田对结构/道路另有 3 格 margin。`CLUSTER_SPACING` = StructureSet 40/20 chunk random spread 的候选约束，**不是整个最大 128×128 预留区与邻 cluster 的包围盒碰撞**。两个候选起点有 Vanilla 间距，但未见对所有地形环/driveway 的跨系统占位登记。

`CROSS_SYSTEM_COLLISION` = Rural 对 Highway、Bunker、SettlementPrototype、未来 City 的独立放置数据无显式查询/避让；Vanilla 自身的其他结构规则不能代替一个 AFL 共享占位图。`RURAL_HIGHWAY_INTEGRATION = NONE`（本轮仅检查 Rural 引用与 Highway 注册标签，未审 Highway 内部）。

## 8. Terrain Adaptation

自然路径 `HEIGHT_SAMPLE_METHOD`：中心+四正交+四对角+四远端共 **13 柱**做站点检查，取 p10/median/p90，水比例阈值 22%、robust relief≤18、steep ratio≤0.20；center 无效/有水使 waterRatio=1。每栋 lot 的旋转包围盒取四角、边中点、中心 **9 柱**；任一无效或水样本拒绝；`MAX_SLOPE/MAX_LOT_RELIEF=6`，以九点 median 为底，最大 cut/fill 各≤3。命令路径则在 128×128 每 8 格网格取样（256 柱），并以 lot 样本最高点定底；两条链路不等价。

`TERRAIN_FLATTENING` = 不平整整个预留区。自然回放会按 chunk 在 lot 足迹内做≤3 格 dirt 填/可切块挖，外侧 2 格混合环，并清理 lot/农田植被；模板支撑掩码来自 NBT 首层实心块，必要时向下补≤6 格 cobblestone。`CUT_FILL` = 局部存在；命令路径较保守，以填土和清植被为主。`WATER_REJECTION` = 站点比例阈值、lot 与 farm 样本拒水；`LIQUID_REJECTION` = ServerLevel 采样检查任何非空 fluid，但 generation-time `NoiseColumn` 分支仅把水/冰列为 water-like，没有专用工业废液/所有 mod 液体分类。`CLIFF_REJECTION` = 高差阈值与失效样本的间接限制，无悬崖/洞穴几何专用分类。

近地表水抑制仅在 Scorched Lands aquifer mixin，Rural 本身不调用；scorched 不在 Rural biome tag。Plains/Fallout 缓冲通过 biome 间接影响准入，Rural 不读取 `StartupSettlementProtection`。`fallout_barrens` 明确在准入标签，可生成于焦土邻近或辐射区（取决于单点 biome/地形）；没有距离污染、工业废液或辐射的专用限制。自然道路只按 chunk 裁剪并查询当前表面高度，未对道路的每格水/坡度做规划时同等严格的检查。农田会程序化放置灌溉水，这与“近地表水抑制”不是同一机制，若末世美术要求干田，应单独决策。

## 9. Retry / Failure

自然流程：站点失败→无 `GenerationStub`；缺 Barn 模板或已选模板→拒绝整 cluster；每个 `Spec` 最多 **9 个锚点偏移**（0、±4、±8），超出 lot 请求预算即跳过，预算依档位分别约 27/63/81/99；已通过的 lot 达目标即停。Isolated/Farmstead/Cluster 可低于目标但不能低于 tier 最少建筑数，Full 低于目标直接拒绝；须有住宅，Farmstead/Full 还须 Barn，Cluster 须农业建筑。农田按 owner 候选再用 fallback 候选，bounded 最多 **32 次**，少于每档最低田数则拒绝。没有无限循环。自然失败没有向另一个候选 region 重新滚动，也没有回滚已写 chunk：`postProcess` 捕获异常、记日志并跳过后续结果，已写的方块无法从该处理器自动恢复。

命令流程：先强制 farmhouse、barn，再按候选角色从可用池做**加权**抽取；一栋失败可换池中下一栋，已满 maxCount 不再入池。命令 `preCommitValidate` 要求最终 `lots.size()==targetBuildings`，所以 `fallbackUsed` 即使在 plan 阶段标为可用，也会在提交前被拒绝；模板缺失、资源/牌子 NBT 预检失败会停。提交阶段先写路和地形再放结构，后续失败无事务级回滚，`GenerationCrashedException` 明确记录 partial commit；手动再次运行前应检查现场，不能假定幂等。

## 10. Chunk Safety

`CROSSES_CHUNKS = YES`。自然 Piece 以预留区 XZ × 完整建造高度作为包围盒，保存完整计划，按每个相交 chunk 调 `postProcess`。道路、driveway、地形、农田有 chunkBox 裁剪；模板 `StructurePlaceSettings.setBoundingBox(chunkBox)`。计划期用 `ChunkGenerator.getBaseHeight/getBaseColumn` 的 noise column，不主动向 WorldGenRegion 请求远处 chunk；这显著降低自然路径的 far-chunk 写入风险。`FORCE_CHUNK_LOAD` = 自然路径未见强制加载；**命令路径** `inspectSite` 和 `place` 会 `getChunk` 加载邻 chunk。`CASCADING_WORLDGEN_RISK = LOW`（就自然路径的显式跨 chunk 读写而言），但宽 Piece 会在很多 chunk 被回放，性能风险另计。计划随 Piece NBT 保存；reload 后不重新计划缺失的序列化计划，现有 StructureStart/Vanilla 已生成 chunk 阻止正常重复结构放置。跨版本变更、命令手动重跑、模板异常后部分写入不在此保证内。

## 11. Performance

`PERFORMANCE_RISK = MEDIUM`。StructureSet 稀疏（40 chunk spacing），但每个候选计划有 13 站点探针、最多 256 个去重地形探针、最多约 27–99 次 lot 位置请求、最多 32 农田尝试；生成期单次地形探针会构造 noise column。每个成功 Piece 跨 56–128 格（约 4×4 至 8×8 chunk），每次 `postProcess` 都遍历道路与全部 lot/农田，再由 chunkBox 过滤，且有多个 INFO 日志。模板管理器读取六栋模板不等于每候选重新从压缩 NBT 解析；support metadata 通过资源重载清缓存，建成后缓存。命令路径有更大的同步 `getChunk`/完整建筑写入风险，但不是自然结构生成热路径。这里是代码风险评级，未做 TPS/采样压测。

## 12. Hardcoded vs Data-Driven

`DATA-DRIVEN`：StructureSet 的 40/20/salt、Structure biome tag/step、六栋 NBT 尺寸/方块/BE；**没有**统一 Rural 建筑 metadata JSON。`HARDCODED`：六个建筑 ID、角色、front、weight、maxCount、groundAnchorOffsetY，四档规模权重/建筑及农田目标、道路宽长、候选离路偏移、lot margin、地形阈值、重试预算、田地形状/作物/灌溉选择。自然路径的权重字段被声明但选择算法未按权重使用，`maxCount` 同样未执行。`DATA_DRIVEN_LEVEL = LOW`；`HARDCODED_BUILDING_IDS = YES`；`HARDCODED_FOOTPRINTS = NO`（建筑几何取 NBT，布局槽位与预留尺度是硬编码）；`HARDCODED_WEIGHTS = YES`。`RESPONSIBILITY_OVERLAP = YES`：`RuralGenerator` 同时承担命令规划、预检、完整提交以及自然 chunk 回放；`RuralNaturalGenerator` 另有一套站点/选择逻辑。

## 13. Historical Bug Findings

必须把开发用 `SettlementPrototype` 与当前 Rural 分开。Git 提交 `6eb1a8c` 在 `SettlementPrototype` 引入 `VALIDATOR_VERSION=V1_3_SURFACE_VALIDATION`，移除早期硬编码 `MIN_STARTUP_DISTANCE=600`，将锚点/路面采样从 `WORLD_SURFACE` 转为 `MOTION_BLOCKING_NO_LEAVES`/`SettlementSurfaceSampler`，加入完整 footprint 的 startup ecology 交叉检查、开发区块预加载、无效表面样本/轻微流体容限和分阶段拒绝诊断。此前确有可能因固定出生半径或误把植被/水面当真实地面而拒绝，但仓库提交说明为“Restore stable startup ecology and bunker worldgen baseline”，未找到能把某次“不能生成”报告唯一归因到一条检查的测试日志/issue。`HISTORICAL_ROOT_CAUSE = NOT CONFIRMED`；可确认的是**修复范围及旧逻辑差异**，不能声称那次故障被特定条件单独导致。后续 `8d3b525` 又改为 `V2_ROAD_CONSTRUCTION_AWARE`，加入道路施工约束；当前原型仅 dev 命令，不是 Rural Natural 的 fallback。旧的 V1.3 validator/固定 600 半径已不在当前文件，Rural 自身仍保留两套不同实现及硬编码候选。

## 14. KEEP / REWORK / REPLACE

- **KEEP**：Vanilla StructureSet/StructurePiece 的正常生命周期与 chunkBox 裁剪；NBT `getBoundingBox` + `StructurePlaceSettings` 旋转；确定性 seed；计划期 noise-column 地形读取；模板 support mask 缓存与资源重载清理（需验收后复用）。
- **REWORK**：把统一建筑记录、角色/权重/maxCount、真实 front/ground anchor、旋转许可、道路接点、地形阈值和跨系统占位抽为可测试元数据/接口；统一命令与自然路径的选择语义，保留各自调度；对已有 `RuralFoundationSupport.evaluate` 是否纳入自然预检做专项实验。
- **REPLACE**：按 `definitions.get(2..5)` 索引和候选固定偏移绑定建筑；声明但自然路径不消费的 weight/maxCount 语义；把 driveway 当最后一步且无整体碰撞预留的做法。不要替换已验证的 NBT 资源或在本轮改生成算法。

## 15. Unified Framework Migration Dependencies

建议最小共享结构记录：`id`/NBT 路径、category/role、NBT 派生 footprint+height、模板原点到地面的 anchor offset、front、allowed rotations、weight、maxCount/unique、道路入口 socket 或可明确回退的入口点、地形容差。`minCount` 应放在**聚落配方/布局层**，不是强加给每个 NBT；全局 spacing/separation 应在 StructureSet/placement policy，而非每栋资产。共享 API 依赖顺序：NBT+元数据校验 → 旋转 footprint/入口变换 → 统一地形样本与支撑评估 → 带 margin 的占位登记 → 受限 chunk writer/失败观测 → Rural 与 City 各自接入。

Rural/City 可共享 NBT 注册、元数据、旋转/包围盒、道路入口、地形检查、碰撞及结构放置；Rural 的不规则农田、分散 driveway、四档 road/cluster 规划与 City 的城市网格、密度、分区、楼高/街区约束应独立。Highway 需要一个明确的**占位/接驳接口**，但不把 Highway 的网络规划并入 Rural。`Unified Framework ≠ Same Generator`。

## 16. Open Questions / Documentation Match

1. 四向实机摆放、含 BE 和潜在多方块资产的旋转是否都通过？本轮未运行。
2. 自然路径 foundation mask `evaluate` 未被调用，模板局部悬空在何种地形出现？需专项生成样本。
3. driveway/农田路径是否可能穿过已接纳建筑或其他结构？静态检查显示未整体预留，需要世界回归确认。
4. `fallout_barrens` 被允许是否符合 Rural 设计；工业废液与崖边的预期是什么？
5. 自然与命令路径的池权重/maxCount 不同是暂时现状还是有意设计？
6. Highway/Rural/City 共享占位及接驳尚不存在，先完成 Highway 独立 Audit 再定接口。

`DOC_MATCH = NO`（现有资料不完整，并非已发现文字自相矛盾）：现有 `small_city_building_authoring_v1.md` 对“City 未迁移 Rural”与当前代码一致；`startup_enclave_bunker_regression_fix_v1.md` 只记录一次 Rural 自然生成执行，不足以覆盖当前所有四档。`DOC_STALE_SECTIONS`：本轮未发现直接描述 Rural 参数却过期的 `docs/worldgen` 段落；主要问题是**此前缺少专门 Rural 现行行为文档**。本报告不修改原行为文档，也不把静态审查冒充运行验证。

---

`RURAL_GENERATOR_AUDIT_V1 = COMPLETE`；`AUDIT_ONLY = YES`；`JAVA_MODIFIED = NO`；`NBT_MODIFIED = NO`；`WORLDGEN_BEHAVIOR_MODIFIED = NO`。`MAIN_GENERATOR_CLASS = RuralNaturalStructure -> RuralNaturalGenerator -> RuralNaturalPiece/RuralGenerator.generateNaturalChunk`；`STRUCTURE_POOL_COUNT = 6`；`ROAD_SOCKET_SYSTEM = NOT_IMPLEMENTED`；`RURAL_HIGHWAY_INTEGRATION = NONE`；`HISTORICAL_SETTLEMENT_GENERATION_BUG = PARTIALLY_CONFIRMED`（修复差异可确认，唯一根因不可确认）；`RECOMMENDED_NEXT_STEP = Highway Generator Audit`。

审查摘要字段：`PLACEMENT_MODEL = Vanilla random_spread StructureSet + chunk-bounded StructurePiece`；`SPACING = 40 chunks`；`SEPARATION = 20 chunks`；`ATTEMPT_FREQUENCY = one candidate chunk per region`；`FOOTPRINT_SOURCE = NBT StructureTemplate.getBoundingBox`；`ANCHOR_CONTRACT = template origin XZ + median terrain Y - per-definition offset`；`ROAD_SYSTEM = procedural main road / optional branch / driveways / farm paths`；`COLLISION_SYSTEM = in-plan 2D boxes, no shared cross-system registry`；`BUILDING_PADDING = 2`；`CLUSTER_SPACING = 40/20 chunk placement only`；`HEIGHT_SAMPLE_METHOD = 13 site samples + 9 per lot`；`TERRAIN_FLATTENING = lot-local cut/fill`；`MAX_SLOPE_RULE = site p90-p10<=18, lot max-min<=6, per-lot cut/fill<=3`；`RETRY_SYSTEM = bounded spec offsets and farm fallback`；`MAX_ATTEMPTS = 9 offsets/spec, 32 farm candidates`；`CROSSES_CHUNKS = YES`；`CASCADING_WORLDGEN_RISK = LOW`；`PERFORMANCE_RISK = MEDIUM`；`DATA_DRIVEN_LEVEL = LOW`；`HARDCODED_BUILDING_IDS = YES`；`HARDCODED_FOOTPRINTS = NO`；`HARDCODED_WEIGHTS = YES`。
