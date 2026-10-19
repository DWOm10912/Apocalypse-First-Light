# 道路

<StatusTag s="new" />

联邦六州由两级公路连接，路面、护栏、标线和路牌都是 AFL 自己的方块

| 等级 | 车道 | 限速 |
|---|---|---|
| 州际高速 | 双向四车道 | 110 km/h |
| 州道 | 双向两车道 | 90 km/h |

- 互通立交还在做，现在高速之间还不能互相转换

## 隧道

- 山体挡路时公路会钻隧道，净高 5.1 m
- 隧道里没有灯，开车记得开大灯
- 洞口有名字牌
- 隧道里的灯具、标志等部件可以用撬棍拆下来

## 县道和老宅车道

<StatusTag s="test" /> 只在测试世界里有

- 县道是双向两车道的乡下沥青路，从溪谷老宅附近路过，两头接 Linden 的街道或者州道
- 县道接州道的地方是 T 字路口，县道这边立着 STOP 牌
- 老宅有一条碎石车道，沿着溪走 <V j="RoadCounty.DRIVE_MIN" />–<V j="RoadCounty.DRIVE_MAX" u="m" />，从侧面接到县道路边，路口没有 STOP 牌
- 车道最陡 <V j="RoadCounty.DRIVE_GRADE" pct />，快到县道的那一段很缓，路口两边看得远
- 县道一头接不上别的路时，过了车道口再走约 <V j="RoadCounty.DEAD_END_RUN" u="m" /> 就到头了

<StatusTag s="planned" /> 车道口的管式大门、信箱和 PRIVATE DRIVE 牌，县道尽头的 ROAD ENDS 路障：位置定了，东西还没放

## 开车

见 [T15](../vehicles/t15)
