# AFL Worldgen Architecture Decisions V1

日期：2026-09-13。ADR 正文保留 **Phase 0 原始设计基线**；目前 Phase 1 WG-01～05.1 已完成无接入基础实现，WG-06 已冻结 Rural legacy 计划并抽取有限共享核心；跨系统协调仍未启用。事实来源：[Rural Audit](rural_generator_audit_v1.md)、[Highway Audit](highway_generator_audit_v1.md)；规范细节与阶段见 [Architecture V1](unified_worldgen_architecture_v1.md)，当前 WG-06 范围及测试边界见 [Rural Legacy Regression](rural_legacy_regression_wg06.md)。以下目标不能解读为已有生产 provider、SavedData 或旧世界迁移。

## ADR-01 — 保留双生命周期

背景：Rural 用 StructureStart/Piece 有限计划；Highway 用 Feature 随机访问无限走廊。

决定：Site/Structure 与 Infrastructure 分层，公共服务在其下。Highway 不迁成 Structure，Rural 不使用 Highway route planner。

替代：统一 Generator 基类、所有设施 NBT 化，均不采用。

后果/验收：各自保有序列化/调度/写入实现；Phase 2/3 回归入口与 chunk 权限，公共代码不引入统一生命周期。

## ADR-02 — Shared Spatial Claims 与顺序无关裁决

背景：当前两端都无跨系统 claim；可变“先到先得”会使 seed 结果受加载顺序影响。

决定：有限半开 AABB、可选Y、hard/soft、owner/稳定ID/版本/priority/margin/连接边；margin配对取max。查询带完整性与预算，UNKNOWN 对必需施工先拒绝。自然站点候选使用确定性最大envelope；固定优先级+稳定ID排序，任何更高资格候选相交就拒绝，不递归补位。

替代：全世界空间图、全局锁、第一登记者赢，不采用。

后果/验收：接受未用预留地与准入率变化；随机查询顺序/负坐标/边界/预算耗尽测试必须一致。Shadow阶段不改变分布，启用单独版本。

## ADR-03 — 保护优先级与 Highway 冲突激活门

背景：稀疏主干值得预留，但 startup/Bunker、既有内容不能默认被覆盖；当前无自然 reroute。

决定：保护区100 > 已知既有内容90 > 新Highway70 > 新Site50 > 软缓冲10；新Site可邻近主干并预留入口。City软区可以包含主干，硬地块避让。保护信息不完整时不启用需要它的协调profile；高优先保护与Highway冲突不靠单chunk删路解决。

后果/验收：新世界启用前完成确定性保护查询；旧世界维持legacy。Vanilla未知结构保护不能写成全部支持；不以强载消除未知。

激活只预检有限已知保护区，并要求其他必需provider具备确定性、走廊兼容的放置契约；不扫描无限世界，查询存在本身不等于兼容保证。无法满足的组合暂不启用，运行时契约违例必须报告失败，不声称已保证不断路。

## ADR-04 — Structure Metadata + NBT Building Contract

背景：Rural几何来自NBT，角色与选择硬编码；既有City authoring metadata不等于生成池注册。

决定：资产记录front/ground offset/rotations/socket/revision；尺寸从NBT完整size派生；数量与weight放recipe，spacing放placement，City zoning归City。Origin为模板原点，地面公式 originY=groundSurfaceY-offset；socket同Vanilla变换，V1 mirror=NONE。

后果/验收：旧authoring字段显式映射，road_facing不能代替入口；四向机械验证与BE/双格实测分开；air写入计入bounds和保护；资源替换使QA失效。

## ADR-05 — Connection Socket / Anchor 分阶段

背景：两系统当前均无可用外部road socket；Highway交点只是上下跨越。

决定：资产局部socket→站点世界anchor；包含owner/dimension/position/facing/type/status。V1 Highway仅PLANNED_HOOK，未做匝道不得发布可通车HIGHWAY_EXIT。V2连接local/service roads，Future独立设计exit/ramp。

后果/验收：发布anchor不等于已连接；连接需两端认可和窄带claim，不允许切穿主体/主线隔离带。

## ADR-06 — Terrain Query 输入语义

背景：Rural自然与dev地形不同；Highway同时使用noise与施工快照；没有共享工业液体分类。

决定：显式source/后端/validity；Y统一为表面上第一格位；NONE/WATER/INDUSTRIAL_WASTE/OTHER_FLUID配合UNKNOWN。噪声不能证明BE不存在，冰用surfaceType。采样策略和阈值独立。

后果/验收：无自动实时fallback或force-load；共享契约先不挂接入口，来源/高度/流体未知测试构成WG-01验收。

## ADR-07 — Dev / Natural 核心共享

背景：两份Audit都发现开发测试与正式路径语义分叉。

决定：NATURAL_PARITY复用相同planner/validator/资源版本/选择；工程实验显式override并标识。Rural先以现有自然选择为legacy基线，weight/maxCount新语义需独立版本批准。

后果/验收：同输入plan digest一致，自然writer单独验收。开发Highway八向测试不能代表自然走廊；不要求取消有用的实验命令。

## ADR-08 — City Placeholder 先行

背景：正式NBT供应不应阻塞City布局开发。

决定：dev namespace四种实际有尺寸的NBT，完整front/ground/socket/rotation/claim。Generator只按category配方消费；正式到货仅替换资源/数据映射，重做资产QA。

后果/验收：正式pool/build检查排除placeholder；新体积需slot验证，不保证任意NBT无条件互换；不替换已生成世界建筑。

## ADR-09 — 混合持久化 C

背景：Highway可重建，Rural已有权威Piece；全部SavedData会保存无限网络并诱发先到先得。

决定：确定性走廊/候选claims按需重建；有限accepted/partial站点保留紧凑索引，Piece仍权威；缓存不是施工状态。worker只读快照，服务器线程合并索引，崩溃后不能把索引缺失视为空地。

后果/验收：不保存无限路网；同ID摘要幂等，不同摘要拒绝；不全世界扫描，无法安全核对返回UNKNOWN；重启/索引滞后/资源版本场景需要测试。

## ADR-10 — Chunk 写入与部分提交

背景：两系统都有部分提交风险，跨chunk全事务代价高。

决定：必需资源/已知claim冲突先abort；当前slice预检后受权限写入；NOT_OWNED是正常后续slice，不是失败。区分APPLIED_SLICE与COMPLETE；异常保留PARTIAL claim与诊断，无全世界rollback。

后果/验收：BE/不可破坏物及模板air同受保护；可选装饰best-effort明确声明，主路/桥墩不能默认为可选；自然无force-load，dev有限恢复账本另行约束。

## ADR-11 — 版本与旧世界

背景：路线、高程、资源更新可能破坏新旧chunk边界与进行中的Piece。

决定：世界profile与系统算法版本固定，资产revision/digest固定；缺版本旧世界为LEGACY。已生成chunk不重放；进行中计划使用旧资源，不能同路径覆盖后继续读取新体积。

后果/验收：保留被引用旧NBT或拒不兼容profile；改Highway公式/高程策略仅新世界或独立边界迁移；首次协调测试使用新世界。

## ADR-12 — 最小类型与分阶段实施

决定：优先record/enum，仅TerrainQuery、ClaimQuery作为首批共享接口；保留各系统工程/布局/cache/seam。新core不要求大规模移动现有文件。

后果/验收：Phase 0仅文档，Phase 1无接入primitive，随后Rural兼容、Highway协调、City placeholder、正式资产、POI。第一实施任务WG-01，模型分工新机制Astra、重复适配Sol。架构评审后只启动首项，不自动执行backlog。
