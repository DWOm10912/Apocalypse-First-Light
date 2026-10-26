# AFL 独立渲染器（Forge 1.20.1 · Vulkan · PBR · 硬件光追 · DLSS SR）可行性研究

- **状态**：Research Only。未修改 AFL、Aureveil、Vextris 任何代码，未编码；本文件为研究分支上的调研文档。
- **日期**：2026-10-26
- **项目定位**（用户 2026-10-26）：面向 Apocalypse: First Light 的独立现代渲染器，作为并行实验项目；Aureveil + Oculus 保持为稳定方案。
- **许可证原则**（用户 2026-10-26）：
  - 渲染器核心采用专有许可证。
  - 公开 API 与闭源实现分离。
  - GPLv3 代码只作调研参考，不复制、不改写进专有核心。
  - NVIDIA SDK 单独审核。

---

## 0. 结论先行

**建议：B，部分技术值得做，但"完整目标"在现阶段不合理。**

一句话回答核心问题：

- 自研一个 Forge 1.20.1 的 Vulkan + PBR + 硬件光追 + DLSS 渲染器，**在工程上是可行的**。Radiance（Forge 1.20.1，0.1.6-alpha）和 NVIDIA RTX Remix for Minecraft 两个独立实现，已经用源码证明了关键路径能走通。
- 但它**不是一个小项目**。Radiance 的规模是 Java 205 个文件约 2.7 万行，加上原生 MCVR 约 6.1 万行 C++ 和 278 个 shader，而且仍处于 alpha。
- 按"专有核心、不复用 GPLv3"的约束，这些代码都要独立实现。
- **值得立即做的**：
  - 引擎外的独立 Vulkan RT + DLSS SR 验证（S0）。
  - Forge 内的 GL↔Vulkan 互操作骨架（P0–P1）。
  - "AFL 场景提交 API"的设计。
- **不合理的**：
  - 以"替代 Aureveil、覆盖所有 AFL 视觉"为近期目标。
  - 完全替换 OpenGL（VulkanMod 路线）。
  - 先做路径追踪全家桶再做 AFL 适配。

---

## 1. 证据来源

| 简称 | 来源 | 版本 / 提交 | 许可证 |
|---|---|---|---|
| `NV:` | NVIDIAGameWorks/rtx-remix-minecraft | v0.1.0（170197a） | Apache-2.0（Mod、外观层）；runtime 按 NVIDIA 条款 |
| `RAD:` | Minecraft-Radiance/Radiance，分支 `release-0.1.6-1.20.1-forge` | 787670a（2026-09-29） | **GPL-3.0**（`RAD:LICENCE`；`mods.toml` `license="GPL-3.0"`） |
| `MCVR:` | Minecraft-Radiance/MCVR | 6719d74（= `release-0.1.6`） | **GPLv3 为主**；少量文件为 Apache-2.0 / MIT（`MCVR:LICENSE.md`） |
| `SL:` | NVIDIA-RTX/Streamline | 625db07（SDK 2.14.16，2026-10-16） | MIT（`SL:license.txt:1-21`） |
| `NGX:` | NVIDIA/DLSS | 9981c63（DLSS 310.9.16 SDK） | NVIDIA RTX SDKs License（`NGX:LICENSE.txt`） |
| `DLSSPG:` / `RTXUI:` | NVIDIA/DLSS 仓库 `doc/DLSS_Programming_Guide_Release.pdf`、`doc/RTX UI Developer Guidelines.pdf` | 9981c63（指南日期 2026-03-31） | 随 NVIDIA RTX SDKs License 提供 |
| `VM:` | xCollateral/VulkanMod，分支 `1.20.x` | 14efa9d | **LGPL-3.0**（`VM:LICENSE`） |
| `AFL:` / `AUR:` | 本地 AFL、Aureveil | 研究分支，未改动 | — |

**验证边界**：本容器为 Linux，没有 GPU，无法运行任何渲染器。下文"已实现"指"源码中存在完整调用链"，**不等于本地运行验证**。

**授权声明**：本文对 Radiance / MCVR 只引用文件位置、架构做法和公开事实，没有复制其代码，也不建议复制。

---

## 2. 现有技术研究（NVIDIA RTX Remix、Radiance、Streamline）

### 2.1 十个问题对照

| 问题 | NVIDIA RTX Remix for MC | Radiance / MCVR |
|---|---|---|
| **1. 世界几何如何提交** | 就地捕获：<br>• 地形：`SectionBuilder` Mixin → 堆外 SectionMeshData → 按列分页 `createMesh` + `sceneInstanceAdd`（`NV:M/mixin/SectionBuilderMixin.java:86-195`、`M/terrain/ChunkManager.java:1405-1500`）<br>• 实体：`renderEntity` 包装 + 捕获型 VertexConsumer，按部件做刚体实例（`M/entity/RemixEntitySubmitter.java`） | **整体取代 `LevelRenderer.renderLevel`**（HEAD 注入后取消，`RAD:…/WorldRendererMixins.java:249-392`）：<br>• 区块：Mixin 拦截 `RenderChunk` 的 setDirty / reset / setOrigin，再由 `ChunkProxy` 自己调用 Forge `model.getModelData` / `getRenderTypes` / `renderBatched`（`RAD:…/ChunkBuilderBuiltChunkMixins.java`、`client/proxy/world/ChunkProxy.java:1716-1758`）<br>• 实体：`StorageVertexConsumerProvider` 捕获 MultiBufferSource |
| **2. Java ↔ 原生通信** | `RemixAPI` → `RemixBridge`（static native）→ `remixbridge.dll` → `d3d9.dll`（`remixapi_InitializeLibrary`）；句柄是 `long` | `client/proxy/vulkan/*`、`client/proxy/world/*` 共 86 个 `native` 方法，原生库为 `core.dll` / `libcore.so`（`RAD:build.gradle:51`）；顶点通过直接内存地址传递（`EntityProxy.java:1168-1180`） |
| **3. 顶点、纹理、PBR 传递** | `HardcodedVertex` 64 字节（含 sprite UV 边界）；图集级 PBR 图；LabPBR 解码（`NV:M/texture/LabPBRConverter.java`） | 原版顶点格式按 `Constants.VertexFormats` 映射，未知格式直接抛异常（`RAD:client/constant/Constants.java:80-117`）；按基础纹理 GL id 关联 `_s` / `_n` / `_f` 辅助纹理（`RAD:client/texture/AuxiliaryTextures.java:30-35`），独立实体纹理也适用 |
| **4. 区块变化 / 动态 Mesh** | 页级替换并退役旧页；变化几何每帧新建 transient mesh | 区块按 setDirty 重建；实体每帧重新捕获并上传 |
| **5. 相机、运动矢量、深度** | 只提交参数化相机，**没有 jitter、没有上一帧数据**，由 runtime 自行推导（`NV:M/RemixRenderer.java:1190-1256`；`remix_c.h:690-697`） | 原生端保留**上一帧的顶点和索引缓冲及模型矩阵**，命中着色器按相同 primitiveID 加重心坐标取上一帧位置，从而得到逐顶点运动（`MCVR:src/shader/world/ray_tracing/internal/vanilla-pt/world/default.rchit:633-660`）；jitter 传给 DLSS（`MCVR:…/dlss/dlss_wrapper.cpp:538-541,660-666`） |
| **6. 如何调用路径追踪器** | 私有 runtime 内部执行 | `vkCmdTraceRaysKHR`（`MCVR:src/core/vulkan/command.cpp:375`）；BLAS / TLAS 构建在 `MCVR:src/core/vulkan/as.cpp:248-844`；ReSTIR 加 SHARC（`Shaderpack-Advanced` README） |
| **7. DLSS 由谁实现** | runtime 内置，Mod 只选档位 | **MCVR 直接调用 NGX Vulkan**：`NVSDK_NGX_VULKAN_Init`、`NGX_VULKAN_EVALUATE_DLSS_EXT` / `_DLSSD_EXT`（`MCVR:…/dlss_wrapper.cpp:107,552,669`），**没有使用 Streamline** |
| **8. 可参考 / 合法复用** | **可复用**（Apache-2.0，保留 NOTICE）：`rtxremix-java`、`GlInterop` 思路 | **专有项目不可复用代码**（GPLv3）。只可参考公开的架构事实 |
| **9. 依赖 NVIDIA 专有运行时的部分** | 整个 runtime（私有构建 `minecraft-rtx2-71a581da`，C API 0.16.0；公开 dxvk-remix 为 0.6.6） | DLSS（`nvngx_*.dll`，由用户自行放入 `<instance>/radiance/`，`RAD:README.md`「Optional DLSS runtime libraries」）、NRD 与 SHARC（NVIDIA RTX SDKs License，见 §8） |
| **10. 能否只复用原生组件** | 能：外观层、JNI、runtime 与 MC 无关（第一份报告 §2.5–2.6）；但 runtime 是私有构建且只支持 Windows | MCVR 是 GPLv3，专有项目不能链接 |

