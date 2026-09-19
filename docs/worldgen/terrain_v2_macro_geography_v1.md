# Terrain V2 Phase 1 / Macro Geography V1.1

状态：用户已完成 V1 首轮实机与 Dev Export 验收。V1.1 定向更新附属岛拓扑和导出审计，规划版本 `MacroGeography.VERSION = 2`；本轮只执行 `compileJava`，结果见交付报告，未执行 V1.1 实机或多 seed 验收。正常 Overworld **新世界**使用新岛屿布局；已有 chunk 不重写，旧世界中新旧岛岸接缝不在本轮解决范围。相同 seed 在 V1.1 内保持相同规划，不承诺 V1 与 V1.1 的附属岛一致。

## 世界与确定性契约

V1.1 只有一个 `MAIN_NATION`：一个连通 MAINLAND，加 1–3 个同国 SATELLITE_ISLAND。没有 FOREIGN_LAND、外国国家、第二大陆或远方占位岛；岛群与有限近岸水域之外持续为 OPEN_OCEAN，没有世界边界墙/传送。此承诺是基础 terrain/biome 的地理规则；现有 Highway 仍可能在海上施工，不能把基础海洋规则解释为所有 decoration/结构已禁止入海。

唯一查询源：`src/main/java/com/antaurora/apofirstlight/worldgen/geography/MacroGeography.java`。
`MacroGeography.forSeed(worldSeed).sample(blockX, blockZ)` 返回 `MacroGeographySample`：

- `surfaceClass`: LAND / COAST / INLAND_WATER / OPEN_OCEAN。
- `nationId`: MAIN_NATION / NONE。陆地（包括干燥海岸）属于 MAIN_NATION；水域为 NONE。两岸归属由各自 landmass 判断。
- `regionId`: 主国陆地 1，水域 0；预留以后扩展，不生成第二国家。
- `landmassId`: 主岛 0；附属岛按确定性规划顺序 1–3；水域 -1。
- `landmassRole`: MAINLAND / SATELLITE_ISLAND / MINOR_ISLAND；NONE 用于水域。旧用途型枚举已移除，未发现其他生产代码依赖；不存在内容用途字段，军事基地、监狱、机场等由未来 content planner 分配。MINOR_ISLAND 仍预留，本轮数量为 0。
- `waterbodyId`: 内海 1、Bay 10–12、附属岛海峡 100–102、外海及其陆架 0；干陆地 -1。
- `waterClass`: NONE / INLAND_SEA / BAY / STRAIT / COASTAL_WATER / OPEN_OCEAN。
- `coastDistance`: 正为陆地、负为水；这是径向/椭圆组合场的格单位距离估计，**不是精确最近岸线的欧氏距离**。不可直接作为已验证的工程跨距。
- `surfaceHeight`: 连续的 density 零面 Y；实际最高实体方块/heightmap 存在取整和噪声插值误差，并非已落地方块扫描。

规划只依赖 seed、坐标、V1 常量，使用固定盐值与整数 hash；无 Math.random、世界运行时 RNG、已生成 chunk 扫描或探索顺序输入。按 seed 最多缓存 16 份不可变参数，持有中的生成器继续引用自己的计划；不缓存无界坐标表。地形 DF、生态状态和生成上下文持有计划，不在每个 block 上查全局缓存。

## 主岛与水体 grammar

