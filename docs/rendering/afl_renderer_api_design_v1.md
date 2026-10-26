# AFL Renderer API（`afl-renderer-api`）接口设计 v1

- **状态**：设计草案，Research Only。**未实现、未编码**，不修改 AFL / Vextris / Aureveil。下文的 Java 片段是接口形状示意，不是可提交的代码。
- **日期**：2026-10-26
- **上游文档**：
  - `docs/rendering/afl_independent_renderer_research_v1.md`：§5.3 场景提交、§6 DLSS、§7 AFL 需求、§8 许可证边界、§11 推荐架构
  - `docs/rendering/afl_renderer_license_inventory_v1.md`
- **许可证**：API 计划以 **Apache-2.0** 公开，渲染器核心为专有，二者分离。设计中不参考任何 GPL 或 LGPL 实现。Radiance 没有公开 API（issue #283），因此这里不存在可借鉴的对象。

---

## 1. 目标与非目标

**目标**

1. 让 AFL 的内容以**数据**形式直接进入渲染器，而不是让渲染器去"捕获" GL 绘制。这是报告二的核心结论：捕获式架构看不到 AFL 的 Vextris GPU 缓存、自有 VBO 水体和近处植物。
2. **运动矢量是一等公民**：每个会动的对象都能提供"上一帧"信息，满足 DLSS（`DLSSPG:` §3.6）和时域降噪的需要。
3. 同一份数据**既能走光栅路径，也能走光追路径**，光追只是一个可选开关。
4. 渲染器不存在时，AFL 的现有路径照常工作，API 调用全部是空操作。
5. 对第三方 Mod 开放：其他 Mod 也能按同一套接口提交 mesh、灯光和材质。

**非目标（v1 不做）**

- 不替代原版和第三方内容的通用捕获。那部分由 `afl-renderer-core` 内部负责，不对外暴露。
- 不暴露 Vulkan 对象、shader 或管线状态。API 只描述"是什么"，不描述"怎么画"。
- 不做服务端逻辑，API 只在客户端存在。
- 不承诺跨 Minecraft 大版本的二进制兼容。API 本身不依赖 MC 类型，以降低迁移成本。

---

## 2. 设计原则

| 原则 | 具体做法 |
|---|---|
| 不依赖 Minecraft 类型 | 只用 JDK 和 JOML。资源标识用 `"namespace:path"` 字符串，坐标用 `double`。AFL 现有代码的 `ResourceLocation` 与 `Vec3`，由 AFL 侧的适配层转换 |
| 保留式为主 | Mesh、材质、实例、水面、灯光都是**有句柄的对象**：创建一次，按需更新，显式销毁。每帧只传变化量（矩阵、骨骼、参数）。这与 Vextris `MeshGpuCache`"几何缓存在 GPU、每帧只给矩阵"的思路一致 |
| 历史自动保留 | 实例每次 `setTransform` 时，渲染器自动把旧值存为"上一帧"。只有发生**不连续**的情况才需要显式声明，例如传送、换模型、拓扑变化 |
| 绝不让游戏崩溃 | 非法输入只记日志并跳过该对象，不抛到游戏主循环。Radiance issue #273 正是"一个不支持的 layer 导致整个 JVM 崩溃"的反例 |
| 线程模型简单 | 所有修改都在**客户端渲染线程**上调用。数据量大的上传（mesh 顶点）可以交给渲染器后台处理，但句柄立即可用 |
| 能力可查询 | 调用方先问"支持什么"，再决定走哪条路。例如没有光追时，AFL 镜子保留现有方案 |
| 版本可协商 | `ApiVersion` 用语义化版本。小版本只增加不删除；不支持的扩展字段被忽略并记录 |

---

## 3. 模块与获取方式

```
afl-renderer-api.jar   (Apache-2.0，纯接口 + 不可变数据类 + NoOp 实现)
  └─ com.antaurora.renderer.api
       AflRenderer         入口：instance()、capabilities()、version()
       RenderScene         场景对象工厂与生命周期
       FrameHooks          帧回调注册（provider 模式）
       mesh/  material/  light/  water/  environment/  debug/
```

