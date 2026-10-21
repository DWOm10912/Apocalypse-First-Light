# 联邦六州国家地图 V1 — C2：六州实验世界

日期：2026-10-14。分支 `feature/federal-worldgen-v1`，独立 worktree `D:\Minecraft Modding\AFL-federal-worldgen-v1`，基于 C1（`2bb762af`）。上一阶段：[C1](federal_six_state_c1.md)。

## 状态

**C2 完成；用户 2026-10-14 实机验收：雪峰、峡谷复验通过，其余此前已确认"基本上没问题"（实机反馈 1）。同日用户批准集成：合并进 master（`082ebd77`），新建普通世界默认是联邦六州（§12）。C3 没有开始。**

- 世界类型 **"AFL 联邦六州"**（`apocalypse_firstlight:federal_experimental`；C2 时叫"AFL 联邦（实验）"，只能手动选）。
- **默认世界（2026-10-14 起）**：新建普通世界默认联邦六州；r1（`minecraft:normal` → AFL r1 噪声设置，按钮上叫"AFL r1（旧地形）"）、超平坦、其他模组的类型都还能手动选；`overworld.json` 没有修改；已有的 r1 世界照旧按 r1 打开（§12）。
- r1 回归在隔离的开发客户端里实测过：C1 构建和 C2 构建生成的 r1 世界地形 256/256 区块一致，锁指纹相同（§7）。
- 所有游戏内测试都在 worktree 的 `build/federal-c2-client/` 运行目录里进行，用全新测试存档。没有访问 Main 的 `run/`，没有预生成整个国家。

| 项 | 状态 |
|---|---|
| 一 独立实验世界 | **已实现**；预设、噪声设置、密度函数、群系源接入；实测 PASS |
| 二 宏观地理接入 | **已实现**：C1 的海陆、六州、地貌权重、气候进入规划器；海岸、河网、地形全国连续 |
| 三 S1 / S2 | **可进入**：S1 复用 Terrain V2 的森林丘陵与河谷；S2 是开阔农业平原，森林覆盖 12%（S1 65%） |
| 四 S3–S6 | **基础地貌 V1**（地形是真的，生态是**过渡状态**，见 §5）。**不算这些州已完成** |
| 五 公路 | 只保留 C1 的走廊数据作参考；**没有铺设任何道路**；高坡度段不算可驾驶路线 |
| 六 版本锁 | 新增联邦 profile；新建 / 旧世界 / LEGACY / BLOCK / 重开全部实测 PASS；补了"拦截后不存盘" |
| 七 性能 | 已实测（§8） |

**实机反馈 1（用户，2026-10-14）**：
- 雪峰不通过：一格一格的雪块台阶、悬空的雪层；
- 新群系的地表方块（峡谷陶瓦等）没有进地表平滑；
- 其余基本没问题。

已修（代码完成、离线检查和编译通过；用户 2026-10-14 **复验通过**："雪峰和峡谷复验通过"）：雪块、红沙、7 种陶瓦加入地表平滑；坡上的雪层改画成贴坡的雪皮。见 [terrain_smooth_v1.md U 节](terrain_smooth_v1.md) 和本文 §4。

## 1. 实验世界怎么接入

| 部分 | 做法 | 文件 |
|---|---|---|
| 世界类型 | 数据包预设：主世界用联邦噪声设置 + `minecraft:overworld` 多噪声群系源；下界、末地原版。加入 `#minecraft:normal` 世界类型标签，出现在"世界类型"按钮里。2026-10-14 起是建世界界面的默认选项（§12，测试客户端从界面数据确认过） | `data/apocalypse_firstlight/worldgen/world_preset/federal_experimental.json`、`data/minecraft/tags/worldgen/world_preset/normal.json` |
| 噪声设置 | `minecraft:overworld` 的副本：密度函数改指 `federal/plan_*`，地表规则在第 1 位插入联邦分支，顶部 slide 240/256 → 296/312（给 S3 的山留空间） | `worldgen/noise_settings/federal_experimental.json`、`worldgen/density_function/federal/`（8 个） |
| profile | 密度函数 JSON 带 `"profile": "federal"`（缺省 r1）。`RandomStateSeedMixin` 按 profile 给每个 RandomState 绑定一份规划，混用两种 profile 直接报错 | `TerrainProfile`、`Plan*Density`、`RandomStateSeedMixin`、`TerrainPlanStore`（缓存键 = profile + 种子） |
| 群系源 | TerraBlender 的延迟群系列表只能追加一次，所以联邦的 8 个新群系不走 TerraBlender：`ServerAboutToStartEvent` 时，只对**联邦世界**的主世界群系源，把它们并进 `possibleBiomes()`（特征排序、装饰都靠它） | `mixin/BiomeSourceFederalMixin`、`worldgen/federal/FederalPreset`、`FederalBiomeSourceAccess` |
| 群系映射 | 群系参数表绑定时，联邦世界用联邦噪声设置（规划直接传入，不再按种子查缓存）；地表群系由 `FederalEcologyBiomes` 决定 | `ClimateParameterListMixin`、`MainNationBiomeRegionPlan`、`StartupEcologyState`、`SurfaceSystemStartupBiomeMixin` |
| 出生点 | 联邦世界在 C1 的开局区（S1，`FederalMap.start()`）里找平地；找不到就扩到 6 km。**没有改老宅出生流程** | `TerrainV2SpawnEvents` |

种子 `-295378578869149513` 的实测出生点：`404 106 -408`（S1，橡树–山核桃林地）。

## 2. 规划器（`FederalTerrainPlan`，`afl_federal_plan_c2_v1`）

继承 `TerrainPlanV2`，网格 1760 × 1760 × 16 m（28.2 km 见方，原点 −14080），国家本体 20 × 16 km（C1 正式尺度）。r1 规划器只加了两个**r1 行为不变**的钩子：

