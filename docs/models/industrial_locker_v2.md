# Industrial Locker V2（工业储物柜 V2）

状态（2026-09-30）：**已实现，Mesh Shape 坐标错位已修复，待用户重新实机验证**。材质观感用户已实机确认。
- 首轮 `compileJava --offline` 跑了两次，都是 PASS：第一次之后改了 1 行（`markPlacedByPlayer` 在写入状态后复位临时标记），所以重新编译了一次。
- Mesh Shape 接入之后又跑了一次 `compileJava --offline`，PASS。
- 资产生成器 `--check` 通过。
- 没有运行客户端、服务端或 GameTest；下文的游戏内行为是代码层面的设计，待用户实机确认。
- **货物（2026-10-01，没有实机验证）**：柜里画通用货物，按主题（工业 / 学校 / 通用）随机，东西多就摆得多，见"货物"。战利品改为第一次开门时生成。

## 不变与改变

| 项目 | 状态 |
|---|---|
| Registry ID | `apocalypse_firstlight:industrial_locker`，不变 |
| 两格高、朝向规则、放置规则 | 不变 |
| 碰撞 / 选框 | 2026-09-29 起由 Mesh Shape profile 提供（见下文）：关门时是柜体 + 门板，开门时是柜体 + 打开后的门板 |
| 挖掘 | 规则不变（2026-09-30 起方块声音改用 `AflSoundTypes.SHEET_METAL`，见"音效"）：硬度 5、爆炸抗性 8、`requiresCorrectToolForDrops()`、`minecraft:mineable/pickaxe` + `minecraft:needs_diamond_tool` |
| 掉落 | 方块本身不变（正确工具在生存模式下掉 1 个储物柜）；库存掉落规则见"搜索"一节 |
| 容量 | **54 → 27 格（3 行）**，用户要求。开发阶段，不做旧存档迁移，旧存档中 27 格以后的物品不会读入 |
| 外观 | 3 个 cube 的旧模型 → Pure Mesh + LabPBR，门可开关 |
| 交互 | 右键任意位置直接打开界面 → 看向门板或柜口时出现世界内提示：打开 / 搜索 / 查看 / 关门 |
| 搜索 | 世界 loot 储物柜接入 Progressive Container Search V1 |

另外修复了一个旧问题：原来的 `load()` 不读取 `LootTable`，带战利品表的储物柜在保存和读取后会丢失战利品表。现在按原版方式读取和保存。

## 模型

生成器：`tools/build-industrial-locker-v2.mjs`（`--check` 校验所有输出是否最新；2026-10-01 加了 `--preview DIR`，只输出 geo、sidecar 和贴图，供离线检查）。

可编辑源：
- `src/main/blockbench/industrial_locker.bbmodel`（Free Model，组 `body` / `door`）
- `src/main/blockbench/textures/industrial_locker{,_s,_n}.png`

运行时文件：
- `assets/apocalypse_firstlight/geo/industrial_locker.geo.json`
- `assets/apocalypse_firstlight/meshes/industrial_locker.aflmesh.json`（V2 sidecar）
- `assets/apocalypse_firstlight/block_mesh_profiles/industrial_locker.json`
- `assets/apocalypse_firstlight/textures/block/industrial_locker{,_s,_n}.png`

坐标：单位是 px，原点在方块底面中心，y 取 0..32。门朝 -Z（facing=north 时朝北），+X 是站在柜前的人的**左手边**。占地与 NORTH 碰撞盒一致：x 2..14 / z 1..14 px。

**638 个三角面**（220 个四边形 + 198 个三角形）：body 264，door 374。共 22 个 Mesh part，没有共面重叠。2026-10-01 加货物后整个模型 5222 个三角面、119 个 Mesh part：柜体不变，货物 34 个骨骼共 4584 个（每个 52–232），一个柜子最多同时画 7 个，约 1400 个；没有共面重叠（每个货物骨骼单独和柜体一起检查）。

