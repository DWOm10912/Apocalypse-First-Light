# RTX Remix for Minecraft 0.1.0 → AFL（Forge 1.20.1）逆向移植可行性调查

状态：Research Only，调查报告与 PoC 设计。未修改 AFL / Aureveil / Vextris 任何代码，未编码；本文件为研究分支上的调研文档。
日期：2026-10-26

## 0. 调查范围与证据来源

| 简称 | 来源 | 版本 |
|---|---|---|
| `NV:` | https://github.com/NVIDIAGameWorks/rtx-remix-minecraft | tag `v0.1.0` = `170197a`。分析在 HEAD `f2c6fda` 上进行，与 v0.1.0 仅差 `build.gradle` 2 行测试属性（`git diff --stat v0.1.0..HEAD`） |
| `M/` | `NV:src/main/java/com/nvidia/rtxremix/minecraft/` | 267 个 Java 文件，57,765 行，其中 162 个文件 import `net.minecraft` |
| `J/` | `NV:rtxremix-java/src/com/nvidia/rtxremix/` | Java 外观层 3,978 行；`remixbridge.cpp` 2,006 行；`remix_c.h` 1,342 行 |
| `AFL:` | `/home/user/Apocalypse-First-Light`（研究分支，未改动） | Forge 47.4.22 / MC 1.20.1 / Java 17 |
| yarn / intermediary | `FabricMC/yarn` 分支 1.21.1、1.20.1；`FabricMC/intermediary` | 用于逐个比对 Mixin 目标在 1.20.1 是否存在 |
| Forge 1.20.1 | `MinecraftForge/MinecraftForge` 分支 1.20.1（sparse checkout） | 用于确认 Forge 事件与补丁 |

验证方式与局限：

- 本容器是 Linux，没有 GPU，也没有 MSVC。Mojang 和 Fabric 的 Maven 被代理拒绝（403），所以**无法编译或运行任何 Minecraft / Forge 环境**。
- 下文所有"存在 / 不存在 / 签名变化"的结论都来自映射文件和源码比对，不来自运行。
- `--release 17` 编译测试只做了 `rtxremix-java`。
- Mojang 官方名（mojmap）没有拿到映射文件。下文中的 mojmap 类名是常识对照，标注"需在 MDK 中核对"。存在性证据来自 intermediary（跨版本稳定 ID）。

---

## 第一部分：源码与架构

### 1.1 三层结构

```
Fabric Mod（M/，Minecraft 1.21.1 专用，57.8k 行）
   │  捕获地形 / 实体 / 物品 / 粒子 / 天空 / 灯光 → Remix 资源
   ▼
rtxremix-java（J/，与 Minecraft 无关的 Java 外观层 + JNI）
   │  RemixAPI → RemixBridge(static native) → remixbridge.dll
   ▼
d3d9.dll = dxvk-remix 运行时（私有 Minecraft 分支构建，Vulkan 路径追踪）
```

- 外观层明确规定"must never import net.minecraft or net.fabricmc"，并由 `RemixJavaBoundaryTest` 强制（`NV:rtxremix-java/README.md`；`NV:AGENTS.md` "Integrated JNI Wrapper"）。

### 1.2 Runtime 是什么

- **版本锁定**：`NV:packman/remix-sdk.packman.xml:3,6` 锁定 `rtx_remix_runtime` 的 `minecraft-rtx2-71a581da-win64-release` 和 `…-arm64-release`。
- **C API 版本**：`REMIXAPI_VERSION 0.16.0`（`NV:rtxremix-java/native/include/remix_c.h:66-68`）。
- **与公开版本的关系**：
  - 子任务于 2026-10-26 抓取了公开 `dxvk-remix` main 的 `remix_c.h`，版本是 **0.6.6**。
  - 仓库自己说 header "can be ahead of dxvk-remix main"（`NV:AGENTS.md`）。
  - 结论：**这不是公开的 RTX Remix 1.x Runtime，而是 NVIDIA 为 Minecraft 维护的私有分支构建**，只能通过 NVIDIA Packman 或官方 Release JAR 获得。
- **私有扩展**：0.16.0 相对公开 API 多出以下内容，并且 `MaterialInfoOpaqueEXT` 与公开版 ABI 不兼容：
  - struct type 28–31（`remix_c.h:146-149`）
  - `UpdateTextureFromMemory` / `Submit` / `SceneInstance*` / `UpdateMaterial` / `GetConfigVariableResolved` / 配置层（`remix_c.h:1135-1154`）
  - GL 互操作合约 `remixapi_dxvk_InteropInfo`（`remix_c.h:1052-1097`）
  - `PresentInfo` 的 4 个新字段（`remix_c.h:975-983`）
- 缺少 `UpdateTextureFromMemory`、`UpdateMaterial`、`GetConfigVariableResolved` 的 runtime 会被 bridge 以 `INCOMPATIBLE_VERSION` 拒绝（`remixbridge.cpp:535-553`）。

### 1.3 rtxremix-java / JNI / DLL 调用流程

1. `NativeExtractor.extractIfNeeded()` 把 JAR 内的 `native/remix-sdk-win64/` 解压到 `<gameDir>/remix-sdk/`（`M/RtxRemixMod.java:342`；`M/NativeExtractor.java:156`）。
2. `RemixRenderer.initialize()` 用 `GLFWNativeWin32.glfwGetWin32Window` 取 HWND（`M/RemixRenderer.java:229-232, 498-503`）。DLL 路径固定为 `<gameDir>/remix-sdk/d3d9.dll`（`:505-511`）。
3. `RemixAPI.load` 用 `System.load(remixbridge.dll)`（`J/RemixAPI.java:114-140`）。随后 native 端执行：
   - `SetDllDirectoryW` 加 `LoadLibraryW(d3d9.dll)`；
   - `GetProcAddress("remixapi_InitializeLibrary")`；
   - 版本协商（`remixbridge.cpp:499-561`）。
4. `remixapi_Startup(hwnd, interopImage=true)`（`M/RemixRenderer.java:298-302`）。Remix 在同一 HWND 上自建 `VkSwapchainKHR`。**不创建 D3D9 device**：D3D9 包装函数存在（`remixbridge.cpp:1322-1500`），但 `src/main/java` 中没有调用者。
5. `dxvkGetInteropInfo()` 加 `new GlInterop(info)`：把 Remix 的颜色图、深度图和 timeline semaphore 通过 Win32 handle 导入 OpenGL（`M/RemixRenderer.java:310-323`；`M/gl/GlInterop.java`）。

### 1.4 各功能真正负责的代码

