# 联邦全国道路网络 C3 V1

日期：2026-10-14。分支 `feature/federal-highways-c3`（C 的 worktree `AFL-federal-worldgen-v1`，从 master `a650e8d6` 切出），未合并 Main，未 Push。

| 阶段 | 内容 | 状态 |
|---|---|---|
| C3.1 | 在 C2 最终地形上重算 C1 路线，消除超限纵坡；按世界的开关（版本锁） | **已实现，离线验证**；全部是**规划数据**，世界里还没有任何道路方块 |
| C3.2 | 正式 S1→S2 示范段（`interstate_ew:county_seat-valley_town`）：路面、路基、地形适配、碰撞、桥下水流 | **已实现**（第 10 节）：离线校验和隔离客户端探针通过；**等用户实机验收**；车辆行驶没有测（还没有车辆） |
| C3.3 | 全国铺设 | 未开始；等 C3.2 实机验收 |
| C3.4 | 接口与锚点（`H` 节点、占地查询、路边锚点） | 未开始；契约见 [Main/C3 接口契约](main_c3_interface_contract_v1.md) |

用户要求：先做 C3.1 + C3.2，提供独立开发客户端测试，实机验收通过后再全国铺设。

## 1. 生成的与只在规划里的

- **世界里生成的**（C3.2 起，只在 C3.2 之后新建的联邦实验世界里）：S1→S2 示范段一段，`interstate_ew:county_seat-valley_town`，6.6 km，含一座 20 m 的桥。见第 10 节。
- **只在规划里的**：其余 19 段路线（州际 4 段、州道 15 段）和全部 60 个节点（互通、平交只是点）。离线工具算出，JSON 和图在 `design/worldgen/federal_c3/`。
- **不在网络里**：C1 的三段岛链（`ISLAND_BRIDGE`）照旧只在 C1 规划里；县道、私人车道、镇区街道在 C3.1 没有路线（县道由 C3.4 的 `H` 节点和老宅接口带出来；车道归 Main）。

## 2. 道路等级

每个等级有自己的参数（`worldgen/network/route/RoadClassSpec.java`），不把所有路都当州际。

| 等级 | 设计速度 | 设计坡度 / 山区坡度 | 最小半径 | 路基半宽 | 软区半宽 | 最大挖 / 填 | 桥下净空 | 竖曲线 K |
|---|---|---|---|---|---|---|---|---|
| 州际（R4 四车道） | 110 km/h | 3% / 5% | 600 m | 22.5 m | 60 m | 18 / 14 m | 6 m | ≥ 30 |
| 州道（双车道） | 90 km/h | 5% / 7% | 250 m | 7.0 m | 30 m | 12 / 9 m | 5 m | ≥ 15 |
| 县道（双车道乡道） | 70 km/h | 7% / 10% | 100 m | 4.9 m | 15 m | 7 / 6 m | 3.5 m | ≥ 8 |

- 山区坡度只在路线穿过山地或高原（地貌权重 ≥ 0.4）时允许；更陡的不算这个等级的路，规划器绕行，或者报隧道、高架。
- 私人碎石车道（Main）和镇区街道（以后）不在这里，以后共用路面底层，参数各自定。

## 3. 路线怎么算（C3.1）

离线、纯 Java，`worldgen/network/route/`：

1. **代价网格** `RouteGrid`：32 m 一格，覆盖陆地外扩 512 m。每格存 16 个采样点的平均高程、坡度、格内起伏、水体和地貌系数。A* 走 16 邻域，代价包括：
   - 纵坡：设计坡度的一半以内免费，到设计坡度二次增长，到山区坡度陡增，超过山区坡度近乎禁止（网格路径按 90% 的限值算，因为平滑后的线形会更陡一点）；
   - 横坡和格内起伏：沿陡坡走意味着宽的填挖，按等级的路基宽度计价；
   - 水：河、湖的长度按桥长计价，每次过水另加一笔固定价（桥台），所以路线少过水、过窄处、正交过；湖更贵；海和河口不能走；沼泽加路堤价；
   - **已有路线**：在先排路线的硬区（路基加填挖）里顺着走，每米加 3；州道、县道进入州际硬区要付一次立交的价。C1 原来对已用格打 0.75 折，C3 有了真实路基以后，这个折扣把州道吸到州际的路基上（valley_town 西边约 1.7 km、desert_town 北边的垭口约 2.5 km 重叠），所以取消了。
