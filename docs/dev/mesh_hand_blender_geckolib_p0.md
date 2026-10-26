# AFL Mesh 五指手掌 H1-P0：Blender → GeckoLib → 游戏内

2026-10-23。目的：验证 Blender 里做的五指手和骨骼动画能不能转成 GeckoLib 动画、经 Vextris Mesh 管线在游戏里正确显示。只是原型：没碰正式第一人称手臂、P9 正式模型和动画、换弹逻辑、其他枪和人体。

状态：Blender、导出、离线 GeckoLib 换算全部 PASS。用户 2026-10-23 第一次进游戏：B 正常显示、五指在动；A 的手指扭到别处、有两节飘在枪下面。原因在导出：出对比图时 A 的集合在视图里被隐藏，Blender 不再更新隐藏物体，A 停在第 84 帧的姿势，被当成静止姿势写进文件，游戏里又叠一遍动画。已修：导出时先显示 A，静止位置直接按骨骼矩阵算；检查工具加了 A 的比对（每块按 GeckoLib 骨骼摆好后和 Blender 比，现在 0.010 px）。两份模型首尾相顶，间距从 11 px 改成 18 px。修好后用户再进游戏：A 和 B 一样了（2026-10-23）。**用户定 B（连续蒙皮）为方向**：A 分段感太重，只留作对照。第 6 节的最小 API 需求同日发给 Vextris 会话，AFL 不等它，先在 Blender 侧修指蹼、做前臂。

## 1. 实际技术路线

```
hand_p0_lib.py（骨架、SDF 手形、P9 距离场）
  → build_mesh_hand_p0.py（放置 → Rig → 求解握姿 → 网格 A/B → 动画 → 路径检查 → 自检）
  → mesh_hand_p0.blend（源工程）
  → export_geckolib.py（通用导出：geo / animation / aflmesh / skin / Blender 参考）
  → tools/check-mesh-hand-p0.mjs（离线按 GeckoLib 规则重算骨骼和蒙皮，对 Blender）
  → client/dev/MeshHandTest.java（/afl dev handtest，GeckoLib 播放 + Vextris 画 A、CPU 蒙皮画 B）
```

两种显示方式都做了，一起保留：

| | A 分段刚体 | B 连续蒙皮 |
|---|---|---|
| 网格 | 每根骨骼一块闭合体（`A_<bone>`），16 块 | 一整块皮肤 `hand_B`（SDF 混合 → OpenVDB 等值面 → 减面），每顶点最多 4 根骨骼权重 |
| 游戏内 | Vextris 现有能力：`GeckoMeshRenderer.renderBone` 挂在 GeckoLib 骨骼上 | Vextris **没有**蒙皮部件：测试入口在 `renderCubesOfBone` 里记下每根骨骼的矩阵，渲染后在 CPU 上做线性混合蒙皮，写进 `MeshRendering.cutoutType(tex, TRIANGLES)` |
| 外观 | 关节处是分开的段，指节有缝（像关节玩偶） | 手背、指蹼、关节连续，像真手 |
| 定位 | 兼容原型 | 目标方案 |

## 2. 骨骼结构

16 根骨骼，全在 P9-01 V3 的模型空间（同一套像素坐标，不缩放枪）：

```
hand（手掌 / 腕根，定位点 forearm_socket = 以后接前臂）
├─ thumb_01 → thumb_02 → thumb_03     拇指：腕掌关节三轴，掌指、指间各一轴
├─ index_01 → index_02 → index_03
├─ middle_01 → middle_02 → middle_03
├─ ring_01 → ring_02 → ring_03
└─ pinky_01 → pinky_02 → pinky_03
```

- 四指每段的长度、粗细、张开角各自不同（`hand_p0_lib.py` 的 `FINGERS`）；拇指单独构造（外展 44°、掌侧 14°、旋前 60°），不是复制四指。
- 每个关节都能弯；握姿由求解器按接触停止，不手写角度：`PROFILES` 定每节的弯曲 / 扭转 / 尺偏比例，`REACH` 让拇指找目标点（贴左侧握把面、压在中指上方）。
- 绑定姿态没有旋转（GeckoLib 动画骨骼的约定），动画骨骼全部带关键帧。

## 3. 资源格式

