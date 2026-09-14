# AFL Unified Worldgen Architecture V1

WG-05.1 更新：claim 的 `generationVersion` 为 owner-local；跨系统候选先由 profile 层逐条校验冻结版本，再进入空间裁决，不要求各系统版本字符串相同。已实现的资格入口、UNKNOWN/重复 ID 边界及合成联合回归见 [WG-05.1 实现说明](cross_system_claim_version_arbitration_wg05_1.md)。[Phase 1 Gate 复审](worldgen_phase1_gate_rereview_v1_1.md) 为 `PASS_WITH_NON_BLOCKING_FINDINGS`。WG-06 已冻结 Rural legacy 计划；WG-07/07.1 已发布八资产打包 metadata、保留旧六配方和 24 digest；两份 `_02` 来自用户人工视觉确认，四向游戏 QA 未执行，见 [WG-07 说明](rural_metadata_recipe_migration_wg07.md)。生产生成器仍未启用跨系统协调。

日期：2026-09-13。状态：**架构设计基线；Phase 1 WG-01～05.1、WG-06 Rural legacy 基线及 WG-07/07.1 八资产 metadata/旧六 recipe 兼容迁移已完成；跨系统协调未启用**。适用基线为 Minecraft 1.20.1 / Forge 47.4.22 / Java 17，包名前缀 `com.antaurora.apofirstlight`。当前有限索引、版本冻结与激活门的实际接口、限制及纯测试见 [WG-05 实现说明](claim_index_profile_gate_wg05.md)。下文保留 Phase 0 原始设计语境，目标接口/迁移计划不代表生产系统已启用；本轮仅做无图形机械/计划测试，客户端四向/in-world 验证未进行。

事实来源：[Rural Audit V1](rural_generator_audit_v1.md)（下称 R）和 [Highway Audit V1](highway_generator_audit_v1.md)（下称 H）。本文正文“当前”仅指这两份静态审查的结论，不把代码存在当成实机验收。其他内容为目标设计；决策索引见 [ADR V1](worldgen_architecture_decisions_v1.md)。初始 Phase 0 交付仅新增两份设计文档，后续实现状态以上述 WG-05 说明为准。

## 1. 决策与架构边界

采用 **MINIMAL_SHARED_CORE**。Rural 继续由 StructureSet/Structure/StructurePiece 管理候选和跨 chunk 计划回放；Highway 继续由 biome modifier 注入 Feature，以随机访问走廊和 chunk-owned writer 施工。两者在地形来源、占位、资产契约、写入权限和失败观测上协调，各自保有布局与工程算法。

```text
                  AFL Worldgen Framework（目标）
  +----------------------------------------------------------+
  | Shared: Context/seed | Terrain meaning | Claims/priority  |
  |         Write bounds/results | Failure/metrics            |
  +-------------------------+--------------------------------+
                            ^ 使用公共契约
           +----------------+------------------+
           |                                   |
  Site / Structure Layer                Infrastructure Layer
  NBT metadata / transform                     Highway
  recipe / site placement              corridor / station / grade
     |          |          |           bridge / tunnel / seam/cache
   Rural      City       Radio/Camp             |
     |          |          |                   Feature
 Structure  街区/分区    独立 placement    当前区块施工生命周期
  生命周期    专用布局       policy
           \__________ Connection anchors __________/
                    发布入口，不等于已通路
```

Rural 的有限聚落、资产选择和已序列化计划适合 Vanilla Structure 生命周期。Highway 的无限稀疏线性设施适合按需计算有限窗口；强制它持久化全部路网或包装成 NBT Structure 会增加不必要状态。公共服务在生命周期之下；不引入统一 Generator 基类。

## 2. Rural + Highway 事实矩阵

SHARE 表示共享基础实现/值类型；COORDINATE 表示输入输出契约一致、实现独立；KEEP SEPARATE 表示专用算法。每行主分类不排斥使用少量共同值类型。

| 能力 | Rural 当前（R） | Highway 当前（H） | 分类及目标 |
| --- | --- | --- | --- |
| 入口 | StructureSet random_spread 40/20 chunks | biome modifier → top_layer_modification Feature | KEEP SEPARATE |
| 生命周期 | StructureStart/Piece 保存计划并分 chunk 回放 | 每适用 chunk 查询走廊、施工 | KEEP SEPARATE |
| Seed/RNG | placement salt、tier/池选择 | NS/EW 两盐、确定性坐标 | SHARE 上下文/hash 工具；原随机序列保留 |
| 地形读取 | 自然 13 点 site、9 点 lot；命令另采样 | noise 高程与当前施工快照分开 | SHARE 来源/高度契约；采样策略独立 |
| 液体 | 自然 water/ice；命令泛 fluid | 泛 fluid 候选，无工业分类 | SHARE 分类，COORDINATE 准入阈值 |
| 地形施工 | lot 切填、支撑、田地 | 路基、深切、桥隧 | KEEP SEPARATE；共用保护/预算契约 |
| 安全写入 | chunkBox 裁剪模板/程序施工 | owner+ensureCanWrite | COORDINATE 权限及结果，writer 实现独立 |
| 跨 chunk 连续性 | 序列化有限计划 | station、256 core+192 halo、接缝指标 | KEEP SEPARATE |
| 空间占位 | 仅计划内 | 无跨系统 claim | SHARE 占位语义与查询 |
| 碰撞 | 旋转模板 XZ、道路/田地 | 同向间距与分层交会 | SHARE AABB 数学；自身布局/交会独立 |
| 失败语义 | 规划拒绝；回放异常可能部分提交 | skip/异常/无事务回滚 | SHARE 原因码与结果范围 |
| Retry | lot 偏移、田地 fallback | 无重路由 | KEEP SEPARATE |
| 部分提交 | 有风险 | 有风险 | COORDINATE 预检、报告与失败保留 |
| 持久化 | StructureStart/Piece NBT | 无 Highway SavedData、内存缓存 | COORDINATE 稳定版本/索引；实现独立 |
| 调试命令 | 与自然池选择/高度有分叉 | 有限线段/实时地形/编辑账本 | COORDINATE 正式核心与显式 override |
| 数据驱动 | NBT/placement JSON；选择硬编码 | biome JSON；路线/工程常量 | SHARE 资产注册；各自 policy |
| 性能诊断 | 预算、日志；静态 MEDIUM | cache/计时/seam；静态 MEDIUM | SHARE 指标语义；保留专用指标 |
| Connection anchor | 无 socket，driveway 用前框推导 | 无对外连接接口 | SHARE socket/anchor 值与变换 |

