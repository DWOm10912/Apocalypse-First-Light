# 手枪微型红点 V1

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **29**，客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](attachments/README.md)。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | 手枪微型红点瞄具 / `apocalypse_firstlight:pistol_red_dot` |
| 槽位 / 当前兼容 | `SIGHT` / P9-01 |
| 主要效果 | Pure Mesh 外壳 + 半透明镜片；本轮无实体/准直瞄准点，ADS 沿用现有值待新资产校准 |
| ADS 中心 | P9 当前视觉校准坐标 `[1.50,5.80,2.04]` |
| FOV / ADS 时间 | 沿用 P9 `0.95` / `0.15 s`，无额外倍率或速度加成 |
| 伤害 / 后坐力 / 散布 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |

## 实现说明

当前 Tooltip 仅显示原版名称和灰色非斜体介绍，不再显示额外的“瞄准：红点瞄具”行或分割线。配件装拆仅通过枪械维护台，无快捷键。统一规则见 [装备 Tooltip V1](../ui/equipment_tooltip_v1.md)。

正式物品：`apocalypse_firstlight:pistol_red_dot`，最大堆叠 1，进入「AFL 武器与弹药」标签页配件位置。本手枪红点当前只兼容 P9-01；步枪另有独立 `rifle_red_dot_01`。无配件耐久、品质或倍率选项；装拆复用维护台 UI，无安装手臂动画。

## 使用

- 将枪放入维护台，点击瞄具热点，通过背包候选页安装；已装配件可更换或拆卸。
- 安装消耗候选来源物品，拆卸安全返还并保留 NBT；共用音效结束后服务端重新验证并提交。规则见 [维护台交互](attachments/gun_maintenance_attachment_interaction_v1.md)。
- SIGHT、MUZZLE、MAGAZINE 独立保存；移除红点不会拆掉其他配件。手持状态不再支持快捷装拆。

## 历史 Cube 资产与比例（已退出当前渲染）

源：`src/main/blockbench/pistol_red_dot.bbmodel`，独立 21 cube / 5 group、32×32 像素材质图集。

`sight_root` 下分 `mount_base`、`sight_body`、`window_frame`、`illuminated`；`reticle_dot` 是独立小几何，不是画在镜片上的点。开放镜窗无中央玻璃面，没有半透明排序、反射或折射；仅红点使用全亮渲染，外壳正常受光。

瞄具宽约 2.12、长 2.40、高约 1.75 模型单位；与 P9 2.24 单位宽套筒匹配。短 adapter plate 随瞄具自带，放在后半段平顶且在照门前，不跨抛壳口。不复制 BR51 的模型、导轨或比例；以独立低模硬表面、暗灰面差和克制边缘高光保持 AFL 风格。

P9 的源几何、静态 `sight_anchor`、Display 和全部动画关键帧**均未修改**。白版没有额外顶轨或永久底座。

独立配件的第一/第三人称手持和地面 `display.scale` 从 3.0 调整为 0.8，即原线性尺寸约 27%；背包图标、物品展示框及枪上挂载比例不变。源文件与物品模型同步，不缩改几何本体。

## AFL Micro Pistol Red Dot V1 资产（2026-09-27，已接入，待实机验收）

当前 `pistol_red_dot` 已切到 NativeSightItem(true) + Pure Mesh sidecar。5 个外壳 part 走 CUTOUT，optic_lens 走 TRANSLUCENT。保存源与源贴图未修改；只导出 runtime。Shader OFF 代码就绪，Oculus 默认 0.1 alpha test 可能丢弃 24/255 镜片，不能宣称透明 PBR 已通过。见 [透明 Runtime V1](transparent_hybrid_mesh_runtime_v1.md)。旧 body/reticle 资源仍保留作历史，但当前 Geo 路径不再绘制它们。

**定位**：原创、无品牌的通用手枪微型红点，低矮、紧凑、大镜窗、薄镜框，倒角克制。通过统一的手枪瞄具安装接口使用，不是 P9 专用。首批目标是 P9 和 Blackridge，模型内部不包含任何枪专用偏移。

