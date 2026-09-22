# Sea Bridge V1.1B — Steel Cable 与 Semi-Fan

## 1. Steel Cable State Design

同一个 Registry ID/Item：apocalypse_firstlight:steel_cable。
Properties：facing=north/south/east/west（指向上坡），segment=vertical/r1/r2_0/r2_1/r3_0/r3_1/r3_2。共 28 状态、6 个斜段基础模型，朝向复用 y 旋转。
默认 facing=north,segment=vertical。Segment 合并 slope 与 phase，避免无效乘积状态。rotate/mirror 只变换上坡方向。旧无属性 BlockState 使用默认竖直，不需要另注册或 Item NBT 迁移。

## 2. Segment Geometry

令 q 为沿上坡方向的水平局部坐标，0≤q≤1；k 是每升高一格的水平距离，p 是相位。每格中心线 y=(p+q)/k。

| segment | rise/run | entry (q,y) | exit (q,y) |
| --- | --- | --- | --- |
| r1 | 1/1 | (0,0) | (1,1) |
| r2_0 | 1/2 | (0,0) | (1,1/2) |
| r2_1 | 1/2 | (0,1/2) | (1,1) |
| r3_0 | 1/3 | (0,0) | (1,1/3) |
| r3_1 | 1/3 | (0,1/3) | (1,2/3) |
| r3_2 | 1/3 | (0,2/3) | (1,1) |

相位每格递增，p=k−1 后下一格上移一格并回到 p=0。规划从 deck 向 tower 排序，逐段比较世界 exit/entry，平方误差阈值 1e−16。没有 Bresenham 后随意赋角度。

普通 JSON 不支持任意有理坡度的整块旋转，因此使用每格 16 个连叠细 AABB 近似直线：第 i 片水平范围 [i,i+1] model units，高度 [(p+i/16)*16/k−1,(p+(i+1)/16)*16/k+1]，横向 [7,9]。相邻片相交，跨格截面也重合，不留空气缝隙。纵向外缘有最多 1 model unit 的细阶梯，不宣称解析光滑圆索或已完成近景视觉验收。无 cullface，避免非满格相邻索段误剔除。

VoxelShape 与模型采用相同 16 片公式，按方向变换并缓存；不是整格碰撞柱。模型 y 可略伸入相邻格（−1…17 units），端点接触部位允许细微嵌入。材质沿用 steel_cable.png，UV 随每格连续片序推进，不使用 uvlock。

模型资源为 steel_cable_r1、steel_cable_r2_0、steel_cable_r2_1、steel_cable_r3_0、steel_cable_r3_1、steel_cable_r3_2。

## 3. Player Placement

getStateForPlacement 无条件返回默认竖直。点击面、朝向、潜行不会启用桥索模式。原 block item、creative entry、loot 未改；挖斜索掉普通 steel_cable，重放竖直。
静态读取确认 requiresCorrectToolForDrops、mineable/pickaxe、needs_diamond_tool 及普通自身 loot 保留。未进行生存挖掘实测。

## 4. BridgeCableGeometry

输入：SeaBridgeGeometry（局部轴/bounds）、HighwayProfile（deckY）、已验证的 BridgePylonGeometry（双塔、baseY、144 个原始 Anchor）。
输出：不可变 Cable 列表，含 pylon/side/mainSpan/index/run、实际 tower/deck Vec3 与有序 Piece（BlockPos、BlockState、entry、exit）；拒绝原因、实际 bounds、坡度分布通过 description 输出。

实际索段从原 deck socket 的下外侧面进入，水平走到 tower socket 的内侧面。原 socket 是候选方块坐标，实际端点位于块面而非强制块中心。保持 deck station 不动，选择 k=1/2/3 后实际 tower 高度为 deckY+distance/k。允许对齐到任一既有预留高度 band 的 ±3 格范围；选择最接近原 tower socket 的合法解，且外侧索塔端不得低于此前索。不改变塔几何或原始锚点表。

拒绝：没有合法 band、端点不连续、越界、与塔/平台/此前钢缆占同一格、进入道路高度。左右索成对接收/拒绝，绝不写半根路径。位置去重保守地拒绝同格共享，不依赖邻居更新。高度 guard 当前按项目 Overworld −64…320，保持索最高块有额外余量。

连续性在生产规划中检查；未执行本轮自动测试。未创建 connector；塔端与原混凝土凹槽之间仍可能有轻微可见连接空隙，需用户观察，CONNECTOR_ASSET_REQUIRED = UNDETERMINED。

## 5. Semi-Fan Layout

