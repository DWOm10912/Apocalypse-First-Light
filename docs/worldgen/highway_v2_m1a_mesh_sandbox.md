# Highway V2 M1-A：隔离世界空间 Mesh 与基础碰撞测试场

日期：2026-10-07。基线：`65d40b0f`（原型）及 `a97e428d`（审查/设计契约）。

**状态：M1-A 测试能力已集成主目录 Master；用户确认连续道路 Mesh 实际渲染正常、双向分离与弯道视觉基本正常、玩家坡面行走无明显卡脚，且 /afl dev highway_mesh verify 已成功执行。完整生命周期和光影兼容尚未验收。** 此文描述开发原型，不把全国 Highway、Terrain Phase 2 或 Road V1-B 标为已改造。
集成说明（2026-10-07）：从 `master@6ad0801f` 正常合并隔离测试分支 `codex/highway-m1a-test@0dcd6025`。仅迁入 `a97e428d → 8cb341dc`、`65d40b0f → c1e9905f`、`f5c86b84 → 0dcd6025` 三个 Highway 提交，原始引用保存在 cherry-pick 记录；本合并另同步用户验收状态文档。不引入 `38e3f391`、`5dc68bbc` 的 Terrain 增量，也不删除 Master 原来已有的 Terrain Phase 0。V2-0 审查采用的原基线包含 Terrain 规划，其历史接口描述不代表此次已将规划代码带入 Master。

本次实机结论来自用户确认，不扩展为保存/重进、区块卸载重载、资源重载、光影或性能全面 PASS。下文离线指标和第11节原编译记录保留原型交付时点；本次主目录编译结果以集成交付记录为准。原有正式 Highway/RouteGraph、Road V1-B、枪械/机器 Mesh、A1 建筑和 MCP 均不改变，M1-B 现已获准在独立分支开发，见 [M1-B报告](highway_v2_m1b_route_geometry.md)；没有在Master启用新路线施工。

## 1. 复用与实现边界

逐项核对并复用了 [V2-0 原型](highway_v2_0_mesh_prototype.md)：256m、38m、四个 64m 母件、257 个中心线样本、连续水平缓弯、5% 示意纵坡/竖曲线、2% 横坡到 3% 超高、原有占位 atlas。没有重做离线道路，没有编辑 `.bbmodel`、原始四件 `.aflmesh.json`、枪械或 Claude 资产。

新增 Java 全在 `src/dev/java/com/antaurora/apofirstlight/dev/highwaymesh/`：

| 文件 | 实际责任 |
|---|---|
| `ChunkMeshGeometry.java` | 无 Minecraft 依赖；按世界 X/Z 的 16 格平面裁剪三角形，插值原 UV，继承裁剪前法线；负坐标用 floor |
| `RoadMeshAsset.java` | 校验资源 SHA/契约；调用原 `AflMeshLoader` 读四件模型；生成 tile、碰撞列、连续表面查询；tile 仍由同一 Loader 校验并烘焙 |
| `WorldChunkMeshRenderer.java` | 世界空间位置、相机变换、逐顶点采光、原法线适配；调用原 `AflMeshRenderer.renderPartsAtCurrentPose`，恢复其 cull 开关 |
| `DevHighwayMeshClient.java` | AFTER_BLOCK_ENTITIES 渲染入口、真实客户端 chunk 驻留、视锥剔除、每 tick 最多烘焙 2 个 tile、资源重载/退出清理、统计 |
| `DevRoadSurfaceBlock.java` | 一个不可见测试 block ID，32 个有限高度状态；碰撞和选择框；无物品、无 BE、无动态 entity physics |
| `DevHighwayMeshRegistration.java` | 非 production 环境注册测试方块及独立网络通道 |
| `DevHighwayMeshCommands.java` | OP2 + 创造模式 + 主世界入口；preview → dry_run → create；status/verify/sample/hide/remove；读写保护 |
| `DevHighwayMeshData.java` | 每维度一份 SavedData，记录实例 UUID、版本、原点、阶段、精确位置/高度状态所有权表 |
| `DevHighwayMeshNetwork.java` | 独立 S2C scene descriptor；只发原点/版本/实例，不传 Mesh 顶点；不改枪械网络 |

调用链：

