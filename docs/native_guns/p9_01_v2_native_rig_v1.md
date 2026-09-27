# P9-01 V2 — AFL Native Rig + Pure Mesh（已接入运行时，客户端待验收）

状态（2026-09-27）：正式 Registry ID 仍为 `apocalypse_firstlight:p9_01`，Java 物品已切换到 `ConfiguredNativeGunItem`，运行时 Profile 指向 `p9_01_v2_native` Geo、动画、贴图、Display 与 Pure Mesh sidecar；玩家手臂走 `NativePlayerArmRenderer`。旧 P9 专用类及旧 Geo/动画/贴图/手持 Display 运行时资源已删除，旧 `.bbmodel` 只作历史源保留。V2 源有 10 条 clip，其中 9 条在 Profile 中启用；`first_draw` 仅作为资源存在，没有运行时触发。静态/离线结果不能代替客户端验证；尚未执行 `runClient` 或游戏内手部、ADS、附件、维护台、声音验收。

## 文件

| 用途 | 路径 |
|---|---|
| 正式可编辑源（Free Model） | `src/main/blockbench/p9_01_v2_native.bbmodel` |
| 几何 + UV + Base Color + LabPBR `_s` / `_n` 生成器（同一趟光栅） | `tools/build-p9-01-v2-mesh.mjs` |
| Rig 构建（直接运行生成器，把 Pure Mesh 挂到新 Rig，写出源贴图） | `tools/build-p9-01-v2-native.mjs` |
| 源贴图（Base Color，内嵌于 bbmodel，同时写成独立文件） | `src/main/blockbench/textures/p9_01_v2_native.png` |
| 源 PBR 贴图（LabPBR，不内嵌 bbmodel，与 Blackridge 相同） | `src/main/blockbench/textures/p9_01_v2_native_s.png`、`p9_01_v2_native_n.png` |
| 动画生成（设计节点 → 60 Hz 烘焙线性关键帧） | `tools/author-p9-01-v2-native-animations.mjs --write-source --write-runtime` |
| 运行时导出（geo / aflmesh / 贴图与 `_s` / `_n` / in_hand，`--check` 校验） | `tools/export-p9-01-v2-native.mjs` |
| 第一人称离线预览（游戏同一变换链 + 真实手臂盒，P9 / Blackridge 并排） | `tools/preview-fp-arms.mjs` |
| geo（仅骨骼，28 根） | `assets/apocalypse_firstlight/geo/p9_01_v2_native.geo.json` |
| 动画 | `assets/apocalypse_firstlight/animations/p9_01_v2_native.animation.json` |
| Pure Mesh sidecar（35 part，8248 三角形） | `assets/apocalypse_firstlight/meshes/p9_01_v2_native.aflmesh.json` |
| Base Color（V4 A 版近黑；源内嵌、独立源文件与运行时 PNG 逐字节一致） | `assets/apocalypse_firstlight/textures/item/p9_01_v2_native.png` |
| LabPBR 高光 / 法线（PBR V1，1024×1024 RGBA，与源文件逐字节一致） | `assets/apocalypse_firstlight/textures/item/p9_01_v2_native_s.png`、`p9_01_v2_native_n.png` |
| Display（builtin/entity） | `assets/apocalypse_firstlight/models/item/p9_01_v2_native_in_hand.json` |

重建顺序：`build` → `author --write-source --write-runtime` → `export`。`build` 重跑时保留源中已有动画。

## Rig 层级（Blockbench 源坐标：枪口 -Z，+X 抛壳侧；geo 按导出约定 pivot `[-x,y,z]`、rotation `[-rx,-ry,rz]`）

```
root [0,0,0]
├─ handling [0,3,2.06]                     枪 + 右手的共同控制节点；枢轴 = 右手握把上部
│  ├─ gun_body [0,0,0]                     frame_receiver / core / rail / trigger_guard / grip / magwell / controls
│  │  ├─ barrel [0,5.3752,-1.6]            barrel_tube / bore / hood / chamber_mouth / chamber / feed_ramp（固定，无倾转）
│  │  │  ├─ muzzle_anchor [0,5.3752,-7.633]   螺纹延伸段前端 crown；枪口装置后端面挂载平面
│  │  │  └─ chamber_round_anchor [0,5.3752,-0.44]   预留，无几何
│  │  └─ trigger [0,4.0432,-0.6026]        trigger_blade（静态）
│  ├─ slide [0,5.3752,0]                   套筒 10 part；+Z 为后坐方向（挂机 1.88）
│  │  ├─ front_sight [0,5.9672,-6.2266]
│  │  ├─ rear_sight [0,5.9672,2.9494]
│  │  ├─ ejection_anchor [0.8399,5.6416,-0.6618]
│  │  └─ sight_anchor [0,6.2928,-6.2266]
│  ├─ magazine [0,3.5992,1.647]            枪内正式弹匣（body + baseplate）
│  │  ├─ follower [0,3.5992,1.647]         托弹板；有弹时沿弹匣轴下压 0.45
│  │  └─ magazine_round_anchor [0,3.3938,1.8573] rot [-22,0,0]
│  └─ righthand [0,0,0]
│     └─ right_hand_anchor [0,3,2.06]      （right_arm_reference，仅编辑器）
├─ lefthand [0,0,0]                        独立于 handling
│  └─ lefthand_pos [0,0,0]                 每条 clip 一个恒定预旋转（避免欧拉奇点）
│     └─ left_hand_anchor [-1.5,4,0.5]     （left_arm_reference，仅编辑器）
├─ mag_out [0,3.5992,1.647]                左手操作的新弹匣 / 检视取出的弹匣
│  └─ reload_magazine                      可见性开关 + 复制的正式弹匣几何
│     └─ mag_out_round_anchor [0,3.3938,1.8573] rot [-22,0,0]
├─ empty_old_mag [0,3.5992,1.647]          换弹时被卸下的旧弹匣
│  └─ empty_old_mag_round_anchor [0,3.3938,1.8573] rot [-22,0,0]
├─ positioning [0,0,0]                     标准定位入口（空）
└─ maintenance_anchor [0,4.6,-1.6]
camera [0,12,18]                           顶层；仅作小角度动画跟随
```