| 功能 | 关键代码（file:line） | 机制 / 调用的 Remix API |
|---|---|---|
| **地形几何捕获** | `M/mixin/SectionBuilderMixin.java:86-195`（HEAD/RETURN `SectionBuilder.build`；@Redirect `isOpaqueFullCube`、`getRenderType`、`renderFluid`）→ `M/terrain/capture/BlockQuadEmitter.java:269,318,358,544-640` → `DirectQuadWriter` → `SectionMeshData`（堆外、引用计数）→ `BlockDataQueue` → `M/terrain/ChunkManager.java:607-760,1215-1315,1405-1500` → `BlockMeshBuilder.buildColumnMesh`（`M/terrain/BlockMeshBuilder.java:77-160`） | Section 在 worker 上捕获，按"列 × 类别"合并成上限 8 MiB 的页。渲染线程在帧预算内调用 `createMesh` 和 `sceneInstanceAdd`（先 HIDDEN，再 `sceneInstanceUpdate` 激活），旧页走退役队列 `sceneInstanceRemove` / `destroyMesh`。原版 GL 光栅化被关闭：`getRenderType` 返回 INVISIBLE（`SectionBuilderMixin.java:138-145`），`BuiltChunkBuffersMixin` 跳过 VertexBuffer |
| **实体 Mesh 提交** | `M/mixin/WorldRendererMixin.java:351-399`（@WrapOperation `renderEntity`）→ `M/entity/RemixEntityPass.java:250-336` → `RemixVertexConsumerProvider` / `RemixCapturingConsumer`（局部顶点 + 记录 pose 作"bone"）；`CuboidMixin`（`M/mixin/CuboidMixin.java:38-76`）按 Cuboid 身份捕获；`M/entity/RemixEntitySubmitter.java:785-903,1183-1240,1385-1466` | **刚体部件**：每个（owner, category, layer, part）一个池化 mesh 和一个 retained instance，变换在 CPU 上组合（`composeRigidTransform :2743-2790`）。**流式部件**（拴绳、光束）每帧新建 mesh（`createTransientMesh :1952-1977`），下一帧销毁 |
| **动态物品 Mesh** | `M/mixin/ItemRendererMixin.java:36-77`、`M/mixin/HeldItemRendererMixin.java:38-86`；第一人称：`M/mixin/GameRendererMixin.java:33-53` → `M/entity/RemixFirstPersonPass.java:75-166` | 走同一实体提交路径。第一人称使用 `VIEW_MODEL` 类别，外加第二次 `setupCamera(VIEW_MODEL)`（`M/RemixRenderer.java:1239-1249`） |
| **材质** | `M/texture/BlockTextureManager.java:247-674`（图集捕获、atlas 级 normal/roughness/metal/emissive/SSS 图、5 种层材质）；`M/texture/LabPBRConverter.java:33-178`；实体材质 `RemixEntitySubmitter.java:2089-2668` | `createTextureFromMemory`、`createMaterial`、`updateMaterial`、`updateTextureFromMemory`（动画精灵，`AtlasAnimationForwarder.java:422`）。每个顶点带 sprite UV bounds（`J/HardcodedVertex.java:17-31`），使 atlas 级材质的 POM 不越界 |
| **相机与深度** | `M/RemixRenderer.java:1190-1256` | 只提交参数化相机（位置、forward/up/right、FOV、aspect、near 0.05、far 1000），**不提交矩阵，不提交 jitter**（`grep jitter` 无结果）。**GL 深度从不送给 Remix**。深度只按反方向流动：Remix 深度导入 GL，给雨雪 pass 用（`M/weather/RemixWeatherRenderer.java:296-317`） |
| **运动矢量** | 无专门代码 | `remixapi_InstanceInfo` 只有 `{categoryFlags, mesh, transform, doubleSided}`（`remix_c.h:690-697`），**API 没有"上一帧变换 / 上一帧顶点"字段**。运动历史由 runtime 根据 retained instance 句柄的连续性自行推导。注释佐证："using Remix skinning here … makes instance motion history harder to track"（`M/entity/RemixEntitySubmitter.java:82-84`）；"current/previous perspective correction at identity for stable motion vectors"（`M/entity/RemixFirstPersonPass.java:49-55`） |
| **光照** | 地形灯：资源包规则 `rtx_remix/block_lights/*.json`（`M/light/BlockLightRuleManager.java`）→ `ChunkManager.buildBlockLightInfo :1097-1145`，每帧 `drawLightInstance`；实体 / 手持：`M/light/LightManager.java:202-743`；日月：Distant（`:1036-1124`） | `createLight`：Sphere（可带 Shaping 锥形）、Rect、Disk、Cylinder、Distant、Dome。地形灯有预算：每帧 32 次操作 / 0.75 ms |
| **天空与天气** | `M/sky/SkyRenderer.java:241-570,620-841`（程序化大气 RGBA16F dome、日月 quad、天空环境光 Rect）；雨雪：`WorldRendererMixin.java:556-617` 加 `RemixWeatherRenderer`；体积雾：`M/logic/ConfLayerDriver.java:175-191` | **雨雪不进路径追踪**，在 GL 中合成后按 Remix 深度测试叠加。云：`M/cloud/CloudRenderer.java`，每朵云一次 `drawInstance` |
| **粒子** | `WorldRendererMixin.java:521-535`（重定向 `renderParticles`）→ `M/particle/RemixParticleRenderer.java:241-270,356-360,820-845` | 在 worker 上生成世界空间 billboard，比场景晚一帧。内容变化时按 bucket 重建 mesh，并调用 `drawInstance` |
| **帧提交 / Present** | Submit 点：`M/mixin/GameRendererMixin.java:55-79`（`renderOverlays` 之前）→ `M/RemixRenderer.java:731-985,1320-1322`；合成：`blitVkImageToGL :1373-1385` 加 `M/gl/TextureOverlay.java:20-111`；Present：`M/mixin/RenderSystemMixin.java:20-34` 把 `RenderSystem.flipFrame` 中的 `glfwSwapBuffers` 重定向到 `presentFrameVk`（`M/RemixRenderer.java:1331-1403`） | 每帧顺序：①原版 world pass 运行，但地形层、粒子、云被取消，实体就地捕获；②`submit`；③GL 等待 semaphore，用**不透明全屏 quad** 把 Remix 图像画进 GL 帧缓冲；④原版 HUD / GUI 在 GL 中画在上面；⑤`glBlitNamedFramebuffer` 把 FBO 0 拷回 Remix 共享图；⑥Remix 的 Vulkan swapchain 执行 present。**GL 的 SwapBuffers 永不执行** |
| **水 / 半透明** | `BlockQuadEmitter.emitFluidQuads :544-640`；`BlockTextureManager.buildWaterMaterials :736-799` | 水是 translucent 材质，IOR 1.33，transmittance (0.6,0.9,0.89)，法线来自 DDS flipbook `water_normals_flipbook.dds`（10×12 帧，20 fps）。水下使用 camera medium 材质。染色玻璃 IOR 1.52 |
| **蒙皮** | 外观层有 `J/MeshInfoSkinning.java`、`InstanceInfoBoneTransformsEXT`、`RemixAPI.drawInstance(InstanceInfo) :534-571` | **Mod 从未使用**。`RemixMeshSurface.toNative` 从不设置 skinning（`M/asset/RemixMeshSurface.java:134-170`） |

### 1.5 Fabric API、Mixin、1.21.1 依赖清单

**Mixin：共 73 个**（`NV:src/main/resources/rtx-remix-renderer.mixins.json`）。其中 3 个 common，70 个 client。`compatibilityLevel JAVA_21`，`defaultRequire 1`；9 个文件使用 MixinExtras（`@WrapOperation`、`@WrapMethod`、`@ModifyReturnValue`）。完整表见证据文件 `evidence_nvidia_java_subsystems.md` 的 A 节。

**Fabric API：19 个文件，39 个不同的 import。**

