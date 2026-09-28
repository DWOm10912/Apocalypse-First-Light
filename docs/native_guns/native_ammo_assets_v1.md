# Native 正式弹药资产 V1

## 当前实现

| ID（均为 apocalypse_firstlight 命名空间） | 中文 | 英文 | 用途 |
| --- | --- | --- | --- |
| 9x19mm_round | 9×19毫米手枪弹 | 9×19mm Pistol Round | P9-01 |
| 762x51mm_round | 7.62×51毫米步枪弹 | 7.62×51mm Rifle Round | BR51-01 |
| 12_7x55mm_round | 12.7×55毫米重型弹 | 12.7×55mm Heavy Round | HR55 |
| 12_gauge_round | 12号霰弹 | 12 Gauge Shotgun Round | Silverwood 12；每次射击消耗1发，独立追踪8颗弹丸 |
| 50_ae_round | .50 AE 手枪弹 | .50 AE Round | Blackridge .50 唯一可用弹药；64 堆叠普通 Item，客户端使用静态 Hybrid Mesh |
| 9x19mm_casing | 9×19毫米弹壳 | 9×19mm Casing | P9-01 弹壳类型及普通物品；飞行抛壳使用低模 `9x19mm_casing_fx`（见 Ejected Casing Low-Poly FX V1） |
| 762x51mm_casing | 7.62×51毫米弹壳 | 7.62×51mm Casing | BR51-01 抛壳及普通物品 |
| 12_7x55mm_casing | 12.7×55毫米弹壳 | 12.7×55mm Casing | HR55 抛壳及普通物品 |
| 12_gauge_casing | 12号霰弹空壳 | 12 Gauge Casing | 已注册普通物品；Silverwood V1 无射击时自动抛壳 FX/地面掉落 |
| 50_ae_casing | .50 AE 弹壳 | .50 AE Casing | 已注册的 3D 普通 Item；Blackridge 引用为弹壳类型；飞行抛壳使用低模 `50_ae_casing_fx`，待实机验收 |

十项均64堆叠。原四种实弹和新 `.50 AE` 实弹进入 AFL 武器与弹药标签；仅新 `.50 AE` 空壳也进入该标签，原四种空壳仍不展示。数字开头是合法 ResourceLocation 路径，无需前缀。弹壳不作为弹药，也没有回收配方；当前枪械射击不会生成可拾取弹壳实体。

## 源资产与美术

已核对 `src/main/blockbench/br51_01.bbmodel` 的 `magazine_bullet/bullet/2/3` 和 `7`：各12 cubes，是错位排列的两颗完整弹药，不是两半。选择组3（UUID `335a03a2-aae3-f396-93b6-9a21cf307383`），移除弹匣父级放置旋转，统一放大4倍并居中；保留12个原方块、局部22.5°肩部旋转和原UV关系。补一个简化底火，未改枪械源文件。

7.62整弹为13 cubes；弹壳移除铜色弹头段，保留壳体、肩部、底缘与抽壳槽，补暗色凹口和底火，共9 cubes。独立256×256贴图是原BR51 atlas的逐字节副本，保留材质一致性，不改枪纹理。

9mm 的旧 Cube vFinal 资产（`src/main/blockbench/9x19mm_round.bbmodel` / `9x19mm_casing.bbmodel`，整弹 29 cubes、弹壳 26 cubes，共用 64×64 黄铜/铜材质）已于 2026-09-27 退出正式运行时，由下文「9×19mm Visible Ammo V1」的 Pure Mesh 资产替换。旧物品 JSON 原样保留在 `models/item/legacy/9x19mm_round_java.json` / `9x19mm_casing_java.json`，旧源文件和 `textures/item/9x19mm_round.png` / `9x19mm_casing.png` 也保留，便于回滚。

12.7×55mm资源复用同一普通 Item 注册路径：`src/main/blockbench/ammo_127x55_cube_v1/12_7x55mm_round.bbmodel` 为35 cubes，`12_7x55mm_casing.bbmodel` 为29 cubes；运行模型与贴图位于 `assets/apocalypse_firstlight/models/item/` 和 `textures/item/`，使用64×64黄铜/钢色贴图。该口径供 HR55 使用；抛壳使用同名的客户端模型预注册路径，不新增 Ammo 系统或属性。

