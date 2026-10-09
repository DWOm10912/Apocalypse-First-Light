# Ground Materials V1：路面和地面材料

状态（2026-10-08）：**已做进游戏**。用户实机看了外观（竖着砌成墙、沥青铺在地上），钢筋混凝土按反馈改了一次，改后还没复看。
- 第 1 轮先出了贴图和离线预览。用户问分辨率会不会和原版不搭，定了保持每格 240（AFL 人造材料、设备统一这一档；特效、界面叠加效果贴着原版像素感；自然地形暂时原版），同日让做进游戏。
- 5 个新方块已注册；`reinforced_concrete`、`asphalt` 已换贴图。
- `compileJava --offline` 通过，生成器 `--check` 通过。
- 没有实机检查：缝的位置和方向、板色调、生存挖掘掉落。

## 为什么做

- A1 下一步是停车场和地块其余部分，大部分面积是沥青和混凝土。
- 现在的 `reinforced_concrete`、`asphalt` 是 16 × 16 的噪点贴图，没有 PBR；店里地面、加油区地面还是原版混凝土占位。
- 用户 2026-10-08 想把旧方块材质重做，并问北美店内、加油岛用什么。

## 北美实际做法（行业通行做法，这次没有逐条查资料核对）

| 位置 | 实际做法 | 对应材料 |
|---|---|---|
| 店内营业区 | 钢筋混凝土地坪上铺大规格瓷砖（便宜的用 VCT 塑胶地砖） | `porcelain_floor_tile` |
| 卫生间 | 瓷砖地面，墙下部贴砖 | `restroom_floor_tile`（墙砖以后做） |
| 后场（库房、设备间） | 密封或打磨的混凝土，或 VCT | `sealed_concrete_floor` |
| 现做食品区域 | 缸砖（卫生规范要求） | 没做 |
| 加油岛 | 镀锌钢板岛模里浇混凝土，约 15 cm 高 | 已有 `fuel_island_curb` |
| 顶棚下、油罐盖板、垃圾围栏前的垫板 | 钢筋混凝土路面（汽油、柴油会溶沥青，油罐车重），拉毛，每 3.5–4.5 m 一道切缝 | `concrete_pavement` |
| 人行道 | 混凝土，扫帚纹，约 1.5 m 一道压缝，比车道高一个路缘 | `concrete_sidewalk`（路缘见 [Curbs V1](curbs_v1.md)） |
| 停车场、车道 | 多数用沥青 | `asphalt` |
| 结构（桥墩、地堡墙、挡墙） | 现浇钢筋混凝土 | `reinforced_concrete` |

## 材料

生成器：`tools/build-ground-materials-v1.mjs`。不带参数写资源，`--check` 校验，`--preview DIR` 只写贴图（预览页用）。

共同规格（和 [外墙砖](facade_brick_v1.md) 一样）：
- 每格 240 × 240 texel（1 texel ≈ 4.2 mm），3 × 3 超采样，没有逐像素噪点。
- LabPBR：`_s` R 光滑度、G F0 14（非金属）、B 孔隙度（≤ 64，下雨变湿）、A 255；`_n` DirectX 法线（由高度场按毫米算坡度），B 环境光遮蔽，A 高度（1 − 深度 / 250 mm）。

| 材料 | 外观 | 颜色 | 光滑度 | 变体 |
|---|---|---|---|---|
| `reinforced_concrete`（重做） | 现浇面：很淡的斑驳，水泥浆里的细砂（主要在法线里，约 4–6 texel 一粒、起伏 0.6 mm，颜色只变 ±0.8%），每格约 5 个浅气孔（深 1.5–3 mm，颜色只暗 13%） | [150,149,144] | 70，气孔 50 | 3 |
| `asphalt`（重做） | 旧沥青：约 2 cm 的骨料一颗挨一颗，约 70% 露出石料，缝里和其余是沥青 | 沥青 [54,54,56]，石料 [74,73,70] ±14% | 沥青 82，石料 58 | 3 |
| `concrete_sidewalk`（新） | 扫帚纹横过人行道；每 2 m 一道压缝（槽宽 8 mm、深 10 mm、圆角），缝两边 5 cm 光面 | [170,168,162] | 48，光面 85 | 1 张（4 个方向镜像） |
| `concrete_pavement`（新） | 较轻的拉毛；每 4 m 一道锯缝（3 mm） | [162,160,155] | 52 | 无缝 / 一边 / 两边 3 种 |
| `sealed_concrete_floor`（新） | 钢抹子收光、封闭剂，柔和的云纹，有光泽 | [146,144,140] | 约 150 | 3 |
| `porcelain_floor_tile`（新） | 0.5 m 仿混凝土瓷砖，一格 4 块，灰缝 8 mm 下凹 2 mm，每块色调略不同 | 砖 [186,182,174]，缝 [158,153,145] | 砖 150，缝 40 | 3 |
| `restroom_floor_tile`（新） | 0.25 m 深灰瓷砖，一格 16 块 | 砖 [112,110,106]，缝 [88,86,83] | 砖 130，缝 40 | 3 |

