# Emergency Radio V1（手摇应急收音机，2026-10-14）

森林开局 B「溪谷老宅」M2 的 F4（[实现计划](../worldgen/forest_start_home_b_v1_plan.md) 包 F）。外形来自用户花钱请人做的纯 cube 模型（54 个方块，概念图「地堡收音机」BR-10）。我把它 Mesh 化，做成游戏方块。

用户 2026-10-14 定：**先不做接收信号**。V1 能摇、能开关、表盘会亮，不出声、不收广播。

状态：
- 代码、资源、生成器 `--check`、编译、GameTest 都通过；离线网页预览看过外观；
- 2026-10-14 用户第一次进游戏看：20 cm 的初版"也太小了吧"。整体放大到 2 倍（40 cm），见第 2 节；
- 2026-10-14 用户重启游戏看了 40 cm 版：**这一轮暂时 PASS**（"这轮暂时PASS，功能都正常，后面再做调频和发声"）。下一轮做调频和发声（第 8 节）。

## 1. 方块

| Registry ID | 中文 / English | 质量（估） | 搬运标签 |
|---|---|---|---|
| `apocalypse_firstlight:emergency_radio` | 手摇应急收音机 / Crank Emergency Radio | 3.0 kg | 无（普通物品） |

- 方块实体类型：`apocalypse_firstlight:emergency_radio`，存电量 `Charge`（tick）。
- 方块状态：

  | 属性 | 值 | 是什么 |
  |---|---|---|
  | `facing` | 四个水平方向 | 正面朝向；放的时候正面朝着放的人 |
  | `on` | true / false | 拨动开关在 FM / OFF |
  | `lit` | true / false | 在播：开着而且有电；表盘和电源灯发光 |
  | `crank` | 0..3 | 摇柄转到第几个四分之一圈 |
  | `surface` | `floor` / `table` / `counter` / `desk` | 放在什么上面，决定往下沉多少 |

- 创造栏：AFL 建筑方块，跟在小屋方块后面（`CabinBlocks#tabs`，排在石板后面）。

## 2. 尺寸和放法

- 机身 40 × 20 × 17.5 cm（大号手提收音机），放在格子正中；加上提手和天线高 33 cm，摇柄伸到 x 0.74。
  - 按 cube 模型的像素量：1 px = 2.5 cm，机身 16 × 8 × 7 px；
  - 经过：概念图写 14–16 cm，初版做成 20 cm（1 px = 1.25 cm）。2026-10-14 用户进游戏看了说"也太小了吧"（放在一格地上只占一小点），整体放大到 2 倍：几何、选框和碰撞、摇柄的判定线、提示的位置一起放大，物品显示的缩放减半（手里、物品栏里的大小不变）；
  - 生成器里只有一个比例常数 `K`（每 px 多少格），再要改大小就改它，再同步 Java 里的盒子、`CRANK_X`、提示点和物品显示。
- 要放在东西上面，放不了就不放：

  | 下面是 | `surface` | 下沉 | 理由 |
  |---|---|---|---|
  | 木桌 `wooden_table` | `table` | 0.24 格 | 桌面在 0.76 |
  | 木质橱柜 / 木质水槽柜 | `counter` | 0.10 格 | 台面在 0.90 |
  | 现代办公桌 `ModernOfficeDeskBlock` | `desk` | 2.5/16 格 | 桌面在 13.5/16 |
  | 别的顶面中间结实的方块 | `floor` | 0 | 直接放在上面 |
  | 都不是 | — | — | 放不了 |

- 下沉的时候整个模型、选框、碰撞一起往下挪。收音机是画在下面那格里的（桌子那格）。
- 下面的东西换了（桌子换成石头），`surface` 跟着变；下面的东西没了，收音机掉下来（掉落物）。
- 准星和子弹：机身在下面那格里，射线从侧面进桌子那格时也要能选中它。命中系统每过一格会多看一眼上面那格（[Mesh Hit Runtime V1](../rendering/mesh_hit_runtime_v1.md) 0.8 节，`MeshHitReachesDown`）。

## 3. 操作

- 右键要看点在哪：
  - **摇柄那头**（从正面看的左端，朝北画时 x ≥ 0.696，机壳到 0.7）：摇；
  - **其它地方**（正面、顶上、背面）：拨开关，开 / 关。
