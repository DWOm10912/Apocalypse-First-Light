# AFL 北美道路网络 V1-B：施工与地形适配

日期：2026-10-07。环境：Minecraft 1.20.1 / Forge 47.4.22 / Java 17。

状态：**V1-B 代码、静态道路资产、持久化及 Segment / Survey 开发工具已实现，此前离线 compileJava 已通过；当前用户实机验收失败（FAIL）。** 不是尚未开始验收；这正是启动 Terrain V2 审计的原因。没有注册自然生成入口；道路 V1-C 暂停开发。

2026-10-07 地形研究补充：[哥伦比亚联邦 Terrain V2](columbian_federation_terrain_v2_research.md)与[迁移计划](terrain_v2_migration_plan.md)目前仅为研究设计，未改道路或地形代码。用户反馈当前大量候选因浅层空洞、坡度及削填预算拒绝、缺乏连续可建设地形。审查确认正式预检默认检查地面下6层，任一空气格即可触发 `UNSAFE_SUBSURFACE_VOID`；未加载区 Survey 的噪声估算不能验证真实地下支撑。后续须分别验证地形改善和 Survey/Preview 来源一致性，不能认为前者自动修好后者。横断面、G/S、1/16纵坡及3格削填预算保持。当前验收失败状态不变，地形实施后必须重新验收。

## Terrain Phase 0 诊断补充（2026-10-07）

[坐标诊断与基准](terrain_v2_phase0_diagnostics_and_baseline.md)已实现，待用户验证。RoadConstructionPlanner 增加可选只读失败上下文，包含 XYZ、表面 G/方块 Y、station/lateral、支撑深度、block/below、bounds；profile 尚未求解时 expected G/H16 为 NOT_AVAILABLE。默认 begin 不启用观察；不改变工程规则、计划身份、生成结果或施工状态。

Survey/Preview 同用 RoadSegmentPreset 与正式 planner，静态审查未发现几何/起点/肩部/支撑算法分叉。Survey VERIFIED 仅指地形预检；Preview 额外要求玩家处于包络外且靠近，Prepare 还需要台账批准。新增 player gate 报告和统一 `ROAD_PREFLIGHT_VERIFIED` 标签。331,-762 曾见的 STEP_OUTSIDE 可由站位门解释，不能据此认定地形失败；其它实机不一致仍待复现。322,-812 的浅层空气具体位置由 `/afl terrain benchmark samples` 重新读取，不凭历史状态猜测。

V1-B 实机 FAIL 保持，未实施地形修复、未放宽 void/cut/fill/grade/肩部检查。

## Git 检查点验收记录（2026-10-07，历史检查点）

- V1-A 保留已通过的规划层实现；V1-B 施工器及全部开发测试命令保留。
- Segment 和 Survey 已完成代码实现，不代表道路施工或调查推荐结果已获实机验收。
- **Survey / Preview 运行一致性仍待复现**：Phase 0 已定位“地形 VERIFIED 与独立玩家站位门”的语义差别，但没有发现并修复工程几何分叉；必须按同一坐标、方向、长度及现场状态对照新报告。
- **当前用户实机验收失败**（用户后续明确确认），不能用离线编译、Survey 推荐或 Preview 状态替代施工成功。
- 后续优先开展 AFL 地形 V2；地形变化可能改变施工候选成功率，不能预先保证提高，也不能替代上述一致性调查。
- 地形 V2 完成后必须重新验证 V1-B 的候选、Preview / Prepare、实际施工、跨区块接缝、碰撞及保存恢复。
- 本次仅检查 Git 范围并同步文档，不修改 Java 或地形生成器，不运行 Gradle / 游戏，不启动地形 V2 或道路 V1-C。

权威横断面来自[统一规格](north_american_roads_and_lots_spec_v1.md)，二维布局复用[已验收 V1-A](north_american_roads_v1a_implementation.md)的 `north_american_roads_v1a_2`。施工版本为 `north_american_roads_v1b_1`。

2026-10-07开发工具扩展：已增加16～64格（默认32）的单路段Preview/Prepare适配器及只读Survey。复验仍优先小样本，不要求先寻找整街区平地。详见[开发测试工具V1](road_construction_debug_tools_v1.md)；当前用户实机验收失败，修复后重新验收，原工程上限和保护不变。

