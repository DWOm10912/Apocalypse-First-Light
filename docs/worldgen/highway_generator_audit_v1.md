# Highway Generator Audit V1（2026-09-13，静态审查）

历史快照提示（Highway V2 Phase 1）：下文无限走廊、2200±300、旧盐与 corridor-index cache identity 均已过时，不代表现行实现。现行 Source of Truth 为 [HighwayRouteGraph](highway_v2_route_graph_phase1.md)：两条有限轴向主干、共享非原点交点、spawn 避让、海岸终止，renderer/claim 共用 edge。`PrimaryHighwayNetwork` 与 `HighwayGenerationContext` 已删除。下文保留用于追溯原审计；当前行为以 Phase 1 文档及源码为准，未声称实机验收。

现行冲突更新：已接入 [Highway ↔ Rural Spatial Conflict V1](highway_rural_spatial_conflict_v1.md)。本文下方 SHARED_SPATIAL_CLAIMS/Highway–Rural INTEGRATION=NONE、无排除区等结论为历史快照：现已由 seed 预测 provider 发布 ±32 格施工 claim，Rural 整体 reservation 带 12 格间距主动拒绝冲突。Highway writer 不查询 Rural、路线不变、无新出口或互通。其他 POI 避让仍未接入；仅编译通过。

范围：现行自然生成、同仓库开发命令及相关资源；只读。本文的“已实现”指代码存在且位于所述调用链，不等于已在本轮游戏内验收。本轮未修改 Java、NBT、worldgen JSON、既有文档或世界。

## 1. Executive Summary

现行高速公路不是 Vanilla Structure/StructureSet，也不是按上一区块出口逐步延伸；它是 Forge biome modifier 挂载的 `top_layer_modification` Feature。每个装饰区块独立按世界 seed 查询全局轴向南北/东西走廊，离走廊远时快速退出，命中时获取共享的确定性工程段，**仅向目标区块**写道路。南北与东西走廊相交时规划立交高差；这只是上下跨越，**没有车辆转接匝道**。

核心结论：路线和高程计算可随机访问，运行态有有界缓存与接缝计数器，但没有持久化路网/SavedData，也没有 Highway 与 Rural/City/其他 POI 的共享空间占用。主线自然生成只轴向直线，宽 23 格；开发 `/afl highway_test` 可测试八方位线段，但不能代表自然网络。桥、桥墩、隧道都在自然工程链中，工程失败通常是局部跳过/记录，而非整段回滚。性能评级 **MEDIUM**：无路区块便宜，命中区块会为 640 格带 halo 的段构造/遍历大量格元、快照和清理；未做本轮 TPS/视觉实测。

## 2. Code / Resource Inventory

`HIGHWAY_CODE_FILES`：

- 正式入口/注册：`src/main/java/com/antaurora/apofirstlight/registry/AflFeatures.java`、`world/feature/PrimaryHighwayFeature.java`；`worldgen/highway/NaturalHighwayGenerationAdapter.java`、`PrimaryHighwayNetwork.java`。
- 正式工程/缓存/观测：同一 `src/main/java/com/antaurora/apofirstlight/worldgen/highway/` 下 `CorridorEngineeringSegment.java`、`HighwayTerrainSampler.java`、`HighwayGenerationContext.java`、`HighwayNodeConstraints.java`、`InterstateInterchangeNode.java`、`NaturalHighwayCacheManager.java`、`NaturalHighwaySeamValidator.java`、`NaturalHighwayRuntimeStats.java`、`HighwayBlockWriter.java`、`ChunkOwnedHighwayWriter.java`、`HighwayNetworkCommand.java`。
- 自然链**也会调用**的 `src/dev/java/com/antaurora/apofirstlight/worldgen/highway/` 类：`HighwayPlan.java`、`HighwayProfile.java`、`HighwayBridgeSpanResolver.java`、`HighwayTunnelSpanResolver.java`、`HighwayTunnelGeometry.java`、`HighwayCorridor.java`、`HighwayTerrainMode.java`、`HighwayPalette.java`、`HighwayRenderer.java`、`HighwayPreConstructionSnapshot.java`、`HighwayFinalHygienePass.java`、`HighwayHygieneWriter.java`、`HighwayRenderStats.java`。
- 开发测试/旁支：同目录 `HighwayDebugCommand.java`、`HighwayEditSession.java`、`HighwayContinuityValidator.java`、`HighwayInterchangeRenderer.java`；开发命令注册于 `src/dev/java/com/antaurora/apofirstlight/dev/AflDevCommands.java`。`HighwayGenerationContext.create` 与 `HighwayInterchangeRenderer.render` 未见从**现行自然入口**调用；不能把其中的坡道/曲线当成自然生成能力。
- 路面块：`src/main/java/com/antaurora/apofirstlight/block/RoadMarkingBlock.java`、`RoadMarkingStepConnectorBlock.java`；`registry/AflBlocks.java`。