- 摇：
  - 按住右键，原版每 4 tick 重复一次使用，摇柄就每 4 tick 转四分之一圈；
  - 每四分之一圈充 60 tick 的电（播 3 秒），也就是摇 1 秒播 15 秒，摇 1 分钟播 15 分钟；
  - 满电 24000 tick（20 分钟），从空摇满要 80 秒；
  - 每四分之一圈扣耐力 0.4（`costs.crank`，每秒 2 点，和挖掘一样，打断恢复）。摇满一次要 160 点，比满耐力（100）多，中间会力竭一次；
  - 力竭时每下只充 48 tick（×0.8，`winded.dig_speed`，[耐力系统](../gameplay/stamina_system_v1.md)）；
  - 声音：原版弩上弦（中段）当占位，音量 0.2，音调随转角 1.5–1.8。
- 开关：
  - 开机拨到 FM，关机拨到 OFF。V1 只有这两档，AM 档没做；
  - 声音：原版拉杆，音量 0.25，开 1.8、关 1.6。
- 播：
  - 开着而且有电时 `lit`：表盘背光（暖黄）和电源灯（红）发光，方块光 2；
  - 电每 tick 少 1，客户端同步往下数（提示和 Jade 用）；
  - 电用完：灯灭，开关还在 FM；再摇就又亮；
  - V1 不出声。
- 拆：电量跟着物品走（战利品表 `copy_nbt`：`Charge` → `BlockEntityTag.Charge`），再放下还是那么多电；开关状态不带，放下是关着的。

## 4. 模型和材质

- Mesh 拼块（`CabinPieces`），都画成朝北（正面在 z < 0.5），游戏里按 `facing` 转，再按 `surface` 往下沉（`CabinPieces.Sink.add(名字, 转角, dy)`）。
- 拼块在 `cabin_mesh/radio/`：

  | 拼块 | 四边形 | 什么时候画 |
  |---|---:|---|
  | `body` | 1176 | 一直画 |
  | `dial_off` / `dial_lit` | 30 / 30 | `lit` 关 / 开 |
  | `slider_off` / `slider_fm` | 26 / 26 | `on` 关 / 开 |
  | `crank_0` .. `crank_3` | 60 × 4 | 按 `crank` |

  一个状态画 1292 个四边形。
- 正面（从左到右看）：
  - 喇叭：黑钢板，圆角方框里冲 12 道横槽，后面是黑布，四角各一颗螺丝；
  - 表盘窗：黑色边框，刻度单独一张图，红色指针；
  - 下面：大的调台旋钮（右）、小的音量旋钮、红色电源灯、AM / FM / OFF 拨动开关。
- 顶上：钢带提手，两个用螺丝固定的脚；右后角一根镀铬拉杆天线。
- 背面：一块素钢铭牌（不印字），电池盖带一道边，四角螺丝。
- 左端（+x）：折叠摇柄，cube 模型里没有，按计划 F4 的"手摇充电"加的。黑色轴座 + 钢帽，钢摇臂长 2.5 px（约 6 cm），电木把手；四个转角各一个拼块。
- 机壳是倒角钢盒，倒角磨出钢色。
- 贴图在 `textures/block/radio/`，240 texel = 1 m，LabPBR（`_s`、`_n`），由 `tools/radio/emergency-radio-art.mjs` 生成：

  | 贴图 | 用在 | 材料 |
  |---|---|---|
  | `case_paint` | 机壳 | 灰绿色烤漆，半光，非金属 |
  | `case_edge` | 机壳倒角 | 磨出来的钢，金属 |
  | `trim` | 喇叭板、表盘框、开关板、提手、摇柄座 | 黑色缎面钢，非金属漆面 |
  | `cloth` | 喇叭布、槽底 | 近黑色哑光布 |
  | `knob` | 旋钮、拨动开关、摇柄把手 | 黑色电木，略亮 |
  | `pointer` | 旋钮指示条 | 米白 |
  | `steel` | 螺丝、铭牌、电池盖、摇臂 | 钢，金属 |
  | `chrome` | 天线、电源灯框 | 镀铬，金属 |
  | `needle` | 表盘指针 | 红 |
  | `dial` / `dial_glow` | 表盘（关 / 开） | 单独一张 448 × 128 的图 |
  | `lamp` / `lamp_glow` | 电源灯（关 / 开） | 红色灯罩 |

