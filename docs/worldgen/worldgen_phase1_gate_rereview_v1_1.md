# Unified Worldgen Framework — Phase 1 Gate Re-Review V1.1

日期：2026-09-13。范围：WG-01～05.1 的**当前工作树**纯契约和测试；起始 HEAD `6fa9c21`，其中 WG-05.1 实现仍为未提交改动。本轮仅新增本审查文档，没有修改 Java、正式 NBT 或 worldgen 资源。旧 [Phase 1 Review V1](worldgen_phase1_review_v1.md) 是当时的 FAIL_BLOCKED 快照，不被回写；WG-05.1 修复说明见 [实现记录](cross_system_claim_version_arbitration_wg05_1.md)。本次 Gate 不是客户端、GameTest、新世界、生产协调激活或真实 provider 覆盖验收。

## 1. Executive Summary

`PHASE_1_GATE = PASS_WITH_NON_BLOCKING_FINDINGS`。旧 B-01 所依据的两处全局版本相等检查已在实际源码中移除；现在按 owner 查询冻结 profile 的期望版本，只有通过校验的候选才能通过 WG-05.1 安全入口发布空间裁决。Rural v3 / Highway v7 / Protected v2 的合成候选能在同一 profile 下按固定优先级裁决；错版本、缺 owner、缺 profile、重复 ID 与 UNKNOWN 都不被当作可接受的完整候选集。现有生产 Rural/Highway 均未调用这些新契约，故本次纯契约改动没有改变正式 worldgen 行为。

仍保留 M-01 包级依赖环、M-02 Rural 固定 legacy plan baseline 缺口，以及 H-01 先前过宽的历史 NBT 断言。它们在本 Gate 范围内均非 blocker。WG-06 可以开始，但必须先建立 legacy 回归基线并在适配时保持版本校验、provider 覆盖与 UNKNOWN 边界；本轮没有启动 WG-06。

## 2. B-01 Closure

旧问题的两个具体实现点：`spatial/ClaimConflictResolver.java` 原先在 `resolveWinner` 中比较 `a.generationVersion()` 和 `b.generationVersion()`；`spatial/ClaimSets.java` 原先在 `normalize` 中拒绝一个快照内的混合版本。当前 `resolveWinner`（第 35–49 行）只有同 ID 的全内容一致性检查、维度、XZ/optional Y、配对最大 margin、HARD/SOFT 和优先级/ID 裁决，没有跨 claim 版本相等条件；`ClaimSets.normalize`（第 10–30 行）仍稳定排序与隔离错误重复 ID，但不检查全局混合版本。`ClaimQueryResult` 因 failure 将 COMPLETE 降为 UNKNOWN，且 COMPLETE 本身只代表查询覆盖，不代表版本合格（第 9–29 行）。

`profile/ClaimProfileCompatibility.java:26–37` 直接读取 `profile.systemVersions().get(claim.owner())` 并与该 claim 的 `generationVersion()` 比较。不存在 owner 时返回 `MISSING_OWNER_VERSION`，不同版本返回 `VERSION_MISMATCH`，缺 profile 返回 `PROFILE_UNAVAILABLE`；这些情况都不自动升级或转成 warning。`validateClaimsForProfile`（第 58–69 行）先接收已规范化的查询结果，再逐条校验；任一不兼容会给整个返回查询附 failure、保持 UNKNOWN。其私有构造的 `ValidatedClaims.resolveCandidates` 才调用空间 primitive（第 40–49 行）；UNKNOWN 的底层 resolver 不发布 accepted/rejected（`ClaimConflictResolver.java:80–95`）。原始查询的 UNKNOWN、failure 与 operation 计数不会因合格过滤而升级为 COMPLETE。

因此 `B01_STATUS = CLOSED`，适用范围是**未接入生产的 Phase 1 纯契约安全入口**。底层公开 `ClaimConflictResolver.resolveCandidates(rawQuery)` 仍是 profile 无关的空间 primitive，Javadoc 明示其调用前置条件；直接调用 raw COMPLETE query 不会自动检查 active profile。当前生产调用搜索只有 WG-05.1 helper 使用该 primitive，没有生产旁路。WG-06 适配必须通过冻结 profile preflight、完整 provider evidence、查询聚合后使用 `ValidatedClaims`，不能把底层 primitive 当成自带版本授权。这是后续接入约束，不是本轮新 blocker。

## 3. Cross-System Version Model

