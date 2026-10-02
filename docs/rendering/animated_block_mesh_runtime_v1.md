# AFL Animated Block Mesh Runtime V1

2026-09-29。**STATUS = COMPLETE / FROZEN（用户确认）**。通用 Runtime V1 保持冻结；开发 Demo 方块及其全部专用代码、资源、源模型、生成器和 Gradle 配置已删除。

验证记录：首次 `compileJava --offline` 通过；本次移除 Demo 仅做文件、引用与 Runtime 内容不变检查，没有重新编译或启动客户端。封版状态依据用户确认，不补记代理未执行的世界内、PBR 或挖掘测试。

**STATIC PROP → baked model / OBJ**：桌、椅、垃圾桶、静态货架、钢支撑、HVAC、电气面板等永久静态资产继续原路径。

**ANIMATED PROP → AFL Animated Block Mesh Runtime**：仅用于几何需要连续运动的柜门、箱盖、滑门、机器运动部件。现有 glass double door、stall door、freezer、machines **没有迁移**；lead chest 已于 2026-09-30 迁移（当前外观 V3，[lead_chest_v3.md](../models/lead_chest_v3.md)）。industrial electrical box 因为要能开门搜刮，2026-09-30 也改走本运行时（门是可动部件，[industrial_electrical_box_v2.md](../models/industrial_electrical_box_v2.md)）；它不再是上面"静态电气面板"那一类。cash register 同理：钱箱要能拉出来搜刮，2026-09-30 改走本运行时（可动部件是钱箱，[cash_register_v2.md](../models/cash_register_v2.md)）。beverage cooler 也于 2026-09-30 迁移，只换了渲染：两扇门是可动部件；门逻辑、四格结构和形状仍是原来的代码，门开始转动由方块事件通知客户端（[beverage_cooler_v2.md](../models/beverage_cooler_v2.md)）。

**首个正式资产（2026-09-29）**：`industrial_locker` V2，见 [industrial_locker_v2.md](../models/industrial_locker_v2.md)。

**接口扩展（2026-09-29，唯一的运行时改动）**：新增 `blockmesh/AflAnimatedMeshHost` 接口，渲染器的泛型约束从 `T extends AflAnimatedMeshBlockEntity` 改为 `T extends BlockEntity & AflAnimatedMeshHost`。
- 原因：Java 只能单继承，储物柜必须继续继承 `RandomizableContainerBlockEntity`，才能满足逐格搜索和容器的 contract。
- 行为不变：`AflAnimatedMeshBlockEntity` 现在实现这个接口，它的刷新和 bounds 逻辑移到接口的静态方法 `refreshTargets` / `renderBounds` 里，逻辑没有改动。
- 不受影响：profile 格式、动画状态、提交路径、渲染器行为都没有变。
- 接入方式：必须保留其它父类的方块实体直接实现 `AflAnimatedMeshHost`，并在 `onLoad`、`setBlockState`、`getRenderBoundingBox` 中调用这两个静态方法。

## 已审查和复用的基础

- `AflMeshLoader`：继续唯一负责 V1/V2 `.aflmesh.json` 解码、骨骼绑定校验、Quad/Triangle 检查、法线及不可变顶点数组。文件格式和容量上限不变。
- `AflMeshCache`：沿用一个 prepare/apply reload listener；先载 mesh，随后解析 `block_mesh_profiles/*.json`。没有新增另一套 reload listener。
- `AflMeshModel` / `AflMeshPart`：继续按 bone / cutout / translucent 缓存 parts、corners、face offsets。只新增只读 `boneNames()`，用于校验 profile 不遗漏 mesh。
- `AflMeshRenderer`：把原顶点循环抽成 `renderPartsAtCurrentPose`。旧 GeoBone API 仍先平移到 pivot 再调用同一循环；hidden/zero-scale、镜像法线、Quad/退化 Quad、UV、light/overlay、metrics 语义保留。
- `AflStaticMeshItemRenderer`、`AflHybridMeshRendering`、`NativeAnimatedWeaponRenderer` 调用方式及正式资产均未改。新 BER 不使用 GeoBone/GeckoLib 动画控制器、不共享枪械实例姿态；已有项目 GeckoLib 依赖没有新增、升级或移除。
- 现有 double door / stall / freezer 为专用 `GeoBlockRenderer`（lead chest、cooler 已迁移到本运行时）；部分机器组合 baked model、流体/物品与手工顶点。现有 BER 注册使用 `EntityRenderersEvent.RegisterRenderers`。保留这些实现及各自状态同步。
- 现有 easing 只有特定功能的私有函数和枪械 hold easing；没有合适的独立通用接口。本轮用小型本地 easing enum，不牵入枪械动画高层。

