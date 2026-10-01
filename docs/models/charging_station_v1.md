# Charging Station V1（充电站，第一台 Mesh 机器）

状态（2026-10-01）：**已实现，用户实机检查 PASS**（光影下，接两路线缆）。`compileJava --offline` 一次 PASS。属性见 [机器.md 第 9 节](../项目内容/01%20-%20设计/方块/机器.md)。

## 设计

- 落地式钢制充电台，2 格长、1 格宽、1 格高。没有 GUI：东西放到托盘上就开始充，取下就停。
- **一次只充一个物品**，不分物品种类：小电池和以后的长条充能武器都放在托盘正中。
- 只从能源线缆取电，只给物品充电；不向网络放电，不从物品里抽电。和能量单元（存电、应急供电）不重叠。
- 机器画风：中灰钢机壳、深炭黑端架和腿、凹进去的面板、橙色点缀，表面素净、不画字。

## 方块

| 项目 | 值 |
|---|---|
| Registry ID | `apocalypse_firstlight:charging_station`（方块、物品、方块实体同名） |
| 占地 | 2 × 1 × 1。放置时玩家面对的方向是正面；主格是玩家看过去的**左格**，另一格在右边 |
| 放置条件 | 两格都可替换、无液体、没有实体挡着，两格下方都是实心顶面 |
| 挖掘 | 镐，钻石级（`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool`），`requiresCorrectToolForDrops`，属性照铁块（硬度 5），金属音效 |
| 掉落 | 拆任一格：另一格一起消失，掉一台充电站（`survives_explosion`）；托盘上的物品单独掉落；内部缓冲的 FE 丢失 |
| 地面 | 任一格下方失去支撑时整台被破坏并掉落 |
| 活塞 | 不能推动 |
| 创造模式 | 工业与工作站页，电缆之后 |

## 供电

| 项目 | 值 |
|---|---:|
| 内部缓冲 | 10,000 FE |
| 最大输入 | 256 FE/t（两个接口共用） |
| 充电速率 | 128 FE/t，同时受物品自己的接收上限限制 |
| 损耗 / 待机耗电 | 无 |

- 每格背面正中一个标准电源接口（共两个），都通到主格的缓冲。同一个线缆网络同时接两个接口时只算一个用电端，不会多分电。
- 数值在 `data/apocalypse_firstlight/machine_balance/charging_station.json`（`capacity_fe`、`max_receive_fe_per_tick`、`charge_fe_per_tick`），缺失或无效时用同样的默认值。
- 50,000 FE 的能量电池从空充满约 390 tick（约 20 秒）。线缆供电不足 128 FE/t 时，按实际到手的电量充。

## 托盘

- 右键（主手优先，其次副手）：托盘空着时，手里的物品有 Forge Energy 能力并且能接收电量，就放上去一个；托盘上有东西时，空手右键取回物品栏（物品栏满了掉在脚下）。
- 不能充电的物品放不上去。
- 准星提示（画在托盘上方）：
  - 拿着可充电物品："放上充电"；
  - 拿着别的东西："无法充电"；
  - 托盘上有物品且有一只手空着："取下（36,500 / 50,000 FE）"，数值是同步到客户端的值，按整数百分比更新。
- 物品平躺在托盘中间，正面朝上；抬高量按能量电池的半径定（约 2.1 px），薄的二维物品会略微悬空。

## 显示

- **有电**：缓冲大于 0，或者最近 1 秒内收到过电。
- 有电时，机架上十个橙色指示灯亮（全亮度，光影下带 LabPBR 自发光）；没电时是暗橙色。
- 有电且托盘上有物品时，正面的长电量条从玩家看过去的左边往右填，读数屏显示 `NN%`；满了显示 `FULL`，电量条和读数从琥珀色变绿色。没电时两者都不亮。
- 电量条和读数按整数百分比同步（每过一个百分点才发一次包），平时不发包。

## 模型与渲染

- 生成器：`tools/build-charging-station-v1.mjs`。不带参数时写全部输出；`--check` 检查输出是否过期；`--preview DIR` 只写预览文件。
- 输出：
  - `src/main/blockbench/charging_station.bbmodel` 和 `src/main/blockbench/textures/charging_station{,_s,_n}.png`；
  - 运行时 `geo/charging_station.geo.json`、`meshes/charging_station.aflmesh.json`、`block_mesh_profiles/charging_station.json`、`textures/block/charging_station{,_s,_n}.png`；
  - `models/item/charging_station.json`（builtin/entity）、`models/block/charging_station.json`（只有粒子贴图）、`blockstates/charging_station.json`。
- 1784 个三角面（body 1144，两套指示灯各 320，同一时刻只画一套），0 处共面重叠，贴图 512²，约 4.75 texel/px。所有材质都是涂层（F0 20，非金属）。
- 骨骼：
  - `body`；
  - `lamps`（暗灯）；
  - `lamps_lit`（亮灯，贴图 `_s` 的 alpha 为 200 表示自发光；geo 里标了 `neverRender`，所以物品模型只显示暗灯）。
- 贴图右下角 500..508 留了一块白色自发光像素（`_s` alpha 230），动态电量条的四边形采样它，再乘顶点颜色。
- 渲染：
  - 方块不烘焙（`RenderShape.INVISIBLE`），由主格的方块实体渲染器 `client/ChargingStationRenderer` 画。它先用通用的 `AflAnimatedBlockMeshRenderer` 画机身，再画托盘物品和正面显示。
  - 显示用全亮度；读数用游戏字体（`POLYGON_OFFSET`）；阴影 pass 不画显示。
  - 亮灯 / 暗灯用 Animated Block Mesh Runtime 新增的 `meshPartVisible` / `meshPartEmissive` 选择。
