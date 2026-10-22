# 溪谷老宅 M4 第二轮：接上县道、新世界都放、大门和信箱、老宅开局出生

状态（2026-10-19）：
- **代码写完，在 master 上（未提交）**；`compileJava` / `compileTestJava` / `compileDevJava` 通过。
- C 的整格平台（`feature/county-road-g3-pads` @ `f12c1df2`）2026-10-19 合并进 master `3e67b2c5`（用户批准，未推送）。
- 离线检查 `forestHomeStartCheck`：合并平台后 6 个种子 PASS（第 7 节）。
- M4 GameTest：两项 PASS，放置那项加了大门和信箱（第 7 节）。
- 用户 2026-10-19 进新世界看了（截图：从县道看碎石车道通到老宅），说"还不错"。前廊出生、大门、信箱、车道的 12 % 坡还没单独确认，wiki 先留 `integrated`。

上级文档：
- [M4 第一轮](forest_home_b_m4_placement.md)：选址、放置、清场；
- [县道 G3](county_road_g3.md)：C 的县道、碎石车道和 `home` 锚点；
- [Main ↔ C3 接口契约](main_c3_interface_contract_v1.md)；
- [老宅 B V1 计划](forest_start_home_b_v1_plan.md)：P4。

用户 2026-10-19：「先做接入的第 1–5 步，出生先按照你说的」。

## 1. 这一轮做了什么

1. **位置读路网**：房子落在 G3 选的位置（`RoadCounty.Home#reservation()`），也就是碎石车道的尽头。G3 选址时带着道路的禁区调用 G2；Main 不再自己跑一遍不带禁区的 G2。
2. **新世界都放**：去掉存档名前缀 `AFL_HOME_`，改用版本锁的 `start_area` 开关（第 2 节）。
3. **大门和信箱**：车道口的 `tube_farm_gate` 和 `rural_mailbox` 由区块生成放（第 4 节）。
4. **老宅开局出生**：选了老宅开局的世界，出生点在前廊台阶下，第一次进来的幸存者站在那里（第 5 节）。
5. **碎石抓地**：车道路面种类从 `TURF` 改成新加的 `GRAVEL`，抓地 0.65（第 6 节）。

没做（下一步）：
- 开局物资（P5）、燃料（P6）、收音机广播（P8）；
- ~~模板重新导出~~：2026-10-22 已做（下面"模板重新导出"一节）；
- PRIVATE DRIVE 牌就是大门上的牌子（`sign=front`，朝县道）；县道尽头的 ROAD ENDS 路障没有模型，没放；
- 大门只能朝东南西北（第 4 节"已知问题"）。

## 2. 什么世界会放老宅

- 联邦世界，2026-10-19 以后新建，带 `start_area` 开关：
  - 规则表 `WorldgenRules`（联邦）加了子系统 `start_area`，版本 `forest_home_b#2`（`ForestHomeLayout.RULES`），HARD；
  - 和道路的两个开关一样：新世界打开并记录，旧世界的记录里没有，自动关闭，不会被版本锁拦。
- 建世界时（`LevelEvent.CreateSpawnPosition`，HIGH）：开关开着，而且这个世界有路网，就取路网 JSON 里的 `home`。
  - 取到了：预留、大门、信箱写进存档（`ForestHomeSavedData`），马上激活；
  - 没有路网或者 G3 没选出位置：日志警告，这个世界没有老宅，出生照旧。
- 每次加载：存档里有预留就激活，不管开关现在怎么样。一个世界已经开始放房子，就要放完。
  - 第一轮用 `AFL_HOME_` 建的测试世界照样激活它存着的房子。这些世界的房子是旧选址，没有车道，也没有大门和信箱。
- `ForestHomeLayout.RULES`：模板、模板坐标系、整平、大门和信箱的放法，任何一样变了都要加 1。世界是一个区块一个区块放房子的，规则一变，同一栋房子前后两部分就对不上；版本锁会拦下旧世界。

## 3. 选址和存档

- 位置：`home.reservation()`（`ForestHomeLayout.origin` + `reservation`），和 G2 给的一样。
- 存档 `apocalypse_firstlight_forest_home`：
  - 预留（模板、原点、旋转、状态、版本 1）；
  - 2026-10-19 起还有 `Gate`、`Mailbox`：第一列底格 + 朝向；旧存档没有，就不放。
- 报告（`/afl dev forest_home info` 的最后一行）：G3 的位置、`link`、`beyond`、县道 id、大门和信箱的格子。

## 4. 大门和信箱