## 1. 已实现与边界

| 项目 | 状态 | 实际行为 |
|---|---|---|
| 实际世界预检 | 已实现 | 只读已加载区块的方块、流体、支撑、净空、结构引用和已知保护 |
| 道路纵坡 | 已实现保守版本 | 保留二维图，整数节点 G、整数地块入口 G、路段每格最多变化 1/16 |
| 削坡/填方/路基 | 已实现 | 完整批准快照内的天然材料削填与支撑，不平整整个候选或地块 |
| 沥青/路缘/人行道/设施带 | 已实现静态资源及施工 | 永久 ID，静态烘焙模型，无道路 BlockEntity |
| TURN/T/CROSS 与混合宽度 | 已实现施工分区消费 | 节点优先，不重新计算另一套道路宽度 |
| 入口 | 已实现道路侧与地块边界条带 | 内部停车场、内部路径、建筑主体仍只预留 |
| 保存/暂停/恢复 | 已实现有限开发施工台账 | 保存精确前后方块状态；显式恢复前复核，不自动续建 |
| 撤销/事务回滚 | 未实现 | 施工前用户备份；失败可能留下部分已施工区域，不称原子事务 |
| 自然道路/建筑生成 | 未实现 | 无 chunk 加载触发施工、无正式 NBT 放置、无 V1-C 激活 |
| 平地/缓坡/跨 chunk/复杂地形拒绝 | 需要分别实机验证 | 不以编译或资产存在代替任一项 PASS |
| 视觉/Survival采掘/保存重载 | 需要实机验证 | 本轮不运行客户端或 GameTest |

## 2. 数据流与二维布局

街区`preview/prepare`先调用原V1-A synthetic布局入口，仅得到相同种子/坐标/布局下的二维图、断面、节点、地块和入口；单路段入口改用专用小型拓扑适配器，仅有一条edge和两个紧凑端部，无地块。二者合成G=64 **不用于施工**，也不授权写世界。随后均由同一正式V1-B核心扫描真实世界并生成纵坡和逐格施工快照。

没有放宽或改写原 `/afl roads plan` 的平面地形约束。真实地形高程由 V1-B 的 `profiles`、`lot_ground_y` 和 `effective_lots` 表达；报告嵌入的 `source_layout` 只提供 X/Z 来源，其旧 Y 不应拿来放置建筑。

| 断面 | 沥青 | 单侧路缘 | 单侧设施带 | 单侧人行道 | 完整ROW |
|---|---:|---:|---:|---:|---:|
| R12 | 12 | 1 | 2 | 2 | 22 |
| C14 | 14 | 1 | 2 | 3 | 26 |
| I12 | 12 | 1 | 3 | 2 | 24 |

栅格化直接使用 `RoadCrossSection` 和 `RoadJunction` 的矩形。所有区间为 `[min,max)`；先登记节点分区，路段遇到节点格立即让出。重复格必须同一材料/归属，否则拒绝，不能通过后写覆盖前写解决冲突。

节点保存一个统一整数 G。各连接边在节点臂范围内同高，路段在这个接缝之后才允许纵坡。T 只消费实际三个臂，不制造第四条道路。最小转角为正交静态组件；高质量圆弧、路口过街及标线视觉完善不属于当前验收完成项。

## 3. 真实地形与保护

预检只读 `getChunkNow` 已加载区块，缺失或中途卸载返回 UNKNOWN；不创建区块、不增加票据、不自动飞行或传送加载区域。

扫描实际 WORLD_SURFACE，识别可清理的低矮植被及天然地面；树干、树叶拒绝，当前不实施伐木。读取削填深度、路基和头部净空所需方块，记录完整 guard。水、地下流体、近地表空洞、方块实体、非白名单表面/地下材料和不明净空均拒绝。天然材料白名单由代码明确限定，不能将任何可挖方块都当泥土。

每8×8网格点与生成器 OCEAN_FLOOR_WG 基础高度比较，偏差超预算返回 UNKNOWN。这只是异常警报；不是逐格噪声复刻，更不能证明天然材料没有被玩家放置。