## 资源与注册

每份资产提供：

| 文件 | 作用 |
|---|---|
| `assets/<namespace>/geo/<id>.geo.json` | 与现有 loader 相同的骨骼名/atlas 校验输入，无需 cubes |
| `assets/<namespace>/meshes/<id>.aflmesh.json` | 现有 AFL sidecar，V1/V2 均支持 |
| `assets/<namespace>/block_mesh_profiles/<id>.json` | 本文的 native 变换、层级、动画、边界 contract |
| `assets/<namespace>/textures/block/<id>.png` | Base Color，整模型一个 atlas |
| 同目录 `<id>_s.png` / `<id>_n.png` | 可选 LabPBR companions，同 UV、同尺寸 |

`AflAnimatedMeshBlockEntity` 子类在构造时传入 **完整 profile ResourceLocation**（包含 `block_mesh_profiles/` 和 `.json`），实现 `meshAnimationTarget(channel)`；必须保留其它父类的方块实体改为实现 `AflAnimatedMeshHost`（见上文）。客户端注册：

```java
event.registerBlockEntityRenderer(MY_BLOCK_ENTITY.get(), AflAnimatedBlockMeshRenderer::new);
```

方块返回 `RenderShape.ENTITYBLOCK_ANIMATED`，baked block model 只保留 particle texture，避免双重绘制。Block/BE 的注册、碰撞、交互、容器和挖掘规则仍由具体资产负责，不在通用 renderer 中硬编码。

最小 profile 格式示例（`example:hinged_panel` 是文档占位，不是已注册方块或现存资源；接入时换成实际资产）：

```json
{
  "format_version": 1,
  "geometry": "example:geo/hinged_panel.geo.json",
  "texture": "example:textures/block/hinged_panel.png",
  "origin": [0, 0, 0],
  "scale": [1, 1, 1],
  "facing": "horizontal",
  "bounds": [-0.25, 0, -0.75, 1.125, 1, 1],
  "parts": {
    "body": {"pivot": [0, 0, 0]},
    "door": {"pivot": [-0.38, 0.1, -0.3]}
  },
  "animations": {
    "open": {
      "duration_ticks": 16,
      "easing": "ease_in_out",
      "transforms": {"door": {"rotation": [0, 105, 0]}}
    }
  }
}
```

`parts` 的 key 是 sidecar 的 **bone binding 名**，不是 mesh part 名；多个 mesh part 可绑定同一 bone。每个节点必须给 `pivot`，可给 `parent` 和 `rest`。`rest` 和动画目标都支持 `translation`、`rotation`、`scale`；缺省分别为 `[0,0,0]`、`[0,0,0]`、`[1,1,1]`。无 motion 的节点为静态。允许纯 pivot 节点，要求包含所有有 mesh 的 bone。

动画 channel 名任意；服务器同步状态由具体 BE 映射为 boolean。多 channel 可控制不同 parts；同一 part 仅允许一个 channel，避免隐式混合。duration 单位 ticks（20 ticks/s），范围 `(0,72000]`。easing：`linear`、`ease_in`（二次）、`ease_out`（二次）、`ease_in_out`（smoothstep）。动画目标是相对 rest 的 delta：位移/欧拉角相加，scale 乘以 `lerp(1,targetScale,progress)`。

profile 最多 128 parts、32 channels、32 层深度；拒绝未知字段、非有限数、循环层级、缺失 binding、无效 geometry/texture 和越界参数。错误每次 reload 按资源记录一次；仅禁用出错 profile，不影响其它 mesh。它不是第二个 mesh parser。

## 坐标、pivot 与 facing

- native model space：**block 单位**，Y 向上、X 向东、Z 向南，NORTH 为前方 `-Z`；默认原点在方块底面中心。BB 的 16 units = 1 block。
- sidecar corners 保持既有 `bone_pivot_local_blocks`，不在新 BER 中再反转 X 或再除 16。
- profile pivot 是这个 native model space 的**绝对 pivot**；子节点使用 `childPivot - parentPivot`，继承父旋转/缩放。源 Free Mesh 的 group origin / 16 即该 pivot；现有 geo exporter 的 X 符号转换只属于 geo 文件，不能直接把 geo pivot 数值原样复制到 profile。
- 变换链：BER 方块原点 → `[0.5,0,0.5]` → facing → origin → profile scale → parent → pivot 差/位移 → Rz·Ry·Rx → scale → pivot-local corners。列向量实际先受 X，再 Y，再 Z 旋转。
- NORTH=0°、EAST=-90°、SOUTH=180°、WEST=90°，沿用 `HorizontalShapeUtils` 的方向约定。旋转发生在 part 之前，pivot、geometry 和 normal 同时转动。`facing: "none"` 可禁用；默认读取 `HORIZONTAL_FACING`，特殊方块覆盖 `meshFacing()`。
- mesh loader 对 geo 只做既有骨骼名与 atlas 校验；**新 BER 的 rest、parent 和 pivot 以 profile 为准，不从 GeoBone 隐式补变换**。从既有资产迁移时必须把 authoring rest pose 显式写入 profile，不能重复烘焙。

