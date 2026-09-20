# Terrain V2 Phase 1 / Macro Geography V1.1

状态：V1.1 宏观拓扑曾由用户完成 3 个随机 seed 的导出验收，`MacroGeography.VERSION = 2` 保持不变。Terrain Relief Tuning V1 与 Vanilla Relief Reintegration 均已被用户实机判定不合格；Parity Audit 将旧实现归为 **C：自定义 density graph 部分复用 Vanilla signals**。当前 **Vanilla Density Mainline Restoration** 的 LAND 密度主链已获用户实机视觉验收，本次 Worldgen Hygiene Fix 不改该主链；地貌面积比例与地堡落地未在本次验证。已有 chunk 不重写，新旧 chunk 的高度接缝不在本次范围。

## 世界与确定性契约

V1.1 只有一个 `MAIN_NATION`：一个连通 MAINLAND，加 1–3 个同国 SATELLITE_ISLAND。没有 FOREIGN_LAND、外国国家、第二大陆或远方占位岛；岛群与有限近岸水域之外持续为 OPEN_OCEAN，没有世界边界墙/传送。此承诺是基础 terrain/biome 的地理规则；现有 Highway 仍可能在海上施工，不能把基础海洋规则解释为所有 decoration/结构已禁止入海。

宏观拓扑唯一查询源：`src/main/java/com/antaurora/apofirstlight/worldgen/geography/MacroGeography.java`。
`MacroGeography.forSeed(worldSeed).sample(blockX, blockZ)` 返回 `MacroGeographySample`：

- `surfaceClass`: LAND / COAST / INLAND_WATER / OPEN_OCEAN。
- `nationId`: MAIN_NATION / NONE。陆地（包括干燥海岸）属于 MAIN_NATION；水域为 NONE。两岸归属由各自 landmass 判断。
- `regionId`: 主国陆地 1，水域 0；预留以后扩展，不生成第二国家。
- `landmassId`: 主岛 0；附属岛按确定性规划顺序 1–3；水域 -1。
- `landmassRole`: MAINLAND / SATELLITE_ISLAND / MINOR_ISLAND；NONE 用于水域。旧用途型枚举已移除，未发现其他生产代码依赖；不存在内容用途字段，军事基地、监狱、机场等由未来 content planner 分配。MINOR_ISLAND 仍预留，本轮数量为 0。
- `waterbodyId`: 内海 1、Bay 10–12、附属岛海峡 100–102、外海及其陆架 0；干陆地 -1。
- `waterClass`: NONE / INLAND_SEA / BAY / STRAIT / COASTAL_WATER / OPEN_OCEAN。
- `coastDistance`: 正为陆地、负为水；这是径向/椭圆组合场的格单位距离估计，**不是精确最近岸线的欧氏距离**。不可直接作为已验证的工程跨距。
- `surfaceHeight`: 冻结的旧版高度参考，目前仅水域仍用于真实海床。陆地实际零面由原版样条与三维噪声组合决定；原始 sample 高度不是最终陆地高度，`macro_height` 仅供海床与 COAST 分支使用。需通过生成器原有高度 API 查询，实际方块/heightmap 存在取整和插值误差。

规划只依赖 seed、坐标、V1 常量，使用固定盐值与整数 hash；无 Math.random、世界运行时 RNG、已生成 chunk 扫描或探索顺序输入。按 seed 最多缓存 16 份不可变参数，持有中的生成器继续引用自己的计划；不缓存无界坐标表。地形 DF、生态状态和生成上下文持有计划，不在每个 block 上查全局缓存。

## 主岛与水体 grammar