- **获取方式**：`AflRenderer.instance()`。
  - 渲染器核心加载后，返回真实实现。
  - 否则返回内置的 `NoOp` 实现：所有方法都是空操作，`capabilities()` 返回"全部不支持"。
- **AFL 侧**：
  - 以 `compileOnly` 依赖 API，并用 Forge JarInJar 打包 API JAR。Apache-2.0 允许这样做，于是 API 类始终存在，调用方不需要用反射。
  - 这比 AFL 现在用反射对接 Oculus 的方式（`AflShaderCompat.java:13-35`）更安全。
- **渲染器核心**：在 Forge 客户端初始化时注册实现，并检查 API 版本是否兼容。

```java
// 示意
public interface AflRenderer {
    static AflRenderer instance() { ... }          // 永不为 null
    ApiVersion version();
    Capabilities capabilities();
    boolean active();                              // 本帧是否由 AFL Renderer 负责画世界
    RenderScene scene();
    FrameHooks hooks();
}

public record Capabilities(boolean rayTracing, boolean rtShadows, boolean rtReflections, boolean rtGlobalIllumination,
                           boolean gpuSkinning, int maxBonesPerSkin, boolean upscalerDlssSr, boolean upscalerDlssRr,
                           boolean upscalerFsr, boolean motionVectors, int maxDynamicLights) {}
```

**`active()` 的含义**：只有它为 true 时，AFL 才停用对应系统的 GL 绘制（水体 VBO、`MeshGpuCache` 绘制等），改为提交数据。这样可以避免"两边都画"和"两边都不画"。

---

## 4. 坐标、时间与帧模型

- **世界坐标**：所有位置都用 `double`（世界空间）。渲染器内部转换为以相机为原点的 `float`，避免远离原点时的精度抖动。Minecraft 的世界坐标可达 ±3×10⁷。
- **姿态**：`Pose = 原点 (double x, y, z) + 3×4 float 矩阵`（旋转与缩放）。可以从 JOML 的 `Matrix4f` 加 `double` 原点构造。
- **时间**：调用方只需提交"本帧插值后的状态"，与 AFL 现有用法一致，例如 `DynamicLights.Provider.collect(..., partialTick)`。渲染器在帧开始时给出 `FrameInfo`：

```java
public record FrameInfo(long frameIndex, float partialTick, double cameraX, double cameraY, double cameraZ,
                        double gameTimeSeconds, boolean historyReset) {}
```

- **`historyReset`**：由渲染器在换维度、传送、重新加载资源时置 true。此时所有运动历史失效，DLSS 也会 `InReset`（`DLSSPG:` §3.13）。
- **帧回调**：沿用 AFL 已有的 provider 模式（`DynamicLights.Provider`），渲染器在每帧固定时点拉取数据：

```java
public interface FrameHooks {
    void onFrame(FrameListener listener);          // 世界渲染开始前：更新实例、骨骼、灯光、环境
    interface FrameListener { void update(FrameInfo frame, RenderScene scene); }
}
```

---

## 5. 几何

### 5.1 Mesh（静态几何）

```java
public interface RenderScene {
    MeshHandle createMesh(MeshData data);                  // 一次性上传，可后台进行
    SkinnedMeshHandle createSkinnedMesh(SkinnedMeshData data);
    void destroy(Handle h);
    ...
}

public record MeshData(String debugName, VertexLayout layout, java.nio.ByteBuffer vertices, java.nio.IntBuffer indices,
                       Topology topology, java.util.List<Section> sections) {}
// Section = (indexStart, indexCount, MaterialHandle)：一个 mesh 可以有多个材质段
// Topology: TRIANGLES 或 QUADS（QUADS 由渲染器拆成三角形；Vextris 在光影下用 TRIANGLES，见 MeshRendering.java:77-88）
```

