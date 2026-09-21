# Highway V2 Phase 2A — Strategic Branch Framework

当前正式路由见 [Orthogonal Strategic Branch Routing V1](highway_v2_orthogonal_routing_v1.md)：默认卫星支线发布多个 N/E/S/W edge，显式 TURN，以及硬基础设施 TURN_RESERVED_ZONE / JUNCTION_RAMP_ZONE claims。预留区不铺转角或匝道模块；原两条 National Trunk 不变。下文旧阶段的默认图数量、无实际支线和几何 defer 状态仅为历史记录。

现行覆盖说明：[Phase 2B-2 Satellite Routing](highway_v2_satellite_routing_phase2b2.md) 已成为第一个真实consumer；默认图发布合法卫星支线和两岸edge，route现在按routeId分组，允许多edge。下文“无实际支线/未接Satellite/每route一edge”为旧阶段历史状态。ParentAttachment语义、原两主干不变，Sea Bridge仍仅预留metadata。

现行补充：[Phase 2B-1](highway_v2_diagonal_geometry_v1.md) 增加geometry重载与POLYLINE surface消费，默认仍两条主干。下文仅轴向限制对应Phase 2A旧targetX/targetZ重载；两个重载的parent仍要求轴向。新几何桥隧明确defer。末尾资源/执行状态为Phase 2A历史记录，新阶段见链接。parent/junction语义未变，Satellite Routing未实现。

状态：通用支线框架已接入，compileJava与30项纯Java契约检查通过，默认自然世界仍仅有原两条National Trunk。没有实际Strategic Branch，也没有Satellite Routing、Sea Bridge或City Connector。此结果不代表实机连接处或工程效果验收。

## 单一图真相与兼容性

`src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwayRouteGraph.java` 继续是唯一authority。`build(seed)` 的主干规划算法、salt、回退位置、海岸终止、8格对齐完全不改；原routeId `national_trunk_a/b`、edgeId `national_trunk_a/b/main`、`national_intersection`及端点ID保持。默认2 routes / 2 edges / 5 nodes。`VERSION=1`作为现有主干几何/cache契约保持，不用文档阶段号使主干cache漂移。

`RouteType`：`NATIONAL_TRUNK`、`STRATEGIC_BRANCH`、`CONNECTOR`。最后一项仅预留，不可通过本轮API构建。旧`RouteRole.NATIONAL_TRUNK_A/B`保留，增加`STRATEGIC_BRANCH` role；edge.routeType()统一映射。Phase 2A每route一个有限轴向edge，`Route`暴露routeId/type/purpose/edges/parentAttachment。用途为非空字符串metadata，不选不同材料、宽度或renderer。

graph、Route列表及Edge/Node/ParentAttachment records不可变；`forSeed`沿用16 seed同步有界cache。构建API返回新图，不修改cache或已有graph。未来规划器必须在graph build阶段统一构建/发布新图供所有消费者读取；本轮没有chunk阶段append、没有独立支线真相。

## Branch creation与精确attachment

`withStrategicBranch(key, parentRouteId, parentEdgeId, parentStation, targetX, targetZ, purpose)` 是不可变copy API。

- key必须是稳定语义键 `[a-z0-9][a-z0-9_-]*`；ID为 `strategic_branch/<key>`、edge为 `<routeId>/main`，不依赖hashCode、随机数、运行期计数或调用顺序。重复ID报错，不覆盖。
- parent必须是图内已有edge且route匹配，可为主干或已有支线；station必须在父edge闭区间内。不能引用未来edge，因而该API不会创建parent环。
- 选择保留父edge方案：`ParentAttachment(parentRouteId,parentEdgeId,parentStation,junctionNodeId)` 精确绑定世界轴向整数station，不使用坐标“附近”推断。内点节点ID为 `<parentEdgeId>/junction/<station>`，kind为BRANCH_JUNCTION；端点复用原节点，国家交点复用national_intersection。
- 分支目标为明确有限坐标，V1仅接受与父edge垂直的非零轴向直线。拒绝斜线、同轴重叠、零长度和超出受支持世界坐标域的目标；不做terrain pathfinding或设施/岛屿选择。
- Edge沿station升序存储，反方向支线的junction可位于endNode；业务起点以parentAttachment.junctionNodeId为准，业务目标为另一个端点。不得把升序startNode误认成始终从父路出发的起点。
- 支线按edge ID排序，节点按ID排序，独立分支以不同插入顺序构建得到相同图内容。父route几何/ID/claim不被split或修改。

