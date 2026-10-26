# 物理水体交接：B → C（2026-10-26）

用户 2026-10-26 原话："我想把水体也交给C，因为他那边做河流需要水体，我不想传来传去的有点麻烦，你把我们对话里面的细节也写入文档"。

这份文档是交接的全部上下文：C 不用再问 B。先读第 1、2、7 节，其余按需查。

## 1. 交接范围

| 交给 C | B 留下 |
|---|---|
| 水的计算引擎 `liquid/`（格子、管道流、盆地、账本、休眠、下渗、地表水账） | 天气本身：`AflWeather`、`RegionalWeather`、雷电（水只读雨量） |
| 水的存档、同步、开发命令 `liquid/world/` | 体温里的湿度（`PlayerTemperature`，读入水深度） |
| 水的画法 `client/liquid/`（水面、落水、湿地面、`afl_water` 着色器） | 口渴、喝水、灌瓶（`PlayerThirst`，以后从 AFL 的水灌瓶时调 `WaterQuery`） |
| 实体入水 `WaterQuery`、`WaterContact`、`EntityAflWaterMixin`、`CameraAflWaterMixin` | 湿身（新功能，见 6.6，B 写方案时调 C 的水球接口） |
| 雨水进水体 `LiquidRain`、`SurfaceWater`（第 3 包） | 水生植物 P4b（以后读 `WaterQuery` 的水深、流速） |
| 河流方案、Mesh 水面 + 水球方案、第 5、6 包 | |
| 离线检查 `liquidCheck`、`liquidPerf`，`SyntheticGround` | |

B 交接后不再改 `liquid/`、`client/liquid/` 和上面的 mixin。B 要用水的地方只调公开接口，要新接口找 C。

## 2. 状态总表

| 部分 | 状态 | 在哪 |
|---|---|---|
| 第 1 包 引擎 | 离线 PASS | master（e9ae03b8 合进的 437b4ca3） |
| 第 2 包 存档、同步、水面 | 实机 PASS | master |
| F 包 落水（F1、跌落、倒水命令、涟漪） | 实机 PASS；F3 声音没做；第 19 节起不开光影不画空中的水 | master |
| 浑水、雨停在水面、斜坡按三参数碰撞形状读（落水方案第 20、21 节） | 用户 10-25 实机"还行吧，先这样" | master |
| 第 3 包 3a 地表水账 | 实机看过三次；地面暗化全部关掉（用户定）；压测 1,480 区块 1.0 ms / tick | master |
| 第 3 包 3b–3f | 没做；按河流方案放在 R2 之后、R3 之前 | — |
| 河流 V1 总方案 | 用户 10-25 批准，10-26 按 C 的河床改了 2.1 | B-Workbench，没提交 |
| 河流第 1 步 性能 | 离线完成，没进游戏 | B-Workbench，没提交 |
| 河流第 2 步 = 第 4 包 实体入水 | 编译通过，离线 122 / 0，没进游戏 | B-Workbench，没提交 |
| 连续 Mesh 水面 + 水球方案 | 写好，**等用户批** | B-Workbench，没提交 |
| 第 5 包（天然水体边界、原版水流接管开关） | 没做 | — |
| 第 6 包（完整水面 + Aureveil 接口） | 没做；Mesh 水面方案提前了它的一部分 | — |

网络协议在 B-Workbench 上是 **76**（第 4 包给水面包加了流速）。

## 3. 代码地图

