# Vextris 0.1.1-alpha.2 接入（AFL 内部基线）

状态：**分支 `feature/vextris-0.1.1`，2026-10-15。依赖已切换；离线加载检查与 GameTest 通过；用户 2026-10-15 客户端实机回归 PASS（第 3 节）。** T15 的平滑法线已接好渲染路径，但资源暂时保持平面法线，等 Vextris 的面积加权（第 4 节）。

用户 2026-10-15 批准 Vextris 0.1.1-alpha.2（P0 平滑法线 + P1 折角平滑、定位器）作为 AFL 的内部 Mesh 库开发基线，由 Main 接入。只在内部使用：不上传 Maven Central，不发公开 Release，不覆盖已发布的 0.1.0。Vextris 本身冻结，除真实集成 Bug 外不加功能；P2 CPU 蒙皮暂缓，等 D 提出实际需求。

Vextris 侧文档：`D:/Minecraft Modding/Vextris/docs/release/0.1.1-alpha.2/AFL_INTEGRATION.zh-CN.md`（坐标、API、限制），`docs/smooth-normals.md`、`docs/format.md`（format 3）。

## 1. 依赖

| 项 | 现在 | 之前（0.1.0-alpha.1，[迁移记录](vextris_migration_v1.md)） |
|---|---|---|
| 坐标 | `io.github.dwom10912:vextris:0.1.1-alpha.2` | `com.antaurora.vextris:vextris-forge-1.20.1:0.1.0-alpha.1` |
| 仓库 | `gradle.properties` `vextris_repo=../Vextris/build/internal-repo`（只有 Vextris 工作区写入） | `../Vextris/build/repo` |
| `build.gradle` | 仓库名 `Vextris internal`，`includeGroup 'io.github.dwom10912'`；`compileOnly` + `runtimeOnly fg.deobf("io.github.dwom10912:vextris:${vextris_version}")`，不打包进 AFL JAR | 组 `com.antaurora.vextris` |
| `mods.toml` | `vextris` `[0.1.1-alpha.2,0.2.0-alpha)`（AFL 开始用 0.1.1 API：`PickupTruckRenderer`） | `[0.1.0-alpha.1,0.2.0-alpha)` |
| JAR SHA-256 | `b0823abc9ee868435927002a889a06a12a96a2e96b10396b80f1acc5791f5adb`（与交付记录一致） | |

Java 包名没变（`com.antaurora.vextris.*`），0.1.0 的公开签名全部保留（Vextris `API_DIFF.txt`：删除或改变 0 个），所以 AFL 代码除 T15 渲染器外不用改。`tools/highway-v2-0/verify-loader.mjs`、`tools/highway-v2-m1a/verify.mjs`、`tools/highway-v2-m1b/verify.mjs` 的默认 JAR 改为内部仓库里的 0.1.1-alpha.2（原来的 `build/libs/...-0.1.0-alpha.1.jar` 已不存在；`VEXTRIS_JAR` 仍可覆盖）。

## 2. 已做的检查

| 检查 | 结果 |
|---|---|
| `compileJava` | 通过（只有原有的弃用警告） |
| 依赖解析 | compile / runtime classpath 都是 `io.github.dwom10912:vextris:0.1.1-alpha.2` |
| GameTest（`runGameTestServer -PaflWithoutShaders`，专用服务器） | Vextris 0.1.1-alpha.2 在 DEDICATED_SERVER 上加载；86 项，16 项失败，与合并前 master（6bab93f9）的已知失败完全相同，没有新增 |
| 离线加载（`node tools/vextris-check/check-meshes.mjs`） | 用 0.1.1-alpha.2 JAR 里真正的 `MeshLoader` 加载 AFL 全部 95 个 Mesh（`.aflmesh.json` + geo）：全部通过，都是 format 2，角点法线长度都为 1 |

`tools/vextris-check/`：`check-meshes.mjs` 用 Java 单文件启动器运行 `MeshCheck.java`（不跑 Gradle、不启动 Minecraft），JAR 路径从 `gradle.properties` 读；有拒绝时退出码为 1。`--stats <id> ...` 运行 `SmoothStats.java`：按面积统计每个部件的角点法线偏离面法线多少（看折角平滑会不会把大平面拉歪）。

## 3. 用户实机回归清单

用户 2026-10-15 实机测过，开光影和无光影都 PASS（第 1、3 项；用的是 C3.3a 之后新建的世界，旧世界被世界生成版本锁拦下属正常行为）。第 5 项枪械挂点归 D（正在做枪械和动作），Main 这边不做。按批准的优先级：

