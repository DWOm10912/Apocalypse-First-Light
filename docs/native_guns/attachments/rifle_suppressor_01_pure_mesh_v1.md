# 7.62×51mm 步枪消音器 V1（Pure Mesh + PBR）

2026-09-28，已实现，`compileJava --offline` 通过，**未实机验证**（未运行 runClient / GameTest / 光影）。

- 注册 ID 不变：`apocalypse_firstlight:rifle_suppressor_01`。没有新物品，也没有做注册 ID 迁移。
- 显示名：`7.62×51mm 步枪消音器` / `7.62×51mm Rifle Suppressor`，tooltip 同步改为 QD 套装描述。
- 定位：通用 7.62×51mm 战斗步枪 / 步枪消音器，采用 QD 方式套装在消焰器上，现代军用风格。
  - 结构语言（快拆安装、比例、分段、材质逻辑）参考 SureFire SOCOM762 / KAC QDC 一类。
  - 不对应任何品牌，没有铭文、标识或序列号，也没有复制具体外形。
- 取代旧版资产：56 段 cube，旧 BR51 128 配色。
  - 旧建模说明 [rifle_suppressor_01_model_v1.md](rifle_suppressor_01_model_v1.md) 仅作历史。
  - 旧 `src/main/blockbench/br51_rifle_suppressor_fit_preview.bbmodel` 是 cube 时代的装配预览，已过时，只作历史保留。

## 文件

| 类型 | 路径 |
|---|---|
| 生成器 | `tools/build-rifle-suppressor-01.mjs`（`--check` 校验确定性），基于共享的 `tools/lathe-mesh-lib.mjs` |
| 可编辑源 | `src/main/blockbench/rifle_suppressor_01.bbmodel`（Free Model，4 个 mesh 部件；覆盖了旧 cube 源，旧版在 Git 历史） |
| Runtime | `geo/rifle_suppressor_01.geo.json`（只有骨骼 `rifle_suppressor_root` 和 `muzzle_exit_anchor`）、`meshes/rifle_suppressor_01.aflmesh.json`（Hybrid Mesh V2） |
| 贴图 | `textures/item/rifle_suppressor_01{,_s,_n}.png`，512×512 RGBA；源副本在 `src/main/blockbench/textures/` |
| 配件数据 | `data/apocalypse_firstlight/native_attachments/rifle_suppressor_01.json` |
| 离线校验 | `tools/verify-rifle-suppressor-01.mjs` |

## 模型结构

- 车削 Pure Mesh，20 个圆周段，接缝朝下。
- 原点在后端安装面中心，前方为 −Z。
- 共 1240 三角等效：670 个面，其中 570 个四边形、100 个三角形；顶点提交 2680。
- 比目标 800–1200 略高，低于软上限 1400。多出的部分主要来自锁定环中间那道分隔两段滚花的凹槽（约 120）。

| 部件 | 内容 |
|---|---|
| `suppressor_qd_mount` | 后端机加工安装面 r 0.80，外径 r 0.84。消焰器内腔 r 0.545、深 4.12（带底面）|
| `suppressor_locking_collar` | 快拆锁定环 r 0.97：前后台阶面与倒角、两段滚花带，中间一道凹槽（r 0.94）；前端缩颈 r 0.88 |
| `suppressor_main_tube` | 主筒 r 0.92（直径 1.84，约为 BR51 枪管的 2.8 倍、消焰器台肩的 1.6 倍）。两条很浅的宽 V 分段线（深 0.015、半宽 0.06），以及端盖接缝的后半部分 |
| `suppressor_front_cap` | 独立端盖：接缝、滚边（0.07）、平端面、锥形出口倒角、真实内孔 r 0.26 深 0.9（带底面）|

- 全长 13.2。装在 BR51 上时，比裸枪消焰器前端多出 9.18（旧 cube 版为 9.75）。
- 不建内部消音隔板，没有真实螺纹或几何滚花；滚花只在 `_n` 里。
- 轮廓分成圆柱段与锥面段两种 UV 展开（`strip` / `facets`），坐标保留 12 位小数，让 Hybrid Mesh V2 尽量保住四边形。
  - 共 600 个源四边形，保留 570 个。
  - 剩下的 30 个是很小的锥面倒角，按规则三角化。

## 材质（Base Color / `_s` / `_n`）

