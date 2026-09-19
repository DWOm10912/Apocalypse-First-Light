# Terrain V2 Phase 1 / Macro Geography V1

状态：代码与资源已接入；本轮验证限定为 `compileJava` / `processResources`，执行结果见交付报告。未进行客户端、GameTest、多 seed worldgen 或视觉验收。必须用正常 Overworld **新世界**手动验收；不重写旧 chunk，旧世界边界接缝不在本轮解决范围。

## 世界与确定性契约

V1 只有一个 `MAIN_NATION`：一个连通 MAINLAND，加 0–3 个同国较大附属岛。没有 FOREIGN_LAND、外国国家、第二大陆或远方占位岛；岛群与有限近岸水域之外持续为 OPEN_OCEAN，没有世界边界墙/传送。此承诺是基础 terrain/biome 的地理规则；现有 Highway 仍可能在海上施工，不能把基础海洋规则解释为所有 decoration/结构已禁止入海。

唯一查询源：`src/main/java/com/antaurora/apofirstlight/worldgen/geography/MacroGeography.java`。
`MacroGeography.forSeed(worldSeed).sample(blockX, blockZ)` 返回 `MacroGeographySample`：

- `surfaceClass`: LAND / COAST / INLAND_WATER / OPEN_OCEAN。
- `nationId`: MAIN_NATION / NONE。陆地（包括干燥海岸）属于 MAIN_NATION；水域为 NONE。两岸归属由各自 landmass 判断。
- `regionId`: 主国陆地 1，水域 0；预留以后扩展，不生成第二国家。
- `landmassId`: 主岛 0；附属岛按确定性规划顺序 1–3；水域 -1。
- `landmassRole`: MAINLAND / MILITARY_ISLAND / INDUSTRIAL_ISLAND / STRATEGIC_ISLAND；NONE 用于水域。MINOR_ISLAND 仅为预留元数据，V1 不撒景观小岛。
- `waterbodyId`: 内海 1、Bay 10–12、战略岛海峡 100–102、外海及其陆架 0；干陆地 -1。
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
- 附属岛 **0–3**：半径380–600格；角色按稳定槽位为军事/工业/战略。与主岛径向水面间距144–208格，并用主岛排除场保持分离。role 只是元数据，没有基地、工业建筑或城市。
- 每座附属岛暴露一个 `crossingCandidates()` 项，包含两岸内部坐标、两端landmass ID、waterbody ID和径向水面跨度。海峡语义限定在候选轴线两侧96格的有限走廊。没有正式桥梁，也不保证工程纵坡/基础已合格。
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

后续Highway V2必须共同修改route及claim来源：主国LAND正常通行；两岸同国且有明确对岸的INLAND_SEA/BAY/STRAIT可成为桥候选；OPEN_OCEAN必须终止/绕行，不自动跨洋。coastDistance用于approach初筛；crossingCandidates只提供拓扑，需要后续验证实际水面跨度、岸上空间、纵坡、海床和桥墩。海桥跨度128–256格为后续建议，当前没有Highway海桥参数。

future City/Port/Foreign Land只保留ID扩展接口，均未实现；外国没有任何物理占位陆块。Bridge Destruction未实现。

## 用户手动验收

开发环境命令：`/afl dev macro_geography` 查询当前坐标；`/afl dev macro_geography <x> <z>` 查询任意坐标（只读、不加载chunk）。显示sample、基础轴长、附属岛中心与海峡两岸坐标。此命令位于dev包，发布JAR排除；生成日志仍有 `[AFL MACRO GEO]` 摘要。

建议在新建正常世界检查：地堡正常首登；0,0与reserve为干陆；沿岛岸观察beach/ocean与实际水面一致；远处如20000,20000为持续大洋；附属岛岸线缓坡与两岸ID正确；水下有海床及地下洞穴/矿物。旧Highway可能跨海不在此阶段修复。上述均为待用户执行的检查，不是已通过结果。

本轮不运行runClient、clean、GameTest、多seed自动worldgen、大型benchmark或批量生成chunk，不commit/push，不部署发布包。
