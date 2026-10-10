# Terrain V2：美国真实地貌研究与 AFL 原创地形规划 r1

日期：2026-10-10。状态：**研究与设计完成，规划参数冻结为 `afl_terrain_plan_v2_r1`，等用户看图验收**。只有离线规划和预览图，**没有接入 Minecraft 实际地形生成**，没有改正式 Terrain 密度链、群系、Highway 或任何存档；没有运行 Gradle、客户端或服务器。

用户 2026-10-10 批准本轮："保留 Codex Phase 1 值得复用的规划器、诊断工具和基本架构，但重新设计全国宏观地貌的实际形态"；目标是"让虚构国家哥伦比亚联邦拥有真实可信的北美温带地貌、河流水系和自然生态"，不是复刻美国地图。参考区：美国中西部平原（Ohio、Indiana、Illinois）、阿巴拉契亚山前与山脊谷地（Pennsylvania）、中大西洋海岸（Chesapeake）。不做五大湖、寒带、冰原、永久雪原。用户中途要求（同日）：研究深度已够，修完 V13 的河谷距离场、海岸湿地和局部锯齿后冻结参数提交，不要为每个指标对齐美国样方无限迭代。

验收图（`docs/worldgen/terrain_v2_research/`）：

| 图 | 文件 |
|---|---|
| 全国地貌总览 | [national_overview.jpg](terrain_v2_research/national_overview.jpg) |
| F 全国地貌分区 | [national_landforms.jpg](terrain_v2_research/national_landforms.jpg) |
| G 全国目标高程 | [national_elevation.jpg](terrain_v2_research/national_elevation.jpg) |
| H 全国河流水系 | [national_rivers.jpg](terrain_v2_research/national_rivers.jpg) |
| I 建设适宜性、城市候选、走廊 | [national_suitability.jpg](terrain_v2_research/national_suitability.jpg) |
| 生态分区 | [national_ecology.jpg](terrain_v2_research/national_ecology.jpg) |
| J 三尺度对比（6 组） | [compare_plain_oh](terrain_v2_research/compare_plain_oh.jpg) · [compare_plain_il](terrain_v2_research/compare_plain_il.jpg) · [compare_plain_in](terrain_v2_research/compare_plain_in.jpg) · [compare_belt](terrain_v2_research/compare_belt.jpg) · [compare_front](terrain_v2_research/compare_front.jpg) · [compare_coast](terrain_v2_research/compare_coast.jpg) |
| C 真实统计表 | [real_statistics.jpg](terrain_v2_research/real_statistics.jpg) |
| D Codex Phase 1 对照 | [phase1_vs_v2.jpg](terrain_v2_research/phase1_vs_v2.jpg) |
| 其他种子 | [other_seeds.jpg](terrain_v2_research/other_seeds.jpg) |

统计表原文（Markdown）和每块样方的完整 JSON 在 [terrain_v2_research/stats/](terrain_v2_research/stats/)。

## A. USGS 真实参考区域

6 块，每块 10.02 × 10.02 km（EPSG:5070，对齐 NLCD 30 m 网格）。先用 3DEP 服务自带的灰度晕渲预览确认地貌，再下载。

| 编号 | 地区 | 中心（NAD83） | 为什么选它 |
|---|---|---|---|
| mw1_darby_plains_oh | 俄亥俄中部 Darby Plains | 39.97 N, 83.30 W | 威斯康星期底碛冰碛平原，被 Big / Little Darby Creek 切开 |
| mw2_wabash_valley_in | 印第安纳 Wabash 河谷（Lafayette 下游） | 40.32 N, 87.12 W | 切入冰碛平原的大河谷：台地、陡岸、冲沟 |
| mw3_bloomington_moraine_il | 伊利诺伊 Bloomington 脊状平原（Funks Grove） | 40.36 N, 89.10 W | 平坦冰碛平原上的终碛垄 |
| ap1_susquehanna_gaps_pa | 宾州 Susquehanna 穿山缺口 | 40.40 N, 76.95 W | 平行山脊（Blue / Second / Peters Mountain）和长谷，大河切穿山脊 |
| ap2_blue_mountain_front_pa | 宾州 Blue Mountain 山前 / Cumberland 谷 | 40.23 N, 77.40 W | 山前到大谷低地的过渡，页岩丘陵的密集切割 |
| cp1_york_pamunkey_va | 弗吉尼亚 York 河口（Pamunkey、Mattaponi 汇合） | 37.53 N, 76.80 W | 潮汐河、湿地、河漫滩林、被切割的海岸台地 |