| Fabric API | 用途 |
|---|---|
| `ClientModInitializer` / `ModInitializer` / `PreLaunchEntrypoint` | 入口 |
| `FabricLoader` | 游戏和配置目录、解压 payload |
| `WorldRenderEvents` | START … END，共 10 个阶段（`M/RtxRemixMod.java:573-623`） |
| `CoreShaderRegistrationCallback` | 雨雪折射 shader |
| `EntityModelLayerRegistry` | 实体模型层 |
| `ClientLifecycleEvents` / `ClientTickEvents` / `ClientChunkEvents` / `ServerChunkEvents` / `ClientPlayConnectionEvents` | 生命周期与区块事件 |
| `PayloadTypeRegistry` + `Client`/`ServerPlayNetworking` | Copper Beacon 网络包 |
| `ClientCommandRegistrationCallback` | `/rtx` 命令 |
| `KeyBindingHelper` | 按键 |
| `ScreenEvents` | 设置按钮 |
| `ResourceManagerHelper` | 资源重载 |
| `FluidRenderHandlerRegistry` | 流体 |
| **FRAPI**（`FabricBakedModel` / `RenderContext` / `QuadEmitter` / `RenderMaterial`，约 47 处） | 方块模型捕获 |
| `ParticleFactoryRegistry` / `FabricParticleTypes` | 粒子 |
| `ItemGroupEvents` | 物品栏 |
| `ExtendedScreenHandlerType` | Copper Beacon GUI |

**其他 1.21.1 特有依赖：**

- Access widener 共 62 条：`ModelPart$Cuboid/$Quad/$Vertex`、`RenderLayer$MultiPhase`、`ChunkRenderingDataPreparer$*`、`SpriteContents$Animation` 等。
- 1.21 新 API 的使用次数：
  - `VertexConsumer` 新接口，4 个实现类：`RemixCapturingConsumer` 1,361 行、`RemixVertexConsumerProvider`、`RemixBlockDamagePass`、`CopperBeaconBlockEntityRenderer`
  - `BuiltBuffer` 10 处、`BufferAllocator` 11 处、`RenderTickCounter` 14 处、`Identifier.of` 37 处、`CustomPayload` / `PacketCodec` 9 处

---

## 第二部分：Forge 1.20.1 差异分析

### 2.1 Fabric API → Forge 替代

| Fabric | Forge 1.20.1 替代 | 难度 |
|---|---|---|
| `ClientModInitializer` / `ModInitializer` | `@Mod` + `FMLClientSetupEvent` / `FMLCommonSetupEvent` | 低 |
| `PreLaunchEntrypoint`（`WorkerPoolCap`，在启动前限制 worker 池） | 无直接等价物。改为 JVM 参数或 Mixin config plugin `onLoad` | 中 |
| `FabricLoader` 路径 / `ModContainer` 解压 | `FMLPaths.GAMEDIR/CONFIGDIR`；`ModList.get().getModFileById(..).getFile().findResource(..)` | 低 |
| `WorldRenderEvents` | `RenderLevelStageEvent` 只有 AFTER_SKY / SOLID / CUTOUT / ENTITIES / BLOCK_ENTITIES / TRANSLUCENT / PARTICLES / WEATHER / LEVEL 这些阶段。START / END / BEFORE_ENTITIES / BLOCK_OUTLINE 无 1:1 对应，需要用 Mixin 或 `RenderHighlightEvent` 补 | 中 |
| `CoreShaderRegistrationCallback` | `RegisterShadersEvent`（存在：`forge-1.20.1/src/main/java/net/minecraftforge/client/event/RegisterShadersEvent.java`） | 低 |
| `EntityModelLayerRegistry` | `EntityRenderersEvent.RegisterLayerDefinitions` | 低 |
| `ClientTickEvents` / `ClientPlayConnectionEvents` | `TickEvent.ClientTickEvent` / `ClientPlayerNetworkEvent`（存在） | 低 |
| `Client`/`ServerChunkEvents` | `ChunkEvent.Load/Unload` | 低 |
| `ClientCommandRegistrationCallback` | `RegisterClientCommandsEvent`（存在） | 低（107 处引用需机械改写） |
| `KeyBindingHelper` / `ScreenEvents` / `ResourceManagerHelper` | `RegisterKeyMappingsEvent` / `ScreenEvent` / `RegisterClientReloadListenersEvent`（均存在） | 低 |
| 1.21 `CustomPayload` + `PacketCodec` | `SimpleChannel` + `FriendlyByteBuf`（1.20.1 网络模型完全不同） | 中。Copper Beacon 专用，可删 |
| `FluidRenderHandlerRegistry` | `IClientFluidTypeExtensions` | 中 |
| **FRAPI 模型捕获** | **Forge 没有 FRAPI**。Forge 模型走 `getQuads(state, side, rand, ModelData, RenderType)` 加 `getRenderTypes(..)`（见 Forge 补丁 `ChunkRenderDispatcher.java.patch`：RebuildTask 内的 `model.getModelData(...)`、`getRenderTypes(...)`、`renderBatched(..., modelData, rendertype)`） | **中高**。必须改写成 ModelData 路径，AFL 的 `SmoothSurfaceModel` / `PlantModel` / `TreeCellModel` 都依赖它 |
| Access widener | Forge `accesstransformer.cfg`（mojmap/SRG） | 中，62 条需重新映射 |
| MixinExtras | 需在 Forge 47 下自行 JarInJar 打包 MixinExtras（需核实 47.4.22 是否已内置） | 低 |

### 2.2 Mixin 逐项比对

方法：yarn 1.21.1 名 → intermediary → 检查 1.20.1 intermediary 中类与方法是否存在、描述符是否一致。脚本输出：`evidence_mixin_1.21.1_vs_1.20.1.txt`。

**A 类：目标类在 1.20.1 不存在（必须换目标重写）**

| NVIDIA Mixin | 1.21.1 目标（yarn / mojmap*） | 1.20.1 对应位置* | 影响 |
|---|---|---|---|
| `SectionBuilderMixin` | `SectionBuilder` / `SectionCompiler`（`class_9810`） | `ChunkRenderDispatcher.RenderChunk.RebuildTask#compile`（已被 Forge 补丁加入 ModelData 与 per-RenderType 循环） | **地形捕获入口，核心** |
| `BlockBufferBuilderPoolMixin` | `BlockBufferBuilderPool` / `SectionBufferBuilderPool`（`class_8901`） | `ChunkRenderDispatcher` 内的 `freeBuffers` 队列 | 并发上限 |
| `ChunkRenderingDataPreparerMixin`（689 行）、`RenderableChunksMixin` | `ChunkRenderingDataPreparer` / `SectionOcclusionGraph`（`class_8679`） | `LevelRenderer` 内的可见性遍历 | 可见性无分配移植，可以先删 |
| `ChunkDataSenderMixin` | `ChunkDataSender` / `PlayerChunkSender`（`class_8608`，1.20.2 引入区块批量发送） | `ChunkMap` 发包逻辑 | 集成服节流，可以先删 |
| `BuiltChunkMixin` 的 `method_60908` | 1.21 lambda | 无 | 需另找挂点 |

\* mojmap 名为常识对照，需在 Forge MDK 中核对。

**B 类：方法存在但签名改变（需改 descriptor 与内部 @At）**

