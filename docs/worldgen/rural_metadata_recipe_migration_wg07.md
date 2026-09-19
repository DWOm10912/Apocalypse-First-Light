# WG-07 Rural Metadata / Socket / Legacy Recipe Migration V1

后续现行行为：见 [Rural Road Framework V1 Core](rural_road_framework_v1.md) 与 [Building / Lot Planning V2](rural_building_lot_planning_v2.md)。自然八资产（原六资产加 `rural_farmhouse_02`、`rural_house_small_02`）按 frontage 放置并优先消费合法 metadata socket（缺失时 midpoint fallback）。Legacy 配方仍只有六资产，开发命令仍走旧六资产/midpoint 路径。本页以下 WG-07/07.1 的迁移状态、构建结果和验收结论均为历史记录，不代表当前自然池边界；两栋 `_02` 的自然生成实机与四向入口通行尚未验收。

日期：2026-09-13。WG-07.1 收尾状态：**八资产 metadata 已完成，Legacy 六资产配方不变**。两份 `_02` 的 front/anchor/socket 来自用户人工视觉 QA 确认，本轮完成机械验证；八资产四向游戏内摆放 QA 仍未执行。WG-07.1 不是 Rural V2。

## 1. Migration Scope

八资产的 NBT 引用、类别、SOUTH front、ground anchor、四向旋转与入口 socket 进入 `data/apocalypse_firstlight/afl_worldgen/structures/*.json`；只有旧六资产的有序池成员、role 和旧声明 weight/maxCount 进入 `data/apocalypse_firstlight/afl_worldgen/rural/legacy_natural_v1.json`。`RuralStructureCatalog` 在类加载时一次性读取八份打包 metadata，将配方旧六份与两份 `_02` 同角色变体映射为自然池 `Definition`；开发命令仍只读取旧六份。序列化 Piece 可解析八份定义。它**不是** datapack reload listener，也不支持热重载或数据包覆盖；正式 reload/snapshot 边界留待另一次任务。

Natural 仍由 `RuralPlanningCore.SelectionMode.LEGACY_NATURAL_V1` 按旧顺序筛选角色并取模，FLEX 保留 `weight > 0` 资格过滤，但不执行加权抽取和 maxCount。Dev `LEGACY_DEV_V1` 仍执行旧加权/maxCount。legacy driveway 仍使用 front midpoint，不消费新 socket。未迁移 tier、场地大小、road、terrain、farm、retry 和 candidate offset 参数；未启用 Spatial Claim 或 Highway 协调。

## 2. 8-Asset Metadata Inventory / Front / Anchor

尺寸来自实际压缩 NBT，单位为 X×Y×Z。旧六 front/anchor 与 WG-06 Java 值一致；`_02` 采用 WG-07.1 用户确认值。

| Asset | Size | Category / role | Front | Anchor Y | Status |
| --- | --- | --- | --- | ---: | --- |
| rural_barn_large_01 | 21×14×33 | agricultural_large | SOUTH | 0 | VALID_WITH_WARNINGS |
| rural_farmhouse_01 | 20×17×14 | farmhouse | SOUTH | 0 | VALID_WITH_WARNINGS |
| rural_farmhouse_02 | 17×16×17 | farmhouse | SOUTH | 0 | METADATA_DEFINED_FROM_USER_VISUAL_QA；机械 VALID_WITH_WARNINGS |
| rural_grain_silo_01 | 16×23×15 | agricultural_utility | SOUTH | 1 | VALID_WITH_WARNINGS |
| rural_house_small_01 | 17×13×11 | residential | SOUTH | 1 | VALID_WITH_WARNINGS |
| rural_house_small_02 | 17×12×11 | residential | SOUTH | 0 | METADATA_DEFINED_FROM_USER_VISUAL_QA；机械 VALID_WITH_WARNINGS |
| rural_storage_small_01 | 13×11×17 | agricultural_utility | SOUTH | 0 | VALID_WITH_WARNINGS |
| rural_water_tower_01 | 13×31×13 | landmark | SOUTH | 0 | VALID_WITH_WARNINGS |

