# Generator Inlet V1：发电机进线盒和 15 m 发电机电缆

状态（2026-10-13）：**已实现，`compileJava` / `compileDevJava`（`--offline`）通过，未实机验证。**
- 编译前确认游戏没开（只有 Gradle 守护进程）。
- 生成器 `--check` 通过，没有共面重叠。
- 网格、贴图、插头模型在离线 three.js 预览里看过：合盖拉出电线、掀盖电线盘在钩上、侧面、插头。图在 `design/buildings/forest_start_home_v1/m2_sheets/m2_4_inlet_game.png`，源页 `m2_source/inlet_preview.html`。
- 游戏里的外观、插拔、供电、挖掘都还没看过。

属于 [森林开局 B「溪谷老宅」](../worldgen/forest_start_home_b_v1_plan.md) 的 E1：便携式柴油发电机怎么给一栋房子供电。供电规则沿用 [Building Power V1](building_power_v1.md)，插头规则沿用 [Power Outlets V1](power_outlets_v1.md)，发电机见 [portable_diesel_generator_v1.md](../machines/portable_diesel_generator_v1.md)。

## 用户的决定（2026-10-13，看过概念图 `m2_3_e1_inlet.png`）

- 电缆 15 m，属于进线盒：电缆那一头的 L14-30 母头一直插在进线口里，不做成可以带走的单独物品。
- 发电机面板加一个 L14-30R 旋锁插座给这根电缆。
- 配电盘底部的接线盒改成接了电缆才画；另做一个住宅小配电箱（见 [building_power_v1.md](building_power_v1.md)）。

## 方块

| 方块 | ID | 说明 |
|---|---|---|
| 发电机进线盒 | `generator_inlet_box` | 30 A 防雨进线盒，挂外墙（点在墙的侧面，背后要有结实的面，墙没了会掉）。自带 15 m 黄色电缆 |

- **挖掘**：钢制工业设施，镐 + 钻石级（`mineable/pickaxe`、`needs_diamond_tool`），`requiresCorrectToolForDrops()`，掉落自身（`survives_explosion`）。硬度 3 / 抗爆 5，金属声。
- **碰撞**：没有碰撞。选择框是盒子 + 进线口的接头 + 挂钩；盖子掀开时加上盖子，电缆盘在钩上时加上那一圈电缆（方块 `dynamicShape`，跟着方块实体变）。
- **质量 / 堆叠**（估计）：7.5 kg，叠 4。
- **创造栏**：工业页，电表箱后面。
- **建造工具**：登记成 SAFE_FIXTURE，挂墙支撑（`ATTACHED_OPPOSITE_FACING`），`open` 固定为 false。

## 外形（`tools/build-generator-inlet-v1.mjs`）

真实尺寸，按毫米建模，最后换算成 px（1 mm = 0.016 px）。纯网格 + 512 LabPBR 贴图，AFL Animated Block Mesh Runtime（静止零件烘焙进区块）。不印字、没有标志。

- **盒子**：灰色喷涂钢壳，连盖子约 17.6 × 24.3 × 16.8 cm，盒子中心在那一格中间高度（离格子底 0.5 m）。边缘倒角。
- **进线口**：黑色尼龙 L14-30 公口，装在斜凸台上，朝下斜 60°；法兰、护罩、四颗螺丝。
- **盖子**（骨骼 `hood`，通道 `open`）：深罩防雨盖，顶上合页，掀开时绕合页转 100°，像雨篷一样向外翻起。盖子底边有缺口，插着电缆也能合上。
- **接头**：电缆的 L14-30 母头一直插在进线口里，尾巴四道棱，电缆从尾巴出来。
- **挂钩**：盒子下面一块小底板加 J 形钩。
- **盘起的电缆**（骨骼 `coil`）：电缆不用时从接头垂下，绕在挂钩上 6 圈（圈半径 15 cm），插头挂在圈里。只在电缆收着时画（`meshPartVisible`）。
- **插头**（L14-30P，单独的 OBJ `models/block/l14_30_plug`，不烘焙）：黑色模压旋锁插头，四片弧形插片，尾巴四道棱，电缆渲染器画在电缆末端。
- **电缆**：10/4 黄色电缆，真实尺寸，半径 10.5 mm（不放大 1.5 倍），八边形截面；色样在贴图里，单点 UV（`CORD_UV`），和 `PlugCordRenderer.L14_CORD_U / V` 一致。
- 三角面：一共 9,028（其中盘起的电缆 6,140），插头 1,708；贴图密度 25 texel/px。