- 三份弹匣共用同一正式几何：`magazine` 下为原件，`reload_magazine`、`empty_old_mag` 下为逐顶点 / UV 相同的副本（`mag_out_*`、`empty_old_mag_*`）。
- 所有弹药锚点均无可见几何，不含常驻假子弹。
- 参考臂是 `export:false` 的方块，尺寸等于 `NativePlayerArmRenderer` 在该 Display 缩放下实际绘制的原版手臂（半宽 2.34、长 17.66），不导出。
- 旧 Rig 中的 TACZ 残留不再保留：`refit`（idle / iron / refit_* 视角）、`positioning` 下的 `thirdperson_hand` / `ground` / `fixed`、`g19_and_mag` / `g17` / `lower`、`righthand_1` / `righthand_pos` / `lefthand_1` 补偿缩放链、`root` 的 -20.557° 展示侧倾。

## 几何与 UV

- Pure Mesh 与 Base Color（当前为 V4 A 版，见下文）由 `build-p9-01-v2-mesh.mjs` 生成，`build-p9-01-v2-native.mjs` 直接调用它，不再读取旧 `p9_01.bbmodel`（该文件停留在精修前的 11204 三角形版本，不再同步）。生成器本体 32 个 part、7556 三角形；Native 源另含换弹用的辅助弹匣副本，合计 35 个 Mesh、8248 三角形，常态第一人称可见 6864（2026-09-27 Threaded Muzzle Extension 之后；Geometry Performance Pass 后为 7076 / 7768 / 6384）。
- 贴图 UV 尺寸改为 1024。这是 Free Model 必需的：网格 UV 按 1024 像素编写，旧 GeckoLib 工程带的是项目分辨率 256。
- 拓扑调整：`build-p9-01-v2-native.mjs` 的 `splitWarped` 把扭曲超过 AFL 转换器容差（>10%）的四边面沿对角线拆成两个三角形，顶点和 UV 不变，渲染表面相同。
- sidecar 为满足运行时 4 MiB 字符上限（`AflMeshLoader.MAX_CHARACTERS`），数字行改为单行书写（数据与通用 `export-afl-mesh.mjs` 的 `serialize` 结果解析后完全相同，当前约 1.32 MB）。因此对这个文件应使用 `export-p9-01-v2-native.mjs --check` 校验，通用转换器的 `--check` 会因格式不同报 stale。

### 滑套前端噪点修复（2026-09-27）

用户指出滑套前端面（枪口一侧）有噪点和细线。本轮只改几何切分和明暗绘制，材质基色全部不变。

- 原因：
  - 枪口环形截面的采样角里有相距约 0.23° 的重复角，生成了亚像素级的细条面；
  - 旧绘制器对"细面"整面统一提亮或压暗，于是这些细条在贴图上变成散点和横线；
  - 15–22° 的倒角折线也被画成了虚线状高光。
- 几何：
  - `angleSet` 去重阈值由 0.004 rad 提高到 0.035 rad（约 2°），首尾相接处同样去重，细条面消失；
  - 生成器由 11204 三角形降到 10748 三角形。
  - 枪管罩底边由 5.02 降到 4.98（藏在滑套内、供弹坡块上方）。这样后端面在 r 0.335 膛室开口外保留完整边框，否则 0.03 倒角内缩后开口会穿出轮廓，AFL 转换器会拒绝这个自相交面。
- 绘制器：
  - 硬边只在 UV 岛边界上判定（相邻面法线点积 < 0.9，强度 `min(1, (1-d)/0.35)`）；
  - 凸棱提亮 1.5 px 线性衰减，凹角压暗 2.5 px 线性衰减，两者取最大值，不再逐面叠加；
  - 窄岛（面积 / 最大边长 < 2.5 px）整体用一个均匀值；
  - 拉丝纹只画在侧面和顶面（|nz| < 0.5），前后端面保持干净。
- 验证：
  - 贴图裁切对比；
  - Blockbench 离屏渲染：枪口近景两个角度，外加侧面、后 3/4、前 3/4 整枪视角；
  - `export-p9-01-v2-native.mjs --check` 通过。
  - 未进游戏。

### Surface Detail Pass（2026-09-27）

目标：补上"切进去"的层次（凹槽、台阶、凹面板），取代原先往外贴的凸条。本轮只改几何和明暗绘制。
- 材质基色全部不变。
- Rig、骨骼枢轴、锚点、动画设计值不变；动画按原数值重新烘焙写出。

**滑套**
- **锯齿**：删除原凸条 `slide_serrations`，改为两侧侧面切进去的闭口斜槽。
  - 前 4 条前倾：底部中心 z = -5.89 + 0.24·i，顶部前移 0.135。
  - 后 7 条后倾：底部中心 z = 1.715 + 0.24·i，顶部后移 0.153。
  - 槽宽 0.085（沿 Z），y 4.94–5.47，槽口倒角 0.012，深 0.045，槽壁拔模 0.006。
  - 侧面带孔面由 `flankPockets` / `pocket` / `polyHoles` 生成：每个孔通过最短可见连线并入外环后耳切三角化，只使用环上已有顶点，不产生 T 形接缝，三角化面积有断言校验。
- **前段下倒角台阶**：ZS = -6.25 之前，下缘倒角由 0.07 × 0.07 增大到宽 0.12、高 0.17，在 ZS 处形成台阶面。
- **采样**：`angleSet` 改为在 +X 半边去重后镜像到 -X 半边，两侧采样完全一致；滑套侧面平直段的两个端点作为必保留角度，锯齿条带的边界因此精确对齐。

**机架**
- **尾托**：加长并上翘，尖端 z 4.22、y 约 4.52–4.62（原尖端 3.92 / 4.44）。上缘全程低于滑套底面 y 4.80，滑套后坐 1.88 行程内不干涉。
- **两侧外侧面凹槽**（`prismXR` 新增 `holesLo` / `holesHi`，深 0.025，槽口倒角 0.01）：
  - 护木长槽：z -6.40 至 -3.30，y 4.175–4.245；
  - 护圈上方食指定位凹台：z -1.78 至 -0.84，y 4.12–4.34。