| 部位 | 几何 |
|---|---|
| 柜体 | 左右侧板、背板（0.5 px 钢板，0.08 倒角） |
| 顶部 | 四周外伸 0.15 的顶板翻边 |
| 前框 | 上框、底框 |
| 底部 | 柜底；内缩 1.3 的踢脚底座（深色） |
| 柜内 | 上层隔板和向下折边、镀锌挂衣杆、背板上 2 个 L 形挂钩；朝内的面自动改用柜内材质 |
| 门板 | 0.65 厚，0.12 倒角作折边 |
| 百叶 | 上下各 5 片冲压百叶：楔形真几何，开口朝下，开口面用深色缝隙材质，门板没有开孔 |
| 门锁 / 把手 | 右侧锁板、D 形拉手、锁芯 |
| 铰链 | 左侧 3 节铰链 |
| 门背 | 竖向加强槽钢、把手后的锁舌凸轮、锁杆和 2 个导向座 |

- **铰链 pivot**：`[6.05, 0, -6.62]` px，也就是三节铰链的竖直轴线；在 profile 中是 `[0.378125, 0, -0.41375]` block。
- **开门动画**：通道 `open`，门绕 Y 轴转 **-100°**，门的自由边向外、向人的左手边转出。时长 10 ticks，`ease_in_out`；中途反向由运行时处理，从当前角度开始转回。
- **Facing**：profile 设为 `facing: horizontal`，运行时读取 `HORIZONTAL_FACING`，NORTH 0°、EAST -90°、SOUTH 180°、WEST 90°。旋转在 part 之前，pivot、几何和法线一起转动。
- **Render bounds**：NORTH 方向，方块局部坐标 `[0.1, -0.016, -0.679, 1.084, 2.016, 0.9]`。由生成器把门的所有顶点在 0..-100° 之间扫 21 个角度得出，再加 0.25 px 余量，四个方向在加载时预先旋转好。

## 材质与贴图

- 512×512 atlas，每 px 3.25 texel（加货物前是 5.75），边距 2 px。实体贴图没有 mipmap。
- 岛展开、绘制和 PNG 输出都复用 `tools/cube-slab-mesh-lib.mjs`（unwrap / paint / png）。
- 为了让同一材质可以按部件取不同 Base Color，库里的材质颜色函数现在会额外收到 `(part, face)` 两个参数。这不改变任何 UV 岛；其它使用这个库的资产 `--check` 全部通过，输出逐字节不变。

| 材质 | Base Color | `_s`（R 光滑度 / G F0） |
|---|---|---|
| `coat` 粉末喷涂钢 | 按区域取色（见下） | 112 / 24，倒角 138，涂层不是金属 |
| `interior` 柜内 | 更暗更冷 | 100 / 24 |
| `dark` 踢脚 | [35,36,38]，向下略暗 | 60 / 24 |
| `louverGap` 百叶缝 | [8,9,11] | 40 / 24 |
| `zinc` 镀锌五金 | 按部件取色（见下） | 118 / 255，较粗糙的金属 |
| `lock` 锁芯 | 暖镍 [146,140,128] | 130 / 255 |

`_n` 是平直法线（128/128），B 通道为 AO（孔壁 195，朝下的面 225）。

Base Color 分区（2026-09-29 按用户要求加强，关掉 PBR 也要能读出"粉末喷涂钢柜"）：
- **做法**：只靠结构分区、低频明暗和冷暖差、少量符合逻辑的磨损。没有随机噪点、锈迹或脏污。
- **不影响其它贴图**：这一步只改颜色函数，`_s`、`_n`、sidecar（几何和 UV）、geo、profile 与改动前逐字节相同。

| 区域 | 颜色 |
|---|---|
| 门正面 | 偏冷的蓝灰 [73,79,89] |
| 门的侧边 | 门正面 ×0.94 |
| 门背、加强槽钢 | [63,68,77] |
| 百叶片 | 比门面暗一级 [64,69,78] |
| 侧板 | 略暖、略闷 [70,73,79] |
| 顶板上面 | 较亮 [80,84,90] |
| 前框和边缘 | [65,69,76] |
| 背板 | [61,64,69] |
| 柜内 | [48,53,61]，越往里、越靠底部和顶部越暗，朝下的面再暗一点 |
| 隔板 | [52,57,65]，与柜内使用相同的衰减 |

外部整体从下到上有 7% 的低频明暗过渡。

逻辑磨损只有三处：
- 把手周围一圈椭圆形手摸磨损：向浅灰 [93,96,100] 混合，最多 22%；
- 门的自由边在把手高度附近变浅；
- 门脚略暗 7%，以及底框顶面被磨浅 14%。

