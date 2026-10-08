# Highway V2 M1-B：RouteGraph 接入、统一断面与独立预览

日期：2026-10-07。基线：Master `4db85e52acb4728d8b89dd5b978b66d790b6754a`。
开发分支：`codex/highway-v2-m1b`。
Worktree：`C:\Users\willi\.codex\worktrees\highway-m1b\Apocalypse First Light`。

**状态：有限真实路线的只读预览、统一几何和离线验证已实现；M1-B 客户端尚未验收。未替换正式高速。**
M1-A 用户已确认连续 Mesh、双向分离、弯道、坡面视觉、行走无明显卡脚，且 `/afl dev highway_mesh verify` 成功；保存重进、区块卸载恢复、完整光影和性能继续待验收。M1-B 不把这些局部结论扩展为全面 PASS。

## A. 实际 RouteGraph 接入与保留边界

`RouteGraphMeshAdapter.select` 读取 `HighwayRouteGraph.forSeed(seed)`，保存 seed、graphVersion、routeId、edgeId、端点 Node ID、原 authorityStart、原控制点，以及 parent attachment、junction、海桥和预留区限制说明。没有复制选线算法，也没有另造国家道路。

当前 Master 的 `buildTrunks` 发布两条有限、轴向直线 NATIONAL_TRUNK；卫星岛连接由 `SatelliteHighwayRouting` 发布正交边，TURN/BRANCH_JUNCTION 之间保留未施工区域。**不能为了样段出现弯道而给国家主干添加转弯。** 本轮真实主干保持直线；连续曲线能力通过明确标记的离线 fixture 检查，不能称为实际国家路线曲线验收。

固定种子 20261007：2 条主干、11 条 Edge、3 处海跨连接、6 个 reserved zone。此计数是该种子证据，不声称所有种子边数相同。主样段：

- route `national_trunk_a`，edge `national_trunk_a/main`。
- authorityStart=-5656；原端点块中心 (-5655.5,721.5) 到 (5576.5,721.5)。
- arc offset=128，length=768；真实源 XZ 从 (-5527.5,721.5) 到 (-4759.5,721.5)。
- 坐标只加一次 +0.5；几何以权威 station 递增方向构造。轴向边的原 authority station = authorityStart + arc station；未来平滑多段线必须单独维护旧里程映射，不能假定等长。

不会跨 Edge 自动补齐转弯、桥头、海跨或匝道缺口。部分 polyline 的旧里程映射不完整时拒绝；曲线超出走廊或缺少保护证据时返回 RESTRICTED。

`CorridorEngineeringSegment.build` / `HighwayProfile` / `HighwayCorridor` 的正式标高及 SURFACE/VIADUCT/TUNNEL 依赖 WorldGenLevel 和地形采样，不能从 RouteGraph 凭空得出。本轮不调用正式施工链；海桥和匝道引用保留为限制元数据，工程类型明确 UNKNOWN。

## B. 连续几何

调用链：

```text
HighwayRouteGraph (未改)
  -> RouteGraphMeshAdapter (只读、有限 Edge)
  -> RouteRoadGeometry.Plan (带版本的参数)
  -> RoadAlignment + RoadSection + 显式示意纵断面
  -> surface / query / anchor / triangles
  -> RoadMeshAsset.generated
  -> 原 ChunkMeshGeometry 16m 裁剪 + 原有限碰撞列算法
  -> 原 AflMeshLoader -> 原 WorldChunkMeshRenderer -> 原 AflMeshRenderer
```

水平曲线使用五次 Bezier 过渡：首尾切线与原直段同向，端点二阶导数为零，因此连接处曲率回到零。按 Simpson 积分建立弧长表，在同一不可变 alignment 上反求参数；不使用随机转弯、不依赖 chunk 加载顺序。局部尖折、回头、超过90度折角、半径/offset 退化被拒绝。150m 是开发几何退化防护下限，**不是高速设计速度认证半径**；离线45度用例最小半径约304.53m。

曲线的所有 Bezier 控制点连同最大保护半宽，必须落入同一个允许凸走廊，利用凸包性质保守约束整条曲线。窄旧走廊往往拒绝，这优于把曲线硬塞进64格 TURN 区。实际曲线还需要可靠保护证据；当前 provider=UNKNOWN，因此不开放真实弯曲边预览。离线用例使用显式空白宽走廊，仅检验几何；该用例不是国家路线、不是施工许可。

