# Forest Home Sounds V1（溪谷老宅音效 V1）

状态（2026-10-15）：
- **已实现**：用户的 Firefly 素材接进游戏，替换老宅设施原来的原版占位声音；响度统一，按动画对时，每个声音的传播半径等于它的感染者噪声半径。
- `compileJava` / `compileDevJava` 通过，资源引用检查通过（`sounds.json` 里 191 个 AFL 声音文件都在）。**用户 2026-10-15 实机听过：PASS**（"我听完了，没问题，pass"）。
- 范围（用户 2026-10-14）："枪架、进线盒盖不好做，暂时不换"；"B的目前不补，后面精修加油站再说"。信箱门没有素材，还是原版声音。

## 素材和生成器

- 素材：`E:/Download` 里的 21 个文件（2026-10-14，立体声 48 kHz），生成器按 SHA-256 前 16 位核对，素材变了会报错。
- 生成器：`tools/build-forest-home-sounds-v1.mjs [素材目录]`（默认 `E:/Download`，要 ffmpeg），用 `tools/sound-mix-lib.mjs`；输出是单声道 Ogg Vorbis（q6，48 kHz），在 `src/main/resources/assets/apocalypse_firstlight/sounds/` 下。
- 这次给 `sound-mix-lib.mjs` 加了三样：`buildSounds` 的 `prepare`（解码后先处理一遍，这里是高通）、导出的 `highPass(x, fc)`（4 阶巴特沃斯）、每层的 `fadeIn`（从一段连续声音中间切出来时的淡入）。

## 响度

- 单次声音：最响的 400 ms 瞬时响度对齐金属垃圾桶开盖声（-24.3 LUFS），再按角色加偏移；采样峰值不超过 -1 dBFS（超过就整体压低，报告里标 `peakLimited`）。
- 循环：对齐便携柴油机运转循环（-26.8 LUFS）减 3–6。
- 游戏里都用音量 1 播，文件本身就是响度。
- 门和开关类先高通（柴炉关门那条有一半能量在 80 Hz 以下，一直拖到被切断的结尾），砍树和倒树留着低频。

| 输出 | 素材 | 处理 | 偏移 | 结果（M400 / 峰值） |
|---|---|---|---:|---|
| `box_stove/door_open` | 柴炉开门 | 高通 110 Hz，0.32–0.55 s 淡出 | 0 | -24.4 / -11.7 |
| `box_stove/door_close` | 柴炉关门 | 高通 110 Hz，0.36–0.60 s 淡出 | 0 | -24.7 / -6.4 |
| `box_stove/add_wood` | 添柴 | 高通 60 Hz，0.6–0.95 s 淡出 | -2 | -26.4 / -6.4 |
| `emergency_radio/switch_on`、`switch_off` | 收音机开关（前一下、后一下） | 高通 100 Hz，在 0.5–0.64 s 之间分开 | -8 | -32.5 / -2.4、-6.6 |
| `emergency_radio/crank_1..8` | 摇柄 1、2 | 每条素材切 4 段 0.2 s（4 ms 淡入、30 ms 淡出） | -9 | -33.4..-33.7 |
| `wood_cabinet/open`、`close` | 厨房柜门开、关 | 高通 60 / 50 Hz | -3 | -28.6（压峰）、-27.4 |
| `lamp_switch/pull_chain_1`、`_2` | 拉线灯座（两下） | 高通 100 Hz，在 0.55–0.72 s 之间分开 | -7 | -32.9（压峰）、-31.4 |
| `lamp_switch/wall_switch` | 墙面开关 | 高通 100 Hz | -7 | -31.3 |
| `power_plug/plug_in`、`plug_out` | 插头插入、拔出 | 高通 100 Hz | -8 | -32.4、-32.3 |
| `rural_mailbox/flag_up`、`flag_down` | 信箱红旗（前一下、后一下） | 高通 80 Hz，咔哒落在 0.42 s | -4 | -28.4、-28.5 |
| `tube_farm_gate/open` | 农场大门开 | 高通 60 Hz | +2 | -22.5 |
| `tube_farm_gate/close` | 农场大门关 | 高通 50 Hz，"哐"落在 1.15 s | +2 | -22.6 |
| `tree_felling/chop_1..3` | 砍树（三斧） | 高通 40 Hz，每斧一段 | +3 | -21.3..-21.4 |
| `tree_felling/crack` | 树倒下 0–2.75 s | 高通 30 Hz | +1 | -24.5（压峰） |
| `tree_felling/crash` | 树倒下 2.75 s 到结尾 | 高通 30 Hz | +5 | -19.4 |
| `box_stove/fire_loop` | 炉火循环 | 12 s 循环，1 s 等功率交叉淡化，高通 40 Hz | -6 | -32.7 |
| `box_stove/fry_loop` | 煎炒 | 同上 | -3 | -29.9 |
| `box_stove/boil_loop` | 烧水 | 同上 | -4 | -30.8 |
| `emergency_radio/static_loop` | 收音机杂音 | 同上 | -5 | -32.0 |

## 对时