- 基础长轴 **14,600–16,400 blocks**、短轴 **11,400–12,600 blocks**。这是种子参数中的椭圆基础轴，不是旋转后世界 X/Z 包围盒。
- seed 决定旋转、轴长、低频 3/5/9 次海岸波动、正向半岛叶瓣、1–3 个 Bay 凹口（深 400–600 格）。不是固定圆，也不是无约束噪声撒岛。
- 主岛外岸是单值径向边界，任何方向半径下限 **4,800 格**，保证主体连通且 Spawn 不会很快抵达外洋。内部水体不计作 OPEN_OCEAN。
- `STARTUP_MAINLAND_RESERVE = 384`：拓扑保证完整干陆地；不设目标Y或最低支撑面，使用原版平原样条空间及完整三维密度。禁用丘陵/高山参数，384–768格平滑开启核心区的弱丘陵。地堡32–160主搜索、256备用搜索、入口与埋设检查未改；新起伏下地堡落地仍待实机检查。
- `MAINLAND_CORE_RADIUS = 3800`：无海洋、Bay 或内海切入。
- 内海 **0–1**：最多约840×1240格，在主轴肩部，距外岸约1050格；仅当离核心还有256格余量，且64个解析边界探针均保留至少256格陆地环时启用。失败则不生成内海，不改变核心。
- 附属岛 **1–3**：数量为 `1 + floor(unit(50) * 3)`，ID 按规划槽位为 1..N，角色全部 SATELLITE_ISLAND，归 MAIN_NATION。长向半轴550–750格，短向半轴400–500格；轮廓为旋转椭圆径向场乘以 `1 + sin²(a) * (0.06*sin(3a+phase) + 0.04*cos(5a-phase))`。长向全长1100–1500格，短向保守下限720格，内含至少半径360格的连续陆地区域；每个方向边界唯一且正值，不切碎、不裁岛，不生成第二大陆。
- `supportingShore` 在现有主岛外岸求指定方向最大投影（2048探针及40次局部细化）。岛屿放在外侧支撑线之外，面向主岛的轮廓端点不受扰动，水隙400–500格，位于要求的300–600格区间内。原先径向144–208格/圆岛规则已废弃。规划在独立角域内最多33个确定性方向尝试，岸内96–192格检查64×128格桥头范围（16格探针，至少8格内部余量）；若无法满足契约则明确报错，绝不悄悄退回零岛或无效候选。
- 每座附属岛暴露一个 `crossingCandidates()` 项，包含两岸内部坐标、两端landmass ID、waterbody ID和支撑岸线水面跨度估计。相对支撑线与岛体角域保证分离；导出还以不大于2格步长验证 MAINLAND→连续水体→同国 SATELLITE_ISLAND，不接受无关陆块或OPEN_OCEAN。海峡语义限定在候选轴线两侧96格的有限走廊。没有正式桥梁；纵坡、桥墩与真实方块登陆条件留待工程层验收。
- Major Coastal Feature 采用同国附属岛与 MAINLAND 形成的 STRAIT_COMPLEX；沿用 seed 驱动的原有海湾及可选内海，不新增强制内海，不改主岛轮廓、轴长、出生位置或核心区。原主岛的海湾/半岛公式保持不变。
- 附属岛同样采用缓坡海岸，预留乘船登陆；本轮没有测试实际船只登陆、也没有破坏状态系统。

## 地形、海床与地下

继续使用 `NoiseBasedChunkGenerator`，海平面 **Y=63**，高度范围仍为 -64 到319。

注册四个 DensityFunction codec：`apocalypse_firstlight:macro_height`、`land_relief`、`land_bias`、`macro_terrain`。`RandomStateSeedMixin` 在 vanilla noise wiring **之前**绑定宏观节点，并把 `land_relief` recipe 展开为普通 Vanilla DensityFunctions 图；随后 RandomState 遍历完整图绑定每个 noise，包括 TerrainProvider spline 坐标中的输入。相同 recipe 在一次 router 绑定中复用展开结果。没有全局“当前世界 seed”；Nether/End 没有这些节点，不受影响。

### Parity Audit 与废弃实现

旧 Reintegration 虽然调用了 TerrainProvider，但仍以宏观Y76基准替换原版 depth，手算并重标定 sloped cheese，再以 `max(slope,(floor-Y)/8)` 加入内陆Y66支撑；最外层的 `MacroTerrainDensity` 又在深度6以内覆盖为表层包络，6–24混合地下图。因此不是“Vanilla主链 + 参数偏置”，而是架构C。最低支撑会截断谷底，浅层包络会覆盖洞口；此外旧版只插值 base noise，不能代替原版对完整洞穴/地表组合的插值。不能据静态代码将截图中每一处墙面都归于单一公式。

以下旧逻辑已退出 **LAND** 实际路径：宏观目标高度、`(baseHeight-Y)/128` 替代深度、factor最小2.5、岸线对各个noise的振幅压缩、cheese等效深度重标定、固定Y66支撑、最终密度±1,000,000钳制、浅层6/24屋盖。旧实现不再作为兼容fallback保留。

