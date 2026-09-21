# Highway Network Export V2

Sea Bridge V1 更新：下文旧的“仅预留/无施工claim/未生成Sea Bridge”描述已被 [Sea Bridge V1](highway_v2_sea_bridge_v1.md) 取代。现有 crossing 接入自然生成并拥有窄条形 infrastructure claim，图例为 `SEA_BRIDGE_V1 (plan)`。TXT 增加 bridgeType、generationStatus、grade endpoints、pierCount/plannedPierCount、两岸 abutment status；离线导出为 ENGINEERING_NOT_SAMPLED/UNKNOWN，不声称已生成或取得 live 高程。青色虚线仍是规划图，CONNECTED/UNCONNECTED 仍指路由结果而非落块验收。

当前默认路由采用 [Orthogonal Strategic Branch Routing V1](highway_v2_orthogonal_routing_v1.md)。轴向道路按实际裁切后的 station endpoints 绘制，不连到预留区中心；TURN / junction 以洋红方框标记核心预留区。`TURN NODES AND INFRASTRUCTURE RESERVED ZONES`文本节现列出[Branch Junction / Turn Ramp V1](highway_v2_branch_junction_turn_ramp_v1.md)的moduleId、moduleType、nodeId、incoming/outgoingDirection、核心bounds、seamBounds与geometryStatus=TURN_RAMP_V1/JUNCTION_RAMP_V1；gradeStatus=RUNTIME_CHECK_REQUIRED，不将几何导出冒充实际坡度或落块成功。保留POLYLINE控制点导出能力，但默认卫星支线不再发布长距离POLYLINE。

状态：开发环境的既有 `/afl macro export [radius] [step]` 同时导出 Macro Geography 与当前 `HighwayRouteGraph`。输出仍为 `afl_debug/macro/macro_geography_<seed>.png` 和同名 `.txt`；不新增格式、世界生成入口或路由算法。实现见 `src/dev/java/com/antaurora/apofirstlight/dev/MacroGeographyExportCommand.java` 与 `HighwayNetworkExport.java`，发布JAR不包含该dev命令。读取同seed的缓存Macro计划和immutable Highway图，绘制地图时不按像素重建图。

PNG保留原Macro陆海颜色、岛屿标签、黄色虚线300–600格原始桥候选；再叠加白色National Trunk、橙色Strategic Branch、洋红色显式节点和预留方框、青色虚线planned sea crossing、未连接岛中心红X。右上角图例区分道路、节点与仅预留的海桥。POLYLINE沿真实control points逐腿绘制；轴向edge使用裁切后的施工端点。转向处保留规划缺口，不以branch起终点直线替代真实中心线。节点短标签为INT/JCT/TURN/MBH/SBH/END。PNG是计划图，不表示已经铺出的方块或物理海桥。

TXT保留原Macro审计全文，追加 `HIGHWAY NETWORK EXPORT V2`：summary、ROUTES、EDGES、NODES、SEA CROSSING RESERVATIONS、CONNECTED SATELLITES、UNCONNECTED SATELLITES。按routeId、edgeId、nodeId、crossingId稳定排序。

[Route Cost Fix](highway_v2_route_cost_fix.md)为info和CONNECTED TXT新增`selectedParentTrunk`、`mainlandRouteLength`、`turnCount`、`extraDistance`与`networkCost`，原parentTrunk/mainlandBranchLength仍保留兼容。extraDistance=大陆路径长−junction到大陆桥头Manhattan距离，networkCost=大陆路径长+64×大陆TURN数。原islandHighwayLength、actualBankSpan、combinedDisplacement复用；PNG不加新图层。数值是规划成本，不是Terrain工程评估。

- Route：ID、type、purpose、edge IDs、完整ParentAttachment。
- Edge：ID、route、起终节点ID与XZ、AXIAL/POLYLINE、真实segment kinds、bounds(32)、station范围、长度、所有control points、ParentAttachment。
- Node：ID、实际NodeKind、XZ、所连edge；有适用的岛ID、connection ID、parent route/station。国家交点和支线junction的共享关系保留。
- Sea reservation：稳定crossing ID、岛ID、所属route、两岸bridgehead ID/XZ、实际桥头跨度、当前四方向轴、原Macro waterbody ID与完整CrossingCandidate。仅为PLANNED metadata，没有sea edge或施工claim。
- Connected summary：岛ID、状态、完整sourceCandidate、父主干/junction、选中两岸桥头、两岸道路长度、actualBankSpan、bridgeAxis和共享route/connection ID。Shoreline Search V1新增mainlandDisplacement、satelliteDisplacement、combinedDisplacement及bridgeApproachEngineeringStatus=UNKNOWN；info同步输出相同信息。未输出/claim未选中的搜索备选。
- Unconnected summary：枚举Macro全部SATELLITE_ISLAND减去图内已连接岛，逐岛输出`status = UNCONNECTED`、`failureReason`及原`routerDiagnostic`。无qualified candidate为`NO_QUALIFIED_CROSSING`；否则直接采用路由器`firstFailureReason`（主要阻断阶段），不再覆盖为笼统失败。诊断含shorePointsMainland、shorePointsSatellite、axialPairs、viablePairs、islandApproaches、junctions、landRoutes、rejected及sourceCandidates；缺失诊断仍为`ROUTING_STATUS_UNKNOWN`。具体定义见[Shoreline Search V1](highway_v2_shoreline_bridgehead_search_v1.md)。

命令返回可复制的PNG/TXT路径；TXT提供junction、TURN、桥头、route endpoint的精确XZ供TP。导出测试在内存中验证seed 0/2/42的稳定序列、graph数量、父引用、控制点、桥头坐标、ID唯一性与无branch场景；新增正交契约验证TURN及预留区导出。不保存测试截图、不生成新世界。当前完整验证与Satellite Routing/claims变更见正交路由文档；Macro topology、Terrain不变，未接City/Port/Military，未生成Sea Bridge。实机导出效果待用户使用命令验收。
