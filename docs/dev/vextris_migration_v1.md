# AFL → Vextris 迁移 V1（Mesh 加载、缓存与渲染）

日期：2026-10-10。分支：`vextris-migration`（基线 `678d70fab792f3cf3b069c894d3bb8c3076a9b7c`；迁移由作者提交为 `afa0f2e8`；2026-10-11 作者批准后快进合并进 `master`（`860f6fbb`，已推送到 GitHub），此后 `master` 是 AFL 的默认开发分支，`vextris-migration` 暂时保留）。状态：**代码迁移完成，离线与自动化运行时验证完成，等待人工验收**（验收项见文末）。

2026-10-10 补充：Vextris 依赖已从 `0.1.0-dev` 升级到首个公开 Alpha **`0.1.0-alpha.1`**（见下面的“升级到 Vextris 0.1.0-alpha.1”）。

2026-10-16 补充（`feature/federal-tunnels`，未合并）：道路、桥、隧道接显卡缓存，依赖升到 **`io.github.dwom10912:vextris:0.1.1-alpha.4`**（`build.gradle` 坐标和仓库组、`gradle.properties` 的 `vextris_version` / `vextris_repo=../Vextris/build/internal-repo`、mods.toml `[0.1.1-alpha.4,0.2.0-alpha)`）。用法见 `docs/worldgen/federal_highways_c3.md` 10.5。

## 结论

- AFL 现在依赖独立的 Mesh 库 **Vextris 0.1.0-alpha.1**（作者 Antaurora，仓库 `D:/Minecraft Modding/Vextris`，Apache-2.0）。迁移时用的是 `0.1.0-dev`，两者的类文件逐字节相同。
  - 加载、校验、烘焙、缓存、重载、CPU 顶点提交、两层 RenderType 和 GeckoLib 骨骼上的 Mesh 提交，都由 Vextris 完成。
  - AFL 自己的 `client/mesh` 包（6 个类）已删除。
- **GeckoLib 4.7.4 继续负责** AFL 的骨骼和全部动画：
  - 枪械的 GeoItem 和控制器、换弹、射击、检视、相机骨骼，手部锚点的动画数据。
  - Vextris 只在 GeckoLib 已经算好的骨骼姿态上提交 Mesh 顶点（`GeckoMeshRenderer.renderBone` 只移到骨骼中心点再提交，不重复施加变换）。
- **以下内容留在 AFL，没有改动**：
  - 第一人称手臂（原版 PlayerModel）、手部绑定、晃动 / 后坐 / 开镜；
  - 附件、弹匣替换、枪械特效；
  - 方块 profile 与部件动画、区块绘制（`AflMeshChunking`、`AflStaticMeshModel`）、`AflMovingGlass`、Sundial 相关修正；
  - meshhit / meshshape、`AflShaderCompat`。
- 资源文件（Mesh、geo、动画、贴图）**一个都没改**：`src/main/resources/assets` 与基线完全相同。

## 依赖与构建

> 2026-10-15 起依赖换成内部基线 0.1.1-alpha.2（新坐标 `io.github.dwom10912:vextris`、仓库 `../Vextris/build/internal-repo`），见 [Vextris 0.1.1-alpha.2 接入](vextris_0_1_1_integration.md)。下表是 0.1.0-alpha.1 时的记录。

| 项 | 内容 |
|---|---|
| Gradle | `gradle.properties`：`vextris_version=0.1.0-alpha.1`（迁移时是 `0.1.0-dev`），`vextris_repo=../Vextris/build/repo` |
| | `build.gradle` 新增一个本地 Maven 仓库（只收 `com.antaurora.vextris` 组） |
| | `compileOnly` + `runtimeOnly fg.deobf("com.antaurora.vextris:vextris-forge-1.20.1:${vextris_version}")`。Vextris 不会被打包进 AFL JAR（已检查 JAR 里 `com/antaurora/vextris` 条目为 0） |
| 构建前提 | 先在 Vextris 仓库执行 `gradlew build publish`，生成 `build/repo` |
| `mods.toml` | `vextris` 强制依赖，`[0.1.0-alpha.1,0.2.0-alpha)`（迁移时是 `[0.1.0-dev,0.2)`），`ordering="AFTER"`，`side="BOTH"` |
| | `geckolib` 从 `[4.7,5)` **收窄为 `[4.7.4]`**，原因见下 |
| 运行 | 玩家需要同时安装 Vextris。缺少时 Forge 拒绝启动，并报 missing mandatory dependency |