- 柴炉门、柜门的模型是一下子换的，声音从头开始（开头最多 0.1 s 的轻声）。
- 信箱红旗 10 tick 转到位，咔哒在 0.42 s。
- 大门转 24 tick（1.2 s）：开门的铁链和转动声从头开始；关门先一下轻碰，"哐"在 1.15 s，门扇停住的时候。
- 收音机摇柄每用一下转四分之一圈（4 tick），每下 0.2 s 棘轮声，8 段随机。
- 砍树每 10 tick 一斧，每段不到 0.9 s；最后一刀放断裂声（2.7 s，盖住倒下的过程），倒地时放倒地声。

## 半径（声音 = 噪声）

全部走 `noise/RangedSound`：声音只发给半径内的玩家，线性衰减到半径处听不到；同一下发一个同半径的感染者噪声（新加的 `RangedSound.playWithNoise`）。`sounds.json` 的 `attenuation_distance` 也写成同一个数（循环靠它）。

| 声音 | 半径 | 噪声类型 | 原来 |
|---|---:|---|---|
| 柴炉开门、关门 | 4 | INTERACTION | 原版铁活板门声，16 格，没有噪声 |
| 添柴 | 3 | INTERACTION | 原版木头放置声，16 格 |
| 炉火循环（烧着时） | 4 | MACHINE，每 40 tick | 随机营火噼啪声，48 格内本地放（已删掉） |
| 烧水循环（炉上有脏水在煮） | 6 | MACHINE，每 40 tick | 没有 |
| 煎炒循环（炉上有别的东西在煮） | 8 | MACHINE，每 40 tick | 没有 |
| 收音机开关 | 2 | INTERACTION | 原版拉杆声 |
| 收音机摇柄 | 3 | INTERACTION | 原版弩上弦声 |
| 收音机杂音（开着且有电） | 12 | MACHINE，每 40 tick | 没有 |
| 厨房柜、水槽柜开关 | 3 | INTERACTION | 原版木桶声，16 格 |
| 拉线灯座 | 2 | INTERACTION | 原版拉杆声 |
| 墙面开关 | 2 | INTERACTION | 原版拉杆声 |
| 插头插上、拔出 | 2 | INTERACTION | 原版石按钮声 |
| 信箱红旗 | 3 | INTERACTION | 原版铁活板门声 |
| 信箱门（原版铁活板门声，没有素材） | 4 | INTERACTION | 16 格，没有噪声 |
| 农场大门开关 | 8 | INTERACTION | 原版铁门声 + 铁链声 |
| 农场大门换牌（原版铁链放置声） | 3 | INTERACTION | 16 格 |
| 砍一斧 | 10 | BLOCK_BREAK | 原版木头破坏声，没有噪声 |
| 树干断裂 | 12 | BLOCK_BREAK | 原版僵尸破门声 |
| 大树倒地 | 24 | BLOCK_BREAK | 原版三个声音叠加 |

- 一个炉子可以同时放炉火和烧水 / 煎炒；服务端每 40 tick 只发最响的那个噪声。
- 收音机杂音在 V1 只是底噪，还没有调频和广播（[应急收音机 V1](../models/emergency_radio_v1.md)）。
- 炉火循环开门关门一样响（没有按门的状态变音量）。

## 代码

- 事件：`registry/AflSounds`（`BOX_STOVE_*`、`EMERGENCY_RADIO_*`、`WOOD_CABINET_*`、`LAMP_PULL_CHAIN`、`WALL_SWITCH_CLICK`、`POWER_PLUG_*`、`RURAL_MAILBOX_FLAG_*`、`TUBE_FARM_GATE_*`、`TREE_CHOP`、`TREE_CRACK`、`TREE_CRASH`），变体在 `sounds.json`（摇柄 8、拉线 2、砍树 3）。字幕在 `lang` 的字幕段，便携柴油机后面。
- 半径常量放在各自的类里，声音和噪声共用：`BoxStove.DOOR_RADIUS` 等、`EmergencyRadio.SWITCH_RADIUS / CRANK_RADIUS / STATIC_RADIUS`、`WoodCounterBlockEntity.DOOR_RADIUS`、`PorcelainLampholderBlock.PULL_CHAIN_RADIUS`、`WallSwitchBlock.CLICK_RADIUS`、`PowerPlugs.PLUG_RADIUS`、`RuralMailboxBlock.FLAG_RADIUS / DOOR_RADIUS`、`TubeFarmGateBlock.SWING_RADIUS / SIGN_RADIUS`、`FellingServer.CHOP_RADIUS / CRACK_RADIUS / CRASH_RADIUS`。
- 循环：`client/BlockLoopSoundController` 加了 4 个来源，每个淡入 10 tick。原来一个方块只能放一个循环（按位置记），现在按"位置 + 来源"记，一个炉子能同时放几条。
- 炉子的"在煮什么"：`BoxStoveBlockEntity#boiling()` / `#frying()`（烧着、某个位置有东西还没熟；脏水 `ThirstWaterItem` 算烧水，别的算煎炒），客户端和服务端共用。

## 没做的

- 枪架、发电机进线盒盖、信箱门：没有素材，还是原版声音（枪架、进线盒盖是用户定的）。
- B（加油站、商业设施）：等精修加油站时再补。
- 点火（打火石、火焰弹）、往炉上放取东西还是原版声音。