2. **平面线形** `RoutePolyline`：格子路径 → Douglas-Peucker（12 m）→ Chaikin 五次 → 每 40 m 重采样 → 弯道放松到 1.1 倍最小半径；`RoadCurve` 再做成 B 样条。
3. **纵断面** `RouteDesigner` + `RoadProfile`：
   - 每 2 m 测一次地面：横跨路基每 2 m 一个点，去掉两头各五分之一取平均；
   - 坡度限值在 50 m 内平滑，夹在设计坡度和山区坡度之间；
   - 设计线：地面高斯平滑并抬高，再按逐点坡度限值正反夹紧；桥面下限（水面 + 净空 + 梁高）用坡度受限的锥体抬上去；
   - 竖曲线：先按等级的 vSigma 平滑，再在 K 不够的地方局部加大平滑，直到 K（10 m 弦长测）都达标；平滑上限按"最陡的变坡"需要的量算，不会把整条线抹平；
   - 结构物：桥（过河、湖；桥台向两边延伸到填高降到 0.7 倍最大填高，再加 6 m）、涵洞（河宽 ≤ 6 m 且水面宽 ≤ 14 m：下限 = 水面 + 2.6 m）、高架（填高超过上限 ≥ 20 m）、隧道（挖深超过上限 ≥ 60 m，否则记深挖）。
4. **设计反馈**：设计后，凡落在高架、隧道、深挖或填挖超过 60% 上限的里程，附近的格子加罚分，重新寻路，最多 12 轮，取得分最低的一轮。得分 = 长度 + 土方 + 桥、涵、高架、隧道的价 + 2 × 山区坡度长度。
5. **汇入（共线）**：一段路的终点城市如果已经有先排的路线**以它为端点**，这段路可以在离城市一定半径内接到那条路上，不必自己进城：
   - 州际接州际：2.5 km；州道接州道：1.2 km；**州道接州际：2.5 km**（州道沿州际共线到下一个出口，美国常见做法）；
   - 不接在那条路的桥、高架、隧道 120 m 以内；
   - 只是"从城市附近经过"的路不算服务这座城市（否则离州际 2 km 的 hill_village 会被判成不用修路）；
   - 汇入点成为节点：有州际参与是 INTERCHANGE，两条州道是 JUNCTION；同一条路上相距 150 m 以内的汇入点合并成一个节点。
6. **节点**：城市（有州际端点的城市是 INTERCHANGE，三条以上州道端点是 JUNCTION）、汇入点、州界穿越、垭口（离路段两端 300 m 以外的山区最高点）、桥、隧道。

确定性：固定遍历顺序、堆按 id 打破平局、超越函数全用 StrictMath。路线摘要（JSON 的 SHA-256 前 16 位）两次独立构建相同。

## 4. 结果（种子 -295378578869149513，C2 规划缓存）

```bash
bash tools/federal-roads/run.sh routes
```

- 路线摘要 `c62c13a4f793bd65`，第二次构建相同；构建约 7 s。
- 20 段路线，60 个节点，0 段没排通。
- 州际 29.5 km，州道 55.2 km（州道有一部分和州际共线，所以比 C1 短）。
- **0 高架，0 隧道**；桥只在真正的水上（最长 98 m 过湖，其余 20–38 m）；涵洞若干。
- **没有任何两段路共用路基**（报告里的 `SHARES` 检查：离路段两端 300 m 以外，一段路的路基落进另一段路硬区连续超过 150 m 就报；本次 0 处）。
- 所有设计纵坡都在等级限值内；Kmin 都达到等级要求（示范段有 2 m 低于 30，是最后抬升留下的毫米级台阶）。

C1 走廊和 C3 路线在 C2 地形上的对比（地面坡度按 10 m 步长测，"C3 设计"是填挖后的设计纵坡）：