保护来源：

- 现有 `HighwaySpatialClaimProvider`，包括主路线、保留区和桥梁占地，不修改路线。
- 启动区域的保守外围方形包络。
- 已生成地堡的真实旋转模板范围加8格余量；模板或旋转信息不可读则拒绝。
- `BuildingAuthoringCommands.overlaps` 的活动建筑制作保留区。
- 已加载 chunk 的有效 StructureStart 或非空结构 references：整个相关 chunk 保守拒绝。
- 施工台账里已记录的区域：不让新计划叠加覆盖旧工程。

**玩家与第三方保护的准确边界：** 当前无法可靠辨认玩家放置的泥土/石头，也没有假设所有模组都发布统一 claims。开发者必须选择新开发存档的无人建设区域，并用 `confirm_unbuilt` 明确声明；该声明不跳过未知方块、结构、流体或保护检查。不能将本机制推广为公共服务器的完整玩家建筑保护。

## 4. 纵坡和 G/S

- 节点 G：实际节点区域地面高度的中位数，节点内部平坦。
- 地块 G：各入口边界条带实际地面的中位数；同一地块所有入口共用一个整数 G。
- 节点和入口喉部给路段设定固定高程；中间段朝实际中心线高度拟合，同时满足前一格和下一固定点的可达范围。
- `maxGradeH16PerBlock=1`：每格最多变化1/16，即6.25%，比任务建议约8%更保守，遵从既有正式规格。配置可收紧为0，不能升为无限陡坡。
- 固定点之间不可满足坡度时 `SLOPE_TOO_STEEP` 或 `INCOMPATIBLE_NODE_OR_LOT_DATUM`，拒绝整个计划，不截断道路。
- 水平节点/入口：沥青 `S=16G−3`，人行道和路缘顶 `16G`。普通纵坡段可有非整数中间顶高，非沥青分带比沥青高3/16。
- 实际 NBT 尚不绑定；未来放置必须取 `effective_lots.G − ground_anchor_offset_y`，不能用合成64或分数沥青高度当 NBT origin。

## 5. 削填、路基与边坡

逐柱计算批准顶面及需要的修改。清理批准高度以上的多余天然地形/低植被，补齐低处地面，普通路面下采用现有石材支撑，保留不可修改的基岩支撑。施工区不撒落物品。

外侧只扩展有限肩部范围，使用周边草土、普通泥土、焦土/受损土壤或石材类别衔接；不足以收回到自然地形时拒绝。不会把所有边坡铺成混凝土。设施带保留土壤/地形材料；分数高度采用独立设施带面层，不铺成沥青。

完整地块不参与大范围削填。仅入口边界条带允许写入，地块边缘有只读高差守卫；其余地块内的方块不施工。非入口边缘最多允许一格自然台阶，超出拒绝；不声称已经完成地块内部高程验收或建筑地基整平。

拒绝原因包括 `CUT_DEPTH_EXCEEDED`、`FILL_HEIGHT_EXCEEDED`、`EARTHWORK_BUDGET_EXCEEDED`、`SEGMENT_EARTHWORK_BUDGET_EXCEEDED`、`SLOPE_TOO_STEEP`、`SIDE_SLOPE_BUDGET_EXCEEDED`、`PROTECTED_STRUCTURE_CONFLICT`、`UNSAFE_SUBSURFACE_VOID/FLUID`、`ACTUAL_NOISE_HEIGHT_MISMATCH` 等。UNKNOWN 不生成可确认施工记录。

## 6. 车辆/行人入口

正式消费 V1-A `road_access`、`entry_strip`、`sidewalk_contact`、`asphalt_contact`、`curb_transitionH16`。`internal_paths` 保留未来地块施工，不在本阶段铺整个停车场。

步行入口从人行道以 G 接到地块边界。车辆/后勤按完整净宽替换凸起路缘，用原有相对 S→G 的14/15/16量化层次过渡；再沿侧带缓降至地块边界的停车场 S。入口上没有阻断车道的连续凸唇，单格跨越不大于1/16。当前是量化平顶坡段，不是三角连续曲面。

