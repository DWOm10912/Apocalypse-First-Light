# 土方 A3：车辆和联机

- **状态（2026-10-21）：** 代码在工作树 `D:/Minecraft Modding/AFL-earthwork`（分支 `feature/earthwork-a`），**没有合并 master，没有提交**。编译通过，离线检查通过（第 4 节）。**用户 2026-10-21 实机：T15 开进坑"车子进坑的逻辑没问题"**（第 5 节第 1、2 项）；联机（第 4～6 项）用户 2026-10-21 实机 PASS："测试没问题"；松土抓地（第 3 项）用户 2026-10-21 PASS。**A3 全部通过。**坑底露天的修复（2.1）用户实机看过，好了；人和车在坑里变黑的修复（2.2）用户 2026-10-21 实机 PASS。
- **设计：** [`docs/gameplay/sandbox_v1_design.md`](../gameplay/sandbox_v1_design.md) 第 3.8 节和第 11 节 A3 那一行。A1、A2 见 [`earthwork_a1.md`](earthwork_a1.md)、[`earthwork_a2.md`](earthwork_a2.md)。
- **范围（用户 2026-10-21）：** 只做 T15 开在挖过、填过的土上，和专用服务器加第二个客户端的同步。**感染体在沟里的寻路不做了**："现在目前先不管原版僵尸的寻路，我们差不多要开始设计afl的自定义生物了"。以后的 AFL 生物自己设计寻路时再接土方的形状。
- **网络协议：** 分支上 `AflNetwork.PROTOCOL` 62 → 65（`EarthChunkPacket` 每种材料多了抓地力）。master 现在是 63，D 的分支是 64，合并时按当时最高的再 +1。
- **车辆是 Main 的模块：** 这次改了 `vehicle/physics/VehicleWorld`、`LevelVehicleWorld`（第 1 节），已经告诉 Main；合并以前 Main 不用做什么。

## 1. 车开在土方上

原来车轮碰到挖过的格子，会落到方块兜底：表皮格的碰撞是 8×8 根高度不同的小柱子（A1 3.5），车轮看到的是一级一级 1/8 m 的小台阶，法线永远朝正上。现在：

- `VehicleWorld.Source` 加了 `EARTH`：`NONE, ROAD, EARTH, SMOOTH, BLOCK`。
- `LevelVehicleWorld#ground` 的顺序是 道路 → **土方** → 平滑地形 → 方块。道路层先答，所以路面和路肩不受影响；挖过的格子不再走平滑地形的那两个三角形（那是没挖以前的地面）。
- 判断：地形平滑开着、这一格是土方能编辑的土（`Earthwork.soil`）、这一列有土方数据（`Earthwork.vehicleGround` 返回 true）。没挖过的地方照旧。
- **高度和法线：** `EarthGrid.ground`。在这一列 4×4 个小格里找到车轮所在的小格，按 nw-se 对角线分成两个三角形，和画出来的表皮（`EarthGrid.skin`）完全同一个三角形：高度用三角形里的插值，法线用这个三角形的法线（单位向量，朝上）。不吸附到格子中心。
- **车身和车轮侧面**（`LevelVehicleWorld#solid`）：点在表皮下面时，推出的方向是三角形的法线，深度是（表皮高度 − 点的高度）× 法线 y；点在表皮上面就不算碰到。
- **抓地力：** 车轮所在小格最近的那个顶点上有松土（`loose > 0`），就用松土材料的 `grip`；没有松土（挖出来的原状土坑底、坑壁）就用那一格方块原来的抓地力（`LevelVehicleWorld.grip(block)`，和没挖过一样）。

### 1.1 松土的抓地力

松土比它来的那种天然方块松，抓地力低一点。数据在 `data/apocalypse_firstlight/bulk_materials/<id>.json` 的 `grip`，代码里内置一份一样的（`BulkMaterials`）。原状土的数字是 `LevelVehicleWorld.GRIP_WORDS` 里的。

| 松土 | grip | 对照：天然方块 |
|---|---:|---|
| 表土 `topsoil` | 0.60 | 泥土、草方块 0.70 |
| 回填土 `backfill` | 0.60 | — |
| 砾石 `gravel` | 0.60 | 沙砾 0.65 |
| 沙 `sand` | 0.45 | 沙子 0.55 |
| 黏土 `clay` | 0.50 | 黏土 0.55 |
| 泥 `mud` | 0.35 | 泥巴 0.45 |

