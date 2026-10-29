# Blackridge .50 重做 V1（规划 + 概念图）

## 状态（2026-10-28）

- 用户 2026-10-28 定：「外形原创，需要参考沙漠之鹰」「大小也要调整」。
- 第 1 轮概念图（Blender 高模 + 模拟 PBR）做完，两种涂装各一套（用户：「双色和黑钢都做一份，我看看效果」）。
- 用户看第 1 轮：「我怎么感觉有点像强尼银手的马洛里安呢」「抛壳的地方，感觉过度有点生硬」「握把感觉大小不对啊，感觉有点小了」。第 2 轮改了枪管截面、抛壳窗斜线、握把，还没发图，用户又说：「不行，你是不是在按照p9的框架去做啊 / 我想让别人一眼就认出来这个是沙漠之鹰，但是我们可以改细节 / 你能不能去skechfeb找一些沙漠之鹰的模型，批准下载」。
- 第 3 轮（2026-10-28）：轮廓和截面按 Sketchfab 下载的沙鹰参考模型量出来，细节换成我们的，见 4.1 节。第 1、2 轮的脚本 `gun.py` 留着。
- **用户 2026-10-28 通过第 3 轮：「可以，就纯黑钢吧，细节不用改」**。外形定为 R3（`gun_r3.py`），涂装定为黑钢，双色不用。
- 声音：用户要重新生成，提示词见第 10 节（2026-10-28 发给用户，等录音）。
- 游戏模型 V2 做完（第 11 节）。动画第 1 轮做完，物品已换到 V2（第 12 节），**用户 2026-10-28 实机通过：「PASS，开始第二轮吧」**。
- 第 2 轮（瞄准时换弹，12.2 节）和 Blackridge 自己的检视（12.3 节，换掉第 1 轮照抄 P9 的检视）：**用户 2026-10-28 实机通过：「PASS，开始第三轮吧」**。
- **第 3 轮（分级换弹 16 段，12.4 节）做完，离线检查通过，没进游戏看过。**

## 1. 为什么重做

- 旧模型是从 TACZ 包改来的 Blockbench 模型，属于"所有枪从头重做"的一部分（见 P9、BR-51）。
- 旧贴图在原版下像白色塑料：颜色贴图的枪身是浅灰（亮度大约 130–170），63% 的像素在 `_s` 里标成全金属（F0 255）。原版没有反射，浅灰金属就是一块灰塑料；开光影后大面积镜面反射天空，又整片发亮。

## 2. 参考资料

