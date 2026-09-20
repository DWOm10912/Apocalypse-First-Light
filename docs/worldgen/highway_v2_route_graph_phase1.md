# Highway V2 — Route Graph Phase 1

状态：Phase 1有限路由已接入。后续 [Phase 2A — Strategic Branch Framework](highway_v2_strategic_branch_phase2a.md) 已增加可选支线数据/API，默认 `build/forSeed` 仍只有原两条主干，无实际支线。下文Phase 1验证记录为历史记录，不代表Phase 2A实机验收。

## 当前路线真相

`src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwayRouteGraph.java` 是唯一宏观路线 Source of Truth。旧 `PrimaryHighwayNetwork` 和无调用的 `HighwayGenerationContext` 已删除，2200±300 无限周期不再参与自然生成或 claim。

- `build(seed)` 只读取 MacroGeography，返回不可变列表与 record；不读 chunk、block、fluid 或 biome，不依赖生成顺序。
- `forSeed(seed)` 仅提供最多 16 个 seed 的有界 memoization；清空/淘汰后可重建同样结果，cache 不是规划真相。
- 默认恰好两条 route、两个有限 edge：role `NATIONAL_TRUNK_A` 为 `EAST_WEST`，`NATIONAL_TRUNK_B` 为 `NORTH_SOUTH`；二者 routeType 均为 `NATIONAL_TRUNK`。
- 每条 edge 包含 routeId、id、role、startNode/endNode、orientation、fixedCoordinate、闭区间 startStation/endStation、junctionNodeId；`bounds(halfWidth)` 返回有限半开 XZ 包络，`query(bounds, halfWidth)` 返回相交 edge。
- 四个 TERMINUS 和一个共享 INTERSECTION，共五个 node。交点通过 edge 的内部 junctionNodeId 表达；未拆成导航边，也不代表有匝道或转向连通。
- ID 为 `national_trunk_a/main`、`national_trunk_b/main`，交点 ID 为 `national_intersection`；它们在 seed/world scope 内稳定。

## 核心位置与终点

seed hash 派生 X/Z 的符号及各 640～1023 的绝对偏移，交点距原点约 905～1447 格。校验核心 ±96 格探针均为 MAIN_NATION / MAINLAND / LAND、离海岸至少 256 格；失败时按固定顺序尝试四个 `(±768, ±768)`，全部失败则明确报错，不随机重试。

两个轴向偏移都超过出生保护半径 384 加施工半宽 32。因此不是仅交点避开出生点，而是两条主干及施工包络均不穿出生保护区。

从交点向四个轴向逐格查询 MacroGeography，每个 station 检查全部 65 格宽的横断面。仅 MAIN_NATION / MAINLAND / LAND 继续；遇到第一处 COAST 或其他非稳定主岛陆地即停止，取最后完整稳定横断面向内退 32 格，再向内对齐 8 格采样网格。搜索上限为 MacroGeography.outerOceanRadius，不是无限搜索。

这是一种保守 Phase 1 策略：宏观内海岸也会终止，不绕行、不跨越、不在对岸续建；OPEN_OCEAN、附属岛均没有批准 edge。宏观 LAND 内的实际局部河湖/小水体仍由原工程链决定是否 VIADUCT。终点当前只是道路截断语义，不含港口、回车场或新桥型。

## 自然生成与有限写入

`NaturalHighwayGenerationAdapter` 查询同一 `HighwayRouteGraph.forSeed(seed)`。常规查询沿横向 ±20 格；既有 hygiene 邻区查询外扩 16 格。远离所有 edge 时不构造 terrain sampler/profile。

`CorridorEngineeringSegment` 保留全局 station 的 256 格 core 与每侧 192 格 halo，但 core、plan 起终点均裁到 edge 的 start/end。工程版本为 2；anchor/segment cache key 使用 routeId + edgeId + station/segment，node key 使用 intersectionId + graphVersion，外层仍按世界身份隔离。

