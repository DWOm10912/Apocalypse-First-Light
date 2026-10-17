# 联邦全国道路：隧道 V1

日期：2026-10-16。分支 `feature/federal-tunnels`（C 的 worktree `AFL-federal-worldgen-v1`，从 master `6bab93f9` 切出；2026-10-16 用户批准后快进到 master `7507bd11`（皮卡物理第 1、2 包、压草、D 的外部网格和坐姿），未提交的改动带过来，冲突（`gradle.properties` 注释、两个 lang 文件的 Jade 条目）两边都保留；同步后编译、`roadSurfaceCheck`、`vehicleCheck`、隧道部件 GameTest 都通过）；提交 `850205b7`（用户 2026-10-16 批准），Main 把 master 快进到这个提交（未推送）。

**状态：T-A + T-B + T-C 一次实现（用户 2026-10-15：“就按照你说的做隧道V1，一次性补齐所需的mesh模型+PBR”），用户实机看了四次：前两次不通过，问题已修；第三次"基本上可以了"，问部件碰撞，部件碰撞已加（第 7 节）；第四次（2026-10-16，世界 `tun4c`）用户实机 PASS。洞内卡顿不在这次验收里；2026-10-16 接上 Vextris 显卡缓存，用户开光影再看：PASS（第 9 节）。随后用户要求部件能拆下带走，做了隧道部件可拆 V1.1（第 7 节"可拆部件"）。用户第一次看发现牌子挂不到普通方块上，已改（surface14）；用户 2026-10-16 在 `tun5c` 里再看：PASS。** 已做的检查：离线检查（种子 4）、`compileJava`、部件网格过 Vextris 加载器、生成器 `--check`。GameTest 和隔离客户端新世界探针的结果见第 10 节。用户在游戏里还没看过。

**不发光（用户 2026-10-15：“会发光的模型默认是灭的，末日也不会有电”）**：灯具、信号灯、车道控制灯、情报板都是灭的。镜片和 LED 面是暗色，所有 `_s` 贴图 alpha 都是 255（LabPBR 不自发光）。洞内没有任何光源，光照和别处一样取世界的光，所以深处是黑的。概念里“基本照明”这一项取消。

概念图：`design/worldgen/federal_tunnels/federal_tunnels.html`（T1–T5，用户已认可方向）。

## 1. 为什么要做

- 规划器在挖深超过上限 60 m 以上时报隧道（[C3 文档](federal_highways_c3.md) 第 3 节）。离线扫的 10 个种子里有 6 个有隧道，州际、州道都有。C3.3a 把这些路段整段丢掉；V1 起整段照建。
- 默认开发种子 -295378578869149513 没有隧道。实机验收要用别的种子，例如种子 4：州际 `interstate_ew:desert_town-county_seat` 在里程 2662–2978 有一座 316 m 隧道，`interstate_ew:valley_town-river_city` 在 1636–1788 有一座 152 m 隧道。

## 2. 资料

- FHWA-NHI-10-034《Technical Manual for Design and Construction of Road Tunnels — Civil Elements》（2009）第 1、2 章，读的是 ez-pdh 课程收录的版本：
  - 典型两车道断面（图 2-4）：马蹄形拱、两侧防撞墙加扶手、缝隙排水；
  - 车道不小于 3.6 m，洞内路肩缩窄；人行道 0.7 m 以上，疏散通道不小于 1.12 m；
  - 净空：公路至少 4.9 m；
  - 疏散出口间距不超过 300 m；
  - 射流风机挂在拱顶，纵向通风；
  - NFPA 502 的门槛：长于 90 m 要能在洞外拦车，长于 240 m 洞内要有车道控制灯、情报板等。
- Caltrans Caldecott 第四孔：净空 5.1 m，双层衬砌。
- Devil's Slide 隧道：NATM 双孔马蹄形。

## 3. 规划和建成

- `RoadNetworkJson.BUILT_KINDS` 加了 `TUNNEL`，有隧道的路段不再整段丢掉。建成 JSON 里隧道是一种结构（`RoadDesign.Tunnel(s0, s1)`）。
- 路段截断时，端点离隧道至少 `RoadNetworkBuild.TUNNEL_CLEAR` = 引道 60 m + 10 m。即使是路段本身的起止端也要满足（洞口不会正对城镇或截断的路端）。别的结构仍是 `STRUCTURE_CLEAR`。
- 隧道里和前后 `RoadDesign.NO_PASS_LEAD` 内不准超车：R4 车道线改成实线（`RoadDesign.solidLanes`），R2 中线按原来的禁止超车规则画双黄线。

## 4. 断面（T1，`TunnelSection`）

两种断面都是三心圆马蹄形：两侧小圆弧（半径 rs）接竖直侧墙，拱顶一段大圆弧，起拱线由建筑限界角点决定（离侧拱留 0.2 m），至少比人行道高 2.2 m。拱按 `FACET` = 5° 分段，每段平面法线。

| | R4 州际单向孔（双孔） | R2 州道双向孔（单孔） |
|---|---|---|
| 车道 | 3.75 × 2 | 3.65 × 2 |
| 路肩 | 内侧 1.00 / 外侧 3.00 | 1.20 × 2 |
| 人行道 | 内侧 1.20（疏散，通横通道），外侧 0.75 | 两侧 0.90 |
| 人行道高 | 0.80（F 型防撞墙顶面兼人行道，墙脚外 0.25 m） | 0.80 |
| 墙面到墙面 | 13.45 | 11.50 |
| 净空 | 5.10 | 5.10 |
| 起拱 / 拱顶 | 3.00 / 8.54 | 3.00 / 7.89 |
| 衬砌厚 | 0.75（`road_structure`） | 同左 |

- 双孔：每孔内侧墙在 |u| = 8.8 m，中心距 31.05 m（R4 两幅的中心距），路线不用拉开。
- 墙面：人行道往上三排 1.2 m 高的搪瓷钢板（到底板上 4.4 m，`PANEL_TOP`），再往上是深色涂装拱顶。