12 Gauge 完整弹现以 `src/main/blockbench/12ga_hybrid_mesh_prototype.bbmodel` 为可编辑 Mesh 源，4 个部件、672 个三角面，共用原 256×256 贴图。正式 ID 仍为 `12_gauge_round`，仍是64堆叠普通 `Item`；仅客户端视觉入口改为 `models/item/12_gauge_round.json` 的 `builtin/entity`。GUI 的 `display.gui` 为近直立摆放（rotation `[20,-25,0]`、translation `[0,-2.5,0]`），scale `[0.48,0.48,0.48]`；掉落物 `display.ground` 使用零旋转、translation `[0,1.25,0]`、scale `[0.2,0.2,0.2]`。其他两种整弹沿用此掉落旋转与 JSON 垂直位移，但需要下文所述的 GROUND 专用渲染偏移，才能抵消 Minecraft 按掉落缩放添加的悬浮高度。其他视角沿用原 `display` 变换。`AflStaticMeshItemClient` 将现有 Item 的 Forge 客户端扩展绑定到 `AflStaticMeshItemRenderer`；后者从 `geo/12_gauge_round.geo.json`、`meshes/12_gauge_round.aflmesh.json` 和 `textures/item/12_gauge_round_mesh.png` 读取骨骼、阶段1 Mesh sidecar 和原样贴图，使用现有 `AflMeshCache` / `AflMeshRenderer` 提交。旧110 cube 的 Java 模型原样留在 `models/item/legacy/12_gauge_round_java.json`，旧可编辑源 `src/main/blockbench/12g_round.bbmodel` 和 `textures/item/12g_round.png` 也保留，便于回滚。

`12_gauge_casing` 仍使用原 `12g_casing.bbmodel` 对应的 Java item JSON 和64×64贴图；新的独立 spent Mesh 源暂不接入正式空壳。Silverwood 内部 live/spent shell 组、动画和玩法数据未变。GUI、手持、掉落、F3+T、Embeddium/Oculus 与法线/UV 视觉均待用户实机验收；离线导出与编译不等于这些场景通过。

`.50 AE` 标准源为 `src/main/blockbench/50ae_round.bbmodel` 和 `50ae_casing.bbmodel`。2026-09-27 起改为 V2 软尖弹（JSP），由 `node tools/build-50ae-ammo.mjs` 确定性生成（`--check` 校验），与 9mm 共用生成库 `tools/lathe-mesh-lib.mjs`（2026-09-27 由 `lathe-ammo-lib.mjs` 改名，现也生成通用 9mm 消音器；弹药输出逐字节不变）。原先从 Blender 导入的空尖弹版本（`live_round` 512 三角面、`spent_case` 448 三角面）在 git 历史中可取回。

- **为什么换**：旧版弹头是铜色空尖，侧面看起来像放大的 9mm。新版参照软尖弹外观：被甲只包住弹头下段，上面露出一大块深灰色铅头，顶端是宽平面，和 9mm 全铜圆头一眼可分。
- **整弹**：骨骼 `round`，共 1200 三角面。
  - `round_casing`（480）：底面倒角、缩径底缘、抽壳槽、微锥壳体、锥形收口。
  - `round_bullet`（580）：一段承力带，被甲弧段到 y 2.03，带一圈亮色被甲口；铅头圆顶收到半径 0.20 的平顶。
  - `round_primer`（140）：大号手枪底火，半径 0.143。
- **空壳**：骨骼 `casing`，共 1080 三角面。`casing_body`（820）壳体胀到膛室尺寸，壳口带卷边，内壁有烟熏，壳底有传火孔；`casing_primer`（260）有击针凹坑。
- **不变的部分**：
  - 圆周 20 段，比例沿用旧资产（1 单位约 18.58 mm）；
  - 壳体尺寸沿用旧资产：缩径底缘半径 0.3488、壳底 0.3732、壳口 0.3634、弹头 0.3418、壳长 1.763；
  - 总高与旧资产完全相同：整弹 2.201056、空壳 1.773824。原有中心补偿 `0.431217` / `0.444568`（等于 0.5 − 总高/32）仍用于非 GROUND 视角；整弹掉落物另有下文所述的 GROUND 专用偏移。Blackridge 的 `magazine_round_anchor` 和 `NativeGunFx` 不变。
