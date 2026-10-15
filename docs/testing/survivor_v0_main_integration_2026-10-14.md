# 集成审核报告：幸存者死亡与继任 V0 同步最新 Main（2026-10-14）

状态：**分支内集成完成，编译通过，GameTest 没有新回归（第 5 节）；实机回归等用户做（第 6 节）。合并进 master 由 Main 会话做，见第 8 节。**

- 分支：`feature/survivor-v0`，工作区 `D:/Minecraft Modding/AFL-survivor-v0`。Main 工作目录没有动。
- 同步的 Main：`a650e8d6`（联邦六州设为新建普通世界默认，含 C1/C2 合并 `082ebd77`、收音机 V1、暗线 V1 第 5–7 阶段）。分叉点 `f08982d0`。
- 合并方式照 Main 合并联邦分支的做法：先打检查点分支，再 `--no-ff` 合并，冲突在合并提交里解决。方向反过来：这次是把 Main 合进功能分支，这样以后 Main 合并这个分支时没有冲突。
- 检查点：`checkpoint/survivor-v0-pre-main-sync-20261014` → `0548dc36`（同步前分支的最后一个提交）。

## 1. 未提交文件的清点和保存

同步前工作区有 18 个已修改文件、102 个新文件，全部保存成 3 个提交，没有删除或覆盖任何文件：

| 提交 | 内容 | 状态 |
|---|---|---|
| `23ef605d` 人物皮肤资源 | 名册 60 人皮肤 `f01`–`f30`、`m01`–`m30`，测试皮肤 `test_classic` / `test_slim`，生成器 `tools/build-survivor-skins.mjs`、`build-survivor-test-skins.mjs`，总览图 `design/survivor_skins/` | 用户实机 PASS |
| `a754bc71` 幸存者系统 + 字体 | 身份、固定名册、难度锁、建世界界面的幸存者模式按钮、尸体外观、`Identity` 包；AFL Sans SC 全局字体（字体文件、Mixin、生成脚本）；文档 `survivor_death_succession_v0.md`、`survivor_start_template_pool.md`（只有设计）、`afl_font_v1.md`；多人测试脚本 `src/dev/survivor-multiplayer.init.gradle` | 用户实机 PASS；名册用尽 / 回归未测 |
| `0548dc36` 网格背包概念 | `docs/ui/afl_unified_ui_v0_research.md`、`afl_inventory_grid_v0_concept.md`、`design/ui/inventory_grid_v0/`；**只有文档和图，没有代码** | 布局用户通过；正式开发暂停 |

字体和幸存者代码放在同一个提交里，因为 `apocalypse_firstlight.mixins.json` 和语言文件里两边的条目交错在一起，拆开会让中间那个提交引用不存在的类。

## 2. 冲突和处理

自动合并失败的 3 个文件：

| 文件 | 冲突 | 处理 |
|---|---|---|
| `network/AflNetwork.java` | 协议号：分支 42，Main 40 | 改成 **43**。分叉点是 39，两边各自升了号：Main 的暗线第 5 阶段 39 → 40，分支 39 → 40 → 41 → 42。两边的“40”包集合不同，合并后的版本和两边都不能互连，所以取一个新号 |
| `ApocalypseFirstLight.java` | 两边在同一个位置各注册了一份服务端配置 | 两份都保留：`apocalypse_firstlight-survivor.toml`（幸存者）和 `apocalypse_firstlight-worldgen.toml`（世界生成版本锁） |
| `apocalypse_firstlight.mixins.json` | 两边都改了 client 和 mixins 两行 | 取并集：client 加上 Main 的 `CreateWorldFederalDefaultMixin` 和分支的 `SurvivorSkinPlayerInfoMixin` 加 5 个字体 Mixin；mixins 用 Main 那一行（多了 `BiomeSourceFederalMixin`、`WorldgenLockSaveMixin`、`DedicatedServerFederalDefaultMixin`），分支的 `SurvivorRespawnGateMixin`、`AflDifficultyLockMixin` 在没有冲突的末行里，都在 |

