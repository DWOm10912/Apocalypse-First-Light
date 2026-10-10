# 哥伦比亚联邦 Terrain V2：迁移与分阶段实施计划

日期：2026-10-07。状态：**Phase 0 IMPLEMENTED / PENDING USER VALIDATION；Phase 1～8 PLANNED / NOT IMPLEMENTED**。原研究阶段只有源码审查、外部研究和文档变更；后续已实现[Phase 0 只读诊断](terrain_v2_phase0_diagnostics_and_baseline.md)，没有切换生成算法或资源，没有实机验收。

**2026-10-10 第三次更新**：用户 Phase 2 初步实机验收 PASS。**Phase 2b（河流水体 + 湿地水塘 + 稳定层硬底与熔岩湖门控 + 自动环境辐射停用）已实现**，见 [Terrain V2 Phase 2b](terrain_v2_phase2b_rivers_v1.md)，离线验证与编译通过，等实机验收；Phase 3 的稳定层由此成为"平原硬底、山区软层"。生态与群系阶段在 Phase 2b 实机验收之后。

**2026-10-10 第二次更新**：用户验收 r1 规划 PASS。旧世界生成逻辑已退役（[旧世界生成逻辑退役 V1](legacy_worldgen_retirement_v1.md)）；**Phase 2（LAND 高程 + 近地表稳定层 + 统一水面掩码）已实现**，见 [Terrain V2 Phase 2](terrain_v2_phase2_generation_v1.md)，等用户在全新世界实机验收；Phase 3 的稳定层在这一步一起做了第一版（密度侧 + 雕刻器侧）。Phase 4 群系重构要等 Phase 2 实机验收之后。

**2026-10-10 更新（Claude 接手）**：Phase 1 已按真实地貌重做，见 [Terrain V2 真实地貌研究与规划 r1](terrain_v2_real_terrain_research_v1.md)。用户批准下载 USGS 3DEP 高程与 NLCD 土地覆盖，对 6 块美国样方（OH / IN / IL 冰碛平原、PA 山脊谷地与山前、VA 海岸平原）做了定量统计；新的离线规划器 `TerrainPlanV2`（`tools/terrain-v2-research/`，不进 mod）先规划水系、再塑造地形，参数冻结为 `afl_terrain_plan_v2_r1`，**等用户看图验收**。Codex 的 Phase 1 分支 `codex/terrain-v2-phase1` 不合并：它的确定性、海陆掩码、有界查询和适宜性思路被保留，地貌公式（三正弦平原、双高斯山、三条平行河）被替换。下面 Phase 2–8 的边界仍然有效，Phase 2 的接入方案见新文档 K 节；验收前不动正式密度链和群系。

设计依据：[Terrain V2 研究报告](columbian_federation_terrain_v2_research.md)。参数均为 **REFERENCE TARGET**；当前生效值仍以仓库 Java/JSON 为准。研究报告中“V2”不是宣布 `MacroGeography.VERSION=3` 已升级或新世界 profile 已接通。

问题基线：**V1-B 当前用户实机验收失败**，不是仅待测试。本迁移方案由该失败和连续可建设地形不足驱动；保留既有实现以便精确诊断/复验，不把保留代码等同于认可当前运行效果。Terrain V2 是否解决各类失败需后续重新验收。

## 1. 冻结边界

保留原版 `NoiseBasedChunkGenerator`。重构对象是 LAND 地貌/密度/浅地下和生态分布，不是世界生成框架、城市道路施工器或 Highway 的全面重写。

保留：主岛＋三卫星岛 topology、角色、岸线/海湾/内海、landmass/waterbody 身份；Highway 路由与工程、SpatialClaim；Road V1-A/V1-B；StructureTransform/StructureSocket/整数 NBT 锚点；G/S 高度契约、1/16 道路高程；地堡独立启动流程。

Road 横断面保持 R12/C14/I12 沥青宽 12/14/12、完整 ROW 22/26/24。G 为整数建筑行走面，S=G−3/16。地形重构不借机修复 authoring SOUTH、油罐旋转、structure_void 或其他独立建筑系统，不恢复旧 Rural。