- **顶点布局**：固定几种。
  - 位置为 `float3`。
  - 法线为 `float3` 或八面体编码。
  - 切线可选；不给时由渲染器生成。
  - UV 为 `float2`。
  - 颜色可选。
  - 可选的植被风权重：`float`，0 = 根部，1 = 尖端。
  - 可选的 sprite 边界：用于图集贴图的 POM 与夹取，与 NVIDIA 方案中"每顶点带 sprite UV 边界"的思路同类。
- **不支持任意自定义属性**：避免重蹈 Radiance 遇到未知格式直接抛异常的问题（`RAD:client/constant/Constants.java:117`）。
- 缓冲必须是**直接内存**（`ByteBuffer.allocateDirect` 或 LWJGL `MemoryUtil`），渲染器零拷贝读取后，调用方就可以释放。

### 5.2 SkinnedMesh（骨骼蒙皮）

```java
public record SkinnedMeshData(MeshData bindPose, int boneCount, int weightsPerVertex /* 1..4 */,
                              java.nio.ByteBuffer jointIndices, java.nio.ByteBuffer jointWeights,
                              float[] inverseBindMatrices /* boneCount × 12 */) {}
```

- **在 GPU 上蒙皮**。每帧只传骨骼矩阵，渲染器同时保存上一帧矩阵，从而得到**逐顶点运动矢量**。这解决了 AFL 现在 CPU 每帧蒙皮的两个问题：CPU 成本，以及光影包拿不到实体运动（`AUR:taa.fsh:52`）。
- **上限**：v1 为 **每个蒙皮 128 根骨骼、每顶点 4 个权重**，能覆盖 AFL 现有资产：
  - Block-Mesh V2 B：22 根骨骼、最多 4 个权重（`docs/dev/block_mesh_v2_b_ingame.md:28`）
  - 幸存者 Mesh：54 根骨骼（`docs/dev/mesh_survivor_v1_m25_ingame.md:24-26`）
  - AFL 骨骼身体：rig 16 根（`BlockMeshBody.java:62` 注释，"the poser's 16"）
- **没有 GPU 蒙皮能力时**（`capabilities().gpuSkinning == false`）：渲染器在 CPU 上蒙皮，或者由调用方改用 §5.4 的每帧顶点提交。

### 5.3 Instance（实例）

```java
InstanceHandle createInstance(InstanceDesc desc);
// InstanceDesc: mesh 或 skinnedMesh、材质覆盖（可选）、flags、Pose

interface InstanceHandle extends Handle {
    void setPose(Pose pose);                        // 自动保留上一帧，用于运动矢量
    void setBones(float[] matrices, int count);     // 仅蒙皮实例；自动保留上一帧
    void setVisible(boolean visible);
    void setTint(float r, float g, float b, float a);
    void breakHistory();                            // 传送、换模型时调用：本帧不产生运动矢量
}
```

**`flags`**：

| flag | 作用 |
|---|---|
| `CAST_SHADOW` | 投射阴影 |
| `VISIBLE_IN_REFLECTIONS` | 出现在反射中 |
| `FIRST_PERSON` | 第一人称视图模型：独立 FOV、不进入世界阴影或只投"影子身体" |
| `NO_MOTION_VECTORS` | 不写运动矢量 |
| `DOUBLE_SIDED` | 双面 |
| `DECAL` | 贴花 |

**`FIRST_PERSON` 的依据**：AFL 第一人称有自己的 FOV 和姿态处理（`ConfiguredGunFirstPerson.java:18-44`、`docs/rendering/first_person_body_v1.md`）；NVIDIA 方案也为视图模型单独设置相机（`NV:M/RemixRenderer.java:1239-1249`）。

### 5.4 每帧变化的几何（DynamicMesh）

适用于无法用骨骼表达的形变，例如近处植物的弹簧模拟（`VegetationDynamics`）和布料。

```java
DynamicMeshHandle createDynamicMesh(DynamicMeshDesc desc);   // 固定的顶点数、索引与材质
interface DynamicMeshHandle extends Handle {
    void updatePositions(java.nio.FloatBuffer positions, java.nio.FloatBuffer normals); // 拓扑不变：自动保留上一帧位置
    void replaceTopology(MeshData data);                                               // 拓扑改变：本帧不产生运动矢量
}
```

