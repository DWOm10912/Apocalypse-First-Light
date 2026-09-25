# Terrain V2 Phase 1 / Macro Geography V1.1

状态：2026-09-22 起 `MacroGeography.VERSION = 3`，只把附属岛数量固定为3并增加稳定设施/桥梁政策metadata，详见 [Fixed Three Satellite Metadata V3](satellite_facility_islands_v3.md)。曾加入的天然Campus最终地形资格和初始化期候选重规划导致游戏崩溃，现已完整撤回；岛屿尺寸、位置、bank-fit、轮廓、缓存和terrain pipeline恢复V1.1行为。V1.1/version 2 的随机数量验收不覆盖固定三岛。Terrain密度公式未改，已有chunk不重写。

## 世界与确定性契约

当前只有一个 `MAIN_NATION`：一个连通 MAINLAND，加固定 3 个同国 SATELLITE_ISLAND。没有 FOREIGN_LAND、外国国家、第二大陆或远方占位岛；岛群与有限近岸水域之外持续为 OPEN_OCEAN，没有世界边界墙/传送。此承诺是基础 terrain/biome 的地理规则；Highway 仍可在海上施工，不能把基础海洋规则解释为所有 decoration/结构已禁止入海。

宏观拓扑唯一查询源：`src/main/java/com/antaurora/apofirstlight/worldgen/geography/MacroGeography.java`。
`MacroGeography.forSeed(worldSeed).sample(blockX, blockZ)` 返回 `MacroGeographySample`：

- `surfaceClass`: LAND / COAST / INLAND_WATER / OPEN_OCEAN。
- `nationId`: MAIN_NATION / NONE。陆地（包括干燥海岸）属于 MAIN_NATION；水域为 NONE。两岸归属由各自 landmass 判断。
- `regionId`: 主国陆地 1，水域 0；预留以后扩展，不生成第二国家。
- `landmassId`: 主岛 0；附属岛按确定性规划顺序 1–3；水域 -1。
- `landmassRole`: MAINLAND / SATELLITE_ISLAND / MINOR_ISLAND；NONE 用于水域。地理角色不混入设施用途；V3 通过 `satellitePolicy(id)` 的独立 metadata 分配 VIRUS_LAB / MILITARY_BASE / LARGE_PRISON，一岛一个，不生成建筑。MINOR_ISLAND 仍预留，数量为 0。
- `waterbodyId`: 内海 1、Bay 10–12、附属岛海峡 100–102、外海及其陆架 0；干陆地 -1。
- `waterClass`: NONE / INLAND_SEA / BAY / STRAIT / COASTAL_WATER / OPEN_OCEAN。
- `coastDistance`: 正为陆地、负为水；这是径向/椭圆组合场的格单位距离估计，**不是精确最近岸线的欧氏距离**。不可直接作为已验证的工程跨距。
- `surfaceHeight`: 冻结的旧版高度参考，目前仅水域仍用于真实海床。陆地实际零面由原版样条与三维噪声组合决定；原始 sample 高度不是最终陆地高度，`macro_height` 仅供海床与 COAST 分支使用。需通过生成器原有高度 API 查询，实际方块/heightmap 存在取整和插值误差。

规划继续只依赖seed、坐标与原V1常量，使用固定salt与整数hash；无运行时RNG、chunk顺序或已生成地形输入。按seed最多缓存16份不可变计划。新增角色shuffle使用独立稳定salt，完全不参与地形。不存在generator height采样、Campus搜索、初始化Mixin、可变snapshot或ThreadLocal候选预览。

## 主岛与水体 grammar