高度选择为 **B：普通内容与 Terrain V2 足够，未来超高层可能不足；当前保持 minY=-64 / height=384 / maxYExclusive=320**。低地 Y72–104、峰顶通常 Y145–220 是参考目标。只有正式内容明确持续超出建筑净空或地貌需求时再开独立扩高任务；不在本轮顺带改维度。

## 2. 当前类与资源的处理清单

Java 根目录：`src/main/java/com/antaurora/apofirstlight/`；资源根目录：`src/main/resources/`。此表指**未来实施动作**，本轮没有修改或删除它们。

| 类 / 资源 / 系统 | 决策 | 下一阶段应做什么与禁止什么 |
| --- | --- | --- |
| `worldgen/geography/MacroGeography`、`MacroGeographySample` | 保留拓扑，扩展组合入口 | 不改岸线 seed 规则；明确历史 LAND surfaceHeight 不是最终高程，新增上下文不得改变旧字段语义 |
| `LandTerrainRelief`、`LandTerrainBias`、`InlandElevationBias` | 重构 LAND 高度职责 | 从 C/E 参数倾向转为可约束的宏观基底/细节；避免新 H 加旧 +6 elevation 再重复抬高。保留旧链作基线，切换通过后退役旧特定算法，不长期并跑两套生产地形 |
| `MacroTerrainDensity`、`MacroHeightDensity` | 保留陆海/海床职责，审计接缝 | 对接新 LAND，保持 WATER/海平面；海床保护不得误扩成固定高度陆地平台 |
| `mixin/RandomStateSeedMixin` | 保留必要种子绑定，扩展被绑定 recipe | 在 vanilla noise wiring 前完成 immutable context，禁止初始化期间调用完整 generator 高度反向递归 |
| `NoiseChunkMacroWaterMixin`、`NoiseBasedChunkGeneratorAquiferMixin`、MacroAquiferContext | 保留现行海水边界 | 新河流另加共享 water field，不能把只支持宏观海域的逻辑宣称支持内陆河流 |
| `data/minecraft/worldgen/noise_settings/overworld.json` | 后续版本化改 LAND 图 | 保留海平面/维度高度；记录实际 datapack 资源摘要，检查 initial/final 双链 |
| `terrain/macro_sloped_cheese.json`、`macro_initial_density` 对应链、`terrain/macro_caves.json` | 后续随新 recipe 修改 | 明确基础 H、坡度与洞穴职责；不要仅换一个倍率并称完成 V2 |
| `terrain/base_3d_noise_scaled.json`、`terrain/surface_relief.json` | 待退役候选 | 当前不是主 LAND 活跃链；实施时全引用检查后再清理，禁止本轮删除或据文件名宣称有效 |
| Vanilla configured cave / canyon 与 Fallout carver 配置 | 有边界扩展 | 同一地表稳定场同时约束 noise cave 和逐方块 carver；保留深层洞穴，不关闭全部 carving |
| `world/biome/MainNationBiomeRegionPlan` | 替换全国默认 Fallout 策略 | 保留出生生态安全需求；不继续用少量小 Plains islands 代表正常国家 |
| `AflVanillaBiomePolicy`、`OverworldBiomeBuilderMixin`、`MultiNoiseBiomeSourceMixin`、`AflOverworldRegion` | 同步修改生态准入/兜底 | 只调 TerraBlender 权重不够；最终返回与候选列表保持一致 |
| `StartupEcologyState`、`ClimateParameterListMixin`、`StartupSurfaceBiomeContext`、两处 startup surface Mixin | 保留“surface 与 biome 同源”原则，扩展映射 | 取消仅五个固定 holder 的设计局限，保留地下生态与温度限制；避免又造第二套 surface 分区 |
| Fallout Barrens biome、DegradedGroundPatchFeature、EnvironmentalParticleProfile | 保留能力，改触发政策 | 灾难覆盖选择土壤/植被/粉尘；不默认全国荒原。实际 feature 接入和扩散预算另验 |
| `radiation/BiomeRadiationResolver` | 更换环境 profile 来源 | 保留 current-loaded/unknown 边界；缓存 key 需覆盖世界及 profile，不能用旧 biome 强耦合缓存 |
| `RadiationField`、RadiationManager、safe anchor、剂量/遮蔽/物品辐射 | 保留 | 只适配环境输入；不在 Terrain V2 顺便重平衡游戏数值 |
| `worldgen/terrain/TerrainQuery`、TerrainSample/TerrainSource/TerrainHeights | 保留并增量扩展 | 高度仍为地表上方第一格；macro reference 不冒充真实表面；UNKNOWN 不冒充安全 |
| `worldgen/roads/RoadTerrainQuery` | 适配宏观上下文及来源标记 | 已加载/未加载估算差异写明；不强加载，不降低施工安全规则 |
| `roads/construction/RoadConstructionPlanner` 与施工/台账 | 保留现有核心 | 地形变化后重验 32 格、跨 chunk、保存恢复；发现自身 bug 另列证据/任务，不因 terrain 实施顺带改工程规则 |
| HighwayRouteGraph、SatelliteHighwayRouting、HighwaySpatialClaimProvider | 保留路由、角色与占地 | 同 topology 尽可能保持二维计划；terrain 变化不等于 bridge endpoint 永远不变 |
| HighwayTerrainSampler、CorridorEngineeringSegment、SeaBridgeEngineering、NaturalHighwayCacheManager | 保留工程，增加版本识别/重新验收 | 更新 height backend 身份；重新算桥头和基础；不带入旧工程缓存，不降低既有资格门 |
| StructureTransform、StructureSocket、StructureDefinition、SpatialClaim | 保留 | 资产实际朝向/占地/入口仍按正式道路规格；没有新 NBT 也可开发地形 |
| WorldgenProfile、ProfileCompatibility | 复用已存在的值契约，补生命周期 | 当前并非已经接通的世界级冻结；新世界首次生成前固定版本/资源身份，重开世界校验 |
| `src/dev/.../TerrainV2Diagnostics`、TerrainSuitabilitySampler | 校准和扩展诊断 | 移除对不存在的 `terrain/macro_relief` 的成功假设；不能沿用过时 0.06/0.075/2÷268 校准或把粗起伏阈值当道路纵坡 |