```text
V2-0 的四个已导出母件 + metadata
  → prepare.mjs 字节复制 + scene 描述（不生成新道路）
  → RoadMeshAsset / 现有 AflMeshLoader
      ├─ canonical surface → sample(x,z) → 高度/法线/近似里程/材质
      ├─ 16×16 XZ 裁剪 → 原 Loader 校验 → 客户端驻留 tile 缓存
      │    → WorldChunkMeshRenderer → 原 AflMeshRenderer → 单 atlas cutout buffer
      └─ 每 1×1 XZ 列的高程范围 → 1/32m 高度状态 → 原生方块碰撞
preview → 全空气 dry_run → create → SavedData 所有权表 + 原生 chunk 方块保存
登录/重生/换维度 → S2C 描述 → 按已加载 chunk 重建视觉
remove → 只删除表内仍精确匹配的测试方块 → 清空描述
```

没有新增全国路线、自动施工、RouteGraph 消费者、Terrain 规划/生成切换、Road V1-B 接口调用或正式建筑 ID。测试放置会只读检查现有 authoring reservation，重叠即拒绝。

## 2. 开发资源与打包

`src/dev` 不是隔离保证。当前 `build.gradle` 本来就把 dev Java 加入 main compilation。本轮：

- 沿用现有发布 jar 对 `com/antaurora/apofirstlight/dev/**` 的排除；命令、注册和客户端入口还显式检查 `!FMLEnvironment.production`。
- 新增 main resources 源目录 `src/dev/highway_mesh_resources`，供正常开发启动复制资源。
- 发布 jar 明确排除 `assets/afl_highway_demo/**`、测试 blockstate、测试 particle model 及根 `.gitattributes`。本轮只审查配置，未运行 jar/build，故没有“发布包实测 PASS”。
- 模型放在 `assets/afl_highway_demo/prototype/`，不会进入现有全局 `meshes/` 扫描缓存。四个固定母件，61 个运行时 tile **没有各自注册资源 ID**。
- 测试方块唯一 ID：`apocalypse_firstlight:dev_highway_surface`，`layers=1..32`。资源独立，不覆盖任何既有 ID。

这是专用开发测试世界内容。正式 jar 不提供测试方块，切换到正式版本前先移除测试场，不把含此 fixture 的世界当生产存档。

## 3. 生成、校验与启动准备

仓库根目录：

```powershell
node tools/highway-v2-m1a/prepare.mjs
node tools/highway-v2-m1a/prepare.mjs --check
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
node tools/highway-v2-m1a/verify.mjs
# 在正常 compileJava 已完成之后，可校验真实编译产物：
node tools/highway-v2-m1a/verify.mjs --compiled
```

`JAVA_HOME` 按本机已有 JDK17 调整。验证只用现有 Gson 缓存，不下载依赖、不启动 Minecraft。源码模式将相关纯 Java 类临时放入同一 compilation unit，机械移除 package/调整类型限定；编译产物模式直接调用 `build/classes/java/main` 中同一类。本轮两种模式的结果 JSON **SHA-256 完全一致**：`5417280b9257d815605b8cd1ae827eef07234f7f0d02b5c7e02ab4f6aea73a11`。

输出：12 件开发资源（四 mesh/geo 配对、atlas、scene、blockstate、particle model）；统计 [validation.json](../../tools/highway-v2-m1a/validation.json)。原始来源字节与 V2-0 相同；`runtimeContract=highway-v2-m1a-1`，scene SHA 为 `e40acd8fa2180f5dbf8b08308df0e8ba4e7d5ecd4749150419933da2e96b6c67`。将来改几何/碰撞算法应升级契约，不能静默重新铺设已有实例。

用户随后用项目正常 **开发客户端** 启动流程运行。新增 Gradle resource sourceSet 需要正常刷新/复制开发资源；仅 compileJava 不会替用户执行 processResources。遇到 `Missing dev resource` 时检查开发运行配置是否已刷新；不要把资源手工塞入生产 meshes 目录。本轮没有替用户启动客户端。

## 4. 游戏内操作

建议新建开启作弊的超平坦创造测试世界，主世界，客户端及服务器 view-distance 至少 12。选择此例原点 `(0,80,0)`；X/Z 必须是 16 的倍数，原型沿 +X 前进，不提供旋转/多实例选项。正常地形世界也可用，但包络必须完全空；命令不会削山、砍树、排水或清建筑。

先飞到上空，确保人在扫描包络之外：