**握把**
- 两侧各一块下沉 0.025 的纹理面板：弹匣系 y -0.36 至 2.72，前缘角 -24.2°、后缘角 29.5°（约 z 0.98–2.62），四边都是直角台阶。
- 边界行和列各复制一份，焊接后自动收拢成台阶。
- 颗粒纹理只画在面标签为 `panel` 的面上；前握面、背带、握把顶部和底部外扩部分保持光面。

**控件**
- 空仓挂机杆拇指垫：加 3 条凸起防滑棱，位于 z 0.68 / 0.76 / 0.84。
- 弹匣释放钮：外加一圈聚合物护圈 `frame_mag_catch_bezel`（挂 `gun_body`）。外框 z 0.64–1.10、y 3.62–4.04，与按钮留 0.02 间隙，高出机匣侧面 0.02。

**弹匣底板**
- 下部唇边前伸到 z 0.56、后伸到 3.00（原 0.70 / 2.84），上沿通过一道小台阶收回底板。
- 换弹和检视用的辅助弹匣副本同步更新。

**绘制器**
- 凹陷结构周围不画贴图空间的边缘明暗：锯齿槽、护木槽、食指凹台、握把面板台阶和滑套前段台阶。
  - 这些轮廓大多斜穿贴图像素网格，按像素画出的高光或压暗在近看时会变成阶梯状锯齿。
  - 做法：凹陷面带面标签 `pocket`（槽口倒角、槽壁、槽底）或 `step`（握把面板台阶壁、滑套前段台阶面）。周围的大面和握把面板底面不再收到来自这些边的高光或压暗。
  - 凹陷本身仍然可读：窄的倒角、槽壁、槽底按整岛统一色调绘制（倒角提亮，槽底压暗），轮廓由几何边缘决定。
- 其他硬边规则不变。握把颗粒纹理只画在 `panel` 面上。
- 贴图密度由 28 降到 27 px/单位。

**数据**（该轮结束时；之后的性能优化见「Geometry Performance Pass」）
- 生成器：32 part、12508 三角形。
- Native 源和运行时：35 个 Mesh、14032 三角形。
- sidecar：约 2.41 MB（上限 4 MiB）。

**验证**
- Blockbench 离屏渲染：整枪左右两侧、后 3/4、前 3/4，以及锯齿、前段台阶、握把面板、护木槽和凹台的近景。
- 贴图裁切检查。
- `tools/preview-fp-arms.mjs` 第一人称预览：待机、空仓换弹、检视的关键帧。
- AFL 转换器接受全部面，`export-p9-01-v2-native.mjs --check` 通过。
- 未进游戏。

### V4 Base Color A 版：近黑（2026-09-27，待游戏内确认）

参照一张黑色 striker-fired 手枪产品照，把整枪压到接近黑色。部件之间靠表面质感和 1–2 个色阶的冷暖差区分，不靠明暗差。

- 本轮只改绘制器，模型和 UV 不变。
- 另有一个"整体提亮约 15%"的 B 版方案，尚未制作。
- A 版与 B 版的取舍需要在游戏内白天、夜晚和光影包下比较；Blockbench 预览偏暗，不作为依据。

| 材质 | 基色 sRGB | 边缘亮线 hl |
|---|---|---|
| 滑套 | 48,49,51（中性略冷） | 15 |
| 抛壳口 / 滑套内壁 | 26,26,27 | 5 |
| 弹底板 | 56,57,58 | 16 |
| 枪管 / 枪管罩 | 74,75,76 / 72,73,74 | 20 |
| 供弹坡 | 118,119,120 | 26 |
| 枪膛 / 膛室 / 击针孔 | 14,14,15 | 3 |
| 机架 / 护圈 / 导轨 | 42,41,41（略暖；导轨每面单一色调） | 12 / 12 / 0 |
| 握把光面 | 40,39,39 | 10 |
| 握把颗粒面板 | 基色 ×1.25，逐像素 ±5%、2 像素块 ±3%（约 50 ±4） | 10 |
| 弹匣井 | 18,18,18 | 4 |
| 控件 | 52,52,54 | 16 |
| 扳机 | 38,37,37 | 11 |
| 销钉 | 70,70,71 | 18 |
| 准星 / 照门 | 30,30,31（全枪最黑） | 10 |
| 弹匣本体 / 供弹唇 / 底板 | 44,44,45 / 56,56,57 / 40,39,39 | 13 / 16 / 12 |
| 托弹板 | 56,54,50 | 14 |

**规则调整**

- **朝向项**：由顶面 +6% / 底面 -4% 减为 +3% / -2%。
- **表面变化**：
  - 滑套拉丝减到约 ±0.8%，只在侧面和顶面；
  - 车削件 ±0.4%，弹匣 ±0.5%；
  - 聚合物件不加颗粒，因为近黑底色上的颗粒会读成脏点。
- **边缘明暗**：大面上的逐像素亮线和压暗只沿贴图像素轴向的边画（长倒角大多沿枪身方向）。斜向的边一律不画，避免阶梯锯齿。窄岛仍按整岛统一色调，不受这条影响。
- **凹陷轮廓**：沿用 Surface Detail Pass 的规则，不画。

**验证**
- Blockbench 离屏渲染（左侧整枪、左后 3/4、左前 3/4、握把近景、抛壳口），与参考照并排对比。
- `export-p9-01-v2-native.mjs --check` 通过。
- 未进游戏。

### Geometry Performance Pass（2026-09-27）

**起因**：实机测试（Sundial Lite 光影）下，空手 200 FPS，拿 Blackridge 120 FPS，拿 P9 70 FPS。
- 按帧耗时看，P9 多出约 9.3 ms，Blackridge 多出约 3.3 ms，比例与两者的常态可见三角面数（10984 对约 4500）基本一致。
- 原因是 AFL Pure Mesh 仍走 CPU 逐顶点提交：每帧每个三角面在 Java 中变换，并按退化四边形提交 4 个顶点。开光影后单个顶点更贵，枪在一帧里也不止画一次，所以开销和三角面数成正比。

**本轮做法**：只删玩家看不出来的几何，外形尺寸、Rig、锚点和动画都不变。