每块另有一个 1 km（1 m）和一个 2 km（2 m）的激光雷达细节窗口，放在河谷边、山脊坡、湿地与台地交界等有代表性的位置（`tools/terrain-v2-research/tiles.json` 的 `detail`）。精确范围、获取时间和所用激光雷达项目见 [real_statistics.jpg](terrain_v2_research/real_statistics.jpg) 下表。

## B. 数据来源和统计方法

**接口（2026-10-10 核实并下载，UTC 16:35 前后）**

| 数据 | 接口 | 实际分辨率 / 坐标系 | 使用条件 |
|---|---|---|---|
| 10 m 高程 | USGS 3DEP 动态影像服务 `elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer`（服务说明：多分辨率 3DEP 裸地 DEM，数据截至 2026-09-28；单次导出上限 8000²，F32） | 用镶嵌规则只取 1/3 弧秒无缝 DEM（目录字段 LowPS 10–11，源像元约 10.3 m；新栅格的 Resolution_Y 是负数，所以按 LowPS 筛），双线性重采样到 EPSG:5070 10 m 网格；高程 NAVD88，米 | USGS 自产数据属美国公有领域，要求注明来源"U.S. Geological Survey"（USGS Copyrights and Credits 页面，2026-10-10 读取） |
| 1 m / 2 m 细节 | 同一服务，镶嵌规则只取 LowPS < 5（3DEP 激光雷达 DEM，最细优先） | 各州激光雷达 1 m DEM：OH Statewide Phase3 2021、IN Statewide 2017、IL 10CountyNRCS / MidNorth、PA South Central 2017、VA UpperMiddleNeck 2018 | 同上 |
| 土地覆盖 | MRLC GeoServer WCS 2.0.1，`mrlc_download__NLCD_2021_Land_Cover_L48` | 30 m，EPSG:5070（原生网格，原点 −2493045, 3310005），不重采样 | WCS 能力文档写明 Fees NONE、AccessConstraints NONE；NLCD 是 USGS 牵头的 MRLC 产品 |

- 坐标：自写的 EPSG:5070（NAD83 Albers）正反算（Snyder 公式），与服务器投影结果差 0.4–0.9 m，这是 WGS84 / NAD83 的基准差。Albers 在这几块地方的纬向比例 0.990–0.991，长度误差 < 1%，对坡度统计可以忽略。
- 原始数据只放在 `build/terrain-v2-research/raw/`（git 忽略），每块 4.2 MB（10 m）+ 4.2 MB（1 m）+ 4.2 MB（2 m）+ 115 KB（NLCD）。仓库里只有统计结果和预览图。
- 没有把任何真实地形拼进游戏，也没有把真实海拔用作 AFL 高度：只比较相对起伏。

**统计方法**（`tools/terrain-v2-research/metrics.py`；真实样方和 AFL 规划用同一份代码）：

| 指标 | 做法 |
|---|---|
| 高程分布 | 绝对高程只记录；比较的是第 1 百分位以上的相对高度、面积高程积分 |
| 坡度 | Horn 3×3 差分；10 m、30 m、90 m（块平均）三个尺度，P50 / P95 与 1/32、1/16、1/8、1/4、1/2 分级占比 |
| 不同尺度的局部起伏 | 3、10、30、100、300 格方块内的最大减最小（10 m 网格即 30 m … 3 km），以及去最佳拟合平面后的 RMS |
| 尺度分离 | 去平面、Hann 窗后的二维功率谱，按 Parseval 分成倍频程带（20–40 m … 2.5–5 km）的高度 RMS：几十米 / 几百米 / 几公里分开看 |
| 水系 | 优先洪泛填洼（Barnes 2014，ε 坡度）、D8 汇流；汇水 ≥ 0.05 / 0.5 / 5 km² 三级河网的水系密度、Strahler 分级、分岔比、汇流点密度、河源密度、坡面流长 |
| 河谷间距与深度 | 东西、南北向截线上相邻河道的间距；HAND（沿流线到最近河道的高差，Rennó 2008），分水岭处（流长前 20%）的 HAND 即谷深 |
| 平原台地面积与连续性 | 30 m 坡度 ≤ 1/32（或 1/16）且 HAND ≥ 3 m 且不是水面，4 邻连通块：占比、最大连片占比 |
| 山脊 | TPI（高出 1.5 km 方框均值）> 30 m 的连通块 ≥ 0.25 km²：长、宽（主轴）、走向、山脊线 100 m 分段的起伏与鞍部、侧坡冲沟密度（0.01 km² 河网在坡面的河源数 / 山脊长）、平行山脊间距 |
| 河漫滩与海岸低地 | HAND < 3 m 且坡度 < 5% 的面积与主河长度之比（平均宽度）；高出水面 0–1、1–3、3–10、10–20、20–40 m 的面积分布 |
| 土地覆盖与地貌 | NLCD 类别在河漫滩、平地、缓坡、中坡、陡坡、山脊上的占比 |