```mcfunction
/gamemode creative
/tp @s 128 105 16
/afl dev highway_mesh preview 0 80 0
```

等待当前 chunk 的 tile 出现，最多每 tick 烘焙 2 个，61 个全加载约需 31 ticks 加上实际 CPU 时间。预览为轻微偏绿的实体视觉面，**无碰撞、无世界写入、不保存**，只有执行者收到预览。可以先空中观察形状，不能当可站立路面。

```mcfunction
/afl dev highway_mesh dry_run 0 80 0
/afl dev highway_mesh create 0 80 0
/afl dev highway_mesh status
/afl dev highway_mesh verify
```

preview 后 5 分钟内 dry_run；dry_run 后 60 秒内 create，必须同一玩家、同一原点/资源版本。create 重做全量预检才写块。全场扫描 AABB 在此例约为 `[-1,78,-20]..[259,89,44)`；这是隔离场的安全空域盒，不是正式46m施工模板。检查所有涉及 chunk 已加载、世界边界/高度、空气/空流体/无 BE、无实体、无 authoring reservation。遇到 `UNLOADED_CHUNK`，留在场地中心上方并扩大服务端可视距离、等待加载；不会强制加载。

创建后场景广播给该维度玩家，预期 `phase=COMPLETE expected=9963 present=9963 changed=0 unloaded=0`（全场已加载且未被编辑时）。再次 create 拒绝覆盖，必须先 remove。`verify` 是所有权/加载状态检查，**不是“实机视觉自动验收”**，不修补被改动方块。

```mcfunction
/afl dev highway_mesh sample 156 15
/afl dev highway_mesh sample 128 12
/afl dev highway_mesh hide
```

`sample` 只查询已持久化实例及已加载查询 chunk；返回世界高度、单位法线、近似里程和材质，场外返回 OUTSIDE_ROAD。`hide` 取消自己的临时预览并恢复已有持久化场景，**不删除碰撞、不隐藏已建成场景**。

移除时仍需创造模式/OP2，在场地中心上方使全部 owned chunks 已加载：

```mcfunction
/tp @s 128 105 16
/afl dev highway_mesh remove
/afl dev highway_mesh status
```

remove 只删除 ledger 内、ID 和 layers 都仍匹配且无 BE 的测试方块。玩家替换为其他方块或改变测试高度状态的格子保留，并报告数量；不恢复旧地形、不动 ledger 外方块。不提供“搜索全世界删同 ID”的兜底。空场重复 remove 安全。

## 5. 碰撞、查询与局限

视觉三角面、方块碰撞、选择框独立：

- 对每个与道路投影相交的 1×1 格，求原道路（不含标线）表面在格内的 min/max 高程，取中间高度再量化到 1/32m，生成一个有限 AABB。这也为草地中隔带提供基础脚底支撑。
- 玩家仍走 Minecraft 原生台阶近似，**没有任意三角斜面物理**。相邻列最大落差 0.0625m，小于原版通常 0.6m step height，因此静态上具备免跳上坡条件；真实行走、冲刺、潜行及其他模组对 step 的影响尚未测，不能承诺无卡脚。
- 道路与碰撞顶面的保守格内最大偏差 0.057551m；玩家宽度会使脚底同时受相邻列最高面影响，不能把此数字当站姿/第一人称的实机最大误差。边缘碰撞铺满整格，可能伸出视觉轮廓不足一格；没有精确边缘碰撞、护栏阻挡或桥台侧壁。
- 选择框就是同一离散高度盒，没有 Mesh 三角选取和新增交互锚点。创造模式敲掉测试块会造成支撑缺口，verify 会报告 changed，系统不会自动覆盖修复。
- `RoadMeshAsset.sample(localX,localZ)` 是最小连续表面契约：Optional、height、单位 normal、station、material。高度为原三角表面的分片线性插值；法线保留面法线，在三角边界/路冠/中隔带凹槽可有折变；station 来自最近中心线样段投影。它不是完整设计线解析解、车辆接地或车辆动力学。

挖掘审查：该 block 是不可生存开采的管理员 fixture（strength -1），没有 BlockItem/loot table，不属于可回收工业资产；不配置 mineable/needs tier 标签，不调用 requiresCorrectToolForDrops，因为不存在正常工具掉落路径。仅命令所有权删除/创造破坏；生存挖掘与掉落**尚未实机检查**，列入验收，不将静态配置当生存 PASS。