| 文件 | 作用 |
|---|---|
| `liquid/LiquidEngine` | 每个维度一个：列 → 层（`LiquidCell`）→ 面（`LiquidFace`）。每 tick：雨和水位 → 唤醒 → 流动（三遍：水位差 + 曼宁摩擦 + 上限；按可用水量限；按空间限，整数毫升带零头搬运）→ 静止判断、睡觉、下渗。`spent[4]` 分项计时 |
| `liquid/LiquidCell` | 一层水：底、顶、容积曲线（`area`、`prefix`）、水量（mL）、水位、休眠、盆地、地面蓄水量；`velocity(double[])`（第 4 包） |
| `liquid/LiquidFace` | 两格之间的开口：每 1/16 m 的开口宽度、累计面积、流量 q（m³/s）、零头 carry；归西 / 北那格所有 |
| `liquid/LiquidColumn`、`LiquidBasin`、`LiquidLedger` | 一列的各层；连在一起的静水；每区块账本（雨入、边界进出、在空中、地表水） |
| `liquid/LiquidFall` | 跌落点（水位落差 ≥ 0.5 m，平滑坡不算），每秒流量 |
| `liquid/LiquidSoak`、`SurfaceWater` | 地面蓄水、下渗、蒸发；每列地表水账（3a） |
| `liquid/LiquidSurfaceEdge` | 坡顶水面角点贴到斜面（落水方案第 21 节） |
| `liquid/WaterQuery` | 第 4 包：按列问水层（底、水面、流速），`surfaceAt`、`immersion`；服务器问引擎，客户端问同步的水面；只算 ≥ 2 cm 的水，不回答边界格 |
| `liquid/ground/BlockGroundView` | 从方块读地面：**必须用三参数 `getCollisionShape(level, pos, CollisionContext.empty())`**，平滑坡伸进上一格的部分算实心 |
| `liquid/ground/GroundViewV0`、`GroundMaterialV0` | C 的 GroundView V0 签名的镜像（换成 C 的正式 GroundView 时替换） |
| `liquid/world/LiquidWorld` | 每个 ServerLevel 一个引擎；区块读写（NBT `apocalypse_firstlight:water`）；每 2 tick 发水面包；`perf` 分项计时；`engineIfPresent` |
| `liquid/world/LiquidPackets` | 水面（每格：列、底、水位 1/4096 m、标志、流速 2 cm/s）、跌落、倒水、湿地面 |
| `liquid/world/LiquidRain`、`LiquidPours`、`LiquidDevCommand`、`WaterContact` | 天然雨；倒水；开发命令；实体入水规则 |
| `mixin/LiquidServerLevelMixin` | 方块变化 → 地面变了 |
| `mixin/EntityAflWaterMixin`、`mixin/client/CameraAflWaterMixin` | 第 4 包挂钩 |
| `client/liquid/ClientLiquid` | 客户端水面数据和画法（每区块缓存网格，`afl_water`）；`WaterQuery` 的客户端源 |
| `client/liquid/WaterLook` | 不开光影时的水着色器 `afl_water`（复制画面、Beer-Lambert、折射、菲涅尔、太阳高光、两层波纹；浑水参数） |
| `client/liquid/ClientFalls` | 空中的水（不开光影时直接返回，第 19 节） |
| `client/liquid/ClientRainWet` | 湿地面（关着，`/afl_render wet` 开发用） |
| `fluid/LiquidJet`、`client/LiquidJetRenderer` | 水滴链 + 管状渲染（加油枪做的，用户要求所有喷射复用）；水球方案在它上面加水量 |
| `src/test/.../liquid/LiquidCheck`、`LiquidPerf`、`SyntheticGround` | `gradlew liquidCheck`（122 项）；`gradlew liquidPerf -Pscene=hill|pond|channel -Pjfr=文件` |

## 4. 文档地图

| 文档 | 内容 |
|---|---|
| `docs/gameplay/water_v1_design.md` | 总设计；第 14 节用户决定；第 15–16 节第 1、2 包；第 17 节第 4 包 |
| `docs/gameplay/water_falling_v1_design.md` | 落水；第 11–21 节七次实机和根因 |
| `docs/gameplay/water_rain_v1_design.md` | 第 3 包雨水；第 10 节用户批准；第 11–15 节 3a |
| `docs/gameplay/water_river_v1_design.md` | 河流总方案；第 12 节用户决定；第 13 节 C 的河床；第 14 节第 1 步性能；第 15 节第 2 步；第 16 节 Mesh 方案提议 |
| `docs/gameplay/water_mesh_parcels_v1_design.md` | 连续 Mesh 水面 + 水球方案（待批）；第 10 节雨滴和湿身 |
| `docs/gameplay/world_time_b_units_v1.md` | 第 2 节：雨量按游戏小时换算（**还没做**，见 7.3） |

## 5. 用户已定的事（按时间，原话）

