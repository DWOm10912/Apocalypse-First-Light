# 旧世界生成逻辑退役 V1（Terrain V2 实施第一步）

日期：2026-10-10。状态：**已实现，编译通过（compileJava + compileDevJava），未实机验证**。

用户 2026-10-10 验收 Terrain V2 r1 后定的顺序："第一步：旧世界生成逻辑退役"。停用旧高速自动生成与施工入口、旧地堡强制生成、强制地堡出生、出生点周围圆形平原、出生点大范围辐射安全缓冲；保留 Pure Mesh 核心、已验收的连续道路几何、有价值的 RouteGraph 接口、仍在用的开发工具、旧地堡可复用的方块和建筑资产；不删除整个 `src/dev/java`；正式森林住宅开局完成前保留可靠的自然陆地出生回退。

做法是**停用，不删除**：入口断开，代码和资产留着，回退时把下表的改动还原即可。旧世界里已经生成的东西不动（见文末）。

## 停用了什么

| 旧逻辑 | 原来的入口（2026-10-10 勘察） | 现在 |
|---|---|---|
| 高速自然生成与施工 | 唯一触发点是群系修改器 `data/apocalypse_firstlight/forge/biome_modifier/primary_highway.json`（`forge:add_features`，把 `primary_highway` feature 挂到每个主世界区块的 `top_layer_modification`）→ `PrimaryHighwayFeature` → `NaturalHighwayGenerationAdapter`（路线、地形采样、区块写入、清植被、匝道、海桥） | 该文件改为 `{"type": "forge:none"}`（Forge 的空修改器）：新区块不再放高速。feature 注册、configured / placed feature JSON 和全部代码保留（无引用的注册不会出错，回退只需改回这一个文件） |
| 地堡强制生成 | `world/bunker/BunkerWorldEvents`：`ServerStartedEvent` 时在 (0,0) 附近搜位置并放置 `structures/bunker.nbt`（强制加载约 384 个区块），再设地堡辐射锚点 | 去掉该类的 `@Mod.EventBusSubscriber`：新世界不生成地堡、不设地堡锚点；启动时的出生飞地诊断日志也停了 |
| 强制地堡出生 | `world/bunker/BunkerPlayerSpawnEvents`：首次登录传送进地堡、`setRespawnPosition(..., forced=true)`；地堡没生成就**断开玩家连接** | 去掉 `@Mod.EventBusSubscriber`：不传送、不设强制重生点、不会因为没有地堡踢人 |
| 出生点周围圆形平原 | `world/biome/MainNationBiomeRegionPlan.regionAt` 里 `StartupPlainsEnclave` 的核心 / 边缘区（半径约 176–240 的 Plains 圆） | 删掉这一分支，不再产生 `STARTUP_PLAINS`。另外 0–3 个"附加 Plains 区"位置不变（同种子同位置），其余主国陆地仍是 Fallout（群系重构要等 Phase 2 实机验收之后） |
| 出生点大范围辐射安全缓冲 | `radiation/RadiationManager.startupRadiationCap`：以 (0,0) 为中心，160 内压到 SAFE，向外到约 480±56 是 0.10–0.42，再 48 格过渡，共约 530 格 | `effectiveEnvironmentalField` 不再取这个上限（函数留着） |
| 出生飞地的野生动物规则 | `world/WildlifeSpawnPolicy`：飞地平原放行、飞地外缓冲禁止 | 删掉，交给原有的 `isNaturalZone` |
| 出生飞地的道路保护方块 | `worldgen/roads/construction/RoadConstructionProtection`：(0,0) ±336 的 HARD 保护 | 删掉（"已放置地堡"的保护保留，旧世界有地堡时仍生效） |

## 保留了什么

