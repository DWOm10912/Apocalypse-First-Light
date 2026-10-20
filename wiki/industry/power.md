# 建筑供电

<StatusTag s="creative" />

建筑里的电线走暗线，藏在墙内：用布线扳手在墙里布线，把灯、开关和插座接到配电盘上，再把配电盘接到发电机

## 配电盘

<ModelShot id="distribution_panel" zh="商业配电盘" en="Distribution panel" />

| 项目 | 数值 |
|---|---|
| 回路 | <V j="DistributionPanelBlockEntity.SLOTS" /> 个断路器位 |
| 储能 | <V j="DistributionPanelBlockEntity.CAPACITY" u="FE" /> |
| 最大输入 / 输出 | <V j="DistributionPanelBlockEntity.MAX_IN" u="FE/t" /> / <V j="DistributionPanelBlockEntity.MAX_OUT" u="FE/t" /> |
| 跳闸 | 过载持续 <V j="DistributionPanelBlockEntity.TRIP_TICKS" x="0.05" u="秒" /> 后断开 |

打开配电盘能看到供电、负载和各回路的状态，每个回路都能单独合闸或分闸

## 布线扳手

- <ItemIcon id="wiring_wrench" />手持布线扳手在方块上拖动即可布线，方式类似泰拉瑞亚的电线；线路藏在方块内部
- <ItemIcon id="insulated_copper_wire" />每格消耗 1 根绝缘铜线
- 共有 4 种颜色的回路，同一个方块里能同时走多条
- 只有手持布线扳手时才能看到线路

## 插座和电线

- <ItemIcon id="wall_outlet" /><ItemIcon id="power_strip_3" />低功率电器（灯、小电器）通过电源线插在插座上
- 大功率设备和固定设备（如柴油发电机组）使用钢制接线口
- 电线长度：插线板 <V j="PlugCord.STRIP_LENGTH" u="格" />，普通电器 <V j="PlugCord.APPLIANCE_LENGTH" u="格" />，接发电机 <V j="PlugCord.GENERATOR_LENGTH" u="格" />

## 灯

- <ItemIcon id="porcelain_lampholder" /><ItemIcon id="keyless_lampholder" /><ItemIcon id="wall_switch" />室内灯每盏功耗 <V j="BuildingLights.FE_PER_TICK" u="FE/t" />，墙上开关控制整条回路
- <ItemIcon id="emergency_light" />**应急灯**：内置电池 <V j="EmergencyLightBlockEntity.CAPACITY" u="FE" />；有电时每 tick 充入 <V j="EmergencyLightBlockEntity.CHARGE" u="FE" />，停电后自动点亮，每 tick 消耗 <V j="EmergencyLightBlockEntity.DRAIN" u="FE" />（充满后约能亮 5 分钟）
- 停电时灯会先闪一下再熄灭
