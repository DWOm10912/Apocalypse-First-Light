# AFL 北美道路网络 V1-A：规划层与建筑地块实施记录

日期：2026-10-06。环境：Minecraft 1.20.1 / Forge 47.4.22 / Java 17。权威设计依据为 [北美道路与建筑地块统一规格 V1](north_american_roads_and_lots_spec_v1.md)。

本文记录**规划数据实现**，不是自然生成施工验收。V1-A 不注册 Structure、Feature、StructureSet 或建筑池，不写世界方块，不复活旧 Rural。主岛/卫星岛、MacroGeography、Highway 路线、生物群系、辐射、地堡及正式建筑资产保持原状。

> **2026-10-07 验收更新：V1-A = PASS（用户确认）。** 用户明确确认“V1-A 可以视为通过”，并在 V1-B 任务中确认 synthetic mixed/t 均为 PLANNED、三类断面正确、connected=true、T 字三臂、地块无非法重叠及坐标统一。该结论覆盖本阶段规划层交付，不代表 V1-B 施工、自然生成、正式 NBT、视觉或性能专项验收通过；不补造未提供的具体计数。以下首轮结果保留为历史记录。

> **后续实现：** [V1-B 施工层](north_american_roads_v1b_implementation.md)已增加开发命令触发的真实逐格施工、实际地形纵坡、薄路面/路缘和有限持久化恢复；尚待用户实机验收。本文以下“本轮/当前/下一步”描述 V1-A 阶段，V1-A 的二维几何及只读 plan/synthetic 命令保持不变；不得据此忽略已经实现的 V1-B 独立入口。

## 0. V1-A 断面修正与第一次用户验收

旧 `north_american_roads_v1a_1` 将 R12/C14/I12 命名中的沥青宽度当成完整道路宽度，得到沥青/完整走廊8/12、10/14、10/12。**旧正式规格文档也记载了相同错误**，因此本轮同时纠正文档与代码，不能将原因描述成只有JSON展示错误。现在以用户明确确认的12/22、14/26、12/24为准，路缘、设施和人行道单独预留。几何、路口、地块及claims一并重算，不仅替换导出数值。

用户报告首版实际执行：`/afl roads synthetic mixed` 返回 `PLANNED / nodes=7 / edges=8 / lots=21 / connected=true`，节点为 `CROSS=1 / TURN=6 / T=0`；`/afl roads plan mixed` 返回 `REJECTED`，原因为 `TERRAIN_WATER_OR_NON_SOLID` 和 `NETWORK_RELIEF_EXCEEDS_FLAT_PLAN_BUDGET`。这些是**用户对旧版本的实机结果**，不是本轮再次运行，也不证明T字口或修正后宽度已验收。

本轮不放宽地形规则，不要求维持21个地块，不修改A1设计；新计划使用 `north_american_roads_v1a_2` 与新的JSON schema。新增明确的合成T字诊断预设，修正版规划层已于2026-10-07由用户确认通过。

## 1. 实现状态与范围

| 项目 | 当前状态 | 边界 |
| --- | --- | --- |
| R12 / C14 / I12 道路图 | 已实现规划代码 | 统一节点、稳定 ID、完整道路走廊；不铺方块 |
| 直线、90°、T 字、十字 | 已实现规划代码 | 有效连接臂决定节点类型，图连通性校验失败则拒绝 |
| 住宅、商业、工业布局 | 已实现不同有界布局策略 | 不模拟交通流，不是完整城市分区 |
| 完整地块、主体、入口和功能分区 | 已实现数据与规划代码 | 九种用途和多个地块变体；不加载或放置正式建筑 NBT |
| Highway 占地 | 接入现有只读 claim 查询 | 不修改 Highway 路线，不自动创建高速出入口 |
| 地形资格 | 已实现生成器基础地形适配 | 有限采样、明确来源、UNKNOWN 拒绝；不是地表装饰/玩家内容扫描 |
| 高程 | 部分实现 | 当前网络采用单一整数 G，S 的规划整数值为 `16G−3`；不做纵坡、切填或薄路面施工 |
| 灰盒 | 已实现为数据 | 主体体积、朝向、入口和分区可以交给下一阶段；没有实物灰盒 |
| 生产自然生成、跨候选网络连接、按 chunk 施工 | 尚未实现 | V1-B 与后续任务；当前跨 chunk 的坐标连续不等于施工接缝通过 |
| V1-A 阶段验收 | PASS（2026-10-07 用户确认） | mixed/t、断面、连通、T三臂、地块与坐标通过；V1-B施工、视觉及性能专项不在本次通过范围内 |

