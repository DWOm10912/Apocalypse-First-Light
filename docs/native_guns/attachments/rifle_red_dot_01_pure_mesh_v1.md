# 步枪微型红点 Pure Mesh V1（rifle_red_dot_01）

2026-09-28 重置。仅做了离线生成、离线校验、离线渲染，以及 `compileJava`（通过）；**未启动客户端，未实机验证**。

Registry ID、名称、Tooltip、槽位不变：`apocalypse_firstlight:rifle_red_dot_01`，中文名“步枪红点瞄具”，英文名 “Rifle Red Dot Sight”。接入、装拆与维护台规则见 [步枪红点 V1](rifle_red_dot_01_v1.md)。

## 定位

原创、无品牌的**封闭短管微型红点**，装在一体式增高座上，适用于 AFL 步枪。它不是 BR51 专属：挂载按通用接口 `rifle_optic_rail` 定义。三种瞄具的外形刻意区分：

- 手枪微型红点：开放式反射镜，低矮；
- 本步枪微型红点：圆管 + 增高座；
- 全息瞄具：尚未制作，计划为方形大窗。

## 文件

| 类型 | 路径 |
| --- | --- |
| 生成器 | `tools/build-rifle-red-dot-01.mjs`（直接运行写出全部文件；加 `--check` 只校验不写入） |
| 离线校验 | `tools/verify-rifle-red-dot-01.mjs` |
| 可编辑源 | `src/main/blockbench/rifle_red_dot_01_mesh.bbmodel`（Free Model，5 个 Mesh，内嵌 Base Color） |
| 源贴图 | `src/main/blockbench/textures/rifle_red_dot_01{,_s,_n}.png`，均为 512×512 |
| 运行时 | `geo/rifle_red_dot_01.geo.json`（仅骨骼）、`meshes/rifle_red_dot_01.aflmesh.json`（V2）、`textures/item/rifle_red_dot_01{,_s,_n}.png`、`models/item/rifle_red_dot_01.json`（`builtin/entity`） |
| 准直红点数据 | `assets/apocalypse_firstlight/optics/rifle_red_dot_01.json` |
| 配件数据 | `data/apocalypse_firstlight/native_attachments/rifle_red_dot_01.json`（`mount_interface: rifle_optic_rail`） |

旧的 29-cube 源 `src/main/blockbench/rifle_red_dot_01.bbmodel` 和提取脚本 `extract_rifle_red_dot.cjs` 保留作历史，不再导出。提取脚本已标注 LEGACY：BR51 源模型里的 `sight` 组已删除，运行会直接失败。旧的全亮 `reticle` cube 随旧 Geo 一起退出。

## 安装接口 `rifle_optic_rail`

- 原点：导轨顶面，位于夹座正中下方。+Y 向上，−Z 为枪口方向，X 对称，资产缩放 1.0，单位为 AFL 步枪模型单位。
- 夹座占地：x ±0.66、z ±1.05，向导轨顶面以下伸出 0.22，包住导轨两侧。
- 对枪的要求：在整个夹座长度内提供顶面位于原点、宽度至少 0.8 的导轨。
- 光轴高度：导轨顶面以上 2.0 单位。

枪在 `sight_slot` 中声明：
- `mount_interface: "rifle_optic_rail"`；
- `mount_offset`：把原点放到自己的导轨顶面；
- `ads_center`：等于装上后 `lens_center` 的位置。

加载时 `NativeSightMount` 校验：`accepts` 中每个声明了 `mount_interface` 的瞄具，都必须与槽位声明一致，否则拒绝加载。`accepts` 仍是运行时兼容的唯一依据。

## 几何（1314 triangle-equivalent：559 Quad + 196 Triangle，共 755 面）

| 部件 | 内容 | 三角面 |
| --- | --- | --- |
| `optic_tube` | 管身：32 边；外径 0.66，内径 0.53；z −0.95 … +0.85。包括前端面、前后 0.035 倒角、镜罩与管身（一条连续 strip）、后目镜端面、哑光内膛，以及内膛底部近目镜处的 LED 发射器小凸块 | 432 |
| `optic_mount` | 导轨夹座（x ±0.66，y −0.22 … 0.22，两端倒角）；腹板（侧视梯形，贯通减重窗，x ±0.34）；一体底座（顶部埋入管壁，低于内膛） | 304 |
| `optic_controls` | 顶部高低调节盖、右侧风偏调节盖（均 14 边，护帽带倒角）；左侧亮度 / 电池旋钮（16 边，防滑带） | 396 |
| `optic_hardware` | 右侧快拆扳把与枢轴凸台（12 边）；左侧六角横螺母 | 150 |
| `optic_lens` | 镜片：32 片扇形平面，translucent 层 | 32 |