**约定**：只要顶点数和索引不变，渲染器就把上一帧位置作为运动来源。这是一种通用做法，与 Radiance 保留上一帧缓冲的思路结论相同，但这里由 API 合约保证，不靠渲染器猜测。

### 5.5 大量同类对象（InstanceBatch）

用于"大量感染者"、成片道具：

```java
InstanceBatchHandle createInstanceBatch(MeshOrSkinHandle mesh, int capacity);
// 每帧：set(i, pose)、setBones(i, matrices)、setCount(n)
```

- 同一个 mesh 的多个实例走 GPU instancing，蒙皮实例共享骨骼数据的布局。
- **目标**：100 个以上同模型感染者时，CPU 端每帧只做骨骼矩阵计算和一次批量上传。

---

## 6. 材质

```java
MaterialHandle createMaterial(MaterialDesc desc);

public record MaterialDesc(
    String debugName,
    TextureRef albedo,            // "namespace:path"，或图集 sprite 引用
    TextureRef labPbrNormal,      // LabPBR _n：RG 法线、B AO、A 高度
    TextureRef labPbrSpecular,    // LabPBR _s：R 光滑度、G F0 或金属编号、B 孔隙度或次表面、A 自发光
    AlphaMode alphaMode,          // OPAQUE、CUTOUT(阈值)、BLEND、TRANSMISSIVE
    float emissiveScale,
    WetnessResponse wetness,      // 见下
    Transmission transmission,    // 玻璃、车窗：IOR、厚度、吸收色；可选
    Subsurface subsurface,        // 叶片、皮肤：可选
    int flags) {}                 // 例如 FORCE_METAL_F0、NO_POM
```

**LabPBR 解码由渲染器统一完成，按 LabPBR 规范执行：**

- `_s.G` 在 230–254 时视为**预定义金属**（铁、金、铝等），不简化成"是 / 否金属"的二值判断。NVIDIA 方案就是简化成了二值（`NV:M/texture/LabPBRConverter.java:38`，`g >= 230`）。
- 这对 AFL 枪械的金属外观很关键：AFL 已有"裸金属 `_s` 标为电介质"的问题记录（Aureveil 提交 a26628b）。

**AFL 现有贴图的数量**：方块 `_n` 405 个、`_s` 412 个；物品和实体约 125 套（报告二 §3.3）。

**湿润（`WetnessResponse`）：**

| 字段 | 含义 |
|---|---|
| `porosity` | 孔隙度：0 = 金属、玻璃（只积水膜），1 = 土、布（吸水变暗） |
| `puddles` | 是否允许在水平面形成积水 |
| `streaks` | 是否在竖直面产生水流痕迹 |

- 全局湿润度来自 §9 的环境数据。
- AFL 枪械金属的效果：低孔隙度 + 水膜，表现为光滑度提高、出现细小水珠法线。

---

## 7. 灯光

```java
LightHandle createLight(LightDesc desc);   // 保留式；每帧可以 update
public record LightDesc(LightType type,    // POINT、SPOT、RECT、DISK、DIRECTIONAL
                        double x, double y, double z, Vector3f forward, Vector3f up,
                        float radiusOrSize, LightProfile profile, Vector3f color, float intensity, IntensityUnit unit,
                        boolean castsShadows, float volumetricScale, int flags) {}
```

**`LightProfile`（光型）：**

- 对称锥角：内角和外角。
- **非对称光型**：上、下、左、右四个方向的张角，外加远近衰减。直接对应 AFL `LightCone` 的参数：`aimDown`、`across`、`below`、`above`、`reach`、`near`、`far`、`rightReach`、`back`、`belowLoss`（`DynamicLights.java:84-110` 的 24 个 float 打包）。
- 车灯近光的截止线形状因此可以原样保留。

**强度单位：**

- `IntensityUnit` 支持 `CANDELA`、`LUMEN`，以及 **`AFL_BLOCK_LEVEL`**。最后一种是 AFL 现在使用的"方块光等级"单位，由渲染器按固定标定换算。
- 标定值在 P8 实测时确定，记录在案。
- 这样，AFL 现有 provider 的参数不需要先改写。

