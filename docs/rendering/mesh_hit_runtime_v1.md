# AFL Mesh Hit（命中网格）V1 设计

状态（2026-10-06）：**P1（子弹）已实现**，用户 2026-10-06 实机 PASS。**P2（准星选中、选中轮廓）已实现**（同日用户要求继续做）；同日补上多格资产和 AFL 动画网格方块（0.6 节）。`compileJava`、`compileDevJava`（`--offline`）通过；P2 连同 0.6 节用户 2026-10-06 实机 PASS（"这样 voxelshape 就好很多了"）。P3（碰撞盒自动拟合）未做，见第 3 节"以后再做"。

用户 2026-10-06 确认：第 11 节的问题都按推荐来（所有 AFL OBJ 方块默认启用；子弹可以穿过空隙；贴图透明处仍挡子弹），先只做 P1。

## 0. 实现（P1）

代码在 `src/main/java/com/antaurora/apofirstlight/meshhit/`：

| 类 | 作用 |
|---|---|
| `MeshHitModel` | 读模型 JSON（`forge:obj`）和 OBJ，四边形以上按扇形拆成三角形，丢掉退化面；BVH（叶子 ≤ 8 个三角形，按最长轴中位数分）；双面求交（Möller–Trumbore），法线翻到朝向射线一侧。射线某个分量为 0 时用 1e-12 代替，避免包围盒检测里出现 0 × ∞ |
| `BlockstateMeshResolver` | 读本 mod 的 blockstate JSON：`variants`（"" / "normal" / "a=b,c=d"；带权重的数组取第一个）、`multipart`（`when`，"a\|b"，"!" 取反，`OR` / `AND`），`x` / `y` |
| `MeshHitModels` | 按模型 id、按方块状态、按零件列表缓存（`ConcurrentHashMap`，服务端和客户端线程共用）；旋转和原版 `BlockModelRotation` 一样：绕方块中心 `rotateYXZ(-y, -x, 0)`，射线用它的逆变换进模型坐标；排除清单 `EXCLUDED`（目前为空） |
| `MeshHitProvider` | 动态拼装模型的方块自己给零件：`FluidPipeBlock`（流体管道、耐热管道）按 `block/FluidPipePieces`（从 `client/FluidPipeBakedModel` 挪出来的通用代码，客户端模型和命中网格共用）给出钢零件和对应的 `_glass` 零件 |
| `MeshHitAssembled`（2026-10-09） | 零件要转向、而且要合成一个模型的方块：路牙（`CurbBlock`）给出"地面方块立方体（`block/curb/hit_base`，只用于命中和轮廓，不画）+ 这一格画的路牙零件"，每个零件带 y 转角（90° 的倍数，和 `BlockModelRotation` 一样北转东）。`MeshHitModels` 把它们转好、合成一个 `MeshHitModel`，所以选中轮廓在零件接缝处没有多余的线。零件列表来自 `block/CurbGeometry.pieces`，和客户端模型共用。原因：用户实机看到路牙过渡段的选中框是碰撞盒的 4 级台阶，没有用 mesh 轮廓。改后用户 2026-10-09 实机 PASS |
| `MeshHitClip` | 和原版 `BlockGetter#clip` 一样逐格走；有命中网格的格子用三角面，没有的照旧用形状；这一格的形状对当前检测是空的（本来就挡不住）时不用网格：命中网格只会让子弹"少挡"，不会让原来打不中的方块变得能挡子弹 |
| `MeshBlockHitResult` | 继承 `BlockHitResult`，带真实法线；`getDirection()` 是最接近法线的轴向；`normalOf(hit)` 对普通命中返回面的法线 |

## 0.5 实现（P2：准星选中和选中轮廓，2026-10-06）

