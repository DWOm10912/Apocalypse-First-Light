# 土方 / 岩石：E → C 交接（V1）

- **日期：** 2026-10-23。
- **起因：** 长期会话从五个收成三个（Main、B、C）。E 负责的自然环境（连续地形、Earthwork、岩石、采矿、地下地质）全部交给 C。
- **本轮范围：** 只做 Git 只读审计和交接说明。没有合并、提交、改代码，也没有动 master 和 C 的工作树。
- **后续（2026-10-23）：** 用户要求把 E 合到 C 的工作树，已经合了，见第 8 节开头。第 1 节是合并前的审计记录。
- **怎么读：** 下面每条都标了来源。「[代码已证实]」是这次用 git 或读代码核对过的；「[文档]」出自设计 / 进度文档；「[推断]」是没有核对的推断。

## 1. Git 现状（2026-10-23，只读命令）

| 项 | 值 |
|---|---|
| 工作树 | `D:/Minecraft Modding/AFL-earthwork` |
| 分支 | `feature/earthwork-a` |
| HEAD | `66802745`（2026-10-22 圆头铲大铁锹 + 手动倒 + 动作去穿模，连同岩石 R2 三轮） |
| master HEAD | `b53f6063`（协议 71） |
| 共同祖先 | `4dea3c8a`（2026-10-22 大合并，E 的 A1–A3、R1a、R1b 已在里面） |
| 比 master 多 | 2 个提交：`42c28af6`（R1c + D 形把手 + 导出规则 + forest_home_b），`66802745`（铲子第二轮 + R2） |
| 比 master 少 | 7 个提交（P9 换弹、州名、物品检视、背带物理等，和土方无关） |
| 未提交 / 暂存 / 未追踪 | 都没有，工作树干净 |
| stash | 没有 |

[代码已证实]

### 1.1 试合并（`git merge-tree`，不改任何引用）

- **E + master：** 只有 `network/AflNetwork.java` 冲突，而且只冲突协议号那一行（E `"69"`，master `"71"`）。合并时取当时最高的号 +1，即 **`"72"`**。master 新加的 `InspectPackets` 注册保留。
- 另外几个两边都改过的文件能自动合并：`docs/models/equipment_meshes_v1.md`、`client/WoodGunRackRenderer.java`、`lang/en_us.json`、`lang/zh_cn.json`。合并后要编译确认，并且 en_us / zh_cn 两边的行要对齐。
- **E + C（`feature/underground-u2`，`9961186d`）：** 没有冲突。两边共同改过的只有两个 lang 文件。
- **master + C：** 没有冲突。
- **C 的工作树**（`AFL-federal-worldgen-v1`）没有未提交的改动，所以和 E 不存在"未提交文件撞车"。

[代码已证实]

## 2. 功能进度

| 系统 | 内容 | 状态 | 在哪 |
|---|---|---|---|
| A1 | 可编辑高度场覆盖层（0.25 m 顶点、`EarthChunk`、坍落、轮廓规则、`/afl dev earth`） | 已提交且实机通过（2026-10-21） | master |
| A2 第一版 | 圆头铲挖 / 装 / 倒 / 刮平，散装材料表，骨骼动作，坑里第一人称变黑和光照的修复 | 已提交且实机通过（2026-10-21） | master |
| A3 | T15 开进沟（`VehicleWorld.Source.EARTH`、松土抓地力），专用服务器 + 第二客户端同步 | 已提交且实机通过（2026-10-21，8 项）。感染体在沟里的寻路被用户去掉了，没做 | master |
| R1a | 巨石（Mesh、破碎）、鹤嘴锄敲击、田野石 | 已提交且实机通过 | master |
| R1b | 地上的散石（田野石 / 河卵石 / 燧石）、石子 | 已提交且实机通过 | master |
| R1c | 原版石头建材 44 种 + 燧石退役，砾石掉落表只掉砾石 | 已提交且实机通过 | `42c28af6`，没进 master |
| 铲子 D 形把手、挂工具架；导出规则放行架子上的东西；`forest_home_b` 重新导出 | | 已提交且实机通过 | `42c28af6`，没进 master |
| A2 第二轮 | 大铁锹 1.6 m、一铲约 300 L、挖完手动倒（自动甩土已去掉），拿着和三个动作重做，离线穿模检查 0 个坏样本 | 已提交且实机通过（2026-10-22："暂时这样吧，PASS"） | `66802745`，没进 master |
| R2 第一到三轮 | 原版石头换成花岗岩照片贴图（4 个变体），岩石进高度场（`rock` 深度限制），石面空手打不动（`RockFace`），坑壁补面（`EarthSkin.walls`），挖过的地只能用工具，鹤嘴锄凿一下直接凿低、按体积掉石块和石子（第三轮），`EarthProbe` 开发探针 | 已提交且实机通过（2026-10-23 用户 PASS） | `66802745`，没进 master |
| R3 | 地下岩体、和 C 的地下地质对接 | 尚未开始（只在 R1 / R2 设计里列为下一步） | — |
| 手推车 | 装土和石头运走 | 仅设计意向（用户 2026-10-22 提出，开了一张设计任务卡，没做） | — |
| 自然土空手不能挖 | 沙盒设计第 12 节第 2 条：放铲子以后空手挖自然土改成"用铲子" | 尚未开始。现在只有挖过的地和岩石空手打不动 | — |

