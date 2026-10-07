# Terrain V2 Phase 0 — 坐标诊断与 Terrain V1 基准

日期：2026-10-07。实现状态：**IMPLEMENTED / PENDING USER VALIDATION**。

仅增加可观测性；没有修改正式 density、noise、spline、carver、biome、Fallout、Radiation、surface、海平面、世界高度、MacroGeography、Highway 路由或 Road 工程标准。**Road V1-B 用户实机验收仍为 FAIL。** Phase 1 未开始。编译状态见本文末尾，实机基准尚未生成。

## 1. 命令与输出

开发环境、主世界、Creative、OP 权限 2。所有命令只读世界，不请求新区块、不登记施工、不执行 edits。

```text
/afl terrain inspect here
/afl terrain inspect <x> <z>
/afl terrain subsurface <x> <z>
/afl terrain subsurface <x> <z> <depth>
/afl terrain survey 256
/afl terrain benchmark 256
/afl terrain benchmark samples
/afl terrain status
/afl terrain cancel
```

inspect/subsurface 均允许省略坐标或使用 here；地下深度默认 16、允许 1～32。两者都包含坐标、地下和 ±16 剖面，subsurface 额外允许自定义深度。survey/benchmark 默认 256，范围只允许 128/256/512/1024；这里 radius 指**正方形半边长**，不是圆形面积。x/z 限制 ±29,990,000，给边缘采样留余量。

标准 runClient 输出目录 `run/afl_debug/terrain/`：

| 文件 | 内容 |
| --- | --- |
| `terrain_inspect_<x>_<z>.json` | 单点及真实/噪声剖面、density 中间节点 |
| `terrain_subsurface_<x>_<z>.json` | 同上，可指定地下深度 |
| `terrain_region_survey.json` | 区域统计与中心点记录 |
| `terrain_v1_baseline.json` | 区域 + 32 个道路候选 + 2 个历史回归点 + 轻量 Highway |
| `terrain_regression_samples.json` | 仅两处历史道路样本的正式只读预检 |
| `road_preview_position_gate.json` | Preview/Prepare 位置拒绝证据，区分未开始与完成后玩家入场 |
| `terrain_partial.json` | 超时的部分结果，不覆盖上次完整基准 |

完整输出通过临时文件与原子替换（不支持时普通替换）保存。取消/退出/换维度/失去权限/卸载丢弃未完成任务，保留已有完整报告。不自动提交用户世界数据。相同命令会覆盖对应文件；BEFORE/AFTER 必须由用户另存。

## 2. 数据来源与高度约定

| 标识 | 实际含义 | 不能证明 |
| --- | --- | --- |
| `LOADED_WORLD_BLOCKS` | `getChunkNow` 已有区块的 Heightmap/BlockState | 自然来源、未被玩家修改、生成阶段归因 |
| `GENERATED_CHUNK_DATA_QUART` | 已加载区块中的 quart biome holder | 不等于带边界抖动的玩家精确位置 biome 查询 |
| `NOISE_ESTIMATE` | 现有 `RoadTerrainQuery`、当前 generator 基础列和 seed-bound RandomState | 后续 carver/features/结构/玩家操作后的最终地形 |
| `MACRO_ESTIMATE` | MacroGeography 的 landmass/region/water/coast field | LAND 最终高度、测量岸线距离 |
| `ROAD_PREFLIGHT_VERIFIED` | 正式 V1-B planner 返回 PREVIEW_READY | 玩家站位、Prepare 台账批准、实际施工成功 |

`datasets` 按 LOADED / NOISE 完全分开；不使用估计值填补真实样本缺口。缺失为 null、UNKNOWN 或明确状态，不用 0 假装完成。任务跨多个 tick 观察，并非原子世界快照；请不要在测试区域施工。

