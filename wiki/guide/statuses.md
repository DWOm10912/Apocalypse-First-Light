# 状态效果

左下角生存模块上方排列着一列圆形状态图标，只在状态生效时显示：绿色是 BUFF，棕色是 DEBUFF，蓝色表示寒冷和潮湿，红色表示危险。越紧急的状态离模块越近。获得新状态（或状态加重）时，图标边框闪一下，旁边滑出状态名称和效果，显示 4 秒。有时限的状态（肠胃感染、沾染汽油 / 沾染柴油）外圈有一道弧线，旁边显示剩余时间

- <StatusTag s="verified" d="2026-10-17" /> 状态图标的显示
- <StatusTag s="integrated" /> 下面的效果是目前的测试值，之后还会调整。各状态的触发数值见[生存状态](./survival)、[背包与负重](./inventory)和[辐射](./radiation)

## DEBUFF

<StatusTable kind="bad" />

## BUFF

<StatusTable kind="good" />
