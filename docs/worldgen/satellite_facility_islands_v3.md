# Fixed Three Satellite Metadata V3

状态（2026-09-22）：固定三岛与非地形元数据保留；天然 Campus 资格实现因导致游戏崩溃已完整撤回。岛屿恢复 V1.1 原始尺寸、角度、bank-fit 与缓存行为。本轮没有运行编译、测试或客户端。

## Fixed geography count

- `MacroGeography.VERSION = 3`。
- 附属岛数量固定为3；稳定ID/slot始终是1、2、3，不按位置、面积或设施角色重新排序。
- 除 count 固定为3外，岛屿生成恢复原始行为：major `[550,750)`、minor `[400,500)`、gap `[400,500)`、原salt、原slot jitter、最多33次确定性bank-fit修正、原椭圆加modulation轮廓。
- 没有新增岛间spacing rejection、sector candidate pool、尺寸放大、最终高度查询、Campus retry或初始化期拓扑替换。
- `MacroGeography` 恢复不可变计划和原16-seed LRU缓存；不再依赖generator初始化阶段。

## Non-terrain metadata

`SatelliteIslandPolicy` 只保存不参与地形的政策：

- `FacilityRole`: `VIRUS_LAB`、`MILITARY_BASE`、`LARGE_PRISON`。
- world seed xor稳定`ROLE_SALT`驱动Fisher–Yates；固定enum输入顺序，三个角色一岛一个。
- `BridgePolicy`: 三个slot当前均为`BRIDGE_REQUIRED`。
- API：`MacroGeography.satellitePolicy(id)`。

设施角色不会改变岛屿ID、尺寸、位置、地形或routing排序。本轮不生成设施建筑。

## Routing

`SatelliteHighwayRouting` 读取bridge policy；三个slot均会进入既有routing。原solver、岸线搜索、桥头、路线冲突、诊断和“连接失败时保留diagnostic而不使世界初始化崩溃”的行为恢复。

候选接受阶段不再调用routing内部算法，不再因bridge-compatible或Campus资格替换岛屿。Sea Bridge Geometry/Engineering、H塔、钢缆、桥面和damage state均未改。

## Removed crash path

已移除：

- `SatelliteFacilityEligibility` 最终高度扫描与Campus记录；
- `ChunkGenerator.createState` 注入；
- `getBaseHeight` 初始化期资格调用；
- ThreadLocal candidate preview、可变satellite snapshot、weak canonical plan；
- 384/320 Campus、16/4高差、1024 spacing、8候选重试；
- `requireFacilitiesReady()` 与required routing fail-closed异常。

因此当前代码不再承诺天然Campus资格。导出明确写 `facilityEligibilityStatus = NOT_EVALUATED`。

## Validation status

```text
SATELLITE_COUNT_FIXED_3 = YES
STABLE_SLOT_IDS = YES
ORIGINAL_ISLAND_SIZE_AND_PLACEMENT = RESTORED
FACILITY_ROLE_SHUFFLE = YES
ONE_ROLE_PER_ISLAND = YES
BRIDGE_REQUIRED_SATELLITE_1 = YES
BRIDGE_REQUIRED_SATELLITE_2 = YES
BRIDGE_REQUIRED_SATELLITE_3 = YES
FINAL_TERRAIN_ELIGIBILITY = REMOVED
FACILITY_CAMPUS_RECORDED = NO
PAIRWISE_SPACING = REMOVED
CHUNK_GENERATOR_INIT_MIXIN = REMOVED
SEA_BRIDGE_DAMAGE_STATE = DEFERRED
COMPILEJAVA = NOT RUN AFTER ROLLBACK
PROCESSRESOURCES = NOT RUN
BUILD = NOT RUN
GAMETEST = NOT RUN
RUNCLIENT = NOT RUN
WORLD GENERATED = NO
COMMIT = NO
PUSH = NO
```

```text
DOCUMENTATION:
DOCS UPDATED = YES
Updated:
- docs/worldgen/satellite_facility_islands_v3.md
- docs/worldgen/terrain_v2_macro_geography_v1.md
- docs/worldgen/highway_v2_satellite_routing_phase2b2.md
```
