# 光影和 PBR 调整 V1（测量记录）

状态（2026-10-09）：**地面四种材质的整面细纹已改柔（人行道、路面、钢筋混凝土、路沿），贴图已重新生成，还没进游戏看**。只改了贴图，不用编译，游戏里 F3+T 重载资源就能看到。

## 起因

用户 2026-10-09 在 A1 店门口对比两个光影（截图）：
- Sundial Lite v1.2.0（整体偏黄）："锐化是不是太高了"。人行道斜着看有一道道像木纹的硬条纹，店门口钢筋混凝土地面俯视有一层起伏。
- Complementary Reimagined r5.9："总体更柔和，但是 PBR 效果没有那么好"。
- 问："我们可以调 PBR 效果吗"。

项目的光影支持原则见记忆 / 开发约定：以 Sundial 为准调，原版要好看，其他光影只要不坏。

## 量到的

### 我们的贴图（LabPBR `_n`：RG 法线、B 环境光遮蔽、A 高度）

| 贴图 | 法线平均偏角 | 最大 | 高度 |
|---|---|---|---|
| `ground/concrete_sidewalk_plain_n` | 1° | 5° | 255（全平） |
| `ground/concrete_sidewalk_corner_n` | 1.9° | 38°（路沿倒角） | 245..255 |
| `ground/concrete_pavement_plain_n` | 1° | 4° | 255 |
| `reinforced_concrete_n`（及 `_1`、`_2`） | 1–1.2° | 19° | 252..255 |
| `asphalt_n` / `asphalt_1_n` | 3° | 9° | 255 |
| `facade_masonry/ground_face_block_0_n` | 2° | 20° | 226..255 |
| `ground/porcelain_floor_tile_0_n` | 1° | 17° | 253..255 |
| `roof_tpo_top_n` | 0° | 0° | 255 |

地面类的法线都很弱，高度几乎是平的。底色贴图：人行道有很淡的横向扫纹（扫帚面），混凝土是很淡的斑驳和小点，对比度都很低。

### 光影设置（`run/shaderpacks/*.txt`，只记录改过默认值的项）

- Sundial：
  - `FINAL_SHARPENING=false`：最终锐化是关的，不是"锐化太高"。
  - `ANISOTROPIC_FILTERING_QUALITY=16`：用户改的，默认 4。各向异性过滤越高，斜着看地面时细纹越不被模糊。
  - `LABPBR_TEXTURE_AO=true`：用户打开的，默认关。
  - 默认（`settings/GlobalSettings.glsl`）：`NORMAL_STRENGTH 1.0`，视差 `PARALLAX` 开、`PARALLAX_DEPTH 1.0`、`SMOOTH_PARALLAX` 开。
- Complementary：`RP_MODE=3`（读资源包的 LabPBR，即我们的贴图），`NORMAL_MAP_STRENGTH=200`（法线强度 200%）。

## 分析（第一次的判断错了）

第一次只看了平均坡度（1° 左右），就说"条纹和起伏不是我们的贴图做的"，猜是 Sundial 的 16 倍各向异性过滤。用户把它调回 4，条纹和起伏都还在（截图）。

把法线贴图放大对比度看（草稿目录 `pbr/amp.png`）：
- 人行道的法线整张是密密的横向细线（扫帚纹，2–3 texel 一道），和游戏里的条纹一模一样；
- 钢筋混凝土的法线整张是 2–4 texel 的碎斑，就是那层沙纹。

平均坡度小，但都是成片的细线、细斑。灯贴着地面斜照时（Sundial 夜里的壁灯、门口灯），几度的坡度就让亮度差十几个百分点，所以看起来"锐"。Complementary 同样在读这些贴图，光照模型不同，显得柔。

教训：判断法线贴图要看图案和尺度，不能只看平均值。

## 改了什么

