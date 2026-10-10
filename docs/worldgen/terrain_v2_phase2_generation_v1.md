# Terrain V2 Phase 2：r1 规划接入实际地形生成

日期：2026-10-10。状态：**已实现，编译通过（compileJava + compileDevJava），等用户在全新世界实机验收**。没有跑游戏、GameTest 或服务器（按"低消耗编译规则"）。

用户 2026-10-10 验收 r1 规划（[研究报告](terrain_v2_real_terrain_research_v1.md)）后批准："把 r1 规划器接入 Minecraft 实际地形生成"。要求：
- 新地形高度与规划一致；
- 保留主岛与三座卫星岛；
- initial/final 密度链一致；
- 处理好地下洞穴与近地表稳定层；
- 避免区块接缝；
- 统一河口、水域与海陆掩码；
- 不同种子保持确定性；
- 规划器不能反复初始化造成卡顿。

本轮不做完整群系重构、高速、城市、住宅。第一步（旧逻辑退役）见 [legacy_worldgen_retirement_v1.md](legacy_worldgen_retirement_v1.md)。

## 结构

```text
世界加载：RandomState 构造 → RandomStateSeedMixin 把噪声路由里的 plan_* 绑定到种子
          → TerrainPlanStore.get(seed)：内存 → 磁盘缓存 → 构建一次（约 9 s）
区块生成：overworld.json final_density = plan_terrain
            陆地（离水 ≥ 48 m）：原版洞穴图 terrain/plan_caves，地表是 0.125 ×（规划高度 − y）
            水域（统一掩码：宏观海 + 溺谷河口）：实心海床，海床 6 格以下才有洞
            0–48 m 岸带：两者平滑过渡（同一个地表，只是洞穴不同）
          initial_density_without_jaggedness = plan_initial_density（同一个规划高度）
          雕刻器：WorldCarverStabilityMixin 在稳定层内不挖
          水：NoiseChunkMacroWaterMixin 宏观海照旧，河口湾的空气补到海平面
出生：TerrainV2SpawnEvents 从规划里挑最近原点的干燥平原格
```

| 文件 | 作用 |
|---|---|
| `worldgen/terrain/v2/TerrainPlanV2.java` | r1 规划器（从 `tools/` 移进 mod，研究工具也编译这一份，只有一份源码）。行级循环并行化：输出与冻结的 r1 逐字节相同（h、河网、水面、汇水、分区五个文件 SHA-256 一致），初始化 4.7 s → 0.75 s，全程约 8.7 s |
| `worldgen/terrain/v2/PlanNoise.java` | 规划用的确定性噪声（规划器和运行期共用，保证同一个结果） |
| `worldgen/terrain/v2/TerrainPlanSurface.java` | 运行期紧凑数据：规划高度（float）、水面类型（0 陆 / 1 海 / 2 河口湾 / 3 湿地）、山带权重、稳定层深度、离水距离（±127 m），约 20 MB；`heightAt` 与研究出图同一公式；读写磁盘（格式号、版本、种子、CRC 校验）；`naturalSpawn` |
| `worldgen/terrain/v2/TerrainPlanStore.java` | 缓存：内存最多 2 份；磁盘 `<游戏目录>/afl_cache/terrain_plan/afl_terrain_plan_v2_r1_<种子>.bin`（deflate，坏文件或旧版本自动重建，写入用临时文件 + 原子替换）；同一种子只构建一次（单飞） |
| `worldgen/terrain/v2/PlanHeightDensity.java` | `apocalypse_firstlight:plan_height`，外面包 flat_cache + cache_2d：每个区块的每个 4×4 柱只算一次 |
| `worldgen/terrain/v2/PlanStabilityDensity.java` | `apocalypse_firstlight:plan_stability`：稳定层（见下） |
| `worldgen/terrain/v2/PlanTerrainDensity.java` | `apocalypse_firstlight:plan_terrain`：陆 / 水路由（替换 `MacroTerrainDensity`） |
| `data/apocalypse_firstlight/worldgen/density_function/terrain/plan_*.json` | `plan_height`、`plan_sloped_cheese`（= 0.125 ×（H − y），与原海洋分支同一形式）、`plan_stability`、`plan_caves`（原 macro_caves 的原版洞穴图，地表换成规划，洞口与面条洞加稳定层）、`plan_ocean_caves`、`plan_initial_land`、`plan_initial_density` |
| `data/minecraft/worldgen/noise_settings/overworld.json` | `final_density`、`initial_density_without_jaggedness` 两项改指新链 |
| `mixin/RandomStateSeedMixin.java` | 绑定 plan_* 到种子；新增 `apocalypse$hasTerrainPlan()` |
| `mixin/WorldCarverStabilityMixin.java` | 洞穴、峡谷雕刻器的逐块检查 |
| `mixin/NoiseChunkMacroWaterMixin.java` | 河口湾的水 |
| `world/biome/MainNationBiomeRegionPlan.java` | 河口湾格的群系按水域（Ocean），与宏观海一致 |
| `worldgen/terrain/v2/TerrainV2SpawnEvents.java` | 新世界出生点 |
| `src/dev/.../dev/TerrainV2Command.java` | 验收命令（见下） |

