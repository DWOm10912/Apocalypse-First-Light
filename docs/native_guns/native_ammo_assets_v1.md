# Native 正式弹药资产 V1

## 当前实现

| ID（均为 apocalypse_firstlight 命名空间） | 中文 | 英文 | 用途 |
| --- | --- | --- | --- |
| 9x19mm_round | 9×19毫米手枪弹 | 9×19mm Pistol Round | P9-01 |
| 762x51mm_round | 7.62×51毫米步枪弹 | 7.62×51mm Rifle Round | BR51-01 |
| 12_7x55mm_round | 12.7×55毫米重型弹 | 12.7×55mm Heavy Round | HR55 |
| 12_gauge_round | 12号霰弹 | 12 Gauge Shotgun Round | Silverwood 12；每次射击消耗1发，独立追踪8颗弹丸 |
| 50_ae_round | .50 AE 手枪弹 | .50 AE Round | Blackridge .50 唯一可用弹药；64 堆叠普通 Item，客户端使用静态 Hybrid Mesh |
| 9x19mm_casing | 9×19毫米弹壳 | 9×19mm Casing | P9-01 抛壳及普通物品 |
| 762x51mm_casing | 7.62×51毫米弹壳 | 7.62×51mm Casing | BR51-01 抛壳及普通物品 |
| 12_7x55mm_casing | 12.7×55毫米弹壳 | 12.7×55mm Casing | HR55 抛壳及普通物品 |
| 12_gauge_casing | 12号霰弹空壳 | 12 Gauge Casing | 已注册普通物品；Silverwood V1 无射击时自动抛壳 FX/地面掉落 |
| 50_ae_casing | .50 AE 弹壳 | .50 AE Casing | 已注册的 3D 普通 Item；Blackridge 引用为弹壳类型；瞬时抛壳已接入现有 Mesh 渲染分支，待实机验收 |

十项均64堆叠。原四种实弹和新 `.50 AE` 实弹进入 AFL 武器与弹药标签；仅新 `.50 AE` 空壳也进入该标签，原四种空壳仍不展示。数字开头是合法 ResourceLocation 路径，无需前缀。弹壳不作为弹药，也没有回收配方；当前枪械射击不会生成可拾取弹壳实体。

## 源资产与美术

已核对 `src/main/blockbench/br51_01.bbmodel` 的 `magazine_bullet/bullet/2/3` 和 `7`：各12 cubes，是错位排列的两颗完整弹药，不是两半。选择组3（UUID `335a03a2-aae3-f396-93b6-9a21cf307383`），移除弹匣父级放置旋转，统一放大4倍并居中；保留12个原方块、局部22.5°肩部旋转和原UV关系。补一个简化底火，未改枪械源文件。

7.62整弹为13 cubes；弹壳移除铜色弹头段，保留壳体、肩部、底缘与抽壳槽，补暗色凹口和底火，共9 cubes。独立256×256贴图是原BR51 atlas的逐字节副本，保留材质一致性，不改枪纹理。

9mm当前正式美术资产已切换为 `src/main/blockbench/9x19mm_round.bbmodel` 与 `9x19mm_casing.bbmodel` 的纯 Cube vFinal 版本：整弹29 cubes，弹壳26 cubes，共用64×64黄铜/铜材质。模型用削角分块表达紧凑壳体、分层壳口、收束弹头、底缘与底火；没有使用 Mesh、图像生成或照片贴图。运行 item JSON 与贴图同步替换为对应 vFinal 内容；本次仅完成资源接入与构建，游戏内视觉验收仍需实机检查。

12.7×55mm资源复用同一普通 Item 注册路径：`src/main/blockbench/ammo_127x55_cube_v1/12_7x55mm_round.bbmodel` 为35 cubes，`12_7x55mm_casing.bbmodel` 为29 cubes；运行模型与贴图位于 `assets/apocalypse_firstlight/models/item/` 和 `textures/item/`，使用64×64黄铜/钢色贴图。该口径供 HR55 使用；抛壳使用同名的客户端模型预注册路径，不新增 Ammo 系统或属性。

12 Gauge 完整弹现以 `src/main/blockbench/12ga_hybrid_mesh_prototype.bbmodel` 为可编辑 Mesh 源，4 个部件、672 个三角面，共用原 256×256 贴图。正式 ID 仍为 `12_gauge_round`，仍是64堆叠普通 `Item`；仅客户端视觉入口改为 `models/item/12_gauge_round.json` 的 `builtin/entity`。GUI 的 `display.gui` 调为近直立居中（rotation `[20,-25,0]`、translation `[0,-2.5,0]`、scale `[0.4,0.4,0.4]`）；其他视角沿用原 `display` 变换。`Afl12GaugeRoundClient` 将现有 Item 的 Forge 客户端扩展绑定到 `Afl12GaugeRoundRenderer`；后者从 `geo/12_gauge_round.geo.json`、`meshes/12_gauge_round.aflmesh.json` 和 `textures/item/12_gauge_round_mesh.png` 读取骨骼、阶段1 Mesh sidecar 和原样贴图，使用现有 `AflMeshCache` / `AflMeshRenderer` 提交。旧110 cube 的 Java 模型原样留在 `models/item/legacy/12_gauge_round_java.json`，旧可编辑源 `src/main/blockbench/12g_round.bbmodel` 和 `textures/item/12g_round.png` 也保留，便于回滚。