**生成与文件**

- 生成器：`tools/build-pistol-red-dot.mjs`，替换了旧的 cube 导出器，旧的 `--create` / `--export` 不再存在。
  - 默认只写编辑源和三张源贴图；加 `--runtime` 才会写 Geo、sidecar、运行时贴图和物品模型；加 `--check` 只校验不写入。
- 编辑源：`src/main/blockbench/pistol_red_dot_mesh.bbmodel`（Free Model，6 个 Mesh，内嵌 Base Color）。旧的 cube 源 `pistol_red_dot.bbmodel` 原样保留作历史参考。
- 源贴图：`src/main/blockbench/textures/pistol_red_dot.png`、`_s.png`、`_n.png`，均为 256×256，同一套自动 UV，约 47 texel/单位。
- 本轮由 `node tools/export-pistol-red-dot-mesh.mjs` 从保存源写出（`--check` 验证；不运行生成器覆盖源）：`geo/pistol_red_dot.geo.json`（仅骨骼）、`meshes/pistol_red_dot.aflmesh.json`（V2）、`textures/item/pistol_red_dot{,_s,_n}.png`，以及 `models/item/pistol_red_dot.json`（`builtin/entity`）。

**手枪瞄具安装接口**

- 原点在安装底面中心，+Y 向上，-Z 为枪口方向，X 对称，资产缩放 1.0。
- 占地 x ±0.55、z ±1.05（适配 1.10 宽的套筒平顶），总高 1.05。
- 枪的 `sight_slot.mount_offset` 负责把原点放到自己套筒的瞄具平台上；`ads_center` 取镜片中心的高度。V1 仍由人工填写，保留手动标定作为回退。

**几何**：784 triangle-equivalent，保存源经当前 V2.1 导出为 603 个面（181 Quad + 422 Triangle），分 6 个部件。旧生成器即时数据的 308 Quad 不再作为保存源统计：

| 部件 | 内容 | 三角面 |
|---|---|---|
| `optic_hood` | 镜框。侧边约 0.083、顶部 0.08；前后 0.025 倒角；前端 z −1.00，后端 −0.40 | 320 |
| `optic_body` | 低矮后机身，高 0.36，后缘 0.035 倒角 | 60 |
| `optic_emitter` | 发射器外壳，从后方看位于镜窗下沿。只是外壳，不发光 | 40 |
| `optic_plate` | 安装底板 | 96 |
| `optic_controls` | 左侧两个亮度键、顶部调节/电池盖、两颗螺丝、右侧风偏盖 | 248 |
| `optic_lens` | 镜片 | 20 |

**光学层**

- **镜片 `optic_lens`**：一张薄平面（绕中心的 20 片扇形三角），不做体积，也不用 Cube。
  - 位置：在镜窗通道内，距前端面 0.15。
  - 轮廓：等于镜窗开口外扩 0.005，边缘插进通道壁里，不留缝，也不和任何镜框面共面。
  - 倾角：绕 X 轴 −4°，顶部前倾。
  - 独立性：有独立骨骼 `optic_lens`，材质标识为 `glass`，UV 岛也是单独的，不和机身共用材质区域。现由 tools/pistol-red-dot.layers.json 将该 part 标为 translucent，Java 不按 bone 名硬编码材质。
- **镜片尺寸**：
  - 镜窗开口：底宽 0.93、顶宽 0.80、高 0.59（沿模型 Y）；
  - 沿倾斜镜片面的高度为 0.5914；
  - 圆角：下角 0.06，上角 0.13。
  - 准星判定用的有效窗口：半宽 0.43、半高 0.295，即宽 0.86（镜片中心高度处）、高 0.59。