- 表盘：
  - FM 88–108，线性；AM 530–1600 kHz，分段（按真收音机的刻度疏密）；
  - 字是笔画字，只有刻度数字和 FM、AM、kHz、MHz；指针停在 FM 98；
  - 关着是米白底，开着是暖黄背光，墨迹不发光（自发光 0.85 × (1 − 墨)）。
  - 表盘是唯一超过 240 texel 密度的贴图：刻度数字是收音机正面本来就有的功能印刷，不算装饰。别的地方都不印字（不画装饰）。
- 发光：`_glow` 结尾的贴图由小屋模型加载器当发光面画（固定满光、不压暗，[Cabin Exterior V1](cabin_exterior_v1.md)"技术"）。没开光影时黑处也亮；开光影时靠 `_s` 的 alpha 自发光。
- 物品：在 GUI 里转 [25, 210, 0]，缩放 1.7，模型抬高 0.36 格放正（放大前是 3.4 和 0.43，其他显示的缩放也都减半，看起来一样大）。
- 选框和碰撞：四个盒子（机身、提手、旋钮的前沿、摇柄转过的范围），跟着 `facing` 转、跟着 `surface` 下沉。准星的选框是网格的折边（Mesh Hit）。

## 5. 挖掘和掉落（AGENTS.md 审计）

- 小家电，不是工业设备：
  - 在 `minecraft:mineable/pickaxe`；
  - 不在 `needs_*_tool`，不要求用对工具才掉落：空手拆也掉自己；
  - 生成器 `--check` 会审这几条（也审不在斧标签、不在搬运标签）。
- 硬度 1.0，抗爆 3.0，金属声；活塞推它会碎（`DESTROY`）。
- 在 `apocalypse_firstlight:noise_metal_blocks`。
- 2026-10-14 GameTest 验过：生存模式空手、木镐拆都掉自己，带着电量。**没在游戏里实机确认。**

## 6. 接入

- 提示（一个动作词，`WorldInteractionHint#emergencyRadio`）：

  | 对着 | 提示 | 显示在哪 |
  |---|---|---|
  | 摇柄那头 | 摇动 / Crank | 摇柄轴那里 |
  | 别处，关着 | 开机 / Turn on | 正面开关那里 |
  | 别处，开着 | 关机 / Turn off | 正面开关那里 |

- Jade（`compat/jade/EmergencyRadioJadeProvider`）：
  - 状态：开机 / 没电 / 关机（On / Flat / Off）；
  - 电量：电量 x%（按满电 20 分钟算，向上取整）；
  - 配置项：AFL 应急收音机。
- 语言（en_us / zh_cn 逐行对齐）：方块名在 `flagstone` 后面，Jade 在 `box_stove.cooking` 后面，Jade 配置在 `box_stove` 后面，提示在 `box_stove.take` 后面。
- 耐力：`stamina_v1.json` 的 `costs.crank` = 0.4（`StaminaConfig.Costs#crank`）。
- 质量：`item_mass/afl_content_v1.json`，3.0 kg，估的（放大前 1.2 kg）。
- 开局老宅：老宅里本来就有收音机桌（[Wood Furniture V1](wood_furniture_v1.md)，(6, 7)）。收音机没放进「house test」，用户自己摆。

## 7. 文件

- 代码（`src/main/java/com/antaurora/apofirstlight/`）：
  - `cabin/EmergencyRadio.java`：方块、方块实体类型注册、放法、操作；
  - `cabin/EmergencyRadioBlockEntity.java`：电量、充电、放电、同步；
  - `cabin/CabinBlocks.java`：注册（同一串 ID、同一个 Mesh 加载器）；
  - `cabin/CabinPieces.java`：`Sink.add` 多了下沉量 `dy`；
  - `cabin/CabinMeshModel.java`：发光面；
  - `meshhit/CabinMeshHits.java`：命中网格跟着下沉；
  - `meshhit/MeshHitReachesDown.java`、`meshhit/MeshHitClip.java`：从侧面也能选中；
  - `compat/jade/EmergencyRadioJadeProvider.java`、`compat/jade/AflJadePlugin.java`；
  - `client/WorldInteractionHint.java`；
  - `stamina/StaminaConfig.java`。
- 生成器：
  - `tools/build-emergency-radio-v1.mjs`：拼块、方块 / 物品模型、方块状态、战利品表；`--check` 核对输出是最新的，并审标签、质量、搬运标签、语言、耐力消耗；
  - `tools/radio/emergency-radio-art.mjs`：贴图和表盘；
  - `tools/cabin/cabin-art.mjs` 的 `renderX` 多了自发光 `e`。
