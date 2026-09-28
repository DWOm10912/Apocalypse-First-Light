# AFL Transparent Hybrid Mesh Runtime V1

2026-09-27。状态：**PARTIAL**。混合材质提交、旧格式兼容与红点资源接入已完成；离线验证通过。Shader OFF 代码就绪，实际 GPU/视觉未验证。**当前 alpha 24/255 在 Oculus 默认透明 shader alpha test 下仍可能被丢弃**，不宣称 Shader ON 镜片/PBR 已通过。最终 compileJava 结果见本轮交付报告。

## 格式与作者入口

Sidecar 保留 `format_version: 2`，新增可选 part 字段 `render_layer: "cutout" | "translucent"`。没有该字段的 V1/unversioned/V2 资产全部默认 CUTOUT；不批量重导旧资产。新 loader 也接受 V1 的可选层字段。旧程序的严格 loader 不接受新增字段，因此新资产必须配套新 runtime；这是向后兼容，不是旧二进制的向前兼容。

`tools/export-afl-mesh.mjs --layers <json>` 接受 part-name → layer 映射，拒绝未知 part 和未知层值。Java 不判断资源名、bone 名或材质字符串来决定透明。`AflMeshPart.Layer` 与按 bone/layer 分区的不可变列表在 load-time 构建，无每帧 JSON/材质字符串解析。

红点映射 `tools/pistol-red-dot.layers.json` 只将 `optic_lens` 标为 translucent。正式导出使用：

```powershell
node tools/export-pistol-red-dot-mesh.mjs
node tools/export-pistol-red-dot-mesh.mjs --check
node tools/verify-transparent-mesh.mjs
```

该导出器读取保存的 `src/main/blockbench/pistol_red_dot_mesh.bbmodel`，从原 groups/outliner 提取 geo，保留 lens_center/lens_aperture，复制作者三张 atlas；**不运行几何生成器、不写源模型或源贴图**。本轮发现 `build-pistol-red-dot.mjs --check` 与保存源不同，故不使用生成器覆盖保存源。不要用它的 `--runtime` 代替正式透明导出流程。

## Render path

`NativeSightRendering` → `NativeMuzzleRendering.drawItem` → `AflHybridMeshRendering.renderAtCurrentPose`。Pistol sight 使用 `NativeSightItem(true)`，安装、维护台、野外检查与独立 builtin/entity 物品共用此入口。P9 sight_slot 保持原值，可能需要后续新模型构图校准；本轮禁止修改它。Blackridge 当前没有 sight_slot，**NOT YET WIRED**。

`AflHybridMeshRendering` 使用调用方的 MultiBufferSource 与当前 PoseStack，在同一同步调用中：

1. 遍历当前骨骼，仅提交 CUTOUT parts 到 `entityCutoutNoCull(texture)`。
2. 仅当存在 TRANSLUCENT parts 且不是已确认的 Oculus shadow pass，提交透明层到 `entityNoOutline(texture)`。
3. 对标准 BufferSource 定向 flush 当前 cutout type，再提交/flush 透明 type；不全局 endBatch。包装 buffer 延用标准 buffer 的排序/flush 机制，实际 Oculus 批处理顺序仍需实机验收。

第二层只遍历 bone/缓存 part 列表，不重复提交外壳 faces。不重新计算 animation time，始终使用相同当前 GeoBone pose/attachment anchor。`AflMeshRenderer` 增加 layer 参数，原 API 默认 CUTOUT；新调用者必须使用双层高层入口或显式提交两层，不能把低层单 VertexConsumer API 当成自动双 pass。原生枪本体当前没有透明 metadata，本轮未改枪本体 traversal；未来动态机器/枪本体接入时仍需在调用方使用正确双层提交。

`AflStaticMeshItemRenderer` 对 mixed 模型接入相同高层 helper，原无透明物品继续原 foil/单层路径。Mixed item 暂不额外叠加 enchantment glint；红点不使用 glint。Muzzle、magazine、dynamic ammo 的既有 CUTOUT 行为不变。

## 标准 RenderType 审计

依据本机 Forge 1.20.1 映射 jar 的 RenderType bytecode，以及 client-extra.jar 内原版 shader：

| 属性 | CUTOUT | TRANSLUCENT |
|---|---|---|
| RenderType | entityCutoutNoCull | entityNoOutline |
| 格式/拓扑 | NEW_ENTITY / QUADS | NEW_ENTITY / QUADS |
| Blend | 原行为 | TRANSLUCENT_TRANSPARENCY |
| Depth test | 原行为 | 默认 LEQUAL |
| Depth write | 原行为 | OFF（COLOR_WRITE） |
| Cull | 原有 no-cull | no-cull，仅透明层 |
| 原版 alpha discard | 0.1 | 无 |

