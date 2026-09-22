# Sea Bridge V1.1C — 钢缆近距收口与模型/UV 修正

## 1. Root Cause

A. 原 tower socket 位于塔面钢架外侧相邻块，索的中心线到该块边界即停止；钢架实际位于自己方块的中心细带，仍隔约半格。Deck centerline 停在混凝土顶部的外侧块边界，只有极少边缘接触，未埋入单格混凝土 ledge。

B. V1.1B 每格用 16 个轴对齐微盒模拟斜索，每盒保留六个面。真实可见轮廓因此有台阶，不能仅靠 UV 消除。每片又把 u=0…4 映射到很短的沿索方向，重复原贴图中的明暗纵束，变成密集横纹；各 phase 重复同样的逐片 UV。问题为模型几何与 UV 方向/重复共同导致，现有 blockstate y 旋转本身没有发现需要更改的错误。

依据：本轮读取 live SteelCableBlock、六个 slope/phase 模型、原 vertical model、64×64 PNG、blockstate、BridgeCableGeometry、BridgePylonGeometry、PylonZoneGeometry 及 SeaBridgeEngineering 接入点。用户截图说明 V1.1B 大尺度生成成立，同时展示近距问题，不是修正后验收。

## 2. Endpoint Fix

采用同一刚性平移 T = 0.5 × uphill − 0.25 × Y。uphill 是已有 facing。可见模型、近似碰撞、Piece entry/exit 均应用同一 T。

- Deck：起点从原混凝土外缘移动到原锚位方块中心，Y 从 roadY+1 降至 roadY+0.75，中心线进入现有 reinforced_concrete 0.25 格。既有单格 ledge 原样保留。
- Tower：末端向塔内推进 0.5 格，进入现有 steel_brace 所在格的中心结构带；在塔顶 band 则进入现有混凝土。截面仍为原来的 2 model units，与钢架细杆产生小量几何相交。
- 原 Anchor 数据、tower band 选择、deck station、BlockPos、slope 选择、索序、拒绝条件均未改。实际接点移动半格/四分之一格，不重算整套布局。
- 不拉长相邻段、不复制端段、不加落块、不挖掉混凝土。整条索各段统一平移，所以相邻段仍满足 T(exit[n])=T(entry[n+1])；新增模型无重叠的共面长面。
- 模型/shape 可向 facing 超出所属块 0.5 格；仍由原始所属块的 chunk writer 提交。横桥方向厚度未改，仍在 ±14 包络内。
- 此修正含资源几何变化，现有斜索加载新资源也会使用该平移；无需为收口改变索的保存状态。

上述为静态几何依据；与光影、材质包及游戏碰撞的最终效果由用户实机确认。

## 3. Texture / UV Fix

最终方案：model adjustment + UV 修正，继续复用原 PNG。

六个原斜段 model JSON 改为 Forge 原生 `forge:obj` 静态资源入口；每个 OBJ 仅有 8 个顶点与 4 个连续纵向四边面。彻底移除可见的微盒台阶和逐片端盖。各 voxel 之间直接对接，端点开口在桥面/塔体内。没有新增 Java renderer、BER、实体或运行时动态几何系统。

本地 Forge 1.20.1-47.4.22 sources.jar 中 ObjLoader/ObjModel/ObjMaterialLibrary 已核对：model 指向命名空间资源路径、mtllib 相对 OBJ 解析、automatic_culling/shade_quads/flip_v/emissive_ambient 字段与 map_Kd/forge_TintIndex 均为现有 loader 支持。

令 q∈[0,1]，k=run，p=phase：
- 模型中心线 x=q+0.5，y=(p+q)/k−0.25，z=0.5，基础朝向 EAST。
- 横截面 z=7/16…9/16，y=center±1/16，保持细钢缆尺寸。
- 纹理 u 横跨截面，使用原 PNG 第 2.5…10.5 像素色带。
- 纹理 v 沿索推进，取 (32.49+0.02×(p+q)/k)/64；整个 slope 周期保持在原图同一像素行中心附近。保持非退化 UV，phase 接点精确衔接；周期回绕仍在同一 texel 的极小范围内，抑制逐格明暗节。
- flip_v=false，automatic_culling=false，emissive_ambient=false，forge_TintIndex=-1；不把钢缆变成自发光或染色块。
- 原 EAST/SOUTH/WEST/NORTH = y 0/90/180/270 映射和 uvlock 缺省关闭保持不变。两侧与两塔复用相同模型/UV，不引入左右专属贴图或手工反向相位。