位置在建世界时由 G3 的锚点算出来（`ForestHomeRuntime#gate` / `#mailbox`），存进存档。区块生成时，每个区块只写自己那几格（`ForestHomeChunkPass#fixtures`）。

**大门**（`tube_farm_gate`，4 × 2 格）：
- 关着，PRIVATE DRIVE 牌在前面（`sign=front`）。
- 朝向：锚点给的是车道朝县道的方向。门的前面朝县道，取最近的东南西北。
- 四列沿朝向的顺时针排开，中心对着锚点。
- 高度：四个底格所在的道路施工列，碰撞顶（或草皮顶）往上第一格，取最高的那个。不在施工列上，就按锚点高度四舍五入。

**信箱**（`rural_mailbox`，1 × 2 格）：
- 红旗放下，里面是空的。
- 门朝县道，取最近的东南西北。
- 高度规则和大门一样。

**写入**：
- 时机：区块装饰阶段的末尾（`ChunkGeneratorForestHomeMixin` 加了 `applyBiomeDecoration` 的 RETURN 注入），在道路的区块写入和装饰物之后。道路写入会把车道上方清空，所以大门必须排在它后面。
- 写法：直接写进区块（`ChunkAccess#setBlockState`），因为道路的 `WorldGenRegion` 写入保护挡住道路列上的生成写入；主格的方块实体用方块自己的 `newBlockEntity` 建。
- 跨区块：每格只由它所在的区块写，区块生成顺序随便。多格方块的自检 tick 遇到邻格区块没加载会等着（`RectMultiblockBlock#tick`）。
- 新的公共方法：`RectMultiblockBlock#cellStates(origin, facing, masterState)`，给世界生成用，和物品放置写的状态一样。

**整格平台**（C，`feature/county-road-g3-pads` @ `f12c1df2`，2026-10-19 合并进 master `3e67b2c5`；[县道 G3](county_road_g3.md) 6.4 节）：
- 车道表面和路边草皮是小数高度，碰撞是分层的。没有平台时，门和信箱会比地面高出最多 1 格。
- 现在大门在离铺装边 13–17 m 处，前后各约 3 m 车道压平到整数 Y；信箱 Y = ⌊铺装边高度 + 0.3⌋，信箱格和四邻压平。锚点的 y 就是这个整数。Main 这边没改。
- 离铺装边不到 0.3 m 的格子不压平（C 的规则）。信箱靠路那一侧通常有 2 个邻格是这样：它们比信箱的格子高出不到 1 格（检查按"顶格往上第一格"算，显示高 1）。
- 代价：两个种子（用户种子 18151… 和 1013…）车道最陡处升到 12 %，在离路边 20–22 m，平台后面。要实机看会不会像个坎。

**已知问题**：
- 车道最后 20 m 垂直接县道，县道多半不是正东西南北走向。离线检查里大门和车道的夹角是 28–41°，大门斜着横在车道上。
- 方块结构只能朝四个方向。要让门正对车道，得做任意角度的大门：网格按角度画，碰撞另算。这个还没做。

## 5. 老宅开局出生（P4）

**接口**：新加 `survivor/SurvivorSceneStarts`。
- 开局场景的地方登记自己的站位；老宅由 `ForestHomeRuntime` 登记 `afl:forest_home`。
- 站位：前廊三级台阶下面那排草地（纸面 k 0、Z 15，X 8 / 7 / 9），面朝房子。最多 3 个（`ForestHomeLayout.START_SPOTS`，和场景的 `maxPlayers` 一样）。

**建世界时**（`SurvivorEvents#onCreateWorld`，LOWEST）：
- 先读建世界页选的开局；
- 选了老宅开局，而且这个世界有老宅：世界出生点设在第 0 个站位，朝向是面朝房子的 yaw。这样出生区块就是老宅周围的区块。

**第一次进来**（`Survivors#ensureSurvivor` → `placeAtScene`）：
- 一个账号的第一个幸存者，按到达顺序站第 0、1、2 个站位。站之前要先过设置页面的"按住不动"。
- `SpawnSource` 记成 `afl:forest_home`，`SpawnPos` 记站位。
- 站位用完了、没有老宅、或者选的是别的开局：照旧在世界出生点附近，`SpawnSource` = `initial`。

**其他**：
- 继任者（死了以后的下一个幸存者）不回老宅，照旧走 `SurvivorSpawns`（用户 2026-10-14 定：老宅不进继任出生池）。
- `SurvivorScenes` 里老宅开局的 `connected` 改成 true，建世界页不再提示"这个地点还没做好"。

## 6. 碎石抓地