### 当前 LAND 主链与原版 parity

以当前 Forge 1.20.1 mapped source 的 `NoiseRouterData.registerTerrainNoises / overworld / underground / postProcess / slideOverworld` 为依据：

```text
现有 continents / erosion → LandTerrainBias（仅参数重映射）
原版 ridges / ridges_folded ───────────────┐
                                        ↓
TerrainProvider offset / factor / jaggedness splines
→ 原版 splineWithBlending（blendAlpha + flatCache/cache2d）
→ offset = -0.50375F + offsetSpline
→ depth = yClampedGradient(-64,320,1.5,-1.5) + offset
→ jagged = jaggedness * halfNegative(jaggedNoise)
→ gradient = 4 * quarterNegative((depth + jagged) * factor)
→ sloped_cheese = gradient + 原版 base_3d_noise
→ rangeChoice(sloped_cheese, -1e6, 1.5625,
    min(sloped_cheese, 5 * entrances), 原版 underground(sloped_cheese))
→ 原版顶部/底部 slide
→ blend_density
→ interpolated（包住前面的整个组合）
→ ×0.64 → squeeze
→ min(noodle)
→ LAND final_density（直接返回，无AFL表层包络）
```

`LandTerrainRelief` 不再 compute 自定义密度，而是一次性组装原版图的 recipe；未在 RandomState 展开时会明确报错。使用真正的 TerrainProvider 样条与原版 folded-ridges DF，不自行按单点 float 重写样条。不单独插值 base_3d_noise，不压缩最终地形振幅。

`initial_density_without_jaggedness` 另走原版预估链：`slide(clamp(4*quarterNegative(depth*cache2d(factor)) - 0.703125, -64,64))`。它不含 base noise、jagged 或 cave，不再拿 final slope冒充 initial density。

保留的 `min/max/clamp` 含义：

- LAND final 中洞穴/entrances/pillars/noodle 的 min/max，以及 cave cheese 的 clamp，均来自原版洞穴组合，负责洞穴与实心柱，不是水平支撑面。
- initial 的 [-64,64] clamp 来自原版初始密度，仅用于预估地表，不是最终地表限高。
- AFL clamp 仅限制 **输入参数**、smoothstep权重；不截断 LAND 的密度零面。
- ocean surface 的 [-1,1] clamp 与6/24屋盖只在水域及其 COAST 过渡分支保留，不在 LAND 分支运行。

#### Plains-heavy 参数分布

`LandTerrainBias` 只读X/Z、原c/e与冻结地理角色，完全不读取Y。bias发生在 TerrainProvider 样条之前，router的原始气候c/e/r/depth字段不改。`smooth` 是clamped cubic smoothstep，阈值不是实测占比：

| 区间 | 参数映射/开启条件 |
| --- | --- |
| Plains默认 | `e' = 0.65 + 0.04*e`，`c' = 0.12 + 0.04*c` |
| Rolling | 原e从-0.10至-0.45平滑开启，e'趋向0.15 |
| Highland | 仅外主岛，原e从-0.40至-0.60开启，e'趋向-0.35、c'趋向0.35 |
| Mountain | 仅外主岛，原e从-0.70至-0.82且原c从0.35至0.65双门控，e'趋向-0.80、c'趋向0.65 |

这些是嵌套平滑门控，不是互斥格网或随机抽签。ridges及folded-ridges保持原版输入；jaggedness由偏置参数下的原版样条决定，不再对其结果追加AFL振幅门控。c'保持内陆参数范围（约0.08–0.65），避免 vanilla ocean continentalness 在主岛内部生成宏观海洋；这不是保证每个谷底都高于海平面的硬支撑，也不禁止自然局部低谷/水洼。

- ≤384启动区：rolling/highland/mountain门控为0，保留原版3D小起伏，无固定Y保护。
- 384–768：平滑进入核心区弱rolling；核心3800以内rolling权重0.65，高地/山地门控0。
- 主岛3800–4824：平滑恢复外主岛权重，仍以平原偏置为基底。
- 附属岛rolling权重0.50，高地/山地门控0；不绑定岛屿用途。
- 目标：自然平原65–75%、rolling20–30%、高地3–8%、真正山地1–3%或更低。**没有执行面积统计或实机验证，不声称已经达到目标**；未恢复vanilla原始参数分布，未以最终密度缩放或限高实现平原。