| 环节 | 实际语义与证据 |
| --- | --- |
| `SpatialClaim.generationVersion` | owner-local 值；`SpatialClaim.java` 不要求其他 owner 同版本。 |
| `WorldgenProfile.systemVersions` | `WorldgenProfile.java:7–19` 是排序、不可变的 `ResourceLocation → String` map，并保留资源快照。 |
| 裁决资格 | `ClaimProfileCompatibility.java:26–37,58–69`：`profile.systemVersions[claim.owner] == claim.generationVersion`；缺项或不等均拒绝。 |
| 空间裁决 | `ClaimConflictResolver.java:35–49,80–95` 只处理空间、strength、priority、stable ID；同 ID 全内容冲突仍 INVALID。 |
| 有限索引 | `ClaimIndexSnapshot.java:24–30`、`LimitedClaimIndex.java:48–56` 各条按 own owner 版本检查，允许不同 owner 合格版本并存。索引没有裁决权。 |
| Gate | `ProviderActivationGate.java:44–89` 对每个 required provider 独立核对冻结 map、descriptor、scope、返回 claim 与当前 query 完整性；没有“全 provider 同一字符串”规则。 |

`CROSS_WG_VERSION_MODEL = PASS`；`OWNER_LOCAL_VERSION = PASS`；`PROFILE_COMPATIBILITY_BEFORE_ARBITRATION = PASS`，特指上述安全入口。WG-05.1 测试中的 `rural_v3` SITE 50 与 `highway_v7` INFRASTRUCTURE 70 均通过 profile 校验，Highway 胜出；Rural `rural_v2` 在 profile 期望 v3 时使整个裁决 UNKNOWN。`InMemoryClaimProvider.java:28–42` 仍是单 owner 有限 fixture，不冒充生产级多系统 provider；合成 Gate→各 owner 查询→保留错误的聚合→验证→裁决链在 `CrossSystemClaimVersionRegressionTest.java:155–191` 中执行。生产聚合器/世界 provenance 适配尚未实现，留给后续阶段，不把测试局部 aggregate 当作已上线实现。

## 4. Determinism Regression

`ClaimSets.ORDER` 是 priority DESC、id ASC；`ClaimConflictResolver.resolveCandidates` 对每个候选检查**所有**排名更高的 HARD 竞争者，不因较高者自身被拒而贪心回填（`ClaimSets.java:10–11`、`ClaimConflictResolver.java:80–95`，对应 ADR-02）。`DeterministicClaimId.java:10–22` 使用 owner/维度/version/candidateKey 的可转义值拼接；不同 owner 生成不同 ID，version 不被删除。相同 ID 全内容相同幂等；内容、owner 或版本变化时 `ClaimSets.normalize` 隔离该 ID 的所有变体并令查询 UNKNOWN，pairwise 同 ID 返回 INVALID。`LimitedClaimIndex.upsert` 对同 ID 不同计划拒绝并保留旧条目/MISMATCH，无 last-write-wins。

实际 WG-05.1 回归覆盖 Rural v3/Highway v7 的 priority、Rural v3/Highway v7 及 Rural v3/Radio v2 同优先级 stable-ID tie-break、Protected v2 > Highway v7 > Rural v3、错版本与缺 owner/profile、三种重复 ID 变体、UNKNOWN 带/不带预算失败，以及 **12,000** 轮合法多版本输入乱序（`CrossSystemClaimVersionRegressionTest.java:71–135,200–235`）。WG-04 既有 **12,000** 轮乱序与 **720** 排列继续通过；WG-05 对 provider/lifecycle 的 **2,000** 轮乱序继续通过。测试既检查预期获胜 claim，也检查顺序变化不改变结果，而非只比较总检查数。`DETERMINISM_REGRESSION = PASS`；`DUPLICATE_ID_REGRESSION = PASS`；`MULTI_SYSTEM_ARBITRATION = PASS`（纯合成范围）。

## 5. Index/Profile Regression

`ClaimIndexEntry.java:18–29` 拒绝 CANDIDATE_RESERVED、SOFT 与 INFRASTRUCTURE corridor；仅有限 accepted HARD site 类入镜像。`LimitedClaimIndex` 保留 PARTIAL、拒绝生命周期倒退，同 ID 内容/摘要/来源冲突不会覆盖旧占地。`ClaimIndexSnapshot.java:8–15,39–70` 的镜像查询始终 UNKNOWN，空索引、已确认条目或缺来源都不证明空地；restore 的 verification 变为 UNVERIFIED。`ClaimIndexCodec.java:26–44,48–103` 保留 profile 的逐 system 版本及每条 claim 的 owner-local `generation_version`，strict decode 不按最新版本替换。

