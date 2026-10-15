# 联邦全国道路网络 C3 V1

日期：2026-10-14。分支 `feature/federal-highways-c3`（C 的 worktree `AFL-federal-worldgen-v1`，从 master `a650e8d6` 切出），未合并 Main，未 Push。

| 阶段 | 内容 | 状态 |
|---|---|---|
| C3.1 | 在 C2 最终地形上重算 C1 路线，消除超限纵坡；按世界的开关（版本锁） | **已实现，离线验证**；全部是**规划数据**，世界里还没有任何道路方块 |
| C3.2 | 正式 S1→S2 示范段（`interstate_ew:county_seat-valley_town`）：路面、路基、地形适配、碰撞、桥下水流 | 未开始 |
| C3.3 | 全国铺设 | 未开始；等 C3.2 实机验收 |
| C3.4 | 接口与锚点（`H` 节点、占地查询、路边锚点） | 未开始；契约见 [Main/C3 接口契约](main_c3_interface_contract_v1.md) |

用户要求：先做 C3.1 + C3.2，提供独立开发客户端测试，实机验收通过后再全国铺设。

## 1. 生成的与只在规划里的

- **世界里生成的**：目前没有。C3.1 不改区块，任何世界（包括新建的）看不到 C3 道路。
- **只在规划里的**：20 段路线（州际 5 段、州道 15 段），60 个节点。离线工具算出，JSON 和图在 `design/worldgen/federal_c3/`。
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
- C3.1 还**没有**在 `WorldgenRules` 里声明 `road_network` / `terrain_works`：它们要等 C3.2 真正影响区块时再声明，所以现在任何世界的开关都是关的，行为和以前完全一样。

## 7. 已验证 / 未验证

已验证（离线）：
- 路线构建两次摘要相同；20 段全部排通；0 高架、0 隧道、0 段共用路基；
- 版本锁判定表：原有用例加 17 条开关用例全部通过（`bash tools/federal-worldgen/run.sh lock`）；
- `compileJava --offline` 通过。

未验证：
- 没有进游戏；C3.1 本来就不生成任何东西；
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