### 2.2 两个独立实现的共同结论

这些是公开、通用的图形工程技术，不是任何一方的专有表达：

1. **保留 OpenGL 上下文绘制 GUI，Vulkan 负责世界，双方通过外部内存和信号量共享图像。**
   - NVIDIA：`NV:M/gl/GlInterop.java`，`GL_EXT_memory_object(_win32)` 和 `GL_EXT_semaphore(_win32)`。
   - Radiance 默认构建 `radianceUiBackend=opengl`（`RAD:build.gradle:33-35`）：`RAD:client/opengl_ui/OpenGLUiBridge.java:96-204`，同一组扩展，并支持 Linux 的 `_fd`。
2. **GL 不再 SwapBuffers，由 Vulkan swapchain 显示合成后的整帧。**
   - NVIDIA：`NV:M/mixin/RenderSystemMixin.java`。
   - Radiance：`RAD:mixins/opengl_ui/OpenGLUiRenderSystemMixins.java` 把 `flipFrame(J)V` 中的 `glfwSwapBuffers` 置空。
   - 附带确认：**1.20.1 的 `RenderSystem.flipFrame(long)` 内确有 `glfwSwapBuffers` 调用**。第一份报告 §2.2 中这一项原为待核实，现已由 Radiance 的 Forge 1.20.1 Mixin（`defaultRequire 1`）间接证实。
3. **世界画面在 HUD 之前以全屏方式合成进 GL 帧缓冲。** 因此，**在世界阶段直接用 GL 绘制的第三方内容都会被覆盖**。
   - Radiance 的合成点：`RAD:mixins/opengl_ui/OpenGLUiGameRendererMixins.java` 重定向 `GameRenderer.render` 中的 `RenderTarget.bindWrite` → `compositePreparedWorld`。
4. **只有经过 MultiBufferSource / VertexConsumer 的绘制能进入场景。**
   - Radiance 对 `net.minecraft` 的 `VertexBuffer` **没有任何 Mixin**（`grep -rn VertexBuffer` 只命中 Distant Horizons 适配）。
   - OpenGL UI 构建会**排除**捕获 `BufferUploader._drawWithShader` 的 `BufferRendererMixins`（`RAD:build.gradle:74-96`），只保留天气捕获（`OpenGLUiBufferRendererMixins`）。
   - Forge 的 `RenderLevelStageEvent` 由 Radiance 自己派发，并用 `EntityProxy.captureWorldRender` 收集 `bufferSource()` 中的内容（`RAD:mixins/vulkan_render_integration/WorldRenderEventMixins.java`、`EntityProxy.java:828-864`）。

**对 AFL 的含义**：NVIDIA 和 Radiance 两条路线，都看不到 AFL 的 Vextris GPU 缓存、自有 VBO 水体、近处植物、道路。这不是某一方的缺陷，而是"捕获式架构"的结构性限制。AFL 自研渲染器的核心价值正好在这里：**让 AFL 内容直接提交，而不是被动捕获。**

---

## 3. Radiance 专项（回答用户 7 个问题）

### 3.1 RTX 与 DLSS 是否真正可运行？

- **源码层面，完整调用链存在：**
  - 设备要求硬性包含 `VK_KHR_ray_tracing_pipeline`、`VK_KHR_acceleration_structure`、`VK_KHR_deferred_host_operations`（`MCVR:src/core/vulkan/physical_device.cpp:112-125`）。
  - 有真实的 AS 构建和 `vkCmdTraceRaysKHR` 调用。
  - DLSS SR 与 RR 各有模块：`dlss_sr_module.cpp`（526 行）、`dlss_rr_module.cpp`（629 行）。
  - 评估调用传入了颜色、深度、MV、jitter、reset；RR 还传入了 diffuse / specular albedo、法线粗糙度、specular hit distance（`dlss_wrapper.cpp:530-549,651-666`）。
  - FG 有 NGX 特性查询（`:375,398`）。
  - FSR 通过 FidelityFX ffx-api 接入（`MCVR:CMakeLists.txt:129-133`），另有 XeSS 和 NRD 模块。
- **本地未运行验证**（无 GPU）。公开 issue 显示存在运行期问题：
  - **#277**：RTX 4060 Laptop 在 `assembleNRDFSRInternal` / `assembleDLSSRRInternal` 管线组装时发生 C++ 异常。
  - Intel Arc 无法进入游戏或载入世界：**#261、#268、#284**。
- **判定**：
  - "已实现（源码完整）"。
  - "在 RTX 3080 Ti 上可运行"属于高可能，但需在 Windows 上实测（见 §9 的 R0）。
  - **无非光追回退**：RT 扩展是 required。

### 3.2 1.20.1 Forge 版本的已知限制

**源码可证的：**

- **硬件与系统**：
  - 必须有硬件光追（见上）。
  - Forge / NeoForge 必须在高性能 GPU 上启动，因为早期加载窗口的 GL 上下文必须与 Vulkan 在同一块 GPU 上（`RAD:README.md` FAQ）。
- **DLSS 运行库**：需要用户自行下载 `nvngx_dlss.dll` 等并放到 `<instance>/radiance/`，Radiance 不随包分发（README）。
- **不捕获的绘制**：`VertexBuffer.drawWithShader` 以及世界阶段的原生 GL 绘制（§2.2-4）。
- **原版 `LevelRenderer.renderLevel` 被整体取消**（`WorldRendererMixins.java:392`）。任何依赖原版地形绘制流程的 Mod 都会失效。
- **区块 GL 缓冲被置空**：`RenderChunk` 构造中的 `collect` 被重定向为 null（`ChunkBuilderBuiltChunkMixins.java` 中的 `cancelCollect`）。对应 issue **#278**：仍调用 `LevelRenderer.renderSectionLayer` 的 Mod 会直接崩溃。
- **兼容性检测与 API**：
  - 没有任何 Embeddium / Oculus / Sodium / Iris 检测代码（`grep` 无结果）。
  - `mods.toml` 只声明了可选依赖 Distant Horizons 和 Flywheel。
- **几何格式的隐性假设**：
  - 实体层强制转换为 `RenderType.CompositeRenderType`（`EntityProxy.java:1133`；`Constants.java:163`）。
  - 断言索引数 = 顶点数 / 4 × 6，即假定 QUADS（`EntityProxy.java:1181-1183`）。
  - 外部 PBR 路径只在 QUADS 下启用（`PBRVertexConsumer.java:561-565`）。
- **没有公开 API**：没有 `api` 包。issue **#283**（2026-10-04，请求"公开世界几何提交 API"）尚无维护者回复；#253 也是类似请求。

**Issue 报告的（未在本地复现）：**

- **#273**：世界阶段的线框几何会让 `EntityProxy.queueBuildNative` 抛出 "Shouldn't be touched" 并导致 JVM 崩溃。
- **#271**：游戏路径中含非 ASCII 字符会导致启动崩溃。
- **#276**：RenderSystem 被从错误线程调用。
- **#281**：进入世界后 HUD 渲染崩溃。

### 3.3 与 AFL 内容的兼容性（基于 AFL 现有渲染路径推断，未运行）