- 基础长轴 **14,600–16,400 blocks**、短轴 **11,400–12,600 blocks**。这是种子参数中的椭圆基础轴，不是旋转后世界 X/Z 包围盒。
- seed 决定旋转、轴长、低频 3/5/9 次海岸波动、正向半岛叶瓣、1–3 个 Bay 凹口（深 400–600 格）。不是固定圆，也不是无约束噪声撒岛。
- 主岛外岸是单值径向边界，任何方向半径下限 **4,800 格**，保证主体连通且 Spawn 不会很快抵达外洋。内部水体不计作 OPEN_OCEAN。
- `STARTUP_MAINLAND_RESERVE = 384`：拓扑保证完整干陆地；不设目标Y或最低支撑面，使用原版平原样条空间及完整三维密度。禁用丘陵/高山参数，384–768格平滑开启核心区的弱丘陵。地堡32–160主搜索、256备用搜索、入口与埋设检查未改；新起伏下地堡落地仍待实机检查。
- `MAINLAND_CORE_RADIUS = 3800`：无海洋、Bay 或内海切入。
- 内海 **0–1**：最多约840×1240格，在主轴肩部，距外岸约1050格；仅当离核心还有256格余量，且64个解析边界探针均保留至少256格陆地环时启用。失败则不生成内海，不改变核心。
- 附属岛固定 **3**：稳定slot ID为1/2/3，不按位置、面积或设施排序。除固定count外保持V1.1参数：长半轴550–750格、短半轴400–500格；轮廓仍为旋转椭圆径向场乘以 `1 + sin²(a) * (0.06*sin(3a+phase) + 0.04*cos(5a-phase))`。长向全长1100–1500格、短向保守下限720格、至少半径360的连续内接区域。当前不声称天然Campus资格。
- `supportingShore` 保持2048探针及40次细化；初始slot角仍为mainland rotation +0.6 + index×120° +原±0.125rad jitter，bank fitting仍最多33次±0.02rad递增修正。水隙400–500格，岸内96–192格检查64×128格bank。没有新增1024 spacing、固定±20°sector、8候选池、尺寸放大或地形高度筛选。
- 三岛非地形metadata均为`BRIDGE_REQUIRED`，每岛继续暴露一个`crossingCandidates()`项。正式routing恢复原solver语义：逐岛尝试，失败写diagnostic，不让地理规划或世界初始化因Campus/bridge前置资格而崩溃。Sea Bridge V1.1系统未改；桥梁破坏留待后续Sea Bridge Damage State。
- Major Coastal Feature 采用同国附属岛与 MAINLAND 形成的 STRAIT_COMPLEX；沿用 seed 驱动的原有海湾及可选内海，不新增强制内海，不改主岛轮廓、轴长、出生位置或核心区。原主岛的海湾/半岛公式保持不变。
- 附属岛同样采用缓坡海岸，预留乘船登陆；本轮没有测试实际船只登陆、也没有破坏状态系统。

## 地形、海床与地下

继续使用 `NoiseBasedChunkGenerator`，海平面 **Y=63**，高度范围仍为 -64 到319。

注册五个 DensityFunction codec：`apocalypse_firstlight:macro_height`、`land_relief`、`land_bias`、`inland_elevation_bias`、`macro_terrain`。`RandomStateSeedMixin` 在 vanilla noise wiring **之前**绑定宏观节点，并把 `land_relief` recipe 展开为 Vanilla DensityFunctions 图与已绑定 seed 的内陆偏置节点；随后 RandomState 遍历完整图绑定每个 noise，包括 TerrainProvider spline 坐标中的输入。相同 recipe 在一次 router 绑定中复用展开结果。没有全局“当前世界 seed”；Nether/End 没有这些节点，不受影响。

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
→ offset 分支：-0.50375F + offsetSpline + InlandElevationBias
→ 原版 splineWithBlending（blendAlpha + flatCache/cache2d）
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

`initial_density_without_jaggedness` 另走原版预估链：`slide(clamp(4*quarterNegative(depth*cache2d(factor)) - 0.703125, -64,64))`。它与 final LAND slope 共用带内陆偏置的 offset/depth，不含 base noise、jagged 或 cave，不再拿 final slope冒充 initial density。router 原始气候 depth 字段仍不改。

保留的 `min/max/clamp` 含义：

- LAND final 中洞穴/entrances/pillars/noodle 的 min/max，以及 cave cheese 的 clamp，均来自原版洞穴组合，负责洞穴与实心柱，不是水平支撑面。
- initial 的 [-64,64] clamp 来自原版初始密度，仅用于预估地表，不是最终地表限高。
- AFL clamp 仅限制 **输入参数**、smoothstep权重；不截断 LAND 的密度零面。
- ocean surface 的 [-1,1] clamp 与6/24屋盖只在水域及其 COAST 过渡分支保留，不在 LAND 分支运行。

#### Plains-heavy 参数分布

`LandTerrainBias` 只读X/Z、原c/e与冻结地理角色，完全不读取Y。bias发生在 TerrainProvider 样条之前，router的原始气候c/e/r/depth字段不改。`smooth` 是clamped cubic smoothstep，阈值不是实测占比：