| 方法 | 1.21.1 | 1.20.1 |
|---|---|---|
| `GameRenderer.render` | `(RenderTickCounter, Z)` | `(F, J, Z)` |
| `GameRenderer.renderWorld`（mojmap `renderLevel`） | `(RenderTickCounter)` | `(F, J, PoseStack)` |
| `GameRenderer.renderHand` | `(Camera, F, Matrix4f)` | `(PoseStack, Camera, F)` |
| `WorldRenderer.render`（`LevelRenderer.renderLevel`） | `(RenderTickCounter, Z, Camera, GameRenderer, Lightmap, Matrix4f, Matrix4f)` | `(PoseStack, F, J, Z, Camera, GameRenderer, Lightmap, Matrix4f)` |
| `WorldRenderer.renderLayer` | `(RenderLayer, DDD, Matrix4f, Matrix4f)` | `(RenderType, PoseStack, DDD, Matrix4f)` |
| `WorldRenderer.renderClouds` / `buildStarsBuffer` | 多一个 Matrix4f / 用 `Tessellator` 和 `BuiltBuffer` | 少一个 Matrix4f / 用 `BufferBuilder` 和 `BufferBuilder$RenderedBuffer` |
| `ModelPart.renderCuboids`、`Cuboid.renderCuboid` | 颜色为 `int ARGB` | 颜色为 `F F F F` |
| `VertexBuffer.upload*` | `BuiltBuffer$DrawParameters`；`method_60829` 不存在 | `BufferBuilder$DrawParameters` |
| `ParticleManager.renderParticles` | `(Lightmap, Camera, F)` | `(PoseStack, BufferSource, Lightmap, Camera, F)` |
| `ClientChunkManager.unload` | `(ChunkPos)` | `(II)` |

**C 类：类和方法在 1.20.1 存在、描述符一致**

其余约 50 个 Mixin 属于此类，例如：

- `EntityRenderDispatcher.render`、`BlockEntityRenderDispatcher.render`
- `ItemRenderer.renderItem`（3 个重载）、`HeldItemRenderer.renderFirstPersonItem`
- `WorldRenderer.setupTerrain` / `updateChunks` / `renderWeather`
- `SpriteContents.upload`、`NativeImage.uploadInternal`、`Window.swapBuffers`
- `MinecraftClient.render(Z)`、`BuiltChunk.getBuffer` / `createRebuildTask`
- `Keyboard` / `Mouse` 系列、`Biome` 颜色

这些"只需重映射为 mojmap"。**但方法内部的 INVOKE 目标仍可能变化**（例如 1.21 的 `VertexConsumer` 方法名），并且 Forge 补丁会改动部分方法体（`LevelRenderer`、`ItemInHandRenderer`、`ChunkRenderDispatcher`）。所以每个 @At 仍需在 Forge 1.20.1 反编译源中逐一核对。

**D 类：未在映射中直接解析（继承或接口方法）**

`MultipartBakedModel.getQuads`、`WeightedBakedModel.getQuads`、`RebuildTask.run`、`ClientChunkManager.onLightUpdate`、`DownloadingTerrainScreen.close`、`PackedIntegerArray.copy`、`MinecraftClient.close`、`Deadmau5FeatureRenderer.render`。需人工核对。

特别注意：Forge 的分块编译调用的是 **5 参数** `getQuads(..., ModelData, RenderType)`（Forge 补丁），NVIDIA 针对 3 参数版本做的无分配优化在 Forge 上不会命中。

**不在 intermediary 中的 `com.mojang` 类**（`RenderSystem.flipFrame`、`GlStateManager._upload`）：

- 1.21.1 中 `flipFrame` 有 Tracy 参数，1.20.1 是 `flipFrame(long)`（凭经验，需核对）。
- `RenderSystemMixin` 只重定向其中的 `glfwSwapBuffers` 调用，移植成本低。

### 2.3 1.21.1 → 1.20.1 渲染 API 差异要点

1. **顶点管线重写（1.20.5/1.21）。**
   - 1.21 的 `VertexConsumer` 用 `vertex(x,y,z).color(int)` 一类写法，**没有 `endVertex()`**；`BufferBuilder` 被拆成 `BufferAllocator` + `BuiltBuffer`（mojmap `ByteBufferBuilder` / `MeshData`）。
   - 1.20.1 是 `vertex(..).color(..).uv(..).overlayCoords(..).uv2(..).normal(..).endVertex()`。
   - `RemixCapturingConsumer`（1,361 行）的顶点组装逻辑（`finishOpenVertex :369`）要按 1.20.1 语义重写。
2. **分块管线换代**：1.20.2 改名为 SectionRenderDispatcher，1.20.5/1.21 拆出 SectionCompiler。见 A 类。
3. **时间参数**：`RenderTickCounter`（DeltaTracker）在 1.20.1 改为 `float partialTick` 加 `long`。
4. **矩阵**：1.21 的 `renderLevel` 直接传 positionMatrix 和 projection；1.20.1 用 `PoseStack` 加一个 projection。
5. **`ModelPart` 颜色**：1.21 用 int ARGB，1.20.1 用 float rgba。
6. **网络与注册**：1.20.5 的 payload codec 与 `Identifier.of`（37 处）改为 1.20.1 的 `new ResourceLocation`。

### 2.4 Java 21 → Java 17

- **无语言特性阻碍**：没有 record pattern、没有 type-pattern switch、没有 `case null`。
- **只有 28 处 Java 21 专有 API 调用**，全部可以机械替换：
  - `List.getFirst/getLast` 7 处
  - `Math.clamp` 20 处
  - `StringBuilder.repeat(char,int)` 1 处
- Mixin `compatibilityLevel` 改为 `JAVA_17`。
- **实测**：`javac --release 17` 编译 `rtxremix-java/src`（JOML 1.10.5 在 classpath 上，sha1 已核对）**只有 1 个错误**：`J/CommandLineRedaction.java:118 StringBuilder.repeat(char,int)`。`--release 21` 编译成功，生成 63 个 class。
- **结论：Java 版本不是实际阻碍。**

### 2.5 JNI 桥能否独立编译 / 加载

- **依赖**：`remixbridge.cpp` 只 include `windows.h`、`winternl.h`、`dbghelp.h`、`jni.h`、`<gl/GL.h>`、`remix_c.h`（`:14-36`），**不依赖 Minecraft、Fabric、LWJGL、GLFW**。
- **JNI 版本**：
  - 没有 `JNI_OnLoad`；使用的 JNIEnv 函数都是 JNI 1.4 及以前的。
  - `build_native.bat` 只读取 `%JAVA_HOME%\include\jni.h`（`:59-67`）。
  - 推断（外部知识）：JDK 17 的 `jni.h` 可以编译；同一 DLL 可以在 JDK 17 x64 JVM 上加载。
- **构建限制**：只能在 Windows 上用 MSVC 构建（`docs/GETTING_STARTED.md:8-9`）。本容器未做 native 构建。
- **结论：可独立编译和加载（待 Windows 实测，见 PoC A）。**

### 2.6 Native Runtime 是否依赖 1.21.1 / Fabric

- **不依赖。** Runtime 只看 C API 版本（0.16.0）、HWND 和 Win32 共享句柄，与 Minecraft 版本和加载器无关。
- **但依赖以下条件：**
  - Windows 10/11（x64 / ARM64）
  - **RTX 30 系及以上 GPU，驱动 ≥ 616.92**（`NV:README.md:50`）
  - 驱动过低时报 `DRIVER_VERSION_BELOW_MINIMUM`（`remix_c.h:180-186`）
  - Vulkan

### 2.7 GLFW / OpenGL / DirectX 交互

- **OpenGL 上下文**：`WindowMixin` 在 `glfwCreateWindow` 前强制 **GL 4.4 core**（`M/mixin/WindowMixin.java:31-45`），因为互操作需要 `GL_EXT_memory_object(_win32)` 和 `GL_EXT_semaphore(_win32)`（`M/RemixRenderer.java:466-496`）。
  - 1.20.1 原版是 3.2 core，提高版本在 NVIDIA 驱动上可行。
  - 但这会影响所有依赖 3.2 行为的 Mod，需要实测，尤其是 Oculus 和 Embeddium。