目录中同地块的 drive/service 可以共用同一入口；只有道路、朝向、几何、高程相容的通道才合并。真正冲突的入口拒绝，不靠最后写入覆盖。两套正式加油站地块仍为64×72和A1的64×64；未放置或改动A1建筑。

## 7. 正式道路资产：Claude 接口

完整形状坐标和开发说明见[道路静态资产契约](road_surface_assets_v1.md)。以下 ID 已正式注册，不是计划将来删除的临时 ID；当前临时部分仅是简洁美术和复用材质。

所有 ID 前缀为 `apocalypse_firstlight:`。模型/贴图路径以下相对 `src/main/resources/assets/apocalypse_firstlight/`，`n=1..16`。

> 2026-10-08：下表四个方块已移除（用户要求），施工改用满格占位材料，见 [Road Surface Assets V1](road_surface_assets_v1.md) 开头的说明。

| Registry ID | BlockState / 默认 | 模型路径 | 当前贴图 | VoxelShape |
|---|---|---|---|---|
| `road_asphalt_surface` | `layers=1..16` / 13 | `models/block/road_surfaces/road_asphalt_surface_<n>.json` | `textures/block/asphalt.png` | `[0,0,0]..[16,n,16]` |
| `road_sidewalk_surface` | `layers=1..16` / 16 | `models/block/road_surfaces/road_sidewalk_surface_<n>.json` | `textures/block/reinforced_concrete.png` | 同上 |
| `road_utility_surface` | `layers=1..16` / 16 | `models/block/road_surfaces/road_utility_surface_<n>.json` | `minecraft:textures/block/dirt.png` | 同上 |
| `road_curb` | `layers=1..16,facing,shape` / `16,north,straight` | `models/block/road_surfaces/road_curb_<shape>_<n>.json` | `textures/block/reinforced_concrete.png` | 完整低基座加4px窄凸唇；driveway为完整平顶 |

每个ID配有 `blockstates/<id>.json`、`models/item/<id>.json`，同名BlockItem及掉落表。当前复用贴图而未修改共享PNG；Claude以后可以新增专用道路贴图、更换模型贴图引用、增加LabPBR，而不是覆盖工业混凝土的共享材质。

**高度：** `blockY=floorDiv(topH16−1,16)`，`layers=floorMod(topH16−1,16)+1`。G整数时沥青放在Y=G−1、layers13，人行道/路缘顶layers16。Java形状与烘焙模型使用相同盒子，不允许仅降低视觉而仍留下整格碰撞。

**路缘：** facing指沥青侧；NORTH基准凸唇在z=12..16，宽4px。`b=max(0,n−3)`，基座整格0..b，凸唇b..n。straight为单条；inner为东/南两条并集；outer为东南4×4交集；driveway去掉凸唇、整格高n。E/S/W旋转90/180/270度。n≤3时本格无完整低基座，由施工器保证下层支撑，不能添加负Y越界模型。

直线按相邻沥青方向选facing；节点按正交邻接/对角关系选择内外角。状态由计划明确指定，不做邻居自动改形。后续美术不得改变车道边界、最高顶面和碰撞接口；如需要真正改变碰撞，必须另行升级契约，不能偷偷用模型突出到通行区。

工具：沥青/人行道/路缘使用普通镐、木镐即可，`requiresCorrectToolForDrops=true`，`mineable/pickaxe`，无needs_diamond；设施带铲加速、手挖可掉落，`mineable/shovel`。每块掉同名物品一份并保留状态。静态注册/标签/loot已审查，**Survival实机采掘仍待验收**。

## 8. 有界配置

可选配置：`config/apocalypse_firstlight/road_construction_v1.json`（游戏运行目录）。未提供时使用下表默认值；不自动生成文件。键使用下列camelCase，支持部分覆盖，未知键、非整数或越界值拒绝。准备时冻结配置，修改文件不改变已经批准的逐格快照。