- `shoreSpread(k)`：离岸 900 m 以外的岸坡传播（联邦大平原上原来零代价扩散，会撑爆堆）；
- `floatRelax()`：山坡 Dijkstra 用 float 比较（double/float 混比导致同一格反复入堆，内存溢出）。

每次改动后都复核了 r1 指纹：**`8d712b1dd53f3bd3 / 90287ece05fe7a5c`，不变**。

### 2.1 地貌（按 C1 的地貌权重混合，州界不参与）

| 地貌 | 主要在 | 做法 |
|---|---|---|
| 丘陵 HILLS | S1 | r1 地形放进较小的框：倾伏褶皱带、山麓、河谷；坡脚加 560 m 尺度的起伏 |
| 平原 PLAINS | S2 | r1 冰碛平原：宽缓隆起、丘状底碛、终碛弧、缓丘 |
| 湖区低地 LAKE_LOWLAND | S4 | 低平底碛 + 鼓丘；**冰川槽谷**（4–6 条，长 2.4–4 km，半宽 160–300 m，深 22–36 m，弯曲 80–200 m）和**锅穴**（55–85 个，半径 60–240 m，深 3–10 m）；离海 900 m 以内不挖，避免接通大海；湖区从离海岸 250 m 起、到 1.75 km 处逐渐抬高 20 m（冰碛高地，湖盆不会低到海面） |
| 海岸低地 COASTAL_LOWLAND | S5 | 低海岸平原、宽河漫滩，入海处是潮沼 |
| 山地 MOUNTAIN | S3 | 山体：脊状山峰（约 Y230–270），起伏 = 22 + 30·核心 + (20 + 130·核心)·脊状噪声；陡沟、谷壁、岩崖 |
| 台地 PLATEAU | S6 | Y145–160 的台地，前缘陡坎，台地外有方山和孤峰，河流切出峡谷；台阶层 7.5–12.5 m，每层 12% 缓坡 + 最后 26% 陡崖 |

### 2.2 湖泊（S4 的核心，"不能把山区改名叫湖区"）

- 规划：水格类型 4 = 湖。封闭洼地按溢出口定水位：`湖面顶块 = floor(溢出高度) − 1`。
- 湖盆里高于水面的地面保持原形（不填平到溢出口，否则湖两端会出现平顶梯形）；湖周洼地从湖面 +0.5 m 做优先填充，所以湖外一圈地面至少比湖面高 0.5 m。
- 出口：沿排水方向从湖格往下，把非湖格降到湖面 +0.55 m（逐步 −0.002），直到地面本来就更低。
- 结果（本种子）：27 个湖，12159 个湖格（3.1 km²），S4 陆地的 6.4%。
- 剩余 13 处"河道上坡"（≤ 1.3 m，全在 S4 湖区；河流水位强制顺流不升，所以只是河床的小台阶）。列为已知问题，实机检查。

### 2.3 区块里的湖水（运行期，`RiverCarver` / `RiverWaterFill`）

只对联邦世界生效（`s.layers != null`）；r1 的列规则一行没变。

| 规则 | 内容 |
|---|---|
| LAKE | 列在湖格的双线性范围内（四个相邻格有任一湖格），且生成的地面低于湖面：从地面灌水到湖面，岸线跟着地面等高线走；**优先于河流规则**（穿湖的河按湖面走，河岸不会在湖里立起来）；床下两格封住洞穴 |
| RIM | 湖格范围外、紧挨湖格范围的列，地面低于湖面：抬到湖面高度，挡住湖水。在河流规则之后，出湖的河保留河道 |
| 河流水位 | 河流顶点一进入湖格范围就取湖面水位，并向上游传播（以前湖岸带地面低于湖面，会把河压到湖面以下 1 格以上） |
| 雕刻器 | 湖附近两格内，雕刻器只能在最深湖床 −10 以下工作 |
| 落差 | 湖水旁有更低的水（出湖河道）时，标记流动后处理（和河流的浅滩一样） |

离线审计（`FederalPlanPreview lakes`，全部湖周两格内逐列）：289 万个湖水列（2.89 km²），**干侧水面 0**（加 RIM 之前 54 处，全是 1 格），落到更低水面 0。审计用 `ceil(h) − 1` 近似生成地面，没有模拟原版 4×4 密度插值，所以以实测为准：实测湖岸站点水面贴空气 0（§7）。

### 2.4 规划层（`TerrainPlanSurface.Layers`，缓存格式 5）

| 层 | 内容 |
|---|---|
| `lakeTop` | 湖格的湖面顶块 |
| `terrace` | 台地台阶权重 0–255 |
| `rough` | 山地岩石细节权重（脊状岩崖、岩坎） |
| `open` | 开阔度 −1..1（字节 = 127.5·(1+o)）：−1 = 林木覆盖的丘陵（S1），0 = r1 的林地比例，1 = 开阔平原（S2）。由湿度和地貌决定：`dry = 1 − smooth((湿度−0.40)/0.30)`，平原开阔 = min(1, 平原·1.35)·dry·(1 − 0.6·丘陵)，林地 = max(smooth((湿度−0.45)/0.25), 丘陵)，o = 平原开阔 − 0.9·林地·(1 − 平原开阔) |

## 3. 生态

### 3.1 分区（`FederalEcology`，r1 的 9 区 + 8 个联邦区）

r1 区：agri_plain、riparian、slope_forest、ridge_oak、hollow_conifer、coastal_pine、swamp_forest、tidal_marsh、wet_prairie。
联邦区：montane_conifer、alpine_meadow、alpine_rock、lakeshore_forest、fen、lake、steppe、canyon。