[文档] + [代码已证实]（提交位置）

## 3. 关键类和文件

- **土方核心**（`earthwork/`）：
  - `Earthwork`：编辑、挖、倒、刮平、坍落、松回填土、破坏规则；
  - `EarthGrid`：网格常量 `PER_BLOCK = 4`，硬底、`pickFloor`；
  - `EarthChunk`：区块数据，`VERSION = 3`；
  - `EarthChunkHolder` / `EarthChunkPacket`：存档和同步；
  - `BulkMaterials` / `BulkHolder`：散装材料表，`data/apocalypse_firstlight/bulk_materials/*.json`，7 种，其中 `rubble` 是 R2 加的。
- **岩石**（`boulder/`）：巨石方块、模型、数据、索引；`BoulderStrike`（镐敲巨石和凿地面）、`BoulderEarth`；`LooseStones*`（散石）；`RockFace`（生存里石面打不动）；`PickPacket`。
- **铲子：** `item/ShovelItem`（`CAPACITY = 1216` 量子 = 296.88 L）、`interaction/ShovelAction`（服务端每一铲）、`client/ShovelClient`、`client/ShovelItemRenderer`、`client/skeleton/SkeletonActions`（铲子的拿着姿势、三个动作、铲头锚点）。
- **地形平滑接口**（C 原有的 TerrainSmooth，E 改过，见第 4 节）：`terrainsmooth/client/EarthSkin`（表皮和坑壁）。
- **Mixin：**
  - E 加的：`EarthworkLevelChunkMixin`、`client.EarthworkVisGraphMixin`、`client.EarthworkEntityLightMixin`、`client.EarthworkParticleLightMixin`；
  - E 改过的：`TerrainSmoothBlockStateMixin`。
- **开发和测试：**
  - `dev/earthwork/EarthCommands`（`/afl dev earth …`、`/afl dev rock …`）；
  - `dev/earthwork/EarthProbe`（`-Pearthprobe=WORLD`，见 `src/dev/earth-probe-client.init.gradle`）；
  - `src/test/.../earthwork/EarthworkCheck`（`gradlew earthworkCheck`）。
- **生成脚本：**
  - `tools/author-shovel-actions-a2.cjs`：铲子动作，`--check` 含穿模检查；
  - `tools/build-round-point-shovel-v1.mjs`：铲子模型；
  - `tools/boulders/*.mjs`：巨石、散石、田野石、石子、燧石；
  - `tools/rock/build-rock-face.mjs`：石面贴图。
- **文档：**
  - `docs/gameplay/sandbox_v1_design.md`：总设计；
  - `docs/worldgen/earthwork_a1.md`、`earthwork_a2.md`、`earthwork_a3.md`；
  - `docs/gameplay/rock_r1_stones_boulders_design.md`、`rock_r2_rock_face_mining_design.md`；
  - Wiki：`wiki/guide/earth-and-rock.md`（中英）。

## 4. 跨会话依赖