- 基础长轴 **14,600–16,400 blocks**、短轴 **11,400–12,600 blocks**。这是种子参数中的椭圆基础轴，不是旋转后世界 X/Z 包围盒。
- seed 决定旋转、轴长、低频 3/5/9 次海岸波动、正向半岛叶瓣、1–3 个 Bay 凹口（深 400–600 格）。不是固定圆，也不是无约束噪声撒岛。
- 主岛外岸是单值径向边界，任何方向半径下限 **4,800 格**，保证主体连通且 Spawn 不会很快抵达外洋。内部水体不计作 OPEN_OCEAN。
- `STARTUP_MAINLAND_RESERVE = 384`：完整干陆地，零面约 Y=74.5–77.5，缓变微地形。384–768 格平滑接回自然地形。保留地堡32–160主搜索、256备用搜索、入口与埋设检查；没有修改 bunker。
- `MAINLAND_CORE_RADIUS = 3800`：无海洋、Bay 或内海切入。
- 内海 **0–1**：最多约840×1240格，在主轴肩部，距外岸约1050格；仅当离核心还有256格余量，且64个解析边界探针均保留至少256格陆地环时启用。失败则不生成内海，不改变核心。
- 附属岛 **1–3**：数量为 `1 + floor(unit(50) * 3)`，ID 按规划槽位为 1..N，角色全部 SATELLITE_ISLAND，归 MAIN_NATION。长向半轴550–750格，短向半轴400–500格；轮廓为旋转椭圆径向场乘以 `1 + sin²(a) * (0.06*sin(3a+phase) + 0.04*cos(5a-phase))`。长向全长1100–1500格，短向保守下限720格，内含至少半径360格的连续陆地区域；每个方向边界唯一且正值，不切碎、不裁岛，不生成第二大陆。
- `supportingShore` 在现有主岛外岸求指定方向最大投影（2048探针及40次局部细化）。岛屿放在外侧支撑线之外，面向主岛的轮廓端点不受扰动，水隙400–500格，位于要求的300–600格区间内。原先径向144–208格/圆岛规则已废弃。规划在独立角域内最多33个确定性方向尝试，岸内96–192格检查64×128格桥头范围（16格探针，至少8格内部余量）；若无法满足契约则明确报错，绝不悄悄退回零岛或无效候选。
- 每座附属岛暴露一个 `crossingCandidates()` 项，包含两岸内部坐标、两端landmass ID、waterbody ID和支撑岸线水面跨度估计。相对支撑线与岛体角域保证分离；导出还以不大于2格步长验证 MAINLAND→连续水体→同国 SATELLITE_ISLAND，不接受无关陆块或OPEN_OCEAN。海峡语义限定在候选轴线两侧96格的有限走廊。没有正式桥梁；纵坡、桥墩与真实方块登陆条件留待工程层验收。
- Major Coastal Feature 采用同国附属岛与 MAINLAND 形成的 STRAIT_COMPLEX；沿用 seed 驱动的原有海湾及可选内海，不新增强制内海，不改主岛轮廓、轴长、出生位置或核心区。原主岛的海湾/半岛公式保持不变。
- 附属岛同样采用缓坡海岸，预留乘船登陆；本轮没有测试实际船只登陆、也没有破坏状态系统。

## 地形、海床与地下

继续使用 `NoiseBasedChunkGenerator`，海平面 **Y=63**，高度范围仍为 -64 到319。

注册 `apocalypse_firstlight:macro_height` 与 `apocalypse_firstlight:macro_terrain` 两个 DensityFunction codec。`RandomStateSeedMixin` 在 vanilla noise wiring 前仅绑定自定义 macro height 节点的 seed，不用全局“当前世界 seed”。不存在这些节点的 Nether/End router 不受影响。

资源：

- `data/minecraft/worldgen/noise_settings/overworld.json`：`initial_density_without_jaggedness` 改用共享 macro slope；`final_density` 使用 macro terrain 表面包络。
- `data/apocalypse_firstlight/worldgen/density_function/terrain/macro_height.json`：按柱 cache_2d 包装共享高度函数。
- `.../macro_sloped_cheese.json`：`(surfaceHeight - Y) / 8`。
- `.../macro_caves.json`：从原 final_density 提取洞穴组合，sloped_cheese 输入改接 macro slope，其余 cave entrances/spaghetti/pillars/noodle、底部/顶部过渡保留。
- 原 `minecraft:overworld/continents` 仍为陆地气候/既有地下节点的兼容输入，不再作为实际无限陆地的来源。