- 林线 `210 + 噪声 − 14·北移 + 40·(温度 − 0.3)`；高山区只在山地权重 > 0.25 的地方出现。
- 地表群系 `FederalEcologyBiomes`：r1 的规则（海、海滩、30 m 扭曲的软过渡、林地斑块、河岸带），加开阔度调整林地阈值：`0.95 + (o > 0 ? 0.85 : 1.2)·o`。

### 3.2 新群系（8 个，`data/apocalypse_firstlight/worldgen/biome/`，由 `tools/federal-worldgen/build_federal_data.py` 生成）

| 群系 | 中文名 | 温度 / 降水 | 地表 | 树（过渡，见 §5） |
|---|---|---|---|---|
| montane_conifer_forest | 山地针叶林 | 0.4 / 0.8 | 灰化土 / 粗泥 | 网格过渡林分：白松–铁杉；原版云杉 + 松 + 桦（约 10/区块） |
| alpine_meadow | 高山草甸 | 0.45 / 0.7 | 草，陡处石头 | 2026-10-20 起网格过渡林分：零星白松–铁杉（约 0.15/区块）；之前数据里是原版云杉（约 0.2） |
| alpine_peaks | 高山岩峰 | −0.3 / 0.9（下雪） | 雪块 / 石头 / 砂砾 | 原版云杉零星（约 0.2/区块） |
| lakeshore_mixed_forest | 湖岸针阔混交林 | 0.55 / 0.8 | 灰化土 | 网格过渡林分：铁杉–白松–红枫；原版云杉 + 橡 + 桦 + 松（约 8） |
| lake_fen | 湖滨沼泽 | 0.55 / 0.9 | 泥 | 2026-10-20 起网格过渡林分：零星红枫–铁杉–白松（约 0.25/区块，泥上不长，实际更少）；之前是原版云杉 + 桦（约 0.3） |
| freshwater_lake | 淡水湖 | 0.55 / 0.8 | 湖岸沙 / 砂砾 | — （鲑鱼） |
| plateau_steppe | 高原干草原 | 0.95 / 0.1 | 粗泥、红沙；陡处层状陶瓦 | 无 |
| canyon_badlands | 峡谷劣地 | 1.2 / 0.0（无降水） | 红沙、层状陶瓦（`minecraft:bandlands`） | 无 |

- 名字：`lang` 群系段落里对齐加入，`generator.apocalypse_firstlight.federal_experimental` = "AFL Federation" / "AFL 联邦六州"（C2 时是"AFL Federation (Experimental)" / "AFL 联邦（实验）"，2026-10-14 设为默认时改名，§12）。
- 体感温度（`temperature/temperature_v1.json`）已加 8 条。
- 只有 alpine_peaks 会下雪（原版雪线公式：温度 −0.3 时整个高山岩峰都在雪线以上）。
- 生物：新增 alpine、peak、lake、steppe、canyon 五种刷怪表。

- 2026-10-20（原版木头退役，`docs/gameplay/vanilla_survival_rework_v1.md` 4.6 节）：`alpine_meadow`、`lake_fen` 加进 `MESH_TREES`，网格森林打开时（默认）所有联邦群系都不再长原版树；表里的"原版云杉 + 松 + 桦"只是 `eco_trees_*` 数据里还留着的补位，网格森林关掉时才长。

### 3.3 各州统计（种子 −295378578869149513，陆地每 32 m 采样）

| 州 | 陆地 | Y 中位 / p90 / 最高 | 坡 > 0.3 | 湖 | 森林 | 主要群系 |
|---|---|---|---|---|---|---|
| S1 森林丘陵 | 62 km² | 94 / 124 / 237 | 4.8% | 0 | **64.8%**（r1 54.3%） | 橡–山核桃林地 35%、温带草原 34%、山脊橡林 10%、混合中生林 9% |
| S2 农业平原 | 41 km² | 78 / 115 / 153 | 2.1% | 0 | **12.0%** | 温带草原 71%、湿草原 8%、高原干草原 7%（S6 边缘）、河岸林 5% |
| S3 寒冷山地 | 23 km² | 172 / 229 / 262 | 15.1% | 0.1% | 51.8% | 山地针叶林 29%、高山草甸 22%、高山岩峰 15%、混合中生林 14% |
| S4 湖区 | 47 km² | 84 / 98 / 120 | 0.4% | **6.4%** | 61.4% | 温带草原 30%、湖岸混交林 30%、橡–山核桃 18%、海岸松 11%、淡水湖 6% |
| S5 湿地海岸 | 54 km² | 72 / 80 / 91 | 0 | 0 | 77.0% | 海岸松–橡林 53%、海岸沼泽林 19%、草原 16%、潮沼 1.6% |
| S6 干旱高原 | 40 km² | 138 / 153 / 265 | 9.3% | 0 | 13.4% | 高原干草原 74%、山地针叶林 7%、峡谷劣地 5% |

S1 与 S2 的差别不只是边界数据：地形起伏（Y p90 124 vs 115，坡 4.8% vs 2.1%）、森林覆盖（65% vs 12%）、开阔度（−0.70 vs +0.89）、河岸带比例（7% vs 22%）都不同。

### 3.4 州界连续性

离线检测：每段州界上跨 96 m 的群系变化率，和州界两侧各 1 km 处的同样测量对比（比值 1 = 州界上和别处一样）。