| AFL 内容 | 现有路径 | 在 Radiance 下的预期 |
|---|---|---|
| 平滑 Mesh 地形 / 岩石 / 土方 | `BakedModelWrapper` + ModelData（`AFL:…/terrainsmooth/client/SmoothSurfaceModel.java`） | **大概率能进入光追场景**：Radiance 区块构建走 Forge `getModelData` / `getRenderTypes` / `renderBatched` |
| Mesh 植物 / 树（静态） | `IDynamicBakedModel` | 能进入；**风动画丢失**（风写在 Embeddium 顶点着色器或 Aureveil 中） |
| 近处植物（踩踏） | 自有 `VertexBuffer` | **不可见**（被世界合成覆盖） |
| Block-Mesh V2 / 骨骼身体 | CPU LBS → entity consumer（QUADS） | 能进入；MCVR 的上一帧缓冲机制理论上可以给出正确的逐顶点 MV |
| Vextris 蒙皮（幸存者，TRIANGLES） | `renderSkins`，三角形 | **有风险**：QUADS 断言与外部 PBR 只认 QUADS（见 §3.2） |
| 原生枪械 / T15 / 道路（Vextris GPU 缓存） | `MeshGpuCache` / `GpuGeometry` 持久 GPU 缓冲 | **不可见**；可用 `-Dvextris.gpuCache=false` 回退 CPU，但性能差（T15 约 40 万顶点/帧，`AFL:docs/dev/vextris_gpu_cache_request.md:46-58`） |
| 物理水 / 瀑布 | 私有 BufferBuilder → `VertexBuffer.drawWithShader` + 自有 ShaderInstance（`AFL:…/client/liquid/ClientLiquid.java:358-518`） | **不可见** |
| TaCZ | **AFL 并不依赖 TaCZ**：只有 `-PaflWithTacz` 可选加载（`AFL:build.gradle:244-253`），`src/main/java` 中无引用 | TaCZ 本身走 GeckoLib 或自绘，需单独测试；与 AFL 原生枪械无关 |
| Embeddium / Oculus | AFL 运行时默认加载（`AFL:build.gradle:233-242`） | 必须移除：两者都要接管世界渲染，且 Radiance 无检测；AFL 的风和地形动态光随之失效 |
| AFL DynamicLights（24 盏 spot） | 写入 Aureveil `customTexture` 或原版 shader 补丁 | **无接口**：Radiance 没有显式灯光 API |

### 3.4 能否用独立适配层解决？

- **不碰 Radiance 代码的部分可以**：
  - AFL 侧检测到 Radiance 时，把不可见内容改走 MultiBufferSource：Vextris 关闭 GPU 缓存，水体改为 `bufferSource().getBuffer(...)`。
  - 这只是 AFL 自己的代码选择，不受 GPL 约束。
  - **代价**：大量 CPU 顶点吞吐，T15、枪械架这类场景会回到当初需要 GPU 缓存的性能问题。
- **想高效接入**（直接提交 mesh、显式灯光、水体材质），就需要 Radiance 内部接口：
  - 要么等上游的 #283 API；
  - 要么 Mixin 进 Radiance 内部类。后者形成与 GPLv3 代码的紧密结合，**该适配 Mod 应按 GPLv3 发布**，且随 Radiance 内部变动而失效。
- **判定**：可以做出"能看"的兼容，做不到"高质量 + 高性能"的 AFL 专项表现。

### 3.5 是否还值得从零开发 Vulkan 渲染器？

在用户的定位下（AFL 专项、专有核心、可控），**仍有价值**。理由：

1. Radiance 是通用渲染器，捕获式架构天然看不到 AFL 的 GPU 缓存、水体、灯光数据。
2. GPLv3 与专有目标不兼容，不能 Fork 后闭源。
3. NVIDIA runtime 是私有构建、只支持 Windows，且不开放源码。

但 Radiance 的存在**显著降低了技术不确定性**：Forge 1.20.1 + Java 17 + LWJGL 3.3.1 + GL/Vulkan 互操作 + NGX Vulkan DLSS 这条路已被证明可行，**剩下的主要是工作量和质量，而不是可能性。**

### 3.6 能否提交上游兼容性改进？

- **可以**：仓库接受 issue 和 PR（README「Mod compatibility」节明确欢迎），`RAD:` 中未见 CLA 文件。
- 提交的代码将以 GPLv3 发布。**作者仍然拥有自己贡献代码的版权**，可以另行许可自己的那份；但不能把 Radiance 其他人的代码带回专有核心。
- **建议**：
  - 由不参与 AFL 专有核心编写的人负责上游贡献，或者只提交 bug 报告和最小复现，以降低"代码混入"的争议风险。
  - 以 issue 形式推动 #283 公开 API。如果它落地，AFL 侧只需写一个提交适配（该适配需与 GPL 兼容，见下）。

### 3.7 GPLv3 对 Fork、修改和公开发布的影响

以下为技术性归纳，**不是法律意见**，发布前需法务确认。

| 场景 | 影响 |
|---|---|
| 私下 Fork、修改、内部测试，不分发 | GPLv3 不要求公开源码 |
| 公开发布修改后的 Radiance 或 MCVR（JAR 或 DLL） | 必须以 GPLv3 提供完整对应源码，包括 MCVR 原生部分与构建脚本；保留版权与许可证声明；**不能改为专有许可** |
| 专有代码与 Radiance 静态或动态链接、或通过 Mixin 深度结合后一并分发 | 通常被视为"组合作品"，组合整体需按 GPLv3 发布 → **与 AFL Renderer 专有目标冲突** |
| AFL 只提供数据（资源包、配置），或只走原版 MultiBufferSource 路径 | AFL 不构成 Radiance 的衍生作品，不受 GPL 约束 |
| 第三方许可证张力（Radiance 自身） | MCVR 编入了 NVIDIA RTX SDKs License 下的 NRD 与 SHARC（`MCVR:CMakeLists.txt:140-163`；许可证见 `Ljiong201108/NRD:LICENSE.txt`、`NVIDIA-RTX/SHARC:License.md`）。该许可证禁止 SDK "subject to an open source software license"（`NGX:LICENSE.txt:27`）。Radiance 不分发 DLSS DLL，可能正是为了回避这一点，但 NRD 与 SHARC 是否同样处理需另行确认。**Fork Radiance 会继承这一不确定性** |

---

## 4. 独立渲染器架构候选

### 4.1 三类方案的明确区分

| 类型 | 定义 | 例子 |
|---|---|---|
| **真正独立渲染器** | 自己拥有设备、管线、光追与降噪、上采样调用 | 候选 A、B、C；Radiance / MCVR 属于此类 |
| **使用原生渲染运行时的适配器** | 只负责采集场景和提交，渲染由第三方 runtime 完成 | 候选 D（NVIDIA RTX Remix） |
| **传统 Shader Pack** | 在 Iris / Oculus 的 GL 管线中写 GLSL，不控制几何提交 | Aureveil |

### 4.2 候选路线比较

