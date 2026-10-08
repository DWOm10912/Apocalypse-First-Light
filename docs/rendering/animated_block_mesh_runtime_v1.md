# AFL Animated Block Mesh Runtime V1

2026-09-29。**STATUS = COMPLETE / FROZEN（用户确认）**。通用 Runtime V1 保持冻结；开发 Demo 方块及其全部专用代码、资源、源模型、生成器和 Gradle 配置已删除。

验证记录：首次 `compileJava --offline` 通过；本次移除 Demo 仅做文件、引用与 Runtime 内容不变检查，没有重新编译或启动客户端。封版状态依据用户确认，不补记代理未执行的世界内、PBR 或挖掘测试。

**STATIC PROP → baked model / OBJ**：桌、椅、静态货架、钢支撑、HVAC、电气面板等永久静态资产继续原路径（金属垃圾桶 2026-10-02 改成可开盖的容器，不再属于这一类）。

**ANIMATED PROP → AFL Animated Block Mesh Runtime**：仅用于几何需要连续运动的柜门、箱盖、滑门、机器运动部件。现有 stall door、machines **没有迁移**（glass double door 已于 2026-10-07 迁移，银框 / 黑框两个 profile 共用一套网格，见 [commercial_glass_double_door_v2.md](../models/commercial_glass_double_door_v2.md)）；lead chest 已于 2026-09-30 迁移（当前外观 V3，[lead_chest_v3.md](../models/lead_chest_v3.md)）。industrial electrical box 因为要能开门搜刮，2026-09-30 也改走本运行时（门是可动部件，[industrial_electrical_box_v2.md](../models/industrial_electrical_box_v2.md)）；它不再是上面"静态电气面板"那一类。配电箱已于 2026-10-07 删除；Building Power V1 的配电盘、电表箱也走本运行时（[building_power_v1.md](../models/building_power_v1.md)）。cash register 同理：钱箱要能拉出来搜刮，2026-09-30 改走本运行时（可动部件是钱箱，[cash_register_v2.md](../models/cash_register_v2.md)）。beverage cooler 也于 2026-09-30 迁移，只换了渲染：两扇门是可动部件；门逻辑、四格结构和形状仍是原来的代码，门开始转动由方块事件通知客户端（[beverage_cooler_v2.md](../models/beverage_cooler_v2.md)）。chest freezer 于 2026-10-01 迁移：两片滑盖是可动部件（平移动画）；盖子状态机、两格结构和形状仍是原来的代码，滑盖开始由方块事件通知客户端（[chest_freezer_v2.md](../models/chest_freezer_v2.md)）。vending machine 于 2026-10-01 重做（V2）并改走本运行时：没有动画，只用部件显示（`broken` 时不画玻璃，两套灯），两半结构、砸玻璃和形状仍是原来的代码（[vending_machine_v2.md](../models/vending_machine_v2.md)）。commercial dumpster 于 2026-10-02 重做（V2，四种颜色共用一套模型、各自一份 profile 和贴图）并改走本运行时：主格的方块实体画整个 2×1 垃圾箱，两块盖子是可动部件（通道 `left_open` / `right_open`，绕顶面后沿转 95°），由方块状态驱动，垃圾用部件显示；两格结构和形状是方块自己的代码（[commercial_dumpster_v2.md](../models/commercial_dumpster_v2.md)）。metal trash can 于 2026-10-02 重做（V2）并改走本运行时：盖子是可动部件（绕后沿铰链转 80°，通道 `open`），状态由方块状态 `open` 驱动，垃圾袋用部件显示（[metal_trash_can_v2.md](../models/metal_trash_can_v2.md)）。water dispenser 于 2026-10-01 重做（V2）并改走本运行时：原来是没有方块实体的静态方块，现在下半有方块实体；没有动画，只用部件显示（两套指示灯）（[water_dispenser_v2.md](../models/water_dispenser_v2.md)）。checkout counter gate（柜台通道门）于 2026-10-04 改走本运行时：原来是按方块状态切换的静态 OBJ，现在有只用来画动画的方块实体；两个骨骼 `flap` / `door` 各占一个同名通道（180° / 0.8 秒 `ease_in_out`，-90° / 0.4 秒 `ease_out`），都由方块状态 `open` 驱动，按门轴左右各一份 profile（[checkout_counter_v1.md](../models/checkout_counter_v1.md)）。steel door 和新的 commercial wood door 于 2026-10-07 改走本运行时（Steel-frame doors V1）：仍是原版门，下半的方块实体只负责画；按铰链左右各一份 profile，骨骼 `leaf` 跟着 `open` 转；网格朝放门的人那一面是 −Z，所以 `meshFacing()` 返回 `FACING.getOpposite()`；木门的三种样式是 `leaf` 下面的三个子骨骼，用 `meshPartVisible` 切换（[Steel-frame Doors V1](../models/steel_frame_doors_v1.md)）。