| 州界 | 采样 | 界上 / 旁边 | 比值 | 平均 \|dY\| 界上 / 旁边 |
|---|---|---|---|---|
| S1–S2 | 152 | 23.7% / 29.3% | 0.81 | 1.2 / 2.0 |
| S1–S4 | 289 | 51.6% / 46.8% | 1.10 | 1.6 / 2.1 |
| S1–S5 | 263 | 35.4% / 31.0% | 1.14 | 1.1 / 2.0 |
| S2–S3 | 50 | 40.0% / 14.0% | 2.86 | 5.5 / 3.9 |
| S2–S4 | 62 | 21.0% / 35.5% | 0.59 | 2.0 / 1.3 |
| S2–S5 | 122 | 18.0% / 19.7% | 0.92 | 1.1 / 0.9 |
| S2–S6 | 298 | 24.8% / 18.2% | 1.36 | 6.5 / 2.4 |
| S3–S4 | 142 | 23.9% / 39.5% | 0.61 | 5.8 / 3.3 |
| S3–S6 | 70 | 30.0% / 28.6% | 1.05 | 8.7 / 8.1 |
| 全部 | | 31.9% / 30.3% | 1.05 | |

- 全国平均没有"州界断层"（1.05）。
- 偏高的两段是地貌本身的边缘，不是行政线：**S2–S6** 是台地东缘陡坎（呈裂片状，南段越过州界伸进 S2，见 S6 切片图）；**S2–S3** 是山前，只有 50 个采样（州界很短），统计噪声大。实机要看这两处。

## 4. 地表与过渡资产（用了哪些原版方块）

联邦地表规则分支（插在联邦噪声设置地表规则的第 1 位）：

- 湿 / 干底：按原版规则；
- 高山岩峰：雪块 / 石头 / 砂砾；高山草甸：陡处石头；
- 山地针叶林：灰化土 / 粗泥；湖岸林：灰化土；湖缘：沙 / 砂砾；湖滨沼泽：泥；
- 高原干草原：陡处层状陶瓦（所有深度）、粗泥、红沙；峡谷劣地：红沙 + 层状陶瓦（`minecraft:bandlands`，崖壁全部深度）。

这些都是**原版方块作过渡**，不是 AFL 风格的最终材质（AFL 人造方块 240 texel，自然方块目前保持原版风格，见 §6）。

**地表平滑**（2026-10-14，实机反馈 1）：
- 普查用户测试世界的 5185 个区块，平滑名单以外的天然表层只有雪块（高山岩峰 79.7%）、红沙（峡谷 61%、干草原 4.4%）和 7 种陶瓦。三者已加入平滑，名单从 9 种变成 18 种。
- 坡上的雪层贴坡画（`SnowSkinModel`）。
- 砂岩、方解石、冰、露头矿石保持整格。

详见 [terrain_smooth_v1.md U 节](terrain_smooth_v1.md)。以后给联邦群系加新的天然地表方块时，要同时加进平滑名单（`TerrainSmooth.GROUND` / `ground()`，以及雪层存活标签）。

## 5. 未完成的州级生态（如实标注）

| 州 | 现在是什么 | 还差什么才能算完成 |
|---|---|---|
| S1 | **可用**：r1 的森林、河谷、6 种网格树 | 可选：美国梧桐（河岸）、黑胡桃 |
| S2 | **地形与开阔度可用**；草原 / 林地斑块 / 河岸林 | 田块格局（耕地、牧场、休耕）、防风林带、橡树稀树草原；三角叶杨、大果栎 |
| S3 | 地形真实（山体、雪峰、林线）；**生态过渡**：网格林用白松–铁杉代替云杉–冷杉，原版云杉补位；高山岩峰用原版雪 / 石 / 砂砾 | 红云杉 / 恩氏云杉、香脂冷杉、颤杨；裸岩（花岗岩 / 片麻岩）、碎石坡、高山草皮；积雪与地形平滑的配合 |
| S4 | **湖泊是真的**（封闭湖盆、湖面、出口、湖岸低地、锅穴）；**生态过渡**：湖岸林用铁杉–白松–红枫代替北方阔叶林，原版桦、云杉补位 | 糖枫、黄桦、美洲山毛榉、红松；落叶松 / 黑云杉（沼泽）；湖滩砂砾、漂砾、泥炭沼 |
| S5 | 宽谷、低湿地、沼泽林、潮沼（r1 的海岸生态） | 河口和低湿地偏少（潮沼 1.6%、沼泽林 18.5%）；落羽杉、水紫树、弗吉尼亚栎、火炬松；泥炭 / 腐殖泥、贝壳滩 |
| S6 | 地形真实（台地、陡坎、方山、峡谷）；**生态过渡**：原版红沙、陶瓦层；干草原和峡谷没有树 | 矮松、犹他刺柏、弗氏三角叶杨；蒿、兔丛等灌木；AFL 的红 / 黄砂岩层理、页岩、钙质壳、荒漠砾面；峡谷偏窄 |

**S3、S4、S5、S6 的状态是"基础地貌 V1 + 过渡生态"**，不是完成。

## 6. 需要的模型与材质

按优先级（S1/S2 优先，其次湖区与山地）：

| 类别 | 内容 | 用途 |
|---|---|---|
| 网格树种 | 三角叶杨、大果栎 | S2 防风林、河岸 |
| 网格树种 | 糖枫、黄桦、美洲山毛榉、红松；落叶松、黑云杉 | S4 北方阔叶林、湖滨沼泽 |
| 网格树种 | 红云杉（或恩氏云杉）、香脂冷杉、颤杨 | S3 山地针叶林、林线 |
| 网格树种 | 落羽杉、水紫树、弗吉尼亚栎、火炬松 | S5 沼泽、海岸 |
| 网格树种 / 灌木 | 矮松、犹他刺柏、弗氏三角叶杨；蒿、兔丛 | S6 |
| 地表方块 | 耕地 / 草场地表、草原草 | S2 |
| 地表方块 | 湖滩砂砾、漂砾、泥炭 | S4 |
| 地表方块 | 裸岩（花岗岩 / 片麻岩）、碎石坡（talus）、高山草皮 | S3 |
| 地表方块 | 红 / 黄砂岩层理、页岩、钙质壳、荒漠砾面 | S6（替换原版陶瓦） |
| 地表方块 | 泥炭 / 腐殖泥、贝壳滩 | S5 |