`HIGHWAY_RESOURCE_FILES`：`src/main/resources/data/apocalypse_firstlight/forge/biome_modifier/primary_highway.json`、`worldgen/configured_feature/primary_highway.json`、`worldgen/placed_feature/primary_highway.json`、`tags/worldgen/biome/primary_highway_generation.json`；`assets/apocalypse_firstlight/blockstates/`、`models/block/`、`textures/block/` 下 `asphalt`、`edge_lane_white`、`edge_lane_yellow`、`white_lane_divider` 及三类 `*_step_connector` 的资源。道路/桥/隧道**无专用 NBT**。道路写入混用 AFL asphalt/标线/钢筋混凝土与 Vanilla 石头、碎石、泥土。

`HIGHWAY_DOC_FILES`：此前 `docs/worldgen/` 无独立 Highway 现行行为文档；`docs/03 - 制作清单.md` 是后续审查任务清单，`docs/worldgen/rural_generator_audit_v1.md` 提供 Rural 对照而不审 Highway 内部。本报告是新增的唯一 Highway 文档。

构建边界：`build.gradle` 将 `src/dev/java` 加入 main；发布 jar 仅排除包 `com/antaurora/apofirstlight/dev/**`。位于 `.../worldgen/highway/**` 的工程类即使源文件在 `src/dev` 也会进入主编译/发布包；`.../dev/AflDevCommands` 则属于排除包。不要按源目录名误判自然链是否存在。

## 3. Entry / Lifecycle

`ENTRY_POINT` = `AflFeatures.PRIMARY_HIGHWAY` Feature → configured/placed Feature（placement 空列表）→ Forge `add_features` biome modifier，step=`top_layer_modification`，biome tag 为 `#minecraft:is_overworld` 加 fallout barrens（Biome Region Planner V1已移除Scorched tag项；Highway代码未改）。`PrimaryHighwayFeature.place` 调 `NaturalHighwayGenerationAdapter.generate`。适用 biome 的装饰区块运行一次 Feature 回调，**不是** StructureSet 的区域候选；Feature 是否在某区块出现还受 biome modifier 与正常 Minecraft 生成生命周期控制。

```text
Biome modifier / top_layer_modification
  → PrimaryHighwayFeature.place
  → WorldGenRegion + Overworld 硬门
  → seed-based nearby NS/EW corridor + hygiene halo 快速拒绝
  → per-world cache → 256 core / 192×2 halo engineering segment
  → terrain profile → bridge/tunnel/立交约束 → corridor cells
  → 同目标区块预施工快照 → 先下层、后上层
  → ChunkOwnedHighwayWriter：清理 → 路基/路面 → 边缘/中隔/标线 → 隧道 → 桥墩
  → FinalHygienePass（只写源区块）→ runtime counters

DEV /afl highway_test [length] [N|NE|E|SE|S|SW|W|NW]
  → 玩家位置/朝向 → 显式有限线段 → 实时 ServerLevel 地形
  → 同类 profile/corridor/renderer，但不同路线、采样和 writer
  → 内存 edit ledger；/afl highway_test clear 尝试恢复
```

