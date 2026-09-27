# P9-01 V2 Pure Mesh Rebuild + Visible Internal Mechanics + High-Fidelity Geometry Refinement（Pure Mesh 几何尚未接入运行时）

> 2026-09-27：P9-01 V2 已在资产侧迁移到 AFL Native Rig 并重做 9 条动画，新源为 `src/main/blockbench/p9_01_v2_native.bbmodel`，独立运行时资源见 [p9_01_v2_native_rig_v1.md](p9_01_v2_native_rig_v1.md)。本文件描述的 `p9_01.bbmodel`（旧 TACZ/G19 Rig）及其运行时资源保持不变，仍是游戏当前使用的版本，直到 Java 迁移完成。生成器 `tools/build-p9-01-v2-mesh.mjs` 之后只服务 V2 Native 源。2026-09-27 起的生成器改动只进入 V2 Native 源，未回写 `p9_01.bbmodel`，详见 [p9_01_v2_native_rig_v1.md](p9_01_v2_native_rig_v1.md) 的「滑套前端噪点修复」和「Surface Detail Pass」：
>
> - 滑套前端去细条面、绘制器硬边改写；
> - 锯齿由凸条改为切槽，新增滑套前段台阶、握把凹陷纹理面板、护木侧槽与食指定位凹台，尾托加长上翘，控件细节，弹匣底板唇边；
> - 生成器现为 12508 三角形。下文的三角形数与绘制器描述对应 `p9_01.bbmodel` 当时的版本。

状态：`src/main/blockbench/p9_01.bbmodel` 的可见几何已由旧 168 个 Cube 替换为 32 个原创 Pure Mesh，并补齐玩家正常可见的内部机械；High-Fidelity Geometry Refinement 后为 11204 三角形（精修前 2936；其中两份弹匣各 1380，任一时刻正常只显示一份）。状态：GEOMETRY_FREEZE_CANDIDATE（仅编辑器内验证）。**Pure Mesh 几何尚未接入运行时**：`geo/p9_01.geo.json` 与 `textures/item/p9_01.png` 仍是旧 Cube 资产，游戏内仍显示旧 P9。`animations/p9_01.animation.json` 已单独同步下述两条换弹片段的 handling 位移，其他动画保持原样；该动画变更尚未实机验证。P9 目前没有 `.aflmesh.json` sidecar，Hybrid Mesh 导出路径待工程接入（HYBRID_EXPORT = PENDING ENGINEERING PASS）。不要对新源运行旧的 `tools/export-native-gun.mjs`：它按旧 Cube 源编写，不支持 Mesh。

## draw / put_away 重做（handling）

源文件与运行时 `animations/p9_01.animation.json` 已同步；运行时只替换了 `draw` 与 `put_away` 两段，其余 7 条动画逐项不变。关键姿态经 Catmull-Rom 平滑后按 60fps 烘焙为线性关键帧（与 Blackridge 做法一致）；`root` 两段均固定为待机值（rotation z -20.557、position 0），修掉了旧 put_away 首帧 `root` 旋转为 0 的跳变。

- `draw`：0.6 秒，`hold_on_last_frame`（约 12 tick）。`handling` 从画面外右下方（position `[-4, -11, 3]`，rotation `[-30, -12, -45]`，内倾 45°）扫入，0.22 秒接近到位（内倾余 18°、枪口上扬 6°），0.36 秒反向过冲 4°，0.46 秒再衰减，0.6 秒精确落到待机。`lefthand` 0.2 秒前藏在左下方，0.34 秒搭上握把，之后跟随枪的回稳。`camera` 最大偏转约 1.2°。
- `put_away`：0.45 秒，`hold_on_last_frame`（原为 `loop: true`、1.3 秒且仅前 0.167 秒有动作）。0–0.06 秒小幅上提预备，`lefthand` 0.12 秒内加速离开，`handling` 以 ease-in 内倾 40°、枪口下压 35° 落向右下方画面外。
- 数值为源文件约定；运行时按导出约定 position x 取反、rotation x/y 取反。draw 末帧与 `static_idle` 的 `lefthand` / `root` / `camera` 值逐项一致。
- 音效：按要求删除了两段原有的音效触发点（`p9_01_draw`、`p9_01_put_away`，均在 0 秒），等待重新生成。音频文件和 `sounds.json` 登记未改动。Java 的 put_away 只播放 tick ≤ 1 的触发点，新 put_away 音效需放在 0 秒。
- 已知限制：切枪时 Java 同时给旧枪触发 put_away、给新枪触发 draw，原版会立刻停止渲染旧物品，因此游戏内通常看不到完整 put_away。
- 验证：仅 Blockbench 关键时间点渲染；参考手臂当前隐藏，未检查手部贴合；未编译、未进游戏验证。