| 维度 | A. Forge + OpenGL 自研 | B. Forge + Vulkan（推荐） | C. Forge + D3D12 | D. 捕获 + RTX Remix Runtime | E. 捕获 + 其他现成框架（Radiance / MCVR） |
|---|---|---|---|---|---|
| 替换原版渲染难度 | 中（GL 内替换） | 中高（互操作模式下保留 GL GUI） | 高（无 LWJGL 绑定，全部走 C++） | 中（已有参考实现） | 低（直接使用） |
| Forge 1.20.1 兼容 | 好 | 已被 Radiance 证明可行 | 未见先例 | 需重写接入层（见第一份报告） | 已有 Forge 版本 |
| Java / JNI 开销 | 无 | 中（批量提交） | 中 | 中 | 中 |
| Chunk Mesh 管理 | 自研 | 自研 | 自研 | runtime 负责 BLAS | 现成 |
| 动态模型 | 自研 | 自研，可支持 GPU 蒙皮与上一帧数据 | 同 B | 无原地更新，每帧新建 mesh | 每帧捕获 |
| PBR | 可做 | 可做 | 可做 | LabPBR 现成 | 现成 |
| **硬件光追** | **不可能**（GL 无 RT；NV 的 GL 光追扩展已过时） | KHR RT pipeline / ray query | DXR | runtime 路径追踪 | 现成，且强制要求 |
| **DLSS 集成** | **不可直接集成**：NGX 与 Streamline 都不支持 GL（`SL:include/sl_device_wrappers.h:12-18` 只有 D3D11/12/Vulkan；`NGX:include/nvsdk_ngx.h:150-156` 只有 D3D11/12/CUDA，外加 `nvsdk_ngx_vk.h`；`include/` 中无 GL 头）。只能借 Vulkan 互操作绕行 | NGX Vulkan 或 Streamline Vulkan | Streamline 的主路径 | runtime 内置 | 现成 |
| NVIDIA / AMD | 两者 | 两者（AMD 走 FSR，RT 需 RDNA2 及以上） | 两者（仅 Windows） | **仅 NVIDIA RTX 30 及以上** | 两者（Intel 有问题） |
| CPU / GPU 风险 | GL 驱动开销大 | 同步与内存管理复杂 | 同 B，且无 Java 生态 | 不可控（私有） | 不可控 |
| 许可证 / 发布 | 自主 | 自主（依赖 LWJGL BSD 等） | 自主 | runtime 受 NVIDIA 条款约束；私有构建 | **GPLv3，与专有目标冲突** |
| **专有核心可行性** | 是，但无 RT 和 DLSS | **是** | 是 | 部分（runtime 不归我们） | **否** |

---

## 5. Vulkan 专项（用户补充的 6 个重点）

### 5.1 Forge 1.20.1 的 OpenGL 如何与 Vulkan 共存；完全替换 OpenGL 的成本

**三种模式：**

| 模式 | 做法 | 先例 | 对 AFL 的代价 |
|---|---|---|---|
| **M1 / M2 互操作（推荐）** | 保留 MC 的 GL 窗口与上下文（GUI、HUD、未支持的 Mod 继续用 GL）；Vulkan 渲染世界，再通过 `VK_KHR_external_memory(_win32)` + `GL_EXT_memory_object_win32` 与信号量共享。M1：GL 显示（最简单）。M2：Vulkan swapchain 显示整帧（为 FG 预留） | NVIDIA（M2）、Radiance 的 OpenGL UI 构建（M2） | 低：AFL 的 GUI 和 GL 代码不受影响 |
| M3 完全替换 | `GLFW_NO_API`，不创建 GL 上下文，再用 Mixin 模拟 GL 调用 | VulkanMod：`VM:…/mixin/window/WindowMixin.java:69`（`GLFW_NO_API`）、`GL.createCapabilities` 返回 null（`:62-65`）；GL11/15/30 的模拟合计仅 421 行（`VM:…/mixin/compatibility/gl/*.java`）；core shader 用 `GlslConverter` + shaderc 转换（`VM:…/vulkan/shader/parser/GlslConverter.java`、`mixin/render/ShaderInstanceM.java:229-237`）；Radiance 的 Vulkan UI 构建（`RAD:…/WindowMixins.java`） | **极高**：AFL 自身有 27 个文件 824 处 `GLxx.` 直接调用，19 个文件用 `ShaderInstance`，47 个 core shader，32 个 GLSL 文件（本次 grep 统计）；Embeddium、Oculus、GeckoLib 等依赖还有更多 GL 代码 |

**结论**：不做 M3。AFL Renderer 采用 M2，并保留 M1 作调试回退。

### 5.2 借鉴 VulkanMod，而不假定它兼容 Forge 或 AFL

**事实：**

- VulkanMod 是 Fabric 项目（`VM:build.gradle` 使用 `fabric-loom`）。
- `1.20.x` 分支目标为 MC 1.20.3/1.20.4（`VM:src/main/resources/fabric.mod.json`）。
- 标签中没有 1.20.1：`0.3.2` 对应 1.19.4，`0.3.3` 对应 1.20.2（`git show <tag>:gradle.properties`）。
- **无光追**（`grep acceleration_structure|ray_query` 无结果）。
- 许可证 **LGPL-3.0**。

**可借鉴的（只借思路，不复制代码，因为 LGPL 修改部分需要开源）：**

- 区块 area buffer 加 indirect draw（`VM:…/render/chunk/ChunkArea*.java`）。
- 通过 LWJGL 使用 VMA 和 shaderc（`VM:build.gradle:21-33`，lwjgl-vulkan / vma / shaderc 3.3.2）。
- core shader 自动转换的做法。

**注意**：MC 1.20.1 自带 LWJGL 3.3.1。若引入 `lwjgl-vulkan` 等，版本必须与之匹配，需在 MDK 中核对；VulkanMod 用的 3.3.2 对应 1.20.4。

### 5.3 自定义 Mesh 如何进入 Vulkan

核心是 **"AFL 场景提交 API"**，提交数据而不是 GL 绘制：

```
afl-renderer-api（公开、宽松许可证，纯 Java 接口）
  RenderScene.submitStaticMesh(meshId, vertices, indices, materialId)      // 一次性
  RenderScene.submitSkinnedMesh(meshId, bindPose, weights, indices, mat)   // 一次性
  RenderScene.instance(instanceId, meshId, transform, prevTransform, flags)// 每帧
  RenderScene.boneMatrices(instanceId, float[] current, float[] previous)  // 每帧
  RenderScene.waterSurface(surfaceId, vertices, prevVertices, waterParams) // 变化时
  RenderScene.light(lightId, type, position, direction, cone, radiance)    // 每帧
  Materials.define(materialId, albedo, normal(LabPBR), specular(LabPBR), wetness, emissive)
```

| AFL 系统 | 进入方式 |
|---|---|
| Vextris `MeshGpuCache`（枪械、T15、弹匣） | 按（模型, 骨骼, 层）调用 `submitStaticMesh` 一次，每帧只传骨骼矩阵。与缓存的现有结构一一对应 |
| Vextris 蒙皮 / Block-Mesh V2 / 幸存者 | `submitSkinnedMesh` + 每帧骨骼矩阵；**在 GPU 上用 compute 蒙皮**，同时得到上一帧位置，解决 MV |
| 平滑 Mesh 地形 | 走原版和 Forge 的 ModelData 区块构建（参考 Radiance 也走这条路），或 AFL 直接提交 |
| 物理水 | `waterSurface`（顶点 + 上一帧顶点 + 水体参数），专用水体材质 |
| 动态植被 | 静态几何走区块；把风函数移植到渲染器的顶点或 compute 阶段（AFL 已有 CPU 侧风数学，`VegetationWindMath`、`afl_vegetation` 数据），并用上一帧时间算 MV |
| 原版或第三方实体 | 捕获 MultiBufferSource（通用兜底） |
| 灯光（DynamicLights / 车灯 / 室内） | `light(...)` 直接进入光源列表（RT 下直接光照与阴影；非 RT 下为 clustered forward 或 deferred） |
| TaCZ | AFL 不依赖 TaCZ；如需支持，走通用捕获，另行评估 |

**`afl-renderer-api` 与 AFL 的关系**：AFL 以 compileOnly 依赖 API；运行时检测渲染器是否存在，与 AFL 现在用反射对接 Oculus 的方式一致。

### 5.4 GLFW 窗口、GL 上下文、Vulkan Surface、GUI

- **窗口**：沿用 MC 的 GLFW 窗口与 GL 上下文。注意 Forge 1.20.1 有早期加载窗口；Radiance 用 `ImmediateWindowHandler.setupMinecraftWindow` 接管它（`RAD:…/OpenGLUiWindowMixins.java`）。
- **Surface**：GLFW 的 `glfwCreateWindowSurface` 要求窗口以 `GLFW_NO_API` 创建（GLFW 文档，外部知识）。因此在 M2 下，应当用 `GLFWNativeWin32.glfwGetWin32Window` 取 HWND，再直接 `vkCreateWin32SurfaceKHR`。NVIDIA 正是在同一 HWND 上建立 Vulkan swapchain（`NV:M/RemixRenderer.java:298-302,1340-1348`）。
- **GPU 一致性**：GL 与 Vulkan 必须在同一块 GPU 上。用 `GL_EXT_memory_object` 的 device UUID 与 Vulkan 的 `VkPhysicalDeviceIDProperties` 比对（Radiance 也做了，`OpenGLUiBridge.java:281-305`）。
- **GUI**：在 GL 中绘制，然后整帧拷入 Vulkan 再显示。**DLSS SR 必须在 GUI 之前、对世界图像单独执行**，GUI 以原生分辨率叠加。

