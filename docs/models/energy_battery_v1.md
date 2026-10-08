# Energy Battery V1（能量电池）

状态（2026-10-01）：**物品已实现，未实机验证**。`compileJava --offline` 一次 PASS。外观只看过离线预览。生存里给电池充电用充电站（2026-10-01 已实现，用户实机 PASS，见 [Charging Station V1](charging_station_v1.md)）；创造模式物品栏里也有空电池和满电电池。

## 定位（2026-10-01 与用户商定）

- 大部分电器只接能源线缆，**不装电池**。
- 少数便携工具和设备（以后的盖革计数器、手电等）内置电量，不换电池，没电了拿到**充电站**充。~~吸顶灯也靠内置电量~~（作废，2026-10-08：吸顶灯走建筑暗线，接配电盘的"照明"那一路，天花板上仍然不拉线缆；见 [Building Lights V1](building_lights_v1.md)）。
- 能量电池是一个独立的小储能物品：
  - 以后做这些工具时当合成材料；
  - 背包里有电的电池可以给没电的工具补电（下面的 `EnergyBatteries`）；
  - 以后可能做成能放在地上、给相邻线缆供电的小电源（未做）。
- 能量单元（方块）负责存多余的电和应急供电，不加电池格，和充电站不重叠。
- 以后再做容量更大的锂电池。

## 物品

| 项目 | 值 |
|---|---|
| Registry ID | `apocalypse_firstlight:energy_battery`（`AflItems.ENERGY_BATTERY`，`item/EnergyBatteryItem`） |
| 名称 | 能量电池 / Energy Battery |
| 堆叠 | 1 |
| 容量 | **50,000 FE**，充、放电每 tick 各最多 128 FE（`data/apocalypse_firstlight/machine_balance/energy_battery.json`，由 `MachineBalanceManager.energyBattery()` 读取；文件缺失或无效时用相同的默认值） |
| 刚造出来 | 空的。合成配方等机器做完再定 |
| 能量接口 | 标准 Forge Energy 物品能力（`ForgeCapabilities.ENERGY`），可以充也可以放，其它支持 FE 物品的设备也能用 |
| 存储 | 物品 NBT：`Energy`（电量），`Capacity`（写入时的容量，让没有服务端数值的客户端也能画对电量条） |
| 显示 | 有电时显示耐久条：满电绿色，越少越偏红，空电池不显示。鼠标提示"能量：x / 50,000 FE"（沿用 `tooltip.apocalypse_firstlight.stored_energy_capacity`） |
| 创造物品栏 | "工业与工作站"标签页，能量单元后面：空电池，紧跟一个满电电池（`AflCreativeTabExtras`） |
| 掉落、配方、战利品 | 都还没有 |

## 背包补电（给以后的工具）

`energy/EnergyBatteries`（服务端）：
- `storedInInventory(player)`：玩家身上所有能量电池的总电量；
- `drawFromInventory(player, amount, simulate)`：按格子顺序（主背包、快捷栏，再是副手）从电池里取电，每块电池受它每次 128 FE 的限制，不需要界面。

现在还没有工具调用它。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-energy-battery-v1.mjs`（`--check`、`--preview DIR`），复用 `tools/build-material-meshes-v1.mjs` 导出的 `bake`（只是给它加了 `export`，材料物品的输出不变） |
| 外形 | 圆柱形小电池：深色收缩膜外壳，膜包过两端边缘；正极一端有琥珀色色环；镀镍的端面和凸起的正极帽；负极是平的金属底。不印字 |
| 成品 | 252 个三角面，9.35 × 4.66 × 4.54 px，没有共面重叠，256 atlas，13 texel/px |
| 材质（LabPBR） | 收缩膜 [42,46,52] smoothness 118–140、F0 20；色环 [184,136,46] 同样的光泽；端面和正极帽 [156,158,162] 镀镍钢，F0 255、smoothness 132–150 |
| 显示 | 物品栏是由模型画出的 16×16 2D 图标（`textures/item/energy_battery.png`，视角 [30,225,0]）；手持、地面、展示框是 Mesh（`forge:separate_transforms`，`AflStaticMeshItemRenderer`，竖直偏移 0.354375） |
| 文件 | `src/main/blockbench/energy_battery.bbmodel`、`textures/energy_battery_mesh{,_s,_n}.png`；运行时 `geo/energy_battery.geo.json`、`meshes/energy_battery.aflmesh.json`、`models/item/energy_battery.json`、`textures/item/energy_battery{,_mesh,_mesh_s,_mesh_n}.png` |

## 待实机确认

1. 物品栏图标、手持和展示框里的模型；
2. 满电电池的耐久条和鼠标提示；
3. 其它模组的 FE 物品充电器能不能给它充电（如果装了的话）。

## 充电站

已做（2026-10-01）：接线缆，一次给托盘上的一个物品充电，128 FE/t（50,000 FE 的电池约 20 秒充满），内部只有 10,000 FE 缓冲，不向网络放电。见 [Charging Station V1](charging_station_v1.md)。