## 动画状态与时间

服务器维护真实 BlockState/BE 数据；renderer 从不改服务器状态。接入方可在服务器用 `setBlock(..., UPDATE_ALL)` 更新 `OPEN`，走原版状态同步。通用 Runtime 本身不提供交互行为，也不引入新 packet 或 server ticker。

基类在客户端 `onLoad` 和 `setBlockState` 收到目标状态时更新 channel。因此曾加载的离屏实例也按状态到达时间推进，不等到下一次可见才开动。NBT 状态资产在应用 `handleUpdateTag` / BE update packet 后调用 `refreshMeshAnimationTargets()`；自己的 `setBlockState`/`onLoad` override 必须调用 super。

每个 BE 独有 `AflBlockMeshAnimationState`。render 用 `gameTime + partialTick` 采样，而非每帧固定增量。反向切换从当前进度出发，剩余时长按行程比例缩短；同一 tick 内不会倒退到比上一显示帧更早的时间。保证位置连续，不承诺反向瞬间速度连续。初次载入、重进世界或 F3+T 后直接恢复权威目标姿态，不重播历史开门；后续变更才平滑插值。每帧不发包、不解析 JSON、不生成 vertex arrays。

## 材质、光照与 shader

沿用现有 `NEW_ENTITY / QUADS` 顶点提交：CUTOUT 为 `entityCutoutNoCull(texture)`；存在透明 parts 时先 cutout 再 `entityNoOutline(texture)`，沿用定向 buffer flush 与 `AflShaderCompat.activeShadowPass()` 查询。透明层不重复提交实体外壳；无新 RenderType、VertexFormat、shader 或 PBR system。

已读本机 Oculus 6020952 的 `SimplePBRLoader`、`PBRTextureManager` 和 `MixinGameRenderer`：它按 Base Texture ResourceLocation 查同目录 companions；有 default holder/default normal/default specular；标准 cutout 可进入 block-entity shader 分支。**这是代码兼容性依据，不是新 BER 的 GPU 绑定/PBR 实机 PASS**。无 shaders 使用 Base Color；缺 `_s`/`_n` 交给 Oculus 既有默认值，不由 AFL 主动打开缺失图。Embeddium 仍通过原 VertexConsumer 路径，无 internals patch。

renderer 默认原样传递 dispatcher 的 `packedLight`（sky + block light）与 `packedOverlay`。位置/法线经过同一 PoseStack；低层保留逆转置、归一化及奇异矩阵保护。V1 是 **BER 采样点世界光照**，不是 baked terrain 的逐顶点邻接 AO，也不是大门每个移动端点独立取光。AO 可用材质表达。

**部件显示与自发光（2026-10-01，充电站加入）**：`AflAnimatedMeshHost` 新增两个默认方法，renderer 每帧逐 part 询问：

- `meshPartVisible(part)`（默认 true）：返回 false 时跳过这个 part 的几何体和它的子 part。用于同一位置的两套部件互相替换，例如指示灯的暗灯 / 亮灯两套镜片（亮灯那套在贴图 `_s` 的 alpha 里带 LabPBR 自发光，这样光影下只有亮着的灯发光）。
- `meshPartEmissive(part)`（默认 false）：返回 true 时这个 part 用 `LightTexture.FULL_BRIGHT` 代替方块光照。

已有资产都不覆盖这两个方法，行为不变。备用的那套部件在 geo 骨骼上标 `neverRender: true`，`AflStaticMeshItemRenderer` 会跳过这种骨骼，所以物品模型只显示默认那套。使用者：[Charging Station V1](../models/charging_station_v1.md) 的指示灯，[Beverage Cooler V2](../models/beverage_cooler_v2.md) 的门头灯箱和 LED 灯条（2026-10-01）。带玻璃等半透明部件的物品走 `AflHybridMeshRendering`，它也跳过 `neverRender` 骨骼。

透明排序和 shader alpha 阈值仍受既有 [Transparent Hybrid Mesh 限制](../native_guns/transparent_hybrid_mesh_runtime_v1.md) 约束，不保证相交玻璃/液体或不同 shader pack 的透明 PBR。