## 6. 几何、法线与实际16格接缝

| 离线指标 | 结果/含义 |
|---|---|
| 原存储顶点 / 原等效三角形 | 14,864 / 7,432；四件 V2-0 源未改 |
| 实际16×16渲染 tile | 61，覆盖负 Z chunk；不是四个64m窗口 |
| 裁剪烘焙后三角形 / faces | 10,841 / 10,841，全部 triangle fallback，无新的 quad 合并假设 |
| 烘焙存储 corners / 全场渲染提交 corners | 32,523 / 43,364；三角面按现有 QUADS 后端 ABCC 提交 |
| 裁剪投影面积误差 / Float32烘焙面积误差 | 7.46e-11 / 4.21e-5 m² |
| Float32烘焙最大高度偏差（面心采样） | 3.52e-7m |
| 实际16格边界采样 | 5,059 对，缺失0；每0.25m错开整数顶点采样，不是连续域穷举证明 |
| 这些边界最大高度差 | 8.88e-16m；低于1e-5m门槛 |
| 边界UV相对原里程映射最大偏差 | 1.69e-6；未在tile重置UV，保留原4m周期的合法回绕 |
| 裁剪新增的法线偏差 | 0（保留原double法线，提交时转换float）；源面之间的最大向量差仍约0.08564，路冠/中隔带折面不是光滑法线 |
| 实际16格碰撞邻列 | 1,279 对，最大台阶0.0625m；视觉接缝不等于物理光滑 |
| 碰撞总列 / 格内最大高度误差 | 9,963 / 0.0575503m |
| 负坐标 / 重复性 | 同一 clipper 平移(-320,-64)检查通过；独立加载/重烘焙指纹一致 |

裁剪产生一个 Float32 下几乎坍缩的极小三角面，投影行列式≤1e-9时被剔除；这是有记录的亚微米级 sliver 处理，不绕过 Loader，不声称数学上每个 infinitesimal 面都完整。其余均通过现有 Loader 的几何/UV/法线检查。

离线检查发现重新由狭长 float 三角形求法线会放大误差，因此本轮道路适配层为每个裁剪面携带原法线，按实际 pose 变换。共享 `AflMeshPart/Loader/Renderer` 均未改；格式仍 v2，且检查 Loader 没有改变三角形数量/次序契约（独立索引不允许配对），否则拒绝该 tile。没有为枪械增加新 normal/tangent 规则。

光照在 adapter 中通过实际 pose 逆变换取得世界顶点位置，每帧按 BlockPos 缓存 packed light，避免整tile用一个中心光值；同一位置的两边采相同格。未做光照插值高级后端、PBR/tangent、阴影专用 pass 或 LOD。标线仍抬高3mm；Z-fighting、纹理渗色、昼夜光照断层和第三方 shader 表现都必须实机检查。

原64m窗口本身的 Float32 接缝也仍适用 V2-0 检查（最大约3.56e-6m），本轮运行其 `verify.mjs --check` 通过，没有误把曲线 s=128/192 当成 XZ chunk 平面。

## 7. 保存、重载和缓存生命周期

| 事件 | 实现行为 | 当前证据 |
|---|---|---|
| preview | 仅执行者收到虚拟场景，无方块/存盘；退出即丢弃 | 源码路径，待客户端 |
| create | 正常原生 chunk 保存碰撞；`data/afl_dev_highway_mesh_m1a.dat` 保存 schema1 + UUID + 原点 + version + owned表 | 源码路径，待存档往返 |
| chunk unload | 当帧驻留检查不再提交；下个client tick删除该tile缓存；不保持 chunk ticket | 源码路径，待实际卸载 |
| chunk reload | 仅当前描述的唯一tile key重建，Map防重复，每tick最多2件 | 源码路径，待实际重载 |
| 退出/换世界 | client Level.Unload/LoggingOut 清描述、母件和tile缓存；服务端停止释放asset/pending | 源码路径，待游戏退出 |
| 重进/重生/换维度 | 服务端读取SavedData后发送描述；资源版本吻合才显示；回主世界重同步 | 源码路径，待实际时序验收 |
| F3+T资源重载 | 保留场景描述，清母件和tile，重新校验版本/构建；不写碰撞 | 源码路径，待实机 |
| 损坏/未知存档版本 | Loader拒绝；显式检查“文件存在但Vanilla返回null”以防把反序列化失败当空场覆盖 | 已对照Vanilla源码，待故障注入 |
| create/remove中途写入失败 | 保留PARTIAL阶段及已知ledger，只允许人工重试remove；不显示完整视觉、不自动删除外部方块 | 源码路径，未人为触发 |
| Mesh资源不匹配 | 停止视觉；碰撞保持原状，remove仍按ledger工作 | 源码路径，待资源故障实测 |

