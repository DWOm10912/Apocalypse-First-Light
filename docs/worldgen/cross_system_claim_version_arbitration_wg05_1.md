# WG-05.1 — Cross-System Claim Version Arbitration Repair V1

日期：2026-09-13。起始 HEAD：`6fa9c21`。状态：**修复与纯联合回归完成；未接入生产 worldgen；Phase 1 Gate 待重新审查**。
MODEL_TASK = ASTRA_HIGH 为用户指定任务标签，不是运行模型身份的验证结果。

## B-01 实际实现点

Java 根：`src/main/java/com/antaurora/apofirstlight/worldgen/`。

| class#method | 修复前事实 / 本轮处理 |
| --- | --- |
| `ClaimConflictResolver#resolveWinner` | 双方不同 generationVersion 直接 INVALID；移除跨 claim 版本相等条件，保留同 ID 全内容冲突防御。 |
| `ClaimSets#normalize` | 任意混合版本产生 VERSION_MISMATCH；移除此全局版本不变量，重复 ID 全变体隔离仍不变。 |
| `ClaimQueryResult#ClaimQueryResult` | normalization 的失败把 COMPLETE 降 UNKNOWN；继续保留，明确 COMPLETE 只表示覆盖完整性，不是版本资格。 |
| `InMemoryClaimProvider#InMemoryClaimProvider / query` | 快照按 scope 的单一版本过滤；明确为单 owner fixture，增加 owner 相等检查，不将它改为全局多系统 provider。 |
| `DeterministicClaimId#create` | ID 包含 owner、维度 registry/location、owner-local version、candidateKey；无需修改。 |
| `WorldgenProfile#WorldgenProfile / ProfileCompatibility#check` | 每 system 独立版本 map + 全 profile 精确冻结，本身正确，未改。 |
| `ProviderActivationGate#evaluate` | provider 版本分别与 map 对应项匹配；其 scope/覆盖/保护/UNKNOWN 规则正确，未改。 |
| `LimitedClaimIndex#upsert / ClaimIndexSnapshot#ClaimIndexSnapshot` | 每 entry 按 owner 校验冻结版本，本身允许多版本；未改逻辑，仅修正 snapshot 的过时 Javadoc。 |
| `ClaimIndexCodec#encode / decode / encodeProfile / decodeProfile` | 每 entry 和 system 独立存版本；schema 不变，新增多系统二进制往返测试。 |

## 最小职责拆分与调用入口

方案 A：新增 `profile/ClaimProfileCompatibility.java`，依赖方向为 profile → spatial/core，不给空间 primitive 添加 profile 依赖。

`check(Optional<WorldgenProfile>, SpatialClaim)` 返回独立的 COMPATIBLE / MISSING_OWNER_VERSION / VERSION_MISMATCH / PROFILE_UNAVAILABLE。
资格公式：

```text
profile.systemVersions[claim.owner] == claim.generationVersion
```

owner 缺失或版本错误产生 VERSION_MISMATCH failure；profile 缺失产生 MISSING_RESOURCE，不借用其他 owner 的同名版本、不升级 claim、不作为 warning 放行。

安全协调入口：

```java
var checked = ClaimProfileCompatibility.validateClaimsForProfile(activeProfile, candidateQuery);
var resolution = checked.resolveCandidates();
```

返回的 `ValidatedClaims` 构造器私有，只能通过上述校验获得。`query()` 可读取不可变的 compatible evidence、failures、completeness 和原 operationsUsed。任何不兼容成员使**整组** UNKNOWN；拒绝提前发布 accepted/rejected 决策，不裁决兼容前缀。缺 profile 的空列表也不能变成 known-empty。

`ClaimQueryResult` 先完成重复 ID normalization，再做资格过滤。这样旧版/错误 owner 的重复变体不会因被过滤而留下一个“看似合法”的同 ID。继承原始 UNKNOWN、budget failure 和已用 operation 数，绝不因版本匹配而升级完整性。有限资格校验本身是单独的纯扫描，不假装属于原 provider 的访问预算。

底层 `ClaimConflictResolver` 仍是公开空间数学 primitive；直接传 raw COMPLETE query **不构成 profile 安全校验**。后续协调调用必须使用上述入口。空间层只依据 dimension、XZ/optional Y、max margin、HARD/SOFT、priority、stable ID；相同 ID 不同全内容继续 INVALID。没有引入运行时注册、真实 provider 聚合器或自动激活流程。

调用方仍负责冻结 profile、world seed/resource provenance、所需 provider/区域覆盖的 preflight；这里不把版本兼容解释为真实世界安全。合成回归通过每个 owner 自己的 `InMemoryClaimProvider` 取得 query，经过 Gate，再合并保留 UNKNOWN/errors 的证据并调用校验入口。测试的聚合函数只在测试类中，不是生产 coordinator。