**原则**：
- 保留轮廓、主倒角、锯齿、抛壳口、瞄具和控件的外形；
- 降低过密的圆周采样、多段滚圆倒角、平直边上的冗余采样，以及平时看不见的内部精度；
- 微小表面细节以后交给贴图和法线贴图。

**降低密度的位置**（都在 `tools/build-p9-01-v2-mesh.mjs`）：

| 部位 | 之前 | 之后 |
|---|---|---|
| 滑套截面均匀采样（外形仍由拐角采样决定，枪口孔同步） | 24 | 16 |
| 滑套前、后滚圆倒角 | 4 圈 | 3 圈 |
| 枪口孔 | 带一圈倒角 | 直接接到孔边 |
| 弹底板、击针孔 | 40 段 | 20 段 |
| 枪管外管、枪膛 | 32 段 | 24 段（16 段在枪口极近景能看出折面，故取 24） |
| 膛线几何 | 有 | 去掉，只保留有深度的暗色孔 |
| 枪管罩、膛室、膛室口采样 | 32 段 | 16 段 + 拐角 |
| 枪管罩前端倒角（藏在滑套里）、后端倒角 | 多段滚圆 | 各一道斜角 |
| 供弹坡 | 8 截面 × 13 点 | 5 截面 × 9 点 |
| 机匣侧板、上沿、芯体、导轨、准星、照门、各控件的倒角和轮廓圆角 | 2 段 | 1 段（导轨凸台去掉 0.03 的轮廓小圆角，只保留倒角） |
| 护木槽、食指凹台的轮廓圆角 | 3 段 | 1 段（护木槽端部圆角半径 0.035 → 0.025，避免槽口外扩后出现交叉四边形） |
| 扳机护圈 | 路径平滑 2 次，截面圆角 2 段 | 平滑 1 次，截面圆角 1 段 |
| 扳机截面圆角 | 2 段 | 1 段 |
| 握把圆周 | 40 列 | 28 列（加 8 列面板边界列；距面板边界不到约 3° 的均匀列直接去掉，避免细条面） |
| 销钉 | 16 段 | 8 段 |
| 弹匣圆周 | 24 段 | 16 段 |
| 弹匣供弹唇 | 16 圈 | 11 圈（保留外肩、唇顶、内勾和贴住顶弹的下缘；与顶弹轴的最小间距仍为 0.2557） |
| 弹匣底板、托弹板 | 11 圈、5 圈 | 7 圈、4 圈 |

三个弹匣（枪内、`mag_out`、`empty_old_mag`）仍由同一套弹匣几何复制，同步降低。

**结果**（三角面）：

| 骨骼 | 之前 | 之后 |
|---|---|---|
| gun_body | 4832 | 2716 |
| slide | 2022 | 1580 |
| barrel | 1906 | 920 |
| magazine | 1288 | 568 |
| follower | 236 | 124 |
| trigger | 332 | 220 |
| rear_sight / front_sight | 276 / 92 | 196 / 60 |
| reload_magazine（mag_out） | 1524 | 692 |
| empty_old_mag | 1524 | 692 |
| **总计** | **14032** | **7768** |
| **常态第一人称可见**（总计减去两个隐藏的辅助弹匣） | **10984** | **6384**（−41.9%） |

- 生成器本体：12508 → 7076。
- sidecar：约 2.41 MB → 约 1.32 MB。
- 采用的手枪预算：常态可见 4500–6000 为目标，约 6500 为软上限，约 7000 为硬上限，均按三角面计。

**不变的部分**：
- Rig、骨骼枢轴、全部锚点、动画 JSON（逐字节相同）、geo 和 Display 文件；
- 材质基色和绘制规则。

**UV 重新生成**：P9 的 UV 由生成器自动展开、自动打包，几何一变图集布局就会整体重排，所以这次 UV 和贴图是按同一套规则重新生成的，贴图密度仍为 28 px/单位。目前没有手绘贴图，也还没有 `_s` / `_n` 贴图，所以没有资产因此失效。以后如果制作 LabPBR 贴图，应在几何定稿后再做，或者改为从生成器输出。

**工具**：旧 P9 的运行时文件已在迁移中删除，`tools/export-p9-01-v2-native.mjs` 改为只校验仍存在的旧文件。

**验证**：
- Blockbench 离屏渲染，优化前后同一机位对比：前 45°、后 45°、左侧整枪、枪口近景、滑套后坐锁定（抛壳口与膛室）、弹匣唇部与整只弹匣。枪口近景在改用 24 段后与原来一致，其余视角看不出差别。
- `tools/preview-fp-arms.mjs` 第一人称对比：待机、检视 0.7 秒、空仓换弹 0.62 秒，轮廓没有可见差别。
- AFL 转换器接受全部面，`export-p9-01-v2-native.mjs --check` 通过。
- 未进游戏，帧数变化需要实机复测。按上面的线性关系粗估，Sundial Lite 下 P9 约从 70 FPS 回到 95 FPS 左右，这只是估算。

### PBR V1：LabPBR `_s` / `_n`（2026-09-27，待实机验收）

**格式**（沿用 Blackridge PBR V1 的命名与运行时约定）：
- `textures/item/p9_01_v2_native_s.png` 与 `p9_01_v2_native_n.png`，1024×1024 RGBA，与 Base Color 同一套 UV。
- 运行时 Profile 绑定 `textures/item/p9_01_v2_native.png`，Oculus 按 LabPBR 命名在同目录查找 `_s` / `_n`。Java 未改。
- `_s` 通道：
  - R：感知光滑度；
  - G：F0。金属 255，F0 取 Base Color；聚合物 10，约 0.04；
  - B：0；
  - A：255，不发光，这一版不做夜光瞄具。
- `_n` 通道：
  - RG：切线空间法线 XY，OpenGL，+v 向上；
  - B：材质 AO；
  - A：255，不启用视差。

**生成方式**：两张图由 `tools/build-p9-01-v2-mesh.mjs` 在绘制 Base Color 的同一趟光栅里写出，与 Base Color 共用 UV 岛、部件材质分类、面标签和边缘判定。
- 以后几何或 UV 再变，三张图会一起重新生成，不需要手工同步。
- 法线的切线方向取每个面所在 UV 岛的 +u 方向，投影到面上。
- 构建脚本把两张图写到 `src/main/blockbench/textures/`，导出脚本复制到运行时；`export-p9-01-v2-native.mjs --check` 逐字节校验三张图。
- 设置 `P9_PBR_STATS=1` 运行生成器，可以按部件和面标签打印实际写出的数值。