**接缝处理**：
- 混凝土、沥青这种连成一片的材料，所有碰到方块边的东西在各变体之间都一样：
  - 低频斑驳按一格循环；
  - 只属于某个变体的变化乘一个在方块边上为 0 的窗；
  - 靠边的气孔、骨料来自同一组种子。
  - 所以任何两个变体挨着都没有接缝。
- 瓷砖只在砖块内部变化，灰缝盖住交界。
- 人行道、路面的缝在方块边上，每边各画一半。贴图只画西边、北边的缝；方块按世界坐标（2 m、4 m 的整数倍）镜像或转置贴图，按自己的走向转扫帚纹方向。每块板（两道缝之间）再给一个 ±3% 的整体色调（以后用 `BlockColor` 按位置算）。

## 方块（游戏里）

| ID | 中文 / 英文 | 类 | 硬度 / 抗性 | 音效 | 重量 |
|---|---|---|---|---|---|
| `concrete_sidewalk` | 混凝土人行道 / Concrete Sidewalk | `JointedPavementBlock`（缝距 2） | 2.0 / 6.0 | 石头 | 0.3 kg |
| `concrete_pavement` | 混凝土路面 / Concrete Pavement | `JointedPavementBlock`（缝距 4） | 2.0 / 6.0 | 石头 | 0.3 kg |
| `sealed_concrete_floor` | 密封混凝土地面 / Sealed Concrete Floor | `Block` | 2.0 / 6.0 | 石头 | 0.3 kg |
| `porcelain_floor_tile` | 瓷砖地面 / Porcelain Floor Tile | `Block` | 1.5 / 6.0 | 深板岩砖 | 0.25 kg |
| `restroom_floor_tile` | 卫生间地砖 / Restroom Floor Tile | `Block` | 1.5 / 6.0 | 深板岩砖 | 0.25 kg |

- **挖掘审计**（AGENTS）：五个都算普通建筑材料，和原版石头、外墙砖一样：`minecraft:mineable/pickaxe`，不加等级标签，任何镐都行，`requiresCorrectToolForDrops()`。掉落表掉自身，带 `survives_explosion`。没有台阶、楼梯、门。
  - 加油区路面现实里是钢筋混凝土路面，但这里按"路面材料"算，不按 AGENTS 的"加固结构默认钻石级"算：它是玩家到处会铺的地面。
  - `reinforced_concrete`（钻石级）和 `asphalt`（石级）原来的规则不变。
- **噪音**：五个都加进 `noise_stone_blocks`。
- **重量**：`item_mass` 估计值，按建材搬运单位（石头 0.25、外墙砖 0.3）。
- **创造栏**：建筑方块，紧跟在沥青后面。
- **贴图位置**：新方块在 `textures/block/ground/`。`reinforced_concrete`、`asphalt` 的变体 0 留在原来的路径（`textures/block/reinforced_concrete.png`，台阶、楼梯的模型照用），变体 1、2 在旁边（`reinforced_concrete_1.png` ……）。旧的 16 × 16 贴图被替换掉了。
- **变体**：方块状态按位置随机选 3 个模型之一（和外墙砖一样）。台阶、楼梯只用变体 0。
- **高速**：桥墩、匝道用的就是这两个方块，所以也跟着换了样子。

**人行道、路面的缝**（`client/GroundJointModel`，加载器 `apocalypse_firstlight:ground_joints`）：
- 方块模型在区块网格化时从 Forge 的 `getModelData(level, pos, …)` 拿到位置，按 x、z 各算一次：
  - 坐标 mod 缝距 = 0：西 / 北边有缝；
  - 坐标 mod 缝距 = 缝距 − 1：东 / 南边有缝；
  - 其它：没有缝。