## 不变量、M-01 与审计边界

- 同 owner/ID/version/内容：幂等。相同 ID 任意 owner/version/内容冲突：全变体隔离并 UNKNOWN；index 拒绝覆盖、保留旧占地和 MISMATCH。
- stable ID 仍包含 owner-local version；相同 priority 的 Rural v3 / Highway v7 以及 Rural v3 / Radio v2 按原 lexical ID tie-break。
- priority 保持 PROTECTED 100 > INFRASTRUCTURE 70 > SITE 50，SOFT 10 不排除 HARD；ADR-02 对所有更高资格候选检查、不贪心回填。
- index 仍只是有限 accepted-site 镜像，始终 UNKNOWN；INFRASTRUCTURE corridor 仍拒绝持久化。测试里的 Highway v7 index entry 是合成有限 SITE，不是公路 corridor。
- M01_DEPENDENCY_CYCLE = UNCHANGED。既有路径：`core.ChunkWriteBounds → spatial.Bounds3i`，`spatial.ClaimQuery/ClaimQueryResult → core.WorldgenIdentity/GenerationFailure`；`spatial.ClaimIndexEntry/ClaimIndexSnapshot/LimitedClaimIndex/ClaimIndexCodec → profile.WorldgenProfile`，`profile.ProviderDescriptor/ProviderActivationGate → spatial`。新增 helper 只使用既有 profile→spatial/core 方向，未新增包级环边；未为非 blocker 搬类。
- M02_LEGACY_BASELINE = DEFERRED_TO_WG-06。未实现 plan digest/fixed sample，不调用现有生成器。
- H01_AUDIT_SCOPE_WORDING_CORRECTED = YES。本轮结论只覆盖任务开始→结束的文件快照；不声称整个 Phase 1 历史所有 NBT 都未变化。
- 历史 `worldgen_phase1_review_v1.md` 保持原审查快照及 FAIL_BLOCKED 结论，本说明不代替一次新的 Gate Review。

## 验证结果

最终执行（无客户端）：

```text
.\gradlew.bat compileJava terrainContractTest check projectionMathTest --offline --console=plain
BUILD SUCCESSFUL in 24s
```

| 验证 | 结果 |
| --- | --- |
| compileJava / check | PASS |
| WG-01 | 186 checks |
| WG-02 core / bounds | 87 / 2,134,735 checks |
| WG-03 structure / asset scan | 2,595 checks / 8 NBT，hash 无变化；游戏 QA 未执行 |
| WG-04 | 232,991 checks；12,000 shuffle；720 全排列；原 margin/Y/budget/重复/soft-hard/known-empty 回归通过 |
| WG-05 | 8,312 checks；2,000 lifecycle/provider shuffle；冻结/严格 codec 回归通过 |
| WG-05.1 B-01 | 36,103 checks；额外 12,000 multi-version shuffle；Gate→query→validation→arbitration 联合通过 |
| projectionMathTest | 9,078 cases |
| git diff review / --check | 通过；新文件单独纳入 review，未使用 git add |
| Rural / Highway production references | 0 / 0 |

B-01 专测还覆盖：双系统 v3/v7 裁决、相同 priority 异版本 tie-break、三个系统 priority、双方各自 stale version、缺 owner/profile、空 profile 空 query、UNKNOWN 带/不带 failure、重复 ID 三种冲突、多系统 index/profile 二进制 NBT 往返、错版本 codec 拒绝、镜像 UNKNOWN 不升级。

引用搜索覆盖 `src/dev/java/.../worldgen/rural`、main/dev 的 `worldgen/highway` 以及 `world/feature/PrimaryHighwayFeature.java`，检查新 framework 的 profile/spatial/core/terrain/structure 包与具体类名，均无生产引用。受保护目录和 PrimaryHighwayFeature 的 git diff 为空。

Java 17.0.19；编译仍有 ResourceLocation 等 API removal/deprecation、Gradle deprecation 和 headless terminal warnings，不是新的运行验收。没有启动客户端、GameTest、新世界或 TPS 测试；没有 commit/push。

## 任务内资源哈希

开始与结束使用同一枚举规则及 SHA-256：`src/main/resources/data` 下 319 文件（其中正式结构 NBT 13 个），另加 Rural 17 / Highway 30 个包内 Java 文件，共 **366 → 366**；新增/删除/内容改变均 **0**。PrimaryHighwayFeature 位于包外，另用 git diff 确认未修改。正式 metadata、worldgen JSON、biome modifier、StructureSet 均包含在 data 快照中。

