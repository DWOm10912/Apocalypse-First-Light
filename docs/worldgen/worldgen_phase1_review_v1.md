# Unified Worldgen Framework — Phase 1 Gate Review V1

日期：2026-09-13。范围：WG-01～05、现行 Rural/Highway 入口和六份既有 worldgen 文档。**只读审查；本文是本轮唯一新增文件。** 结论基于当前 `6fa9c21` 工作树、源码及本轮重新执行的测试，不代表新世界、客户端或存档迁移验收。

## 1. Executive Summary

`PHASE_1_GATE = FAIL_BLOCKED`。核心值类型总体小而可复用，现行 Rural/Highway 尚未引用新框架；但 WG-04 要求被比较的 claim 使用**相同** `generationVersion`，WG-05 则允许并测试各 owner 各有版本。Rural 与 Highway 的正常跨系统候选因此不能用现有 `ClaimQueryResult` 发布为 COMPLETE，也不能用 `resolveWinner` 作有效裁决。这是 Phase 1 内部契约冲突，按本次 Gate 定义属于 BLOCKER；测试全绿不消除它。修正前可做 WG-06 的只读旧行为样本准备，但不应开始声称已通过 Gate 的 Natural/Dev 正式适配。

另有非阻塞的 package 依赖环、尚未建立的固定 Rural plan digest 样本，以及“Phase 1 期间所有正式 NBT 未变化”这一过宽历史断言。它们不证明当前生产 worldgen 行为已改变。

## 2. Phase 1 Inventory

| 工作包 | 当前实现和边界 | 主要证据 |
| --- | --- | --- |
| WG-01 | `terrain/`：来源、有效性、液体/保护知识、单柱样本与纯查询；无世界适配器。 | `terrain/TerrainSample.java:16-19,55-106`、`terrain/TerrainQuery.java:3-13` |
| WG-02 | `core/` 身份/结果/预算/写入许可；`spatial/` 半开 bounds。无真实 writer。 | `core/PlacementResult.java:10-40`、`core/ChunkWriteBounds.java:7-22`、`spatial/BoundsXZ.java:8-38` |
| WG-03 | `structure/` metadata/NBT/旋转/旧 authoring 机械检查；无注册或游戏 QA。 | `structure/StructureDefinition.java:10-29`、`structure/StructureNbtReader.java:56-130` |
| WG-04 | `spatial/` 有限 claim 原型、查询、排序和冲突裁决；无生产 provider。 | `spatial/ClaimConflictResolver.java:13-98`、`spatial/InMemoryClaimProvider.java:13-80` |
| WG-05 | `spatial/` 有限镜像/codec，`profile/` 版本和合成 evidence 门；无 SavedData 或真实来源验证。 | `spatial/LimitedClaimIndex.java:10-22`、`profile/ProviderActivationGate.java:9-18` |

静态引用搜索覆盖 `src/dev/java/.../worldgen/rural/`、`src/main/java/.../worldgen/highway/`、`src/dev/java/.../worldgen/highway/`、`world/feature/` 和 `registry/`：对 `terrain/core/spatial/structure/profile` 新契约的生产调用均为 **0**。不存在已启动的 `worldgen/city` generator。`git status --short` 在审查和测试后为空；`git diff 993a749..HEAD` 对 `worldgen/` JSON 和 Highway biome modifier 无差异。8 份 `rural_*.nbt` 本轮动态扫描的 SHA-256 与 `rural_structure_asset_scan_wg03.md:59-70` 完全一致；这只证明本次/该扫描快照未漂移，不应被延伸成 Phase 1 全期间所有正式 NBT 从未增添。

## 3. Dependency Graph

实际 Java import/类型依赖（箭头为“使用”）：

```text
terrain       (独立)
core          -> spatial.Bounds3i                         [ChunkWriteBounds]
spatial       -> core                                     [query/result/index]
structure     -> spatial.Bounds3i                         [NBT/transform/Vanilla adapter]
profile       -> core + spatial                           [gate/descriptor]
spatial index -> profile.WorldgenProfile                  [entry/snapshot/codec/index]
```