两份 Audit 未证明全 seed、四向 BE、多次重启或任意 chunk 顺序的最终视觉一致。H 的 seed 确定性描述的是路线与工程目标；当前世界内容仍可能影响施工。它们是迁移回归基线，不是“已通过”的运行测试。

## 3. Shared Services V1

### 3.1 Context / seed

WorldgenContext 持有世界标识、维度、world seed、系统 ID、算法/资源版本以及只在本次调用有效的 generator/RandomState/读区域适配引用。使用命名空间 hash 为新机制派生独立种子，拒绝共用一个可变 RandomSource。现有 Highway 两盐与 Rural 随机调用顺序必须由兼容模式保留；统一工具本身不意味着重新播种。

上下文不入全局静态缓存、不序列化 Level/Chunk 引用；跨线程仅传递不可变计划和数据快照。缓存键至少区分世界会话、维度、算法版本、generator/RandomState 身份和资源快照版本；失效后重建，不能把缓存命中当成已施工状态。

### 3.2 Terrain Query 与液体语义

只引入一个查询接口，按请求来源分派适配器。四种来源必须显式传入：NOISE_PRE_DECORATION、STRUCTURE_PLANNING（绑定明确的 noise/已捕获规划来源）、WRITABLE_PRECOMMIT（当前允许区域内冻结的施工快照）、CURRENT_POST_FEATURE（仅已可读取区域）。STRUCTURE_PLANNING 不允许偷偷 fallback 到实时地形；结果同时注明实际后端与采样版本。禁止不可读时加载邻 chunk 来完成查询，返回 UNKNOWN。

所有 Y 统一为整数**表面上方第一个格位**，例如顶面实体方块位于 y=63，则 topSolidSurfaceY=64；实际方块位置另用 BlockPos。TerrainSample 最少包含 validity、source、surfaceY、topSolidSurfaceY、oceanFloorSurfaceY、fluidSurfaceY（可空）、FluidCategory、实际 fluid ID（可空）、surfaceType。空字段表示该来源未提供；UNKNOWN 与 NONE 严格不同。单柱只描述最高相关液体，不能冒充完整地下流体体积探测。

FluidCategory = NONE / WATER / INDUSTRIAL_WASTE / OTHER_FLUID；validity = VALID / UNKNOWN / INVALID。未来工业废液分类用显式资源 ID/tag 映射，优先于 WATER，再到泛非空 OTHER_FLUID。冰是 surfaceType=ICE，可按 Rural policy 作为拒绝因素；不是把固态冰伪装为 fluid。BE/protected 信息只允许当前世界来源提供，noise 结果为 UNKNOWN，不能默认“无保护物”。

Slope/relief 可共享纯数组统计函数，不统一采样点数、阈值或建筑底高选择。Rural 可默认拒工业液体 lot，Highway 可按专用规则考虑桥跨；这属于后续 policy 设计。接入早期先提供兼容适配器，未经行为任务批准不改变现有 water/ice 规则。

### 3.3 Writer / failure / metrics

共享 ChunkWriteBounds 及写入结果契约：允许区域=当前生命周期授权区域∩计划施工区∩建造高度；自然路径不得 force-load，不写未准备 chunk。模板引擎仍使用其合法裁剪机制，Highway 保留 owner writer。超出当前 chunk 但属于后续 chunk 的计划格属于 NOT_OWNED，不计错误；越出整个计划属于 OUT_OF_BOUNDS。

每格返回 CHANGED / ALREADY_MATCHED / NOT_OWNED / PROTECTED / UNAVAILABLE / FAILED。保护策略覆盖 BE、不可破坏物、现有结构 claim、入口/多格资产完整范围；未知保护状态对必需写入先拒绝。flags、实体/邻居更新仍由具体 writer 负责，不在共享层暗改。NBT 的显式 air 写入也受同一保护契约约束。

GenerationFailure 原因码：INVALID_TERRAIN、UNKNOWN_TERRAIN、COLLISION、OUT_OF_BOUNDS、PROTECTED_CONTENT、MISSING_RESOURCE、VERSION_MISMATCH、BUDGET_EXCEEDED、INTERNAL_ERROR。PlacementResult 另有状态 REJECTED_BEFORE_WRITE / APPLIED_SLICE / COMPLETE / PARTIAL_COMMIT，并记录 system、stable plan ID、chunk/slice、attempt、changed/skipped/failed 数。APPLIED_SLICE 不代表跨 chunk 聚落完成；不能用一个布尔 true 混淆。

