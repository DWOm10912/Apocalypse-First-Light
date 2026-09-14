# WG-05 — Limited Claim Index / Profile Freeze / Activation Gate V1

日期：2026-09-13。状态：**Phase 1 纯基础实现，未注册、未启用、未接入任何现有 generator**。
前置 WG-01～04 已有无接入契约及纯测试；本轮不启动 WG-06。

## 范围与权威边界

采用 [Architecture V1](unified_worldgen_architecture_v1.md) / [ADR-09](worldgen_architecture_decisions_v1.md) 的 Hybrid Persistence C。
`LimitedClaimIndex` 只是有限 accepted-site 协调镜像，不是世界真相、空间分配器或先到先得注册表。
真实 StructureStart/Piece 核对、服务器线程生命周期适配、SavedData、旧世界扫描/迁移均未实现。
现有 Rural、Highway、City、worldgen JSON、biome modifier、StructureSet、正式 NBT/metadata 均不接入或修改。

Java 源码根：`src/main/java/com/antaurora/apofirstlight/worldgen/`。

| 文件 | 当前职责 |
| --- | --- |
| `spatial/ClaimStage.java` | CANDIDATE_RESERVED / ACCEPTED_PLAN / COMMITTED / PARTIAL |
| `spatial/ClaimIndexEntry.java` | 完整 SpatialClaim + stage + planDigest + authoritativeSourceId + source revision |
| `spatial/ClaimIndexVerification.java` | 纯值核对：CONFIRMED / UNVERIFIED / STALE / MISMATCH |
| `spatial/LimitedClaimIndex.java` | 服务器线程所有的有限镜像、摘要校验和生命周期推进，不声称 thread-safe |
| `spatial/ClaimIndexSnapshot.java` | worldSeed、冻结 profile、indexRevision、capacity、稳定 ID 排序的不可变条目 |
| `spatial/ClaimIndexCodec.java` | 内存 NBT schema 1；包括独立 profile 编解码，不执行文件 IO |
| `profile/WorldgenProfile.java` | profileId、schemaVersion=1、每 system 版本、resourceSnapshot |
| `profile/ProfileCompatibility.java` | 精确冻结比较；COMPATIBLE / INCOMPATIBLE / LEGACY |
| `profile/ProviderDescriptor.java` | 只声明契约，不注册 provider |
| `profile/ProviderActivationGate.java` | 纯 descriptor + scoped synthetic evidence 门控，无 provider 回调 |

## 有限索引规则

- V1 只持久表示 HARD 的 SITE、BUILDING、PROTECTED_SITE、CONNECTION；接受 ACCEPTED_PLAN、COMMITTED、PARTIAL。
- CANDIDATE_RESERVED、SOFT zoning intent、INFRASTRUCTURE corridor 默认拒绝。有限基础设施例外需要后续独立 policy；不能借有限条目保存每段无限公路。
- capacity 必须显式传入，范围 1～100,000。满额返回 BUDGET_EXCEEDED，不驱逐旧条目或 PARTIAL。
- `upsert(planProfile, entry)` 必须匹配整个冻结 profile，包含所有 systemVersions 与 resourceSnapshot；每条 claim 的 generationVersion 必须匹配 owner 对应版本。
- 同 ID 的 claim 全内容、digest、authoritative source 必须一致。不同内容/版本拒绝并将原记录标为 MISMATCH；保留原占地，绝不后写覆盖。
- 同计划、stage、revision 重复提交幂等。更旧 revision 不回退；同 revision 不同 stage 拒绝。新 revision 可推进 ACCEPTED→PARTIAL/COMMITTED、PARTIAL→COMMITTED；同 stage 的新 revision 更新核对基线。
- COMMITTED→PARTIAL 或新 revision 回到 ACCEPTED 属回退，拒绝；本轮无 release/delete/recovery 机制。
- 不同已接受 ID 可以空间重叠：镜像不重新进行 WG-04 候选裁决，也不因谁先写入决定获胜。
- indexRevision 仅计实际镜像状态变化，不是 stable ID 或裁决顺序；有效事件不同到达顺序的最终条目可相同，而此本地变更计数可以不同。
- revision 溢出在 map 写入前抛错，不能半次更新。

## 核对与查询

来源/索引任一缺失→UNVERIFIED；claim/digest/source 不一致→MISMATCH；同计划但 stage/revision 不一致→STALE；完整相等→CONFIRMED。
核对只比较调用方提供的不可变值，不读取真实 StructureStart，也不自动导入缺失条目。

快照按维度、XZ bounds 与原有 margin 查询，复用 WG-04 long-safe、max-not-sum 数学。无 Y 仍是整列；XZ 查询返回条目内的原 optional Y，不做三维筛选。
每访问一条记录计一次 operation，包括维度/范围未命中；到预算上限停止，附 BUDGET_EXCEEDED。
快照复制/排序在捕获时执行，不属于 worker 查询预算；查询不调用来源核对器。