**GeckoLib 范围为什么收窄**：Vextris 的 GeckoLib 兼容层只认已经验证过的版本（目前只有 4.7.4）；装了其他 GeckoLib 版本时，兼容层不可用，枪械 Mesh 无法绘制，`renderBone` 会抛出 `IllegalStateException`。所以改为让 Forge 在启动时明确拒绝，而不是游戏中途崩溃。**这一条需要作者确认**；另一种做法是先在 Vextris 中验证更多 GeckoLib 版本，再放宽范围。

**为什么用 `ordering="AFTER"`**：AFL 的方块 profile 需要用到 Vextris 这一轮刚发布的 Mesh。Forge 按 mod 顺序发出注册事件，而重载监听器严格按注册顺序执行 apply（`SimpleReloadInstance`：每个监听器都要等前一个整体完成）。新的 `AflBlockMeshProfileReload` 在 apply 时读取 `VextrisMeshes.snapshot()`；如果发现 generation 没有前进，会写一条警告。

## 已迁移的 API

| AFL（已删除） | Vextris |
|---|---|
| `client.mesh.AflMeshLoader.load(res, sidecar, geo)` | `com.antaurora.vextris.mesh.MeshLoader.load(...)` |
| `AflMeshModel` / `AflMeshPart` / `AflMeshPart.Layer` | `MeshModel` / `MeshPart` / `MeshLayer` |
| `AflMeshCache.snapshot().get(geo)` | `VextrisMeshes.byGeometry(geo)`（键还是 `ns:geo/<stem>.geo.json`） |
| `AflMeshCache.snapshot().generation()` | `VextrisMeshes.generation()` |
| `AflMeshRenderer.renderPartsAtCurrentPose(...)` | `MeshRenderer.renderParts(...)` |
| `AflMeshRenderer.render(mesh, GeoBone, ...)` | `compat.geckolib.GeckoMeshRenderer.renderBone(...)` |
| `AflMeshRenderer.collectBounds(mesh, GeoBone, ...)` | `GeckoMeshRenderer.collectBounds(...)` |
| `AflMeshRenderer.cullBackFaces` / `.Metrics` | `MeshRenderer.cullBackFaces` / `.Metrics` |
| `AflHybridMeshRendering.renderAtCurrentPose(geo, tex, ...)` | `GeckoMeshRenderer.render(geo, tex, ...)` |
| `AflMeshCache` 中的方块 profile 加载 | 新类 `client/blockmesh/AflBlockMeshProfileReload`（AFL 专属） |

改动的调用点共 21 个文件：
- 枪械与第一人称：`NativeGunContextRenderer`、`NativeGunRenderProfile`、`NativeGunFx`、`NativeMuzzleRendering`、`NativeMagazineRoundRendering`、`NativeAttachmentGuiFit`、`MaintenanceGunRendering`、`GunInspectionController`、`JerryCanFirstPerson`；
- 物品与方块：`AflStaticMeshItemRenderer`、`OfficeChairRenderer`、`AflGoodsLibrary`、`client/blockmesh/*`（5 个）；
- 开发用高速公路代码：4 个（`dev/highwaymesh`）。

所有改动都是一一对应的替换：每个被调用的 Vextris 方法，都是从对应的 AFL 方法原样抽取过去的，见 Vextris 的 `docs/provenance.md`（原来的 `NOTICE.md`）。

离线工具也改为使用 Vextris：
- `tools/verify-afl-mesh.mjs`：jshell 改为从 classpath 加载 Vextris 类；源码断言改为检查新 API；GeoBone 的渲染和包围盒检查改为调用与 `GeckoMeshRenderer` 相同的辅助函数，因为离线没有 FML，兼容层的版本检查会拒绝。
- `tools/highway-v2-0/verify-loader.mjs`、`tools/highway-v2-m1a/*`、`tools/highway-v2-m1b/*`：改为使用 `../Vextris/build/libs/vextris-forge-1.20.1-0.1.0-alpha.1.jar`（迁移时是 `-0.1.0-dev.jar`），也可以用 `VEXTRIS_JAR` 指定。