**首个正式资产（2026-09-29）**：`industrial_locker` V2，见 [industrial_locker_v2.md](../models/industrial_locker_v2.md)。

**接口扩展（2026-09-29，唯一的运行时改动）**：新增 `blockmesh/AflAnimatedMeshHost` 接口，渲染器的泛型约束从 `T extends AflAnimatedMeshBlockEntity` 改为 `T extends BlockEntity & AflAnimatedMeshHost`。
- 原因：Java 只能单继承，储物柜必须继续继承 `RandomizableContainerBlockEntity`，才能满足逐格搜索和容器的 contract。
- 行为不变：`AflAnimatedMeshBlockEntity` 现在实现这个接口，它的刷新和 bounds 逻辑移到接口的静态方法 `refreshTargets` / `renderBounds` 里，逻辑没有改动。
- 不受影响：profile 格式、动画状态、提交路径、渲染器行为都没有变。
- 接入方式：必须保留其它父类的方块实体直接实现 `AflAnimatedMeshHost`，并在 `onLoad`、`setBlockState`、`getRenderBoundingBox` 中调用这两个静态方法。

## 已审查和复用的基础

- `AflMeshLoader`：继续唯一负责 V1/V2 `.aflmesh.json` 解码、骨骼绑定校验、Quad/Triangle 检查、法线及不可变顶点数组。文件格式和容量上限不变。2026-10-05 起 loader 会把能安全合并的三角形对合成 Quad（见 [Hybrid Mesh Runtime](../native_guns/hybrid_mesh_runtime_v1.md#加载时三角面配对与第一人称背面跳过2026-10-05)），方块也走这一步。现有方块 sidecar 本来就是 Quad，基本不受影响（办公椅合并 53 次）。只给第一人称枪用的背面跳过不对方块开。
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

方块原来返回 `RenderShape.ENTITYBLOCK_ANIMATED`，baked block model 只保留 particle texture，避免双重绘制。2026-10-08 起改成 `RenderShape.MODEL`，方块模型用 `apocalypse_firstlight:static_mesh` 加载器，静止的零件由区块画（见下面"静止部件由区块画"）。Block/BE 的注册、碰撞、交互、容器和挖掘规则仍由具体资产负责，不在通用 renderer 中硬编码。

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

**channel 权威状态（2026-10-06，命中网格加入）**：`AflAnimatedMeshHost` 新增必须实现的 `meshChannelTarget(channel)`：这个 channel 现在应该在开（目标姿势）还是关。原来各方块实体在 `refreshMeshAnimationTargets` 里传给 `refreshTargets` 的 lambda 挪成了这个方法（`AflAnimatedMeshBlockEntity` 转给已有的 `meshAnimationTarget`），动画行为不变。它两端都能调用：命中网格（[mesh_hit_runtime_v1.md](mesh_hit_runtime_v1.md) 0.6 节，`meshhit/AnimatedMeshHits`）在服务端也要按它摆出开 / 关姿势。新接入的方块实体必须让它只读两端都有的状态（方块状态、同步过的字段）。

**部件显示与自发光（2026-10-01，充电站加入）**：`AflAnimatedMeshHost` 新增两个默认方法，renderer 每帧逐 part 询问：

- `meshPartVisible(part)`（默认 true）：返回 false 时跳过这个 part 的几何体和它的子 part。用于同一位置的两套部件互相替换，例如指示灯的暗灯 / 亮灯两套镜片（亮灯那套在贴图 `_s` 的 alpha 里带 LabPBR 自发光，这样光影下只有亮着的灯发光）。
- `meshPartEmissive(part)`（默认 false）：返回 true 时这个 part 用 `LightTexture.FULL_BRIGHT` 代替方块光照。

已有资产都不覆盖这两个方法，行为不变。备用的那套部件在 geo 骨骼上标 `neverRender: true`，`AflStaticMeshItemRenderer` 会跳过这种骨骼，所以物品模型只显示默认那套。使用者：[Charging Station V1](../models/charging_station_v1.md) 的指示灯，[Beverage Cooler V2](../models/beverage_cooler_v2.md) 的门头灯箱和 LED 灯条（2026-10-01），[Chest Freezer V2](../models/chest_freezer_v2.md) 的显示屏和电源指示灯（2026-10-01），[Vending Machine V2](../models/vending_machine_v2.md) 的灯箱、LED 灯条、显示屏，砸碎后不画的玻璃，以及有货的货道不画的螺旋前段（2026-10-01），[Water Dispenser V2](../models/water_dispenser_v2.md) 的两颗指示灯（2026-10-01）。带玻璃等半透明部件的物品走 `AflHybridMeshRendering`，它也跳过 `neverRender` 骨骼。

透明排序和 shader alpha 阈值仍受既有 [Transparent Hybrid Mesh 限制](../native_guns/transparent_hybrid_mesh_runtime_v1.md) 约束，不保证相交玻璃/液体或不同 shader pack 的透明 PBR。

**已知光影包差异（2026-10-01，用户实机）**：Oculus 用 `gbuffers_block_translucent` 画方块实体的透明层，光影包没有这个程序就退回 `gbuffers_block`。Sundial Lite v1.2.0 就没有，而且 `gbuffers_block` 只对颜色缓冲做 alpha 混合，所以透明部件后面那些像素的材质、自发光、法线、光照都被透明部件的值覆盖：玻璃后面的自发光部件不再发光，光照也变成玻璃所在格的光照。Complementary Reimagined r5.9 有这个程序，用户实机确认不受影响（2026-10-01）。

**发光部件在半透明层之后补画（2026-10-01）**：有半透明部件时，renderer 在画完半透明层后，把当前可见的发光部件（`meshPartEmissive`）用自定义渲染类型 `afl_mesh_emissive_relit`（`RelitType`）再画一遍（全亮度），把被透明部件覆盖掉的材质写回去。排序依据：
- Oculus 6020952 的 `FullyBufferedMultiBufferSource.endBatch(RenderType)` 是空方法，开光影时上面的定向 flush 都不起作用。
- 绘制顺序由 `GraphTranslucencyRenderOrderManager` 决定：先按透明类别分组，顺序是不透明 → 不透明贴花 → 普通半透明 → 贴花（glint / crumbling 透明）→ water_mask → lines。分类见 `MixinCompositeRenderType`。
- 同一类别里，只有 Oculus 分了组的绘制（实体，`MixinLevelRenderer` 的 pre/postRenderEntity）才按调用先后连边排序；方块实体不分组，同类里的先后不确定。第一版用 `entityTranslucent` 补画，实机无效。
- 所以补画类型用 glint 透明方式进"贴花"类，一定在所有普通半透明之后。着色器用实体半透明的，光影包会把它映射到和玻璃相同的程序，并用该程序自己的混合设置（Sundial：颜色 alpha 混合，其余直接写入）。
- 不开光影时由上面的定向 flush 保证顺序，glint 叠加混合让全亮度部件在玻璃上显得更亮。

没有半透明部件或没有可见发光部件的资产不补画，行为不变。第二版用户 2026-10-01 在 Sundial Lite 下实机 PASS（见冷柜文档）。

## 静止部件由区块画（2026-10-08，用户实机 PASS）

渲染性能 V1 第二步（[render_performance_v1.md](../dev/render_performance_v1.md)）。方块实体渲染器每帧都把全部顶点重新提交一遍，开光影时阴影还要再提交一遍。但一个网格方块的大部分零件大部分时间都不动：柜体、关着的门、盖、货物。

- **规则**：一个零件的不透明（cutout）几何由区块画，条件是：
  - 动画通道停稳（关或开都行）；
  - 可见（`meshPartVisible`）；
  - 不是全亮（`meshPartEmissive`）；
  - 父零件也满足这几条。

  玻璃（半透明层）、发光件、正在动的零件始终由渲染器画，所以"半透明层之后补画发光部件"不受影响。
- **数据流**（`client/blockmesh/AflMeshChunking`）：
  - 方块实体在客户端主线程算出区块该画的版本（`Variant`）：零件按先序编号的位、停稳通道的值、朝向、着色方式。
  - 这个版本通过方块实体的模型数据交给区块模型。`AflAnimatedMeshHost` 现在继承 Forge 的 `IForgeBlockEntity`，默认的 `getModelData()` 只返回事先算好的值，因为 Forge 可能在区块构建线程上调用它。
  - 版本变化时调 `requestModelDataUpdate()`，并把所在区块段标记为重建：
    - 零件开始动或被隐藏，立刻请求；
    - 零件停稳并加入区块，最快每 0.2 秒一次；
    - 4 秒内请求 8 次以上，就 10 秒内不再往区块里加零件。
- **确认**：区块模型每网格化一个版本就登记一次，并带上它实际画了哪些零件。渲染器只在下面两条都满足时才不画某个零件，所以零件不会缺：
  - 登记之后已经过了 2 帧；
  - 登记的姿势和现在一致。

  零件开始动的那一刻，区块里可能还有 1–3 帧静止的那一份。
- **看不见的方块**（0.25 秒内没在正常画面里画过）：正在动的零件按目标姿势交给区块，远处的门直接跳到开着，不会在动画期间消失。
- **检查频率**：每个客户端 tick 检查正在动或动画目标变了的方块（`AflBlockMeshAnimationState.version()`），其他方块大约每秒检查一次。
- **区块模型**（`client/blockmesh/AflStaticMeshModel`）：
  - 方块模型 JSON 是 `{"loader": "apocalypse_firstlight:static_mesh", "textures": {"particle": ...}}`，方块返回 `RenderShape.MODEL`。
  - profile、朝向和零件都来自模型数据。只有放方块实体的那一格有模型数据，所以一个加载器能服务所有资产、所有格子。
  - 生成的面和渲染器画的一样：
    - 变换相同：方块中心 → 朝向 → origin → scale → 零件 pivot、rest 姿势和停稳的动画姿势；
    - UV 映射进 profile 贴图在方块图集里的 sprite，`ns:textures/block/x.png` 对应 `ns:block/x`；
    - 不封闭的零件补一份背面，因为渲染器不剔除背面；
    - 平面光照（不开 AO）；
    - cutout 层。
  - 着色：开光影时顶点色是白色，用区块自己的方向明暗；不开光影时，把渲染器的实体光照（两盏定向灯，下界用下界的那组）算进顶点色，并关掉方向明暗，这样区块画的零件和渲染器画的零件亮度一致。
  - 一个零件的 UV 超出 0..1（图集里的 sprite 不能平铺），或者伸出所在格超过 1.1 格（Embeddium 判断区块段是否在视锥里时只多留 1.125 格），就留给渲染器画，并记一次警告。
  - 每个版本、每个资源代际只生成一次，资源重载后作废。
- **开关**：开发开关 `static_mesh off`，或方块模型不是这个模型时，渲染器照旧画全部。
- **PBR**：区块路径由 Oculus 按图集 sprite 读同名 `_n` / `_s`，所以贴图必须放在 `textures/block/`。原版会自动把这个目录拼进方块图集。
- **可见距离**：区块在任何距离都画静止零件，所以网格渲染器的可见距离从 64 格改成 128 格（`AflAnimatedBlockMeshRenderer.VIEW_DISTANCE`），玻璃、发光件和货物跟得更远一点。
- **使用者**（2026-10-08 全部接入，生成器已同步）：饮料冷柜、收银机、充电站、柜台通道门、冰柜、四色垃圾箱、两色玻璃双开门、配电盘、工业储物柜、铅箱、金属垃圾桶、电表箱、钢门、商用木门、自动售货机、饮水机。冷柜的第一版原型是在 profile 里手写 `baked` 列表，已删除。
- **实机**：用户 2026-10-08 在 Sundial 下 PASS（外观、开关门、动画正常，性能"好太多"），数字见 [render_performance_v1.md](../dev/render_performance_v1.md)"结果"。仍属理论上的差异：区块的面按自己贴着的那一格取光照，渲染器只在主格取一次；开光影时区块可能被按图集 mipmap 采样；动画开始那一两帧可能有重影。

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
- 新增客户端：`src/main/java/com/antaurora/apofirstlight/client/blockmesh/{AflBlockMeshProfileLoader,AflAnimatedBlockMeshRenderer}.java`。2026-10-08 加入 `AflStaticMeshModel.java`、`AflMeshChunking.java`（静止部件由区块画）；`blockmesh/AflMeshChunkData.java`；`AflBlockMeshAnimationState` 加入 `version()` / `settled()` / `targetValue()`。
- 文档：本文件及 `docs/native_guns/hybrid_mesh_runtime_v1.md` 的共享底层说明。
