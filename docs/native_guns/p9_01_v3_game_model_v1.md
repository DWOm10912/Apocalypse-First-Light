# P9-01 V3 游戏模型（低模 + 烘焙贴图 + LabPBR）V1

## 状态（2026-10-17）

**已接入运行时，2026-10-17 用户实机 PASS（「这轮PASS」）**：第二版贴图（斑驳清理）和维护台 GPU 缓存绘制，原版和 Aureveil 下都看过。还没做：Aureveil alpha.49 的反射（Aureveil A 排期中，做完要复测）；消音器后面露出一小段枪管螺纹（`muzzle_anchor` 沿用 v2，v3 螺纹段更长）。 `apocalypse_firstlight:p9_01` 的 Profile、第三人称持枪（`GunStance`）和物品模型都已指向 `p9_01_v3_native`。骨骼、锚点和 v2 完全一样，换的是网格、贴图和 PBR。动画：2026-10-17 起由 `tools/author-p9-01-v3-native-animations.mjs` 生成，换弹、空仓换弹、检视、空仓检视重做成熟练档（[p9_reload_inspect_v1.md](../dev/p9_reload_inspect_v1.md)，2026-10-17 用户实机 PASS），其他片段和 v2 一样；之前动画文件是 v2 的逐字节副本。v2 的资源都还在，只是不再被物品引用。

用户原话：
- 「方向可以，你能直接做进blockbench和游戏里面吗，把材质和PBR一起做好？不要求原版就有光影级别的材质，但是要求至少看起来还行，开了光影效果很好的那种 如果你需要光影包那边做什么，直接发消息给他就行」
- 看了两版光影预览后：「图1的磨砂感是不是太重了？我比较喜欢图2那种」。图 1 是颜色、光滑度、法线里都加了像素颗粒的版本，图 2 是不加颗粒、只有烘焙结果的版本。最终用的是图 2 的方向，见第 4 节。
- 第一次进游戏后：「原版的效果有点略脏」「我们自己的光影5-8看起来好了很多」「我感觉我们的光影还差很多，材质和反光这一块 也可能是因为原版的套筒就很脏」。截图存在 `design/items/p9_01_v3/ingame/2026-10-17/`（01–04 原版，05–08 Aureveil，09–13 Sundial Lite）。贴图的处理见第 4 节，光影那边见第 6 节。
- 斑驳清理 + 维护台改走 GPU 缓存后再进游戏（同日）：「改完之后，Aureveil 下其实已经很接近了，不过还是需要那边做alpha49」。消音器、红点装上去位置正常：v3 的 28 根骨骼枢轴（含 `muzzle_anchor`、`sight_anchor`）和 v2 完全一样，配件按锚点挂，不看网格。

外形和细节来自概念图，见 [p9_01_v3_concept_v1.md](p9_01_v3_concept_v1.md)。质感的调研和方案见 [gun_surface_texture_reference_v1.md](gun_surface_texture_reference_v1.md)。

## 1. 文件

| 用途 | 路径（D 工作区） |
|---|---|
| Blender 工程（集合 `P9_hi` 烘焙用高模、`P9_lo` 游戏低模、`P9_bake` 烘焙时的合并件、`P9_v3` 概念图、`P9_v2_ref` 旧网格对照） | `design/items/p9_01_v3/source/p9_01_v3_game.blend` |
| 生成和烘焙脚本 | `design/items/p9_01_v3/source/blender/`：`game_build.py`、`game_uv.py`、`game_bake.py`、`game_save_bakes.py`、`game_compose.py`、`game_export.py`；预览用 `game_preview_mat.py`、`game_compare.py` |
| 低模导出（Blockbench 像素坐标，UV 是 2048 像素，左上角原点） | `design/items/p9_01_v3/source/p9_01_v3_game_lowpoly.json` |
| Blockbench 源（Free Model，内嵌 2048 颜色贴图；动画由 `tools/author-p9-01-v3-native-animations.mjs --write-source` 写） | `src/main/blockbench/p9_01_v3_native.bbmodel` |
| 源贴图 | `src/main/blockbench/textures/p9_01_v3_native.png`、`_s.png`、`_n.png` |
| Blockbench 构建 / 运行时导出 | `tools/build-p9-01-v3-native.mjs`、`tools/export-p9-01-v3-native.mjs`（`--check` 只核对） |
| 运行时 | `geo/p9_01_v3_native.geo.json`（28 根骨骼，贴图 2048）、`meshes/p9_01_v3_native.aflmesh.json`、`textures/item/p9_01_v3_native{,_s,_n}.png`、`models/item/p9_01_v3_native_in_hand.json`、`animations/p9_01_v3_native.animation.json` |
| 玩家道具（拔枪、收枪、枪套） | `player_props/p9_01.json`，用 `./gradlew gunPropExport` 按 v3 网格重新生成 |

