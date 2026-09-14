# WG-06 Rural Legacy Regression + Natural/Dev Core Adapter V1

日期：2026-09-13。状态：已实现基线与有限共享核心；**Natural Rural 输出未改变**。本页描述 WG-06 完成时的源码和测试，不代表客户端视觉、既有存档或所有 seed 的验收。Phase 1 Gate 为 `PASS_WITH_NON_BLOCKING_FINDINGS`；WG-07 尚未执行。

## 1. Goal

在任何 Rural V2 参数/池修改之前，把旧 Natural planner 的成功和拒绝结果冻结，并将 Natural 与 `/afl rural` 已可共享的六资产 catalog 和各自 legacy selection policy 放在 `RuralPlanningCore`。不启用 Phase 1 Claim/Index/Gate，不接 Highway。

## 2. Legacy Baseline Definition

固定输入是 seed、候选中心 `(x,0,z)`、确定性地形 fixture 类型和可选缺失模板。`RuralLegacyRegressionTest` 在**修改 `RuralNaturalGenerator` 之前**调用其原有 planner，读取六份正式 NBT 的真实 `size`，用测试地形模拟 Noise 的 `RuralTerrainSource`；随后冻结 24 行 SHA-256 到 `src/test/resources/worldgen/rural/legacy_regression_v1.tsv`。捕获时原 planner 的 SHA-256 为 `073e846ccc6283212096519173476279a4d86503cea79b57b9902a4d8457a535`。fixture 从不由正常测试自动更新，后续不能因回归而改期望值。

这是一套**planner 结果**基线，不是单纯成功/失败计数，也不声称等同真实固定 seed 世界的 biome/noise 采样、StructureSet 接受率或模板放置视觉。真实 NBT 尺寸及 legacy pool 成员参与测试；模拟模板仅需要尺寸，实际 NBT 方块/BE 的四向实机 QA 仍未完成。

## 3. Fixture Coverage

共 24 案例：Isolated 5、Farmstead 5、Cluster 9、Full 5；12 个接受、12 个拒绝。包含 flat/gentle/rough/water/invalid/mixed 地形、四向 lot rotation、Cluster 支路有和无、主路、田地、住宅、Barn、农业 utility、缺 Barn 模板和 site/lot 拒绝。每个固定案例运行两次并比较摘要；拒绝原因字符串也被锁定。测试另外验证 ID、origin、rotation、道路和农田 bounds 的改动会变更摘要，列表乱序不会。

已知覆盖缺口：这组固定案例没有接受的 Farmstead，也没有 Landmark。当前 Farmstead 的 Barn 候选仍由 legacy 40 格支路定位，而 Farmstead 的 reservation 仅 72 格，测试中该档四个有效地形案例均以 `tier requires a barn` 拒绝；这不是 WG-06 修复对象。Landmark 候选位于候选序列后部，已捕获的成功案例先达到建筑目标。没有独立稳定的 farm-minimum 拒绝样本、非零 retry slot 样本或真实 NoiseColumn 固定 seed 回放；不得把这些未覆盖项写成通过。它们是 WG-07/后续 QA 的补充建议，不以更改 legacy 算法换取测试通过。

## 4. Canonical Snapshot / Digest

`src/dev/java/com/antaurora/apofirstlight/dev/RuralLegacyRegressionTest.java` 是**dev-only、发布 jar 排除**的投影和 GameTest runner。投影使用 UTF-8、字段名+字符长度+值、明确的 `WG06_LEGACY_NATURAL_V1` schema 标识，再计算 SHA-256。投影包括 mode、seed、center、tier、reservation、主路/支路方向与 bounds/宽度/跨度、lot role/ID/origin/rotation/roadFacing/bounds/baseY/地形分类与 cut-fill、farm index/owner/bounds/shape/baseY/crop/irrigationType，以及逐项排序的 cell、fence、gate、灌溉、path、surface 高度，另含有效性和精确拒绝原因。lots、branches、farms 与农田子集合明确排序，不依赖 HashMap/HashSet、集合 `toString()`、对象身份、日志时间或绝对文件路径。

旧 `RuralPlan` 不保存候选 retry slot、farm orientation、farm fallback 标记和正式 rejection stage；投影明确记 `NOT_AVAILABLE_IN_LEGACY_PLAN`，不捏造这些数据。`failureReason` 记录实际旧文案。投影不包含模板 palette/BE 或世界落块结果；摘要变化敏感性只覆盖上述字段。

## 5. Natural/Dev Adapter Boundary

`RuralNaturalGenerator` 和 `RuralGenerator` 保持各自生命周期和 terrain source：前者读生成期 noise/只构造计划，随后 `RuralNaturalPiece` 逐 chunk 回放；后者读 `ServerLevel`、执行旧命令预检和完整写入。两者现在都调用 `RuralPlanningCore.catalog`，从相同六资产池按原顺序取模板。Natural 模式允许未选模板缺失并在实际需要时失败；Dev 模式在第一个缺失模板立刻失败，保留旧语义。

选择策略在同一核心类中**明确分开**：`LEGACY_NATURAL_V1` 继续按 role 筛选并用 seed/center/index 取模，不进行加权抽取或 maxCount 计数；旧 FLEX 候选的 `weight > 0` 资格过滤仍保留。`LEGACY_DEV_V1` 继续执行旧 maxCount 可用列表与 RandomSource 加权抽取。`RuralLayoutPlanner`、`RuralFarmPlanner`、`RuralPlan` 等原有共享组件不变。共享 core 仅覆盖 catalog 与选择政策，**不是**把 Natural/Dev 的地形、候选拒绝、提交或完整 planner 强行统一。Dev-only `NATURAL_PARITY` 命令本轮未添加；现有 `/afl rural` 语法和行为保留。