新地表方块都要：按 AGENTS.md 审核挖掘工具与等级（自然地形按原版规则），加进地形平滑的自然地表名单，按 32 px 自然风格或 240 texel 规则决定。

## 7. 实测（隔离开发客户端，2026-10-14）

运行方式：`gradlew runClient --offline -PaflWithoutShaders -I src/dev/federal-c2-client.init.gradle -Pc2="mode=create|open;world=名字;preset=预设;seed=种子;tag=标记;minutes=超时;verify=基线报告"`。2026-10-14 加了 `mode=ui`（走原版建世界界面，可加 `type=` 切类型，§12.3）。2026-10-16（C3.3b）起，幸存者 V0 在新世界弹的两个窗口（会暂停游戏）由测试程序直接回答，不显示：起名用账号名，房主的 NORMAL / HARD 选 NORMAL（`C2HarnessClient#onNamePrompt`）。

- 运行目录 `build/federal-c2-client/`，存档全部是新建的测试存档；
- 客户端自己建 / 开世界，服务器端探针（`dev/worldgen/C2Probe`）在玩家进入后逐个生成测点（阻塞 FULL 区块），写报告到 `afl_c2_probe/`，存盘退出；看门狗超时强退；
- 没有截图、没有接管用户的客户端。

**为什么不用逐方块哈希判定**：同一构建、同一种子连建两次，256 个区块里约 95 个方块哈希不同，差异全是矿石 / 石材 / 泥土团块互相覆盖（相邻区块的装饰顺序随线程调度变化）、泉水流动和花。这是原版的不确定性。所以判定用**地形签名**：每列的地面高度（阻挡移动、非原木 / 树叶的第一块）、贴地静水高度、地面群系。方块哈希照样报告，只作参考。

| 测试 | 世界 | 结果 |
|---|---|---|
| r1 回归（新建） | C2 构建新建 r1（同种子）对 C1 构建建的 `r1_base` | **PASS**：地形签名 256/256 一致；锁指纹都是 `49c55f90d2a4677a`；各站点统计（地面偏差、台阶）一致 |
| 旧世界打开 | C1 建的 `r1_base` 用 C2 构建打开 | **MATCH** |
| LEGACY | `r1_base` 的副本删掉锁记录后打开 | **ADOPT_LEGACY**（日志警告、写入新记录），地形 256/256 一致 |
| BLOCK | 联邦世界副本换成 r1 世界的锁记录后打开 | **拦截**：6 项 HARD（profile、四个子系统、生态），给出令牌，世界未打开。156 个文件中只有 `level.dat` 变化（仅 `LastPlayed`，原版单人客户端在打开任何世界时、服务器启动前写入），原文件完整保留为 `level.dat_old`；区块、锁记录、其他数据逐字节不变 |
| 联邦新建 | `fed_c2`，19 个测点（出生点 9×9 区块，其余 5×5，湖岸 7×7） | 生成成功；全部测点水面贴空气 0、水悬空 0；跨区块台阶最大 2（峡谷、潮沼）、区块内最大 3（峡谷），无 ≥ 4 的台阶；地面与规划平均偏差 0.39–0.75 格；S3 有积雪（3900 块） |
| 联邦重开 | `fed_c2` 重开 | 规划从磁盘缓存加载 0.9–1.0 s；锁 MATCH；地形签名 500/500 一致（23 个方块哈希差异全在出生点常加载区，随机刻造成） |
| 最终代码回归 | 改完锁之后再开 `r1_c2` 和 `fed_c2` | 都 MATCH、地形一致、正常存盘 |

拦截修复：原来拦截后服务器停服时还会存一次盘（重写 `level.dat`、轮换 `level.dat_old`）；现在被拒的服务器跳过 `saveAllChunks`（`mixin/WorldgenLockSaveMixin`）。

测点位置（种子 −295378578869149513）：出生点 404 −408；S1 中心 4854 −1200；S2 中心 −2889 1935；S3 中心 / 雪峰 −7113 −3952；S3 林线 −7049 −3952；S4 中心 950 −5488；S4 湖岸 886 −5872；S5 中心 / 沼泽 3958 4431；S5 潮沼 6518 5711；S6 中心 −7049 2511；S6 峡谷 −7497 3151；S6 草原 −7817 1743；S2 开阔平原 −3657 1167；州界 S1–S2 −841 −816、S2–S6 −4809 3151、S3–S4 −4809 −5488、S1–S5 4342 1935。

报告和记录：`design/worldgen/federal_c2/probe/`。

## 8. 性能

| 项 | r1 | 联邦 |
|---|---|---|
| 首次规划（游戏内） | 已缓存 | **12.5 s**（离线：规划器 8.8 s + 地表 / 生态 2.0 s） |
| 规划缓存（磁盘） | — | 12.2 MB（压缩）；重开加载 0.9–1.0 s |
| 规划常驻内存 | — | 142 MB |
| 离线构建峰值堆 | — | 1126 MB（float 比较修复前会内存溢出） |
| 出生区准备 | 17.0 s | 18.8 s |
| 区块生成（阻塞 FULL，含所需邻区） | 75–116 ms/区块 | 59–126 ms/区块（山地最慢） |
| 开发客户端 GC 后堆 | 1.75 GB | 1.85 GB |
| 存档（出生区 + 19 测点） | — | 60 MB |
| 运行期查询（单线程） | | heightAt 0.54 µs、biomeAt 1.09 µs、lakeTopAt 0.14 µs |