旧链（`LandTerrainRelief` / `LandTerrainBias` / `InlandElevationBias` / `MacroTerrainDensity` 和 macro_* JSON）还在，只是不再被 `overworld.json` 引用；验收通过后再清理。

## 关键做法

- **高度一致**：密度 = 0.125 ×（规划高度 − y），和原版近地表的密度梯度一样，所以原版洞穴图里"地表下约 12.5 格内只有洞口、更深才有大洞"的阈值照常工作；没有原版的三维 base noise，地表就是规划高度（4×4 角点上算、双线性插值，规划的最小细节 16 m，不丢东西）。initial（初步地表，含水层、地表规则、地下群系判断用）是同一个高度减 0.703125，与原版两条链的关系相同。
- **稳定层**：在规划地表以下保持实心。平原和海岸平原 12 格，山前 6，褶皱山带 4（山坡上保留洞口），海床和河口湾床 6。两条路都管：
  - 密度侧：洞口项和面条洞项加上 1.5 ×（稳定深度 − 深度），地表处需要洞口噪声低于 −3.6，实际不会出现；
  - 雕刻器侧：每个要挖的方块都查一次，在稳定层内就不挖。
- **接缝**：高度来自一张连续的规划图，和区块无关；陆和水两个分支用同一个地表，只有洞穴不同。
- **海陆掩码**：规划在 `MacroGeography` 的海之外又淹了一部分河谷（本种子河口湾约 2.0 km²、湿地 1.3 km²）。生成侧（地形、水）和群系侧（河口湾 → Ocean）都按规划的统一掩码；湿地是 Y63.4 的陆地。
- **初始化**：规划只在世界加载、绑定噪声路由时取一次，区块线程只读成品。第一次在这台机器上用某个种子时构建约 9 s（加载界面会多停这么久，日志有 `[AFL Terrain V2] building plan`），以后读磁盘缓存。
- **确定性**：规划纯种子、单线程等价（并行循环只写自己的格）；缓存文件内容就是构建结果。

## 实机验收（全新世界）

1. 新建世界，**种子 `-295378578869149513`**（研究报告里的图就是这个种子），默认世界类型。看地形建议用创造模式：出生点小安全泡外是 Fallout 荒原（群系还没重构），辐射重。
2. 第一次进世界看日志：
   - `[AFL Terrain V2] building plan afl_terrain_plan_v2_r1 for seed … (first time on this machine, about 10 s)`
   - `ready in … ms (built …)`
   - `[AFL Terrain V2] spawn 1112 … 1112`
3. 开发命令（op）：
   - `/afl dev terrain_v2 here`：脚下的规划地表、实际地面顶块和差值、水面类型、稳定层深度、离水距离、坡度；
   - `/afl dev terrain_v2 check 8`：玩家周围已加载区块（不会生成新区块）的统计：
     - 实际地表与规划的平均 / P95 / 最大偏差，1 格以内的比例；
     - 跨区块边界的平均台阶与区块内部的平均台阶（两者接近就没有接缝）；
     - 稳定层里有洞的柱数；
     - 河口湾柱在 Y62 是否有水；
   - `/afl dev terrain_v2 points`：本种子的出生点。