## 2. 固定契约与本轮预算

- 新建筑资产默认正面 `NORTH / -Z`；旧资产使用其真实显式 `front`，不改旧 NBT，也不对所有模板统一加 180°。
- `G` 为整数建筑行走面，`S = G−3/16`，`surfaceH16 = 16G−3`；NBT 放置原点仍为 `G−ground_anchor_offset_y`。
- 道路沥青/完整ROW宽度：R12=12/22、C14=14/26、I12=12/24。每侧的路缘/设施/人行道分别为1/2/2、1/2/3、1/3/2。中心线位于偶数断面的两列块之间；沥青和完整ROW围绕同一中心对称，占地均为整数半开区间。
- 标准加油站是临街宽 64、纵深 72；A1 是显式 64×64 变体，不能缩放或默认塞入另一种地块。
- `RoadPlanningConfig.DEFAULT`：候选范围 512×512、道路网络最大范围 384×384、最多 4 次布局尝试、64 条边、64 个地块、8192 个不同地形列、采样步长 8、最大局部切填预算 3 格。所有参数进入计划身份；这些是当前受限 API 配置，不是已接入 Forge 配置页面或世界存档 profile。
- 统一规格中最初的 256×256 / 8 次尝试 / 256 项 LRU 是历史建议，V1-A 按本轮授权采用上述预算。没有常驻道路计划缓存；调用内的有限地形缓存随命令结束释放。

## 3. 确定性与完整计划

`RoadPlanner.VERSION = north_american_roads_v1a_2`。修正版不再与旧窄走廊计划共用算法版本。计划身份包含 world seed、候选 ID、中心坐标、布局类型、规格版本、配置、TerrainSource及地块目录 fingerprint。节点 ID 含稳定坐标，边 ID 含两个规范端点；地块也使用稳定身份。当前无道路计划缓存或已启用的持久化重放，不存在自动复用旧几何；旧导出JSON只能作为历史诊断，V1-B不得把其version1几何按version2解释。

道路布局只使用该稳定输入派生的旋转和有限位置尝试。交点分割、节点及边排序、claim 归一化和连通检查不依赖 HashMap 遍历、系统时间、玩家探索方向或运行时随机源。相同输入还要求相同地形来源/生成器与占地快照；不能把当前世界保护条件改变后的结果称作同一输入。

`plan_id` 是请求身份，不是包含全部地形/保护结果的内容hash。不同保护快照可以使同一请求由PLANNED变为REJECTED；未来持久化必须另行冻结输入快照。图的 edge corridor 与 node footprint 在交接处有意重叠，**节点具有后续施工/标线终止优先权**；对外 road claims 将该几何并集切分为互不重叠的矩形 tiles，不让通用占地索引把同一计划的边/节点重叠误判为冲突。

住宅布局采用较短街段和不完全闭合的街区连接；商业布局使用较长主街与较疏转角；工业布局使用较少连接的较长服务道路。`mixed` 布局同时包含三种道路类型。不是给同一棋盘格换三种宽度。

断面修正后，商业/mixed预设的基础街段跨度由144调整为152，保持原拓扑与有界布局。原因是C14扩大至26后，节点21格半长加24格入口净距，使144格街段可用入口跨度只有54格，放不下标准64×72加油站的60格入口总跨度；152提供必要余量。最大偏移16与节点21叠加为189，仍在384×384道路预算半径192内。没有扩大A1、放宽24格规则或改写正式建筑设计。

生成器先将相交或共端点的线段分割成统一图，再依据实际方向计算 END / STRAIGHT / TURN / T_JUNCTION / CROSS。节点携带中心、所有有效臂、道路类型、关联边和完整 footprint；臂过渡为 8 格。T 字恰有三臂，十字恰有四臂。混合宽度连接在节点内协调。

