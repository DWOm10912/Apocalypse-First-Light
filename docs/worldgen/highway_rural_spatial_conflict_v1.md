# Highway ↔ Rural Spatial Conflict V1

现行更新：[Highway V2 Route Graph Phase 1](highway_v2_route_graph_phase1.md) 已将 Highway 与其 claim 同步改为两条有限主干，在第一处宏观海岸前终止，不跨 OPEN_OCEAN；Sea Bridge 未实现。[Phase 2A](highway_v2_strategic_branch_phase2a.md) 增加通用支线API及接收不可变graph的同源claim查询，STRATEGIC_BRANCH复用同一edge.bounds(32)、有限Y与ID规则；默认graph没有支线，原主干claim不变。[Macro Geography V1](terrain_v2_macro_geography_v1.md) 的 Rural MAINLAND/海岸128格缓冲 gate（16格有界探针）保持。Rural planner、建筑、农业与冲突裁决算法未改。

状态：已接入自然 Rural Structure 候选入口；compileJava 离线编译通过。未运行客户端、GameTest、多 seed 回归或性能测试，实机验收由用户完成。

## 接入与优先级

RuralNaturalStructure.findGenerationPoint 在地形/模板/建筑规划前，通过 RuralNaturalGenerator.reservationFor(seed, center) 复用现有 tier 和完整 56/72/96/128 格 reservation。RuralHighwayConflict.blocker 将 Vanilla 闭区间 BoundingBox 转为半开 BoundsXZ，构造 HARD SITE claim，与 Highway claims 交给 ClaimConflictResolver.resolveWinner。现有固定优先级为 Highway HARD INFRASTRUCTURE=70 > Rural HARD SITE=50。

冲突返回 Optional.empty()，不创建 Piece、不重选址；debug 原因为 HIGHWAY_SPATIAL_CONFLICT，包含 reservation 与阻挡 claim ID。不冲突继续原有规划。本适配器针对现有 Overworld 自然入口；Structure.GenerationContext 不提供维度 key，因此使用 Overworld 约定，不声明适配自定义维度移植。开发命令及既有 Piece 回放不经过此检查。

## Seed 查询与边界

- 新增 src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwaySpatialClaimProvider.java，输入 seed、dimension、BoundsXZ，返回通用 SpatialClaim；非 Overworld 返回空。
- 与自然 renderer 同样查询 HighwayRouteGraph.forSeed(seed).query，消费相同有限 Edge/routeId，不复制 route math，不读已生成 chunk、asphalt、StructureStart 或现场地形。
- Highway 自己定义 constructionEnvelopeHalfWidth()=32：中心线两侧各 32 格，离散硬占用带宽 65 格。它是包含道路、工程及清理余量的保守 V1 规划包络，不是 renderer 精确最大范围的证明；工程范围扩大时需同步审查。
- claim 沿路线方向只覆盖 edge 的闭区间 startStation～endStation，端点外不延伸；Y 显式为当前 Overworld 的 [-64,320)。内容不因查询窗口变化。DeterministicClaimId 包含 owner、dimension、owner-local version（highway_spatial_v2_graph_1）和 seed/routeId/edgeId。
- RuralHighwayConflict.SAFETY_BUFFER=12。查询先扩 12 以发现所有竞争路线，裁决只应用一次 margin；边界空隙小于 12 拒绝，恰好 12 允许。
- 保守预留完整预测路线，包括 biome 不允许 Highway 落地或施工失败的部分；可能额外拒绝 Rural，但不会因 Highway 尚未生成而漏判。

该冲突判定只依赖 seed/候选边界，无先到先得登记；不可变 graph 的有界缓存仅作优化，因此规划真相不依赖 chunk 探索顺序。不据此宣称既有 worldgen 已经过全流程顺序一致性实测。

## Spatial 复用与兼容

复用 BoundsXZ、SpatialClaim、SpatialClaimType/Strength、ClaimPriorityPolicy、DeterministicClaimId、ClaimConflictResolver/ClaimConflict；不新造 Reservation Core，不修改通用类型。固定两个代码内 V1 producer 使用纯 pair resolver，没有启用 WG-05 profile/activation/persistence 流水线。LimitedClaimIndex 未接入：路线可随机访问预测，不需要有限 accepted-site 镜像作为世界真相。未来 City/Small Town/Large POI 可复用通用 claim/provider 模式，但各自候选完整性与版本接入尚未实现。

Highway 旧无限 spacing 已被有限 graph 取代，renderer/局部桥隧工程保持复用；无 reroute、exit、匝道或 Rural 接高速主车道。Rural spacing/separation、tier 权重、biome、terrain 阈值、道路几何、建筑、农田、怪物生成不变；冲突拒绝会降低高速附近的接受率。

仅影响后续新规划候选。旧 StructureStart/Piece（包括已规划但未完全回放的部分）、已生成区域不回溯修复；不在 Highway writer 跳过 Rural 方块制造断路。

## 验证

V1 历史验证：gradlew.bat --offline --gradle-user-home .gradle-user compileJava 曾通过。Phase 1 当前验证：项目缓存依赖缺失，使用现有 C:/Users/willi/.gradle 缓存离线 compileJava 后 BUILD SUCCESSFUL；未改依赖。无资源改动，未运行 processResources。未 runClient、clean、GameTest、大量测试、commit/push；不等同实机验收。