五金件按部件取色：
- 拉手 [160,164,168]
- 挂衣杆 [168,171,174]
- 挂钩 [138,142,146]
- 锁板 [132,136,140]
- 锁杆 / 凸轮 [112,115,119]
- 铰链 [98,101,106]，偏暗，与拉手区分开

## 运行时接入

- **方块**：新增属性 `open`（`BlockStateProperties.OPEN`），上下半同步。下半 `RenderShape.ENTITYBLOCK_ANIMATED`，方块模型只剩粒子贴图（`models/block/industrial_locker.json`）；上半仍是空模型。blockstate JSON 的 variant 键不含 `open`，两种状态共用同一个模型。
- **渲染**：通用 `AflAnimatedBlockMeshRenderer`，在 `AflBlockEntityRenderers` 注册，没有储物柜专用渲染器。
- **方块实体**：`IndustrialLockerBlockEntity` 继续继承 `RandomizableContainerBlockEntity`，同时实现 `AflSearchableContainer` 和 `AflAnimatedMeshHost`（后者是本轮为动画运行时新增的接口，见 [animated_block_mesh_runtime_v1.md](../rendering/animated_block_mesh_runtime_v1.md)）。
- **物品**：
  - `models/item/industrial_locker.json` 改为 `builtin/entity`，保留旧模型的 display 变换；
  - `AflStaticMeshItemClient` 把物品绑定到 `AflStaticMeshItemRenderer`，用同一份 geo、sidecar 和 `textures/block/industrial_locker.png`，门为关闭姿态，竖直偏移 0（Mesh 原点与旧的两格高物品模型位置一致）；
  - `AflStaticMeshItemRenderer` 新增一个接收完整纹理位置的构造函数，原来按 `textures/item/` 拼路径的构造函数不变。

## 形状与世界内交互（2026-09-29 改为 Mesh Shape Runtime）

2026-09-30 审计：原缓存生成按 NORTH/EAST/SOUTH/WEST，查询却按 Minecraft SOUTH/WEST/NORTH/EAST 编号取值，造成整体 180° 错位。已在通用 `AflMeshShapeTransform` 修正，并统一 shape / region / anchor 的转换。不是资产需要翻转 X/Z；本轮没有修改源模型、运行时 Mesh、材质、动画 profile、shape JSON 或生成器。

从实际 `.aflmesh` 门顶点加回 native pivot，再施加 -100° 旋转，所得 OPEN AABB 与 shape JSON 最大差为 0.000420 px 以内（JSON 保留 3 位小数），确认生成器与视觉门轴一致。补充 picking 现包含斜对角格：NORTH 开门盒子同时越过 X=1 和 Z=0，旧四邻域会漏查。上下格交互的遮挡判定使用完整资产形状，避免中缝斜向视线差异。详见 [坐标契约](../rendering/mesh_shape_runtime_v1.md)。这些是静态核对，不代表实机通过；最终单次编译结果见任务交付报告。

用户实机反馈了两个问题：
- 开门后瞄准门把手关不上门，而且打开的门没有碰撞；
- 只有中间一小块能触发"查看"。

因此形状和交互改由 [Mesh Shape Runtime](../rendering/mesh_shape_runtime_v1.md) 提供：
- profile 是 `src/main/resources/data/apocalypse_firstlight/mesh_shapes/industrial_locker.json`，由 `tools/build-industrial-locker-v2.mjs` 用 Mesh 的常量生成；
- `IndustrialLockerBlock` 实现 `AflMeshShapeBlock`，状态 `closed` / `open` 对应 `open` 属性，上下两半对应 `cells_y: 2` 的第 0、1 格。

以下坐标都是 NORTH 朝向的模型坐标，单位 px。门打开时的门板盒子是门部件全部顶点转到 -100° 后取的 AABB。

| 状态 | Physical（碰撞） | Selection（选中/轮廓） | 交互区域 → 锚点 |
|---|---|---|---|
| closed | 柜体 [-6,0,-6.3 → 6,32,6] + 门板 [-5.95,2.35,-7 → 5.85,31.45,-6.3]，共 2 个 AABB | 柜体（含顶部外沿，±6.15）+ 门板 + 拉手，共 3 个 | `door` [-6,2.3,-7.9 → 5.9,31.5,-6.3]（整块门板）→ `door_lock` [-4.6,17.6,-7.8] |
| open | 柜体 + 打开的门板 [5.757,2.35,-18.464 → 9.094,31.45,-6.327]，共 2 个 | 柜体（含外沿）+ 打开的门板，共 2 个 | `interior` [-6,0,-6.9 → 6,32,5.5]（整个柜口正面）→ `interior` [0,18,-6.5]；`door` = 打开的门板 → `door_lock` [9.061,17.6,-16.903]（打开后把手的位置） |

