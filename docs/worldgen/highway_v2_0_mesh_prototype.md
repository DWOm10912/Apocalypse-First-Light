# Highway V2-0：Pure Mesh离线样段与能力证明

日期：2026-10-07。状态：**离线导出、数据校验、现行Java Loader加载PASS；客户端/世界/碰撞/兼容/性能未测。**

后续状态：[M1-A隔离开发测试场](highway_v2_m1a_mesh_sandbox.md)已复用本页原型，新增dev资源、实际16格裁剪、世界渲染及有限碰撞；编译/离线检查通过，M1-A 已集成 Master，基础渲染/坡面行走已获用户确认，生命周期与光影兼容仍待验收。下文“此次/本轮/没有Java改动”等均描述V2-0提交范围，不表示M1-A尚未实现。原始离线产物保持不变。

[完整源码审查](highway_v2_0_architecture_audit.md) / [现实断面契约与M1–M5](highway_v2_0_design_contract.md)。当前基线`5dc68bbc`，此次不修改生产系统。实现只在`tools/highway-v2-0/`和可编辑源`src/main/blockbench/dev/highway_v2_0/`，不在任何main/dev resources目录，不触发模型、方块、worldgen或资源自动注册。

## 1. 复现命令

从仓库根目录，使用现有Node（本轮v24.19.0）：

```powershell
node tools/highway-v2-0/generate.mjs
node tools/highway-v2-0/generate.mjs --check
node tools/highway-v2-0/verify.mjs
node tools/highway-v2-0/verify.mjs --check
# 可选单独验证；JDK17+与本机已有Gson缓存，不下载依赖。
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
node tools/highway-v2-0/verify-loader.mjs
```

Java路径应改成本机已有JDK路径；Gson默认在用户`.gradle/caches`，也可通过`GRADLE_USER_HOME`指向已有缓存。Java检查复用原校验器的JShell方式，读取**未修改**的AflMeshPart/Model/Loader，在临时目录装载副本；本轮为避免沙箱JDWP回环端口限制使用local execution和显式Gson classpath。无需Gradle、Forge、游戏或网络。不把Loader通过当客户端通过。

单资产也能直接交给原导出CLI验证，无新的输出格式：

```powershell
node tools/export-afl-mesh.mjs --input src/main/blockbench/dev/highway_v2_0/highway_v2_0_demo_000.bbmodel --geometry tools/highway-v2-0/demo/highway_v2_0_demo_000.geo.json --output tools/highway-v2-0/demo/highway_v2_0_demo_000.aflmesh.json --format v2 --compact --check
```

生成器导入原`convert/serializeCompact/meshCounts`和原PNG编码器，只添加道路参数→Free Model几何配方。所有产物来自脚本，不手工修`.aflmesh.json`或`.bbmodel`。`manifest.json`保存输入和18件生成产物集合中除manifest自身外各文件SHA-256；`validation.json`与`loader-validation.json`是另行校验产物。

两个隔离目录内的`.gitattributes`固定文本LF，避免Windows checkout自动改成CRLF导致字节复现失败；PNG保持二进制。输入脚本hash先统一UTF-8/LF，产物hash按原始字节。复现基准为上述Node版本，不保证不同zlib/Node版本的PNG压缩字节相同。

## 2. 产物路径与预览

| 路径 | 内容 |
|---|---|
| `tools/highway-v2-0/recipe.json` | 唯一参数入口，版本highway-v2-0-demo-1 |
| `tools/highway-v2-0/geometry.mjs` | 连续中心线、坡度、断面、s/u面、离线normal计算 |
| `tools/highway-v2-0/generate.mjs` | 参数几何→Free Model→现有exporter；四窗口与数据、纹理、预览 |
| `tools/highway-v2-0/verify.mjs` | 两次重建字节一致、当前文件一致、顶点/UV/面积/绕序、接缝、曲率/纵坡 |
| `tools/highway-v2-0/verify-loader.mjs` | 仅本原型的现行Java Loader加载及面数/法线检查 |
| `src/main/blockbench/dev/highway_v2_0/highway_v2_0_demo_{000,064,128,192}.bbmodel` | 四件生成的可编辑Free Model来源；未在Blockbench GUI验收 |
| `tools/highway-v2-0/demo/highway_v2_0_demo_{000,064,128,192}.aflmesh.json` | 现有format_version=2，非新增格式 |
| 同目录同stem `.geo.json` | 一个空cube骨骼road、纹理尺寸128×128；给现有Loader配对 |
| `demo/placeholder.png` | 开发占位atlas，asphalt/shoulder/median/white/yellow五分组，没有PBR |
| `demo/centerline.csv`、`metadata.json` | 257中心线样本；四模块origin/bounds/extents、统计、seam节点、surface normal |
| `demo/preview.html` | 无依赖本地Canvas预览，导出后的顶点；拖动旋转，俯视/纵断面、里程滑块 |
| `demo/preview.png` | 静态几何图：上方平面含少量高差投影、下方放大纵断面；青竖线为x=64 |
| `demo/manifest.json`、`validation.json`、`loader-validation.json` | 重复性、离线指标、真实Loader证据 |

直接打开[交互预览](../../tools/highway-v2-0/demo/preview.html)或[PNG](../../tools/highway-v2-0/demo/preview.png)。本轮已读取PNG确认四车道、分离带、弯曲、接缝标记与纵坡显示。Canvas/PNG用材质颜色而非Minecraft纹理采样，不能验证PBR/UV视觉/光照；细线像素化不是mesh裂口证据。