## 3. 接口草案：一套查询、分清预测和事实

以下名称待实现阶段确定，不代表新类已经存在。

1. `LandformPlan`：纯 seed、profile、topology 派生的不可变连续地貌图元；不引用 Level，不加载 chunk。低地/山带/谷地数量有明确上限、失败有记录，不无限重抽。
2. `TerrainMacroContext`：组合 topology、landform、ecology、disaster 的只读入口，返回带版本的宏观样本；可由现有 MacroGeography 所在模块提供，不创建另一套公开地形查询服务。
3. 扩展 `TerrainQuery`：保留现有函数式 `sample`，优先新增 default 宏观查询或组合适配方法；现有 lambda/调用者不必同时重写。宏观样本含 referenceElevation、lowlandId、slopeWindow、roughness、valley/water/mountain、predictedCover、suitability 和版本身份。
4. `TerrainSample` 继续只表示声明来源的列观测。`UNKNOWN` 所有 optional 清空，`NONE` 不是已证明无水；`NOISE_PRE_DECORATION` 的 protection 仍为 UNKNOWN。
5. `EcologyPlan` 和 `DisasterPlan` 输出分别独立。最终 biome/surface 与 Radiation 使用同一计划身份；禁止先切生态再把其他 biome 的 UNKNOWN 当 SAFE。

接口所需三种证据要并存：`MACRO_PREDICTED`（区域政策）、`NOISE_ESTIMATED`（生成器列查询）、`ACTUAL_SNAPSHOT`（允许区域实际方块）。名称可调整，来源区别不可省略。施工与保护只接受它们能证明的内容；宏观稳定层承诺仍需实际检验。

## 4. 高度场与洞穴的实施约束

建议从连续目标高程 H(x,z) 与地貌权重出发，先构建受控低频基底，再附加有幅度/波长/导数边界的细节。陆地 density 以相对 H 的深度为核心；低地削弱造成表面多次穿零的三维形态，山地允许更丰富的体积细节。不要用全国固定 Y 的密度下限制造平板。

initial density 和 final density 的职责可以不同，但必须共享高程基底、岸线/水系和版本；需要验证 getBaseHeight/getBaseColumn 与实际 NOISE 阶段一致，而不是要求它们包含后续 carver/features。水域边界、水面和河床必须由一致上下文驱动。