纵断面为**显式开发示意**：从整条 Edge 的 arc=0 起，1024m 跨度，五次 smoothstep，高差16.384m，最大坡度3%，端部坡度和竖向二阶变化回零。它不是已有 Highway 标高、不是 Terrain 目标高度，也不是每个 preview 窗口重新起坡。断面横坡2%；曲率驱动的示意超高平滑到不超过4%，未完成设计速度、视距、排水或正式超高旋转轴校核。

采样使用全局2m里程格，UV每8m重复，虚线12m周期/4m实线、0.15m宽，标线抬高0.003m。窗口先查询同一 alignment，再裁剪实际16格 XZ 平面。保守三角形拓扑不依赖任意 Quad 合并。视觉使用面法线，查询返回连续曲面导数法线；同源三角形跨块保留原法线。

## C. 统一断面参数

唯一默认数据源：
`src/dev/highway_mesh_resources/assets/afl_highway_demo/route/sections.json`。
单位为米（1格约1m），不先把每条车道取整为方块。`formationExtra/constructionExtra/protectionExtra` 均为在视觉总宽上增加的**总宽**，不是单侧宽。

| 参数/层 | rural4 乡村/城际四车道 | suburban6 城市外围/郊区六车道 |
|---|---:|---:|
| 每方向车道数 | 2 | 3 |
| 单车道 | 3.75 | 3.75 |
| 单方向行车道 | 7.50 | 11.25 |
| 每方向内肩 | 1.50 | 3.25 |
| 每方向外肩 | 3.50 | 3.50 |
| 中央带（不含内肩） | 19.00 | 12.00 |
| 含两侧内肩的 median | 22.00 | 18.50 |
| 两侧铺装量合计 | 25.00 | 36.00 |
| **可见总跨度（含中央带）** | **44.00** | **48.00** |
| 路基/边带模板 | 48.00 | 52.00 |
| 施工模板包络 | 52.00 | 56.00 |
| 建议保护宽度 | 60.00 | 64.00 |
| 中央带语义 | grass | reserved_barrier |
| 正常横坡 | 2% | 2% |

公式：paved=2×(lanes×laneWidth+inner+outer)，visible=paved+median；formation=visible+4，construction=visible+8，protection=visible+16。后面三层是 AFL 开发模板，不是现实 ROW、安全净区或削填工程最终边界。坡脚、桥墩、洞壁、辅路等仍须工程计算。