`ProfileCompatibility.java:16–26` 精确比较冻结与请求的整个 profile/resourceSnapshot；缺世界 profile 明确为 LEGACY。WG-05.1 的多 owner 索引测试用实际二进制 NBT 往返验证 Rural/Radio/Camp/有限 Highway-owned SITE 的各自版本原样保留，并验证错版本 NBT、同 ID 跨 owner 冲突拒绝及镜像 UNKNOWN（`CrossSystemClaimVersionRegressionTest.java:193–216`）。此处 Highway-owned SITE 是合成有限条目，不是持久化无限公路。`INDEX_PROFILE_REGRESSION = PASS`。

## 6. Activation Gate Regression

`ProviderActivationGate.evaluate` 对每个 required provider 独立匹配 requested profile 中的系统版本；检查 descriptor/identity 的 seed、dimension、system、resourceSnapshot、capabilities、COMPLETE 保证与 query、覆盖范围和 protection policy（第 38–94 行）。有 UNKNOWN、缺 provider、错版本或 LEGACY 时不会 READY；重复冲突 evidence 按固定严重度报告。WG-05.1 合成 Rural v3、Highway v7、Protected v2 可 READY，Rural v2 为 INCOMPATIBLE，缺 Highway 为 INCOMPATIBLE/MISSING_PROVIDER，Highway UNKNOWN 不可 READY（`CrossSystemClaimVersionRegressionTest.java:155–191`）。WG-05 既有 City SOFT + Highway、保护承诺和 provider 顺序回归仍通过。READY 仅代表提供的合成证据通过纯检查，不证明真实 provider 或世界保护覆盖。`PROVIDER_ACTIVATION_GATE = PASS`。

## 7. Remaining Findings

| ID | 状态 | 当前证据与处置 |
| --- | --- | --- |
| M-01 | MEDIUM / NON_BLOCKING | 包级 `core ↔ spatial`、`spatial ↔ profile` 依赖仍在：`core/ChunkWriteBounds.java` 用 spatial.Bounds3i，spatial 查询/结果用 core；spatial index 用 profile.WorldgenProfile，profile Gate/descriptor 用 spatial。新 helper 只增加既有 profile→spatial/core 方向。代码可编译、无共享 core→Rural/Highway 反向导入，未发现循环初始化；不为整理包而在本轮重构。 |
| M-02 | MEDIUM / EXPECTED_WG06_WORK | `RuralNaturalPiece.java:114–170` 已保存有效计划部分字段，但没有固定 seed/候选/拒绝样本集与规范化 plan digest。WG-06 必须在变更 natural/dev 适配前建立 legacy baseline；这正是 WG-06 的第一部分，不阻止启动 WG-06。 |
| H-01 | 历史审计措辞 / NON_BLOCKING | 旧 V1 报告中“整个 Phase 1 正式 NBT 未变化”被 commit 历史反例推翻。WG-05.1 报告只声明其**任务开始→任务结束** 319 个 data 文件（含 13 个正式结构 NBT）及 47 个 Rural/Highway 包内 Java 文件的 SHA-256 逐文件一致。本次重新计算当前 13 个正式 NBT，与该任务结束的 13 条记录完全一致；不据此声称 Phase 1 历史未添加资产。后续任务继续使用各自开始/结束哈希。 |

`M01_STATUS = NON_BLOCKING`；`M02_STATUS = EXPECTED_WG06_WORK`；`H01_STATUS = NON_BLOCKING`；`TASK_SCOPED_HASH_AUDIT = ACCEPTABLE`。另有一般接入前置条件：底层 raw resolver 不内嵌 profile 校验，Gate 不证明真实数据来源，镜像仍无 SavedData/权威核对；都未被误报为已完成的运行机制，也不是 Phase 1 纯契约 Gate 的 blocker。

## 8. Production Boundary Check

重新在 `src/dev/java/.../worldgen/rural`、`src/dev/java/.../worldgen/highway`、`src/main/java/.../worldgen/highway` 与 `src/main/java/.../world/feature/PrimaryHighwayFeature.java` 搜索对新 `worldgen.terrain/core/spatial/structure/profile` 的 imports/FQCN 及 `ClaimProfileCompatibility`、`ClaimConflictResolver`、`ProviderActivationGate`、`LimitedClaimIndex`、`WorldgenProfile` 的具体引用：**Rural 0、Highway 0**。全 main/dev Java 的新 helper / resolver 调用搜索只找到共享 primitive 内部和 profile helper，没有生产 generator 调用。`git diff --name-only` 对上述 Rural/Highway 目录、PrimaryHighwayFeature 和 `src/main/resources/data` 均为空。

WG-05.1 的 Java 改动只发生在尚未被生产路径引用的纯框架包。因此 `WORLDGEN_BEHAVIOR_MODIFIED = NO` 指当前生产世界生成路径未变化，不宣称未来启用协调 profile 后分布也不会变。没有运行新世界、客户端或 GameTest；当前 Gate 也不是这些运行验证的替代品。