`client/MeshHitPicking` 和 `mixin/client/MeshHitPickMixin`：
- **选中**：原版准星选中（`GameRenderer#pick`）里找方块的那一步（`Entity#pick`）被重定向到 `MeshHitClip`（选中形状 `OUTLINE`）。有命中网格的方块只在瞄到模型表面时才被选中；瞄到模型旁边、模型里的空气（圆桶和格子角之间、提手之间、管道旁边），视线继续往后，选中后面的东西。之后原版照旧：只在这个距离以内找实体、检查触及距离，所以不会隔着模型点到后面的实体。
- 命中点在模型伸出本格的部分时，夹回本格范围内（服务端会拒绝离方块中心超过 1 格的使用请求），和 `AflMeshShapePicking` 的做法一样。
- 原有的 `AflMeshShapePickMixin`（打开的门、垃圾箱盖这类伸出本格的 Mesh Shape 方块）仍在之后运行，只有更近时才替换，互不影响。
- **轮廓**：Forge `RenderHighlightEvent.Block` 里，被选中的方块有命中网格时取消原版方框，改画模型的特征边（`MeshHitModel#edges`）：先把重合的顶点焊接起来（精度 1e-4），只属于一个三角形的开口边、被两个以上三角形共用的边、两侧面折角超过 35° 的边保留（不管面的朝向，双面的平面不算折角），短于 0.015 格的边去掉（免得螺栓之类把轮廓画乱）。和原版一样用 `RenderType.lines()`、黑色、透明度 0.4。特征边每个模型第一次被选中时算一次并缓存。
- 适用范围和 P1 相同：有命中网格的方块才改；其它方块（包括原版方块）照旧。

已知不足（P2）：
- 圆柱面按 20 段左右的多边形建模，相邻面只折 18° 左右，不会画出轮廓线，所以圆桶的轮廓是两端的圆和接缝、提手等折角，没有侧面的"剪影线"（剪影随视角变化，V1 不画）。
- 生成器做出来的网格如果有 T 形接缝（一条边的中间接着另一个面的顶点），接缝处会被当成开口边画出来，可能在面上多出几条线。
- 选中只在瞄到模型表面时成立，细小的零件（细管、栏杆）需要瞄得更准。

## 0.6 补充：多格资产和 AFL 动画网格方块（2026-10-06）

为什么补：用户实机截图（地下储罐、商用垃圾箱）问"阶段 2 改了啥，为啥选框还是这样"。P1 / P2 只覆盖"自己那一格的方块状态解析到 OBJ 模型"的方块，下面两类原来没有命中网格，选框、子弹都还是方盒子：
- **多格资产**：模型只由一格（主格）画，其余格子是只有粒子贴图的 "cell" 模型。
- **AFL 动画网格方块**：模型由方块实体渲染器（`AflAnimatedBlockMeshRenderer`）按 profile 画，方块状态没有 OBJ。

**多格**（`meshhit/MeshHitMultiCell`）：方块实现 `meshHitMaster(state, pos)`，给出画模型的那一格。`MeshHitModels#shape` 对非主格取主格的整台模型，按主格相对这一格的偏移平移（`Shape#moved`），所以射线、准星在任何一格都按整台模型算，任何一格的选框都是整台模型的轮廓。主格不是同一种方块时（结构不完整）不用网格，照旧方盒子。命中点落在同一方块的另一格时，命中结果改成那一格（`MeshHitModels#inCell`：沿法线往表面里退 1e-4 后取所在格），弹坑和选框归它所在的格子；落在结构外面（模型伸出去的部分）时仍归射线正在检测的那一格。

| 方块 | 主格 |
|---|---|
| 地下储罐（汽油、柴油） | 加油口格（`UndergroundFuelTankBlock#masterPosition`），它的 multipart 画罐体 |
| 加油机 | a0 格（`FuelDispenserBlock#rootPosition`） |
| 加油机底槽 | a0 格（`FuelDispenserSumpBlock#rootPosition`） |
| 取水泵、耐热取水泵 | bank 格（`IntakePumpBlock#bankPosition`） |
| 饮料冷柜 | 左下格（`BeverageCoolerBlock#masterPosition`） |
| 充电站 | 左格（`ChargingStationBlock#masterPosition`） |
| 卧式冰柜 | 左格（`ChestFreezerBlock#masterPosition`） |
| 商用垃圾箱 | 主格（`CommercialDumpsterBlock#rootPosition`） |
| 工业储物柜、自动售货机、饮水机 | 下半格 |