**风险**：
- 首次进入联邦世界要多等约 12 s（之后有缓存）；
- 规划常驻 142 MB，加上 r1 世界同时打开时两份规划都在内存里（只在同一进程先后开两种世界时）；
- 只测了开发客户端（集成服务器）；专用服务器、正式 jar、长时间跑图都没测；
- 没有测光影（`-PaflWithoutShaders`）下的渲染表现。

## 9. 已知问题

- S4 湖区 13 处河床小台阶（≤ 1.3 m）；
- S6 峡谷偏窄，台地上有少量直线状断坎（河漫滩分段线，r1 算法里也有）；
- S3 岩崖细节有"梳齿"纹理；
- 只有高山岩峰下雪；
- S5 的河口和低湿地比要求的少；
- 生态大量用原版方块补位（§4、§5）；原版树补位 2026-10-20 起全部换成网格过渡林分（§3.2）；
- 实机没看过光影下的雪、水、陶瓦层。

## 10. C3 前置条件

1. 用户实机验收 C2（下节），确认地形与过渡生态可以作为公路选线的底图。
2. 在 C2 地形上重新评估 C1 的国家公路走廊和城市候选点：C1 的走廊是在 r1 地貌上画的，S3/S6 段的坡度（18%–30.9%）不能驾驶，需要重新选线（绕行、隧道、桥）。
3. R4 正式接入所需的东西（C1 §8.4）：道路实体、路基与地形的接合、版本锁里的公路子系统。
4. 决定 S3–S6 资产的先后（§6），资产到位前公路规划不依赖这些州的最终生态。
5. ~~决定联邦世界是否、何时取代默认世界~~：2026-10-14 用户决定取代，新建普通世界默认联邦（§12）。

## 11. 实机验收要求（用户）

在 worktree 里启动它自己的开发客户端（运行目录是 worktree 里的 `run/`，和 Main 的 `run/` 分开；可以加 `-PaflWithoutShaders` 关闭光影）：

```bash
cd "D:/Minecraft Modding/AFL-federal-worldgen-v1" && ./gradlew.bat runClient --offline
```

1. 新建世界 → "世界类型"按钮切到 **AFL 联邦（实验）**，种子 `-295378578869149513`；确认默认选项仍是"默认"。（C2 当时的步骤；2026-10-14 起默认就是"AFL 联邦六州"，见 §12。）
2. 进入后在 S1 出生点（404 −408）附近：森林丘陵、河谷。
3. `/tp` 到 §7 的测点，逐个看：
   - S2 开阔平原（−3657 1167）：大片草原、零星树林，和 S1 明显不同；
   - S4 湖岸（886 −5872）：湖面平整、湖岸不漏水、没有悬空水、出湖河道；
   - S3 雪峰（−7113 −3952）和林线（−7049 −3952）：积雪、林线、岩崖；雪坡没有一格一格的台阶，雪层贴坡、不悬空（实机反馈 1 的修正）；
   - S6 峡谷（−7497 3151）和台地陡坎（−4809 3151 附近）：陶瓦层、陡坎；红沙和陶瓦的一格台阶是缓坡，两格以上的崖壁仍是硬边；
   - S5 沼泽（3958 4431）和潮沼（6518 5711）；
   - 州界 S2–S6、S2–S3：有没有不自然的生态断层。
4. 退出重进一次：日志应为 `world matches its recorded rules`。
5. 用测试世界，不要用正式存档；不要一次跑遍整个国家。

## 12. 集成 Main 与默认世界（用户正式决策，2026-10-14）

用户实机验收 C2 后批准："C2 联邦六州地形已经通过我的实机验收。现在批准将 C1+C2 集成到 AFL Main，并正式调整默认世界生成行为。"

### 12.1 Git

- 合并前 master 工作区干净（收音机 V1 已由用户提交为 `2bae125d`），安全检查点：分支 `checkpoint/pre-federal-merge-20261014` → `2bae125d`；
- `git merge --no-ff feature/federal-worldgen-v1`，合并提交 `082ebd77`。两边只有两个语言文件都改了，自动合并没有冲突；合并后两边的键都在（956 个键），en/zh 逐行对齐；合并后 `compileJava` 通过；
- 默认世界的改动在合并之后另做（下节）。

### 12.2 默认世界（现在的行为）

| 情况 | 行为 | 实现 |
|---|---|---|
| 单人"创建新世界" | 界面一打开就选中 **AFL 联邦六州**（`apocalypse_firstlight:federal_experimental`），主世界维度也一起换成联邦的，直接按"创建"就是联邦世界 | `mixin/client/CreateWorldFederalDefaultMixin`：`WorldCreationUiState` 构造末尾，只当传进来的默认类型正好是 `minecraft:normal`（原版 `CreateWorldScreen.openFresh` 就是这样传的）时，用 `setWorldType` 换成联邦（会同时换维度） |
| 专用服务器 | `server.properties` 没写 `level-type` 时新世界用联邦，并把它写进生成的 `server.properties`；写了的照写的用（`minecraft:normal` 还是 r1，`minecraft:flat` 还是超平坦） | `mixin/DedicatedServerFederalDefaultMixin`：只换 `level-type` 的缺省值 |
| 开发用 GameTest 服务器 | **仍用 r1**：Forge 的 `GameTestMain` 用专用服务器那套代码、在新的 `server.properties` 上建测试世界，现有 GameTest 都是在 r1 地形上写、在 r1 地形上实测的；只在开发环境出现，正式版里永远不成立 | `FederalPreset.defaultLevelType`：`ForgeGameTestHooks.isGametestServer()` 时不换 |
| 超平坦 | 原版行为：`FlatLevelSource`，没有联邦地形、河流、生态，不上锁 | 没改；联邦群系只并进联邦主世界的群系源（`FederalPreset.onServerAboutToStart` 先看噪声设置） |
| r1 | 还能手动选：按钮上原版的"默认"改名为 **"AFL r1（旧地形）"**（`generator.minecraft.normal`，在 AFL 里它本来就是 r1） | 语言文件 |
| 其他模组的世界类型 | 不替换：它们照旧在 `#minecraft:normal` 里；别的模组如果自己把默认换成了别的类型，传进来的就不是 `minecraft:normal`，这里不动 | `FederalPreset.replacesDefault` |
| 重建世界（编辑世界 → 重新创建） | 不动：原版传的是那个世界自己的类型 | 同上 |
| 已有世界 | 不变：生成器存在各自的 level.dat 里；版本锁照旧比对，不迁移、不覆盖、不删除 | 版本锁（§7、[版本锁文档](worldgen_version_lock_v1.md)） |