山地按 0.55 同比例（水平、垂直一起）缩小后重采样到同一 10 m 网格再统计，这样窗口在两边都是同样多的米（见 E）。已知局限：10 km 样方截断了外来大河的流域；激光雷达保留了道路、排水沟、田埂；平坦农田上的 D8 会沿排水沟走。

## C. 美国平原、山地和海岸的实测特征

完整表：[real_statistics.jpg](terrain_v2_research/real_statistics.jpg)、[stats/real_tiles.md](terrain_v2_research/stats/real_tiles.md)。要点：

**中西部冰碛平原（OH / IN / IL）**

- 很平：10 m 坡度中位数 1.2–2.2%，30 m 尺度 0.8–1.9%；坡度 ≤ 1/32 的面积 69–88%，> 1/8 的不到 3.5%。
- 起伏按尺度递增：30 m 窗口 0.3–0.6 m，100 m 1.2–2.5 m，300 m 2.9–7.5 m，1 km 6.7–20 m，3 km 16–32 m。频带 RMS：20–80 m 0.24–0.39 m，80–320 m 0.7–1.4 m，320–1280 m 1.5–2.7 m，1.3–5 km 3.8–7.8 m。**几百米到几公里的变化主要在河谷和谷边陡坎上，河间台地本身很平**（这一条决定了 AFL 平原的做法，见 E）。
- 水系是树枝状的：0.05 km² 河网密度 3.2–3.6 km/km²，汇流点 4.6–4.9 /km²，分岔比约 4；0.5 km² 河网的河道间距中位数 655–990 m。
- 谷深：分水岭离河高 HAND 中位数 4.1 m（OH）、8.3 m（IL）、13.9 m（IN，Wabash 是外来大河）；P90 14–36 m。
- 能建设：坡度 ≤ 1/32 且高出河网 3 m 的土地占 66–82%，最大连片 41–52 km²（占样方 41–52%）；放宽到 1/16 时最大连片 62–92 km²。
- 土地覆盖：耕地占 60–84%；平地（< 3%）上耕地 72%，8–20% 的坡上 67% 是落叶林，> 20% 的陡坡 82% 是落叶林；河漫滩约一半耕地、两成林地。
- 1 m 地表细节：16 m 窗口去平面后 RMS 中位数只有 0.07–0.09 m，2–32 m 频带 RMS 约 0.2 m——**比一格还小**。玩家脚下的"不平"来自 100 m 以上的尺度和人工痕迹，而不是细碎噪声。

**阿巴拉契亚山脊谷地（PA）**

- 平行山脊（走向一致性 0.60，平原只有 0.08–0.22），间距 2.5–3.0 km；山脊核心（TPI > 30 m）宽 360–530 m，最长连续 12 km，沿脊 7 处 ≥ 20 m 的鞍部；山脊线高度标准差 20–65 m（"连续但不规则"）。
- 侧坡冲沟每公里山脊 8.8 条（AP1）、12.5 条（AP2），即单侧约 230 m 一条。
- 山脊谷地的谷里不是平地：100 m 起伏中位数 15 m、300 m 37 m；1 km 窗口 162 m、3 km 290 m。
- 大河穿山缺口：Susquehanna 横切三道山脊，河漫滩窄（平均 70 m）。
- 土地覆盖：落叶林 42%，混交林 11%，耕地与牧草在谷底（19%）。
- 山前过渡（AP2）：Blue Mountain 前缘高出大谷 300–400 m，大谷里是页岩丘陵的密集树枝状切割。