稳定层按到当地地表深度过渡。推荐低地 8–12 格实体覆盖、一般 6–8，24–32 格以下逐步回到完整洞穴能力。岩体与 3–5 格参考土层分离。noise-cave 与 carver 使用同一限制；carver 只看起点不够，必须防止从允许区挖进稳定区。

优先评估受控 configured carver 扩展。若确实需要 Mixin，限定 carveBlock 级决策及支持维度/context，确认 bulk carving 不绕开检查；不能仅加入一个全局 Y 门槛。API 可行性与 aquifer 副作用属于 Phase 3 的实施前检查，本报告没有声称 wrapper 已完成。

## 5. 世界、缓存与保存迁移

建议 **新世界专用切换**。Early Alpha 不维护为了旧开发世界而并存的两套长期地形；旧世界保留文件作为参考，另备旧版本运行。新构建遇到无法证明一致的旧世界 profile 时提示不兼容或拒绝新生成，不自动补写“已升级”标志。

冻结身份至少包括 topology、landform/terrain、ecology、disaster、有效生成资源摘要，以及已有 infrastructure/settlement 的版本映射。不要把世界种子相同等同于地形相同。当前 WorldgenProfile/ProfileCompatibility 是复用基础，实际保存/初次生成门尚待接入。

| 对象 | 迁移政策 |
| --- | --- |
| 已生成旧 chunk | 不重写、不裁剪；不能在其外继续新 terrain 后称无缝兼容 |
| 新旧 chunk 边界 | 不承诺自动 blending 能解决 AFL 规则变更；正式验收使用全新世界 |
| Highway | 没有“一份总计划存档改版本即可”的保证；路由可按同 seed/topology 重建，但缓存/工程结果/桥头必须按新 terrain 重算；已放置旧 Highway 不迁到新地面 |
| Road V1-B 施工记录 | 保留格式和旧世界记录；旧精确 before/after、guard 与新地形不兼容。缺 terrain 身份的旧计划不能自动 resume，禁止用新版本号包装旧快照 |
| 地堡 | 新世界走现行独立生成流程，在新高度/洞穴/生态下重新检查入口、埋设、水与出生保护；旧地堡实体与状态留在旧世界 |
| Structure references | 旧 chunk 的引用留在旧世界，不拷贝到新世界、不按新高度重放旧 NBT |
| NBT / registry / unrelated data | 不因 terrain 切换删除资产 ID、物品、模型或非地形数据格式 |
| 缓存 | 新上下文使用 seed+profile+版本，地形列缓存保留世界/generator/random-state 身份；退出世界清理，禁止只靠全局 current seed |

回退规则：代码/资源可以按阶段 commit 回退；**已经生成的新 chunk 不能通过代码回退自动复原**。每个地形版本使用新的验收世界或明确备份，不在同一世界混合试错。

## 6. 分阶段开发与验收

“需要 Ultra/Astra”表示适合由高推理架构审查承担的任务范围建议，不是模型性能承诺。每阶段单独授权、实现、同步文档并停止；不能把本研究视为一次性实施所有阶段的授权。

