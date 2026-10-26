# AFL Mesh 幸存者 V1：Vextris alpha.5 升级 + M2.5 游戏内静态渲染测试

2026-10-25，Main。按用户“AFL Mesh 幸存者 V1：M2 验收与下一阶段调整”。前一阶段见 [蒙皮 M1 / M2](mesh_survivor_v1_skinning_m1_m2.md)。

**没有提交、没有推送、没有合并；没有启动客户端；没有改 Vextris 仓库；没有改 P9-01 第一人称动画；没有改玩家移动速度。**

| 项 | 状态 |
|---|---|
| M2 男性蒙皮 | 用户 2026-10-25 **暂时接受**（离线结果）；衣服局部穿模继续记录，不要求现在精修 |
| Vextris 0.1.1-alpha.4 → alpha.5 | 用户 2026-10-25 批准。API 对比、现有资源加载对比、编译都已通过（第 2 节）；**游戏内未运行** |
| M2.5 资源导出 + 离线检查 | 完成，离线检查通过（第 3、4 节） |
| M2.5 开发渲染测试类 | 已写，`compileJava` 通过（第 5 节）；**游戏内未运行**，第 6 节清单全部“未实机” |
| M3 Idle / Walk / Run / Sprint | 未开始（第 7 节写了按现有速度的计划） |
| 女性基础人体 | 未开始，列为 M3 批量动画前的兼容任务（第 8 节） |
| 皮肤 PBR、指甲、脸部精修 | 用户 2026-10-25 定为延期 |

## 1. 文件

| 文件 | 内容 |
|---|---|
| `gradle.properties` | `vextris_version=0.1.1-alpha.5` |
| `src/main/resources/META-INF/mods.toml` | `vextris` 版本范围 `[0.1.1-alpha.5,0.2.0-alpha)` |
| `design/characters/survivor_male_v1/source/export_game_male_v1.py` | 从 `survivor_male_v1.blend` 导出游戏资源（新） |
| `assets/apocalypse_firstlight/meshes/dev/survivor_male_v1.vxmesh.json` | format 4 蒙皮 Mesh，独立资源 ID `apocalypse_firstlight:dev/survivor_male_v1`（新，1,355 KB） |
| `assets/apocalypse_firstlight/textures/dev/survivor_male_v1.png` | 1024 × 1280 图集（8 个材质拼一张，新） |
| `assets/apocalypse_firstlight/skins/dev/survivor_male_v1.poses.json` | 54 根骨骼在 9 个静态测试姿势下的变形矩阵 + P9-01 握持矩阵（新） |
| `src/main/java/com/antaurora/apofirstlight/client/dev/MeshBodyTpTest.java` | 开发测试开关和第三人称渲染（新） |

已 PASS 的资源都没动：H1-P1 的 `survivor_base_h1p1.*`、P9-01 的 geo / animation / 第一人称片段、`MeshBodyFpTest`。

## 2. Vextris alpha.5 升级检查

### 2.1 构建产物

- 来源：Vextris 内部仓库 `D:/Minecraft Modding/Vextris/build/internal-repo/io/github/dwom10912/vextris/0.1.1-alpha.5/vextris-0.1.1-alpha.5.jar`
- SHA-256：`68e4a14b3df5bb21d477b89cc875a9a3af275dd62d508c9b67fe979b36017670`
- Gradle 解析结果（`dependencies --configuration compileClasspath`）：`io.github.dwom10912:vextris:{strictly 0.1.1-alpha.5_mapped_official_1.20.1}`。
- 运行时同一个坐标（`build.gradle` 的 `runtimeOnly fg.deobf(...)`），`run/mods` 里没有旧的 Vextris jar。

### 2.2 API 兼容