## 行为变化：`closedSolid` 修复（82 个部件）

Vextris 修正了 AFL 焊接哈希的碰撞问题：互为对径点的位置会得到同一个哈希键，旧算法因此把带 UV 接缝的封闭实体误判为开放。AFL 有 **82 个部件**从 `closed=false` 变为 `true`，没有反方向的变化，清单见 Vextris 的 `docs/verification/afl-parity-2026-10-10.txt`。

影响两处：

1. **第一人称枪械的背面跳过**：br51、hr55、瞄具、弹壳特效的部件开始跳过背面。部件确实封闭时，从外面看结果不变。
2. **区块静态 Mesh**（`AflStaticMeshModel.emit` 只为非封闭部件生成背面）：
   - 涉及配电箱、柴油发电机、便携发电机、电表箱、铅箱、垃圾桶、收银机、门、办公椅等方块的部件，不再生成背面；
   - 从外面看结果不变，镜头进入部件内部时，原来看到的内壁会消失。

验证结果和截图对比见下一节。

## 验证（2026-10-10）

### 构建与离线检查

| 项 | 结果 |
|---|---|
| `compileJava compileDevJava --offline` | 通过。在删除旧类前先编译了一次：唯一的报错就在旧的 `AflMeshCache` 本身，说明包外已没有引用 |
| `gradlew build --offline` | 通过（`projectionMathTest`、`terrainContractTest` 都 PASS） |
| AFL JAR | `com/antaurora/vextris` 条目 0；`client/mesh` 条目 0；`dev` 条目 0 |
| | 字节码引用的是 Vextris API，已重混淆 |
| | `mods.toml` 含 `vextris` 强制依赖和 `geckolib [4.7.4]` |
| AFL 一致性（Vextris `aflParity`，对照基线 worktree 中 AFL 原来的加载器） | 65/65 个 Mesh 逐角点、逐比特一致 |
| | GeckoLib 绑定姿态最大误差 0 |
| | `closed` 变化 82 个，只有 false → true |
| | 资源与基线完全相同 |
| `tools/highway-v2-m1a/verify.mjs` | 通过，与迁移前数字相同 |
| `tools/highway-v2-m1b/verify.mjs --check` | 通过。M-1A 模型指纹不变，说明烘焙结果逐字节一致；结果文件没有任何改动 |
| `tools/highway-v2-0/verify-loader.mjs` | 通过，三角形数和烘焙面数与迁移前相同 |
| `tools/verify-afl-mesh.mjs` | 迁移前就有三处期望值过期，迁移前后都跑不通（见"已知问题"） |
| | 两边做完全相同的修正后：Java 加载器检查在基线和迁移版都 PASS；渲染器检查在两边都停在同一条过期计数（"quad submission count"） |

### 运行时：自动截图对比

迁移前后用同一个开发用截图脚本（`dev/MeshMigrationCapture`，`-I src/dev/mesh-migration-capture.init.gradle`）各跑一遍：

- **运行环境**：独立运行目录 `build/mesh-migration-capture`、新建的超平坦创造世界、固定正午光照和固定机位，Embeddium 和 Oculus 都在（不加载光影包）。
- **固定的环境变量**：窗口视为激活、释放鼠标、皮肤固定为 Steve。这一项是对比过程中发现必须固定的：第一次对比时开镜受窗口焦点影响、轻微晃动受鼠标影响、玩家皮肤来源也不一样。
- **基线**：在 git worktree `D:/Minecraft Modding/AFL-vextris-baseline`（`678d70fa`）里运行。

每轮 116 张截图：

| 类别 | 内容 |
|---|---|
| 枪械 | p9、br51、hr55、silverwood12、blackridge50：待机、战术换弹两帧、空仓待机、空仓换弹、射击（滑套/枪栓）、开镜、第三人称背面、第三人称正面 |
| 附件组合 | br51 加红点和弹鼓（`reload_empty_drum`）、p9 加红点、hr55 加红点 |
| 机器 | 柴油发电机、便携发电机、铅箱、垃圾桶、收银机、配电箱、电表箱：外观，以及镜头在模型内部朝前、朝后两个视角 |
| 重载 | `Minecraft#reloadResourcePacks`（F3+T 走的就是这个方法）之后，重拍枪械和机器 |

