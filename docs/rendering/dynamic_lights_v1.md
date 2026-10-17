# 动态光源 V1：逐像素车灯光（不开光影时）

状态：**不开光影：已实现（2026-10-17），`compileJava` 通过，离线 GLSL 检查全部 PASS（NVIDIA RTX 3080 Ti 驱动 596.49，GL 3.2 core），用户启动不崩，亮度、阴影、帧数还没反馈。光影包接口（第 3.1 节）：AFL 这边已实现（2026-10-17，`compileJava` 通过，没在游戏里跑过），Aureveil 那边由 Aureveil A 做（alpha.46，还没出）。** 开 Aureveil 并打开它的选项 `AFL_VEHICLE_LIGHTS` 时由 Aureveil 画车灯；开 Sundial、别的包，或者关掉这个选项时，车灯照旧用方块光（`client/vehicle/HeadlightField`）。

## 1. 为什么要做

用户 2026-10-17："我就是想做柔和光照，现在那个毛刺三角形又多，而且又卡，这个需要很正经的去做，因为后面我们如果要做战术手电或者家具的光柱，同样需要这个"。

原来的车灯光是客户端算出来的方块光（第一辆车设计文档第 8.10、8.13、8.14 节）：
- 只能一格一格地变，开车时光斑一跳一跳；
- 方块光按顶点插值，斜坡和平滑地形上是一块块亮三角形（"毛刺三角形"）；
- 每次光变了都要重建区块网格；Embeddium 对镜头 16 格以内的区块段是在渲染线程上等着重建的（第 8.14 节末），所以一边开车一边卡。

## 2. 做法

每一帧在着色器里按聚光灯算：灯的位置用这一帧的插值姿势（partial tick），所以光斑跟着车平滑移动；不改方块光、不重建区块。

**光型**：和 `vehicle/HeadlightBeam` 完全同一个公式（离线逐格比过，最大差 0.0000 级）：
- 近处（`nearDistance`，车灯 4 m）满亮；到 0.7 倍射程按距离的对数降到远处亮度；再平滑降到射程处为 0；
- 左右：乘 1 − 占比²（占比从灯后 `back` 米处量，车灯 2.5 m）；
- 瞄准线以上：乘 1 − 占比²（柔和的上截止线）；以下：往下最多损失 `belowLoss`（车灯 0.4）；
- 近光右边缘射程多 15%。
- 单位是"方块光等级"，几盏灯按颜色通道相加，然后走原版方块光曲线：x = 等级 / 15，x / (4 − 3x)，× 1.5，再按亮度选项做原版的 gamma 提升，× 0.96，最后和这个像素原来的光照图颜色做"滤色"叠加：1 − (1 − 原光)(1 − 车灯光)。白天露天时原光接近 1，车灯几乎看不出（和方块光时一样）。

**朝向**：面的法线用位置的屏幕导数算，朝镜头翻正；背对灯的面不受光，在法线和灯的夹角接近 90° 时用一小段平滑过渡（不是硬边）。

**阴影**：每个像素从自己（沿法线抬 6 cm）往灯那一端的"遮挡原点"走一遍方块格（3D DDA），碰到实心就算在阴影里：
- 方块格：镜头周围 128 × 128 × 128 格的一张 R8 3D 贴图（`client/light/OccupancyGrid`），按世界坐标 & 127 环绕存放，以 16³ 的区块段为单位上传，跟着镜头走（8 × 8 × 8 个区块段）。
- 每格的值：0 空（空气、植物、液体、没有碰撞箱的、薄的或不完整的形状）；255 整块（实心渲染方块或整块碰撞箱）；1–254 从底面实心到 (v − 1) / 253 格高（下半砖、道路铺层：铺满整格、贴底的碰撞形状）。平滑地形（terrainsmooth）的自然地表按它最低的那个角算实心高度，所以被照的坡面自己不会挡住自己。
- 新进入范围的区块段马上清空（当作空）再排队重建，每帧最多花 1 ms，离镜头近的先建；方块改动（`ClientLevel#sendBlockUpdated`）和区块加载会让相关区块段插队重建（平滑地形会读邻格，所以改动处 x、z ±1、y −2…+1 都算）。
- 同一端的灯共用一个遮挡原点（车头中间、车尾中间），每个像素每端只走一次；灯按遮挡原点排好序。
- 每条最多走 160 格；出了方块格范围算不挡；起点所在的格子不算。

