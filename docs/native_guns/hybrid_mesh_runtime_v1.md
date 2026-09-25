# AFL Hybrid Mesh Runtime V1 — Round 1

2026-09-24。状态：核心实现；离线数据/坐标/顶点提交检查通过，图形客户端与资源热重载尚未实机验收。基线：Minecraft 1.20.1、Forge 47.4.22、Java 17、GeckoLib 4.7.4。

## 范围与 opt-in

GeckoLib 继续持有 skeleton、动画、骨骼姿态和 Cube 绘制。AFL 的独立 sidecar 只提供绑定到一个 bone 的刚性 Mesh。没有 skinning、morph、动态拓扑、运行时 subdivision、多材质、透明排序、LOD、VBO、自定义 shader 或 GeckoLib fork。

资源约定（命名空间和子目录均保留）：

| 资源 | 路径 |
|---|---|
| 既有 Gecko geometry | `assets/<namespace>/geo/<id>.geo.json` |
| 可选 Mesh sidecar | `assets/<namespace>/meshes/<id>.aflmesh.json` |
| atlas | 沿用枪械 renderer 当前 texture；sidecar 不另选贴图 |

`NO_SIDECAR => OLD_RENDER_PATH_UNCHANGED`。BR51、HR55 和 P9 没有新增 sidecar，继续走旧绘制。正式 `silverwood_12` 现已通过 `meshes/silverwood_12.aflmesh.json` 绑定 Hybrid Mesh V2、Claude 动画和四个事件化机械音效；其 Registry ID 与原 Native Gun 玩法不变。临时测试物品已删除。

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

Loader 校验版本、固定坐标/UV/winding 标记、字段集合、唯一 part 名、目标 bone 存在、atlas 与 geometry 描述一致、有限数、数组长度、索引范围、非零面积。顶点在转成 float 后再验证退化。限制：每份 JSON 最多 4 MiB 字符；1–128 parts；全模型最多 16,384 triangles、65,536 vertex slots；局部位置绝对值不超过 256 格；UV 在 `[0,1]`；atlas 各边 1–4096。未知/多余字段拒绝。错误包含资源路径，part 内错误另含 bone/part（名称无法解码时给 part 索引）。

## 坐标、pivot 与 winding 合同

以下合同与已读取的 Blockbench 5.2.1 Free Model 及 GeckoLib 4.7.4 实现一致；不用补偿偏移。

- Blockbench Mesh `vertices` 相对于 **element.origin**；Free Model group 使用绝对 `origin`。每格 16 authoring units。
- Mesh element 的 Euler order 为 `XYZ`：列向量下 `Rmesh = Rx * Ry * Rz`。group 的 order 为 `ZYX`：`Rbone = Rz * Ry * Rx`。
- 本项目现有 Native geo exporter 写入 `geo.pivot = [-BB.x, BB.y, BB.z]`、`geo.rotation = [-BB.rx, -BB.ry, BB.rz]`。Gecko loader 再反转 pivot 的 X 与 rotation 的 X/Y；最终运行轴与 source 一致。因此 **sidecar 不再反转 X**，右手叉乘及 source 的 CCW 正面方向保持。
- 设 source element origin 为 `E`，其 parent group origin 为 `P`，Mesh vertex 为 `v`，则 sidecar 存储 `q = (E + Rmesh * v - P) / 16`。只烘焙 element 的刚性局部变换，保留 bone 动画能力。
- Gecko 在当前 parent pose 后依序乘：`T((-posX,posY,posZ)/16)`、`T(P/16)`、`Rz*Ry*Rx`、`Sbone`、`T(-P/16)`。Mesh hook 在该已准备好的矩阵后追加 `T(P/16)`，然后提交 `q`。每层父 bone 都沿用实际 traversal，不另算第二套动画。
- UV 来自 Blockbench face 的像素坐标，除以 atlas UV 宽高；V=0 在顶部，**不翻 V**。THREE 预览内部的 V 翻转不搬到 Minecraft。
- face 顶点必须按边界顺序给出，从法线外侧看为 CCW。转换器保留 winding；拒绝可检测的自交/重复点/退化/严重非平面面，不猜测乱序 quad。镜像 pose 反转提交顺序，并用实际矩阵的 inverse-transpose 变换法线；零尺度/奇异矩阵不提交。