| 分区 | Base Color | 光滑度 | F0 | 说明 |
|---|---|---|---|---|
| 主筒 | 35,36,38 | 72（边缘 84） | 24（约 0.09） | 黑色高温陶瓷类涂层，覆在钢上：哑光，保留一点克制的高光，不当成塑料那样完全平的非金属 |
| QD 安装座 | 46,47,50（端面 50–64） | 138–165 | 金属 | 深色机加工钢，比主筒更金属、更光滑，边缘接高光 |
| 锁定环 | 44,45,48（边缘 62） | 118（滚花）/ 160（边缘） | 金属 | 滚花用 `_n` 表现（直纹菱形，节距 0.16），凹槽压暗 |
| 端盖 | 40,41,44（滚边 54） | 108 / 132 | 金属 | 处理钢；光滑度和锁定环错开 |
| 出口与内孔 | 13–24 | 28–45 | 非金属 | 很暗、很粗糙，越深越暗，AO 90–200 |
| 分段线 / 接缝 | 27,28,30 | 64 | 24 | AO 215 |

- **Base Color**：
  - 各分区一种干净色调，只有很轻的朝上受光项。
  - 钢件有很弱的车削行纹（±0.6%），涂层有很弱的低频斑驳（±1%）。
  - 出口周围有一圈极淡的积碳。
  - 没有划痕、磨损、铭文，也没有画上去的反射或白色描边。
- **`_s`**：R 光滑度（带 ±3–5 的低频漂移），G 为 F0（255 金属），B 0，A 255。
- **`_n`**：
  - 20 棱面按真圆柱着色（和 P9 手枪消音器相同的方法）。
  - 锁定环加浅滚花。
  - B 通道为材质 AO。
  - 大结构（锁定环、分段线、端盖、轮廓）全部由几何表现。
- 没有针对 Sundial、Complementary 或 Oculus 的特殊处理。

## `rifle_fh_qd` 挂载接口（通用规范）

以后的 7.62 步枪要接这支消音器，只需满足下面的接口规范，不用为消音器写特殊代码、偏移或缩放。

1. 枪在 `muzzle_slot` 里声明 `"mount_interface": "rifle_fh_qd"`，并且枪的 `ammo` 是 `apocalypse_firstlight:762x51mm_round`。
2. `muzzle_slot.anchor` 指向一个空骨骼。它位于枪管轴线上，在**消焰器的安装肩面**处（消音器后端面顶住的位置），不带旋转，前方为 −Z。
3. 锚点前方、半径 0.99 以内的所有几何都必须能装进内腔：半径小于 0.525，深度小于 4.07。
4. 锚点后方 0.8 以内，必须有半径大于 0.545 的肩面，让后端面顶住。
5. 资产按 AFL 统一实际比例建模。附件缩放固定为 1.0，没有 `attachment_scale`。

`tools/verify-rifle-suppressor-01.mjs` 会对**每一把**接受这支消音器的枪自动检查第 3、4 条，不依赖枪名或部件名：

- 按 geo 的静态骨骼旋转还原 bind pose。
- 换弹新匣和甩出的旧匣副本不参与检查。

以后某把 7.62 步枪的枪口不同，有三种处理方式：

- **按接口建模**：像现实中消音器厂商给每把枪配套消焰器座一样，把那把枪的消焰器 / 安装座做成符合上述尺寸。首选这种。
- **声明另一种接口**：比如 `rifle_thread` 直连螺纹。此时这支消音器不会被接受，加载时数据校验直接报错，需要另做对应接口的消音器。
- **接口本身不够用时**：再讨论是否为不同接口做后端安装段变体。本轮没有做。

## 兼容与数据

- **运行时权威仍是枪的 `muzzle_slot.accepts`**（`NativeAttachments.compatible`），本轮没有重构兼容框架。
- **新增通用配件数据层** `NativeAttachmentData`：
  - 读取包内 `data/<ns>/native_attachments/<id>.json`，可选字段为 `noise_multiplier`、`rated_ammo`、`mount_interface`。
  - 没有对应文件时使用代码默认值，因此 P9 手枪消音器行为不变，仍为 0.05。
  - 这是只读的包内数据，不是数据包重载系统。要调数值，改 JSON 后重新打包即可。
- **`NativeMuzzleMount.parse` 的校验**：`accepts` 里的枪口配件如果声明了 `rated_ammo`，必须等于枪的 `ammo`；如果声明了 `mount_interface`，必须等于槽位声明的接口。不一致时抛异常，防止明显的口径或接口错误。
  - 只做严格相等，不做“小口径向下兼容”。5.56 等枪以后要用这支消音器，必须显式 opt-in，并单独放宽规则。
- **噪声**：`noise_multiplier: 0.05` 已移到数据层，数值不变（BR51 112 → 6 格）。