**上限**：每帧最多 24 盏灯、8 个遮挡原点。

## 3. 接到哪些程序里

都只在不开光影时用；这一帧的关卡渲染（`AFTER_SKY` 到 `AFTER_LEVEL`）之外灯数是 0（手里的东西、界面里的实体不受影响）。

| 程序 | 怎么接 | 位置从哪来 |
|---|---|---|
| Embeddium 地形 `sodium:blocks/block_layer_opaque`（所有 pass） | mixin `client.DynamicLightEmbeddiumShaderMixin` 改 GlShader 交给 GL 的最终源码：顶点阶段多输出 `afl_Pos`（相对镜头的位置）、`afl_Tint`（顶点色）、`afl_Lm`（光照图颜色），片元阶段用它们代替 `v_Color`；uniform 在 `ChunkShaderInterface#setupState` 之后上传（`client.DynamicLightEmbeddiumStateMixin`） | 顶点阶段传下来，精确 |
| 原版实体 `rendertype_entity_solid / cutout / cutout_no_cull / cutout_no_cull_z_offset / smooth_cutout / translucent` | mixin `client.DynamicLightProgramMixin`：原版预处理之后、交给 GL 之前，只改片元阶段，把 `color *= lightMapColor;` 换成加了车灯的光照图；没有新的 varying，所以 Vextris 的姿势孪生程序（自己的顶点阶段 + 原版片元阶段）照样能链接 | 从深度还原（`afl_ClipToWorld` = (投影 × 模型视图 × 关卡姿势)⁻¹），离线量地面 120 m 内误差 0.2 mm |
| 道路网格（C3，`client/road/RoadRenderTypes`） | 原版 `rendertype_solid` 把光照图乘进了顶点色，Vextris 的地形孪生程序又共用它的片元阶段，不能加 varying；所以道路在画车灯的帧里改用 AFL 自己的 `apocalypse_firstlight:afl_dynlit_solid`（`client/light/DynamicLitPrograms`，`RegisterShadersEvent` 注册），别的时候（包括开光影）还是原版的 | 顶点阶段从裁剪坐标还原；雾的距离也按它算（球形、柱形雾都精确） |

- uniform 在程序绑定时上传：原版程序在 `ShaderInstance#apply` 之后（`client.DynamicLightShaderApplyMixin`），每个程序每个"帧状态"（关卡内 / 关卡外）只传一次灯数据；方块格贴图每次绑定到第 15 号纹理单元（原版用 0–11）。
- 光照代码只有一份：`assets/apocalypse_firstlight/shaders/include/afl_dynamic_light.glsl`，由 `client/light/DynamicLightShaders` 插进上面三处。每个改过的阶段先单独编译一次，编译不过就用原来的源码（日志里有 `AFL dynamic lights: ... does not compile`）。
- **只有 Embeddium 地形改成功了才打开**（`DynamicLights.active()` = 开关开 && 地形阶段改成功且没有失败 && 没开光影包）；没打开时一切照旧（车灯方块光、尾灯地面贴片）。

### 3.1 光影包接口（Aureveil A 2026-10-17 定的方案）

光影包自己画车灯：AFL 只出数据，不用 mixin，也不加 uniform。