**其他：**

- **上限**：没有 AFL 现在的 24 盏限制（`DynamicLights.MAX_LIGHTS`）。光追下由采样策略处理（如 ReSTIR 一类方法），光栅下按簇剔除。`capabilities().maxDynamicLights` 给出实际上限。
- **遮挡点**：AFL 现在用 occluder 作为阴影射线起点（`DynamicLights.java:67-75`）。光追模式下不再需要；光栅模式下映射为阴影贴图的投射位置。
- **自发光几何**：材质的 `_s.A` 自发光和 `emissiveScale` 由渲染器自动当作面光源采样，不需要另外建灯。
- **兼容现有 provider**：AFL 适配层可以把 `DynamicLights.Provider` 的输出逐帧转成 `LightHandle` 更新。

---

## 8. 水体

```java
WaterSurfaceHandle createWaterSurface(WaterSurfaceDesc desc);
interface WaterSurfaceHandle extends Handle {
    void updateMesh(java.nio.FloatBuffer positions, java.nio.FloatBuffer flow, java.nio.IntBuffer indices, boolean sameTopology);
    void setBody(WaterBody body);   // 吸收、散射、浑浊度、泡沫阈值
}
public record WaterBody(Vector3f absorption, Vector3f scattering, float turbidity, float foam, float ior, WaveSet waves) {}
```

**为什么独立建模：**

- AFL 物理水不是方块，而是一张连续 mesh：每个湿格一个四边形，角点取相连格水位的平均（`ClientLiquid.java:51-73` 注释），外加侧面和水膜。
- 现有 `afl_water` 着色器已经按 Beer–Lambert 吸收与散射来渲染浑水（提交 437b4ca3 说明）。
- `WaterBody` 直接承接这些参数。

**数据约定：**

- `positions`：水面顶点（世界坐标的 `double` 原点加 `float` 偏移）。
- `flow`：每顶点流向与流速，用于法线滚动、泡沫、尾迹。
- `sameTopology = true` 时，渲染器用上一帧位置生成运动矢量；水位升降也能得到正确运动。

**其他：**

- **相机在水下**：由 §9 环境数据给出介质，渲染器切换水下体积效果。
- **瀑布、水帘、水膜**（`ClientFalls.java`）：作为同一种水材质的 DynamicMesh 提交。
- **透明度与 DLSS**：水面作为 `TRANSMISSIVE` 层，有专门的运动矢量处理。报告二 §6 第 5 条指出，半透明层处理不当会拖影。

---

## 9. 环境与全局参数

```java
interface Environment {
    void setWind(WindState wind);          // 方向、强度、阵风、湍流、时间
    void setWeather(WeatherState w);       // 降雨、降雪强度，全局湿润度，积水程度
    void setLightning(LightningFlash f);   // 位置、强度、持续时间；可以同时创建临时灯光
    void setCameraMedium(Medium m);        // AIR、WATER(WaterBody)、LAVA、POWDER_SNOW
}
```

**风（植被）：**

- AFL 的风现在只存在于 GLSL 中：Embeddium 顶点着色器（`VegetationWind.java`）或 Aureveil。CPU 侧有 `VegetationWindMath` 作为副本。
- API 只传**风的状态**。每顶点的风权重随 mesh 提交（§5.1），风函数在渲染器 shader 中求值。
- 渲染器**在当前帧与上一帧两个时间点各算一次**，得到植被的运动矢量。
- 风函数的形式由 AFL 与渲染器共同约定，计划以 `VegetationWindMath` 为基准，P8 设计评审时确认。

**天气与湿润：**

- AFL 已经在给 Aureveil 提供天气数据：`afl_rain_*`、`afl_glass_*`、`afl_vegetation`、`afl_lightning` 等 customTexture（`Aureveil:shaders.properties:213-281`）。
- API 用结构化参数取代纹理打包，内容来源相同：`RainShaderPack`、`VegetationShaderData`、`LightningShaderData`。

