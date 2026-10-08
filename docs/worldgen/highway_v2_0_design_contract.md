# Highway V2-0：北美州际高速设计契约与分阶段方案

日期：2026-10-07。状态：**设计建议，尚未接入正式 Highway。** 当前生产行为以[源码审查](highway_v2_0_architecture_audit.md)为准；[离线原型](highway_v2_0_mesh_prototype.md)实现了下述部分参数化视觉几何。后续[M1-A隔离开发测试场](highway_v2_m1a_mesh_sandbox.md)已接入真实16格渲染tile及有限碰撞代码，编译/离线验证通过，现已集成 Master，基础渲染/坡面行走已获用户确认，生命周期与光影兼容仍待验收；这不等于完成整个M1或正式高速升级。下文“当前/本轮”工具能力结论保留V2-0审查时点，M1-A增量以新报告为准。

后续 M1-B 已建立44m四车道、48m郊区六车道的统一开发参数及只读真实路线预览，见 [M1-B报告](highway_v2_m1b_route_geometry.md)。本文38/37m仍是V2-0历史建议，M1-A固定38m不变；本文“后续M1-A/未实现接口”按原审查时点保留，当前局部实现与限制以M1-A/M1-B报告为准。

## 1. 现实参考的适用边界

本次在线核查官方页面，TxDOT 手册索引标注 November 2024。它是 Texas 设计上下文的参考，不是哥伦比亚联邦的法律；I-75/I-45仅表达乡村/郊区与城市/工业区尺度目标，并未据此宣称复刻某段实测断面。没有访问受限 AASHTO 全文，不能宣称符合其所有条款。