| 路段 | C1 最陡地面坡 | C3 最陡地面坡 | C3 设计最大坡度 |
|---|---|---|---|
| interstate_ew desert_town→county_seat | 29.4% | 7.6% | 5.0%（山区，988 m） |
| interstate_ew county_seat→valley_town（示范段） | 7.5% | 7.2% | 2.0% |
| interstate_ew valley_town→river_city | 11.4% | 8.9% | 3.0% |
| interstate_ns industrial_city→valley_town | 9.0% | 12.4% | 3.0% |
| interstate_ns valley_town→port_city | 11.2% | 2.9% | 2.6% |
| sh_range_front pass_village→desert_town | 35.5% | 12.5% | 6.9%（山区） |
| sh_canyon desert_town→canyon_outpost | 49.9% | 20.9% | 3.5% |
| sh_hills valley_town→hill_village | 38.7% | 10.5% | 4.2% |
| sh_mountain pass_village→farm_town_north | 17.5% | 13.2% | 7.0%（山区，1264 m） |

全部 20 段见 `design/worldgen/federal_c3/c3_routes.txt`。地面坡度高的地方由路基的填挖消化（例如 sh_canyon 填高最大 11.5 m），设计纵坡都不超限。C1 的 sh_canyon（49.9%）、sh_range_front（35.5%）、sh_hills（38.7%）这类不能当正式道路的山路都已经改线。

### S1→S2 示范段（C3.2 要铺的那一段）

`interstate_ew:county_seat-valley_town`：

- 6598 m，设计坡度最大 2.0%，平均 0.9%；最小半径 667 m；
- 挖深最大 5.0 m，填高最大 4.6 m，土方约 28 万 m³；
- 1 座桥 20 m（站 1620–1640，河宽 7 m，水面 10 m，桥高 3.9 m）；
- S2→S1 州界在 (−777, −541)；
- 与 interstate_ns 的互通在站 4060 (247, −999)，离开局区中心 (300, −400) 约 600 m。

## 5. 图

`design/worldgen/federal_c3/`：

| 文件 | 内容 |
|---|---|
| `c3_national_routes.png` | 全国路线：红色州际、橙色州道；白点城市和汇入点，粉点州界，蓝点桥，黄点垭口 |
| `c3_sample_s1_s2.png` | 示范段周边 4 m/像素，含河网和桥 |
| `c3_sample_profile.png` | 示范段纵断面：地面（横跨路基的截尾平均）和设计线，下方是坡度色带 |
| `c3_routes.txt` / `c3_routes.json` | 报告和路线数据（控制点、每 2 m 设计高程、每 2 m 硬区半宽、结构物、节点） |

## 6. 按世界的开关（C3.1，契约第 12 节第 5 条）

见[版本锁](worldgen_version_lock_v1.md)"按世界的开关"。要点：

- `road_network`、`terrain_works`（C）和 `start_area`（Main）是**按世界开关**的子系统；
- 新建世界全部打开；已有记录的世界，记录里有才打开；领养的旧世界全部关闭；
- 生成代码查 `WorldgenGateState.enabled(level, WorldgenGates.ROAD_NETWORK)`；
- C3.2 在联邦规则里声明了 `road_network` 和 `terrain_works`（10.7）：C3.2 之后新建的联邦世界两者都打开；之前建的联邦世界、r1 世界照旧全关，不生成道路，也不会被拦截。

## 7. 已验证 / 未验证

已验证（离线）：
- 路线构建两次摘要相同；20 段全部排通；0 高架、0 隧道、0 段共用路基；
- 版本锁判定表：原有用例加 17 条开关用例全部通过（`bash tools/federal-worldgen/run.sh lock`）；
- `compileJava --offline` 通过。

C3.2 的验证见 10.8。

未验证（C3.1 部分）：
- C3.1 本身不生成任何东西；
- 开关在真实世界里的绑定（加载时写进 RandomState）只编译过，没有实机运行；
- 路线是在 32 m 网格和 2 m 采样的规划高度上设计的，实际区块地形（含 C2 的细节噪声）上的贴合要到 C3.2 才看得到。

## 8. 已知问题和留给后面的

- **州道共线**：sh_gulf bayou_village→port_city 和 port_city→shore_town 在 port_city 西北约 1.4 km 处汇入 interstate_ns（合并成一个互通），port_city 本身只靠州际进城。这符合共线的做法，但互通位置还要在 C3.3 按城镇规划再看。
- **sh_lakes mill_town→industrial_city** 过一个湖，桥 94 m（水面 14 m，桥台因为填高延伸）；可以接受，C3.3 可能再调。
- **节点只是点**：互通的匝道、立交的跨线桥、平交路口都还没有几何，C3.3/C3.4 做。
- 路线依赖模板顺序（先州际、后州道），同一等级内先排的占先。
- 岛链不在网络里。