---

## 10. 与 AFL 现有系统的对应关系

以下只是设计映射，**不实施**。

| AFL 系统（现有代码） | 现在的绘制方式 | 改用 API 后 | 改动点（以后由 AFL 侧做） |
|---|---|---|---|
| 原生枪械 / 弹匣 / 手持 Mesh 物品（`NativeGunContextRenderer.java:144-160`、`MeshItemCache.java:58`、`MaintenanceGunRendering.java:260`） | `MeshGpuCache.drawBone(mesh, bone, CUTOUT, ALL_PARTS, texture, pose, light, overlay, r, g, b, a)` | 每个（mesh, bone, layer）调用一次 `createMesh` 并缓存；每帧对每根骨骼 `setPose`。第一人称加 `FIRST_PERSON` | 在 `drawCached` 处增加分支：`AflRenderer.instance().active()` 时提交实例，不再调用 `drawBone` |
| T15 皮卡（`PickupTruckRenderer.java:86-412`） | pass 1–2 走 `MeshGpuCache`；玻璃走 `AflMovingGlass`；车灯辉光 | 车体：刚体实例。玻璃：`TRANSMISSIVE` 材质。车灯：`SPOT` 灯光（`TruckLights`）加自发光材质 | 同上；`TruckLights` 改由适配层转成 `LightHandle` |
| 道路 / 桥 / 隧道（`RoadClient.java:469-556`、`TunnelFixtureClient.java:244-260`） | `GpuGeometry.build/draw` + light stream | 每个区块一个静态 mesh 实例；不再需要 light stream（光照由渲染器计算） | 在 `GpuGeometry` 调用处分支 |
| Block-Mesh V2 / 骨骼身体（`PlayerSkeleton.java:402-444`、`BlockMeshBody.java:217-260`） | CPU LBS，写入原版 entity consumer | `createSkinnedMesh`（rig 的 bind pose 加权重）；每帧 `setBones` | 把 `BlockMeshBody.matrices(...)` 的结果交给 API，跳过 `emit` |
| Mesh 幸存者（dev，`MeshBodyTpTest.java:306-390`） | Vextris `renderSkins`（CPU） | 同上，54 根骨骼 | 同上 |
| 感染者（规划中，大量） | — | `InstanceBatch` + 蒙皮 | 新系统直接按 API 设计 |
| 物理水 / 瀑布（`ClientLiquid.java:358-518`、`ClientFalls.java:667-779`） | 私有 BufferBuilder → `VertexBuffer.drawWithShader` | `WaterSurfaceHandle`（每个区块一个），加 `WaterBody` | 构建水面时，把同一份顶点交给 API |
| 近处植物（`VegetationDynamics.java:110-624`） | 自有 VertexBuffer + 自有程序 | `DynamicMesh`（拓扑不变，逐帧更新位置） | 同上 |
| Mesh 植物 / 树（`PlantModel`、`TreeCellModel`、`TreeLogModel`） | 区块 BakedQuad；风在 GLSL 中 | 静态几何仍走区块路径（渲染器核心捕获 Forge ModelData）；需要额外提供**每顶点风权重** | 风权重编码方式待定：沿用 sprite 行位置，或新增顶点属性 |
| 平滑地形（`SmoothSurfaceModel.java`） | 区块 BakedQuad | 不变，由渲染器核心的区块捕获处理 | 无 |
| 动态灯光（`DynamicLights.Provider`：`TruckLights`、`FlipLighterClient`、`AflLightning`） | 24 盏，打包进 GL 纹理 | `LightHandle`（`AFL_BLOCK_LEVEL` 单位 + 非对称 `LightProfile`） | 新增一个"provider 到 API"的转接器，provider 本身不动 |
| 雨（`RainRenderer.java`、`RainShaderPack`） | 静态 VBO + 顶点着色器；或交给光影包 | `Environment.setWeather`；雨滴由渲染器自己的粒子或屏幕效果处理 | 同上 |
| 镜子（`MirrorReflection.java`） | 离屏第二世界 pass | 有光追时不需要；没有时保留现有方案 | 按 `capabilities().rtReflections` 选择 |
| 闪电（`LightningRenderer`、`LightningShaderData`） | 自有几何 + 数据纹理 | 几何：DynamicMesh（自发光）。光照：`Environment.setLightning` + 临时灯光 | 同上 |