## 5. 世界生成（`TunnelWorks`）

距离 d 从较近的洞口算起：洞内 d ≥ 0，引道 d < 0（洞口前 t = −d）。

- **山体（密度面）**：孔上保留山体；衬砌拱顶上不足 `COVER` = 2 m 覆土时堆回填土丘（边坡 1:1.5，规划的洞口处覆土常常几乎为零）。端墙后的岩面从墙顶按 `CUT` = 2:1 开挖。
- **引道**：60 m（`APPROACH`）。路基外是平台，40–60 m（`BLEND_FROM` 起）过渡回普通道路的土方。
  - 挡墙下面：地面（平台）照常延伸到墙脚下，墙站在地面上，跟桥的结构一样，不替换地面（2026-10-16 第二次修）。
  - 挡墙后：先是一条和墙顶齐平的平台 `BENCH` = 1.5 m（到墙尾 40 m 为止，40–60 m 收窄到 0），再从平台外缘起 2:1 岩石开挖或 1:1.5 填方。挡墙顶和平台的高度都按 `TunnelWorks.wallTop` 算。
  - 密度面在端墙前 6 m、挡墙前后（|u| 在 hw−6 到 hw+0.6 之间，t ≤ 40）保持岩石，碰撞方块不会悬空。这两条带里的地表从来不是"原样"（不返回 NaN），以前保留下来的岩石会在墙前立成土柱。
  - 墙后和墙尾外 1.5 m 内是回填碰撞（`pavement`，从路基下 1 m 到墙顶），画出来的是墙顶盖板（第 6 节）。这段碰撞单独不会让一列变成 TUNNEL 列。
- **孔**：
  - 衬砌、人行道和防撞墙是 `road_structure`（看不见的碰撞），孔内挖空，流体也清掉，孔里是干的；
  - 路面照常铺 `road_surface`，路面柱带 `RoadWorks.TUNNEL_PAVED`，路面上方挖孔，不挖到天；
  - 人行道和防撞墙伸出洞口 `LEAD` = 16 m，最后 `LEAD_RAMP` = 4 m 斜坡收口。
- **洞门**：两孔共用一道现浇混凝土端墙。R4 宽 51 m、高 12.6 m；R2 宽 26 m、高 11 m。端墙厚 1 m，带 0.3 m 压顶（外挑 0.15 m）。每孔一圈环框：从内表面量起宽 0.9 m，凸出 0.35 m。挡墙厚 0.6 m、长 40 m，墙顶从端墙高度降到 0.6 m。
- **横通道**（仅 R4，隧道长 ≥ 300 m）：每 250 m 一处，从一孔的疏散人行道通到另一孔，宽 2.4 m、高 2.2 m（`PASSAGE_W` × `PASSAGE_H`），外包 0.3 m 结构。
- **列的处理**：
  - 洞内是 NONE（山体照原样）、PAVED + `TUNNEL_PAVED`，或 TUNNEL（按 `TunnelWorks.cells` 写结构 / 空气 / 原样）；
  - 引道上有混凝土的列是 TUNNEL + `APPROACH_COLUMN`（底板以上清到天）；挡墙和平台以外是 NONE；
  - 一列里同时有混凝土和墙前地面（引道或洞口前有底板的采样）时，`TunnelWorks.column` 给它标 `ON_GROUND`。`RoadWorks.finish` 照普通道路给它铺 PAVED / TURF，再加 `RoadWorks.TUNNEL_OVER`：`RoadChunkPass` 先写地面，再从地面上一格起按 `TunnelWorks.cells` 写结构，引道上写到结构顶（`TunnelWorks.worksTop`）再往上清到天。地面四角在墙后或洞内的取底板高度，在墙前的取两者较低的。`TUNNEL_OVER` 的草皮列不长植物；
  - `RoadChunkPass` 的写入保护对隧道列到 `roof` 为止。
  - 洞口带（端墙 + 压顶下）、引道上的人行道 / 防撞墙引导段，以及它们压到路面的格子，路面那格放的是 `road_structure` 而不是 `road_surface`。`RoadCells.of` 不把这些格（隧道列里非 PAVED 的）算作路面格，客户端就不会把它们当成"被挖掉的路面"在网格上开洞。2026-10-16 第一次实机看时洞口一条黑带、引导段脚下的三角缺口和阶梯状的土色方块，都是这个原因。`ON_GROUND` 的列（地面在墙脚下延伸）不跳过。
- **碰撞**：`BridgeMesh.boxes` 改成多段（`TunnelWorks.solids`），衬砌和人行道各自是一段。
- **隧道周围的岩石（`TunnelWorks.rock`）**：地形 V2 的稳定层（`PlanStabilityDensity` 硬底、`WorldCarverStabilityMixin`）只保护规划地表往下一段深度，丘陵和褶皱带这个深度是 0，深埋在山里的孔完全没有保护，第二次实机看时一座双孔隧道露在一个大洞穴的顶上。现在两处都加了隧道岩体：
  - 洞内：从结构最低处往下 `ROCK` = 4 m 到衬砌拱顶往上 4 m，横向到土丘半宽外 4 m；
  - 引道：从结构和平台底往下 4 m 一直到地表，横向到平台外 4 m；
  - 这个范围内地表以下强制实心（硬底的 FLOOR），雕刻器不挖。`RoadWorksRef.rock` 按列缓存。

## 6. 画出来的结构（`TunnelMesh`，跟桥一起在 `BridgeMesh.finish` 里加入）

| 部分 | 材质（`RoadMesh.Material`） | 说明 |
|---|---|---|
| 墙板 | `TUNNEL_PANEL` | 2.4 × 1.2 m 一块，三排 |
| 拱顶 | `TUNNEL_ARCH` | 2.4 × 2 m 一块，深色 |
| 人行道、防撞墙、端墙、压顶、环框、挡墙、横通道 | `CONCRETE`（`reinforced_concrete`） | |
| 缝隙排水 | `DRAIN` | 防撞墙脚 0.13 m 宽，跟标线一起画 |