| 字段 | 默认 | 作用 |
|---|---:|---|
| maxColumns | 20000 | 包含道路、肩部和守卫的总柱数 |
| maxEdits | 120000 | 单计划最多修改方块数 |
| maxGuards | 400000 | 快照守卫上限 |
| maxChunks | 256 | 计划区块上限，不代表强载数量 |
| maxCutDepth / maxFillHeight | 3 / 3 | 单柱最大削/填格数，硬上限3 |
| maxSegmentEdits | 50000 | 单节点/路段归属的写入预算 |
| maxNoiseDeviation | 3 | 实际地面与稀疏基础高度警报阈值 |
| maxGradeH16PerBlock | 1 | 每格纵坡量化上限，硬上限1 |
| shoulderWidth | 3 | 外侧衔接最大宽度 |
| supportDepth / clearanceHeight | 2 / 3 | 路基/净空检查 |
| columnsPerTick | 64 | 分阶段实际方块取样与修改计划生成预算 |
| maxChecksPerTick | 2048 | 执行时每tick快照复核预算 |
| maxEditsPerTick | 256 | 执行时每tick写入上限；一批不跨区块 |
| planLifetimeTicks | 12000 | 未开工计划有效期，按世界gameTime约10分钟 |

允许最大columns50000、edits240000、guards500000、chunks512；具体参数仍由构造器校验。整个维度台账最多4条记录，总编辑240000、总守卫1000000；记录不会为了腾空间静默删除。初测优先32格segment，再验证T；mixed范围较大可能触发默认预算。拒绝是正常结果，不必放大工程深度或坡度。开发需要更大样本时只能在上限内调整计数预算。

## 9. 命令、预检报告与确认

仅开发环境（非production）、主世界、Creative且OP权限2玩家可使用以下入口。普通V1-A命令继续只读。

```text
/afl roads preview <layout> <x> <z>
/afl roads prepare <layout> <x> <z> confirm_unbuilt
/afl roads status
/afl roads status "<plan_id>"
/afl roads build "<plan_id>" confirm
/afl roads pause "<plan_id>"
/afl roads resume "<plan_id>" confirm
/afl roads cancel_preview

/afl roads preview segment r12 32 north
/afl roads prepare segment r12 32 north confirm_unbuilt
/afl roads preview segment r12 32 north at <x> <z>
/afl roads prepare segment r12 32 north at <x> <z> confirm_unbuilt
/afl roads survey 256
/afl roads cancel_survey
```

layout为residential/commercial/industrial/mixed/t，x/z为候选中心。不要照抄尖括号。每次预检间隔5秒；每维度只允许一个准备任务。执行者需靠近区域，途中退出/切维度/失去权限会取消只读预检；已开工施工使用台账独立管理。

segment是独立小样本入口，支持r12/c14/i12、16～64长度、四个水平方向；无at时从玩家整数位置前方12格开始，at坐标为起点而不是街区中心。省略length时默认为32，例如`preview segment r12 north`。回显固定坐标命令可避免玩家移动造成候选漂移。紧凑末端复用正式断面、支撑和肩部，不改变旧路口契约；单路段施工遇玩家进入范围加2格即暂停。Survey仅检查32个R12/32格候选，半径128/256/512，默认256；完整实际预检通过与基础噪声估计分开，所有Survey结果都不能授权施工。

`preview` 是只读完整预检，没有可执行记录。`prepare ... confirm_unbuilt` 显式声明开发无人建设区域，仍重新检查真实世界，PREVIEW_READY才登记PREPARED。二者都不写方块。报告必须读完，再对**报告精确plan_id**执行build confirm。ID绑定来源布局、维度/世界种子、配置、快照与具体目标状态，不是全局模糊confirm。

报告位置：`afl_debug/roads/construction_<layout>.json`，包含segment后最多6个固定文件覆盖输出；Survey另写`road_survey.json`。包括plan_id、来源plan_id、版本、bounds、道路/节点/区块数、最大削填、cut/fill/预计修改量、issues、配置、纵坡、实际地块G及入口原点、二维来源与逐格before/after编辑表。PREVIEW_READY/REJECTED/UNKNOWN明确区分；report不能替代当前台账状态，can_confirm仅表示报告生成时已登记的可确认预检。construction_authorized=false表示仍需后续显式Build，不代表Prepare没有成功。