## 3. 路线、断面、UV参数

- 总里程256m，1m采样，四个64m局部窗口，初始沿+X，Y向上，横向+Z。
- s=0..64直线；64..96用smoothstep曲率过渡到1/600m；96..192恒曲率；192..224退回零；224..256末端直线。heading由曲率解析积分，XZ由固定起点Simpson积分（步长≤0.25m），不从上一模块端点累积。最大转角约12.223°，无硬折；600m仅几何示意，不代表指定速度设计合格。
- 纵坡96..136线性增至5%（抛物线竖曲线），136..176恒5%，176..216降到0，最终基准高4m。136..156恰好升1m/20m。过渡长度40m、A=5%，K≈26.25ft/%，不足以直接当50–70mph视距验收，故只做工具能力示意。
- A断面：每方向两条3.75m车道、内肩1.25m、外肩3.25m；未铺装median14m；全宽38m。路缘/排水/削填/地基未生成。草地中央开发凹槽0.3m，非已设计排水系统。
- 直线路面向外2%横坡；按曲率过渡权重将断面过渡为统一3%超高平面，再过渡回来。未按设计速度计算正式超高展开长度；仅验证非共面表面导出与三角回退。
- 里程UV周期4m，u按材质分带映射单atlas；显式周期切分，UV全部0..1。材质区padding和V端点颜色重复，未做正式mipmap/PBR验收。
- 白/黄边线宽0.15m，虚线3m实/9m空是开发演示参数（不声明本轮MUTCD标线认证）；标线抬高0.003m，游戏深度冲突未测。当前生产旧3实/6空标线没有变化。
- s=64恰是世界x=64平面，路面仍轴向，左右chunkX=3/4；横断面覆盖的各chunkZ共用此平面。s=128/192是曲线**里程窗口接缝**，没有声称它们是完整真实XZ chunk裁剪。

## 4. 本轮验证结果

| 窗口 | 存储顶点 | 原始Triangle | 原始Quad | 等效三角形 | 实际Loader baked faces |
|---|---:|---:|---:|---:|---:|
| 0–64 | 3728 | 0 | 932 | 1864 | 932 |
| 64–128 | 3704 | 1852 | 0 | 1852 | 1769 |
| 128–192 | 3704 | 1852 | 0 | 1852 | 1852 |
| 192–256 | 3728 | 932 | 466 | 1864 | 1315 |
| 合计 | **14864** | **4636** | **1398** | **7432** | **5868** |

每件5parts；实际单件bounds/extent见metadata，位置均在±256局部范围、单件文本小于4MiB、远低于现行顶点/面数上限。存储顶点包含UV面切分重复，不是拓扑唯一空间点。导出拓扑按QUADS提交为24136 corners；现行Loader再保守合并166对Triangle后，若全部提交为23472 corners，不是GPU draw calls实测。

- `generate --check`与`verify --check`通过；两次独立构造结果全字节一致，SHA-256清单可追溯。
- Float32后最小三角面积约0.073054m²，UV行列式最小约0.035，所有面向上，最小法线Y≈0.99822；无退化面/NaN/越界索引或UV。
- x=64整断面材质/顶点/U/周期V集合一致，实际float顶点缝隙0；另两个窗口最大3.56e-6m以内，小于1e-5验收阈值。
- 元数据解析surface normal在共同s/u一致；**当前渲染仍是平面法线**，这不等于跨边界每个显示法线都无可见变化。
- 真实 `AflMeshLoader` 四件全部加载，等效三角形与part数量正确、烘焙法线单位化。其额外Triangle pairing使统计不同于JSON；没有绕过或修改校验器。
- 初版离线检查曾把已量化Quad重新当源精度调用旧保留函数，错误拒绝28个合法量化恢复面；已改为源精度重导出一致性+现行Java Loader实际校验，未改共享算法/资产。
- JShell初次默认JDI受沙箱端口限制，最终local execution成功；不是游戏启动失败，也不是Mesh格式失败。

## 5. 可行性结论与尚未实现能力

**已证明**：少量几何函数+参数JSON可直接生产道路顶点/面，复用已有.mjs导出，包含直路、分带肩/median、连续缓弯、直坡/竖曲线、横坡/超高过渡、全局里程UV和局部窗口。无需新export格式或修改枪械/机器渲染。Atlas只是开发材质；既有手工艺术不被替换。

**能力边界**：本配方仅双向四车道；宽度参数可改，但校验报告针对已提交的38m配方，六车道/断面切换/分合流/gore/匝道/桥隧并未生成。无全国批量资源目录、运行时Mesh缓存、LOD、chunk曲线裁剪、车辆动力学或物理斜面。对角/超高曲面的渲染光照需要实机；任意PBR、透明depth、Oculus/Embeddium结果均UNKNOWN。

后续最小扩展是道路专属连续surface+有限可见分片适配，不是扩共享sidecar格式。有限离线母件用于直路/护栏等重复资产；复杂主线按参数生成受预算Mesh，持久化路线/工程版本而非每几格永久独占大资产。正式细化及Claude资产接口见设计契约。

本轮没有Java源码改动，因此未运行compileJava；也未运行build/processResources/runClient/全套Java测试。没有施工、方块注册、新挖掘标签或Survival掉落行为，故Survival验证不适用。