| 文件 | 内容 |
|---|---|
| `geo/dev/mesh_hand_p0.geo.json` | 只有骨骼、无方块；纹理 16 × 16 |
| `animations/dev/mesh_hand_p0.animation.json` | `open_to_grip_p9`，1.4 秒，60 fps 烘焙后约简成 255 个线性键，`hold_on_last_frame` |
| `meshes/dev/mesh_hand_p0.aflmesh.json` | A：16 个部件 / 3871 顶点 / 7678 三角面（Vextris 上限 128 / 65536 / 16384） |
| `skins/dev/mesh_hand_p0.skin.json` | B：AFL 自己的格式 `afl_skin` v1，模型方块坐标，顶点 `[x,y,z,u,v,nx,ny,nz]`，权重 `[[骨骼序号, 权重]…]`（≤4，和为 1），三角形；3502 顶点 / 7000 三角面 |
| `textures/dev/mesh_hand_p0.png` | 中性肤色 |

坐标换算（导出脚本和检查工具一致）：Blender → 渲染 = (x, z, −y)；geo 的 pivot 和 position 的 x 取反；旋转从 M = Rz·Ry·Rx 拆成 `[-x, -y, z]`；局部变换 = T(pivot + pos)·Rz·Ry·Rx·T(−pivot)。

## 4. Blender 导出流程

1. 在 Blender 里打开 `design/items/mesh_hand_p0/source/mesh_hand_p0.blend`（场景里 P9_ref 是从游戏 aflmesh 读进来的参考，只读）。
2. 改手形或握姿：执行 `build_mesh_hand_p0.py`，`run('place','rig','solve','mesh','anim','path','checks')`。改了 rig 之后必须重跑 mesh（B 的骨架修改器要重新绑定）。
3. 导出：执行 `export_geckolib.py` 的 `export(cfg)`，`cfg` 写骨架名、动作名、帧范围、资源 id、A 的部件集合、B 的蒙皮物体、定位点、参考帧（见脚本末尾示例）。脚本按骨骼遍历，不按手指写死，其他骨架也能用。
4. 离线检查：

```bash
node tools/check-mesh-hand-p0.mjs
```

5. 出对比图：`render_views.py` 的 `render(frames, 'A'|'B', out_dir)`（Workbench、正交相机）。

## 5. 兼容结论

| 环节 | 结论 |
|---|---|
| Blender | PASS：工程能打开，五指独立骨骼，握住 P9，A 全程 0 mm 穿模，B 最大 0.22 mm（指蹼的混合区，看不出） |
| 导出 | PASS：geo、动画、aflmesh、skin 都生成，Vextris 上限和纹理尺寸对得上 |
| GeckoLib | 离线 PASS：按 GeckoLib 规则重算 16 根骨骼 8 个参考帧，旋转误差 0.095°、位置 0.008 px（关键帧约简容差累积；不约简时 0.001° / 0.0001 px）；游戏内读取待用户看 |
| Vextris | A：现有 API 就够（`renderBone`）；第一次进游戏错位是导出问题，已修，离线 0.010 px，待再看；B：Vextris 缺蒙皮能力，原型用 CPU 蒙皮绕过，离线蒙皮误差 0.010 px，游戏内已显示 |
| 游戏内 | 第一次：B 显示、手指在动；A 错位（已修）；握住后的姿势待用户确认 |

B 的已知问题：

- 食指伸直、中指弯曲时，两指之间的指蹼被拉开，最大拉伸 5.79 倍（49 条边超过 2 倍），看起来是薄膜。改善办法是在指蹼加辅助骨骼或修正形状，不是 P0 的范围。
- CPU 蒙皮每帧算 3502 顶点，测试用够；正式用要进 Vextris（下一节）。
- 光影包下 B 走 TRIANGLES，法线逐顶点保留；没有验证 Oculus 阴影通道（测试入口在阴影通道直接跳过，不投影）。

握姿的取舍：拇指原想沿框架往前伸，但所有接近目标的姿势都会插进套筒，改成拇指朝下压在左侧握把面、中指上方（常见的手枪握法之一）。食指伸直贴在扳机护圈上方的框架上，没进护圈。

## 6. 对 Vextris 的最小 API 需求（等用户决定）

只在要把 B 用到正式内容时才需要：