指标共享 attempts/accepted/rejected、reason、partial、query/write 时间与预算。Highway 保留 anchor/cache/seam/marking 指标，Rural 保留 role/lot/farm 失败指标。聚合按 system/维度/版本，日志采样限频；不以每格坐标作为指标标签，不持久化无限成功事件。诊断不得触发邻段生成。

## 4. Spatial Claim V1：模型与确定性裁决

采用有限 AABB 查询；不引入通用 GIS、任意多边形或世界级锁。

| 字段 | 语义 |
| --- | --- |
| id / owner / dimension / generationVersion | 可重建稳定 ID，owner 为 namespaced system；不同维度绝不相撞 |
| boundsXZ / optional yRange | 整数半开区间 [min,max)；无 Y 表示整列保守占用 |
| type | PROTECTED_SITE / SITE / BUILDING / INFRASTRUCTURE / CONNECTION |
| strength | HARD 拒绝不兼容占用；SOFT 只影响候选评分 |
| priority | 类型 policy 给出优先级，不信任资产作者任意填写最高值 |
| exclusionMargin | 与真实 footprint 分开；配对有效 margin 取 max(a,b)，避免重复加两次 |
| connectionEdges | 可为空，仅引用已发布 socket/anchor；不是任意穿越整个 claim 的许可 |

施工包围盒必须包含地基、地下部分、道路/driveway、植被清理和填切缓冲；Highway 的 core/halo 是计算窗口，**192 halo 不是全部硬占地**。走廊 provider 从查询区域解析有限 station 范围的实体/清理 envelope，计入桥墩/隧道/净空；查询不需要造整个无限网络。V1 对跨系统 Highway 先使用保守全高 corridor 预留，避免提早计算高程；允许立交是 Highway 自己处理，外部系统不得据此擅自叠建。以后经过 QA 可对隧道/桥采用实际 Y 分层。

### 4.1 禁止“先登记者获胜”

提供只读 `ClaimQuery.query(context, bounds, budget)`：返回有限 claims + COMPLETE/UNKNOWN 状态。EMPTY+COMPLETE 才意味着无冲突。未加载结构资料、provider 预算耗尽或缺少保护区基线返回 UNKNOWN；必需站点在写入前拒绝，不同步强载。每个 provider 声明最大影响半径；查询扩大到所有可能与目标相交的候选原点，禁止只查原点所在 chunk。

V1 自然站点使用**与加载顺序无关的候选 envelope**预留：由 placement seed/版本/候选格点生成，包含该候选所有允许布局的最大施工预算。与 Highway/保护区冲突时先筛掉候选。不同站点 envelope 竞争按固定 system priority，再按 stable ID 的固定排序。候选若与任何排名更高的、通过基本资格检查的 envelope 冲突即拒绝；即使该较高候选后来因 terrain 失败，也不立即释放给低位候选。查询不递归构建其他聚落，不做无界重新填空；接受一些未使用预留地换取确定性与有界成本。

基本资格仅用 seed、维度、版本、确定性 placement/保护规则，不能依赖实时“已经放好什么”。站点详细布局完全落在获胜 envelope 内；扩展超预算必须放弃该候选而非抢占邻居。Rural 原有选择/重试仍可在 envelope 内执行。保守预留可能改变接受率，因此 Phase 2 先 shadow 查询，再用新世界 profile 明确启用。

### 4.2 Claim 状态不是施工状态

区分 CANDIDATE_RESERVED（确定性潜在占用）、ACCEPTED_PLAN（已冻结计划）、COMMITTED/PARTIAL（实际施工证据）。重试失败不能撤销另一 chunk 已使用的计划；PARTIAL 仍持有占地，防止下一系统覆盖残留。同一 stable ID+版本+摘要重复注册幂等，不同摘要视为 VERSION_MISMATCH；不得“最后写入者更新占地”。

## 5. Spatial Priority / Conflict Policy V1

以下为建议默认值，不是当前代码事实：PROTECTED=100，已确认既有内容=90，确定性 Highway 主干预留=70，新普通 Site=50，外部软缓冲=10。同级按 stable ID 排序；数字仅供比较，无资产级自由覆盖权。City 的大区域可用 SOFT 声明选址意图，内部具体地块/道路用 HARD；大城市 soft 区不把整条 Highway 驱逐。

| 配对 | V1 默认决策 | 接驳/覆盖限制 |
| --- | --- | --- |
| startup 保护区 / Bunker vs 新 Highway/Site | 必须避让、保护优先 | 已确定保护占地不覆盖；保护计划必须在双方写入前可查 |
| 新 Highway corridor vs 新 Rural | Rural 预留避开 corridor，可在旁边候选 | local road 后续只连批准 anchor，不能穿建筑 |
| Highway vs 新 City | City 区域可包围软范围，硬地块/街道避开主干 | 保留未来 service-road 带，V1 无直接高速入口 |
| Highway vs Radio Tower/Camp | 新 POI 避让主干；可作为 roadside POI | 塔基/营地主体硬保护，入口可连外部本地路 |
| 既有建筑/玩家内容 vs 新施工 | 已确认内容优先，未知时拒绝必需写入 | 不自动拆旧建筑，保护不是“有 BE 才算” |
| 已知 Vanilla Structure vs AFL | 保守避让已知完整结构 footprint | 未加载/未知结构集成范围返回 UNKNOWN；不宣称已保护所有 Vanilla/mod 结构 |
| 合法连接边 vs local connection | 可按双方接受的窄 connection claim 重叠 | 只允许连接通道，不允许越界覆盖主体 |
| 普通可替换地形 vs 已批准施工 | 按 cut/fill/clear policy 可覆盖 | 仍受 fluid/预算/保护检查；不是无条件覆盖 |