- **自然陆地出生回退**：没有地堡以后，出生点是原版的自然出生点：原版按气候点搜索，再在 11×11 区块内找能站的地面。勘察确认 (0,0) 周围约 2.5 km 内全是主岛陆地。Phase 2 加了按规划挑平原出生点的处理（`TerrainV2SpawnEvents`，见 [Phase 2](terrain_v2_phase2_generation_v1.md)），没有规划的世界仍走原版。
- ~~出生点的小安全泡~~：辐射系统本来就有的锚点保护（40 格内 SAFE，96 格渐变）当时保留；**同日在 [Terrain V2 Phase 2b](terrain_v2_phase2b_rivers_v1.md) 按用户要求取消**，自动环境辐射也整体停用（默认 OFF）。
- **地堡资产**：`structures/bunker.nbt`、里面用到的 AFL 方块（钢筋混凝土及台阶 / 楼梯、钢块、钢格栅、钢门、工业灯、线形灯、配电盘、工业储物柜）全部注册不变；`BunkerPlacementManager`（放置、坐标换算）、`BunkerSurfaceIntegration`、`BunkerPlacementHygiene`、`BunkerSavedData`、安全位置检查保留，以后做成普通结构时可以复用。开发工具 `BunkerExteriorAirMasker`、`/afl dev bunker status` 保留。
- **高速**：`HighwayRouteGraph` 及其纯几何类（`HighwayGeometry`、`OrthogonalHighwayPath`、`SatelliteHighwayRouting`、`SeaBridgeGeometry`、`HighwaySpatialClaimProvider` 等）、Pure Mesh 核心（`client/mesh/*`）、M1-A / M1-B 网格预览开发命令、只读的 `/afl highway_network` 诊断全部保留。注意：`src/dev/java/.../worldgen/highway/*` 和主代码同一个包，打包时会进 jar（`build.gradle` 只排除 `dev/**`），这次没动。
- **城市道路 V1-B**：本来就没有自然生成入口（只有开发命令），不受影响；它仍把 RouteGraph 的高速走廊当作 HARD 保护。
- `src/dev/java` 没有删除；开发命令都还能编译。`StartupRegressionProbe`（只在 `-Dafl.startupRegressionProbe` 时运行）会因为没有地堡而报失败，属预期。
- `MacroGeography` 的 `STARTUP_MAINLAND_RESERVE` 等拓扑常数没动（海陆拓扑冻结）；高速路线图里离原点 416 格的避让也没动（路线图冻结）。

## 改动文件

- `src/main/resources/data/apocalypse_firstlight/forge/biome_modifier/primary_highway.json`
- `src/main/java/com/antaurora/apofirstlight/world/bunker/BunkerWorldEvents.java`、`BunkerPlayerSpawnEvents.java`
- `src/main/java/com/antaurora/apofirstlight/world/biome/MainNationBiomeRegionPlan.java`
- `src/main/java/com/antaurora/apofirstlight/radiation/RadiationManager.java`
- `src/main/java/com/antaurora/apofirstlight/world/WildlifeSpawnPolicy.java`
- `src/main/java/com/antaurora/apofirstlight/worldgen/roads/construction/RoadConstructionProtection.java`
- `src/test/java/com/antaurora/apofirstlight/world/biome/MainNationBiomeRegionPlanTest.java`（断言改成 (0,0) 不再是 STARTUP_PLAINS）

## 对旧世界和测试的影响

- 已经生成的地堡方块、`BunkerSavedData`、地堡辐射锚点留在旧世界里。锚点已经初始化，不会自己挪回出生点。
- 已有玩家的强制重生点（SpawnForced）仍指向地堡，直到睡床或 `/spawnpoint` 改掉。
- 新旧规则混在同一个世界会有接缝：**验收用全新世界**。
- 没有出生平原以后，出生点附近是 Fallout 荒原（群系重构要等 Phase 2b 实机验收之后）。它当时带 HEAVY_FALLOUT 环境辐射；2026-10-10 Phase 2b 起自动环境辐射默认关闭，荒原只剩外观。