陆地 detail 与岸边距离同源衰减：主要低起伏平原，600/80格尺度微起伏，1300格 rolling hills，1800格场高阈值才有少量山地。所有地表 detail 在岸线归零，外洋没有重新抬高大陆的分支。

V1.1 未改上述 Terrain Relief 数值、DensityFunction codec、worldgen JSON 或 biome gating。新岛的几何距离直接进入现有 `sample().surfaceHeight()`，由 `MacroHeightDensity` 驱动真实陆地密度；`MacroBiomePolicy` 对相同 sample 的 isLand/COAST 使用现有陆地/海岸策略，不存在 export 专用假岛。

海床由离岸深度分层平滑下降：约220格内下降12格（陆架约Y=50），再向约Y=27普通海过渡，远海约Y=8；低频罕见盆地可再降26格，细节约±3格。内海/Bay/Strait使用较浅层，不启用深海盆地。数值是设计场值，实际视觉待手测。

宏观零面以Y=62为岸线参考，对齐“水在Y<63”的离散流体规则。NoiseChunk在宏观水域中，仅将零面以上至Y=62的材质结果统一为水，避免vanilla局部aquifer压力/随机水位留下干浅海；真实fill及getBaseHeight/getBaseColumn共用此入口。每个NoiseChunk有最多256列的有界临时查询缓存，不是全局坐标网格。海床以下继续原aquifer/global fluid picker，没有从海平面挖空到基岩。surface包络最上6格为实体屋盖，6–24格逐渐恢复洞穴，24格以下完整使用洞穴组合。**地下洞穴保留，不宣称洞口完全不变：浅层洞口会被此包络封闭/调整。** Aquifer噪声、fluid picker、ore vein节点及矿石feature未重做。

海床/海岸SurfaceRules优先处理 ocean/deep_ocean/beach 顶部约3层：Y≥45 sand，Y≥20 gravel，Y≥0 stone，更低 deepslate。其余陆地继续原规则，无新增方块。

## Biome、出生生态与保水

`MacroBiomePolicy` 共用于 TerraBlender ParameterList 的 `StartupEcologyState` 和 `StartupSurfaceBiomeContext`：

- 宏观水域→ocean；零面低于32→deep_ocean；干陆岸带48格→beach。
- 主国内陆→原AFL地表规则；气候候选偶然选到marine时归回Plains。
- ocean/deep_ocean/beach加入vanilla候选白名单，但实际位置由macro gate决定。
- 地下洞穴biome在零面以下12格保留；接近表面时服从地理/陆地规则。
- macro水域判定先于startup生态覆盖。384格reserve及完整core保证Plains/SAFE不会落在实际海洋。辐射系统未重构；海洋当前仍走UNKNOWN biome的自然场语义，**不是自动无辐射海洋**。
- `NoiseChunkScorchedAquiferMixin` 显式跳过宏观水域与48格干岸带，再执行原Scorched近地表12格水抑制。不依赖generated block scan。

所有主岛/水体surface查询与密度使用同一seed计划；高度查询仍通过生成器原API，适用于Rural/Highway的noise-time采样。

## Rural / Highway边界

只在 `RuralNaturalStructure.findGenerationPoint` 的入口，对既有完整reservation做16格间距的有界解析探针；要求MAINLAND并且coastDistance至少128格。没有修改Rural planner、道路、building/lot、农业地块、模板或阈值。已有Piece与开发命令不回溯执行此新gate；第三方将Rural移植到别的noise settings不启用这个macro gate。

Highway与HighwaySpatialClaimProvider **本轮未改**，仍是无限走廊/整列预留。因此当前新世界可能仍有旧Highway穿海；这是Phase 2待办，不是Sea Bridge已接入。