| 项目 | 测试点 | 坐标 X Z（规划地表 Y） | 看什么 |
|---|---|---|---|
| 5 出生位置 | spawn | 1112 1112（78.5） | 出生在干燥平地上，不在水里、河边或湿地；离水 ≥ 64 m |
| 1 平原 | plain_flat | 1096 1128（78.6） | 冰碛平原台地：整体平坦，1–2 格的缓起伏，不是一张平板 |
| 1 平原河谷 | plain_valley | −280 5016（66.7） | 平原上最大的河谷：宽浅的冲积平原、谷边陡岸、侧面冲沟（河里暂时没有水，见"未做"） |
| 1 终碛 / 分水岭 | moraine_or_divide | 3336 −4904（89.0） | 平原最高处 |
| 2 山脊 | belt_crest | −1128 −2120（228.1） | 褶皱山带最高的山脊顶 |
| 2 山谷 | belt_valley | 456 −4520（68.3） | 两道山脊之间的走向谷 |
| 2 穿山缺口 | water_gap | 392 −4808（67.3） | 主干河切穿山脊的缺口（山口通道） |
| 2 山前丘陵 | foothills | 2568 −4936（91.8） | 被切割的山前丘陵 |
| 3 河口 | estuary | 104 5880（61.1） | 最大的溺谷河口湾：海平面 Y63 的水，水下是河谷床 |
| 3 湿地 | marsh | 4296 2648（63.4） | 潮汐湿地（Y63.4 的平地） |
| 3 海岸平原 | coastal_plain | 4024 2456（70.0） | 海岸台地 |
| 6 接缝 | seam_test | −2520 1608（126.7） | 山带最陡的坡：看区块边界有没有断崖，`check` 的跨界台阶应接近区块内台阶 |
| 卫星岛 | satellite | −8344 3368（71.8） | 卫星岛 1 中部 |

- **4 地下浅层**：在平原测试点往下挖 12 格、或用旁观模式看地下，近地表应是实心；`check` 的"稳定层有洞的柱"应接近 0。更深处的洞穴和矿洞照常存在。
- **7 保存与重载**：退出、重进同一个世界，已生成的地形不变，日志显示 `ready in … ms (disk cache)`；继续往外走，新区块和旧区块无缝。
- **8 性能**：
  - 第一次建世界多 9 s 左右，之后每次加载约 1 s 以内；
  - 正常飞行时区块生成速度应和以前相近（规划高度每个区块只算 25 个柱，其余是查表）；
  - 有明显卡顿请记下坐标和日志里的 `Can't keep up`。
  - 缓存文件：开发环境在 `run/afl_cache/terrain_plan/`，可以删掉它来测"第一次构建"。

坐标由 `tools/terrain-v2-research/java/.../TestPoints.java` 按固定规则从规划里挑出（`java -cp … TestPoints <种子>`）。

## 未做 / 已知限制

- **群系**：仍是旧的"默认 Fallout + 少量附加 Plains"（Phase 2 验收后才重构）；只把河口湾改成了 Ocean。
- **河流没有水**：规划里有河道和河谷，但河水（宽度、水面、与含水层一致）是 Phase 2b，没有做。只有海和河口湾有水。
- **湿地**是 Y63.4 的普通陆地（群系还是 Fallout / Plains）。
- **规划构建约 9 s**，只在每台机器第一次用该种子时发生；加载界面没有专门的进度提示。
- 没有原版的三维地形噪声，所以不会有悬崖凹进、浮岛之类的形状；山坡的岩石感靠规划的细节噪声。
- 旧世界不能用（新旧地形之间会有断崖）。
- 高速、城市、结构都没接新地形；`MacroGeography` 本身仍把河口湾判为陆地（只有生成和群系按规划的掩码）。
- 原版村庄等结构大多已被禁用；地形适配（beardifier）照原版。

## 回退

- 只退 Phase 2：把 `overworld.json` 的 `final_density` 改回 `{"type": "apocalypse_firstlight:macro_terrain", "land": "apocalypse_firstlight:terrain/macro_caves", "terrain": "apocalypse_firstlight:terrain/ocean_sloped_cheese", "underground": "apocalypse_firstlight:terrain/ocean_caves"}`，`initial_density_without_jaggedness` 改回 `apocalypse_firstlight:terrain/macro_initial_density`。改回以后新代码都不再被触发：mixin 发现 `hasTerrainPlan()` 为假就跳过。
- 第一步的回退见 [legacy_worldgen_retirement_v1.md](legacy_worldgen_retirement_v1.md)。
- 两步的文件互不重叠（只有 `MainNationBiomeRegionPlan.java` 两步都改了，各改一处）。