## 3. Socket Table

Socket 是模板局部 `(x,y,z)`：位于 NBT 外边缘内的第一格入口空气位，facing 向外。坐标来自逐格 NBT 边界/低层门与通路检查；机械几何 PASS 不等于玩家视角或车辆可通行已验收。

| Asset | Primary socket | Type | NBT 几何依据 |
| --- | --- | --- | --- |
| rural_barn_large_01 | west_stock_gate `(0,1,16)` WEST | SERVICE_ROAD | 西边三格围栏门；实际通车待视觉 QA |
| rural_farmhouse_01 | front_porch `(9,2,13)` SOUTH | PEDESTRIAN | 双门 `(8/10,2,8)` 前方门廊与南侧台阶；无明确车库入口 |
| rural_grain_silo_01 | south_access `(8,2,14)` SOUTH | SERVICE_ROAD | 南检修门 `(8,2,12)` 与台阶 `(8,1,13)`；不承诺车行门 |
| rural_house_small_01 | front_step `(7,2,10)` SOUTH | PEDESTRIAN | 南门 `(7,2,9)` 与边界台阶；无明确车库入口 |
| rural_storage_small_01 | south_entry `(6,1,16)` SOUTH | SERVICE_ROAD | 南门 `(6,1,14)` 和前场；另有东侧门但 V1 只取主入口 |
| rural_water_tower_01 | 无 | — | 无可证实的地面道路入口 |
| rural_farmhouse_02 | main `(9,0,16)` SOUTH | PEDESTRIAN | 用户人工视觉 QA 确认的场地/门廊主步行入口；不改作 DRIVEWAY |
| rural_house_small_02 | main `(8,1,10)` SOUTH | PEDESTRIAN | 用户人工视觉 QA 确认的南侧双开门前边界入口 |

WG-03 validator 已检查 7 个正式 socket：NBT 内、边界、朝外、入口空气及四向旋转后仍在模板框内。WG-07.1 另对两个 `_02` 的 socket/front/bounds 做四向明确断言。`SOCKET_USED_BY_LEGACY_DRIVEWAY = NO`。

## 4. Legacy Recipe Membership

有序配方只有：`rural_farmhouse_01`, `rural_barn_large_01`, `rural_house_small_01`, `rural_storage_small_01`, `rural_grain_silo_01`, `rural_water_tower_01`。声明的 `weight/maxCount` 分别是 `0/1`, `0/1`, `100/3`, `60/1`, `50/1`, `25/1`。两个 `_02` 不在配方，但现已通过独立自然池注册参与自然生成；未引入自然路径的加权抽取或 maxCount 执行。配方不包含 spacing、terrain、tier；WG-03 StructureDefinition 不放权重/数量。

## 5. `_02` Variant Status

WG-07 时两份为 `METADATA_PENDING_VISUAL_QA`；WG-07.1 用户已人工确认两栋的 SOUTH front、anchor=0 和上表 primary PEDESTRIAN socket。现已发布两份正式 JSON，状态 `METADATA_DEFINED_FROM_USER_VISUAL_QA`，机械结果 `VALID_WITH_WARNINGS`；未在本轮重启客户端做四向视觉验收，也未加入 Legacy Recipe。

## 6. Legacy Digest Regression

WG-06 `legacy_regression_v1.tsv` **原 24 行未更新**。迁移后无图形 GameTest 通过 24/24，摘要逐项一致。它是模拟地形、真实 NBT size 的 planner 回归，不是固定 seed 的真实 noise 或视觉证据。没有可靠冻结新增 Farmstead/Landmark 成功样本；这两个覆盖缺口仍在。不能为了补样本改 legacy 选择/布局。

## 7. Mechanical QA