- 客户端也要这些数字：驾驶的那个客户端自己算车（V0 第 5 节），所以 `EarthChunkPacket` 里每种材料除了方块和颜色，现在还带 `grip`（`EarthChunk.matGrip`）。客户端没收到的材料按 0.6 算。

## 2. 专用服务器和第二个客户端

下面几条是读代码查的；专用服务器 2026-10-21 启动过（最后一条），两个客户端里的效果用户 2026-10-21 实机走过第 5 节第 4～6 项，PASS。

- **客户端代码不会在服务器上加载：** 铲子的渲染器 `ShovelItemRenderer` 只在 `AflStaticMeshItemClient` 里绑定，那个类只在客户端注册（`Dist.CLIENT`）；`ShovelItem` 本身不引用客户端类。土方、地形平滑、负重、车辆这次碰过的文件里也没有客户端类。客户端的包处理走 `DistExecutor`。
- **形状同步：** 服务端每 tick 结束时把改过的土方区块发给所有能看到这个区块的玩家（`TRACKING_CHUNK`）；玩家开始看到一个区块时单独发一次（`ChunkWatchEvent.Watch`）。所以第二个客户端后来走过来，也能收到别人挖好的坑。客户端收到后替换自己的副本、重建网格；区块和世界卸载时清掉副本。
- **铲子动作：** 每一铲的动作阶段发给周围的玩家和自己（`TRACKING_ENTITY_AND_SELF`），所以别人能看到你在挖、在倒。
- **车：** 车由驾驶的客户端或者服务端算，两边都用同一个 `Earthwork.vehicleGround`，客户端用自己的副本，所以和服务端一致。
- **配置：** 土方没有单独的开关；地形平滑的配置（`TerrainSmoothConfig`）是 SERVER 类型，客户端跟着服务器走。
- **联机测试的开法（2026-10-21 加的，`build.gradle`）：**
  - 专用服务器 `.\gradlew.bat runServer --offline -PaflWithoutShaders`，工作目录 `run-server`（以前和客户端共用 `run`，会抢同一个日志）。服务器不需要 Embeddium / Oculus，所以不带。
  - `run-server` 里：`eula.txt`（用户 2026-10-21 同意 Minecraft EULA）、`server.properties`（离线模式、创造、和平、出生点不保护、允许飞行）、`ops.json`（Dev、Dev2 都是 OP 4，开发命令要 OP）。
  - 第一个客户端照常 `runClient`（玩家名 Dev，目录 `run`）；第二个客户端 `runClient2`（玩家名 Dev2，目录 `run-client2`，继承 client 的配置，设置和配置文件从 `run` 复制过去）。
  - 进游戏：多人游戏 → 直接连接 → `localhost`。
  - 这两个目录在 `.gitignore` 里。
- **专用服务器启动过（2026-10-21）：** 75 秒启动完，日志里没有本模组的错误，没有加载客户端类的崩溃。Dev、Dev2 同时在线约 4 分钟，服务器没有报错。
- **联机时发现的车辆问题（不是土方的，归 Main）：** 两个人同坐一辆 T15，平地上一直有"哐当"声。已经告诉 Main（疑似乘客那一端用插值后的位置算车速，触发了 `TruckSounds` 的碰撞或触底声）。

## 2.1 坑底露天（用户 2026-10-21 实机发现，已改，用户实机看过："之前的问题没了"）

- **现象：** 连挖几次挖出的深坑，从上面看坑底有一块圆形的浅蓝（天空色），缺一个扇形；旁观模式钻到地下看，坑底是完整的。
- **原因：** 原版和 Embeddium 都按 16 格高的区块段做遮挡剔除：一段里"整块不透明"的格子连成一片，就认为从上面看不进下面那一段，下面那一段整个不画。表皮格在数据里还是整块的土，只是画出来的地面在格子里面。坑底刚好挖到一条 16 格分界线下面一点时，分界线上下的格子全是表皮格，下面那一段被判成看不见，坑底在那一段里的部分就没画，露出天空色。旁观模式的镜头在方块里面时原版不剔除，所以从下面看是好的。
- **修法：** `mixin/client/EarthworkVisGraphMixin`：区块段的可见性图（`VisGraph.setOpaque`，原版的区块编译和 Embeddium 都走这里）不把表皮格算成不透明（`Earthwork.clientSkinCell(x, y, z)`）。只影响剔除，多画一点、不会少画；没挖过的地方照旧。