`PLACEMENT_LIFECYCLE` = 装饰阶段执行一次目标区块 Feature；不是 chunk-load/tick 续建，也没有靠 SavedData 的持续扩张。`DIMENSION_GATE` = `WorldGenRegion` 且 `Level.OVERWORLD`；`BIOME_GATE` = 上述 biome tag（不是道路坐标处每一格重新过滤）。正式只读 `/afl highway_network nearest|info|perf|node` 可查询状态，不放路。

## 4. Route Planning Model

`ROUTE_MODEL = E: deterministic infinite orthogonal corridors`。`PrimaryHighwayNetwork` 从世界 seed 分别给 NS 的固定 X 和 EW 的固定 Z 选初始相位，索引每 ±1 的候选由 2200 格基间距与 ±300 格成对抖动构成；相邻距离 1900–2500 格，成对平均 2200。`nearby` 根据当前 chunk 横向范围及路宽查相关走廊；没有随机起点/终点或 A*、全局可变 graph。`NODE_MODEL` = 每个 NS×EW 几何交点可生成稳定 ID `I_NS_i_EW_j` 的 `InterstateInterchangeNode`，由两条地形基准高程选择上跨走廊（相同代价由 seed 定），要求上下路面差至少 11 格，96 格接近段按每升 1 格需要 4 格行进调整。`SEGMENT_MODEL` = 每走廊按全局 station 的 `floorDiv(station,256)` 切 256 格核心段；工程取两侧各 192 格 halo。这个 segment 是**缓存/计算单元**，不是独立 NBT 结构或持久实体。

`BRANCHING = NO`；`INTERSECTIONS = grade-separated crossing, no connectivity`；`LOOPS = NO`；`DEAD_ENDS = 无规划终点`。自然主线方向只有两个正交轴，不转弯。`HighwayPlan` 有通用 Bezier 工具，`HighwayInterchangeRenderer` 有 collector/ramp 试验实现，但没有接入自然回调，也未见当前开发命令调用该 renderer；不要据此标“匝道已实现”。

## 5. Seed / Determinism

`DETERMINISTIC_BY_SEED = YES（路线/工程目标，未做本轮实机全 seed 证明）`；`HIGHWAY_SALT` = 代码常量 `NS_SALT=0x4e535f5052494d41`、`EW_SALT=0x45575f5052494d41`，不是 JSON salt。`RNG_SOURCE` = `PrimaryHighwayNetwork.mix(seed ^ salt ^ index)`、noise generator 的 `RandomState`/base height；没有持久化游走 RNG。固定 seed、worldgen 配置和代码下可随机访问同一 corridor/node/anchor。`LOAD_ORDER_DEPENDENCE` = 规划意图不依赖 chunk 顺序；**实际格元内容可能受其他 Feature/Structure 的相对写入与后续跨 chunk 植被写入影响**，本轮没有跨加载顺序矩阵证明最终外观完全一致。

## 6. Chunk Continuity

`CROSSES_CHUNKS = YES`（线路逻辑跨 chunk；每次提交不跨目标 chunk）。连续性依靠全局走廊坐标、station、512 格高程锚点、固定 256 核心段与 192 halo；相邻区块在同一核心段共享缓存，跨核心段由重叠工程窗口重算。白线虚线相位按**全局** station 的 3 格亮/6 格空（周期 9）计算。`NaturalHighwaySeamValidator` 在相邻缓存段都可用时比较边界 station、高程/模式、标线相位、桥/隧道状态和节点 ID，只**计数诊断**，不自动修复；缓存淘汰时不强制生成邻段。