- **贴图**：两件仍共用 `textures/item/blackridge_50ae_ammo_v1.png`（源文件在 `src/main/blockbench/textures/`，内容已整体重画）。
  - 512×512，只有 Base Color，约 106 px/单位；两件的逻辑 UV 尺寸都是 512（弹壳原来是 16），弹壳 geo 的 `texture_width` / `texture_height` 同步改为 512。
  - 颜色：黄铜 188,152,84（比 9mm 略亮略黄），被甲铜 190,128,78，铅 78,80,86（平顶略亮，带极淡的低频斑驳），镍底火。
  - 分区方式、去锯齿的做法与 9mm 相同。
- **显示与游戏内**：
  - 整弹 GUI `display.gui` 与 12 Gauge 使用相同的近直立旋转 `[20,-25,0]`，translation `[0,0,0]`，scale `[3.8,3.8,3.8]`。掉落物 `display.ground` 为零旋转、translation `[0,1.25,0]`、scale `[2,2,2]`。由于 Minecraft 的 `ItemEntityRenderer` 另外按 `0.25 × ground.scale` 抬高掉落物，整弹在 `AflStaticMeshItemRenderer` 的 GROUND 专用中心偏移为 `0.257`（其他视角仍为 `0.431217`），按 12 Gauge 的较低基准对齐。手持和展示未改。
  - Blackridge 已通过 `native_guns/blackridge_50.json` 绑定该弹药，容量 7；其射击消耗沿用现有 Native Gun 流程。
  - 新版已在 Blockbench 里按 Blackridge 弹匣锚点放入弹匣检查过，没有穿插。显示姿态和新外观都没有进游戏验证。

其他既有弹药继续使用普通 Java 三维 item 模型。12 Gauge 完整弹与两件 .50 AE 是纯 Mesh 静态 geo 的例外，均为普通 Item 而非枪械 renderer；具体游戏内可读性待目测，不把离线检查当成实机通过。

### 9×19mm Visible Ammo V1：纯 Mesh 整弹 + 空壳（2026-09-27，已替换为正式运行时资产，待实机验收）

按 `.50 AE` 标准资产的约定新建的 9mm 可视弹药，供 P9-01 的弹匣顶弹、退匣展示、空仓旧匣和后续 Mesh 抛壳使用。

**运行时接入（2026-09-27）**
- 两个正式 Registry ID `9x19mm_round` / `9x19mm_casing` 不变，没有新增 Item。它们与 `.50 AE` 两件走同一条 Static Hybrid Mesh 路线：Pure Mesh 源 → `.aflmesh` sidecar + geo → `AflMeshCache` / `AflMeshRenderer`。
- **物品显示**：
  - `models/item/9x19mm_round.json` / `9x19mm_casing.json` 改为 `builtin/entity`（particle 用 `item/9x19mm_ammo_v1`）；
  - `AflStaticMeshItemClient` 用现有的参数化 `AflStaticMeshItemRenderer` 绑定这两个物品，没有新增 renderer；
  - 中心补偿等于 0.5 − 模型总高/32：整弹 `0.451463`，空壳 `0.468168`；
  - 整弹 GUI `display.gui` 与 12 Gauge 使用相同的近直立旋转 `[20,-25,0]`，translation `[0,0,0]`，scale `[4,4,4]`（Forge 1.20.1 的物品模型 JSON 缩放上限）。掉落物 `display.ground` 为零旋转、translation `[0,1.25,0]`、scale `[2.4,2.4,2.4]`；为抵消 `ItemEntityRenderer` 按此较大 scale 添加的悬浮高度，整弹在 `AflStaticMeshItemRenderer` 的 GROUND 专用中心偏移为 `0.2558333333`（其他视角仍为 `0.451463`），按 12 Gauge 的较低基准对齐。第一人称 2.64、第三人称 2.16、展示框 2.4 均未改。空壳 GUI 4.08、第一人称 3、第三人称 2.4、掉落和展示框 2.64 及原有旋转均未改。