**AFL 动画网格方块**（`meshhit/AnimatedMeshHits`）：方块实体是 `AflAnimatedMeshHost` 时，用它的 mesh sidecar（`meshes/<stem>.aflmesh.json`）按 block mesh profile 摆出整个模型，变换和渲染器完全一致：方块原点 → `[0.5,0,0.5]` → 朝向 → origin → scale → 每个 part：父节点、pivot 差和位移、Rz·Ry·Rx、缩放。
- 从 mod jar 直接读 profile 和 sidecar（`AflBlockMeshProfiles` 只在客户端有），所以服务端（子弹）和客户端（准星）算的一样。
- **姿势**：每个动画 channel 取两端之一，关（0）或开（1），由方块的权威状态决定。为此 `AflAnimatedMeshHost` 新增 `meshChannelTarget(channel)`，各方块实体原来写在 `refreshMeshAnimationTargets` 里的 lambda 挪成这个方法，两端共用，动画行为不变。所以开着盖的垃圾箱、开着门的储物柜，命中网格也是开着的姿势。
  - 2026-10-09（柴油发电机组）：方块实体用 `meshChannelAffectsHits(channel)` 返回 false 的通道（仪表指针、钥匙、小时计鼓轮、排气防雨帽这类停在两端之间的数值通道）不参与：按 0 摆，也不等它在客户端停稳（否则指针一动，整台机组就退回碰撞盒）。门和加油口盖照常参与。
- **动画进行中**（客户端这个 channel 的采样值还没到目标）不用网格，这几 tick 照旧方盒子。服务端没有动画过程，直接按目标姿势。
- **不算的部件**：`goods_` 开头的部件（物资）和它们的子部件不算，它们在壳里面，开着时随战利品出现消失；方块实体当前隐藏的部件（`meshPartVisible` 为 false：售货机砸碎后的玻璃、灯的另一套镜片）不算。
- 缓存：profile 按 id 读一次；每个（profile，朝向，各 channel 开 / 关）组合摆一次，每个部件一个 `MeshHitModel`；可见部件组合对应的 `Shape` 也缓存。
- 覆盖：饮料冷柜、收银机、充电站、收银台闸门（左、右）、卧式冰柜、商用垃圾箱（4 色）、工业电箱、工业储物柜、铅箱、金属垃圾桶、自动售货机、饮水机。

数值检查（`node`，按同样的变换算全部 16 个 profile 关 / 开两种姿势的网格包围盒）：都落在 profile 的 `bounds` 里，和它相差约 1/64 格（bounds 本来就比网格外扩 1/64），说明变换链和渲染器一致。**这是数值检查，不是实机验证。**

已知不足（0.6）：
- 垃圾箱开着的盖子竖在上一格里。原版的选中、射线只检测经过的格子，射线只从上一格（空气）经过时不会检测垃圾箱，所以子弹打不到竖起来的盖子；准星仍由原来的 `AflOverhangPickBlock` 补选（方盒子）。
- 同理（2026-10-09）：停车场区域灯 `area_light`（[Site Lighting V1](../models/site_lighting_v1.md)）的灯头悬在杆顶旁边那一格，射线只经过灯头那一格、不经过杆顶格时，选不中也打不中灯头。没做补选。
- 动画网格方块的碰撞、选中形状（mesh shape profile 的方盒子）没变；命中网格只在射线进入这些方盒子所在的格子后才参与。
- 动画进行中那几 tick 是方盒子，选框会跳一下。

和第 6 节设计的差别：网格交点**不限制在本格范围内**，只要射线经过这个方块所在的格子，它整个模型都参与求交（和原版形状 `clip` 的行为一致）。

