# 燃料与加油

AFL 的液体按真实体积计算：**1 mB = 1 L**，界面上一律以 L 显示。原版的桶不能装燃料

## 燃料

| 燃料 | 密度 | 用途 |
|---|---|---|
| 汽油 | 0.74 kg/L | 目前没有使用汽油的机器 |
| 柴油 | 0.84 kg/L | T15、柴油发电机组、便携式柴油发电机 |

## 容器

<StatusTag s="creative" />

<ModelShot id="jerry_can" zh="钢制油壶" en="Jerry can" />

| 容器 | 容量 | 说明 |
|---|---|---|
| <ItemIcon id="jerry_can" /> 钢制油壶（20 L） | 20 L | 拿在手里对准加油口倒油，每秒约 4 L |
| <ItemIcon id="small_fuel_drum" /> 小油桶（60 L） | 60 L | 放在地上，配手摇油泵使用 |
| <ItemIcon id="fuel_drum" /> 油桶（200 L） | 200 L | 放在地上，配手摇油泵使用 |
| <ItemIcon id="hand_fuel_pump" /> 手摇油泵 | — | 装在油桶上，每 <V j="HandFuelPumpBlockEntity.TICKS_PER_LITRE" u="tick" /> 抽 1 L（每秒 2 L） |

世界里建筑中放置的容器，初始油量由建筑数据决定

## 给 T15 加油

1. 打开车身左后侧的油箱盖
2. 手持装有柴油的钢制油壶，对准加油口按住使用键；也可以使用加油机的油枪
3. 油枪流量约每秒 10 L

加错油加不进去：T15 只接受柴油

## 加油站

<StatusTag s="test" /> 加油站设备已经可以使用，但世界里还不会自然生成加油站

<ModelShot id="fuel_dispenser" zh="加油机" en="Fuel dispenser" />

- <ItemIcon id="fuel_dispenser" />**加油机**：拿起油枪使用，油管长 <V j="FuelDispenserBlockEntity.HOSE_LENGTH" u="格" />，拉到 <V j="FuelDispenserBlockEntity.BREAKAWAY" u="格" /> 时自动脱开。需要供电
- <ItemIcon id="underground_fuel_tank_diesel" />**地下油罐**：3×3×7，容量 <V j="UndergroundFuelTankBlockEntity.CAPACITY_MB" u="L" />，通过潜油泵向加油机供油
- **加油站顶棚**：带灯，需要供电

供电见 [建筑供电](../industry/power)