`CHUNK_EDGE_HANDOFF` = 无前区块出口状态，按公式独立重建。`FORCE_CHUNK_LOAD = NO`（自然路径）；`ChunkOwnedHighwayWriter.owns` 加 `ensureCanWrite` 限制路面写目标区块，快照也只收集 owner 列；hygiene 的读写先过 own+`ensureCanWrite`，BFS 有边界与 1024 方块上限。开发命令的 `ensureChunks/getChunk` **会**加载范围内区块，不能混同自然路径。`CASCADING_WORLDGEN_RISK = LOW`，理由是无自然强载和跨区块建造写入；`BROKEN_SEGMENT_RISK = MEDIUM/UNVERIFIED`：桥/隧道模式若在 halo/缓存重算边界产生差异只会记指标；保护方块、局部写失败和邻区块后续自然装饰都可留下视觉不一致。没有本轮实机断路统计。

## 7. Road Geometry

主线 `ROAD_TOTAL_WIDTH = 23`（lateral -11..11），高架结构边缘扩到 25。横断面从左至右：外肩 2、左车行道 7、内肩 1、中隔 3、内肩 1、右车行道 7、外肩 2，共 23。`LANE_COUNT = 4（每方向 2 条的标线意图）`；`LANE_WIDTH = 每方向车行道 7 格，中心虚线占 1 格位置，两侧各约 3 格可行驶带`；`SHOULDER_WIDTH = 外 2/侧、内 1/侧`；`MEDIAN = 3 格，其中中心格上方有钢筋混凝土半砖隔挡`。

路面 `apocalypse_firstlight:asphalt`，位于 `roadY`。外白边在 lateral ±10、内黄边 ±2、白色车道虚线 ±6，标线方块在 `roadY+1`，通过 `RoadMarkingBlock.FACING` 定向；高差为 1 时三种 `*_step_connector` 在台阶转接位置放置，虚线空档不会强行补白线；超过 1 格的台阶连接跳过并计数。`MARKING_SYSTEM` = 上述六类正式 AFL 标线/step connector 注册，不是纹理贴在 asphalt 单一方块状态上。边缘由钢筋混凝土和桥缘半砖表达。

## 8. Turns / Curves

`STRAIGHT_SUPPORT = YES（自然 NS/EW）`；`DIAGONAL_SUPPORT = DEV TEST ONLY`；`CURVE_SUPPORT = 几何帮助类存在，但自然入口未接入`；`TURN_RADIUS = 自然路径不适用`；`TURN_IMPLEMENTATION = NONE`。开发 `highway_test` 可用八方位线段，`HighwayPlan.bezier` 可算曲线，然而自然规划永远建立 `HighwayPlan.linear` 轴向路段。`HighwayCorridor.addSupercover` 可覆盖像素化斜线，但 `buildRoadMarkings` 对非正交 tangent 明确跳过标线；因此开发斜线“路可画”不等于完整斜向标线能力。

## 9. Terrain / Grade

自然规划 `HEIGHT_SAMPLE_METHOD`：`HighwayTerrainSampler` 的 `ChunkGenerator.getBaseHeight(MOTION_BLOCKING_NO_LEAVES)`，每横断面 23 列排序取中位、最小、最大；两侧另在 ±12/14/16 取侧向地形，可能有 fluid 时用 `OCEAN_FLOOR_WG` 差异筛选后按需 `getBaseColumn` 检查顶层流体。纵向 Profile 每 8 格取样；路面高程是每 512 station 一处锚点、锚点用 station ±256/128/0 的五组中位横断面按 1:2:3:2:1 加权，再在锚点间线性插值，约束到世界建造高度内。节点另外覆盖局部高差。

`MAX_SLOPE`：自然路径**没有**独立硬性坡度拒绝/逐段 `clampAdjacent`；高程由 512 格锚点插值及节点每 4 格最多升 1 格的接近段形成，整数取整可能产生台阶。开发命令走 `HighwayProfile.sample(ServerLevel)` 的 9 点局部平滑及相邻 8 格样本最大 ±1 的双向夹取，是另一条规则。`VERTICAL_SMOOTHING` = 自然低频加权锚点+插值；开发局部平滑+clamp。`CUT` = 横断面低于高地面时清理道路/路权上方至预施工快照顶部，`MAX_CUT_DEPTH=20` 是常量但 **不是一条会拒绝超过 20 格的自然切土硬上限**；`FILL` = 路面下石/碎石两层，加泥土至地表/最多 7 格；`EMBANKMENT` = 局部填方/路权清理，没有完整边坡剖面。深切在满足封闭度后可升级隧道。陡崖可触发 viaduct；不重路由。