### 5.5 能否先在引擎外验证 RTX 3080 Ti 的硬件光追与 DLSS SR？

**能，而且应该先做（§9 的 S0）。**

- RTX 3080 Ti（Ampere）支持 `VK_KHR_ray_tracing_pipeline` / `ray_query`，支持 DLSS SR、RR、DLAA，**不支持 DLSS FG**（外部常识；NVIDIA 要求 RTX 40 及以上）。
- Streamline 要求 Vulkan 1.2 及以上、驱动 ≥ 512.15（`SL:README.md:19-23`）。
- 用 C++ 写一个独立小程序：BLAS / TLAS → 光追阴影与反射 → 低分辨率 + jitter + MV → DLSS SR → 显示。
- 这一步与 Minecraft 无关，几天到两周可以出结论，并且能沉淀专有原生核心的最初骨架。

### 5.6 第三方 Forge Mod 直接调用 OpenGL 时如何处理

| 方案 | 说明 | 建议 |
|---|---|---|
| 兼容层（模拟 GL） | 即 M3 路线 | 不做 |
| 双后端：GL 叠加通道 | 第三方在世界阶段的 GL 绘制，放到 Vulkan 世界合成**之后**，用共享深度（导入 GL 做深度测试）叠加。NVIDIA 的雨雪就是这样处理的（`NV:M/weather/RemixWeatherRenderer.java:296-317`）。它们**可见但不参与光追** | 作为兼容兜底，P4 之后再做 |
| 明确列为不兼容 | 对 Oculus、Embeddium 等接管世界渲染的 Mod，检测到即拒绝启用 RTX 模式 | 必须做 |

### 5.7 Vulkan 与 D3D12 比较

| 维度 | Vulkan | D3D12 |
|---|---|---|
| Java 绑定 | LWJGL `lwjgl-vulkan`（Windows 上是纯 Java，无原生库）；MC 1.20.1 已带 LWJGL 3.3.1 核心 | LWJGL **无 D3D12 绑定**，全部需要 C++ 加 JNI |
| 与 GL 互操作 | `GL_EXT_memory_object_win32` / `GL_EXT_semaphore_win32`，**两个 MC 项目已经在用** | 同组扩展有 D3D12 资源和 fence 句柄类型（外部知识），但 MC 圈内无先例 |
| 光追 | KHR RT pipeline / ray query | DXR 1.1 |
| DLSS | NGX Vulkan（`NGX:include/nvsdk_ngx_vk.h`）、Streamline Vulkan（`sl_device_wrappers.h`） | Streamline 主路径，FG 支持最成熟 |
| 平台 | Windows 与 Linux | 仅 Windows |
| AMD / Intel | 支持（Intel 在 Radiance 中问题多） | 支持 |
| 工具 | RenderDoc、Nsight Graphics | PIX、Nsight Graphics |
| **结论** | **推荐** | 只有将来以 FG 为重点且确定仅支持 Windows 时才值得重新评估 |

---

## 6. DLSS 可行性（基准：RTX 3080 Ti；优先 SR；FG / MFG 不是必要条件）

1. **能否通过 Streamline 接入？——能。**
   - Streamline 支持 Vulkan：把 `sl.interposer.dll` 当作 `vulkan-1.dll` 加载（`SL:docs/ProgrammingGuide.md:32`），或者手动 hook，调用 `slSetVulkanInfo`（`:130,595-600`）。
   - 也可以像 MCVR 那样直接用 NGX Vulkan（`nvsdk_ngx_vk.h`）。
   - **建议**：原生核心用 Streamline。它是 MIT 许可，统一了 DLSS、Reflex 和后续 FG 的接入；只有当 Streamline 的 interposer 与 LWJGL 的加载方式冲突时，才退回 NGX 直连。
2. **选定 API 是否满足 SDK 要求？——Vulkan 满足；OpenGL 不满足。**
   - Streamline `RenderAPI` 只有 `eD3D11` / `eD3D12` / `eVulkan`（`SL:include/sl_device_wrappers.h:12-18`）。
3. **运动矢量如何提供？**
   - 需要标记 `kBufferTypeMvec`，并设置 `mvecScale`、`cameraMotionIncluded`、`motionVectors3D` 等常量（`SL:docs/ProgrammingGuideDLSS.md:128-158`；`ProgrammingGuide.md:1147-1230`）。
   - 静态地形：只需相机运动（`clipToPrevClip`）。
   - 实体与载具：需要每实例的上一帧变换。
   - 蒙皮：需要上一帧骨骼矩阵（GPU 蒙皮时计算两次）。
   - 水体与植被风：需要上一帧顶点或上一帧时间。
   - MCVR 的"保留上一帧缓冲"是一种可选做法；**专有实现应按标准方法（prev transform / prev skinning）独立设计**。
4. **深度、曝光、抖动如何提供？**
   - 深度：标记 `kBufferTypeDepth`，并设置 `depthInverted`。
   - 曝光：可选标记 1×1 曝光纹理；不提供时 DLSS 自动曝光（`ProgrammingGuideDLSS.md:157-158`）。
   - 抖动：投影矩阵**不含** jitter，jitter 单独以 `jitterOffset` 传入（`ProgrammingGuide.md:1155-1170`，"must NOT contain temporal AA jitter offset"）。
   - 渲染器必须自己控制投影矩阵，Radiance 也在 `GameRenderer.renderLevel` 处重建矩阵（`OpenGLUiGameRendererMixins.java` 中的 `performBTimesV`）。
5. **动态 Mesh、植被、水面如何处理？** 见 3。另需注意：
   - 半透明水面必须单独处理，例如作为不写入 MV 的 translucent 层，或使用 RR 的 transparency 输入，否则会出现拖影。
   - 粒子可以不写 MV，交给 DLSS 的 reset / 反应式掩码策略处理。
6. **DLSS 输出如何回到窗口？** 上采样后的世界图像 → 共享图像 → GL 合成 GUI → Vulkan swapchain 显示（M2）。
7. **许可证与分发**：
   - Streamline 本体是 MIT。
   - DLSS SDK 与 DLL 受 NVIDIA RTX SDKs License 约束：
     - 只能"incorporated in object code format into a software application"（`NGX:LICENSE.txt:12`）；
     - 不能单独分发（`:24`）；
     - 不能使 SDK 受开源许可证约束（`:27`）；
     - 须在启动画面、关于页、制作人员名单中署名并放置 NVIDIA 商标（`:89`）；
     - 分发条款至少同等保护（`:17`）。
   - **对专有核心而言反而比 GPLv3 项目更容易合规**（Radiance 选择不分发 DLL）。
   - **不需要向 NVIDIA 申请应用 ID**：可以用自定义的 GUID 形式 Project ID 加 `NVSDK_NGX_ENGINE_TYPE_CUSTOM` 初始化。另有 DLL 签名、放置位置、第三方声明等发布要求，见 §6.9。
8. **无法绕过的接口阻碍：**
   - (a) **OpenGL 不能直接调用 DLSS**。绕行办法是 Vulkan 互操作，两个项目已验证可行。
   - (b) **运动矢量必须由真正绘制几何的渲染器生成**。这是 Aureveil 路线的硬阻碍：`AUR:shaderpack/shaders/program/taa.fsh:52-57` 写明 "Oculus 1.8 gives no previous positions (no velocity attribute)"；Aureveil 虽有 Halton jitter（`AUR:…/lib/taa.glsl:24-27`），但实体 MV 拿不到。
   - (c) **在 Oculus 管线中插入"渲染分辨率视口 + DLSS + 原生分辨率 GUI"需要改动 Oculus 内部**。

**DLSS 能否独立于完整路径追踪？——能。**