`RoadCrossSection`为直线边提供沥青、两侧路缘、设施带、人行道及完整ROW矩形。`RoadJunction`为节点按嵌套阶段半宽和真实连接臂的并集分配同类空间；外阶段减去内阶段，得到互不重叠的沥青/路缘/设施/人行预留分区。节点足迹保留最大完整半宽并在真实连接方向延伸8格，连接臂的停止边界、共享G和edgeId均归节点所有；不能用较窄道路代替整个混合节点，也不生成T口第四条臂。道路相邻节点出现非法footprint重叠时必须拒绝，不截去其中一半继续成功。这里是离散规划分区，尚非最终路缘曲线、行人过街铺装或标线模型。

道路超预算、出界、冲突、地形不合法或图不连通会拒绝完整尝试；全部尝试失败返回 `REJECTED`、失败原因和空节点/边/地块/claim，不能把截断的半条路作为成功结果。合法道路旁的个别地块可以因尺寸、入口、地形或冲突拒绝，并计入地块诊断；没有合法地块则整个计划拒绝。

## 4. 地形和保护的实际含义

`RoadTerrainQuery` 绑定当前调用的 ServerLevel、ChunkGenerator 和 RandomState，只调用 `getBaseHeight(WORLD_SURFACE_WG)` 与 `getBaseColumn`。它不读取/强载世界 chunk，不借用 `MacroGeography.surfaceHeight` 伪造真实高度，也不回退到当前已放置方块。

真实来源为 `NOISE_PRE_DECORATION`，高度使用 `TerrainSample` 的“表面上第一格位”契约。向下最多扫描 64 格确认支撑和液体，区分 solid / water 或 other fluid / ice / unknown。非法、缺失、预算用尽或来源不匹配不视为平地；未知地形拒绝。噪声查询的 protection 始终是 `UNKNOWN`，这是明确的信息边界。

当前以道路 footprint 的有限规则采样点选取中位数 G，所有样本偏离 G 不得超过切填预算。**只规划这一平坦网络的标高，不实际整平**。有限步长不能证明采样点之间每一格均可施工；V1-B 仍须按施工体积补充更密预检和保护快照。

占地输入采用现有 `SpatialClaim`，最多4096条，超出返回 `BLOCKER_CLAIM_BUDGET`。通过公开 `ClaimQueryResult` 归一化并保留完整性 `UNKNOWN`，不把传入列表假称全世界保护全集；道路与地块预检已知外部 claims。诊断会传入 Highway 完整走廊/接缝/跨海桥预留、启动保护区与已知地堡范围。未发布的其他结构或玩家建筑不是“确认无保护物”；生产施工不能只凭 V1-A 的 `PLANNED` 状态放行。旧 Rural 没有候选、占地 provider 或永久保留障碍。

## 5. 已知限制与明确未实施内容

1. 不生成道路、标线、人行道、路缘、路灯、正式建筑或建筑 NBT 灰盒；不创建自然生成注册。
2. 不实施道路纵坡、逐格切填、1/16 坡面、桥隧、完整城市规划、交通或车辆寻路。
3. 当前网络内部节点共用 G；跨候选网络的接驳、共享边、持久化 plan/profile 和旧世界版本迁移尚未接线。
4. 只保护明确输入或诊断 provider 发布的区域；没有完整 Vanilla/mod 结构及玩家方块保护扫描。
5. `/afl_author` 的 SOUTH 硬编码、地下油罐多方块旋转、`structure_void` 留白及分数道路施工均未在本轮修复。这些不阻塞无写入的规划层，不代表后续正式 NBT/道路施工已就绪。
6. 无地图 GUI；二维俯视检查使用导出的世界 X/Z bounds、道路节点与地块数据。

## 6. 地块数据与建筑资产接口

打包目录：`src/main/resources/data/apocalypse_firstlight/afl_worldgen/roads/lot_catalog_v1.json`。`schema_version=1`，`revision=lots_v1a_1`。这是 Java classpath 加载并校验的只读目录，**不是已实现的 datapack 热重载或世界级保存配置**；目录内容 fingerprint 参与计划身份。

