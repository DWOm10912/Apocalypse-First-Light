# 工业制造

AFL 的电用 FE（Forge Energy）计量。发电机把柴油、煤或岩浆变成电，配电箱把电分给房子里的灯和插座，机器用电加工矿石和材料

<StatusTag s="creative" /> 机器、电气件和木材现在都没有合成配方，只能在创造模式拿到；矿石也还不会自然生成

## 分区

| 页面 | 内容 |
|---|---|
| [发电机](./generators) | 柴油发电机、便携发电机、热能发电机 |
| [建筑供电](./power) | 配电箱、隐藏布线、插座和电线、灯 |
| [材料与加工](./materials) | 矿石、加工链、机器耗电、木材 |

## 机器一览

| 机器 | 储电 | 工作耗电 |
|---|---|---|
| 破碎机 | <V d="machine_balance/crusher#capacity_fe" u="FE" /> | <V d="machine_balance/crusher#work_fe_per_tick" u="FE/t" /> |
| 工业熔炉（3 条通道） | <V d="machine_balance/industrial_furnace#capacity_fe" u="FE" /> | 每条通道 <V d="machine_balance/industrial_furnace#work_fe_per_tick_per_lane" u="FE/t" /> |
| 合金炉 | <V d="machine_balance/alloy_furnace#capacity_fe" u="FE" /> | <V d="machine_balance/alloy_furnace#work_fe_per_tick" u="FE/t" /> |
| 化学反应器 | <V d="machine_balance/chemical_reactor#capacity_fe" u="FE" /> | <V d="machine_balance/chemical_reactor#work_fe_per_tick" u="FE/t" /> |
| 压缩机 | <V d="machine_balance/compressor#capacity_fe" u="FE" /> | <V d="machine_balance/compressor#work_fe_per_tick" u="FE/t" />（还没有配方） |
| 抽水泵 | <V d="machine_balance/intake_pump#capacity_fe" u="FE" /> | <V d="machine_balance/intake_pump#work_fe_per_tick" u="FE/t" />，每 tick <V d="machine_balance/intake_pump#pump_mb_per_tick" u="L" /> |
| 充电站 | <V d="machine_balance/charging_station#capacity_fe" u="FE" /> | 充电 <V d="machine_balance/charging_station#charge_fe_per_tick" u="FE/t" /> |
| 储能电池 | <V d="machine_balance/energy_battery#capacity_fe" u="FE" /> | 进出 <V d="machine_balance/energy_battery#max_extract_fe_per_tick" u="FE/t" /> |
| 储能单元 | <V d="machine_balance/energy_cell#capacity_fe" u="FE" /> | 进出 <V d="machine_balance/energy_cell#max_extract_fe_per_tick" u="FE/t" /> |

精密加工站目前只是装饰