| 区间 | 参数映射/开启条件 |
| --- | --- |
| Plains默认 | `w = smooth((coastDistance - 48) / 384)`（仅 MAIN_NATION，其余为0）；`c' = 0.12 + 0.04*c + 0.16*w`；`e' = lerp(0.65 + 0.04*e, 0.63 + 0.02*e, w)` |
| Rolling | 原e从-0.10至-0.45平滑开启，e'趋向0.15 |
| Highland | 仅外主岛，原e从-0.40至-0.60开启，e'趋向-0.35、c'趋向0.35 |
| Mountain | 仅外主岛，原e从-0.70至-0.82且原c从0.35至0.65双门控，e'趋向-0.80、c'趋向0.65 |

这些是嵌套平滑门控，不是互斥格网或随机抽签。Rolling/Highland/Mountain 的门控仍读取原始 c/e，阈值、权重、目标值不变；独立 elevation bias 不参与这些门控或三套样条。ridges及folded-ridges保持原版输入；jaggedness由偏置参数下的原版样条决定，不再对其结果追加AFL振幅门控。c'总体范围仍约0.08–0.65；完整内陆 plains 基底为0.24–0.32，e基底为0.61–0.65，仍为正 erosion。这里限制的是参数空间，不是地表Y或最终密度；不保证每个谷底高于海平面，也不禁止自然局部低谷/水洼。

- ≤384启动区：rolling/highland/mountain门控为0，保留原版3D小起伏，无固定Y保护。
- 384–768：平滑进入核心区弱rolling；核心3800以内rolling权重0.65，高地/山地门控0。
- 主岛3800–4824：平滑恢复外主岛权重，仍以平原偏置为基底。
- 附属岛rolling权重0.50，高地/山地门控0；不绑定岛屿用途。
- 目标：自然平原65–75%、rolling20–30%、高地3–8%、真正山地1–3%或更低。**没有执行面积统计或实机验证，不声称已经达到目标**；未恢复vanilla原始参数分布，未以最终密度缩放或限高实现平原。

#### Inland Elevation Rebalance（历史方案，已被替代）

`Inland Surface Water Audit` 对 seed `-1010026562491965701` 的既有中心区完整区块抽取32,768列：固体地表低于Y63为43.66%，顶层WATER为26.08%，其中99.31%的水面处于Y62。这是旧存档的局部样本，不能外推为全国比例或新参数结果；但与 LAND 平均高程偏低、海平面流体填充低地的代码路径一致。Macro LAND 分类本身并不保证实际地面露出水面。

该轮只修改了 `src/main/java/com/antaurora/apofirstlight/worldgen/geography/LandTerrainBias.java`，先给予continentalness最多+0.16的偏置。原版1.20.1 `TerrainProvider.overworldOffset / buildErosionOffsetSpline` 在e约0.65的分支对这项C调整反应很弱：当时201点样条探针中，(c,e)=(0.12,0.65)→(0.28,0.65) 的平均offset增量仅0.001427；改为(0.28,0.565)时为0.043632。这些只是历史样条响应，不是实际surface Y或水面比例预测。旧 `0.565 + 0.01*e` 方案经用户反馈增加了丘陵/高地观感，现已由 `0.63 + 0.02*e` 基线与独立 elevation bias 替代。C/E 同时控制 offset/factor/jaggedness，单靠它们无法独立调整 plains frequency 和 average elevation。

#### Continuous Inland Elevation Bias

职责分离：`LandTerrainBias` 继续负责 plains/rolling/highland/mountain 的参数分布；新增 `src/main/java/com/antaurora/apofirstlight/worldgen/geography/InlandElevationBias.java` 只按冻结 `MacroGeography` 的 MAIN_NATION 与 coastDistance 计算平均高程偏置。`LandTerrainRelief.resolve` 将它加在 `-0.50375F + TerrainProvider.overworldOffset(...)` 后、原版 `splineWithBlending` 前，因此保留 blendAlpha、blendOffset、flatCache/cache2d，旧 blending 区域中该偏置随 blendAlpha 缩放。initial 与 final recipe 均走这处接入；factor、jaggedness、base_3d_noise、cave composition、slide/blend_density/interpolated/×0.64/squeeze/min(noodle) 顺序不改。`AflDensityFunctions` 注册 `apocalypse_firstlight:inland_elevation_bias`，`RandomStateSeedMixin` 同步其直接节点的 seed 绑定；正常 recipe 在展开时已传入同 seed 的 geography。没有新增 density JSON 或噪声资源。