目录最多32个变体、128 KiB；本轮10个变体覆盖9种用途。默认 `lot_gap=4`、`node_clearance=24`、`terrain_step=8`、`max_height_difference=3`。重复ID、未知/缺失字段、越界功能区、冲突主体/停车/服务范围、缺失或不连通入口路径均由 parser 拒绝。

| 用途 `use` | 变体 ID | 临街宽×纵深 | 允许道路 |
| --- | --- | --- | --- |
| `suburban_small` | `suburban_small_24x32` | 24×32 | R12 |
| `suburban_large` | `suburban_large_32x40` | 32×40 | R12 |
| `convenience_store` | `convenience_40x48` | 40×48 | C14 |
| `gas_station` | `gas_standard_64x72` | 64×72 | C14 |
| `gas_station` | `fuel_stop_a1_64x64` | 64×64 | C14主临街，另需相容支路 |
| `restaurant` | `restaurant_40x48` | 40×48 | C14 |
| `auto_repair` | `auto_repair_48x56` | 48×56 | C14/I12 |
| `hardware_store` | `hardware_56x64` | 56×64 | C14/I12 |
| `warehouse` | `warehouse_64x64` | 64×64 | I12 |
| `industrial` | `industrial_80x96` | 80×96 | I12 |

尺寸全部在数据文件中；用途与未来正式 NBT ID 分离，新增变体无需在道路布局算法中写死另一套尺寸。权重决定确定性候选顺序，不保证每个计划一定包含九种用途，也不保证一定有 A1。

局部地块正面 NORTH，原点为完整地块最小 X/Z；`size=[W,D]`。矩形字段是 `[x,z,width,depth]`，不是 `[minX,minZ,maxX,maxZ]`。完整地块、主体、停车、服务区及内部通路分别表达；主体四周剩余距离给出实际前/侧/后退界，不把整个地块当成建筑 NBT size。

`building` 指定 `rect`、`height`、`ground_anchor` 与主体局部 `entrance_x`。`entrances` 指定名字、`PEDESTRIAN` / `DRIVEWAY` / `SERVICE_ROAD`、临街 `side`、沿该侧的 `offset`、净宽 `width` 和连续 `paths`。停车/后勤矩形是占位功能区，当前没有逐个真实车位、设备或房间。

地块分配保留完整 bounds，并从**完整ROW外缘**重新计算临街位置：北南道路使用横向半宽11/13/12，东西道路在Z轴使用相同规则；不是从沥青半宽6/7/6开始放地块。完整地块重新检查道路/节点、外部保护和已分配地块，主体、退界、停车、后勤、内部通路随该地块一起作整数旋转和平移。车辆入口按**入口最近边缘到节点完整 footprint 投影边缘**测距，至少24格；不能按节点中心测距。转角A1同时需要两条合法临街道路及两个入口检查，不合法就拒绝该变体，不缩窄安全距离强行放入，也不为保留旧21块计数牺牲规则。

地块候选最多2048次，达到64块或候选预算后结束地块尝试并记录原因；不会截断道路图。当前策略保守地将24格检查也应用到行人入口。地块terrain检查额外要求自身样本总高差不超过3格，且不超出相邻道路G的切填预算。

扩大后的入口不再只触碰ROW外缘就视为接通：连接器增加 `roadAccess`、`sidewalkContact` 和可选 `asphaltContact`。行人从完整地块入口接到所属侧的整个人行道宽度，端点高度G，`asphaltContact`为空；车辆/后勤保留原净宽，`roadAccess`跨越人行道、设施与路缘带到沥青边缘一格接触条，端点S。该道路内通道必须完全包含在所属edge corridor、避开所有节点及外部保护claim；它已属于该道路的SpatialClaim，不另发一个互相冲突的建筑claim。地块内部通路与道路侧接入分别保留，禁止把跨人行道通道当作建筑可占地。

`roadSurfaceH16`表示该连接器的实际接触目标：行人为人行道G，车辆/服务为沥青S；`lotSurfaceH16`对应入口的G或S。车辆连接器的 `curbTransitionH16=[S+1,S+2,G]`只是路缘过渡建议，**不等于已设计出整个人行道横穿段的纵断面**；行人G→G无需此序列。具体降缘、贯穿人行道时的连续步行面与薄路面施工留给V1-B。