**结果**（baseline3 对 migrated2，CSV 见 `vextris_migration_v1/`）：

- 116 组里 42 组像素完全一致，86 组差异小于 0.05%。
- **枪械**：所有状态都一致，包括开镜，以及 82 个改判封闭部件中离镜头最近的红点 `optic_mount`。
  - 剩下的 0.1%–3.8% 只出现在快速动画中途的帧（换弹、射击）。差异图显示的是整幅画面边缘和地平线的错位，也就是相机骨骼所处的动画时刻不同，几何没有变化，没有缺面。
  - 两次基线之间同类帧的差异也达到 2.8%（`screenshot_noise_baseline_vs_baseline2.csv`）。
- **机器外观**：一致。
- **资源重载**：
  - 迁移后：Vextris generation 1 → 2，65 个 Mesh，拒绝 0 个；
  - 方块 profile 重载没有出现 "Vextris meshes were not reloaded first" 警告；
  - 重载后的截图与基线一致（柴油发电机内部视角除外）。
- **唯一可见的变化：柴油发电机内部视角**（`closedSolid` 的影响，图：`vextris_migration_v1/diesel_generator_inside*.jpg`）：
  - 基线：镜头在机身里时，能看到机壳内壁的背面，挡住了内部；
  - 迁移后：机壳判为封闭，不再生成内壁，能看到内部的滤清器圆筒和面板背面；
  - 其他机器的内部视角都没有变化（受影响的部件没有包住镜头位置）。

### 运行时：专用服务器

- `gradlew runServer -PaflWithoutShaders -I src/dev/mesh-migration-server.init.gradle`
  - 运行在独立目录 `build/vextris-server-check`，`eula.txt` 经作者同意写入；
  - 真实的 `DedicatedServer` 启动完成，日志记录了 vextris 0.1.0-dev（迁移时的版本）、geckolib 4.7.4、apocalypse_firstlight 1.0.0-alpha，没有加载客户端类的错误，随后自动停止。
- 不加 `-PaflWithoutShaders` 时，**Oculus** 在服务端加载失败（加载了客户端的 Screen 类）。这是开发运行时本来就有的问题，以前跑 GameTest 时就需要这个参数，与 Vextris 无关。

## 升级到 Vextris 0.1.0-alpha.1（2026-10-10）

作者确定首个公开版本为 `0.1.0-alpha.1`，并批准 AFL 跟着升级。修改范围只有依赖声明，**不涉及玩法和 Mesh 渲染逻辑**：

| 文件 | 修改 |
|---|---|
| `gradle.properties` | `vextris_version=0.1.0-dev` → `0.1.0-alpha.1` |
| `src/main/resources/META-INF/mods.toml` | vextris 的 `versionRange="[0.1.0-dev,0.2)"` → `"[0.1.0-alpha.1,0.2.0-alpha)"` |
| `tools/highway-v2-0/verify-loader.mjs`、`tools/highway-v2-m1a/verify.mjs`、`tools/highway-v2-m1b/verify.mjs` | 默认 JAR 路径改为 `vextris-forge-1.20.1-0.1.0-alpha.1.jar` |

**为什么范围必须改**：Forge 按 Maven 规则比较版本，`dev` 这类未知限定词排在正式版之后（`0.1.0-dev > 0.1.0 > 0.1.0-alpha.1`）。所以旧范围 `[0.1.0-dev,0.2)` 会拒绝 alpha.1，ANANKE 在运行时实测被拒。新范围接受整个 0.1 系列，拒绝 0.2 的预发布版和正式版。实测记录在 Vextris 的 `tools/version-check/`。

**代码层面没有变化**：alpha.1 和 0.1.0-dev 的 24 个类文件逐字节相同。区别只在元数据：版本号、Logo、Apache-2.0 许可证文件。

**验证（在 AFL 工作区）**：

