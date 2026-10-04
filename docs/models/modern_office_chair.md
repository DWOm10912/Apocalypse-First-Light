# Modern Office Chair — 建模资产

状态：**V2（2026-10-03）已接入；能坐、能转，坐上去后能用 WASD 推着走，移动时有滚动音效**。生成脚本 `--check` 通过，`compileJava --offline` 通过；**没有实机验证**。

时间线：
- V1 是 202 个 cube 的 Blockbench 模型，2026-10-03 被 V2 替换。
- 同一天加了坐和移动，用户选的是"方案 B"：坐过的椅子从此是实体，停在哪就在哪。

注册 ID、方块状态（`facing`）、方块的掉落和挖掘规则都没变。

| 项目 | 值 |
|---|---|
| 方块 | `apocalypse_firstlight:modern_office_chair`（`block/ModernOfficeChairBlock`），整把椅子的静态 OBJ，跟区块一起烘焙，没有方块实体 |
| 实体 | `apocalypse_firstlight:modern_office_chair`（`entity/OfficeChairEntity`），0.85 × 1.15 格，渲染器 `client/OfficeChairRenderer` |
| 生成脚本 | `tools/build-office-props-v2.mjs chair`（命令见 [modern_office_desk.md](modern_office_desk.md)） |
| 可编辑源 | `src/main/blockbench/modern_office_chair_v2.bbmodel`，按骨骼分组：`base`、`caster_0`–`caster_4`、`swivel`；贴图副本 `src/main/blockbench/textures/modern_office_chair_v2{,_s,_n}.png` |
| 方块模型 | `models/block/modern_office_chair.{json,obj,mtl}`（`forge:obj`）；物品模型的父模型也是它 |
| 实体网格 | `geo/modern_office_chair.geo.json` + `meshes/modern_office_chair.aflmesh.json`（AFL mesh sidecar，由 `AflMeshCache` 自动加载；没有 block mesh profile） |
| 贴图 | `textures/block/modern_office_chair{,_s,_n}.png`，512 LabPBR，约 9 texel/px；方块和实体共用 |
| 包围盒 | x ±6.65、y 0–18.75、z -5.2–6.16 px；坐垫顶 y 8.75，扶手顶 10.85 |
| 三角面 | 3352，其中 base 504、每个脚轮 180、swivel 1948 |
| 方块采掘 | 镐；铁镐或更高等级（`mineable/pickaxe` + `needs_iron_tool`，`requiresCorrectToolForDrops`）。未改 |

## 外观

- **底座**：五爪尼龙底座，其中一爪朝正后方；双轮脚轮。
- **气杆**：黑色伸缩护套加镀铬气杆。镀铬（F0 230）只用在这一处。
- **底盘**：黑色涂层钢底盘，调高手柄在坐的人右手边。
- **座面**：模压座盆，上面是圆角放样的软包坐垫。
- **靠背**：上宽下窄、后仰 8°，前面软包、后面黑色背壳，由支撑脊连到底盘。
- **扶手**：T 型扶手。
- **材质**：炭灰粗面织物 `[52,54,58]`，黑色 PP / 尼龙，涂层钢，橡胶轮。不印字。
- **法线**：相邻面夹角小于 36° 处做了平滑，OBJ 和实体网格都是。

## 骨骼（实体网格）

坐标单位 px，方块中心为原点。

| 骨骼 | 内容 | 转轴 / pivot |
|---|---|---|
| `base` | 中心毂、五爪、气杆护套 | 原点；整体按实体的底座朝向转 |
| `caster_0`–`caster_4` | 第 k 个脚轮的叉架和两只轮子 | 脚轮立轴：半径 5.55、y 1.2、第 k 爪方向（从 +Z 往 +X 转 72k°）；先跟底座转，再绕自己的立轴转 |
| `swivel` | 镀铬气杆、底盘、手柄、座盆、坐垫、靠背、扶手 | 柱轴 (0, 4.6, 0)；按座椅朝向转 |

这些 pivot 写在生成脚本的 `CHAIR` 常量里，`OfficeChairRenderer` 里有同样的数值。模型正面是 -Z，所以世界朝向 ψ 对应绕 +Y 转 180 − ψ。