VoxelShape 仍允许用 16 个 AABB 近似，但只用于交互/碰撞，不再用这些小盒作为可见模型。shape 与可见索具有相同平移与坡度。

## 4. Sloped Texture

未新增 steel_cable_sloped.png。读取原图确认其深灰纵向束缆色带可以复用；异常主要来自把色带横向压缩重复到每个微盒。新 UV 直接把这些颜色沿整段展开，无需程序化改图或生成新材质。原 steel_cable.png、vertical model、Item 模型均未修改；没有使用图像生成模型。

## 5. Files Changed

本轮新增/更新：

- src/main/java/com/antaurora/apofirstlight/block/SteelCableBlock.java
- src/main/java/com/antaurora/apofirstlight/worldgen/highway/BridgeCableGeometry.java
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r2_0.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r2_1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_0.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_1.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_r3_2.json
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r1.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r2_0.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r2_1.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r3_0.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r3_1.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/r3_2.obj
- src/main/resources/assets/apocalypse_firstlight/models/block/steel_cable_sloped/cable.mtl
- src/main/blockbench/steel_cable_sloped/steel_cable_r1.bbmodel
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_0.bbmodel
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_1.bbmodel
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_0.bbmodel
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_1.bbmodel
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_2.bbmodel
- docs/authoring/afl_industrial_palette_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1_1b.md
- docs/worldgen/highway_v2_sea_bridge_v1_1c.md

被新 Free mesh .bbmodel 替代的旧微盒源（从工作树移除，可从 Git 恢复）：

- src/main/blockbench/steel_cable_sloped/steel_cable_r1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_0.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r2_1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_0.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_1.json
- src/main/blockbench/steel_cable_sloped/steel_cable_r3_2.json

可编辑 source 保存在 src/main/blockbench；runtime OBJ/JSON 为独立文件。Free mesh 源坐标为 Blockbench model units，runtime OBJ 为 block units，换算系数 1/16。原来的竖直 steel_cable 源保留。

工作期间出现的 .obsidian/workspace.json 与 docs/03 - 制作清单.md 改动不属于本轮，未编辑。

## 6. Connector Decision

CONNECTOR_ASSET_REQUIRED = NO

按现有几何，刚性平移即可使 deck 端嵌入原混凝土、tower 端进入既有钢架。未新增连接件、钢块锚点、Registry ID、Item 或 creative entry。

## 7. Validation

本轮只运行一次 compileJava --offline；不执行资源处理、build、check、GameTest、客户端、截图或自动视觉验收。静态检查不等于 Forge 资源加载验收，compileJava 不会验证 OBJ/MTL/UV 的游戏内表现。

```text
TOWER_ENDPOINT_CONNECTED = YES
DECK_ENDPOINT_CONNECTED = YES
STEEL_BLOCK_ANCHOR_MARKERS_REINTRODUCED = NO
SEMI_FAN_LAYOUT_UNCHANGED = YES
SLOPE_STATE_CONTRACT_UNCHANGED = YES
PLAYER_VERTICAL_PLACEMENT_UNCHANGED = YES
SLANTED_UV_FIXED = YES
DEDICATED_SLOPED_TEXTURE = NO
FOUR_DIRECTION_RESOURCE_MAPPING = YES
CONNECTOR_ASSET_REQUIRED = NO
COMPILEJAVA = PASS (single compileJava --offline; BUILD SUCCESSFUL in 43s)
PROCESSRESOURCES = NOT RUN
BUILD = NOT RUN
GAMETEST = NOT RUN
RUNCLIENT = NOT RUN
WORLD GENERATED = NO
RESOURCE_VISUAL_QA = USER REQUIRED
COMMIT = NO
PUSH = NO
```

YES 表示已实现且有静态几何依据；不表示本轮完成了实机连接、碰撞或视觉验收。SLOPE_STATE_CONTRACT_UNCHANGED 指 enum、facing、rise/run、phase 转移和连续规则不变，局部中心线偏移为本轮明确的收口修正。

玩家仍只有原 steel_cable Item，正常放置仍 vertical，斜索掉普通 steel_cable；未修改采掘标签或 loot，未做生存采掘测试。

DOCUMENTATION:

DOCS UPDATED = YES

Updated:
- docs/authoring/afl_industrial_palette_v1.md
- docs/worldgen/highway_v2_sea_bridge_v1_1b.md
- docs/worldgen/highway_v2_sea_bridge_v1_1c.md