旋转后的输出还会复核主体 `StructureTransform.bounds`、socket世界Y=G及朝向、功能区/通路不出地块、入口净宽、roadAccess不侵入lot、主门外侧与人行通路衔接。数据目录和A1建筑设计不因断面修正而改尺寸。

A1配方使用规范归一化后的 NORTH/WEST：店主体占位 `(17,37)`、31×20、height10、ground_anchor2；S1 是 NORTH、offset47、width9，E1 是 WEST、offset57、width6。它只复现本阶段需要的接入约束与大体占位，没有宣称整个A1施工图（顶棚、油罐、管线、景观）已经完成或可直接放置。

建筑朝向分成资产本地正面、地块临街方向、最终世界方向。当前占位使用 NORTH 本地正面；四向旋转与 integer-cell bounds 复用零 pivot 的 `StructureTransform`。主体放置原点按旋转 bounds 修正，Y使用 `originY(G, ground_anchor)`。合成 `StructureSocket` 只表达入口空气格语义，未创建伪造存在的 `StructureDefinition` 或 NBT 注册；正式资产将来必须读取真实 definition/size 重新校验，旧 SOUTH 资产按显式 front 选择旋转。

## 7. 诊断、二维数据与用户最小验收

命令只在现有开发命令入口注册，要求权限2且位于主世界。每个服务器5秒冷却，不进行持续/每帧规划。

```text
/afl roads plan residential [x z]
/afl roads plan commercial [x z]
/afl roads plan industrial [x z]
/afl roads plan mixed [x z]

/afl roads synthetic residential [x z]
/afl roads synthetic commercial [x z]
/afl roads synthetic industrial [x z]
/afl roads synthetic mixed [x z]
/afl roads synthetic t [x z]
```

`[x z]` 表示两个可选整数参数，使用时不输入方括号；省略时取执行者 X/Z 向下取整。这里的 X/Z 是候选中心，不是候选西北角。当前没有自动扫描岛屿寻找城市候选。

- `plan`：真实 generator 的基础地形。水域、陡起伏、未知、预算或占地冲突可以正常返回 REJECTED，不能为展示成功而伪造高度。
- `synthetic`：明确标记的合成平地 `STRUCTURE_PLANNING / G=64`，仅用于审查道路图和地块逻辑。仍消费真实 Highway/启动保护/已知地堡 claims；**不是实地地形或自然生成验收**。
- 两种模式均不改世界方块、不强载候选覆盖区块。不因使用真实地形查询而自动取得正式施工权限。

开发运行目录输出 `afl_debug/roads/{plan|synthetic}_{layout}.json`；常规项目 `runClient` 对应 `run/afl_debug/roads/`。原有8个固定文件名保留，新增 `synthetic_t.json`，最多9个；每次覆盖同模式/布局的上次结果，不按坐标或时间无限累积文件。T仅作为合成诊断，不增加真实地形 `plan t`。JSON包含世界 X/Z 数据，可用于二维俯视绘制；本轮没有额外地图 GUI 或生成预览方块。

输出包含候选/计划/规格身份、节点/边/地块、节点类型和连接臂、R12/C14/I12分布、用途统计、完整占地/主体/入口/分区、G及h16、claims、连通状态、失败原因、采样数/尝试数，以及 diagnostic_context 中的种子、来源、合成标记、Highway/已知保护数量。Highway查询返回数量不是“发生碰撞数量”；实际拒绝原因以计划 diagnostics 为准。

`RoadPlanJson` 的导出schema升为2：保留 `spec_version`、`plan_id`、`candidate_id`、`layout`、`status`、`construction_authorized=false`、`candidate_bounds`、`counts`、`nodes`、`edges`、`lots`、`claims`、`diagnostics`。`total_width`保留但明确等于完整ROW，新增的分带数据不能与旧schema混用。矩形对象明确使用 `min_x/min_z/max_x_exclusive/max_z_exclusive`。`counts.road_types`三类均存在，`node_kinds/building_uses`只列本结果出现的项。建筑有 `placeholder_only=true`、`actual_nbt_bound=false`，保留 `asset_local_front/world_front/rotation/placement_origin/lot_local_offset/main_socket/main_socket_world`；连接器保留道路ID、方向、入口条带、内部路径及S→G规划高度序列。地块 `generation_version` 与顶层 `spec_version` 均使用道路算法版本；目录自身另有 `revision` 和内容fingerprint，不能将目录revision冒充道路算法版本。