- 背面接口：每格背面正中一块 6 × 6 px 钢板，从机壳背面（z 7.38）伸到方块边界（z 8），中间一个 r 1.95 的圆插座和触点，符合 [电源接口规格](power_cable_v2.md)。
- 选中框和碰撞是简化的盒子组合：机柜连托盘、后挡板、横撑，以及两端的端架和腿；端架斜顶做了一级台阶。

## 声音（2026-10-01，用户实机 PASS）

**可听半径**（2026-10-01）：在 `sounds.json` 里用 `attenuation_distance` 设定（原来是原版默认的 16 格），声音随距离线性变小，到半径处听不到；播放音量都不超过 1，所以半径就是实际范围。开始充电 8 格，充满 12 格，充电中的嗡嗡 8 格。放上 / 取下用原版皮革装备声，保持原版的 16 格。

| 声音 | 什么时候 | 来源 |
|---|---|---|
| 放上 / 取下 | 托盘放上或取下物品，从托盘位置发出 | 原版皮革装备声 `item.armor.equip_leather`（用户选的），音高 0.97–1.03 随机 |
| 开始充电 `charging_station_start` | 开始充电的那一刻：有电时放上没满的物品，或者托盘上有没满的物品时来电；和放上声同时播放，两声"滴"落在 0.12 s 和 0.30 s，跟在放下声后面 | `sounds/charging_station/start.ogg` |
| 充满 `charging_station_full` | 物品充到 100% 时响一次；有电时放上一个本来就满的物品也会响 | `sounds/charging_station/full.ogg` |
| 充电中 `charging_station_hum` | 有电、托盘上有物品、还没满时循环播放，从充电站中间发出，8 格内听得到（`attenuation_distance` 8，线性衰减）；停止充电、方块没了或玩家走出 9 格就停 | `sounds/charging_station/hum.ogg`，客户端 `client/ChargingStationSoundController` 每 5 tick 扫一次附近区块里的充电站 |

开始和充满两声从正面控制条发出，服务端播放，附近玩家都听得到。字幕：充电站开始充电 / 充电站：充电完成 / 充电站嗡嗡作响。

- 构建脚本：`tools/build-charging-station-sounds-v1.mjs`（素材在 `E:/Download`，按 SHA-256 前缀校验）。三个素材都是用户生成的 1 秒 48 kHz 立体声 WAV，左右相关性 0.998–0.999，合成单声道。
- **开始音**：素材只有一个 80 ms、6.26 kHz 的极尖短音（前面带一点 12.5 kHz 的咔哒），也不是要的"两声上扬"。用同一个素材叠两次，分别降到 2.8 kHz 和 3.8 kHz（播放速率 0.45 / 0.6，时长按同样比例变长），做成两声上扬的"滴-滴"。比电箱开门声低 8 LU。
- **充满音**：素材是 932 / 621 / 932 Hz 三个短音加一个 697 Hz（带八度）的"叮"。裁掉开头 0.06 s 里的 5.3 kHz 咔哒，其余不动。比电箱开门声低 2 LU。
- **嗡嗡声**：素材音色稳定（50 Hz、149 Hz 基频和谐波），但音量一秒内涨了 18 dB，线圈高频声跟着变大，文件在最响处直接结束，用户听到结尾有炸音。只用前面平稳的 0.02–0.70 s：
  - 按每个 20 ms 周期把音量拉平；
  - 剪成 30 个整周期（0.6 s），开头 4 个周期和结尾之后的延续交叉淡化，接缝两边相位一致。
  - 这个处理放在 `tools/sound-mix-lib.mjs` 新加的 `buildLoop` 里，以后别的循环音也能用。
  - 比电箱开门声低 16 LU。
- **循环核对**：用和 Minecraft 1.20.1 `OggAudioStream` 一样的 stb_vorbis 逐帧解码。
  - 解出来正好 28,800 个采样（0.6 s），和设计长度一致。ffmpeg 解码少 128 个，是 ffmpeg 自己的处理，不影响游戏。
  - 首尾跳变 0.00011，比内部采样步长的中位数 0.0002 还小；最后一个周期和第一个周期的差异 0.086，小于内部相邻周期的中位数 0.097；30 个周期的音量都在 ±0.35 dB 以内。

## 代码

| 文件 | 内容 |
|---|---|
| `block/ChargingStationBlock.java` | 两格结构（放置、校验、拆除）、托盘右键、形状、电源接口（默认背面）、坐标换算 `sourceToWorld` |
| `blockentity/ChargingStationBlockEntity.java` | 缓冲、充电、显示状态同步；右格的方块实体把接口转给主格；Mesh 宿主（灯的显示和全亮度） |
| `item/ChargingStationBlockItem.java` | 两格放置，物品渲染 |
| `client/ChargingStationRenderer.java` | 机身、托盘物品、电量条、读数 |
| `client/WorldInteractionHint.java` | 托盘提示 |
| `client/ChargingStationSoundController.java` | 充电中的嗡嗡循环 |
| `registry/AflSounds.java`、`sounds.json` | `charging_station_start` / `_full` / `_hum` |
| `energy/MachineBalanceManager.java` | `ChargingStationBalance` |

## 实机检查要点（2026-10-01 PASS）

1. 放置、朝向、两格的选中框和碰撞，拆除和掉落（生存模式，钻石镐）；
2. 线缆接到两个背面接口时插头和接口板是否对齐；断开、接上时的状态变化；
3. 电池平躺在托盘上的位置和大小；
4. 电量条、读数的位置、方向和亮度，光影下指示灯和电量条的自发光；
5. 提示文字和位置；
6. 物品栏、手持、展示框里的图标朝向。

## 没做的

- 合成配方（等机器都做完再统一定）。
- Jade 显示。
- 长条充能武器的专门摆放（以后加物品标签，再定大小和朝向）。
