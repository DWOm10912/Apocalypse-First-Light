# Rural Road Framework V1 Core

自然候选更新：已接入 [Highway ↔ Rural Spatial Conflict V1](highway_rural_spatial_conflict_v1.md)。完整 reservation 在地形/建筑规划前避让 Highway ±32 格硬包络及 12 格安全间距，冲突即拒绝。道路几何、tier/spacing/biome、建筑农田不变；旧 Piece/已生成区域不回溯。仅编译通过，实机待验。

状态：道路已接入自然生成与 `/afl rural` 开发命令。自然建筑已迁移到 [Building / Lot Planning V2](rural_building_lot_planning_v2.md)，开发命令仍为旧建筑布局。V2仅完成compileJava，实机生成、接受率、存档往返和视觉验收待用户测试；农田仍为Legacy。

## 规划与回放

保留 `RuralNaturalStructure -> RuralNaturalGenerator -> RuralNaturalPiece -> RuralGenerator.generateNaturalChunk`。先由 RuralRoadPlanner 确定完整道路，自然路径经 RoadNetwork/FrontagePlanner 生成模板尺寸lot，沿用原角色选择，再联合接纳建筑/access，最后规划避让它们的农田。四档 reservation仍为56/72/96/128方块，spacing=40 chunks、separation=20 chunks、salt、biome和地形门槛不变；档位权重现为25/30/30/15、Cluster/Full目标为6–8/9–12，详见 [Scale / Tier Tuning V1](rural_scale_tier_tuning_v1.md)。实际接受率需实机验收。

源码位于 `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/`：

- `RuralRoadType`：MAIN/SIDE/FARM_TRACK 的有效宽度、路肩、过渡和材质选择。
- `RuralRoadSegment`：XZ 轴向起终点、类型、方向、含端帽的完整占地。
- `RuralRoadNetwork`：从保存道路重建图，分支处拆分主路，发布节点、segments和实际接纳的anchors/occupiedAccess；不再生成统一尺寸示意lot。
- `RuralLotAnchor`：connection、frontage、朝路方向、usableBounds/depth、sourceRoad、entry/entryFacing和access；旧构造兼容V1。
- `RuralFrontagePlanner`：沿实际segments搜索两侧frontage，按真实旋转NBT框算origin，优先metadata socket，缺失/非法则front midpoint。
- `RuralRoadPlanner`：有界、非网格的四档布局，种子仅在 plan 阶段参与选择。
- `RuralAccessPlanner`：V2建筑+access联合占位，直线/单直角路径接所属道路；旧重载保留开发命令的midpoint逻辑。
- `RuralRoadPainter`：根据 profile 合并每格材质；有效道路带优先于路肩/过渡，同层 MAIN 优先于 SIDE/FARM_TRACK；每个 XZ 柱只写一次。

适配修改：`RuralPlan`、`RuralNaturalGenerator`、`RuralGenerator`、`RuralNaturalPiece`、`RuralFarmPlanner`、`RuralTerrainAdapter`。NBT 模板、rotation、ground anchor、六资产池、Natural/Dev 各自角色选择规则保持原实现，两个 `_02` 未加入配方。

## 实际道路规格

| Type | 有效路面 | 路肩/过渡 | 材质 |
| --- | --- | --- | --- |
| MAIN | 5 格 | 每侧 1 格砂砾路肩 + 1 格过渡，总带宽 9 | `apocalypse_firstlight:asphalt` 中心；砂砾；砂土/泥土过渡 |
| SIDE | 3 格 | 每侧 1 格过渡，总带宽 5 | 砂砾中心；砂土/泥土过渡 |
| FARM_TRACK | 3 格 | 无额外带 | gravel/coarse_dirt/dirt，世界坐标与计划 seed 确定的混合 |

不会使用 reinforced_concrete。新 painter 不直接决定砂砾材质，交给 RoadType；旧已保存计划仍保留原矩形/碎石/driveway 回放分支，所以 `RuralGenerator` 中为旧计划兼容而保留的 GRAVEL 代码没有删除。

Isolated 使用 18 格中心线 FARM_TRACK；Farmstead 使用 32 格中心线 SIDE。Cluster/Full 主路中心线分别 56/80 格 MAIN，并从中心产生单侧 20/28 格 SIDE，再以 8–12 格 FARM_TRACK 转 90 度到 endpoint；Full 另在主路 -24 格处向相反侧延伸 16 格 SIDE。端帽占地在中心线端点外扩 profile radius（MAIN=4、SIDE=2、FARM_TRACK=1）。这形成 T、turn 与 dead end，不生成棋盘网格。所有道路仍在原 reservation 内。

## Access 占地与兼容

自然建筑已使用V2 frontage placement，完整范围见V2文档。通道宽3格、建筑间空隙至少4格；建筑/access联合检查，均在reservation内，拒绝穿建筑/其他access/非所属道路，只允许终点合法接触所属道路。农田含围栏包络继续检查access并留1格间距，建筑blend ring仍跳过预留通道。开发命令保留原offset与旧connect规则。

这是计划内部占位；外部Highway整体reservation冲突在它之前执行。六资产自然路径全部使用frontage/anchor，未新增zoning或City框架。

## 确定性与保存

`RuralPlan.Road` 对新计划携带 segment；旧四参数构造保持 legacy 语义。Piece 保存 `RoadTypeV1`、`StartV1`、`EndV1`，每个 lot 保存 `RoadAccessV1` 的 connection/frontage/segments。恢复时从保存几何重建 graph，不在 chunk 到来时随机扩路。新道路计划缺失 lot access 会拒绝恢复；缺 V1 字段的旧计划继续原有逻辑。标记为 `ROAD_NETWORK_V1` 的新计划与旧 `MAIN_T_BRANCH` 记录区分。

Building V2额外保存SourceRoadV2、UsableBoundsV2、EntryV2、EntryFacingV2，旧V1 anchor继续读取；逐chunk不重新挑选位置。

Painter 以完整计划生成当前 chunk 的截片，材质由坐标/seed 决定，并按固定优先级合并交会格。维持地表高度查询；不对道路进行大规模挖填、坡度求解、桥梁或隧道工程。跨系统地形覆盖、道路实际通行和逐 chunk 生成顺序的游戏表现仍待实机验证，编译不证明这些运行结果。

## 边界与验证

无现代公共路灯、sidewalk、traffic light、road sign、bridge/tunnel；道路夜间不提供公共照明。未改 City/Small Town/Highway、未扩 160×160、未新增方块/贴图/资源 JSON/NBT。

本轮一次 `gradlew.bat --offline compileJava --console=plain` 成功（36 秒，现有弃用/unchecked 警告）。没有资源 JSON 修改，未运行 processResources；未 runClient/clean/GameTest、大量测试、世界生成或性能测试，未 commit/push。WG-06 的 24 条旧道路摘要属于历史基线，新布局不再承诺逐项相同；本轮未运行或重写该 fixture。

用户验收建议：在新生成区观察四档形态，重点检查 Cluster/Full 转角及 T 口材质、六类建筑/农田仍可落地、access 无穿插；保存重进并跨区块观察道路连续性。实际接受率与视觉效果未在本轮确认。