- 名字：`generator.apocalypse_firstlight.federal_experimental` 从"AFL 联邦（实验）/ AFL Federation (Experimental)"改成 **"AFL 联邦六州" / "AFL Federation"**。
- id 不改：预设和噪声设置的 id、锁的 profile `afl_federal_experimental` 都留着"experimental"。已有的 C2 测试世界的 level.dat 和锁记录写的就是这些 id，改了就打不开。
- 按钮顺序：AFL r1（旧地形）、超平坦、巨型生物群系、放大化、单一生物群系、AFL 联邦六州（`replace: false` 追加在标签最后；没有用 `replace: true` 往前排，因为那样会把别的模组加进这个标签的类型挤掉）。默认选中的是联邦，点按钮从它往后转。
- 创建时没有弹出"实验性设置"确认框（测试客户端记录：按"创建"后直接进入）。

### 12.3 实测（隔离开发客户端，2026-10-14）

测试客户端加了 `mode=ui`：走原版"创建新世界"界面（`CreateWorldScreen.openFresh`），记下界面默认选中的类型和主世界，可选像按钮那样切类型（`type=minecraft:flat`），再按"创建"。探针每个世界多记两行：主世界生成器和群系源（含几个联邦群系），以及用 `server.properties` 的同一段解析代码（`DedicatedServerProperties`）算出的三种 `level-type` 结果。运行目录是 Main 的 `build/federal-c2-client/`，存档全是新建的测试存档，或从联邦 worktree 的测试目录**复制**过来的 r1 测试存档；没有碰 `run/` 和用户存档，没有截图、没有接管用户的客户端。

| # | 测试 | 结果 |
|---|---|---|
| 1 | **普通世界默认联邦**：`mode=ui`，界面不动，直接按"创建"（`fd_default`，种子 −295378578869149513） | **PASS**。界面默认类型 `AFL Federation [apocalypse_firstlight:federal_experimental]`，主世界 `NoiseBasedChunkGenerator apocalypse_firstlight:federal_experimental`；没有确认框；锁 CREATE，profile `afl_federal_experimental`，指纹 `b7ba3090bb2309d7`（和 C2 的联邦新建相同）；出生点 404 106 −408（S1）；主世界群系源 25 个可能群系，8/8 个联邦群系都在 |
| 2 | **六州地形和群系**：同一个世界对用户验收过的 C2 世界 `fed_c2`（`fed_c2_final`，19 个测点） | **PASS**：地形签名 500/500 一致（方块哈希 192 个不同，是 §7 说的原版装饰顺序噪声，不作判定）；测点水贴空气 0、水悬空 0，跨区块台阶最大 2 |
| 3 | **存盘与重开**：`fd_default` 存盘退出，再用 `mode=open` 打开 | **PASS**：锁 MATCH（`b7ba3090bb2309d7`）；地形签名 500/500 和第 1 次一致（23 个方块哈希差异在出生点常加载区，随机刻，和 C2 一样）；正常存盘退出 |
| 4 | **超平坦保持原版**：`mode=ui`，按钮切到超平坦再创建（`fd_flat`） | **PASS**：主世界 `FlatLevelSource`，`FixedBiomeSource` 只有平原，联邦群系 0/8；不上锁（`not locked`，没有锁文件）；出生点 0 −60 0（原版）；81 个区块方块全一样，没有台阶、没有水 |
| 5 | **旧 r1 存档受保护**：C1 构建建的 r1 测试世界 `r1_base` 的副本（`r1_old`） | **PASS**：锁 MATCH，profile r1，指纹 `49c55f90d2a4677a`（和 C1/C2 时一样）；主世界仍是 `minecraft:overworld`（r1 规划 `afl_terrain_plan_v2_r1`），联邦群系 0/8；地形签名对 C2 时打开它的报告 256/256 一致；没有被改成联邦、没有迁移 |
| 6 | **锁出现之前的 r1 存档**：同一份 r1 测试世界的副本删掉锁文件（`r1_pre_lock`） | **PASS**：ADOPT_LEGACY（日志警告），按 r1 的规则领养，指纹 `49c55f90d2a4677a`；主世界仍是 r1，联邦群系 0/8；地形签名 256/256 一致 |
| 7 | **专用服务器默认**（在测试客户端里用 `DedicatedServerProperties` 的同一段解析代码） | **PASS**：没写 `level-type` → 联邦；`level-type=minecraft:normal` → r1（`minecraft:overworld`）；`level-type=minecraft:flat` → 超平坦 |

- 5 个测试客户端都正常存盘退出（exit 0），日志里没有 AFL 的错误（只有原有的 `dev_search_crate` 模型警告）。
- 报告：`design/worldgen/federal_c2/probe/default_20261014/`。
- 测试世界：联邦和超平坦是这次新建的；两个 r1 世界是从联邦 worktree 的测试目录复制的副本，原件没动。
- 回归 GameTest：见 §12.6。