**中大西洋海岸平原（VA York 河口）**

- 陆地 75% 坡度 ≤ 1/32；高出水面 1–3 m 的占 28%，3–10 m 占 44%，10–20 m 占 17%，20–40 m 占 8%。
- 河口是被淹没的河谷，潮汐河宽 0.5–3 km；湿地紧贴 0–1 m，蜿蜒潮沟；台地边缘是密集的树枝状冲沟（0.5 km² 河道间距 478 m）。
- 土地覆盖：水 23%，草本湿地 15%，常绿林（火炬松）14%，混交林 12%，木本湿地 10%；中坡以上 42% 混交林、26% 常绿林。

## D. 原 Phase 1 地貌设计的问题

Codex 分支 `codex/terrain-v2-phase1`（2026-10-07，没有合并）。用它自己的 `LandformPlan.targetHeight`，在同一种子、同样 6 km 的窗口上采样，用同一套代码统计（[stats/real_vs_codex_phase1_vs_afl_r1.md](terrain_v2_research/stats/real_vs_codex_phase1_vs_afl_r1.md)，对照图 [phase1_vs_v2.jpg](terrain_v2_research/phase1_vs_v2.jpg)）：

| 问题 | 公式 / 实测 |
|---|---|
| 平原是三条正弦波 | `80 + 4 sin(u/2400) + 3 sin(v/1700) + 2 sin((u+v)/1100)`，波长 7–15 km。实测：20–80 m 频带 RMS 0.002 m（真实 0.28）、80–320 m 0.04 m（真实 1.0）；500 m 细节窗口 16 m 残差为 0：玩家尺度是一张完全光滑的板，只有整体倾斜（走向一致性 0.68，真实 0.15） |
| 平原没有河谷 | 平原上只有 3 条规划河线，下垫面无切割；D8 在光滑斜面上产生 180 m 间距的平行流线（真实 640 m 的树枝状河网） |
| 山是一条光滑的"长面包" | 双高斯脊 `105 × (0.90 e^-((x+240)/420)² + 0.58 e^-((x−420)/350)²)`，沿一条正弦中线；实测走向一致性 0.95（宾州 ×0.55 为 0.64），1 条山脊、宽 159 m、侧沟 0，1 m 残差 0.005 m |
| 河流平行等距 | 3 条 24 段河谷从山脚向同一侧海岸平行排水，没有支流和汇流，河谷是预留带、不参与造地形 |
| 没有真实依据 | 引用了 USGS / EPA 资料但没有量任何 DEM，全部参数为设计假设 |
| 群系 | 没做（研究报告只列了原版群系名） |
| 现有两条高速主干 | 是东西、南北两条直线，在山带段目标纵坡 13–15%，没有解决 |

**保留的部分**：确定性、只读海陆掩码（`MacroGeography.sample`）、有界查询、规划与 Minecraft 无关、离线出图、城市适宜性的思路（连通分量、方块、通达性）。这些在新规划器里都继续用。

## E. 新地形生成算法及参数依据

规划器 `tools/terrain-v2-research/java/com/antaurora/apofirstlight/worldgen/terrain/v2/TerrainPlanV2.java`（研究副本，不编进 mod），导出 `PlanV2Export.java`。纯种子 + `MacroGeography`，16 m 网格覆盖 [−11264, 11264)²（1408² ≈ 198 万格）。**先水系，后地形**：