- **开关**：光影包里的布尔选项 `AFL_VEHICLE_LIGHTS`（Aureveil：光照与阴影页，默认开）。AFL 每帧用反射读 Oculus：`Iris.isPackInUseQuick()`、`Iris.getCurrentPack()` → `ShaderPack#getShaderPackOptions` → 先 `OptionSet#isBooleanOption`（直接读没有的选项 Oculus 每次都打警告），再 `OptionValues#getBooleanValueOrDefault`。改选项时 Oculus 会重新载入包（新的 ShaderPack 对象），所以按包对象缓存结果。`DynamicLights.packActive()` = 开关开 && 有光影包 && 这个选项开。
- **两张贴图**（`client/light/ShaderPackLights`）：普通的 `AbstractTexture`，在客户端初始化时（`TruckLights` 的 `FMLClientSetupEvent`）注册进 TextureManager；每帧开头检查一次，被换掉了就重新注册。光影包在 shaders.properties 里写 `customTexture.aflLightTex=apocalypse_firstlight:dynamic/afl_lights` 和 `customTexture.aflOccTex=apocalypse_firstlight:dynamic/afl_occupancy`；Oculus 每次绑定都按名字查 TextureManager，所以 AFL 换贴图、重建贴图都没问题。两张都设成最近点采样、只有一层（MAX_LEVEL 0）、边缘夹紧，否则 texelFetch 读出来是 0。上传前把 GL_UNPACK_* 恢复成默认值，用完复原；绑定用原生 GL 记下再还原当前单元的 2D 贴图，这样 GlStateManager 缓存的绑定状态仍然准确。
  - `afl_lights`：RGBA32F，6 × 33。第 0 行是头：像素 0 = (灯数, 遮挡原点数, 0, 4242)，4242 是"装了 AFL"的校验值；像素 1 = (网格最小方块 − 镜头方块).xyz, w = 网格已放好时为 1；像素 2 = 镜头方块 & 127；像素 3 = 镜头方块里的小数部分。第 1–8 行：遮挡原点（像素 0，相对镜头）。第 9–32 行：每行一盏灯，像素 0–5 = 第 2 节那 6 个 vec4，按遮挡原点排序。没有灯的帧，头里灯数写 0（只在从有灯变成没灯时传一次）。
  - `afl_occupancy`：R8，2048 × 1024，值和 3D 方块格一样。g = 世界方块坐标 & 127，放在像素 (g.x·16 + (g.z & 15), g.y + 128·(g.z >> 4))；一个区块段正好是一块 256 × 16 的矩形，一次 `glTexSubImage2D` 传完，数据顺序是 (y·256 + x·16 + z)。`OccupancyGrid` 按当前模式只填一种排布（不开光影填 3D，光影包填平铺）；换模式、或者贴图被重建了，就清空整个窗口重新填。
- **坐标**：都相对 `Camera#getPosition()`，用同一个 partialTick，这也是光影包的场景原点。
- **时机**：每帧 `AFTER_SKY` 收灯、更新方块格、写灯表。Oculus 的阴影 pass 在这之后才画，Aureveil 也不在阴影 pass 和 prepare 里读这两张贴图；地形、实体、手、deferred 都在这之后读。手里的东西也会被车灯照到（由 Aureveil 决定）。
- **打开时**：`DynamicLights.perPixel()`（= 不开光影时的 `active()` 或 `packActive()`）为真，`HeadlightField` 不投方块光，`HeadlightFx` 不画尾灯地面贴片；光束锥照画。AFL 自己的程序这时不用（`drawing()` 为假，道路用原版程序，也就是交给光影包）。
- 离线检查：平铺排布按 AFL 的上传矩形和数据顺序算出的像素，和 Aureveil 的采样公式逐格比对（10 × 5 × 10 个区块段，包括负坐标，共 2,048,000 格），全部一致。

## 4. 现在的灯（T15）

`client/vehicle/TruckLights`（每帧，离镜头 128 格内最近的 3 辆车，每辆最多 8 盏）：

| 灯 | 光型 | 颜色 | 什么时候亮 |
|---|---|---|---|
| 近光 ×2 | `HeadlightBeam.LOW` | 暖白 (1.0, 0.93, 0.82) | 开大灯 |
| 远光 ×2 | `HeadlightBeam.HIGH` | 暖白 | 开远光 |
| 倒车灯 ×2 | `HeadlightBeam.REVERSE`，朝后 | 白 (1.0, 0.97, 0.92) | 倒挡 |
| 尾灯 ×2 | 下瞄 20°、左右各 70°、射程 4.5 m、近处 3.0 级 | 红 (1.0, 0.10, 0.06) | 开大灯 |
| 刹车（代替尾灯） | 同上，射程 5.5 m、近处 7.0 级 | 红 | 踩刹车 |