1. **E 改过的 TerrainSmooth 文件**（E 全部历史）：`SmoothRules`、`TerrainSmooth`、`client/EarthSkin`、`client/SmoothOutline`、`client/SmoothSurfaceModel`、`client/TerrainSmoothClient`、`mixin/TerrainSmoothBlockStateMixin`。没进 master 的只有前三个：
   - `SmoothRules` / `TerrainSmooth`：`notFree` 加了一个带 `walls` 参数的重载，允许天然台阶的坡脚被编辑。C 2026-10-22 已确认合并时两个重载都保留，C 自己不调用。
   - `EarthSkin`：坑壁补画旁边没编辑过的格子被剔掉的面，剔除规则没改。

   [代码已证实]
2. **和 C 的道路、地质、世界生成有关的：**
   - 轮廓规则：自然地面、岩石、矿石，以及 C 的 `graded_turf` 和 `road_surface` 都不画轮廓线，放的方块、桥、路缘、构筑物照旧画（`SmoothOutline`，已在 master）。
   - 石头换皮：`assets/minecraft/blockstates/stone.json` 和 `textures/block/stone*.png` 覆盖了原版石头，注册 ID 和世界生成不变。
   - 砾石掉落表：`data/minecraft/loot_tables/blocks/gravel.json` 覆盖为只掉砾石。
   - E 没有改 C 的噪声、生物群系、道路、城镇。

   C 的 U4 洞口造型（碗口、坡道、碎石锥）可以给 E 的巨石和散石当摆放点，C 只给几何（`UndergroundPlan.groundAt` 读挖过以后的地面），石头的材料和撒法归岩石系统。这原来是 E 的工作，现在归 C 自己。还欠两项没做：给 C 的 U1 第 11.7 节答复（6 条）和 R1 设计第 7 节的摆放提案。

   [文档]
3. **和 Main 的车辆、渲染有关的：**
   - `vehicle/physics/LevelVehicleWorld`：土方在平滑地形之前插了一层，`VehicleWorld.Source.EARTH` 在 master；这次没合并的改动是 `soil` 换成 `ground`，让岩石和碎石也算车辆地面。
   - `first_pickup_v0_design.md` 第 5 节的来源列表改过编号（在 master）。
   - 4 个客户端 Mixin 碰了区块遮挡、实体光照和粒子光照，Oculus / Embeddium 下 A3 实机通过过。
   - 还有个 T15 两人同乘时平地上咔咔响的问题，已转交 Main。
  - E 分支动过几个 Main 或 B 的文件，合并时要让它们知道：
    - Main 的建筑模板 `data/apocalypse_firstlight/structures/forest_home_b.nbt`：用户在 E 分支上摆了家具、冷柜、插座和工具架上的工具，重新导出，用户 PASS；
    - 共用的导出代码 `authoring/BuildingAuthoringService`：放行枪架、工具架格子和工作台木钉上的东西；
    - `cabin/WoodFurnitureV2`、`cabin/Workbench`、`client/WoodGunRackRenderer`：铲子挂到木质工具架上，工作台和搁板不收铲子。

   [代码已证实] / [文档]
4. **网络协议：** E 分支 `"69"`，master `"71"`，C 分支 `"68"`。E 在 R2 改过 `EarthChunkPacket`（岩石深度限制、碎石材料）。下一次合并按当时最高 +1，现在是 `"72"`。[代码已证实]
5. **build.gradle、语言文件、资源：**
   - `build.gradle` 里的 `earthworkCheck`、`runServer` / `runClient2` 已经在 master，没合并的两个提交不改 `build.gradle`；
   - lang：R2 删了 `pick.covered`，铲子第二轮删了 `shovel.nowhere`，另外加了 R2 的提示；
   - 资源：石面贴图和模型（`textures/block/rock`、`models/block/rock`）、铲子模型、贴图、图标、动作。

   [代码已证实]
6. **注册 ID 和数据格式：**
   - 没合并的两个提交**没有新注册 ID**；E 的物品和方块（`round_point_shovel`、`pick_mattock`、`field_stone`、`pebble`、`flint`、巨石方块、`loose_stones`）都已经在 master。
   - 新数据有三份：`bulk_materials/rubble.json`、`tags/items/retired/stone.json`（R1c，45 个 ID）、砾石掉落表。
   - 注意：master 里另有一个物品 `concrete_rubble`，和土方的散装材料 `rubble` 不是一回事。

   [代码已证实]
