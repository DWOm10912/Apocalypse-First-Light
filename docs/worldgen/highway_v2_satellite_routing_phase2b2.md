# Highway V2 Phase 2B-2 — Satellite Island Routing

当前规则见 [Orthogonal Routing V1](highway_v2_orthogonal_routing_v1.md)。现行替代规则：live长距离支线已改轴向multi-edge + TURN/junction reservation。本文八方向选桥、两岸各单POLYLINE edge、评分及旧seed连接结果均为历史记录。

状态（2026-09-20）：build/forSeed已在原两条National Trunk之外发布可用卫星岛的Strategic Branch。每个connection有大陆、岛侧两个有限道路edge及一个仅metadata的海峡reservation。没有海上可施工edge、海桥renderer或海桥claim。无世界逻辑验证不代表实机道路、junction高程/边缘交汇、桥头或chunk视觉验收。

## Live geography authority

`MacroGeography.islands()`返回Island(id,role,x,z,majorRadius,minorRadius,angle,outlinePhase,gap)。当前1–3个SATELLITE_ISLAND，稳定id由Macro分配（1..3），没有“不需要道路”的标记。只按role筛选、id升序处理，不重新定义地理。

`CrossingCandidate(waterbodyId,fromLandmassId,toLandmassId,fromX,fromZ,toX,toZ,waterSpan)`：坐标是double世界格，当前from=0大陆、to=岛id，每岛一个candidate；waterbodyId为100+稳定slot。waterSpan当前400–500格，是海面gap，不是桥头间距离。原端点通常各向陆地内退96格；bankFits验证原方向的64×128格bank，不能据此认为端点已八方向对齐或有足够高速引道。Macro export的300–600格qualified判断只针对waterSpan，并非高速接入成功保证。

## Deterministic selection

实现：`src/main/java/com/antaurora/apofirstlight/worldgen/highway/SatelliteHighwayRouting.java`。只在graph构建时运行；沿用16-seed有界cache，不在每chunk枚举岛屿或修改图。

1. 筛选from=0、to=目标岛、waterSpan∈[300,600]的candidate。按waterbodyId、fromX/Z、toX/Z、waterSpan排序。
2. 只尝试最接近原candidate方向的八方向轴（22.5°内，等距可两者）。端点沿桥轴及横向做局部8格步进适配，横移/各端纵移均[-32,32]；每个最终桥头距原对应bank点不超过96格。96是本轮局部搜索政策，参考Macro strait分类半宽，**不是Macro授权范围字段**。
3. 两岸分别通过Macro sample检查所属landmass与LAND，施工半宽32、横向16格/纵向8格有界探针；大陆还排除出生reserve384+32。验证陆侧引道，不能只验证两个中心点。按总位移、坐标排序保留最多8对有效bank，限制构建成本；不是对所有海岸或连续空间的全局最优搜索。
4. 岛内路线必须可行，再枚举两条有限主干的整数attachment（从起点+192开始，每32格一候选）。排除国家交点、端点、同父路已选junction的192格邻域。
5. 枚举0–2次45°转向模板，起始段斜向离开轴向主干；不得同轴重叠或90°硬接。腿长≥32，沿线Macro LAND/正确landmass检查。只作交通几何与陆块校验，不采样坡度、不避山、不A*。
6. 合法组合按以下字典序择优：**转向数、大陆路线长度、桥头总位移、原waterSpan、waterbodyId、parentEdgeId、parentStation、大陆桥头x/z**；完整candidate及bank遍历顺序确定余下平局。

minimum junction spacing = **192格**：沿用工程halo尺度，超过两侧32施工包络+32过渡，也超过国家交点96格approach。不同岛按稳定id顺序占用junction，后续岛从其余合法attachment择优；不split原trunk、不改parent model。此规则解决junction近距冲突，不是完整路线间立交/匝道系统。

## Geometry and bridgeheads

minimum straight coastal approach = **64格**，即两倍32格transition；最后一腿至少80格，扣除接点后16格transition仍有64格直段，方向与未来bridge axis一致。Bridgehead节点分别为MAINLAND_BRIDGEHEAD、SATELLITE_BRIDGEHEAD，保存准确整数XZ及岛identity；“干燥”是Macro LAND语义，不是假称采样了游戏方块/局部水体。

岛侧从bridgehead沿同一轴向岛内延伸，每8步检验陆块/施工带，长度上限按该岛majorRadius缩放，最低长度max(128,minorRadius/2)，不允许短短几十格或跨回海里。终点在目标岛内，不创建环岛路/设施道路。

同一route `strategic_branch/satellite_<id>`包含 `/mainland`、`/island` 两个POLYLINE edge。共享精确ParentAttachment(parentRouteId,parentEdgeId,integer station,junctionNodeId)。connection id为 `sea_crossing/satellite_<id>`；Connection record保存岛id、原完整candidate、所属route、两岸节点、整数方向dx/dz、实际bank-to-bank span与两岸geometry。waterSpan和bank span分别保留，不混淆两者。

## Live consumers and limitations