**材质表**：R 为基础值，edge 为倒角或高接触区的值。实际像素在基础值上还有 ±5（金属）或 ±3（聚合物）的低频起伏，约 0.3 单位尺度，不是逐像素噪点。

| 材质 | 部件 | 类型（G） | R 基础 | R 倒角 / 边 | AO |
|---|---|---|---|---|---|
| 黑色氮化钢 | 滑套外表面 | 金属 255 | 125 | 160 | 255 |
| 锯齿槽内 | 滑套凹槽 | 金属 255 | 95 | — | 205 |
| 前段台阶面 | 滑套 | 金属 255 | 110 | — | 230 |
| 抛壳口内壁 | 滑套内腔 | 金属 255 | 80 | 90 | 175 |
| 弹底板 | 抛壳口内可见 | 金属 255 | 140 | 150 | 200 |
| 机加工钢 | 枪管外管、膛室口 | 金属 255 | 165 | 180（枪口冠至少 180） | 255 |
| 机加工钢 | 枪管罩 | 金属 255 | 165 | 175 | 215 |
| 抛光钢 | 供弹坡 | 金属 255 | 205 | 210 | 215 |
| 枪膛、膛室、击针孔 | 内腔 | 金属 255 | 70 | 70 | 150 |
| 深色枪钢 | 控件、抽壳钩、击针尾盖 | 金属 255 | 115 | 150 | 255 |
| 销钉 | | 金属 255 | 150 | 165 | 255 |
| 哑光瞄具 | 准星、照门 | 金属 255 | 55 | 70 | 255 |
| 磷化钢弹匣 | 弹匣本体 / 供弹唇 | 金属 255 | 100 / 135 | 125 / 150 | 255 |
| 深色聚合物 | 机架、护圈、导轨 | 聚合物 10 | 90 | 100（导轨 90） | 255 |
| 握把 | 光面边框 / 颗粒面板 / 台阶壁 | 聚合物 10 | 100 / 35 / 70 | 105 / — / — | 255 / 255 / 230 |
| 机架凹槽 | 护木槽、食指凹台 | 聚合物 10 | 80 | — | 215 |
| 弹匣井 | | 聚合物 10 | 60 | 60 | 150 |
| 扳机 | | 聚合物 10 | 110 | 120 | 255 |
| 弹匣底板 / 托弹板 | | 聚合物 10 | 85 / 70 | 95 / 70 | 255 / 190 |

**克制的磨亮**：
- 窄的凸倒角条整条使用 edge 值；大面上只在沿像素轴向的硬边旁 1.5 像素内过渡到 edge 值，斜向边不画，不会出现阶梯锯齿。
- 高接触区在基础值上加：
  - 滑套前后锯齿区的侧面 +12；
  - 空仓挂机杆拇指垫和弹匣释放钮外表面 +15；
  - 扳机指面 +10。
- 不改底色，不做掉漆、白边或刮痕。

**法线**：只用在两处，其余全部平整。
- **握把颗粒面板**：与底色颗粒同一套 2 像素格点，最大倾斜约 0.35。
- **枪膛**：6 条膛线的法线暗示，带轻微缠距，代替已删除的几何膛线。

滑套和机架大面不加拉丝法线，避免物品贴图没有 mipmap 时，第一人称移动中出现闪烁。

**AO**：只给内腔和凹槽，数值见上表。底色已经压暗过凹处，所以保持克制。

**实际输出核对**（`P9_PBR_STATS=1`）：
- 各部件 R 均值：滑套大面约 128，枪管约 167，供弹坡约 206，控件约 146（小件多为倒角窄条），机架约 92，握把面板约 35，弹匣本体约 109。
- G 只有 255 与 10 两种值。
- 法线像素只出现在握把面板（10,649 像素）与枪膛（902 像素）。

**不变的部分**：
- 几何、UV、Rig、动画、Java、Hybrid Mesh V2；
- sidecar、geo、动画 JSON 与本轮开始时逐字节相同；
- Base Color 像素完全相同，PNG 按标准编码器重新写出。

**验证**：
- 生成器、构建、导出和 `--check` 通过；
- Blockbench 中把 `_s` 当作贴图查看，确认金属与聚合物分区落在正确部件上；
- 未进游戏，需要在 Complementary 与 Sundial Lite 下实机验收；
- 法线绿通道的方向沿用 Blackridge 的 OpenGL 约定，Blackridge 的 `_n` 也尚未实机确认。如果实机发现握把颗粒的明暗上下颠倒，两把枪应一起翻转 G 通道。

### Threaded Muzzle Extension（2026-09-27，待实机验收）

为 AFL 通用 9mm 消音器（[`pistol_suppressor_01` Model V2](attachments/pistol_suppressor_01_model_v1.md)）提供枪械侧的外露螺纹枪管。螺纹属于 P9，不烘焙进通用消音器。

- 位置：`barrel_tube` 在原 crown 平面（z −7.233）之前加长 `THREAD_L = 0.40`。
  - 原枪管外径 r 0.35 在 z −7.213 结束，由 45° 小肩部倒角收到螺纹大径 r 0.33。
  - 其后是 5 个 V 形牙顶（r 0.33）和牙底（r 0.305），节距 0.074，是按 1/2x28 外观做的风格化粗牙距。
  - 前端 0.03 倒角后接凹入式 crown（r 0.30 → 0.26 → 内孔 0.22），crown 平面为 z **−7.633**。
- `muzzle_anchor` 同步移到 `[0,5.3752,-7.633]`，仍为 barrel 的静态子骨骼，位于枪膛轴线上。契约：锚点 = 枪口 crown 中心，枪口装置的后端面挂在这里。
  - 裸枪的枪口焰、弹道视觉起点和维护台 MUZZLE 热点随之前移 0.40。
  - 装消音器后，套筒前端到消音器之间可以看到约 0.52 的枪管加螺纹。