**已知光影包差异（2026-10-01，用户实机）**：Oculus 用 `gbuffers_block_translucent` 画方块实体的透明层，光影包没有这个程序就退回 `gbuffers_block`。Sundial Lite v1.2.0 就没有，而且 `gbuffers_block` 只对颜色缓冲做 alpha 混合，所以透明部件后面那些像素的材质、自发光、法线、光照都被透明部件的值覆盖：玻璃后面的自发光部件不再发光，光照也变成玻璃所在格的光照。Complementary Reimagined r5.9 有这个程序，用户实机确认不受影响（2026-10-01）。用户决定先不处理。实例见 [Beverage Cooler V2](../models/beverage_cooler_v2.md) 的"已知问题"。

## Bounds、缓存与 reload

profile `bounds` 为 NORTH 朝向、**已包含 origin/scale/全部运动行程之后的方块局部 AABB** `[minX,minY,minZ,maxX,maxY,maxZ]`，默认 `[0,0,0,1,1,1]`。作者负责覆盖整个运动范围；不是静止 mesh bounds。禁止空/反向/非有限 bounds，各坐标最大绝对值 64；不会默认无限包围盒。

四个朝向的 bounds 在 load 时绕方块中心预计算。BE 的 `getRenderBoundingBox()` 返回它加 BlockPos；缺 profile 回退单格。沿用 Forge frustum 与 BER 默认 64-block 距离门槛，不强制 `shouldRenderOffScreen=true`。

mesh、分层列表、profile 树、变换定义、bounds 均在 reload 时缓存；逐帧只有 pose/channel 采样和原 CPU 顶点提交。新增 profile 与 mesh 在同一个 reload 的 apply 阶段替换；BE 下一次读取拿新 bounds，下一次渲染丢弃旧动画定义，renderer 不留旧 mesh/GeoBone。删资源或无效 profile 会停止该资产绘制，不继续画旧缓存。

## 开发 Demo 已移除

`apocalypse_firstlight:animated_block_mesh_demo` 已注销，开发注册类、专用资源、Blockbench 源和生成器均已删除；没有保留替代 Demo 或旧 ID 兼容注册。其旧 `/setblock` 入口已失效。同步清除该 Demo 的本地编译 class 与复制资源，避免旧构建产物继续被扫描。未修改任何世界存档或日志。

今后的正式资产应按上面的通用 profile/BE 注册示例接入。Block/BlockEntityType 应在合法注册阶段创建（DeferredRegister supplier 或对应 RegisterEvent supplier），不能在自动事件订阅类的静态初始化中提前实例化。正式工业 locker 的 pickaxe、Diamond tier、drops/tags 与 Survival 验收仍由独立资产接入任务负责。

## V1 边界与后续接入

支持刚性 part 层级、一个 atlas、独立 boolean channels、translation/rotation/scale、四种 easing。暂不支持 timeline/keyframes、animation graph、同一 part 多动画混合、IK/skinning、连续循环 rotor 时钟、自动碰撞/门占位、自动 multi-block、逐 part 贴图、LOD/instancing/GPU skinning、terrain AO；自发光只有上面的逐 part 全亮度开关，没有更细的光照策略。

industrial_locker V2 已于 2026-09-29 按这个流程接入（`tools/build-industrial-locker-v2.mjs`，bones `body` / `door`，通道 `open`，-100°，10 ticks），详见 [industrial_locker_v2.md](../models/industrial_locker_v2.md)。

潜在回归范围只有共享 `AflMeshRenderer` 的无语义顶点循环抽取以及 `AflMeshCache` 的新增 profile 加载阶段。静态比较确认抽取前后顶点循环一致；枪械/物品调用方、mesh parser、资产、shader、RenderType/VertexFormat 定义均未变。不能以此声称枪械画面已经实测。

## 冻结 Runtime 文件清单

- 共享底层：`src/main/java/com/antaurora/apofirstlight/client/mesh/{AflMeshCache,AflMeshModel,AflMeshRenderer}.java`。
- 新增通用数据/实例：`src/main/java/com/antaurora/apofirstlight/blockmesh/{AflBlockMeshProfile,AflBlockMeshProfiles,AflBlockMeshAnimationState,AflAnimatedMeshBlockEntity,AflAnimatedMeshHost}.java`（`AflAnimatedMeshHost` 于 2026-09-29 加入，2026-10-01 加入 `meshPartVisible` / `meshPartEmissive` 默认方法）。
- 新增客户端：`src/main/java/com/antaurora/apofirstlight/client/blockmesh/{AflBlockMeshProfileLoader,AflAnimatedBlockMeshRenderer}.java`。
- 文档：本文件及 `docs/native_guns/hybrid_mesh_runtime_v1.md` 的共享底层说明。
