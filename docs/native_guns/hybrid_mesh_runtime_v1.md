# AFL Hybrid Mesh Runtime — V1 / V2

2026-09-29 新增 [AFL Animated Block Mesh Runtime V1](../rendering/animated_block_mesh_runtime_v1.md)，现按用户确认标记 **COMPLETE / FROZEN**。方块复用同一 loader/cache/part 数据和 CPU 提交；`AflMeshRenderer.renderPartsAtCurrentPose` 提供无 GeoBone 的低层入口，原枪械/物品 wrapper 保留。`AflMeshCache` 在同一 reload 增加载入 `block_mesh_profiles/*.json`。新方块的层级/pivot/简单开合由 native profile 管理；下文 GeckoLib skeleton/动画描述继续适用于原枪械与物品路径。开发 Demo 已删除，通用 Runtime 保留；没有迁移现有生产家具，也没有修改枪械动画、正式资产或材质绑定。本次清理未新增代理实机验证记录。

2026-09-27 新增 [Transparent Hybrid Mesh Runtime V1](transparent_hybrid_mesh_runtime_v1.md)：V1/V2 part 可选 `render_layer`，缺省 CUTOUT，透明层使用标准 `entityNoOutline`。Mixed attachment/static presentation 先提交 cutout，再提交透明 parts；shadow pass 跳过透明层。首个正式资产为 pistol_red_dot，保持源几何/UV/贴图 alpha 24 不变。Shader OFF 路径就绪；Oculus 默认透明 shader 的 0.1 alpha test 仍可能裁掉镜片，透明 PBR 尚未实机通过。原低层单 VertexConsumer API 仍默认 CUTOUT，不自动分配第二个 buffer；新 mixed 调用者应使用高层双层入口。见专文的排序与 GPU 验证限制。