查询：`routes()`、`getNationalTrunks()`、`getStrategicBranches()`、`getRouteById()`、`getEdgeById()`；`findParentCandidate(x,z)` 在有限National Trunk做简单整数projection，夹到端点，等距离按edge ID稳定决胜。旧`edge(orientation)`明确只返回National Trunk，避免新增同向支线改变国家交点。

本API只验证图/轴向几何合同，不批准海上路线、出生保护区、设施位置或跨路线冲突。后续Phase 2B负责路线选址与发布；当前没有自动路由策略。显式图junction不是匝道/转向模块，也不宣称新支线接入处已完成路面高程、护栏开口等实机工程。

## 同源消费

- `HighwaySpatialClaimProvider.query(graph,dimension,area)` 与既有seed入口共用一个实现；两种routeType均使用edge.bounds(32)，finite Y=`[-64,320)`，HARD INFRASTRUCTURE优先级70、margin=0、owner和`highway_spatial_v2_graph_1`身份规则不改。维度按ResourceKey registry+location核对为Overworld，无需初始化游戏注册表；Rural12格安全距和ClaimConflictResolver不变。
- `NaturalHighwayGenerationAdapter` 仍查询同一cached graph，按edge生成并有限裁断，不按purpose自算路径。`CorridorEngineeringSegment` 读取routeType，只有National Trunk使用国家交点上下层约束；支线不误套该交点。两种类型仍复用HighwayPlan.linear、23格横断面、HighwayProfile、HighwayCorridor、HighwayRenderer及surface/tunnel/viaduct工程。原两条主干的工程行为未改。
- `/afl highway_network info` 遍历全部edges，输出type/routeId/edgeId/purpose/parent station与node引用/两端节点。现有macro export没有输出Highway trunk，本轮不增加地图导出。nearest/node命令保留主干语义。

## 验证与边界

改动前先用原代码纯Java记录seed42基线：交点(926,-859)，A从(-6664,-859)到(7168,-859)，B从(926,-6944)到(926,6416)。相应bounds(32)为 `[-6664,7169)×[-891,-826)` 与 `[894,959)×[-6944,6417)`。

测试代码：`src/test/java/com/antaurora/apofirstlight/worldgen/highway/HighwayPhase1Snapshot.java`、`HighwayStrategicBranchTest.java`。沿用现有main-method契约测试方式，没有新增测试框架。可通过以下命令仅编译Java并运行测试，不依赖processResources/testClasses：

```text
gradlew.bat --gradle-user-home .gradle-user --offline -I scripts/highway-branch-tests.init.gradle compileJava highwayBranchContractTest
```

检查基线端点/交点/bounds、2主干与原ID、同seed重建、synthetic支线父引用/junction/反方向端点、真实claim consumer、有限Y/有限XZ、维度排除、主干claim身份不变、嵌套parent、输入拒绝、插入顺序确定性、列表不可变与cache未污染。Synthetic branch只存在于测试进程，不发布到自然生成。

2026-09-20执行结果：`BUILD SUCCESSFUL`，`HighwayStrategicBranchTest PASS: 30 checks; no world created`；独立运行改动前/后snapshot，seed42输出完全一致；`git diff --check`通过。首次测试因直接引用Level.OVERWORLD触发未bootstrap注册表而失败，现改为完整registry/location身份核对及仓库既有ResourceKey构建方式后通过，未以启动游戏绕过。编译仍有项目弃用/unchecked警告。

不改Terrain、Macro Geography、aquifer、surface water、洞穴、Highway横断面/材质及Tunnel/Viaduct算法。资源未改，不运行processResources。未runClient、clean、GameTest、新建世界、批量chunk生成、benchmark、截图或commit/push；完成后停止，不进入Phase 2B。