- 方法：用 `javap -public` 导出 alpha.4、alpha.5 全部公开签名，逐行对比。
- 结果：删除或改变 **0** 行，全部是新增：
  - `SkinPose`；
  - `SkinnedMeshPart`；
  - `MeshModel.skins()`、`skins(layer)`、`hasSkins()`；
  - `MeshModel.Builder.skin(...)`；
  - `MeshRendering.renderSkins(...)`；
  - `MeshRendering.renderPasses(..., SkinPose, ...)`；
  - `MeshRenderer.captureSkinPose`、`renderSkins`；
  - GeckoLib 桥的 `captureBone`、`captureSkinPose`；
  - `Metrics` 的三个计数字段；
  - `MeshPart.source()`。
- 源码对比：没有蒙皮部件的模型，绘制路径和 alpha.4 一样（`render` 里只有 `hasSkins()` 为真才多走一步）。

### 2.3 现有资源加载（离线，Vextris 自己的 `MeshLoader`）

- 方法：AFL 全部 142 个 Mesh（`meshes/**/*.vxmesh.json` 20 个、`*.aflmesh.json` 122 个，配对的 `geo/*.geo.json` 一起读），分别用 alpha.4 和 alpha.5 的加载器加载，比较每个模型的层级类型、format、部件数、三角形数、骨骼数。
- 结果：
  - alpha.5：142 / 142 加载成功；
  - alpha.4：141 / 142，没加载的是新的 `dev/survivor_male_v1`（format 4，alpha.4 不认，属正常）；
  - 原有 141 个模型两个版本的结果**逐行一致**。
- 程序：会话临时目录 `scratchpad/vx/LoadAll.java`（不进仓库）。

### 2.4 编译

- 2026-10-25 运行 `gradlew compileJava`（确认没有客户端在跑，只有 Gradle 守护进程）：**BUILD SUCCESSFUL**。
- 编译范围：AFL 全部现有用到 Vextris 的代码，和新的 `MeshBodyTpTest`，`src/dev` 一起编。
- 警告：只有原有的弃用警告，新类没有警告。

**游戏内还没运行**。alpha.5 下旧模型的实机回归（枪、T15、家具、隧道设施）要用户进游戏看，第 6 节第 1 项。

## 3. 导出（Blender → Vextris format 4）

在 `survivor_male_v1.blend` 里运行（Blender 5.2）：

```python
import sys, importlib
sys.path.insert(0, r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_male_v1/source")
import export_game_male_v1 as X; importlib.reload(X); X.export()
```

做法：
- 坐标：Blender Z 向上、米 → 渲染坐标 (x, z, −y) 方块，1 m = 1 格；角色在模型坐标里面朝 +z。
- 骨骼：`bones` 块写 54 根形变骨骼。
  - 轴点是静止姿势的骨头根部；
  - 父骨骼是最近的形变祖先，4 根 IK 辅助骨不导出；
  - 旋转为 0，所以逆绑定矩阵是单位阵。
- 网格：
  - 8 个对象各成一个蒙皮部件；
  - 网格按批准外观（`stylise` 形态键 = 1）求值，关掉骨架和遮罩修改器，所以顶点顺序和原网格一致；
  - 被遮罩隐藏的顶点所在的三角形不导出（衣服下面的身体）。
- 权重：
  - 每顶点取最大 4 个形变骨骼；
  - 归一化后保留 4 位小数，舍入误差加到第一个权重上（Vextris 要求和为 1，误差 ≤ 1e-3）。
- 法线：
  - 用 Blender 角点法线；
  - 和面法线点积 < 0.02 的角点改用面法线：T 恤 21 个、夹克 18 个、牛仔裤 6 个，都是加厚外壳边缘的翻转角点；
  - 原因：Vextris 会拒绝背向面的角点法线（第一次导出 T 恤就被拒，离线检查当场发现）。
- 贴图：8 张基础色拼一张 1024 × 1280 图集；头部区域左右各放一份，UV 往区域内收半个像素。
- 姿势文件：每个测试姿势把每根骨骼的模型空间变形 D = C (W_pose W_rest⁻¹) C⁻¹ 按列主序写出（C 是 Blender → 渲染的坐标变换）；P9-01 握持沿用 H1-P1 的 props 矩阵（`hand_r` 上）。