数学依据来自当前 Forge `1.20.1-47.4.22_mapped_official_1.20.1` sources：`NoiseRouterData.registerTerrainNoises` 的 depth 为 `yClampedGradient(-64,320,1.5,-1.5) + offset`，`DensityFunctions.YClampedGradient.compute` 使用 clampedMap；范围内 Y 导数为 `-3/384 = -1/128`。选择 **FULL_OFFSET_BIAS = 0.046875**，所以完整内陆 `ΔY = 128 × 0.046875 = 6` 格。对固定 X/Z、非顶部/底部 clamp 区域，`depth_new(Y) = depth_old(Y - 6*w)`；固定 factor/jaggedness 且将 base noise 视为常数时，gradient/sloped_cheese 零面具有同样的平移量。这不改变两处 full-bias 地点的样条相对高差。真实 base_3d_noise 和洞穴仍在原世界 Y 采样，未整体移动整个三维噪声场，因此最终地表只能预期约4–8格量级，不能保证每一列精确+6或地表相对高差逐块不变。

`bias = 0.046875 * smooth(clamp((coastDistance - 48)/384, 0, 1))`，仅 MAIN_NATION 生效；水域、整个48格干COAST带为0，48–432格间连续增加，≥432格取完整偏置。cubic smoothstep两端一阶导数为0，最高距离方向等效附加坡度为 `6*1.5/384 = 0.0234375` 格/格。它只使用现有低频 coastDistance，没有随机noise、周期波纹、已生成方块/流体/区块顺序输入；不引入 coast cliff 的硬切换。coastDistance 是冻结地理场的距离估计，不是精确岸线测量；连续公式并不代表已完成实机海岸验收。

附件规定的 C/E 基线保持为 `.16 / .63 / .02`。实现前实际源码中 erosion 常量误写为 `63`，超出该类声明的 `.70` 上界；本轮将这个缺失小数点纠正为 `.63`，没有采用降低正确 erosion 基线来抬地。Startup≤384、Core≤3800、Outer 的原始 rolling/highland/mountain gates 均未修改；elevation 不额外读取这些半径，不在其边界产生高度台阶。Startup/Core 获得内陆偏置而不增加 relief 门控；Outer 仍使用原分布。SATELLITE_ISLAND 同样按 coastDistance 获得0–6格等效抬升，较窄岛内可能仅获得部分偏置；保留rolling权重0.50及高地/山地门控0，没有改变岛形/大小/数量。

没有fixed-Y floor、最低支撑、`(height-Y)` LAND主链、MacroHeightDensity陆地接管、target height或自定义heightfield。只钳制 ramp 权重，不钳制surface Y。sea level仍Y63，aquifers保持开启，default fluid仍 `minecraft:water`。Macro Geography代码、MAIN_NATION/mainland/island形状及数量、IDs、Bay/Strait/Inland Sea/Open Ocean、CrossingCandidate与300–600格桥隙均未改；同seed Macro Export输入算法保持，未重新运行导出。Highway、Rural、Bunker未改。抬升本身不保证陆地无水；后续曾有Scorched专属抑水，现已随Biome Region Planner V1移除，当前仅保留共享Macro保水，见下文。

验证：直接调用当前 mapped `TerrainProvider.overworldOffset`，6组固定C/E/folded-ridges参数的固定噪声零面均精确+6格，相对高差保持；3种factor、3种固定base noise、Y=-30..230共14,094次gradient平移比较通过。coast ramp有界、单调及48/240/432格对应0/3/6格等效抬升检查通过。这些是纯数学探针，不是完整三维噪声或实际水面统计。`compileJava --offline` 使用现有用户Gradle缓存构建成功（沙箱初次访问缓存锁被拒，授权执行后成功）；只有现有弃用/unchecked警告。无resource变更，未运行processResources。未生成新区块、未做多seed或新世界统计、未运行客户端；实际水面减少、自然平原Y68–75、无平台/断层及地堡落地均待用户新世界验收，不是硬阈值或已通过结论。

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

## Biome、出生生态与保水 — MAIN_NATION Biome Region Planner V1

当前实现见[群系策略](群系.md)。本次仅修改biome分配与Scorched相关行为，Terrain relief/elevation与Macro topology不改。

