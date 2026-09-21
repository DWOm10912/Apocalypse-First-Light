# Terrain V2 Phase 1 / Macro Geography V1.1

状态：V1.1 宏观拓扑曾由用户完成 3 个随机 seed 的导出验收，`MacroGeography.VERSION = 2` 保持不变。Terrain Relief Tuning V1 与 Vanilla Relief Reintegration 均已被用户实机判定不合格；Parity Audit 将旧实现归为 **C：自定义 density graph 部分复用 Vanilla signals**。**Vanilla Density Mainline Restoration** 的 LAND 密度主链已获用户实机视觉验收。当前 **Continuous Inland Elevation Bias** 在保留该主链及 operator 顺序的基础上，将平均高程偏置与 plains-heavy C/E 分布分开；旧 Inland Elevation Rebalance 的低 erosion 抬升方案已退出。新偏置的实际水面比例、地貌观感、地堡落地及 Highway 适应性待用户新世界验收。已有 chunk 不重写，新旧 chunk 的高度接缝不在本次范围。

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

没有fixed-Y floor、最低支撑、`(height-Y)` LAND主链、MacroHeightDensity陆地接管、target height或自定义heightfield。只钳制 ramp 权重，不钳制surface Y。sea level仍Y63，aquifers保持开启，default fluid仍 `minecraft:water`。Macro Geography代码、MAIN_NATION/mainland/island形状及数量、IDs、Bay/Strait/Inland Sea/Open Ocean、CrossingCandidate与300–600格桥隙均未改；同seed Macro Export输入算法保持，未重新运行导出。Highway、Rural、Bunker未改。抬升本身不保证焦土无水；后续独立水策略见下文 Scorched Lands Surface Water Suppression V1，待新世界验收。

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

## Biome、出生生态与保水

`MacroBiomePolicy` 共用于 TerraBlender ParameterList 的 `StartupEcologyState` 和 `StartupSurfaceBiomeContext`：

- 宏观水域→ocean；零面低于32→deep_ocean；干陆岸带48格→beach。
- 主国内陆→原AFL地表规则；气候候选偶然选到marine时归回Plains。
- ocean/deep_ocean/beach加入vanilla候选白名单，但实际位置由macro gate决定。
- 地下洞穴biome依据同资源initial密度在上方12格的preliminary判据保留（水域仍用海床深度>12）；接近表面时服从既有地理/陆地规则。
- macro水域判定先于startup生态覆盖。384格reserve及完整core保证Plains/SAFE不会落在实际海洋。辐射系统未重构；海洋当前仍走UNKNOWN biome的自然场语义，**不是自动无辐射海洋**。
- `NoiseChunkScorchedAquiferMixin` 排除真实宏观水域；干岸带不再无条件跳过，而是与LAND一样仅在实际地表biome为Scorched Lands时应用下述水策略。不依赖generated block scan。

所有主岛/水体surface查询与密度使用同一seed计划；高度查询仍通过生成器原API，适用于Rural/Highway的noise-time采样。

### Worldgen Hygiene Fix：结构权限与地表熔岩湖

真实 `minecraft:ocean`、`minecraft:deep_ocean`、`minecraft:beach` biome 继续由宏观地理规则返回；biome 语义与 Vanilla structure 许可独立。`src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/` 现在以 `replace: true, values: []` 显式清空 `shipwreck`、`shipwreck_beached`、`ocean_ruin_cold`、`ocean_ruin_warm`、`buried_treasure` 五个原版 tag。之前已有的 Ocean Monument 等空 tag 与 stronghold 的 structure set 禁用保持不变；AFL Rural 使用自己的 `apocalypse_firstlight:has_structure/rural`，不受这些 Vanilla tag 影响。

`src/main/resources/data/apocalypse_firstlight/tags/worldgen/biome/surface_lava_suppression.json` 精确列出当前可返回、需要禁止自然地表熔岩湖的 `minecraft:plains`、`minecraft:beach`、`minecraft:ocean`、`minecraft:deep_ocean`、`apocalypse_firstlight:fallout_barrens`、`apocalypse_firstlight:scorched_lands`。`src/main/resources/data/apocalypse_firstlight/forge/biome_modifier/remove_surface_lava_lakes.json` 使用 `forge:remove_features`，仅从 `lakes` 步骤移除 `minecraft:lake_lava_surface`。原先只作用 Scorched Lands、且仅移除同一特征的 `scorched_lands_remove_surface_liquids.json` 已由统一规则替代。`minecraft:lake_lava_underground`、`minecraft:spring_lava`、共用的 `minecraft:lake_lava` configured feature、lava aquifer、其他湖泊与海水仍保留。此规则只覆盖列出的当前 AFL 地表 biome，不声称涵盖未来第三方新增 biome。

本次只有 worldgen 资源与本文档变更；Terrain V2 density、Macro Geography、Highway、Rural 均未修改。资源处理结果见交付报告；未做新世界/多 seed 实机回归，因此不宣称已目测消除旧区块的沉船或熔岩湖。

## Scorched Lands Surface Water Suppression V1

代码已接入，**未进行新世界实机验收**。目标为 `Scorched natural surface / near-surface water = 0`；这里 near-surface 明确定义为 `y >= preliminarySurfaceLevel(x,z) - 12`，不是任意深度、任意侧向连通洞穴的3D暴露判定。