用户自己的 `p9_01_v2_native.bbmodel` 没有改动，构建脚本只读它的动画。

重建顺序：
1. Blender 里按顺序执行 `game_build.py` → `game_uv.py` → `materials.py`（带下面第 3 节的参数）→ `game_bake.py`（每次一个 pass）→ `game_compose.py`（`OUT_PREFIX` 设为源贴图路径）→ `game_export.py`（`EXPORT_PATH` 设为低模导出路径）。每个脚本执行前先设 `BASE` 为脚本目录。
2. `node tools/build-p9-01-v3-native.mjs`（已有 v3 源时保留它的动画）。
3. `node tools/export-p9-01-v3-native.mjs`（不再复制 v2 的动画）。
3a. `node tools/author-p9-01-v3-native-animations.mjs --write-source --write-runtime`（动画）。
4. `./gradlew gunPropExport`。

4096 的烘焙图没有进仓库：一套约 11 分钟，可以从 `.blend` 和脚本重新烘出来。

## 2. 网格

- `game_build.py` 用概念图的同一套脚本生成两份：
  - `P9_hi`：概念图精度，带刻字、膛线、螺纹，只用来烘焙；
  - `P9_lo`：游戏低模，`LO_Q` 0.7（圆角、圆柱、车削件的段数按半径减少），不带螺纹和膛线（这些从高模烘进法线），35° 以上的边设为硬边，加权法线。
- 两份都带动画要用到的内部结构（`INTERIOR`）：
  - 滑套内槽、击针孔；
  - 弹膛口、供弹坡、枪管锁定块；
  - 机架导槽、弹匣井上口；
  - 复进簧和导杆；
  - 弹匣口唇和托弹板。
  - 滑套后坐 32 mm 时在 Blender 里看过，抛壳口里能看到枪管和弹膛。
- 低模 34 个物体，13330 个三角形。弹匣三件（弹匣体、底板、托弹板）另外复制给 `reload_magazine` 和 `empty_old_mag`。
- 合计 40 个 part，三角形当量 14542（上限 16384）。sidecar 3136309 字符（上限 4 MiB）。
- 骨骼分配（`game_export.py` 的 `bone_of`）：

| 骨骼 | 部件 |
|---|---|
| `slide` | 套筒、后盖板、抽壳钩、击针指示销 |
| `rear_sight` / `front_sight` | 照门、准星和各自的氚光点（白圈 + 玻璃） |
| `barrel` | 枪管方块、枪管 |
| `trigger` | 扳机 |
| `gun_body` | 机架、导杆、复进簧、销钉、铭牌、挂机柄、两侧拆卸杆、两侧弹匣释放钮 |
| `magazine` / `follower` | 弹匣体、底板 / 托弹板 |

- 坐标换算（概念毫米 → Blockbench 像素，和 v2 Rig 同一个绑定空间）：
  - x / 16.99015；
  - z / 16.99015 − 1.048045；
  - −y / 16.99015。

## 3. UV 和烘焙

- 全部低模共用一张 2048 图集：Smart UV Project（33°），轴对齐打包（`AXIS_ALIGNED`，直的倒角落在像素行上，边缘高光不会出现台阶），边距 2 像素。
  - 填充 54.7%，密度 4.92 像素/毫米。
  - v2 是 1024 图集，约 28 像素/单位；v3 约 84 像素/单位。
- 材质参数（`materials.py` 的游戏值）：`GRAIN_K` 0.18、`ALB_K` 2.0、`STEEL_BASE` 0.030、`BARREL_BASE` 0.028。
- 烘焙（Cycles OptiX，4096）：
  - 套筒、枪管、机架、弹匣分四组，各自高模烘到各自低模（selected to active），运动件不会烘进邻件。
  - 6 个 pass：
    - 颜色、粗糙度、材质常量（F0、发光）；
    - 完整法线、只有几何的法线；
    - AO（低模自身，2.5 mm）。
- 发黑钢（枪管、销钉、控件）烘成**有涂层的非金属**（F0 28），不是 LabPBR 金属。原因：
  - 金属的颜色就是它的 F0，原版会直接显示成银白色（第一版对比图里枪口螺纹、挂机柄就是发白的）；
  - 用户之前也说过金属 F0 在光影里反光刺眼。
  - 裸金属以后跟磨损层一起做。

## 4. 贴图（`game_compose.py`，4096 合成后缩到 2048）

| 图 | 内容 |
|---|---|
| 颜色（sRGB） | 底色 × AO（强度 0.6）× 缝隙变暗（0.35）× 法线细节的浮雕（1.6）+ 凸棱高光（涂层线性 +0.030、聚合物 +0.018）+ 很淡的颜色颗粒 |
| `_s` | R 光滑度（1 − 粗糙度）；G F0：聚合物 10、套筒涂层 24、发黑钢 28；B 0；A 255，氚光玻璃 A 110（发光） |
| `_n` | RG 切线法线，DirectX 方向（G 正向 = 图像向下）；B AO；A 255（不做视差） |

