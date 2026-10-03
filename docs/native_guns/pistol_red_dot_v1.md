# 手枪微型红点 V1

> 当前附件入口：维护台与 Z Field Attachment View V1 共用附件业务、候选 HUD、音效及服务端原子交易。共享通道协议为 **31**（2026-10-02 加入容器搜索声音包后），客户端/服务端须匹配；以下旧协议和验证记录属于历史。V 仍为 Inspect，快捷安装未恢复。详见 `docs/native_guns/field_attachment_view_v1.md`。

## 配件与属性速查

统一目录和后续属性模板见 [配件总表](attachments/README.md)。

| 项目 | 当前值 / 行为 |
| --- | --- |
| 名称 / ID | 手枪微型红点瞄具 / `apocalypse_firstlight:pistol_red_dot` |
| 槽位 / 当前兼容 | `SIGHT` / P9-01 |
| 主要效果 | Pure Mesh 外壳 + 半透明镜片；第一人称准直红点（通用 `NativeCollimatedReticleRendering`，标记屏幕中心 / hitscan，只在视线穿过镜窗时显示），模型内无红点实体 |
| ADS 中心 | P9 `ads_center = [0, 6.52778, 0.25339]`，即当前安装下镜片 `lens_center` 的枪模型坐标；机械安装 `mount_offset [0,-0.44,7.33]` 不变 |
| FOV / ADS 时间 | 沿用 P9 `0.95` / `0.15 s`，无额外倍率或速度加成 |
| 伤害 / 后坐力 / 散布 | 无额外修改 |
| 安装入口 | 仅枪械维护台安装 / 拆卸 / 更换 |

## 实现说明

当前 Tooltip 仅显示原版名称和灰色非斜体介绍，不再显示额外的“瞄准：红点瞄具”行或分割线。配件装拆仅通过枪械维护台，无快捷键。统一规则见 [装备 Tooltip V1](../ui/equipment_tooltip_v1.md)。

正式物品：`apocalypse_firstlight:pistol_red_dot`，最大堆叠 1，进入「黎明启示录 · 配件」标签页（2026-09-28 起，原「AFL 武器与弹药」已拆分）。本手枪红点当前只兼容 P9-01；步枪另有独立 `rifle_red_dot_01`。无配件耐久、品质或倍率选项；装拆复用维护台 UI，无安装手臂动画。

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