- 全部法线贴图扫了一遍（细节小于 7 texel 那部分的坡度 RMS）。扫描做成了正式工具 `tools/audit-normal-relief.mjs`：不带参数时按细节坡度排序列出所有方块法线贴图（≤ 512 px 的平铺贴图）；`--stretch IN OUT` 把一张法线贴图的偏移放大 12 倍输出，用来看图案。以后做或改平铺贴图都先跑它。数值高的大多是砖缝、地砖缝、墙板棱、盲道圆点这类窄线，是真实凹凸，截图里也正常；问题是"整面铺满的细纹"这一类，一共四种，一起改了：
  | 材质 | 生成器 | 改前 | 改后 | 细节坡度 RMS |
  |---|---|---|---|---|
  | 人行道扫帚纹 | `build-ground-materials-v1.mjs` | 2–3 texel 一道、深 0.55 mm | 5–8 texel、0.12 mm | 1.35° → 0.18° |
  | 路面扫帚纹 | 同上 | 2–3 texel、0.42 mm | 5–8 texel、0.10 mm | 1.04° → 0.17° |
  | 钢筋混凝土细砂 | 同上 | 4–6 texel、0.6 mm | 10–16 texel、0.12 mm，加 40 texel 0.1 mm 缓坡 | 1.10° → 0.47°（剩下的主要是气孔） |
  | 路沿混凝土 | `build-curbs-v1.mjs` | 2–4 texel、0.4 mm | 6–10 texel、0.15 mm | 1.03° → 0.22° |
- 缝、气孔、骨料（沥青）、地砖缝不动。颜色几乎没变。两个生成器 `--check` 通过。
- 斜光对比图（草稿目录 `refs/pbr_view.html` → `pages/pbr_grazing.png`，灯 1.2 m 高贴地斜照）：改前的条纹、沙纹、发麻都复现了，改后平顺。

## 还没做

- 进游戏看（Sundial 和 Complementary 各看一下，夜里、门口灯下）。
- "PBR 效果更好"那一半（Complementary 下更有质感）：砖、砌块、地砖已经有真实的缝；整面材质要更有质感，应该加的是大尺度、柔和的变化（板与板之间的光滑度差、轻微积灰、磨损），不是细纹。等用户看完这一轮再定。

## 格子边界上的面（z-fighting 检查，2026-10-09）

- 新工具 `tools/audit-boundary-faces.mjs`：把每个方块状态用到的模型（JSON 元素和 forge:obj）按状态里的转向摆好，找正好落在方块网格平面上、背后是别的格子的面。那个格子要是整方块，它在这个平面上的面朝向相同，两个面会闪。
  - face：背后是共面的邻格（那格是整方块就会闪）；diag：背后是斜对角的格子（中间那格也是整方块时，那个面被剔除，一般不闪）。
  - 多格模型占满的格子算自己的（伸进去超过 1 px 的格子），只伸出一点点的不算。
  - `--detail` 列出每个模型有问题的面（法线、位置、面积）。
- 起因：玻璃窗台滴水边的顶面在 y 0，贴着门口地面的顶面闪（见 [storefront_glazing_v1.md](../models/storefront_glazing_v1.md)）。已修。
- 2026-10-09 扫的结果（603 个模型），face 一类剩下的都要旁边贴上整方块才会闪，A1 里现在没有这种摆法，没改（已验收的模型不动，等真出现再改）：
  - `checkout_counter/cap_left`、`cap_right`：端板内侧面在格子边上，柜台一头顶着墙时从柜台里面看会闪（约 211 px²）；
  - `facade_masonry/cap_straight`、`cap_outer`、`facade_metal_panel/coping_lip`、`coping_corner`、`cmu_screen_wall/cap_*`：压顶伸出格子的那一截，顶面在 y 16，旁边紧贴同高的整方块、而且那块顶上露天时会闪；
  - `fuel_canopy/fascia_*`、隔断端头、货架连接件、油罐 / 泵坑 / 加注口里的接口小面（每个 0.1 px² 上下，在凹槽里）。
- 以后做模型：出模型前跑一遍这个工具；会落地或贴墙的部件（窗台、门槛、踢脚、端板）的面不要正好落在格子边界上、背对着邻格。
