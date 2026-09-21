# Highway V2 Phase 2B-1 — 45° / Diagonal Geometry V1

当前规则见 [Orthogonal Routing V1](highway_v2_orthogonal_routing_v1.md)。National Highway V1 live Strategic Branch routing does not use long-distance diagonal geometry. 本文geometry/ribbon/Polyline Viaduct保留，live发布对角支线描述已成为历史。

现行覆盖说明：[Phase 2B-2 Satellite Routing](highway_v2_satellite_routing_phase2b2.md) 已发布合法卫星岛支线；[Phase 2B-1.1 Polyline Viaduct](highway_v2_polyline_viaduct_v1.md) 已接入高架。下文默认无支线、资源构建结果为 Phase 2B-1 历史记录。当前仅 POLYLINE Tunnel 保留 safe-skip，Sea Bridge 仍未实现。

状态（2026-09-20）：可选八方向ribbon几何及surface corridor消费链已实现，编译、资源处理、无世界契约测试通过。默认自然graph仍仅两条Phase 1轴向National Trunk，没有实际对角支线。未做客户端、实机视觉、真实chunk写入或性能验收。Satellite Routing、Sea Bridge、City/Port/Military接入未实现；本轮不进入Phase 2B-2。

## Geometry / width / transition

权威实现为`src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwayGeometry.java`：不可变control points、legs、patches和32格tile索引，统一产生centerline、station、signed lateral、footprint与detail bands。不是多条粗直线分别绘制后覆盖。

- N/NE/E/SE/S/SW/W/NW八方向；严格轴向或abs(dx)=abs(dz)。拒绝任意角、90°直接转向、短于32格的腿，连续同向腿须合并。
- AXIAL_STRAIGHT、DIAGONAL_45、AXIAL_TO_DIAGONAL_TRANSITION、DIAGONAL_TO_AXIAL_TRANSITION。
- transition为32 station格，接点前后各16；横截面normal→共享miter→normal。miter=(n1+n2)/(1+n1·n2)。中心线仍是45°折线，不是平滑圆弧、Bezier或spline。
- 16格半模块大于最大施工半包络32×tan(22.5°)≈13.255的沿线剪切，映射不翻转；相邻转向至少32格，过渡模块不重叠。
- 名义宽23世界格，以各腿垂直signed lateral的[-11.5,11.5]判定方块整数坐标占用。允许阶梯离散误差，不承诺每个轴向扫描行都有23块。
- station是累计欧氏弧长，对角每(1,1)增加sqrt(2)，跨patch/转向/chunk不归零。query以绝对lateral、再以较早station确定性消歧。
- 控制点2..256个，有限整数世界坐标。本版本验证简单有限路线，不提供自交路线选址/消解和多路线交汇模块；未来规划器不能将自交路径当作已验收几何。

## Raster / marking / median / shoulder / guardrail

patch逆投影求station/lateral；32格索引只枚举附近patch，raster去重并按世界x/z排序。所有路带来自同一footprint，中央带和标线在斜向角接触处使用确定性4邻域连接格，road和中央线为4连通；外边缘从最终footprint邻域提取，为阶梯式8连通。

标线为lateral ±2黄线、±6白虚线、±10白边线；虚线floor(globalStation) mod 9 < 3。没有转向/patch/chunk相位重置。方块内使用中心片和NESW连接臂，仅连接同一paint band。

`RoadMarkingBlock`新增connections=0..16、rises=0..15：0保留旧轴向模型，1..15为连接mask，16孤立点；rises连接高一格邻块。旋转/镜像同步mask。三个既有标线blockstate改为multipart，12个`models/block/*ribbon*.json`提供通用和材质引用模型；复用原texture，不新增Registry ID、纹理、材质或采掘规则。旧四方向apply与HEAD逐项相同。

surface延续既有外边缘混凝土/中央隔离带。边缘只从最终road footprint产生；缓坡median/outer edge有高差连接位置，标线有一格riser。Phase 2B-1.1 的高架复用该 footprint，外加一格法向混凝土边梁及 slab parapet，未新增护栏材质或造型。平面检查不代表实机光照、任意陡坡或视觉验收。

## 工程与默认兼容

`HighwayRouteGraph.Edge`增加可选geometry，非空时orientation=POLYLINE；旧构造器/轴向数据保持。新`withStrategicBranch(...,parentStation,geometry,purpose)`返回新图，geometry首点必须等于精确父attachment。parent/junction ID语义未变；V1父edge仍须轴向，未提供斜向父路attachment。默认build/forSeed不调用新重载。

`HighwayPlan.ribbon`保存完整geometry和局部station窗口；`CorridorEngineeringSegment`保留256 core/192 halo；`HighwayTerrainSampler`按真实切线/法线采样。`HighwayCorridor.buildRibbon`输出同源surface/core/cut/ROW，ROW为半宽14.5的expanded ribbon，不填满轴向AABB。`HighwayRenderer`使用既有chunk-owned writer，只过滤本chunk写入，不重新定义端点法线。默认geometry=null路径保持原逻辑。

graph query用有限patch包络；claim仍为edge.bounds(32)、Y[-64,320)、既有ID与优先级。POLYLINE claim是整条路线保守AABB，可能多预留转弯内侧空地，不代表施工填充该矩形。Rural冲突算法不变。

默认自然入口未发布对角路线；测试中的显式graph仅验证API/查询/真实corridor，不代表自动路由或junction开口/高程已实机完成。

## Tunnel / Viaduct 安全限制

当前 POLYLINE VIADUCT/bridge span 已支持，包括 Surface+Viaduct 混合窗口；不再仅因 VIADUCT defer。仅显式 TUNNEL 或合格 tunnel span 使整个窗口输出空 surface/core/cut/ROW/markings/tunnel，renderer 早退。非空 ribbon 进入共享 pier pass，按 station 的局部切线放置桥墩。

这不是完整桥隧路线连续性方案，含 Tunnel 的窗口仍可能缺路。默认轴向主干的 Tunnel/Viaduct 不变。

## 验证

`src/test/java/com/antaurora/apofirstlight/worldgen/highway/HighwayGeometryTest.java`包含A E→NE、B NE→E、C E→NE→E、D E→SE→E、E N→NW→N、F跨chunk边界、G精确chunk角点、纯diagonal，各四旋转，共32例；包含镜像转向和负坐标。

通过500,227项检查：4连通road、无内部洞、detail bands连通、外边缘8连通、宽度/bounds、station、重建确定性、各chunk独立重建查询union严格等于global footprint、无重复所有权、标线phase和互相匹配的mask。该保证是几何层，不替代真实世界工程mode交界验收。

`HighwayGeometryIntegrationTest.java`检查真实 graph 绑定/查询、原主干、corridor footprint、ROW、坡面 riser；当前结构断言为 Viaduct 支持、Tunnel defer 无施工列/洞腔。新增高架工程回归见 Phase 2B-1.1 文档。原 Phase 2A 保留端点/交点/bounds/claim/cache 契约。

```text
gradlew.bat --gradle-user-home .gradle-user --offline -I scripts/highway-branch-tests.init.gradle compileJava processResources highwayBranchContractTest highwayGeometryContractTest
```

BUILD SUCCESSFUL；compileJava、processResources通过，现存弃用/unchecked警告仍在。资源JSON解析、multipart引用、旧模型apply等价检查通过。没有新增测试框架或游戏bootstrap。

未runClient、clean、GameTest、新世界、批量chunk生成、benchmark、截图、commit/push；未改Terrain/Macro、Rural算法、设施/海桥。未触碰`.obsidian/workspace.json`的外部改动。完成后停止。