## 生成方式

全部几何由 `tools/build-p9-01-v2-mesh.mjs` 生成（`node tools/build-p9-01-v2-mesh.mjs <out.json> <texture.rgba>`；`P9_DIAG=1` 输出高对比诊断配色，仅用于检查渲染，几何与 UV 与正式输出逐字节相同）。脚本在冻结的旧 Rig 空间笼子内用截面放样、倒角挤出、路径扫掠建模，统一面朝向（连通块一致 + 朝外投票；枪膛/膛室/击针孔/弹匣井按轴线朝内），自动展开 UV 并打包到 1024 图集（精修后约 28 px/单位），UV 以 1024 像素空间写入（Blockbench 以贴图像素尺寸归一 Mesh UV）。Base Color：`src/main/blockbench/textures/p9_01_v2_temp.png`（V3 正式 Base Color，文件名沿用以免改动绑定；`P9_DIAG=1` 时改为输出高对比识别色）。

造型语言参考现代 striker-fired 9×19 手枪（P320 / XCarry 类比例与切面），尺寸完全服从旧 P9 空间合同；未复制任何品牌造型、铭文或 Logo。用户打开的 Sketchfab P320 仅用于比例/轮廓测量，未导入其网格。

## High-Fidelity Geometry Refinement（本轮）

统一倒角语言（生成器 `offRound` / `prismXR` / `roundPoly`）：主倒角为滚圆倒角（滑套前端 r 0.10、后端 r 0.08、机匣外侧 0.045、枪管罩前端 0.07），次级边 0.02–0.03，轮廓圆角 0.02–0.07；大平面保持单面。

- 滑套：截面分层为顶平面 / 窄次级斜面 / 主顶倒角 / 竖直侧面 / 下倒角，各转折带圆角；前后端滚圆倒角；枪口孔带倒角。锯齿为梯形凸棱，两端斜坡收入侧面（后 7、前 4 每侧）。
- 抛壳口：前壁补面（此前抛壳口前端可透视），右壁上沿与顶板边缘倒角，抛壳口和膛室空腔底部加入滑套导轨内唇（x ±0.56–0.62）。弹底板改为带击针孔的圆角板。
- 枪管：外圆 32 段，圆滑内凹枪口冠；枪膛 6 条浅膛线暗示。枪管罩改为沿 Z 放样的圆角截面，前端滚圆倒角，后端面开孔露出膛室（此前后端面遮住膛室内腔）；膛室口为圆角环；供弹坡为曲面并带浅槽，坡顶 5.098 与膛室底平齐。
- 机架：侧轨轮廓圆角 + 外侧滚圆倒角；滑套-机架交界上带（y ≥ 4.40）比防尘盖外凸 0.04，形成第二层硬表面；导轨各段倒角。扳机护圈路径平滑、截面圆角、宽度 0.74。
- 握把：超椭圆截面（前握面较平、后背更圆），侧面掌鼓 0.03，底部外扩与滚圆底边；弹匣井同参数截面。
- 控件：空仓挂机杆带凸起拇指垫，分解杆、弹匣释放钮圆角倒角，销钉带倒角帽。
- 扳机：修正弯曲方向——指面（-Z）内凹、下端朝枪口；截面加厚至 0.17×0.24、圆角。`trigger2` 枢轴与挂接点未动。
- 照门 / 准星：圆角倒角；照门两侧收至 ±0.57，底面降到 5.86 以贴合新顶部倒角；缺口宽度与照门顶高 6.29、缺口底 6.05、准星顶 6.29 不变（瞄准线不变）。
- 弹匣：卷边供弹唇有实际厚度（约 0.15，唇尖约 0.04），唇下缘沿以弹轴为心 r ≈ 0.255 的圆弧包住顶弹；供弹唇前端下压形成供弹缺口；底板滚圆底边、倒角上沿与前端小指托；托弹板圆角顶缘，顶面仍为 3.065。
- 三角面分布：滑套 2242、枪管/膛室/供弹坡 2002、弹匣每份 1380、机架 900、护圈 812、握把+弹匣井 800、控件 656、导轨 332、扳机 332、瞄具 368。

## V3 Base Color（本轮，仅 Blockbench 源）

由生成器内置绘制器生成（确定性哈希噪声，可复现）：逐面栅格化到现有 UV，按零件分材质，加轻微顶光朝向项（顶面 +6%、底面约 -4%）、凸棱 1 px 提亮、凹角柔和压暗，以及按 3D 位置生成的表面变化。几何与 UV 未改：输出网格 JSON 与本轮开始前逐字节一致。不做 PBR（无 `_s` / `_n`），不烘焙反射。钢件基色保持近中性灰（蓝色分量最多高 2–3），便于后续 PBR 直接作为金属反照率。