- **按格切开**：physical / selection 按上下两格切开。门板跨两格，所以上下每格各有 2–3 个盒子。
- **打开的门**：门板盒子伸进前方那一格，而且有碰撞；形状不跟随开门动画逐帧变化。
- **选中**：伸出本格的部分由 `AflMeshShapePicking` 补充检测，所以从侧面也能瞄中打开的门。
- **碰撞变化**：旧碰撞是 12×13 px 手写盒子，东西朝向差 1 px。现在四个方向都由同一份 profile 旋转得到；2026-09-30 已修复缓存 facing 索引错位，实际碰撞与视觉一致性仍需用户实机复验。

交互提示仍复用自动售货机的 `WorldInteractionHint` 和 `AttachmentHintStyle`。瞄中区域就算命中，提示框画在该区域锚点的屏幕投影旁边，不再固定在准星处。服务端 `use()` 和客户端提示都用 `meshInteraction`，即玩家视线对交互区域的检测。

| 区域 + 状态 | 提示 | 右键 |
|---|---|---|
| `door` + 关 | 打开 / Open | 设置 `open=true`，播放开门动画和 `industrial_locker_open`（见"音效"），不弹界面 |
| `door` + 开 | 关门 / Close | 设置 `open=false`，播放 `industrial_locker_close`；瞄准打开后的门板任意位置都可以 |
| `interior` + 开，还有未揭示的格子 | 搜索 / Search | 打开 Progressive Search 界面，开始或继续搜索 |
| `interior` + 开，已全部揭示，或是玩家自己放的储物柜 | 查看 / View | 普通 3 行箱子界面 |

- 上一版的"空手潜行关门"和门锁小区域判定（`ANCHOR_*`、`atInteractionAnchor`）都已删除。
- 关门时所有正在查看这个储物柜的界面都会关闭（方块实体的 `stillValid` 要求 `open`），进行中的搜索随之暂停。
- 感染者破门逻辑只处理门、栅栏门、玻璃等，`open` 属性对它没有影响；储物柜开门不发交互噪音。

## 音效（2026-09-30）

**可听半径**（2026-10-01）：在 `sounds.json` 里用 `attenuation_distance` 设定（原来是原版默认的 16 格），声音随距离线性变小，到半径处听不到；播放音量都不超过 1，所以半径就是实际范围。开门、关门 12 格。钢板方块声（`sheet_metal_*`）是方块材质声，和原版方块一样保持 16 格。

素材来自用户提供的 `E:/Download/*.wav`（1 秒、立体声、48 kHz）。本次只接入储物柜自己的门声和钣金方块声；格栅音效暂不接入。搜索时的翻找声 2026-10-02 起由搜索框架统一提供，所有容器共用一套（见 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md) 17a）。

**处理**：
- 双声道混成单声道（左右相关度 0.93–1.00，不会相互抵消），编码为 48 kHz ogg vorbis（q6）。
- 裁掉首尾的静音，结尾加 60–150 ms 淡出。
- 峰值统一到 -1 ~ -4 dBFS；破坏声持续时间长，压到 -4。

| 事件 | 文件 | 源文件（SHA256 前 16 位） | 处理 |
|---|---|---|---|
| `industrial_locker_open` | `sounds/industrial_locker/open.ogg`（0.68 s） | `locker_open.wav` c0a2206f152e6860 | 去掉开头 60 ms；锁舌声在 53 ms，第二声约在 480 ms（门接近全开） |
| `industrial_locker_close` | `sounds/industrial_locker/close.ogg`（1.17 s） | `locker_close.wav` b51e4c4e7ac6586b | 前面补 220 ms 静音，把撞击声移到 **471 ms**，对上 10 tick 关门动画的结束 |
| `sheet_metal_hit` | `sounds/sheet_metal/hit.ogg`（0.26 s） | `sheet_metal_hit.wav` 03c7b491d783ddef | 裁剪 |
| `sheet_metal_step` / `sheet_metal_fall` | `sounds/sheet_metal/step.ogg`（0.20 s） | `sheet_metal_step.wav` 1129a84ae7c1cb37 | 裁剪，+3.7 dB |
| `sheet_metal_break` | `sounds/sheet_metal/break.ogg`（0.95 s） | `sheet_metal_break.wav` ba15ad6080fc58f4 | 裁剪，峰值 -4 dB；2026-09-30 实机反馈太吵，`sounds.json` 里再加 `volume: 0.45`（约 -7 dB） |
| `sheet_metal_place` | `sounds/sheet_metal/place.ogg`（0.52 s） | `sheet_metal_place.wav` 69862e18a65cbf93 | 裁剪 |