- Heightmap 数值为表面**上一格**，即 top block Y + 1；WorldSurface、OceanFloor、MotionBlocking 分别输出。
- `top_solid_y` 为已加载 `OCEAN_FLOOR` 最高 blocksMotion 方块的 Y；包括树冠/建筑，不是第二套“自然土层查找器”。顶部方块与流体单列。
- NOISE 的 `support_surface_y` 复用 RoadTerrainQuery 在至多 64 格中找到的非可替换、非空碰撞支撑；它与 OCEAN_FLOOR 判据不同，不能混算。
- 噪声 `ocean_floor_wg_y` 另外调用 generator 的对应 Heightmap API，不拿 support Y 冒充；没有 MotionBlocking 噪声 API 时明确 NOT_AVAILABLE。
- 温度为 biome base temperature，降水为对应位置 precipitation；downfall 无当前公开 getter，输出 NOT_EXPOSED。biome region 同时读取已有 MainNationBiomeRegionPlan。
- Macro 输出真实 regionId/landmassId/role/waterClass/signed coastDistance/surfaceHeight；原始 island field/land mask 未公开就标 NOT_EXPOSED。coastDistance 是椭圆场距离，不是实测岸线。

## 3. Density 与洞穴来源

inspect 使用**当前客户端对应服务端已经 seed-bound 的 RandomState.router**；不从注册表取得未绑定函数后直接 compute，也不新建或安装生成器。

输出 router C/E/ridges/temperature/vegetation/depth/initial/final；遍历实际图，观测 LandTerrainBias C/E 及输入、InlandElevationBias、MacroTerrainDensity 的 land/terrain/underground 输入、BlendedNoise 与最多 8 类 spline 输出。Spline 以输出范围区分标签，**不是注册名，也不保证覆盖每个同范围节点**。引用的 noise ID 单列；不把 ID 当作执行记录。

默认在参考表面下 0/4/8/12 格读取节点值。图遍历最多 30,000 次，引用 noise ID 最多 128；中断或无法求值单独标记。已有地表优先作为采样 Y，否则取噪声支撑面。

这些是 `OBSERVED_SAMPLE_OF_SEED_BOUND_NODES`，不是数学独立的“抬高几格”贡献。它们不等于 NoiseChunk 插值、aquifer、carver、features 后的实际体素；不以某个负 density 证明某块最终空气的来源。没有安装生成阶段追踪 hook。

地下来源始终保守标记 `NOT_DETERMINABLE_POST_GENERATION`，另提供 underground graph sample、biome configured carvers（资格上下文，非运行证据）、区块 structure starts/references。单柱无法证明 lateral 开口或空腔连通，`SURFACE_OPENING` 不猜测。

旧 `/afl dev terrain_calibration` 已停止输出过时的 `-64..204`、268 高度梯度和旧 macro amplitude，改为提示使用新 inspect。旧 profile 命令保留，不作为 Phase 0 权威基准。

## 4. 指标定义

### 单点剖面

X/Z 两条轴各 -16～+16，33 点，共 65 个唯一坐标；surface height 使用上节各自支撑面口径。

- 高程：min/max/range、mean、median、人口标准差；分位数使用排序后的线性插值 `index=(n-1)*p`。
- 1/4/8/16 block rise：同轴间隔 k 的绝对高差最大值，不是累计爬升。
- grade：`abs(Yb-Ya)/水平距离`；局部 max adjacent grade 使用 1 格相邻点。
- roughness：对每条完整剖面拟合 `Y=a+b*s`，计算残差 `sqrt(mean((Y-Yfit)^2))`，单位 block；均匀斜坡的 roughness 为 0，但坡度并不为 0。
- 任一缺样时不计算需要完整剖面的 RMS/max rise；保留样本和 complete=false。

### 区域

9×9=81 个固定中心，spacing=radius/4（32/64/128/256）。每中心加两轴 ±1/±4/±8/±16，共最多 17 个点；共享坐标去重。高程、biome、地下比例只统计中心，不因某处有更多探针而重复加权。