接入（和第 7 节一致）：
- `weapon/NativeGunShot#trace`：子弹射线改用 `MeshHitClip`。
- `weapon/BulletImpacts`：火花位置、同步给客户端的弹着点都带法线。
- `fluid/FuelLeaks`：弹孔 `Hole` 多一个 `normal`，漏油油柱沿它喷出；存档多存 `NX/NY/NZ`，旧存档没有时按原来的面推出法线；爆炸冲击线打到容器的位置也改用 `MeshHitClip`。
- `network/AflNetwork`：`BulletImpactS2CPacket`、`FuelLeakS2CPacket.Leak` 各多 3 个 float（法线）。
- `client/BulletHoles`：法线和某个轴对齐时照旧（贴在方块面上、夹在面的范围内、木头纹理沿 u 轴）；否则贴在切平面上（u 轴尽量水平），多抬高 0.006 格（平的贴花在曲面上两端会离开表面），不再夹在面的范围内；光照取命中点沿法线 0.1 格处。
- `client/ClientFuelLeaks`：漏油油柱、弹孔上的火、弹孔贴花都按法线。

开发命令（`src/dev`，`dev/meshhit/DevMeshHitCommands`）：
- `/dev meshhit`：沿视线打一条 8 格的测试射线，聊天栏对比碰撞盒和命中网格各自打到哪里、法线、这个方块的三角形数；命中网格处沿法线冒几颗 END_ROD 粒子。
- `/dev meshhit check`：遍历本 mod 所有方块状态，统计有命中网格的状态数和三角形总数，动态方块（管道）单列，没有命中网格的方块写进日志（`[AFL MESH HIT]`）。

已知不足（P1）：
- ~~多格资产只在画整台机器的那一格有命中网格~~：0.6 节已补上（`MeshHitMultiCell`）。
- 电缆（`PowerCableBakedModel`，也是动态拼装）没有接 `MeshHitProvider`，仍按碰撞盒。
- AFL 动画网格方块：0.6 节已补上（`AnimatedMeshHits`）。其它方块实体渲染器画的方块（GeckoLib 等）、原版 JSON 方块元素模型：仍按碰撞盒。
- 抛壳落地、加油枪油流还是碰撞盒（以后）；玩家准星在 P2 里改了，见 0.5 节。


## 1. 问题

用户 2026-10-06：很多模型的 VoxelShape 没有紧贴模型，开枪的弹坑浮在空中（截图：红色 / 蓝色油桶的弹坑贴在桶外的方盒子表面上；玻璃流体管道的弹坑浮在管道上方的盒子顶面上）。

原因：
- 子弹射线（`weapon/NativeGunShot#trace`）用 `ClipContext.Block.COLLIDER`，打中的是碰撞盒。圆桶、圆管、斜面这类模型的碰撞盒是一个或几个方盒子，盒子和模型之间的空气也算"打中了"。
- 命中结果只带一个轴向的面（`Direction`）。弹坑贴花（`client/BulletHoles`）、油桶弹孔和漏油（`fluid/FuelLeaks.Hole`、`client/ClientFuelLeaks`）都按这个轴向的面放，圆面上只能贴成平的。
- 抛壳落地（`weapon/client/NativeGunFx`）也用碰撞盒，弹壳会停在桶顶上方的空气里。

## 2. 为什么不是"多边形 VoxelShape"

Minecraft 的 `VoxelShape` 本质上是一组轴对齐方盒子。碰撞、移动、实体推挤、原版射线都只认它，没法换成三角面。所以 V1 不改 VoxelShape，而是在它旁边加一层**命中网格**：只给"射线打到哪里"用的精确三角面。

和 [mesh_shape_runtime_v1.md](mesh_shape_runtime_v1.md) 的关系：那里的五层（画面、碰撞、选中、交互区域、锚点）保持不变，原则"网格精细、形状简化"仍适用于碰撞和选中。命中网格是第六层，只回答"这条射线最先碰到模型的哪一点、那里的表面朝哪边"。

## 3. 目标与范围

**V1 要做（P1：子弹）**
- 子弹射线在 AFL 方块上打到真实的模型表面：命中点在面上，带真实的表面法线。
- 子弹能穿过模型上的空隙（提手之间、货架层板之间、管道旁边的空气），继续往后飞。
- 弹坑贴花按真实法线贴在面上（圆桶上是切着圆面的）。
- 油桶 / 油壶的弹孔位置、漏油油柱的方向都按真实法线。

