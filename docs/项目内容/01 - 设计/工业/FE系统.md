# FE 系统

AFL 使用 Forge Energy 构建发电端、用电端与公平分配网络；机器容量与配方见 [机器参数](机器.md)。

# 1. FE 电力系统

电力单位：

`FE / Forge Energy`

## Power Network V2

能源线缆连接的设备组成一个电力网络。

### 基础规则

| 项目 | 当前规则 |
|---|---|
| 网络结算 | 每 tick 统一结算 |
| 分配方式 | 公平分配 |
| 满足后的剩余 FE | 重新分配给仍有需求的设备 |
| 整数余数 | 按 tick 轮转，避免长期偏向固定设备 |
| 最大 Cable 扫描量 | 4,096 |
| 区块加载 | 不强制加载区块 |
| Cable 储能 | 无 |
| Cable 损耗 | 无 |
| Cable 吞吐上限 | 无 |
| Cable Tier | 无 |

只要设备：

- 储能未满
- 能源接口允许输入

就可以参与网络供电。

设备即使当前没有配方，也可以先充电。

---

## Producer / Consumer

### Producer

向网络提供 FE 的设备，例如：

- Thermal Generator
- 放电模式的 Energy Cell

### Consumer

从网络接收 FE 的设备，例如：

- Crusher
- Industrial Furnace
- Compressor
- Alloy Furnace
- Chemical Reactor
- 充电模式的 Energy Cell
- Charging Station（两个背面接口，同一网络里只算一个用电端）

单台设备仍然受到自己的：

`Max Input / Max Output`

限制。

Cable 本身不限制总吞吐。

---

# 2. 机器能耗规则

机器决定：

- Energy Capacity
- Max Input
- Max Output
- Work FE/t

配方决定：

- 输入
- 输出
- 加工时间

单次加工总能耗：

`Work FE/t × Processing Ticks`

只有加工进度真正前进时才消耗 FE。

以下情况会暂停加工，并且不扣 FE：

- 能量不足
- 输入不足
- 输出空间不足
- 流体不足
- 废液槽已满
- 其它配方条件不满足

---

# 3. 能量电池与便携设备（2026-10-01）

- 大部分电器只接能源线缆；少数便携工具（盖革计数器、手电、吸顶灯等）内置电量，到充电站充电。
- 充电站（2026-10-01 已实现，未实机验证）：两格长的充电台，接线缆取电，一次给托盘上的一个物品充电，128 FE/t；不向网络放电，不从物品抽电。属性见 [机器参数](../方块/机器.md) 第 9 节，模型见 [Charging Station V1](../../../models/charging_station_v1.md)。
- 能量电池：小储能物品，50,000 FE，造出来是空的；当合成材料，或给背包里没电的工具补电。详见 [Energy Battery V1](../../../models/energy_battery_v1.md)。
- 能量单元负责存多余的电和应急供电，不给物品充电。

# 4. Energy Cable

能源线缆只负责连接 FE 网络。

## 规则

- 六方向连接
- 无 BlockEntity 储能
- 无损耗
- 无 Tier
- 无单段吞吐限制
- 不强制加载区块

外观（2026-10-01 起 V2）：三根黑色导线的集束线缆，按连接形状显示直线、转弯、接线盒、封头；连向机器电源接口的一端是插头。机器的电源接口由方块自己声明（`AflPowerPortBlock`），原有七台机器都是背面一个，充电站两格背面各一个。详见 [Power Cable V2](../../../models/power_cable_v2.md)，包括以后 Mesh 机器要遵守的电源接口规格。

---