- 复用 `src/main/java/com/antaurora/apofirstlight/mixin/NoiseChunkScorchedAquiferMixin.java`：构造器RETURN将原Aquifer包装为 `worldgen/aquifer/SurfaceWaterSuppressingAquifer.java`；`NoiseChunk.forChunk` RETURN绑定当前ChunkAccess。只在带AFL Macro Geography density标记的当前Overworld配置启用，不作用Vanilla Nether/End。若未来自定义维度复制整套AFL router，需另行增加维度权限；当前没有对此类未来配置作隔离保证。
- 精确地域条件：`nationId == MAIN_NATION`、`isLand()`、`waterClass == NONE`、`surfaceClass == LAND || COAST`。OPEN_OCEAN、COASTAL_WATER、BAY、STRAIT、INLAND_SEA全部排除；不改海岸线或海床fill。干岸若实际为Beach则不处理。
- 使用已生成的chunk biome，在 `max(preliminarySurfaceLevel, SEA_LEVEL)` 高度确认 `apocalypse_firstlight:scorched_lands`；不是每block重新调用BiomeSource气候搜索。每NoiseChunk有256槽带完整XZ键的列缓存，保存地理、preliminary surface与biome判定。无新增ThreadLocal或全局可变chunk状态。
- 深度沿用原live mixin的12格，不新增/扩大深度。preliminary surface来自Vanilla `NoiseChunk.preliminarySurfaceLevel`（4格XZ量化、当前8格Y步进、initial density > 0.390625），是估计面而非精确最终高度图。轻量边界probe覆盖H=48/56/64/80：H-13保留、H-12开始抑制，移除旧 `y <= H` 上界后H=48/56的Y62积水不再漏过。该probe不是实际地形采样或“最小深度”实机证明；异常无surface估计时保持原状态。
- 仅当原Aquifer返回WATER且满足上述条件时改为其“solid”语义null。NOISE阶段MaterialRuleList继续走默认地层（当前stone及原矿脉规则），随后现有SURFACE规则应用fused_ground/scorched_soil等；不使用WATER→AIR，不手工重写地表材质。
- 同一个包装Aquifer也被后续carver读取：命中时null令Vanilla WorldCarver保留已有方块，防止它重新写入同一近地表水。没有修改carver路径/概率、canyon、noodle、spaghetti、cheese；只过滤其水状态。不把carver阶段的null直接写为空气。
- 低于H-12的deep cave water、deep aquifer与underground water pockets原样委托；全部lava、AIR和其他原状态保持。玩家/桶/命令及结构主动放水不经过此包装，不受影响。当前Scorched biome没有spring_water；不声称拦截未来新增的独立feature或来自范围外的流体传播。
- 原宏观水域 `getInterpolatedState` RETURN补水保持；无ChunkAccess的generator-only高度/列查询不猜测Scorched biome，继续原地形查询。这意味着它们不反映此最终水回填，不能将其当成此策略的最终高度验收。
- `MAIN_NATION Surface Cave Seal` 仍为 **DEFERRED**，未做洞口封闭。LandTerrainBias `.16/.63/.02`、rolling `.30`、InlandElevationBias `.046875`、所有relief/elevation及Macro拓扑均未修改；sea level=63、default fluid=WATER、aquifer开关不改。Highway/Rural/City未改。

验证限于Java编译及上述边界逻辑probe；地表材质、海岸、地下水与浅水洞视觉效果仍等待用户新世界验收。没有runClient、clean、GameTest、新世界/批量chunk生成、benchmark或截图。

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

TXT 对每岛列 `landmassId/landmassRole/nationId`、采样 bounds/width/height/area、`nearestMainlandWaterGap`（支撑岸线几何估计）、`sampledCrossingWaterGap`（不大于2格探针估计）、`bridgeCandidate300to600`。Summary 记录 `satelliteIslandCount`、`minorIslandCount`、`bridgeCandidateCount300to600`、`requiredSatelliteIslandRuleSatisfied` 和 `requiredBridgeCandidateRuleSatisfied`，后两项分别检查1–3座附属岛及至少1个合格桥候选。外洋审计半径通过 `outerOceanRadius()` 根据岛群包络加320格余量推导，不再固定10000格；拒绝不足以覆盖岛群和外围的导出半径，默认12000格仍覆盖现有参数上限。

TXT 记录 seed、采样分辨率、MAIN_NATION/主岛与附属岛 landmassId/role/采样 bounds 和面积、spawn 至最近 OPEN_OCEAN/INLAND_SEA 的采样距离、waterbodyId 与水体分类/覆盖、foreign/non-main-nation land 采样检查、外圈外洋占比、spawn 安全标记、桥候选清单。各项 bounds、距离、面积均是采样估计，不能当作精确海岸测量。桥只为审计标记，不接入 worldgen。实现只调用 `MacroGeography.forSeed(seed).sample(x,z)` 与现有只读规划记录，不读取或生成 chunk、Xaero/DH 地图，也不修改 Terrain V2 参数。

开发环境命令：`/afl dev macro_geography` 查询当前坐标；`/afl dev macro_geography <x> <z>` 查询任意坐标（只读、不加载chunk）。显示sample、基础轴长、附属岛中心与海峡两岸坐标。此命令位于dev包，发布JAR排除；生成日志仍有 `[AFL MACRO GEO]` 摘要。

建议在新建正常世界检查：地堡正常首登；0,0与reserve为干陆；沿岛岸观察beach/ocean与实际水面一致；远处如20000,20000为持续大洋；附属岛岸线缓坡与两岸ID正确；水下有海床及地下洞穴/矿物。旧Highway可能跨海不在此阶段修复。上述均为待用户执行的检查，不是已通过结果。

本轮不运行runClient、clean、GameTest、多seed自动worldgen、大型benchmark或批量生成chunk，不commit/push，不部署发布包。