## 4. 离线检查（Vextris alpha.5 自己的加载器 + 它的蒙皮公式）

程序 `scratchpad/vx/MaleCheck.java`：用 alpha.5 的 `MeshLoader` 加载导出的 Mesh，再用姿势文件里的矩阵 × 逆绑定，按 Vextris 的线性混合蒙皮算采样顶点，和 Blender 里同一姿势的顶点位置比。

| 项 | 结果 |
|---|---|
| 加载 | 通过：format 4，层级 SIDECAR，54 根骨骼，8 个蒙皮部件 |
| 三角形 | 7,813（同 M2 遮罩后的可见数） |
| 顶点槽 | 12,937（上限 65,536） |
| 贴图 | 1024 × 1280 |
| 绑定位置和文件比 | 一致 |
| 逆绑定和单位阵比 | 一致 |
| 9 个姿势（0、A–H）和 Blender 比 | 最大偏差 ≤ 0.027 mm |

第一次导出时头和头发即使在静止姿势也差 10–20 mm：读的是 Basis 网格，没用批准外观的 `stylise` 形态键。改成按形态键求值后全部对上。

**这只说明资源能被 Vextris 加载、蒙皮数学和 Blender 一致，不代表游戏里看起来对。**

## 5. 开发测试开关（M2.5）

`MeshBodyTpTest`：
- 只在开发客户端注册（`!FMLEnvironment.production`）；
- 名单默认空，**不会对所有玩家生效**；
- 不保存，退出游戏就没了；
- 不发网络包。

命令：

| 命令 | 作用 |
|---|---|
| `/afl dev meshbody` | 状态：Mesh 是否加载、三角形 / 骨骼数、名单、姿势 |
| `/afl dev meshbody self` | 自己加进名单 |
| `/afl dev meshbody add <玩家>` / `remove <玩家>` | 按名字加 / 删别的玩家（只影响本客户端怎么画） |
| `/afl dev meshbody pose auto` | 默认：主手拿 P9-01 用 H 双手握枪，其他用 A 站立；头跟视线转（左右 ±70°、上下 ±60°） |
| `/afl dev meshbody pose <名称>` | 固定成某个测试姿势（0_rest、A_stand … H_p9_two_hand），头不跟视线 |
| `/afl dev meshbody crowd <0–16>` | 在自己身边 1.4 格的圈上多画 n 个身体（各自单独蒙皮），用来测 1 / 4 / 8 个的开销 |
| `/afl dev meshbody stats` | 最近 2 秒实测：每个身体、每帧合计和最差一帧的 CPU 蒙皮 + 顶点提交时间，主通道和阴影通道分开 |
| `/afl dev meshbody off` | 清空名单和额外身体，全部回到原版 |

怎么画：
- 名单里的玩家，在 `RenderPlayerEvent.Pre`（LOW 优先级，已取消的不管）里取消原版渲染，改画 Mesh 身体；
- 身体先按身体朝向转（`Ry(-bodyYaw)`），每根骨骼 = 实体矩阵 × 姿势变形（头和它下面的骨骼再乘视线旋转），交给 `SkinPose.set` 和 `MeshRendering.renderSkins`；
- 光照用实体光照，受伤闪红用原版的 overlay；
- P9-01：主手拿着时按 H1-P1 的握持矩阵画在 `hand_r` 上，换弹用的两个弹匣隐藏（同 `MeshBodyFpTest`）。

回退和兼容：
- Mesh、贴图或姿势文件没加载 → 不取消，照常画原版，日志警告一次；
- 自己在第一人称时不接管：`FirstPersonBody` 的原版第一人称身体和已 PASS 的 P9 手部通道照旧，避免画两个身体；
- 光影阴影通道里也画（Vextris 蒙皮部件的规则：CUTOUT 进阴影，TRANSLUCENT 不进；这个模型没有半透明部件）；
- F3+T 重载 Mesh 后姿势文件跟着重读。