Highway 遇到优先级更高保护区时，V1 **没有凭空提供 reroute 能力**。不能在单个 chunk 静默掐断路面来假装完成冲突处理。先要求保护区规划在道路确定性生成前可用，或在新世界启动兼容预检中判定不兼容并阻止启用协调 profile；未来显式改道属于独立设计。旧世界不启用需要未知保护资料的自动协调。Vanilla 保护适配器无法在安全阶段完整查询的覆盖范围必须列为未支持，并阻止声称全系统无碰撞；不能通过强载消除 UNKNOWN。

激活门不是“启动时扫描无限世界”。有限且位置已知的 startup/Bunker 可直接与解析走廊做预检；其余必需 provider 必须给出可按区域重建的确定性保护契约及与走廊兼容的放置规则。只有查询能力、却无法保证未来高优先保护区与固定走廊兼容，仍不足以启用该组合。没有这种保证的 Vanilla/外部结构组合保持未支持，不用抽样无冲突冒充全域保证。启用后若出现契约外冲突，应在当前 slice 首笔写入前失败并报告保护契约违例；这不是自动改道或道路连续性验收成功。

## 6. Persistence：推荐混合方案 C

A（全部按需重建）适合 Highway，但不能保留旧版本站点/手动批准站点/部分提交证据；B（全部 SavedData）需要保存无限公路、带来加载顺序和写锁；推荐 C：确定性候选与公路解析重建，有限已接受站点和异常保留紧凑持久索引。

Rural 的计划仍由 StructureStart/Piece NBT 保存，作为站点权威；共享索引存 id、dimension、bounds、version、plan digest、来源 start 引用及状态，不复制模板方块。索引是可核对的镜像，**不用于先到先得仲裁**。Highway 只存世界 profile/version 与必要的有限异常标记，正常 corridor/segment/cache 不持久化。动态 dev 放置必须显式选择“临时会话”或“持久站点”，不能悄悄加入自然分布。

未来每维度可用一个紧凑 SavedData 维护有限站点索引，但 generation worker 不直接改 SavedData；worker 消费冻结快照并产出不可变事件，服务器线程验证摘要后合并、批量 setDirty。结构权威记录先保存可恢复标识，索引更新滞后/崩溃时按该来源核对；不得把索引丢失当成空地。无法在允许读取范围核对则 UNKNOWN，禁写或维持旧 profile。不做启动全世界 chunk 扫描。异常索引做有界聚合，不能逐成功格记录。

算法版本/资源快照固定后，天然 claims 无需随着缓存淘汰而释放。显式删除站点/更改版本需要独立操作；自然候选预留在本版本中不因一次失败立即消失。旧世界实际结构的保护导入与 Vanilla 索引能力是协调激活的前置门，不是可跳过的清理任务。

## 7. Structure Metadata V1：资产、策略、配方分开

建议资源位置（**仅设计，尚未新增任何 JSON**）：`data/<namespace>/afl_worldgen/structures/<path>.json`，资源文件键就是 namespaced id；NBT 继续 `data/<namespace>/structures/<path>.nbt`。不要向 Vanilla `worldgen/structure` 塞建筑资产 metadata。记录 schema_version=1 和 asset_revision/content digest，便于资源冻结与兼容。

| 归属 | 最小数据 | 决策 |
| --- | --- | --- |
| StructureDefinition | id、structure_nbt、category、front、ground_anchor_offset_y、allowed_rotations、sockets、tags、asset_revision | footprint/height 从 NBT size 自动读取；V1 mirror 固定 NONE |
| 资产可选默认 | terrain_profile、collision_margin、qa 状态 | 配方可更严格；不得低于资产最低保护需求 |
| Placement Policy | dimension/biome、spacing/separation/salt、candidate 规则、系统 claim 优先级、site envelope 上限、query 预算 | 保持各系统生命周期；不是每栋资产的分布参数 |
| Settlement Recipe | tier 权重、role/category 需求、min/max、候选权重、每资产数量限制、道路/农田数、fallback、局部间距/地形容差 | weight/max_count/unique 的有效值放 recipe pool entry；unique 是 max_count=1 的别名，冲突配置报错 |
| City policy | zoning、density、block size、street hierarchy、height class、category mix | 属 City 模块，不放通用建筑文件 |

同一资产在不同配方有不同权重，故不把选择权重当几何固有属性。资产可不带 socket（无路边连接的塔/装饰）；需要入口的 role 必须声明 required socket 类型，缺失该资产不合格，不自动猜前框。`min_count` 属 role 需求，不能让每个 NBT 都要求“至少一栋”。

示意资产片段，数值是假设的开发 placeholder，不是已存在资产：

```json
{
  "schema_version": 1,
  "asset_revision": "prototype_1",
  "structure_nbt": "afl_dev:commercial_small_placeholder",
  "category": "apocalypse_firstlight:commercial",
  "front": "SOUTH",
  "ground_anchor_offset_y": 1,
  "allowed_rotations": ["NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"],
  "sockets": [{"name": "main", "position": [7, 1, 11], "facing": "SOUTH", "type": "PEDESTRIAN"}],
  "tags": ["afl_dev:placeholder"]
}
```

