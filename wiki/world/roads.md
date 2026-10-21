# 道路

<StatusTag s="new" />

联邦六州由两级公路连接，路面、护栏、标线和路牌都是 AFL 自己的方块

| 等级 | 车道 | 限速 |
|---|---|---|
| 州际高速 | 双向四车道 | 110 km/h |
| 州道 | 双向两车道 | 90 km/h |

- 互通立交还在做，现在高速之间还不能互相转换

## 路基和边坡

<StatusTag s="test" /> 只在测试世界里有，还没进游戏看过

- 路基、挖方和填方的边坡下面 <V j="RoadWorks.ROCK" u="m" /> 是实心的：边坡上不会露出洞口，路边不会冒出泉水
- 边坡外面的天然山坡照旧，有洞口和泉水
- 很深的挖方做成岩石边坡：陡的石面，每升 <V j="CrossSection.ROCK_LIFT" u="m" /> 留一道 <V j="CrossSection.ROCK_BENCH" u="m" /> 宽的平台；浅的挖方还是草坡
- 州际旁边特别深的挖方，边坡最外面还可能剩一段较矮的直壁

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

## 镇里的路口和标线

<StatusTag s="test" /> 只在测试世界里有，还没进游戏看过

- Linden 的路口设有停车让行标志（STOP）：T 字路口是支路一侧停车，十字路口是次要街道停车；Main St 和出口横街交叉的路口四个方向都要停车，STOP 牌下面挂 ALL WAY 小牌
- 停车让行的方向在路口前画有白色停止线，车停在线前
- Main St 和出口横街上的路口画有人行横道，宽度不小于 <V j="TownControl.WALK" u="m" />
- Main St 和出口横街画双黄中心线，不可越线；住宅街不画中心线
- 镇里的 STOP 牌比公路上的高，牌底离地 <V j="RoadSigns.TOWN_BOTTOM" u="m" />，行人从牌下经过不会碰头
- 进镇的路上，城界牌往里 <V j="TownSigns.SPEED_IN" u="m" /> 立着限速牌：SPEED LIMIT <V j="TownSigns.SPEED_LIMIT" /> mph
- 城界牌第三行是镇上的人口（POP）
- 街名牌上，街名后面的小字是这一段的门牌号段，例如 6th St 路口写 600
- 这些路牌和公路上的一样，可以用撬棍拆下

## 开车

见 [T15](../vehicles/t15)