- **抛壳**：`NativeGunFx` 原来只为 `.50 AE` 弹壳写死了 Mesh 分支，现在改成按弹壳模型查表（`MESH_CASINGS`：geo、贴图、世界缩放）。
  - `item/50_ae_casing` 缩放 0.65 不变；
  - `item/9x19mm_casing`（即 `NativeGunFx.CASING_MODEL`）缩放 0.62，与旧 Cube 弹壳抛出时的大小一致；
  - 居中改为取弹壳所有部件包围盒的并集；
  - 其他口径仍用烘焙四边面。抛壳物理、寿命、上限都没有改。
  - 2026-09-27 起这两个口径的飞行抛壳改用低模 FX 资产，高精度弹壳只作回退，见下文 Ejected Casing Low-Poly FX V1。
- **没有改动**：伤害、弹量、射速、配方、战利品、弹壳物理、Dynamic Ammo 判断逻辑。

**生成与校验**
- 全部由 `node tools/build-9x19mm-ammo.mjs` 确定性生成（共用 `tools/lathe-mesh-lib.mjs`，原名 `lathe-ammo-lib.mjs`）；加 `--check` 逐字节校验所有输出。在 Blockbench 里保存过的源文件，与生成结果只差 3×10⁻⁷ 以内的浮点舍入，已用生成器重新导出，使源文件与运行时资源重新一致。
- 两个 sidecar 由 `tools/export-afl-mesh.mjs` 的 `convert` / `serialize` 生成，AFL 转换器接受全部面。

**文件**

| 用途 | 路径 |
|---|---|
| 整弹源（Free Model，骨骼 `round`） | `src/main/blockbench/9x19mm_round_mesh.bbmodel` |
| 空壳源（Free Model，骨骼 `casing`） | `src/main/blockbench/9x19mm_casing_mesh.bbmodel` |
| 共用 Base Color 源贴图（512×512，已内嵌进两个源） | `src/main/blockbench/textures/9x19mm_ammo_v1.png` |
| 运行时贴图（与源贴图逐字节相同） | `assets/apocalypse_firstlight/textures/item/9x19mm_ammo_v1.png` |
| Geo（单骨骼，pivot 0，texture 512） | `geo/9x19mm_round.geo.json`、`geo/9x19mm_casing.geo.json` |
| AFL Mesh sidecar | `meshes/9x19mm_round.aflmesh.json`、`meshes/9x19mm_casing.aflmesh.json` |

**资产合同**
- 与 `.50 AE` 相同：弹轴 +Y，弹壳底面在 y = 0，X/Z 居中，单骨骼 pivot `[0,0,0]`。
- 比例跟 P9-01 模型一致：1 个 Blockbench 单位约 18.8 mm。壳体半径 0.264，等于 P9 膛室半径 0.265。`.50 AE` 约为 18.6 mm/单位。
- 圆周分段为 20（`.50 AE` V2 也是 20），近景检视时轮廓更圆。

**尺寸（C.I.P. 9×19 mm Parabellum）**
- 底缘 Ø9.96、壳底 Ø9.93、壳口 Ø9.65（锥形收口到 Ø9.53）、弹头 Ø9.02。
- 壳长 19.15、底缘厚 1.27、抽壳槽 Ø8.0。
- 全长取常见工厂弹 29.2 mm（1.553 单位）。C.I.P. 上限 29.69 mm 会穿出 P9 弹匣前端的供弹唇。

**整弹**：三个部件，共 1000 三角面。
- `round_casing`（480）：
  - 底面倒角、底缘；
  - 抽壳槽及其上方斜坡；
  - 微锥壳体；
  - 锥形收口和壳口边沿；
  - 底火座（带一圈可见的装配缝）。
- `round_bullet`（380）：铜被甲弹头，壳口上方一段圆柱承力带，接超椭圆圆头（指数 1.9，钝圆弹尖）。
- `round_primer`（140）：镍色底火，略低于底面。