1. **旧模型与 API**（无光影）：枪（Native 枪械 + GeckoLib 枪）、设备物品（盖革计数器、扳手等）、家具与会动的家具、加油站、自动售货机、商业垃圾箱、公路样板（`/afl dev highway_r4`，AFL_R4_ 世界）、T15 皮卡（`/afl dev` 的皮卡命令）。看有没有缺模型、错位、闪烁，日志里有没有 Vextris 拒绝。
2. T15 平滑法线：等第 4 节。
3. 光影环境：Embeddium + Oculus + Aureveil、只开 Embeddium、无光影，各看一遍第 1 项。Vextris 只在 Embeddium 0.3.31 + Oculus 1.8.0 + Aureveil 0.2.0-alpha.25 上实测过。
4. 车辆渲染性能：放几辆 T15，JFR 或 F3 帧时间对比（平滑部件在 Vextris 实测里有 +22% 帧时间，等第 4 节后再测才有意义）。
5. 之后：Native 枪械与 GeckoLib 动画挂点（`GeckoMeshRenderer.applyLocator`：枪口火焰、抛壳、瞄具）。

## 4. T15 平滑法线与面积加权需求

**已接好**：`PickupTruckRenderer` 改用 0.1.1 的 `renderParts(parts, PartFilter, Topology, …)`。没开光影包时所有部件进同一个 QUADS 缓冲（平滑部件保留角点法线）；开了光影包时（`MeshRendering.smoothTopology()` 为 TRIANGLES）每个 pass 先画平面部件（QUADS），再把平滑部件画进 Vextris 的 TRIANGLES 孪生类型（`cutoutType` / `translucentType(texture, topology)`），因为 Oculus 会按面覆盖 QUADS 的法线。磨损层与漆面层对同一部件用同一拓扑和同一对角线，深度相同，叠加规则不变。资源没有平滑部件时（现在）只走第一段，和以前逐位相同。

**生成器**：`tools/build-pickup-t15.mjs` 的 `CREASE` 开关。打开时 sidecar 写成 format 3：根上 `crease_angle` 50°，按材质覆盖（玻璃 / 镜子 / 表盘 0°，灯罩 30°，轮胎 60°，`tools/pickup-t15/common.mjs` 的 `SMOOTHING`），不写法线。打开后的七个 Mesh 用离线检查加载全部通过，平滑部件数：`pickup_t15` 25/29、`_front` 10/11、`_wheel` 5/5、`_cab` 24/28、`_seat` 6/7、`_engine` 14/15、`_chassis` 6/6。**现在关着**，原因如下。

**问题**（2026-10-15 测量）：Vextris 0.1.1-alpha.2 按角点夹角加权平均面法线。大块平面和相邻的细倒角条权重一样，面板角点被拉偏 22–25°（`--stats`：`pickup_t15` 的 `body_paint` 有 78 个面积超过 0.01 m² 的面偏离超过 15°，其中车厢侧板一块 0.22 m² 的面四角都是 25°；尾门、车门同样）。离线预览里（`design/vehicles/first_pickup_v0/source/pickup_mesh_preview.html`，`crease.js` 按 Vextris 文档的规则重算，统计与 Java 加载器一致）：尾门压筋被抹糊，车厢侧板出现一整片明暗渐变，加高光后更明显。按面积加权时面板保持平整，倒角和轮胎、发动机盖照样平滑（同一统计降到 18 个面）。对比图：`design/vehicles/first_pickup_v0/sheets/v3n_close_rearClose.png`、`v3n_close_bedSide.png`、`v3n_close_faceClose.png`、`v3n_close_doorSide.png`、`v3n_compare_*.png`。

**决定（用户 2026-10-15）**：请 Vextris 加面积加权，作为集成中发现的问题处理；在它交付前 T15 保持平面法线；AFL 不自己写法线（共享 Mesh 能力归 Vextris）。

**给 Vextris 的需求**（2026-10-15 已发给 Vextris A；键名由 Vextris 定）：

- format 3 加一个可选的权重方式，根与 part 上都可写，例如 `"crease_weighting": "angle" | "area"`，缺省为 `angle`（与 0.1.1-alpha.2 逐位相同）。
- `area`：经软边可达的每个面按面积加权（Blender Weighted Normal 的 Face Area 模式）；其他规则（按位置焊接、软边判定、`hard_edges`、显式法线优先、朝向校验、平面判定）不变。
- `MeshModel.Builder` 的 `CreaseSmoothing` 对应加一个选项。
- 验收：AFL 的 `SmoothStats` 对 T15 的结果与本节的面积加权数字一致；T15 在 Aureveil 下实机看尾门和车厢侧板。

交付后：`CREASE = true`，sidecar 加上权重键，重新生成，`check-meshes.mjs` 检查，用户在三种环境下实机看。

## 5. 未做

- T15 平滑法线、车辆性能、枪械挂点（第 3 节第 2、4、5 项，等第 4 节）。
- 定位器（T15 座位、车灯；枪口）还没有用到；`rig.json` 的 `anchors` 以后换成 geo / sidecar 定位器。
