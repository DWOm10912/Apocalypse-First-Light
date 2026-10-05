# 耐热流体设备（Heat-Resistant Fluid Set V1）

状态（2026-10-05）：**已实现，用户实机 PASS**：用户看过外观，也看过耐热泵抽岩浆、经耐热管道灌进耐热储罐。之后改了一处：耐热管道贴地时的管卡卡箍改成橙色，这处还没有实机验证。普通设备被烧毁没有单独确认。

## 是什么

一套专门输送高温液体（岩浆，以及以后其他超过 400 K 的液体）的设备，用户 2026-10-05 定下：
- 三样一起做，沿用普通设备的骨架，换材料和配色，改少量模型；
- 命名用"耐热 xx"；
- 泵用耐高温银漆；
- 高温储罐也能合并，上限和普通储罐一样（5 × 5 × 16）；
- 普通设备也能装高温液体，但会被烧毁：管道、泵、储罐都一样（以后在合适的地方加提示）。

| 注册 ID | 名称 | 基于 |
|---|---|---|
| `apocalypse_firstlight:heat_resistant_fluid_pipe` | 耐热流体管道 / Heat-Resistant Fluid Pipe | [Fluid Pipe V2](fluid_pipe_v2.md) |
| `apocalypse_firstlight:heat_resistant_fluid_tank` | 耐热流体储罐 / Heat-Resistant Fluid Tank | [Fluid Tank V2](fluid_tank_v2.md) |
| `apocalypse_firstlight:heat_resistant_intake_pump` | 耐热取液泵 / Heat-Resistant Intake Pump | [Intake Pump V1](intake_pump_v1.md) |

代码沿用普通设备的类，用一个"耐热"标记区分：`FluidPipeBlock(props, true)`、`FluidTankBlock(props, true)`、`IntakePumpBlock(props, true)`；方块实体类型和普通款共用（`fluid_tank`、`intake_pump`）。材料方面用的是这一轮新做的耐热陶瓷和石英玻璃（见 [material_system_v1.md](../gameplay/material_system_v1.md)），**还没有配方**。

## 规则

- **高温液体**：温度超过 400 K 的液体（`fluid/FluidHeat`；岩浆 1300 K）。
- **耐热设备**：什么液体都能装、能抽、能送，包括岩浆。
- **普通设备遇到高温液体会被烧毁**（`FluidHeat.melt`）：方块直接消失，不掉落，伴随岩浆遇水的"嗤"声和烟。里面的液体丢失。
  - 普通管道：高温液体经过以后，这条路线上的普通管道全部烧毁（液体已经送到终点）。
  - 普通储罐：装进高温液体后，下一 tick 起一格一格地烧毁，合并的大罐会整罐烧掉。
  - 普通取液泵：开着、有电、下面是高温液体源时，烧毁（两格一起）。关着时 Jade 显示"液体温度过高：开泵会烧毁普通泵"。
- **两种设备互不相连**：普通管道和耐热管道挨着放不会连上；普通储罐和耐热储罐挨着也不会合并。它们共用同一个标准流体接口，所以耐热管道可以接普通储罐（会把它烧掉），普通管道也可以接耐热储罐。
- **液池规则**：耐热泵抽岩浆也要 3 × 3 × 1 以上的岩浆池，抽不完（`PumpSourceRules`）。
- 其他行为（容量、流速、耗电、开关、Jade、提示、声音）和普通款一样。

## 外观

| 设备 | 改动 |
|---|---|
| 管道 | 玻璃换成石英玻璃（更透：透明度 40，普通款 56）；每个法兰口里有一圈米白色的耐热陶瓷内衬（从 ±4.0 到 ±3.5 px）；箍带和它的耳板换成橙色高温色环 [196,108,32]；贴着地面或墙时那一格画的是管卡，管卡的卡箍也是橙色，支脚和底座仍是镀锌色（2026-10-05 用户实机发现贴地的管道只有灰白的卡箍，补上）。生成：`node tools/build-fluid-pipe-v2.mjs --heat-resistant`，输出 `models/block/heat_resistant_fluid_pipe/*`、`textures/block/heat_resistant_fluid_pipe{,_s,_n}.png` |
| 储罐 | 玻璃换成石英玻璃；接口板上每个口外面一圈耐热陶瓷内衬（±4.0 到 ±4.9 px）；顶板下面一道橙色高温警示条（4 个新部件 `band_<面>`，这一面没相连且上面没相连时显示）。生成：`node tools/build-fluid-tank-v2.mjs --heat-resistant` |
| 取液泵 | 机身换成耐高温银漆 [168,170,172]（F0 40，非金属，避免在光影下反光过强）；竖直吸液管和滤网换成耐热陶瓷。生成：`node tools/build-intake-pump-v1.mjs --heat-resistant` |

可编辑源：`src/main/blockbench/heat_resistant_fluid_pipe_v2.bbmodel`、`heat_resistant_fluid_tank_v1.bbmodel`、`heat_resistant_intake_pump_v1.bbmodel` 及各自的贴图。耐热陶瓷 [194,183,160]、石英玻璃 [205,226,232] 的颜色和材料物品一致。

## 其他

- 挖掘：和普通款一样，镐 + 钻石级，需要对的工具才掉落。
- 负重（估计）：管道 0.63 kg、储罐 37.5 kg、泵 56.25 kg（普通款的 1.25 倍，多了内衬）；储罐和泵在 `carry/oversized`。
- 创造标签页 `industry`：取液泵之后依次是耐热流体管道、耐热流体储罐、耐热取液泵。

## 已知问题 / 以后

- 没有实机验证。
- 普通设备被烧毁之前没有提示（除了普通泵关着时的 Jade 行）；用户说以后在合适的地方加。
- 现有存档里装着岩浆的普通储罐、普通管道，载入后会被烧毁。