---

## 11. 生命周期与错误处理

| 事件 | 渲染器行为 | 调用方义务 |
|---|---|---|
| 资源重载（F3+T） | 销毁所有材质和纹理引用并重新解析；mesh 保留 | 通过 `onResourceReload` 回调重新创建自己的材质 |
| 换维度 / 退出世界 | 清空场景中的所有实例、水面和灯光；`historyReset = true` | 不再使用旧句柄。旧句柄失效后调用会被忽略，不会崩溃 |
| 显存不足 | 按优先级降级：先降远处 LOD、关反射，再拒绝新建 | 检查 `create*` 的结果；失败时回退到现有绘制路径 |
| 非法数据（NaN、索引越界、骨骼数超限） | 记日志，跳过该对象，本帧不画它 | — |
| 渲染器在运行中被关闭（设置中关闭 RTX） | `active()` 变为 false；保留式对象可以保留或释放（实现决定） | 下一帧起回到现有绘制路径 |

---

## 12. 调试与统计

- 每个对象都有 `debugName`，在渲染器的调试界面中可见。
- `scene().stats()`：每帧实例数、三角形数、上传字节数、骨骼数、灯光数、BLAS 更新数。
- **运动矢量可视化**：渲染器提供调试视图。这是报告二 §9 P6 的验收手段：静止时 MV ≈ 0，平移和旋转时方向正确。

---

## 13. 版本与兼容

- `ApiVersion(major, minor)`：
  - `major` 改变表示不兼容：渲染器拒绝加载不匹配的 API，并只记录一次警告。
  - `minor` 只增加方法、字段和枚举值，旧调用方不受影响。
- 新增字段采用 builder 风格的数据类，调用方不需要的字段保留默认值。
- **第三方 Mod**：只依赖 `afl-renderer-api`，使用前用 `capabilities()` 判断能力。不鼓励 Mixin 进渲染器核心，那属于专有实现，不保证稳定。

---

## 14. 待定问题（P8 设计评审时决定）

1. **植被风权重的编码**：沿用"sprite 内纵向位置"推断（现有 `VegetationWind` 的做法），还是在 BakedQuad 之外增加一个顶点属性。后者需要动区块构建流程。
2. **`AFL_BLOCK_LEVEL` 到物理单位的标定**：用哪个参考场景、参考亮度。
3. **蒙皮上限**：128 根骨骼够不够将来的角色（含手指，`docs/dev/hand_fingers_feasibility_v1.md`）。
4. **区块级 AFL 内容**（道路、隧道）：由 AFL 按区块提交实例，还是让渲染器核心直接识别 AFL 的区块数据。前者解耦更好。
5. **第一人称**：枪械和手臂是否共用一个 `FIRST_PERSON` 相机，光追阴影是否需要"影子身体"。
6. **API 包名与 Maven 坐标**：本文用的 `com.antaurora.renderer.api` 只是占位。
7. **API 许可证**：最终选 Apache-2.0 还是 MIT，需要与许可证清单一起定稿。

---

## 15. 与 PoC 阶段的关系

| PoC 阶段（报告二 §9） | 本 API 的哪一部分 |
|---|---|
| P1 动态三角形 | 无，渲染器内部 |
| P2 PBR 方块 | §6 材质（LabPBR 解码） |
| P4 动态实体与自定义 Mesh | §5.1–5.3：用只读拷贝的 T15 mesh 与 Block-Mesh B 做第一版原型 |
| P6 深度与运动矢量 | §5.3 `setPose` / `setBones` 历史、§5.4 DynamicMesh、§12 MV 调试视图 |
| P8 AFL 专用 Mesh 评估 | §8 水体、§9 环境、§10 映射表、§14 待定问题 |