**空壳**：两个部件，共 1080 三角面。
- `casing_body`（820）：
  - 与整弹相同的底部结构；
  - 壳体胀到膛室尺寸，壳口微张、带卷边；
  - 内壁有烟熏，一直深到壳底（y 0.28），底部有传火孔。
- `casing_primer`（260）：底火中心有击针凹坑。

**贴图**：只有 Base Color，没有 PBR，没有底面刻字和品牌标记。
- 与 `.50 AE` 同一色系：黄铜 182,144,78，铜被甲 180,102,66，镍底火 168,164,154。`.50 AE` V2 的黄铜略亮，靠深色铅头和口径大小区分。
- 按区域做平滑明暗：
  - 壳体中段较亮，靠底部和壳口略暗；
  - 抽壳槽 ×0.64，壳口边沿 ×1.08；
  - 弹头在壳口上方有一道收口阴影线，圆头中段稍亮；
  - 空壳壳口外侧有轻微火药烟熏，内壁逐渐加深；
  - 底面有极淡的车削圈。
- 侧面按圆周展开成横条，所有分区边界都沿贴图像素轴向，不会出现阶梯锯齿。
- 除约 ±0.3% 的拉制细纹外不加噪点。
- 贴图密度约 149 px/单位。

**P9-01 运行时接入状态（2026-09-27；`compileJava --offline` 成功但为 `UP-TO-DATE`，待客户端修正后验收）**
1. **弹匣顶弹**：`presentation.magazine_round_visual` 已指向 `apocalypse_firstlight:geo/9x19mm_round.geo.json` 和 `apocalypse_firstlight:textures/item/9x19mm_ammo_v1.png`，复用通用 Native Magazine Round 渲染。枪内、`mag_out`、`empty_old_mag` 三处分别使用现成锚点。三个锚点在源文件中仍是顶弹中心、rotation `[-22,0,0]`；未改 Rig。Geo 的 `+22°` 在 GeckoLib 烘焙时取反，故通用配置应为 `local_offset=[0,-0.000036,0.789958]` 与 `local_rotation=[-90,0,0]`，有效姿态为弹底 pivot `[0,3.6897,2.5897]`、rotation `[-112,0,0]`，对应弹匣系 z 2.555。先前按 Geo JSON 正号直接计算的偏移/旋转会造成弹头反向和穿模，已废弃。最终遮挡仍需游戏内近景确认。
2. **Mesh 抛壳与物品显示**：原有接入保留（见上文「运行时接入」），本次未修改。
3. **膛内弹**：仍未实现。P9 膛室深约 0.85 单位，弹壳长 1.019；后续若显示膛内弹，需要单独处理。

**验证**
- Blockbench 离屏渲染：
  - 散放的整弹和空壳；
  - 底面和底火；
  - 空壳口内部；
  - 整弹按上面的建议锚点放进 P9 弹匣，从顶部和前方看，确认不穿插。
- `--check` 通过。
- `.\gradlew.bat compileJava --offline` 通过。
- 未进游戏：GUI、手持、掉落、展示框和抛壳的画面都待实机验收。
- `tools/verify-native-ammo.mjs` 已能识别 Mesh 物品：检查 geo、sidecar 和图集，方块检查改用 `legacy/` 里保留的 JSON。脚本仍 在 `762x51mm_round` 处失败：HEAD 中物品模型为 44 个元素、源文件为 13 个。这是既有问题，与本轮无关。

### Ejected Casing Low-Poly FX V1（2026-09-27，待实机验收）

**规则（今后所有会抛壳的 Pure Mesh 弹药默认遵守）**

- **HIGH DETAIL CASING**：正式高精度空壳，例如 `9x19mm_casing`、`50_ae_casing`。只用于物品（背包、手持、掉落、展示框）和静态展示（检视、维护台）；弹匣顶弹用的是配套的高精度整弹。不再直接拿去做飞行抛壳。
- **LOW POLY CASING FX**：`<空壳 geo 名>_fx`，只用于 `NativeGunFx` 的飞行抛壳，第一和第三人称共用同一资产，不注册 Item。
- 以后新增会抛壳的 Pure Mesh 弹药，必须同时提供这两套资产。FX 版的约定：
  - 与高精度空壳外形尺寸、骨骼 `casing`、+Y 轴、弹底 y = 0 一致；
  - 圆周约 8 段、约 80–160 三角面；
  - 贴图 64 或 128；
  - AFL Mesh V2，侧面和平面环带保留为 Quad。