- **Present**：只用 Vulkan swapchain，GL 的 SwapBuffers 被替换（见 1.4）。
- **DirectX**：不使用 D3D9 / D3D12 渲染路径。"d3d9.dll" 只是 runtime 的文件名。
- **GUI**：GUI 仍由 GL 绘制，然后整体拷入 Remix 再 present。

### 2.8 必须重写 / 可原样保留

| 组件 | 判断 |
|---|---|
| `rtxremix-java`（3,978 行 Java） | **几乎原样保留**：改 1 行 Java 21 API；JOML 由 MC 1.20.1 提供 |
| `remixbridge.cpp` + `remix_c.h` | **原样保留**，需 MSVC 构建或复用官方 Release 中的 `remixbridge.dll`（Apache-2.0） |
| Runtime `remix-sdk/` | **原样使用**，必须与 header 0.16.0 匹配，只能用官方 pin |
| `gl/GlInterop`、`gl/TextureOverlay`、Present 流程 | 基本保留，少量 GL / 映射调整 |
| `asset/*`（Mesh/Instance/Material/Light 的 Store） | 基本保留，几乎不碰 MC |
| `light/*`、`sky/*`、`cloud/*`、`logic/*`（conf 层） | 中等改写：MC 类型重映射 |
| `texture/*`（图集捕获、LabPBR） | 中等改写：`TextureAtlas` / `SpriteContents` 1.20.1 API 与动画 Mixin |
| `terrain/*`（10,450 行）+ 地形相关 Mixin | **重写入口**：SectionCompiler → RebuildTask；FRAPI → Forge ModelData；**AFL 使用 Embeddium 时 vanilla 入口根本不触发**（见第三部分） |
| `entity/*`（8,174 行） | **大改**：1.20.1 VertexConsumer 语义、ModelPart 颜色、渲染器签名 |
| Fabric 入口 / 命令 / 网络 / 设置界面 | 重写为 Forge |
| Copper Beacon、ChunkDataSender、可见性移植、服务器优化 | **建议 PoC 阶段全部删除** |

---

## 第三部分：AFL 兼容性

AFL 侧事实来自子任务对 AFL / Vextris / Aureveil 源码的阅读；关键点已抽查（`AFL:build.gradle:233-264`、`AFL:gradle.properties:20`、`ClientLiquid.java:384-394,488-515`、6 个文件使用 `MeshGpuCache` / `GpuGeometry`）。

### 3.0 决定性前提：AFL 有大量内容不经过 NVIDIA 的捕获点

NVIDIA 的合成方式是用一张**不透明全屏 quad** 把 Remix 图像盖在 GL 世界画面上（`M/gl/TextureOverlay.java:20-111`），然后才画 HUD。因此，**凡是 AFL 在世界渲染阶段直接用 GL 画的内容，都会被盖掉而不可见**（同时还白白消耗 GL 时间），除非为它们单独写 Remix 提交适配。

| AFL 内容 | 现有渲染路径（证据） | 能否被 NVIDIA 捕获点看到 |
|---|---|---|
| 默认骨骼身体 / Block-Mesh V2 B | CPU LBS 蒙皮，写入原版 entity consumer（`AFL:…/client/skeleton/PlayerSkeleton.java:402-444`、`BlockMeshBody.java:217-260`） | **能**（VertexConsumer 捕获）。但每帧顶点都变，NVIDIA 会按"哈希变化 → 新 mesh"处理 |
| Mesh 幸存者（dev） | Vextris `renderSkins`，CPU LBS，写入 MultiBufferSource（`MeshBodyTpTest.java:306-390`） | 能（同上） |
| 原生枪械 / 弹匣 / 手持 Mesh 物品 | GeckoLib `GeoItemRenderer` + **Vextris `MeshGpuCache.drawBone`**（持久 GPU 缓冲；`NativeGunContextRenderer.java:121-160`） | **不能**（缓存开启时）。可用 `-Dvextris.gpuCache=false` 回退 CPU，但性能差 |
| T15 皮卡 | pass 1–2 走 `MeshGpuCache`，玻璃走 `AflMovingGlass`，灯光辉光走 CPU（`PickupTruckRenderer.java:86-412`） | 大部分**不能** |
| 道路 / 桥 / 隧道 | `RenderLevelStageEvent AFTER_SOLID_BLOCKS` + **Vextris `GpuGeometry`**（`RoadClient.java:469-556`） | **不能** |
| 平滑地形 / 岩石雕刻 / 土方 | `BakedModelWrapper` → 分块 BakedQuad（`SmoothSurfaceModel.java:51-113`） | 能，但只能经由**分块构建器**，而 AFL 实际的分块构建器是 **Embeddium** |
| Mesh 植物 / 树 | `IDynamicBakedModel` 分块四边形；**风在 Embeddium 顶点着色器中（GPU）计算**（`VegetationWind.java:9-33`、`VegetationWindEmbeddiumShaderMixin.java:14-21`） | 静止几何能（同上，需经 Embeddium）；**风动画捕获不到** |
| 近处植物（被踩踏） | 自有 `VertexBuffer` + 自有 ShaderInstance（`VegetationDynamics.java:110-624`） | **不能** |
| 物理水 / 瀑布 | 私有 `BufferBuilder` → `VertexBuffer.drawWithShader`（`ClientLiquid.java:358-518`，`ClientFalls.java:667-779`） | **不能** |
| 雨（无光影包时） | 静态 20k quad VBO，在顶点着色器中动画（`RainRenderer.java:43-295`） | 不能（NVIDIA 自己的雨也不进路径追踪） |
| 镜子 | 离屏第二世界 pass（`MirrorReflection.java:58-95`） | 不能，也不需要（路径追踪天然有反射） |
| 无光影包后处理链 | `AflPost` / `PostGameRendererMixin` | 与 NVIDIA 的 Submit 时机冲突，需要关闭 |

另外两个硬性前提：

- **Embeddium 是 AFL 实际的地形渲染器**（`AFL:build.gradle:233-242`）。它完全替换了 `ChunkRenderDispatcher`，NVIDIA 式的 vanilla 地形捕获在 Embeddium 下**一个 section 都看不到**。只有两条路：
  - 写 Embeddium 专用的捕获层（如 Embeddium 的 meshing task 或 `ChunkMeshEvent`）；
  - 在 RTX 配置下不加载 Embeddium，代价是失去 AFL 的风和地形动态光，这两者本来就依赖 Embeddium shader。
- **Oculus 必须不存在。** 两者都要控制整帧，NVIDIA 还要求 GL 4.4 core 并替换 present。

### 3.1 逐项回答

1. **RTX Remix 能否接收动态骨骼 Mesh？——API 能，NVIDIA Mod 未使用，属"源码存在但未验证"。**
   - C API 有 `remixapi_MeshInfoSkinning`（每顶点 `bonesPerVertex` 个权重和索引，`remix_c.h:457-467`）和每实例骨骼变换 `InstanceInfoBoneTransformsEXT`（上限 256 根骨骼，`remix_c.h:542-549`）。外观层已封装（`J/MeshInfoSkinning.java`、`J/RemixAPI.java:534-571`）。
   - AFL 骨骼数在 256 以内：Block-Mesh B 22 根，幸存者 54 根，最多 4 个权重（`AFL:docs/dev/block_mesh_v2_b_ingame.md:28`、`mesh_survivor_v1_m25_ingame.md:24-26`）。
   - NVIDIA 明确选择刚体部件而不用 skinning（`M/entity/RemixEntitySubmitter.java:82-84`），所以这条路径**在 Minecraft 构建中没有任何使用证据**，需要 PoC 实测。