- 相邻高差分布：中心与四个 ±1 轴向探针的绝对高差。
- 8/16/32m relief：中心与两轴 ±4/±8/±16 的五点 max-min；这是稀疏十字采样，不是整个方形内的绝对极值。
- 坡度分类取完整四邻点的最大 grade：flat≤1/16，gentle≤1/8，moderate≤1/4，其他 steep。**Phase 0 diagnostic thresholds**，不是新 Terrain 标准，也不改 Road 1/16 工程上限。整数地表的 1 格坡度分布会偏离大尺度缓坡直觉，须结合 relief 与 Highway 32 格 grade 阅读。
- `biomes` 输出 registry key/count/percent/denominator；显式保留 Fallout Barrens、Plains 零计数。land_water 是相同已观测中心的 Macro 分类，不宣称实测水域面积。

### 地下

surface block 为 depth=0，向下扫描到 depth=N（含端点）；报告每层 block/state/fluid/air/collision/full_support、first void/fluid depth、连续 air runs。first void 缺失表示“扫描内未见空气”，不是地下无洞穴。

stable_2/4/6/8/12：depth=0..N 内无 air/fluid 的比例；范围被世界底部截断则不进入该深度的分母。**不是完整实体/天然材料/工程可施工判定**。碰撞形状使用 EmptyBlockGetter 隔离上下文，避免邻区块加载；依赖邻居的特殊方块形状只能视为近似。

第一层下空气标为 unsupported-column-like，更深空气标为 cave-like，均仅为柱形态。Road 默认检查 `low=G-maxCutDepth-supportDepth-1=G-6` 到上部净空；G 为地面上一格，因此默认地下段为 depth=0..5 共 6 层。报告同时列 surface_y(G)、surface_block_y(G-1)、depth_below_surface_block，避免偏一格混淆。

## 5. Road 基准及 Survey/Preview 一致性

固定 32 个 R12/32 候选：radius/2 与 radius 两圈、各八个方位、每处 NORTH/EAST；复用 RoadSurvey 同一候选函数以及 RoadSegmentPreset。不是全国随机抽样，不允许一直搜索直到成功。

每候选调用同一个 `RoadConstructionPlanner.begin(..., diagnostics=true)` / `advance()`，使用同一配置、Protection query、raster、支撑、profile、cut/fill、snapshot，随后丢弃 edit/guard 快照，不调用 prepare/build 或台账。每次只持有一个大快照。统计 requested/completed/PREVIEW_READY/REJECTED/UNKNOWN，以及**陆地已完成候选**两种通过率：

- pass / (pass+rejected+unknown)：含未知的保守率；
- pass / (pass+rejected)：排除未知，仅代表已判定样本。

分母为 0 不输出百分比。回归样本不混入区域分母。保护、树、水、自然来源不明等拒绝均保留，不能说它们全是 terrain shape 失败。

**代码审查结论：未确认 candidate geometry、start/end、肩部、support 或正式 preflight 不一致。** Survey 有额外稀疏初筛；只有正式 preflight 通过才返回 SUITABLE_VERIFIED。Preview/Prepare 另有玩家站位和距离门，Prepare 还需台账/授权；这些原本不属于 Survey 的 VERIFIED 承诺。本轮补充验证范围和位置门报告，没有放宽规则或改变候选坐标。

`RoadConstructionPlanner` 仅新增可选失败观察信息，不参与 plan ID 或工程计算。void 失败附 world XYZ、G/表面方块 Y、station/cross offset、读到的 block/below、depth/support/policy/bounds。尚未求解 profile 时 expected_G/profile 必须 NOT_AVAILABLE，不能拿合成布局 G64 替代。部分布局级错误无唯一坐标，明确 NOT_AVAILABLE；纵坡固定端点冲突可报告相应约束位置。

位置失败单独报告：起点 corridor、保守 4 格肩部包络、额外 2 格玩家排除包络、offending X/Z、station/lateral。完成后玩家进入实际包络，则另存 actual_exclusion_bounds，保留 terrain_status。

### 历史回归 fixture（仅 dev Java）