现有 authoring 兼容注意（补充文件核对，非新运行验证）：`small_city/buildings/*.json` 已有 structure、手填 footprint/height、surface_offset_y、road_facing、city_zones 等格式；见 `small_city_building_authoring_v1.md`。未来导入适配器将 structure→structure_nbt，surface_offset_y 需按同一地面公式验证后映射，手填尺寸只作与 NBT 的一致性断言；city_zones 留在 City 分类表。road_facing=true 不能自动变出真实入口坐标，缺 socket 输出待补状态。新文件规范与旧 authoring 导出需有显式版本适配，不能创建第二份互相矛盾的权威 metadata。

## 8. NBT Building Contract V1

Origin 是模板局部 (0,0,0) 对应的世界块位，不是建筑中心或门口。默认新 authoring 正面 SOUTH/+Z，既有资产允许显式水平 front；不自动旋转源 NBT。ground_anchor_offset_y 是局部“期望地面上方第一格位”的 Y；placement 使用 `originY = desiredGroundSurfaceY - offset`。允许地下部分，offset 下的所有块仍在 NBT size 范围内并纳入施工 claim，不允许模板坐标偷偷超出 size。

旋转使用与 `StructurePlaceSettings(Mirror.NONE, rotation)` 同一原点/pivot 和坐标变换；定义变换函数 T：worldSocket=origin+T(localSocket)，worldFacing=rotation(localFacing)。不另写一套以中心旋转的 socket 算法。四向均可声明，但 declared 不等于 verified；V1 不接受 mirror，后续若放开需单独 QA。

Bounds 使用整个模板 size 对应的局部 `[0,sizeX)×[0,sizeY)×[0,sizeZ)`，经 Vanilla 模板包围盒 API 旋转，再把其 inclusive max 转成 shared 半开 max+1。不以非 air tight bounds 缩小：R Audit 已发现显式 air 和边界 padding，air 会清理方块。occupancy 包含 porch、overhang、地下与模型声明需保护的施工缓冲。collision_margin 单独计算；超出模板的可视模型需 authoring QA 标记并给 placement 保护 envelope，不能由空碰撞形状误判。

StructureSocket = name、local BlockPos、facing、type。name 表用途，例如 main_entrance/service_entrance；type 为 PEDESTRIAN/DRIVEWAY/ROAD/SERVICE_ROAD。局部坐标指定模板边界内的入口第一格空气块，必须 `0<=x<sizeX, 0<=y<sizeY, 0<=z<sizeZ`；外部连接从 socket 沿 facing 前一格开始。V1 入口需位于外边界且 facing 朝外；一个 main socket 已足够。地下/屋顶出口需显式类型/策略支持，不能用普通平面道路强接。

导入验证：NBT 存在且标准尺寸>0、坐标在 size 内、无被禁止控制/调试块和实体；metadata key/id 唯一、schema/revision 合法、旋转非空且水平；socket 名唯一、位于边界、朝外、ground offset 在允许范围；四个声明旋转的 bounds/socket/facing 使用同一变换可逆。模板空气默认保留，仅 structure_void 按导入禁用/明示规则处理；不偷偷全过滤 air 以规避冲突。

QA 分两级：机械验证（尺寸/变换/引用/字段）与资产实测（门/楼梯/双格主副、BE 内容、地面与入口、实际视觉越界）。结果携带 NBT+metadata digest、tested rotations 和未覆盖风险，资产更换后 QA 失效。声明四向且机械测试通过也不能标四向游戏验收通过。结构中的 BE 不需要自行复制 rotation NBT 逻辑，但必须让 authoring/QA 检查 Minecraft 模板处理后的真实结果。

## 9. Settlement Recipe / Layout Policy

RuralRecipe 用现有四 tier 的角色/建筑目标、farm 目标、主路/支路 policy、footprint margin、site/lot terrain_profile 和有界 fallback 表达配方；具体 irregular layout、lot 偏移、田地主属关系仍由 Rural planner 实现。通用 pool service 只做合法资源过滤和给定策略的稳定选择，不规定所有系统必须 weighted random。

迁移优先冻结自然路径现有选择为 legacy 模式，使 dev 的 NATURAL_PARITY 调用相同实现。R Audit 已确认自然与命令对 weight/maxCount 行为不同，不能同时保持两条旧语义并声称统一；把 weight/maxCount 真正启用为 weighted-v1 必须列为独立行为变化、独立版本与批准后的回归。配方外置只声明现有值，不默认执行此前没有生效的字段。

CityRecipe 定义地块/分区/密度/楼高及街道层级，可按 category/tag 选已注册资产、按 NBT 尺寸适配地块；不能按每个建筑 id 写 Java 分支。Radio/Camp 用轻量独立 placement policy 和单体/小组合配方，无需另起基础框架。

## 10. City Placeholder Strategy

Phase 4 才创建开发数据包，例如 `afl_dev` namespace 的四种真实有限 NBT：commercial_small（建议16×10×12）、office_small（20×24×16）、industrial_medium（24×16×24）、residential_small（16×12×16），尺寸顺序 X×Y×Z。用混凝土轮廓/门洞标记制作，拥有实际地面 offset、SOUTH front、边界 main socket 和声明四旋转；参与同一 metadata validator、claim/terrain/placement 链。

开发 City recipe 只引用 category/tag，不把 placeholder id 写入 generator。正式 recipe 排除 `afl_dev:placeholder`，打包检查禁止 dev namespace/placeholder tag 混入正式池。正式交付时新增正式 NBT+metadata，替换配方池引用/数据映射，生成器代码不改。若尺寸、地下部分或入口不同，必须重新适配地块与 QA；超出原 slot 预算则拒绝/重选而非硬塞。所谓可替换指**数据供应可替换**，不保证任意体积无验证互换，更不在已生成世界原地换建筑。