**开关门**：`IndustrialLockerBlock.setOpen` 由服务端播放，音量 0.8，音高在 0.96–1.04 之间随机，替换原来的原版铁门声。字幕为"储物柜门打开 / 储物柜门关上"（`subtitles.apocalypse_firstlight.industrial_locker_*`）。

**挖掘、破坏、放置、脚步、摔落**：
- 新增共享声音套 `registry/AflSoundTypes.SHEET_METAL`（`ForgeSoundType`，音量 1、音高 1），代表"空心喷涂钣金柜"这一类声学材质。
- 储物柜和金属垃圾桶（2026-10-02 起）使用它；文件柜、电箱等以后可以直接改用同一套。
- 原版播放方块声时会给音高乘一个系数：挖掘 ×0.5，破坏和放置 ×0.8，脚步 ×1，摔落 ×0.75。`sounds.json` 里每条声音的 `pitch` 抵消了这个系数，让素材按原音高播放。
- 每个事件都只有一段录音，所以同一文件列了三条，音高相差 ±5%，作为变体。
- 摔落复用脚步素材。字幕使用原版的 `subtitles.block.generic.*`。
- 感染者听到的破坏噪音按方块标签判断，与声音套无关，所以不受影响。

未实机试听。

## 搜索

按 [progressive_container_search_v1.md](../gameplay/progressive_container_search_v1.md) 的 contract 接入：
- 5 个转发覆写，加上 load/save；
- 所有移除路径（玩家破坏、失去支撑、爆炸）都经过 `dropContentsOnce()` → `dropContentsOnBreak`：已揭示的格子掉落，未揭示的 loot 随柜子销毁。

**计时**：`IndustrialLockerBlockEntity.SEARCH_SETTINGS = (40 ticks/格, ±15% 抖动, 无噪声)`。
- 抖动只由 seed 和格序号决定，与格子内容无关。
- 翻找噪声暂不配置，没有定正式数值。

**世界 loot 与玩家放置的区分**：
- **玩家放置**：`setPlacedBy` 调用 `markPlacedByPlayer()`，立刻把"不需要搜索"写入持久状态，之后永远是普通储物。
- **世界生成 / 带 NBT 放置**：`/setblock`、结构模板等带 `LootTable` 的储物柜不走 `setPlacedBy`，第一次接触时判定为需要搜索（loot table 仍在）。

**客户端同步**：`getUpdateTag` 只发送一个布尔值 `AflSearchComplete`，供提示在"搜索"和"查看"之间选择，从不包含物品；搜索完成时推送一次更新。

测试用法：
1. 正常放置一个储物柜。
2. 对**下半**执行 `/data merge block <x y z> {LootTable:"minecraft:chests/simple_dungeon"}`。
3. 再执行 `/data remove block <x y z> AflContainerSearch`，让它回到未初始化状态。
4. 下一次交互时，它会作为世界 loot 进入搜索。

## 货物（2026-10-01）

通用规则和主题见 [container_goods_v1.md](../gameplay/container_goods_v1.md)。

**位置**（源坐标 px，_a 在正对柜子时的左手边）：

| 位置 | 坐标 | 可摆的道具 |
|---|---|---|
| `floor_a` / `floor_b` | 柜底 y 2.72，x ±2.75 | 纸箱（1–2 层）、帆布旅行袋、工具箱、劳保靴、书包（靠背板立着）、运动鞋、运动包、篮球 |
| `shelf_a` / `shelf_b` | 上层隔板 y 26.37，x ±2.75 | 小纸箱、保温杯 + 饭盒、安全帽、课本 |
| `rod` | 挂衣杆 y 24.6，z 0.8 | 工作夹克、反光背心 |
| `hook_a` / `hook_b` | 背板挂钩 x ±3，钩尖 y 20.95 | 帆布包、劳保手套、安全帽、书包 |

