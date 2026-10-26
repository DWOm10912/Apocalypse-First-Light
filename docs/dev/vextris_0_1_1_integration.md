# Vextris 0.1.1 接入（AFL 内部基线）

状态：
- **0.1.1-alpha.2**：已合进 master（`fd620b8e`，2026-10-15），用户实机回归 PASS（开光影和无光影，第 3 节）。
- **0.1.1-alpha.3 + T15 平滑法线**：分支 `feature/t15-smooth-normals`（2026-10-16），已做离线检查、编译和 GameTest；T15 平滑法线用户 2026-10-16 实机 PASS（第 4 节）。
- **0.1.1-alpha.4 + T15 显卡缓存**：同一分支（2026-10-16），已做编译和离线 Mesh 加载检查（95 个全部通过）；GameTest 没有重跑；用户 2026-10-16 实机 PASS（帧率、外观、多辆车）。需求和发版信息见 [Vextris 显卡缓存需求](vextris_gpu_cache_request.md)。
- **0.1.1-alpha.5 + 蒙皮部件**：用户 2026-10-25 批准，用于 Mesh 幸存者（format 4 `skins`、`SkinPose`、`MeshRendering.renderSkins`）；在 master 工作区改了 `gradle.properties` 和 `mods.toml`（`[0.1.1-alpha.5,0.2.0-alpha)`），未提交。JAR SHA-256 `68e4a14b3df5bb21d477b89cc875a9a3af275dd62d508c9b67fe979b36017670`。公开签名对比只有新增（删除或改变 0 行）；AFL 全部 142 个 Mesh 用 alpha.4 / alpha.5 的加载器各加载一遍，原有 141 个结果逐行一致；`compileJava` 通过；**游戏内未运行**。见 [M2.5](mesh_survivor_v1_m25_ingame.md) 第 2 节。

用户 2026-10-15 批准 Vextris 0.1.1-alpha.2（P0 平滑法线 + P1 折角平滑、定位器）作为 AFL 的内部 Mesh 库开发基线，由 Main 接入。只在内部使用：不上传 Maven Central，不发公开 Release，不覆盖已发布的 0.1.0。Vextris 本身冻结，除真实集成 Bug 外不加功能；P2 CPU 蒙皮暂缓，等 D 提出实际需求。0.1.1-alpha.3 是 Vextris 按 AFL 的集成需求（第 4 节）加的面积加权，只做新增。

Vextris 侧文档：`D:/Minecraft Modding/Vextris/docs/release/0.1.1-alpha.4/AFL_INTEGRATION.zh-CN.md`、`VERIFICATION.zh-CN.md`（验收清单在第 7 节；alpha.3、alpha.2 的在 `release/0.1.1-alpha.3/`、`release/0.1.1-alpha.2/`），`docs/smooth-normals.md`、`docs/format.md`（format 3，折角一节）。

## 1. 依赖

| 项 | 现在（分支 `feature/t15-smooth-normals`） | master（`fd620b8e`） | 更早（0.1.0-alpha.1，[迁移记录](vextris_migration_v1.md)） |
|---|---|---|---|
| 坐标 | `io.github.dwom10912:vextris:0.1.1-alpha.4`（alpha.3 之前在这个分支上） | `…:0.1.1-alpha.2` | `com.antaurora.vextris:vextris-forge-1.20.1:0.1.0-alpha.1` |
| 仓库 | `gradle.properties` `vextris_repo=../Vextris/build/internal-repo`（只有 Vextris 工作区写入） | 同左 | `../Vextris/build/repo` |
| `build.gradle` | 仓库名 `Vextris internal`，`includeGroup 'io.github.dwom10912'`；`compileOnly` + `runtimeOnly fg.deobf("io.github.dwom10912:vextris:${vextris_version}")`，不打包进 AFL JAR | 同左 | 组 `com.antaurora.vextris` |
| `mods.toml` | `vextris` `[0.1.1-alpha.4,0.2.0-alpha)`（T15 渲染器用了 `MeshGpuCache`；资源用了 `crease_weighting`，alpha.2 会拒绝） | `[0.1.1-alpha.2,0.2.0-alpha)` | `[0.1.0-alpha.1,0.2.0-alpha)` |
| JAR SHA-256 | `bd8db1b85a3019ef31db254c4d6eafbdc629ef21dc664d15bc81387f07f59704`（alpha.3：`df4d620a…6fc1`） | `b0823abc9ee868435927002a889a06a12a96a2e96b10396b80f1acc5791f5adb` | |