已知限制（测试版，不是正式替换）：
- 只有静态姿势，走路时整个人滑着走，没有行走循环（M3 做）；
- 名牌、手里的其他物品、盔甲层、披风、骑乘缩放都不画；
- 多人时每个客户端都要自己执行 `add`，名单不同步。

## 6. 游戏内验证清单（全部未实机）

用户进开发客户端，按第 5 节命令测。**以下没有一项是 PASS，等用户实机结果。**

| # | 项 | 怎么看 | 状态 |
|---|---|---|---|
| 1 | alpha.5 下旧模型回归 | P9-01 / BR-51 等枪、T15、家具、隧道设施，日志里有没有 Vextris 拒绝 | 未实机 |
| 2 | 骨骼加载 | `/afl dev meshbody` 显示 8 个蒙皮部件、7813 个三角形、54 根骨骼 | 未实机 |
| 3 | 蒙皮变形 | `pose` 依次切 0_rest、A–H，和 `renders/m2/m2_poses_sheet.png` 对照 | 未实机 |
| 4 | 材质与服装 | 图集颜色对、头发 / 脸 / 衣服没错位，衣服下没有露出身体 | 未实机 |
| 5 | 无光影 | 关光影包看 F5 | 未实机 |
| 6 | Aureveil | 开 Aureveil 看 F5，身体有阴影、没有黑块或闪烁 | 用户 2026-10-25 实机：正常（alpha.75） |
| 7 | Embeddium | Embeddium + Oculus 下看 | 未实机 |
| 8 | 原版回退 | `off` 后马上回原版；名单外的玩家始终是原版 | 未实机 |
| 9 | 第一人称 | 自己在名单里切回第一人称，没有第二个身体，P9 第一人称照旧 | 未实机 |
| 10 | P9-01 握持 | 主手拿 P9-01，枪在右手里、左手托着 | 未实机 |
| 11 | 视线 | 左右、上下看，头跟着转，脖子没有断开 | 未实机 |
| 12 | 多人 | 至少 2 个客户端，各自 `add` 对方，互相看见 | 未实机（单人 F5 正常不能当成多人 PASS） |
| 13 | 性能 | `crowd 0 / 3 / 7`（1 / 4 / 8 个身体），开 / 关 Aureveil 各记一次 `stats` 和 F3 帧率 | 未实机，没有任何实测数字 |

### 6.1 第一次实机（用户 2026-10-25 截图 + `run/logs/latest.log`，用户未给 PASS）

- 日志：`Vextris 0.1.1-alpha.5 on CLIENT`，`Meshes: 142 loaded, 0 rejected`。`VextrisNormalMat` 的 5 条着色器警告 alpha.4 的日志里也有，不是这次升级带来的。
- 截图里看到的：
  - A 站立、D 抬肘、E 下蹲、H 双手握 P9-01 都正常显示，图集颜色、头发、衣服位置对，没看到衣服下露出身体；
  - P9-01 在双手里，旧模型（P9-01、房子、树）照常显示；
  - `off` 的提示出现。
- 性能（无光影，`crowd 7` 共 8 个身体，`stats` 实测）：
  - 每个身体 0.531 ms；
  - 每帧 8 个合计 4.248 ms，最差一帧 5.165 ms；
  - 这时 128 FPS。
  - 结论：8 个身体每帧 4 ms 多，太贵，M4 前必须降开销。思路：主通道和阴影通道共用一次蒙皮结果、按距离 LOD、视野外不蒙皮，或请 Vextris 提供 GPU 蒙皮；方案另定。