2026-10-05 起 loader 在加载时把能安全合并的三角形对合成 Quad，第一人称手持枪跳过封闭 part 的背面，减少 CPU 提交的顶点。sidecar 格式和导出器不变。已编译，未实测 FPS，详见下文 [加载时三角面配对与第一人称背面跳过](#加载时三角面配对与第一人称背面跳过2026-10-05)。

2026-09-27。V2.1 工具链已实现保守的量化感知 Quad 恢复，存储格式仍为 V2。用户反馈此前 V2 实机通过；V2.1 的离线检查通过，新一轮图形/PBR/CPU 验收仍待用户执行。基线：Minecraft 1.20.1、Forge 47.4.22、Java 17、GeckoLib 4.7.4。

## 范围与 opt-in

GeckoLib 继续持有 skeleton、动画、骨骼姿态和 Cube 绘制。AFL 的独立 sidecar 只提供绑定到一个 bone 的刚性 Mesh。没有 skinning、morph、动态拓扑、运行时 subdivision、多材质、透明排序、LOD、VBO、自定义 shader 或 GeckoLib fork。

资源约定（命名空间和子目录均保留）：

| 资源 | 路径 |
|---|---|
| 既有 Gecko geometry | `assets/<namespace>/geo/<id>.geo.json` |
| 可选 Mesh sidecar | `assets/<namespace>/meshes/<id>.aflmesh.json` |
| atlas | 沿用枪械 renderer 当前 texture；sidecar 不另选贴图 |

`NO_SIDECAR => OLD_RENDER_PATH_UNCHANGED`。BR51（2026-09-28）和 HR55（2026-09-29）后来都改为 V2 sidecar，见 `br51_01_v2_pure_mesh_pbr.md`、`hr55_v2_pure_mesh_pbr.md`。枪口 / 瞄具 / 弹匣附件共用的 `NativeMuzzleRendering.drawItem` 也按此 opt-in：附件 Geo 在 `AflMeshCache` 中有 sidecar 时走 `AflHybridMeshRendering.renderAtCurrentPose`，否则走原 Cube 路径（2026-09-27 起首个 Mesh 附件为 `pistol_suppressor_01.aflmesh.json`，格式 V2）。正式 `p9_01` 使用 `p9_01_v2_native.aflmesh.json`，与 `blackridge_50.aflmesh.json` 同为首批 **sidecar 格式 V2**。`silverwood_12.aflmesh.json` 与弹药 sidecar 保留格式 V1；此前 Silverwood 资产名中的“Hybrid Mesh V2”是模型迭代编号，不是本节的存储格式版本。Registry ID、动画和玩法未因本轮迁移改变。

## Sidecar V1

```json
{
  "format_version": 1,
  "coordinate_space": "bone_pivot_local_blocks",
  "uv_origin": "top_left",
  "winding": "ccw",
  "texture_size": [16, 16],
  "parts": [{
    "name": "example_facet",
    "bone": "receiver",
    "vertices": [[0,0,0,0,0], [0.25,0,0,1,0], [0,0.25,0,0,1]],
    "triangles": [[0,1,2]]
  }]
}
```

每个 vertex 为 `[x,y,z,u,v]`，每个 triangle 为三个零起始索引。一个 part 对应一个 Mesh element、一个 bone；同一 bone 可有多个 part。Runtime 绑定稳定 bone name，不使用 Blockbench UUID。UV seams 允许重复位置、不同 UV；flat normal 按三角形叉乘生成，不接受自定义 smooth normals。

Loader 校验版本、固定坐标/UV/winding 标记、字段集合、唯一 part 名、目标 bone 存在、atlas 与 geometry 描述一致、有限数、数组长度、索引范围、非零面积。顶点在转成 float 后再验证退化。限制：每份 JSON 最多 4 MiB 字符；1–128 parts；全模型最多 16,384 triangle-equivalents（Quad 计 2）、65,536 vertex slots；局部位置绝对值不超过 256 格；UV 在 `[0,1]`；atlas 各边 1–4096。未知/多余字段拒绝。错误包含资源路径，part 内错误另含 bone/part（名称无法解码时给 part 索引）。

## Sidecar V2 与回退

`format_version: 2` 保持 V1 的 root 元数据、`vertices: [x,y,z,u,v]` 和 bone contract，只把 part 的 `triangles` 字段替换为 `faces`。每个 face 是 3 或 4 个索引，例如 `"faces": [[3,0,1,2], [4,5,6]]`。面顺序、逐 corner UV 和边界 winding 均保留，不合并 UV seam 或硬边。Runtime 用 primitive arrays 保存 corners 与 face offsets，加载时缓存 face count、triangle-equivalent、part count、版本和 bounds；V2 Quad 不再展开为两组三角形 corners。

- 显式 `format_version: 1` 或**完全缺少版本字段**：只读取 V1 `triangles`；不能用缺少版本的文件承载 V2 `faces`。
- 显式 `format_version: 2`：读取 `faces`，长度必须为 3/4；Quad 在 float 精度下必须为非退化、凸、平面面。Loader 对每个连续三点的单位法线差容差为 `2e-5`。
- 其它版本、混用 `faces`/`triangles`、非整数索引和无效拓扑直接拒绝该 sidecar，沿用原资源错误隔离策略。
- 通用导出器默认仍为 V1，使用 `--format v2` 明确选择 V2；P9 专用 `tools/export-p9-01-v2-native.mjs` 默认导出 V2，避免后续正常导出覆盖本轮迁移。其它资产不批量迁移。
- 单资产回退：对相同 source/geo 使用通用导出器 `--format v1`，覆盖该资产的 sidecar 即可；Runtime 保留 V1 支持，无需回滚整个 Runtime。

V2.0 历史策略在坐标/UV 舍入及 float 转换后，以单位法线和单位 tangent/bitangent 向量差 `1e-5` 判断 Quad。V2.1 改为下节的高精度分类和误差传播验证。四角仍循环旋转到原 ear-clipping 第一组三角形，使 Minecraft 的 `ABC + CDA` 与 V1 **使用同一条对角线**。凹面、扭曲面和不安全 UV 保留原三角化结果；非法自交面继续报错。

### V2.1 Quantization-Aware Safe Quad Recovery

状态 **PARTIAL（安全恢复与兼容性通过，收益低于最初估算，实机待验收）**。这不是新的存储格式；`format_version: 2`、VertexFormat、RenderType、Triangle ABCC、renderer、Oculus、shadow skip 和 NativeGunRenderProfile 均未改。只重导正式 P9 与 Blackridge sidecar；其它 V1/V2 弹药和静态附件不迁移。

审计链：bbmodel polygon → deterministic triangulation → element rotation / pivot bake → block units / normalized UV → 原先 `clean(toFixed(10))` → `Math.fround` → Quad 判定 → sidecar → Java float bake / Quad revalidation → cached parts → 原 renderer。源数据实际已包含最多 4–5 位小数，不能声称恢复了源文件丢失的作者精度。序列化 positions 和 UV 均为 10 位小数；Java 又转 float。旧 exporter 角度向量差阈值 `1e-5`，旧 loader `2e-5`（平方 `4e-10`），后者虽较宽仍没有 float ULP / 面尺度误差预算。误判不是全部来自 exporter 的 10 位舍入，float 转换、源文件已有扭曲及 UV 非仿射也各自存在。

新 `classifyQuad` 在 decimal/float bake 前读取当前可用 double 数据，exporter 是 topology authority。已直接满足旧 float 检查的面，还须通过高精度平面/凸性/完整 UV 导数检查。新增恢复要求源法线向量差 ≤`1e-10` 且点到平面距离 ≤`max(1e-14, extent*1e-12)`；这是数值平面证据，不根据 4–5 位源坐标猜测理想平面。源中已存在的微小真实 warp 不进入恢复分支。

量化预算逐面使用 **实际 decimal + float 转换误差** `e`：每条边误差 ≤`2√3 e`，cross-product 误差 ≤`edgeError*(|edge1|+|edge2|)+edgeError²`，单位法线误差 ≤`2*crossError/area`。同时检查点到原平面距离。切线使用 UV numerator 的位置/UV 扰动界，比较两条历史三角形各自的 tangent/bitangent 与源结果及 handedness。单位方向误差另设 `1e-3` 硬上限，**上限不能单独放行，必须满足逐面解析误差界**；病态 UV/退化面回退。不是把全局 epsilon 改成 0.06°。

完整 UV 导数还比较大小（相对误差 `1e-5`），弥补旧版本只比单位方向的漏洞。不同 handedness、seam、非仿射 distortion 拒绝；整面连续镜像且 handedness 一致可以保留，不把合法镜像误判为 seam。绝不跨源 face 合并；硬边保留，当前 flat-only schema 对 smooth/per-corner normals 显式拒绝，不丢弃它们后继续导出。

Java loader 保留 schema/finite/index/count/degenerate 校验。Quad corruption check 用每坐标 `0.5 float ULP + 5e-11`、边长和面积推导 normal budget，另检查凸性、正 winding、距离。兼容旧 V2 的 `2e-5` 基线并有 `0.00202` 双法线总偏差硬上限；不会重新用固定作者阈值把合理量化的 Quad 拒绝。loader 不重做 UV 作者决策。新旧生产 sidecar 已通过实际 Java loader。

#### 当前整资产 benchmark（含隐藏 helper，非实机 Main FP）

| 指标 | P9 before → V2.1 | Blackridge before → V2.1 |
|---|---:|---:|
| Triangle-equivalent | 8248 → 8248 | 4992 → 4992 |
| Quad | 1225 → 1306 | 878 → 927 |
| Triangle | 5798 → 5636 | 3236 → 3138 |
| Submitted vertices estimate | 28092 → 27768 | 16456 → 16260 |
| 净减少 | 1.15335% | 1.19105% |
| 新恢复 Quad | 120 | 92 |
| 旧 Quad 因完整 UV 安全检查退回 Triangle | 39 | 43 |
| Sidecar bytes | 1378408 → 1376915 | 800068 → 799091 |

默认可见主枪（排除 `reload_magazine` / `empty_old_mag`）：P9 Quad `987→1024`、Triangle `4890→4816`、triangle-equivalent `6864` 不变、vertices `23508→23360`（0.62957%）；Blackridge Quad `796→845`、Triangle `2908→2810`、triangle-equivalent `4500` 不变、vertices `14816→14620`（1.32289%）。P9 已含此前新增螺纹枪管；下方 V2.0 的 6384/21584 是历史资产值，不能与当前值混算性能收益。

`--diagnostics` 可选输出，默认不增加诊断噪声：

| reason | P9 | Blackridge |
|---|---:|---:|
| QUAD_PRESERVED_DIRECT | 1186 | 835 |
| QUAD_RECOVERED_QUANTIZATION | 120 | 92 |
| TRIANGULATED_NON_PLANAR | 1296 | 475 |
| TRIANGULATED_UV_UNSAFE | 927 | 559 |
| SOURCE_TRIANGLE | 1190 | 1070 |
| CONCAVE_OR_SELF_INTERSECT / DEGENERATE / TANGENT_UNSAFE / NORMAL_UNSAFE / NGON | 0 | 0 |

计数采用首个失败条件，不是互相独立的缺陷统计。triangulate 在非法自交输入处直接报错，不输出 sidecar。当前源 face 已经有的舍入误差、非仿射 UV 和真实 warp 没有为提高比例而放宽；因此不宣称回收约 1378 面或获得原估算降幅。

验证命令：`node tools/verify-afl-mesh-v21.mjs`（只读，HEAD 作 before 基线，提交后重跑时 before 会自然改变）；`node tools/verify-afl-mesh.mjs --java-renderer`（使用项目 `.gradle-user` 的缓存，不启动客户端）。前者逐 part 比较全部原始 position/UV，逐面展开比对 V1 历史 diagonal/winding，因 vertices 完全不变，bounds 也不变；新恢复面的两个历史 float normal 最大差：P9 `7.67224e-6`、Blackridge `4.70010e-7`，单位 tangent/bitangent 最大差 `2.71487e-4` / `3.51001e-5`，handedness 一致。包括 A–J 对抗案例；实际 Java loader/renderer 校验 V1/unversioned/V2、全生产 sidecar、普通/镜像/非均匀缩放、normal/UV/light/overlay、hidden/zero scale 和真实 4/8 次提交，均已通过。

没有修改 bbmodel、geo、animation、UV、Base Color、`_s`、`_n`、ammo 或 rig。仅允许最后运行一次 `./gradlew.bat compileJava --offline`，编译结果随交付报告；不运行 build/processResources/runClient/GameTest。离线 normal/tangent 误差检查不是 LabPBR 实测：用户需用 NativeGunRenderProfile 复测 **Sundial + P9、Sundial + Blackridge、Shader OFF + P9**，比较 quad/triangle/triangle-equivalent、vertices_per_frame、cpu_ms_per_frame，以及 normal map/highlight/reflection 与 V2.0 是否一致。未测 CPU/GPU/FPS，不声称显著帧率提升。

### 提交与 Oculus / Embeddium 审计

原 Quad 在 `tools/export-afl-mesh.mjs:convert → triangulate` 被拆成两个三角形。当前 `entityCutoutNoCull` 使用 `DefaultVertexFormat.NEW_ENTITY` 和 `VertexFormat.Mode.QUADS`（已检查本地 Forge 47.4.22 类），每组三/四边面都必须提交 4 vertices：V2 Quad 为 `A,B,C,D`；真实 Triangle 和所有 V1 triangle 为 `A,B,C,C`。镜像时分别为 `A,D,C,B`、`A,C,B,B`，保持对角线、正面绕序和向外法线。

已检查本地 Oculus `6020952` 的 `MixinBufferBuilder`、`NormalHelper` 及 Oculus/Embeddium `MixinSodiumBufferBuilder`：QUADS 每四个顶点触发 extended data，生成 mid-UV、normal/tangent；entity 路径使用传入法线和 `computeTangentSmooth`。本轮继续沿用同一个 VertexConsumer、RenderType、light/overlay、贴图及 `_s`/`_n` 绑定，不修改 Embeddium internals。原来的两个退化 Quad 改为一个原生 Quad 后，mid-UV 将描述完整面；这项附加数据自然会变化，不能把离线几何一致性当成所有 Shader/POM 行为的实机证明。切线连续检查保护 normal-map 方向，实际 LabPBR 高光、反射仍由用户验证。

热路径原本没有逐顶点对象分配，pose/normal matrix 也已在循环外获取。本轮将矩阵系数及镜像 inverse-transpose 移到每次调用只计算一次；每 face 只变换一次 flat normal；Triangle 重复的第四角复用第三角的变换结果；parts 使用索引遍历，metrics 按 face 累计。未引入 VBO、GPU cache、OpenGL 或专用 Shader。隐藏骨骼、隐藏子树和零尺度 early skip 沿用原规则。

### V2.0 历史首批静态提交预算（当前值见上节）

以下是导出/顶点捕获验证的**预期每次主枪绘制**，不是新的实机 CPU/FPS 测量。辅助弹匣 `reload_magazine`、`empty_old_mag` 不在默认可见预算内，换弹时 Profiler 会按实际提交统计。

| 模型 | V1 triangle-equivalent | V2 Quad | V2 Triangle | V2 triangle-equivalent | V1 vertices | V2 vertices | 减少 |
|---|---:|---:|---:|---:|---:|---:|---:|
| P9-01 | 6384 | 988 | 4408 | 6384 | 25536 | 21584 | 15.48% |
| Blackridge .50 | 4500 | 796 | 2908 | 4500 | 18000 | 14816 | 17.69% |

含全部隐藏 helper 的 sidecar：P9 为 35 parts、1226 Quad、5316 Triangle、7768 triangle-equivalent；Blackridge 为 16 parts、878 Quad、3236 Triangle、4992 triangle-equivalent。P9 源含 3289 Quad，Blackridge 含 1961 Quad；未被保留的面继续三角化，故本轮不声称 25–50% 或 2× 收益。源 Geometry、UV、Rig、动画、贴图与玩法数据没有改变；只重导出这两个 sidecar 的表示。

资产预算以 **visible triangle-equivalent** 计：手枪目标 4500–6000、soft limit 约 6500、hard limit 约 7000；SMG/Rifle 目标 6000–8000、soft limit 约 9000、hard limit 约 10000。大型特殊武器单独审核。这是作者验收预算，不是新增 runtime 拒绝条件；V2 节省顶点提交不等于可以无限增加面数。

## 坐标、pivot 与 winding 合同

以下合同与已读取的 Blockbench 5.2.1 Free Model 及 GeckoLib 4.7.4 实现一致；不用补偿偏移。

- Blockbench Mesh `vertices` 相对于 **element.origin**；Free Model group 使用绝对 `origin`。每格 16 authoring units。
- Mesh element 的 Euler order 为 `XYZ`：列向量下 `Rmesh = Rx * Ry * Rz`。group 的 order 为 `ZYX`：`Rbone = Rz * Ry * Rx`。
- 本项目现有 Native geo exporter 写入 `geo.pivot = [-BB.x, BB.y, BB.z]`、`geo.rotation = [-BB.rx, -BB.ry, BB.rz]`。Gecko loader 再反转 pivot 的 X 与 rotation 的 X/Y；最终运行轴与 source 一致。因此 **sidecar 不再反转 X**，右手叉乘及 source 的 CCW 正面方向保持。
- 设 source element origin 为 `E`，其 parent group origin 为 `P`，Mesh vertex 为 `v`，则 sidecar 存储 `q = (E + Rmesh * v - P) / 16`。只烘焙 element 的刚性局部变换，保留 bone 动画能力。
- Gecko 在当前 parent pose 后依序乘：`T((-posX,posY,posZ)/16)`、`T(P/16)`、`Rz*Ry*Rx`、`Sbone`、`T(-P/16)`。Mesh hook 在该已准备好的矩阵后追加 `T(P/16)`，然后提交 `q`。每层父 bone 都沿用实际 traversal，不另算第二套动画。
- UV 来自 Blockbench face 的像素坐标，除以 atlas UV 宽高；V=0 在顶部，**不翻 V**。THREE 预览内部的 V 翻转不搬到 Minecraft。
- face 顶点必须按边界顺序给出，从法线外侧看为 CCW。转换器保留 winding；拒绝可检测的自交/重复点/退化/严重非平面面，不猜测乱序 quad。镜像 pose 反转提交顺序，并用实际矩阵的 inverse-transpose 变换法线；零尺度/奇异矩阵不提交。

2026-09-26 **Raw Hybrid Mesh @ current pose（底层能力；此阶段尚无动态弹药业务调用）**：`client/mesh/AflHybridMeshRendering.renderAtCurrentPose(geometry, texture, pose, buffers, light, overlay)` 在调用者已经准备好的 bone/anchor 局部 `PoseStack` 中绘制独立静态 Mesh。它从 `AflMeshCache` 和 GeckoLib 的当前资源缓存读取 sidecar/骨骼，沿既有 `RenderUtils.prepMatrixForBone` 与 `AflMeshRenderer` 路径提交顶点、UV、法线；用独立贴图的 `RenderType.entityCutoutNoCull` 获取 buffer，透传调用者的光照与 overlay，并以 push/pop 隔离 pose。调用者负责目标 anchor 的 pivot/动画姿态；该 helper 不应用 Item JSON display/居中变换、不读取弹量、不识别特定枪械或弹药。现有物品、抛壳、枪械与维护台渲染入口未改；本阶段只编译检查，未做游戏内视觉验收。

## 离线转换器

`tools/export-afl-mesh.mjs` 接受 Free `.bbmodel` 和独立目标 `.geo.json`，只写 sidecar。原始 Cube、骨架、动画、贴图均不被改写。当前仅支持 source group 与目标骨架在 hierarchy/pivot/rotation 上一致的模型；可选 mapping 只重命名 group，不是任意骨架 rebinder。编辑器 `visibility` 是预览状态；`export:false` 才剔除资源，runtime hidden 由 bone 管理。

```powershell
node tools/export-afl-mesh.mjs --input src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel --geometry src/dev/resources/afl_mesh_core/fixture.geo.json --output src/dev/resources/afl_mesh_core/fixture.aflmesh.json --check
node tools/export-afl-mesh.mjs --input src/main/blockbench/p9_01_v2_native.bbmodel --geometry src/main/resources/assets/apocalypse_firstlight/geo/p9_01_v2_native.geo.json --output src/main/resources/assets/apocalypse_firstlight/meshes/p9_01_v2_native.aflmesh.json --format v2 --compact --check
node tools/export-afl-mesh.mjs --input src/main/blockbench/blackridge_50.bbmodel --geometry src/main/resources/assets/apocalypse_firstlight/geo/blackridge_50.geo.json --output src/main/resources/assets/apocalypse_firstlight/meshes/blackridge_50.aflmesh.json --format v2 --compact --check
```

去掉 `--check` 才写出结果；`--compact` 仅把数值数组压到一行，保持数据完全相同，避免超过 4 MiB。`--mapping mapping.json` 的内容为 `{ "source_group_name": "runtime_bone_name" }`。未使用的 mapping、unknown parent、层级/pivot/rotation 不匹配直接报错。每个 face 支持 3–64 点的简单多边形；确定性 ear clipping 支持凹多边形。常规平面容差为 `max(1e-5, extent*1e-5)` source units，轻微扭曲四边面的例外见下文。排序由 bone name、part name、face key 确定。

缺 UV、坏索引、零面积、多个 texture、材质模式/动画 strip、Mesh 自身动画、影响几何的未知属性、weighted skinning、morph、subdivision、动态拓扑等均 fail-fast。source 元素缩放也不在 V1 authoring 合同内；运行时 bone scale 由已有动画姿态处理。动画资源仍由原 Gecko 管线提供，转换器不生成动画。轻微扭曲的导入四边面可在导出时三角化：相对该面的最大轴向尺寸，平面偏差不超过 10%；顶点与可编辑面的边界不改动。超过此限或非平面的五边及以上面仍被拒绝。

2026-09-25 Blackridge .50 已从最新 14 Mesh 标准模型转换为 `src/main/blockbench/blackridge_50.bbmodel`、`assets/apocalypse_firstlight/geo/blackridge_50.geo.json`、`meshes/blackridge_50.aflmesh.json` 和基础 `animations/blackridge_50.animation.json`（仅空 `static_idle` 合同）。14 个刚性 Mesh bone、28 个骨骼、4476 triangles、0 Cube；slide、barrel、trigger、hammer、safety、slide_stop、magazine、magazine_release、extractor_visual、front/rear sight、follower 独立分层，保留 muzzle/ejection/sight/hand/maintenance/positioning/camera anchor。标准源的坐标统一旋转 180°、缩放 0.04、底部移至零点；仅将 2 个严重扭曲的导入四边面拆三角，未移动顶点或 UV。最初转换时枪械原源没有贴图/有效展开，运行时导出资源使用灰阶占位材质；后续 authoring-only 材质进度见下段。**尚未注册 Blackridge 枪械 Item，也没有 Native Gun gameplay definition**：当前 `ConfiguredNativeGunItem` 不能在缺少完整伤害/ADS/recoil 等定义时安全使用，而这些数据不在本轮范围。资源静态可导出不等于第一/第三人称或维护台实机验收。旧 `src/dev/resources/blackridge_50_candidate/` 仍是更早的 11 part/2444 triangle 验证快照，不是当前正式资源。

2026-09-25 **Blackridge Material V1（历史单图版本；已被下文双材质候选替代）**：`src/main/blockbench/blackridge_50.bbmodel` 已保存有效 Pure Mesh UV，引用并内嵌 `src/main/blockbench/textures/blackridge_50.png`（1024×1024 RGBA、Base Color only）。2741 faces / 1337 UV islands，4 px padding；静态展开检查未发现零面积 UV 或面重叠，投影面积比例最小 0.9764、加权 0.9996。按部位分配约 12.15–28.67 px/authoring-unit。主体为克制的中性银灰，controls 为 deep gunmetal，grip 为低对比细纹哑光黑，magazine/follower 独立于 grip 表达，内腔为深灰；无 PBR、品牌文字或额外动画。修正了枪口上方实体面、左侧实体壁面误用内腔深色的问题，未删除或改变这些面。与本轮开始时 V1/V2 工作源逐字段比较，除 UV、texture、resolution 外全部保持一致（含 Geometry、机械结构、Rig、Pivot、Anchor、pose 与动画）；ammo assets 未修改。

当时的 Material V1 检查仅包含 Blockbench 默认 diffuse / 无光照 Base Color 预览和静态文件比较，**不代表游戏内验收或重新机械验证**。当时没有重新生成 `.geo.json` / `.aflmesh.json` / runtime animation，也没有替换 `src/main/resources/assets/apocalypse_firstlight/textures/item/blackridge_50.png`；后文 V2.1 已同步 geo/sidecar 的骨骼与 UV，但游戏用贴图仍未替换或验收。外观肩位的几何优化继续冻结，待用户看过 Material V1 后再判断。
2026-09-25 **Blackridge Dual Material Base Color V1（当前编辑源，待用户视觉验收）**：同一个 `src/main/blockbench/blackridge_50.bbmodel` 保存两张正式候选 `src/main/blockbench/textures/blackridge_50_silver_v1.png` 与 `src/main/blockbench/textures/blackridge_50_blackiron_v1.png`，均为 1024×1024 RGBA、同一 UV、非 PBR；两图都内嵌并带外部路径。旧 `textures/blackridge_50.png` 保留作上版备份，不再是当前候选。仅参考本机 TaCZ 默认包 Deagle / CZ75 / MK23 的 Base Color 材质分区、明度层级和局部边缘语言，没有复制贴图像素、铭文、Logo 或模型。银色候选使用中银灰大面、克制低频明暗、略亮顶面、略暗凹面；黑铁候选使用石墨灰大面和可读的倒角，controls / grip / magazine / cavity 保持分区，现有 serration 槽面统一压暗。

默认显示 silver。切换时，在 Blockbench 编辑模式中全选模型的 14 个 Mesh，右键目标贴图，选择 **应用到元素**（Apply to Elements），然后保存即可；只选中贴图缩略图不会切换面材质。已使用该原生操作验证两图各覆盖全部 2741 faces，UV 完全一致。项目保持 14 Mesh / 28 groups；与本轮开始的正式源相比，除 texture records 和 face texture assignment 外全部字段保持一致。两版各完成侧视、前后 3/4、muzzle、chamber/ejection、grip、隔离 magazine 和无光照 Base Color 编辑器预览；机械、动画、ammo、AFL runtime、Java 均未修改，没有实机验证或 PBR 贴图。当前双图仅为编辑源候选，不能直接当作已支持双材质运行时的证据。
2026-09-25 **当前 Blackridge 源文件状态（覆盖上文“双材质候选为当前编辑源”的历史描述）**：`src/main/blockbench/blackridge_50.bbmodel` 当前枪械贴图为单张 `src/main/blockbench/textures/blackridge_50_blackiron_v2.png`，另内嵌 16×16 的 `afl_arm_reference_source_only.png` 中性参考纹理。沿用 `righthand → righthand_pos → right_hand_anchor` 和 `lefthand → lefthand_pos → left_hand_anchor`，在各 anchor 下新增 Classic、隐藏的 Slim 参考组及四个参考 cube，组与 cube 均 `export=false`；现为 14 个未改动的枪械 Mesh、4 个仅供编辑器预览的 Cube。按 `docs/dev/native-gun/afl_weapon_art_standard_v1.md` 中固定第一人称 presentation，在源文件默认 Display 比例 1 下参考臂宽/长/深分别为 Classic 2.48/9.36/2.48、Slim 1.86/9.36/2.48，手端对齐原有 anchor。未更改 anchor、枪械 Mesh/UV、机械件、枪械贴图像素、弹药或动画。预览臂不含独立手指 Mesh；游戏中真实 Classic/Slim 手臂、袖层和玩家皮肤仍须由 `NativePlayerArmRenderer` 沿 hand anchor 绘制。Blackridge 仍未注册 Native Gun 玩法，本次没有重新导出 runtime 资源、编译或进行游戏内握持/间隙验证；默认双手接触效果尚待动画和实机校准。

2026-09-25 **Blackridge Animation Rig Preparation V2（历史准备阶段）**：在源文件的 `root` 下新增零旋转的 `handling`，pivot `[1.15,4,3.75]` 与右手现有 anchor 一致；`gun_body`、`slide`、`magazine`（含 `follower`）、`righthand` 改挂到 `handling`。`lefthand`、`positioning`、`maintenance_anchor`、`camera` 留在原层级。新增 `mag_out`、`empty_old_mag` 和 `mag_out → reload_magazine` 控制骨骼；当时它们尚无视觉 Mesh，后来由下述 V2.1 补全。Runtime 按骨骼名查找 hand/camera/Mesh；纯 Mesh 转换器校验导出 geo 的父骨骼。`tools/export-afl-mesh.mjs` 允许额外的 `export=false` 作者参考纹理，但拒绝导出几何引用该纹理。

2026-09-26 **Blackridge Animation Rig Preparation V2.1（视觉辅助弹匣已备齐，未接入游戏）**：`src/main/blockbench/blackridge_50.bbmodel` 在 `mag_out → reload_magazine` 下加入 `reload_magazine_visual`，在 `empty_old_mag` 下加入 `empty_old_mag_visual`。两者逐项复用正式 `magazine` Mesh 的 146 vertices、143 faces、UV、Blackiron 贴图引用、origin/rotation 和缩放；未复制 `follower`，正式 `magazine → follower` 保持原状。原有 18 个元素、36 个组、贴图和 UV 均未改动；仅新增这两个动画视觉 Mesh。同步重导 `geo/blackridge_50.geo.json` 的 32 个可导出骨骼及 `meshes/blackridge_50.aflmesh.json` 的 16 个 Mesh part，以反映 V2 handling/辅助骨骼层级。`animations/blackridge_50.animation.json` 的现有空 `static_idle` 基线只增加 `reload_magazine` 与 `empty_old_mag` 的 `[0,0,0]` scale；正式弹匣保持默认可见。今后的换弹动作可分别给相应辅助骨骼 `[1,1,1]` scale，并用 `mag_out` 控制新匣位置；无需新增 Java 可见性机制。Hybrid Mesh renderer 对零尺度 pose 不提交顶点，第三人称和维护台沿用 `reload_magazine` / `empty_old_mag` 名称排除。没有制作正式换弹、待机或其它动作。源/geo/sidecar/基线动画已静态核对，尚无 Blackridge 注册武器或客户端实机验证；**状态：动画辅助弹匣资源准备完成，待 Claude 动画与后续游戏接入验收**。此前“辅助骨骼为空、默认隐藏未可靠实现”的描述已过时。上文“没有制作正式换弹、待机或其它动作”仅描述 V2.1 当时状态，已被下段取代。

2026-09-26 **Blackridge First-Person Animation V1（作者动画已导出，未接入游戏）**：九条动画由可复现脚本 `tools/author-blackridge-50-animations.mjs` 生成（设计节点 → 60 Hz 烘焙线性关键帧；`--emit <file>` 输出 Blockbench 动画数据，`--write-runtime` 写 `animations/blackridge_50.animation.json`）。clip 与时长：`static_idle` 0.25（hold）、`static_bolt_caught` 0.25（loop）、`shoot` 0.36、`draw` 0.9、`put_away` 0.6、`reload_tactical` 2.45、`reload_empty` 2.7（重型手枪节奏，约为普通手枪 1.3 倍；未来 gameplay 换弹时长应对应 49 / 54 tick）、`inspect` 4.8、`inspect_empty` 3.65。动作语言参考 MW2 .50 GS 与 Silverwood：预备反向 → 加速到位 → 过冲回弹，机械事件处 1–2 帧硬抖，`camera` 仅作小角度跟随与冲击尖峰（`shoot` 不写 camera，留给 Native recoil）。
- 握姿：待机时握把位于右手正中，左手从左侧斜贴（用户在 Blockbench 中校准后烘焙进 `lefthand`，不依赖不导出的参考臂组）。每条动画都写入双手轨道，Blockbench 单独预览与游戏 baseline 一致。左手在 `lefthand_pos` 上按 clip 选一个恒定预旋转，避免欧拉奇点；姿态混合用四元数，旋转轨道逐帧展开，240 Hz 实测锚点每步 ≤5.5°。
- 换弹/检视位移：`handling` 进入侧面姿态，同时枪在自身坐标内滑到右手左侧（`righthand` 反向补偿），弹匣井不再被手遮挡；拉滑套检查前滑回手中。旧匣沿弹匣轴离井后按真实世界速度接续飞行（无速度折角）：战术换弹为重力下落；空仓换弹为整臂蓄力后猛甩（顶点处手腕约 98° 翻转，弹匣井朝右），空匣向镜头右侧抛出翻滚出画。新匣由 `mag_out/reload_magazine` 从画面外沿弹匣轴插入，入座帧与正式弹匣位置误差 ≤0.01。滑套/保险同步后定；空仓换弹插匣后，枪保持在右手左侧，左手上手拉滑套（后拉 0.4 脱离挂机）并松开复进，之后才随整体回位滑回右手。拔枪先把枪掏出稳住，再由左手拇指在 0.44 s 关闭保险（音效 0.43 s）后回到握把；收枪先由左手在 0.13 s 打开保险（音效 0.12 s），再放下出画。**已知问题**：当前 `NativeGunActions` 切换物品时只播放 put_away 中 ≤1 tick 的 cue，因此游戏内收枪的保险音效会被跳过，待后续 Java 调整。检视为整臂蓄力后向左上猛甩（右手随枪运动，不固定），弹匣离井后沿连续抛物线向左上飞起，左手约 1.15 s 空中接住、检查再插回；有弹检视最后做拉滑套验膛。
- 声音 marker（`sound_effects`）：`blackridge_50_safety_lever`、`magazine_release`、`magazine_out`、`magazine_flick`、`magazine_in`、`slide_back`、`slide_release`（`slide_release` 提前 0.135 s 以对齐文件内预点击）。9 个 OGG（另含 `blackridge_50_fire`、`blackridge_50_suppressed`）已放入 `assets/apocalypse_firstlight/sounds/weapons/blackridge_50/`，在 `sounds.json` 登记并由 `AflSounds.BLACKRIDGE_50` 注册为同名 SoundEvent（48 kHz 立体声，与现有枪械音效一致）；`compileJava/processResources` 通过。开火/消音开火由服务端射击路径播放，不在运行时 marker 中；Blockbench 源的音效关键帧指向仓库 OGG 供预览试听，其中 `shoot` 额外带一个仅预览用的开火关键帧，不写入运行时 JSON。未做游戏内听感验证。
- 比例：参考臂按建议的第一人称 Display 缩放 0.37 重设为 Classic 6.70×25.30×6.70（Slim 宽 5.03，均 `export=false`），枪长/臂宽约为 P9 标准的 1.3 倍。后续游戏接入已建立 `models/item/blackridge_50_in_hand.json`，首版 Display 为 0.45，画面效果待实机确认。
- 状态：九条动画与参考臂已保存于 `src/main/blockbench/blackridge_50.bbmodel`；runtime JSON 已写出并静态核对（骨骼均存在于 geo、时间有序）。Blackridge 后续已按下段注册；通用 `ConfiguredNativeGunItem.inspectClip(ItemStack)` 现按服务端弹匣弹数与 clip 可用性选择 `inspect` / `inspect_empty`；用户已报告该分支实机测试通过。

2026-09-26 **Blackridge .50 Native Gun 游戏接入 V1（资源静态验收完成；待实机验收）**：`AflItems.BLACKRIDGE_50` 注册 `apocalypse_firstlight:blackridge_50` 为标准 `ConfiguredNativeGunItem`，进入武器与弹药创造标签。`data/apocalypse_firstlight/native_guns/blackridge_50.json` 使用唯一弹药 `50_ae_round`、容量 7、SEMI/5 tick、基础伤害 15、爆头 1.5 倍、28/56/72 格衰减区间、末端 0.60 倍、1° 基础及 ADS 散布、88 格噪声与耳鸣；换弹时长采用正式动画 2.45/2.70 秒，插匣结算为第 25/23 tick。ADS 为 0.18 秒、FOV 0.94，机瞄瞄点 `[0,9.6,4.1]` 仍待实机校准；后坐值见数据 JSON。`models/item/blackridge_50_in_hand.json` 为已有 Native Gun `builtin/entity` 渲染入口；`models/item/blackridge_50.json` 按现有 `forge:separate_transforms` 预留 `textures/item/blackridge_50_inventory.png`，正式背包图标现已位于该路径。HUD 路径为 `textures/gui/gun/blackridge_50_hud.png`，已由用户提供正式图标。正式 `textures/item/blackridge_50.png` 复用 Blockbench 的 Blackiron v2 PNG；geo/animation/aflmesh 由现有 Hybrid Mesh 管线读取。九条 clip 保持源名，缺独立 `dry_fire` 动画，干击沿用通用音效；`inspect_empty` 现由通用 Item 在弹匣弹数为零且资源存在时选择，用户已报告实机验证通过。动态 top round 的后续状态见下文；`NativeGunFx` 现对 .50 AE 弹壳使用 Mesh 缓存、GeckoLib 骨骼和正式 atlas 绘制，其他口径沿用 baked quads；抛壳视觉仍待实机验收。客户端第一/第三人称、ADS、GUI、音效和掉落尚未实机验收。

## Runtime 与薄适配

源码位于 `src/main/java/com/antaurora/apofirstlight/client/mesh/`：

- `AflMeshLoader`：纯解析/验证，一次性展开 V1 triangles / V2 faces 的 corners，烘焙 flat normals 和 local AABB；Quad 仅存四个 corners。2026-10-05 起还会把能安全合并的三角形对合成 Quad，并标记封闭 part（见下文）。
- `AflMeshModel` / `AflMeshPart`：不可变 CPU 数据，不保存 live GeoBone 或 instance pose。
- `AflMeshCache`：client reload listener 的 prepare 阶段扫描 sidecar，并从同一 ResourceManager 读取对应 geometry 验证；apply 原子替换不可变 snapshot，递增 generation。无需依赖 Gecko cache 的 apply 顺序。坏 sidecar 单独记录错误并省略，其他资源继续；移除或损坏的 sidecar 不保留上代 Mesh。无 GPU 资源生命周期。
- `AflMeshRenderer`：每帧只根据当前 pose 提交已烘焙 corners；不解析 JSON、不三角化、不生成逐 face 对象。使用原 texture、VertexConsumer、RenderType、color、packedLight、overlay；entity buffer 为 QUADS，native Quad 提交 `A,B,C,D`，Triangle 提交 `A,B,C,C`。未直接操作 OpenGL。2026-10-05 起调用者可打开背面跳过（`cullBackFaces`），目前只有第一人称手持枪打开。

`NativeGunContextRenderer.renderCubesOfBone` 先沿用 Cube 绘制，再追加 Mesh；本地玩家第一人称相机的 Shader shadow pass 例外，详见下节。当前枪械由 `NativeAnimatedWeaponRenderer` 继承该薄适配；其递归入口的临时弹匣替换、subtree 省略、shell 显隐仍控制是否进入 hook。P9 的旧 `P901Renderer` 已在通用 Runtime 迁移时退役。Mesh 检查 own hidden；hidden child 遵循 Gecko 的原遍历。hook 也在 `reRender` 执行，避免使用会在 reRender 跳过的 layer callback。第三人称传入的是该路径真实的 frozen/static-idle bone 副本，未改变第三人称动画语义。没有 sidecar 时不提交 Mesh。

### Render Pass Probe 与本地第一人称阴影跳过（2026-09-27）

开发客户端可用 JVM 属性 `-Dafl.debug.renderProfile=true` 开启 `NativeGunRenderProfile`，默认和正式发布环境关闭。Probe 只统计正式 `p9_01` 与 `blackridge_50` 的主枪 `AflMeshRenderer` 调用；独立弹药、弹壳 Mesh 不计入。Forge `RenderTickEvent.START/END` 定义一帧，连续 120 帧后以 `[AFL-RENDER-PROFILE]` 仅输出一次采样结果。换枪、Shader 状态、F5 相机类型或资源 generation 变化后重新采样。保留 total / pass、calls、parts、CPU 指标；新增 `format_version`（0=无提交调用，-1=混合版本）、`quad_faces_per_frame`、`triangle_faces_per_frame`、`triangle_equivalent_per_frame`。旧 `triangles_per_frame` 现在明确为 triangle-equivalent 的别名，Quad 计 2、Triangle 计 1；`vertices_per_frame` 是实际提交次数，不推算。CPU 是顶点提交的 CPU 耗时，不是 GPU 时间。隐藏/零尺度/无效法线的跳过数量同样用 triangle-equivalent；Gecko 在进入 hook 前隐藏的子树无法计数，日志标注 `hidden_skip_scope=invoked_bones_only`。

Shader 启用与 shadow pass 由共享 `AflShaderCompat` 通过 Oculus/Iris 公开 API 软依赖反射获取；可选的内部 phase/name 反射只供 Probe 标注 hand phase 和包名。查询失败时不授权阴影跳过。原始实机样本中，Shader OFF 的 Blackridge/P9 主枪分别为 4500/6384 triangles/frame；Sundial Lite ON 时分别为 9000/12768，其中 main 与 shadow 各提交完整的 4500/6384 triangles。shadow 调用的 `ItemDisplayContext` 是第三人称手持，而当时客户端相机仍处于第一人称，因此不能仅凭 display context 识别本地玩家。

`NativeGunShadowSkip` 在 Forge `RenderPlayerEvent.Pre/Post` 期间记录当前被渲染玩家；`NativeGunContextRenderer` 仅在当前物品为 `ConfiguredNativeGunItem`、第三人称手持路径、owner 是本地玩家、相机处于第一人称、Shader 与 shadow pass 均明确启用时，跳过该次 Pure Mesh 的 `AflMeshRenderer` 入口。它不更改 Cube、手臂、动态弹药、RenderType 或资源；F5 下的本地玩家、其他玩家及世界/GUI 物品不满足该条件。Render tick 边界清空 owner 上下文，以免取消的玩家渲染将上下文带到下一帧。用户在本轮任务中反馈 P0（含阴影跳过及 P9 几何优化）后 Sundial Lite 双枪已约 120 FPS；该反馈不等于本轮 V2 实测。V2 未修改 shadow policy，`NativeGunRenderProfile` 保留用于复验，第一人称 shadow triangle-equivalent 仍应为 0。

正式 `apocalypse_firstlight:12_gauge_round` 是首个普通 Item 的真实资产 opt-in；`.50 AE` 的 `50_ae_round` / `50_ae_casing` 和 9mm 的 `9x19mm_round` / `9x19mm_casing`（2026-09-27 起）现在也复用同一入口。Forge 1.20.1 没有独立的普通 Item 客户端扩展注册事件，因此 `AflStaticMeshItemClient` 在客户端 setup 时给这些 Item 实例设置 Forge `renderProperties` 扩展。`AflStaticMeshItemRenderer` 现以模型/纹理参数选择资源（2026-09-29 增加一个接收完整 geo 和纹理 ResourceLocation 的构造函数，供方块物品共用方块自己的 atlas，首个使用者是 `industrial_locker`；原构造函数不变），继续使用 Gecko 当前 baked geo 骨骼和既有 `AflMeshCache` / `AflMeshRenderer`；每次绘制取当前缓存快照，不持有跨 F3+T 的旧 Mesh/GeoBone。12 Gauge 保持原 256×256 atlas、4 Mesh part/672 triangles 和原 display；.50 AE 两件为 `builtin/entity` Item。2026-09-27 起换成 V2 软尖弹资产：整弹 3 个 Mesh 部件、1200 三角面，空壳 2 个部件、1080 三角面，共用重画后的 `blackridge_50ae_ammo_v1.png`。总高不变，所以中心补偿常量也不变；详见 [native_ammo_assets_v1.md](native_ammo_assets_v1.md)。9mm 两件（整弹 1000、空壳 1080 三角面，共用 `9x19mm_ammo_v1.png`）的中心补偿为 `0.451463` / `0.468168`。抛壳侧，`NativeGunFx` 的 Mesh 弹壳现在按弹壳模型查表（`.50 AE` 与 9mm），不再只写死 `.50 AE`。实际 GUI/手持/掉落、热重载与 shader 画面仍待实机验证。

`MaintenanceGunRendering` 仍使用自己的静态 bind-pose 副本。Mesh 同步参与 draw 和 bounds；替换弹匣的 Mesh 与 Cube 同样省略。每个 part 的缓存 AABB 八角用同一 bone/pivot 矩阵变换，得到保守包围盒；纵向居中使用 Cube+Mesh 合并范围。Mesh resource generation 变化时清理 bounds/纵向中心缓存，避免仅 sidecar 改变而沿用旧边界。附件仍不参与重心重算；原静态副本的 subtree 省略规则保留。

**Anchor contract changed = NO**。right/left hand、muzzle、shell、sight、maintenance/attachment anchors 与 hotspot semantics 均未改；没有 triangle picking。CPU 数据、cache 与 backend 分离，未来可替换提交 backend；当前没有实现或承诺 VBO。

### 加载时三角面配对与第一人称背面跳过（2026-10-05）

**起因**：用户 2026-10-05 的录屏（Sundial 光影，加油站着火冒烟的场景）里，空手约 115–139 FPS，掏出 P9 后约 65–90 FPS，GPU 占用反而更低。据此判断瓶颈在 CPU 逐顶点提交：Oculus 的扩展顶点格式还要给每个 Quad 算 tangent 和 mid-UV。没有改 sidecar 格式、导出器、贴图或动画。

**1. 加载时三角面配对**（`AflMeshLoader#pairTriangles`，对所有 AFL Mesh 生效）
- 合并条件：
  - 两个三角形共用一条边：同两个顶点索引，所以位置和 UV 相同；两边方向相反，绕序一致。
  - 面法线点积 > 1 − 1e-6。
  - UV 导数 dP/du、dP/dv 相差不超过自身长度的 1e-3，而且 handedness 相同（`sameUvFrame`）。
  - UV seam 两边的顶点索引不同，永远不会合并。
- 合成的 Quad 是 `(a,b,c,d)`，共用边 a–c 当对角线。QUADS 绘制按 (0,1,2)(2,3,0) 拆分，正好还原原来那两个三角形；镜像提交保持同一条对角线。每合并一次，提交的顶点从 8 个变成 4 个（省掉两个退化的第四角）。按 face 顺序贪心合并。
- 光栅化的几何、UV、绕序都不变。Oculus 下有三处附加数据会变：
  - 后一个三角形改用前一个的面法线，两者差 < 1e-6。
  - Quad 的 tangent 由前三个角算出，后一个三角形沿用它。两者的 UV 导数差 ≤ 1e-3，而 tangent 本身按每轴 1 字节打包，精度远粗于 1e-3。
  - mid-UV 改成整个面四个角的平均。
- 和 V2.1 导出器的关系：
  - 导出器要求差异能被量化误差解释（UV 导数相对误差 1e-5，严格等价），而且不跨源 face 合并，所以 P9 有 927 对 UV_UNSAFE、1296 对 NON_PLANAR 留成了三角形。
  - 运行时用的是视觉容差。在已经量化过的数据上，1e-5 一对都过不了，因为量化噪声约 1e-4。1e-3 以内的差异在打包后的 tangent 里看不出来；共面容差 1e-6（约 0.08°）同样远小于字节法线的精度。
  - 两个不同源 face 的三角形，只要共用顶点、共面、UV 走向一致，也可能被合并，画出来一样。
  - 导出器、sidecar、`verify-afl-mesh-v21.mjs` 都不变。离线校验检查的是文件，不包括运行时配对。

**2. 封闭体标记**（`AflMeshPart#closed`）

加载时按精确位置焊接顶点，然后检查三件事：没有同向重复的边；每条边都有反向边；每个独立壳体的有向体积都 > 0（法线朝外）。有一个壳体是翻面的，整个 part 就不算封闭。

**3. 第一人称背面跳过**（`AflMeshRenderer.cullBackFaces`）
- 只有 `NativeGunContextRenderer` 在第一人称手持（左手或右手）、且不在 shadow pass 时打开，结束后恢复原值。第三人称、F5、GUI、维护台、掉落物、方块和 shadow pass 都不跳。
- 只跳封闭的 CUTOUT 层 part。透明层（例如红点镜片）照常提交，因为透过玻璃要能看到背面。
- 判断方法：face 的法线背向眼睛（眼睛在原点，n·p ≥ 0）就不提交。手持路径的视角晃动在 PoseStack 里，所以眼睛正好在原点。
- 原来用的 `entityCutoutNoCull` 也会提交背面，只是被正面挡住。已检查 P9、Blackridge、BR51、HR55、Silverwood、弹匣和消音器的贴图，都没有透明像素，跳过背面不会从镂空处露出差别。红点贴图有半透明像素，但镜片在透明层，不跳。

**离线估算（未实测）**

下表是第一人称每帧提交的 vertices，按与 loader 相同逻辑的离线脚本算出：
- 不含隐藏的换弹辅助骨骼；
- 背面跳过按封闭 part 的 face 数一半估算，实际值随视角变化。

| 枪 | 原来 | 配对后 | 再加第一人称背面跳过（估） |
|---|---|---|---|
| P9 | 23,360 | 17,392（−26%） | ≈12,300（−47%） |
| Blackridge | 14,620 | 11,660（−20%） | ≈10,000（−32%） |
| BR51 | 22,020 | 21,628（−2%） | ≈14,000（−36%） |
| HR55 | 21,440 | 20,976（−2%） | ≈11,200（−48%） |
| Silverwood | 18,992 | 17,608（−7%） | ≈8,800（−54%） |

方块 Mesh 的情况：
- 饮料柜、冰柜、售货机、收银台、货架商品库的 sidecar 本来就是 Quad，配对 0 次；办公椅 53 次（−2%）。
- 背面跳过不对方块开：世界相机的视角晃动加在投影矩阵里，眼睛不严格在原点，贴边的角度会判错。

验证方法：用 `-Dafl.debug.renderProfile=true`，对比 `vertices_per_frame`、`quad_faces_per_frame`、`triangle_faces_per_frame` 和 `cpu_ms_per_frame`。Probe 只统计 P9 和 Blackridge 的主枪。

尚未完成的验证：没有实测 FPS；没有在实机 Sundial 下核对 normal map 和高光是否与之前一致。没有做 VBO 缓存，在 Oculus 扩展格式下风险较高。

## 离线 fixture 与已完成检查

- Editable source：`src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel`。
- 固定测试输入：`src/dev/resources/afl_mesh_core/fixture.{geo.json,aflmesh.json,expected.json}`。
- 1 Cube + 3 Mesh（4 triangles）；Cube/Mesh 共用 root；child 有非零 pivot/rotation，Mesh 自身也有非零 origin/rotation；独立 visibility bone；16×16 单 atlas。保留离线坐标验证数据，不再提供游戏内动画查看物品。
此 fixture 只供 `tools/verify-afl-mesh.mjs` 离线验证坐标、loader 与 renderer；`afl_mesh_core_fixture` 游戏物品仍已移除。正式 `silverwood_12` 自 2026-09-29 起为 V3 左右并列双管：可编辑源 `src/main/blockbench/silverwood_12.bbmodel`（由 `tools/build-silverwood-12-v3.mjs` 生成），V2 sidecar 24 个部件（含 4 发膛内弹壳）、三角等效 6580，无 Cube，1024 atlas 加 `_s` / `_n`，见 `silverwood_12_v3_sxs.md`。（历史：此前可编辑源为 `silverwood_12_hybrid_claude_reload_presentation_v2.bbmodel`，46 part / 4088 triangle sidecar 加 115 Cube 骨架。）V3 未实机验证。

```powershell
node tools/verify-afl-mesh.mjs
node tools/verify-afl-mesh.mjs --java-loader
node tools/verify-afl-mesh.mjs --java-renderer
```

第三个命令包含 loader 检查。Java 模式需要本机已缓存的本项目 Gson/Minecraft/GeckoLib/JOML 等依赖；JShell 直接执行实际源码，仅临时移除 package 声明。临时 harness 位于系统 temp，完成删除；不运行 Gradle、不生成项目 class 文件、不启动客户端。`--java-renderer` 使用真实 PoseStack/GeoBone/RenderUtils 和记录顶点的 VertexConsumer。

已完成：确定性 V1 输出、22 类 converter 错误、实际 V1/无版本号/V2 loader 检查、凹多边形三角化、flat normal/unit length、local bounds；非零 element/group 坐标相对独立 Blockbench THREE 参考的最大 double 误差约 `4.92e-11` 格。实际 Gecko traversal 和 renderer 对参考验证 float 误差小于 `2e-6` 格；五组普通/镜像/非均匀缩放下 V1/V2 winding 与 normal 一致，UV/color/light/overlay 一致，hidden/null/零尺度不提交，bounds 包含提交顶点。V2 新增非法版本、非 3/4 面、自交/扭曲 Quad 拒绝测试，以及真实 Quad 4 次 / V1 两三角形 8 次提交和 metrics 检查。正式 P9、Blackridge 重新导出结果与保存的 V2 sidecar 一致，展开后逐 part 的 position/UV、带绕序的三角形与 V1 完全一致；所有当前正式 sidecar（含 V1 弹药）通过真实 Java loader。无 sidecar、P0 shadow skip hook、frozen 遍历和 reload generation 连接另有静态检查。**这些是离线验证，未启动游戏，未测 V2 实机 CPU、视觉或 Shader 输出。**

此前的最小项目编译使用 `./gradlew.bat compileJava --offline`；该结果不等同于客户端渲染验收。游戏内测试仍需单独执行。

未完成的实机验收：Cube+Mesh 画面、第一人称/第三人称/维护台 framing、动画/hidden-child/reRender 与弹匣/shell 场景组合、实际热重载/坏资源回退、Embeddium/Oculus/shader 兼容。当前沿用原 buffer/state 以降低风险，不能据此宣称 shader 验收通过。

旧 `src/dev/resources/blackridge_50_candidate/` 验证快照、旧 Blackridge 候选材质 PNG、未使用的弹药占位 PNG 与仓库根目录两张粘贴图片已在正式接入清理时删除；上文涉及它们的段落仅记录历史阶段，当前有效资产为 Silver V3 拉丝不锈钢枪械贴图（见下文，黑铁系列已移除）和 `blackridge_50ae_ammo_v1.png` 弹药 atlas。可编辑正式源与当前运行资源保留。

2026-09-26 **（历史，贴图已移除）Blackridge Material Refinement V2（Blackiron v3，降低锯齿感）**：新贴图 `src/main/blockbench/textures/blackridge_50_blackiron_v3.png`（1024×1024、Base Color only、同一 UV），曾复制为运行贴图（已被下段 v5 取代）；v2 原文件保留在 `src/main/blockbench/textures/` 作回退。与 v2 相同的调色与分区（主体深 gunmetal、控件更深更哑、握把低反光、内腔近黑，内腔按 v2 亮度 ≤18 识别），差别在明暗逻辑：改用自动平滑法线（40° 内相邻面共享）逐像素插值后连续映射受光，折面不再各自一个明度；侧壁为贯穿部件长度的连续渐变（下缘暗、中灰、上半一条柔和长高光带）；凸边高光只保留夹角 >55° 且长度 >1.2 的主轮廓边（枪口、抛壳窗、主倒角等），软衰减、无硬亮线；锯齿槽/凹面仅做软 AO，去掉 v2 的逐行随机细纹。生成脚本与参数：`tools/paint-blackridge-50-blackiron-v3.bb.js`、`tools/paint-blackridge-50-blackiron-v3.params.json`（在已打开的 Blockbench 工程中执行）。Geometry、UV、Rig、Pivot、Anchor、动画均未改；Blockbench 工程中 v3 已排在贴图索引 0 并分配给全部面，**工程尚未保存**。`blackridge_50_inventory.png` 图标仍由 v2 外观渲染。仅做 Blockbench 预览对比，未做游戏内验收。

2026-09-26 **（历史，贴图已移除）Blackridge Material V5（Blackiron v5，统一黑化钢）**：新贴图 `src/main/blockbench/textures/blackridge_50_blackiron_v5.png`（同 UV、Base Color only）已复制为运行贴图 `textures/item/blackridge_50.png`；v2/v3 原文件保留。修正 v3 “滑套偏亮灰、枪身下半偏黑”的双色感：金属部件（滑套、枪身/扳机护圈/尾部护板、枪管外露段、控件、准星）的侧壁渐变不再按各部件自身高度归一，而统一使用整枪世界高度 y 4.8–9.8、长度 z −10–7.8 和同一相位的低频起伏，因此相邻部件在交界处连续；渐变幅度收窄为 0.86–1.06，受光增益降为顶面 1.08 / 底面 0.74（立体感交给游戏光照，避免与 Minecraft 面向明暗叠加）。调色压回深冷 gunmetal：主体 [54,57,63]、控件 [38,39,43]、准星 [34,35,39]、握把 [21,21,23]（保留颗粒防滑纹）、弹匣 [50,53,58]、内腔 [12,12,14]；上半部一条更宽的柔和长高光带（强度 0.2），主轮廓边薄高光 0.2，凹槽/接缝 AO 加深。生成脚本与参数：`tools/paint-blackridge-50-blackiron-v5.bb.js`、`tools/paint-blackridge-50-blackiron-v5.params.json`。Geometry、UV、Rig、Pivot、Anchor、动画均未改；Blockbench 工程中 v5 为贴图索引 0 并分配给全部面，**工程尚未保存**；`blackridge_50_inventory.png` 图标仍为旧外观。仅 Blockbench 预览，未做游戏内验收。

2026-09-26 **Blackridge Material：Chrome v3 抛光镀铬（已被下段 v4 取代）**：用户选定镀铬外观并要求移除黑铁版本。黑铁 v2/v3/v5 源贴图已从 `src/main/blockbench/textures/` 删除（v2、v5 可从 git 历史恢复）；源同名副本 `src/main/blockbench/textures/blackridge_50.png` 与运行贴图 `textures/item/blackridge_50.png` 现均为 `blackridge_50_silver_v3.png` 内容（另保留中间稿 `blackridge_50_silver_v2.png`）。明暗逻辑沿用 v5 的整枪统一高度渐变与自动平滑法线，另加：侧壁中下部一条暗色"地平线"反射带（强度 0.45、中心 h 0.52）与上部窄亮高光带（0.4、h 0.85），朝上面额外提亮 8% 模拟天空反射；调色为主体 [170,173,178]、控件 [140,143,148]、弹匣 [130,133,138]、握把黑色橡胶 [22,22,24]、准星/内腔深色。生成脚本与参数：`tools/paint-blackridge-50-chrome-v3.bb.js`、`tools/paint-blackridge-50-chrome-v3.params.json`（黑铁 v3/v5 生成脚本保留在 `tools/` 仅作参考）。Blockbench 工程中 `blackridge_50.png` 为贴图索引 0、分配给全部面，**工程尚未保存**；`blackridge_50_inventory.png` 图标仍为旧外观，需重新渲染。仅 Blockbench 预览，未做游戏内验收。

2026-09-26 **Blackridge Material：Chrome v4 硬镜面镀铬（已被下段 v5 取代：窄暗线在滑套大平面上像一条缝）**：`src/main/blockbench/textures/blackridge_50_silver_v4.png` 已复制为源同名副本 `src/main/blockbench/textures/blackridge_50.png` 与运行贴图 `textures/item/blackridge_50.png`（v3 保留）。在 v3 基础上把反射做硬：暗色"地平线"带收窄加深（强度 0.6、宽 0.06、中心上移到整枪高度 0.64，落在滑套/枪管侧面中部），上方窄亮高光带（0.5、宽 0.06、h 0.86），侧壁基础渐变 0.78–1.08，天空反射提亮 10%，主轮廓边高光 0.34。内腔识别改用 `blackridge_50_silver_v3.png` 亮度 ≤25（不再依赖已删除的黑铁贴图）。参数：`tools/paint-blackridge-50-chrome-v4.params.json`（脚本 `tools/paint-blackridge-50-chrome-v3.bb.js` 通用）。反射带画在 Base Color 中，不随视角移动（无 PBR）。Blockbench 工程贴图已重新加载，**工程尚未保存**；物品栏图标待重渲染；未做游戏内验收。

2026-09-26 **Blackridge Material：Chrome v5 镜面镀铬（已被下段 Silver Base Color V2 取代）**：`src/main/blockbench/textures/blackridge_50_silver_v5.png` 已复制为 `src/main/blockbench/textures/blackridge_50.png` 与运行贴图 `textures/item/blackridge_50.png`（v3、v4 保留）。保留 v4 的窄亮高光（0.48、宽 0.065、h 0.86），暗部改为宽而柔的过渡（强度 0.4、宽 0.15、中心 h 0.58），侧面呈连续"上亮 → 下暗 → 下缘回升"，不再出现孤立黑线。参数：`tools/paint-blackridge-50-chrome-v5.params.json`（脚本 `tools/paint-blackridge-50-chrome-v3.bb.js`）。Blockbench 工程需重新加载贴图并保存；物品栏图标待重渲染；未做游戏内验收。

2026-09-26 **Blackridge Material：Silver Base Color V2 抛光不锈钢（已被下段 Silver V3 取代：实机偏白）**：`src/main/blockbench/textures/blackridge_50_silver_basecolor_v2.png` 已复制为 `src/main/blockbench/textures/blackridge_50.png` 与运行贴图 `textures/item/blackridge_50.png`（v5 保留）。纯 Base Color，面向原版无光影环境：不再伪造环境反射（去掉暗色反射带与天空提亮）；明暗按每个切面的平面法线分级（顶 1.28 / 上倒角 1.16 / 主侧 1.0 / 下倒角 0.78 / 底 0.6），整枪统一高度渐变仅 0.88–1.0；滑套/枪身主侧面沿长轴有连续窄高光（世界高度 y 8.25，宽 0.06 单位≈2–3 px，另 y 6.95 一条较弱），主轮廓边薄高光 0.28；凹槽/锯齿槽 AO 加深（核心 0.42、凹底 0.52）。调色：主体 [165,168,174]、控件 [120,123,129]、弹匣 [150,153,159]、握把 [22,22,24]、准星 [42,43,47]、内腔 [18,18,20]（内腔按 silver_v5 亮度 ≤25 识别）。脚本与参数：`tools/paint-blackridge-50-silver-basecolor-v2.bb.js`、`.params.json`。Geometry/UV/Rig/动画未改；Blockbench 工程需保存；物品栏图标待重渲染；未做游戏内验收。

2026-09-26 **Blackridge Material：Silver V3 Brushed Stainless（当前正式外观）**：`src/main/blockbench/textures/blackridge_50_silver_v3_brushed.png` 已复制为 `src/main/blockbench/textures/blackridge_50.png` 与运行贴图 `textures/item/blackridge_50.png`（Silver Base Color V2 保留）。基于 V2 精修：主体压到中间调 [140,143,149]（贴图亮度中位 119→97、P95 215→172），弹匣 [128,131,137]、控件偏暗枪钢 [96,99,105]、准星 [40,41,45]、握把 [22,22,24]、内腔 [16,16,18]；切面分级 顶 1.2 / 上倒角 1.1 / 主侧 1.0 / 下倒角 0.74 / 底 0.56；长轴窄高光收窄减弱（0.34，宽 0.045），边缘高光 0.26、宽 1.1；凹槽 AO 核心 0.4、凹底 0.5。新增定向拉丝：仅主体/弹匣面积 >0.25 的非端面，沿枪身长轴（侧面按世界高度、顶/底面按 X 分线，线距 0.045 单位≈1 px），每线沿长轴低频起伏，幅度 ±3.5%；控件、握把、小倒角、端面不加。无环境反射烘焙，无 PBR。脚本与参数：`tools/paint-blackridge-50-silver-v3-brushed.bb.js`、`.params.json`。Geometry/UV/Rig/动画未改；Blockbench 工程需保存；物品栏图标待重渲染；未做游戏内验收。

2026-09-26 **（历史，已被下段 PBR V1 取代；V0 实机 PASS：Oculus + Complementary labPBR 已读取 `_s`）Blackridge PBR Compatibility V0**：新增 LabPBR 高光贴图 `textures/item/blackridge_50_s.png`（1024×1024 RGBA，与运行 Base Color `textures/item/blackridge_50.png` 同名加 `_s`、同 UV；源副本 `src/main/blockbench/textures/blackridge_50_s.png`）。GeckoLib 以 `apocalypse_firstlight:textures/item/blackridge_50.png` 绑定枪械贴图，Iris/Oculus 按 LabPBR 约定查找同目录 `_s`。通道：R 光滑度、G F0（255 = 取 Base Color 的金属）、B 孔隙度 0、A 255（无自发光）。V0 故意夸张：银色主体 [215,255,0]、弹匣 [200,255,0]、跟弹板 [160,255,0]、控件 [150,255,0]、准星 [90,230,0]（铁）、握把 [25,10,0] 与内腔 [40,10,0] 为非金属高粗糙度；分区与 Silver V3 Base Color 同一脚本生成：`tools/paint-blackridge-50-labpbr-s.bb.js` + `tools/paint-blackridge-50-labpbr-s-v0.params.json`。未加 `_n` 法线；Base Color、模型、UV、动画、Java 均未改。**是否被 Hybrid Mesh 渲染路径读取尚未验证**；Complementary 需把材质模式设为 labPBR（默认的 Integrated PBR 不读资源包 `_s`）。

2026-09-26 **Blackridge PBR V1（正式 LabPBR 1.3，待实机验收）**：运行贴图三件套均为 1024×1024 RGBA、同 UV：Base Color `textures/item/blackridge_50.png`（Silver V3 Brushed，未改）、`blackridge_50_s.png`（重写）、`blackridge_50_n.png`（新增）；源副本在 `src/main/blockbench/textures/`。三张图由同一脚本 `tools/paint-blackridge-50-pbr.bb.js` 生成（`P.pbr` 省略 = Base Color，`'spec'` = `_s`，`'normal'` = `_n`；参数 `tools/paint-blackridge-50-pbr-v1-s.params.json` / `-n.params.json`），共享 UV 光栅、部件分类、平面法线切面分级、主轮廓凸边/凹边判定与拉丝函数（同种子、同方向，沿枪身长轴）。
- `_s`：G 金属均为 255（F0 取 Base Color），握把 G 10（F0≈0.04 非金属）；B 0；A 255。R 光滑度：主体 180（顶 +12、上倒角 +8、下倒角 −15、底 −22，主轮廓凸边 2.5 px 内趋向 205，凹面/凹边 3 px 内趋向 118，拉丝 ±7）；弹匣 168（倒角 185、凹 118、拉丝 ±5）；控件 140（倒角 158、凹 100）；跟弹板 150；准星 105；内腔 70（仍为金属，但暗且粗糙）；握把 32（2 px 颗粒 ±5）。
- `_n`：RG 为切线空间法线 XY（OpenGL，+v 向上），B（AO）255、A（高度）255（不触发视差）。仅拉丝大面（强度 0.07，法线偏移约 ±9° 以内）与握把颗粒（0.35）有微法线，其余平。
- 未烘焙环境反射；Java、渲染器、模型、UV、动画未改。实机验收（Oculus + Complementary，labPBR，高级反射开启）尚未进行。

2026-09-26 **Blackridge Dynamic Ammo V1 Step 1（仅资产锚点）**：正式可编辑源 `src/main/blockbench/blackridge_50.bbmodel` 的 `handling → magazine` 下新增空的 `magazine_round_anchor`，Blockbench pivot `[0,7.96,3.68]`、rotation `[-90,0,0]`；其原点对齐弹匣 feed lips 下方顶弹的弹壳尾部，子弹轴朝枪口（−Z）。正式 `geo/blackridge_50.geo.json` 同步新增同名子骨骼；重新运行 `tools/export-afl-mesh.mjs` 后 16 个 Mesh part / 4992 triangles 的 sidecar 无内容差异。该步骤当时**没有动态顶弹渲染或弹量可见性逻辑**，不处理辅助弹匣、inspect/reload/follower，未改弹药模型、枪械几何、UV、贴图、动画或 Java。位置与角度仅静态核对，游戏内视觉仍待后续步骤验证。

2026-09-26 **Dynamic Magazine Round Visual Step 3A（历史阶段；合同已由下段 V1 扩展）**：Native Gun 的 `presentation.magazine_round_visual` 是完全可选的对象，仅含 `anchor`（非空合法 bone name）、`geometry`（Hybrid Mesh Geo 资源 ID）、`texture`（独立 atlas 资源 ID）；字段缺失时为禁用，不改变旧枪默认值。`NativeMagazineRoundVisual` 在 `NativeGunData` 解析阶段校验三字段并作为 `NativeGunDefinition.magazineRoundVisual` 提供给客户端；现有 Native Gun 原始 JSON 快照同步自动携带此字段，无新 packet，不在数据加载时打开模型或验证资源是否存在。Blackridge 的 `native_guns/blackridge_50.json` 已声明 `magazine_round_anchor`、`apocalypse_firstlight:geo/50_ae_round.geo.json` 与 `apocalypse_firstlight:textures/item/blackridge_50ae_ammo_v1.png`，复用正式 .50 AE 资产。**此阶段尚未读取弹量或调用 renderer，因此当时不会显示顶弹**；下一步计划将可见条件固定为 `magazineAmmo > 0` 并由 `AflHybridMeshRendering` 在 bone pose 下绘制，不使用 ItemDisplayContext。辅助弹匣、chamber round 与维护台接入均未实现。

2026-09-26 **Dynamic Magazine Top Round Step 3B（历史阶段；辅助弹匣限制已由下段扩展）**：`NativeAnimatedWeaponRenderer` 在原有每骨骼 render layer 中调用 `NativeMagazineRoundRendering`，按 `NativeGunDefinition.magazineRoundVisual` 的 anchor 名匹配、读取当前枪械 `ItemStack` 的 `NativeGunAmmo.read(stack, definition)`；弹数 `> 0` 时在该骨骼现有动画姿态下调用 `AflHybridMeshRendering.renderAtCurrentPose` 绘制配置指定的 Geo/atlas，弹数 `0` 时跳过。调用者沿用 GeckoLib 已继承的父骨骼变换；当前骨骼 hidden、祖先 `childrenHidden` 或 pose 矩阵为零缩放/无效时跳过，避免枪内弹匣隐藏时出现悬浮顶弹。raw helper 缺失 Geo/sidecar 或贴图时安全跳过；贴图存在性按现有 Mesh 资源代次缓存，不逐帧读文件。无 ItemDisplayContext 二次变换、无 Blackridge 硬编码、新 NBT 或弹量缓存。范围仅限 `NativeAnimatedWeaponRenderer` 负责的 3D 持枪路径；该阶段辅助弹匣、chamber round、maintenance renderer 和 inspect 辅助弹匣顶弹尚未实现，follower 的任意弹数状态未改。`inspect_empty` 选择现由独立的通用 Item 修复提供，不属于 Step 3B。用户后续已报告枪内顶弹与 inspect 分支实机测试通过；辅助弹匣状态以下段为准。

2026-09-26 **Blackridge Dynamic Ammo V1 — Auxiliary Magazine Round（已实现，待用户实机验收）**：正式源 `src/main/blockbench/blackridge_50.bbmodel` 在 `mag_out → reload_magazine` 与 `empty_old_mag` 下分别新增空的 `reload_magazine_round_anchor`、`empty_old_mag_round_anchor`，两者 Blockbench pivot `[0,7.96,3.68]`、rotation `[-90,0,0]`；正式 Geo 同步，AFL Mesh sidecar 仍为 16 parts / 4992 triangles、内容不变。`presentation.magazine_round_visual` 保留原 `anchor`、`geometry`、`texture`，增加可选且不得重名的 `loaded_auxiliary_anchor`、`old_magazine_anchor`；旧 JSON 不提供新字段时仅有枪内顶弹，行为不变。Blackridge 为两个新字段指向上述锚点，三处共享原 `50_ae_round` Geo 与贴图。`NativeAnimatedWeaponRenderer` 从 GeckoLib `action` 控制器的当前 clip 提供动作状态，`NativeMagazineRoundRendering` 在同一骨骼遍历中按 anchor 处理：枪内顶弹仍仅在 `NativeGunAmmo.read > 0` 时显示；新弹匣顶弹在 `reload_tactical`、`reload_empty` 或有弹 `inspect` 时显示，在 `inspect_empty` 隐藏；旧弹匣顶弹仅在 `reload_tactical` 显示。辅助顶弹继承父骨骼姿态；隐藏父级与零尺度姿态跳过绘制，不改动画、弹药结算时机或枪内顶弹判定。未实现 chamber round、辅助 follower 逐发位置、完整弹匣内部弹列或维护台动态弹药。此轮仅静态/编译验证；战术换弹、空仓换弹与检视时的辅助顶弹位置及遮挡仍需用户实机测试。

2026-09-27 **P9-01 Dynamic Magazine Ammo Visual V1（运行时接入，待用户实机复验）**：`data/apocalypse_firstlight/native_guns/p9_01.json` 的 `presentation.magazine_round_visual` 使用现有 `magazine_round_anchor`、`mag_out_round_anchor`、`empty_old_mag_round_anchor`，共用正式 `geo/9x19mm_round.geo.json` 与 `textures/item/9x19mm_ammo_v1.png`。三个锚点在 Blockbench 源与 Geo 中均已存在；无 Rig、动画、Mesh、UV、贴图或 gameplay 变更。P9 锚点原点在弹体中部；运行时 Geo X 旋转被 GeckoLib 取反，现用 `local_offset=[0,-0.000036,0.789958]`（Blockbench 单位）及 `local_rotation=[-90,0,0]` 对齐弹底。此前把 Geo JSON 的 `+22°` 误认为最终骨骼姿态、配置了 `[0,0.548725,0.568273]` / `[90,0,0]`，导致三处顶弹方向与位置错误，已纠正；未配置这些字段的 Blackridge 保持单位变换。枪内顶弹以真实 `NativeGunAmmo` 装弹数 `>0` 显示，旧匣顶弹要求战术换弹且当前弹数 `>0`，新匣顶弹在被接受的两种换弹动作或有弹检视中显示；辅助匣父骨骼隐藏/零尺度时均在 Mesh 提交前跳过，`inspect_empty` 不显示顶弹。旧匣的战术/空仓分类取自服务端选出的 reload clip，弹数只读取枪内 NBT，不用创造模式无限备弹判断。`NativeGunShadowSkip` 同样作用于独立顶弹 Mesh，仅跳过本地第一人称相机下的本人第三人称 shader shadow traversal；F5 和其他玩家仍按原渲染路径。9mm 静态物品、弹壳 FX、膛内弹、换弹结算与 Blackridge JSON 未改。本轮修正后 `compileJava --offline` 成功，但任务为 `UP-TO-DATE`，未重新执行 Java 编译；客户端视觉、顶弹尺寸/遮挡及 shader 表现仍需实机验收。

2026-09-27 **Muzzle Attachment Hybrid Mesh（已实现，compileJava 通过，待用户实机验收）**：`NativeMuzzleRendering.drawItem` 在绘制前用附件自身的 `geo/<id>.geo.json` 查询 `AflMeshCache.snapshot()`；存在 sidecar 时调用 `AflHybridMeshRendering.renderAtCurrentPose(geometry, textures/item/<id>.png, pose, buffers, light, overlay)` 后返回，否则保持原 GeckoLib Cube 遍历（含 `reticle` 全亮组）。枪上安装（`NativeMuzzleRendering.render`，锚点 pivot 已由调用者平移）、维护台、Geo 瞄具 / 弹匣附件和独立物品 `NativeMuzzleRendering.ItemRenderer` 都经过这一处，没有新增 renderer，也没有任何枪械专用分支。`applyExit` 仍从同一 Geo 的 `muzzle_exit_anchor` 骨骼求出口，Mesh 附件的 Geo 只需保留该骨骼。当前只有 `pistol_suppressor_01`（AFL 通用 9mm Pure Mesh，1440 三角面，V2 sidecar，512 atlas 带 `_s` / `_n`）有 sidecar；`rifle_suppressor_01`、`pistol_red_dot`、`rifle_red_dot_01`、扩容弹匣等 Cube 附件的渲染路径与结果不变。资产与生成器见 `docs/native_guns/attachments/pistol_suppressor_01_model_v1.md`；车床生成库由 `tools/lathe-ammo-lib.mjs`（`runLatheAmmo`）改名为 `tools/lathe-mesh-lib.mjs`（`runLathe`），弹药输出逐字节不变。

2026-09-27 **Ejected Casing Low-Poly FX V1（已实现，compileJava 通过，待用户实机验收）**：`NativeGunFx.MESH_CASINGS` 的 `MeshCasing` 增加 `fxGeometry` / `fxTexture`，飞行抛壳优先使用 FX 专用低模 `geo/9x19mm_casing_fx.geo.json`、`geo/50_ae_casing_fx.geo.json`：各 112 三角面，V2 格式，48 Quad + 16 三角形，每个弹壳提交 256 个顶点，原先是 4320 个；配套 64×64 贴图带 `_s` / `_n`。FX geo 或 sidecar 不在 `AflMeshCache` / GeckoLib 缓存中时，才回退到正式高精度弹壳。物品、掉落、弹匣顶弹与展示不变，抛壳物理、寿命和上限不变。资产规则与数据见 `docs/native_guns/native_ammo_assets_v1.md`。`tools/verify-afl-mesh.mjs` 中 P9 常态可见三角面的固定期望值同步为 6864，原值 6384，变化来自已提交的 P9 螺纹枪管，与本轮无关；`--java-loader` 模式用真实 Java 加载器通过了全部生产 sidecar，包括两件 FX。
