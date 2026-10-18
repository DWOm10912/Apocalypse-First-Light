# 燃料与加油

AFL 的液体按真实体积算：**1 mB = 1 升**，界面上一律显示升（L）。原版的桶不能装燃料

## 燃料

| 燃料 | 密度 | 用途 |
|---|---|---|
| 汽油 | 0.74 kg/L | 目前没有用汽油的机器 |
| 柴油 | 0.84 kg/L | T15、柴油发电机、便携发电机 |

## 容器

<StatusTag s="creative" />

| 容器 | 容量 | 说明 |
|---|---|---|
| 油桶（Jerry can） | 20 L | 拿在手里对着油箱口倒，每秒约 4 L |
| 小油桶 | 60 L | 放在地上，配手摇泵 |
| 大油桶 | 200 L | 放在地上，配手摇泵 |
| 手摇泵 | — | 插在油桶上，每 <V j="HandFuelPumpBlockEntity.TICKS_PER_LITRE" u="tick" /> 抽 1 L（每秒 2 L） |

放在世界里的建筑容器，一开始装多少油由建筑数据决定

## 给 T15 加油

1. 打开车身左后侧的油箱盖
2. 拿着装柴油的油桶对着油箱口按住使用键，或者用加油机的油枪
3. 油枪每秒约 10 L

加错油不会加进去：T15 只收柴油

## 加油站

<StatusTag s="test" /> 加油站的设备已经能用，但世界里还不会自然生成加油站

- **加油机**：拿起油枪，油管长 <V j="FuelDispenserBlockEntity.HOSE_LENGTH" u="格" />，拉到 <V j="FuelDispenserBlockEntity.BREAKAWAY" u="格" /> 会自动脱开。需要电
- **地下油罐**：3×3×7，容量 <V j="UndergroundFuelTankBlockEntity.CAPACITY_MB" u="L" />，用潜油泵给加油机供油
- **雨棚**：带灯，需要电

供电见 [建筑供电](../industry/power)