## 9. 文件

| 文件 | 作用 |
|---|---|
| `worldgen/network/route/RoadClassSpec.java` | 道路等级参数 |
| `worldgen/network/route/RouteTerrain.java` | 规划地形查询（高度、水体、地貌、州） |
| `worldgen/network/route/RouteGrid.java` | 代价网格与 A* |
| `worldgen/network/route/RoutePolyline.java` | 格子路径到平滑控制线 |
| `worldgen/network/road/RoadCurve.java` | B 样条线形、里程和横向偏移 |
| `worldgen/network/road/RoadProfile.java` | 纵断面设计、竖曲线 K |
| `worldgen/network/route/RouteDesigner.java` | 测地面、设计纵断面、结构物 |
| `worldgen/network/route/RoadNetworkPlan.java` | 全国网络：分段、反馈、汇入、节点、报告、JSON、摘要 |
| `worldgen/lock/WorldgenGates.java` / `WorldgenGateState.java` | 按世界的开关 |
| `tools/federal-roads/run.sh`、`tools/federal-roads/java/.../RoadsPreview.java` | 离线工具：报告、图、JSON |
| `worldgen/network/road/R4CrossSection.java` | R4 横断面、超高、路边模板（C3.2，R4 样板的正式拷贝） |
| `worldgen/network/road/RoadDesign.java` | 一条建成路线：线形、纵断面、横断面、桥跨 |
| `worldgen/network/road/RoadNetworkJson.java` | 路网 JSON 读回成设计；给客户端的子集 |
| `worldgen/network/road/RoadNetworkStage.java` | 建成路线清单、版本 |
| `worldgen/network/road/RoadWorks.java` / `RoadWorksRef.java` | 完工面、每列施工内容；挂在 RandomState 上的工程 |
| `worldgen/network/road/RoadMesh.java` | 按区块裁好的路面网格（纯 Java） |
| `worldgen/network/road/RoadCells.java` | 客户端的路面格：预测碰撞块、挖开的格、坑壁（纯 Java） |
| `worldgen/network/RoadNetworkRuntime.java` | 路网的构建、磁盘缓存、按开关绑定 |
| `worldgen/network/RoadChunkPass.java` | 区块施工、写入保护 |
| `worldgen/network/RoadNetworkS2CPacket.java` / `RoadNetworkEvents.java` | 登录时把建成路线发给客户端 |
| `worldgen/network/RoadNetworkCommand.java` | `/afl worldgen roads` |
| `mixin/ChunkGeneratorRoadWorksMixin.java` / `mixin/WorldGenRegionRoadGuardMixin.java` | 施工入口、生成写入保护 |
| `worldgen/terrain/v2/PlanHeightDensity.java` / `PlanStabilityDensity.java` | 密度用完工面（C3.2 改） |
| `block/RoadSurfaceBlock.java`，方块 `road_surface` | 路面碰撞 |
| `client/road/RoadClient.java` / `RoadRenderTypes.java` | 客户端路面网格 |
| `assets/apocalypse_firstlight/textures/block/road/` | 路面贴图（R4 样板拷贝） |
| `tools/federal-roads/java/.../road/RoadWorksCheck.java` | 离线施工与网格校验（`run.sh works`） |
| `src/dev/.../worldgen/C3RoadProbe.java` | 隔离客户端的道路探针（`roads=1`，只在开发构建） |

## 10. C3.2：S1→S2 示范段正式生成

### 10.1 生成什么

- 只铺一段：`interstate_ew:county_seat-valley_town`（`RoadNetworkStage.BUILT`），6598 m，四车道 R4 断面（中央分隔带 19 m，每侧 2 条 3.75 m 车道、1.5 m 内路肩、3.5 m 外路肩）。
- 只在**新建的联邦实验世界**里生成（版本锁按世界的开关：`road_network` 和 `terrain_works` 都打开时）。C3.2 之前建的联邦世界、r1 世界、旧世界都不生成。
- 其余 19 段路线照旧只在规划里。两端直接在 County Seat 和 Valley Town 的城市中心点结束（路面端头有 30° 安全斜边，路基向地面 1:4 收坡），城市街道和互通还没有。
- 不预生成，不常驻加载：道路就是区块生成的一部分，玩家走到哪里，哪里的区块生成时带上道路。

### 10.2 设计数据