- **定位骨骼**（都不是可见几何，模型里没有任何红点实体）：
  - `lens_center`：`optic_lens` 的子骨骼，枢轴 (0, 0.675, −0.85)，位于镜片平面中心，与镜窗开口中心一致。它带 −4°（绕 X）旋转，本地 +Z 就是镜片法线：朝射手，(0, 0.0698, 0.9976)；朝前的一侧为反向。Geo 按导出约定写成 rotation [4,0,0]。
  - `lens_aperture`：`lens_center` 的子骨骼，枢轴 (0.43, 0.97, −0.85)，Geo 中写为 x −0.43。它随父骨骼的倾斜落到镜片平面上，在镜片坐标系中的位置就是有效窗口的半宽和半高 (0.43, 0.295)。
  - 以后的通用准直准星渲染会用这两个骨骼：判断屏幕中心视线与镜片平面的交点是否仍在有效窗口内，并作为光学对准的参考。

**材质**（Base Color / `_s` / `_n`）

| 材质 | Base Color | 平滑度 | F0 | 备注 |
|---|---|---|---|---|
| 机身（硬质阳极氧化黑铝） | 40,41,43 | 95 | 介电 | 倒角 54,55,58、平滑度 120 |
| 镜窗通道 | 28,29,31 | 55 | 介电 | AO 215，哑光防眩 |
| 底板（磷化钢） | — | 85 | 金属 | |
| 螺丝（机加工钢） | — | 140 | 金属 | |
| 按键（橡胶） | — | 35 | 介电 | 粗糙 |
| 调节盖 | — | 100 | 介电 | |
| 发射器外壳 | 22,23,26 | 170 | 介电 | |
| 镜片（光学玻璃） | 168,186,194 | 235 | 介电 | alpha 24/255，见下 |

- 所有表面 `_n` 都是平面法线，只有通道带 AO，没有高频纹理。
- 镜片 alpha 24/255 保持不变。Shader OFF 使用标准 entityNoOutline alpha blend、深度测试、关闭深度写入；Oculus 默认透明 shader 仍有 0.1 阈值，Shader ON 低 alpha 与 PBR 状态为 LIMITED，待实机验证。

**建议的接入标定值**（按两把枪的实际网格截面测得，**尚未写入枪械数据**）：

| 枪 | 瞄具平台 | `mount_offset` | `ads_center` |
|---|---|---|---|
| P9 | 套筒平顶 y 5.950，宽 1.10，z 0.1–2.7（抛壳口止于 0.05，照门起于 2.72） | [0, −0.33978, 7.62661] | [0, 6.628, 2.94939] |
| Blackridge | 套筒平顶 y 9.754，宽 1.48，z 0.3–2.6 | [0, −0.103, 10.6] | [0, 10.432, 4.1] |

- 两把枪的 `sight_anchor` 都在套筒上。
- 瞄具中心都放在 z 1.40，另加 0.003 抬升，避免和套筒顶面共面。
- `ads_center` 的 z 沿用各自机瞄的 aim z（P9 2.94939，Blackridge 4.1），保持原有眼距。
- 离线渲染的 ADS 视角确认：屏幕中心落在镜窗中心，两把枪的照门都在镜窗下方。

**仍待后续轮次**

1. 通用准直准星渲染（`NativeCollimatedReticleRendering`，只在第一人称）。
2. 已完成：NativeSightItem(true)、保存源导出、旧 body/reticle 停止绘制。独立物品 display 沿用旧值，视觉待验。
3. 为 P9 和 Blackridge 写入上表的 `sight_slot`。
4. 已完成混合层代码；待验证 Shader OFF 画面与 Shader ON 低 alpha/PBR 限制。

当前已经使用新 Mesh 外壳与镜片；准直 reticle 未实现，所以没有瞄准点。P9/Blackridge sight_slot 本轮未改，不宣称新模型 ADS 已校准。

## 挂载、保存与 ADS