每塔每侧目标主跨 6 根、背跨 4 根；两塔双侧总目标 40 根。实际数允许因合法性拒绝减少，diagnose 给出数量与原因。未声称所有地形都生成满额。
主跨原 anchor indices = 0,2,3,4,6,8，deck 距塔 = 24,48,60,72,96,120。
背跨 indices = 0,3,5,8，距离 = 24,60,84,120。
原 tower 高度表 = D+[24,28,32,36,40,44,56,59,62]；实际高度按上节离散化。
索面固定 L=±13；塔 A 的正 station 方向为主跨，塔 B 的负方向为主跨。两塔采用镜像索序，绝对高度服从既有纵坡，坡度组合不强制相同。多高度/多距离形成有限坡度 Semi-Fan，未用单一 Harp 排布。

## 6. Sea Bridge Integration

SeaBridgeEngineering.plan 在 Landmark 和既有 profile 准备后调用 BridgeCableGeometry.plan 并保存结果；render 先执行原道路/基础/主塔，再 cables.render。Landmark 禁用时得到空索计划，不改变 V1 fallback。
landmarkDescription 增加 cableEnabled、cableCount、cableBlockCount、cableSlopeDistribution、rejectedCableCount/reason、cableBounds、连续性契约；HighwayLiveGenerationDiagnostic 同步 ownedCableCells/wouldRender。离线 export 未新增真实索数量，不能在没有 profile/基础时伪造结果。

## 7. Chunk Safety

同 crossing 规划一次完整不可变索序；无世界或 chunk 引用。经既有 FiniteRouteHighwayWriter 与传入的 chunk writer，只在 owns(pos) 时 set。不建立新 writer/ownership，不读取邻 chunk。L=±13 的 2-unit 横截面仍在 ±14 包络内；原 claim 不变。
只影响新生成区域；既有世界桥不会自动升级，需同 seed 新世界或未生成区域。没有执行世界生成或存档修改。

## 8. Resource Changes

一个原 blockstate、6 个新增 runtime model、6 个 Blockbench 可导入 Java model 源（src/main/blockbench/steel_cable_sloped）；源和 runtime 为不同文件。原 vertical 源和模型保留，原 PNG、loot、Item、creative、采掘标签均不改。新 Java SteelCableBlock 替换同 ID 的普通 block factory。

## 9. Files Changed

- src/main/java/com/antaurora/apofirstlight/block/SteelCableBlock.java
- src/main/java/com/antaurora/apofirstlight/registry/AflBlocks.java
- src/main/java/com/antaurora/apofirstlight/worldgen/highway/BridgeCableGeometry.java
- src/main/java/com/antaurora/apofirstlight/worldgen/highway/SeaBridgeEngineering.java
- src/main/java/com/antaurora/apofirstlight/worldgen/highway/HighwayLiveGenerationDiagnostic.java
- src/main/resources/assets/apocalypse_firstlight/blockstates/steel_cable.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r2_0.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_0.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r2_1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_0.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_0.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_2.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_2.json
- docs/authoring/afl_industrial_palette_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1_1a.md
- docs/worldgen/highway_v2_sea_bridge_v1_1b.md

## 10. Validation

本轮仅允许一次 compileJava --offline。所有其他测试、资源处理、打包和客户端操作均不运行；编译不验证资源加载或视觉。

```text
STEEL_CABLE_SINGLE_ITEM = YES
PLAYER_PLACEMENT_VERTICAL = YES
SLOPED_STATES_IMPLEMENTED = YES
SEGMENT_CONTINUITY_CONTRACT = YES
BRIDGE_CABLE_GEOMETRY = YES
SEMI_FAN_MAIN_SPAN = YES
SEMI_FAN_BACK_SPAN = YES
LEFT_RIGHT_CABLE_PLANES = YES
FOUR_AXIS_SUPPORT = YES
CHUNK_OWNED_WRITING = YES
WITHIN ±14 ENVELOPE = YES
STEEL_BLOCK_ANCHOR_MARKERS_REINTRODUCED = NO
NEW_CABLE_ITEM = NO
CONNECTOR_ASSET_CREATED = NO
COMPILEJAVA = PASS (single compileJava --offline; BUILD SUCCESSFUL in 42s)
PROCESSRESOURCES = NOT RUN
BUILD = NOT RUN
GAMETEST = NOT RUN
RUNCLIENT = NOT RUN
WORLD GENERATED = NO
VISUAL VERIFIED = NO
COMMIT = NO
PUSH = NO
```

YES 指实现契约，不表示实机或回归测试通过。

Seed：-4332662446239654818。A = X−2592/Z−7452，B = X−2592/Z−7196，中心 X−2592/Z−7324。实际塔基 Y 由既有 profile 决定，塔顶为 D+64；没有本轮实测 Y。
观察命令：/tp @s -2592 180 -7452；/tp @s -2592 180 -7196；/tp @s -2472 170 -7324。仅提供给用户，不执行。

DOCUMENTATION:

DOCS UPDATED = YES

Updated:
- docs/authoring/afl_industrial_palette_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1_1a.md
- docs/worldgen/highway_v2_sea_bridge_v1_1b.md
