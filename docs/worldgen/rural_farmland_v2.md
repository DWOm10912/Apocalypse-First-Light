# Rural Farmland V2 / Agricultural Lot V1

状态：新自然生成计划已接入，`compileJava --offline --console=plain` 成功。无资源修改，未运行 processResources。未运行客户端、GameTest、多 seed worldgen、性能测试或视觉测试；自然接受率、入口通行、围栏跨 chunk 外观和保存重进由用户验收。

## 入口和职责

`RuralNaturalGenerator.plan` 在建筑与 access 接纳后调用 `RuralFarmlandPlanner.plan`，沿现有 `RuralRoadNetwork` / `RuralFrontagePlanner.frontages` 搜索农业地块。只消费已有道路和建筑占地，不改变它们的生成逻辑。

源码均位于 `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/`：

- `RuralAgriculturalLot`：保存所属 road segment、frontage、朝路方向、gate、lot bounds、种植行方向、variant、pattern seed、含高度的三格宽 access；田埂、维护环和作物区从保存边界按固定 V1 规则内缩得到，`occupiedBounds()` 包含完整地块与 access。
- `RuralFarmlandPlanner`：决定尺寸、位置、高度、状态、围栏缺口、湿灌溉格与维护路径。
- `RuralFarmlandVariant`：三种视觉状态和独立 64-bit mixer。
- `RuralFarmlandPainter`：读取计划，按当前 chunk 范围准备地面和写入方块。
- `RuralFarmPlot`：追加可空的 Agricultural Lot 数据，兼容旧构造和旧列表接口。
- `RuralNaturalPiece`：在每个 FarmPlots 条目保存/读取 `AgriculturalLotV1`。
- `RuralGenerator.generateNaturalChunk`：有新字段时调用新 painter，否则保持 Legacy preparation/replay。

`RuralFarmPlanner` 和开发命令 `/afl rural` 的旧农田路径保留。V2 的验收入口是新自然生成区，开发命令不是 V2 预览。

## Frontage、尺寸与数量

按 FARM_TRACK > SIDE 排序；V1 不向 MAIN 直接挂农田。每个当前 tier 都有 FARM_TRACK 或 SIDE。field 长边朝道路外侧延伸，种植行沿该长边。outer lot 前缘距所属道路完整包络外沿三格，gate 位于朝路一侧田埂中点；三格宽直线 access 接到所属道路包络外沿，不能穿入道路内部或其他道路。

全部尺寸单位为 block，表示完整外包络（含过渡/田埂/维护条带）：

| 档位 | 可选尺寸 |
| --- | --- |
| Isolated | 10×14、12×16 |
| Farmstead | 上述 Small，加 14×18、16×22 |
| Cluster / Full | 上述尺寸，加 18×24、20×28 |

每个 frontage 按 seed 决定首选尺寸，失败后尝试其他可用尺寸。保持现有 farmPlotTarget/minFarms：Isolated 为 1，Farmstead 为 1–2，Cluster/Full 为 1–3；没有修改 tier 权重、building target、reservation、spacing/separation 或 biome。目标数不保证全部落地；低于既有 minFarms 仍拒绝整个候选。

## 外围、入口与作物

从外向内依次是：一格土/草过渡环 → 一格实体田埂 → 一格维护环 → 作物区。田埂混合 coarse_dirt 65%、dirt 28%、gravel 7%，不用混凝土或石砖。Fence/gate 底下始终是实体土质田埂，不是 farmland。

使用已有 vanilla oak fence、oak fence gate。四角使用两格高 stripped oak log，gate 朝所属道路。损坏只决定围栏摆放，未修改 Fence class、blockstate、模型或连接实现。每第三个围栏位置和 gate 邻接位置强制保留，控制连续缺口；连接状态按完整保存计划计算，包括角柱/gate，不读取相邻 chunk 的围栏状态。

维护环依状态部分铺 dirt_path，缺失处变为 dirt/coarse_dirt/grass；入口外侧和内侧两格必留路径。作物区每五列留一列灌溉沟，其他列形成沿长边的种植行。四种作物 wheat/carrot/potato/beetroot 逐地块稳定选取；每个种植格成熟度从 EARLY/MID/LATE/MATURE 四档选择，不满铺成熟作物。空行、缺苗、裸 farmland、退化土和杂草共同形成废弃状态。

