# 手枪消音器 / Pistol Suppressor — Model V2（AFL 通用 9mm，Pure Mesh）

Asset ID：`pistol_suppressor_01`（Registry ID 未变）。2026-09-27 起为 **AFL 通用 9mm 手枪消音器**：圆柱分段式 Pure Mesh，运行时走 AFL Hybrid Mesh（`.aflmesh` V2 → `AflHybridMeshRendering` / `AflMeshRenderer`），带 Base Color 与 LabPBR `_s` / `_n`。装备、声音、噪声等运行行为见 [可装备 V1](pistol_suppressor_01_v1.md)；本文件只说明资产。

**已废弃**：V1 八边形 57 cube GeckoLib 资产（长 9.1、`muzzle_exit_anchor` `(0,0,-9.1)`、64×128 贴图）已被本版整体替换，可在 git 历史取回。`src/dev/pistol-suppressor-model.js` 是生成 V1 的 Blockbench 脚本，仅作历史参考，不要再运行。V1 文档里的 P9 锚点 `(-2.98,10.35,-4.72)` 属于更早的 P9 V1 Rig，早已失效。

## 资源

| 用途 | 路径 |
|---|---|
| 生成器（几何、UV、Base Color、`_s`、`_n` 同一趟光栅；`--check` 逐字节校验全部输出） | `tools/build-pistol-suppressor-01.mjs` |
| 共用车床生成库（与 9mm / .50 AE 弹药共用，原 `tools/lathe-ammo-lib.mjs` 改名） | `tools/lathe-mesh-lib.mjs` |
| 可编辑源（Free Model，3 个 Mesh，内嵌 Base Color） | `src/main/blockbench/pistol_suppressor_01.bbmodel` |
| 源贴图（512×512 RGBA；`_s` / `_n` 不内嵌 bbmodel，与 P9 相同） | `src/main/blockbench/textures/pistol_suppressor_01.png`、`_s.png`、`_n.png` |
| Geo（仅骨骼，identifier `geometry.pistol_suppressor_01`，512×512） | `assets/apocalypse_firstlight/geo/pistol_suppressor_01.geo.json` |
| Pure Mesh sidecar（V2，紧凑书写，约 217 KB） | `assets/apocalypse_firstlight/meshes/pistol_suppressor_01.aflmesh.json` |
| 运行时贴图（与源文件逐字节一致；Oculus 按命名自动查找 `_s` / `_n`） | `assets/apocalypse_firstlight/textures/item/pistol_suppressor_01.png`、`_s.png`、`_n.png` |
| 物品模型（`builtin/entity`；GUI 缩放 1.4 → 1.6，补偿变短的模型） | `assets/apocalypse_firstlight/models/item/pistol_suppressor_01.json` |

重建：`node tools/build-pistol-suppressor-01.mjs`；校验：加 `--check`。

## 挂载契约（与枪无关，资产缩放 1.0）

- 骨骼 `pistol_suppressor_root`，pivot `(0,0,0)` = **后端安装面中心**；无旋转；前方为 local **−Z**。三个 Mesh 都挂在这根骨骼上。
- 子骨骼 `muzzle_exit_anchor`，pivot `(0,0,-7.85)` = 前端面实际中心，供出口烟雾与弹道视觉起点使用（`NativeMuzzleRendering.applyExit`）。
- 资产不含任何枪专属枪管或螺纹。枪械把 `muzzle_slot.anchor` 放在**自己枪管（螺纹段）前端 crown 平面的枪膛轴线上**，消音器后端面与之重合。后端安装孔半径 0.34、深 0.30（暗色内螺纹孔），螺纹外径不超过 0.34 的枪管端头看起来都像拧进孔里。
- 当前唯一兼容枪 P9-01：`muzzle_anchor` = `[0,5.3752,-7.633]`，即 P9 螺纹延伸段前端（见 [P9 V2 Rig](../p9_01_v2_native_rig_v1.md) 的 Threaded Muzzle Extension）。其他 9mm 手枪接入时只需在数据里声明 `accepts` 并把锚点放到自己的枪口 crown，不改本资产。

## 几何

单位为 Blockbench 模型单位（沿用 P9 比例，1 单位约 18.8 mm）。车床轮廓绕轴一周 20 段，贴图接缝在正下方（−Y）。

| 段 | 轴向范围（前向距离） | 半径 | 细节 |
|---|---|---|---|
| 安装面 / 安装孔 | 0 | 孔 0.34，面 0.34–0.62 | 平整的机加工安装面；孔深 0.30，孔底暗色 |
| Booster / 连接座 | 0–0.90 | 0.66 | 后缘 0.04 倒角；4 道矩形环槽（宽 0.055、深 0.035、间距 0.13，首槽起于 0.2275）；前缘倒角收到颈部 |
| 颈部 | 0.90–1.00 | 0.60 | 凹入分隔环 |
| 主筒 | 1.00–7.50 | 0.625 | 后缘 0.025 倒角；5.20 处一道浅 V 模块分段线（深 0.012） |
| 前盖 | 7.50–7.85 | 0.625 | 与主筒之间一道浅 V 接缝；前缘 r 0.06 滚圆（2 段）；平端面 |
| 出口 | 7.85 | 0.20 | 0.05 沉头倒角，孔深 0.50 后以暗色平底封口 |