- **10-24 不要原版水方块：** "不是原版水方块的视觉增强或有限流动改版"；要"挖一个坑，下雨能蓄水。挖一条沟，水会真正流进去。水量有限，水面连续，地形决定水流"。设计第 2 版同日批准；原版水流分阶段接管，先验证兼容，不一次全局关掉。
- **10-24 多液体：** 通用框架，V1 只做水；**汽油、柴油、工业废液、岩浆的代码和数值不改**。单位 1 mB = 1 L，内部用 mL。
- **10-24 落水：** "都按照推荐做，先不做容器倒水"：空中的水带水量，落地后才进下面；倒水只做开发命令。
- **10-25 雨水第 3 包批准：** 水坑方案 B、屋顶当汇水面、3a → 3f；性能数字要实机压测才算 PASS。
- **10-25 地面不暗化：** "如果只能这么丑，干脆别做暗面了呗，效果交给光影"。湿地面全部关掉，数据照发给以后的 Aureveil。
- **10-25 落水第 19 节：** "别搞了吧，卡死了 / 后面的效果就让光影做吧，屋檐滴水什么的，我们原版不做了"。不开光影只画 2 cm 以上的积水，不画空中的水和坡上的流水。**10-26 的 Mesh 方案提议推翻这一条（等批）。**
- **10-25 先这样：** 看过浑水和斜坡根因修正后说"还行吧，先这样"。
- **10-25 河流：** "新世界不放原版河水：河里只有 AFL 的水 / 按上面 1–5 的顺序 / 第 3 包剩下的部分放在 R2 之后、R3 之前"。顺序：性能 → 第 4 包 → R1 → R2 → 3b–3f → R3。湖和海这一版还是原版水当边界。
- **10-26 C 的河床：** 用户批准 C 的 9.1；河床由 C 给（宽 W = max(2.5, 3·A^0.42)，深 D = 0.40·A^0.22 限 0.35–1.4 m，常水位在满槽下 0.6 D，曼宁 n 按河床材料，连续河床高度 η 由 C 给）。
- **10-26 性能预算：** "同意，预算改成河道每格每子步 ≤ 250 ns、活动窗口上限 6,000 格，接着做第 2 步（第 4 包）"。
- **10-26 Mesh 水和水球：** 见第 6 节。

## 6. 今天（10-26）对话里的细节

### 6.1 第 1 步性能

- 游戏里雨中 7 ms 来自开发命令 `/afl dev water rain`（逐列下雨，坡上每格都成了流动水膜）。天然雨走 3a 地表水账，只在已经有水的地方进引擎，不会这样。
- 修了四处：
  - 水柱索引用 `HashMap<Long>`，`Long` 的哈希是 x^z，大量冲突 → fastutil `Long2ObjectOpenHashMap`；
  - 池边碎格 1 mL 来回晃永远睡不着 → `ROCK_ML` 5 mL 也算静止；
  - 下渗重置了静止计数 → 不再重置；
  - 干掉的休眠水膜变成几千个盆地；每 tick 扫所有干格 → 只扫有水的格和它们的邻格（面的顺序不变，没看到的面清零）。
- 结果：池塘 230–285 ns / 格 / 子步，水渠 170–240 ns；雨停后山坡全部睡着（0.79 → 0.27 ms）。
- 并列数组（SoA）实测只快 20%：成本在算术，不在内存，没做。
- 实机读数没测：天然雨下用 `/afl dev water rainforce 20` 再 `/afl dev water perf`。

### 6.2 第 4 包

细节见 `water_v1_design.md` 第 17 节。要点：
- 水到身体高度 45% 就当原版的水（游泳、浮力、溺水、溅水、摔落清零、灭火），更浅是涉水减速；
- 流水按真实流速推人，浮着的玩家漂到水速约 80%，掉落物约等于水速；
- 眼睛和镜头低于 AFL 水面就是水下；
- 不覆盖：按水深 × 流速被冲倒（河流第 7 节，R1 之后）、船、灌瓶。
- 测试 E 的步骤在第 17.5 节，没进游戏。

### 6.3 实机截图：坡上的水断开

用户 10-26 看实机（水沟连着两个水池，中间斜坡那段没有水）："还是有之前的那个老问题，水流不连续，在斜坡会断掉 / 你到底这不知道我要的是哪种风格的流体水？/ 那些3D生存游戏，比如说《七日杀》是怎么做的？现在还是平面水 / 有没有可能做mesh水？"

- **原因：** 坡上流动的水只有几毫米（每秒 2 升流过 1 m 宽、坡度 0.2 的坡约 5 mm、0.6 m/s），第 19 节规则不到 8 mm 不同步、不画。计算里水是连着的。
- **《七日杀》：** A21 改成水体素，官方说明写明水不会像河或瀑布那样持续流动，不是目标。

### 6.4 "每 1 mB 是一个水球"

用户原话："也就是说每1mB实际上是一个水球，你懂吧？不然后面很难模拟液柱，比如说我打爆了有水的水管，水会真的喷出来，现在我们的漏油实际上就还行，但是不真实"。

- 回答：全部做成水球不行（1 L 球 12 cm，比坡上的薄水厚 20 多倍；一个小水池 5.9 万升，算不动）。
- 推荐：格子算水体，离开水体的水（喷射、跌落、倒水、溅起）是带真实水量的水球，落地交还格子。用户同意按这个写方案。
- 用户问要不要用 Blender 模拟：不用。Blender 流体是离线的另一套算法，结果搬不进游戏；验证用 Java 离线检查，外观对真实视频。
- 方案：`water_mesh_parcels_v1_design.md`。M1 连续水面 → M2 水球和账本 → M3 水柱、水滴画法 → M4 爆管命令，排在 R1 前面。第 9 节 5 条待批（推荐：推翻第 19 节、1 L 水球上限 2,000、撞人弄湿推人、M1–M4 在 R1 前、燃料漏液这次不动）。