## 功能

**供电**（`GeneratorInletBoxBlockEntity`）：
- 和电表箱一样，进线盒是"这栋楼"的进户点：它背后那面墙所在的列在哪个配电盘的范围里，就送给那个配电盘（`BuildingPowerZone.panelServing`，每 2 秒找一次）。中间是暗线，不用电缆。
- 进线盒自己不存电，每 tick 按配电盘需要的从发电机取电，直接送进配电盘的进线：
  - 取多少：`min(配电盘缓冲剩余空间, max(负载, min(发电机额定, 负载 + 4)))`。负载是配电盘约 2 秒的平均用电；缓冲没满时多取最多 4 FE/t 补缓冲，但不超过发电机额定。
  - 负载本身超过额定时，照样按负载去要，发电机的断路器会像真的一样跳闸（它按约 2.5 秒的平均需求，超过 20.5 FE/t 就跳）。
- 发电机的额定（运行中 20 FE/t，停机或跳闸时 0）当作配电盘的"容量"报给配电盘界面。
- 配电盘照常管：总闸、分路、跳闸规则都不变。
- 记一个约 5 秒的供电平均（`Feed`），给 Jade 看。

**电缆**（`energy/PlugCord`，种类 `L14_30`，长 15 m）：
- **收着**：插头不在插座上、也不在手里时，电缆盘在挂钩上（模型里的 `coil`），不画拉出来的电缆。
- **拿起插头**：空手潜行右键进线盒。插头本来插在发电机上，就先拔下来再拿到手里；电缆从接头拉到手上。
- **插上**：拿着插头空手右键发电机面板上的 L14-30R。L14-30P 只能插 L14-30R：拿它去插墙上插座、插线板、发电机的 5-15R，提示"插头不匹配"。反过来普通插头也插不进 L14-30R。
- **放回**：插头在自己手里时，再潜行右键进线盒，插头挂回钩上。
- **拔下**：空手潜行右键进线盒（插头回到手里），或者空手右键发电机上插着的那个插座。
- **拿着走**：离电缆出口超过 16.5 m（15 + 1.5），插头从手里掉回挂钩，提示"插头挂回钩上"。下线、换维度也一样。手里的插头不存档。
- **检查**：每秒检查插头所在的插座：插座没了、标记不对、或者超过 15 m，就拔下来挂回钩上。进线盒被拆掉时先放开发电机上的插座。
- **找插头**：插座要知道插着哪根电缆时，现在按区块里的方块实体找，范围是最长电缆 15 m + 2（原来逐格扫一个立方体，15 m 时有 35³ 格）。

**盖子**：空手右键（不潜行）掀开 / 盖上。声音暂时是原版铁活板门（音量 0.35、音高 1.6），等 Firefly 录音。

**电缆的样子**（`client/PlugCordRenderer` 的 L14 样式）：
- 从接头尾巴沿进线口的方向（朝下斜 60°）出来，三次曲线下垂，最多下垂 3 格（短距离按距离的 1/4），落在经过的方块上不穿模。
- 15 m 的电缆分段更多（至少 18 段，每格至少 3 段），每个点单独取光照，长电缆穿过亮处、暗处时颜色跟着变。
- 渲染范围跟着电缆伸出去（`renderBounds`），离屏也画。