## 9. Test Results

本轮重新执行（Java 17.0.19，离线、未启动客户端）：

```text
.\gradlew.bat compileJava terrainContractTest check projectionMathTest --offline --console=plain
BUILD SUCCESSFUL in 10s
```

| 项目 | 实际结果 |
| --- | --- |
| compileJava / compileTestJava | PASS，任务 UP-TO-DATE；沿用当前工作树已生成的类，本轮未改 Java |
| WG-01 terrain | PASS，186 checks |
| WG-02 core / bounds | PASS，87 / 2,134,735 checks |
| WG-03 structure / Rural asset scan | PASS，2,595 checks / 8 NBT；机械 QA，游戏 QA 未执行 |
| WG-04 spatial | PASS，232,991 checks；12,000 shuffle、720 permutations |
| WG-05 index/profile/gate | PASS，8,312 checks；2,000 lifecycle/provider shuffles |
| WG-05.1 B-01 | PASS，36,103 checks；12,000 multi-version shuffles、binary NBT round-trip |
| `check` | PASS；`test` UP-TO-DATE |
| `projectionMathTest` | PASS，9,078 cases |

检查数量不是覆盖率。Gradle deprecation 与 headless terminal warning 未导致失败。未进行 GameTest、真实多系统 provider 覆盖、客户端/新世界或 TPS 验证；这些不能由纯测试推断。

## 10. Final Gate Decision

`B01_STATUS = CLOSED`；`CROSS_SYSTEM_VERSION_MODEL = PASS`；`OWNER_LOCAL_VERSION = PASS`；`PROFILE_COMPATIBILITY_BEFORE_ARBITRATION = PASS`；`MULTI_SYSTEM_ARBITRATION = PASS`；未找到 WG-05.1 引入的新 Phase 1 blocker。保留三个明确非阻塞 finding，故 `PHASE_1_GATE = PASS_WITH_NON_BLOCKING_FINDINGS`，不是要求“零 finding”的 PASS，也不是因尚未开始 WG-06 而 FAIL_BLOCKED。`BLOCKERS = NONE`。

## 11. WG-06 Readiness

`WG06_READINESS = READY`，仅指可**开始** WG-06 的 legacy 回归基线与 Natural/Dev Core Adapter 工作，不代表迁移已经完成或生产协调可启用。WG-06 应先冻结原有 seed、候选 chunk、tier、biome、六份当前 Rural pool NBT 哈希、有效与拒绝计划的结果，形成稳定 plan digest；然后在适配时把冻结 profile、所需 provider 的 COMPLETE/UNKNOWN、owner-local 校验和 `ValidatedClaims` 作为明确调用链，并保持现有自然选择语义。现有索引与 Gate 仍是纯契约；真实来源核对、保护覆盖与实机 QA 需在相应后续阶段验证。本报告只推荐下一任务，不执行 WG-06。

```text
WORLDGEN_PHASE1_GATE_REREVIEW_V1_1 = COMPLETE
AUDIT_ONLY = YES
JAVA_MODIFIED = NO
NBT_MODIFIED = NO
WORLDGEN_BEHAVIOR_MODIFIED = NO
B01_STATUS = CLOSED
CROSS_SYSTEM_VERSION_MODEL = PASS
OWNER_LOCAL_VERSION = PASS
PROFILE_COMPATIBILITY_BEFORE_ARBITRATION = PASS
MULTI_SYSTEM_ARBITRATION = PASS
DETERMINISM_REGRESSION = PASS
DUPLICATE_ID_REGRESSION = PASS
INDEX_PROFILE_REGRESSION = PASS
PROVIDER_ACTIVATION_GATE = PASS
M01_STATUS = NON_BLOCKING
M02_STATUS = EXPECTED_WG06_WORK
H01_STATUS = NON_BLOCKING
PRODUCTION_GENERATOR_REFERENCES = RURAL: 0; HIGHWAY: 0
COMPILE = PASS
UNIT_TESTS = PASS
CHECK = PASS
PROJECTION_CHECKS = PASS
PHASE_1_GATE = PASS_WITH_NON_BLOCKING_FINDINGS
BLOCKERS = NONE
NON_BLOCKING_FINDINGS = M-01 package cycles; M-02 legacy baseline gap; H-01 historical NBT wording corrected to task scope
WG06_READINESS = READY
REVIEW_REPORT = docs/worldgen/worldgen_phase1_gate_rereview_v1_1.md
RECOMMENDED_NEXT_STEP = WG-06 Rural Legacy Regression Baseline + Natural/Dev Core Adapter
```

本轮只新增本报告。Architecture / ADR / WG-05.1 实现及 V1 历史审查文档均未改动。