| 项 | 结果 |
|---|---|
| `gradlew build --offline` | 通过：`projectionMathTest`、`terrainContractTest`，以及 WG-02 到 WG-05.1 全部 PASS |
| AFL JAR | `com/antaurora/vextris` 条目 0（没有打包 Vextris）；`mods.toml` 里是新范围 |
| 专用服务器 | 通过：`runServer -PaflWithoutShaders -I src/dev/mesh-migration-server.init.gradle`，日志 `vextris 0.1.0-alpha.1, geckolib 4.7.4, apocalypse_firstlight 1.0.0-alpha`，自动停止，没有 ERROR |
| 客户端截图对比 | 用 `MeshMigrationCapture` 拍了 116 张（标签 `alpha1`），与迁移时的 `migrated2` 对比：<br>• 35 张逐像素相同，9 张差异超过 0.5%，最大 2.47%（`br51_fp_reload_empty`，换弹中途帧的亚像素位移）；<br>• 没有一张超出两次 0.1.0-dev 运行之间的自然噪声；<br>• 重载后 generation 2，65 个 Mesh，0 个被拒。 |
| 公路验证脚本 | 三个脚本都通过，结果文件没有改动：<br>• `highway-v2-m1a/verify.mjs`：`M1A_OFFLINE_PASS`；<br>• `highway-v2-m1b/verify.mjs --check`：`M1B_OFFLINE_PASS`、`M1B_EXPORTED_LOADER_PASS`、`M1B_REPRODUCIBLE_OUTPUT_PASS`；<br>• `highway-v2-0/verify-loader.mjs`：`HIGHWAY_DEMO_JAVA_LOADER_PASS` |

- 截图在 `build/mesh-migration-capture/screenshots/alpha1_*`。这是独立运行目录，不是开发者自己的 `run/` 和存档。
- 用 0.1.0-dev 存过的世界，第一次用 alpha.1 打开时，Forge 会打一条 WARN：`vextris (version 0.1.0-dev -> 0.1.0-alpha.1)`。Vextris 不往存档写数据，这条警告无害。
- 上面“需要人工验收”的各项仍然有效。这次升级没有改变它们。

## 已知问题

- **柴油发电机内部视角改变**（见上）。这是唯一可见的行为变化，需要作者决定：
  - A：接受。和原版方块一样，从内部看不到背面；
  - B：恢复原样。让区块路径给所有部件都生成背面，代价是增加几何量；
  - C：只为指定资产生成背面。
  - 本次没有改。
- **GeckoLib 版本收窄为 `[4.7.4]`**，待作者确认。
- **`tools/verify-afl-mesh.mjs` 有三处迁移前就过期的期望值**，在基线上实测确认，这次没有修改：
  - `silverwood_12` 的三角形当量期望 6580，实际 6552；
  - fixture 四边形期望 6 个角点，实际 4 个（2026-10-05 起三角形会配对合并）；
  - 渲染器的 "quad submission count" 计数。
  - 另外，旧的 `AflMeshCache` 源码断言已改为检查新的 `AflBlockMeshProfileReload`。
- **开发环境的玩家皮肤会变**（在线拉取或默认皮肤），会影响手臂和第三人称截图；截图脚本已固定为 Steve。

## 需要人工验收（自动化没有覆盖）

1. **Sundial 等光影包下的枪械和机器**：截图是在不加载光影包时拍的。重点看透明层、阴影阶段、发光重绘、区块玻璃。
2. **真实操作**：
   - 真实射击（弹壳特效，`NativeGunFx` 的弹壳 Mesh）、连续射击；
   - 真实换弹，以及弹匣拆装和换弹匣（附件界面）；
   - 检视（`GunInspectionController`）、维护视图（`MaintenanceGunRendering` 的包围盒）；
   - 附件界面里的取景（`NativeAttachmentGuiFit`）、消音器。
3. **第一人称相机骨骼和手臂锚点的连续动画**：截图只验证了几个时间点。
4. **其他 Mesh 用户**：油桶第一人称（`JerryCanFirstPerson`）、办公椅、货架商品（`AflGoodsLibrary`）、动画方块（门、箱盖、发电机风扇和表针）开合时的运动玻璃（`AflMovingGlass`）。
5. **柴油发电机内部视角**：确认选择 A、B 还是 C。另外在第三人称镜头贴近或穿过机器时看一下（82 个部件中的方块部分）。
6. **使用现有存档**（不只是新世界）进入游戏，在原来的场景（Fuel Stop A1 等）里看一遍。