| 分区 | 基色 sRGB | 表现 |
|---|---|---|
| 滑套 | 83,84,86 近中性深灰氮化钢 | 沿滑套方向的细拉丝（仅侧面和顶面），倒角提亮；锯齿凸棱 93,94,96，每面单一色调（正面亮、斜面暗），不做逐像素亮线和噪点 |
| 抛壳口 / 滑套内壁 | 46,46,48 | 内凹面整体压暗；弹底板 95,96,98 |
| 枪管 / 枪管罩 | 126,127,129 / 120,121,123 | 比滑套亮的中性钢，独立加工钢件；供弹坡 160,161,162 抛光 |
| 枪膛 / 膛室 / 击针孔 | 22,22,24 | 越深越暗 |
| 机架 / 护圈 | 71,70,70 / 70,69,69 | 中性偏暖聚合物，极轻颗粒（±0.4%）；滑套下沿接缝处压暗 14% |
| 握把 | 64,63,63 | 哑光聚合物，握持区（弹匣系 y -0.45–2.65）柔和过渡的细颗粒，无边界线 |
| 导轨 | 80,80,82 | 中性深钢，每面单一色调 |
| 控件（空仓挂机杆、分解杆、弹匣释放钮、抽壳钩、击针尾盖） | 97,97,99 深枪灰金属 | 与滑套接近但更亮、更中性，独立零件可读 |
| 销钉 | 112,112,114 | 亮钢 |
| 扳机 | 55,54,55 | 比机架更深的聚合物 |
| 准星 / 照门 | 44,44,45 | 深黑钢，轮廓提亮；无夜光点 |
| 弹匣本体 / 供弹唇 | 73,73,75 / 87,87,89 | 工业深色钢，比滑套更哑 |
| 弹匣底板 / 托弹板 | 64,63,63 / 82,78,70 | 聚合物；托弹板偏暖 |

可见 9×19 弹药：模型当前没有可见子弹（顶弹由 `magazine_round_anchor` 预留），本轮不适用。

## Rig 合同（未改）

48 根原有骨骼的名称、父级、枢轴、旋转与提交版本一致（`left_hand_anchor` 仅有 1e-15 的浮点序列化差异）；精修后全部 50 个组（含两个顶弹锚点）与精修前逐项一致。几何精修完成时，9 条动画（static_idle、empty_idle、shoot、draw、put_away、reload_tactical、reload_empty、inspect、inspect_empty）共 9031 个关键帧与旧版逐帧数值一致，含 10 个音效事件。其后仅两条换弹动画新增下述 handling 位移，故该历史关键帧总数不再是当前值。muzzle / ejection / sight / hand / camera anchor 未动。旧视觉子组 `1`、`2` 保留为空组。

## Reload Handling Pass（2026-09-26）

在正式 Blockbench 源和 `src/main/resources/assets/apocalypse_firstlight/animations/p9_01.animation.json` 中，仅修改 `reload_tactical`、`reload_empty`：现有 `g19_and_mag` 增加整体位置轨道，`righthand` 原位置轨道按同一偏移量补偿。`lefthand`、弹匣与滑套的局部轨道、camera、音效 marker、Rig 层级和几何均未改。位移使用密集采样的平滑起止曲线，均不增加旋转，结尾偏移严格为零。

| 片段 | `g19_and_mag` 最大源坐标偏移 | 侧移 | 保持 | 回位 | 原时长 |
|---|---|---|---|---|---|
| `reload_tactical` | `(＋2.00, ＋0.75, 0)` | 0–0.30 s | 0.30–0.92 s（覆盖 0.36/0.79 s 弹匣事件） | 0.92–1.70 s | 2.38 s |
| `reload_empty` | `(＋2.60, ＋0.975, 0)` | 0–0.25 s | 0.25–1.77 s（覆盖 0.26/0.76 s 弹匣和 1.64 s 套筒事件） | 1.77–2.72 s | 3.12 s |

Blockbench 离屏视角已检查两种换弹的开始、侧移、弹匣、套筒（空仓）及回位姿态；该视角不能代替游戏第一人称实机验收。未运行客户端、编译或游戏测试。