两个版本都与交付记录一致。Java 包名没变（`com.antaurora.vextris.*`），0.1.0 的公开签名全部保留（alpha.2、alpha.3 的 `API_DIFF.txt` 都是删除或改变 0 个），所以 AFL 代码除 T15 渲染器外不用改。`tools/highway-v2-0/verify-loader.mjs`、`tools/highway-v2-m1a/verify.mjs`、`tools/highway-v2-m1b/verify.mjs` 的默认 JAR 跟着改为内部仓库里的当前版本（原来的 `build/libs/...-0.1.0-alpha.1.jar` 已不存在；`VEXTRIS_JAR` 仍可覆盖）。

## 2. 已做的检查

| 检查 | alpha.2（master） | alpha.3（分支） |
|---|---|---|
| `compileJava` | 通过（只有原有的弃用警告） | 通过 |
| 依赖解析 | `io.github.dwom10912:vextris:0.1.1-alpha.2` | `…:0.1.1-alpha.3` |
| GameTest（`runGameTestServer -PaflWithoutShaders`，专用服务器） | Vextris 在 DEDICATED_SERVER 上加载；86 项，16 项失败，与 6bab93f9 的已知失败完全相同 | 同左，失败的还是那 16 项 |
| 离线加载（`node tools/vextris-check/check-meshes.mjs`） | 95 个 Mesh 全部通过，都是 format 2 | 95 个全部通过；T15 七个是 format 3（平滑部件数见第 4 节），其余 88 个 format 2 |

`tools/vextris-check/`：`check-meshes.mjs` 用 Java 单文件启动器运行 `MeshCheck.java`（不跑 Gradle、不启动 Minecraft），用 `gradle.properties` 里的版本和仓库找 JAR，检查角点法线长度都为 1；有拒绝时退出码为 1。`--stats <id> ...` 运行 `SmoothStats.java`：按面积统计每个部件的角点法线偏离面法线多少（看折角平滑会不会把大平面拉歪）。

## 3. 用户实机回归清单

用户 2026-10-15 实机测过 alpha.2，开光影和无光影都 PASS（第 1、3 项；用的是 C3.3a 之后新建的世界，旧世界被世界生成版本锁拦下属正常行为）。第 5 项枪械挂点归 D（正在做枪械和动作），Main 这边不做。按批准的优先级：

1. **旧模型与 API**：枪（Native 枪械 + GeckoLib 枪）、设备物品（盖革计数器、扳手等）、家具与会动的家具、加油站、自动售货机、商业垃圾箱、公路样板（`/afl dev highway_r4`，AFL_R4_ 世界）、T15 皮卡。看有没有缺模型、错位、闪烁，日志里有没有 Vextris 拒绝。——alpha.2 PASS。
2. **T15 平滑法线**（alpha.3，未验收）：在 Aureveil、只开 Embeddium、无光影三种环境下看 T15，重点看尾门、车厢侧板（应当平整、压筋清楚）、发动机盖和轮胎（应当圆顺，没有面片）、开关车门 / 发动机盖时的铰接部件，以及磨损层（`wear` 高时漆面磨穿的地方要和漆面贴合，不闪烁）。
3. 光影环境：Embeddium + Oculus + Aureveil、只开 Embeddium、无光影，各看一遍第 1 项。——alpha.2 PASS。Vextris 只在 Embeddium 0.3.31 + Oculus 1.8.0 + Aureveil 0.2.0-alpha.25 上实测过。
4. 车辆渲染性能：放几辆 T15，JFR 或 F3 帧时间对比平滑前后（Vextris 实测平滑部件开光影 +22% 帧时间）。
5. Native 枪械与 GeckoLib 动画挂点（`GeckoMeshRenderer.applyLocator`：枪口火焰、抛壳、瞄具）：D 负责。

