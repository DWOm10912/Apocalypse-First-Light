# Beverage Cooler V2（饮料冷柜 V2：Mesh + PBR）

状态（2026-10-01）：**模型和渲染已重做，用户实机确认外观没问题**。用户开着光影截了图：两个朝向，玻璃可见，金属丝隔板的镂空正常，打开的门也正常。`compileJava --offline` 一次 PASS（包括 `src/dev`），GameTest 按规则没有运行。

V2 只换了模型和渲染。门的逻辑、四格结构、碰撞和选中形状、开门点击检测、挖掘规则都沿用 V1（见下面"沿用 V1 的运行逻辑"）。**仍然没有物品栏，也不能摆放东西**：摆放功能已经商定（见"计划"），还没做。

V2 取代 V1 文档（原 `docs/beverage_cooler_model.md`，已删除，仍然有效的内容并入本文）。下面这些 V1 文件已删除（都在 git 里）：
- 方块源模型 `src/main/blockbench/afl_beverage_cooler.bbmodel` 及其贴图、备份、5 张预览图；
- 构建 / 烘焙脚本 `tools/build-beverage-cooler.bb.js`、`tools/bake-beverage-cooler-runtime.js`；
- GeckoLib 动画 `animations/beverage_cooler.animation.json` 和贴图 `textures/entity/beverage_cooler.png`；
- 渲染类 `BeverageCoolerRenderer`、`BeverageCoolerModel`、`BeverageCoolerItemRenderer`。

## 模型

| 项目 | 值 |
|---|---|
| 生成器 | `tools/build-beverage-cooler-v2.mjs`（`--check` 校验成品是否最新，`--preview DIR` 只输出 geo、sidecar 和贴图） |
| 成品 | **1544 个三角面**（body 1152、每扇门 196），23 个 Mesh part，没有共面重叠。V1 是 452 个方块（约 2700 个四边形），其中 5 层金属丝隔板就占 295 个方块 |
| 贴图 | 512 atlas，`textures/block/beverage_cooler{,_s,_n}.png`。左上 448×448 是 2 texel/px 的常规展开；底部一条 508×16 是金属丝隔板的镂空图案，5 层隔板的上下两面共用 |
| 可编辑源 | `src/main/blockbench/beverage_cooler.bbmodel`（Free Model，组 `body`、`left_door`、`right_door`）和 `src/main/blockbench/textures/beverage_cooler{,_s,_n}.png` |
| 运行时 | `geo/beverage_cooler.geo.json`（只有骨骼，覆盖了 V1 的 GeckoLib geo）、`meshes/beverage_cooler.aflmesh.json`（两块玻璃在 `translucent` 层，其余为 cutout）、`block_mesh_profiles/beverage_cooler.json`；方块模型 `models/block/beverage_cooler.json` 只有粒子贴图（原来是黑色混凝土，现在用新贴图） |
| 渲染 | AFL Animated Block Mesh Runtime，由主格（左下格）的方块实体画整台冷柜；四个格子仍是 `RenderShape.INVISIBLE`。通道 `left_open` / `right_open`：8 ticks（0.40 秒，与 `BeverageCoolerBlock.ANIMATION_TICKS` 相同），`ease_in_out`；左门绕 [6.87, 16, -7.495] 转 -95°，右门绕 [-22.87, 16, -7.495] 转 +95°（主格坐标） |
| 物品 | `models/item/beverage_cooler.json`：`builtin/entity` + `AflStaticMeshItemRenderer`（关着门的整台冷柜）。各视角都由生成器按投影范围居中，GUI 为 rotation `[25,45,0]`、translation `[1.78,-1.619,-2.887]`、scale 0.328 |

坐标：几何按 V1 源模型单位编写（px；X -8..24 横跨两列，Y 0..32，正面朝 -Z，铰链在外侧，左 = 正对冷柜时的左手边 = +X），输出时 X 平移 -16。所以运行时原点是主格的底面中心，第二列在 -X 方向，即 `FACING` 逆时针的一侧，和方块的格子布局一致。

外观沿用 V1：
- 炭灰色柜体，顶部蓝色灯箱（无字、不发光），底部百叶通风口和温控小面板，背面检修板和百叶；
- 白色内胆、后部中间风道、两根隔板立柱、两侧前角的竖条灯罩、顶灯罩（都不发光）；
- 5 层金属丝隔板：前后横梁、两侧边梁、3 根横撑和前面的标签槽是实体；中间的铁丝网是一对贴镂空图案的面（铁丝沿前后方向，间距 0.65 px，粗 0.18 px），不再是 44 根方块；
- 两扇铝框玻璃门：门框带倒角，门框内侧颜色压暗；玻璃内凹在门框里；外侧两个铰链筒，中间门梃上各一根不锈钢拉手。

### 材质（LabPBR）