因此有 **`core ↔ spatial` 和 `spatial ↔ profile` 两个 package-level 环**，不是 Java 类初始化循环，也未观察到构建失败。`core/ChunkWriteBounds.java:4`、`spatial/ClaimQuery.java:3-4`、`profile/ProviderDescriptor.java:5`、`spatial/ClaimIndexSnapshot.java:5` 给出可复核边。`structure/` 未导入 Rural/Highway；上述 shared 包也没有导入 Rural/Highway/City 或 live `Level` 实例。`StructureTransform` 继承 `StructureTemplate` 的私有静态桥仅为读取受保护的 Vanilla size-based bounds API，不是将全部纯值类型塞入模板继承层。任务顺序不是源依赖链：WG-03/04 并列消费 WG-02 值，WG-05 的索引反向耦合 profile。

## 4. Contract Consistency

- Terrain：`TerrainSample.unknown/invalid` 保留请求 source、清空全部观测；`NONE` 在非 VALID 样本仅为规范占位，`hasKnownNoFluid()` 明确先检 validity。`NOISE_PRE_DECORATION` 不能声明保护已清；高度经 `Math.addExact(blockY,1)` 转为表面上方第一格。无 AUTO source、隐式实时 fallback 或 force-load（`TerrainSample.java:16-19,55-106`；`TerrainHeights.java:17-20`）。**PASS**。液体分类尚无真实 registry/tag 映射，不应宣传已识别工业废液。
- Bounds：`BoundsXZ`/`Bounds3i`/`YRange` 均半开，尺寸用 long；Vanilla inclusive max 用 `addExact` 转换，反向用 `subtractExact`；claim pair 用 `max(a.margin,b.margin)` 一次，XZ 比较使用 long。候选 query 的 margin-expanded area 由调用方负责，`expand` 对越界显式抛错，不静默缩边（`VanillaBoundsAdapter.java:9-24`；`ClaimConflictResolver.java:18-39`；`BoundsXZ.java:29-38`）。**PASS**；极端坐标的未来调用方须将不能表示的 envelope 视为拒绝/UNKNOWN。
- 结果/失败：`APPLIED_SLICE` 与 `COMPLETE` 分离；`PARTIAL_COMMIT` 要求实际变更和失败证据，WG-05 `PARTIAL` 是镜像的生命周期 stage，不能相互替代；`NOT_OWNED` 不等于 `FAILED`，`UNAVAILABLE` 不授权强载（`PlacementResult.java:13-37`；`WriteOutcome.java:3-6`；`ClaimStage.java:3-4`）。九个原因码足以给 WG-06 兼容适配作**分类**；当前还没有把真实 Rural 写入错误映射到它们。**PASS（纯契约）**。

## 5. Determinism Review

WG-04 的固定优先级加 ID 排序、候选排序后对**所有**更高资格 HARD envelope 的拒绝（不是贪心回填）与 ADR-02 一致；相同 ID 内容冲突隔离并降为 UNKNOWN。`DeterministicClaimId` 使用 owner/维度/版本/候选键转义拼接，不使用 `identityHashCode`、UUID、时钟或运行时随机；scope seed 在 provider/index 快照，而非 ID 本身（`ClaimSets.java:10-32`；`DeterministicClaimId.java:9-23`；`ClaimConflictResolver.java:68-98`）。12,000 次乱序、720 种排列及 2,000 次 WG-05 lifecycle/evidence 乱序的**各自单版本**结果一致。**排序/顺序无关性质 PASS；跨系统版本组合见 B-01，不能据此称多系统确定性裁决已可用。**

## 6. Structure/NBT Contract Review

`StructureDefinition` 无 footprint/height、weight/maxCount/zoning；几何从 NBT 完整 `size` 得来，包括显式 air/padding。`front` 只声明方向，socket 单列出入口空气格、边界及朝外规则；旧 `road_facing` 只生成缺 socket 警告，不发明入口（`StructureDefinition.java:15-20`；`StructureNbtReader.java:67-112`；`StructureDefinitionValidator.java:26-55`；`LegacyStructureAuthoringAdapter.java:64-73`）。`StructureTransform` 使用 `StructureTemplate.transform`、零 pivot 和 Vanilla bounding box；四旋转机械测试与非方形尺寸匹配。机械 `VALID_WITH_WARNINGS` 仍标出 BE/多格 **GAME_QA 未做**（`StructureTransform.java:13-51`；`StructureNbtReader.java:113-123`）。8 份 Rural NBT 动态只读扫描成功；其中只有六份进入当前 `RuralStructurePool`，两份 `_02` 的 front/anchor/socket 未定，不能悄悄加入 legacy pool。**纯资产契约 PASS；实机四向/BE QA 未验证。**