1. **地貌分区**（公里尺度，平滑权重，过渡 600–1500 m）：用主岛自身的主轴（陆地格 PCA，不改 `MacroGeography`）。一侧（种子决定）离海岸 3.9 km 处是**褶皱山带**：透镜形，长约主岛长轴的 64%，宽约 2.9 km，两端收窄、边缘不规则，像宾州弧形向外凸；山带与该侧海岸之间是**山前**和**海岸平原**（中大西洋的顺序：山、山前、海岸平原、河口）；其余是**冰碛平原**。海岸平原宽度沿岸起伏 ±38%（山带侧 1.8 km，另一侧 0.75 km），没有等宽环带。
2. **初始地表 H0**：
   - 一张区域面：从海岸升到**主分水岭**（在山带内侧 3.6 km，不是岛的中线），高差 10 m，再加长轴方向倾斜 ±2–4 m 和 7 km 尺度的起伏，所以流域大小不一。海岸后面有一道 4 m 的单调台地。
   - 平原：1.5 m @ 3.6 km 的宽缓起伏，1.3 m @ 800 m、0.6 m @ 300 m 的底碛缓丘（OH / IL 的冰碛面本身很平，几百米尺度的变化留给河谷），1–2 道终碛垄（高 8–14 m、宽 700–1200 m，Bloomington 型）。
   - 山带：抗蚀岩层是褶皱面 `L = cos(2πq/3200 + 漂移) + 轴部抬升(p)` 的等值线，每个抗蚀层在每个波长出露两次，**山脊间距约 1.6 km**（宾州 2.5–3 km × 0.55）；轴部沿走向抬升、下沉，两翼山脊在隆起处合拢成**船形端头和之字形**，各褶皱相位不同；山脊高 105 m × 沿走向 ±18% 变化，偶有风口；陡坎一侧比顺向坡陡；山脊面有 230 m 尺度的粗糙度，让坡面水汇成冲沟。山带谷里是高出区域面 24 m 的弱岩丘陵，留给河网切割。
   - 山前：高出区域面 22 m 的滚动丘陵。
3. **汇流**：优先洪泛填洼 + 抖动 D8（平坦区加 60–170 m 的路由微起伏，打散沿网格的直线）。**主干河先在"山脊立起之前"的地表上定线**（≥ 6 km²），再烧进初始面：它们比山脊老，所以切出**穿山缺口**（宾州 Susquehanna 的成因）；其余河网在有山脊的地表上汇流，沿走向形成格子状水系。
4. **河床纵剖面**：Flint 定律 S = ks · A^−0.45，从海面往上游积分。低海平面期的基准是 Y51（今天海面以下 12 m）：河谷先切下去，海面回升后被淹，形成**溺谷河口**，浅的淤成**潮汐湿地**（与 Chesapeake 的成因一致）。ks：平原 3.0、海岸 4.0、山前 4、山带弱岩 3.6、抗蚀岩 +36。
   - **年轻地貌的下切上限**：平原和海岸平原是冰后期 / 海退后的年轻地面，下切只随汇水面积的对数增加（平原 0.5 + 2.6·lg(A/5×10⁴) m，即 0.05 km² 约 0.5 m、5 km² 约 5.7 m）。但在更深的谷或海边，允许深度沿上游按 Lk = 120 m·(A/10⁵)^0.35 衰减（**溯源侵蚀**），所以主谷两侧有冲沟带、远处的台地保持平坦（Wabash 型）。山带和山前是老地貌，不设上限。
5. **谷坡**：从所有河道（和海岸线）同时往外按坡度"生长"，取下包络（16 邻域加权最短路），所以谷坡按离河的真实距离长，两个谷的坡相遇处成为分水岭；先是半宽 35·√A(km²) m 的河漫滩（坡 1.2%），再是谷坡：年轻地貌的谷坡陡度随下切深度变（深谷是陡岸，浅沟是缓洼），山带抗蚀岩 0.55、弱岩 0.22。不会高于初始地表；低于河道的洼地填到河道高度（冲积）。
6. **收尾**：两轮整体圆化、抗蚀岩上再圆化 3 轮、下切出来的河谷带圆化 6 轮（磨掉 D8 河道的直线和急转），淹没，填掉残余小坑，最后在结果上重新汇流得到最终河网和 Strahler 分级。
7. **单点查询** `heightAt(x, z)`：16 m 网格 Catmull-Rom 插值 + 48 m / 11 m / 4 m 的细节（平地 0.2 m，陡坡和山带更大；真实 1 m 激光雷达 16 m 残差 0.05–0.12 m）。

**尺度换算**：平原、海岸水平和垂直都按 1 格 = 1 m（真实起伏本来就是几米到几十米）。山地水平和垂直一起按 0.55 缩小：坡度不变，山脊间距、宽度、高差都是宾州的 0.55 倍。这样山带能放进 16 km 长的主岛，山脊中位 Y161、最高 Y228（世界高度 −64～320 不变）。