## 坐、转、推着走（2026-10-03）

**坐下**
- 右键放好的椅子：方块被移除，原地换成椅子实体，玩家坐上去。
- 实体刚生成时，底座和座椅都朝方块原来的朝向，所以看上去不会动。
- 以下情况不坐：下蹲时、已经在骑乘、这里不允许玩家改动方块（`mayBuild` / `mayInteract`）。
- 拿着方块右键椅子也是坐下。

**之后它一直是实体**
- 右键坐上去，下蹲起身。
- 打一下就捡起来，掉落椅子物品（创造模式不掉）；只有玩家的攻击有效，其他伤害都忽略。
- 中键选取得到椅子物品。
- 再放下物品就又是一个方块。

**驾驶**（坐着的人的客户端控制，服务端走原版的载具移动校验，和船相同）
- W / S / A / D 相对视线方向。
- 速度（格/tick）：
  - 最高 0.13，约为走路的 0.6 倍；再乘"推力系数" = 坐着的人当前移速 ÷ 基础移速（最大 1）。负重、寒冷减速、缓慢效果都会同样拖慢椅子，速度加成不会让它更快。例如负重 50 kg 时移速约 50%，最高速约 0.065；
  - 按住时每 tick 保留 0.7 的旧速度，约 0.4 秒到最高速；
  - 松开后每 tick 保留 0.8，滑行约 0.65 秒停下。
- 地形：不能上台阶（连半砖都不行）；悬空时下落，摔落伤害按原版传给坐着的人。
- 滚动没有脚步声。没人坐时可以被玩家推着滑开；有人坐时不能被推。
- 体力（`stamina/PlayerStamina`，用户 2026-10-03 定）：
  - 坐着不动：不消耗、正常恢复、不产生运动热；
  - 滑行（水平移动 > 0.01 格/tick）：算低强度活动，每秒 0.5，负重越高略多，≥ 60 kg 时 1.0；
  - 滑行不打断恢复，算进口渴和体温的运动量。
  - 详见 `docs/gameplay/stamina_system_v1.md`。

**朝向**
- 座椅每帧跟着坐的人的视角转，360° 不分档，坐的人身体也跟着转。
- 起身后座椅停在最后的方向，存进实体的朝向里，所有玩家看到的一样。
- 底座保持放置时的朝向，不跟着转，存档字段 `BaseYaw`，并同步给客户端。

**脚轮**（只在客户端，纯视觉）
- 每个脚轮绕自己的立轴转，让轮子顺着这一 tick 的移动方向，每 tick 最多转 25°。
- 轮子正反都能滚，所以取较近的那个对齐方向，倒车时不会整个甩 180°。
- 速度低于 0.004 格/tick 时不转。
- 轮子本身不做滚动动画：纯黑圆柱，直径 1.2 px，转起来看不见。

**起身落点**：依次找视线前方、左右、后方的安全位置，都不行就站到椅子上面。

**其他客户端**：位置按船的方式插值（10 步）。

## 滚动音效（2026-10-03）

**播放**（`client/OfficeChairRollSound`，只在客户端）
- 椅子实体移动时在椅子位置循环播放，无论是坐着推、被人推开，还是别人在开。
- 每个客户端每 tick 检查玩家 16 格内的椅子，移动超过 0.01 格/tick 才开始播放。
- 音量跟速度成正比：静止为 0，最高速 0.13 格/tick 时为满音量。
- 淡入每 tick 补差值的 50%，淡出每 tick 35%，起步和停下不会突然出声或断掉。
- 音调随速度从 0.85 升到 1.1。
- 椅子所在格是地毯，或者脚下是羊毛时：音量乘 0.45，音调再乘 0.85。
- 停下并淡出后，再等 5 tick 自动结束；再动时重新开始。
- 音效事件 `apocalypse_firstlight:office_chair_roll`，类别 NEUTRAL，衰减距离 12 格。
- 字幕 `subtitles.apocalypse_firstlight.office_chair_roll`：Office chair rolls / 办公椅滚动。

**音频文件**：`sounds/office_chair/roll_loop.ogg`，1.4 秒无缝循环，单声道 48 kHz Ogg Vorbis。

