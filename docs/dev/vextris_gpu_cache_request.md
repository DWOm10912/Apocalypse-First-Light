# Vextris 需求：网格显卡缓存（2026-10-16）

状态：**AFL Main 已接入（分支 `feature/t15-smooth-normals`，2026-10-16，用户 2026-10-16 实机 PASS）；C 已接入（分支 `feature/federal-tunnels`，C 自测通过，等用户实机验收）**（用户 2026-10-16 批准由 Vextris 做，AFL 侧不做替代方案）。

发版信息（Vextris A，2026-10-16）：

- 版本：`io.github.dwom10912:vextris:0.1.1-alpha.4`，JAR SHA-256 `bd8db1b85a3019ef31db254c4d6eafbdc629ef21dc664d15bc81387f07f59704`；mods.toml 依赖版本范围 `[0.1.1-alpha.4,0.2.0-alpha)`。
- 接入说明：`Vextris/docs/release/0.1.1-alpha.4/AFL_INTEGRATION.zh-CN.md`。验证记录：同目录 `VERIFICATION.zh-CN.md`。
- T15 帧时间（Vextris 基准，非 AFL 实机，每帧毫秒）：
  - Sundial Lite：41.75 → 3.71；不放车时 3.44。
  - Aureveil：57.90 → 2.26。
  - 原版：9.17 → 1.18。
- 外观：原版和光影关闭时与 alpha.3 逐像素一致；光影下只差光影包自身的逐帧噪声。
- 隧道（C，Vextris 基准，145 个区块，每帧毫秒）：原版 10.91 → 1.41，Sundial 26.0 → 3.69。
- 接入要点：
  - 每个根骨骼调用 `MeshGpuCache.drawBone`，selector 必须是常量。
  - 一辆车的绘制包在一个 `GpuGeometry.Batch` 里。
  - **玻璃要在 Batch 关闭之后再提交**，否则会挡住驾驶室内饰。
- 接入（AFL Main，2026-10-16）：`PickupTruckRenderer` 的第 1、2 遍（镂空和磨损）改走 `MeshGpuCache.drawBone`，每个根骨骼用 4 个常量 selector（主漆、副漆、不着色、磨损），整辆车包在一个 `GpuGeometry.Batch` 里，玻璃在 Batch 关闭后提交。`drawBone` 返回 false 时按原来的 CPU 路径画。用户实机验收按 Vextris 的 `VERIFICATION.zh-CN.md` 第 7 节。

2026-10-16 Vextris A 发来接口草案（还可能小改）：
- 底层 `GpuGeometry`：给路面、桥、隧道内衬用，按区块一份。`build(renderType, options, writer)` 建，`setLights(int[])` 只更新光照，`draw(...)` 画；过期时返回 false，由调用方重建；`close()` 释放。
- 上层 `MeshGpuCache`：给 T15、TunnelParts 这类 Vextris 网格用，每个实例一个矩阵。`drawBone` / `drawModel` 画，缓存键是（模型、骨骼、层、selector）。贴图、光照、颜色每次绘制时给，所以磨损层可以复用同一份显存。selector 必须是常量。
- 两者都在 `GpuGeometry.Batch` 里画。光影开关、换包、资源重载时自动失效。半透明层仍走 CPU 路径，AFL 的玻璃继续走 `AflMovingGlass`。

AFL Main 的回复：
- 要求缓存保留平面 QUADS 和平滑 TRIANGLES 的拓扑拆分（Oculus 会按面覆盖 QUADS 的法线）。
- 建议把"30 秒没画就释放"改成 LRU，或做成可调。
- 要求 Batch 结束时恢复渲染状态。

Vextris A 已处理（2026-10-16，未发版）：
- 拓扑：烘焙时按 `MeshRendering.smoothTopology()` 自动拆分。开光影时，平面部件存一份 QUADS，平滑部件存一份 TRIANGLES；不开光影时只有一份 QUADS。调用方不用传 Topology，光影开关时缓存整体重建。
- 释放：默认空闲 5 分钟才释放，可用 `MeshGpuCache.setIdleRelease(ms)` 调整。另有显存预算 LRU，用 `setMemoryBudget(bytes)` 设置，默认 256 MiB。
- 状态：每组画完按该 RenderType 恢复状态，和 `MultiBufferSource.endBatch` 一致。
- 原版基准（Vextris 自测，100 个动画炮塔）：CPU 路径 10.28 ms/帧，缓存加 Batch 5.16 ms/帧。这个场景受 draw call 数量限制；T15 受顶点数限制，预计收益更大，Vextris 会用 pickup_t15 实测。

## 为什么要做

T15 皮卡开光影时，只要车在视野里就掉到约 15 FPS（用户实机）。AFL 侧的距离裁剪和阴影遍裁剪（见 [皮卡设计](../vehicles/first_pickup_v0_design.md)）用户实测没有改善。

JFR 实测（用户客户端，Sundial Lite + Oculus + Embeddium，Vextris 0.1.1-alpha.3，站在车旁 20 秒）：