1. `SkinnedMeshPart`：顶点 + 每顶点最多 4 个（骨骼序号, 权重），和现有 `MeshPart` 并列，从 aflmesh 的新字段读（或者读本原型的 `afl_skin` 格式）。
2. `GeckoMeshRenderer` 一个入口：一次 GeckoLib 遍历里收集骨骼矩阵，遍历结束后画蒙皮部件（CPU 版本先够用；以后再考虑 GPU 蒙皮）。
3. 走现有的 `smoothTopology()` / TRIANGLES，保证光影包下法线正确。

不需要重写动画运行时，也不碰 GeckoLib。

## 7. 游戏内测试入口

只在开发客户端注册（`FMLEnvironment.production` 时什么都不做），不存档，不影响任何物品和手部通道。

| 命令 | 作用 |
|---|---|
| `/afl dev handtest` 或 `on` | 在面前约 1 格处放两份 P9 + 手：玩家左边 B，右边 A；握把右侧（手掌那面）朝向玩家 |
| `replay` | 重播（每 3 秒也会自动重播） |
| `spin` | 开关缓慢旋转，看背面 |
| `scale <倍数>` | 默认 2 倍 |
| `show both / a / b` | 只看一份 |
| `off` | 关掉，恢复原状 |

P9 用它自己的 aflmesh 绑定姿态，隐藏换弹弹匣和旧弹匣两根骨骼；P9 的 geo、动画、模型文件都只读。

回退：删掉 `client/dev/MeshHandTest.java` 和第 3 节的 5 个资源文件即可，没有别的地方引用它们。

## 8. 验收清单（任务第十节）

| # | 项 | 结果 |
|---|---|---|
| 1 | Blender 工程能打开 | PASS |
| 2 | 五指都有独立可控骨骼 | PASS（15 根手指骨 + 手掌） |
| 3 | 手掌自然包握 P9 握把 | PASS（Blender）：中、无名、小指包住前侧，掌心贴右侧，拇指在左侧 |
| 4 | Blender 动作没有明显穿模 | PASS：A 0 mm，B 最大 0.22 mm |
| 5 | 骨骼动画成功导出 | PASS |
| 6 | GeckoLib 正确读取动画 | 离线 PASS，游戏内待看 |
| 7 | Vextris 正确渲染 Mesh | A 待看；B 是原型 CPU 蒙皮，待看 |
| 8 | 游戏内五指正常弯曲 | 待看 |
| 9 | 关节没有严重扭曲或断裂 | Blender PASS（B 指蹼拉伸见第 5 节）；游戏内待看 |
| 10 | 手掌与枪械位置正确 | Blender PASS；两者同一模型空间，游戏内待看 |
| 11 | 动画稳定停在握枪姿势 | Blender PASS：握住后手掌漂移 0，没有回弹；游戏内待看 |
| 12 | 不影响 P9 V3 现有动画 | PASS：P9 文件没改，只读它的 aflmesh |
| 13 | 不影响其他玩家、武器和模型 | PASS：只有开发客户端、只画在世界里的测试点 |
| 14 | 能关闭测试入口并恢复原状 | `off`；待游戏内确认 |

## 9. 复用到 BR51-01

可行，工作量主要在握姿参数：

- 手形、骨架、Rig、导出脚本、检查工具直接复用；导出脚本是通用的。
- 要换的：P9_ref 换成 BR-51 的 aflmesh；`GRIP`（握把倾角、偏航、滚转、中指根落点）和拇指 `REACH` 目标按步枪握把重定；动作的起点和帧数。
- 步枪还有护木上的左手：需要左手（镜像骨架）和第二套握姿求解，这是新内容。
- 如果用 B，先要第 6 节的 Vextris 蒙皮能力。

## 10. 下一阶段建议

1. 用户进游戏看 `/afl dev handtest`：A、B 都在不在、手指弯得对不对、手和枪对不对齐。
2. 用户在 A / B 之间定方向；选 B 就先给 Vextris 提第 6 节的需求。
3. B 的指蹼：加辅助骨骼或权重修正，把最大拉伸压到 2 倍以内。
4. 和第一人称手臂刚性化（`docs/dev/fp_arm_rigidity_v1.md`，R0 / R1 待用户选）合在一起考虑：手腕定位点 `forearm_socket` 已经留好。

本轮不开始十指人体、P9 V4 换弹或第三人称 Mesh 人体。
