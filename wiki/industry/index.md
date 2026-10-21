# 工业制造

AFL 的电能以 FE（Forge Energy）计量。发电机把柴油、煤或岩浆转化为电能，配电盘把电分配给建筑里的灯和插座，机器用电加工矿石和材料

<StatusTag s="creative" /> 机器、电气部件和木材目前都没有合成配方，只能在创造模式获取；矿石也还不会自然生成

## 分区

| 页面 | 内容 |
|---|---|
| [发电机](./generators) | 柴油发电机组、便携式柴油发电机、热力发电机 |
| [建筑供电](./power) | 配电盘、暗线布线、插座和电线、灯 |
| [材料与加工](./materials) | 矿石、加工流程、机器功耗、木材 |

## 工作台

<StatusTag s="test" /> <ItemIcon id="wooden_workbench" /> 木制工作台，2 格长、1 格宽、2 格高：左边一台台钳，右边桌面下两个抽屉，背后一块工具挂板，顶上一层搁板

- 对着抽屉面右键，抽屉平滑地拉出来；再右键推回去。拉出来以后对着抽屉里面右键打开，每个抽屉 <V j="WorkbenchBlockEntity.DRAWER_SLOTS" /> 格，两个抽屉分开放东西
- 挂板上有 <V j="Workbench.PEGS" /> 对木钉，每对挂一件工具：拿着工具对着木钉右键挂上，空手右键取下，和木质工具架一样。伐木斧太长，挂不上
- 挂着的工具和工作台一起画进区块，不会每帧重画
- 拆掉工作台时，抽屉里的东西和挂着的工具都会掉出来；用斧子拆得快
- 抽屉暂时用木柜门的声音
- 目前只能从创造模式物品栏获得
- <StatusTag s="planned" /> 制作：界面、配方和制作用时还在设计

## 机器一览

| 机器 | 储能 | 工作功耗 |
|---|---|---|
| <ItemIcon id="crusher" /> 粉碎机 | <V d="machine_balance/crusher#capacity_fe" u="FE" /> | <V d="machine_balance/crusher#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="industrial_furnace" /> 工业熔炉（3 条通道） | <V d="machine_balance/industrial_furnace#capacity_fe" u="FE" /> | 每条通道 <V d="machine_balance/industrial_furnace#work_fe_per_tick_per_lane" u="FE/t" /> |
| <ItemIcon id="alloy_furnace" /> 合金炉 | <V d="machine_balance/alloy_furnace#capacity_fe" u="FE" /> | <V d="machine_balance/alloy_furnace#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="chemical_reactor" /> 化学反应器 | <V d="machine_balance/chemical_reactor#capacity_fe" u="FE" /> | <V d="machine_balance/chemical_reactor#work_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="compressor" /> 压缩机 | <V d="machine_balance/compressor#capacity_fe" u="FE" /> | <V d="machine_balance/compressor#work_fe_per_tick" u="FE/t" />（尚无配方） |
| <ItemIcon id="intake_pump" /> 取液泵 | <V d="machine_balance/intake_pump#capacity_fe" u="FE" /> | <V d="machine_balance/intake_pump#work_fe_per_tick" u="FE/t" />，每 tick 抽取 <V d="machine_balance/intake_pump#pump_mb_per_tick" u="L" /> |
| <ItemIcon id="charging_station" /> 充电站 | <V d="machine_balance/charging_station#capacity_fe" u="FE" /> | 充电 <V d="machine_balance/charging_station#charge_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_battery" /> 能量电池 | <V d="machine_balance/energy_battery#capacity_fe" u="FE" /> | 充放电 <V d="machine_balance/energy_battery#max_extract_fe_per_tick" u="FE/t" /> |
| <ItemIcon id="energy_cell" /> 能量单元 | <V d="machine_balance/energy_cell#capacity_fe" u="FE" /> | 充放电 <V d="machine_balance/energy_cell#max_extract_fe_per_tick" u="FE/t" /> |

<ItemIcon id="precision_fabrication_station" />精密制造台目前只作装饰