普通 entityTranslucent 的原版片元 shader 也有 `color.a < 0.1` discard，且沿用默认 depth write；不能满足 alpha 24/255。选择标准 entityNoOutline，保留 lightmap/overlay 和低 alpha，不用自定义 GL、shader 或全亮镜片。镜片薄面无需增添反面 geometry。透明像素使用 Base Color 原始 RGB/alpha，Java 无硬编码 tint/alpha。

## Oculus / LabPBR 限制（必须实机复测）

审计本机 Oculus 6020952：`MixinGameRenderer.iris$overrideEntityTranslucentShader` 的注入目标明确包含 `getRendertypeEntityNoOutlineShader`。它按阶段选 HAND_CUTOUT_DIFFUSE / HAND_WATER_DIFFUSE、BE_TRANSLUCENT 或 ENTITIES_TRANSLUCENT。标准 texture ResourceLocation 没有改变；`IrisSamplers.addLevelSamplers` 仍通过 pipeline 注册 `normals` / `specular` samplers，使用现有 PBR holder companion 机制；不手动绑定 GPU texture。

但 ShaderKey 的 ENTITIES_TRANSLUCENT 与 HAND_WATER_DIFFUSE 默认 **ONE_TENTH_ALPHA**。因此 Shader OFF 的低 alpha 支持**不能等价于** Shader ON 通过；shader pack 可以覆盖，当前 pack 的 GPU 行为本轮未启动客户端验证。没有改 alpha 24，没有提高 texture alpha、没有 shader-specific hack 或 Oculus private patch。

`TRANSLUCENT_SPECULAR_S = LIMITED`、`TRANSLUCENT_NORMAL_N = LIMITED`：接口路径存在且 `_s/_n` 已复制，实际 GPU companion 绑定、高光、normal/tangent、TAA shimmer 和低 alpha 行为尚未验证。若默认阈值生效，镜片会先被丢弃，不能将不可见镜片当作 PBR PASS。需后续资产 alpha 决策或受支持配置验证，本轮不擅自改变预设。

## Shadow / sorting / performance

透明层用已有 `AflShaderCompat.activeShadowPass()` 在提交前跳过；只将既有查询公开，无更改 P0 Local Player Native Gun Shadow Skip。外壳阴影沿原逻辑。API 未确认/失败不会自行假定 shadow，这种环境不保证透明阴影跳过，应记录为兼容性限制。

只提供同资产 CUTOUT before TRANSLUCENT、固定 bone 顺序与标准 RenderType 排序。**不保证**跨资产/相交多层透明、玻璃内部液体、透明嵌套和体积排序；不做 per-triangle 自定义排序、折射、反射探针、VBO/GPU cache、世界管道或机器 renderer。

当前红点共 6 parts，784 triangle-equivalent、181 Quad、422 Triangle、估算 2412 次 vertex submission。镜片 20 Triangle / 80 vertices；5 个外壳部件 2332 vertices 只提交一次。源文件保存精度与生成器即时数据不同，所以不沿用旧生成器的 308 Quad 数字。新增开销仅是透明 buffer/pass 与第二次 bone traversal；未测 CPU/FPS。

## 验证

- `verify-transparent-mesh.mjs`：saved source 与 sidecar geometry/UV 一致、只新增 metadata；三张 runtime PNG 与 source PNG 字节一致；逐 lens face UV 中心 alpha 24；locator 保留、无 reticle geometry、builtin/entity 入口，PASS。
- `verify-afl-mesh.mjs --java-renderer`：真实 Java V1/unversioned/V2 loader、全部生产 sidecar、非法 metadata 拒绝、缓存 layer 分区；真实 renderer 单层排除/无重复提交、当前 bone transform/UV/normal/attributes 完全一致，PASS。保留原 V2/V2.1 检查。
- 最后只运行一次 `gradlew.bat compileJava --offline`，结果见交付；不运行 processResources/build/runClient/GameTest。
- 用户矩阵：P9 安装红点，Shader OFF → Sundial Lite → Complementary；检查外壳不透明、淡色透明镜片、镜框遮挡、无黑片/z-fighting/额外阴影；野外检查旋转看斜角，维护台/独立物品确认跟随。Shader ON 须特别记录低 alpha 是否被阈值裁掉及 PBR 响应。
- 新资产不含实体 reticle，本轮也不实现准直投影，故当前新红点**没有瞄准点**。正式 reticle 与新 sight_slot 校准是后续独立任务。