## 10. Liquids / Bridges

`HighwayProfile.mode` 在横断面液体覆盖率 ≥0.30、路高比地形中位高 ≥8，或横坡 ≥12 且切/填深 ≥8 时给 viaduct 候选。`HighwayBridgeSpanResolver` 可封闭最多两个非桥样本间隙，丢弃短于 24 格的候选，前后最多各补 8 格接近段；自然桥结构直接按已解析 Profile 建，无自然路径的实时支撑率扩展。自然 Profile 流体检测并非专门查水 tag：顶层 `FluidState` 非空即记入 waterColumns；但快速“可能液体”依赖两个 heightmap 的差异，不能声称对所有工业/mod 流体都完整识别。`INDUSTRIAL_WASTE_AWARE = NO（无专用液体 ID/标签策略；有泛流体检测）`。对河/湖/海不 reroute，而按阈值桥跨或直接 CUT/FILL/覆写；局部/薄水可能不够 30%。

`BRIDGE_SUPPORT = PARTIAL/IMPLEMENTED`：桥面 asphalt 下 3 层钢筋混凝土，高架边缘、护墙和每 32 格候选桥墩；桥墩向下最多找 128 格稳定非流体支点，找不到时警告并**跳过该墩**，不整段撤销。交汇节点强制一条路上跨，但没有匝道。`TUNNEL_SUPPORT = IMPLEMENTED`：`HighwayTunnelSpanResolver` 用岩层覆盖、侧壁、无液体/无 viaduct/无节点干涉判定；普通隧道最小 40 格、实质内部最小 24 格，深切晋升候选最小约 24 格并保留至少 16 格内部；`HighwayTunnelGeometry` 挖通道并铺钢筋混凝土衬砌、门洞。深切晋升的阈值/开口几何在类内写死。隧道不处理被保留的 BlockEntity 或不可破坏障碍的“替代路线”。

## 11. Foundation

`FOUNDATION_METHOD`：地面/CUT/FILL 模式 asphalt 下一层 stone、下一层 gravel；FILL 再以 dirt 从 `roadY-3` 填到地表或深度预算。高架模式以三层 reinforced concrete 作为桥面，间歇桥墩/承台接稳定地基。`MAX_FILL_DEPTH = 7`（普通 FILL），`MAX_PIER_SEARCH_DEPTH = 128`（独立桥墩）。并未针对每一列做“底下必须实心”的最终通过/失败门；深洞可出现局部悬空，桥墩失败也不会撤销桥面。

`VEGETATION_CLEAR` = 施工前路权与道路竖向 envelope 清理，树组件 BFS 最多 1024 格且半径 10、高差 24；末尾 `FinalHygienePass` 再清规划空气区的可替换植被及触发树组件。`TREE_CLEAR = YES（有限且保护 BE/不可破坏物；非无限全树搜索）`；`CAVE_HANDLING = 未见洞穴专用探测/补洞，普通填方 7 格/桥墩 128 格搜索是主要处理`。

## 12. Collision / Spatial Claims

`HIGHWAY_SELF_COLLISION` = 规则格网走廊间距保证同向不重叠；NS×EW 交会由 `InterstateInterchangeNode` 选择上下层，目标 chunk 先写下层再写上层。没有通用“已占用路段”数据库。`CROSS_SYSTEM_COLLISION = NONE`：Highway 自然链未查询 Rural、Bunker、SettlementPrototype、City、Radio Tower、Survivor Camp 或 Vanilla Structure 的 bounding box；仅 writer 跳过有 BlockEntity 的单格，清理函数跳过 BE/不可破坏方块。这不等价于保护整栋建筑；普通路面写入不读取共享 POI claim。`SHARED_SPATIAL_CLAIMS = NONE`；`EXCLUSION_ZONE_SYSTEM = NONE（只有本公路节点影响域/工程区域，非其他系统避让区）`。