自动合并成功、另外核对过的：

- **网络包注册**：`SurvivorPackets.register` 在 `WiringPackets.register` 之后，暗线的包号不变；没有重复注册。
- **语言文件**：`en_us.json` 和 `zh_cn.json` 都是合法 JSON，没有重复的键，两边各 1038 行，逐行键名和空行对齐（脚本检查）。
- `client/WorldInteractionHint.java`、`docs/ui/afl_overlay_ui_style_v1.md`：只有 Main 那边的改动，分支没碰。

## 3. 合并后发现并修好的语义冲突

`survivor/SurvivorSpawns.java`：继任出生点在死亡时粗筛候选列，读 Terrain V2 规划判断干地、离岸距离和坡度。以前是按种子查规划（`TerrainPlanStore.peek(seed)`）。C2 以后规定规划只能从世界生成器绑定的那一份读（`RandomStateSeedAccess.apocalypse$terrainPlanSurface()`），因为同一个种子的 r1 世界和联邦世界规划不同；而 C3 已经把联邦设成新世界默认。按种子查，联邦世界会读到 r1 的规划（或者查不到），继任者可能被放进水里或者岸边。现在改成读绑定的那一份，和 `ForestGate` 的写法一样。

幸存者其它代码（`survivor/`、`client/survivor/`、`client/font/`）里没有别的按种子读世界生成数据的地方（已搜索）。

## 4. 共存检查（读代码）

| 系统 | 和幸存者 V0 的接触点 | 结论 |
|---|---|---|
| 联邦 C1/C2/C3 | 建世界界面：Main 在 `WorldCreationUiState` 构造时把世界类型设成联邦；分支在 Game 标签页把难度按钮换成幸存者模式按钮。专用服务器：Main 改默认世界类型，分支读 `apocalypse_firstlight-survivor.toml` 的开局模式 | 改的是不同的控件和不同的配置，互不影响；出生点规划已按第 3 节修正 |
| 世界生成版本锁 | 两边各有自己的 `SavedData`：`data/apocalypse_firstlight_survivors.dat` 和版本锁记录。版本锁拒绝的世界在加载时直接报错打不开（并拦下保存），所以不会出现“世界被锁住但幸存者还在写记录”的情况。这个工作区里以前的测试存档第一次用新版本打开时，版本锁会把它当成“锁之前的旧世界”收编（日志警告，不拦） | 不重叠 |
| 建筑暗线 V1 | 网络包（见第 2 节）；死亡时物品进尸体，不涉及暗线方块 | 不重叠 |
| 收音机 V1 | 没有共享代码 | 不重叠 |
| 难度锁 | 分支把原版难度锁在普通；Main 没有改难度的代码 | 不重叠 |

## 5. 编译和自动测试

- `gradlew compileJava --offline`：通过（只有原有的过时 API 警告）。
- GameTest（`runGameTestServer -PaflWithoutShaders --offline`）：见下面的结果。幸存者系统本身没有 GameTest，这一步主要验证合并后服务端能启动、服务端 Mixin 能全部应用，并回归暗线、加油站、老宅 M2 等已有测试。

结果：服务端正常启动（所有服务端 Mixin 都应用上了），74 项必需测试里 58 项通过、**16 项失败**。为了分清是不是合并引起的，在临时目录检出纯 master `a650e8d6`（临时 worktree，跑完已删掉）跑同一套：**master 自己失败 17 项**，包含合并版失败的全部 16 项，外加 `singlesourceflowsanddrains`（文档里记过的不稳定流体测试）。合并版没有任何 master 没有的失败。

两边都失败的 16 项，都在这次合并没碰过的系统里：

