# Cash Register V2（收银机 V2：Mesh + 可拉出的钱箱）

状态（2026-09-30）：**已实现，未实机验证**。`compileJava --offline` 一次 PASS（包括 `src/dev` 里改过的 GameTest），GameTest 按规则没有运行。外观只看过离线预览：生成器的 `--preview` 输出，用一个简单的平光渲染器渲染。游戏里的效果、四个朝向、钱箱动画、3×3 搜索界面和 PBR 都还没看过。音效（2026-09-30 后加）也没有实机试听。

V2 取代 V1 文档（原 `docs/cash_register_model.md`，已删除）。Registry ID、物品、挖掘规则、方块掉落表都不变。V1 的方块源模型 `src/main/blockbench/afl_cash_register.bbmodel` 及其贴图、预览图、构建脚本 `tools/build-cash-register.bb.js` 和导出脚本 `tools/export-cash-register.mjs` 已删除，因为 V2 由新生成器从头生成。

## 方块与交互

| 项目 | 值 |
|---|---|
| Registry ID | `apocalypse_firstlight:cash_register`，不变 |
| blockstate | `facing`（north/east/south/west，收银员一侧：钱箱和键盘朝向这一面）、`open`（默认 `false`） |
| 放置 | 放在结实的顶面上，钱箱朝向放置的玩家（和 V1 一样）；下面的支撑没了会掉落收银机，钱箱里的东西一起掉 |
| 方块实体 | `CashRegisterBlockEntity`：`RandomizableContainerBlockEntity`，9 格（钱箱） |
| 渲染 | AFL Animated Block Mesh Runtime（`ENTITYBLOCK_ANIMATED`），骨骼 `body`、`drawer`。通道 `open`：6 ticks（0.3 秒），`linear`，钱箱沿正面方向滑出 8 px（0.5 格）。开和关都是匀速运动，最后一帧突然停住，和音效里的撞击对齐。最初用的是 `ease_out`，接音效时改成了匀速 |
| 搜索 | 接入 Progressive Container Search，3×3 发射器式布局（`AflContainerSearchLayout.GRID_3X3`），40 ticks/格，±15%，没有翻找噪音 |
| 挖掘与掉落 | 不变：硬度 2.5、抗性 4、`SoundType.METAL`、`requiresCorrectToolForDrops()`、`mineable/pickaxe` + `needs_iron_tool`（零售设备，不走工业设备的钻石级默认）。铁镐及以上掉收银机（掉落表 `loot_tables/blocks/cash_register.json`，爆炸按存活率） |
| 内容物 | 所有移除路径（挖掉、爆炸、支撑没了、`/setblock`）都走 `onRemove` → `dropContentsOnce`：已揭示的格子掉落，未揭示的 loot 随收银机销毁 |
| 比较器 | 只统计已揭示的格子 |
| 声音 | 方块音效仍是原版 `METAL`。开、关钱箱有专属音效（开钱箱带收银铃），并发出 `BLOCK_OPEN` / `BLOCK_CLOSE` 游戏事件，见"音效" |

| 区域 + 状态 | 提示 | 右键 |
|---|---|---|
| `drawer`（整台收银机）+ 关 | 打开钱箱 / Open Drawer | `open=true`，钱箱滑出 |
| `interior`（拉出来的钱箱）+ 开 | 搜索 / Search，或查看 / View | 打开 3×3 界面；有未揭示的格子时逐格搜索 |
| `drawer`（钱箱面板）+ 开 | 关上钱箱 / Close Drawer | `open=false`；正在查看的界面全部关闭（`stillValid` 要求 `open`） |