- 常量：`tools/build-p9-01-v2-mesh.mjs` 的 `THREAD_L` / `MUZZLE_Z = -7.233 - THREAD_L`，以及 `tools/build-p9-01-v2-native.mjs` 的 `MUZZLE_Z = -7.633`，两处须保持一致。
  - 内孔 `barrel_bore` 仍从新 crown 向后 1.10 封底。
  - 画笔中与 crown 相关的规则都跟随新 `MUZZLE_Z`：crown 平滑度 ≥ 180、内孔越深越暗、膛线相位。
- 材质：沿用现有 `M.barrel` 钢，Base Color 74,75,76 系，平滑度 165，窄条倒角为 180。不新增材质、法线细节或 Java 逻辑。
- 面数：`barrel_tube` 240 → 720 三角面（24 段 × 15 环带）。Native 合计 7768 → 8248，常态可见 6384 → 6864。装上消音器（1440）后约 8300，超出约 7000 的手枪预算参考线；用户已确认这是 guideline，优先保证第一人称圆柱轮廓。
- 变化范围：Geo 只改了 `muzzle_anchor` pivot 的 Z，动画 JSON 与 in_hand Display 字节不变。1024 atlas 重新打包，新增的螺纹小岛使 Base Color、`_s`、`_n` 整体重排，材质表未变。
- 验证：`export-p9-01-v2-native.mjs --check` 通过；Blockbench 离屏渲染确认螺纹侧面轮廓、crown 和消音器同轴贴合。未做游戏内验收。

## 握姿与第一人称构图

- 握姿与 Blackridge 统一（AFL 手部约定：锚点 = 手末端，前臂沿局部 -Y，掌心 +Z）。
  - 右手：握把位于手中，手顶在套筒 / 尾托下方（y 4.45），指节在上前握面（z 0.95），前臂水平向后。
  - 左手：沿用 Blackridge 的校准，按手的尺寸比例缩放，从左侧斜贴右手。
- Display：`firstperson_*` 平移 `[3.25, -6.46691, -11.9885]`、缩放 0.53、无旋转。
  - 平移的算法：让 P9 右手锚点在视角中与 Blackridge 右手锚点完全重合，所以两把手枪的手臂位置和角度相同。
  - 缩放 0.53 使手宽约为 88 mm（1 单位约 18.8 mm），P9 在画面中约为 Blackridge 的 0.8 倍，与 9 mm 手枪和 .50 大手枪的真实比例一致。旧 P9 的专用平移 `COMPOSITION_X/Y` 不再需要。
  - 第三人称、GUI、地面、展示框：按相同比例（0.53 / 0.45）缩放 Blackridge 的数值，未做实机校准。

## 动画（10 条，MW 风格手感重做，2026-09-27）

参考用户提供的 MWII X12（Glock 17 类）实机视频学习节奏与构图，关键帧为原创设计：抬枪到画面中央偏右、枪口朝左上、每个机械动作有一下顿挫。待机保持双手（换弹 / 检视开始时左手离开，结束时回到握把）。

刚性手臂约束：原版手臂是没有手腕的长方块，握把完全包在拳头方块里。换弹和检视时，枪沿自身坐标向右手左侧滑出最小距离（手宽一半 + 握把宽一半，约 3.1 单位），右手反向补偿保持不动，弹匣井才能露在右前臂外。已验证仅沿握把轴下退时，弹匣井和入匣过程会被前臂挡住。翻转类姿态（验膛、看抛壳口、first_draw 上膛）以绕前臂轴的翻滚为主，并加 12–16° 上仰抵消抬高，否则刚性前臂的近端会横穿画面。

| clip | 时长 (s) | loop | handling | 内容 | 音效标记 |
|---|---|---|---|---|---|
| static_idle | 0.25 | hold | – | 标准双手握姿；有弹托弹板 | – |
| empty_idle | 0.25 | loop | – | 套筒挂机 1.88，托弹板在顶 | – |
| shoot | 0.3 | once | 是 | 套筒 0→1.7→0，轻型 9 mm 短促上跳；camera 由运行时后坐控制 | – |
| draw | 0.5 | once | 是 | 画面外右下方带内倾升起，0.22 s 到位后小幅过冲；左手 0.12–0.3 s 搭上 | 0.0 draw |
| put_away | 0.5 | once | 是 | 左手 0.2 s 内先离开，枪内倾落出画面 | 0.0 put_away |
| first_draw | 1.2 | once | 是 | 首次装备 / 空膛：升到胸前偏左、绕前臂内翻 44°，左手从左侧反手上膛（0.46–0.62 s），再回双手待机。**需 Java 触发** | 0.0 draw，0.45 slide_back，0.615 slide_release |
| reload_tactical | 1.7 | once | 是 | 左手立即离开；枪抬到中央、枪口左上；拇指退匣，旧匣靠重力掉落；新匣从左下方送入并推入大半，左手让开后 0.86 s 掌根拍到位；1.0–1.28 s 回正 | 0.23 magazine_release，0.26 magazine_out，0.585 magazine_in，0.855 magazine_seat |
| reload_empty | 2.0 | once | 是 | 同样抬枪后手腕猛地上甩（约 0.3 s），空匣在上甩途中沿弹匣轴脱出、带着枪的速度向前左方甩飞并翻滚落下；新匣 0.9 s 拍到位；左手从左侧反手拉滑套（1.12–1.26 s 拉离挂机后松开复进）；回正 | 0.25 magazine_release，0.32 magazine_out，0.625 magazine_in，0.895 magazine_seat，1.11 slide_back，1.255 slide_release |
| inspect | 5.4 | once | 是 | CoD 式：手腕内翻，左手捏套筒前部两下短拉（0.35，各 0.16 s 拉开、停 0.08 s、0.1 s 手控送回）加一下深拉（0.75，0.22 s 拉开、停约 0.45 s 观察、0.12 s 送回）；转到左侧，弹匣滑入左手，翻转展示供弹唇和顶弹，两段式插回（4.2 s 拍到位）；左手让开后 4.56 s 补拍弹匣底板收尾；回正 | 0.51 / 0.93 / 1.35 slide_back，0.845 / 1.265 / 2.135 slide_release（对准回到位），2.61 magazine_release，2.65 magazine_out，3.925 magazine_in，4.195 magazine_seat，4.555 magazine_seat（补拍） |
| inspect_empty | 5.3 | once | 是 | 挂机状态下先绕前臂内翻看敞开的抛壳口和膛室；转回左侧，退出空匣展示托弹板，两段式插回（3.6 s 拍到位）；左手从左侧反手拉一下（0.16 s 从挂机位再拉 0.3），松手后滑套被空匣托弹板重新挂住（结束仍为挂机，与 empty_idle 衔接）；4.46 s 补拍弹匣底板；回正 | 1.99 magazine_release，2.03 magazine_out，3.325 magazine_in，3.595 magazine_seat，3.85 slide_back，4.093 slide_release，4.455 magazine_seat（补拍） |