## 13. Rural / City Relation

`HIGHWAY_RURAL_INTEGRATION = NONE`，与 Rural Audit 的反向结论相符：Highway 不知道 Rural 主路、driveway 或住宅，也不发布可连接的路侧入口。Rural 的随机分布可偶然靠近 Highway，但没有吸附、避让、接驳保证。`CITY_CONNECTION_INTERFACE = NOT_IMPLEMENTED`：当前节点只是两条干道的高差交会，没有 highway exit、服务路/城市锚点、可通车匝道或 road socket。`HighwayInterchangeRenderer` 的孤立测试工具不改变这个结论。

其他特殊段现状：主线桥/桥墩/桥缘护墙 = IMPLEMENTED；岩层隧道/portal = IMPLEMENTED；轴向立体交叉 = IMPLEMENTED（无转向连接）；ramp、普通十字转向、collector 接自然主线 = NOT_IMPLEMENTED；单独收费站/涵洞/道路终点 = 未见现行自然链实现。城市或 Radio/Camp 的未来兼容性不可从现有代码推定。

## 14. Persistence / Duplicate Guard

`PERSISTENCE_MODEL` = **无 Highway 自有 SavedData**；纯 seed/terrain 随机访问 + Minecraft 已生成区块的常规 Feature 生命周期。`SAVED_DATA = NONE`（没有 graph、segment done、node done、版本迁移存档）；工程缓存仅进程内，按 world session/维度/seed/generator/RandomState 键分开，在卸载/停服时移除，有界 LRU+single-flight。`DUPLICATE_PLACEMENT_GUARD` = 正常生成过的区块不会因为普通重进而重跑 Feature；writer 把已等于目标状态的块视为 no-op，并统计同次重复尝试。没有可跨强制重跑/数据包变更/异常中断的独立“段已完成”标记。`NaturalHighwayRuntimeStats` 是内存计数，不是生成状态。

## 15. Failure / Retry

`RETRY_SYSTEM = NONE`（自然路线不移动锚点、不寻找替代线路）；`MAX_ATTEMPTS = 每区块一次 Feature 调用，段缓存失败后下次可重建但非计划内 reroute`；`REROUTE_SUPPORT = NO`；`ROLLBACK = NO`。非 Overworld/非 WorldGenRegion 或远离路线直接返回 false。短桥/隧道候选被阈值拒绝时回普通模式；桥墩无支点则单墩 skip+warn；受 BE/不可破坏物保护的清理格可能留下障碍；`ensureCanWrite` 失败由 writer 计数并跳过。异常向上抛出并标记统计失败，已写入的路面没有事务回滚。`PARTIAL_COMMIT_RISK = YES`（局部写入失败、桥墩 skip、异常或与其他 Feature 竞争时）。开发 `HighwayEditSession` 有**会话内**原方块账本和 `clear`，但它不是自然路径 rollback，也不保留 BE NBT；进程结束即失去该内存账本。

## 16. Performance

`PERFORMANCE_RISK = MEDIUM`。Feature 对适用 biome 的区块调用，但 Tier-0 只造轻量走廊查询，离走廊远即退出，不做 RandomState/剖面。命中区块同段尽量命中每世界 LRU（工程段最多 24、height 65,536、顶层列 16,384、anchor 2,048、node 256）；失配/被驱逐时为 256+192×2≈640 格工程窗口每 8 格做 profile 横断面，生成约 640×23 的路面候选并扩出路权，之后每目标区块仍遍历整段候选，依 writer 裁掉外区块。快照用真实 `WORLD_SURFACE` 读取被拥有列，按快照顶逐块清障；隧道几何与深切判断、桥墩最多 128 格向下搜索、树 BFS 1024 格、hygiene 都可能形成局部尖峰。缓存占用、同区域多段并发与大山体切挖需要运行时实测。现有 `/afl highway_network perf` 提供快拒、cache 命中、采样、清理、写入和段构建耗时；本轮未启动客户端/服务器测量。没有自然路径的 A*/BFS 路由搜索；BFS 只用于树清理。