| 阶段 | 修改范围 / 输出 | 风险与审查需求 | 用户实机验收点 | 独立回退边界 |
| --- | --- | --- | --- | --- |
| Phase 0 基准与身份（IMPLEMENTED / PENDING USER VALIDATION） | inspect/subsurface/survey/benchmark、绑定 density 节点、选定资源/代码指纹、Road 失败坐标与两处 dev fixture 已实现。没有安装世界 profile 锁；未切换生产地形 | 只读；来源/分母/预算详见 Phase 0 文档。全国 stratified benchmark 未实现 | 用户在原失败世界运行固定样本、区域基准；运行数据和性能尚未验证 | 诊断可独立撤回；保留采样报告，不影响正式地形 |
| Phase 1 宏观地貌 | LandformPlan、低地/山带/谷地权重和纯 seed 地图；组合宏观上下文及版本设计，不激活新 density | 中高；建议 Ultra/Astra 审查确定性、有界搜索、依赖环 | 先验地图和候选包络；真实世界暂不应变化 | 可独立撤回新计划/预览，无存档地形影响 |
| Phase 2 LAND 高程 | 新 recipe + 共享 initial/final 高程基底 + 近岸接缝；接通首次生成前 profile 冻结门。保持海平面和世界高度 | 高；建议 Ultra/Astra | 全新世界检查 1024/2048 低地、32/64/256 起伏、海岸断层、卫星岛/地堡；确认非固定平台 | 代码可退，测试世界重建；不能沿用已经生成区块 |
| Phase 3 地下稳定性 | noise caves 与 carver 双路径、少量入口区、稳定岩体与水的边界；深层功能保留 | 高；建议 Ultra/Astra | 开地面/地下剖面，检验 road void 拒绝减少的原因；深洞、矿、aquifer、山坡入口仍存在 | 与 Phase 2 的版本分开，可退到 Phase 2 新世界；不要将旧已挖空 chunk 当验证 |
| Phase 4 温带生态 | whitelist、holder/resolver、surface 同源、地下生态、温度/冻结、structure/lava hygiene tags；同时明确正常生态的环境 profile 来源过渡 | 高；建议 Ultra/Astra 复核跨系统合同 | 森林/平原/湿地/岩岸连续，无寒带化；辐射不会因 UNKNOWN 静默变化，出生保护正常 | biome 与对应环境 profile 适配一起回退；新世界重新验收 |
| Phase 5 灾难覆盖 | 独立环境覆盖替代默认 Fallout；先辐射/植被/土壤/粉尘，不要求本阶段实现 crater | 中高；接口审查用 Ultra/Astra，有限配方可常规开发 | 同类地貌可有正常/污染状态；Forest 可有辐射，Fallout 可平缓；safe anchors、天气与剂量不回归 | 覆盖与 profile 来源成套回退；不逆改已生成 feature |
| Phase 6 Road / Highway 适配与复验 | 扩展已有 TerrainQuery；缓存/profile 身份；固定路线工程复验；明确 estimated/verified。保留道路/桥梁算法 | 高；建议 Ultra/Astra 复核高度与恢复语义 | R12/C14/I12、路口、32 格及跨 chunk 施工；Highway 海桥桥端与基础；拒绝原因可追溯 | 适配器可独立回退，精确施工快照不得跨版本恢复 |
| Phase 7 城市候选 | 从地貌场提取大中小/工业/港口候选，连通可用面积、占地与入口资格；不生成城市 | 中；核心评分可常规实现，整体复核用 Ultra/Astra | 地图候选和代表性步行观察吻合；不依赖 Claude 已完成全部 NBT | 只读候选可独立退，不清理既有道路 |
| Phase 8 回归与性能 | 多 seed / chunk 顺序 / profile 重开 / 有界缓存；与基线比较分阶段耗时 | 高；建议 Ultra/Astra 审查报告，执行需另行授权 | 用户确认国家观感、道路施工、出生流程、洞穴探索和性能；失败项保持明确 | 按失败阶段回退，新世界重验；不靠降低标准换 PASS |

真实河流/河口施工可在 Phase 2 后以独立子任务接入，必须先锁定有向骨架、水位与 valley field；若本轮目标只需低地和道路成功率，可先交付谷地而不生成河水。局部 crater、季节、完整城市调度、高速复杂改线/桥隧均不抢入第一轮。

Phase 4 的过渡必须显式：可在保持现有剂量公式的前提下，提供临时固定、可版本化的正常/灾难环境 profile 映射，随后由 Phase 5 替换其规划来源；不能把“所有新增 biome 都当 Plains SAFE”作为暗中兼容层，也不能将该临时映射写成灾难层已完成。

## 7. 最小诊断与验收协议（均尚未执行）

### 7.1 固定样本与公平比较

- 基准种子至少包含现有失败世界 seed；其他种子用预先固定列表，不只挑容易过的世界。先小样本，再在独立授权下扩至例如 8 个固定 seed。
- 每 seed 先地图采样，再仅生成明确的小测试区；不能为了全国统计在服务器热路径强加载大量 chunk。
- 采样报告必须含 seed、坐标、dimension、topology/terrain/ecology/disaster/profile/resource digest、chunk status、采样 backend、道路方向/长度/断面/配置。
- 按生成为 NOISE 后、carver 后、features 后、已加载实际快照区分高度/空洞。不能将 carver 后的空洞都归到 base noise。
- 每个失败列记录 ground、void 的相对深度/连通范围、fluid、材料和 raster 类型；只记录有界摘要/抽样，不每帧刷日志。