- 未用到辅助弹匣的 clip 都显式把 `reload_magazine` / `empty_old_mag` 缩放为 0，不依赖 idle 层。
- 每条 clip 都带右手、左手轨道；左手每条 clip 在 `lefthand_pos` 上取一个恒定预旋转避免欧拉奇点（左手最终姿态不受影响），逐帧最大角度跳变 ≤ 6°。
- 首尾衔接：除 draw / first_draw 的起点与 put_away 的终点外，所有动作首尾与待机姿态的偏差 ≤ 0.1（辅助弹匣首尾为隐藏状态，不计）。空仓换弹开头托弹板为空匣位置，换匣后为有弹位置。

### 音效（2026-09-27 安装）

用户新生成的音效已放入 `sounds/weapons/p9_01/`（OGG 48 kHz 立体声；`E:\Download` 原件未改）：

- 覆盖旧文件：`p9_01_fire`、`p9_01_suppressed`（开火，暂无变体，由服务端射击路径播放，不在动画中）、`p9_01_draw`、`p9_01_put_away`。
- 新增：`p9_01_magazine_release`、`p9_01_magazine_seat`、`p9_01_slide_back`、`p9_01_slide_release`、`p9_01_foley_raise`、`p9_01_foley_lower`，并已在 `sounds.json` 登记同名事件。
- `foley_raise` / `foley_lower` 的动画标记已按用户要求移除（听感不理想，待重新处理）；两个文件与 `sounds.json` 登记保留，当前任何 clip 都不播放它们。
- 处理：`draw` 剪掉开头 0.20 s 淡入空白，`foley_raise` 剪掉开头 0.09 s 并提高 9 dB（峰值约 -7 dB），`magazine_seat` 剪掉开头 0.08 s 静音；三者加 3 ms 淡入。其余原样复制。
- 插匣为两段式：左手把弹匣推入大半（距到位 0.9），掌根向下让开约 1.1，再在 `seat` 时刻上拍到位（枪同时一震）。音效分两层：旧 `p9_01_magazine_in` 从到位前 0.275 s 开始播放，前段摩擦声对应弹匣进井，文件内 0.28 s 处自带的卡扣声恰好与到位重合；新 `p9_01_magazine_seat` 在到位前 0.005 s 播放，两者叠成一次拍到位的声音，不会先后响两次。`p9_01_magazine_out`（旧）继续用于脱匣；旧 `p9_01_slide_action`、整段 `p9_01_inspect` 本版不用。
- 动画 marker 在 `NativeGunAnimations` 中按名字从 Forge 音效注册表取事件；新增的正式 SoundEvent 已在 `AflSounds` 中注册，声音的实际播放/时机仍待客户端验收。
- 现行 Java 切枪时只播放 put_away 中 tick ≤ 1 的标记，put_away 音效放在 0 秒。


## 验证范围

- `tools/preview-fp-arms.mjs` 按游戏变换链（Display → 骨骼矩阵 → 手臂规范化与 0.62 / 0.78 / 0.62 展示缩放 → 原版 4×12×4 手臂）离线渲染。已检查：
  - 待机；
  - 两种换弹的开始、侧移、出匣、入匣、滑套、回位；
  - 两种检视的关键姿态；
  - draw、put_away、shoot。

  换弹和检视期间弹匣井始终露在右前臂之外，左手取匣和送匣路径清晰。
- Blockbench 已打开新源文件，确认 V3 贴图对齐、参考臂与握把的关系、动画可播放。
- 运行时动画用到的骨骼全部存在于 geo，时间轴有序、没有超出时长。当前 `tools/export-p9-01-v2-native.mjs --check` 的 Geo/Mesh/Display 对照匹配，但贴图字节比较会报 stale：源内嵌 PNG 与运行时 PNG 编码不同，解码 RGBA 像素相同。这是导出检查的字节一致性限制，不是已知视觉差异。
- V2 切换尚未进行客户端实机验收：手臂接触、ADS、第三人称、维护台、附件、声音时机都需要人工确认。此前离线预览不等于游戏内 PASS。

# Runtime Contract（2026-09-27 已切换；以下旧骨骼映射供历史审计）

**OLD_P9_RUNTIME（历史，已退休）**：`P901Item` + `P901Renderer`（`NativeGunRig("gun","right_hand_anchor","left_hand_anchor","root")`）+ `P901HandLayer`（ADS 时收缩手臂）+ `P901FirstPerson`（`COMPOSITION_X/Y` 平移）+ `P901SightLayer` + `P901AnimationController`；`P901Renderer` 中写死了 `g19_and_mag` 与 `empty_old_magazine`；资源为旧 `p9_01.*`。

**CURRENT_RUNTIME**：`ConfiguredNativeGunItem` + `NativeAnimatedWeaponRenderer` + `NativePlayerArmRenderer` + AFL Hybrid Mesh；`AflItems.P9_01` 保留正式物品 ID，Profile 的资源 ID 为 `p9_01_v2_native`，骨骼锚点为 `right_hand_anchor`、`left_hand_anchor`、`muzzle_anchor`、`ejection_anchor`。