schema2 的分区字段如下，均为规划数据：

| 位置 | 字段及含义 |
| --- | --- |
| 顶层 `cross_section_types`、每条edge及node arm | `asphalt_width`、`right_of_way_width`、`total_width`；后两者均为完整ROW。`curb_reservation`、`utility_band`、`sidewalk_width`均为**单侧**宽度，不能将其误读成两侧之和 |
| `edges[].cross_section` | `asphalt`为一个沥青矩形；`curbs`、`utilities`、`sidewalks`为两侧矩形列表，按X或Z负侧到正侧排列，不随行车方向交换 |
| `nodes[]` | `arm_extent`为节点中心到边接缝的距离；`regions.asphalt/curbs/utilities/sidewalks`均为互斥分区矩形列表；`arms[].edge_join_x/edge_join_z`给出该连接臂边界。`marking_stop_boundary`说明节点优先的标线终止契约，不是已经施工的标线 |
| `lots[].connectors[]` | `road_access`为道路侧接入预留，`sidewalk_contact`为人行道接触范围，车辆/服务入口的`asphalt_contact`为沥青边缘接触范围，步行入口该字段为`null`；`access_geometry=RESERVATION_ONLY`明确没有真实路缘切口或过街坡面 |

最小回归检查清单（V1-A已获用户总体确认；保留供后续回归，代理未代跑）：

1. 在启动保护范围及Highway之外选择固定候选中心，运行 `/afl roads synthetic mixed <x> <z>`。确认版本 `north_american_roads_v1a_2`、schema2、PLANNED、connected=true；R12/C14/I12的 `asphalt_width/total_width` 必须分别是12/22、14/26、12/24。检查实际corridor半宽11/13/12及路口分带，不仅看这两个数字。新地块数量不要求等于21。
2. 等待5秒，运行 `/afl roads synthetic t <x> <z>`。合法计划要求 `nodes=4 / edges=3 / T_JUNCTION=1 / END=3`；唯一T节点有且仅有三个方向、三条不同edgeId，并接入R12/C14/I12各一条，缺失方向不得出现第四条边。节点分带连续、止线边界一致、附近lot不侵入完整ROW。该断言已写入规划预设，**未收到单独T预设的新版输出记录，不虚构计数**。
3. 等待5秒，对相同模式/坐标再次运行，比较planId及整个计划数据；相同种子、来源、目录、版本和保护输入时应一致。使用不同候选观察其它确定性旋转，再检查入口净宽、主体socket方向和G、`road_access/sidewalk_contact/asphalt_contact`、地块间距及24格节点退距。
4. 运行 `/afl roads plan mixed <x> <z>`，确认真实地形与合成模式明确分离。水域/高差仍可按原规则REJECTED，不以放宽限制换取PASS；Highway/启动保护仍参与，未知不自动成为安全施工区域。世界方块应无变化。

原有residential/commercial/industrial命令均保留，可后续分别检查其不同布局；A1不一定被每个候选接受，须看变体拒绝原因，不能以有无某个变体或旧地块计数代替占地验收。

## 8. 本轮文件清单与复用关系

新增 Java 位于 `src/main/java/com/antaurora/apofirstlight/worldgen/roads/`：

| 文件 | 职责 |
| --- | --- |
| `RoadType.java` | R12/C14/I12沥青及每侧路缘/设施/人行道，派生完整ROW |
| `RoadCrossSection.java` | 断面修正版新增；四向对称的沥青/路缘/设施/人行道世界矩形 |
| `RoadJunction.java` | 断面修正版新增；真实连接臂、混合宽度节点的完整footprint及互斥分区 |
| `RoadPlanningConfig.java` | 显式有界预算与默认值 |
| `RoadPlan.java` | 不可变计划、节点/臂/边、状态与诊断 |
| `RoadPlanner.java` | 有界布局、交点图、连通性、地形/claim资格、完整尝试、分割claim |
| `RoadTerrainQuery.java` | 无chunk加载的真实基础地形查询 |
| `RoadLotCatalog.java` | 严格地块目录parser、进程内只读snapshot/fingerprint |
| `RoadLotPlanner.java` | 完整地块、功能区、四向占位、入口/道路连接和拒绝原因 |
| `RoadPlanJson.java` | 明确的JSON诊断/二维数据schema |

