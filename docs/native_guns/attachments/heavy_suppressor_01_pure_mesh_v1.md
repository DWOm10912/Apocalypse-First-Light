# 12.7×55mm 重型消音器 V1（Pure Mesh + PBR）

2026-09-29 新增，已接入 HR55。`compileJava --offline` 通过，离线校验通过。**未实机验证**：没有运行 runClient、GameTest，也没有在光影下看过。

## 定位

| 项目 | 当前实现 |
| --- | --- |
| ID / 名称 | `apocalypse_firstlight:heavy_suppressor_01` / 12.7×55mm 重型消音器 / 12.7×55mm Heavy Suppressor |
| 物品类 | `NativeSuppressorItem`，与另外两种消音器相同；最大堆叠 1 |
| 槽位 / 兼容 | `MUZZLE`；接口 `heavy_brake_qd`，额定弹药 12.7×55mm。当前只有 HR55 |
| 效果 | 游戏噪声半径 ×0.05（HR55：128 → 6 格）；开火声切换为枪自己的 `suppressed_fire_sound`（HR55 为 `hr55_fire_suppressed`）；枪口特效和出口移到筒口 |
| 平衡代价 | 暂无。降噪倍率与另外两种消音器统一，按用户决定：这是制式装备，以后玩家可以手搓便宜的消音器，届时再区分倍率 |
| 创造标签 | “黎明启示录 · 配件”，排在 7.62×51mm 步枪消音器之后 |

**外形**
- 参考 12.7×55mm 系列（例如 VKS）那种很粗很长的消音器：从护木前端出来直接接一根粗长光滑的管子，长径比约 7.6。
- 不对应任何品牌，没有铭文、标识或序列号。

## 结构：反向套管式（overbore）

- 前腔套在枪的制退器外面，内腔台阶顶在制退器后方的套环上。
- 后套管向后包住裸露的枪管，一直延伸到护木前端前 0.37。
- 因此装上后看不到枪管和制退器。

| 项 | 值（HR55 比例，1 单位约 23.5 mm） |
| --- | --- |
| 全长 | 22（约 520 mm）：装配点后 4.25，装配点前 17.75 |
| 主筒直径 | 2.9（约 68 mm） |
| 前腔（套制退器） | 半径 1.0，深 3.7 |
| 后套管内孔（套枪管） | 半径 0.6，长 4.25 |
| 出口孔 | 半径 0.40，深 1.3，带锥形倒角 |
| 装到 HR55 上 | 出口 `[0, 7.34375, −36.13375]`，比裸枪制退器前端多出 14.2；全枪长从 40.6 增加到约 54.8 |

**部件**：车削 Pure Mesh，24 个圆周段，接缝朝下。

| 部件 | 内容 |
| --- | --- |
| `suppressor_rear_mount` | 前腔（底面 + 内壁）、落座台阶、后套管内孔、后端面（机加工，孔口倒角）、后端盖 r 1.34 |
| `suppressor_locking_ring` | 快拆锁定环 r 1.40，两侧倒角，环面滚花（只在 `_n` 里）；后接缩颈 r 1.33 |
| `suppressor_main_tube` | 主筒 r 1.45，素面；前端是端盖接缝的后半 |
| `suppressor_front_cap` | 端盖接缝的前半、半径 0.28 的大圆角前缘（4 段）、平端面、锥形出口、内孔 |

- **面数**：1200 三角等效，664 个面，其中 536 个四边形、128 个三角形。低于软上限 1400。
- **不建模**：内部消音隔板、真实螺纹、几何滚花。

## 材质（512 贴图，每单位 20 像素；Base Color / `_s` / `_n`）

| 分区 | Base Color | 光滑度 | F0 | 说明 |
| --- | --- | --- | --- | --- |
| 主筒 | 35,36,38 | 72 | 24（约 0.09） | 与 7.62 消音器相同的哑光高温涂层，大面积表面不会在光影下反射天空 |
| 端盖 / 前缘 / 端面 | 34–44 | 74–92 | 24 | 同一涂层，圆角前缘稍亮 |
| 后端盖、端面、锁定环 | 40–60 | 116–160 | 金属 | 深色机加工钢；锁定环滚花用 `_n` 表现，边缘接高光 |
| 缩颈、接缝 | 27–31 | 64–95 | 24 / 金属 | 带 AO |
| 前腔、后套管内孔、出口孔 | 7–26 | 28–80 | 金属 / 非金属 | 越深越暗，AO 90–220 |

- **Base Color**：
  - 钢件有很弱的车削行纹；涂层有很弱的低频斑驳。
  - 出口周围有一圈极淡的积碳。
  - 没有划痕、铭文，也没有画上去的反射。
- **`_n`**：24 棱面按真圆柱着色，锁定环加浅滚花，B 通道为 AO。
- 材质语言与 7.62×51mm 步枪消音器一致。

## `heavy_brake_qd` 挂载接口（通用规范）

以后的 12.7×55mm 枪要接这支消音器，只要满足下面几条，不需要为它写专门的代码、偏移或缩放：

1. 枪在 `muzzle_slot` 里声明 `"mount_interface": "heavy_brake_qd"`，枪的 `ammo` 为 `apocalypse_firstlight:12_7x55mm_round`，并配置 `suppressed_fire_sound`。
2. `muzzle_slot.anchor` 指向一个空骨骼：
   - 位于枪管轴线上，在**制退器套环的后端面**；
   - 不带旋转，前方为 −Z。