City 模块边界：region candidate → street/block planner → zoning/density → category selection → shared asset/terrain/claims/placement → anchors。它可使用 Site/Structure 承载有限冻结计划；具体城市候选密度和块尺寸需 City Prototype 设计确定，V1 不冒充已经实现。

## 11. ConnectionAnchor V1

World anchor 由 socket/聚落边缘变换而来：stable id、owner/plan id、dimension、position、facing、type、status；priority 从 owner policy 派生，不再在任意资产中重复可调。type = ROAD_LOCAL / ROAD_ARTERIAL / DRIVEWAY / SERVICE_ROAD / PEDESTRIAN / HIGHWAY_EXIT；status = PLANNED_HOOK / AVAILABLE / CONNECTED。仅 AVAILABLE 可作为可施工目标，CONNECTED 需要双方及连接计划证据。

V1 结构/站点发布 main 入口，Highway 只可提供 PLANNED_HOOK（潜在服务路接入位置）；当前没有匝道，不能发布 AVAILABLE 的 HIGHWAY_EXIT 来伪装可行驶。连接兼容检查使用同维度、朝向、地面高差与路型匹配、有限搜索距离/预算；V1 仅做匹配/诊断，不修路。连接计划将来须发布窄带 claim 并验证两端入口和路权。

V2 先做 City/Rural 本地路与 AVAILABLE 的地面 local/service-road anchor 靠接，不能直接拆高速隔离带接主线。真正 exit/ramp/interchange connectivity 留到 Future 的 Highway 专项；anchor 数据类型无需提前引入完整路网图或交通仿真。

## 12. Partial Commit / Rollback 与 dev/natural

资源缺失、metadata 错误、已知 claim 冲突、unknown 必需地形、版本不兼容、预算超限必须在首笔写入前 abort。有限 Site 先冻结完整布局/引用/施工 envelope，逐 chunk 提交前再检查当前 slice 的保护物和写权限；不能为了“全站点预检”加载所有 chunk。未提交 slice 的未知内容仍是运行风险，预检不声称跨 chunk 原子事务。

Highway 先完成工程和当前 slice 的必需路面/净空预检。V1 不要求世界级 rollback；不可恢复错误保留 PARTIAL claim，记录 slice、原因和已写数，停止错误依赖操作，不盲目重跑整个聚落。可选护栏/植被清理允许 policy 明示 best-effort；桥墩/主路连通性不能仅以“可选”掩盖，沿用旧模式跳墩时必须报告 degraded，并将启用更严格门作为单独行为任务。单格保护不得由后续模板 air 绕过。

dev 命令提供 NATURAL_PARITY（同 planner/validator/选择版本/terrain 来源/预算，只替换合法入口和测试坐标）与 ENGINEERING_EXPERIMENT（显式 override 方向/长度/实时地形）的标签。原有 Highway 八向测试保留在实验模式，不能输出自然网络已验收。rollback wrapper 可在限定已加载测试区域记录原块/必要 BE，成功前拒绝无法安全恢复的内容；不扩展成自然生成通用事务。对比同 seed/candidate 的 normalized plan digest，并分别验证自然 writer 的 owner 边界。

## 13. Java-level 最小类型草案

以下是原始设计伪签名，不是当前 Java 接口的逐字副本；Phase 1 已落地的具体契约以源码及 WG-05 实现说明为准。优先 record/enum，只有 TerrainQuery、ClaimQuery 两个共享查询接口。现有具体 writer 的接入适配仍是后续任务，不要求重写其类层次。

```java
record WorldgenIdentity(long seed, ResourceKey<Level> dimension,
                       ResourceLocation system, String generationVersion,
                       String resourceSnapshot) {}
record TerrainSample(Validity validity, TerrainSource source,
                     OptionalInt surfaceY, OptionalInt topSolidSurfaceY,
                     OptionalInt oceanFloorSurfaceY, OptionalInt fluidSurfaceY,
                     FluidCategory fluid, Optional<ResourceLocation> fluidId,
                     SurfaceType surfaceType, ProtectionKnowledge protection) {}
interface TerrainQuery { TerrainSample sample(int x, int z, TerrainSource source); }
record SpatialClaim(String id, ResourceLocation owner, ResourceKey<Level> dimension,
                    BoundsXZ bounds, Optional<YRange> y, ClaimType type,
                    Strength strength, int priority, int exclusionMargin,
                    List<String> connectionEdges, String generationVersion) {}
interface ClaimQuery { ClaimQueryResult query(WorldgenContext context, BoundsXZ area, QueryBudget budget); }
record StructureSocket(String name, BlockPos localPosition, Direction facing, SocketType type) {}
record ConnectionAnchor(String id, String ownerPlanId, ResourceKey<Level> dimension,
                        BlockPos position, Direction facing, AnchorType type, AnchorStatus status) {}
record ChunkWriteBounds(Bounds3i allowed, Bounds3i planEnvelope) {}
record PlacementResult(PlacementStatus status, String planId, ChunkPos slice,
                       long changed, long skipped, long failed, List<GenerationFailure> failures) {}
```