**审计（按实际资源与代码，不按文档推断）**

| 资产 | 形式 | 用途 |
|---|---|---|
| `9x19mm_round`、`50_ae_round`、`12_gauge_round` | Mesh 整弹 | 物品；前两者另作弹匣顶弹。整弹不参与抛壳 |
| `9x19mm_casing`、`50_ae_casing` | Mesh 空壳（V1 sidecar，1080 三角面） | 物品展示；此前也被 `NativeGunFx.MESH_CASINGS` 直接用于抛壳，本轮改为 FX 资产 |
| `762x51mm_casing`、`12_7x55mm_casing`、`12_gauge_casing` | Cube 物品模型 | BR51 / CAT / HR55 用烘焙四边面抛壳；Silverwood V1 不抛壳。不是 Mesh，本轮不适用 |
| `src/main/blockbench/12ga_hybrid_mesh_spent.bbmodel` | 仅源原型 | 没有运行时 geo 或 sidecar，不参与抛壳 |

**FX 资产**

| | 9mm | .50 AE |
|---|---|---|
| Geo / Sidecar（V2） | `geo/9x19mm_casing_fx.geo.json`、`meshes/9x19mm_casing_fx.aflmesh.json` | `geo/50_ae_casing_fx.geo.json`、`meshes/50_ae_casing_fx.aflmesh.json` |
| 运行时贴图（64×64，Base Color + `_s` + `_n`） | `textures/item/9x19mm_casing_fx.png` | `textures/item/50_ae_casing_fx.png` |
| 源 | `src/main/blockbench/9x19mm_casing_fx.bbmodel`、`textures/9x19mm_casing_fx*.png` | `src/main/blockbench/50ae_casing_fx.bbmodel`、`textures/50_ae_casing_fx*.png` |
| 生成 | `node tools/build-9x19mm-ammo.mjs`（同一脚本里的第二次 `runLathe`） | `node tools/build-50ae-ammo.mjs` |

**几何**

- 8 段，共用各自高精度生成器的尺寸常量 `D`。
- 轮廓依次为：
  - 底火面；弹底环面；底缘柱面；
  - V 形抽壳槽：下斜面，再加上斜面回到壳体；
  - 带锥度的壳体：9mm 半径 0.2641 → 0.2606，.50 AE 0.3732 → 0.3700；.50 AE 保留缩底缘（rebated rim）轮廓；
  - 壳口端环：壁厚 9mm 0.016、.50 AE 0.020。比高精度稍厚，一方面让飞行中更易辨认，另一方面太窄的环在 float32 精度下过不了 V2 的平面检查；
  - 壳口内浅暗碟：深 9mm 0.10、.50 AE 0.14。壳口不封死，但不做内壁。
- 去掉的细节：底火窝、底缘小倒角、壳口内壁与底部、底火凹痕几何，以及所有细 bevel。凹痕只保留为底火中心的暗色渐变。
- 尺寸与居中：高度与正式弹壳完全一致（9mm 1.01862，.50 AE 1.773824），最大半径差在 0.6% 以内。`NativeGunFx` 按包围盒并集居中后，翻滚中心不变，缩放 0.62 / 0.65 沿用。
- UV：`tools/lathe-mesh-lib.mjs` 新增 `facets` 布局，每个棱面按自身平面上的等腰梯形放进自己的列，UV 映射是仿射的。配合 8 位小数，V2 保留了全部 48 个 Quad，只有两处极点扇面是三角形。

**面数与每个弹壳的提交顶点**（每个面按 4 个顶点提交）

| 弹壳 | 高精度三角面 | 高精度提交顶点 | FX 三角面 | FX 提交顶点 | 降幅 |
|---|---|---|---|---|---|
| 9mm | 1080（V1，全三角形） | 4320 | 112（48 Quad + 16 三角形） | 256 | 三角面 −89.6%，顶点 −94.1% |
| .50 AE | 1080（V1，全三角形） | 4320 | 112（48 Quad + 16 三角形） | 256 | 三角面 −89.6%，顶点 −94.1% |

