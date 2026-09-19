# Rural Road Framework V1 Core

自然候选更新：已接入 [Highway ↔ Rural Spatial Conflict V1](highway_rural_spatial_conflict_v1.md)。完整 reservation 在地形/建筑规划前避让 Highway ±32 格硬包络及 12 格安全间距，冲突即拒绝。道路几何、tier/spacing/biome、建筑农田不变；旧 Piece/已生成区域不回溯。仅编译通过，实机待验。

状态：代码已接入自然生成与 `/afl rural` 开发命令；仅完成 `compileJava`，实机生成、接受率、存档往返和视觉验收待用户测试。不是 Rural Building Framework，也不是 Rural V2 完整迁移。

## 规划与回放

保留 `RuralNaturalStructure -> RuralNaturalGenerator -> RuralNaturalPiece -> RuralGenerator.generateNaturalChunk`。新计划先由 `RuralRoadPlanner` 确定完整道路，再沿用 legacy building candidates/角色选择，接纳建筑时同步预留连接路，最后规划避让连接路的农田。四档 reservation 仍为 56/72/96/128 方块；StructureSet spacing=40 chunks、separation=20 chunks、salt、40/30/22/8 tier 权重、biome tag、地形准入阈值和怪物生成均未改。新道路及 access 占地会改变候选建筑的可用性，因此不能把“调度频率配置未改”解释为成功聚落接受率一定不变。

源码位于 `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/`：

- `RuralRoadType`：MAIN/SIDE/FARM_TRACK 的有效宽度、路肩、过渡和材质选择。
- `RuralRoadSegment`：XZ 轴向起终点、类型、方向、含端帽的完整占地。
- `RuralRoadNetwork`：从计划的道路生成图；在分支接点拆分主路，保存 START/END/TURN/THROUGH/T_JUNCTION 节点、路段、候选 frontage 和已接纳 access bounds。候选 anchor 不自动占用世界；已接纳建筑的 access 才进入 occupiedAccess。
- `RuralLotAnchor`：connection、frontage、朝路方向、候选/实际 lot bounds、连接路 segments 与完整 access bounds。
- `RuralRoadPlanner`：有界、非网格的四档布局，种子仅在 plan 阶段参与选择。
- `RuralAccessPlanner`：建筑和连接路一起检查；当前入口仍为正面包围盒中点，未消费 metadata socket。下一轮可在入口解析处用旋转后的 socket 替换 midpoint，再迁移正式 lot/frontage 选择。
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

兼容建筑候选仍使用旧 offset，完整 lot-anchor 驱动放置留待下一轮。每个候选在地形检查前尝试向现有道路中心线连接；从正面中点外移 2 格起步，再沿朝向外延，通过轴向折线接入道路。通道宽 3 格，所有矩形在 reservation 内，避开自身与其他建筑；其他建筑保留 2 格地形混合缓冲。新的建筑不能占用已预留 access，新 access 不能穿过已有建筑或另一条 access；只有已有道路占地内部允许共享接入。农田含围栏包络也检查 access，并留 1 格间距。建筑自身的地形 blend ring 跳过预留通道。

这是一份计划内部的占位规则，不是跨 Rural/Highway/其他 POI 的 Spatial Claim。尚未把所有建筑迁移到道路候选 frontage，也未新增 zoning。新 anchor 数据目前由 network 提供，实际 legacy 建筑接纳产生的 anchor/access 参与 occupancy 和 painter，非只声明未使用的数据结构。

## 确定性与保存

`RuralPlan.Road` 对新计划携带 segment；旧四参数构造保持 legacy 语义。Piece 保存 `RoadTypeV1`、`StartV1`、`EndV1`，每个 lot 保存 `RoadAccessV1` 的 connection/frontage/segments。恢复时从保存几何重建 graph，不在 chunk 到来时随机扩路。新道路计划缺失 lot access 会拒绝恢复；缺 V1 字段的旧计划继续原有逻辑。标记为 `ROAD_NETWORK_V1` 的新计划与旧 `MAIN_T_BRANCH` 记录区分。

Painter 以完整计划生成当前 chunk 的截片，材质由坐标/seed 决定，并按固定优先级合并交会格。维持地表高度查询；不对道路进行大规模挖填、坡度求解、桥梁或隧道工程。跨系统地形覆盖、道路实际通行和逐 chunk 生成顺序的游戏表现仍待实机验证，编译不证明这些运行结果。

## 边界与验证

无现代公共路灯、sidewalk、traffic light、road sign、bridge/tunnel；道路夜间不提供公共照明。未改 City/Small Town/Highway、未扩 160×160、未新增方块/贴图/资源 JSON/NBT。

本轮一次 `gradlew.bat --offline compileJava --console=plain` 成功（36 秒，现有弃用/unchecked 警告）。没有资源 JSON 修改，未运行 processResources；未 runClient/clean/GameTest、大量测试、世界生成或性能测试，未 commit/push。WG-06 的 24 条旧道路摘要属于历史基线，新布局不再承诺逐项相同；本轮未运行或重写该 fixture。

用户验收建议：在新生成区观察四档形态，重点检查 Cluster/Full 转角及 T 口材质、六类建筑/农田仍可落地、access 无穿插；保存重进并跨区块观察道路连续性。实际接受率与视觉效果未在本轮确认。