| 类型/服务 | 谁调用、职责及最小补充字段 | 持久化/线程约束 |
| --- | --- | --- |
| WorldgenContext + WorldgenIdentity | 各 lifecycle adapter；identity+本次 generator/RandomState/区域引用+预算 | identity 可保存；Context 线程内且不缓存 Level |
| TerrainQuery / TerrainSample / FluidCategory | Rural/Highway/City planner 与预提交；含 query 来源/validity | sample 不可变；实时查询只在合法线程/区域；缓存按来源版本分开 |
| SpatialClaim / ClaimType / ClaimQueryResult | 各 provider 与站点裁决；result 有 completeness 与 failures | 确定性 claims 不保存；有限站点镜像可保存；只读快照并发安全 |
| StructureDefinition | Site 资产 registry；第7节字段+derived size/digest | 原定义数据包，计划保存引用与revision；模板实例不跨线程可变共享 |
| StructureSocket / ConnectionAnchor | 资产导入、站点/本地道路规划 | local socket 属资源；accepted anchor 属计划；不可变 |
| PlacementResult / GenerationFailure | lifecycle adapter、diagnostics | 失败列表限额并聚合，不保存无限事件；结果不能携带 Level |
| ChunkWriteBounds | writer adapter；许可与计划范围 | 调用级派生，不全局保存；输入不可变 |
| BoundsXZ/YRange/Bounds3i、QueryBudget 与 enums | 纯数学、预算、状态辅助值 | record/enum，无服务单例；溢出/负坐标测试 |

WorldgenSystemId 直接使用 ResourceLocation，不再包一层只转发的接口。并发安全必须由不可变资源快照、thread-confined 当前世界查询和服务器线程索引合并共同保证，不能因使用 record 就宣称 Minecraft 对象线程安全。

## 14. 目标包结构与专用保留项

```text
com.antaurora.apofirstlight.worldgen/
  core/        context, identity, result/failure, budgets
  terrain/     query/sample/source/fluid semantics
  spatial/     claim values, bounds math, deterministic query adapters
  structure/   definition/socket/validator, NBT transforms
  rural/       保留现有专用算法
  highway/     保留现有专用工程和生命周期
  city/        Phase 4 才创建
  poi/         Phase 6 按需要创建 policy adapter
```

Phase 1 必须创建的仅是 core/terrain/spatial/structure 中被首批任务使用的值类型/契约/验证器；不创建空 City/POI 包、统一基类、全系统生命周期管理器。后续可通过局部适配替换重复数学/导入代码；无需批量搬 `src/dev` 中已经被 main 编译的工程类，也不移动 registry、现有 feature/Structure 入口或正式 NBT。

Highway 专用保留：无限正交 corridor、station、256+192 工程窗口、bridge/tunnel resolver、上下分层交会、虚线相位、具体 chunk-owned corridor writer、seam validator、专用 cache。公共层只定义它们对外的 bounds、输入来源和结果，不复制这些算法。

Rural 专用保留：tier/irregular layout、farm planning、farmhouse/barn 要求、driveway/local path、RuralRecipe 与 NBT 池选择策略。通用资产 loader 和稳定选择工具可共享，何时选何种角色仍归 Rural。City 的街区网格、密度与 zoning 独立；Radio/Camp 是 Site Layer 的小型 policy 使用者。

主动排除：IWorldgenEverything、所有 Generator 继承同类、Highway NBT 化、Rural 用 Highway renderer、全事务回滚、所有 claim 永久保存、巨型 JSON、为未知机制提前造接口。

## 15. 迁移阶段与每阶段验收

下表保留原始阶段计划；Phase 1 WG-01～05.1 已完成无接入基础实现，WG-06 已冻结模拟地形的 Rural legacy plan digest；WG-07/07.1 已完成八资产打包 metadata 与旧六 recipe 兼容迁移，四向游戏 QA 仍待办，跨系统激活尚未开始。真实 fixed-seed/noise 与 chunk 顺序基线尚未建立，不能将 WG-06 fixture 当成这类验收。

| 阶段 | 工作及行为边界 | 完成门 |
| --- | --- | --- |
| Phase 0 Architecture | 本轮两份文档 | 覆盖双生命周期、未知占位、版本、占位资产与 backlog；不改运行文件 |
| Phase 1 Shared primitives | 无调用接入的 terrain/bounds/result、metadata validator、确定性 claim 原型 | compile；来源/UNKNOWN、负坐标/半开边界、四旋转/socket、missing NBT/BE QA失效测试；随机化候选查询顺序结果相同；原入口未调用新策略 |
| Phase 2 Rural migration | metadata/自然-dev同核心兼容模式、入口 socket、shadow claims；后续显式协调 profile | 固定 seed 的legacy分布/选择/plan digest保持；四向真实 NBT+BE；dev同输入同计划；无新强载；claims激活导致的候选减少单独报告，不能混称零变化 |
| Phase 3 Highway coordination | 保持route公式/owner writer；先shadow claims/hook，再保护基线完整的新世界profile启用 | 同 seed corridor/station/高程和无冲突区块对比；256边界/桥隧/立交/重启/缓存淘汰与chunk顺序；无强载/越区写；高优先冲突在激活前被明确拒绝 |
| Phase 4 City Placeholder | dev-only City candidate/街区/分区与四种 NBT；本地入口连接 | placeholder 可生成、确定性、无主体/道路碰撞、四旋转入口正确，生产池不含dev资产；不要求真实高速匝道 |
| Phase 5 Formal assets | 正式 metadata+NBT进入正式配方，替换dev引用 | 不改generator Java；每个资产尺寸/slot/入口/BE/旋转 QA；新资源revision，旧进行中计划不被替换 |
| Phase 6 POI | Radio/Camp/industrial policy接入 | category注册与placement、液体/claim拒绝、roadside入口与权重、旧区块保护；逐系统批准分布 |