- 打开时瞄准收银机的其它部分（键盘、打印机、显示屏）没有动作。
- 玩家放置的收银机不需要搜索；带 `LootTable` 的世界收银机第一次接触时进入搜索。目前没有收银机的战利品表（钱、票据之类的物品还没做）；接上战利品表不需要改代码。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-cash-register-v2.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 geo、sidecar 和贴图） |
| 成品 | **1032 个三角面**（body 684、drawer 348），23 个 Mesh part，没有共面重叠 |
| 贴图 | 512 atlas，6.75 texel/px，479 个 UV 岛；`textures/block/cash_register{,_s,_n}.png`（取代 V1 的 128 贴图，同名文件） |
| 可编辑源 | `src/main/blockbench/cash_register.bbmodel`（Free Model，组 `body`、`drawer`）和 `src/main/blockbench/textures/cash_register{,_s,_n}.png` |
| 运行时 | `geo/cash_register.geo.json`、`meshes/cash_register.aflmesh.json`、`block_mesh_profiles/cash_register.json`；方块模型 `models/block/cash_register.json` 只有粒子贴图；blockstate 只有一个 `""` 变体（朝向由运行时旋转） |
| 物品 | `models/item/cash_register.json`：`builtin/entity`，`AflStaticMeshItemRenderer` 画关着钱箱的整台收银机；GUI 视角由生成器按投影范围拟合（rotation `[30,225,0]`，translation `[-0.038,1.482,0]`，scale 0.711） |
| 碎屑粒子 | `CashRegisterParticleExtensions`，每次最多 16 个，不变 |

坐标（px）：方块底面中心为原点，收银员一侧朝 -Z（`facing=north` 时朝北），+X 是收银员的左手边。整体 14 × 12.7 × 11.9 px（关着），布局沿用 V1：

| 部件 | 说明 |
|---|---|
| 钱箱底座 | 14 × 12 × 2.8 px 的炭灰色外壳，正面开口装钱箱；底下有内缩的底座和 4 个橡胶脚；背面两个接口（电源、网线） |
| 钱箱 | 面板 12.8 × 2.4 px，下沿有手扣，中间一个镀铬锁芯。后面是钢制抽屉盒，里面是黑色找零托盘：前面 5 个硬币格，后面 4 个纸币格，每格一根带铰座的弹簧压夹 |
| 键盘 | 收银员右手边，斜面 22.5°。深色键盘底板上是梯形键帽：4 × 5 数字键、3 个功能键，以及一个占两行高的浅色确认键 |
| 小票打印机 | 收银员左手边：底座、纸仓盖、后部凸起，纸仓盖和后部之间的缝是出纸口 |
| 显示屏 | 键盘后面的立柱上，屏幕玻璃凹在边框里 |

### 材质（LabPBR）

| 材质 | Base Color | smoothness | F0 |
|---|---|---|---|
| 外壳 `casing` | [58,60,65] 炭灰 ABS | 96–118 | 20 |
| 浅一档 `trim`（纸仓盖、立柱） | [74,77,83] | 100–120 | 20 |
| 钱箱面板 `drawer` | [66,68,74] | 98–120 | 20 |
| 键盘底板 `keybed` | [36,37,41] | 92–104 | 20 |
| 键帽 `key` / 确认键 `enter` | [112,115,121] / [158,160,164] | 122–140 | 20 |
| 屏幕玻璃 `screen` | [18,26,34] | 200 | 20 |
| 抽屉盒 `tray`（喷漆钢） | [56,58,62] | 90–108 | 20 |
| 找零托盘 `insert` | [40,41,45] | 84–100 | 20 |
| 镀铬 `metal`（锁芯、压夹） | [160,163,168] | 140–165 | 255 |
| 橡胶 `rubber`、腔体 `cavity`、接口 `port` | 深灰 | 40–76 | 20 |

没有印刷字符、品牌或磨损。

## 形状（AFL Mesh Shape，`data/apocalypse_firstlight/mesh_shapes/cash_register.json`）

| 状态 | 碰撞 | 选中 | 交互区域 |
|---|---|---|---|
| closed | 6 个盒子：底座（含手扣）、键盘斜面两段、打印机、立柱、显示屏 | 同碰撞 | `drawer` = 全部 6 个盒子，提示锚点在钱箱面板前 |
| open | 同 closed（拉出的钱箱**没有碰撞**，不会把收银员挤开） | closed 的 6 个盒子 + 拉出的钱箱面板 + 本格外面的那段托盘 | `interior` = 托盘盒子，锚点在托盘中间；`drawer` = 钱箱面板盒子，锚点在面板前 |

- 打开时钱箱伸出本格 6.6 px（0.41 格）。伸出部分靠 Mesh Shape 运行时的水平邻格选中检测来选中，和储物柜打开的门一样。
- 交互区域的盒子和选中盒子完全重合，满足运行时"区域命中必须在选中表面后 0.03 格以内"的要求。
- Render bounds（NORTH，方块局部坐标）：`[0.047, -0.016, -0.429, 0.953, 0.759, 0.894]`，包含拉出的钱箱。

