# Startup Enclave + Bunker Regression Fix V1

## 根因与实现

TerraBlender 的 `MixinMultiNoiseBiomeSource` 在 cancellable HEAD 中调用 `ParameterList.findValuePositional` 并提前返回。AFL 原本在另一个 MultiNoise HEAD 设置 `BiomeTraceContext`，再在 ParameterList 读取并清理；该入口可能被提前返回绕过，导致 `CONTEXT_MISSING`，不是 ecology shape 缺少参数。旧 BiomeSource→seed 弱表不能解决被绕过的 ThreadLocal 入口。

`ClimateParameterListMixin` 现于 TerraBlender `initializeForTerraBlender(registryAccess, regionType, seed)` RETURN 将 `StartupEcologyState(seed, plainsHolder, woodlandHolder)` 发布到该 ParameterList 实例的 volatile 字段，仅 OVERWORLD。回调中的 seed 在复现新世界中与 ServerLevel 世界 seed 相同。worker 只读该不可变状态，调用既有 `StartupPlainsEnclave.zoneAt`；不依赖 ThreadLocal、每 query 注册表遍历、全局当前 seed 或 synchronized map。相同实例若再次绑定不同 seed 明确抛错，避免静默污染。

状态由 ParameterList 生命周期管理，无全局引用，随世界 registry/generator 被回收。Nether 不绑定。原有外部 biome 与地下洞穴策略保留；surface band 的洞穴 biome 修正规则未改。生态尺寸与形状参数未改。

MultiNoise 保留候选表过滤，移除用于正确性的 trace set/read/clear 及 per-query INFO。aquifer doFill 的诊断需 `-Dafl.startupDiagnostics=true` 才输出。ScorchedAquiferContext 与 StartupSurfaceBiomeContext 仍是同步 doFill/buildSurface 调用内部作用域，未观察到跨异步 continuation 读取；本轮未改水体规则，未宣称完成全部水体实机回归。

## 地堡与出生

- 严格搜索仍为半径 32–160、步长16、每环8点，最多72次；原 biome、水域、坡度、入口支撑、地下液体/空洞、高度检查全部保留。
- 严格耗尽后 `BUNKER_STARTUP_FALLBACK`：半径32–256、步长32、每环8点，最多64次额外候选；由近至远，只放宽 startup biome gate。成功 WARN `STARTUP_BIOME_FALLBACK_USED` 包含 seed、位置、surfaceY、biome、primary attempts 和拒绝统计。仍保留 ecology audit ERROR，不以地堡成功替代 ecology PASS。
- 候选不再要求其 footprint 已被玩家加载。仅缺失的候选 footprint chunk 请求 FULL；每次 ensureGenerated 主/备搜索共最多384次额外 chunk 请求，不设置永久 forceload。依赖区块生成的实际耗时/扩展仍需性能观察。
- ServerStarted（spawn preparation 后）在服务器线程同步完成地堡生成，早于首次正常登录；移除20 tick延后放置路径。成功写原 SavedData：origin、rotation、surfaceY、generated、placementVersion，并绑定辐射锚点。
- 首次玩家登录仍用原首登标记、安全落点检测和 teleport/respawn 路径。若最终无地堡，ERROR 并断开该次首次登录，避免把地表出生当正常开局；不会无限 deferred。已完成首登的玩家不重复传送。
- 再次 ensureGenerated 读取 generated 后直接返回，不重复放置；完整退出重进实测与玩家首登验收待启动器验证。

## 构建标识与实际环境

启动标识：`[AFL BUILD] version=1.0.0 fix=startup-parameterlist-context-v1`。

实际启动器 gameDir 已从日志确认：`D:/Games/MC TEST/.minecraft/versions/1.20.1-Forge_47.4.22`。修改前 mods 中仅发现 `apocalypse_firstlight-1.0.0.jar`（4125696 bytes，2026-09-09 00:52:46）；不是检查 run/ 后推断正式环境。

已 `build --offline` 并部署至上述独立目录；构建与部署 jar SHA-256 均为 `B29C42CAD7BB8D50D63ED52924FF93D0FF18240EC8F4A06A0FB36B69390C0AE4`。旧 jar 备份：`C:/Users/willi/AppData/Local/Temp/afl-startup-backup-20862634-a9d3-4c9c-9620-8751d27a7471/`。现有存档不做 biome 重写，验收必须新世界。

已另执行 `clean build -I src/dev/startup-clean-build.init.gradle --offline`，31秒成功，输出隔离到 `build/startup-clean-build`，避免 clean 删除普通 build/ 中的测试存档。干净产物 SHA-256：`58AB3EF6035F629E9B9EB59127D2BAE5BF119CB2F924AA9DA5CD7590192CD68B`；与部署 jar 比较1694个条目，仅 `META-INF/MANIFEST.MF` 不同，所有代码及资源逐项相同。

## 验证

| 环境 / seed | Ecology | Bunker | 备注 |
|---|---|---|---|
| 独立正常地形 dev server / -262568241377452353 | PASS | PASS | 六核心点+300,0林地；32次并发中心查询；第2候选成功，无fallback |
| random1 / -6246601325580816384 | PASS | PASS | 第1候选成功，无fallback |
| random2 / 7941719070376591360 | PASS | PASS | 第18候选成功，无fallback |
| random3 / 7064436257945374720 | PASS | PASS | 第1候选成功，无fallback |
| PCL packaged jar / Oculus + Complementary ON / -2368253589143695049 | PASS | PASS | 01:15:25 六核心audit PASS，第11候选成功；01:15:27 首登传送至(-9,66,53)，用户确认正常 |
| PCL packaged jar / 2145963260012717079 | PASS | PASS | 01:17:30 六核心audit PASS，第1候选成功；首登传送至(27,69,-9)，用户确认正常；该次 Shader OFF 状态未由日志单独确认 |
| 同存档退出重进 | 进行中 | 进行中 | 自动测试覆盖持久化 roundtrip、幂等 ensure 和安全落点存在，正常服务器重启补测见 restart.log |

日志 `build/startup-regression-repro.log`；fixture `src/dev/StartupRegressionProbe` 实际位于 `src/dev/java/com/antaurora/apofirstlight/dev/StartupRegressionProbe.java`，由 `src/dev/startup-regression-server.init.gradle` 启动真实 normal-world server，不是 flat GameTest。复现日志可见 Rural natural generation 仍执行，但未完成全部聚落重复/选址及 aquifer A/B 验收。

边界：开发环境验证用真实 normal-world dedicated server，而非 runClient；启动器 packaged jar 已有2个随机新世界通过，不能把另3个 dev server随机seed算作3个启动器seed。fallback在正常样本中未触发，未完成强制故障注入验收。完整水体/聚落回归、明确 Shader OFF 对照和第三个启动器随机seed仍未独立验收。