- MAINLAND：1个Startup Plains（核心160，边界208±32），0～3个Additional，其余LAND为Fallout。Satellite LAND全部Fallout。
- Additional目标数量0/1/2/3概率40%/40%/15%/5%，每slot最多128候选，失败跳过。基础半径96～224、低频角度扰动24～48、最大包络间隔128；仅MAINLAND LAND，保守包围方形检查距岸≥112，不覆盖Beach。
- MainNationBiomeRegionPlan是seed-only immutable缓存；StartupEcologyState与StartupSurfaceBiomeContext调用同一biomeAt，统一quart坐标与地下判据。原始TerraBlender/Vanilla LAND候选不再保留随机Plains。
- Macro水域→Ocean，宏观海床高度<32→Deep Ocean；干COAST→Beach，始终先于LAND planner。48格岸带、384出生reserve、3800主陆核心不变。
- Deep Dark从候选过滤，最终resolver回退Fallout，关闭自然Deep Dark/sculk来源；Ancient City仍禁用。Lush/Dripstone保留原地下判据：水域海床深度>12，陆地上方12格initial density>0.390625。
- Scorched biome/key/JSON/surface rule删除。两个方块scorched_soil/fused_ground完整保留，未来用于灾害外围与爆心/弹坑，Damage系统未实现。
- Scorched→EXTREME和Scorched→WHITE_ASH的biome绑定删除；Fallout/Plains当前辐射规则保持，海洋/Beach仍为UNKNOWN而非自动SAFE；WHITE_ASH能力保留。
- Rural原允许Plains/Fallout，原Scorched土地转Fallout而扩大资格是允许变化。

### Worldgen Hygiene Fix：结构权限与地表熔岩湖

data/minecraft/tags/worldgen/biome/has_structure中shipwreck、shipwreck_beached、ocean_ruin_cold、ocean_ruin_warm、buried_treasure继续由空tag禁用；既有Ocean Monument、Ancient City及stronghold禁用保持。AFL Rural自有tag不变。

data/apocalypse_firstlight/tags/worldgen/biome/surface_lava_suppression.json现在列Plains、Beach、Ocean、Deep Ocean、Fallout，删除Scorched项。forge/biome_modifier/remove_surface_lava_lakes.json仍仅移除lakes步骤的minecraft:lake_lava_surface；地下熔岩湖、spring_lava、lava aquifer及其他规则保持。

### Macro water保留；Scorched Surface Water Suppression已移除

旧Scorched专属12格近地表抑水已废止，删除biome检查、相关列缓存和SurfaceWaterSuppressingAquifer，不扩展为Fallout抑水。

src/main/java/com/antaurora/apofirstlight/mixin/NoiseChunkMacroWaterMixin.java保留原共享逻辑：绑定Macro seed，256槽完整XZ键缓存；getInterpolatedState RETURN在isWater且surfaceHeight≤y<63时补WATER。Ocean/Coastal Water/Bay/Strait/Inland Sea、海床填充条件、default fluid和aquifer开关不变。诊断上下文泛化为worldgen/aquifer/MacroAquiferContext.java。旧Scorched采样器与开发命令删除。

Surface Cave Seal仍DEFERRED；不改洞口、carver、canyon、noodle、spaghetti、LandTerrainBias或InlandElevationBias。**Biome planner change requires new world**，不迁移旧存档。

compileJava、processResources通过；单seed -4332662446239654818的401项小检查通过，实际Additional中心1471,6107，radius179/amplitude30。静态确认无live旧biome引用且两个方块保留。未启动客户端、生成世界/chunk、GameTest、大规模测试或截图；等待用户新世界/Xaero验收。

## Rural / Highway边界

只在 `RuralNaturalStructure.findGenerationPoint` 的入口，对既有完整reservation做16格间距的有界解析探针；要求MAINLAND并且coastDistance至少128格。没有修改Rural planner、道路、building/lot、农业地块、模板或阈值。已有Piece与开发命令不回溯执行此新gate；第三方将Rural移植到别的noise settings不启用这个macro gate。

历史边界：Terrain V2 本轮未改 Highway。后续 [Highway V2 Route Graph Phase 1](highway_v2_route_graph_phase1.md) 已替换旧无限走廊/整列预留：renderer 与 claim 共用两条有限主干，在宏观海岸前终止，不再批准 OPEN_OCEAN 路线。Sea Bridge/附属岛支线仍未实现；此次仅 Highway 路由更新，Macro Geography topology 未改。