正常保存不是跨 chunk 和 SavedData 的原子事务；强制杀进程/磁盘错误可能使两者不同步，本轮不保证崩溃恢复。未知 orphan 不会按 ID 扫描删除。`status/verify` 可显式重新同步描述，但如果正常登录需要它才能恢复显示，应记录为生命周期验收失败，而非当作通过。

## 8. 性能与全国扩展判断

本机 JDK17 编译产物的隔离检查一次测得 asset load（含clip/collision）约307.7ms，61 tile 全量 bake约203.6ms；源码模式的冷启动/JIT结果有波动。这是离线墙钟示例，非游戏加载/帧耗时基准。客户端命令报告自己的 asset load、累计tile bake、缓存/绘制tile、累计烘焙/回收数及上一帧提交量，供实机记录。

- 同atlas的可见tile共用一个 cutout RenderType，每帧最多一次显式 `endBatch`。这叫提交批次，**不是已测GPU Draw Calls**，shader/驱动可能另有pass。
- 全场角点float数组约1,040,736 bytes，原法线double数值约260,184 bytes；还有对象、索引、原母件、clip列表、碰撞列表、临时JSON及render buffer，未测总heap/GPU占用。
- tile缓存只保留已加载的61件上限；母件/clip/碰撞数据在当前场景存续期间保留，退出/资源重载释放引用；不持有Level引用、不强制chunk。服务端一份asset缓存持续到停止。
- 现有 renderer 是 CPU 每帧顶点变换/提交；本例全部可见时43,364 corners。不宜据此声称全国高速已具备性能容量。按密度粗线性推至10km，全量常驻约2,383 tiles、42.35万三角面、169.39万提交corners、38.8MiB角点数组、38.9万碰撞方块；这仅是数量风险估算，不是实际全国方案。
- 未来需要按路线工程片段按需生成/失效、可见区域预算、独立性能采样，之后决定缓存上传或LOD；不能把当前四母件的全量解析方式扩成全国唯一资产目录，也不能复制每格一个BE/模型注册。

结论：**可作为 M1 的隔离验证入口；尚不足以批准全国替换。** Oculus/Embeddium、PBR、阴影pass、帧时间p95/p99、多人同步及实际内存均待测。没有进行无证据的大规模优化。

## 9. 38m四车道与37m六车道复核

