# 精密制造台 V1

| 项目 | 当前行为 |
| --- | --- |
| Registry ID | `apocalypse_firstlight:precision_fabrication_station`，Block / BlockItem |
| 名称 | 精密制造台 / Precision Fabrication Station |
| 尺寸 | 宽 2 × 高 2 × 深 1；四个真实占位格 |
| 朝向与组成 | `facing=north/east/south/west`；`part=base/side/upper/upper_side`，side 沿 facing 顺时针一格 |
| 放置 | 完整四格空间、支撑、液体、实体、世界边界与高度检查；失败回退，成功消耗一个物品 |
| 破坏 | 任意 part 清理整台；仅最初破坏格执行一次标准 loot，其余联动移除不掉落；创造模式无掉落 |
| 区块恢复 | 复用 `StaticWorkstationIntegrity` 加载检查，不强制加载相邻区块，孤块延后清理且不产出物品 |
| VoxelShape | 25 个主要结构盒裁成四片，预缓存四向旋转；覆盖柱、台面、背板、收纳、控制台、驱动与压装头；按钮等不细分 |
| 工具 | `requiresCorrectToolForDrops()`；`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`，钻石/下界合金镐正确采集 |
| 属性 | 硬度 3.0，抗爆 6.0，METAL；不可被活塞推动 |
| 创造页 | AFL 方块，枪械维护台之后；不加入武器与弹药页 |
| 渲染 | 四格静态 baked model，无 BE / BER；最新降噪贴图，原接口像素及平滑底板保留 |
| 背面布局 | 接口保持所属格中心最近整像素位置；右面板随接口居中，左排气检修框收窄，统一上下沿和边框；排气孔缩短 25%。仅背面装饰几何/贴图调整，碰撞和方块逻辑不变 |
| 交互与灯 | 右键无 UI、无聊天、无功能；工作灯/状态灯仅视觉，光照 0 |
| 未实现 | 制造/配方、GUI、库存、FE capability、Power Network、动画、声音、JEI/Jade |
| 后续定位 | FE 驱动的精密制造/装配：枪械、配件、弹药/精密部件及小型装备；仅计划 |

资源：`src/main/blockbench/precision_fabrication_station.bbmodel`；运行时 `src/main/resources/assets/apocalypse_firstlight/` 下 `blockstates/precision_fabrication_station.json`、`models/block/precision_fabrication_station/*.json`、`models/item/precision_fabrication_station.json`、`textures/block/precision_fabrication_station.png`。Geo 仅预留，不参与渲染。

同步：`node tools/export-gun-maintenance-bench-parts.mjs precision_fabrication_station`。分片检查几何体积、UV 与源对应，不重建模型。

公共逻辑：`StaticWorkstationBlock`、`StaticWorkstationBlockItem`、`StaticWorkstationIntegrity`；两台不复制放置/拆除实现。

验证：资源分片/源与纹理校验、`compileJava processResources build --offline -PaflWithoutTacz`、`git diff --check` 通过。两台参数化 Forge GameTest **10/10 通过（每台 5 项）**，覆盖四向放置与碰撞、阻挡、32 种工具×部位生存采集、创造/命令/爆炸清理、单一掉落和孤块修复。日志 `build/workstation-tests.log`；NBT 往返与孤块测试不等同于真实跨区块卸载重载验收。

图形客户端已由 `runClient --offline -PaflWithoutTacz` 启动，日志 `build/workstation-client.log`；视觉与人工保存重进仍待用户确认，不把启动等同于实机验收。