- 顶面从 `plain` / `edge_w` / `edge_n` / `corner` 里选一张，东 / 南边的缝镜像贴图。
- 状态只有 `axis`（x / z），是人行道、车道走的方向，放置时取玩家朝向，扫帚纹横过去：`axis=x` 时顶面贴图转置，扫帚纹顺着 z。结构旋转 90° 时 `axis` 互换。
- 不存缝的状态，所以玩家放、WorldEdit、结构模板、旋转后的模板都按新位置自动画缝。
- 侧面、底面用 `plain`。物品模型顶面是 `corner`。
- **板色调**：所有面 tint 0，`BlockColor` 按板（两道缝之间那几格）算 95–100%。因为 tint 只能变暗，这两种贴图本身调亮了约 2.5%（人行道 [174,172,166]、路面 [166,164,159]）。
- 镜子反射里也按位置画缝（`MirrorReflection` 网格化时同样调 `getModelData`）。

## A1 里的占位还没换

店铺地面、加油区地面现在还是原版 `light_gray_concrete`，店前人行道是 `reinforced_concrete`。要换成：
- 营业区 `porcelain_floor_tile`、卫生间 `restroom_floor_tile`、后场 `sealed_concrete_floor`；
- 店外人行道 `concrete_sidewalk`（`axis` 顺着店面）；
- 加油区和油罐盖板 `concrete_pavement`。

2026-10-08 脚本已改；店铺（`floors`）和加油区（`pavement`）同日都已在开发存档换好，读回核对过，用户还没看：
- 店铺脚本：`FLOOR_ZONES` / `SIDEWALK_ZONES`（纸面坐标，k −1），新建时直接用新材料；`floors` 模式把已经建好的店按区域 `we_replace`（原版浅灰混凝土 → 地砖 / 后场混凝土，`reinforced_concrete` → 人行道），墙下基础不动。
  - 营业区 X 1..25、Z 6..14 和办公室 X 23..25、Z 1..5：`porcelain_floor_tile`；
  - 两间卫生间 X 1..5、Z 1..5：`restroom_floor_tile`；
  - 设备间、冷库、库房 X 6..22、Z 1..5：`sealed_concrete_floor`（设备间墙角那格电缆不是浅灰混凝土，不会被换掉）；
  - 人行道：前后两条 `axis=x`，两侧 `axis=z`。
  - 隔墙下面和门洞那一格归到门里那个房间。
- 加油区脚本：地面改成 `concrete_pavement[axis=z]`（车顺着加油岛走）；`pavement` 模式把建好的加油区 k −1 层的浅灰混凝土换掉，井盖、底槽、电缆不动。底槽是两层的多方块，编辑框不能切开它，所以按行绕开底槽分成 13 个框。
- 离线检查：店铺人行道 188 格、店内地面 350 格（和原来一样），加油区脚本没有冲突、没有悬空连接。

## 预览

- 页面：草稿目录 `refs/gm_view.html`（three.js r128）。LabPBR 换算：粗糙度 = 1 − 光滑度，法线由 `_n` 的 RG 重建（DirectX，`normalScale` y = −1），AO 来自 `_n` B。贴图按夹边采样，和游戏里方块图集的 sprite 一样，不会在方块边上卷到另一边。
- 输出：`gm_swatches.png`（7 种材料各 4 × 4 格）、`gm_lot.png` / `gm_lot_close.png`（沥青停车区、人行道、加油区路面、混凝土墙）、`gm_interior.png` / `gm_interior_back.png`（营业区瓷砖、卫生间砖、后场混凝土）。
- 调整记录：
  - 第一版沥青是散点石子，看着像星点，改成骨料挨着骨料；
  - 第一版扫帚纹弯得像木纹，改成细而直的线；
  - 第一版后场混凝土云纹太花，减弱了；
  - 预览曝光调过两次。
  - 用户实机（2026-10-08，Sundial，把几种材料竖着砌成墙看）："挺真实的"；问现实里钢筋混凝土、沥青是不是这样。对比后：沥青铺在地上用户觉得可以，不改；钢筋混凝土"稍微改一下"——原来太光滑（像腻子），气孔太深太匀（像脏点），所以加了细砂、气孔减半变浅。对拉螺栓孔、模板缝没加：每格都带会变成 1 m 网格，以后要的话另做一个清水混凝土墙方块。
- 离线预览只看材质方向，不代表游戏里的光照。

## 没做

- 实机：Sundial 和原版下的外观，缝的位置和扫帚纹方向，板色调，挖掘掉落。
- 以后的：卫生间墙砖、缸砖、隔墙涂料面、吊顶板（室内装修一轮）；路缘 2026-10-09 已做成 [Curbs V1](curbs_v1.md)。停车标线 2026-10-08 已做成 [Pavement Markings V1](pavement_markings_v1.md)。