**以后再做**
- P2：准星选中按命中网格算（瞄空气不选中），选中轮廓画模型的外形线而不是方框。
- P3：碰撞盒由网格自动拟合（按 1–2 px 体素化后合并成少量盒子），替代手写的大方块。
- 以后：抛壳落地、加油枪喷出的油流和油斑、爆炸遮挡也可以用命中网格。

**V1 不做**
- 不改碰撞、移动、选中（P2、P3 再说）。
- 不按贴图透明度判断（网格、格栅的透明像素照样挡子弹）。
- 不覆盖由方块实体渲染器画的方块（模型里没有面）和 AFL 动画网格方块（储物柜、垃圾箱等），它们保持现状：用碰撞盒。（V1 当时的范围；AFL 动画网格方块后来在 0.6 节补上了。）
- 不覆盖原版 JSON 方块元素模型（本来就是方盒子，碰撞盒基本贴合）。

## 4. 数据从哪里来

**运行时直接读渲染用的模型**，不另外生成文件：

- 方块状态 → 模型：读我们自己的 `assets/apocalypse_firstlight/blockstates/<方块>.json`。支持 `variants`（含 `x` / `y` 旋转；有多个带权重的随机变体时取第一个）和 `multipart`（`when` 条件、`OR`）。
- 模型 → 三角面：模型 JSON 是 `"loader": "forge:obj"` 时读它的 `.obj`（顶点就是方块坐标 0–1，见各生成器的说明）。四边形拆成两个三角形，退化的面丢掉。
- 动态拼装的模型（流体管道、耐热管道、电缆：`client/FluidPipeBakedModel`、`PowerCableBakedModel` 按连接情况挑零件）：方块实现一个接口 `MeshHitProvider`，按方块状态返回要用哪些零件模型（和客户端的挑法共用同一份代码，放在通用代码里）。
- 文件从 mod jar 里读（`Class#getResourceAsStream`，和 `AflMeshShapes` 读 `mesh_shapes` 一样），服务端和客户端读到的完全相同。专用服务端的 jar 里也有 `assets/`。

为什么不让生成器另外导出命中网格：一共 468 个 OBJ 模型、几十个生成器，逐个改容易漏，而且模型一改两份数据就可能不一致；直接读渲染模型，命中网格永远和画面一致，新方块也自动生效。

**规模**：468 个 OBJ 一共约 9.2 万个面（中位数 52，最多约 5200：取水泵）。全部加载约 18 万个三角形、约 7 MB；实际按需加载（第一次有子弹打到这个模型时才读），通常只有几十个模型。

## 5. 运行时结构

计划放在 `src/main/java/com/antaurora/apofirstlight/meshhit/`（通用代码，服务端客户端都用）：

| 类 | 作用 |
|---|---|
| `MeshHitModel` | 一个模型的三角形（float 数组）和包围盒层次树（BVH，每个叶子 ≤ 8 个三角形），加载后不变 |
| `MeshHitModels` | 按模型 id 缓存 `MeshHitModel`；按方块状态缓存"这个状态由哪些模型、各转多少度组成"（`MeshHitShape`，没有可用模型时记为空） |
| `BlockstateMeshResolver` | 解析我们自己的 blockstate JSON：`variants` / `multipart`，`x` / `y` 旋转 |
| `MeshHitProvider` | 方块可选实现：动态拼装的方块自己给出零件列表（管道、电缆） |
| `MeshHitClip` | 射线检测：沿射线逐格走，有命中网格的格子用三角面，没有的用原版形状 |
| `MeshBlockHitResult` | 继承 `BlockHitResult`，多带一个表面法线 `normal`；`getDirection()` 仍返回最接近法线的轴向（给只认轴向的旧代码用） |

**坐标**：OBJ 顶点（方块单位）→ 先绕方块中心按 blockstate 的 `x` 旋转、再按 `y` 旋转（和原版一样的顺序与方向）→ 加方块位置。射线检测时把射线反过来变换到模型坐标里，不变换三角形。

