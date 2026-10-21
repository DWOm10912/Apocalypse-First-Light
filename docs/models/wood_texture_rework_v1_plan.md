# 木材贴图重做 V1：调研和计划

- **状态（2026-10-20）：** 用户选了做法 A（CC0 实拍做底）、样板第 1 版和家具胡桃木 B，已经换进游戏贴图（第 4.1 节）。用户实机看过第一轮：家具认可（"现在的家具材质很不错，可以继续保持"）；第二轮木板、方木端面、老宅白栎木料（第 4.2 节）用户实机看过："其他的纹理没问题，也不卡"。第三轮老宅原木墙、板条墙、柴垛、炉子里的柴火（第 4.3 节）用户实机 **PASS**（"进游戏看过了，木纹可以，PASS"）。
- **起因（用户 2026-10-20）：** "我目前对于模组里面的木头材质还是不认可。树有点糊，原木有点糊，家具的纹路很奇怪，木板也是。""其他原材料的贴图和纹理也奇怪。"
- **范围：** 木板 `<树种>_planks`、圆木和木梁 `<树种>_timber_*`（`tools/build-wood-blocks-v1.mjs`）、老宅原木墙 `cabin/white_oak_log_*`、家具木材 `furniture/wood*`（`build-wood-furniture-v*.mjs`）、树皮 `tree/<树种>_bark`。其他原材料以后单独过一遍。

## 1. 现在的问题（读贴图看的）

| 贴图 | 现在 | 问题 |
|---|---|---|
| `wood/white_oak_planks` | 每块板上画等距的正弦条纹，板缝是很粗的深色线 | 像卡通贴纸或瓷砖，不像木地板。条纹间距、粗细、深浅都一样，没有年轮的疏密变化；每块板颜色几乎一样 |
| `wood/white_oak_timber_side`、`cabin/white_oak_log_round` | 一整片等距的细波浪线 | 远看像瓦楞纸或摩尔纹，近看像条码。没有木射线、导管、节疤 |
| `furniture/wood` | 等距的横向细线，颜色很平 | 同上，而且像没上过漆的灰棕色，没有漆面那种暖、透的颜色 |
| `tree/*_bark` | 32 px | 用户 2026-10 通过过 32 px，但现在说"糊"。分辨率和画法要再定 |

## 2. 现实中上了漆的木头（2026-10-20 内置浏览器看的实拍）

来源：The Wood Database 的白栎、黑胡桃页面（打磨后和上清漆后的样板照片、成品照片），Poly Haven 的 CC0 木材贴图（`wood_floor`、`oak_veneer_01`）。Wood Database 的照片有版权，只用来看，不拿来做贴图。

- **年轮不是等距的线：** 早材（浅、宽）和晚材（深、窄）交替，间距一圈圈不一样，线有粗有细、有断续。
- **弦切板（最常见）：** 板中间是一串套在一起的尖拱形（"教堂拱"），往两边变成近似平行的直纹。每块板的拱位置不一样。
- **径切白栎：** 有明显的浅色木射线斑块（一片片横向的亮斑，像虎皮纹），这是白栎、红栎最有辨识度的地方。
- **导管：** 栎木有很多短短的深色细点，沿着纹理方向排，近看是"毛孔"。胡桃、枫木细得多。
- **上漆以后：** 颜色变暖、变深、更饱和（白栎从浅灰黄变成蜂蜜色，胡桃变成深巧克力色带紫调）；纹理对比更清楚；光泽来自漆面，是一层均匀的高光，不是贴图上画出来的亮条。
- **木地板：** 板宽一样，长度不一，端头错开；**板与板之间颜色深浅明显不同**；板缝很细，只比木头深一点，不是一条黑线。
- **成品家具（白栎盒子、胡桃碗）：** 看得出每块料的纹理方向和颜色都不同，边角处能看到端面年轮。

## 3. 两种做法

| | A. 用 CC0 实拍贴图做底（建议） | B. 改进程序生成 |
|---|---|---|
| 怎么做 | 从 Poly Haven 这类 CC0（公共领域）贴图库取对应树种的木纹照片，缩到 240 px，按树种校色，做无缝，生成 `_n` / `_s` | 重写生成脚本：噪声扭曲的年轮、教堂拱、导管点、木射线、板间色差 |
| 好处 | 一步就是真木头的样子，最符合"真实" | 6 个树种风格完全统一，能精确控制 |
| 坏处 | 每个树种要找到合适的 CC0 照片；照片里的光影要去掉 | 很难做到像照片那样真，现在的问题就是程序生成做出来的 |
| 授权 | CC0 不用署名，可以随便用 | 没有问题 |

- 不管哪种，都按现有规则：人造物 240 px + LabPBR；法线贴图里不能有细的连续线条（会在地上反光成条纹）；漆面光泽放在 `_s` 里，不画进颜色。
- 树皮要不要从 32 px 提高，和用户单独定。

## 4. 样板（2026-10-20，做法 A，还没进游戏）

