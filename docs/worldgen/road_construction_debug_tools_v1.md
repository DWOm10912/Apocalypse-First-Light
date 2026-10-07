# AFL 道路施工开发测试工具 V1

日期：2026-10-07。Minecraft 1.20.1 / Forge 47.4.22 / Java 17。

状态：**单路段适配器、只读Survey及命令/报告代码已实现；此前单次离线 compileJava 已通过。当前用户实机验收失败（V1-B = FAIL），Survey / Preview 一致性与具体拒绝原因仍需调查，本文不登记施工、性能、碰撞或恢复PASS。** 用户已测试，并因此启动[Terrain V2 审计](columbian_federation_terrain_v2_research.md)，不是尚未开始验收。道路 V1-C 暂停；新地形目前仅有设计，实施后须重新验证 V1-B。本次文档同步不编译或启动游戏。

依赖[统一规格](north_american_roads_and_lots_spec_v1.md)、[V1-A](north_american_roads_v1a_implementation.md)、[正式V1-B施工核心](north_american_roads_v1b_implementation.md)和[静态资产契约](road_surface_assets_v1.md)。本轮不增加方块、美术或自然生成入口。

## 1. 单路段范围与实际命令

仅非production开发环境、主世界、Creative且OP权限2的玩家可用。三种道路均为正式断面：R12沥青12/完整ROW22，C14为14/26，I12为12/24。长度为整数16～64格，省略时32格；方向north/south/east/west。

```text
/afl roads preview segment r12 32 north
/afl roads prepare segment r12 32 north confirm_unbuilt

# 省略长度时为32；仍须明确方向
/afl roads preview segment c14 east
/afl roads prepare segment c14 east confirm_unbuilt

# 固定坐标形式，x/z是起点中心线的世界整数边界坐标
/afl roads preview segment r12 32 north at <x> <z>
/afl roads prepare segment r12 32 north at <x> <z> confirm_unbuilt

/afl roads build "<plan_id>" confirm
/afl roads status "<plan_id>"
/afl roads pause "<plan_id>"
/afl roads resume "<plan_id>" confirm
/afl roads cancel_preview
```

方括号说明不属于命令，尖括号替换为报告中的实际数值。无at时，起点为`floor(player X/Z) + direction × 12`，终点为`start + direction × length`；不随鼠标角度改变。道路沿轴的半开区间长恰好length，宽度不包含肩部。**Preview回显固定坐标Preview/Prepare命令；玩家移动后应复制固定坐标版本，不能假设无at命令仍指向同一处。**

预检开始时玩家须靠近范围（保守包络外32格以内），且在道路、最大4格肩部、额外2格缓冲之外。正式施工每tick再次检查所有当前维度玩家；任何玩家进入单路段实际施工bounds加2格包络即PAUSED，不在脚下写入。离开后使用resume原plan_id，不重新prepare半成品。这个位置检查只新增于source ID为`segment:`的开发样本，旧街区行为不变。

## 2. 适配器、末端与正式施工流程

`RoadSegmentPreset`只创建一条edge、两个`DEVELOPMENT_TERMINAL`节点，无lot、停车场或建筑。新增`Layout.SEGMENT`仅供该适配器；旧plan/synthetic命令不枚举它，普通RoadPlanner拒绝直接请求SEGMENT，避免误套街区布局。

两个端部各占沿线1格，分带直接调用正式`RoadCrossSection`；节点`armExtent=1`，只用于孤立开发样本。旧END/转弯/T/十字的节点形状和8格过渡不变。端部固定整数G，沥青仍G−3/16，人行道及设施带顶G；中间由原V1-B规划纵坡。两端开放结束，侧路缘沿正确沥青侧终止，不横铺挡路的整宽凸缘；正式支撑和有限肩部工程延伸至外侧自然地面，支撑或削填不合格仍拒绝。不是正式道路接驳、回车场或自然生成死胡同设计。

样本复用`RoadConstructionPlanner.begin/advance`完整实际预检，`RoadConstructionJobs.prepare/start/status/pause/resume`及原快照、分批写入、持久化。**没有第二套方块填充器。**