`WG05_1_TASK_SCOPED_FORMAL_NBT_MODIFIED = NO`。下表为每个正式 NBT 的开始 SHA-256；结束逐文件比较均完全相同。路径根：`src/main/resources/data/apocalypse_firstlight/structures/`。

| 正式 NBT | 开始 = 结束 SHA-256 |
| --- | --- |
| `bunker.nbt` | `79f89948f8577c35cf1a9c86161353b9dbb402317dd1b5665fae553b070d1f9e` |
| `convenience_store_01.nbt` | `3524e648bd9f948169f548142c7ae361d7ba47e4d2c2285e2783ddd9f65a267a` |
| `gas_station_01.nbt` | `99248a66bffcf78eb71bc9f19a86196abdfba7f598341a0f71195aef071f7da8` |
| `office_midrise_01.nbt` | `9a1b8508412e849982fa1b6fc7d495702d2a949222607f75de329ab4490b592b` |
| `rural_barn_large_01.nbt` | `458aa1b10cc9796b44743a4ea25d492c7c4047ccf7c73ac2de911af2eff1e6bc` |
| `rural_farmhouse_01.nbt` | `ffbe6aa670b861a635b22fc3c3f720989d04d355a1b94a9521a518cc660a9d0d` |
| `rural_farmhouse_02.nbt` | `2c671ea0eb3a92eab3b49a899fa0643196b09d2aebc3654b6ce9693772bd6ded` |
| `rural_grain_silo_01.nbt` | `2c3ddcbebdbf9742706e8e88a91cec195df51e26c2fd7a0aa2bf00252bd72aa5` |
| `rural_house_small_01.nbt` | `c0d021494381e402806097ea1747287f677186cefee27ca1d6275bfc62a1d590` |
| `rural_house_small_02.nbt` | `76aafe4f43e9445dd504b43d01ae4bad1df2ba903e952d4f77f3a329f500d51b` |
| `rural_storage_small_01.nbt` | `70a022742682f72a92da8c09cc09f22565028bfa1ff843f9b6cffe5866f7498d` |
| `rural_water_tower_01.nbt` | `809fe5ed2db1daa5f96cb035a70a024bc0da7f2bc9ede27e0e20a4c4ea94be0e` |
| `suburban_house_01.nbt` | `22aba2e28241b82dfe86d31bdd8ff95c092c69b185655c70fb8237ea820e9bae` |

## 本轮变更文件

新增：
- `src/main/java/com/antaurora/apofirstlight/worldgen/profile/ClaimProfileCompatibility.java`
- `src/test/java/com/antaurora/apofirstlight/worldgen/profile/CrossSystemClaimVersionRegressionTest.java`
- `docs/worldgen/cross_system_claim_version_arbitration_wg05_1.md`

修改：
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/ClaimSets.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/ClaimConflictResolver.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/ClaimQueryResult.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/InMemoryClaimProvider.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/SpatialClaim.java`（Javadoc）
- `src/main/java/com/antaurora/apofirstlight/worldgen/spatial/ClaimIndexSnapshot.java`（Javadoc）
- `src/test/java/com/antaurora/apofirstlight/worldgen/spatial/ClaimContractTest.java`
- `src/test/java/com/antaurora/apofirstlight/worldgen/terrain/TerrainContractTest.java`（runner）
- `docs/worldgen/claim_index_profile_gate_wg05.md`
- `docs/worldgen/unified_worldgen_architecture_v1.md`

开始时已有 `.obsidian/workspace.json` 修改及未跟踪的 `docs/worldgen/worldgen_phase1_review_v1.md`；均保留，不纳入本轮变更。

## 交付结论

WG-05_1_CROSS_SYSTEM_VERSION_ARBITRATION_REPAIR = COMPLETE。
BEHAVIOR_CHANGE / WORLDGEN_BEHAVIOR_CHANGED = NO，指生产 worldgen；未启用的共享契约有上述明确修正。
PROFILE_COMPATIBILITY_HELPER = IMPLEMENTED；VERSION_SCOPE = OWNER_LOCAL；
CROSS_SYSTEM_VERSION_EQUALITY_REQUIRED = NO；SPATIAL_RESOLVER_VERSION_ROLE = NONE_AFTER_PROFILE_VALIDATION。

RECOMMENDED_NEXT_STEP = Re-run Phase 1 Gate Review focused on B-01 closure; do not begin WG-06 until Gate passes.

DOCUMENTATION:
DOCS UPDATED = YES
Updated:
- docs/worldgen/cross_system_claim_version_arbitration_wg05_1.md
- docs/worldgen/claim_index_profile_gate_wg05.md
- docs/worldgen/unified_worldgen_architecture_v1.md