**准星提示**：
- 空手、不潜行："掀开盖子" / "盖上盖子"；
- 潜行：插头提示（"拿起插头""拔下插头""放回插头"），和插线板、电器一样；
- 拿着插头对着发电机的 L14-30R："插上插头"。

**Jade**（`GeneratorInletJadeProvider`）：电缆"挂在钩上 / 插头在手上 / 插在发电机上"；插着时显示"供电 N FE/t"；墙后找不到配电盘时显示"墙后没有配电盘"。

**存档**：插头插在哪（`PlugHost` / `PlugSocket`）、找到的配电盘（`Panel`，相对位置）、供电平均都存在方块实体里。建筑 NBT 里 `Panel` 和 `PlugHost` 由 `AflBlockEntityProcessor` 跟着旋转、镜像转换。

## 文件

- 生成器：`tools/build-generator-inlet-v1.mjs`（`--check`、`--preview DIR`）；可编辑源 `src/main/blockbench/generator_inlet_box_v1.bbmodel` 和 `textures/generator_inlet_box_v1{,_s,_n}.png`。
- 运行时：
  - `geo/generator_inlet_box.geo.json`、`meshes/generator_inlet_box.aflmesh.json`、`block_mesh_profiles/generator_inlet_box.json`；
  - `textures/block/generator_inlet_box{,_s,_n}.png`；
  - `models/block/generator_inlet_box.json`（`static_mesh` 加载器）、`models/block/l14_30_plug.{obj,mtl,json}`、`models/item/generator_inlet_box.json`（`builtin/entity`）；
  - `blockstates/generator_inlet_box.json`。
- 代码：
  - `block/GeneratorInletBoxBlock`、`blockentity/GeneratorInletBoxBlockEntity`、`client/GeneratorInletBoxRenderer`；
  - `energy/PlugCord`（`Kind`、`stowing()`、`GENERATOR_LENGTH`）、`energy/PowerPlugs`（`socketFits`、`fits`、`capacity`、按区块找插头）、`energy/PlugSocketHost`（`socketFits`、`capacity`）；
  - `client/PlugCordRenderer`（`Style`、L14 插头）；
  - `compat/jade/GeneratorInletJadeProvider`；
  - 准星提示在 `client/WorldInteractionHint`；
  - 注册：`AflBlocks`、`AflItems`、`AflBlockEntities`、`AflBlockEntityRenderers`、`AflCreativeTabs`、`AflJadePlugin`；建造工具 `dev/authoring/bridge/AuthoringFixtureRegistry`。
- 数据：掉落表；`mineable/pickaxe`、`needs_diamond_tool`；`item_mass/afl_content_v1.json`；中英文名、提示、Jade 文本。

## 已知问题 / 占位

- 盖子的声音是原版铁活板门的占位，要一段 Firefly 录音（铝或钢防雨盖掀开、合上）。
- 电缆收起、拿起时，烘焙在区块里的那一圈电缆最多晚约 1 秒消失或出现（区块模型约每秒按 `meshPartVisible` 重查一次）。
- 进线盒不管转换开关：真实的进线盒后面要接转换开关或互锁，防止发电机电反送到电网。游戏里没有市电，V1 不做。
- 一根电缆只能接一台发电机，一台发电机只有一个 L14-30R。

## 实机验收清单（还没做）

- 挂墙四个朝向，墙拆掉会掉；生存模式要钻石镐，掉落自身；空手拆不掉。
- 盖子掀开、盖上的动作和选择框。
- 潜行右键拿起插头：钩上的电缆消失，电缆从接头拉到手上；再潜行右键放回。
- 插到发电机的 L14-30R；插墙上插座、5-15R 时提示"插头不匹配"。
- 走出 16.5 m 插头掉回挂钩。
- 发电机运行时，配电盘界面显示容量 20 FE/t，房子的灯和插座有电；停机后断电。
- 负载超过 20 FE/t 时发电机跳闸。
- 光影和无光影下的外观、电缆颜色、物品图标；Jade。