- 服务端第一次加载一个种子时，从规划算出全国网络（约 8 s），存 `afl_cache/road_network/<规则版本>_<种子>_<规划摘要>.json`，以后直接读。
- 服务端和客户端都从**同一份 JSON**（控制点、每 2 m 设计高程、结构物，2 位小数）重建 `RoadDesign`：线形 `RoadCurve`，纵断面 `RoadProfile`，R4 横断面 `R4CrossSection`（R4 样板的正式拷贝，带超高和超高渐变），桥跨（`BRIDGE`/`VIADUCT` → 桥面，梁高 1.8 m）。客户端只收建成路线的子集（约 44 KB，压缩传输）。
- 涵洞、隧道在 C3.2 会被拒绝（示范段没有）；C3.3 再做。

### 10.3 地形工程（密度层）

`RoadWorks`（纯 Java）给出每个点的完工面：

- 路基内（|u| ≤ 22 m）：中央分隔带的浅沟，或路面下 2 cm 的草皮；
- 路基外：路肩铰点 + R4 路边模板（挖方：1:4 前坡、1 m 深 1.2 m 宽边沟、1:3 后坡；填方：6 m 内 1:4，再往外 1:3），自然在坡脚和地面相接；48 m 处最后 4 m 混回地面，不留台阶；
- 路线两端外：端面按 1:4 收向地面；
- 桥跨内：不施工（桥下保持天然河谷）；
- 离地面 3 cm 以内算天然；
- **路边工程不进水**：规划里的河、湖、海点上不填不挖（斜交过河时路边坡不会把河堵住）；路基本身过水一律是桥。

密度函数用它：`plan_height`（`PlanHeightDensity`）在道路列返回完工面，所以挖填直接由噪声生成；硬稳定层（`PlanStabilityDensity` 的 `Columns.height`）也从完工面往下算，不会把挖方填回去。工程通过 `RoadWorksRef` 挂在每个世界自己的 RandomState 上，版本锁绑定开关时才填进去；锁的密度探针在绑定前算，所以探针不受影响。

### 10.4 区块施工

`RoadChunkPass` 在区块特征阶段开头（结构和特征之前，`ChunkGeneratorRoadWorksMixin`）把密度做出的近似形状精确完工，只写本区块：

| 列 | 内容 |
|---|---|
| PAVED（整列在路面内） | `road_surface` 碰撞（1/32 格，最多两格高），下面沥青、碎石、圆石底基层，再往下用泥土填到天然地面 |
| TURF（路基其他部分、边坡） | 一块 `graded_turf`（四角取相邻列共用的完工面，1/8 量化；路面下的角向下取整，草皮不会穿过沥青），下面泥土 |
| DECK / ABUTMENT | 桥面：碰撞 + 1.8 m 钢筋混凝土板；桥头那一排混凝土一直到地面（桥台） |
| BARRIER | 桥上内外两侧 1 m 混凝土护栏；桥头的护栏和翼墙落到地面 |
| OPEN | 桥下：天然河谷，水不动，只清掉密度插值留在规划地面以上的固体 |

- 完工列上方一直清到区块地表。
- 填土最多填到规划地面以下 2 格：路下的洞穴保持洞穴，不会被灌成一根土柱。
- 之后任何生成写入（本区块或相邻区块的树、雪、石堆、结构件）落进道路列的保护高度以上都会被拒绝（`WorldGenRegionRoadGuardMixin`），所以结果和区块生成顺序无关。保护高度：路面列从底基层（碰撞块下 3 格）起，草皮列从草皮起，桥的各列从混凝土底起。
- 原版矿物特征（煤、铁、碎石团、泥土团……）不走 `setBlock`，而是直接写区块段（`BulkSectionAccess`），保护拦不住它。它们只替换石头类方块（`base_stone_overworld`），所以底基层用**圆石**而不是石头：任何矿物规则都不会替换它。第一次隔离测试用的是石头，两个同种子世界在 10 个区块里底基层出现了不同的煤矿和碎石，换成圆石后一致（10.8）。
- 保护高度以下是天然地层，矿物照原版生成；原版矿物在暴露于空气时会按概率放弃，结果和区块生成顺序有关（原版本身就这样），所以那里不保证两个世界逐块相同。
- 网格森林（ForestPlan）在路中心线 40 m + 树冠半径内不生根。
- 出生点如果落在道路工程上，Y 取完工面。