- 衬砌、排水沿 2.4 m 一格；人行道沿 1 m 一格。
- 扶手原来是衬砌的一部分（`STEEL`，同一格子里画），隧道部件可拆 V1.1（2026-10-16）起改成独立部件 `handrail`（第 7 节）。横通道的开口在内侧墙上，从人行道往上 2.2 m 不画墙板。
- 挡墙盖板（`TunnelMesh.caps`，2026-10-16）：每个挡墙列（`TunnelWorks.wallColumn`）方格里墙背以外的部分、墙尾以外两墙面之间的部分，按墙顶高度铺一层混凝土朝上的面。下面是回填碰撞，外侧平台的草皮和它同高，墙后不会露出一条缝看到下面的地面。
- 种子 4 的 316 m 隧道（双孔，145 个区块）共 72,800 个三角形（含盖板 369 个），UV 和法线没有坏值。
- 客户端（`RoadClient`）每个顶点的光照缓存几个 tick：玩家附近 2 个区块内每 4 tick 重取，更远的每 40–55 tick（错开）。

## 7. 设备部件（`TunnelMesh.all` 定位置，`client/road/TunnelFixtureClient` 画）

部件是独立的 Vextris 网格 `apocalypse_firstlight:road/tunnel/<名字>`，共用一张贴图 `block/road/tunnel/equipment`（2048 × 2048）。扶手是程序生成的（`TunnelParts.handrail`）。隧道部件可拆 V1.1 起每个部件是世界里的独立方块，可以用撬棍拆下，见下面"可拆部件"。

| 部件 | 位置和间距 |
|---|---|
| `luminaire` 隧道灯（灭） | 两侧上墙，底板上 5.75 m。从入口算：前 80 m 每 2.2 m，到 230 m 每 4 m，之后每 8 m，出口段最后 60 m 每 4 m。R2 两头都按入口算 |
| `jet_fan` 射流风机 | 隧道长 ≥ 300 m：每 150 m 一组，两台挂在拱顶，离孔中线 ±1.6 m；直径 1.2 m，长 5.2 m |
| `fire_door` 防火门 | 每个横通道的两孔各一个：门框、两扇门开着贴在通道墙上、上方 EXIT 牌 |
| `emergency_cabinet` 紧急柜 | 隧道长 ≥ 240 m：约每 83 m，在疏散人行道一侧墙上 |
| `egress_sign` 低位疏散指示 | 隧道长 ≥ 240 m：每 25 m，人行道上 0.5 m，箭头指向最近的出口（洞口或横通道），距离取 25 m 的整倍数（25–150）；横通道旁不放 |
| `lane_signal` 车道控制灯（灭） | 隧道长 ≥ 240 m：每条车道一个，洞口内 5 m 和每 250 m；吊在拱顶，底边在净空上 0.25 m |
| `cctv` 监控 | 隧道长 ≥ 240 m：对侧上墙 5 m，约每 125 m |
| `name_plate` 名牌 | 两端端墙：R4 在两孔之间，R2 在环框上方。名字按路和里程从 8 个里选（CEDAR PASS、HOLLOW RIDGE、IRON GAP、PINE SUMMIT、BLACK CANYON、EAGLE CREST、STONE NOTCH、RED BLUFF） |
| `clearance_plate` 限高牌 | 每孔环框顶上 0.2 m：“CLEARANCE 5.1 m” |
| `portal_signal` 洞口信号（灭） | 隧道长 ≥ 90 m：入洞方向右侧路肩外，洞口前 40 m；悬臂到每条车道上方，杆上再挂一个 |
| `headlight_sign` 开灯牌 | 隧道长 ≥ 90 m：洞口前 47 m，“TUNNEL / TURN ON / HEADLIGHTS” |
| `closure_gate` 关闭栏杆 | 隧道长 ≥ 90 m：洞口前 55 m，杆竖着（开着） |
| `cms_gantry` 情报板（灭） | 隧道长 ≥ 240 m：洞口前 200 m，悬臂伸到车道上方。200 m 处在桥上（桥跨和桥两端 20 m 内）或别的隧道引道上时，在 100–800 m 里找离 200 m 最近的空地，每 10 m 试一次，找不到就不放（`TunnelMesh.gantry`） |
| `handrail` 扶手 | 每孔两侧防撞墙上，方管 10 cm，离墙 0.1 m，高出人行道 1 m。按衬砌的 2.4 m 格切成一段一段（`TunnelMesh.handrails`），每段两个支架（不到 1 m 的一个在中间）；横通道处断开，短于 0.2 m 的碎段不放。骨骼是这段的长度（`"%.4f"` 米），原点在弦的中点、人行道高度 |

- 路边四种部件（洞口信号、开灯牌、关闭栏杆、情报板）立在路基边缘外 0.9 m（`ROADSIDE`），底座在完成后的地面上：开放道路那里是 1:4 的前坡，比路肩铰点低 0.225 m；引道 40 m 以内是平台（不降）；40–60 m 之间按 `TunnelWorks#surface` 同样的平滑过渡。朝向按部件自己所在里程的路线方向。
- 2026-10-16 之前它们都放在路肩铰点的高度上，底座离地 0.06–0.22 m；情报板固定在 200 m，种子 4 上有一块正好在 430 m 高架桥边上，立柱离谷底 21 m。朝向按洞口的方向，离洞口远了在弯道上会转歪。
- 种子 4：316 m 隧道西口前是 430 m 高架桥，情报板挪到 530 m；东口 200 m。152 m 隧道不到 240 m，没有情报板。

种子 4 的 316 m 隧道共 442 个部件，其中隧道灯 366 个。

### 可拆部件（隧道部件可拆 V1.1，2026-10-16）

用户看了部件碰撞后说："很多组件都被归类到了道路建筑？这些组件不应该独立成为模型吗，这样玩家可以拆了带走，比如说牌子、风扇、栏杆什么的"。V1.1 起 13 种设备部件和扶手都是独立的部件方块；衬砌、人行道、排水、洞门混凝土和路面还是道路结构（`road_structure`），不能拆。

