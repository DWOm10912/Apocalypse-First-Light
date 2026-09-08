# 热力发电机动态燃料视觉预留 V1

状态：**静态 3D 外观、动态燃料、液体视觉和转子已由 [Dynamic Renderer V1](thermal_generator_dynamic_renderer_v1.md) 接入；锚点粒子见 [Particles V1](thermal_generator_particles_v1.md)。** 真实 4000 mB 熔岩槽与左进右出已接入，见 [Fluid IO V1](thermal_generator_fluid_io_v1.md)。原注册、GUI、FE 输出、燃料定标、声音及挖掘规则保留；旧烟雾占位由客户端持续排烟替代。

## 结构

- 正面为 −Z：三色状态灯；中左固体燃烧室；中右转子；左下液体窗；右下原通风栅。
- 左下沿用 X6.65～14.1、Y2.15～3.7 区域。压框 Z0.35～1.06，净玻璃 X6.95～13.8 / Y2.45～3.4 / Z1.06～1.1；内部暗壁、底面、顶面和 Z4.6 后壁形成真实深槽。两端短刻度强调液位观察用途，没有增加百叶或第三个大窗。
- 固体燃烧室保留主玻璃、后壁、左右壁、底面及炉排。炉排顶面 Y4.55；旧橙色方柱和炭床归入隐藏且不导出的 `source_only_flame_preview`，不作为最终燃料或火焰。
- 右下 `right_lower_vent` 的五个 cube、坐标和 UV 原样保留。转子共心参数及外部机壳不变。
- 1×1×1 基准尺寸不变；沿用原排气塔最高 Y17.2 的轻微突出。

## 坐标与锚点

以下为 Blockbench 模型绝对坐标，16 单位 = 1 格，+Y 向上，−Z 朝前。所有新增 anchor 无自身旋转，不含可见 cube。Geo 导出把 X 取反，Y/Z 保持；表中坐标不是相对 parent 的平移量，renderer 接入时应使用骨骼变换，不要再次叠加 parent pivot。

| Anchor | Parent | 源坐标 | 用途 |
| --- | --- | --- | --- |
| `solid_fuel_display_anchor` | `solid_combustion_chamber` | `[10.325,4.55,3.825]` | 炉排上方燃料底面中心 |
| `solid_flame_fx_anchor` | `solid_combustion_chamber` | `[10.325,4.8,3.825]` | 燃料上方火焰定位 |
| `solid_ember_fx_anchor` | `solid_combustion_chamber` | `[10.325,6,3.825]` | 火星定位 |
| `liquid_render_anchor` | `liquid_heat_window` | `[10.375,2.5,2.9]` | 液体显示底面中心 |
| `liquid_surface_anchor` | `liquid_heat_window` | `[10.375,3.32,2.9]` | 满液位表面参考；不是随 tank 变化的当前液位 |
| `liquid_fx_anchor` | `liquid_heat_window` | `[10.375,3.34,2.9]` | 槽内液面上方效果定位 |
| `fluid_input_anchor` | `left_fluid_port` | `[16,8,8]` | 左侧视觉接口中心，外法线 +X |
| `fluid_output_anchor` | `right_fluid_port` | `[0,8,8]` | 右侧视觉接口中心，外法线 −X |

## 显示体积约束

隐藏且 `export=false` 的源参考 cube 保留在各自 `*_source_only` 组，**不得导出为实体燃料或不透明填充物**。运行时 renderer 已沿用下列模型边界；显示体积不代表逻辑容量。

| 参考体积 | 最小坐标 | 最大坐标 |
| --- | --- | --- |
| `solid_fuel_display_volume` | `[7.65,4.55,2.1]` | `[13,6.3,5.55]` |
| `liquid_display_volume` | `[7.35,2.5,1.45]` | `[13.4,3.32,4.35]` |

固体参考体积底面与炉排顶面相接；距离主玻璃背面至少 0.83 单位。未来燃料随机位移/旋转后的完整包围盒必须仍在此体积内。火焰/火星应限制在燃烧室内部 X7.05～13.6、Y4.55～11.05、Z1.3～6.25，并留边距，不能穿过隔壁进入转子。

液体底面 Y2.5，最大表面 Y3.32，可见高度 0.82 单位；未来按填充比例限制在该范围，空槽不画液体。体积距玻璃背面 0.35、后壁 0.25 单位，完全在液槽及观察口投影内，避免玻璃共面闪烁。动态液体已按真实 Tank 在该体积内绘制。

## 左右流体接口与材质

按本轮补充要求新增左右接口，面中心 Y8/Z8，接口面板 Y6～10/Z6～10，左 X15.9～16，右 X0～0.1。保留原侧面检修缝和下方散热孔；后 FE 口及顶排气结构不变。

使用项目现有 `textures/block/machine_side_left_fluid.png` 和 `machine_side_right_fluid.png`；它们与用户提供的 Download 文件 SHA256 一致。中央 `[9,9]..[22,22]` 的 14×14 图案逐像素不变，外围以六像素宽的方形渐变混合到现有灰钢底色。图集槽位分别 `[96,96,128,128]` 和 `[128,96,160,128]`，没有修改共享机器原贴图。

左侧 Input / 右侧 Output 或 Drain **仅为后续规划和锚点语义**，当前未增加 tank、`IFluidHandler`、side-based IO、排空行为或管道兼容逻辑。

## 同步与验证

- 源：`src/main/blockbench/thermal_generator_3d.bbmodel`（362 cubes / 45 groups，含隐藏参考）。
- 图集：`src/main/resources/assets/apocalypse_firstlight/textures/block/thermal_generator_3d.png`，256×256。
- 静态：`src/main/resources/assets/apocalypse_firstlight/models/block/thermal_generator_3d.json`，342 cubes。Java block JSON 不承载命名锚点。
- 原方块八个朝向/运行状态使用 `models/block/thermal_generator_3d_world.json` 配合 BER；物品使用完整 `thermal_generator_3d_render.json`。机壳 solid、两块玻璃 translucent；GUI scale 0.624。粒子通过客户端 ticker 读取导出的锚点，非模型自身动画。
- Geo：`src/main/resources/assets/apocalypse_firstlight/geo/thermal_generator_3d.geo.json`，342 cubes / 39 bones，包含八个可供未来 renderer 查找的空 anchor bones。
- 局部编辑：`tools/add-thermal-fuel-windows.bb.js`；空项目构建：`tools/build-thermal-generator-3d.bb.js`；导出校验：`node tools/sync-thermal-generator-3d.mjs`。
- 校验包含源/Java/Geo 几何、UV、pivot/parent、右下通风口、转子共心、标准接口像素、玻璃留空、两个参考体积无静态遮挡、source-only 内容不进入 runtime。
- 新增结构没有轴对齐实体交叠；修改前已有侧底板 Y1.1 延伸与底座/角柱的 10 对内部相交，本轮保留，不以本任务扩大机壳修整范围。
- 原静态阶段仅检查 Blockbench 左右前方视角；后续动态显示验收见 Dynamic Renderer V1，锚点粒子验收见 Particles V1，不以静态阶段构建替代运行验证。
- `gradlew.bat build --offline` 已通过（2026-09-08）；`:test NO-SOURCE`，不代表运行了游戏测试。构建日志：`build/thermal-generator-fuel-windows-build.log`。