- `NativeGunDefinition.sightMount` 来自可选 JSON `sight_slot`；P9 白名单仅含本配件。BR51 的 SIGHT 接入独立 `rifle_red_dot_01`，不接受本手枪红点。
- P9 使用现有 `sight_anchor` 的动画变换并叠加 `sight_slot.mount_offset=[0,-0.44,7.33]` 渲染瞄具；挂载位置、模型和动画未因本次 ADS 重标而改变。
- `NativeAdsProfile.forStack` 在安装兼容瞄具时使用 `sight_slot.ads_center` 替换机械瞄具坐标。旧值 `[2.48,7.756,2.04]` 与当前 Artist rig/HIP 标定不符，实机会把红点明显压向左下；现按用户截图重标为 `[1.50,5.80,2.04]`，保持当前 eye relief、FOV、进入时间、后坐力和伤害不变。最终像素级对齐仍需客户端复验。
- 模型从真实 `sight_anchor` 遍历矩阵渲染，继承套筒后坐/后定、换弹、整枪 ADS、第三人称与地面显示变换，不使用屏幕固定 HUD 点。
- 配件保存在枪 ItemStack 的 `AflAttachments.SIGHT` 完整配件 NBT。服务端原子装拆、正常背包同步负责客户端显示，丢弃/存档随枪保留；不以全局布尔值开关。
- `NativeSightRendering` 是共享静态挂载渲染器；当前 P9 通过 `NativeAnimatedWeaponRenderer` 在动画 anchor 遍历中调用它。首批 P9 接口适配 `P901SightLayer` 已随专用 Renderer 退役。以后其他手枪仍需声明兼容 ID/局部安装点/ADS 点；不承诺只有 anchor 名称就自动完成所有渲染与 ADS 标定。
- 当前网络协议 **22**；旧快捷装拆报文保留编号但不执行任何变更。玩家装拆仅使用维护台服务端事务。

## 历史 Cube 导出与限制（当前改用上文 Mesh 入口）

运行时目录 `src/main/resources/assets/apocalypse_firstlight/`：

- `geo/pistol_red_dot.geo.json`：同源几何导出，保留分组。
- `textures/item/pistol_red_dot.png`：独立图集，与源中嵌入图一致。
- `models/item/pistol_red_dot.json`：独立物品模型，含背包/手持/地面 Display。
- `models/item/pistol_red_dot_body.json`、`pistol_red_dot_reticle.json`：挂载时的正常受光外壳和独立全亮红点。V1 实际使用同源 baked item quads，geo 为完整几何交付，不增加第二套动画实体。

V1 cube 资产原来由旧版 `tools/build-pistol-red-dot.mjs --export/--check/--create` 导出。2026-09-27 该脚本已被新的 Pure Mesh 生成器替换（见上文 AFL Micro Pistol Red Dot V1 资产一节），上面这些运行时文件保持 V1 当时的内容，可从 git 历史取回旧导出器。

已装瞄具的枪在背包仍沿用现有 P9 平面图标，不动态合成配件图标。V1 为固定几何红点、非真实光学准直/视差模拟；无玻璃染色、镜片反射。暂无合成配方，正式入口为创造标签或 `/give`。

## 历史 Cube 验证边界（不代表新 Mesh 验收）

- 构建：compileJava / processResources / build 离线通过；缩放修正版再次通过，`pistol-red-dot-scale-build.log`。源/导出一致性检查及 `git diff --check` 通过。
- Blockbench：独立模型已载入，21 个 cube 全部绑定同一有效贴图 UUID，截图 `build/pistol-red-dot-blockbench.png`；未覆盖用户打开的 P9 工作页。
- 两轮 GameTest 均为 67 项中 66 项通过；新增装拆测试无失败。整套未全绿：已有的 `nativenoiseflatdistances` 分别在 Hearing boundary 60、40 失败，日志 `pistol-red-dot-gametest.log` / `pistol-red-dot-gametest-final.log`；未扩大范围调整噪声，也未断言已排除其原因。
- 旧版客户端 ADS 数值检查不能代表当前 Artist rig 的红点对齐；用户实机截图确认旧 `ads_center` 明显向左下偏移。本轮重标后的红点中心以及独立配件手持/掉落视觉均待客户端复验，不把资源处理或构建当作视觉通过。

未 commit、未 push。
> Current channel protocol: **22**, adding P9 MAGAZINE support while retaining atomic shot confirmation. Earlier protocol references below are historical. Matching client/server required. See [24R magazine](p9_01_extended_magazine_v1.md).