- DLSS SR 本来就是为光栅游戏设计的时域上采样器，只需要光栅输出的颜色、深度、MV 和 jitter。
- 前提是渲染器自己拥有几何提交与 MV 生成，所以**"Vulkan 光栅 PBR + DLSS SR（不开光追）"是合理的第一个产品形态**。
- 光追在其上作为可选开关。

**提醒**：DLSS SR 降低的是像素着色成本。AFL 当前的瓶颈多在 CPU 顶点吞吐（T15 约 40 万顶点/帧、BER、近处植物，见 `AFL:docs/dev/render_performance_v1.md`），这些 **DLSS 解决不了**。

### 6.9 DLSS Programming Guide 核对结果（2026-10-26 追加）

来源：`DLSSPG:` = NVIDIA/DLSS 仓库提交 9981c63（DLSS 310.9.16 SDK）中的 `doc/DLSS_Programming_Guide_Release.pdf`（文档日期 2026-03-31，共 84 页），以及同目录的 `RTX UI Developer Guidelines.pdf`（`RTXUI:`）。页码为 PDF 页脚页码。

**集成前提**

| 项目 | 指南要求 | 对 AFL Renderer 的含义 |
|---|---|---|
| 图形 API | 只支持 DirectX 11、DirectX 12 或 Vulkan；Vulkan 路径要求 Vulkan 1.1 及以上（`DLSSPG:` p.2 §2.2） | 与 §5.7 结论一致，OpenGL 不在列 |
| 硬件 / 驱动 / 系统 | NVIDIA RTX GPU；DLSS 2.4.11+ 要求 2022-03-03 之后的驱动（如 512.15）；Windows 10 v1709 64 位及以上（p.1 §2.1） | RTX 3080 Ti 满足 |
| 每帧输入 | 原始颜色、16 或 32 位精度的屏幕空间运动矢量、深度、HDR 时的曝光值（p.2 §2.2） | 与 §6 第 3–4 条一致 |
| 抖动 | 至少 16 个相位，32 个以上更好（p.2 §2.2）；推荐 Halton 序列，训练数据就用 Halton（§3.7.1）；相位数 = 8 ×（输出分辨率 / 渲染分辨率）²（§3.7.1.1） | Performance 档（0.5 倍）需要 32 个相位 |
| 运动矢量格式 | `RG16_FLOAT` 或 `RG32_FLOAT`，单位为渲染分辨率下的像素位移，指向上一帧；可通过 MV scale 换算（§3.6.1–3.6.3） | 实体、蒙皮、水体、植被都要按此标准输出 |
| 深度 | 默认近平面 0、远平面 1；反向深度需设置 `DepthInverted` 标志（§3.8） | 按渲染器实际约定设置 |
| 曝光 | 1×1 纹理（推荐 R16F），每帧提供；**只有预设 J 和 K 使用曝光输入，预设 L 总是自动曝光**（§3.9） | 由色调映射器统一给出 |
| 场景切换 | 重大切换后第一帧设置 `InReset`（§3.13） | 换维度、传送、死亡重生、关闭菜单时需要 reset |
| **纹理 Mip 偏置** | 启用 DLSS 时必须使用负 LOD 偏置：`NativeBias + log2(渲染宽 / 显示宽) - 1.0`，Performance 档约为 -2.0；指南也承认这可能带来闪烁和摩尔纹（p.14–15 §3.5） | **Minecraft 专属风险**：16×16 像素风贴图使用最近邻采样，负偏置很可能放大闪烁。需要在 S0 / P7 实测，并按材质分组调整 |

**初始化与 ID**

- 没有 NVIDIA 提供的 Application ID 时，用 `NVSDK_NGX_*_Init_with_ProjectID()`，传入 GUID 形式的 Project ID（例如 `a0f57b54-1daf-4934-90ae-c4035c19df04` 这种格式）、引擎类型 `CUSTOM` 和引擎版本。驱动会校验格式，失败原因写在 NGX 日志中（p.42 §5.2.1）。
- 只有在 NVIDIA 开发者关系人员提供了 Application ID 时，才改用 `NVSDK_NGX_*_Init()`（同上）。
- 结论：**AFL Renderer 自行生成一个固定的 Project ID 即可，无需申请**。
- 注意：部署检查表写的是"Game specific Application ID is used"（p.8 §2.5），与 §5.2.1 的 Project ID 路径并存。正式发布前可以向 NVIDIA 确认，单独记为合规待办。
- NGX API 不是线程安全的（§5.2.4）；在 Vulkan 下，命令缓冲状态由应用自己维护（§5.2.5）。

**发布要求（第 4 章，p.35–36）**

| 要求 | 内容 |
|---|---|
| 只能分发 release 版 DLL | 正式发布只能带 `lib/<平台>_<架构>/rel/` 下的 `nvngx_dlss.dll`；`dev/` 版带屏幕水印和调试叠加层，**禁止公开分发**（§4.2） |
| 放置位置 | 默认与游戏可执行文件同目录，作为插件时与插件 DLL 同目录；也可以通过 `InFeatureInfo` 中的 `NVSDK_NGX_PathListInfo` 指定搜索路径（§4.2） |
| 签名 | DLL 由 NVIDIA 签名，驱动（NGX Core）校验签名后才加载。如果对发行包做签名，必须**追加**签名，不能剥离 NVIDIA 的签名，否则 DLSS 无法加载（§4.2.2） |
| 卸载 | 安装程序卸载时要一并删除 DLL（§4.2.1） |
| 第三方声明 | DLL 内含第三方代码（CURL、8x13 bitmap font、d3dx12.h、xml、npy、stb、DirectX-Graphics-Samples、Vulkan-Headers，§9.6），需要在产品公开文档中收录其版权与许可证全文（§4.2.3） |
| 商标与署名 | 见 §6 第 7 条（`NGX:LICENSE.txt:89`） |

**对 Minecraft Mod 的含义**：Mod 没有自己的可执行文件，`java.exe` 不归我们控制，所以必须用 PathListInfo 指向 Mod 自己的原生库目录。Radiance 用 `<instance>/radiance/` 存放 DLL，也是这个思路（`RAD:README.md`）。

**3080 Ti 上的 DLSS 自身开销**

NVIDIA 用命令行工具实测（不含渲染器开销），模式为 Performance，即输入为输出的 1/4 像素（p.6–7 §2.4）：

| RTX 3080 Ti | 1920×1080 | 2560×1440 | 3840×2160 |
|---|---|---|---|
| 预设 E、F | 0.30 ms | 0.44 ms | 0.87 ms |
| 预设 J、K | 0.65 ms | 1.02 ms | 2.06 ms |
| 预设 L | 1.67 ms | 2.63 ms | 5.32 ms |
| 预设 M | 1.19 ms | 2.04 ms | 4.35 ms |

DLSS 内部显存占用，RTX 30 系、2560×1440：预设 J/K 约 144 MB，预设 M 约 207 MB（p.5）。

**含义**：在 1440p 下，DLSS 本身每帧要花约 1–2 ms。只有当原生分辨率的着色成本明显高于这个数时，SR 才有收益。纯光栅、着色很轻的场景收益有限，光追场景收益明显。

**OTA 更新**：`NVSDK_NGX_UpdateFeature` 可以选择接收预设的在线更新，需要在独立线程调用，下次初始化 NGX 时生效（p.5 §2.4）。是否启用属于产品决策。

**UI 规范要点（`RTXUI:` DLSS 节）**

- 模式顺序：Off、Auto、Quality、Balanced、Performance、Ultra-Performance、DLAA。检测到 RTX 硬件时默认选 Auto。
- 开启 SR 时，需要禁用其他抗锯齿和分辨率缩放选项。
- 需要提供锐度滑条，首选 NVIDIA Image Scaling。
- 不支持的硬件或驱动上要禁用 DLSS。
- **RR** 适用于所有 RTX 显卡，但必须同时开启 SR 和光追，否则要禁用。
- **FG** 需要 RTX 40 系及以上，并且必须集成 Reflex，FG 开启时用户不能关闭 Reflex；还要求 Windows 开启"硬件加速 GPU 计划"。在 3080 Ti 上不可用，与本报告"FG 不是必要条件"的定位一致。

---

