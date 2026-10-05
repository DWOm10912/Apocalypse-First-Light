# Fuel Fluids V1（汽油、柴油）

状态（2026-10-04）：**已实现**，`compileJava --offline` PASS。第一次进游戏两种油都是紫黑缺失贴图，有两个原因，都已修（修后用户实机确认显示正常，材质"还行"，2026-10-04）：一是贴图没加进方块图集；二是 `FuelFluidType` 的贴图路径在 Forge 调用 `initializeClient` 时（`FluidType` 构造函数里，子类字段还没赋值）就拼好了，变成 `fluid/null_still`，现在改为调用时再读液体名和雾色（雾色原来也会在人泡进油里时变成空指针）。水下雾、光影下的 PBR、管道里的显示没有单独的检查记录。

## 是什么

加油站 V2 燃油系统用的两种液体。用户 2026-10-04 定下：
- 属性和颜色交给我定；
- 不做桶装：这类东西只能用容器运，平时不会像水那样倒在地上；
- 加一个指令来生成液体源方块；
- 材质重新设计，能做 PBR 就做。

| 液体 | 注册 ID（静止 / 流动 / 方块） | 名称 | 颜色 |
|---|---|---|---|
| 汽油 | `apocalypse_firstlight:gasoline` / `flowing_gasoline` / 方块 `gasoline` | 汽油 / Gasoline | 清亮的金黄色，偏透明，表面有很淡的油膜虹彩 |
| 柴油 | `apocalypse_firstlight:diesel` / `flowing_diesel` / 方块 `diesel` | 柴油 / Diesel | 更深的琥珀色，更不透明 |

## 属性

代码：`fluid/FuelFluidType`、`fluid/FuelFluid`，注册在 `registry/AflFluids`；液体方块是 `LiquidBlock`，属性复制自水。

| | 汽油 | 柴油 |
|---|---|---|
| 密度（kg/m³） | 740 | 840 |
| 黏度（水 1000） | 600 | 2400 |
| 推动实体 `motionScale`（水 0.014） | 0.016 | 0.010 |
| 流动 | `tickRate` 4，每格减 1，找坡 5 格（比水快、略远） | `tickRate` 10，每格减 2，找坡 3 格（慢、流不远） |
| 水下雾 | 颜色 0xC9A64A，5 格内看不清 | 颜色 0x8A5E1C，3 格内看不清 |

两种都有的属性：
- 不会生成新的源方块；
- 不能灭火；
- 不能湿润耕地；
- 能游泳、会溺水、能划船；
- 摔落伤害清零（同水）；
- 温度 295 K；
- 不发光；
- 没有环境粒子和滴落粒子。

还**没有**做：易燃、遇火爆燃、污染。

## 怎么拿到

AFL 统一 1 mB = 1 升（2026-10-04），汽油、柴油的重量按密度算：每 mB 0.74 / 0.84 kg。

- **没有桶**，只在容器里：地下油罐、管道，以后的加油机。
- **取液泵**（2026-10-05）能把汽油、柴油的源方块抽进管道，每抽 1,000 mB 用掉一格源方块，燃油不会再生，见 [intake_pump_v1.md](../models/intake_pump_v1.md)。
- **开发指令**（`src/dev`，OP 2）：
  - `/dev fuel source <gasoline|diesel>`：在看着的那个面前面放一格源方块（看着的方块本身可替换时就放在那一格）；
  - 原版 `/setblock <位置> apocalypse_firstlight:gasoline` 也可以；
  - 地下罐用 `/dev fuel fill`，见 [underground_fuel_tank_v1.md](../models/underground_fuel_tank_v1.md)。

## 材质

| 项 | 内容 |
|---|---|
| 生成器 | `tools/build-fuel-fluids-v1.mjs`（`--check`；`--preview DIR` 出第一帧对照图） |
| 贴图 | `textures/fluid/{gasoline,diesel}_{still,flow}.png` 加 `.mcmeta`。静止 16×16、流动 32×32，各 32 帧；汽油每帧 2 tick，柴油 3 tick。流动贴图每帧往下走 1 px（一圈走完一格） |
| 画法 | 像原版液体那样的像素风：用整数频率的正弦波叠成能无缝平铺、首尾帧衔接的平滑起伏，量化成 5 个色阶，不加逐像素噪点。汽油最亮的两个色阶混一点淡紫和淡青，模拟油膜虹彩 |
| 颜色（暗→亮，含不透明度） | 汽油 [214,176,70] 不透明度 138 → [250,228,152] 156；柴油 [146,94,24] 178 → [210,158,66] 188 |
| PBR | `_s`：光滑度汽油 226、柴油 212，F0 6（液体约 0.02），无孔隙，无自发光。`_n`：由起伏的梯度算出的轻微波纹法线，AO 255，高度 255 |
| 方块模型 | `blockstates/{gasoline,diesel}.json`、`models/block/{gasoline,diesel}.json`（只给破坏粒子用静止贴图，液体本身由原版液体渲染器画），同一个生成器写出 |
| 渲染层 | 半透明（`client/AflBlockRenderTypes`，静止和流动都设） |
| 颜色放在顶点色里（2026-10-04） | 液体的颜色是它的顶点色（`getTintColor`）：汽油 0xFAE4A2，柴油 0xD29F44；贴图存的是"颜色 ÷ 顶点色"（生成器取两张贴图各通道的最大值作顶点色，再把贴图除以它），所以原版和 Complementary 里贴图 × 顶点色还原出上表的颜色。原版的水也是这样做的。原因：Sundial Lite 把没有登记的半透明方块当成彩色玻璃（只给后面的东西染色，自己不受光、不显示颜色），默认下汽油、柴油、工业废液都看不见（用户截图）；它的"模组液体检测"选项（`MOD_WATER_DETECTION`，默认关）会把**有顶点色**的这类液体当成水来画，颜色取顶点色、带它自己的波浪和反射，但不用贴图的花纹。所以在 Sundial Lite 里要打开这个选项。生成器 `--check` 会核对 `AflFluids` 里的顶点色和贴图一致。改完没有实机验证 |
| 图集 | 四张贴图要列在 `assets/minecraft/atlases/blocks.json` 里（`minecraft:single`，和工业废液一样）。`textures/fluid/` 不在原版方块图集的扫描目录里，第一版漏了这一步，进游戏是紫黑缺失贴图（2026-10-04 用户截图，已补）。`_s` / `_n` 由光影按同名自动加载 |

管道和储罐里的液体用的是同一套静止 / 流动贴图（`FluidRenderHelper`）。