候选身份含适配器版本`road_segment_debug_1`、道路类型、长度、方向、起点；布局plan ID另绑定种子、维度和V1-A规格版本。实际施工plan ID仍由原V1-B对配置、世界快照及目标状态生成。样本不登记永久自然道路claim；正式Prepare才登记开发施工台账。

Preview不登记可执行计划；Prepare必须confirm_unbuilt且完整通过，随后Build必须精确plan_id确认并再次核对世界。沿用最多4条台账、重叠/重复拒绝、过期检查及分批恢复限制。没有撤销或事务回滚，施工前备份开发存档。

## 3. Survey候选搜索

```text
/afl roads survey
/afl roads survey 128
/afl roads survey 256
/afl roads survey 512
/afl roads status
/afl roads cancel_survey
```

默认半径256，只接受128/256/512三个档位。以启动命令时玩家整数X/Z为圆心，在半径R/2和R上各取8个固定方向的中心；斜方向用`floor(r/sqrt(2))`换算X/Z偏移。每个中心测试NORTH和EAST两种轴向，**总计32个R12、32格长的候选**。Survey当前不穷举三种断面或任意长度，推荐R12不等于该地点已验证C14/I12。反方向同轴足迹无必要重复采样。

候选起点为中心减去方向16格；中心在给定半径内，完整道路/肩部可超出中心半径，报告给出真实bounds。不宣称穷尽圆内所有可用地形；未发现候选不能证明整个区域无地可建。

每个候选先检查已知Highway/启动区/地堡/authoring保护和世界边界。未知保护查询返回UNKNOWN，明确冲突REJECTED。

- footprint加4格包络的所有chunk已经加载：先取15个实际地表样本，再进入正式V1-B完整逐柱预检；全部通过才为SUITABLE_VERIFIED。
- 任一包络chunk未加载：该候选统一复用`RoadTerrainQuery`的NOISE_PRE_DECORATION估计（WORLD_SURFACE_WG及有限NoiseColumn支撑扫描），**不混淆为实际方块检查**。只检查15个稀疏高度/基础流体样本，并计算中位数恒定G方案的预计削填；满足单点限制时为SUITABLE_ESTIMATED。无有效采样结果或表面未知返回UNKNOWN。

15个样本是沿线5站×横向左/中/右3条采样线。报告的sampled高度范围、地形平均/最大坡度都是稀疏样本统计，不是整个区域的精确统计；实际预检通过的planned坡度和削填来自正式V1-B输出。

估计方案planned_max_grade=0明确表示稀疏恒定G假设，不是已求解真实施工纵坡。未加载区域的树木、洞穴、玩家内容和实际工程体积未验证。已加载候选会检查植被、流体、近地表支撑，并最终复用正式白名单/结构引用/噪声差异及全部工程限制。

## 4. 状态、报告与权限边界

| 状态 | 含义 |
|---|---|
| SUITABLE_ESTIMATED | 基础噪声稀疏初筛通过，未执行完整实际方块检查，不是安全或施工许可 |
| SUITABLE_VERIFIED | 本次正式V1-B实际方块预检通过；不证明所有玩家/模组保护风险均已排除，也未登记施工 |
| REJECTED | 已检查项明确失败；查看terrain_rejections |
| UNKNOWN | 卸载、查询异常或实际检查未完成等，不能视为通过 |

最多向聊天推荐5个候选，VERIFIED优先，其次按预计最大削+填排序；所有32个已处理结果保留JSON。每个候选提供可复制的固定坐标Preview及Prepare命令。接近推荐区域、加载其区块并站在施工范围外后，必须重新执行Preview/Prepare，世界发生变化可使此前候选失效。

开发运行目录下：

- `afl_debug/roads/construction_segment.json`：最近一次单路段Preview/Prepare报告。
- `afl_debug/roads/road_survey.json`：最近一次完成或达到时间上限的Survey。
- 旧`construction_<layout>.json`文件和街区命令保留。