- **这次没测到光影**：Oculus 配置指向 `Aureveil-Shaders-0.2.0-alpha.71.zip`，`shaderpacks` 里只有 alpha.75，日志 `Pack ... is not valid`，回退成无光影。要在光影菜单选 alpha.75 再测第 6 项和阴影通道开销。
- 还没测：
  - 第 6 项 Aureveil；
  - 第 9 项第一人称；
  - 第 11 项视线；
  - 第 12 项两客户端；
  - 第 13 项 1 / 4 个身体和光影下的开销。
- **Aureveil（第 6 项）：用户 2026-10-25 实机说“光影下也正常”**（日志 01:08 `Using shaderpack: Aureveil-Shaders-0.2.0-alpha.75.zip`）。截图里地上有身体的投影，说明阴影通道也画了；头跟视线低头、侧转也在截图里看到（第 11 项，用户没单独判定）。光影下的 `stats` 开销还没有数字。
- 群体截图右边那个身体的腿上有方块状绿色遮挡，看着是前面的草，待确认。

### 6.2 身高（用户 2026-10-25：比原版小）

量出来的数（Mesh 文件）：

| | AFL Mesh 男性 | 原版玩家模型 |
|---|---|---|
| 总高 | 1.80 格（头顶 1.79，头发 1.80） | 1.875 格（32 像素 × 0.9375） |
| 头宽 | 约 0.19 | 0.47 |
| 碰撞箱 / 第一人称眼高 | 1.8 / 1.62（原版，没改） | 同左 |

- 高度只差 7.5 cm（4%）。看着小，主要是因为原版的头宽是我们的 2.5 倍，身体也是方块，体积大得多。
- AFL 是 1 格 = 1 m，1.80 m 已经是北美成年男性偏高的身高。
- 我们的眼睛大约在 1.67，已经比第一人称相机（1.62）高 5 cm 左右；再放大，第一人称和第三人称的眼高差得更多。
- 建议保持 1.80，不放大。等用户决定。

## 7. M3 计划（未开始）：按现有速度做动画

用户 2026-10-25 定：玩家移动速度暂时不改，按现有实际速度规划 Idle、Walk、Run、Sprint，优先避免滑步和速度不匹配。现有速度见 [M1 / M2 第 7.1 节](mesh_survivor_v1_skinning_m1_m2.md)：

- 按 W：约 4.32 m/s；
- 疾跑：约 5.61 m/s；
- 潜行：约 1.30 m/s；
- AFL 只会降低这些速度（负重、受伤、寒冷、耐力）。

计划：
- 每段片段量出一个参考速度：
  - Walk 约 1.4 m/s；
  - Run 约 4.32 m/s，这是平时按 W 看到的样子；
  - Sprint 约 5.61 m/s；
  - 潜行用 Walk 的低姿态版本，或单独一段，M3 时定。
- 播放速度 = 实际水平速度 ÷ 参考速度；相邻两段按速度交叉混合，四段共用一个归一化步态相位，切换时脚步对得上；
- 每段片段导出前检查一个循环里落地脚的水平位移：脚着地期间相对地面不动，偏差 ≤ 2 cm；
- 片段用新的独立资源 ID，不碰 P9-01 第一人称片段；
- 片段帧加进遮罩检查（衣服下的身体不露出来）。

## 8. 女性基础人体（M3 批量动画前的兼容任务，未开始）

- 先做女性基础网格和蒙皮，尽量共用这 54 根骨骼（名字、层级不变）和同一套动画；
- 不做精细女性面部材质；
- 要在 M3 批量做动画前确认：同一套片段放到女性体型上，手、脚、头的位置是否可用，需要的话只在网格和权重上调，不加骨骼；
- 资源单独 ID（如 `dev/survivor_female_v1`），测试开关以后加一个按玩家选身体的参数。

## 9. 延期

- 皮肤 LabPBR（`_s` / `_n`）、指甲、脸部精修：用户 2026-10-25 定为延期；
- 衣服局部穿模：继续记录在 [M1 / M2 第 5.3 节](mesh_survivor_v1_skinning_m1_m2.md)，不要求现在精修。