后续Highway V2必须共同修改route及claim来源：主国LAND正常通行；同一MAIN_NATION内 MAINLAND↔SATELLITE_ISLAND 的300–600格水道可作为本版海桥候选；OPEN_OCEAN必须终止/绕行，不自动跨洋，不自动连接外国。coastDistance用于approach初筛；crossingCandidates只提供拓扑，需要后续验证实际水面跨度、岸上空间、纵坡、海床和桥墩。当前没有Highway海桥参数，也未实现Sea Bridge、Bridge Damage、City或Port。

future City/Port/Foreign Land只保留ID扩展接口，均未实现；外国没有任何物理占位陆块。Bridge Destruction未实现。

## 用户手动验收

### Dev-only 宏观地图导出

现行扩展：[Highway Network Export V2](highway_network_export_v2.md) 在原PNG/TXT内叠加真实Highway图、节点、planned sea reservation，并列出已连接与未连接卫星岛及可证实的失败原因。下文“无岛屿用途判断/桥只为审计标记/不接入worldgen”为原Macro候选图层的历史描述；蓝绿色planned crossing表示Highway路由预留，仍未生成物理海桥。原Macro地理采样及候选验证规则未变。

开发环境命令：`/afl macro export`，可选 `/afl macro export <radius> [step]`。默认以原点为中心采样 ±12,000 blocks，步长 32 blocks（751×751 个解析样本）；radius 限 9,000–16,000，step 限 16–128，最多 1,000,000 样本。需要 Overworld 正常 AFL noise settings 和命令权限等级 2。命令位于发布 JAR 排除的 `src/dev/java/.../dev` 包。

写入游戏工作目录 `afl_debug/macro/macro_geography_<seed>.png` 和同名 `.txt`（通常是 `run/afl_debug/macro/`）。PNG 图例分别标 MAINLAND、SATELLITE_ISLAND、MINOR_ISLAND、干岸、INLAND_SEA、BAY、STRAIT、COASTAL_WATER、OPEN_OCEAN；标出 SPAWN、主岛核心圈、启动保留圈、附属岛中心与地理角色。通过2格查询探针验证的300–600格桥候选画黄虚线，无岛屿用途判断。

TXT 对每岛列 `landmassId/landmassRole/nationId`、采样bounds/width/height/area、major/minor/gap/angle、稳定slot、facilityRole、bridgePolicy、`facilityEligibilityStatus = NOT_EVALUATED`、`nearestMainlandWaterGap`、`sampledCrossingWaterGap`和`bridgeCandidate300to600`。不再输出Campus bounds/Y、资格尝试、spacing或地形拒绝原因。Summary要求恰好3岛；桥候选仍是审计标记，不等同工程成功。

TXT 记录 seed、采样分辨率、MAIN_NATION/主岛与附属岛 landmassId/role/采样 bounds 和面积、spawn 至最近 OPEN_OCEAN/INLAND_SEA 的采样距离、waterbodyId 与水体分类/覆盖、foreign/non-main-nation land 采样检查、外圈外洋占比、spawn 安全标记、桥候选清单。各项 bounds、距离、面积均是采样估计，不能当作精确海岸测量。桥只为审计标记，不接入 worldgen。实现只调用 `MacroGeography.forSeed(seed).sample(x,z)` 与现有只读规划记录，不读取或生成 chunk、Xaero/DH 地图，也不修改 Terrain V2 参数。

开发环境命令：`/afl dev macro_geography` 查询当前坐标；`/afl dev macro_geography <x> <z>` 查询任意坐标（只读、不加载chunk）。显示sample、基础轴长、附属岛中心与海峡两岸坐标。此命令位于dev包，发布JAR排除；生成日志仍有 `[AFL MACRO GEO]` 摘要。

建议在新建正常世界检查：地堡正常首登；0,0与reserve为干陆；沿岛岸观察beach/ocean与实际水面一致；远处如20000,20000为持续大洋；附属岛岸线缓坡与两岸ID正确；水下有海床及地下洞穴/矿物。旧Highway可能跨海不在此阶段修复。上述均为待用户执行的检查，不是已通过结果。

本轮不运行runClient、clean、GameTest、多seed自动worldgen、大型benchmark或批量生成chunk，不commit/push，不部署发布包。