- 用户定了做法 A；树皮提高到 64 或 128，以前的问题是"法线感太强"，所以法线做得很弱；以后只能用锅烧水（记在 `thirst_system_v1.md`）。
- 对比图：`design/wood_textures_v1/wood_samples_v1.png`、`wood_samples_v2.png`，生成脚本 `design/wood_textures_v1/wood_samples.mjs`（原图在会话临时目录，不进仓库）。还没有替换游戏里的任何贴图。
- CC0 原图：
  - Poly Haven 树皮：白栎 `bark_willow_02`、红栎 `bark_willow`、红枫 `chinese_hackberry_bark`、山核桃 `tree_bark_03`（去掉青苔；没有对得上的粗皮照片）、铁杉 `pine_bark`、白松 `knotted_pine_bark`；
  - Poly Haven 木料：`white_oak_veneer`、`red_oak_veneer`、`white_maple_veneer`、`ash_veneer`（山核桃）、`coated_pine`（白松、铁杉）；
  - ambientCG 端面：`TreeEnd002`（栎、山核桃）、`TreeEnd003`（枫、松、杉）。
- 做法：
  - 树皮按真实尺寸截取，一张约 1 m，和 `TreeShape` 一样按周长铺；接缝用错位混合，保持对比度；去掉照片里的大块明暗；按树种调色，只留棕灰色；
  - `_n` 用原图法线乘一个强度系数（第 1 版 0.35，第 2 版 0.2），G 通道和 `hd-art.mjs` 同向；
  - 原木端面把实拍圆盘变形到各树种 `LogShape` 的圆角方形（2.5/16–6/16），树皮边沿着轮廓；树桩截面用圆盘（`TreeCut` 的 1/2.1 半径）。
- 两版：

| | 第 1 版 | 第 2 版 |
|---|---|---|
| 树皮 | 128 px，一张 1 m，法线强度 0.35 | 64 px，一张 0.7 m，法线强度 0.2 |
| 端面 | 128 px | 64 px |
| 刨光木料 | 240 px | 240 px |
| 家具统一漆面 | 白栎单板，胡桃色漆 | 红栎单板，琥珀色清漆 |

- 已知问题：
  - 红枫的地衣斑、白松的节疤，2×2 铺开时能看出重复；
  - 东部铁杉的木料借用了松木照片；
  - 家具用的胡桃木原图（`black_walnut_veneer_01`）是平直纹，几乎没有花纹，所以第 1 版改成白栎上胡桃色漆。要真正的胡桃木花纹，得再下载别的胡桃木单板。

- **用户选了第 1 版**（2026-10-20）：树皮 128 px，一张 1 m，法线强度 0.35；端面 128 px。家具"再下载胡桃木试试"。
- 家具胡桃木对比图：`design/wood_textures_v1/furniture_walnut_options.png`，脚本 `furn_walnut.mjs`。新下载了三张 Poly Haven CC0 单板：`walnut_veneer_02`、`smoked_walnut_veneer`、`natural_walnut_veneer`。
  - 四个选项：A 白栎上胡桃色漆（第 1 版原来的）；B `walnut_veneer_02`，直纹带拱形花纹；C `smoked_walnut`，火焰纹更强；D `natural_walnut`，花纹很碎。
  - 用户选了 B："B，换进游戏里吧"。

### 4.1 换进游戏（2026-10-20，没进游戏看过）

- 生成脚本 `tools/build-wood-photo-v1.mjs`：
  - 原图缓存在 `build/cc0-wood/`，不进 git；第一次要先跑 `--fetch`，地址列表在脚本的 `SOURCES` 里；
  - 解码 JPEG 用 `wiki/node_modules` 里的 sharp；
  - 用法：直接运行是写出，`--check` 是逐字节比对，`--preview DIR` 输出一张对比图。
- 写出的贴图，都在 `textures/block/` 下：

| 贴图 | 尺寸 | 说明 |
|---|---|---|
| `tree/<树种>_bark` + `_s` / `_n` | 128 | 树干、树枝、树桩侧面、原木方块侧面 |
| `tree/<树种>_log_end` + `_s` / `_n` | 128 | 原木方块端面，各树种圆角方形，平法线 |
| `tree/<树种>_end_grain` + `_s` / `_n` | 128 | 树桩、倒下树干的截面，圆盘 |
| `tree/<树种>_cut` + `_s` / `_n` | 128 | 砍口，用该树种的刨光木料调暗 |
| `wood/<树种>_timber_side` + `_s` / `_n` | 240 | 圆角方木、木梁的侧面 |
| `tools/furniture/walnut_finish_240.png` | 240 | 家具木纹底图（不是游戏贴图），给 `furniture/wood-furniture-art.mjs` 用 |

- 家具：
  - `furniture/wood-furniture-art.mjs` 的五种木材质都从胡桃木底图取色；
  - 重跑了 `build-wood-furniture-v1.mjs` 和 `v2`。V1 要跑两遍：它的柜台贴图是从磁盘上的 `wood*.png` 烘出来的，第一遍读到的还是旧图；
  - 换掉的有材质贴图 `furniture/wood*`、5 件家具的贴图和 `src/main/blockbench/furniture/` 里对应的 Blockbench 源文件。