**材质**

- Base Color 沿用各自正式弹壳的黄铜、镍色板和分区倍率，去掉车削细纹、逐行拉丝这类高频项，壳口附近保留轻微火药烟熏。
- `_s`：黄铜是金属（F0 = 255），平滑度 100–135，中等不镜面；底火镍 140；壳口暗碟是介电（F0 10），平滑度 50。
- `_n`：全平面法线（128,128），AO 只加在抽壳槽（215）和壳口暗碟（150 → 110）。
- 没有针对任何光影包的特殊处理。

**运行时**

- `NativeGunFx.MESH_CASINGS` 每项增加 `fxGeometry` / `fxTexture`。飞行抛壳优先画 FX 资产，只有 FX 的 geo 或 sidecar 缺失时才回退到正式高精度弹壳。
- 物品（`AflStaticMeshItemClient`）、掉落、弹匣顶弹（`magazine_round_visual`）和展示仍使用高精度资产。
- 抛壳速度、重力、旋转、寿命（50 tick）、上限（64 个）和落地声都没有改。

**验证**

- 两个弹药生成器的 `--check` 通过，高精度输出逐字节不变。
- `node tools/verify-afl-mesh.mjs --java-loader` 通过：真实 Java 加载器在 JShell 中加载了全部生产 sidecar，包括两件 FX。
- `compileJava --offline` 通过。
- 未进游戏：单发、快速连射、Blackridge 开火、光影开关下的外观和帧时间都待实机验收。

## 文件与旧新映射

上述十个 ID 均有 item 模型与贴图资源；12 Gauge 完整弹和 .50 AE 两件另有 Mesh sidecar/geo，.50 AE 共用正式 Base Color atlas。源文件与贴图命名如下：

- `src/main/blockbench/<ID>.bbmodel`（12 Gauge 完整弹当前源为 `12ga_hybrid_mesh_prototype.bbmodel`，旧源 `12g_round.bbmodel` 为回退；空壳仍为 `12g_casing.bbmodel`）
- `src/main/resources/assets/apocalypse_firstlight/models/item/<ID>.json`
- `src/main/resources/assets/apocalypse_firstlight/textures/item/<ID>.png`（12 Gauge 完整弹当前使用 `12_gauge_round_mesh.png`，旧贴图为 `12g_round.png`；空壳仍使用 `12g_casing.png`）
- `.50 AE` 两件源文件分别为 `50ae_round.bbmodel` / `50ae_casing.bbmodel`，共用运行贴图 `textures/item/blackridge_50ae_ammo_v1.png`，并分别有 `geo/50_ae_round.geo.json` / `50_ae_casing.geo.json` 与同名 `meshes/*.aflmesh.json`。`NativeGunFx` 对 .50 AE 弹壳使用现有 Mesh 缓存、GeckoLib 骨骼与共用 atlas 绘制，旧口径继续使用烘焙四边面；物品本身的 3D 渲染已配置。开火抛壳与物品显示均待实机验收。

旧 `9mm_round` 注册通过 `registry/NativeWeaponLegacyMappings.java` 的 MissingMappings 转为 `9x19mm_round`，不把旧手枪弹转换为步枪弹；旧存档加载兼容尚待实机确认。旧 `9mm_round` / `9mm_casing` 源模型、item和 `9mm_palette.png` 留作历史对照，不再是正式引用。旧导出器 `tools/export-9mm-assets.mjs` 不用于当前资产。

既有 9mm / 7.62 工作流：`node tools/export-native-ammo.mjs`；引用检查：`node tools/verify-native-ammo.mjs`。12 Gauge 完整弹的 Mesh sidecar 由 `node tools/export-afl-mesh.mjs` 从对应 Blockbench 源和 geo 确定性导出；.50 AE 与新 9mm 两套的源文件、贴图、geo 和 sidecar 分别由 `node tools/build-50ae-ammo.mjs`、`node tools/build-9x19mm-ammo.mjs` 一次生成（内部同样调用该转换器）；旧 12 Gauge Java JSON / PNG 原样保留作为回退。离线几何预览：`tools/preview-native-ammo.py` → `build/native-ammo-preview.png`，仅用于既有资产检查。

