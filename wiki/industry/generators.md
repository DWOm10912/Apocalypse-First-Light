# 发电机

<StatusTag s="creative" />

AFL 中的“1 小时”指游戏内 1 小时（<V j="DieselGeneratorBlockEntity.HOUR_TICKS" u="tick" />）；1 FE/t 折合 <V j="DieselGeneratorBlockEntity.KW_PER_FE_TICK" u="kW" />

## 柴油发电机组

<ModelShot id="diesel_generator" zh="柴油发电机组" en="Diesel generator" />

固定安装的大型柴油发电机组，通过钢制接线口接入建筑配电

| 项目 | 数值 |
|---|---|
| 额定输出 | <V j="DieselGeneratorBlockEntity.RATED" u="FE/t" />（<V j="DieselGeneratorBlockEntity.RATED" x="0.25" u="kW" />） |
| 油箱 | <V j="DieselGeneratorBlockEntity.TANK" u="L" />，只用柴油 |
| 低油位警告 | <V j="DieselGeneratorBlockEntity.LOW_FUEL" u="L" /> |
| 油耗 | 每 kWh <V j="DieselGeneratorBlockEntity.L_PER_KW" u="L" /> + 怠速每小时 <V j="DieselGeneratorBlockEntity.IDLE_L" u="L" /> |
| 噪音 | 启动 <V j="DieselGeneratorBlockEntity.CRANK_NOISE" u="格" />，运转 <V j="DieselGeneratorBlockEntity.RUN_NOISE" u="格" /> |

- 空载时仍按怠速油耗烧油，不用时应停机
- 用钥匙启动

## 便携式柴油发电机

<ModelShot id="portable_diesel_generator" zh="便携式柴油发电机" en="Portable generator" />

拉绳启动的小型发电机，自带 <V j="PortableDieselGeneratorBlockEntity.SOCKETS" /> 个插座，用电线直接给电器供电

| 项目 | 数值 |
|---|---|
| 额定输出 | <V j="PortableDieselGeneratorBlockEntity.RATED" u="FE/t" />（<V j="PortableDieselGeneratorBlockEntity.RATED" x="0.25" u="kW" />） |
| 油箱 | <V j="PortableDieselGeneratorBlockEntity.TANK" u="L" />，只用柴油 |
| 油耗 | 每 kWh <V j="PortableDieselGeneratorBlockEntity.L_PER_KW" u="L" /> + 怠速每小时 <V j="PortableDieselGeneratorBlockEntity.IDLE_L" u="L" /> |
| 噪音 | 拉绳 <V j="PortableDieselGeneratorBlockEntity.PULL_NOISE" u="格" />，运转 <V j="PortableDieselGeneratorBlockEntity.RUN_NOISE" u="格" /> |

**拉绳启动**：视角锁定，屏幕上出现时间条，在好的区间内按键即为一次好的拉动。每拉一次消耗 <V d="stamina/stamina_v1#costs.recoil_pull" /> 点耐力

| | 拉得好 | 拉得差 |
|---|---|---|
| 冷机 | <V j="PortableDieselGeneratorBlockEntity.COLD_GOOD" pct /> 起动成功 | <V j="PortableDieselGeneratorBlockEntity.COLD_POOR" pct /> |
| 热机 | <V j="PortableDieselGeneratorBlockEntity.WARM_GOOD" pct /> | <V j="PortableDieselGeneratorBlockEntity.WARM_POOR" pct /> |

熄火后 <V j="PortableDieselGeneratorBlockEntity.WARM_TICKS" x="0.05" u="秒" /> 内仍按热机计算。所接电器超过额定功率时会跳闸

## 热力发电机

<ModelShot id="thermal_generator" zh="热力发电机" en="Thermal generator" />

热力发电机燃烧煤或岩浆发电

| 项目 | 数值 |
|---|---|
| 发电功率 | <V d="machine_balance/thermal_generator#generation_fe_per_tick" u="FE/t" /> |
| 储能 | <V d="machine_balance/thermal_generator#capacity_fe" u="FE" /> |
| 煤 / 木炭 | <V d="machine_balance/thermal_generator#fuels.minecraft:coal.energy_fe" u="FE" /> |
| 煤块 | <V d="machine_balance/thermal_generator#fuels.minecraft:coal_block.energy_fe" u="FE" /> |
| 岩浆桶 | <V d="machine_balance/thermal_generator#fuels.minecraft:lava_bucket.energy_fe" u="FE" /> |
| 岩浆罐 | <V j="ThermalFuelDefinitions.TANK_CAPACITY_MB" u="L" /> |

储能存满后停止燃烧