颗粒的取舍（2026-10-17）：
- 概念材质的 3D 颗粒烘出来只有约 ±0.8 级 sRGB，原版看不出来。所以合成时在像素空间另加一层颗粒（2–3 像素的点 + 1 像素的细点 + 约 10 像素的斑驳，按材质分强度）。
- 第一版把颗粒同时加进颜色、光滑度和法线。光影预览里像锤纹漆，用户选了更光滑的那版（「我比较喜欢图2那种」）。
- 然后只在颜色里留一层淡颗粒：涂层 ±5%、聚合物 ±4%（sRGB 乘法），斑驳权重 0.2。光滑度和法线不加颗粒，光影下是连贯的缎面高光。
- 进游戏后用户说原版「略脏」。量出来主要来源是概念材质烘进底色和粗糙度的中等尺度斑驳（滑套底色约 ±12%、钢件约 ±14%，粗糙度约 ±0.04），比后加的颗粒大。现在的做法（脚本默认值）：
  - 涂层和钢件（F0 ≥ 20）：底色和粗糙度相对同材质局部平均值（半径 64 个 4096 像素，约 6 mm）的偏差乘 0.25（`MOTTLE_KEEP`）；
  - 聚合物：只压比 6 像素模糊更粗的那一段（`POLY_FINE_R`），握把防滑纹的颜色保留；
  - 颜色颗粒降到涂层 ±2%、聚合物 ±2.5%。
  - Blender 对比图：`design/items/p9_01_v3/ingame/2026-10-17/c7_side_vanilla_cmp.png`、`c7_side_pbr_cmp.png`（上旧下新）。

原版观感：整体仍是黑钢的 32–52 sRGB（滑套平均约 32）。看得到的细节来自网格的平/曲面分块、握把防滑纹（颜色里纹尖亮、纹底暗）、缝隙 AO、棱边高光和淡颗粒。

## 5. 运行时

- sidecar 是 Vextris format 3：由 v2 转换器的结果加上 `crease_angle` 35 和 `crease_weighting` "area"。
  - 加载时按 35° 生成平滑法线，和烘焙时 Blender 的硬边 + 加权法线一致。
  - 用 Vextris 0.1.1-alpha.4 的 `MeshLoader` 离线加载过：40 个 part 里 39 个是平滑 part（只有击针指示销是纯平面）。
- 绘制（`NativeGunContextRenderer#drawCached`）：
  - 枪网格在第一、第三人称走 Vextris GPU 缓存（`MeshGpuCache.drawBone`，每根骨骼一次，位姿已在骨骼枢轴）。原来只有 format 3；2026-10-17 起所有 format 都走（下雨天拿 BR-51 掉帧，JFR：format 1 / 2 网格每帧逐顶点写入是拿枪时渲染线程最大的 CPU 开销；改后拿枪比空手多出的渲染线程 Java 采样从约 59 个降到约 2 个/12 s）。format 1 / 2 的平面 part 在缓存里是 QUADS，和 CPU 路径画出来一样。**改后其他枪还没进游戏看过。**
  - 原因 1：开光影时 Oculus 会把 GeckoLib 那个 QUADS 缓冲里的法线按面重写，曲面会变成一块一块的，和烘焙的法线贴图对不上；GPU 缓存在开光影时把平滑 part 存成 TRIANGLES，保留顶点法线。
  - 原因 2：网格不用每帧重新提交。
  - 这些情况仍走原来的 CPU 路径（`GeckoMeshRenderer.renderBone`）：
    - 物品栏、展示框等其他显示方式；
    - Oculus 手部的半透明阶段；
    - `-Dvextris.gpuCache=false`。
  - GPU 缓存不做第一人称的背面剔除（CPU 路径的优化），对显卡开销影响很小。
- 绘制（`MaintenanceGunRendering#drawCached`，2026-10-17 加）：维护台检视视图、背着的枪、地上的枪、枪架（非区块路径）这些静止姿态的枪，format 3 网格也走 GPU 缓存，和 T15 皮卡一样即时绘制。原因同上：用户的检视截图在光影下是这条路径，原来是 GeckoLib QUADS。
  - 2026-10-17 晚：改成所有 format 都走，并去掉“只限 `MultiBufferSource.BufferSource`”这个条件。开光影时 Oculus 给实体图层的是一个包装过的 buffer（给每个实体打标记），不是 `BufferSource`，所以身上背着的枪（背上的 BR-51、枪套里的 P9）一直在 CPU 上逐顶点写，每帧在阴影和主画面里各写一遍。在用户存档里这占了开光影时渲染线程约 70%（见 [雨 V1](../rendering/rain_v1.md) 第 3.4 节）。现在只有两种截取几何的用途留在 CPU 路径：检视截取（`MaintenanceGunRendering.Capture`，`GunInspectionGeometry`）和枪架 / 地上枪的区块截取（`ClosedFaces`）。
  - 测试客户端截图看过（开 Aureveil 和不开光影，第三人称前后）：背上的枪、枪带、枪套位置和改之前一样。**用户还没在游戏里看过。**
  - 只在真实的 `MultiBufferSource.BufferSource` 上走；检视取景的包围盒捕获（`GunInspectionGeometry`）和枪架的区块捕获（`ClosedFaces`）仍走 CPU。