- 灯的位置和 `HeadlightField` 一样（灯罩外面一点）；尾灯在车后 2.34 m、左右 0.77 m、高 0.86 m。遮挡原点：车头前 2.36 m、车尾后 2.40 m 的中间。
- 打开时（不开光影，或光影包打开了 `AFL_VEHICLE_LIGHTS`）：`HeadlightField` 不再投方块光（上一次的光场清掉一次）；`HeadlightFx` 不再画尾灯的地面红光贴片；光束锥（雾里的光柱）照画。
- 以后的战术手电、家具光柱：写一个 `DynamicLights.Provider`（`DynamicLights.register`），每帧用 `Lights#occluder` 要一个遮挡原点、用 `Lights#spot` 加灯（`LightCone`：瞄准、张角、射程、亮度、颜色）。

## 5. 调试

- 开发环境命令（只在开发环境注册）：`/afl_render dynamic_lights on | off | status`。`off` 回到方块光，方便对比；`status` 显示开没开（光影包画的时候显示 `on (shader pack)`）、改了几个阶段、这一帧几盏灯、方块格建了多少区块段和上一帧花的时间。

## 6. 检查（2026-10-17）

- 用户第一次启动（2026-10-17）：`client.DynamicLightEmbeddiumShaderMixin` 应用失败、游戏崩溃（"'static' modifier of handler method does not match target"：注入点在 `GlShader` 的构造函数里，处理方法却写成了 static）。改成实例方法后重新 `compileJava` 通过；用户再次启动不再崩溃（"修好了"），亮度、阴影、帧数还没有反馈。
- `compileJava` 通过；mixin 映射表里有 `Program#compileShaderInternal`、`ShaderInstance#apply`、`ClientLevel#sendBlockUpdated`（Embeddium 的两个是 `@Pseudo`、`remap = false`、`require = 0`）。
- 离线 GLSL 检查（草稿目录 `glsl/GlslCheck.java`，隐藏窗口 + GL 3.2 core，按 Embeddium 的 ShaderParser 和原版预处理器拼源码，再走游戏里同一套补丁代码），全部 PASS：
  - Embeddium 地形两套选项（压缩顶点 + 雾 + 片元丢弃；原版颜色格式）：顶点、片元都改了，编译、链接通过，灯的 uniform 都在；
  - 六个原版实体片元阶段和原版顶点阶段链接通过；道路程序链接通过；`rendertype_solid` 不改；
  - 光型对 `HeadlightBeam`（一盏近光，769 格）：最大差 0.0000 级；灯前 4 m 6.55 级、20 m 4.11 级、30 m 3.25 级；
  - 阴影：墙后 32/32 格暗，墙边 16/16 亮，墙前 36/36 亮；半砖顶上的点不挡，紧贴半砖后面的低点在阴影里；
  - 从深度还原位置：地面 120 m 内高度差最多 0.0002 m，60 m 内法线误差 0；
  - 开销：1920 × 1080 整屏地面、8 盏灯（两端两个遮挡原点）、41% 的像素被照到，一遍 0.23–0.34 ms（3080 Ti；别的显卡没量）。

## 7. 没做 / 已知

- **没在游戏里看过**：亮度、颜色、阴影边、地形接缝、斜坡、帧数都要用户实测。尾灯 / 刹车的亮度是估的。
- 只有 NVIDIA 驱动编译过；AMD / Intel 的驱动更严，日志里如果有 `does not compile`，那个程序会自动退回原样（地形退回时整个功能关掉、回到方块光）。
- 不受车灯照的东西：原版地形程序（没装 Embeddium 时）、BER 里用 `RenderType.solid()` 等方块渲染类型画的东西、Vextris 地形孪生程序画的东西（道路之外）、粒子、天气、水下雾。
- 方块格只认整块、贴底铺满的部分方块和平滑地表；栅栏、墙、薄板、玻璃、树叶不挡光（光会穿过去）。范围是镜头周围 ±64 格，更远的东西不挡。
- 阴影边是方块的直边（灯近似一个点），没有半影。
- 开 Sundial 或别的光影包时还是方块光（卡、一格一格）。
- 光影包接口（第 3.1 节）AFL 这边没在游戏里跑过；Aureveil 那边的 alpha.46 还没出，在它出来之前开 Aureveil 时仍然是方块光（没有这个选项）。
- 多人：别的玩家的车灯也在自己客户端上算（灯的开关是同步的），没试过。