## 离线转换器

`tools/export-afl-mesh.mjs` 接受 Free `.bbmodel` 和独立目标 `.geo.json`，只写 sidecar。原始 Cube、骨架、动画、贴图均不被改写。当前仅支持 source group 与目标骨架在 hierarchy/pivot/rotation 上一致的模型；可选 mapping 只重命名 group，不是任意骨架 rebinder。编辑器 `visibility` 是预览状态；`export:false` 才剔除资源，runtime hidden 由 bone 管理。

```powershell
node tools/export-afl-mesh.mjs --input src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel --geometry src/dev/resources/afl_mesh_core/fixture.geo.json --output src/dev/resources/afl_mesh_core/fixture.aflmesh.json --check
```

去掉 `--check` 才写出结果；`--mapping mapping.json` 的内容为 `{ "source_group_name": "runtime_bone_name" }`。未使用的 mapping、unknown parent、层级/pivot/rotation 不匹配直接报错。每个 face 支持 3–64 点的简单平面多边形；确定性 ear clipping 支持凹多边形，非平面容差为 `max(1e-5, extent*1e-5)` source units。排序由 bone name、part name、face key 确定。

缺 UV、坏索引、零面积、多个 texture、材质模式/动画 strip、Mesh 自身动画、影响几何的未知属性、weighted skinning、morph、subdivision、动态拓扑等均 fail-fast。source 元素缩放也不在 V1 authoring 合同内；运行时 bone scale 由已有动画姿态处理。动画资源仍由原 Gecko 管线提供，转换器不生成动画。

## Runtime 与薄适配

源码位于 `src/main/java/com/antaurora/apofirstlight/client/mesh/`：

- `AflMeshLoader`：纯解析/验证，一次性展开三角形 corners，烘焙 flat normals 和 local AABB。
- `AflMeshModel` / `AflMeshPart`：不可变 CPU 数据，不保存 live GeoBone 或 instance pose。
- `AflMeshCache`：client reload listener 的 prepare 阶段扫描 sidecar，并从同一 ResourceManager 读取对应 geometry 验证；apply 原子替换不可变 snapshot，递增 generation。无需依赖 Gecko cache 的 apply 顺序。坏 sidecar 单独记录错误并省略，其他资源继续；移除或损坏的 sidecar 不保留上代 Mesh。无 GPU 资源生命周期。
- `AflMeshRenderer`：每帧只根据当前 pose 提交已烘焙 corners；不解析 JSON、不三角化、不生成逐 face 对象。使用原 texture、VertexConsumer、RenderType、color、packedLight、overlay；现有 entity buffer 为 QUADS，所以每个三角形提交 `A,B,C,C`（第二个三角形退化）。未直接操作 OpenGL。

`NativeGunContextRenderer.renderCubesOfBone` 先沿用 Cube 绘制，再追加 Mesh。`NativeAnimatedWeaponRenderer` 和 `P901Renderer` 继承该薄适配；各自递归入口的临时弹匣替换、subtree 省略、shell 显隐仍控制是否进入 hook。Mesh 检查 own hidden；hidden child 遵循 Gecko 的原遍历。hook 也在 `reRender` 执行，避免使用会在 reRender 跳过的 layer callback。第三人称传入的是该路径真实的 frozen/static-idle bone 副本，未改变第三人称动画语义。没有 sidecar 时不提交 Mesh。

正式 `apocalypse_firstlight:12_gauge_round` 是首个普通 Item 的真实资产 opt-in。Forge 1.20.1 没有独立的普通 Item 客户端扩展注册事件，因此 `Afl12GaugeRoundClient` 在客户端 setup 时仅给该既有 Item 实例设置 Forge 的 `renderProperties` 扩展，未改变 Registry ID、Item 类或弹药逻辑。`Afl12GaugeRoundRenderer` 使用 Gecko 当前 baked geo 骨骼和阶段1现有 `AflMeshCache` / `AflMeshRenderer`，每次绘制取当前缓存快照，不持有跨 F3+T 的旧 Mesh/GeoBone；一个 256×256 atlas，4 个纯 Mesh part、672 triangles，不新增 Cube、动画或另一套 Mesh backend。正式 item JSON 为 `builtin/entity` 且无 elements，避免旧 Java cube 与 Mesh 双重绘制；手持、掉落等 `display` 变换保留，`display.gui` 另调为近直立居中（rotation `[20,-25,0]`、translation `[0,-2.5,0]`、scale `[0.4,0.4,0.4]`）。旧 JSON 在 `models/item/legacy/12_gauge_round_java.json`。实际 GUI/手持/掉落、热重载与 shader 画面尚待实机验证。