## 16. 旧世界兼容与版本

版本拆开：metadata schema_version、asset_revision/digest、每系统 generationVersion、世界选择的 worldgen profile（包含 Highway route/engineering version、Rural selection/claim policy version）。未来只需有限世界版本记录，不保存无限路网。缺版本的旧世界识别为 LEGACY，不能自动切成新协调 profile；迁移功能首次实验推荐新世界。

已生成 chunk 不重放、不修复、不替换建筑。Highway 公式、盐、anchor/engineering 规则任何变化都可能在新旧 chunk 交界断路；Phase 3 必须保持原值，若日后修改只允许新世界或单独边界迁移方案。即使道路 XZ 相同，高程/隧道策略变化也属于兼容破坏。

Rural spacing/recipe/metadata 改变会影响未生成候选；保存过的 plan 继续使用原 definition revision 和坐标。资源内容不能在同一 NBT 路径上原地覆盖后让未完成 Piece 读取新尺寸；正式资产更新要保留被引用旧 NBT revision 或拒绝不兼容世界 profile，未知旧资源不得自动 fallback 到新建筑。资源重载对进行中生成冻结快照；不能半段用旧资产、半段用新资产。

共享 claim 不能只看新 registry 就覆盖旧结构。旧世界若没有可靠已知站点/Vanilla保护索引，保持 legacy 模式，并在审查/显式导入后才考虑协调。保护资料 UNKNOWN 不是“可以覆盖”。本设计未实现任何 SavedData 迁移或旧世界扫描。

## 17. 架构评审重点与验证边界

建议批准的 V1 取舍：保守确定性预留可能浪费少量候选；UNKNOWN 时拒绝新站点；世界级事务不做；Highway 高优先保护冲突阻止启用而非静默断路；正式建筑替换需重新 QA；legacy 选择语义先保留。若希望优先填满聚落空位、动态改道或旧世界无缝启用，必须另立机制任务，不能在后续迁移中隐式加入。

初始 Phase 0 检查的是设计覆盖、来源对应与文档一致性，当时没有新增 Java 或编译接口。后续 Phase 1 已完成编译和纯契约测试；WG-06 已完成 Rural 固定计划摘要与无图形 GameTest、有限 catalog/selection core 抽取，但仍未生成 placeholder、验证物理道路连接或运行压力/存档迁移测试。

## 18. Implementation Backlog（原始依赖顺序；WG-01～05.1 与 WG-06 已完成各自限定范围）

RISK 为实施风险；MODEL 是任务分工建议，不是本轮切换模型或创建任务。新机制 Astra，已有模式迁移/重复资产接入 Sol。

| ID | TASK | RISK | MODEL | DEPENDENCIES | BEHAVIOR_CHANGE |
| --- | --- | --- | --- | --- | --- |
| WG-01 | TerrainSample/TerrainSource/FluidCategory 纯契约与高度/UNKNOWN测试，不接入生成 | LOW | Astra | 架构评审 | NO |
| WG-02 | Context身份、failure/result、write bounds数学与预算值 | LOW | Astra | WG-01 | NO |
| WG-03 | Structure metadata schema、旧authoring导入映射、NBT/socket validator | MEDIUM | Astra | WG-02 | NO（仅验证） |
| WG-04 | 确定性claim候选/优先级/UNKNOWN原型与乱序测试 | HIGH | Astra | WG-02 | NO（无接入） |
| WG-05 | 有限claim索引/世界版本冻结/崩溃核对设计落地及保护provider门 | HIGH | Astra | WG-03,WG-04 | NO（未启用） |
| WG-06 | 建立legacy回归样本；Rural natural/dev兼容核心适配 | MEDIUM | Sol | WG-01..05 | dev统一；自然目标不变 |
| WG-07/07.1 | Rural metadata/socket、配方legacy映射；不接 shadow claim | MEDIUM/LOW | Sol | WG-06 | 八资产已发布、仅旧六入配方；NO自然分布变化；四向游戏 QA 未做 |
| WG-08 | Highway terrain/bounds契约适配、shadow claims与PLANNED_HOOK | MEDIUM | Sol | WG-05,WG-07 | NO路线变化；诊断增加 |
| WG-09 | 新世界协调profile激活与保护冲突/跨chunk回归 | HIGH | Astra | WG-07,WG-08；保护资料完整 | YES，站点准入变化；不改Highway公式 |
| WG-10 | dev City街区/分区planner与本地路连接原型 | HIGH | Astra | WG-03,WG-09 | YES，仅dev profile |
| WG-11 | 四类placeholder NBT/metadata按契约导入与QA | MEDIUM | Sol | WG-03；验收依赖WG-10 | YES，仅dev资产 |
| WG-12 | 正式建筑分批metadata注册/池替换/四向QA | MEDIUM | Sol | WG-10,WG-11 | YES，新区域资产供应 |
| WG-13 | Radio/Camp/工业site按现有契约接入 | MEDIUM | Sol | WG-09,WG-12 | YES，各独立placement |

INITIAL_IMPLEMENTATION_TASK = WG-01。CURRENT_CHECKPOINT = WG-07.1（八资产 metadata 完成，四向游戏 QA 待办）。RECOMMENDED_NEXT_STEP = 独立 Rural V2 设计任务；资产四向实机 QA 与真实 noise 世界样本仍需单独验收，不自动启动 V2。