**验证（离线）**：
- 同一种子跑两次，高程、河网、水面、汇水面积的 SHA-256 完全一致；
- 汇水 ≥ 1 km² 的河道逆坡 0 处；16 m 网格上严格的单格局部最低点 67 个（填洼后残留的小坑）；所有河最终入海；
- 规划耗时 14.0 s（初始化：海陆掩码采样 198 万点、距离场、分区、初始地表共 4.7 s；水系与地貌 9.2 s），单线程；1 GB 堆可以跑完；
- 另外两个种子（1、20261010）规则同样成立：山带方位、长度、褶皱形态、河网和缺口都不同，逆坡 0，见 [other_seeds.jpg](terrain_v2_research/other_seeds.jpg)。

## F. 新全国地貌图

[national_landforms.jpg](terrain_v2_research/national_landforms.jpg)、总览 [national_overview.jpg](terrain_v2_research/national_overview.jpg)。东南大片冰碛平原，西北一条褶皱山带（中段船形闭合、分叉、合并，北端有穿山缺口），山带与西北海岸之间是山前和海岸平原；三座卫星岛是低海岸平原。

## G. 新目标高程图

[national_elevation.jpg](terrain_v2_research/national_elevation.jpg)。各分区 P1–P99：平原 Y66–87（中位 Y78，分水岭附近最高）、海岸平原 Y64–79、山前 Y76–118、山带谷地 Y69–115、抗蚀岩山脊 Y75–208（中位 Y161，全岛最高 Y228）；河口湾和湿地在 Y63 上下。等高线每 20 格。

## H. 新河流水系图

[national_rivers.jpg](terrain_v2_research/national_rivers.jpg)。树枝状河网；山带里的河沿走向流（格子状水系），主要经北端一处穿山缺口出山、进入北岸河口，另一支沿走向从山带西南端流出；平原河流从分水岭两侧分别入海，流域大小不一，没有平行等距的河。所有河口都是被淹没的河谷。

## I. 城市建设适宜性评估

[national_suitability.jpg](terrain_v2_research/national_suitability.jpg)，数据 [stats/afl_national_plan_r1.json](terrain_v2_research/stats/afl_national_plan_r1.json)。32 m 网格，分级与真实样方的"可建"口径一致：高 = 坡度 ≤ 1/32 且（大河 400 m 内）高出河网 ≥ 3 m、不在抗蚀岩、离水 ≥ 64 m；中 = ≤ 1/16；低 = ≤ 1/8；河漫滩、湿地、水面不适宜。

- 全岛陆地：高 54%，中 13%，低 10%，不适宜 23%。
- 城市候选（"高 + 中"4 邻连通块，城市本来就跨小谷建）：C1 48.0 km²，可放下边长 3.1 km 的完整方块；C2 15.3 km²、2.2 km（能放 2048² 城市）；C3 8.2 km²；C4 5.3 km²；C5 4.0 km²；C6 3.4 km²（另有 2 片 1.5–2 km² 的小块）。都在平原或海岸平原，均高 Y70–86。
- 港口候选：西岸（山带西南端外侧）一处河口（约 −6976, 2400），旁边 600 m 内中高适宜地最多。
- 候选间走廊（最小代价路径，代价见图注）全部 96 m 尺度纵坡 ≤ 1/16，最大 2.1–3.9%。
- 两条国家级主干的走廊建议（**只是地形可行性，不是高速规划**）：A 长轴干线 C5–C1–C6 约 20.7 km，最大坡度 3.9%，不穿山带；B 港口—内陆最省力路线 14.1 km、最大 3.0%（绕过山带端头）；B′ 经穿山缺口 14.7 km、最大 6.4%（99% 路段 ≤ 1/16）：缺口确实是山口通道，代价是坡度。现有 `HighwayRouteGraph` 的两条直线主干不再适用，高速阶段要按走廊重新选线。
- 宏观适宜不等于施工安全：浅层空洞、实际方块、道路施工仍需各自验证。

## J. 不同尺度的真实地形与 AFL 地形对比

6 组对比图，每组三个尺度：区域（约 6 km，10 m）、局部（约 2 km，2 m；山地缩放后 1.1 km）、细节（500 m，1 m）。同一渲染规则：颜色是本图第 1 百分位以上的相对高度，同一色阶；西北光源 45°；不夸张高度；坡度图分级 1/32、1/16、1/8、1/4、1/2。

主要数字（[stats/real_vs_afl_r1.md](terrain_v2_research/stats/real_vs_afl_r1.md)）：