接入修改：`AflItems.java`、`AflCreativeTabs.java`、`NativeWeaponLegacyMappings.java`、`NativeGunDefinition.java`、`ConfiguredNativeGunItem.java`、中英文lang；客户端 `NativeGunFx.java`、`NativeGunFxModels.java`、`NativeAnimatedWeaponRenderer.java`；回归 `BR5101CombatGameTests.java`。

## 行为边界

ammo、magazine_capacity和casing引用现由单枪 `data/apocalypse_firstlight/native_guns/*.json` 控制，/reload同步两端；重载取消未完成枪械操作。弹药资源本身和创造模式规则不变。

创造模式：所有 Native 枪械通过公共 `NativeGunAmmo` 将备弹视为无限，HUD 只在备弹栏显示 `∞`。无需携带对应弹药，换弹结算填至正常容量且不扣背包弹药；当前弹匣仍逐发扣减，空仓、干击与原换弹时序不变。生存/冒险仍按实体弹药统计与扣除。换弹结算时重新检查当前游戏模式，切换模式不会保留无限备弹缓存。

本规则验证：`creativeReserveAndModeSwitch` 覆盖两把枪的无弹药创造补弹、有限弹匣扣弹、空仓、实体弹药保留，以及切换生存/冒险后的有限补弹消耗；现有射击/换弹测试通过。共63项GameTest中62项通过，唯一失败仍为无关的 `nativenoiseflatdistances / Hearing boundary 20`（`creative-reserve-verification.log`）。独立 `compileJava processResources build --offline --stacktrace` 通过（`creative-reserve-build.log`）。本轮未进行图形客户端HUD验收。

P9-01只吃9x19mm_round，BR51-01只吃762x51mm_round，HR55使用12_7x55mm_round，Silverwood 12 使用12_gauge_round。背包HUD统计沿用同一definition口径查询；Silverwood 显示的是 2/1/0 发霰弹而非弹丸数。已装枪内弹数NBT不重置。既有枪械换弹时间、扣弹次数、dry-fire、伤害、精度、枪械模型、动画、Display均不变。

两把枪均已切换对应弹壳；Casing实例保存诞生时的模型，不会因切枪改变。沿用 .072 FX比例、局部抛出方向、重力、阻力、翻滚、碰撞、弹跳、音效、寿命和上限。枪焰不变。未来新枪默认9mm，若有新口径需显式增加资源选择，当前仅两种正式枪。

## 验证

- `compileJava processResources build --offline --stacktrace` 通过（36秒；首次测试API调用错误已修正）。
- GameTest：61项中60项通过，枪械/弹药测试通过，包括BR51拒收9mm、7.62普通/空仓结算、取消/不足补弹、射击/干击。整套未通过：`nativenoiseflatdistances` / `Hearing boundary 20`；不在本轮修改范围，未据此宣称全套通过。日志 `native-ammo-gametest.log`。
- 离线几何/贴图预览已查看；游戏内GUI、手持、掉落、抛壳美术与声音尚未完成验收。
- 12.7×55mm 已绑定 HR55；未完成项是 HR55 的实机视觉验收和旧存档迁移验证，不是正式 Ammo 系统接入。

测试命令（对应八项物品；本轮未在游戏内执行）：

```text
/give @s apocalypse_firstlight:9x19mm_round 64
/give @s apocalypse_firstlight:762x51mm_round 64
/give @s apocalypse_firstlight:9x19mm_casing 1
/give @s apocalypse_firstlight:762x51mm_casing 1
/give @s apocalypse_firstlight:12_7x55mm_round 64
/give @s apocalypse_firstlight:12_7x55mm_casing 1
/give @s apocalypse_firstlight:12_gauge_round 64
/give @s apocalypse_firstlight:12_gauge_casing 1
/give @s apocalypse_firstlight:50_ae_round 64
/give @s apocalypse_firstlight:50_ae_casing 1
```

尚不能标记 VISUAL_VALIDATION_DONE=YES。未提交、未推送。