- 引用切到 v3 的地方：
  - `AflItems.P9_01` 的 Profile id；
  - `GunStance` 的 P9 `Carried` 资源名；
  - `models/item/p9_01.json` 的手持父模型；
  - 开发自检 `NativeGunRuntimeSmokeCheck`。
- 显示参数：`p9_01_v3_native_in_hand.json` 就是 v2 的那份，只换了粒子贴图。所以第一人称位置、大小（0.53）、ADS 锚点（`rear_sight`）都没变。
- 枪膛口刻字是 `CAL 9x19mm`，和游戏里的弹药一致（概念图写的是 5.7x28）。刻字、编号铭牌都烘在法线和颜色里，低模没有这些几何。

## 6. 光影（Aureveil）

2026-10-17 已发通知给 Aureveil A。
- 内容：
  - 贴图约定，和 alpha.46 现有的读法一致，不需要改格式；
  - 手持枪改成 GPU 缓存即时绘制。
- 请他们在全局光照告一段落后：
  - 确认手部阶段拿到的是 hand 程序、法线和 `_s` 生效；
  - 确认手持物能拿到环境反射和镜面高光，必要时加强手持物的细节；
  - 看氚光点 A 110 的亮度是否合适。
- 还没收到回复。

2026-10-17 第一次实机后又发了一条（附截图路径）。对比 Sundial Lite：Sundial 里滑套顶部斜面有清楚的天空反射带，螺纹枪口、棱边反光明显；Aureveil 下几乎看不到反射，深色的枪被整体抬成蓝灰，像哑光塑料。请他们优先：
- 按 `_s` 光滑度和 F0 做带菲涅尔的环境反射（手持、实体、方块实体三条路径）；
- 检查深色非金属的环境光和 AO / 镜面遮蔽，去掉蓝灰的抬升；
- 太阳的镜面高光；
- 确认各视图用的 gbuffers 程序。
- Aureveil A 回复（同日）：排在 alpha.48（车灯高光、光束、反射光）用户 PASS 之后，作为 alpha.49 的主要内容；AFL 这边暂时不用改，要配合时他们先给接口；做完通知 D，再请用户复测。

## 已知问题和没做的

- 加长弹匣（24 发配件）的模型和贴图是旧的，没有跟着 v3 重做，见 [p9_01_extended_magazine_v1.md](p9_01_extended_magazine_v1.md)。
- 复进簧固定在机架上，滑套后坐时不会压缩。
- 弹匣释放钮在烘焙时和弹匣分在一组，它的 AO 没算机架的遮挡，影响很小。
- 套筒上 192 个看不到的面（照门燕尾槽底、后盖板凹槽的侧壁）烘出的法线方向不对，正常视角看不到。
- 照门缺口 3.4 mm、准星 3.8 mm，按角度算是对的；ADS 实际是否舒服要进游戏看。
- 没有磨损；磨损以后按耐久做成每把枪自己的一层。
- 枪的贴图没有 mipmap：颗粒已经很淡，远处闪烁应该不明显，还没在游戏里确认。

## 验证

做过的（都是离线）：
- Blender 里用游戏贴图渲染对比：原版光照模拟和 PBR 各一套，比例接近第一人称；
- Vextris 加载器离线加载 v3 sidecar + geo，通过；
- `compileJava` 通过；
- `gunPropExport` 重新生成 `player_props/p9_01.json`；
- `export-p9-01-v3-native.mjs --check` 核对运行时资源和源一致。

- 2026-10-17 第二轮：`game_compose.py` 重新合成（斑驳清理），重建 bbmodel、导出运行时，`--check` 通过；`MaintenanceGunRendering` 改动后 `compileJava` 通过。

用户实机看过的（2026-10-17，第一版贴图）：原版、Aureveil、Sundial Lite 下的手持和维护台检视，结论见状态里的原话。

用户实机 PASS（2026-10-17 第二轮）：斑驳清理后的贴图和维护台的 GPU 缓存绘制，原版和 Aureveil 下的检视视图，配件（消音器、红点）装上后的位置。

没专门看的：ADS、换弹、拔枪收枪、第三人称、枪架。