其他本轮代码/资源：

- 新增 `src/dev/java/com/antaurora/apofirstlight/dev/RoadPlanningCommand.java`。
- 修改 `src/dev/java/com/antaurora/apofirstlight/dev/AflDevCommands.java`，只挂接开发子命令。
- 新增 `src/main/resources/data/apocalypse_firstlight/afl_worldgen/roads/lot_catalog_v1.json`。

复用而未改写：`worldgen/structure/StructureSocket`、`StructureSocketType`、`StructureTransform`、`worldgen/spatial/BoundsXZ`、`SpatialClaim`、claim排序/优先级、`worldgen/terrain/TerrainQuery`与`TerrainSample`、`HighwaySpatialClaimProvider`。真实地形使用与Highway相同的generator/RandomState基础查询边界；没有改动或假装直接复用Highway专属station/bridge高程算法。StructureDefinition仍是正式资产契约，当前无正式NBT绑定，所以不造虚假的definition。

首轮同步文档：本页、`north_american_roads_and_lots_spec_v1.md`、`unified_worldgen_architecture_v1.md`、`worldgen_architecture_decisions_v1.md`、`small_city_building_authoring_v1.md`、`fuel_stop_a1_city_interface_v1.md`。旧资产和历史验收记录保留，冲突的当前状态已明确覆盖。

断面修正本轮仅更新本页、正式规格和统一架构当前状态；没有修改A1设计/接口图纸、正式NBT或地块catalog。修改`RoadType`、`RoadPlan`、`RoadPlanner`、`RoadPlanJson`、`RoadLotPlanner`及开发`RoadPlanningCommand`，新增上表两份分区类；没有重新开发一套规划器。

## 9. 验证状态

2026-10-07 用户确认V1-A通过。本次提交仅同步验收状态，不重新执行编译或游戏。以下为上一修正轮历史验证边界：源码和文档完成后，仅由主任务执行一次 `./gradlew.bat compileJava --offline`；结果为BUILD SUCCESSFUL，compileJava为UP-TO-DATE，不表示该次重新编译源文件。没有执行processResources/build/check/test/GameTest/runClient/runServer、世界生成、性能或截图测试。§0的旧版JSON只作现有结果的只读核对；本轮未执行新版规划命令，没有将未运行的确定性/旋转/冲突/T字验收登记为通过。

## 10. V1-B 交接与当前状态

开发测试工具现有独立SEGMENT适配器，使用原断面与施工器但不生成地块；其1格紧凑端部不是对V1-A旧END/路口形状的替换。`/afl roads synthetic mixed`、`synthetic t`、`plan mixed`保持旧行为，不开放`synthetic segment`或`plan segment`。单路段及Survey命令见[开发测试工具](road_construction_debug_tools_v1.md)。

V1-B 已消费本阶段的二维节点/断面/地块/入口，另行从实际方块生成节点与入口 G、沿线 profile、逐格前后状态及守卫快照。永久薄路面、路缘、车辆入口与节点优先施工已经接通，开发台账保存精确版本/范围/状态并支持显式暂停恢复。完整玩家/第三方保护、生产世界版本锁定、自然调度和跨候选连通仍未实现；不能把开发台账当成正式自然生成生命周期。薄路面/碰撞/支撑/恢复均仍需实机验收，标线与正式 NBT 放置后续单独接入。

V1-A规划阶段已获用户确认，可以作为V1-B设计输入。V1-B 的 preview/prepare/build 与 V1-A 的 plan/synthetic 分开，只有实际预检通过且明确 plan_id 确认后才写世界；普通 PLANNED 从不授予施工权限。V1-A不会自动触发V1-B，也不会自动开放旧世界自然生成。