`MaintenanceGunRendering` 仍使用自己的静态 bind-pose 副本。Mesh 同步参与 draw 和 bounds；替换弹匣的 Mesh 与 Cube 同样省略。每个 part 的缓存 AABB 八角用同一 bone/pivot 矩阵变换，得到保守包围盒；纵向居中使用 Cube+Mesh 合并范围。Mesh resource generation 变化时清理 bounds/纵向中心缓存，避免仅 sidecar 改变而沿用旧边界。附件仍不参与重心重算；原静态副本的 subtree 省略规则保留。

**Anchor contract changed = NO**。right/left hand、muzzle、shell、sight、maintenance/attachment anchors 与 hotspot semantics 均未改；没有 triangle picking。CPU 数据、cache 与 backend 分离，未来可替换提交 backend；当前没有实现或承诺 VBO。

## 离线 fixture 与已完成检查

- Editable source：`src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel`。
- 固定测试输入：`src/dev/resources/afl_mesh_core/fixture.{geo.json,aflmesh.json,expected.json}`。
- 1 Cube + 3 Mesh（4 triangles）；Cube/Mesh 共用 root；child 有非零 pivot/rotation，Mesh 自身也有非零 origin/rotation；独立 visibility bone；16×16 单 atlas。保留离线坐标验证数据，不再提供游戏内动画查看物品。
此 fixture 只供 `tools/verify-afl-mesh.mjs` 离线验证坐标、loader 与 renderer；`afl_mesh_core_fixture` 游戏物品仍已移除。正式 `silverwood_12` 当前可编辑源为 `src/main/blockbench/silverwood_12_hybrid_claude_reload_presentation_v2.bbmodel`；其 46 part / 4088 triangle sidecar、115 Cube 的 Gecko 骨架和 1024×1024 atlas 与前版相同，七动画中的换弹与检视轨道已同步至新源。此前测试版的主要实机表现由用户验收；新换弹动画、正式 ID 图形客户端、shader 与资源热重载仍待最终验收。

```powershell
node tools/verify-afl-mesh.mjs
node tools/verify-afl-mesh.mjs --java-loader
node tools/verify-afl-mesh.mjs --java-renderer
```

第三个命令包含 loader 检查。Java 模式需要本机已缓存的本项目 Gson/Minecraft/GeckoLib/JOML 等依赖；JShell 直接执行实际源码，仅临时移除 package 声明。临时 harness 位于系统 temp，完成删除；不运行 Gradle、不生成项目 class 文件、不启动客户端。`--java-renderer` 使用真实 PoseStack/GeoBone/RenderUtils 和记录顶点的 VertexConsumer。

已完成：确定性输出、22 类 converter 错误、8 类实际 loader 错误、凹多边形三角化、flat normal/unit length、local bounds；非零 element/group 坐标相对独立 Blockbench THREE 参考的最大 double 误差约 `4.92e-11` 格。实际 Gecko traversal 和 renderer 也对同一参考验证 float 误差小于 `2e-6` 格；五组普通/镜像/非均匀缩放下 winding 与 normal 一致，UV/color/light/overlay 正确提交，hidden/null/零尺度不提交，变换后 bounds 包含 Mesh 顶点。无 sidecar 的 opt-in、frozen 遍历适配和 reload generation 失效连接另有静态检查。

此前的最小项目编译使用 `./gradlew.bat compileJava --offline`；该结果不等同于客户端渲染验收。游戏内测试仍需单独执行。

未完成的实机验收：Cube+Mesh 画面、第一人称/第三人称/维护台 framing、动画/hidden-child/reRender 与弹匣/shell 场景组合、实际热重载/坏资源回退、Embeddium/Oculus/shader 兼容。当前沿用原 buffer/state 以降低风险，不能据此宣称 shader 验收通过。
