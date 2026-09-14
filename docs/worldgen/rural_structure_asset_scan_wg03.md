# Rural Structure Asset Scan — WG-03

日期：2026-09-13。**只读机械扫描事实清单；无生成器迁移、无正式 metadata 写入、无游戏 QA。**

## 范围与来源

- 动态枚举 `src/main/resources/data/apocalypse_firstlight/structures/rural_*.nbt`，本次实际 **8** 份。
- 与 WG-03 请求列出的 8 个文件相比：无新增、无缺失。与旧 Audit 的六资产快照相比，多出 `rural_farmhouse_02` 和 `rural_house_small_02`。
- 尺寸取压缩 NBT 的标准 `size`（X/Y/Z），不是手填 metadata、可见模型包围盒或非 air 紧包围盒。显式 air 和 padding 均保留在 derived bounds。
- 池成员、front、anchor 直接来自本次读取的 `src/dev/java/com/antaurora/apofirstlight/worldgen/rural/RuralStructurePool.java`。未更改该文件或池参数。
- 扫描实现：`src/main/java/com/antaurora/apofirstlight/worldgen/structure/StructureNbtReader.java`；动态扫描测试：`src/test/java/com/antaurora/apofirstlight/worldgen/structure/RuralAssetScanTest.java`。
- BLOCK_COUNT 是 NBT `blocks` 记录数；EXPLICIT_AIR_COUNT 是所引用 palette 为 air/cave_air/void_air 的记录数（多 palette 时要求所有候选均为空气）；BE 是含 compound `nbt` 的方块记录数，不包含实体列表。

## 文件与几何事实

所有 bounds 均为整数半开区间，顺序 X × Y × Z。

| FILE | SIZE_X | SIZE_Y | SIZE_Z | DERIVED_BOUNDS | BLOCK_COUNT | EXPLICIT_AIR_COUNT | BLOCK_ENTITY_COUNT |
| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| rural_barn_large_01.nbt | 21 | 14 | 33 | [0,21) × [0,14) × [0,33) | 3676 | 0 | 34 |
| rural_farmhouse_01.nbt | 20 | 17 | 14 | [0,20) × [0,17) × [0,14) | 2449 | 652 | 49 |
| rural_farmhouse_02.nbt | 17 | 16 | 17 | [0,17) × [0,16) × [0,17) | 2666 | 705 | 24 |
| rural_grain_silo_01.nbt | 16 | 23 | 15 | [0,16) × [0,23) × [0,15) | 1875 | 822 | 4 |
| rural_house_small_01.nbt | 17 | 13 | 11 | [0,17) × [0,13) × [0,11) | 1082 | 305 | 37 |
| rural_house_small_02.nbt | 17 | 12 | 11 | [0,17) × [0,12) × [0,11) | 1320 | 374 | 18 |
| rural_storage_small_01.nbt | 13 | 11 | 17 | [0,13) × [0,11) × [0,17) | 1697 | 775 | 23 |
| rural_water_tower_01.nbt | 13 | 31 | 13 | [0,13) × [0,31) × [0,13) | 1656 | 0 | 87 |

## 旧池与未定义信息

表中 anchor 是旧定义已知值，不是本轮新设定。所有资产 `SOCKET_STATUS = NOT_DEFINED`；旧 driveway 的 front midpoint 推导不是显式 socket。

| FILE | GROUND_ANCHOR_CURRENT_KNOWN_VALUE | CURRENT_FRONT | CURRENT_POOL_STATUS |
| --- | --- | --- | --- |
| rural_barn_large_01.nbt | 0 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |
| rural_farmhouse_01.nbt | 0 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |
| rural_farmhouse_02.nbt | UNDEFINED_PENDING_METADATA | UNDEFINED_PENDING_METADATA | NOT_IN_LEGACY_RURAL_POOL |
| rural_grain_silo_01.nbt | 1 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |
| rural_house_small_01.nbt | 1 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |
| rural_house_small_02.nbt | UNDEFINED_PENDING_METADATA | UNDEFINED_PENDING_METADATA | NOT_IN_LEGACY_RURAL_POOL |
| rural_storage_small_01.nbt | 0 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |
| rural_water_tower_01.nbt | 0 | SOUTH | CURRENTLY_IN_LEGACY_RURAL_POOL |