3. 锚点前方、筒体包络（半径 1.47）以内的枪口装置，必须装得进前腔：半径小于 0.98，深度小于 3.65。
4. 紧贴锚点前方要有一个比后套管内孔更粗（半径大于 0.6）的套环，消音器的内腔台阶顶在它上面。
5. 锚点后方 4.25 以内、包络以内，只允许有半径小于 0.58 的枪管；其他几何（护木等）必须更靠后。
6. 资产按 AFL 统一实际比例建模，附件缩放固定为 1.0。

`tools/verify-heavy-suppressor-01.mjs` 会对**每一把**接受这支消音器的枪自动检查第 2–5 条，不依赖枪名或部件名：
- 按 geo 的静态骨骼旋转还原 bind pose；
- 除了顶点，还沿每个面的边每 0.2 取样，能查出没有顶点落在包络里、但整个穿过包络的长面；
- 换弹新匣和甩出的旧匣副本不参与检查。

**与其他接口的关系**：`NativeMuzzleMount.parse` 严格比较 `rated_ammo` 和 `mount_interface`。所以这支消音器装不上 BR51，7.62×51mm 步枪消音器也装不上 HR55。

## HR55 接入

- **槽位**：`native_guns/hr55.json` 的 `muzzle_slot` 声明接口 `heavy_brake_qd`，`accepts` 为这支消音器。
- **锚点**：`muzzle_anchor` 从 `[0, 7.32835, −16.13348]` 移到 `[0, 7.34375, −18.38375]`。
  - 原位置在裸枪管中段，那里没有枪口装置，而且比枪管轴线低 0.0154。
  - 新位置是制退器套环（`octagon2`）的后端面，在枪管轴线上。
  - 由 `tools/build-hr55-v2-mesh.mjs` 的 `MUZZLE_ANCHOR` 写入源文件和运行时 geo。这是一个空锚点，枪的 Mesh、其他骨骼和动画都没有变。
  - 裸枪的枪口特效仍然用 `muzzle_pos` + 3.55125。
  - 维护台和野外配件视图的枪口热点跟着锚点前移 2.25。
- **装配结果**（离线）：

  | 位置 | 内容 |
  | --- | --- |
  | 前腔内 | 枪管前端、两个套环和八角制退器；最深 3.546，最大半径 0.933 |
  | 落座 | 套环半径 0.637，大于后套管内孔 0.6 |
  | 后套管内 | 只有枪管，半径 0.536 |
  | 后端面到护木 | 0.374 |

- **视线**：筒顶比红点光轴（`ads_center` y 13.48）低 4.69，不挡红点，也不挡机瞄。
- **通用路径**：渲染、物品栏统一图标、维护台 / 野外配件视图的安装与拆卸、出口特效、消音开火声都走现有通用代码，没有 HR55 专属代码。

## 文件

| 类型 | 路径 |
| --- | --- |
| 生成器 | `tools/build-heavy-suppressor-01.mjs`（`--check` 校验确定性），基于共享的 `tools/lathe-mesh-lib.mjs` |
| 可编辑源 | `src/main/blockbench/heavy_suppressor_01.bbmodel`（Free Model，4 个 mesh 部件）；贴图源副本 `src/main/blockbench/textures/heavy_suppressor_01{,_s,_n}.png` |
| 运行时 | `geo/heavy_suppressor_01.geo.json`（骨骼 `heavy_suppressor_root` 和 `muzzle_exit_anchor`）、`meshes/heavy_suppressor_01.aflmesh.json`（V2）、`textures/item/heavy_suppressor_01{,_s,_n}.png`（512）、`models/item/heavy_suppressor_01.json`（`builtin/entity`，display 与 7.62 消音器相同） |
| 配件数据 | `data/apocalypse_firstlight/native_attachments/heavy_suppressor_01.json`：`noise_multiplier` 0.05、`rated_ammo`、`mount_interface` |
| 注册 | `AflItems.HEAVY_SUPPRESSOR_01`、`AflCreativeTabs`（配件页）、中英文名称与说明 |
| 离线校验 | `tools/verify-heavy-suppressor-01.mjs` |

## 离线验证

- `node tools/build-heavy-suppressor-01.mjs --check`：CHECK OK。
- `node tools/verify-heavy-suppressor-01.mjs`：HEAVY_SUPPRESSOR_V1_OFFLINE_PASS。
  - 网格闭合、朝外，无 NaN，UV 在范围内；三张贴图都是 512。
  - 原点和出口锚点位置正确。
  - 兼容数据：HR55 可装，BR51 不可装。
  - 装配：上面列的各项。
  - 注册 ID、创造标签顺序、中英文名称和说明、物品模型。
- `node tools/verify-hr55-v2-mesh.mjs`：通过（锚点改动已纳入校验）。
- `node tools/verify-rifle-suppressor-01.mjs`、`node tools/verify-rifle-red-dot-01.mjs`：通过。
- `node tools/verify-afl-mesh.mjs --java-loader`：通过，JShell 用真实加载器加载了全部生产 sidecar，包括这支消音器。
- `node tools/migrate-hr55-native-rig-v2.mjs`：CHECK OK。
- `./gradlew compileJava --offline`：BUILD SUCCESSFUL。
- 离线渲染看过：侧面、前 3/4，以及护木与消音器接合处特写。

**开发探针**：`src/dev` 里的 `NativeSuppressorProbe` / `NativeSuppressorTests` 只覆盖手枪和 7.62 两种消音器，没有扩展到这一支。

## 待实机验收

- 第一 / 第三人称外观，尤其是腰射时加长的枪口位置。
- 光影下的涂层和钢件。
- 维护台和野外配件视图的安装与拆卸，以及装上后在维护垫上的位置。
- 掉落物和物品栏图标。
- 消音开火声、筒口的烟雾和枪口特效位置。