| 指标 | 冰碛平原 真实 OH / IL | AFL 平原 | 宾州 ×0.55 | AFL 山带 | 海岸 真实 VA | AFL 海岸 |
|---|---|---|---|---|---|---|
| 坡度（30 m）P50 | 0.9% / 1.5% | 1.3% | 10.5% | 7.7% | 1.1% | 1.3% |
| 坡度 ≤ 1/32 占比 | 88% / 87% | 77% | 11% | 26% | 75% | 81% |
| 起伏 100 m P50 | 1.3 / 2.0 m | 1.9 m | 13.3 m | 10.1 m | 1.7 m | 1.9 m |
| 起伏 300 m P50 | 3.2 / 5.1 m | 5.5 m | 32 m | 29 m | 4.3 m | 5.6 m |
| 起伏 1 km P50 | 7.8 / 11.6 m | 12.0 m | 100 m | 97 m | 17 m | 41 m |
| 水系密度 0.05 km² | 3.4 / 3.3 | 2.9 | 2.3 | 2.8 | 2.8 | 2.9 |
| 分岔比 1/2 | 4.1 / 4.1 | 4.3 | 5.0 | 4.2 | 4.2 | 4.0 |
| 谷深 HAND P50 | 4.3 / 8.6 m | 6.6 m | 75 m | 29 m | 5.6 m | 17 m |
| 1 m 细节 16 m 残差 P50 | 0.08 / 0.09 m | 0.05 m | 0.08 m | 0.14 m | 0.06 m | 0.05 m |

- **吻合的**：平原的坡度、30 m–1 km 各尺度起伏、水系密度和分岔比；山带各尺度起伏（100 m–1 km 相差 3–25%）、走向一致性（0.62 对 0.64）。
- **AFL 偏差**：
  - 平原 30 m 以下的细节偏少（0.05 对 0.08 m，真实里有田埂、沟渠）；
  - 山带山脊偏窄（270 对 390 m）、侧沟偏少（每 km 1.0 对 3.3 条），谷深偏浅（宾州样方里有 Susquehanna 大河谷）；
  - 海岸台地偏高：1 km 起伏 41 对 17 m，谷深 17 对 5.6 m。这是因为岛小，AFL 海岸样窗紧挨着山前。
- **玩家尺度**：2 km 和 500 m 图上，河谷边缘不再有网格台阶，谷平面弯曲；山脊顶不再有锯齿；平地是 1–2 m 的缓起伏加平滑的谷坡。地表方块按 1 m 量化以后，平原上每隔 50–200 m 有一格的台阶，形状跟着这些缓起伏走，不是同心圆。

## K. 与现有 Terrain 生成架构的接入方案（Phase 2，未开始）

现有链路：`overworld.json` final_density → `MacroTerrainDensity`（陆海混合）→ LAND 走 `LandTerrainRelief` / `LandTerrainBias` / `InlandElevationBias` + 完整原版 3D 噪声和洞穴（Codex 研究报告 §3.2，抽查属实）。建议：

1. **规划器进 mod**：`TerrainPlanV2` 移到 `worldgen/terrain/plan/`（替代 Codex 的 `LandformPlan`，那个分支不合并），按种子 + 版本构建、有界缓存（建议最多 2 份），不引用 Level / 注册表。
2. **LAND 密度**：新的 density function 用 `H(x,z) = heightAt(x,z)` 作地表基准，密度 ≈ (H − y) × 斜率因子，加有界的三维细节（只在山带、陡坡）。去掉对 C/E 重映射和完整 base_3d_noise 的依赖。initial_density 与 final_density 用同一 H。保留海平面 63 和 −64～320。
3. **近地表稳定层**：在 H 以下 8–12 格（城市候选）/ 6–8 格（一般陆地）压制 noise cave 和 carver，24–32 格以下恢复（Codex 研究报告 §8 的方案，仍然有效）。
4. **水体**：海面和河口湾按规划的 water 掩码放水；河流（≥ 1–2 km²）需要统一的河道宽度、河床和单调水面，与 aquifer 一致，这是 Phase 2b。
5. **海陆一致**：规划把一部分 `MacroGeography` 判为陆地的格子淹成河口湾和湿地（本种子约 2.0 + 1.3 km²，约占陆地 2%）。Highway、结构、群系规划器都按 `MacroGeography` 判海陆，需要改为查询规划的水面，或给 `MacroGeography` 升版本吃进这张掩码。
6. **查询接口**：按 Codex 迁移计划的原则扩展 `TerrainQuery`，增加 MACRO_PREDICTED 来源：规划高程、地貌权重、HAND、适宜性、河道。不冒充真实地表。
7. **群系**：按生态分区（[national_ecology.jpg](terrain_v2_research/national_ecology.jpg)，见下）映射到受支持的生态，替换"默认 Fallout + 少量 Plains"；Fallout / 辐射作为独立灾难层（Codex 迁移计划 Phase 4/5 的边界不变）。
8. **新世界**：地形改了必须新世界；规划版本号 `afl_terrain_plan_v2_r1` 进世界 profile。

