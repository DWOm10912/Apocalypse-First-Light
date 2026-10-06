# Native Bullet Holes V1（弹孔）

状态（2026-10-05）：**已实现，未实机验证**，`compileJava --offline` PASS。贴图只看过离线预览。

## 为什么做

用户 2026-10-05 实测：地下油罐里只有 5000 mB 油时，打在液面以上的弹孔不漏油（这是对的，见 [fuel_fire_v1.md](../gameplay/fuel_fire_v1.md)），但打完什么都看不出来。所以给所有子弹打中的方块加上看得见的弹孔。

## 行为

- 子弹（`NativeGunShot#trace`）停在方块上时，服务端调用 `weapon/BulletImpacts.onBlock`：
  1. 先交给 `fluid/FuelLeaks.bullet`：油容器打孔、钢面火花；
  2. 再把命中点、面、方块位置发给正在看这个区块的玩家（`AflNetwork.BulletImpactS2CPacket`）。
- 客户端（`client/BulletHoles`）收到后：
  - 从弹孔里崩出 5 颗这个方块的碎屑（原版方块粒子）；
  - 在打中的面上贴一个弹孔。
- 霰弹每颗弹丸各算一次。击碎后穿过的玻璃不算，只算最后停下的那一格。
- **材质**按方块的声音类型分三种，每种三个样子随机：
  - 钢（金属、下界合金、铁砧、铜、锁链、灯笼）：黑孔，一圈往里凹的深色金属唇边，外面一圈漆被打掉、露出来的亮钢（LabPBR 金属），再外面一圈淡淡的擦痕。
  - 木头（各种木头、竹、樱花木、梯子）：黑孔，沿纹理撕开的浅色木纤维，周围一圈深色压痕。木头的弹孔只会翻转 180°，纤维始终沿面的水平方向。
  - 其它（石头、混凝土、砖、泥土等）：小黑孔，深色崩坑，一圈浅色粉尘，几道短裂纹。
- **大小**：钢 0.16 格、其它 0.18 格见方的贴图，孔和痕迹占一半多一点。真实的 9 mm 弹孔在这个尺度下看不见，所以放大了。
- **画法**：
  - 贴在面上，离面 0.002 格加上每格距离 0.0001 格，只写颜色不写深度，和油斑一样不会闪（`LiquidRenderTypes.HOLE`）；
  - 亮度取面前那一格；
  - 靠近方块边缘的弹孔往里挪，不会挂出面外；
  - 48 格内画；在油斑之前画，油流过弹孔时盖在上面。
- **保留时间**：
  - 每个客户端自己记，最多 512 个，超了先删最旧的；
  - 每个留 5 分钟，最后 30 秒淡出；
  - 方块被挖掉或变了，弹孔马上去掉；
  - 不存档，换维度或重进世界就没了。
- **油容器上的弹孔**（加油机、装油的储液罐、地下油罐）不走上面这套，而是按服务端同步的漏油孔画（`ClientFuelLeaks`）：孔在就一直画，存档保存，不淡出；漏不漏油都看得见。材质取那一格方块的。

## 贴图

- `textures/effect/bullet_holes.png`（96 × 96：3 行材质 × 3 个样子，每个 32 × 32）和 `_s`（亮钢是金属 F0 230、光滑度约 150；其它粗糙、非金属；都不发光）。
- 不染色，颜色直接画在贴图里。
- 生成器：`node tools/build-bullet-holes-v1.mjs [--check]`。

## 代码

- `weapon/BulletImpacts`、`weapon/NativeGunShot`（调用处）；
- `network/AflNetwork.BulletImpactS2CPacket`、`sendBulletImpact`；
- `client/BulletHoles`；
- `client/LiquidRenderTypes.HOLE`；
- `client/ClientFuelStains`（在它的渲染里画）、`client/ClientFuelLeaks#forEachHole`。

## 已知不足

- 没有实机验证。
- 没有弹着声音，钢面也只有服务端判定的那 25% 会冒火星（见 [fuel_fire_v1.md](../gameplay/fuel_fire_v1.md)）。
- 弹孔是平贴在方块格的面上，不贴合弧面或斜面：地下油罐、储液罐的圆角处可能悬空一点。
- 实体身上没有弹孔。