- `RoadSurfaceQuery.Surface` 加了 `GRAVEL`，车道的路面查询（`driveNormal`）报 `GRAVEL`。
- 车辆抓地表 `LevelVehicleWorld#roadGrip`：`GRAVEL` = 0.65，和碎石方块（`GRIP_WORDS` 里的 `gravel`）一样。
- 车道边上的草皮还是 `TURF`，0.70。

## 7. 验证

**离线检查** `./gradlew forestHomeStartCheck --offline -PterrainPlans=<6 个 .bin，分号隔开>`（`src/test/.../network/road/ForestHomeStartCheck.java`，要先跑 `systemPlans`）：
- 每个种子按服务端的做法建路网，再算预留、大门、信箱和三个站位。
- 不合格就失败：
  - 没有 `home`，或者大门 / 信箱没算出来；
  - 大门 / 信箱进了房子的整平圈；
  - 大门底格不在车道碎石或草皮上，信箱不在草皮上；
  - 朝向和锚点差超过 45°；
  - 站位不在地块里；
  - 算两次结果不一样。
- 只警告：大门四个底格不一样高，信箱四邻和它不一样高（写出是哪一边，靠路的那边标 road side）。
- 2026-10-19 第一次（平台之前）：6 个种子 PASS，两个种子的信箱四邻差 1 格；大门和车道的夹角是 28–41°。
- 2026-10-19 合并平台后（master `3e67b2c5`）：`countyCheck` 和 `forestHomeStartCheck` 都是 6 个种子 PASS。大门离铺装边 13–13.5 m，四个底格都一样高；大门、信箱的高度就是锚点的整数 y。三个种子（18151…、1013…、1016…）信箱有 2 个邻格高 1：一个靠路、一个沿路，就是 C 不压平的那两格（上面"整格平台"）。夹角不变，28–41°。

**M4 GameTest** `afl_m4_tests`（`runGameTestServer --offline -PaflWithoutShaders --init-script src/dev/forest-home-m4-gametest.init.gradle`）：
- 放置那一项加了一个跨区块的大门和一个信箱。区块打乱顺序生成以后，查 8 + 2 格的状态和两个主格的方块实体。
- 2026-10-19 结果：planner 和 placement 两项 PASS。房子 12506 格和 8 个方块实体和参照一样，暗线 138 格、17 个设备；大门 + 信箱 10 格跨区块，状态和方块实体都对。
- GameTest 世界是 r1 测试世界，没有路网，所以建世界那一步（`start_area` 开关 → `home` → 存档）没有测到。

**没有验证的**：
- 真的建一个新世界：开关、选址、出生点、第一次进来的站位、建世界页的提示；
- 大门和信箱在游戏里的样子；
- 车在碎石上的手感。

### 模板重新导出（2026-10-22，在 AFL-earthwork 做）

- 用户在超平坦里摆好家具和冰柜（多一个插座），`/afl_author resume forest_home_b 0 -64 0 37 26 13 3` 后导出（房子比超平坦地面低一格，只影响摆的时候，模板的行和旧的一一对上）。
- 新模板 SHA-256 前缀 `ca559bcb8913031b`，换进 `structures/forest_home_b.nbt`；旧的（`fe143cf7e7331363`）和这次的导出存在 `run/afl_authoring_exports/archive/`。
- 读回：37 × 13 × 26，12,506 格，16 个方块实体，0 个实体。和旧模板比改了 17 格，全是新加的（按格数）：床 2、衣柜 2、床头柜 1、储物箱 2、老式冷柜 2、工作台 4、木质工具架 2、应急收音机 1、墙插座 1。没有删掉的。
- 暗线：配电箱 1 个，156 个节点，没打包的 0 个；新插座 (12, -60, 19) 走第 3 路（插座），冷柜的插头指向它（模板里存相对偏移 x −1）。
- 挂着的东西跟着模板走（导出规则 2026-10-22 起放行，[building_wiring_v1.md](../gameplay/building_wiring_v1.md)）：东墙工具架 (34, -60, 7) 手锯、伐木斧、圆头铲；另一个工具架 (13, -59, 16) 撬棍。储物箱、冷柜、工作台抽屉都是空的。
- 导出状态：便携发电机灌油标记 1 个，搜索记录 7 个清掉。
- 没在新世界里看。

## 8. 用户怎么看

1. 新建一个世界，联邦地形（默认），开局选"溪谷老宅"（默认）。
2. 进来以后应该站在前廊台阶下面、面朝房子。
3. 沿碎石车道走到县道：车道口有关着的管式大门，PRIVATE DRIVE 牌朝县道；路边有信箱。
4. `/afl dev forest_home info` 显示选址、大门、信箱的格子和建世界时的报告。