- 沙漠之鹰 Mark XIX（Magnum Research / IMI）：
  - [Wikipedia: IMI Desert Eagle](https://en.wikipedia.org/wiki/IMI_Desert_Eagle)：导气和枪机结构、保险、各口径弹匣容量、Mark XIX 尺寸。
  - [Athlon Outdoors: Easy Disassembly of the MRI Desert Eagle Mark XIX](https://athlonoutdoors.com/article/easy-disassembly-of-the-mri-desert-eagle-mark-xix/)：分解步骤和枪管锁的位置。
  - 经销商规格（Kinsey's、Rainier Arms）：.50 AE 全长、高、宽、重量、扳机距离、瞄准基线。
- 规格：全长 273 mm（10.75 in）、枪管 152 mm（6 in）、高 159 mm（6.25 in）、宽 32 mm（1.25 in）、空枪约 2.0 kg（72 oz）、扳机距离 70 mm（2.75 in）、瞄准基线 216 mm（8.5 in）、.50 AE 弹匣 7 发。
- 规则：只参考真枪资料，不参考别的游戏或模组的模型。

## 3. 和真枪对得上的部分（机构和操作件）

| 功能 | 位置和动作 |
|---|---|
| 枪管 | 固定在上面，不随套筒动；顶部整体导轨（Weaver 型） |
| 导气 | 枪管上膛室前方开导气孔，气体经枪管下的细管往前，进枪管前端下面的气缸；套筒前端的活塞被推着往后 |
| 套筒 | 套筒就是枪机框；后面是高的后座块（装枪机、保险、照门），前面是包着气缸的前鼻，在枪管下面 |
| 枪机 | 转栓，3 个闭锁凸笋 + 1 个推弹凸笋，右边是抛壳挺；套筒后坐时凸轮销带枪机转动开锁 |
| 复进簧 | 两根，在套筒里，复进簧组件压住活塞 |
| 保险 | 套筒后部两侧的拨片（两面都有），转动一个鼓形件挡住击针 |
| 击发 | 单动，外露击锤 |
| 空仓挂机 / 套筒释放 | 机匣左侧，扳机上方 |
| 弹匣卡笋 | 机匣左侧，扳机护圈后面的按钮 |
| 分解 | 左侧前方按住枪管锁销，同时把右侧的枪管锁杆逆时针转；枪管往前一点再往上取下，套筒往前取下，再取复进簧组件和活塞 |
| 弹匣 | .50 AE 单排 7 发 |

## 4. 我们自己的外形（第 1 轮）

- **枪管块**：截面是下宽上窄的六边形（两侧下半竖直，上半斜收到顶面），不是真枪的三角截面；两侧各一个长圆底窗（深 0.9 mm），左窗刻 `BLACKRIDGE .50`，右窗刻 `.50 AE`；前端下面一块方头凸耳盖住气缸前端，凸耳前面是气缸端盖孔；枪口是内凹的冠部。
- **导轨**：枪管顶上，11 道横槽，前后两端斜切。
- **套筒**：后座块两侧 9 道前倾防滑槽；前鼻上半往里收 1 mm，一条直线从后座块拉到前端；右侧刻 `BLACKRIDGE FIREARMS`，左侧刻 `MESA ROJA  S6`（厂家在 S6 的 Mesa Roja，见 `docs/world/federation_companies_v1.md`）。
- **照门 / 准星**：照门卡在后座块顶上的燕尾槽里，方口缺口加两个白点；准星是枪管前端的刀片，一个白点。
- **机匣**：扳机护圈是方形前端，前面 6 道横向防滑槽；左侧前方是枪管锁销，右侧是带一字槽的枪管锁杆。
- **握把**：一体的聚合物包覆握把，满面圆点颗粒；前后背带圆角；底下是弹匣底垫。
- **操作件**（裸钢）：带减重孔和防滑槽的击锤、两侧保险拨片、长的空仓挂机杆（后端有拇指防滑槽）、弹匣卡笋按钮、宽扳机。

### 4.1 第 3 轮：按沙鹰参考量轮廓

- 参考模型（只用来量，不进游戏、不进仓库）：放在 `D:/Minecraft Modding/AFL-reference/blackridge_50/`，`CREDITS.txt` 写了作者和协议：
  - 「Desert Eagle」by ELIZION（Sketchfab，CC BY），主要依据；
  - 「Desert Eagle .50AE」by drcrazzie（Sketchfab，CC BY），核对用。
- 量法：`measure.py`（后台 Blender）把参考按全长 273 mm 缩放，换到我们的坐标系，逐个零件算 0.5 mm 一列的侧面轮廓、几处横截面和水平截面的凸包，写成 `measure_elizion.json`。缩放后高 155 mm、宽 32.6 mm（不含保险），和真枪规格（159、32）对得上。
- 和沙鹰一样（一眼认得出来的地方）：
  - 全长贯通的套筒侧板，后部高块；
  - 枪管截面下圆上三角，顶宽约 13 mm；
  - 圆头扳机护圈，护木下的导轨；
  - 往后伸的尖尾舌，大握把；
  - 保险拨片的位置，空仓挂机杆、枪管锁的位置。
- 我们的细节：
  - 套筒后部 7 道前倾 15° 的宽防滑槽（沙鹰是竖的细槽）；
  - 枪管顶上 8 道宽槽的导轨；枪口两侧各两道斜向泄气口（沙鹰是顶上的口）；
  - 握把：满面圆点颗粒，Blackridge 椭圆厂标（山脊线），沙鹰那里是圆形徽章；
  - 带减重孔的环形击锤；保险拨片上 3 道防滑槽；
  - 刻字：左侧 `BLACKRIDGE  .50 AE`，右侧 `BLACKRIDGE FIREARMS` / `MESA ROJA  S6`。
- 脚本 `gun_r3.py`。机匣轮廓按列的栅格描边，带扳机护圈开口；其余零件取侧面外轮廓，和截面凸包求交。
- 图：`design/items/blackridge_50/sheets/r3/`（两种涂装各 6 张，对比图 `br50_r3_black_vs_two_tone.png`）。

## 5. 涂装（两种都出图，用户看效果再定）

- **黑钢**：全枪发黑涂层（34 sRGB），只有击锤、保险、空仓挂机、扳机、枪机这些机加工件是裸钢。原版只看颜色贴图，靠棱边亮线、凹处暗部读出钢件（和 P9、BR-51 一样）；光影下大面积按涂层处理（F0 约 24），不整片反射天空。
- **双色**：枪管块（含导轨和准星）是喷砂不锈钢（哑光，粗糙度约 0.44），其余同黑钢。喷砂面本来就不该有镜面反射，原版下主要靠颜色和细颗粒；风险是原版里可能偏灰，进游戏前要按游戏比例出图再看。
- 不用抛光银：抛光面全靠反射环境才像金属，原版没有反射，就是灰白塑料；把反光画进颜色贴图的话，枪转动时反光不跟着动，开光影又和真反射叠在一起。

## 6. 尺寸（毫米；y = 0 在握把尾部的尾舌尖，朝枪口为 +y；z = 0 在握把底面，朝上为 +z）

| 部位 | 位置 |
|---|---|
| 套筒后座块 / 前鼻 | y 8–120，z 100–150 / y 118–252，z 100–121 |
| 枪管块 / 枪口 | y 120–273，z 121–150；枪口 y 273（枪管 153） |
| 枪膛轴线 | z 134 |
| 导轨 / 准星 / 照门 | y 138–252，顶 z 155.2 / y 255–269，顶 z 161 / y 12.6–27.4，顶 z 160（瞄准基线 238，真枪 216） |
| 扳机 / 扳机护圈 | y 88–97，z 66–86 / 开口 y 84–133，z 61.5–86.5 |
| 握把 | 后背带 (23.5, 92) 到 (2.5, 6)，前背带 (79, 66) 到 (60.5, 6)，宽 32 |
| 全长 / 全高 / 宽 | 273 / 161（到准星顶，不含弹匣底垫）/ 32（握把）|
| 扳机距离 | 约 70（扳机面到后背带，按扳机高度量） |

## 7. 游戏里的大小

用户要求重新调大小。做法：按 P9 现在游戏里的"每毫米多少像素"来定 Blackridge 的比例，同一把尺子量两把枪。沙鹰全长是 Five-seveN（P9 的外形参考，208 mm）的 1.31 倍，高 1.09 倍。第一人称、第三人称、背包图标都按这个比例，进游戏前出一张和 P9 并排的对比图。

## 8. 接下来的顺序

1. ~~用户看概念图，定外形和涂装~~（R3 + 黑钢，2026-10-28）。
2. 游戏版：低模 + 烘焙贴图 + LabPBR，见第 11 节（2026-10-28 用户：「按 2，先做游戏模型」，声音等动画做好再接）。
3. 骨骼和动画（`GunStance.carried` 加 Blackridge，用 P9 的手枪姿势）：大腿枪套掏枪、收枪；第三人称胸前预备、冲刺；低头交给身体；换弹（熟练 / 有经验 / 新手三档，空仓、瞄准换弹）；检视。第一人称照旧用老的持枪画面，带手肘和袖子。
4. 声音：Firefly，一次出声。
5. 游戏数据（.50 AE、7 发、伤害、射速、换弹时间）先不动，动画时长和数据冲突时列出来给用户定。

## 9. 文件

- 脚本：`design/items/blackridge_50/source/blender/`（`lib.py`、`render.py` 从 P9 V3 复制改名；`materials.py` 用 P9 的材质层；`gun.py` 整把枪；`finals.py` 出图；`run_concept.py` 后台入口）。
- 运行（不开 Blender 窗口，不占用别的会话）：`blender -b --factory-startup -P run_concept.py -- <black|two_tone> <输出目录> [采样数] [镜头...]`；第 3 轮加环境变量 `BR50_GUN=gun_r3.py`（存成 `br50_concept_r3.blend`）。
- 概念图：`design/items/blackridge_50/sheets/br50_<black|two_tone>_0N_*.png`（主视图、左、右、后上、前、射手视角）。
- `.blend`：`design/items/blackridge_50/source/blender/br50_concept.blend`（黑钢）。

## 10. 声音提示词（Firefly，2026-10-28）

用户：「我想重新生成音效，你按照后面要重做的动画，把音效提示词发我」。第一版把成对的动作（弹匣出 / 入、套筒拉 / 放）放在一条录音里，用户改成：「不要连起来的音效吧，还是每一个动作一段，然后你拼接，我会尽量找音调差不多的，不然现在老丢声音」。所以**一个动作一次生成**，掏枪、收枪这种两步的动作也拆开，由我按动画时刻拼接。同日用户又说：「我觉得弹匣出和弹匣入可以再拼在一起试试看，卡笋单独」，所以弹匣出 + 入先试一条录音（4+5 合成一条，见下面），丢声音就退回 4、5 分开生成。所有手上动作的提示词开头用同一句，用户挑音调相近的录音，我统一响度。

检视、三档分级换弹、瞄准换弹都复用换弹的剪辑；空仓挂机放套筒用「套筒复位」；新手插歪顶住时没有声音（同 BR-51）。不做消音器（枪口自带泄气口，见用户 2026-10-28 的讨论），旧的 `suppressed_fire_sound`、`blackridge_50_suppressed`、`magazine_flick` 新动画不用。

| # | 录音（建议文件名） | 声音 ID | 用在 |
|---|---|---|---|
| 1 | `br50_开火.wav` | `fire`、`fire_near` | 开火 |
| 2 | `br50_空击.wav` | `dry_fire`（这把枪自己的，不再用共用的 `native_gun_dry_fire`） | 没子弹扣扳机 |
| 3 | `br50_卡笋.wav` | `magazine_release` | 换弹、检视 |
| 4 | `br50_弹匣出.wav` | `magazine_out` | 换弹、检视 |
| 5 | `br50_弹匣入.wav` | `magazine_in`、`magazine_seat`（结尾的「咔」切出来） | 换弹、检视，分级换弹靠 `magazine_seat` 找入枪时刻 |
| 6 | `br50_套筒后拉.wav` | `slide_back` | 空仓换弹、检视 |
| 7 | `br50_套筒复位.wav` | `slide_release` | 空仓换弹、检视、空仓挂机放套筒 |
| 8 | `br50_保险.wav` | `safety_lever`（关、开都用） | 掏枪、收枪、检视 |
| 9 | `br50_解开枪套.wav` | `draw` 的前半 | 大腿枪套掏枪 |
| 10 | `br50_拔枪.wav` | `draw` 的后半 | 大腿枪套掏枪 |
| 11 | `br50_插回枪套.wav` | `put_away` 的前半 | 收枪 |
| 12 | `br50_合上枪套.wav` | `put_away` 的后半（和 9 很像，听着能用就复用 9） | 收枪 |
| 13 | `br50_持枪动作.wav` | `foley_lower` | 胸前预备、冲刺、检视翻枪 |
| 可选 | `br50_压弹.wav` | `mag_load`、`mag_load_last` | 默认用 P9 的压弹降调（同 BR-51） |

开火：`A single gunshot from a heavy .50 caliber magnum semi-automatic pistol, fired outdoors in a wide open field, recorded close to the shooter. One very loud, deep, sharp boom with a heavy low-frequency punch, the heavy steel slide cycling with a quick metallic clack right after, then a short natural outdoor echo fading out over about one and a half seconds. One shot only.`

其余都以这一句开头：`Close-miked foley of a large, heavy all-steel .50 caliber semi-automatic pistol, recorded alone in a quiet, acoustically dead studio, dry and close, at a real hand pace.` 后面接：

2. 空击：`The trigger is pulled on an empty gun and the exposed steel hammer snaps forward: one single crisp, solid metallic click with a slight heavy snap, very short. Silence before and after.`
3. 卡笋：`A thumb presses the small steel magazine release button on the side of the frame: one single short, firm click. Silence before and after.`
4. 弹匣出：`The released single-stack steel magazine slides downward out of the bottom of the grip and is pulled free by hand: one short, smooth metallic scrape that fades out softly as the magazine comes away into the open hand. One movement only.`
4+5. 弹匣出 + 入（先试这条，`br50_弹匣出 + 入.wav`）：`The released single-stack steel magazine slides downward out of the bottom of the grip and is pulled free by hand: one short, smooth metallic scrape that fades out softly as the magazine comes away into the open hand. Then about one second of silence. Then the same magazine is pushed back upward into the grip by the palm: one short sliding metal scrape that ends in one firm, clear latch click as it locks in place. Two separate movements in total.`
5. 弹匣入：`The single-stack steel magazine is pushed upward into the bottom of the grip by the palm: one short sliding metal scrape that ends in one firm, clear latch click as it locks in place. One movement only.`
6. 套筒后拉：`The heavy steel slide is pulled fully back by hand and held there: one deep, smooth metal-on-metal slide ending in a solid clunk at the rear. One movement only.`
7. 套筒复位：`The heavy steel slide, held fully back, is let go and slams forward under two strong springs: one loud, heavy, solid metallic clack as the bolt locks. One impact only.`
8. 保险：`A thumb flips the small steel safety lever on the slide: one firm, crisp detent click of a turning steel drum. One click only.`
9. 解开枪套：`A thumb pushes forward the retention hood of a molded polymer holster strapped to the thigh, unlocking it: one short, firm plastic click. One click only.`
10. 拔枪：`The heavy steel pistol is pulled quickly up out of a stiff molded polymer holster strapped to the thigh: one short dry scrape and a light rustle of a canvas trouser leg. One movement only.`
11. 插回枪套：`The heavy steel pistol is pushed down into a stiff molded polymer holster strapped to the thigh: one short dry scrape ending in one firm click as the holster locks around it. One movement only.`
12. 合上枪套：`The retention hood of a molded polymer holster strapped to the thigh is rotated back over the pistol and snaps shut: one small, firm plastic click. One click only.`
13. 持枪动作：`The pistol is shifted in both hands and brought up close to the chest, then lowered again: soft rustle of a canvas jacket sleeve, a light tap of the palm on the textured polymer grip, very gentle and quiet, about two seconds.`
可选 压弹：`One single large brass .50 caliber cartridge is pressed by a thumb down into a steel pistol magazine against a stiff spring: one short, firm, metallic click-slide. One cartridge only.`

每条生成一次，挑音调相近的用。

### 10.1 收到的录音（2026-10-28，`E:/Download`，48 kHz 立体声 32 位浮点）

用户：「掏枪和收枪音效先复用p9的，不好做」。所以 9–12 条不生成，`draw` / `put_away` 用 P9 的；持枪动作（13）也先用 P9 的 `foley_lower`，压弹用 P9 降调。

| 文件 | 长度 | 内容（按 10 ms 能量和频谱看） | 打算切 |
|---|---|---|---|
| `开火.wav` | 3.0 s | 0–0.97 s 一声枪响和余响，峰值 +3 dBFS（浮点超过满幅） | 整段，限幅到 −1 dBFS |
| `空击.wav` | 0.34 s | 0.02 s 一声脆响 | 整段 |
| `按弹匣卡笋.wav` | 1.0 s | 0.01 s 按下的一声（−17.5 dB），0.88 s 一声很轻的（−27 dB，松开） | 0–0.3 s |
| `弹匣出.wav` | 0.61 s | 0.04 s 一下金属响，之后一串小的摩擦和碰撞到 0.57 s，带约 1.5 kHz 的金属余音 | 整段 |
| `弹匣入.wav` | 0.81 s | 0.39 s 推入，0.69 s 很响的卡笋「咔」（−7.9 dB） | 0.30–0.62 s `magazine_in`，0.62 s–结尾 `magazine_seat` |
| `套筒拉 + 放.wav` | 5.0 s | 0.05 + 0.33 s 一对（拉开、撞到后端），1.60 s 一声最重的（低频最强，复位），4.55 + 4.85 s 又一对（多拉了一次） | 0–0.6 s `slide_back`，1.5–2.0 s `slide_release`；4.5 s 以后不用 |
| `保险关 + 开.wav` | 3.0 s | 0.02 s 一声较轻的，1.33 s 和 2.62 s 两声差不多的 | 用 1.33 s 那声 |

整体有一层 200 Hz 以下的底噪（约 −75 dB），切的时候加高通。

## 11. 游戏模型 V2（低模 + 烘焙贴图 + LabPBR，2026-10-28）

用户：「按 2，先做游戏模型」。外形是批准的 R3（`gun_r3.py`），全枪黑钢。**还没接进物品**：物品、第三人称持枪和动画还指着旧的 `blackridge_50`（旧动画是按 TACZ 那套骨骼做的，新骨骼对不上），等新动画做好一起换，和 BR-51 一样。

### 11.1 文件

| 用途 | 路径 |
|---|---|
| Blender 工程（`BR50_hi` 烘焙用高模、`BR50_lo` 游戏低模、`BR50_bake` 烘焙合并件） | `design/items/blackridge_50/source/blender/br50_game.blend` |
| 脚本 | 同目录：`game_run.py`（后台入口）、`game_build.py`、`game_bones.py`、`game_cull.py`、`game_uv.py`、`game_bake.py`、`game_save_bakes.py`、`game_compose.py`、`game_export.py`、`game_compare.py`（和 P9 并排的对比图） |
| 低模导出（Blockbench 像素坐标，UV 2048，左上角原点） | `design/items/blackridge_50/source/blackridge_50_v2_game_lowpoly.json` |
| Blockbench 源（Free Model，内嵌颜色贴图） | `src/main/blockbench/blackridge_50_v2_native.bbmodel` |
| 源贴图 | `src/main/blockbench/textures/blackridge_50_v2_native{,_s,_n}.png` |
| 构建 / 运行时导出 | `tools/build-blackridge-50-v2-native.mjs`、`tools/export-blackridge-50-v2-native.mjs`（`--check` 只核对） |
| 运行时（新文件，还没被引用） | `geo/blackridge_50_v2_native.geo.json`、`meshes/blackridge_50_v2_native.aflmesh.json`、`textures/item/blackridge_50_v2_native{,_s,_n}.png`、`models/item/blackridge_50_v2_native_in_hand.json` |

重建（都在后台 Blender 里跑，不开窗口，不碰别的会话的 Blender）：

```
blender -b --factory-startup -P design/items/blackridge_50/source/blender/game_run.py -- build uv
blender -b --factory-startup -P design/items/blackridge_50/source/blender/game_run.py -- bake compose export
node tools/build-blackridge-50-v2-native.mjs
node tools/export-blackridge-50-v2-native.mjs
blender -b --factory-startup -P design/items/blackridge_50/source/blender/game_compare.py -- <输出目录>
```

`BR50_LO_Q`（默认 0.6，这一版用 0.85）、`BR50_BAKE_RES`（4096）、`BR50_BAKE_DIR`（4096 烘焙图存放处，不进仓库）是环境变量。

### 11.2 内部结构（游戏版才有，`INTERIOR`）

概念图只有外形。动画要拉套筒、换弹匣，所以加了这些（概念图不变）：

- **套筒下半截改成 U 形槽**（宽 ±14.3 mm，侧壁 2 mm，从后座块里面一直到前鼻的横梁），包着机架和枪管下半部，和真枪一样。参考模型的截面凸包把它算成了实心，套筒一后坐缝里只能看到一块板。
- **枪管后端**：枪机凹槽（直径 17.2 mm，深到 133.2）和弹膛（直径 13.8 mm）。
- **枪机**：参考的侧面轮廓再和它的截面相交，后坐时露出来的是圆的。
- **弹匣**：参考模型的弹匣（单排 7 发，宽 20 mm，往后倾 10.1°），顶部挖空放子弹（内宽 14.4 mm，前后壁 1.3 mm）；托弹板单独一件，平时停在口唇下面（空弹匣的位置），有子弹时动画把它往下推 9.5 mm。
- **弹匣井**：机架和握把沿弹匣斜度挖穿（间隙 0.4 mm），上面通到套筒的槽里。
- 防尘盖那段机架本来就在套筒前鼻里面，导气活塞和复进簧都在机架里，套筒后坐时也看不到，所以没做（试做过，几乎全被包住）。

套筒行程按参考量出来约 **75 mm**：枪机面（133）要退到弹匣最上面那发子弹的底部（60.6）后面才能推弹。挂机时套筒后端伸出握把后面约 46 mm，真沙鹰也是这样。具体后坐多少由动画定。

### 11.3 比例和骨骼

- **比例**：和 P9 V3 用同一把尺子，每像素 16.99015 mm，第一人称显示缩放同样是 0.53，所以两把枪在手里的大小比例就是真枪的比例：全长 273 对 208 mm（1.31 倍），高 1.09 倍。
- **握持点对齐 P9**：握把中间、尾舌下面的手握点（y 68，z 78 mm）放在 P9 骨骼的握持点 HP [0, 3, 2.06] 上，P9 的手枪姿势、手持 `display`（直接用 P9 V3 的 `in_hand`）不用重算。沙鹰枪膛轴线比手高 56 mm（P9 40 mm），这是真枪的高枪膛。
- 坐标换算（概念毫米 → Blockbench 像素）：x / 16.99015；3 + (z − 78) / 16.99015；2.06 − (y − 68) / 16.99015。`game_export.py` 和构建工具的 `mm()` 是同一个式子。
- 骨骼（按沙鹰机构）：

| 骨骼 | 部件 / 位置 |
|---|---|
| `gun_body` | 机架、握把、厂标、握把螺丝、枪管块（固定）、导轨、准星、后部、枪管锁杆和锁销 |
| `barrel`（不动） | 子骨骼 `muzzle_anchor`、`chamber_round_anchor`、`front_sight`、`sight_anchor`（准星在枪管上，不随套筒动） |
| `slide` | 套筒、照门和白点；子骨骼 `bolt`（转栓，绕枪膛轴转）、`safety`（保险在套筒上，绕鼓轴转）、`rear_sight`、`ejection_anchor` |
| `hammer` / `trigger` | 击锤（销轴 y 40.5，z 113）/ 扳机（y 109.5，z 104） |
| `slide_stop` / `magazine_release` | 左侧空仓挂机杆（后端销轴）/ 弹匣卡笋按钮 |
| `magazine` / `follower` | 弹匣体和底垫 / 托弹板；`magazine_round_anchor` 是最上面那发的底部，子弹平躺朝枪口 |
| `mag_out` → `reload_magazine`、`empty_old_mag` | 换弹用的两份弹匣（带托弹板） |
| 其他 | `handling`（= 握持点）、`righthand` / `right_hand_anchor`、`lefthand` / `lefthand_pos` / `left_hand_anchor`（P9 的位置）、`positioning`、`maintenance_anchor`、`camera`（P9 的） |

### 11.4 网格、UV、烘焙、贴图

- UV、烘焙、合成都照 P9 V3 / BR-51：一张 2048 图集（Smart UV 33°，轴对齐打包，边距 4 像素），4096 烘焙，每根骨骼单独一组（运动件不烘进邻件），6 个 pass（颜色、粗糙度、材质常量、完整法线、几何法线、AO 2.5 mm），合成后缩到 2048。
- 材质：套筒、机架、枪管是发黑涂层（F0 24），击锤、保险、扳机、枪机这些机加工件是钢（F0 28，和 P9 一样按有涂层的非金属烘，原版不会发白），握把和底垫是聚合物（F0 10），照门白点 F0 10。游戏材质参数和 P9 一样：`GRAIN_K` 0.18、`ALB_K` 2.0、`STEEL_BASE` 0.030；合成去斑驳（`MOTTLE_KEEP` 0.25）和淡颗粒也一样。

### 11.5 数据和验证（2026-10-28，离线）

- 低模 `LO_Q` 0.85：26 个物体，7076 个三角形；剔除埋在同组别的零件里的面 97 个（`game_cull.py`）。Blockbench 源 32 个网格（弹匣三件各多两份给换弹用），**三角形当量 7400**（上限 16384），33 根骨骼，sidecar 1634102 字符（上限 4 MiB）。
- UV 填充 61.7%，密度 3.89 像素/毫米（P9 4.92，这把枪大 1.3 倍）。
- 烘焙 4096，6 个 pass 共 525 s（OptiX）；4096 的烘焙图没进仓库，在会话临时目录，可以从 `br50_game.blend` 和脚本重新烘。
- `node tools/export-blackridge-50-v2-native.mjs --check` 通过。
- 检查过（Blender 后台渲染，低模按骨骼着色）：套筒后坐 75 mm 时缝里看得到枪机、枪管后端的凹槽和弹膛、机架顶面、弹匣口唇和托弹板；弹匣沿斜度抽出 40–45 mm，弹匣井和握把底面对得上。握把正面那道竖线是 R3 本来就有的握片和机架前缘之间的分缝，不是弹匣井挖穿。
- 对比图（`game_compare.py`，用导出的低模和游戏贴图，在同一个像素空间里）：`design/items/blackridge_50/sheets/game_v2/`：
  - `side_*`：和 P9 同比例并排的左侧正交图（上面是 P9）；
  - `hero_*`：右前上方；
  - `slide_*`：套筒后坐 + 弹匣抽出一半；
  - `*_vanilla`：原版模拟（颜色 × 原版实体的两盏固定光，没有高光、没有法线）；`*_pbr`：LabPBR 转 Principled；
  - 拼图 `br50_v2_game_sheet.png`（2026-10-28 发给用户）。
- **没进游戏看过**：物品还没换到新模型（见本节开头）。原版下整枪是 34 sRGB 左右的黑，和 P9 一样靠棱边亮线、缝隙 AO 和握把颗粒读出细节；要在游戏里看是否太黑。

### 11.6 接下来

1. 用户看对比图（外形、大小、原版和光影下的效果）。
2. ~~动画、换模型、声音~~：第 12 节（第 1 轮，2026-10-28）。
4. 物品栏图标、HUD 剪影、Wiki 图（进游戏 PASS 以后）。

## 12. 动画（2026-10-28 起，三轮）

用户 2026-10-28：「可以，开始做动画吧 / 我们之前的COD参考还能不能用」；弹匣系统选「接，和 P9 一样」；「另外，现在所有手枪的idle，基本上都用的是一个姿势吧，这样最好」。

做法照 BR-51 动作重做（[br51_animation_redo_v1.md](br51_animation_redo_v1.md)），每轮用户进游戏看过再做下一轮：

| 轮 | 内容 |
|---|---|
| 1 | 第一人称：待机、空仓待机、开火、掏枪、首次掏枪（拉套筒）、收枪、战术 / 空仓换弹和没弹匣版、4 段检视、2 段退弹匣（16 段）；第三人称：待机、冲刺、大腿枪套掏枪 / 收枪（和 P9 共用一个姿势）、换弹 2 段、检视 4 段、退弹匣 2 段、压弹；7 发弹匣物品；换模型；接声音 |
| 2 | 瞄准时换弹（战术、空仓和没弹匣版） |
| 3 | 分级换弹 16 段（有经验、新手、新手插歪），从熟练档派生 |

COD 参考还能用（.50 GS：MW2019 全武器 07:17、MW2（2022）01:00–01:15、MW2019 第三人称 02:33 和 05:18），截帧只放临时目录。只看枪和手的位置、节奏。

### 12.1 第 1 轮（2026-10-28，实机 PASS：「PASS，开始第二轮吧」）

**所有手枪一个姿势**：Blackridge 的握把放在 P9 的同一个手点上（第 11.3 节），两只手的静止位置、物品显示（0.53）、身上的大小（`GunStance` 0.49467）都用 P9 的。`gunPropExport` 导出的两只手握点和 P9 的完全一样。第三人称待机、冲刺、掏枪、收枪不另做，由 `tools/build-pistol-shared-actions.mjs` 从 P9 的复制，只把枪道具换成自己的（P9 那边改了重跑一次；以后的手枪加一行）。

**第一人称**（`tools/author-blackridge-50-v2-native-animations.mjs`，从 P9 V3 的脚本复制，求解器和烘焙相同）：
- 手调关键帧（`handling`、`lefthand`、`camera`，检视加 `mag_out`）的初值由 `tools/seed-blackridge-50-v2-hand-keys.mjs` 从 P9 V3 已通过的关键帧复制，按下面的节拍分段拉伸（贝塞尔手柄跟着缩放），之后在 Blockbench 里改。种子脚本发现 Blackridge 已有手调关键帧时拒绝覆盖。
- 机构：弹匣顺着 10.1° 的倾角滑出（长 7.39 px，比 P9 长 3 px）；空仓时套筒停在后面（`SLIDE_LOCK` 4.21 px），空仓挂柄抬起，右手拇指压下；开火套筒全行程 4.41 px（75 mm）；检视翻到右侧时保险杆扳下再扳上（各一声）。
- 节拍（秒；入枪 = 游戏的 `mag_in_tick`）：

| 片段 | 卡笋 | 弹匣出 | 取新弹匣 | 对准 | 入枪 | 放套筒 | 左手回握 | 结束 |
|---|---|---|---|---|---|---|---|---|
| `reload_tactical` | 0.30 | 0.33 | 0.64 | 0.92 | 1.25 | | 1.72 | 2.45 |
| `reload_empty` | 0.30 | 0.33 | 0.66 | 0.96 | 1.30 | 1.44 | 1.88 | 2.70 |
| 没弹匣版 | | | 每个节拍早 0.1 s（`NO_MAG_CUT`） | | | | | 2.35 / 2.60 |

  检视 11.1 s、没弹匣检视 6.75 s、退弹匣 1.45 s，和 P9 相同（检视已换成自己的，见 12.3 节）。首次掏枪 1.3 s（套筒重，拉得慢一点）。
- 声音时刻：`magazine_in` 在入枪前 0.39 s，`magazine_seat` 在前 0.07 s（录音里卡笋的「咔」落在入枪那一刻）；`magazine_out` 在弹匣出前 0.02 s；`slide_release` 的撞击落在套筒合上那一刻。

**第三人称**（`tools/author-blackridge-50-reload-actions.mjs`，从 P9 的复制）：换弹、检视、退弹匣的枪位、左手路线和 P9 相同，换成 Blackridge 的弹匣道具（`player_props/blackridge_50_mag.json`、`_mag_old.json`）和节拍。看弹匣和退弹匣时枪比 P9 高 0.8 px、近 0.2 px，不然弹匣滑出来那一下左手差 1.07 px 够不着。压弹 `gun/blackridge_50_mag_load` 由 `tools/build-blackridge-50-mag-load.mjs` 从 P9 的换算（同 BR-51 的做法）。

逐帧检查（5 个俯仰角）：
- 换弹 2 段、退弹匣 2 段：0 帧不合格。手离目标最多 0.57 px，手腕 ≤ 40°，扭转 ≤ 29°。
- 检视 4 段：30–31 帧「手臂离胸口 1.5 px（要求 2 px）」，P9 检视同样的帧（游戏里已通过）。
- 低头 45° 时，看弹匣那一刻枪进身体判定框 0.2–0.3 px（P9 是离开 1.1 px，Blackridge 枪大）。

**弹匣**：`blackridge_50_magazine`，7 发，钢制单排，枪自己的弹匣骨骼切出来（`tools/build-blackridge-50-magazine.mjs`），用枪的贴图，物品立直 10.15°、抬高 3.55866 px，质量 0.11 kg（估计）。中英文名称和说明已加。

**换模型**（同一轮）：
- `AflItems` Profile 换到 `blackridge_50_v2_native`（16 段片段）。
- `GunStance` 加 Blackridge（P9 的大小和手）。
- 物品模型指向 V2。
- 枪数据：瞄准点改成后照门（`rear_sight`，[0, 7.54969, 2.76629]）；显示改成 P9 的（0.53）；`empty_mag_in_tick` 23 → 26（空仓换弹入枪 1.30 s）；子弹锚点 `mag_out_round_anchor`，手里弹匣的子弹 `item_rounds`；加 `magazine_slot`；空击声改成自己的 `blackridge_50_dry_fire`；去掉消音声（这把枪不接消音器）。
- 伤害、射速、换弹时间（2.45 / 2.70 s）没动。
- 老的 `blackridge_50` 骨骼、动画和声音文件都留着。

**声音**：`tools/build-blackridge-50-sounds.mjs` 从第 10.1 节的录音切出：fire、fire_near、dry_fire、magazine_release、magazine_out、magazine_in、magazine_seat、slide_back、slide_release、safety，放在 `sounds/weapons/blackridge_50_v2/`。录音复制了一份到 `E:/Download/AFL/音效原始/blackridge_50/`，防止 `E:/Download` 里同名文件被下一把枪覆盖。掏枪、收枪、`foley_lower` 用 P9 的；压弹用 P9 的录音降调（0.85）。`sounds.json` 里老的事件改指新文件；老动画用到的 `suppressed`、`magazine_flick`、`safety_lever` 还指老文件。

离线图：`design/items/blackridge_50/anim_r1/`（第一人称和 P9 对比、换弹、检视、首次掏枪、退弹匣；第三人称待机和 P9 对比、换弹、检视）。

**还没做 / 已知**：
- 物品栏图标还是老的（`blackridge_50_inventory`），HUD 剪影、Wiki 等进游戏通过以后再做。
- 第一人称首次掏枪拉套筒时，左手方块挡住大半把枪（P9 也是这样，Blackridge 枪大，更明显）。
- 看弹匣时左手托着底板，弹匣比 P9 长，举到脸左边时上端离画面顶不远。

### 12.2 第 2 轮：瞄准时换弹（2026-10-28，实机 PASS：「PASS，开始第三轮吧」）

用户 2026-10-28：「PASS，开始第二轮吧」。

**第一人称**（4 段：`reload_tactical_ads`、`reload_empty_ads`、`reload_tactical_no_mag_ads`、`reload_empty_no_mag_ads`）：
- 手调关键帧初值由 `tools/seed-blackridge-50-v2-hand-keys.mjs` 从 P9 V3 的瞄准换弹复制，按 Blackridge 的节拍分段拉伸（同第 1 轮）。和 P9 一样，左手回握比腰射晚 0.1 s；没弹匣版用没弹匣腰射版的节拍。
- 时长、入枪时刻和腰射版相同（2.45 / 2.70 s，入枪 1.25 / 1.30 s），游戏数据不用改。
- 预览：瞄具始终在画面中间，换弹时枪只下沉一点。
- `AflItems` Profile 加了这 4 段。

**第三人称**（`gun/blackridge_50_reload_tactical_ads`、`_reload_empty_ads`）：视线不离目标，枪在视线下方稍微下沉、侧倾，左手到弹匣底。从 P9 的瞄准换弹换算，左手伸手时间 0.26 s（和 P9 相同）。枪回到瞄准位：战术 1.30–1.66 s，空仓 1.49–1.81 s。

逐帧检查（5 个俯仰角）：两段都是 0 帧不合格，扭转 27° / 28°。低头 45° 时枪进身体判定框 0.65 px（P9 是离开 0.26 px，Blackridge 枪大）。试过把枪往前放（-7.6、-8.0），左手就够不到弹匣，所以保留 P9 的位置。

离线图：`design/items/blackridge_50/anim_r2/`（第一人称和 P9 对比、第三人称两段）。

### 12.3 Blackridge 自己的检视（2026-10-28，实机 PASS：「PASS，开始第三轮吧」）

> 2026-10-29：后半段的扳保险换成了看弹匣（有弹匣时），见 12.6。

用户 2026-10-28：「另外，br50的检视动画，你不会是照抄P9的吧？」→「给 Blackridge 单独做一套检视」。第 1 轮的 4 段检视确实是 P9 V3 的关键帧原样复制（节拍也一样），只是机构不同。旧的留在 `design/items/blackridge_50/anim_inspect/inspect_p9_copy_r1.animations.json`。

参考：MW2019 .50 GS 的检视（截帧只放临时目录）。重枪由一只有力的手展示：
1. 左手松开，右手单手把枪举高，枪口朝上，前臂内转，让左侧面朝眼睛（看 0.65 s），再小幅换个角度（1.60–1.85 s）。
2. 前臂往回转过水平位置，套筒顶面和右侧朝眼睛，左手从下面托住枪身前部（护木下方、扳机护圈前面），双手托着重量（2.15–2.95 s 到位，停到 4.15 s）。空仓时套筒停在后面，抛壳窗敞开对着眼睛。
3. 左手放开，前臂继续转到右侧（抛壳窗、刻字）朝眼睛（4.25–5.05 s），拇指把保险扳下再扳上（5.4 s、5.8 s，各一声）。
4. 回到持枪姿势（6.15–6.85 s），左手握回，7.15 s 结束（原来 11.1 s）。

做法和取舍：
- **右手跟着枪转**：枪的每次转动都是前臂自己的旋转和抬手，不用 P9 那种「手腕反向转回、枪在拳头里转」的做法（用户同日指出 P9 检视时右手不会转，后面再查）。
- **不露手肘**：试过把枪横举在脸前（枪口朝左、前臂横过画面、手肘在右边露出来，用户：「如果可以的话，是完全可以展示手肘的，我们做了就要用」），用户看图后改主意：「这种短枪我感觉不能用手肘，视角有点奇怪，还是用手臂？」。所以前臂一直从画面下方伸进来。
- **没有弹匣检查**：参考里没有，换弹和退弹匣已经展示弹匣。没弹匣的两段和有弹匣的动作相同（看弹匣井要把握把底转到眼前，只有横举才做得到）。
- 4 段长度都是 7.15 s。

文件：
- 第一人称：`tools/author-blackridge-50-v2-native-animations.mjs`（`INSPECT_T`、`INSPECT_POSES`、`inspectKeys`，左手和保险由脚本解算）；手调关键帧（`handling`、`camera`）由 `tools/seed-blackridge-50-inspect-keys.mjs` 写进 bbmodel，之后可以在 Blockbench 里改（脚本只覆盖自己写的和第 1 轮复制的关键帧，改过的拒绝覆盖）。
- 第三人称：`tools/author-blackridge-50-reload-actions.mjs` 的 `inspect(empty, noMag)`。第三人称手臂有手腕和肘部，侧面靠手腕偏转枪口来展示（像第一人称那样翻 80° 前臂，扭转会超过 30°）。看顶面时单手：左手托枪身前部时前臂会横压在枪和脸上，握回握把在那个翻转角度下扭转超过 60°。

检查：
- 第一人称刚性探针（`tools/probe-fp-rigidity.mjs`，已加 Blackridge）：静止段 52%（P9 原检视 30%），来回反向 8 次（P9 54 次），右手握把滑动 0（P9 4.3），左手在枪上滑动 0.2。
- 第三人称逐帧检查（5 个俯仰角）：44 帧「左臂离胸口不到 2 px」，都出现在左手放下（0.32–0.36 s）和握回（6.52–6.54 s）的瞬间，和 P9 检视同类（P9 实机已通过）。手腕最大 49°，扭转 25°，枪离身体判定框最近 0.33 px。

离线图：`design/items/blackridge_50/anim_inspect/`（`fp_inspect.png`：第一人称检视、空仓检视的关键姿势；`tp_inspect.png`：第三人称正面和侧面）。

**待查**：P9 检视时右手不跟着枪转（用户 2026-10-28：「P9的手腕是不是有问题，检视的时候右手不会转，这个可以后面再查」）。原因应该是 P9 检视的 `bentRight`（右手骨骼反向转回，让前臂保持从右下方伸进来）。

### 12.4 第 3 轮：分级换弹（2026-10-28，离线通过，没进游戏看过）

用户 2026-10-28：「PASS，开始第三轮吧」。

**做法**：P9 V3 的分级换弹（[gun_proficiency_v1_plan.md](../dev/gun_proficiency_v1_plan.md) 第 4 节）搬到 Blackridge，动作和姿势相同，每档的节拍按「Blackridge 熟练档 / P9 熟练档」放慢：战术 2.45 / 1.7 = ×1.44，空仓 2.70 / 2.0 = ×1.35。插歪那一下的动作（顶住、再推一下、拔出、摆正）保持 P9 的速度，不放慢。服务器按每段的长度和 `magazine_seat` 音效定换弹时间和装弹时刻（`NativeReloadTiers`），游戏数据不用改。

| 片段（有弹匣） | 战术 | 空仓 | P9（战术 / 空仓） |
|---|---|---|---|
| 有经验 `_experienced` | 3.31 s（入枪 1.73） | 3.78 s（入枪 1.67） | 2.3 / 2.8 |
| 有经验瞄准 `_experienced_ads` | 3.31 s | 3.65 s | 2.3 / 2.7 |
| 新手 `_novice` | 4.47 s（入枪 2.88） | 5.13 s（入枪 2.70） | 3.1 / 3.8 |
| 新手插歪 `_novice_fumble` | 5.37 s | 6.03 s | 4.0 / 4.7 |

没弹匣版（`reload_*_no_mag_<档>`）每段早 0.1 s，共 16 段。

- 有经验和新手的空仓换弹：左手从上面拉套筒（套筒拉开时空仓挂柄落下）。有经验瞄准版：右手拇指压下空仓挂柄放套筒。
- 声音时刻用 Blackridge 自己的提前量（同第 1 轮），另加 `slide_back` 和 `slide_release`。
- 插歪：弹匣歪着进到离入口 1 px 处卡住，拔到快要脱出（按 Blackridge 的弹匣长度换算 P9 的位置）。
- 手调关键帧由 `tools/seed-blackridge-50-v2-hand-keys.mjs` 从 P9 V3 同名片段复制。每段的节拍按名字一一对应，分段拉伸。
- `AflItems` Profile 加了这 16 段。

**第三人称**（`tools/author-blackridge-50-reload-actions.mjs`，从 P9 的复制）：6 段（有经验 / 新手 × 战术 / 空仓，新手插歪 × 2），加上有经验瞄准 2 段（熟练瞄准换弹按节拍映射拉伸）。没弹匣版由游戏把完整动作从 0.1 s 处开始播。新加辅助道具 `player_props/blackridge_50_rack.json`，是拉套筒时左手的落点，跟着套筒走。Blackridge 套筒后端离握把比 P9 远约 1.6 px，所以拉套筒时枪往前、往右各多放一点（`RACK_SHIFT` [-1.0, 0, -1.6]），不然左臂会贴着胸口。

**检查**：
- 第一人称刚性探针，和 P9 实机通过的分级片段同一水平：
  - 静止段 26–63%（P9 19–59%）；
  - 右手滑动 3.35（P9 3.11）；
  - 左手在弹匣上滑动：新手 0.97–1.07（P9 0.67–0.80，弹匣长了 3 px）。
- 第三人称逐帧检查（5 个俯仰角）：

| 动作 | 不合格帧 | P9 |
|---|---|---|
| 有经验战术 | 1 | 0 |
| 有经验空仓 | 0 | 0 |
| 新手战术、插歪 | 14 | 8 |
| 新手空仓、插歪 | 30 | 8 |
| 有经验瞄准 2 段 | 0 | 0 |

不合格帧全是「左臂离胸口不到 2 px」，出现在左手去弹匣袋、去套筒的路上。手腕最大 50°，扭转 27°。低头 45° 时，新手的枪进身体判定框 0.59 px（P9 是离开 0.16 px），有经验瞄准是 0.62 px（同第 2 轮）。

离线图：`design/items/blackridge_50/anim_r3/`（`fp_tiers.png`：第一人称有经验战术、有经验空仓、新手战术、新手插歪、有经验瞄准空仓；第三人称有经验空仓、新手插歪战术）。

**接下来**：物品栏图标（换成 V2 的 3D 渲染）、HUD 剪影、Wiki，都等实机 PASS 以后做。

### 12.5 材质发灰、枪套里不显示（2026-10-29，实机 PASS：「枪套也没问题了，材质也OK」）

用户 2026-10-29：「我觉得这把枪的材质有点奇怪，为啥会这样？P9就很好看」「这把枪不会在枪套里面渲染（P9是有的）」。

**材质**：两把枪的合成脚本相同（`game_compose.py`），颜色贴图也几乎一样（中位 33 / 36）。差别在 `_s` 的光滑度：

| 材质（F0） | P9 占比 / 光滑度中位 | Blackridge 原来 | Blackridge 现在 |
|---|---|---|---|
| 聚合物（10） | 63% / 105 | 19% / 106 | 不变 |
| 涂层钢（24） | 23% / 175 | 75% / 171 | 118 |
| 裸钢（28） | 13% / 180 | 6% / 175 | 145 |

P9 只有窄窄的套筒是亮的，Blackridge 整把都是涂层钢，大块平面在光影下像镜子一样反射天空，所以发灰。BR-51 的金属是 120，用户认可，所以涂层压到 118 左右（按比例缩放，保留原来的明暗变化）。
- 做法：`design/items/blackridge_50/source/retone_spec_v2.py` 从原来的亮面贴图生成，原图另存在同一目录的 `blackridge_50_v2_native_s_glossy_2026-10-28.png`。
- `game_compose.py` 加了同样的 `SMOOTH_SCALE`，以后重新合成，结果一致。
- 再用 `tools/export-blackridge-50-v2-native.mjs` 拷进游戏资源。

**枪套**：画身上武器的代码（`CarriedWeaponsLayer`）所有手枪共用。逐项对过：同步规则、武器角色、物品类、`geo` 骨骼、网格分层、手持缩放 0.49467、收枪片段长度 0.5 s，两把枪都一样。代码对创造模式不区别对待（10-17 已修过创造模式里 P9 消失的问题），日志里也没有报错。
- 现在加了诊断日志 `[AFL HOLSTER]`：自己的枪套状态一变就写一行，内容是 2 号位的物品、角色、是否戴枪套、选中格、是否创造、是否画、是否被隐藏、手持缩放、外形范围、网格。
- 等下一次实机日志定位原因，找到后删掉。

### 12.6 检视：扳保险换成看弹匣（2026-10-29，实机 PASS：「枪套也没问题了，材质也OK，提交吧」）

用户 2026-10-29：「这把枪的检视，到后面会拨动保险，但是我看不见手去操作，你看看能不能改成把弹匣抽出来看（有的话），并且做成和P9不一样的」。

前半段（举高看左侧、短暂换角度、翻到顶面、左手托住枪身）不变，只换后半段。12.3 那一版（带扳保险）原样存在 `design/items/blackridge_50/anim_inspect/inspect_r2_safety_pass.animations.json`。

**有弹匣**（`inspect`、`inspect_empty`，7.15 s → 10.3 s）：
1. 4.15–4.75：枪转到检查姿势（枪口朝上，左侧朝眼，握把底朝左，枪在右前臂上方，不被挡住）；左手从枪身前端沿下沿滑到弹匣底板。
2. 4.95：拇指按卡笋，弹匣弹出 0.3 px 落到手指上。
3. 5.1–5.55：左手捏住底板，把弹匣沿轴线慢慢抽出（往左下）。
4. 5.6–6.15：举到枪的左边，两样都在画面里，顶端朝眼睛（看最上面那发子弹）；停住。
5. 6.9–7.2：小幅转过去看右侧面；停住。
6. 7.8–8.35：送回弹匣口；8.75 用掌根推到底，越推越快，撞到位；枪被顶得往上一跳。
7. 9.0：捏住底板往下拽一下，确认卡住，枪跟着往下顿一下（推—拉检查）。
8. 9.3–10.0：收回，左手回到握把。

和 P9 的区别：

| | P9 | Blackridge |
|---|---|---|
| 露出弹匣口 | 枪在手里侧滑 | 不侧滑，底板本来就露在拳头下面约 1.4 px |
| 取出弹匣 | 掉进等在下面的手里 | 手指捏住底板抽出来 |
| 看弹匣 | 拿到画面左边，翻来覆去看两侧和顶部 | 举在枪旁边，只倾一下看顶部，再转一次看侧面 |
| 装回 | 拍进去 | 推到底，再往下拽一下 |

**没弹匣**（`inspect_no_mag`、`inspect_empty_no_mag`，7.15 s）：转到右侧看，只去掉了扳保险（保险不再动，也不放 `safety` 音效）。

音效：`magazine_release`（卡笋）、`magazine_out`（抽出时）、`magazine_in` 和 `magazine_seat`（推到底，提前量和换弹相同）。

**代码**：
- 第一人称：`tools/author-blackridge-50-v2-native-animations.mjs`（`INSPECT_T` / `INSPECT_NO_MAG_T` 分开，新姿势 `INSPECT_POSES.CHECK`，弹匣举起的两个姿势 `SHOW` / `SHOW_SIDE`）。枪和镜头的手调关键帧由 `tools/seed-blackridge-50-inspect-keys.mjs` 重新写入。左手、弹匣、推的那一跳和拽的那一下由代码算出。调参数用环境变量 `BR50_I_CHECK`、`BR50_I_SHOW_AT` 等。
- 第三人称：`tools/author-blackridge-50-reload-actions.mjs` `inspect()`。枪用退弹的检查姿势；弹匣举在左胸前（再往外，左手就够不到了）；拽的那一下是枪往下顿 0.4 px。

**检查**：
- 第一人称刚性探针：静止段 66%，反向 10 次，右手打滑 0（P9 检视：30% / 54 次 / 4.26）；左手腕最大 96°（P9 检视 73°，P9 战术换弹 86°），出现在把弹匣竖着举起来时。
- 第三人称逐帧检查：有弹匣版 57 个不合格帧，全是前半段已经 PASS 的部分（0.32–4.38 s，左臂离胸口不到 2 px）；新的弹匣检查段没有不合格帧，手腕最大 49°，扭转 24°，手到底板的偏差 0.57 px。低头 45° 时，7.0 s 那一帧枪进了身体判定框 0.26 px（用的是退弹的姿势）。没弹匣版 44 帧，和上一轮一样。
- 离线图：`design/items/blackridge_50/anim_inspect/fp_magcheck.png`、`tp_magcheck.png`。

### 12.7 去掉掏枪、收枪音效（所有枪，2026-10-29）

用户 2026-10-29：「我觉得不能给掏枪和收枪做音效，因为每把枪的材质不一样，P9的可能很不错，但是BR就不行，干脆都别做了」。

所有枪的 `draw`、`put_away`、`first_draw` 片段去掉了开头的掏枪 / 收枪音效；`first_draw` 里拉套筒 / 拉机柄的声音保留。
- 有生成脚本的 4 套（Blackridge、BR-51 V3、P9 V3、P9 V2）：改脚本后重新生成。逐片段对比过，只有这几条音效不同，关键帧一帧没变。
- 没有生成脚本的 3 套（cat、HR55、旧 BR-51）：直接从运行时动画和 bbmodel 里删掉这一条。
- 音效文件和注册的事件留着，没有删除，只是不再用到。退弹里的 `foley_lower`（放低枪）不属于掏枪收枪，保留。

### 12.8 物品栏图标和 HUD 剪影（2026-10-29，实机 PASS）

用户 2026-10-29 实机：「图标和剪影没问题，枪管也好了，一起提交吧」。

用户 2026-10-29：「你顺便改一下剪影和物品栏贴图」。两张都换成 V2 游戏低模画的图（原来是旧的银色 Blackridge），弹匣在枪里，托弹板不画。

- **物品栏图标**：`tools/render-blackridge-50-inventory-icon.mjs`。角度对齐 P9 的图标：从右前上方看右侧，枪口朝右下、朝向镜头（偏转 45°、俯视 20°、画面内转 -12°，`ICON_VIEW` 可调）。黑钢贴图在 BR-51 那套光照下几乎全黑，所以亮度乘 1.8（`ICON_EXPOSURE`），和 P9 的 Blockbench 渲染接近。脚本只写源图 `src/main/blockbench/inventory_icons/blackridge_50_inventory.png`，游戏用的图由 `tools/normalize-native-gun-inventory-icons.py` 生成（LARGE_PISTOL，最长边 0.88）。
  - 注意：这个取景脚本一次会重写所有枪的图标。BR-51、HR55、Silverwood 12 已提交的图标本来就和它的输出有像素差，这次没有动它们，只更新了 Blackridge。
- **HUD 剪影**：`tools/render-blackridge-50-hud.mjs`（照 `tools/render-br51-01-hud.mjs`），从左侧正视，枪口朝左，灰色，640 × 364。`native_guns/blackridge_50.json` 的 `presentation.hud` 改成 48 × 27：宽度按实际长度对 P9 的 36 换算，高度按新图的比例（HUD 只用这个比例）。
- 对比图：`design/items/blackridge_50/icons_v2_vs_p9.png`。