全部预检完成后，执行器再做完整guard复核，并在每批施工前复核相关区块、保护与支撑净空。现场发生修改、BE/流体出现或保护冲突时拒绝/失败；区块卸载暂停，不强载。

## 10. 持久化、失败与恢复

主世界数据：`<存档>/data/afl_road_construction_v1.dat`。记录所有者UUID、维度、plan/source/version、范围、创建/过期时间、方块状态palette、精确before/after、guard、游标、阶段、完成区块和原因。

`PREPARED → RUNNING（VALIDATING → WRITING）→ COMPLETED`；可进入PAUSED或FAILED。重载后RUNNING转PAUSED，不在加载区块或进入世界时自动续建。重复ID、重叠已记录区域和容量满拒绝；不能重新prepare一遍来覆盖半成品。

未写入的过期计划不能执行。已经开工的暂停工程可显式resume，重新核对全部before/after：仍是before的格继续，已经是批准after的格跳过，其他状态拒绝。损坏/未知版本的台账失败关闭，不重建空台账绕过保护。该机制避免重复削填，但不是事务级回滚；异常崩溃仍须依靠存档备份。

每维度最多一个RUNNING，Level修改只在服务端END tick发生；无工作线程写世界，无区块票据。写入按chunk、先清理再支撑再面层排序，失败保留已完成/未完成数量和原因，不伪报全部完成。

## 11. 用户最小实机验收

1. 新建开发测试世界并备份，Creative+OP，选择启动保护和Highway之外的无人建设、无树、干燥平缓地面。亲自移动使候选涉及区块加载。
2. 先`/afl roads preview segment r12 32 north`验证小样本，再用`/afl roads preview t <x> <z>`验证网络。若UNKNOWN/REJECTED检查具体原因，不强制施工；可用只读`/afl roads survey 256`寻找候选。
3. 阅读JSON的范围、削填、区块、纵坡和入口。使用同坐标prepare并带confirm_unbuilt，得到PREPARED的精确ID。
4. 备份完成后build该ID confirm，status观察VALIDATING/WRITING/COMPLETED。单路段先核对所选断面、3/16高差、设施带、端部支撑和行走碰撞；随后T样本分别核对三类道路、T节点及车辆入口，不把单路段通过扩展为网络验收。
5. **独立验收**缓坡削填、跨chunk接缝、陡坡/水域/结构的拒绝，不把平地成功代替这些结果。
6. 单独检查暂停/显式resume、退出重进、已完成重复执行拒绝、现场改动拒绝、Survival工具与掉落。
7. mixed/其他布局和转角/十字样本在预算允许的区域分别验收。记录plan_id及JSON，不把旧V1-A PLANNED当作实际施工通过。

## 12. 文件与后续边界

- 主施工核心：`worldgen/roads/construction/RoadConstructionConfig.java`、`RoadConstructionPlan.java`、`RoadConstructionPlanner.java`、`RoadConstructionProtection.java`、`RoadConstructionJobs.java`、`RoadConstructionMaterials.java`。
- 开发适配：`src/dev/java/com/antaurora/apofirstlight/dev/RoadConstructionCommand.java`，通过原`RoadPlanningCommand`挂接。
- 道路资产：`block/RoadSurfaceBlock.java`、`block/RoadCurbBlock.java`、正式注册/语言/工具标签/loot，以及`tools/generate-road-surface-assets.mjs`和静态模型。
- 未修改二维RoadPlanner算法、Highway路线、MacroGeography、生物群系、辐射、温度、旧Rural、建筑NBT、A1设计、地下油罐、枪械、机器或Mesh Runtime。

V1-C仍需正式世界级版本/profile冻结与迁移、完整第三方/玩家保护契约、自然候选调度、跨候选连通、正式chunk生命周期协调与性能验收。建筑地块内部整平、正式NBT接入、完善标线/过街美术、树木工程、桥隧另立任务；本轮不自动启动这些内容。

历史验证：V1-B 实现及 Segment / Survey 工具扩展各自已完成获授权的单次 `./gradlew.bat compileJava --offline`，结果通过。本次 Git 检查点未重新编译，也未执行 build/processResources/test/GameTest/runClient/runServer、世界生成、截图或性能测试；实机施工未通过验收。