## 6. Exact Behavior Preserved

`structure_set/rural.json` 保持 spacing=40、separation=20、salt=1374512467；四档尺度/目标数量、13 点 site 与 9 点 lot 采样、各阈值/重试预算、路/driveway/田地/地基、rotation、NBT anchor、biome tag 和 Piece 回放均未改。原 Natural planner 的 24 个冻结摘要在核心抽取前后完全一致。没有改变世界生成 JSON、正式 NBT、Highway 或生产世界的实际 Claim 调用链。

## 7. Known Legacy Oddities Preserved

Natural 仍不按 weight 加权（FLEX 的 `weight > 0` 过滤除外），也不严格执行 maxCount；Barn/farmhouse 预排不保证最终接受。Farmstead 被现有布局/预留尺寸限制的现象未顺手修复。Dev 路径仍使用 128×128、自己的采样/选择/预检；其结果不能冒充 Natural 的同 seed 同坐标 parity。Natural 的随机数调用顺序保持；没有将 WG-03 metadata、socket 或 Phase 1 profile gate 加到生产入口。

## 8. New Variants Excluded

旧池严格为 `rural_barn_large_01`、`rural_farmhouse_01`、`rural_grain_silo_01`、`rural_house_small_01`、`rural_storage_small_01`、`rural_water_tower_01`。`rural_farmhouse_02`、`rural_house_small_02` 虽有正式 NBT，但仍未入 Natural/Dev legacy pool；GameTest 断言六 ID 集合精确相等。

## 9. Automated Regression Results

- 重构前 GameTest 基线捕获及原实现回放：24/24；重构后 GameTest：24/24，摘要逐项相同。覆盖断言验证四档、四旋转、Cluster 双支路状态、核心角色和六资产池；投影变更/排序不变性检查通过。
- `gradlew.bat compileJava terrainContractTest check projectionMathTest --offline --console=plain`：通过。`terrainContractTest` 包含 WG-01～05.1 的现有纯契约回归；`check` 和 projection 通过。
- `gradlew.bat runGameTestServer -Pwg06Verify --offline --console=plain`：只启用 `wg06_rural_baseline`，1 个必需 GameTest 通过。这是无图形服务端测试，不是客户端/in-world 人工验收。
- WG-06 起止 SHA-256 审计：`src/main/resources/data/apocalypse_firstlight/structures/*.nbt`、`worldgen/**/*`、`tags/worldgen/**/*` 共 44 文件的任务开始/结束哈希逐一相同。五份 Forge biome modifier 与 `data/minecraft/worldgen/structure_set/strongholds.json` 任务开始时工作区干净、结束时 `git diff` 为零，结束哈希已另行检查；这六份没有单独保存开始时 SHA-256，不能将其写成“起止双哈希比对”。总体正式资源无本轮修改。该结论**只针对本任务开始到结束**，不主张历史上 NBT 从未变化。
- M-01 包依赖：`UNCHANGED`；新核心留在 `worldgen.rural`，未向 Phase 1 core/spatial/profile 增添反向依赖。

<a id="manual-test-guide"></a>

## 10. Manual Test Guide

人工检查尚未执行。先关闭任何占用同一构建输出的开发客户端，在项目根目录运行 `gradlew.bat runClient --offline`；这是开发客户端，不是发布 jar。新建**可丢弃**的创造模式测试世界，seed 固定为数值 `62091306`，启用作弊，避免用旧世界判断新生成区域。

Natural：进入允许 Rural 的生物群系（当前 tag：plains、irradiated_woodland、fallout_barrens），在游戏输入 `/locate structure apocalypse_firstlight:rural`；若找到，点击返回的坐标或用 `/tp @s <x> <y> <z>` 到附近，等待区块加载，从上方和四侧查看。若单次 locate 无结果，换同 seed 的远处未生成区域/新世界；不要把一次候选失败当成全局失败。

Dev：站在不与 Natural 站点重叠的空旷测试区，先输入 `/afl rural plan` 读取站点/lot/田地诊断；需要实际提交时，在**可丢弃的新测试世界**输入 `/afl rural generate`，或明确坐标 `/afl rural generate <x> <y> <z>`。这是开发命令路径，不是 Natural parity，也不会自动回滚部分写入。本轮未添加 `NATURAL_PARITY` 子命令。

人工只需确认能生成且不崩溃，道路/建筑/农田未明显消失，朝向正常，旧 dev 命令仍可用。WG-06 之后 Rural 应与以前基本一样；明显变大、变多或建筑池变更都应视为回归。人工结果待用户验证，不能用上述自动测试替代。

## 11. WG-07 Readiness

固定计划基线与有限 core 抽取可供 WG-07 做 metadata/socket/recipe 的 legacy 映射；但实际 terrain/noise、四向 BE/视觉和未覆盖的 Farmstead/Landmark/farm-failure/retry 案例仍需后续独立验证。WG-06 不启用 metadata 正式加载、SpatialClaim、索引或 Gate。**Rural V2 changes intentionally deferred until after WG-07.**