| Variant | 权重 | 非保护围栏保留率 | 非空行作物概率 | 整行空缺概率 | 沟渠湿段概率 | 维护环铺路概率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ABANDONED_FIELD | 55 | 82% | 55% | 15% | 45% | 55% |
| SURVIVING_FIELD | 25 | 97% | 85% | 5% | 95% | 90% |
| OVERGROWN_FIELD | 20 | 70% | 15% | 35% | 15% | 25% |

强制保留位置使实际围栏完整度高于表中概率。作物概率不含沟渠、维护条带及田埂；ABANDONED 的空行和缺苗合计约 53%（不含沟渠）。OVERGROWN 的退化格更偏 grass_block，可有单格草；仍保留行和边界痕迹。SMALL_GARDEN 未实现。

灌溉湿段在 plan 阶段按三格长度分组决定，湿格列表存档；湿段使用 vanilla water，干涸段使用 coarse_dirt。复用原版水/作物机制，不新增流体或专用流体模拟。自然随机 tick 后的作物生长、缺水退化与水行为仍按原版执行。

## 占地和地形

使用既有 `RuralAccessPlanner.inside/intersects` 进行 XZ 占地判断。完整过渡环/田埂/维护环/作物区及 access 均不能超 reservation；field 避开所有道路完整包络，access 只允许终点接触自己的道路外沿。与建筑留三格保护边界、与建筑 access 和已有农业地块/access 留一格；不修改 Building / Lot V2，也不让农田覆盖已接纳建筑的通道。Highway 继续由前置 reservation conflict gate 处理。

仍使用现有整次规划 terrain cache（256 个独立采样上限）及 farm 候选上限（32 次进入地形验证的候选）。先进行几何/占地过滤，再以田块九点和 gate 检查地形，沿用 relief ≤3、cut/fill ≤2；接入通道每格检查无水且偏差 ≤1，田面与道路连接地面高差 ≤1。没有扩大任何 site 地形/水/坡度阈值或预算。

painter 以保存 target Y 为准，只在当前 chunk 内检查实际地面并做最多两格填挖，地块之外不加清理边缘。稀疏 plan 采样不能保证每个格子：实落地遇水、过大高差或 block entity 的柱会跳过，并记录 `[FARM_V2_TERRAIN_SKIP]` debug 日志；不会靠加大填挖范围强行落地。实际局部缺口和接受率需实机验收。

## 确定性、保存和兼容

图案 key 混入 world seed、Rural X/Z、frontage、方向、尺寸及接纳序号，再使用 SplitMix64 finalizer。逐格图案进一步包含保存的 variant、patternSeed、局部位置和用途 salt；没有全局随机流或 Math.random。相同 plan 的布局与图案不依赖探索顺序。

Piece 保存 sourceRoad、frontage、gate、bounds、rowDirection、variant、patternSeed 和带目标高度的 access；现有 FarmPlots 字段继续保存作物、围栏、湿灌溉格及维护路径。chunk replay 不重新选地块、gate、variant 或尺寸。Fence 连接和作物图案由完整计划确定；实际地面保护检查只读取当前 chunk 的对应柱。未通过实机跨 chunk/存档验证，编译不等于视觉验收。

没有 `AgriculturalLotV1` 的旧 FarmPlots 条目仍加载为 Legacy，继续旧代码回放。已生成旧 Rural 不重建，不修复此前缺建筑/缺农田的区域。

## 范围与验证

已运行 compileJava，成功；现有弃用/unchecked 警告仍在。无 resource JSON/NBT/metadata 修改，未运行 processResources；未改 Fence 方块资产、道路框架、Highway、建筑 V2、变体选择器、foundation support、结构池、tier/spacing/biome、怪物、City/Small Town；未新增建筑。未 runClient、clean、GameTest、大量测试、性能 benchmark、commit/push。

建议用户在新自然生成区检查：三种状态、gate 与道路接入、田埂上的围栏/角柱、作物行和缺苗、农田与建筑 access 无穿插；保存重进并从不同方向加载跨 chunk 农田。旧区域仍显示 Legacy 属于兼容行为。