新增动画骨骼 `handling`（为 draw / put_away 重做准备）：层级为 `root → handling → g19_and_mag + righthand`，`lefthand` 仍直接挂在 `root` 下独立运动。Blockbench 枢轴 `[-0.6, 2.4, 2.5]`（握把中上部、右手掌侧），运行时 `geo/p9_01.geo.json` 已同步（pivot `[0.6, 2.4, 2.5]`，X 按导出约定取反），共 45 根骨骼。现有 9 条动画在 `handling` 骨骼上没有关键帧，保持零位，表现不变（上文换弹的“handling 位移”是 `g19_and_mag` 位置轨道加 `righthand` 补偿，不是这根骨骼；两者现在都位于 `handling` 之下，零位时结果不变）；Java 按名字查找 `gun` / `right_hand_anchor` / `left_hand_anchor` / `root` / `g19_and_mag`，不受多一层父级影响。未做游戏内验证。

新增（无可见几何）：`magazine_round_anchor`（父 `magazine`）与 `empty_old_magazine_round_anchor`（父 `empty_old_magazine`），origin `[0, 3.39381, 1.85725]`、rotation `[-22, 0, 0]`，对应标准弹匣顶弹中心，供后续 Dynamic Magazine Top Round 复用。

## 几何挂接

| 骨骼 | Mesh |
|---|---|
| slide2 | slide_body（前段+枪口孔+套管）、slide_chamber_cavity（抛壳口前 0.6 的倒 U 空腔）、slide_port（U 形抛壳口段）、slide_port_front（抛壳口前壁）、slide_rear、slide_breech_plate、slide_firing_pin_hole、slide_extractor、slide_serrations、slide_striker_cap |
| front_sight / rear_sight | front_sight_post / rear_sight_body |
| barrel4（固定视觉枪管） | barrel_tube（冠状枪口）、barrel_bore（r 0.22，深 1.06）、barrel_hood、barrel_chamber_mouth、barrel_chamber（r 0.28）、barrel_feed_ramp |
| frame | frame_receiver（外侧导轨）、frame_receiver_core（被供弹通道切开的中芯）、frame_rail、frame_trigger_guard、frame_grip、frame_magwell、frame_controls（空仓挂机杆、分解杆、弹匣释放钮、销钉） |
| trigger2 | trigger_blade（静态） |
| magazine / empty_old_magazine | magazine_body（开口弹匣壳+供弹唇）、magazine_baseplate、magazine_follower（各一份，几何相同） |

## 关键尺寸与间隙（静态几何核对）

- 比例：P9 全长 10.8 单位 ≈ 203 mm，1 单位 ≈ 18.8 mm；9×19 弹径 ≈ 0.53、全长 ≈ 1.58。
- 弹匣井：弹匣本体 ±0.56 / z 0.92–2.61（弹匣系）对弹匣井 ±0.615 / 0.855–2.695，间隙 0.055 / 0.065 / 0.085；底板位于握把底面以下。弹匣顶部世界高度 ≤ 3.66，低于机匣底 3.80。
- 顶弹：弹轴位于弹匣系 x 0、y 3.33，沿弹匣轴，弹头朝前；精修后供弹唇到弹轴最近距离 0.2526（弹径 r 0.265，嵌入约 0.012 起夹持作用），托弹板顶面 3.065 与弹体相切；弹长 1.53 在弹匣内腔 0.98–2.55 内。弹匣中宽于 ±0.44 的部分在世界高度 ≤ 3.787（低于机匣侧轨底 3.80），更窄的唇部进入 ±0.45 供弹通道，最高 3.872。
- 供弹路径：弹匣井向上由机匣中的倾斜供弹通道（±0.45）延续到机匣顶面；供弹坡（±0.24）自膛室口下沿斜向通道；膛室口 r 0.28 ≥ 弹壳 r 0.265。受旧弹匣轴约束，弹匣顶部与枪膛之间有约 1.9 单位的风格化高度差。
- 滑套行程：shoot 1.6、empty_idle 1.88。挂机时抛壳口位于供弹通道正上方，可见膛室罩与膛室口、供弹坡、供弹通道、弹匣顶部/托弹板与弹底面（击针孔）；滑套底面 4.80 高于尾部护板 4.60。
- 抛壳：ejection_anchor 位于右侧抛壳口开口内，右侧无遮挡。

## 验证范围

仅 Blockbench 静态几何核对与逐段动画姿态渲染（诊断配色；精修后重新检查了 static_idle、shoot、empty_idle、reload_tactical、reload_empty、inspect、inspect_empty 以及左右侧、前后 45°、顶部、枪口、抛壳口、供弹唇、握把/护圈、挂机与照门视角）。参考手臂按显示比例过大，无法在编辑器内判断手部接触；手部 anchor 与握把轮廓未变，沿用旧接触关系。未做游戏内验证；V3 Base Color 已存在于 Blockbench 源，尚未制作 PBR、扩容弹匣、红点、枪口配件、动态顶弹或 Pure Mesh 几何运行时导出；仅上述两条换弹动画已同步运行时资源。
