# 建筑供电

<StatusTag s="creative" />

房子里的电线藏在墙里：用布线扳手在墙里画线，把灯、开关和插座接到配电箱上，再把配电箱接到发电机

## 配电箱

| 项目 | 数值 |
|---|---|
| 回路 | <V j="DistributionPanelBlockEntity.SLOTS" /> 个断路器位 |
| 储电 | <V j="DistributionPanelBlockEntity.CAPACITY" u="FE" /> |
| 最大进 / 出 | <V j="DistributionPanelBlockEntity.MAX_IN" u="FE/t" /> / <V j="DistributionPanelBlockEntity.MAX_OUT" u="FE/t" /> |
| 跳闸 | 过载 <V j="DistributionPanelBlockEntity.TRIP_TICKS" x="0.05" u="秒" /> 后断开 |

打开配电箱能看到供电、负载和每个回路的状态，可以单独开关回路

## 布线扳手

- 拿着扳手在方块上拖动就能铺线，像泰拉瑞亚的电线一样，线藏在方块里
- 每格消耗 1 根铜线
- 有 4 条颜色不同的回路，一个方块里可以同时走几条
- 拿着扳手时才能看到线

## 插座和电线

- 低功率电器（灯、小电器）用电源线插到插座上
- 大功率或固定设备（柴油发电机等）用钢制接线口
- 电线长度：插线板 <V j="PlugCord.STRIP_LENGTH" u="格" />，普通电器 <V j="PlugCord.APPLIANCE_LENGTH" u="格" />，接发电机 <V j="PlugCord.GENERATOR_LENGTH" u="格" />

## 灯

- 房间灯每盏耗电 <V j="BuildingLights.FE_PER_TICK" u="FE/t" />，开关控制整条回路
- **应急灯**：自带电池 <V j="EmergencyLightBlockEntity.CAPACITY" u="FE" />，有电时每 tick 充 <V j="EmergencyLightBlockEntity.CHARGE" u="FE" />，停电后自动亮起，每 tick 耗 <V j="EmergencyLightBlockEntity.DRAIN" u="FE" />（充满能亮约 5 分钟）
- 停电时灯会闪一下再灭
