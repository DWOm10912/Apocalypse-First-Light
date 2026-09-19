# Rural Building / Lot Planning V2

状态：自然生成主路径已接入，离线 compileJava 成功。未运行客户端、GameTest、多 seed 回归或性能测试；实际接受率、四向布局、存档往返、入口高差与视觉由用户验证。

## 权威路径

RuralNaturalStructure.findGenerationPoint 先执行既有 Highway reservation 冲突检查；通过后 RuralNaturalGenerator 继续原有 site gate 和八资产加载，调用 RuralRoadNetwork.from → RuralFrontagePlanner.frontages → placements → RuralAccessPlanner.connect → 原有 lot terrain evaluation → 接纳 → 原有 RuralFarmPlanner.planBounded。

自然路径不再调用 RuralLayoutPlanner.candidates，也不再以中心 ±10/±30、侧向18/30 或其4/8格偏移重试作为位置来源。RuralLayoutPlanner 的 rotationFor/boundsAt 继续复用。角色需求、原角色槽顺序和数量、selectNatural、最少建筑数和必要角色拒绝条件保留；选择资产后再按真实尺寸评估其 frontage lot。未启用新的权重或 maxCount 语义。

## Frontage 与真实 lot

- 从 RoadNetwork 拆分后的实际轴向 segments 两侧取候选，沿段从距端点2格开始，每6格取一个中心，距另一端至少2格；再由 seed/中心/朝向稳定排序。这是搜索步长，不是要求每6格建房。
- 按角色给道路类型排序：农业/utility/landmark 优先 FARM_TRACK、SIDE、MAIN；住宅/farmhouse 优先 SIDE、MAIN、FARM_TRACK。属于偏好，短道路或单路网可以回退，不新增 zoning 或 asset-ID 特判。
- building front 按 metadata front 旋转至 frontage.facing（道路该侧的朝内方向）。通过真实 StructureTemplate.getBoundingBox 的旋转框及 front midpoint 求 origin；真实建筑沿路宽度必须落在所属段的长度内，建筑矩形必须完整位于 reservation。
- 通常从完整道路包络外退3格；farmhouse/barn 优先6格，再尝试较近位置，farmhouse最低3格、AGRICULTURAL_LARGE最低2格。大农业建筑较小退距用于兼容现有小档 reservation，不扩大区域或改道路。参数集中在 RuralFrontagePlanner。
- usableBounds 是该模板拟占用的实际矩形，不是所有建筑共用的固定 lot 尺寸；depth 从朝向和矩形计算。RuralLotAnchor 保存 sourceRoad、frontage中心、facing、usableBounds、connection、entry/entryFacing 和 access。
- 建筑间至少4个空格；新建筑也避开既有 access 的2格缓冲。候选不要求连续填满每条路，不增加目标建筑数。

## Socket 与 access

从 RuralStructureCatalog 获取该正式资产 metadata，按声明顺序选择首个在真实模板XYZ范围内、位于外边界且 facing 向外的 socket；用 StructureTransform.world 和同一 rotation 转为入口坐标/朝向。没有合法 socket时采用旋转后正面框中点 fallback。建筑正面朝向和入口朝向分别保存，因此谷仓西侧入口不会被误当作南侧正面入口。

入口向外2格为宽3格通道起点，通道端帽触及模板外紧邻格而不进入模板；连接点投影至所属 road segment 的外路面/路肩边缘。尝试两种轴向 L 形路径，去掉零长度段，因此最多一个直角弯；不做 A*。首个通过完整占地检查的路径被保存。合法 socket 的路径不可达时拒绝该 placement，继续下一 frontage，不悄悄改用别的建筑面。

access 是现有地表 FARM_TRACK painter 的水平XZ路径，保存真实 socket Y 但不新增坡道、楼梯或垂直通行求解；门廊台阶的实机高差仍需验收，不能以编译通过宣称入口可通行。

## Occupancy 与农田

建筑与通道接纳前联合检查：完整 reservation、道路完整包络、其他建筑及既有 access。建筑不能压道路；通道不能穿自己/其他建筑，不能与已接纳通道重叠，也不能穿其他 road。仅允许在所属道路终点连接邻域接触路面边缘/路肩，不允许沿主路长距离重叠或横穿至另一侧。

农田仍最后规划，沿用 existing fencedEnvelope 对道路、建筑、access及已有农田的检查；新建筑阶段还没有已接纳农田，因此无需把旧农田算法反向重写。农田外观、作物、围栏、田埂、变体完全保留 Legacy，Farmland V2 未实现。terrain preparation、道路 painter 和真实 lot 地形门槛未改。