## 17. Hardcoded vs Data-Driven

`DATA-DRIVEN`：biome modifier、biome tag、configured/placed Feature 指向；方块/纹理资源。`HARDCODED`：两组盐、2200±300 走廊间距、256+192 段窗口、23 路宽与各 lateral 角色/标线位置、512 高程锚点、地形桥/隧道阈值、11 格交会高差、96 格接近段、桥墩间距/深度、材料 palette、清理限额。没有道路段 NBT 或可调 worldgen 配置，也没有 JSON 节点概率（交叉点由几何必然出现）。`DATA_DRIVEN_LEVEL = LOW`；`HARDCODED_ROAD_DIMENSIONS = YES`；`HARDCODED_MATERIALS = YES`；`HARDCODED_TERRAIN_RULES = YES`。`HighwayPalette` 内 `CARRIAGEWAY/SHOULDER/MEDIAN` 常量不是自然当前路面的实际写入：renderer 全断面先用 AFL asphalt；要以调用处而非常量名判定材质。

## 18. Class Responsibilities

`PrimaryHighwayNetwork` = 纯 seed→走廊/交点；`NaturalHighwayGenerationAdapter` = Feature 分流、缓存调度、下/上层排序、施工和统计；`CorridorEngineeringSegment` = 256 核心+halo 的计划/profile/corridor 聚合；`HighwayTerrainSampler` = pre-decoration 噪声地形查询；`HighwayProfile` = 路面高程/模式；`HighwayBridgeSpanResolver`、`HighwayTunnelSpanResolver`/`HighwayTunnelGeometry` = 特殊段；`HighwayCorridor` = 路面/标线/路权格元展开；`HighwayPreConstructionSnapshot` = 真正可写区块的施工前顶高；`HighwayRenderer` = 清障、路基、表面、桥隧/桥墩提交；`ChunkOwnedHighwayWriter` = 区块所有权/写入护栏；`HighwayFinalHygienePass` = 最终自然障碍清理；`NaturalHighwayCacheManager`/`RuntimeStats`/`SeamValidator` = 内存缓存、诊断；`HighwayDebugCommand`/`EditSession` = 可逆开发现场测试；`HighwayNetworkCommand` = 正式只读查询。

`RESPONSIBILITY_OVERLAP = YES`：`HighwayCorridor` 同时做格元、桥支撑判别、道路标线和隧道几何绑定；`HighwayRenderer` 同时清障/结构/桥墩/材料写入；自然与开发路径共用多数渲染但采用不同地形采样及支撑判别。分层已有雏形，不代表当前职责完全单一。

## 19. KEEP / REWORK / REPLACE

- **KEEP**：seed 可随机访问的 NS/EW 走廊定位；全局 station/虚线相位；目标区块独占 writer 与前置区域检查；段级缓存及只读 seam 指标；已实现的桥/隧道专用工程思路（复用前需实机验证）。
- **REWORK**：把 terrain query 的 pre-decoration/施工现场区别明文化；高程/流体/保护对象/POI 占位统一可审接口；对跨段连续性与局部失败提供可验收的状态/诊断契约；明确道路接驳点和跨系统避让策略，但不让 Rural 管 Highway 路线。
- **REPLACE**：不要把未接入的试验 ramp/collector 当正式节点系统；未来若引入共享占位，不能继续以“单格 BE 存在”代替整栋保护；局部施工失败静默留下半段路应有明确策略。这些是后续建议，**本轮不改**现行行为。

## 20. Rural vs Highway Shared Candidate Services