- 轮廓：x −0.91 … 0.915，y −0.22 … 2.915，z −1.05 … 1.05。
- 光轴：y 2.0，沿 −Z。
- 调节盖与旋钮位于 z −0.05。
- 所有旋转体的底端都埋入相邻零件，没有缝隙，也不需要封口。
- 旋转体按类型展开 UV，映射都是仿射的，V2 能保留 Quad：
  - 同半径段展开为条带（角度 → u，弧长 → v）；
  - 锥形段展开为逐面分栏；
  - 平环面用平面投影。
- 其余硬表面零件用法线分块的平面 UV 岛。

## 光学层与骨骼

- **镜片 `optic_lens`**：
  - 一张薄平面，位于 z −0.73，即前端面后方 0.22；
  - 半径 0.535，比内膛大 0.005，边缘插进管壁；
  - 不倾斜；
  - 独立骨骼、独立 UV 岛。
  - 生成器把该部件标为 `translucent`，其余部件为 cutout。
- **定位骨骼**（都不是可见几何，模型里没有红点实体）：
  - `lens_center`：`optic_lens` 的子骨骼，枢轴 (0, 2.0, −0.73)，不旋转，本地 +Z 就是镜片法线（朝射手）。
  - `lens_aperture`：`lens_center` 的子骨骼，枢轴 (0.51, 2.51, −0.73)，Geo 中写为 x −0.51。它相对 `lens_center` 的偏移就是有效窗口的半轴 0.51 × 0.51（内膛半径 0.53）。
- **准直红点**：通用 `NativeCollimatedReticleRendering`，与手枪红点同一套实现，数据见下表。

| 字段 | 值 |
| --- | --- |
| `texture` | `apocalypse_firstlight:textures/effects/collimated_reticle_dot.png` |
| `color` | `[255, 38, 30]` |
| `angular_diameter_degrees` | `0.35`（手枪为 0.4） |
| `max_off_axis_degrees` | `12` |
| `aperture_shape` | `ellipse`：圆管窗口按椭圆判定，交点超出半轴椭圆即隐藏 |

## 材质（Base Color / `_s` / `_n`）

镜身和座子与 BR51 机匣同一套涂层方案：
- 平面：F0 24（涂层）；
- 倒角：F0 255，底色均匀提亮一档；
- 不做逐像素描边；硬表面上短于 0.35 的倒角面，高光按长度淡出。

| 材质 | Base Color | 平滑度（平面 / 倒角） | F0 | 备注 |
| --- | --- | --- | --- | --- |
| 镜身（硬质阳极氧化黑铝） | 38,40,43 | 92 / 142 | 24（倒角 255） | 倒角高光 +20 |
| 座子 / 夹座 | 35,37,40 | 86 / 136 | 24（倒角 255） | 减重窗内壁 AO 220 |
| 调节盖 / 旋钮顶 | 41,43,46 | 100 / 148 | 24（倒角 255） | |
| 旋钮防滑带 | 30,31,33 | 58 | 10 | 只用粗糙度区分，不画滚花（避免闪烁） |
| 钢件（扳把、枢轴、螺母） | 54,56,59 | 145 / 170 | 金属 | |
| 内膛 | 19,20,22 | 40 | 10 | 越靠近镜片越暗；AO 205 → 160 |
| LED 发射器 | 16,17,19 | 150 | 10 | AO 225 |
| 镜片镀膜 | 178,160,208 | 235 | 10 | alpha 40/255，淡紫红镀膜 |

- 镜罩环线：在管身 z −0.70 … −0.665 处画一条暗带（亮度 ×0.78，AO 215）。它沿条带的像素行排列，不是几何。
- `_n` 用于旋转体：每个像素朝真实圆柱法线倾斜（切线方向 x = +u = 角度增大方向），光影下 32 边管身呈圆柱面。硬表面零件的法线保持平面。
- 镜片 alpha 40/255，高于 Oculus 0.1 的透明阈值（手枪红点为 24/255，低于阈值）。开 Shader 时镜片会作为透明面绘制，实际效果未验证。
- 关 Shader 时整个 ADS 视野会带上约 16% 的淡紫色调。如果嫌重，可以在生成器 `MAT.glass` 中调低 alpha 或改色。