## 4. T15 平滑法线

**渲染器**（已在 master）：`PickupTruckRenderer` 用 0.1.1 的 `renderParts(parts, PartFilter, Topology, …)`。没开光影包时所有部件进同一个 QUADS 缓冲（平滑部件保留角点法线）；开了光影包时（`MeshRendering.smoothTopology()` 为 TRIANGLES）每个 pass 先画平面部件（QUADS），再把平滑部件画进 Vextris 的 TRIANGLES 孪生类型（`cutoutType` / `translucentType(texture, topology)`），因为 Oculus 会按面覆盖 QUADS 的法线。磨损层与漆面层对同一部件用同一拓扑和同一对角线，深度相同，叠加规则不变。

**资源**（分支，alpha.3）：`tools/build-pickup-t15.mjs` 的 `CREASE = true`。七个 sidecar 写成 format 3：根上 `crease_angle` 50° 和 `crease_weighting` `"area"`，按材质覆盖折角（玻璃 / 镜子 / 表盘 0°，灯罩 30°，轮胎 60°，`tools/pickup-t15/common.mjs` 的 `SMOOTHING`），不写法线，法线由 Vextris 加载时算。贴图、geo、bbmodel 没变。平滑部件数：`pickup_t15` 25/29、`_front` 10/11、`_wheel` 5/5、`_cab` 24/28、`_seat` 6/7、`_engine` 14/15、`_chassis` 6/6（玻璃、镜子等折角 0° 的部件保持平面）。

**为什么用面积加权**（2026-10-15 测量）：默认的 `angle` 按角点夹角加权，大块平面和相邻的细倒角条权重一样，面板角点被拉偏 22–25°（`--stats`：`pickup_t15` 的 `body_paint` 有 78 个面积超过 0.01 m² 的面偏离超过 15°，其中车厢侧板一块 0.22 m² 的面四角都是 25°；尾门、车门同样）。预览里尾门压筋被抹糊，车厢侧板出现一整片明暗渐变。用户决定请 Vextris 加面积加权，AFL 不自己写法线（共享 Mesh 能力归 Vextris）。Vextris 2026-10-16 交付 0.1.1-alpha.3（提交 e75e4d8）。

**alpha.3 的结果**（Vextris 自己的加载器，`--stats`）：`pickup_t15` `body_paint` 偏离 >8° / >15° / >25° 的面积占比 11.3% / 5.8% / 1.9%（angle 时 35.2% / 23.4% / 13.0%），面积超过 0.01 m² 且偏离超过 15° 的面从 72 个降到 14 个，剩下的都是 0.01–0.017 m² 的圆角过渡面（15–22°），大面板没有了。和预览里 `crease.js` 的面积加权模拟一致。

**预览**：`design/vehicles/first_pickup_v0/source/pickup_mesh_preview.html`（`normals: 'crease'` 按文件里的 `crease_weighting` 重算，`crease.js` 按 Vextris 文档的规则；`'area'` 强制面积加权；`gloss` 加 Phong 高光模拟光影）。对比图：`design/vehicles/first_pickup_v0/sheets/v3n_final_area.png`（平面 vs alpha.3 面积加权：整车、尾门、车厢侧板、车头），角度加权的问题图 `v3n_close_*.png`、`v3n_compare_*.png`。这些只是离线预览，游戏里的样子以用户实机为准。

## 5. 未做

- T15 平滑法线实机验收（第 3 节第 2 项）和车辆性能（第 4 项）。
- 定位器（T15 座位、车灯）还没有用到；`rig.json` 的 `anchors` 以后换成 geo / sidecar 定位器。