两份 `_02` 变体具备可读取的正尺寸 NBT，可供未来 metadata 契约描述；当前仍缺已批准的 front、ground anchor、显式 socket、资产 category/tags、revision 和 allowed-rotations 声明。本轮不猜测、不生成这些 metadata，也不加入任何池。旧六资产的 Java front/anchor 已知，不代表已有 WG-03 metadata 文件或入口坐标。

## 机械结果与未验证项

8 份 NBT 的 size 均为正，方块记录坐标均在 size 内，无重复记录坐标，palette 索引有效；本次读取未报禁止控制/调试块或实体导入错误。

每份均报告 `BE_REQUIRES_GAME_QA` 和 `MULTIBLOCK_REQUIRES_GAME_QA`，读取结果为 `VALID_WITH_WARNINGS`。这只是 NBT 内容机械检查结果，**不是这些资产的完整 metadata/socket 已通过验证**。

多方块警告采用保守的 palette `half` / `part` 属性识别，也可能包含楼梯等半部状态，不证明多格配对完整，不把单个属性记录数当成设备数量。此扫描无法确认第三方私有 BE 格式、实际贴地效果、可视模型越界、门/床/设备存活或四方向摆放后的正确性。

`GAME_QA = NOT_PERFORMED`。没有启动客户端、创建世界、执行摆放或 GameTest。未写入任何 GAME_QA 状态。旧 City authoring 的 road/surface-plane 文案仍需逐资产确认是否等同 WG-01 的地面上方第一格；WG-03 显式适配器在未确认时拒绝发布映射定义，不影响当前 authoring 或 Rural。

## NBT SHA-256 快照

动态扫描对每个文件在读取前、读取后重新计算 SHA-256；全部一致。以下仅为本次事实证据，不是新的运行时 asset digest/QA cache 系统。

| FILE | SHA-256 |
| --- | --- |
| rural_barn_large_01.nbt | 458aa1b10cc9796b44743a4ea25d492c7c4047ccf7c73ac2de911af2eff1e6bc |
| rural_farmhouse_01.nbt | ffbe6aa670b861a635b22fc3c3f720989d04d355a1b94a9521a518cc660a9d0d |
| rural_farmhouse_02.nbt | 2c671ea0eb3a92eab3b49a899fa0643196b09d2aebc3654b6ce9693772bd6ded |
| rural_grain_silo_01.nbt | 2c3ddcbebdbf9742706e8e88a91cec195df51e26c2fd7a0aa2bf00252bd72aa5 |
| rural_house_small_01.nbt | c0d021494381e402806097ea1747287f677186cefee27ca1d6275bfc62a1d590 |
| rural_house_small_02.nbt | 76aafe4f43e9445dd504b43d01ae4bad1df2ba903e952d4f77f3a329f500d51b |
| rural_storage_small_01.nbt | 70a022742682f72a92da8c09cc09f22565028bfa1ff843f9b6cffe5866f7498d |
| rural_water_tower_01.nbt | 809fe5ed2db1daa5f96cb035a70a024bc0da7f2bc9ede27e0e20a4c4ea94be0e |

## 复核入口

退出使用本项目输出的开发客户端后，在项目根目录运行现有任务：

```text
gradlew.bat compileJava terrainContractTest check --offline
```

`terrainContractTest` 顺序运行 WG-01、WG-02、WG-03 与动态 Rural 扫描；`check` 同时包含现有 projectionMathTest。扫描只输出事实，不更新本文、不写 NBT、不生成正式 metadata、不注册资源重载监听器。