## 音效（2026-09-30）

**可听半径**（2026-10-01）：在 `sounds.json` 里用 `attenuation_distance` 设定（原来是原版默认的 16 格），声音随距离线性变小，到半径处听不到；播放音量都不超过 1，所以半径就是实际范围。开钱箱（带收银铃）12 格，关钱箱 10 格。

生成器：`tools/build-cash-register-sounds-v1.mjs [源目录]`（默认 `E:/Download`，需要 ffmpeg）。响度和混音用共享库 `tools/sound-mix-lib.mjs`：K 加权，每段素材先拉到相同的 100 ms 响度，再按关键帧对齐混合，峰值不超过 -1 dBFS，输出单声道 48 kHz Ogg Vorbis。

源素材是用户生成的三段单一事件录音（1 秒，立体声，48 kHz）：

| 源文件 | SHA-256 前 16 位 | 内容 | 左右相关度 |
|---|---|---|---|
| `register_drawer_open.wav` | 5c67df7e1f56b8d9 | 0.13 s 释放的"咔"，滚轮滑动，0.40 s 撞到底并带硬币晃动 | 0.77 |
| `register_drawer_close.wav` | de7a622c096e1c7e | 0.17–0.42 s 推动，0.47 s 扣上 | 0.90 |
| `register_bell.wav` | 67cebec1225e54ae | 0.05 s 拉杆"咔"，0.11 s 铃声，余音到 0.98 s | 0.93 |

响度基准"配电盘开门 / 关门"就是原来配电箱的那两段音效，2026-10-07 配电箱删除后挪到 `sounds/distribution_panel/`，文件没变。

| 事件 | 文件 | 对齐 | 成品 |
|---|---|---|---|
| `cash_register_open` | `sounds/cash_register/open.ogg`（0.98 s） | 开钱箱素材剪掉前 0.12 s：释放的"咔"落在约 0.03 s，撞到底落在 0.30 s（动画最后一帧）。铃声的峰值在 0.10 s，比钱箱低 3 dB，余音盖在硬币声下面 | -24.8 LUFS（目标：配电盘开门 ±0） |
| `cash_register_close` | `sounds/cash_register/close.ogg`（0.74 s） | 剪掉前 0.18 s 很轻的滑动，扣上落在 0.30 s（最后一帧） | -24.7 LUFS（目标：配电盘关门 -1 LU） |

- 铃声只在开钱箱时响，关钱箱时不响。
- 播放：服务端，音量 0.8，音高 0.98–1.02（不让对好的时间点偏移）。
- 字幕："收银机钱箱弹开 / 收银机钱箱关上"（Cash drawer opens / closes）。
- 开钱箱素材的左右相关度是 0.77，比之前的素材低，转单声道时取左右平均，没有做额外处理。
- 没有实机试听。

## 开发工具

- `AuthoringFixtureRegistry`：收银机从"安全装饰"改为"带容器"（`STORAGE_WITH_INVENTORY`），4 个水平朝向，放在地面上，初始 `open=false`、内容为空。
- `CashRegisterIntegrationGameTests`（**没有运行**）：
  - 放置后有方块实体，9 格、3×3 布局，玩家放置的不需要搜索；
  - `action` 的区域 / 状态映射；
  - 四个朝向下，关着时碰撞和选中一致且不超出本格；打开时碰撞不变，选中向收银员一侧伸出 0.3–0.5 格；
  - 各种镐子的掉落；
  - 挖掉时钱箱里的东西只掉一次。

## 待实机确认

1. 外观、四个朝向、PBR、物品栏图标；
2. 钱箱动画（6 ticks、匀速）的手感，以及铃声和撞击声是否对上动画；
3. 拉出的钱箱在本格外能否正常选中，以及打开、搜索、关上的提示位置；
4. 旧存档：`convenience_store_01.nbt`、`gas_station_01.nbt` 里有收银机。以后新生成的结构放置时会自动建方块实体，没有问题。但 V2 之前已经生成进存档的收银机没有方块实体，而 V2 只由方块实体渲染。按代码推断（未验证）：这些收银机读入后可能看不见，但仍能选中；第一次打开钱箱时补建方块实体，之后恢复显示。重新放置也能解决。