**CURRENT_RESOURCE_PATHS**：geo `geo/p9_01_v2_native.geo.json`；animation `animations/p9_01_v2_native.animation.json`；aflmesh `meshes/p9_01_v2_native.aflmesh.json`；texture `textures/item/p9_01_v2_native.png`（LabPBR `_s` / `_n` 同目录，Oculus 按命名自动查找）；display `models/item/p9_01_v2_native_in_hand.json`。`models/item/p9_01.json` 的手持路径指向 V2 Display；旧 `p9_01` Geo/动画/贴图/手持 Display 已从运行时资源中删除。

**NEW_ASSET_BONES**：root、handling、gun_body、barrel、muzzle_anchor、chamber_round_anchor、trigger、slide、front_sight、rear_sight、ejection_anchor、sight_anchor、magazine、follower、magazine_round_anchor、righthand、right_hand_anchor、lefthand、lefthand_pos、left_hand_anchor、mag_out、reload_magazine、mag_out_round_anchor、empty_old_mag、empty_old_mag_round_anchor、positioning、maintenance_anchor、camera。

**OLD_TO_NEW_BONE_MAP**

| 旧 | 新 |
|---|---|
| root（带 -20.557° 展示侧倾） | root（无侧倾） |
| handling（旧 Rig 临时加的） | handling |
| g19_and_mag | 整体运动 → handling；静态结构 → gun_body（非第一人称尺寸兼容所挂的骨骼需改为 handling 或 root） |
| g17 / lower / gun / frame | gun_body（`NativeGunRig.gunModelRoot` 原为 `gun`） |
| barrel4（固定视觉） | barrel；旧 barrel / barrel2 失效轨道不再存在 |
| trigger2 | trigger |
| slide / slide2 / slide3 / slide_1 | slide |
| front_sight / rear_sight / muzzle_anchor / ejection_anchor / sight_anchor | 同名（父级改为 slide 或 barrel） |
| magazine（及其 `2` 子组） | magazine |
| empty_old_magazine（及其 `1` 子组，实际含义为新匣） | mag_out → reload_magazine（新匣）；empty_old_mag（被卸下的旧匣） |
| magazine_round_anchor / empty_old_magazine_round_anchor | magazine_round_anchor / mag_out_round_anchor / empty_old_mag_round_anchor |
| righthand → righthand_1 → righthand_pos → right_hand_anchor | righthand → right_hand_anchor |
| lefthand → lefthand_1 → lefthand_pos → left_hand_anchor | lefthand → lefthand_pos → left_hand_anchor |
| camera | camera |
| refit / idle_view / iron_view / refit_* / thirdperson_hand / ground / fixed | 删除（TACZ 残留） |
| — | maintenance_anchor、chamber_round_anchor、follower（新增） |

**clip 名**：动画资源共 10 条：static_idle、empty_idle、shoot、draw、put_away、first_draw、reload_tactical、reload_empty、inspect、inspect_empty。Profile 当前启用除 `first_draw` 外的九条；`first_draw` 没有触发条件。P9 0 发时由通用 `baseline` 选择 `empty_idle`，BR51 等 `static_bolt_caught` 由可选 `empty_state` 处理。

**当前配置与待验收**：
- 换弹时长：tactical 1.7 s / 34 tick，empty 2.0 s / 40 tick（旧版 2.38 / 3.12）；`presentation.mag_in_tick=18`、`empty_mag_in_tick=18`。动画的 `magazine_seat` cue 在约 0.855 / 0.895 秒，实际音画及弹数结算仍待客户端验收。
- 新音效事件已注册，`foley_raise` / `foley_lower` 无当前动画 marker；音效播放尚未实机验收。
- ADS：V2 Display 无旋转、缩放 0.53；JSON 的 `aim=[0,5.96718,2.94939]`、`eye_relief=0.47`、`hip_translation=[3.25,-6.46691,-11.9885]` 已按新照门数值重设，视觉待验证。
- 第三人称、GUI、地面、展示框的缩放未经实机校准。
- 右手锚点带展示缩放后的手臂盒，新 Rig 不再需要 `P901HandLayer` 的 ADS 缩臂或 `P901FirstPerson` 的平移。

**DYNAMIC_AMMO_READY** = MAGAZINE_ROUND_RUNTIME_INTEGRATED_UNVERIFIED（2026-09-27；三个弹匣顶弹锚点接入通用 Native Magazine Round 渲染，客户端修正后画面待用户验收；`chamber_round_anchor` 仍不用于动态膛内弹）。`native_guns/p9_01.json` 复用正式 `geo/9x19mm_round.geo.json` + `9x19mm_ammo_v1.png`，枪内顶弹以 `NativeGunAmmo.read > 0` 判定；新匣、旧匣同时受动作语义与 helper 骨骼可见性/零尺度控制。`inspect_empty` 和空仓换弹的旧匣不画顶弹。修正局部姿态后 `compileJava --offline` 成功，但任务为 `UP-TO-DATE`，没有重新执行 Java 编译。

  三个源锚点继续保持 pivot `[0,3.39381,1.85725]`、rotation `[-22,0,0]`，Rig/动画未改。Geo JSON 虽导出为 X `+22°`，GeckoLib 烘焙骨骼时会再取反，运行时锚点为 `-22°`。配置中的 `local_offset=[0,-0.000036,0.789958]`（Blockbench 单位）和 `local_rotation=[-90,0,0]` 把有效姿态对齐弹底 pivot `[0,3.6897,2.5897]`、rotation `[-112,0,0]`。先前误按 `+22°` 计算的 `local_offset=[0,0.548725,0.568273]` / `local_rotation=[90,0,0]` 会让三处顶弹反向并偏离供弹唇，已废弃。这只影响动态顶弹，不改变共享 9mm 模型或静态物品。膛室深约 0.85，短于 9mm 弹壳长 1.019，本轮不显示膛内弹。
**ATTACHMENT_READY** = UNVERIFIED。MUZZLE：`pistol_suppressor_01` Model V2（通用 9mm Pure Mesh）经 `NativeMuzzleRendering` 的 Hybrid Mesh 分支挂在新 `muzzle_anchor` `[0,5.3752,-7.633]`，只通过 compileJava 与离线校验。sight / magazine 仍为旧配置，未对 V2 重新适配，不可据此断言可用。
**MAINTENANCE_READY** = ASSET_ANCHOR_ONLY（maintenance_anchor 已建立；具体维护台取景未校准）