| 能力 | Rural（见 Rural Audit） | Highway（本审查） | 共享判断 |
| --- | --- | --- | --- |
| Seed/RNG | StructureSet 40/20 与 tier 选择 | 世界 seed+两盐的随机访问正交网 | 可共享 seed 上下文，**不共用布局算法** |
| Terrain query | 13 点 site/9 点 lot、局部切填 | 23 列横断面、512 格锚点及施工前快照 | 共享查询抽象/来源标注；采样策略独立 |
| Spatial claim | 计划内建筑/道路二维碰撞 | 仅自身格网/立交，无跨系统 claim | **需要**统一占位契约，现无实现 |
| Chunk safety | StructurePiece+chunkBox 回放 | Feature 每区块 owner writer | 可共享有限写入原则，保留不同生命周期 |
| Placement state | StructureStart/Piece 计划 NBT | 无自有 SavedData，依生成区块生命周期 | 共享状态语义/失败报告，持久实现各自决定 |
| Rotation | NBT `StructurePlaceSettings` 四向 | 路线轴向 tangent/标线 FACING | 工具可协调，不能把 Highway 改为模板旋转 |
| NBT placement | 六栋正式模板 | 无道路 NBT | 不应强制共享；城市/乡村用 NBT 服务 |
| Road anchor | driveway 前框推导，无 socket | 无对外连接 anchor，只有交会 ID | 后续定义接驳契约，当前两端都不具备 |
| Retry/failure | lot 偏移/田地回退，部分提交无回滚 | 无路线重试，局部 skip/无回滚 | 可共享失败类型/观测，不共用重试算法 |

## 21. Unified Framework Migration Dependencies

建议先做跨审查确认：① 定义 site/structure 与 infrastructure 各自生命周期；② 共享世界 seed、pre-decoration/施工现场地形语义及液体类别；③ 定义跨系统 XZ/Y 占位/优先级及 Highway–Rural/City 接驳锚点；④ 规定有限写入、失败/回滚或可接受部分提交与运行时指标；⑤ 再分别适配 Rural 的模板/聚落选择和 Highway 的线性网络工程。**Unified Framework ≠ Highway 必须变成 Structure Generator**。Highway 更接近 Infrastructure Layer；Rural/City/Radio/Camp 更接近 Site/Structure Layer。共同服务是接口和冲突契约，不是同一条生成调用链。本节仅列设计依赖，未编码。

## 22. Open Questions

1. 固定 seed、不同区块生成顺序、重启及缓存淘汰下，桥/隧道/标线跨 256 核心段是否实机完全一致？`NaturalHighwaySeamValidator` 目前只报告可见的相邻缓存段。
2. 其他 Feature 先后写入/邻区块树冠延迟写入时，末尾 hygiene 是否足以保持最终路权畅通？需游戏内顺序矩阵；静态不能承诺。
3. 特殊流体（工业废液）是否经 heightmap 候选与 `getBaseColumn` 准确进入 bridge 判定？没有专用类型策略。
4. 被高速经过的 Rural/City/Bunker/Radio/Camp 应由谁拥有优先级，哪些区域可接驳、哪些必须避让？现无共享 claim。
5. 桥墩找不到地基、受保护障碍或局部写失败后，何种缺口可以接受，是否需跨区块持久失败记录？当前只计数/警告。
6. `HighwayInterchangeRenderer` 的试验匝道是否有未来意图？当前未接自然生成，不能提前算正式资产。

`HIGHWAY_GENERATOR_AUDIT_V1 = COMPLETE`；`AUDIT_ONLY = YES`；`JAVA_MODIFIED = NO`；`NBT_MODIFIED = NO`；`WORLDGEN_BEHAVIOR_MODIFIED = NO`；`RECOMMENDED_NEXT_STEP = Rural + Highway Audit Cross-Review → Unified Worldgen Architecture Design`。本报告的风险级别是**静态代码风险**，未运行 GameTest、客户端、生成世界或性能基准。