2. **能否每帧提交变化后的顶点？——能，但没有原地更新接口。**
   - C API 只有 `CreateMesh` / `DestroyMesh`。NVIDIA 对变化几何的做法是每帧新建 mesh、下一帧销毁（`RemixEntitySubmitter.java:1952-1977,724-735`）。
   - 这意味着每帧重建 BLAS 并经过 JNI 拷贝。AFL 的 CPU 蒙皮人物和大量感染者会直接压在这条最贵的路径上。
3. **动态 Mesh 有没有运动矢量？——API 没有上一帧字段；只有 retained instance 的变换历史由 runtime 推导。**
   - 每帧新建的 mesh 没有逐顶点历史（仓库内无法验证 runtime 怎么处理），对 DLSS-RR 和降噪会产生拖影风险。
   - 走 GPU skinning（骨骼变换历史）理论上更好，但也属未验证。
4. **半透明水体？**
   - NVIDIA 用 translucent 材质：IOR 1.33，transmittance，DDS 法线动画，水下 camera medium（`BlockTextureManager.java:736-799`）；API 还有 `INSTANCE_CATEGORY_BIT_ANIMATED_WATER`（`remix_c.h:601`）。
   - AFL 物理水不是方块，也不走 VertexConsumer。需要把 `ClientLiquid` 的 mesh 直接作为 Remix mesh 加 NVIDIA 水材质提交。可行，但需要新适配。
5. **LabPBR 能否沿用？——基本能。**
   - `LabPBRConverter` 的解码规则：roughness=(1-s)²、`_s.g≥230` 为金属、`_s.b≥65` 为 SSS、`_n.rg` 为法线、`_n.b` 为 AO（被烘焙进 albedo）、`_n.a` 为高度、`_s.a<255` 为自发光（`M/texture/LabPBRConverter.java:33-178`）。这与 AFL 的 `_n` 约定（RG 法线、B AO、A 高度，`AFL:docs/rendering/shader_pbr_tuning_v1.md:15`）一致。
   - AFL 的数量：方块图集精灵约 405 个 `_n`、412 个 `_s`，可走 atlas 路径；物品和实体的独立贴图约 125 套 `_n`/`_s`，可走实体材质路径（`RemixEntitySubmitter.java:2202-2204`，贴图旁的 `_n`/`_s`）。
   - 差异：LabPBR 230–254 的"预设金属"语义被简化为二值金属；AFL 按 Sundial 调好的参数需要重调。
6. **自定义灯光能否进入路径追踪？——能。**
   - AFL `DynamicLights.spot`（位置、forward、锥角、RGB，上限 24，`AFL:…/DynamicLights.java:50-146`）可以直接映射成 `Sphere + LightInfoLightShaping`（`remix_c.h:718-735`），或 Disk。
   - 单位需要标定：AFL 用方块光等级，Remix 用辐亮度。
   - 卡车灯、打火机、闪电都可以接入。
7. **AFL Mesh 能否直接进 RTX 场景，而不伪装成方块？——能，这是 C API 的原生用法。**
   - `createMesh(HardcodedVertex + material)` 加 `sceneInstanceAdd(transform)` 与 MC 无关。
   - Vextris `MeshGpuCache` 按（模型, 骨骼, 层）缓存几何、每帧只改矩阵，**正好对应"一次 createMesh + 每帧 sceneInstanceUpdate"**，适配成本低于 CPU 捕获。
8. **如何与 Forge/Oculus 路径隔离？**
   - 做成**独立可选 JAR**，并使用**独立启动配置**：不装 Oculus，Embeddium 视适配情况决定。
   - AFL 与 RTX JAR 之间只通过一个很小的提交 API 或反射挂钩耦合，与 AFL 现在耦合 Oculus 的方式相同。
   - 注意：NVIDIA 的 `/rtx off` 仍然经过 Remix swapchain present（`M/RemixRenderer.java:758-767`），**不是真正的回退**。真正的隔离只能靠"不加载该 JAR"。

---

## 第四部分：DLSS

| 功能 | 分类 | 证据 |
|---|---|---|
| **DLSS Super Resolution** | **已实现（间接）** | Mod 不直接设置 upscaler：仓库中没有任何 `rtx.upscalerType` / `qualityDLSS` / `resolutionScale`。Mod 只写 `rtx.graphicsPreset`（`M/QualityPreset.java:13-50`；`/rtx preset` 命令，`M/RtxRemixMod.java:1033-1043`），由 Remix 质量层展开为 DLSS 配置（`NV:packaging/rtx-remix/mods/minecraft-logic/README.md:507-512`）。Payload 中有 `nvngx_dlss.dll`（`NV:docs/GETTING_STARTED.md:444-447`）。设置页有按钮打开 Remix 自身的图形设置（`M/settings/RtxSettingsScreen.java:72-82`） |
| **DLSS Ray Reconstruction** | **已实现（调参层面；开关由 runtime 或预设决定）** | `rtx.rayreconstruction.combineSpecularAlbedo = true`（`NV:packaging/rtx.conf:163-168`）；手持烟雾 alpha 按 RR 透明层调整（`M/entity/HeldItemSmoke.java:63-73`）；payload 中有 `nvngx_dlssd.dll` |
| DLSS "NR" 控制掩码（3D-Guided Neural Rendering） | 已实现（bridge 硬编码，效果未在仓库文档说明） | `remixbridge.cpp:1540-1544` 对所有不透明材质设置 `enableDlssControlMask=1`；payload 中有 `nvngx_dlssnr.dll` |
| **DLSS Frame Generation** | **源码存在但未验证（默认关闭）** | `NV:packaging/rtx.conf:19-20` 写明 `rtx.dlfg.enable = false`；只能在 Startup 前通过 `RTXREMIX_EXTRA_RTXCONF` 实验性开启（`M/RemixRenderer.java:254-271`）。仓库未说明关闭原因。推断：HUD 在 present 前已合成进画面，FG 会连 GUI 一起插帧 |
| DLSS Multi Frame Generation | 理论可行（仓库中零提及） | — |
| Reflex | 理论可行（仅 runtime 带有 `NvLowLatencyVk`，`NV:THIRD_PARTY_NOTICES.md:34`） | — |
| DLAA / NIS / TAA-U / XeSS | 理论可行（runtime 层；仓库带有 `libxess.dll`） | — |
| FSR | 不支持（无二进制、无许可证） | — |

**GPU / 驱动要求：**

- 仓库只给出总体要求：RTX 30 系及以上，驱动 ≥ 616.92（`NV:README.md:50`），**没有逐功能的要求**。
- 外部常识（非仓库）：SR、RR、DLAA 支持任何 RTX；FG 需 RTX 40 及以上；MFG 需 RTX 50 及以上。
- **在 AFL 的测试卡 RTX 3080 Ti 上，可用的只有 SR、RR、DLAA（以及 Reflex）。**

**DLSS 能否脱离完整 RTX Remix 使用？——在这份 Mod 中不能。**

- 仓库中没有任何 NGX 或 Streamline 调用。DLSS 存在于 `d3d9.dll` 的路径追踪管线内部，Mod 只通过配置变量选择档位。
- 原版 GL 画面只是以全分辨率拷进 Remix 再 present（`NV:docs/GETTING_STARTED.md:493`）。
- 想在不走路径追踪的前提下使用 DLSS，属于第二份报告（独立渲染器）的研究范围。

---

## 第五部分：独立 PoC 设计（尚未实施）

**环境要求：**