7. **存档兼容：**
   - `EarthChunk.VERSION = 3`。版本 1 读进来没有松土、硬底在 `top0 - 1`；版本 2 读进来没有岩石深度限制（可以继续用）。
   - 散装材料按 ID 存，不认识的 ID 当回填土读。
   - 土方没有总开关，只有 `TerrainSmoothConfig.terrainSmoothEnabled`（SERVER 配置）。设计第 3.10 节说关掉以后退回整格土，**这次没有核对**。
   - 用 R2 的分支进过的世界，再用 master（版本 2）的版本打开，会丢掉岩石深度数据 [推断]。所以 R2 合并前，别用 master 的版本打开在 E 分支上玩过的存档。
8. **还没解决的问题：** 见第 7 节。

## 5. 测试情况

**过了的：**
- 实机：A1、A2 第一版、A3（8 项）、R1a、R1b、R1c、D 形把手和挂架、`forest_home_b`、A2 第二轮的铲子。
- 离线：
  - `earthworkCheck`：铲子第二轮以后 0 失败；
  - R2 第三轮的岩石部分：0 失败；
  - `node tools/author-shovel-actions-a2.cjs --check`：拿着、挖、倒、刮平都是 0 个坏样本；
  - Wiki check：只剩缺截图的 4 个报错，和土方无关。

**没过或没测的：**
- R2 第一到三轮：用户 2026-10-23 实机 PASS（凿岩、石面空手打不动、坑壁补面）。
- 土方的性能没有正式测过（JFR、服务器 tick）。
- 专用服务器上的 R2 没看过。
- 2026-10-21 时 master 的 `compileDevJava` 编译不过（开发源码集缺 Jade 的 compileOnly），已告诉 Main，现在修没修**没核对**。

## 6. 当前的设计决定（用户定的）

- **职责划分：** C 管世界生成。原来的 E（现在都归 C）管地形数据、挖、岩石、地质材料；建筑、拆除、构件、坍塌归 B。
- **每一步都等用户点头再做**，不自己往下一阶段走。
- **自然地面不画轮廓线。** 这个交互方向不要自己改。
- **铲子：** 挖一铲倒一铲，不自动甩土（"不然我这挖个坑，周围都填满了"）。以后做手推车。
- **动作：** 手看成套在把上的环，可以顺着把滑。工具动作要过离线穿模检查，坏样本 0 个。Main 的手臂运行时碰撞不搬过来。所有动作要沉稳。
- **镐：** 像英灵神殿那样凿一下直接凿低，掉石块和石子，不留碎石，不用铲子清（R2 第三轮）。两格以上的天然崖壁不平滑、不能凿。
- **原版石头：** 石头建材和燧石退役；天然 `minecraft:stone` 留作世界的岩体，只换皮。
- **巨石：** 一块最多敲 50 下。
- **其他：** 配方重做之前不加配方。熔炉先不做。感染体在沟里的寻路先不管。

[文档]

## 7. 已知风险和没解决的问题

- **R2 和铲子第二轮在同一个提交里**（共用同一批文件）；两边都已实机 PASS（2026-10-22 / 10-23），合并时一起进。
- **R2 的地方锁定：** C 2026-10-23 做了第四轮（R2 设计第 13 节），离线检查通过，用户 2026-10-23 实机 PASS：
  - 台阶顶上那一圈：两格台阶的上沿放开，三格以上的崖壁照旧锁；共用顶点"谁先改归谁"；
  - 墙角快照对不上：`EarthSkin.walls` 沿边补齐表皮和没编辑的邻列之间的缝（朝里、朝外两种）。
- **动作和实际位置的偏差：**
  - 挖土动作是固定的，坑挖深以后动画里的铲头浮在坑底上方；
  - 倒和刮平的铲头锚点只在身前 1.0–1.08 m，落点太近或太远时，动画和实际动土的地方差最多约 0.3 m。
- **石子和燧石：** 物品质量是 0.15 / 0.2 kg，世界里放大后看起来 10–14 cm，现实里应有 0.5–1.8 kg，还没对上。
- **声音：** 铲子和镐还是原版声音占位。
- **小问题：** `ShovelItem.CAPACITY` 的注释还写着"thrown aside"（已经不甩土了），只是注释过时。
- **坑底积水：** 水不处理（沙盒设计第 3.12 节），坑里不会积水。