#### Macro/ocean 职责与资源路径

`MacroTerrainDensity` 是 LAND / COAST / water 路由器：

- `coastDistance >= 48`（LAND）：立即返回 `land.compute(context)`，不计算heightfield或浅层屋盖。
- 水域（distance≤0）：沿用冻结海床高度、旧海床斜率与海底洞穴/6–24格屋盖。
- 现有干COAST带（0<distance<48）：用 `smooth(distance/48)` 将海岸参考分支连续混合到LAND图；只有这段宏观海岸包络参与过渡，不扩展到LAND内部。海岸的垂直形态因新LAND端点改变，需要实机验收。
- `MacroHeightDensity`：水域返回冻结 `sample.surfaceHeight()`；COAST干侧延伸为海岸参考Y62。这个值不作为LAND基准，不再输出内陆Y76。

资源接线（均在 `src/main/resources/data/`）：

- `minecraft/worldgen/noise_settings/overworld.json`：final为macro路由器；land输入 `terrain/macro_caves`，terrain输入 `terrain/ocean_sloped_cheese`，underground输入 `terrain/ocean_caves`；initial输入 `terrain/macro_initial_density`。
- `apocalypse_firstlight/worldgen/density_function/terrain/macro_sloped_cheese.json`：LAND recipe，原版continents/erosion/ridges/ridges_folded、未独立插值的base_3d_noise、原版jagged1500/0输入。
- `.../land_initial_density.json`：相同参数recipe，`initial: true`，展开原版预估密度。
- `.../macro_caves.json`：原有原版cave/final组合保留，三个sloped-cheese引用现在解析为恢复后的LAND主链。
- `.../macro_height.json` / `ocean_sloped_cheese.json`：海床柱高度及其斜率；仅water/COAST分支消费。
- `.../ocean_caves.json`：独立海底洞穴组合，三个sloped-cheese引用绑定ocean slope，避免LAND参数改写远海海床。
- `.../macro_initial_density.json`：同一地理路由，LAND用原版initial，water用原海床slope，无地下混合。

自定义数据包若复制旧codec内容需更新：`land_relief` 删除base_height、增加folded_ridges与可选initial；`macro_terrain` 增加land与可选initial，terrain现在只指海床斜率。已有区块不重写，不承诺旧版本资源定义兼容。

海床离岸分层、ocean shelf/basin、OPEN_OCEAN/BAY/STRAIT/INLAND_SEA masks与sea level **Y63** 保持。约220格内下降12格（陆架约Y50），再向Y27、远海Y8过渡；罕见盆地可再降26格、细节约±3格。数值是冻结场值，不是本轮实测。现有宏观水域fill/getBaseHeight/getBaseColumn保水入口及其有界256列缓存不改；海床以下继续原aquifer/global fluid picker。

LAND洞穴入口、spaghetti、pillars、noodle重新由原版组合直接参与浅表密度，不再被AFL屋盖封口；随新地形变化，不能承诺洞形逐块不变。Aquifer噪声、ore vein节点、矿石feature、deepslate规则未改。海床SurfaceRules继续：Y≥45 sand，Y≥20 gravel，Y≥0 stone，更低deepslate；无新增方块。

`StartupEcologyState` 必要同步：不再把新initial密度当“等效深度/8”；地下候选在其上方12格的initial密度>0.390625（NoiseChunk原版preliminary阈值）时保留。水域仍用冻结海床深度>12。该值是预估，不是精确扫描地表；COAST沿同一宏观initial混合。TerraBlender初始化每个Overworld ParameterList只创建一次同seed/同资源的只读RandomState，没有chunk扫描或第二套目标高度图；单点查询与NoiseChunk插值仍有差异。SAFE/Fallout/Radiation二维边界与biome policy未改。

地堡32–160主搜索、256备用搜索及埋设检查未改；启动区保护来自平原参数偏置，不保证未经实机验证的地堡落地结果。宏观拓扑类未修改，mainland/island shape/count/size、IDs、384/3800平面范围及300–600桥候选保持；2D导出工具不改，未重新运行导出。`/afl dev macro_geography` 的surfaceHeight仍是旧参考值，不可作为LAND高度验收。