`HighwayRouteGraph.buildTrunks(seed)`保留原Phase1算法；`build(seed)`先构建原主干再运行planner，发布不可变nodes/edges/routes/seaCrossings/routingDiagnostics。Route按routeId分组，支持同route多edge，所有copy API保留reservation metadata。默认仍恰好2个NATIONAL_TRUNK；salt、端点、交点、coast termination、原ID/claim不变。

既有NaturalHighwayGenerationAdapter与HighwaySpatialClaimProvider读取同一forSeed图，自动看到两岸edge。实际geometry bounds进入finite claim，保留[-64,320)、优先级70、margin及Rural12格安全距；claim是道路edge的保守AABB。reservation不加入edges，不产生海上道路surface/claim。

两岸复用 Phase2B-1 ribbon/transition/station/chunk-owned output。[Phase 2B-1.1](highway_v2_polyline_viaduct_v1.md) 已支持 Diagonal/Polyline Viaduct 和混合窗口，大陆 junction 读取父路工程高程并平滑衔接。只有 Tunnel 仍整窗口 safe-skip；没有实现匝道、海桥或更改路由。实机接入处效果待验收。

某岛无满足这些有限政策的组合时，不修改Macro、不造超长桥或任意角路线：该岛不发布道路，routingDiagnostics明确记录。其余合法连接保留；并不保证任意seed所有岛都能接通。未来若遇此诊断，应审查candidate与受限几何，不静默扩大搜索。

## Debug and verification

### Highway live-generation dry diagnostic

`/afl highway_network diagnose <x> <z>`（或省略坐标使用执行者所在 X/Z）在当前已加载区块做**只读 DRY REPLAY**。目标区块未加载时直接返回 `CHUNK_NOT_LOADED`，不会为诊断加载或生成区块。命令读取当前维度、目标坐标海平面高度的 biome 与 `primary_highway_generation` tag，使用和自然生成相同的 seed graph、区块边界查询、区块左上角 station 选段、工程构建与 terrain/profile/corridor 算法；不调用 placed feature、renderer 或 block writer，不改方块及 chunk NBT。

每条命中 edge 输出 route/type/geometry/bounds、坐标 station 与选段 station、core/halo 范围、GROUND/CUT/FILL 合计的 `SURFACE` 及 `VIADUCT`/`TUNNEL`/其他样本数、首个不支持的 station/XZ、`geometryDeferred`、由 profile 加空 cells 推导的 `safeSkipTriggered`、全工程窗口 corridor cells、目标区块 owned cells 与预期 ribbon cells。每条 edge 给单值 `FIRST DROP POINT`：`FEATURE_NOT_ELIGIBLE`、`CHUNK_NOT_LOADED`、`NO_GRAPH_EDGE`、`SEGMENT_SELECTION`、`ENGINEERING`、`BUILD_RIBBON`、`RENDERER` 或 `NONE`；多 edge 结论不同时 overall 为 `MIXED`。`NONE` 仅说明当前预检有可归属道路单元，**不证明实际写入成功**。目标坐标 biome 采样不能代替历史 chunk 的 feature 执行记录；整个命令不能证明旧区块当初使用过相同代码。命令只诊断，不修复 safe-skip、junction 或海桥。

后续现行扩展：[Highway Network Export V2](highway_network_export_v2.md) 已复用Macro PNG/TXT导出卫星连接、两岸edge/节点、planned crossing，并显式列`CONNECTED SATELLITES`、`UNCONNECTED SATELLITES`。下文“不重复扩展macro export”是本阶段历史边界，不再代表当前导出功能。

`/afl highway_network info`输出每 connection 的岛 id、candidate、route/connection id、父主干/junction、两岸节点、方向、span、两岸长度和未规划岛诊断。当前能力提示为 `seaBridge=RESERVATION_ONLY diagonalViaduct=SUPPORTED diagonalTunnel=UNSUPPORTED_SAFE_SKIP`；`diagnose` 的 firstUnsupported 只统计 POLYLINE Tunnel，不把已支持的 VIADUCT/bridge span 计作失败。

纯测试：`HighwaySatelliteRoutingTest`覆盖seed0/2/42的1/2/3岛，多个candidate平局，A/B两个parent，国家交点退选和合成占用junction退选，八方向轴、64直引道、反序输入的完整control points一致、有限终点与live query。F/G使用生产junction候选过滤器的显式冲突fixture，不冒充真实岛恰好发生冲突。Phase2A原30项在隔离trunk fixture执行，另校验live原主干未变；Phase1 snapshot只打印国家主干。Phase2B-1保留32例/500,227项和真实corridor契约。

```text
gradlew.bat --gradle-user-home .gradle-user --offline -I scripts/highway-branch-tests.init.gradle compileJava highwayBranchContractTest highwayGeometryContractTest highwaySatelliteRoutingTest
```

最终通过：原30项、几何32例/500,227项、corridor integration及routing95项，BUILD SUCCESSFUL；现有弃用/unchecked警告仍在。资源未修改，不运行processResources。未runClient、clean、GameTest、world/chunk批量生成、benchmark、截图或commit/push；没有修改Terrain/Macro/City/Port/Military，也未进入Phase2C。未触碰Obsidian workspace外部改动。