- Windows 10/11、RTX 3080 Ti、驱动 ≥ 616.92、JDK 17、VS 2022 C++ 工具链。
- **独立仓库或目录**（例如 `…/afl-rtx-poc/`，基于 Forge 1.20.1 MDK 47.4.22），**不放进 AFL 仓库，不引用 AFL 源码**。只在 E 阶段以只读方式读取 AFL 的资源文件。
- Runtime 取自 NVIDIA 官方 Release JAR 的 `native/remix-sdk-win64/`，必须是 header 0.16.0 对应的那一版。

| 阶段 | 内容 | 需要的组件 | 客观通过标准 | 失败即停止？ |
|---|---|---|---|---|
| **A. 加载 JNI** | 空 Forge mod：拷入 `rtxremix-java`（修 `CommandLineRedaction.java:118`）+ `remixbridge.dll` + runtime；客户端启动时 `RemixAPI.load(d3d9.dll)` | `rtxremix-java`、`build_native.bat`（或官方 DLL）、`NativeExtractor` 的简化版 | 日志显示 `remixapi_InitializeLibrary` 成功；版本协商通过（不是错误码 8 `INCOMPATIBLE_VERSION`）；JDK 17 下无 `UnsatisfiedLinkError` | **是**。A 失败说明 JDK 17、Forge 类加载或 runtime 获取不可行 |
| **B. 初始化 Runtime** | 移植 `WindowMixin`（GL 4.4 core）、`RenderSystemMixin`（swap → present）、`GlInterop`、`TextureOverlay`；只把原版 GL 画面经 Remix 呈现 | mojmap 版 `Window.<init>` / `RenderSystem.flipFrame` Mixin | 主菜单和原版世界经 Remix swapchain 正常显示；Alt+X 能打开 Remix 菜单；连续 10 分钟无崩溃、无黑屏；GPU 时间可测 | **是**。B 失败说明 Forge 1.20.1 GL 环境与互操作不兼容 |
| **C. 静态立方体** | 在玩家前方 `createMaterial` + `createMesh`（12 个三角形）+ `sceneInstanceAdd` + 相机 + 一盏 Distant 光 + `submit` | `asset/*` Store、`RemixRenderer.updateCamera` 的简化版 | 截图中立方体有**路径追踪阴影和地面反射**；Remix 调试视图（albedo / normal）中可见该实例；移动相机时稳定 | **是** |
| **D. 一小片原版地形** | **不装 Embeddium / Oculus**：在 `ChunkRenderDispatcher.RenderChunk.RebuildTask#compile` 上挂捕获；渲染 3×3 区块，先只处理 SOLID 和 CUTOUT，再加 LabPBR 图集 | `terrain/capture/*` + Forge ModelData `getQuads` 路径 | 几何与原版逐面一致（截图对比）；PBR 法线可见；改动方块后 1 秒内更新；记录 3×3 / 8 / 16 区块视距下的帧时间 | 否。可以退回 C 继续做实体验证，但完整地形结论记为"失败" |
| **E. AFL 自定义 Mesh** | 只读导入 AFL 的一个 T15 或枪械 mesh 资源，在 PoC 中按 bone 执行 `createMesh` 一次，每帧 `sceneInstanceUpdate`；再做一个 CPU 蒙皮的 Block-Mesh B 每帧 transient mesh 对照组；可选做 skinning API 试验 | 新写的"MeshGpuCache → Remix"适配原型（不改 Vextris / AFL） | 移动物体有正确阴影和反射；开启 DLSS / RR 时**无明显拖影**（录屏逐帧检查）；记录每帧 JNI 时间和 GPU 时间；transient mesh 与 skinning 两种方式的成本对比 | 否，但若 transient 和 skinning 都拖影严重，记为"动态人物不可用" |
| **F. DLSS 启用** | 通过 `rtx.graphicsPreset` 或 Remix 菜单选 DLSS 档位 | `QualityPreset` | Remix 菜单显示 upscaler 为 DLSS，内部分辨率低于输出分辨率；1440p 下给出 Off / Quality / Performance 的 FPS 对比；确认 3080 Ti 上 FG 不可用或不开启 | — |

**"能编译"不算通过。** 每个阶段都需要截图或录屏、日志和帧时间数据。

---

## 第六部分：结论

### 6.1 可行性评级

**C：技术上可行，但必须重写大部分 Minecraft 接入层；直接逆向移植（逐文件改映射）不可行。**

评级标尺：A 直接可移植 / B 中等改造 / C 需重写大部分接入层 / D 架构不可行。

- 可直接复用的部分：外观层、JNI、runtime，加上约 30% 与 MC 弱相关的代码（asset Store、灯光、天空、配置层、GL 互操作）。
- 必须重写的部分：地形入口、实体捕获、Fabric 层；另外还要为 AFL 新写一整层自定义渲染适配。

### 6.2 核心技术阻碍（按严重程度）

1. **AFL 的大量内容绕过所有捕获点**：Vextris GPU 缓存、自有 VBO 水体、近处植物、道路。而 NVIDIA 的合成会盖住这些 GL 绘制，所以它们需要一个个写 Remix 适配，工作量不亚于移植本身。
2. **Embeddium 替换了原版分块管线。** RTX 配置下要么去掉 Embeddium（失去风和地形动态光），要么为 Embeddium 写捕获。
3. **私有 runtime。** C API 0.16.0 是 NVIDIA 的 Minecraft 分支，公开版 dxvk-remix 是 0.6.6，ABI 不兼容。后续升级、Bug 修复和功能完全取决于 NVIDIA。重新分发受 NVIDIA RTX SDK 许可约束：只能作为应用的一部分以目标码形式分发，不能单独分发（`NV:packaging/licenses/remix-runtime/ThirdPartyLicenses.txt:122-177`）。
4. **动态顶点的运动历史无 API 支持**：只有每帧新 mesh 的路径，且与 DLSS-RR 的配合未验证。
5. **1.21 顶点管线、分块管线、渲染签名的系统性差异**：A、B 类 Mixin 约 15 个，`RemixCapturingConsumer` 1,361 行需重写。
6. 只支持 Windows 和 RTX 30 系及以上 GPU；与 Oculus 互斥；GL 4.4 core 对其他 Mod 的影响未知。

### 6.3 需要修改的模块与文件

- **重写**：
  - `M/mixin/SectionBuilderMixin`、`BuiltChunk*`、`BlockBufferBuilderPool`、`ChunkRenderingDataPreparer*`、`RenderableChunks`、`ChunkDataSender`
  - `GameRendererMixin`、`WorldRendererMixin`（920 行）、`ModelPart*`、`CuboidMixin`、`VertexBufferMixin`、`ParticleManager` 相关 Mixin
  - `M/entity/RemixCapturingConsumer`、`RemixVertexConsumerProvider`、`RemixEntitySubmitter`
  - `M/terrain/capture/BlockQuadEmitter`（FRAPI 改为 ModelData）
  - `M/RtxRemixMod`（2,440 行，全部 Fabric 事件和命令）
  - 网络包、access widener 改为 AT
- **删除**：`block/*`（Copper Beacon）、`benchmark/*`、服务器优化 Mixin、可见性移植、`ChunkDataSender` 节流。
- **轻改**：`texture/*`、`light/*`、`sky/*`、`cloud/*`、`particle/*`、`camera/*`、`logic/*`、`gl/*`。

### 6.4 可复用的 NVIDIA 原始组件