标准runClient对应仓库`run/afl_debug/roads/`。报告覆盖固定文件，不无限新增。JSON记录schema/spec/construction版本、种子/维度、候选ID、起点/中心等坐标、类型/长度/方向、terrain_source、validation_level、保护边界、工程预算、拒绝原因和命令。未得到的指标为null或UNVERIFIED，不用0伪装检查结果。Survey候选共享报告顶层engineering_budget。

Survey和Preview的`construction_authorized=false`；Prepare报告也维持该值，因为仍需Build确认，另用`can_confirm=true`表示台账已接受、允许下一步显式确认，不表示已经开工。报告不能替代当前台账状态。

## 5. 性能预算与生命周期

- 不创建chunk、不使用票据、不传送加载地形；未加载处复用基础生成器地形查询，单次Survey噪声缓存上限480列，用完释放。
- 每维度一个只读诊断任务：Survey与Preview/Prepare互斥，不叠加两个预检预算。旧施工台账执行预算不变。
- Survey上限32候选、每候选15粗样本，最多480粗样本；一tick最多2个粗采样单元，单元之间检查4ms时间预算。**单次生成器调用不可抢占，4ms不是硬实时帧保证。**
- 完整实际预检一tick最多推进32柱（还受原columnsPerTick更小值约束），随后也分批生成原施工快照；始终只保留一个候选的大快照。单候选32×22，加最大4格肩部的矩形包络不超过40×30＝1200柱；32候选最多38400实际采样柱，每柱垂直读取仍由原工程配置界定。
- 每次Survey最长12000世界tick；达到上限输出已完成候选的部分报告，timed_out=true，不把未完成项标为通过。冷却200tick；预检沿用100tick冷却。
- 玩家退出、换维度、失去权限或世界卸载取消正在扫描的内存任务；cancel_survey显式取消，不输出伪完整报告。单个候选异常写UNKNOWN后继续有限搜索。
- 无异步线程访问Level，无自然生成缓存/占地注册。性能仍需用户实测，不以预算存在宣称无卡顿。

## 6. 最小实机验收

1. 备份无人建设开发测试存档，Creative＋OP，选择保护范围外的干燥陆地。
2. 先尝试`/afl roads preview segment r12 32 north`。检查回显位置和`construction_segment.json`；实际道路只需32×22，另留默认各侧3格肩部，包络约38×28，不需要整街区平地。
3. Preview通过后复制回显的固定坐标Prepare命令，带confirm_unbuilt；等待PREPARED，阅读实际削填、保护和工程预算。
4. 使用`/afl roads build "实际plan_id" confirm`，用status观察；始终站在施工包络外。实际走行、路面3/16高度、两端支撑、侧路缘、保存重载分别验收。
5. 若找不到位置，运行`/afl roads survey 256`。复制推荐Preview命令，靠近、加载区块后重新完整预检。ESTIMATED和UNKNOWN均不能跳过Prepare。
6. C14/I12、其他方向、16/64长度、暂停/resume及旧synthetic mixed/t、plan mixed、街区preview/prepare分别验证。本轮未运行这些实机命令。

仍要求干燥、支撑可靠、无树/未知建筑的地形；不放宽最大削填各3格、纵坡1/16、水域/洞穴/结构保护或工程体积。自然石土无法证明玩家来源，confirm_unbuilt不是绕过检查。测试工具不解决V1-C自然候选协调、跨候选连通、正式版本迁移或完整玩家/第三方保护。

## 7. 修改范围与验证

新增`src/dev/java/com/antaurora/apofirstlight/dev/RoadSegmentPreset.java`和`RoadSurveyCommand.java`；扩展`RoadConstructionCommand`，旧`RoadPlanningCommand`跳过适配器专属SEGMENT。主数据模型新增SEGMENT/DEVELOPMENT_TERMINAL，`RoadJunction`增加单站端部，旧二维节点不变；`RoadPlanner`防止误用专属preset；`RoadConstructionJobs`仅为单路段增加玩家入场暂停。没有重写施工器、修改工程配置或开启自然生成。

源码和文档完成后仅执行一次`./gradlew.bat compileJava --offline`。不运行processResources/build/check/test/GameTest/runClient/runServer、世界生成、截图或性能测试。编译与实机验收分别记录。