**生态分区**（潜在自然植被，规划，未实现）按真实样方 NLCD 与地貌的关系划分。占陆地比例：
- 农业冰碛平原 44%（栎-山核桃 / 山毛榉-槭，实际以农田为主）；
- 海岸平原松栎林 23%；
- 山脊栎林 11%；
- 河岸与河漫滩林 9%；
- 坡地冲沟落叶林 7%；
- 海岸沼泽林 2.5%；
- 山谷阴坡铁杉-白松 1.4%；
- 潮汐湿地 0.9%；
- 平原湿草甸 0.3%。

农业是土地利用，不等于草原；污染、焦土是独立的环境层。树木以后做 mesh，这里只定分区。

## L. 仍然存在的技术问题

- **规划耗时 14 s**（单线程）：生成新世界时会卡这一下。要优化（并行、粗网格先算），或者异步构建并按种子 + 版本缓存到世界目录。
- **内存**：16 m 全域栅格约 30 MB（高程、水面、汇水、河道、分区）；进 mod 后可以只留高程、水面和河道。
- **河道平面**仍来自 D8，靠河谷圆化掩盖；真正放水前要把河道抽成平滑折线（Chaikin）再刻河床。
- **山带侧沟偏少、山脊偏窄**：16 m 网格画不出 125 m 一条的冲沟；要么山带局部用 8 m 网格，要么用单点可求的侵蚀纹理补。
- **海岸台地偏高**；海岸线本身仍是 `MacroGeography` 的光滑轮廓，只有河口嵌进去。
- **河口、湿地与 `MacroGeography` 不一致**（见 K.5）。
- **Strahler 统计只在 ≥ 0.05 km² 河网上做**；10 km 真实样方截断外来流域，河道级数不可比。
- **山地 0.55 缩放**是设计选择：宾州的真实山脊比 AFL 宽、高、间距大。如果以后要更大的山，就要扩大主岛或提高世界上限（Codex 研究报告 §7 的结论 B 仍然成立）。
- **原版群系的雪线**：Taiga 一类低温群系在 Y200 以上的山脊会下雪；"局部针叶林"要用温度 ≥ 0.5 的群系，或在暖温群系里放针叶 feature。
- **未做**：实机生成、洞穴 / 稳定层、河流水体、群系、辐射层、Highway 选线、多种子批量统计。

## 文件与复现

`tools/terrain-v2-research/`（研究工具，不进 mod）：

| 文件 | 作用 |
|---|---|
| `tiles.json` | 6 块样方与细节窗口 |
| `geo.py` | EPSG:5070 正反算 |
| `fetch.py` | 下载：`python -I fetch.py preview|dem|nlcd|lidar|lidar2 build/terrain-v2-research/raw` |
| `tiff.py` | GeoTIFF 读取（无 GDAL） |
| `metrics.py`、`analyze.py` | 统计（真实与 AFL 同一套）：`analyze.py real RAW ID OUT [缩放 [裁剪]]`、`analyze.py afl 窗口.json OUT` |
| `summary.py` | 对比表 |
| `java/.../TerrainPlanV2.java`、`PlanV2Export.java` | 规划器与导出 |
| `run_plan.sh` | 一键：编译、规划、全国图、AFL 样窗统计、对比表：`bash tools/terrain-v2-research/run_plan.sh -295378578869149513 r1` |
| `national.py`、`national_plan.py` | 全国图、适宜性、候选、走廊、生态 |
| `render.py`、`compare.py`、`pages.py` | 晕渲、三尺度对比、验收图页（headless Chrome 截图） |

只用了 JDK 17、Python 3 + numpy、curl / urllib、Chrome；没有 Gradle。原始数据重新下载即可（`fetch.py`），`build/` 不进 Git。