### 10.5 客户端路面

- 登录时服务端发 `RoadNetworkS2CPacket`（协议 `40-c3.2`），客户端重建同样的设计。
- 每个加载了的、道路经过的区块，在工作线程上生成该区块的路面网格（`RoadMesh`：R4 的沥青、黄色内边线、白色外边线、3 m/12 m 虚线车道线、两侧和两端的安全斜边；先在世界坐标生成，再裁到区块，所以区块之间无缝无重叠），每 tick 最多开 4 个；区块卸载就丢掉。
- **挖开的路面**（2026-10-14 实机反馈后加）：客户端用和施工同一个纯函数（`RoadWorks.pavedRange` + `RoadWorks.paved`）算出区块和外围一圈每个路面格应有的碰撞块（`RoadCells`），和世界里的方块比较；碰撞块不在了（被挖、被炸）或高度档不对的格，网格在这一格开口，坑边朝坑里补一圈沥青侧壁（从邻格碰撞块底到路面）。坑底是原版方块（沥青方块）。比较时机：网格建好时一次，玩家所在 3×3 区块每 tick，其余网格每 tick 轮查 8 个；有变化就在工作线程上重建这个区块的网格。
- 在实心地形之后画，用地形实心着色器和方块图集（`RoadRenderTypes`，沥青和标线各自的多边形偏移），逐顶点取世界光照；光影包的阴影 pass 不画。
- 贴图：`textures/block/road/asphalt|paint_white|paint_yellow`（含 `_n`/`_s`），从 R4 样板拷贝。

### 10.6 碰撞方块 `apocalypse_firstlight:road_surface`

- 看不见、不挡光、不窒息；32 档高度（`layers` 1..32，每档 1/32 格），每列顶面和路面网格相差约 3 cm（列内横坡造成的范围）；原版碰撞，服务端和客户端一样，不改实体物理。
- 挖掘：镐，石质及以上（`minecraft:mineable/pickaxe` + `minecraft:needs_stone_tool`，`requiresCorrectToolForDrops`），和下面的沥青一致；没有掉落（沥青才掉），没有物品。
- 名称：Road Pavement / 道路路面。
- 挖掉碰撞块以后，路面网格在这一格开口（10.5）。最初版本网格不跟方块，挖了路上看不出坑（2026-10-14 实机发现，已修正）。

### 10.7 版本锁

- 联邦规则新增两个 HARD 子系统：`road_network` = `afl_road_network_c3_v1#c3.2#interstate_ew:county_seat-valley_town#surface1`，`terrain_works` = `road_works_r4#1`；都是按世界开关的。
- 新增探针 `road_network` = 路网 JSON 摘要。
- 改了建成路线、路面或施工规则，要改 `RoadNetworkStage` 里对应的版本。

### 10.8 测试

离线（`bash tools/federal-roads/run.sh works`，种子 -295378578869149513，C2 规划缓存）：

- 两次独立构建（全网 JSON 和客户端子集 JSON 各自解析）列摘要相同；
- 3115 个区块受影响；PAVED 146 912 列，TURF 261 847 列，桥面 401 + 桥台 43 + 护栏 228，桥下 1328 列；
- 碰撞：最大误差 0.058 m，平均 0.034 m；
- 路面范围内草皮角高于路面：0；
- 规划水面上的路边工程：0 列；
- 完工面相对规划地面：最大挖 6.6 m，最大填 4.9 m；
- 网格：2 万个随机路面点，缺口 0，重叠 0，高度和设计差最大 0.003 m；
- 挖开的格：客户端预测的碰撞块 186 463 格，和施工结果不同的 0 格；300 个区块每 7 个路面格挖一格：开口后的网格和坑里的部分正好拼回原网格（面积误差 < 1e-9 m²），坑里没有残留三角形，坑壁三角形 26 798 个全部竖直、高度正常；
- 耗时：施工列 4.5 ms/区块，网格 0.27 ms/区块（162 个三角形）；密度查询 1.6 µs/列。

隔离客户端探针（2026-10-14，`src/dev/federal-c2-client.init.gradle`，参数 `roads=1`，运行目录 `build/federal-c2-client`，没有光影；种子 -295378578869149513，联邦实验预设）：