`rtxremix-java` 全部、`remixbridge.cpp`、`remix_c.h`、runtime payload、`rtx.conf`、`rtx-remix/mods/minecraft-logic` 配置层、`gl/GlInterop`、`gl/TextureOverlay`、`asset/*`、`LabPBRConverter`、天空和云生成算法、灯光规则格式。

许可证：Mod 与外观层是 Apache-2.0，`remix_c.h` 是 MIT，runtime 按 NVIDIA 条款分发。

### 6.5 Forge 侧需要新建的适配层

1. Forge 入口、事件、命令、按键、设置界面。
2. 1.20.1 地形捕获：vanilla `RebuildTask` 和 Embeddium 二选一。
3. 1.20.1 实体 / 物品 VertexConsumer 捕获。
4. **AFL 专用提交 API**，三个子适配：
   - Vextris GPU 缓存 → retained instance
   - 水体 / 瀑布 mesh → water 材质
   - DynamicLights → Remix 光源
5. 隔离开关：独立 JAR 加启动配置检测，检测到 Oculus 时拒绝初始化。

### 6.6 初步性能风险

- **CPU**：
  - 捕获点就是原版绘制点，原版的 CPU 成本仍在。
  - AFL 的 CPU 蒙皮人物和"大量感染者"会走每帧 transient mesh（创建、JNI 拷贝、BLAS 重建）。
  - T15 在 CPU 路径上约 40 万顶点/帧（`AFL:docs/dev/vextris_gpu_cache_request.md:46-58`），必须改用 retained instance，否则不可用。
- **GPU**：
  - 路径追踪 BVH 的规模与几何量成正比。AFL 约 5,500 棵 Mesh 树（32 区块视距估算）和 cutout 植被，会显著增加 BLAS 内存和 any-hit 成本。
  - NVIDIA 只针对展示地图调优，仓库自述"Blocks, mobs, dimensions… some will look wrong or run slowly"（`NV:README.md`）。
- **无基准数据**：3080 Ti 上的路径追踪帧时间在仓库中没有给出，必须由 PoC D/E/F 实测。

### 6.7 PoC 最小实施范围

A → B → C 三个阶段（约 1–2 周专职工作量，凭经验估计）即可回答"Forge 1.20.1 + JDK 17 能否驱动这份 runtime"。D 和 E 才回答"AFL 值不值得"。

### 6.8 相对 Aureveil 的实际收益

| 维度 | Aureveil + Oculus（现状） | RTX Remix 路线 |
|---|---|---|
| 阴影 | 1024–2048 阴影贴图，64–112 格范围 | 路径追踪，任意距离、软阴影、多光源 |
| 反射 | SSR（屏幕外内容丢失） | 真实反射，含屏幕外与镜面 |
| GI | 体素探针 GI，128×64×128 网格，多帧多次反弹 | 路径追踪全局光照 + 降噪 / RR |
| 动态光 | 最多 24 盏解析光 + 洪泛彩色光 | 任意数量物理光源（受预算限制） |
| 抗锯齿 / 放大 | TAA，无 upscaler | DLSS SR / RR / DLAA |
| 硬件 / 平台 | 任意 GL 3.2+ 显卡 | 仅 Windows + RTX 30 系及以上 |
| AFL 特性 | 雨、植被风、闪电、水焦散等已经通过 `customTexture` 数据接口适配 | **全部需要重新适配**；雨仍是 GL 叠加 |

**结论**：画质上限明显更高，但它只能是一个面向 RTX 用户的**可选第二渲染路径**，不能替代 Aureveil。

### 6.9 失败时的回退方案

1. Aureveil + Oculus 始终是默认且唯一必需的路径；RTX 只作为可选 JAR 和可选启动配置。
2. PoC 在 A 或 B 失败时，立即停止，并记录失败点（JDK 17、GL 互操作、runtime 获取）。
3. D 或 E 失败（地形或 AFL Mesh 无法达标）时，保留 A–C 的成果作为技术储备，等待 NVIDIA 公开 runtime，或等待社区出现 1.20.x 移植。
4. 战略选项（**需另行批准，本次不做**）：AFL 未来若升级到 1.21.1，移植成本会降为"Fabric → (Neo)Forge 加载器层 + AFL 适配"，但仍需要解决 3.0 节的 AFL 渲染路径问题。

---

### 附：证据文件（调研会话临时目录，未入库）

- `evidence_nvidia_java_subsystems.md`：NVIDIA Mod 子系统映射与 73 个 Mixin 完整表（子任务输出）。
- `evidence_mixin_1.21.1_vs_1.20.1.txt`：Mixin 目标逐项映射比对脚本输出。
- `map/forge-1.20.1/patches/...ChunkRenderDispatcher.java.patch`：Forge 分块编译补丁。
- `javac17/`：`rtxremix-java` 的 Java 17 / 21 编译测试产物。

---

## 第七部分（2026-10-26 追加）：Radiance 对照

本节为追加内容，第一至第六部分的结论不变。完整分析见 `docs/rendering/afl_independent_renderer_research_v1.md` §2–§3。

**Radiance 是什么**

- Minecraft-Radiance/Radiance，分支 `release-0.1.6-1.20.1-forge`，提交 787670a，Forge 47.3.19，Java 17，许可证 **GPL-3.0**。
- 原生渲染器为 MCVR（6719d74，以 GPLv3 为主）。
- 它是一个**已发布的 Forge 1.20.1 Vulkan 硬件光追渲染器**：
  - 强制要求 KHR RT pipeline（`MCVR:src/core/vulkan/physical_device.cpp:112-125`）。
  - DLSS SR / RR 通过 NGX Vulkan 直接调用（`MCVR:src/core/render/modules/world/dlss/dlss_wrapper.cpp:107,552,669`）。

**对本报告结论的影响**

1. **"Forge 1.20.1 能否承载 Vulkan 光追与 DLSS"已由 Radiance 的源码证明可行。**
   - 本报告 2.2 节待核实的 `RenderSystem.flipFrame(long)` 中的 `glfwSwapBuffers`，在 1.20.1 中存在：Radiance 的 `OpenGLUiRenderSystemMixins` 以 `flipFrame(J)V` 为目标，且 `defaultRequire 1`。
   - GL / Vulkan 互操作所需扩展与 NVIDIA 的做法一致（`RAD:client/opengl_ui/OpenGLUiBridge.java:96-204`）。
2. **AFL 的"内容绕过捕获点"问题在 Radiance 中同样存在。**
   - Radiance 对 `VertexBuffer` 没有任何 Mixin。
   - 在默认的 OpenGL UI 构建中，世界阶段只捕获 MultiBufferSource 内容（`EntityProxy.captureWorldRender`，`EntityProxy.java:828-864`）。
   - 世界图像在 GUI 之前以全屏方式合成（`OpenGLUiGameRendererMixins` 中的 `compositePreparedWorld`）。
   - 结论：本报告 3.0 节的判断（AFL 的 GPU 缓存、水体、近处植物不可见）**同样适用于 Radiance**。
3. **对"逆向移植 NVIDIA Mod"的评级（C）不变。** Radiance 的出现提供了第三条现成路线（直接使用或向上游贡献），但它是 GPLv3，与 AFL Renderer 的专有目标冲突。
4. **相对 NVIDIA 路线，Radiance 的不同点：**
   - 支持 Linux 和 AMD（FSR）。
   - 原生端保留上一帧的顶点缓冲以生成逐顶点 MV（`MCVR:…/default.rchit:633-660`），NVIDIA C API 没有这一能力。
   - DLSS DLL 由用户自行放置，不随包分发。
   - 没有公开 API（issue #283 尚无回复）。