- 渲染线程 1367 个样本，87% 在 `PickupTruckRenderer` → `MeshRenderer.renderParts` → `emit` → `BufferBuilder.vertex` / `endVertex`。
- 自身耗时：`endVertex` 里的 `ByteBuffer.put` 30%，`BufferBuilder.nextElement` 19%（主要经 Oculus 的 `iris$beforeNext`），Oculus `NormalHelper.computeTangentSmooth` 14%，`endVertex` 11%，`MeshRenderer.renderParts` 4%，`fillExtendedData` 3%。
- 阴影遍（`ShadowRenderer.renderEntities`）占 33%，车轮（`drawWheels`）占 30%。GC 正常，20 秒内约 2 次年轻代回收。
- 每帧顶点数（光影路径，平滑部件按 TRIANGLES 提交，不含玻璃）：

  | 网格 | 顶点数 | 每帧画几次 |
  |---|---|---|
  | 外壳 | 3.96 万 | 2（主遍 + 阴影遍） |
  | 车头 | 3.47 万 | 2 |
  | 车轮 | 1.36 万 | 主遍 5 + 阴影遍 4 |
  | 驾驶室 | 3.82 万 | 1，20 格内 |
  | 座椅 | 2.14 万 | 1，20 格内 |
  | 底盘 | 3.73 万 | 1，16 格内 |
  | 发动机 | 3.83 万 | 1，16 格内，引擎盖开着时 |

  近处每帧合计约 40 万顶点，约 145 ns/顶点。

结论：瓶颈是每帧由 CPU 重新逐顶点提交。减面只能按比例缓解，所以需要把网格缓存在显卡上。

## 需要的能力（具体 API 由 Vextris 决定）

1. 把 `MeshModel` 的部件按（根骨骼、`MeshLayer`、`Topology`、贴图）烘焙成 GPU 顶点缓冲，之后每帧只用 `PoseStack` 矩阵画。AFL 的车门、引擎盖、尾门是不同的根骨骼，各用自己的矩阵（pushPose → 移到 pivot → 旋转 → 画）。
2. `PartFilter` 仍要可用。同一网格的 paint 部件会换一张贴图再画一遍（磨损层 `WEAR[level]`），所以缓存要按（filter、贴图）区分，或者由调用方给一个稳定的缓存键。
3. 光照、overlay 和颜色：整辆车用同一个 light 值。light 变化时重建、按 light 值缓存几份、或改用 uniform 覆盖都可以。
4. Oculus：开光影时 gbuffers_entities 和阴影遍都要能画，切线、中点纹理等扩展数据在烘焙时算好。光影包切换、资源重载、Oculus 管线重建时自动失效并重建。不开光影时用原版实体 cutout 着色器，外观和现在一致。
5. 玻璃不需要：开光影时 AFL 的玻璃走自己的 `AflMovingGlass` 队列，按地形玻璃画。不开光影时实体半透明玻璃量很小，可以继续逐顶点提交。
6. 提供释放接口（实体移除、维度切换时由 AFL 调用），或者由 Vextris 自己管理生命周期。

## 验收

- 同一场景（T15 在视野里，开 Sundial）的 FPS 接近没有车时的水平，渲染线程里车的 CPU 占比降到个位数百分比。
- 开光影和不开光影的外观都和 alpha.3 一致（平滑法线、area 加权不变）。
- 发版沿用 internal-repo 坐标 `io.github.dwom10912:vextris`，给出版本号和 SHA-256。AFL 在分支 `feature/t15-smooth-normals` 上接入，并更新 [Vextris 0.1.1 接入](vextris_0_1_1_integration.md)。

## 补充：通用顶点缓存（路面、桥、隧道；用户 2026-10-16 批准）

用户实机：不开光影，一进 C 的隧道（Tunnel V1，分支 `feature/federal-tunnels`）就开始卡。这次还没有采样，但从代码看是同一类问题：`client/road/RoadClient.render`（AFTER_SOLID_BLOCKS）每帧把视野内每个区块的路面、桥、隧道内衬和设备逐顶点重新提交（`out.vertex(...).endVertex()`）。一条隧道有几万个顶点，进去以后四周全在视野里。车的 JFR 里也有 `RoadClient.render` 的逐顶点样本。

需要的能力：

1. 调用方把自己的顶点数组（按区块，位置相对区块原点，带 uv、法线、颜色）一次上传成 GPU 缓冲，每帧只用一个平移矩阵画，不经过 MeshModel。
2. 逐顶点光照：现在 RoadClient 每顶点存一个 light 值，近处每 4 刻、远处每 40 刻左右重新取一次。缓存要能只更新光照、不重传几何，比如光照放单独的缓冲或属性。或者给出别的办法，让光照变化不用整块重传。
3. 区块卸载、资源重载、光影切换时可以释放或重建。
4. 开光影时：路面现在不进阴影遍（`activeShadowPass()` 跳过），以后隧道可能要进，所以接口上要允许调用方选择进不进阴影遍。
5. 用的 RenderType 是 C 自己的 `RoadRenderTypes.ASPHALT`（实心）和 `MARKING`（标线）。缓存要能配合调用方给定的 RenderType 使用。

C 不自己另做一套，等这个接口出来后再接入。验收：不开光影时进隧道，FPS 接近隧道外的水平。

C 的 JFR 实测（2026-10-16）：
- 测法：C 自己的客户端，不开光影，种子 4 的 316 m 双孔隧道内，采样 20 秒。
- 结果：渲染线程 890 个样本，`RoadClient.render` 占 68.1%（含子调用），几乎全是逐顶点调用：`BufferBuilder.nextElement` 39%、`normal` 12%、`uv` 6%、`color` 5%。`RoadClient.lights` 只占 2.6%，`dug` / `recheck` 各 0.2%。
- 规模：隧道每个区块约 500 个三角形，这条隧道 145 个区块共 72,800 个三角形，另有 TunnelParts 442 个部件。
- 结论：瓶颈确认是逐顶点提交。

C 对接口的补充：
- 每个区块一份（路面、桥、隧道衬砌）。
- 挖掉路面格时，整个区块失效重建。
- TunnelParts 的部件本身已是 Vextris 网格，走 Vextris 的实例提交即可。

有问题回给 AFL Main 会话。