## 7. Spatial Claim Review

`SpatialClaim` 拒空 XZ/Y、无 Y 等于全列；SOFT 只报告 advisory overlap，不赢 HARD；连接边不豁免主体冲突。镜像允许不同 accepted ID 共存，不担任 first-writer-wins 分配器；UNKNOWN 的查询即使携带已知 claims，也不发布 accepted 候选（`SpatialClaim.java:10-30`；`ClaimConflictResolver.java:34-52,61-97`；`LimitedClaimIndex.java:8-12,63-86`）。这些局部性质通过。**跨 owner 的版本裁决 ISSUE（B-01）：同一 profile 合法的不同版本在 WG-04 被当作不可裁决。**

## 8. Index/Profile/Activation Review

有限索引只收 HARD 的 `ACCEPTED_PLAN/COMMITTED/PARTIAL` 站点类，拒绝 `CANDIDATE_RESERVED`、SOFT 与无限 INFRASTRUCTURE；PARTIAL、STALE、MISMATCH 占地仍保留。镜像 query 永远 UNKNOWN，缺索引不等于空地；codec 解码后只给 UNVERIFIED，不假装重启来源核对。Profile 必须精确匹配，缺失世界 profile 为 LEGACY；没有自动升级、SavedData、无限 Highway segment 持久化（`ClaimIndexEntry.java:8-31`；`ClaimIndexSnapshot.java:8-15,42-70`；`ClaimIndexCodec.java:14-19,64-101`；`ProfileCompatibility.java:15-26`）。

Gate 区分 LEGACY/INCOMPATIBLE/MISSING/UNKNOWN/READY，检查 seed/dimension/version、覆盖范围、COMPLETE 承诺和已给 evidence；未写死 City 与 Highway 互斥，也不制造 reroute。`READY` 只表示**调用方声明的合成证据通过纯检查**，不能证明保护 provider 的 corridor-compatible 承诺真实、更不能激活生产 worldgen（`ProviderActivationGate.java:9-15,38-94`）。WG-05 本身的镜像/版本冻结/门控契约 **PASS**；但它与 WG-04 版本不变量组合后失效（B-01）。

## 9. Over-Abstraction Review

`OVER_ABSTRACTION_FINDINGS = NONE`。只有 `TerrainQuery`、`ClaimQuery` 两个有实际查询语义的 shared 接口；未见空 City/POI 包、万能 generator 基类、全局单例注册表或 giant manager。`StructureDefinitionLoader`、`ClaimIndexCodec` 和激活门虽有多个类型，但分别承担严格解析、有限镜像、证据归一化的具体职责。纯契约没有 `Level` 字段或强制 world lookup。需要整理的是第 3 节的包方向，不是再增加一层通用框架。

## 10. Test Quality Review

本轮在无运行客户端状态下执行 `gradlew.bat compileJava terrainContractTest check --offline --console=plain`：**BUILD SUCCESSFUL**；`compileJava` 等编译任务为 UP-TO-DATE，`terrainContractTest`、`projectionMathTest` 实际执行。输出分别为 WG-01 186、WG-02 core 87 / bounds 2,134,735、WG-03 2,595 + 8 NBT 扫描、WG-04 232,990、WG-05 8,312、projection 9,078 个检查；数字不是风险覆盖率。Gradle 9 deprecation 警告非测试失败。

高价值：`BoundsContractTest.java:24-95` 小域集合 oracle + int 极值；`ClaimContractTest.java:329-396` 顺序/并发、冲突重复 ID、UNKNOWN、预算；`StructureContractTest.java:122-174,181-287` 真实 Vanilla 变换/错误 NBT；`IndexProfileContractTest.java:230-325` NBT 往返、错字段、乱序及冻结快照并发；`RuralAssetScanTest.java:14-57` 当前真实 NBT/hash。低边际价值：某些 enum 顺序/海量小域重复断言增加计数但不构成运行行为证据。关键缺口：**同一合法 profile 下不同 owner version 的 COMPLETE 合并/裁决测试**（目前 WG-04 反向测试锁定 INVALID）；真实 StructureStart/Piece 与索引崩溃核对、Rural 同 seed natural/dev plan digest、游戏内四旋转 BE/多格、跨 chunk 顺序及 Highway 连续性均未做。后几项属于 WG-06 后续或更晚阶段，不能仅凭纯测试替代。

