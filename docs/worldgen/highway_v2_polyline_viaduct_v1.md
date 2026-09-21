# Highway V2 Phase 2B-1.1 — Polyline / Diagonal Viaduct V1

当前规则见 [Orthogonal Routing V1](highway_v2_orthogonal_routing_v1.md)。本文高架/junction高程能力保留供显式geometry调用。旧live seed回放为历史记录；对应测试保留为独立历史diagonal fixture，不代表当前卫星路由。

## 当前实现与边界

2026-09-20：Polyline Viaduct 已接入自然工程/renderer，未进行实机新区块或视觉验收。
支持 AXIAL_STRAIGHT、DIAGONAL_45、AXIAL_TO_DIAGONAL_TRANSITION、DIAGONAL_TO_AXIAL_TRANSITION，沿用 32 station transition。
VIADUCT 或 bridge span 不再导致整个 POLYLINE 窗口 `geometryDeferred`；Surface+Viaduct 混合窗口正常消费。
显式 TUNNEL 或现有 resolver 判定的 tunnel span 仍整窗口 deferred，无施工列、清地或桥墩。
Sea Bridge 仍只有 reservation；Satellite #1 路由失败不在本轮处理范围。

## 复用工程与几何

- `HighwayCorridor.buildRibbon` 复用同一 HighwayGeometry footprint，名义法向宽 23，lateral ±11.5；没有独立窄高架路面。
- VIADUCT cell 标记 structuralBridge，进入既有 `HighwayRenderer.placeRoadStructure`：沥青顶面，下方三层 reinforced concrete；仍使用既有 median、shoulder、station markings 和坡面连接块。
- 法向 (11.5,12.5] 外缘带复用 BRIDGE_EDGE：混凝土边梁及 Y+1 reinforced concrete slab。该边梁不计入名义 23 格路面；轴向旧路径仍为 lateral ±12。
- roadY 由局部 station 对应的工程 profile 给出，方向变化不重置 station、高程或虚线相位。浮点端点判断采用与几何 query 一致的 1e-8 容差。
- Surface 保留原 grading；Viaduct 不执行 surface fill，cut envelope 排除高架施工列，不铲平桥下地形。
- CORE=256 / HALO=192 不变，ENGINEERING_VERSION=3。FiniteRoute/ChunkOwned 写入、edge.bounds(32)、claim Y[-64,320) 不变；没有新增 claim 或海上 edge。

## Junction elevation

`CorridorEngineeringSegment` 仅对起点为 BRANCH_JUNCTION 的大陆支线读取 ParentAttachment 的父 edge。
从 attachment 所在区块对应的父工程 segment/cache 取得真实只读 profile，由 `HighwayBranchGrade` 保存。
station 0..64 保留父工程平面（每个 XZ 查询父路对应 station），不是只匹配中心点再横向铺平；父路为 VIADUCT 时继承该结构模式。
station 64..256 以 smoothstep 权重接回支线自己的 globalRoadY；之后完全由支线工程控制。
`HighwayProfile.roadYAt` 保留 junction 横向父路坡面差异，避免重叠处因父路纵坡产生一格错位。
岛侧 bridgehead 不继承大陆高程。没有修改 RouteGraph、选址、路线控制点或增加匝道/完整互通；交汇边缘视觉仍待实机验收。

## Piers / foundation / determinism