后续Highway V2必须共同修改route及claim来源：主国LAND正常通行；同一MAIN_NATION内 MAINLAND↔SATELLITE_ISLAND 的300–600格水道可作为本版海桥候选；OPEN_OCEAN必须终止/绕行，不自动跨洋，不自动连接外国。coastDistance用于approach初筛；crossingCandidates只提供拓扑，需要后续验证实际水面跨度、岸上空间、纵坡、海床和桥墩。当前没有Highway海桥参数，也未实现Sea Bridge、Bridge Damage、City或Port。

future City/Port/Foreign Land只保留ID扩展接口，均未实现；外国没有任何物理占位陆块。Bridge Destruction未实现。

## 用户手动验收

### Dev-only 宏观地图导出

开发环境命令：`/afl macro export`，可选 `/afl macro export <radius> [step]`。默认以原点为中心采样 ±12,000 blocks，步长 32 blocks（751×751 个解析样本）；radius 限 9,000–16,000，step 限 16–128，最多 1,000,000 样本。需要 Overworld 正常 AFL noise settings 和命令权限等级 2。命令位于发布 JAR 排除的 `src/dev/java/.../dev` 包。

写入游戏工作目录 `afl_debug/macro/macro_geography_<seed>.png` 和同名 `.txt`（通常是 `run/afl_debug/macro/`）。PNG 图例分别标 MAINLAND、SATELLITE_ISLAND、MINOR_ISLAND、干岸、INLAND_SEA、BAY、STRAIT、COASTAL_WATER、OPEN_OCEAN；标出 SPAWN、主岛核心圈、启动保留圈、附属岛中心与地理角色。通过2格查询探针验证的300–600格桥候选画黄虚线，无岛屿用途判断。

TXT 对每岛列 `landmassId/landmassRole/nationId`、采样 bounds/width/height/area、`nearestMainlandWaterGap`（支撑岸线几何估计）、`sampledCrossingWaterGap`（不大于2格探针估计）、`bridgeCandidate300to600`。Summary 记录 `satelliteIslandCount`、`minorIslandCount`、`bridgeCandidateCount300to600`、`requiredSatelliteIslandRuleSatisfied` 和 `requiredBridgeCandidateRuleSatisfied`，后两项分别检查1–3座附属岛及至少1个合格桥候选。外洋审计半径通过 `outerOceanRadius()` 根据岛群包络加320格余量推导，不再固定10000格；拒绝不足以覆盖岛群和外围的导出半径，默认12000格仍覆盖现有参数上限。

TXT 记录 seed、采样分辨率、MAIN_NATION/主岛与附属岛 landmassId/role/采样 bounds 和面积、spawn 至最近 OPEN_OCEAN/INLAND_SEA 的采样距离、waterbodyId 与水体分类/覆盖、foreign/non-main-nation land 采样检查、外圈外洋占比、spawn 安全标记、桥候选清单。各项 bounds、距离、面积均是采样估计，不能当作精确海岸测量。桥只为审计标记，不接入 worldgen。实现只调用 `MacroGeography.forSeed(seed).sample(x,z)` 与现有只读规划记录，不读取或生成 chunk、Xaero/DH 地图，也不修改 Terrain V2 参数。

开发环境命令：`/afl dev macro_geography` 查询当前坐标；`/afl dev macro_geography <x> <z>` 查询任意坐标（只读、不加载chunk）。显示sample、基础轴长、附属岛中心与海峡两岸坐标。此命令位于dev包，发布JAR排除；生成日志仍有 `[AFL MACRO GEO]` 摘要。

建议在新建正常世界检查：地堡正常首登；0,0与reserve为干陆；沿岛岸观察beach/ocean与实际水面一致；远处如20000,20000为持续大洋；附属岛岸线缓坡与两岸ID正确；水下有海床及地下洞穴/矿物。旧Highway可能跨海不在此阶段修复。上述均为待用户执行的检查，不是已通过结果。

本轮不运行runClient、clean、GameTest、多seed自动worldgen、大型benchmark或批量生成chunk，不commit/push，不部署发布包。
