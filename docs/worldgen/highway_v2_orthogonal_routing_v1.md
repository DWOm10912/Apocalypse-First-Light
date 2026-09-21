# Highway V2 Phase 2B-2.1 — Orthogonal Strategic Branch Routing V1

## 正式范围

National Highway V1 live Strategic Branch routing does not use long-distance diagonal geometry.

Satellite consumer仅发布N/E/S/W finite AXIAL edges，geometry=null，复用Axis Surface/Viaduct/Tunnel V1。原National Trunk A/B算法、端点、intersection、IDs、claims、生成路径冻结。
Diagonal/transition、ribbon models、Polyline Viaduct和测试完整保留供显式实验/未来局部道路使用；Diagonal Tunnel仍unsupported，不再阻塞live支线长段。

## Planner / scoring

实现：`src/main/java/com/antaurora/apofirstlight/worldgen/highway/SatelliteHighwayRouting.java`、`OrthogonalHighwayPath.java`、`HighwayRouteGraph.java`。仍消费Macro SATELLITE_ISLAND/CrossingCandidate，按稳定island ID顺序build-time选择，chunk不改图、不寻路。

- candidate要求mainland 0→target island、source waterSpan 300..600。
- 仅前向四轴bridge axis；现由 [Orthogonal Shoreline Bridgehead Search V1](highway_v2_shoreline_bridgehead_search_v1.md) 在原两端各512格圆形窗口搜索，96格局部平移政策已废止。不改Macro、不退回diagonal。
- actualBankSpan必须300..800，校验后才评分。800依据历史两岸各约96格内退：600+192=792，按16格上取800。这是有限路由上限，不是已实现海桥的结构跨度承诺；额外bank调整也不得突破800。
- 每candidate按总位移、actualBankSpan、方向和坐标排序，最多保留64对合法bank；有界搜索，不是全局完备解。沿线逐格排除错误水域、OPEN_OCEAN及第三陆块。
- 枚举Straight、两个L拐点、两转dogleg；后者尝试末轴回退112格、轴向中点、首轴128格三个中间坐标。排除零长/非轴向/冗余同向/短腿、不匹配桥轴的末腿。
- 第一腿垂直离开parent；junction候选32格步进，距国家交点/端点/其他同父junction至少192。
- 最终排序现由[Route Cost Fix](highway_v2_route_cost_fix.md)修正为：networkCost=大陆长度+64×TURN数，随后extraDistance、TURN数、actualBankSpan、总位移、岛侧长度及稳定完整方案tie-break。跨现有桥头池、A/B主干、合法junction和路径模板比较，不再用位移或0 TURN优先锁定长路线。
- 统一检验Macro dry LAND/COAST、正确landmass、waterClass=NONE、waterbodyId=-1、coastDistance≥0；排除出生reserve，检查65格施工带及TURN方形区。新施工包络/预留区不得与其他主干、已选支线/预留区冲突；自己的junction reservation可与parent相交。冲突继续尝试次优路线。
- 岛内优先同桥轴直线；不足时有界尝试朝岛内部的一转L，最多一个TURN，无环路或terrain pathfinding。

## TURN / edges / reservation

NodeKind.TURN与BRANCH_JUNCTION分离；Turn保存node（ID/XZ）、incomingEdge/outgoingEdge、incomingDirection/outgoingDirection。
每腿独立为`<route>/mainland_0..2`或`<route>/island_0..1`；TURN为`<route>/<side>/turn/<index>`。逻辑节点与裁切后的可施工station范围分别保留。

- TURN_RESERVED_LENGTH=64（RESERVED_LENGTH），中心±32，整数方形65×65列；依据23格道路及32格施工半包络，供未来模块使用，不声称完整互通已能装入。
- incoming/outgoing在中心32格外停止，再向外对齐8格工程网格，端点距中心33..40格，施工列不进入预留方形。
- minimum turn-to-turn spacing=128，防止两侧预留区吃光中间腿。
- junction同样发布JUNCTION_RAMP_ZONE，父主干不挖断，支线在区外开始；尚无可驾驶Ramp连接。
- 末腿至少112格，覆盖≤40格转向裁切和≤7格桥头端点网格裁切，实际轴向直引道至少64格。
- claim覆盖finite edges及TURN/junction预留区，保持HARD INFRASTRUCTURE、Y[-64,320)、原优先级/owner。旧live diagonal corridor不再claim，sea reservation仍无施工claim。
- 既有FiniteRouteHighwayWriter边界限制全部落块/清理；没有新90°renderer或两条23格道路硬叠。

## Debug / export / diagnose

info显示AXIAL、可施工station范围、TURN两侧edge/方向、TURN_RESERVED_ZONE/JUNCTION_RAMP_ZONE。
export橙线取裁切后的axis endpoints，TURN标签/洋红方框独立显示；planned crossing仍青色虚线。TXT增加TURN/预留区段，保留CONNECTED/UNCONNECTED SATELLITES。
diagnose在预留区返回PLANNED_RESERVATION及`PLANNED TURN / NO ROAD MODULE YET`或`PLANNED JUNCTION RAMP / NO ROAD MODULE YET`。只读图查询可在未加载chunk报告，不加载世界、不误报NO_GRAPH_EDGE；区外axis沿用原已加载chunk terrain/profile dry replay。

## 代表seed与验证

| seed | 已连接岛（actualBankSpan） | 未连接岛 | TURN数 |
| --- | --- | --- | ---: |
| 0 | #1 (750) | 无 | 2 |
| 2 | #1 (729), #2 (683) | 无 | 2 |
| 42 | #1 (626), #2 (651), #3 (685) | 无 | 3 |
| -645704099691625981 | #1 (641), #2 (774), #3 (627) | 无 | 4 |
| -4332662446239654818 | #1 (627) | 无 | 1 |

以上为Route Cost Fix后的纯规划结果，保留Shoreline Search已取得的10/10；严格轴向政策仍不保证任意seed全部岛连接。诊断输出firstFailureReason、两岸点数、axialPairs、viablePairs及阶段拒绝计数，质量字段见Route Cost Fix。Bridge Approach最终工程模式仍UNKNOWN，未修隧道接岸问题。
HighwayOrthogonalRoutingTest覆盖正/负Straight/L/Dogleg、TURN引用、预留/施工包络互斥、claim、diagnose、export、冲突、Axis Surface/Viaduct/Tunnel消费者。Satellite测试覆盖五seed、反序输入、有效candidate tie-break、junction冲突替代。旧高架回放保留为显式历史diagonal fixture，不假称当前live仍为POLYLINE。

```text
gradlew.bat --gradle-user-home .gradle-user --offline -I scripts/highway-branch-tests.init.gradle highwayOrthogonalRoutingTest highwaySatelliteRoutingTest highwayViaductTest highwayGeometryContractTest highwayBranchContractTest highwayNetworkExportTest
```

依赖compileJava，不依赖processResources；资源未改。无世界测试不代表视觉、真实Axis Tunnel洞腔/地基或新区块落块验收。

历史Phase 2B-2.1校验：compileJava及六个契约任务通过（Orthogonal126、Satellite96、Viaduct181656、Geometry32 cases/500227及integration、Phase2A30、Export54）。最新Shoreline Search校验见其文档；不将旧计数当作本轮结果。
Ramp/物理Turn Module、Sea Bridge、Fluid Safety未实现。已知grading可能暴露lava/water仅记录，未处理。未接City/Port/Military、未改Terrain/Macro；未runClient、clean、GameTest、新世界、批量chunk、benchmark、截图、commit/push，未改.obsidian/workspace.json。