共享 renderer 的 pier pass 继续按 global station 每 32 格执行，沿用 span 边缘 8 格保护、node pierAllowed、既有尺寸和 reinforced concrete 材料。
Polyline 的 `HighwayPierGeometry.at` 从该 station 的 centerline + local tangent 求中心，以世界坐标统一 round；transition 也正常参与，不按控制点额外放柱。
斜向矩形用逆投影栅格化，而非逐个旋转已取整像素，避免 cap/shaft 内部孔隙。轴向旧 placement 数学不变。
地基继续向下最多搜索 128 格，跳过水/空气/树叶/可替换/无碰撞方块。Polyline 在工程阶段从 generator baseColumn 取得只读支持结果，存入不可变 profile；避免相邻 chunk 把已经放出的桥墩当作地形。未找到支持时沿用 foundation failure 日志并跳过桥墩，不伪造地基。
基座、shaft、cap 的方块最后才由 ChunkOwned + FiniteRoute writer 过滤；没有每区块独立定义方向或 station 相位。
这验证的是规划几何，地基落块、洞穴上支撑及实机视觉仍需新区块验收。轴向 foundation 仍走原世界查询路径。

## 诊断与回归边界

`HighwayLiveGenerationDiagnostic` 的 firstUnsupported 仅报告 POLYLINE TUNNEL/tunnel span；VIADUCT 不再被报为不支持。
`/afl highway_network info` 显示 `diagonalViaduct=SUPPORTED diagonalTunnel=UNSUPPORTED_SAFE_SKIP`。
`NONE` 只表示有可归属 corridor cells，不代表实际落块成功。

新增 `HighwayViaductTest`：轴向 23 格及边梁回归；纯对角与两种 transition；Surface+Viaduct；各 chunk 独立 CORE/HALO 重建 union=global deck；cut 排除高架；station pier pose、chunk 边界 cap union；junction 高程；Tunnel defer。
保留 Phase 1 snapshot/Phase 2A、Phase 2B-1 geometry、Phase 2B-2 routing、Export V2 测试。

真实 seed `-645704099691625981` 使用当前生产 graph，#2 `(6544,-916)` 和 #3 `(-879,3376)` 做 **真实路由 + 受控混合 profile** 的 BUILD_RIBBON 回归。
受控 profile 不是实际噪声地形采样，不能冒充用户报告的 49 SURFACE / 8 VIADUCT，也不能断言 #3 实际没有 Tunnel。
尚未在运行中的 Forge 世界加载本轮 Java；真实 terrain/profile 的 dry diagnose replay 和实际落块仍待用户执行：

```text
/afl highway_network diagnose 6544 -916
/afl highway_network diagnose -879 3376
```

请使用新构建后运行环境并在新区块验收；旧区块不会因本次改动自动补路。

## 验证命令

```text
gradlew.bat --gradle-user-home .gradle-user --offline -I scripts/highway-branch-tests.init.gradle highwayViaductTest highwayGeometryContractTest highwayBranchContractTest highwaySatelliteRoutingTest highwayNetworkExportTest
```

该 runner 依赖 compileJava，不依赖 processResources。此次无资源修改，不运行 processResources。

本轮最终结果：compileJava 成功（既有弃用/unchecked 警告）；HighwayViaductTest 181,652 项断言通过，包含上述几何/混合/接缝/桥墩/junction/受控回放；Phase 1 seed42 snapshot 保留；Geometry 32 例 / 500,227 项及 integration 通过；Phase 2A 30 项、Satellite Routing 95 项、Export V2 84 项通过。测试脚本 BUILD SUCCESSFUL，diff whitespace 检查通过。

受控回放输出（不是实际地形 profile dry replay）：

| seed -645704099691625981 | geometryDeferred | cells | ownedCells | FIRST DROP POINT |
| --- | --- | ---: | ---: | --- |
| #2 mainland `(6544,-916)` | false | 10300 | 71 | NONE |
| #3 mainland `(-879,3376)` | false | 10445 | 255 | NONE |

真实 Forge terrain/profile 的两处 dry replay **未执行**，因此不将完整实机验收标记通过；#3 实际可能仍因 Tunnel deferred。基础扫描在真实世界上的支撑可靠性、交汇护栏/边缘视觉也未实机验证。

未 runClient、clean、GameTest、新建世界、生成 chunk、benchmark、截图、commit/push；未改 Terrain/Macro、Satellite Routing、RouteGraph、HUD 或 .obsidian/workspace.json。