## 自然八资产与四档

自然生成池现包含原六资产 farmhouse_01、barn_large_01、house_small_01、storage_small_01、grain_silo_01、water_tower_01，以及同角色变体 farmhouse_02、house_small_02；全部使用同一 V2 geometry/access 路径。除 water tower 无 socket 时使用正面中点 fallback 外，其他资产优先消费其现有合法 metadata socket。同角色变体初选使用统一的确定性选择器：world seed、Rural 中心 X/Z、选择角色及 placement slot index 分别混入 64 位 key，经 SplitMix64 finalizer 后对候选数取模；同一 seed/中心/角色/slot 结果稳定，不受 chunk 加载顺序影响。旧公式直接对 `seed ^ center.asLong()` 取模；由于中心 Y 恒为 0，两个 farmhouse 候选的索引在同一世界被 seed 奇偶锁定。现在 farmhouse 与住宅的 `_01/_02` 在不同 Rural/slot 的理论初选机会相等（各约 50%，不保证少量站点均匀）；选中后仍先尝试其 lot，失败才尝试同角色另一变体，因此最终落地比例可能不同。不增加角色槽、目标建筑数或新的 weight/maxCount 语义。Legacy 六资产配方及 `/afl rural` 开发命令池保持原样；未修改 NBT、metadata 或 recipe JSON，也未改变 tier、spacing、biome、农田、Highway 或 Building / Lot V2 几何。两栋 `_02` 的自然生成率、四向实机布局与入口通行仍待用户验证。

落地阶段的 `RuralFoundationSupport` 原先用旧六资产 `definitions()` 建立受管模板 ID 集，导致规划已选中的两个 `_02` 在 `metadata()` 抛出 `Unsupported Rural foundation template`，可能留下仅有道路的残缺 Rural。现改为从自然八资产 `naturalDefinitions()` 派生该集合，不再维护第二份模板 ID 白名单或为 `_02` 复制 `_01` 参数。foundation support mask 仍逐个从**实际选中的 NBT 模板**及其 metadata ground anchor 生成：`farmhouse_02` 和 `house_small_02` 均为 anchor 0，沿用原六资产相同的计算流程，但不假定其地基方块布局与 `_01` 相同。原六资产、变体选择器、Building / Lot V2 几何与地形门槛均未改；本次仅 `compileJava` 通过，实际建筑落地、support/fill/cut 和旧世界残缺区修复情况尚未实机验证（已生成区不会自动重建）。

Isolated目标仍1栋、Farmstead目标仍2–4栋；后续 [Scale / Tier Tuning V1](rural_scale_tier_tuning_v1.md) 已将Cluster目标改为6–8、有效最低4，Full目标改为9–12、有效最低8，档位权重改为25/30/30/15。住宅/农业/谷仓必要角色条件仍保留。空间不足或地形不合适时继续原候选拒绝机制，不靠降低间距保证成功。spacing=40 chunks、separation=20 chunks、biome/site gate未改。新规模的实际接受率尚未实测。

## 保存、兼容和开发命令

RuralNaturalPiece 保留原 origin/rotation/bounds/baseY 和 RoadAccessV1 路径字段；V2 加 SourceRoadV2、UsableBoundsV2、EntryV2、EntryFacingV2。readAccess 检测 V2 字段恢复完整 anchor；旧 V1 字段仍可读取，不回溯重排旧 Piece。RoadNetwork 只发布实际接纳 anchors，移除原不参与落地的统一尺寸示意 anchors。

所有位置/路径在 plan 阶段确定，排序不使用全局随机状态或 chunk 到达顺序；每 chunk 按保存计划回放，不重新选择建筑。此处只说明规划数据路径，不宣称已有地表 painter 的跨 chunk 高度采样已完成实机顺序一致性验证。

自然路径为 V2 权威。`/afl rural` 仍保留旧固定offset/weighted selection/maxCount/现场地形及失败规则，继续使用 V1 connect 重载与 midpoint；未为开发命令扩大重构。请用新自然生成区验证 V2，开发命令不能作为 V2 布局预览。

Highway claim ±32、Rural buffer12、Highway>Rural规则原样保留；V2建筑/通道不得越出已经通过冲突检查的reservation。未修改 Highway、怪物、City/Small Town。

## 本轮验证

`gradlew.bat --offline --gradle-user-home .gradle-user compileJava`：BUILD SUCCESSFUL，36秒，现有弃用/unchecked警告。无resource变更，未运行processResources。未runClient、clean、GameTest、大量测试、commit/push；未编辑.obsidian/workspace.json。