| ID | 请求 | 历史观察 |
| --- | --- | --- |
| ROAD_PREFLIGHT_CONSISTENCY_SAMPLE | R12/32/NORTH，331,-762 | Survey VERIFIED 后 Preview 失败；曾出现 STEP_OUTSIDE_SEGMENT_AND_SHOULDERS |
| SUBSURFACE_VOID_SAMPLE | R12/32/NORTH，322,-812 | REJECTED / UNSAFE_SUBSURFACE_VOID，旧 bounds=[308,-847,336,-809) |

原世界 seed 未提供，fixture 明确 UNKNOWN；同一坐标换 seed 不构成复现。运行 `/afl terrain benchmark samples` 在当前世界只读执行两次正式 preflight，输出当前真实失败列。**inspect 322 -812 只检查起点本柱，不能证明整段路中首次失败也在该起点。**

A 的 STEP_OUTSIDE 可由独立站位门解释，不能推导为地形错误，也不能证明 A 的其他失败已解决。B 只能从历史报告确认地下空气拒绝类别，实际 Y、哪条柱、噪声还是 carver 仍须用户运行工具。

## 6. Highway

benchmark 查询现有 HighwayRouteGraph，选距中心最近两条直线 edge，不改路线，不增加 route。每条以投影 station 为中心截取 ±min(radius,512)，每 32 格一点，最多 33 点；不覆盖 polyline 分支、整个国家或桥梁承载宽度。

分 LOADED/NOISE 报告中心线高程、相邻 grade max/mean/分布、累计升降、表面或浅层流体样本、stable_6 支撑样本。缺失处不跨缺口计算升降。该值是**自然地形 suitability baseline**，不是实际 Highway engineered deck 验收。邻路虽可能在范围外仍有明确坐标；不能把它算进中心区域高程分布。

## 7. JSON schema v1

```text
schema_version / diagnostic_version / terrain_version / worldgen_profile
world_seed / dimension / generated_at_game_tick / completed_at_game_tick
sample_center / sample_radius / sample_spacing / sample_count / completed_center_samples
runtime_fingerprint
datasets:
  LOADED_WORLD_BLOCKS / NOISE_ESTIMATE:
    elevation / adjacent_1_block_delta / roughness / slope / subsurface / biomes / land_water
inspection / density / local_profiles                 # 单点命令
center_samples
road_buildability / known_regression_samples / highway
diagnostics / status
```

fingerprint 读取当前 ResourceManager 中 AFL worldgen JSON（最多 256 个、累计约 4 MiB、每项 256 KiB 上限）和选定 Macro/Land/Road 类字节 SHA-256。明确不是完整 modpack/resource snapshot；外部 TerraBlender、Mixin/其它模组及配置变化仍须人为固定。WorldgenProfile 现仅为值契约，本工具没有建立世界持久版本锁。

状态 COMPLETE 指任务结束，不表示所有区块已加载或每项 PASS。TIMEOUT_INCOMPLETE 不进入完整基准文件。数据会包含 seed 和坐标，默认仅本地输出。

## 8. 性能与发布边界

- 一个维度一个任务；与现有 Survey/Preview/Prepare 互斥。活跃施工台账的既有预算不变，基准期间不要同时 build。
- inspect 65 个唯一探针；survey 最多 1377；加两条 Highway 后最多 1443，硬上限 2048。RoadTerrainQuery 任务内缓存上限 4096，完成/取消释放。
- 每 tick 最多 2 个探针，探针间 4ms 软截止；或 1 个详细柱；或正式 planner 16 柱（还服从更低配置）。基础列、density 图、指纹、正式 profile/finish 单次操作不可抢占，**不承诺 4ms 硬上限**。记录实际 max_work_unit_ms 供用户评估。
- detail 81 中心+最多66 Highway，浅层默认12；inspect默认16最高32。详细噪声柱重复调用有计数，未隐藏为零成本。
- Road 最多32区域+2回归，不复制整个 snapshot 到JSON；半径不增加候选数。所有未知/异常保留，无重试搜索。
- 超时12000世界tick；启动冷却100tick；无后台无限缓存，无强载 chunk、票据、Level 写入。记录 sampled_columns、noise_query_columns、extra_noise_detail_columns、loaded_chunks_used、forced_chunk_loads=0、duration_ticks、block_checks、road_successfully_sampled_columns。
- 新诊断、fixture、导出器全部在 `src/dev/java/com/antaurora/apofirstlight/dev/`。build.gradle 当前仍将 dev source 加入 main 编译供开发客户端使用，但发布 jar 明确排除 `com/antaurora/apofirstlight/dev/**`；本轮不重构构建，也没有运行 jar 核验。其他旧 dev/worldgen 包边界问题仍是独立后续工作。
- main 仅增加 RoadConstructionPlanner 可选 DTO 观察，未注册 release 诊断 tick。默认旧 begin 关闭观察；正式生成结果、Road plan identity 不变。