## 2.2 人和车在深坑里变黑（用户 2026-10-21 实机发现，已改，用户 2026-10-21 实机 PASS）

- **现象：** 人走进深坑、T15 开进深坑，整个模型变黑；地面本身亮度正常。
- **原因：** 实体的亮度取它眼睛那一格存的光照。坑壁陡的地方，一列里的表皮高低差能有一两格，眼睛所在那一格常常还是表皮格，表皮格在数据里是整块的土，存的光照是 0。
- **修法：** `Earthwork.clientLightCell`：问的那一格是表皮格时，往上找第一个不是表皮格的格子（最多 8 格），用它的光照。用在两处：
  - `mixin/client/EarthworkEntityLightMixin`（`EntityRenderer#getPackedLightCoords`）：人、生物、T15、掉落物、第一人称的手；
  - `mixin/client/EarthworkParticleLightMixin`（只按位置问光照的 `LevelRenderer#getLightColor(level, pos)`）：坑里的粒子、方块实体。
- 区块网格用的是带方块状态的另一个重载，不改，地面的样子照旧。

## 3. 改了的文件

| 文件 | 改了什么 |
|---|---|
| `earthwork/EarthGrid.java` | 新增 `ground(h, fx, fz, out)`：表皮高度、三角形法线、最近顶点 |
| `earthwork/Earthwork.java` | 新增 `vehicleGround(level, x, z, out)`：高度、法线、松土抓地力（没有松土返回 -1） |
| `earthwork/EarthChunk.java` | 客户端副本加 `matGrip`；包里每种材料多写一个 `grip` |
| `earthwork/BulkMaterials.java`、`bulk_materials/*.json` | 六种松土的 `grip`（1.1） |
| `vehicle/physics/VehicleWorld.java` | `Source.EARTH` |
| `vehicle/physics/LevelVehicleWorld.java` | `ground`、`solid` 在平滑地形之前问土方 |
| `network/AflNetwork.java` | 协议 65 |
| `mixin/client/EarthworkVisGraphMixin.java`（新）、`Earthwork.clientSkinCell(x, y, z)` | 表皮格不挡区块段的遮挡剔除（2.1） |
| `mixin/client/EarthworkEntityLightMixin.java`、`EarthworkParticleLightMixin.java`（新）、`Earthwork.clientLightCell` | 坑里的人、车、粒子用上面一格的光照（2.2） |
| `src/test/.../earthwork/EarthworkCheck.java` | 车辆地面的离线检查（第 4 节） |

## 4. 离线检查

`./gradlew earthworkCheck --offline`，2026-10-21：

```text
Vehicle ground: 20000 points, height error 0.0e+00, normal error 1.3e-10
Earthwork check: 2377371 checks, 0 failures
```

- 随机 20000 个点，每个点一组随机高度：`EarthGrid.ground` 的高度和 `EarthGrid.skin` 一样；法线是单位向量、朝上；法线和在同一个三角形里往旁边挪一小步量出来的坡度一致。
- A1、A2 原来的检查都还通过（守恒、坍落、600 铲）。

## 5. 实机验收清单（设计第 11 节 A3；全部 8 项用户 2026-10-21 PASS）

1. 用铲子挖一条比车宽的沟（1 m 左右深，两头挖成缓坡），T15 从一头开进去、另一头开出来：车轮贴着土面走，不抖、不卡在小台阶上，车身跟着坡倾斜。
2. 沟边是陡坎（挖到开挖角）的地方，车轮顶上去会被挡住，或者爬上去时车身明显颠一下，不会穿进土里。
3. 倒一堆松土，开上去：比旁边的草地滑一点（泥最滑）；开过没挖过的地方，手感和以前一样。
4. 开一个专用服务器（`runServer`），两个客户端进去：一个挖坑、倒土，另一个看到的形状一样；第二个客户端从远处走过来以后，看到的也是挖好的坑。
5. 第二个客户端能看到第一个人挖土的动作；两个人轮流开 T15 进沟，都正常。
6. 两个客户端退出重进，坑还在，形状一样。
7. 深坑（同一处连挖几次）从上面、从坑边各个角度看，坑底都是完整的土，没有天空色的洞（2.1）。用户 2026-10-21 实机："之前的问题没了"。
8. 人走进深坑、T15 开进深坑，模型亮度和坑外差不多，不变黑（2.2）。用户 2026-10-21 PASS。