**方块和方块实体**

- 每种部件一个方块 `apocalypse_firstlight:tunnel_<名字>`（14 个：`tunnel_jet_fan`、`tunnel_portal_signal`、`tunnel_cms_gantry`、`tunnel_closure_gate`、`tunnel_fire_door`、`tunnel_luminaire`、`tunnel_cctv`、`tunnel_lane_signal`、`tunnel_headlight_sign`、`tunnel_clearance_plate`、`tunnel_egress_sign`、`tunnel_name_plate`、`tunnel_emergency_cabinet`、`tunnel_handrail`），共用方块实体 `TunnelFixtureBlockEntity`。方块本身不画（`INVISIBLE`），只给碰撞、形状和挖掘；准星、选框和子弹按部件自己的网格（见下面"命中网格"）。
- C3.3b 第一组（2026-10-16，surface15）用同一套部件加了 3 种路牌：`road_stop_sign`、`road_side_road_sign`、`road_curve_sign`（`TunnelFixtureBlock.Kind` 的 `STOP_SIGN`、`SIDE_ROAD_SIGN`、`CURVE_SIGN`）；第二组（2026-10-16，surface16）又加了立交出口匝道口的 `road_do_not_enter_sign`、`road_wrong_way_sign`（`DO_NOT_ENTER_SIGN`、`WRONG_WAY_SIGN`，[第二组](federal_interchanges_c3_3b.md) 第 10 节）。第二组 B 包（2026-10-17，surface17）加了立交的指路标志 `road_guide_sign`（`GUIDE_SIGN`）：牌面内容存在 bone 里，没有固定网格，由客户端按 bone 烘焙（`client/road/GuideSignParts`）；为它 `TunnelParts.Baked` 多了一个叠层流（牌面字走标线的渲染类型，多边形偏移）和光照锚点（一块牌一个光照），`TunnelFixtureClient` 画两个流。注册名前缀是 `road_`，隧道部件是 `tunnel_`；网格同样在 `road/tunnel/` 下。它们立在路边、路口，不在隧道里：区块施工在 NONE 列（路的工程外）也放部件。位置、尺寸、拆的时间见 [C3.3b 第一组](federal_junctions_c3_3b.md) 第 5 节。
- 一个部件占它的碰撞盒伸进去超过 1/16 的所有格子（`TunnelFixtures.Part.cells`），每个格子的方块实体都存这个部件的准确位置（种类、变体骨骼、原点、三个轴）。一个格子可以同时存几个部件（两段扶手在一格里接上、疏散指示贴在扶手边上），方块是第一个部件的种类。同种类、原点差 0.1 m 以内、轴对齐（余弦 > 0.999）的算同一个部件：道路网络缓存只存 2 位小数，后来生成的区块里同一个部件的位置会差一点。
- 部件原来占的 `road_structure` 层数存在方块实体里；部件全部拆走后格子变回原来的 `road_structure`，原来是空气的变回空气。
- 形状：格子里每个部件的盒子按 1/8 格的列采样合并，加上原来的 `road_structure` 层，不再被衬砌的命中网格盖住。部件网格缺了的时候准星和选框退回这个形状。
- **命中网格（2026-10-16）**：准星、选框和子弹按部件画出来的网格（`meshhit/TunnelFixtureHits`，[命中网格](../rendering/mesh_hit_runtime_v1.md) 0.10 节）。原来的选框是上面的盒子按 1/8 格采样的结果：斜放的路牌是一圈台阶，比牌子厚很多（用户在 C3.3b 测试世界问"为什么选择区域这么奇怪"）。现在瞄到牌面、立柱才选中，瞄到旁边视线穿过去；选框是指着的那个部件的整个外形，从它的哪一格看都一样；子弹打在牌面上，从立柱旁边过去。
- 碰撞：同样的盒子，但薄的墙面部件（隧道灯、监控、限高牌、疏散指示、名牌，`TunnelFixtures.collides`）没有碰撞，走过去不会被看不见的盒子挡住，跟 V1 一样。
- 光照：不挡光（`lightBlock` 0、透天光），形状按方块实体变（`dynamicShape`），不能被活塞推（`PushReaction.BLOCK`）。

**碰撞盒**（部件自己的坐标系，第一个是主盒）

| 部件 | 碰撞盒 |
|---|---|
| `closure_gate` | 机箱 0.5 × 0.5 × 1 m；竖起的栏杆 0.8–9 m 高 |
| `headlight_sign` | 牌面连柱顶 1.6 × 1.2 m；两根立柱到 2.1 m |
| `portal_signal` | 立柱 6.6 m；悬臂；挂在悬臂上的信号头；杆上的信号头 |
| `cms_gantry` | 立柱 7.7 m；桁架上、下弦；情报板 |
| `lane_signal` | 灯箱 0.6 × 0.6 m（吊杆没有） |
| `jet_fan` | 机壳连消声器 1.28 × 1.28 × 5.2 m（吊杆没有） |
| `emergency_cabinet` | 柜体，从墙面往外 0.27 m |
| `fire_door` | 门楣；两侧门框连开着的门扇（伸进通道墙里） |
| `luminaire`、`cctv`、`clearance_plate`、`egress_sign`、`name_plate` | 灯具 / 牌面，从墙面往外，最少 0.24 m 厚（只用来占格；没有碰撞） |
| `handrail` | 扶手连支架，从墙面到管外 |

- 盒子最薄 0.24 m（`MIN`），保证 1/8 格的列采样一定碰得到；细杆的碰撞比画出来的厚一些（准星和选框按网格，不受影响）。
- 墙面部件的盒子都从墙面（网格的 z = 0）往外，不伸进墙里，所以挂在任何墙上都只占墙前面的格子。surface13 时它们是往墙里伸的：在隧道里能放（墙是 `road_structure`，可以接管），在普通方块上放不了（用户 2026-10-16 实机："牌子好像不能在普通的cube方块上面放"）。surface14 改成往外。

**拆**

