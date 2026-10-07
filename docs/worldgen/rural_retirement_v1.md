# 旧 Rural 退役与建筑资产清理 V1

日期：2026-10-06。范围：Minecraft 1.20.1 / Forge 47.4.22 / Java 17，`apocalypse_firstlight`。本页描述退役后的当前行为；旧 Rural 文档仅保留历史算法和审查依据，不再作为自然生成、资产目录或验收命令指南。

## 当前状态

- 旧 Rural 独立自然生成已经退出：不再注册 StructureType/PieceType，不再创建候选、规划旧农田和建筑，不依靠调整 spacing 或权重伪装关闭。
- 删除 `RuralNaturalWorldgen`、`RuralNaturalStructure`、`RuralNaturalGenerator`、`RuralNaturalPiece`、`RuralGenerator` 的旧入口/提交链以及专属目录、配方、选择和冲突适配实现。
- 删除 `data/apocalypse_firstlight/worldgen/structure/rural.json`、`worldgen/structure_set/rural.json`、`tags/worldgen/biome/has_structure/rural.json`。不再提供 `apocalypse_firstlight:rural` Structure 注册或 locate 目标。
- Highway 的独立 Feature、modifier、路线、工程、区块施工、缓存及 `HighwaySpatialClaimProvider` 不变。旧 Rural consumer 退役；不存在给旧 Rural 新候选发布或保留占地的生产入口。
- 新道路、小镇、城市及新建筑均未在本轮实现。未来 rural 仅作为场地类型、建筑用途或布局策略；新建筑采用新资产 ID，与小镇/城市共用地块、NBT 和入口契约。

## 删除的八栋旧建筑

下表每个 ID 对应两份已删除资源：`src/main/resources/data/apocalypse_firstlight/structures/<ID>.nbt` 与 `src/main/resources/data/apocalypse_firstlight/afl_worldgen/structures/<ID>.json`。

| 退役资产 ID | NBT | 专属 metadata |
| --- | --- | --- |
| rural_farmhouse_01 | 已删除 | 已删除 |
| rural_farmhouse_02 | 已删除 | 已删除 |
| rural_house_small_01 | 已删除 | 已删除 |
| rural_house_small_02 | 已删除 | 已删除 |
| rural_barn_large_01 | 已删除 | 已删除 |
| rural_storage_small_01 | 已删除 | 已删除 |
| rural_grain_silo_01 | 已删除 | 已删除 |
| rural_water_tower_01 | 已删除 | 已删除 |

专属配方 `src/main/resources/data/apocalypse_firstlight/afl_worldgen/rural/legacy_natural_v1.json` 删除；旧 `RuralStructureCatalog`、`RuralRecipe` 与 `RuralPlanningCore` 已删除，不再静态加载这些资源或选择旧建筑。未将旧建筑改名保留为正式池成员，未删除无关 NBT、方块或物品。

## 保留能力与非活跃算法

- `src/main/java/com/antaurora/apofirstlight/worldgen/structure/` 保留 `StructureDefinition`、loader、validator、`StructureNbtReader`、`StructureTransform`、`StructureSocket`、`VanillaBoundsAdapter` 及旧 authoring 字段适配。
- `terrain/`、`core/`、`spatial/`、`profile/` 的通用地形语义、边界、预算、claim、缓存/版本基础设施保留；不把尚未接入生产的 profile/SavedData 能力描述为已启用。
- `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/` 中 frontage/access、lot anchor、road network/layout/painter、terrain source/sampler/probe cache/adapter、foundation support 和农业局部规划/施工等可复用算法暂时保留；它们没有正式旧 Rural 生成调度者，也不会自行加载旧八建筑。
- `RuralStructurePool` 仅保留资产无关的 `Role`/`Definition` 输入类型，没有打包资产池、权重、recipe 或静态资源加载。
- `RuralFoundationSupport` 保留模板支撑扫描及旋转后的支撑检查；缓存上限改为独立的 128 项，不再由已删除目录静态初始化。资源 reload 仅清空缓存，不发起结构生成。
- `RuralFrontagePlanner` 的 `List<StructureSocket>` 输入由调用者明确提供，继续使用通用 socket/`StructureTransform`；不再从退役 catalog 取 metadata。`RuralScaleTier` 仅保留非活跃布局预设，删除旧加权档位选择和建筑组成要求。
- `MacroGeography.allowsRural` 保留在冻结的 MacroGeography 源码中，但没有旧 Rural 自然入口调用。它不是当前占地 provider，也不是新城市必须依赖的接口。

上述保留是为迁移算法，不是保留旧存档解码、旧建筑兼容或另一套活跃聚落系统。不为消除 Rural 类名做整包改名。

## 地堡保持独立