`ruralMetadataTest` 读取 8 份真实压缩 NBT，校验八份 metadata 与 NBT SHA-256 revision、front/anchor、四向旋转、socket 和 catalog 映射；两个 `_02` 还断言用户确认的精确 socket 值、四向 rotated bounds/朝外/front 对齐及未进入 Legacy adapter。八份机械结果 `VALID_WITH_WARNINGS`（BE/保守多方块警告）。`terrainContractTest` 保留 WG-01～05.1 与 WG-03 动态 8 NBT 扫描；`check` 依赖 `ruralMetadataTest`/projection。构建和 GameTest 不证明门、BE 或通行体验。

WG-07.1 实测 `gradlew.bat compileJava terrainContractTest check projectionMathTest jar --offline --console=plain` 与 `gradlew.bat compileJava terrainContractTest check projectionMathTest runGameTestServer -Pwg06Verify --offline --console=plain`：均成功。WG-01～05.1 契约、WG-03 八 NBT 扫描、WG-07.1 八 metadata 与两个 `_02` 的 8 个四向变换断言、projection/check 均 PASS；无图形 GameTest 1/1、原 WG-06 digest 24/24 逐项一致。jar 内包含 catalog/recipe class、八份 metadata 和一份 recipe。WG-07 起止对 73 个正式 NBT/worldgen/相关 modifier 哈希一致；WG-07.1 起止同范围另行比对 73/73 一致。新增 `afl_worldgen` JSON 不算旧 worldgen JSON 修改；结论只限各任务起止，不追溯历史。

## 8. Game QA / Manual QA Boundary

WG-07.1 接受用户此前截图所做的两栋 `_02` 正面、贴地与主入口视觉确认，未自行启动开发客户端，未创建 8×4 QA 场，未做四向门/BE、入口或贴地复验。`FOUR_ROTATION_GAME_QA = NOT_PERFORMED`。未添加 dev 命令；机械 transform PASS 不冒充实机 QA。

<a id="manual-test-guide"></a>

## 9. Manual Test Guide

完全退出占用本项目构建输出的客户端，在根目录运行 `gradlew.bat runClient --offline`，新建可丢弃的创造模式世界，seed `62091307`，允许作弊。

**Test A — Natural（WG-07 历史建议，现需更新预期）：**执行 `/locate structure apocalypse_firstlight:rural`，传送到结果附近，换未生成区域连续检查 3～5 个站点。检查建筑数量/尺度、道路、农田与 front；两栋 `_02` 现在可以自然出现，应额外检查其四向朝向和入口。单次 locate 失败不是全局失败。

**Test B — 六旧资产四向：**在空旷可丢弃的作者测试区，使用结构方块或 `/place template apocalypse_firstlight:<asset> ~ ~ ~ <rotation>` 逐个摆放六份 NBT；rotation 依游戏命令提示选择四向。每次至少留 40 格间距。查看正面、门、BE/多方块、贴地层，确认 socket 边界到入口是否可通。`/place template` 默认处理不代替 Rural anchor；筒仓/小房需将模板原点下移 1 格对照 Rural 贴地。勿在正式世界覆写建筑。

**Test C — 新变体：**单独以相同方式摆放 `rural_farmhouse_02`、`rural_house_small_02` 并四向查看正门、步行入口、地基和门/BE。核对已确认值：两栋 front=SOUTH、anchor=0；分别为 `(9,0,16)` 和 `(8,1,10)` 的 SOUTH/PEDESTRIAN socket。两栋现已进入自然池，但单独摆放不能代替自然生成验收。

## 10. Rural V2 Readiness

八资产 metadata、七个入口 socket、旧六配方边界和 24 条兼容摘要已具备。按 WG-07.1 本次收尾口径，`WG-07 = COMPLETE`、`READY_FOR_RURAL_V2 = YES`（表示可进入独立设计任务，不表示四向实机 QA 已通过）；全资产四向实机 QA 与真实 noise 世界样本仍是后续验证项。本轮不启动 Rural V2。