- 只有撬棍能拆（`TunnelFixtureBlock.tool`）。拿别的东西（空手、镐）挖进度是 0。接线扳手不拆部件（它是接线工具，`canAttackBlock` 是 false，这次不改接线模块）。
- 时间按部件（`Kind.seconds`）：射流风机、情报板 15 s，洞口信号 12 s，关闭栏杆、防火门 8 s，开灯牌、车道控制灯、名牌、紧急柜 4 s，隧道灯 3 s，监控、限高牌、疏散指示、扶手 2 s。
- 拆的是准星指着的那个部件（格子里几个部件时按射线先打到的部件网格，打不到再按盒子；选框画的也是它）；它从所有已加载的格子里一起拿走，别的部件留下。
- 小部件掉自己的物品（带变体：疏散指示的方向和距离、名牌的隧道名），可以重新放：

| 物品 | 挂法 | 重量 | 堆叠 |
|---|---|---|---|
| `tunnel_luminaire` 隧道灯 | 墙面 | 14 kg | 4 |
| `tunnel_cctv` 监控 | 墙面 | 4 kg | 4 |
| `tunnel_lane_signal` 车道控制灯 | 天花（方块底面），吊杆顶在点的位置 | 22 kg | 2 |
| `tunnel_headlight_sign` 开灯牌 | 地面，朝玩家（16 个方向） | 45 kg | 1 |
| `tunnel_clearance_plate` 限高牌 | 墙面 | 8 kg | 4 |
| `tunnel_egress_sign` 疏散指示 | 墙面 | 3 kg | 8 |
| `tunnel_name_plate` 名牌 | 墙面 | 30 kg | 1 |
| `tunnel_emergency_cabinet` 紧急柜 | 墙面 | 40 kg | 1 |
| `tunnel_handrail` 扶手（2.4 m 一段） | 地面，朝玩家 | 12 kg | 4 |

- 放：点的面要合挂法（墙面部件点侧面，以点的位置为中心贴墙，任何方块的侧面都行；地面部件点顶面），部件占的每格都要是空气、可替换方块、`road_structure` 或别的部件格（墙面部件在点的那个方块那一层及更里面的格子不算，墙留着），范围里不能有实体，否则放不下。创造物品栏在"工业"页，疏散指示 12 种、名牌 8 种都列出来。
- 大部件拆成材料（掉在部件第一个格子上）：

| 部件 | 材料 |
|---|---|
| 射流风机 | 钢板 8、废钢 12、绝缘铜线 6、电缆 2、钢缆 2 |
| 洞口信号 | 钢梁 4、废钢 8、电缆 2、废塑料 3 |
| 情报板 | 钢梁 10、钢板 4、废钢 12、电缆 3、废塑料 4 |
| 关闭栏杆 | 废钢 4、废塑料 3、电缆 1、绝缘铜线 1 |
| 防火门 | 钢板 4、废钢 4 |

- 创造模式拆不掉落。用命令或爆炸去掉部件的一格，部件从别的格子里一起拿走，不掉落。
- 挖掘审计：部件不走原版挖掘等级——没有 `mineable/*` 和 `needs_*_tool` 标签，没有 `requiresCorrectToolForDrops()`，没有战利品表；能不能拆、要多久、掉什么都由 `TunnelFixtureBlock`（撬棍 + `Kind.seconds` + `drops`）决定。
- 提示：拿撬棍看着部件显示"拆下"（攻击键）；拿别的东西按住攻击时显示"需要撬棍才能拆下"。Jade 显示指着的部件、变体和"撬棍拆下，约 N 秒" / "撬棍拆成材料，约 N 秒"。

**世界生成**：`RoadChunkPass.parts` 在每列最后一步写部件方块。格子原来是空气或可替换方块（植物）的直接放，是 `road_structure` 的接管并记下层数，已经是部件格的加上这个部件；别的方块不放；PAVED / TURF 列只放在完成面以上。部件按列建索引（`TunnelFixtures.Index`，`RoadWorks.parts()`），`TunnelWorks.solids` 不再把部件的盒子并进 `road_structure` 的碰撞。

**画**：`TunnelFixtureClient` 每个区块扫描部件方块实体，每个部件只由它第一个格子（`Part.cells` 顺序，主盒中心的格子在前）所在的区块画，按区块烘焙后交给 Vextris 显卡缓存（`GpuGeometry`），光照用路面的那一套（`RoadClient.Light`）。方块实体加载、变化、移除时重扫周围 3 × 3 个区块，另外每 tick 轮询 16 个区块。光影的阴影 pass 不画。手里和物品栏里的部件物品由 `TunnelFixtureItemRenderer` 画（部件朝 −z 缩到一格）。

**种子 4 离线数量**（`FixtureCheck`，2 座隧道）：1,458 个部件。

| 部件 | 个数 | 占格 |
|---|---|---|
| handrail | 788 | 3,763 |
| luminaire | 582 | 2,025 |
| egress_sign | 26 | 90 |
| lane_signal | 12 | 24 |
| jet_fan | 8 | 271 |
| clearance_plate | 8 | 51 |
| emergency_cabinet | 8 | 44 |
| cctv | 6 | 15 |
| closure_gate / headlight_sign / name_plate / portal_signal | 各 4 | 57 / 44 / 84 / 124 |
| cms_gantry / fire_door | 各 2 | 123 / 37 |

- 扶手段长 0.77–2.48 m。两个部件共用格子：扶手 + 扶手 796 格、疏散指示 + 扶手 78、紧急柜 + 扶手 31、防火门 + 扶手 5、隧道灯 + 隧道灯 13、隧道灯 + 监控 3。所以一格要能存几个部件，不然有 6 段扶手一格都占不到。

### 部件碰撞（`TunnelPartSolids`，2026-10-16，已被 V1.1 取代）

第三次实机看后加的：部件的盒子放看不见的 `road_structure`，并进道路碰撞。V1.1 删掉了 `TunnelPartSolids`，碰撞改由部件方块自己给（上面）。

## 8. 贴图和网格