| 项目 | 当前实际路径 |
| --- | --- |
| 模板资源 ID | `apocalypse_firstlight:bunker`；这是 StructureTemplate ID，不是旧 Rural 的 StructureType/StructureSet |
| NBT | `src/main/resources/data/apocalypse_firstlight/structures/bunker.nbt` |
| 入口 | Forge 自动订阅 `BunkerWorldEvents.onServerStarted` → `BunkerPlacementManager.ensureGenerated(overworld)` |
| 放置 | Vanilla `StructureTemplateManager`、`StructureTemplate`、`StructurePlaceSettings`，独立选址/朝向/地表接合 |
| 持久化 | `BunkerSavedData`，ID=`apocalypse_firstlight_bunker` |
| 玩家出生 | `BunkerPlayerSpawnEvents`，登录时使用地堡出生锚点 |
| 辐射锚点 | `RadiationManager.ensureBunkerAnchor` |

源码及资源引用检查未发现地堡对旧 Rural 目录、注册或八建筑的依赖。地堡 NBT、事件、放置、出生、辐射及开发状态命令全部保留，预期自然放置行为不变；本轮没有启动客户端/服务器验证这一预期。

## 开发命令和测试

- 删除 `/afl rural plan`、`/afl rural generate [x y z]` 及对应 handler。
- 删除 `RuralLegacyRegressionTest` GameTest 和 `src/test/resources/worldgen/rural/legacy_regression_v1.tsv`，移除 `-Pwg06Capture` / `-Pwg06Verify` 专属运行配置。
- 删除 `ruralMetadataTest` Gradle task 及 `check` 对其依赖，删除 `RuralMetadataMigrationTest`、`RuralAssetScanTest`；`StructureContractTest` 只移除对旧八建筑扫描的调用。
- 通用合成 structure/socket/transform/NBT/legacy-authoring、Terrain/core/spatial 和 Highway 测试保留。合成 claim fixture 内名为 rural 的 owner 不是正式注册或自然候选。
- 保留 `/afl dev bunker status`、`mask_structure_exterior_air`、`schem_to_nbt`、独立 SettlementPrototype/terrain 诊断、作者工具和 Highway 命令；本轮不新增城市道路命令。

## 兼容与道路边界

本次 Early Alpha 清理明确不承诺旧开发存档中的 Rural StructureStart/Piece 解码或继续回放；旧世界也不会自动擦除已经落下的建筑。验收应使用新测试世界，不把旧开发存档兼容视为本轮目标。

不改 MacroGeography、岛屿/海岸、地形噪声、Highway 路线、MainNationBiomeRegionPlan、Fallout/Plains、辐射或温度。R12/C14/I12、1/16 道路高程表达、3/16 路缘高差、整数 NBT 地面锚点和完整地块/建筑主体分离契约保持；未来新聚落查询 Highway claim，不查询已不存在的旧 Rural 自然候选。

## 验证状态

本轮已且仅执行一次 `./gradlew.bat compileJava --offline`，使用项目 `.gradle-user` 缓存。结果为 **BLOCKED（离线依赖解析失败）**：该缓存缺少 Curios、JEI、Jade、WorldEdit、GeckoLib 和 TerraBlender 所需依赖，未进入 Java 源码编译，因此不能声明编译 PASS。未重新编译、下载依赖或修改依赖配置。静态引用检查未发现退役类/资产的悬空引用；未执行 processResources/build/check/test/GameTest/runClient/runServer、世界生成、截图或性能测试。

用户最小实机检查：新世界能启动并正常出生于地堡；新探索区域不再出现旧 Rural 建筑群；Highway 仍生成；旧 `/afl rural` 命令和旧 Rural locate 目标不再存在。静态引用检查和编译不能代替这四项运行验收。

## 本轮同步文档

以下路径相对项目 `docs/`，历史页的旧结论已明确失效，历史验证没有重写为当前通过：

- `worldgen/rural_retirement_v1.md`
- `worldgen/rural_generator_audit_v1.md`
- `worldgen/rural_structure_asset_scan_wg03.md`
- `worldgen/rural_legacy_regression_wg06.md`
- `worldgen/rural_metadata_recipe_migration_wg07.md`
- `worldgen/rural_road_framework_v1.md`
- `worldgen/rural_building_lot_planning_v2.md`
- `worldgen/rural_farmland_v2.md`
- `worldgen/rural_scale_tier_tuning_v1.md`
- `worldgen/highway_rural_spatial_conflict_v1.md`
- `worldgen/highway_generator_audit_v1.md`
- `worldgen/highway_v2_route_graph_phase1.md`
- `worldgen/highway_v2_diagonal_geometry_v1.md`
- `worldgen/highway_v2_strategic_branch_phase2a.md`
- `worldgen/highway_v2_satellite_routing_phase2b2.md`
- `worldgen/unified_worldgen_architecture_v1.md`
- `worldgen/worldgen_architecture_decisions_v1.md`
- `worldgen/claim_index_profile_gate_wg05.md`
- `worldgen/small_city_building_authoring_v1.md`
- `worldgen/terrain_v2_macro_geography_v1.md`
- `worldgen/startup_enclave_bunker_regression_fix_v1.md`
- `项目内容/01 - 设计/世界与环境/群系.md`
- `项目内容/02 - 制作清单.md`
- `项目内容/03 - 注意事项 重要内容.md`