## 7. AFL 专项需求：进入统一场景，还是需要额外适配

| 需求 | 统一场景（通用路径即可） | 需要 AFL 专项适配 | 说明 |
|---|---|---|---|
| Block-Mesh V2 人物 / 骨骼 | 部分（CPU 蒙皮结果可以被捕获） | **是** | GPU 蒙皮 + 上一帧骨骼矩阵 → 正确 MV 与性能 |
| TaCZ 枪械（第一 / 第三人称） | 走通用捕获 | 视情况 | AFL 不依赖 TaCZ；AFL 原生枪械见 Vextris 一行 |
| AFL 原生枪械 / 金属与湿润 | 否 | **是** | Vextris 缓存直接提交；金属 F0、湿润层（粗糙度降低、积水）作为材质参数 |
| 平滑 Mesh 地形 | **是**（Forge ModelData 区块构建） | 可选 | 也可以由 AFL 直接提交高质量法线 |
| 物理水连续 Mesh | 否 | **是** | 专用水体材质（折射、吸收、波浪法线），附带上一帧顶点 |
| 雨滴、水珠、湿润 | 否 | **是** | 世界湿润度作为全局参数；雨粒子单独处理 |
| T15 车辆 | 否 | **是** | 刚体部件实例化；玻璃作为透射材质；车灯作为显式光源 |
| 动态植被 | 静态几何是 | **风需要适配** | 风函数进入渲染器 shader |
| 城市建筑与室内动态光 | 方块光是 | **是** | 显式光源列表 + 光追阴影；室内依赖 GI 或 ReSTIR 才有意义 |
| 大量感染者 | 是（捕获） | 建议 | 同模型实例化（GPU instancing）+ GPU 蒙皮；否则 CPU 每帧上传是主要瓶颈 |
| 镜子 | — | 光追下不需要 | RT 反射天然覆盖；非 RT 模式下需要保留现有方案或用 SSR |

---

## 8. 许可证合规框架（专有核心）

### 8.1 模块与许可证边界

```
┌──────────────────────────────┐   公开、宽松许可证（建议 Apache-2.0 或 MIT）
│ afl-renderer-api  (Java)     │ ← AFL 与第三方 Mod 编译时依赖
└──────────────┬───────────────┘
               │ 只有接口与数据结构
┌──────────────┴───────────────┐   专有
│ afl-renderer-core (Java/Forge)│  捕获层、Mixin、JNI、资源追踪
└──────────────┬───────────────┘
┌──────────────┴───────────────┐   专有
│ afl-renderer-native (C++)     │  Vulkan、RT、降噪、上采样调度
└──────────────┬───────────────┘
               │ 动态加载、单独审核
   Streamline(MIT) · nvngx_dlss.dll(NVIDIA RTX SDK) · NRD(NVIDIA RTX SDK) · FSR(MIT) · XeSS(Intel)
```

### 8.2 第三方依赖与来源初始清单（发布前需复核）

| 组件 | 许可证 | 专有核心可用？ | 义务 |
|---|---|---|---|
| LWJGL（vulkan / vma / shaderc 绑定） | BSD-3 | 是 | 保留声明 |
| Vulkan-Headers、volk、VMA、glm | Apache-2.0 / MIT | 是 | 保留声明 |
| shaderc / glslang | Apache-2.0 / BSD 类 | 是 | 保留声明 |
| Streamline | MIT（`SL:license.txt`） | 是 | 保留声明 |
| DLSS / NGX（`nvngx_*.dll`） | NVIDIA RTX SDKs License | 是 | 只能内嵌在应用内分发；署名与商标；条款等同保护；禁止开源化 |
| NRD、SHARC | NVIDIA RTX SDKs License（`Ljiong201108/NRD:LICENSE.txt`、`NVIDIA-RTX/SHARC:License.md`） | 是 | 同上 |
| AMD FidelityFX（FSR） | MIT（外部知识；MCVR 所用 fork 根目录未见 LICENSE 文件） | 是 | 保留声明 |
| Intel XeSS | Intel Simplified Software License（仅二进制、不可修改，`intel/xess:LICENSE.txt`） | 是 | 按其条款再分发 |
| NVIDIA rtxremix-java / remixbridge | Apache-2.0 | 是（如走 D 路线） | NOTICE 与修改声明 |
| VulkanMod | LGPL-3.0 | **不并入核心** | 只作参考 |
| Radiance / MCVR | GPLv3 | **不并入核心** | 只作参考 |

### 8.3 流程要求（对应用户的 10 条原则）

- **外部代码准入**：先开"依赖审查记录"（来源 URL、版本、许可证、用途、审核人）。
- **参考记录**：阅读 Radiance、MCVR、VulkanMod 时只记录"结论与公开事实"，不保存代码片段；实现时不对照抄写。
- **决策日志**：每个架构决策写明依据，以及是否参考过某 GPL 项目。
- **商标**：NVIDIA、DLSS、RTX 的商标署名，按 `NGX:LICENSE.txt:89` 的要求放置。
- **发布前**：完成一次正式的许可证合规审查。

---

## 9. PoC 分阶段计划

**总原则：**

- 每阶段必须有截图或录屏、日志和帧时间数据。**"能编译"不算通过。**
- 所有工作放在独立仓库（例如 `afl-renderer/`），**不进 AFL 仓库**，不修改 AFL、Vextris、Aureveil。
- 需要 AFL 资源时只读拷贝。

| 阶段 | 内容 | 需要的文件 / 技术 / 接口 | 主要障碍 | 独立验证方法 | 成功标准（客观） | 失败处理 |
|---|---|---|---|---|---|---|
| **R0（可选，1–2 天）** | 在 3080 Ti 上运行 **Radiance 0.1.6 Forge 1.20.1 官方发行版**（纯净实例，不装 AFL） | 官方 JAR、`nvngx_dlss.dll` | 无 | 运行加截图 | DLSS SR 和 RR 选项可选并生效（内部分辨率下降）；记录 FPS | 仅作参照基准；**不 Fork、不读来改** |
| **S0：引擎外 Vulkan RT + DLSS SR** | C++ 独立程序：立方体场景 → BLAS / TLAS → RT 阴影与反射 → jitter + MV → DLSS SR | Vulkan SDK、VMA、Streamline 或 NGX、`nvngx_dlss.dll` | Streamline Vulkan 的加载方式；MV 正确性 | 自动截图对比；Nsight 帧捕获 | ① RT 阴影与反射正确；② DLSS SR 开启后内部分辨率为输出的 67% / 50% 且画面稳定、运动物体无明显拖影；③ 1440p 下 SR 关与开的 GPU 时间对比 | **失败即停止**（说明基础能力或团队能力不足） |
| **P0：Forge 启动模块** | Forge 47.4.22 MDK 中的空 Mod：加载原生 DLL，创建 Vulkan 实例与设备，与 GL 比对 device UUID | LWJGL `glfwGetWin32Window`、JNI、mods.toml | JDK 17 + Forge 类加载 + 原生加载；早期加载窗口 | 日志 | 设备创建成功；UUID 一致；30 分钟无崩溃 | 失败即停止 |
| **P1：动态三角形** | Vulkan 画一个旋转三角形 → 共享图像 → GL 合成到世界之前 → GUI 正常 | external memory / semaphore；`GameRenderer` 合成点 Mixin；`flipFrame` 显示 | 同步、帧节奏、窗口缩放 | 录屏 | 三角形与 HUD 同时正确显示；缩放窗口不崩；GL GPU 时间可测 | 失败即停止 |
| **P2：PBR 方块** | 一个带 LabPBR `_n` / `_s` 的方块 mesh；Vulkan 光栅 G-buffer + 方向光 PBR | 纹理上传；LabPBR 解码（独立实现） | 色彩空间、法线约定 | 与 Blockbench 或参考渲染对比截图 | 法线、粗糙度、金属的响应与 LabPBR 规范一致 | 可修复后继续 |
| **P3：一小片区块** | **不装 Embeddium / Oculus**；捕获 3×3 区块（Forge ModelData 路径，独立实现） | `RebuildTask` 或 ChunkRenderDispatcher 挂钩；区块缓冲管理 | Forge 补丁差异；与原版 `renderLevel` 的关系 | 与原版画面逐面对比 | 几何一致；改动方块 1 秒内更新；8 / 16 区块视距的帧时间 | 失败则先停在 P2，评估是否改为 AFL 直接提交 |
| **P4：动态实体 + 自定义 Mesh** | 通用 MultiBufferSource 捕获一个原版生物；`afl-renderer-api` 原型提交一个 T15 mesh（只读拷贝资源）和一个蒙皮 Block-Mesh B | API 原型；GPU 蒙皮 compute | 每帧上传成本；实例生命周期 | 录屏加 CPU / GPU 计时 | 动画正确；T15 每帧只传矩阵（CPU < 0.3 ms/辆，目标值）；蒙皮 GPU 化 | 继续，但记录瓶颈 |
| **P5：基础硬件光追** | 在 P3、P4 场景上加 RT 阴影与反射（可开关） | BLAS / TLAS 更新策略（静态 compact、动态 refit） | BLAS 重建成本；植被 alpha | Nsight | 开关可用；关闭后回到光栅 PBR；阴影与反射正确 | 失败则保留光栅 PBR，RT 冻结 |
| **P6：深度与运动矢量** | 相机 MV + 每实例上一帧变换 + 蒙皮上一帧 + 水体上一帧顶点 | MV 缓冲；jitter 序列 | 半透明、粒子 | MV 可视化调试视图 | 静止场景 MV≈0；平移、旋转与实体运动的 MV 方向和量级正确（数值抽检） | **失败则不进入 P7** |
| **P7：真实 DLSS SR** | 在 MC 内接入 SR；GUI 原生分辨率 | Streamline / NGX；曝光；reset | 设置切换、窗口缩放、reset 时机 | 录屏 + 内部分辨率日志 | SR 各档位生效；运动中无严重拖影；1440p 帧时间下降可量化 | 失败则保留 TAA 或 FSR |
| **P8：AFL 专用 Mesh 评估** | 用只读资源评估水体、植被风、湿润、室内灯光的 API 设计 | API 设计文档 | AFL 侧需要的改动范围 | 设计评审 | 列出 AFL 侧需要的最小改动清单（不实施） | — |