全部由 `tools/build-tunnel-v1.mjs` 生成（`--check` 检查是否最新）。

- **衬砌贴图**（`textures/block/road/tunnel/`，每米 240 像素，LabPBR）：
  - `panel` 576 × 288：搪瓷钢板，边缘接缝；
  - `arch` 576 × 480：深色哑光喷涂混凝土；
  - `steel` 240：热镀锌钢；
  - `drain` 576 × 32：两边钢唇中间一条缝。
- **设备贴图** `equipment` 2048 × 2048：
  - 材质块：镀锌、不锈钢、黑、红、绿、白、黄、红 / 黄 / 绿镜片、灯罩、玻璃、混凝土；
  - 文字面：8 块名牌、12 块疏散指示、限高牌、开灯牌、EMERGENCY、EXIT；
  - 熄灭的 LED 点阵面：车道灯、情报板（情报板每米 120 像素）；
  - 风机格栅。
  - 文字用 `tools/afl-sign-font.mjs` 的字母；数字、小数点、小写 m、箭头在生成器里按同样的笔画画。
  - 2026-10-16 修了数字 5：竖笔停在下半圈的肩上面，两段之间有个缺口（用户在 C3.3b 弯道牌的"50"上看到"少了一部分"）。改成竖笔一直接到下半圈的内顶，下半圈左边从内顶往下开口。疏散指示（25、75、125、150 m）和限高牌（5.1 m）上的 5 一起变了。
- **网格**（`meshes/road/tunnel/*.vxmesh.json`）：Vextris 独立格式 2，13 个，每个 12–472 个三角形。有变体的用骨骼区分：名牌按名字，疏散指示按 `l25`…`r150`，信号和情报板按 `r4` / `r2`；没有变体的骨骼是 `root`。13 个文件都能用 Vextris 0.1.0-alpha.1 的 `MeshLoader` 加载通过。
- **Blockbench 源**：`src/main/blockbench/tunnel_equipment_v1.bbmodel`（全部部件 + 设备贴图）。衬砌是程序生成的，没有 Blockbench 源。

## 9. 已知限制

- 隧道里的结构格没有损坏扫描：挖掉衬砌或人行道的 `road_structure`，画出来的衬砌不会开洞（桥的扫描不管隧道格）。
- 人行道 0.80 m 高，玩家要跳上去；横通道门口没有台阶。
- 横通道按 2.4 × 2.2 m 做，比概念图（4.2 × 4.7 m）小，只供人走。
- 州道单孔长隧道没有疏散出口（没有横通道和平行通道），只有洞口。
- 部件的碰撞是简化的盒子（第 7 节），细杆比画出来的厚。准星、选框和子弹从 2026-10-16 起按网格（第 7 节"命中网格"）：路牌的选框用户在 C3.3b 测试世界实机 PASS，隧道里的部件（灯、监控、柜子、扶手）的选框没有单独看过。
- 部件只能用撬棍拆；接线扳手不拆（接线模块这次不改）。挖掘不走原版等级（以后挖掘规则重做时再统一）。
- 拆一个部件只处理已加载的格子。部件最长约 11 m，拆的人就在旁边，那些区块都是加载的，实际碰不到；如果有格子留在没加载的区块里，那个区块加载后部件会从剩下的格子再画出来，要再拆一次。
- 大部件（射流风机、洞口信号、情报板、关闭栏杆、防火门）不能整件带走，只拆成材料；以后要整件搬运再说。
- 疏散指示和名牌重新放时还是原来的文字（变体跟着物品走），不能改字。
- **洞内卡（已接显卡缓存，等用户验收）**：原来画的部分（衬砌和部件）每帧逐顶点提交（跟路面一样）。2026-10-16 在种子 4 的长隧道里（无光影）用 JFR 采样 20 s：渲染线程 890 个样本里 `RoadClient.render` 占 68.1%（含子调用），几乎全是逐顶点调用（`BufferBuilder.nextElement` 39%、`normal` 12%、`uv` 6%、`color` 5%）。同一天接上 Vextris 0.1.1-alpha.4 的显卡缓存（`GpuGeometry`，每区块上传一次，部件烘焙在区块的实心几何里）：同一位置帧时间从 17.4–19.2 ms 降到 4.2–4.6 ms，`RoadClient.render` 降到 42 个样本。做法、自测和验收清单见 `federal_highways_c3.md` 10.5。光影包下还没测。用户实机：站着不卡了，但快速飞过新的隧道段还卡；查到主因是 `road_structure` 碰撞形状第一次计算太慢（原版环境粒子每 tick 抽查几百格），另有道路网格第一次上屏没有每帧上限，两项都已改，飞行复现最长一帧从 3.4 s 降到 84 ms（10.5"第一次加载的卡顿"）。用户 2026-10-16 再看（开光影）：PASS。

## 10. 验证

- 离线检查（`TunnelCheck`，种子 4 的建成网络）：
  - 两座隧道建成；
  - 端墙后有岩面，洞口、孔中、横通道处的格子剖面正确；
  - 三角形和部件数量见上面两节，没有坏 UV 或法线；
  - 车道控制灯顶 5.95 m，拱面在它上方 7.98–8.50 m，吊杆长到 9.15 m，伸进衬砌。
- `compileJava` 通过。
- 13 个部件网格过 Vextris 加载器（`ALL OK`）。
- `node tools/build-tunnel-v1.mjs --check`：CHECK OK。
- GameTest（`-PaflWithoutShaders`）：86 个，16 个失败，跟基线一样。
- 隔离客户端探针（种子 4，测试世界 `tun4a`，`only=tunnels`，11 个站点：两座隧道的洞口、中点、横通道、引道外 30 m）：
  - 隧道格全部按设计建成（每个站点 `tunnel cells as built` 全对）；
  - 碰撞顶比设计最多高 0.034 m；
  - "路面列上方的方块" 33–271 处，抽查的例子全是 TURF 列上一格的草、蕨、花（植物，不是方块堆）；
  - 引道外 30 m 的两个站点"桥面下 solid" 12 / 26：是附近桥下 OPEN 列里高于规划地面的实心方块，属于桥的检查，没有细查；
  - 2026-10-16 修 `RoadCells` 后重开：客户端 201 个区块网格，被判为挖掉的路面格 0（修之前没有统计这个数）。