`12_gauge_casing` 仍使用原 `12g_casing.bbmodel` 对应的 Java item JSON 和64×64贴图；新的独立 spent Mesh 源暂不接入正式空壳。Silverwood 内部 live/spent shell 组、动画和玩法数据未变。GUI、手持、掉落、F3+T、Embeddium/Oculus 与法线/UV 视觉均待用户实机验收；离线导出与编译不等于这些场景通过。

`.50 AE` 标准源为 `src/main/blockbench/50ae_round.bbmodel`（live_round、512 triangles）和 `50ae_casing.bbmodel`（spent_case、448 triangles）。坐标从原 Blender 导入空间按枪械同一 0.04 比例转换，弹轴 Z→Y 并将底部放到零点；几何、比例和 UV 保留。`geo/50_ae_round.geo.json`、`geo/50_ae_casing.geo.json` 与对应 `.aflmesh.json` 是静态纯 Mesh 资源；`models/item/50_ae_round.json`、`50_ae_casing.json` 使用现有 `builtin/entity` Item Mesh 入口。两件共用 `textures/item/blackridge_50ae_ammo_v1.png` 正式 512×512 Base Color atlas，源文件位于 `src/main/blockbench/textures/`；整弹以 512、弹壳以 16 为逻辑 UV 尺寸，均归一映射到同一 PNG。整弹 GUI `display.gui` 为 `[25,-30,-45]` 旋转、3 倍缩放，绕中点呈弹头朝上的立体 45° 斜置；客户端 Mesh renderer 的中心补偿为 `0.431217`（弹壳 `0.444568`），手持/掉落/展示各有独立缩放。显示姿态均只经静态检查，仍待实机微调。Blackridge 已通过 `native_guns/blackridge_50.json` 绑定该弹药，容量 7；其射击消耗沿用现有 Native Gun 流程。

其他既有弹药继续使用普通 Java 三维 item 模型。12 Gauge 完整弹与两件 .50 AE 是纯 Mesh 静态 geo 的例外，均为普通 Item 而非枪械 renderer；具体游戏内可读性待目测，不把离线检查当成实机通过。

## 文件与旧新映射

上述十个 ID 均有 item 模型与贴图资源；12 Gauge 完整弹和 .50 AE 两件另有 Mesh sidecar/geo，.50 AE 共用正式 Base Color atlas。源文件与贴图命名如下：

- `src/main/blockbench/<ID>.bbmodel`（12 Gauge 完整弹当前源为 `12ga_hybrid_mesh_prototype.bbmodel`，旧源 `12g_round.bbmodel` 为回退；空壳仍为 `12g_casing.bbmodel`）
- `src/main/resources/assets/apocalypse_firstlight/models/item/<ID>.json`
- `src/main/resources/assets/apocalypse_firstlight/textures/item/<ID>.png`（12 Gauge 完整弹当前使用 `12_gauge_round_mesh.png`，旧贴图为 `12g_round.png`；空壳仍使用 `12g_casing.png`）
- `.50 AE` 两件源文件分别为 `50ae_round.bbmodel` / `50ae_casing.bbmodel`，共用运行贴图 `textures/item/blackridge_50ae_ammo_v1.png`，并分别有 `geo/50_ae_round.geo.json` / `50_ae_casing.geo.json` 与同名 `meshes/*.aflmesh.json`。`NativeGunFx` 对 .50 AE 弹壳使用现有 Mesh 缓存、GeckoLib 骨骼与共用 atlas 绘制，旧口径继续使用烘焙四边面；物品本身的 3D 渲染已配置。开火抛壳与物品显示均待实机验收。

旧 `9mm_round` 注册通过 `registry/NativeWeaponLegacyMappings.java` 的 MissingMappings 转为 `9x19mm_round`，不把旧手枪弹转换为步枪弹；旧存档加载兼容尚待实机确认。旧 `9mm_round` / `9mm_casing` 源模型、item和 `9mm_palette.png` 留作历史对照，不再是正式引用。旧导出器 `tools/export-9mm-assets.mjs` 不用于当前资产。

既有 9mm / 7.62 工作流：`node tools/export-native-ammo.mjs`；引用检查：`node tools/verify-native-ammo.mjs`。12 Gauge 完整弹和 .50 AE Mesh sidecar 由 `node tools/export-afl-mesh.mjs` 从对应 Blockbench 源和 geo 确定性导出；旧 12 Gauge Java JSON / PNG 原样保留作为回退。离线几何预览：`tools/preview-native-ammo.py` → `build/native-ammo-preview.png`，仅用于既有资产检查。

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