## 11. WG-06 Readiness

`WG06_READY_APIS = WorldgenIdentity; TerrainSample/TerrainSource/TerrainQuery; GenerationFailure/PlacementResult/WriteOutcome; BoundsXZ/Bounds3i/VanillaBoundsAdapter; StructureDefinition/Socket/Transform; claim 值与优先级; WorldgenProfile/门控纯预检。` 它们可复用为适配的值边界，不能误称 Rural 现已使用。

`WG06_MISSING_PRIMITIVES = profile-aware 跨 owner 版本验证与候选聚合/裁决规则（B-01，Gate 前必须定清）；其余没有证据表明必须先新建 primitive。` 不应为 City/Highway 未来需求提前造类。

`REGRESSION_BASELINE_READINESS = PARTIAL`。当前可稳定取 seed、候选 chunk/center、tier、selected NBT ID、rotation、lot origin、road/farm plan 和失败原因：`RuralNaturalStructure.java:25-48`、`RuralPlan.java:122-149,205-209`，有效 plan 的道路/lot/farm 可从 `RuralNaturalPiece.java:114-170` 序列化。**BASELINE_GAP：尚无规范化 plan digest/API/固定 seed+候选样本集；拒绝候选无 StructurePiece 保存，必须在 WG-06 先记录其判拒样本。** `RuralNaturalPiece.java:40-57` 只序列化有效计划；`RuralNaturalGenerator.java:59-70,84-94` 含早期拒绝。WG-06 应先冻结旧自然选择（`RuralNaturalGenerator.java:380-428` 并不按 dev 的 weight/maxCount）、生物群系门、地形来源和六资产哈希，再比较 natural/dev；不要把新语义混入“零回归”。

## 12. Findings by Severity

| ID | 级别 | 证据和影响 | 建议（本轮不实施） |
| --- | --- | --- | --- |
| B-01 | **BLOCKER** | `ClaimConflictResolver.java:37` 不同 version 返回 INVALID；`ClaimSets.java:29-30` 混合版本将 COMPLETE 降 UNKNOWN；WG-05 `WorldgenProfile.java:7-19` / `ClaimIndexSnapshot.java:25-30` 支持各 owner 版本，`IndexProfileContractTest.java:24-25,181-187` 使用 `site-v1`/`route-v1`/`protect-v1` 并可 Gate READY。合法跨系统 claim 无法按现有共享裁决链判断。 | 明确“各 claim 与自己 owner 的冻结 profile 版本匹配”与“同 ID 不可版本漂移”的不同规则；让聚合/裁决接受同 profile 的不同 owner 版本，并添加联合回归。不得靠强行给所有系统同一版本号或把 UNKNOWN 当无冲突绕过。 |
| H-01 | **HIGH（资源断言/审计边界）** | `git diff 993a749..HEAD --name-status -- src/main/resources/data/apocalypse_firstlight/structures` 显示新增 `gas_station_01.nbt` 与两份 `rural_*_02.nbt`；`5de40c7`（WG-01/02 合并提交）内含 gas station NBT，`9d8ecad` 独立新增两份 Rural 变体。故“Phase 1 期间**全部**正式 NBT 未改”按提交历史为假；当前 Rural 六资产池和 Highway 生成入口无这些新 ID 引用。 | 不回滚用户资产；将行为边界精确限定为已引用六份 Rural 模板及 Highway/worldgen JSON，固定 baseline commit+SHA 与池列表，确认新增资源何时正式启用。 |
| M-01 | **MEDIUM** | `core/ChunkWriteBounds.java:4`→spatial，而 `spatial/ClaimQuery.java:3-4`→core；`profile/ProviderDescriptor.java:5`→spatial，而 `spatial/ClaimIndexSnapshot.java:5`→profile。包层出现双环，但无 generator 反向导入且编译通过。 | 以后在有真实适配使用点时厘清 bounds/索引/profile 所属层；避免仅为美观做大规模搬包。 |
| M-02 | **MEDIUM** | `RuralPlan.java:122-149` 暴露计划要素，`RuralNaturalPiece.java:114-170` 存有效计划，却没有规范化 digest 或固定输入/拒绝样本；当前纯契约测试没有 natural/dev 同输入回归。 | WG-06 **先**建立带 seed、候选 chunk、biome、资源哈希和版本的 legacy golden baseline，再改适配；hash 不包含日志时间、对象身份或非决定性映射迭代序。 |
| I-01 | **INFO** | `ProviderActivationGate.java:9-15` 的 READY 是合成 evidence 预检；`RuralAssetScanTest.java:14-57` 是机械扫描，非 live activation、SavedData 或游戏 QA。 | 后续任务各自补运行时证据，审查报告不得越级表述。 |