**终止条件汇总：**

- S0、P0、P1 任一失败，**整体停止**。
- P6 不达标，不做 DLSS。
- P3 帧时间若在 8 区块视距下劣于 Aureveil 方案的 2 倍且无明确优化路径，暂停扩展地形，重新评估。

---

## 10. 与第一份报告（RTX Remix 逆向移植）的对照

第一份报告已完成：`docs/rendering/rtx_remix_forge_backport_research_v1.md`，结论评级为 C。

| 方案 | 技术可行性 | 研发复杂度 | 可维护性 | 发布难度 | AFL 兼容性 | 性能潜力 |
|---|---|---|---|---|---|---|
| 1. 直接逆向移植 NVIDIA Mod | 低（地形入口、FRAPI、1.21 顶点管线须重写，见报告一 §6.1） | 高（约 5.8 万行接入层的大部分要重写） | 差（私有 runtime，受 NVIDIA 节奏约束） | 中（runtime 可随应用分发，但仅 Windows + RTX 30 及以上） | 差（AFL 自绘内容被覆盖） | 高（路径追踪 + DLSS / RR），但不可调 |
| 2. 自研 Forge 接入层 + NVIDIA Runtime | 中（复用 Apache 外观层与 JNI，C API 通用） | 中高 | 中（runtime 仍私有） | 中 | 中（可以直接提交 AFL mesh 和灯光） | 高；动态顶点 MV 无 API 支持 |
| 3. 完全自研 Vulkan 后端（本报告） | **中高（已有两个独立实现证明可行）** | **很高**（参考规模：Radiance + MCVR 约 9 万行，仍为 alpha） | **好**（自主） | 中（NVIDIA SDK 合规） | **最好**（为 AFL 设计） | 可以按 AFL 场景定向优化；上限取决于投入 |
| 4. 继续 Aureveil + Oculus | 已稳定 | 低 | 中（受 Oculus 版本约束） | 低 | 已深度适配 | 中；**无 DLSS**（实体 MV 不可得），无硬件光追 |
| 5. Radiance（参照项） | 高（已发布） | 低（使用）/ 高（深度适配） | 依赖上游 | **GPLv3**，与专有目标冲突 | 中低（同捕获式限制，见 §3.3） | 高（RT + DLSS RR） |

---

## 11. 推荐架构

1. **分层**：API（公开）/ core（专有 Java）/ native（专有 C++），见 §8.1。
2. **帧架构**：M2 互操作。GL 负责 GUI 与未支持的 Mod，Vulkan 负责世界，Vulkan 负责显示；DLSS SR 只作用于世界图像。
3. **渲染管线**：
   - 基础层：Vulkan 光栅 PBR（deferred）+ TAA 或 DLSS SR，非 RTX 显卡也能用。
   - 可选层：RT 阴影、反射、AO，之后再做 ReSTIR 或 GI。
   - **不以完整路径追踪为首个目标。**
4. **场景来源**：
   - AFL 内容通过 `afl-renderer-api` 直接提交（主要目标）。
   - 原版与第三方 Mod 通过 Forge ModelData 区块构建与 MultiBufferSource 捕获（基础兼容）。
   - 不支持的 GL 绘制走 GL 叠加通道或明确不兼容。
5. **运动矢量**：所有动态内容提交时都带上一帧数据，是 API 的一等公民。
6. **互斥**：检测到 Oculus 或 Embeddium 时不启用；Aureveil 方案保持默认。

---

## 12. 关键阻碍与终止条件

| 阻碍 | 严重度 | 应对 |
|---|---|---|
| 工作量（参考规模约 9 万行） | **最高** | 分阶段，先做 AFL 最在意的 3–4 项；不以 Radiance 的功能面为目标 |
| 不能复用 GPLv3 实现 | 高 | 只用公开的标准技术与宽松许可的库，并留下参考与决策记录 |
| Embeddium / Oculus 互斥，导致 AFL 的风和地形动态光需在新渲染器中重做 | 高 | 并入 P8 的设计 |
| 第三方 GL 绘制不可见 | 中 | GL 叠加通道或不兼容列表 |
| CPU 顶点吞吐（不是 DLSS 能解决的） | 高 | GPU 蒙皮、实例化、AFL 直接提交 |
| NVIDIA SDK 许可与商标 | 中 | 发布前合规审查；DLL 动态加载 |
| 早期加载窗口和 GPU 选择（Forge 特有） | 中 | 参照公开 FAQ 的做法：device UUID 比对、强制独显 |

**终止条件**：见 §9。另有：若 S0–P3 合计投入超过预算的 50% 仍未完成 P3，应停止并改为"Aureveil 维持 + 关注上游"。

---

## 13. 最终建议

**B. 部分技术值得做，但完整目标暂不合理。**

依据：

1. **可行性已被证明**：Radiance（Forge 1.20.1）与 NVIDIA 的实现都证明了互操作、光追和 DLSS 路径。因此"是否可能"不再是问题，这**不是技术幻想**。
2. **工作量巨大，且专有约束排除了捷径**：最接近的现成实现是 GPLv3，不能复用。参考规模约 9 万行且仍在 alpha。以"替代 Aureveil + 全面 AFL 专项"为完整目标，对小团队是多年量级（经验估计）。
3. **值得做的部分是确定且有独立价值的**：
   - S0（引擎外 Vulkan RT + DLSS SR）；
   - P0–P1（Forge 内互操作骨架）；
   - `afl-renderer-api` 设计（让 AFL 内容以数据形式提交，同时对未来任何渲染器有益，包括可能出现的 Radiance 公开 API）。
4. **建议的第一个可交付形态**："Vulkan 光栅 PBR + DLSS SR + AFL 直接提交（水、载具、人物）"，光追作为后续可选开关。不建议以全路径追踪为起点。
5. **继续推进完整目标的门槛**：S0、P0、P1 全部通过，且 P3 帧时间达标，再决定是否升级为 A（进入完整 PoC 开发）。