沿用 [V2-0设计契约和官方来源](highway_v2_0_design_contract.md#2-参数化断面与每层宽度)，尺寸是AFL分数格取值，不是本轮新的规范认证：

| 构件/层 | A：乡村、城际四车道（本原型） | B：城市/郊区紧凑六车道（未实现） |
|---|---:|---:|
| 单车道 | 3.75m | 3.75m |
| 每方向车道数 | 2 | 3 |
| 每方向内肩 | 1.25m | 3.25m |
| 每方向外肩 | 3.25m | 3.25m |
| 中央带（不含内肩） | 14m未铺装分离带 | 1.5m实体/侧向余量带 |
| median（含两内肩） | 16.5m | 8m |
| 两方向行车道+肩铺装量 | 24m | 35.5m |
| 最外路缘之间总跨度 | **38m** | **37m** |
| 初步路基/排水带 | 42m | 41m |
| 初步局部施工模板 | 46m | 45m |

六车道的铺装量大11.5m，而中央未铺装宽带缩小12.5m，故总跨度反而小1m。A用于土地较宽裕的乡村/城际；B用于需要紧凑中央防护的城市/郊区，不是给乡村高速压缩净区的理由。B可选外肩3.75m时总跨度38m。42/46与41/45为工程预算，削填、净区、桥隧及ROW还应独立求包络，不保证65格旧claim足够。测试场只显示38m表面，不施工42m路基或46m地表工程。

## 10. 用户实机验收表（基础视觉与坡面行走已确认，其余仍需逐项验收）

1. **启动/资源**：按第4节新世界创建；客户端无缺失材质/报错，status预期COMPLETE/9963且changed=0，渲染tile最多61、无重复实例。保存seed、原点、view-distance、模组/着色器配置。
2. **视觉**：空中俯视和低角度观察四条车道、双向分离、路肩/草地中隔带、平缓曲线。按s约96/136/176/216观察竖曲线及5%段（s136→156基准高程增加1m），侧看横坡/超高。无异常翻面、偏移、显著Z-fighting、接缝黑线；预览偏绿与创建后的正常颜色应区分。
3. **基础行走**：create后 `/tp @s 16 82 11`，退出飞行落到路面，先创造步行/冲刺，沿正Z方向车道向+X跟随弯道至末端；再走反方向车道、横穿肩部/中隔带。关闭自动跳跃，检查免跳缓坡、无卡脚/穿落；台阶近似允许，明显悬空需记录坐标和脚底Y。可临时切生存测试fixture不可开采/无掉落，再切回创造执行管理命令。
4. **真实chunk边界**：F3+G，重点x=16/64直路、x=128/160/192曲线纵坡、x=224和z=16横向跨界。沿不同车道步行穿越，视觉和碰撞分开记录；`sample 156 15`等查询作高程参照。不能只看四母件接缝。
5. **卸载/返回**：飞到 `/tp @s 2048 105 2048`，等待卸载后status应cached=0（canonical asset仍可存在）；返回中心等待≤2tile/tick的重建，数量恢复且无残影/重复。未加载时remove必须拒绝且不写块。
6. **保存重进/换维度/重生**：正常退出保存、重进，不输入create即恢复唯一场景及碰撞。再次verify在全场加载时仍9963/changed=0。换维度后旧场景消失、回主世界恢复；死亡重生也无重复。任何需要status救回场景的情况记录FAIL。
7. **重载与光照**：F3+T后自动重建、碰撞不变；日夜分别观察真实16格边界，手持/放置光源检查采光。先纯Forge，再Embeddium，再Oculus+指定shader；每组合单独记录，不能互相代表PASS。
8. **保护/移除/重复**：独立测试位置先用现有物件堵住扫描盒，dry_run/create必须拒绝且现场不变；成功创建后把一个fixture换成普通方块，verify changed增加，remove应保留替换方块并移除其余owned块。清理该人工替换物后可重新preview/dry_run/create。预览退出重进不能变成已建场景。
9. **性能**：记录status客户端统计、F3帧图、加载/重载尖峰、离开后缓存数；确认性能是否适合继续M1。GPU draw calls如需数值须另用真实分析工具采集，不能用batches=1替代。

客观门槛：几何离线1e-5m缝差门槛已通过；实机要求原版无跳上坡/不掉落、真实16格边界无可见孔隙/重复/明显光照断层、正常保存重进恢复且计数一致、卸载无残留、保护删除正确。未完成这些项目之前，仅称“可测试实现”。

## 11. 验证与Git记录

- 开始时工作树干净，HEAD确为65d40b0f；新分支 `codex/highway-v2-m1a`，只纳入本轮文件，不推送、不合并其他分支。
- prepare字节检查、V2-0现有verify --check、新的源码模式及编译产物模式几何检查通过；source/compiled报告一致，相关结果见validation.json。
- 首次wrapper启动因沙箱不能写既有Gradle缓存锁而退出，**没有执行compileJava**；获得缓存访问权限后，同一命令唯一一次实际编译 `BUILD SUCCESSFUL in 35s`，4 actionable tasks（2 executed/2 up-to-date）。存在仓库既有及同类ResourceLocation弃用警告，共100条；未为消除警告扩大范围。
- 没有运行build、processResources、runClient、完整测试套件或第二次实际Java编译；没有客户端截图、实机存盘回读、FPS或兼容性PASS。
- 本轮变更：build.gradle开发资源及发布排除，9个隔离Java文件，开发资源目录，tools/highway-v2-m1a及本报告；V2-0三文档仅增加后续状态链接/历史范围说明。具体提交号由交付回复及git log给出，避免文档自引用哈希。

M1-A 已完成本次批准的 Master 集成，仅提供开发测试能力。继续补充生命周期、光影兼容和性能验收；后续M1-B见 [独立路线预览报告](highway_v2_m1b_route_geometry.md)，不自动启动全国 Highway V2 替换。