**加载时机**：方块状态的命中形状第一次被查询时解析并缓存，之后只查表。资源重载不影响（数据来自 jar 本身，和 `mesh_shapes` 一样不随资源包变化）。

## 6. 射线检测（`MeshHitClip`）

1. 用 `BlockGetter.traverseBlocks` 沿射线逐格走（和原版 `clip` 相同的格子顺序）。
2. 每一格：
   - 方块有命中形状：把射线变换到每个零件模型的坐标里，用 BVH 求最近的三角形交点（两面都算，法线翻到朝向射线来的一侧）。只接受落在本格范围内（外扩 0.001）的交点；没有交点就继续走下一格（子弹穿过空隙）。
   - 没有命中形状：照旧用这一格的碰撞形状 `clip`。
3. 返回第一个命中。

子弹之外的代码（原版射线、准星选中）V1 不受影响。

**性能预算**：一发子弹最多走几十到一百多格，只有 AFL 方块所在的格子才做三角面检测，BVH 每次约 10–30 次包围盒测试和少量三角形测试。目标：一发子弹的额外开销小于 0.05 ms。霰弹每发多颗弹丸，各自独立检测。

## 7. 接入点（P1）

| 位置 | 改动 |
|---|---|
| `weapon/NativeGunShot#trace` | `loaded.clip(COLLIDER)` 换成 `MeshHitClip`；穿透可破坏方块（玻璃）的逻辑不变 |
| `weapon/BulletImpacts#onBlock` | 命中结果带法线时，火花、弹孔都按法线 |
| `fluid/FuelLeaks#bullet` / `puncture` | 判断"打在油桶上"改用命中网格的结果（现在用容器盒子外扩 0.02 判断）；弹孔多存一个法线 |
| `FuelLeaks.Hole` 存档 | 增加可选的法线字段；旧存档没有法线时按原来的轴向面处理 |
| `network/AflNetwork` 弹着点包、漏油同步包 | 增加法线（3 个 float） |
| `client/BulletHoles` | 贴花平面由法线算出（切平面里任取两条正交轴，再随机转角）；网格命中时不再把贴花夹在方块面的范围里；离表面抬高略多一点（圆面上 0.16 格的贴花两端会离开曲面约 0.01 格） |
| `client/ClientFuelLeaks` | 漏油弹孔贴花和油柱方向按法线 |

## 8. 打算先覆盖的方块

默认：凡是方块状态能解析到 OBJ 模型的 AFL 方块都启用（可以用一个排除清单关掉个别方块）。

第一批重点检查：钢制油壶、小油桶、油桶、手摇油泵；流体管道、耐热管道（玻璃段、法兰）；立式储罐、地下油罐；加油机、加油岛、卸油口盖。

## 9. 会改变的玩法

- 子弹会从模型的空隙里穿过去（例如货架层板之间、油桶之间的缝、提手下面），以前这些地方会被碰撞盒挡住。躲在这些方块后面的掩护效果会变弱一些，更接近看到的样子。
- 打到油桶弹孔的位置会更准，漏油油柱从桶的圆面上沿法线喷出。

## 10. 验证计划

- 开发命令（`src/dev`）：对准星所指的方块打一条测试射线，聊天栏输出命中点、法线、用到的模型和三角形数，并在世界里画出这一格的命中网格线框几秒钟。
- 静态检查：所有 AFL 方块状态都能解析（解析失败的列出来，退回碰撞盒，不崩溃）。
- 实机检查清单（用户）：油桶、管道、储罐上的弹坑是否贴在面上；子弹能否穿过空隙；油桶漏油方向；霰弹连续射击时的帧率。

## 11. 需要用户确认的问题

1. 是否默认对所有 AFL OBJ 方块启用（推荐），还是先只对第一批方块启用。
2. 子弹穿过模型空隙会改变掩护效果，是否接受。
3. 贴图透明的地方（格栅、网孔）V1 仍然挡子弹，是否可以接受（按贴图透明度判断要读贴图，开销和工作量都大很多）。
4. P2（准星选中、轮廓贴合）和 P3（碰撞盒自动拟合）是否接着做，还是先只做 P1。