每个位置上的每种道具都是单独的骨骼 `goods_<位置>_<道具>`（34 个），尺寸、角度、颜色由生成器按固定种子随机，所以两个地面位置的同一种道具也不一样。挂衣杆上的衣服（z −0.4..2.0）和挂钩上的东西（z 2.3 以后）前后错开，地面的东西最高约 y 11，衣服下摆约 y 12.8，挂钩上的东西最低约 y 14.6，互不穿插。

**主题 → 每种位置能摆的道具**（`IndustrialLockerBlockEntity.GOODS_THEMES`）：

| 主题 | 地面 | 隔板 | 挂衣杆 | 挂钩 |
|---|---|---|---|---|
| `generic`（通用、玩家放置） | 纸箱、帆布旅行袋 | 纸箱、保温杯 | 夹克 | 帆布包 |
| `industrial` | 纸箱、帆布旅行袋、工具箱、劳保靴 | 纸箱、保温杯、安全帽 | 夹克、反光背心 | 帆布包、手套、安全帽 |
| `school` | 纸箱、书包、运动鞋、运动包、篮球 | 纸箱、保温杯、课本 | 夹克 | 帆布包、书包 |

- 每个柜子按位置和主题随机：7 个位置的填充顺序打乱，每个位置从主题允许的道具里选一种；同一种位置（两个地面、两个隔板、两个挂钩）尽量不选同一种。
- 显示几处按共用规则，最多 7 处（11 格以上全摆）。
- 主题在战利品生成时按战利品表记下（存档键 `GoodsTheme`），玩家放置的是 `generic`。
- **战利品改为第一次开门时生成**（原来是第一次打开界面时），这样门一开货物数量就对。只是生成，格子仍要搜才能看到。
- 门完全关上时不画货物（建筑里常常一排储物柜），开着或正在开关时才画。
- 同步给客户端的：`AflSearchComplete`、主题名、显示几处；不含物品。
- 测试：`/dev container_search spawn apocalypse_firstlight:industrial_locker theme <主题> fill <格数>`。

## 需要实机重点验证

1. 四个朝向下，门的铰链侧、开门方向和提示命中区域是否一致。
2. 四个朝向下的碰撞、选中轮廓和提示位置是否与画面一致，重点复验本次 facing 索引修正。
3. 打开的门伸进前方那一格并有碰撞：从正面、侧面、斜侧瞄准门板时，是否都能出现"关门"；站在门板所在位置开门时，玩家会不会被卡住。
4. 物品栏、手持、地面掉落的大小和位置（沿用旧的 display 变换）。
5. 光影开和关时的材质观感；实体贴图没有 mipmap，远处的百叶可能会闪。
6. 货物（2026-10-01）：三个主题的道具样子、位置有没有穿插、数量随格数变化、拿空后消失、关门后不画；贴图密度降低后柜体近看有没有变糊。

## 修改文件

- `tools/build-industrial-locker-v2.mjs`（新）
- `tools/cube-slab-mesh-lib.mjs`（颜色函数多收两个参数）
- `src/main/resources/data/apocalypse_firstlight/mesh_shapes/industrial_locker.json`（生成）
- `meshshape/{AflMeshShapeProfile,AflMeshShapes,AflMeshShapeBlock}.java`、`client/AflMeshShapePicking.java`、`mixin/client/AflMeshShapePickMixin.java`、`apocalypse_firstlight.mixins.json`（Mesh Shape Runtime）
- 上文列出的资产和源文件
- `models/{block,item}/industrial_locker.json`
- `lang/{en_us,zh_cn}.json`（`hint.apocalypse_firstlight.locker.{open,search,view,close}`）
- `block/IndustrialLockerBlock.java`
- `blockentity/IndustrialLockerBlockEntity.java`
- `client/WorldInteractionHint.java`（原 `VendingMachineHint.java`）
- `client/AflBlockEntityRenderers.java`
- `client/AflStaticMeshItemRenderer.java`
- `client/AflStaticMeshItemClient.java`
- `client/AflContainerSearchScreen.java`
- `blockmesh/AflAnimatedMeshHost.java`（新）
- `blockmesh/AflAnimatedMeshBlockEntity.java`
- `client/blockmesh/AflAnimatedBlockMeshRenderer.java`