- 探针窗口沿路线：两端、每 900 m、桥中间，共 10 个，每个 5×5 区块（250 个区块）；每列和 `RoadChunkPass.columns` 算出的应建内容逐列比对。
- 新建世界 c32d：所有列和设计一致（PAVED、TURF、DECK 401、ABUTMENT 43、BARRIER 228、OPEN 1129，没有一列不对）；完工列上方 12 格内没有任何方块（树、雪、植物、结构件都没有）；服务端碰撞形状的顶面和设计路面差最大 0.035 m；桥下有水 900 格（水面在规划地面下 1 m），规划地面以上没有固体。
- 盔甲架：在 5 个桩号（450、2050、3150、5450 和桥中间 1630）的左右两条车道中心，从路面上方 2 m 放下，服务端物理落定：10/10 着地，和设计路面差最大 0.039 m（桥面上 0.019 m）。
- 存档重开（c32b 建、关、再开）：250 个区块的道路列哈希全部相同（VERIFY ROADS PASS），地形签名全部相同。
- 两个世界同种子（c32d 和 c32e 分别新建）：250 个区块的道路列哈希全部相同（VERIFY ROADS PASS），地形签名全部相同。哈希范围是每列保护高度到完工面上 6 格。
- C3.2 之前建的联邦世界 fed_c2 重开：开关保持关闭，不规划路网，地形签名 500/500 和当初相同。
- 新世界第一次加载规划路网 7.7 s（之后读缓存），新世界版本锁记录约 8.2 s；探针区块 90–130 ms/区块（含原版其余生成，单人开发客户端）。

这次测试中发现并修正的问题：
- 施工列缓存命中时返回了空数组（应返回 null），特征阶段的写入保护读它时数组越界，第一个测试世界在特征放置时崩溃；已修正。
- 底基层原来是石头，被邻区块的原版矿物（不受保护）按生成顺序替换；改为圆石（10.4）。

探针报告：`design/worldgen/federal_c3/c3_2_probe/`（c32d、c32e 新建，c32b 重开，fed_c2 旧世界）。

没有测的：真实玩家在游戏里走、跑、坐车（还没有车辆）；多人服务器；专用服务器；高速移动时的区块生成和路面网格；光影包下的路面外观；渲染帧率。

### 10.9 实机测试方法

隔离开发客户端（自己的运行目录 `build/federal-c2-client`，不碰正式存档）：

```bash
gradlew runClient --offline -I src/dev/federal-c2-client.init.gradle
```

新建世界，世界类型选 "AFL 联邦（实验）"，种子 `-295378578869149513`。进入后 `/afl worldgen roads` 显示路线两端、桥的坐标和自己在路上的位置。坐标顺序是 x y z（中间是路面高度），传送用 `/tp <x> <y+2> <z>`，例如起点 `/tp -2708 100 1626`。（2026-10-14 修正：输出行尾的 `%n` 在 Windows 上带回车符，聊天栏显示成方框，改为 `\n`。）

### 10.10 已知问题（C3.2）

- **桥是方块搭的临时外观**：护栏、桥台、翼墙是钢筋混凝土方块，斜交和曲线处是台阶和锯齿，和网格路面风格不一致。用户 2026-10-14 实机要求：高速的所有模型最终都用 Mesh（桥面侧边、护栏、桥台、翼墙、桥墩、路边设施），方块只做看不见的碰撞，像路面一样。没有实现，排在后面的阶段。
- 挖开路面后，坑底露出的是沥青方块本身的贴图（像素材质），和网格路面的 240 贴图不一样；路边斜边、桥面以外的格挖开，网格不开口。
- `graded_turf` 只有草皮外观，沙漠和雪地路段也是草（以后加变体）。
- 互通、平交、匝道都没有几何；示范段两端在城市中心点直接结束。
- 一列只属于一条路；路线交叉处的几何是 C3.3 的事。
- 路下洞穴：雕刻器的稳定层按规划地面算，深挖方下面在山麓地段可能离洞穴更近。
- 村庄等结构和道路相交时，结构件在道路列里被拒绝写入，会出现被切掉的房子；结构选址避开道路要到 C3.4。
- 雨雪天气在游戏里照常落到路面网格下面的碰撞块上（雪层会盖住路面网格）。
- 路面网格每帧逐顶点提交（和 R4 样板一样），还没有在大量道路时测过帧率；C3.3 前要测。
- 地形平滑（Terrain Smooth）和道路边坡的配合没有在游戏里看过。