## 枪上标定（离线求解，未实机验证）

装配公式：`sight 原点 = sight_anchor 绑定位置 + mount_offset`，`ads_center = sight 原点 + (0, 2.0, −0.73)`。

| 枪 | 导轨顶面（实测） | `sight_anchor` | `mount_offset` | sight 原点 | `ads_center` |
| --- | --- | --- | --- | --- | --- |
| BR51 | 机匣导轨 x ±0.55、y 12.8125、z 2.64 … 7.03 | (0, 12.75, 5.54688) | `[0, 0.0625, -1.11375]` | (0, 12.8125, 4.43313) | `[0, 14.8125, 3.70313]`（不变） |
| HR55 | 顶部导轨，横筋顶 y 11.484375，z −4.09 … 1.08 | (0, 11.18035, −2.10661) | `[0, 0.30403, -0.00719]` | (0, 11.48438, −2.1138) | `[0, 13.48438, -2.8438]`（y 原为 13.4625） |

- **框景不变**：两把枪的镜片深度 z 都保持原 `ads_center` 的 z，所以开镜时整枪到眼睛的距离与之前一致。BR51 的光轴高度正好也与旧值相同。
- **HR55**：y 抬高 0.022，使镜片中心正好在光轴上。旧值来自旧 cube 红点的截图标定。
- **离线开镜渲染**：按 `NativeAdsProfile` 的眼点和手部 FOV（70 × ADS 倍率）渲染，两把枪的镜窗都在屏幕正中；镜片到眼点之间的视线上没有枪体几何。
  - BR51 的准星柱出现在镜窗下沿，相当于“下三分之一共线”；照门低于光轴约 0.87，且位于近裁面以内，不遮挡。
  - HR55 的准星环顶部低于镜窗下沿。

## 物品展示

- 物品模型为 `builtin/entity`，渲染器是 `NativeMuzzleRendering.ItemRenderer`，它把原点（导轨顶面）放在物品中心。
- 每种展示情境的 translation 都由生成器计算：先旋转、缩放，再把变换后的包围盒中心移回原点，所以各情境都居中。GUI 离线复算：宽 ±0.29、高 ±0.425 格，占格子高度约 85%。
- 缩放：物品展示框 4.0，手持和地面 0.7。
- 物品栏（GUI）：2026-09-28 起改为所有配件共用的朝向 `[20, 135, 0]`、缩放 1，由 `NativeAttachmentGuiFit` 统一居中并把最长边缩放到格子的 85%；上面的生成器居中只用于其他情境。见 `native_gun_inventory_presentation_v1.md`。
- 背包、手持与地面的实际观感未实机验证。

## 离线校验（`node tools/verify-rifle-red-dot-01.mjs`：RIFLE_RED_DOT_V1_OFFLINE_PASS）

- 资产：V2 格式；三张贴图均为 512；无 NaN，UV 在 0–1 内；只有 `optic_lens` 在 translucent 层；预算不超过 1400 三角面。
- 骨骼：`lens_center` 等于 LENS 且不旋转；`lens_aperture` 半轴为 0.51；镜片平面位于 z −0.73，边缘在内膛壁内。
- 数据：准直红点数据为椭圆窗口；配件数据声明 `rifle_optic_rail`；所有接受本红点的槽位接口一致。
- 装配（每把接受本红点的枪）：
  - 夹座内侧下方的最高点就是原点平面（BR51 偏差 0，HR55 < 0.001）；
  - 夹座、增高座和镜身包络内没有枪体几何；
  - 镜片后方 6 单位、半径为窗口半径的视线圆柱内没有枪体几何；
  - `ads_center` 等于装配后的镜片中心。
- 其他已跑：`tools/build-rifle-red-dot-01.mjs --check`、`tools/verify-afl-mesh.mjs`、`tools/verify-transparent-mesh.mjs`、`tools/verify-rifle-suppressor-01.mjs`、`tools/export-pistol-red-dot-mesh.mjs --check` 均通过。

## 待实机验收

- 第一人称开镜：镜窗居中、红点位置和大小、椭圆窗口边缘的隐藏效果、镜片色调（Shader 关 / 开）。
- 第三人称、维护台、野外附件视图中的外观与挂载位置。
- 背包图标与手持展示。
- 光影下旋转体 `_n` 的圆滑效果（切线方向约定与步枪消音器相同，同样未经实机验证）。