### 6.5 以前坡上流水层为什么失败（C 必读）

落水方案第 16–19 节三轮都没成，后来查明：
1. 引擎在游戏里一直用两参数 `getCollisionShape`，平滑坡全被读成整格（第 21 节，已修）；
2. 7 ms 是开发用逐列雨（6.1，已修）；
3. 三套材质看着割裂（第 17 节）。

这次只用一套 `afl_water`。**改画法之前先读存档或游戏里的实际数据**；离线场景自己造地面，绕过了游戏读形状那条路。

### 6.6 雨滴和湿身（用户问，B 的功能）

用户原话："后面的雨滴也能用水球吗，因为我想做湿身效果，雨滴或者入水之后，东西会真的湿掉 / 特别是雨滴打在枪械上面会很好看"。

- 雨不做成水球：2 mm 的雨滴每平方米每秒约 1,300 颗，雨还是按每列 mm/h 算。
- 湿身是 B 的单独系统（身体、衣服、手里的枪、方块的湿度，来源：雨、入水、水球）。枪上的雨滴复用 T15 挡风玻璃的 `GlassWater`。建议放在 M2 之后。
- **C 要留的接口：** 水球撞到实体时回调"这个实体被多少 mL 的水打中、打在哪"，B 的湿身接上去。

## 7. 接手后要做的（按顺序）

1. **等用户批 Mesh + 水球方案第 9 节**，然后 M1。
2. 用户在游戏里看：第 4 包测试 E、天然雨下 `/afl dev water perf`。
3. **雨量按游戏小时换算**（`world_time_b_units_v1.md` 第 2 节）：`LiquidUnits.ML_PER_TICK_PER_MM_H = 1.0` 还按 1,000 tick 一小时算，一天 60 分钟时每场雨的水量是设计的 3 倍。改成 `AflTime.perTick`，重跑 `liquidCheck`。
4. R1（C1–C3、C7：新世界不放原版河水的开关要和 R1 一起上）→ R2 → 3b–3f → R3。
5. 第 5 包：C 的 R0 已让 `NaturalGroundBlock` 带 WATERLOGGED（`getFluidState` 返回原版水源），第 5 包要把 `getFluidState()` 有水的格当边界。
6. 换成 C 的正式 GroundView 时，`LiquidSoak` 的穷举 switch 要加 ASPHALT / CONCRETE / COMPACTED_GRAVEL（路面排水 D1）。
7. 已知限制：坡顶在一格中间时一格只有一个水位，挡不住水（落水第 21 节）；F3 落水声音。

## 8. 规则和教训

- 汽油、柴油、工业废液、岩浆的代码和数值不改（10-24）。
- 地面不暗化，湿的样子交给光影。
- 不开光影的水效果：现在只画积水；Mesh 方案批了才改。
- 一套水材质，不再分开画。
- 测性能用真实场景，不用开发命令的逐列雨下结论。
- 地形相关的 bug 先读存档 / 游戏数据（`reference-two-arg-collision-shape`）。
- 每次改水都跑 `liquidCheck`；离线检查不算实机 PASS。
- 不要在游戏开着时编译（会崩）；不要自动开客户端；用户自己提交。

## 9. 工具和命令

- 离线：`gradlew liquidCheck`、`gradlew liquidPerf -Pscene=hill|pond|channel [-Pjfr=out.jfr]`；`jfr print --stack-depth 40` 才有完整调用栈。
- 游戏内：`/afl dev water here | fill <L> | drain <L> | rain <mm/h> [半径] | rainforce <mm/h> | surface | rainstats | ledger | perf | pour <L> [L/s] | pour stop | clear [半径] | pause | resume`；`/afl_render falls status`、`/afl_render wet`。

## 10. 合并

- B-Workbench 和 C 的 `feature/c-worldgen` 都还没提交。建议用户先让两边各自提交，再由 Main 合进 master。
- 会撞的文件：`apocalypse_firstlight.mixins.json`（B 加了 `EntityAflWaterMixin`、`client.CameraAflWaterMixin`）、`network/AflNetwork.java`（B 是 76，C 也升过的话取大的再 +1）、`build.gradle`（B 加了 `liquidPerf`）、`docs/项目内容/02 - 制作清单.md`、`wiki/world/weather.md` 和英文版。
- B 没碰 `ground/`、`terrainsmooth/`、`worldgen/river/`。
- 合并后 C 从 master 接着做水。