### 12.4 没做 / 没验证

- **没有启动玩家用的专用服务器**：要接受 EULA，没有替用户接受。证据只有两条：
  - 客户端里用同一段解析代码验了结果（上表第 7 项）；
  - 开发用 GameTest 服务器走的就是专用服务器的启动流程。第一次回归时，它在新的 `server.properties` 里写下了联邦，也建了联邦世界（§12.6），说明这个默认值在真实的启动流程里生效。
- 正式版 jar 用 SRG 名，没有在里面跑过；只检查了 refmap，`DedicatedServerProperties.get` 映射到 `m_139817_`。
- 界面上的按钮文字、按钮顺序是从界面数据读出来的，没有截图看。
- 世界类型里的"AFL r1（旧地形）"只是改名，r1 本身没变。
- **C3（全国高速）没有开始。**

### 12.5 给 M4（溪谷老宅自动生成）的约束

见[老宅计划](forest_start_home_b_v1_plan.md)第 6 节"M4 选址与道路接口"。要点：
- 选址在 S1 森林州的开局区 `FederalMap.start()`；
- 每个世界按种子挑一处合格址，不固定在原点；
- 写一条"道路接入口"记录，留给 C3 接；
- M4 不依赖还没做的道路生成器。

### 12.6 回归 GameTest（合并 + 默认改动之后）

- 第一次跑（还没加 GameTest 例外）：`ForestHomeM2GameTests`（收音机、椅子、柜子、16 个方块的生存挖掘）PASS，木屋屋顶 PASS；暗线 13 项里 12 项 PASS，`template` 失败："loaded: the stone cell written without the link, the air cell dropped, the keys gone"；
- 原因：GameTest 服务器的测试世界跟着专用服务器的默认变成了联邦。它新生成的 `server.properties` 里写的是 `level-type=apocalypse_firstlight:federal_experimental`，锁记了联邦 profile，出生点 308 85 −392；合并前同一测试的世界是 r1，出生点 904 71 904。`template` 在 4000 格外加载一个区块，假设和配电箱同高的那格是空气，在联邦测试世界里不是。暗线本身没有改动。这次也顺带在真正的专用服务器流程里证明了默认值会写进 `server.properties`；
- 处理：GameTest 服务器保持 r1（上表）。加了例外之后**没有重跑**：用户 2026-10-14 叫停（"先不用gametest了，我等会自己去测"），由用户自己测。

## 文件

| 文件 | 作用 |
|---|---|
| `worldgen/terrain/v2/FederalTerrainPlan.java` | 联邦规划器（地貌、湖泊、层、生态入口） |
| `worldgen/terrain/v2/FederalEcology.java` / `FederalEcologyBiomes.java` | 联邦生态分区 / 地表群系 |
| `worldgen/terrain/v2/TerrainProfile.java` | r1 / 联邦 profile |
| `worldgen/terrain/v2/TerrainPlanV2.java` | 两个钩子（`shoreSpread`、`floatRelax`），r1 行为不变 |
| `worldgen/terrain/v2/TerrainPlanSurface.java` | 联邦层、`lakeTopAt`、`lakeNear`、`lakeFloorNear` |
| `worldgen/terrain/v2/RiverCarver.java` / `RiverWaterFill.java` / `RiverNetwork.java` | 湖水、湖岸、穿湖河流水位 |
| `worldgen/federal/FederalPreset.java`、`mixin/BiomeSourceFederalMixin.java` | 预设识别、联邦群系并入群系源；默认世界的判断（`replacesDefault`、`defaultLevelType`） |
| `mixin/client/CreateWorldFederalDefaultMixin.java`、`mixin/DedicatedServerFederalDefaultMixin.java` | 新建普通世界默认联邦（建世界界面 / 专用服务器，§12） |
| `mixin/WorldgenLockSaveMixin.java` | 被锁拦截的服务器不存盘 |
| `worldgen/lock/WorldgenRules.java` | 联邦 profile 的五个子系统 |
| `registry/AflBiomes.java` | 8 个联邦群系键 |
| `tree/ForestPlan.java` | 两个过渡网格林分 |
| `tools/federal-worldgen/build_federal_data.py` | 生成群系、树、密度函数、噪声设置、预设、标签、语言、体感温度（先跑 r1 的生成器，再跑它） |
| `tools/federal-worldgen/java/.../FederalPlanPreview.java` | 离线：`parity`、`r1eco`、`plan`、`lakes`；`bash tools/federal-worldgen/run.sh c2` |
| `src/dev/federal-c2-client.init.gradle`、`dev/worldgen/C2*.java` | 隔离测试客户端与探针（只在开发构建） |
| `design/worldgen/federal_c2/` | 全国 / 各州地图、统计、湖泊审计、实测报告 |

## 与 Main 的可能冲突

（2026-10-14 已合并，自动合并无冲突，§12.1。下面是合并前的提醒，留作记录。）

C2 改了几处 Main 也会改的共享文件，合并时要注意：

- `lang/en_us.json`、`lang/zh_cn.json`（群系段落里加了 8 行 + 世界类型名；Main 有未提交的语言改动）；
- `apocalypse_firstlight.mixins.json`（加了 `BiomeSourceFederalMixin`、`WorldgenLockSaveMixin`）；
- `temperature/temperature_v1.json`、`tags/worldgen/biome/is_overworld.json`、`forge/biome_modifier/mesh_forest.json`；
- Terrain V2 的 `EcologyBiomes`、`ForestPlan`、`RiverCarver`、`RiverWaterFill`、`RiverNetwork`、`TerrainPlanSurface`（r1 行为不变，但是代码有改动）。