- **用户第一次实机看（2026-10-16）不通过**：洞口路面一条黑带透天、引导段脚下三角缺口、阶梯状土色方块，原因和修法见第 5 节"列的处理"最后一条。
- **用户第二次实机看（2026-10-16，世界 `tun4a`）不通过**：
  - 挡墙和引导段旁、中央带里草皮边缘锯齿状：引道上有混凝土的列整列换成了结构，地面断掉。改成墙站在地面上（`ON_GROUND` / `TUNNEL_OVER`，第 5 节）；
  - 挡墙前的土柱和悬空方块：密度面保留的岩石带里地表返回"原样"。改成带里地表永远是工程的，墙下是平台（第 5 节"引道"）；
  - 方块从墙顶穿出来：墙后的开挖从墙顶直接起坡。加了平台、回填碰撞和盖板（第 5、6 节）；
  - 隧道露在大洞穴顶上：稳定层不管深处。加了隧道岩体（第 5 节最后一条）；
  - 洞里卡：JFR 测过，见第 9 节。
- 第二次修后（`WORKS_VERSION` 6、`SURFACE_VERSION` 11）：
  - `compileJava`、`compileDevJava` 通过；
  - 离线检查：316 m 隧道 145 个区块 72,800 个三角形，152 m 隧道 41,203 个，坏值 0；洞口线上的列从 TUNNEL 变成 PAVED / TURF（墙站在地面上）；
  - 新测试世界 `tun4b`（种子 4，标签 `fix1`，`only=tunnels`，同样 11 个站点）：隧道格全部按设计建成；每个引道站点 250–300 列是"工程下的地面"（`TUNNEL_OVER`）；隧道岩体每个站点 15,000–25,000 格，其中空气或流体 0；路面列上方的方块 0；碰撞顶最多高 0.034 m；客户端 201 个区块网格，挖掉的路面格 0；
  - 路面导出（`roaddump`）抽查：引导段的列地面高度是草皮或 `road_surface`，`road_structure` 只在地面上方；
  - "桥面下 solid" 12 / 26 没变（桥的指标，不是隧道）；
  - GameTest 这一轮没有重跑；
  - `tun4a` 是旧版本建的，不能用来验收。
- **用户第三次实机看（2026-10-16，世界 `tun4b`）**："基本上可以了"，问新的部件没有碰撞对不对。加了部件碰撞（第 7 节），顺带修了路边部件底座悬空、情报板落在桥边、远处部件朝向。`SURFACE_VERSION` 12。
  - 离线检查（种子 4）：44 个部件 82 个盒子，每个盒子都占到格子，没有落在 NONE 列里；路边部件底座和完成地面的高差 0.00 m（修之前 0.06–0.22 m，一块情报板 21.29 m）；衬砌网格和部件数量跟上一轮一样（72,800 / 41,203 个三角形，442 / 228 个部件）。
  - 新测试世界 `tun4c`（种子 4，标签 `fix2`，`only=tunnels`，13 个站点：原来 11 个加两块情报板处）：
    - 部件格每个站点 28–70 个，全部是 `road_structure`，碰撞形状（服务端）没有空的；
    - 路边部件底座和实际地面方块碰撞顶的高差 −0.09 到 +0.05 m（草皮方块本身的精度）；
    - 隧道格全部按设计建成，隧道岩体里空洞和流体 0，路面列上方的方块 0，碰撞顶最多高 0.034 m；
    - 530 m 情报板站点（430 m 高架桥起点外 20 m）"桥面下 solid" 144：是那座桥 OPEN 列里高于规划地面的天然方块，桥的指标，没有细查；
    - 这次探针没有让客户端建网格（没有 `look`），客户端那边的形状和画面没有测；
    - GameTest 没有重跑。
- **用户第四次实机看（2026-10-16，世界 `tun4c`）：PASS。** 隧道 V1（surface12 / `road_works#6`）验收通过，在 提交 `850205b7`（用户 2026-10-16 批准），Main 把 master 快进到这个提交（未推送）。剩下的是洞内卡顿：显卡缓存已接上并自测（第 9 节），等用户实机验收。
- **隧道部件可拆 V1.1（2026-10-16，surface13，后来 surface14）自测**：
  - `compileJava`（含 dev 源）通过；
  - 离线检查（`FixtureCheck`，种子 4）：数量见第 7 节"可拆部件"，每个部件至少占一格，扶手不落空；
  - GameTest `afl_tunnel_fixture_tests`（`./gradlew runGameTestServer --offline -PaflWithoutShaders -I src/dev/tunnel-fixture-gametest.init.gradle`）PASS：生存玩家用物品在地面放两段扶手（3 + 4 格，共用 1 格），点墙面放地面部件被拒；空手、铁镐挖进度 0，撬棍 2 s；从共用格拆指着的那段，掉 1 个扶手物品，另一段留下；用命令去掉一格，部件所有格一起清掉，不掉落；射流风机（24 格）撬棍 15 s，掉钢板 8、废钢 12、绝缘铜线 6、电缆 2、钢缆 2，接管的 `road_structure` 3 层恢复；
  - 隔离客户端新世界 `tun5b`（种子 4，`only=tunnels`，13 个站点，看洞内 60 s）：
    - 部件格每个站点全部按设计建成（例如 1036/1036、530/530），埋进地里的 0，没有碰撞的 0；部件格接管 `road_structure` 的占大多数（921/1036）；
    - 隧道格全部建成，隧道岩体空洞 0，路面列上方的方块 0，碰撞顶最多高 0.034 m，跟 `tun4c` 一样；
    - 路边部件底座和地面高差 −0.09 到 +0.05 m（探针原来把部件自己的方块当成地面，读出 −1.0 到 −1.8 m，已修探针）；
    - 客户端：67 个区块有部件，783 个部件，15,920 个面，没有报错；
  - 关掉再打开 `tun5b` 第一次探针：6 个站点部件格少了（例如 461/530）。原因：道路网络从缓存读回来（`RoadNetworkJson` 存 2 位小数），部件位置跟第一次新建的差几毫米到几厘米，扶手长度骨骼也不同，原来按完全相等判断"同一个部件"。真实游戏里，跨两个区块、分两次生成的部件会存成两个近似副本：画两次，拆了只拆掉一个。改成同种类、原点差 0.1 m 以内、轴对齐就是同一个部件（`Entry.same`），客户端去重也按这个。改后重新打开：13 个站点全部对上；GameTest 加了"近似副本算同一个部件"，PASS；
  - 用户第一次实机看（2026-10-16，`tun5b`）："牌子好像不能在普通的cube方块上面放"。墙面部件的盒子伸进墙里，墙格是石头这类方块时整块放不下。改成盒子从墙面往外（薄的只有轮廓、没有碰撞），放的时候点的那层墙格不要求是空的，`SURFACE_VERSION` 14：
    - GameTest 加了"疏散指示挂在石头墙上"：石头留着，牌子占墙前那一格，有轮廓没有碰撞，撬棍拆下掉 1 个疏散指示，石头还在；PASS；
    - 新世界 `tun5c`（种子 4，同样 13 个站点）：部件格全部建成，没有形状的 0；隧道格全部建成；路边部件底座高差 −0.09 到 +0.05 m；客户端 783 个部件；
  - **用户第二次实机看（2026-10-16，世界 `tun5c`，surface14）：PASS。** 隧道部件可拆 V1.1 验收通过，跟隧道 V1 一起提交 `850205b7`（用户 2026-10-16 批准），Main 把 master 快进到这个提交（未推送）。