全国 stratified benchmark **未实现**：未来可按 island/region/coast band 固定有限种子点并复用同样 source/budget/schema；当前区域 81 点不能冒充全国 Fallout 占比或全岛建设比例。

## 9. 用户最短验证与 BEFORE / AFTER

按顺序执行，每个任务完成后再启动下一个：

1. A：原失败世界执行 `/afl terrain inspect 331 -762`，再 `/afl roads preview segment r12 32 north at 331 -762`。分别看 terrain 与 player gate，站位门不能算地形拒绝。
2. B：`/afl terrain benchmark samples` 获取实际 failing XYZ，再对失败列执行 `/afl terrain subsurface <failure_x> <failure_z> 16`；也可先 inspect 322 -812 对照起点。
3. C：固定玩家整数中心执行 `/afl terrain survey 256`，检查两个 datasets 的分母、缺失值及浅层统计。
4. D：同一中心 `/afl terrain benchmark 256`；保留 terrain_v1_baseline.json 为 BEFORE。UNKNOWN 多通常意味着候选区块未加载；工具不会替用户生成它们。
5. E：平原、荒原、海岸各选一点 `/afl terrain inspect here`，核对 biome/Macro/三个 Heightmap，并试 status/cancel。

Phase 1 以后继续使用同 seed、维度、中心、半径、配置、诊断版本；正式生成改变使用单独全新世界，不能只在旧 chunk 上换代码冒充 AFTER。固定 loaded coverage 与时间/人为改造条件，分别比较真实与噪声；先检查指纹差异，再看 elevation/roughness/stability/road reasons。改地形不自动意味着 Survey/Preview、施工、桥梁或性能通过。

## 10. 修改路径及验证

- 新增 dev：`TerrainDiagnosticCommand`（注册/生命周期）、`TerrainDiagnosticJob`（任务/汇总）、`TerrainColumnInspection`（柱/biome/地下）、`TerrainDensityInspection`（绑定图）、`TerrainDiagnosticMetrics`（统计）、`TerrainDiagnosticIO`（schema/指纹/导出）、`TerrainRoadBenchmark`（候选/回归/站位）。
- 修改 dev：`AflDevCommands`、`RoadSurveyCommand`、`RoadConstructionCommand`。
- 修改 main：`worldgen/roads/construction/RoadConstructionPlanner.java` 只读诊断 overload/上下文。
- 未新增 worldgen 资源、方块、模型、profile激活或正式施工权限。

验证：2026-10-07 `./gradlew.bat compileJava --offline` **PASS，BUILD SUCCESSFUL in 33s**。第一次 wrapper 启动被沙箱外 Gradle 缓存锁权限阻止，尚未进入编译；以所需缓存权限重试后首次实际 Java 编译通过，无本轮编译错误修复循环。存在现有 deprecated/unchecked 警告。通过后停止自动验证，仅回填本条结果；未运行游戏、世界生成、benchmark、截图或其它请求的 Gradle 任务（仅 compileJava 的正常依赖任务）。运行时数据正确性、性能、原失败世界复现均 **PENDING USER VALIDATION**。