### 7.2 高度与地貌

记录主岛实际岸线包围盒与面积、干陆分类占比；低地包络的 32/64/256/1024/2048 尺度 relief、去趋势 RMS、局部坡度分位数、水比例与最大连通可用区。参考阈值见研究报告，不用单一“平均坡度”盖过断崖/小空洞。

量化高度预算时用 `maxBuildHeight` 排他上界，验证真实建筑 NBT 上下界而非楼层名。测试 G72/80/96/104 的代表高度包络即可，不需要提前制作摩天楼；上界是319，不能用 Y320 作为最后一层。

### 7.3 道路

Survey/Preview 同坐标、同方向、同长度、同加载状态、同版本逐项对比；先 32 格 R12，再 C14/I12 和路口。分别报告宏观推荐、noise estimate、actual preview、prepare、实际施工成功，后者不能由前者替代。

拒绝率至少拆分 void、slope、cut/fill、water、tree、protection、unknown。森林恢复后 tree 拒绝可能增加，不能把“低地无树样本通过率”宣传成全国所有样本通过率。不以放宽 tree/protection/void 门槛凑地形 PASS。

### 7.4 Highway 与出生流程

保留 topology 时比对路线端点、landmass ID、桥接候选；分别比较新的工程 Y、桥端、支撑和 failure 状态。跨海失败不可通过减少报错隐藏。地堡检查首登、入口朝向/埋设、空腔进水、出生保护和 biome 兜底。

### 7.5 性能与确定性

相同 seed/profile 的宏观场与按 chunk 不同访问顺序输出必须一致。基线与新版本分别记录宏观查询开销、noise/carver 阶段耗时、cache 命中/容量、内存及重开世界行为。可先以相同机器同样本 P95 生成时间不恶化超过约 15–20% 作为调查线（REFERENCE TARGET，不是本轮 benchmark），超出时定位热路径而非盲目放大缓存。

禁止每次密度采样遍历全部城市候选、扫描几百 chunk、强加载或无界搜索。所有生成期算法必须有明确常数/窗口上限和失败返回。

## 8. 第一轮正式实施建议

原研究建议优先 Phase 0；现在其代码已完成，下一步为用户实机验证，Phase 1 接口和生产 density 切换须另开任务。原完成条件与当前边界如下：

1. 核对并替换诊断工具过时资源假设，输出当前活跃链和来源身份。
2. 对用户提供的失败道路样本建立一份最小可复查报告：真实 void/坡度/约束位置，分别列 estimate 与 actual。
3. 固定 baseline seed/坐标列表及指标格式，记录当前状态，不要求 baseline 达标。
4. 明确新 profile 的持久化/首次生成冻结接入点，禁止旧世界静默升级。
5. 更新本计划的“已实现/未实现/需实机验证”；通过该阶段验收后才进入 Phase 1/2。

如果下一轮没有用户世界数据可用，仍可完成静态诊断工具和纯 seed 宏观预览；实际洞穴归因保留 UNCONFIRMED，不重复索要不影响独立工作的资料，也不启动客户端替用户生成世界。

## 9. 本轮交付状态

- 当前代码链、官方现实参考、目标参数、世界高度容量及迁移边界：已审查并文档化。
- Phase 0 诊断工具已实现，编译记录见[诊断实施文档](terrain_v2_phase0_diagnostics_and_baseline.md)；实际 terrain_v1_baseline.json 由用户运行命令生成，不把实现完成当成数据已采集。
- 新 LandformPlan / 新高度 / 稳定层 / 生态 / 灾难层 / 世界 profile 生命周期：均未实施。
- 原研究未执行 DEM 样方统计、编译或运行测试；Phase 0 仅允许离线 compileJava。客户端、新世界、世界生成测试、性能 benchmark 仍未执行。
- V1-B 当前用户实机验收失败；静态审查确认 Survey VERIFIED 不包含 Preview 玩家站位门，未确认几何或正式预检不一致。331,-762 的 STEP_OUTSIDE 属站位拒绝；其它失败及322,-812的具体空洞来源仍需原世界证据。地形改造后必须重新验收，V1-C 暂停。
- 后续必须用全新世界复验；本报告不授予自动施工、清理旧世界、提交或推送权限。