密度主链恢复时未处理结构与地表熔岩湖；后续 Worldgen Hygiene Fix 已独立补齐指定 Vanilla 结构的禁用 tag，并仅移除当前地表 biome 的自然 surface lava lake。Rural、Highway/claims、Sea Bridge、City、Port 不属于此修复。

## Biome、出生生态与保水

`MacroBiomePolicy` 共用于 TerraBlender ParameterList 的 `StartupEcologyState` 和 `StartupSurfaceBiomeContext`：

- 宏观水域→ocean；零面低于32→deep_ocean；干陆岸带48格→beach。
- 主国内陆→原AFL地表规则；气候候选偶然选到marine时归回Plains。
- ocean/deep_ocean/beach加入vanilla候选白名单，但实际位置由macro gate决定。
- 地下洞穴biome依据同资源initial密度在上方12格的preliminary判据保留（水域仍用海床深度>12）；接近表面时服从既有地理/陆地规则。
- macro水域判定先于startup生态覆盖。384格reserve及完整core保证Plains/SAFE不会落在实际海洋。辐射系统未重构；海洋当前仍走UNKNOWN biome的自然场语义，**不是自动无辐射海洋**。
- `NoiseChunkScorchedAquiferMixin` 显式跳过宏观水域与48格干岸带，再执行原Scorched近地表12格水抑制。不依赖generated block scan。

所有主岛/水体surface查询与密度使用同一seed计划；高度查询仍通过生成器原API，适用于Rural/Highway的noise-time采样。

### Worldgen Hygiene Fix：结构权限与地表熔岩湖

真实 `minecraft:ocean`、`minecraft:deep_ocean`、`minecraft:beach` biome 继续由宏观地理规则返回；biome 语义与 Vanilla structure 许可独立。`src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/` 现在以 `replace: true, values: []` 显式清空 `shipwreck`、`shipwreck_beached`、`ocean_ruin_cold`、`ocean_ruin_warm`、`buried_treasure` 五个原版 tag。之前已有的 Ocean Monument 等空 tag 与 stronghold 的 structure set 禁用保持不变；AFL Rural 使用自己的 `apocalypse_firstlight:has_structure/rural`，不受这些 Vanilla tag 影响。

`src/main/resources/data/apocalypse_firstlight/tags/worldgen/biome/surface_lava_suppression.json` 精确列出当前可返回、需要禁止自然地表熔岩湖的 `minecraft:plains`、`minecraft:beach`、`minecraft:ocean`、`minecraft:deep_ocean`、`apocalypse_firstlight:fallout_barrens`、`apocalypse_firstlight:scorched_lands`。`src/main/resources/data/apocalypse_firstlight/forge/biome_modifier/remove_surface_lava_lakes.json` 使用 `forge:remove_features`，仅从 `lakes` 步骤移除 `minecraft:lake_lava_surface`。原先只作用 Scorched Lands、且仅移除同一特征的 `scorched_lands_remove_surface_liquids.json` 已由统一规则替代。`minecraft:lake_lava_underground`、`minecraft:spring_lava`、共用的 `minecraft:lake_lava` configured feature、lava aquifer、其他湖泊与海水仍保留。此规则只覆盖列出的当前 AFL 地表 biome，不声称涵盖未来第三方新增 biome。

本次只有 worldgen 资源与本文档变更；Terrain V2 density、Macro Geography、Highway、Rural 均未修改。资源处理结果见交付报告；未做新世界/多 seed 实机回归，因此不宣称已目测消除旧区块的沉船或熔岩湖。

## Rural / Highway边界

只在 `RuralNaturalStructure.findGenerationPoint` 的入口，对既有完整reservation做16格间距的有界解析探针；要求MAINLAND并且coastDistance至少128格。没有修改Rural planner、道路、building/lot、农业地块、模板或阈值。已有Piece与开发命令不回溯执行此新gate；第三方将Rural移植到别的noise settings不启用这个macro gate。

历史边界：Terrain V2 本轮未改 Highway。后续 [Highway V2 Route Graph Phase 1](highway_v2_route_graph_phase1.md) 已替换旧无限走廊/整列预留：renderer 与 claim 共用两条有限主干，在宏观海岸前终止，不再批准 OPEN_OCEAN 路线。Sea Bridge/附属岛支线仍未实现；此次仅 Highway 路由更新，Macro Geography topology 未改。

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