| 枪 | 结果 |
|---|---|
| BR51-01 | 兼容（`mount_interface: rifle_fh_qd`） |
| HR55（12.7×55mm） | 已移出 `accepts`，不能安装。2026-09-29 起 HR55 的枪口槽改为 `heavy_brake_qd` 接口，只接受 12.7×55mm 重型消音器，见 `heavy_suppressor_01_pure_mesh_v1.md` |
| C.A.T. | 没有枪口槽，未改 |
| 其他枪 | 未增加兼容 |

**HR55 旧存档**：用户确认不使用旧存档，本轮没有做兼容处理。现有 Runtime 下的行为（审查结论，未实测）：

- 已存的附件 NBT 仍可安全读取，不崩溃，也不删除物品。
- 但因为 `accepts` 为空，枪口热点不再显示，也不能通过维护台拆下。
- 消音器既不渲染、也不生效（`active()` 只返回兼容的附件）。

## Runtime（复用通用路径，没有 BR51 专属代码）

- **渲染**：`NativeMuzzleRendering.drawItem` 发现同名 sidecar 就走 Hybrid Mesh，覆盖手持、第一 / 第三人称、掉落物、物品栏、维护台和 Field Attachment View。物品模型 `models/item/rifle_suppressor_01.json`（builtin/entity 的 display）未改。
- **出口与特效**：`NativeMuzzleRendering.applyExit` 取配件 geo 的 `muzzle_exit_anchor`。装上配件后 `barrelExitOffset` 置 0，不再叠加裸枪偏移；消音烟雾和枪口特效都从罐口生成。裸枪行为不变，仍是 `muzzle_pos` + 4.8125，属于 LEGACY_COMPAT。
- **声音**：继续使用枪自己的 `suppressed_fire_sound`（BR51 为 `br51_01_suppressed`），没有新增通用回退。
- **不改平衡**：伤害、射程、后坐、散布、射速、ADS、穿透、耳鸣阈值均未改动。

## BR51 装配

- **锚点调整**：BR51 的 `muzzle_anchor` 从 `[0, 11.4375, -26.2]`（消焰器中段，无肩面）移到 `[0, 11.4375, -23.98437]`，也就是消焰器后部台肩（r 0.5625）的前端面。
  - 这是这一个空锚点的最小改动，由 `tools/build-br51-01-v2-mesh.mjs` 生成。
  - 主枪 Mesh、PBR、其它骨骼和动画都没有改。
  - 维护台和 Field 的枪口热点随锚点后移了 2.2。
- **装配结果**：
  - 消焰器（深 4.016、最大半径 0.507）完全在内腔里。
  - 装上后出口位于 `[0, 11.4375, -37.18437]`。
  - 护木、准星、枪管、导气管都在安装面之后，没有穿模：最近的是枪管末端，在安装面后 0.74。

## 验证（离线）

- **`node tools/verify-rifle-suppressor-01.mjs`**：`RIFLE_SUPPRESSOR_V1_OFFLINE_PASS`。
  - 网格：无 NaN，UV 在 [0,1] 内；整体闭合、朝外。
  - 贴图：三张都是 512×512。
  - 出口锚点位于端面内孔中心，原点位于后端面。
  - 兼容：BR51 允许，HR55 拒绝，C.A.T. 无槽，`accepts` 与 `rated_ammo` / 接口一致。
  - 装配：内腔包覆、肩面、护木 / 准星 / 枪管穿模检查。
  - 其它：注册 ID 与显示名；P9 手枪消音器文件未改动。
- **生成器 `--check`**：`build-rifle-suppressor-01`、`build-pistol-suppressor-01`、`build-br51-01-v2-mesh` 均通过。
- **`node tools/verify-afl-mesh.mjs --java-loader`**：通过（JShell 用真实的 `AflMeshLoader` 加载全部生产 sidecar，包括本配件）。
- **`./gradlew.bat compileJava --offline`**：BUILD SUCCESSFUL。
- **视图**：离线渲染了装配件的左、前 3/4、后 3/4、近景和第一人称；Blockbench 临时标签看了侧面近景和前 3/4（已关闭）。
- **开发探针同步**：`src/dev/.../NativeSuppressorProbe.java` 的步枪出口期望值从 11.55 改为 13.2（开发源集，本轮未编译运行）。

**未实机验证**：

- 第一 / 第三人称外观
- Shader 关闭，以及 Oculus + Sundial / Complementary 下的 PBR 分区
- 维护台 / Field 安装与拆卸
- 掉落物与物品栏
- 消音烟雾出口位置
- HR55 拒绝安装