- 旧生成脚本：
  - `mesh-trees/build-tree-textures.mjs` 不再写树皮、端面、截面、砍口；
  - `build-wood-blocks-v1.mjs` 不再写 `timber_side`。
  - 原来的 `bark-art.mjs`、`felling-art.mjs` 的端面函数还留着，没删。
- 离线检查都通过：
  - `build-wood-photo-v1` `--check`：91 个输出；
  - `build-tree-textures` `--check`；
  - `build-wood-blocks-v1` `--check`：18 个方块；
  - `build-wood-furniture-v1` `--check`：99 个输出；
  - `build-wood-furniture-v2` `--check`：87 个输出；
  - `build-box-stove-v1` `--check`；
  - `mesh-trees/check.mjs`。
- 顺带修了一处和这次无关的问题：`build-wood-furniture-v2.mjs` 里储物箱的中文名，跟着本地化审校改成"木制储物箱"，原来检查不过。
- `build-cabin-exterior-v1.mjs --check` 报 `cabin_mesh/log_wall/pad_12.json` 过期。这不是这次改的：老宅的原木墙贴图 `cabin/white_oak_log_*` 不在本轮范围，这次也没碰。
- 没做（木板和方木端面第二轮做了，见 4.2）：
  - 老宅原木墙 `cabin/white_oak_log_*`；
  - 柴火 `stove/firewood_bark`。

### 4.2 第二轮（2026-10-20，用户实机看过：没问题，也不卡）

- 用户实机看了第一轮："现在各类木板没有换贴图""白栎门/窗，铁皮屋面的木纹也没有改""现在的家具材质很不错，可以继续保持"。
- `build-wood-photo-v1.mjs` 多写两组（一共 127 个输出）：
  - `wood/<树种>_planks` + `_s` / `_n`（240）：4 块 25 cm 的板，从刨光木料照片里各截一条，错缝，板间明暗和冷暖不同，细板缝（[Wood Building Blocks V1](wood_building_blocks_v1.md)）；
  - `wood/<树种>_timber_end` + `_s` / `_n`（240）：截面照片里以髓心为中心的正方形，不带树皮。
- `build-wood-blocks-v1.mjs` 不再写任何贴图，程序木板、端面的代码和 `--preview` 删掉了。
- 老宅白栎木料（门、门框、窗套、吊顶板、门廊地板）从白栎刨光木料照片取色（[Cabin Exterior V1](cabin_exterior_v1.md)"白栎颜色统一"一节）。
- 离线检查：`build-wood-photo-v1 --check` 127 个 OK；`build-wood-blocks-v1 --check` 18 个方块 OK；`build-cabin-exterior-v1 --check` 只剩原来就有的 `pad_12.json`。
- 还是程序木纹的：老宅原木墙 `cabin/white_oak_log_*`、柴火 `stove/firewood_bark`、柴垛 `cabin/firewood_*`、板条墙。
- 同一轮树的棱角和 LOD 过渡见 [Mesh Trees P4 X 节](../rendering/mesh_trees_p4_formal.md)。

### 4.3 第三轮（2026-10-20，用户实机 PASS）

- 用户："老宅原木墙、柴火、柴垛、板条墙都换成新的木纹""其他的纹理没问题，也不卡"。
- `build-wood-photo-v1.mjs` 多写三张底图到 `tools/wood-photo/`（不是游戏贴图，一共 130 个输出）：
  - `white_oak_log_end_240.png`：白栎截面，只到木头边缘，半径占贴图 0.47；
  - `firewood_end_240.png`：北方红栎截面，带树皮边，半径 0.47；
  - `firewood_bark_240.png`：北方红栎树皮，每米 240 像素。
- 新的共用取色模块 `tools/wood-photo/sample.mjs`（双线性、环绕），`cabin/cabin-art.mjs` 和 `stove/box-stove-art.mjs` 都用它。
- 换掉的贴图：
  - 老宅：`cabin/white_oak_log_wall`、`white_oak_log_round`、`white_oak_log_end`、`brown_board_siding`、`grey_board_siding`、`firewood_side`、`firewood_end`（[Cabin Exterior V1](cabin_exterior_v1.md)）；
  - 炉子：`stove/firewood_bark`、`firewood_end`（[Box Stove V1](box_stove_v1.md)）。
- 对比图 `design/wood_textures_v1/cabin_round3.png`。
- 重新生成的顺序：照片脚本 → 炉子生成器 → 老宅贴图（老宅外饰生成器整个跑会重写过期的 `pad_12.json`，这次只写了贴图）。
- 离线检查：`build-wood-photo-v1 --check` 130 个 OK；`build-box-stove-v1 --check` 100 个 OK；`build-cabin-exterior-v1 --check` 只剩原来就有的 `pad_12.json`。
- `cabin-art.mjs` 里没再用的程序直纹 `grain()` 删了。

## 5. 要用户决定

1. 做法 A 还是 B。
2. 树皮的分辨率。
3. 先做哪一个当样板：建议先做白栎木板和家具木材（老宅里最显眼），出 PNG 对比图给用户看，通过了再铺到 6 个树种。