| 来源及适用前提 | 核查到的参考值 | 对AFL的约束 |
|---|---|---|
| [FHWA Interstate Design Standards](https://www.fhwa.dot.gov/programadmin/interstate.cfm)，页面自身提示部分内容陈旧、更新中 | 举例为全封闭、每方向至少2车道、12ft车道、10ft右肩、4ft左肩；设计速度例50–70mph随地形 | 作为基础尺度，不将摘要当所有现行场景的完整规范 |
| [TxDOT 8.1.7 Shoulders](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-1-design-considerations/8-1-7-shoulders.html) | 四车道外肩至少10ft、内肩4ft；六车道及以上内肩10ft；疏散用途外肩推荐12ft | B不能照搬A的4ft内肩；可选3.75m外肩需单独扩大包络 |
| [TxDOT 8.2 Rural](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-2-rural-design-elements.html) | 常见乡村median 50–100ft，较宽可有独立双向线形；辅路通常短而稀疏 | 乡村默认分离、留绿地；不全国复制密集辅路 |
| [TxDOT 8.4 Urban](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-4-urban-design-elements.html) | 城市四车道median至少12ft，六车道及以上24ft，重卡prefer26ft | B以包含内肩的8m median为建议；不混用章节中其他受限下挖断面数值 |
| [TxDOT 4.7 Horizontal Alignment](https://www.txdot.gov/manuals/des/rdw/chapter-4--basic-design-criteria/4-7-horizontal-alignment.html) | 曲率、速度、超高和侧摩擦联动；常用e上限6%，8%有协调/文档前提；高速主线不能套“城市max4%”例外 | 按设计速度求R/e/过渡，不能把45°折角当缓弯；normal crown建议2% |
| [TxDOT 4.8 Vertical Alignment](https://www.txdot.gov/manuals/des/rdw/chapter-4--basic-design-criteria/4-8-vertical-alignment.html) | Table4-11 freeway在60–80mph：level3%、rolling4%；50–55mph：4%/5%；竖曲线由K=L/A和视距等控制 | 5%是演示/特定条件，不是全国高速默认。高设计速度主线目标≤3%，rolling≤4%，例外须分类 |
| [TxDOT 15.7 Ramps & Direct Connectors](https://www.txdot.gov/content/txdotoms/us/en/manuals/des/rdw/chapter-15-grade-separations-and-interchanges-/15-7-ramps---direct-connectors-.html) | 匝道宜不超4%；示例最大坡度随速度25–30mph7%、35–40mph6%、45mph及以上5%；单车道14ft，多车道每道12ft，肩另算 | 匝道有自己的速度/坡度/宽度；鼻端标高、横坡和加减速距离不能由主线宽度推导 |
| [TxDOT 8.1.18 Frontage Roads](https://www.txdot.gov/manuals/des/rdw/chapter-8--freeways--4r-/8-1-design-considerations/8-1-18-frontage-roads.html) | 辅路服务地方交通/沿线出入，按collector标准；新辅路一般单向，例外另批 | M4按城市连接需求选择，不作为全国必有标准件 |

### 单位与取整

取1格≈1m，1ft=0.3048m。视觉顶点允许分数格，地基和claim最后向外覆盖到整数格，不能先逐车道四舍五入成整数再算总宽。

| 构件 | 英制参考 | 精确m | AFL建议m | 偏差 |
|---|---:|---:|---:|---:|
| 标准车道 | 12ft | 3.6576 | 3.75 | +0.0924（+2.53%） |
| 标准外肩/B内肩 | 10ft | 3.0480 | 3.25 | +0.2020（+6.63%） |
| A内肩 | 4ft | 1.2192 | 1.25 | +0.0308（+2.53%） |
| 可选疏散外肩 | 12ft | 3.6576 | 3.75 | +0.0924 |
| 匝道单车道参考 | 14ft | 4.2672 | 暂建议4.50，M4确定 | +0.2328；此值不含肩 |

这些是便于1/4格参数化的开发选择，不是强制模仿美国误差。未来可以输入3.6576等精确值，碰撞/方块覆盖另算；原型固定A以避免无意义资产变体。

## 2. 参数化断面与每层宽度

median宽度在下表统一指**两侧最内行车道边缘之间**，包含两个内肩；“未铺装中隔带”不含内肩。路面铺装总量与从最外路缘到最外路缘的跨度是两种量，不能混淆。

| 层/构件 | A：乡村/城际默认双向4车道 | B：城市/郊区双向6车道 |
|---|---:|---:|
| 每方向车道 | 2×3.75=7.50m | 3×3.75=11.25m |
| 每方向内肩 | 1.25m | 3.25m |
| 每方向外肩 | 3.25m | 3.25m；疏散可选3.75m |
| 两侧行车道+肩铺装量 | 2×12=24m | 2×17.75=35.50m |
| 未铺装中隔带/中央实体与侧向余量 | 14m草地（需排水、护栏条件另设计） | 1.50m中央安全带；建议1m实体+两侧各0.25m工作余量，非认证防撞尺寸 |
| 含内肩的median | 14+2×1.25=16.50m≈54.13ft | 1.5+2×3.25=8m≈26.25ft |
| **视觉道路总体跨度** | **38m** | **37m**；加宽外肩版本38m |
| 两侧路基边缘/排水初步带 | 每侧2m，**总42m** | 每侧2m，**总41m**（加宽版42m） |
| 两侧检修/构件余量后的局部施工模板 | 再每侧2m，**总46m** | 再每侧2m，**总45m** |
| 初步规划预留 | 复用±32中心格的**65格**走廊仅作下限候选 | 同为**65格**下限候选；辅路/匝道另加 |
| 真实削填、清障、clear-zone、隧道/桥头、ROW | 按工程求包络，可能超过65，不预称满足安全规范 | 同左；墙体、集散路、匝道不能挤入固定45/65模板 |

A的绿地更宽，因此四车道总跨度可以大于B的紧凑六车道。这符合两者用途差异。65格是AFL现有claim规则，不是TxDOT ROW或clear-zone要求；2m边带和2m施工余量也只是本方案预算，正式安全净区必须独立计算。

示例可选B辅路方案（仅为占地预算，不实施）：每侧外分隔6m + 单向两车道2×3.5m + 两侧各1.5m肩/边带，共16m/侧，主线37+32=69m；再两外侧各2m施工边带=73m，已超过65。匝道再按4.5m车道+肩+分隔/鼻端/渐变区求联合包络，不得用73m当互通通用宽度。此辅路尺寸是AFL占位参数，尚未按collector速度、交通量或行人条件设计。

### 与旧断面及claims对比

当前 `MAIN_WIDTH=23` 对应23格栅格结构；标线带约在±2/±6/±10，中央单格slab；没有证明符合3.66m标准车道与内外肩独立组合。`ROW_MARGIN=3` 形成29格露天ROW。Tunnel低部净宽25、shell27、portal29；`areaColumns` 横向±16形成33格周边清障排除包络，**33并非净空或总ROW**。另外RouteGraph查询41格、施工claim65格；这五层不能合并成一个宽度。

- A 视觉38比旧23增加15（65.2%），施工模板46比旧ROW29增加17（58.6%）；B对应37/45。两者都超旧33 tunnel area。A不应直接开一个38m大宽单洞：建议双洞分别求车道+肩、检修带、壳厚及围岩间隔，缩窄median的过渡先设计，不凭空在洞口横跳。
- 曲线offset的轴向AABB宽度会增大，不能用直线宽度假设±32总够。桥边梁、护栏、墩、桥头过渡和中隔带变化都必须发布完整稳定claim。
- 土坡粗估：总46m模板外若两边各10m高差、坡比1V:2H，每边再20m，总86m；此例说明claim必须按真实工程计算，非建议一律按1:2施工。
- 海岸终点现在逐格检查65格全宽；若工程包络增大须重算终点/桥头、交汇预留区与claim版本，同时维持Route/Edge ID迁移语义。不可因原claim65大于路面38就称宽度升级已安全。
- Terrain规划32格grid只能做粗筛；单个A断面已经超过一个grid格，主干/城市/河道需连续精采样。先优化路线和桥隧选择，不为旧线路降低合理山脉或填平水系。

## 3. 连续几何与批量Mesh方案

参数记录而非人工变体：sectionId/lanes/shoulders/median、水平alignment、profile、crossfall/e(s)、station原点、材质集、LOD策略、版本。Astra生成道路面、肩、标准中隔带、直坡、竖曲线、平缓曲线分片、标线里程相位和有限标准接缝；Claude负责艺术母件，算法实例化。宽度过渡、超高旋转轴和分合流需有独立连接段，不在任意chunk边界切换断面。

UV以全局里程s和分带横向u展开；现有0..1限制下，本原型每4m周期显式切UV，保留相同s相位。量产可以有限重复模块或道路专属材质适配，但不能改全局Loader放开任意值而影响枪械/机器。atlas需padding、mipmap防渗、每材质密度约定；跨块不能各自从UV=0开始。曲率与超高使quad不共面或UV导数不同，必须Triangle回退。现有面法线不能保证曲面光照平滑；需控制细分误差、同步halo端面，或未来隔离道路法线后端，不能偷偷扩共享格式。

| 比较项 | 有限可重复离线模块 | 按路线/渲染区块运行时Mesh | 推荐混合 |
|---|---|---|---|
| 连续性/变宽 | 网格化档位有限，接头需严格契约；任意曲率组合容易错台 | 可从同一连续surface直接生成，有确定性采样/裁剪需求 | 直路/艺术母件复用；曲面路床来自连续几何 |
| CPU | 加载解析与逐帧提交；复用数据可降建模成本 | 初次生成/失效重建成本，不能每帧重算 | 预算队列+异步纯几何，渲染线程只上传/提交 |
| 内存 | 唯一资源多会被现有全量reload常驻；有限目录可控 | 可见区域缓存/LRU、缓冲池、按需释放 | 共享材质/原型+有限可见tile；不存全国独立大JSON |
| draw calls | 同材质可共享buffer，但每实例调用、BER/透明flush仍有成本 | 按空间tile和材质批次，兼顾剔除；合太大降低剔除效率 | 目标每可见tile少量材质批次；实测，不假设一个part=一draw |
| 光照/LOD | 固定细分，长模块单packedLight不够 | 每tile/顶点采光，独立LOD接缝需解决 | 先小tile固定密度，性能证实后上LOD；本轮无LOD |
| 加载/保存 | 文件版本固定可追溯，地图复杂变化造成组合增长 | 保存参数/route版本/工程决策，不保存每帧Mesh；重载重建 | 存生成版本及工程状态，资源版本独立 |
| 兼容/回滚 | 既有sidecar安全，但未来不同母件接头需版本化 | 新渲染入口、持久/失效/着色器兼容工作较大 | V1保持，新世界显式版本；禁止自动重铺旧块 |

推荐以16/32格空间渲染tile为初始候选，几何按全局station和halo生成，再对真正XZ chunk边界裁剪。工程256格窗口不等于渲染tile。此次4个64m离线窗口只是工具能力证明，不能将其复制为全国每64格一个永久独占资源的架构。

Oculus/Embeddium验收矩阵：纯Forge、Embeddium、Oculus+实际目标shader，分别日/夜/隧道内外/资源重载/远近剔除、标线depth、PBR法线切线和阴影；不透明路面先行，透明灯罩等艺术分批。记录可见tile/面/顶点、draw提交、构建/重建耗时、内存、p95/p99帧时间。当前无这些数据，不能写兼容PASS或推算FPS。

## 4. 推荐V2职责接口（尚未实现）

```text
HighwayRouteGraph          → Route/Edge/Node/attachment/topology authority
RoadAlignment             → point(s), tangent(s), curvature(s), width(s)
RoadVerticalProfile       → height(s), grade(s), verticalCurvature(s)
RoadSection/Crossfall     → surface(s,u), normal(s,u), lane/material bands
TerrainEvidence           → current ground/support/water + planning hint + provenance/version
EngineeringPlanner        → surface/cut/fill/bridge/tunnel spans + full envelopes + rejected reasons
ConstructionPreflight     → COMPLETE claims/protection/snapshot + bounded write plan; UNKNOWN rejects
ChunkConstructionWriter   → owned chunk only, state compare, journal/idempotence; no route invention
RoadMeshTileCache          → pure mesh inputs, finite visible cache, light/cull/material batches
RoadSurfaceQuery(x,z,hint) → candidate surfaces / height / normal / lane / version / VALID or UNKNOWN
```

surface约定Y为可接触表面；旧地面方块Y与旧高度图首空气Y必须显式转换。计划、渲染、碰撞与未来车辆query使用同一连续surface但允许不同近似精度。玩家步行AABB/VoxelShape仍是台阶近似；车辆query只提供地面接触信息，不包含悬挂、轮胎、摩擦积分或完整动力学。

## 5. Claude正式资产交接清单（没有发起并行修改）

统一建议：1作者单位=1/16格；Y向上；静态物件默认前方-Z（NORTH），道路母件局部沿+X、外侧+Z时必须附转换；给pivot、尺寸、端点平面、接点/交互anchor、有限碰撞盒和独立选择盒。每个sidecar一个atlas，材质“槽位”先是part名+atlas区域，不能未经后端支持交多个独立材质。

| 资产族 | Claude交付及尺寸接口建议 | 材质/动态要求 |
|---|---|---|
| 波形梁护栏/端头/桥接转换 | 可复用4m母件；rail_start/end相距4m；默认顶高1m；端头单独约4m包络，详细防撞造型由Claude定稿 | steel/paint/bolt/damage；弯段由算法按锚点布置，不手绘百种角度 |
| 中央混凝土屏障/桥边护栏 | B中央1.5m安全带内；实体暂1m宽；4m母件、顶高建议1m；留检修侧距 | concrete/steel/reflector；高度不是认证防撞性能 |
| 灯杆/灯头 | 杆脚anchor，建议12m高、4m横臂；放置需落在施工带内并检查上方包络 | metal/glass/emissive；静态优先，不默认每杆动画/BE |
| 路牌/标志门架 | 牌面可参数文字/贴花；门架横跨单幅A12m或B17.75m加侧距，建议最低下缘离实际最高路面6m | coated_metal/sign/reflective；6m为AFL接口预算，需未来正式净空校核 |
| 桥梁伸缩缝/桥头缝/检修件 | 标准缝母件长1m横向平铺，纵向占0.25m建议；必须接同一surface，无视觉隆起侵入车轮 | metal/rubber/concrete；不逐宽度复制整座桥资产 |
| 隧道洞口艺术、灯/通风/设备箱 | 洞口依M3断面后再锁尺寸；设备不占车辆净空 | lining/concrete/metal/emissive；不能先按旧33格强制建正式洞口 |
| 收费/检修/互通特殊构件 | M4场地确定后，提供底座和连接anchor；收费不是全国默认 | 精致静态；需要时单独动臂/指示灯通道 |
| 可动道闸/可变信息牌/检修件 | 旋转/平移通道与状态范围、碰撞状态明确；独立小资产 | Claude制作精致Mesh/动画/PBR；Astra仅接口和实例化 |

共享PBR要求：基础色、normal、roughness/metallic等按项目实际shader资源契约落地，不凭本表创建新打包约定；atlas像素尺寸一致、法线手性/UV方向一致、padding，材质槽名稳定。不要替换已有办公/武器资产。原型只有开发占位atlas，没有正式PBR或动画。

## 6. 后续M1–M5与客观验收

| 工作包 | 内容与边界 | 与其他阶段的依赖 / 验收 |
|---|---|---|
| M1 连续几何+真实断面+局部Mesh | 保持graph拓扑/ID；A断面、s/u surface、竖曲线/横坡、局部渲染tile、有限碰撞和只读surface query；可先隔离测试场，不自然注册 | 不需要先开始Terrain2便能验几何；生产标高必须等Terrain2真实generator校准。Road V1-B不改 |
| M2 地表施工/保护 | 可证自然来源、claims completeness、自然结构/人工建筑/既有路保护、削填/地基、预检拒绝和写前对比 | Terrain2地表，Terrain3地下/装饰实现后再次回归；Road保护层可共享接口但不绕过其FAIL/规则 |
| M3 桥隧/净空 | 分幅桥/双洞、弯道隧道、portal/桥头过渡、支撑、净空和遮挡，复用已有桥塔/钢索成果 | M1 surface与M2安全；真实地下数据依赖Terrain3相关交付；不把宏观cover当岩体 |
| M4 城市互通/匝道/交通连接/正式资产 | 按需求少量互通、辅路；连接Road V1-B经独立验收后合约；Claude正式替换 | 城市/工业站点、主线参数、地形与道路连接契约确定；不全岛套I-45城区规模 |
| M5 全国/跨chunk/性能回归 | 有限route/工程/存档版本、生成顺序、存档重载、旧V1对比、性能/兼容/资源回退 | Terrain2/3与M1–4对应能力已验收；固定seed+坐标+生成器版本+硬件+shader矩阵 |

下一轮建议先做 **M1-A：独立测试场的256m连续路面渲染与有限碰撞验收**。只读取现有graph标识或使用明确标为demo的路线；不发布新自然路由，不在老世界自动铺路。准备新测试世界/明确隔离场地后，先preview/dry-run再由用户实机确认。

客观PASS条件：

1. 同一s/u权威输出A断面38m；车道、肩、median与本契约一致；中心线无硬折，5%示意段20m升1m，坡过渡连续，正常横坡与超高方向可观测。
2. 真正曲线/坡面穿XZ chunk边界，端顶点误差≤1e-4m、UV周期一致；正序/逆序/随机生成的最终几何hash一致。此次只做了直线处精确chunk平面，不能替代此条件。
3. 使用者步行不穿地、不被不可见大盒阻挡；碰撞表面与视觉误差预算≤1/16m（待具体有限shape方案证明）；车辆query在单层面返回height/normal，双层/unknown显式处理。没有要求开发车辆动力学。
4. 保护方块/玩家方块/既有道路样本必须整段拒绝且写入数0；M1若仅渲染可使用不写世界的测试方式，M2前不得宣称自然施工安全。
5. 保存退出重进、资源重载、转向视角/跨chunk卸载无消失或孤立残片；只在用户实测后记PASS。
6. 预定500m视距测试场固定硬件、分辨率、shader记录5分钟baseline/road的帧时p95/p99、heap、可见tile和提交数；M1-A建议预算p95增量≤2ms、road生成主线程单帧≤2ms、稳定后缓存无持续增长。它们是待测验收目标，不是现有性能成绩。

完成V2-0后停止；由用户选择是否进入M1或先深化某项审查。这里的方案和PASS条件不代表授权已实施M1–M5。