## 11. 版本锁

改了世界生成，要新建世界：

- `road_network` 升到 `#surface10`（隧道 V1），第二次修后 `#surface11`，部件碰撞后 `#surface12`，隧道部件可拆 V1.1 后 `#surface13`（部件写成部件方块，扶手不再画在衬砌里），墙面部件的盒子改成往外后 `#surface14`（`RoadNetworkStage.SURFACE_VERSION` 14）；`tun4c` 是 surface12、`tun5b` 是 surface13 建的，V1.1 要在 `tun5c` 或更新的世界里看；
- `terrain_works` 升到 `road_works#5`（隧道 V1），第二次修后 `road_works#6`。
- 之后 C3.3b 第一组（2026-10-16）升到 `#c3.3b#all#surface15`、`road_works#7`（[C3.3b 第一组](federal_junctions_c3_3b.md)），第二组（2026-10-16）升到 `#c3.3b#all#surface16`、`road_works#8`（[C3.3b 第二组](federal_interchanges_c3_3b.md)），第二组 B 包（2026-10-17）升到 `#c3.3b#all#surface17`（`road_works#8` 不变），隧道本身的生成没变。

见 [版本锁](worldgen_version_lock_v1.md)。

## 12. 文件

| 路径 | 内容 |
|---|---|
| `worldgen/network/road/TunnelSection.java` | 断面几何 |
| `worldgen/network/road/TunnelWorks.java` | 山体、引道、孔、洞门、横通道、列和格、碰撞、隧道岩体（`rock`） |
| `worldgen/network/road/TunnelMesh.java` | 画出来的衬砌、人行道、扶手、排水、洞门、横通道、挡墙盖板；部件的位置 |
| `client/road/TunnelParts.java` | 部件网格烘焙（含程序生成的扶手） |
| `worldgen/network/road/TunnelFixtures.java` | 部件的碰撞盒、占的格子、射线、按列的索引（V1.1，替代删掉的 `TunnelPartSolids`） |
| `block/TunnelFixtureBlock.java`、`blockentity/TunnelFixtureBlockEntity.java` | 部件方块：形状、撬棍拆、掉落、一格几个部件 |
| `item/TunnelFixtureItem.java`、`client/road/TunnelFixtureItemRenderer.java` | 部件物品：放置、变体、手里的样子 |
| `client/road/TunnelFixtureClient.java` | 世界里的部件按区块烘焙进显卡缓存 |
| `compat/jade/TunnelFixtureJadeProvider.java`、`WorldInteractionHint` | Jade 和提示 |
| `AflBlocks.TUNNEL_FIXTURES`、`AflItems.TUNNEL_FIXTURE_ITEMS`、`AflBlockEntities.TUNNEL_FIXTURE`、`AflCreativeTabs` | 注册 |
| `blockstates/tunnel_*.json`、`models/block/tunnel_fixture.json`、`models/item/tunnel_*.json`、`item_mass/afl_content_v1.json`、语言文件 | 资源 |
| `src/dev/.../TunnelFixtureGameTests.java`、`src/dev/tunnel-fixture-gametest.init.gradle` | 拆卸和掉落的 GameTest |
| `worldgen/network/road/RoadWorks.java` | `TUNNEL_OVER`：工程下的地面 |
| `worldgen/network/RoadChunkPass.java` | 写地面再写上面的工程 |
| `worldgen/network/road/RoadCells.java` | 路面格不算洞口带（`ON_GROUND` 除外） |
| `worldgen/terrain/v2/PlanStabilityDensity.java`、`mixin/WorldCarverStabilityMixin.java` | 隧道岩体进硬底和雕刻器 |
| `RoadDesign`、`RoadNetworkJson`、`RoadNetworkBuild`、`RoadWorksRef`、`BridgeMesh`、`RoadMesh`、`RoadNetworkStage`、`meshhit/RoadStructureHits`、`client/road/RoadClient` | 接入 |
| `tools/build-tunnel-v1.mjs` | 贴图、网格、Blockbench 源 |
| `textures/block/road/tunnel/`、`meshes/road/tunnel/` | 生成的资源 |
| `design/worldgen/federal_tunnels/` | 概念图 |