当前 `pistol_red_dot` 已切到 NativeSightItem(true) + Pure Mesh sidecar。5 个外壳 part 走 CUTOUT，optic_lens 走 TRANSLUCENT。保存源与源贴图未修改；只导出 runtime。Shader OFF 代码就绪，Oculus 默认 0.1 alpha test 可能丢弃 24/255 镜片，不能宣称透明 PBR 已通过。见 [透明 Runtime V1](transparent_hybrid_mesh_runtime_v1.md)。旧 body/reticle 资源仍保留作历史，但当前 Geo 路径不再绘制它们。瞄准点由下文 [准直 Reticle Runtime V1](#准直-reticle-runtime-v1) 在第一人称单独绘制。

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
  - 通用准直准星渲染（见下文）读取这两个骨骼：判断屏幕中心视线与镜片平面的交点是否仍在有效窗口内；P9 的 `ads_center` 也按 `lens_center` 求出。

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

**早期建议标定值（历史，P9 未采用）**：按两把枪的实际网格截面测得。P9 实机确认现有机械安装正常后冻结 `mount_offset`，只按当前安装重算 `ads_center`（见下文 [P9 ADS 标定](#p9-ads-标定2026-09-27)）；Blackridge 仍未接入，下表只作后续参考。

| 枪 | 瞄具平台 | `mount_offset` | `ads_center` |
|---|---|---|---|
| P9（未采用） | 套筒平顶 y 5.950，宽 1.10，z 0.1–2.7（抛壳口止于 0.05，照门起于 2.72） | [0, −0.33978, 7.62661] | [0, 6.628, 2.94939] |
| Blackridge（未接入） | 套筒平顶 y 9.754，宽 1.48，z 0.3–2.6 | [0, −0.103, 10.6] | [0, 10.432, 4.1] |

- 两把枪的 `sight_anchor` 都在套筒上。
- 当时建议把瞄具中心放在 z 1.40，另加 0.003 抬升；`ads_center` 的 z 沿用各自机瞄的 aim z（P9 2.94939，Blackridge 4.1）。

**仍待后续轮次**

1. 已完成：通用准直准星渲染 `NativeCollimatedReticleRendering`（只在第一人称），待实机验收。
2. 已完成：NativeSightItem(true)、保存源导出、旧 body/reticle 停止绘制。独立物品 display 沿用旧值，视觉待验。
3. P9：`ads_center` 已按 `lens_center` 重标，机械安装冻结，待实机验收。Blackridge：没有 `sight_slot`，需要在 P9 通过后单独做安装与 ADS 标定；运行时无需新代码。
4. 已完成混合层代码；待验证 Shader OFF 画面与 Shader ON 低 alpha/PBR 限制。

当前 P9 装上本红点后：新 Mesh 外壳与镜片，ADS 时镜片中心在屏幕中心，第一人称有准直红点。以上只经过离线计算、离线渲染和 compileJava，未启动客户端，不宣称实机通过。

## P9 ADS 标定（2026-09-27）

**机械安装（冻结，未修改）**：`sight_slot.anchor = sight_anchor`，`mount_offset = [0, −0.44, 7.33]`。用户实机确认安装正常；本轮用 bind pose 网格截面复核：

- 左右居中：原点 x = 0，外壳关于 X 对称（右侧风偏盖与左侧按键只差 0.005）。
- 贴合：sight 原点 (0, 5.85278, 1.10339)。套筒平顶 y 5.950，覆盖 z 0.05–2.70。底板占地 z 0.053–2.153，完全落在平顶上。
  - 底板 y 5.853–5.958：厚 0.105 的底板有 0.097 藏在套筒内，只露出 0.008。
  - 外壳底面（本地 y 0.09）比套筒顶面低 0.007。
  - 结果：没有缝隙，也没有可见穿插，看起来是外壳直接坐在套筒上。
- 抛壳口：底板前缘 z 0.0534，在抛壳口后沿（约 z 0.05）之后，不遮挡；抛壳锚点 z −0.66 在瞄具前方。

按"安装正确就不为 ADS 移动 mount"的规则，mount 保持不变；上表 [0, −0.33978, …] 的"完全贴平"方案未采用。

**ADS 中心**：`ads_center` 与机械安装独立，仍是手填数据（不做运行时自动推导），但数值按实际几何求出：

```
sight 原点   = sight_anchor pivot (0, 6.29278, −6.22661) + mount_offset (0, −0.44, 7.33) = (0, 5.85278, 1.10339)
lens_center  = sight 原点 + 红点 geo lens_center pivot (0, 0.675, −0.85)          = (0, 6.52778, 0.25339)
ads_center   = lens_center = [0, 6.52778, 0.25339]
```

- 求解：`NativeAdsProfile.ads() = T(0,0,−eyeRelief)·S(0.53)·R(ads_rotation)·T(−aim/16)`，`correction = ads·hip⁻¹` 已抵消 P9 第一人称 Display（translation [3.25, −6.46691, −11.9885]/16、rotation 0、scale 0.53、Geo 净 +0.01 Y）。完全 ADS 时 `lens_center` 落在视空间 (0, 0, −0.47)，即屏幕中心。
- 偏斜：`ads_rotation = [0,0,0]`，aim x = 0，因此枪轴与视线平行，没有左右偏斜；基础机瞄轴本身沿模型 −Z（见 [ADS 文档](native_ads_v1.md)）。
- 框景：镜片中心离眼 0.47（与机瞄 profile 到照门的 eye relief 相同）。因此整枪比机瞄 ADS 近 0.089 格：镜片在照门前 2.696 单位，乘 0.53/16。
  - 手部 FOV 为 70 × ADS 倍率 0.95 = 66.5°。在 1080p 下镜窗约宽 56 px、高 36 px；枪最靠后的弹匣底板约在 0.33 格处，远离近裁面 0.05。
  - 若实机觉得太近，可把 z 改回机瞄 aim z 2.94939：保持机瞄 ADS 的整枪距离，镜窗约小 16%，屏幕中心仍在镜片中心。
- 离线软件渲染 ADS 视角：屏幕中心落在镜窗中心，镜框四周对称。
- `NativeAdsProfile.forStack` 的调试标签仍为 `sight_anchor/reticle_dot`，只是标签，与几何无关。
- 旧值历史：`[2.48, 7.756, 2.04]` → `[1.50, 5.80, 2.04]`（旧 cube 红点时代的截图标定），现均作废。

## 准直 Reticle Runtime V1

2026-09-27，已实现，**未实机验证**。P9 + 本红点先行；Blackridge 接入 `sight_slot` 后自动适用，不需新代码。

**入口**：`weapon/client/NativeCollimatedReticleRendering`，通用类，没有 P9 或红点专用 renderer。

1. `ConfiguredGunFirstPerson` 在第一人称 `renderStatic` 前调用 `begin()`，之后调用 `draw()`，在 finally 中调用 `end()`。
2. 在这段时间内，`NativeSightRendering` 画完 Geo 瞄具后调用 `capture(sight, pose)`，记录瞄具原点矩阵。
3. 其他上下文（第三人称、维护台、地面、GUI）不会 begin，因此不会记录或绘制。
4. 整把枪提交完后才画红点，保证所有外壳和枪体的深度已经写入。

**数据**（客户端资源，F3+T 重载即可实机微调）：`assets/apocalypse_firstlight/optics/pistol_red_dot.json` 对应物品 `apocalypse_firstlight:pistol_red_dot`。

| 字段 | 当前值 | 含义 |
|---|---|---|
| `texture` | `apocalypse_firstlight:textures/effects/collimated_reticle_dot.png` | 柔边圆点贴图，必须存在，否则拒绝该文件 |
| `color` | `[255, 38, 30]` | 顶点颜色染色，可加第 4 个 alpha |
| `angular_diameter_degrees` | `0.4` | 贴图四边形的视角直径（含柔边；50% alpha 核心约为 0.68 倍，即约 0.27°）；0.01–5。实机后由 0.55 调小 |
| `max_off_axis_degrees` | `12`（缺省 12） | 瞄具光轴（sight −Z）与视线的最大夹角；0–45 |
| `aperture_shape` | 缺省 `rectangle` | `rectangle` 或 `ellipse`（2026-09-28 新增，圆管瞄具用）；椭圆时交点按半轴椭圆判定 |
| `lens_center_bone` / `lens_aperture_bone` | `lens_center` / `lens_aperture`（缺省同名） | 瞄具自身 geo 中的骨骼 |

没有 `optics/<item>.json` 或没有 `collimated_reticle` 对象的瞄具不画红点。2026-09-28 起 `rifle_red_dot_01` 也改用本渲染（`optics/rifle_red_dot_01.json`，椭圆窗口），旧的全亮 `reticle` cube 已退出，见 [步枪红点 Pure Mesh V1](attachments/rifle_red_dot_01_pure_mesh_v1.md)。

**判定**（手部渲染的 pose 空间即视空间：相机在原点，屏幕中心 / hitscan 沿 −Z）：

1. 从瞄具 geo 骨骼遍历（与绘制瞄具相同的 `prepMatrixForBone` 链）求 `lens_center` 坐标系。
   - 本地 +Z 为镜片法线，带 −4° 倾角。
   - `lens_aperture` 原点在该坐标系中的 |x|、|y| 为有效窗口的半宽和半高：0.43 × 0.295。
2. 光轴门限：瞄具 −Z 与视线夹角大于 `max_off_axis_degrees` 时不画。避免检视、换弹或野外附件视图中斜看镜窗时出现红点。
3. 求交：屏幕中心射线与镜片平面求交。平行或交点距离小于 0.05（近裁面）时不画。
4. 窗口：交点换算回镜片坐标，超出半宽或半高就隐藏（`aperture_shape: ellipse` 时按半轴椭圆判定）；在窗口内才画。
5. 绘制：在交点处（向眼侧偏移 0.1%）画一个垂直于视线的四边形，半边长为 `t · tan(直径/2)`，`t` 为交点距离。因此屏幕尺寸只由视角决定，不随模型远近变化。
   - ADS 时 `t` 约为 0.47；在 1080p、66.5° 手部 FOV 下，四边形约 5.8 px，核心约 3.9 px。

**渲染状态**：私有 RenderType `afl_collimated_reticle`。

- 着色器：原版 `rendertype_entity_translucent_emissive`，不受 lightmap 和漫反射明暗影响，顶点光照 FULL_BRIGHT。
- 混合：TRANSLUCENT，alpha 低于 0.1 的像素丢弃。
- 深度：LEQUAL 测试，关闭深度写入（COLOR_WRITE），NO_CULL。
- 贴图：线性过滤。几像素的点在 TAA 抖动下仍保持圆形，不会闪烁。
- 遮挡：比镜片更靠近眼的镜框、通道壁和枪体都通过深度遮挡红点。镜片本身不写深度，因此不会遮住红点。
- 标准 BufferSource：外壳 cutout 与镜片在绘制瞄具时已定向 flush，红点在整枪之后提交并 flush。
- Oculus：包装 buffer 按透明类别排序，不透明类在前；同类中镜片先请求，红点后请求。实际批处理顺序待实机确认。

**Shadow**：只在第一人称手部绘制中存在，且提交前再用 `AflShaderCompat.activeShadowPass()` 跳过，不进入 shadow pass。

**不改变**：实际 hitscan、`NativeGunShot`、ADS 进度和 FOV、`optic_lens` 几何、透明 Hybrid Mesh 框架、P9 几何与动画、枪口附件、BR51。

**贴图**：`tools/build-collimated-reticle-dot.mjs`（加 `--check` 只校验）生成 32×32 贴图：

- `collimated_reticle_dot.png`：RGB 全白，alpha 在核心内为 1，从半径 0.42 平滑过渡到 0.94 处为 0。
- `collimated_reticle_dot_s.png`：LabPBR `_s`，R 0，G 10（介电），B 0，A 发光 = 254 × alpha。
- 不做 `_n`，使用平面默认值。

**已知限制**：

- 红点标的是相机中心，也就是真实弹着方向，不模拟光轴视差。
  - 因此 sway 或后坐让镜窗偏离屏幕中心时，红点不会跟着枪移动，而是在交点离开窗口时隐藏。
  - P9 `recoil.modelPitch` 已从 5 调到 0.5（实机发现每发都把镜窗顶出中心，红点闪灭）。模型后坐绕相机旋转：单发 0.5° 时交点下移约 0.12 单位；半自动极限射速稳态约 0.64°，下移约 0.16 单位，都在窗口半高 0.295 内。相机后坐（真实瞄准上跳）不变，机瞄 ADS 与腰射的模型上跳也同样变小。
  - `shoot` 动画第 1 帧 `handling` 约 4.3° 上抬并后移，离线估算单独就把交点推到约 −0.26（接近窗口下沿和发射器外壳），约 1–2 帧；本轮未改动画。
- Shader ON：alpha 阈值 0.1 会切掉最外圈柔边。能否通过 `_s` 发光、bloom 强度，取决于光影包。
- 与 Oculus 的批处理顺序、TAA 下的观感都未实机验证。
- 只画一个红点；不做多点或 MOA 刻度环，也没有亮度档位。

## 挂载、保存与 ADS

- `NativeGunDefinition.sightMount` 来自可选 JSON `sight_slot`；P9 白名单仅含本配件。BR51 与 HR55 的 SIGHT 接入独立 `rifle_red_dot_01`，不接受本手枪红点。
- P9 使用现有 `sight_anchor` 的动画变换并叠加 `sight_slot.mount_offset=[0,-0.44,7.33]` 渲染瞄具；挂载位置、模型和动画未因本次 ADS 重标而改变。
- `NativeAdsProfile.forStack` 在安装兼容瞄具时使用 `sight_slot.ads_center` 替换机械瞄具坐标。当前 P9 为 `[0, 6.52778, 0.25339]`（镜片 `lens_center`，见上文 [P9 ADS 标定](#p9-ads-标定2026-09-27)）；eye relief、FOV、进入时间、后坐力和伤害不变。旧值 `[2.48,7.756,2.04]`、`[1.50,5.80,2.04]` 属于旧 cube 红点，已作废。像素级对齐仍需客户端复验。
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