## 8. 安全整合建议

**实际做法（用户 2026-10-23 改了路线）：** 用户要求直接把 E 合到 C 的工作树，不走 Main 的集成分支。

- `feature/earthwork-a`（`66802745`）用 `--no-ff` 合进了 `feature/underground-u2`，合并提交 `a2730863`。没有冲突。
- 协议取 E 的 `69`（C 原来是 `68`，C 自己没加新包）。
- 合并后在 C 的工作树里跑过三项检查：
  - `compileJava`：通过；
  - `earthworkCheck`：2378882 项，0 失败；
  - `node tools/author-shovel-actions-a2.cjs --check`：穿模数字和 E 一样。它报 4 个动作 JSON「stale」，但这 4 个文件去掉 `\r` 后和 E 逐字节一致，git 里也是同一个 blob，只是工作区换行符不同。
- 没在游戏里看。`undergroundCheck` 由 C 在合并后补跑（2026-10-23）：6 个种子 PASS，开局洞 Bear Hollow Cave 的距离、路线和合并前一样（洞口离老宅 1.2–2.2 km，离路 190–932 m，只带灯 83–336 m，0 个不通的点）。
- 下一步 master 进来时，`AflNetwork.PROTOCOL` 会冲突，取两边最高 +1（现在是 `72`）。
- 下面第 1–4 条是原来的建议，留作参考；第 3 条现在反过来，是 C 分支整体进 master。

原来的推荐顺序：E 分支 → Main 的临时集成分支 → 完整检查 → master → C 从新 master 同步。

1. **R2 已验收：** 用户 2026-10-23 在 E 分支上看过凿岩、石面空手打不动、坑壁补面，PASS。整个 E 分支一起进集成分支，不要拆开挑提交（`66802745` 里铲子和 R2 共用 `Earthwork`、`ShovelAction`、`EarthGrid`、`EarthSkin`）。
2. **Main 在临时分支上合并 `feature/earthwork-a`：**
   - 协议号写成当时最高 +1（现在是 72）；
   - 检查 lang 两边的行是否对齐；
   - 跑 `compileJava`、`earthworkCheck`、`node tools/author-shovel-actions-a2.cjs --check`，以及 `tools/boulders/`、`tools/rock/` 里会解析 `--check` 的 `build-*.mjs`；
   - 进游戏看一眼铲子和镐。
3. **进 master 以后：** C 把 master 合进 `feature/underground-u2`。试合并显示和 E 没有冲突，只要 lang 对齐一下。
4. **不要做的：**
   - 不要把 E 的工作树复制到 C 的工作树里覆盖；
   - 不要动 C 现在的分支内容；
   - 不要删 E 的工作树，等进了 master、C 确认接手以后，再由 Main 按分支清理规则处理。

- **合并方式（用户 2026-10-23）：** Main 在忙，用户说也可以由 E 来合并 master，具体等用户发话再做。

## 9. C 接手后的优先级（建议）

1. E 分支已经在 C 分支里（`a2730863`）。等 C 自己的东西 PASS 后，整个 C 分支进 master，协议取最高 +1。
2. 接着做已经开始的事，**不要重做 Earthwork**：
   - R2 的锁定问题（台阶顶那一圈、墙角的缝）：2026-10-23 已做，用户实机 PASS（R2 设计第 13 节）；
   - 给 C 自己 U1 第 11.7 节的那 6 条答复和摆放提案，现在是 C 内部的事，可以直接定；
   - U4 洞口的碗底和碎石锥脚下撒巨石和散石。
3. 再按用户的顺序：R3 地下岩体（和 C 的地下地质合在一起做），手推车（设计卡已有；装车和运输的部分和 B 的放置系统要商量），空手不能挖自然土（沙盒设计第 12 节第 2 条）。
4. B 的建筑破坏系统（可编辑建筑和统一环境破坏 V0）需要从这边拿到这些接口：
   - 自然土弹坑：调高度场的挖低；
   - 碎石：作为散装材料；
   - 给地基支撑查"某个位置的地面现在多高、是什么材料"：现在只有内部函数，还没有正式接口；
   - 墙板的占位方块算"非自然方块"，会锁住旁边的土方列。

   天然岩石的体积破坏还没有现成支持，要另外研究。