| 系统 | 测试 |
|---|---|
| 废液管道 / 储罐 | `eastwasteupward`、`westwasteupward`、`northwasteupward`、`southwasteupward`、`horizontalwaste`、`wastebackpressureandresume` |
| 桶、水、化学反应器 | `buckettankpipeandreactorstorage`、`survivalbucketuseandwaterpublichooks`、`survivalbreaktooltipandblockitemreplacement`、`jadedataandsaveditemtooltips`、`recipesloadwithexacttimesandnovanillabypass`、`chemicalprocessingisatomicandpauseswhenblocked` |
| 爆炸 | `vanillatntuseslivestrength` |
| 枪械 | `reloadcommitandcancellation`、`classdrivenpresentation`、`successfulshotnoisematrix` |

失败信息大多是“25 mB”“Bucket → tank”这类旧断言，看起来是后来的流体改成升、原版桶停用之后测试没跟着改；这次没有去查，也没有改。

默认这一轮只跑 `apocalypse_firstlight` 命名空间（74 项）。暗线、老宅 M2、木屋屋顶、加油站的测试在各自的命名空间里，要用 `src/dev/*-gametest.init.gradle` 单独开，结果见下面。

专项 GameTest（合并后的版本，各跑一次）：

| 组 | 命令 | 结果 |
|---|---|---|
| 暗线 | `-I src/dev/wiring-gametest.init.gradle` | 13/13 通过（含 `template`） |
| 老宅 M2（收音机、椅子、柜子、16 个方块的生存挖掘） | `-I src/dev/forest-home-m2-gametest.init.gradle` | 1/1 通过 |
| 木屋屋顶 | `-I src/dev/cabin-roof-gametest.init.gradle` | 1/1 通过 |
| 加油站 A1（结构旋转） | `-I src/dev/gas-station-01-gametest.init.gradle` | 1/1 通过 |

GameTest 服务器的测试世界是 r1（Main 的 C3 特意保留），所以联邦世界里的行为只能靠第 6 节的实机回归。

**结论：没有发现合并引入的回归。**

日志在会话的临时目录里（`gametest.log`、`gametest-master.log`），没有放进仓库。

## 6. 实机回归（等用户做，没做之前不算通过）

用这个工作区的 `runClient`（`D:/Minecraft Modding/AFL-survivor-v0`），它的存档在这个工作区自己的 `run/` 里，不碰 PCL 正式测试世界。

1. 主界面、选项里的字体正常（AFL Sans SC）。
2. 新建世界：世界类型按钮默认是联邦；Game 标签页里是“幸存者模式：普通 / 困难”按钮，没有难度按钮。
3. 进入新的联邦世界：首位幸存者命名提示；`/kill` 一次，死亡结算界面正常，继任者是名册里的人，出生在干地（不在水里、不在沙滩），尸体在死亡点，东西在尸体里。
4. 暗线：拿接线扳手放一组开关和灯，能通电、透视能看到线（暗线的包和幸存者的包在同一个频道）。
5. 收音机：放一台手摇收音机，能用。
6. 打开这个工作区以前的幸存者测试存档（r1 地形）：名字、皮肤还在；`/kill` 一次，继任正常。

## 7. 没做 / 已知限制

- 第 6 节的实机回归。
- 联邦世界里的继任出生是第 3 节修过的代码，没有实机测过。
- 多人联机没有重测（之前在协议 42 上测过）。
- 名册用尽、回归没有测过（和之前一样）。
- 开局模板池只有设计文档。
- 网格背包：只有概念图。Main 集成通过以后，从最新 Main 开 `feature/inventory-grid-v0` 继续。

## 8. 合并进 master

- 这个分支已经包含 master `a650e8d6`，所以 master 合并它不会有冲突。
- 合并在 Main 的工作目录里做（`git merge --no-ff feature/survivor-v0`）。Main 工作目录的状态由 Main 会话自己确认；D 会话不碰它。
- 合并以后要核对的：`git merge-base --is-ancestor feature/survivor-v0 master` 成立；如果 master 在这期间没有新提交，合并后 master 的文件树和这个分支完全相同（`git diff feature/survivor-v0 master` 为空），这份报告的编译和 GameTest 结果就直接适用；如果 master 有新提交，要在 master 上重新编译一次。
- 没有推送。推送要用户另外确认。