总长 7.85，最大直径 1.32（booster）。三角面（按三角形计）1440：`suppressor_booster` 900、`suppressor_tube` 240、`suppressor_front_cap` 300；V2 保留 300 个四边形 + 840 个三角形，共 1140 个提交面。圆柱段是四边形，倒角 / 环面 / 端面因为条带 UV 不是仿射映射，按导出器规则拆成两个三角形。

## 材质（Base Color + LabPBR）

UV：每个 Mesh 一条展开条带（角度 → u，轮廓弧长 → v），61 texel/单位。条带宽度取 20 的整数倍，所以每个棱面的边界都落在 texel 边界上。每个区段的颜色都沿 texel 行变化，不会出现斜向锯齿。光栅化采用 `noOverdraw`：先只画 texel 中心落在面内的像素，半像素容差只用于补空，所以细倒角三角形的尖角不会在相邻区段留下单点噪点。

| 区域 | Base Color | 平滑度（`_s` R） | F0（`_s` G） | AO（`_n` B） |
|---|---|---|---|---|
| Booster 外圆（深色机加工钢） | 56,57,60 | 140 | 255（金属） | 255 |
| 后缘 / 前缘倒角 | 74,75,78 / 68,69,72 | 165 / 155 | 255 | 255 |
| 环槽侧壁 / 槽底 | 44,45,47 / 36,37,39 | 120 / 100 | 255 | 215 / 195 |
| 安装面 | 60,61,64 | 150 | 255 | 255 |
| 安装孔壁 / 孔底 | 30,30,32 / 14,14,15 | 90 / 50 | 255 | 170 / 110 |
| 颈部 | 28,29,31 | 95 | 255 | 175 |
| 主筒（哑光黑涂层，介电） | 36,37,39 | 70 | 10 | 255 |
| 主筒后倒角 / 分段线与前盖接缝 | 42,43,45 / 27,28,30 | 78 / 62 | 10 | 225 / 215 |
| 前盖 / 前缘滚圆 / 端面 | 37,38,40 / 46,47,50 / 36,37,39 | 72 / 84 / 68 | 10 | 255 |
| 出口积碳（端面 r < 0.42 渐变） | 向 22,21,20 混合 60 % | 降至 40 | 10 | 255 |
| 出口倒角 / 内孔 / 孔底（暗色粗糙） | 24,23,22 / 14,14,15（越深越暗）/ 8,8,9 | 45 / 40 / 30 | 10 | 200 / 140 / 100 |

- Base Color 只带与 P9 相同的微弱顶光项，以及几乎不可见的低频差异（钢件逐行 ±0.6 % 车削纹、涂层 ±1 % 低频斑驳），不烘焙高光，没有随机噪点。
- `_s`：B = 0，A = 255（无自发光）；平滑度叠加 ±5（金属）/ ±3（涂层）低频值噪声，没有逐像素变化。
- `_n` RG：只有一种用途，就是把 20 个平面棱面在光影下按真实圆柱着色。R = 128 + 127·n_r·sin(θ − θ_棱面中线)，G = 128。n_r 是轮廓法线的径向分量，所以平环面、端面保持 128。切线方向 = u 增大方向 = 车床角度增大方向（LabPBR / OpenGL 约定）。这个方向**尚未在光影中实机确认**：如果 Complementary / Sundial Lite 下棱面反而更明显，把生成器 `pbr()` 中 `nx` 的符号取反即可。原版渲染不读 `_n`，照常显示 20 个棱面。
- 不做高频法线细节。微小加工痕迹只体现在区段平滑度差异上（倒角更亮、槽底更脏）。

## 预览与验证

- Blockbench 离屏渲染（临时副本，未保存）：单体侧视、后 3/4、booster 特写；P9 挂载（消音器放在 `muzzle_anchor`）的侧视、后 3/4、接口特写、前 3/4；P9 裸枪螺纹侧视和枪口正视。只检查了 Blockbench 默认光照下的 Base Color，没有做 PBR 预览。
- 贴图放大检查：条带无单点噪点，也没有斜向锯齿（启用 `noOverdraw` 之前的试验版在倒角角点有单像素点，已修复）。
- `node tools/build-pistol-suppressor-01.mjs --check`：CHECK OK。共用库改名后，9mm / .50 AE 两个弹药生成器的 `--check` 仍逐字节一致。
- `gradlew compileJava --offline` 通过。**未运行 runClient，未做游戏内验收**（手持、维护台、掉落物、GUI、出口烟雾、光影下 `_s` / `_n`），由用户实机测试。