| 材质 | Base Color | smoothness | F0 |
|---|---|---|---|
| 柜体 `cabinet` | [44,47,52] 炭灰喷涂钢 | 96–118 | 20 |
| 灯箱 `lightbox` | [64,104,138] 蓝色（不发光） | 118 | 20 |
| 内胆 `liner` | [184,190,194] 白色搪瓷 | 122–134 | 20 |
| 隔板框 `rail` / 铁丝 `wire` | [204,208,210] / [208,212,214] 环氧涂层 | 142–156 / 150 | 20 |
| 门框 `door` | [40,43,48] | 100–124 | 20 |
| 玻璃 `glass` | [150,186,204] 淡蓝，**alpha 56/255** | 235 | 10 |
| 拉手、铰链 `metal` | [168,171,175] 不锈钢 | 150–170 | 255 |
| 其它 | 百叶、温控屏、橡胶脚、灯罩、标签条等 | — | 20 |

玻璃沿用步枪红点镜片的做法（`docs/native_guns/transparent_hybrid_mesh_runtime_v1.md`）：
- 半透明层，`entityNoOutline` 渲染，阴影 pass 里跳过；
- smoothness 235，F0 10；
- alpha 高于光影包半透明 0.1 的阈值。红点镜片 alpha 40，冷柜门面积大，取 56，让玻璃看得出来。

光影下的效果和红点一样还没实机验证。

## 门动画同步（代码改动）

V1 用 GeckoLib 的 `triggerAnim` 让客户端在点击时开始播放动画，8 tick 后服务端才提交方块状态。V2 改用原版方块事件：
- `BeverageCoolerBlockEntity.startDoor` 发 `level.blockEvent`，事件 1 = 左门、2 = 右门，参数 1 = 开、0 = 关；
- `BeverageCoolerBlock.triggerEvent` 把事件转给主格方块实体；服务端返回 true，所以会广播给 64 格内的玩家；
- 客户端记下"已宣布的门目标"，动画立刻朝它播放；8 tick 后状态提交，和目标一致时清掉记录，之后跟随方块状态；
- 在宣布范围外、或者 chunk 在开门过程中重载的玩家，等到状态提交时才看到门开始动。

方块实体的渲染包围盒保持 V1 的手写值（覆盖两列和门的摆动范围），没有改用 profile 的范围，因为服务端没有 profile 缓存，GameTest 会检查这个包围盒。

## 沿用 V1 的运行逻辑

- `BeverageCoolerBlock`：`FACING`、`PART`（`LOWER_LEFT` / `LOWER_RIGHT` / `UPPER_LEFT` / `UPPER_RIGHT`）、`LEFT_OPEN`、`RIGHT_OPEN`。四个格子的朝向和门状态始终一致；左下格是主格，只有它有方块实体。右列在 `FACING` 的逆时针一侧，上排在下排正上方。
- 放置：`BeverageCoolerBlockItem` 检查四个位置、两个地面支撑、区块和世界边界、权限、可替换、流体和遮挡后一次写入四格，失败回滚。
- 开关门：右键门的区域开始 0.40 秒的动画，8 tick 后服务端提交方块状态、碰撞和静止姿势。每扇门有 pending 标记，动画期间重复点击无效，另一扇门可以同时动。过渡标记不存盘，chunk 在过渡中重载会回到上次提交的状态。
- 开着的门伸出本格，`BeverageCoolerDoorRaycast` / `BeverageCoolerDoorInput` / `BeverageCoolerDoorC2SPacket` 负责在客户端检测门板并发包，服务端复核距离、门板和遮挡。
- 碰撞 / 选中按格子 × 朝向 × 两个门状态缓存：侧壁、后壁、底座、顶部、简化的隔板，关着的门各有一块薄板，开着的门是约 95° 的侧摆薄板，选中额外包含门轴附近的一小块。
- 挖掉任一格会移除全部四格；只有被挖的那一格走掉落表，掉一台冷柜。创造模式不掉。镐子 + 铁级（`requiresCorrectToolForDrops`、`mineable/pickaxe`、`needs_iron_tool`）。方块音效是原版金属声，没有开关门音效。
- 开发：`src/dev/java/com/antaurora/apofirstlight/dev/BeverageCoolerGameTests.java`（`src/dev/beverage-cooler-gametest.init.gradle`）没有改，V2 也没有运行它。

## 计划（已商定，未实现）

实时摆放，规则和零售货架 V3 一样：
- 准星指哪拿/放哪；先拿前排，前排拿完才能拿后排；不自动补货。
- 每层 6 列 × 前后 2 排 = 12 格，5 层共 60 格。每层可用 28.3 × 8.9 px，到上一层底下 4.5 px；物品大小同货架（0.24，3.84 px）。
- 每扇门只管自己那半边（各 3 列），开哪扇门就拿哪边。
- 满载时 60 个物品每帧都要渲染，以后的饮料模型要控制面数（单件 300 面以内，罐、瓶做到一两百面）。

## 实机确认

用户 2026-10-01 确认"没问题"。没有单独报告的细项：物品栏图标、关光影下的玻璃、开门动画的时机（点击即开始、8 tick 后提交状态时不跳）、远处金属丝闪烁。