- 可编辑源：`src/main/blockbench/emergency_radio_cube_v1.bbmodel`，用户的 cube 模型原样（和下载的文件一样）。游戏里的 Mesh 是生成器按从它量出来的尺寸算的，不从 `.bbmodel` 导出。
- 资源：
  - `blockstates/emergency_radio.json`；
  - `models/block/radio/emergency_radio.json`（加载器 `apocalypse_firstlight:cabin`）、`models/item/emergency_radio.json`；
  - `cabin_mesh/radio/*.json`（9 个拼块）；
  - `textures/block/radio/`；
  - `data/apocalypse_firstlight/loot_tables/blocks/emergency_radio.json`。
- 数据：`minecraft:mineable/pickaxe`、`noise_metal_blocks`、`item_mass/afl_content_v1.json`、`stamina/stamina_v1.json`。
- 离线预览：
  - `design/buildings/forest_start_home_v1/m2_source/radio_preview.html`（three.js，在 `m1_source/serve.cjs` 上开）：两台收音机放在拼起来的木桌上；
  - 截图在 `m2_sheets/m2_f4_radio_v1_*.png`（pair、front34、front、back34、crank、top、lit、lit34），2026-10-14 按 40 cm 重出（页面里的机位按比例 `S` 放大）；
  - 不是游戏截图：网页光照比游戏亮，没有色调映射。表盘框下面淡淡的斜纹应该是 three.js 的阴影瑕疵，不是贴图。
- GameTest：`src/dev/java/com/antaurora/apofirstlight/dev/ForestHomeM2GameTests.java`，跑法：

  ```bash
  ./gradlew runGameTestServer -PaflWithoutShaders --offline --init-script src/dev/forest-home-m2-gametest.init.gradle
  ```

## 8. 没做 / 已知限制

- 不收信号，不出声：用户定**下一轮做调频和发声**（2026-10-14）；底噪、广播文字、广播声音和计划 P8 剧情钩子一起，声音要 Firefly 素材。
- 摇和开关的声音是原版占位。
- 只有 FM / OFF 两档，没有 AM 档；调台、音量旋钮不能转；天线不能拉。
- 表盘窗没有玻璃片。
- 没有合成配方。

## 9. 验证记录

- 2026-10-14（游戏关着）：
  - `node tools/build-emergency-radio-v1.mjs --check`：CHECK OK（52 个输出），审计 0 问题；改过 `cabin-art.mjs` 后 `build-cabin-exterior-v1.mjs`（352）、`build-box-stove-v1.mjs`（91）、`build-wood-furniture-v1.mjs`（82）的 `--check` 也都 OK；
  - `compileJava` + `compileDevJava` 通过；
  - 离线预览两轮：第一轮表盘数字挤、指针压在 100 上，改小字、AM 分段刻度、指针挪到 98 后第二轮干净；
  - GameTest `ForestHomeM2GameTests` PASS（服务端假玩家）：
    - 下面是桌子 / 橱柜 / 石头 / 空气时的 `surface`；
    - 放在桌上：朝向、`table`、关着、没电；
    - 点摇柄那头：`crank` 1、电量 60、不亮；点正面：开、亮、方块光 2；
    - 从正面 3.5 m 外斜着看，射线从桌子那格进来，选中的还是收音机；
    - 所有状态的 9 个拼块文件都在，下沉量都对；
    - 80 tick 后：电量 0、灭了、开关还开着；再摇：又亮、`crank` 2；
    - 空手拆：掉自己，带 `Charge` 60；再放下：电量 60、关着；
    - 桌子换成石头：`floor`；石头拿掉：收音机掉下来；
  - 小屋屋面 GameTest（`cabin_roof`）回归通过（`Sink` 接口改了）。
- 2026-10-14（同日，用户实机，放在草地上）：20 cm 太小。放大到 40 cm 后：生成器 `--check` OK（52 个输出）；量了网格包围盒，都在格子里（x 0.30..0.74，高到 0.33），和 Java 的碰撞盒对得上；GameTest `ForestHomeM2GameTests` 按新的点击位置重跑 PASS；预览图重出。
- 2026-10-14（同日，用户重启游戏实机）：**暂时 PASS**，"功能都正常"。用户没有单独说每一项（光影下的外观、Jade、生存挖掘），按"功能都正常"记。