## 13. Gate Decision

`PHASE_1_GATE = FAIL_BLOCKED`；`BLOCKERS = B-01`。此项属于题设明列的“shared core 存在语义冲突”，不是因 City 尚未实现或缺运行时适配而误判。`DETERMINISM_CONTRACT = PASS（单版本纯裁决）`，`BOUNDS_CONTRACT = PASS`，`TERRAIN_CONTRACT = PASS`，`STRUCTURE_CONTRACT = PASS（机械）`，`SPATIAL_CLAIM_CONTRACT = ISSUE（跨 owner 版本）`，`INDEX_PROFILE_CONTRACT = PASS（独立值/镜像/门控；与 WG-04 组合不通）`，`SHARED_CORE_DEPENDENCY_DIRECTION = ISSUE（非阻塞包环）`。生产引用 Rural=0、Highway=0；本次无 Java/NBT/正式资源写入、worldgen 行为变化=NO。H-01 是历史范围发现，不等于当前 Rural/Highway 已接入或突然改变生成分布。

## 14. Recommended Next Step

先单独解决 B-01 并增加使用**不同 owner version、同一冻结 profile**的跨 provider 反例/正例；复核 Gate 后再启动 WG-06 `Rural Legacy Regression Baseline + Natural/Dev Core Adapter`。在修正前只可准备只读 baseline，不接 Rural、Highway，也不更改现有选择/资源。M-01 可随真实适配小范围整理；M-02 正是 WG-06 的第一阶段，而非新 City primitive。

```text
WORLDGEN_PHASE1_REVIEW_V1 = COMPLETE
AUDIT_ONLY = YES
JAVA_MODIFIED = NO
NBT_MODIFIED = NO
WORLDGEN_BEHAVIOR_MODIFIED = NO
PHASE_1_GATE = FAIL_BLOCKED
BLOCKERS = B-01 cross-owner version arbitration mismatch
HIGH_FINDINGS = H-01 broad formal-NBT-unchanged claim contradicted by commit history
MEDIUM_FINDINGS = M-01 package dependency cycles; M-02 legacy digest/fixture baseline gap
LOW_FINDINGS = NONE
SHARED_CORE_DEPENDENCY_DIRECTION = ISSUE
DETERMINISM_CONTRACT = PASS (single-version scope only)
BOUNDS_CONTRACT = PASS
TERRAIN_CONTRACT = PASS
STRUCTURE_CONTRACT = PASS (mechanical only)
SPATIAL_CLAIM_CONTRACT = ISSUE
INDEX_PROFILE_CONTRACT = PASS (isolated; cross-WG composition blocked)
PRODUCTION_GENERATOR_REFERENCES = RURAL: 0; HIGHWAY: 0
OVER_ABSTRACTION_FINDINGS = NONE
WG06_READY_APIS = identity, terrain, result, bounds, structure, claim values, profile/gate pure contracts
WG06_MISSING_PRIMITIVES = profile-aware cross-owner version aggregation/arbitration rule
REGRESSION_BASELINE_READINESS = PARTIAL
REVIEW_REPORT = docs/worldgen/worldgen_phase1_review_v1.md
RECOMMENDED_NEXT_STEP = fix B-01 and re-review Gate; then WG-06 Rural Legacy Regression Baseline + Natural/Dev Core Adapter
```