**生成**：`tools/build-office-chair-sounds-v1.mjs [sourceDir]`，默认 `E:/Download`，需要 ffmpeg。
- 源文件：用户 2026-10-03 生成的 `office_chair_roll.wav`，3 秒立体声，左右相关 0.965，用 SHA-256 前缀校验。
  - 开头约 0.7 秒是渐强，2.45 秒后渐弱；
  - 中间的平稳滚动里有轻微的轮子咔嗒声。
- 处理：
  - 取 0.80–2.40 秒，按 28 个 50 ms 段做电平拉平；
  - 400 Hz 以上的咔嗒声压到比周围最多高 8 dB（原来最高高出 16.4 dB）；
  - 最后 4 段用等功率交叉淡化接回开头。噪声两侧不相关，线性交叉淡化会在中间凹下约 3 dB。
- 等功率交叉淡化是共享库 `tools/sound-mix-lib.mjs` 的 `buildLoop` 这次新加的 `equalPower` 选项，默认关闭，不影响已有的循环音效。
- 响度：用户觉得原始录音偏响，所以定在工业储物柜开门声下 16 LU，比翻找循环声低 6 LU。
- 结果：最大瞬时响度 −38.5 LUFS，峰值 −21.7 dBFS，接缝跳变 0.0005。

## 选中轮廓与碰撞

**放置的方块**（`ModernOfficeChairBlock`）
- 选中轮廓：一个盒子 `1.35,0,2.8 → 14.65,18.75,14.2`，不是台阶状。
- 碰撞：
  - 底座 + 坐垫 `2.95,0,2.85 → 13.05,8.75,12.35`；
  - 靠背 `3.4,8.75,11.5 → 12.6,18.75,14.2`；
  - 两侧扶手 `x 1.35–2.75 / 13.25–14.65`，`y 6.5–10.85`，`z 4–10.4`。
- 以上都是 facing=north 时的值，随朝向旋转。

**实体**：原版实体包围盒 0.85 × 1.15 格。挡住放方块（`blocksBuilding`），其他实体不能站在上面。

## 物品

父模型是 `block/modern_office_chair`。GUI：rotation `[25,135,0]`、translation `[0.025,-0.814,0]`、scale 0.62。

## 已知限制 / 代价

- 捡起椅子实体不需要镐，等于绕过了方块"铁镐才掉落"的规则（用户选方案 B 时已说明）。
- 推不进办公桌下面：椅子实体高 1.15 格，桌板只有 0.84 格高。
- 坐过的椅子都变成实体存在区块里。
- 旋转不再经过 AFL Animated Block Mesh Runtime：中途做过一版"方块实体 + 运行时转角接口"，已在同一天撤回，运行时文件恢复原样。实体只用到共享的 `AflMeshCache` / `AflMeshRenderer`。

## 旧工具（已过时）

- V1 cube 源 `src/main/blockbench/modern_office_chair.bbmodel` 保留作参考。
- **不要再运行** `tools/export-modern-office-chair.mjs`：它会把 V1 贴图写回 `textures/block/modern_office_chair.png`。
- `tools/modern-office-chair*.blockbench.js` 只对 V1 源有意义。
- 运行时导出脚本 `tools/export-office-props-runtime.mjs` 已删除。

## 需要实机验证

- 右键时方块换成实体是否看不出跳动（位置、朝向、光照）；
- 坐下高度：大腿是否落在坐垫上，背是否贴靠背；
- WASD 手感（速度、加速、滑行）、撞墙、从边缘掉落；
- 座椅跟视角转是否顺滑；底座不转；脚轮跟着移动方向转；
- 第三人称和其他玩家看到的移动、转动是否平滑；
- 起身落点、退出重进后椅子的位置和朝向；
- 打掉椅子是否掉落物品（生存）、创造模式不掉；
- 推动没人坐的椅子；
- 在 Sundial 下的材质；
- 滚动音效：音量是否合适、循环能否听出接缝、快慢时的音调、在地毯上的效果、停下时是否干净结束。
- 体力：坐着不动不扣、滑行时每秒约 0.5（负重下更多，用 `/aflstamina` 看）；负重 50 kg 时椅子明显变慢、不比走路快。