来源复核：延续 [V2-0设计契约](highway_v2_0_design_contract.md)，2026-10-07再次核查 [FHWA Interstate摘要](https://www.fhwa.dot.gov/programadmin/interstate.cfm)、[TxDOT 8.1.7](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-1-design-considerations/8-1-7-shoulders.html) 与 [TxDOT 8.2](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-2-rural-design-elements.html)。摘要提及12ft车道；Texas四车道内/外肩最低4/10ft，六车道内肩10ft；乡村常见median 50–100ft。FHWA页面仍提醒部分内容陈旧。以上不是 AFL 法定规范或所有场景的完整 AASHTO 认证。

换算：12ft=3.6576m，选3.75偏大0.0924m；10ft=3.048m，3.5m外肩偏大0.452m，3.25m内肩偏大0.202m；4ft=1.2192m，1.5m内肩偏大0.2808m。这是按用户视觉和用地目标调整后的 AFL 选择，44/48m并非美国官方固定总宽。

六车道默认是留有余地的郊区设计，不是密集城区紧凑分隔墙断面。`medianType` 保留草地、中央护栏/墙、排水设施等语义入口；本轮中央带只有共用占位面，**没有混凝土墙、植被或排水沟实体**。狭窄中央带可在后续参数配置，但不把本轮12m带宽称为狭窄城市墙。

## D. 新旧宽度与过渡

M1-A 固定试验仍为38m，不改其物理/资源指纹。V2-0旧建议四/六车道38/37m是当时宽草地与紧凑城市墙的比较；本轮默认改为44/48m。A相对38增加6m（15.79%），B相对37增加11m（29.73%）。

旧正式施工 MAIN_WIDTH=23、ROW约29、隧道周边33格不改变，也不能直接承载新断面。旧 RouteGraph claim 是65格，M1-B给出60/64m参数但**不发布或改写正式claim**。直线几何落在原走廊不等于土木/保护安全；曲线、坡脚和设施需重新计算工程包络。

`widen_4_to_6` 共用同一生成器，整条Edge arc=256..512用五次函数连续改变铺装、内肩、中央带和三层包络。外侧第三车道从零宽发展，新增虚线从零宽显现。它是有限几何过渡占位，不是已验收的加减速车道、汇入鼻端或车道减少交通设计。不在chunk边界切换断面；当前断面查询返回from/to/blend及所有实际宽度。

## E. Mesh 工具复用与文件

新增纯几何/契约类（均在 `src/dev/java/com/antaurora/apofirstlight/dev/highwaymesh/`）：

- `RoadSection.java`：断面、尺寸检查、默认JSON读取。
- `RoadAlignment.java`：直线/五次曲线、弧长、曲率、走廊约束。
- `RouteRoadGeometry.java`：版本Plan、连续surface/normal/section、锚点、同源三角形。
- `RouteGraphMeshAdapter.java`：实际RouteGraph只读适配、有限端点留距、受限状态。
- `RoadPlanningContext.java`：未来规划证据接口，当前UNKNOWN。
- `RouteRoadCodec.java`：显式record构造；兼容现有Gson 2.9.x，不升级依赖。
- `RouteMeshCommands.java` / `RouteMeshNetwork.java` / `RouteMeshClient.java`：隔离命令、32KiB以内配方、单执行者预览和客户端缓存。
- 既有 `RoadMeshAsset.java` 只新增生成数据工厂/预算入口；原M1-A仍保持128tile/16000列上限。M1-B最多1024m、56m视觉宽、50000源三角形、512tile、65536碰撞列。超限拒绝。

共享 AflMeshLoader、AflMeshRenderer、M1-A chunk clipper/世界renderer均未修改。新代码沿用当前build.gradle的main编译扫描和dev jar排除；非production门控仍生效。没有改枪械、机器、家具、A1、MCP、Terrain、Road V1-B或正式Highway Java。

工具：`tools/highway-v2-m1b/verify.mjs` 实际编译/调用纯Java kernel与真实RouteGraph，`export.mjs` 消费同一Java三角形，再调用原 `tools/export-afl-mesh.mjs`。无第二套格式或第二套中心线算法。

生成产物的两个新目录在.gitattributes中固定LF，避免Windows换行转换破坏字节重复性。只保存两件32m exporter fixture（4/6车道），非全国每段独占资产：

- `tools/highway-v2-m1b/results/m1b_rural4_export_fixture.aflmesh.json`：376三角形、1504存储顶点。
- `tools/highway-v2-m1b/results/m1b_suburban6_export_fixture.aflmesh.json`：400三角形、1600存储顶点。
- 同目录配套geo、export-summary、export-input、validation和real-route-plan。
- 可编辑源仅在 `src/main/blockbench/dev/highway_v2_m1b/*.bbmodel`，由脚本生成，未放进运行模型目录。
- 运行时只保留有界配方和临时tile，不注册每段模型ID，不使用这两件离线fixture代替真实路线计算。

## F–H. 离线样段、接缝与物理查询

| 用例 | 长度 | 源顶点/三角形 | tile | Loader后 三角形/角点 | 真实16m seam探针 | 最大高度缝隙 |
|---|---:|---:|---:|---:|---:|---:|
| 实际主干四车道 | 768m | 26880 / 8960 | 192 | 13568 / 40704 | 17488 | 3.55e-15m |
| 实际主干六车道 | 768m | 28416 / 9472 | 192 | 14080 / 42240 | 18240 | 3.55e-15m |
| 独立曲线+4→6过渡 | 1024m | 37440 / 12480 | 292 | 22936 / 68808 | 24183 | 2.35e-7m |

源顶点指未合并三角输入的3N角点，不是去重后的顶点数。三种用例无缺失seam探针，UV相对源插值最大偏差2.55e-6以内，继承源法线偏差0；面积覆盖、法线向上及有限数值检查通过。曲线连接处曲率差2.53e-10/m，切线差1.27e-15；宽度误差6.4e-14m以内。几何重复生成、实际Graph重建配方、随机tile烘焙顺序和输出字节复核通过。坐标包含负chunk；代码保留M1-A的floor裁剪，未改截断规则。

连续query世界XZ投影的最大高度误差2.63e-11m；section给出左右路缘、中央带、内/外肩范围；anchor提供left/right_edge、left/right_median、left/right_lane_outer。source坐标、局部Mesh坐标和preview坐标明确分开：preview原点对应所选样段起点，Y对应该起点示意标高的平移；原点XZ必须16对齐。以后原位施工必须采用统一世界原点与标高契约，不能直接把每个样段原点Y都设成同一值。

继续使用M1-A的1×1列、1/32格高度量化和有限AABB近似，**不会变成任意三角形碰撞**：

| 用例 | 计算碰撞列 | 最大视觉误差 | 最大相邻/跨16m台阶 |
|---|---:|---:|---:|
| 真实四车道 | 33792 | 0.040563m | 0.03125m |
| 真实六车道 | 36864 | 0.040562m | 0.03125m |
| 曲线/过渡 | 48831 | 0.045645m | 0.0625m |

这些列只用于离线验证及统一资产数据，M1-B没有create入口，**游戏中预览无碰撞、不能站立**。M1-A已验证的玩家步行不等于M1-B物理实测。普通方块支撑、自动放置、车辆动力学未实现。无新方块注册/矿物工具或掉落变更。

## I. Terrain与施工证据

当前Master有既有 `TerrainQuery/TerrainSample`，区分NOISE_PRE_DECORATION、STRUCTURE_PLANNING、WRITABLE_PRECOMMIT、CURRENT_POST_FEATURE及有效性、保护知识；没有 `LandformPlan`。未合并Terrain Phase1，也未改已有Phase0。

`RoadPlanningContext.Evidence` 预留Optional宏观高度、坡度、城市候选、山带、河谷、适宜性及Protection/provenance。当前UNAVAILABLE返回空值和UNKNOWN。它没有伪装为已接入TerrainQuery的实现。正式施工还缺完整claims、自然/玩家所有权、地形采样和写前比对；UNKNOWN不能清障。

预览放置位置通过现有HighwaySpatialClaimProvider检查（含海桥、匝道预留及接缝），另查建筑authoring reservation，避免贴到旧高速或A1保留区。不会加载地形证明可施工，不判断任意方块所有权，不写方块。仍应使用独立测试世界和空中场地。

## J–K. 验证、性能、生命周期、Git

必要离线命令（JDK17，已缓存Gson，无下载）：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
node tools/highway-v2-m1b/verify.mjs
node tools/highway-v2-m1b/verify.mjs --check
```

已得到 M1A_OFFLINE_PASS、M1B_OFFLINE_PASS、M1B_EXPORTED_LOADER_PASS、M1B_REPRODUCIBLE_OUTPUT_PASS；原M1-A modelFingerprint保持不变。曾发现现有Gson不能反射写record final字段，已改显式构造并增加配方往返检查，未升级依赖。没有把离线结果写成游戏PASS。

一次离线样本生成+裁剪+碰撞约182/57/107ms（依次四、六、曲线过渡；只代表此机器该次运行），不是帧耗时。每份全加载模型角点float载荷约1.30/1.35/2.20MB，不含对象、JSON、canonical三角、碰撞列和Driver内存。没有报告虚构FPS、总堆内存或GPU draw call。

客户端最多一个后台纯CPU任务，不保留Level引用；新描述替换待处理目标，不排无限任务。只对已加载chunk每tick烘焙最多2tile，视锥/驻留检查后提交同一atlas的一次buffer flush（不是GPU draw-call统计）。status/verify报告tile、提交角点、有限float载荷、累计烘焙耗时和淘汰数。离开chunk停提交并淘汰模型；canonical资产最多一份，有界留存。

remove/退出/换世界清描述和缓存；正在做的纯CPU任务可完成后丢弃，不恢复旧预览。资源重载保留当前配方、清模型重建。服务端仅内存session，退出/换维度/停止清除，**重进不自动恢复M1-B，须重发preview**。这与M1-A持久化碰撞场不同。生命周期为已实现代码路径，尚待实机验收。

全国几十公里不能直接常驻所有canonical几何、碰撞列及模型：还缺空间窗口调度、局部工程版本、LOD/缓存预算、真实Terrain方案、性能采样。当前是一人一段≤1024m工具，不能作为全国部署PASS。Oculus/Embeddium、PBR、阴影、标线Z-fighting、reload、多人和帧时未验收。

Java完成后仅运行一次 `.\gradlew.bat compileJava --offline`；本次结果为 BUILD SUCCESSFUL in 57s，4个必要任务执行；有弃用/unchecked警告，无编译错误。没有运行build/processResources/runClient/runServer/全套Gradle测试。源代码编译进入新Worktree自己的build/classes，不使用主目录输出。

本次只在新Worktree修改；主目录master仍为4db85e52，启动检查时保留 `.obsidian/workspace.json` 未提交修改和 `TemperatureRing.java` 的AD异常暂存/工作区状态，未暂存、修复或清理它们。本次未写入Claude/A1/MCP文件。结束复查发现主目录另有并行的A1脚本、文档、注册项与杨木资源调整；它们仍留在主目录，既未纳入本分支，也未清理。独立提交，不push、不merge、不删除任何worktree；最终commit见交付消息或本分支git log。

## L. 用户实机流程

在IntelliJ新窗口打开此Worktree目录/其build.gradle，选该项目的Gradle JDK17和Gradle runClient任务。核对项目路径、任务working directory和classpath都属于 `highway-m1b\Apocalypse First Light`；不要复用主目录runClient配置。用户也可从新Worktree终端自行运行 `.\gradlew.bat runClient`。本轮助手没有启动。

使用这里新建的创造测试世界（建议seed=20261007、允许作弊），存档在本Worktree的run/saves，勿复制或链接Fuel Stop A1存档。M1-B预览在空中，必须保持飞行。

```text
/afl dev highway_route_mesh list
/tp @s 16512 190 16384
/afl dev highway_route_mesh preview "national_trunk_a/main" 128 768 rural4 16384 160 16384
/afl dev highway_route_mesh verify
/afl dev highway_route_mesh sample 16512 16396
```

edge带斜杠，**保留双引号**或用补全。上述样段沿+X延伸768m；沿路飞行加载chunk。不强制加载整段，远端未显示首先检查可视距离/驻留，不把未加载认作裂缝。

比较六车道和连续加宽（只替换自己的当前预览）：

```text
/afl dev highway_route_mesh preview "national_trunk_a/main" 128 768 suburban6 16384 160 16384
/afl dev highway_route_mesh preview "national_trunk_a/main" 128 768 widen_4_to_6 16384 160 16384
/afl dev highway_route_mesh status
/afl dev highway_route_mesh remove
```

M1-A仍用 `/afl dev highway_mesh ...`，命令含义与固定256m场景完全不变。M1-B的verify只检查描述/指标并重新发送同一实例，不是自动实机PASS，也不创建或修复碰撞。

客观验收：先看44/48m双向分离、每方向2/3车道、肩部、示意坡及过渡；F3+G检查实际16格线，移动视角检查面翻转、漏面、深度和光照。离开再回到同一场地检查无重复/残影；F3+T应按同一配方恢复；正常重进后M1-B应为空，重发preview后恢复；remove后无Mesh/方块变更。分别记录纯Forge、Embeddium、Oculus+指定shader，不能相互代替。真实主干不会凭空出现水平弯道，曲线本轮仅有离线证据。

## M. 未实现与下一步边界

未完成：真实弯曲支线保护证据及跨预留区连续接头、正式Terrain标高、工程分类/削填、自然或玩家所有权识别、真实新路面碰撞施工、桥隧和匝道、普通方块支撑、全国调度/持久化/LOD、正式Claude资产/PBR、车辆物理。六车道/加宽的标线是几何占位，需要后续交通工程审查。

本轮交付独立开发能力后停止。未合并Master；不自动推进后续桥隧、土木或全国替换。