继续复用 `HighwayPlan.linear`、`HighwayTerrainSampler`、`HighwayProfile`、`HighwayCorridor`、`HighwayRenderer`、`ChunkOwnedHighwayWriter`。高程锚点仍允许读取路线外的地形作为既有平滑输入；这不是批准施工。自然 corridor 的 ROW/cut 包络在真实端点裁断；`FiniteRouteHighwayWriter` 再以 edge 包络限制最终放置和清理，防止 halo、桥墩纵向偏移等越过终点铺路。既有 final hygiene 的植被组件清理规则不改，不用于延伸路面。

共享交点继续用 `InterstateInterchangeNode.fromGraph` 处理上下层：最小抬升选上层，平局按 seed+交点 ID 选择；原 11 格路面间距、96 格接近段及下层先施工保持。没有匝道。

23 格横断面、材料、标线、路肩、cut/fill、桥墩和支撑视觉均未调整；`HighwayBridgeSpanResolver` 与局部 VIADUCT 判定保留。没有在 renderer 内用 OPEN_OCEAN 临时 return 掩盖无限路线。

## Claim 与 Rural

`HighwaySpatialClaimProvider` 与自然生成直接取得同一 graph 的 Edge 对象，不复制路线算法。claim 的 XZ 为 `edge.bounds(32)`，纵向不外扩，端点外无 Highway claim；Y 显式为当前冻结 AFL Overworld 的 `[-64, 320)`（对应 `data/minecraft/worldgen/noise_settings/overworld.json`），不再为空/无限柱。未来若改变维度高度，必须同步该契约。

owner 保持 `apocalypse_firstlight:primary_highway`，版本改为 `highway_spatial_v2_graph_1`，ID 包含 seed + routeId + edgeId。HARD INFRASTRUCTURE 优先级 70、Highway exclusionMargin=0 保持；Rural 自有 12 格安全距仍只裁决一次。claim 是有限批准路线的保守施工包络，不是每个实际写入块的精确占用；biome gate/施工失败可能使部分批准路线未落地。

Rural 本身、通用 BoundsXZ/SpatialClaim/ClaimConflictResolver 未修改。旧 StructureStart/Piece 和已生成道路不迁移、不删除；新旧生成边界可能不连续，不能据此声称旧存档已修复。

## 现有调试入口

未新增命令。`/afl highway_network info` 显示 graph 版本、所有 edges、交点与端点，Phase 2A增加 routeType/routeId/edgeId/purpose/parent attachment；默认仍显示2条主干。`nearest` 使用有限主干线段距离，`node` 查询唯一国家主干共享交点；`perf` 保持原统计。未添加图像导出或 debug renderer。

## 未实现 / 验证边界

通用STRATEGIC_BRANCH框架已由Phase 2A实现，但SATELLITE_ISLAND实际路由、CrossingCandidate 消费、Sea Bridge、Gentle Turn、斜线/Bezier 正式生成、车辆导航、港口均未实现。Terrain V2 density、Macro Geography topology、Rural、Worldgen Hygiene、Vanilla structures 和 surface lava suppression 未在Highway阶段修改。

首次 seed 查询需扫描有限主干的完整横断面；有界 cache 仅减少重复工作。本轮不跑 benchmark，不声明首查询耗时或世界性能已验收。确定性、两条有限轴向主干、共享交点、spawn 避让、端点约束与共享 claim 来源已静态核对；真实世界海岸外观、施工接缝和生成顺序下的最终方块内容仍待用户验收。

未 runClient、clean、GameTest、多 seed 测试、大规模生成、截图、commit/push；未进入 Phase 2。

构建记录：项目 `.gradle-user` 离线缓存缺少 JEI 等依赖，首次调用未进入 Java 编译。改用现有用户缓存后，`gradlew.bat --gradle-user-home C:/Users/willi/.gradle compileJava --offline` 返回 `BUILD SUCCESSFUL`（26 秒）；有既有弃用/unchecked 警告，未改依赖与构建配置。`git diff --check` 通过。