**有限镜像的查询始终为 UNKNOWN，`isKnownEmpty()` 始终 false。** 即使所有索引条目已确认，仍不能证明未记录区域没有结构。
PARTIAL、STALE、MISMATCH、UNVERIFIED 的已知占地仍返回；不能因资料不足释放保护。
这不是 WG-04 `ClaimQueryResult`：独立结果类型保留镜像来源与 UNKNOWN 边界，不能被包装成权威 COMPLETE 候选集。
WG-05.1 后，不同 owner 的不同 system version 可以正常共存；权威候选须先逐条通过 `ClaimProfileCompatibility.validateClaimsForProfile`，再由返回的 `ValidatedClaims.resolveCandidates()` 裁决。兼容公式是 `profile.systemVersions[claim.owner] == claim.generationVersion`，不是比较两个 claim 的版本字符串。细节及 B-01 联合回归见 [WG-05.1 实现说明](cross_system_claim_version_arbitration_wg05_1.md)。
未来权威 provider 必须单独证明覆盖完整性；本轮没有将索引包装为“全世界 COMPLETE”查询。

## Profile freeze 与激活门

缺失的世界 profile 由 `Optional.empty()` 明确分类为 LEGACY；不能伪造 `profileId=LEGACY` 后激活。
损坏的已存在 profile 不是缺失，codec 严格拒绝。没有自动 newest fallback、资源重载替换、旧 Piece 重规划或当前存档 profile 写入。

Provider capability 仅 CLAIM_QUERY、DETERMINISTIC_CANDIDATES、PROTECTION_QUERY。
调用方显式提供非空 required-provider policy、冻结/请求 profile、activation world identity、区域及 evidence。
Evidence 包含 descriptor、world identity、覆盖区域与 WG-04 query result，均为不可变值。

READY 需要全部 required provider：

1. profile/system/resource 版本精确兼容，evidence seed/dimension/system 与激活范围一致；
2. deterministic、CLAIM_QUERY 和所需 capability 满足；
3. COMPLETE 契约保证及当前 COMPLETE 结果均满足，evidence 区域覆盖整个请求区域；
4. 保护 provider 明确承诺经审核的 corridor-compatible placement policy，不能用一次空查询替代；
5. 没有矛盾的同 ID evidence，返回 claims 不冒用其他 owner/dimension/version。

所有问题稳定排序保留；主状态优先级：LEGACY_UNSUPPORTED > INCOMPATIBLE > MISSING_PROVIDER > UNKNOWN > READY。
相同 descriptor/evidence 重复幂等，矛盾重复不使用 first/last-wins。
READY 仅表示**调用方所提供的范围、策略与契约通过纯检查**，不证明提供者声明真实、不授予写入权限、不启用全世界协调、不提供 Highway reroute。
未来 adapter 必须审核 required-provider 列表与保护兼容承诺；未支持的外部结构组合不得包装为安全。
门控不写死 City/Highway 互斥。City SOFT region 与 Highway 可通过契约门，Building footprint 冲突由后续 claim policy 处理。
确定性 Highway descriptor 可以通过门控而有限 persistent index 为空，无 corridor/segment SavedData。

## NBT schema 1

根字段：`schema` INT、`world_seed` LONG、`index_revision` LONG、`capacity` INT、`profile` COMPOUND、`entries` LIST。
Profile：`schema` INT、`profile_id` STRING、`resource_snapshot` STRING、`systems` LIST，每项 `id` / `version` STRING。
Entry：`id`、`owner`、`dimension`、`generation_version`、`type`、`strength`、`stage`、`plan_digest`、`source_id` STRING；
`bounds_xz` INT_ARRAY `[minX,minZ,maxXExclusive,maxZExclusive]`；可选 `y_range` INT_ARRAY `[minY,maxYExclusive]`；
`priority` / `margin` INT、`revision` LONG、`edges` STRING LIST。

除 optional Y 外不填默认值；未知 schema/field/enum、缺失关键字段、错误 tag 类型、重复 claim/system ID、反向/空 bounds 均拒绝。
最多 100,000 entries、128 systems、每条 1,024 edges、每段文本 4,096 Java 字符；未来文件 adapter 还需压缩/解压字节预算。
CompoundTag 本身已折叠的重复键无法追溯；entry/system 使用列表保存以便显式检测重复 ID。

所有 verification 状态不持久化。解码/显式 restore 后均为 UNVERIFIED，保留占地但不能沿用崩溃前 CONFIRMED。
标准 decode 使用公开 dimension key factory；纯测试重载注入仅创建 key 值的函数，避免 Forge registry bootstrap；返回错误维度 key 会被拒绝。
真正 SavedData adapter **DEFERRED**，无自动加载、`computeIfAbsent`、`setDirty`、Mod 初始化 hook 或存档写入。

## 验证与后续

测试：`src/test/java/com/antaurora/apofirstlight/worldgen/spatial/IndexProfileContractTest.java`。
接入现有 `TerrainContractTest` / `terrainContractTest`，不新增测试框架或修改 Gradle 配置。
执行 `gradlew.bat compileJava terrainContractTest check --offline`；WG-01～05 和 projectionMathTest 通过。
覆盖四组种子的 2,000 轮生命周期/激活证据乱序、快照并发只读、二进制 NBT 往返及坏输入拒绝。
构建日志中的 API/Gradle deprecation warnings 不等同运行验收。

未运行客户端、GameTest、新世界生成、真实来源重启恢复或 TPS 测试；没有实际 SavedData 可供验证。
下一步先重新执行聚焦 B-01 闭环的 Phase 1 Gate Review；Gate 通过前不开始 WG-06。WG-05.1 不替代 Gate 审查，也未实施 Rural baseline 或任何生产适配。
