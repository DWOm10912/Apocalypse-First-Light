# FE 系统

AFL 使用 Forge Energy 构建发电端、用电端与公平分配网络；机器容量与配方见 [机器参数](../../02%20-%20内容设计/方块/机器.md)。

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

# 3. Energy Cable

能源线缆只负责连接 FE 网络。

## 规则

- 六方向连接
- 无 BlockEntity 储能
- 无损耗
- 无 Tier
- 无单段吞吐限制
- 不强制加载区块

直线内部段会隐藏 Core，只保留连续线缆模型。

端点、转弯、分叉和机器接口显示 Core + Arm。

---

